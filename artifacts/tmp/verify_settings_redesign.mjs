/**
 * 一次性验证：设置页重设计交互稿 V1.1（全设置段）。
 * 走查：根页 → Key 页三态 → 提供商管理/编辑（新 Key 表单层级）→ 模型选择器
 *      → 搜索引擎 Key 表单 → 文件孤儿清理；全程收集 pageerror/console.error。
 * 用法：node verify_settings_redesign.mjs
 */
const PORT = 9222;
const FILE = 'file:///D:/Project/Android/Treelearn/Docs/04-ux-design/知芽-TreeLearn-设置页重设计-交互稿-V1.0.html';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class CDP {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map(); this.errors = [];
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

let page = null;
for (let i = 0; i < 10 && !page; i++) {
  await sleep(500);
  const targets = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
  page = targets.find((t) => t.type === 'page');
}
if (!page) { console.error('FAILED: no page target'); process.exit(2); }
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
const cdp = new CDP(ws);
await cdp.send('Runtime.enable');
await cdp.send('Log.enable');
await cdp.send('Page.enable');
await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1200, height: 1100, deviceScaleFactor: 2, mobile: false });
await cdp.send('Page.navigate', { url: FILE });
await sleep(2500);

const evalJs = async (expr) => (await cdp.send('Runtime.evaluate', { expression: expr, returnByValue: true })).result.value;
const clickText = async (sel, text) => evalJs(
  `[...document.querySelectorAll('${sel}')].find(b => b.textContent.includes('${text}'))?.click()`);
const clickFirst = async (sel) => evalJs(`document.querySelector('${sel}')?.click()`);

const results = [];
const check = (name, ok, detail) => { results.push({ name, ok, detail }); console.log((ok ? 'PASS' : 'FAIL') + ' | ' + name + ' | ' + detail); };

// 1) 根页
const root = await evalJs(`(() => ({
  title: document.querySelector('.appbar-title .t1')?.textContent,
  links: [...document.querySelectorAll('.set-link .sl-t')].map(e => e.textContent),
  chevs: document.querySelectorAll('.set-link .sl-c svg').length,
  provSub: [...document.querySelectorAll('.set-link .sl-s')].map(e => e.textContent).find(t => t.includes('已启用'))
}))()`);
check('根页分组列表', root.title === '设置' && root.links.length === 6 && root.chevs === 6,
  `${root.links.length} 行 / ${root.chevs} 个› / 提供商副标题「${root.provSub}」`);

// 2) Key 页（输入框+图标按钮同行 + 删除文字链 + 删除流程）
await clickText('.set-link', 'API KEY');
await sleep(500);
const kp = await evalJs(`(() => {
  const row = document.querySelector('.key-input-row');
  const input = row?.querySelector('input');
  const btn = row?.querySelector('.save-ico');
  const sameRow = input && btn && Math.abs(input.getBoundingClientRect().top - btn.getBoundingClientRect().top) < 6;
  return { hasRow: !!row, hasIco: !!btn, sameRow, danger: document.querySelector('.danger-link')?.textContent };
})()`);
check('Key 页单行表单 + 图标按钮', kp.hasRow && kp.hasIco && kp.sameRow && kp.danger?.includes('删除此 Key'),
  `sameRow=${kp.sameRow} / danger=${kp.danger}`);
await clickFirst('.danger-link');
await sleep(400);
await clickText('.sheet-actions button', '确认删除');
await sleep(500);
const kpDel = await evalJs(`(() => ({
  pill: document.querySelector('.key-none')?.textContent,
  dangerGone: !document.querySelector('.danger-link')
}))()`);
check('Key 删除流程', kpDel.pill === '发送将被阻止' && kpDel.dangerGone, `pill=${kpDel.pill}`);
await evalJs(`const i=document.getElementById('key-input'); i.value='sk-test-demo-1234567890';`);
await clickFirst('.save-ico');
await sleep(500);
const kpSave = await evalJs(`document.querySelector('.key-ok')?.textContent`);
check('Key 保存流程（图标按钮）', kpSave === '可用', `pill=${kpSave}`);

// 3) 提供商管理：开关 + 进入编辑页
await clickText('.appbar .icon-btn + .appbar-title', '设置'); // no-op safeguard
await evalJs(`[...document.querySelectorAll('.appbar .icon-btn')][0].click()`); // back to settings
await sleep(400);
await clickText('.set-link', '提供商管理');
await sleep(500);
const provs = await evalJs(`(() => ({
  cards: document.querySelectorAll('.prov-card').length,
  onSwitches: document.querySelectorAll('.switch.on').length,
  badge: document.querySelector('.pv-badge')?.textContent
}))()`);
check('提供商管理页', provs.cards === 3 && provs.onSwitches === 2, `${provs.cards} 卡 / ${provs.onSwitches} 开`);
await evalJs(`document.querySelectorAll('.switch')[2].click()`); // 启用 AiHubMix
await sleep(300);
const onAfter = await evalJs(`document.querySelectorAll('.switch.on').length`);
check('提供商开关切换', onAfter === 3, `on=${onAfter}`);
await clickFirst('.prov-card');
await sleep(500);
const pe = await evalJs(`(() => {
  const row = document.querySelector('.key-input-row');
  const input = row?.querySelector('input');
  const btn = row?.querySelector('.save-ico');
  return {
    title: document.querySelector('.appbar-title .t1')?.textContent,
    curKey: [...document.querySelectorAll('.form-label')].map(e => e.textContent),
    sameRow: input && btn && Math.abs(input.getBoundingClientRect().top - btn.getBoundingClientRect().top) < 6,
    danger: document.querySelector('.danger-link')?.textContent
  };
})()`);
check('提供商编辑页 Key 表单（新层级）', pe.title === 'DeepSeek' && pe.curKey.includes('当前 KEY') &&
  pe.curKey.includes('替换 KEY') && pe.sameRow && pe.danger?.includes('删除此 Key'),
  `sections=${pe.curKey.join('/')} sameRow=${pe.sameRow}`);

// 4) 模型设置：思考预算选择器
await evalJs(`[...document.querySelectorAll('.appbar .icon-btn')][0].click()`); // back -> providers
await sleep(300);
await evalJs(`[...document.querySelectorAll('.appbar .icon-btn')][0].click()`); // back -> settings
await sleep(300);
await clickText('.set-link', '模型设置');
await sleep(500);
await clickText('.set-link', '思考预算');
await sleep(400);
await clickText('.sheet .set-link', '高');
await sleep(400);
const think = await evalJs(`document.querySelector('.model-pill')?.textContent`);
check('思考预算选择器', think === '高', `pill=${think}`);

// 5) 搜索服务 → 引擎详情（新 Key 表单层级）
await evalJs(`[...document.querySelectorAll('.appbar .icon-btn')][0].click()`);
await sleep(300);
await clickText('.set-link', '搜索服务');
await sleep(500);
await clickFirst('.prov-card');
await sleep(500);
const se = await evalJs(`(() => {
  const row = document.querySelector('.key-input-row');
  const input = row?.querySelector('input');
  const btn = row?.querySelector('.save-ico');
  return {
    title: document.querySelector('.appbar-title .t1')?.textContent,
    sameRow: input && btn && Math.abs(input.getBoundingClientRect().top - btn.getBoundingClientRect().top) < 6,
    danger: document.querySelector('.danger-link')?.textContent
  };
})()`);
check('引擎详情页 Key 表单（新层级）', se.title === '智谱 AI 搜索' && se.sameRow && se.danger?.includes('删除此 Key'),
  `title=${se.title} sameRow=${se.sameRow}`);

// 6) 文件管理：孤儿清理
await evalJs(`[...document.querySelectorAll('.appbar .icon-btn')][0].click()`);
await sleep(300);
await evalJs(`[...document.querySelectorAll('.appbar .icon-btn')][0].click()`);
await sleep(300);
await clickText('.set-link', '文件管理');
await sleep(500);
const files0 = await evalJs(`(() => ({
  rows: document.querySelectorAll('.file-row').length,
  orphanSub: [...document.querySelectorAll('.set-link .sl-s')].map(e => e.textContent).find(t => t.includes('可清理') || t.includes('引用'))
}))()`);
await clickText('.set-link', '清理孤儿');
await sleep(400);
await clickText('.sheet-actions button', '确认清理');
await sleep(500);
const files1 = await evalJs(`document.querySelectorAll('.file-row').length`);
check('文件管理孤儿清理', files0.rows === 4 && files1 === 2, `4 行 → 清理后 ${files1} 行`);

// 7) 全程错误
check('页面零错误', cdp.errors.length === 0, cdp.errors.length ? JSON.stringify(cdp.errors).slice(0, 400) : 'pageerror=0');

const failed = results.filter(r => !r.ok);
console.log('---');
console.log('EXITCODE=' + (failed.length === 0 ? 0 : 1));
ws.close();
process.exit(0);
