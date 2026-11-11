// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DesktopWorkspace } from "../../src/pages/DesktopWorkspace";
import type { DesktopApi, DesktopState } from "../../src/platform/desktop";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe("桌面草稿写入合并", () => {
  let host: HTMLDivElement;
  let root: Root;
  let state: DesktopState;
  let api: DesktopApi;
  let close: () => boolean | Promise<boolean>;
  let resolveFirst: () => void;
  let rejectFirst: (error: Error) => void;
  let first: Promise<void>;

  beforeEach(async () => {
    first = new Promise((resolve, reject) => { resolveFirst = resolve; rejectFirst = reject; });
    state = { settings: { baseUrl: "https://example.test", model: "m", hasApiKey: true }, drafts: {}, topics: [{ id: "A", title: "主题 A", createdAt: 1, updatedAt: 1, messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [] },
      { id: "B", topicId: "A", parentBranchId: "A", sourceMessageId: null, createdAt: 2, messages: [] }
    ] }] };
    api = {
      getState: vi.fn(async () => structuredClone(state)),
      saveSettings: vi.fn(async () => {}), deleteApiKey: vi.fn(async () => {}), deleteTopic: vi.fn(async () => {}), setPersona: vi.fn(async () => {}),
      saveDraft: vi.fn().mockImplementationOnce(() => first).mockImplementation(async () => {}),
      sendMessage: vi.fn(async () => ({ topicId: "A", branchId: "A" })), retryMessage: vi.fn(async () => ({ topicId: "A" })),
      createBranch: vi.fn(async () => ({ branchId: "B" })), cancelMessage: vi.fn(async () => ({ cancelled: true })),
      selectAttachment: vi.fn(async () => ({ cancelled: true })), removeAttachment: vi.fn(async () => {}),
      onProgress: vi.fn(() => () => {}), onBeforeClose: vi.fn((callback) => { close = callback; return () => {}; }), onCloseCancelled: vi.fn(() => () => {})
    };
    host = document.createElement("div"); document.body.append(host); root = createRoot(host);
    await act(async () => { root.render(<DesktopWorkspace api={api} />); });
  });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function type(value: string) {
    const input = host.querySelector<HTMLTextAreaElement>("textarea")!;
    const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")!.set!;
    await act(async () => { setter.call(input, value); input.dispatchEvent(new Event("input", { bubbles: true })); });
  }
  async function click(selector: string) { await act(async () => { host.querySelector<HTMLButtonElement>(selector)!.click(); }); }

  it("首笔在途时合并后续百次同范围修改，关闭等待最后一笔", async () => {
    let resolveLast!: () => void;
    vi.mocked(api.saveDraft).mockReset().mockImplementationOnce(() => first).mockImplementationOnce(() => new Promise((resolve) => { resolveLast = resolve; }));
    await type("草稿 0");
    expect(api.saveDraft).toHaveBeenCalledTimes(1);
    for (let i = 1; i <= 100; i++) await type(`草稿 ${i}`);
    let closed = false;
    let result!: Promise<boolean>;
    await act(async () => { result = Promise.resolve(close()); void result.then(() => { closed = true; }); });
    expect(closed).toBe(false);
    await act(async () => { resolveFirst(); });
    expect(api.saveDraft).toHaveBeenCalledTimes(2);
    expect(closed).toBe(false);
    await act(async () => { resolveLast(); });
    expect(await result).toBe(true);
    expect(api.saveDraft).toHaveBeenCalledTimes(2);
    expect(api.saveDraft).toHaveBeenNthCalledWith(1, { topicId: null, text: "草稿 0" });
    expect(api.saveDraft).toHaveBeenNthCalledWith(2, { topicId: null, text: "草稿 100" });
  });

  it("不同分支交错及首笔失败保持范围隔离，最终空草稿正常保存", async () => {
    await click(".recent-link");
    await type("A 第一笔");
    await type("A 新内容");
    await click('[data-branch-id="B"]');
    await type("B 内容");
    await type("");
    await act(async () => { rejectFirst(new Error("STORAGE_ERROR")); });
    expect(api.saveDraft).toHaveBeenCalledTimes(3);
    expect(api.saveDraft).toHaveBeenNthCalledWith(2, { topicId: "A", text: "A 新内容" });
    expect(api.saveDraft).toHaveBeenNthCalledWith(3, { topicId: "A", branchId: "B", text: "" });
    await click('[data-branch-id="A"]');
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("A 新内容");
    await click('[data-branch-id="B"]');
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("");
  });

  it("发送必须等待最新草稿确认，失败不能发送并保留输入", async () => {
    await type("首笔");
    await type("最后内容");
    vi.mocked(api.saveDraft).mockImplementationOnce(async () => { throw new Error("STORAGE_ERROR"); });
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    expect(api.sendMessage).not.toHaveBeenCalled();
    await act(async () => { resolveFirst(); });
    expect(api.sendMessage).not.toHaveBeenCalled();
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("最后内容");
    expect(host.textContent).toContain("草稿暂未保存");
  });

  it("旧版失败不覆盖新版，另一范围最新失败时关闭仍被阻止", async () => {
    await click(".recent-link");
    await type("A 旧版");
    await type("A 新版");
    await click('[data-branch-id="B"]');
    await type("B 未保存");
    vi.mocked(api.saveDraft).mockImplementation(async ({ branchId }) => {
      if (branchId === "B") throw new Error("STORAGE_ERROR");
    });
    await act(async () => { rejectFirst(new Error("STORAGE_ERROR")); });
    expect(host.textContent).toContain("草稿暂未保存");
    let result!: boolean;
    await act(async () => { result = await close(); });
    expect(result).toBe(false);
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("B 未保存");
    await click('[data-branch-id="A"]');
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("A 新版");
  });

  it("刷新读取落后时不覆盖读取期间刚保存的新草稿", async () => {
    let finishRead!: (value: DesktopState) => void;
    vi.mocked(api.getState).mockImplementationOnce(() => new Promise((resolve) => { finishRead = resolve; }));
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-settings"]')!.click(); });
    const form = host.querySelector<HTMLFormElement>("form")!;
    await act(async () => { form.requestSubmit(); });
    await act(async () => { [...host.querySelectorAll<HTMLButtonElement>(".side-link")].find((button) => button.textContent?.includes("开始新学习"))!.click(); });
    await type("读取期间新草稿");
    await act(async () => { resolveFirst(); });
    await act(async () => { finishRead({ ...state, drafts: {} }); });
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("读取期间新草稿");
  });

  it("一个分支保存失败后，切换并成功保存另一分支仍显示可操作告警", async () => {
    vi.mocked(api.saveDraft).mockReset().mockImplementation(async ({ topicId, branchId, text }) => {
      if ((branchId ?? topicId) === "A") throw new Error("STORAGE_ERROR");
      state.drafts[branchId ?? topicId ?? "home"] = text;
    });
    await click(".recent-link");
    await type("A 的未保存草稿");
    expect(host.textContent).toContain("草稿暂未保存");
    await click('[data-branch-id="B"]');
    await type("B 的已保存草稿");
    expect(host.textContent).toContain("草稿暂未保存");
    expect(host.textContent).toContain("重试保存草稿");
    await click('[data-action="desktop-settings"]');
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    expect(host.textContent).toContain("草稿暂未保存");
    expect(host.textContent).toContain("重试保存草稿");
    await click('[data-action="desktop-settings"]');
    await click(".recent-link");
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("A 的未保存草稿");
    vi.mocked(api.saveDraft).mockImplementation(async ({ topicId, branchId, text }) => { state.drafts[branchId ?? topicId ?? "home"] = text; });
    const retry = [...host.querySelectorAll<HTMLButtonElement>("button")].find((button) => button.textContent === "重试保存草稿")!;
    await act(async () => { retry.click(); });
    expect(state.drafts.A).toBe("A 的未保存草稿");
    expect(host.textContent).not.toContain("草稿暂未保存");
  });

  it("失败草稿尝试发送后，编辑保存成功只清除该范围告警", async () => {
    vi.mocked(api.saveDraft).mockReset().mockImplementation(async ({ topicId, branchId }) => {
      if ((branchId ?? topicId) === "A") throw new Error("STORAGE_ERROR");
    });
    await click(".recent-link"); await type("A 失败草稿");
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    expect(api.sendMessage).not.toHaveBeenCalled();
    expect(host.textContent).toContain("草稿暂未保存");
    vi.mocked(api.saveDraft).mockImplementation(async () => {});
    await type("A 修正后保存");
    expect(host.textContent).not.toContain("草稿暂未保存");
    expect(host.textContent).not.toContain("重试保存草稿");
    await click('[data-branch-id="B"]');
    vi.mocked(api.saveDraft).mockImplementation(async ({ branchId }) => { if (branchId === "B") throw new Error("STORAGE_ERROR"); });
    await type("B 失败草稿");
    await click('[data-branch-id="A"]'); await type("A 再次成功");
    expect(host.textContent).toContain("草稿暂未保存");
    expect(host.textContent).toContain("重试保存草稿");
  });

  it("失败草稿阻止重试后，编辑保存成功不遗留过期告警", async () => {
    state.topics[0]!.branches![0]!.messages = [{ id: "failed", branchId: "A", role: "user", content: "失败问题", createdAt: 1, status: "failed", requestId: "request" }];
    vi.mocked(api.saveDraft).mockReset().mockRejectedValueOnce(new Error("STORAGE_ERROR")).mockResolvedValue(undefined);
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await click(".recent-link"); await type("未保存");
    await click('[data-action="desktop-retry"]');
    expect(api.retryMessage).not.toHaveBeenCalled();
    await type("保存成功");
    expect(host.textContent).not.toContain("草稿暂未保存");
  });

  it("失败草稿阻止删除后，重试保存成功不遗留过期告警", async () => {
    vi.mocked(api.saveDraft).mockReset().mockRejectedValueOnce(new Error("STORAGE_ERROR")).mockResolvedValue(undefined);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    await click(".recent-link"); await type("未保存");
    const history = [...host.querySelectorAll<HTMLButtonElement>(".side-link")].find((button) => button.textContent?.includes("学习记录"))!;
    await act(async () => { history.click(); });
    await click('[aria-label="删除学习记录：主题 A"]');
    expect(api.deleteTopic).not.toHaveBeenCalled();
    const retry = [...host.querySelectorAll<HTMLButtonElement>("button")].find((button) => button.textContent === "重试保存草稿")!;
    await act(async () => { retry.click(); });
    expect(host.textContent).not.toContain("草稿暂未保存");
    vi.restoreAllMocks();
  });

  it("保存草稿重试成功不会清除并存的网络或存储错误", async () => {
    vi.mocked(api.saveDraft).mockReset().mockRejectedValueOnce(new Error("STORAGE_ERROR")).mockResolvedValue(undefined);
    vi.mocked(api.selectAttachment!).mockRejectedValueOnce(new Error("NETWORK_ERROR")).mockRejectedValueOnce(new Error("STORAGE_ERROR"));
    await type("保存失败的草稿");
    await click('[data-action="desktop-select-attachment"]');
    expect(host.textContent).toContain("无法连接模型服务");
    const retry = () => [...host.querySelectorAll<HTMLButtonElement>("button")].find((button) => button.textContent === "重试保存草稿")!;
    await act(async () => { retry().click(); });
    expect(host.textContent).not.toContain("草稿暂未保存");
    expect(host.textContent).toContain("无法连接模型服务");
    vi.mocked(api.saveDraft).mockRejectedValueOnce(new Error("STORAGE_ERROR"));
    await type("再次保存失败");
    await click('[data-action="desktop-select-attachment"]');
    expect(host.textContent).toContain("本地保存失败");
    await act(async () => { retry().click(); });
    expect(host.textContent).not.toContain("草稿暂未保存");
    expect(host.textContent).toContain("本地保存失败");
  });
});
