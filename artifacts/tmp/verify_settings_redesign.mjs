/**
 * 一次性验证：设置页重设计交互稿。
 * 打开页面 → 收集 pageerror/console.error → 点击走查（进 Key 页 / 删除确认 / 保存）
 * → 根页与 Key 页各截一张 2x 图。
 * 用法：node verify_settings_redesign.mjs
 */
const PORT = 9222;
const FILE = 'file:///D:/Project/Android/Treelearn/Docs/04-ux-design/知芽-TreeLearn-设置页重设计-交互稿-V1.0.html';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class CDP {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map();
    ws.addEventListener('message', (e) => {
      const m = JSON.parse(e.data);
      if (m.id && this.pending.has(m.id)) {
        const { res, rej } = this.pending.get(m.id);
        this.pending.delete(m.id);
        m.error ? rej(new Error(m.error.message)) : res(m.result);
      } else if (m.method) this.onEvent(m);
    });
  }
  send(method, params = {}) {
    const id = ++this.id;
    return new Promise((res, rej) => {
      this.pending.set(id, { res, rej });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }
  onEvent(m) {
    if (m.method === 'Runtime.exceptionThrown')
      this.errors.push('PAGEERROR: ' + JSON.stringify(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text));
    if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error')
      this.errors.push('CONSOLE: ' + m.params.entry.text);
  }
}

const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const page = list.find((t) => t.type === 'page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
const cdp = new CDP(ws);
cdp.errors = [];

await cdp.send('Runtime.enable');
await cdp.send('Log.enable');
await cdp.send('Page.enable');
await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1200, height: 1100, deviceScaleFactor: 2, mobile: false });
await cdp.send('Page.navigate', { url: FILE });
await sleep(2500);

const evalJs = async (expr) => (await cdp.send('Runtime.evaluate', { expression: expr, returnByValue: true })).result.value;

// 1) 根页渲染检查
const rootCheck = await evalJs(`(() => {
  const links = [...document.querySelectorAll('.set-link .sl-t')].map(e => e.textContent);
  const chevs = document.querySelectorAll('.set-link .sl-c svg').length;
  return { title: document.querySelector('.appbar-title .t1')?.textContent, links, chevCount: chevs };
})()`);
console.log('ROOT:', JSON.stringify(rootCheck, null, 1));

// 2) 截根页
await cdp.send('Emulation.setVisibleSize', { width: 1200, height: 1100 });
const clip = await evalJs(`(() => { const r = document.querySelector('.screen').getBoundingClientRect();
  return { x: r.x, y: r.y, width: r.width, height: r.height, scale: 2 }; })()`);
const shot1 = await cdp.send('Page.captureScreenshot', { format: 'png', clip });
(await import('node:fs')).default.writeFileSync('artifacts/tmp/settings_redesign_root.png', Buffer.from(shot1.data, 'base64'));

// 3) 进 Key 页
await evalJs(`[...document.querySelectorAll('.set-link')].find(b => b.textContent.includes('API KEY')).click()`);
await sleep(600);
const keyCheck = await evalJs(`(() => ({
  title: document.querySelector('.appbar-title .t1')?.textContent,
  sections: [...document.querySelectorAll('.set-label')].map(e => e.textContent),
  saveBtn: document.querySelector('.btn-primary')?.textContent,
  dangerLink: document.querySelector('.danger-link')?.textContent,
  saveAlign: getComputedStyle(document.querySelector('.btn-primary').parentElement).justifyContent
}))()`);
console.log('KEYPAGE:', JSON.stringify(keyCheck, null, 1));

// 4) 删除确认弹层 → 确认 → 状态变未配置
await evalJs(`document.querySelector('.danger-link').click()`);
await sleep(500);
const sheetCheck = await evalJs(`(() => ({
  h3: document.querySelector('.sheet h3')?.textContent,
  btns: [...document.querySelectorAll('.sheet-actions button')].map(b => b.textContent)
}))()`);
console.log('SHEET:', JSON.stringify(sheetCheck, null, 1));
await evalJs(`[...document.querySelectorAll('.sheet-actions button')].find(b => b.textContent.includes('确认删除')).click()`);
await sleep(600);
const afterDel = await evalJs(`(() => ({
  statePill: document.querySelector('.key-none')?.textContent,
  toast: document.querySelector('.toast')?.textContent,
  dangerGone: !document.querySelector('.danger-link')
}))()`);
console.log('AFTER_DELETE:', JSON.stringify(afterDel, null, 1));

// 5) 保存 → 变可用
await evalJs(`const i = document.getElementById('key-input'); i.value = 'sk-test-demo-1234567890';`);
await evalJs(`document.querySelector('.btn-primary').click()`);
await sleep(600);
const afterSave = await evalJs(`(() => ({
  statePill: document.querySelector('.key-ok')?.textContent,
  mask: document.querySelector('.set-card .sl-t')?.textContent || [...document.querySelectorAll('.set-card span')].map(s=>s.textContent).find(t=>t.includes('sk-'))
}))()`);
console.log('AFTER_SAVE:', JSON.stringify(afterSave, null, 1));

// 6) 截 Key 页（可用态）
const shot2 = await cdp.send('Page.captureScreenshot', { format: 'png', clip });
(await import('node:fs')).default.writeFileSync('artifacts/tmp/settings_redesign_keypage.png', Buffer.from(shot2.data, 'base64'));

// 7) 回根页看副标题联动
await evalJs(`document.querySelector('.appbar .icon-btn').click()`);
await sleep(600);
const backCheck = await evalJs(`(() => ({
  keyRowSub: [...document.querySelectorAll('.set-link .sl-s')].map(e => e.textContent).find(t => t.includes('sk-')),
  toast: document.querySelector('.toast')?.textContent
}))()`);
console.log('BACK_ROOT:', JSON.stringify(backCheck, null, 1));

console.log('ERRORS:', cdp.errors.length === 0 ? '0 (clean)' : JSON.stringify(cdp.errors, null, 1));
console.log('EXITCODE=' + (cdp.errors.length === 0 ? 0 : 1));
ws.close();
process.exit(0);
