import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Locale } from '../i18n';
import { I18nProvider } from '../i18n/I18nProvider';
import { ProviderEditorPage } from './ProviderEditorPage';

// Test expectations may assert localized output; production code may not
// hardcode it (enforced by scripts/check-user-copy-i18n.mjs).
function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

function providerJson(overrides: Record<string, unknown> = {}) {
  return { id: 'p1', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', enabled: true, hasKey: false, modelCount: 0, ...overrides };
}

function modelJson(modelId: string) {
  return { providerId: 'p1', providerName: 'DeepSeek', modelId };
}

interface Handlers {
  providers?: () => unknown;
  create?: (body: string | null) => unknown;
  update?: (body: string | null) => unknown;
  saveKey?: (body: string | null) => unknown;
  refresh?: () => unknown;
}

function installFetch(handlers: Handlers = {}) {
  const calls: { method: string; url: string; body: string | null }[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = (init?.method ?? 'GET').toUpperCase();
      const body = typeof init?.body === 'string' ? init.body : null;
      calls.push({ method, url, body });
      if (url === '/api/providers' && method === 'GET') return json(handlers.providers ? handlers.providers() : [providerJson()]);
      if (url === '/api/providers' && method === 'POST') return json(handlers.create ? handlers.create(body) : providerJson({ id: 'p2' }));
      if (/^\/api\/providers\/[^/]+$/.test(url) && method === 'PUT') return json(handlers.update ? handlers.update(body) : providerJson({ enabled: false }));
      if (/^\/api\/providers\/[^/]+$/.test(url) && method === 'DELETE') return json({ deletedProviderId: 'p1' });
      if (/^\/api\/providers\/[^/]+\/key$/.test(url) && method === 'PUT') return json(handlers.saveKey ? handlers.saveKey(body) : providerJson({ hasKey: true }));
      if (/^\/api\/providers\/[^/]+\/key$/.test(url) && method === 'DELETE') return json(providerJson({ hasKey: false }));
      if (/^\/api\/providers\/[^/]+\/models$/.test(url) && method === 'POST') {
        return json(handlers.refresh ? handlers.refresh() : { added: 2, models: [modelJson('a'), modelJson('b'), modelJson('c')] });
      }
      throw new Error(`unexpected ${method} ${url}`);
    }),
  );
  return calls;
}

function renderPage(locale: Locale = 'zh-CN') {
  return render(
    <I18nProvider initialLocale={locale}>
      <ProviderEditorPage />
    </I18nProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  window.history.replaceState({}, '', '/settings/providers');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

describe('ProviderEditorPage list', () => {
  it('renders each provider with key status, model badge and enable state', async () => {
    installFetch({
      providers: () => [providerJson({ modelCount: 3 }), providerJson({ id: 'p2', name: 'Zhipu', enabled: false, hasKey: true, modelCount: 0 })],
    });
    renderPage();
    expect(await screen.findByText('DeepSeek')).toBeInTheDocument();
    expect(screen.getByText('Zhipu')).toBeInTheDocument();
    expect(screen.getByText('Key 未配置')).toBeInTheDocument();
    expect(screen.getByText('Key 已配置')).toBeInTheDocument();
    const toggles = screen.getAllByRole('button', { name: '启用开关' });
    expect(toggles[0]).toHaveAttribute('aria-pressed', 'true');
    expect(toggles[1]).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByLabelText('3 个模型')).toBeInTheDocument();
  });

  it('guides with an empty state when there are no providers', async () => {
    installFetch({ providers: () => [] });
    renderPage();
    expect(await screen.findByText('还没有提供商，新建一个并填入 API Key 后即可开始使用。')).toBeInTheDocument();
  });
});

describe('ProviderEditorPage create', () => {
  it('rejects a duplicate provider name inline', async () => {
    const calls = installFetch({ providers: () => [providerJson()] });
    renderPage();
    await screen.findByText('DeepSeek');

    fireEvent.click(screen.getByRole('button', { name: '新建提供商' }));
    fireEvent.change(screen.getByLabelText('名称'), { target: { value: 'DeepSeek' } });
    fireEvent.change(screen.getByLabelText('接口基址'), { target: { value: 'https://api.deepseek.com' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('该提供商名称已被使用。');
    expect(calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('creates a provider and clears the form including the typed key', async () => {
    const calls = installFetch({ providers: () => [], create: () => providerJson({ id: 'p2', name: 'NewProv', hasKey: false }) });
    renderPage();
    await screen.findByText('还没有提供商，新建一个并填入 API Key 后即可开始使用。');

    fireEvent.click(screen.getByRole('button', { name: '新建提供商' }));
    fireEvent.change(screen.getByLabelText('名称'), { target: { value: 'NewProv' } });
    fireEvent.change(screen.getByLabelText('接口基址'), { target: { value: 'https://api.example.com' } });
    fireEvent.change(screen.getByLabelText('API Key'), { target: { value: 'sk-typed' } });
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(calls.some((call) => call.method === 'POST')).toBe(true));
    // 表单提交后折叠：create 表单的「名称」字段不再存在，内存中的 Key 也不残留。
    await waitFor(() => expect(screen.queryByLabelText('名称')).toBeNull());
    expect(JSON.stringify(localStorage)).not.toContain('sk-typed');
  });
});

describe('ProviderEditorPage actions', () => {
  it('toggles a provider through PUT /api/providers/{id}', async () => {
    const calls = installFetch({ providers: () => [providerJson()] });
    renderPage();
    const toggle = await screen.findByRole('button', { name: '启用开关' });
    fireEvent.click(toggle);
    await waitFor(() => expect(toggle).toHaveAttribute('aria-pressed', 'false'));
    expect(calls.some((call) => call.method === 'PUT' && call.url === '/api/providers/p1')).toBe(true);
  });

  it('warns before deleting the last provider and removes it on confirm', async () => {
    const calls = installFetch({ providers: () => [providerJson()] });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '删除' }));
    expect(await screen.findByText('这是最后一个提供商，删除后将没有任何可用模型。')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '确认删除' }));
    expect(await screen.findByText('还没有提供商，新建一个并填入 API Key 后即可开始使用。')).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'DELETE' && call.url === '/api/providers/p1')).toBe(true);
  });

  it('saves a password-typed key, clears it from memory and never persists it', async () => {
    const secret = 'sk-secret-value';
    const calls = installFetch({ providers: () => [providerJson({ hasKey: false })] });
    renderPage();

    const input = await screen.findByLabelText('API Key');
    expect(input).toHaveAttribute('type', 'password');
    fireEvent.change(input, { target: { value: secret } });
    fireEvent.click(screen.getByRole('button', { name: '保存 Key' }));

    expect(await screen.findByText('Key 已配置')).toBeInTheDocument();
    expect((screen.getByLabelText('API Key') as HTMLInputElement).value).toBe('');

    const keyCall = calls.find((call) => call.method === 'PUT' && call.url === '/api/providers/p1/key');
    expect(keyCall?.body).toContain('"apiKey"');
    expect(JSON.stringify(localStorage)).not.toContain(secret);
    expect(window.location.href).not.toContain(secret);
  });

  it('refreshes models and reports the added and total counts', async () => {
    installFetch({ providers: () => [providerJson({ modelCount: 0 })] });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: '拉取模型' }));

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('新增模型');
    expect(status).toHaveTextContent('模型总数');
    expect(screen.getByLabelText('3 个模型')).toBeInTheDocument();
  });
});

describe('ProviderEditorPage i18n', () => {
  it('renders the provider copy in English when the locale is en-US', async () => {
    installFetch({ providers: () => [] });
    renderPage('en-US');
    expect(await screen.findByText('No providers yet. Create one and add an API key to get started.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New provider' })).toBeInTheDocument();
    expect(screen.getByText('Providers & keys')).toBeInTheDocument();
  });
});
