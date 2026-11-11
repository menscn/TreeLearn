// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { WorkspaceDataProvider } from "../../src/components/WorkspaceDataProvider";
import { copyText } from "../../src/copy";
import { AppShell } from "../../src/layouts/AppShell";
import { WorkspaceRouter } from "../../src/pages/WorkspaceRouter";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import {
  openView,
  resetWorkspace,
  scrollFor,
  workspaceSnapshot
} from "../../src/store/workspace";

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

async function typeInto(host: HTMLElement, selector: string, value: string): Promise<void> {
  const el = host.querySelector(selector);
  expect(el, selector).not.toBeNull();
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
  await act(async () => {
    setter?.call(el, value);
    el!.dispatchEvent(new Event("input", { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function scrollTo(host: HTMLElement, top: number): Promise<void> {
  const el = host.querySelector(".page-scroll") as HTMLDivElement | null;
  expect(el).not.toBeNull();
  await act(async () => {
    el!.scrollTop = top;
    el!.dispatchEvent(new Event("scroll", { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function source(entries: Record<string, string> = bundledEntries()) {
  return new FixtureRuntimeAdapter(memoryFixtureSource(entries));
}

function workspaceShell(): ReactNode {
  return createElement(
    WorkspaceDataProvider,
    null,
    createElement(AppShell, null, createElement(WorkspaceRouter))
  );
}

function rows(host: HTMLElement): string[] {
  return Array.from(host.querySelectorAll('[data-slot="library-rows"] [data-node="library_topic_title"]'))
    .map((el) => el.textContent ?? "");
}

describe("工作区学习库：搜索、筛选、摘要与恢复", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    window.history.replaceState(null, "", "/");
    resetWorkspace();
    openView("library");
    restore = injectRuntime(source());
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

  it("两个主题、真实摘要与 meta 都在；摘要默认收起，可展开且 aria 指向摘要", async () => {
    root = await render(host, workspaceShell());
    expect(host.querySelector('[data-screen="library"]')).not.toBeNull();
    expect(rows(host)).toEqual([
      "注意力权重可视化与模型可解释性的关系",
      "系统理解 Transformer 的注意力机制"
    ]);
    const metas = Array.from(host.querySelectorAll('[data-node="library_topic_meta"]')).map((el) => el.textContent);
    expect(metas).toEqual(["1 个分支 · 3天前", "2 个分支 · 3天前"]);

    const excerpt = host.querySelector("#library-summary-topic-0") as HTMLElement | null;
    const toggle = host.querySelector('[data-summary-toggle="topic-0"]');
    expect(excerpt).not.toBeNull();
    expect(excerpt?.textContent).toContain("注意力头消融");
    expect(excerpt?.getAttribute("data-expanded")).toBe("false");
    expect(toggle?.getAttribute("aria-expanded")).toBe("false");
    expect(toggle?.getAttribute("aria-controls")).toBe("library-summary-topic-0");
    expect(toggle?.textContent).toContain(copyText("library_expand"));

    await press(host, '[data-summary-toggle="topic-0"]');
    expect(toggle?.getAttribute("aria-expanded")).toBe("true");
    expect(toggle?.textContent).toContain(copyText("library_collapse"));
    expect(excerpt?.getAttribute("data-expanded")).toBe("true");
    expect(workspaceSnapshot().libraryExpanded).toEqual(["topic-0"]);

    await press(host, '[data-summary-toggle="topic-0"]');
    expect(toggle?.getAttribute("aria-expanded")).toBe("false");
    expect(workspaceSnapshot().libraryExpanded).toEqual([]);
  });

  it("搜索命中标题、摘要与推荐问题，大小写不敏感；无命中出空态并可清空", async () => {
    root = await render(host, workspaceShell());
    await typeInto(host, "#library-search", "Transformer");
    expect(rows(host)).toEqual(["系统理解 Transformer 的注意力机制"]);

    await typeInto(host, "#library-search", "消融");
    expect(rows(host)).toEqual(["注意力权重可视化与模型可解释性的关系"]);

    await typeInto(host, "#library-search", "关键头");
    expect(rows(host)).toEqual(["注意力权重可视化与模型可解释性的关系"]);

    // This phrase occurs in a follow-up question in the bundled conversation,
    // but not in the library's titles, summaries or home recommendations.
    await typeInto(host, "#library-search", "分布偏移程度不同");
    expect(rows(host)).toEqual([]);

    await typeInto(host, "#library-search", "transformer");
    expect(rows(host)).toEqual(["系统理解 Transformer 的注意力机制"]);

    await typeInto(host, "#library-search", "完全不存在的问题");
    expect(rows(host)).toEqual([]);
    const empty = host.querySelector('[data-empty="search"]');
    expect(empty?.textContent).toContain(copyText("library_empty_search"));
    const clear = host.querySelector('[data-action="library-empty-clear"]');
    expect(clear?.getAttribute("aria-label")).toBe(copyText("library_clear_search"));
    await press(host, '[data-action="library-empty-clear"]');
    expect((host.querySelector("#library-search") as HTMLInputElement | null)?.value).toBe("");
    expect(rows(host)).toHaveLength(2);
  });

  it("状态分段按真实计数工作：进行中 2、已完成 0，已完成不伪造条目", async () => {
    root = await render(host, workspaceShell());
    const segment = (filter: string) => host.querySelector(`[data-filter="${filter}"]`);
    expect(segment("all")?.textContent).toBe(`${copyText("library_filter_all")} 2`);
    expect(segment("in_progress")?.textContent).toBe(`${copyText("library_filter_in_progress")} 2`);
    expect(segment("done")?.textContent).toBe(`${copyText("library_filter_done")} 0`);
    expect(segment("all")?.getAttribute("aria-pressed")).toBe("true");

    await press(host, '[data-filter="done"]');
    expect(rows(host)).toEqual([]);
    const empty = host.querySelector('[data-empty="filter"]');
    expect(empty?.textContent).toContain(copyText("library_empty_done"));
    expect(workspaceSnapshot().libraryFilter).toBe("done");

    await press(host, '[data-filter="in_progress"]');
    expect(rows(host)).toHaveLength(2);
    expect(segment("in_progress")?.getAttribute("aria-pressed")).toBe("true");

    await typeInto(host, "#library-search", "Transformer");
    await press(host, '[data-filter="done"]');
    expect((host.querySelector("#library-search") as HTMLInputElement | null)?.value).toBe("Transformer");
    expect(workspaceSnapshot().libraryQuery).toBe("Transformer");
    expect(rows(host)).toEqual([]);
    expect(host.querySelector('[data-empty="search"]')).not.toBeNull();
  });

  it("往返后恢复查询、筛选、展开与滚动位置", async () => {
    root = await render(host, workspaceShell());
    await typeInto(host, "#library-search", "消融");
    await press(host, '[data-summary-toggle="topic-0"]');
    await press(host, '[data-filter="in_progress"]');
    await scrollTo(host, 180);
    expect(scrollFor("library")).toBe(180);

    await press(host, '[data-nav-view="home"]');
    expect(host.querySelector('[data-screen="library"]')).toBeNull();
    await press(host, '[data-nav-view="library"]');

    expect(host.querySelector('[data-screen="library"]')).not.toBeNull();
    expect((host.querySelector("#library-search") as HTMLInputElement | null)?.value).toBe("消融");
    expect(host.querySelector('[data-filter="in_progress"]')?.getAttribute("aria-pressed")).toBe("true");
    expect(host.querySelector('[data-summary-toggle="topic-0"]')?.getAttribute("aria-expanded")).toBe("true");
    expect(host.querySelector("#library-summary-topic-0")?.getAttribute("data-expanded")).toBe("true");
    expect((host.querySelector(".page-scroll") as HTMLDivElement | null)?.scrollTop).toBe(180);
  });

  it("窗口缩到窄窗后，查询、筛选、展开与滚动位置都不被清空", async () => {
    root = await render(host, workspaceShell());
    await typeInto(host, "#library-search", "注意力");
    await press(host, '[data-filter="in_progress"]');
    await press(host, '[data-summary-toggle="topic-1"]');
    await scrollTo(host, 90);

    const originalWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 390 });
    try {
      await act(async () => {
        window.dispatchEvent(new Event("resize"));
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
      expect((host.querySelector("#library-search") as HTMLInputElement | null)?.value).toBe("注意力");
      expect(workspaceSnapshot().libraryFilter).toBe("in_progress");
      expect(workspaceSnapshot().libraryExpanded).toEqual(["topic-1"]);
      expect(scrollFor("library")).toBe(90);
      expect(host.querySelector('[data-summary-toggle="topic-1"]')?.getAttribute("aria-expanded")).toBe("true");
    } finally {
      Object.defineProperty(window, "innerWidth", { configurable: true, value: originalWidth });
    }
  });

  it("搜索图标、清空按钮与摘要按钮都有字形和可访问名", async () => {
    root = await render(host, workspaceShell());
    expect(host.querySelector(".search-field svg")).not.toBeNull();
    await typeInto(host, "#library-search", "注");
    const clear = host.querySelector('[data-action="library-clear-search"]');
    expect(clear?.getAttribute("class")).toContain("icon-btn");
    expect(clear?.querySelector("svg")).not.toBeNull();
    expect(clear?.getAttribute("aria-label")).toBe(copyText("library_clear_search"));
    expect(clear?.getAttribute("title")).toBe(copyText("library_clear_search"));
    const toggle = host.querySelector('[data-summary-toggle="topic-0"]');
    expect(toggle?.querySelector("svg")).not.toBeNull();
    expect(toggle?.getAttribute("title")).toContain(copyText("library_expand"));
    expect(toggle?.getAttribute("aria-label")).toContain(copyText("library_expand"));
  });

  it("搜索提示与当前可匹配的内容一致", async () => {
    root = await render(host, workspaceShell());
    const input = host.querySelector<HTMLInputElement>("#library-search");
    expect(input).not.toBeNull();
    expect(input!.placeholder).toBe(copyText("library_search_placeholder"));
    expect(input!.placeholder).toContain("主题");
    expect(input!.placeholder).toContain("摘要");
    expect(input!.placeholder).toContain("推荐问题");
    expect(input!.placeholder).not.toContain("回答");
    expect(input!.placeholder).not.toContain("原文");
  });

  it("library 夹具缺失时显示采集缺失文案，而不是空列表", async () => {
    const entries = { ...bundledEntries() };
    delete entries.library;
    restore?.();
    restore = injectRuntime(source(entries));

    root = await render(host, workspaceShell());
    expect(host.querySelector('[data-screen="library"]')).not.toBeNull();
    expect(host.textContent).toContain(copyText("state_fixture_unavailable"));
    expect(host.querySelector('[data-slot="library-rows"]')).toBeNull();
  });
});
