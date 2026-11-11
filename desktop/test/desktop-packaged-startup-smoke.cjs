/* A single production portable startup and graceful close, in a fresh synthetic profile. */
const { spawn, spawnSync } = require('node:child_process');
const { fileURLToPath } = require('node:url');
const crypto = require('node:crypto');
const fs = require('node:fs');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');

const exe = path.resolve(process.argv[2] || '');
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-start-smoke-'));
const profile = path.join(root, 'profile');
const evidence = path.resolve(__dirname, '../../artifacts/evidence', `desktop-start-smoke-${path.basename(root)}`);
const workspace = path.resolve(__dirname, '../..');
const desktop = path.resolve(__dirname, '..');
const inputs = ['desktop/main.js', 'desktop/window-options.cjs', 'desktop/navigation.cjs', 'desktop/preload.cjs',
  'desktop/desktop-ipc.cjs', 'desktop/close-coordinator.cjs', 'desktop/close-service.cjs', 'desktop/learning-service.cjs',
  'desktop/branch-context.cjs', 'desktop/app/index.html', 'desktop/app/learning-prompt.json', 'frontend/dist-offline/index.html',
  'desktop/test/desktop-packaged-startup-smoke.cjs', 'desktop/test/desktop-profile-isolation-probe.cjs'];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const assert = (ok, message) => { if (!ok) throw new Error(message); };
function fileHash(file) { return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'); }
function hashes() { return Object.fromEntries(inputs.map(name => [name, fileHash(path.join(workspace, name))])); }
function scopedProcesses(stop = false) {
  const script = `$ErrorActionPreference='Stop'
    $r=[IO.Path]::GetFullPath($env:TREELEARN_SMOKE_ROOT).TrimEnd('\\')
    $t=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\\')
    if ([IO.Path]::GetDirectoryName($r) -ine $t -or [IO.Path]::GetFileName($r) -notmatch '^treelearn-start-smoke-[a-zA-Z0-9]+$') { throw 'INVALID_TEST_ROOT' }
    $p=Join-Path $r 'profile'
    $items=@(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.CommandLine -and $_.ExecutablePath.StartsWith($r+'\\temp\\',[StringComparison]::OrdinalIgnoreCase) -and $_.CommandLine.IndexOf($p,[StringComparison]::OrdinalIgnoreCase) -ge 0 -and $_.Name -notmatch '^(pwsh|powershell)\\.exe$' })
    if ($env:TREELEARN_SMOKE_STOP -eq '1') { foreach($item in $items){ Stop-Process -Id $item.ProcessId -Force -ErrorAction Stop } }
    [Console]::WriteLine('SCOPED_PROCESS_COUNT='+$items.Count)`;
  const out = spawnSync('pwsh', ['-NoProfile', '-Command', script], { encoding: 'utf8', timeout: 15000, windowsHide: true,
    env: { ...process.env, TREELEARN_SMOKE_ROOT: root, TREELEARN_SMOKE_STOP: stop ? '1' : '0' } });
  assert(out.status === 0, 'scoped process inspection failed');
  const count = Number((out.stdout.match(/SCOPED_PROCESS_COUNT=(\d+)/) || [])[1]);
  assert(Number.isSafeInteger(count), 'scoped process count missing');
  return count;
}
async function getPort() {
  const server = net.createServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}
async function getTarget(port, child) {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error(`portable exited before page: ${child.exitCode}`);
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(500) });
      const page = (await response.json()).find(item => item.type === 'page' && item.url.startsWith('file:'));
      if (page?.webSocketDebuggerUrl) return page;
    } catch {}
    await sleep(100);
  }
  throw new Error('debug endpoint unavailable');
}
async function connect(url) {
  const socket = new WebSocket(url), pending = new Map();
  let sequence = 0, networkRequests = 0;
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
  socket.addEventListener('message', event => {
    const data = JSON.parse(event.data);
    if (data.method === 'Network.requestWillBeSent' && /^https?:/i.test(data.params.request.url)) networkRequests++;
    const callback = pending.get(data.id); if (!callback) return;
    pending.delete(data.id); if (data.error) callback.reject(new Error(`CDP ${callback.method} failed`)); else callback.resolve(data.result);
  });
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence; pending.set(id, { resolve, reject, method }); socket.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) throw new Error('renderer evaluation failed');
    return result.result.value;
  };
  await send('Network.enable');
  return { socket, send, evaluate, requests: () => networkRequests };
}
async function waitForExit(child, timeout = 12000) {
  if (child.exitCode !== null) return child.exitCode;
  let timer;
  try { return await Promise.race([new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); }),
    new Promise((_, reject) => { timer = setTimeout(() => reject(new Error('portable close timeout')), timeout); })]); }
  finally { clearTimeout(timer); }
}

(async () => {
  fs.mkdirSync(evidence, { recursive: true });
  for (const name of ['roaming', 'local', 'temp']) fs.mkdirSync(path.join(root, name));
  assert(fs.existsSync(exe) && fs.statSync(exe).isFile() && /\.exe$/i.test(exe), 'portable exe unavailable');
  assert(!fs.existsSync(path.join(profile, 'desktop-learning-v1', 'learning.sqlite')), 'isolated database already exists');
  const electron = require('electron');
  const probe = spawnSync(electron, [path.join(__dirname, 'desktop-profile-isolation-probe.cjs'), `--user-data-dir=${profile}`], { encoding: 'utf8', timeout: 10000, windowsHide: true });
  const measured = (probe.stdout || '').split(/\r?\n/).find(line => line.startsWith('PROFILE_PROBE '))?.slice('PROFILE_PROBE '.length);
  assert(probe.status === 0 && measured && path.resolve(measured).toLowerCase() === path.resolve(profile).toLowerCase(), 'profile isolation probe failed');
  const before = hashes(), exeBefore = fileHash(exe), port = await getPort();
  fs.writeFileSync(path.join(evidence, 'hashes-before.json'), JSON.stringify(before, null, 2));
  const env = { ...process.env, APPDATA: path.join(root, 'roaming'), LOCALAPPDATA: path.join(root, 'local'),
    TEMP: path.join(root, 'temp'), TMP: path.join(root, 'temp'), HTTP_PROXY: '', HTTPS_PROXY: '', ALL_PROXY: '',
    http_proxy: '', https_proxy: '', all_proxy: '', NO_PROXY: '127.0.0.1,localhost', no_proxy: '127.0.0.1,localhost' };
  delete env.NODE_EXTRA_CA_CERTS;
  const child = spawn(exe, [`--user-data-dir=${profile}`, '--remote-debugging-address=127.0.0.1', `--remote-debugging-port=${port}`], {
    cwd: desktop, env, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true
  });
  let stdout = '', stderr = '', client, exitCode;
  child.stdout.on('data', data => { stdout += data; }); child.stderr.on('data', data => { stderr += data; });
  try {
    const page = await getTarget(port, child);
    const loaded = path.resolve(fileURLToPath(page.url));
    const relative = path.relative(root, loaded);
    assert(relative && !relative.startsWith('..') && !path.isAbsolute(relative), 'page outside isolated extraction');
    client = await connect(page.webSocketDebuggerUrl);
    const deadline = Date.now() + 12000;
    let state;
    while (Date.now() < deadline) {
      state = await client.evaluate(`(async () => { if (!window.treelearnDesktop || !document.querySelector('[data-screen="home"]')) return null; const s=await window.treelearnDesktop.getState(); return { bridge:true, topics:s.topics.length, hasKey:s.settings.hasApiKey, node:typeof require, text:document.body.innerText.length }; })()`);
      if (state) break; await sleep(80);
    }
    assert(state && state.bridge && state.topics === 0 && state.hasKey === false && state.node === 'undefined' && state.text > 0, 'home/bridge/empty state failed');
    const database = path.join(profile, 'desktop-learning-v1', 'learning.sqlite');
    assert(fs.existsSync(database) && fs.statSync(database).isFile(), 'database missing in isolated profile');
    assert(client.requests() === 0, 'unexpected renderer network request');
    const screenshot = await client.send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(evidence, 'home.png'), Buffer.from(screenshot.data, 'base64'));
    client.send('Page.close').catch(() => {});
    exitCode = await waitForExit(child);
    assert(exitCode === 0, `portable exit ${exitCode}`);
    client.socket.close(); client = null;
    await sleep(300);
    assert(scopedProcesses() === 0, 'isolated process survived close');
    let listening = false;
    try { await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(500) }); listening = true; } catch {}
    assert(!listening, 'debug listener survived close');
    const after = hashes();
    fs.writeFileSync(path.join(evidence, 'hashes-after.json'), JSON.stringify(after, null, 2));
    assert(JSON.stringify(before) === JSON.stringify(after) && exeBefore === fileHash(exe), 'inputs changed during test');
    fs.writeFileSync(path.join(evidence, 'stdout.txt'), stdout); fs.writeFileSync(path.join(evidence, 'stderr.txt'), stderr);
    fs.writeFileSync(path.join(evidence, 'result.json'), JSON.stringify({ exitCode, portableSha256: exeBefore, sourceHashesStable: true,
      home: true, bridge: true, emptyProfile: true, isolatedDatabase: true, rendererHttpRequests: 0, scopedProcessesAfter: 0,
      debugListenerAfter: false, screenshotSha256: fileHash(path.join(evidence, 'home.png')) }, null, 2));
    console.log('PACKAGED_STARTUP_SMOKE_OK home=ok bridge=ok isolated_profile=ok close=ok network=0');
    console.log(`EVIDENCE_DIR ${evidence}`);
  } finally {
    if (client) { client.send('Page.close').catch(() => {}); client.socket.close(); }
    if (child.exitCode === null) { child.kill(); await waitForExit(child, 3000).catch(() => {}); }
    if (scopedProcesses() !== 0) scopedProcesses(true);
  }
})().catch(error => { console.error('PACKAGED_STARTUP_SMOKE_FAIL', error.message); process.exitCode = 1; });
