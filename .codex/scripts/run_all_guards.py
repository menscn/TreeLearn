#!/usr/bin/env python3
"""把仓库现存的只读守门收成一条命令，并且拒绝「退出码 0 但没打印判据」。

用法（从任意目录）：
    python .codex/scripts/run_all_guards.py [--tier core|precommit|external|all] [--only 子串]... [--list]

为什么要「判据行存在性」这第二道闸：一条守门返回 0 只说明它自己没报错，
不说明它判过。解释器换成了别的、路径解析失败、被检集合变空——这些形态都会让
脚本「安静地」什么都不判却仍打印 0。所以这里要求每条守门至少打出一行它自己的
判决行（注册表里逐条写明要找哪一行），找不到就算 FAIL，不看退出码。

分档：
  core        只读、确定性、不依赖构建产物或 git 增量（提交任何一片都要整跑）
  precommit   依赖未提交增量、构建产物或 Gradle 的那几支（提交前整跑）
  external    判定结果取决于**本机以外的东西**的那几支（此处指需要浏览器真的把原型渲染出来，
              而 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 React/Babel 走 unpkg CDN）。
              它留在注册表里、也被 `--tier all` 跑到，但不进 core：一台代理挂了的机器能让它必红，
              而那种红与产物无关（ROADMAP D11③、D14）。
  all         三档相加
不在注册表里的东西也打印出来，不是隐掉：需要实机或本机服务端的走查（web_walk 正文、
capture_screen、release-gate）属 ROADMAP 的 C4，不在只读汇总面内。

退出码（与仓库其余守门同一纪律）：
  0 = 入选守门全部打印了判据行且全部判绿
  1 = 至少一条判红，或至少一条没打印判据行（「没判据」不等于「通过」）
  3 = 本次结论不成立：汇总器没能把每条入选守门都跑起来（解释器或脚本缺失、注册表自相矛盾、
      过滤条件选中零条、输出不是合法 UTF-8），或至少一条守门**撞到自己的超时上限**——
      超时是「没判」，不是产物判红，所以它走 3 不走 1（ROADMAP D12）

任何一条不是 PASS 的守门都紧跟一段 `DIAG`：把那条守门自己的原始输出摆出来（短的输出整段，
长的取末尾），因为「哪一例红」必须在整跑读数里就有答案，而不是要人再单独跑一次那条命令
才知道（ROADMAP D11）。
"""

import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import REPO_ROOT, enable_utf8_stdout  # noqa: E402

EXIT_PASS = 0
EXIT_FAIL = 1
EXIT_FAULT = 3

TIERS = ("core", "precommit", "external")
# 注册表形状的裸下限：被检集合不能给自己定下限，否则删空整张表仍是绿。
MIN_ROSTER = 20
MIN_CORE = 18
DEFAULT_TIMEOUT = 300
# 上限同样是裸字面量：一条守门把 `timeout=` 写成 24 小时，它就永远不会「挂住不回」，
# 而「永不超时」和「判绿」在汇总面上长得一模一样（D12 那族放宽上限的冲动要在注册表就被挡住）。
MAX_TIMEOUT = 1800
# 红的时候最怕只看见一行汇总判决：子进程逐用例的输出此前被整个丢弃，
# 「哪一例红」在整跑读数里根本没有答案，必须再单独跑那条命令才知道（D11①）。
# 短输出整段打印，长输出只打印末尾——Gradle 那种几千行的不能灌进汇总面。
DIAG_FULL_MAX = 60
DIAG_TAIL_MAX = 30
DIAG_LINE_CHARS = 200

PY = "python"
NODE = "node"
PS = "powershell"


class Entry(object):
    """一条守门：怎么跑、必须看到哪一行、什么退出码算绿。

    `args[0]` 是相对仓库根的脚本路径，其余参数原样传递（不拼到路径上去）。
    `argv` 是给配套自检用的逃生口：注入用例需要直接给一条现成的命令行，
    而不是往仓库里塞一个临时脚本。
    rc_labels 只影响打印措辞（PS1 那几支的 1/2 各有本义），不改变判定：
    判定只有 pass_codes / fail_codes / 其余（=无从认证）三档。
    """

    def __init__(self, name, head, args, needles, tiers, pass_codes=(0,),
                 fail_codes=(1,), timeout=DEFAULT_TIMEOUT, rc_labels=None, argv=None):
        self.name = name
        self.head = head
        self.args = list(args)
        self.argv = list(argv) if argv else None
        self.needles = list(needles)
        self.tiers = list(tiers)
        self.pass_codes = list(pass_codes)
        self.fail_codes = list(fail_codes)
        self.timeout = timeout
        self.rc_labels = dict(rc_labels or {})


def E(*a, **kw):
    """注册表里的短名，纯粹为了让上表读起来是一列一列的。"""
    return Entry(*a, **kw)


CHECKS_NEEDLE = "check(s) failed"
SELFTEST_NEEDLE = "SELFTEST cases="

GUARDS = [
    # ── 仓库侧文档/流程守门（Checks 纪律：末尾必打 "<name>: N check(s) failed"）──
    E("validate_tasks", PY, [".codex/scripts/validate_tasks.py"],
      ["SUMMARY files="], ["core"]),
    E("check_context_pack", PY, [".codex/scripts/check_context_pack.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_code_reviewer_agent", PY, [".codex/scripts/check_code_reviewer_agent.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_codebase_researcher_agent", PY,
      [".codex/scripts/check_codebase_researcher_agent.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_code_simplifier_agent", PY,
      [".codex/scripts/check_code_simplifier_agent.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_loop_watcher_agent", PY,
      [".codex/scripts/check_loop_watcher_agent.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_source_of_truth", PY, [".codex/scripts/check_source_of_truth.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("run_evals", PY, [".codex/scripts/run_evals.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_eval_keeper_agent", PY, [".codex/scripts/check_eval_keeper_agent.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("metrics_report", PY, [".codex/scripts/metrics_report.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_metrics_reporter_agent", PY,
      [".codex/scripts/check_metrics_reporter_agent.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_intent_capturer_agent", PY, [".codex/scripts/check_intent_capturer_agent.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_intent_template", PY, [".codex/scripts/check_intent_template.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_pm_discovery_skill", PY, [".codex/scripts/check_pm_discovery_skill.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_roadmap_and_routing", PY, [".codex/scripts/check_roadmap_and_routing.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_spec_author_agent", PY, [".codex/scripts/check_spec_author_agent.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_stack_facts", PY, [".codex/scripts/dev/check_stack_facts.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_design_tokens", PY, [".codex/scripts/dev/check_design_tokens.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_pointer_liveness", PY, [".codex/scripts/check_pointer_liveness.py"],
      [CHECKS_NEEDLE], ["core"]),
    E("check_pointer_liveness --selftest", PY,
      [".codex/scripts/check_pointer_liveness.py", "--selftest"],
      [SELFTEST_NEEDLE], ["core"]),
    # ── Android 静态契约与判据自检（快、确定性、无设备）──
    E("check_androidtest_schema_expectations", PY,
      [".codex/scripts/dev/check_androidtest_schema_expectations.py"],
      ["VERDICT:"], ["core"]),
    E("check_run_all_guards", PY, [".codex/scripts/check_run_all_guards.py"],
      [SELFTEST_NEEDLE], ["core"]),
    E("checkers_selftest", PY, [".codex/scripts/dev/checkers_selftest.py"],
      ["OVERALL:"], ["core"]),
    E("parity_ledger_selftest", PY, [".codex/scripts/dev/parity_ledger_selftest.py"],
      ["VERDICT:"], ["core"]),
    E("screen_inventory_selftest", PY, [".codex/scripts/dev/screen_inventory_selftest.py"],
      ["VERDICT:"], ["core"]),
    E("measure_screen --selftest", PY,
      [".codex/scripts/dev/parity/measure_screen.py", "--selftest"],
      ["VERDICT:"], ["core"]),
    # ── Web 侧只读守门（node，无 node_modules 依赖）──
    # 一条要浏览器真跑出读数的守门有「harness 没材料」这条提前中止分支：它打
    # `FAIL vocabulary-layer-aborted` + `EXITCODE=3` 而**不打**汇总行 `cases=`。
    # 只把 `cases=` 当判据行时，那条诚实的「本轮没判」会被汇总器读成「没打印判据」＝判红——
    # 正是 SKILL.md「needles 必须在该条红路径上也在」要防的形状（实测读数见片 036 证据块 04）。
    E("verify_1to1_selftest", NODE, [".codex/scripts/dev/parity/verify_1to1_selftest.mjs"],
      ["cases=", "vocabulary-layer-aborted"], ["core"], timeout=600),
    # D11③：这一条要浏览器真把原型渲染出来，而原型的 React/Babel 来自 unpkg CDN（D14），
    # 本机系统代理一挂它必红——那种红与产物无关，所以它不进每片都要整跑的 core 档。
    # 它的裸下限分支同样绕开 `cases=`（打 `selftest-case-floor` 后直接 EXITCODE=3），第二判据行为此而设。
    # `timeout=600` 按实测：单跑三次读数 123.3s / 265.3s / 314.5s（314.5s 那次判 rc=1 并打印 `cases=17 failures=2`），
    # 默认 300s 会把最后那种姿势掐在判据行之前——掐断一条**会给判决**的守门，等于把 D11① 挣来的诊断又丢掉。
    E("cdp_shot_selftest", NODE, [".codex/scripts/dev/parity/cdp_shot_selftest.mjs"],
      ["cases=", "selftest-case-floor"], ["external"], timeout=600),
    E("web_walk --selftest", NODE, [".codex/scripts/dev/parity/web_walk.mjs", "--selftest"],
      [SELFTEST_NEEDLE], ["core"]),
    E("check_dependency_direction", NODE, ["frontend/scripts/check_dependency_direction.mjs"],
      ["dependency scan root="], ["core"]),
    E("check_dependency_direction --selftest", NODE,
      ["frontend/scripts/check_dependency_direction.mjs", "--selftest"],
      [SELFTEST_NEEDLE], ["core"]),
    E("check_user_copy_i18n", NODE, ["frontend/scripts/check_user_copy_i18n.mjs"],
      ["user-copy scan root="], ["core"]),
    E("check_user_copy_i18n --selftest", NODE,
      ["frontend/scripts/check_user_copy_i18n.mjs", "--selftest"],
      [SELFTEST_NEEDLE], ["core"]),
    E("check_device_time", NODE, ["frontend/scripts/check_device_time.mjs"],
      ["device-time scan root="], ["core"]),
    E("check_device_time --selftest", NODE,
      ["frontend/scripts/check_device_time.mjs", "--selftest"],
      [SELFTEST_NEEDLE], ["core"]),
    # 包内副本读到的是不是权威夹具的字节：漂移不判红，Web 就在跑一份旧基线。
    E("sync_fixtures --check", NODE, ["frontend/scripts/sync_fixtures.mjs", "--check"],
      ["SYNC CHECK"], ["core"]),
    E("sync_fixtures --selftest", NODE, ["frontend/scripts/sync_fixtures.mjs", "--selftest"],
      [SELFTEST_NEEDLE], ["core"]),
    # ── precommit：依赖未提交增量、构建产物或 Gradle ──
    E("check_offline_bundle", NODE, ["frontend/scripts/check_offline_bundle.mjs"],
      ["offline scan root="], ["precommit"]),
    E("check_offline_bundle --selftest", NODE,
      ["frontend/scripts/check_offline_bundle.mjs", "--selftest"],
      [SELFTEST_NEEDLE], ["precommit"]),
    E("frontend verify", NODE, ["frontend/scripts/verify.mjs"],
      ["verify steps="], ["precommit"], timeout=900),
    E("line-budget", PS, [".codex/skills/line-budget/line-budget.ps1"],
      ["业务变更行:", "[line-budget] 错误"], ["precommit"],
      pass_codes=(0,), fail_codes=(1, 2), rc_labels={1: "WARN", 2: "FAIL/错误"}),
    E("no-kotlin-check", PS, ["android/scripts/no-kotlin-check.ps1"],
      ["VERDICT:"], ["precommit"], rc_labels={2: "PARTIAL（跳过阶段，绝不是通过）"}),
    E("guard-selftest -Fast", PS, ["android/scripts/dev/guard-selftest.ps1", "-Fast"],
      ["OVERALL ="], ["precommit"]),
]


def roster_problems(guards):
    """注册表自相矛盾 ⇒ 结论不成立：这里查的是「这张表还值不值得跑」。"""
    problems = []
    if len(guards) < MIN_ROSTER:
        problems.append("roster below floor: %d entry(ies) < %d" % (len(guards), MIN_ROSTER))
    core = [g for g in guards if "core" in g.tiers]
    if len(core) < MIN_CORE:
        problems.append("core tier below floor: %d entry(ies) < %d" % (len(core), MIN_CORE))
    seen = set()
    for g in guards:
        if not g.name.strip():
            problems.append("entry with an empty name")
        elif g.name in seen:
            problems.append("duplicate entry name: %s" % g.name)
        seen.add(g.name)
        if not g.args and not g.argv:
            problems.append("%s: empty command" % g.name)
        if g.head not in (PY, NODE, PS):
            problems.append("%s: unknown interpreter %r" % (g.name, g.head))
        if not g.needles or any(not n.strip() for n in g.needles):
            problems.append("%s: needs at least one non-empty judgement line" % g.name)
        if not g.tiers or any(t not in TIERS for t in g.tiers):
            problems.append("%s: tiers must be a non-empty subset of %s" % (g.name, list(TIERS)))
        if 0 not in g.pass_codes:
            problems.append("%s: pass_codes must contain 0" % g.name)
        if set(g.pass_codes) & set(g.fail_codes):
            problems.append("%s: pass_codes and fail_codes overlap" % g.name)
        if not isinstance(g.timeout, (int, float)) or g.timeout <= 0:
            problems.append("%s: timeout must be a positive number" % g.name)
        elif g.timeout > MAX_TIMEOUT:
            problems.append("%s: timeout %ss above the %ss ceiling — 一条永不超时的守门与一条判绿"
                            "在汇总面上无法区分" % (g.name, g.timeout, MAX_TIMEOUT))
    return problems


def repo_paths(entry):
    """这条守门声明要用到的仓库文件（argv 逃生口里的东西不归仓库管，不检查）。"""
    if entry.argv or not entry.args:
        return []
    return [os.path.join(REPO_ROOT, a.replace("/", os.sep)) for a in entry.args if
            a.lower().endswith((".py", ".mjs", ".ps1"))]


def resolve(entry):
    """返回 (argv, 故障原因)；解释器或脚本不在场时绝不猜测、绝不静默跳过。"""
    if entry.argv:
        return list(entry.argv), None
    script = os.path.join(REPO_ROOT, entry.args[0].replace("/", os.sep))
    rest = [str(a) for a in entry.args[1:]]
    if entry.head == PY:
        return [sys.executable, "-X", "utf8", script] + rest, None
    if entry.head == NODE:
        node = os.environ.get("TREELEARN_NODE") or _which(("node", "node.exe"))
        if not node:
            return None, "node interpreter not found"
        return [node, script] + rest, None
    shell = _which(("powershell.exe", "pwsh.exe", "powershell", "pwsh"))
    if not shell:
        return None, "powershell interpreter not found"
    # 不先把控制台切成 UTF-8，PS 的输出会按系统代码页落进管道，判据行就不可读；
    # 而「判据行不可读」必须走到「无从认证」，不能被当成一条绿。
    command = "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; %s" % " ".join(
        ["& '%s'" % script.replace("'", "''")] + rest)
    return [shell, "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command], None


def _which(candidates):
    import shutil
    for name in candidates:
        found = shutil.which(name)
        if found:
            return found
    return None


def judge(entry, rc, output):
    """把一次真实执行归成 PASS / FAIL / NO-VERDICT / INCONCLUSIVE 四行之一。"""
    hit = [line for line in output.splitlines() if any(n in line for n in entry.needles)]
    if not hit:
        return "FAIL", "no judgement line printed (rc=%s, wanted %s)" % (
            rc, " or ".join('"%s"' % n for n in entry.needles)), ""
    label = entry.rc_labels.get(rc, "")
    if rc in entry.pass_codes:
        return "PASS", "rc=%s%s" % (rc, " " + label if label else ""), hit[0].strip()
    if rc in entry.fail_codes:
        return "FAIL", "rc=%s%s" % (rc, " " + label if label else ""), hit[0].strip()
    return "INCONCLUSIVE", "rc=%s%s — 退出码不在该守门的判绿/判红集合里，本次无从认证" % (
        rc, " " + label if label else ""), hit[0].strip()


def _decode(stdout, stderr):
    """两路管道拼成一段文本；不是合法 UTF-8 时返回 None（调用方据此判仪器故障）。"""
    try:
        return (stdout or b"").decode("utf-8") + (stderr or b"").decode("utf-8")
    except UnicodeDecodeError:
        return None


def diag(output):
    """非 PASS 时要摆出来的原始输出：短的就整段，长的只取末尾，行宽一律截断。

    整段/末尾的分界按**非空行**数算，因为判红输出常见形态是十几行逐用例加一行汇总，
    整段打印才能把「哪一例红」留在汇总面上；而构建类守门的输出动辄几千行，
    那种只能看末尾（真实红因通常在最后）。
    """
    lines = [line for line in output.splitlines() if line.strip()]
    if not lines:
        return "empty", 0, []
    if len(lines) <= DIAG_FULL_MAX:
        return "full", len(lines), lines
    return "tail", len(lines), lines[-DIAG_TAIL_MAX:]


def execute(entry):
    """跑一条守门，返回 (状态, 详情, 判据行摘录, 原始输出)。

    状态有 PASS / FAIL / INCONCLUSIVE / TIMEOUT / FAULT 五种： TIMEOUT 与 FAIL 分开计，
    因为「被自己的上限掐断」是**没判**，借判红那个词会让下一位实现者去查一个不存在的回归（D12）。
    """
    argv, fault = resolve(entry)
    if fault:
        return "FAULT", fault, "", ""
    for path in repo_paths(entry):
        if not os.path.isfile(path):
            return "FAULT", "script not found: %s" % path, "", ""
    start = time.time()
    try:
        # 先拿裸字节再自己解码：Windows 上 subprocess 的读取线程会把解码异常打在 stderr
        # 里然后当成「没输出」，于是「乱码」会被读成「这条守门没判据」——两个不同的病因，
        # 一个是仪器坏了，一条是判据没打印，不能混。
        # 也不用 subprocess.run：它超时后 kill + 无限等管道，守门的孙进程握着管道时汇总器
        # 会跟着一起挂死，那比判红更糟。这里的每一次读都带 deadline。
        process = subprocess.Popen(argv, cwd=REPO_ROOT, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, stdin=subprocess.DEVNULL)
    except OSError as exc:
        return "FAULT", "cannot start: %s" % exc, "", ""
    deadline = start + entry.timeout
    try:
        stdout, stderr = process.communicate(timeout=max(0.1, deadline - time.time()))
    except subprocess.TimeoutExpired:
        process.kill()
        # 第二次读同样带 deadline：孙进程握着管道时这里也会挂，那份输出就只能放弃，
        # 但「放弃了」要写进读数里，不能悄悄返回一个空串冒充「它一个字都没打」。
        stdout = stderr = b""
        try:
            stdout, stderr = process.communicate(timeout=5)
            partial = ""
        except subprocess.TimeoutExpired:
            partial = "（管道没排空，超时前的输出取不到）"
        return "TIMEOUT", "timeout after %ss — 本轮对这条没有判据，既不是绿也不是红%s" % (
            entry.timeout, "" if not partial else "：" + partial), "", _decode(stdout, stderr) or ""
    output = _decode(stdout, stderr)
    if output is None:
        return "FAULT", "output is not valid UTF-8 — 判据行不可读就不能报通过", "", ""
    status, detail, excerpt = judge(entry, process.returncode, output)
    return status, "%s %.1fs" % (detail, time.time() - start), excerpt, output


def selected(guards, tier, filters):
    if tier == "all":
        pool = list(guards)
    else:
        pool = [g for g in guards if tier in g.tiers]
    if not filters:
        return pool, []
    picked, dropped = [], []
    for g in pool:
        (picked if any(f.lower() in g.name.lower() for f in filters) else dropped).append(g)
    return picked, dropped


def main(argv):
    enable_utf8_stdout()
    tier = "core"
    filters = []
    listing = False
    index = 0
    while index < len(argv):
        arg = argv[index]
        if arg == "--tier" and index + 1 < len(argv):
            tier = argv[index + 1]
            index += 1
        elif arg == "--only" and index + 1 < len(argv):
            filters.append(argv[index + 1])
            index += 1
        elif arg == "--list":
            listing = True
        elif arg in ("-h", "--help"):
            print(__doc__)
            return EXIT_PASS
        else:
            print("usage error: unrecognized argument %s" % arg)
            print("usage: python .codex/scripts/run_all_guards.py "
                  "[--tier core|precommit|external|all] [--only 子串] [--list]")
            print("VERDICT: INCONCLUSIVE (the command line asked for nothing that can be judged)")
            print("EXITCODE=%d" % EXIT_FAULT)
            return EXIT_FAULT
        index += 1

    problems = roster_problems(GUARDS)
    for problem in problems:
        print("ROSTER FAULT %s" % problem)
    if problems:
        print("VERDICT: INCONCLUSIVE (the roster itself is inconsistent)")
        print("EXITCODE=%d" % EXIT_FAULT)
        return EXIT_FAULT

    if listing:
        print("ROSTER entries=%d %s" % (
            len(GUARDS), " ".join("%s=%d" % (tier, len([g for g in GUARDS if tier in g.tiers]))
                                  for tier in TIERS)))
        for g in GUARDS:
            print("%-14s %-38s %s" % ("/".join(g.tiers), g.name,
                                      " ".join('"%s"' % n for n in g.needles)))
        print("EXITCODE=%d" % EXIT_PASS)
        return EXIT_PASS

    pool, dropped = selected(GUARDS, tier, filters)
    if not pool:
        print("NO-SELECTION tier=%s filters=%s — 一条守门都没选中，本次无从认证" % (
            tier, filters))
        print("VERDICT: INCONCLUSIVE")
        print("EXITCODE=%d" % EXIT_FAULT)
        return EXIT_FAULT

    # subset 回答的是「整表是不是都被判过了」，不是「有没有 --only」。
    # 只看 dropped 的话，`--tier core` 会打印 `subset=no` 而它其实只跑了 43 条里的 36 条——
    # 那一行会把「档位选择」读成「全表已判」，正是 D11③ 挪档之后最容易被误用的措辞。
    unjudged = len(GUARDS) - len(pool)
    print("RUN-ALL-GUARDS tier=%s selected=%d/%d subset=%s" % (
        tier, len(pool), len(GUARDS), "yes" if unjudged else "no"), flush=True)
    counts = {"PASS": 0, "FAIL": 0, "INCONCLUSIVE": 0, "TIMEOUT": 0, "FAULT": 0}
    for entry in pool:
        status, detail, excerpt, output = execute(entry)
        counts[status] += 1
        print("%-12s %-38s %s" % (status, entry.name, detail), flush=True)
        if excerpt:
            print("             > %s" % excerpt[:160], flush=True)
        if status != "PASS" and status != "FAULT":
            mode, total, lines = diag(output)
            if lines:
                print("             DIAG %s lines=%d/%d mode=%s" % (
                    entry.name, len(lines), total, mode), flush=True)
                for line in lines:
                    print("             | %s" % line[:DIAG_LINE_CHARS], flush=True)
    if unjudged:
        print("NOTE         %-38s 未选中：%d 条不在本次判定面内（%s），整表绿不由此声明" % (
            "not-selected", unjudged,
            "--only 过滤 + 档位选择" if dropped else "档位选择"))

    print("RUN-ALL-GUARDS pass=%d fail=%d inconclusive=%d fault=%d timeout=%d" % (
        counts["PASS"], counts["FAIL"], counts["INCONCLUSIVE"], counts["FAULT"],
        counts["TIMEOUT"]))
    if counts["FAULT"]:
        print("VERDICT: INCONCLUSIVE (some guard never ran, so this run proves nothing)")
        code = EXIT_FAULT
    elif counts["TIMEOUT"]:
        print("VERDICT: INCONCLUSIVE (%d guard(s) hit their own timeout, so nothing was "
              "judged for them — 重跑或换串行姿势，别去查不存在的回归)" % counts["TIMEOUT"])
        code = EXIT_FAULT
    elif counts["INCONCLUSIVE"]:
        print("VERDICT: INCONCLUSIVE (a guard returned an unjudged exit code)")
        code = EXIT_FAULT
    elif counts["FAIL"]:
        print("VERDICT: FAIL (%d guard(s) red or silent)" % counts["FAIL"])
        code = EXIT_FAIL
    elif unjudged:
        print("VERDICT: PASS (subset only — %d/%d entry(ies) judged, not the whole roster)" % (
            len(pool), len(GUARDS)))
        code = EXIT_PASS
    else:
        print("VERDICT: PASS (all %d selected guard(s) printed a judgement line and returned green)"
              % len(pool))
        code = EXIT_PASS
    print("EXITCODE=%d" % code)
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
