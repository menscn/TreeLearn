// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import { replaceStack } from "../../src/store/screenRoute";
import { HomePage } from "../../src/pages/HomePage";
import { ScreenRouter } from "../../src/pages/ScreenRouter";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** 夹具读取是异步的（端口返回 Promise），所以每次渲染都要跨一个宏任务把 then 链跑完。 */
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

function source(entries: Record<string, string> = bundledEntries()) {
  return new FixtureRuntimeAdapter(memoryFixtureSource(entries));
}

describe("主页把采集夹具读成可点的历史主题", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    replaceStack("home");
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
  });

  it("标题、推荐与历史主题按夹具正文出字，节点身份留在 data-node 上", async () => {
    root = await render(host, createElement(HomePage));
    const titles = Array.from(host.querySelectorAll(".topic-title")).map((el) => el.textContent);
    expect(titles.length).toBeGreaterThanOrEqual(2);
    expect(titles[0]).toContain("注意力");
    expect(host.querySelector('[data-node="home_recent_title"]')?.textContent).toContain("历史主题");
    expect(host.querySelectorAll('[data-node="home_topic_title"]').length).toBe(titles.length);
    const chips = Array.from(host.querySelectorAll('.rec-list button[data-node="rec_chip"]'));
    expect(chips.length).toBeGreaterThanOrEqual(1);
    expect(host.querySelectorAll('[data-node="rec_tag"]').length).toBe(chips.length);
    expect(host.querySelectorAll('[data-node="rec_text"]').length).toBe(chips.length);
    expect(chips[0]?.textContent).toContain("顺着你学的");
  });

  it("点推荐芯片走到对话屏：整枚芯片都是可点面，不是只把问题当文字贴出来", async () => {
    root = await render(host, createElement(ScreenRouter));
    await press(host, '[data-node="rec_chip"]');
    expect(host.querySelector('[data-screen="chat"]')).not.toBeNull();
  });

  it("点历史主题卡走到对话屏，而不是留在原地", async () => {
    root = await render(host, createElement(ScreenRouter));
    expect(host.querySelector('[data-screen="home"]')).not.toBeNull();
    await press(host, '[data-node="home_topic_card"]');
    expect(host.querySelector('[data-screen="chat"]')).not.toBeNull();
    expect(host.querySelector('[data-screen="home"]')).toBeNull();
  });

  it("点设置入口走到设置屏，返回键回到主页", async () => {
    root = await render(host, createElement(ScreenRouter));
    await press(host, '[data-node="home_settings_button"]');
    expect(host.querySelector('[data-screen="settings"]')).not.toBeNull();
    await press(host, '[data-node="settings_back"]');
    expect(host.querySelector('[data-screen="home"]')).not.toBeNull();
  });

  it("读不到夹具时出的是那条文案，而不是空白屏", async () => {
    restore?.();
    restore = injectRuntime(source({}));
    root = await render(host, createElement(HomePage));
    expect(host.querySelector('[data-copy="state_fixture_unavailable"]')?.textContent)
      .toContain("还没有采集到基线");
  });
});
