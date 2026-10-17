/**
 * 1:1 闭环量测的共享仪器层（T-2026-10-15-006）。零第三方依赖：Node 内置 fetch / WebSocket
 * 加 Edge headless 的 CDP。两支工具（cdp_shot.mjs 与待收编的 verify_1to1.mjs）共用同一份屏档案、
 * 同一个 CDP 客户端与同一套退出码档位；**判定口径不在这里**，这里只管能不能量、量不到算哪一档。
 * DOM 判据词表属 T-2026-10-15-009（verify_1to1 那半边），本片不预置无人消费的实现。
 *
 * 退出码：0=跑完且判据全成立 1=跑完且有判据判 FAIL 2=用法错误 3=仪器自身故障。
 * 「没跑完」与「跑完发现坏了」是两种结论，不得共用退出码。
 */
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const EXIT = { OK: 0, FAIL: 1, USAGE: 2, FAULT: 3 };
export const HERE = path.dirname(fileURLToPath(import.meta.url));
export const REPO = path.resolve(HERE, "..", "..", "..", "..");
export const DEFAULT_PROFILE = path.join(HERE, "screens.json");

/** 仪器自身故障（缺浏览器、缺 HTML、CDP 握手失败、档案读不出、未捕获异常）。 */
export class Fault extends Error {}
/** 调用形态错误（未知选项、缺参数、--screen 与 --url 同时给）。 */
export class Usage extends Error {}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 给「等一个可能永远不来的应答」上界：CDP 套接字已关时 send() 的 promise 永不 settle，
 *  顶层 await 因此挂着，Node 会以 unsettled-top-level-await 警告退出（实测码 13）——
 *  那会把本次真正的判定码盖掉。收尾路径一律包这一层。 */
const withTimeout = (promise, ms, label) => Promise.race([
  promise,
  sleep(ms).then(() => { throw new Error(`${label} 在 ${ms}ms 内没有应答`); }),
]);

/* ---------------------------------------------------------------- 参数 */

export function parseArgs(argv, spec) {
  const opts = {};
  const seen = [];
  for (let i = 0; i < argv.length; i += 2) {
    const flag = argv[i];
    if (flag === "--selftest" || flag === "--help") { i -= 1; continue; }
    if (!flag.startsWith("--")) throw new Usage(`不是选项的参数: ${flag}`);
    if (!spec.includes(flag)) throw new Usage(`未知选项 ${flag}（可用: ${spec.join(" ")}）`);
    const value = argv[i + 1];
    if (value === undefined || value.startsWith("--")) throw new Usage(`${flag} 缺参数值`);
    if (opts[flag] !== undefined) throw new Usage(`${flag} 重复出现`);
    opts[flag] = value;
    seen.push(flag);
  }
  return { opts, seen };
}

/* ---------------------------------------------------------------- 档案 */

export function readJson(absPath, what) {
  if (!fs.existsSync(absPath)) throw new Fault(`${what} 不存在: ${absPath}`);
  let raw;
  try {
    raw = fs.readFileSync(absPath);
  } catch (e) {
    throw new Fault(`${what} 读不出来: ${absPath} (${e.message})`);
  }
  /* 编码判定必须落在**字节**上：文本模式读入后非法序列早已被替换成 U+FFFD，
     而"检查解码结果里有没有替换字符"这一句极易被写成空串子串测试（恒真，实测打通过本仪器）。
     fatal:true 让解码器自己在遇到非法 UTF-8 时抛，判据不依赖任何肉眼可见的字符。 */
  try {
    new TextDecoder("utf-8", { fatal: true }).decode(raw);
  } catch (e) {
    throw new Fault(`${what} 不是合法 UTF-8: ${absPath} (${e.message})`);
  }
  try {
    return JSON.parse(raw.toString("utf8"));
  } catch (e) {
    throw new Fault(`${what} 不是合法 JSON: ${absPath} (${e.message})`);
  }
}

export function pickScreen(profile, name) {
  const screens = profile.screens || {};
  if (!Object.prototype.hasOwnProperty.call(screens, name)) {
    throw new Usage(`屏档案里没有 "${name}"；可用: ${Object.keys(screens).join(", ") || "(空)"}`);
  }
  return screens[name];
}

/** 回放段缺失必须判 3，而不是「没有点击所以直接量」——后者会把空跑读成一次成功闭环。 */
export function prototypeOf(entry, name) {
  const p = entry && entry.prototype;
  if (!p) throw new Fault(`屏 "${name}" 的档案没有 prototype 回放段`);
  for (const key of ["html", "clicks", "wait_for"]) {
    if (!p[key]) throw new Fault(`屏 "${name}" 的 prototype 段缺 ${key}`);
  }
  if (!Array.isArray(p.clicks) || !p.clicks.length) throw new Fault(`屏 "${name}" 的 clicks 不是非空数组`);
  return p;
}

export function htmlUrl(prototype) {
  const abs = path.isAbsolute(prototype.html) ? prototype.html : path.join(REPO, prototype.html);
  if (!fs.existsSync(abs)) throw new Fault(`原型 HTML 不存在: ${abs}`);
  return { abs, url: encodeURI("file:///" + abs.split(path.sep).join("/")) };
}

/* ---------------------------------------------------------------- 浏览器 */

const CANDIDATES = [
  "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  "/c/Program Files/Microsoft/Edge/Application/msedge.exe",
];

const toNative = (p) => p.replace(/^\/c\//i, "C:/").replace(/\//g, path.sep);

/**
 * 可执行文件位置不写死单一来源：--browser > 环境变量 > 逐候选探测；找不到一律 3。
 * 显式给定时**不回退**：--browser 指到一个不存在的路径必须判 3，否则「按 --browser
 * 注入一个坏路径」这个负向用例会静默走到真浏览器上，自检就成了空见证。
 */
export function findBrowser(explicit) {
  const override = explicit || process.env.PARITY_BROWSER || "";
  const source = explicit ? "--browser" : (process.env.PARITY_BROWSER ? "PARITY_BROWSER" : "probe");
  const tries = override ? [override] : CANDIDATES;
  for (const candidate of tries) {
    const win = toNative(candidate);
    if (fs.existsSync(win)) return { path: win, source };
  }
  throw new Fault(`找不到 Edge 可执行文件（试过: ${tries.join(" | ")}）；用 --browser <路径> 指定`);
}

export class Cdp {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    ws.addEventListener("message", (e) => {
      let m;
      try { m = JSON.parse(e.data); } catch { return; }
      if (m.id && this.pending.has(m.id)) {
        const { res, rej } = this.pending.get(m.id);
        this.pending.delete(m.id);
        m.error ? rej(new Error(JSON.stringify(m.error))) : res(m.result);
      }
    });
  }

  static async connect(port) {
    let tab;
    try {
      const r = await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: "PUT" });
      tab = await r.json();
    } catch (e) {
      throw new Fault(`CDP 握手失败（端口 ${port} 没有应答: ${e.message}）`);
    }
    if (!tab || !tab.webSocketDebuggerUrl) throw new Fault(`CDP 端口 ${port} 应答里没有 webSocketDebuggerUrl`);
    const ws = new WebSocket(tab.webSocketDebuggerUrl);
    const opened = await new Promise((res) => {
      ws.addEventListener("open", () => res(true));
      ws.addEventListener("error", () => res(false));
      setTimeout(() => res(false), 15000);
    });
    if (!opened) throw new Fault(`CDP WebSocket 连不上（端口 ${port}）`);
    return { cdp: new Cdp(ws), tab, ws };
  }

  send(method, params = {}) {
    const id = ++this.id;
    return new Promise((res, rej) => {
      this.pending.set(id, { res, rej });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  async eval(expr) {
    const r = await this.send("Runtime.evaluate", {
      expression: `(()=>{ try { return JSON.stringify(${expr}); } catch(e){ return JSON.stringify({__err:String(e),stack:e.stack}); } })()`,
      returnByValue: true,
    });
    return r.result && r.result.value !== undefined ? JSON.parse(r.result.value) : undefined;
  }

  async has(sel) {
    return !!(await this.eval(`!!document.querySelector(${JSON.stringify(sel)})`));
  }

  /** 轮询任意 JS 谓词。waitFor 是它的选择器特例。 */
  async waitUntil(expr, timeoutMs = 45000, stepMs = 300) {
    const t0 = Date.now();
    while (Date.now() - t0 < timeoutMs) {
      if (await this.eval(`!!(${expr})`)) return true;
      await sleep(stepMs);
    }
    return false;
  }

  async waitFor(sel, timeoutMs = 45000) {
    return this.waitUntil(`document.querySelector(${JSON.stringify(sel)})`, timeoutMs);
  }

  async version() {
    const v = await this.send("Browser.getVersion");
    return `${v.product} (${v.revision}, protocol ${v.protocolVersion})`;
  }
}

/**
 * 默认自己起一个无头实例：端口 9222 在本机可能被 **别人的**东西占着（实测被
 * `adb forward tcp:9222` 占过，HTTP 探针直接失败），所以既不假设它、也不去动它。
 * --attach <port> 是给「已经有调试实例」的逃生口，那种情况下不关闭别人的浏览器。
 */
async function launchOnce(browser) {
  const udd = fs.mkdtempSync(path.join(os.tmpdir(), "parity-cdp-"));
  const proc = spawn(browser.path, [
    "--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
    "--remote-debugging-port=0", `--user-data-dir=${udd}`, "about:blank",
  ], { stdio: "ignore" });
  /* spawn 的 ENOENT 是异步 emit 的：在事件回调里 throw 会变成未捕获异常，
     进程带着「没有任何 EXITCODE 行」死掉，档位 3 因此拿不到。这里把它收进变量，
     由下面的等待循环当作仪器故障报出。 */
  let spawnError = null;
  proc.on("error", (e) => { spawnError = e; });
  const portFile = path.join(udd, "DevToolsActivePort");
  let port = null;
  for (let i = 0; i < 60 && !port; i += 1) {
    if (spawnError) { rmDir(udd); throw new Fault(`启动浏览器失败: ${spawnError.message}`); }
    if (proc.exitCode !== null) {
      rmDir(udd);
      /* 可重试：Windows 上 msedge.exe 是启动器，它发现「已经有一个 Edge」就交接后退出 0，
         交接对象是上一条用例正在散架的那个实例——所以早期退出既是仪器故障，也是竞态。 */
      const e = new Fault(`浏览器进程提前退出（code=${proc.exitCode}），DevToolsActivePort 未出现`);
      e.retryable = true;
      throw e;
    }
    await sleep(500);
    if (fs.existsSync(portFile)) port = parseInt(fs.readFileSync(portFile, "utf8").split("\n")[0].trim(), 10);
  }
  if (!port) {
    proc.kill(); rmDir(udd);
    const e = new Fault(`浏览器起不来：DevToolsActivePort 在 30s 内没出现（profile=${udd}）`);
    e.retryable = true;
    throw e;
  }
  const { cdp, tab, ws } = await Cdp.connect(port);
  return {
    cdp, tab, ws, port, owned: true, udd,
    close: async () => {
      /* 顺序要紧：先在有应答的套接字上请浏览器退出，再关 ws。
         反过来会让 Browser.close 挂在一个已关的套接字上永不 settle。 */
      try { await withTimeout(cdp.send("Browser.close"), 3000, "Browser.close"); } catch { /* 已经关了 */ }
      try { ws.close(); } catch { /* 已经关了 */ }
      await closeTab(port, tab);
      proc.kill();
      /* 等它真的没了再返回：不等就会把「上一条用例的浏览器还在退」这个竞态交给下一条用例。 */
      try { await withTimeout(onceExit(proc), 8000, "browser exit"); } catch { /* 强杀后仍没回报就继续 */ }
      let removed = rmDir(udd);
      if (!removed) { await sleep(500); removed = rmDir(udd); }
      if (!removed) console.error(`WARN user-data-dir 没能删除（浏览器子进程仍持有）: ${udd}`);
    },
  };
}

const onceExit = (proc) => new Promise((res) => {
  if (proc.exitCode !== null) res();
  else proc.once("exit", () => res());
});

/**
 * 默认自己起一个无头实例：端口 9222 在本机可能被 **别人的**东西占着（实测被
 * `adb forward tcp:9222` 占过，HTTP 探针直接失败），所以既不假设它、也不去动它。
 * --attach <port> 是给「已经有调试实例」的逃生口，那种情况下不关闭别人的浏览器。
 */
export async function openBrowser(browser, attachPort, attempts = 3) {
  if (attachPort) {
    const { cdp, tab, ws } = await Cdp.connect(attachPort);
    return {
      cdp, tab, ws, port: attachPort, owned: false,
      /* 只关自己新建的那个 tab；调试实例是别人的，不能替它关浏览器。 */
      close: async () => { await closeTab(attachPort, tab); ws.close(); },
    };
  }
  let last = null;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const opened = await launchOnce(browser);
      if (attempt > 1) opened.relaunches = attempt - 1;
      return opened;
    } catch (e) {
      if (!(e instanceof Fault) || !e.retryable) throw e;
      last = e;
      if (attempt < attempts) await sleep(2000);
    }
  }
  throw new Fault(`浏览器起不来（${attempts} 次都失败）: ${last.message}`);
}

/** 删除尽力而为，但失败必须出声：静默失败在本机留下过 48 个 user-data-dir，
 *  把「一次跑通的仪器」变成磁盘泄漏。返回是否真的删掉了。 */
const rmDir = (dir) => {
  try {
    if (fs.existsSync(dir)) fs.rmSync(dir, { recursive: true, force: true });
    return !fs.existsSync(dir);
  } catch { return false; }
};

/** 只关自己开的标签页；--attach 的是别人的调试实例，关它的 tab 属越界。 */
const closeTab = async (port, tab) => {
  if (!tab || !tab.id) return;
  try {
    await fetch(`http://127.0.0.1:${port}/json/close/${tab.id}`, { signal: AbortSignal.timeout(3000) });
  } catch { /* best effort：浏览器已退时这条本就该失败 */ }
};

/** 点击序列由档案给出；点不动的目标是判据不成立（1），不是仪器坏了（3）。
 *  log 收到的是**裸消息**：前缀由接收方（Ledger.fail）加，这里自带 FAIL 会印成「FAIL FAIL …」。 */
export async function replay(cdp, prototype, log) {
  for (const sel of prototype.clicks) {
    if (!(await cdp.has(sel))) { log(`click-target-absent ${sel}`); return false; }
    await cdp.eval(`(document.querySelector(${JSON.stringify(sel)}).click(), true)`);
    await sleep(900);
  }
  return true;
}

/* ---------------------------------------------------------------- 判据收集 */

export class Ledger {
  constructor() { this.lines = []; this.failed = 0; }
  ok(text) { this.lines.push(`OK   ${text}`); }
  fail(text) { this.lines.push(`FAIL ${text}`); this.failed += 1; }
  print() { for (const l of this.lines) console.log(l); }
}

/* ---------------------------------------------------------------- 收尾 */

/** 作为主程序运行（而不是被自检 import）时为真。 */
export function isMain(metaUrl) {
  const me = path.resolve(fileURLToPath(metaUrl));
  const argv1 = process.argv[1] ? path.resolve(process.argv[1]) : "";
  return me === argv1;
}

export function fingerprint(absPath, label) {
  const raw = fs.readFileSync(absPath);
  /* 行数按换行符计数要用**解码后的文本**：Buffer 没有 split，直接调用会在指纹这一行
     抛 TypeError——而指纹行本身属于头部说明，崩在这里等于「仪器自己坏在记账上」。 */
  const lines = raw.toString("utf8").split("\n").length - 1;
  return `${label} bytes=${raw.length} lines=${lines} sha256=${crypto.createHash("sha256").update(raw).digest("hex")}`;
}

/**
 * 统一收尾：打印命令行、档案指纹、判据行与由判据派生的退出码。
 * 退出码不是「有没有抛异常」，而是本次到底量到了什么。
 */
export async function finish(ledger, ctx) {
  console.log(`# command: ${ctx.command}`);
  for (const line of ctx.header || []) console.log(`# ${line}`);
  ledger.print();
  const judged = ledger.lines.length;
  if (!judged) {
    console.log("VERDICT: FAULT 没有任何判据被执行——空见证不算通过");
    console.log(`EXITCODE=${EXIT.FAULT}`);
    return EXIT.FAULT;
  }
  const verdict = ledger.failed ? "FAIL" : "PASS";
  console.log(`judgements=${judged} failed=${ledger.failed}`);
  console.log(`VERDICT: ${verdict}`);
  console.log(`EXITCODE=${ledger.failed ? EXIT.FAIL : EXIT.OK}`);
  return ledger.failed ? EXIT.FAIL : EXIT.OK;
}

/** 顶层包装：Fault→3、Usage→2、其它异常→3（未捕获异常属仪器故障）。 */
export async function mainTool(body) {
  let code;
  try {
    code = await body();
  } catch (e) {
    if (e instanceof Usage) {
      console.error(`USAGE ERROR: ${e.message}`);
      console.log(`EXITCODE=${EXIT.USAGE}`);
      return EXIT.USAGE;
    }
    if (e instanceof Fault) {
      console.error(`INSTRUMENT FAULT: ${e.message}`);
      console.log("FACTOR-check: 仪器故障不写成判据失败，也不写 PASS");
      console.log(`EXITCODE=${EXIT.FAULT}`);
      return EXIT.FAULT;
    }
    console.error(`INSTRUMENT FAULT (uncaught): ${e && e.stack ? e.stack : e}`);
    console.log(`EXITCODE=${EXIT.FAULT}`);
    return EXIT.FAULT;
  }
  return code;
}

export { sleep };
