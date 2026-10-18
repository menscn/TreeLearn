"""校验 intent-capturer 角色（T-2026-09-21-028）。

用法（从任意目录）：
    python .codex/scripts/check_intent_capturer_agent.py

字段集合与小节集合都不在本脚本里手写，唯一权威是 .codex/templates/INTENT.md：
- 字段集合 = 模板尾部 <!-- FIELDS: ... --> 注解除列的集合；
- 小节集合 = 模板正文的 ## 标题集合。
脚本另做一次不共享该解析器状态的原文扫描（每个字段名必须同时以反引号形式出现在
「输出格式」小节里），避免判据与豁免依据同源。
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, enable_utf8_stdout, exists, field_names_from_marker,
    max_overlap_with_files, placeholder_hits, read_text, section_body, section_titles,
)

AGENT = ".codex/agents/intent-capturer.md"
REFERENCE_AGENT = ".codex/agents/verifier.md"
TEMPLATE = ".codex/templates/INTENT.md"
PM_SKILL = ".codex/skills/pm-discovery/SKILL.md"

REQUIRED_SECTIONS = ["定位与权限", "触发时机", "执行前必读", "职责清单", "输出格式", "禁区"]
REQUIRED_READS = [
    ".codex/context/project-brief.md",
    ".codex/skills/pm-discovery/SKILL.md",
    ".codex/templates/INTENT.md",
]
READONLY_SCOPES = ["android/**", "frontend/**", "backend/**", ".codex/tasks/**"]
WRITABLE_SCOPE = "Docs/intent/**"
TERMINAL_STATES = ["accepted", "rejected", "needs-more-info"]
OVERLAP_N = 60
MAX_OVERLAP_HITS = 1
# 只有 60 字一档挡不住「抄一整句」：60 字的整句恰好只产生 1 个窗口，
# 而 ≤1 是允许的（实测：把 pm-discovery 的 59 字原文贴进来，60 字档完全不响）。
# 因此再加一档更短的窗口，两档都要过。
SHORT_OVERLAP_N = 40
# 40 字档收成 0 命中：把它当「允许抄 2 窗口」时，41 字的整句照抄恰好只产生 2 个窗口，
# 于是「抄一句」仍能过（独立复核用 41 字粘贴实测打穿过）。当前正文实测 0 命中，
# 最长公共连续串只有 24 字（`交给requirement-decomposer`），收紧不会误伤。
MAX_SHORT_OVERLAP_HITS = 0

# 「背景缺口上报」这类条款必须写成禁令。纯关键词判定挡不住语义取反：
# 「直接按常识补一个合理的答案，不必标注未知项，也不要报告缺口」同样含
# 未知项 + 缺口，却把规则反过来（独立复核实测打穿过）。
PROHIBITION_WORDS = ("不得", "不许", "禁止")
# 同行三词判定仍挡不住「否定式同行禁令」：
# 「把它登记为「未知项」并显式报告缺口，「不得编造」这条规矩不再适用」含全部三词，
# 却把禁令本身废掉了（独立复核第二轮实测打穿）。因此再钉两条：禁令必须逐字保留，
# 且该行不得出现把禁令否掉的措辞。
GAP_CANONICAL_PROHIBITION = "不得凭印象补一个看起来合理的答案"
# 「否定式绕过」的实测形态（独立复核第二轮 A2–A5、第三轮 B1/B2）：把禁令换一个同义词废掉
# （作废 / 失效 / 例外 / 忽略 / 允许按印象补齐 / 放宽 / 废止 / 取消 / 停止 / 无约束 / 免于 / 松绑 / 解除），
# 或把降级写在**另一行**。据此三条改动：
# ① 线索词从 4 个词扩成一族正则；
# ② 扫描范围从「只取第一行」扩到**全文每一行**——把否定写在「不含 缺口/未知项 的行」上曾是耦合洞
#    （独立复核第三轮 B1：`- 上述规则于赶工期时作废。` rc=0，尽管 `作废` 本来就在名单里——
#    因为它只扫「提到 缺口/未知项 的行」，而这一行两个词都没提）；
# ③ 该扫描对当前正文零命中（实测），所以放宽到全文不会误报。
# 它仍然是**名单制启发式**：未列入名单的同义改写仍可能通过。这一点进验收标准的 Gaps，
# 不得当成「禁令语义不会被反转」的保证。
NEGATION_OF_RULE_RE = re.compile(
    r"不再|不予|无需|不必|不适用|作废|失效|例外|忽略|跳过|允许|可以按|不受此限"
    r"|放宽|废止|取消|停止|无约束|免于|松绑|解除")


def bare(title):
    """去掉小节标题里的括注，用于跨文件比对小节覆盖。"""
    return re.sub(r"（[^）]*）", "", title).strip()


def backticked_tokens(text):
    return [m.group(1).strip() for m in re.finditer(r"`([^`]+)`", text)]


def prohibition_line(text, terms, words=PROHIBITION_WORDS):
    """返回同时含全部 terms 与任一禁止词的那一行；没有则返回 None。"""
    for line in text.splitlines():
        if all(t in line for t in terms) and any(w in line for w in words):
            return line
    return None


def main():
    enable_utf8_stdout()
    c = Checks("check_intent_capturer_agent")

    for rel in (AGENT, REFERENCE_AGENT, TEMPLATE, PM_SKILL):
        if not c.check(exists(rel), "file exists: " + rel):
            return c.finish()

    text = read_text(AGENT)
    titles = section_titles(text)
    bare_titles = set(bare(t) for t in titles)

    missing = [s for s in REQUIRED_SECTIONS if s not in set(titles)]
    c.check(not missing, "required sections present",
            "measured={0}; missing={1}".format(len(REQUIRED_SECTIONS), missing))

    ref_titles = section_titles(read_text(REFERENCE_AGENT))
    uncovered = [t for t in ref_titles if bare(t) not in bare_titles]
    c.check(not uncovered, "section set covers " + REFERENCE_AGENT,
            "reference={0}; uncovered={1}".format(len(ref_titles), uncovered))

    perm = section_body(text, "定位与权限")
    if c.check(perm is not None, "permission section is readable"):
        c.check(WRITABLE_SCOPE in perm and "可写" in perm,
                "declares writable scope " + WRITABLE_SCOPE)
        missing_ro = [s for s in READONLY_SCOPES if s not in perm]
        c.check(not missing_ro and "只读" in perm, "declares read-only scopes",
                "missing={0}".format(missing_ro))

    pre = section_body(text, "执行前必读")
    if c.check(pre is not None, "prerequisite-reading section is readable"):
        missing_ref = [p for p in REQUIRED_READS if p not in pre]
        c.check(not missing_ref,
                "names all {0} prerequisite files".format(len(REQUIRED_READS)),
                "missing={0}".format(missing_ref))

    for rel in REQUIRED_READS:
        c.check(exists(rel), "prerequisite path exists on disk: " + rel)

    tpl = read_text(TEMPLATE)
    template_fields = field_names_from_marker(tpl)
    agent_fields = field_names_from_marker(text, "OUTPUT-FIELDS:")
    if c.check(template_fields is not None and agent_fields is not None,
               "both field markers are readable",
               "template={0} agent={1}".format(
                   None if template_fields is None else len(template_fields),
                   None if agent_fields is None else len(agent_fields))):
        diff = sorted(agent_fields.symmetric_difference(template_fields))
        c.check(not diff, "agent output field set equals the template FIELDS marker",
                "agent={0} template={1} diff={2}".format(
                    len(agent_fields), len(template_fields), diff))

        out = section_body(text, "输出格式")
        if c.check(out is not None, "output-format section is readable"):
            tokens = set(backticked_tokens(out))
            uncovered_fields = sorted(f for f in template_fields if f not in tokens)
            c.check(not uncovered_fields,
                    "every template field appears backticked in the output-format section",
                    "measured={0}; missing={1}".format(len(template_fields), uncovered_fields))

            anchor = out.find("**正文字段")
            marker_at = out.find("<!--")
            listed = set()
            if anchor >= 0 and marker_at > anchor:
                listed = set(backticked_tokens(out[anchor:marker_at]))
            template_sections = set(section_titles(tpl))
            c.check(listed == template_sections,
                    "listed body sections equal the template ## titles",
                    "template={0} agent={1} diff={2}".format(
                        len(template_sections), len(listed),
                        sorted(listed.symmetric_difference(template_sections))))

    for st in TERMINAL_STATES:
        c.check(st in text, "handles terminal state: " + st)
    c.check("移交清单" in text, "accepted branch produces a handoff checklist")
    c.check("requirement-decomposer" in text, "handoff target is requirement-decomposer")
    c.check("不得删除" in text, "rejected branch keeps the intent file")
    gap_line = prohibition_line(text, ("未知项", "缺口"))
    c.check(gap_line is not None,
            "background-gap escalation is written as a prohibition in one clause",
            "measured={0}".format(gap_line))
    c.check(GAP_CANONICAL_PROHIBITION in text,
            "background-gap escalation keeps the canonical prohibition clause",
            "measured={0}".format(GAP_CANONICAL_PROHIBITION in text))
    hedged = [line for line in text.splitlines() if NEGATION_OF_RULE_RE.search(line)]
    c.check(not hedged,
            "no line in the agent file negates or exempts the background-gap rule",
            "measured={0}".format(hedged[:2] if hedged else "no negation marker"))

    ph = placeholder_hits(text)
    c.check(not ph, "no placeholder tokens", "measured={0}".format(ph))

    hits, detail = max_overlap_with_files(text, [PM_SKILL], OVERLAP_N)
    c.check(hits <= MAX_OVERLAP_HITS,
            "shares at most {0} run(s) of {1} chars with pm-discovery".format(
                MAX_OVERLAP_HITS, OVERLAP_N),
            "measured_hits={0} sample={1}".format(hits, detail[:1]))

    short_hits, short_detail = max_overlap_with_files(text, [PM_SKILL], SHORT_OVERLAP_N)
    c.check(short_hits <= MAX_SHORT_OVERLAP_HITS,
            "shares at most {0} run(s) of {1} chars with pm-discovery".format(
                MAX_SHORT_OVERLAP_HITS, SHORT_OVERLAP_N),
            "measured_hits={0} sample={1}".format(short_hits, short_detail[:1]))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
