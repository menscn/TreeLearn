#!/usr/bin/env node
/**
 * 依赖方向守门：`src/**` 只允许按 pages→layouts→components→hooks→store→api→platform→utils→copy
 * 单向 import，且任何文件不得绕过 `platform/RuntimePort` 直读仓库里的夹具或源码外文件。
 *
 * 分层目录名单的唯一来源是规格 §2 与本文件的 LAYERS；任务卡只指向本文件，不复述名单。
 * 用法：node frontend/scripts/check_dependency_direction.mjs [--selftest]
 * 退出码：0 成立 / 1 方向或边界不符 / 2 用法错误 / 3 仪器故障（扫描根缺失、可判文件低于下限）。
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const HERE = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
const FRONTEND = path.resolve(HERE, "..");
const SCAN_ROOT = path.join(FRONTEND, "src");
const SCAN_ROOT_REL = "src"; // 字面量：与 SCAN_ROOT 无派生关系，自检夹具根不会跟着常量挪

const LAYERS = ["pages", "layouts", "components", "hooks", "store", "api", "platform", "utils", "copy"];
const ENTRY_ALLOWLIST = ["main.tsx"]; // 分层层之外的入口文件，逐个点名，不容通配
const MIN_FILES = 8; // 裸字面量下限：扫到 0 个文件时「零违规」是假见证
const MIN_CASES = 8;

const RANK = new Map(LAYERS.map((name, i) => [name, i]));

function listSourceFiles(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...listSourceFiles(p));
    else if (/\.tsx?$/.test(entry.name)) out.push(p);
  }
  return out.sort();
}

function relOf(root, file) {
  return path.relative(root, file).split(path.sep).join("/");
}

/** 顶层分层目录名；不在名单里也不是入口文件的，返回 null 交给调用方点名。 */
function layerOf(rel) {
  const top = rel.includes("/") ? rel.slice(0, rel.indexOf("/")) : "";
  return RANK.has(top) ? top : null;
}

function importsOf(text) {
  const out = [];
  const re = /(?:^|[\s;}])(?:import|export)[^;'"\n]*?from\s*["']([^"']+)["']|import\s*\(\s*["']([^"']+)["']\s*\)/g;
  for (const m of text.matchAll(re)) out.push(m[1] ?? m[2]);
  return out;
}

/** 把一条 import 规格解析成它落在扫描树内的相对路径；树外/包名返回 null 并说明原因。 */
function resolveInside(rootRel, fromRel, spec) {
  if (!spec.startsWith(".")) {
    return { kind: "external" }; // 包名或绝对路径：由离线守门与依赖清单管，本守门不判
  }
  const joined = path.posix.normalize(path.posix.join(path.posix.dirname(fromRel), spec));
  if (joined.startsWith("../")) {
    return { kind: "escapes", target: joined }; // 越出 src：绕过端口直读夹具/仓库文件就是这条
  }
  return { kind: "inside", target: joined };
}

function scan(root) {
  if (!fs.existsSync(root)) return { error: "root-missing", findings: [], scanned: 0 };
  const files = listSourceFiles(root);
  const rootRel = relOf(path.dirname(root), root);
  const findings = [];
  for (const f of files) {
    const rel = relOf(root, f);
    const layer = layerOf(rel);
    if (layer === null && !ENTRY_ALLOWLIST.includes(rel)) {
      findings.push(`${rel}: 文件不在声明的分层目录里，也不在入口名单里`);
      continue;
    }
    const fromRank = layer === null ? -1 : RANK.get(layer);
    for (const spec of importsOf(fs.readFileSync(f, "utf8"))) {
      const r = resolveInside(rootRel, rel, spec);
      if (r.kind === "escapes") {
        findings.push(`${rel}: import "${spec}" 越出 ${rootRel}/ 之外（${r.target}），夹具与源码外文件只能经 platform 端口取`);
      } else if (r.kind === "inside") {
        const targetLayer = layerOf(r.target);
        if (targetLayer !== null && RANK.get(targetLayer) < fromRank) {
          findings.push(`${rel}(${layer}) → ${r.target}(${targetLayer}) 反向 import：下游不得依赖上游`);
        }
      }
    }
  }
  return { findings, scanned: files.length, rootRel };
}

function verdict(root) {
  const { findings, scanned, error } = scan(root);
  const head = [`dependency scan root=${SCAN_ROOT_REL} layers=${LAYERS.length} scanned=${scanned} findings=${findings.length}`];
  for (const f of findings) head.push(`  VIOLATION ${f}`);
  const out = (rc, label) => ({ scanned, flagged: findings.length, rc, lines: [...head, label] });
  if (error === "root-missing") return out(3, `DEPENDENCY FAIL 扫描根不存在 ${SCAN_ROOT_REL}`);
  if (scanned < MIN_FILES) return out(3, `DEPENDENCY FAIL 可判文件 ${scanned} 低于下限 ${MIN_FILES}，零集合不等于通过`);
  if (findings.length) return out(1, "DEPENDENCY FAIL 依赖方向或端口边界不成立");
  return out(0, `dependency direction ok — ${scanned} 个文件全部按 ${LAYERS.join("→")} 单向`);
}

function selftest() {
  const cases = [];
  const push = (name, ok) => cases.push({ name, ok });
  const dir = fs.mkdtempSync(path.join(process.env.TEMP ?? process.env.TMPDIR ?? "/tmp", "tl-depdir-"));
  const LITERAL_ROOT = "src"; // 字面量，与 SCAN_ROOT 无派生关系
  try {
    const mk = (rel, body) => {
      const p = path.join(dir, LITERAL_ROOT, rel);
      fs.mkdirSync(path.dirname(p), { recursive: true });
      fs.writeFileSync(p, body);
    };
    mk("pages/Home.tsx", 'import { AppShell } from "../layouts/AppShell";\nexport const Home = () => null;\n');
    mk("layouts/AppShell.tsx", 'import { useCopy } from "../hooks/useCopy";\nexport const AppShell = null;\n');
    mk("hooks/useCopy.ts", 'import { copyText } from "../copy/index";\nexport const useCopy = copyText;\n');
    mk("copy/index.ts", "export const copyText = (k: string) => k;\n");
    mk("utils/deviceTime.ts", "export const now = 1;\n");
    mk("platform/RuntimePort.ts", 'import { now } from "../utils/deviceTime";\nexport const p = now;\n');
    mk("platform/runtime.ts", 'import type { RuntimePort } from "./RuntimePort";\nexport const q: RuntimePort | null = null;\n');
    mk("main.tsx", 'import { Home } from "./pages/Home";\nexport default Home;\n');
    mk("store/session.ts", 'import { Home } from "../pages/Home";\nexport const bad = Home;\n');
    mk("components/Bad.tsx", 'import fix from "../../artifacts/fixtures/parity/chat.json";\nexport const v = fix;\n');

    const dirty = verdict(path.join(dir, LITERAL_ROOT));
    push("反向 import 被点名", dirty.lines.some((l) => l.includes("store/session.ts") && l.includes("反向 import")));
    push("越出 src 直读夹具被点名", dirty.lines.some((l) => l.includes("components/Bad.tsx") && l.includes("越出")));
    push("合法方向链不被点名", !dirty.lines.some((l) => l.trim().startsWith("VIOLATION pages/Home.tsx")));
    push("入口文件按名单放行", !dirty.lines.some((l) => l.includes("main.tsx")));
    push("可判文件计数含全部样本", dirty.scanned === 10);
    push("违规存在判 1", dirty.rc === 1);
    push("分层名单与规格一致（按当前态逐字比对）", LAYERS.join(",") === "pages,layouts,components,hooks,store,api,platform,utils,copy"
      && SCAN_ROOT_REL === LITERAL_ROOT && MIN_FILES > 0 && MIN_CASES > 0);

    fs.rmSync(path.join(dir, LITERAL_ROOT, "store"), { recursive: true, force: true });
    fs.rmSync(path.join(dir, LITERAL_ROOT, "components"), { recursive: true, force: true });
    const clean = verdict(path.join(dir, LITERAL_ROOT));
    push("去掉两处违规后同一棵树判通过（反向对照）", clean.rc === 0 && clean.flagged === 0);

    fs.rmSync(path.join(dir, LITERAL_ROOT), { recursive: true, force: true });
    const gone = verdict(path.join(dir, LITERAL_ROOT));
    push("扫描根缺失判 3", gone.rc === 3 && gone.lines.some((l) => l.includes("扫描根不存在")));

    fs.mkdirSync(path.join(dir, LITERAL_ROOT, "pages"), { recursive: true });
    fs.writeFileSync(path.join(dir, LITERAL_ROOT, "pages", "Only.tsx"), "export const Only = 1;\n");
    const few = verdict(path.join(dir, LITERAL_ROOT));
    push("可判文件低于下限判 3 并点名下限", few.rc === 3 && few.lines.some((l) => l.includes("低于下限")));
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
  if (argv.length > 0 && argv[0] !== "--help") return 2;
  if (argv[0] === "--help") {
    console.log("usage: node frontend/scripts/check_dependency_direction.mjs [--selftest]");
    return 2;
  }
  const v = verdict(SCAN_ROOT);
  for (const l of v.lines) console.log(l);
  return v.rc;
}

process.exit(main(process.argv.slice(2)));
