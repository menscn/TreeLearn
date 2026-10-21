// @vitest-environment jsdom
import { act, createElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import { navigate, replaceStack } from "../../src/store/screenRoute";
import { ScreenRouter } from "../../src/pages/ScreenRouter";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

async function render(host: HTMLElement): Promise<Root> {
  let root!: Root;
  await act(async () => {
    root = createRoot(host);
    root.render(createElement(ScreenRouter));
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

/** 夹具正文 → 必须出现在页面上的文字，与 web_walk 的覆盖判据同一份来源。 */
function expectedTexts(screen: string): string[] {
  const envelope = JSON.parse(bundledEntries()[screen] ?? "{}") as {
    payload?: { nodes?: Array<{ text?: string }> };
  };
  return (envelope.payload?.nodes ?? [])
    .map((node) => (node.text ?? "").trim())
    .filter((text) => text !== "");
}

describe("还没精修的屏按采集坐标还原，并且出得去", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    replaceStack("home");
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

  it("设置页的屏头、卡面与六个入口都在采集坐标上，入口逐条走到各自的屏", async () => {
    navigate("settings");
    root = await render(host);
    expect(host.querySelector('[data-screen="settings"]')).not.toBeNull();
    expect(host.querySelector(".appbar-title")?.textContent).toBe("设置");
    for (const text of expectedTexts("settings")) expect(host.textContent).toContain(text);
    const entries = host.querySelectorAll("button.node-tap[data-node$='_entry']");
    expect(entries.length).toBe(6);
    await press(host, '[data-node="settings_models_entry"]');
    expect(host.querySelector('[data-screen="modelsettings"]')).not.toBeNull();
    expect(host.querySelector(".appbar-title")?.textContent).toBe("模型设置");
    expect(host.textContent?.match(/deepseek-flash/g)?.length ?? 0).toBeGreaterThanOrEqual(3);
  });

  it("自绘屏的返回图标没有 resource-id，Web 侧把它认成 state_back 并把栈退回去", async () => {
    navigate("settings");
    root = await render(host);
    await press(host, '[data-node="settings_search_entry"]');
    const screen = host.querySelector('[data-screen="searchservicesettings"]');
    expect(screen).not.toBeNull();
    const back = host.querySelector('[data-node="state_back"]');
    expect(back?.tagName).toBe("BUTTON");
    await press(host, '[data-node="state_back"]');
    expect(host.querySelector('[data-screen="settings"]')).not.toBeNull();
  });

  it("名单里没有的屏读成那条文案，而不是空白或假装有内容", async () => {
    replaceStack("nosuchscreen");
    root = await render(host);
    expect(host.querySelector('[data-copy="state_fixture_unavailable"]')).not.toBeNull();
  });

  it("抽屉那屏保留家底与抽屉两层，日历卡是表面而不是假按钮", async () => {
    navigate("homedrawer");
    root = await render(host);
    const card = host.querySelector('[data-node="drawer_heatmap_card"]');
    expect(card?.tagName).not.toBe("BUTTON");
    expect(card?.className).toContain("snap-surface");
    expect(host.textContent).toContain("学习概览");
    expect(host.textContent).toContain("学习热度");
    expect(host.textContent).toContain("最近学习");
    const back = host.querySelector('[data-node="state_back"]');
    expect(back).not.toBeNull();
    await press(host, '[data-node="state_back"]');
    expect(host.querySelector('[data-screen="home"]')).not.toBeNull();
  });

  it("画布那屏把采集到的标题和副题还原成屏头，返回键按 resource-id 退出", async () => {
    navigate("treecanvas");
    root = await render(host);
    expect(host.querySelector(".appbar-title")?.textContent).toBe("总览画布");
    expect(host.textContent).toContain("注意力权重可视化与模型可解释性的关系");
    const back = host.querySelector('[data-node="tree_back"]');
    expect(back?.tagName).toBe("BUTTON");
    await press(host, '[data-node="tree_back"]');
    expect(host.querySelector('[data-screen="home"]')).not.toBeNull();
  });
});
