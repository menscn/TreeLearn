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
# 片 036 抬过一次：新增的那几条全部围绕「非 PASS 时把子进程输出摆出来」「超时不再借判红那个词」
# 与「环境依赖不进 core」，用例面涨了就不许再用旧下限给新面背书。
# 当前实有多少条不写在这里——那是一个会随每片漂移的数，由本支脚本自己打印（`SELFTEST cases=`）；
# 这里的字面量只回答「少到多少条就不能再算判过」。
MIN_CASES = 26

GREEN = "VERDICT-GREEN"
PYC = [sys.executable, "-X", "utf8", "-c"]
# 兜底值只为了一件事：RED 对照要把这支电池架在**没有超时上限**的旧实现上跑。
# 直接写 R.MAX_TIMEOUT 会在构造用例表时 AttributeError，整支电池崩成仪器故障，
# 那份读数就说不出「哪一条新判据在旧实现里是缺的」——而这正是 RED 要回答的问题。
CEILING = getattr(R, "MAX_TIMEOUT", 1800)


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
# D11 的正例形态：逐用例的行与汇总行都打出来，汇总行含 needle，退出码 1。
# 「needle 命中那一行」恰好是最后一条，所以旧实现里那条 `FAIL neg-*` 的用例名会被丢掉。
RED_WITH_CASES = ("import sys\n"
                  "print('ok   pos-real-render rc=0')\n"
                  "print('FAIL neg-wait-for-absent rc=1 want=0')\n"
                  "print('%s cases=2 failures=1')\n"
                  "sys.exit(1)\n" % GREEN)
# 构建类守门的形态：输出远超 DIAG_FULL_MAX，只能取末尾，且读数里要写明取的是末尾。
# 这段子进程代码里本来就带 `%03d` 和 `% i`，再用一次外层 `% GREEN` 会把它当成自己的转换符吃掉
# （RED 那轮真实撞过：`TypeError: %i format`）——所以这里改用拼接，needle 不进格式化。
LONG_OUT = ("import sys\n"
            "for i in range(1, 81):\n    print('line-%03d' % i)\n"
            "print(" + repr(GREEN) + ")\n"
            "sys.exit(1)\n")
# 挂住不回但在此之前已经打了逐用例行：超时那份输出也必须进 DIAG，否则「超时」连
# 「它当时在判哪一例」都不肯留下。flush 是必需的——被 kill 的进程不会替缓冲区收尾。
PARTIAL_SLEEPY = ("import sys, time\n"
                  "print('%s')\n"
                  "print('CASE-ALPHA 是子进程自己的逐用例行')\n"
                  "sys.stdout.flush()\n"
                  "time.sleep(20)\n" % GREEN)
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
    ("守门挂住不回 → 超时算「本轮没判」，走退出码 3，不借判红那个词（D12）",
     [guard("sleepy", SLEEPY, timeout=2)], [], EXIT_FAULT,
     ["TIMEOUT", "timeout after 2s", "timeout=1", "nothing was judged for them"],
     ["VERDICT: PASS", "fail=1", "VERDICT: FAIL"]),
    ("超时之前子进程已经打出来的逐用例行要进 DIAG",
     [guard("partial-sleepy", PARTIAL_SLEEPY, timeout=2)], [], EXIT_FAULT,
     ["TIMEOUT", "CASE-ALPHA", "mode=full"], ["VERDICT: PASS"]),
    ("判红时把子进程自己的用例名一起打印（D11：红因不能只留在单独一次直跑里）",
     [guard("red-with-cases", RED_WITH_CASES)], [], EXIT_FAIL,
     ["FAIL", "DIAG", "mode=full", "neg-wait-for-absent"], ["VERDICT: PASS"]),
    ("长输出只取末尾，并在读数里写明取的是末尾",
     [guard("long-out", LONG_OUT)], [], EXIT_FAIL,
     ["DIAG", "mode=tail", "line-080"], ["line-001", "VERDICT: PASS"]),
    ("判绿的守门不附带 DIAG（汇总面不能被绿条目的流水输出灌满）",
     [guard("green", OK)], [], EXIT_PASS, ["VERDICT: PASS"], ["DIAG"]),
    ("超时上限写超过 MAX_TIMEOUT → 注册表故障：一条永不超时的守门与一条判绿无法区分",
     [guard("ceiling", OK, timeout=CEILING + 1)], [], EXIT_FAULT,
     ["ROSTER FAULT", "ceiling"], ["VERDICT: PASS"]),
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
    ("--tier external 只选外部依赖档，core 条目一条都不进判定面（D11③ 的档位边界）",
     [guard("core-one", OK), guard("ext-one", OK, tiers=("external",))],
     ["--tier", "external"], EXIT_PASS,
     ["tier=external selected=1", "ext-one", "subset=yes"], ["core-one"]),
    ("--list 打印整表且不运行任何守门",
     [guard("green", OK)], ["--list"], EXIT_PASS,
     ["ROSTER entries=", "green"], ["VERDICT: PASS (all"]),
    ("认不出的参数 → 用法错误，退出码 3",
     [guard("green", OK)], ["--bogus"], EXIT_FAULT,
     ["usage error", "EXITCODE=3"], ["VERDICT: PASS"]),
    ("真实注册表：形状合法、声明的脚本都在场、没用逃生口",
     None, "roster-shape", EXIT_PASS, ["entries=", "missing=0", "escape=0"], []),
    ("core 档仍覆盖那几支关键守门（删条目就会在这里断）",
     None, "core-keys", EXIT_PASS, ["core-keys=5/5"], []),
    ("同时挂 external 与 core 两档 → 注册表故障：档位声明不许是装饰（片 037 用结构规则替掉按名字钉人）",
     [guard("core-one", OK), guard("double", OK, tiers=("core", "external"))], [], EXIT_FAULT,
     ["ROSTER FAULT", "both external and core"], ["VERDICT: PASS"]),
]

# core 档必须一直带着这几支：它们分别守着任务契约、栈事实、Android 静态契约、夹具字节，
# 以及**汇总器自己的判定分支**——最后一支在片 036 之前只写在 SKILL.md 的「改过判定分支必须整跑自检」
# 那句话里，没有任何常驻仪器替这句话负责。
# 「需要外网的不得混进 core 档」这一半不再按名字钉人：片 037 闭合 D14 之后 `cdp_shot_selftest`
# 不再是环境依赖（原型脚本已按 sha384 收编进仓库，代理不通也判绿），把它留在 external 档等于在
# 没有理由的时候放宽判定面。改由 `run_all_guards.roster_problems` 的结构规则守（同时挂两档＝注册表故障），
# 上面那条注入用例替这条规则作证。
CORE_KEYS = ["validate_tasks", "check_stack_facts",
             "check_androidtest_schema_expectations", "sync_fixtures --check",
             "check_run_all_guards"]


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
