import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * Morandi material consumption on the Web (T-2026-10-22-058).
 *
 * T-2026-10-22-053 added six Morandi tokens to the generated tokens.css and left the Web
 * consumer to this slice. CSS is not rendered by jsdom, so the judgement is read from the
 * shipped source: the shared scene must consume the two glows and an existing tag surface must
 * consume a tint, without inventing surfaces or literals.
 */
const glassPath = fileURLToPath(new URL("../../src/styles/glass.css", import.meta.url));
const tokensPath = fileURLToPath(new URL("../../src/styles/tokens.css", import.meta.url));

function read(path: string): string {
  return readFileSync(path, "utf8");
}

describe("Web 莫兰迪材质消费", () => {
  it("生成的 tokens.css 仍提供六枚新 token", () => {
    const css = read(tokensPath);
    for (const token of [
      "--glow-rose",
      "--glow-lilac",
      "--tint-rose",
      "--tint-lilac",
      "--tint-sand",
      "--tint-sage",
    ]) {
      expect(css, token).toMatch(new RegExp(`${token}:\\s*[^;]+;`));
    }
  });

  it("共享场景消费两处莫兰迪柔光", () => {
    const css = read(glassPath);
    expect(css).toMatch(/background-image:[^;]*var\(--glow-rose\)/s);
    expect(css).toMatch(/background-image:[^;]*var\(--glow-lilac\)/s);
  });

  it("推荐标签的 tint 语义仍在，且材质层不写裸色值", () => {
    // 2026-10-25（T-2026-10-25-093）：原先这里断言的是 glass.css 的 `.rec-tag`，
    // 它在 frontend/src 里没有任何消费者（判据 A3），已随 app.css 退役。
    // 推荐标签的活样式是 pages.css 的 `.suggestion-tag`，颜色依旧只引 token。
    const pages = read(fileURLToPath(new URL("../../src/styles/pages.css", import.meta.url)));
    const css = read(glassPath);
    expect(pages).toMatch(/\.suggestion-tag\s*\{[^}]*color:\s*var\(--green-deep\)/s);
    expect(css.match(/#[0-9a-fA-F]{3,8}\b/g) ?? []).toEqual([]);
  });
});
