"""T-042 independent verification runner (read-only; never runs Gradle).

Writes artifacts/evidence/T-042-verify-scan.txt. Every step records its exact command,
the captured output and EXITCODE=. Run from the repo root:
    python -X utf8 artifacts/evidence/T-042-verify-scan-runner.py
All ripgrep calls use fixed strings (-F -e) so no regex escaping can silently disable a check.
"""
import datetime
import hashlib
import os
import re
import subprocess
import zipfile

REPO = r"D:\Project\Android\Treelearn"
OUT = os.path.join(REPO, "artifacts", "evidence", "T-042-verify-scan.txt")
os.chdir(REPO)

fh = open(OUT, "w", encoding="utf-8", newline="\n")


def w(line=""):
    fh.write(line + "\n")
    fh.flush()


def step(label, note, cmd):
    w("### " + label)
    w("note: " + note)
    w("command: " + cmd)
    proc = subprocess.run(cmd, shell=True, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    for line in (proc.stdout or "").splitlines():
        w(line)
    for line in (proc.stderr or "").splitlines():
        w("stderr: " + line)
    w("EXITCODE=" + str(proc.returncode))
    w("")


MAIN = "android/app/src/main/java/com/treelearn/app/MainActivity.java"
CONTAINER = "android/app/src/main/java/com/treelearn/app/AppContainer.java"
PRESENTER = "android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java"
SCREEN = "android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java"
STORE = "android/app/src/main/java/com/treelearn/app/reading/SqliteReadingStore.java"
REPORT = '"Docs/06-delivery/TreeLearn_V1_交付报告.md"'
DESIGN = '"Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md"'

w("T-042 independent verification - source / artifact / doc scans (another lane holds the Gradle lock, so no Gradle run)")
w("captured-at: " + datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S") + " (Asia/Shanghai)")
w("cwd: " + REPO)
w("runner: artifacts/evidence/T-042-verify-scan-runner.py (python -X utf8), each step flushed to this file")
w("verifier: independent of the implementing lane; read-only judgements, no production file was edited")
w("")

step("1 - what does git track among the files T-042 cites?",
     "the task claims no per-task git diff exists; this records the tracking state that claim rests on",
     "git -c core.quotepath=false status --porcelain -- android/app/src/main/java/com/treelearn/app/ "
     "android/core/src/main/java/com/treelearn/core/domain/ReadingAnchor.java")

step("2a - the door, in the CURRENT MainActivity",
     "the task and the delivery report cite MainActivity.java:383; re-read the current line numbers",
     "rg -n -F -e \"private void showChat\" -e \"new ChatPresenter\" -e \"reading.saveAnchor\" "
     "-e \"ReadingStore reading = container.readingStore();\" -e \"chatPresenter.start();\" " + MAIN)

step("2b - the store, in the CURRENT AppContainer",
     "the task cites AppContainer.java:119 for 'new SqliteReadingStore'",
     "rg -n -F -e \"new SqliteReadingStore\" -e \"public ReadingStore readingStore()\" -e \"readingStore\" " + CONTAINER)

step("2c - what is actually at the cited line 395 today",
     "MainActivity.java:395 is cited as a reading consumer by the task file and the delivery report",
     "rg -n --no-heading \".\" " + MAIN + " | rg \"^(39[0-9]|44[0-9]|46[0-9]):\"")

step("3 - production consumers of the reading ports (outside the reading package)",
     "acceptance: ReadingStore/ReadingSession must have production consumers, not test-only references",
     "rg -n \"ReadingStore|ReadingSession|SqliteReadingStore\" android/app/src/main/java --glob \"!**/reading/**\"")

step("3b - every main-code producer of new ReadingSession / anchorOf / saveAnchor / saveSeen / remember / markSeenThrough",
     "second-decision-point check: one reader, one writer, one unread decision",
     "rg -n -F -e \"new ReadingSession\" -e \"anchorOf(\" -e \"saveAnchor(\" -e \"saveSeen(\" "
     "-e \".remember(\" -e \"markSeenThrough\" android/app/src/main/java android/core/src/main/java --glob \"*.java\"")

step("4 - the restore path from the real view callback to the store",
     "chain: layout listener -> reportRowsLaidOut -> onRowsLaidOut -> session.open -> anchorOf -> renderPosition -> scrollTo",
     "rg -n -F -e \"addOnLayoutChangeListener\" -e \"reportRowsLaidOut\" -e \"onRowsLaidOut\" "
     "-e \"renderPosition\" -e \"scrollTo(0, target)\" -e \"setOnScrollChangeListener\" -e \"reportScroll\" "
     "-e \"onViewportChanged\" " + SCREEN + " " + MAIN + " " + PRESENTER)

step("5 - UI-thread IO check: every store call site vs AppContainer.background()",
     "reads and writes must sit inside background.execute; SqliteReadingStore must hold the helper, not an open db",
     "rg -n -F -e \"container.background().execute\" -e \"Executors.newSingleThreadExecutor\" -e \"background.execute\" "
     "-e \"getWritableDatabase\" " + CONTAINER + " " + MAIN + " " + PRESENTER + " " + STORE)

step("6 - save-failure containment: which write can throw, and who catches it?",
     "the brief promises 'a failed save only records a visible notice'; the DAO documents ISE on a missing branch",
     "rg -n -F -e \"try {\" -e \"catch (\" -e \"throw new IllegalStateException\" " + STORE +
     " android/data/src/main/java/com/treelearn/data/db/dao/ReadingPositionDao.java "
     "android/data/src/main/java/com/treelearn/data/db/dao/BranchDao.java")

step("6b - is a global uncaught-exception handler installed anywhere in the app?",
     "an uncaught throw on the background thread reaches Android's default handler when none is installed",
     "rg -n -F -e \"UncaughtExceptionHandler\" -e \"setDefaultUncaughtExceptionHandler\" android --glob \"*.java\"")

step("7 - Snapshot.of overloads: production arg count vs test-only overload",
     "producer-less-overload check: which overload does production use, which only tests",
     "rg -n -A 3 -F \"Snapshot.of(\" android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java "
     "android/app/src/test/java/com/treelearn/app/ui/ChatTestDoubles.java "
     "android/app/src/test/java/com/treelearn/app/ui/ChatReadingTest.java")

step("8 - the seam and its callers",
     "one door: showChat(String) must delegate to showChat(String, ReadingAnchor)",
     "rg -n -F -e \"showChat(\" -e \"openAnchor(\" -e \"public void openBranch\" " + MAIN +
     " android/app/src/main/java/com/treelearn/app/search/SearchPresenter.java")

w("### 9 - mtime ordering: every T-042 artifact against the sources it claims to have measured")
w("note: the task discloses that RED and the first mutations predate the implementation; it does not disclose")
w("      that MainActivity.java and AppContainer.java were rewritten AFTER every artifact (parallel T-040 lane)")
w("command: python os.path.getmtime per path (equivalent to Get-Item | LastWriteTime)")
w("kind            mtime                path")
targets = [
    "artifacts/evidence/T-042-red.txt",
    "artifacts/evidence/T-042-mutation-save-thread.txt",
    "artifacts/evidence/T-042-mutation-gone-anchor.txt",
    "artifacts/evidence/T-042-mutation-unread-monotonic.txt",
    "artifacts/evidence/T-042-mutation-reverted.txt",
    "artifacts/evidence/T-042-source-scan.txt",
    "artifacts/evidence/T-042-sizing.txt",
    "artifacts/evidence/T-042-green.txt",
    "artifacts/evidence/T-042-java-only.txt",
    "artifacts/evidence/T-042-apk-contains-wiring.txt",
    "android/app/build/outputs/apk/release/app-release.apk",
    "android/app/src/main/java/com/treelearn/app/ui/ChatData.java",
    "android/app/src/main/java/com/treelearn/app/ui/ChatSink.java",
    "android/app/src/main/java/com/treelearn/app/ui/ChatActions.java",
    "android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java",
    "android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java",
    "android/app/src/main/java/com/treelearn/app/wiring/ChatStore.java",
    "android/app/src/main/java/com/treelearn/app/wiring/SqliteChatStore.java",
    "android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java",
    "android/app/src/main/java/com/treelearn/app/reading/SqliteReadingStore.java",
    "android/app/src/main/java/com/treelearn/app/AppContainer.java",
    "android/app/src/main/java/com/treelearn/app/MainActivity.java",
]
for rel in targets:
    p = os.path.join(REPO, rel)
    if os.path.exists(p):
        w("  %-60s %s" % (rel, datetime.datetime.fromtimestamp(os.path.getmtime(p))
                          .strftime("%Y-%m-%d %H:%M:%S")))
    else:
        w("  %-60s MISSING" % rel)
w("EXITCODE=0")
w("")

w("### 10 - release APK: size, mtime, SHA-256 and dex string-table scan (recomputed, not copied)")
w("note: same claim as T-042-apk-contains-wiring.txt; t he names live in the dex string table as MUTF-8,")
w("      so an ASCII byte scan finds them with no Android tooling. ChatReadingTest is the negative control.")
apk = os.path.join(REPO, "android", "app", "build", "outputs", "apk", "release", "app-release.apk")
if not os.path.exists(apk):
    w("APK MISSING: " + apk)
else:
    st = os.stat(apk)
    w("apk-path: " + apk)
    w("apk-bytes: %d" % st.st_size)
    w("apk-mtime: " + datetime.datetime.fromtimestamp(st.st_mtime).strftime("%Y-%m-%d %H:%M:%S"))
    digest = hashlib.sha256()
    with open(apk, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    w("apk-sha256: " + digest.hexdigest().upper())
    symbols = ["ReadingSession", "SqliteReadingStore", "onViewportChanged", "renderPosition",
               "onRowsLaidOut", "onLeaving", "settle", "ChatReadingTest", "PendingChatWiring", "NOT_WIRED"]
    with zipfile.ZipFile(apk) as z:
        names = sorted(n for n in z.namelist() if re.match(r"classes\d*\.dex$", n))
        w("dex-files: " + ", ".join(names))
        counts = dict((s, 0) for s in symbols)
        for name in names:
            blob = z.read(name)
            for s in symbols:
                counts[s] += blob.count(s.encode("ascii"))
    for s in symbols:
        w("  %-24s occurrences=%d" % (s, counts[s]))
w("EXITCODE=0")
w("")

w("### 11 - JUnit XML actually on disk right now (the debug report may belong to the parallel lane now)")
for rel in ["android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.ChatReadingTest.xml",
            "android/app/build/test-results/testReleaseUnitTest/TEST-com.treelearn.app.ui.ChatReadingTest.xml"]:
    p = os.path.join(REPO, rel)
    if not os.path.exists(p):
        w(rel + " :: MISSING in the current build tree")
        continue
    w(rel + " :: mtime " + datetime.datetime.fromtimestamp(os.path.getmtime(p))
      .strftime("%Y-%m-%d %H:%M:%S"))
    body = open(p, encoding="utf-8").read()
    for line in body.splitlines()[:2]:
        w("  " + line)
    w("  failure-elements=%d error-elements=%d testcase-elements=%d"
      % (body.count("<failure"), body.count("<error"), body.count("<testcase")))
w("EXITCODE=0")
w("")

step("12 - the runtime fact in the current docs",
     "README and the delivery report must carry the wired fact plus the deferred device item",
     "rg -n -F -e \"T-042\" -e \"showChat(String, ReadingAnchor)\" -e \"deferred\" android/README.md " + REPORT)

step("13 - the doc that states what a stale anchor does",
     "authority for 'top of the branch' vs 'live end': the task file says top, the design says live end",
     "rg -n -F -e \"atLiveEnd\" -e \"活动末端\" -e \"顶部\" android/README.md " + DESIGN)

step("14 - whole-file production line count (the upper-bound tool the task cites)",
     "the 94-line figure is the implementer's per-symbol attribution; this is the disclosed upper bound",
     "python -X utf8 .trae/scripts/dev/count_production_lines.py "
     "android/app/src/main/java/com/treelearn/app/ui/ChatData.java "
     "android/app/src/main/java/com/treelearn/app/ui/ChatSink.java "
     "android/app/src/main/java/com/treelearn/app/ui/ChatActions.java " + PRESENTER + " " + SCREEN + " "
     "android/app/src/main/java/com/treelearn/app/wiring/ChatStore.java "
     "android/app/src/main/java/com/treelearn/app/wiring/SqliteChatStore.java "
     "android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java " + STORE + " " + CONTAINER + " " + MAIN)

w("### 15 - re-apply the sizing evidence's own attribution patterns to the CURRENT tree")
w("note: T-042-sizing.txt declares its 94 as a FLOOR and lists the hit lines; those lines were taken before")
w("      MainActivity/AppContainer changed, so the same patterns are re-run here on today's files.")
src = open(os.path.join(REPO, "artifacts", "evidence", "T-042-sizing.txt"), encoding="utf-8").read()
pairs = re.findall(r'\("([^"]+)", r"((?:[^"\\]|\\.)*)"\)', src)
w("patterns read out of T-042-sizing.txt: %d" % len(pairs))
reported = re.search(r"\(B\) subtotal \(judgement lines, deciding value\) = (\d+)", src)
w("subtotal reported in T-042-sizing.txt: " + (reported.group(1) if reported else "NOT FOUND"))
listed = re.findall(r"^\s+(\S+\.java)\s+(\d+)\s+lines", src, re.M)
if listed:
    w("listed per-file counts: " + ", ".join("%s=%s" % (f, c) for f, c in listed))
    w("sum of the listed counts: %d" % sum(int(c) for _, c in listed))


def is_prose(s):
    t = s.strip()
    if not t:
        return True
    return t.startswith("//") or t.startswith("*") or t.startswith("/*") or t.startswith("*/")


total = 0
for rel, pattern in pairs:
    path = os.path.join(REPO, rel)
    if not os.path.exists(path):
        w("  MISSING FILE: " + rel)
        continue
    rx = re.compile(pattern)
    hits = [i + 1 for i, ln in enumerate(open(path, encoding="utf-8").read().splitlines())
            if rx.search(ln) and not is_prose(ln)]
    total += len(hits)
    w("  %-28s %3d  lines %s" % (rel.split("/")[-1], len(hits), ",".join(str(h) for h in hits)))
w("re-run subtotal on today's tree: %d" % total)
w("EXITCODE=0")
w("")

step("16 - task contract validator", "errors must be 0 for T-042 to stand",
     "python -X utf8 .trae/scripts/validate_tasks.py")

w("END OF T-042 INDEPENDENT VERIFICATION SCAN")
fh.close()
print("written: " + OUT)
print("bytes: %d" % os.path.getsize(OUT))
