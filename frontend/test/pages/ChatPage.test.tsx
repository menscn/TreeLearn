// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WorkspaceDataProvider } from "../../src/components/WorkspaceDataProvider";
import { copyFormat, copyText } from "../../src/copy";
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
    Object.defineProperty(Range.prototype, "getBoundingClientRect", {
      configurable: true,
      value: () => ({
        x: 300, y: 400, width: 300, height: 20,
        top: 400, left: 300, right: 600, bottom: 420,
        toJSON: () => ({})
      })
    });
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
    window.getSelection()?.removeAllRanges();
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

  /** 用真实 Range 选区，再让宿主收到 selectionchange；矩形由 beforeEach 的 stub 提供。 */
  async function select(selector: string): Promise<string> {
    const el = host.querySelector(selector);
    expect(el, selector).not.toBeNull();
    const range = document.createRange();
    range.selectNodeContents(el!);
    const sel = window.getSelection();
    expect(sel).not.toBeNull();
    await act(async () => {
      sel!.removeAllRanges();
      sel!.addRange(range);
      document.dispatchEvent(new Event("selectionchange"));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    return sel!.toString();
  }

  function toolbar(): Element | null {
    return host.querySelector(".selection-toolbar");
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
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
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
    expect(confirm).not.toHaveBeenCalled();

    await typeInto(host, "#chat-composer-input", "我想继续追问补偿性替代头");
    expect(draftFor("chat:0")).toBe("我想继续追问补偿性替代头");

    await press(host, '[data-nav-view="library"]');
    expect(host.querySelector('[data-screen="chat"]')).toBeNull();
    await press(host, '[data-recent-topic="0"]');
    const restored = host.querySelector("#chat-composer-input") as HTMLTextAreaElement | null;
    expect(restored?.value).toBe("我想继续追问补偿性替代头");
  });

  it("对话推荐遇到草稿先确认，取消保留，确认后才替换并聚焦", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    await openChat();
    await typeInto(host, "#chat-composer-input", "对话原草稿");
    const question = host.querySelector('[data-suggestion="0"]')?.textContent;
    await press(host, '[data-suggestion="0"]');
    expect(draftFor("chat:0")).toBe("对话原草稿");
    expect((host.querySelector("#chat-composer-input") as HTMLTextAreaElement).value).toBe("对话原草稿");
    expect(confirm).toHaveBeenCalledWith(copyText("draft_replace_confirm"));
    await press(host, '[data-suggestion="0"]');
    const input = host.querySelector("#chat-composer-input") as HTMLTextAreaElement;
    expect(input.value).toBe(question);
    expect(draftFor("chat:0")).toBe(question);
    expect(document.activeElement).toBe(input);
  });

  it("引用快捷追问保护草稿并保留引用，确认后才替换", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    await openChat();
    await typeInto(host, "#chat-composer-input", "引用前草稿");
    const selected = await select(".chat-answer p");
    await press(host, '[data-word-action="ask_ai"]');
    await press(host, '[data-ask-chip="why"]');
    expect(draftFor("chat:0")).toBe("引用前草稿");
    expect(host.querySelector(".chat-quote")?.textContent).toContain(selected);
    expect(confirm).toHaveBeenCalledWith(copyText("draft_replace_confirm"));
    await press(host, '[data-ask-chip="why"]');
    expect(draftFor("chat:0")).toBe(copyText("chat_ask_query_why"));
    expect(host.querySelector(".chat-quote")?.textContent).toContain(selected);
    expect(document.activeElement).toBe(host.querySelector("#chat-composer-input"));
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

  it("选正文出现六格浮条；选侧栏或折叠选区不出现", async () => {
    await openChat();
    expect(toolbar()).toBeNull();
    await select(".chat-answer p");
    expect(toolbar()).not.toBeNull();
    expect(host.querySelectorAll(".selection-toolbar button")).toHaveLength(6);

    await select(".brand-word");
    expect(toolbar()).toBeNull();

    await act(async () => {
      window.getSelection()!.removeAllRanges();
      document.dispatchEvent(new Event("selectionchange"));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(toolbar()).toBeNull();
  });

  it("浮条复制写入选中文字并给出成功反馈，拒绝时明确失败", async () => {
    const written: string[] = [];
    const writeText = vi.fn(async (text: string) => {
      written.push(text);
    });
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText }
    });
    await openChat();
    const selected = await select(".chat-answer p");
    await press(host, '[data-word-action="copy"]');
    expect(writeText).toHaveBeenCalledTimes(1);
    expect(written[0]).toBe(selected);
    expect(host.querySelector('[data-copy-status]')?.textContent)
      .toBe(copyText("chat_selection_copied"));
    expect(toolbar()).toBeNull();

    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: vi.fn(async () => Promise.reject(new Error("denied"))) }
    });
    Object.defineProperty(document, "execCommand", {
      configurable: true,
      value: vi.fn(() => false)
    });
    await select(".chat-answer p");
    await press(host, '[data-word-action="copy"]');
    expect(host.querySelector('[data-copy-status]')?.textContent).toBe(copyText("chat_copy_failed"));
  });

  it("浮条全选选中整条回答，浮条仍在", async () => {
    await openChat();
    await select(".chat-answer p");
    await press(host, '[data-word-action="select_all"]');
    const answer = host.querySelector(".chat-answer")?.textContent ?? "";
    expect(window.getSelection()?.toString()).toBe(answer);
    expect(toolbar()).not.toBeNull();
  });

  it("问 AI 后出引用条与三个追问 chip，chip 只填草稿，清除引用后一起消失", async () => {
    await openChat();
    const selected = await select(".chat-answer p");
    await press(host, '[data-word-action="ask_ai"]');

    expect(toolbar()).toBeNull();
    const quote = host.querySelector(".chat-quote");
    expect(quote?.textContent).toContain(copyFormat("chat_quoted", selected));
    const chips = host.querySelectorAll("[data-ask-chip]");
    expect(chips).toHaveLength(3);
    expect(chips[0]?.textContent).toBe(copyText("chat_ask_chip_why"));
    expect(chips[1]?.textContent).toBe(copyText("chat_ask_chip_what"));
    expect(chips[2]?.textContent).toBe(copyText("chat_ask_chip_how"));
    const input = host.querySelector("#chat-composer-input") as HTMLTextAreaElement | null;
    expect(document.activeElement).toBe(input);

    await press(host, '[data-ask-chip="why"]');
    expect(input?.value).toBe(copyText("chat_ask_query_why"));
    expect(document.activeElement).toBe(input);
    expect(draftFor("chat:0")).toBe(copyText("chat_ask_query_why"));

    await press(host, '[data-action="chat-quote-clear"]');
    expect(host.querySelector(".chat-quote")).toBeNull();
    expect(host.querySelectorAll("[data-ask-chip]")).toHaveLength(0);
  });

  it("滚动、Esc 与窗口缩放都收起浮条", async () => {
    await openChat();
    const scroll = host.querySelector(".chat-scroll");
    expect(scroll).not.toBeNull();

    await select(".chat-answer p");
    expect(toolbar()).not.toBeNull();
    await act(async () => {
      scroll!.dispatchEvent(new Event("scroll", { bubbles: true }));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(toolbar()).toBeNull();

    await select(".chat-answer p");
    await escape();
    expect(toolbar()).toBeNull();
    expect(window.getSelection()?.rangeCount).toBe(0);

    await select(".chat-answer p");
    await act(async () => {
      window.dispatchEvent(new Event("resize"));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(toolbar()).toBeNull();
  });
});
