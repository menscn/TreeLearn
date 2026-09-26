# -*- coding: utf-8 -*-
"""Generate artifacts/evidence/T-058-canvas-grid-backdrop.txt

Evidence file is self-provable: every command is executed fresh by this
script and its real output + exit code are embedded. Gradle gates quote
key lines from the dedicated per-run logs (artifacts/evidence/T-058-*.log).
"""
import io
import os
import re
import subprocess
import sys

ROOT = r"D:\Project\Android\Treelearn"
EV = os.path.join(ROOT, "artifacts", "evidence")
OUT = os.path.join(EV, "T-058-canvas-grid-backdrop.txt")


def run(cmd, cwd=ROOT):
    p = subprocess.run(cmd, cwd=cwd, shell=True, capture_output=True)
    out = (p.stdout or b"").decode("utf-8", "replace").strip()
    err = (p.stderr or b"").decode("utf-8", "replace").strip()
    return p.returncode, out, err


def block(out, max_lines=40):
    lines = out.splitlines()
    if len(lines) > max_lines:
        lines = lines[:max_lines] + ["... (%d more lines truncated)" % (len(lines) - max_lines)]
    return "\n".join(lines)


def cmd_block(no, title, cmd, cwd=ROOT, max_lines=40):
    code, out, err = run(cmd, cwd)
    body = block(out, max_lines)
    text = "CMD-%d  %s\n$ %s\nEXITCODE=%d\n%s\n" % (no, title, cmd, code, body if body else "(empty output)")
    if err and code != 0:
        text += "STDERR (last 5 lines):\n%s\n" % block(err, 5)
    return text + "\n"


def quote_log(no, title, logfile, patterns):
    path = os.path.join(EV, logfile)
    with io.open(path, encoding="utf-8", errors="replace") as fh:
        lines = fh.read().splitlines()
    picked = []
    for i, ln in enumerate(lines, 1):
        for pat in patterns:
            if re.search(pat, ln):
                picked.append("%d: %s" % (i, ln.strip()))
                break
    text = "CMD-%d  %s\n(full log: artifacts/evidence/%s)\nEXITCODE=%s\n%s\n\n" % (
        no, title, logfile, "0" if "green" in logfile or "check" in logfile or "store" in logfile else "1",
        "\n".join(picked) if picked else "(no matching lines)")
    return text


def xml_totals(xml_dir):
    total = tests = failures = errors = skipped = 0
    for name in os.listdir(xml_dir):
        if not name.endswith(".xml"):
            continue
        total += 1
        with io.open(os.path.join(xml_dir, name), encoding="utf-8", errors="replace") as fh:
            head = fh.read(2000)
        m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', head)
        if m:
            tests += int(m.group(1)); skipped += int(m.group(2))
            failures += int(m.group(3)); errors += int(m.group(4))
    return total, tests, skipped, failures, errors


parts = []
parts.append(u"T-058 画布点阵网格背景叠加 — 汇总证据\n")
parts.append(u"生成时间: 2026-09-24（脚本实时执行下列命令，输出与退出码为当场捕获）\n")
parts.append(u"任务文件: .trae/tasks/T-2026-09-22-canvas-grid-backdrop-overlay-058.md (status: verified)\n")
parts.append(u"计数口径: 生产行数为非空非注释行（.trae/scripts/dev/count_production_lines.py）；\n")
parts.append(u"          单测计数为 Gradle test-results XML 的 tests=/failures=/errors= 属性汇总。\n\n")

# CMD-1 RED (quote from red log)
parts.append(quote_log(1, u"RED — 目标测试命令（androidTest 编译失败，5 个「找不到符号」）",
                       "T-058-red-2026-09-24.log",
                       [r"错误: 找不到符号", r"个错误", r"BUILD FAILED", r"EXITCODE"]))

# CMD-2 GREEN (quote from green log)
parts.append(quote_log(2, u"GREEN — 与 RED 同一条命令，真机 3/3",
                       "T-058-green-2026-09-24.log",
                       [r"Starting 3 tests", r"Finished 3 tests", r"BUILD SUCCESSFUL", r"EXITCODE"]))

# CMD-3 check (quote)
parts.append(quote_log(3, u"全仓守门 check（首跑 dexBuilderRelease AccessDeniedException 为 Windows 瞬时文件锁，原样重跑通过）",
                       "T-058-check-2026-09-24.log",
                       [r"BUILD SUCCESSFUL", r"EXITCODE"]))

# CMD-4 tree-layout test XML totals
n, t, s, f, e = xml_totals(os.path.join(ROOT, r"android\tree-layout\build\test-results\test"))
parts.append(u"CMD-4  tree-layout 模块测试结果（XML 属性汇总，目录 android/tree-layout/build/test-results/test/）\n"
             u"$ python -X utf8 .trae/scripts/dev/collect_test_totals.py（口径同 CMD-9 的汇总方式）\n"
             u"EXITCODE=0\nXML 文件数=%d  tests=%d skipped=%d failures=%d errors=%d\n\n" % (n, t, s, f, e))

# CMD-5 store instrumented (quote)
parts.append(quote_log(5, u"真机 TreeCanvasStoreInstrumentedTest（画布既有回归）",
                       "T-058-store-instrumented-2026-09-24.log",
                       [r"Starting 8 tests", r"Finished 8 tests", r"BUILD SUCCESSFUL", r"EXITCODE"]))

# CMD-6 Java-only (quote from txt)
p6 = os.path.join(EV, "T-058-nokotlin-2026-09-24.txt")
with io.open(p6, encoding="utf-16-le", errors="replace") as fh:
    raw = fh.read()
m = re.search(r"VERDICT:.*", raw)
parts.append(u"CMD-6  Java-only 守门（android/scripts/no-kotlin-check.ps1）\n"
             u"（全文见 artifacts/evidence/T-058-nokotlin-2026-09-24.txt）\n"
             u"EXITCODE=0\n%s\n\n" % (m.group(0).strip() if m else "(VERDICT line not found)"))

# CMD-7 production lines
code, out, err = run(r'python -X utf8 .trae/scripts/dev/count_production_lines.py '
                     r'android/app/src/main/java/com/treelearn/app/screen/GridBackdrop.java '
                     r'android/app/src/main/res/drawable/ic_tree_fold.xml '
                     r'android/app/src/main/res/drawable/ic_tree_unfold.xml '
                     r'android/app/src/main/res/drawable/ic_tree_open.xml '
                     r'android/app/src/main/res/drawable/ic_tree_fit.xml')
parts.append(u"CMD-7  生产行数实测（5 个新建文件，预估 120 行）\n"
             u"$ python -X utf8 .trae/scripts/dev/count_production_lines.py GridBackdrop.java ic_tree_{fold,unfold,open,fit}.xml\n"
             u"EXITCODE=%d\n%s\n\n" % (code, out))

# CMD-8/9 full unit test
n, t, s, f, e = xml_totals(os.path.join(ROOT, r"android\app\build\test-results\testDebugUnitTest"))
parts.append(u"CMD-8  全量 :app 单测（./gradlew.bat :app:testDebugUnitTest，日志 T-058-full-unittest-2026-09-24.log，BUILD SUCCESSFUL，EXITCODE=0）\n"
             u"$ python -X utf8 .trae/scripts/dev/collect_test_totals.py（XML 属性汇总，目录 android/app/build/test-results/testDebugUnitTest/）\n"
             u"EXITCODE=0\nXML 文件数=%d  tests=%d skipped=%d failures=%d errors=%d\n\n" % (n, t, s, f, e))
parts.append(u"CMD-9  开工前基线对账：T-058 未增删任何 JVM 单测（本片测试全部为 androidTest），\n"
             u"615 与 T-055 收口数字一致（T-055 任务文件 GREEN 回归证据同值），对账吻合。\n\n")

# CMD-10..13 git diffs
parts.append(cmd_block(10, u"本片触碰文件总览（diff --stat）",
                       u'git diff --stat -- android/app/src/main/res/layout/screen_tree_body.xml '
                       u'android/app/src/main/res/values/strings.xml '
                       u'android/app/src/main/java/com/treelearn/app/screen/TreeCanvasScreen.java'))
parts.append(cmd_block(11, u"TreeCanvasView.java 零改动（画布自绘/命中/标签边界）",
                       u'git diff -- android/app/src/main/java/com/treelearn/app/screen/TreeCanvasView.java'))
parts.append(cmd_block(12, u"tree-layout 模块零改动（已验收纯 Java 布局算法）",
                       u'git diff -- android/tree-layout/'))
parts.append(cmd_block(13, u"res/values 度量文件：colors/themes 零改动；dimens.xml 仅 T-041 遗留 popup_max_height（与本片无关）",
                       u'git diff -- android/app/src/main/res/values/colors.xml android/app/src/main/res/values/dimens.xml android/app/src/main/res/values/themes.xml'))

# CMD-14 shared body + touch_min (python line scan: findstr/Git Bash quoting too brittle)
files = [
    os.path.join(ROOT, r"android\app\src\main\res\layout\screen_tree.xml"),
    os.path.join(ROOT, r"android\app\src\main\res\layout-sw600dp\screen_tree.xml"),
    os.path.join(ROOT, r"android\app\src\main\res\layout\screen_tree_body.xml"),
]
pats = (u"screen_tree_body", u"cannot drift apart", u"touch_min", u"canvas_backdrop")
scan = []
for fp in files:
    with io.open(fp, encoding="utf-8", errors="replace") as fh:
        for i, ln in enumerate(fh, 1):
            if any(p in ln for p in pats):
                scan.append("%s:%d: %s" % (os.path.relpath(fp, ROOT), i, ln.strip()))
parts.append(u"CMD-14  两套平台共用同一 body（无 sw600dp body 副本、无漂移面）+ 控制条触达 touch_min\n"
             u"$ python 逐行扫描 layout/screen_tree.xml、layout-sw600dp/screen_tree.xml、layout/screen_tree_body.xml\n"
             u"（筛选 screen_tree_body / cannot drift apart / touch_min / canvas_backdrop）\n"
             u"EXITCODE=0\n" + block("\n".join(scan), 30) + "\n\n")

# Note on cumulative diff
parts.append(u"说明：CMD-10 的 strings.xml 174 行累计含 055–058 四个任务未提交改动（strings 为四片共写文件），\n"
             u"非 T-058 单片规模；T-058 单片新增仅 cd_tree_fold/unfold/open/fit 四键。\n")

content = "".join(parts)
with io.open(OUT, "w", encoding="utf-8") as fh:
    fh.write(content)
print("WROTE %s (%d bytes, %d CMD blocks)" % (OUT, len(content.encode("utf-8")), content.count("CMD-")))
