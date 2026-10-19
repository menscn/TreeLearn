"""metrics-reporter 角色与它的成对守门（ROADMAP B6 的角色面，片 031）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_metrics_reporter_agent.py

为什么需要这支脚本：片 030 把「每个数字是哪条命令读出的」做成了一页能复算的报告，但那份报告只在
**有人按流程跑它**的时候才成立。这个角色的失效方向和本仓库登记过的病一一对应：① 直接引用上一页留下的
抄本——引用的其实是某个 HEAD 的旧读数；② 把 `--write` 的退出码当成「判据通过」（它只写文件，不判东西）；
③ 手抄读数、把六个字段合并成一句人话（D9 的同一族成因：抄出来的数字判的是抄写者记住了什么）；
④ 为了让报告好看去调下限、删指标行、改家族名（D4 的「仪器给自己开后门」）。所以这里判的形状是：
跑法那一行必须指向真实脚本、生成命令与复算命令必须逐字等于生成器自己拼出来的那两条、家族与落点只能从
生成器派生、重生成之后必须整跑 core 档、判据面不许动、唯一可写落点只能是报告目录。

判据面和片 030 严格分开：那一片判的是「报告里的数能不能被它自己那行命令复现」，这一片判的是
「角色正文有没有把这套流程写成可执行的约束」。家族清单（`families()`）、落点（`REPORT_DIR`）、
两条命令（`WRITE_COMMAND` / `read_command`）全部从 `metrics_report` 现场取——角色正文里写的家族
如果和生成器派生的家族不一致，那条判据必须响；两处各写一份就是第二份权威。

退出码：0 = 通过；1 = 有 FAIL。（注入用例在 .codex/scripts/dev/checkers_selftest.py 的
    `reporter:*` 条目里，判据红绿由退出码体现。）
"""

import os
import re
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, agent_section_names, citable_paths, enable_utf8_stdout, exists,
    max_overlap_with_files, placeholder_hits, read_text, repo_path)

AGENT_MD = ".codex/agents/metrics-reporter.md"
AGENT_TOML = ".codex/agents/metrics-reporter.toml"
REFERENCE_ROLE = ".codex/agents/verifier.md"
GENERATOR = ".codex/scripts/metrics_report.py"
ROADMAP = ".codex/ROADMAP.md"
CODEX_README = ".codex/README.md"
ROSTER = ".codex/scripts/run_all_guards.py"

INPUTS = (AGENT_MD, AGENT_TOML, REFERENCE_ROLE, GENERATOR, ROADMAP, CODEX_README, ROSTER)

# 裸下限：被检集合不能给自己定下限。verifier.md 的骨架是两种形状拼起来的——实测 4 个 `<tag>`
# 小节 + 1 个二级标题，下限取 5（> 任一单独形状），否则把尖括号那一形状整体改形，
# 剩下 1 个仍然 ≥ 旧的 1，「沿用同一套角色骨架」这条就悄悄变成半个骨架在比。
MIN_ROLE_SECTIONS = 5
# 「写命令在场」与「那一行指向真脚本」是两半：只查一侧时，另一侧删空仍然报绿。
MIN_REGEN_CMD_LINES = 2
MIN_PARSED_SCRIPTS = 2
# 引用数字前先重生成 / 重生成后整跑：这是本片要钉住的两条链，写一次不算形状。
MIN_REGEN_BEFORE_QUOTE_LINES = 2
MIN_RERUN_LINES = 2
MIN_READ_LINES = 2
MIN_TRANSCRIBE_LINES = 2
MIN_PROHIBITION_LINES = 2
MIN_VERBATIM_READING_LINES = 2
MIN_NOT_ACCEPTANCE_LINES = 2
MIN_READONLY_LINES = 2
# 四族由 ROADMAP 派生；下限刻意低于派生条数，好让「生成器把家族抽干」也响。
MIN_FAMILY_MENTIONS = 3
MIN_SPLIT_LINES = 2
MIN_SHAPE_ONLY_LINES = 1
MIN_CITED_PATHS = 4
# 与角色共享措辞但绝不能共享整段的两份正文：生成器 docstring 与 ROADMAP 的 B6 条目。
OVERLAP_RUNS = 60

RUN_CMD_RE = re.compile(r"python -X utf8\s+(\S+\.py)")
CMD_RE = re.compile(r"python -X utf8")
QUOTE_RE = re.compile(r"报告|读数|数字|指标")
REGEN_RE = re.compile(r"--write|重生成")
RERUN_RE = re.compile(r"整跑|重跑|--tier core|run_all_guards")
READ_RE = re.compile(r"--read")
FIELD_RE = re.compile(r"字段")
TRANSCRIBE_RE = re.compile(r"逐字|零差异|转录|手抄|原样")
READING_RE = re.compile(r"逐字|原样")
MEASURE_RE = re.compile(r"读数|OK|FAIL|EXITCODE|measured=|check\(s\) failed")
PROHIBITION_RE = re.compile(r"不得|禁止|不许|不调低|不删|不改")
TAMPER_RE = re.compile(r"下限|指标行|家族名|登记表")
SHAPE_RE = re.compile(r"复算=否")
ONLY_SHAPE_RE = re.compile(r"只判形状")
SLICE_RE = re.compile(r"另开一片|另一片")
INJECT_RE = re.compile(r"注入用例")
ACCEPTANCE_RE = re.compile(r"不写 Verdict|不宣布")
LANDING_MARK = "只写"
READ_PLACEHOLDER = "<指标名>"


def lines_matching(text, *patterns):
    return [l.strip() for l in text.splitlines()
            if all(p.search(l) for p in patterns)]


def main():
    enable_utf8_stdout()
    c = Checks("check_metrics_reporter_agent")

    missing_inputs = [p for p in INPUTS if not exists(p)]
    if not c.check(not missing_inputs, "the seven inputs exist",
                   "missing={0}".format(missing_inputs)):
        return c.finish()

    # 家族、落点与两条命令只能有一份权威：从生成器现场取，取不到就没有可比对象（那是仪器坏，不是通过）。
    try:
        import metrics_report
        families = tuple(metrics_report.families())
        landing = metrics_report.REPORT_DIR
        report = metrics_report.REPORT
        script = metrics_report.SCRIPT
        write_command = metrics_report.WRITE_COMMAND
        read_command = metrics_report.read_command(READ_PLACEHOLDER)
    except Exception as exc:  # noqa: BLE001 — 派生失败一律算仪器坏
        c.fail("metric families and the report landing derive from the generator", repr(exc))
        return c.finish()
    if not c.check(len(families) >= MIN_FAMILY_MENTIONS and bool(landing) and bool(report),
                   "metric families and the report landing derive from the generator",
                   "families={0} floor={1} landing={2} report={3}".format(
                       len(families), MIN_FAMILY_MENTIONS, landing, report)):
        return c.finish()

    agent = read_text(AGENT_MD)

    needed = agent_section_names(read_text(REFERENCE_ROLE))
    if not c.check(len(needed) >= MIN_ROLE_SECTIONS,
                   "reference role exposes a usable set of section names",
                   "measured={0} floor={1} names={2}".format(len(needed), MIN_ROLE_SECTIONS, needed)):
        return c.finish()
    missing_sections = [n for n in needed if n not in set(agent_section_names(agent))]
    c.check(not missing_sections, "agent section names cover the reference role",
            "reference_sections={0} missing={1}".format(len(needed), missing_sections))

    cmd_lines = [l.strip() for l in agent.splitlines() if script in l and CMD_RE.search(l)]
    c.check(len(cmd_lines) >= MIN_REGEN_CMD_LINES,
            "agent names the runnable metric command",
            "measured={0} floor={1}".format(len(cmd_lines), MIN_REGEN_CMD_LINES))
    c.check(write_command in agent, "agent carries the generator's write command verbatim",
            "write_command={0} present={1}".format(write_command, write_command in agent))
    c.check(read_command in agent, "agent carries the generator's single-metric recheck command verbatim",
            "read_command={0} present={1}".format(read_command, read_command in agent))

    scripts = sorted(set(RUN_CMD_RE.findall(agent)))
    dead_scripts = [s for s in scripts if not exists(s)]
    c.check(len(scripts) >= MIN_PARSED_SCRIPTS and not dead_scripts,
            "every command in the role points at a script that exists",
            "scripts={0} floor={1} missing={2}".format(scripts, MIN_PARSED_SCRIPTS, dead_scripts))

    quote = lines_matching(agent, QUOTE_RE, REGEN_RE)
    c.check(len(quote) >= MIN_REGEN_BEFORE_QUOTE_LINES,
            "agent requires regenerating the report before quoting any number",
            "measured={0} floor={1}".format(len(quote), MIN_REGEN_BEFORE_QUOTE_LINES))

    rerun = lines_matching(agent, REGEN_RE, RERUN_RE)
    c.check(len(rerun) >= MIN_RERUN_LINES,
            "agent obliges a full core-battery re-run after regenerating",
            "measured={0} floor={1}".format(len(rerun), MIN_RERUN_LINES))

    reads = [l for l in agent.splitlines() if READ_RE.search(l)]
    c.check(len(reads) >= MIN_READ_LINES,
            "agent requires single-metric rechecks instead of mental arithmetic",
            "measured={0} floor={1}".format(len(reads), MIN_READ_LINES))

    transcribe = lines_matching(agent, FIELD_RE, TRANSCRIBE_RE)
    c.check(len(transcribe) >= MIN_TRANSCRIBE_LINES,
            "agent forbids hand-copying the report readings",
            "measured={0} floor={1}".format(len(transcribe), MIN_TRANSCRIBE_LINES))

    shape = lines_matching(agent, SHAPE_RE, ONLY_SHAPE_RE)
    c.check(len(shape) >= MIN_SHAPE_ONLY_LINES,
            "agent says out loud that history readings are judged by shape only",
            "measured={0} floor={1}".format(len(shape), MIN_SHAPE_ONLY_LINES))

    prohib = lines_matching(agent, PROHIBITION_RE, TAMPER_RE)
    c.check(len(prohib) >= MIN_PROHIBITION_LINES,
            "agent forbids touching the judgement surface to get green",
            "measured={0} floor={1}".format(len(prohib), MIN_PROHIBITION_LINES))

    split = lines_matching(agent, SLICE_RE, INJECT_RE)
    c.check(len(split) >= MIN_SPLIT_LINES,
            "agent sends family or metric changes to a separate slice with injected cases",
            "measured={0} floor={1}".format(len(split), MIN_SPLIT_LINES))

    readings = lines_matching(agent, READING_RE, MEASURE_RE)
    c.check(len(readings) >= MIN_VERBATIM_READING_LINES,
            "agent requires verbatim readings instead of a summary",
            "measured={0} floor={1}".format(len(readings), MIN_VERBATIM_READING_LINES))

    defer = [l.strip() for l in agent.splitlines() if ACCEPTANCE_RE.search(l)]
    c.check(len(defer) >= MIN_NOT_ACCEPTANCE_LINES,
            "agent refuses to be the one that declares acceptance",
            "measured={0} floor={1}".format(len(defer), MIN_NOT_ACCEPTANCE_LINES))

    landing_lines = [l.strip() for l in agent.splitlines() if LANDING_MARK in l]
    c.check(bool(landing_lines) and all(landing in l for l in landing_lines),
            "agent declares its only writable landing is the report directory",
            "landing_lines={0} all_named={1} landing={2}".format(
                len(landing_lines), bool(landing_lines) and all(landing in l for l in landing_lines),
                landing))
    readonly = [l.strip() for l in agent.splitlines() if "READONLY" in l]
    c.check(len(readonly) >= MIN_READONLY_LINES,
            "agent declares everything else READONLY",
            "measured={0} floor={1}".format(len(readonly), MIN_READONLY_LINES))

    unnamed = [f for f in families if f not in agent]
    c.check(len(families) - len(unnamed) >= MIN_FAMILY_MENTIONS and not unnamed,
            "agent names every family derived from the roadmap goal sentence",
            "families={0} missing={1}".format(len(families), unnamed))
    c.check(report in agent, "agent names the report file the generator writes",
            "report={0} present={1}".format(report, report in agent))

    readme = read_text(CODEX_README)
    c.check(landing in readme,
            "report directory is registered in the harness README",
            "registered={0} path={1}".format(landing in readme, landing))

    cited = sorted(set(citable_paths(agent)))
    if not c.check(len(cited) >= MIN_CITED_PATHS,
                   "agent cites >= {0} checkable repo paths".format(MIN_CITED_PATHS),
                   "measured={0} floor={1} cited={2}".format(len(cited), MIN_CITED_PATHS, cited)):
        return c.finish()
    dead_paths = sorted(p for p in cited if not exists(p))
    c.check(not dead_paths, "every path cited by the role exists on disk",
            "dead={0} cited={1}".format(dead_paths, len(cited)))

    hits, detail = max_overlap_with_files(agent, [GENERATOR, ROADMAP], OVERLAP_RUNS)
    c.check(hits == 0, "role text does not restate the generator docstring or the roadmap entry",
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
    same_body = instructions == agent
    c.check(bool(data.get("name")) and bool(data.get("description")),
            "agent .toml declares name and description",
            "name={0} description={1}".format(bool(data.get("name")), bool(data.get("description"))))
    c.check(same_body, "agent .toml body equals the agent markdown verbatim",
            "toml_chars={0} md_chars={1} equal={2}".format(
                len(instructions), len(agent), same_body))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
