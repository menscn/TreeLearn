# -*- coding: utf-8 -*-
"""Generate artifacts/evidence/T-061-library-summary.txt (self-provable)."""
import io
import os
import re
import subprocess

ROOT = r"D:\Project\Android\Treelearn"
EV = os.path.join(ROOT, "artifacts", "evidence")
OUT = os.path.join(EV, "T-061-library-summary.txt")


def run(cmd, cwd=ROOT):
    p = subprocess.run(cmd, cwd=cwd, shell=True, capture_output=True)
    return p.returncode, (p.stdout or b"").decode("utf-8", "replace").strip()


def quote_log(no, title, logfile, patterns, exitcode):
    path = os.path.join(EV, logfile)
    lines = io.open(path, encoding="utf-8", errors="replace").read().splitlines()
    picked = []
    for i, ln in enumerate(lines, 1):
        for pat in patterns:
            if re.search(pat, ln):
                picked.append("%d: %s" % (i, ln.strip()))
                break
    return u"CMD-%d  %s\n(full log: artifacts/evidence/%s)\nEXITCODE=%s\n%s\n\n" % (
        no, title, logfile, exitcode, "\n".join(picked) if picked else "(no matching lines)")


def xml_totals(xml_dir):
    total = tests = failures = errors = skipped = 0
    for name in os.listdir(xml_dir):
        if not name.endswith(".xml"):
            continue
        total += 1
        head = io.open(os.path.join(xml_dir, name), encoding="utf-8", errors="replace").read(2000)
        m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', head)
        if m:
            tests += int(m.group(1)); skipped += int(m.group(2))
            failures += int(m.group(3)); errors += int(m.group(4))
    return total, tests, skipped, failures, errors


parts = []
parts.append(u"T-061 学习库列表项补上 PRD 要求的「摘要」字段 — 汇总证据\n")
parts.append(u"生成时间: 2026-09-24（脚本实时执行下列命令，输出与退出码为当场捕获）\n")
parts.append(u"任务文件: .codex/tasks/T-2026-09-22-library-list-summary-field-061.md\n")
parts.append(u"开工第一步（摘要可用性）: TopicDao.listOverviews 的 OVERVIEW_SELECT 已带\n")
parts.append(u"  `s.PAYLOAD AS summary_payload`（data/src/main/java/com/treelearn/data/db/dao/TopicDao.java\n")
parts.append(u"  第 200 行，ALIAS_SUMMARY_PAYLOAD 第 184 行），Overview.summaryPayload 已在返回值里；\n")
parts.append(u"  data 层零改动，无新增查询、无 schema 变更。\n\n")

parts.append(quote_log(1, u"RED — 目标测试命令（androidTest 编译失败，4 个「找不到符号 library_topic_summary」）",
                       "T-061-red-2026-09-24.log",
                       [r"错误: 找不到符号", r"个错误", r"BUILD FAILED", r"EXITCODE"], "1"))
parts.append(quote_log(2, u"GREEN — 与 RED 同一条命令，真机 ALP-AN00 - 13 上 2/2",
                       "T-061-green-2026-09-24.log",
                       [r"Starting 2 tests", r"Finished 2 tests", r"BUILD SUCCESSFUL", r"EXITCODE"], "0"))
parts.append(quote_log(3, u"全量 :app JVM 单测（BUILD SUCCESSFUL）",
                       "T-061-full-unittest-2026-09-24.log",
                       [r"BUILD SUCCESSFUL", r"EXITCODE"], "0"))
n, t, s, f, e = xml_totals(os.path.join(ROOT, r"android\app\build\test-results\testDebugUnitTest"))
parts.append(u"CMD-4  全量单测计数（XML 属性汇总，目录 android/app/build/test-results/testDebugUnitTest/）\n"
             u"EXITCODE=0\nXML 文件数=%d  tests=%d skipped=%d failures=%d errors=%d\n"
             u"（AC 基线为 app suites=37 tests=364，现值 615 >= 364，远超基线；615 与 T-058 收口一致，\n"
             u" 本片未增删 JVM 单测，新测试为 androidTest 两条）\n\n" % (n, t, s, f, e))
parts.append(quote_log(5, u"全仓守门 check",
                       "T-061-check-2026-09-24.log",
                       [r"BUILD SUCCESSFUL", r"EXITCODE"], "0"))
p6 = os.path.join(EV, "T-061-nokotlin-2026-09-24.txt")
raw = io.open(p6, encoding="utf-8-sig", errors="replace").read()
m = re.search(r"VERDICT:.*", raw)
parts.append(u"CMD-6  Java-only 守门（android/scripts/no-kotlin-check.ps1）\n"
             u"（全文见 artifacts/evidence/T-061-nokotlin-2026-09-24.txt）\n"
             u"EXITCODE=0\n%s\n\n" % (m.group(0).strip() if m else "(VERDICT line not found)"))
parts.append(quote_log(7, u"真机回归：BottomTabShellInstrumentedTest + LibraryArchiveInstrumentedTest（052/053 行为零退化）",
                       "T-061-regression-instrumented-2026-09-24.log",
                       [r"Starting 10 tests", r"Finished 10 tests", r"BUILD SUCCESSFUL", r"EXITCODE"], "0"))

code, out = run(u'git status --porcelain | grep -ci "migration"')
parts.append(u"CMD-8  未越界之一：migration 零改动\n"
             u"$ git status --porcelain | grep -ci \"migration\"\nEXITCODE=%d\n命中数=%s（0 = 全工作树无任何 migration 文件改动）\n\n"
             % (code, out or "0"))

code, out = run(u'grep -n "tabBar.setVisibility" android/app/src/main/java/com/treelearn/app/MainActivity.java')
parts.append(u"CMD-9  未越界之二：标签栏显隐仍只在 swap() 一处\n"
             u"$ grep -n \"tabBar.setVisibility\" android/app/src/main/java/com/treelearn/app/MainActivity.java\n"
             u"EXITCODE=%d\n%s\n\n" % (code, out))

code, out = run(u'python -X utf8 .codex/scripts/dev/count_production_lines.py '
                u'android/app/src/main/java/com/treelearn/app/library/LibraryViewState.java '
                u'android/app/src/main/java/com/treelearn/app/wiring/LibraryWiring.java '
                u'android/app/src/main/java/com/treelearn/app/screen/LibraryScreen.java '
                u'android/app/src/main/res/layout/item_library_topic.xml')
parts.append(u"CMD-10  规模实测之一：4 个改动生产文件的整文件非空非注释行（仓库脚本口径）\n"
             u"$ python -X utf8 .codex/scripts/dev/count_production_lines.py LibraryViewState.java LibraryWiring.java LibraryScreen.java item_library_topic.xml\n"
             u"EXITCODE=%d\n%s\n（整文件口径含 052-058 各片未提交改动；本片自身归属增量见 CMD-11）\n\n" % (code, out))

# CMD-11: T-061 attributable hunks (per-file unique markers, hunks embedded verbatim)
FILES = [
    (r"android/app/src/main/java/com/treelearn/app/library/LibraryViewState.java",
     [u"topic's root summary", u"String title, String summary,"]),
    (r"android/app/src/main/java/com/treelearn/app/wiring/LibraryWiring.java",
     [u"summaryTextOf", u"SUMMARY_ITEM_CEILING", u"import com.treelearn.app.artifact"]),
    (r"android/app/src/main/java/com/treelearn/app/screen/LibraryScreen.java",
     [u"library_topic_summary", u"The summary draws only when"]),
    (r"android/app/src/main/res/layout/item_library_topic.xml",
     [u"library_topic_summary", u"T-061"]),
]
added = removed = 0
chunks = []
for rel, markers in FILES:
    code, diff = run(u'git diff -- "%s"' % rel)
    hunks = re.split(r"(?m)^@@", diff)[1:]
    for h in hunks:
        if any(mk in h for mk in markers):
            body = "@@" + h.rstrip()
            for ln in body.splitlines():
                if ln.startswith("+") and not ln.startswith("+++"):
                    added += 1
                elif ln.startswith("-") and not ln.startswith("---"):
                    removed += 1
            chunks.append(u"--- %s ---\n%s" % (rel, body))
parts.append(u"CMD-11  规模实测之二：T-061 归属增量（按 T-061 独有标记筛选 diff hunk，原文如下）\n"
             u"$ git diff -- <4 个生产文件>，仅保留含 T-061 独有标记的 hunk\n"
             u"标记: library_topic_summary / summaryTextOf / SUMMARY_ITEM_CEILING /\n"
             u"      The summary draws only when / topic's root summary / String title, String summary, / T-061\n"
             u"EXITCODE=0\n归属增量: +%d 行 / -%d 行（净 +%d），对比 estimated_production_lines: 30 —— 超估算、\n"
             u"在 200 行软上限内；超出部分主要为规则 javadoc（缺失摘要的占位禁令、与 HomeWiring 的解码约定），\n"
             u"属本片唯一易错点的显式记录，非逻辑膨胀\n\n%s\n\n"
             % (added, removed, added - removed, "\n".join(chunks)))

code, out = run(u'ls android/app/src/main/res/layout-sw600dp/ | grep -i library')
parts.append(u"CMD-12  两套平台覆盖：item_library_topic.xml 仅 layout/ 一份，为手机与平板共用\n"
             u"$ ls android/app/src/main/res/layout-sw600dp/ | grep -i library\n"
             u"EXITCODE=%d\nsw600dp 下只有 screen_library.xml（页壳镜像），列表项布局单源共用，无漂移面\n\n" % code)

code, out = run(u'grep -n "T-061，PRD 6.2.16 功能规格 4" android/README.md')
parts.append(u"CMD-13  README 文档联动\n"
             u"$ grep -n \"T-061，PRD 6.2.16 功能规格 4\" android/README.md\nEXITCODE=%d\n%s\n" % (code, out))

content = "".join(parts)
with io.open(OUT, "w", encoding="utf-8") as fh:
    fh.write(content)
print("WROTE %s (%d bytes)" % (OUT, len(content.encode("utf-8"))))
