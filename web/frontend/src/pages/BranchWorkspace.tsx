import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { ApiError, errorI18nKey, FALLBACK_ERROR_KEY, isErrorCode } from '../api/errors';
import type { OperationFrameSource, TerminalKind } from '../api/sse';
import type { Branch, BranchTree, Message, ReadingAnchor, Topic } from '../api/types';
import { Composer } from '../components/Composer';
import { Dialog } from '../components/Dialog';
import { GlassPanel } from '../components/GlassPanel';
import { IconButton } from '../components/IconButton';
import { Icon } from '../components/Icon';
import { ArrowLeft, ChevronRight, GitFork, Network, RotateCcw, Square } from '../components/icons';
import { MessageView } from '../components/MessageView';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { useDraft } from '../hooks/useDraft';
import { useOperationStream } from '../hooks/useOperationStream';
import { sha256Hex } from '../lib/digest';
import { historyFrom, navigate } from '../platform/router';
import { draftKeyFor } from '../store/drafts';
import { loadHomeConfig } from '../store/homeData';
import { cancelOperation, forkBranch, loadBranchTree, loadConversation, loadTopic, markBranchSeen, newCommandId, operationEventsUrl, resetMessage, resolveBranch, retryMessage, rootBranch, saveReadingPosition, sendBranchMessage, uploadAttachment } from '../store/topicData';

// 工作区（任务 T-2026-10-10-031）：`/topic/:id` 的 A 专注阅读 + B 并排分支 + C 树图占位。
// 顶部为根分支标题（=主题标题）、到当前分支的面包屑、总览入口与返回；阅读列渲染已提交
// 消息位；流式区订阅当前活跃操作（sse.ts 帧源 → useOperationStream），提供停止/原位重试；
// 追问 composer 带引用 chip。
// T-132：整棵树是**入口**（`/topic/:id/tree`，对齐 Android `chat_overview` → `sink.openOverview`），
// 不再是本页的 A/B/C 布局档位；本页固定为单列阅读。
const TERMINAL_LABELS: Record<TerminalKind, TranslationKey> = { stable: 'workspace.status.stable', failed: 'workspace.status.failed', interrupted: 'workspace.status.interrupted', canceled: 'workspace.status.canceled' };

const errorKey = (reason: unknown): TranslationKey => (reason instanceof ApiError ? reason.i18nKey : FALLBACK_ERROR_KEY);

interface Loaded { topic: Topic; tree: BranchTree; branch: Branch; messages: Message[]; recommendations: string[]; anchor: ReadingAnchor | null }

export function BranchWorkspace({ topicId, branchId, streamSource }: { topicId: string; branchId: string | null; streamSource?: OperationFrameSource | null }) {
  const { t } = useI18n();
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [error, setError] = useState<TranslationKey | null>(null);
  const [activeOperationId, setActiveOperationId] = useState<string | null>(null);
  const [activeMessageId, setActiveMessageId] = useState<string | null>(null);
  const [model, setModel] = useState<string | null>(null);
  const [thinking, setThinking] = useState(false);
  const [quote, setQuote] = useState('');
  const [pendingAttachment, setPendingAttachment] = useState<{ attachmentId: string; displayName: string | null } | null>(null);
  const [uploading, setUploading] = useState(false);
  const [sending, setSending] = useState(false);
  const [actionError, setActionError] = useState<TranslationKey | null>(null);
  // 「子分支列表」弹窗：打开时记录来源消息 id，列表取自分支树的 source.sourceMessageId。
  const [childrenFor, setChildrenFor] = useState<string | null>(null);
  // 追问草稿按分支保存（reuse T-030 的 useDraft）：切换分支不丢各分支草稿、重挂载恢复。
  const draft = useDraft(draftKeyFor(loaded?.branch.id ?? branchId));

  useEffect(() => {
    let active = true;
    setLoaded(null);
    setError(null);
    setActiveOperationId(null);
    setActionError(null);
    setRetryTarget(null);
    Promise.all([loadTopic(topicId), loadBranchTree(topicId)]).then(async ([topic, tree]) => {
      const branch = resolveBranch(tree, branchId ?? topic.lastVisitedBranchId);
      if (branch === null) { if (active) setError('workspace.empty'); return; }
      const conversation = await loadConversation(branch.id);
      if (!active) return;
      setLoaded({ topic, tree, branch, messages: conversation.messages, recommendations: conversation.recommendations, anchor: conversation.readingAnchor });
      const running = conversation.messages.find((message) => message.activeOperationId !== null) ?? null;
      setActiveOperationId(running?.activeOperationId ?? null);
      setActiveMessageId(running?.id ?? null);
      restoredRef.current = false;
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

  // T-127 阅读恢复：打开分支时定位到上次读到的消息位；无锚点或锚点已失效则到最新端
  // （Android ReadingSession.open：无 stored anchor → ReadingPosition.liveEnd）。
  const restoredRef = useRef(false);
  useEffect(() => {
    if (restoredRef.current || loaded === null || loaded.messages.length === 0) return;
    restoredRef.current = true;
    const anchored = loaded.anchor === null ? null : document.getElementById(`msg-${loaded.anchor.messageId}`);
    const liveEnd = document.getElementById(`msg-${loaded.messages[loaded.messages.length - 1].id}`);
    const target = anchored ?? liveEnd;
    if (target !== null && typeof target.scrollIntoView === 'function') target.scrollIntoView({ block: 'center' });
  }, [loaded]);
  // T-127 未读口径：只在新回答真正进入视口后清未读（Android ViewportSeen：进入分支不算看过）。
  const newestAnswerId = useMemo(() => {
    const messages = loaded?.messages ?? [];
    for (let index = messages.length - 1; index >= 0; index -= 1) {
      const message = messages[index];
      if (message.role === 'AI' && message.activeRevisionId !== null) return message.id;
    }
    return null;
  }, [loaded?.messages]);
  const branch = loaded?.branch ?? null;
  useEffect(() => {
    if (branch === null || branch.unreadCount === 0 || newestAnswerId === null) return;
    const target = document.getElementById(`msg-${newestAnswerId}`);
    // 环境没有 IntersectionObserver（旧浏览器/jsdom）时静默降级：宁可不清未读，也不谎报已读。
    if (target === null || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver((entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return;
      observer.disconnect();
      markBranchSeen(branch.id, branch.latestStableSequence).catch(() => {});
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, [branch, newestAnswerId]);
  // 阅读位置锚点：最后一条消息与其显示版本（cleanup 里经 ref 读取最新值，避免闭包过期）。
  const loadedRef = useRef<{ branchId: string; anchor: ReadingAnchor } | null>(null);
  useEffect(() => {
    const answer = [...(loaded?.messages ?? [])].reverse().find((message) => message.role === 'AI');
    loadedRef.current = answer === undefined ? null : {
      branchId: loaded?.branch.id ?? '',
      anchor: { messageId: answer.id, revisionId: answer.activeRevisionId, listItemOffset: 0, viewportOffset: 0 },
    };
  }, [loaded]);
  const stream = useOperationStream({ operationId: activeOperationId, url: activeOperationId !== null ? operationEventsUrl(activeOperationId) : null, source: streamSource ?? null });
  const streamActive = activeOperationId !== null && stream.terminal === null;

  // T-126 生成终态闭环：终态到达后读回权威消息/版本，解除生成锁（四种终态都保留已收内容），
  // 并把失败/中断的原位重试钉在对应回答位上——不再让「生成完一次就再也问不下去」发生。
  const activeBranchRef = useRef<string | null>(null);
  const activeMessageRef = useRef<string | null>(null);
  const [retryTarget, setRetryTarget] = useState<string | null>(null);
  useEffect(() => { activeBranchRef.current = loaded?.branch.id ?? null; }, [loaded]);
  useEffect(() => { activeMessageRef.current = activeMessageId; }, [activeMessageId]);
  useEffect(() => {
    if (stream.terminal === null) return;
    const branchId = activeBranchRef.current;
    const messageId = activeMessageRef.current;
    if (branchId === null) return;
    let active = true;
    loadConversation(branchId).then((conversation) => {
      if (!active) return;
      setLoaded((prev) => (prev === null ? prev : { ...prev, messages: conversation.messages, recommendations: conversation.recommendations }));
    }).catch(() => {}).finally(() => {
      if (!active) return;
      setActiveOperationId(null);
      setActiveMessageId(null);
      setRetryTarget(stream.terminal !== null && (stream.terminal.kind === 'failed' || stream.terminal.kind === 'interrupted') ? messageId : null);
    });
    return () => { active = false; };
  }, [stream.terminal]);

  // 子分支索引：来源消息 id → 直接从它分出的子分支（契约 §6.3 forks 的溯源字段）。
  const childrenByMessage = useMemo(() => {
    const index = new Map<string, Branch[]>();
    for (const node of loaded?.tree.nodes ?? []) {
      const key = node.source?.sourceMessageId;
      if (key === undefined) continue;
      index.set(key, [...(index.get(key) ?? []), node]);
    }
    return index;
  }, [loaded?.tree.nodes]);

  const handleStop = useCallback(() => {
    if (activeOperationId === null) return;
    cancelOperation(activeOperationId).then(() => setActiveOperationId(null)).catch((reason: unknown) => setActionError(errorKey(reason)));
  }, [activeOperationId]);

  const handleRetry = useCallback(() => {
    // 原位重试：终态锁解除后仍以 retryTarget 定位到那条回答。
    const target = retryTarget ?? activeMessageId;
    if (target === null || model === null) return;
    setRetryTarget(null);
    retryMessage(target, { model, thinkingEnabled: thinking }).then((result) => {
      setLoaded((prev) => (prev === null ? prev : { ...prev, messages: prev.messages.map((message) => (message.id === result.message.id ? result.message : message)) }));
      setActiveOperationId(result.operation.id);
      setActiveMessageId(result.message.id);
    }).catch((reason: unknown) => setActionError(errorKey(reason)));
  }, [activeMessageId, retryTarget, model, thinking]);

  // 发送的唯一出口：composer 与推荐 chip 共用同一条路径，守卫与副作用只有一份。
  const submit = useCallback((text: string, textQuote: string) => {
    if (loaded === null || model === null || text.trim().length === 0 || activeOperationId !== null) return false;
    setSending(true);
    setActionError(null);
    setRetryTarget(null);
    sendBranchMessage({ branchId: loaded.branch.id, text, quote: textQuote, attachmentId: pendingAttachment?.attachmentId ?? null, model, thinkingEnabled: thinking }).then((result) => {
      setLoaded((prev) => (prev === null ? prev : { ...prev, messages: [...prev.messages, result.userMessage, result.aiMessage] }));
      draft.clear();
      setQuote('');
      setPendingAttachment(null);
      setActiveOperationId(result.operation.id);
      setActiveMessageId(result.aiMessage.id);
    }).catch((reason: unknown) => setActionError(errorKey(reason))).finally(() => setSending(false));
    return true;
  }, [loaded, model, thinking, activeOperationId, pendingAttachment?.attachmentId, draft.clear]);

  const handleSend = useCallback(() => { submit(draft.text, quote); }, [submit, draft.text, quote]);

  /**
   * T-130 推荐即发送：对齐 Android `ChatPresenter.onRecommendationFollowed` —— 先成为草稿再
   * 立刻发送，这样发送被拦住时输入框里仍留着被点的那个问题，而不是什么都没发生。
   * 推荐行上的分叉热区走它自己的入口（handleForkRecommendation），两个手势互不可达。
   */
  const handleFollowRecommendation = useCallback((question: string) => {
    if (loaded === null || activeOperationId !== null) return;
    draft.setText(question.trim());
    submit(question.trim(), '');
  }, [loaded, activeOperationId, draft.setText, submit]);

  // 整条分叉（契约 §6.3，anchor=null）：创建即跳转到新分支；bootstrap 首问由新页面加载时恢复。
  const handleForkAnswer = useCallback((messageId: string, revisionId: string) => {
    if (loaded === null) return;
    setActionError(null);
    forkBranch({ branchId: loaded.branch.id, sourceMessageId: messageId, sourceRevisionId: revisionId })
      .then((result) => { navigate(`/topic/${topicId}?branch=${result.branch.id}`); })
      .catch((reason: unknown) => setActionError(errorKey(reason)));
  }, [loaded, topicId]);

  // 选文分叉：hash 与共享域 codec 同算法（SHA-256 十六进制）；安全上下文不可用时明确拒绝。
  const handleForkSelection = useCallback((messageId: string, revisionId: string, start: number, end: number, quote: string) => {
    if (loaded === null) return;
    setActionError(null);
    sha256Hex(quote).then(
      (hash) => {
        forkBranch({ branchId: loaded.branch.id, sourceMessageId: messageId, sourceRevisionId: revisionId, anchor: { startInclusive: start, endExclusive: end, quote, hash } })
          .then((result) => { navigate(`/topic/${topicId}?branch=${result.branch.id}`); })
          .catch((reason: unknown) => setActionError(errorKey(reason)));
      },
      () => setActionError('workspace.fork.hashUnavailable'),
    );
  }, [loaded, topicId]);

  // 推荐问题直达分叉（原型 v2.2 右侧分叉热区）：来源挂最新回答，问题原子接受为首问。
  const handleForkRecommendation = useCallback((question: string) => {
    if (loaded === null) return;
    setActionError(null);
    if (model === null) { setActionError('errors.KEY_MISSING'); return; }
    const lastAi = [...loaded.messages].reverse().find((item) => item.role === 'AI');
    if (lastAi === undefined || lastAi.activeRevisionId === null) { setActionError('workspace.empty'); return; }
    forkBranch({ branchId: loaded.branch.id, sourceMessageId: lastAi.id, sourceRevisionId: lastAi.activeRevisionId, question, model, thinkingEnabled: thinking })
      .then((result) => { navigate(`/topic/${topicId}?branch=${result.branch.id}`); })
      .catch((reason: unknown) => setActionError(errorKey(reason)));
  }, [loaded, model, thinking, topicId]);

  if (error !== null) return <GlassPanel className="workspace"><p className="workspace__error" role="alert">{t(error)}</p></GlassPanel>;
  if (loaded === null) return <GlassPanel className="workspace"><p className="workspace__loading">{t('workspace.loading')}</p></GlassPanel>;

  const root = rootBranch(loaded.tree);
  const rootTitle = root?.title ?? t('nav.topic');
  const branchTitle = loaded.branch.title ?? rootTitle;
  const statusKey: TranslationKey = stream.terminal === null ? 'workspace.status.generating' : TERMINAL_LABELS[stream.terminal.kind];
  // 失败终态的 errorCode 映射到 errors.<code> 具体可行动文案；未知/缺失码回退通用「生成失败」。
  const failedCode = stream.terminal?.kind === 'failed' ? stream.terminal.errorCode : undefined;
  const statusText = failedCode !== undefined && isErrorCode(failedCode) ? t(errorI18nKey(failedCode)) : t(statusKey);

  return (
    <div className="workspace">
      <GlassPanel className="workspace__bar">
        <IconButton icon={ArrowLeft} label={t('workspace.back')} data-testid="workspace-back"
          onClick={() => navigate(historyFrom() ?? '/')} />
        <nav className="workspace__crumbs" aria-label={t('nav.topic')}>
          <h2 className="workspace__crumb workspace__crumb--root">{rootTitle}</h2>
          {loaded.branch.id !== root?.id ? (
            <>
              <Icon icon={ChevronRight} size="sm" />
              <span className="workspace__crumb workspace__crumb--current">{branchTitle}</span>
            </>
          ) : null}
        </nav>
        <IconButton icon={Network} label={t('workspace.overview')} data-testid="workspace-overview"
          onClick={() => navigate(`/topic/${topicId}/tree?branch=${loaded.branch.id}`)} />
      </GlassPanel>
      <div className="workspace__reading">
          {loaded.messages.length === 0 ? <p className="workspace__empty">{t('workspace.empty')}</p> : loaded.messages.map((message) => {
            // T-126：生成中的回答在它自己的消息位里推进；终态解除锁后，失败/中断仍留原位重试。
            const streaming = activeOperationId !== null && message.id === activeMessageId;
            const retrying = retryTarget !== null && message.id === retryTarget;
            return (
              <div key={message.id} id={`msg-${message.id}`}>
                <MessageView message={message} annotationsEnabled
                  streamText={streaming && stream.answer.length > 0 ? stream.answer : null}
                  streamReasoning={streaming && stream.reasoning.length > 0 ? stream.reasoning : null}
                  footer={streaming || retrying ? (
                    <div className="message__stream-status" aria-live="polite">
                      <span className="workspace__status-text">{statusText}</span>
                      {streamActive ? <IconButton icon={Square} label={t('workspace.action.stop')} variant="solid" onClick={handleStop} /> : null}
                      {retrying || stream.terminal?.kind === 'failed' || stream.terminal?.kind === 'interrupted' ? <IconButton icon={RotateCcw} label={t('workspace.action.retry')} onClick={handleRetry} /> : null}
                      {streamActive ? <span className="workspace__cursor" aria-hidden="true" /> : null}
                    </div>
                  ) : null}
                  onQuoteText={message.role === 'AI' ? (text) => { setQuote(text); } : undefined}
                  onReset={model !== null ? (messageId) => {
                    resetMessage(messageId, { commandId: newCommandId(), model, thinkingEnabled: thinking }).then((result) => {
                      setLoaded((prev) => (prev === null ? prev : { ...prev, messages: prev.messages.map((item) => (item.id === result.message.id ? result.message : item)) }));
                      setActiveOperationId(result.operation.id);
                      setActiveMessageId(result.message.id);
                    }).catch((reason: unknown) => setActionError(errorKey(reason)));
                  } : undefined}
                  onForkAnswer={message.role === 'AI' ? handleForkAnswer : undefined}
                  onForkSelection={message.role === 'AI' ? handleForkSelection : undefined}
                  childBranchCount={message.role === 'AI' ? (childrenByMessage.get(message.id)?.length ?? 0) : undefined}
                  onListChildren={message.role === 'AI' ? (messageId) => setChildrenFor(messageId) : undefined} />
              </div>
            );
          })}
      </div>
      {loaded !== null && (loaded.recommendations ?? []).length > 0 ? (
        <div className="workspace__recommendations" data-testid="recommendations" role="listbox" aria-label={t('workspace.recommendations.label')}>
          {(loaded.recommendations ?? []).map((rec, index) => (
            <div key={rec} className="workspace__recommendation-row">
              <button type="button" role="option" aria-selected="false" className="workspace__recommendation"
                onClick={() => handleFollowRecommendation(rec)}>{rec}</button>
              <IconButton icon={GitFork} label={t('workspace.recommendations.fork')} className="workspace__recommendation-fork"
                data-testid={`fork-recommendation-${index}`} onClick={() => handleForkRecommendation(rec)} />
            </div>
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
      <Dialog open={childrenFor !== null} title={t('workspace.children.title')} closeLabel={t('annotation.action.cancel')} onClose={() => setChildrenFor(null)}>
        {childrenFor !== null && (childrenByMessage.get(childrenFor)?.length ?? 0) === 0 ? (
          <p className="workspace__note">{t('workspace.children.empty')}</p>
        ) : (
          <ul className="workspace__children-list">
            {(childrenFor === null ? [] : childrenByMessage.get(childrenFor) ?? []).map((node) => (
              <li key={node.id}>
                <button type="button" className="workspace__children-item" data-testid={`child-${node.id}`}
                  onClick={() => { setChildrenFor(null); navigate(`/topic/${topicId}?branch=${node.id}`); }}>
                  <span className="workspace__children-title">{node.title ?? t('workspace.tree.untitled')}</span>
                  <Icon icon={ChevronRight} size="sm" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </Dialog>
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
