import type { Message } from '../api/types';
import { useI18n } from '../i18n/I18nProvider';
import { Markdown } from './markdown/Markdown';

// 消息位渲染（任务 T-2026-10-10-031 引入安全纯文本；任务 T-2026-10-10-027 升级为统一
// Markdown 渲染器）。内容经 React 元素渲染（默认转义、无 HTML 注入）：USER 位带引用段
// （quotedText 非空时以 blockquote 呈现）；AI 位显示活跃修订的回答，思考过程折叠。
// `highlight` 供并排视图按原文偏移在来源消息的回答里高亮分叉锚点区间。
export type TextHighlight = { start: number; end: number };

export function activeRevision(message: Message) {
  return message.versions.find((version) => version.id === message.activeRevisionId) ?? message.versions[message.versions.length - 1] ?? null;
}

export function MessageView({ message, highlight = null }: { message: Message; highlight?: TextHighlight | null }) {
  const { t } = useI18n();
  if (message.role === 'USER') {
    return (
      <article className="message message--user">
        {message.quotedText.length > 0 ? <blockquote className="message__quote">{message.quotedText}</blockquote> : null}
        <div className="message__user"><Markdown text={message.userContent ?? ''} /></div>
      </article>
    );
  }
  const revision = activeRevision(message);
  const reasoning = revision?.reasoningContent ?? '';
  return (
    <article className="message message--ai">
      {reasoning.length > 0 ? (
        <details className="message__reasoning">
          <summary>{t('workspace.reasoning.toggle')}</summary>
          <p>{reasoning}</p>
        </details>
      ) : null}
      <div className="message__answer"><Markdown text={revision?.answerContent ?? ''} highlight={highlight} /></div>
    </article>
  );
}