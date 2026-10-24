/** 逐屏矩阵：在真实 Electron 窗口里加载 Web 产物，14 个状态 × 两个尺寸逐项断言并截图。 */
const { app, BrowserWindow, nativeImage } = require("electron");
const fs = require("node:fs");
const path = require("node:path");
const { buildWindowOptions, APP_TITLE } = require("../window-options.cjs");
const { SCREEN_MATRIX, WINDOW_SIZES } = require("./matrix.cjs");

const INDEX = path.join(__dirname, "..", "app", "index.html");
const OUT = path.join(__dirname, "..", "..", "artifacts", "evidence", "desktop-shell", "2026-10-24");

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function waitFor(win, expression, timeout = 8000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const value = await win.webContents.executeJavaScript(expression);
    if (value) return value;
    await delay(150);
  }
  return null;
}

async function applyState(win, state) {
  await win.webContents.executeJavaScript(`window.location.hash = ${JSON.stringify(state.hash)}; true`);
  const ready = await waitFor(
    win,
    `document.body.innerText.trim().length > 0 && location.hash === ${JSON.stringify(state.hash)}`
  );
  if (ready === null) throw new Error("视图未在超时内渲染");
  if (state.filter !== undefined) {
    const clicked = await win.webContents.executeJavaScript(`(() => {
      const button = document.querySelector('[data-filter="${state.filter}"]');
      if (button === null) return false;
      button.click();
      return true;
    })()`);
    if (!clicked) throw new Error(`学习库筛选 ${state.filter} 不存在`);
    const pressed = await waitFor(
      win,
      `document.querySelector('[data-filter="${state.filter}"]')?.getAttribute("aria-pressed") === "true"`
    );
    if (pressed === null) throw new Error(`学习库筛选 ${state.filter} 未生效`);
  }
  if (state.category === undefined) return;
  const clicked = await win.webContents.executeJavaScript(`(() => {
    const chip = document.querySelector('[data-settings-category="${state.category}"]');
    if (chip === null) return false;
    chip.click();
    return true;
  })()`);
  if (!clicked) throw new Error(`设置分类 ${state.category} 不存在`);
  const detail = await waitFor(
    win,
    `document.querySelector('[data-settings-detail="${state.category}"]') !== null`
  );
  if (detail === null) throw new Error(`设置详情 ${state.category} 未渲染`);
}

app.whenReady().then(async () => {
  const report = { generatedAt: new Date().toISOString(), title: APP_TITLE, sizes: WINDOW_SIZES, states: [] };
  const failures = [];
  const consoleErrors = [];
  let label = "boot";

  const win = new BrowserWindow(buildWindowOptions());
  win.webContents.on("console-message", (...args) => {
    const event = args[0];
    const level = typeof event?.level === "number" ? event.level : args[1];
    const message = typeof event?.message === "string" ? event.message : args[2];
    if (level >= 3) consoleErrors.push({ label, message });
  });

  await win.loadFile(INDEX);
  await delay(600);

  for (const size of WINDOW_SIZES) {
    const folder = `${size.width}x${size.height}`;
    fs.mkdirSync(path.join(OUT, folder), { recursive: true });
    win.setSize(size.width, size.height);

    for (const state of SCREEN_MATRIX) {
      label = `${folder}/${state.id}`;
      const errorsBefore = consoleErrors.length;
      try {
        await applyState(win, state);
        await delay(350);
        const reading = await win.webContents.executeJavaScript(`(() => {
          const root = document.getElementById("root");
          return {
            title: document.title,
            rootChildren: root === null ? -1 : root.children.length,
            textLength: document.body.innerText.trim().length,
            hash: location.hash,
            viewport: [window.innerWidth, window.innerHeight],
            scrollWidth: document.documentElement.scrollWidth,
            detail: document.querySelector("[data-settings-detail]")?.getAttribute("data-settings-detail") ?? null,
            filter: document.querySelector("[data-filter][aria-pressed='true']")?.getAttribute("data-filter") ?? null
          };
        })()`);
        const image = await win.webContents.capturePage();
        const jpeg = image.getSize().width > size.width
          ? image.resize({ width: size.width, quality: "good" })
          : image;
        fs.writeFileSync(path.join(OUT, folder, `${state.id}.jpg`), jpeg.toJPEG(82));

        const checks = {
          title: reading.title === APP_TITLE,
          rendered: reading.rootChildren > 0 && reading.textLength > 0,
          noOverflow: reading.scrollWidth <= reading.viewport[0],
          wideViewport: reading.viewport[0] > 760,
          noConsoleErrors: consoleErrors.length === errorsBefore,
          filter: state.filter === undefined || reading.filter === state.filter,
          category: state.category === undefined || reading.detail === state.category
        };
        const failed = Object.keys(checks).filter((key) => checks[key] === false);
        if (failed.length > 0) failures.push({ label, failed, reading });
        report.states.push({ state: state.id, size, checks, reading });
      } catch (error) {
        failures.push({ label, error: error.message });
      }
    }
  }

  fs.writeFileSync(path.join(OUT, "report.json"), JSON.stringify({ ...report, consoleErrors, failures }, null, 2));
  win.destroy();
  const summary = {
    states: report.states.length,
    expected: SCREEN_MATRIX.length * WINDOW_SIZES.length,
    failures: failures.length,
    consoleErrors: consoleErrors.length
  };
  console.log(`${failures.length === 0 ? "MATRIX_OK" : "MATRIX_FAIL"} ${JSON.stringify(summary)}`);
  if (failures.length > 0) console.log(JSON.stringify(failures.slice(0, 5), null, 2));
  app.exit(failures.length === 0 ? 0 : 1);
});
