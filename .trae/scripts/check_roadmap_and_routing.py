"""校验遗留路线文档与最高级路由登记（T-2026-09-21-031）。

用法（从任意目录）：
    python .trae/scripts/check_roadmap_and_routing.py

条目数、字段名、路径存在性、AGENTS.md 的改动形状全部按正文与 git 实测，
不接收任务文件里的自述。

AGENTS.md 的路由判定按**当前文件全文**（行数下限 + 四个触发词覆盖 + 链接目标存在），
不按 git 增量；「本次改动新增了几行路由」只在增量窗口内成立，因此降级为打印，
并写明基线与窗口。理由见 AGENTS.md 失败案例库 [2026-10-16] [guard] 与 ROADMAP 的 D1。
"""

import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, REPO_ROOT, enable_utf8_stdout, exists, placeholder_hits, read_text,
    section_body,
)

ROADMAP = ".trae/ROADMAP.md"
AGENTS = "AGENTS.md"
TRAE_README = ".trae/README.md"

PHASE_SECTIONS = ["Phase A（已完成）", "Phase B（下一批）", "Phase C（依赖外部条件）"]
ENTRY_FIELDS = ["ID", "目标", "范围（可写路径）", "依赖", "owner_lane", "验收方式",
                "开源来源", "执行提示词"]
MIN_PHASE_B = 7
MIN_PHASE_C = 3
MIN_PROMPT_CHARS = 40
PHASE_B_KEYWORDS = ["spec-author", "code-reviewer", "REVIEW.md", "eval-keeper", "守门",
                    "codebase-researcher", "code-simplifier", "metrics-reporter"]
PHASE_C_KEYWORDS = ["loop-watcher", "事实源", "release-gate"]

README_ENTRIES = [".trae/context/", ".trae/templates/", ".trae/ROADMAP.md", "Docs/intent/"]
ROUTE_KEYWORDS = ["intent-capturer", "pm-discovery", "project-brief", "ROADMAP"]

# 当前态下限，写在与那张表无关的裸字面量上（实测 20 行，留纯追加的余量）。
# 用「发现行数」推导自己的下限等于没有下限——见 AGENTS.md 失败案例库
# 「用被检清单算自己的下限」；把下限改成 len(rows) 之后，删空整张表仍是绿。
MIN_ROUTE_ROWS = 12

ENTRY_HEAD_RE = re.compile(r"^###\s+(\S+)")
FIELD_LINE_RE = re.compile(r"^-\s*\*\*([^*]+)\*\*\s*[：:]\s*(.*)$")
# 执行提示词里只允许引用「现在就已经存在」的仓库路径。这里比 lib 的 backticked_paths
# 多认 AGENTS.md，因为提示词确实会让人先读它；命令片段（如 python .trae/...）不以
# 仓库前缀开头，天然不被匹配。
PROMPT_PATH_RE = re.compile(r"`((?:Docs|\.trae|android|artifacts)/[^`\s]+|AGENTS\.md)`")
LINK_RE = re.compile(r"\]\(([^)]+)\)")


def split_entries(body):
    entries = []
    cur = None
    for line in body.splitlines():
        if ENTRY_HEAD_RE.match(line):
            cur = {"title": line.lstrip("#").strip(), "lines": []}
            entries.append(cur)
        elif cur is not None:
            cur["lines"].append(line)
    return entries


def entry_fields(entry):
    out = {}
    for line in entry["lines"]:
        m = FIELD_LINE_RE.match(line.strip())
        if m:
            out.setdefault(m.group(1).strip(), m.group(2).strip())
    return out


def phase_body(text, title):
    """取某个 ## 小节的正文。

    不能用 lib 的 section_body：它在任何以 # 开头的行处就停，
    而 Phase 小节里满屏都是 ### 条目标题，会得到空正文
    （实测：条目数被读成 0 而不是 6）。
    """
    lines = text.splitlines()
    prefix = "## " + title
    start = None
    for i, l in enumerate(lines):
        if l.rstrip() == prefix:
            start = i + 1
            break
    if start is None:
        return None
    out = []
    for l in lines[start:]:
        if l.startswith("## "):
            break
        out.append(l)
    return "\n".join(out)


def git(*args):
    return subprocess.run(["git", "-c", "core.quotepath=false"] + list(args),
                          cwd=REPO_ROOT, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def routing_rows(text):
    """路由表里带链接的行：表头与分隔行不含 ](，天然被排除。

    取材必须是整份文件而不是 git 增量——增量在提交后必然为空，
    用它当判据等于给一条只在「尚未提交」窗口内可成立的断言挂永久门禁。
    """
    return [l for l in text.splitlines() if l.startswith("|") and "](" in l]


def main():
    enable_utf8_stdout()
    c = Checks("check_roadmap_and_routing")

    for rel in (ROADMAP, AGENTS, TRAE_README):
        if not c.check(exists(rel), "file exists: " + rel):
            return c.finish()

    text = read_text(ROADMAP)
    for name in PHASE_SECTIONS:
        c.check(phase_body(text, name) is not None, "phase section exists: " + name)

    for name in PHASE_SECTIONS[1:]:
        body = phase_body(text, name) or ""
        entries = split_entries(body)
        minimum = MIN_PHASE_B if "Phase B" in name else MIN_PHASE_C
        c.check(len(entries) >= minimum,
                "{0} has >= {1} entries".format(name, minimum),
                "measured={0}".format(len(entries)))

        field_problems = []
        short_prompts = []
        dead_paths = []
        weak_sources = []
        for e in entries:
            f = entry_fields(e)
            miss = [k for k in ENTRY_FIELDS if k not in f]
            if miss:
                field_problems.append((e["title"], miss))
                continue
            if len(f["执行提示词"]) < MIN_PROMPT_CHARS:
                short_prompts.append((f["ID"], len(f["执行提示词"])))
            for p in PROMPT_PATH_RE.findall(f["执行提示词"]):
                if not exists(p):
                    dead_paths.append((f["ID"], p))
            src = f["开源来源"]
            if ("github.com" not in src) and ("skills/" not in src):
                weak_sources.append(f["ID"])

        c.check(not field_problems,
                "{0} every entry carries all 8 field names".format(name),
                "measured_entries={0} problems={1}".format(len(entries), field_problems))
        c.check(not short_prompts,
                "{0} every 执行提示词 is >= {1} chars".format(name, MIN_PROMPT_CHARS),
                "measured_short={0}".format(short_prompts))
        c.check(not dead_paths,
                "{0} every path inside 执行提示词 exists".format(name),
                "measured_dead={0}".format(dead_paths))
        c.check(not weak_sources,
                "{0} every 开源来源 cites github.com or a skills path".format(name),
                "measured_weak={0}".format(weak_sources))

    b_text = phase_body(text, PHASE_SECTIONS[1]) or ""
    c_text = phase_body(text, PHASE_SECTIONS[2]) or ""
    miss_b = [k for k in PHASE_B_KEYWORDS if k not in b_text]
    c.check(not miss_b, "Phase B covers the seven planned capabilities",
            "missing={0}".format(miss_b))
    miss_c = [k for k in PHASE_C_KEYWORDS if k not in c_text]
    c.check(not miss_c, "Phase C covers the three planned capabilities",
            "missing={0}".format(miss_c))

    ph = placeholder_hits(text)
    c.check(not ph, "no placeholder tokens in ROADMAP", "measured={0}".format(ph))

    work_text = read_text(AGENTS)
    rows = routing_rows(work_text)
    targets = sorted({t for l in rows for t in LINK_RE.findall(l)})
    c.check(len(rows) >= MIN_ROUTE_ROWS,
            "AGENTS.md routing table currently has >= {0} linked rows".format(MIN_ROUTE_ROWS),
            "measured={0}".format(len(rows)))
    missing_kw = [k for k in ROUTE_KEYWORDS if not any(k in l for l in rows)]
    c.check(not missing_kw, "current routing rows cover all four triggers",
            "measured_rows={0} missing={1}".format(len(rows), missing_kw))
    dead = [t for t in targets if not exists(t)]
    c.check(bool(targets) and not dead, "every current routing-row target exists",
            "measured_targets={0} dead={1}".format(len(targets), dead))

    # 下面量的是「本次未提交改动」的形状，只在增量窗口内可成立，因此不再当判据：
    # 空窗口打 OK 会把「无从认证」读成「认证通过」，所以打 SKIPPED；
    # 永久强制力由上面三条当前态判据承担，总退出码不需要为它单独开一档。
    diff = git("diff", "--unified=0", "--", AGENTS)
    if c.check(diff.returncode == 0, "git diff AGENTS.md is readable",
               "stderr={0}".format(diff.stderr.strip()[:200])):
        added = [l[1:] for l in diff.stdout.splitlines()
                 if l.startswith("+") and not l.startswith("+++")]
        removed = [l for l in diff.stdout.splitlines()
                   if l.startswith("-") and not l.startswith("---")]
        base = git("rev-parse", "--verify", "HEAD")
        base_id = base.stdout.strip()[:12] if base.returncode == 0 else "unreadable"
        if not added and not removed:
            c.note("SKIPPED append-only guard has no uncommitted AGENTS.md increment",
                   "base={0} window=uncommitted-only added=0 deletions=0".format(base_id))
        else:
            c.check(not removed, "AGENTS.md change contains no deletion line",
                    "base={0} window=uncommitted-only added={1} deletions={2} sample={3}".format(
                        base_id, len(added), len(removed), removed[:2]))

        head = git("show", "HEAD:" + AGENTS)
        if c.check(head.returncode == 0, "HEAD revision of AGENTS.md is readable",
                   "stderr={0}".format(head.stderr.strip()[:200])):
            head_rows = [l for l in head.stdout.splitlines()
                         if l.startswith("|") or l.startswith("- [")]
            vanished = [l for l in head_rows if l.strip() and l not in work_text]
            c.check(not vanished,
                    "every pre-existing routing row and case record survives verbatim",
                    "measured_head_rows={0} base={1} vanished={2}".format(
                        len(head_rows), base_id, vanished[:2]))

        added_rows = routing_rows("\n".join(added))
        c.note("routing-row increment is printed, not judged",
               "base={0} window=uncommitted-only added_rows={1} added_missing_triggers={2} "
               "added_dead_targets={3}".format(
                   base_id, len(added_rows),
                   [k for k in ROUTE_KEYWORDS if not any(k in l for l in added_rows)],
                   sorted({t for l in added_rows for t in LINK_RE.findall(l)
                           if not exists(t)})))

    readme = read_text(TRAE_README)
    tree = section_body(readme, "目录结构")
    if c.check(tree is not None, ".trae/README.md has a 目录结构 section"):
        miss = [k for k in README_ENTRIES if k not in tree]
        c.check(not miss, "目录结构 section lists the four new entries",
                "missing={0}".format(miss))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
