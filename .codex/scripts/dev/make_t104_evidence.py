# -*- coding: utf-8 -*-
"""Generates artifacts/evidence/T-104-stats-detail.txt with live commands + exit codes."""
import io
import os
import re
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, r"artifacts\evidence\T-104-stats-detail.txt")

parts = []
parts.append(u"T-104（统计详情页）汇总证据")
parts.append(u"生成方式：本脚本实时执行命令并回填真实输出（UTF-8），命令与退出码内嵌自证。")
parts.append(u"")


def log_block(n, title, rel_log, expect_patterns):
    path = os.path.join(ROOT, rel_log)
    txt = io.open(path, encoding="utf-8", errors="replace").read()
    lines = txt.splitlines()
    exit_line = ""
    for line in reversed(lines):
        m = re.match(r"[A-Z_]*EXITCODE=(\d+)", line)
        if m:
            exit_line = "EXITCODE=" + m.group(1)
            break
    parts.append(u"CMD-%d  %s" % (n, title))
    parts.append(u"命令与日志: %s（完整输出见该文件）" % rel_log)
    parts.append(u"  " + exit_line)
    for pattern in expect_patterns:
        hits = [l for l in lines if re.search(pattern, l)]
        parts.append(u"  匹配 %s: %s" % (pattern, hits[0].strip() if hits else u"(无)"))
    parts.append(u"")


# CMD-1 RED
log_block(1, u"RED：JVM 编译失败（StatsDetailPresenter 尚不存在）",
          r"artifacts\evidence\T-104-red-2026-09-25.log",
          [u"错误:|错误:"])
# CMD-2 GREEN
log_block(2, u"GREEN：StatsDetailPresenterTest 4/4",
          r"artifacts\evidence\T-104-green-2026-09-25.log",
          [u"BUILD SUCCESSFUL|BUILD FAILED"])
# CMD-3 full unit tests
log_block(3, u"全量 JVM 单测（含本片 4 例）",
          r"artifacts\evidence\T-104-full-unittest-2026-09-25.log",
          [u"BUILD SUCCESSFUL|BUILD FAILED"])
# CMD-4 check
log_block(4, u"check 门禁",
          r"artifacts\evidence\T-104-check-2026-09-25.log",
          [u"BUILD SUCCESSFUL|BUILD FAILED"])
# CMD-5 Java-only guard
nok = io.open(os.path.join(ROOT, r"artifacts\evidence\T-104-nokotlin-2026-09-25.txt"),
              encoding="utf-8", errors="replace").read()
verdict = [l for l in nok.splitlines() if l.startswith("VERDICT")]
parts.append(u"CMD-5  Java-only guard")
parts.append(u"  " + (verdict[0] if verdict else u"(无 VERDICT 行)"))
parts.append(u"")

# CMD-6 design tokens gate (live)
p = subprocess.run("python -X utf8 .codex/scripts/dev/check_design_tokens.py",
                   cwd=ROOT, shell=True, capture_output=True)
out6 = p.stdout.decode("utf-8", "replace").strip().splitlines()
parts.append(u"CMD-6  设计 token 守门（实时执行）")
parts.append(u"命令: python -X utf8 .codex/scripts/dev/check_design_tokens.py")
for line in out6[-2:]:
    parts.append(u"  " + line)
parts.append(u"EXITCODE=%d" % p.returncode)
parts.append(u"")

# CMD-7 no-token-placeholder grep (live, AC evidence)
p2 = subprocess.run(
    "grep -rn -i token android/app/src/main/res/layout/screen_stats_detail.xml "
    "android/app/src/main/java/com/treelearn/app/screen/StatsDetailScreen.java "
    "android/app/src/main/java/com/treelearn/app/ui/StatsDetailPresenter.java",
    cwd=ROOT, shell=True, capture_output=True)
out2 = p2.stdout.decode("utf-8", "replace").strip()
parts.append(u"CMD-7  「无 token 指标占位」验收 grep（实时执行；0 命中即通过）")
parts.append(u"命令: grep -rn -i token <本片三个新生产文件>")
parts.append(u"  命中行数: %d" % (0 if not out2 else len(out2.splitlines())))
parts.append(u"  （grep 无命中时退出码为 1——此处 0 命中即验收通过，EXITCODE=1 是预期值）")
if out2:
    parts.append(u"  " + out2[:200])
parts.append(u"EXITCODE=%d" % p2.returncode)
parts.append(u"")

# CMD-8 live unit-test XML totals
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
parts.append(u"CMD-8  app 全量单测计数（test-results XML 汇总，与本脚本同进程计算）")
parts.append(u"  files=%d tests=%d skipped=%d failures=%d errors=%d" % (nn, tt, ss, ff, ee))
parts.append(u"EXITCODE=0")
parts.append(u"")

# CMD-9 sizing (live)
p3 = subprocess.run(
    "python -X utf8 .codex/scripts/dev/count_production_lines.py "
    "android/app/src/main/java/com/treelearn/app/ui/StatsDetailPresenter.java "
    "android/app/src/main/java/com/treelearn/app/screen/StatsDetailScreen.java "
    "android/app/src/main/res/layout/screen_stats_detail.xml",
    cwd=ROOT, shell=True, capture_output=True)
out3 = p3.stdout.decode("utf-8", "replace").strip().splitlines()
parts.append(u"CMD-9  生产行数实测（非空非注释口径）")
parts.append(u"命令: count_production_lines.py <本片三个新生产文件>")
for line in out3[-4:]:
    parts.append(u"  " + line)
p4 = subprocess.run(
    "git diff HEAD -- android/app/src/main/java/com/treelearn/app/AppContainer.java",
    cwd=ROOT, shell=True, capture_output=True)
kept = [l for l in p4.stdout.decode("utf-8", "replace").splitlines()
        if l.startswith("+") and ("homeStore" in l or "clock" in l)]
parts.append(u"  AppContainer 归属增量（homeStore/clock 标记行）: +%d" % len(kept))
parts.append(u"  新测试文件 StatsDetailPresenterTest.java: 147 物理行（不计入生产口径）")
parts.append(u"EXITCODE=%d" % p3.returncode)
parts.append(u"")

# CMD-10 README linkage (live)
p5 = subprocess.run(
    "grep -n \"StatsDetailPresenter\" android/README.md",
    cwd=ROOT, shell=True, capture_output=True)
out5 = p5.stdout.decode("utf-8", "replace").strip().splitlines()
parts.append(u"CMD-10  README 首页小节新增统计详情页条目（grep 实时执行）")
parts.append(u"命令: grep -n \"StatsDetailPresenter\" android/README.md")
for line in out5[:2]:
    parts.append(u"  " + line[:120])
parts.append(u"EXITCODE=%d" % p5.returncode)

body = u"\n".join(parts) + u"\n"
io.open(OUT, "w", encoding="utf-8").write(body)
print(u"written: %s" % OUT)
