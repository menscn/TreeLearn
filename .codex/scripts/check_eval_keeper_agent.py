"""eval-keeper 角色与它的成对守门（ROADMAP B2 的角色面，片 029）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_eval_keeper_agent.py

为什么需要这支脚本：片 028 把「产物 vs 基线期望」做成了一条可重跑的命令，但那条命令只在**有人跑它、
并且基线本身没被悄悄改坏**的时候才有意义。这个角色的失效方向和本仓库登记过的病一一对应：
① 快照靠手抄——抄出来的「基线」判的是抄写者记住了什么，不是产物有没有变差；② 刷新快照之后只跑运行器
不跑整轮注入自检——片 028 真实撞过一次：快照一改，钉在快照原文上的注入锚点就漂了，运行器仍然全绿而
自检报 HARNESS（D9 的同一族成因，只是这次漂的是仪器而不是文档）；③ 为了让评测变绿去调阈值、删期望、
动判据（D4 的「仪器给自己开后门」）。所以这里判的形状是：跑法那一行必须指向真实脚本、快照必须逐字转录、
刷新后必须整轮重跑、判据面不许动、唯一可写落点只能是评测目录、期望语法只能从运行器那一份权威派生。

期望形状（`KINDS`）、三件套（`ASSETS`）与评测目录（由 `CASES_DIR` 派生）全部从 `run_evals` 现场取：
角色正文里写的五种形状如果和运行器认的五种不一致，那条判据必须响——两处各写一份就是第二份权威。

退出码：0 = 通过；1 = 有 FAIL。（注入用例在 .codex/scripts/dev/checkers_selftest.py 的
    `keeper:*` 条目里，判据红绿由退出码体现。）
"""

import os
import re
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, agent_section_names, citable_paths, enable_utf8_stdout, exists,
    max_overlap_with_files, placeholder_hits, read_text, repo_path)

AGENT_MD = ".codex/agents/eval-keeper.md"
AGENT_TOML = ".codex/agents/eval-keeper.toml"
REFERENCE_ROLE = ".codex/agents/verifier.md"
EVALS_README = ".codex/evals/README.md"
RUNNER = ".codex/scripts/run_evals.py"
CODEX_README = ".codex/README.md"
REGRESSION_RULE = ".codex/rules/regression.md"
SELFTEST = ".codex/scripts/dev/checkers_selftest.py"

INPUTS = (AGENT_MD, AGENT_TOML, REFERENCE_ROLE, EVALS_README, RUNNER, CODEX_README,
          REGRESSION_RULE)

# 裸下限：被检集合不能给自己定下限。verifier.md 的骨架是两种形状拼起来的——实测 4 个 `<tag>`
# 小节 + 1 个二级标题。下限取 5（> 任一单独形状），否则把尖括号那一形状整体改形，剩下 1 个
# 仍然 ≥ 旧的 1，「沿用同一套角色骨架」这条就悄悄变成半个骨架在比。
MIN_ROLE_SECTIONS = 5
# 「跑法那一行」与「那一行指向真脚本」是两半：只查一侧时，另一侧删空仍然报绿。
MIN_RUN_CMD_LINES = 2
MIN_PARSED_SCRIPTS = 2
# 刷新快照 → 整轮重跑，是本片真实撞到的那条链；只在合同里写一次不算形状。
MIN_REFRESH_RERUN_LINES = 2
MIN_SELFTEST_NAMED = 1
# 禁令要落在判据面上：「不得改阈值/删期望/动下限/改形状」至少两条各写一件事，写成一句谁都能满足。
MIN_PROHIBITION_LINES = 2
MIN_TRANSCRIBE_LINES = 2
MIN_READING_LINES = 2
# 「不宣布验收」是分工的下半句：只有一处时把它删掉另一处还在，两处都删才响。
MIN_NOT_ACCEPTANCE_LINES = 2
MIN_READONLY_LINES = 2
# 五种形状、三件资产都要被角色点名；下限低于派生条数，好让「运行器把形状抽干」也响。
MIN_KIND_MENTIONS = 3
MIN_ASSET_MENTIONS = 2
MIN_CITED_PATHS = 4
# 与角色共享措辞但绝不能共享整段的两份正文：期望语法表与防回归规则。
OVERLAP_RUNS = 60

RUN_CMD_RE = re.compile(r"python -X utf8\s+(\S+\.py)")
SNAPSHOT_RE = re.compile(r"快照")
RERUN_RE = re.compile(r"整轮|重跑|checkers_selftest")
TRANSCRIBE_RE = re.compile(r"逐字|零差异|转录|手抄")
READING_RE = re.compile(r"逐字|原样")
MEASURE_RE = re.compile(r"读数|OK|FAIL|EXITCODE|OVERALL")
PROHIBITION_RE = re.compile(r"不得|禁止|不许|不调低|不删|不改")
JUDGEMENT_RE = re.compile(r"阈值|期望|判据|下限|形状|守门")
ACCEPTANCE_RE = re.compile(r"不写 Verdict|不宣布")
LANDING_MARK = "只写"


def lines_matching(text, *patterns):
    return [l.strip() for l in text.splitlines()
            if all(p.search(l) for p in patterns)]


def main():
    enable_utf8_stdout()
    c = Checks("check_eval_keeper_agent")

    missing_inputs = [p for p in INPUTS if not exists(p)]
    if not c.check(not missing_inputs, "the seven inputs exist",
                   "missing={0}".format(missing_inputs)):
        return c.finish()

    # 期望语法只能有一份权威：从运行器现场取，取不到就没有可比对象（那是仪器坏，不是通过）。
    try:
        import run_evals
        kinds = tuple(run_evals.KINDS)
        assets = tuple(run_evals.ASSETS)
        evals_dir = run_evals.CASES_DIR.rsplit("/", 1)[0] + "/"
    except Exception as exc:  # noqa: BLE001 — 派生失败一律算仪器坏
        c.fail("expectation grammar derives from the runner", repr(exc))
        return c.finish()
    c.check(len(kinds) >= MIN_KIND_MENTIONS and len(assets) >= MIN_ASSET_MENTIONS,
            "expectation grammar derives from the runner",
            "kinds={0} assets={1} evals_dir={2}".format(len(kinds), len(assets), evals_dir))

    agent = read_text(AGENT_MD)

    needed = agent_section_names(read_text(REFERENCE_ROLE))
    if not c.check(len(needed) >= MIN_ROLE_SECTIONS,
                   "reference role exposes a usable set of section names",
                   "measured={0} floor={1} names={2}".format(len(needed), MIN_ROLE_SECTIONS, needed)):
        return c.finish()
    missing_sections = [n for n in needed if n not in set(agent_section_names(agent))]
    c.check(not missing_sections, "agent section names cover the reference role",
            "reference_sections={0} missing={1}".format(len(needed), missing_sections))

    cmd_lines = [l.strip() for l in agent.splitlines()
                 if RUNNER in l and "python -X utf8" in l]
    c.check(len(cmd_lines) >= MIN_RUN_CMD_LINES,
            "agent names the runnable evaluation command",
            "measured={0} floor={1}".format(len(cmd_lines), MIN_RUN_CMD_LINES))

    scripts = sorted(set(RUN_CMD_RE.findall(agent)))
    dead_scripts = [s for s in scripts if not exists(s)]
    c.check(len(scripts) >= MIN_PARSED_SCRIPTS and not dead_scripts,
            "every command in the role points at a script that exists",
            "scripts={0} floor={1} missing={2}".format(scripts, MIN_PARSED_SCRIPTS, dead_scripts))

    rerun = lines_matching(agent, SNAPSHOT_RE, RERUN_RE)
    c.check(len(rerun) >= MIN_REFRESH_RERUN_LINES,
            "agent obliges a full selftest re-run after refreshing a snapshot",
            "measured={0} floor={1}".format(len(rerun), MIN_REFRESH_RERUN_LINES))
    c.check(SELFTEST in agent, "agent names the selftest target verbatim",
            "named={0} floor={1}".format(SELFTEST in agent, MIN_SELFTEST_NAMED))

    prohib = lines_matching(agent, PROHIBITION_RE, JUDGEMENT_RE)
    c.check(len(prohib) >= MIN_PROHIBITION_LINES,
            "agent forbids touching the judgement surface to get green",
            "measured={0} floor={1}".format(len(prohib), MIN_PROHIBITION_LINES))

    transcribe = lines_matching(agent, SNAPSHOT_RE, TRANSCRIBE_RE)
    c.check(len(transcribe) >= MIN_TRANSCRIBE_LINES,
            "agent forbids hand-copying the product snapshot",
            "measured={0} floor={1}".format(len(transcribe), MIN_TRANSCRIBE_LINES))

    readings = lines_matching(agent, READING_RE, MEASURE_RE)
    c.check(len(readings) >= MIN_READING_LINES,
            "agent requires verbatim readings instead of a summary",
            "measured={0} floor={1}".format(len(readings), MIN_READING_LINES))

    defer = [l.strip() for l in agent.splitlines() if ACCEPTANCE_RE.search(l)]
    c.check(len(defer) >= MIN_NOT_ACCEPTANCE_LINES,
            "agent refuses to be the one that declares acceptance",
            "measured={0} floor={1}".format(len(defer), MIN_NOT_ACCEPTANCE_LINES))

    landing = [l.strip() for l in agent.splitlines() if LANDING_MARK in l]
    c.check(bool(landing) and all(evals_dir in l for l in landing),
            "agent declares its only writable landing is the evals directory",
            "landing_lines={0} all_named={1} landing={2}".format(
                len(landing), bool(landing) and all(evals_dir in l for l in landing), evals_dir))
    readonly = [l.strip() for l in agent.splitlines() if "READONLY" in l]
    c.check(len(readonly) >= MIN_READONLY_LINES,
            "agent declares everything else READONLY",
            "measured={0} floor={1}".format(len(readonly), MIN_READONLY_LINES))

    unnamed_kinds = [k for k in kinds if k not in agent]
    unnamed_assets = [a for a in assets if a not in agent]
    c.check(len(kinds) - len(unnamed_kinds) >= MIN_KIND_MENTIONS and not unnamed_kinds,
            "agent names every expectation kind the runner accepts",
            "kinds={0} missing={1}".format(len(kinds), unnamed_kinds))
    c.check(len(assets) - len(unnamed_assets) >= MIN_ASSET_MENTIONS and not unnamed_assets,
            "agent names the three files a case must ship",
            "assets={0} missing={1}".format(len(assets), unnamed_assets))

    readme = read_text(CODEX_README)
    c.check(evals_dir in readme,
            "evals directory is registered in the harness README",
            "registered={0} path={1}".format(evals_dir in readme, evals_dir))

    cited = sorted(set(citable_paths(agent)))
    if not c.check(len(cited) >= MIN_CITED_PATHS,
                   "agent cites >= {0} checkable repo paths".format(MIN_CITED_PATHS),
                   "measured={0} floor={1} cited={2}".format(len(cited), MIN_CITED_PATHS, cited)):
        return c.finish()
    dead_paths = sorted(p for p in cited if not exists(p))
    c.check(not dead_paths, "every path cited by the role exists on disk",
            "dead={0} cited={1}".format(dead_paths, len(cited)))

    hits, detail = max_overlap_with_files(agent, [EVALS_README, REGRESSION_RULE], OVERLAP_RUNS)
    c.check(hits == 0, "role text does not restate the grammar table or the rules it cites",
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
            "toml_chars={0} md_chars={1} equal={2}".format(len(instructions), len(agent), same_body))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
