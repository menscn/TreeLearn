/* Real UI/preload/IPC/SQLite branch interactions with synthetic SSE and an isolated profile. */
const { app, BrowserWindow, ipcMain, safeStorage } = require('electron');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { createLearningService } = require('../learning-service.cjs');
const { registerDesktopIpc } = require('../desktop-ipc.cjs');
const { createCloseCoordinator } = require('../close-coordinator.cjs');
const { buildWindowOptions } = require('../window-options.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-e2e-'));
const evidence = path.resolve(__dirname, '../../artifacts/evidence', `desktop-branch-e2e-${path.basename(root)}`);
const entry = path.resolve(__dirname, '../../frontend/dist-offline/index.html');
const database = path.join(root, 'data', 'learning.sqlite');
const sourceFiles = ['desktop/learning-service.cjs', 'desktop/branch-context.cjs', 'desktop/preload.cjs',
  'desktop/desktop-ipc.cjs', 'desktop/close-coordinator.cjs', 'desktop/window-options.cjs',
  'frontend/dist-offline/index.html', 'desktop/test/desktop-branch-live-e2e.cjs'];
const secret = 'synthetic-branch-test-key';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const assert = (condition, message) => { if (!condition) throw new Error(message); };
let win, service, calls = 0;
const contexts = [];
app.setPath('userData', path.join(root, 'profile'));
app.disableHardwareAcceleration();
app.on('window-all-closed', () => {});
function hashes() {
  const workspace = path.resolve(__dirname, '../..');
  return Object.fromEntries(sourceFiles.map(name => [name, crypto.createHash('sha256').update(fs.readFileSync(path.join(workspace, name))).digest('hex')]));
}
async function transport(url, options) {
  assert(url === 'https://example.test/chat/completions' && options.redirect === 'error', 'unexpected transport policy');
  assert(options.headers.authorization === `Bearer ${secret}`, 'synthetic key not decrypted');
  const body = JSON.parse(options.body);
  assert(body.stream === true, 'request not streaming');
  contexts.push(body.messages);
  const responses = ['根答案唯一标记', '父续答唯一标记', '子答案唯一标记'];
  assert(calls < responses.length, 'unexpected synthetic request');
  const content = responses[calls++];
  const stream = `data: ${JSON.stringify({ choices: [{ delta: { content }, finish_reason: 'stop' }] })}\n\ndata: [DONE]\n\n`;
  return new Response(stream, { status: 200, headers: { 'content-type': 'text/event-stream' } });
}
async function evaluate(code) { return win.webContents.executeJavaScript(code, true); }
async function until(expression, label, timeout = 12000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) { if (await evaluate(expression)) return; await delay(50); }
  throw new Error(`timeout: ${label}`);
}
async function click(selector) {
  assert(await evaluate(`(() => {const e=document.querySelector(${JSON.stringify(selector)});if(!e||e.disabled)return false;e.click();return true;})()`), `cannot click ${selector}`);
}
async function input(selector, value) {
  assert(await evaluate(`(() => {const e=document.querySelector(${JSON.stringify(selector)});if(!e)return false;const p=e instanceof HTMLTextAreaElement?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));return true;})()`), `cannot input ${selector}`);
}
async function state() { return service.getState(); }
async function waitDraft(scope, expected) {
  for (let n=0;n<150;n++) { if ((await state()).drafts[scope] === expected) return; await delay(25); }
  throw new Error('draft not persisted');
}
async function send(text, answer) {
  await input('.desktop-composer textarea', text);
  await until(`!!document.querySelector('[aria-label="发送问题"]:not(:disabled)')`, 'send ready');
  await click('[aria-label="发送问题"]');
  await until(`document.body.innerText.includes(${JSON.stringify(answer)}) && !!document.querySelector('.desktop-composer textarea:not(:disabled)')`, 'answer complete');
}
async function openWindow() {
  const options = buildWindowOptions();
  options.webPreferences.preload = path.resolve(__dirname, '../preload.cjs');
  win = new BrowserWindow(options);
  createCloseCoordinator({ win, ipcMain, entry, getService: () => service,
    clearService: () => { service = null; }, showError: () => { throw new Error('close rejected'); } });
  await win.loadFile(entry);
  await until(`!!document.querySelector('[data-screen="home"]')`, 'home');
  // A hidden Chromium window may retain a stale compositor frame even after the DOM updates.
  win.showInactive();
}
async function closeWindow() {
  const closed = new Promise(resolve => win.once('closed', resolve));
  win.close();
  await Promise.race([closed, delay(10000).then(() => { throw new Error('close handshake timeout'); })]);
  win = null;
  assert(service === null, 'service not closed');
}

app.whenReady().then(async () => {
  try {
    assert(await safeStorage.isAsyncEncryptionAvailable(), 'OS encryption unavailable');
    fs.mkdirSync(evidence, { recursive: true });
    const before = hashes();
    fs.writeFileSync(path.join(evidence, 'hashes-before.json'), JSON.stringify(before, null, 2));
    const options = { database, secrets: safeStorage, transport, tutorPrompt: '合成学习导师', personaPrompts: { default: '' } };
    service = createLearningService(options);
    const bridge = new Proxy({}, { get: (_target, key) => (...args) => service[key](...args) });
    registerDesktopIpc({ ipcMain, service: bridge, entry, getWindow: () => win });
    await openWindow();
    await click('[data-action="desktop-settings"]');
    await until(`!!document.querySelector('[data-screen="settings"] input[type="password"]')`, 'settings');
    await input('[data-screen="settings"] label:nth-of-type(1) input', 'https://example.test');
    await input('[data-screen="settings"] label:nth-of-type(2) input', 'synthetic-model');
    await input('[data-screen="settings"] input[type="password"]', secret);
    await evaluate(`document.querySelector('[data-screen="settings"] form').requestSubmit()`);
    await until(`document.querySelector('[data-screen="settings"]')?.innerText.includes('设置已保存')`, 'settings saved');
    await evaluate(`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='开始新学习').click()`);
    await send('根问题唯一标记', '根答案唯一标记');
    let current = await state();
    const topic = current.topics[0], rootId = topic.rootBranchId || topic.id;
    const source = topic.branches.find(b => b.id === rootId).messages.find(m => m.content === '根答案唯一标记');
    assert(source?.status === 'complete', 'root answer unavailable');
    await input('.desktop-composer textarea', '父草稿唯一标记');
    await waitDraft(rootId, '父草稿唯一标记');
    await click('[data-action="desktop-create-branch"]');
    await until(`!!document.querySelector('[data-branch-source]')`, 'child source card');
    current = await state();
    const child = current.topics[0].branches.find(b => b.parentBranchId === rootId);
    assert(child && child.sourceMessageId === source.id, 'child source identity');
    await input('.desktop-composer textarea', '子草稿唯一标记');
    await waitDraft(child.id, '子草稿唯一标记');
    await click(`[data-branch-id="${rootId}"]`);
    await until(`document.querySelector('.desktop-composer textarea')?.value === '父草稿唯一标记'`, 'root draft restore');
    await send('父继续问题唯一标记', '父续答唯一标记');
    await click(`[data-branch-id="${child.id}"]`);
    await until(`document.querySelector('.desktop-composer textarea')?.value === '子草稿唯一标记'`, 'child draft restore');
    await send('子追问唯一标记', '子答案唯一标记');
    assert(contexts.length === 3, 'incorrect request count');
    const childContext = contexts[2].map(message => message.content);
    assert(childContext.includes('根问题唯一标记') && childContext.includes('根答案唯一标记')
      && childContext.includes('子追问唯一标记') && !childContext.includes('父继续问题唯一标记')
      && !childContext.includes('父续答唯一标记'), 'child context changed after birth');
    console.log('BRANCH_IMMUTABLE_CONTEXT_OK');
    current = await state();
    const childAnswer = current.topics[0].branches.find(branch => branch.id === child.id).messages.find(message => message.content === '子答案唯一标记');
    assert(childAnswer, 'search target missing');
    for (const [width,height] of [[1180,820],[900,620]]) {
      win.setSize(width,height); await delay(200);
      await evaluate(`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='学习记录').click()`);
      await until(`!!document.querySelector('[data-screen="library"] input[type="search"]')`, 'library search');
      await input('[data-screen="library"] input[type="search"]', '子答案唯一标记');
      await until(`!!document.querySelector('[data-screen="library"] .topic-open')`, 'child result');
      await click('[data-screen="library"] .topic-open');
      await until(`document.activeElement?.getAttribute('data-message-id') === ${JSON.stringify(childAnswer.id)}`, 'child result focus');
      await delay(100);
      const searchReading = await evaluate(`(() => {const scroll=document.querySelector('.desktop-conversation-scroll');const message=document.activeElement;const a=scroll.getBoundingClientRect(),b=message.getBoundingClientRect();return {active:message.getAttribute('data-message-id'),top:b.top,bottom:b.bottom,viewTop:a.top,viewBottom:a.bottom,scrollTop:scroll.scrollTop,width:innerWidth,scrollWidth:document.documentElement.scrollWidth};})()`);
      assert(searchReading.active === childAnswer.id && searchReading.top < searchReading.viewBottom && searchReading.bottom > searchReading.viewTop && searchReading.scrollWidth <= searchReading.width,
        `child search target not focused and visible at ${width}: ${JSON.stringify(searchReading)}`);
      assert(await evaluate(`document.querySelector('[data-branch-id="${child.id}"]')?.getAttribute('aria-current') === 'page'`), 'search opened wrong branch');
      await evaluate(`new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`); await delay(300);
      fs.writeFileSync(path.join(evidence, `search-${width}x${height}.png`), (await win.webContents.capturePage()).toPNG());
      fs.writeFileSync(path.join(evidence, `search-${width}x${height}.json`), JSON.stringify(searchReading, null, 2));
      await click('[data-action="desktop-branch-parent"]');
      await until(`document.querySelector('[data-branch-id="${rootId}"]')?.getAttribute('aria-current') === 'page'`, 'return to parent');
      await until(`document.activeElement?.getAttribute('data-message-id') === ${JSON.stringify(source.id)}`, 'parent source focus');
      await delay(200);
      const reading = await evaluate(`(() => { const scroll=document.querySelector('.desktop-conversation-scroll');const message=document.querySelector('[data-message-id="${source.id}"]');const a=scroll.getBoundingClientRect(),b=message.getBoundingClientRect();return {active:document.activeElement?.getAttribute('data-message-id'),top:b.top,bottom:b.bottom,viewTop:a.top,viewBottom:a.bottom,scrollTop:scroll.scrollTop,width:innerWidth,scrollWidth:document.documentElement.scrollWidth}; })()`);
      assert(reading.active === source.id && reading.top >= reading.viewTop - 2 && reading.top < reading.viewBottom && reading.scrollWidth <= reading.width,
        `source not visible after return at ${width}: ${JSON.stringify(reading)}`);
      await evaluate(`new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))`);
      await delay(300);
      fs.writeFileSync(path.join(evidence, `source-${width}x${height}.png`), (await win.webContents.capturePage()).toPNG());
      fs.writeFileSync(path.join(evidence, `source-${width}x${height}.json`), JSON.stringify(reading, null, 2));
      await click(`[data-branch-id="${child.id}"]`);
      await until(`!!document.querySelector('[data-branch-source]')`, 'child reopened');
    }
    await evaluate(`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='学习记录').click()`);
    await until(`!!document.querySelector('[data-screen="library"] input[type="search"]')`, 'library title search');
    await input('[data-screen="library"] input[type="search"]', '根问题唯一标记');
    await click('[data-screen="library"] .topic-open');
    await until(`document.activeElement?.matches('.chat-title[tabindex="-1"]')`, 'title search root focus');
    assert(await evaluate(`document.querySelector('[data-branch-id="${rootId}"]')?.getAttribute('aria-current') === 'page'`), 'title search opened wrong branch');
    await evaluate(`[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='学习记录').click()`);
    await until(`!!document.querySelector('[data-screen="library"] input[type="search"]')`, 'library empty search');
    await input('[data-screen="library"] input[type="search"]', '');
    await click('[data-screen="library"] .topic-open');
    await until(`document.activeElement?.matches('.chat-title[tabindex="-1"]')`, 'empty search root focus');
    assert(await evaluate(`document.querySelector('[data-branch-id="${rootId}"]')?.getAttribute('aria-current') === 'page'`), 'empty search opened wrong branch');
    await click(`[data-branch-id="${child.id}"]`);
    await until(`!!document.querySelector('[data-branch-source]')`, 'child restored after search checks');
    console.log('BRANCH_SEARCH_FOCUS_OK windows=2 title_and_empty=root');
    console.log('BRANCH_SOURCE_SCROLL_OK windows=2');
    await input('.desktop-composer textarea', '子草稿重开标记');
    await waitDraft(child.id, '子草稿重开标记');
    await closeWindow();
    assert(!fs.readFileSync(database).includes(Buffer.from(secret)), 'plaintext key in database');
    service = createLearningService(options);
    await openWindow();
    await until(`!!document.querySelector('.recent-link')`, 'history reopen');
    await click('.recent-link');
    await until(`!!document.querySelector('[data-branch-id="${child.id}"]')`, 'branches reopen');
    await click(`[data-branch-id="${child.id}"]`);
    await until(`document.querySelector('.desktop-composer textarea')?.value === '子草稿重开标记' && document.body.innerText.includes('子答案唯一标记')`, 'child state reopen');
    current = await state();
    assert(current.topics[0].branches.length === 2 && current.settings.hasApiKey, 'reopened branch state');
    await closeWindow();
    const after = hashes();
    fs.writeFileSync(path.join(evidence, 'hashes-after.json'), JSON.stringify(after, null, 2));
    assert(JSON.stringify(before) === JSON.stringify(after), 'source files changed during test');
    fs.writeFileSync(path.join(evidence, 'result.json'), JSON.stringify({exitCode:0, sourceHashesStable:true, requests:contexts.length, windows:2}, null, 2));
    console.log('DESKTOP_BRANCH_LIVE_E2E_OK');
    console.log(`EVIDENCE_DIR ${evidence}`);
    app.exit(0);
  } catch (error) {
    if (win && !win.isDestroyed()) win.destroy();
    if (service) { try { service.close(); } catch {} }
    console.error('DESKTOP_BRANCH_LIVE_E2E_FAIL', error.message);
    app.exit(1);
  }
}).catch(() => { console.error('DESKTOP_BRANCH_LIVE_E2E_FAIL startup'); app.exit(1); });
