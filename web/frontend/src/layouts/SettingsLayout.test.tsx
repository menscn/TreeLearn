import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import type { Locale } from '../i18n';
import { I18nProvider } from '../i18n/I18nProvider';
import { SettingsLayout } from './SettingsLayout';

// Test expectations may assert localized output; production code may not
// hardcode it (enforced by scripts/check-user-copy-i18n.mjs).
function renderLayout(active: 'providers' | 'model-roles' | 'about', locale: Locale = 'zh-CN') {
  return render(
    <I18nProvider initialLocale={locale}>
      <SettingsLayout active={active}>
        <p>body</p>
      </SettingsLayout>
    </I18nProvider>,
  );
}

afterEach(() => {
  window.history.replaceState({}, '', '/');
});

describe('SettingsLayout', () => {
  it('renders icon-only section navigation with the active section marked', () => {
    renderLayout('providers');
    expect(screen.getByRole('button', { name: '提供商与密钥' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '模型分工' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('button', { name: '通用与关于' })).toBeInTheDocument();
  });

  it('navigates on Enter so the sections are keyboard reachable', () => {
    window.history.replaceState({}, '', '/settings/providers');
    renderLayout('providers');
    fireEvent.keyDown(screen.getByRole('button', { name: '模型分工' }), { key: 'Enter' });
    expect(window.location.pathname).toBe('/settings/model-roles');
  });

  it('renders the section labels in English when the locale is en-US', () => {
    renderLayout('about', 'en-US');
    expect(screen.getByRole('button', { name: 'Model roles' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'General & about' })).toHaveAttribute('aria-current', 'page');
  });
});
