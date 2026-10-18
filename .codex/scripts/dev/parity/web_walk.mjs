#!/usr/bin/env node
/**
 * Web 侧「走一遍」仪器（T-2026-10-18-020 的运行证据面，复用 parity_cdp.mjs 的浏览器驱动）。
 *
 * 它证明的是采集夹具证明不了的那一半：H5 真的在浏览器里跑起来、每一屏读得出正文、
 * 点得动的入口真的换屏。判据全部来自页面 DOM（`data-screen` / `data-node` 两端共用同一个
 * resource-id 身份），截图只是留证，不参与判定。
 *
 * 用法：node .codex/scripts/dev/parity/web_walk.mjs --url http://127.0.0.1:5173/ --out <dir>
 *       pnpm --dir frontend preview   # 构建产物要有本机静态服务：file:// 下浏览器按 CORS 取模块，产物跑不起来
 *       node .codex/scripts/dev/parity/web_walk.mjs --url http://127.0.0.1:4173/ --out <dir>
 *       node .codex/scripts/dev/parity/web_walk.mjs --selftest    # 不启动浏览器，只按夹具核走查表
 * 退出码：0 全部成立 / 1 某屏读不出或点不动 / 2 用法错误 / 3 仪器故障（起不来浏览器、导航不生效）。
 */
import fs from "node:fs";
import path from "node:path";
import * as K from "./parity_cdp.mjs";

const SPEC = ["--url", "--out", "--attach", "--browser", "--wait-ms", "--only"];

/** 视口按采集夹具那份设备事实（ALP-AN00：1200x2652@520dpi → 369.2x816 dp）。 */
const VIEWPORT = { width: 369, height: 816, scale: 2, deviceScaleFactor: 2 };

/**
 * 走查表：每步「期望在哪一屏、点哪个节点去下一步」。
 * `click` 选择器里的 data-node 要么是夹具里那个可点 resource-id，要么是 Web 侧自造的
 * 出口（`state_back`）或底部标签（`data-tab`）。--selftest 拿夹具逐条核这张表。
 *
 * 正文多少不由这张表说了算：每屏的判据是**夹具里每一段可见正文都必须在 H5 上出现**
 * （`missing` 为空），行数只是留证。自造下限会随实现漂移，覆盖判据不会。
 */
const WALK = [
  { screen: "home", click: '[data-node="home_topic_card"]' },
  { screen: "chat", click: '[data-node="chat_settings"]' },
  { screen: "settings", click: '[data-node="settings_key_entry"]' },
  { screen: "settingskey", click: '[data-node="settings_key_back"]' },
  { screen: "settings", click: '[data-node="settings_providers_entry"]' },
  { screen: "provideradmin", click: '[data-node="providers_back"]' },
  { screen: "settings", click: '[data-node="settings_models_entry"]' },
  { screen: "modelsettings", click: '[data-node="state_back"]' },
  { screen: "settings", click: '[data-node="settings_search_entry"]' },
  { screen: "searchservicesettings", click: '[data-node="state_back"]' },
  { screen: "settings", click: '[data-node="settings_voice_entry"]' },
  { screen: "voicesettings", click: '[data-node="state_back"]' },
  { screen: "settings", click: '[data-node="settings_files_entry"]' },
  { screen: "filemanager", click: '[data-node="state_back"]' },
  { screen: "settings", click: '[data-tab="library"]' },
  { screen: "library", click: '[data-node="library_topic"]' },
  { screen: "chat", click: '[data-node="chat_overview"]' },
  { screen: "treecanvas", click: '[data-node="tree_back"]' },
  { screen: "chat", click: '[data-node="chat_nav"]' },
  { screen: "navigation", click: '[data-node="navigation_back"]' },
  { screen: "chat", click: '[data-tab="home"]' },
  { screen: "home", click: '[data-node="home_drawer_button"]' },
  { screen: "homedrawer", click: '[data-node="state_back"]' },
  { screen: "home", click: null }
];

/** 走查表里由 Web 侧自己造出来的出口：它们不会出现在夹具的 resource-id 名单里。 */
const WEB_ONLY_NODES = new Set(["state_back"]);
const ROW_SELECTOR = ".row,.row-title,.topic-title,.rec-text,.suggestion-text,.chat-answer-run,.node-plain,.segment,.appbar-title,.app-tab,.composer-hint,.chip,.section-title,.section-count";

/**
 * 豁免只按**规则**给，逐条列 id 会在夹具变化后变成一张谁也不看的清单。
 * 目前只有一条：包内脱敏。sync_fixtures 把夹具里长得像可加载地址的 URL 换成
 * 〔地址已在包内脱敏〕（离线包不得含外部加载 URL，见 check_offline_bundle.mjs §3），
 * 所以那几段 Android 正文在 H5 上必然不出现——这是两端各自的约束撞出来的差额，不是漏画。
 */
const TEXT_EXEMPT_RULES = [
  { test: (t) => /https?:\/\//.test(t), reason: "包内脱敏后的端点/模型地址" }
];

function exemptReason(text) {
  for (const rule of TEXT_EXEMPT_RULES) if (rule.test(text)) return rule.reason;
  return null;
}

function fault(message) {
  return new K.Fault(message);
}

/**
 * 夹具名单，走查表的权威，不靠人回忆 Android 上有哪些入口：
 * `screens` 屏名单 / `clickable` 每屏可点 id / `expect` 每屏必须出现在 H5 上的正文。
 */
function fixtureAuthority() {
  const dir = path.join(K.REPO, "artifacts", "fixtures", "parity");
  if (!fs.existsSync(dir)) throw fault(`夹具目录不存在：${dir}（先跑 capture_screen.py --all）`);
  const screens = [];
  const clickable = new Map();
  const expect = new Map();
  for (const file of fs.readdirSync(dir).filter((f) => f.endsWith("-android.json")).sort()) {
    const screen = file.slice(0, -"-android.json".length);
    const env = JSON.parse(fs.readFileSync(path.join(dir, file), "utf8"));
    const nodes = env?.payload?.nodes ?? [];
    screens.push(screen);
    clickable.set(screen, new Set(nodes.filter((n) => n.clickable && n.id).map((n) => n.id)));
    const kept = [];
    let exempted = 0;
    for (const node of nodes) {
      const text = String(node.text ?? "").trim();
      if (text === "") continue;
      if (exemptReason(text) !== null) { exempted += 1; continue; }
      kept.push({ id: node.id, text });
    }
    expect.set(screen, { kept, exempted });
  }
  if (screens.length === 0) throw fault("夹具目录里一条 *-android.json 都没有：零集合不构成通过");
  return { screens, clickable, expect };
}

function nodeIdOf(selector) {
  const m = /^\[data-node="([^"]+)"\]$/.exec(selector ?? "");
  return m ? m[1] : null;
}

/** 只核走查表这张纸：每一步的期望屏在不在名单里、点得动的目标在那一屏的夹具里是不是真可点。 */
export function checkWalk() {
  const { screens, clickable, expect } = fixtureAuthority();
  const bad = [];
  WALK.forEach((step, i) => {
    const next = WALK[i + 1];
    if (!screens.includes(step.screen)) bad.push(`step${i} 期望屏 ${step.screen} 不在夹具名单里`);
    const exp = expect.get(step.screen);
    if (!exp || exp.kept.length === 0) bad.push(`step${i} ${step.screen} 的夹具读不出任何正文，覆盖判据无从成立`);
    if (next === undefined) {
      if (step.click !== null) bad.push(`step${i} 最后一步不该还带点击`);
      return;
    }
    if (!step.click) { bad.push(`step${i} ${step.screen} 没有点击，却还有下一步`); return; }
    const id = nodeIdOf(step.click);
    if (id === null) {
      if (!/^\[data-tab="(home|library)"\]$/.test(step.click)) {
        bad.push(`step${i} 点击选择器认不出身份：${step.click}`);
      }
      return;
    }
    if (WEB_ONLY_NODES.has(id)) return;
    const here = clickable.get(step.screen);
    if (!here) { bad.push(`step${i} 夹具读不出 ${step.screen} 的可点名单`); return; }
    if (!here.has(id)) bad.push(`step${i} ${step.screen} 里 ${id} 不是可点节点（入口在夹具里是死的）`);
    if (!screens.includes(next.screen)) bad.push(`step${i} 点 ${id} 去的 ${next.screen} 不在夹具名单里`);
  });
  const walked = new Set(WALK.map((s) => s.screen));
  const unwalked = screens.filter((s) => !walked.has(s));
  return { steps: WALK.length, screens: screens.length, walked: walked.size, unwalked, expect, bad };
}

async function probeScreen(cdp, screen, expected) {
  return cdp.eval(`(function(){
    var s=document.querySelector('[data-screen="${screen}"]');
    if(!s) return null;
    var norm=function(t){return (t||"").replace(/\\s+/g,"").trim();};
    var page=norm(document.body.textContent);
    var exp=${JSON.stringify(expected)};
    var missing=[];
    for(var i=0;i<exp.length;i++){
      if(page.indexOf(norm(exp[i].text))<0) missing.push({id:exp[i].id,text:exp[i].text});
    }
    var rows=Array.from(s.querySelectorAll(${JSON.stringify(ROW_SELECTOR)}))
      .map(function(e){return (e.textContent||"").trim();}).filter(function(t){return t.length>0;});
    var title=(s.querySelector(".appbar-title")||{}).textContent||"";
    return {rows: rows.length, chars: rows.join("").length, title: title.trim(),
      taps: s.querySelectorAll("button.node-tap").length,
      expect: exp.length, missing: missing};
  })()`);
}

async function navigate(cdp, url, ledger) {
  const navigated = async () => {
    const nav = await cdp.send("Page.navigate", { url });
    if (nav && nav.errorText) throw fault(`导航被浏览器拒绝: ${nav.errorText}（目标 ${url}）`);
    return cdp.waitUntil("location.href !== 'about:blank'", 8000);
  };
  if (!(await navigated()) && !(await navigated())) {
    const stuck = await cdp.eval("({href: location.href, ready: document.readyState})");
    throw fault(`导航两次都没生效：标签页仍在 ${stuck.href}（readyState=${stuck.ready}，目标 ${url}）`);
  }
  if (!(await cdp.waitFor('[data-screen="home"]', 30000))) {
    const probe = await cdp.eval("({ready: document.readyState, href: location.href, body: document.body.innerHTML.slice(0,160)})");
    ledger.fail(`page-not-rendered 30s 内没出现主页（readyState=${probe.ready} href=${probe.href} body=${probe.body}）`);
    return false;
  }
  return true;
}

export async function run(argv) {
  const cmd = ["web_walk.mjs", ...argv].join(" ");
  const { opts } = K.parseArgs(argv, SPEC);
  const url = opts["--url"] || "";
  if (url === "") throw new K.Usage("必须给 --url：H5 要么由 dev server 提供，要么给 dist 的 file:/// 路径");
  if (!opts["--out"]) throw new K.Usage("必须给 --out <dir>：逐屏留证图显式给目录，避免覆盖已归档基线");
  const out = path.resolve(opts["--out"]);
  fs.mkdirSync(out, { recursive: true });
  const waitMs = opts["--wait-ms"] ? parseInt(opts["--wait-ms"], 10) : 4000;
  const only = opts["--only"] ? parseInt(opts["--only"], 10) : null;

  const plan = checkWalk();
  const header = [`url ${url}`, `viewport ${VIEWPORT.width}x${VIEWPORT.height}@${VIEWPORT.deviceScaleFactor}`,
    `walk steps=${plan.steps} fixture_screens=${plan.screens}`];
  const ledger = new K.Ledger();
  if (plan.bad.length > 0) {
    for (const b of plan.bad) ledger.fail(`walk-plan ${b}`);
    return await K.finish(ledger, { command: cmd, header });
  }
  ledger.ok(`walk-plan = ${plan.steps} 步全部落在 ${plan.screens} 屏夹具的可点名单上`);

  const browser = K.findBrowser(opts["--browser"]);
  const br = await K.openBrowser(browser, opts["--attach"] ? parseInt(opts["--attach"], 10) : 0);
  const consoleErrors = [];
  try {
    br.ws.addEventListener("message", (e) => {
      let m;
      try { m = JSON.parse(e.data); } catch { return; }
      if (m.method === "Runtime.consoleAPICalled" && (m.params.type === "error" || m.params.type === "assert")) {
        consoleErrors.push((m.params.args || []).map((a) => a.value ?? a.description ?? a.type).join(" "));
      }
      if (m.method === "Log.entryAdded" && m.params.entry.level === "error") {
        const entry = m.params.entry;
        consoleErrors.push(`${entry.source}: ${entry.text}${entry.url ? ` (${String(entry.url).slice(0, 120)})` : ""}`);
      }
      /* Network 这一路是为「只说一句 404、不说是谁」准备的：报出 URL 才能判定它是页面缺陷还是浏览器自发的探测。 */
      if (m.method === "Network.responseReceived") {
        const r = m.params.response ?? {};
        if (r.status >= 400) consoleErrors.push(`http ${r.status} ${String(r.url ?? "").slice(0, 160)}`);
      }
      if (m.method === "Network.loadingFailed") {
        consoleErrors.push(`load-failed ${m.params.type}: ${m.params.errorText}`);
      }
    });
    await br.cdp.send("Page.enable");
    await br.cdp.send("Runtime.enable");
    await br.cdp.send("Log.enable");
    await br.cdp.send("Network.enable");
    await br.cdp.send("Emulation.setDeviceMetricsOverride", {
      width: VIEWPORT.width, height: VIEWPORT.height, deviceScaleFactor: VIEWPORT.deviceScaleFactor, mobile: true
    });
    ledger.ok(`browser = ${await br.cdp.version()}`);
    if (br.relaunches) ledger.ok(`browser-relaunch = ${br.relaunches} 次早期退出后起来（Windows 启动器交接竞态）`);
    if (!(await navigate(br.cdp, url, ledger))) {
      /* 页面起不来时原因几乎都在控制台：把它先倒出来，否则「没渲染」只剩一句现象，归因不了。 */
      for (const n of consoleErrors.slice(0, 5)) ledger.fail(`console-error ${n.slice(0, 220)}`);
      if (url.startsWith("file://")) {
        ledger.fail("file-url 提示 file:// 下浏览器会以 CORS 模式取外部 ES 模块，dist/ 那种带 assets/ 的产物双击必空白；"
          + "要在 file:// 下真实可用就喂 frontend/scripts/inline_offline.mjs 产出的 dist-offline/index.html（模块已内联，不发请求）");
      }
      return await K.finish(ledger, { command: cmd, header });
    }

    const tabTitle = String((await br.cdp.eval("document.title")) ?? "").trim();
    if (tabTitle === "") ledger.fail("tab-title 浏览器标签页上没有应用名，多开了就认不出是哪一屏");
    else ledger.ok(`tab-title = "${tabTitle}"`);

    const limit = only === null ? WALK.length : Math.min(only, WALK.length);
    for (let i = 0; i < limit; i += 1) {
      const step = WALK[i];
      const exp = plan.expect.get(step.screen);
      const got = await probeScreen(br.cdp, step.screen, exp.kept);
      if (got === null) {
        ledger.fail(`step${i} 不在 ${step.screen}（页面上没有那一屏的根，换屏没发生或渲染失败）`);
        break;
      }
      if (got.missing.length > 0) {
        const shown = got.missing.slice(0, 4).map((m) => `${m.id}:${m.text.slice(0, 18)}`).join(" / ");
        ledger.fail(`step${i} ${step.screen} 少了 ${got.missing.length}/${got.expect} 段 Android 正文（${shown}${got.missing.length > 4 ? " …" : ""}）`);
      } else {
        ledger.ok(`step${i} ${step.screen} 正文全覆盖 ${got.expect} 段 rows=${got.rows} chars=${got.chars} taps=${got.taps} title="${got.title}"`);
      }
      const shot = await br.cdp.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true });
      const png = path.join(out, `web-${String(i).padStart(2, "0")}-${step.screen}.png`);
      fs.writeFileSync(png, Buffer.from(shot.data, "base64"));
      if (i + 1 >= limit || step.click === null) continue;
      if (!(await br.cdp.has(step.click))) {
        ledger.fail(`step${i} 点不动：${step.click} 在 ${step.screen} 上不存在`);
        break;
      }
      await br.cdp.eval(`(document.querySelector(${JSON.stringify(step.click)}).click(), true)`);
      const arrived = await br.cdp.waitUntil(
        `!!document.querySelector('[data-screen="${WALK[i + 1].screen}"]')`, waitMs, 150);
      if (!arrived) {
        const now = await br.cdp.eval(`(document.querySelector("section.screen")||{}).dataset?.screen||""`);
        ledger.fail(`step${i} 点 ${step.click} 之后 ${waitMs}ms 内没到 ${WALK[i + 1].screen}（当前 ${now || "无屏"}）`);
        break;
      }
    }
    const noise = consoleErrors.filter((t) => !/\/favicon\.ico/i.test(t));
    if (noise.length > 0) {
      for (const n of noise.slice(0, 5)) ledger.fail(`console-error ${n.slice(0, 200)}`);
    } else {
      ledger.ok(`console = 0 条错误（按 URL 认出的 favicon 探测不计：${consoleErrors.length - noise.length} 条）`);
    }
    ledger.ok(`shots = ${out}`);
    return await K.finish(ledger, { command: cmd, header });
  } finally {
    await br.close();
  }
}

function selftest() {
  const plan = checkWalk();
  const cases = [];
  const add = (name, ok, detail) => cases.push({ name, ok, detail });
  add("走查表每步的期望屏都能在夹具里找到", plan.bad.length === 0, plan.bad.slice(0, 3).join(" | ") || `steps=${plan.steps}`);
  add("每一屏采集到的都被走到", plan.unwalked.length === 0,
    plan.unwalked.length === 0 ? `walked=${plan.walked}/${plan.screens}` : `没走到：${plan.unwalked.join(",")}`);
  add("最后一步不带点击", WALK[WALK.length - 1]?.click === null, JSON.stringify(WALK[WALK.length - 1]?.click));
  add("data-node 选择器都能读出身份", nodeIdOf('[data-node="chat_nav"]') === "chat_nav"
    && nodeIdOf('[data-tab="home"]') === null, `${nodeIdOf('[data-node="chat_nav"]')} / ${nodeIdOf('[data-tab="home"]')}`);
  const rows = [...plan.expect.entries()];
  const emptied = rows.filter(([, e]) => e.kept.length === 0);
  const heavy = rows.filter(([, e]) => e.exempted > 0 && e.exempted >= e.kept.length);
  add("每一屏都还剩得出正文（豁免没把屏幕吃空）", emptied.length === 0,
    emptied.length > 0 ? `无正文可比：${emptied.map(([s]) => s).join(",")}`
      : `${rows.length} 屏都有可比正文，豁免合计=${rows.reduce((a, [, e]) => a + e.exempted, 0)} 段（全是 URL 形状）`);
  add("单屏豁免不过半（过半就该人来判是不是漏画）", heavy.length === 0,
    heavy.length > 0 ? `豁免≥正文：${heavy.map(([s, e]) => `${s}(${e.exempted}vs${e.kept})`).join(",")}`
      : `没有任何一屏的豁免段数 ≥ 需覆盖段数`);
  console.log(`WEB-WALK SELFTEST screens=${plan.screens} steps=${plan.steps}`);
  let bad = 0;
  for (const c of cases) {
    console.log(`${c.ok ? "PASS" : "FAIL"} ${c.name} — ${c.detail}`);
    if (!c.ok) bad += 1;
  }
  console.log(`SELFTEST cases=${cases.length} bad=${bad}`);
  console.log(`EXITCODE=${bad === 0 ? K.EXIT.OK : K.EXIT.FAIL}`);
  return bad === 0 ? K.EXIT.OK : K.EXIT.FAIL;
}

if (K.isMain(import.meta.url)) {
  const argv = process.argv.slice(2);
  process.exitCode = await K.mainTool(async () => {
    if (argv.includes("--help")) {
      console.log("usage: node .codex/scripts/dev/parity/web_walk.mjs --url <http://本机服务> --out <dir> [--only N] | --selftest");
      console.log("       dev: pnpm --dir frontend dev → 5173；产物: pnpm --dir frontend preview → 4173");
      console.log(`viewport ${VIEWPORT.width}x${VIEWPORT.height}@${VIEWPORT.deviceScaleFactor} walk steps=${WALK.length}`);
      console.log(`EXITCODE=${K.EXIT.USAGE}`);
      return K.EXIT.USAGE;
    }
    if (argv.includes("--selftest")) return selftest();
    if (!argv.length) throw new K.Usage("给 --url/--out 或 --selftest");
    return run(argv);
  });
}
