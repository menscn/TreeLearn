import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Branch, BranchTree, SearchResult, TopicSummary } from '../api/types';
import { I18nProvider } from '../i18n/I18nProvider';
import { formatDateTime, setDeviceTimeZone } from '../platform/datetime';
import { LibraryPage } from './LibraryPage';

// Test expectations may assert localized output; production code may not
// hardcode it (enforced by scripts/check-user-copy-i18n.mjs).
const TOPIC: TopicSummary = { topicId: 't1', rootTitle: '光合作用', rootQuestion: '光合作用是什么？', displayTitle: '光合作用', lastUsedAtMillis: 1700000600000 };

function branch(id: string, parentBranchId: string | null, unreadCount: number, archivedAtMillis: number | null = null, title: string | null = null): Branch {
  return { id, topicId: 't1', parentBranchId, source: null, title, createdAtMillis: 1, updatedAtMillis: 1700000600000, latestStableSequence: 0, lastSeenStableSequence: 0, unreadCount, collapsed: false, archivedAtMillis };
}

const TREE: BranchTree = { topicId: 't1', nodes: [branch('b1', null, 0), branch('b2', 'b1', 1)] };

const SEARCH: SearchResult = {
  query: '光合',
  hits: [
    { field: 'TITLE', topicId: 't1', branchId: 'b1', messageId: null, messageOrdinal: -1, revisionId: null, revisionNo: 0, snippet: '光合作用是什么', start: 0, end: 2 },
    { field: 'ANCHOR', topicId: 't1', branchId: 'b2', messageId: 'm1', messageOrdinal: 0, revisionId: 'r1', revisionNo: 1, snippet: '植物如何利用光能', start: 0, end: 2 },
    { field: 'ANSWER', topicId: 't1', branchId: 'b1', messageId: 'm2', messageOrdinal: 1, revisionId: 'r2', revisionNo: 1, snippet: '光合作用是植物利用光能', start: 0, end: 2 },
  ],
};

function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

interface Call { method: string; url: string; body: Record<string, unknown> | null }

function installFetch(handlers: { topics?: () => Response; tree?: () => Response; search?: () => Response; restore?: () => Response } = {}): Call[] {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    calls.push({ method, url, body: typeof init?.body === 'string' ? JSON.parse(init.body) : null });
    if (method === 'GET' && url === '/api/topics') return handlers.topics ? handlers.topics() : json([TOPIC]);
    if (method === 'GET' && url === '/api/topics/t1/branches') return handlers.tree ? handlers.tree() : json(TREE);
    if (method === 'GET' && url.startsWith('/api/search')) return handlers.search ? handlers.search() : json(SEARCH);
    if (method === 'POST' && /^\/api\/branches\/[^/]+\/restore$/.test(url)) return handlers.restore ? handlers.restore() : json(branch('arc', 'b1', 0));
    throw new Error(`unexpected ${method} ${url}`);
  }));
  return calls;
}

function renderLibrary(locale: 'zh-CN' | 'en-US' = 'zh-CN') {
  return render(
    <I18nProvider initialLocale={locale}>
      <LibraryPage />
    </I18nProvider>,
  );
}

function submitSearch(value: string) {
  // 用 searchbox 角色唯一锚定输入框：页面的搜索 section 与 input 共用同一占位文案作为
  // 可访问名称，getByLabelText 会同时命中两者（region + searchbox）。
  const input = screen.getByRole('searchbox');
  fireEvent.change(input, { target: { value } });
  fireEvent.submit(input.closest('form') as HTMLFormElement);
}

beforeEach(() => {
  localStorage.clear();
});
afterEach(() => {
  vi.unstubAllGlobals();
  setDeviceTimeZone(null);
  window.history.replaceState({}, '', '/');
});

describe('LibraryPage search', () => {
  it('groups hits into topic, branch and message scopes with highlighted ranges', async () => {
    installFetch();
    renderLibrary();
    submitSearch('光合');
    expect(await screen.findByRole('heading', { name: '主题' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '分支' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '问答' })).toBeInTheDocument();
    const marks = document.querySelectorAll('mark.library__mark');
    expect(marks).toHaveLength(3);
    expect(marks[0].textContent).toBe('光合');
  });

  it('shows the empty hint before a query and the no-results copy for no hits', async () => {
    installFetch({ search: () => json({ query: 'zzz', hits: [] }) });
    renderLibrary();
    expect(await screen.findByText('输入关键词，搜索你的主题、分支与回答。')).toBeInTheDocument();
    submitSearch('zzz');
    expect(await screen.findByText('没有找到匹配的内容，换个关键词试试。')).toBeInTheDocument();
  });

  it('does not call the search endpoint for a blank query', async () => {
    const calls = installFetch();
    renderLibrary();
    await screen.findByText('光合作用');
    submitSearch('   ');
    expect(calls.some((call) => call.url.startsWith('/api/search'))).toBe(false);
  });

  it('focuses the search input on Ctrl/Cmd+K', async () => {
    installFetch();
    renderLibrary();
    const input = await screen.findByRole('searchbox');
    fireEvent.keyDown(document, { key: 'k', ctrlKey: true });
    expect(document.activeElement).toBe(input);
  });

  it('surfaces a contract error code as translated copy when search fails', async () => {
    installFetch({ search: () => json({ error: { code: 'RATE_LIMITED', retryable: true, params: { httpStatus: 429 } }, requestId: 'r' }, 503) });
    renderLibrary();
    submitSearch('光合');
    expect(await screen.findByRole('alert')).toHaveTextContent('请求过于频繁，已被限流，请稍后重试。');
  });
});

describe('LibraryPage topics and archive', () => {
  it('lists topics with displayTitle and the unread rollup from the branch tree', async () => {
    installFetch();
    renderLibrary();
    expect(await screen.findByText('光合作用')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('renders the last-used time by the current device time zone', async () => {
    installFetch();
    setDeviceTimeZone('Asia/Shanghai');
    const view = renderLibrary();
    const shanghai = formatDateTime(TOPIC.lastUsedAtMillis, 'zh-CN', 'Asia/Shanghai');
    expect(await screen.findByText(shanghai)).toBeInTheDocument();
    view.unmount();

    setDeviceTimeZone('America/Los_Angeles');
    renderLibrary();
    const losAngeles = formatDateTime(TOPIC.lastUsedAtMillis, 'zh-CN', 'America/Los_Angeles');
    expect(await screen.findByText(losAngeles)).toBeInTheDocument();
    expect(losAngeles).not.toBe(shanghai);
  });

  it('lists archived subtree roots and restores one with a commandId', async () => {
    const calls = installFetch({ tree: () => json({ topicId: 't1', nodes: [branch('b1', null, 0), branch('arc', 'b1', 0, 1700000000000, '已归档分支')] }) });
    renderLibrary();
    expect(await screen.findByText('已归档分支')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '恢复分支' }));
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === '/api/branches/arc/restore')).toBe(true));
    const restore = calls.find((call) => call.url === '/api/branches/arc/restore');
    expect(typeof restore?.body?.commandId).toBe('string');
  });

  it('renders the two locales without hardcoded copy', async () => {
    installFetch();
    renderLibrary('en-US');
    expect(await screen.findByText('All topics')).toBeInTheDocument();
  });
});
