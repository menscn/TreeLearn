// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WorkspaceDataProvider } from "../../src/components/WorkspaceDataProvider";
import { copyText } from "../../src/copy";
import { AppShell } from "../../src/layouts/AppShell";
import { WorkspaceRouter } from "../../src/pages/WorkspaceRouter";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import {
  draftFor,
  openTopic,
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
  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")?.set;
  await act(async () => {
    setter?.call(el, value);
    el!.dispatchEvent(new Event("input", { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function escape(): Promise<void> {
  await act(async () => {
    document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe("工作区对话页：阅读、复制、面板与知识树", () => {
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
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: undefined });
    window.history.replaceState(null, "", "/");
  });

  function chatShell() {
    return createElement(
      WorkspaceDataProvider,
      null,
      createElement(AppShell, null, createElement(WorkspaceRouter))
    );
  }

  async function openChat(topic = 0): Promise<void> {
    openTopic(topic, 2);
    root = await render(host, chatShell());
  }

  it("主题 0 的标题、模型、时间、回答结构与三条建议都来自夹具", async () => {
    await openChat();
    expect(host.querySelector('[data-screen="chat"]')).not.toBeNull();
    expect(host.querySelector('[data-node="chat_title"]')?.textContent).toContain("注意力");
    expect(host.textContent).toContain("V4.1 Flash");
    expect(host.textContent).toContain("3天前");

    const answer = host.querySelector(".chat-answer");
    expect(answer).not.toBeNull();
    expect(answer?.textContent).toContain("核心先说结论");
    expect(answer?.textContent).toContain("接管了。");
    expect(answer?.querySelectorAll("h2").length).toBe(3);
    expect(answer?.querySelectorAll("h3").length).toBeGreaterThanOrEqual(10);
    expect(answer?.querySelectorAll("ul").length).toBeGreaterThanOrEqual(4);

    const suggestions = host.querySelectorAll("[data-suggestion]");
    expect(suggestions).toHaveLength(3);
    expect(suggestions[0]?.textContent).toContain("zero ablation");

    const scroll = host.querySelector(".chat-scroll");
    const dock = host.querySelector(".chat-dock");
    expect(scroll).not.toBeNull();
    expect(dock).not.toBeNull();
    expect(scroll?.nextElementSibling).toBe(dock);
  });

  it("点建议把问题填进草稿并聚焦输入框，草稿在换视图后仍读得回来", async () => {
    await openChat();
    const first = host.querySelector('[data-suggestion="0"]');
    const question = first?.textContent ?? "";
    expect(question.length).toBeGreaterThan(0);

    await press(host, '[data-suggestion="0"]');
    const input = host.querySelector("#chat-composer-input") as HTMLTextAreaElement | null;
    expect(input).not.toBeNull();
    expect(input?.value).toBe(question);
    expect(document.activeElement).toBe(input);
    expect(draftFor("chat:0")).toBe(question);

    await typeInto(host, "#chat-composer-input", "我想继续追问补偿性替代头");
    expect(draftFor("chat:0")).toBe("我想继续追问补偿性替代头");

    await press(host, '[data-nav-view="library"]');
    expect(host.querySelector('[data-screen="chat"]')).toBeNull();
    await press(host, '[data-recent-topic="0"]');
    const restored = host.querySelector("#chat-composer-input") as HTMLTextAreaElement | null;
    expect(restored?.value).toBe("我想继续追问补偿性替代头");
  });

  it("离开对话时记下正文滚动位置，回来按主题恢复", async () => {
    await openChat();
    const scroll = host.querySelector(".chat-scroll") as HTMLDivElement | null;
    expect(scroll).not.toBeNull();
    await act(async () => {
      scroll!.scrollTop = 240;
      scroll!.dispatchEvent(new Event("scroll", { bubbles: true }));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(scrollFor("chat:0")).toBe(240);

    await press(host, '[data-nav-view="home"]');
    await press(host, '[data-recent-topic="0"]');
    const restored = host.querySelector(".chat-scroll") as HTMLDivElement | null;
    expect(restored?.scrollTop).toBe(240);
  });

  it("复制取原始回答全文并给出成功反馈；剪贴板拒绝时明确失败", async () => {
    const written: string[] = [];
    const writeText = vi.fn(async (text: string) => {
      written.push(text);
    });
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText }
    });

    await openChat();
    await press(host, '[data-action="chat-copy"]');
    expect(writeText).toHaveBeenCalledTimes(1);
    expect(written[0]?.length).toBeGreaterThan(1500);
    expect(written[0]?.startsWith("核心先说结论")).toBe(true);
    expect(written[0]?.endsWith("接管了。")).toBe(true);
    expect(host.querySelector('[data-copy-status]')?.textContent).toBe(copyText("chat_copied"));

    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: vi.fn(async () => Promise.reject(new Error("denied"))) }
    });
    Object.defineProperty(document, "execCommand", {
      configurable: true,
      value: vi.fn(() => false)
    });
    await press(host, '[data-action="chat-copy"]');
    expect(host.querySelector('[data-copy-status]')?.textContent).toBe(copyText("chat_copy_failed"));
  });

  it("发送、附件、重置与新建分支都禁用，可访问名说明尚未接通", async () => {
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: vi.fn(async () => undefined) }
    });
    await openChat();
    const copy = host.querySelector('[data-action="chat-copy"]');
    expect(copy?.hasAttribute("disabled")).toBe(false);
    for (const action of ["chat-send", "chat-attach", "chat-reset", "chat-fork"]) {
      const button = host.querySelector(`[data-action="${action}"]`);
      expect(button, action).not.toBeNull();
      expect(button?.tagName).toBe("BUTTON");
      expect(button?.hasAttribute("disabled"), action).toBe(true);
      expect(button?.getAttribute("aria-label"), action).toContain(copyText("chat_action_unavailable"));
      expect(button?.getAttribute("title"), action).toContain(copyText("chat_action_unavailable"));
      expect(button?.getAttribute("class"), action).toContain("icon-btn");
    }
  });

  it("分支面板默认关闭，打开后列根分支、子分支数与 AI 总结，Esc 还焦", async () => {
    await openChat();
    const toggle = host.querySelector('[data-action="toggle-branch-panel"]');
    const panel = host.querySelector("#branch-panel");
    expect(toggle).not.toBeNull();
    expect(toggle?.getAttribute("aria-expanded")).toBe("false");
    expect(panel?.hasAttribute("hidden")).toBe(true);

    await press(host, '[data-action="toggle-branch-panel"]');
    expect(toggle?.getAttribute("aria-expanded")).toBe("true");
    expect(panel?.hasAttribute("hidden")).toBe(false);
    expect(panel?.textContent).toContain("注意力权重可视化与模型可解释性的关系");
    expect(panel?.textContent).toContain("根分支 · 不可归档");
    expect(panel?.textContent).toContain("当前 · 0 子分支");
    expect(panel?.textContent).toContain(copyText("chat_summary"));
    expect(panel?.textContent).toContain("本分支从注意力权重可视化");
    expect(panel?.querySelector('[data-branch="root"]')).not.toBeNull();
    expect(document.activeElement?.id).toBe("branch-close");

    await escape();
    expect(toggle?.getAttribute("aria-expanded")).toBe("false");
    expect(panel?.hasAttribute("hidden")).toBe(true);
    expect(document.activeElement).toBe(toggle);
  });

  it("窄窗遮罩可点关闭分支面板", async () => {
    await openChat();
    await press(host, '[data-action="toggle-branch-panel"]');
    expect(host.querySelector(".panel-scrim")).not.toBeNull();
    await press(host, ".panel-scrim");
    expect(host.querySelector("#branch-panel")?.hasAttribute("hidden")).toBe(true);
    expect(host.querySelector('[data-action="toggle-branch-panel"]')?.getAttribute("aria-expanded")).toBe("false");
  });

  it("知识树显示真实根节点，缩放夹在 50–150%，Esc 关闭并还焦", async () => {
    await openChat();
    await press(host, '[data-action="toggle-tree"]');
    const dialog = host.querySelector('[role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog?.getAttribute("aria-modal")).toBe("true");
    expect(dialog?.textContent).toContain("总览画布");
    expect(dialog?.textContent).toContain("注意力权重可视化与模型可解释性的关系");
    expect(dialog?.textContent).toContain(copyText("chat_tree_only_root"));
    expect(dialog?.querySelectorAll(".tree-node")).toHaveLength(1);
    expect(dialog?.querySelectorAll('[data-tree-child]')).toHaveLength(0);
    expect(dialog?.querySelector("[data-zoom]")?.textContent).toBe("100%");
    expect(document.activeElement?.id).toBe("tree-close");

    await press(host, '[data-action="tree-zoom-in"]');
    expect(host.querySelector("[data-zoom]")?.textContent).toBe("110%");
    for (let step = 0; step < 6; step += 1) await press(host, '[data-action="tree-zoom-in"]');
    expect(host.querySelector("[data-zoom]")?.textContent).toBe("150%");
    for (let step = 0; step < 12; step += 1) await press(host, '[data-action="tree-zoom-out"]');
    expect(host.querySelector("[data-zoom]")?.textContent).toBe("50%");
    await press(host, '[data-action="tree-fit"]');
    expect(host.querySelector("[data-zoom]")?.textContent).toBe("100%");

    const trigger = host.querySelector('[data-action="toggle-tree"]');
    await escape();
    expect(host.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });

  it("主题 1 只显示学习库摘要与无基线说明，不借用主题 0 的回答", async () => {
    await openChat(1);
    expect(host.querySelector('[data-node="chat_title"]')?.textContent).toBe("系统理解 Transformer 的注意力机制");
    expect(host.textContent).toContain(copyText("chat_no_chat_baseline"));
    expect(host.textContent).toContain("Transformer 的注意力机制");
    expect(host.querySelector(".chat-answer")).toBeNull();
    expect(host.textContent).not.toContain("核心先说结论");
    expect(host.textContent).not.toContain("接管了。");
    expect(host.querySelector('[data-action="toggle-tree"]')).toBeNull();
    expect(host.querySelector('[data-action="chat-send"]')?.hasAttribute("disabled")).toBe(true);
    expect(host.querySelector('[data-action="chat-copy"]')?.hasAttribute("disabled")).toBe(true);
    expect(host.querySelector('[data-action="chat-fork"]')?.hasAttribute("disabled")).toBe(true);
    expect(workspaceSnapshot().topic).toBe(1);
  });
});
