"""校验 pm-discovery 技能（T-2026-09-21-029）。

用法（从任意目录）：
    python .trae/scripts/check_pm_discovery_skill.py

字段清单不在这里手写：直接从 .trae/rules/harness-clarification.md 正文解析，
避免「同一条规则存两处」。
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, bullets, clarification_fields, enable_utf8_stdout, exists,
    max_overlap_with_files, placeholder_hits, read_text, section_body, section_titles,
)

SKILL = ".trae/skills/pm-discovery/SKILL.md"
CLARIFICATION = ".trae/rules/harness-clarification.md"
INSTALLED_BRAINSTORMING = "C:/Users/23745/.agents/skills/brainstorming/SKILL.md"

CONVERGENCE_SECTIONS = ["有价值吗", "值得做吗", "可以做吗"]
MIN_JUDGEMENTS = 3
MIN_REJECTS = 3
MIN_ANTIPATTERNS = 4
MIN_ADAPTATIONS = 4
OVERLAP_N = 60

ADAPTATION_KEYWORDS = [
    "本地优先", "Java-only", "200 行", "单人开发", "44dp", "平板横屏", "冻结",
]


def main():
    enable_utf8_stdout()
    c = Checks("check_pm_discovery_skill")

    if not c.check(exists(SKILL), "file exists: " + SKILL):
        return c.finish()

    text = read_text(SKILL)
    head = text[:400]
    c.check(re.search(r"^name:\s*pm-discovery\s*$", head, re.M) is not None,
            "front matter declares name: pm-discovery")
    c.check(re.search(r"^description:\s*\S", head, re.M) is not None,
            "front matter declares a non-empty description")

    titles = section_titles(text) + section_titles(text, 3)
    for s in CONVERGENCE_SECTIONS:
        body = section_body(text, s, 3) or section_body(text, s, 2)
        if not c.check(body is not None, "convergence section exists: " + s):
            continue
        n = len(bullets(body))
        c.check(n >= MIN_JUDGEMENTS, "  {0} has >= {1} judgements".format(s, MIN_JUDGEMENTS),
                "measured={0}".format(n))

    for name, minimum in (("建议不做", MIN_REJECTS), ("反模式", MIN_ANTIPATTERNS)):
        body = section_body(text, name, 3) or section_body(text, name, 2)
        if not c.check(body is not None, "section exists: " + name):
            continue
        n = len(bullets(body))
        c.check(n >= minimum, "  {0} has >= {1} items".format(name, minimum),
                "measured={0}".format(n))

    checklist = section_body(text, "提问清单", 3) or section_body(text, "提问清单", 2)
    fields = clarification_fields()
    if c.check(checklist is not None and fields is not None,
               "checklist section and clarification field list are both readable",
               "fields_parsed={0}".format(len(fields) if fields else None)):
        missing = [f for f in fields if f not in checklist]
        c.check(not missing, "checklist covers every clarification field",
                "measured={0}; missing={1}".format(len(fields), missing))

    adapt = section_body(text, "项目适配", 3) or section_body(text, "项目适配", 2)
    if adapt is None:
        adapt = next((section_body(text, t, 3) for t in titles
                      if t.startswith("项目适配")), None)
    if c.check(adapt is not None or "项目适配" in text,
               "project adaptation section exists"):
        scope = adapt if adapt is not None else text
        found = [k for k in ADAPTATION_KEYWORDS if k in scope]
        c.check(len(found) >= MIN_ADAPTATIONS,
                "adaptation section names >= {0} Treelearn constraints".format(MIN_ADAPTATIONS),
                "measured={0} found={1}".format(len(found), found))

    src = section_body(text, "溯源与改动", 3) or section_body(text, "溯源与改动", 2)
    if c.check(src is not None, "provenance section exists"):
        c.check(("github.com" in src) or ("skills/" in src),
                "provenance cites a concrete upstream location")
        c.check("2026-09-21" in src, "provenance records the access date")

    sources = [CLARIFICATION]
    if exists(INSTALLED_BRAINSTORMING):
        sources.append(INSTALLED_BRAINSTORMING)
    else:
        print("SKIPPED  upstream overlap check against installed brainstorming skill"
              " (not present at {0})".format(INSTALLED_BRAINSTORMING))
    hits, detail = max_overlap_with_files(text, sources, OVERLAP_N)
    c.check(hits <= 2, "shares at most 2 runs of {0} chars with its sources".format(OVERLAP_N),
            "measured_hits={0} sources={1} sample={2}".format(hits, sources, detail[:2]))

    ph = placeholder_hits(text)
    c.check(not ph, "no placeholder tokens", "measured={0}".format(ph))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
