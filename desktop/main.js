const { app, BrowserWindow, Menu, dialog, ipcMain, safeStorage } = require("electron");
const fs = require("node:fs");
const path = require("node:path");
const { APP_TITLE, buildWindowOptions } = require("./window-options.cjs");
const { isAllowedNavigation } = require("./navigation.cjs");
const { createLearningService, PERSONA_IDS } = require("./learning-service.cjs");
const { registerDesktopIpc } = require("./desktop-ipc.cjs");
const { closeService } = require("./close-service.cjs");
const { createCloseCoordinator } = require("./close-coordinator.cjs");
const { readSelectedAttachment } = require("./attachment-reader.cjs");

const SMOKE = process.argv.includes("--smoke");
const INDEX = path.join(__dirname, "app", "index.html");
let currentWindow = null;
let service = null;

app.setName(APP_TITLE);
app.setAppUserModelId("com.treelearn.zhiya");

function createWindow() {
  const options = buildWindowOptions();
  if (!SMOKE) options.webPreferences.preload = path.join(__dirname, "preload.cjs");
  const win = new BrowserWindow(options);
  currentWindow = win;
  if (!SMOKE) createCloseCoordinator({ win, ipcMain, entry: INDEX, getService: () => service, clearService: () => { service = null; }, showError: (title, message) => dialog.showErrorBox(title, message) });
  win.on("closed", () => { if (currentWindow === win) currentWindow = null; });

  win.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  win.webContents.on("will-navigate", (event, url) => {
    if (!isAllowedNavigation(url, INDEX)) event.preventDefault();
  });
  win.once("ready-to-show", () => {
    if (!SMOKE) win.show();
  });
  win.loadFile(INDEX);
  return win;
}

async function runSmoke(win) {
  const errors = [];
  win.webContents.on("console-message", (...args) => {
    const event = args[0];
    const level = typeof event?.level === "number" ? event.level : args[1];
    const message = typeof event?.message === "string" ? event.message : args[2];
    if (level >= 3) errors.push(message);
  });
  win.webContents.on("did-fail-load", (_event, code, description, _url, isMainFrame) => {
    if (isMainFrame) errors.push(`did-fail-load ${code} ${description}`);
  });

  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("did-finish-load 超时")), 15000);
    win.webContents.once("did-finish-load", () => {
      clearTimeout(timer);
      resolve();
    });
  });

  const deadline = Date.now() + 10000;
  let reading = null;
  while (Date.now() < deadline) {
    reading = await win.webContents.executeJavaScript(`(() => {
      const root = document.getElementById("root");
      return {
        title: document.title,
        rootChildren: root === null ? -1 : root.children.length,
        textLength: document.body.innerText.trim().length,
        hash: location.hash,
        viewport: [window.innerWidth, window.innerHeight]
      };
    })()`);
    if (reading.rootChildren > 0 && reading.textLength > 0) break;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  const ok = reading !== null
    && reading.rootChildren > 0
    && reading.textLength > 0
    && reading.title.length > 0
    && errors.length === 0;
  console.log(`${ok ? "SMOKE_OK" : "SMOKE_FAIL"} ${JSON.stringify({ ...reading, consoleErrors: errors })}`);
  app.exit(ok ? 0 : 1);
}

function reportMissingBundle() {
  const message = `缺少 ${INDEX}\n先运行：pnpm --dir frontend build`;
  if (SMOKE) {
    console.error(`SMOKE_FAIL ${message}`);
    app.exit(2);
    return;
  }
  dialog.showErrorBox("知芽启动失败", message);
  app.quit();
}

const hasLock = SMOKE || app.requestSingleInstanceLock();

if (!hasLock) {
  app.quit();
} else {
  app.on("second-instance", () => {
    const [win] = BrowserWindow.getAllWindows();
    if (win === undefined) return;
    if (win.isMinimized()) win.restore();
    win.focus();
  });

  app.whenReady().then(() => {
    if (!fs.existsSync(INDEX)) {
      reportMissingBundle();
      return;
    }
    if (!SMOKE) {
      try {
        const resource = JSON.parse(fs.readFileSync(path.join(__dirname, "app", "learning-prompt.json"), "utf8"));
        if (resource.version !== 1 || typeof resource.prompt !== "string" || !resource.prompt.trim()) throw new Error("invalid prompt");
        if (!resource.personas || typeof resource.personas !== "object" || Object.keys(resource.personas).length !== PERSONA_IDS.length || PERSONA_IDS.some(id => typeof resource.personas[id] !== "string" || (id === "default" ? resource.personas[id] !== "" : !resource.personas[id].trim()))) throw new Error("invalid persona policies");
        service = createLearningService({ database: path.join(app.getPath("userData"), "desktop-learning-v1", "learning.sqlite"), secrets: safeStorage, tutorPrompt: resource.prompt, personaPrompts: resource.personas });
        registerDesktopIpc({ ipcMain, service, entry: INDEX, getWindow: () => currentWindow,
          chooseAttachment: win => dialog.showOpenDialog(win, { title: "选择文本资料", properties: ["openFile"], filters: [{ name: "文本资料", extensions: ["txt", "md"] }] }),
          readAttachment: readSelectedAttachment });
      } catch {
        dialog.showErrorBox("知芽启动失败", "本地学习数据无法安全打开。请检查存储空间、访问权限或应用版本；已有数据不会被重置。");
        app.quit();
        return;
      }
    }
    Menu.setApplicationMenu(null);
    const win = createWindow();
    if (SMOKE) {
      runSmoke(win).catch((error) => {
        console.error(`SMOKE_FAIL ${error.message}`);
        app.exit(1);
      });
    }
    app.on("activate", () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on("window-all-closed", () => {
    if (process.platform !== "darwin") app.quit();
  });
  app.on("before-quit", event => {
    if (currentWindow && !currentWindow.isDestroyed() && service) {
      event.preventDefault();
      currentWindow.close();
      return;
    }
    closeService(event, () => service, () => { service = null; }, (title, message) => dialog.showErrorBox(title, message));
  });
}
