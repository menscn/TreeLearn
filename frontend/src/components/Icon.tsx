import type { ReactNode } from "react";
import type { FixtureNode } from "../platform/fixtureNodes";

/**
 * Android 矢量 drawable 的 Web 副本。
 *
 * 每条 pathData 取自 `android/app/src/main/res/drawable/ic_*.xml`，节点与 drawable 的对应关系
 * 取自 layout 里的 `android:src` / `android:drawableStart`；不按形状"看起来像"另画一套。
 * 本文件只承载字形，可见文案与可访问名留在夹具节点和宿主控件上。
 */

export type GlyphName =
  | "nav_menu"
  | "sidebar"
  | "chat_gear"
  | "nav_back"
  | "nav_tree"
  | "nav_forest"
  | "forest_pin"
  | "chat_overview"
  | "chat_attach"
  | "chat_send"
  | "action_copy"
  | "action_fork"
  | "action_highlight"
  | "action_note"
  | "action_select_all"
  | "nav_restore"
  | "nav_more"
  | "chevron_right"
  | "chevron_down"
  | "search"
  | "tab_home"
  | "tab_library"
  | "brand_sprout"
  | "sparkle"
  | "nav_chat"
  | "chat_close"
  | "check"
  | "zoom_in"
  | "zoom_out"
  | "tree_fit"
  | "tree_center"
  | "think";

type IconNode = Pick<FixtureNode, "id" | "class" | "text" | "desc" | "bounds_dp">;

const BY_ID: Readonly<Record<string, GlyphName>> = {
  home_drawer_button: "nav_menu",
  home_settings_button: "chat_gear",
  home_brand_mark: "brand_sprout",
  home_recommendations_sparkle: "sparkle",
  home_topic_tree: "nav_tree",
  home_send: "chat_send",
  model_chip_effort: "think",
  model_chip_chevron: "chevron_down",
  tab_home_icon: "tab_home",
  tab_library_icon: "tab_library",
  chat_back: "nav_back",
  chat_nav: "nav_tree",
  chat_overview: "chat_overview",
  chat_settings: "chat_gear",
  chat_attach: "chat_attach",
  chat_send: "chat_send",
  act_copy: "action_copy",
  act_reset: "nav_restore",
  act_fork: "action_fork",
  suggestion_fork: "action_fork",
  library_back: "nav_back",
  navigation_enter: "nav_chat",
  navigation_canvas: "nav_tree",
  navigation_row_more: "nav_more",
  navigation_row_restore: "nav_restore",
  settings_key_save: "check",
  provider_key_save: "check",
  tree_nav: "nav_tree",
  tree_zoom_in: "zoom_in",
  tree_zoom_out: "zoom_out",
  tree_fit: "tree_fit",
  tree_fit_tool: "tree_fit",
  tree_center: "tree_center"
};

/** 转义写中文：这里只比较夹具正文，不在生产代码里落一份可见文案。 */
const BACK = "\u8FD4\u56DE";
const CLOSE = "\u5173\u95ED";
const SAVE = "\u4FDD\u5B58";
const ZOOM_IN = "\u653E\u5927";
const ZOOM_OUT = "\u7F29\u5C0F";
const ZOOM_FIT = "\u7F29\u653E";
const CENTER = "\u56DE\u5230";
const BRANCH = "\u6253\u5F00\u5206\u652F";
const ENTER_CHAT = "\u8FDB\u5165\u5BF9\u8BDD";
const VIEW_CANVAS = "\u67E5\u770B\u753B\u5E03";
const MORE = "\u66F4\u591A";
const LOGO = "Logo";

export function isIconNode(node: Pick<FixtureNode, "class" | "text">): boolean {
  const cls = node.class;
  return cls.endsWith("ImageView") || cls.endsWith("ImageButton")
    || (cls.endsWith("Button") && node.text === "");
}

/** 夹具节点 → Android drawable；映射不到的图标节点返回 null，由判据转红而不是退回文字。 */
export function iconFor(node: IconNode): GlyphName | null {
  const byId = BY_ID[node.id];
  if (byId !== undefined) return byId;
  if (!isIconNode(node)) return null;
  const desc = node.desc;
  if (desc.startsWith(BACK)) return "nav_back";
  if (desc.startsWith(CLOSE)) return "chat_close";
  if (desc === SAVE) return "check";
  if (desc.startsWith(ZOOM_IN)) return "zoom_in";
  if (desc.startsWith(ZOOM_OUT)) return "zoom_out";
  if (desc.startsWith(ZOOM_FIT)) return "tree_fit";
  if (desc.startsWith(CENTER)) return "tree_center";
  if (desc.startsWith(BRANCH)) return "nav_tree";
  if (desc.startsWith(ENTER_CHAT)) return "nav_chat";
  if (desc.startsWith(VIEW_CANVAS)) return "nav_tree";
  if (desc.includes(MORE)) return "nav_more";
  if (desc.includes(LOGO)) return "brand_sprout";
  const [left = 0, , width = 0] = node.bounds_dp;
  if (left > 260 && width > 0 && width <= 44 && desc !== "") return "chevron_right";
  return null;
}

const STROKE = {
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round"
} as const;

const GLYPHS: Record<GlyphName, ReactNode> = {
  nav_menu: <g {...STROKE}><path d="M4 7h16M4 12h16M4 17h16" /></g>,
  sidebar: (
    <g {...STROKE}>
      <rect x="3.5" y="4.5" width="17" height="15" rx="2.5" />
      <path d="M9.5 4.5v15" />
    </g>
  ),
  chat_gear: (
    <g {...STROKE}>
      <path d="M12 12m-3 0a3 3 0 1 1 6 0a3 3 0 1 1-6 0" />
      <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5 5l2.1 2.1M16.9 16.9 19 19M19 5l-2.1 2.1M7.1 16.9 5 19" />
    </g>
  ),
  nav_back: <g {...STROKE}><path d="M15 5l-7 7 7 7" /></g>,
  nav_tree: (
    <g {...STROKE}>
      <path d="M5 12m-2.1 0a2.1 2.1 0 1 1 4.2 0a2.1 2.1 0 1 1-4.2 0" />
      <path d="M17.5 6m-2.1 0a2.1 2.1 0 1 1 4.2 0a2.1 2.1 0 1 1-4.2 0" />
      <path d="M17.5 18m-2.1 0a2.1 2.1 0 1 1 4.2 0a2.1 2.1 0 1 1-4.2 0" />
      <path d="M7 11.2 15.5 6.8M7 12.8l8.5 4.4" />
    </g>
  ),
  nav_forest: (
    <g {...STROKE}>
      <g strokeWidth={1.5}>
        <path d="M12 3l3.2 5.2H8.8L12 3z" />
        <path d="M12 7.3l4.4 6.2H7.6L12 7.3z" />
        <path d="M12 21v-6.2" />
      </g>
      <g strokeWidth={1.4}>
        <path d="M5 9.2l2.4 4H2.6L5 9.2z" />
        <path d="M5 21v-4.2" />
        <path d="M19 9.2l2.4 4h-4.8L19 9.2z" />
        <path d="M19 21v-4.2" />
      </g>
    </g>
  ),
  forest_pin: (
    <g {...STROKE}>
      <path d="M12 21s-7-6.2-7-11a7 7 0 1 1 14 0c0 4.8-7 11-7 11z" />
      <path d="M12 12.2a2.2 2.2 0 1 0 0-4.4 2.2 2.2 0 0 0 0 4.4z" />
    </g>
  ),
  chat_overview: (
    <g {...STROKE}>
      <path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2z" />
      <path d="M9 4v14M15 6v14" />
    </g>
  ),
  chat_attach: (
    <g {...STROKE}>
      <path d="M20.5 11.5 11 21a5 5 0 0 1-7.1-7.1L13.8 4a3.5 3.5 0 0 1 5 5L9.7 18.1a2 2 0 0 1-2.8-2.8l8.5-8.5" />
    </g>
  ),
  chat_send: <g {...STROKE}><path d="M12 19V5M5.5 11.5 12 5l6.5 6.5" /></g>,
  action_copy: (
    <g {...STROKE}>
      <path d="M11 9h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z" />
      <path d="M6 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v1" />
    </g>
  ),
  action_fork: (
    <g {...STROKE}>
      <path d="M6 6m-2.1 0a2.1 2.1 0 1 1 4.2 0a2.1 2.1 0 1 1-4.2 0" />
      <path d="M6 18m-2.1 0a2.1 2.1 0 1 1 4.2 0a2.1 2.1 0 1 1-4.2 0" />
      <path d="M18 8m-2.1 0a2.1 2.1 0 1 1 4.2 0a2.1 2.1 0 1 1-4.2 0" />
      <path d="M6 8.1v7.8M6 13c0-2.4 2.6-3.4 5.4-3.4h4.4" />
    </g>
  ),
  action_highlight: (
    <g {...STROKE}>
      <path d="M14.06 4.94a2.2 2.2 0 0 1 3.11 3.11L10.4 14.82 7.29 11.71Z" />
      <path d="M7.29 11.71 4.6 14.4l3.11 3.11 2.69-2.69" />
      <path d="M4 20.5h13" />
    </g>
  ),
  action_note: (
    <g {...STROKE}>
      <path d="M5 4.5h14v9.5l-5.5 5.5H5Z" />
      <path d="M13.5 19.5V14H19" />
    </g>
  ),
  action_select_all: (
    <g {...STROKE}>
      <path d="M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z" />
      <path d="M8 12.2 10.8 15 16.4 9.4" />
    </g>
  ),
  nav_restore: (
    <g {...STROKE}>
      <path d="M20 12a8 8 0 1 1-2.3-5.7" />
      <path d="M20 3.5V8h-4.5" />
    </g>
  ),
  nav_more: (
    <g fill="currentColor" stroke="none">
      <path d="M5 12m-1 0a1 1 0 1 1 2 0a1 1 0 1 1-2 0" />
      <path d="M12 12m-1 0a1 1 0 1 1 2 0a1 1 0 1 1-2 0" />
      <path d="M19 12m-1 0a1 1 0 1 1 2 0a1 1 0 1 1-2 0" />
    </g>
  ),
  chevron_right: <g {...STROKE}><path d="M9 6l6 6-6 6" /></g>,
  chevron_down: <g {...STROKE}><path d="M6 9l6 6 6-6" /></g>,
  search: <g {...STROKE}><path d="M10.8 4.2A6.6 6.6 0 1 0 10.8 17.4A6.6 6.6 0 1 0 10.8 4.2M15.7 15.7 20.2 20.2" /></g>,
  tab_home: <g {...STROKE}><path d="M3.6 10.2 12 3.6 20.4 10.2V19.8H3.6ZM9.4 19.8V14.2h5.2v5.6" /></g>,
  tab_library: (
    <g {...STROKE}>
      <path d="M12 6.6C10.2 5 7.2 4.6 4.6 5.4v13c2.6-.8 5.6-.4 7.4 1.2M12 6.6c1.8-1.6 4.8-2 7.4-1.2v13c-2.6-.8-5.6-.4-7.4 1.2M12 6.6v13" />
    </g>
  ),
  brand_sprout: (
    <g stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.7}>
      <path d="M12 21.5V13" fill="none" />
      <path d="M12 13C12 9.2 9.2 6.5 4.8 6.5C4.8 10.3 7.6 13 12 13Z" fill="currentColor" fillOpacity={0.18} />
      <path d="M12 11C12 6.8 15 4 19.5 4C19.5 8.2 16.5 11 12 11Z" fill="currentColor" fillOpacity={0.3} />
    </g>
  ),
  sparkle: (
    <g {...STROKE}>
      <path d="M12 3.5 13.8 9 19.5 10.8 13.8 12.6 12 18.5 10.2 12.6 4.5 10.8 10.2 9z" />
      <path d="M18.5 3.5v3M20 5h-3" />
    </g>
  ),
  nav_chat: (
    <g {...STROKE}>
      <path d="M20 12.5c0 3.6-3.6 6.5-8 6.5-1 0-2-.15-2.9-.43L4.5 20l1.1-3.3A6.6 6.6 0 0 1 4 12.5C4 8.9 7.6 6 12 6s8 2.9 8 6.5z" />
    </g>
  ),
  chat_close: <g {...STROKE}><path d="M6 6l12 12M18 6 6 18" /></g>,
  check: <g {...STROKE}><path d="M5 12.5l4.5 4.5L19 7.5" strokeWidth={2.4} /></g>,
  zoom_in: (
    <g {...STROKE}>
      <path d="M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14M16.2 16.2 21 21M11 8v6M8 11h6" />
    </g>
  ),
  zoom_out: (
    <g {...STROKE}>
      <path d="M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14M16.2 16.2 21 21M8 11h6" />
    </g>
  ),
  tree_fit: (
    <g {...STROKE}>
      <path d="M9 4H6a2 2 0 0 0-2 2v3M15 4h3a2 2 0 0 1 2 2v3M20 15v3a2 2 0 0 1-2 2h-3M4 15v3a2 2 0 0 0 2 2h3" />
    </g>
  ),
  tree_center: (
    <g {...STROKE}>
      <path d="M12 4v4M12 16v4M4 12h4M16 12h4M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6" />
    </g>
  ),
  think: (
    <g {...STROKE}>
      <path d="M12 4.5a5 5 0 0 0-3 9v2.5h6V13.5a5 5 0 0 0-3-9z" />
      <path d="M10 19h4" />
    </g>
  )
};

export function Icon({ name, className = "icon" }: { name: GlyphName; className?: string }) {
  return (
    <svg aria-hidden="true" className={className} focusable="false" viewBox="0 0 24 24">
      {GLYPHS[name]}
    </svg>
  );
}
