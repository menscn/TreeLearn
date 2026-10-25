/**
 * 选中文本浮条的几何与坐标：只依赖数字，不依赖 React 或 DOM 节点，宿主与组件共用。
 *
 * 尺寸来自 Android `popup_word_action.xml`：六格 44dp、五条 1×20dp 分隔、四周 4dp 内边距，
 * 于是 6×44 + 5×1 + 2×4 = 277、高 44 + 2×4 = 52。样式只画这些格子，不再写第二份宽高。
 */
export const TOOLBAR_SIZE = { width: 277, height: 52 } as const;

const GAP = 8;
const MARGIN = 8;

export interface SelectionRect {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

export interface SelectionSnapshot {
  text: string;
  rect: SelectionRect;
}

export interface MenuPlacement {
  top: number;
  left: number;
  placement: "above" | "below";
}

/** 选区上方优先；放不下翻到下方；左右夹在视口 8px 边距内。 */
export function menuPosition(
  rect: SelectionRect,
  size: { width: number; height: number },
  viewport: { width: number; height: number },
  gap = GAP,
  margin = MARGIN
): MenuPlacement {
  const centered = (rect.left + rect.right) / 2 - size.width / 2;
  const left = Math.min(
    Math.max(centered, margin),
    Math.max(margin, viewport.width - margin - size.width)
  );
  const above = rect.top - gap - size.height;
  if (above >= margin) return { top: above, left, placement: "above" };
  const below = rect.bottom + gap;
  return {
    top: Math.min(below, Math.max(margin, viewport.height - margin - size.height)),
    left,
    placement: "below"
  };
}
