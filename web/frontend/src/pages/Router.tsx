import { GlassPanel } from '../components/GlassPanel';
import { useRoute } from '../hooks/useRoute';
import { useI18n } from '../i18n/I18nProvider';
import { AppShell } from '../layouts/AppShell';
import { SettingsLayout, type SettingsSection } from '../layouts/SettingsLayout';
import { BranchWorkspace } from './BranchWorkspace';
import { FilesPage } from './FilesPage';
import { HomePage } from './HomePage';
import { LibraryPage } from './LibraryPage';
import { ModelRolesPage } from './ModelRolesPage';
import { ProviderEditorPage } from './ProviderEditorPage';
import { StatsPage } from './StatsPage';
import { TopicTreePage } from './TopicTreePage';

// 路由 → 页面映射放在 pages 层：布局层不得反向依赖 pages，由本组件把页面注入 AppShell。
// `/library` 与 `/stats` 为真实页（T-2026-10-10-044）；`/settings/<section>` 由 SettingsLayout
// + 真实设置页渲染（T-2026-10-10-039），其余设置分区回落到通用/关于页；其余路径为 404。
function AboutPage() {
  const { t } = useI18n();
  return (
    <GlassPanel className="settings-page">
      <h2 className="settings-page__title">{t('settings.sections.about')}</h2>
      <p className="settings-page__note">{t('settings.about.body')}</p>
    </GlassPanel>
  );
}

function settingsSection(section: string | null): SettingsSection {
  return section === 'providers' || section === 'model-roles' || section === 'files' ? section : 'about';
}

function settingsContent(section: string | null) {
  if (section === 'providers') return <ProviderEditorPage />;
  if (section === 'files') return <FilesPage />;
  if (section === 'model-roles') return <ModelRolesPage />;
  return <AboutPage />;
}

export function Router() {
  const route = useRoute();
  const { t } = useI18n();
  const page = route.name === 'home'
    ? <HomePage />
    : route.name === 'library'
      ? <LibraryPage />
      : route.name === 'stats'
        ? <StatsPage />
        : route.name === 'topic'
          ? <BranchWorkspace topicId={route.id} branchId={route.branchId} />
          : route.name === 'topicTree'
            ? <TopicTreePage topicId={route.id} branchId={route.branchId} />
            : route.name === 'settings'
            ? <SettingsLayout active={settingsSection(route.section)}>{settingsContent(route.section)}</SettingsLayout>
            : (
              <GlassPanel className="placeholder-page">
                <p className="placeholder-page__body">{t(route.name === 'notFound' ? 'notFound.body' : 'placeholder.body')}</p>
              </GlassPanel>
            );
  return <AppShell route={route}>{page}</AppShell>;
}
