import { existsSync, readFileSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * 衬线字面随包（T-2026-10-25-090，086 线 X1）。
 *
 * jsdom 不加载字体、也不做宿主字体解析，所以这条判据只能从落盘源码与签入资产读：
 * 「Web 声明了哪个族名」「那个族名有没有真的随包字节」「兜底栈在 Windows 上会落到哪」。
 *
 * 为什么要有这条测试而不是只靠 `.codex/scripts/dev/check_ui_asset_parity.py`：
 * 守门判的是仓库整体形状（A1–A6），要跑在 `run_all_guards.py` 的档位里；这条判的是
 * **前端自己的构建面**，跟着 `pnpm --dir frontend test` 每次跑，改 import 或删资产的人
 * 在 vitest 阶段就该被拦住，而不是等到收尾整跑才发现多了一条红。两者判据同向，不互相代替。
 */
const tokensPath = fileURLToPath(new URL("../../src/styles/tokens.css", import.meta.url));
const fontsCssPath = fileURLToPath(new URL("../../src/styles/fonts.css", import.meta.url));
const mainPath = fileURLToPath(new URL("../../src/main.tsx", import.meta.url));
const inlinePath = fileURLToPath(new URL("../../scripts/inline_offline.mjs", import.meta.url));

const SUBSET_MAX = 6 * 1024 * 1024; // 与 check_ui_asset_parity.INLINE_FONT_MAX 同档
const WINDOWS_SERIF = ["SimSun", "NSimSun", "SimSun-ExtB", "宋体"];

function read(path: string): string {
  return readFileSync(path, "utf8");
}

/** RED 阶段字体文件还不存在：抛在单条用例里，别让整个 suite 挂掉冒充判据。 */
function readRequired(path: string, why: string): string {
  if (!existsSync(path)) throw new Error(`${why}（${path}）`);
  return read(path);
}

/** `--serif` 的原始族名列表（保留引号，顺序即降级顺序）。 */
function serifParts(css: string): string[] {
  const stack = /--serif\s*:\s*([^;]+);/.exec(css)?.[1];
  if (stack === undefined) throw new Error("tokens.css 里没有 --serif 声明");
  return stack.split(",").map((p) => p.trim());
}

/** `--serif` 的第一个引号族名 = 设计意图的那个字面（裸 generic 关键字不算声明身份）。 */
function firstSerifFamily(css: string): string {
  for (const part of serifParts(css)) {
    const fam = /^(["'])(.+)\1$/.exec(part)?.[2];
    if (fam) return fam;
  }
  throw new Error("--serif 栈里没有任何带引号的族名");
}

describe("衬线字面随包", () => {
  const tokens = read(tokensPath);
  const wanted = firstSerifFamily(tokens);
  const fontsCss = (): string =>
    readRequired(fontsCssPath, "frontend/src/styles/fonts.css 不存在：衬线身份没有随包声明，Windows 目标机落到宋体");

  it("@font-face 的族名与 --serif 的首选一致", () => {
    const css = fontsCss();
    const faces = [...css.matchAll(/@font-face\s*\{([^}]*)\}/g)].map((m) => m[1] ?? "");
    expect(faces.length, "@font-face 声明数为 0").toBeGreaterThan(0);
    const hit = faces.filter((b) => ((/font-family\s*:\s*["']?([^;"']+)["']?\s*;/.exec(b)?.[1] ?? "").trim()) === wanted);
    expect(hit.length, `没有任何 @font-face 声明 ${wanted}（--serif 首选与随包字面必须同名）`).toBeGreaterThan(0);
  });

  it("src 只允许包内字节，不许出现任何远端加载", () => {
    const css = fontsCss();
    expect(css).not.toMatch(/https?:\/\/\S*(woff2?|ttf|otf)/i);
    expect(/url\(\s*["']?\.\.\/fonts\/[\w.-]+\.woff["']?\s*\)/.test(css), "src 必须指向 frontend/src/fonts/ 下签入的子集").toBe(true);
    expect(/format\(\s*["']woff["']\s*\)/.test(css), 'src 必须声明 format("woff")，否则浏览器不知道这段字节怎么解').toBe(true);
    expect(/font-display:\s*swap/.test(css), "随包字体必须 font-display: swap，否则首屏文字要等子集解完才出现").toBe(true);
  });

  it("子集资产真的签在仓库里，且体积不超过判据上限", () => {
    const css = fontsCss();
    const rel = /url\(\s*["']?\.\.\/(fonts\/[\w.-]+\.woff)["']?\s*\)/.exec(css)?.[1] ?? "";
    expect(rel, "fonts.css 里没有指向 ../fonts/ 的 src").not.toBe("");
    const assetPath = fileURLToPath(new URL(`../../src/${rel}`, import.meta.url));
    expect(existsSync(assetPath), `签入的字体资产不存在：${assetPath}`).toBe(true);
    const size = statSync(assetPath).size;
    expect(size, "子集不到 100KB 说明生成脚本挑错了字符集或失败了").toBeGreaterThan(100_000);
    expect(size, `子集 ${size} 字节超过上限 ${SUBSET_MAX}`).toBeLessThanOrEqual(SUBSET_MAX);
  });

  it("OFL 许可文本随包（子集化再分发是 OFL 允许的，但许可必须一起走）", () => {
    const lic = readRequired(
      fileURLToPath(new URL("../../src/fonts/OFL.txt", import.meta.url)),
      "frontend/src/fonts/OFL.txt 不存在：子集是 OFL 授权下的再分发，许可文本要一起随包"
    );
    expect(/SIL Open Font License/i.test(lic), "许可文本里没有 SIL Open Font License 字样").toBe(true);
  });

  it("兜底栈里有一个 Windows 真实存在的中文衬线", () => {
    const names = serifParts(tokens).map((p) => p.replace(/^["']|["']$/g, "").toLowerCase());
    const hit = WINDOWS_SERIF.find((w) => names.includes(w.toLowerCase()));
    expect(hit, `--serif 栈 ${names.join(" / ")} 里没有任何 Windows 自带中文衬线`).toBeTruthy();
  });

  it("fonts.css 被 main.tsx 引进构建面", () => {
    const main = read(mainPath);
    expect(/import\s+["']\.\/styles\/fonts\.css["']/.test(main), "main.tsx 没有 import ./styles/fonts.css：@font-face 进了样式表但没进构建图").toBe(true);
  });

  it("离线打包链会把字体资产转成 data: 内联字节", () => {
    const mjs = read(inlinePath);
    expect(/woff2?|ttf|otf/.test(mjs), "inline_offline.mjs 不认 woff/otf/ttf 这类字体后缀，字体资产不会被内联").toBe(true);
    expect(/base64/.test(mjs), "字体必须以 base64 内联，否则单文件产物离线时拿不到字节").toBe(true);
  });
});
