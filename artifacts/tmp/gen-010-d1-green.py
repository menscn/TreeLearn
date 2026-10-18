"""T-2026-10-18-010 GREEN：当前态三条判据、窗口三档、以及那条一直被打死的电池。

用法（从仓库根）：
    python -X utf8 artifacts/tmp/gen-010-d1-green.py

判读约定：总退出码由每块的 want/got 期望表派生（不硬写 0）；任何前置条件不成立、
任何归因落不到本块的读数，一律落 3（仪器故障 / 现在还不能认证），不落 0 也不落 1。

名单边界（与 AGENTS.md [2026-10-16] [evidence] ②③ 同形）：块 09 的判定前指纹只列
**本运行只读、且在同一窗口末尾确实回算过**的文件。本运行自己写出的产物（本文件
写出的 GREEN 产物）不进名单——给一个会被自己下一行改写的对象取指纹，读者按它复核
只能得出「产物被人改过」的反向结论。

正文不出现 7 到 40 位的裸十六进制词：守门的 NOTE/FAIL 见证行自带 base=<rev-parse 前 12 位>，
嵌入产物前一律经 scrub() 换成 <hex-redacted>（块 09 另给一条形状对照，说明 64 位全文
之所以能打印，是因为该扫描要求整词边界，而不是因为它放过哈希）。
"""

import difflib
import hashlib
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
EV = os.path.join(REPO, "artifacts", "evidence")
OUT = os.path.join(EV, "T-2026-10-18-harness-roadmap-guard-current-state-010-green.txt")

CHECKER_REL = ".trae/scripts/check_roadmap_and_routing.py"
LIB_REL = ".trae/scripts/harness_check_lib.py"
SELFTEST_REL = ".trae/scripts/dev/checkers_selftest.py"
ROADMAP_REL = ".trae/ROADMAP.md"
AGENTS_REL = "AGENTS.md"
CARD = "T-2026-10-18-harness-roadmap-guard-current-state-010.md"
PRE = "T-2026-10-18-010-pre-fix-"
SIBLINGS = ["artifacts/tmp/gen-004-green.sh", "artifacts/tmp/gen-005-green.sh",
            "artifacts/tmp/gen-007-green.sh"]

NEW_LABELS = ["AGENTS.md routing table currently has >=",
              "current routing rows cover all four triggers",
              "every current routing-row target exists"]
WINDOW_LABELS = ["AGENTS.md change contains no deletion line",
                 "every pre-existing routing row and case record survives verbatim"]
NOTE_LABELS = ["SKIPPED append-only guard has no uncommitted AGENTS.md increment",
               "routing-row increment is printed, not judged"]
ROUTING_CASES = ["roadmap:routing-floor-load-bearing", "roadmap:routing-table-truncated",
                 "roadmap:routing-trigger-lost", "roadmap:routing-target-dead"]
NEUTRAL = ["SIBLING TIER=", "tier read live"]
BANNED = ["PRE-EXISTING DEBT"]
OLD_REFS = ["MIN_NEW_ROUTES", "AGENTS.md adds >= ", "new routing rows cover all four triggers"]

HEX_WORD = re.compile(r"\b[0-9a-f]{7,40}\b")
MIN_BLOCKS = 9
MIN_CASES = 50

EXPECT = {}
ORDER = []
FAULTS = []
CUR = [None]
SNAPSHOT = {}

PROD_FILES = [CHECKER_REL, LIB_REL, SELFTEST_REL, ROADMAP_REL, AGENTS_REL,
              ".trae/tasks/" + CARD] + SIBLINGS + [
              "artifacts/evidence/" + PRE + "check_roadmap_and_routing.py",
              "artifacts/evidence/" + PRE + "harness_check_lib.py",
              "artifacts/evidence/" + PRE + "checkers_selftest.py"]


def emit(fh, line):
    fh.write(line + "\n")
    fh.flush()


def scrub(text):
    return HEX_WORD.sub("<hex-redacted>", text)


def run(args, cwd=REPO, env=None):
    e = dict(os.environ)
    if env:
        e.update(env)
    p = subprocess.run(args, cwd=cwd, capture_output=True, text=True,
                       encoding="utf-8", errors="replace", env=e)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def sha(path):
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def abs_path(rel):
    return os.path.join(REPO, rel.replace("/", os.sep))


def read(rel):
    with open_text(abs_path(rel)) as fh:
        return fh.read()


def open_text(path, mode="r"):
    import io
    # newline="" 保持原字节：Windows 上文本模式读写会把 CRLF 归一成 LF，
    # 「还原成功但字节已变」是本仓库栽过的形态。
    return io.open(path, mode, encoding="utf-8", newline="")


def block(tag, label):
    def deco(fn):
        def wrapper(fh):
            emit(fh, "===== block %s - %s =====" % (tag, label))
            before = len(FAULTS)
            CUR[0] = tag
            try:
                fn(fh)
            except Exception as exc:  # noqa: BLE001 - 仪器故障不得伪装成用例不符
                emit(fh, "  INSTRUMENT FAULT in block %s: %s: %s" %
                     (tag, type(exc).__name__, exc))
                FAULTS.append(tag)
            CUR[0] = None
            expect = 3 if tag in FAULTS else 0
            got = 3 if len(FAULTS) > before else 0
            EXPECT[tag] = expect
            ORDER.append(tag)
            emit(fh, "  block %s: want=%d got=%d" % (tag, expect, got))
            emit(fh, "")
        wrapper.__name__ = fn.__name__
        return wrapper
    return deco


def fail(fh, text):
    emit(fh, "  FAIL " + text)
    FAULTS.append(CUR[0] or "fault")


def battery():
    sys.path.insert(0, abs_path(SELFTEST_REL).rsplit(os.sep, 1)[0])
    import checkers_selftest  # noqa: N813 - 复用生产者自己的建盘逻辑，不重写一份
    return checkers_selftest


def make_mirror(prefix):
    import shutil
    import tempfile
    root = tempfile.mkdtemp(prefix=prefix)
    built, note = battery().build_workspace(root)
    return root, built, scrub(str(note)), shutil


def ok_lines(out, label):
    return [l for l in out.splitlines() if l.startswith("OK   ") and label in l]


def fail_lines(out, label):
    return [l for l in out.splitlines() if l.startswith("FAIL ") and label in l]


def note_lines(out, label):
    return [l for l in out.splitlines() if l.startswith("NOTE ") and label in l]


def measure(text):
    m = re.search(r"measured(?:_rows|_targets)?=(\d+)", text)
    return int(m.group(1)) if m else None


def section(lines, heading_prefix):
    """取某条 ### 标题到下一个 ##/### 标题之间的正文（含标题行本身）。

    块 06 用它而不是整文件子串扫描：`处置` / `回读` 这类词在 ROADMAP 的其它小节里
    同样出现，按全文扫会把别的小节的行算进本条闭合，那条判据因此从未真正限定作用域。
    """
    for i, l in enumerate(lines):
        if l.startswith(heading_prefix):
            out = [l]
            for nxt in lines[i + 1:]:
                if re.match(r"^#{2,3} ", nxt):
                    break
                out.append(nxt)
            return out
    return []


@block("01", "the three current-state predicates are green with non-empty match sets")
def b01(fh):
    src = read(CHECKER_REL)
    m = re.findall(r"(?m)^MIN_ROUTE_ROWS = (\d+)$", src)
    rc, out = run([sys.executable, "-X", "utf8", abs_path(CHECKER_REL)])
    out = scrub(out)
    trio = [ok_lines(out, l) for l in NEW_LABELS]
    row_m = measure(trio[0][0]) if trio[0] else None
    trig = re.search(r"measured_rows=(\d+) missing=(\[[^\]]*\])", trio[1][0]) if trio[1] else None
    targ = re.search(r"measured_targets=(\d+) dead=(\[[^\]]*\])", trio[2][0]) if trio[2] else None
    tally = re.search(r"(\d+) check\(s\) failed", out)
    emit(fh, "  守门 rc=%d 结论行=%s" % (rc, tally.group(1) if tally else "unreadable"))
    for l in [x[0] for x in trio if x]:
        emit(fh, "  " + l.strip())
    emit(fh, "  三条当前态判据各打一条 OK=%s；下限是源里的裸字面量=%s"
         % (all(bool(x) for x in trio), len(m) == 1))
    emit(fh, "  匹配集非空三件：路由行 %s >= 下限 %s、触发词缺失集 %s、链接目标 %s 个且死链 %s"
         % (row_m, m[0] if m else None, trig.group(2) if trig else None,
            targ.group(1) if targ else None, targ.group(2) if targ else None))
    bad = (rc != 0 or not all(bool(x) for x in trio) or len(m) != 1
           or row_m is None or row_m < int(m[0])
           or not trig or int(trig.group(1)) != row_m or trig.group(2) != "[]"
           or not targ or int(targ.group(1)) < 1 or targ.group(2) != "[]"
           or not tally or tally.group(1) != "0")
    if bad:
        fail(fh, "current-state trio not all green on today's tree: rc=%s floor=%s rows=%s "
                 "trig=%s targ=%s tally=%s" % (rc, m, row_m,
                                               trig.groups() if trig else None,
                                               targ.groups() if targ else None,
                                               tally.group(1) if tally else None))


@block("02", "the row floor is a bare literal and is load-bearing")
def b02(fh):
    import shutil
    src = read(CHECKER_REL)
    shape = re.compile(r"^MIN_ROUTE_ROWS = \d+$")
    assigns = [l for l in src.splitlines() if l.startswith("MIN_ROUTE_ROWS =")]
    lit = [l for l in assigns if shape.match(l)]
    derived = [l for l in assigns if not shape.match(l)]
    pos_lit = bool(shape.match("MIN_ROUTE_ROWS = 7"))
    pos_der = bool(shape.match("MIN_ROUTE_ROWS = len(rows)")) is False \
        and "MIN_ROUTE_ROWS = len(rows)".startswith("MIN_ROUTE_ROWS =")
    emit(fh, "  赋值行=%d，其中整行等于「常量 = 数字」=%d（必须各 1）；不合规形状=%s"
              % (len(assigns), len(lit), derived))
    emit(fh, "  形状判据正控：字面量被认=%s、派生式不被认=%s（否则会响的扫描与空扫描同形；"
              "早先用 \\s*=\\s*[^0-9] 找派生式会在合法行上也响，因为 \\s 能吃掉等号后的空格）"
         % (pos_lit, pos_der))
    rc0, out0 = run([sys.executable, "-X", "utf8", abs_path(CHECKER_REL)])
    out0 = scrub(out0)
    rows0 = ok_lines(out0, NEW_LABELS[0])
    n0 = measure(rows0[0]) if rows0 else None
    root, built, note, _ = make_mirror("tlx-010fl-")
    try:
        if not built:
            fail(fh, "mirror workspace could not be built - inconclusive")
            return
        mirror_checker = os.path.join(root, ".trae", "scripts", "check_roadmap_and_routing.py")
        text = read(CHECKER_REL)
        lifted = re.sub(r"(?m)^MIN_ROUTE_ROWS = \d+$", "MIN_ROUTE_ROWS = 999", text, count=1)
        if lifted == text:
            fail(fh, "the floor literal could not be lifted in the mirror copy")
            return
        with open_text(mirror_checker, "w") as fh2:
            fh2.write(lifted)
        rc, out = run([sys.executable, "-X", "utf8", mirror_checker], cwd=root)
        out = scrub(out)
        bad = fail_lines(out, "AGENTS.md routing table currently has >= 999 linked rows")
        others = [bool(ok_lines(out, l)) for l in NEW_LABELS[1:]]
        n = measure(bad[0]) if bad else None
        emit(fh, "  镜像里把下限抬到 999：rc=%d 该条判红=%s 读数=%s（真树同文件读数=%s）"
             % (rc, bool(bad), n, n0))
        for l in bad[:1]:
            emit(fh, "  " + l.strip())
        emit(fh, "  另外两条当前态判据在该次运行仍 OK=%s（红只归因于下限，不是整表失效）" % all(others))
        if not lit or derived or not pos_lit or not pos_der or rc0 != 0 or n0 is None \
                or rc != 1 or not bad or n != n0 or not all(others):
            fail(fh, "floor is not a bare literal, or raising it did not turn exactly one predicate red: "
                     "lit=%s derived=%d rc0=%s rc=%s bad=%s n=%s n0=%s others=%s"
                 % (lit, len(derived), rc0, rc, bool(bad), n, n0, others))
    finally:
        shutil.rmtree(root, ignore_errors=True)


@block("03", "the uncommitted-window predicates land in their own tier per shape")
def b03(fh):
    import shutil
    root, built, note, _ = make_mirror("tlx-010win-")
    try:
        if not built:
            fail(fh, "mirror workspace could not be built - inconclusive")
            return
        checker = os.path.join(root, ".trae", "scripts", "check_roadmap_and_routing.py")
        agents = os.path.join(root, AGENTS_REL)
        # 干净档：镜像的 HEAD 就是真仓库 HEAD:AGENTS.md 的字节；把工作树摆回同一状态，
        # 让三档与真实工作树此刻脏不脏无关（否则本块判的是别人的在飞工作）。
        subprocess.run(["git", "checkout", "--", AGENTS_REL], cwd=root,
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
        rc_clean, out_clean = run([sys.executable, "-X", "utf8", checker], cwd=root)
        out_clean = scrub(out_clean)
        skipped = note_lines(out_clean, NOTE_LABELS[0])
        judged_clean = ok_lines(out_clean, WINDOW_LABELS[0]) + fail_lines(out_clean, WINDOW_LABELS[0])
        emit(fh, "  档 1 无增量：rc=%d；NOTE SKIPPED 行=%d（必须 1）；被判决的 no-deletion 行=%d"
                  "（必须 0：空窗口打 OK 就是把「无从认证」读成「认证通过」）"
             % (rc_clean, len(skipped), len(judged_clean)))
        for l in skipped[:1]:
            emit(fh, "  " + l.strip())

        with open_text(agents, "a") as fh2:
            fh2.write("| `Docs/spec/**` | [规格](Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md)"
                      " | 复刻方案与判据改动前 |\n")
        rc_add, out_add = run([sys.executable, "-X", "utf8", checker], cwd=root)
        out_add = scrub(out_add)
        ok_nodev = ok_lines(out_add, WINDOW_LABELS[0])
        inc_note = note_lines(out_add, NOTE_LABELS[1])
        survived = ok_lines(out_add, WINDOW_LABELS[1])
        emit(fh, "  档 2 只新增：rc=%d；no-deletion 判 OK=%s；逐字存活判 OK=%s；增量打印行=%d"
             % (rc_add, bool(ok_nodev), bool(survived), len(inc_note)))
        for l in (ok_nodev + inc_note)[:2]:
            emit(fh, "  " + l.strip())

        with open_text(agents) as fh2:
            text = fh2.read()
        dropped = [l for l in text.split("\n") if "](.trae/rules/android.md)" in l]
        with open_text(agents, "w") as fh2:
            fh2.write("\n".join(l for l in text.split("\n")
                                if "](.trae/rules/android.md)" not in l))
        rc_del, out_del = run([sys.executable, "-X", "utf8", checker], cwd=root)
        out_del = scrub(out_del)
        f1 = fail_lines(out_del, WINDOW_LABELS[0])
        f2 = fail_lines(out_del, WINDOW_LABELS[1])
        trio_ok = [bool(ok_lines(out_del, l)) for l in NEW_LABELS]
        emit(fh, "  档 3 删一行（锚点命中=%d）：rc=%d；no-deletion 判红=%s；逐字存活判红=%s；"
                 "三条当前态判据仍全 OK=%s（红只归因于窗口形状，不是整表失效）"
             % (len(dropped), rc_del, bool(f1), bool(f2), all(trio_ok)))
        for l in (f1 + f2)[:2]:
            emit(fh, "  " + l.strip())
        if rc_clean != 0 or len(skipped) != 1 or judged_clean \
                or rc_add != 0 or not ok_nodev or not survived or not inc_note \
                or len(dropped) != 1 or rc_del != 1 or not f1 or not f2 or not all(trio_ok):
            fail(fh, "window tiers wrong: clean=%s skipped=%d judged=%d add=%s ok_nodev=%s "
                     "survived=%s note=%d dropped=%d del=%s f1=%s f2=%s trio_ok=%s"
                 % (rc_clean, len(skipped), len(judged_clean), rc_add, bool(ok_nodev),
                    bool(survived), len(inc_note), len(dropped), rc_del, bool(f1), bool(f2),
                    trio_ok))
    finally:
        shutil.rmtree(root, ignore_errors=True)


@block("04", "the doc-gate battery runs to completion and the four new cases fire")
def b04(fh):
    rc, out = run([sys.executable, "-X", "utf8", abs_path(SELFTEST_REL)])
    out = scrub(out)
    tally = re.search(r"SELFTEST cases=(\d+) ok=(\d+) mismatch=(\d+) harness_errors=(\d+) "
                      r"skipped=(\d+)", out)
    baselines = len([l for l in out.splitlines() if l.startswith("BASELINE OK")])
    passed = len([l for l in out.splitlines() if l.startswith("OVERALL: PASS")])
    codes = re.findall(r"EXITCODE=(\d+)", out)
    code = codes[-1] if codes else None
    case_ok = {}
    for name in ROUTING_CASES:
        hits = [l for l in out.splitlines()
                if l.startswith("CASE ") and " OK " in l and name in l]
        case_ok[name] = len(hits)
    n = int(tally.group(1)) if tally else -1
    verdicts = len([l for l in out.splitlines()
                    if re.match(r"^CASE \d\d (OK|MISMATCH|HARNESS) ", l)])
    five = [tally.group(i) for i in range(1, 6)] if tally else []
    emit(fh, "  电池 rc=%d 汇总行=%s 基线绿=%d OVERALL:PASS=%d 末条退出码行=%s"
         % (rc, tally.group(0) if tally else "unreadable", baselines, passed, code))
    for name in ROUTING_CASES:
        emit(fh, "  %s 的 OK 判决行=%d" % (name, case_ok[name]))
    emit(fh, "  按行形状归位的判决行=%d（须与自述 cases=%d 相等，条数不能只由汇总行自称）；"
              "汇总行五字段=%s（后四字段须恰好 全等条数/0/0/0）" % (verdicts, n, five))
    emit(fh, "  条数下限（与本清单无关的裸字面量）=%d 达成=%s" % (MIN_CASES, n >= MIN_CASES))
    if rc != 0 or not tally or baselines != 4 or passed != 1 or code != "0" \
            or n < MIN_CASES or verdicts != n or five != [str(n), str(n), "0", "0", "0"] \
            or any(case_ok[x] != 1 for x in ROUTING_CASES):
        fail(fh, "battery not green or a new case missing: rc=%s tally=%s baselines=%d "
                 "pass=%d exit=%s verdicts=%d cases=%s" % (rc, tally.group(0) if tally else None,
                                                           baselines, passed, code, verdicts, five))


@block("05", "consumers pin the tier not a value, and the increment labels have no live refs")
def b05(fh):
    def banned_line(l):
        return "checkers_selftest" in l and any(b in l for b in BANNED)

    def neutral_line(l):
        return "checkers_selftest" in l and any(n in l for n in NEUTRAL)
    pos_b = banned_line('WANT_TEXT="checkers_selftest rc=|PRE-EXISTING DEBT"')
    pos_n = neutral_line('WANT_TEXT="checkers_selftest rc=|SIBLING TIER="')
    neg_n = neutral_line('WANT_TEXT="checkers_selftest rc=|references to this slice=0|"')
    emit(fh, "  探测器正控：钉档位行必被点名=%s、中性行必被认=%s、无中性标记行必不被认=%s"
              % (pos_b, pos_n, not neg_n))
    bad = []
    for rel in SIBLINGS:
        lines = [l for l in read(rel).splitlines() if l.startswith("WANT_TEXT=")]
        sib = [l for l in lines if "checkers_selftest" in l]
        hit_b = [l for l in sib if banned_line(l)]
        hit_n = [l for l in sib if neutral_line(l)]
        emit(fh, "  %s：提到本电池的 WANT_TEXT 行=%d，其中钉死档位=%d、含中性标记=%d"
             % (rel, len(sib), len(hit_b), len(hit_n)))
        if not sib or hit_b or len(hit_n) != len(sib):
            bad.append(rel)
    prod = [CHECKER_REL, SELFTEST_REL, LIB_REL]
    refs = {}
    for rel in prod:
        text = read(rel)
        refs[rel] = {k: text.count(k) for k in OLD_REFS}
    emit(fh, "  生产脚本里旧增量标签/常量的活动引用：%s" % refs)
    total = sum(sum(v.values()) for v in refs.values())
    sample = "MIN_NEW_ROUTES 与 AGENTS.md adds >= 以及 new routing rows cover all four triggers"
    control = sum(sample.count(k) for k in OLD_REFS)
    emit(fh, "  同一计数对合成样本会响=%s（三词各命中一次：%d/%d）" % (control == len(OLD_REFS), control, len(OLD_REFS)))
    if bad or total or not pos_b or not pos_n or neg_n or control != len(OLD_REFS):
        fail(fh, "consumer coupling or stale refs remain: bad=%s total=%s controls=%s/%s/%s/%d"
             % (bad, total, pos_b, pos_n, neg_n, control))


@block("06", "ROADMAP records the D1 closure with recompute commands only")
def b06(fh):
    lines = read(ROADMAP_REL).splitlines()
    body = section(lines, "### D1 ")
    heading = [l for l in body if l.startswith("#") and "已按修法 ① 闭合" in l]
    disposal = [l for l in body if "处置" in l and "当前文件全文" in l]
    reread = [l for l in body if "回读" in l and "check_roadmap_and_routing.py" in l]
    closure = heading + disposal + reread
    # 只判「闭合那三行」有没有把判定值抄成现状；现象行按逐字回读保留旧 FAIL 标签（含 measured=0），
    # 那是缺陷复现的一部分，不是现状声明——把它一起禁掉等于要求文档删掉证据。
    emitted_value = [l for l in closure if re.search(r"measured=\d+", l) or "EXITCODE=" in l]
    live_wording = [l for l in reread if "现场读" in l or "不抄值" in l]
    placeholder = [p for p in ("TBD", "TODO", "待填", "PENDING") if p in "\n".join(lines)]
    ctl = section(["### D1 x", "- 处置（选 ①）当前文件全文", "### D2 y",
                   "- 处置（选 ②）当前文件全文"], "### D1 ")
    ctl_whole = len([l for l in ["- 处置（选 ①）当前文件全文", "- 处置（选 ②）当前文件全文"]
                     if "处置" in l and "当前文件全文" in l])
    emit(fh, "  D1 小节正文=%d 行（切到下一个标题为止）；闭合标题=%d；处置行=%d；回读行=%d；"
              "回读行写明现场读=%d" % (len(body), len(heading), len(disposal), len(reread),
                                      len(live_wording)))
    for l in closure[:3]:
        emit(fh, "  " + l.strip()[:220])
    emit(fh, "  闭合三行里抄了判定值（measured=N / EXITCODE=）的行=%d" % len(emitted_value))
    emit(fh, "  ROADMAP 占位符=%s" % placeholder)
    emit(fh, "  切片形状对照：同样逻辑对合成两小节正文只取本小节那条=%d（必须 1）；"
              "整文件子串扫描对同一输入给 %d（必须 2，否则切片与不扫同形）"
         % (len([l for l in ctl if "处置" in l]), ctl_whole))
    if len(disposal) != 1 or not heading or len(reread) != 1 or not live_wording \
            or emitted_value or placeholder or len(ctl) != 2 or len([l for l in ctl if "处置" in l]) != 1 \
            or ctl_whole != 2:
        fail(fh, "D1 closure not recorded as expected: heading=%d disposal=%d reread=%d "
                 "live=%d values=%d placeholders=%s ctl=%s/%d" % (len(heading), len(disposal),
                 len(reread), len(live_wording), len(emitted_value), placeholder,
                 len(ctl), ctl_whole))


@block("07", "this slice's task card is mechanically valid and contributes no errors")
def b07(fh):
    rc, out = run([sys.executable, "-X", "utf8", ".trae/scripts/validate_tasks.py"])
    mine = [l for l in out.splitlines() if CARD in l]
    errs = [l for l in mine if l.startswith("ERROR ")]
    passes = [l for l in mine if l.startswith("PASS ")]
    summary = re.search(r"SUMMARY files=(\d+) errors=(\d+) warnings=(\d+)", out)
    control = len([l for l in ["ERROR " + CARD + ": x"] if CARD in l and l.startswith("ERROR ")])
    emit(fh, "  本片行=%d（PASS=%d ERROR=%d）；校验器 rc=%d（只打印：它的档位由别人那 16 条预存债务决定，"
              "钉死 rc 就是本片要消灭的「只在某个 git 状态下成立」同形缺陷）"
         % (len(mine), len(passes), len(errs), rc))
    for l in mine:
        emit(fh, "  " + l.strip())
    emit(fh, "  全局汇总只打印不判：%s" % (summary.group(0) if summary else "unreadable"))
    emit(fh, "  点名探测器对合成的本片 ERROR 行会响=%s" % (control == 1))
    if len(passes) < 1 or errs or not summary or control != 1:
        fail(fh, "task card not clean or detector silent: rc=%s pass=%d err=%d summary=%s ctl=%d"
             % (rc, len(passes), len(errs), bool(summary), control))


@block("08", "sizing is adjudicated by the repo counter over archived-byte diffs")
def b08(fh):
    import importlib.util
    import shutil
    import tempfile
    spec = importlib.util.spec_from_file_location(
        "cpl", abs_path(".trae/scripts/dev/count_production_lines.py"))
    cpl = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(cpl)
    pairs = [(CHECKER_REL, PRE + "check_roadmap_and_routing.py"),
             (LIB_REL, PRE + "harness_check_lib.py"),
             (SELFTEST_REL, PRE + "checkers_selftest.py")]
    rows = []
    strip_rows = []
    tmp = tempfile.mkdtemp(prefix="tlx-010size-")
    try:
        for rel, pre in pairs:
            old = read(os.path.join("artifacts/evidence", pre)).splitlines(True)
            new = read(rel).splitlines(True)
            added = [l[1:] for l in difflib.unified_diff(old, new, n=0)
                     if l.startswith("+") and not l.startswith("+++")]
            frag = os.path.join(tmp, os.path.basename(rel) + ".added.py")
            with open_text(frag, "w") as fh2:
                fh2.write("".join(added))
            rows.append((rel, cpl.counted(frag)))
            strip_rows.append((os.path.basename(rel), len([l for l in added if l.strip()])))
        whole = [(os.path.basename(rel), cpl.counted(abs_path(rel)),
                  len([l for l in read(rel).splitlines() if l.strip()]))
                 for rel, _p in pairs]
        added_rows = [l[1:] for l in difflib.unified_diff(["a\n"], ["a\n", "b\n"], n=0)
                      if l.startswith("+") and not l.startswith("+++")]
        frag2 = os.path.join(tmp, "ctl.py")
        with open_text(frag2, "w") as fh2:
            fh2.write("".join(added_rows))
        control = cpl.counted(frag2)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    total = sum(n for _r, n in rows)
    emit(fh, "  判定值（仓库自带计数器读「归档字节 -> 当前字节」的本次新增行片段）=%s 合计=%d"
              % (rows, total))
    emit(fh, "  同一片段按非空行数=%s（两口径相等说明该脚本对 .py 只做非空行统计、"
              "没有额外剥掉任何东西；它只剥 // 与 星斜杠 块，Python 的 # 注释两种口径都保留）"
         % strip_rows)
    emit(fh, "  整文件对照（同一计数器读整文件 / 非空行数）=%s" % whole)
    emit(fh, "  差分计数正控（加一行必须记 1）=%s" % (control == 1))
    emit(fh, "  硬上限 500 未越=%s；每文件都有位移=%s"
         % (total < 500, all(n > 0 for _r, n in rows)))
    if control != 1 or total <= 0 or total >= 500 or len(rows) != 3 or any(n <= 0 for _r, n in rows) \
            or [n for _r, n in rows] != [n for _b, n in strip_rows] \
            or [a for _b, a, _c in whole] != [c for _b, _a, c in whole]:
        fail(fh, "sizing adjudication unreadable, out of band, or the two口径 diverge: "
                 "rows=%s strip=%s whole=%s total=%d control=%d"
             % (rows, strip_rows, whole, total, control))


@block("09", "pre-run fingerprints of read-only inputs, plus the shape control")
def b09(fh):
    now = {}
    missing = []
    for rel in PROD_FILES:
        p = abs_path(rel)
        if not os.path.exists(p):
            missing.append(rel)
            continue
        now[rel] = sha(p)
    moved = sorted(k for k in set(SNAPSHOT) | set(now) if SNAPSHOT.get(k) != now.get(k))
    emit(fh, "  名单条目=%d（复算：sha256sum 逐个路径）；缺失=%s" % (len(now), missing))
    emit(fh, "  名单边界：本运行写出的产物不进名单——即 %s；它的当前值由本文件自身承载。"
              % os.path.relpath(OUT, REPO).replace(os.sep, "/"))
    emit(fh, "  同窗口位移=%d movers=%s" % (len(moved), moved))
    ctl_start = {"a/keep": SNAPSHOT.get(PROD_FILES[0], "")}
    ctl_end = {"a/keep": SNAPSHOT.get(PROD_FILES[0], ""), "a/late": "written"}
    ctl_moved = sorted(k for k in set(ctl_start) | set(ctl_end)
                       if ctl_start.get(k) != ctl_end.get(k))
    emit(fh, "  位移计算的正控（缺席->出现必须点名）=%s" % (ctl_moved == ["a/late"]))
    shape64 = len(HEX_WORD.findall(sha(abs_path(AGENTS_REL))))
    shape12 = len(HEX_WORD.findall(sha(abs_path(AGENTS_REL))[:12]))
    emit(fh, "  形状对照：整段 64 位哈希被扫描命中=%d、12 位前缀命中=%d"
              "（因此本产物只打印整段，不打印前缀；前缀会被 scrub）" % (shape64, shape12))
    if missing or moved or len(now) != len(PROD_FILES) or ctl_moved != ["a/late"] \
            or shape64 != 0 or shape12 != 1:
        fail(fh, "fingerprint list incomplete or displaced: missing=%s moved=%s entries=%d "
                 "ctl=%s shapes=%s/%s" % (missing, moved, len(now), ctl_moved, shape64, shape12))


def main():
    total = 0
    for rel in PROD_FILES:
        p = abs_path(rel)
        SNAPSHOT[rel] = sha(p) if os.path.exists(p) else "<absent>"
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        for head in ("# T-2026-10-18-010 GREEN：路由判据改判当前态，文档类守门自检首次跑完并绿",
                     "# 生成命令：python -X utf8 artifacts/tmp/gen-010-d1-green.py",
                     "# 判读约定：总退出码由 want/got 表派生；前置不成立与归因不落一律 3。",
                     "# 复算归档出处：git log --format=%H -- " + CHECKER_REL +
                     " 最新那条的父提交里的该路径字节（该值随提交移动，故只写命令不写值）。"):
            emit(fh, head)
        emit(fh, "")
        for fn in (b01, b02, b03, b04, b05, b06, b07, b08, b09):
            fn(fh)
        faults = len(FAULTS)
        blocks = len(ORDER)
        emit(fh, "===== 汇总 =====")
        emit(fh, "  blocks=%d floor=%d expectation_entries=%d faults=%d"
             % (blocks, MIN_BLOCKS, len(EXPECT), faults))
        for tag in ORDER:
            got = 3 if tag in FAULTS else 0
            emit(fh, "  block %s want=%d got=%d %s"
                 % (tag, EXPECT[tag], got, "ok" if EXPECT[tag] == got else "MISMATCH"))
        floor_bad = blocks < MIN_BLOCKS or len(EXPECT) != blocks
        mismatch = any(EXPECT[t] != (3 if t in FAULTS else 0) for t in ORDER)
        if floor_bad:
            total = 3
            emit(fh, "  FLOOR FAULT 判定块条数少于裸字面量下限，或期望表条数与块数不一致")
        elif faults:
            total = 3 if any(t in FAULTS for t in ORDER) else 1
        elif mismatch:
            total = 1
        emit(fh, "OVERALL_EXITCODE=%d" % total)
        raw = open(OUT, "rb").read()
        cr = raw.count(bytes([13]))
        try:
            raw.decode("utf-8", "strict")
            utf8_ok = True
        except UnicodeDecodeError:
            utf8_ok = False
        tail = HEX_WORD.findall(raw.decode("utf-8", "replace"))
        if utf8_ok and cr == 0 and not tail:
            fh.write("DECODE ok utf8=True cr=0 commit-shaped-tokens=0\n")
        else:
            fh.write("DECODE FAIL utf8=%s cr=%d commit-shaped-tokens=%d\n"
                     % (utf8_ok, cr, len(tail)))
            total = 3
        print("DECODE gate utf8=%s cr=%d bytes=%d" % (utf8_ok, cr, len(raw)))
    return total


if __name__ == "__main__":
    sys.exit(main())
