/* Runs against the built offline app in a real Electron renderer. */
const path = require("node:path");
const fs = require("node:fs");

if (!process.versions.electron) {
  const { spawnSync } = require("node:child_process");
  const electron = require("../../../desktop/node_modules/electron");
  const child = spawnSync(electron, [__filename, ...process.argv.slice(2)], { stdio: "inherit", env: process.env });
  process.exit(child.status ?? 1);
}

const { app, BrowserWindow } = require("electron");
const { buildWindowOptions } = require("../../../desktop/window-options.cjs");
const offline = path.resolve(__dirname, "../../dist-offline/index.html");
const output = process.env.TREELEARN_CONTRAST_EVIDENCE;
const noScroll = process.argv.includes("--selftest-no-scroll");
const noHome = process.argv.includes("--selftest-no-home");
const noTyping = process.argv.includes("--selftest-no-typing");
const corruptDraft = process.argv.includes("--selftest-corrupt-draft");
const draftText = "abcdefghijklmnopqrstuvwxyz0123456789".repeat(9).slice(0, 300);
const failures = [];
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function until(win, script) {
  for (let i = 0; i < 80; i += 1) {
    if (await win.webContents.executeJavaScript(script)) return;
    await wait(100);
  }
  throw new Error(`Element did not become ready: ${script}`);
}

async function screenshot(win, name) {
  const bytes = (await win.webContents.capturePage()).toPNG();
  if (output) {
    fs.mkdirSync(output, { recursive: true });
    fs.writeFileSync(path.join(output, `${name}.png`), bytes);
  }
  return bytes;
}

async function click(win, selector) {
  const point = await win.webContents.executeJavaScript(`(() => {
    const e = document.querySelector(${JSON.stringify(selector)});
    if (!e) return null;
    const r = e.getBoundingClientRect();
    return { x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2) };
  })()`);
  if (!point) throw new Error(`Missing interactive element: ${selector}`);
  win.webContents.sendInputEvent({ type: "mouseDown", ...point, button: "left", clickCount: 1 });
  win.webContents.sendInputEvent({ type: "mouseUp", ...point, button: "left", clickCount: 1 });
}

function key(win, keyCode) {
  win.webContents.sendInputEvent({ type: "keyDown", keyCode });
  if (keyCode === "Return") win.webContents.sendInputEvent({ type: "char", keyCode: "\r" });
  win.webContents.sendInputEvent({ type: "keyUp", keyCode });
}

async function checkInteractions(win, label) {
  await win.webContents.executeJavaScript(`(async () => {
    document.querySelector('.chat-scroll').scrollTop = 0;
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  })()`);
  win.webContents.focus();
  await click(win, ".chat-dock textarea");
  win.webContents.sendInputEvent({ type: "keyDown", keyCode: "A", modifiers: ["control"] });
  win.webContents.sendInputEvent({ type: "keyUp", keyCode: "A", modifiers: ["control"] });
  key(win, "Backspace");
  const cleared = await win.webContents.executeJavaScript("document.querySelector('.chat-dock textarea').value");
  if (cleared !== "") failures.push(`${label}: test draft was not cleared`);
  if (!noTyping) {
    for (const character of draftText) win.webContents.sendInputEvent({ type: "char", keyCode: character });
  }
  const draft = await win.webContents.executeJavaScript("document.querySelector('.chat-dock textarea').value");
  if (draft !== draftText) failures.push(`${label}: keyboard draft was not preserved`);
  await screenshot(win, `chat-${label}-long-draft`);
  key(win, "Tab");
  const focus = await win.webContents.executeJavaScript("document.activeElement?.className");
  console.log(`COMPOSER_INPUT_STEP ${JSON.stringify({ window: label, draftLength: draft.length, tabTarget: focus })}`);
  if (!String(focus).includes("persona-chip")) failures.push(`${label}: Tab did not reach persona control: ${focus}`);
  key(win, "Return");
  await until(win, "Boolean(document.querySelector('[data-persona-popover]'))");
  const popover = await win.webContents.executeJavaScript(`(() => {
    const r = document.querySelector('[data-persona-popover]').getBoundingClientRect();
    return { visible: r.width > 0 && r.height > 0, left: r.left, top: r.top, right: r.right, bottom: r.bottom,
      width: innerWidth, height: innerHeight };
  })()`);
  if (!popover.visible || popover.left < 0 || popover.top < 0 || popover.right > popover.width || popover.bottom > popover.height) {
    failures.push(`${label}: persona popover is clipped: ${JSON.stringify(popover)}`);
  }
  await screenshot(win, `chat-${label}-persona`);
  key(win, "Escape");
  await until(win, "!document.querySelector('[data-persona-popover]')");
  const selection = await win.webContents.executeJavaScript(`(() => {
    const p = document.querySelector('.chat-answer p');
    const node = p && p.firstChild;
    if (!node || node.nodeType !== Node.TEXT_NODE || node.length < 8) return null;
    const range = document.createRange();
    range.setStart(node, 0);
    range.setEnd(node, 8);
    const r = range.getBoundingClientRect();
    return { x1: Math.round(r.left + 2), x2: Math.round(r.right - 2), y: Math.round(r.top + r.height / 2) };
  })()`);
  if (!selection) throw new Error(`${label}: no visible answer text to select`);
  win.webContents.sendInputEvent({ type: "mouseDown", x: selection.x1, y: selection.y, button: "left", clickCount: 1 });
  win.webContents.sendInputEvent({ type: "mouseMove", x: selection.x2, y: selection.y, button: "left", modifiers: ["leftButtonDown"] });
  win.webContents.sendInputEvent({ type: "mouseUp", x: selection.x2, y: selection.y, button: "left", clickCount: 1 });
  await until(win, "Boolean(document.querySelector('[data-word-action=ask_ai]'))");
  const selected = await win.webContents.executeJavaScript("window.getSelection().toString()");
  if (!selected.trim()) failures.push(`${label}: mouse drag did not select answer text`);
  await screenshot(win, `chat-${label}-selection`);
  await click(win, "[data-word-action=ask_ai]");
  await until(win, "Boolean(document.querySelector('[data-quote]'))");
  if (corruptDraft) {
    await win.webContents.executeJavaScript(`(() => {
      const input = document.querySelector('.chat-dock textarea');
      input.value = 'z' + input.value.slice(1);
    })()`);
  }
  const quote = await win.webContents.executeJavaScript(`(() => {
    const composer = document.querySelector('.chat-dock .composer');
    const quote = document.querySelector('[data-quote]');
    const input = document.querySelector('.chat-dock textarea');
    const r = quote.getBoundingClientRect();
    return { text: quote.innerText, visible: r.width > 0 && r.height > 0 && r.top >= 0 && r.bottom <= innerHeight,
      draftLength: input.value.length, draftUnchanged: input.value === ${JSON.stringify(draft)},
      focused: document.activeElement === input,
      border: getComputedStyle(composer).borderTopWidth };
  })()`);
  if (!quote.visible || !quote.text.includes(selected) || quote.draftLength !== 300 || !quote.draftUnchanged || !quote.focused || quote.border !== "1px") {
    failures.push(`${label}: quote or draft interaction failed: ${JSON.stringify(quote)}`);
  }
  await screenshot(win, `chat-${label}-quote`);
  console.log(`COMPOSER_INTERACTION ${JSON.stringify({ window: label, draftLength: draft.length, tabTarget: focus,
    popover, selectedLength: selected.length, quote })}`);
}

async function checkWindow(win, width, height) {
  win.setSize(width, height);
  await win.loadFile(offline, { hash: "/chat/0" });
  await until(win, "Boolean(document.querySelector('.chat-dock .composer textarea'))");
  await win.webContents.executeJavaScript(`(async () => {
    document.activeElement.blur();
    document.querySelector('.chat-scroll').scrollTop = 0;
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  })()`);
  const read = () => win.webContents.executeJavaScript(`(() => {
    const c = document.querySelector('.chat-dock .composer');
    const s = getComputedStyle(c);
    return { width: s.borderTopWidth, style: s.borderTopStyle, color: s.borderTopColor,
      shadow: s.boxShadow, viewport: [innerWidth, innerHeight],
      overflow: document.documentElement.scrollWidth > innerWidth };
  })()`);
  const unfocused = await read();
  await screenshot(win, `chat-${width}x${height}-unfocused`);
  await win.webContents.executeJavaScript("document.querySelector('.chat-dock textarea').focus()");
  const focused = await read();
  const focusedImage = await screenshot(win, `chat-${width}x${height}-focused`);
  const scroll = await win.webContents.executeJavaScript(`(async () => {
    const element = document.querySelector('.chat-scroll');
    const before = element.scrollTop;
    const max = element.scrollHeight - element.clientHeight;
    if (!${noScroll}) element.scrollTop = max;
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    return { before, after: element.scrollTop, max };
  })()`);
  const scrolledImage = await screenshot(win, `chat-${width}x${height}-scrolled`);
  const distinctImages = !focusedImage.equals(scrolledImage);
  if (scroll.max <= 0 || scroll.after <= scroll.before) failures.push(`${width}x${height}: scroll did not move: ${JSON.stringify(scroll)}`);
  if (!distinctImages) failures.push(`${width}x${height}: scroll screenshot did not change`);
  if (Number.parseFloat(unfocused.width) < 1 || unfocused.style !== "solid") failures.push(`${width}x${height}: chat outline missing: ${JSON.stringify(unfocused)}`);
  if (focused.width !== unfocused.width || focused.style !== unfocused.style || focused.shadow === unfocused.shadow) failures.push(`${width}x${height}: focus outline or focus indicator changed unexpectedly`);
  if (unfocused.overflow || focused.overflow) failures.push(`${width}x${height}: horizontal overflow`);
  await checkInteractions(win, `${width}x${height}`);
  if (!noHome) {
    await win.loadFile(offline, { hash: "/home/0" });
    await until(win, "Boolean(document.querySelector('.home-dock .composer'))");
  }
  const home = await win.webContents.executeJavaScript(`(async () => {
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    const composer = document.querySelector('.home-dock .composer');
    if (!composer) return { visible: false, border: null };
    const rect = composer.getBoundingClientRect();
    const style = getComputedStyle(composer);
    return { visible: rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.right > 0
        && rect.top < innerHeight && rect.left < innerWidth && style.visibility === 'visible'
        && style.display !== 'none', border: style.borderTopWidth };
  })()`);
  const homeImage = await screenshot(win, `home-${width}x${height}`);
  const distinctHome = !homeImage.equals(scrolledImage);
  if (!home.visible) failures.push(`${width}x${height}: home composer is not visible`);
  if (!distinctHome) failures.push(`${width}x${height}: home screenshot matches chat`);
  if (home.border === null || Number.parseFloat(home.border) !== 0) failures.push(`${width}x${height}: home outline unexpectedly changed: ${home.border}`);
  console.log(`COMPOSER_READING ${JSON.stringify({ window: [width, height], unfocused, focused, scroll, distinctImages, home, distinctHome })}`);
}

app.whenReady().then(async () => {
  const options = buildWindowOptions();
  options.webPreferences.backgroundThrottling = false;
  const win = new BrowserWindow(options);
  try {
    if (!fs.existsSync(offline)) throw new Error("Build frontend/dist-offline/index.html first");
    for (const [width, height] of [[1180, 820], [900, 620]]) await checkWindow(win, width, height);
  } catch (error) {
    failures.push(error.stack ?? String(error));
  } finally {
    win.destroy();
  }
  if (failures.length) console.error(`COMPOSER_CONTRAST_FAIL ${JSON.stringify(failures)}`);
  else console.log("COMPOSER_CONTRAST_OK");
  app.exit(failures.length ? 1 : 0);
});
