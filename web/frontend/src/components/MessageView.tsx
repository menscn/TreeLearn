import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { Annotation, Message } from '../api/types';
import { useI18n } from '../i18n/I18nProvider';
import { createAnnotation, deleteAnnotation, loadAnnotations } from '../store/topicData';
import { Dialog } from './Dialog';
import { IconButton } from './IconButton';
import { TranslationBlock } from './TranslationBlock';
import { speak, speechAvailable, stopSpeaking } from '../lib/speech';
import { selectRevision } from '../store/topicData';
import { Languages, RotateCcw, Trash2, Volume2 } from './icons';
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

function MessageView({ message, highlight = null, annotationsEnabled = false, onQuoteText, onReset }:
{ message: Message; highlight?: TextHighlight | null; annotationsEnabled?: boolean; onQuoteText?: (text: string) => void; onReset?: (messageId: string) => void }) {
  const { t } = useI18n();
  const answerRef = useRef<HTMLDivElement | null>(null);
  const [annotations, setAnnotations] = useState<Annotation[]>([]);
  const [pending, setPending] = useState<Highlight | null>(null);
  const [pendingIndex, setPendingIndex] = useState(-1);
  const [noteDraft, setNoteDraft] = useState('');
  const [noteOpen, setNoteOpen] = useState(false);
  const [speaking, setSpeaking] = useState(false);
  const [translationOpen, setTranslationOpen] = useState(false);
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
  const reasoning = revision?.reasoningContent ?? '';
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
        <Markdown
          text={revision?.answerContent ?? ''}
          highlight={highlight}
          highlights={annotationsEnabled ? proseHighlights : undefined}
          withOffsets={annotationsEnabled}
        />
      </div>
      <div className="message__tools" data-testid={`tools-${message.id}`}>
        {revision !== null && speechAvailable() ? (
          <IconButton icon={Volume2} label={t(speaking ? 'workspace.action.stopSpeech' : 'workspace.action.speak')}
            onClick={() => {
              if (speaking) {
                stopSpeaking();
                setSpeaking(false);
                return;
              }
              speak(revision.answerContent);
              setSpeaking(true);
            }} />
        ) : null}
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
        {revision !== null ? (
          <IconButton icon={Languages} label={t('workspace.translation.action')} onClick={() => setTranslationOpen((prev) => !prev)} />
        ) : null}
        {onReset && message.versions.length > 0 ? (
          <IconButton icon={RotateCcw} label={t('workspace.action.reset')} onClick={() => onReset(message.id)} />
        ) : null}
      </div>
      {revision !== null && translationOpen ? <TranslationBlock messageId={message.id} revisionId={revision.id} /> : null}
      {annotationsEnabled && pending !== null ? (
        <div className="annotation-bar" role="toolbar" aria-label={t('annotation.bar.label')}>
          <button type="button" className="annotation-bar__action" data-testid="annotation-highlight"
            onClick={() => write({ messageId: message.id, kind: 'HIGHLIGHT', container: 'PROSE', proseIndex: pendingIndex, start: pending.start, end: pending.end })}>
            {t('annotation.action.highlight')}
          </button>
          <button type="button" className="annotation-bar__action" data-testid="annotation-note"
            onClick={() => setNoteOpen(true)}>
            {t('annotation.action.note')}
          </button>
          {onQuoteText ? (
            <button type="button" className="annotation-bar__action" data-testid="annotation-quote"
              onClick={() => { onQuoteText(answerText.slice(pending.start, pending.end)); closePending(); }}>
              {t('annotation.action.quote')}
            </button>
          ) : null}
          <button type="button" className="annotation-bar__action" data-testid="annotation-cancel" onClick={closePending}>
            {t('annotation.action.cancel')}
          </button>
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
    </article>
  );
}

export { MessageView };
