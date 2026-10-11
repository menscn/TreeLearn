import { Fragment, useCallback, useMemo, useState, type ReactNode } from 'react';

import { useI18n } from '../../i18n/I18nProvider';
import { IconButton } from '../IconButton';
import { Copy } from '../icons';
import { parseMarkdown, type Align, type Block, type InlineNode, type TableBlock, type TextNode } from './parse';

// 渲染层（任务 T-2026-10-10-027）：把 parse.ts 的 AST 转成 React 元素树。
// 全部文本经 React 文本节点输出（自动转义），代码/表格/公式都不经过 HTML 字符串，
// 因此没有 dangerouslySetInnerHTML，恶意内容只能成为可见文本。highlight 用于并排视图
// 高亮来源消息的分叉锚点区间（半开 [start, end)，按原文偏移定位到具体文本节点）。
// T-049 扩展：`highlights` 支持多个批注区间同渲染；`withOffsets` 让文本节点携带
// data-md-start/data-md-end（原文偏移），供划选批注把 DOM Selection 映射回原文区间。
export interface Highlight { start: number; end: number }

const ALIGN_CLASS: Record<Align, string> = { left: 'md-align-left', center: 'md-align-center', right: 'md-align-right' };

function overlapping(node: TextNode, range: Highlight): boolean {
  return node.end > range.start && node.start < range.end;
}

function textNodeView(node: TextNode, highlights: Highlight[], withOffsets: boolean, key: string): ReactNode {
  const active = highlights.filter((range) => overlapping(node, range));
  if (active.length === 0) {
    return withOffsets
      ? <span key={key} data-md-start={node.start} data-md-end={node.end}>{node.value}</span>
      : node.value;
  }
  // 逐字符切分：任一活跃区间覆盖到的字符进 <mark>，其余保持普通段；每段带原文偏移。
  const parts: ReactNode[] = [];
  let cursor = node.start;
  const emit = (from: number, to: number, marked: boolean, partKey: string) => {
    if (to <= from) return;
    const value = node.value.slice(from - node.start, to - node.start);
    parts.push(marked
      ? <mark key={partKey} className="message__anchor" data-md-start={from} data-md-end={to}>{value}</mark>
      : <span key={partKey} data-md-start={from} data-md-end={to}>{value}</span>);
  };
  const boundaries = [...new Set(active.flatMap((range) => [Math.max(range.start, node.start), Math.min(range.end, node.end)]))]
    .sort((a, b) => a - b);
  for (const boundary of boundaries) {
    const marked = active.some((range) => boundary > range.start && boundary <= range.end && boundary > node.start && boundary <= node.end);
    emit(cursor, boundary, marked, `${key}-${cursor}`);
    cursor = boundary;
  }
  emit(cursor, node.end, false, `${key}-${cursor}`);
  return <Fragment key={key}>{parts}</Fragment>;
}

function renderInline(nodes: InlineNode[], highlights: Highlight[], withOffsets: boolean, prefix = ''): ReactNode[] {
  return nodes.map((node, index) => {
    const key = `${prefix}${index}`;
    switch (node.type) {
      case 'text':
        return textNodeView(node, highlights, withOffsets, `${key}-t`);
      case 'code':
        return <code key={key} className="md-inline-code">{node.value}</code>;
      case 'math':
        return <span key={key} className="md-math">{node.value}</span>;
      case 'strong':
        return <strong key={key}>{renderInline(node.children, highlights, withOffsets, `${key}.`)}</strong>;
      case 'em':
        return <em key={key}>{renderInline(node.children, highlights, withOffsets, `${key}.`)}</em>;
      case 'link':
        return <a key={key} className="md-link" href={node.href} target="_blank" rel="noopener noreferrer">{renderInline(node.children, highlights, withOffsets, `${key}.`)}</a>;
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

function TableView({ block, highlights, withOffsets }: { block: TableBlock; highlights: Highlight[]; withOffsets: boolean }) {
  return (
    <div className="md-table-wrap">
      <table className="md-table">
        <thead>
          <tr>
            {block.header.map((cell, index) => (
              <th key={index} className={ALIGN_CLASS[block.align[index] ?? 'left']}>{renderInline(cell, highlights, withOffsets, `th${index}.`)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {block.rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, colIndex) => (
                <td key={colIndex} className={ALIGN_CLASS[block.align[colIndex] ?? 'left']}>{renderInline(cell, highlights, withOffsets, `td${rowIndex}.${colIndex}.`)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function BlockView({ block, highlights, withOffsets }: { block: Block; highlights: Highlight[]; withOffsets: boolean }) {
  switch (block.type) {
    case 'heading':
      return block.level === 1
        ? <h1 className="md-heading">{renderInline(block.children, highlights, withOffsets, 'h.')}</h1>
        : block.level === 2
          ? <h2 className="md-heading">{renderInline(block.children, highlights, withOffsets, 'h.')}</h2>
          : <h3 className="md-heading">{renderInline(block.children, highlights, withOffsets, 'h.')}</h3>;
    case 'paragraph':
      return <p className="md-paragraph">{renderInline(block.children, highlights, withOffsets, 'p.')}</p>;
    case 'list':
      return block.ordered
        ? <ol className="md-list">{block.items.map((item, index) => <li key={index}>{renderInline(item, highlights, withOffsets, `o${index}.`)}</li>)}</ol>
        : <ul className="md-list">{block.items.map((item, index) => <li key={index}>{renderInline(item, highlights, withOffsets, `u${index}.`)}</li>)}</ul>;
    case 'quote':
      return <blockquote className="md-quote"><p className="md-paragraph">{renderInline(block.children, highlights, withOffsets, 'q.')}</p></blockquote>;
    case 'code':
      return <CodeBlock lang={block.lang} value={block.value} />;
    case 'table':
      return <TableView block={block} highlights={highlights} withOffsets={withOffsets} />;
    case 'hr':
      return <hr className="md-hr" />;
    case 'math':
      return <div className="md-math md-math--block">{block.value}</div>;
    default:
      return null;
  }
}

export function Markdown({ text, highlight = null, highlights, withOffsets = false }:
{ text: string; highlight?: Highlight | null; highlights?: Highlight[]; withOffsets?: boolean }) {
  const blocks = useMemo(() => parseMarkdown(text), [text]);
  // 旧单值 prop 与新数组取并集（SideBySideView 用 highlight，批注用 highlights）。
  const all = useMemo(
    () => [...(highlight === null ? [] : [highlight]), ...(highlights ?? [])],
    [highlight, highlights],
  );
  return <div className="md">{blocks.map((block, index) => <BlockView key={index} block={block} highlights={all} withOffsets={withOffsets} />)}</div>;
}
