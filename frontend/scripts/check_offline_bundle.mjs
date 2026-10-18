#!/usr/bin/env node
/**
 * 构建产物离线可开性检查（唯一归属：任何别处只许指向本文件，不得复抄判定）。
 * 判据来自规格 §3：构建产物可离线打开，不得经外部 CDN 加载脚本或字体。
 *
 * 退出码：0 全部成立 / 1 判据不符 / 2 用法错误 / 3 仪器故障（目录不存在等，无法归因给产物）。
 * 用法：node frontend/scripts/check_offline_bundle.mjs [--dist <dir>]
 *       node frontend/scripts/check_offline_bundle.mjs --selftest
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const FRONTEND = path.resolve(HERE, "..");

/**
 * 「标识符 URL」白名单：W3C 命名空间 URI 与 React 报错文档串是标识符/文本，不产生网络加载。
 * 判定按**解析出的主机名**而不是按前缀子串——`https://www.w3.org.evil.example/x` 含合法前缀
 * 但不是那个主机；解析不出的一律当违规（fail-closed）。这条白名单只存此处。
 */
const NON_LOAD_HOSTS = { "www.w3.org": null, "react.dev": "/errors/" };

function isNonLoad(raw) {
  let u;
  try {
    u = new URL(raw);
  } catch {
    return false;
  }
  if (u.protocol !== "http:" && u.protocol !== "https:") return false;
  const pathNeedle = NON_LOAD_HOSTS[u.hostname];
  if (pathNeedle === undefined) return false;
  return pathNeedle === null || u.pathname.startsWith(pathNeedle);
}

/** 「像加载目标」的判据只存此处：包内副本的脱敏（scripts/sync_fixtures.mjs）复用它，不复抄正则。 */
export function urlsIn(text) {
  return text.match(/https?:\/\/[A-Za-z0-9./_-]+/g) || [];
}

/** 扫一份「产物面」：返回判决所需的全部读数，不掺任何期望值。 */
function scan(root) {
  const index = path.join(root, "index.html");
  const html = fs.readFileSync(index, "utf8");
  const assetsDir = path.join(root, "assets");
  const assets = fs.existsSync(assetsDir)
    ? fs.readdirSync(assetsDir).filter((f) => /\.(js|css)$/.test(f)).sort()
    : [];
  const bad = [];
  for (const m of html.matchAll(/(?:src|href)="([^"]+)"/g)) {
    const ref = m[1];
    if (/^(https?:)?\/\//.test(ref)) bad.push(`index.html 引用外部主机: ${ref}`);
    else if (ref.startsWith("data:")) continue; // 内联字节，不存在加载去向；§3 禁的是「要联网才拿得到」
    else if (!ref.startsWith("./") && !ref.startsWith("/") && !ref.startsWith("#"))
      bad.push(`index.html 引用非相对路径: ${ref}`);
  }
  let allowed = 0;
  for (const f of assets) {
    for (const u of urlsIn(fs.readFileSync(path.join(assetsDir, f), "utf8"))) {
      if (isNonLoad(u)) allowed += 1;
      else bad.push(`${f} 含外部加载 URL: ${u}`);
    }
  }
  // 匹配集为空必须判 FAIL：没有任何资产可扫时，「零违规」是假见证。
  if (assets.length === 0) bad.push("assets 目录里没有可扫的 js/css：零集合不构成通过");
  return { assets: assets.length, scanned: assets.length, allowed, bad };
}

function report(root) {
  const index = path.join(root, "index.html");
  if (!fs.existsSync(index)) {
    console.error(`INSTRUMENT FAULT: 入口不存在 ${index}（先跑 pnpm --dir frontend build）`);
    return 3;
  }
  const r = scan(root);
  console.log(`offline scan root=${path.relative(FRONTEND, root).replace(/\\/g, "/")} `
    + `assets=${r.assets} allowed-non-load-urls=${r.allowed} violations=${r.bad.length}`);
  for (const b of r.bad.slice(0, 8)) console.log(`  VIOLATION ${b}`);
  if (r.bad.length > 0) {
    console.log("OFFLINE FAIL");
    return 1;
  }
  console.log("offline ok — 产物内没有任何需要联网加载的脚本、字体或样式");
  return 0;
}

/** 正反双向自检：合法样本必须放行、违规样本必须被点名，两侧都要实测。 */
function selftest() {
  const cases = [];
  const add = (name, want, got) => cases.push({ name, want, got, ok: want === got });
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "tl-offline-"));
  try {
    const good = path.join(tmp, "good");
    fs.mkdirSync(path.join(good, "assets"), { recursive: true });
    fs.writeFileSync(path.join(good, "index.html"),
      '<html><head><link rel="stylesheet" href="./assets/a.css"></head>'
      + '<body><script type="module" crossorigin src="./assets/a.js"></script></body></html>');
    fs.writeFileSync(path.join(good, "assets", "a.css"), "body{background:#fff}");
    fs.writeFileSync(path.join(good, "assets", "a.js"),
      'var ns="http://www.w3.org/2000/svg", x="http://www.w3.org/1998/Math/MathML",'
      + 'y="http://www.w3.org/XML/1998/namespace", e="https://react.dev/errors/130";');
    add("合法产物必须放行（含三种 W3C 命名空间写法）", 0, scan(good).bad.length);
    const lookalike = path.join(tmp, "lookalike");
    fs.mkdirSync(path.join(lookalike, "assets"), { recursive: true });
    fs.writeFileSync(path.join(lookalike, "index.html"),
      '<html><body><script src="./assets/a.js"></script></body></html>');
    fs.writeFileSync(path.join(lookalike, "assets", "a.js"),
      'var u="https://www.w3.org.evil.example/x", v="https://react.dev.evil.example/errors/1";');
    add("含合法前缀的仿冒主机必须被点名", 2, scan(lookalike).bad.length);
    const cdn = path.join(tmp, "cdn");
    fs.mkdirSync(path.join(cdn, "assets"), { recursive: true });
    fs.writeFileSync(path.join(cdn, "index.html"),
      '<html><body><script src="https://unpkg.com/react@18/umd/react.js"></script></body></html>');
    fs.writeFileSync(path.join(cdn, "assets", "a.js"), 'var f="https://cdn.jsdelivr.net/x/y.css";');
    add("CDN 脚本必须被点名", 2, scan(cdn).bad.length);
    const empty = path.join(tmp, "empty");
    fs.mkdirSync(path.join(empty, "assets"), { recursive: true });
    fs.writeFileSync(path.join(empty, "index.html"), '<html><body><div id="root"></div></body></html>');
    add("零资产不得算通过", 1, scan(empty).bad.length);
    const proto = path.join(tmp, "proto");
    fs.mkdirSync(path.join(proto, "assets"), { recursive: true });
    fs.writeFileSync(path.join(proto, "index.html"),
      '<html><head><link href="//fonts.googleapis.com/css2?family=X" rel="stylesheet">'
      + '</head><body><div id="root"></div><script src="./assets/a.js"></script></body></html>');
    fs.writeFileSync(path.join(proto, "assets", "a.js"), "var v=1;");
    add("协议相对 URL 必须被点名", 1, scan(proto).bad.length);
    const inline = path.join(tmp, "inline");
    fs.mkdirSync(path.join(inline, "assets"), { recursive: true });
    fs.writeFileSync(path.join(inline, "index.html"),
      '<html><head><link rel="icon" href="data:,"></head>'
      + '<body><div id="root"></div><script src="evil.example/x.js"></script>'
      + '<script src="./assets/a.js"></script></body></html>');
    fs.writeFileSync(path.join(inline, "assets", "a.js"), "var v=1;");
    add("内联 data: 放行，但缺协议的主机引用仍被点名", 1, scan(inline).bad.length);
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
    console.error("INSTRUMENT FAULT: 自检用例数低于裸下限 5");
    return 3;
  }
  return bad === 0 ? 0 : 1;
}

function main(argv) {
  if (argv[0] === "--selftest") return selftest();
  if (argv.includes("--help")) {
    console.log("usage: node frontend/scripts/check_offline_bundle.mjs [--dist <dir>] | --selftest");
    return 2;
  }
  const i = argv.indexOf("--dist");
  const root = i >= 0 ? (argv[i + 1] || "") : path.join(FRONTEND, "dist");
  if (i >= 0 && !argv[i + 1]) {
    console.error("usage error: --dist 需要一个目录参数");
    return 2;
  }
  if (!fs.existsSync(root)) {
    console.error(`INSTRUMENT FAULT: 产物目录不存在 ${root}`);
    return 3;
  }
  return report(root);
}

/** 作为脚本直接跑才判决；被别的守门脚本 import 走 urlsIn 时不得顺带退出进程。 */
const invoked = process.argv[1]
  && fs.realpathSync(path.resolve(process.argv[1])) === fs.realpathSync(fileURLToPath(import.meta.url));
if (invoked) process.exit(main(process.argv.slice(2)));
