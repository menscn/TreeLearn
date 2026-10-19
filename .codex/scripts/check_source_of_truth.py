"""事实源（SoT）全量清单与它的守门（ROADMAP C3）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_source_of_truth.py

为什么需要这支脚本：`.codex/context/project-brief.md` 的 SoT 小节只写了五条域级主张，
「同一事实只允许一处权威」这句话本身没有牙——同一条约束在 `AGENTS.md`、`.codex/README.md`、
`.codex/rules/**`、`.codex/skills/**`、`Docs/**` 与 `android/README.md` 里各写一遍时，
没有任何一条命令会响。本清单把「哪一条事实在哪一处有权威版本」逐条落成机器可读的条目，
这支脚本判 ROADMAP 验收方式点名的三件事：每条事实有且只有一个权威路径、该路径在仓库里真实存在、
同一事实出现第二处定义即 FAIL。

两种事实型分开判，因为它们的失效形状不同（实测时两条锚点各自命中过对方那一类行）：
- 声明型：这条约束由人写定在权威那一处。别处再出现同一形状，只有同一行带归属标记才算「指向权威」，
  不带标记就是另立权威 → FAIL。允许带标记的引用而不允许裸抄，是因为纠正性引用（「已由 X 收窄」）
  本身就是证据；把它判红只会逼人删证据来变绿。
- 生成型：这个数是脚本产出的。它在任何散文里被写死一次就已经是第二权威，所以要求扫描面零命中；
  并要求它的漂移检查是一支**带裸下限常量**的守门——否则「以实测为准」又是一句没有牙的主张。

清单自身也在这套判据里：它不许被任何条目当成权威（索引不是权威）、不许抄权威正文
（抄检窗口沿用 `harness_check_lib.max_overlap_with_files`，与角色守门同一口径）、
它自己那张「覆盖面」表里的数字必须由本脚本现场复算对上——复算对不上的数就是手抄数。

退出码：0 = 通过；1 = 有 FAIL。（注入用例在 .codex/scripts/dev/checkers_selftest.py 的
    `sot:*` 条目里，判据红绿由退出码体现。）
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, enable_utf8_stdout, exists, max_overlap_with_files, placeholder_hits,
    read_text, repo_path)

INVENTORY = ".codex/context/source-of-truth.md"
BRIEF = ".codex/context/project-brief.md"

# 扫描面＝「会被当成现状读的入口面」。历史面（任务卡、evidence、references、需求基线与 PRD 草稿）
# 不在里面：`documentation.md` 自己就判它们描述的是意图或历史，逐行追指针只会逼人删掉证据。
SCAN_PREFIXES = ("AGENTS.md", "README.md", "README_EN.md", ".codex/README.md",
                 ".codex/HARNESS.md", ".codex/ROADMAP.md", ".codex/context/",
                 ".codex/rules/", ".codex/skills/", "Docs/spec/", "Docs/05-technical-design/",
                 "Docs/04-ux-design/design-system/README.md", "android/README.md",
                 "frontend/README.md", "backend/README.md")
# 扫描集不能为空：这条防的是本仓库登记过三次的打穿形状——匹配集被挪空却打印 PASS。
MIN_SCAN_FILES = 40

# 条目数下限。写成裸字面量而不是数出来的条数：被检集合不能给自己定下限。
MIN_ENTRIES = 12
# 生成型条目下限：没有它们，「以实测为准」永远只是一句主张。
MIN_GENERATED = 2
# 锚点最长长度：再长就不是「认出这一行的形状」，而是把权威正文抄进了清单。上限按「整行
# 级别的合取锚点」定：`^(?=.*A)(?=.*B).*(?:C|D)` 这种形状是必要的（锚点要在同一行的
# 任意位置认出定义，光靠零宽前视从当前位置往后看会漏掉关键词在前的行，实测漏过 2 处），
# 但它把长度抬过 80，所以把上限定在 96——仍然远小于一句话的长度，抄正文照样会被判长。
MAX_ANCHOR_CHARS = 96
# 更新时机最短长度：一个词（「随时」）不构成更新时机。
MIN_UPDATE_WHEN_CHARS = 8
# 与角色守门同一口径的抄检窗口。
OVERLAP_RUNS = 60
# 权威不许落在历史面上：那里的句子是记录，不该指导下一位实现者。
HISTORY_ROOTS = (".codex/tasks/", "artifacts/", ".codex/references/", "Docs/intent/",
                 "Docs/01-demand/", "Docs/03-prd-draft/", "Docs/03a-prd-cross-review/",
                 "Docs/06-delivery/", "Docs/reviews/")

DECLARED_KINDS = ("声明型", "生成型")

ROOT_CELL_RE = re.compile(r"`([^`]+)`")
CITATION_RE = re.compile(r"`([^`]+?):(\d+)`")
FIELD_LINE_RE = re.compile(r"^- \*\*([^*]+)\*\*\s*[：:]\s*(.*)$")
ENTRY_HEAD_RE = re.compile(r"^###\s+(.+?)\s*$")
DRIFT_CMD_RE = re.compile(r"python(?:\.3)?(?: -X \w+)* ([^\s`]+\.py)")
FLOOR_RE = re.compile(r"^MIN_[A-Z][A-Z_0-9]*\s*=\s*\d+", re.M)


def scan_files():
    """按 SCAN_PREFIXES 收集当前态入口面的 `.md` 文件（仓库相对路径，已排序、去重）。"""
    root = repo_path("")
    out = []
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in ("__pycache__", "node_modules", "build",
                                               "dist", "tmp", ".git")]
        for name in files:
            if not name.endswith(".md"):
                continue
            rel = os.path.relpath(os.path.join(base, name), root).replace(os.sep, "/")
            if rel.startswith(SCAN_PREFIXES):
                out.append(rel)
    return sorted(set(out))


def parse_entries(text):
    """把 `### 条目` 下的字段行读成 `[{id, fields:{字段名: [值, …]}}]`；同名字段的值全部留着。

    字段值按出现顺序存成列表而不是覆盖：清单里同一字段写两遍正是「同一事实的第二处定义」，
    覆盖成后一份就等于把它藏起来。
    """
    entries = []
    cur = None
    for line in text.splitlines():
        head = ENTRY_HEAD_RE.match(line)
        if head:
            cur = {"id": head.group(1), "fields": {}}
            entries.append(cur)
            continue
        if line.startswith("## "):
            cur = None
            continue
        if cur is None:
            continue
        m = FIELD_LINE_RE.match(line.strip())
        if m:
            cur["fields"].setdefault(m.group(1).strip(), []).append(m.group(2).strip())
    return entries


def field(entry, key):
    vals = entry["fields"].get(key) or []
    return vals[0] if len(vals) == 1 else None


def coverage_table(text):
    """读「覆盖面」里的 `| `root` | n |` 行，返回 [(root, n)]；表头与分隔行天然不匹配。"""
    rows = []
    for line in text.splitlines():
        s = line.strip()
        if not (s.startswith("|") and s.endswith("|")):
            continue
        cells = [x.strip() for x in s.strip("|").split("|")]
        if len(cells) != 2:
            continue
        m = ROOT_CELL_RE.fullmatch(cells[0])
        if not m or not re.fullmatch(r"\d+", cells[1]):
            continue
        rows.append((m.group(1), int(cells[1])))
    return rows


def authority_root(rel):
    """把权威路径归到「覆盖面」表用的根：`.codex/` 下按二级目录分，其余按顶层目录分。"""
    parts = rel.split("/")
    if parts[0] == ".codex":
        if len(parts) >= 2 and "." not in parts[1]:
            return ".codex/{0}/".format(parts[1])
        return ".codex/"
    if len(parts) == 1:
        return parts[0]
    if parts[0] == "Docs":
        return "Docs/"
    return "{0}/".format(parts[0])


def check_entry(e, kind, declared, state):
    """判单条目的形状、权威、锚点与漂移检查，把可用条目追加进 state["usable"]。

    返回 [( judgement_key, detail )] 形式的坏法，由调用方按判据分桶——分桶是为了让读数能指明
    「坏在哪一类」，而不是把六类坏法印成同一行。
    """
    problems = []
    ident = e["id"]
    got = sorted(e["fields"])
    if got != sorted(declared):
        return [("shape", "{0}::missing={1} extra={2}".format(
            ident, sorted(set(declared) - set(got)), sorted(set(got) - set(declared))))]
    doubled = {k: len(v) for k, v in e["fields"].items() if len(v) > 1}
    if doubled:
        return [("shape", "{0}::duplicated_fields={1}".format(ident, doubled))]
    if kind not in DECLARED_KINDS:
        return [("shape", "{0}::kind={1!r}".format(ident, kind))]

    cites = CITATION_RE.findall(field(e, "唯一权威") or "")
    if len(cites) != 1:
        return [("authority", "{0}::cited={1}".format(ident, cites))]
    rel, line_no = cites[0][0].strip().rstrip("/"), int(cites[0][1])
    if rel == INVENTORY:
        return [("authority", "{0}::the index cites itself as its own authority".format(ident))]
    if not exists(rel):
        return [("dead", "{0}::{1}:{2}".format(ident, rel, line_no))]
    if rel.startswith(HISTORY_ROOTS):
        return [("history", "{0}::{1}".format(ident, rel))]
    auth_lines = read_text(rel).splitlines()
    if line_no < 1 or line_no > len(auth_lines):
        return [("dead", "{0}::{1}:{2} beyond eof={3}".format(ident, rel, line_no,
                                                              len(auth_lines)))]

    pattern = (field(e, "锚点") or "").strip("`")
    if not pattern or len(pattern) > MAX_ANCHOR_CHARS:
        return [("anchor", "{0}::len={1} max={2}".format(ident, len(pattern), MAX_ANCHOR_CHARS))]
    try:
        rx = re.compile(pattern)
    except re.error as exc:
        return [("anchor", "{0}::uncompilable={1}".format(ident, exc))]

    upd = field(e, "更新时机") or ""
    if len(upd) < MIN_UPDATE_WHEN_CHARS:
        problems.append(("update", "{0}::len={1}".format(ident, len(upd))))
    cmd = field(e, "漂移检查") or ""
    m = DRIFT_CMD_RE.search(cmd)
    if not m or not exists(m.group(1)):
        problems.append(("drift", "{0}::cmd={1!r} runnable_py_found={2}".format(
            ident, cmd[:48], bool(m))))
    drift_script = m.group(1) if m and exists(m.group(1)) else None

    if kind == "声明型":
        if not rx.search(auth_lines[line_no - 1]):
            problems.append(("anchor", "{0}::anchor does not fire on the cited line "
                                       "{1}:{2} line={3!r}".format(ident, rel, line_no,
                                                                    auth_lines[line_no - 1]
                                                                    [:56])))
        attr = (field(e, "归属标记") or "").strip("`")
        try:
            attr_rx = re.compile(attr)
        except re.error as exc:
            problems.append(("anchor", "{0}::uncompilable 归属标记={1}".format(ident, exc)))
            attr_rx = None
        if attr_rx is None:
            return problems
    else:
        attr_rx = None
        if drift_script and not FLOOR_RE.search(read_text(drift_script)):
            problems.append(("drift", "{0}::drift script {1} declares no bare MIN_ floor "
                                      "so the generated count has no owner".format(
                ident, drift_script)))
    state["usable"].append((e, kind, rel, rx, attr_rx, ident))
    return problems


def main():
    enable_utf8_stdout()
    c = Checks("check_source_of_truth")

    if not c.check(exists(INVENTORY), "inventory exists", "path={0}".format(INVENTORY)):
        return c.finish()
    if not c.check(exists(BRIEF), "the L0 entry that must route here exists",
                   "path={0}".format(BRIEF)):
        return c.finish()
    text = read_text(INVENTORY)

    files = [f for f in scan_files() if f != INVENTORY]
    if not c.check(len(files) >= MIN_SCAN_FILES,
                   "the current-state scan surface holds >= {0} files".format(MIN_SCAN_FILES),
                   "measured={0} floor={1}".format(len(files), MIN_SCAN_FILES)):
        return c.finish()
    texts = {rel: read_text(rel) for rel in files}

    marker = re.search(r"<!--\s*FIELDS:(.*?)-->", text, re.S)
    if not c.check(bool(marker), "the inventory declares its field names in a FIELDS marker",
                   "found={0}".format(bool(marker))):
        return c.finish()
    declared = [x.strip() for x in marker.group(1).split("|") if x.strip()]

    entries = parse_entries(text)
    if not c.check(len(entries) >= MIN_ENTRIES,
                   "inventory has >= {0} fact entries".format(MIN_ENTRIES),
                   "measured={0} floor={1}".format(len(entries), MIN_ENTRIES)):
        return c.finish()
    ids = [e["id"] for e in entries]
    dup_ids = sorted({i for i in ids if ids.count(i) > 1})
    c.check(not dup_ids, "every fact id is declared once",
            "entries={0} duplicated={1}".format(len(entries), dup_ids))

    state = {"usable": [], "bad": set()}
    buckets = {"shape": [], "authority": [], "dead": [], "history": [], "anchor": [],
               "update": [], "drift": []}
    kinds = {}
    for e in entries:
        kind = (field(e, "类型") or "").strip(" `")
        for key, detail in check_entry(e, kind, declared, state):
            buckets.setdefault(key, []).append(detail)
        if kind in DECLARED_KINDS:
            kinds[kind] = kinds.get(kind, 0) + 1
    parsed = state["usable"]
    dropped = len(entries) - len(parsed)
    c.check(not buckets["shape"],
            "every entry declares each field exactly once, with a known 类型",
            "fields={0} malformed={1}".format(declared, buckets["shape"][:4]))
    c.check(not buckets["authority"],
            "every entry names exactly one authority, and the index is never its own authority",
            "measured={0} bad={1}".format(len(parsed), buckets["authority"][:4]))
    c.check(not buckets["dead"], "every authority path exists and the cited line is inside it",
            "dead={0}".format(buckets["dead"][:4]))
    c.check(not buckets["history"], "no authority is a historical record",
            "hit={0}".format(buckets["history"][:4]))
    c.check(not buckets["anchor"], "every anchor is a bounded, compilable pattern that fires on "
                                   "the cited authority line",
            "bad={0}".format(buckets["anchor"][:6]))
    c.check(not buckets["update"], "every entry states an update trigger of >= {0} chars".format(
        MIN_UPDATE_WHEN_CHARS), "bad={0}".format(buckets["update"][:4]))
    c.check(not buckets["drift"], "every drift check is a runnable command, and generated counts "
                                  "are owned by a script with a bare floor",
            "bad={0}".format(buckets["drift"][:4]))
    c.check(dropped == 0, "no entry was dropped by the per-entry checks",
            "entries={0} usable={1} dropped={2} bad_kinds={3}".format(
                len(entries), len(parsed), dropped, kinds))
    c.check(kinds.get("生成型", 0) >= MIN_GENERATED,
            "at least {0} entries are generated-count facts".format(MIN_GENERATED),
            "measured={0} floor={1} kinds={2}".format(kinds.get("生成型", 0), MIN_GENERATED,
                                                      kinds))

    second = []
    frozen = []
    for e, kind, auth_rel, rx, attr_rx, ident in parsed:
        for rel, body in texts.items():
            if rel == auth_rel:
                continue
            for no, line in enumerate(body.splitlines(), 1):
                if not rx.search(line):
                    continue
                if kind == "生成型":
                    frozen.append("{0} <- {1}:{2}".format(ident, rel, no))
                elif not attr_rx.search(line):
                    second.append("{0} <- {1}:{2}".format(ident, rel, no))
    c.check(not second, "no fact is defined a second time without an attribution pointer",
            "scanned_files={0} unattributed={1} hits={2}".format(len(texts), len(second),
                                                                 second[:16]))
    c.check(not frozen, "no generated count is written into prose anywhere on the scan surface",
            "measured={0} hits={1}".format(len(frozen), frozen[:16]))

    rows = coverage_table(text)
    counted = {}
    for e, kind, auth_rel, rx, attr_rx, ident in parsed:
        root = authority_root(auth_rel)
        counted[root] = counted.get(root, 0) + 1
    declared_cov = dict(rows)
    c.check(bool(rows) and len(rows) == len(declared_cov),
            "the 覆盖面 table is readable and names each root once",
            "rows={0} roots={1}".format(len(rows), sorted(declared_cov)))
    c.check(declared_cov == counted, "the 覆盖面 counts equal the recomputed per-root entry counts",
            "declared={0} measured={1}".format(declared_cov, counted))

    authorities = sorted({auth for _, _, auth, _, _, _ in parsed})
    hits, detail = max_overlap_with_files(text, authorities, OVERLAP_RUNS)
    c.check(hits == 0, "the index restates no authority body (>= {0}-char run)".format(
        OVERLAP_RUNS), "measured_hits={0} sample={1}".format(hits, detail[:2]))

    ph = sorted(set(placeholder_hits(text)))
    c.check(not ph, "the index carries no placeholder tokens", "hits={0}".format(ph))

    print("scanned_files={0} entries={1} kinds={2} authority_files={3}".format(
        len(texts), len(parsed), kinds, len(authorities)))
    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
