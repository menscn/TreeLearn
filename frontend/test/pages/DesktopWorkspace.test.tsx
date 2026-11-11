// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DesktopWorkspace } from "../../src/pages/DesktopWorkspace";
import type { DesktopApi, DesktopState } from "../../src/platform/desktop";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

describe("桌面真实工作区", () => {
  let host: HTMLDivElement;
  let root: Root;
  let data: DesktopState;
  let api: DesktopApi;

  beforeEach(async () => {
    data = { settings: { baseUrl: "https://api.deepseek.com", model: "deepseek-flash", hasApiKey: false }, topics: [], drafts: { home: "未发送的草稿" } };
    api = {
      getState: vi.fn(async () => structuredClone(data)),
      saveSettings: vi.fn(async ({ baseUrl, model, apiKey }) => { data.settings = { baseUrl, model, hasApiKey: Boolean(apiKey) || data.settings.hasApiKey }; }),
      deleteApiKey: vi.fn(async () => { data.settings.hasApiKey = false; }),
      deleteTopic: vi.fn(async ({ topicId }) => { data.topics = data.topics.filter((topic) => topic.id !== topicId); delete data.drafts[topicId]; }),
      setPersona: vi.fn(async () => {}),
      saveDraft: vi.fn(async ({ topicId, branchId, text }) => { data.drafts[branchId ?? topicId ?? "home"] = text; }),
      sendMessage: vi.fn(async () => ({ topicId: "topic-1" })),
      retryMessage: vi.fn(async () => ({ topicId: "topic-1" })),
      createBranch: vi.fn(async () => ({ branchId: "child" })),
      cancelMessage: vi.fn(async () => ({ cancelled: true })),
      onProgress: vi.fn(() => () => {}),
      onBeforeClose: vi.fn(() => () => {}), onCloseCancelled: vi.fn(() => () => {})
    };
    host = document.createElement("div"); document.body.append(host);
    root = createRoot(host);
    await act(async () => { root.render(<DesktopWorkspace api={api} />); });
  });
  afterEach(() => { act(() => root.unmount()); host.remove(); });

  async function chooseTopic(title: string) {
    const button = [...host.querySelectorAll<HTMLButtonElement>(".recent-link")].find((item) => item.textContent === title);
    expect(button).toBeDefined();
    await act(async () => { button!.click(); });
  }

  async function typeDraft(value: string) {
    const input = host.querySelector<HTMLTextAreaElement>("textarea")!;
    const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")!.set!;
    await act(async () => { setter.call(input, value); input.dispatchEvent(new Event("input", { bubbles: true })); });
  }

  it("展示真实空历史和已保存草稿，并可进入配置而不展示示例数据", async () => {
    expect((host.querySelector("textarea") as HTMLTextAreaElement).value).toBe("未发送的草稿");
    expect(host.textContent).not.toContain("Transformer");
    expect(host.textContent).toContain("配置模型");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-settings"]')!.click(); });
    expect(host.querySelector<HTMLInputElement>('input[type="password"]')?.value).toBe("");
  });

  it("失败的首次提问可定位持久化消息，并使用原请求标识重试", async () => {
    data.settings.hasApiKey = true;
    root.unmount(); root = createRoot(host);
    vi.mocked(api.sendMessage).mockImplementation(async ({ text, requestId }) => {
      data.topics = [{ id: "topic-1", title: text, createdAt: 1, updatedAt: 1, messages: [{ id: "m1", role: "user", content: text, createdAt: 1, status: "failed", requestId }] }];
      throw new Error("NETWORK_ERROR");
    });
    await act(async () => { root.render(<DesktopWorkspace api={api} />); });
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    const requestId = vi.mocked(api.sendMessage).mock.calls[0]![0].requestId;
    expect(host.textContent).toContain("未发送的草稿");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-retry"]')!.click(); });
    expect(api.retryMessage).toHaveBeenCalledWith({ requestId });
    expect(data.topics[0]?.messages).toHaveLength(1);
  });

  it("一个主题草稿保存失败后，其他主题保存和刷新不会覆盖未保存的输入", async () => {
    data.topics = ["A", "B"].map((id) => ({ id, title: id, createdAt: 1, updatedAt: 1, messages: [] }));
    data.drafts = { home: "", A: "旧草稿 A", B: "旧草稿 B" };
    vi.mocked(api.saveDraft).mockImplementation(async ({ topicId, text }) => {
      if (topicId === "A") throw new Error("STORAGE_ERROR");
      data.drafts[topicId ?? "home"] = text;
    });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("A"); await typeDraft("需要保留的 A");
    expect(host.textContent).toContain("草稿暂未保存到本机");
    await chooseTopic("B"); await typeDraft("已保存的 B");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-settings"]')!.click(); });
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    await chooseTopic("A");
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("需要保留的 A");
    await chooseTopic("B");
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("已保存的 B");
  });

  it("显示真实流式回答，安全呈现代码和格式并忽略其他请求", async () => {
    data.settings.hasApiKey = true;
    let listener!: (event: { requestId: string; topicId: string; content: string }) => void;
    vi.mocked(api.onProgress).mockImplementation((callback) => { listener = callback; return () => {}; });
    let finish!: (value: { topicId: string }) => void;
    vi.mocked(api.sendMessage).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    const requestId = vi.mocked(api.sendMessage).mock.calls[0]![0].requestId;
    await act(async () => { listener({ requestId: "other", topicId: "bad", content: "不应展示" }); });
    expect(host.textContent).not.toContain("不应展示");
    await act(async () => { listener({ requestId, topicId: "topic-1", content: "## 标题\n**重点**\n```js\nconst x = 1;\n```\n<img src=x>" }); });
    expect(host.querySelector(".desktop-markdown h3")?.textContent).toBe("标题");
    expect(host.querySelector(".desktop-markdown strong")?.textContent).toBe("重点");
    expect(host.querySelector(".desktop-markdown pre code")?.textContent).toBe("const x = 1;");
    expect(host.querySelector(".desktop-markdown img")).toBeNull();
    expect(host.textContent).toContain("<img src=x>");
    await act(async () => { finish({ topicId: "topic-1" }); });
  });

  it("追问时先显示当前用户问题，再显示流式回答", async () => {
    data.settings.hasApiKey = true;
    data.topics = [{ id: "topic-1", title: "已有主题", createdAt: 1, updatedAt: 1, messages: [] }];
    data.drafts["topic-1"] = "新的追问";
    let listener!: (event: { requestId: string; topicId: string; content: string }) => void;
    vi.mocked(api.onProgress).mockImplementation((callback) => { listener = callback; return () => {}; });
    let finish!: (value: { topicId: string }) => void;
    vi.mocked(api.sendMessage).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("已有主题");
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    const requestId = vi.mocked(api.sendMessage).mock.calls[0]![0].requestId;
    await act(async () => { listener({ requestId, topicId: "topic-1", content: "正在回答" }); });
    const bubbles = [...host.querySelectorAll(".desktop-message-body")].map((item) => item.textContent);
    expect(bubbles).toContain("新的追问");
    expect(host.textContent).toContain("正在回答");
    await act(async () => { finish({ topicId: "topic-1" }); });
  });

  it("停止生成后保留已保存的问题、部分回答和重试入口", async () => {
    data.settings.hasApiKey = true;
    let rejectSend!: (error: Error) => void;
    vi.mocked(api.sendMessage).mockImplementation(({ text, requestId }) => {
      data.topics = [{ id: "topic-1", title: text, createdAt: 1, updatedAt: 1, messages: [
        { id: "u1", role: "user", content: text, createdAt: 1, status: "pending", requestId },
        { id: "a1", role: "assistant", content: "已有部分回答", createdAt: 2, status: "pending" }
      ] }];
      return new Promise((_resolve, reject) => { rejectSend = reject; });
    });
    vi.mocked(api.cancelMessage).mockImplementation(async () => {
      for (const message of data.topics[0]!.messages) message.status = "cancelled";
      rejectSend(new Error("REQUEST_CANCELLED"));
      return { cancelled: true };
    });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    const requestId = vi.mocked(api.sendMessage).mock.calls[0]![0].requestId;
    const stop = [...host.querySelectorAll<HTMLButtonElement>("button")].find((button) => button.textContent === "停止生成");
    expect(stop).toBeDefined();
    await act(async () => { stop!.click(); });
    expect(api.cancelMessage).toHaveBeenCalledWith({ requestId });
    expect(host.textContent).toContain("已有部分回答");
    expect(host.textContent).toContain("已停止生成");
    expect(host.querySelector('[data-action="desktop-retry"]')).not.toBeNull();
    expect(data.drafts.home).toBe("未发送的草稿");
  });

  it("同一时刻重复提交只发起一个请求", async () => {
    data.settings.hasApiKey = true;
    let finish!: (value: { topicId: string }) => void;
    vi.mocked(api.sendMessage).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await act(async () => {
      const form = host.querySelector<HTMLFormElement>("form")!;
      form.requestSubmit();
      form.requestSubmit();
    });
    expect(api.sendMessage).toHaveBeenCalledTimes(1);
    await act(async () => { finish({ topicId: "topic-1" }); });
  });

  it("回车发送，输入法组合和 Shift 回车不发送", async () => {
    data.settings.hasApiKey = true;
    let finish!: (value: { topicId: string }) => void;
    vi.mocked(api.sendMessage).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    const input = host.querySelector<HTMLTextAreaElement>("textarea")!;
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", isComposing: true, bubbles: true }));
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", shiftKey: true, bubbles: true }));
    });
    expect(api.sendMessage).not.toHaveBeenCalled();
    await act(async () => { input.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true })); });
    expect(api.sendMessage).toHaveBeenCalledTimes(1);
    await act(async () => { finish({ topicId: "topic-1" }); });
  });

  it("删除学习记录前确认，只移除指定主题和草稿", async () => {
    data.topics = ["A", "B"].map((id) => ({ id, title: id, createdAt: 1, updatedAt: 1, messages: [] }));
    data.drafts = { home: "首页草稿", A: "A 草稿", B: "B 草稿" };
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    const history = [...host.querySelectorAll<HTMLButtonElement>(".side-link")].find((button) => button.textContent?.includes("学习记录"))!;
    await act(async () => { history.click(); });
    const remove = host.querySelector<HTMLButtonElement>('[aria-label="删除学习记录：A"]')!;
    await act(async () => { remove.click(); });
    expect(api.deleteTopic).not.toHaveBeenCalled();
    await act(async () => { remove.click(); });
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(api.deleteTopic).toHaveBeenCalledWith({ topicId: "A" });
    expect(data.topics.map((item) => item.id)).toEqual(["B"]);
    expect(data.drafts).toEqual({ home: "首页草稿", B: "B 草稿" });
    expect(host.textContent).toContain("学习记录已删除");
    confirm.mockRestore();
  });

  it("复制回答时复制原文并反馈结果", async () => {
    data.topics = [{ id: "A", title: "A", createdAt: 1, updatedAt: 1, messages: [{ id: "answer", role: "assistant", content: "**答案**", createdAt: 1, status: "complete" }] }];
    const writeText = vi.fn(async () => {});
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText } });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("A");
    const copy = [...host.querySelectorAll<HTMLButtonElement>("button")].find((button) => button.textContent === "复制整条回答")!;
    await act(async () => { copy.click(); });
    expect(writeText).toHaveBeenCalledWith("**答案**");
    expect(host.textContent).toContain("已复制回答");
    Reflect.deleteProperty(navigator, "clipboard");
  });

  it("重试成功后仍区分先前未完成的回答", async () => {
    data.topics = [{ id: "A", title: "A", createdAt: 1, updatedAt: 1, messages: [
      { id: "u1", role: "user", content: "问题", createdAt: 1, status: "complete", requestId: "request" },
      { id: "a1", role: "assistant", content: "失败时的部分答案", createdAt: 2, status: "failed" },
      { id: "a2", role: "assistant", content: "停止时的部分答案", createdAt: 3, status: "cancelled" },
      { id: "a3", role: "assistant", content: "中断时的部分答案", createdAt: 4, status: "interrupted" },
      { id: "a4", role: "assistant", content: "完整回答", createdAt: 5, status: "complete" }
    ] }];
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("A");
    const answers = [...host.querySelectorAll<HTMLElement>(".desktop-message-assistant")];
    expect(answers[0]?.textContent).toContain("回答未完成");
    expect(answers[1]?.textContent).toContain("已停止");
    expect(answers[2]?.textContent).toContain("已中断");
    expect(answers[3]?.textContent).not.toContain("部分回答");
    expect(answers.every((answer) => answer.textContent?.includes("复制整条回答"))).toBe(true);
  });

  it("关闭前等待排队草稿保存，失败时阻止关闭并保留输入", async () => {
    let beforeClose!: () => boolean | Promise<boolean>;
    let cancelClose!: () => void;
    let failSave!: (reason: Error) => void;
    vi.mocked(api.onBeforeClose).mockImplementation((callback) => { beforeClose = callback; return () => {}; });
    vi.mocked(api.onCloseCancelled).mockImplementation((callback) => { cancelClose = callback; return () => {}; });
    vi.mocked(api.saveDraft).mockImplementation(() => new Promise((_resolve, reject) => { failSave = reject; }));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await typeDraft("关闭前刚输入的内容");
    let result!: Promise<boolean>;
    await act(async () => { result = Promise.resolve(beforeClose()); });
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.disabled).toBe(true);
    let finished = false;
    void result.then(() => { finished = true; });
    await act(async () => { await Promise.resolve(); });
    expect(finished).toBe(false);
    await act(async () => { failSave(new Error("STORAGE_ERROR")); });
    expect(await result).toBe(false);
    await act(async () => { cancelClose(); });
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.disabled).toBe(false);
    expect(host.querySelector<HTMLTextAreaElement>("textarea")!.value).toBe("关闭前刚输入的内容");
    expect(host.textContent).toContain("草稿");
    vi.mocked(api.saveDraft).mockImplementation(async ({ topicId, text }) => { data.drafts[topicId ?? "home"] = text; });
    const retry = [...host.querySelectorAll<HTMLButtonElement>("button")].find((button) => button.textContent === "重试保存草稿")!;
    await act(async () => { retry.click(); });
    expect(data.drafts.home).toBe("关闭前刚输入的内容");
    let second!: Promise<boolean>;
    await act(async () => { second = Promise.resolve(beforeClose()); });
    expect(await second).toBe(true);
  });

  it("选择回答风格后持久化，重新打开仍显示并按主题分别设置", async () => {
    const current = data.settings as typeof data.settings & { defaultPersona: string };
    current.defaultPersona = "default";
    const setPersona = vi.fn(async ({ topicId, persona }: { topicId?: string | null; persona: string }) => {
      if (topicId) Object.assign(data.topics.find((topic) => topic.id === topicId)!, { persona });
      else current.defaultPersona = persona;
    });
    Object.assign(api, { setPersona });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    const select = host.querySelector<HTMLSelectElement>('select[aria-label="回答风格"]');
    expect(select).not.toBeNull();
    expect(select!.options).toHaveLength(7);
    await act(async () => { select!.value = "hand-holding"; select!.dispatchEvent(new Event("change", { bubbles: true })); });
    expect(setPersona).toHaveBeenCalledWith({ topicId: null, persona: "hand-holding" });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    expect(host.querySelector<HTMLSelectElement>('select[aria-label="回答风格"]')?.value).toBe("hand-holding");
    data.topics = [{ id: "A", title: "A", createdAt: 1, updatedAt: 1, messages: [], persona: "default" }];
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("A");
    const topicSelect = host.querySelector<HTMLSelectElement>('select[aria-label="回答风格"]')!;
    expect(topicSelect.value).toBe("default");
    await act(async () => { topicSelect.value = "strict-mentor"; topicSelect.dispatchEvent(new Event("change", { bubbles: true })); });
    expect(setPersona).toHaveBeenLastCalledWith({ topicId: "A", persona: "strict-mentor" });
    expect(current.defaultPersona).toBe("hand-holding");
  });

  it("风格保存失败时不显示切换成功，生成中不能切换", async () => {
    data.settings.hasApiKey = true;
    const setPersona = vi.fn(async () => { throw new Error("STORAGE_ERROR"); });
    Object.assign(api, { setPersona });
    let finish!: (value: { topicId: string }) => void;
    vi.mocked(api.sendMessage).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    const select = host.querySelector<HTMLSelectElement>('select[aria-label="回答风格"]')!;
    expect(select.value).toBe("default");
    await act(async () => { select.value = "study-buddy"; select.dispatchEvent(new Event("change", { bubbles: true })); });
    expect(select.value).toBe("default");
    expect(host.textContent).toContain("本地保存失败");
    expect(host.textContent).not.toContain("已切换到");
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    expect(host.querySelector<HTMLSelectElement>('select[aria-label="回答风格"]')?.disabled).toBe(true);
    await act(async () => { finish({ topicId: "topic-1" }); });
  });

  it("风格正在保存时阻止发送，并在关闭前等待保存完成", async () => {
    data.settings.hasApiKey = true;
    let completeSave!: () => void;
    let beforeClose!: () => boolean | Promise<boolean>;
    vi.mocked(api.onBeforeClose).mockImplementation((callback) => { beforeClose = callback; return () => {}; });
    const setPersona = vi.fn(() => new Promise<void>((resolve) => { completeSave = resolve; }));
    Object.assign(api, { setPersona });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    const select = host.querySelector<HTMLSelectElement>('select[aria-label="回答风格"]')!;
    await act(async () => { select.value = "late-night"; select.dispatchEvent(new Event("change", { bubbles: true })); });
    expect(select.disabled).toBe(true);
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })); });
    expect(api.sendMessage).not.toHaveBeenCalled();
    let closed = false;
    let result!: Promise<boolean>;
    await act(async () => { result = Promise.resolve(beforeClose()); void result.then(() => { closed = true; }); });
    expect(closed).toBe(false);
    await act(async () => { completeSave(); });
    expect(await result).toBe(true);
  });

  it("未知风格提示回落默认，并让用户显式选择默认而不是静默修改数据", async () => {
    data.settings.defaultPersona = "future-default";
    data.topics = [{ id: "A", title: "旧主题", createdAt: 1, updatedAt: 1, persona: "future-topic", messages: [
      { id: "answer", role: "assistant", content: "旧回答", createdAt: 1, status: "complete", persona: "future-message" },
      { id: "default-answer", role: "assistant", content: "默认风格的回答", createdAt: 2, status: "complete", persona: "default" }
    ] }];
    vi.mocked(api.setPersona).mockImplementation(async ({ topicId, persona }) => {
      if (topicId) data.topics[0]!.persona = persona;
      else data.settings.defaultPersona = persona;
    });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    expect(host.querySelector<HTMLSelectElement>('select[aria-label="回答风格"]')?.value).toBe("default");
    expect(host.textContent).toContain("这个人设档位无法识别");
    expect(api.setPersona).not.toHaveBeenCalled();
    expect(data.settings.defaultPersona).toBe("future-default");
    const resetDefault = [...host.querySelectorAll<HTMLButtonElement>("button")].find((button) => button.textContent === "使用默认风格")!;
    expect(resetDefault).toBeDefined();
    await act(async () => { resetDefault.click(); });
    expect(api.setPersona).toHaveBeenCalledWith({ topicId: null, persona: "default" });
    expect(data.settings.defaultPersona).toBe("default");
    await chooseTopic("旧主题");
    expect(host.querySelector<HTMLSelectElement>('select[aria-label="回答风格"]')?.value).toBe("default");
    expect(host.textContent).toContain("这个人设档位无法识别");
    const historicalStyles = [...host.querySelectorAll(".desktop-message-assistant .field-note")].map((element) => element.textContent);
    expect(historicalStyles).toEqual(["未知风格", "默认"]);
    expect(data.topics[0]?.persona).toBe("future-topic");
    expect(data.topics[0]?.messages[0]?.persona).toBe("future-message");
    expect(api.setPersona).toHaveBeenCalledTimes(1);
  });

  it("仅完整回答能创建分支，切换分支保留各自草稿并按分支发送", async () => {
    data.settings.hasApiKey = true;
    data.topics = [{ id: "A", title: "主题 A", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [
        { id: "root-answer", branchId: "A", role: "assistant", content: "完整根回答", createdAt: 1, status: "complete" },
        { id: "partial", branchId: "A", role: "assistant", content: "未完成回答", createdAt: 2, status: "failed" }
      ] }
    ] }];
    data.drafts = { home: "", A: "根草稿" };
    const createBranch = vi.fn(async () => {
      data.topics[0]!.branches!.push({ id: "child", topicId: "A", parentBranchId: "A", sourceMessageId: "root-answer", createdAt: 2, messages: [] });
      return { branchId: "child" };
    });
    Object.assign(api, { createBranch });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("主题 A");
    expect(host.querySelectorAll('[data-action="desktop-create-branch"]')).toHaveLength(1);
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-create-branch"]')!.click(); });
    expect(createBranch).toHaveBeenCalledWith(expect.objectContaining({ topicId: "A", parentBranchId: "A", sourceMessageId: "root-answer", requestId: expect.any(String) }));
    expect(host.textContent).toContain("由回答创建的分支");
    await typeDraft("子分支草稿");
    expect(api.saveDraft).toHaveBeenLastCalledWith({ topicId: "A", branchId: "child", text: "子分支草稿" });
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="A"]')!.click(); });
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("根草稿");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="child"]')!.click(); });
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("子分支草稿");
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    expect(api.sendMessage).toHaveBeenCalledWith(expect.objectContaining({ topicId: "A", branchId: "child", text: "子分支草稿" }));
  });

  it("分支创建失败时保留当前页面和原草稿，解释上下文过大", async () => {
    data.topics = [{ id: "A", title: "主题 A", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [
        { id: "answer", branchId: "A", role: "assistant", content: "完整回答", createdAt: 1, status: "complete" }
      ] }
    ] }];
    data.drafts = { A: "原草稿" };
    Object.assign(api, { createBranch: vi.fn(async () => { throw new Error("CONTEXT_TOO_LARGE"); }) });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("主题 A");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-create-branch"]')!.click(); });
    expect(host.textContent).toContain("上下文过长");
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("原草稿");
    expect(host.textContent).toContain("完整回答");
  });

  it("分支草稿失败不会被别支保存覆盖，提交结果不确定时复用创建 ID", async () => {
    data.topics = [{ id: "A", title: "主题 A", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [
        { id: "answer", branchId: "A", role: "assistant", content: "完整回答", createdAt: 1, status: "complete" }
      ] },
      { id: "child", topicId: "A", parentBranchId: "A", sourceMessageId: "answer", createdAt: 2, messages: [] }
    ] }];
    data.drafts = { A: "根草稿", child: "子草稿" };
    vi.mocked(api.saveDraft).mockImplementation(async ({ topicId, branchId, text }) => {
      if (branchId === "child") throw new Error("STORAGE_ERROR");
      data.drafts[branchId ?? topicId ?? "home"] = text;
    });
    const create = vi.fn().mockRejectedValueOnce(new Error("REQUEST_FAILED")).mockResolvedValueOnce({ branchId: "child" });
    Object.assign(api, { createBranch: create });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("主题 A");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="child"]')!.click(); });
    await typeDraft("未保存的子草稿");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="A"]')!.click(); });
    await typeDraft("成功保存的根草稿");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-create-branch"]')!.click(); });
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-create-branch"]')!.click(); });
    expect(create).toHaveBeenCalledTimes(2);
    expect(create.mock.calls[0]![0].requestId).toBe(create.mock.calls[1]![0].requestId);
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("未保存的子草稿");
  });

  it("多个子分支显示各自来源并可返回父回答，保留分支草稿", async () => {
    data.topics = [{ id: "A", title: "主题 A", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [
        { id: "first", branchId: "A", role: "assistant", content: "第一条完整回答：\n带有信息", createdAt: 1, status: "complete" },
        { id: "second", branchId: "A", role: "assistant", content: "第二条完整回答 <img src=x>", createdAt: 2, status: "complete" },
        { id: "later", branchId: "A", role: "user", content: "父分支后来追加的提问", createdAt: 3, status: "complete" }
      ] },
      { id: "child-1", topicId: "A", parentBranchId: "A", sourceMessageId: "first", createdAt: 2, messages: [] },
      { id: "child-2", topicId: "A", parentBranchId: "A", sourceMessageId: "second", createdAt: 3, messages: [] }
    ] }];
    data.drafts = { A: "父草稿", "child-1": "子草稿一", "child-2": "子草稿二" };
    const scroll = vi.fn(function (this: HTMLElement) {
      const container = this.closest<HTMLElement>(".desktop-conversation-scroll");
      if (container) container.scrollTop = 260;
    });
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", { configurable: true, value: scroll });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("主题 A");
    const focus = vi.spyOn(HTMLElement.prototype, "focus");
    const scrollArea = host.querySelector<HTMLElement>(".desktop-conversation-scroll")!;
    scrollArea.scrollTop = 40;
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="child-1"]')!.click(); });
    expect(host.querySelector('[data-branch-source]')?.textContent).toContain("第一条完整回答");
    expect(host.querySelector('[data-branch-source]')?.textContent).not.toContain("父分支后来追加的提问");
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("子草稿一");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-action="desktop-branch-parent"]')!.click(); });
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("父草稿");
    expect(scroll).toHaveBeenCalled();
    expect(scrollArea.scrollTop).toBe(260);
    expect(host.querySelector('[data-message-id="first"]')).not.toBeNull();
    expect(document.activeElement).toBe(host.querySelector('[data-message-id="first"]'));
    expect(focus).toHaveBeenCalledWith({ preventScroll: true });
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="child-2"]')!.click(); });
    expect(host.querySelector('[data-branch-source]')?.textContent).toContain("第二条完整回答");
    expect(host.querySelector('[data-branch-source]')?.textContent).toContain("<img src=x>");
    expect(host.querySelector('[data-branch-source] img')).toBeNull();
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("子草稿二");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="A"]')!.click(); });
    expect(scrollArea.scrollTop).toBe(260);
    focus.mockRestore();
    Reflect.deleteProperty(HTMLElement.prototype, "scrollIntoView");
  });

  it("来源回答缺失时明确提示而不拼接或执行数据", async () => {
    data.topics = [{ id: "A", title: "A", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [] },
      { id: "child", topicId: "A", parentBranchId: "A", sourceMessageId: "missing", createdAt: 2, messages: [] }
    ] }];
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("A");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="child"]')!.click(); });
    expect(host.querySelector('[data-branch-source]')?.textContent).toContain("来源回答暂不可用");
    expect(host.querySelector('[data-action="desktop-branch-parent"]')).toBeNull();
    expect(document.activeElement?.hasAttribute("data-message-id")).toBe(false);
  });

  it("流式期间用户上滚后，后续片段与状态刷新不抢走阅读位置", async () => {
    data.settings.hasApiKey = true;
    data.topics = [{ id: "A", title: "主题 A", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [] }
    ] }];
    data.drafts = { A: "追问" };
    let listener!: (event: { requestId: string; topicId: string; branchId?: string; content: string }) => void;
    vi.mocked(api.onProgress).mockImplementation((callback) => { listener = callback; return () => {}; });
    let finish!: (value: { topicId: string; branchId: string }) => void;
    vi.mocked(api.sendMessage).mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("主题 A");
    const area = host.querySelector<HTMLElement>(".desktop-conversation-scroll")!;
    Object.defineProperty(area, "scrollHeight", { configurable: true, value: 1000 });
    Object.defineProperty(area, "clientHeight", { configurable: true, value: 200 });
    await act(async () => { host.querySelector<HTMLFormElement>("form")!.requestSubmit(); });
    const requestId = vi.mocked(api.sendMessage).mock.calls[0]![0].requestId;
    await act(async () => { listener({ requestId, topicId: "A", branchId: "A", content: "第一段" }); });
    await act(async () => { area.scrollTop = 180; area.dispatchEvent(new Event("scroll", { bubbles: true })); });
    await act(async () => { listener({ requestId, topicId: "A", branchId: "A", content: "第一段 第二段" }); });
    expect(area.scrollTop).toBe(180);
    await act(async () => { listener({ requestId, topicId: "A", branchId: "A", content: "第一段 第二段 第三段" }); });
    expect(area.scrollTop).toBe(180);
    await act(async () => { area.scrollTop = 800; area.dispatchEvent(new Event("scroll", { bubbles: true })); });
    Object.defineProperty(area, "scrollHeight", { configurable: true, value: 1250 });
    await act(async () => { listener({ requestId, topicId: "A", branchId: "A", content: "第一段 第二段 第三段 第四段" }); });
    expect(area.scrollTop).toBe(1250);
    await act(async () => { area.scrollTop = 180; area.dispatchEvent(new Event("scroll", { bubbles: true })); });
    data.topics[0]!.branches![0]!.messages.push({ id: "answer", branchId: "A", role: "assistant", content: "完整回答", createdAt: 1, status: "complete" });
    await act(async () => { finish({ topicId: "A", branchId: "A" }); });
    expect(area.scrollTop).toBe(180);
  });

  it("搜索仅存在于子分支的内容时，进入首个匹配分支并定位消息", async () => {
    data.topics = [{ id: "A", title: "学习主题", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [{ id: "root", branchId: "A", role: "assistant", content: "根回答", createdAt: 1, status: "complete" }] },
      { id: "child-1", topicId: "A", parentBranchId: "A", sourceMessageId: "root", createdAt: 2, messages: [
        { id: "first-match", branchId: "child-1", role: "assistant", content: "首个子支稀有关键词", createdAt: 2, status: "complete" },
        { id: "next-match", branchId: "child-1", role: "assistant", content: "第二个稀有关键词", createdAt: 3, status: "complete" }
      ] },
      { id: "child-2", topicId: "A", parentBranchId: "A", sourceMessageId: "root", createdAt: 3, messages: [{ id: "later-match", branchId: "child-2", role: "assistant", content: "另一支稀有关键词 <img src=x>", createdAt: 3, status: "complete" }] }
    ] }];
    data.drafts = { A: "根草稿", "child-1": "子草稿一", "child-2": "子草稿二" };
    const scroll = vi.fn(function (this: HTMLElement) {
      const container = this.closest<HTMLElement>(".desktop-conversation-scroll");
      if (container) container.scrollTop = this.dataset.messageId === "first-match" ? 260 : 500;
    });
    Object.defineProperty(HTMLElement.prototype, "scrollIntoView", { configurable: true, value: scroll });
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("学习主题");
    const area = host.querySelector<HTMLElement>(".desktop-conversation-scroll")!;
    area.scrollTop = 45;
    await act(async () => { [...host.querySelectorAll<HTMLButtonElement>(".side-link")].find((button) => button.textContent?.includes("学习记录"))!.click(); });
    const input = host.querySelector<HTMLInputElement>('input[type="search"]')!;
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, "稀有关键词");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => { host.querySelector<HTMLButtonElement>(".topic-open")!.click(); });
    expect(host.querySelector('[data-branch-id="child-1"]')?.getAttribute("aria-current")).toBe("page");
    expect(host.querySelector('[data-message-id="first-match"]')?.textContent).toContain("首个子支稀有关键词");
    expect(host.querySelector<HTMLElement>(".desktop-conversation-scroll")?.scrollTop).toBe(260);
    expect(document.activeElement).toBe(host.querySelector('[data-message-id="first-match"]'));
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("子草稿一");
    await act(async () => { host.querySelector<HTMLButtonElement>('[data-branch-id="A"]')!.click(); });
    expect(host.querySelector<HTMLElement>(".desktop-conversation-scroll")?.scrollTop).toBe(45);
    expect(host.querySelector<HTMLTextAreaElement>("textarea")?.value).toBe("根草稿");
    Reflect.deleteProperty(HTMLElement.prototype, "scrollIntoView");
  });

  it("主题标题匹配优先进入根分支，空查询也进入根分支", async () => {
    data.topics = [{ id: "A", title: "共同关键词主题", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [] },
      { id: "child", topicId: "A", parentBranchId: "A", sourceMessageId: null, createdAt: 2, messages: [{ id: "m", branchId: "child", role: "assistant", content: "共同关键词", createdAt: 2, status: "complete" }] }
    ] }];
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    const history = () => [...host.querySelectorAll<HTMLButtonElement>(".side-link")].find((button) => button.textContent?.includes("学习记录"))!;
    await act(async () => { history().click(); });
    const input = host.querySelector<HTMLInputElement>('input[type="search"]')!;
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, "共同关键词"); input.dispatchEvent(new Event("input", { bubbles: true })); });
    await act(async () => { host.querySelector<HTMLButtonElement>(".topic-open")!.click(); });
    expect(host.querySelector('[data-branch-id="A"]')?.getAttribute("aria-current")).toBe("page");
    expect(document.activeElement).toBe(host.querySelector(".chat-title"));
    await act(async () => { history().click(); });
    const empty = host.querySelector<HTMLInputElement>('input[type="search"]')!;
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(empty, ""); empty.dispatchEvent(new Event("input", { bubbles: true })); });
    await act(async () => { host.querySelector<HTMLButtonElement>(".topic-open")!.click(); });
    expect(host.querySelector('[data-branch-id="A"]')?.getAttribute("aria-current")).toBe("page");
    expect(document.activeElement).toBe(host.querySelector(".chat-title"));
  });

  it("搜索点击后目标在渲染时消失，回退到标题焦点和分支原滚动", async () => {
    data.topics = [{ id: "A", title: "主题", createdAt: 1, updatedAt: 1, rootBranchId: "A", messages: [], branches: [
      { id: "A", topicId: "A", parentBranchId: null, sourceMessageId: null, createdAt: 1, messages: [{ id: "m", branchId: "A", role: "assistant", content: "独有词", createdAt: 1, status: "complete" }] }
    ] }];
    vi.mocked(api.getState).mockImplementation(async () => data);
    await act(async () => { root.unmount(); root = createRoot(host); root.render(<DesktopWorkspace api={api} />); });
    await chooseTopic("主题");
    const area = host.querySelector<HTMLElement>(".desktop-conversation-scroll")!;
    area.scrollTop = 71;
    await act(async () => { [...host.querySelectorAll<HTMLButtonElement>(".side-link")].find((button) => button.textContent?.includes("学习记录"))!.click(); });
    const input = host.querySelector<HTMLInputElement>('input[type="search"]')!;
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, "独有词"); input.dispatchEvent(new Event("input", { bubbles: true })); });
    const result = host.querySelector<HTMLButtonElement>(".topic-open")!;
    // Simulates a concurrently removed target after selection but before the chat render commits.
    await act(async () => { result.click(); data.topics[0]!.branches![0]!.messages.splice(0); });
    expect(host.querySelector('[data-message-id="m"]')).toBeNull();
    expect(document.activeElement).toBe(host.querySelector(".chat-title"));
    expect(host.querySelector<HTMLElement>(".desktop-conversation-scroll")?.scrollTop).toBe(71);
  });
});
