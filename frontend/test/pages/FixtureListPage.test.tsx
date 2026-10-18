// @vitest-environment jsdom
import { act, createElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { copyText } from "../../src/copy";
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

describe("还没精修的屏走通用读取，并且出得去", () => {
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

  it("设置里的六个子项按 resource-id 走到各自的屏，正文按采集顺序出字", async () => {
    navigate("settings");
    root = await render(host);
    // 屏头在 Android 侧是没有 resource-id 的文本，这里不猜它是标题，让它作为一行出字。
    expect(host.querySelector(".appbar-title")?.textContent).toBe("");
    expect(Array.from(host.querySelectorAll(".row")).map((el) => el.textContent)).toContain("设置");
    await press(host, '[data-node="settings_models_entry"]');
    expect(host.querySelector('[data-screen="modelsettings"]')).not.toBeNull();
    const rows = Array.from(host.querySelectorAll(".row, .row-title")).map((el) => el.textContent ?? "");
    expect(rows).toContain("模型设置");
    expect(rows.filter((t) => t === "deepseek-flash").length).toBeGreaterThanOrEqual(3);
  });

  it("自绘屏的夹具里没有 *_back 节点，Web 侧补一枚返回按钮把栈退回去", async () => {
    navigate("settings");
    root = await render(host);
    await press(host, '[data-node="settings_search_entry"]');
    expect(host.querySelector('[data-screen="searchservicesettings"]')).not.toBeNull();
    const back = host.querySelector('[data-node="state_back"]');
    expect(back?.textContent).toBe(copyText("state_back"));
    await press(host, '[data-node="state_back"]');
    expect(host.querySelector('[data-screen="settings"]')).not.toBeNull();
  });

  it("名单里没有的屏读成那条文案，而不是空白或假装有内容", async () => {
    replaceStack("nosuchscreen");
    root = await render(host);
    expect(host.querySelector('[data-copy="state_fixture_unavailable"]')).not.toBeNull();
  });

  it("抽屉那屏的日历卡与最近学习按夹具正文出字，没有动作映射的行不做假可点", async () => {
    navigate("homedrawer");
    root = await render(host);
    const card = host.querySelector('[data-node="drawer_heatmap_card"]');
    expect(card?.tagName).toBe("P");
    expect(card?.textContent).toContain("本月学习热度日历");
    expect(Array.from(host.querySelectorAll(".row")).some((el) => (el.textContent ?? "").includes("最近学习")))
      .toBe(true);
  });
});
