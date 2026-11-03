// Uses an already-installed Chrome and Node's built-in CDP WebSocket support.
// Test artifacts and browser profile stay in ignored test-results/.
import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

await mkdir('test-results', {recursive:true});
const server = spawn(process.execPath, ['scripts/serve.mjs'], {env:{...process.env, PORT:'4179'}, windowsHide:true});
let browser, socket;
const timeout = setTimeout(() => { console.error('Browser check timed out'); browser?.kill(); server.kill(); process.exit(1); }, 60000);
try {
  await new Promise((res, rej) => { server.once('error',rej); server.stdout.once('data',res); server.once('exit',code => rej(new Error(`Server exited ${code}`))); });
  const executable = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
  browser = spawn(executable, ['--headless=new','--disable-gpu','--no-first-run','--no-default-browser-check','--remote-debugging-port=0',`--user-data-dir=${resolve('test-results/browser-profile')}`, 'about:blank'], {windowsHide:true});
  const endpoint = await new Promise((res, rej) => {
    let output = '';
    browser.once('error',rej);
    browser.stderr.on('data',chunk => { output += chunk; const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/); if (match) res(match[1]); });
    browser.once('exit',code => rej(new Error(`Chrome exited ${code}`)));
  });
  const origin = new URL(endpoint).origin.replace('ws:', 'http:');
  const targets = await (await fetch(`${origin}/json/list`)).json();
  const target = targets.find(item => item.type === 'page');
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res,rej) => { socket.onopen = res; socket.onerror = rej; });
  let id = 0;
  const pending = new Map(), errors = [];
  socket.onmessage = event => {
    const payload = JSON.parse(event.data);
    if (payload.method === 'Runtime.exceptionThrown') errors.push(payload.params.exceptionDetails.text);
    if (pending.has(payload.id)) {
      const {res,rej} = pending.get(payload.id); pending.delete(payload.id);
      payload.error ? rej(new Error(payload.error.message)) : res(payload.result);
    }
  };
  const send = (method, params = {}) => new Promise((res,rej) => { const current = ++id; pending.set(current,{res,rej}); socket.send(JSON.stringify({id:current,method,params})); });
  async function evaluate(expression) {
    const result = await send('Runtime.evaluate', {expression,awaitPromise:true,returnByValue:true});
    if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
  }
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setDeviceMetricsOverride', {width:1440,height:1100,deviceScaleFactor:1,mobile:false});
  await send('Page.navigate', {url:'http://127.0.0.1:4179'});
  // Wait on actual module initialization, not a fixed delay.
  await evaluate(`new Promise((resolve,reject)=>{let attempts=0;const timer=setInterval(()=>{if(document.querySelectorAll('#radios tr').length===12){clearInterval(timer);resolve(true)}else if(++attempts>100){clearInterval(timer);reject(Error('Dashboard did not render'))}},50)})`);
  const checks = await evaluate(`(async () => {
    const checks = [];
    const assert = (condition,name) => {if(!condition)throw Error(name);checks.push(name)};
    const $ = s=>document.querySelector(s);
    window.confirm=()=>true;
    $('#load-demo').click();
    assert($('#stat-total').textContent==='12','sample renders 12 radios');
    assert($('#source-label').textContent==='SAMPLE SESSION','sample is clearly labeled');
    $('[data-view="WIFI"]').click();assert($('#radios').children.length===6,'Wi-Fi navigation filters');
    $('[data-view="saved"]').click();assert($('#radios').children.length===2,'watchlist navigation filters');
    $('[data-view="all"]').click();
    $('#search').value='weather';$('#search').dispatchEvent(new Event('input'));assert($('#radios').children.length===1,'search filters');
    $('.radio-name').click();assert($('#details').open,'radio details open');
    $('#note').value='<img src=x onerror=alert(1)> field note';$('#note-form').requestSubmit();
    assert(!$('#details').open,'notes save closes details');
    assert(JSON.parse(localStorage.getItem('the-watch.workspace.v1')).observations.some(r=>r.note.includes('field note')),'notes persist locally');
    $('#search').value='';$('#search').dispatchEvent(new Event('input'));
    const before=Number($('#stat-saved').textContent);$('.watch-button').click();assert(Number($('#stat-saved').textContent)===before+1,'watchlist toggle updates counts');
    $('#strength').value='strong';$('#strength').dispatchEvent(new Event('change'));assert($('#radios').children.length===4,'signal filtering');
    $('#strength').value='all';$('#strength').dispatchEvent(new Event('change'));
    let exported;const realCreate=URL.createObjectURL;URL.createObjectURL=blob=>{exported=blob;return realCreate(blob)};
    $('#export').click();const envelope=JSON.parse(await exported.text());assert(envelope.observations.length===12,'JSON download contains current filtered view');
    const importFile=async(content,name)=>{const input=$('#file');Object.defineProperty(input,'files',{configurable:true,value:[new File([content],name)]});input.dispatchEvent(new Event('change'));await new Promise(r=>setTimeout(r,100))};
    window.confirm=()=>true;
    await importFile(JSON.stringify([{kind:'BLE',mac:'AA:BB:CC',name:'<img src=x onerror=alert(1)>',rssi:-48,ts:1793687400000}]),'radio.json');
    assert($('#stat-total').textContent==='1','JSON import replaces session');
    assert($('.radio-name').textContent.includes('<img'),'untrusted names render as plain text');
    assert(!$('#radios img'),'imported names cannot create HTML');
    await importFile('kind,mac,rssi\\nBLE,ABC,invalid','bad.csv');assert($('#stat-total').textContent==='1'&&$('#message').textContent.includes('Import failed'),'invalid import preserves current session');
    await importFile(JSON.stringify(envelope),'sample.json');assert($('#source-label').textContent==='SAMPLE SESSION','sample exports remain labeled on reimport');
    $('#about').click();assert($('#help').open,'capability help opens');$('#help .close-button').click();
    assert(document.documentElement.scrollWidth<=innerWidth,'desktop has no horizontal page overflow');
    return checks;
  })()`);
  const desktop = await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
  await writeFile('test-results/desktop.png', Buffer.from(desktop.data,'base64'));
  await send('Emulation.setDeviceMetricsOverride', {width:390,height:844,deviceScaleFactor:1,mobile:true});
  const mobile = await evaluate(`({overflow:document.documentElement.scrollWidth>innerWidth,rows:document.querySelectorAll('#radios tr').length})`);
  if (mobile.overflow) throw new Error('Mobile page has horizontal overflow');
  const shot = await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
  await writeFile('test-results/mobile.png', Buffer.from(shot.data,'base64'));
  await send('Page.reload');
  await evaluate(`new Promise((resolve,reject)=>{let tries=0;const timer=setInterval(()=>{if(document.querySelectorAll('#radios tr').length===12){clearInterval(timer);resolve(true)}else if(++tries>100){clearInterval(timer);reject(Error('Saved session did not restore'))}},50)})`);
  if (errors.length) throw new Error(`Browser errors: ${errors.join(', ')}`);
  console.log(JSON.stringify({passed:checks.length+2,checks,mobileOverflow:false,persistenceReload:true,browserErrors:errors,screenshots:['test-results/desktop.png','test-results/mobile.png']},null,2));
} finally { clearTimeout(timeout); socket?.close(); browser?.kill(); server.kill(); }
