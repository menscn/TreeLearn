import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { BranchPanel } from "../components/BranchPanel";
import { ChatAnswer } from "../components/ChatAnswer";
import { Composer } from "../components/Composer";
import { Icon } from "../components/Icon";
import { IconButton } from "../components/IconButton";
import { Notice } from "../components/Notice";
import { PersonaPicker } from "../components/PersonaPicker";
import { SelectionToolbar } from "../components/SelectionToolbar";
import { TreeOverlay } from "../components/TreeOverlay";
import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { copyFormat } from "../copy";
import { useCopy } from "../hooks/useCopy";
import { useDraftReplacement } from "../hooks/useDraftReplacement";
import { useDockHeight } from "../hooks/useDockHeight";
import { useTextSelection } from "../hooks/useTextSelection";
import { useWorkspace } from "../hooks/useWorkspace";
import { personaNameKey, type PersonaId } from "../store/persona";
import {
  draftFor,
  openView,
  rememberScroll,
  scrollFor,
  setDraft,
  setTreeOpen
} from "../store/workspace";

type CopyFeedback = "copied" | "selection" | "failed" | null;

const ASK_CHIPS = [
  { id: "why", label: "chat_ask_chip_why", query: "chat_ask_query_why", a11y: "cd_chat_ask_why" },
  { id: "what", label: "chat_ask_chip_what", query: "chat_ask_query_what", a11y: "cd_chat_ask_what" },
  { id: "how", label: "chat_ask_chip_how", query: "chat_ask_query_how", a11y: "cd_chat_ask_how" }
] as const;

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
  const canReplaceDraft = useDraftReplacement();
  const state = useWorkspace();
  const data = useWorkspaceData();
  const topic = data.topics[state.topic];
  const chat = data.chat !== null && data.chat.topicIndex === state.topic ? data.chat : null;
  const branch = data.branches.find((item) => item.topicIndex === state.topic) ?? null;
  const tree = data.tree !== null && data.tree.topicIndex === state.topic ? data.tree : null;
  const topicKey = `chat:${state.topic}`;

  const [draft, setDraftState] = useState(() => draftFor(topicKey));
  const [feedback, setFeedback] = useState<CopyFeedback>(null);
  const [personaNote, setPersonaNote] = useState<string | null>(null);
  const [quote, setQuote] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const mainRef = useRef<HTMLDivElement>(null);
  const dockRef = useRef<HTMLDivElement>(null);
  const readingRef = useRef<HTMLElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const feedbackTimer = useRef<number | undefined>(undefined);
  const personaTimer = useRef<number | undefined>(undefined);
  const { selection, clear: clearSelection, selectAll } = useTextSelection(readingRef);

  useEffect(() => {
    setDraftState(draftFor(topicKey));
    setPersonaNote(null);
    setQuote(null);
  }, [topicKey]);

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (element === null) return;
    element.scrollTop = scrollFor(topicKey);
    return () => rememberScroll(topicKey, element.scrollTop);
  }, [topicKey]);

  useEffect(() => () => {
    if (feedbackTimer.current !== undefined) window.clearTimeout(feedbackTimer.current);
    if (personaTimer.current !== undefined) window.clearTimeout(personaTimer.current);
  }, []);

  useDockHeight(mainRef, dockRef);

  const updateDraft = (value: string): void => {
    setDraftState(value);
    setDraft(topicKey, value);
  };

  const report = (next: Exclude<CopyFeedback, null>): void => {
    setFeedback(next);
    if (feedbackTimer.current !== undefined) window.clearTimeout(feedbackTimer.current);
    feedbackTimer.current = window.setTimeout(() => setFeedback(null), 3000);
  };

  const announcePersona = (persona: PersonaId): void => {
    setPersonaNote(copyFormat("persona_switched", t(personaNameKey(persona))));
    if (personaTimer.current !== undefined) window.clearTimeout(personaTimer.current);
    personaTimer.current = window.setTimeout(() => setPersonaNote(null), 3000);
  };

  const copyAnswer = async (): Promise<void> => {
    if (chat === null) return;
    report(await writeClipboard(chat.answer) ? "copied" : "failed");
  };

  const copySelection = async (text: string): Promise<void> => {
    report(await writeClipboard(text) ? "selection" : "failed");
    clearSelection();
  };

  const askSelection = (text: string): void => {
    setQuote(text);
    clearSelection();
    inputRef.current?.focus();
  };

  const selectWholeAnswer = (): void => {
    selectAll(readingRef.current?.querySelector<HTMLElement>(".chat-answer") ?? null);
  };

  const feedbackText = feedback === "copied"
    ? t("chat_copied")
    : feedback === "selection"
      ? t("chat_selection_copied")
      : feedback === "failed"
        ? t("chat_copy_failed")
        : "";

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
        <div ref={mainRef} className="chat-main">
          <div
            ref={scrollRef}
            className="chat-scroll"
            data-slot="chat-scroll"
            onScroll={(event) => rememberScroll(topicKey, event.currentTarget.scrollTop)}
          >
            <article ref={readingRef} className="chat-reading">
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
                {feedbackText}
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
                        if (!canReplaceDraft(topicKey)) return;
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
            dockRef={dockRef}
            onChange={updateDraft}
            personaSlot={
              <PersonaPicker
                topic={state.topic}
                onIntro={() => openView("persona-intro")}
                onSwitched={announcePersona}
              />
            }
            statusSlot={
              personaNote === null ? null : (
                <p className="persona-feedback" data-persona-feedback role="status">{personaNote}</p>
              )
            }
            quoteSlot={quote === null ? null : (
              <>
                <div className="chat-quote" data-quote>
                  <p className="chat-quote-text">{copyFormat("chat_quoted", quote)}</p>
                  <IconButton
                    action="chat-quote-clear"
                    className="chat-quote-clear"
                    glyph="chat_close"
                    label={t("chat_quote_clear")}
                    onClick={() => setQuote(null)}
                  />
                </div>
                <div className="chat-ask-chips" role="group" aria-label={t("chat_quote_chips")}>
                  {ASK_CHIPS.map((chip) => (
                    <button
                      aria-label={t(chip.a11y)}
                      className="chat-ask-chip"
                      data-ask-chip={chip.id}
                      key={chip.id}
                      type="button"
                      onClick={() => {
                        if (!canReplaceDraft(topicKey)) return;
                        updateDraft(t(chip.query));
                        inputRef.current?.focus();
                      }}
                    >
                      {t(chip.label)}
                    </button>
                  ))}
                </div>
              </>
            )}
          />
        </div>
        <BranchPanel branch={branch} open={state.panelOpen} />
      </div>
      <SelectionToolbar
        selection={selection}
        onAskAi={askSelection}
        onCopy={(text) => {
          void copySelection(text);
        }}
        onSelectAll={selectWholeAnswer}
      />
      {tree !== null && state.treeOpen ? <TreeOverlay tree={tree} onClose={closeTree} /> : null}
    </section>
  );
}
