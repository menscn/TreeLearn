import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { BranchPanel } from "../components/BranchPanel";
import { ChatAnswer } from "../components/ChatAnswer";
import { Composer } from "../components/Composer";
import { Icon } from "../components/Icon";
import { IconButton } from "../components/IconButton";
import { Notice } from "../components/Notice";
import { TreeOverlay } from "../components/TreeOverlay";
import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { useCopy } from "../hooks/useCopy";
import { useWorkspace } from "../hooks/useWorkspace";
import {
  draftFor,
  rememberScroll,
  scrollFor,
  setDraft,
  setTreeOpen
} from "../store/workspace";

type CopyFeedback = "copied" | "failed" | null;

async function writeClipboard(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText !== undefined) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // 剪贴板权限被拒时仍可退回选区复制，下面再试一次。
  }
  try {
    const area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.top = "-1000px";
    document.body.append(area);
    area.select();
    const copied = typeof document.execCommand === "function" && document.execCommand("copy");
    area.remove();
    return copied;
  } catch {
    return false;
  }
}

/** 对话工作区：正文独立滚动，输入区固定在底部；未接通的动作只说现状。 */
export function ChatPage() {
  const t = useCopy();
  const state = useWorkspace();
  const data = useWorkspaceData();
  const topic = data.topics[state.topic];
  const chat = data.chat !== null && data.chat.topicIndex === state.topic ? data.chat : null;
  const branch = data.branches.find((item) => item.topicIndex === state.topic) ?? null;
  const tree = data.tree !== null && data.tree.topicIndex === state.topic ? data.tree : null;
  const topicKey = `chat:${state.topic}`;

  const [draft, setDraftState] = useState(() => draftFor(topicKey));
  const [feedback, setFeedback] = useState<CopyFeedback>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const feedbackTimer = useRef<number | undefined>(undefined);

  useEffect(() => {
    setDraftState(draftFor(topicKey));
  }, [topicKey]);

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (element === null) return;
    element.scrollTop = scrollFor(topicKey);
    return () => rememberScroll(topicKey, element.scrollTop);
  }, [topicKey]);

  useEffect(() => () => {
    if (feedbackTimer.current !== undefined) window.clearTimeout(feedbackTimer.current);
  }, []);

  const updateDraft = (value: string): void => {
    setDraftState(value);
    setDraft(topicKey, value);
  };

  const report = (next: Exclude<CopyFeedback, null>): void => {
    setFeedback(next);
    if (feedbackTimer.current !== undefined) window.clearTimeout(feedbackTimer.current);
    feedbackTimer.current = window.setTimeout(() => setFeedback(null), 3000);
  };

  const copyAnswer = async (): Promise<void> => {
    if (chat === null) return;
    report(await writeClipboard(chat.answer) ? "copied" : "failed");
  };

  const closeTree = useCallback((): void => {
    setTreeOpen(false);
    window.setTimeout(() => document.getElementById("tree-toggle")?.focus(), 0);
  }, []);

  if (topic === undefined) {
    return (
      <section className="chat-screen" data-screen="chat">
        <Notice copyKey="state_fixture_unavailable" />
      </section>
    );
  }

  return (
    <section className="chat-screen" data-screen="chat">
      <div className="chat-shell">
        <div className="chat-main">
          <div
            ref={scrollRef}
            className="chat-scroll"
            data-slot="chat-scroll"
            onScroll={(event) => rememberScroll(topicKey, event.currentTarget.scrollTop)}
          >
            <article className="chat-reading">
              <h1 className="chat-title" data-node="chat_title">{topic.title}</h1>
              <p className="chat-meta">{chat?.time ?? topic.meta}</p>

              {chat === null ? (
                <section className="chat-baseline" data-chat-baseline>
                  <h2>{t("chat_no_chat_baseline")}</h2>
                  <p>{topic.summary}</p>
                  <p className="chat-muted">{t("chat_no_chat_body")}</p>
                </section>
              ) : (
                <>
                  <p className="chat-assistant">
                    <Icon className="chat-assistant-mark" name="brand_sprout" />
                    <span>{t("app_name")}</span>
                    <small>{chat.model}</small>
                  </p>
                  <ChatAnswer blocks={chat.blocks} />
                </>
              )}

              <div className="chat-answer-actions" data-node="chat_actions">
                <IconButton
                  action="chat-copy"
                  glyph="action_copy"
                  label={t("chat_copy")}
                  disabled={chat === null}
                  onClick={() => {
                    void copyAnswer();
                  }}
                />
                <IconButton
                  action="chat-reset"
                  glyph="nav_restore"
                  label={`${t("chat_action_reset")} · ${t("chat_action_unavailable")}`}
                  disabled
                />
                <IconButton
                  action="chat-fork"
                  glyph="action_fork"
                  label={`${t("chat_action_fork")} · ${t("chat_action_unavailable")}`}
                  disabled
                />
              </div>
              <p className="chat-feedback" data-copy-status={feedback ?? undefined} role="status">
                {feedback === "copied" ? t("chat_copied") : feedback === "failed" ? t("chat_copy_failed") : ""}
              </p>

              {chat !== null && chat.suggestions.length > 0 ? (
                <section className="chat-followups" aria-label={t("chat_suggestions")}>
                  <p>{t("chat_suggestions")}</p>
                  {chat.suggestions.map((text, index) => (
                    <button
                      className="followup"
                      data-suggestion={index}
                      key={`suggestion-${index}`}
                      type="button"
                      onClick={() => {
                        updateDraft(text);
                        inputRef.current?.focus();
                      }}
                    >
                      <Icon name="nav_chat" />
                      <span>{text}</span>
                    </button>
                  ))}
                </section>
              ) : null}
            </article>
          </div>
          <Composer
            value={draft}
            placeholder={chat?.placeholder ?? t("chat_composer_placeholder")}
            model={chat?.model ?? ""}
            inputRef={inputRef}
            onChange={updateDraft}
          />
        </div>
        <BranchPanel branch={branch} open={state.panelOpen} />
      </div>
      {tree !== null && state.treeOpen ? <TreeOverlay tree={tree} onClose={closeTree} /> : null}
    </section>
  );
}
