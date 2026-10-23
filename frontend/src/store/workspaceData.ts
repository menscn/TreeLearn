import {
  firstById,
  groupsById,
  pickChild,
  subtreeOf,
  type FixtureNode,
  type FixturePayload
} from "../platform/fixtureNodes";

/**
 * 夹具 → 工作区视图模型。
 *
 * 这里只重排真机上已经存在的文字与状态，不补写任何演示内容：
 * 夹具里没有的字段保持 null/空串，由页面显示「未采集」而不是猜一个值。
 */

export type ScreenKey =
  | "home"
  | "library"
  | "chat"
  | "navigation"
  | "treecanvas"
  | "settings"
  | "settingskey"
  | "provideradmin"
  | "modelsettings"
  | "searchservicesettings"
  | "voicesettings"
  | "filemanager";

export const WORKSPACE_SCREENS: readonly ScreenKey[] = [
  "home",
  "library",
  "chat",
  "navigation",
  "treecanvas",
  "settings",
  "settingskey",
  "provideradmin",
  "modelsettings",
  "searchservicesettings",
  "voicesettings",
  "filemanager"
];

export type PayloadMap = Partial<Record<ScreenKey, FixturePayload | null>>;

export type WorkspaceStatus = "loading" | "ready" | "partial" | "error";

export type TopicStatus = "in_progress" | "done" | "unknown";

export interface Topic {
  id: string;
  title: string;
  summary: string;
  meta: string;
  status: TopicStatus;
  branchCount: number | null;
  questions: string[];
}

export interface HomeModel {
  draftPlaceholder: string;
  model: string;
  recommendationsTitle: string;
  recentTitle: string;
  recentCount: string;
}

export interface Recommendation {
  tag: string;
  text: string;
  topicIndex: number;
}

export type AnswerKind = "h2" | "h3" | "p" | "list";

export interface AnswerBlock {
  kind: AnswerKind;
  text?: string;
  items?: string[];
}

export interface ChatModel {
  topicIndex: number;
  title: string;
  answer: string;
  blocks: AnswerBlock[];
  time: string;
  model: string;
  placeholder: string;
  suggestions: string[];
}

export interface BranchModel {
  topicIndex: number;
  title: string;
  rootChip: string;
  rootMeta: string;
  summary: string;
  knownChildren: number | null;
  captured: boolean;
}

export interface TreeModel {
  topicIndex: number;
  title: string;
  subtitle: string;
  rootLabel: string;
}

export interface SettingsField {
  label: string;
  value?: string;
  description?: string;
  control?: "switch";
  state?: "on" | "off" | "unknown";
  selected?: boolean;
  action?: boolean;
  disabled?: boolean;
}

export interface SettingsGroup {
  heading?: string;
  fields: SettingsField[];
  notes: string[];
}

export interface SettingsCategory {
  id: string;
  title: string;
  summary: string;
  available: boolean;
  groups: SettingsGroup[];
  actions: SettingsField[];
}

export interface WorkspaceData {
  status: WorkspaceStatus;
  home: HomeModel | null;
  topics: Topic[];
  recommendations: Recommendation[];
  chat: ChatModel | null;
  branches: BranchModel[];
  tree: TreeModel | null;
  settings: SettingsCategory[];
}

function textOf(payload: FixturePayload | null | undefined, id: string): string {
  if (!payload) return "";
  return firstById(payload.nodes, id)?.text ?? "";
}

function textsOf(payload: FixturePayload | null | undefined): string[] {
  if (!payload) return [];
  return payload.nodes.map((node) => node.text).filter((text) => text.length > 0);
}

function topicIndexOf(topics: Topic[], title: string): number {
  const index = topics.findIndex((topic) => topic.title === title);
  return index < 0 ? 0 : index;
}

function segmentCount(payload: FixturePayload, id: string): number | null {
  const text = textOf(payload, id);
  const match = text.match(/(\d+)/);
  return match ? Number.parseInt(match[1] ?? "", 10) : null;
}

function statusFromMeta(meta: string): TopicStatus {
  if (meta.includes("已完成")) return "done"; // i18n-ignore: 读取夹具 meta 里的状态
  if (meta.includes("进行中")) return "in_progress"; // i18n-ignore: 读取夹具 meta 里的状态
  return "unknown";
}

function statusesFor(metas: readonly string[], payload: FixturePayload): TopicStatus[] {
  const statuses = metas.map(statusFromMeta);
  const unknown = statuses.filter((status) => status === "unknown").length;
  if (unknown === 0) return statuses;
  const capturedInProgress = segmentCount(payload, "library_segment_in_progress");
  const capturedDone = segmentCount(payload, "library_segment_done");
  if (capturedInProgress === null || capturedDone === null) return statuses;
  const remainingInProgress = capturedInProgress
    - statuses.filter((status) => status === "in_progress").length;
  const remainingDone = capturedDone - statuses.filter((status) => status === "done").length;
  // 分段是采集到的真实汇总：只有余额正好覆盖全部未知条目、另一段没有余额时才回填，
  // 否则保持 unknown，不用默认值把条目算进任何一段。
  const fill: TopicStatus | null = remainingInProgress === unknown && remainingDone === 0
    ? "in_progress"
    : remainingDone === unknown && remainingInProgress === 0
      ? "done"
      : null;
  return fill === null
    ? statuses
    : statuses.map((status) => (status === "unknown" ? fill : status));
}

export function topicsFrom(payloads: PayloadMap): Topic[] {
  const payload = payloads.library;
  if (!payload) return [];
  const recommendations = recommendationsFrom(payloads);
  const groups = groupsById(payload.nodes, "library_topic");
  const metas = groups.map((group) => pickChild(group.children, "library_topic_meta")?.text ?? "");
  const statuses = statusesFor(metas, payload);
  return groups.map((group, index) => {
    const title = pickChild(group.children, "library_topic_title")?.text ?? "";
    const summary = pickChild(group.children, "library_topic_summary")?.text ?? "";
    const meta = metas[index] ?? "";
    const branchMatch = meta.match(/(\d+)\s*个分支/); // i18n-ignore: 解析夹具里的分支计数
    return {
      id: `topic-${index}`,
      title,
      summary,
      meta,
      status: statuses[index] ?? "unknown",
      branchCount: branchMatch ? Number.parseInt(branchMatch[1] ?? "", 10) : null,
      questions: recommendations.filter((item) => item.topicIndex === index).map((item) => item.text)
    };
  });
}

export function recommendationsFrom(payloads: PayloadMap): Recommendation[] {
  const payload = payloads.home;
  if (!payload) return [];
  // 首页推荐区不带主题身份；主页对应最近主题，即学习库首项。
  return groupsById(payload.nodes, "rec_chip")
    .map((group) => ({
      tag: pickChild(group.children, "rec_tag")?.text ?? "",
      text: pickChild(group.children, "rec_text")?.text ?? "",
      topicIndex: 0
    }))
    .filter((item) => item.text !== "");
}

export function homeFrom(payloads: PayloadMap): HomeModel | null {
  const payload = payloads.home;
  if (!payload) return null;
  return {
    draftPlaceholder: textOf(payload, "home_draft"),
    model: textOf(payload, "model_chip_label"),
    recommendationsTitle: textOf(payload, "home_recommendations_title"),
    recentTitle: textOf(payload, "home_recent_title"),
    recentCount: textOf(payload, "home_recent_count")
  };
}

const H2_LINE = /^[一二三四五六七八九十]+、/; // i18n-ignore: 识别夹具中的小节编号
const H3_LINE = /^(?:\d+\.\s|方法\s*\d+[：:])/; // i18n-ignore: 识别夹具中的步骤与方法编号
const BULLET_LINE = /^[•·]\s*/;

function stripBullet(line: string): string {
  return line.replace(BULLET_LINE, "").trim();
}

export function answerBlocks(text: string): AnswerBlock[] {
  const blocks: AnswerBlock[] = [];
  const normalized = text.replace(/\r\n?/g, "\n").trim();
  if (normalized === "") return blocks;
  for (const segment of normalized.split(/\n{2,}/)) {
    const lines = segment.split("\n").map((line) => line.trim()).filter((line) => line !== "");
    const first = lines[0];
    if (first === undefined) continue;
    if (H2_LINE.test(first) || H3_LINE.test(first)) {
      blocks.push({ kind: H2_LINE.test(first) ? "h2" : "h3", text: first });
      const rest = lines.slice(1).join("\n").trim();
      if (rest !== "") blocks.push({ kind: "p", text: rest });
      continue;
    }
    if (BULLET_LINE.test(first)) {
      const items = lines.filter((line) => BULLET_LINE.test(line)).map(stripBullet);
      const previous = blocks[blocks.length - 1];
      if (previous?.kind === "list") {
        previous.items = [...(previous.items ?? []), ...items];
      } else {
        blocks.push({ kind: "list", items });
      }
      const rest = lines.filter((line) => !BULLET_LINE.test(line));
      if (rest.length > 0) blocks.push({ kind: "p", text: rest.join("\n") });
      continue;
    }
    blocks.push({ kind: "p", text: lines.join("\n") });
  }
  return blocks;
}

export function chatFrom(payloads: PayloadMap): ChatModel | null {
  const payload = payloads.chat;
  if (!payload) return null;
  const answer = textOf(payload, "chat_answer");
  if (answer === "") return null;
  const title = textOf(payload, "chat_title");
  return {
    topicIndex: topicIndexOf(topicsFrom(payloads), title),
    title,
    answer,
    blocks: answerBlocks(answer),
    time: textOf(payload, "act_time"),
    model: textOf(payload, "model_chip_label"),
    placeholder: textOf(payload, "chat_draft"),
    suggestions: payload.nodes
      .filter((node) => node.id === "suggestion_text")
      .map((node) => node.text)
      .filter((text) => text !== "")
  };
}

export function branchesFor(payloads: PayloadMap): BranchModel[] {
  const topics = topicsFrom(payloads);
  if (topics.length === 0) return [];
  const branches: BranchModel[] = [];
  const navigation = payloads.navigation;
  if (navigation) {
    const title = textOf(navigation, "navigation_current_title");
    const rootMeta = textOf(navigation, "navigation_row_meta");
    const childrenMatch = rootMeta.match(/(\d+)\s*子分支/); // i18n-ignore: 解析夹具里的子分支计数
    branches.push({
      topicIndex: topicIndexOf(topics, title),
      title,
      rootChip: textOf(navigation, "navigation_current_chip"),
      rootMeta,
      summary: textOf(navigation, "navigation_summary_text"),
      knownChildren: childrenMatch ? Number.parseInt(childrenMatch[1] ?? "", 10) : null,
      captured: true
    });
  }
  topics.forEach((topic, index) => {
    if (branches.some((branch) => branch.topicIndex === index)) return;
    // 未采集导航的主题只保留学习库 meta 能证明的数量；子分支数按「除当前根之外」计。
    branches.push({
      topicIndex: index,
      title: topic.title,
      rootChip: "",
      rootMeta: "",
      summary: topic.summary,
      knownChildren: topic.branchCount === null ? null : Math.max(topic.branchCount - 1, 0),
      captured: false
    });
  });
  return branches;
}

export function branchesFrom(payloads: PayloadMap): BranchModel | null {
  return branchesFor(payloads)[0] ?? null;
}

export function treeFrom(payloads: PayloadMap): TreeModel | null {
  const payload = payloads.treecanvas;
  if (!payload) return null;
  const title = textOf(payload, "tree_title");
  const subtitle = textOf(payload, "tree_subtitle");
  if (title === "" && subtitle === "") return null;
  return {
    topicIndex: topicIndexOf(topicsFrom(payloads), subtitle),
    title,
    subtitle,
    rootLabel: subtitle === "" ? title : subtitle
  };
}

type SettingsSubKey = Extract<
  ScreenKey,
  "settingskey" | "provideradmin" | "modelsettings" | "searchservicesettings" | "voicesettings" | "filemanager"
>;

const CATEGORY_ORDER = [
  { id: "api-key", entry: "settings_key_entry", sub: "settingskey" },
  { id: "provider", entry: "settings_providers_entry", sub: "provideradmin" },
  { id: "model", entry: "settings_models_entry", sub: "modelsettings" },
  { id: "search", entry: "settings_search_entry", sub: "searchservicesettings" },
  { id: "voice", entry: "settings_voice_entry", sub: "voicesettings" },
  { id: "files", entry: "settings_files_entry", sub: "filemanager" }
] as const;

function entryMeta(payload: FixturePayload, containerId: string): { title: string; summary: string } {
  const container = firstById(payload.nodes, containerId);
  if (!container) return { title: "", summary: "" };
  const texts = subtreeOf(payload.nodes, container)
    .map((node) => node.text)
    .filter((text) => text !== "");
  return { title: texts[0] ?? "", summary: texts[1] ?? "" };
}

function textNodes(payload: FixturePayload | null | undefined): FixtureNode[] {
  if (!payload) return [];
  return payload.nodes.filter((node) => node.text !== "");
}

function emptyCategory(id: string, title: string, summary: string): SettingsCategory {
  return { id, title, summary, available: false, groups: [{ fields: [], notes: [] }], actions: [] };
}

function apiKeyCategory(payload: FixturePayload | null, title: string, summary: string): SettingsCategory {
  if (!payload) return emptyCategory("api-key", title, summary);
  const nodes = textNodes(payload);
  const maskNode = firstById(payload.nodes, "settings_key_mask");
  const stateNode = firstById(payload.nodes, "settings_key_state");
  const deleteNode = firstById(payload.nodes, "settings_key_delete_link");
  const inputNode = firstById(payload.nodes, "settings_key_input");
  const saveNode = firstById(payload.nodes, "settings_key_save");
  const at = (node: FixtureNode | null): number => (node ? nodes.indexOf(node) : -1);
  const deleteAt = at(deleteNode);
  const inputAt = at(inputNode);
  const note = deleteAt > 0 ? nodes[deleteAt - 1]?.text ?? "" : "";
  const replaceLabel = inputAt > 0 ? nodes[inputAt - 1]?.text ?? "" : "";
  const footnote = inputAt >= 0 ? nodes[inputAt + 1]?.text ?? "" : "";
  const groups: SettingsGroup[] = [];
  if (maskNode || note !== "") {
    groups.push({
      fields: maskNode ? [{ label: maskNode.text, value: stateNode?.text ?? "" }] : [],
      notes: note === "" ? [] : [note]
    });
  }
  if (inputNode) {
    groups.push({
      heading: replaceLabel,
      fields: [{ label: inputNode.text, disabled: true }],
      notes: footnote === "" ? [] : [footnote]
    });
  }
  if (groups.length === 0) groups.push({ fields: [], notes: [] });
  const actions: SettingsField[] = [];
  if (deleteNode) actions.push({ label: deleteNode.text, action: true, disabled: true });
  if (saveNode?.desc) actions.push({ label: saveNode.desc, action: true, disabled: true });
  return {
    id: "api-key",
    title,
    summary,
    available: true,
    groups,
    actions
  };
}

function providerCategory(payload: FixturePayload | null, title: string, summary: string): SettingsCategory {
  if (!payload) return emptyCategory("provider", title, summary);
  const texts = textsOf(payload);
  const enabledMatch = summary.match(/已启用\s*(\d+)/); // i18n-ignore: 解析夹具里的启用家数
  const enabled = enabledMatch ? Number.parseInt(enabledMatch[1] ?? "", 10) > 0 : false;
  const fields: SettingsField[] = [];
  if (texts[2] !== undefined) fields.push({ label: texts[2], value: texts[3] ?? "" });
  if (texts[4] !== undefined) fields.push({ label: texts[4] });
  const toggle = payload.nodes.find((node) => node.class.endsWith("Switch"));
  if (toggle) {
    fields.push({
      label: toggle.desc,
      control: "switch",
      state: enabled ? "on" : "off",
      disabled: true
    });
  }
  const actions: SettingsField[] = texts[1] === undefined
    ? []
    : [{ label: texts[1], action: true, disabled: true }];
  return { id: "provider", title, summary, available: true, groups: [{ fields, notes: [] }], actions };
}

interface Spoken {
  node: FixtureNode;
  text: string;
  isDesc: boolean;
}

function spokenNodes(payload: FixturePayload): Spoken[] {
  return payload.nodes
    .filter((node) => node.text !== "" || node.desc !== "")
    .map((node) => ({ node, text: node.text !== "" ? node.text : node.desc, isDesc: node.text === "" }));
}

function isSwitch(node: FixtureNode): boolean {
  return node.class.endsWith("Switch");
}

function modelCategory(payload: FixturePayload | null, title: string, summary: string): SettingsCategory {
  if (!payload) return emptyCategory("model", title, summary);
  const spoken = spokenNodes(payload);
  const fields: SettingsField[] = [];
  const notes: string[] = [];
  let pendingSwitchLabel = "";
  for (let index = 0; index < spoken.length; index += 1) {
    const item = spoken[index];
    if (item === undefined) continue;
    if (isSwitch(item.node)) {
      fields.push({
        label: pendingSwitchLabel !== "" ? pendingSwitchLabel : item.text,
        description: item.node.desc,
        control: "switch",
        state: "unknown",
        disabled: true
      });
      pendingSwitchLabel = "";
      continue;
    }
    if (item.isDesc || item.text === title || item.text === "›") continue;
    const next = spoken[index + 1];
    const after = spoken[index + 2];
    if (next?.node && isSwitch(next.node)) {
      pendingSwitchLabel = item.text;
      continue;
    }
    if (next !== undefined && after !== undefined && after.text === "›") {
      fields.push({ label: item.text, value: next.text, disabled: true });
      index += 2;
      continue;
    }
    notes.push(item.text);
  }
  return { id: "model", title, summary, available: true, groups: [{ fields, notes }], actions: [] };
}

function searchCategory(payload: FixturePayload | null, title: string, summary: string): SettingsCategory {
  if (!payload) return emptyCategory("search", title, summary);
  const texts = textsOf(payload);
  const spoken = spokenNodes(payload);
  const toggleIndex = spoken.findIndex((item) => isSwitch(item.node));
  const toggle = toggleIndex >= 0 ? spoken[toggleIndex] : undefined;
  const switchLabel = toggleIndex > 0 ? spoken[toggleIndex - 1] : undefined;
  const note = texts[2] ?? "";
  const defaultOff = /默认关闭/.test(note); // i18n-ignore: 解析夹具里的默认状态
  const fields: SettingsField[] = [];
  if (toggle) {
    fields.push({
      label: switchLabel && !switchLabel.isDesc ? switchLabel.text : toggle.text,
      description: toggle.node.desc,
      control: "switch",
      state: defaultOff ? "off" : "unknown",
      disabled: true
    });
  }
  const engineStarts: number[] = [];
  for (let index = 0; index < texts.length - 1; index += 1) {
    if (/未配置/.test(texts[index + 1] ?? "")) engineStarts.push(index); // i18n-ignore: 解析夹具里的引擎状态
  }
  engineStarts.forEach((start, order) => {
    const end = engineStarts[order + 1] ?? texts.length;
    const selected = texts.slice(start, end).some((text) => text === "✓");
    fields.push({ label: texts[start] ?? "", value: texts[start + 1] ?? "", selected });
  });
  const unsaved = texts.find((text) => /未保存/.test(text)); // i18n-ignore: 解析夹具里的保存状态
  const placeholder = firstById(payload.nodes, "settings_key_input")?.text
    ?? texts.find((text) => /输入/.test(text)); // i18n-ignore: 解析夹具里的输入占位
  if (placeholder) fields.push({ label: placeholder, disabled: true });
  const actions: SettingsField[] = [];
  const saveButton = payload.nodes.find((node) => node.class.endsWith("Button") && node.text !== "");
  if (saveButton) actions.push({ label: saveButton.text, action: true, disabled: true });
  const notes = [note, unsaved ?? ""].filter((text) => text !== "");
  const groups: SettingsGroup[] = [{ fields, notes }];
  for (let index = 0; index < texts.length - 2; index += 1) {
    if (texts[index + 2] !== "›") continue;
    groups.push({
      heading: texts[index] ?? "",
      fields: [{ label: texts[index + 1] ?? "", action: true, disabled: true }],
      notes: []
    });
    index += 2;
  }
  return { id: "search", title, summary, available: true, groups, actions };
}

function voiceCategory(payload: FixturePayload | null, title: string, summary: string): SettingsCategory {
  if (!payload) return emptyCategory("voice", title, summary);
  const texts = textsOf(payload);
  const fields: SettingsField[] = [];
  if (texts[2] !== undefined) {
    fields.push({ label: texts[2], value: texts[3] ?? "", selected: texts.includes("✓") });
  }
  const button = payload.nodes.find((node) => node.class.endsWith("Button"));
  const notes = texts[6] !== undefined ? [texts[6]] : [];
  return {
    id: "voice",
    title,
    summary,
    available: true,
    groups: [{ heading: texts[1] ?? "", fields, notes }],
    actions: button ? [{ label: button.text || button.desc, action: true, disabled: true }] : []
  };
}

function filesCategory(payload: FixturePayload | null, title: string, summary: string): SettingsCategory {
  if (!payload) return emptyCategory("files", title, summary);
  const texts = textsOf(payload);
  return {
    id: "files",
    title,
    summary,
    available: true,
    groups: [{ fields: texts[1] === undefined ? [] : [{ label: texts[1] }], notes: texts[2] === undefined ? [] : [texts[2]] }],
    actions: []
  };
}

const BUILDERS: Record<
  (typeof CATEGORY_ORDER)[number]["id"],
  (payload: FixturePayload | null, title: string, summary: string) => SettingsCategory
> = {
  "api-key": apiKeyCategory,
  provider: providerCategory,
  model: modelCategory,
  search: searchCategory,
  voice: voiceCategory,
  files: filesCategory
};

export function settingsFrom(payloads: PayloadMap): SettingsCategory[] {
  const main = payloads.settings;
  if (!main) return [];
  return CATEGORY_ORDER.flatMap((category) => {
    const meta = entryMeta(main, category.entry);
    if (meta.title === "") return [];
    const sub: SettingsSubKey = category.sub;
    return [BUILDERS[category.id](payloads[sub] ?? null, meta.title, meta.summary)];
  });
}

export function workspaceDataFrom(payloads: PayloadMap, status: WorkspaceStatus = "ready"): WorkspaceData {
  return {
    status,
    home: homeFrom(payloads),
    topics: topicsFrom(payloads),
    recommendations: recommendationsFrom(payloads),
    chat: chatFrom(payloads),
    branches: branchesFor(payloads),
    tree: treeFrom(payloads),
    settings: settingsFrom(payloads)
  };
}
