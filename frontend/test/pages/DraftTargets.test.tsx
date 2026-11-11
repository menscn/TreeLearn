// @vitest-environment jsdom
import { act, createElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as dataProvider from "../../src/components/WorkspaceDataProvider";
import { AppShell } from "../../src/layouts/AppShell";
import { WorkspaceRouter } from "../../src/pages/WorkspaceRouter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { payloadOf } from "../../src/platform/fixtureNodes";
import { draftFor, resetWorkspace, setDraft, workspaceSnapshot } from "../../src/store/workspace";
import { workspaceDataFrom } from "../../src/store/workspaceData";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe("推荐目标主题的草稿保护", () => {
  let host: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    window.history.replaceState(null, "", "/");
    resetWorkspace();
    host = document.createElement("div");
    document.body.append(host);
  });

  afterEach(() => {
    act(() => root?.unmount());
    vi.restoreAllMocks();
    host.remove();
    window.history.replaceState(null, "", "/");
  });

  it("不同主题的推荐按目标保护草稿，连续取消和确认不串写", async () => {
    const payloads = Object.fromEntries(Object.entries(bundledEntries()).map(([screen, text]) =>
      [screen, payloadOf(JSON.parse(text))]));
    const actual = workspaceDataFrom(payloads, "ready");
    expect(actual.topics.length).toBeGreaterThanOrEqual(2);
    vi.spyOn(dataProvider, "useWorkspaceData").mockReturnValue({
      ...actual,
      recommendations: [
        { tag: "测试", text: "主题一的问题", topicIndex: 1 },
        { tag: "测试", text: "主题零的问题", topicIndex: 0 }
      ]
    });
    const confirm = vi.spyOn(window, "confirm")
      .mockReturnValueOnce(false).mockReturnValueOnce(false).mockReturnValueOnce(true);
    setDraft("chat:1", "主题一原稿");
    setDraft("home", "首页原稿");
    await act(async () => {
      root = createRoot(host);
      root.render(createElement(AppShell, null, createElement(WorkspaceRouter)));
    });
    const target = host.querySelector<HTMLButtonElement>('[data-recommendation="0"]')!;
    expect(target).not.toBeNull();
    target.focus();
    await act(async () => target.click());
    expect(document.activeElement).toBe(target);
    expect(workspaceSnapshot().view).toBe("home");
    expect(draftFor("chat:1")).toBe("主题一原稿");
    await act(async () => target.click());
    expect(document.activeElement).toBe(target);
    expect(draftFor("chat:1")).toBe("主题一原稿");
    await act(async () => target.click());
    expect(workspaceSnapshot()).toMatchObject({ view: "chat", topic: 1 });
    expect(draftFor("chat:1")).toBe("主题一的问题");
    expect(draftFor("chat:0")).toBe("");
    expect(draftFor("home")).toBe("首页原稿");
    expect(confirm).toHaveBeenCalledTimes(3);

    await act(async () => host.querySelector<HTMLButtonElement>('[data-nav-view="home"]')!.click());
    await act(async () => host.querySelector<HTMLButtonElement>('[data-recommendation="1"]')!.click());
    expect(workspaceSnapshot()).toMatchObject({ view: "chat", topic: 0 });
    expect(draftFor("chat:0")).toBe("主题零的问题");
    expect(draftFor("chat:1")).toBe("主题一的问题");
    expect(confirm).toHaveBeenCalledTimes(3);
  });
});
