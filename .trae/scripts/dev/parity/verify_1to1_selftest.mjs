#!/usr/bin/env node
/**
 * verify_1to1.mjs 的正反双向自检电池（独立伴随测试文件，与 cdp_shot_selftest.mjs 同构）。
 *
 * 为什么单独一支文件：仪器本体 + 共享层的判定面并入电池会越过本仓库的 500 行硬上限，
 * 而测试文件按判定口径不计入生产面（`.trae/rules/harness-tasks.md` 「Tests … NOT counted」）。
 *
 * 电池有**两层**，各自挡一种不同的坏法：
 * ① CLI 层（CASES）：调用形态、档案加载、词表认不认这个检查名、缺浏览器/缺 HTML 落 3、
 *    页面结构不符落 1、用法错误落 2。
 * ② 词表层（MUTS）：每条 DOM 判据都要有「注入该违规必须判红」的用例。这一层**不开浏览器**，
 *    而是把 ① 的 pos 用例刚落盘的读数当健康样本，逐条改动一个字段再喂回词表。
 *    为什么从产物派生而不是手写夹具：手写样本的形状很容易不是生产探针吐出的形状
 *    （本仓库栽过一次——探针形状与生产形状不同导致七条断言全部「通过」）。这里形状由探针自己给出，
 *    正控因此无法漂移。
 *
 * 用法：node .trae/scripts/dev/parity/verify_1to1.mjs --selftest （推荐入口）
 *       node .trae/scripts/dev/parity/verify_1to1_selftest.mjs   （同一支电池的直驱入口）
 */
import fs from "node:fs";
import path from "node:path";
import * as K from "./parity_cdp.mjs";
import { run, DOM_CHECKS } from "./verify_1to1.mjs";

const CASES = [
  { name: "pos-real-dom-render", want: 0, out: "dom.json", args: (f) => ["--screen", "chat", "--out", path.join(f, "dom.json")] },
  /* 逃生口的正向：前身工具的唯一调用形态是显式 URL，收编后这条路径必须仍然走得通。 */
  {
    name: "pos-url-escape",
    want: 0,
    out: "dom-url.json",
    args: (f) => {
      const entry = K.pickScreen(K.readJson(K.DEFAULT_PROFILE, "屏档案"), "chat");
      const u = K.htmlUrl(K.prototypeOf(entry, "chat"));
      return ["--screen", "chat", "--out", path.join(f, "dom-url.json"), "--url", u.url];
    },
  },
  { name: "neg-unknown-option", want: 2, args: () => ["--screen", "chat", "--turbo", "1"] },
  { name: "neg-missing-screen", want: 2, args: () => [] },
  { name: "neg-bad-wait", want: 2, args: () => ["--screen", "chat", "--wait-ms", "0"] },
  { name: "neg-unknown-screen", want: 2, args: () => ["--screen", "nope", "--out", "x.json"] },
  { name: "neg-browser-absent-injection", want: 3, args: () => ["--screen", "chat", "--out", "x.json", "--browser", "/c/nowhere/msedge.exe"] },
  { name: "neg-profile-no-prototype", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "no-proto.json")] },
  { name: "neg-html-missing", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "bad-html.json")] },
  { name: "neg-profile-not-utf8", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "bad-utf8.json")] },
  /* 下面三条是本仪器自己新增的档案分支：检查项缺席、为空、名字不在词表内。
     三条都必须判 3 而不是「少测一项照样 PASS」——那是「匹配集为空必须判 FAIL」在词表上的形态。 */
  { name: "neg-checks-absent", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "no-checks.json")] },
  { name: "neg-checks-empty", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "empty-checks.json")] },
  { name: "neg-check-name-unknown", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "unknown-check.json")] },
  { name: "neg-url-file-missing", want: 3, args: () => ["--screen", "chat", "--out", "x.json", "--url", "file:///D:/nope/missing.html"] },
  /* 导航被浏览器自己拒绝（net::ERR_ABORTED）属「缺被核对象」那一档，必须落在 3 而不是 1。 */
  { name: "neg-navigate-rejected", want: 3, args: () => ["--screen", "chat", "--out", "x.json", "--url", "javascript:document.write(1)"] },
  { name: "neg-cdp-handshake-fail", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--attach", "1"] },
  /* 两条期望 rc=1 的用例各测一条页面判据分支：等待目标缺失、点击目标缺失。
     它们与「仪器故障」同为一个非零码，所以判据是 rc 恰好 1 **且那条判决行本身在输出里**：
     只看 rc 时，一次「页面 60s 没渲出来」会让 neg-click-absent 拿到 rc=1 却从没走到点击分支
     （本仓库实测过一次，见证在 green 产物块 02 的夹具 echo 行里）。witness 就是为此而设。 */
  { name: "neg-wait-for-absent", want: 1, witness: "wait-for-absent", args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "bad-wait.json"), "--wait-ms", "1500"] },
  { name: "neg-click-absent", want: 1, witness: "click-target-absent", args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "bad-click.json")] },
  /* 「等不到 .screen」有两种成因，各一条用例（用 HTML 桩造，不打公网，因此可重复）：
     桩里 #root 有内容却没有 .screen = 结构不符 (1)；#root 空的 = 被核对象没起来，仪器故障 (3)。
     两条各要等满 60s 的渲染等待——这是本电池耗时的一部分，换来的是退出码档位不会互相顶替。 */
  { name: "neg-page-not-rendered", want: 1, witness: "page-not-rendered", args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "stub-rendered.json")] },
  { name: "neg-app-not-booted", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.json"), "--profile", path.join(f, "stub-empty.json")] },
];

/** 从渐变正文里派生「墨色那个 rgba」：浏览器把 `transparent` 也序列化成 `rgba(0, 0, 0, 0)`，
 *  所以不能拿第一个 rgba 当墨色——电池的第一版就是这么漏掉一条注入的（改到透明停止点上，
 *  墨色一个字没变，判据照样判绿）。派生而不是硬写：渐变的序列化形状一变，这里立刻抛错出声。 */
const inkText = (tex) => {
  const m = [...String(tex).matchAll(/rgba?\([^)]*\)/g)].map((x) => x[0]);
  const ink = m.find((t) => !/^rgba\(0,\s*0,\s*0,\s*0\)$/.test(t));
  if (!ink) throw new Error("健康样本里找不到非透明墨色——渐变的序列化形状与注入假设不符");
  return ink;
};

/** 词表层的变异：每条只动一个字段，动的正是该检查赖以成立的那个量。
 *  `only` 声明「除本条以外其它检查仍须判绿」——一条改动同时打红两条，说明这两条判据共用
 *  一个未被隔离的量（本仓库出现过「业务码遮住调用方 bug」的同形缺陷），此时必须出声而不是各红一半。 */
const MUTS = [
  { check: "body-bg", label: "底色不等于 --bg", only: true, hurt: (d) => { d.screen.css["background-color"] = "rgb(1, 2, 3)"; } },
  { check: "body-bg", label: "--bg 取空（token 缺失）", only: true, hurt: (d) => { d.tokens["--bg"] = ""; } },
  { check: "grid-period", label: "纸感渐变整个没了", only: true, hurt: (d) => { d.gridTexture = "none"; } },
  { check: "grid-period", label: "所有墨色停止点一起改色（脱离 token）", only: true, hurt: (d) => { const ink = inkText(d.gridTexture); d.gridTexture = d.gridTexture.split(ink).join("rgba(11, 22, 33, 0.6)"); } },
  { check: "grid-period", label: "只改第一个墨色停止点（渐变里出现两种墨）", only: true, hurt: (d) => { const ink = inkText(d.gridTexture); const i = d.gridTexture.indexOf(ink); d.gridTexture = d.gridTexture.slice(0, i) + "rgba(11, 22, 33, 0.6)" + d.gridTexture.slice(i + ink.length); } },
  { check: "grid-period", label: "声明侧改色而页面没跟上", only: true, hurt: (d) => { d.tokens["--paper-rule"] = "rgba(30, 43, 35, 0.5)"; } },
  { check: "grid-period", label: "周期不等于 --s8", only: true, hurt: (d) => { d.tokens["--s8"] = "40px"; } },
  { check: "user-bubble", label: ".msg-user 不存在", only: true, hurt: (d) => { d.userBubble = null; } },
  { check: "user-bubble", label: "气泡底被加回来了", only: true, hurt: (d) => { d.userBubble.css["background-color"] = "rgb(63, 108, 73)"; } },
  { check: "user-bubble", label: "圆角被加回来了", only: true, hurt: (d) => { d.userBubble.css["border-radius"] = "14px"; } },
  { check: "user-bubble", label: "字号脱离 --fs-body", only: true, hurt: (d) => { d.userBubble.css["font-size"] = "13px"; } },
  { check: "user-bubble", label: "右缘间距脱离 gutter+滚动条", only: true, hurt: (d) => { d.userBubble.rightGapToScreen = 99; } },
  { check: "body-pitch", label: "正文行高脱离 --lh-read", only: true, hurt: (d) => { d.aiBody.css["line-height"] = "24px"; } },
  { check: "action-pitch", label: "图标行不等距", only: true, hurt: (d) => { d.actionCenterPitch = [44, 40, 44]; } },
  { check: "action-pitch", label: "等距但不等于按钮宽", only: true, hurt: (d) => { d.actionCenterPitch = [50, 50, 50]; } },
  { check: "action-pitch", label: "量不到按钮宽度", only: true, hurt: (d) => { d.actionCss = null; } },
  { check: "pill-inset", label: "胶囊与正文不同栏", only: true, hurt: (d) => { d.pillLeftInset = 24; } },
  { check: "pill-inset", label: "量不到胶囊左内缩", only: true, hurt: (d) => { d.pillLeftInset = null; } },
];

/** 与被判清单**无关**的裸字面量下限：用 len(CASES) 算自己的下限等于没有下限。 */
const MIN_CASES = 8;
const MIN_MUTS = 8;

const clone = (o) => JSON.parse(JSON.stringify(o));

/** 从一次真实运行的读数派生健康样本后，逐条注入违规喂回词表。
 *  返回失效条数；任何「注入后仍判绿」都计一条——那是判据是死的的直接证明。 */
function runVocabularyBattery(healthy, log) {
  const names = Object.keys(DOM_CHECKS);
  let bad = 0;

  /* 正控：健康样本必须让每条词表项都判绿。这一条不成立时后面的负向全部无意义，
     因为「全都红」的样本里注入什么都不会更红。 */
  const unhealthy = names.filter((n) => !DOM_CHECKS[n](clone(healthy)).ok);
  if (unhealthy.length) {
    log(`FAIL positive-control 健康样本上这些检查本就判红: ${unhealthy.join(", ")}——正控不成立，负向比对因此是空见证`);
    return unhealthy.length + 1;
  }
  log(`ok   positive-control ${names.length}/${names.length} 项在真实读数上判绿（形状由探针自己给出，非手写夹具）`);

  /* 覆盖：每条词表项至少要有「注入该违规必须判红」的用例；有项没负向就等于那条从没被验证过。 */
  const uncovered = names.filter((n) => !MUTS.some((m) => m.check === n));
  if (uncovered.length) {
    log(`FAIL negative-coverage 这些检查没有任何注入用例: ${uncovered.join(", ")}——判据分支未被验证`);
    bad += 1;
  }

  for (const m of MUTS) {
    const d = clone(healthy);
    try {
      m.hurt(d);
    } catch (e) {
      log(`FAIL ${m.check}/${m.label} 注入本身抛错: ${e.message}（健康样本的形状与变异假设不符）`);
      bad += 1;
      continue;
    }
    const v = DOM_CHECKS[m.check](d);
    if (v.ok) { log(`FAIL ${m.check}/${m.label} 注入后仍判绿: ${v.detail}`); bad += 1; continue; }
    let crossed = [];
    if (m.only) crossed = names.filter((n) => n !== m.check && !DOM_CHECKS[n](clone(d)).ok);
    if (crossed.length) {
      log(`FAIL ${m.check}/${m.label} 判红，但同一注入同时打红 ${crossed.join(", ")}——两条判据未隔离，归因不成立`);
      bad += 1;
      continue;
    }
    log(`ok   ${m.check}/${m.label} 判红且其它 ${names.length - 1} 项不受影响`);
  }
  return bad;
}

export async function selftest() {
  const parent = path.join(K.REPO, "artifacts", "tmp", "_verify1to1-selftest");
  fs.mkdirSync(parent, { recursive: true });
  const fixture = fs.mkdtempSync(path.join(parent, "run-"));
  let bad = 0;
  const profile = K.readJson(K.DEFAULT_PROFILE, "屏档案");
  const write = (name, obj) => fs.writeFileSync(path.join(fixture, name), JSON.stringify(obj, null, 2), "utf8");
  const chatProto = () => profile.screens.chat.prototype;

  /* 每条负向档案分支写**自己那份**文件：共用一个文件名会让后写的覆盖前写的，
     于是「缺 prototype 段」那条其实量的是别的用例留下的档案（本仓库出现过覆盖导致的假绿）。 */
  const withoutProto = { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat } } };
  delete withoutProto.screens.chat.prototype;
  write("no-proto.json", withoutProto);
  write("bad-html.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...chatProto(), html: "Docs/04-ux-design/no-such-prototype.html" } } } });
  /* 非法 UTF-8 的夹具要写**字节**：写成 JS 字符串再落盘，写入器会把它编码成合法序列，该分支永远不触发。 */
  fs.writeFileSync(path.join(fixture, "bad-utf8.json"), Buffer.concat([Buffer.from('{"screens": "'), Buffer.from([0xe0, 0xff, 0xff, 0x22])]));
  write("no-checks.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: (() => { const p = { ...chatProto() }; delete p.dom_checks; return p; })() } } });
  write("empty-checks.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...chatProto(), dom_checks: [] } } } });
  write("unknown-check.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...chatProto(), dom_checks: ["body-bg", "no-such-check"] } } } });
  write("bad-wait.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...chatProto(), wait_for: ".no-such-node" } } } });
  write("bad-click.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...chatProto(), clicks: ["#no-such-click-target"] } } } });

  /* 两支 HTML 桩：桩里不引 React/Babel，所以 .screen 一定等不到；#root 有没有子元素，
     正是仪器用来分「结构不符(1)」与「被核对象没起来(3)」的那个观测量。桩写成绝对路径进档案——
     htmlUrl 只在相对时按 REPO 拼，按相对算会让夹具目录的位置决定判决。 */
  const stubHtml = (name, inner) => fs.writeFileSync(path.join(fixture, name),
    `<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>桩</title></head>`
      + `<body><div id="root">${inner}</div></body></html>\n`, "utf8");
  stubHtml("stub-rendered.html", '<div class="other">渲出了内容，但没有 .screen</div>');
  stubHtml("stub-empty.html", "");
  const withHtml = (abs) => ({ ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...chatProto(), html: abs } } } });
  write("stub-rendered.json", withHtml(path.join(fixture, "stub-rendered.html")));
  write("stub-empty.json", withHtml(path.join(fixture, "stub-empty.html")));

  if (CASES.length < MIN_CASES || MUTS.length < MIN_MUTS) {
    console.log(`FAIL selftest-floor declared cases=${CASES.length}/${MIN_CASES} muts=${MUTS.length}/${MIN_MUTS} —— 清单被截短时判据不能为空见证`);
    fs.rmSync(fixture, { recursive: true, force: true });
    console.log("EXITCODE=3");
    return 3;
  }

  const lines = [];
  const log = (s) => lines.push(s);

  for (const c of CASES) {
    const t0 = Date.now();
    let rc = 3;
    /* 逐用例截住 console.log：**原样转发**（产物里的顺序与字节不变），同时留一份本用例自己的
       回显，用来核对 witness 那条判决行确实出现过。截不住就等于「只用 rc 当判据」。 */
    const echoed = [];
    const real = console.log;
    console.log = (...a) => { echoed.push(a.map((x) => String(x)).join(" ")); real.apply(console, a); };
    try {
      rc = await K.mainTool(() => run(c.args(fixture)));
    } catch (e) {
      log(`FAIL ${c.name} threw outside mainTool: ${e && e.message}`);
    } finally {
      console.log = real;
    }
    const secs = ((Date.now() - t0) / 1000).toFixed(1);
    const miss = c.witness && !echoed.some((l) => l.startsWith(`FAIL ${c.witness} `));
    if (rc === c.want && !miss) log(`ok   ${c.name} rc=${rc} (${secs}s, want=${c.want})`);
    else if (rc === c.want) {
      log(`FAIL ${c.name} rc=${rc} want=${c.want}，但那条分支没走到：输出里没有 FAIL ${c.witness} ——`
        + `同一个 rc 可以由别的分支给出（最后一行回显：${echoed.filter((l) => l.startsWith("FAIL ")).slice(-1)[0] || "(无 FAIL 行)"}`);
      bad += 1;
    }
    else { log(`FAIL ${c.name} rc=${rc} want=${c.want} (${secs}s)`); bad += 1; }
  }

  /* 空见证兜底：判据一条都没有时 finish() 必须报 3，而不是 PASS。 */
  const empty = new K.Ledger();
  const emptyRc = await K.finish(empty, { command: "(unit) 空判据", header: [] });
  if (emptyRc === 3) log("ok   neg-empty-ledger rc=3 (unit, want=3)");
  else { log(`FAIL neg-empty-ledger rc=${emptyRc} want=3`); bad += 1; }

  /* 词表层的前置条件：健康样本来自上面 pos 用例刚落盘的读数。拿不到就**立即中止并返回独立码**，
     不得跑完再解释——「harness 没材料」与「判据判红」是两种结论。 */
  const domPath = path.join(fixture, "dom.json");
  if (!fs.existsSync(domPath)) {
    for (const l of lines) console.log(l);
    console.log(`FAIL vocabulary-layer-aborted 健康样本 ${domPath} 不存在——浏览器层没跑出读数，词表层从未被验证`);
    console.log("EXITCODE=3");
    fs.rmSync(fixture, { recursive: true, force: true });
    return 3;
  }
  const healthy = K.readJson(domPath, "真实 DOM 读数");
  bad += runVocabularyBattery(healthy, log);

  for (const l of lines) console.log(l);
  fs.rmSync(fixture, { recursive: true, force: true });
  const total = CASES.length + 1 + 1 + MUTS.length;
  console.log(`SELFTEST ${bad ? "FAIL" : "PASS"} cases=${total} cli=${CASES.length} muts=${MUTS.length} failures=${bad}`);
  console.log(`EXITCODE=${bad ? 1 : 0}`);
  return bad ? 1 : 0;
}

if (K.isMain(import.meta.url)) process.exitCode = await K.mainTool(() => selftest());
