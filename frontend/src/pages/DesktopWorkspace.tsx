import { useEffect, useRef, useState, type FormEvent } from "react";
import { DesktopSettings } from "../components/DesktopSettings";
import { DesktopMarkdown } from "../components/DesktopMarkdown";
import { DesktopBranches, desktopBranchLabels } from "../components/DesktopBranches";
import { DesktopAttachment } from "../components/DesktopAttachment";
import { Icon } from "../components/Icon";
import { copyFormat } from "../copy";
import { useCopy } from "../hooks/useCopy";
import type { DesktopApi, DesktopMessage, DesktopState, DesktopTopic } from "../platform/desktop";
import { PERSONA_ORDER, personaFromWireId, personaNameKey, type PersonaId } from "../store/persona";
import { DraftWriteQueue } from "../store/draftWriteQueue";

type View = "home" | "history" | "settings" | "chat";

function errorKey(error: unknown): string {
  const message = error instanceof Error ? error.message : "";
  const codes: readonly [string, string][] = [
    ["KEY_MISSING", "desktop_error_key"], ["KEY_UNAVAILABLE", "desktop_error_key_unavailable"],
    ["ENCRYPTION_UNAVAILABLE", "desktop_error_encryption"], ["INVALID_SETTINGS", "desktop_error_settings"],
    ["AUTH_FAILED", "desktop_error_auth"], ["BALANCE_EXHAUSTED", "desktop_error_balance"],
    ["RATE_LIMITED", "desktop_error_rate"], ["REQUEST_TIMEOUT", "desktop_error_timeout"],
    ["NETWORK_ERROR", "desktop_error_network"], ["STORAGE_ERROR", "desktop_error_storage"],
    ["BUSY", "desktop_error_busy"], ["STALE_RETRY", "desktop_error_stale_retry"],
    ["INCOMPLETE_RESPONSE", "desktop_error_incomplete"], ["CONTEXT_TOO_LARGE", "desktop_branch_context_large"],
    ["CONTEXT_CHANGED", "desktop_branch_context_changed"], ["ATTACHMENT_INVALID", "desktop_attachment_invalid"],
    ["ATTACHMENT_TOO_LARGE", "desktop_attachment_large"], ["ATTACHMENT_ENCODING", "desktop_attachment_encoding"],
    ["ATTACHMENT_CHANGED", "desktop_attachment_changed"], ["ATTACHMENT_READ_FAILED", "desktop_attachment_read"],
    ["ATTACHMENT_PENDING", "desktop_attachment_pending"]
  ];
  return codes.find(([code]) => message.includes(code))?.[1] ?? "desktop_error_generic";
}

export function DesktopWorkspace({ api }: { api: DesktopApi }) {
  const t = useCopy();
  const [state, setState] = useState<DesktopState | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [view, setView] = useState<View>("home");
  const [topicId, setTopicId] = useState<string | null>(null);
  const [branchId, setBranchId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [notice, setNotice] = useState("");
  const [copied, setCopied] = useState<{ id: string; ok: boolean } | null>(null);
  const [navOpen, setNavOpen] = useState(false);
  const versions = useRef<Record<string, number>>({});
  const dirtyDrafts = useRef(new Set<string>());
  const failedDrafts = useRef(new Set<string>());
  const [failedDraftCount, setFailedDraftCount] = useState(0);
  const [draftQueue] = useState(() => new DraftWriteQueue(
    ({ scope, owner, text }) => api.saveDraft(scope === "home" || scope === owner
      ? { topicId: scope === "home" ? null : owner, text }
      : { topicId: owner, branchId: scope, text }),
    ({ scope, revision }, succeeded) => {
      if (versions.current[scope] !== revision) return;
      if (succeeded) { dirtyDrafts.current.delete(scope); failedDrafts.current.delete(scope); }
      else failedDrafts.current.add(scope);
      setFailedDraftCount(failedDrafts.current.size);
    }
  ));
  const activeRequest = useRef<{ requestId: string; text: string; topicId: string | null; branchId: string | null } | null>(null);
  const [progress, setProgress] = useState<{ requestId: string; topicId: string; branchId?: string; content: string } | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const personaSave = useRef<Promise<void> | null>(null);
  const attachmentPending = useRef<Promise<unknown> | null>(null);
  const [attachmentBusy, setAttachmentBusy] = useState(false);
  const branchPending = useRef<{ key: string; requestId: string; promise: Promise<{ branchId: string }> | null } | null>(null);
  const [closing, setClosing] = useState(false);
  const closePending = useRef(false);
  const requestInProgress = useRef(false);
  const composing = useRef(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const chatTitleRef = useRef<HTMLHeadingElement>(null);
  const followAnswer = useRef(true);
  const messageElements = useRef(new Map<string, HTMLElement>());
  const focusIntent = useRef<{ topicId: string; branchId: string; messageId: string | null } | null>(null);
  const branchScroll = useRef(new Map<string, number>());
  const displayedBranch = useRef<string | null>(null);

  async function refresh(): Promise<DesktopState> {
    await draftQueue.drain();
    const observedVersions = { ...versions.current };
    const next = await api.getState();
    setState(next);
    setDrafts((current) => {
      const merged = { ...next.drafts };
      for (const key of dirtyDrafts.current) merged[key] = current[key] ?? "";
      for (const key of Object.keys(versions.current)) {
        if (versions.current[key] !== observedVersions[key]) merged[key] = current[key] ?? "";
      }
      return merged;
    });
    return next;
  }

  useEffect(() => {
    let active = true;
    api.getState().then((next) => {
      if (active) { setState(next); setDrafts(next.drafts); }
    }).catch((failure: unknown) => { if (active) setError(t(errorKey(failure))); });
    return () => { active = false; };
  }, [api, t]);

  useEffect(() => api.onProgress((event) => {
    if (event.requestId !== activeRequest.current?.requestId) return;
    setProgress(event);
    setTopicId(event.topicId);
    setBranchId(event.branchId ?? event.topicId);
    setView("chat");
  }), [api]);

  useEffect(() => {
    const offBefore = api.onBeforeClose(async () => {
      closePending.current = true;
      setClosing(true);
      try {
        await draftQueue.drain();
        if (personaSave.current) await personaSave.current;
        if (branchPending.current?.promise) await branchPending.current.promise;
        if (attachmentPending.current) await attachmentPending.current;
        if (dirtyDrafts.current.size || failedDrafts.current.size) {
          setError(t("desktop_close_failed"));
          closePending.current = false;
          setClosing(false);
          return false;
        }
        return true;
      } catch {
        setError(t("desktop_close_failed"));
        closePending.current = false;
        setClosing(false);
        return false;
      }
    });
    const offCancelled = api.onCloseCancelled(() => {
      closePending.current = false;
      setClosing(false);
      setError(t("desktop_close_failed"));
    });
    return () => { offBefore(); offCancelled(); };
  }, [api, t, draftQueue]);

  useEffect(() => {
    if (view !== "chat") { displayedBranch.current = null; return; }
    const switched = displayedBranch.current !== branchId;
    displayedBranch.current = branchId;
    const intent = focusIntent.current;
    if (intent && intent.topicId === topicId && intent.branchId === branchId) {
      const element = intent.messageId ? messageElements.current.get(intent.messageId) : undefined;
      if (element) {
        element.scrollIntoView({ block: "center" });
        element.focus({ preventScroll: true });
        if (branchId && scrollRef.current) branchScroll.current.set(branchId, scrollRef.current.scrollTop);
      } else {
        if (branchId && scrollRef.current) {
          if (intent.messageId || !followAnswer.current) scrollRef.current.scrollTop = branchScroll.current.get(branchId) ?? 0;
          else scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
        chatTitleRef.current?.focus({ preventScroll: true });
      }
      focusIntent.current = null;
      return;
    }
    if (intent) focusIntent.current = null;
    if (scrollRef.current) {
      if (followAnswer.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
      else if (switched && branchId) scrollRef.current.scrollTop = branchScroll.current.get(branchId) ?? 0;
    }
  }, [view, topicId, branchId, state?.topics, progress?.content]);

  function navigate(next: View, id: string | null = null, branch: string | null = id, messageId?: string | null) {
    if (closePending.current || attachmentPending.current) return;
    if (view === "chat" && branchId && scrollRef.current) branchScroll.current.set(branchId, scrollRef.current.scrollTop);
    followAnswer.current = next !== "chat" || !branch || !branchScroll.current.has(branch);
    focusIntent.current = next === "chat" && id && branch && messageId !== undefined
      ? { topicId: id, branchId: branch, messageId } : null;
    if (messageId) followAnswer.current = false;
    setView(next); setTopicId(id); setBranchId(branch); setError(""); setNotice(""); setNavOpen(false);
  }

  function queueDraft(key: string, value: string, owner: string | null) {
    const revision = (versions.current[key] ?? 0) + 1;
    versions.current[key] = revision;
    dirtyDrafts.current.add(key);
    setDrafts((current) => ({ ...current, [key]: value }));
    draftQueue.enqueue({ scope: key, owner, text: value, revision });
  }

  function updateDraft(value: string) {
    if (closePending.current) return;
    queueDraft(view === "chat" ? branchId ?? topicId ?? "home" : "home", value, view === "chat" ? topicId : null);
  }

  function retryDrafts() {
    if (closePending.current) return;
    for (const key of failedDrafts.current) {
      const owner = state?.topics.find((item) => item.id === key || item.branches?.some((branch) => branch.id === key))?.id ?? null;
      queueDraft(key, drafts[key] ?? "", owner);
    }
  }

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = drafts[view === "chat" ? branchId ?? topicId ?? "home" : "home"] ?? "";
    if (closePending.current || attachmentPending.current || personaSave.current || requestInProgress.current || text.trim() === "") return;
    if (!state?.settings.hasApiKey) { setError(t("desktop_error_key")); return; }
    requestInProgress.current = true;
    setBusy(true); setError("");
    const requestId = crypto.randomUUID();
    try {
      await draftQueue.drain();
      if (failedDrafts.current.has(view === "chat" ? branchId ?? topicId ?? "home" : "home")) return;
      activeRequest.current = { requestId, text, topicId: view === "chat" ? topicId : null, branchId: view === "chat" ? branchId : null };
      setActiveId(requestId);
      const scope = view === "chat" ? branchId ?? topicId ?? "home" : "home";
      const result = await api.sendMessage({ topicId: view === "chat" ? topicId : null, branchId: view === "chat" ? branchId : null, text, requestId, attachmentId: state?.attachmentDrafts?.[scope]?.id ?? null });
      await refresh();
      navigate("chat", result.topicId, result.branchId ?? (view === "chat" ? branchId : result.topicId));
    } catch (failure) {
      let reloadFailed = false;
      try {
        const next = await refresh();
        const created = next.topics.find((item) => item.messages.some((message) => message.requestId === requestId) || item.branches?.some((branch) => branch.messages.some((message) => message.requestId === requestId)));
        if (created) { setTopicId(created.id); setBranchId(created.branches?.find((branch) => branch.messages.some((message) => message.requestId === requestId))?.id ?? created.id); setView("chat"); }
      } catch { reloadFailed = true; }
      if (reloadFailed) setError(t("desktop_error_storage"));
      else if (!(failure instanceof Error && failure.message.includes("REQUEST_CANCELLED"))) setError(t(errorKey(failure)));
    } finally { requestInProgress.current = false; activeRequest.current = null; setActiveId(null); setProgress(null); setBusy(false); }
  }

  async function retry(message: DesktopMessage) {
    if (closePending.current || attachmentPending.current || personaSave.current || requestInProgress.current || !message.requestId) return;
    requestInProgress.current = true;
    setBusy(true); setError("");
    try {
      await draftQueue.drain();
      if (failedDrafts.current.has(branchId ?? topicId ?? "home")) return;
      activeRequest.current = { requestId: message.requestId, text: message.content, topicId, branchId };
      setActiveId(message.requestId);
      const result = await api.retryMessage({ requestId: message.requestId });
      await refresh();
      navigate("chat", result.topicId, message.branchId ?? branchId ?? result.topicId);
    } catch (failure) {
      let reloadFailed = false;
      try { await refresh(); } catch { reloadFailed = true; }
      if (reloadFailed) setError(t("desktop_error_storage"));
      else if (!(failure instanceof Error && failure.message.includes("REQUEST_CANCELLED"))) setError(t(errorKey(failure)));
    } finally { requestInProgress.current = false; activeRequest.current = null; setActiveId(null); setProgress(null); setBusy(false); }
  }

  async function cancel() {
    if (closePending.current || !activeId || cancelling) return;
    setCancelling(true);
    try { await api.cancelMessage({ requestId: activeId }); }
    catch (failure) { setError(t(errorKey(failure))); }
    finally { setCancelling(false); }
  }

  async function changePersona(persona: PersonaId) {
    if (closePending.current || attachmentPending.current || requestInProgress.current || personaSave.current) return;
    setBusy(true); setError(""); setNotice("");
    const pending = api.setPersona({ topicId: view === "chat" ? topicId : null, persona });
    personaSave.current = pending;
    try {
      await pending;
      await refresh();
      setNotice(copyFormat("persona_switched", t(personaNameKey(persona))));
    } catch (failure) { setError(t(errorKey(failure))); }
    finally { if (personaSave.current === pending) personaSave.current = null; setBusy(false); }
  }

  async function changeAttachment(select: boolean) {
    if (closePending.current || requestInProgress.current || attachmentPending.current || personaSave.current || branchPending.current?.promise) return;
    const scope = { topicId: view === "chat" ? topicId : null, branchId: view === "chat" ? branchId : null };
    if (!api.selectAttachment || !api.removeAttachment) { setError(t("desktop_error_generic")); return; }
    setBusy(true); setAttachmentBusy(true); setError("");
    const pending = Promise.resolve().then(async () => {
      if (select) await api.selectAttachment!(scope);
      else await api.removeAttachment!(scope);
    });
    attachmentPending.current = pending;
    try { await pending; await refresh(); }
    catch (failure) { setError(t(errorKey(failure))); }
    finally { if (attachmentPending.current === pending) attachmentPending.current = null; setAttachmentBusy(false); setBusy(false); }
  }

  async function createBranch(message: DesktopMessage) {
    if (closePending.current || attachmentPending.current || busy || !topicId || !branchId || message.role !== "assistant" || message.status !== "complete" || branchPending.current?.promise) return;
    const key = `${topicId}:${branchId}:${message.id}`;
    const requestId = branchPending.current?.key === key ? branchPending.current.requestId : crypto.randomUUID();
    branchPending.current = { key, requestId, promise: null };
    requestInProgress.current = true;
    setBusy(true); setError(""); setNotice("");
    try {
      await draftQueue.drain();
      if (closePending.current) { branchPending.current = null; return; }
      const promise = api.createBranch({ topicId, parentBranchId: branchId, sourceMessageId: message.id, requestId });
      branchPending.current = { key, requestId, promise };
      const result = await promise;
      branchPending.current = null;
      await refresh();
      navigate("chat", topicId, result.branchId);
    } catch (failure) { branchPending.current = { key, requestId, promise: null }; setError(t(errorKey(failure))); }
    finally { requestInProgress.current = false; setBusy(false); }
  }

  async function removeTopic(id: string, title: string) {
    if (closePending.current || attachmentPending.current || busy || !window.confirm(copyFormat("desktop_delete_confirm", title))) return;
    setBusy(true); setError(""); setNotice("");
    try {
      await draftQueue.drain();
      const scopes = [id, ...(state?.topics.find((item) => item.id === id)?.branches?.map((branch) => branch.id) ?? [])];
      if (scopes.some((scope) => failedDrafts.current.has(scope))) return;
      await api.deleteTopic({ topicId: id });
      await refresh();
      setNotice(t("desktop_topic_deleted"));
    } catch (failure) { setError(t(errorKey(failure))); }
    finally { setBusy(false); }
  }

  async function copyAnswer(message: DesktopMessage) {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied({ id: message.id, ok: true });
    } catch { setCopied({ id: message.id, ok: false }); }
  }

  if (!state) return <div className="workspace-status" role={error ? "alert" : "status"}>
    <p>{error || t("workspace_loading")}</p>
    {error ? <button type="button" onClick={() => { setError(""); void refresh().catch((failure: unknown) => setError(t(errorKey(failure)))); }}>{t("desktop_retry_load")}</button> : null}
  </div>;
  const topic = state.topics.find((item) => item.id === topicId);
  const branches = topic?.branches ?? (topic ? [{ id: topic.id, topicId: topic.id, parentBranchId: null, sourceMessageId: null, createdAt: topic.createdAt, messages: topic.messages }] : []);
  const branch = branches.find((item) => item.id === branchId);
  const parent = branches.find((item) => item.id === branch?.parentBranchId);
  const source = parent?.messages.find((message) => message.id === branch?.sourceMessageId && message.role === "assistant" && message.status === "complete");
  const branchLabels = desktopBranchLabels(branches, t("desktop_branch_root"));
  const messages = branch?.messages ?? (branchId === topic?.id ? topic?.messages ?? [] : []);
  const persona = personaFromWireId(view === "chat" ? topic?.persona : state.settings.defaultPersona);
  const draft = drafts[view === "chat" ? branchId ?? topicId ?? "home" : "home"] ?? "";
  const attachment = state.attachmentDrafts?.[view === "chat" ? branchId ?? topicId ?? "home" : "home"];
  const shown = state.topics.filter((item) => {
    const needle = query.trim().toLowerCase();
    const allMessages = item.branches?.flatMap((entry) => entry.messages) ?? item.messages;
    return needle === "" || [item.title, ...allMessages.map((message) => message.content)]
      .some((value) => value.toLowerCase().includes(needle));
  });

  function openSearchResult(item: DesktopTopic) {
    const needle = query.trim().toLowerCase();
    if (!needle || item.title.toLowerCase().includes(needle)) {
      navigate("chat", item.id, item.rootBranchId ?? item.id, null);
      return;
    }
    const matchingBranch = item.branches?.find((entry) => entry.messages.some((message) => message.content.toLowerCase().includes(needle)));
    const matchingMessage = matchingBranch?.messages.find((message) => message.content.toLowerCase().includes(needle))
      ?? (!item.branches ? item.messages.find((message) => message.content.toLowerCase().includes(needle)) : undefined);
    navigate("chat", item.id, matchingBranch?.id ?? item.rootBranchId ?? item.id, matchingMessage?.id ?? null);
  }

  return (
    <div className="app-workspace desktop-workspace" data-nav={navOpen ? "expanded" : "collapsed"}>
      {navOpen ? <button className="nav-scrim" type="button" aria-label={t("nav_collapse")} onClick={() => setNavOpen(false)} /> : null}
      <aside className="app-sidebar desktop-sidebar" aria-label={t("nav_primary")}>
        <button className="brand" type="button" disabled={busy} onClick={() => navigate("home")}>
          <Icon name="brand_sprout" className="brand-mark" /><span className="brand-word">{t("app_name")}</span>
        </button>
        <nav className="side-nav">
          <button className={`side-link${view === "home" ? " active" : ""}`} type="button" disabled={busy} onClick={() => navigate("home")}><Icon name="tab_home" /><span>{t("desktop_new_chat")}</span></button>
          <button className={`side-link${view === "history" ? " active" : ""}`} type="button" disabled={busy} onClick={() => navigate("history")}><Icon name="tab_library" /><span>{t("desktop_history_title")}</span></button>
          <button className={`side-link${view === "settings" ? " active" : ""}`} type="button" disabled={busy} data-action="desktop-settings" onClick={() => navigate("settings")}><Icon name="chat_gear" /><span>{t("nav_settings")}</span></button>
        </nav>
        <p className="side-heading">{t("nav_recent")}</p>
        <div className="recent">{state.topics.map((item) => (
          <button className={`recent-link${view === "chat" && topicId === item.id ? " active" : ""}`}
            key={item.id} type="button" disabled={busy} onClick={() => navigate("chat", item.id)}>
            <span className="recent-title">{item.title}</span>
          </button>
        ))}</div>
        {view === "chat" && topic && branchId ? <DesktopBranches branches={branches} active={branchId} disabled={busy || closing} onSelect={(id) => navigate("chat", topic.id, id)} /> : null}
      </aside>
      <div className="workspace-column">
        <header className="workspace-topbar">
          <button className="icon-btn desktop-menu" type="button" aria-label={t("desktop_menu")} aria-expanded={navOpen} onClick={() => setNavOpen(!navOpen)}><Icon name="sidebar" /></button>
          <div className="workspace-crumb"><span className="crumb-current">{view === "chat" ? topic?.title : view === "settings" ? t("desktop_settings_title") : view === "history" ? t("desktop_history_title") : t("tab_home")}</span></div>
        </header>
        <main className="workspace-main desktop-main">
          {closing ? <p className="desktop-alert" role="status">{t("desktop_close_wait")}</p> : null}
          {failedDraftCount > 0 ? <p className="desktop-alert" role="alert">{t("desktop_save_failed")} <button type="button" disabled={closing} onClick={retryDrafts}>{t("desktop_retry_drafts")}</button></p> : null}
          {error ? <p className="desktop-alert" role="alert">{error} {!state.settings.hasApiKey && view !== "settings" ? <button type="button" onClick={() => navigate("settings")}>{t("desktop_open_settings")}</button> : null}</p> : null}
          {notice ? <p className="desktop-alert" role="status">{notice}</p> : null}
          {view === "settings" ? <div className="page-scroll"><DesktopSettings api={api} settings={state.settings} refresh={refresh} reportError={(failure) => setError(t(errorKey(failure)))} disabled={closing} /></div> : null}
          {view === "history" ? <div className="page-scroll"><section className="page" data-screen="library">
            <h1 className="page-title">{t("desktop_history_title")}</h1>
            <div className="library-tools"><div className="search-field"><Icon name="search" /><input type="search" aria-label={t("desktop_search_label")} placeholder={t("desktop_search_placeholder")} value={query} onChange={(event) => setQuery(event.target.value)} /></div></div>
            {shown.length === 0 ? <p className="empty-state">{query.trim() ? t("desktop_no_results") : t("desktop_no_topics")}</p> : <ul className="topic-list">{shown.map((item) => <li className="topic-row" key={item.id}><button className="topic-open" type="button" disabled={busy} onClick={() => openSearchResult(item)}><strong>{item.title}</strong><Icon name="chevron_right" /></button><button className="desktop-delete" type="button" disabled={busy} aria-label={`${t("desktop_delete_topic")}：${item.title}`} onClick={() => { void removeTopic(item.id, item.title); }}>{t("desktop_delete_topic")}</button></li>)}</ul>}
          </section></div> : null}
          {view === "home" || view === "chat" ? <div className="desktop-conversation" data-screen={view}>
            <div ref={scrollRef} className="page-scroll desktop-conversation-scroll" onScroll={(event) => {
              const element = event.currentTarget;
              followAnswer.current = element.scrollHeight - element.scrollTop - element.clientHeight < 80;
            }}><div className="page">
              {view === "home" ? <><p className="page-eyebrow"><Icon name="brand_sprout" />{t("home_eyebrow")}</p><h1 className="home-title">{t("home_headline")}</h1>
                {!state.settings.hasApiKey ? <button className="settings-action" type="button" onClick={() => navigate("settings")}>{t("desktop_open_settings")}</button> : null}
                {state.topics.length === 0 ? <p className="desktop-empty">{t("desktop_no_topics")}</p> : <><h2 className="home-heading">{t("desktop_history_title")}</h2><ul className="topic-list">{state.topics.map((item) => <li className="topic-row" key={item.id}><button className="topic-open" type="button" disabled={busy} onClick={() => navigate("chat", item.id)}><strong>{item.title}</strong><Icon name="chevron_right" /></button></li>)}</ul></>}
              </> : <><h1 className="chat-title" ref={chatTitleRef} tabIndex={-1}>{topic?.title ?? activeRequest.current?.text ?? t("app_name")}</h1>
                {branch?.parentBranchId ? <section className="desktop-branch-source" data-branch-source>
                  <p>{t("desktop_branch_source_label")} · {branchLabels.get(branch.parentBranchId) ?? t("desktop_branch_navigation")}</p>
                  {source ? <><p className="desktop-branch-excerpt">{source.content.slice(0, 180)}{source.content.length > 180 ? "…" : ""}</p><button type="button" className="settings-action" data-action="desktop-branch-parent" disabled={busy || closing} onClick={() => {
                    if (!topicId || !branch.parentBranchId) return;
                    navigate("chat", topicId, branch.parentBranchId, source.id);
                  }}>{t("desktop_branch_parent")}</button></> : <p>{t("desktop_branch_source_missing")}</p>}
                </section> : null}
                {messages.map((message, index) => <article className={`desktop-message desktop-message-${message.role}`} key={message.id} data-message-id={message.id} tabIndex={-1} ref={(element) => { if (element) messageElements.current.set(message.id, element); else messageElements.current.delete(message.id); }}>
                  <p className="desktop-message-label">{message.role === "user" ? t("desktop_user") : t("desktop_assistant")}</p>
                  {message.role === "user" && message.attachment ? <p className="field-note">{copyFormat("desktop_attachment_history", message.attachment.name)}</p> : null}
                  {message.role === "assistant" && message.persona ? <p className="field-note">{personaFromWireId(message.persona).fellBack ? t("desktop_persona_unknown_history") : t(personaNameKey(personaFromWireId(message.persona).persona))}</p> : null}
                  {message.role === "assistant" && (message.status === "failed" || message.status === "cancelled" || message.status === "interrupted") ? <p className="desktop-partial-status">{t(message.status === "failed" ? "desktop_partial_failed" : message.status === "cancelled" ? "desktop_partial_cancelled" : "desktop_partial_interrupted")}</p> : null}
                  <div className="desktop-message-body">{message.role === "assistant" ? <DesktopMarkdown text={message.content} /> : message.content}</div>
                  {message.role === "assistant" ? <div className="desktop-copy"><button type="button" className="settings-action" onClick={() => { void copyAnswer(message); }}>{t("chat_copy")}</button>{message.status === "complete" ? <button type="button" className="settings-action" data-action="desktop-create-branch" disabled={busy || closing} onClick={() => { void createBranch(message); }}>{t("desktop_branch_create")}</button> : null}<span role="status">{copied?.id === message.id ? t(copied.ok ? "chat_copied" : "chat_copy_failed") : ""}</span></div> : null}
                  {message.role === "user" && (message.status === "failed" || message.status === "interrupted" || message.status === "cancelled") ? <div className="desktop-message-status"><span>{t(message.status === "failed" ? "desktop_failed" : message.status === "cancelled" ? "desktop_cancelled" : "desktop_interrupted")}</span>{message.requestId && !messages.slice(index + 1).some((next) => next.role === "user") ? <button className="settings-action" data-action="desktop-retry" type="button" disabled={busy} onClick={() => { void retry(message); }}>{t("desktop_retry")}</button> : null}</div> : null}
                  {message.status === "pending" ? <p className="field-note">{t("desktop_pending")}</p> : null}
                </article>)}
                {activeRequest.current && !messages.some((message) => message.requestId === activeRequest.current?.requestId)
                  ? <article className="desktop-message desktop-message-user"><p className="desktop-message-label">{t("desktop_user")}</p><div className="desktop-message-body">{activeRequest.current.text}</div></article> : null}
                {progress?.topicId === topicId && (progress.branchId ?? progress.topicId) === branchId ? <article className="desktop-message desktop-message-assistant" aria-live="polite"><p className="desktop-message-label">{t("desktop_assistant")}</p><DesktopMarkdown text={progress.content} /></article> : null}
              </>}
            </div></div>
            <form className="composer desktop-composer" onSubmit={(event) => { void send(event); }}>
              <DesktopAttachment attachment={attachment} disabled={busy || closing} selecting={attachmentBusy} onSelect={() => { void changeAttachment(true); }} onRemove={() => { void changeAttachment(false); }} />
              <textarea aria-label={t("home_composer_label")} placeholder={t("desktop_composer_placeholder")} rows={2} value={draft} disabled={busy || closing} onChange={(event) => updateDraft(event.target.value)} onCompositionStart={() => { composing.current = true; }} onCompositionEnd={() => { composing.current = false; }} onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing && !composing.current) {
                  event.preventDefault(); event.currentTarget.form?.requestSubmit();
                }
              }} />
              <div className="composer-row"><span className="chip">{state.settings.model}</span><label className="desktop-persona-label"><span className="desktop-persona-caption">{t("persona_picker_label")}</span><select className="chip desktop-persona-select" aria-label={t("persona_picker_label")} value={persona.persona} disabled={busy || closing || (view === "chat" && !topic)} onChange={(event) => { void changePersona(event.target.value as PersonaId); }}>{PERSONA_ORDER.map((id) => <option key={id} value={id}>{t(personaNameKey(id))}</option>)}</select></label><span className="composer-spacer" />{busy && activeId ? <button className="settings-action" type="button" disabled={cancelling} onClick={() => { void cancel(); }}>{t(cancelling ? "desktop_stopping" : "desktop_stop")}</button> : <button className="icon-btn composer-send" type="submit" aria-label={t("desktop_send")} title={t("desktop_send")} disabled={busy || !draft.trim()}><Icon name="chat_send" /></button>}</div>
              {persona.fellBack ? <p role="status" className="field-note">{t("persona_fallback_notice")} <button type="button" className="desktop-persona-reset" disabled={busy || closing || (view === "chat" && !topic)} onClick={() => { void changePersona("default"); }}>{t("desktop_persona_use_default")}</button></p> : null}
              {busy && activeId ? <p role="status" className="field-note">{t("desktop_pending")}</p> : null}
            </form>
          </div> : null}
        </main>
      </div>
    </div>
  );
}
