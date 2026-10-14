import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { I18nProvider } from '../i18n/I18nProvider';
import { Router } from './Router';

// Route -> page mapping. HomePage mounts with a stubbed transport so the router
// test stays focused on reachability (component state is covered in HomePage.test).
function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = (init?.method ?? 'GET').toUpperCase();
      if (method === 'GET' && url.endsWith('/api/topics')) return json([]);
      if (method === 'GET' && url.endsWith('/api/settings/model-resolution')) {
        return json({ roles: { CHAT: { providerId: 'p1', modelId: 'deepseek-v4-pro', fallback: 'NONE' }, FAST: { providerId: null, modelId: null, fallback: 'EMPTY' }, TRANSLATION: { providerId: null, modelId: null, fallback: 'EMPTY' } } });
      }
      if (method === 'GET' && url.endsWith('/api/settings/model-roles')) {
        return json({ roles: { CHAT: { providerId: 'p1', modelId: 'deepseek-v4-pro', fallback: 'NONE' }, FAST: { providerId: null, modelId: null, fallback: 'EMPTY' }, TRANSLATION: { providerId: null, modelId: null, fallback: 'EMPTY' } }, budget: 'AUTO', suggestions: true, notice: 'NONE' });
      }
      if (method === 'GET' && url.endsWith('/api/providers')) return json([]);
      // T-132：`/topic/<id>/tree` 这一屏要消费服务端树布局与分支树。
      if (method === 'GET' && url.startsWith('/api/topics/t1/tree-layout')) {
        return json({
          topicId: 't1', currentBranchId: 'b-root',
          bounds: { minX: -100, minY: -400, maxX: 100, maxY: 44 },
          branches: [
            { branchId: 'b-root', kind: 'LIMB', depth: 0, rounds: 1, ownUnread: 0, hiddenDescendants: 0, hiddenUnread: 0, baseX: 0, baseY: 0, tipX: 0, tipY: -156, baseWidth: 4.6, tipWidth: 3, length: 156, direction: 0, onCurrentPath: true },
            { branchId: 'b-child', kind: 'LEAF', depth: 1, rounds: 0, ownUnread: 1, hiddenDescendants: 0, hiddenUnread: 0, baseX: 0, baseY: -156, tipX: 24, tipY: -254.4, baseWidth: 3, tipWidth: 2, length: 98.4, direction: 0.24, onCurrentPath: false },
          ],
          labels: [{ branchId: 'b-child', x: -44, y: -345.4, width: 136, height: 43, row: 0, showSummary: true, leaderX: 24, leaderY: -254.4, title: '子分支', summary: '一段摘要' }],
          hits: [{ branchId: 'b-child', source: 'LABEL', x: -44, y: -345.4, width: 136, height: 43 }],
        });
      }
      if (method === 'GET' && url.endsWith('/api/topics/t1/branches')) {
        return json({ topicId: 't1', nodes: [{ id: 'b-root', topicId: 't1', parentBranchId: null, source: null, title: '主题根', createdAtMillis: 1, updatedAtMillis: 1, latestStableSequence: 1, lastSeenStableSequence: 1, unreadCount: 0, collapsed: false, archivedAtMillis: null }] });
      }
      if (method === 'GET' && url.startsWith('/api/stats')) return json({ empty: false, timeZone: 'Asia/Shanghai', month: '2026-10', totalQuestions: 0, totalMessages: 0, currentStreakDays: 0, activeDays: [], monthCells: [] });
      throw new Error(`unexpected ${method} ${url}`);
    }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

function renderAt(path: string) {
  window.history.replaceState({}, '', path);
  return render(
    <I18nProvider>
      <Router />
    </I18nProvider>,
  );
}

describe('Router', () => {
  it('renders the home page at "/"', async () => {
    renderAt('/');
    expect(await screen.findByText('今天，想从哪个问题开始？')).toBeInTheDocument();
  });

  it('renders the real library page with the route title in the shell', async () => {
    renderAt('/library');
    expect(screen.getByRole('heading', { name: '学习库' })).toBeInTheDocument();
    expect(await screen.findByText('还没有学习主题。')).toBeInTheDocument();
  });

  it('renders the stats page at /stats and exposes the nav entry', async () => {
    renderAt('/stats');
    expect(screen.getByRole('heading', { name: '学习足迹' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '学习足迹' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '学习统计' })).toBeInTheDocument();
  });

  it('renders the providers settings page at /settings/providers', async () => {
    renderAt('/settings/providers');
    expect(screen.getByRole('heading', { name: '设置' })).toBeInTheDocument();
    expect(await screen.findByText('还没有提供商，新建一个并填入 API Key 后即可开始使用。')).toBeInTheDocument();
  });

  it('renders the model roles settings page at /settings/model-roles', async () => {
    renderAt('/settings/model-roles');
    expect(await screen.findByRole('combobox', { name: '对话' })).toBeInTheDocument();
  });

  it('falls back to the about page for an unknown settings section', async () => {
    renderAt('/settings/models');
    expect(screen.getByRole('heading', { name: '设置' })).toBeInTheDocument();
    expect(await screen.findByText('本机服务只监听 loopback，数据保存在本机，与 Android 端相互独立。')).toBeInTheDocument();
  });

  // T-132：整棵树有自己那一屏（Android `sink.openOverview` → 打开主题画布），
  // 不再是对话页里的一个布局档位。
  it('renders the topic overview screen at /topic/:id/tree', async () => {
    renderAt('/topic/t1/tree?branch=b-root');
    expect(await screen.findByText('子分支')).toBeInTheDocument();
    expect(screen.getByText('一段摘要')).toBeInTheDocument();
  });

  it('renders a 404 placeholder for an unknown path', () => {
    renderAt('/no/such/page');
    expect(screen.getByRole('heading', { name: '页面不存在' })).toBeInTheDocument();
    expect(screen.getByText('这个地址没有对应的页面，请从导航重新进入。')).toBeInTheDocument();
  });
});