// @vitest-environment jsdom
import { act, createElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import { copyText } from "../../src/copy";
import { AppShell } from "../../src/layouts/AppShell";

// React 19 的 act 需要这个全局开关才承认「这是测试环境」。
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe("外壳的文案在渲染时解析", () => {
  let root: Root | undefined;

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    root = undefined;
    document.title = "";
  });

  it("标题与两枚标签的文本来自文案表，而不是组件里的常量", () => {
    const host = document.createElement("div");
    document.body.appendChild(host);
    act(() => {
      root = createRoot(host);
      root.render(createElement(AppShell));
    });
    expect(document.title).toBe(copyText("app_name"));
    const labels = Array.from(document.querySelectorAll(".app-tab")).map((el) => el.textContent);
    expect(labels).toEqual([copyText("tab_home"), copyText("tab_library")]);
    expect(document.querySelector("main[data-slot='screen']")).not.toBeNull();
  });
});
