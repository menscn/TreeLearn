// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { WorkspaceDataProvider } from "../../src/components/WorkspaceDataProvider";
import { copyFormat, copyText } from "../../src/copy";
import { AppShell } from "../../src/layouts/AppShell";
import { WorkspaceRouter } from "../../src/pages/WorkspaceRouter";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import { personaNameKey } from "../../src/store/persona";
import {
  openView,
  resetWorkspace,
  setTopicPersona,
  workspaceSnapshot
} from "../../src/store/workspace";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const SEPARATOR = ` ${copyText("forest_meta_separator")} `;
const FOOT_SEPARATOR = ` ${copyText("forest_foot_separator")} `;
const COUNT_PREFIX = `${copyText("forest_meta_separator")} `;
const TITLES = [
  "注意力权重可视化与模型可解释性的关系",
  "系统理解 Transformer 的注意力机制"
] as const;

async function render(host: HTMLElement, node: ReactNode): Promise<Root> {
  let root!: Root;
  await act(async () => {
    root = createRoot(host);
    root.render(node);
    await new Promise((resolve) => setTimeout(resolve, 0));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  return root;
}

async function press(host: HTMLElement, selector: string): Promise<void> {
  const el = host.querySelector(selector);
  expect(el, selector).not.toBeNull();
  await act(async () => {
    el!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function workspaceShell(): ReactNode {
  return createElement(
    WorkspaceDataProvider,
    null,
    createElement(AppShell, null, createElement(WorkspaceRouter))
  );
}

/** 改夹具文本得到变体：只动 payload，不动信封字段，parseEnvelope 仍然放行。 */
function variant(mutate: (envelope: Record<string, unknown>) => void): Record<string, string> {
  const entries = { ...bundledEntries() };
  const raw = JSON.parse(entries.library ?? "{}") as Record<string, unknown>;
  mutate(raw);
  entries.library = JSON.stringify(raw);
  return entries;
}

function withoutLibrary(): Record<string, string> {
  const entries = { ...bundledEntries() };
  delete entries.library;
  return entries;
}

function textOf(host: HTMLElement, selector: string): string | null {
  return host.querySelector(selector)?.textContent ?? null;
}

describe("Web 我的森林：入口、林地、选中纸条与诚实页脚", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  const mount = async (entries: Record<string, string> = bundledEntries()) => {
    restore = injectRuntime(new FixtureRuntimeAdapter(memoryFixtureSource(entries)));
    root = await render(host, workspaceShell());
  };

  beforeEach(() => {
    window.history.replaceState(null, "", "/");
    resetWorkspace();
    host = document.createElement("div");
    document.body.append(host);
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    root = undefined;
    restore?.();
    restore = undefined;
    host.remove();
    window.history.replaceState(null, "", "/");
  });

  it("侧栏森林入口卡在主导航与「最近学习」之间，点入 forest 视图", async () => {
    await mount();
    const entry = host.querySelector("[data-forest-entry]");
    expect(entry).not.toBeNull();
    expect(entry?.getAttribute("aria-label")).toBe(copyText("cd_drawer_forest"));
    expect(textOf(host, "[data-forest-entry-title]")).toBe(copyText("drawer_forest_title"));
    expect(entry?.textContent).toContain(copyText("drawer_forest_sub"));
    expect(entry?.previousElementSibling?.className).toContain("side-nav");
    expect(entry?.nextElementSibling?.className).toContain("side-heading");

    await press(host, "[data-forest-entry]");
    expect(workspaceSnapshot().view).toBe("forest");
    expect(host.querySelector('[data-slot="breadcrumb"]')?.textContent).toBe(copyText("forest_title"));
  });

  it("林地渲染页头、提示与每个主题一棵树，数字在标题行", async () => {
    await mount();
    await act(async () => {
      openView("forest");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    const screen = host.querySelector('[data-screen="forest"]');
    expect(screen).not.toBeNull();
    expect(textOf(screen as HTMLElement, '[data-node="forest_title"]')).toBe(copyText("forest_title"));
    expect(textOf(host, "[data-forest-subtitle]")).toBe(
      copyText("forest_subtitle") + COUNT_PREFIX
      + [copyFormat("forest_foot_tree", 2), copyFormat("forest_foot_segments", 3)].join(FOOT_SEPARATOR)
    );
    expect(textOf(host, "[data-forest-hint]")).toBe(copyText("forest_hint"));
    // 旧版右上角那枚「N」指北圆已撤：场地隐喻不需要方位，屏幕只留一条指令。
    expect(host.querySelector("[data-forest-compass]")).toBeNull();
    expect(host.querySelector("[data-forest-scene]")?.getAttribute("aria-label"))
      .toBe(copyFormat("cd_forest_scene", 2));
    const trees = Array.from(host.querySelectorAll("[data-forest-tree]"));
    expect(trees).toHaveLength(2);
    // 绘制序按地面远近（y 升序），所以按 data-forest-tree 认领，不按 DOM 顺序。
    expect(trees.map((tree) => Number(tree.getAttribute("data-forest-tree"))).sort()).toEqual([0, 1]);
    for (const [index, title] of TITLES.entries()) {
      expect(host.querySelector(`[data-forest-tree="${index}"]`)?.getAttribute("aria-label")).toBe(title);
      const radius = Number(host.querySelector(`[data-forest-tree="${index}"] .fs-hit`)?.getAttribute("r"));
      expect(radius).toBeGreaterThanOrEqual(22);
    }
    expect(host.querySelector("[data-forest-sheet]")).toBeNull();
  });

  it("点树出现选中标记与纸条：段枝数 + 当前人设，箭头进入该主题", async () => {
    await mount();
    await act(async () => {
      openView("forest");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await press(host, '[data-forest-tree="1"]');
    expect(workspaceSnapshot().view).toBe("forest");
    expect(host.querySelector('[data-forest-tree="1"]')?.getAttribute("data-selected")).toBe("true");
    expect(host.querySelector('[data-forest-tree="0"]')?.getAttribute("data-selected")).toBe("false");
    expect(textOf(host, "[data-forest-tag]")).toBe(TITLES[1]);
    expect(textOf(host, "[data-forest-sheet-title]")).toBe(TITLES[1]);
    expect(textOf(host, "[data-forest-sheet-meta]")).toBe(
      [copyFormat("forest_meta_branches", 2), copyText(personaNameKey("default"))].join(SEPARATOR)
    );
    expect(host.querySelector("[data-forest-go]")?.getAttribute("aria-label"))
      .toBe(copyFormat("cd_forest_open_topic", TITLES[1]));
    // 一棵树已选中时不再挂「点一棵树看看」：同一屏两句话互相否定。
    expect(host.querySelector("[data-forest-hint]")).toBeNull();

    await press(host, '[data-forest-tree="0"]');
    expect(textOf(host, "[data-forest-sheet-title]")).toBe(TITLES[0]);
    expect(textOf(host, "[data-forest-tag]")).toBe(TITLES[0]);
    expect(host.querySelector('[data-forest-tree="0"]')?.getAttribute("data-selected")).toBe("true");
    expect(host.querySelector('[data-forest-tree="1"]')?.getAttribute("data-selected")).toBe("false");

    await press(host, "[data-forest-go]");
    expect(workspaceSnapshot().view).toBe("chat");
    expect(workspaceSnapshot().topic).toBe(0);
  });

  it("纸条显示该主题当前人设档名", async () => {
    await mount();
    await act(async () => {
      setTopicPersona(0, "strict-mentor");
      openView("forest");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await press(host, '[data-forest-tree="0"]');
    expect(textOf(host, "[data-forest-sheet-meta]")).toBe(
      [copyFormat("forest_meta_branches", 1), copyText(personaNameKey("strict-mentor"))].join(SEPARATOR)
    );
  });

  it("标题行只写读得到的数字：树数恒有，段枝总数在缺失时省略", async () => {
    await mount();
    await act(async () => {
      openView("forest");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(textOf(host, "[data-forest-count]")).toBe(
      COUNT_PREFIX + [copyFormat("forest_foot_tree", 2), copyFormat("forest_foot_segments", 3)].join(FOOT_SEPARATOR)
    );
    expect(host.querySelector("[data-forest-foot]"), "页脚撤掉，数字不再在底端重复").toBeNull();
  });

  it("某主题段枝数读不到时，纸条省略段枝、标题行只留树数", async () => {
    await mount(variant((raw) => {
      const payload = raw.payload as Record<string, unknown>;
      payload.nodes = JSON.parse(JSON.stringify(payload.nodes).replaceAll("1 个分支 · 3天前", "3天前"));
    }));
    await act(async () => {
      openView("forest");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(host.querySelectorAll("[data-forest-tree]")).toHaveLength(2);
    expect(textOf(host, "[data-forest-count]")).toBe(COUNT_PREFIX + copyFormat("forest_foot_tree", 2));
    await press(host, '[data-forest-tree="0"]');
    expect(textOf(host, "[data-forest-sheet-meta]")).toBe(copyText(personaNameKey("default")));
  });

  it("没有主题时是空态，按钮回首页", async () => {
    await mount(variant((raw) => {
      (raw.payload as Record<string, unknown>).nodes = [];
    }));
    await act(async () => {
      openView("forest");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    const empty = host.querySelector("[data-forest-empty]");
    expect(empty).not.toBeNull();
    expect(empty?.textContent).toContain(copyText("forest_empty_title"));
    expect(empty?.textContent).toContain(copyText("forest_empty_message"));
    expect(textOf(host, "[data-forest-empty-action]")).toBe(copyText("forest_empty_action"));
    expect(host.querySelector("[data-forest-scene]")).toBeNull();
    await press(host, "[data-forest-empty-action]");
    expect(workspaceSnapshot().view).toBe("home");
  });

  it("学习库读不到时是错误态：只有说明，不画不生效的重试按钮", async () => {
    await mount(withoutLibrary());
    await act(async () => {
      openView("forest");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    const error = host.querySelector("[data-forest-error]");
    expect(error).not.toBeNull();
    expect(error?.textContent).toContain(copyText("forest_error_title"));
    expect(error?.textContent).toContain(copyText("forest_error_message"));
    expect(error?.querySelector("button")).toBeNull();
    expect(host.querySelector("[data-forest-empty]")).toBeNull();
  });

  it("森林只读：进出森林不改视图栈以外的状态", async () => {
    await mount();
    await act(async () => {
      openView("forest");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    const before = workspaceSnapshot();
    await press(host, '[data-forest-tree="0"]');
    const after = workspaceSnapshot();
    expect(after.drafts).toEqual(before.drafts);
    expect(after.personas).toEqual(before.personas);
    expect(after.libraryQuery).toBe(before.libraryQuery);
    expect(after.view).toBe("forest");
  });
});
