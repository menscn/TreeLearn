#!/usr/bin/env node
/**
 * 1:1 闭环量测的「DOM / 计算样式侧」仪器（T-2026-10-15-009，前身 artifacts/tmp/verify_1to1.mjs）。
 * 打开高保真原型 → 按屏档案回放点击 → 量 DOM 几何与计算样式 → 逐条打印判据并把完整读数落盘。
 *
 * 与产图侧 `cdp_shot.mjs` 共用 `parity_cdp.mjs`：同一份屏档案、同一个 CDP 客户端、同一套退出码档位。
 * **本工具不判「与 Android 是否等价」**：它判的是「档案点名的每一项确实量到了，且量到的值与
 * 它自己的同源依据一致」。与 Android 的等价要由阶段 3 拿这份读数去比，另给一份记录。
 *
 * 用法：node .trae/scripts/dev/parity/verify_1to1.mjs --screen chat [--out <json>]
 *       node .trae/scripts/dev/parity/verify_1to1.mjs --selftest
 */
import fs from "node:fs";
import path from "node:path";
import * as K from "./parity_cdp.mjs";
import { selftest } from "./verify_1to1_selftest.mjs";

const SPEC = ["--screen", "--out", "--profile", "--browser", "--attach", "--url", "--wait-ms"];
/** 「被核对象没起来」的可重载上限：公网脚本没答应是偶发，但重试次数必须是常数而不是玄学。 */
const MAX_BOOT_RETRIES = 1;

export const USAGE = "usage: node .trae/scripts/dev/parity/verify_1to1.mjs --screen <名> "
  + "[--out <json>] [--profile <json>] [--browser <exe>] [--attach <port>] "
  + "[--url <现成的 file:/// URL，不再二次转义>] [--wait-ms N] | --selftest";

/* ---------------------------------------------------------------- 读数探针
   探针的**输出形状**是外部契约：`artifacts/evidence/ux-chat-1to1-render-dommeasure-2026-09-27.json`
   就是这套键名，收编前后必须逐键相同，否则「读数逐项可比」这条验收无人能核。
   因此这里只改调用形态（URL / 点击序列 / 等待选择器改由档案给），不改探针里的键与算法。 */

/** 探针里 `out.tokens` 的取值表达式：读**词表点名的那批 token**，而不是在探针里再抄一串名字。
 *  抄第二份就是「同一条清单有两处正文」——档案加了名字而探针没加，那条检查只会以「token 缺失」
 *  失败，而读者据此去改档案。清单由 `window.__PARITY_TOKEN_KEYS` 在主流程里注入，只有一处正文。
 *  必须在 PROBE 之前定义：PROBE 是模板字面量，`${}` 在模块求值时展开，后置的 const 会撞 TDZ。 */
const readTokensExpr = () => `(function(){
  var rootStyle = getComputedStyle(document.documentElement), o = {};
  (window.__PARITY_TOKEN_KEYS || []).forEach(function(k){ o[k] = rootStyle.getPropertyValue(k).trim(); });
  return o;
})()`;

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

  var u = document.querySelector('.msg-user');
  out.userBubble = { rect: R(u), css: C(u, ['background-color','border-radius','padding-top','padding-bottom','padding-left','padding-right','font-size','line-height','max-width']) };
  if (u) out.userBubble.rightGapToScreen = +(R(scr).right - R(u).right).toFixed(2);

  var tag = document.querySelector('.ai-tag');
  out.aiTag = { rect: R(tag), css: C(tag, ['min-height','margin-bottom','font-size','color','display','align-items','line-height']) };
  var ai = document.querySelector('.msg-ai');
  out.aiMsg = { rect: R(ai), css: C(ai, ['font-family','font-size','line-height','color','background-color','border-left-width','padding-left']) };

  /* 无头浏览器的 .body-scroll 会占掉 ::-webkit-scrollbar 的布局宽度，而设计截图（真机 overlay 滚动条）不占。
     渲染侧所有「距屏右」的间距都比设计值大这么多；左边不受影响，可作对照。 */
  var bs = document.querySelector('.body-scroll');
  out.scroll = { offsetWidth: bs ? bs.offsetWidth : null, clientWidth: bs ? bs.clientWidth : null,
                 scrollbarWidth: bs ? (bs.offsetWidth - bs.clientWidth) : 0 };
  out.scrollNote = '渲染值里的「距屏右」= 设计值 + scrollbarWidth；左侧内边距不受影响。';

  if (u && ai) {
    var ub = R(u), ab = R(ai);
    out.msgUserBottomToAiTop = +(ab.y - ub.bottom).toFixed(2);
    out.flowGapFromCss = C(ai.parentElement, ['gap','padding']).gap;
  }
  if (tag) {
    var th = R(tag).h, mb = parseFloat(C(tag, ['margin-bottom'])['margin-bottom']);
    var blh = document.querySelector('.sent') ? parseFloat(getComputedStyle(document.querySelector('.sent')).lineHeight) : 28;
    out.derived = { tagBoxH: th, marginBottom: mb, bodyLineHeight: blh,
                    centerGap: +((th/2) + mb + (blh/2)).toFixed(2) };
  }
  out.aiTagHasReasoningRow = !!(ai && ai.querySelector('.reasoning-wrap'));
  var body = null, kids = ai ? ai.children : [];
  for (var i=0;i<kids.length;i++){ if (!kids[i].classList.contains('ai-tag') && !kids[i].classList.contains('reasoning-wrap') && kids[i].textContent.trim()) { body = kids[i]; break; } }
  out.aiBody = { rect: R(body), css: C(body, ['font-size','line-height','color']) };
  if (tag && body) {
    var t = R(tag), b = R(body);
    out.aiTagToBodyCenterGap = +(((b.y + b.h/2) - (t.y + t.h/2))).toFixed(2);
    out.aiTagBottomToBodyTop = +(b.y - t.bottom).toFixed(2);
  }
  if (body) out.aiBodyLeftInset = +(R(body).x - R(scr).x).toFixed(2);

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

  if (acts.length && pills.length) {
    var block = document.querySelector('.sugg-block');
    out.suggBlockCss = C(block, ['margin-top']);
    out.actionRowRect = R(document.querySelector('.ai-actions'));
    out.actionRowBottomToPillTop = +(R(pills[0]).y - R(document.querySelector('.ai-actions')).bottom).toFixed(2);
  }

  /* token 回读必须与检查表**同一个表达式求值**（见 DOM_CHECKS 注释），否则判据量的是副本而不是正文。 */
  out.tokens = ${readTokensExpr()};

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

/* ---------------------------------------------------------------- 数值解析 */

/** `#RGB` / `#RRGGBB` / `rgb(...)` / `rgba(...)` → [r,g,b,a]；解析不出返回 null。
 *  颜色一致性判定不能靠字符串相等：计算样式会把 `#F4F1EB` 归一成 `rgb(244, 241, 235)`，
 *  而 token 侧保留十六进制原文，两侧永不相等。 */
function parseColor(text) {
  if (typeof text !== "string") return null;
  const s = text.trim();
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(s);
  if (hex) {
    const h = hex[1].length === 3 ? hex[1].split("").map((c) => c + c).join("") : hex[1];
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), 1];
  }
  const m = /^rgba?\(([^)]+)\)$/i.exec(s);
  if (!m) return null;
  const parts = m[1].split(/[,\/\s]+/).filter(Boolean).map((v) => (v.endsWith("%") ? parseFloat(v) / 100 : parseFloat(v)));
  if (parts.length < 3 || parts.some((v) => !Number.isFinite(v))) return null;
  const [r, g, b] = parts;
  return [r, g, b, parts.length > 3 ? parts[3] : 1];
}

/** px 值（含 `31px`、`0`、`1.5`）→ 数字；非 px 一律 null，不猜单位。 */
function parsePx(text) {
  if (typeof text !== "string") return null;
  const m = /^(-?[\d.]+)px$/.exec(text.trim());
  return m ? parseFloat(m[1]) : null;
}

/* 颜色等值比对的 alpha 容差 = 0.0051，不是随手放宽：**读数通道的分辨率就是这么粗**。
   浏览器把 `repeating-linear-gradient(..., var(--paper-rule) ...)` 展开后重新序列化，
   alpha 只保留两位小数——实测 `--paper-rule:rgba(30,43,35,.018)` 在页内读回 `rgba(30, 43, 35, 0.02)`，
   差值 0.002 在二进制浮点里是 0.0020000000000000018，因此取 0.002 反而判红（本次真实踩到）。
   0.0051 = 两位小数的半个末位再加浮点余量；rgb 三分量仍要求逐位相等。
   该容差必须写进判据行，否则读者会以为墨色是逐位比对的。 */
const ALPHA_TOL = 0.0051;
const sameColor = (a, b, alphaTol = ALPHA_TOL) => {
  if (!a || !b) return false;
  return a[0] === b[0] && a[1] === b[1] && a[2] === b[2] && Math.abs(a[3] - b[3]) <= alphaTol;
};

/* ---------------------------------------------------------------- 判据词表 */

/**
 * `prototype.dom_checks` 里每个名字的**唯一**正文。档案写了词表不认的名字必须判仪器故障（3）：
 * 静默跳过会让「档案加了第六项、仪器其实没测」读成六项全过——这是本仓库「匹配集为空必须判 FAIL」
 * 在词表上的形态。每条返回 {ok, detail}；detail 里的数字全部现场取自 DOM 读数。
 *
 * 依赖的 token 由检查自己声明（`tokens`），主程序在求值前把并集注入页面（见 readTokensExpr）——
 * 检查名与 token 名因此同一处维护，不需要在档案里再抄一份 token 清单。
 * 导出给自检伴随文件：变异用例要能直接喂一份「与生产同形状」的读数，而不是靠改原型 HTML 来触发某一条。
 */
export const DOM_CHECKS = {
  "body-bg": (d) => {
    const declared = d.tokens["--bg"], actual = d.screen && d.screen.css && d.screen.css["background-color"];
    const a = parseColor(actual), b = parseColor(declared);
    if (!a || !b) return { ok: false, detail: `body-bg 量不到或 token 解析不出（实测=${actual} --bg=${declared}）` };
    return sameColor(a, b)
      ? { ok: true, detail: `body-bg = ${actual} = :root --bg ${declared}` }
      : { ok: false, detail: `body-bg 实测=${actual} 而 :root --bg=${declared}——变量声明没落到 .screen 上` };
  },
  "grid-period": (d) => {
    const tex = d.gridTexture;
    if (!tex || !/repeating-linear-gradient/.test(tex)) {
      return { ok: false, detail: `grid-period 量不到纸感渐变（实测 background-image=${JSON.stringify(tex)}）` };
    }
    /* 周期取最后一个 px 停止点；解析不出周期属结构变了，不是「没量到」，要出声。 */
    const pxs = [...tex.matchAll(/(-?[\d.]+)px/g)].map((m) => parseFloat(m[1]));
    if (!pxs.length) return { ok: false, detail: `grid-period 渐变里没有可解析的 px 停止点: ${tex}` };
    const period = Math.max(...pxs);
    /* 墨色的识别形状：**渐变里所有非透明颜色必须彼此相等且等于 --paper-rule**。
       两条都不是凭空造的：
       ① 不取「最后一个 rgba」——浏览器把 `transparent` 也序列化成 `rgba(0, 0, 0, 0)` 排在最前，
          电池实测「改最后一个」与「改第一个」在判据里长得一样，一条打中透明停止点的注入因此判绿；
       ② 不要求「非透明恰好一个」——真实读数是 31px 与 32px 两个停止点用同一个墨色，
          恰好一个的规则在健康页面上就判红（正控断言当场抓住，见 009 的 TDD 记录）。
       「透明停止点是正常成分」同理：只数非透明的。 */
    const inks = [...tex.matchAll(/rgba?\([^)]*\)/g)].map((m) => ({ text: m[0], c: parseColor(m[0]) })).filter((x) => x.c);
    const opaque = inks.filter((x) => x.c[3] > ALPHA_TOL);
    const declared = d.tokens["--paper-rule"], want = parseColor(declared);
    const bad = [];
    if (!opaque.length) bad.push(`渐变里一个非透明墨色都没有（全部颜色=${JSON.stringify(inks.map((x) => x.text))}）——纸感渐变没了墨色`);
    else if (opaque.some((x) => !sameColor(x.c, opaque[0].c))) bad.push(`渐变里有多种墨色: ${JSON.stringify([...new Set(opaque.map((x) => x.text))])}——一条格线只许一种墨`);
    else if (!want) bad.push(`:root --paper-rule 解析不出（实测=${JSON.stringify(declared)}），墨色无从核对`);
    else if (!sameColor(opaque[0].c, want)) bad.push(`格线墨色实测=${opaque[0].text}（${opaque.length} 个停止点）而 :root --paper-rule=${declared}——超出读数分辨率 ±${ALPHA_TOL}，纸感 token 改了而页面没用上`);
    /* 周期的权威是 `--s8`（32px），不是本仪器发明的常量：Android 侧 `PaperRuleBackdrop` 第 44 行
       取 `R.dimen.space_s8` 并在注释里写明「原型页面的格线每第二个间距步重复一次」，
       两端读的是同一个 token。拿它当判据才是「适配而非重新开发」。 */
    const wantPeriod = parsePx(d.tokens["--s8"]);
    if (wantPeriod === null) bad.push(`周期无法核对：:root --s8 解析不出（实测=${JSON.stringify(d.tokens["--s8"])}）`);
    else if (period !== wantPeriod) bad.push(`格线周期实测=${period}px 而 --s8=${wantPeriod}px（Android 侧同名 dimen space_s8）`);
    /* tokens.json 里 lh-read 的注释写着「阅读行高 == 格线间距」，而两边实测是 28 与 32。
       这是**设计侧文档与 CSS 的矛盾**，不属本片判据：判红会把一条待裁定的文档错误冒充成渲染缺陷，
       判绿则是替设计侧编一条从未成立的等式。因此只把它打印出来，缺口登记在 009 的 Gaps。 */
    const lh = parsePx(d.tokens["--lh-read"]);
    const note = lh !== null && lh !== period ? `（注意：--lh-read=${lh}px ≠ 周期 ${period}px，与 tokens.json 里「阅读行高 == 格线间距」的注释不符，属设计侧待裁定）` : "";
    return bad.length ? { ok: false, detail: `grid-period ${bad.join("；")}` }
      : { ok: true, detail: `grid-period 周期=${period}px = --s8 墨色=${opaque[0].text}（${opaque.length} 个停止点同色）≈ --paper-rule ${declared}（alpha 按读数分辨率 ±${ALPHA_TOL} 比对）${note}` };
  },
  /** 前身（artifacts/tmp）与 2026-09-27 的那份读数把这条判成「气泡底 == --green-bubble、圆角 == --r-md」。
   *  那个 token 已在 2026-09-28 退役（原型 :root 第 34 行注释、design-system/README.md 的 16→15 变更日志；
   *  tokens.json 里也已没有它），`.msg-user` 现在是右对齐纯文本。所以这条判的是**现行制式**：
   *  ① 退役真的落到了页面上（无底、无圆角）；② 字号落到 --fs-body；③ 右缘间距 = --gutter + 渲染侧滚动条占位。
   *  ③ 是这条检查里唯一量几何的一半，也是 2026-09-27 记录中 24 = 20 + 4 那个补偿关系本身。
   *  前身读的 `color` 不在探针的 .msg-user 属性清单里（那份清单是外部契约），因此不判它。 */
  "user-bubble": (d) => {
    const css = d.userBubble && d.userBubble.css;
    if (!css) return { ok: false, detail: "user-bubble .msg-user 不存在，量不到计算样式" };
    const bad = [];
    const bg = parseColor(css["background-color"]);
    if (!bg) bad.push(`用户消息底色解析不出（实测=${css["background-color"]}）`);
    else if (bg[3] !== 0 || bg[0] || bg[1] || bg[2]) bad.push(`用户消息仍带气泡底 bg=${css["background-color"]}，而 --green-bubble 已于 2026-09-28 退役`);
    const radius = parsePx(css["border-radius"]);
    if (radius === null) bad.push(`用户消息圆角解析不出（实测=${css["border-radius"]}）`);
    else if (radius !== 0) bad.push(`用户消息仍带圆角 radius=${css["border-radius"]}，与退役后的纯文本制式不符`);
    const sizePx = parsePx(css["font-size"]), wantFs = parsePx(d.tokens["--fs-body"]);
    if (sizePx === null || wantFs === null) bad.push(`--fs-body 或 font-size 解析不出（实测=${css["font-size"]} --fs-body=${d.tokens["--fs-body"]}）`);
    else if (sizePx !== wantFs) bad.push(`用户消息 font-size 实测=${css["font-size"]} 而 --fs-body=${d.tokens["--fs-body"]}`);
    const gap = d.userBubble.rightGapToScreen, gutter = parsePx(d.tokens["--gutter"]);
    const sb = d.scroll && typeof d.scroll.scrollbarWidth === "number" ? d.scroll.scrollbarWidth : null;
    if (typeof gap !== "number") bad.push(`用户消息距屏右量不到（实测=${JSON.stringify(gap)}）`);
    else if (gutter === null) bad.push(`:root --gutter 解析不出（实测=${JSON.stringify(d.tokens["--gutter"])}），右缘间距无从核对`);
    else if (sb === null) bad.push(`滚动条占位量不到（实测=${JSON.stringify(d.scroll)}）`);
    else if (gap !== gutter + sb) bad.push(`用户消息距屏右=${gap} 而 --gutter=${gutter} + 滚动条占位=${sb} = ${gutter + sb}`);
    return bad.length
      ? { ok: false, detail: `user-bubble ${bad.join("；")}` }
      : { ok: true, detail: `user-bubble 无气泡底（bg=${css["background-color"]} radius=${css["border-radius"]}=退役后制式）`
        + ` font-size=${css["font-size"]} = --fs-body；距屏右=${gap} = --gutter ${gutter} + 滚动条占位 ${sb}` };
  },
  "body-pitch": (d) => {
    const lh = d.aiBody && d.aiBody.css && d.aiBody.css["line-height"];
    const got = parsePx(lh), want = parsePx(d.tokens["--lh-read"]);
    if (got === null || want === null) {
      return { ok: false, detail: `body-pitch 解析不出（AI 正文 line-height=${lh} --lh-read=${d.tokens["--lh-read"]}）` };
    }
    return got === want
      ? { ok: true, detail: `body-pitch AI 正文行高=${lh} = :root --lh-read ${d.tokens["--lh-read"]}` }
      : { ok: false, detail: `body-pitch AI 正文行高=${lh} 而 --lh-read=${d.tokens["--lh-read"]}——阅读行高与 token 脱钩` };
  },
  "action-pitch": (d) => {
    const pitch = d.actionCenterPitch;
    if (!Array.isArray(pitch) || !pitch.length) return { ok: false, detail: `action-pitch 中心距数组为空（实测=${JSON.stringify(pitch)}）` };
    if (pitch.some((v) => v !== pitch[0])) {
      return { ok: false, detail: `action-pitch 不等距，实测=[${pitch.join(", ")}]——图标行挤压或留缝不均` };
    }
    const box = parsePx((d.actionCss || {})["width"]);
    if (box === null) return { ok: false, detail: `action-pitch 拿不到按钮宽度（实测=${JSON.stringify(d.actionCss)}）` };
    /* 相邻图标按钮的中心距必须等于按钮宽度本身，即「正好相切」：这是该行的制式，
       与 .ai-actions 的 gap 同源。中心距 ≠ 宽度就意味着要么有缝要么叠着。 */
    if (pitch[0] !== box) {
      return { ok: false, detail: `action-pitch 中心距=${pitch[0]} 而按钮宽=${box}（gap 应为 0 的图标行）` };
    }
    return { ok: true, detail: `action-pitch = [${pitch.join(", ")}] 等距且=按钮宽 ${box}（首钮中心内缩=${d.firstActionCenterInset}；44 无 CSS token，权威是 Android 侧 @dimen/touch_min=44dp，跨端比对属阶段 3）` };
  },
  /** 推荐胶囊与 AI 正文必须落在同一栏：两者的左内缩相等，且等于 `--gutter`（原型 :root 第 45 行、
   *  tokens.json 的 space.gutter=20px，由 `.msg-flow` 的左右内边距给出）。前身只打印 20，
   *  这里把「20」换成它的权威——脱离 token 的一个数字不算判据。 */
  "pill-inset": (d) => {
    const pill = d.pillLeftInset, body = d.aiBodyLeftInset, gutter = parsePx(d.tokens["--gutter"]);
    if (typeof pill !== "number" || typeof body !== "number") {
      return { ok: false, detail: `pill-inset 量不到左内缩（胶囊=${JSON.stringify(pill)} 正文=${JSON.stringify(body)}）` };
    }
    if (gutter === null) return { ok: false, detail: `pill-inset 无从核对：:root --gutter 解析不出（实测=${JSON.stringify(d.tokens["--gutter"])}）` };
    const bad = [];
    if (pill !== body) bad.push(`胶囊左内缩=${pill} 而 AI 正文左内缩=${body}——推荐胶囊与正文不同栏`);
    if (pill !== gutter) bad.push(`胶囊左内缩=${pill} 而 --gutter=${gutter}`);
    return bad.length ? { ok: false, detail: `pill-inset ${bad.join("；")}` }
      : { ok: true, detail: `pill-inset = ${pill} = AI 正文左内缩 ${body} = :root --gutter ${gutter}` };
  },
};

/* ---------------------------------------------------------------- 主流程 */

export async function run(argv) {
  /* 打印自己的 argv 而不是 process.argv：自检在同进程里调 run()，记成驱动那一条会让产物
     声称一次从未发生过的调用（与 cdp_shot.mjs 同一做法）。 */
  const cmd = ["verify_1to1.mjs", ...argv].join(" ");
  const { opts } = K.parseArgs(argv, SPEC);
  if (!opts["--screen"]) throw new K.Usage("必须给 --screen <屏名>：原型路径、点击序列、等待选择器与 DOM 检查项都从屏档案取");
  const profilePath = path.resolve(opts["--profile"] || K.DEFAULT_PROFILE);
  const profile = K.readJson(profilePath, "屏档案");
  const entry = K.pickScreen(profile, opts["--screen"]);
  const proto = K.prototypeOf(entry, opts["--screen"]);
  const checkNames = Array.isArray(proto.dom_checks) ? proto.dom_checks : [];
  if (!checkNames.length) throw new K.Fault(`屏 "${opts["--screen"]}" 的 prototype 段没有 dom_checks——没有检查项就没有见证`);
  /* 词表不认的名字判仪器故障：跳过它等于让「档案说 6 项、实际量 5 项」读成 6 项全过。 */
  const unknown = checkNames.filter((n) => !Object.prototype.hasOwnProperty.call(DOM_CHECKS, n));
  if (unknown.length) throw new K.Fault(`档案里的 DOM 检查名不在词表内: ${unknown.join(", ")}（词表现有: ${Object.keys(DOM_CHECKS).join(", ")}）`);

  const waitMs = opts["--wait-ms"] ? parseInt(opts["--wait-ms"], 10) : 20000;
  if (!Number.isFinite(waitMs) || waitMs < 1) throw new K.Usage(`--wait-ms 必须是正整数毫秒，收到 ${opts["--wait-ms"]}`);

  const where = opts["--url"] ? { abs: opts["--url"], url: opts["--url"] } : K.htmlUrl(proto);
  /* 逃生口也 fail-closed：--url 指本机文件却不存在属「缺被核对象」(3)，
     让浏览器导航过去再等 .screen 会把前者读成后者(1)。 */
  if (opts["--url"] && where.url.startsWith("file:")) {
    const p = decodeURI(where.url.replace(/^file:\/\/\/?/i, "")).replace(/\//g, path.sep);
    if (!fs.existsSync(p)) throw new K.Fault(`--url 指向的本机文件不存在: ${p}`);
  }

  const out = path.resolve(opts["--out"] || path.join(K.REPO, "artifacts", "tmp", `dommeasure-${opts["--screen"]}.json`));
  const ledger = new K.Ledger();
  const browser = K.findBrowser(opts["--browser"]);
  const br = await K.openBrowser(browser, opts["--attach"] ? parseInt(opts["--attach"], 10) : 0);
  try {
    const version = await br.cdp.version();
    await br.cdp.send("Page.enable");
    await br.cdp.send("Runtime.enable");
    /* 导航必须自己证明生效（about:blank 的 readyState 同样是 complete）：与 cdp_shot.mjs 同一实测竞态。 */
    let navRetries = 0;
    const navigated = async () => {
      const nav = await br.cdp.send("Page.navigate", { url: where.url });
      if (nav && nav.errorText) throw new K.Fault(`导航被浏览器拒绝: ${nav.errorText}（目标 ${where.url}）`);
      return br.cdp.waitUntil("location.href !== 'about:blank'", 8000);
    };
    if (!(await navigated())) {
      navRetries += 1;
      if (!(await navigated())) {
        const stuck = await br.cdp.eval("({href: location.href, ready: document.readyState})");
        throw new K.Fault(`导航两次都没生效：标签页仍在 ${stuck.href}（readyState=${stuck.ready}，目标 ${where.url}）`);
      }
    }
    if (navRetries) ledger.ok(`navigation-retry = ${navRetries} 次（about:blank 未让位，Windows 启动竞态）`);
    /* 等不到 .screen 有两种成因，退出码必须分开：原型的界面是 React+Babel 在**页面里**现场编译出来的
       （head 里三个 unpkg 脚本 + 两份公网字体），外部脚本没到 = 被核对象根本没起来，属仪器故障 (3)；
       界面渲出来了却没有 .screen 才属结构不符 (1)。readyState=complete 分不出这两件事——
       2026-10-17 实测两次 CDN 未答应的读数正是 nodes=16、ready=complete、title 完全正确。
       「没起来」与 about:blank 同属启动竞态，因此对这一档先按同一 URL 重载再判：公网抖动不该让
       整条证据链不可重跑。结构不符**不**走重试，否则一次真缺陷会被重试掩盖成偶发。 */
    const renderProbe = () => br.cdp.eval("({ready: document.readyState, href: location.href, title: document.title,"
      + " nodes: document.querySelectorAll('*').length,"
      + " root: (document.getElementById('root') || {childElementCount: -1}).childElementCount})");
    let bootRetries = 0;
    for (;;) {
      if (await br.cdp.waitFor(".screen", 60000)) break;
      const probe = await renderProbe();
      if (probe.root) {
        ledger.fail(`page-not-rendered .screen 在 60s 内没出现（readyState=${probe.ready} `
          + `#root 子元素=${probe.root} nodes=${probe.nodes} href=${probe.href} title=${probe.title}）`);
        return await K.finish(ledger, { command: cmd, header: headerOf() });
      }
      if (bootRetries >= MAX_BOOT_RETRIES) {
        throw new K.Fault(`#root 是空的或不存在（root=${probe.root} nodes=${probe.nodes} `
          + `ready=${probe.ready} title=${probe.title}，重载 ${bootRetries} 次后仍空）——原型的界面由页面内的 `
          + "React+Babel 现场编译、脚本走公网 unpkg；被核对象没起来属仪器故障，不是结构不符");
      }
      bootRetries += 1;
      if (!(await navigated())) {
        const stuck = await br.cdp.eval("({href: location.href, ready: document.readyState})");
        throw new K.Fault(`为「#root 是空的」重载第 ${bootRetries} 次没生效：标签页仍在 ${stuck.href}`
          + `（readyState=${stuck.ready}，目标 ${where.url}）`);
      }
    }
    if (bootRetries) {
      ledger.ok(`boot-retry = ${bootRetries} 次后界面才渲出来（公网脚本未答应的启动竞态；`
        + `仍空即判仪器故障 3，上限 ${MAX_BOOT_RETRIES}）`);
    }
    await K.sleep(1200);
    ledger.ok(`browser = ${version}`);
    if (br.relaunches) ledger.ok(`browser-relaunch = ${br.relaunches} 次早期退出后起来（Windows 启动器交接竞态）`);
    ledger.ok(`html = ${where.abs}`);
    ledger.ok(`clicks = ${proto.clicks.join(" -> ")}`);

    if (!(await K.replay(br.cdp, proto, (m) => ledger.fail(m)))) {
      return await K.finish(ledger, { command: cmd, header: headerOf() });
    }
    /* 超时与仪器坏了必须分行：页面已加载却等不到目标属「结构不符」(1)，等不到 CDP 应答才属仪器故障 (3)。 */
    if (!(await br.cdp.waitFor(proto.wait_for, waitMs))) {
      const ready = await br.cdp.eval("document.readyState");
      ledger.fail(`wait-for-absent ${proto.wait_for} 在 ${waitMs}ms 内没出现（readyState=${ready}）——页面已加载，属结构不符而非仪器故障`);
      return await K.finish(ledger, { command: cmd, header: headerOf() });
    }
    await K.sleep(1500);

    /* token 清单 = 词表声明的那批名字的并集，先注入页面再求值。
       **注入与量测必须共用同一个表达式求值结果**：早期写法是「第一次 eval 取完整读数、
       第二次独立 eval 取 token」，于是一个只改第二支表达式的缺陷（例如把值换成手写的 `#2B6E45`）
       会让六条一致性判据全部量在副本上——判据把被测量自己替换掉了。这里让 out.tokens
       由 PROBE 内部按 window.__PARITY_TOKEN_KEYS 现读，独立第二次 eval 只用于**比对同一性**。 */
    const tokenKeys = [...new Set(checkNames.flatMap((n) => DOM_CHECKS[n].tokens || []))];
    await br.cdp.eval(`(window.__PARITY_TOKEN_KEYS = ${JSON.stringify(tokenKeys)}, true)`);
    const data = await br.cdp.eval(PROBE);
    if (data && data.__err) throw new K.Fault(`探针在页内抛错: ${data.__err}`);
    if (!data) throw new K.Fault("探针没有返回任何读数");
    /* 兜底复核：**逐个**独立再取一次每个被声明的 token，必须与探针自带的那次逐字相同。
       早期写法只对 `--bg` 做一次，于是「把词表里 --bg 之外那几个的取值改掉」这类缺陷
       恰好落在兜底覆盖不到的地方；而词表哪天不再声明 --bg 时，那条兜底会因 `--bg` 取空而**恒失败**，
       把仪器搞坏。逐个复核让兜底的覆盖面由词表自己决定，不新增第二份清单。 */
    for (const key of tokenKeys) {
      const independent = await br.cdp.eval(`getComputedStyle(document.documentElement).getPropertyValue(${JSON.stringify(key)}).trim()`);
      if (typeof independent !== "string" || independent !== (data.tokens || {})[key]) {
        throw new K.Fault(`token 回读两份不一致（${key}：探针=${JSON.stringify((data.tokens || {})[key])} 独立=${JSON.stringify(independent)}）`);
      }
    }
    ledger.ok(`tokens = 现场回读 ${Object.keys(data.tokens || {}).length} 个（词表要求 ${tokenKeys.length} 个，逐个独立复核一致）`);

    /* 字体面实证：CSS 写了字体不等于真用上了——缺失会静默回退，而回退栈末尾常挂 serif。
       CSS.getPlatformFontsForNode 给的是实际参与栅格化的字体族，这是唯一能证伪「静默回退」的读数。 */
    await br.cdp.send("DOM.enable");
    await br.cdp.send("CSS.enable");
    const doc = await br.cdp.send("DOM.getDocument", { depth: 1 });
    const fonts = {};
    for (const sel of [".screen", ".msg-ai", ".msg-user", ".sugg-item", ".ai-tag", ".appbar-title .t1"]) {
      const q = await br.cdp.send("DOM.querySelector", { nodeId: doc.root.nodeId, selector: sel });
      if (!q.nodeId) { fonts[sel] = null; continue; }
      const f = await cdpFonts(br.cdp, q.nodeId);
      fonts[sel] = f;
    }
    data.platformFonts = fonts;
    data.platformFontsNote = "实际参与栅格化的字体族（非 CSS 声明值）。若这里出现 serif/Songti 之类，说明声明字体缺失、发生了静默回退。";

    let failures = 0;
    for (const name of checkNames) {
      const verdict = DOM_CHECKS[name](data);
      if (verdict.ok) ledger.ok(verdict.detail);
      else { ledger.fail(verdict.detail); failures += 1; }
    }
    /* 汇总行也是判据：把 5/6 打印成 OK 会让「档案加了第六项而仪器没测」在产物里长成一条绿。
       有失败时它也判红，因此 failed 计数比违反的检查数多 1——那一条就是这条汇总，行文本自说明。 */
    const summary = `dom-checks = ${checkNames.length - failures}/${checkNames.length} 成立（名单来自档案 ${checkNames.join(", ")}）`;
    if (failures) ledger.fail(summary); else ledger.ok(summary);

    fs.mkdirSync(path.dirname(out), { recursive: true });
    const text = JSON.stringify(data, null, 2);
    fs.writeFileSync(out, text, "utf8");
    ledger.ok(`out = ${out} bytes=${Buffer.byteLength(text, "utf8")}`);
    return await K.finish(ledger, { command: cmd, header: headerOf() });
  } finally {
    await br.close();
  }

  function headerOf() {
    return [
      `profile ${K.fingerprint(profilePath, "screens.json")}`,
      opts["--url"] ? "html 由 --url 显式给出，未核档案路径" : K.fingerprint(where.abs, "prototype.html"),
      `wait_ms=${waitMs} dom_checks=${checkNames.length}`,
    ];
  }
}

/** getPlatformFontsForNode 的容错封装：该域偶发不回应答，属仪器层而不是判据层。 */
async function cdpFonts(cdp, nodeId) {
  try {
    const f = await cdp.send("CSS.getPlatformFontsForNode", { nodeId });
    return (f.fonts || []).map((x) => ({ family: x.familyName, glyphs: x.glyphCount, custom: !!x.isCustomFont }));
  } catch (e) {
    throw new K.Fault(`CSS.getPlatformFontsForNode 没有应答（仪器层）: ${e.message}`);
  }
}

/** 词表每条声明它要哪些 token：主程序据此注入页面，读者据此知道某条检查依赖什么。
 *  名字必须是 :root 里**真实存在**的自定义属性——`--green-bubble` 与 `--s5` 都不存在（前者 2026-09-28
 *  退役、后者从未有过），拿它们当依据会让检查以「解析不出」失败，而那条失败读起来像页面缺陷。 */
DOM_CHECKS["body-bg"].tokens = ["--bg"];
DOM_CHECKS["grid-period"].tokens = ["--paper-rule", "--s8", "--lh-read"];
DOM_CHECKS["user-bubble"].tokens = ["--fs-body", "--gutter"];
DOM_CHECKS["body-pitch"].tokens = ["--lh-read"];
DOM_CHECKS["action-pitch"].tokens = [];
DOM_CHECKS["pill-inset"].tokens = ["--gutter"];

if (K.isMain(import.meta.url)) {
  const argv = process.argv.slice(2);
  process.exitCode = await K.mainTool(async () => {
    if (argv.includes("--help")) { console.log(USAGE); console.log("EXITCODE=0"); return K.EXIT.OK; }
    /* 必须静态 import：伴随文件反向 import 本文件的 run()，动态取会让进程以退出码 13
       结束且 stdout 一行不写（机理与两种形态的对照读数记在 T-2026-10-15-006 的 GREEN 产物块 02）。 */
    if (argv.includes("--selftest")) return selftest();
    if (!argv.length) throw new K.Usage(USAGE);
    return run(argv);
  });
}
