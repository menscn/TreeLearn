#!/usr/bin/env python3
"""run_all_guards.py 的配套自检：给它的每条检测分支注入一次违规，并要求那次违规被判。

用法（从任意目录）：
    python .codex/scripts/check_run_all_guards.py

为什么单独一支脚本：汇总器自己就是「报通过」的那个入口，让它给自己的判据背书等于没有背书。
这里跑的是合成注册表（argv 逃生口 + 临时子进程），真实仓库文件一个都不改；
每条用例写清「期望退出码 + 必须出现的判决行 + 必须不出现的措辞」，
其中「必须不出现」是这条脚本存在的理由：汇总器最容易犯的错，
是把「没跑」「只跑了一部分」「跑完了但什么都没判」都说成绿。

退出码：
  0 = 每条用例都符合期望
  1 = 至少一条不符（某条检测分支坏了，或坏了却安静地判绿）
  3 = 自检本身没跑完（用例数对不上、合成命令起不来）→ 本次结论不成立
"""

import contextlib
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import run_all_guards as R  # noqa: E402

EXIT_PASS, EXIT_FAIL, EXIT_FAULT = 0, 1, 3
# 裸下限：用例数由被检集合自己数的话，删掉一半用例仍然是绿。
MIN_CASES = 20

GREEN = "VERDICT-GREEN"
PYC = [sys.executable, "-X", "utf8", "-c"]


def guard(name, code, needles=(GREEN,), tiers=("core",), **kw):
    return R.E(name, R.PY, [], list(needles), list(tiers), argv=PYC + [code], **kw)


def fill(name, tiers=("precommit",), count=20):
    """造一批只声明不运行的条目，用来碰注册表形状分支。"""
    return [guard("%s-%d" % (name, i), "print('%s')\n" % GREEN, tiers=tiers)
            for i in range(count)]


OK = "print('%s')\n" % GREEN
SILENT = "pass\n"
RED = "import sys\nprint('%s')\nsys.exit(1)\n" % GREEN
STDERR = "import sys\nprint('%s', file=sys.stderr)\n" % GREEN
OTHER_LINE = "print('totally unrelated chatter')\n"
RC2 = "import sys\nprint('%s')\nsys.exit(2)\n" % GREEN
BAD_UTF8 = "import sys\nsys.stdout.buffer.write(b'\\xff\\xfe\\x00bad')\n"
SLEEPY = "import time\nprint('%s')\ntime.sleep(20)\n" % GREEN
BAD_INTERPRETER = R.Entry("dead-interpreter", R.PY, [], [GREEN], ["core"],
                          argv=[os.path.join(R.REPO_ROOT, "no-such-interpreter-xyz.exe"), "-c", "pass"])
MISSING_SCRIPT = R.E("missing-script", R.PY, [".codex/scripts/dev/does-not-exist.py"],
                     [GREEN], ["core"])


def pad_to_floor(guards):
    """把合成注册表补到真实下限，好让下限不替其余分支背书。

    下限本身只在 NO_PAD 那两条里检验。不去改 MIN_ROSTER 的数值：让自检替汇总器
    放宽判据，正是这条脚本要防的事。
    """
    out = list(guards)
    index = 0
    while (len(out) < R.MIN_ROSTER
           or len([g for g in out if "core" in g.tiers]) < R.MIN_CORE):
        out.append(guard("pad-%d" % index, OK))
        index += 1
    return out


# 这两条查的就是下限，所以不许补齐。
NO_PAD = frozenset(["空注册表 → 注册表故障",
                    "core 档被挪空 → 注册表故障（不许把守门降档来凑绿）"])


def run(guards, argv, pad=True):
    """在合成注册表上真实执行一次汇总器，返回 (退出码, 打印全文)。"""
    saved = R.GUARDS
    R.GUARDS = pad_to_floor(guards) if pad else list(guards)
    buffer = io.StringIO()
    try:
        with contextlib.redirect_stdout(buffer):
            code = R.main(list(argv))
    finally:
        R.GUARDS = saved
    return code, buffer.getvalue()


# (用例名, 注册表, 命令行, 期望退出码, 必须出现, 必须不出现)
CASES = [
    ("判绿的守门被认下来", [guard("green", OK)], [], EXIT_PASS,
     [GREEN, "PASS", "VERDICT: PASS"], []),
    ("rc=0 但一个字都不打 → FAIL（B3 的核心要求）", [guard("silent", SILENT)], [], EXIT_FAIL,
     ["no judgement line printed", "VERDICT: FAIL"], ["VERDICT: PASS"]),
    ("rc=1 且打了判据行 → FAIL", [guard("red", RED)], [], EXIT_FAIL,
     ["FAIL", "rc=1"], ["VERDICT: PASS"]),
    ("判据行只出现在 stderr 也算数", [guard("stderr", STDERR)], [], EXIT_PASS,
     ["VERDICT: PASS"], []),
    ("rc=2（既不是判绿也不是判红的集合）→ 无从认证", [guard("rc2", RC2)], [], EXIT_FAULT,
     ["INCONCLUSIVE", "无从认证"], ["VERDICT: PASS"]),
    ("注册的脚本文件不在场 → FAULT，不能当成通过", [MISSING_SCRIPT], [], EXIT_FAULT,
     ["FAULT", "script not found"], ["VERDICT: PASS"]),
    ("解释器起不来 → FAULT", [BAD_INTERPRETER], [], EXIT_FAULT,
     ["FAULT", "cannot start"], ["VERDICT: PASS"]),
    ("子进程输出不是合法 UTF-8 → FAULT（乱码不能算判据行）",
     [guard("badbytes", BAD_UTF8)], [], EXIT_FAULT,
     ["FAULT", "not valid UTF-8"], ["VERDICT: PASS"]),
    ("守门挂住不回 → FAIL 并写明超时", [guard("sleepy", SLEEPY, timeout=2)], [], EXIT_FAIL,
     ["timeout after 2s"], ["VERDICT: PASS"]),
    ("一条绿 + 一条哑：总体判红，但两条都要打印出来",
     [guard("green-a", OK), guard("silent-b", SILENT)], [], EXIT_FAIL,
     ["green-a", "silent-b", "fail=1", "VERDICT: FAIL"], ["VERDICT: PASS"]),
    ("一条判红 + 一条起不来：故障压过判红，退出码 3",
     [guard("red", RED), BAD_INTERPRETER], [], EXIT_FAULT,
     ["fail=1", "fault=1", "VERDICT: INCONCLUSIVE"], ["VERDICT: FAIL"]),
    ("空注册表 → 注册表故障", [], [], EXIT_FAULT,
     ["ROSTER FAULT", "roster below floor"], ["VERDICT: PASS"]),
    ("core 档被挪空 → 注册表故障（不许把守门降档来凑绿）",
     fill("precommit-only", tiers=("precommit",)), [], EXIT_FAULT,
     ["core tier below floor"], ["VERDICT: PASS"]),
    ("重名条目 → 注册表故障", [guard("dup", OK), guard("dup", OK)], [], EXIT_FAULT,
     ["duplicate entry name"], ["VERDICT: PASS"]),
    ("判据行了 = 没有判据行 → 注册表故障",
     [guard("empty-needle", OK, needles=[])], [], EXIT_FAULT,
     ["non-empty judgement line"], ["VERDICT: PASS"]),
    ("档位名写错 → 注册表故障",
     [guard("bad-tier", OK, tiers=("device",))], [], EXIT_FAULT,
     ["tiers must be"], ["VERDICT: PASS"]),
    ("pass_codes 不含 0 → 注册表故障",
     [R.E("no-zero", R.PY, [], [GREEN], ["core"], argv=PYC + [OK], pass_codes=(7,))],
     [], EXIT_FAULT, ["pass_codes must contain 0"], ["VERDICT: PASS"]),
    ("判绿码与判红码重叠 → 注册表故障",
     [R.E("overlap", R.PY, [], [GREEN], ["core"], argv=PYC + [OK], pass_codes=(0,), fail_codes=(0,))],
     [], EXIT_FAULT, ["overlap"], ["VERDICT: PASS"]),
    ("超时写成非正数 → 注册表故障",
     [guard("no-timeout", OK, timeout=0)], [], EXIT_FAULT,
     ["timeout must be a positive number"], ["VERDICT: PASS"]),
    ("--only 一个字都没选中 → 无从认证", [guard("green", OK)], ["--only", "zzz"], EXIT_FAULT,
     ["NO-SELECTION"], ["VERDICT: PASS"]),
    ("--only 只跑子集：绿话必须写明是子集",
     [guard("green", OK), guard("other", OK, needles=[GREEN], tiers=("precommit",))],
     ["--tier", "all", "--only", "green"], EXIT_PASS,
     ["subset=yes", "subset only"], ["selected guard(s) printed a judgement line"]),
    ("--tier precommit 而该档为空 → 无从认证",
     [guard("green", OK)], ["--tier", "precommit"], EXIT_FAULT,
     ["NO-SELECTION"], ["VERDICT: PASS"]),
    ("--list 打印整表且不运行任何守门",
     [guard("green", OK)], ["--list"], EXIT_PASS,
     ["ROSTER entries=", "green"], ["VERDICT: PASS (all"]),
    ("认不出的参数 → 用法错误，退出码 3",
     [guard("green", OK)], ["--bogus"], EXIT_FAULT,
     ["usage error", "EXITCODE=3"], ["VERDICT: PASS"]),
    ("真实注册表：形状合法、声明的脚本都在场、没用逃生口",
     None, "roster-shape", EXIT_PASS, ["entries=", "missing=0", "escape=0"], []),
    ("core 档仍覆盖那几支关键守门（删条目就会在这里断）",
     None, "core-keys", EXIT_PASS, ["core-keys=4/4"], []),
]

# core 档必须一直带着这几支：它们分别守着任务契约、栈事实、Android 静态契约与夹具字节。
CORE_KEYS = ["validate_tasks", "check_stack_facts",
             "check_androidtest_schema_expectations", "sync_fixtures --check"]


def probe(kind):
    """不跑子进程的两条：它们查的是「那张表还值不值得相信」。"""
    if kind == "core-keys":
        names = set(g.name for g in R.GUARDS if "core" in g.tiers)
        hits = [k for k in CORE_KEYS if k in names]
        return len(hits) == len(CORE_KEYS), "core-keys=%d/%d missing=%s" % (
            len(hits), len(CORE_KEYS), [k for k in CORE_KEYS if k not in names])
    problems = R.roster_problems(R.GUARDS)
    missing = [p for g in R.GUARDS for p in R.repo_paths(g) if not os.path.isfile(p)]
    escape = [g.name for g in R.GUARDS if g.argv]
    detail = "entries=%d core=%d problems=%d missing=%d escape=%d" % (
        len(R.GUARDS), len([g for g in R.GUARDS if "core" in g.tiers]),
        len(problems), len(missing), len(escape))
    if problems:
        detail += " first=%s" % problems[0][:60]
    return not problems and not missing and not escape, detail


def main():
    R.enable_utf8_stdout()
    if len(CASES) < MIN_CASES:
        print("SELFTEST harness error: 用例数 %d 低于裸下限 %d，本次结论不成立" % (
            len(CASES), MIN_CASES))
        print("OVERALL: INCONCLUSIVE")
        print("EXITCODE=%d" % EXIT_FAULT)
        return EXIT_FAULT

    executed = 0
    failures = []
    for name, guards, argv, want, needles, absent in CASES:
        executed += 1
        if guards is None:
            ok, detail = probe(argv)
            missing = [n for n in needles if n not in detail]
            ok = ok and not missing
            if not ok:
                failures.append("%s — %s missing=%s" % (name, detail, missing))
            print("CASE %-52s expected=%d actual=%s  %s" % (
                name, want, detail[:52], "Ok" if ok else "BROKEN"))
            continue
        try:
            got, out = run(guards, argv, pad=name not in NO_PAD)
        except Exception as exc:  # noqa: BLE001 — 合成命令起不来属仪器故障，不是判红
            failures.append("%s — cannot run: %r" % (name, exc))
            print("CASE %-52s expected=%d actual=crash  BROKEN" % (name, want))
            continue
        missing = [n for n in needles if n not in out]
        leaked = [n for n in absent if n in out]
        ok = got == want and not missing and not leaked
        if not ok:
            failures.append("%s — rc want=%d got=%d missing=%s forbidden=%s" % (
                name, want, got, missing, leaked))
        print("CASE %-52s expected=%d actual=%d  %s" % (
            name, want, got, "Ok" if ok else "BROKEN"))
        if not ok:
            for line in out.splitlines()[-6:]:
                print("      | %s" % line[:160])

    if executed != len(CASES):
        print("SELFTEST harness error: 执行 %d 条，注册了 %d 条" % (executed, len(CASES)))
        print("OVERALL: INCONCLUSIVE")
        print("EXITCODE=%d" % EXIT_FAULT)
        return EXIT_FAULT

    print("SELFTEST cases=%d executed=%d floor=%d broken=%d" % (
        len(CASES), executed, MIN_CASES, len(failures)))
    for failure in failures:
        print("  - %s" % failure[:200])
    print("OVERALL: %s" % ("PASS" if not failures else "FAIL"))
    code = EXIT_PASS if not failures else EXIT_FAIL
    print("EXITCODE=%d" % code)
    return code


if __name__ == "__main__":
    sys.exit(main())
