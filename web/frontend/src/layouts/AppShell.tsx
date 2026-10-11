import { useEffect, useRef, useState, type ReactNode } from 'react';

import { IconButton } from '../components/IconButton';
import { ChartColumn, House, Languages, Library, Settings } from '../components/icons';
import type { TranslationKey } from '../i18n';
import { LOCALES } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { navigate, type Route } from '../platform/router';

const NAV_ITEMS: readonly { key: 'home' | 'library' | 'stats' | 'settings'; path: string; labelKey: TranslationKey; icon: typeof House }[] = [
  { key: 'home', path: '/', labelKey: 'nav.home', icon: House },
  { key: 'library', path: '/library', labelKey: 'nav.library', icon: Library },
  { key: 'stats', path: '/stats', labelKey: 'nav.stats', icon: ChartColumn },
  { key: 'settings', path: '/settings', labelKey: 'nav.settings', icon: Settings },
];
const TITLE_KEYS: Record<Route['name'], TranslationKey> = {
  home: 'nav.home', library: 'nav.library', stats: 'nav.stats', topic: 'nav.topic', settings: 'nav.settings', notFound: 'notFound.title',
};

// 桌面断点与 global.css 的 `@media (min-width: 1024px)` 一致：断点以下是 88px 图标栏，
// 展宽后是 248px 侧栏。英文完整品牌名（TreeLearn）在窄栏会被裁切，故按宽度切换呈现。
const WIDE_SIDEBAR_QUERY = '(min-width: 1024px)';

/** 窄栏品牌缩写：拉丁名取词首大写字母（TreeLearn→TL），非拉丁名取首字（知芽→知）。 */
function brandMark(name: string): string {
  const initials = name.match(/[A-Z]/g)?.join('') ?? '';
  return initials.length > 0 ? initials : name.slice(0, 1);
}

function matchesViewport(query: string, fallback: boolean): boolean {
  if (typeof window.matchMedia !== 'function') return fallback;
  return window.matchMedia(query).matches;
}

/** 跟踪侧栏是否处于桌面宽度；jsdom 无 matchMedia 时回退为宽窗（渲染完整品牌）。 */
function useWideSidebar(): boolean {
  const [wide, setWide] = useState(() => matchesViewport(WIDE_SIDEBAR_QUERY, true));
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return;
    const query = window.matchMedia(WIDE_SIDEBAR_QUERY);
    setWide(query.matches);
    const onChange = (event: MediaQueryListEvent) => setWide(event.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);
  return wide;
}

/**
 * 应用外壳（任务 T-2026-10-10-030）：icon-only 主导航、当前路由高亮、双语切换、键盘可达
 * （Tab 移动、Enter 激活、Escape 回到内容区）。页面内容由 Router 经 children 注入
 * （layouts 不得反向依赖 pages）。窄窗保留 88px 图标栏，导航始终可达。
 */
export function AppShell({ route, children }: { route: Route; children: ReactNode }) {
  const { t, locale, setLocale } = useI18n();
  const wideSidebar = useWideSidebar();
  const contentRef = useRef<HTMLElement>(null);
  const nextLocale = LOCALES[(LOCALES.indexOf(locale) + 1) % LOCALES.length];
  const activeNav = route.name === 'home' || route.name === 'library' || route.name === 'stats' || route.name === 'settings' ? route.name : null;

  useEffect(() => {
    document.title = t('app.name');
    document.documentElement.lang = locale;
  }, [t, locale]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') contentRef.current?.focus();
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);

  // 品牌始终以完整名为可访问名称（role=img）；窄栏只换可见字形为缩写，避免被裁切。
  return (
    <div className="app-shell">
      <aside className="app-shell__sidebar" aria-label={t('shell.sidebarLabel')}>
        <span className="app-shell__brand" role="img" aria-label={t('app.name')}>
          {wideSidebar ? t('app.name') : brandMark(t('app.name'))}
        </span>
        <nav className="app-shell__nav" aria-label={t('shell.sidebarLabel')}>
          {NAV_ITEMS.map((item) => (
            <IconButton
              key={item.key}
              icon={item.icon}
              label={t(item.labelKey)}
              className={item.key === activeNav ? 'app-shell__nav-item--active' : undefined}
              aria-current={item.key === activeNav ? 'page' : undefined}
              onClick={() => navigate(item.path)}
              // 显式处理 Enter/Space 并阻止默认，避免与原生点击二次触发，也让 jsdom
              // （不为 Enter 合成点击）能覆盖键盘可达性。
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  navigate(item.path);
                }
              }}
            />
          ))}
        </nav>
      </aside>
      <main className="app-shell__main">
        <header className="app-shell__header">
          <h1 className="app-shell__title">{t(TITLE_KEYS[route.name])}</h1>
          <IconButton icon={Languages} label={t('shell.language.switch')} onClick={() => setLocale(nextLocale)} />
        </header>
        <section ref={contentRef} className="app-shell__content" aria-label={t('shell.contentLabel')} tabIndex={-1}>
          {children}
        </section>
      </main>
    </div>
  );
}