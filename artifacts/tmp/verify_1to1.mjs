/**
 * 闭环验证：把高保真原型的对话页渲染出来，用 DOM 几何 + 计算样式量一遍，
 * 输出可与「设计截图像素实测」逐项对照的数字。
 *
 * 用法：node verify_1to1.mjs "file:///.../原型.html" [out.json]
 */
const PORT = 9222;
const [, , URL_RAW, OUT] = process.argv;
const URL_ = encodeURI(URL_RAW);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class CDP {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map();
    ws.addEventListener('message', (e) => {
      const m = JSON.parse(e.data);
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
      expression: `(()=>{ try { return JSON.stringify(${expr}); } catch(e){ return JSON.stringify({__err:String(e),stack:e.stack}); } })()`,
      returnByValue: true,
    });
    return r.result && r.result.value !== undefined ? JSON.parse(r.result.value) : undefined;
  }
  async waitFor(sel, timeoutMs = 45000) {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      if (await this.eval(`!!document.querySelector(${JSON.stringify(sel)})`)) return true;
      await sleep(300);
    }
    throw new Error('waitFor timeout: ' + sel);
  }
}

const PROBE = `(function(){
  var R = function(el){ if(!el) return null; var b=el.getBoundingClientRect();
    return {x:+b.x.toFixed(2), y:+b.y.toFixed(2), w:+b.width.toFixed(2), h:+b.height.toFixed(2), right:+b.right.toFixed(2), bottom:+b.bottom.toFixed(2)}; };
  var C = function(el, props){ if(!el) return null; var s=getComputedStyle(el), o={};
    props.forEach(function(p){ o[p]=s.getPropertyValue(p); }); return o; };
  var out = {};
  var scr = document.querySelector('.screen');
  out.viewport = { w: window.innerWidth, h: window.innerHeight, dpr: window.devicePixelRatio };
  out.screen = { rect: R(scr), css: C(scr, ['background-color','font-family']) };
  out.gridTexture = C(scr, ['background-image']).then ? null : C(scr, ['background-image'])['background-image'];

  /* 1. 用户气泡（取第一个） */
  var u = document.querySelector('.msg-user');
  out.userBubble = { rect: R(u), css: C(u, ['background-color','border-radius','padding-top','padding-bottom','padding-left','padding-right','font-size','line-height','max-width']) };
  if (u) out.userBubble.rightGapToScreen = +(R(scr).right - R(u).right).toFixed(2);

  /* 2. AI 标识行 + 正文 */
  var tag = document.querySelector('.ai-tag');
  out.aiTag = { rect: R(tag), css: C(tag, ['min-height','margin-bottom','font-size','color','display','align-items','line-height']) };
  var ai = document.querySelector('.msg-ai');
  out.aiMsg = { rect: R(ai), css: C(ai, ['font-family','font-size','line-height','color','background-color','border-left-width','padding-left']) };

  /* 0. 渲染偏移校正：无头浏览器的 .body-scroll 会占掉滚动条布局宽度（原型 ::-webkit-scrollbar 宽 4px），
     而截图（真机 overlay 滚动条）不占。因此渲染出来的所有「距屏右」间距都偏大这么多，
     比较前必须减去。左边不受影响，可作对照。 */
  var bs = document.querySelector('.body-scroll');
  out.scroll = { offsetWidth: bs ? bs.offsetWidth : null, clientWidth: bs ? bs.clientWidth : null,
                 scrollbarWidth: bs ? (bs.offsetWidth - bs.clientWidth) : 0 };
  out.scrollNote = '渲染值里的「距屏右」= 设计值 + scrollbarWidth；左侧内边距不受影响。';

  /* 锚点：前一条用户消息底 → AI 消息顶，应等于 .msg-flow 的 gap(--s6=24)。
     该锚点独立于 reasoning 是否存在，用来反推 .ai-tag 行盒高，进而定 margin-bottom。 */
  if (u && ai) {
    var ub = R(u), ab = R(ai);
    out.msgUserBottomToAiTop = +(ab.y - ub.bottom).toFixed(2);
    out.flowGapFromCss = C(ai.parentElement, ['gap','padding']).gap;
  }
  /* 推导：tag 行盒高(m) + margin-bottom(mb) + 正文行高/2(lh/2) = tag↔正文 中心距 */
  if (tag) {
    var th = R(tag).h, mb = parseFloat(C(tag, ['margin-bottom'])['margin-bottom']);
    var blh = document.querySelector('.sent') ? parseFloat(getComputedStyle(document.querySelector('.sent')).lineHeight) : 28;
    out.derived = { tagBoxH: th, marginBottom: mb, bodyLineHeight: blh,
                    centerGap: +((th/2) + mb + (blh/2)).toFixed(2) };
  }
  /* 说明：若这条 AI 消息带「思考过程」行（.reasoning-wrap），DOM 实测的 tag→正文 距离会把这行一并算进去；
     截图里的演示回答没有思考过程行，故两者不可比。可比的是 derived.centerGap——
     它由三个 DOM 实测值算出（tag 行盒高 / tag margin-bottom / 正文行高），不含 reasoning 行。 */
  out.aiTagHasReasoningRow = !!(ai && ai.querySelector('.reasoning-wrap'));
  /* 正文容器 = .msg-ai 下第一个不含 .ai-tag 且含文本的 div */
  var body = null, kids = ai ? ai.children : [];
  for (var i=0;i<kids.length;i++){ if (!kids[i].classList.contains('ai-tag') && !kids[i].classList.contains('reasoning-wrap') && kids[i].textContent.trim()) { body = kids[i]; break; } }
  out.aiBody = { rect: R(body), css: C(body, ['font-size','line-height','color']) };
  if (tag && body) {
    var t = R(tag), b = R(body);
    out.aiTagToBodyCenterGap = +(((b.y + b.h/2) - (t.y + t.h/2))).toFixed(2);
    out.aiTagBottomToBodyTop = +(b.y - t.bottom).toFixed(2);
  }
  /* 正文左边缘相对 screen */
  if (body) out.aiBodyLeftInset = +(R(body).x - R(scr).x).toFixed(2);

  /* 3. 动作行 */
  var acts = Array.from(document.querySelectorAll('.ai-actions .ai-act'));
  out.actions = acts.map(function(el){ return R(el); });
  out.actionCss = C(acts[0], ['width','height']);
  if (acts.length >= 2) {
    var c = acts.map(function(el){ var b=R(el); return b.x + b.w/2; });
    out.actionCenterPitch = c.slice(1).map(function(v,i){ return +(v-c[i]).toFixed(2); });
    out.firstActionCenterInset = +(c[0] - R(scr).x).toFixed(2);
  }
  var at = document.querySelector('.ai-time');
  out.aiTime = { rect: R(at), css: C(at, ['font-size','color']) };
  if (at) out.aiTimeRightGap = +(R(scr).right - R(at).right).toFixed(2);

  /* 4. 推荐问题胶囊 */
  var pills = Array.from(document.querySelectorAll('.sugg-item'));
  out.pills = pills.map(function(el){
    var b = R(el), cs = getComputedStyle(el);
    var txt = el.querySelector('.txt'), fk = el.querySelector('.sugg-fork');
    return { rect: b, css: { 'background-color': cs.backgroundColor, 'border-radius': cs.borderRadius },
             txtRect: R(txt), forkRect: R(fk),
             txtLeftInset: txt ? +(R(txt).x - b.x).toFixed(2) : null,
             forkCenterFromRight: fk ? +(b.right - (R(fk).x + R(fk).w/2)).toFixed(2) : null };
  });
  out.pillGaps = pills.slice(1).map(function(el,i){ return +(R(el).y - R(pills[i]).bottom).toFixed(2); });
  out.pillLeftInset = pills[0] ? +(R(pills[0]).x - R(scr).x).toFixed(2) : null;

  /* 5. 动作行 → 首个胶囊 的间距（设计截图判据：按钮盒底 → 胶囊顶 ≈ 16） */
  if (acts.length && pills.length) {
    var lastBtn = R(acts[0]); /* 按钮高 44，与 capsules 的关系只看 .sugg-block */
    var block = document.querySelector('.sugg-block');
    out.suggBlockCss = C(block, ['margin-top']);
    out.actionRowRect = R(document.querySelector('.ai-actions'));
    out.actionRowBottomToPillTop = +(R(pills[0]).y - R(document.querySelector('.ai-actions')).bottom).toFixed(2);
  }

  /* 6. token 取值（从 :root 读，确认 CSS 变量真实生效） */
  var rootStyle = getComputedStyle(document.documentElement);
  out.tokens = {};
  ['--green','--green-bubble','--green-deep','--bg','--ink','--ink3','--r-md','--r-sm','--s3','--s4','--fs-body','--fs-sm','--fs-cap','--lh-read'].forEach(function(k){
    out.tokens[k] = rootStyle.getPropertyValue(k).trim() || rootStyle.getPropertyValue(k);
  });
  /* 7. 已注册的字体面：判定"CSS 写了字体但实际静默回退 / 字重不对"。
     注意 fontFamily 是**声明值**，platformFonts 是**实际用于栅格化的字体**，两者都要看。
     坑：FontFaceSet 是 Set 语义（只有 size，没有 length），用 Array.prototype.forEach.call 迭代 0 次且不报错，
     必须走 Array.from 或它自己的 forEach。 */
  try {
    var seen = {}, faces = [];
    Array.from(document.fonts || []).forEach(function (f) {
      var k = f.family + '|' + f.weight + '|' + f.style;
      if (seen[k] || !/Noto Sans SC|Noto Serif SC|LXGW|Ma Shan/i.test(f.family)) return;
      seen[k] = 1; faces.push({ family: f.family, weight: f.weight, style: f.style, status: f.status });
    });
    out.fontFaces = faces;
  } catch (e) { out.fontFaces = String(e); }

  return out;
})()`;

(async () => {
  const tab = await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json());
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); });
  const cdp = new CDP(ws);
  await cdp.send('Page.enable');
  await cdp.send('Runtime.enable');
  await cdp.send('Page.navigate', { url: URL_ });
  await cdp.waitFor('.screen', 60000);
  await sleep(1200);
  for (const sel of ['.demo-link', '.topic-card']) {
    if (!(await cdp.eval(`!!document.querySelector(${JSON.stringify(sel)})`))) throw new Error('missing ' + sel);
    await cdp.eval(`(document.querySelector(${JSON.stringify(sel)}).click(), true)`);
    await sleep(900);
  }
  await cdp.waitFor('.msg-user', 20000);
  await sleep(1200);

  const data = await cdp.eval(PROBE);
  if (data && data.__err) throw new Error(data.__err + '\n' + data.stack);

  /* —— 平台字体实证 ——
     CSS 里写了 font-family 不等于真的用上了：字体缺失会静默回退，而回退栈末尾常挂 serif，
     于是"改了字体却看不出变化"。CSS.getPlatformFontsForNode 给出**实际参与栅格化的字体族**，
     这是唯一能证伪"静默回退"的读数。 */
  await cdp.send('DOM.enable');
  await cdp.send('CSS.enable');
  const doc = await cdp.send('DOM.getDocument', { depth: 1 });
  const fonts = {};
  for (const sel of ['.screen', '.msg-ai', '.msg-user', '.sugg-item', '.ai-tag', '.appbar-title .t1']) {
    const q = await cdp.send('DOM.querySelector', { nodeId: doc.root.nodeId, selector: sel });
    if (!q.nodeId) { fonts[sel] = null; continue; }
    const f = await cdp.send('CSS.getPlatformFontsForNode', { nodeId: q.nodeId });
    fonts[sel] = (f.fonts || []).map((x) => ({ family: x.familyName, glyphs: x.glyphCount, custom: !!x.isCustomFont }));
  }
  data.platformFonts = fonts;
  data.platformFontsNote = '实际参与栅格化的字体族（非 CSS 声明值）。若这里出现 serif/Songti 之类，说明声明字体缺失、发生了静默回退。';

  const text = JSON.stringify(data, null, 2);
  console.log(text);
  if (OUT) (await import('node:fs')).writeFileSync(OUT, text);
  ws.close();
  await fetch(`http://127.0.0.1:${PORT}/json/close/${tab.id}`).catch(() => {});
  process.exit(0);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
