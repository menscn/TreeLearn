"""loop-watcher 角色与它的成对守门（ROADMAP C2）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_loop_watcher_agent.py

为什么需要这支脚本：C2 要监督的是**回路本身**，而回路失效的形状在本仓库已经登记过三条
（D8 一对既有 flaky、D11 core 档间歇转红、D12 最重一条贴着 300s 上限）——共同走向都是
「重跑到绿」。所以这里不判角色写得好不好看，只判四件真会坏的事：
① 阈值必须是一个能解析、能算、且不超过「还在监督」上限的整数，写成「若干次」等于没写；
② 重复计数必须挂在**失败身份**上而不是挂在水泵次数上，否则换个用例红会被读成仍在收敛；
③ 「没判」（超时、脚本不在场、输出非 UTF-8）必须被排除在重复计数之外，否则仪器坏会被
   读成收敛失败，而修仪器的动作永远不会发生（D12 的形状）；
④ 收敛结论必须带那一轮带判据行的原始读数，否则「应该好了」就是无证据宣布。

上报四字段的小节名与字段名全部从 `.codex/tasks/_TEMPLATE.md` 的 LOOP 小节现场派生
（片 025/026 的同一口径：模板改名而角色没跟，就是死指针）；抄检窗口复用
`.codex/scripts/harness_check_lib.py` 里已有的派生，不在这里写第二份。

退出码：0 = 通过；1 = 有 FAIL。（注入用例在 .codex/scripts/dev/checkers_selftest.py 的
    `watch:*` 条目里，判据红绿由退出码体现。）
"""

import os
import re
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, agent_section_names, citable_paths, enable_utf8_stdout, exists,
    max_overlap_with_files, placeholder_hits, read_text, repo_path)

AGENT_MD = ".codex/agents/loop-watcher.md"
AGENT_TOML = ".codex/agents/loop-watcher.toml"
REFERENCE_ROLE = ".codex/agents/code-reviewer.md"
VERIFIER_ROLE = ".codex/agents/verifier.md"
SIMPLIFIER_ROLE = ".codex/agents/code-simplifier.md"
TASK_TEMPLATE = ".codex/tasks/_TEMPLATE.md"
REGRESSION_RULE = ".codex/rules/regression.md"
VERIFICATION_RULE = ".codex/rules/harness-verification.md"
OBSERVABILITY_RULE = ".codex/rules/observability.md"
GUARD_ROSTER = ".codex/scripts/run_all_guards.py"

INPUTS = (AGENT_MD, AGENT_TOML, REFERENCE_ROLE, VERIFIER_ROLE, SIMPLIFIER_ROLE,
          TASK_TEMPLATE, REGRESSION_RULE, VERIFICATION_RULE, OBSERVABILITY_RULE,
          GUARD_ROSTER)

# 裸下限：被检集合不能给自己定下限。code-reviewer.md 的骨架是两种形状拼起来的——实测
# 4 个 `<tag>` 小节 + 5 个二级标题，与 B5 同一判定，下限取 6（高于任一单独形状）。
MIN_ROLE_SECTIONS = 6
# 阈值要在两处各自承重：契约里那条「连续 N 次即停」与停止条件清单第 1 条。只留一处时
# 另一处删空仍然绿，所以数「带整数的阈值行」而不是数「阈值」这个词。
MIN_THRESHOLD_LINES = 2
# 阈值上限：超过这个数，「监督回路」就退化成「多试几次」——与本角色存在的理由相反。
MAX_THRESHOLD_VALUE = 5
# 失败身份的两半：身份的定义处，和「按身份而不是按次数」的计数口径处。
MIN_IDENTITY_LINES = 2
# 「没判」必须同时被写成两件事：不进重复计数，以及它不是判红。只写一半时仪器坏了仍会被
# 当成失败重复——那正是 D12 里「把没判当红」的形状。
MIN_NOTJUDGED_LINES = 2
# 停止条件清单的条数下限：角色正文列了几条，注入删到比这还少就必须响。取 5 是因为
# 判据面被动过与需要用户独有事实这两条各自独立，删掉任一条剩下的仍是一句像样的话。
MIN_STOP_ITEMS = 5
# 上报字段：字段名从模板 LOOP 小节派生，模板被抽干时「每个字段都在角色里出现」会空集通过。
MIN_LOOP_FIELDS = 4
MIN_CONVERGENCE_EVIDENCE_LINES = 2
MIN_VERBATIM_LINES = 2
MIN_ROUND_LINES = 2
# 角色引用其它角色必须用反引号那一份计数：只数裸名字时，删光分工句、只剩一条路径引用也算「提到」。
MIN_ROLE_REF_MENTIONS = 2
MIN_CITED_PATHS = 6
# 与本角色共享措辞、但绝不能共享整段的三份正文：抄过来就有第二份权威。
OVERLAP_RUNS = 60

THRESHOLD_RE = re.compile(r"连续\s*(\d+)\s*次")
IDENTITY_RE = re.compile(r"失败身份")
IDENTITY_PARTS_RE = re.compile(r"命令|判据行|用例名|断言|读数")
NOTJUDGED_RE = re.compile(r"没判|nothing was judged|超时")
NOTJUDGED_TREAT_RE = re.compile(r"不进|不算|不计|不是|先修")
STOP_ITEM_RE = re.compile(r"^\s{2,}\d+\.\s+\S", re.M)
CONVERGENCE_RE = re.compile(r"收敛")
EVIDENCE_RE = re.compile(r"读数|判据行|证据|命令")
FORBID_RE = re.compile(r"不得|禁止|不许")
VERBATIM_RE = re.compile(r"逐字")
PASTE_RE = re.compile(r"粘贴|读数")
ROUND_RE = re.compile(r"一轮")
JUDGEMENT_LINE_RE = re.compile(r"判据行")
EXITCODE_RE = re.compile(r"退出码")
PRIVACY_RE = re.compile(r"prompt|正文|凭据|API Key")
READONLY_RE = re.compile(r"只读")
# 拒绝性措辞按判据分档：FORBID_RE 管「不得改判据面」这类硬禁令（正文用「不得/禁止/不许」），
# 而隐私与只读两条在角色正文里的自然写法是「不贴 prompt」「不改测试期望」——把它们也要求成
# 「禁止」只会逼正文说假话，所以各自配一条与语义同宽的形状。下限仍是 1 行，注入即响。
PRIVACY_FORBID_RE = re.compile(r"不得|禁止|不许|不贴|不把")
READONLY_FORBID_RE = re.compile(r"不得|禁止|不许|不改|不修")
VERIFIER_REF_RE = re.compile(r"`verifier`")
SIMPLIFIER_REF_RE = re.compile(r"`code-simplifier`")
REVIEWER_REF_RE = re.compile(r"`code-reviewer`")
LOOP_HEAD_RE = re.compile(r"^###\s+(LOOP[^\n]*)$", re.M)
LOOP_FIELD_RE = re.compile(r"^-\s+([A-Za-z][A-Za-z /—-]*?):", re.M)


def loop_title(template_text):
    titles = LOOP_HEAD_RE.findall(template_text)
    return titles[0].strip() if titles else None


def loop_fields(template_text, title):
    """从模板 LOOP 小节里现场派生字段名；小节找不到就返回空列表（由调用方判仪器故障）。

    窗口必须同时停在下一个 `###` 与下一个 `##` 上：模板里 LOOP 小节后面紧跟着
    `### TDD or sizing exception`，只找 `##` 会把 exception 的四个字段名也当成 LOOP 的字段，
    于是「角色必须逐字命名模板字段」这条判据变成要求角色写出别人的字段（实测多派生 4 个）。
    """
    head = "### " + title
    start = template_text.find(head)
    if start < 0:
        return []
    rest = template_text[start + len(head):]
    stops = [i for i in (rest.find("\n## "), rest.find("\n### ")) if i >= 0]
    body = rest if not stops else rest[:min(stops)]
    return [m.strip() for m in LOOP_FIELD_RE.findall(body)]


def threshold_lines(text):
    return [l.strip() for l in text.splitlines() if THRESHOLD_RE.search(l)]


def identity_lines(text):
    return [l.strip() for l in text.splitlines()
            if IDENTITY_RE.search(l) and IDENTITY_PARTS_RE.search(l)]


def notjudged_lines(text):
    return [l.strip() for l in text.splitlines()
            if NOTJUDGED_RE.search(l) and NOTJUDGED_TREAT_RE.search(l)]


def convergence_evidence_lines(text):
    return [l.strip() for l in text.splitlines()
            if CONVERGENCE_RE.search(l) and EVIDENCE_RE.search(l)]


def evidence_free_prohibition_lines(text):
    return [l.strip() for l in text.splitlines()
            if FORBID_RE.search(l) and CONVERGENCE_RE.search(l) and EVIDENCE_RE.search(l)]


def verbatim_lines(text):
    return [l.strip() for l in text.splitlines() if VERBATIM_RE.search(l) and PASTE_RE.search(l)]


def round_lines(text):
    return [l.strip() for l in text.splitlines() if ROUND_RE.search(l) and JUDGEMENT_LINE_RE.search(l)]


def privacy_lines(text):
    return [l.strip() for l in text.splitlines()
            if PRIVACY_RE.search(l) and PRIVACY_FORBID_RE.search(l)]


def main():
    enable_utf8_stdout()
    c = Checks("check_loop_watcher_agent")

    missing_inputs = [p for p in INPUTS if not exists(p)]
    if not c.check(not missing_inputs, "the ten inputs exist",
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

    tlines = threshold_lines(agent)
    values = sorted({int(m.group(1)) for l in tlines for m in THRESHOLD_RE.finditer(l)})
    sane = [v for v in values if 1 <= v <= MAX_THRESHOLD_VALUE]
    c.check(len(tlines) >= MIN_THRESHOLD_LINES and bool(values) and len(sane) == len(values),
            "agent states a parseable repeat threshold within a supervisable range",
            "lines={0} floor={1} values={2} sane={3} max={4}".format(
                len(tlines), MIN_THRESHOLD_LINES, values, sane, MAX_THRESHOLD_VALUE))

    ilines = identity_lines(agent)
    c.check(len(ilines) >= MIN_IDENTITY_LINES,
            "agent defines a failure identity from command, judgement line and reading",
            "measured={0} floor={1}".format(len(ilines), MIN_IDENTITY_LINES))

    nlines = notjudged_lines(agent)
    c.check(len(nlines) >= MIN_NOTJUDGED_LINES,
            "agent keeps 没判 (timeout/absent script) out of the repeat count",
            "measured={0} floor={1}".format(len(nlines), MIN_NOTJUDGED_LINES))

    stop_items = len(STOP_ITEM_RE.findall(agent))
    c.check(stop_items >= MIN_STOP_ITEMS,
            "agent lists at least {0} distinct stop conditions".format(MIN_STOP_ITEMS),
            "measured={0} floor={1}".format(stop_items, MIN_STOP_ITEMS))

    title = loop_title(template)
    if not c.check(bool(title), "task template still declares the LOOP subsection",
                   "title={0!r}".format(title)):
        return c.finish()
    fields = loop_fields(template, title)
    if not c.check(len(fields) >= MIN_LOOP_FIELDS,
                   "the LOOP subsection declares its report fields",
                   "measured={0} floor={1} fields={2}".format(len(fields), MIN_LOOP_FIELDS,
                                                              fields)):
        return c.finish()
    unnamed = [f for f in fields if f not in agent]
    c.check(not unnamed, "agent names every report field from the template verbatim",
            "fields={0} missing={1}".format(len(fields), unnamed))

    clines = convergence_evidence_lines(agent)
    c.check(len(clines) >= MIN_CONVERGENCE_EVIDENCE_LINES,
            "agent ties any convergence claim to a printed reading",
            "measured={0} floor={1}".format(len(clines), MIN_CONVERGENCE_EVIDENCE_LINES))

    forbids = evidence_free_prohibition_lines(agent)
    c.check(bool(forbids), "agent forbids declaring convergence without evidence",
            "measured={0} floor=1".format(len(forbids)))

    vlines = verbatim_lines(agent)
    c.check(len(vlines) >= MIN_VERBATIM_LINES,
            "agent pastes readings verbatim instead of paraphrasing them",
            "measured={0} floor={1}".format(len(vlines), MIN_VERBATIM_LINES))

    rlines = round_lines(agent)
    exitcode_hits = len(EXITCODE_RE.findall(agent))
    c.check(len(rlines) >= MIN_ROUND_LINES and exitcode_hits >= 2,
            "a round requires its own judgement line and exit code",
            "round_lines={0} floor={1} exitcode_hits={2}".format(
                len(rlines), MIN_ROUND_LINES, exitcode_hits))

    plines = privacy_lines(agent)
    c.check(bool(plines), "agent forbids pasting bodies or credentials into the report",
            "measured={0} floor=1".format(len(plines)))

    readonly_lines = [l.strip() for l in agent.splitlines()
                      if READONLY_RE.search(l) and READONLY_FORBID_RE.search(l)]
    c.check(bool(readonly_lines), "agent declares itself read-only over code and judgement assets",
            "measured={0} floor=1".format(len(readonly_lines)))

    landing = [l.strip() for l in agent.splitlines() if "只写" in l]
    c.check(bool(landing) and all(title in l for l in landing),
            "agent declares its only writable landing is that subsection",
            "landing_lines={0} all_named={1}".format(
                len(landing), bool(landing) and all(title in l for l in landing)))

    verifier_refs = len(VERIFIER_REF_RE.findall(agent))
    reviewer_refs = len(REVIEWER_REF_RE.findall(agent))
    simplifier_refs = len(SIMPLIFIER_REF_RE.findall(agent))
    c.check(verifier_refs >= MIN_ROLE_REF_MENTIONS and reviewer_refs >= MIN_ROLE_REF_MENTIONS
            and simplifier_refs >= MIN_ROLE_REF_MENTIONS,
            "agent defers acceptance, review and consolidation to the existing roles",
            "verifier_refs={0} reviewer_refs={1} simplifier_refs={2} floor={3}".format(
                verifier_refs, reviewer_refs, simplifier_refs, MIN_ROLE_REF_MENTIONS))

    cited = sorted(set(citable_paths(agent)))
    if not c.check(len(cited) >= MIN_CITED_PATHS,
                   "agent cites >= {0} checkable repo paths".format(MIN_CITED_PATHS),
                   "measured={0} floor={1} cited={2}".format(len(cited), MIN_CITED_PATHS, cited)):
        return c.finish()
    dead_paths = sorted(p for p in cited if not exists(p))
    c.check(not dead_paths, "every path cited by the role exists on disk",
            "dead={0} cited={1}".format(dead_paths, len(cited)))

    hits, detail = max_overlap_with_files(
        agent, [REGRESSION_RULE, VERIFICATION_RULE, OBSERVABILITY_RULE], OVERLAP_RUNS)
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
