#!/usr/bin/env node
/**
 * 打包便携版 exe。构建输出放在系统临时目录：工作区内的 .asar 会被 IDE 文件索引占用，
 * electron-builder 再次清理时报 EBUSY；只在最后把成品 exe 拷回 desktop/release。
 */
import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const DESKTOP = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const RELEASE = path.join(DESKTOP, "release");
const CLI = require.resolve("electron-builder/cli.js");
const MIRROR = "https://npmmirror.com/mirrors/electron-builder-binaries/";

const sync = spawnSync(process.execPath, ["scripts/sync-app.mjs"], { cwd: DESKTOP, stdio: "inherit" });
if (sync.status !== 0) process.exit(sync.status ?? 1);

// A unique directory avoids deleting a previous build or another process's files.
const BUILD = fs.mkdtempSync(path.join(os.tmpdir(), "treelearn-desktop-build-"));
const build = spawnSync(process.execPath, [CLI, "--win", "portable", `--config.directories.output=${BUILD}`], {
  cwd: DESKTOP,
  stdio: "inherit",
  env: { ...process.env, ELECTRON_BUILDER_BINARIES_MIRROR: process.env.ELECTRON_BUILDER_BINARIES_MIRROR ?? MIRROR }
});
if (build.status !== 0) process.exit(build.status ?? 1);

const { version } = require(path.join(DESKTOP, "package.json"));
const artifact = path.join(BUILD, `Treelearn-${version}-portable.exe`);
if (!fs.existsSync(artifact)) {
  console.error(`缺少打包产物：${artifact}`);
  process.exit(1);
}
fs.mkdirSync(RELEASE, { recursive: true });
// Preserve any existing portable build, including one currently in use.
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const target = path.join(RELEASE, `Treelearn-${version}-portable-${stamp}.exe`);
fs.copyFileSync(artifact, target, fs.constants.COPYFILE_EXCL);
console.log(`PACKAGE_OK ${target} ${fs.statSync(target).size}`);
