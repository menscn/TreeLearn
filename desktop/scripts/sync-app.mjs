#!/usr/bin/env node
/**
 * 把 frontend 的离线单文件产物复制进 desktop/app/，供 Electron 与打包器使用。
 *
 * 为什么不用 dist/index.html：它通过外部 ES 模块加载，file:// 下会被 CORS 拦成空白页
 * （frontend/scripts/inline_offline.mjs 的注释里有同一条实测）。dist-offline/index.html
 * 是内联后的单文件，file:// 下可直接执行。
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DESKTOP = path.resolve(HERE, "..");
const SRC = path.resolve(DESKTOP, "../frontend/dist-offline/index.html");
const OUT_DIR = path.join(DESKTOP, "app");
const OUT = path.join(OUT_DIR, "index.html");

if (!fs.existsSync(SRC)) {
  console.error(`缺少 ${SRC}\n先运行：pnpm --dir frontend build`);
  process.exit(2);
}

fs.mkdirSync(OUT_DIR, { recursive: true });
fs.copyFileSync(SRC, OUT);
console.log(`synced ${path.relative(DESKTOP, SRC).replace(/\\/g, "/")} -> app/index.html (${fs.statSync(OUT).size} bytes)`);
