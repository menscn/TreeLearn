/** 桌面窗口的唯一参数来源：测试与主进程读同一份，避免「测试绿但窗口没按测试建」。 */
const APP_TITLE = "知芽";
const BACKGROUND = "#F4F1EB";
const DEFAULTS = {
  width: 1180,
  height: 820,
  minSize: { width: 900, height: 620 }
};

function buildWindowOptions(overrides = {}) {
  return {
    width: DEFAULTS.width,
    height: DEFAULTS.height,
    minWidth: DEFAULTS.minSize.width,
    minHeight: DEFAULTS.minSize.height,
    show: false,
    title: APP_TITLE,
    backgroundColor: BACKGROUND,
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false
    },
    ...overrides
  };
}

module.exports = { APP_TITLE, BACKGROUND, DEFAULTS, buildWindowOptions };
