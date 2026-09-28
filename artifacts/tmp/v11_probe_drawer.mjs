/**
 * 抽屉定点诊断：为什么抽屉打开后无法关闭 / 内部点击无效？
 * 用法：node v11_probe_drawer.mjs <file-url>
 */
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const PORT = 9223;
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const [, , URL_RAW] = process.argv;
const URL_ = encodeURI(URL_RAW);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'edge-v11p-'));
const edge = spawn(EDGE, ['--headless=new', '--disable-gpu', '--no-first-run',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`, '--window-size=1400,1200', 'about:blank'], { stdio: 'ignore' });

async function waitPort(t = 30000) {
  const t0 = Date.now();
  while (Date.now() - t0 < t) { try { const r = await fetch(`http://127.0.0.1:${PORT}/json/version`); if (r.ok) return 1; } catch {} await sleep(400); }
  throw new Error('port');
}
class CDP {
  constructor(ws) { this.ws = ws; this.id = 0; this.p = new Map(); ws.addEventListener('message', (e) => { const m = JSON.parse(e.data); if (m.id && this.p.has(m.id)) { const { res, rej } = this.p.get(m.id); this.p.delete(m.id); m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result); } }); }
  send(method, params = {}) { const id = ++this.id; return new Promise((res, rej) => { this.p.set(id, { res, rej }); this.ws.send(JSON.stringify({ id, method, params })); }); }
  async eval(expr) {
    const r = await this.send('Runtime.evaluate', { expression: `(()=>{try{return JSON.stringify(${expr})}catch(e){return JSON.stringify({__err:String(e)})}})()`, returnByValue: true, awaitPromise: true });
    const v = r.result && r.result.value; return v === undefined ? undefined : JSON.parse(v);
  }
}
const Q = (name, expr) => `console.log(name)`; // 占位

(async () => {
  await waitPort();
  const tab = await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json());
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); });
  const cdp = new CDP(ws);
  await cdp.send('Page.enable'); await cdp.send('Runtime.enable');
  await cdp.send('Page.navigate', { url: URL_ });
  await sleep(6000);

  const out = {};
  // 载入演示数据 → 开抽屉
  await cdp.eval(`(function(){var e=document.querySelector('.demo-link'); if(e) e.click(); return true;})()`);
  await sleep(900);
  await cdp.eval(`(function(){var e=document.querySelector('#phone-content .appbar .icon-btn'); if(e) e.click(); return true;})()`);
  await sleep(900);

  out.drawerCount = await cdp.eval(`document.querySelectorAll('.drawer').length`);
  out.maskCount = await cdp.eval(`document.querySelectorAll('.drawer-mask').length`);
  out.drawerHTML = await cdp.eval(`(function(){var e=document.querySelector('.drawer'); return e? e.outerHTML.slice(0,260):null;})()`);
  out.drawerStyle = await cdp.eval(`(function(){var e=document.querySelector('.drawer'); if(!e) return null; var c=getComputedStyle(e); return {pe:c.pointerEvents,z:c.zIndex,tr:c.transform,vis:c.visibility,disp:c.display,op:c.opacity,pos:c.position,w:c.width};})()`);
  out.maskStyle = await cdp.eval(`(function(){var e=document.querySelector('.drawer-mask'); if(!e) return null; var c=getComputedStyle(e); return {pe:c.pointerEvents,z:c.zIndex,pos:c.position};})()`);
  out.heatFoot = await cdp.eval(`(function(){var e=document.querySelectorAll('.heat-foot'); return {n:e.length, txt:e.length? e[0].textContent.slice(0,60):null, kids:e.length? e[0].children.length:null};})()`);
  out.inert = await cdp.eval(`(function(){var e=document.querySelector('.drawer'); return e? {inert:e.hasAttribute('inert'), ariaHidden:e.getAttribute('aria-hidden')}:null;})()`);

  // 关抽屉：点 close 按钮
  out.clickClose = await cdp.eval(`(function(){var b=document.querySelector('.drawer-head .icon-btn'); if(!b) return {ok:false,reason:'no close btn'}; b.click(); return {ok:true,cls:String(b.className)};})()`);
  await sleep(800);
  out.drawerCountAfterClose = await cdp.eval(`document.querySelectorAll('.drawer').length`);

  // 再开一次，点遮罩
  await cdp.eval(`(function(){var e=document.querySelector('#phone-content .appbar .icon-btn'); if(e) e.click(); return true;})()`);
  await sleep(700);
  const before = await cdp.eval(`document.querySelectorAll('.drawer').length`);
  await cdp.eval(`(function(){var e=document.querySelector('.drawer-mask'); if(e) e.click(); return true;})()`);
  await sleep(800);
  out.maskCloseWorks = { before, after: await cdp.eval(`document.querySelectorAll('.drawer').length`) };

  // 再开一次，直接点 heat-card 容器
  await cdp.eval(`(function(){var e=document.querySelector('#phone-content .appbar .icon-btn'); if(e) e.click(); return true;})()`);
  await sleep(700);
  out.clickHeatBlock = await cdp.eval(`(function(){var e=document.querySelector('.drawer-block.tap'); if(!e) return {ok:false,reason:'no .drawer-block.tap'}; e.click(); return {ok:true};})()`);
  await sleep(900);
  out.afterHeatBlockClick = {
    drawerCount: await cdp.eval(`document.querySelectorAll('.drawer').length`),
    metrics: await cdp.eval(`document.querySelectorAll('.metric').length`),
    screenKey: await cdp.eval(`(function(){var s=document.querySelector('.screen'); return s?s.getAttribute('key'):null;})()`),
  };

  console.log(JSON.stringify(out, null, 1));
  ws.close();
  try { edge.kill(); } catch {}
  process.exit(0);
})().catch((e) => { console.error('FAILED', e.message); try { edge.kill(); } catch {} process.exit(1); });
