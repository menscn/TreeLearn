import { useLayoutEffect, type RefObject } from "react";

/**
 * 把输入坞的实测高度写成定位父级上的 `--dock-h`。
 *
 * 坞悬浮后不再撑高布局，末行留白与底缘羽化只能共用这一个数：写死就会在引用条出现时
 * 把正文末行压进输入框底下。用 layout effect 是为了在首次绘制前就有值，避免留白闪一下。
 */
export function useDockHeight(
  mainRef: RefObject<HTMLElement | null>,
  dockRef: RefObject<HTMLElement | null>
): void {
  useLayoutEffect(() => {
    const main = mainRef.current;
    const dock = dockRef.current;
    if (main === null || dock === null) return;
    const write = (): void => {
      const height = dock.offsetHeight;
      if (height > 0) main.style.setProperty("--dock-h", `${height}px`);
    };
    write();
    if (typeof ResizeObserver !== "function") return;
    const observer = new ResizeObserver(write);
    observer.observe(dock);
    return () => observer.disconnect();
  }, [dockRef, mainRef]);
}
