/** 一次性截图：Key 页单行表单（图标保存按钮）。 */
const PORT = 9222;
const FILE = 'file:///D:/Project/Android/Treelearn/Docs/04-ux-design/知芽-TreeLearn-设置页重设计-交互稿-V1.0.html';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let page = null;
for (let i = 0; i < 10 && !page; i++) {
  await sleep(500);
  const targets = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
  page = targets.find((t) => t.type === 'page');
}
if (!page) { console.error('FAILED: no page target'); process.exit(2); }
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
let id = 0; const pending = new Map();
ws.addEventListener('message', (e) => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); } });
const send = (method, params = {}) => new Promise((res) => { pending.set(++id, res); ws.send(JSON.stringify({ id, method, params })); });
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1200, height: 1100, deviceScaleFactor: 2, mobile: false });
await send('Page.navigate', { url: FILE });
await sleep(2500);
const evalJs = async (expr) => (await send('Runtime.evaluate', { expression: expr, returnByValue: true })).result.value;
await evalJs(`[...document.querySelectorAll('.set-link')].find(b => b.textContent.includes('API KEY'))?.click()`);
await sleep(600);
const clip = await evalJs(`(() => { const r = document.querySelector('.screen').getBoundingClientRect();
  return { x: r.x, y: r.y, width: r.width, height: r.height, scale: 2 }; })()`);
const s = await send('Page.captureScreenshot', { format: 'png', clip });
(await import('node:fs')).default.writeFileSync('artifacts/tmp/settings_redesign_keypage_v2.png', Buffer.from(s.data, 'base64'));
console.log('saved');
ws.close(); process.exit(0);
