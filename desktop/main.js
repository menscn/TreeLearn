const { app, BrowserWindow, Menu, dialog } = require("electron");
const fs = require("node:fs");
const path = require("node:path");
const { APP_TITLE, buildWindowOptions } = require("./window-options.cjs");
const { isAllowedNavigation } = require("./navigation.cjs");

const SMOKE = process.argv.includes("--smoke");
const INDEX = path.join(__dirname, "app", "index.html");

app.setName(APP_TITLE);
app.setAppUserModelId("com.treelearn.zhiya");

function createWindow() {
  const win = new BrowserWindow(buildWindowOptions());

  win.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  win.webContents.on("will-navigate", (event, url) => {
    if (!isAllowedNavigation(url)) event.preventDefault();
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
}
