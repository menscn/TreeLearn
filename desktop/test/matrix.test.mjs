import test from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const {
  VIEWS,
  LIBRARY_FILTERS,
  SETTINGS_CATEGORIES,
  SCREEN_MATRIX,
  WINDOW_SIZES
} = require("../scripts/matrix.cjs");

test("视图、筛选与设置分类与 Web 工作区一致", () => {
  assert.deepEqual(VIEWS, ["home", "library", "settings", "chat", "persona-intro", "forest"]);
  assert.deepEqual(LIBRARY_FILTERS, ["all", "in_progress", "done"]);
  assert.deepEqual(SETTINGS_CATEGORIES, ["api-key", "provider", "model", "search", "voice", "files"]);
});

test("每个视图至少有一个可验证状态，分类与筛选逐项在场", () => {
  const ids = SCREEN_MATRIX.map((state) => state.id);
  for (const view of VIEWS) {
    assert.ok(
      ids.some((id) => id === view || id.startsWith(`${view}-`)),
      `缺少视图 ${view} 的验证状态`
    );
  }
  assert.equal(SCREEN_MATRIX.filter((state) => state.category !== undefined).length, SETTINGS_CATEGORIES.length);
  assert.equal(SCREEN_MATRIX.filter((state) => state.filter !== undefined).length, LIBRARY_FILTERS.length);
});

test("状态 id 唯一，尺寸固定为两档", () => {
  const ids = SCREEN_MATRIX.map((state) => state.id);
  assert.equal(new Set(ids).size, ids.length);
  assert.deepEqual(WINDOW_SIZES, [
    { width: 1180, height: 820 },
    { width: 900, height: 620 }
  ]);
});
