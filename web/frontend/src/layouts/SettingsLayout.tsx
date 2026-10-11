import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { IconButton } from '../components/IconButton';
import { Info, KeyRound, Library, SlidersHorizontal } from '../components/icons';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { navigate } from '../platform/router';

// 设置分区导航（任务 T-2026-10-10-039）：icon-only 分区入口 + 内容槽。布局层不得反向
// 依赖 pages，页面由 Router 经 children 注入。窄窗仍保留图标分区栏，导航始终可达。
export type SettingsSection = 'providers' | 'model-roles' | 'files' | 'about';

const SECTIONS: readonly { id: SettingsSection; path: string; labelKey: TranslationKey; icon: LucideIcon }[] = [
  { id: 'providers', path: '/settings/providers', labelKey: 'settings.sections.providers', icon: KeyRound },
  { id: 'model-roles', path: '/settings/model-roles', labelKey: 'settings.sections.modelRoles', icon: SlidersHorizontal },
  { id: 'files', path: '/settings/files', labelKey: 'settings.sections.files', icon: Library },
  { id: 'about', path: '/settings/about', labelKey: 'settings.sections.about', icon: Info },
];

export function SettingsLayout({ active, children }: { active: SettingsSection; children: ReactNode }) {
  const { t } = useI18n();
  return (
    <div className="settings">
      <nav className="settings__nav" aria-label={t('settings.sections.label')}>
        {SECTIONS.map((section) => (
          <IconButton
            key={section.id}
            icon={section.icon}
            label={t(section.labelKey)}
            aria-current={section.id === active ? 'page' : undefined}
            className={section.id === active ? 'settings__nav-item--active' : undefined}
            onClick={() => navigate(section.path)}
            // 显式处理 Enter/Space：原生按钮在真实浏览器可被键盘激活，这里同时让 jsdom（不为 Enter 合成点击）能覆盖键盘可达性。
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                navigate(section.path);
              }
            }}
          />
        ))}
      </nav>
      <div className="settings__body">{children}</div>
    </div>
  );
}
