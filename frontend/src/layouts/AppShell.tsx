import { useEffect, type ReactNode } from "react";
import { Icon, type GlyphName } from "../components/Icon";
import { IconButton } from "../components/IconButton";
import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { useCopy } from "../hooks/useCopy";
import { useWorkspace } from "../hooks/useWorkspace";
import {
  back,
  canGoBackWorkspace,
  openTopic,
  openView,
  setNavCollapsed,
  setPanelOpen,
  setTreeOpen,
  toggleNav,
  type WorkspaceView
} from "../store/workspace";

const NAV_ITEMS: readonly { view: WorkspaceView; label: string; icon: GlyphName }[] = [
  { view: "home", label: "tab_home", icon: "tab_home" },
  { view: "library", label: "tab_library", icon: "tab_library" },
  { view: "settings", label: "nav_settings", icon: "chat_gear" }
];

/**
 * 轻量学习工作区外壳：左侧可折叠导航 + 稳定顶部栏 + 独立滚动的内容槽。
 * 分支面板/知识树属于对话页，由页面自己挂载，外壳不预占宽度。
 */
export function AppShell({ children = null }: { children?: ReactNode }) {
  const t = useCopy();
  const state = useWorkspace();
  const data = useWorkspaceData();
  const navCollapsed = state.navCollapsed;
  const currentTopic = state.view === "chat" ? data.topics[state.topic] : undefined;
  const treeVisible = data.tree !== null && data.tree.topicIndex === state.topic;
  const crumb = state.view === "home"
    ? t("tab_home")
    : state.view === "library"
      ? t("tab_library")
      : state.view === "settings"
        ? t("nav_settings")
        : state.view === "persona-intro"
          ? t("persona_intro_title")
          : state.view === "forest"
            ? t("forest_title")
            : currentTopic?.title ?? t("app_name");

  useEffect(() => {
    document.title = t("app_name");
  }, [t]);

  useEffect(() => {
    const collapseIfNarrow = () => {
      if (window.innerWidth <= 760) setNavCollapsed(true);
    };
    collapseIfNarrow();
    window.addEventListener("resize", collapseIfNarrow);
    return () => window.removeEventListener("resize", collapseIfNarrow);
  }, [state.view, state.topic]);

  useEffect(() => {
    const closeNav = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      if (state.panelOpen || state.treeOpen) return;
      if (window.innerWidth <= 760) setNavCollapsed(true);
    };
    document.addEventListener("keydown", closeNav);
    return () => document.removeEventListener("keydown", closeNav);
  }, [state.panelOpen, state.treeOpen]);

  return (
    <div className="app-workspace" data-layer="layouts" data-nav={navCollapsed ? "collapsed" : "expanded"}>
      {!navCollapsed ? (
        <button
          className="nav-scrim"
          data-action="close-nav"
          type="button"
          aria-label={t("nav_collapse")}
          title={t("nav_collapse")}
          onClick={() => setNavCollapsed(true)}
        />
      ) : null}
      <aside className="app-sidebar" aria-label={t("nav_primary")}>
        <button className="brand" type="button" onClick={() => openView("home")} aria-label={t("app_name")}>
          <Icon name="brand_sprout" className="brand-mark" />
          <span className="brand-word">{t("app_name")}</span>
        </button>
        <nav className="side-nav">
          {NAV_ITEMS.map((item) => {
            const active = state.view === item.view;
            return (
              <button
                key={item.view}
                className={`side-link${active ? " active" : ""}`}
                data-nav-view={item.view}
                type="button"
                aria-label={t(item.label)}
                title={t(item.label)}
                aria-current={active ? "page" : undefined}
                onClick={() => openView(item.view)}
              >
                <Icon name={item.icon} />
                <span className="side-label">{t(item.label)}</span>
              </button>
            );
          })}
        </nav>
        <button
          className={`forest-entry${state.view === "forest" ? " active" : ""}`}
          data-forest-entry
          type="button"
          aria-label={t("cd_drawer_forest")}
          title={t("cd_drawer_forest")}
          aria-current={state.view === "forest" ? "page" : undefined}
          onClick={() => openView("forest")}
        >
          <span className="forest-entry-icon" aria-hidden="true">
            <Icon name="nav_forest" />
          </span>
          <span className="forest-entry-main">
            <span className="forest-entry-title" data-forest-entry-title>{t("drawer_forest_title")}</span>
            <span className="forest-entry-sub">{t("drawer_forest_sub")}</span>
          </span>
          <span className="forest-entry-arrow" aria-hidden="true">
            <Icon name="chevron_right" />
          </span>
        </button>
        <p className="side-heading">{t("nav_recent")}</p>
        <div className="recent">
          {data.topics.map((topic, index) => {
            const active = state.view === "chat" && state.topic === index;
            return (
              <button
                key={topic.id}
                className={`recent-link${active ? " active" : ""}`}
                data-recent-topic={index}
                type="button"
                aria-current={active ? "page" : undefined}
                onClick={() => openTopic(index, data.topics.length)}
              >
                <span className="recent-title">{topic.title}</span>
                <small>{topic.meta}</small>
              </button>
            );
          })}
        </div>
      </aside>
      <div className="workspace-column">
        <header className="workspace-topbar">
          <button
            className="icon-btn"
            data-action="toggle-nav"
            type="button"
            aria-expanded={!navCollapsed}
            aria-label={navCollapsed ? t("nav_expand") : t("nav_collapse")}
            title={navCollapsed ? t("nav_expand") : t("nav_collapse")}
            onClick={() => toggleNav()}
          >
            <Icon name="sidebar" />
          </button>
          {canGoBackWorkspace() ? (
            <button
              className="icon-btn"
              data-action="workspace-back"
              type="button"
              aria-label={t("state_back")}
              title={t("state_back")}
              onClick={() => back()}
            >
              <Icon name="nav_back" />
            </button>
          ) : null}
          <div className="workspace-crumb" data-slot="breadcrumb">
            <span className="crumb-current">{crumb}</span>
          </div>
          {state.view === "chat" ? (
            <div className="workspace-tools">
              <IconButton
                action="toggle-branch-panel"
                controls="branch-panel"
                expanded={state.panelOpen}
                glyph="nav_tree"
                id="branch-toggle"
                label={state.panelOpen ? t("chat_branch_close") : t("chat_branch_open")}
                onClick={() => setPanelOpen(!state.panelOpen)}
              />
              {treeVisible ? (
                <IconButton
                  action="toggle-tree"
                  expanded={state.treeOpen}
                  glyph="chat_overview"
                  id="tree-toggle"
                  label={state.treeOpen ? t("chat_tree_close") : t("chat_tree_open")}
                  onClick={() => setTreeOpen(!state.treeOpen)}
                />
              ) : null}
            </div>
          ) : null}
        </header>
        <main className="workspace-main" data-slot="screen">
          {children}
        </main>
      </div>
    </div>
  );
}
