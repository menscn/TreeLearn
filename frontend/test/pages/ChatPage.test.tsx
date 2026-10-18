// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import { navigate, replaceStack } from "../../src/store/screenRoute";
import { ChatPage } from "../../src/pages/ChatPage";
import { ScreenRouter } from "../../src/pages/ScreenRouter";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

async function render(host: HTMLElement, node: ReactNode): Promise<Root> {
  let root!: Root;
  await act(async () => {
    root = createRoot(host);
    root.render(node);
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

describe("对话屏把一条回答、三个动作与三行建议读出来", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    replaceStack("chat");
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
  });

  it("正文段落非空、建议行与夹具里的 suggestion_row 同数", async () => {
    root = await render(host, createElement(ChatPage));
    expect(host.querySelector('[data-node="chat_title"]')?.textContent).toContain("注意力");
    expect(host.querySelectorAll(".chat-answer-run").length).toBeGreaterThanOrEqual(1);
    const runs = Array.from(host.querySelectorAll(".chat-answer-run")).map((el) => el.textContent ?? "");
    expect(runs.every((t) => t.length > 0)).toBe(true);
    expect(host.querySelectorAll(".suggestion-row").length).toBe(3);
    expect(host.querySelectorAll('[data-node="act_copy"], [data-node="act_reset"], [data-node="act_fork"]').length)
      .toBe(3);
    expect(host.querySelector('[data-node="model_chip_label"]')?.textContent).toBe("V4.1 Flash");
  });

  it("工具条三枚入口各自走到对应的屏，返回键把栈缩回去", async () => {
    navigate("home");
    replaceStack("chat");
    root = await render(host, createElement(ScreenRouter));
    await press(host, '[data-node="chat_overview"]');
    expect(host.querySelector('[data-screen="treecanvas"]')).not.toBeNull();
    await press(host, '[data-node="tree_back"]');
    expect(host.querySelector('[data-screen="chat"]')).not.toBeNull();
    await press(host, '[data-node="chat_nav"]');
    expect(host.querySelector('[data-screen="navigation"]')).not.toBeNull();
  });

  it("节点表里没有动作映射的入口画成静态块，不做假可点", async () => {
    root = await render(host, createElement(ChatPage));
    expect(host.querySelectorAll(".node-plain").length).toBeGreaterThan(0);
    const time = host.querySelector('[data-node="act_time"]');
    expect(time?.classList.contains("node-plain")).toBe(true);
    expect(time?.tagName).toBe("DIV");
    expect(host.querySelector('button[data-node="act_copy"]')).toBeNull();
  });
});
