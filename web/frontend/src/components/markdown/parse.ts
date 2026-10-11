// 统一消息渲染器的解析层（任务 T-2026-10-10-027）。
// 自写最小 Markdown 解析器：输出纯数据 AST，交给 Markdown.tsx 用 React 元素渲染，
// 结构上不产生 HTML 字符串（无 dangerouslySetInnerHTML），恶意输入只能变成文本。
// 范围与 Android MarkdownRenderer 的格式范围、降级语义对齐（见功能一致性矩阵该行），
// 但不复刻其实现。范围外语法（嵌套列表深层、脚注、定义列表、setext 标题、缩进代码块、
// 图片等）一律降级为段落/原文，属可读降级。
// 有界：块数量、行内解析深度、行内长度、单行长度均有上限，超出降级为原文，避免
// 超长/深嵌套输入造成无界递归或超大工作量。

export type Align = 'left' | 'center' | 'right';

export interface TextNode { type: 'text'; value: string; start: number; end: number }
export interface CodeSpanNode { type: 'code'; value: string; start: number; end: number }
export interface MathSpanNode { type: 'math'; value: string; start: number; end: number }
export interface StrongNode { type: 'strong'; children: InlineNode[]; start: number; end: number }
export interface EmphasisNode { type: 'em'; children: InlineNode[]; start: number; end: number }
export interface LinkNode { type: 'link'; href: string; children: InlineNode[]; start: number; end: number }
export type InlineNode = TextNode | CodeSpanNode | MathSpanNode | StrongNode | EmphasisNode | LinkNode;

export interface HeadingBlock { type: 'heading'; level: 1 | 2 | 3; children: InlineNode[] }
export interface ParagraphBlock { type: 'paragraph'; children: InlineNode[] }
export interface ListBlock { type: 'list'; ordered: boolean; items: InlineNode[][] }
export interface QuoteBlock { type: 'quote'; children: InlineNode[] }
export interface CodeBlock { type: 'code'; lang: string; value: string }
export interface TableBlock { type: 'table'; align: Align[]; header: InlineNode[][]; rows: InlineNode[][][] }
export interface HrBlock { type: 'hr' }
export interface MathBlock { type: 'math'; value: string }
export type Block = HeadingBlock | ParagraphBlock | ListBlock | QuoteBlock | CodeBlock | TableBlock | HrBlock | MathBlock;

const MAX_BLOCKS = 1000;
const MAX_INLINE_DEPTH = 8;
const MAX_INLINE_LENGTH = 20000;

interface SourceLine { text: string; offset: number }

function splitLines(source: string): SourceLine[] {
  const lines: SourceLine[] = [];
  let offset = 0;
  for (const raw of source.split('\n')) {
    const text = raw.endsWith('\r') ? raw.slice(0, -1) : raw;
    lines.push({ text, offset });
    offset += raw.length + 1;
  }
  return lines;
}

function isSafeUrl(url: string): boolean {
  const scheme = /^([a-zA-Z][a-zA-Z0-9+.-]*):/.exec(url.trim());
  if (scheme === null) return false;
  const name = scheme[1].toLowerCase();
  return name === 'http' || name === 'https';
}

function matchLink(text: string, start: number): { href: string; label: string; end: number } | null {
  const closeBracket = text.indexOf(']', start + 1);
  if (closeBracket < 0 || text[closeBracket + 1] !== '(') return null;
  const closeParen = text.indexOf(')', closeBracket + 2);
  if (closeParen < 0) return null;
  const label = text.slice(start + 1, closeBracket);
  const href = text.slice(closeBracket + 2, closeParen).trim().split(/\s+/)[0] ?? '';
  return { href, label, end: closeParen + 1 };
}

function parseInline(text: string, base: number, depth = 0): InlineNode[] {
  if (depth > MAX_INLINE_DEPTH || text.length > MAX_INLINE_LENGTH) {
    return text.length === 0 ? [] : [{ type: 'text', value: text, start: base, end: base + text.length }];
  }
  const nodes: InlineNode[] = [];
  let buffer = '';
  let i = 0;
  const flush = () => {
    if (buffer.length === 0) return;
    nodes.push({ type: 'text', value: buffer, start: base + i - buffer.length, end: base + i });
    buffer = '';
  };
  while (i < text.length) {
    const ch = text[i];
    if (ch === '`') {
      const close = text.indexOf('`', i + 1);
      if (close > i) {
        flush();
        nodes.push({ type: 'code', value: text.slice(i + 1, close), start: base + i, end: base + close + 1 });
        i = close + 1;
        continue;
      }
    } else if (ch === '[') {
      const link = matchLink(text, i);
      if (link !== null && isSafeUrl(link.href)) {
        flush();
        nodes.push({ type: 'link', href: link.href, children: parseInline(link.label, base + i + 1, depth + 1), start: base + i, end: base + link.end });
        i = link.end;
        continue;
      }
    } else if (ch === '$' && text[i - 1] !== '$' && text[i + 1] !== '$') {
      const close = text.indexOf('$', i + 1);
      if (close > i) {
        flush();
        nodes.push({ type: 'math', value: text.slice(i + 1, close), start: base + i, end: base + close + 1 });
        i = close + 1;
        continue;
      }
    } else if (text.startsWith('**', i) || text.startsWith('__', i)) {
      const marker = text.slice(i, i + 2);
      const close = text.indexOf(marker, i + 2);
      if (close > i + 1) {
        flush();
        nodes.push({ type: 'strong', children: parseInline(text.slice(i + 2, close), base + i + 2, depth + 1), start: base + i, end: base + close + 2 });
        i = close + 2;
        continue;
      }
    } else if (ch === '*' || ch === '_') {
      const close = text.indexOf(ch, i + 1);
      if (close > i + 1) {
        flush();
        nodes.push({ type: 'em', children: parseInline(text.slice(i + 1, close), base + i + 1, depth + 1), start: base + i, end: base + close + 1 });
        i = close + 1;
        continue;
      }
    }
    buffer += ch;
    i += 1;
  }
  flush();
  return nodes;
}

function matchFence(text: string): { char: string; length: number; lang: string } | null {
  const match = /^\s{0,3}(`{3,}|~{3,})(.*)$/.exec(text);
  if (match === null) return null;
  const marker = match[1];
  return { char: marker[0], length: marker.length, lang: (match[2].trim().split(/\s+/)[0] ?? '') };
}

function isClosingFence(text: string, char: string, min: number): boolean {
  const match = /^\s{0,3}(`{3,}|~{3,})\s*$/.exec(text);
  return match !== null && match[1][0] === char && match[1].length >= min;
}

function matchHeading(text: string): { level: 1 | 2 | 3; content: string; contentOffset: number } | null {
  const match = /^(#{1,3})[ \t]+(.*)$/.exec(text);
  if (match === null) return null;
  return { level: match[1].length as 1 | 2 | 3, content: match[2], contentOffset: match[0].length - match[2].length };
}

function isHr(text: string): boolean {
  return /^\s{0,3}(-{3,}|\*{3,}|_{3,})\s*$/.test(text);
}

function matchListItem(text: string): { ordered: boolean; content: string; contentOffset: number } | null {
  const bullet = /^\s{0,3}([-*+])[ \t]+(.*)$/.exec(text);
  if (bullet !== null) return { ordered: false, content: bullet[2], contentOffset: bullet[0].length - bullet[2].length };
  const ordered = /^\s{0,3}(\d{1,9})[.)][ \t]+(.*)$/.exec(text);
  if (ordered !== null) return { ordered: true, content: ordered[2], contentOffset: ordered[0].length - ordered[2].length };
  return null;
}

function isBlockMathStart(text: string): boolean {
  return text.trim().startsWith('$$');
}

function splitTableRow(text: string): string[] {
  let body = text.trim();
  if (body.startsWith('|')) body = body.slice(1);
  if (body.endsWith('|')) body = body.slice(0, -1);
  return body.split('|').map((cell) => cell.trim());
}

function alignOf(cell: string): Align {
  const value = cell.trim();
  const left = value.startsWith(':');
  const right = value.endsWith(':');
  if (left && right) return 'center';
  if (right) return 'right';
  return 'left';
}

function isDelimiterRow(text: string): boolean {
  const cells = splitTableRow(text);
  return cells.length > 0 && cells.every((cell) => /^:?-{1,}:?$/.test(cell));
}

function isBlockStarter(lines: SourceLine[], index: number): boolean {
  const text = lines[index].text;
  if (matchFence(text) !== null) return true;
  if (isBlockMathStart(text)) return true;
  if (matchHeading(text) !== null) return true;
  if (isHr(text)) return true;
  if (/^\s{0,3}>/.test(text)) return true;
  if (matchListItem(text) !== null) return true;
  if (text.includes('|') && index + 1 < lines.length && isDelimiterRow(lines[index + 1].text)) return true;
  return false;
}

function readFence(lines: SourceLine[], start: number, fence: { char: string; length: number; lang: string }): { next: number; block: CodeBlock } {
  const body: string[] = [];
  let i = start + 1;
  for (; i < lines.length; i++) {
    if (isClosingFence(lines[i].text, fence.char, fence.length)) { i += 1; break; }
    body.push(lines[i].text);
  }
  return { next: i, block: { type: 'code', lang: fence.lang, value: body.join('\n') } };
}

function readMath(lines: SourceLine[], start: number): { next: number; block: Block } {
  const head = lines[start].text.trim();
  if (head.length > 4 && head.endsWith('$$')) {
    return { next: start + 1, block: { type: 'math', value: head.slice(2, -2) } };
  }
  const body: string[] = [];
  for (let i = start + 1; i < lines.length; i++) {
    if (lines[i].text.trim() === '$$') return { next: i + 1, block: { type: 'math', value: body.join('\n') } };
    body.push(lines[i].text);
  }
  const literal = lines.slice(start).map((line) => line.text).join('\n');
  return { next: lines.length, block: { type: 'paragraph', children: parseInline(literal, lines[start].offset) } };
}

function readQuote(lines: SourceLine[], start: number): { next: number; block: QuoteBlock } {
  const parts: string[] = [];
  let i = start;
  for (; i < lines.length; i++) {
    const match = /^\s{0,3}>[ \t]?(.*)$/.exec(lines[i].text);
    if (match === null) break;
    parts.push(match[1]);
  }
  const marker = /^\s{0,3}>[ \t]?/.exec(lines[start].text);
  const contentStart = lines[start].offset + (marker === null ? 0 : marker[0].length);
  return { next: i, block: { type: 'quote', children: parseInline(parts.join('\n'), contentStart) } };
}

function readList(lines: SourceLine[], start: number): { next: number; block: ListBlock } {
  const first = matchListItem(lines[start].text) as NonNullable<ReturnType<typeof matchListItem>>;
  const items: InlineNode[][] = [];
  let i = start;
  for (; i < lines.length; i++) {
    const item = matchListItem(lines[i].text);
    if (item === null || item.ordered !== first.ordered) break;
    items.push(parseInline(item.content, lines[i].offset + item.contentOffset));
  }
  return { next: i, block: { type: 'list', ordered: first.ordered, items } };
}

function readTable(lines: SourceLine[], start: number): { next: number; block: TableBlock } {
  const align = splitTableRow(lines[start + 1].text).map(alignOf);
  const header: InlineNode[][] = splitTableRow(lines[start].text).map((cell) => parseInline(cell, 0));
  const rows: InlineNode[][][] = [];
  let i = start + 2;
  for (; i < lines.length; i++) {
    if (lines[i].text.trim() === '' || !lines[i].text.includes('|')) break;
    rows.push(splitTableRow(lines[i].text).map((cell) => parseInline(cell, 0)));
  }
  return { next: i, block: { type: 'table', align, header, rows } };
}

function readParagraph(lines: SourceLine[], start: number): { next: number; block: ParagraphBlock } {
  const parts: string[] = [];
  let i = start;
  for (; i < lines.length; i++) {
    if (lines[i].text.trim() === '') break;
    if (i > start && isBlockStarter(lines, i)) break;
    parts.push(lines[i].text);
  }
  return { next: i, block: { type: 'paragraph', children: parseInline(parts.join('\n'), lines[start].offset) } };
}

export function parseMarkdown(source: string): Block[] {
  const blocks: Block[] = [];
  if (source.length === 0) return blocks;
  const lines = splitLines(source);
  let i = 0;
  const push = (read: { next: number; block: Block }) => { blocks.push(read.block); i = read.next; };
  while (i < lines.length && blocks.length < MAX_BLOCKS) {
    if (lines[i].text.trim() === '') { i += 1; continue; }
    const fence = matchFence(lines[i].text);
    if (fence !== null) { push(readFence(lines, i, fence)); continue; }
    if (isBlockMathStart(lines[i].text)) { push(readMath(lines, i)); continue; }
    const heading = matchHeading(lines[i].text);
    if (heading !== null) { blocks.push({ type: 'heading', level: heading.level, children: parseInline(heading.content, lines[i].offset + heading.contentOffset) }); i += 1; continue; }
    if (isHr(lines[i].text)) { blocks.push({ type: 'hr' }); i += 1; continue; }
    if (/^\s{0,3}>/.test(lines[i].text)) { push(readQuote(lines, i)); continue; }
    if (matchListItem(lines[i].text) !== null) { push(readList(lines, i)); continue; }
    if (lines[i].text.includes('|') && i + 1 < lines.length && isDelimiterRow(lines[i + 1].text)) { push(readTable(lines, i)); continue; }
    push(readParagraph(lines, i));
  }
  if (i < lines.length) {
    const rest = lines.slice(i).map((line) => line.text).join('\n');
    blocks.push({ type: 'paragraph', children: parseInline(rest, lines[i].offset) });
  }
  return blocks;
}