// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DesktopWorkspace } from "../../src/pages/DesktopWorkspace";
import type { DesktopApi, DesktopState } from "../../src/platform/desktop";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe("桌面文本资料", () => {
  let host: HTMLDivElement;
  let root: Root;
  let state: DesktopState;
  let api: DesktopApi;
  let selection: Record<string, { id: string; name: string; size: number; preview: string }>;
  let beforeClose: () => boolean | Promise<boolean>;
  const file = { id: "attachment-1", name: "资料.md", size: 14, preview: "合成资料\n<script>不得执行</script>" };

  beforeEach(async () => {
    selection = {};
    state = { settings: { baseUrl: "https://api.deepseek.com", model: "deepseek-flash", hasApiKey: true }, drafts: { home: "我的问题", A: "根草稿", child: "子草稿" }, topics: [{ id: "A", title: "主题 A", createdAt: 1, updatedAt: 1, messages: [], rootBranchId: "A", branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [] },
      { id: "child", topicId: "A", parentBranchId: "A", sourceMessageId: null, createdAt: 2, messages: [] }
    ] }] };
    api = {
      getState: vi.fn(async () => ({ ...structuredClone(state), attachmentDrafts: structuredClone(selection) })),
      saveSettings: vi.fn(async () => {}), deleteApiKey: vi.fn(async () => {}), deleteTopic: vi.fn(async () => {}), setPersona: vi.fn(async () => {}),
      saveDraft: vi.fn(async () => {}), sendMessage: vi.fn(async () => ({ topicId: "A", branchId: "A" })), retryMessage: vi.fn(async () => ({ topicId: "A" })),
      createBranch: vi.fn(async () => ({ branchId: "child" })), cancelMessage: vi.fn(async () => ({ cancelled: true })),
      onProgress: vi.fn(() => () => {}), onBeforeClose: vi.fn((callback) => { beforeClose = callback; return () => {}; }), onCloseCancelled: vi.fn(() => () => {}),
      selectAttachment: vi.fn(async ({ topicId, branchId }) => { selection[branchId ?? topicId ?? "home"] = file; return { cancelled: false, attachment: file }; }),
      removeAttachment: vi.fn(async ({ topicId, branchId }) => { delete selection[branchId ?? topicId ?? "home"]; })
    };
    host = document.createElement("div"); document.body.append(host); root = createRoot(host);
    await act(async () => { root.render(<DesktopWorkspace api={api} />); });
  });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function click(selector: string) { await act(async () => { host.querySelector<HTMLButtonElement>(selector)!.click(); }); }

  it("选择只保存附件草稿、完整预览是文本，并明确发送范围；移除不发送", async () => {
    await click('[data-action="desktop-select-attachment"]');
    expect(api.selectAttachment).toHaveBeenCalledWith({ topicId: null, branchId: null });
    expect(api.sendMessage).not.toHaveBeenCalled();
    expect(host.textContent).toContain("资料.md");
    expect(host.textContent).toContain("14 字节");
    expect(host.textContent).toContain("选择后资料会保存在本机；发送时会提供给当前模型服务商。");
    expect(host.querySelector('[data-attachment-preview]')?.textContent).toContain(file.preview);
    expect(host.querySelector('[data-attachment-preview] script')).toBeNull();
    expect(host.textContent).toContain("仅支持 TXT 和 Markdown");
    expect(host.textContent).toContain("128 KiB");
    expect(host.textContent).toContain("20,000 个 UTF-16 编码单元");
    await click('[data-action="desktop-remove-attachment"]');
    expect(api.removeAttachment).toHaveBeenCalledWith({ topicId: null, branchId: null });
    expect(host.textContent).not.toContain("资料.md");
    expect(api.sendMessage).not.toHaveBeenCalled();
  });

  it("发送所见附件 ID；分支附件和首页附件互不覆盖并重启恢复", async () => {
    await click('[data-action="desktop-select-attachment"]');
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    expect(api.sendMessage).toHaveBeenCalledWith(expect.objectContaining({ attachmentId: file.id }));
    await click('.recent-link');
    await click('[data-branch-id="child"]');
    expect(host.textContent).not.toContain("资料.md");
    const other = { id: "attachment-2", name: "子支.txt", size: 4, preview: "子支内容" };
    vi.mocked(api.selectAttachment!).mockImplementation(async ({ topicId, branchId }) => { selection[branchId ?? topicId ?? "home"] = other; return { cancelled: false, attachment: other }; });
    await click('[data-action="desktop-select-attachment"]');
    expect(api.selectAttachment).toHaveBeenLastCalledWith({ topicId: "A", branchId: "child" });
    await click('[data-branch-id="A"]');
    expect(host.textContent).not.toContain("子支.txt");
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    expect(host.textContent).toContain("资料.md");
    await click('.recent-link'); await click('[data-branch-id="child"]');
    expect(host.textContent).toContain("子支.txt");
  });

  it("选择期间阻止切支和发送，关窗等待并保留取消前附件", async () => {
    selection.home = file;
    let finish!: (value: { cancelled: boolean }) => void;
    vi.mocked(api.selectAttachment!).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await click('[data-action="desktop-select-attachment"]');
    expect(host.querySelector<HTMLButtonElement>('.recent-link')!.disabled).toBe(true);
    expect(host.querySelector<HTMLButtonElement>('button[aria-label="发送问题"]')!.disabled).toBe(true);
    let done = false;
    let close!: Promise<boolean>;
    await act(async () => { close = Promise.resolve(beforeClose()); void close.then(() => { done = true; }); });
    expect(done).toBe(false);
    await act(async () => { finish({ cancelled: true }); });
    expect(await close).toBe(true);
    expect(selection.home).toEqual(file);
  });

  it("附件错误不吞掉已有资料和文本草稿", async () => {
    selection.home = file;
    vi.mocked(api.selectAttachment!).mockRejectedValue(new Error("ATTACHMENT_ENCODING"));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await click('[data-action="desktop-select-attachment"]');
    expect(host.textContent).toContain("UTF-8");
    expect(host.textContent).toContain("资料.md");
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("我的问题");
  });

  it("超限时说明准确的字节和编码长度限制", async () => {
    vi.mocked(api.selectAttachment!).mockRejectedValue(new Error("ATTACHMENT_TOO_LARGE"));
    await click('[data-action="desktop-select-attachment"]');
    expect(host.textContent).toContain("128 KiB");
    expect(host.textContent).toContain("20,000 个 UTF-16 编码单元");
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("我的问题");
  });

  it("发送失败后保留附件和草稿，重试沿原问题请求而非重新选择文件", async () => {
    selection.home = file;
    const send = vi.mocked(api.sendMessage);
    send.mockImplementation(async ({ requestId }) => {
      state.topics[0]!.branches![0]!.messages = [{ id: "user-1", branchId: "A", role: "user", content: "我的问题", createdAt: 1, status: "failed", requestId, attachment: { id: file.id, name: file.name, size: file.size } }];
      throw new Error("NETWORK_ERROR");
    });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    const requestId = send.mock.calls[0]![0].requestId;
    expect(send.mock.calls[0]![0].attachmentId).toBe(file.id);
    expect(selection.home).toEqual(file);
    expect(host.textContent).toContain("本次提问附带资料：资料.md");
    expect(host.textContent).toContain("无法连接模型服务");
    await click('[data-action="desktop-retry"]');
    expect(api.retryMessage).toHaveBeenCalledWith({ requestId });
    expect(api.selectAttachment).not.toHaveBeenCalled();
  });

  it("同一时刻重复选择只打开一次，资料变化错误要求重新确认", async () => {
    let finish!: (value: { cancelled: boolean }) => void;
    vi.mocked(api.selectAttachment!).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await act(async () => {
      const button = host.querySelector<HTMLButtonElement>('[data-action="desktop-select-attachment"]')!;
      button.click(); button.click();
    });
    expect(api.selectAttachment).toHaveBeenCalledTimes(1);
    await act(async () => { finish({ cancelled: true }); });
    vi.mocked(api.selectAttachment!).mockRejectedValue(new Error("ATTACHMENT_CHANGED"));
    await click('[data-action="desktop-select-attachment"]');
    expect(host.textContent).toContain("资料已变化，请重新确认后发送");
  });
});
