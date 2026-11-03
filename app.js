import { MAX_BYTES, parseImport, normalizeRows, filterDevices, demoDevices } from './data.js';

const $ = selector => document.querySelector(selector);
const STORAGE_KEY = 'the-watch.workspace.v1';
let devices = demoDevices(), source = 'Sample field walk', sample = true, selected = null;
const filters = { view: 'all', kind: 'all', query: '', strength: 'all', sort: 'signal' };
function message(text = '', error = false) { $('#message').textContent = text; $('#message').classList.toggle('error', error); }
function persist() {
  try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ observations: devices, source, sample })); }
  catch { message('Browser storage is unavailable or full. Your session is available until you close this page; export to keep it.', true); }
}
try {
  const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
  if (saved) {
    devices = normalizeRows(saved.observations);
    source = String(saved.source ?? 'Saved session').slice(0, 200);
    sample = saved.sample === true;
  }
} catch { message('Saved workspace could not be loaded. Showing the sample session; import a file to continue.', true); }

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}
function date(value) { return value === null ? 'Not recorded' : new Date(value).toLocaleString(undefined, {month:'short', day:'numeric', hour:'2-digit', minute:'2-digit', second:'2-digit'}); }
function saveWatch(device) {
  device.watch = !device.watch;
  persist(); render();
  if (selected) $('#toggle-watch').textContent = selected.watch ? '★ Remove from watchlist' : '☆ Add to watchlist';
}

function renderChart() {
  const bins = Array.from({length:16}, () => ({WIFI:0, BLE:0}));
  devices.forEach(device => {
    if (device.rssi !== null) bins[Math.max(0, Math.min(15, Math.floor((device.rssi + 100) / 5)))][device.kind]++;
  });
  const peak = Math.max(1, ...bins.flatMap(bin => [bin.WIFI, bin.BLE]));
  $('#landscape').replaceChildren(...bins.map((bin, index) => {
    const group = element('div', undefined, 'signal-bin');
    group.title = `${-100 + index * 5} to ${-96 + index * 5} dBm: ${bin.WIFI} Wi-Fi, ${bin.BLE} BLE`;
    for (const kind of ['WIFI', 'BLE']) {
      const bar = element('span', undefined, kind === 'WIFI' ? 'wifi' : 'ble');
      bar.style.height = `${bin[kind] / peak * 100}%`;
      group.append(bar);
    }
    return group;
  }));
}

function render() {
  const visible = filterDevices(devices, filters);
  const savedCount = devices.filter(device => device.watch).length;
  $('#stat-total').textContent = devices.length;
  $('#stat-wifi').textContent = devices.filter(device => device.kind === 'WIFI').length;
  $('#stat-ble').textContent = devices.filter(device => device.kind === 'BLE').length;
  $('#stat-saved').textContent = $('#saved-count').textContent = savedCount;
  $('#result-count').textContent = visible.length;
  $('#source-label').textContent = sample ? 'SAMPLE SESSION' : 'IMPORTED SESSION';
  $('#source-description').textContent = sample ? 'A fictional field walk. Explore the workspace or import a Fieldwatch log.' : `${source} · Reviewed locally in your browser`;
  $('#table-summary').textContent = `${visible.length} of ${devices.length} radios · ${sample ? 'Fictional sample data' : 'Imported observations; no live scanning'}`;
  const names = {all:'Overview', WIFI:'Wi-Fi networks', BLE:'Bluetooth LE', saved:'Watchlist'};
  $('#breadcrumb').textContent = names[filters.view];
  $('#inventory-title').firstChild.textContent = filters.view === 'saved' ? 'Your watchlist ' : 'Radio inventory ';
  $('#page-title').textContent = {all:'Your surroundings, observed.', WIFI:'A closer look at your networks.', BLE:'Small signals. More context.', saved:'Keep the important radios in view.'}[filters.view];
  $('#empty').hidden = visible.length !== 0;
  $('#radios').replaceChildren(...visible.map(device => {
    const row = element('tr');
    const identity = element('td');
    const name = element('button', device.name, 'radio-name');
    name.addEventListener('click', () => openDetails(device));
    identity.append(name, element('span', device.mac, 'radio-id'));
    const type = element('td');
    type.append(element('span', device.kind === 'WIFI' ? '⌁ Wi-Fi' : 'ᛒ BLE', `type-badge${device.kind === 'BLE' ? ' bluetooth' : ''}`));
    const signal = element('td', undefined, 'signal-reading');
    const meter = element('span', undefined, 'signal-meter');
    meter.setAttribute('aria-hidden', 'true');
    for (let i = 0; i < 4; i++) {
      const bar = element('i', undefined, device.rssi !== null && device.rssi >= [-100,-80,-65,-50][i] ? 'lit' : '');
      bar.style.height = `${4 + i * 3}px`; meter.append(bar);
    }
    signal.append(meter, element('span', device.rssi === null ? 'Unknown' : `${device.rssi} dBm`));
    const watch = element('td');
    const button = element('button', device.watch ? '★' : '☆', `watch-button${device.watch ? ' saved' : ''}`);
    button.setAttribute('aria-label', `${device.watch ? 'Remove' : 'Add'} ${device.name} ${device.mac} ${device.watch ? 'from' : 'to'} watchlist`);
    button.setAttribute('aria-pressed', String(device.watch));
    button.addEventListener('click', () => { saveWatch(device); message(`${device.name} ${device.watch ? 'added to' : 'removed from'} watchlist.`); });
    watch.append(button);
    row.append(identity, type, element('td', device.vendor), signal, element('td', date(device.lastSeen)), watch);
    return row;
  }));
  renderChart();
}

function openDetails(device) {
  selected = device;
  $('#detail-name').textContent = device.name;
  $('#detail-id').textContent = device.mac;
  const values = [
    ['Radio type', device.kind === 'WIFI' ? 'Wi-Fi' : 'Bluetooth LE'], ['Vendor', device.vendor],
    ['Latest RSSI', device.rssi === null ? 'Not recorded' : `${device.rssi} dBm`],
    ['Channel', device.channel || 'Not recorded'], ['Frequency', device.freq ? `${device.freq} MHz` : 'Not recorded'],
    ['First observed', date(device.firstSeen)], ['Last observed', date(device.lastSeen)],
    ['Observation count', device.count], ['Exported signature labels', device.fleets || 'None'],
  ];
  $('#detail-fields').replaceChildren(...values.flatMap(([label,value]) => [element('dt', label), element('dd', value)]));
  $('#note').value = device.note;
  $('#toggle-watch').textContent = device.watch ? '★ Remove from watchlist' : '☆ Add to watchlist';
  $('#details').showModal();
}
$('#toggle-watch').addEventListener('click', () => { if (selected) saveWatch(selected); });
$('#note-form').addEventListener('submit', event => {
  event.preventDefault();
  if (!selected) return;
  selected.note = $('#note').value.trim();
  persist(); render(); $('#details').close(); message('Notes saved locally.');
});
document.querySelectorAll('.close-button').forEach(button => button.addEventListener('click', () => button.closest('dialog').close()));
$('#details').addEventListener('close', () => { selected = null; });
$('#about').addEventListener('click', () => $('#help').showModal());
$('#capabilities').addEventListener('click', () => $('#help').showModal());
document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => {
  filters.view = button.dataset.view; filters.kind = 'all';
  document.querySelectorAll('[data-view]').forEach(item => item.classList.toggle('active', item === button));
  updateKind(); render();
}));
function updateKind() {
  document.querySelectorAll('[data-kind]').forEach(button => {
    button.classList.toggle('selected', filters.kind === button.dataset.kind);
    button.setAttribute('aria-pressed', String(filters.kind === button.dataset.kind));
    button.disabled = ['WIFI', 'BLE'].includes(filters.view) && !['all', filters.view].includes(button.dataset.kind);
  });
}
document.querySelectorAll('[data-kind]').forEach(button => button.addEventListener('click', () => { filters.kind = button.dataset.kind; updateKind(); render(); }));
$('#search').addEventListener('input', event => { filters.query = event.target.value; render(); });
$('#strength').addEventListener('change', event => { filters.strength = event.target.value; render(); });
$('#sort').addEventListener('change', event => { filters.sort = event.target.value; render(); });
function resetFilters() {
  Object.assign(filters, {view:'all', kind:'all', query:'', strength:'all', sort:'signal'});
  $('#search').value = ''; $('#strength').value = 'all'; $('#sort').value = 'signal';
  document.querySelectorAll('[data-view]').forEach(button => button.classList.toggle('active', button.dataset.view === 'all'));
  updateKind();
}
$('#clear-filters').addEventListener('click', () => { resetFilters(); render(); });
$('#import').addEventListener('click', () => $('#file').click());
$('#file').addEventListener('change', async event => {
  const file = event.target.files[0];
  if (!file) return;
  try {
    if (file.size > MAX_BYTES) throw new Error('File too large. Maximum import size is 10 MB.');
    const content = await file.text();
    const imported = parseImport(content, file.name);
    let importedSample = false;
    try { const metadata = JSON.parse(content); importedSample = metadata.schema === 'the-watch/v1' && metadata.sample === true; } catch { /* CSV and JSONL have no envelope. */ }
    if (!sample && !window.confirm('Replace the current session, including its notes and watchlist? Export first if you want to keep it.')) return;
    devices = imported; source = file.name; sample = importedSample;
    resetFilters(); message(`Imported ${devices.length} unique radios. Duplicate identifiers were grouped by radio type.`); persist(); render();
  } catch (error) { message(`Import failed: ${error.message} Your current session is unchanged.`, true); }
  finally { event.target.value = ''; }
});
$('#export').addEventListener('click', () => {
  const visible = filterDevices(devices, filters);
  if (!visible.length) { message('No radios in the current view to export.', true); return; }
  const blob = new Blob([JSON.stringify({schema:'the-watch/v1', source, sample, exportedAt:new Date().toISOString(), observations:visible}, null, 2)], {type:'application/json'});
  const url = URL.createObjectURL(blob);
  const anchor = element('a'); anchor.href = url; anchor.download = `the-watch-${new Date().toISOString().slice(0,10)}.json`;
  document.body.append(anchor); anchor.click(); anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  message(`Exported ${visible.length} radios, including notes and identifiers.`);
});
$('#load-demo').addEventListener('click', () => {
  if (!window.confirm('Replace the current session with fictional sample data? Export first to keep your observations and notes.')) return;
  devices = demoDevices(); source = 'Sample field walk'; sample = true;
  resetFilters(); message('Fictional sample session loaded.'); persist(); render();
});
$('#clear-data').addEventListener('click', () => {
  if (!window.confirm('Remove this app’s saved observations, notes, and watchlist from this browser? Downloaded exports are unaffected.')) return;
  try { localStorage.removeItem(STORAGE_KEY); }
  catch { message('Could not clear browser storage. Clear this site’s data in browser settings.', true); return; }
  devices = []; source = 'Empty workspace'; sample = false; resetFilters(); render(); message('Local workspace cleared. Import observations or load the sample session.');
});
updateKind(); render();
