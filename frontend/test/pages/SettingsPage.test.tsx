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
  setSettingsCategory,
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

async function scrollTo(host: HTMLElement, top: number): Promise<void> {
  const el = host.querySelector('[data-slot="settings-scroll"]') as HTMLDivElement | null;
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

function shell(): ReactNode {
  return createElement(
    WorkspaceDataProvider,
    null,
    createElement(AppShell, null, createElement(WorkspaceRouter))
  );
}

function detail(host: HTMLElement): HTMLElement | null {
  return host.querySelector("[data-settings-detail]");
}

function categories(host: HTMLElement): string[] {
  return Array.from(host.querySelectorAll("[data-settings-category]"))
    .map((el) => el.getAttribute("data-settings-category") ?? "");
}

function field(host: HTMLElement, label: string): HTMLElement | null {
  return host.querySelector(`[data-node="settings_field"][data-label="${label}"]`);
}

describe("工作区设置：分类＋详情、只读与恢复", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    window.history.replaceState(null, "", "/");
    resetWorkspace();
    openView("settings");
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

  it("六类真实分类齐备，默认停在 API KEY，详情显示掩码与只读说明", async () => {
    root = await render(host, shell());
    expect(host.querySelector('[data-screen="settings"]')).not.toBeNull();
    expect(categories(host)).toEqual(["api-key", "provider", "model", "search", "voice", "files"]);
    expect(host.querySelector('[data-settings-category="api-key"]')?.getAttribute("aria-current")).toBe("page");
    expect(host.querySelector('[data-settings-category="api-key"]')?.textContent)
      .toContain("sk-••••••••••f9b8 · 可用");
    expect(detail(host)?.getAttribute("data-settings-detail")).toBe("api-key");
    expect(host.textContent).toContain("API KEY · DeepSeek");
    expect(field(host, "sk-••••••••••f9b8")?.textContent).toContain("可用");
    expect(host.querySelector('[data-node="settings_readonly"]')?.textContent).toContain("只读");
    expect(host.textContent).toContain("仅存本机，可随时替换或删除。");
    expect(host.querySelector('[data-slot="settings-missing"]')).toBeNull();
  });

  it("提供商详情只放真实字段：DeepSeek、脱敏地址、1 个模型与开启状态", async () => {
    root = await render(host, shell());
    await press(host, '[data-settings-category="provider"]');
    expect(workspaceSnapshot().settingsCategory).toBe("provider");
    expect(detail(host)?.getAttribute("data-settings-detail")).toBe("provider");
    expect(host.textContent).toContain("DeepSeek");
    expect(host.textContent).toContain("地址已在包内脱敏");
    expect(field(host, "1 个模型")).not.toBeNull();
    const badge = host.querySelector('[data-control="switch"]');
    expect(badge?.getAttribute("data-state")).toBe("on");
    expect(badge?.textContent).toContain(copyText("settings_switch_on"));
    expect(host.querySelector('[data-settings-action="新建"]')?.hasAttribute("disabled")).toBe(true);
  });

  it("模型、搜索、语音与文件详情来自各自夹具", async () => {
    root = await render(host, shell());

    await press(host, '[data-settings-category="model"]');
    for (const label of ["聊天模型", "快速模型", "翻译模型"]) {
      expect(field(host, label)?.textContent).toContain("deepseek-flash");
    }
    expect(field(host, "思考预算")?.textContent).toContain("自动");
    expect(host.querySelector('[data-control="switch"]')?.getAttribute("data-state")).toBe("unknown");
    expect(host.querySelector('[data-control="switch"]')?.textContent).toContain(copyText("settings_switch_unknown"));
    expect(host.textContent).toContain("回答下方生成推荐问题");

    await press(host, '[data-settings-category="search"]');
    expect(host.textContent).toContain("联网搜索");
    expect(host.querySelector('[data-control="switch"]')?.getAttribute("data-state")).toBe("off");
    expect(host.querySelector('[data-control="switch"]')?.textContent).toContain(copyText("settings_switch_off"));
    for (const engine of ["智谱", "Tavily", "博查"]) {
      expect(field(host, engine)).not.toBeNull();
    }
    expect(field(host, "智谱")?.textContent).toContain("未配置 Key");
    expect(field(host, "智谱")?.querySelector("svg")).not.toBeNull();
    expect(host.querySelector('[data-settings-group="检索结果数"]')?.textContent)
      .toContain("每次检索 10 条");
    expect(host.querySelector('[data-settings-action="保存 KEY"]')?.hasAttribute("disabled")).toBe(true);

    await press(host, '[data-settings-category="voice"]');
    expect(field(host, "系统 TTS")?.textContent).toContain("本机默认语音引擎 · 默认");
    expect(host.querySelector('[data-settings-action="试听"]')?.hasAttribute("disabled")).toBe(true);
    expect(host.querySelector('[data-settings-action="试听"] svg')).not.toBeNull();

    await press(host, '[data-settings-category="files"]');
    expect(field(host, "已占用 0 B")).not.toBeNull();
    expect(host.textContent).toContain("还没有附件文件");
  });

  it("所有写入入口禁用并说明尚未接通；开关只是状态徽标，不是可操作控件", async () => {
    root = await render(host, shell());
    for (const id of ["api-key", "provider", "model", "search", "voice", "files"]) {
      await press(host, `[data-settings-category="${id}"]`);
      const actions = Array.from(host.querySelectorAll("[data-settings-action]"));
      for (const action of actions) {
        expect(action.hasAttribute("disabled"), `${id}:${action.getAttribute("data-settings-action")}`).toBe(true);
        expect(action.getAttribute("aria-label")).toContain(copyText("chat_action_unavailable"));
        expect(action.getAttribute("title")).toContain(copyText("chat_action_unavailable"));
      }
      for (const input of Array.from(host.querySelectorAll("input.field"))) {
        expect(input.hasAttribute("disabled"), `${id}:input`).toBe(true);
        expect(input.getAttribute("aria-label")).toContain(copyText("chat_action_unavailable"));
        expect(input.getAttribute("title")).toContain(copyText("chat_action_unavailable"));
      }
      const badge = host.querySelector('[data-control="switch"]');
      if (badge) {
        expect(badge.getAttribute("role")).not.toBe("switch");
        expect(badge.getAttribute("aria-label")).toContain(copyText("chat_action_unavailable"));
        expect(badge.getAttribute("title")).toContain(copyText("chat_action_unavailable"));
      }
    }
    expect(host.querySelector("form")).toBeNull();
    expect(host.querySelector('[role="switch"]')).toBeNull();
    expect(host.textContent).not.toContain("保存成功");
  });

  it("离开再返回恢复分类与滚动位置；窄窗不清空；未知分类回落第一类", async () => {
    root = await render(host, shell());
    await press(host, '[data-settings-category="search"]');
    await scrollTo(host, 140);
    expect(workspaceSnapshot().settingsCategory).toBe("search");

    await press(host, '[data-nav-view="home"]');
    expect(host.querySelector('[data-screen="settings"]')).toBeNull();
    await press(host, '[data-nav-view="settings"]');
    expect(detail(host)?.getAttribute("data-settings-detail")).toBe("search");
    expect((host.querySelector('[data-slot="settings-scroll"]') as HTMLDivElement).scrollTop).toBe(140);

    const originalWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 390 });
    try {
      await act(async () => {
        window.dispatchEvent(new Event("resize"));
        await new Promise((resolve) => setTimeout(resolve, 0));
      });
      expect(workspaceSnapshot().settingsCategory).toBe("search");
      expect(detail(host)?.getAttribute("data-settings-detail")).toBe("search");
      expect((host.querySelector('[data-slot="settings-scroll"]') as HTMLDivElement).scrollTop).toBe(140);
    } finally {
      Object.defineProperty(window, "innerWidth", { configurable: true, value: originalWidth });
    }

    await act(async () => {
      setSettingsCategory("not-a-category");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(detail(host)?.getAttribute("data-settings-detail")).toBe("api-key");
    expect(host.querySelector('[data-settings-category="api-key"]')?.getAttribute("aria-current")).toBe("page");
  });

  it("缺失某类夹具时该分类提示采集缺失，分类仍可切换", async () => {
    const entries = { ...bundledEntries() };
    delete entries.provideradmin;
    restore?.();
    restore = injectRuntime(source(entries));

    root = await render(host, shell());
    expect(categories(host)).toHaveLength(6);
    await press(host, '[data-settings-category="provider"]');
    expect(host.querySelector('[data-slot="settings-missing"]')?.textContent)
      .toContain(copyText("state_fixture_unavailable"));
    await press(host, '[data-settings-category="voice"]');
    expect(host.querySelector('[data-slot="settings-missing"]')).toBeNull();
    expect(field(host, "系统 TTS")).not.toBeNull();
  });

  it("主设置夹具缺失时显示采集缺失，而不是六个空分类", async () => {
    const entries = { ...bundledEntries() };
    delete entries.settings;
    restore?.();
    restore = injectRuntime(source(entries));

    root = await render(host, shell());
    expect(host.querySelector('[data-screen="settings"]')).not.toBeNull();
    expect(host.textContent).toContain(copyText("state_fixture_unavailable"));
    expect(host.querySelectorAll("[data-settings-category]")).toHaveLength(0);
  });
});
