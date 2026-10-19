"""评测集运行器（ROADMAP B2 的评测资产面）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/run_evals.py

为什么需要这支脚本：B2 的目标是「每次改动都能证明没变差」。规则、角色正文、守门脚本
都已经各有只读守门，唯独**AI 产物本身**没有——片 026 那份影响面清单当时的验收方式是
人读一遍 + 一堆 `文件:行` 指针复算，指针复算是一次性驱动（写在 artifacts/tmp 里，跑完就没了），
没有任何东西在下一次改动时重跑它。所以这里把「产物 vs 基线期望」做成一条可重跑的判定：
期望写在 `.codex/evals/cases/*/expected.md`，产物快照写在同目录 `product.md`，跑法就这一条命令。

判定的对象是**产物**，不是角色正文：角色契约由 029 的 `check_eval_keeper_agent.py` 管，
两边判据不重叠，否则改一处措辞要同时红两支脚本。

期望语法的五种形状都必须在本仓库里真的被用到（`KINDS` 那条判据负责），因为「解析器认识但
没人用」的形状等于没有；阈值一律写在期望文件里由作者给定，运行器只钉住三件它自己不能退让的事：
阈值必须 >= 1（否则 `must: X >= 0` 这种恒真期望能骗过整轮）、产物每一条 bullet 都必须被至少一条
正向期望钉住（否则删掉一条期望就悄悄少判一条产物而运行器照样全绿）、以及无可判对象时不得打 OK。

退出码：0 = 通过；1 = 有 FAIL。（注入用例在 .codex/scripts/dev/checkers_selftest.py 的
    `evals:*` 条目里，判据红绿由退出码体现。）
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, citable_paths, citation_problems, enable_utf8_stdout, exists,
    path_line_citations, read_text, repo_path)

CASES_DIR = ".codex/evals/cases"
ASSETS = ("input.md", "expected.md", "product.md")

# 裸下限：case 数与期望条数都不能由被检集合自己报数（ROADMAP D1 的同一族成因）。
# 实测 1 个 case、9 条期望；下限取 1/5，留「以后加 case」的余量，同时删空任一带立刻转红。
MIN_CASES = 1
MIN_EXPECTATIONS = 5

KINDS = ("must", "shape", "forbid", "pointers-alive", "paths-exist")
POSITIVE = ("must", "shape")

# 计数型形状（pointers-alive / paths-exist）没有载荷，冒号是可选的——写死冒号会让它们
# 在期望文件里以「认不出的行」出现，那条判据本来就该红，但不该因为作者少打一个标点而红。
EXPECT_RE = re.compile(r"^-\s+(must|shape|forbid|pointers-alive|paths-exist)\b[:\s]*(.*)$")
THRESHOLD_RE = re.compile(r">=\s*(\d+)\s*$")
# `路径:行` 形态的引用会整段被 citable_paths 收下（反引号里就是它），去掉行号后缀才是可 exists() 的路径。
CITED_LINE_RE = re.compile(r":\d+(?:\s*[-–]\s*\d+)?$")


def judged_units(text):
    """把 markdown 列表项连同其续行合成一个判定单元。

    直接按原始行判定会造出假 FAIL：一条 bullet 换行成三行后，「生产者 + 消费者 + 同级入口」
    这种多词形状被拆散，内容与判据都没变而读数变了。评测要判的是「这一条有没有说齐」，
    所以判定单元必须是逻辑条目而不是物理行。
    """
    units = []
    for line in text.splitlines():
        if line.lstrip().startswith(("- ", "* ")):
            units.append(line.lstrip()[2:].strip())
        elif units and line.strip():
            units[-1] += " " + line.strip()
    return units


def parse_expectations(text):
    """返回 (条目, 无法识别的行)。条目 = (kind, payload, threshold)。"""
    items = []
    unknown = []
    for line in text.splitlines():
        if not line.strip().startswith("- "):
            continue
        m = EXPECT_RE.match(line.strip())
        if not m:
            unknown.append(line.strip())
            continue
        kind, payload = m.group(1), m.group(2).strip()
        t = THRESHOLD_RE.search(payload)
        threshold = int(t.group(1)) if t else None
        if threshold is not None:
            payload = payload[:t.start()].strip()
        items.append((kind, payload, threshold))
    return items, unknown


def matching_units(units, tokens):
    return [i for i, u in enumerate(units) if all(tok in u for tok in tokens)]


def main():
    enable_utf8_stdout()
    c = Checks("run_evals")

    if not c.check(exists(CASES_DIR), "eval cases directory exists", "path={0}".format(CASES_DIR)):
        return c.finish()

    cases = sorted(d for d in os.listdir(repo_path(CASES_DIR))
                   if os.path.isdir(os.path.join(repo_path(CASES_DIR), d)))
    c.check(len(cases) >= MIN_CASES, "cases/ holds at least MIN_CASES case dirs",
            "measured={0} floor={1} cases={2}".format(len(cases), MIN_CASES, cases))

    kinds_seen = set()
    total = 0
    for case in cases:
        paths = dict((nm, "{0}/{1}/{2}".format(CASES_DIR, case, nm)) for nm in ASSETS)
        missing = [nm for nm in ASSETS if not exists(paths[nm])]
        if not c.check(not missing, "case {0} ships input+expected+product".format(case),
                       "missing={0}".format(missing)):
            continue
        empty = [nm for nm in ASSETS if not read_text(paths[nm]).strip()]
        if not c.check(not empty, "case {0} assets are all non-empty".format(case),
                       "empty={0}".format(empty)):
            continue
        expected = read_text(paths["expected.md"])
        product = read_text(paths["product.md"])
        units = judged_units(product)

        items, unknown = parse_expectations(expected)
        # 解析失败必须转红：认不出的期望行不会判定任何东西，静默跳过等于期望被悄悄删掉。
        if not c.check(not unknown, "case {0} expectations all parse".format(case),
                       "unknown={0} parsed={1}".format(unknown[:2], len(items))):
            continue
        c.check(len(items) >= MIN_EXPECTATIONS,
                "case {0} declares at least MIN_EXPECTATIONS expectations".format(case),
                "measured={0} floor={1}".format(len(items), MIN_EXPECTATIONS))
        weak = [i for i in items if i[2] is not None and i[2] < 1]
        c.check(not weak, "case {0} thresholds are all >= 1".format(case),
                "恒真期望（>=0）不该被允许: {0}".format([i[0:2] for i in weak][:3]))
        kinds_seen.update(i[0] for i in items)
        total += len(items)

        cites = path_line_citations(product)
        cited = sorted(set(CITED_LINE_RE.sub("", p) for p in citable_paths(product)))
        covered = set()
        for kind, payload, threshold in items:
            if kind in POSITIVE:
                tokens = [payload] if kind == "must" else [t.strip() for t in payload.split("&") if t.strip()]
                hits = matching_units(units, tokens)
                covered.update(hits)
                if kind == "must":
                    c.check(len(hits) >= threshold, "case {0} must: {1}".format(case, payload),
                            "bullets={0} floor={1}".format(len(hits), threshold))
                else:
                    c.check(len(hits) >= threshold and len(tokens) >= 2,
                            "case {0} shape: {1}".format(case, " & ".join(tokens)),
                            "bullets={0} floor={1} tokens={2}".format(
                                len(hits), threshold, len(tokens)))
            elif kind == "forbid":
                n = len(matching_units(units, [payload]))
                c.check(n == 0, "case {0} forbid: {1}".format(case, payload),
                        "bullets={0} expected=0".format(n))
            elif kind == "pointers-alive":
                problems = citation_problems(cites)
                c.check(not problems and len(cites) >= threshold,
                        "case {0} pointers-alive".format(case),
                        "citations={0} floor={1} problems={2}".format(
                            len(cites), threshold, problems[:3]))
            elif kind == "paths-exist":
                dead = sorted(set(p for p in cited if not exists(p)))
                c.check(not dead and len(cited) >= threshold,
                        "case {0} paths-exist".format(case),
                        "cited={0} floor={1} dead={2}".format(
                            len(cited), threshold, dead[:3]))
        # 每条产物条目至少要被一条正向期望钉住。这一条是整套期望的「防删」结构：
        # 删掉一条期望既不改变条数下限、也不移除任何形状，运行器本来无从发现；
        # 而「这条 bullet 不再有任何期望判它」必然发生，所以它必然转红。
        loose = [units[i][:36] for i in range(len(units)) if i not in covered]
        c.check(not loose, "case {0} pins every product bullet".format(case),
                "unpinned={0} bullets={1}".format(loose[:2], len(units)))

    # 五种形状都要有用例：解析器里养着一条没人用的形状，那条形状坏了不会有人知道。
    absent = [k for k in KINDS if k not in kinds_seen]
    c.check(not absent, "every expectation kind is exercised by some case",
            "missing={0} seen={1}".format(absent, sorted(kinds_seen)))
    c.note("case and expectation inventory is printed, not judged",
           "EVALS cases={0} expectations={1}".format(len(cases), total))
    return c.finish()


if __name__ == "__main__":
    sys.exit(main())
