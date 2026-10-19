"""codebase-researcher 角色的成对检查（ROADMAP B4）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_codebase_researcher_agent.py

为什么需要这支脚本：B4 把「动手之前先摸清牵动谁」变成一个独立角色，而这个角色的全部价值
都在「每条结论能不能被别人复算」上。措辞好不好看不判，判的是四件能挡住真实事故的事：
每条结论有没有命令与 `文件:行`、查不到时有没有显式写「未找到」、实测与推测有没有分开、
以及它到底能写哪里。

判据（15 条，全部现场派生，不抄清单）：

  1. 七份输入都在（角色 .md/.toml、verifier.md、任务模板、两条被引规则、背景包）——fail-closed；
  2. verifier.md 自己声明的小节集可用（裸下限），角色必须覆盖它（沿用同一套骨架）；
  3. 任务模板 `### Behavior impact` 的四个字段名可用（裸下限），角色必须逐条点名它——
     模板改名而角色没跟，清查结果就会落到一个不存在的小节里；
  4. 角色要求每条结论写成「命令 + `文件:行`」，两侧各有一条裸计数下限；
  5. 角色要求查不到时显式写「未找到」（同样带下限，防止只剩一处措辞偶然命中）；
  6. 角色区分 `实测` 与 `推测`，并要求推测给出可以证实它的命令；
  7. 角色只声明**一个**可写落点（任务卡的 Behavior impact 字段）且声明 READONLY；
  8. 角色引用的仓库路径全部在场（引用死掉比引用少更糟：读者会去一个不存在的文件里找规则）；
  9. 角色正文不与它引用的规则正文重叠成段（同一约束只留一份权威正文）；
 10. 角色正文无占位符；
 11. `.toml` 能解析、声明 name/description、且 developer_instructions 与 `.md` 正文逐字相同。

退出码：0 = 通过；1 = 有 FAIL。（注入用例在 .codex/scripts/dev/checkers_selftest.py 的
    `research:*` 条目里，判据红绿由退出码体现。）
"""

import os
import re
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, agent_section_names, backticked_paths, enable_utf8_stdout, exists,
    max_overlap_with_files, placeholder_hits, read_text, repo_path, section_body)

AGENT_MD = ".codex/agents/codebase-researcher.md"
AGENT_TOML = ".codex/agents/codebase-researcher.toml"
VERIFIER_MD = ".codex/agents/verifier.md"
TASK_TEMPLATE = ".codex/tasks/_TEMPLATE.md"
REGRESSION_RULE = ".codex/rules/regression.md"
DOCUMENTATION_RULE = ".codex/rules/documentation.md"
PROJECT_BRIEF = ".codex/context/project-brief.md"

INPUTS = (AGENT_MD, AGENT_TOML, VERIFIER_MD, TASK_TEMPLATE,
          REGRESSION_RULE, DOCUMENTATION_RULE, PROJECT_BRIEF)

IMPACT_HEADING = "Behavior impact — applicable surfaces only"

# 裸下限：被检集合不能给自己定下限。verifier.md 现有 identity/source/contract/rules/职责清单，
# 下限取 4——把它的标题删空了就没有「沿用同一套骨架」可比。
MIN_VERIFIER_SECTIONS = 4
# 任务模板那一侧同样要有裸下限：只剩 1 个字段时「角色点名了模板字段」会自动成立。
MIN_IMPACT_FIELDS = 4
# 「命令 + 文件:行」不是一处偶然的措辞，而是这个角色反复使用的输出形状，所以两侧各设下限。
MIN_LOCATION_MENTIONS = 2
MIN_COMMAND_MENTIONS = 3
MIN_ABSENT_MENTIONS = 2
# 角色引用了哪些路径就要有哪些路径在场；引用被删到比这还少时，上面那条「死指针」判据
# 会因为无话可判而空集通过，只有下限能挡住这种缩表。
MIN_CITED_PATHS = 5
# 与本角色共享字段名、但绝不能共享整段的规则正文：抄过来就有第二份权威。
OVERLAP_RUNS = 60

LOCATION_RE = re.compile(r"文件:行")
COMMAND_RE = re.compile(r"命令")
ABSENT_RE = re.compile(r"未找到")
# 模板里的字段写法是 `- Label: ...`；括号后缀（如 "(code evidence)"）是模板的注脚，
# 不要求角色一并照抄，所以取标签时先剥掉它。
IMPACT_FIELD_RE = re.compile(r"^-\s*([^:(]+?)\s*(?:\([^)]*\))?\s*[：:]")


def impact_fields(template_text):
    body = section_body(template_text, IMPACT_HEADING, level=3)
    if body is None:
        return None
    return [m.group(1).strip() for m in
            (IMPACT_FIELD_RE.match(l) for l in body.splitlines()) if m]


def cited_paths(text):
    """角色正文里反引号引出的、能判定在场性的仓库路径。

    通配写法（`android/**`）指的是「这一类路径」而不是某一个文件，无法用 exists() 判，
    所以跳过——它由 `check_stack_facts.py` 那类按目录内容的守门管。
    """
    out = []
    for raw in backticked_paths(text):
        token = raw.strip().rstrip("/")
        if "*" in token or "?" in token:
            continue
        out.append(token)
    return out


def main():
    enable_utf8_stdout()
    c = Checks("check_codebase_researcher_agent")

    missing_inputs = [p for p in INPUTS if not exists(p)]
    if not c.check(not missing_inputs, "the seven inputs exist",
                   "missing={0}".format(missing_inputs)):
        return c.finish()

    agent = read_text(AGENT_MD)
    verifier = read_text(VERIFIER_MD)
    template = read_text(TASK_TEMPLATE)

    needed = agent_section_names(verifier)
    if not c.check(len(needed) >= MIN_VERIFIER_SECTIONS,
                   "verifier.md exposes a usable set of section names",
                   "measured={0} floor={1} names={2}".format(len(needed), MIN_VERIFIER_SECTIONS,
                                                             needed)):
        return c.finish()
    have = set(agent_section_names(agent))
    c.check(all(n in have for n in needed), "agent section names cover verifier.md",
            "verifier_sections={0} missing={1}".format(len(needed),
                                                       [n for n in needed if n not in have]))

    fields = impact_fields(template)
    if not c.check(bool(fields) and len(fields) >= MIN_IMPACT_FIELDS,
                   "task template declares the four impact fields",
                   "measured={0} floor={1} names={2}".format(len(fields or []),
                                                             MIN_IMPACT_FIELDS, fields)):
        return c.finish()
    unnamed = [f for f in fields if f not in agent]
    c.check(not unnamed, "agent names every impact field from the task template",
            "template_fields={0} missing={1}".format(len(fields), unnamed))

    location_hits = len(LOCATION_RE.findall(agent))
    command_hits = len(COMMAND_RE.findall(agent))
    c.check(location_hits >= MIN_LOCATION_MENTIONS
            and command_hits >= MIN_COMMAND_MENTIONS,
            "agent requires each conclusion as command + file:line",
            "location_hits={0} floor={1} command_hits={2} floor={3}".format(
                location_hits, MIN_LOCATION_MENTIONS, command_hits, MIN_COMMAND_MENTIONS))

    absent_hits = len(ABSENT_RE.findall(agent))
    c.check(absent_hits >= MIN_ABSENT_MENTIONS,
            "agent requires an explicit 未找到 instead of silence",
            "hits={0} floor={1}".format(absent_hits, MIN_ABSENT_MENTIONS))

    provenance = [t for t in ("实测", "推测", "证实") if t not in agent]
    c.check(not provenance, "agent separates 实测 from 推测 and demands proof for 推测",
            "missing={0}".format(provenance))

    landing = [l for l in agent.splitlines() if "只写" in l]
    c.check("READONLY" in agent and bool(landing)
            and all("Behavior impact" in l for l in landing),
            "agent declares READONLY with one writable landing",
            "readonly={0} landing_lines={1} named={2}".format(
                "READONLY" in agent, len(landing),
                bool(landing) and all("Behavior impact" in l for l in landing)))

    cited = sorted(set(cited_paths(agent)))
    if not c.check(len(cited) >= MIN_CITED_PATHS,
                   "agent cites >= {0} checkable repo paths".format(MIN_CITED_PATHS),
                   "measured={0} floor={1} cited={2}".format(len(cited), MIN_CITED_PATHS, cited)):
        return c.finish()
    dead = sorted(p for p in cited if not exists(p))
    c.check(not dead, "every path cited by the role exists on disk",
            "dead={0} cited={1}".format(dead, len(cited)))

    # 这一条不是防抄写，是防「同一个约束有两份权威正文」：角色一旦把规则段落抄进来，
    # 下次改规则就只改一处，而读者照抄的那份仍然是旧措辞。
    total, detail = max_overlap_with_files(
        agent, [REGRESSION_RULE, DOCUMENTATION_RULE], OVERLAP_RUNS)
    c.check(total == 0, "role text does not restate the rules it cites",
            "measured_hits={0} sample={1}".format(total, detail))

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
    same = instructions.strip() == agent.strip()
    c.check(bool(data.get("name")) and bool(data.get("description")),
            "agent .toml declares name and description",
            "name={0} description={1}".format(bool(data.get("name")),
                                              bool(data.get("description"))))
    c.check(same, "agent .toml body equals the agent markdown verbatim",
            "toml_chars={0} md_chars={1} equal={2}".format(len(instructions), len(agent), same))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
