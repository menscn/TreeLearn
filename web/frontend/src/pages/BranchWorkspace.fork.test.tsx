import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { BranchTree, Conversation, Message, Topic } from '../api/types';
import { I18nProvider } from '../i18n/I18nProvider';
import { BranchWorkspace } from './BranchWorkspace';

// 分叉入口（契约 §6.3 POST /api/branches/{id}/forks）与回答动作行新增项的行为测试。
// 三个入口共用一条 store 封装 forkBranch；断言打到端点的请求体与跳转目标。
const ROOT = 'a0b1c2d3-e4f5-4a6b-8c9d-0e1f2a3b4c5d';
const CHILD = 'c1a2b3c4-d5e6-4f70-8a9b-0c1d2e3f4a5b';
const NEW = 'd2b3c4d5-e6f7-4a80-9b0c-1d2e3f4a5b6c';

function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

function revision(id: string, answer: string) {
  return { id, messageId: 'a1', revisionNo: 1, reasoningContent: '', answerContent: answer, model: 'deepseek-v4-pro', thinkingEnabled: false, promptPolicyVersion: 'pv', createdAtMillis: 1 };
}

function userMessage(id: string, text: string): Message {
  return { id, branchId: ROOT, role: 'USER', ordinal: 0, userContent: text, quotedText: '', createdAtMillis: 1, activeRevisionId: null, activeOperationId: null, attachments: [], versions: [] };
}

function aiMessage(id: string, answer: string): Message {
  return { id, branchId: ROOT, role: 'AI', ordinal: 1, userContent: null, quotedText: '', createdAtMillis: 1, activeRevisionId: 'r1', activeOperationId: null, attachments: [], versions: [revision('r1', answer)] };
}

const TOPIC: Topic = { id: 't1', lastVisitedBranchId: ROOT, createdAtMillis: 1, updatedAtMillis: 1 };

function tree(): BranchTree {
  return {
    topicId: 't1',
    nodes: [
      { id: ROOT, topicId: 't1', parentBranchId: null, source: null, title: '光合作用是什么', createdAtMillis: 1, updatedAtMillis: 1, latestStableSequence: 1, lastSeenStableSequence: 1, unreadCount: 0, collapsed: false, archivedAtMillis: null },
      { id: CHILD, topicId: 't1', parentBranchId: ROOT, source: { parentBranchId: ROOT, sourceMessageId: 'a1', sourceRevisionId: 'r1', anchor: WHOLE_ANCHOR, contextSnapshotId: 'snap-1' }, title: '光合作用的分支追问', createdAtMillis: 2, updatedAtMillis: 2, latestStableSequence: 1, lastSeenStableSequence: 1, unreadCount: 0, collapsed: false, archivedAtMillis: null },
    ],
  };
}

const WHOLE_ANCHOR = { startInclusive: null, endExclusive: null, quote: null, hash: null };

const NEW_BRANCH = { id: NEW, topicId: 't1', parentBranchId: ROOT, source: { parentBranchId: ROOT, sourceMessageId: 'a1', sourceRevisionId: 'r1', anchor: WHOLE_ANCHOR, contextSnapshotId: 'snap-new' }, title: null, createdAtMillis: 3, updatedAtMillis: 3, latestStableSequence: 0, lastSeenStableSequence: 0, unreadCount: 0, collapsed: false, archivedAtMillis: null };

function conversation(recommendations: string[] = []): Conversation {
  return { branchId: ROOT, messages: [userMessage('u1', '光合作用是什么？'), aiMessage('a1', '光合作用是植物利用光能把二氧化碳和水合成有机物的过程。')], readingAnchor: null, recommendations };
}

interface Call { method: string; url: string; body: Record<string, unknown> | null }

function installFetch(handlers: { conversation?: () => Response } = {}): Call[] {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    calls.push({ method, url, body: typeof init?.body === 'string' ? JSON.parse(init.body) : null });
    if (method === 'GET' && url === '/api/topics/t1') return json(TOPIC);
    if (method === 'GET' && url === '/api/topics/t1/branches') return json(tree());
    if (method === 'GET' && url === '/api/settings/model-resolution') return json({ roles: { CHAT: { providerId: 'p', modelId: 'deepseek-v4-pro', fallback: 'NONE' }, FAST: { providerId: null, modelId: null, fallback: 'EMPTY' }, TRANSLATION: { providerId: null, modelId: null, fallback: 'EMPTY' } } });
    if (method === 'GET' && /^\/api\/branches\/[^/]+\/messages$/.test(url)) return handlers.conversation ? handlers.conversation() : json(conversation());
    if (method === 'POST' && /^\/api\/branches\/[^/]+\/forks$/.test(url)) return json({ branch: NEW_BRANCH, source: NEW_BRANCH.source, bootstrap: null });
    throw new Error(`unexpected ${method} ${url}`);
  }));
  return calls;
}

function renderWorkspace() {
  return render(
    <I18nProvider>
      <BranchWorkspace topicId="t1" branchId={null} streamSource={null} />
    </I18nProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
});
afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

describe('BranchWorkspace fork entries', () => {
  it('forks the whole answer from the action row and navigates to the new branch', async () => {
    const calls = installFetch();
    renderWorkspace();
    fireEvent.click(await screen.findByTestId('fork-a1'));
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === `/api/branches/${ROOT}/forks`)).toBe(true));
    const fork = calls.find((call) => call.url === `/api/branches/${ROOT}/forks`);
    expect(fork?.body).toMatchObject({ sourceMessageId: 'a1', sourceRevisionId: 'r1', anchor: null });
    expect(typeof fork?.body?.commandId).toBe('string');
    await waitFor(() => expect(window.location.pathname + window.location.search).toBe(`/topic/t1?branch=${NEW}`));
  });

  it('forks a recommended question as the first message with model and thinking', async () => {
    const calls = installFetch({ conversation: () => json(conversation(['光合作用需要哪些条件？'])) });
    renderWorkspace();
    fireEvent.click(await screen.findByTestId('fork-recommendation-0'));
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === `/api/branches/${ROOT}/forks`)).toBe(true));
    const fork = calls.find((call) => call.url === `/api/branches/${ROOT}/forks`);
    expect(fork?.body).toMatchObject({ sourceMessageId: 'a1', sourceRevisionId: 'r1', question: '光合作用需要哪些条件？', model: 'deepseek-v4-pro', thinkingEnabled: false, anchor: null });
    await waitFor(() => expect(window.location.pathname + window.location.search).toBe(`/topic/t1?branch=${NEW}`));
  });

  it('lists child branches of an answer and navigates into one', async () => {
    installFetch();
    renderWorkspace();
    fireEvent.click(await screen.findByTestId('children-a1'));
    expect(await screen.findByText('光合作用的分支追问')).toBeInTheDocument();
    fireEvent.click(screen.getByText('光合作用的分支追问'));
    await waitFor(() => expect(window.location.pathname + window.location.search).toBe(`/topic/t1?branch=${CHILD}`));
  });

  it('copies the whole answer to the clipboard', async () => {
    installFetch();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(window.navigator, 'clipboard', { value: { writeText }, configurable: true });
    renderWorkspace();
    fireEvent.click(await screen.findByRole('button', { name: '复制回答' }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('光合作用是植物利用光能把二氧化碳和水合成有机物的过程。'));
  });
});
