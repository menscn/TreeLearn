import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Stats } from '../api/types';
import { I18nProvider } from '../i18n/I18nProvider';
import { formatMonthLabel } from '../platform/datetime';
import { StatsPage } from './StatsPage';

// Test expectations may assert localized output; production code may not
// hardcode it (enforced by scripts/check-user-copy-i18n.mjs).
const STATS: Stats = { empty: false, timeZone: 'Asia/Shanghai', month: '2026-10', totalQuestions: 42, totalMessages: 88, currentStreakDays: 5, activeDays: ['2026-10-03'], monthCells: [-1, -1, 1, 0, 1] };
const EMPTY_MONTH: Stats = { empty: false, timeZone: 'Asia/Shanghai', month: '2026-01', totalQuestions: 0, totalMessages: 0, currentStreakDays: 0, activeDays: [], monthCells: [-1, -1, 0] };

function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

interface Call { url: string }

function installFetch(stats: Stats = STATS): Call[] {
  const calls: Call[] = [];
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    calls.push({ url });
    if (url.startsWith('/api/stats')) return json(stats);
    throw new Error(`unexpected ${url}`);
  }));
  return calls;
}

function renderStats(props: { timeZone?: string; month?: string } = {}, locale: 'zh-CN' | 'en-US' = 'zh-CN') {
  return render(
    <I18nProvider initialLocale={locale}>
      <StatsPage {...props} />
    </I18nProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('StatsPage', () => {
  it('requests stats for the device time zone and month and renders totals and heatmap', async () => {
    const calls = installFetch();
    renderStats({ timeZone: 'Asia/Shanghai', month: '2026-10' });
    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(screen.getByText('88')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(document.querySelectorAll('.stats__cell')).toHaveLength(STATS.monthCells.length);
    const request = calls.find((call) => call.url.startsWith('/api/stats'));
    expect(request?.url).toContain('timeZone=Asia%2FShanghai');
    expect(request?.url).toContain('month=2026-10');
  });

  it('switches to the next and previous month', async () => {
    const calls = installFetch();
    renderStats({ timeZone: 'Asia/Shanghai', month: '2026-10' });
    await screen.findByText('42');
    fireEvent.click(screen.getByRole('button', { name: '下个月' }));
    await waitFor(() => expect(calls.some((call) => call.url.includes('month=2026-11'))).toBe(true));
    fireEvent.click(screen.getByRole('button', { name: '上个月' }));
    await waitFor(() => expect(calls.some((call) => call.url.includes('month=2026-10'))).toBe(true));
  });

  it('shows a zero-value state for an empty month', async () => {
    installFetch(EMPTY_MONTH);
    renderStats({ timeZone: 'Asia/Shanghai', month: '2026-01' });
    expect(await screen.findByText('这个月还没有学习记录。')).toBeInTheDocument();
  });

  it('keeps the date-only month unshifted across device time zones', async () => {
    const calls = installFetch();
    const label = formatMonthLabel('2026-10', 'zh-CN');
    const first = renderStats({ timeZone: 'Asia/Shanghai', month: '2026-10' });
    expect(await screen.findByText(label)).toBeInTheDocument();
    first.unmount();

    renderStats({ timeZone: 'America/Los_Angeles', month: '2026-10' });
    expect(await screen.findByText(label)).toBeInTheDocument();
    expect(calls.some((call) => call.url.includes('timeZone=Asia%2FShanghai'))).toBe(true);
    expect(calls.some((call) => call.url.includes('timeZone=America%2FLos_Angeles'))).toBe(true);
  });

  it('surfaces a contract error code as translated copy when stats fail', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ error: { code: 'INTERNAL_ERROR', retryable: true, params: {} }, requestId: 'r' }, 500)));
    renderStats({ timeZone: 'Asia/Shanghai', month: '2026-10' });
    expect(await screen.findByRole('alert')).toHaveTextContent('服务出错了，请重试。');
  });

  it('renders translated copy for the en-US locale', async () => {
    installFetch();
    renderStats({ timeZone: 'Asia/Shanghai', month: '2026-10' }, 'en-US');
    expect(await screen.findByText('Total questions')).toBeInTheDocument();
  });
});
