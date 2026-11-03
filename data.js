// Fieldwatch's LogStore.kt defines the CSV and JSONL fields consumed here.
// This adapter is independently written; no Android source or radio databases are bundled.
export const MAX_ROWS = 50000;
export const MAX_BYTES = 10 * 1024 * 1024;

export function parseCsv(text) {
  const rows = [];
  let row = [], cell = '', quoted = false, afterQuote = false;
  function field() { row.push(cell); cell = ''; afterQuote = false; }
  function record() {
    field();
    if (row.some(value => value.trim())) rows.push(row);
    row = [];
    if (rows.length > MAX_ROWS + 1) throw new Error('Maximum 50,000 observations per import.');
  }
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') { cell += '"'; i++; }
      else if (char === '"') { quoted = false; afterQuote = true; }
      else cell += char;
    } else if (char === ',') field();
    else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[i + 1] === '\n') i++;
      record();
    } else if (char === '"') {
      if (cell || afterQuote) throw new Error('Invalid CSV quoting.');
      quoted = true;
    } else {
      if (afterQuote && char.trim()) throw new Error('Unexpected text after a quoted CSV field.');
      if (!afterQuote) cell += char;
    }
  }
  if (quoted) throw new Error('Unclosed quoted CSV field.');
  if (cell || row.length || afterQuote) record();
  if (!rows.length) throw new Error('The file is empty.');
  const headers = rows.shift().map(value => value.trim().toLowerCase());
  if (!headers.includes('kind') || !headers.includes('mac')) throw new Error('CSV must include kind and mac columns. Use Fieldwatch’s Share log export.');
  if (new Set(headers).size !== headers.length) throw new Error('Duplicate CSV column names.');
  return rows.map((values, index) => {
    if (values.length !== headers.length) throw new Error(`CSV row ${index + 2} has the wrong number of columns.`);
    return Object.fromEntries(headers.map((header, i) => [header, values[i]]));
  });
}

function text(value, limit = 300) { return value == null ? '' : String(value).slice(0, limit); }
function number(value, min, max, label) {
  if (value == null || value === '') return null;
  const result = Number(value);
  if (!Number.isFinite(result) || result < min || result > max) throw new Error(`Invalid ${label}.`);
  return result;
}
function timestamp(value) {
  if (value == null || value === '') return null;
  const result = typeof value === 'number' || /^\d+$/.test(value) ? Number(value) : Date.parse(value);
  if (!Number.isFinite(result) || result < 0 || result > 8640000000000000) throw new Error('Invalid observation timestamp.');
  return result;
}

export function normalizeRows(rows) {
  if (!Array.isArray(rows) || rows.length === 0) throw new Error('No observations found.');
  if (rows.length > MAX_ROWS) throw new Error('Maximum 50,000 observations per import.');
  const devices = new Map();
  rows.forEach((raw, index) => {
    try {
      if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Expected an observation object.');
      const kind = text(raw.kind).toUpperCase().replace(/[-_ ]/g, '');
      const type = kind === 'WIFI' ? 'WIFI' : ['BLE', 'BLUETOOTHLE'].includes(kind) ? 'BLE' : null;
      if (!type) throw new Error('kind must be WIFI or BLE.');
      const mac = text(raw.mac).trim().toUpperCase();
      if (!mac || mac.length > 100) throw new Error('A mac identifier is required (maximum 100 characters).');
      const lastSeen = timestamp(raw.lastSeen ?? raw.ts ?? raw.timestamp ?? raw.iso);
      const firstSeen = timestamp(raw.firstSeen ?? lastSeen);
      if (firstSeen !== null && lastSeen !== null && firstSeen > lastSeen) throw new Error('First observation is later than last observation.');
      const device = {
        kind: type, mac, name: text(raw.name) || 'Unnamed radio', vendor: text(raw.vendor) || 'Unknown',
        rssi: number(raw.rssi, -150, 0, 'RSSI (expected −150 to 0 dBm)'),
        channel: number(raw.channel, 0, 1000, 'channel'), freq: number(raw.freq, 0, 100000, 'frequency'),
        firstSeen, lastSeen, count: number(raw.count, 1, Number.MAX_SAFE_INTEGER, 'observation count') ?? 1,
        fleets: text(raw.fleets), note: text(raw.note, 2000), watch: raw.watch === true,
      };
      const key = `${type}:${mac}`;
      const previous = devices.get(key);
      if (!previous) devices.set(key, device);
      else {
        const count = previous.count + device.count;
        const times = [previous.firstSeen, device.firstSeen].filter(value => value !== null);
        const first = times.length ? Math.min(...times) : null;
        const latest = (device.lastSeen ?? -1) >= (previous.lastSeen ?? -1) ? device : previous;
        devices.set(key, { ...latest, count, firstSeen: first, note: device.note || previous.note, watch: previous.watch || device.watch });
      }
    } catch (error) { throw new Error(`Observation ${index + 1}: ${error.message}`); }
  });
  return [...devices.values()];
}

export function parseImport(content, filename = '') {
  const clean = content.replace(/^\uFEFF/, '').trim();
  if (!clean) throw new Error('The file is empty.');
  let rows;
  if (/\.csv$/i.test(filename) || (!clean.startsWith('[') && !clean.startsWith('{'))) rows = parseCsv(clean);
  else {
    let parsed;
    try { parsed = JSON.parse(clean); }
    catch {
      rows = clean.split(/\r?\n/).filter(line => line.trim()).map((line, index) => {
        try { return JSON.parse(line); } catch { throw new Error(`Invalid JSON on line ${index + 1}.`); }
      });
    }
    if (!rows) rows = Array.isArray(parsed) ? parsed : Array.isArray(parsed?.observations) ? parsed.observations : [parsed];
  }
  return normalizeRows(rows);
}

export function filterDevices(devices, { view = 'all', kind = 'all', query = '', strength = 'all', sort = 'signal' } = {}) {
  const term = query.trim().toLowerCase();
  return devices.filter(device => {
    if (view === 'saved' && !device.watch) return false;
    if (['WIFI', 'BLE'].includes(view) && device.kind !== view) return false;
    if (kind !== 'all' && device.kind !== kind) return false;
    if (term && ![device.name, device.mac, device.vendor, device.note, device.fleets].join(' ').toLowerCase().includes(term)) return false;
    if (strength === 'unknown') return device.rssi === null;
    if (strength !== 'all' && device.rssi === null) return false;
    if (strength === 'strong' && device.rssi < -60) return false;
    if (strength === 'medium' && (device.rssi >= -60 || device.rssi < -80)) return false;
    if (strength === 'weak' && device.rssi >= -80) return false;
    return true;
  }).sort((a, b) => sort === 'name' ? a.name.localeCompare(b.name) : sort === 'recent' ? (b.lastSeen ?? -1) - (a.lastSeen ?? -1) : (b.rssi ?? -Infinity) - (a.rssi ?? -Infinity));
}

export function demoDevices() {
  const time = Date.UTC(2026, 10, 3, 6, 30);
  const radios = [
    ['WIFI', 'Field station', 'Example Networks', -42, 6],
    ['BLE', 'Trail beacon', 'Example Beacons', -51, 0],
    ['WIFI', 'Workshop · Guest', 'Example Networks', -57, 36],
    ['BLE', 'Weather sensor', 'Example Sensors', -62, 0],
    ['BLE', 'Unnamed radio', 'Unknown', -67, 0],
    ['WIFI', 'Garden mesh', 'Example Networks', -70, 11],
    ['WIFI', 'Hidden network', 'Unknown', -76, 1],
    ['BLE', 'Bike sensor', 'Example Sensors', -81, 0],
    ['BLE', 'Unnamed radio', 'Unknown', -87, 0],
    ['WIFI', 'Field station · 5G', 'Example Networks', -60, 149],
    ['BLE', 'Temperature tag', 'Example Sensors', -91, 0],
    ['WIFI', 'Cabin', 'Example Networks', -83, 44],
  ];
  return normalizeRows(radios.map(([kind, name, vendor, rssi, channel], index) => ({kind, name, vendor, rssi, channel, mac: `02:00:00:00:00:${index.toString(16).padStart(2, '0')}`, ts: time - index * 17000, firstSeen: time - 1200000, count: 14 + index * 3, watch: [1, 3].includes(index), note: index === 1 ? 'Fictional beacon used in the sample session.' : ''})));
}
