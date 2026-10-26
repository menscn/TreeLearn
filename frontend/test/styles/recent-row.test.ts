import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * 侧栏历史行（T-2026-10-25-097，评审发现 WH1／WH3）。
 *
 * 侧栏那一列原来混了三种东西：路由（首页／学习库／设置）、一张路由卡（我的森林）、
 * 以及历史内容（最近学习）。三者共用一块 hover 底色时，历史行读起来像第四个入口；
 * 而历史行自己的标题、meta 与箭头又分散在两行里，眼睛找标题、手找箭头，中间隔着正文密度的字。
 *
 * 这条判据钉住改后的形状：历史行的 hover 只提字色，不进同一块路由底色；
 * 标题、meta 与箭头排成一行（meta 与箭头不参与压缩），摘要另起一行并收 1 行。
 * 用源码判而不是 jsdom 判，是因为这四件事全是 CSS 的职责——DOM 里结构对了，
 * 样式仍可能把三样挤成两行。
 */
const workspacePath = fileURLToPath(new URL("../../src/styles/workspace.css", import.meta.url));
// 注释里会出现花括号与选择器字样，先剥掉再解析，免得把说明文字读成规则。
const workspace = readFileSync(workspacePath, "utf8").replace(/\/\*[\s\S]*?\*\//g, "");

/** 按选择器列表取规则体：一条规则可以挂多个选择器（`.side-link:hover, .brand:hover`）。 */
function rule(selector: string): string {
  for (const match of workspace.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const selectorList = (match[1] ?? "").split(",").map((part) => part.trim());
    if (selectorList.includes(selector)) return match[2] ?? "";
  }
  return "";
}

describe("侧栏历史行", () => {
  it("历史行的 hover 只提字色，不再与路由共用那块底色", () => {
    const hover = rule(".recent-link:hover");
    expect(hover, ".recent-link:hover 必须存在").not.toBe("");
    expect(hover).toMatch(/color:\s*var\(--ink\)/);
    expect(hover, "hover 一旦加底色，历史行就又读成第四个路由").not.toMatch(/background/);
    expect(rule(".side-link:hover")).toMatch(/background:\s*var\(--green-faint\)/);
  });

  it("标题、meta 与箭头排在同一行，meta 与箭头不参与压缩", () => {
    expect(rule(".recent-head")).toMatch(/display:\s*flex/);
    expect(rule(".recent-meta")).toMatch(/flex:\s*none/);
    expect(rule(".recent-meta")).toMatch(/font-size:\s*var\(--fs-cap\)/);
    expect(rule(".recent-chevron")).toMatch(/flex:\s*none/);
    expect(rule(".recent-title")).toMatch(/text-overflow:\s*ellipsis/);
  });

  it("摘要另起一行，收 1 行", () => {
    const summary = rule(".recent-summary");
    expect(summary).toMatch(/-webkit-line-clamp:\s*1/);
    expect(summary).toMatch(/font-size:\s*var\(--fs-cap\)/);
  });
});
