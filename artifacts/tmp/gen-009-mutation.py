#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T-2026-10-15-009 变异电池：证明 GREEN 里每条**本次新加**的判据各自会响。

量的是判据的**区分力**：把判据退回它自己的旧形态（或把守卫拆掉），同一个输入必须给出两种
不同的结论；否则那条 OK 行只是文字匹配。块 04 需要浏览器，因为它量的「rc 对上了但分支没走到」
这个形状只能由真实调用给出——本仓库在 2026-10-17 的 GREEN 首跑里撞上过一次：`neg-click-absent`
拿到 rc=1，却是从「页面 60s 没渲出来」那条分支拿的。

副本一律放在与被判文件**同深度**的目录里（`.trae/scripts/dev/parity/` 对
`artifacts/tmp/_mut009a/parity/`：两者的 REPO 都由上溯四级算出），否则共享层按自身位置算出的
仓库根跳出仓库、夹具读不到。块 04 的两份副本都把 CASES 截到 2 条（正向产图 + 被盯的那条负向），
为的是把浏览器调用从 36 次压到 4 次；截短是副本自己的夹具形状，不改变它要量的那一条判据，
且截短后 `MIN_CASES` 同步放开——否则两份副本会先撞上条目下限而根本跑不到用例循环，
而「撞下限」这件事本身由块 05 单独量（块 05 用的副本**不**放开下限）。
"""
import hashlib
import importlib.util
import inspect
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
EV = os.path.join(REPO, "artifacts", "evidence")
PAR = os.path.join(REPO, ".trae", "scripts", "dev", "parity")
CAP = os.path.join(REPO, "artifacts", "tmp", "_g009")
OUT = os.path.join(EV, "T-2026-10-15-009-mutation.txt")
GEN = os.path.join(HERE, "gen-009-green.py")
SELFTEST = os.path.join(CAP, "green-selftest.txt")
DOM = os.path.join(CAP, "green-chat-dom.json")
ARCHIVE_JSON = os.path.join(EV, "ux-chat-1to1-render-dommeasure-2026-09-27.json")
LAYER = os.path.join(PAR, "parity_cdp.mjs")
TOOL = os.path.join(PAR, "verify_1to1.mjs")
BATT = os.path.join(PAR, "verify_1to1_selftest.mjs")
PROFILE = os.path.join(PAR, "screens.json")
EMPTY_CHECKS = os.path.join(CAP, "_mut009-empty-checks.json")
STUB_DIR = os.path.join(CAP, "_mut009-stub")
WORK_A = os.path.join(REPO, "artifacts", "tmp", "_mut009a", "parity")
WORK_B = os.path.join(REPO, "artifacts", "tmp", "_mut009b", "parity")
WORK_C = os.path.join(REPO, "artifacts", "tmp", "_mut009c", "parity")
WORK_F = os.path.join(REPO, "artifacts", "tmp", "_mut009f", "parity")
WORK_T = os.path.join(REPO, "artifacts", "tmp", "_mut009t", "parity")
INPUTS = (GEN, SELFTEST, DOM, ARCHIVE_JSON, LAYER, TOOL, BATT, PROFILE)
MIN_MUTS = 6
CALL_TIMEOUT = 900
WITNESSES = ("wait-for-absent", "click-target-absent", "page-not-rendered")
BLOCKS = []
FAULTS = []
N_MUT = 0

RC_LINE = re.compile(r"^(ok   |FAIL )(\S+) rc=(\d+).*want=(\d+)")
FAIL_RC = re.compile(r"^FAIL \S+ rc=(\d+).*want=(\d+)")
MUT_FAIL = re.compile(r"^FAIL \S+/.*判红且其它")
TIMING = re.compile(r"\(\d+\.\d+s")


def emit(fh, text):
    fh.write(text + "\n")
    fh.flush()      # 被中断也要保住已写出的行
    print(text, flush=True)


def sha(p):
    b = open(p, "rb").read()
    return len(b), hashlib.sha256(b).hexdigest()


def run(cmd, cwd=None):
    try:
        r = subprocess.run(cmd, cwd=cwd or REPO, capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=CALL_TIMEOUT)
    except subprocess.TimeoutExpired:
        FAULTS.append("子进程超时: %s" % " ".join(str(c) for c in cmd)[:120])
        return 255, ""
    return r.returncode, (r.stdout or "") + (r.stderr or "")


def block(fh, idx, label, checks):
    bad = [t for t, ok in checks if not ok]
    emit(fh, "===== block %02d - %s =====" % (idx, label))
    for t, ok in checks:
        emit(fh, ("  ok   " if ok else "  FAIL ") + t)
    got = 0 if not bad else 1
    BLOCKS.append((idx, label, 0, got))
    emit(fh, "  block %02d: want=0 got=%d%s" % (idx, got, "" if not bad else " <- MISMATCH"))
    emit(fh, "")


def mut(tag, src, old, new):
    """一条变异必须**确实落进被测文本**：替换没命中就是空变异，比没有变异更坏。"""
    if old not in src:
        FAULTS.append("变异 %s 的锚点不在被测文本里（空变异）: %s" % (tag, old[:60]))
        return None
    out = src.replace(old, new, 1)
    if out == src:
        FAULTS.append("变异 %s 没有改变任何字节（空变异）" % tag)
        return None
    return out


# ---- 判据的两种形态（与 gen-009-green.py 块 02 同文；各块对同一输入分别求值）
def cli_lines_new(text):
    return [l for l in text.splitlines() if RC_LINE.match(l)]


def cli_lines_old(text):
    return [l for l in text.splitlines() if l.startswith("ok   ") and "(want=" in l]


def battery_fail_new(text):
    return [l for l in text.splitlines() if FAIL_RC.match(l)] + \
           [l for l in text.splitlines() if MUT_FAIL.match(l)]


def battery_fail_old(text):
    return [l for l in text.splitlines() if l.startswith("FAIL ")]


def echo_lines(text):
    """夹具自己的回显：以 FAIL 起头但不是电池判决行的那些。"""
    return [l for l in text.splitlines()
            if l.startswith("FAIL ") and " rc=" not in l and "判红且其它" not in l]


def witness_missing_new(text):
    return [w for w in WITNESSES if not any(w in l for l in echo_lines(text))]


def witness_missing_old(text):
    return []      # 旧形态：这条判据不存在，任何输入都「通过」


def nested_keys_old(new, old):
    """被替换掉的旧形态：只比顶层键集，嵌套字典里少一个键它照样判「无差异」。"""
    return None if set(new) == set(old) else "top"


def finish_rc(out):
    m = re.search(r"^RC=(\d+)$", out, re.M)
    return int(m.group(1)) if m else None


def load_gen():
    """导入 GREEN 那份实现本身——判据只许有一处，电池复制一份就等于量了个生产不用的形状。"""
    spec = importlib.util.spec_from_file_location("g009", GEN)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def case_lines(src):
    """电池正文里单行声明的用例条目（多行的 pos-url-escape 不在此列，截短时整条丢弃）。"""
    return [l for l in src.splitlines() if re.match(r'^  \{ name: "\S+"', l)]


def truncate_cases(src, keep, drop_floor=False, guard_off=False, tag="M4"):
    """把副本的 CASES 换成 keep 里那几条——**从被判文本切出来**，不手抄。"""
    sel = [l for l in case_lines(src) if any('"%s"' % k in l for k in keep)]
    if len(sel) != len(keep):
        FAULTS.append("变异 %s 的截短没选中全部用例（选中 %d/%d）" % (tag, len(sel), len(keep)))
        return None
    if not any("witness:" in l for l in sel):
        FAULTS.append("变异 %s 截短后被盯用例没有 witness 字段（守卫的输入丢了）" % tag)
        return None
    i = src.index("const CASES = [")
    j = src.index("\n];", i)
    out = src[:i] + "const CASES = [\n" + "\n".join(sel) + "\n" + src[j + 1:]
    if drop_floor:
        out = mut(tag + "-floor", out, "const MIN_CASES = 8;", "const MIN_CASES = 1;")
        if out is None:
            return None
    if guard_off:
        out = mut(tag + "-guard", out, "const miss = c.witness && !echoed.some(",
                  "const miss = false && !echoed.some(")
        if out is None:
            return None
    if out == src:
        FAULTS.append("变异 %s 没有改变任何字节（空变异）" % tag)
        return None
    return out


def copy_layer_dir(dst, tool_patch=None, batt_patch=None, layer_patch=None):
    os.makedirs(dst, exist_ok=True)
    for name, src_path, patch in (("parity_cdp.mjs", LAYER, layer_patch),
                                  ("verify_1to1.mjs", TOOL, tool_patch),
                                  ("verify_1to1_selftest.mjs", BATT, batt_patch),
                                  ("screens.json", PROFILE, None)):
        text = open(src_path, encoding="utf-8").read()
        if patch:
            text = patch(text)
            if text is None:
                return None
        open(os.path.join(dst, name), "w", encoding="utf-8", newline="\n").write(text)
    return dst


def other_verdicts(text):
    """除被盯用例外的判决行，去掉耗时字段后用于逐字比对（耗时天然两次不同）。"""
    return [TIMING.sub("(Xs", l) for l in text.splitlines()
            if l.startswith(("ok   ", "FAIL ")) and not re.match(r"^(ok   |FAIL )neg-click-absent\b", l)]


def run_battery(dst, guard_on):
    """同一份被变异仪器跑两遍电池；唯一差异是电池里那条见证守卫开/关。"""
    def tool_patch(src):
        return mut("M4-tool", src, "(m) => ledger.fail(m)",
                   '(m) => ledger.fail(m.replace("click-target-absent", "some-other-branch"))')

    def batt_patch(src):
        return truncate_cases(src, ("pos-real-dom-render", "neg-click-absent"),
                              drop_floor=True, guard_off=not guard_on)

    d = copy_layer_dir(dst, tool_patch=tool_patch, batt_patch=batt_patch)
    if not d:
        return None, ""
    rc, out = run(["node", os.path.join(d, "verify_1to1_selftest.mjs")])
    return rc, out


def drive_empty_ledger(parity_dir):
    uri = Path(os.path.join(parity_dir, "parity_cdp.mjs")).as_uri()
    code = ('import("%s").then(async (K) => {'
            'const l = new K.Ledger();'
            'const rc = await K.finish(l, {command: "(mutation) 空判据", header: []});'
            'console.log("RC=" + rc);})' % uri)
    return run(["node", "--input-type=module", "-e", code], cwd=parity_dir)


def drive_empty_ledger_mutant(dst):
    def layer_patch(src):
        return mut("M6", src, "  if (!judged) {", "  if (false && !judged) {")

    d = copy_layer_dir(dst, layer_patch=layer_patch)
    if not d:
        return None, ""
    return drive_empty_ledger(d)


def make_stubs():
    """两支 HTML 桩（不打公网）：#root 空的、#root 有内容的。档案由 screens.json 现场派生，
    只把 prototype.html 换成桩的绝对路径——其余字段一律照抄，否则这条 A/B 量的就不是渲染分流。"""
    os.makedirs(STUB_DIR, exist_ok=True)
    out = {}
    for key, inner in (("empty", ""), ("rendered", '<div class="other">渲出了内容，但没有 .screen</div>')):
        hp = os.path.join(STUB_DIR, "stub-%s.html" % key)
        open(hp, "w", encoding="utf-8", newline="\n").write(
            '<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>桩</title></head>'
            '<body><div id="root">' + inner + '</div></body></html>\n')
        prof = json.loads(open(PROFILE, encoding="utf-8").read())
        prof["screens"]["chat"]["prototype"]["html"] = hp
        pp = os.path.join(STUB_DIR, "stub-%s.json" % key)
        open(pp, "w", encoding="utf-8", newline="\n").write(json.dumps(prof, ensure_ascii=False))
        out[key] = pp
    return out


def main():
    global N_MUT
    for p in INPUTS:
        if not os.path.isfile(p):
            print("FAULT 缺少输入: %s" % p)
            return 3
    base = {p: sha(p) for p in INPUTS}
    batt_src = open(BATT, encoding="utf-8").read()
    fh = open(OUT, "w", encoding="utf-8", newline="\n")
    try:
        emit(fh, "# T-2026-10-15-009 MUTATION：DOM 侧收编的新判据各自有区分力")
        emit(fh, "# 生成命令：python -X utf8 artifacts/tmp/gen-009-mutation.py")
        emit(fh, "# 输入（判定前读数；本电池只读不写这些生产文件，复核见末块）：")
        for p in INPUTS:
            n, s = base[p]
            emit(fh, "#   %s bytes=%d sha256=%s"
                 % (os.path.relpath(p, REPO).replace(os.sep, "/"), n, s))
        emit(fh, "# 变异条数不进正文，由末块的裸字面量下限 MIN_MUTS=%d 现场量。" % MIN_MUTS)
        emit(fh, "# 副本目录 artifacts/tmp/_mut009{a,b,c,f}/parity 与被判文件同深度，用完即删；")
        emit(fh, "#   它们只改自己那份副本，被判的生产文件由末块的 sha 复核兜着。")
        emit(fh, "")
        selftest = open(SELFTEST, encoding="utf-8").read()

        # 01 M1 判决行按形状归位：旧形态把夹具 echo 算成本片缺陷，且看不见合成的真失败
        real_new_fail = battery_fail_new(selftest)
        real_old_fail = battery_fail_old(selftest)
        synth = selftest + ("\nFAIL neg-click-absent rc=1 want=1，但那条分支没走到："
                            "输出里没有 FAIL click-target-absent ——同一个 rc 可以由别的分支给出\n")
        caught_new = battery_fail_new(synth)
        caught_old = cli_lines_old(synth)
        emit(fh, "  同一份捕获：旧形态命中=%d 新形态命中=%d ；合成真失败：新形态=%d 旧形态按 ok/(want= 形状命中=%d"
             % (len(real_old_fail), len(real_new_fail), len(caught_new), len(caught_old)))
        N_MUT += 1
        block(fh, 1, "M1 verdict lines located by shape", [
            ("旧形态把设计内的夹具 echo 判成本片缺陷（>0 条）——这条归位确实在承重", len(real_old_fail) > 0),
            ("新形态对同一份捕获判绿（0 条电池失败）", len(real_new_fail) == 0),
            ("新形态不是把扫描关掉：合成的「rc 对上但分支没走到」必须被点名",
             len(caught_new) == 1 and "neg-click-absent" in caught_new[0]),
            ("旧形态的 ok-行过滤器看不见那条合成失败（它要求行内出现 (want=）", len(caught_old) == 0),
            ("夹具见证仍完整可见（只是不判成本片缺陷）", len(real_old_fail) == len(WITNESSES)),
        ])

        # 02 M2 cases 的会计：旧等式对真实捕获判红（它把电池自己的 unit + 正控两类漏掉）
        m = re.search(r"SELFTEST (?:PASS|FAIL) cases=(\d+) cli=(\d+) muts=(\d+) failures=(\d+)", selftest)
        if not m:
            FAULTS.append("捕获里没有 SELFTEST 汇总行，块 02 无从求值")
            cases = cli_n = muts_n = -1
        else:
            cases, cli_n, muts_n = (int(m.group(i)) for i in (1, 2, 3))
        unit = [l for l in selftest.splitlines() if " rc=" in l and "(unit," in l]
        pos_ctrl = [l for l in selftest.splitlines() if l.startswith("ok   positive-control ")]
        old_eq = cases == cli_n + muts_n
        new_eq = cases == cli_n + muts_n + len(unit) + len(pos_ctrl)
        degraded = selftest.replace(" (unit, want=3)", " (13.0s, want=3)")
        degraded = "\n".join(l for l in degraded.splitlines() if not l.startswith("ok   positive-control "))
        deg_unit = [l for l in degraded.splitlines() if " rc=" in l and "(unit," in l]
        deg_pos = [l for l in degraded.splitlines() if l.startswith("ok   positive-control ")]
        emit(fh, "  读数 cases=%d cli=%d muts=%d unit=%d 正控=%d 旧等式=%s 新等式=%s ；"
             "抹掉那两类见证后新等式=%s"
             % (cases, cli_n, muts_n, len(unit), len(pos_ctrl), old_eq, new_eq,
                cases == cli_n + muts_n + len(deg_unit) + len(deg_pos)))
        N_MUT += 1
        block(fh, 2, "M2 case accounting names the two extra categories", [
            ("真实捕获上旧等式 cases==cli+muts 不成立（它漏了两类）", not old_eq),
            ("新等式成立且那 2 条各有逐字见证", new_eq and len(unit) == 1 and len(pos_ctrl) == 1),
            ("抹掉那 2 条见证后新等式必须失效（不是把 +2 调成恒真）",
             cases != cli_n + muts_n + len(deg_unit) + len(deg_pos)),
            ("额外两条的条数取自实读而不是写死（unit=%d 正控=%d，和=%d）"
             % (len(unit), len(pos_ctrl), len(unit) + len(pos_ctrl)), len(unit) + len(pos_ctrl) == 2),
        ])

        # 03 M3 见证缺失检测：逐条抹掉见证行，检测必须点名那一条且只点名那一条
        real_missing = witness_missing_new(selftest)
        degraded_all = "\n".join(l for l in selftest.splitlines()
                                 if not any(w in l for w in WITNESSES))
        all_missing = witness_missing_new(degraded_all)
        per_one = []
        for w in WITNESSES:
            stripped = "\n".join(l for l in selftest.splitlines() if w not in l)
            per_one.append((w, witness_missing_new(stripped)))
        emit(fh, "  真实=%s ；逐条抹掉=%s ；全抹=%s ；旧形态在任何输入=%s"
             % (real_missing or "[]", [(w, m or "[]") for w, m in per_one], all_missing or "[]",
                witness_missing_old(selftest) or "[]"))
        N_MUT += 1
        block(fh, 3, "M3 witness-presence detector discriminates and isolates",
              [("真实捕获上 %d 条见证都在（0 条缺失）" % len(WITNESSES), real_missing == [])]
              + [("抹掉 %s 的见证必须只点名它自己（隔离）" % w, m == [w]) for w, m in per_one]
              + [("全部抹掉必须点名全部 %d 条，不多不少" % len(WITNESSES), sorted(all_missing) == sorted(WITNESSES)),
                 ("同一份「抹掉全部见证」的输入：新形态点名 %d 条而旧形态点名 0 条（守卫确实承重，不是恒真）"
                  % len(all_missing), all_missing and witness_missing_old(selftest) == []
                  and len(witness_missing_new(selftest)) == 0)])

        # 04 M4 守卫开/关，对同一份「rc 对上但分支没走到」的仪器输入给出两种结论（要浏览器）
        ok_a, cap_a = run_battery(WORK_A, guard_on=True)
        ok_b, cap_b = run_battery(WORK_B, guard_on=False)
        tgt = lambda t: [l for l in t.splitlines() if re.match(r"^(ok   |FAIL )neg-click-absent\b", l)]
        line_a, line_b = tgt(cap_a), tgt(cap_b)
        other_a, other_b = other_verdicts(cap_a), other_verdicts(cap_b)
        emit(fh, "  守卫开: rc=%s 该用例判决行=%s" % (ok_a, line_a[:1]))
        emit(fh, "  守卫关: rc=%s 该用例判决行=%s" % (ok_b, line_b[:1]))
        emit(fh, "  副本健康度：守卫开=%s 守卫关=%s（都要跑到用例循环，且除被盯那条外判决逐字相同）"
             % ("selftest-floor" not in cap_a and "EXITCODE=" in cap_a,
                "selftest-floor" not in cap_b and "EXITCODE=" in cap_b))
        N_MUT += 1
        block(fh, 4, "M4 the witness guard is what turns the observed race red", [
            ("两次运行共用同一份被变异仪器（点击失败标签都被改名），守卫是唯一差异",
             cap_a.count("some-other-branch") > 0 and cap_b.count("some-other-branch") > 0),
            ("守卫开着：这条用例必须判红并说出「分支没走到」",
             ok_a == 1 and line_a and line_a[0].startswith("FAIL neg-click-absent")
             and "那条分支没走到" in line_a[0]),
            ("守卫关掉：同一个输入必须被判绿（这正是 2026-10-17 首跑撞上的假见证形状）",
             ok_b == 0 and line_b and line_b[0].startswith("ok   neg-click-absent")),
            ("除被盯那条以外的判决行逐字相同且非空（变异只影响这一条，隔离）",
             len(other_a) >= 20 and other_a == other_b),
            ("两份副本都没撞上条目下限（否则 A/B 比的是下限不是守卫）",
             "selftest-floor" not in cap_a and "selftest-floor" not in cap_b),
        ])

        # 05 M5 条目下限要真的会响：把 CASES 截到下限以下（**不**放开下限），必须 rc=3 且当场出声
        def batt_patch(src):
            return truncate_cases(src, ("pos-real-dom-render", "neg-click-absent"), tag="M5")

        d = copy_layer_dir(WORK_F, batt_patch=batt_patch)
        floor_rc, floor_out = run(["node", os.path.join(d, "verify_1to1_selftest.mjs")]) if d else (255, "")
        declared = len(case_lines(batt_src))
        emit(fh, "  截短副本: rc=%s 出声行=%s ；真实正文单行声明的用例条数=%d 下限=8（与清单无关的裸字面量）"
             % (floor_rc, [l for l in floor_out.splitlines() if "selftest-floor" in l][:1], declared))
        N_MUT += 1
        block(fh, 5, "M5 declared-case floor fires on a truncated list, not vacuously", [
            ("截短后 rc 恰好 3（仪器故障档：判据不能为空见证）", floor_rc == 3),
            ("出声行点名 declared 与下限两个数字", "selftest-floor" in floor_out and "/8" in floor_out),
            ("真实清单条数高于下限（下限因此不是恒真）", declared > 8),
            ("没有跑到浏览器那一步（产物里没有 browser = 行）", "browser = " not in floor_out),
            ("截短副本确实只留下 2 条（选中集非空见证）", "declared cases=2/8" in floor_out),
        ])

        # 06 M6 空判据必须 FAULT：拆掉共享层那道闸门，同一个空 Ledger 会打出 PASS
        proc_real, real_out = drive_empty_ledger(PAR)
        proc_mut, mut_out = drive_empty_ledger_mutant(WORK_C)
        real_rc, mut_rc = finish_rc(real_out), finish_rc(mut_out)
        emit(fh, "  真层空判据: finish rc=%s 进程 rc=%s 有 FAULT 行=%s ；拆掉闸门的副本: finish rc=%s 打出 PASS=%s"
             % (real_rc, proc_real, "没有任何判据" in real_out, mut_rc, "VERDICT: PASS" in mut_out))
        N_MUT += 1
        block(fh, 6, "M6 empty-ledger gate is the only thing keeping a blank run from PASSing", [
            ("两次 node 调用本身都跑完了（进程 rc=0，读数来自 finish 的返回值不是进程码）",
             proc_real == 0 and proc_mut == 0 and real_rc is not None and mut_rc is not None),
            ("真层：空判据 finish 恰好返回 3 且打印 FAULT 行", real_rc == 3 and "没有任何判据" in real_out),
            ("真层：不得出现 VERDICT: PASS", "VERDICT: PASS" not in real_out),
            ("拆闸门副本：同一输入变成 finish 返回 0 且打 PASS（守卫确实承重）",
             mut_rc == 0 and "VERDICT: PASS" in mut_out),
            ("副本的判决与真层确实不同（空变异不算测过）", mut_out != real_out),
        ])

        # 07 M7 保持行为比对的嵌套维度：只比顶层键的旧判据对「嵌套键漂移」全瞎
        g = load_gen()
        new_json = json.load(open(DOM, encoding="utf-8"))
        old_json = json.load(open(ARCHIVE_JSON, encoding="utf-8"))
        drift = json.loads(json.dumps(new_json))
        popped = drift["userBubble"].pop("css", "ABSENT")
        if popped == "ABSENT":
            FAULTS.append("M7 的嵌套漂移样本没命中：健康读数里没有 userBubble.css 这个键")
        real_top, real_sub = g.key_diff(new_json, old_json)
        hit_top, hit_sub = g.key_diff(drift, old_json)
        old_form = nested_keys_old(drift, old_json)
        gen_main = inspect.getsource(g.main)
        emit(fh, "  真实两份读数: 顶层缺=%s 嵌套差异=%s ；抹掉 userBubble.css: 新=%s 旧形态=%s"
             % (real_top or "无", real_sub or "无", hit_sub or "无", old_form or "无差异"))
        N_MUT += 1
        block(fh, 7, "M7 keep-behaviour comparison covers nested key sets", [
            ("真实读数在顶层与嵌套键集上都无差异（这就是 GREEN 块 04 那两条 OK 的内容）",
             real_top == [] and real_sub == {}),
            ("嵌套键漂移时旧形态全瞎（顶层集合相等即判「无差异」）", old_form is None),
            ("同一输入 GREEN 那份实现点名 userBubble，且点名的是「归档有、新读数没有」那一半",
             list(hit_sub) == ["userBubble"] and hit_sub["userBubble"][0] == ["css"]),
            ("漂移样本确实动了键集（空变异不算测过）",
             set(drift["userBubble"]) != set(new_json["userBubble"])),
            ("块 04 的生产判据调的就是这个函数（不是定义了没被用的第二份形状）",
             "key_diff(new, old)" in gen_main and "missing_top, sub_diff = key_diff" in gen_main),
        ])

        # 08 M8 档位不可混码：旧形态接受「任何非 0」会把用法错误(2)认证成故障档(3)的见证
        prof = json.load(open(PROFILE, encoding="utf-8"))
        prof["screens"]["chat"]["prototype"]["dom_checks"] = []
        os.makedirs(CAP, exist_ok=True)
        open(EMPTY_CHECKS, "w", encoding="utf-8", newline="\n").write(json.dumps(prof, ensure_ascii=False))
        rc_usage, out_usage = run(["node", TOOL, "--out", os.path.join(CAP, "_mut009-usage.json")])
        rc_fault, out_fault = run(["node", TOOL, "--screen", "chat",
                                  "--out", os.path.join(CAP, "_mut009-fault.json"),
                                  "--profile", EMPTY_CHECKS])
        emit(fh, "  用法错误 rc=%d ；档案空判据 rc=%d ；旧形态(任何非0)对用法输入判=%s 新形态(恰好3)判=%s"
             % (rc_usage, rc_fault, rc_usage != 0, rc_usage == 3))
        N_MUT += 1
        block(fh, 8, "M8 exit-code tiers are not interchangeable", [
            ("两条都跑到（rc 分别落在 2 与 3）", rc_usage == 2 and rc_fault == 3),
            ("旧形态会把用法错误认证成「故障档见证」——这条判据本次确实在防混码",
             (rc_usage != 0) and not (rc_usage == 3)),
            ("新形态对真正的故障档输入判真，且说出的是没有检查项而不是别的",
             rc_fault == 3 and "没有 dom_checks" in out_fault and "EXITCODE=3" in out_fault),
            ("两次调用都没起浏览器（都在浏览器启动之前的分支）",
             "browser = " not in out_usage and "browser = " not in out_fault),
        ])

        # 09 M9 「等不到 .screen」的两种成因必须分档：拆掉 #root 那道分流，被核对象没起来会被判成结构不符
        stubs = make_stubs()
        rc_boot, out_boot = run(["node", TOOL, "--screen", "chat",
                                 "--out", os.path.join(CAP, "_mut009-t1.json"), "--profile", stubs["empty"]])
        rc_struct, out_struct = run(["node", TOOL, "--screen", "chat",
                                     "--out", os.path.join(CAP, "_mut009-t2.json"),
                                     "--profile", stubs["rendered"]])
        d = copy_layer_dir(WORK_T, tool_patch=lambda s: mut("M9", s, "if (probe.root) {", "if (true) {"))
        rc_mut, out_mut = (run(["node", os.path.join(d, "verify_1to1.mjs"), "--screen", "chat",
                                "--out", os.path.join(CAP, "_mut009-t3.json"),
                                "--profile", stubs["empty"]]) if d else (255, ""))
        emit(fh, "  空 #root 桩: 真层 rc=%d ；有内容无 .screen 桩: rc=%d ；同一份空桩喂给拆掉分流的副本: rc=%d"
             % (rc_boot, rc_struct, rc_mut))
        N_MUT += 1
        block(fh, 9, "M9 render-wait splits boot failure from structural mismatch", [
            ("真层：空 #root 桩落 3 并点名「被核对象没起来」（仪器故障档）",
             rc_boot == 3 and "INSTRUMENT FAULT" in out_boot and "仪器故障" in out_boot),
            ("真层：有内容却没有 .screen 的桩仍落 1（结构不符档没被顺手改掉）",
             rc_struct == 1 and "page-not-rendered" in out_struct and "#root 子元素=1" in out_struct),
            ("拆掉分流的副本：同一份空桩从 3 变成 1——2026-10-17 那次 CDN 停顿被读成「结构不符」正是这个形状",
             rc_mut == 1 and "page-not-rendered" in out_mut and "#root 子元素=0" in out_mut),
            ("三条读数各带可分观测量（root= / #root 子元素=），不是只报一个退出码",
             "root=" in out_boot and "#root 子元素=" in out_struct and "#root 子元素=" in out_mut),
            ("副本的变异锚点确实命中（空变异不算测过）", d is not None),
        ])

        # 10 裸字面量下限：放在所有变异之后量（006 实测过：放在中途那块会自我矛盾）
        emit(fh, "  变异计数读数：N_MUT=%d 判定块=%d 下限=%d" % (N_MUT, len(BLOCKS), MIN_MUTS))
        block(fh, 10, "mutation-count floor measured after all mutations", [
            ("变异条数 >= MIN_MUTS=%d（截短一条就必须响）" % MIN_MUTS, N_MUT >= MIN_MUTS),
            ("本块之前的判定块数与变异条数相等（每块对应一条变异；汇总里的 %d 含本块自己）"
             % (len(BLOCKS) + 1), len(BLOCKS) == N_MUT),
            ("下限与清单内容无关（它是本文件里的裸字面量，不是 len(MUTS)）", MIN_MUTS == 6),
        ])

        emit(fh, "  输入复核（本电池只读不写生产文件）：")
        moved = []
        for p in INPUTS:
            n, s = sha(p)
            same = (n, s) == base[p]
            if not same:
                moved.append(os.path.relpath(p, REPO).replace(os.sep, "/"))
            emit(fh, "    %s 字节与 sha 未变=%s" % (os.path.relpath(p, REPO).replace(os.sep, "/"), same))
        short = len(BLOCKS) < MIN_MUTS or N_MUT < MIN_MUTS
        total = 3 if (short or FAULTS) else (1 if any(w != g for _, _, w, g in BLOCKS) else 0)
        emit(fh, "===== 汇总 =====")
        if short:
            emit(fh, "  FAULT 判定块=%d 变异=%d 低于裸字面量下限 MIN_MUTS=%d —— 电池被截短，逐块 want=got 不算通过"
                 % (len(BLOCKS), N_MUT, MIN_MUTS))
        for f in FAULTS:
            emit(fh, "  FAULT %s" % f)
        emit(fh, "  blocks=%d mutations=%d floor=%d floor-met=%s faults=%d"
             % (len(BLOCKS), N_MUT, MIN_MUTS, str(not short), len(FAULTS)))
        for idx, label, want, got in BLOCKS:
            emit(fh, "  block %02d %-46s want=%d got=%d %s"
                 % (idx, label[:46], want, got, "ok" if want == got else "MISMATCH"))
        emit(fh, "  空变异=%d 被判输入位移=%d%s"
             % (len([f for f in FAULTS if "空变异" in f]), len(moved),
                (" " + ",".join(moved)) if moved else ""))
        fh.write("OVERALL_EXITCODE=%d\n" % total)
        fh.flush()
    finally:
        fh.close()
        for d in (WORK_A, WORK_B, WORK_C, WORK_F, WORK_T):
            shutil.rmtree(os.path.dirname(d), ignore_errors=True)
        shutil.rmtree(STUB_DIR, ignore_errors=True)
        for tmp in (EMPTY_CHECKS, os.path.join(CAP, "_mut009-usage.json"),
                    os.path.join(CAP, "_mut009-fault.json"), os.path.join(CAP, "_mut009-t1.json"),
                    os.path.join(CAP, "_mut009-t2.json"), os.path.join(CAP, "_mut009-t3.json")):
            if os.path.isfile(tmp):
                os.remove(tmp)

    raw = open(OUT, "rb").read()
    try:
        raw.decode("utf-8", "strict")
        utf8_ok = True
    except UnicodeDecodeError as e:
        utf8_ok = False
        print("UTF8 GATE: %s" % e)
    cr = raw.count(b"\r")
    if utf8_ok and cr == 0:
        with open(OUT, "a", encoding="utf-8", newline="\n") as ap:
            ap.write("DECODE ok utf8=True cr=0\n")
    raw = open(OUT, "rb").read()
    print("DECODE ok utf8=%s cr=%d bytes=%d lines=%d sha256=%s"
          % (utf8_ok, cr, len(raw), raw.count(b"\n"), hashlib.sha256(raw).hexdigest()))
    print("  复算：sha256sum artifacts/evidence/T-2026-10-15-009-mutation.txt")
    return total if utf8_ok and cr == 0 else 3


if __name__ == "__main__":
    sys.exit(main())
