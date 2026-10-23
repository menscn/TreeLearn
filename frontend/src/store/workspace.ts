export type WorkspaceView = "home" | "library" | "settings" | "chat";

export type LibraryFilter = "all" | "in_progress" | "done";

export interface WorkspaceLocation {
  view: WorkspaceView;
  topic: number;
}

export interface WorkspaceState extends WorkspaceLocation {
  branch: number;
  stack: WorkspaceLocation[];
  panelOpen: boolean;
  treeOpen: boolean;
  navCollapsed: boolean;
  drafts: Record<string, string>;
  scroll: Record<string, number>;
  libraryQuery: string;
  libraryFilter: LibraryFilter;
  libraryExpanded: string[];
  settingsCategory: string;
}

export const DEFAULT_SETTINGS_CATEGORY = "api-key";

const VIEWS: ReadonlySet<string> = new Set(["home", "library", "settings", "chat"]);

function isView(value: string): value is WorkspaceView {
  return VIEWS.has(value);
}

function viewOf(value: string): WorkspaceView {
  return isView(value) ? value : "home";
}

function initialNavCollapsed(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false;
  return window.matchMedia("(max-width: 760px)").matches;
}

function initialLocation(): WorkspaceLocation {
  if (typeof window === "undefined") return { view: "home", topic: 0 };
  return parseWorkspaceHash(window.location.hash);
}

function freshState(): WorkspaceState {
  const location = initialLocation();
  return {
    view: location.view,
    topic: location.topic,
    branch: 0,
    stack: [{ ...location }],
    panelOpen: false,
    treeOpen: false,
    navCollapsed: initialNavCollapsed(),
    drafts: {},
    scroll: {},
    libraryQuery: "",
    libraryFilter: "all",
    libraryExpanded: [],
    settingsCategory: DEFAULT_SETTINGS_CATEGORY
  };
}

let state: WorkspaceState = freshState();
const listeners = new Set<() => void>();

function emit(): void {
  for (const listener of listeners) listener();
}

function writeHash(mode: "push" | "replace"): void {
  if (typeof window === "undefined") return;
  const hash = serializeWorkspaceLocation({ view: state.view, topic: state.topic });
  if (window.location.hash === hash) return;
  if (mode === "push") window.history.pushState(null, "", hash);
  else window.history.replaceState(null, "", hash);
}

function normalizedTopic(topic: number, count?: number): number {
  if (!Number.isInteger(topic) || topic < 0) return 0;
  if (count !== undefined && topic >= count) return 0;
  return topic;
}

export function parseWorkspaceHash(hash: string): WorkspaceLocation {
  const raw = (hash.startsWith("#") ? hash.slice(1) : hash).replace(/^\/+/, "");
  const [viewPart = "", topicPart = ""] = raw.split("/");
  const view = viewOf(viewPart);
  const parsed = Number.parseInt(topicPart, 10);
  const topic = Number.isInteger(parsed) && parsed > 0 ? parsed : 0;
  return { view, topic: view === "chat" ? topic : 0 };
}

export function serializeWorkspaceLocation(location: WorkspaceLocation): string {
  return `#/${location.view}/${location.topic}`;
}

export function workspaceSnapshot(): WorkspaceState {
  return state;
}

export function subscribeWorkspace(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function canGoBackWorkspace(): boolean {
  return state.stack.length > 1;
}

export function draftFor(key: string): string {
  return state.drafts[key] ?? "";
}

export function scrollFor(key: string): number {
  return state.scroll[key] ?? 0;
}

export function openView(view: WorkspaceView): void {
  const topic = view === "chat" ? state.topic : 0;
  if (state.view === view && state.topic === topic) return;
  const next = { view, topic };
  state = {
    ...state,
    ...next,
    branch: 0,
    panelOpen: false,
    treeOpen: false,
    stack: [...state.stack, next]
  };
  emit();
  writeHash("push");
}

export function openTopic(topic: number, count?: number): void {
  const nextTopic = normalizedTopic(topic, count);
  if (state.view === "chat" && state.topic === nextTopic) return;
  const next = { view: "chat" as const, topic: nextTopic };
  state = {
    ...state,
    ...next,
    branch: 0,
    panelOpen: false,
    treeOpen: false,
    stack: [...state.stack, next]
  };
  emit();
  writeHash("push");
}

export function back(): void {
  if (state.stack.length <= 1) return;
  const stack = state.stack.slice(0, -1);
  const previous = stack[stack.length - 1];
  if (previous === undefined) return;
  state = {
    ...state,
    view: previous.view,
    topic: previous.topic,
    branch: 0,
    panelOpen: false,
    treeOpen: false,
    stack
  };
  emit();
  writeHash("push");
}

export function setPanelOpen(open: boolean): void {
  if (state.panelOpen === open) return;
  state = { ...state, panelOpen: open, treeOpen: open ? false : state.treeOpen };
  emit();
}

export function setTreeOpen(open: boolean): void {
  if (state.treeOpen === open) return;
  state = { ...state, treeOpen: open, panelOpen: open ? false : state.panelOpen };
  emit();
}

export function toggleNav(): void {
  setNavCollapsed(!state.navCollapsed);
}

export function setNavCollapsed(value: boolean): void {
  if (state.navCollapsed === value) return;
  state = { ...state, navCollapsed: value };
  emit();
}

export function setDraft(key: string, text: string): void {
  state.drafts[key] = text;
}

export function rememberScroll(key: string, top: number): void {
  state.scroll[key] = Number.isFinite(top) && top > 0 ? top : 0;
}

export function setLibraryQuery(query: string): void {
  if (state.libraryQuery === query) return;
  state = { ...state, libraryQuery: query };
  emit();
}

export function setLibraryFilter(filter: LibraryFilter): void {
  if (state.libraryFilter === filter) return;
  state = { ...state, libraryFilter: filter };
  emit();
}

export function toggleSummary(id: string): void {
  const expanded = state.libraryExpanded.includes(id)
    ? state.libraryExpanded.filter((item) => item !== id)
    : [...state.libraryExpanded, id];
  state = { ...state, libraryExpanded: expanded };
  emit();
}

export function setSettingsCategory(id: string): void {
  if (state.settingsCategory === id) return;
  state = { ...state, settingsCategory: id };
  emit();
}

export function applyWorkspaceHash(hash: string): void {
  const next = parseWorkspaceHash(hash);
  if (state.view === next.view && state.topic === next.topic) return;
  state = {
    ...state,
    view: next.view,
    topic: next.topic,
    branch: 0,
    panelOpen: false,
    treeOpen: false,
    stack: [{ ...next }]
  };
  emit();
}

export function resetWorkspace(): void {
  state = freshState();
  emit();
  writeHash("replace");
}
