"""code-simplifier 角色与它的成对守门（ROADMAP B5）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_code_simplifier_agent.py

为什么需要这支脚本：B5 要的是一个**会写代码**的角色，而前面三个（verifier、code-reviewer、
codebase-researcher）全是只读的。会写代码的收敛角色有两个真实的失效方向，而且都是本仓库
登记过的病：① 「行为不变」变成一句自我声明——没人要求它给出改前改后同一条命令的读数，
于是删掉一段还留着注释的实现也算「不变」；② 为了让指标或测试变绿去动判据自己
（D4 记的「仪器给自己开后门」、regression.md 第 6 条的「不得仅为让测试变绿而削弱断言」）。
所以这里判的形状是：两侧各有一个测量点、两侧必须是同一套、两侧都必须全绿、
判据面本身不许被它动、死代码只能删不能藏、落点只能是任务卡的 REFACTOR 小节。

小节集、可引路径、抄检窗口三件事全部复用 `.codex/scripts/harness_check_lib.py` 里已有的
派生（片 026 已把小节集上收一次）。本片顺手把 B4 里那份 `cited_paths()` 也上收成
`citable_paths()`：一个专门消灭第二份实现的角色，自己的守门不该留着第二份实现。

退出码：0 = 通过；1 = 有 FAIL。（注入用例在 .codex/scripts/dev/checkers_selftest.py 的
    `simplify:*` 条目里，判据红绿由退出码体现。）
"""

import os
import re
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, agent_section_names, citable_paths, enable_utf8_stdout, exists,
    max_overlap_with_files, placeholder_hits, read_text, repo_path)

AGENT_MD = ".codex/agents/code-simplifier.md"
AGENT_TOML = ".codex/agents/code-simplifier.toml"
REFERENCE_ROLE = ".codex/agents/code-reviewer.md"
MAINTAINABILITY_RULE = ".codex/rules/maintainability.md"
REGRESSION_RULE = ".codex/rules/regression.md"
TASK_TEMPLATE = ".codex/tasks/_TEMPLATE.md"

INPUTS = (AGENT_MD, AGENT_TOML, REFERENCE_ROLE, MAINTAINABILITY_RULE,
          REGRESSION_RULE, TASK_TEMPLATE)

# 裸下限：被检集合不能给自己定下限。code-reviewer.md 的骨架是两种形状拼起来的——实测
# 4 个 `<tag>` 小节 + 5 个二级标题。下限必须高于任一单独形状（这里取 6），否则把其中一种
# 形状整体改形，总数仍从 9 掉到 5 或 4 而越过旧的 4，「沿用同一套角色骨架」这条就悄悄变成
# 半个骨架在比；下限取 5 也挡不住尖括号那一形状消失（5 不 < 5）。
MIN_ROLE_SECTIONS = 6
# 「改前/改后」是这条判据的两半：只有一侧在场时，另一侧删空仍然报绿（片 024 的同一课）。
MIN_BEFORE_MENTIONS = 2
MIN_AFTER_MENTIONS = 2
# 「同一套测试」「同一条命令」——两侧不是同一套时，改前改后各自绿也证不了行为不变。
MIN_SAME_MENTIONS = 2
MIN_GREEN_MENTIONS = 2
# 禁令必须落在判据面上：只写「不要改坏代码」的禁令谁都能满足，所以要数「禁令 × 判据面词汇」
# 同时出现在一行上的行数。下限 2 是因为「不得改测试」与「不得放宽判据」是两件事。
MIN_PROHIBITION_LINES = 2
# 「只能删」是本角色正文里删除义务的共用标记：数「死代码词汇 × 只能删」同在一行的行数，
# 比单数一个名词更难被「留个注释」糊过去，也让一条注入就能抽干它。
MIN_DEAD_LINES = 2
# 「不许把死代码注释保留」是 B5 目标里点名的那一半，单独成一条：整段死代码措辞都在场
# 但「注释」这一句被删掉时，上面的计数判据仍然绿。
MIN_COMMENT_PROHIBITIONS = 1
# 角色引用了哪些路径就要有哪些路径在场；删到比这还少时「死指针」判据会因无话可判而空集通过。
# 角色名用反引号那一份计数：路径引用 `.codex/agents/verifier.md` 也算「提到 verifier」，
# 只数裸名字的话，删光分工句、只剩一条路径引用，这条判据照样绿。
MIN_ROLE_REF_MENTIONS = 2
MIN_CITED_PATHS = 4
# 与本角色共享措辞、但绝不能共享整段的两份规则正文：抄过来就有第二份权威。
OVERLAP_RUNS = 60

BEFORE_RE = re.compile(r"改前")
AFTER_RE = re.compile(r"改后")
SAME_RE = re.compile(r"同一")
GREEN_RE = re.compile(r"全绿")
DEAD_RE = re.compile(r"无生产者|不可达|死代码")
DELETE_RE = re.compile(r"只能删")
PROHIBITION_RE = re.compile(r"不得|禁止|不许")
JUDGEMENT_RE = re.compile(r"测试|判据|断言|白名单|下限")
# 任务模板里的落点小节标题从这里现场派生：模板改名而角色没跟，就是死指针（片 026 的同一形状）。
VERIFIER_REF_RE = re.compile(r"`verifier`")
REVIEWER_REF_RE = re.compile(r"`code-reviewer`")
REFACTOR_HEAD_RE = re.compile(r"^###\s+(REFACTOR[^\n]*)$", re.M)


def refactor_title(template_text):
    titles = REFACTOR_HEAD_RE.findall(template_text)
    return titles[0].strip() if titles else None


def prohibition_lines(text):
    return [l.strip() for l in text.splitlines()
            if PROHIBITION_RE.search(l) and JUDGEMENT_RE.search(l)]


def dead_deletion_lines(text):
    return [l.strip() for l in text.splitlines() if DEAD_RE.search(l) and DELETE_RE.search(l)]


def comment_prohibition_lines(text):
    return [l.strip() for l in text.splitlines()
            if "注释" in l and PROHIBITION_RE.search(l)]


def main():
    enable_utf8_stdout()
    c = Checks("check_code_simplifier_agent")

    missing_inputs = [p for p in INPUTS if not exists(p)]
    if not c.check(not missing_inputs, "the six inputs exist",
                   "missing={0}".format(missing_inputs)):
        return c.finish()

    agent = read_text(AGENT_MD)
    reference = read_text(REFERENCE_ROLE)
    template = read_text(TASK_TEMPLATE)

    needed = agent_section_names(reference)
    if not c.check(len(needed) >= MIN_ROLE_SECTIONS,
                   "reference role exposes a usable set of section names",
                   "measured={0} floor={1} names={2}".format(len(needed), MIN_ROLE_SECTIONS,
                                                             needed)):
        return c.finish()
    missing_sections = [n for n in needed if n not in set(agent_section_names(agent))]
    c.check(not missing_sections, "agent section names cover the reference role",
            "reference_sections={0} missing={1}".format(len(needed), missing_sections))

    before = len(BEFORE_RE.findall(agent))
    after = len(AFTER_RE.findall(agent))
    c.check(before >= MIN_BEFORE_MENTIONS and after >= MIN_AFTER_MENTIONS,
            "agent names a measurement point on both sides of the change",
            "before_hits={0} floor={1} after_hits={2} floor={3}".format(
                before, MIN_BEFORE_MENTIONS, after, MIN_AFTER_MENTIONS))

    same = len(SAME_RE.findall(agent))
    c.check(same >= MIN_SAME_MENTIONS,
            "agent requires one and the same suite/command on both sides",
            "hits={0} floor={1}".format(same, MIN_SAME_MENTIONS))

    green = len(GREEN_RE.findall(agent))
    c.check(green >= MIN_GREEN_MENTIONS,
            "agent requires that suite to be green on both sides",
            "hits={0} floor={1}".format(green, MIN_GREEN_MENTIONS))

    lines = prohibition_lines(agent)
    c.check(len(lines) >= MIN_PROHIBITION_LINES,
            "agent forbids touching the judgement surface to get green",
            "measured={0} floor={1}".format(len(lines), MIN_PROHIBITION_LINES))

    dead_lines = dead_deletion_lines(agent)
    c.check(len(dead_lines) >= MIN_DEAD_LINES,
            "agent obliges deleting 无生产者/不可达 code",
            "measured={0} floor={1}".format(len(dead_lines), MIN_DEAD_LINES))

    comment_lines = comment_prohibition_lines(agent)
    c.check(len(comment_lines) >= MIN_COMMENT_PROHIBITIONS,
            "agent forbids keeping dead code as comments",
            "measured={0} floor={1}".format(len(comment_lines), MIN_COMMENT_PROHIBITIONS))

    title = refactor_title(template)
    if not c.check(bool(title), "task template still declares the REFACTOR subsection",
                   "title={0!r}".format(title)):
        return c.finish()
    c.check(title in agent, "agent names the template REFACTOR subsection verbatim",
            "title={0!r} named={1}".format(title, title in agent))

    landing = [l.strip() for l in agent.splitlines() if "只写" in l]
    c.check(bool(landing) and all(title in l for l in landing),
            "agent declares its only writable landing is that subsection",
            "landing_lines={0} all_named={1}".format(
                len(landing), bool(landing) and all(title in l for l in landing)))

    verifier_refs = len(VERIFIER_REF_RE.findall(agent))
    reviewer_refs = len(REVIEWER_REF_RE.findall(agent))
    c.check(verifier_refs >= MIN_ROLE_REF_MENTIONS and reviewer_refs >= MIN_ROLE_REF_MENTIONS,
            "agent defers acceptance and code quality to the existing roles",
            "verifier_refs={0} floor={1} reviewer_refs={2} floor={1}".format(
                verifier_refs, MIN_ROLE_REF_MENTIONS, reviewer_refs))

    cited = sorted(set(citable_paths(agent)))
    if not c.check(len(cited) >= MIN_CITED_PATHS,
                   "agent cites >= {0} checkable repo paths".format(MIN_CITED_PATHS),
                   "measured={0} floor={1} cited={2}".format(len(cited), MIN_CITED_PATHS, cited)):
        return c.finish()
    dead_paths = sorted(p for p in cited if not exists(p))
    c.check(not dead_paths, "every path cited by the role exists on disk",
            "dead={0} cited={1}".format(dead_paths, len(cited)))

    hits, detail = max_overlap_with_files(agent, [MAINTAINABILITY_RULE, REGRESSION_RULE],
                                          OVERLAP_RUNS)
    c.check(hits == 0, "role text does not restate the rules it cites",
            "measured_hits={0} sample={1}".format(hits, detail[:1]))

    placeholders = sorted(set(placeholder_hits(agent)))
    c.check(not placeholders, "role text carries no placeholder tokens",
            "hits={0}".format(placeholders))

    try:
        with open(repo_path(AGENT_TOML), "rb") as handle:
            data = tomllib.load(handle)
    except (OSError, tomllib.TOMLDecodeError) as exc:
        c.fail("agent .toml parses as a Codex agent file", repr(exc))
        return c.finish()
    instructions = data.get("developer_instructions", "")
    same_body = instructions.strip() == agent.strip()
    c.check(bool(data.get("name")) and bool(data.get("description")),
            "agent .toml declares name and description",
            "name={0} description={1}".format(bool(data.get("name")),
                                              bool(data.get("description"))))
    c.check(same_body, "agent .toml body equals the agent markdown verbatim",
            "toml_chars={0} md_chars={1} equal={2}".format(len(instructions), len(agent),
                                                           same_body))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
