#!/usr/bin/env node
/**
 * 设备时间守门：生产代码展示时间只能走 `src/utils/deviceTime.ts` 这一处。
 *
 * 用法：node frontend/scripts/check_device_time.mjs [--selftest]
 * 退出码：0 全部成立 / 1 结构与期望不符 / 2 用法错误 / 3 仪器故障。
 *
 * 注释与字符串字面量不豁免——与设计 token 守门同一口径。这里宁可误红也不放行：
 * 一段注释里出现 `Intl.` 不会有运行时后果，但一条豁免规则会，而豁免规则被打穿过的代价
 * 是「整条规则静默失效」。
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const FRONTEND = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1")), "..");
const SCAN_ROOT = "src";
const SHARED = "src/utils/deviceTime.ts"; // 相对 frontend/ 的唯一声明，正斜杠
const EXEMPT = SHARED.slice(SCAN_ROOT.length + 1); // 豁免名按扫描根计，与 rel 同一坐标系
const MIN_FILES = 1; // 裸字面量下限：扫到 0 个文件绝不能读成「干净」

const BANNED = [
  { name: "toLocale*", re: /toLocale(?:String|DateString|TimeString|NumberFormat)?\s*\(/g },
  { name: "Intl.*", re: /\bIntl\s*\./g },
  { name: "时区参数写死", re: /\btimeZone\s*:/g },
  { name: "hourCycle 写死", re: /\bhourCycle\s*:/g }
];

function listSourceFiles(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...listSourceFiles(p));
    else if (/\.(ts|tsx)$/.test(entry.name)) out.push(p);
  }
  return out.sort();
}

function scan(root) {
  if (!fs.existsSync(root)) return { files: [], findings: ["root-missing"], scanned: 0, exemptSeen: false };
  const files = listSourceFiles(root);
  const findings = [];
  let exemptSeen = false;
  for (const f of files) {
    const rel = path.relative(root, f).split(path.sep).join("/");
    if (rel === EXEMPT) {
      exemptSeen = true;
      continue;
    }
    const text = fs.readFileSync(f, "utf8");
    for (const rule of BANNED) {
      const hits = text.match(rule.re);
      if (hits) findings.push(`${rel} 含 ${rule.name} ×${hits.length}`);
    }
  }
  return { files, findings, scanned: files.length, exemptSeen };
}

function verdict(root) {
  const { findings, scanned, exemptSeen } = scan(root);
  const bad = findings.filter((f) => f !== "root-missing");
  const base = { scanned, exemptSeen, flagged: bad.length };
  const head = [`device-time scan root=${root} scanned=${scanned} exempt=${EXEMPT} exempt-in-tree=${exemptSeen} findings=${bad.length}`];
  for (const f of bad) head.push(`  ${f}`);
  const out = (rc, label) => ({ ...base, rc, lines: [...head, label] });
  if (findings.includes("root-missing")) return out(3, "DEVICE TIME FAIL 源码目录不存在");
  if (scanned < MIN_FILES) return out(3, `DEVICE TIME FAIL 可判文件 ${scanned} 低于下限 ${MIN_FILES}，零集合不等于通过`);
  if (!exemptSeen) return out(3, "DEVICE TIME FAIL 共享设备时间模块不在扫描树里，豁免规则无法归位");
  if (bad.length) return out(1, "DEVICE TIME FAIL 生产代码绕过共享设备时间模块");
  return out(0, "device time ok — 时间展示只经 " + SHARED + " 一处");
}

function run(root) {
  const v = verdict(root);
  for (const l of v.lines) console.log(l);
  return v.rc;
}

const MIN_CASES = 8; // 裸字面量下限：自检用例被删空时 "fail=0" 没有任何含义

function selftest() {
  const cases = [];
  const dir = fs.mkdtempSync(path.join(process.env.TEMP ?? process.env.TMPDIR ?? "/tmp", "tl-devtime-"));
  const root = path.join(dir, "src"); // 字面量：夹具根若也从 SCAN_ROOT 派生，改动那个常量时夹具会跟着挪，正控就自证清白了
  const LITERAL_EXEMPT = "utils/deviceTime.ts"; // 字面量，与 EXEMPT 无派生关系
  const LITERAL_SHARED = "src/utils/deviceTime.ts";
  const names = (v, n) => v.lines.filter((l) => l.trim().startsWith(n));
  const flagged = (v, file, rule) => names(v, file).some((l) => l.includes(rule));
  try {
    fs.mkdirSync(root, { recursive: true });
    fs.mkdirSync(path.join(root, "utils/lookalike"), { recursive: true });
    fs.writeFileSync(path.join(root, "BadIntl.tsx"), "const f = new Intl.DateTimeFormat(\"zh-CN\", { timeZone: \"Asia/Shanghai\" });\n");
    fs.writeFileSync(path.join(root, "BadLocal.ts"), "const s = new Date(0).toLocaleString();\n");
    fs.writeFileSync(path.join(root, "Clean.ts"), "import { formatDateTime } from \"./utils/deviceTime\";\nexport const x = 1;\n");
    fs.writeFileSync(path.join(root, LITERAL_EXEMPT), "const p = new Intl.DateTimeFormat(\"zh-CN\", { hourCycle: \"h23\" });\n");
    fs.writeFileSync(path.join(root, "utils/lookalike/deviceTime.ts"), "const q = new Intl.DateTimeFormat();\n");

    const dirty = verdict(root);
    cases.push({ name: "违规样本被点名（Intl 与时区写死）", ok: flagged(dirty, "BadIntl.tsx", "Intl.*") && flagged(dirty, "BadIntl.tsx", "时区参数写死") });
    cases.push({ name: "违规样本被点名（toLocale）", ok: flagged(dirty, "BadLocal.ts", "toLocale*") });
    cases.push({ name: "合法样本不被点名", ok: names(dirty, "Clean.ts").length === 0 });
    cases.push({ name: "共享模块自身豁免且仍计入 scanned", ok: dirty.exemptSeen && names(dirty, LITERAL_EXEMPT).length === 0 && dirty.scanned === 5 });
    cases.push({ name: "单源声明的派生关系逐字一致", ok: SCAN_ROOT === "src" && SHARED === LITERAL_SHARED && EXEMPT === LITERAL_EXEMPT });
    cases.push({ name: "豁免只认路径，同名不同路径必须点名", ok: flagged(dirty, "utils/lookalike/deviceTime.ts", "Intl.*") });
    cases.push({ name: "违规存在判 1 且标签指向绕过", ok: dirty.rc === 1 && dirty.lines.some((l) => l.includes("生产代码绕过共享设备时间模块")) });

    for (const n of ["BadIntl.tsx", "BadLocal.ts"]) fs.rmSync(path.join(root, n), { force: true });
    fs.rmSync(path.join(root, "utils/lookalike"), { recursive: true, force: true });
    const clean = verdict(root);
    cases.push({ name: "去掉违规后同一棵树判通过（反向对照）", ok: clean.rc === 0 && clean.flagged === 0 });

    fs.rmSync(path.join(root, LITERAL_EXEMPT), { force: true });
    const noExempt = verdict(root);
    cases.push({ name: "豁免缺席但仍有可判文件判 3 并点名豁免", ok: noExempt.rc === 3 && noExempt.scanned >= 1 && noExempt.lines.some((l) => l.includes("豁免规则无法归位")) });

    fs.rmSync(path.join(root, "Clean.ts"), { force: true });
    const empty = verdict(root);
    cases.push({ name: "可判文件为空判 3 并点名下限", ok: empty.rc === 3 && empty.lines.some((l) => l.includes("低于下限")) });

    const gone = verdict(path.join(dir, "no-such-dir"));
    cases.push({ name: "目录缺失判 3 并点名目录", ok: gone.rc === 3 && gone.lines.some((l) => l.includes("源码目录不存在")) });
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
  const failed = cases.filter((c) => !c.ok);
  console.log(`SELFTEST cases=${cases.length} floor=${MIN_CASES} fail=${failed.length}`);
  for (const c of cases) console.log(`  ${c.ok ? "ok  " : "FAIL"} ${c.name}`);
  if (cases.length < MIN_CASES) {
    console.log("INSTRUMENT FAULT 自检用例数低于下限，结论不可用");
    return 3;
  }
  return failed.length ? 1 : 0;
}

const arg = process.argv[2];
if (arg !== undefined && arg !== "--selftest") {
  console.error("usage: node frontend/scripts/check_device_time.mjs [--selftest]");
  process.exit(2);
}
process.exit(arg === "--selftest" ? selftest() : run(path.join(FRONTEND, SCAN_ROOT)));
