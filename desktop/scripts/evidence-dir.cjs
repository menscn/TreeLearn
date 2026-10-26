/**
 * 矩阵证据目录的唯一来源。
 *
 * 目录曾经写死成 `2026-10-24`：那次之后每次重跑 verify 都会把同一批 JPEG 与 report.json
 * 覆盖掉，历史证据只能从 git 里找回来。现在默认按运行当天分目录，重跑不再动昨天的文件；
 * `TREELEARN_EVIDENCE_DATE` 只用来复跑某一档，不是把日期变成可变行为。
 */
const path = require("node:path");

const EVIDENCE_ROOT = path.join(__dirname, "..", "..", "artifacts", "evidence", "desktop-shell");

function evidenceDate(env = process.env, now = new Date()) {
  const pinned = typeof env.TREELEARN_EVIDENCE_DATE === "string" ? env.TREELEARN_EVIDENCE_DATE.trim() : "";
  if (pinned !== "") return pinned;
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

function evidenceDir(env, now) {
  return path.join(EVIDENCE_ROOT, evidenceDate(env, now));
}

module.exports = { EVIDENCE_ROOT, evidenceDate, evidenceDir };
