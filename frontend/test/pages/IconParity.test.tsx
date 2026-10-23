// @vitest-environment jsdom
import { act, createElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { iconFor, isIconNode } from "../../src/components/Icon";
import { WorkspaceDataProvider } from "../../src/components/WorkspaceDataProvider";
import { copyText } from "../../src/copy";
import { AppShell } from "../../src/layouts/AppShell";
import { WorkspaceRouter } from "../../src/pages/WorkspaceRouter";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import type { FixturePayload } from "../../src/platform/fixtureNodes";
import { injectRuntime } from "../../src/platform/runtime";
import { navigate, replaceStack } from "../../src/store/screenRoute";
import { openTopic, openView, resetWorkspace, setSettingsCategory } from "../../src/store/workspace";
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

async function renderWorkspace(host: HTMLElement): Promise<Root> {
  let root!: Root;
  await act(async () => {
    root = createRoot(host);
    root.render(
      createElement(
        WorkspaceDataProvider,
        null,
        createElement(AppShell, null, createElement(WorkspaceRouter))
      )
    );
    await new Promise((resolve) => setTimeout(resolve, 0));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  return root;
}

describe("图标节点的字形与 Android drawable 同源", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    replaceStack("home");
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
  });

  it("夹具里的每个图标节点都能落到一枚 drawable 上，未知图标不会静默退回 content-desc", () => {
    let count = 0;
    for (const [screen, raw] of Object.entries(bundledEntries())) {
      const payload = (JSON.parse(raw) as { payload: FixturePayload }).payload;
      for (const node of payload.nodes) {
        if (!isIconNode(node)) continue;
        count += 1;
        expect(iconFor(node), `${screen}:${node.id || node.desc || node.class}`).not.toBeNull();
      }
    }
    expect(count).toBeGreaterThan(50);
  });

  it("工作区首页的品牌、推荐、主题与输入区都出字形，content-desc 不落到页面上", async () => {
    root = await renderWorkspace(host);
    expect(host.querySelector('[data-screen="home"]')).not.toBeNull();
    for (const leaked of ["打开菜单", "知芽 Logo"]) {
      expect(host.textContent, leaked).not.toContain(leaked);
    }
    expect(host.querySelector(".brand svg")).not.toBeNull();
    expect(host.querySelector('[data-action="toggle-nav"] svg')).not.toBeNull();
    expect(host.querySelector(".page-eyebrow svg")).not.toBeNull();
    expect(host.querySelector('[data-node="home_recommendations_title"] svg')).not.toBeNull();
    expect(host.querySelectorAll("[data-recommendation] svg")).toHaveLength(2);
    expect(host.querySelectorAll('[data-node="home_topic_card"] .topic-open svg')).toHaveLength(2);
    expect(host.querySelectorAll('[data-node="home_topic_meta"] svg')).toHaveLength(2);
    expect(host.querySelectorAll('[data-node="home_model_chip"] svg')).toHaveLength(2);
    expect(host.querySelector('[data-action="chat-attach"] svg')).not.toBeNull();
    expect(host.querySelector('[data-action="chat-send"] svg')).not.toBeNull();
  });

  it("工作区对话屏的分支、树、复制、附件与发送都出字形，按钮不贴文字", async () => {
    openTopic(0, 2);
    root = await renderWorkspace(host);
    expect(host.querySelector('[data-screen="chat"]')).not.toBeNull();
    const unavailable = copyText("chat_action_unavailable");
    const icons: [string, string][] = [
      ["toggle-branch-panel", copyText("chat_branch_open")],
      ["toggle-tree", copyText("chat_tree_open")],
      ["chat-copy", copyText("chat_copy")],
      ["chat-reset", `${copyText("chat_action_reset")} · ${unavailable}`],
      ["chat-fork", `${copyText("chat_action_fork")} · ${unavailable}`],
      ["chat-attach", `${copyText("chat_attach")} · ${unavailable}`],
      ["chat-send", `${copyText("chat_send")} · ${unavailable}`]
    ];
    for (const [action, label] of icons) {
      const el = host.querySelector(`[data-action="${action}"]`);
      expect(el, action).not.toBeNull();
      expect(el?.querySelector("svg"), action).not.toBeNull();
      expect(el?.textContent, action).toBe("");
      expect(el?.getAttribute("aria-label"), action).toBe(label);
    }
    expect(host.querySelectorAll('[data-node="chat_model_chip"] svg').length).toBe(2);
  });

  it("工作区学习库的返回、搜索与摘要按钮都带图标", async () => {
    openView("library");
    root = await renderWorkspace(host);
    expect(host.querySelector('[data-screen="library"]')).not.toBeNull();
    expect(host.querySelector('[data-action="workspace-back"] svg')).not.toBeNull();
    expect(host.textContent).not.toContain("返回");
    expect(host.querySelector(".search-field > svg")).not.toBeNull();
    expect(host.querySelectorAll("[data-summary-toggle] svg")).toHaveLength(2);
  });

  it("工作区设置的分类、保存、删除与试听按钮都带图标", async () => {
    openView("settings");
    root = await renderWorkspace(host);
    expect(host.querySelector('[data-screen="settings"]')).not.toBeNull();
    expect(host.querySelectorAll("[data-settings-category] svg")).toHaveLength(6);
    expect(host.querySelector('[data-settings-action="删除此 Key"] svg')).not.toBeNull();
    expect(host.querySelector('[data-settings-action="保存"] svg')).not.toBeNull();
    await act(async () => {
      setSettingsCategory("voice");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(host.querySelector('[data-settings-action="试听"] svg')).not.toBeNull();
    expect(host.textContent).not.toContain("返回");
  });

  it("快照屏的设置入口画 chevron，画布工具画放大/缩小/适应/回到", async () => {
    navigate("settings");
    root = await render(host);
    expect(host.querySelector('[data-screen="settings"] svg')).not.toBeNull();
    expect(host.querySelectorAll('[data-screen="settings"] svg').length).toBeGreaterThanOrEqual(7);

    act(() => root?.unmount());
    root = undefined;
    replaceStack("home");
    navigate("treecanvas");
    root = await render(host);
    for (const id of ["tree_zoom_in", "tree_zoom_out", "tree_fit_tool", "tree_center"]) {
      expect(host.querySelector(`[data-node="${id}"] svg`), id).not.toBeNull();
    }
  });
});
