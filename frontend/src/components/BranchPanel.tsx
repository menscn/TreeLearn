import { useEffect, useRef } from "react";
import { useCopy } from "../hooks/useCopy";
import { setPanelOpen } from "../store/workspace";
import type { BranchModel } from "../store/workspaceData";
import { Icon } from "./Icon";
import { IconButton } from "./IconButton";

interface BranchPanelProps {
  branch: BranchModel | null;
  open: boolean;
}

/**
 * 分支导航：宽窗并排、窄窗覆盖由 CSS 决定；这里只负责内容与焦点。
 * 关闭后焦点回到顶部触发按钮，避免键盘用户丢位置。
 */
export function BranchPanel({ branch, open }: BranchPanelProps) {
  const t = useCopy();
  const closeRef = useRef<HTMLButtonElement>(null);
  const wasOpen = useRef(false);

  useEffect(() => {
    if (open) {
      wasOpen.current = true;
      closeRef.current?.focus();
    } else if (wasOpen.current) {
      wasOpen.current = false;
      document.getElementById("branch-toggle")?.focus();
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key !== "Escape") return;
      event.preventDefault();
      setPanelOpen(false);
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open]);

  return (
    <>
      {open ? (
        <button
          className="panel-scrim"
          data-action="close-branch-panel"
          type="button"
          aria-label={t("chat_branch_close")}
          onClick={() => setPanelOpen(false)}
        />
      ) : null}
      <aside id="branch-panel" className="branch-panel" aria-label={t("chat_branch_title")} hidden={!open}>
        <header className="panel-header">
          <h2>{t("chat_branch_title")}</h2>
          <IconButton
            action="close-branch-panel"
            buttonRef={closeRef}
            glyph="chat_close"
            id="branch-close"
            label={t("chat_branch_close")}
            onClick={() => setPanelOpen(false)}
          />
        </header>
        <div className="panel-content">
          {branch === null ? (
            <p className="panel-muted">{t("chat_branch_unavailable")}</p>
          ) : (
            <>
              <p className="section-label">{t("chat_branch_root")}</p>
              <div className="branch-item active" data-branch="root">
                <Icon name="nav_tree" className="branch-icon" />
                <div className="branch-copy">
                  <p className="branch-title">{branch.title}</p>
                  <small>{branch.rootChip === "" ? t("chat_branch_current") : branch.rootChip}</small>
                  <small>{branch.rootMeta === "" ? t("chat_branch_unavailable") : branch.rootMeta}</small>
                </div>
              </div>
              {branch.captured && branch.knownChildren === 0 ? (
                <p className="panel-muted">{t("chat_branch_children_none")}</p>
              ) : null}
              {branch.captured && branch.knownChildren !== null && branch.knownChildren > 0 ? (
                <p className="panel-muted">
                  {t("chat_branch_children_known")} {branch.knownChildren}
                </p>
              ) : null}
              {!branch.captured ? (
                <p className="panel-muted">
                  {t("chat_branch_children_known")} {branch.knownChildren ?? "—"}
                </p>
              ) : null}
              <section className="panel-summary">
                <h3>{branch.captured ? t("chat_summary") : t("chat_topic_summary")}</h3>
                <p>{branch.summary === "" ? t("chat_branch_unavailable") : branch.summary}</p>
              </section>
            </>
          )}
        </div>
        <footer className="panel-footer">
          <button
            className="panel-action"
            data-action="chat-create-branch"
            type="button"
            aria-label={`${t("chat_branch_create")} · ${t("chat_action_unavailable")}`}
            title={`${t("chat_branch_create")} · ${t("chat_action_unavailable")}`}
            disabled
          >
            <Icon name="action_fork" />
            <span>{t("chat_branch_create")}</span>
            <small>{t("chat_action_unavailable")}</small>
          </button>
        </footer>
      </aside>
    </>
  );
}
