/** 桌面壳逐屏验证矩阵：工作区视图、学习库筛选与设置分类都必须在场，缺一屏就不算「整个样式」。 */
const VIEWS = ["home", "library", "settings", "chat", "persona-intro", "forest"];
const LIBRARY_FILTERS = ["all", "in_progress", "done"];
const SETTINGS_CATEGORIES = ["api-key", "provider", "model", "search", "voice", "files"];

const SCREEN_MATRIX = [
  { id: "home", hash: "#/home/0" },
  ...LIBRARY_FILTERS.map((filter) => ({
    id: `library-${filter}`,
    hash: "#/library/0",
    filter
  })),
  { id: "chat-0", hash: "#/chat/0" },
  { id: "chat-1", hash: "#/chat/1" },
  ...SETTINGS_CATEGORIES.map((category) => ({
    id: `settings-${category}`,
    hash: "#/settings/0",
    category
  })),
  { id: "persona-intro", hash: "#/persona-intro/0" },
  { id: "forest", hash: "#/forest/0" }
];

const WINDOW_SIZES = [
  { width: 1180, height: 820 },
  { width: 900, height: 620 }
];

module.exports = { VIEWS, LIBRARY_FILTERS, SETTINGS_CATEGORIES, SCREEN_MATRIX, WINDOW_SIZES };
