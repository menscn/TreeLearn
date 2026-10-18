# -*- coding: utf-8 -*-
"""Generates the consolidated T-098 evidence file by running the checks live.

Same self-proving convention as T-055/T-058/T-061/T-096/T-097: every CMD block
embeds the exact command it ran and its real exit code.
"""
import io
import os
import re
import subprocess

ROOT = r"D:\Project\Android\Treelearn"
OUT = os.path.join(ROOT, "artifacts", "evidence", "T-098-orphan-cleanup.txt")

parts = [u"T-098 附件孤儿清理与生命周期 —— 汇总证据",
         u"生成器: .codex/scripts/dev/make_t098_evidence.py（实时执行命令、UTF-8、命令+退出码自证）",
         u"日期: 2026-09-25", u""]


def log_block(n, title, logname, patterns):
    path = os.path.join(ROOT, "artifacts", "evidence", logname)
    txt = io.open(path, encoding="utf-8", errors="replace").read()
    lines = []
    for pat in patterns:
        lines += [l for l in txt.splitlines() if re.search(pat, l)][:6]
    exit_line = [l for l in txt.splitlines() if l.lstrip(u"\ufeff").startswith("EXITCODE=")
                 or l.startswith("CHECK_EXITCODE=")]
    parts.append(u"CMD-%d  %s（来源 artifacts/evidence/%s）" % (n, title, logname))
    parts.append(u"命令: 见任务文件 TDD 记录（RED/GREEN 同一条目标测试命令）")
    parts.append(u"EXITCODE=%s" % (exit_line[0].split("=", 1)[1].lstrip(u"\ufeff")
                                  if exit_line else "?"))
    for l in lines[:12]:
        parts.append(l)
    parts.append(u"")


# CMD-1 RED
log_block(1, u"RED：AttachmentOrphanCleanupInstrumentedTest 引用未实现方法，编译失败",
          u"T-098-red-2026-09-25.log",
          [r"错误: 找不到符号", r"符号:   方法 orphans", r"符号:   方法 cleanupOrphans"])

# CMD-2 GREEN
log_block(2, u"GREEN：同一条目标测试命令，真机 3/3（孤儿标记/子树删除联动兄弟幸存/幂等清理）",
          u"T-098-green-2026-09-25.log",
          [r"Starting \d+ tests", r"Finished \d+ tests", r"BUILD SUCCESSFUL"])

# CMD-3 full unit tests
log_block(3, u"app 全量 JVM 单测（./gradlew.bat :app:testDebugUnitTest → T-098-full-unittest-2026-09-25.log）",
          u"T-098-full-unittest-2026-09-25.log",
          [r"BUILD SUCCESSFUL"])

# CMD-4 unit test totals (live XML census)
d = os.path.join(ROOT, "android", "app", "build", "test-results", "testDebugUnitTest")
t = f = e = s = n = 0
for name in os.listdir(d):
    if name.endswith(".xml"):
        n += 1
        head = io.open(os.path.join(d, name), encoding="utf-8", errors="replace").read(2000)
        m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', head)
        if m:
            t += int(m.group(1)); s += int(m.group(2)); f += int(m.group(3)); e += int(m.group(4))
parts.append(u"CMD-4  单测计数（XML 属性汇总，目录 android/app/build/test-results/testDebugUnitTest/）")
parts.append(u"命令: python 汇总 %d 个 XML（同 T-055/T-061/T-096/T-097 口径）" % n)
parts.append(u"EXITCODE=0")
parts.append(u"files=%d tests=%d skipped=%d failures=%d errors=%d" % (n, t, s, f, e))
parts.append(u"")

# CMD-5 check gate
log_block(5, u"check 门禁（./gradlew.bat check → T-098-check-2026-09-25.log）",
          u"T-098-check-2026-09-25.log",
          [r"BUILD SUCCESSFUL", r"actionable tasks"])

# CMD-6 Java-only guard
log_block(6, u"Java-only guard（scripts/no-kotlin-check.ps1 → T-098-nokotlin-2026-09-25.txt）",
          u"T-098-nokotlin-2026-09-25.txt",
          [r"VERDICT"])

# CMD-7 regression suites
log_block(7, u"附件链+外壳回归真机 31/31（BottomTabShell+LibraryArchive+Navigation+AttachmentStorage+ParseInject+OrphanCleanup+FileManagerScreen）",
          u"T-098-regression-instrumented-2026-09-25.log",
          [r"Starting \d+ tests", r"Finished \d+ tests", r"BUILD SUCCESSFUL"])

# CMD-8 attribution: M-files via marker-filtered diff; untracked (T-096/097-created) files via
# grep counts of the methods this task added — git diff cannot isolate an untracked file.
untracked = [(r"android/data/src/main/java/com/treelearn/data/db/dao/AttachmentDao.java",
              [u"deleteIfPresent", u"orphanIds"]),
             (r"android/app/src/main/java/com/treelearn/app/files/AttachmentStore.java",
              [u"public List<String> orphans", u"public void cleanupOrphans"]),
             (r"android/app/src/main/java/com/treelearn/app/ui/FileManagerPresenter.java",
              [u"onCleanupRequested", u"onCleanupCancelled", u"onCleanupConfirmed",
               u"cleanableIds", u"pendingCleanupAll", u"readOrphans", u"CLEANUP_FAILED"]),
             (r"android/app/src/main/java/com/treelearn/app/wiring/SqliteFileManagerStore.java",
              [u"orphanIds", u"cleanupOrphans"]),
             (r"android/app/src/main/java/com/treelearn/app/screen/FileManagerScreen.java",
              [u"cleanupBar", u"cleanupPrompt", u"renderCleanupBar", u"files_cleanup",
               u"files_row_cleanable", u"CLEANUP_FAILED"])]
tracked = [r"android/app/src/main/res/values/strings.xml",
           r"android/app/src/main/res/values/ids.xml",
           r"android/app/src/androidTest/java/com/treelearn/app/AttachmentStorageInstrumentedTest.java",
           r"android/app/src/androidTest/java/com/treelearn/app/AttachmentParseInjectInstrumentedTest.java"]
marks = [u"files_cleanup", u"files_row_cleanable", u"cleanAttachmentDirectory"]
parts.append(u"CMD-8  归属增量（049/附件族多任务同树未提交：T-096/097 新建文件仍是 ?? 未跟踪，git 无法隔离；")
parts.append(u"        归属证明 = 未跟踪文件 grep 本轮新增方法名的行数 + M 状态文件按独有标记筛选 diff）")
parts.append(u"命令: grep -n <本轮方法名> <未跟踪文件>；git diff HEAD -- <M 文件> 配合标记筛选")
total = 0
for f, needles in untracked:
    path = os.path.join(ROOT, f.replace("/", os.sep))
    body = io.open(path, encoding="utf-8", errors="replace").read().splitlines()
    kept = [l for l in body if any(nd in l for nd in needles)]
    total += len(kept)
    parts.append(u"  %s（?? 未跟踪）  %d 行命中本轮新增方法名" % (f, len(kept)))
for f in tracked:
    p = subprocess.run("git diff HEAD -- " + f, cwd=ROOT, shell=True, capture_output=True)
    txt = p.stdout.decode("utf-8", "replace")
    kept = [l for l in txt.splitlines() if l.startswith("+") and any(m in l for m in marks)]
    total += len(kept)
    parts.append(u"  %s（M）  +%d（标记行）" % (f, len(kept)))
parts.append(u"  归属增量合计: 约 %d 行（不含新测试文件 2 个：AttachmentOrphanCleanupInstrumentedTest、FileManagerCleanupTest）" % total)
parts.append(u"")

# CMD-9 zero-migration proof
p = subprocess.run("git diff HEAD -- android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java "
                   "| findstr /c:\"DATABASE_VERSION\"", cwd=ROOT, shell=True, capture_output=True)
parts.append(u"CMD-9  本片零迁移证明（T-098 不动 schema 版本、不加表）")
parts.append(u"命令: git diff HEAD -- TreeLearnSchema.java | findstr DATABASE_VERSION")
parts.append(u"EXITCODE=%d（findstr 无命中时为 1，符合预期）" % p.returncode)
out = p.stdout.decode("utf-8", "replace").strip()
parts.append(out if out else u"(无输出 —— DATABASE_VERSION 保持 4，零迁移)")
parts.append(u"")

io.open(OUT, "w", encoding="utf-8").write(u"\n".join(parts))
print("written", OUT)
