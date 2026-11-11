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
import { extractPersonaPrompts } from "./extract-persona-prompts.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DESKTOP = path.resolve(HERE, "..");
const SRC = path.resolve(DESKTOP, "../frontend/dist-offline/index.html");
const OUT_DIR = path.join(DESKTOP, "app");
const OUT = path.join(OUT_DIR, "index.html");
const PROMPT_SOURCE = path.resolve(DESKTOP, "../android/ai/src/main/java/com/treelearn/ai/prompt/PromptBodiesV1.java");
const PERSONA_SOURCE = path.resolve(DESKTOP, "../android/core/src/main/java/com/treelearn/core/domain/Persona.java");
const PERSONA_PROMPTS_SOURCE = path.resolve(DESKTOP, "../android/ai/src/main/java/com/treelearn/ai/prompt/PersonaPrompts.java");

if (!fs.existsSync(SRC)) {
  console.error(`缺少 ${SRC}\n先运行：pnpm --dir frontend build`);
  process.exit(2);
}

fs.mkdirSync(OUT_DIR, { recursive: true });
// The Android tutor remains the single authored prompt; ship a derived resource in the desktop bundle.
const source = fs.readFileSync(PROMPT_SOURCE, "utf8").replace(/\r\n/g, "\n");
const block = source.match(/static final String LEARNING_ANSWER_TUTOR = """\n([\s\S]*?)\n\s*""";/);
if (!block) throw new Error("学习导师提示词来源不可用");
const prompt = block[1].split("\n").map(line => line.startsWith("            ") ? line.slice(12) : line).join("\n").replace(/\\$/, "");
if (!prompt.trim()) throw new Error("学习导师提示词为空");
const personas = extractPersonaPrompts(fs.readFileSync(PERSONA_SOURCE, "utf8"), fs.readFileSync(PERSONA_PROMPTS_SOURCE, "utf8"));
fs.writeFileSync(path.join(OUT_DIR, "learning-prompt.json"), JSON.stringify({ version: 1, prompt, personas }), "utf8");
fs.copyFileSync(SRC, OUT);
console.log(`synced ${path.relative(DESKTOP, SRC).replace(/\\/g, "/")} -> app/index.html (${fs.statSync(OUT).size} bytes)`);
