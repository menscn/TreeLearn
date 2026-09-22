"""校验一份 intent 实例能否直接交给 requirement-decomposer（父任务 T-2026-09-21-026 验收标准第 4 条）。

用法（从任意目录）：
    python .trae/scripts/dev/check_intent_replay.py Docs/intent/INTENT-YYYY-MM-DD-xxx.md

字段与小节都不在本脚本里手写，唯一权威是 .trae/templates/INTENT.md 的现场内容，
避免「同一条规则存两处」。退出码：0 全过；1 有断言失败；2 用法错误。
"""

import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(REPO_ROOT, ".trae", "scripts"))
from harness_check_lib import (  # noqa: E402
    Checks, enable_utf8_stdout, exists, field_names_from_marker, front_matter,
    front_matter_keys, placeholder_hits, read_text, section_titles,
)

TEMPLATE = ".trae/templates/INTENT.md"
META_KEYS = ["id", "title", "date", "author", "origin", "status"]
TERMINALS = ["accepted", "rejected", "needs-more-info"]
CONVERGENCE_QUESTIONS = ["有价值吗", "值得做吗", "可以做吗"]
MIN_SLICES = 3
# 终态必须从「终态：」这一行的行内取值读出，不能拿 TERMINALS 去全文做子串匹配：
# 模板把三种取值并列写成「终态：accepted / rejected / needs-more-info」，
# 子串匹配会把它读成 terminals == ["accepted"]，于是「不是实例的模板」反而判成实例。
# 取值必须落在同一行：`[ \t]*` 而非 `\s*`，否则「- 终态：」后换行再写取值也会被读成合规
# （独立复核第二轮实测 `- 终态：\\naccepted` 仍 rc=0）。
TERMINAL_LINE_RE = re.compile(r"^[-*][ \t]*终态：[ \t]*(\S[^\n]*?)[ \t]*$", re.M)


def terminal_states(text):
    return TERMINAL_LINE_RE.findall(text)


def main():
    enable_utf8_stdout()
    c = Checks("check_intent_replay")

    if len(sys.argv) != 2:
        print("usage: python .trae/scripts/dev/check_intent_replay.py <intent-file>")
        print("EXITCODE=2")
        return 2

    target = sys.argv[1]
    if not c.check(exists(target), "file exists: " + target):
        return c.finish()

    text = read_text(target)
    tpl = read_text(TEMPLATE)

    keys = front_matter_keys(text)
    c.check(keys == set(META_KEYS), "front matter has exactly the 6 metadata keys",
            "measured={0} diff={1}".format(len(keys), sorted(keys.symmetric_difference(set(META_KEYS)))))

    sections = section_titles(text)
    tpl_sections = section_titles(tpl)
    c.check(sections == tpl_sections, "sections equal the template sections, in order",
            "template={0} instance={1} diff={2}".format(
                len(tpl_sections), len(sections),
                sorted(set(sections).symmetric_difference(set(tpl_sections)))))

    tpl_fields = field_names_from_marker(tpl) or set()
    missing = sorted(f for f in tpl_fields if f not in text)
    c.check(not missing, "every field named by the template marker appears in the instance",
            "measured={0} missing={1}".format(len(tpl_fields), missing))

    for q in CONVERGENCE_QUESTIONS:
        if "### " + q not in text:
            c.fail("convergence verdict present: " + q)
            continue
        body = text.split("### " + q, 1)[1].split("###", 1)[0]
        c.check("结论：" in body and "证据：" in body,
                "verdict pairs a conclusion with evidence: " + q)

    terminals = terminal_states(text)
    c.check(len(terminals) == 1 and terminals[0] in TERMINALS,
            "decision record states exactly one terminal state",
            "measured={0}".format(terminals))

    # 独立判据：终态行的取值必须与 front matter 的 status 一致。它读的是另一处来源，
    # 因此当「终态行」的解析被措辞骗过时仍能拦下。
    status = front_matter(text).get("status")
    c.check(terminals == [status] and status in TERMINALS,
            "front matter status equals the decision-record terminal state",
            "status={0!r} measured={1}".format(status, terminals))

    if terminals == ["accepted"]:
        tail = text.split("### 移交清单", 1)[-1]
        slices = re.findall(r"^- 切片 \d", tail, re.M)
        c.check(len(slices) >= MIN_SLICES,
                "handoff checklist names >= {0} slices".format(MIN_SLICES),
                "measured={0}".format(len(slices)))
        c.check("依赖顺序" in tail, "handoff checklist states the dependency order")
        c.check("不可并行" in tail, "handoff checklist states what cannot run in parallel")

    ph = placeholder_hits(text)
    c.check(not ph, "no placeholder tokens", "measured={0}".format(ph))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
