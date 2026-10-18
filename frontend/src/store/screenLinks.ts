import type { ScreenName } from "../platform/RuntimePort";

/**
 * 「点这条目去哪一屏」的映射，正文只在这里一份。
 *
 * 键用 Android 侧的 resource-id 而不是屏幕标题：标题是用户可见文案，会随措辞改动，
 * 而 resource-id 是两端共同的、被夹具与 DOM 同时带着的身份（`data-node` 与 fixture `id`）。
 * 值用采集夹具的条目名（与 `android_routes.json` 的键一致），换屏即读那一屏的夹具。
 */
const SCREEN_LINKS: Record<string, ScreenName> = {
  home_topic_card: "chat",
  home_drawer_button: "homedrawer",
  home_settings_button: "settings",
  /** Android 上整枚芯片把那句问题当根问题送进对话（PRD 6.2.17 第 4 点）；本阶段先落到那一屏的快照。 */
  rec_chip: "chat",
  library_topic: "chat",
  chat_nav: "navigation",
  chat_overview: "treecanvas",
  chat_settings: "settings",
  settings_key_entry: "settingskey",
  settings_providers_entry: "provideradmin",
  settings_models_entry: "modelsettings",
  settings_search_entry: "searchservicesettings",
  settings_voice_entry: "voicesettings",
  settings_files_entry: "filemanager"
};

export type ScreenLink =
  | { kind: "screen"; screen: ScreenName }
  | { kind: "tab"; screen: ScreenName }
  | { kind: "back" }
  | null;

/** 映射表的全部键，供守门与测试逐条点名「这条入口在夹具里是不是死的」，不依赖对象插入顺序。 */
export function screenLinkKeys(): string[] {
  return Object.keys(SCREEN_LINKS).sort();
}

export function linkFor(nodeId: string): ScreenLink {
  if (nodeId === "tab_home") return { kind: "tab", screen: "home" };
  if (nodeId === "tab_library") return { kind: "tab", screen: "library" };
  if (nodeId.endsWith("_back")) return { kind: "back" };
  const screen = SCREEN_LINKS[nodeId];
  return screen === undefined ? null : { kind: "screen", screen };
}
