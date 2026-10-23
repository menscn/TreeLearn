// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import {
  applyWorkspaceHash,
  back,
  canGoBackWorkspace,
  draftFor,
  openTopic,
  openView,
  parseWorkspaceHash,
  rememberScroll,
  resetWorkspace,
  scrollFor,
  serializeWorkspaceLocation,
  setDraft,
  setLibraryFilter,
  setLibraryQuery,
  setNavCollapsed,
  setPanelOpen,
  setSettingsCategory,
  subscribeWorkspace,
  toggleNav,
  toggleSummary,
  workspaceSnapshot
} from "../../src/store/workspace";

function resetTo(path = "/") {
  window.history.replaceState(null, "", path);
  resetWorkspace();
}

describe("工作区仓库承载跨视图导航与恢复语义", () => {
  beforeEach(() => {
    resetTo();
  });

  it("初始停在主页，栈底只有当前位置", () => {
    const state = workspaceSnapshot();
    expect(state.view).toBe("home");
    expect(state.topic).toBe(0);
    expect(state.stack).toHaveLength(1);
    expect(canGoBackWorkspace()).toBe(false);
  });

  it("同位置不重复压栈，返回一层层退回且栈底不动", () => {
    openView("library");
    expect(workspaceSnapshot().view).toBe("library");
    expect(workspaceSnapshot().stack).toHaveLength(2);
    openView("library");
    expect(workspaceSnapshot().stack).toHaveLength(2);
    openView("settings");
    expect(workspaceSnapshot().stack).toHaveLength(3);
    back();
    expect(workspaceSnapshot().view).toBe("library");
    back();
    expect(workspaceSnapshot().view).toBe("home");
    back();
    expect(workspaceSnapshot().view).toBe("home");
    expect(workspaceSnapshot().stack).toHaveLength(1);
  });

  it("打开主题进入对话；越界主题收敛到 0", () => {
    openTopic(1, 2);
    expect(workspaceSnapshot().view).toBe("chat");
    expect(workspaceSnapshot().topic).toBe(1);
    openTopic(99, 2);
    expect(workspaceSnapshot().topic).toBe(0);
    openTopic(-3, 2);
    expect(workspaceSnapshot().topic).toBe(0);
    openTopic(1.5, 2);
    expect(workspaceSnapshot().topic).toBe(0);
  });

  it("面板、侧栏折叠与设置分类可切换，且不压导航栈", () => {
    const depth = workspaceSnapshot().stack.length;
    setPanelOpen(true);
    expect(workspaceSnapshot().panelOpen).toBe(true);
    toggleNav();
    expect(workspaceSnapshot().navCollapsed).toBe(true);
    toggleNav();
    expect(workspaceSnapshot().navCollapsed).toBe(false);
    setNavCollapsed(true);
    expect(workspaceSnapshot().navCollapsed).toBe(true);
    setSettingsCategory("search");
    expect(workspaceSnapshot().settingsCategory).toBe("search");
    expect(workspaceSnapshot().stack).toHaveLength(depth);
  });

  it("草稿按 key 分开保存；切换主题再回来仍读得到", () => {
    setDraft("home", "根问题草稿");
    setDraft("chat:0", "主题 0 的追问");
    setDraft("chat:1", "主题 1 的追问");
    openTopic(1, 2);
    openView("home");
    openTopic(0, 2);
    expect(draftFor("home")).toBe("根问题草稿");
    expect(draftFor("chat:0")).toBe("主题 0 的追问");
    expect(draftFor("chat:1")).toBe("主题 1 的追问");
    expect(draftFor("chat:9")).toBe("");
  });

  it("阅读位置按 key 记录，后写覆盖前写，未记录为 0", () => {
    rememberScroll("chat:0", 120);
    rememberScroll("chat:0", 260);
    expect(scrollFor("chat:0")).toBe(260);
    expect(scrollFor("library")).toBe(0);
  });

  it("学习库查询、筛选与摘要展开在换视图后保留", () => {
    setLibraryQuery("消融");
    setLibraryFilter("done");
    toggleSummary("topic-0");
    openView("settings");
    openView("library");
    const state = workspaceSnapshot();
    expect(state.libraryQuery).toBe("消融");
    expect(state.libraryFilter).toBe("done");
    expect(state.libraryExpanded).toEqual(["topic-0"]);
    toggleSummary("topic-0");
    expect(workspaceSnapshot().libraryExpanded).toEqual([]);
  });

  it("草稿与滚动写入不触发订阅，导航写入触发一次", () => {
    let calls = 0;
    const unsubscribe = subscribeWorkspace(() => {
      calls += 1;
    });
    setDraft("home", "不打扰整棵树");
    rememberScroll("home", 88);
    expect(calls).toBe(0);
    openView("library");
    expect(calls).toBe(1);
    unsubscribe();
  });

  it("窗口尺寸变化不清空业务状态", () => {
    setDraft("chat:0", "窗口变了也要留着");
    setLibraryQuery("注意力");
    setLibraryFilter("in_progress");
    setSettingsCategory("voice");
    const before = workspaceSnapshot();
    window.dispatchEvent(new Event("resize"));
    const after = workspaceSnapshot();
    expect(after).toBe(before);
    expect(draftFor("chat:0")).toBe("窗口变了也要留着");
    expect(after.libraryQuery).toBe("注意力");
    expect(after.libraryFilter).toBe("in_progress");
    expect(after.settingsCategory).toBe("voice");
  });

  it("resetWorkspace 清空草稿、筛选与位置", () => {
    setDraft("home", "旧草稿");
    setLibraryQuery("旧查询");
    rememberScroll("home", 90);
    setSettingsCategory("files");
    resetTo();
    const state = workspaceSnapshot();
    expect(state.drafts).toEqual({});
    expect(state.scroll).toEqual({});
    expect(state.libraryQuery).toBe("");
    expect(state.libraryFilter).toBe("all");
    expect(state.settingsCategory).toBe("api-key");
    expect(state.stack).toHaveLength(1);
  });
});

describe("工作区 hash 只是位置序列化", () => {
  beforeEach(() => {
    resetTo();
  });

  it("合法的视图与主题可序列化、可解析", () => {
    expect(serializeWorkspaceLocation({ view: "chat", topic: 1 })).toBe("#/chat/1");
    expect(parseWorkspaceHash("#/chat/1")).toEqual({ view: "chat", topic: 1 });
    expect(parseWorkspaceHash("#/settings/0")).toEqual({ view: "settings", topic: 0 });
  });

  it("未知视图回落主页，非法主题回落 0", () => {
    expect(parseWorkspaceHash("#/unknown/4")).toEqual({ view: "home", topic: 0 });
    expect(parseWorkspaceHash("#/chat/abc")).toEqual({ view: "chat", topic: 0 });
    expect(parseWorkspaceHash("")).toEqual({ view: "home", topic: 0 });
  });

  it("浏览器位置应用后不保留旧栈；初始 reset 会读当前 hash", () => {
    openView("library");
    applyWorkspaceHash("#/settings/0");
    expect(workspaceSnapshot().view).toBe("settings");
    expect(workspaceSnapshot().stack).toHaveLength(1);
    expect(canGoBackWorkspace()).toBe(false);

    window.history.replaceState(null, "", "#/chat/1");
    resetWorkspace();
    expect(workspaceSnapshot().view).toBe("chat");
    expect(workspaceSnapshot().topic).toBe(1);
  });
});
