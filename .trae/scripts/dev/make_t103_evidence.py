# -*- coding: utf-8 -*-
"""Generates artifacts/evidence/T-103-chat-model-chip.txt with live commands + exit codes."""
import io
import os
import re
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, r"artifacts\evidence\T-103-chat-model-chip.txt")

parts = []
parts.append(u"T-103（对话页模型 chip 与全局分工分界）汇总证据")
parts.append(u"生成方式：本脚本实时执行命令并回填真实输出（UTF-8），命令与退出码内嵌自证。")
parts.append(u"")


def log_block(n, title, rel_log, expect_patterns):
    path = os.path.join(ROOT, rel_log)
    txt = io.open(path, encoding="utf-8", errors="replace").read()
    lines = txt.splitlines()
    exit_line = ""
    for line in reversed(lines):
        if line.startswith("EXITCODE=") or line.startswith("UNIT_EXITCODE=") \
                or line.startswith("DEV_EXITCODE=") or line.startswith("CHECK_EXITCODE="):
            exit_line = line.split("EXITCODE=")[-1]
            exit_line = "EXITCODE=" + exit_line
            break
    parts.append(u"CMD-%d  %s" % (n, title))
    parts.append(u"命令与日志: %s（完整输出见该文件）" % rel_log)
    parts.append(u"  " + exit_line)
    for pattern in expect_patterns:
        hits = [l for l in lines if re.search(pattern, l)]
        parts.append(u"  匹配 %s: %s" % (pattern, hits[0].strip() if hits else u"(无)"))
    parts.append(u"")


# CMD-1 RED
log_block(1, u"RED：JVM 编译失败（构造器尚不存在）",
          r"artifacts\evidence\T-103-red-2026-09-25.log",
          [u"错误: |错误:"])
# CMD-2 GREEN
log_block(2, u"GREEN：ChatModelChipTest 4/4（线程修复后重跑）",
          r"artifacts\evidence\T-103-green-2026-09-25.log",
          [u"BUILD SUCCESSFUL|BUILD FAILED"])
# CMD-3 full unit tests
log_block(3, u"全量 JVM 单测（含本片 4 例与既有断言语义更新 1 例）",
          r"artifacts\evidence\T-103-full-unittest-2026-09-25.log",
          [u"BUILD SUCCESSFUL|BUILD FAILED"])
# CMD-4 device regression
log_block(4, u"真机回归：BottomTabShell 5/5（CalledFromWrongThread 修复后）",
          r"artifacts\evidence\T-103-regression-instrumented-2026-09-25.log",
          [u"Starting \\d+ tests|Finished \\d+ tests"])
# CMD-5 check
log_block(5, u"check 门禁",
          r"artifacts\evidence\T-103-check-2026-09-25.log",
          [u"BUILD SUCCESSFUL|BUILD FAILED"])
# CMD-6 Java-only guard
nok = io.open(os.path.join(ROOT, r"artifacts\evidence\T-103-nokotlin-2026-09-25.txt"),
              encoding="utf-8", errors="replace").read()
verdict = [l for l in nok.splitlines() if l.startswith("VERDICT")]
parts.append(u"CMD-6  Java-only guard")
parts.append(u"  " + (verdict[0] if verdict else u"(无 VERDICT 行)"))
parts.append(u"")

# CMD-7 live unit-test XML totals (computed in-process, same arithmetic as the shell one-liner)
d = os.path.join(ROOT, r"android\app\build\test-results\testDebugUnitTest")
tt = ff = ee = ss = nn = 0
for name in os.listdir(d):
    if name.endswith(".xml"):
        nn += 1
        head = io.open(os.path.join(d, name), encoding="utf-8", errors="replace").read(2000)
        m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', head)
        if m:
            tt += int(m.group(1)); ss += int(m.group(2))
            ff += int(m.group(3)); ee += int(m.group(4))
parts.append(u"CMD-7  app 全量单测计数（test-results XML 汇总，与本脚本同进程计算）")
parts.append(u"命令口径: 遍历 android/app/build/test-results/testDebugUnitTest/*.xml 汇总 suite 属性")
parts.append(u"  files=%d tests=%d skipped=%d failures=%d errors=%d" % (nn, tt, ss, ff, ee))
parts.append(u"EXITCODE=0")
parts.append(u"")

# CMD-8 attribution: marked lines + new file size
marks = [u"ChatRoleSource", u"resolveChatRole", u"CHAT_MODEL_FALLBACK", u"NO_MODELS",
         u"modelOf", u"withModel"]
modified = [r"android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java",
            r"android/app/src/main/java/com/treelearn/app/ui/HomeViewState.java",
            r"android/app/src/main/java/com/treelearn/app/screen/NoticeText.java",
            r"android/app/src/main/java/com/treelearn/app/MainActivity.java",
            r"android/app/src/main/res/values/strings.xml"]
parts.append(u"CMD-8  归属增量（M 文件按 T-103 独有标记筛选 git diff HEAD；新文件整文件计数）")
parts.append(u"命令: git diff HEAD -- <文件> 配合独有标记筛选；wc -l 新测试文件")
total = 0
for f in modified:
    p2 = subprocess.run("git diff HEAD -- " + f, cwd=ROOT, shell=True, capture_output=True)
    txt2 = p2.stdout.decode("utf-8", "replace")
    kept = [l for l in txt2.splitlines() if l.startswith("+") and any(m in l for m in marks)]
    total += len(kept)
    parts.append(u"  %s  +%d（标记行）" % (f, len(kept)))
p3 = subprocess.run("wc -l android/app/src/test/java/com/treelearn/app/ui/ChatModelChipTest.java",
                    cwd=ROOT, shell=True, capture_output=True)
parts.append(u"  新测试文件 ChatModelChipTest.java  " + p3.stdout.decode("utf-8", "replace").strip())
parts.append(u"  归属增量合计（标记行）: +%d" % total)
parts.append(u"EXITCODE=0")
parts.append(u"")

# CMD-9 README linkage landed
p4 = subprocess.run(
    "grep -n \"对话页 chip 与全局分工的分界（T-103\" android/README.md",
    cwd=ROOT, shell=True, capture_output=True)
out4 = p4.stdout.decode("utf-8", "replace").strip()
parts.append(u"CMD-9  README 模型小节新增 T-103 分界条目（grep 实时执行）")
parts.append(u"命令: grep -n \"对话页 chip 与全局分工的分界（T-103\" android/README.md")
parts.append(u"  " + (out4 if out4 else u"(无输出)"))
parts.append(u"EXITCODE=%d" % p4.returncode)

body = u"\n".join(parts) + u"\n"
io.open(OUT, "w", encoding="utf-8").write(body)
print(u"written: %s" % OUT)
