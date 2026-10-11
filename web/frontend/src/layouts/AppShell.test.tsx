import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Locale } from '../i18n';
import { I18nProvider } from '../i18n/I18nProvider';
import type { Route } from '../platform/router';
import { AppShell } from './AppShell';

// Test expectations may assert localized output; production code may not
// hardcode it (enforced by scripts/check-user-copy-i18n.mjs).
function renderShell(route: Route, children = <p>content</p>, locale: Locale = 'zh-CN') {
  return render(
    <I18nProvider initialLocale={locale}>
      <AppShell route={route}>{children}</AppShell>
    </I18nProvider>,
  );
}

// jsdom 不实现 matchMedia；用它模拟侧栏的窄/宽两态，并返回一个可触发 change 的 setter。
function stubSidebarViewport(initiallyWide: boolean) {
  const listeners = new Set<(event: MediaQueryListEvent) => void>();
  const list = {
    matches: initiallyWide,
    addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => { listeners.add(listener); },
    removeEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => { listeners.delete(listener); },
  };
  vi.stubGlobal('matchMedia', (query: string) => ({ ...list, media: query }));
  return (nextWide: boolean) => {
    list.matches = nextWide;
    listeners.forEach((listener) => listener({ matches: nextWide } as MediaQueryListEvent));
  };
}

describe('AppShell', () => {
  afterEach(() => {
    window.history.replaceState({}, '', '/');
    vi.unstubAllGlobals();
  });

  it('renders translated icon-only navigation with the active route marked', () => {
    renderShell({ name: 'library' });
    expect(screen.getByText('知芽')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '学习库' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '主页' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('button', { name: '设置' })).toBeInTheDocument();
  });

  it('navigates on Enter so the primary navigation is keyboard reachable', () => {
    renderShell({ name: 'home' });
    fireEvent.keyDown(screen.getByRole('button', { name: '学习库' }), { key: 'Enter' });
    expect(window.location.pathname).toBe('/library');
  });

  it('exposes the stats entry as an icon-only keyboard-reachable nav item', () => {
    renderShell({ name: 'stats' });
    const stats = screen.getByRole('button', { name: '学习足迹' });
    expect(stats).toHaveAttribute('aria-current', 'page');
    fireEvent.keyDown(stats, { key: 'Enter' });
    expect(window.location.pathname).toBe('/stats');
  });

  it('returns focus to the content region on Escape', () => {
    renderShell({ name: 'home' });
    const home = screen.getByRole('button', { name: '主页' });
    home.focus();
    expect(document.activeElement).toBe(home);

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(document.activeElement).toBe(screen.getByLabelText('工作区内容'));
  });

  it('switches the document language and re-resolves copy at render time', () => {
    renderShell({ name: 'home' });
    fireEvent.click(screen.getByRole('button', { name: '切换到 English' }));
    expect(document.documentElement.lang).toBe('en-US');
    expect(screen.getByRole('button', { name: 'Home' })).toBeInTheDocument();
  });

  it('shows the full brand name on a wide sidebar and keeps it as the accessible name', () => {
    stubSidebarViewport(true);
    renderShell({ name: 'home' }, <p>content</p>, 'en-US');
    expect(screen.getByText('TreeLearn')).toBeInTheDocument();
    expect(screen.queryByText('TL')).toBeNull();
    expect(screen.getByRole('img', { name: 'TreeLearn' })).toBeInTheDocument();
  });

  it('uses a compact mark on the narrow icon rail without losing the full accessible name', () => {
    stubSidebarViewport(false);
    renderShell({ name: 'home' }, <p>content</p>, 'en-US');
    expect(screen.getByText('TL')).toBeInTheDocument();
    expect(screen.queryByText('TreeLearn')).toBeNull();
    expect(screen.getByRole('img', { name: 'TreeLearn' })).toBeInTheDocument();
  });

  it('keeps the Chinese brand readable and switches its mark when the sidebar crosses the breakpoint', () => {
    const setSidebarWidth = stubSidebarViewport(false);
    renderShell({ name: 'home' });
    expect(screen.getByText('知')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '知芽' })).toBeInTheDocument();

    act(() => setSidebarWidth(true));
    expect(screen.getByText('知芽')).toBeInTheDocument();
    expect(screen.queryByText('知')).toBeNull();
  });
});