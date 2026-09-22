"""校验 intent 模板与存放约定（T-2026-09-21-030）。

用法（从任意目录）：
    python .trae/scripts/check_intent_template.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, backticked_paths, clarification_fields, enable_utf8_stdout, exists,
    field_names_from_marker, front_matter_keys, placeholder_hits, read_text,
    section_titles,
)

TEMPLATE = ".trae/templates/INTENT.md"
HOME_README = "Docs/intent/README.md"

FIELD_NAMES = [
    "id", "title", "date", "author", "origin", "status",
    "Problem（问题与背景）", "Users and scenarios（用户与场景）",
    "Expected outcome（预期结果）", "Core flow（核心流程）",
    "Constraints and non-goals（约束与非目标）",
    "Open questions（开放问题）", "Convergence verdict（收敛判定）",
    "Decision record（决策记录）", "Next step（下一步）",
]
CONVERGENCE_QUESTIONS = ["有价值吗", "值得做吗", "可以做吗"]
STATUS_VALUES = ["accepted", "rejected", "needs-more-info"]

# 下界锚：必需的小节与元数据键**来自契约本身**，不从被测模板反推。
# 只做「注记 == 模板正文」的判据挡不住协同缩表——把模板正文、注记、028 的列表与
# 本脚本常量一起删一个字段时等号两边同步下移，仍然自洽（独立复核第二轮 B-4 实测降到 14）。
# 这与 check_intent_capturer_agent.REQUIRED_SECTIONS 同一口径：规格写在守门里，不由产物推导。
REQUIRED_BODY_SECTIONS = [
    "Problem（问题与背景）", "Users and scenarios（用户与场景）",
    "Expected outcome（预期结果）", "Core flow（核心流程）",
    "Constraints and non-goals（约束与非目标）", "Open questions（开放问题）",
    "Convergence verdict（收敛判定）", "Decision record（决策记录）",
    "Next step（下一步）",
]
REQUIRED_META_KEYS = ["id", "title", "date", "author", "origin", "status"]


def main():
    enable_utf8_stdout()
    c = Checks("check_intent_template")

    for rel in (TEMPLATE, HOME_README):
        if not c.check(exists(rel), "file exists: " + rel):
            return c.finish()

    tpl = read_text(TEMPLATE)

    sections = section_titles(tpl)
    missing_sec = [s for s in REQUIRED_BODY_SECTIONS if s not in sections]
    extra_sec = [s for s in sections if s not in REQUIRED_BODY_SECTIONS]
    c.check(not missing_sec and not extra_sec,
            "template body has exactly the {0} required ## sections".format(
                len(REQUIRED_BODY_SECTIONS)),
            "measured={0} missing={1} extra={2}".format(len(sections), missing_sec, extra_sec))

    missing_meta = [k for k in REQUIRED_META_KEYS if k not in front_matter_keys(tpl)]
    c.check(not missing_meta,
            "template front matter has the {0} required metadata keys".format(
                len(REQUIRED_META_KEYS)),
            "missing={0}".format(missing_meta))
    readme = read_text(HOME_README)

    missing_fields = [f for f in FIELD_NAMES if f not in tpl]
    c.check(not missing_fields,
            "template declares all {0} field names".format(len(FIELD_NAMES)),
            "measured={0}; missing={1}".format(len(FIELD_NAMES), missing_fields))

    missing_q = [q for q in CONVERGENCE_QUESTIONS if q not in tpl]
    c.check(not missing_q, "convergence verdict has the three questions",
            "measured={0}; missing={1}".format(len(CONVERGENCE_QUESTIONS), missing_q))
    c.check("结论：" in tpl and "证据：" in tpl,
            "each question pairs a verdict with evidence")

    missing_st = [s for s in STATUS_VALUES if s not in tpl]
    c.check(not missing_st, "decision record covers all three terminal states",
            "missing={0}".format(missing_st))
    c.check("否决理由" in tpl, "rejected branch requires a reason field")

    fields = clarification_fields()
    if c.check(fields is not None, "clarification field list is readable"):
        gaps = [f for f in fields if f not in tpl]
        c.check(not gaps, "template covers every clarification field",
                "measured={0}; missing={1}".format(len(fields), gaps))

    marker = field_names_from_marker(tpl)
    c.check(marker is not None and marker == set(FIELD_NAMES),
            "FIELDS marker lists exactly the declared field names",
            "measured={0}; diff={1}".format(
                None if marker is None else len(marker),
                None if marker is None else sorted(marker.symmetric_difference(set(FIELD_NAMES)))))

    # 独立判据：注记必须等于「模板自己的 6 个元数据键 + 模板自己的全部 ## 标题」。
    # 上面那条比的是脚本里的常量，只能证明「两处常量一致」——协同缩表（正文与注记与常量
    # 一起删掉一个字段）可以零告警地降字段（独立复核 C-4 实测）。这条读模板正文本身。
    derived = front_matter_keys(tpl) | set(section_titles(tpl))
    c.check(marker is not None and marker == derived,
            "FIELDS marker equals the template own metadata keys + ## titles",
            "marker={0} derived={1} diff={2}".format(
                None if marker is None else len(marker), len(derived),
                None if marker is None else sorted(marker.symmetric_difference(derived))))

    c.check("INTENT-YYYY-MM-DD-<kebab-case>" in readme,
            "home README states the file naming rule")
    c.check("requirement-decomposer" in readme, "home README states the handoff target")
    missing_st_readme = [s for s in STATUS_VALUES if s not in readme]
    c.check(not missing_st_readme, "home README documents the status values",
            "missing={0}".format(missing_st_readme))
    for key in (".trae/tasks", "Docs/01-demand", "Docs/intent"):
        c.check(key in readme, "home README states source of truth: " + key)

    dead = sorted({p for p in backticked_paths(readme) if not exists(p)})
    c.check(not dead, "every path referenced by the home README exists",
            "measured_dead={0}".format(dead))

    bad = {rel: placeholder_hits(read_text(rel)) for rel in (TEMPLATE, HOME_README)
           if placeholder_hits(read_text(rel))}
    c.check(not bad, "no placeholder tokens", "measured={0}".format(bad))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
