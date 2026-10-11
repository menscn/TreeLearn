import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Locale } from '../i18n';
import { I18nProvider } from '../i18n/I18nProvider';
import { ModelRolesPage } from './ModelRolesPage';

// Test expectations may assert localized output; production code may not
// hardcode it (enforced by scripts/check-user-copy-i18n.mjs).
function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

function errorBody(code: string, params: Record<string, unknown> = {}) {
  return { error: { code, retryable: false, params }, requestId: 'r1' };
}

function rolesBody() {
  return {
    roles: {
      CHAT: { providerId: 'p1', modelId: 'deepseek-v4-pro', fallback: 'NONE' },
      FAST: { providerId: 'p1', modelId: 'deepseek-flash', fallback: 'BUILTIN' },
      TRANSLATION: { providerId: null, modelId: null, fallback: 'EMPTY' },
    },
    budget: 'AUTO',
    suggestions: true,
    notice: 'NONE',
  };
}

function resolutionBody() {
  return {
    roles: {
      CHAT: { providerId: 'p1', modelId: 'deepseek-v4-pro', fallback: 'NONE' },
      FAST: { providerId: 'p1', modelId: 'deepseek-flash', fallback: 'BUILTIN' },
      TRANSLATION: { providerId: null, modelId: null, fallback: 'EMPTY' },
    },
  };
}

interface Handlers {
  get?: () => Response;
  put?: (body: string | null) => Response;
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
      if (url === '/api/settings/model-roles' && method === 'GET') return handlers.get ? handlers.get() : json(rolesBody());
      if (url === '/api/settings/model-roles' && method === 'PUT') return handlers.put ? handlers.put(body) : json(rolesBody());
      if (url === '/api/settings/model-resolution' && method === 'GET') return json(resolutionBody());
      if (url === '/api/providers' && method === 'GET') {
        return json([{ id: 'p1', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', enabled: true, hasKey: true, modelCount: 2 }]);
      }
      if (url === '/api/providers/p1/models' && method === 'GET') {
        return json([
          { providerId: 'p1', providerName: 'DeepSeek', modelId: 'deepseek-v4-pro' },
          { providerId: 'p1', providerName: 'DeepSeek', modelId: 'deepseek-flash' },
        ]);
      }
      throw new Error(`unexpected ${method} ${url}`);
    }),
  );
  return calls;
}

function renderPage(locale: Locale = 'zh-CN') {
  return render(
    <I18nProvider initialLocale={locale}>
      <ModelRolesPage />
    </I18nProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  window.history.replaceState({}, '', '/settings/model-roles');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

describe('ModelRolesPage', () => {
  it('renders the three role selectors, budget and fallback labels', async () => {
    installFetch();
    renderPage();

    const chat = (await screen.findByRole('combobox', { name: '对话' })) as HTMLSelectElement;
    expect(chat.value).toBe('p1::deepseek-v4-pro');
    expect(screen.getByRole('combobox', { name: '快速' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '翻译' })).toBeInTheDocument();
    expect((screen.getByRole('combobox', { name: '上下文预算' }) as HTMLSelectElement).value).toBe('AUTO');
    expect(screen.getByRole('button', { name: '建议开关' })).toHaveAttribute('aria-pressed', 'true');

    expect(screen.getByText('按所选模型')).toBeInTheDocument();
    expect(screen.getByText('回落到内置默认模型')).toBeInTheDocument();
    expect(screen.getByText('无可用模型')).toBeInTheDocument();
  });

  it('saves the draft through PUT /api/settings/model-roles', async () => {
    const calls = installFetch();
    renderPage();

    fireEvent.change(await screen.findByRole('combobox', { name: '对话' }), { target: { value: 'p1::deepseek-flash' } });
    fireEvent.click(screen.getByRole('button', { name: '保存分工' }));

    expect(await screen.findByText('模型分工已保存。')).toBeInTheDocument();
    const put = calls.find((call) => call.method === 'PUT' && call.url === '/api/settings/model-roles');
    expect(put?.body).toContain('deepseek-flash');
    expect(put?.body).toContain('"commandId"');
  });

  it('surfaces an explicit message when the save endpoint is not supported (404)', async () => {
    installFetch({ put: () => json(errorBody('NOT_FOUND', { resource: 'model-roles' }), 404) });
    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: '保存分工' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('模型分工端点尚未启用，暂时无法读取或保存。');
    expect(screen.queryByText('模型分工已保存。')).toBeNull();
  });

  it('shows the unavailable state when the read endpoint is not supported (404)', async () => {
    installFetch({ get: () => json(errorBody('NOT_FOUND', { resource: 'model-roles' }), 404) });
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('模型分工端点尚未启用，暂时无法读取或保存。');
  });

  it('renders the model-roles copy in English when the locale is en-US', async () => {
    installFetch();
    renderPage('en-US');
    expect(await screen.findByText('Configured model')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save roles' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Context budget' })).toBeInTheDocument();
  });
});
