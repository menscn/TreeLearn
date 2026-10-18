#!/usr/bin/env node
/**
 * 把 `dist/` 收成一个可双击打开的单文件产物 `dist-offline/index.html`（唯一归属：单文件离线产物只由此脚本产出）。
 *
 * 为什么需要它：Chromium 系浏览器取**外部** ES 模块时按 CORS 模式发起，`file://` 的 origin 是 `null`，
 * 于是 `dist/index.html` 双击打开只会得到空白页（实测：`Access to script at 'file:///…/assets/index-*.js'
 * from origin 'null' has been blocked by CORS policy`）。内联的 `<script type="module">` 不发请求，
 * 在 `file://` 下照常执行（同一条实测里两半都验过：内联模块出字、外部经典脚本出字、外部模块被拦）。
 *
 * 它不替代 `check_offline_bundle.mjs`：那支判「产物不需要联网」（规格 §3），本脚本判「产物不需要目录」。
 * 退出码：0 成立 / 1 判据不符 / 2 用法错误 / 3 仪器故障（dist 不存在等）。
 * 用法：node frontend/scripts/inline_offline.mjs [--dist <dir>] [--out <dir>]
 *       node frontend/scripts/inline_offline.mjs --selftest
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const FRONTEND = path.resolve(HERE, "..");
const SCRIPT = path.relative(FRONTEND, fileURLToPath(import.meta.url)).replace(/\\/g, "/");

const SCRIPT_TAG = /<script\b[^>]*\bsrc="([^"]+)"[^>]*>\s*<\/script>/g;
const STYLE_TAG = /<link\b[^>]*\brel="stylesheet"[^>]*\bhref="([^"]+)"[^>]*>/g;

/** 内联后仍会「去别处取东西」的写法：模块级 import 与动态 import()。有就说明这个包不能收成单文件。 */
const STILL_LOADS = /(^|[;\n{])\s*import\s+[\w{*]|\bimport\s*\(/m;

function relPath(ref) {
  if (ref.startsWith("./") || ref.startsWith("/")) return ref.replace(/^[./]+/, "");
  return null;
}

/** 收一次：返回判决需要的全部读数，不掺期望值。 */
function inline(dist, out) {
  const index = path.join(dist, "index.html");
  const html = fs.readFileSync(index, "utf8");
  const bad = [];
  let jsBytes = 0;
  let cssBytes = 0;
  let scripts = 0;
  let styles = 0;
  const body = html
    .replace(SCRIPT_TAG, (tag, ref) => {
      const rel = relPath(ref);
      if (rel === null) { bad.push(`脚本引用不是包内相对路径，无法内联: ${ref}`); return tag; }
      const file = path.join(dist, rel);
      if (!fs.existsSync(file)) { bad.push(`脚本引用指向不存在的文件: ${rel}`); return tag; }
      const code = fs.readFileSync(file, "utf8");
      if (STILL_LOADS.test(code)) bad.push(`${rel} 里还有 import / import()：内联后仍会去取外部模块，单文件不成立`);
      jsBytes += code.length;
      scripts += 1;
      return tag.includes('type="module"')
        ? `<script type="module">\n${code}\n</script>`
        : `<script>\n${code}\n</script>`;
    })
    .replace(STYLE_TAG, (tag, ref) => {
      const rel = relPath(ref);
      if (rel === null) { bad.push(`样式引用不是包内相对路径，无法内联: ${ref}`); return tag; }
      const file = path.join(dist, rel);
      if (!fs.existsSync(file)) { bad.push(`样式引用指向不存在的文件: ${rel}`); return tag; }
      const css = fs.readFileSync(file, "utf8");
      cssBytes += css.length;
      styles += 1;
      return `<style>\n${css}\n</style>`;
    });
  /* 收完之后标签上必须什么都不剩。掏空只按**自己写出的那两种包裹**做（`<script type="module">\n…\n</script>`
     与 `<style>\n…\n</style>`），不按通用 `</script` 规则——那样会把没能内联、原样留在出口里的外部引用一起掏掉，
     而那种引用正是必须被点名的（实测：掏早了会把 unpkg 那条漏判）。库代码里的 `<img src="…">` 字符串
     则会被掏掉，不再冒充引用（实测假阳性 `+pn(e)+`）。URL 面由 check_offline_bundle.mjs 判，这里不重复。 */
  const shell = body
    .replace(/<script(?: type="module")?>\n[\s\S]*?\n<\/script>/g, "<script></script>")
    .replace(/<style>\n[\s\S]*?\n<\/style>/g, "<style></style>");
  for (const tag of shell.matchAll(/<(?:script|link|img|iframe|source|audio|video|use)\b[^>]*>/g)) {
    for (const m of tag[0].matchAll(/(?:src|href)="([^"]+)"/g)) {
      const ref = m[1];
      if (ref.startsWith("data:") || ref.startsWith("#")) continue;
      bad.push(`单文件里仍留着外部引用: ${ref}`);
    }
  }
  if (scripts === 0) bad.push("没内联任何脚本：零集合不构成通过（dist/index.html 结构变了？）");
  fs.mkdirSync(out, { recursive: true });
  const target = path.join(out, "index.html");
  fs.writeFileSync(target, body);
  return { target, scripts, styles, jsBytes, cssBytes, bytes: body.length, bad };
}

function report(dist, out) {
  const r = inline(dist, out);
  console.log(`inline dist=${path.relative(FRONTEND, dist).replace(/\\/g, "/")} `
    + `scripts=${r.scripts} styles=${r.styles} js=${r.jsBytes}B css=${r.cssBytes}B `
    + `single=${path.relative(FRONTEND, r.target).replace(/\\/g, "/")} bytes=${r.bytes} violations=${r.bad.length}`);
  for (const b of r.bad.slice(0, 8)) console.log(`  VIOLATION ${b}`);
  if (r.bad.length > 0) {
    console.log("INLINE FAIL");
    return 1;
  }
  console.log("inline ok — 单文件产物不需要目录、不需要服务，双击即可执行（模块已内联，file:// 无 CORS 取回）");
  return 0;
}

function selftest() {
  const cases = [];
  const add = (name, want, got) => cases.push({ name, want, got, ok: want === got });
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "tl-inline-"));
  try {
    const good = path.join(tmp, "good");
    fs.mkdirSync(path.join(good, "assets"), { recursive: true });
    fs.writeFileSync(path.join(good, "index.html"),
      '<html><head><link rel="stylesheet" crossorigin href="./assets/a.css"></head>'
      + '<body><div id="root"></div><script type="module" crossorigin src="./assets/a.js"></script></body></html>');
    fs.writeFileSync(path.join(good, "assets", "a.css"), "body{color:#111}");
    /* 库代码里出现的 `src="` 不是引用：这条样本把「扫全文会误伤」钉成断言（实测假阳性 `+pn(e)+`）。 */
    fs.writeFileSync(path.join(good, "assets", "a.js"),
      "var x=1, s='<img src=\"inline-text-not-a-ref.png\">';console.log(x, s);");
    const out = path.join(tmp, "out-good");
    const g = inline(good, out);
    add("合法产物收成单文件", 0, g.bad.length);
    const single = fs.readFileSync(g.target, "utf8");
    add("单文件里没有外部引用", 0, /(?:src|href)="\.\/|https?:\/\//.test(single) ? 1 : 0);
    add("单文件里脚本已内联", 1, /<script type="module">\s*var x=1/.test(single) ? 1 : 0);
    add("单文件里样式已内联", 1, /<style>\s*body\{color:#111\}/.test(single) ? 1 : 0);

    const dyn = path.join(tmp, "dyn");
    fs.mkdirSync(path.join(dyn, "assets"), { recursive: true });
    fs.writeFileSync(path.join(dyn, "index.html"),
      '<html><body><script type="module" src="./assets/a.js"></script></body></html>');
    fs.writeFileSync(path.join(dyn, "assets", "a.js"), "import('./lazy.js').then(function(){});");
    add("内联后仍会取外部模块的包必须被点名", 1, inline(dyn, path.join(tmp, "out-dyn")).bad.length);

    const remote = path.join(tmp, "remote");
    fs.mkdirSync(path.join(remote, "assets"), { recursive: true });
    fs.writeFileSync(path.join(remote, "index.html"),
      '<html><body><script src="https://unpkg.com/react@18/umd/react.js"></script>'
      + '<script src="./assets/a.js"></script></body></html>');
    fs.writeFileSync(path.join(remote, "assets", "a.js"), "var v=1;");
    add("引用外部主机的入口必须被点名", 2, inline(remote, path.join(tmp, "out-remote")).bad.length);

    const empty = path.join(tmp, "empty");
    fs.mkdirSync(path.join(empty, "assets"), { recursive: true });
    fs.writeFileSync(path.join(empty, "index.html"), '<html><body><div id="root"></div></body></html>');
    fs.writeFileSync(path.join(empty, "assets", "a.js"), "var v=1;");
    add("零脚本不得算通过", 1, inline(empty, path.join(tmp, "out-empty")).bad.length);
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
  let bad = 0;
  for (const c of cases) {
    console.log(`CASE ${c.ok ? "ok" : "FAIL"} ${c.name} want=${c.want} got=${c.got}`);
    if (!c.ok) bad += 1;
  }
  console.log(`SELFTEST cases=${cases.length} ok=${cases.length - bad} fail=${bad}`);
  if (cases.length < 5) {
    console.error(`INSTRUMENT FAULT: 自检用例数低于裸下限 5（见 ${SCRIPT}）`);
    return 3;
  }
  return bad === 0 ? 0 : 1;
}

function main(argv) {
  if (argv[0] === "--selftest") return selftest();
  if (argv.includes("--help")) {
    console.log(`usage: node ${SCRIPT} [--dist <dir>] [--out <dir>] | --selftest`);
    return 2;
  }
  const at = (flag, dflt) => {
    const i = argv.indexOf(flag);
    if (i >= 0 && !argv[i + 1]) {
      console.error(`usage error: ${flag} 需要一个目录参数`);
      return null;
    }
    return i >= 0 ? argv[i + 1] : dflt;
  };
  const dist = at("--dist", path.join(FRONTEND, "dist"));
  const out = at("--out", path.join(FRONTEND, "dist-offline"));
  if (dist === null || out === null) return 2;
  if (!fs.existsSync(path.join(dist, "index.html"))) {
    console.error(`INSTRUMENT FAULT: 入口不存在 ${path.join(dist, "index.html")}（先跑 pnpm --dir frontend build）`);
    return 3;
  }
  return report(dist, out);
}

const invoked = process.argv[1]
  && fs.realpathSync(path.resolve(process.argv[1])) === fs.realpathSync(fileURLToPath(import.meta.url));
if (invoked) process.exit(main(process.argv.slice(2)));
