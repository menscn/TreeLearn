#!/usr/bin/env node
/**
 * 文案守门（唯一判定口径，`.codex/rules/frontend.md` 的 user-copy guard 指向本文件）。
 *
 * 判据：`src/**` 生产代码里，注释之外出现的任何中日韩文字都必须来自文案表读取
 * （`t("key")` / `copyText("key")`），否则违规。判定不区分它在字符串字面量还是 JSX 文本里——
 * 两者都能到达 UI；豁免只允许同行的 `i18n-ignore: <理由>`，理由为空不成立。
 * 文案表本体在 `src/copy/`，按目录整体豁免（那里就是唯一正文）。
 *
 * 基线 `scripts/user-copy-baseline.json` 冻结守门建立之前的遗留债务，只减不增：
 * 某文件实测数 > 基线数即「新增违规」判 1；基线数 > 实测即「债务已迁走但条目没删」判 1。
 * 用法：node frontend/scripts/check_user_copy_i18n.mjs [--selftest] [--json]
 * 退出码：0 成立 / 1 新增违规或基线失配 / 2 用法错误 / 3 仪器故障（基线缺失、可判文件低于下限）。
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const HERE = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
const FRONTEND = path.resolve(HERE, "..");
const SCAN_ROOT = path.join(FRONTEND, "src");
const BASELINE_PATH = path.join(FRONTEND, "scripts", "user-copy-baseline.json");
const COPY_DIR = "copy/"; // 目录级豁免，按扫描根计
const CJK = /[぀-ヿ㐀-䶿一-鿿豈-﫿]/;
const MIN_FILES = 1;
const MIN_CASES = 10;

function listSourceFiles(dir) {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...listSourceFiles(p));
    else if (/\.tsx?$/.test(e.name)) out.push(p);
  }
  return out.sort();
}

/**
 * 去掉注释但保留字符串与 JSX 文本：逐字符状态机，不做词法完备解析。
 * 字符串里的 `//` 不会被误认成注释，注释里的引号也不会开启字符串——这两条正是自检的靶子。
 */
function stripComments(text) {
  let out = "";
  let i = 0;
  const n = text.length;
  let mode = "code"; // code | line | block | sq | dq | tpl
  while (i < n) {
    const c = text[i];
    const d = text[i + 1];
    if (mode === "code") {
      if (c === "/" && d === "/") { mode = "line"; i += 2; out += "  "; continue; }
      if (c === "/" && d === "*") { mode = "block"; i += 2; out += "  "; continue; }
      if (c === "'") mode = "sq";
      else if (c === '"') mode = "dq";
      else if (c === "`") mode = "tpl";
      out += c;
      i += 1;
      continue;
    }
    if (mode === "line") {
      if (c === "\n") { mode = "code"; out += c; } else out += " ";
      i += 1;
      continue;
    }
    if (mode === "block") {
      if (c === "*" && d === "/") { mode = "code"; i += 2; out += "  "; continue; }
      out += c === "\n" ? c : " ";
      i += 1;
      continue;
    }
    const quote = mode === "sq" ? "'" : mode === "dq" ? '"' : "`";
    if (c === "\\") { out += c + (d ?? ""); i += 2; continue; }
    if (c === quote) { mode = "code"; out += c; i += 1; continue; }
    out += c;
    i += 1;
  }
  return out;
}

/** 注释之外含 CJK 的行，未被同行 `i18n-ignore: 理由` 覆盖。 */
function findVisibleCjk(root) {
  if (!fs.existsSync(root)) return { error: "root-missing", hits: [], scanned: 0, exemptSeen: false };
  const files = listSourceFiles(root);
  const hits = [];
  let exemptSeen = false;
  for (const f of files) {
    const rel = path.relative(root, f).split(path.sep).join("/");
    if (rel.startsWith(COPY_DIR)) { exemptSeen = true; continue; }
    const raw = fs.readFileSync(f, "utf8").split("\n");
    const code = stripComments(raw.join("\n")).split("\n"); // 逐字符对齐：注释被抹成空格，行号与列位不变
    code.forEach((line, idx) => {
      if (!CJK.test(line)) return;
      // 豁免标记住在注释里，所以只在原文上找它；CJK 判定住在去注释后的代码上。
      const m = (raw[idx] ?? "").match(/i18n-ignore:\s*(\S.*)$/);
      if (m && m[1].trim().length > 0) return;
      hits.push({ rel, line: idx + 1, text: line.trim().slice(0, 70) });
    });
  }
  return { hits, scanned: files.length, exemptSeen };
}

function readBaseline(baselinePath) {
  if (!fs.existsSync(baselinePath)) return { error: "baseline-missing", entries: [] };
  let raw;
  try {
    raw = JSON.parse(fs.readFileSync(baselinePath, "utf8"));
  } catch (e) {
    return { error: "baseline-unparsable", entries: [], detail: String(e) };
  }
  if (!Array.isArray(raw.entries)) return { error: "baseline-no-entries", entries: [] };
  return { entries: raw.entries };
}

function verdict(root, baselinePath) {
  const r = findVisibleCjk(root);
  const b = readBaseline(baselinePath);
  const measured = new Map();
  for (const h of r.hits) measured.set(h.rel, (measured.get(h.rel) ?? 0) + 1);
  const base = new Map(b.entries.map((e) => [e.file, e.count]));
  const grown = [...measured.entries()].filter(([f, n]) => n > (base.get(f) ?? 0));
  const stale = [...base.entries()].filter(([f, n]) => (measured.get(f) ?? 0) < n);
  const covered = [...measured.entries()].reduce((s, [f, n]) => s + Math.min(n, base.get(f) ?? 0), 0);
  const head = [`user-copy scan root=src scanned=${r.scanned} copy-dir-exempt-seen=${r.exemptSeen} `
    + `cjk-lines=${r.hits.length} files=${measured.size} baseline-entries=${base.size} baseline-covered=${covered}`];
  for (const h of r.hits.slice(0, 10)) head.push(`  CJK ${h.rel}:${h.line} ${h.text}`);
  for (const [f, n] of grown) head.push(`  GROWN ${f} 实测 ${n} > 基线 ${base.get(f) ?? 0}：新增用户可见文案必须进 src/copy 按 key 取`);
  for (const [f, n] of stale) head.push(`  STALE ${f} 基线 ${n} 但实测 ${measured.get(f) ?? 0}：债务已迁走，同一改动里删掉这条基线`);
  const out = (rc, label) => ({ scanned: r.scanned, flagged: r.hits.length, rc, lines: [...head, label] });
  if (r.error === "root-missing") return out(3, "USER COPY FAIL 源码目录不存在");
  if (b.error) return out(3, `USER COPY FAIL 基线不可用（${b.error}），没有基线就没有「只减不增」的判据`);
  if (r.scanned < MIN_FILES) return out(3, `USER COPY FAIL 可判文件 ${r.scanned} 低于下限 ${MIN_FILES}，零集合不等于通过`);
  if (!r.exemptSeen) return out(3, "USER COPY FAIL 文案表目录不在扫描树里，目录级豁免无法归位");
  if (grown.length) return out(1, "USER COPY FAIL 生产代码新增了未经文案表的可见文案");
  if (stale.length) return out(1, "USER COPY FAIL 基线与实测失配");
  if (covered > 0) head.push(`  NOTE 基线内遗留 ${covered} 行待迁移`);
  return out(0, "user copy ok — 生产代码的可见文案全部经 src/copy 按 key 读取（或已被冻结基线覆盖）");
}

function writeBaseline(dir, entries) {
  const p = path.join(dir, "user-copy-baseline.json");
  fs.writeFileSync(p, JSON.stringify({ entries }, null, 2) + "\n");
  return p;
}

function selftest() {
  const cases = [];
  const push = (name, ok) => cases.push({ name, ok });
  const dir = fs.mkdtempSync(path.join(process.env.TEMP ?? process.env.TMPDIR ?? "/tmp", "tl-usercopy-"));
  const LITERAL_SRC = "src"; // 字面量：夹具根不随 SCAN_ROOT 挪动，否则正控自证清白
  const named = (v, f) => v.lines.some((l) => l.includes(`CJK ${f}`));
  try {
    const src = path.join(dir, LITERAL_SRC);
    fs.mkdirSync(path.join(src, "copy"), { recursive: true });
    fs.mkdirSync(path.join(src, "pages"), { recursive: true });
    fs.writeFileSync(path.join(src, "copy", "zh-CN.ts"), 'export const zhCN = { app_name: "知芽" };\n');
    fs.writeFileSync(path.join(src, "pages", "Bad.tsx"), 'export const Bad = () => <p>请输入问题</p>;\nconst s = "保存成功";\n');
    fs.writeFileSync(path.join(src, "pages", "Comment.tsx"),
      '// 这行注释里有中文，不该被点名\n/* 块注释\n   里的中文也不该被点名 */\nexport const c = 1;\n');
    fs.writeFileSync(path.join(src, "pages", "Url.tsx"), 'const u = "https://example.com/路径/资源";\nexport const v = u;\n');
    fs.writeFileSync(path.join(src, "pages", "Ignored.tsx"), 'const label = "临时占位"; // i18n-ignore: 协议常量，不到达 UI\nexport const w = label;\n');
    fs.writeFileSync(path.join(src, "pages", "EmptyIgnore.tsx"), 'const x = "必须有理由"; // i18n-ignore:\nexport const y = x;\n');
    fs.writeFileSync(path.join(src, "pages", "Good.tsx"), 'import { useCopy } from "../hooks/useCopy";\nexport default useCopy;\n');
    fs.writeFileSync(path.join(src, "pages", "MarkerElsewhere.tsx"), 'const m = "中文在代码里";\n// i18n-ignore: 理由写在别的行，不成立\nexport const t = m;\n');
    fs.writeFileSync(path.join(src, "pages", "QuoteInComment.tsx"),
      'const a = 1; /* 注释里有单引号 \' 和双引号 " */\nconst b = "又一段中文";\nexport const z = [a, b];\n');

    // 只把两处「应视为遗留债务」的文件写进基线，其余违规必须被判新增。
    const bl = writeBaseline(dir, [{ file: "pages/Url.tsx", count: 1 }, { file: "pages/QuoteInComment.tsx", count: 1 }]);
    const v = verdict(src, bl);
    push("JSX 文本与字符串字面量同判违规", named(v, "pages/Bad.tsx") && v.lines.some((l) => l.includes("GROWN pages/Bad.tsx")));
    push("注释里的中文不点名", !named(v, "pages/Comment.tsx"));
    push("URL 路径里的中文仍算可见文本候选（并由基线覆盖）", named(v, "pages/Url.tsx") && !v.lines.some((l) => l.includes("GROWN pages/Url.tsx")));
    push("带理由的 i18n-ignore 放行", !named(v, "pages/Ignored.tsx"));
    push("空理由的 i18n-ignore 仍点名", named(v, "pages/EmptyIgnore.tsx"));
    push("经文案表取值的合法样本不点名", !named(v, "pages/Good.tsx"));
    push("注释里的引号不会开启字符串（后续中文仍点名）", named(v, "pages/QuoteInComment.tsx") && !v.lines.some((l) => l.includes("GROWN pages/QuoteInComment.tsx")));
    push("文案表目录整体豁免且仍计入 scanned", v.lines.some((l) => l.includes("copy-dir-exempt-seen=true"))
      && !named(v, "copy/zh-CN.ts") && v.scanned === 9);
    push("豁免只在同行生效（理由写在别行仍点名）", named(v, "pages/MarkerElsewhere.tsx"));
    push("存在未经基线的违规判 1", v.rc === 1);

    fs.rmSync(path.join(src, "pages", "Bad.tsx"), { force: true });
    fs.rmSync(path.join(src, "pages", "EmptyIgnore.tsx"), { force: true });
    fs.rmSync(path.join(src, "pages", "MarkerElsewhere.tsx"), { force: true });
    const covered = verdict(src, bl);
    push("违规全部落在基线内判 0 并打 NOTE", covered.rc === 0 && covered.lines.some((l) => l.includes("基线内遗留")));

    writeBaseline(dir, [{ file: "pages/Url.tsx", count: 3 }]);
    const staleV = verdict(src, path.join(dir, "user-copy-baseline.json"));
    push("基线数大于实测判 1 并点名 STALE", staleV.rc === 1 && staleV.lines.some((l) => l.includes("STALE pages/Url.tsx")));

    fs.rmSync(path.join(dir, "user-copy-baseline.json"), { force: true });
    push("基线缺失判 3 并点名基线", (() => {
      const v3 = verdict(src, path.join(dir, "user-copy-baseline.json"));
      return v3.rc === 3 && v3.lines.some((l) => l.includes("基线不可用"));
    })());

    fs.mkdirSync(path.join(dir, "nocopy", LITERAL_SRC, "pages"), { recursive: true });
    fs.writeFileSync(path.join(dir, "nocopy", LITERAL_SRC, "pages", "A.tsx"), "export const a = 1;\n");
    const noCopy = verdict(path.join(dir, "nocopy", LITERAL_SRC), writeBaseline(path.join(dir, "nocopy"), []));
    push("文案表缺席判 3 并点名豁免", noCopy.rc === 3 && noCopy.lines.some((l) => l.includes("豁免无法归位")));

    const gone = verdict(path.join(dir, "no-such-src"), bl);
    push("源码目录缺失判 3", gone.rc === 3 && gone.lines.some((l) => l.includes("源码目录不存在")));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
  const failed = cases.filter((c) => !c.ok);
  console.log(`SELFTEST cases=${cases.length} floor=${MIN_CASES} fail=${failed.length}`);
  for (const c of cases) console.log(`  ${c.ok ? "ok  " : "FAIL"} ${c.name}`);
  if (cases.length < MIN_CASES) return 3;
  return failed.length ? 1 : 0;
}

function main(argv) {
  if (argv[0] === "--selftest") return selftest();
  if (argv.length > 0 && !argv.includes("--json")) return 2;
  const v = verdict(SCAN_ROOT, BASELINE_PATH);
  if (argv.includes("--json")) console.log(JSON.stringify({ scanned: v.scanned, flagged: v.flagged, rc: v.rc }));
  else for (const l of v.lines) console.log(l);
  return v.rc;
}

process.exit(main(process.argv.slice(2)));
