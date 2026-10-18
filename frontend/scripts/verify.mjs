#!/usr/bin/env node
/**
 * `pnpm --dir frontend verify`：阶段 2 出口门禁的唯一汇总入口。
 *
 * 它不新增判据，只按顺序真实执行四个入口与四支守门的正反自检，
 * 断言每条判决行确实被打出来（「没打印判据行」不等于「判据通过」），最后给 want/got 表与总退出码。
 * 用法：node frontend/scripts/verify.mjs [--keep-dist]
 * 退出码：0 全部成立 / 1 任一步不符 / 3 仪器故障（解释器或脚本缺失，无法归因给产物）。
 */
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { spawnSync } from "node:child_process";

const HERE = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
const FRONTEND = path.resolve(HERE, "..");
const SCRIPTS = ["check_dependency_direction", "check_user_copy_i18n", "check_device_time", "check_offline_bundle"];

/** 每步都写明 want（期望 rc + 必须出现的判决行片段），got 由真实执行填入。 */
function plan() {
  const steps = [
    { name: "lint（类型 + 三支静态守门）", cmd: ["pnpm", "run", "lint"], want: 0, needle: "user copy ok" },
    { name: "test（vitest 全量）", cmd: ["pnpm", "run", "test"], want: 0, needle: "Tests " },
    { name: "build（产物 + 离线判定）", cmd: ["pnpm", "run", "build"], want: 0, needle: "offline ok" }
  ];
  for (const s of SCRIPTS) {
    const file = path.join(FRONTEND, "scripts", `${s}.mjs`);
    steps.push({ name: `${s} 判决`, node: file, args: [], want: 0, needle: " ok" });
    steps.push({ name: `${s} --selftest`, node: file, args: ["--selftest"], want: 0, needle: `SELFTEST cases=` });
  }
  return steps;
}

function run(step) {
  const [head, ...rest] = step.cmd ?? [];
  const res = step.node
    ? spawnSync(process.execPath, [step.node, ...step.args], { cwd: FRONTEND, encoding: "utf8", env: { ...process.env, NO_COLOR: "1", FORCE_COLOR: "0" } })
    : spawnSync(head, rest, { cwd: FRONTEND, encoding: "utf8", shell: true, env: { ...process.env, NO_COLOR: "1", FORCE_COLOR: "0" } });
  if (res.error) return { rc: 3, out: String(res.error), fault: true };
  return { rc: res.status, out: `${res.stdout ?? ""}${res.stderr ?? ""}`, fault: false };
}

function main() {
  const rows = [];
  for (const step of plan()) {
    if (step.node && !fs.existsSync(step.node)) {
      rows.push({ name: step.name, want: step.want, got: 3, needle: step.needle, found: false, fault: true });
      continue;
    }
    const r = run(step);
    const found = r.out.includes(step.needle);
    rows.push({ name: step.name, want: step.want, got: r.rc, needle: step.needle, found, fault: r.fault });
    if (r.rc !== step.want || !found) {
      for (const line of r.out.split("\n").filter((l) => l.trim()).slice(-14)) console.log(`    | ${line}`);
    }
  }
  console.log("\n| step | want | got | 判决行 | 命中 |");
  console.log("|---|---|---|---|---|");
  for (const r of rows) {
    console.log(`| ${r.name} | rc=${r.want} | rc=${r.got}${r.fault ? " (fault)" : ""} | "${r.needle}" | ${r.found ? "yes" : "NO"} |`);
  }
  const bad = rows.filter((r) => r.got !== r.want || !r.found);
  const faulty = rows.filter((r) => r.fault);
  const overall = faulty.length ? 3 : bad.length ? 1 : 0;
  console.log(`\nverify steps=${rows.length} ok=${rows.length - bad.length} bad=${bad.length} fault=${faulty.length}`);
  console.log(bad.length || faulty.length ? "VERIFY FAIL" : "VERIFY PASS");
  console.log(`OVERALL_EXITCODE=${overall}`);
  return overall;
}

process.exit(main());
