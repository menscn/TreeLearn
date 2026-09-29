/** 一次性截图：提供商管理 / 提供商编辑（新 Key 表单）/ 模型设置。 */
const PORT = 9222;
const FILE = 'file:///D:/Project/Android/Treelearn/Docs/04-ux-design/知芽-TreeLearn-设置页重设计-交互稿-V1.0.html';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
const page = list.find((t) => t.type === 'page');
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
const click = async (sel, text) => evalJs(text
  ? `[...document.querySelectorAll('${sel}')].find(b => b.textContent.includes('${text}'))?.click()`
  : `document.querySelector('${sel}')?.click()`);
const clip = await evalJs(`(() => { const r = document.querySelector('.screen').getBoundingClientRect();
  return { x: r.x, y: r.y, width: r.width, height: r.height, scale: 2 }; })()`);
const shot = async (name) => {
  const s = await send('Page.captureScreenshot', { format: 'png', clip });
  (await import('node:fs')).default.writeFileSync('artifacts/tmp/' + name, Buffer.from(s.data, 'base64'));
  console.log('saved ' + name);
};
await click('.set-link', '提供商管理'); await sleep(500); await shot('settings_redesign_providers.png');
await click('.prov-card'); await sleep(500); await shot('settings_redesign_provider_edit.png');
await click('.appbar .icon-btn'); await sleep(300);
await click('.appbar .icon-btn'); await sleep(300);
await click('.set-link', '模型设置'); await sleep(500); await shot('settings_redesign_models.png');
ws.close(); process.exit(0);
