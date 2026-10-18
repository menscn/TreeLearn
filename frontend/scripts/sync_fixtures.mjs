#!/usr/bin/env node
/**
 * 阶段一夹具 → 前端可打包副本 的同步与差分守门（T-2026-10-18-020）。
 *
 * 为什么要有这份副本：`artifacts/fixtures/parity/*.json` 是判据的权威正文，它必须留在仓库的
 * 证据面；而浏览器里跑的 H5 要离线读到同一份字节，只能把它打进包（`file://` 双击打开时
 * `fetch` 相对路径拿不到仓库外的文件）。除下面写明的那一条例外，这里刻意**不做转换、不做裁剪**：
 * 逐字节复制，
 * 于是「前端读的那份」与「采集仪器写的那份」是同一串字节，夹具的 sha 就是两边的共同身份。
 * 出现漂移时本脚本判 FAIL 并点名是哪个文件，而不是让 Web 侧悄悄读一份旧的。
 *
 * 唯一的例外：包内副本要把「长得像加载目标的 URL」换成脱敏标记。真机采集到的文本里会出现
 * 用户自己配置的接口地址（提供商管理页把它当作一行文字显示），`?raw` 会把它原样带进产物，
 * 于是 §3 的离线判据（扫描产物里的外部加载 URL）会点名它——判据无法区分「一行数据」和
 * 「一处加载」，而按主机放行会让阶段七真的 `fetch` 也一并溜过去。所以严格侧不动：判据不加
 * 白名单，权威夹具不改字节，只在写入包内副本时替换这类地址，并把替换次数打印成事实。
 *
 * 用法：node frontend/scripts/sync_fixtures.mjs            # 把权威侧刷进包内副本
 *       node frontend/scripts/sync_fixtures.mjs --check    # 只判定，不写盘（lint/build 走这条）
 *       node frontend/scripts/sync_fixtures.mjs --selftest
 * 退出码：0 一致 / 1 有漂移、缺失或孤本 / 2 用法错误 / 3 仪器故障（目录都不在）。
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { urlsIn } from "./check_offline_bundle.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, "..", "..");
const SRC_DIR = path.join(REPO, "artifacts", "fixtures", "parity");
const DST_DIR = path.join(HERE, "..", "src", "platform", "fixtures");
const SUFFIX = "-android.json";
const MIN_FIXTURES = 1;
const REDACTION = "〔地址已在包内脱敏〕";

/** 包内副本唯一的改动：把「判据会当成加载目标」的串换成标记；权威侧字节不动。 */
function toBundle(text) {
  let out = text;
  for (const url of [...new Set(urlsIn(text))]) out = out.split(url).join(REDACTION);
  return { text: out, count: urlsIn(text).length };
}

function fault(message) {
  const err = new Error(message);
  err.exitCode = 3;
  return err;
}

function listFixtures(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((name) => name.endsWith(SUFFIX)).sort();
}

/** 差分只说三种事实：权威侧有而包内没有（missing）、字节不同（stale）、包内有而权威侧删了（orphan）。 */
function diff(srcDir, dstDir) {
  const src = listFixtures(srcDir);
  const dst = listFixtures(dstDir);
  const missing = [];
  const stale = [];
  for (const name of src) {
    const dstAbs = path.join(dstDir, name);
    if (!fs.existsSync(dstAbs)) {
      missing.push(name);
      continue;
    }
    const want = Buffer.from(toBundle(fs.readFileSync(path.join(srcDir, name), "utf8")).text, "utf8");
    if (!want.equals(fs.readFileSync(dstAbs))) stale.push(name);
  }
  const orphan = dst.filter((name) => !src.includes(name));
  return { src, dst, missing, stale, orphan };
}

export function main(argv) {
  const mode = argv[0] ?? "";
  if (mode === "--selftest") return selftest();
  if (mode !== "" && mode !== "--check") {
    console.log(`USAGE 只认 --check / --selftest，收到 ${mode}`);
    return 2;
  }
  if (!fs.existsSync(SRC_DIR)) throw fault(`权威夹具目录不存在：${SRC_DIR}`);
  const src = listFixtures(SRC_DIR);
  if (src.length < MIN_FIXTURES) {
    console.log(`FLOOR fixtures>=${MIN_FIXTURES} got=${src.length} dir=${path.relative(REPO, SRC_DIR)}`);
    console.log("EXITCODE=1");
    return 1;
  }
  const before = diff(SRC_DIR, DST_DIR);
  for (const name of before.missing) console.log(`MISS ${name} 包内副本不存在`);
  for (const name of before.stale) console.log(`DRIFT ${name} 包内副本与权威侧（脱敏后）字节不同`);
  let bad = before.missing.length + before.stale.length;
  let redacted = 0;
  if (mode === "--check") {
    for (const name of before.orphan) {
      console.log(`ORPHAN ${name} 在包内但没有权威侧`);
      bad += 1;
    }
  } else {
    fs.mkdirSync(DST_DIR, { recursive: true });
    for (const name of src) {
      const text = fs.readFileSync(path.join(SRC_DIR, name), "utf8");
      const bundle = toBundle(text);
      redacted += bundle.count;
      fs.writeFileSync(path.join(DST_DIR, name), bundle.text, "utf8");
      if (before.missing.includes(name) || before.stale.includes(name)) {
        console.log(`COPIED ${name} bytes=${Buffer.byteLength(bundle.text, "utf8")} redacted_urls=${bundle.count}`);
      }
    }
    // 写完后的判决只看写完后的状态：刷新成功不是失败。
    const after = diff(SRC_DIR, DST_DIR);
    bad = after.missing.length + after.stale.length + after.orphan.length;
    for (const name of after.orphan) {
      console.log(`ORPHAN ${name} 在包内但没有权威侧`);
      bad += 1;
    }
  }
  console.log(`SYNC ${mode === "--check" ? "CHECK" : "WRITE"} src=${src.length} `
    + `dst=${listFixtures(DST_DIR).length} bad=${bad} redacted=${redacted} `
    + `dir=${path.relative(REPO, DST_DIR)}`);
  console.log(bad === 0 ? "SYNC CHECK ok" : "SYNC CHECK FAIL");
  const code = bad === 0 ? 0 : 1;
  console.log(`EXITCODE=${code}`);
  return code;
}

function selftest() {
  const root = fs.mkdtempSync(path.join(process.env.TEMP ?? process.env.TMP ?? ".", "tl-fixture-sync-"));
  const src = path.join(root, "src");
  const dst = path.join(root, "dst");
  fs.mkdirSync(src, { recursive: true });
  fs.mkdirSync(dst, { recursive: true });
  fs.writeFileSync(path.join(src, `home${SUFFIX}`), '{"screen":"home"}');
  fs.writeFileSync(path.join(dst, `home${SUFFIX}`), '{"screen":"home"}');
  fs.writeFileSync(path.join(dst, `gone${SUFFIX}`), "{}");
  const cases = [];
  const identical = diff(src, dst);
  cases.push(["== 1. 同字节判一致", identical.missing.length === 0 && identical.stale.length === 0,
    JSON.stringify({ missing: identical.missing, stale: identical.stale })]);
  fs.writeFileSync(path.join(dst, `home${SUFFIX}`), '{"screen":"home","x":1}');
  const drifted = diff(src, dst);
  cases.push(["== 2. 改一个字节就判漂移", drifted.stale.length === 1, JSON.stringify(drifted.stale)]);
  fs.rmSync(path.join(dst, `home${SUFFIX}`));
  const lost = diff(src, dst);
  cases.push(["== 3. 包内缺失点名", lost.missing.length === 1, JSON.stringify(lost.missing)]);
  fs.writeFileSync(path.join(src, `only${SUFFIX}`), "{}");
  const empty = diff(src, path.join(root, "empty-dir"));
  cases.push(["== 4. 目标目录不存在时全部算缺失", empty.missing.length === 2, JSON.stringify(empty.missing)]);
  const orphan = diff(src, dst).orphan;
  cases.push(["== 5. 孤本被点名", orphan.length === 1 && orphan[0] === `gone${SUFFIX}`,
    JSON.stringify(orphan)]);
  const withUrl = toBundle('{"nodes":[{"text":"https://api.deepseek.com"}]}');
  cases.push(["== 6. 包内副本不留可加载 URL", urlsIn(withUrl.text).length === 0
    && withUrl.count === 1 && JSON.parse(withUrl.text).nodes.length === 1,
    `count=${withUrl.count} left=${JSON.stringify(urlsIn(withUrl.text))}`]);
  const plain = toBundle('{"nodes":[{"text":"设置"}]}');
  cases.push(["== 7. 不含 URL 时逐字节不动", plain.count === 0 && plain.text === '{"nodes":[{"text":"设置"}]}',
    JSON.stringify(plain.text)]);
  fs.writeFileSync(path.join(src, `url${SUFFIX}`), '{"t":"https://api.deepseek.com"}');
  fs.writeFileSync(path.join(dst, `url${SUFFIX}`), '{"t":"https://api.deepseek.com"}');
  const rawCopy = diff(src, dst);
  cases.push(["== 8. 原样照搬（未脱敏）的包内副本判漂移", rawCopy.stale.includes(`url${SUFFIX}`),
    JSON.stringify(rawCopy.stale)]);
  fs.rmSync(root, { recursive: true, force: true });
  let bad = 0;
  for (const [name, ok, detail] of cases) {
    console.log(`${ok ? "PASS" : "FAIL"} ${name} — ${detail}`);
    if (!ok) bad += 1;
  }
  console.log(`SELFTEST cases=${cases.length} bad=${bad} floor=${MIN_FIXTURES}`);
  const code = bad === 0 ? 0 : 1;
  console.log(`EXITCODE=${code}`);
  return code;
}

try {
  process.exit(main(process.argv.slice(2)));
} catch (exc) {
  console.log(`FAULT ${exc instanceof Error ? exc.message : String(exc)}`);
  console.log(`EXITCODE=${exc && exc.exitCode ? exc.exitCode : 3}`);
  process.exit(exc && exc.exitCode ? exc.exitCode : 3);
}
