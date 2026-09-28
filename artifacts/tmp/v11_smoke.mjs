/**
 * V1.1 原型冒烟测试：Edge headless + CDP（零依赖）
 * 1) 捕获页面异常 / console error（pageerror 等价物）
 * 2) 按走查路径逐屏点击，对 .screen 做 1x 截图
 * 3) 输出每步 PASS/FAIL + 结构探针（元素计数）
 *
 * 用法：node v11_smoke.mjs <file-url> [outDir]
 */
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const PORT = 9222;
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const [, , URL_RAW, OUTDIR = 'artifacts/tmp/v11_shots'] = process.argv;
if (!URL_RAW) { console.error('usage: node v11_smoke.mjs <url> [outDir]'); process.exit(2); }
const URL_ = encodeURI(URL_RAW);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

fs.mkdirSync(OUTDIR, { recursive: true });
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'edge-v11-'));
const errors = [];   // 模块级：失败路径也要能打印

const edge = spawn(EDGE, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--window-size=1400,1200', 'about:blank',
], { stdio: 'ignore' });

async function waitPort(timeoutMs = 30000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    try { const r = await fetch(`http://127.0.0.1:${PORT}/json/version`); if (r.ok) return await r.json(); } catch {}
    await sleep(400);
  }
  throw new Error('Edge 调试端口未就绪');
}

class CDP {
  constructor(ws) {
    this.ws = ws; this.id = 0; this.pending = new Map(); this.listeners = [];
    ws.addEventListener('message', (e) => {
      const m = JSON.parse(e.data);
      if (m.id && this.pending.has(m.id)) {
        const { res, rej } = this.pending.get(m.id); this.pending.delete(m.id);
        m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result);
      } else if (m.method) { this.listeners.forEach((f) => f(m)); }
    });
  }
  on(f) { this.listeners.push(f); }
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
      returnByValue: true, awaitPromise: true,
    });
    const v = r.result && r.result.value;
    const out = v === undefined ? undefined : JSON.parse(v);
    if (out && out.__err) throw new Error('页内异常: ' + out.__err);
    if (out && out.ok === false) throw new Error('点击失败: ' + (out.reason || '未知'));
    return out;
  }
  async has(sel) { return !!(await this.eval(`!!document.querySelector(${JSON.stringify(sel)})`)); }
  async waitFor(sel, timeoutMs = 10000) {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) { if (await this.has(sel)) return true; await sleep(250); }
    throw new Error('等待元素超时: ' + sel);
  }
}

/* ---------- 页内工具 ---------- */
const HELPERS = `(function(){
window.__t = {
  find(txt, sel){
    var list = document.querySelectorAll(sel || '*');
    for (var i=0;i<list.length;i++){
      var el = list[i];
      var leaf = el.children.length === 0 || el.tagName === 'BUTTON' ||
                 el.classList.contains('tab') || el.classList.contains('set-link') ||
                 el.classList.contains('prov-card') || el.classList.contains('topic-card');
      if (leaf && (el.textContent||'').indexOf(txt) >= 0) return el;
    }
    return null;
  },
  clickText(txt, sel){
    var el = window.__t.find(txt, sel);
    if (!el) return {ok:false, reason:'未找到文本「'+txt+'」in '+sel};
    var r = el.getBoundingClientRect();
    if (r.width===0 && r.height===0) return {ok:false, reason:'零尺寸: '+txt};
    el.click(); return {ok:true, cls:String(el.className).slice(0,60)};
  },
  clickSel(sel, idx){
    var el = document.querySelectorAll(sel)[idx||0];
    if (!el) return {ok:false, reason:'未找到 '+sel};
    el.click(); return {ok:true, cls:String(el.className).slice(0,60)};
  },
  probe(){
    var q = function(s){ return document.querySelectorAll(s).length; };
    var scr = document.querySelector('.screen');
    return {
      hasTabbar: !!document.querySelector('.tabbar'),
      screenKey: scr ? (scr.getAttribute('key')||'') : '',
      drawer: !!document.querySelector('.drawer'),
      heatCells: q('.heat-cell'),
      heatFilled: q('.heat-cell[class*="l1"]') + q('.heat-cell[class*="l2"]') + q('.heat-cell[class*="l3"]'),
      setLinks: q('.set-link'), metrics: q('.metric'),
      provCards: q('.prov-card'), modelPills: q('.model-pill'),
      fileRows: q('.file-row'), attachRows: q('.attach-row'),
      topicCards: q('.topic-card'), msgAi: q('.msg-ai'),
      srcCard: !!document.querySelector('.src-card'), srcItems: q('.src-item'),
      sendFab: !!document.querySelector('.send-fab'),
      tabText: Array.prototype.map.call(document.querySelectorAll('.tab'), function(e){return e.textContent.trim();}).join('/'),
      text: (scr?scr.innerText:'').slice(0,110).replace(/\\n/g,' | '),
      tabbar: (function(){ var t=document.querySelector('.tabbar'); if(!t) return null;
        var c=getComputedStyle(t), r=t.getBoundingClientRect();
        return {display:c.display, w:Math.round(r.width), h:Math.round(r.height), tabsW:Array.prototype.map.call(t.querySelectorAll('.tab'),function(x){return Math.round(x.getBoundingClientRect().width);})}; })()
    };
  }
};
return true; })()`;

(async () => {
  const info = await waitPort();
  console.log('Edge:', info.Browser);
  const tab = await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: 'PUT' }).then((r) => r.json());
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((res, rej) => { ws.addEventListener('open', res); ws.addEventListener('error', rej); });
  const cdp = new CDP(ws);

  cdp.on((m) => {
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails;
      errors.push('EXCEPTION: ' + (d.exception?.description || d.text).split('\n')[0]);
    } else if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
      errors.push('CONSOLE.error: ' + m.params.args.map((a) => a.description || a.value).join(' ').split('\n')[0]);
    } else if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
      errors.push('LOG.error: ' + m.params.entry.text);
    }
  });

  await cdp.send('Page.enable');
  await cdp.send('Runtime.enable');
  await cdp.send('Log.enable');
  console.log('navigate ->', URL_);
  await cdp.send('Page.navigate', { url: URL_ });
  await cdp.waitFor('.screen', 60000);
  await sleep(1700);
  await cdp.eval(HELPERS);

  // guide 结构自检（左侧走查清单：V1.1 段 9 条 + 基线段 17 条）
  const guide = await cdp.eval(`(function(){
    var g = document.querySelector('.guide');
    if (!g) return {missing:true};
    var ols = g.querySelectorAll('ol');
    return {
      sub: (g.querySelector('.guide-sub')||{}).textContent,
      h3: Array.prototype.map.call(g.querySelectorAll('h3'), function(e){return e.textContent;}),
      olCount: ols.length,
      liPerOl: Array.prototype.map.call(ols, function(e){return e.querySelectorAll('li').length;}),
      legend: g.querySelectorAll('.legend .k').length
    };
  })()`);
  console.log('guide 自检:', JSON.stringify(guide));

  const results = [];
  async function shot(name) {
    const rect = await cdp.eval(`(function(){var el=document.querySelector('.screen');var r=el.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height};})()`);
    const s = await cdp.send('Page.captureScreenshot', {
      format: 'png', captureBeyondViewport: true,
      clip: { x: rect.x, y: rect.y, width: rect.w, height: rect.h, scale: 1 },
    });
    fs.writeFileSync(path.join(OUTDIR, name + '.png'), Buffer.from(s.data, 'base64'));
  }
  /** 先执行动作，再等目标元素出现；可选 assert(probe) 做硬断言 */
  async function step(name, actions = [], { sel = null, wait = 600, timeout = 9000, assert = null } = {}) {
    try {
      for (const a of actions) { await cdp.eval(a); await sleep(260); }
      if (sel) await cdp.waitFor(sel, timeout);
      await sleep(wait);
      await shot(name);
      const probe = await cdp.eval('window.__t.probe()');
      if (assert && !assert(probe)) throw new Error('断言失败（见 probe）');
      results.push({ name, ok: true, probe });
      console.log(`PASS ${name.padEnd(20)} key=${probe.screenKey.padEnd(14)} tabbar=${probe.hasTabbar?'Y':'n'} text="${probe.text}"`);
      return probe;
    } catch (e) {
      await shot(name + '_FAIL').catch(() => {});
      results.push({ name, ok: false, error: e.message });
      console.log(`FAIL ${name.padEnd(20)} ${e.message}`);
      return null;
    }
  }
  const click = (...a) => `window.__t.clickSel(${JSON.stringify(a[0])}${a[1] !== undefined ? ',' + a[1] : ''})`;
  const ctext = (t, s) => `window.__t.clickText(${JSON.stringify(t)}, ${JSON.stringify(s)})`;
  /** 回到任意一级 tab 屏（子屏靠 appbar 返回键逐级退） */
  async function toRoot(maxBack = 6) {
    for (let i = 0; i < maxBack; i++) {
      if (await cdp.has('.tabbar')) return true;
      await cdp.eval(click('.appbar .icon-btn', 0));
      await sleep(450);
    }
    return await cdp.has('.tabbar');
  }
  async function gotoLink(text) {
    const root = await toRoot();
    if (!root) throw new Error('无法回到根屏（.tabbar 未出现）');
    await cdp.eval(ctext('设置', '.tab')); await sleep(600);
    await cdp.eval(ctext(text, '.set-link'));
  }

  const SEQ = [
    // 底栏必须是横向铺满的 flex 条（回归守卫：曾因注释里 --s*/ 提前闭合注释导致整条规则被解析器丢弃）
    ['00_home',          [], { sel: '.tabbar', assert: (p) => p.tabbar && p.tabbar.display === 'flex' && p.tabbar.w > 300 && p.tabbar.tabsW.every((w) => w > 80) }],
    // 2026-09-27 起默认载入演示数据：首页开箱即有历史主题；演示 Key 也已预置
    ['01_demo_loaded',   [], { wait: 800 }],
    ['02_drawer',        [click('#phone-content .appbar .icon-btn')], { sel: '.drawer' }],
    // 热力卡整块可点（onClick 挂在 .drawer-block.tap 上），点它进入统计详情并自动关抽屉
    ['03_stats',         [click('.drawer-block.tap')], { sel: '.metric' }],
    ['04_home',          [click('.appbar .icon-btn')], { sel: '.tabbar' }],
    ['05_library',       [ctext('学习库', '.tab')], { sel: '.topic-card' }],
    ['06_library_done',  [ctext('已完成', '.lib-seg button')], { wait: 700 }],
    // 设置页（演示 Key 已预置，这里只核设置屏本身）
    ['06b_settings',     [ctext('设置', '.tab')], { sel: '.set-link', wait: 700 }],
    ['07_providers',     [() => gotoLink('提供商管理')], { sel: '.prov-card' }],
    ['08_providerEdit',  [click('.prov-card')], { sel: '.form-row, .key-input-row' }],
    ['09_pull_fail',     [ctext('拉取模型', 'button')], { wait: 2000 }],
    ['10_modelSettings', [() => gotoLink('模型设置')], { sel: '.model-pill' }],
    ['11_searchServices',[() => gotoLink('搜索服务')], { sel: '.prov-card' }],
    ['12_searchEngine',  [click('.prov-card')], { sel: '.key-input-row' }],
    ['13_voice',         [() => gotoLink('语音')], { sel: '.set-link' }],
    ['14_files',         [() => gotoLink('文件管理')], { sel: '.file-row' }],
    ['15_chat',          [() => toRoot(), ctext('主页', '.tab'), click('.topic-card')], { sel: '.msg-ai', wait: 1800 }],
    // 联网 chip 打开的是底部面板（SPEC 设计），开关在面板里
    ['16_search_panel',  [click('.tool-chip', 1)], { sel: '.sheet .switch', wait: 800 }],
    ['17_search_on',     [click('.sheet .switch')], { sel: '.tool-chip.on', wait: 600 }],
    ['18_sheet_close',   [ctext('关闭', '.sheet-actions button')], { wait: 700 }],
    // 点推荐问题直接发出 → 生成中，发送键应变为方形停止钮（三态共位）
    ['19_generating',    [() => cdp.waitFor('.sugg-item .txt', 15000), click('.sugg-item .txt')], { sel: '.send-fab.stop', wait: 600, timeout: 20000 }],
    // 检索中态 → 有结果的来源卡
    ['20_src_card',      [], { sel: '.src-card', wait: 2200, timeout: 25000 }],
    // 再发一条并立刻停止，验证停止态可退出
    // 再发一条并立刻停止：必须先等上一条生成结束（推荐问题只挂最新回答）
    ['21_stop_send',     [() => cdp.waitFor('.send-fab:not(.stop)', 40000), () => cdp.waitFor('.sugg-item .txt', 25000), click('.sugg-item .txt')], { sel: '.send-fab.stop', wait: 500, timeout: 20000 }],
    ['22_stop_done',     [click('.send-fab.stop')], { sel: '.send-fab:not(.stop)', wait: 600, timeout: 8000 }],
  ];
  for (const [name, actions, opt] of SEQ) {
    const fns = actions.filter((a) => typeof a === 'function');
    const acts = actions.filter((a) => typeof a !== 'function');
    let failed = null;
    for (const fn of fns) {
      try { const r = await fn(); if (r === false) throw new Error('前置动作返回失败'); }
      catch (e) { failed = e; break; }
    }
    if (failed) {
      await shot(name + '_FAIL').catch(() => {});
      results.push({ name, ok: false, error: failed.message });
      console.log(`FAIL ${name.padEnd(20)} ${failed.message}`);
      continue;
    }
    await step(name, acts, opt);
  }

  console.log('\n===== 汇总 =====');
  const pass = results.filter((r) => r.ok).length;
  console.log(`步骤：${pass}/${results.length} PASS`);
  console.log('pageerror / console-error 数：', errors.length);
  errors.slice(0, 20).forEach((e) => console.log('  !', e));
  fs.writeFileSync(path.join(OUTDIR, '_report.json'), JSON.stringify({ results, errors }, null, 2));

  ws.close();
  await fetch(`http://127.0.0.1:${PORT}/json/close/${tab.id}`).catch(() => {});
  try { edge.kill(); } catch {}
  process.exit(errors.length || pass < results.length ? 3 : 0);
})().catch((e) => {
  console.error('SMOKE FAILED:', e.message);
  console.error('已捕获错误 %d 条：', errors.length);
  errors.slice(0, 20).forEach((x) => console.error('  !', x));
  try { edge.kill(); } catch {}
  process.exit(1);
});
