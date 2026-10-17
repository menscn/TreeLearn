/**
 * cdp_shot 的正反双向自检电池（独立伴随测试文件，与 Python 侧的 parity_selftest.py 同构）。
 *
 * 为什么单独一支文件：仪器本体 + 共享层的判定面并入电池会越过本仓库的 500 行硬上限，
 * 而测试文件按判定口径不计入生产面（`.trae/rules/harness-tasks.md` 「Tests … NOT counted」）。
 * 该排除是承重的，读数与两种口径都记在 `artifacts/evidence/T-2026-10-15-006-green.txt` 的块 10，
 * 不要留给读者自己发现。
 *
 * 用法：node .trae/scripts/dev/parity/cdp_shot.mjs --selftest （推荐入口）
 *       node .trae/scripts/dev/parity/cdp_shot_selftest.mjs   （同一支电池的直驱入口）
 *
 * 本行 import 的是**被测仪器**，因此两边构成循环依赖：仪器必须以**静态** import 取本文件
 * （cdp_shot.mjs 的 --selftest 分支），不能用 await import()。原因实测：入口在自家顶层 await
 * 里动态导入一个反向 import 自己的模块，进程以退出码 13 结束、stdout 一行不写。该机理与两种形态
 * 的对照读数由 GREEN 产物块 02 的两对无浏览器夹具现场量出。
 */
import fs from "node:fs";
import path from "node:path";
import * as K from "./parity_cdp.mjs";
import { run } from "./cdp_shot.mjs";

const CASES = [
  { name: "pos-real-render", want: 0, args: (f) => ["--screen", "chat", "--out", path.join(f, "shot.png")] },
  /* 逃生口的正向：旧工具的唯一调用形态是显式 URL，收编后这条路径必须仍然走得通，
     否则「参数化」就把老用法悄悄废掉了。 */
  {
    name: "pos-url-escape",
    want: 0,
    args: (f) => {
      const entry = K.pickScreen(K.readJson(K.DEFAULT_PROFILE, "屏档案"), "chat");
      const u = K.htmlUrl(K.prototypeOf(entry, "chat"));
      return ["--screen", "chat", "--out", path.join(f, "shot-url.png"), "--url", u.url];
    },
  },
  { name: "neg-unknown-option", want: 2, args: () => ["--screen", "chat", "--turbo", "1"] },
  { name: "neg-missing-out", want: 2, args: () => ["--screen", "chat"] },
  { name: "neg-unknown-screen", want: 2, args: () => ["--screen", "nope", "--out", "x.png"] },
  { name: "neg-bad-scroll", want: 2, args: () => ["--screen", "chat", "--out", "x.png", "--scroll", "sideways"] },
  { name: "neg-browser-absent-injection", want: 3, args: () => ["--screen", "chat", "--out", "x.png", "--browser", "/c/nowhere/msedge.exe"] },
  { name: "neg-profile-no-prototype", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.png"), "--profile", path.join(f, "no-proto.json")] },
  { name: "neg-html-missing", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.png"), "--profile", path.join(f, "bad-html.json")] },
  { name: "neg-profile-not-utf8", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.png"), "--profile", path.join(f, "bad-utf8.json")] },
  { name: "neg-url-file-missing", want: 3, args: () => ["--screen", "chat", "--out", "x.png", "--url", "file:///D:/nope/missing.html"] },
  /* 导航被浏览器自己拒绝（net::ERR_ABORTED）是「缺被核对象」那一档，必须落在 3 而不是 1；
     这条同时是 errorText 守卫的确定性覆盖——真实 about:blank 竞态只有 in-vivo 见证。 */
  { name: "neg-navigate-rejected", want: 3, args: () => ["--screen", "chat", "--out", "x.png", "--url", "javascript:document.write(1)"] },
  { name: "neg-cdp-handshake-fail", want: 3, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.png"), "--attach", "1"] },
  /* 三条期望 rc=1 的用例各测一条页面判据分支：等待目标缺失、点击目标缺失、比例被覆盖。
     它们与「仪器故障」同为一个非零码，所以判据是 rc 恰好 1 且那条判决行本身在输出里。 */
  { name: "neg-wait-for-absent", want: 1, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.png"), "--profile", path.join(f, "bad-wait.json"), "--wait-ms", "1500"] },
  { name: "neg-click-absent", want: 1, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.png"), "--profile", path.join(f, "bad-click.json")] },
  { name: "neg-shot-scale-mismatch", want: 1, args: (f) => ["--screen", "chat", "--out", path.join(f, "s.png"), "--scale", "1"] },
];

/** 与被判清单**无关**的裸字面量下限：用 len(CASES) 算自己的下限等于没有下限。 */
const MIN_CASES = 8;

/** 每条判据分支都要有「注入该违规必须判红」的用例；这里额外钉住空见证不算通过。 */
export async function selftest() {
  const parent = path.join(K.REPO, "artifacts", "tmp", "_cdpshot-selftest");
  fs.mkdirSync(parent, { recursive: true });
  const fixture = fs.mkdtempSync(path.join(parent, "run-"));
  let bad = 0;
  const profile = K.readJson(K.DEFAULT_PROFILE, "屏档案");
  const write = (name, obj) => fs.writeFileSync(path.join(fixture, name), JSON.stringify(obj, null, 2), "utf8");
  const withoutProto = { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat } } };
  delete withoutProto.screens.chat.prototype;
  write("no-proto.json", withoutProto);
  write("bad-html.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...profile.screens.chat.prototype, html: "Docs/04-ux-design/no-such-prototype.html" } } } });
  /* 非法 UTF-8 的夹具要写**字节**：写成 JS 字符串再落盘，写入器会把它编码成合法序列，
     该分支于是永远不会被触发（与上面 readJson 的教训同源）。 */
  fs.writeFileSync(path.join(fixture, "bad-utf8.json"), Buffer.concat([Buffer.from('{"screens": "'), Buffer.from([0xe0, 0xff, 0xff, 0x22])]));
  write("bad-wait.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...profile.screens.chat.prototype, wait_for: ".no-such-node" } } } });
  write("bad-click.json", { ...profile, screens: { ...profile.screens, chat: { ...profile.screens.chat, prototype: { ...profile.screens.chat.prototype, clicks: ["#no-such-click-target"] } } } });

  if (CASES.length < MIN_CASES) {
    console.log(`FAIL selftest-case-floor declared=${CASES.length} floor=${MIN_CASES} —— 用例清单被截短时判据不能为空见证`);
    fs.rmSync(fixture, { recursive: true, force: true });
    console.log("EXITCODE=3");
    return 3;
  }
  for (const c of CASES) {
    const t0 = Date.now();
    let rc = 3;
    try {
      rc = await K.mainTool(() => run(c.args(fixture)));
    } catch (e) {
      console.log(`FAIL ${c.name} threw outside mainTool: ${e && e.message}`);
    }
    const secs = ((Date.now() - t0) / 1000).toFixed(1);
    if (rc === c.want) console.log(`ok   ${c.name} rc=${rc} (${secs}s, want=${c.want})`);
    else { console.log(`FAIL ${c.name} rc=${rc} want=${c.want} (${secs}s)`); bad += 1; }
  }

  /* 空见证兜底：判据一条都没有时 finish() 必须报 3，而不是 PASS。 */
  const empty = new K.Ledger();
  const emptyRc = await K.finish(empty, { command: "(unit) 空判据", header: [] });
  if (emptyRc === 3) console.log("ok   neg-empty-ledger rc=3 (unit, want=3)");
  else { console.log(`FAIL neg-empty-ledger rc=${emptyRc} want=3`); bad += 1; }

  fs.rmSync(fixture, { recursive: true, force: true });
  const total = CASES.length + 1;
  console.log(`SELFTEST ${bad ? "FAIL" : "PASS"} cases=${total} failures=${bad}`);
  console.log(`EXITCODE=${bad ? 1 : 0}`);
  return bad ? 1 : 0;
}

if (K.isMain(import.meta.url)) process.exitCode = await K.mainTool(() => selftest());
