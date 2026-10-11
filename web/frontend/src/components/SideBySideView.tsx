import type { BranchSource, Message } from '../api/types';
import { useI18n } from '../i18n/I18nProvider';
import { MessageView, type TextHighlight } from './MessageView';

// 并排分支视图（B，任务 T-2026-10-10-031）：当前分支与其分叉来源（Branch.source）两列对照。
// 来源消息按 sourceMessageId 在**已加载的来源分支消息**中定位；命中则高亮 anchor 半开区间，
// 未命中（来源分支消息尚未加载或已不在其中）显示可读降级说明。分叉快照（contextSnapshotId）
// 以说明区呈现。本视图不计算树布局。
interface SideBySideViewProps {
  currentTitle: string;
  currentMessages: Message[];
  source: BranchSource | null;
  sourceTitle: string | null;
  sourceMessages: Message[] | null;
}

function anchorRange(source: BranchSource): TextHighlight | null {
  const { startInclusive, endExclusive } = source.anchor;
  return startInclusive !== null && endExclusive !== null ? { start: startInclusive, end: endExclusive } : null;
}

function SourceColumn({ source, sourceTitle, sourceMessages }: { source: BranchSource; sourceTitle: string | null; sourceMessages: Message[] | null }) {
  const { t } = useI18n();
  const located = sourceMessages?.find((message) => message.id === source.sourceMessageId) ?? null;
  return (
    <>
      <h3 className="side-by-side__title">{sourceTitle ?? t('workspace.source.parent')}</h3>
      {located !== null ? <MessageView message={located} highlight={anchorRange(source)} /> : <p className="side-by-side__note">{t('workspace.source.anchorMissing')}</p>}
      <p className="side-by-side__note">{t('workspace.source.snapshot')}: {source.contextSnapshotId}</p>
    </>
  );
}

export function SideBySideView({ currentTitle, currentMessages, source, sourceTitle, sourceMessages }: SideBySideViewProps) {
  const { t } = useI18n();
  return (
    <div className="side-by-side">
      <section className="side-by-side__column" aria-label={currentTitle}>
        <h3 className="side-by-side__title">{currentTitle}</h3>
        {currentMessages.map((message) => <MessageView key={message.id} message={message} />)}
      </section>
      <section className="side-by-side__column" aria-label={sourceTitle ?? t('workspace.source.parent')}>
        {source === null ? <p className="side-by-side__note">{t('workspace.noSource')}</p> : <SourceColumn source={source} sourceTitle={sourceTitle} sourceMessages={sourceMessages} />}
      </section>
    </div>
  );
}