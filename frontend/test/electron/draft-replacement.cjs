/* Real offline frontend in Electron; only disposable synthetic drafts are used. */
const path = require("node:path");
const fs = require("node:fs");
const os = require("node:os");
if (!process.versions.electron) {
  const { spawnSync } = require("node:child_process");
  const electron = require("../../../desktop/node_modules/electron");
  const child = spawnSync(electron, [__filename], { stdio: "inherit" });
  process.exit(child.status ?? 1);
}

const { app, BrowserWindow } = require("electron");
const { buildWindowOptions } = require("../../../desktop/window-options.cjs");
const offline = path.resolve(__dirname, "../../dist-offline/index.html");
app.setPath("userData", fs.mkdtempSync(path.join(os.tmpdir(), "treelearn-draft-test-")));
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function until(win, predicate) {
  for (let i = 0; i < 100; i += 1) {
    if (await win.webContents.executeJavaScript(predicate)) return;
    await pause(50);
  }
  throw new Error(`Timeout waiting for ${predicate}`);
}

async function click(win, selector) {
  const point = await win.webContents.executeJavaScript(`(async () => {
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) throw new Error('missing target');
    el.scrollIntoView({block:'center', inline:'nearest'});
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    const r = el.getBoundingClientRect();
    return {x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2)};
  })()`);
  win.webContents.sendInputEvent({ type: "mouseDown", ...point, button: "left", clickCount: 1 });
  win.webContents.sendInputEvent({ type: "mouseUp", ...point, button: "left", clickCount: 1 });
}

async function typeDraft(win, text) {
  await click(win, "#chat-composer-input");
  win.webContents.sendInputEvent({ type: "keyDown", keyCode: "A", modifiers: ["control"] });
  win.webContents.sendInputEvent({ type: "keyUp", keyCode: "A", modifiers: ["control"] });
  for (const keyCode of text) win.webContents.sendInputEvent({ type: "char", keyCode });
  await until(win, `document.querySelector('#chat-composer-input')?.value === ${JSON.stringify(text)}`);
}

async function confirmAction(win, selector, accept) {
  const dialog = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("No real confirm dialog")), 5000);
    const listener = async (_event, method, params) => {
      if (method !== "Page.javascriptDialogOpening") return;
      clearTimeout(timer);
      win.webContents.debugger.removeListener("message", listener);
      try {
        if (params.type !== "confirm" || !params.message.includes("已有未发送的草稿")) {
          throw new Error(`Unexpected dialog: ${params.type} ${params.message}`);
        }
        await win.webContents.debugger.sendCommand("Page.handleJavaScriptDialog", { accept });
        resolve();
      } catch (error) { reject(error); }
    };
    win.webContents.debugger.on("message", listener);
  });
  await click(win, selector);
  await dialog;
}

async function read(win) {
  return win.webContents.executeJavaScript(`(() => ({
    view: document.querySelector('[data-screen="home"]') ? 'home' : 'chat',
    draft: document.querySelector('#chat-composer-input')?.value ?? '',
    focused: document.activeElement?.getAttribute('data-suggestion') ??
      document.activeElement?.getAttribute('data-ask-chip') ??
      document.activeElement?.getAttribute('data-recommendation') ?? document.activeElement?.id,
    quote: document.querySelector('[data-quote]')?.textContent ?? ''
  }))()`);
}

function check(condition, label, value) {
  console.log(`${condition ? "PASS" : "FAIL"} ${label} ${JSON.stringify(value)}`);
  if (!condition) throw new Error(label);
}

app.whenReady().then(async () => {
  const options = buildWindowOptions();
  options.webPreferences.backgroundThrottling = false;
  const win = new BrowserWindow(options);
  try {
    await win.loadFile(offline, { hash: "/chat/0" });
    await until(win, "Boolean(document.querySelector('[data-suggestion]'))");
    win.webContents.debugger.attach("1.3");
    await win.webContents.debugger.sendCommand("Page.enable");
    win.webContents.focus();
    await typeDraft(win, "original-draft");
    await confirmAction(win, '[data-suggestion="0"]', false);
    let state = await read(win);
    check(state.draft === "original-draft" && state.focused === "0", "suggestion-cancel", state);
    await confirmAction(win, '[data-suggestion="1"]', true);
    state = await read(win);
    check(state.draft !== "original-draft" && state.draft.length > 0 && state.focused === "chat-composer-input", "suggestion-confirm", state);

    await typeDraft(win, "quote-draft");
    const selection = await win.webContents.executeJavaScript(`(async () => {
      document.querySelector('.chat-scroll').scrollTop = 0;
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      const node = document.querySelector('.chat-answer p').firstChild;
      const range = document.createRange(); range.setStart(node, 0); range.setEnd(node, 8);
      const r = range.getBoundingClientRect();
      return {x1: Math.round(r.left + 2), x2: Math.round(r.right - 2), y: Math.round(r.top + r.height / 2)};
    })()`);
    win.webContents.sendInputEvent({ type: "mouseDown", x: selection.x1, y: selection.y, button: "left", clickCount: 1 });
    win.webContents.sendInputEvent({ type: "mouseMove", x: selection.x2, y: selection.y, button: "left", modifiers: ["leftButtonDown"] });
    win.webContents.sendInputEvent({ type: "mouseUp", x: selection.x2, y: selection.y, button: "left", clickCount: 1 });
    await until(win, "Boolean(document.querySelector('[data-word-action=ask_ai]'))");
    await click(win, '[data-word-action="ask_ai"]');
    await until(win, "Boolean(document.querySelector('[data-ask-chip=why]'))");
    await confirmAction(win, '[data-ask-chip="why"]', false);
    state = await read(win);
    check(state.draft === "quote-draft" && state.focused === "why" && state.quote.length > 0, "quote-cancel", state);
    await confirmAction(win, '[data-ask-chip="how"]', true);
    state = await read(win);
    check(state.draft !== "quote-draft" && state.draft.length > 0 && state.focused === "chat-composer-input" && state.quote.length > 0, "quote-confirm", state);

    await typeDraft(win, "home-target-draft");
    await click(win, '[data-nav-view="home"]');
    await until(win, "Boolean(document.querySelector('[data-recommendation]'))");
    await confirmAction(win, '[data-recommendation="0"]', false);
    state = await read(win);
    check(state.view === "home" && state.focused === "0", "home-cancel", state);
    await click(win, '[data-recent-topic="0"]');
    await until(win, "Boolean(document.querySelector('#chat-composer-input'))");
    state = await read(win);
    check(state.draft === "home-target-draft", "home-cancel-preserves-target", state);
    await click(win, '[data-nav-view="home"]');
    await until(win, "Boolean(document.querySelector('[data-recommendation]'))");
    await confirmAction(win, '[data-recommendation="1"]', true);
    await until(win, "Boolean(document.querySelector('#chat-composer-input'))");
    state = await read(win);
    check(state.view === "chat" && state.draft !== "home-target-draft" && state.draft.length > 0, "home-confirm", state);
    console.log("DRAFT_WINDOW_OK");
    win.destroy();
    app.exit(0);
  } catch (error) {
    console.error(`DRAFT_WINDOW_FAIL ${error.stack ?? error}`);
    win.destroy();
    app.exit(1);
  }
});
