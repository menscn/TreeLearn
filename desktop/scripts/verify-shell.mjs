#!/usr/bin/env node
/**
 * 桌面壳验收编排：同步产物 → 真实主进程 smoke → 逐屏矩阵。
 * 任一步没有打印判据行即失败，退出码沿用同一纪律：0 成立 / 1 判红 / 2 前置缺失。
 */
import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const electron = require("electron");
const HERE = path.dirname(fileURLToPath(import.meta.url));
const DESKTOP = path.resolve(HERE, "..");
const INDEX = path.join(DESKTOP, "app", "index.html");

function run(args) {
  return spawnSync(process.execPath, args, { cwd: DESKTOP, encoding: "utf8" });
}

function runElectron(args) {
  return spawnSync(electron, args, { cwd: DESKTOP, encoding: "utf8" });
}

function report(name, result, needle) {
  const output = `${result.stdout ?? ""}${result.stderr ?? ""}`.trim();
  const ok = result.status === 0 && output.includes(needle);
  console.log(`${ok ? "PASS" : "FAIL"} ${name}${ok ? "" : `\n${output}`}`);
  return ok;
}

const sync = run(["scripts/sync-app.mjs"]);
const synced = sync.status === 0 && existsSync(INDEX);
console.log(`${synced ? "PASS" : "FAIL"} sync-app -> app/index.html`);
if (!synced) {
  console.error(`${sync.stdout ?? ""}${sync.stderr ?? ""}`.trim());
  process.exit(2);
}

const smoke = report("smoke (真实主进程启动)", runElectron([".", "--smoke"]), "SMOKE_OK");
const matrix = report("matrix (14 屏 × 2 尺寸)", runElectron(["scripts/verify-matrix.cjs"]), "MATRIX_OK");

if (smoke && matrix) {
  console.log("VERIFY_OK desktop shell renders every screen at both window sizes");
  process.exit(0);
}
process.exit(1);
