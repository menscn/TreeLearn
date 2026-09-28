/**
 * 用 Edge headless + CDP（零依赖，Node 22 内置 fetch / WebSocket）
 * 打开高保真原型 → 点进对话页 → 对 .screen 元素做 2x 截图。
 *
 * 用法：node cdp_shot.mjs <file-url> <out.png> [clickSelector...]
 * 例：  node cdp_shot.mjs "file:///D:/.../原型.html" out.png ".demo-link" ".topic-card"
 *
 * 输出约定：PNG 以 .screen 元素左上角为原点、2x 缩放（412×816 逻辑 → 824×1632 物理）。
 * 布局宽度会被滚动条占掉 4px（原型 ::-webkit-scrollbar 宽 4px），故图中所有「距屏右」
 * 间距 = 设计值 + 4；左侧内边距不受影响，可作对照。
 */
const PORT = 9222;
const [, , URL_RAW, OUT, ...CLICKS] = process.argv;
if (!URL_RAW || !OUT) {
  console.error('usage: node cdp_shot.mjs <url> <out.png> [clickSelector...]');
  process.exit(2);
}
const URL_ = encodeURI(URL_RAW);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class CDP {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
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
    return new Promise((res, rej) => {
      this.pending.set(id, { res, rej });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }
  async eval(expr) {
    const r = await this.send('Runtime.evaluate', {
      expression: `(()=>{ try { return JSON.stringify(${expr}); } catch(e){ return JSON.stringify({__err:String(e)}); } })()`,
      returnByValue: true,
    });
    const v = r.result && r.result.value;
    return v === undefined ? undefined : JSON.parse(v);
  }
  async waitFor(sel, timeoutMs = 45000) {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      if (await this.eval(`!!document.querySelector(${JSON.stringify(sel)})`)) return true;
      await sleep(300);
    }
    throw new Error(`waitFor timeout: ${sel}`);
  }
}

(async () => {
  const newTab = await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json());
  const ws = new WebSocket(newTab.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); });
  const cdp = new CDP(ws);

  await cdp.send('Page.enable');
  await cdp.send('Runtime.enable');
  console.log('navigate ->', URL_);
  await cdp.send('Page.navigate', { url: URL_ });

  // 等 React + Babel 编译 + CDN 就绪
  await cdp.waitFor('.screen', 60000);
  console.log('原型已渲染 (.screen 出现)');
  await sleep(1200);

  for (const sel of CLICKS) {
    console.log('click', sel);
    const ok = await cdp.eval(`!!document.querySelector(${JSON.stringify(sel)})`);
    if (!ok) throw new Error(`点击目标不存在: ${sel}`);
    await cdp.eval(`(document.querySelector(${JSON.stringify(sel)}).click(), true)`);
    await sleep(900);
  }

  await cdp.waitFor('.msg-user', 20000);
  await sleep(1500); // 等入场动画结束

  const SCROLL = process.env.SCROLL || '';
  if (SCROLL === 'top') {
    await cdp.eval(`(document.querySelector('.body-scroll').scrollTop = 0, true)`);
    await sleep(700);
  } else if (SCROLL === 'bottom') {
    await cdp.eval(`(function(){var e=document.querySelector('.body-scroll'); e.scrollTop=e.scrollHeight; return true;})()`);
    await sleep(700);
  }

  const rect = await cdp.eval(
    `(function(){ var el=document.querySelector('.screen'); if(!el) return null; var r=el.getBoundingClientRect(); return {x:r.x,y:r.y,w:r.width,h:r.height}; })()`
  );
  console.log('screen rect =', JSON.stringify(rect));

  /* 必须 captureBeyondViewport:true + clip 用页面绝对坐标：
     .screen 高 816 > 视口可见高度时会超出视口，只有整页渲染才能截全。
     反例（已踩过）：改成 captureBeyondViewport:false 后 clip 被按页面坐标解释但只截视口，
     输出是页面顶部一片纯色，看起来"截到了"其实是废图——所以截完必须用测量脚本回读校验。 */
  const shot = await cdp.send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: true,
    clip: { x: rect.x, y: rect.y, width: rect.w, height: rect.h, scale: 2 },
  });
  const fs = await import('node:fs');
  fs.writeFileSync(OUT, Buffer.from(shot.data, 'base64'));
  console.log('saved', OUT);

  // 顺带把关键计算样式导出来，便于核对
  const styles = await cdp.eval(`(function(){
    var pick = function(sel, props){
      var el = document.querySelector(sel); if (!el) return {sel:sel, missing:true};
      var cs = getComputedStyle(el); var o = {sel:sel};
      props.forEach(function(p){ o[p] = cs.getPropertyValue(p); });
      return o;
    };
    return {
      user: pick('.msg-user', ['background-color','border-radius','padding','font-size','line-height','font-family']),
      aiTag: pick('.ai-tag', ['min-height','margin-bottom','font-size','color']),
      msgAi: pick('.msg-ai', ['font-family','font-size','line-height','color','border-left-width','background-color']),
      screen: pick('.screen', ['font-family','background-color']),
      sugg: pick('.sugg-item', ['background-color','border-radius','padding','font-size']),
      aiAct: pick('.ai-act', ['width','height'])
    };
  })()`);
  console.log('computed-styles:', JSON.stringify(styles, null, 1));

  ws.close();
  await fetch(`http://127.0.0.1:${PORT}/json/close/${newTab.id}`).catch(() => {});
  process.exit(0);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
