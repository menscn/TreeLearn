import { useEffect, type ReactNode } from "react";
import { useCopy } from "../hooks/useCopy";

/**
 * 换屏骨架：Android 侧是单 Activity 内换屏，这里对应为「一个外壳 + 一个内容槽」。
 * 路由与返回栈在阶段六接线，本文件只提供槽位与两枚标签的结构位置；
 * 标签最终是按钮还是静态文本，由阶段三对照 Android 现状决定，这里不抢先固定形状。
 */
export function AppShell({ children = null }: { children?: ReactNode }) {
  const t = useCopy();
  useEffect(() => {
    document.title = t("app_name");
  }, [t]);
  return (
    <div className="app-shell" data-layer="layouts">
      <main className="app-main" data-slot="screen">
        {children}
      </main>
      <nav className="app-tabs" data-slot="tabs">
        <span className="app-tab" data-tab="home">{t("tab_home")}</span>
        <span className="app-tab" data-tab="library">{t("tab_library")}</span>
      </nav>
    </div>
  );
}
