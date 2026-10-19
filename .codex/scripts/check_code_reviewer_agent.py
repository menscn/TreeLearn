"""code-reviewer 角色与 REVIEW.md 骨架的成对检查（ROADMAP B1）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_code_reviewer_agent.py

为什么需要这支脚本：B1 把「写完自检」和「独立复核」分成两个角色，而两个角色共用一份任务卡
字段名就一定会互相冒充。这里只判五件便宜、且能挡住真实事故的事：

  1. 三份产物都在（角色 .md、运行时 .toml、报告骨架）——写错路径也要报，fail-closed；
  2. 角色文件的小节名**覆盖 verifier.md 现有的小节名**（读的是 verifier 现文，不是抄来的清单），
     并且点名 B1 目标里的四个复核维度（可读性 / 边界 / 错误 / 测试充分性）；
  3. 报告骨架有归档位置、那个目录真的在场、区分阻塞与非阻塞、并要求每条 finding 给 `文件:行`；
  4. 报告骨架的字段名不与任务模板的 Verification report 字段名重复（两边都现场派生）；
  5. 角色正文与骨架里没有占位符（PENDING / TBD / TODO / 待填）。

外加一条打包检查：`.toml` 的 developer_instructions 必须与 `.md` 正文逐字相同。
这条不是形式主义——本仓库的 .md/.toml 是同一份角色正文的两份拷贝，没有这条，
改一处留一处就会变成「加载的角色」和「读的角色说明」各说各话。

退出码：0 = 通过；1 = 有 FAIL。（本脚本不做 --selftest，注入用例在
    .codex/scripts/dev/checkers_selftest.py 的 `review:*` 条目里，判据红绿由退出码体现。）
"""

import os
import re
import sys
import tomllib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, enable_utf8_stdout, exists, placeholder_hits, read_text, repo_path, section_body)

AGENT_MD = ".codex/agents/code-reviewer.md"
AGENT_TOML = ".codex/agents/code-reviewer.toml"
VERIFIER_MD = ".codex/agents/verifier.md"
REVIEW_TEMPLATE = "Docs/REVIEW.md"
TASK_TEMPLATE = ".codex/tasks/_TEMPLATE.md"
# 角色唯一可写的落点。骨架里写了归档到这里，目录不在场就是死指针：git 不存空目录，
# 所以这一条同时钉住 Docs/reviews/README.md 那份落点说明还在。
ARCHIVE_DIR_REL = os.path.join("Docs", "reviews")

# 裸下限：被检集合不能给自己定下限。verifier.md 现在至少有 contract/rules/职责清单三处，
# 加上 identity 就是 4；把下限改成 len(现文) 等于删空 verifier.md 仍是绿。
MIN_VERIFIER_SECTIONS = 4
# 报告骨架至少要有的字段数（复核对象/读取路径/严重度/测试充分性/未覆盖之处等）。
MIN_REVIEW_FIELDS = 6
# 任务模板那一侧也必须有裸下限：只剩 1 个字段时「两边不重名」会自动成立，
# 那等于把这条判据的空集化——下限必须写在与两张字段清单都无关的字面量上。
MIN_TASK_FIELDS = 4

# B1 目标里写明的四个复核维度，缺一个就说明这个角色不负责评代码本身。
REVIEW_DIMENSIONS = ["可读性", "边界", "错误", "测试充分性"]

SEVERITY_PAIRS = ["阻塞项", "非阻塞建议"]
LOCATION_RE = re.compile(r"文件:行")
ARCHIVE_DIR = "Docs/reviews/"
ARCHIVE_PATTERN = "REVIEW-YYYY-MM-DD"
# 报告骨架的字段写法：`- **字段名**：`。散文里出现同名英文词不算字段，避免误判。
REVIEW_FIELD_RE = re.compile(r"^\s*-\s*\*\*([^*]+)\*\*[：:]")
# 任务模板 Verification report 的字段写法：`- Label: ...`（英文标签，逐字取冒号前）。
# 冒号后**可以什么都没有**（`- Gaps:` 就是空着让人填），所以这里不能要求冒号后还有空白。
TASK_FIELD_RE = re.compile(r"^-\s*([A-Za-z][A-Za-z/ ()\-]*?):(?:\s|$)")


def verifier_section_names(text):
    """从 verifier.md 现文里取它自己声明的小节名（尖括号块 + 二级标题）。"""
    tags = re.findall(r"^<([a-z]+)>$", text, re.M)
    titles = [l[3:].strip() for l in text.splitlines() if l.startswith("## ")]
    return tags + titles


def review_fields(text):
    return [m.group(1).strip() for m in
            (REVIEW_FIELD_RE.match(l) for l in text.splitlines()) if m]


def task_verification_fields(text):
    body = section_body(text, "Verification report")
    if body is None:
        return None
    return [m.group(1).strip() for m in
            (TASK_FIELD_RE.match(l) for l in body.splitlines()) if m]


def main():
    enable_utf8_stdout()
    c = Checks("check_code_reviewer_agent")

    present = [p for p in (AGENT_MD, AGENT_TOML, VERIFIER_MD, REVIEW_TEMPLATE, TASK_TEMPLATE)
               if not exists(p)]
    if not c.check(not present, "the five inputs exist",
                   "missing={0}".format(present)):
        return c.finish()

    agent = read_text(AGENT_MD)
    verifier = read_text(VERIFIER_MD)
    review = read_text(REVIEW_TEMPLATE)
    task = read_text(TASK_TEMPLATE)

    needed = verifier_section_names(verifier)
    if not c.check(len(needed) >= MIN_VERIFIER_SECTIONS,
                   "verifier.md exposes a usable set of section names",
                   "measured={0} floor={1} names={2}".format(len(needed), MIN_VERIFIER_SECTIONS,
                                                              needed)):
        return c.finish()
    missing = [n for n in needed if not re.search(r"^<%s>$" % re.escape(n), agent, re.M)
                and not any(n == t for t in
                            [l[3:].strip() for l in agent.splitlines() if l.startswith("## ")])]
    c.check(not missing, "agent section names cover verifier.md",
            "verifier_sections={0} missing={1}".format(len(needed), missing))

    dims = [d for d in REVIEW_DIMENSIONS if d not in agent]
    c.check(not dims, "agent names the four review dimensions",
            "missing={0}".format(dims))

    archive_ok = (ARCHIVE_DIR in review) and (ARCHIVE_PATTERN in review) and "归档位置" in review
    if not c.check(archive_ok, "review template declares an archive path",
                   "dir={0} pattern={1} section={2}".format(ARCHIVE_DIR in review,
                                                            ARCHIVE_PATTERN in review,
                                                            "## 归档位置" in review)):
        return c.finish()
    c.check(os.path.isdir(repo_path(ARCHIVE_DIR_REL)),
            "the declared archive directory exists on disk",
            "dir={0}".format(ARCHIVE_DIR_REL))

    sev = [s for s in SEVERITY_PAIRS if s not in review]
    c.check(not sev and "## 阻塞项" in review and "## 非阻塞建议" in review,
            "report separates blocking from non-blocking",
            "missing={0}".format(sev))

    c.check(bool(LOCATION_RE.search(review)),
            "every finding must carry a file:line location",
            "hits={0}".format(len(LOCATION_RE.findall(review))))

    fields = review_fields(review)
    if not c.check(len(fields) >= MIN_REVIEW_FIELDS,
                   "review template declares its own field list",
                   "measured={0} floor={1}".format(len(fields), MIN_REVIEW_FIELDS)):
        return c.finish()

    task_fields = task_verification_fields(task)
    if not c.check(bool(task_fields) and len(task_fields) >= MIN_TASK_FIELDS,
                   "task template still defines the Verification report fields",
                   "measured={0} floor={1} names={2}".format(len(task_fields or []),
                                                               MIN_TASK_FIELDS, task_fields)):
        return c.finish()
    overlap = [f for f in fields if any(f.replace(" ", "").lower() == t.replace(" ", "").lower()
                                        for t in task_fields)]
    c.check(not overlap, "report fields do not redefine task-card verification fields",
            "review_fields={0} task_fields={1} overlap={2}".format(len(fields),
                                                                    len(task_fields), overlap))

    # 这两份是要被照抄使用的：角色正文里的占位符会被读成「这角色还没写完」，骨架里的占位符
    # 会直接落进第一份复核报告，之后再没人回来删。
    placeholders = sorted(set(placeholder_hits(agent) + placeholder_hits(review)))
    c.check(not placeholders, "role text and report skeleton carry no placeholder tokens",
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
            "name={0} description={1}".format(bool(data.get("name")), bool(data.get("description"))))
    c.check(same, "agent .toml body equals the agent markdown verbatim",
            "toml_chars={0} md_chars={1} equal={2}".format(len(instructions), len(agent), same))

    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
