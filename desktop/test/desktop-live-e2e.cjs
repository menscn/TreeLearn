/* Isolated UI integration: production preload, IPC and SQLite; synthetic provider only. */
const { app, BrowserWindow, ipcMain, safeStorage } = require('electron');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createLearningService } = require('../learning-service.cjs');
const { registerDesktopIpc } = require('../desktop-ipc.cjs');
const { buildWindowOptions } = require('../window-options.cjs');
const { createCloseCoordinator } = require('../close-coordinator.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-live-e2e-'));
const evidence = path.resolve(__dirname, '../../artifacts/evidence', `desktop-live-e2e-${path.basename(root)}`);
const entry = path.resolve(__dirname, '../../frontend/dist-offline/index.html');
const database = path.join(root, 'data', 'learning.sqlite');
const secret = 'synthetic-ui-test-key-89235';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
app.setPath('userData', path.join(root, 'profile'));
app.disableHardwareAcceleration();
// The harness deliberately closes and recreates the only window to test restart.
app.on('window-all-closed', () => {});

let win, service, calls = 0, finishFirst;
let resolveFirst;
finishFirst = new Promise(resolve => { resolveFirst = resolve; });
const requests = [];
function assert(value, message) { if (!value) throw new Error(message); }
function frame(content) { return `data: ${JSON.stringify({ choices: [{ delta: { content } }] })}\n\n`; }
function sse(signal, parts, gate) {
  const encoder = new TextEncoder();
  return new Response(new ReadableStream({
    start(controller) {
      let ended = false;
      signal.addEventListener('abort', () => {
        if (!ended) { ended = true; controller.error(new DOMException('Aborted', 'AbortError')); }
      }, { once: true });
      controller.enqueue(encoder.encode(frame(parts[0])));
      if (gate) gate.then(() => {
        if (ended) return;
        ended = true;
        controller.enqueue(encoder.encode(frame(parts[1]) + 'data: [DONE]\n\n'));
        controller.close();
      });
    }
  }), { status: 200, headers: { 'content-type': 'text/event-stream' } });
}
async function transport(url, options) {
  const body = JSON.parse(options.body);
  assert(url === 'https://example.test/chat/completions', 'unexpected provider URL');
  assert(options.redirect === 'error' && options.headers.authorization === `Bearer ${secret}`, 'provider request policy');
  assert(body.stream === true && body.model === 'synthetic-model', 'provider request body');
  requests.push(body.messages);
  calls += 1;
  if (calls === 1) return sse(options.signal, ['第一段流式', '回答完成'], finishFirst);
  if (calls === 2) return new Response('', { status: 503 });
  if (calls === 3) return sse(options.signal, ['重试', '成功'], Promise.resolve());
  if (calls === 4) return sse(options.signal, ['中断前的回答'], null);
  throw new Error('unexpected synthetic request');
}

async function evaluate(code) { return win.webContents.executeJavaScript(code, true); }
async function until(expression, label, timeout = 12000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await evaluate(expression)) return;
    await delay(50);
  }
  throw new Error(`timeout: ${label}`);
}
async function click(selector) {
  assert(await evaluate(`(() => { const e = document.querySelector(${JSON.stringify(selector)}); if (!e || e.disabled) return false; e.click(); return true; })()`), `cannot click ${selector}`);
}
async function clickText(text) {
  assert(await evaluate(`(() => { const e=[...document.querySelectorAll('button')].find(x=>x.textContent.trim()===${JSON.stringify(text)}&&!x.disabled); if(!e)return false;e.click();return true; })()`), `cannot click ${text}`);
}
async function input(selector, value) {
  assert(await evaluate(`(() => { const e=document.querySelector(${JSON.stringify(selector)}); if(!e)return false; const p=e instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(p,'value').set.call(e,${JSON.stringify(value)}); e.dispatchEvent(new Event('input',{bubbles:true})); return true; })()`), `cannot input ${selector}`);
}
async function createWindow() {
  const options = buildWindowOptions();
  options.webPreferences.preload = path.resolve(__dirname, '../preload.cjs');
  win = new BrowserWindow(options);
  createCloseCoordinator({ win, ipcMain, entry, getService: () => service,
    clearService: () => { service = null; }, showError: () => { throw new Error('close coordinator rejected'); } });
  await win.loadFile(entry);
  await until(`!!document.querySelector('[data-screen="home"]')`, 'home');
}

app.whenReady().then(async () => {
  try {
    assert(fs.existsSync(entry), 'frontend offline build missing');
    assert(await safeStorage.isAsyncEncryptionAvailable(), 'OS encryption unavailable');
    fs.mkdirSync(evidence, { recursive: true });
    service = createLearningService({ database, secrets: safeStorage, transport, tutorPrompt: '合成导师规则' });
    const bridge = new Proxy({}, { get: (_target, key) => (...args) => service[key](...args) });
    registerDesktopIpc({ ipcMain, service: bridge, entry, getWindow: () => win });
    await createWindow();
    assert(await evaluate(`typeof require === 'undefined' && !!window.treelearnDesktop`), 'preload isolation');

    await click('[data-action="desktop-settings"]');
    await until(`!!document.querySelector('[data-screen="settings"]')`, 'settings');
    await input('[data-screen="settings"] input:nth-of-type(1)', 'https://example.test');
    // Each field is nested in a separate label, so address them by document order.
    await input('[data-screen="settings"] label:nth-of-type(2) input', 'synthetic-model');
    await input('[data-screen="settings"] input[type="password"]', secret);
    await clickText('保存设置');
    await until(`document.querySelector('[data-screen="settings"]')?.innerText.includes('设置已保存')`, 'settings saved');
    assert(await evaluate(`document.querySelector('[data-screen="settings"] input[type="password"]').value === ''`), 'key remained visible');
    const publicState = await service.getState();
    assert(publicState.settings.hasApiKey && !JSON.stringify(publicState).includes(secret), 'key exposed in state');
    await clickText('开始新学习');

    await input('.desktop-composer textarea', '第一个合成问题');
    await click('[aria-label="发送问题"]');
    await until(`document.body.innerText.includes('第一段流式')`, 'first streamed chunk');
    assert(await evaluate(`document.body.innerText.includes('第一个合成问题') && !document.body.innerText.includes('回答完成')`), 'stream not incremental');
    resolveFirst();
    await until(`document.body.innerText.includes('第一段流式回答完成') && !!document.querySelector('.desktop-composer textarea:not(:disabled)')`, 'first answer complete');
    assert(requests[0][0].role === 'system' && requests[0].at(-1).content === '第一个合成问题', 'learning context');

    await input('.desktop-composer textarea', '第二个合成追问');
    await click('[aria-label="发送问题"]');
    await until(`!!document.querySelector('[data-action="desktop-retry"]')`, 'failed request retry');
    await click('[data-action="desktop-retry"]');
    await until(`document.body.innerText.includes('重试成功') && !!document.querySelector('.desktop-composer textarea:not(:disabled)')`, 'retry result');
    assert(requests[2].some(m => m.content === '第一段流式回答完成') && requests[2].at(-1).content === '第二个合成追问', 'follow-up context');

    await input('.desktop-composer textarea', '需要停止的合成问题');
    await click('[aria-label="发送问题"]');
    await until(`document.body.innerText.includes('中断前的回答')`, 'partial before stop');
    await clickText('停止生成');
    await until(`document.body.innerText.includes('已停止生成') && !!document.querySelector('.desktop-composer textarea:not(:disabled)')`, 'cancel outcome');
    let state = await service.getState();
    const topic = state.topics[0];
    assert(topic.messages.filter(m => m.content === '第二个合成追问').length === 1, 'duplicate question after retry');
    assert(topic.messages.some(m => m.content === '中断前的回答' && m.status === 'cancelled'), 'partial answer not preserved');
    await input('.desktop-composer textarea', '重启后保留的合成草稿');
    for (let n = 0; n < 100; n += 1) {
      state = await service.getState();
      if (state.drafts[topic.id] === '重启后保留的合成草稿') break;
      await delay(50);
    }
    assert(state.drafts[topic.id] === '重启后保留的合成草稿', 'draft not saved');
    for (const [width, height] of [[1180, 820], [900, 620]]) {
      win.setSize(width, height);
      await delay(350);
      const dimensions = await evaluate(`(() => {
        const scroll = document.querySelector('.desktop-conversation-scroll');
        scroll.scrollTop = scroll.scrollHeight;
        const last = document.querySelector('.desktop-message:last-child');
        const composer = document.querySelector('.desktop-composer');
        return { width:innerWidth, scroll:document.documentElement.scrollWidth,
          lastBottom:last.getBoundingClientRect().bottom, composerTop:composer.getBoundingClientRect().top,
          scrollBottom:scroll.getBoundingClientRect().bottom };
      })()`);
      assert(dimensions.scroll <= dimensions.width, `horizontal overflow at ${width}`);
      assert(dimensions.lastBottom <= dimensions.composerTop + 2 && dimensions.lastBottom <= dimensions.scrollBottom + 2,
        `last answer cannot scroll above composer at ${width}: ${JSON.stringify(dimensions)}`);
      await delay(150);
      fs.writeFileSync(path.join(evidence, `chat-${width}x${height}.png`), (await win.webContents.capturePage()).toPNG());
    }
    // Exercise the real renderer-to-main close handshake, including queued draft saves.
    await input('.desktop-composer textarea', '关闭瞬间的合成草稿');
    const closed = new Promise(resolve => win.once('closed', resolve));
    win.close();
    await Promise.race([closed, delay(12000).then(() => { throw new Error('window close handshake timed out'); })]);
    win = null;
    assert(service === null, 'close handshake did not close service');
    assert(!fs.readFileSync(database).includes(Buffer.from(secret)), 'plaintext key found in database');
    service = createLearningService({ database, secrets: safeStorage, transport, tutorPrompt: '合成导师规则' });
    await createWindow();
    await until(`document.querySelectorAll('.recent-link').length > 0`, 'reopened history');
    await click('.recent-link');
    await until(`document.body.innerText.includes('第一段流式回答完成') && document.querySelector('.desktop-composer textarea')?.value === '关闭瞬间的合成草稿'`, 'reopened data');
    assert((await service.getState()).settings.hasApiKey, 'reopened key state');
    assert(calls === 4, 'unexpected provider request count');
    console.log('DESKTOP_LIVE_E2E_OK windows=2 stream=ok retry=ok cancel=ok service_reopen=ok safeStorage=ok');
    console.log(`EVIDENCE_DIR ${evidence}`);
    win.destroy(); win = null;
    service.close(); service = null;
    app.exit(0);
  } catch (error) {
    if (win && !win.isDestroyed()) win.destroy();
    if (service) { try { service.close(); } catch {} }
    console.error('DESKTOP_LIVE_E2E_FAIL', error.message);
    app.exit(1);
  }
}).catch(() => { console.error('DESKTOP_LIVE_E2E_FAIL startup'); app.exit(1); });
