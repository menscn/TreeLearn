/* Production main.js, real Electron windows, isolated profile, no provider requests. */
const { spawn, spawnSync } = require('node:child_process');
const net = require('node:net');
const https = require('node:https');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const electron = require('electron');
const { fileURLToPath } = require('node:url');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-main-e2e-'));
const profile = path.join(root, 'profile');
const desktop = path.resolve(__dirname, '..');
const entry = path.join(desktop, 'app', 'index.html');
const database = path.join(profile, 'desktop-learning-v1', 'learning.sqlite');
const portable = process.env.TREELEARN_E2E_PACKAGED_EXE ? path.resolve(process.env.TREELEARN_E2E_PACKAGED_EXE) : null;
const networkMode = process.env.TREELEARN_E2E_LOCAL_HTTPS === '1';
const personaMode = process.env.TREELEARN_E2E_PERSONA === '1';
const branchMode = process.env.TREELEARN_E2E_BRANCH === '1';
const evidence = path.resolve(__dirname, '../../artifacts/evidence', `desktop-main-e2e-${path.basename(root)}`);
const synthetic = 'synthetic-main-profile-key-94821';
let localServer, localUrl, certificate, releaseStream, networkRequests = 0, cancelledStreamClosed = false;
let expectedPrompts, promptMatches = [];
let branchRoot, branchChild, branchSource;
const boundFiles = [
  'desktop/main.js', 'desktop/window-options.cjs', 'desktop/navigation.cjs', 'desktop/preload.cjs',
  'desktop/learning-service.cjs', 'desktop/desktop-ipc.cjs', 'desktop/close-coordinator.cjs',
  'desktop/branch-context.cjs',
  'desktop/close-service.cjs', 'desktop/app/index.html', 'desktop/app/learning-prompt.json',
  'frontend/dist-offline/index.html', 'desktop/test/desktop-main-cold-e2e.cjs',
  'desktop/test/desktop-profile-isolation-probe.cjs', 'desktop/test/desktop-e2e-window.ps1'
];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const assert = (condition, message) => { if (!condition) throw new Error(message); };
function hashes() {
  const workspace = path.resolve(desktop, '..');
  return Object.fromEntries(boundFiles.map(name => [name,
    crypto.createHash('sha256').update(fs.readFileSync(path.join(workspace, name))).digest('hex')]));
}

async function portForTest() {
  const server = net.createServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}
async function startLocalProvider() {
  const javaHome = process.env.JAVA_HOME || 'D:\\Environment\\Jdk\\jdk21';
  const keytool = path.join(javaHome, 'bin', 'keytool.exe');
  const store = path.join(root, 'synthetic-server.p12');
  certificate = path.join(root, 'synthetic-certificate.pem');
  assert(fs.existsSync(keytool), 'keytool unavailable');
  const generated = spawnSync(keytool, ['-genkeypair', '-noprompt', '-alias', 'synthetic', '-keyalg', 'EC',
    '-dname', 'CN=127.0.0.1', '-ext', 'SAN=ip:127.0.0.1', '-ext', 'bc=ca:true', '-validity', '1',
    '-storetype', 'PKCS12', '-keystore', store, '-storepass', 'testpass', '-keypass', 'testpass'],
  { encoding: 'utf8', timeout: 15000, windowsHide: true });
  assert(generated.status === 0, 'synthetic TLS certificate creation failed');
  const exported = spawnSync(keytool, ['-exportcert', '-rfc', '-alias', 'synthetic', '-keystore', store,
    '-storepass', 'testpass', '-file', certificate], { encoding: 'utf8', timeout: 15000, windowsHide: true });
  assert(exported.status === 0, 'synthetic TLS certificate export failed');
  localServer = https.createServer({ pfx: fs.readFileSync(store), passphrase: 'testpass' }, (request, response) => {
    let body = '';
    request.on('data', chunk => { body += chunk.toString('utf8'); if (body.length > 1000000) request.destroy(); });
    request.on('end', () => {
      try {
        const payload = JSON.parse(body);
        assert(request.socket.remoteAddress === '127.0.0.1' || request.socket.remoteAddress === '::ffff:127.0.0.1', 'non-loopback request');
        assert(request.method === 'POST' && request.url === '/chat/completions', 'unexpected provider request');
        assert(request.headers.authorization === `Bearer ${synthetic}`, 'incorrect provider authorization');
        assert(payload.stream === true && payload.model === 'synthetic-model', 'incorrect provider options');
        const questions = branchMode ? ['分支根问题', '父后续问题', '子分支问题', '重启后子分支追问'] : ['合成 HTTPS 问题', '合成 HTTPS 追问', '合成 HTTPS 停止问题'];
        assert(payload.messages[0].role === 'system' && payload.messages.at(-1).content === questions[networkRequests], 'incorrect learning context');
        if (branchMode && networkRequests >= 2) {
          const contents = payload.messages.map(message => message.content);
          assert(contents.includes('分支根问题') && contents.includes('根回答来源') && !contents.includes('父后续问题') && !contents.includes('父后续回答'), 'branch snapshot context incorrect');
          if (networkRequests === 3) assert(contents.includes('子分支问题') && contents.includes('子分支回答'), 'child history missing');
        }
        if (personaMode) {
          const style = networkRequests === 0 ? 'strict-mentor' : 'late-night';
          assert(payload.messages[0].content === expectedPrompts.prompt + '\n' + expectedPrompts.personas[style], 'incorrect persona prompt');
          promptMatches.push(style);
        }
        if (!branchMode && networkRequests > 0) assert(payload.messages.some(message => message.content === '本机流式回答完成'), 'prior answer missing');
        networkRequests += 1;
        response.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-store' });
        const chunks = branchMode ? [['根回答', '来源'], ['父后续', '回答'], ['子分支', '回答'], ['冷重启', '追问回答']][networkRequests - 1] : [['本机流式', '回答完成'], ['重启后', '追问完成'], ['停止前的部分', '']][networkRequests - 1];
        response.write(`data: ${JSON.stringify({ choices: [{ delta: { content: chunks[0] } }] })}\n\n`);
        if (!branchMode && networkRequests === 3) response.once('close', () => { cancelledStreamClosed = true; });
        else releaseStream = () => {
          response.end(`data: ${JSON.stringify({ choices: [{ delta: { content: chunks[1] }, finish_reason: 'stop' }] })}\n\ndata: [DONE]\n\n`);
        };
      } catch {
        response.writeHead(400); response.end();
      }
    });
  });
  await new Promise((resolve, reject) => { localServer.once('error', reject); localServer.listen(0, '127.0.0.1', resolve); });
  localUrl = `https://127.0.0.1:${localServer.address().port}`;
}
async function targetFor(port, processHandle) {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    if (processHandle.exitCode !== null) throw new Error(`application exited before UI: ${processHandle.exitCode}`);
    try {
      const response = await fetch(`http://127.0.0.1:${port}/json/list`, { signal: AbortSignal.timeout(500) });
      const pages = await response.json();
      const page = pages.find(item => item.type === 'page' && item.url.startsWith('file:'));
      if (page?.webSocketDebuggerUrl) return page;
    } catch {}
    await sleep(100);
  }
  throw new Error('debugging endpoint unavailable');
}
async function connect(url) {
  const socket = new WebSocket(url);
  const pending = new Map();
  let next = 0;
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', () => reject(new Error('debugging connection failed')), { once: true });
  });
  socket.addEventListener('message', event => {
    const packet = JSON.parse(event.data);
    const request = pending.get(packet.id);
    if (!request) return;
    pending.delete(packet.id);
    if (packet.error) request.reject(new Error(`debugging command failed: ${request.method}`));
    else request.resolve(packet.result);
  });
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++next;
    pending.set(id, { resolve, reject, method });
    socket.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) throw new Error('renderer evaluation failed');
    return result.result.value;
  };
  return { socket, send, evaluate };
}
async function until(evaluate, expression, label) {
  const deadline = Date.now() + 12000;
  while (Date.now() < deadline) {
    if (await evaluate(expression)) return;
    await sleep(60);
  }
  throw new Error(`timeout: ${label}`);
}
async function selectPersona(client, value, topic) {
  assert(await client.evaluate(`(() => {const e=document.querySelector('select[aria-label="回答风格"]');if(!e||e.disabled)return false;e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('change',{bubbles:true}));return true;})()`), 'persona selection unavailable');
  await until(client.evaluate, `(async()=>{const s=await window.treelearnDesktop.getState();return ${topic ? `s.topics[0]?.persona === ${JSON.stringify(value)}` : `s.settings.defaultPersona === ${JSON.stringify(value)}`};})()`, 'persona saved');
  await until(client.evaluate, `document.querySelector('select[aria-label="回答风格"]')?.disabled === false`, 'persona selection complete');
}
async function setDraft(client, value) {
  await client.evaluate(`(() => { const e=document.querySelector('.desktop-composer textarea');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true})); })()`);
}
async function sendBranchQuestion(client, question, partial, answer) {
  await setDraft(client, question);
  await until(client.evaluate, `!!document.querySelector('[aria-label="发送问题"]:not(:disabled)')`, 'branch send ready');
  releaseStream = null;
  await client.evaluate(`document.querySelector('[aria-label="发送问题"]').click()`);
  await until(client.evaluate, `document.body.innerText.includes(${JSON.stringify(partial)})`, 'branch first SSE chunk');
  assert(typeof releaseStream === 'function', 'branch response missing');
  releaseStream();
  await until(client.evaluate, `document.body.innerText.includes(${JSON.stringify(answer)}) && !!document.querySelector('.desktop-composer textarea:not(:disabled)')`, 'branch answer complete');
}
async function verifyBranchSource(client, target, width, height, label) {
  const resized = spawnSync('pwsh', ['-NoProfile', '-File', path.join(__dirname, 'desktop-e2e-window.ps1'),
    '-Action', 'resize', '-Root', root, '-Width', String(width), '-Height', String(height)],
  { encoding: 'utf8', timeout: 15000, windowsHide: true });
  fs.writeFileSync(path.join(evidence, `resize-${label}.json`), JSON.stringify({ status: resized.status,
    error: resized.error?.message || '', stdout: resized.stdout || '', stderr: resized.stderr || '' }, null, 2));
  assert(resized.status === 0 && resized.stdout.includes(`width=${width} height=${height}`),
    `isolated window resize failed: ${resized.stderr?.trim() || resized.error?.message || 'no diagnostics'}`);
  await sleep(200);
  assert(await client.evaluate(`(() => {const e=document.querySelector('[data-action="desktop-branch-parent"]');if(!e||e.disabled)return false;e.click();return true;})()`), 'return to source unavailable');
  await until(client.evaluate, `document.querySelector('[data-branch-id="${branchRoot}"]')?.getAttribute('aria-current') === 'page'`, 'returned to root');
  await sleep(350);
  const reading = await client.evaluate(`(() => {const scroll=document.querySelector('.desktop-conversation-scroll');const message=document.querySelector('[data-message-id="${branchSource}"]');const a=scroll.getBoundingClientRect(),b=message.getBoundingClientRect();return {top:b.top,bottom:b.bottom,viewTop:a.top,viewBottom:a.bottom,scrollTop:scroll.scrollTop,width:innerWidth,scrollWidth:document.documentElement.scrollWidth};})()`);
  assert(reading.top >= reading.viewTop - 2 && reading.top < reading.viewBottom && reading.width === reading.scrollWidth, 'source scroll or viewport invalid');
  const image = await client.send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync(path.join(evidence, `branch-source-${label}.png`), Buffer.from(image.data, 'base64'));
  fs.writeFileSync(path.join(evidence, `branch-source-${label}.json`), JSON.stringify({ ...reading, windowEvidence: resized.stdout.trim() }, null, 2));
  assert(await client.evaluate(`(() => {const e=document.querySelector('[data-branch-id="${branchChild}"]');if(!e||e.disabled)return false;e.click();return true;})()`), 'return to child unavailable');
  await until(client.evaluate, `!!document.querySelector('[data-branch-source]')`, 'child source restored');
}
async function waitForExit(child) {
  if (child.exitCode !== null) return child.exitCode;
  return Promise.race([
    new Promise((resolve, reject) => { child.once('exit', resolve); child.once('error', reject); }),
    sleep(12000).then(() => { throw new Error('application failed to close'); })
  ]);
}
async function run(phase) {
  const port = await portForTest();
  const switches = [`--user-data-dir=${profile}`, '--remote-debugging-address=127.0.0.1', `--remote-debugging-port=${port}`];
  const isolated = { ...process.env, APPDATA: path.join(root, 'roaming'), LOCALAPPDATA: path.join(root, 'local'),
    TEMP: path.join(root, 'temp'), TMP: path.join(root, 'temp') };
  if (networkMode) {
    isolated.NODE_EXTRA_CA_CERTS = certificate;
    isolated.HTTP_PROXY = ''; isolated.HTTPS_PROXY = ''; isolated.ALL_PROXY = '';
    isolated.http_proxy = ''; isolated.https_proxy = ''; isolated.all_proxy = '';
    isolated.NO_PROXY = '127.0.0.1,localhost'; isolated.no_proxy = isolated.NO_PROXY;
  }
  const child = spawn(portable || electron, portable ? switches : [desktop, ...switches], {
    cwd: desktop, env: portable ? isolated : process.env, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true
  });
  let stdout = '', stderr = '', client;
  child.stdout.on('data', chunk => { stdout += chunk; });
  child.stderr.on('data', chunk => { stderr += chunk; });
  try {
    const target = await targetFor(port, child);
    const loaded = path.resolve(fileURLToPath(target.url));
    if (portable) {
      const relative = path.relative(root, loaded);
      assert(relative && !relative.startsWith('..') && !path.isAbsolute(relative), 'packaged page not in isolated extraction directory');
    } else assert(loaded.toLowerCase() === entry.toLowerCase(), 'unexpected application page');
    client = await connect(target.webSocketDebuggerUrl);
    await until(client.evaluate, `!!window.treelearnDesktop && !!document.querySelector('[data-screen="home"]')`, 'production home and bridge');
    const initial = await client.evaluate(`(async () => { const s=await window.treelearnDesktop.getState(); return {hasKey:s.settings.hasApiKey, topics:s.topics.length, leaks:JSON.stringify(s).includes(${JSON.stringify(synthetic)})}; })()`);
    assert(initial.leaks === false && initial.topics === (networkMode && phase !== 'write' ? 1 : 0), 'unexpected initial profile state');
    assert(initial.hasKey === (phase !== 'write'), `key state incorrect during ${phase}`);
    if (personaMode && phase !== 'write') {
      const s = await client.evaluate(`window.treelearnDesktop.getState()`);
      assert(s.settings.defaultPersona === 'strict-mentor', 'default persona not preserved');
    }
    assert(await client.evaluate(`(() => { const b=document.querySelector('[data-action="desktop-settings"]'); if(!b)return false;b.click();return true; })()`), 'settings button unavailable');
    await until(client.evaluate, `!!document.querySelector('[data-screen="settings"] input[type="password"]')`, 'settings page');
    if (phase === 'write') {
      if (networkMode) await client.evaluate(`(() => { const inputs=document.querySelectorAll('[data-screen="settings"] input'); for(const [e,v] of [[inputs[0],${JSON.stringify(localUrl)}],[inputs[1],'synthetic-model']]) { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true})); } })()`);
      await client.evaluate(`(() => { const e=document.querySelector('[data-screen="settings"] input[type="password"]'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(synthetic)}); e.dispatchEvent(new Event('input',{bubbles:true})); document.querySelector('[data-screen="settings"] form').requestSubmit(); })()`);
      await until(client.evaluate, `(async () => (await window.treelearnDesktop.getState()).settings.hasApiKey)()`, 'key saved');
      assert(await client.evaluate(`document.querySelector('[data-screen="settings"] input[type="password"]').value === ''`), 'password field was not cleared');
      if (networkMode) {
        assert(await client.evaluate(`(() => { const b=[...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='开始新学习');if(!b)return false;b.click();return true; })()`), 'home navigation unavailable');
        await until(client.evaluate, `!!document.querySelector('.desktop-composer textarea')`, 'composer');
        if (branchMode) {
          await sendBranchQuestion(client, '分支根问题', '根回答', '根回答来源');
          let branchState = await client.evaluate(`window.treelearnDesktop.getState()`);
          const topic = branchState.topics[0];
          branchRoot = topic.rootBranchId;
          branchSource = topic.branches.find(branch => branch.id === branchRoot).messages.find(message => message.content === '根回答来源')?.id;
          assert(branchRoot && branchSource, 'root branch/source missing');
          await setDraft(client, '父分支草稿');
          await until(client.evaluate, `(async()=>{const s=await window.treelearnDesktop.getState();return s.drafts[${JSON.stringify(branchRoot)}]==='父分支草稿';})()`, 'root draft saved');
          await client.evaluate(`document.querySelector('[data-action="desktop-create-branch"]').click()`);
          await until(client.evaluate, `!!document.querySelector('[data-branch-source]')`, 'child created');
          branchState = await client.evaluate(`window.treelearnDesktop.getState()`);
          branchChild = branchState.topics[0].branches.find(branch => branch.parentBranchId === branchRoot)?.id;
          assert(branchChild && branchState.topics[0].branches.find(branch => branch.id === branchChild).sourceMessageId === branchSource, 'child source mismatch');
          await setDraft(client, '子分支草稿');
          await until(client.evaluate, `(async()=>{const s=await window.treelearnDesktop.getState();return s.drafts[${JSON.stringify(branchChild)}]==='子分支草稿';})()`, 'child draft saved');
          await client.evaluate(`document.querySelector('[data-branch-id="${branchRoot}"]').click()`);
          await until(client.evaluate, `document.querySelector('.desktop-composer textarea')?.value==='父分支草稿'`, 'root draft restored');
          await sendBranchQuestion(client, '父后续问题', '父后续', '父后续回答');
          await client.evaluate(`document.querySelector('[data-branch-id="${branchChild}"]').click()`);
          await until(client.evaluate, `document.querySelector('.desktop-composer textarea')?.value==='子分支草稿'`, 'child draft restored');
          await sendBranchQuestion(client, '子分支问题', '子分支', '子分支回答');
          await verifyBranchSource(client, target, 1180, 820, '1180x820');
          await verifyBranchSource(client, target, 900, 620, '900x620');
          await setDraft(client, '重启后的子草稿');
          await until(client.evaluate, `(async()=>{const s=await window.treelearnDesktop.getState();return s.drafts[${JSON.stringify(branchChild)}]==='重启后的子草稿';})()`, 'child draft before close');
          console.log('MAIN_BRANCH_CONTEXT_AND_SOURCE_OK');
        } else {
        if (personaMode) await selectPersona(client, 'strict-mentor', false);
        await client.evaluate(`(() => { const e=document.querySelector('.desktop-composer textarea');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,'合成 HTTPS 问题');e.dispatchEvent(new Event('input',{bubbles:true})); })()`);
        await until(client.evaluate, `!!document.querySelector('[aria-label="发送问题"]:not(:disabled)')`, 'send ready');
        await client.evaluate(`document.querySelector('[aria-label="发送问题"]').click()`);
        await until(client.evaluate, `document.body.innerText.includes('本机流式')`, 'first HTTPS SSE chunk');
        assert(networkRequests === 1 && typeof releaseStream === 'function', 'provider request not observed');
        assert(!await client.evaluate(`document.body.innerText.includes('回答完成')`), 'stream was not incremental');
        releaseStream();
        await until(client.evaluate, `document.body.innerText.includes('本机流式回答完成') && !!document.querySelector('.desktop-composer textarea:not(:disabled)')`, 'HTTPS response completion');
        const state = await client.evaluate(`window.treelearnDesktop.getState()`);
        assert(state.topics.length === 1 && state.topics[0].messages.some(m => m.content === '本机流式回答完成' && m.status === 'complete'), 'answer not persisted');
        if (personaMode) assert(state.topics[0].persona === 'strict-mentor' && state.topics[0].messages.every(m => m.persona === 'strict-mentor'), 'initial persona not recorded');
        assert(!JSON.stringify(state).includes(synthetic), 'key exposed in response state');
        console.log('MAIN_HTTPS_SSE_STREAM_OK');
        }
      }
    } else {
      assert(await client.evaluate(`document.querySelector('[data-screen="settings"]').innerText.includes('已保存 API Key')`), 'saved key status unavailable');
      const image = await client.send('Page.captureScreenshot', { format: 'png' });
      fs.writeFileSync(path.join(evidence, 'main-reopened-settings.png'), Buffer.from(image.data, 'base64'));
      if (networkMode && branchMode) {
        assert(await client.evaluate(`(() => {const e=document.querySelector('.recent-link');if(!e)return false;e.click();return true;})()`), 'reopened history missing');
        await until(client.evaluate, `!!document.querySelector('[data-branch-id="${branchChild}"]')`, 'child branch after restart');
        await client.evaluate(`document.querySelector('[data-branch-id="${branchChild}"]').click()`);
        await until(client.evaluate, `document.body.innerText.includes('子分支回答')`, 'child answer after restart');
        if (phase === 'reopen') {
          assert(await client.evaluate(`document.querySelector('.desktop-composer textarea')?.value === '重启后的子草稿'`), 'child draft not restored');
          await sendBranchQuestion(client, '重启后子分支追问', '冷重启', '冷重启追问回答');
          console.log('MAIN_BRANCH_REOPEN_FOLLOWUP_OK');
        } else {
          await until(client.evaluate, `document.body.innerText.includes('冷重启追问回答')`, 'child follow-up after restart');
          const branchState = await client.evaluate(`window.treelearnDesktop.getState()`);
          assert(branchState.topics[0].branches.length === 2 && branchState.topics[0].branches.find(branch=>branch.id===branchChild)?.sourceMessageId===branchSource, 'branch state not restored');
          console.log('MAIN_BRANCH_SECOND_REOPEN_OK');
        }
      } else if (networkMode) {
        assert(await client.evaluate(`(() => {const e=document.querySelector('.recent-link');if(!e)return false;e.click();return true;})()`), 'reopened history missing');
        await until(client.evaluate, `document.body.innerText.includes('合成 HTTPS 问题') && document.body.innerText.includes('本机流式回答完成')`, 'reopened HTTPS conversation');
        if (personaMode) {
          const s = await client.evaluate(`window.treelearnDesktop.getState()`);
          const expected = phase === 'reopen' ? 'strict-mentor' : 'late-night';
          assert(s.topics[0].persona === expected && s.topics[0].messages[0].persona === 'strict-mentor'
            && s.topics[0].messages[1].persona === 'strict-mentor', 'historical persona not restored');
          assert(await client.evaluate(`document.querySelector('select[aria-label="回答风格"]')?.value === ${JSON.stringify(expected)}`), 'persona selection not restored');
        }
        console.log('MAIN_HTTPS_HISTORY_REOPEN_OK');
        if (phase === 'reopen') {
          if (personaMode) await selectPersona(client, 'late-night', true);
          for (const [question, first, final] of [['合成 HTTPS 追问', '重启后', '重启后追问完成'], ['合成 HTTPS 停止问题', '停止前的部分', null]]) {
            await client.evaluate(`(() => { const e=document.querySelector('.desktop-composer textarea');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,${JSON.stringify(question)});e.dispatchEvent(new Event('input',{bubbles:true})); })()`);
            await until(client.evaluate, `!!document.querySelector('[aria-label="发送问题"]:not(:disabled)')`, 'follow-up send ready');
            await client.evaluate(`document.querySelector('[aria-label="发送问题"]').click()`);
            await until(client.evaluate, `document.body.innerText.includes(${JSON.stringify(first)})`, 'follow-up partial');
            if (final) {
              assert(typeof releaseStream === 'function', 'follow-up response missing');
              releaseStream();
              await until(client.evaluate, `document.body.innerText.includes(${JSON.stringify(final)}) && !!document.querySelector('.desktop-composer textarea:not(:disabled)')`, 'follow-up complete');
            } else {
              assert(await client.evaluate(`(() => {const b=[...document.querySelectorAll('button')].find(x=>x.textContent.trim()==='停止生成');if(!b)return false;b.click();return true;})()`), 'stop button unavailable');
              await until(client.evaluate, `document.body.innerText.includes('已停止生成') && !!document.querySelector('.desktop-composer textarea:not(:disabled)')`, 'cancelled answer');
              const state = await client.evaluate(`window.treelearnDesktop.getState()`);
              assert(state.topics[0].messages.some(m => m.content === '停止前的部分' && m.status === 'cancelled'), 'cancelled partial answer not saved');
              const deadline = Date.now() + 3000;
              while (!cancelledStreamClosed && Date.now() < deadline) await sleep(25);
              assert(cancelledStreamClosed, 'cancel did not close local stream');
              console.log('MAIN_HTTPS_CANCEL_OK');
            }
          }
          console.log('MAIN_HTTPS_REOPEN_FOLLOWUP_OK');
        } else {
          const state = await client.evaluate(`window.treelearnDesktop.getState()`);
          assert(state.topics[0].messages.some(m => m.content === '停止前的部分' && m.status === 'cancelled'), 'cancelled partial missing after restart');
          if (personaMode) assert(state.topics[0].persona === 'late-night' && state.topics[0].messages.some(m => m.content === '合成 HTTPS 追问' && m.persona === 'late-night') && state.topics[0].messages.some(m => m.content === '重启后追问完成' && m.persona === 'late-night'), 'follow-up persona not preserved');
          console.log('MAIN_HTTPS_CANCEL_REOPEN_OK');
          if (personaMode) console.log('MAIN_PERSONA_HISTORY_REOPEN_OK');
        }
      }
    }
    assert(fs.existsSync(database), 'database not created inside isolated profile');
    assert(!fs.readFileSync(database).includes(Buffer.from(synthetic)), 'plaintext synthetic key in database');
    // Page.close exercises the production close coordinator; normal process exit is required.
    client.send('Page.close').catch(() => {});
    const code = await waitForExit(child);
    assert(code === 0, `application close failed: ${code}`);
    assert(!stdout.includes(synthetic) && !stderr.includes(synthetic), 'key appeared in process output');
    fs.writeFileSync(path.join(evidence, `${phase}-stdout.txt`), stdout, 'utf8');
    fs.writeFileSync(path.join(evidence, `${phase}-stderr.txt`), stderr, 'utf8');
    console.log(`MAIN_${phase.toUpperCase()}_OK`);
  } finally {
    if (client && child.exitCode === null) {
      client.send('Page.close').catch(() => {});
      await waitForExit(child).catch(() => {});
    }
    if (client) client.socket.close();
    if (child.exitCode === null) { child.kill(); await waitForExit(child).catch(() => {}); }
    if (portable) {
      const cleanup = spawnSync('pwsh', ['-NoProfile', '-File', path.join(__dirname, 'desktop-e2e-window.ps1'),
        '-Action', 'cleanup', '-Root', root], { encoding: 'utf8', timeout: 15000, windowsHide: true });
      if (cleanup.status !== 0) throw new Error('isolated process cleanup failed');
    }
  }
}

(async () => {
  fs.mkdirSync(evidence, { recursive: true });
  for (const name of ['roaming', 'local', 'temp']) fs.mkdirSync(path.join(root, name));
  if (networkMode) await startLocalProvider();
  if (branchMode) assert(networkMode && portable && !personaMode, 'branch validation requires packaged local HTTPS mode');
  if (personaMode) {
    assert(networkMode && portable, 'persona validation requires packaged local HTTPS mode');
    expectedPrompts = JSON.parse(fs.readFileSync(path.join(desktop, 'app', 'learning-prompt.json'), 'utf8'));
    assert(expectedPrompts.personas['strict-mentor'] && expectedPrompts.personas['late-night'], 'persona prompt resource missing');
  }
  if (portable) assert(fs.statSync(portable).isFile() && /\.exe$/i.test(portable), 'packaged executable unavailable');
  assert(!fs.existsSync(database), 'synthetic profile already contained a database');
  const probe = spawnSync(electron, [path.join(__dirname, 'desktop-profile-isolation-probe.cjs'), `--user-data-dir=${profile}`], {
    cwd: desktop, encoding: 'utf8', timeout: 10000, windowsHide: true
  });
  const measured = (probe.stdout || '').split(/\r?\n/).find(line => line.startsWith('PROFILE_PROBE '))?.slice('PROFILE_PROBE '.length);
  assert(probe.status === 0 && measured && path.resolve(measured).toLowerCase() === path.resolve(profile).toLowerCase(), 'userData isolation not confirmed');
  if (!portable) {
    const sync = spawnSync(process.execPath, [path.join(desktop, 'scripts', 'sync-app.mjs')], { cwd: desktop, encoding: 'utf8', timeout: 15000 });
    assert(sync.status === 0 && fs.existsSync(entry), 'desktop offline sync failed');
  }
  const before = hashes();
  const portableHash = portable ? crypto.createHash('sha256').update(fs.readFileSync(portable)).digest('hex') : null;
  fs.writeFileSync(path.join(evidence, 'hashes-before.json'), JSON.stringify(before, null, 2));
  fs.writeFileSync(path.join(evidence, 'command.txt'), `node desktop/test/desktop-main-cold-e2e.cjs\npackagedExecutable=${portable || 'none'}\nlocalHttps=${networkMode}\n`, 'utf8');
  await run('write');
  await run('reopen');
  if (networkMode) await run('verify');
  const after = hashes();
  fs.writeFileSync(path.join(evidence, 'hashes-after.json'), JSON.stringify(after, null, 2));
  assert(JSON.stringify(before) === JSON.stringify(after), 'inputs changed during test');
  if (portable) assert(portableHash === crypto.createHash('sha256').update(fs.readFileSync(portable)).digest('hex'), 'packaged executable changed');
  assert(!networkMode || (branchMode ? networkRequests === 4 : networkRequests === 3 && cancelledStreamClosed), 'unexpected local provider request count or cancellation');
  if (personaMode) {
    assert(JSON.stringify(promptMatches) === JSON.stringify(['strict-mentor', 'late-night', 'late-night']), 'persona request mismatch');
    console.log('MAIN_PERSONA_PROMPTS_MATCH_OK');
  }
  fs.writeFileSync(path.join(evidence, 'result.json'), JSON.stringify({ exitCode: 0, inputHashesStable: true,
    firstProcess: 'MAIN_WRITE_OK', secondProcess: 'MAIN_REOPEN_OK', thirdProcess: networkMode ? 'MAIN_VERIFY_OK' : null,
    localHttpsRequests: networkRequests, cancelledStreamClosed, personaPromptsMatched: personaMode ? promptMatches : null, portableHash }, null, 2));
  if (localServer) await new Promise(resolve => localServer.close(resolve));
  console.log('DESKTOP_MAIN_COLD_E2E_OK');
  console.log(`EVIDENCE_DIR ${evidence}`);
})().catch(error => { if (localServer) { localServer.closeAllConnections(); localServer.close(); } console.error('DESKTOP_MAIN_COLD_E2E_FAIL', error.message); process.exitCode = 1; });
