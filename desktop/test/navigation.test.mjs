import test from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { isAllowedNavigation } = require("../navigation.cjs");

test("允许加载包内的 file:// 页面", () => {
  assert.equal(isAllowedNavigation("file:///D:/Project/Android/Treelearn/desktop/app/index.html"), true);
  assert.equal(isAllowedNavigation("file:///D:/Project/Android/Treelearn/desktop/app/index.html#/chat/1"), true);
});

test("拒绝外部与伪协议导航", () => {
  for (const url of [
    "https://example.com",
    "http://127.0.0.1:5173",
    "javascript:alert(1)",
    "data:text/html,<p>x</p>",
    "about:blank",
    "chrome://settings"
  ]) {
    assert.equal(isAllowedNavigation(url), false, url);
  }
});

test("非字符串输入一律拒绝", () => {
  for (const value of [undefined, null, 42, {}, []]) {
    assert.equal(isAllowedNavigation(value), false);
  }
});
