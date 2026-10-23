import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * Structural pins for the Web glass layer (T-2026-10-21-050).
 *
 * CSS is not rendered by jsdom, so the judgement is read from the shipped source: the layer must
 * consume design tokens only, cover the same surfaces the proposal covers, and keep both fallback
 * branches. Reading the file also fails loudly if the layer is dropped from the entry point.
 */
const glassPath = fileURLToPath(new URL("../../src/styles/glass.css", import.meta.url));
const mainPath = fileURLToPath(new URL("../../src/main.tsx", import.meta.url));
const tokensPath = fileURLToPath(new URL("../../src/styles/tokens.css", import.meta.url));

function glass(): string {
  expect(existsSync(glassPath), "glass.css must exist").toBe(true);
  return readFileSync(glassPath, "utf8");
}

describe("Web 晨雾玻璃材质层", () => {
  it("只消费设计 token，不写裸色值", () => {
    const css = glass();
    const bareHex = css.match(/#[0-9a-fA-F]{3,8}\b/g) ?? [];
    expect(bareHex, "裸色值必须回到 tokens.css").toEqual([]);
    for (const token of [
      "--glass-chrome",
      "--glass-card",
      "--glass-reader",
      "--glass-field",
      "--glass-edge",
      "--glass-line",
      "--glass-sheen",
      "--glow-green",
      "--glow-sand",
      "--glow-white",
      "--mist-base",
      "--mist-deep",
      "--mist-warm",
      "--mist-cool",
      "--glass-blur",
      "--glass-saturate",
      "--glass-shadow-key",
      "--glass-shadow-ambient",
      "--glass-tint-falloff",
      "--r-xl",
      "--r-2xl",
    ]) {
      expect(css, token).toContain(`var(${token})`);
    }
  });

  it("场景覆盖 body，工作区与外壳改为玻璃", () => {
    const css = glass();
    for (const selector of [
      "body",
      ".app-workspace",
      ".app-sidebar",
      ".workspace-topbar",
      ".chat-reading",
      ".chat-dock",
      ".composer",
      ".branch-panel",
      ".tree-dialog",
      ".suggestion",
      ".search-field",
      ".settings-page .field",
      ".settings-action",
    ]) {
      expect(css, selector).toContain(selector);
    }
    expect(css).toMatch(/\.chat-reading[^{]*\{[^}]*var\(--glass-reader\)/s);
    expect(css).toMatch(/\.composer[^{]*\{[^}]*var\(--glass-card\)/s);
  });

  it("大面才启用真实模糊，并保留不支持时的回退", () => {
    const css = glass();
    expect(css).toContain("backdrop-filter");
    expect(css).toContain("-webkit-backdrop-filter");
    expect(css).toContain("@supports not");
    expect(css).toContain("prefers-reduced-transparency");
    expect(css).toContain("inset 0 0 0 1px var(--glass-edge)");
    expect(css).toMatch(/@media \(max-width: 760px\)[^{]*\{[^}]*\.app-sidebar/s);
  });

  it("在 pages.css 之后加载，成为最后一层材质覆盖", () => {
    expect(existsSync(mainPath), "main.tsx must exist").toBe(true);
    const main = readFileSync(mainPath, "utf8");
    const pages = main.indexOf('"./styles/pages.css"');
    const layer = main.indexOf('"./styles/glass.css"');
    expect(pages).toBeGreaterThanOrEqual(0);
    expect(layer).toBeGreaterThan(pages);
  });

  /**
   * 064 拍板：玻璃要读得出「有一块面」，五面的 alpha 就不能掉回这些值以下。
   * 断言写的是下限而不是当前值——把 token 抄进测试会出现第二份事实，改 token 时它不会响。
   */
  it("五面 alpha 停在拍板下限之上", () => {
    expect(existsSync(tokensPath), "tokens.css must exist").toBe(true);
    const css = readFileSync(tokensPath, "utf8");
    const floors: Array<[string, number]> = [
      ["glass-chrome", 0.7],
      ["glass-card", 0.84],
      ["glass-reader", 0.92],
      ["glass-field", 0.7],
    ];
    for (const [name, floor] of floors) {
      const found = css.match(new RegExp(`--${name}:\\s*rgba\\([^)]*,\\s*([0-9.]+)\\)`));
      expect(found, `tokens.css must carry --${name} as rgba`).not.toBeNull();
      expect(Number.parseFloat(found?.[1] ?? ""), `--${name} 低于拍板下限`).toBeGreaterThanOrEqual(floor);
    }
  });
});
