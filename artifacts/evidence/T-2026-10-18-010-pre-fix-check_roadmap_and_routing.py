"""校验遗留路线文档与最高级路由登记（T-2026-09-21-031）。

用法（从任意目录）：
    python .trae/scripts/check_roadmap_and_routing.py

条目数、字段名、路径存在性、AGENTS.md 的改动形状全部按正文与 git 实测，
不接收任务文件里的自述。
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
MIN_NEW_ROUTES = 4
ROUTE_KEYWORDS = ["intent-capturer", "pm-discovery", "project-brief", "ROADMAP"]

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

    diff = git("diff", "--unified=0", "--", AGENTS)
    if c.check(diff.returncode == 0, "git diff AGENTS.md is readable",
               "stderr={0}".format(diff.stderr.strip()[:200])):
        added = [l[1:] for l in diff.stdout.splitlines()
                 if l.startswith("+") and not l.startswith("+++")]
        removed = [l for l in diff.stdout.splitlines()
                   if l.startswith("-") and not l.startswith("---")]
        c.check(not removed, "AGENTS.md change contains no deletion line",
                "measured_deletions={0} sample={1}".format(len(removed), removed[:2]))

        work_text = read_text(AGENTS)
        head = git("show", "HEAD:" + AGENTS)
        if c.check(head.returncode == 0, "HEAD revision of AGENTS.md is readable",
                   "stderr={0}".format(head.stderr.strip()[:200])):
            head_rows = [l for l in head.stdout.splitlines()
                         if l.startswith("|") or l.startswith("- [")]
            vanished = [l for l in head_rows if l.strip() and l not in work_text]
            c.check(not vanished,
                    "every pre-existing routing row and case record survives verbatim",
                    "measured_head_rows={0} vanished={1}".format(
                        len(head_rows), vanished[:2]))

        new_routes = [l for l in added if l.startswith("|") and "](" in l]
        c.check(len(new_routes) >= MIN_NEW_ROUTES,
                "AGENTS.md adds >= {0} routing rows".format(MIN_NEW_ROUTES),
                "measured={0}".format(len(new_routes)))
        hit = [k for k in ROUTE_KEYWORDS if any(k in l for l in new_routes)]
        c.check(len(hit) == len(ROUTE_KEYWORDS),
                "new routing rows cover all four triggers",
                "missing={0}".format([k for k in ROUTE_KEYWORDS if k not in hit]))
        dead_routes = sorted({t for l in new_routes for t in LINK_RE.findall(l)
                              if not exists(t)})
        c.check(not dead_routes, "every new routing-row target exists",
                "measured_dead={0}".format(dead_routes))

    readme = read_text(TRAE_README)
    tree = section_body(readme, "目录结构")
    if c.check(tree is not None, ".trae/README.md has a 目录结构 section"):
        miss = [k for k in README_ENTRIES if k not in tree]
        c.check(not miss, "目录结构 section lists the four new entries",
                "missing={0}".format(miss))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
