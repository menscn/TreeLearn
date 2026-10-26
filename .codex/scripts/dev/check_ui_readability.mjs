#!/usr/bin/env node
/**
 * 把「界面上的字到底看得见看不见、读起来累不累」量成渲染态判决。
 *
 * 用法（从仓库根）：
 *     node .codex/scripts/dev/check_ui_readability.mjs
 *     node .codex/scripts/dev/check_ui_readability.mjs --attach <cdp端口>
 *     node .codex/scripts/dev/check_ui_readability.mjs --selftest
 *
 * 退出码（与仓库其余守门同一纪律）：0 判据全成立；1 有判据 FAIL；
 * 2 用法错误；3 仪器故障（离线包不存在、浏览器起不来、页面没渲染出可判文本）。
 *
 * 为什么要它：086 线的 X3 与 B1 都是**只在渲染后才存在**的事实。
 * `--fs-cap: 11px` 写在 `tokens.css` 里，但「这一档字号有没有真的落到用户眼睛前、
 * 落在一行什么文字上」只有渲染面答得出；「48 个汉字一行」更是纯几何量，
 * 静态 CSS 里根本没有这个数。仓库已有的两支静态判据（`check_design_tokens.py`
 * 管取值、`check_glass_depth.py` 管像素观感）都不覆盖这两件事。
 *
 * 判据（编号与 T-2026-10-25-089/092/094 子卡一致）：
 *   R1 最小可见字号不得低于 CJK_FLOOR_PX。取的是**渲染后真的落在文字上的**计算值，
 *      不是 token 表里的声明值——声明了 12 但被某条规则覆盖回 11，静态判据看不出来。
 *      中文界面小字地板取 12px：11px 的中文横画在 1080p 手机与 Windows 桌面上会先掉。
 *   R2 阅读正文每行汉字数不得超过 MAX_CJK_PER_LINE。测量口径见 chars_per_line()：
 *      正文用的是本项目唯一的等宽假设——CJK 全角字形宽 ≈ 1 em，所以
 *      「每行汉字数」= floor(容器内宽 / 计算字号)。这个假设对 `--sans` 下的
 *      汉字成立，对标拉丁为主的文本不成立（本项目阅读正文是中文，见 R2 的
 *      「读出的字号与宽度」行）。
 *   R3 导航标签不得低于 FS_SM_PX。导航是全应用点击率最高的一行字，
 *      不该同时是字号最小的一行——这是 X3 的实际 complaint，不是「11px 本身」。
 *
 * 只判 Web／桌面产物面（`frontend/dist-offline/index.html`，`file://` 直开，
 * 不起 dev server、不联网）。Android 面的同档问题由 092 片的静态断言与真机截图负责，
 * 两端各自有判据，不在这里互相代替。
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as K from "./parity/parity_cdp.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "..", "..", "..");

const BUNDLE = path.join(REPO, "frontend", "dist-offline", "index.html");
const CHAT_ROUTE = "#/chat/0";

const CJK_FLOOR_PX = 12;      // R1：中文界面小字地板
const MAX_CJK_PER_LINE = 41; // R2：32–41 是中文长文舒适区间的上沿
const FS_SM_PX = 13;         // R3：导航标签至少要落在 --fs-sm 这一档
const VIEWPORT = { width: 1440, height: 900 };

const NAV_SELECTOR = ".side-label";
const READING_SELECTOR = ".chat-answer";

const MIN_SELFTEST_CASES = 9;   // 实测 11；裸下限掉到 9 以下说明有分支被静默删空
                                //（合成三例是 R1 不重复计红的合同，删掉它们红条数会开始说谎）

/* ------------------------------------------------------------ 页面侧读数 */

const SCAN_JS = `(function(){
  var floor = __FLOOR__, navSel = __NAV__, reading = __READING__;
  var out = { min: null, minHits: [], nav: null, navText: [], reading: null, textNodes: 0 };
  function boxOf(el){ var r = el.getBoundingClientRect(); return { w: r.width, h: r.height, t: r.top }; }
  function visible(el){
    var cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) return false;
    var b = boxOf(el);
    return b.w >= 1 && b.h >= 1;
  }
  var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
  var n, seen = {};
  while ((n = w.nextNode())) {
    var text = (n.nodeValue || '').replace(/\\s+/g, ' ').trim();
    if (!text) continue;
    var el = n.parentElement;
    if (!el || !visible(el)) continue;
    out.textNodes += 1;
    var fs = parseFloat(getComputedStyle(el).fontSize);
    if (!isFinite(fs)) continue;
    if (out.min === null || fs < out.min) out.min = fs;
    if (fs < floor) {
      var key = el.tagName.toLowerCase() + '.' + (typeof el.className === 'string' ? (el.className.split(' ')[0] || '-') : '-') + '@' + fs;
      if (!seen[key]) { seen[key] = 1; out.minHits.push({ sel: key, px: fs, text: text.slice(0, 24) }); }
    }
  }
  out.minHits.sort(function(a,b){ return a.px - b.px; });
  var navs = [].slice.call(document.querySelectorAll(navSel)).filter(visible);
  if (navs.length) {
    out.nav = parseFloat(getComputedStyle(navs[0]).fontSize);
    out.navText = navs.map(function(e){ return e.textContent.trim(); }).slice(0, 6);
  }
  var rd = [].slice.call(document.querySelectorAll(reading)).filter(visible).sort(function(a,b){
    return boxOf(b).w - boxOf(a).w; })[0];
  if (rd) {
    var cs = getComputedStyle(rd);
    var inner = boxOf(rd).w - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
    var f = parseFloat(cs.fontSize);
    out.reading = { w: Math.round(boxOf(rd).w), innerW: Math.round(inner), px: f,
                    charsPerLine: Math.floor(inner / f) };
  }
  return out;
})()`;

/* ------------------------------------------------------------------ 判据 */

class Checks {
  constructor(name) { this.name = name; this.failures = []; }
  ok(label, detail = "") { console.log(`OK   ${label}${detail ? " — " + detail : ""}`); }
  fail(label, detail = "") { this.failures.push(label); console.log(`FAIL ${label}${detail ? " — " + detail : ""}`); }
  note(label, detail = "") { console.log(`NOTE ${label}${detail ? " — " + detail : ""}`); }
  check(cond, label, detail = "") { cond ? this.ok(label, detail) : this.fail(label, detail); return !!cond; }
  finish() {
    console.log("");
    console.log(`${this.name}: ${this.failures.length} check(s) failed`);
    console.log(`EXITCODE=${this.failures.length ? 1 : 0}`);
    return this.failures.length ? 1 : 0;
  }
}

/** 把多页读数合成一份：同一个成因（例如 `--fs-cap` 那一档）只判一次红。
 *  不合成就会在首页与阅读面各报一条 R1，`check(s) failed` 的条数不再等于缺陷数。 */
function combine(list) {
  const seen = new Set();
  const minHits = [];
  for (const r of list) {
    for (const h of (r.minHits || [])) {
      if (seen.has(h.sel)) continue;
      seen.add(h.sel);
      minHits.push(h);
    }
  }
  minHits.sort((a, b) => a.px - b.px);
  const mins = list.map((r) => r.min).filter((v) => typeof v === "number");
  const navs = list.map((r) => r.nav).filter((v) => typeof v === "number");
  const withNavText = list.find((r) => r.navText && r.navText.length);
  const withReading = list.find((r) => r.reading);
  return {
    min: mins.length ? Math.min(...mins) : null,
    minHits,
    textNodes: list.reduce((a, r) => a + (r.textNodes || 0), 0),
    nav: navs.length ? Math.min(...navs) : null,
    navText: withNavText ? withNavText.navText : [],
    reading: withReading ? withReading.reading : null,
    pages: list.length,
  };
}

/** R1/R2/R3 的判决只吃「读数」，不吃浏览器——自检因此能注入假读数。 */
function judgeReadings(ck, read, { requireReading = true } = {}) {
  if (!read || !read.textNodes) {
    throw new K.Fault("页面没有任何可见文本节点，判据无法归因给产物");
  }
  if (read.min === null) {
    throw new K.Fault("可见文本全都读不出字号，判据无法归因给产物");
  }
  const offenders = read.minHits || [];
  const scope = read.pages ? `，合成 ${read.pages} 页` : "";
  ck.check(read.min >= CJK_FLOOR_PX, "R1 最小可见字号不低于中文小字地板",
    `最小 ${read.min}px（地板 ${CJK_FLOOR_PX}px${scope}），低于地板的选择器：` +
    (offenders.length ? offenders.map(h => `${h.sel}「${h.text}」`).join("；") : "无") +
    `；可见文本节点 ${read.textNodes}`);

  if (!read.reading) {
    if (requireReading) throw new K.Fault(`渲染面找不到 ${READING_SELECTOR}，R2 无从判（不是产物判红）`);
    ck.note("R2 阅读正文每行汉字数有上限", `${READING_SELECTOR} 在本页不存在：本轮没判`);
  } else {
    const r = read.reading;
    ck.check(r.charsPerLine <= MAX_CJK_PER_LINE, "R2 阅读正文每行汉字数有上限",
      `实测每行 ${r.charsPerLine} 个汉字（上限 ${MAX_CJK_PER_LINE}）：内宽 ${r.innerW}px ÷ 字号 ${r.px}px，容器 ${r.w}px`);
  }

  if (read.nav === null) {
    throw new K.Fault(`渲染面找不到 ${NAV_SELECTOR}，R3 无从判（导航文字必须真的存在才算判过）`);
  }
  ck.check(read.nav >= FS_SM_PX, "R3 导航标签不低于 --fs-sm 这一档",
    `实测 ${read.nav}px（地板 ${FS_SM_PX}px）：${(read.navText || []).join(" / ")}`);
}

async function measure(cdp, url) {
  await cdp.send("Page.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", {
    width: VIEWPORT.width, height: VIEWPORT.height, deviceScaleFactor: 1, mobile: false,
  });
  await cdp.send("Page.navigate", { url });
  if (!(await cdp.waitFor("body", 20000))) throw new K.Fault(`页面没渲染出 body: ${url}`);
  if (!(await cdp.waitFor(NAV_SELECTOR, 20000))) throw new K.Fault(`页面没渲染出 ${NAV_SELECTOR}: ${url}`);
  await new Promise((r) => setTimeout(r, 1200));
  const js = SCAN_JS.replace("__FLOOR__", String(CJK_FLOOR_PX))
    .replace("__NAV__", JSON.stringify(NAV_SELECTOR))
    .replace("__READING__", JSON.stringify(READING_SELECTOR));
  return await cdp.eval(js);
}

/* ---------------------------------------------------------------- 自检 */

function selftest(ck) {
  const cases = [];
  const base = { min: 13, minHits: [], nav: 13, navText: ["主页"], textNodes: 40,
    reading: { w: 720, innerW: 620, px: 15, charsPerLine: 41 } };
  const run = (over, opts) => {
    const sink = new Checks("fixture");
    const log = [];
    const orig = console.log;
    console.log = (...a) => log.push(a.join(" "));
    try { judgeReadings(sink, { ...base, ...over }, opts); } finally { console.log = orig; }
    return sink.failures;
  };

  cases.push(["R1 低于地板时必须判红", run({ min: 11, minHits: [{ sel: "span.side-cap@11", text: "3天前", px: 11 }] })
    .includes("R1 最小可见字号不低于中文小字地板")]);
  cases.push(["R1 恰好等于地板时不得判红", !run({ min: 12 }).includes("R1 最小可见字号不低于中文小字地板")]);
  cases.push(["R2 超上限时必须判红", run({ reading: { w: 720, innerW: 720, px: 15, charsPerLine: 48 } })
    .includes("R2 阅读正文每行汉字数有上限")]);
  cases.push(["R2 在上限内时必须转绿", !run({ reading: { w: 720, innerW: 620, px: 15, charsPerLine: 41 } })
    .includes("R2 阅读正文每行汉字数有上限")]);
  cases.push(["R3 导航标签掉到最小档时必须判红", run({ nav: 11 }).includes("R3 导航标签不低于 --fs-sm 这一档")]);
  cases.push(["没有阅读面且要求它时必须报仪器故障而不是判绿", (() => {
    try { run({ reading: null }); return false; } catch (e) { return e instanceof K.Fault; }
  })()]);
  cases.push(["没有阅读面但不要求时可以出声跳过而不是静默", (() => {
    const sink = new Checks("fixture");
    const orig = console.log; const log = [];
    console.log = (...a) => log.push(a.join(" "));
    try { judgeReadings(sink, { ...base, reading: null }, { requireReading: false }); }
    finally { console.log = orig; }
    return log.some(l => l.startsWith("NOTE R2")) && !sink.failures.includes("R2 阅读正文每行汉字数有上限");
  })()]);
  cases.push(["没有可见文本节点时必须报仪器故障", (() => {
    try { run({ textNodes: 0 }); return false; } catch (e) { return e instanceof K.Fault; }
  })()]);
  const dupHit = { min: 11, minHits: [{ sel: "p.composer-help@11", text: "发送与上传尚未接通", px: 11 }],
    nav: 13, navText: ["主页"], textNodes: 30, reading: null };
  const merged = combine([dupHit, { ...dupHit, min: 12, textNodes: 50,
    reading: { w: 720, innerW: 720, px: 15, charsPerLine: 48 } }]);
  cases.push(["合成多页时同一选择器不得重复计入红条", merged.minHits.length === 1]);
  cases.push(["合成取两页里更小的那个字号", merged.min === 11 && merged.textNodes === 80]);
  cases.push(["合成把有阅读面的那页交给 R2 而不是被无阅读面的页淹没", !!merged.reading]);

  for (const [name, ok] of cases) ck.check(ok, "SELFTEST " + name);
  console.log(`selftest judgement cases executed = ${cases.length}`);
  ck.check(cases.length >= MIN_SELFTEST_CASES, "SELFTEST 用例数不得掉到裸下限以下",
    `实测 ${cases.length}，下限 ${MIN_SELFTEST_CASES}`);
}

/* ------------------------------------------------------------------ main */

async function main(argv) {
  const args = argv.slice(2);
  const flagOf = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : ""; };
  const has = (name) => args.includes(name);

  if (has("--help")) {
    console.log("用法：node .codex/scripts/dev/check_ui_readability.mjs [--attach <端口>] [--selftest]");
    return 0;
  }
  const ck = new Checks("check_ui_readability");

  if (has("--selftest")) {
    selftest(ck);
    return ck.finish();
  }

  if (!fs.existsSync(BUNDLE)) {
    console.log(`FAULT 离线产物不存在：${BUNDLE}（先跑 pnpm --dir frontend build 与 inline_offline）`);
    console.log("EXITCODE=3");
    return 3;
  }
  const url = "file:///" + BUNDLE.split(path.sep).join("/");
  let ctx;
  try {
    ctx = await K.openBrowser(K.findBrowser(flagOf("--browser")), parseInt(flagOf("--attach") || "0", 10));
  } catch (e) {
    console.log(`FAULT 仪器起不来：${e.message}`);
    console.log("EXITCODE=3");
    return 3;
  }
  try {
    const home = await measure(ctx.cdp, url);
    const chat = await measure(ctx.cdp, url + CHAT_ROUTE);
    console.log(`读数 首页 ${VIEWPORT.width}x${VIEWPORT.height}：最小 ${home.min}px，可见文本 ${home.textNodes} 个，导航标签 ${home.nav}px`);
    console.log(`读数 阅读面 ${VIEWPORT.width}x${VIEWPORT.height}：正文 ${chat.reading ? chat.reading.px + "px ÷ 内宽 " + chat.reading.innerW + "px = 每行 " + chat.reading.charsPerLine + " 个汉字" : "无 .chat-answer"}`);
    judgeReadings(ck, combine([home, chat]), { requireReading: true });
  } catch (e) {
    console.log(`FAULT ${e && e.message ? e.message : String(e)}`);
    console.log("EXITCODE=3");
    return 3;
  } finally {
    await ctx.close();
  }
  return ck.finish();
}

main(process.argv).then((rc) => { if (typeof rc === "number") process.exitCode = rc; });
