"""校验 Plan 阶段背景包（T-2026-09-21-027）。

用法（从任意目录）：
    python .trae/scripts/check_context_pack.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, backticked_paths, enable_utf8_stdout, exists, max_overlap_with_files,
    nonempty_nonheader_lines, placeholder_hits, read_text, section_body,
    section_titles, walk_files,
)

BRIEF = ".trae/context/project-brief.md"
GLOSSARY = ".trae/context/domain-glossary.md"
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
OVERLAP_N = 40


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
    c.check(listed == actual, "docs index equals the real Docs/ file set",
            "listed={0} actual={1} missing={2} extra={3}".format(
                len(listed), len(actual), sorted(actual - listed), sorted(listed - actual)))

    dead = sorted(p for p in listed if not exists(p))
    c.check(not dead, "every indexed path exists", "dead={0}".format(dead))

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

    hits, detail = max_overlap_with_files(brief, walk_files("Docs"), OVERLAP_N)
    c.check(hits == 0, "brief shares no {0}-char run with Docs/".format(OVERLAP_N),
            "measured_hits={0} sample={1}".format(hits, detail[:3]))

    ph = {rel: placeholder_hits(read_text(rel)) for rel in (BRIEF, GLOSSARY)}
    bad_ph = {k: v for k, v in ph.items() if v}
    c.check(not bad_ph, "no placeholder tokens", "measured={0}".format(bad_ph))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
