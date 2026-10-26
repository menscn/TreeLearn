// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { copyText } from "../../src/copy";
import { WorkspaceDataProvider } from "../../src/components/WorkspaceDataProvider";
import { AppShell } from "../../src/layouts/AppShell";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import { resetWorkspace } from "../../src/store/workspace";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

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

describe("工作区外壳：侧栏、顶部栏与内容槽", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    window.history.replaceState(null, "", "/");
    resetWorkspace();
    restore = injectRuntime(new FixtureRuntimeAdapter(memoryFixtureSource(bundledEntries())));
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
    document.title = "";
  });

  function shell() {
    return createElement(
      WorkspaceDataProvider,
      null,
      createElement(AppShell, null, createElement("div", { "data-testid": "probe" }, "内容槽"))
    );
  }

  it("标题仍来自文案表；侧栏名称、最近学习与主内容槽都在", async () => {
    root = await render(host, shell());
    expect(document.title).toBe(copyText("app_name"));
    expect(host.querySelector(".app-sidebar")?.getAttribute("aria-label")).toBe(copyText("nav_primary"));
    expect(Array.from(host.querySelectorAll("[data-nav-view]")).map((el) => el.textContent)).toEqual([
      copyText("tab_home"),
      copyText("tab_library"),
      copyText("nav_settings")
    ]);
    expect(host.querySelector(".side-heading")?.textContent).toBe(copyText("nav_recent"));
    const recent = Array.from(host.querySelectorAll("[data-recent-topic]"));
    expect(recent).toHaveLength(2);
    expect(recent[0]?.textContent).toContain("注意力权重可视化");
    expect(host.querySelector("main[data-slot='screen'] [data-testid='probe']")).not.toBeNull();
    expect(host.querySelector(".app-tabs")).toBeNull();
    expect(host.querySelector(".app-tab")).toBeNull();
    expect(host.querySelector(".crumb-current")?.textContent).toBe(copyText("tab_home"));
  });

  /**
   * T-2026-10-25-097（评审 WH3）：历史行的标题、meta 与箭头收进同一行；箭头是「回到哪儿」的
   * 落点，不再另占一行。摘要由 CSS 判据钉住（recent-row.test.ts），这里只判结构。
   */
  it("历史行把标题、meta 与箭头收进同一行", async () => {
    root = await render(host, shell());
    const row = host.querySelector('[data-recent-topic="0"]');
    expect(row).not.toBeNull();
    const head = row!.querySelector(".recent-head");
    expect(head, "标题行是 recent-head").not.toBeNull();
    expect(head!.querySelector(".recent-title")?.textContent).toBeTruthy();
    expect(head!.querySelector(".recent-meta")?.textContent).toBeTruthy();
    expect(head!.querySelector(".recent-chevron"), "箭头在标题行内，不再另占一行").not.toBeNull();
    expect(row!.querySelector(":scope > .recent-head"), "标题行是历史行的直接子节点").not.toBeNull();
  });

  it("折叠按钮的可访问名与 aria-expanded 跟着状态走", async () => {
    root = await render(host, shell());
    const shellEl = host.querySelector(".app-workspace");
    const toggle = host.querySelector('[data-action="toggle-nav"]');
    expect(shellEl?.getAttribute("data-nav")).toBe("expanded");
    expect(toggle?.getAttribute("aria-expanded")).toBe("true");
    expect(toggle?.getAttribute("aria-label")).toBe(copyText("nav_collapse"));
    expect(toggle?.getAttribute("title")).toBe(copyText("nav_collapse"));

    await press(host, '[data-action="toggle-nav"]');
    expect(shellEl?.getAttribute("data-nav")).toBe("collapsed");
    expect(toggle?.getAttribute("aria-expanded")).toBe("false");
    expect(toggle?.getAttribute("aria-label")).toBe(copyText("nav_expand"));
    expect(toggle?.getAttribute("title")).toBe(copyText("nav_expand"));
  });

  it("侧栏进入学习库后面包屑跟随，返回按钮把视图退回去", async () => {
    root = await render(host, shell());
    await press(host, '[data-nav-view="library"]');
    expect(host.querySelector(".crumb-current")?.textContent).toBe(copyText("tab_library"));
    expect(host.querySelector('[data-action="workspace-back"]')).not.toBeNull();

    await press(host, '[data-action="workspace-back"]');
    expect(host.querySelector(".crumb-current")?.textContent).toBe(copyText("tab_home"));
    expect(host.querySelector('[data-action="workspace-back"]')).toBeNull();
  });

  it("最近学习点进主题，面包屑用真实主题标题", async () => {
    root = await render(host, shell());
    await press(host, '[data-recent-topic="0"]');
    expect(host.querySelector(".crumb-current")?.textContent).toBe("注意力权重可视化与模型可解释性的关系");
  });

  it("窄窗侧栏初始收起，遮罩可把展开的侧栏收起", async () => {
    const originalWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 390 });
    try {
      root = await render(host, shell());
      const shellEl = host.querySelector(".app-workspace");
      expect(shellEl?.getAttribute("data-nav")).toBe("collapsed");
      expect(host.querySelector(".nav-scrim")).toBeNull();

      await press(host, '[data-action="toggle-nav"]');
      expect(shellEl?.getAttribute("data-nav")).toBe("expanded");
      expect(host.querySelector(".nav-scrim")).not.toBeNull();

      await press(host, ".nav-scrim");
      expect(shellEl?.getAttribute("data-nav")).toBe("collapsed");
      expect(host.querySelector(".nav-scrim")).toBeNull();
    } finally {
      Object.defineProperty(window, "innerWidth", { configurable: true, value: originalWidth });
    }
  });

  it("窗口缩到窄窗时侧栏自动收起", async () => {
    root = await render(host, shell());
    const shellEl = host.querySelector(".app-workspace");
    expect(shellEl?.getAttribute("data-nav")).toBe("expanded");

    const originalWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 390 });
    try {
      await act(async () => {
        window.dispatchEvent(new Event("resize"));
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
      expect(shellEl?.getAttribute("data-nav")).toBe("collapsed");
    } finally {
      Object.defineProperty(window, "innerWidth", { configurable: true, value: originalWidth });
    }
  });

  it("所有图标按钮都有可访问名与悬停提示", async () => {
    root = await render(host, shell());
    const buttons = Array.from(host.querySelectorAll("button.icon-btn"));
    expect(buttons.length).toBeGreaterThan(0);
    for (const button of buttons) {
      expect(button.getAttribute("aria-label"), button.outerHTML).toBeTruthy();
      expect(button.getAttribute("title"), button.outerHTML).toBeTruthy();
    }
  });
});
