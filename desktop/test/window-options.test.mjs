import test from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { buildWindowOptions, DEFAULTS, APP_TITLE, BACKGROUND } = require("../window-options.cjs");

test("默认窗口 1180×820，最小 900×620", () => {
  assert.equal(DEFAULTS.width, 1180);
  assert.equal(DEFAULTS.height, 820);
  assert.deepEqual(DEFAULTS.minSize, { width: 900, height: 620 });
  const options = buildWindowOptions();
  assert.deepEqual([options.width, options.height], [1180, 820]);
  assert.deepEqual([options.minWidth, options.minHeight], [900, 620]);
});

test("使用标准 Windows 标题栏，不移除系统框架", () => {
  const options = buildWindowOptions();
  assert.notEqual(options.frame, false);
  assert.equal(options.titleBarStyle, undefined);
  assert.equal(options.autoHideMenuBar, true);
  assert.equal(options.title, APP_TITLE);
  assert.equal(APP_TITLE, "知芽");
});

test("窗口初始隐藏，底色与 Web 一致", () => {
  const options = buildWindowOptions();
  assert.equal(options.show, false);
  assert.equal(options.backgroundColor, BACKGROUND);
  assert.equal(BACKGROUND, "#F4F1EB");
});

test("渲染进程隔离，不开放 Node 集成", () => {
  const options = buildWindowOptions();
  assert.equal(options.webPreferences.contextIsolation, true);
  assert.equal(options.webPreferences.nodeIntegration, false);
  assert.equal(options.webPreferences.sandbox, true);
});

test("调用方可以覆盖显示时机而不改其它约束", () => {
  const options = buildWindowOptions({ show: true });
  assert.equal(options.show, true);
  assert.equal(options.width, 1180);
  assert.equal(options.webPreferences.sandbox, true);
});
