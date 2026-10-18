import { useEffect, type ReactNode } from "react";
import { useCopy } from "../hooks/useCopy";
import { useRoute } from "../hooks/useRoute";

/**
 * 换屏骨架：Android 侧是单 Activity 内换屏（swap()），这里对应为「一个外壳 + 一个内容槽 + 底部两枚标签」。
 * 标签的形状照 Android 现状定为可点的 tab_home / tab_library 两枚，切换即重置栈（回到那一 tab 的根屏）。
 */
export function AppShell({ children = null }: { children?: ReactNode }) {
  const t = useCopy();
  const route = useRoute();
  useEffect(() => {
    document.title = t("app_name");
  }, [t]);
  return (
    <div className="app-shell" data-layer="layouts">
      <main className="app-main" data-slot="screen">
        {children}
      </main>
      <nav className="app-tabs" data-slot="tabs" data-node="tab_bar">
        <button className="app-tab" data-tab="home" type="button" onClick={() => route.openTab("home")}>
          {t("tab_home")}
        </button>
        <button className="app-tab" data-tab="library" type="button" onClick={() => route.openTab("library")}>
          {t("tab_library")}
        </button>
      </nav>
    </div>
  );
}
