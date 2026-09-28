/**
 * 高亮/批注交互 Demo 的交付前验证（huashu-design §3 交付前真实点击测试）。
 * 零依赖：Edge headless + CDP（沿用 artifacts/tmp/cdp_shot.mjs 的通道）。
 * 通过 window.__demo 驱动与真实点击共用的 handler，断言 DOM 状态 + 收集页面异常。
 *
 * 前置：先启动 Edge headless --remote-debugging-port=9222
 * 用法：node verify_hldemo.mjs <file-url>
 */
const PORT = 9222;
const [, , URL_RAW] = process.argv;
if (!URL_RAW) { console.error('usage: node verify_hldemo.mjs <file-url>'); process.exit(2); }
const URL_ = encodeURI(URL_RAW);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

let exceptions = [];
class CDP {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map();
    ws.addEventListener('message', (e) => {
      const m = JSON.parse(e.data);
      if (m.method === 'Runtime.exceptionThrown') {
        exceptions.push(m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text);
      }
      if (m.id && this.pending.has(m.id)) {
        const { res, rej } = this.pending.get(m.id);
        this.pending.delete(m.id);
        m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result);
      }
    });
  }
  send(method, params = {}) {
    const id = ++this.id;
    return new Promise((res, rej) => { this.pending.set(id, { res, rej }); this.ws.send(JSON.stringify({ id, method, params })); });
  }
  async eval(expr) {
    const r = await this.send('Runtime.evaluate', {
      expression: `(()=>{ try { return JSON.stringify(${expr}); } catch(e){ return JSON.stringify({__err:String(e)}); } })()`,
      returnByValue: true,
    });
    const v = r.result && r.result.value;
    return v === undefined ? undefined : JSON.parse(v);
  }
  async waitFor(expr, timeoutMs = 60000, label = '') {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      if (await this.eval(expr)) return true;
      await sleep(300);
    }
    throw new Error(`waitFor timeout: ${label || expr}`);
  }
}

let PASS = 0, FAIL = 0;
function check(name, cond, detail) {
  if (cond) { PASS++; console.log('  PASS', name); }
  else { FAIL++; console.log('  FAIL', name, detail !== undefined ? JSON.stringify(detail) : ''); }
}

(async () => {
  const newTab = await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json());
  const ws = new WebSocket(newTab.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); });
  const cdp = new CDP(ws);
  await cdp.send('Page.enable');
  await cdp.send('Runtime.enable');
  await cdp.send('Page.navigate', { url: URL_ });
  await cdp.waitFor(`!!document.querySelector('.screen')`, 60000, 'React 渲染 .screen');
  await cdp.waitFor(`!!window.__demo`, 30000, '__demo 挂载');
  await sleep(800);

  console.log('== 1. 六格菜单：选中即弹，取消态关闭 ==');
  check('selectRange 打开菜单', await cdp.eval(`window.__demo.selectRange('p1', 0, 12)`) === true);
  let st = await cdp.eval(`window.__demo.state()`);
  check('菜单可见', st.menuOpen === true, st);
  check('非取消态', st.menuCancel === false);
  const cellCount = await cdp.eval(`document.querySelectorAll('.hl-menu .hl-cell').length`);
  check('六格图标', cellCount === 6, cellCount);
  const textBtns = await cdp.eval(`Array.from(document.querySelectorAll('.hl-menu button')).filter(b => b.textContent.trim() && !b.querySelector('svg')).length`);
  check('菜单内无文字按钮', textBtns === 0, textBtns);
  await cdp.eval(`window.__demo.tapMenu('highlight')`);
  await sleep(120);
  st = await cdp.eval(`window.__demo.state()`);
  check('高亮落库', st.highlights.length === 1 && st.highlights[0].blockId === 'p1' && st.highlights[0].start === 0, st.highlights);
  const marks = await cdp.eval(`document.querySelectorAll('mark.hl').length`);
  check('mark 渲染', marks === 1, marks);
  check('菜单已收', st.menuOpen === false);

  console.log('== 2. 二次点击：取消高亮态 ==');
  await cdp.eval(`window.__demo.tapMarkAt('p1', 0)`);
  await sleep(120);
  st = await cdp.eval(`window.__demo.state()`);
  check('菜单再现', st.menuOpen === true);
  check('切取消态', st.menuCancel === true);
  const cancelIcon = await cdp.eval(`!!document.querySelector('.hl-menu .hl-cell.cancel')`);
  check('取消格有专属样式/图标', cancelIcon === true);
  await cdp.eval(`window.__demo.tapMenu('highlight')`);
  await sleep(120);
  st = await cdp.eval(`window.__demo.state()`);
  check('高亮已移除', st.highlights.length === 0, st.highlights);

  console.log('== 3. 严格重合 + 相交合并 ==');
  await cdp.eval(`window.__demo.selectRange('p1', 0, 12)`);
  await cdp.eval(`window.__demo.tapMenu('highlight')`);
  await cdp.eval(`window.__demo.selectRange('p1', 8, 20)`);
  await cdp.eval(`window.__demo.tapMenu('highlight')`);
  await sleep(120);
  st = await cdp.eval(`window.__demo.state()`);
  check('相交合并为并区间', st.highlights.length === 1 && st.highlights[0].start === 0 && st.highlights[0].end === 20, st.highlights);
  await cdp.eval(`window.__demo.selectRange('p1', 0, 20)`);
  await sleep(80);
  st = await cdp.eval(`window.__demo.state()`);
  check('精确重合→取消态', st.menuCancel === true, st);
  await cdp.eval(`window.__demo.tapMenu('highlight')`);

  console.log('== 4. 批注：建立 / 严格重合转编辑 / 便签角标 ==');
  await cdp.eval(`window.__demo.selectRange('p1', 0, 12)`);
  await cdp.eval(`window.__demo.tapMenu('annotate')`);
  await sleep(120);
  st = await cdp.eval(`window.__demo.state()`);
  check('弹层打开', st.sheetOpen === true);
  await cdp.eval(`window.__demo.setNoteText('重点：光反应在类囊体')`);
  await cdp.eval(`window.__demo.saveSheet()`);
  await sleep(150);
  st = await cdp.eval(`window.__demo.state()`);
  check('批注落库', st.notes.length === 1, st.notes);
  const flagCount = await cdp.eval(`document.querySelectorAll('[data-note-flag]').length`);
  check('便签角标出现', flagCount === 1, flagCount);
  const flagInBlock = await cdp.eval(`(() => { const f=document.querySelector('[data-note-flag]'); const b=f.closest('[data-block]'); return b.dataset.block; })()`);
  check('角标锚在正确块', flagInBlock === 'p1', flagInBlock);
  // 严格重合：同区间再点批注 → 转编辑而非新建
  await cdp.eval(`window.__demo.selectRange('p1', 0, 12)`);
  await cdp.eval(`window.__demo.tapMenu('annotate')`);
  await sleep(120);
  st = await cdp.eval(`window.__demo.state()`);
  check('同区间转入编辑', st.sheetOpen === true && st.sheetEdit !== null && st.sheetText === '重点：光反应在类囊体', st);
  await cdp.eval(`window.__demo.cancelSheet()`);
  // 异区间允许第二条
  await cdp.eval(`window.__demo.selectRange('p1', 14, 24)`);
  await cdp.eval(`window.__demo.tapMenu('annotate')`);
  await cdp.eval(`window.__demo.setNoteText('第二条，与首条末尾相邻')`);
  await cdp.eval(`window.__demo.saveSheet()`);
  await sleep(150);
  st = await cdp.eval(`window.__demo.state()`);
  check('异区间允许第二条', st.notes.length === 2, st.notes);

  console.log('== 5. 横线椭圆列表（同锚点聚簇） ==');
  await cdp.eval(`window.__demo.selectRange('p1', 10, 24)`);
  await cdp.eval(`window.__demo.tapMenu('annotate')`);
  await cdp.eval(`window.__demo.setNoteText('第三条，与前两条末尾相邻')`);
  await cdp.eval(`window.__demo.saveSheet()`);
  await sleep(150);
  const notesNow = await cdp.eval(`window.__demo.state().notes`);
  const last = notesNow[notesNow.length - 1];
  await cdp.eval(`window.__demo.tapFlag('${last.id}')`);
  await sleep(150);
  st = await cdp.eval(`window.__demo.state()`);
  check('椭圆列表打开', st.ovalOpen === true, st);
  check('列出同锚点全部批注', st.ovalCount >= 2, st.ovalCount);
  const ovalRadius = await cdp.eval(`getComputedStyle(document.querySelector('.oval')).borderRadius`);
  check('椭圆(胶囊)圆角', ovalRadius.includes('99'), ovalRadius);
  await cdp.eval(`window.__demo.ovalPick('${last.id}')`);
  await sleep(120);
  st = await cdp.eval(`window.__demo.state()`);
  check('点条目进编辑', st.sheetOpen === true && st.sheetEdit === last.id, st);
  await cdp.eval(`window.__demo.cancelSheet()`);

  console.log('== 6. 表格单元格：同一套菜单 ==');
  await cdp.eval(`window.__demo.selectRange('tc0c1', 0, 4)`);
  await sleep(80);
  st = await cdp.eval(`window.__demo.state()`);
  check('单元格内弹菜单', st.menuOpen === true, st);
  await cdp.eval(`window.__demo.tapMenu('highlight')`);
  await sleep(120);
  const cellMark = await cdp.eval(`document.querySelectorAll('[data-block="tc0c1"] mark.hl').length`);
  check('单元格高亮渲染', cellMark === 1, cellMark);
  await cdp.eval(`window.__demo.selectRange('tc0c1', 0, 4)`);
  await sleep(80);
  st = await cdp.eval(`window.__demo.state()`);
  check('单元格取消态', st.menuCancel === true, st);
  await cdp.eval(`window.__demo.tapMenu('highlight')`);

  console.log('== 7. 黄色档位 ==');
  await cdp.eval(`window.__demo.selectRange('p2', 0, 6)`);
  await cdp.eval(`window.__demo.tapMenu('highlight')`);
  await cdp.eval(`window.__demo.setYellow('#FFE58F')`);
  await sleep(120);
  const markBg = await cdp.eval(`getComputedStyle(document.querySelector('mark.hl')).backgroundColor`);
  check('档位切换生效', markBg === 'rgb(255, 229, 143)', markBg);

  console.log('== 8. 拍板①：重新生成 → 整条清除 ==');
  await cdp.eval(`window.__demo.regen()`);
  await sleep(150);
  st = await cdp.eval(`window.__demo.state()`);
  check('高亮清空', st.highlights.length === 0, st.highlights);
  check('批注清空', st.notes.length === 0, st.notes);
  const flagAfter = await cdp.eval(`document.querySelectorAll('[data-note-flag]').length`);
  check('角标随之消失', flagAfter === 0, flagAfter);
  const textChanged = await cdp.eval(`document.querySelector('[data-block="p1"]').textContent.slice(0, 6)`);
  check('正文已替换', textChanged.startsWith('重新生成后'), textChanged);

  console.log('== 9. 页面异常 ==');
  check('零未捕获异常', exceptions.length === 0, exceptions);

  /* —— 截图留档（两态） —— */
  await cdp.eval(`window.__demo.regen()`); // 切回原文
  await cdp.eval(`window.__demo.selectRange('p1', 0, 12)`);
  await sleep(300);
  const rect = await cdp.eval(`(function(){ var el=document.querySelector('.screen'); var r=el.getBoundingClientRect(); return {x:r.x,y:r.y,w:r.width,h:r.height}; })()`);
  const fs = await import('node:fs');
  const shot = await cdp.send('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: true,
    clip: { x: rect.x, y: rect.y, width: rect.w, height: rect.h, scale: 2 },
  });
  fs.writeFileSync('D:/Project/Android/Treelearn/artifacts/tmp/hldemo-menu.png', Buffer.from(shot.data, 'base64'));
  await cdp.eval(`window.__demo.tapMenu('highlight')`);
  await cdp.eval(`window.__demo.selectRange('p1', 0, 12)`);
  await cdp.eval(`window.__demo.tapMenu('annotate')`);
  await cdp.eval(`window.__demo.setNoteText('重点：光反应在类囊体')`);
  await cdp.eval(`window.__demo.saveSheet()`);
  await sleep(400);
  const shot2 = await cdp.send('Page.captureScreenshot', {
    format: 'png', captureBeyondViewport: true,
    clip: { x: rect.x, y: rect.y, width: rect.w, height: rect.h, scale: 2 },
  });
  fs.writeFileSync('D:/Project/Android/Treelearn/artifacts/tmp/hldemo-note.png', Buffer.from(shot2.data, 'base64'));

  console.log(`\nRESULT: PASS=${PASS} FAIL=${FAIL} exceptions=${exceptions.length}`);
  ws.close();
  await fetch(`http://127.0.0.1:${PORT}/json/close/${newTab.id}`).catch(() => {});
  process.exit(FAIL === 0 && exceptions.length === 0 ? 0 : 1);
})().catch((e) => { console.error('FAILED:', e.message, e.cause ? '| cause: ' + (e.cause.message || e.cause.code) : ''); process.exit(1); });
