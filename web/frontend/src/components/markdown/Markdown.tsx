import { Fragment, useCallback, useMemo, useState, type ReactNode } from 'react';

import { useI18n } from '../../i18n/I18nProvider';
import { IconButton } from '../IconButton';
import { Copy } from '../icons';
import { parseMarkdown, type Align, type Block, type InlineNode, type TableBlock, type TextNode } from './parse';

// 渲染层（任务 T-2026-10-10-027）：把 parse.ts 的 AST 转成 React 元素树。
// 全部文本经 React 文本节点输出（自动转义），代码/表格/公式都不经过 HTML 字符串，
// 因此没有 dangerouslySetInnerHTML，恶意内容只能成为可见文本。highlight 用于并排视图
// 高亮来源消息的分叉锚点区间（半开 [start, end)，按原文偏移定位到具体文本节点）。
export interface Highlight { start: number; end: number }

const ALIGN_CLASS: Record<Align, string> = { left: 'md-align-left', center: 'md-align-center', right: 'md-align-right' };

function highlightText(node: TextNode, highlight: Highlight, key: string): ReactNode {
  if (node.end <= highlight.start || node.start >= highlight.end) return node.value;
  const localStart = Math.max(highlight.start, node.start) - node.start;
  const localEnd = Math.min(highlight.end, node.end) - node.start;
  return (
    <Fragment key={key}>
      {node.value.slice(0, localStart)}
      <mark className="message__anchor">{node.value.slice(localStart, localEnd)}</mark>
      {node.value.slice(localEnd)}
    </Fragment>
  );
}

function renderInline(nodes: InlineNode[], highlight: Highlight | null, prefix = ''): ReactNode[] {
  return nodes.map((node, index) => {
    const key = `${prefix}${index}`;
    switch (node.type) {
      case 'text':
        return <Fragment key={key}>{highlight === null ? node.value : highlightText(node, highlight, `${key}-h`)}</Fragment>;
      case 'code':
        return <code key={key} className="md-inline-code">{node.value}</code>;
      case 'math':
        return <span key={key} className="md-math">{node.value}</span>;
      case 'strong':
        return <strong key={key}>{renderInline(node.children, highlight, `${key}.`)}</strong>;
      case 'em':
        return <em key={key}>{renderInline(node.children, highlight, `${key}.`)}</em>;
      case 'link':
        return <a key={key} className="md-link" href={node.href} target="_blank" rel="noopener noreferrer">{renderInline(node.children, highlight, `${key}.`)}</a>;
      default:
        return null;
    }
  });
}

function CodeBlock({ lang, value }: { lang: string; value: string }) {
  const { t } = useI18n();
  const [status, setStatus] = useState<'idle' | 'copied' | 'failed'>('idle');
  const copy = useCallback(() => {
    const clipboard = typeof navigator === 'undefined' ? undefined : navigator.clipboard;
    if (clipboard === undefined || typeof clipboard.writeText !== 'function') {
      setStatus('failed');
      return;
    }
    try {
      Promise.resolve(clipboard.writeText(value)).then(() => setStatus('copied')).catch(() => setStatus('failed'));
    } catch {
      setStatus('failed');
    }
  }, [value]);
  const message = status === 'copied' ? t('markdown.code.copied') : status === 'failed' ? t('markdown.code.copyFailed') : '';
  const label = lang.length > 0 ? lang : t('markdown.code.label');
  return (
    <div className="md-code">
      <div className="md-code__bar">
        <span className="md-code__lang">{label}</span>
        <IconButton icon={Copy} label={t('markdown.code.copy')} onClick={copy} />
      </div>
      <pre className="md-code__pre"><code>{value}</code></pre>
      <span className="sr-only" role="status" aria-live="polite">{message}</span>
    </div>
  );
}

function TableView({ block }: { block: TableBlock }) {
  return (
    <div className="md-table-wrap">
      <table className="md-table">
        <thead>
          <tr>
            {block.header.map((cell, index) => (
              <th key={index} className={ALIGN_CLASS[block.align[index] ?? 'left']}>{renderInline(cell, null)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {block.rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, colIndex) => (
                <td key={colIndex} className={ALIGN_CLASS[block.align[colIndex] ?? 'left']}>{renderInline(cell, null)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function BlockView({ block, highlight }: { block: Block; highlight: Highlight | null }) {
  switch (block.type) {
    case 'heading':
      return block.level === 1
        ? <h1 className="md-heading">{renderInline(block.children, highlight, 'h.')}</h1>
        : block.level === 2
          ? <h2 className="md-heading">{renderInline(block.children, highlight, 'h.')}</h2>
          : <h3 className="md-heading">{renderInline(block.children, highlight, 'h.')}</h3>;
    case 'paragraph':
      return <p className="md-paragraph">{renderInline(block.children, highlight, 'p.')}</p>;
    case 'list':
      return block.ordered
        ? <ol className="md-list">{block.items.map((item, index) => <li key={index}>{renderInline(item, highlight, `o${index}.`)}</li>)}</ol>
        : <ul className="md-list">{block.items.map((item, index) => <li key={index}>{renderInline(item, highlight, `u${index}.`)}</li>)}</ul>;
    case 'quote':
      return <blockquote className="md-quote"><p className="md-paragraph">{renderInline(block.children, highlight, 'q.')}</p></blockquote>;
    case 'code':
      return <CodeBlock lang={block.lang} value={block.value} />;
    case 'table':
      return <TableView block={block} />;
    case 'hr':
      return <hr className="md-hr" />;
    case 'math':
      return <div className="md-math md-math--block">{block.value}</div>;
    default:
      return null;
  }
}

export function Markdown({ text, highlight = null }: { text: string; highlight?: Highlight | null }) {
  const blocks = useMemo(() => parseMarkdown(text), [text]);
  return <div className="md">{blocks.map((block, index) => <BlockView key={index} block={block} highlight={highlight} />)}</div>;
}