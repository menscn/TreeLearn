import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { BranchTree, Conversation, Message, Topic } from '../api/types';
import type { OperationFrameSource, SseFrame } from '../api/sse';
import { I18nProvider } from '../i18n/I18nProvider';
import { readDraft } from '../store/drafts';
import { BranchWorkspace } from './BranchWorkspace';

const SSE_DIR = join(process.cwd(), '..', 'contracts', 'fixtures', 'sse');

const ROOT = 'a0b1c2d3-e4f5-4a6b-8c9d-0e1f2a3b4c5d';
const CHILD = 'c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b';

function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

function revision(id: string, answer: string) {
  return { id, messageId: 'm', revisionNo: 1, reasoningContent: '先想清楚再回答。', answerContent: answer, model: 'deepseek-v4-pro', thinkingEnabled: true, promptPolicyVersion: 'pv', createdAtMillis: 1 };
}

function userMessage(id: string, text: string, quotedText = ''): Message {
  return { id, branchId: ROOT, role: 'USER', ordinal: 0, userContent: text, quotedText, createdAtMillis: 1, activeRevisionId: null, activeOperationId: null, attachments: [], versions: [] };
}

function aiMessage(id: string, answer: string, activeOperationId: string | null = null): Message {
  return { id, branchId: ROOT, role: 'AI', ordinal: 1, userContent: null, quotedText: '', createdAtMillis: 1, activeRevisionId: 'r1', activeOperationId, attachments: [], versions: [revision('r1', answer)] };
}

const TOPIC: Topic = { id: 't1', lastVisitedBranchId: ROOT, createdAtMillis: 1, updatedAtMillis: 1 };
const TREE: BranchTree = {
  topicId: 't1',
  nodes: [
    { id: ROOT, topicId: 't1', parentBranchId: null, source: null, title: '光合作用是什么', createdAtMillis: 1, updatedAtMillis: 1, latestStableSequence: 1, lastSeenStableSequence: 1, unreadCount: 0, collapsed: false, archivedAtMillis: null },
    { id: CHILD, topicId: 't1', parentBranchId: ROOT, source: { parentBranchId: ROOT, sourceMessageId: 'src-1', sourceRevisionId: 'r1', anchor: { startInclusive: 0, endExclusive: 2, quote: '光合', hash: 'h' }, contextSnapshotId: 'snap-1' }, title: '光合作用的分支追问', createdAtMillis: 2, updatedAtMillis: 2, latestStableSequence: 1, lastSeenStableSequence: 1, unreadCount: 0, collapsed: false, archivedAtMillis: null },
  ],
};

function conversation(aMessages: Message[]): Conversation {
  return { branchId: ROOT, messages: aMessages, readingAnchor: null, recommendations: [] };
}

function loadSseFrames(name: string): SseFrame[] {
  return (JSON.parse(readFileSync(join(SSE_DIR, `${name}.json`), 'utf8')) as { frames: SseFrame[] }).frames;
}
function replaySource(frames: SseFrame[]): OperationFrameSource {
  return { subscribe: (sink) => { for (const frame of frames) sink.onFrame(frame); return () => {}; } };
}
const TREE_LAYOUT = {
  topicId: 't1',
  currentBranchId: ROOT,
  bounds: { minX: -100, minY: -400, maxX: 100, maxY: 44 },
  branches: [
    { branchId: ROOT, kind: 'LIMB', depth: 0, rounds: 1, ownUnread: 0, hiddenDescendants: 0, hiddenUnread: 0, baseX: 0, baseY: 0, tipX: 0, tipY: -156, baseWidth: 4.6, tipWidth: 3, length: 156, direction: 0, onCurrentPath: true },
    { branchId: 'b-child', kind: 'LEAF', depth: 1, rounds: 0, ownUnread: 1, hiddenDescendants: 0, hiddenUnread: 0, baseX: 0, baseY: -156, tipX: 24, tipY: -254.4, baseWidth: 3, tipWidth: 2, length: 98.4, direction: 0.24, onCurrentPath: false },
  ],
  labels: [
    { branchId: 'b-child', x: -44, y: -345.4, width: 136, height: 43, row: 0, showSummary: true, leaderX: 24, leaderY: -254.4, title: '子分支', summary: '一段摘要' },
  ],
  hits: [
    { branchId: 'b-child', source: 'LABEL', x: -44, y: -345.4, width: 136, height: 43 },
  ],
};
const stillStreaming = replaySource([{ id: '1', event: 'snapshot', data: { operationId: 'op-stop', seq: 1, state: 'SUBMITTING', reasoningContent: '', answerContent: '' } }]);

interface Handlers {
  topic?: () => Response;
  conversation?: (branchId: string) => Response;
  cancel?: () => Response;
  retry?: () => Response;
  send?: () => Response;
  resolve?: () => unknown;
  tree?: () => Response;
  branchTree?: () => Response;
}
interface Call { method: string; url: string; body: Record<string, unknown> | null }

function installFetch(handlers: Handlers = {}): Call[] {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    calls.push({ method, url, body: typeof init?.body === 'string' ? JSON.parse(init.body) : null });
    if (method === 'GET' && url === '/api/topics/t1') return handlers.topic ? handlers.topic() : json(TOPIC);
    if (method === 'GET' && url === '/api/topics/t1/branches') return handlers.branchTree ? handlers.branchTree() : json(TREE);
    if (method === 'GET' && url.startsWith('/api/topics/t1/tree-layout')) return handlers.tree ? handlers.tree() : json(TREE_LAYOUT);
    if (method === 'GET' && url === '/api/settings/model-resolution') return json(handlers.resolve ? handlers.resolve() : { roles: { CHAT: { providerId: 'p', modelId: 'deepseek-v4-pro', fallback: 'NONE' }, FAST: { providerId: null, modelId: null, fallback: 'EMPTY' }, TRANSLATION: { providerId: null, modelId: null, fallback: 'EMPTY' } } });
    if (method === 'GET' && /^\/api\/branches\/[^/]+\/messages$/.test(url)) return handlers.conversation ? handlers.conversation(url.split('/')[3]) : json(conversation([userMessage('u1', '光合作用是什么？'), aiMessage('a1', '光合作用是植物利用光能把二氧化碳和水合成有机物的过程。')]));
    if (method === 'POST' && /^\/api\/operations\/[^/]+\/cancel$/.test(url)) return handlers.cancel ? handlers.cancel() : json({ id: 'op-stop', state: 'CANCELED' });
    if (method === 'POST' && /^\/api\/messages\/[^/]+\/retry$/.test(url)) return handlers.retry ? handlers.retry() : json({ message: aiMessage('a1', '重试中'), operation: { id: 'op-retry' } });
    if (method === 'POST' && /^\/api\/branches\/[^/]+\/messages$/.test(url)) return handlers.send ? handlers.send() : json({ userMessage: userMessage('u2', '追问'), aiMessage: aiMessage('a2', '回答', 'op-new'), operation: { id: 'op-new', messageId: 'a2' } });
    if (method === 'POST' && /^\/api\/branches\/[^/]+\/seen$/.test(url)) return json({ branchId: url.split('/')[3] });
    if (method === 'PUT' && /^\/api\/branches\/[^/]+\/reading-position$/.test(url)) return json({ branchId: url.split('/')[3] });
    throw new Error(`unexpected ${method} ${url}`);
  }));
  return calls;
}

// T-127：jsdom 没有 IntersectionObserver，装一个可手动触发进入视口的探针。
interface ObserverProbe { enter: (elementId: string) => void; observed: () => string[] }

function installIntersectionObserver(): ObserverProbe {
  const observers: { targets: Element[]; fire: (target: Element) => void }[] = [];
  class MockObserver {
    private readonly targets: Element[] = [];
    constructor(private readonly callback: (entries: { target: Element; isIntersecting: boolean }[]) => void) {
      observers.push({ targets: this.targets, fire: (target) => this.callback([{ target, isIntersecting: true }]) });
    }
    // 真实浏览器在 observe 时立刻回调一次当前状态；这里按「尚未进入视口」投递，
    // 否则「进入分支即已读」这种捷径在测试里根本不会被触发，断言就成了恒真。
    observe(target: Element) { this.targets.push(target); this.callback([{ target, isIntersecting: false }]); }
    unobserve() { }
    disconnect() { this.targets.length = 0; }
    takeRecords() { return []; }
  }
  vi.stubGlobal('IntersectionObserver', MockObserver as unknown as typeof IntersectionObserver);
  return {
    observed: () => observers.flatMap((observer) => observer.targets.map((target) => target.id)),
    enter: (elementId) => {
      for (const observer of observers) {
        const target = observer.targets.find((element) => element.id === elementId);
        if (target !== undefined) observer.fire(target);
      }
    },
  };
}

// 阅读位置恢复只能靠 scrollIntoView 的目标来观测（jsdom 没有真实布局）。
function installScrollSpy(): string[] {
  const scrolled: string[] = [];
  Element.prototype.scrollIntoView = function record(this: Element) { scrolled.push(this.id); };
  return scrolled;
}

function workspace(streamSource: OperationFrameSource | undefined, branchId: string | null) {
  return (
    <I18nProvider>
      <BranchWorkspace topicId="t1" branchId={branchId} streamSource={streamSource} />
    </I18nProvider>
  );
}

function renderWorkspace(streamSource?: OperationFrameSource, branchId: string | null = null) {
  return render(workspace(streamSource, branchId));
}

beforeEach(() => {
  localStorage.clear();
});
afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

describe('BranchWorkspace reading layout (A)', () => {
  it('shows the root title, the breadcrumb branch and the committed messages', async () => {
    installFetch();
    renderWorkspace();
    expect(await screen.findByRole('heading', { name: '光合作用是什么' })).toBeInTheDocument();
    expect(screen.getByText('光合作用是什么？')).toBeInTheDocument();
    expect(screen.getByText('光合作用是植物利用光能把二氧化碳和水合成有机物的过程。')).toBeInTheDocument();
  });

  it('renders a USER quote block only when quotedText is non-empty', async () => {
    installFetch({ conversation: () => json(conversation([userMessage('u1', '追问', '被引用的回答片段'), aiMessage('a1', '答')])) });
    renderWorkspace();
    const quote = await screen.findByText('被引用的回答片段');
    expect(quote.tagName.toLowerCase()).toBe('blockquote');
  });

  it('escapes HTML in message content instead of injecting it', async () => {
    const payload = '<script>alert(1)</script><img src=x onerror=alert(2)>';
    installFetch({ conversation: () => json(conversation([userMessage('u1', '问题'), aiMessage('a1', payload)])) });
    const { container } = renderWorkspace();
    await screen.findByText(payload);
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
  });

  it('surfaces a contract error code as translated copy when loading fails', async () => {
    installFetch({ topic: () => json({ error: { code: 'NOT_FOUND', retryable: false, params: { resource: 'topic' } }, requestId: 'r' }, 404) });
    renderWorkspace();
    expect(await screen.findByRole('alert')).toHaveTextContent('找不到请求的资源，请刷新后重试。');
  });
});

describe('BranchWorkspace streaming and actions', () => {
  it('appends streamed deltas and cancels the active operation with a commandId', async () => {
    const calls = installFetch({
      conversation: () => json(conversation([userMessage('u1', '问题'), aiMessage('a1', '旧回答', 'op-stop')])),
      cancel: () => json({ id: 'op-stop', state: 'CANCELED' }),
    });
    renderWorkspace(stillStreaming);
    const stop = await screen.findByRole('button', { name: '停止生成' });
    expect(screen.getByText('正在生成…')).toBeInTheDocument();

    fireEvent.click(stop);
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === '/api/operations/op-stop/cancel')).toBe(true));
    const cancelCall = calls.find((call) => call.url === '/api/operations/op-stop/cancel');
    expect(typeof cancelCall?.body?.commandId).toBe('string');
    expect(String(cancelCall?.body?.commandId).length).toBeGreaterThan(0);
  });

  it('offers an in-place retry after an interrupted stream and posts model + thinking', async () => {
    const frames = loadSseFrames('stream-interrupted');
    const operationId = (frames[0].data as { operationId: string }).operationId;
    const calls = installFetch({
      conversation: () => json(conversation([userMessage('u1', '问题'), aiMessage('a1', '部分回答', operationId)])),
      retry: () => json({ message: aiMessage('a1', '重试中'), operation: { id: 'op-retry' } }),
    });
    renderWorkspace(replaySource(frames));
    const retry = await screen.findByRole('button', { name: '重试' });
    expect(await screen.findByText('生成已中断')).toBeInTheDocument();

    fireEvent.click(retry);
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === '/api/messages/a1/retry')).toBe(true));
    const retryCall = calls.find((call) => call.url === '/api/messages/a1/retry');
    expect(typeof retryCall?.body?.commandId).toBe('string');
    expect(retryCall?.body?.model).toBe('deepseek-v4-pro');
    expect(retryCall?.body?.thinkingEnabled).toBe(false);
  });

  it('renders the mapped error copy for a failed stream and offers retry', async () => {
    const frames = loadSseFrames('stream-failed');
    const operationId = (frames[0].data as { operationId: string }).operationId;
    installFetch({ conversation: () => json(conversation([userMessage('u1', '问题'), aiMessage('a1', '部分回答', operationId)])) });
    renderWorkspace(replaySource(frames));
    expect(await screen.findByText('请求过于频繁，已被限流，请稍后重试。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
  });

  it('falls back to the generic failure copy for an unknown error code', async () => {
    const operationId = 'op-unknown';
    const frames: SseFrame[] = [{ id: '1', event: 'failed', data: { operationId, seq: 1, errorCode: 'NOT_A_REAL_CODE', params: {} } }];
    installFetch({ conversation: () => json(conversation([userMessage('u1', '问题'), aiMessage('a1', '部分回答', operationId)])) });
    renderWorkspace(replaySource(frames));
    expect(await screen.findByText('生成失败')).toBeInTheDocument();
  });
});

describe('BranchWorkspace follow-up composer', () => {
  it('sends a follow-up message with commandId, text, quote and thinking', async () => {
    const calls = installFetch({ send: () => json({ userMessage: userMessage('u2', '追问'), aiMessage: aiMessage('a2', '回答', 'op-new'), operation: { id: 'op-new', messageId: 'a2' } }) });
    renderWorkspace();
    const input = await screen.findByLabelText('继续追问…');
    fireEvent.change(input, { target: { value: '那光反应在哪里进行？' } });
    fireEvent.click(screen.getByRole('button', { name: '发送追问' }));

    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === `/api/branches/${ROOT}/messages`)).toBe(true));
    const send = calls.find((call) => call.method === 'POST' && call.url === `/api/branches/${ROOT}/messages`);
    expect(typeof send?.body?.commandId).toBe('string');
    expect(send?.body?.text).toBe('那光反应在哪里进行？');
    expect(send?.body?.model).toBe('deepseek-v4-pro');
    expect(send?.body?.thinkingEnabled).toBe(false);
  });
});

describe('BranchWorkspace layout switching', () => {
  it('persists the layout and renders the C tree canvas from the server layout', async () => {
    installFetch();
    renderWorkspace();
    await screen.findByRole('heading', { name: '光合作用是什么' });

    fireEvent.click(screen.getByRole('button', { name: '并排分支' }));
    await waitFor(() => expect(localStorage.getItem('workspace:layout')).toBe('B'));

    fireEvent.click(screen.getByRole('button', { name: '树图' }));
    expect(await screen.findByText('子分支')).toBeInTheDocument();
    expect(screen.getByText('一段摘要')).toBeInTheDocument();
    expect(localStorage.getItem('workspace:layout')).toBe('C');

    // 触达区点击 → 导航到该分支（/topic/t1?branch=b-child）。
    fireEvent.click(screen.getByRole('button', { name: '子分支' }));
    await waitFor(() => expect(window.location.search).toContain('branch=b-child'));
    // ownUnread=1 的叶名牌带未读徽标。
    expect(document.querySelector('[data-testid="unread-badge-b-child"]')).not.toBeNull();
  });

  // 原「进入分支即标记已读」断言与 Android ViewportSeen 口径相反，已由下方 T-127 describe 取代。

  it('shows the empty note when the server layout has no branches', async () => {
    installFetch({ tree: () => json({ ...TREE_LAYOUT, branches: [], labels: [], hits: [] }) });
    renderWorkspace();
    await screen.findByRole('heading', { name: '光合作用是什么' });
    fireEvent.click(screen.getByRole('button', { name: '树图' }));
    expect(await screen.findByText('暂无可绘制的分支树。')).toBeInTheDocument();
  });

  it('shows the fork source side by side when layout B is chosen on a forked branch', async () => {
    installFetch({
      conversation: (branchId) => branchId === CHILD
        ? json({ branchId: CHILD, messages: [userMessage('c0', '那光反应在哪里进行？'), aiMessage('c1', '光反应在类囊体薄膜上进行。')], readingAnchor: null, recommendations: [] })
        : json(conversation([userMessage('s0', '光合作用是什么？'), aiMessage('src-1', '光合作用是植物利用光能把二氧化碳和水合成有机物的过程。')])),
    });
    localStorage.setItem('workspace:layout', 'B');
    renderWorkspace(undefined, CHILD);
    expect(await screen.findByRole('heading', { name: '光合作用的分支追问' })).toBeInTheDocument();
    expect(await screen.findByText(/合成有机物的过程。$/)).toBeInTheDocument();
    expect(await screen.findByText(/snap-1/)).toBeInTheDocument();
  });
});

// T-126 生成终态闭环：终态后必须解锁追问、读回权威内容，且流式正文落在回答位里。
describe('BranchWorkspace generation closure (T-126)', () => {
  it('unlocks the follow-up composer and reads the authoritative answer back after a stable terminal', async () => {
    const frames = loadSseFrames('stream-stable');
    const operationId = (frames[0].data as { operationId: string }).operationId;
    let conversationCalls = 0;
    installFetch({
      conversation: () => {
        conversationCalls += 1;
        return conversationCalls === 1
          ? json(conversation([userMessage('u1', '问题'), aiMessage('a1', '旧回答', operationId)]))
          : json(conversation([userMessage('u1', '问题'), aiMessage('a1', '权威读回的答案')]));
      },
    });
    renderWorkspace(replaySource(frames));

    await waitFor(() => expect(conversationCalls).toBeGreaterThan(1));
    await waitFor(() => expect(document.getElementById('msg-a1')?.textContent).toContain('权威读回的答案'));
    expect(document.getElementById('msg-a1')?.textContent).not.toContain('把二氧化碳和水合成有机物并释放氧气的过程。');

    fireEvent.change(screen.getByLabelText('继续追问…'), { target: { value: '再问一句' } });
    await waitFor(() => expect(screen.getByRole('button', { name: '发送追问' })).toBeEnabled());
  });

  it('renders the streamed answer inside the answer slot, not in a bottom panel', async () => {
    const frames: SseFrame[] = [
      { id: '1', event: 'snapshot', data: { operationId: 'op-live', seq: 1, state: 'STREAMING_ANSWER', reasoningContent: '', answerContent: '' } },
      { id: '2', event: 'answer_delta', data: { operationId: 'op-live', seq: 2, delta: '流式落在回答位里的正文。' } },
    ];
    installFetch({ conversation: () => json(conversation([userMessage('u1', '问题'), aiMessage('a1', '', 'op-live')])) });
    renderWorkspace(replaySource(frames));

    await waitFor(() => expect(document.getElementById('msg-a1')?.textContent).toContain('流式落在回答位里的正文。'));
    expect(document.querySelector('.workspace__stream')).toBeNull();
  });

  it('keeps the partial answer after a canceled terminal and unlocks the composer', async () => {
    const frames = loadSseFrames('stream-canceled');
    const operationId = (frames[0].data as { operationId: string }).operationId;
    let conversationCalls = 0;
    installFetch({
      conversation: () => {
        conversationCalls += 1;
        return conversationCalls === 1
          ? json(conversation([userMessage('u1', '问题'), aiMessage('a1', '', operationId)]))
          : json(conversation([userMessage('u1', '问题'), aiMessage('a1', '取消后保留的部分回答')]));
      },
    });
    renderWorkspace(replaySource(frames));

    await waitFor(() => expect(document.getElementById('msg-a1')?.textContent).toContain('取消后保留的部分回答'));
    fireEvent.change(screen.getByLabelText('继续追问…'), { target: { value: '继续问' } });
    await waitFor(() => expect(screen.getByRole('button', { name: '发送追问' })).toBeEnabled());
  });
});

// T-127 未读口径与阅读恢复：未读只在新回答真正进入视口后清除（Android ViewportSeen.java:38-51）；
// 打开分支按 readingAnchor 回到上次位置，无锚点或锚点失效则到最新端（ReadingSession.java:46-54）。
describe('BranchWorkspace unread and reading restore (T-127)', () => {
  const unreadTree = (unreadCount: number, latestStableSequence = 5) => json({
    ...TREE,
    nodes: TREE.nodes.map((node) => (node.id === ROOT ? { ...node, unreadCount, latestStableSequence } : node)),
  });

  it('does not mark the branch seen on entry, only after the newest answer enters the viewport', async () => {
    const calls = installFetch({ branchTree: () => unreadTree(2) });
    const observer = installIntersectionObserver();
    renderWorkspace();
    await screen.findByRole('heading', { name: '光合作用是什么' });
    await waitFor(() => expect(observer.observed()).toContain('msg-a1'));
    // 进入即发 seen 是 Android 明令否掉的捷径：没上屏的回答不能被标成已读。
    expect(calls.some((call) => call.method === 'POST' && call.url === `/api/branches/${ROOT}/seen`)).toBe(false);

    await act(async () => { observer.enter('msg-a1'); });
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === `/api/branches/${ROOT}/seen`)).toBe(true));
    expect(calls.find((call) => call.url === `/api/branches/${ROOT}/seen`)?.body).toMatchObject({ seenThroughSequence: 5 });
  });

  it('never sends seen for a branch that has no unread answers', async () => {
    const calls = installFetch({ branchTree: () => unreadTree(0) });
    const observer = installIntersectionObserver();
    renderWorkspace();
    await screen.findByRole('heading', { name: '光合作用是什么' });
    await waitFor(() => expect(document.getElementById('msg-a1')).not.toBeNull());
    await act(async () => { observer.enter('msg-a1'); });
    await waitFor(() => expect(document.getElementById('msg-a1')).not.toBeNull());
    expect(calls.some((call) => call.method === 'POST' && call.url === `/api/branches/${ROOT}/seen`)).toBe(false);
  });

  it('restores the reading position to the anchored message', async () => {
    const scrolled = installScrollSpy();
    installFetch({
      conversation: () => json({
        branchId: ROOT,
        messages: [userMessage('u1', '光合作用是什么？'), aiMessage('a1', '第一条回答'), aiMessage('a2', '最新一条回答')],
        readingAnchor: { messageId: 'a1', revisionId: 'r1', listItemOffset: 0, viewportOffset: 0 },
        recommendations: [],
      }),
    });
    renderWorkspace();
    await waitFor(() => expect(scrolled).toContain('msg-a1'));
  });

  it('opens at the live end when there is no anchor or the anchor points nowhere', async () => {
    const scrolled = installScrollSpy();
    installFetch({
      conversation: () => json({
        branchId: ROOT,
        messages: [userMessage('u1', '光合作用是什么？'), aiMessage('a1', '第一条回答'), aiMessage('a2', '最新一条回答')],
        readingAnchor: { messageId: 'gone', revisionId: 'r1', listItemOffset: 0, viewportOffset: 0 },
        recommendations: [],
      }),
    });
    renderWorkspace();
    await waitFor(() => expect(scrolled).toContain('msg-a2'));

    scrolled.length = 0;
    installFetch({
      conversation: () => json({
        branchId: ROOT,
        messages: [userMessage('u1', '光合作用是什么？'), aiMessage('a1', '第一条回答'), aiMessage('a2', '最新一条回答')],
        readingAnchor: null,
        recommendations: [],
      }),
    });
    renderWorkspace();
    await waitFor(() => expect(scrolled).toContain('msg-a2'));
  });
});

describe('BranchWorkspace per-branch follow-up drafts', () => {
  function twoBranchFetch(handlers: Handlers = {}) {
    return installFetch({
      ...handlers,
      conversation: (branchId) => branchId === CHILD
        ? json({ branchId: CHILD, messages: [userMessage('c0', '子分支问题'), aiMessage('c1', '子分支回答')], readingAnchor: null, recommendations: [] })
        : json(conversation([userMessage('u1', '根分支问题'), aiMessage('a1', '根分支回答')])),
    });
  }

  it('keeps a separate draft per branch and restores it when switching back', async () => {
    twoBranchFetch();
    const view = renderWorkspace(undefined, ROOT);
    fireEvent.change(await screen.findByLabelText('继续追问…'), { target: { value: '根分支草稿' } });

    view.rerender(workspace(undefined, CHILD));
    const childInput = await screen.findByLabelText('继续追问…');
    await waitFor(() => expect((childInput as HTMLTextAreaElement).value).toBe(''));
    fireEvent.change(childInput, { target: { value: '子分支草稿' } });

    view.rerender(workspace(undefined, ROOT));
    const rootInput = await screen.findByLabelText('继续追问…');
    await waitFor(() => expect((rootInput as HTMLTextAreaElement).value).toBe('根分支草稿'));
    await waitFor(() => {
      expect(readDraft(`draft:${ROOT}`)).toBe('根分支草稿');
      expect(readDraft(`draft:${CHILD}`)).toBe('子分支草稿');
    });
  });

  it('restores the branch draft after a remount', async () => {
    twoBranchFetch();
    const first = renderWorkspace(undefined, ROOT);
    fireEvent.change(await screen.findByLabelText('继续追问…'), { target: { value: '未发送草稿' } });
    first.unmount();

    renderWorkspace(undefined, ROOT);
    const input = (await screen.findByLabelText('继续追问…')) as HTMLTextAreaElement;
    await waitFor(() => expect(input.value).toBe('未发送草稿'));
  });

  it('clears the branch draft after a successful follow-up', async () => {
    const calls = twoBranchFetch({ send: () => json({ userMessage: userMessage('u2', '追问'), aiMessage: aiMessage('a2', '回答', 'op-new'), operation: { id: 'op-new', messageId: 'a2' } }) });
    renderWorkspace(undefined, ROOT);
    fireEvent.change(await screen.findByLabelText('继续追问…'), { target: { value: '要发送的草稿' } });
    fireEvent.click(screen.getByRole('button', { name: '发送追问' }));

    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === `/api/branches/${ROOT}/messages`)).toBe(true));
    await waitFor(() => expect(localStorage.getItem(`draft:${ROOT}`)).toBeNull());
    expect((screen.getByLabelText('继续追问…') as HTMLTextAreaElement).value).toBe('');
  });
});