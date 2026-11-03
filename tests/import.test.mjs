import test from 'node:test';
import assert from 'node:assert/strict';
import { parseImport, normalizeRows, filterDevices, demoDevices } from '../data.js';

test('reads Fieldwatch LogStore CSV fields, multiline names and escaped quotes', () => {
  const records = parseImport('timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon,vendor_ie\r\n1793687400000,2026-11-03T06:30:00.000Z,WIFI,02:00:00:00:00:01,"Cabin, ""West""\nGuest",-55,6,2437,,Example,,,,,,,,\r\n', 'log.csv');
  assert.equal(records[0].name, 'Cabin, "West"\nGuest');
  assert.equal(records[0].freq, 2437);
  assert.equal(records[0].lastSeen, 1793687400000);
});
test('reads Fieldwatch JSONL, aggregates duplicate radios, and retains latest signal', () => {
  const rows = [
    {ts:2000, kind:'BLE', mac:'aa:bb', name:'Beacon', rssi:-40},
    {ts:1000, kind:'BLE', mac:'AA:BB', name:'Old name', rssi:-80},
    {ts:3000, kind:'WIFI', mac:'AA:BB', name:'Access point', rssi:-70},
  ];
  const devices = parseImport(rows.map(row => JSON.stringify(row)).join('\n'), 'log.jsonl');
  assert.equal(devices.length, 2);
  assert.equal(devices[0].rssi, -40);
  assert.equal(devices[0].firstSeen, 1000);
  assert.equal(devices[0].lastSeen, 2000);
  assert.equal(devices[0].count, 2);
});
test('JSON export round trips notes, watchlist and observation counts', () => {
  const devices = demoDevices();
  devices[0].note = '<img src=x onerror=alert(1)>'; devices[0].watch = true;
  assert.deepEqual(parseImport(JSON.stringify({schema:'the-watch/v1', observations:devices})), devices);
});
test('filters by type, watchlist, signal, notes, unknown signal and sort', () => {
  const devices = demoDevices();
  assert.equal(filterDevices(devices, {view:'WIFI'}).length, 6);
  assert.equal(filterDevices(devices, {view:'saved'}).length, 2);
  assert.equal(filterDevices(devices, {strength:'strong'}).length, 4);
  assert.equal(filterDevices(devices, {query:'fictional'}).length, 1);
  assert.equal(filterDevices(devices, {kind:'BLE', strength:'weak'}).length, 3);
  const unknown = normalizeRows([{kind:'WIFI', mac:'example', rssi:null}]);
  assert.equal(filterDevices(unknown, {strength:'strong'}).length, 0);
  assert.equal(filterDevices(unknown, {strength:'unknown'}).length, 1);
  assert.equal(filterDevices(devices)[0].rssi, -42);
});
test('rejects malformed or unsupported imports without silently dropping records', () => {
  for (const content of ['', '[]', '{}', '[{"kind":"CELL","mac":"a"}]', '[{"kind":"BLE","mac":"a","rssi":"NaN"}]', '[{"kind":"WIFI","mac":"a","rssi":5}]', '[{"kind":"BLE","mac":"a","ts":"bad"}]', 'kind,mac,name\nBLE,a,"unfinished', 'kind,mac\nBLE,a,extra', '{"kind":"BLE","mac":"a"}\ninvalid']) {
    assert.throws(() => parseImport(content));
  }
  assert.throws(() => normalizeRows(new Array(50001).fill({kind:'BLE',mac:'a'})), /50,000/);
});
test('BOM, missing signal, missing timestamps, and blank lines are supported', () => {
  const [device] = parseImport('\uFEFFkind,mac,name,rssi\r\n\r\nBLE,abc,Beacon,\r\n', 'sample.csv');
  assert.equal(device.rssi, null);
  assert.equal(device.lastSeen, null);
  assert.equal(device.mac, 'ABC');
});
