"""校验 Plan 阶段背景包（T-2026-09-21-027）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_context_pack.py

2026-10-19 追加两条目录现状判据（片 T-2026-10-19-025）：背景包是 L0 常驻入口，它说「某目录
尚未创建」而那个目录其实已经在了，读它的人就会去别处找这份代码——这正是本次实测到的事故
（`frontend/` 已按 Web 线落地，正文仍写「两目录尚未创建」）。判据只认「同一行里出现该说法 +
该行引用了 `name/` 目录」这种形状，两个方向都判，并留一条裸下限防止把引用删光来通过。
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, backticked_paths, enable_utf8_stdout, exists, max_overlap_with_files,
    nonempty_nonheader_lines, placeholder_hits, read_text, repo_path, section_body,
    section_titles, walk_files,
)

BRIEF = ".codex/context/project-brief.md"
GLOSSARY = ".codex/context/domain-glossary.md"
REQUIRED_SECTIONS = [
    "产品一句话",
    "当前阶段与技术栈",
    "关键约束",
    "权威文档索引",
    "事实源（SoT）声明",
    "阅读顺序",
]
MAX_BRIEF_LINES = 60
MIN_TERMS = 12
OVERLAP_N = 80
# 背景包里的「目录尚未落地」是一句**会过期的现状主张**：frontend/ 已按 Web 线落地，而这句话还留着，
# 读它的人（或角色）就会去别处找这份「还不存在」的代码。下面这条只判这一种形状。
ABSENT_CLAIM = "尚未创建"
# 反引号里形如 `name/` 的顶层目录引用。裸下限写死在这里：把引用删光同样能让上一条判据空集通过。
DIR_CITE_RE = re.compile(r"`([a-z][a-z_-]*)/`")
MIN_CITED_DIRS = 2


def main():
    enable_utf8_stdout()
    c = Checks("check_context_pack")

    for rel in (BRIEF, GLOSSARY):
        if not c.check(exists(rel), "file exists: " + rel):
            return c.finish()

    brief = read_text(BRIEF)
    glossary = read_text(GLOSSARY)

    body_lines = nonempty_nonheader_lines(brief)
    c.check(len(body_lines) <= MAX_BRIEF_LINES,
            "brief is one page (non-empty, non-header lines <= {0})".format(MAX_BRIEF_LINES),
            "measured={0}".format(len(body_lines)))

    titles = section_titles(brief)
    missing = [s for s in REQUIRED_SECTIONS if s not in titles]
    c.check(not missing, "brief has the six required sections",
            "measured={0}; missing={1}".format(len(REQUIRED_SECTIONS), missing))

    docs_index = section_body(brief, "权威文档索引")
    listed = set(backticked_paths(docs_index or ""))
    actual = set(walk_files("Docs"))
    c.check(bool(listed) and listed <= actual, "docs index is a valid curated subset of the real Docs/ file set",
            "listed={0} actual={1} missing={2} unlisted={3}".format(
                len(listed), len(actual), sorted(listed - actual), sorted(actual - listed)[:12]))

    dead = sorted(p for p in listed if not exists(p))
    c.check(not dead, "every indexed path exists", "dead={0}".format(dead))

    cited = sorted({m.group(1) for m in DIR_CITE_RE.finditer(brief)})
    if not c.check(len(cited) >= MIN_CITED_DIRS,
                   "brief cites >= {0} top-level dirs".format(MIN_CITED_DIRS),
                   "measured={0} floor={1} cited={2}".format(len(cited), MIN_CITED_DIRS, cited)):
        return c.finish()
    # 两条合成一个充要条件：某行引用的目录**存在 ⇔ 该行没说它「尚未创建」**。
    # 只写一条就会漏另一个方向——「已落地的目录被写成尚未创建」让人去别处找代码，
    # 「不存在的目录被当现状引用」则是一个死指针。
    claimed_absent = sorted({m.group(1) for line in brief.splitlines()
                             if ABSENT_CLAIM in line
                             for m in DIR_CITE_RE.finditer(line)})
    stale = sorted(d for d in claimed_absent if os.path.isdir(repo_path(d)))
    c.check(not stale,
            "no dir is claimed `{0}` while present on disk".format(ABSENT_CLAIM),
            "claimed_absent={0} stale={1} cited={2}".format(claimed_absent, stale, cited))
    dead_dirs = sorted({m.group(1) for line in brief.splitlines()
                        if ABSENT_CLAIM not in line
                        for m in DIR_CITE_RE.finditer(line)
                        if not os.path.isdir(repo_path(m.group(1)))})
    c.check(not dead_dirs, "every other cited dir exists on disk",
            "dead={0} cited={1}".format(dead_dirs, cited))

    terms = []
    for line in glossary.splitlines():
        s = line.strip()
        if s.startswith("|") and s.endswith("|") and s.count("|") >= 4:
            cells = [x.strip() for x in s.strip("|").split("|")]
            if len(cells) >= 3 and cells[2] and not set(cells[0]) <= set("-: "):
                if cells[0] == "术语":
                    continue
                terms.append(cells)
    c.check(len(terms) >= MIN_TERMS, "glossary has >= {0} terms".format(MIN_TERMS),
            "measured={0}".format(len(terms)))

    bad_src = sorted({t[2] for t in terms if not exists(t[2])})
    c.check(not bad_src, "every glossary term cites a real path",
            "measured_terms={0} dead_sources={1}".format(len(terms), bad_src))

    overlap_text = re.sub(r"`[^`]+`", "", brief)
    hits, detail = max_overlap_with_files(overlap_text, walk_files("Docs"), OVERLAP_N)
    c.check(hits == 0, "brief shares no {0}-char run with Docs/".format(OVERLAP_N),
            "measured_hits={0} sample={1}".format(hits, detail[:3]))

    ph = {rel: placeholder_hits(read_text(rel)) for rel in (BRIEF, GLOSSARY)}
    bad_ph = {k: v for k, v in ph.items() if v}
    c.check(not bad_ph, "no placeholder tokens", "measured={0}".format(bad_ph))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())

