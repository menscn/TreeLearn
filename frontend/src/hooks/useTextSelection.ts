import { useCallback, useEffect, useState, type RefObject } from "react";
import type { SelectionSnapshot } from "../utils/selection";

export interface TextSelection {
  selection: SelectionSnapshot | null;
  clear: () => void;
  selectAll: (root: HTMLElement | null) => void;
}

/** 只认落在阅读正文里的非折叠选区；侧栏、输入框和空选都不算。 */
function readSelection(root: HTMLElement | null): SelectionSnapshot | null {
  if (root === null) return null;
  const sel = window.getSelection();
  if (sel === null || sel.isCollapsed || sel.rangeCount === 0) return null;
  const range = sel.getRangeAt(0);
  if (!root.contains(range.commonAncestorContainer)) return null;
  const text = sel.toString();
  if (text.trim() === "") return null;
  const rect = range.getBoundingClientRect();
  if (rect.width === 0 && rect.height === 0) return null;
  return {
    text,
    rect: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom }
  };
}

/**
 * 选区生命周期只活在当前页面实例：不写 store、不持久化。
 * 滚动、缩放与 Esc 收起浮条；全选通过回答根节点重建范围，避免浮条被 select-all 的 selectionchange 闪掉。
 */
export function useTextSelection(rootRef: RefObject<HTMLElement | null>): TextSelection {
  const [selection, setSelection] = useState<SelectionSnapshot | null>(null);

  const clear = useCallback((): void => {
    window.getSelection()?.removeAllRanges();
    setSelection(null);
  }, []);

  const selectAll = useCallback((root: HTMLElement | null): void => {
    if (root === null) return;
    const sel = window.getSelection();
    if (sel === null) return;
    const range = document.createRange();
    range.selectNodeContents(root);
    sel.removeAllRanges();
    sel.addRange(range);
    setSelection(readSelection(rootRef.current));
  }, [rootRef]);

  useEffect(() => {
    const onSelectionChange = (): void => setSelection(readSelection(rootRef.current));
    const dismiss = (): void => setSelection(null);
    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") clear();
    };
    document.addEventListener("selectionchange", onSelectionChange);
    document.addEventListener("keydown", onKeyDown);
    window.addEventListener("scroll", dismiss, true);
    window.addEventListener("resize", dismiss);
    return () => {
      document.removeEventListener("selectionchange", onSelectionChange);
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("scroll", dismiss, true);
      window.removeEventListener("resize", dismiss);
    };
  }, [clear, rootRef]);

  return { selection, clear, selectAll };
}
