import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * Depth consumption on the Web (T-2026-10-22-063).
 *
 * The Web already ran a real `backdrop-filter`, so the missing thing was never the blur - it was
 * something for the blur to act on. Two facts have to hold at once for the glass to read as
 * floating: the material carries a measurable projection and a diagonal tint derived from the same
 * falloff ratio Android uses, and content actually travels under the top bar. jsdom does not lay
 * out or paint CSS, so both facts are read from the shipped source; the pixel verdict for the same
 * two facts is `check_glass_depth.py` run on browser screenshots.
 */
const glassPath = fileURLToPath(new URL("../../src/styles/glass.css", import.meta.url));
const workspacePath = fileURLToPath(new URL("../../src/styles/workspace.css", import.meta.url));
const pagesPath = fileURLToPath(new URL("../../src/styles/pages.css", import.meta.url));
const tokensPath = fileURLToPath(new URL("../../src/styles/tokens.css", import.meta.url));

function read(path: string): string {
  expect(existsSync(path), `${path} must exist`).toBe(true);
  return readFileSync(path, "utf8");
}

/** The body of one top-level rule, keyed by its selector text. */
function rule(css: string, selector: string): string {
  const at = css.indexOf(`${selector} {`);
  expect(at, `missing rule: ${selector}`).toBeGreaterThanOrEqual(0);
  const open = css.indexOf("{", at);
  return css.slice(open + 1, css.indexOf("}", open));
}

describe("Web 玻璃纵深消费", () => {
  it("被取代的 --glass-shadow 在链上与消费面都已清空", () => {
    expect(read(glassPath)).not.toContain("var(--glass-shadow)");
    expect(read(tokensPath)).not.toMatch(/--glass-shadow\s*:/);
  });

  it("投影两档各引用一枚 token，几何照抄方案 A 的 .glass", () => {
    const css = read(glassPath);
    expect(css).toMatch(/box-shadow:[^;]*0 20px 40px -28px var\(--glass-shadow-key\)/s);
    expect(css).toMatch(/box-shadow:[^;]*0 2px 6px var\(--glass-shadow-ambient\)/s);
  });

  it("四面 tint 的暗端由 falloff 推导，不各写一份字面量", () => {
    const css = read(glassPath);
    for (const face of ["--glass-chrome", "--glass-card", "--glass-reader", "--glass-field"]) {
      const derived = new RegExp(
        `linear-gradient\\(145deg,[^;]*var\\(${face}\\)[^;]*` +
          `color-mix\\(in srgb,\\s*var\\(${face}\\)\\s+calc\\(var\\(--glass-tint-falloff\\) \\* 100%\\),\\s*transparent\\)`,
        "s",
      );
      expect(css, face).toMatch(derived);
    }
  });

  it("控制面平涂：标签压在别人的文字之上，担不起 tint 的暗半段", () => {
    const css = read(glassPath);
    expect(css).not.toMatch(/linear-gradient\(145deg[^;]*var\(--glass-control\)/);
    expect(css).toMatch(/\.persona-feedback[^{]*\{[^}]*var\(--glass-control\)/s);
  });

  it("整页背景不再画横格，但场景的渐变与五处柔光一处不少", () => {
    // 2026-10-23 用户判定"背景中的横线…现在不好看"，要求所有界面去掉，同时点名不要动背景的颜色分布。
    // 于是这一片只许删一层：删掉的必须是横格，留下的必须是同一批渐变与柔光。
    const body = rule(read(glassPath), "body");
    expect(body).not.toContain("repeating-linear-gradient");
    expect(body).not.toContain("--paper-rule");
    for (const glow of ["--glow-rose", "--glow-lilac", "--glow-white", "--glow-green", "--glow-sand"]) {
      expect(body, `${glow} must still be in the scene`).toContain(`var(${glow})`);
    }
    expect(body).toMatch(
      /linear-gradient\(135deg, var\(--mist-warm\), var\(--mist-base\) 38%, var\(--mist-cool\) 72%, var\(--mist-deep\)\)/,
    );
  });

  it("顶栏是覆盖层，滚动容器自己付顶部留白，让内容从玻璃下方经过", () => {
    const ws = read(workspacePath);
    const bar = rule(ws, ".workspace-topbar");
    expect(bar).toMatch(/position:\s*absolute/);
    expect(bar).toMatch(/z-index:/);
    expect(rule(ws, ".workspace-column")).toMatch(/position:\s*relative/);
    const height = bar.match(/height:\s*(\d+)px/);
    expect(height, "the bar keeps one declared height").not.toBeNull();
    const rooms: Record<string, string> = {
      ".chat-scroll": ws,
      ".page-scroll": read(pagesPath),
    };
    for (const [scroller, css] of Object.entries(rooms)) {
      const pad = rule(css, scroller).match(/padding-top:\s*(\d+)px/);
      expect(pad, `${scroller} must take the bar's room`).not.toBeNull();
      expect(pad?.[1], `${scroller} drifts from the bar height`).toBe(height?.[1]);
    }
  });

  it("窄窗栏高降下来时，两条留白跟着降，玻璃下缘不留空带", () => {
    const narrow = (path: string) => {
      const css = read(path);
      return css.slice(css.indexOf("@media (max-width: 760px)"));
    };
    const wsBar = rule(narrow(workspacePath), ".workspace-topbar");
    const height = wsBar.match(/height:\s*(\d+)px/);
    expect(height, "the narrow bar declares its own height").not.toBeNull();
    for (const [scroller, css] of [
      [".chat-scroll", narrow(workspacePath)],
      [".page-scroll", narrow(pagesPath)],
    ] as const) {
      const pad = rule(css, scroller).match(/padding-top:\s*(\d+)px/);
      expect(pad, `${scroller} must follow the narrow bar`).not.toBeNull();
      expect(pad?.[1], `${scroller} leaves a gap under the narrow bar`).toBe(height?.[1]);
    }
  });

  it("两条既有降级与窄窗侧栏都还在", () => {
    const css = read(glassPath);
    expect(css).toMatch(/@supports not \(\(backdrop-filter/s);
    expect(css).toMatch(/@media \(prefers-reduced-transparency: reduce\)/);
    expect(css).toMatch(/@media \(max-width: 760px\)[^{]*\{[^}]*\.app-sidebar/s);
  });

  it("材质层不写裸色值", () => {
    const css = read(glassPath);
    expect(css.match(/#[0-9a-fA-F]{3,8}\b/g) ?? []).toEqual([]);
  });
});
