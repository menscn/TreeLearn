import test from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import path from "node:path";

const require = createRequire(import.meta.url);
const { EVIDENCE_ROOT, evidenceDate, evidenceDir } = require("../scripts/evidence-dir.cjs");

test("默认按运行当天分目录，重跑不覆盖历史证据", () => {
  const now = new Date(2026, 9, 27, 15, 30);
  assert.equal(evidenceDate({}, now), "2026-10-27");
  assert.equal(evidenceDir({}, now), path.join(EVIDENCE_ROOT, "2026-10-27"));
});

test("月日补零，跨年与跨月都读本地日期", () => {
  assert.equal(evidenceDate({}, new Date(2027, 0, 5, 1, 0)), "2027-01-05");
  assert.equal(evidenceDate({}, new Date(2026, 11, 31, 23, 59)), "2026-12-31");
});

test("显式日期只用于复跑某一档，空白值回落当天", () => {
  const now = new Date(2026, 9, 27, 9, 0);
  assert.equal(evidenceDate({ TREELEARN_EVIDENCE_DATE: "2026-10-24" }, now), "2026-10-24");
  assert.equal(evidenceDate({ TREELEARN_EVIDENCE_DATE: "  " }, now), "2026-10-27");
});

test("证据根目录固定在 artifacts/evidence/desktop-shell", () => {
  assert.ok(EVIDENCE_ROOT.endsWith(path.join("artifacts", "evidence", "desktop-shell")));
});
