// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WorkspaceDataProvider } from "../../src/components/WorkspaceDataProvider";
import { copyText } from "../../src/copy";
import { zhCN } from "../../src/copy/zh-CN";
import { AppShell } from "../../src/layouts/AppShell";
import { WorkspaceRouter } from "../../src/pages/WorkspaceRouter";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import {
  draftFor,
  resetWorkspace,
  setDraft,
  workspaceSnapshot
} from "../../src/store/workspace";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const FIRST_QUESTION = "注意力头消融实验中如何区分关键头与补偿性替代头";

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
  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")?.set;
  await act(async () => {
    setter?.call(el, value);
    el!.dispatchEvent(new Event("input", { bubbles: true }));
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

describe("工作区主页：真实推荐、主题与草稿", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    window.history.replaceState(null, "", "/");
    resetWorkspace();
    restore = injectRuntime(source());
    host = document.createElement("div");
    document.body.append(host);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    act(() => {
      root?.unmount();
    });
    root = undefined;
    restore?.();
    restore = undefined;
    host.remove();
    window.history.replaceState(null, "", "/");
  });

  it("推荐、历史主题、模型与草稿占位都来自 home 夹具，侧栏最近学习也是真主题", async () => {
    root = await render(host, workspaceShell());
    expect(host.querySelector('[data-screen="home"]')).not.toBeNull();

    const input = host.querySelector("#home-composer-input") as HTMLTextAreaElement | null;
    expect(input).not.toBeNull();
    expect(input?.placeholder).toBe("输入你的根问题，例如：帮我系统理解 Transformer 的注意力机制");
    expect(host.querySelector('[data-node="home_model_chip"]')?.textContent).toContain("V4.1 Flash");

    expect(host.querySelector('[data-node="home_recommendations_title"]')?.textContent).toBe("猜你想学");
    const suggestions = Array.from(host.querySelectorAll("[data-recommendation]"));
    expect(suggestions).toHaveLength(2);
    expect(suggestions[0]?.textContent).toContain(FIRST_QUESTION);
    expect(suggestions[1]?.textContent).toContain("注意力权重与因果贡献不等同的实证检验方法");
    expect(host.querySelector('[data-node="rec_tag"]')?.textContent).toBe("顺着你学的");

    expect(host.querySelector('[data-node="home_recent_title"]')?.textContent).toContain("历史主题");
    expect(host.querySelector('[data-node="home_recent_count"]')?.textContent).toBe("2 个");
    const titles = Array.from(host.querySelectorAll('[data-node="home_topic_title"]')).map((el) => el.textContent);
    expect(titles).toEqual([
      "注意力权重可视化与模型可解释性的关系",
      "系统理解 Transformer 的注意力机制"
    ]);
    const metas = Array.from(host.querySelectorAll('[data-node="home_topic_meta"]')).map((el) => el.textContent);
    expect(metas).toEqual(["1 个分支 · 3天前", "2 个分支 · 3天前"]);

    const recent = Array.from(host.querySelectorAll("[data-recent-topic]"));
    expect(recent).toHaveLength(2);
    expect(recent[0]?.textContent).toContain("注意力权重可视化与模型可解释性的关系");
  });

  it("点推荐进入主题 0，并把这条问题写进对话草稿", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    root = await render(host, workspaceShell());
    await press(host, '[data-recommendation="0"]');

    expect(workspaceSnapshot().view).toBe("chat");
    expect(workspaceSnapshot().topic).toBe(0);
    expect(draftFor("chat:0")).toBe(FIRST_QUESTION);
    const chatInput = host.querySelector("#chat-composer-input") as HTMLTextAreaElement | null;
    expect(chatInput?.value).toBe(FIRST_QUESTION);
    expect(confirm).not.toHaveBeenCalled();
  });

  it("覆盖提示按当前文案表即时解析，而不保存到草稿", async () => {
    const resources = zhCN as unknown as Record<string, string>;
    const original = zhCN.draft_replace_confirm;
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    try {
      setDraft("chat:0", "保留的草稿");
      root = await render(host, workspaceShell());
      resources.draft_replace_confirm = "测试时切换的提示";
      await press(host, '[data-recommendation="0"]');
      expect(confirm).toHaveBeenCalledWith("测试时切换的提示");
      expect(draftFor("chat:0")).toBe("保留的草稿");
    } finally {
      resources.draft_replace_confirm = original;
    }
  });

  it("首页推荐保护目标主题已有草稿：取消留在首页，确认才替换并跳转", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    setDraft("chat:0", "目标主题原草稿");
    setDraft("chat:1", "另一个主题草稿");
    root = await render(host, workspaceShell());
    await typeInto(host, "#home-composer-input", "首页草稿");

    await press(host, '[data-recommendation="0"]');
    expect(workspaceSnapshot().view).toBe("home");
    expect(draftFor("chat:0")).toBe("目标主题原草稿");
    expect(confirm).toHaveBeenCalledWith(copyText("draft_replace_confirm"));
    expect(draftFor("home")).toBe("首页草稿");

    await press(host, '[data-recommendation="0"]');
    expect(workspaceSnapshot().view).toBe("chat");
    expect(workspaceSnapshot().topic).toBe(0);
    expect(draftFor("chat:0")).toBe(FIRST_QUESTION);
    expect(draftFor("chat:1")).toBe("另一个主题草稿");
  });

  it("点历史主题进入对应主题；主页草稿在往返与窗口变化后都还在", async () => {
    root = await render(host, workspaceShell());
    await typeInto(host, "#home-composer-input", "根问题草稿：中文输入法");
    expect(draftFor("home")).toBe("根问题草稿：中文输入法");

    await act(async () => {
      window.dispatchEvent(new Event("resize"));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect((host.querySelector("#home-composer-input") as HTMLTextAreaElement | null)?.value)
      .toBe("根问题草稿：中文输入法");

    await press(host, '[data-topic="1"]');
    expect(workspaceSnapshot().view).toBe("chat");
    expect(workspaceSnapshot().topic).toBe(1);

    await press(host, '[data-nav-view="home"]');
    expect((host.querySelector("#home-composer-input") as HTMLTextAreaElement | null)?.value)
      .toBe("根问题草稿：中文输入法");
    expect(draftFor("home")).toBe("根问题草稿：中文输入法");
  });

  it("home 夹具缺失时不编推荐，学习库里的真主题仍可进入", async () => {
    const entries = { ...bundledEntries() };
    delete entries.home;
    restore?.();
    restore = injectRuntime(source(entries));

    root = await render(host, workspaceShell());
    expect(host.querySelector('[data-screen="home"]')).not.toBeNull();
    expect(host.querySelectorAll("[data-recommendation]")).toHaveLength(0);
    expect(host.querySelector('[data-node="home_recommendations_title"]')).toBeNull();
    expect(host.querySelectorAll('[data-node="home_topic_card"]')).toHaveLength(2);

    await press(host, '[data-topic="0"]');
    expect(workspaceSnapshot().view).toBe("chat");
  });

  it("所有夹具都读不到时出工作区错误文案，而不是一页空白", async () => {
    restore?.();
    restore = injectRuntime(source({}));
    root = await render(host, workspaceShell());
    expect(host.querySelector('[data-screen="home"]')).toBeNull();
    expect(host.textContent).toContain(copyText("workspace_error"));
  });
});
