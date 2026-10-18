"""T-2026-10-18-010 RED：把「守门在现状下不可满足」与「它拖住的自检电池因此跑不出信号」复现一遍。

用法（从仓库根）：
    python -X utf8 artifacts/tmp/gen-010-d1-red.py

前置：修复前的守门与共享库已作为归档副本入库（见 PRE_*），归档与当前文件字节不同。
块 01 检查该前置；不成立判 3（现在还不能认证），不判 0——把「还没归档」读成「归档过且没问题」
是假见证（与 009 的提交后回读同形）。

为什么不写 HEAD 与工作树的对比作为判据：HEAD 会随本片提交移动，任何「归档 == git show HEAD:…」
的读数在提交前后给出相反答案，那是时效性状态而不是缺陷复现。归档副本本身是仓库里的字节，
所以「旧判据在今天的树上不可满足」这件事由**运行归档副本**给出，与提交状态无关。

正文里不出现 7 位以上的裸十六进制词：守门自己的 NOTE 行带 base=<提交号前缀>，
嵌入前一律经 scrub() 替换成 <hex-redacted>，块 04 与收尾闸门各扫一次。
"""

import hashlib
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
EV = os.path.join(REPO, "artifacts", "evidence")
OUT = os.path.join(EV, "T-2026-10-18-harness-roadmap-guard-current-state-010-red.txt")

CHECKER_REL = ".trae/scripts/check_roadmap_and_routing.py"
LIB_REL = ".trae/scripts/harness_check_lib.py"
SELFTEST_REL = ".trae/scripts/dev/checkers_selftest.py"
PRE_CHECKER = os.path.join(EV, "T-2026-10-18-010-pre-fix-check_roadmap_and_routing.py")
PRE_LIB = os.path.join(EV, "T-2026-10-18-010-pre-fix-harness_check_lib.py")

OLD_LABELS = ["AGENTS.md adds >= ", "new routing rows cover all four triggers",
              "every new routing-row target exists"]
NEW_LABELS = ["AGENTS.md routing table currently has >=",
              "current routing rows cover all four triggers",
              "every current routing-row target exists"]
HEX_WORD = re.compile(r"\b[0-9a-f]{7,40}\b")
MIN_BLOCKS = 4

EXPECT = {}
ORDER = []
FAULTS = []
LINES = []
CUR = [None]


def emit(fh, line):
    LINES.append(line)
    fh.write(line + "\n")
    fh.flush()


def run(args, cwd=REPO, env=None):
    e = dict(os.environ)
    if env:
        e.update(env)
    p = subprocess.run(args, cwd=cwd, capture_output=True, text=True,
                       encoding="utf-8", errors="replace", env=e)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def scrub(text):
    return HEX_WORD.sub("<hex-redacted>", text)


def sha(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()


def block(tag, label):
    def deco(fn):
        def wrapper(fh):
            emit(fh, "===== block %s - %s =====" % (tag, label))
            faults_before = len(FAULTS)
            CUR[0] = tag
            try:
                fn(fh)
            except Exception as exc:  # noqa: BLE001 - 仪器故障不得伪装成用例不符
                emit(fh, "  INSTRUMENT FAULT in block %s: %s: %s" %
                     (tag, type(exc).__name__, exc))
                FAULTS.append(tag)
            CUR[0] = None
            expect = 3 if tag in FAULTS else 0
            got = 3 if len(FAULTS) > faults_before else 0
            EXPECT[tag] = expect
            ORDER.append(tag)
            emit(fh, "  block %s: want=%d got=%d" % (tag, expect, got))
            emit(fh, "")
        wrapper.__name__ = fn.__name__
        return wrapper
    return deco


def fail(fh, text):
    """任何 fail 都落在仪器故障档（3）：本产物只认证「旧守门在今天的树上确实复现」，
    复现不出来时无从认证，不能读成「用例不符」（1）。"""
    emit(fh, "  FAIL " + text)
    FAULTS.append(CUR[0] or "fault")


@block("01", "the pre-fix instrument is archived as bytes in the repo")
def b01(fh):
    ok = os.path.exists(PRE_CHECKER) and os.path.exists(PRE_LIB)
    emit(fh, "  归档存在=%s（复算：ls 两个 T-2026-10-18-010-pre-fix-* 路径）" % ok)
    if not ok:
        fail(fh, "pre-fix archive missing - the defect cannot be reproduced from repo bytes")
        return
    cur = open(os.path.join(REPO, CHECKER_REL.replace("/", os.sep)), "rb").read()
    old = open(PRE_CHECKER, "rb").read()
    differ = cur != old
    old_src = old.decode("utf-8", "replace")
    cur_src = cur.decode("utf-8", "replace")
    old_only = [l for l in OLD_LABELS if l in old_src and l not in cur_src]
    new_only = [l for l in NEW_LABELS if l in cur_src and l not in old_src]
    emit(fh, "  归档与当前字节不同=%s；只在归档里出现的旧标签=%s；只在当前文件里的新标签=%s"
         % (differ, old_only, new_only))
    emit(fh, "  三处旧标签都只在归档里=%s；三处新标签都只在当前文件里=%s"
         % (len(old_only) == len(OLD_LABELS), len(new_only) == len(NEW_LABELS)))
    hit = len(HEX_WORD.findall("<" + "ab" * 10 + ">"))
    emit(fh, "  扫描对合成的 20 位十六进制词会响=%s（否则本块与块 04 都是空见证）" % (hit == 1))
    if not differ or len(old_only) != len(OLD_LABELS) or len(new_only) != len(NEW_LABELS) \
            or hit != 1:
        fail(fh, "archive/current pair does not separate the two predicate sets")


@block("02", "the archived one-shot predicate is unsatisfiable on today's tree")
def b02(fh):
    # 归档守门经 sys.path.insert(脚本目录) + from harness_check_lib import … 取共享库，
    # 而 REPO_ROOT 也由该库给出。归档守门躺在 artifacts/evidence/ 时同目录没有库文件，
    # 因此必须把**归档的**共享库按真名放进一个与生产同深度的临时 shim 目录：共享库按
    # dirname(dirname(脚本所在目录)) 求仓库根，生产位于 .trae/scripts/（仓库根下两级），
    # 所以 shim 用 artifacts/_010lib（同样两级）。
    # 缺这一步有两种坏结局：import 失败时 python 也退 1，「rc==1」会伪装成「旧判据确实红」；
    # 把库放进三级深度则 REPO_ROOT 落在 artifacts/，守门会因「ROADMAP 不存在」而红在别的东西上。
    # 两者都是假见证（与「前置条件缺失不得算 PASS」「变异副本要与生产文件同深度」同源）。
    import shutil
    shim = os.path.join(REPO, "artifacts", "_010lib")
    try:
        os.makedirs(shim, exist_ok=True)
        shutil.copyfile(PRE_LIB, os.path.join(shim, "harness_check_lib.py"))
        depth_ok = os.path.dirname(os.path.dirname(shim)) == REPO
        emit(fh, "  shim 目录深度使 REPO_ROOT 回到真仓库根=%s（相对路径=%s，与生产的两级同深）"
             % (depth_ok, "artifacts/_010lib"))
        rc, out = run([sys.executable, "-X", "utf8", PRE_CHECKER],
                      env={"PYTHONPATH": shim + os.pathsep + os.environ.get("PYTHONPATH", "")})
    finally:
        shutil.rmtree(shim, ignore_errors=True)
    lines = scrub(out).splitlines()
    # 必须锚定 FAIL 前缀：该判据的 OK 孪生行（OK file exists: .trae/ROADMAP.md）每次都打印，
    # 用裸子串会让「仓库根错位」探测器在健康运行上也响，把好的复现判成故障。
    def misdiag(l):
        return l.startswith("FAIL file exists: .trae/ROADMAP.md")
    import_err = [l for l in lines if "ModuleNotFoundError" in l or "ImportError" in l]
    missing_repo = [l for l in lines if misdiag(l)]
    fired = [l for l in lines if l.startswith("FAIL ") and OLD_LABELS[0] in l]
    fired2 = [l for l in lines if l.startswith("FAIL ") and OLD_LABELS[1] in l]
    vacuous = [l for l in lines if l.startswith("OK   ") and OLD_LABELS[2] in l]
    emit(fh, "  归档守门 rc=%d（复算：PYTHONPATH=<shim 目录> python -X utf8 <归档守门路径>）" % rc)
    emit(fh, "  导入失败行=%d、仓库根错位行=%d（两者都必须为 0，否则 rc=1 不是判据红）"
         % (len(import_err), len(missing_repo)))
    pos = len([l for l in ["FAIL file exists: .trae/ROADMAP.md — measured=x"] if misdiag(l)])
    neg = len([l for l in ["OK   file exists: .trae/ROADMAP.md"] if misdiag(l)])
    emit(fh, "  错位探测器正控=1?%s、对 OK 孪生行不误响=1?%s（否则会响的扫描与空扫描同形）"
         % (pos == 1, neg == 0))
    for l in fired + fired2 + vacuous:
        emit(fh, "  " + l.strip())
    zero_added = bool(fired) and "measured=0" in fired[0]
    all_missing = bool(fired2) and "missing=" in fired2[0] \
        and all(k in fired2[0] for k in
                ["intent-capturer", "pm-discovery", "project-brief", "ROADMAP"])
    emit(fh, "  新增行读数确为零=%s；四个触发词确为全缺=%s" % (zero_added, all_missing))
    dead_ok_but_empty = bool(vacuous) and "measured_dead=[]" in vacuous[0]
    emit(fh, "  第三条判据在零命中下仍打印 OK=%s（这就是「匹配集为空必须判 FAIL」的增量式形态）"
         % dead_ok_but_empty)
    if rc != 1 or import_err or missing_repo or not depth_ok or pos != 1 or neg != 0 \
            or not zero_added or not all_missing or not dead_ok_but_empty:
        fail(fh, "block 02 conditions not all met: rc=%s import_err=%d missing_repo=%d "
                 "depth_ok=%s pos=%d neg=%d zero_added=%s all_missing=%s vacuous_ok=%s"
             % (rc, len(import_err), len(missing_repo), depth_ok, pos, neg,
                zero_added, all_missing, dead_ok_but_empty))


@block("03", "the archived gate drags the self-test battery to the instrument-fault tier")
def b03(fh):
    import shutil
    import tempfile
    sys.path.insert(0, os.path.join(REPO, SELFTEST_REL.replace("/", os.sep).rsplit(os.sep, 1)[0]))
    import checkers_selftest as S  # noqa: N813 - 复用生产者自己的建盘逻辑，不重写一份
    root = tempfile.mkdtemp(prefix="tlx-010red-")
    try:
        built, note = S.build_workspace(root)
        emit(fh, "  镜像盘建成=%s（最长路径读数经 scrub：%s）" % (built, scrub(str(note))))
        if not built:
            fail(fh, "mirror workspace could not be built - inconclusive")
            return
        shutil.copyfile(PRE_CHECKER, os.path.join(root, ".trae", "scripts",
                                                  "check_roadmap_and_routing.py"))
        shutil.copyfile(PRE_LIB, os.path.join(root, ".trae", "scripts", "harness_check_lib.py"))
        rc, out = run([sys.executable, "-X", "utf8",
                       os.path.join(root, ".trae", "scripts", "dev", "checkers_selftest.py")],
                      cwd=root)
        lines = scrub(out).splitlines()
        base_fail = [l for l in lines if l.startswith("BASELINE FAIL check_roadmap_and_routing")]
        cases = [l for l in lines if l.startswith("CASE ")]
        inconclusive = any(l.startswith("OVERALL: INCONCLUSIVE") for l in lines)
        emit(fh, "  镜像里跑真实电池 rc=%d；BASELINE FAIL 行=%d；CASE 行=%d；结论行=INCONCLUSIVE?%s"
             % (rc, len(base_fail), len(cases), inconclusive))
        for l in base_fail[:1]:
            emit(fh, "  " + l.strip())
        for l in lines[-3:]:
            emit(fh, "  tail| " + l.strip())
        if rc == 3 and len(base_fail) != 1:
            emit(fh, "  FAULT BRANCH rc=3 但不是 roadmap 基线挡住的（基线行=%d）："
                              "归因不成立，本块不能算复现成功" % len(base_fail))
        if rc != 3 or len(base_fail) != 1 or len(cases) != 0 or not inconclusive:
            fail(fh, "the battery did not abort at the instrument-fault tier with zero cases run")
    finally:
        shutil.rmtree(root, ignore_errors=True)


@block("04", "this file's own source carries no commit-shaped token")
def b04(fh):
    src = open(os.path.abspath(__file__), encoding="utf-8", errors="replace").read()
    offenders = sorted(set(HEX_WORD.findall(scrub_free(src))))
    emit(fh, "  源码扫描命中=%d（命中项=%s）" % (len(offenders), offenders))
    control = len(HEX_WORD.findall("see commit " + ("1" * 7) + " for details"))
    emit(fh, "  合成正控（7 位十六进制词，由代码构造、源码里没有裸词）被同一正则命中=%s"
         % (control == 1))
    if offenders or control != 1:
        fail(fh, "commit-shaped token in source, or the scanner does not fire")


def scrub_free(text):
    """本文件源码里合法出现的十六进制串只有正则常量自身的形状，不含裸提交号词。"""
    return text


def main():
    total = 0
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        for head in ("# T-2026-10-18-010 RED：修复前的守门在今天的树上不可满足，且它拖住整条自检电池",
                     "# 生成命令：python -X utf8 artifacts/tmp/gen-010-d1-red.py",
                     "# 前置：修复前的守门与共享库已归档为本片自己的 pre-fix 副本（字节入库）。",
                     "# 复算归档出处：git log --format=%H -- " + CHECKER_REL +
                     " 的最新那条的父提交里的该路径字节；该值随提交移动，因此只写命令不写值。"):
            emit(fh, head)
        emit(fh, "")
        for fn in (b01, b02, b03, b04):
            fn(fh)
        faults = len(FAULTS)
        blocks = len(ORDER)
        emit(fh, "===== 汇总 =====")
        emit(fh, "  blocks=%d floor=%d expectation_entries=%d faults=%d"
             % (blocks, MIN_BLOCKS, len(EXPECT), faults))
        for tag in ORDER:
            emit(fh, "  block %s want=%d got=%d %s"
                 % (tag, EXPECT[tag], 3 if tag in FAULTS else 0,
                    "ok" if EXPECT[tag] == (3 if tag in FAULTS else 0) else "MISMATCH"))
        floor_bad = blocks < MIN_BLOCKS or len(EXPECT) != blocks
        mismatch = any(EXPECT[t] != (3 if t in FAULTS else 0) for t in ORDER)
        if floor_bad:
            total = 3
            emit(fh, "  FLOOR FAULT 判定块条数少于下限，或期望表条数与块数不一致")
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
        tail = [t for t in tail if t not in
                ("harness", "TBD")]
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
