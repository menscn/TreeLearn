import { GlassPanel } from '../components/GlassPanel';
import { IconButton } from '../components/IconButton';
import { TreeCanvasView } from '../components/TreeCanvasView';
import { ArrowLeft } from '../components/icons';
import { useI18n } from '../i18n/I18nProvider';
import { navigate } from '../platform/router';

/**
 * 主题总览屏（T-132）：整棵树自己占一屏，对齐 Android `chat_overview` →
 * `ChatPresenter.onOverviewPressed` → `sink.openOverview(branchId)` ——
 * 「opens the whole topic's canvas」，对话页本身不因此改变它显示的分支。
 *
 * 返回的目标是它所在的那条分支的对话页（`/topic/:id?branch=`），而不是首页：
 * 总览是一次跳转，不是一次离开。几何全部由服务端 `tree-layout` 端点给出。
 */
export function TopicTreePage({ topicId, branchId }: { topicId: string; branchId: string | null }) {
  const { t } = useI18n();
  return (
    <div className="workspace">
      <GlassPanel className="workspace__bar">
        <IconButton icon={ArrowLeft} label={t('workspace.back')} data-testid="topic-tree-back"
          onClick={() => navigate(branchId === null ? `/topic/${topicId}` : `/topic/${topicId}?branch=${branchId}`)} />
        <nav className="workspace__crumbs" aria-label={t('workspace.overview')}>
          <h2 className="workspace__crumb workspace__crumb--current">{t('workspace.overview')}</h2>
        </nav>
      </GlassPanel>
      <TreeCanvasView
        topicId={topicId}
        currentBranchId={branchId}
        onNavigate={(target) => navigate(`/topic/${topicId}?branch=${target}`)}
      />
    </div>
  );
}
