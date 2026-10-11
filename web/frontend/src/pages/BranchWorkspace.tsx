import { useCallback, useEffect, useRef, useState } from 'react';
import type { LucideIcon } from 'lucide-react';

import { ApiError, errorI18nKey, FALLBACK_ERROR_KEY, isErrorCode } from '../api/errors';
import type { OperationFrameSource, TerminalKind } from '../api/sse';
import type { Branch, BranchTree, Message, ReadingAnchor, Topic } from '../api/types';
import { Composer } from '../components/Composer';
import { GlassPanel } from '../components/GlassPanel';
import { IconButton } from '../components/IconButton';
import { TreeCanvasView } from '../components/TreeCanvasView';
import { Icon } from '../components/Icon';
import { ArrowLeft, ChevronRight, Columns2, Maximize2, Network, RotateCcw, Square } from '../components/icons';
import { Markdown } from '../components/markdown/Markdown';
import { MessageView } from '../components/MessageView';
import { SideBySideView } from '../components/SideBySideView';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { useDraft } from '../hooks/useDraft';
import { useOperationStream } from '../hooks/useOperationStream';
import { navigate } from '../platform/router';
import { draftKeyFor } from '../store/drafts';
import { loadHomeConfig } from '../store/homeData';
import { readLayout, WORKSPACE_LAYOUTS, writeLayout, type WorkspaceLayout } from '../store/layoutPreference';
import { cancelOperation, loadBranchTree, loadConversation, loadTopic, markBranchSeen, newCommandId, operationEventsUrl, resetMessage, resolveBranch, retryMessage, rootBranch, saveReadingPosition, sendBranchMessage, uploadAttachment } from '../store/topicData';

// 工作区（任务 T-2026-10-10-031）：`/topic/:id` 的 A 专注阅读 + B 并排分支 + C 树图占位。
// 顶部为根分支标题（=主题标题）、到当前分支的面包屑、布局切换与返回；阅读列渲染已提交
// 消息位；流式区订阅当前活跃操作（sse.ts 帧源 → useOperationStream），提供停止/原位重试；
// 追问 composer 带引用 chip。C 视图依赖服务端树布局端点，本轮仅占位说明（前端不计算树布局）。
const LAYOUT_ICONS: Record<WorkspaceLayout, LucideIcon> = { A: Maximize2, B: Columns2, C: Network };
const LAYOUT_LABELS: Record<WorkspaceLayout, TranslationKey> = { A: 'workspace.layout.focus', B: 'workspace.layout.sideBySide', C: 'workspace.layout.tree' };
const TERMINAL_LABELS: Record<TerminalKind, TranslationKey> = { stable: 'workspace.status.stable', failed: 'workspace.status.failed', interrupted: 'workspace.status.interrupted', canceled: 'workspace.status.canceled' };

const errorKey = (reason: unknown): TranslationKey => (reason instanceof ApiError ? reason.i18nKey : FALLBACK_ERROR_KEY);

interface Loaded { topic: Topic; tree: BranchTree; branch: Branch; messages: Message[]; recommendations: string[] }

export function BranchWorkspace({ topicId, branchId, streamSource }: { topicId: string; branchId: string | null; streamSource?: OperationFrameSource | null }) {
  const { t } = useI18n();
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [error, setError] = useState<TranslationKey | null>(null);
  const [activeOperationId, setActiveOperationId] = useState<string | null>(null);
  const [activeMessageId, setActiveMessageId] = useState<string | null>(null);
  const [sourceMessages, setSourceMessages] = useState<Message[] | null>(null);
  const [model, setModel] = useState<string | null>(null);
  const [thinking, setThinking] = useState(false);
  const [quote, setQuote] = useState('');
  const [pendingAttachment, setPendingAttachment] = useState<{ attachmentId: string; displayName: string | null } | null>(null);
  const [uploading, setUploading] = useState(false);
  const [sending, setSending] = useState(false);
  const [actionError, setActionError] = useState<TranslationKey | null>(null);
  const [layout, setLayout] = useState<WorkspaceLayout>(() => readLayout());
  // 追问草稿按分支保存（reuse T-030 的 useDraft）：切换分支不丢各分支草稿、重挂载恢复。
  const draft = useDraft(draftKeyFor(loaded?.branch.id ?? branchId));

  useEffect(() => {
    let active = true;
    setLoaded(null);
    setError(null);
    setSourceMessages(null);
    setActiveOperationId(null);
    setActionError(null);
    Promise.all([loadTopic(topicId), loadBranchTree(topicId)]).then(async ([topic, tree]) => {
      const branch = resolveBranch(tree, branchId ?? topic.lastVisitedBranchId);
      if (branch === null) { if (active) setError('workspace.empty'); return; }
      const conversation = await loadConversation(branch.id);
      if (!active) return;
      setLoaded({ topic, tree, branch, messages: conversation.messages, recommendations: conversation.recommendations });
      const running = conversation.messages.find((message) => message.activeOperationId !== null) ?? null;
      setActiveOperationId(running?.activeOperationId ?? null);
      setActiveMessageId(running?.id ?? null);
      // 进入分支即标记已读到最新稳定序（只前进；后端拒绝回退）。fire-and-forget：失败不打断阅读。
      if (branch.unreadCount > 0) {
        markBranchSeen(branch.id, branch.latestStableSequence).catch(() => {});
      }
    }).catch((reason: unknown) => { if (active) setError(errorKey(reason)); });
    loadHomeConfig().then((config) => { if (active) setModel(config.model); }).catch(() => {});
    return () => {
      active = false;
      // 卸载/切分支时保存阅读位置（语义锚点：最后一条 AI 消息 + 其显示版本；无 AI 消息不保存）。
      const anchor = loadedRef.current;
      if (anchor !== null) {
        saveReadingPosition(anchor.branchId, anchor.anchor).catch(() => {});
      }
    };
  }, [topicId, branchId]);

  const source = loaded?.branch.source ?? null;
  // 阅读位置锚点：最后一条消息与其显示版本（cleanup 里经 ref 读取最新值，避免闭包过期）。
  const loadedRef = useRef<{ branchId: string; anchor: ReadingAnchor } | null>(null);
  useEffect(() => {
    const answer = [...(loaded?.messages ?? [])].reverse().find((message) => message.role === 'AI');
    loadedRef.current = answer === undefined ? null : {
      branchId: loaded?.branch.id ?? '',
      anchor: { messageId: answer.id, revisionId: answer.activeRevisionId, listItemOffset: 0, viewportOffset: 0 },
    };
  }, [loaded]);
  useEffect(() => {
    if (layout !== 'B' || source === null) return;
    let active = true;
    loadConversation(source.parentBranchId).then((conversation) => { if (active) setSourceMessages(conversation.messages); }).catch(() => { if (active) setSourceMessages([]); });
    return () => { active = false; };
  }, [layout, source]);

  const stream = useOperationStream({ operationId: activeOperationId, url: activeOperationId !== null ? operationEventsUrl(activeOperationId) : null, source: streamSource ?? null });
  const streamActive = activeOperationId !== null && stream.terminal === null;

  const chooseLayout = useCallback((next: WorkspaceLayout) => { setLayout(next); writeLayout(next); }, []);

  const handleStop = useCallback(() => {
    if (activeOperationId === null) return;
    cancelOperation(activeOperationId).then(() => setActiveOperationId(null)).catch((reason: unknown) => setActionError(errorKey(reason)));
  }, [activeOperationId]);

  const handleRetry = useCallback(() => {
    if (activeMessageId === null || model === null) return;
    retryMessage(activeMessageId, { model, thinkingEnabled: thinking }).then((result) => {
      setLoaded((prev) => (prev === null ? prev : { ...prev, messages: prev.messages.map((message) => (message.id === result.message.id ? result.message : message)) }));
      setActiveOperationId(result.operation.id);
      setActiveMessageId(result.message.id);
    }).catch((reason: unknown) => setActionError(errorKey(reason)));
  }, [activeMessageId, model, thinking]);

  const handleSend = useCallback(() => {
    if (loaded === null || model === null || draft.text.trim().length === 0 || activeOperationId !== null) return;
    setSending(true);
    setActionError(null);
    sendBranchMessage({ branchId: loaded.branch.id, text: draft.text, quote, attachmentId: pendingAttachment?.attachmentId ?? null, model, thinkingEnabled: thinking }).then((result) => {
      setLoaded((prev) => (prev === null ? prev : { ...prev, messages: [...prev.messages, result.userMessage, result.aiMessage] }));
      draft.clear();
      setQuote('');
      setPendingAttachment(null);
      setActiveOperationId(result.operation.id);
      setActiveMessageId(result.aiMessage.id);
    }).catch((reason: unknown) => setActionError(errorKey(reason))).finally(() => setSending(false));
  }, [loaded, model, quote, thinking, activeOperationId, draft.text, draft.clear]);

  if (error !== null) return <GlassPanel className="workspace"><p className="workspace__error" role="alert">{t(error)}</p></GlassPanel>;
  if (loaded === null) return <GlassPanel className="workspace"><p className="workspace__loading">{t('workspace.loading')}</p></GlassPanel>;

  const root = rootBranch(loaded.tree);
  const rootTitle = root?.title ?? t('nav.topic');
  const branchTitle = loaded.branch.title ?? rootTitle;
  const parentTitle = loaded.tree.nodes.find((node) => node.id === source?.parentBranchId)?.title ?? null;
  const statusKey: TranslationKey = stream.terminal === null ? 'workspace.status.generating' : TERMINAL_LABELS[stream.terminal.kind];
  // 失败终态的 errorCode 映射到 errors.<code> 具体可行动文案；未知/缺失码回退通用「生成失败」。
  const failedCode = stream.terminal?.kind === 'failed' ? stream.terminal.errorCode : undefined;
  const statusText = failedCode !== undefined && isErrorCode(failedCode) ? t(errorI18nKey(failedCode)) : t(statusKey);

  return (
    <div className="workspace">
      <GlassPanel className="workspace__bar">
        <IconButton icon={ArrowLeft} label={t('workspace.back')} onClick={() => navigate('/')} />
        <nav className="workspace__crumbs" aria-label={t('nav.topic')}>
          <h2 className="workspace__crumb workspace__crumb--root">{rootTitle}</h2>
          {loaded.branch.id !== root?.id ? (
            <>
              <Icon icon={ChevronRight} size="sm" />
              <span className="workspace__crumb workspace__crumb--current">{branchTitle}</span>
            </>
          ) : null}
        </nav>
        <div className="workspace__layout" role="group" aria-label={t('workspace.layout.label')}>
          {WORKSPACE_LAYOUTS.map((value) => (
            <IconButton key={value} icon={LAYOUT_ICONS[value]} label={t(LAYOUT_LABELS[value])} aria-pressed={layout === value} className={layout === value ? 'workspace__layout--active' : undefined} onClick={() => chooseLayout(value)} />
          ))}
        </div>
      </GlassPanel>
      {layout === 'C' ? (
        <TreeCanvasView
          topicId={topicId}
          currentBranchId={loaded.branch.id}
          onNavigate={(target) => navigate(`/topic/${topicId}?branch=${target}`)}
        />
      ) : layout === 'B' ? (
        <SideBySideView currentTitle={branchTitle} currentMessages={loaded.messages} source={source} sourceTitle={parentTitle} sourceMessages={sourceMessages}
          onJumpToSourceMessage={(messageId) => {
            const target = document.getElementById(`msg-${messageId}`);
            target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }} />
      ) : (
        <div className="workspace__reading">
          {loaded.messages.length === 0 ? <p className="workspace__empty">{t('workspace.empty')}</p> : loaded.messages.map((message) => (
            <div key={message.id} id={`msg-${message.id}`}>
              <MessageView message={message} annotationsEnabled
                onQuoteText={message.role === 'AI' ? (text) => { setQuote(text); } : undefined}
                onReset={model !== null ? (messageId) => {
                  resetMessage(messageId, { commandId: newCommandId(), model, thinkingEnabled: thinking }).then((result) => {
                    setLoaded((prev) => (prev === null ? prev : { ...prev, messages: prev.messages.map((item) => (item.id === result.message.id ? result.message : item)) }));
                    setActiveOperationId(result.operation.id);
                    setActiveMessageId(result.message.id);
                  }).catch((reason: unknown) => setActionError(errorKey(reason)));
                } : undefined} />
            </div>
          ))}
        </div>
      )}
      {activeOperationId !== null ? (
        <GlassPanel className="workspace__stream" aria-live="polite">
          <div className="workspace__status">
            <span className="workspace__status-text">{statusText}</span>
            {streamActive ? <IconButton icon={Square} label={t('workspace.action.stop')} variant="solid" onClick={handleStop} /> : null}
            {stream.terminal?.kind === 'failed' || stream.terminal?.kind === 'interrupted' ? <IconButton icon={RotateCcw} label={t('workspace.action.retry')} onClick={handleRetry} /> : null}
          </div>
          {stream.reasoning.length > 0 ? <details className="workspace__reasoning"><summary>{t('workspace.reasoning.toggle')}</summary><p>{stream.reasoning}</p></details> : null}
          {stream.answer.length > 0 ? <div className="workspace__answer"><Markdown text={stream.answer} /></div> : null}
          {streamActive ? <span className="workspace__cursor" aria-hidden="true" /> : null}
        </GlassPanel>
      ) : null}
      {loaded !== null && (loaded.recommendations ?? []).length > 0 ? (
        <div className="workspace__recommendations" data-testid="recommendations" role="listbox" aria-label={t('workspace.recommendations.label')}>
          {(loaded.recommendations ?? []).map((rec) => (
            <button key={rec} type="button" role="option" aria-selected="false" className="workspace__recommendation"
              onClick={() => draft.setText(rec)}>{rec}</button>
          ))}
        </div>
      ) : null}
      <Composer value={draft.text} onValueChange={draft.setText} onClear={draft.clear}
        attachment={pendingAttachment}
        onClearAttachment={() => setPendingAttachment(null)}
        onPickFile={(file) => {
          if (uploading) return;
          setUploading(true);
          fileToBase64(file).then((contentBase64) => uploadAttachment(file.name, file.type || 'application/octet-stream', contentBase64))
            .then((entry) => setPendingAttachment({ attachmentId: entry.attachmentId, displayName: entry.displayName }))
            .catch((reason: unknown) => setActionError(errorKey(reason)))
            .finally(() => setUploading(false));
        }}
        thinkingEnabled={thinking} onToggleThinking={() => setThinking((value) => !value)}
        canSend={model !== null && draft.text.trim().length > 0 && activeOperationId === null && !sending} submitting={sending} blocked={model === null}
        quote={quote} onClearQuote={() => setQuote('')} placeholderKey="workspace.composer.placeholder" sendKey="workspace.composer.send"
        onSend={handleSend} onStop={() => {}} onOpenSettings={() => navigate('/settings/providers')} />
      {actionError !== null ? <p className="workspace__error" role="alert">{t(actionError)}</p> : null}
    </div>
  );
}

/** FileReader→base64（去 data: 前缀；契约 §6.9 contentBase64 无前缀）。 */
function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result);
      const comma = result.indexOf(',');
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.onerror = () => reject(reader.error ?? new Error('read failed'));
    reader.readAsDataURL(file);
  });
}
