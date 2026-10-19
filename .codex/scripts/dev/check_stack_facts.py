"""校验「本仓库有哪些栈」这类方向事实在当前态文档里是否一致（父需求 T-2026-10-15-001，片 T-2026-10-15-002）。

用法（从任意目录）：
    python .codex/scripts/dev/check_stack_facts.py              # 校验真实仓库
    python .codex/scripts/dev/check_stack_facts.py --root <dir> # 校验副本（自检用）
    python .codex/scripts/dev/check_stack_facts.py --selftest   # 正反双向自检

为什么需要它：Web 线（独立 frontend/ + 本机 backend/）已由用户 2026-10-15 拍板，
但多处当前态文档仍写着「没有后端」「frontend/ 与 backend/ 目录不存在」。
这些句子会指导下一位实现者——它们是契约性文档，不是修辞。
判据只有四条：盘点清单不得被清空或截断（条目下限）；旧方向短语不得残留在被盘点文件里（命中给到行号）；
每个被盘点文件必须指向权威规格；与清单无关的模式兜底——扫描集内任何一行留着旧方向形状，
就必须同一行带权威指针（D6）。
本脚本只管这一条判据，设计数值仍归 check_design_tokens.py，背景包结构仍归 check_context_pack.py。
"""

import io
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(REPO_ROOT, ".codex", "scripts"))
from harness_check_lib import Checks, enable_utf8_stdout  # noqa: E402

AUTHORITY = "SPEC-2026-10-15-web-h5-android-parity"

# 每个条目：(仓库相对路径, 该文件里已作废的方向短语)。
# 短语取自实测命中行（见任务 T-2026-10-15-002 的 Facts），刻意用精确串而不是关键词，
# 否则改写后的「不做远程服务端」这类合法表述会被自己的守门判成违规。
STACK_FACTS = [
    ("README.md", "没有后端"),
    ("README_EN.md", "no backend"),
    (".codex/context/project-brief.md", "目录目前不存在"),
    (".codex/ROADMAP.md", "不要假设有服务端"),
    ("Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md",
     "不建设 Spring Boot、React"),
    ("Docs/05-technical-design/TreeLearn_Harness全量开发交付提示词.md", "无 backend/frontend"),
    (".codex/rules/shared-state.md", "无服务端实例"),
    (".codex/rules/observability.md", "无服务端、无后端统一日志基础设施"),
    (".codex/skills/pm-discovery/SKILL.md", "本地优先、无服务端"),
    # 第 10 条由本片收尾时的二次反向搜索发现（不是一开始列出的那 9 条）：已交付的功能
    # 规格也会引 project-brief 的旧约束当理由，读者同样把它当现状读。
    ("Docs/spec/SPEC-2026-09-23-providers-and-models.md", "无服务端、单人维护"),
]

# 条目下限：清单一旦被清空或截断，「零命中」就会伪装成绿灯。本仓库已三次栽在
# 「手写枚举 + 匹配集为空却打印 PASS」上，所以这条判据必须在生产模式里就生效。
# 下限刻意写成字面量而不是 len(STACK_FACTS)——用被检清单算自己的下限等于没有下限。
MIN_INVENTORY = 10

# D6 兜底：上面那份清单钉的是「已知的精确短语」，它拦不住在**清单之外**新长出来的同族主张
# ——002 片的第 10 个命中就是收尾时第二轮反向搜索才冒出来的。模式集合只存这一处。
# 命中不直接判红：同一行带权威指针的算「引用历史」，允许存在；不带指针的才算「把旧方向当现状」。
# 少了这条区分，守门会把每一处纠正性引用也判红，等于逼人删掉证据来变绿。
STALE_PATTERNS = [
    "无服务端",
    "无前后端",
    "没有后端",
    "不做后端",
    r"不建设[^。]{0,12}(?:后端|前端|服务)",
    "no backend",
    r"(?:frontend|backend)[^。]{0,10}目录[^。]{0,6}(?:不存在|未创建|尚未创建)",
    "目录不存在",
]
# 同样是字面量下限而不是 len(STALE_PATTERNS)：被检集合不能给自己定下限。
MIN_PATTERNS = 6
# 扫描集按前缀取，与 STACK_FACTS 那份枚举无关——这正是这条判据存在的理由。
PATTERN_SCAN_PREFIXES = ("README", ".codex/rules/", ".codex/context/", "Docs/spec/")
# 自检用的一份**不在盘点清单里**的扫描集文件，用来证明这条判据确实读到了清单之外。
PATTERN_SCAN_SAMPLE = ".codex/rules/sql.md"


def scan_files(root):
    """按前缀收集扫描集里的文件（仓库相对路径，正斜杠）。"""
    out = []
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in ("__pycache__", "node_modules", "build")]
        for name in files:
            full = os.path.join(base, name)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            if rel.startswith(PATTERN_SCAN_PREFIXES):
                out.append(rel)
    return sorted(out)


def pattern_scan(root):
    """返回 (未带指针的命中, 已带指针的命中数)。权威规格自身豁免：它是这条事实的定义处，
    必然要引用被它取代的旧主张。"""
    unattributed = []
    attributed = 0
    compiled = [re.compile(p) for p in STALE_PATTERNS]
    for rel in scan_files(root):
        if AUTHORITY in rel:
            continue
        with io.open(os.path.join(root, rel.replace("/", os.sep)), encoding="utf-8") as f:
            for no, line in enumerate(f.read().splitlines(), 1):
                if not any(rx.search(line) for rx in compiled):
                    continue
                if AUTHORITY in line:
                    attributed += 1
                else:
                    unattributed.append("{0}:{1}".format(rel, no))
    return unattributed, attributed



def read(rel, root):
    with io.open(os.path.join(root, rel.replace("/", os.sep)), encoding="utf-8") as f:
        return f.read()


def check_files(root, c):
    """逐文件判三条：清单非空、旧短语已清除（给到行号）、权威规格已被指向。缺文件一律判 FAIL。"""
    stale = []
    unlinked = []
    missing = []
    c.check(len(STACK_FACTS) >= MIN_INVENTORY, "inventory floor met",
            "measured={0} floor={1}".format(len(STACK_FACTS), MIN_INVENTORY))
    for rel, phrase in STACK_FACTS:
        path = os.path.join(root, rel.replace("/", os.sep))
        if not os.path.isfile(path):
            missing.append(rel)
            continue
        text = read(rel, root)
        lines = [str(i + 1) for i, line in enumerate(text.splitlines()) if phrase in line]
        if lines:
            stale.append("{0}:{1}::{2}".format(rel, ",".join(lines), phrase))
        if AUTHORITY not in text:
            unlinked.append(rel)
    c.check(not missing, "every inventoried current-state file is present",
            "measured={0} missing={1}".format(len(STACK_FACTS), missing))
    c.check(not stale, "no superseded stack claim survives",
            "measured={0} hits={1}".format(len(stale), stale))
    c.check(not unlinked, "every inventoried file points at the authoritative spec",
            "measured={0} missing_pointer={1}".format(len(unlinked), unlinked))
    c.check(len(STALE_PATTERNS) >= MIN_PATTERNS, "pattern backstop floor met",
            "measured={0} floor={1}".format(len(STALE_PATTERNS), MIN_PATTERNS))
    unattributed, attributed = pattern_scan(root)
    c.check(not unattributed,
            "every stale-shape line in the scan set carries the authority pointer",
            "measured={0} attributed={1} hits={2}".format(
                len(unattributed), attributed, unattributed[:8]))


def main(argv):
    enable_utf8_stdout()
    if "--selftest" in argv:
        return selftest()
    root = REPO_ROOT
    if "--root" in argv:
        i = argv.index("--root") + 1
        if i >= len(argv):
            # 用法错误属于「仪器没跑起来」，必须判 3；旧写法让 IndexError 逃到解释器、
            # 以 1 退出，与「发现违规」同码——读结果的人会把用法错误当成判据成立。
            print("INSTRUMENT FAULT: --root requires a value")
            return 3
        root = os.path.abspath(argv[i])
    # 判定只有一个入口：上一版在 main() 里内联复写了 run() 的三行，于是「仪器故障 → 3」只覆盖
    # selftest 内部的重跑，直接跑守门时第三种异常仍以 1 退出（第四轮 verifier 非阻断 4 的真实位置）。
    return run(root)


def run(root):
    """在本子进程里跑一次判定，返回退出码；1=违规，0=干净，3=自身故障。"""
    try:
        c = Checks("check_stack_facts")
        check_files(root, c)
        return c.finish()
    except Exception as exc:  # 任何仪器自身故障都走 3；只映射两类异常会让第三种故障
        # 以 1 退出，被负向用例当成「如期失败」认证成 ok（第四轮 verifier 非阻断 4）。
        print("INSTRUMENT FAULT: {0}".format(exc))
        return 3


def selftest():
    """正向：健康副本必须 PASS。反向：注入每条旧短语、抹掉每个权威指向、清空清单、
    注入裸的模式命中、清空模式集合都必须 FAIL。

    用例数必须等于「被盘点文件数 × 2 + 基线 + 清空清单 + 模式三例」，即 2N+5；对不上说明有分支从未被验证。
    退出码三档且互斥（本函数曾把「用例数不足」只打印成 EXITCODE=3 而仍 return 0，即 fail-open）：
    0 = 全部用例符合；1 = 有用例不符合；3 = 仪器自身故障（夹具不健康、被判脚本崩、用例数对不上）。
    """
    cases = 0
    bad = 0
    work = tempfile.mkdtemp(prefix="tl-stack-facts-")
    try:
        for rel, _ in STACK_FACTS:
            dst = os.path.join(work, rel.replace("/", os.sep))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(os.path.join(REPO_ROOT, rel.replace("/", os.sep)), dst)
        # 扫描集里的一份「不在盘点清单上」的文件：没有它，模式兜底这条判据在自检里
        # 就只被清单内文件证到，等于没证到它要防的那件事（清单外的同族主张）。
        sample = os.path.join(work, PATTERN_SCAN_SAMPLE.replace("/", os.sep))
        os.makedirs(os.path.dirname(sample), exist_ok=True)
        shutil.copyfile(os.path.join(REPO_ROOT, PATTERN_SCAN_SAMPLE.replace("/", os.sep)), sample)
        print("BASELINE (healthy copy must PASS)")
        rc = run(work)
        cases += 1
        if rc != 0:
            # 夹具不健康 ⇒ 后面每个用例的判决都不可信；立即中止并判仪器故障，
            # 不要继续跑 2N 个用例再去解释为什么全绿。
            print("INSTRUMENT FAULT: healthy baseline copy did not pass (rc={0}); "
                  "aborting, the {1} injected cases would prove nothing".format(
                      rc, 2 * len(STACK_FACTS) + 3))
            return 3
        for rel, phrase in STACK_FACTS:
            for mode in ("inject-stale", "drop-pointer"):
                path = os.path.join(work, rel.replace("/", os.sep))
                original = open(path, encoding="utf-8").read()
                if mode == "inject-stale":
                    mutated = original + "\n\n回归样本：{0}\n".format(phrase)
                else:
                    mutated = original.replace(AUTHORITY, "SPEC-YYYY-已移除")
                try:
                    with io.open(path, "w", encoding="utf-8") as f:
                        f.write(mutated)
                    rc = run(work)
                    cases += 1
                    if rc == 3:
                        print("INSTRUMENT FAULT at CASE {0} {1}: rc=3".format(mode, rel))
                        return 3
                    # 期望是**恰好 1**（违规），不是「任何非 0」：把仪器故障（3）蒙成「通过」
                    # 是本仓库既有的「前置条件缺失不得算 PASS」形态。
                    ok = (rc == 1)
                    print("CASE {0} {1}: rc={2} -> {3}".format(
                        mode, rel, rc, "ok" if ok else "MISMATCH"))
                    if not ok:
                        bad += 1
                finally:
                    with io.open(path, "w", encoding="utf-8") as f:
                        f.write(original)
        print("")
        # 条目下限用例：把清单清空后必须 FAIL，否则「零命中即绿」这条打穿路径无人验证过。
        saved = list(STACK_FACTS)
        try:
            del STACK_FACTS[:]
            rc = run(work)
            cases += 1
            ok = rc == 1
            print("CASE empty-inventory: rc={0} -> {1}".format(rc, "ok" if ok else "MISMATCH"))
            if not ok:
                bad += 1
        finally:
            STACK_FACTS.extend(saved)
        # 模式兜底的三例：① 清单外文件里注入一行裸的旧方向主张必须 FAIL；
        # ② 同一形状但同行带权威指针必须 PASS——不配这一例，判据可以靠「凡提及即红」蒙过自检，
        #    而那种守门会逼人删掉纠正性引用才能变绿；③ 清空模式集合必须 FAIL（下限）。
        original = open(sample, encoding="utf-8").read()
        for mode, expect in (("pattern-bare", 1), ("pattern-attributed", 0)):
            try:
                if mode == "pattern-bare":
                    mutated = original + "\n本仓库无服务端，也不做后端。\n"
                else:
                    mutated = original + (
                        "\n旧文档里的「无服务端」已由 `Docs/spec/{0}.md` 收窄为「无远程服务端」。\n"
                        .format(AUTHORITY))
                with io.open(sample, "w", encoding="utf-8") as f:
                    f.write(mutated)
                rc = run(work)
                cases += 1
                ok = rc == expect
                print("CASE {0}: rc={1} expect={2} -> {3}".format(
                    mode, rc, expect, "ok" if ok else "MISMATCH"))
                if not ok:
                    bad += 1
            finally:
                with io.open(sample, "w", encoding="utf-8") as f:
                    f.write(original)
        saved_patterns = list(STALE_PATTERNS)
        try:
            del STALE_PATTERNS[:]
            rc = run(work)
            cases += 1
            ok = rc == 1
            print("CASE empty-patterns: rc={0} -> {1}".format(rc, "ok" if ok else "MISMATCH"))
            if not ok:
                bad += 1
        finally:
            STALE_PATTERNS.extend(saved_patterns)
        expected = 2 * len(saved) + 5
        print("")
        print("check_stack_facts --selftest: {0} case(s), {1} mismatch(es), expected={2}".format(
            cases, bad, expected))
        if bad:
            print("EXITCODE=1")
            return 1
        if cases != expected:
            # 用例数对不上＝有分支从未执行，这是仪器问题而不是「用例通过」。
            print("EXITCODE=3 (case count {0} != expected {1}: a branch never ran)".format(
                cases, expected))
            return 3
        print("EXITCODE=0")
        return 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
