// T-049：把 DOM Selection 映射回 Markdown 原文偏移（UTF-16 半开区间 [start, end)）。
// 依赖 Markdown 渲染层用 data-md-start/data-md-end 标注每个文本段的原文偏移
// （见 markdown/Markdown.tsx 的 withOffsets）。选区可以起止在 span 边界上——此时
// Selection 的边界容器是父元素而非文本节点，交集计算按「文本节点与 range 的重叠」处理。
import type { Highlight } from '../components/markdown/Markdown';

function intersecting(range: Range, node: Text): boolean {
  const probe = document.createRange();
  probe.selectNodeContents(node);
  // node 完全在 range 终点之前 → END_TO_START >= 0 则不相交；完全在起点之后同理。
  try {
    return range.compareBoundaryPoints(Range.END_TO_START, probe) < 0
      && range.compareBoundaryPoints(Range.START_TO_END, probe) > 0;
  } catch {
    return false;
  }
}

/** 返回选区覆盖的原文偏移；空选区、不含偏移标注、或完全在容器外时返回 null。 */
export function selectionToRange(container: HTMLElement, selection: Selection | null): Highlight | null {
  if (selection === null || selection.rangeCount === 0 || selection.isCollapsed) return null;
  const range = selection.getRangeAt(0);
  if (!container.contains(range.commonAncestorContainer)) return null;
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  let start = -1;
  let end = -1;
  for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) {
    const text = node as Text;
    const holder = text.parentElement as HTMLElement | null;
    const base = holder?.getAttribute('data-md-start');
    const edge = holder?.getAttribute('data-md-end');
    if (base === null || edge === null) continue;
    if (!intersecting(range, text)) continue;
    const origin = Number(base);
    const length = text.textContent?.length ?? 0;
    const localStart = text === range.startContainer ? range.startOffset : 0;
    const localEnd = text === range.endContainer ? range.endOffset : length;
    if (localEnd <= localStart) continue;
    const absStart = origin + localStart;
    const absEnd = origin + localEnd;
    start = start === -1 ? absStart : Math.min(start, absStart);
    end = end === -1 ? absEnd : Math.max(end, absEnd);
  }
  if (start === -1 || end <= start) return null;
  return { start, end };
}

/** 选区起点所在的块级元素序号（契约 §6.10 的 proseIndex：`.md` 直接子块的索引）。 */
export function proseIndexForRange(container: HTMLElement, selection: Selection | null): number {
  if (selection === null || selection.rangeCount === 0) return -1;
  const range = selection.getRangeAt(0);
  let element = range.startContainer instanceof Element
    ? range.startContainer
    : range.startContainer.parentElement;
  const blocks = Array.from(container.children);
  while (element !== null && element !== container) {
    const index = blocks.indexOf(element);
    if (index >= 0) return index;
    element = element.parentElement;
  }
  return -1;
}
