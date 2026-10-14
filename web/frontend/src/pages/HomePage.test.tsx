import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { I18nProvider } from '../i18n/I18nProvider';
import type { Locale } from '../i18n';
import { HomePage } from './HomePage';

const PLACEHOLDER = '写下你想弄懂的问题…';

function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

function resolution(modelId: string | null, fallback: string) {
  return {
    roles: {
      CHAT: { providerId: modelId ? 'p1' : null, modelId, fallback },
      FAST: { providerId: null, modelId: null, fallback: 'EMPTY' },
      TRANSLATION: { providerId: null, modelId: null, fallback: 'EMPTY' },
    },
  };
}

interface Handlers {
  topics?: () => unknown;
  resolve?: () => unknown;
  create?: () => Response | Promise<Response>;
}

function installFetch(handlers: Handlers = {}) {
  const calls: { method: string; url: string; body: string | null }[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = (init?.method ?? 'GET').toUpperCase();
      calls.push({ method, url, body: typeof init?.body === 'string' ? init.body : null });
      if (method === 'GET' && url.endsWith('/api/topics')) return json(handlers.topics ? handlers.topics() : []);
      if (method === 'GET' && url.endsWith('/api/settings/model-resolution')) {
        return json(handlers.resolve ? handlers.resolve() : resolution('deepseek-v4-pro', 'NONE'));
      }
      if (method === 'POST' && url.endsWith('/api/topics')) {
        return handlers.create ? handlers.create() : json({ topic: { id: 'new-topic' } });
      }
      throw new Error(`unexpected ${method} ${url}`);
    }),
  );
  return calls;
}

function renderHome(locale: Locale = 'zh-CN') {
  return render(
    <I18nProvider initialLocale={locale}>
      <HomePage />
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

describe('HomePage composer state machine', () => {
  it('keeps send disabled until the draft is non-empty and a model is usable', async () => {
    installFetch();
    renderHome();
    const input = await screen.findByLabelText(PLACEHOLDER);
    const send = screen.getByRole('button', { name: '开始学习' });
    await waitFor(() => expect(send).toBeDisabled());

    fireEvent.change(input, { target: { value: '什么是光合作用？' } });
    await waitFor(() => expect(send).toBeEnabled());
  });

  it('blocks sending and guides to settings when no model is usable', async () => {
    installFetch({ resolve: () => resolution(null, 'EMPTY') });
    renderHome();
    expect(await screen.findByText('还没有可用的模型，请先到设置里配置提供商与 Key。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '开始学习' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '去设置' }));
    expect(window.location.pathname).toBe('/settings/providers');
  });

  it('submits the first question once and shows a stop placeholder while in flight', async () => {
    let resolveCreate: (response: Response) => void = () => {};
    const pending = new Promise<Response>((resolve) => {
      resolveCreate = resolve;
    });
    const calls = installFetch({ create: () => pending });
    renderHome();

    const input = await screen.findByLabelText(PLACEHOLDER);
    fireEvent.change(input, { target: { value: '什么是光合作用？' } });
    const send = screen.getByRole('button', { name: '开始学习' });
    await waitFor(() => expect(send).toBeEnabled());

    fireEvent.click(send);
    await screen.findByRole('button', { name: '停止' });
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: '停止' }));
    expect(screen.getByRole('button', { name: '开始学习' })).toBeInTheDocument();
    expect(calls.filter((call) => call.method === 'POST')).toHaveLength(1);
    resolveCreate(json({ topic: { id: 'new-topic' } }));
  });

  it('opens the created topic and clears the draft after a successful submit', async () => {
    installFetch();
    renderHome();
    const input = await screen.findByLabelText(PLACEHOLDER);
    fireEvent.change(input, { target: { value: '什么是光合作用？' } });
    const send = screen.getByRole('button', { name: '开始学习' });
    await waitFor(() => expect(send).toBeEnabled());

    fireEvent.click(send);
    await waitFor(() => expect(window.location.pathname).toBe('/topic/new-topic'));
    expect(localStorage.getItem('draft:home')).toBeNull();
  });

  it('surfaces a contract error code as translated copy', async () => {
    installFetch({ create: () => json({ error: { code: 'KEY_MISSING', retryable: false, params: {} }, requestId: 'r1' }, 400) });
    renderHome();
    const input = await screen.findByLabelText(PLACEHOLDER);
    fireEvent.change(input, { target: { value: '什么是光合作用？' } });
    const send = screen.getByRole('button', { name: '开始学习' });
    await waitFor(() => expect(send).toBeEnabled());

    fireEvent.click(send);
    expect(await screen.findByRole('alert')).toHaveTextContent('还没有可用的 API Key，请到设置里填写后再发送。');
  });
});

describe('HomePage recent topics', () => {
  it('renders one titled card per topic summary from the contract client', async () => {
    installFetch({
      topics: () => [
        { topicId: 't1', rootTitle: '大语言模型的注意力机制', rootQuestion: '注意力机制到底是什么？', displayTitle: '大语言模型的注意力机制', lastUsedAtMillis: 1700000001000 },
        { topicId: 't2', rootTitle: null, rootQuestion: '冷链物流的断链风险点？', displayTitle: '冷链物流的断链风险点？', lastUsedAtMillis: 1700000002000 },
      ],
    });
    renderHome();
    expect(await screen.findByRole('button', { name: '大语言模型的注意力机制' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '冷链物流的断链风险点？' })).toBeInTheDocument();
  });

  it('falls back to the generic label when a summary has no derived title', async () => {
    installFetch({
      topics: () => [
        { topicId: 't1', rootTitle: null, rootQuestion: null, displayTitle: null, lastUsedAtMillis: 1700000001000 },
      ],
    });
    renderHome();
    expect(await screen.findByRole('button', { name: '学习主题' })).toBeInTheDocument();
  });

  it('shows the empty state when there are no topics', async () => {
    installFetch({ topics: () => [] });
    renderHome();
    expect(await screen.findByText('还没有学习主题，从上面的问题开始第一个吧。')).toBeInTheDocument();
  });
});

// T-131：Android HomeScreen/screen_home_body 的口径——
// ① brand lockup（芽 + 知芽）恒显示；② 主标语只在「还没有历史主题」时出现；
// ③ 最近学习是快捷面：标题带计数（没有就不显示计数，不是显示 0）、最多 MAX_RECENT=3 条、
//    整卡是点击目标（item_home_topic.xml 里没有单独的 chevron 按钮）。
describe('HomePage structure (T-131)', () => {
  function summary(topicId: string, title: string, lastUsedAtMillis: number) {
    return { topicId, rootTitle: title, rootQuestion: `${title}？`, displayTitle: title, lastUsedAtMillis };
  }

  it('drops the hero slogan once there are history topics, and keeps the brand lockup either way', async () => {
    installFetch({ topics: () => [summary('t1', '光合作用的原理', 1700000001000)] });
    renderHome();

    expect(await screen.findByText('光合作用的原理')).toBeInTheDocument();
    expect(screen.queryByText('今天，想从哪个问题开始？')).toBeNull();
    expect(screen.getByTestId('home-brand')).toHaveTextContent('知芽');
  });

  it('shows the hero slogan while the home has no topics yet', async () => {
    installFetch({ topics: () => [] });
    renderHome();

    expect(await screen.findByText('今天，想从哪个问题开始？')).toBeInTheDocument();
    expect(screen.getByTestId('home-brand')).toHaveTextContent('知芽');
    // 反恒真配对的下半：没有可数之物时计数整条消失，不是显示「0 个」。
    expect(screen.queryByTestId('home-recent-count')).toBeNull();
  });

  it('counts the history topics and hides the count instead of showing zero', async () => {
    installFetch({ topics: () => [summary('t1', '主题一', 1700000001000), summary('t2', '主题二', 1700000002000)] });
    renderHome();

    expect(await screen.findByTestId('home-recent-count')).toHaveTextContent('2 个');
  });

  it('draws at most three history cards however many topics the read returned', async () => {
    installFetch({
      topics: () => [
        summary('t1', '主题一', 1700000001000), summary('t2', '主题二', 1700000002000),
        summary('t3', '主题三', 1700000003000), summary('t4', '主题四', 1700000004000),
        summary('t5', '主题五', 1700000005000),
      ],
    });
    renderHome();

    await screen.findByText('主题一');
    expect(screen.getAllByTestId('home-topic-card')).toHaveLength(3);
    expect(screen.queryByText('主题四')).toBeNull();
  });

  it('opens the topic from anywhere on the card, not only from a trailing button', async () => {
    installFetch({ topics: () => [summary('t1', '光合作用的原理', 1700000001000)] });
    renderHome();

    fireEvent.click(await screen.findByText('光合作用的原理'));
    await waitFor(() => expect(window.location.pathname).toBe('/topic/t1'));
  });
});

describe('HomePage draft and i18n', () => {
  it('restores the home draft after a remount', async () => {
    installFetch();
    const first = renderHome();
    fireEvent.change(await screen.findByLabelText(PLACEHOLDER), { target: { value: '未发送的草稿' } });
    first.unmount();

    renderHome();
    const input = (await screen.findByLabelText(PLACEHOLDER)) as HTMLTextAreaElement;
    expect(input.value).toBe('未发送的草稿');
  });

  it('clears the draft explicitly through the clear button', async () => {
    installFetch();
    renderHome();
    fireEvent.change(await screen.findByLabelText(PLACEHOLDER), { target: { value: '草稿' } });
    fireEvent.click(screen.getByRole('button', { name: '清除草稿' }));
    await waitFor(() => expect(localStorage.getItem('draft:home')).toBeNull());
    expect((screen.getByLabelText(PLACEHOLDER) as HTMLTextAreaElement).value).toBe('');
  });

  it('renders the home copy in English when the locale is en-US', async () => {
    installFetch();
    renderHome('en-US');
    expect(await screen.findByText('What would you like to understand today?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Start learning' })).toBeInTheDocument();
  });
});