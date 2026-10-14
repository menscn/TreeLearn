import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';

import type { Annotation, Message } from '../api/types';
import { useI18n } from '../i18n/I18nProvider';
import { createAnnotation, deleteAnnotation, loadAnnotations } from '../store/topicData';
import { Dialog } from './Dialog';
import { IconButton } from './IconButton';
import { TranslationBlock } from './TranslationBlock';
import { selectRevision } from '../store/topicData';
import { formatRelativeTime } from '../platform/datetime';
import { Copy, GitFork, Highlighter, MessageSquare, Network, RotateCcw, Sparkles, TextSelect, Trash2 } from './icons';
import { Markdown, type Highlight } from './markdown/Markdown';
import { proseIndexForRange, selectionToRange } from '../lib/selectionRange';

// 消息位渲染（任务 T-2026-10-10-031 引入安全纯文本；任务 T-2026-10-10-027 升级为统一
// Markdown 渲染器）。内容经 React 元素渲染（默认转义、无 HTML 注入）：USER 位带引用段
// （quotedText 非空时以 blockquote 呈现）；AI 位显示活跃修订的回答，思考过程折叠。
// `highlight` 供并排视图按原文偏移在来源消息的回答里高亮分叉锚点区间。
// T-049 扩展：`annotationsEnabled` 打开批注消费——加载 §6.10 批注、渲染 PROSE 高亮、
// 划选答案弹出高亮/批注操作条、批注列表与删除；默认关闭（并排视图等既有用法零改动）。
export type TextHighlight = { start: number; end: number };

export function activeRevision(message: Message) {
  return message.versions.find((version) => version.id === message.activeRevisionId) ?? message.versions[message.versions.length - 1] ?? null;
}

function AnnotationList({ message, annotations, onDeleted }:
{ message: Message; annotations: Annotation[]; onDeleted: (id: string) => void }) {
  const { t } = useI18n();
  const revision = activeRevision(message);
  const answer = revision?.answerContent ?? '';
  if (annotations.length === 0) return null;
  return (
    <ul className="annotation-list" data-testid="annotation-list">
      {annotations.map((annotation) => {
        const quote = answer.slice(annotation.start, annotation.end);
        return (
          <li key={annotation.id} className={`annotation-list__item annotation-list__item--${annotation.kind.toLowerCase()}`}>
            <span className="annotation-list__kind">{t(annotation.kind === 'HIGHLIGHT' ? 'annotation.kind.highlight' : 'annotation.kind.note')}</span>
            <q className="annotation-list__quote">{quote}</q>
            {annotation.body.length > 0 ? <span className="annotation-list__body">{annotation.body}</span> : null}
            <IconButton icon={Trash2} label={t('annotation.delete')} onClick={() => onDeleted(annotation.id)} />
          </li>
        );
      })}
    </ul>
  );
}

function MessageView({ message, highlight = null, annotationsEnabled = false, onQuoteText, onReset, onForkAnswer, onForkSelection, childBranchCount, onListChildren, streamText = null, streamReasoning = null, footer = null }:
{ message: Message; highlight?: TextHighlight | null; annotationsEnabled?: boolean; onQuoteText?: (text: string) => void; onReset?: (messageId: string) => void; onForkAnswer?: (messageId: string, revisionId: string) => void; onForkSelection?: (messageId: string, revisionId: string, start: number, end: number, quote: string) => void; childBranchCount?: number; onListChildren?: (messageId: string) => void;
  /** T-126：非空时以流式正文替代修订内容（生成在回答位内推进，不再有底部面板）。 */
  streamText?: string | null;
  /** T-126：非空时以流式思考替代修订里的思考内容。 */
  streamReasoning?: string | null;
  /** T-126：回答位底部区域（生成状态行 + 停止/原位重试）。 */
  footer?: ReactNode | null }) {
  const { t, locale } = useI18n();
  const answerRef = useRef<HTMLDivElement | null>(null);
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [pending, setPending] = useState<Highlight | null>(null);
  const [pendingIndex, setPendingIndex] = useState(-1);
  const [noteDraft, setNoteDraft] = useState('');
  const [noteOpen, setNoteOpen] = useState(false);
  // T-128：翻译卡常驻渲染（翻译入口由卡片自己承载，动作行不再放翻译图标）。
  // 复制回答的二态反馈：copied/failed 各自 2s 后复位；失败不暴露底层异常，只换可行动文案。
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');
  const copyTimer = useRef<number | null>(null);
  useEffect(() => () => { if (copyTimer.current !== null) window.clearTimeout(copyTimer.current); }, []);
  // 显示版本覆盖：切版本持久化后本地立即生效（conversation 刷新由父组件拉取兜底）。
  const [displayRevisionId, setDisplayRevisionId] = useState<string | null>(null);
  const revisionMemo = activeRevision(message);
  const answerText = revisionMemo?.answerContent ?? '';

  useEffect(() => {
    if (!annotationsEnabled || message.role !== 'AI') return;
    let cancelled = false;
    // 批注加载失败不阻塞消息渲染（列表静默为空）。
    loadAnnotations(message.id).then((result) => {
      if (!cancelled) setAnnotations(result.annotations);
    }).catch(() => {});
    return () => { cancelled = true; };
  }, [annotationsEnabled, message.id, message.role]);

  const proseHighlights = useMemo(
    () => annotations
      .filter((item) => item.kind === 'HIGHLIGHT' && item.container === 'PROSE')
      .map((item) => ({ start: item.start, end: item.end })),
    [annotations],
  );

  const closePending = useCallback(() => {
    setPending(null);
    setPendingIndex(-1);
    const selection = typeof window !== 'undefined' ? window.getSelection() : null;
    selection?.removeAllRanges();
  }, []);

  const onSelectionDone = useCallback(() => {
    if (!annotationsEnabled || message.role !== 'AI') return;
    const container = answerRef.current;
    const selection = typeof window !== 'undefined' ? window.getSelection() : null;
    const range = container === null || selection === null ? null : selectionToRange(container, selection);
    if (range === null) {
      setPending(null);
      return;
    }
    setPending(range);
    setPendingIndex(container === null ? -1 : proseIndexForRange(container, selection));
  }, [annotationsEnabled, message.role]);

  const write = useCallback(async (request: Parameters<typeof createAnnotation>[0]) => {
    const result = await createAnnotation(request);
    setAnnotations(result.annotations);
    closePending();
  }, [closePending]);

  const remove = useCallback(async (id: string) => {
    await deleteAnnotation(id);
    setAnnotations((current) => current.filter((item) => item.id !== id));
  }, []);

  const submitNote = useCallback(() => {
    if (pending === null || noteDraft.trim().length === 0) return;
    write({ messageId: message.id, kind: 'NOTE', container: 'PROSE', proseIndex: pendingIndex, start: pending.start, end: pending.end, body: noteDraft.trim() })
      .then(() => { setNoteDraft(''); setNoteOpen(false); })
      // 写入失败保持弹窗内容，用户可重试。
      .catch(() => {});
  }, [message.id, noteDraft, pending, pendingIndex, write]);

  // 复制整条回答（最终文本；思考过程不并入）。非安全上下文无 clipboard 时按失败处理。
  const handleCopyAnswer = useCallback(() => {
    const current = activeRevision(message);
    if (current === null) return;
    const clipboard = navigator.clipboard;
    if (clipboard === undefined) {
      setCopyState('failed');
    } else {
      clipboard.writeText(current.answerContent).then(() => setCopyState('copied')).catch(() => setCopyState('failed'));
    }
    if (copyTimer.current !== null) window.clearTimeout(copyTimer.current);
    copyTimer.current = window.setTimeout(() => setCopyState('idle'), 2000);
  }, [message]);

  // T-129 划词菜单的复制格：只复制选中的片段（不及物到整条回答，也不并入思考过程）。
  const copySelection = useCallback((text: string) => {
    const clipboard = navigator.clipboard;
    if (clipboard === undefined) {
      setCopyState('failed');
    } else {
      clipboard.writeText(text).then(() => setCopyState('copied')).catch(() => setCopyState('failed'));
    }
    if (copyTimer.current !== null) window.clearTimeout(copyTimer.current);
    copyTimer.current = window.setTimeout(() => setCopyState('idle'), 2000);
  }, []);

  // T-129 全选：把选区就地扩成整条回答（Android WordMenuPopup 注释：「全选 widens that same
  // selection in place rather than remembering a second one」），于是高亮/复制/问 AI 读到的
  // 仍是同一个选区，不存在第二份状态。
  const selectAllAnswer = useCallback(() => {
    const container = answerRef.current;
    if (container === null || answerText.length === 0) return;
    const selection = typeof window === 'undefined' ? null : window.getSelection();
    if (selection !== null) {
      const range = document.createRange();
      range.selectNodeContents(container);
      selection.removeAllRanges();
      selection.addRange(range);
    }
    setPending({ start: 0, end: answerText.length });
    setPendingIndex(selection === null ? -1 : proseIndexForRange(container, selection));
  }, [answerText]);

  // 划词菜单的退场：Android 没有「取消」格，点空白处收起；Web 上等价的是 Esc。
  useEffect(() => {
    if (pending === null) return;
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') closePending(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [pending, closePending]);

  if (message.role === 'USER') {
    return (
      <article className="message message--user">
        {message.quotedText.length > 0 ? <blockquote className="message__quote">{message.quotedText}</blockquote> : null}
        {message.attachments.length > 0 ? (
          <div className="message__attachments" data-testid={`attachments-${message.id}`}>
            {message.attachments.map((ref) => (
              <a key={ref.attachmentId} className={ref.present ? 'message__attachment' : 'message__attachment message__attachment--missing'}
                href={`/api/attachments/${ref.attachmentId}/content`} download={ref.displayName ?? undefined}>
                {ref.present ? (ref.displayName ?? t('home.composer.attachment.unnamed')) : t('home.composer.attachment.missing')}
              </a>
            ))}
          </div>
        ) : null}
        <div className="message__user"><Markdown text={message.userContent ?? ''} /></div>
      </article>
    );
  }
  const revision = displayRevisionId === null ? activeRevision(message)
    : message.versions.find((version) => version.id === displayRevisionId) ?? activeRevision(message);
  const reasoning = streamReasoning !== null ? streamReasoning : (revision?.reasoningContent ?? '');
  // T-128 动作行（Android view_answer_actions.xml / AnswerActionBar）：
  // 只留四个纯图标（复制 / 重新生成 / 新建分支 / 分支列表）+ 行尾相对时间；生成中整行隐藏。
  // 分支列表是树形图标加右上角绿色计数徽标，无子分支时整项不出现（不是禁用）。
  // 版本切换：Android 也还没安置新入口（onVersionPressed 无调用方），Web 保留能力但移出四图标行。
  // 翻译入口由 TranslationBlock 自己承载（Android：the translation card keeps its own header entry）。
  return (
    <article className="message message--ai">
      {reasoning.length > 0 ? (
        <details className="message__reasoning">
          <summary>{t('workspace.reasoning.toggle')}</summary>
          <p>{reasoning}</p>
        </details>
      ) : null}
      <div
        className="message__answer"
        ref={answerRef}
        onMouseUp={annotationsEnabled ? onSelectionDone : undefined}
        onTouchEnd={annotationsEnabled ? onSelectionDone : undefined}
      >
        {streamText !== null ? (
          <Markdown text={streamText} />
        ) : (
          <Markdown
            text={revision?.answerContent ?? ''}
            highlight={highlight}
            highlights={annotationsEnabled ? proseHighlights : undefined}
            withOffsets={annotationsEnabled}
          />
        )}
      </div>
      {streamText === null ? (
      <>
      <div className="message__actions" data-testid={`actions-${message.id}`}>
        {revision !== null ? (
          <IconButton icon={Copy}
            label={copyState === 'failed' ? t('workspace.action.copyFailed') : copyState === 'copied' ? t('markdown.code.copied') : t('workspace.action.copyAnswer')}
            onClick={handleCopyAnswer} />
        ) : null}
        {onReset && message.versions.length > 0 ? (
          <IconButton icon={RotateCcw} label={t('workspace.action.reset')} onClick={() => onReset(message.id)} />
        ) : null}
        {onForkAnswer && revision !== null && message.activeOperationId === null ? (
          <IconButton icon={GitFork} label={t('workspace.action.fork')} data-testid={`fork-${message.id}`}
            onClick={() => onForkAnswer(message.id, revision.id)} />
        ) : null}
        {onListChildren && (childBranchCount ?? 0) > 0 ? (
          <span className="message__action-badge">
            <IconButton icon={Network} label={t('workspace.action.children').replace('{n}', String(childBranchCount ?? 0))}
              data-testid={`children-${message.id}`} onClick={() => onListChildren(message.id)} />
            <span className="message__action-count" aria-hidden="true">{childBranchCount ?? 0}</span>
          </span>
        ) : null}
        <span className="message__actions-spacer" />
        <span className="message__time" data-testid={`time-${message.id}`}>{formatRelativeTime(message.createdAtMillis, locale)}</span>
      </div>
      {revision !== null && message.versions.length > 1 ? (
        <label className="message__versions">
          <span className="message__versions-label">{t('workspace.versions.label')}</span>
          <select value={revision.id} data-testid={`versions-${message.id}`}
            onChange={(event) => {
              const target = event.currentTarget.value;
              selectRevision(message.id, target).then(() => setDisplayRevisionId(target)).catch(() => {});
            }}>
            {message.versions.map((version) => (
              <option key={version.id} value={version.id}>
                {t('workspace.versions.option').replace('{n}', String(version.revisionNo))}
              </option>
            ))}
          </select>
        </label>
      ) : null}
      </>
      ) : null}
      {revision !== null ? <TranslationBlock messageId={message.id} revisionId={revision.id} /> : null}
      {annotationsEnabled && streamText === null && pending !== null ? (
        <div className="word-menu" role="toolbar" aria-label={t('annotation.bar.label')} data-testid="word-menu">
          {onQuoteText ? (
            <IconButton icon={Sparkles} label={t('annotation.action.quote')} data-testid="annotation-quote"
              onClick={() => { onQuoteText(answerText.slice(pending.start, pending.end)); closePending(); }} />
          ) : null}
          {onForkSelection && revision !== null ? (
            <IconButton icon={GitFork} label={t('annotation.action.fork')} data-testid="annotation-fork"
              onClick={() => {
                onForkSelection(message.id, revision.id, pending.start, pending.end, revision.answerContent.slice(pending.start, pending.end));
                closePending();
              }} />
          ) : null}
          <IconButton icon={Highlighter} label={t('annotation.action.highlight')} data-testid="annotation-highlight"
            onClick={() => write({ messageId: message.id, kind: 'HIGHLIGHT', container: 'PROSE', proseIndex: pendingIndex, start: pending.start, end: pending.end })} />
          <IconButton icon={MessageSquare} label={t('annotation.action.note')} data-testid="annotation-note"
            onClick={() => setNoteOpen(true)} />
          <IconButton icon={Copy} label={t('annotation.action.copy')} data-testid="annotation-copy"
            onClick={() => { void copySelection(answerText.slice(pending.start, pending.end)); }} />
          <IconButton icon={TextSelect} label={t('annotation.action.selectAll')} data-testid="annotation-select-all"
            onClick={selectAllAnswer} />
        </div>
      ) : null}
      {annotationsEnabled
        ? <AnnotationList message={message} annotations={annotations} onDeleted={(id) => void remove(id)} />
        : null}
      <Dialog open={noteOpen} title={t('annotation.dialog.title')} closeLabel={t('annotation.action.cancel')} onClose={() => setNoteOpen(false)}>
        <textarea
          className="annotation-dialog__input"
          data-testid="annotation-note-input"
          aria-label={t('annotation.dialog.title')}
          value={noteDraft}
          onChange={(event) => setNoteDraft(event.target.value)}
          rows={3}
        />
        <button type="button" className="annotation-dialog__submit" data-testid="annotation-note-submit" onClick={submitNote}>
          {t('annotation.dialog.submit')}
        </button>
      </Dialog>
      {footer}
    </article>
  );
}

export { MessageView };
