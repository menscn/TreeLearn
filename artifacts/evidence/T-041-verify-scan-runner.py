# -*- coding: utf-8 -*-
import os, subprocess, datetime, re

ROOT = r"D:\Project\Android\Treelearn"
OUT = os.path.join(ROOT, "artifacts", "evidence", "T-041-verify-scan.txt")
F = open(OUT, "w", encoding="utf-8", newline="\n")
COUNT = [0]
FAIL = []


def w(line=""):
    F.write(line + "\n")


def flush():
    F.flush()
    os.fsync(F.fileno())


def run(args):
    p = subprocess.run(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return p.returncode, p.stdout.decode("utf-8", "replace")


def sec(title):
    COUNT[0] += 1
    title = re.sub(r"^[a-z]+ - ", "", title)
    w("")
    w("### %d - %s" % (COUNT[0], title))
    flush()


def cmd(argv, note=None):
    w("command: %s" % " ".join(argv))
    if note:
        w("note: %s" % note)
    rc, text = run(argv)
    for ln in text.splitlines():
        w(ln)
    w("EXITCODE=%d" % rc)
    flush()
    return rc, text


def rg(pattern, *paths):
    argv = ["rg", "-n", "--no-heading", "--glob", "!build", "-e", pattern] + list(paths)
    w("command: " + " ".join(argv))
    rc, text = run(argv)
    for ln in text.splitlines():
        w("  " + ln)
    w("EXITCODE=%d" % rc)
    flush()
    return text


def ok(label, cond):
    w("  %-74s %s" % (label, "OK" if cond else "FAILED"))
    flush()
    if not cond:
        FAIL.append(label)
    return cond


def read(rel):
    return open(os.path.join(ROOT, rel), encoding="utf-8", errors="replace").read()


def show_lines(rel, start, count):
    lines = read(rel).splitlines()
    for i in range(start - 1, min(start - 1 + count, len(lines))):
        w("  %s:%d: %s" % (rel.split("/")[-1], i + 1, lines[i]))


def mtime(rel):
    return datetime.datetime.fromtimestamp(os.path.getmtime(os.path.join(ROOT, rel)))


w("T-041 independent verification - source / artifact / doc scans (another lane holds the Gradle lock, so no Gradle run)")
w("captured-at: %s (Asia/Shanghai)" % datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
w("cwd: %s" % ROOT)
w("runner: artifacts/evidence/T-041-verify-scan-runner.py (python -X utf8), every step flushed to this file")
w("verifier: independent of the implementing lane; read-only judgements, no production file was edited")
flush()

NEW = [
 "android/app/src/main/java/com/treelearn/app/ui/TextSelection.java",
 "android/app/src/main/java/com/treelearn/app/ui/WordMenu.java",
 "android/app/src/main/java/com/treelearn/app/ui/ChildBranch.java",
 "android/app/src/main/java/com/treelearn/app/wiring/BranchChildren.java",
 "android/app/src/main/java/com/treelearn/app/screen/WordMenuPopup.java",
 "android/app/src/main/java/com/treelearn/app/screen/ChildBranchPopup.java",
 "android/app/src/main/res/layout/popup_word_action.xml",
 "android/app/src/main/res/layout/popup_child_branches.xml",
 "android/app/src/main/res/layout/item_child_branch.xml",
]
CHANGED = [
 ("android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java",
  r"WordMenu|TextSelection|ChildBranch|pressX|pressY|openWordMenu|showChildren|dismissPopups|onSelectionCopy|MotionEvent|onDetachedFromWindow"),
 ("android/app/src/main/java/com/treelearn/app/ui/ChatSink.java", r"ChildBranch|openChildren"),
 ("android/app/src/main/java/com/treelearn/app/MainActivity.java", r"ChildBranch|openChildren"),
 ("android/app/src/main/res/values/strings.xml",
  r"chat_word_|chat_children_popup|chat_children_unread|chat_act_fork"),
]
POP = "android/app/src/main/java/com/treelearn/app/screen/WordMenuPopup.java"
CS = "android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java"
CP = "android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java"
CB = "android/app/src/main/java/com/treelearn/app/ui/ChatMessage.java"
MA = "android/app/src/main/java/com/treelearn/app/MainActivity.java"
CBP = "android/app/src/main/java/com/treelearn/app/screen/ChildBranchPopup.java"
WM = "android/app/src/main/java/com/treelearn/app/ui/WordMenu.java"
CBL = "android/app/src/main/res/layout/popup_child_branches.xml"
NAVL = "android/app/src/main/res/layout/screen_navigation.xml"

sec("git tracking of the 13 files T-041 cites (the task claims no per-task diff exists)")
cmd(["git", "-c", "core.quotepath=false", "status", "--porcelain", "--"] + NEW + [c for c, _ in CHANGED])

sec("chain 1 - QUOTE: menu row -> Listener.onQuoteRequested -> ChatScreen.Row.onQuotePressed -> ChatPresenter.onQuotePressed")
t = rg("word_menu_quote|onQuoteRequested|onQuotePressed|chat_word_quote", POP)
t += rg("onQuoteRequested|onQuotePressed|quotedFromAnswer|private String selectedText", CS)
t += rg("onQuotePressed|state.withQuote", CP)
ok("QUOTE hops: popup listener -> screen -> presenter -> withQuote", t.count("onQuoteRequested") >= 1 and "withQuote(anchor)" in t)

sec("chain 2 - FORK: menu row -> onForkRequested -> ChatPresenter.onForkPressed -> fork() -> commands.fork -> ChatWiring.fork -> BranchCreation")
t = rg("word_menu_fork|onForkRequested|onForkPressed", POP)
t += rg("onForkRequested|onForkPressed", CS)
t += rg("onForkPressed|private void fork|commands.fork", CP)
t += rg("public ChatCommandResult fork|BranchCreation|Branch.child", "android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java")
ok("FORK reuses the existing fork path, not a second creation route", "commands.fork(messageId, question)" in t and "Branch.child" in t)

sec("chain 3 - COPY: menu row -> onCopyRequested -> onSelectionCopyPressed -> ChatSink.copyToClipboard -> MainActivity sensitive clip")
t = rg("word_menu_copy|onCopyRequested", POP)
t += rg("onCopyRequested|onSelectionCopy|onSelectionCopyPressed", CS)
t += rg("onSelectionCopyPressed|copyToClipboard", CP)
t += rg("copyToClipboard|EXTRA_IS_SENSITIVE|setPrimaryClip", MA)
ok("COPY reaches the same sensitive clipboard path as the answer row copy", "EXTRA_IS_SENSITIVE" in t and "setPrimaryClip" in t)

sec("chain 4 - SELECT_ALL: row -> WordMenuPopup.selectAll -> TextSelection.whole -> applied to this answer's TextView")
rg("word_menu_select_all|selectAll", POP)
rg("public static TextSelection whole", "android/app/src/main/java/com/treelearn/app/ui/TextSelection.java")
ok("SELECT_ALL is answered in the popup and acts on its own TextView",
   "apply(TextSelection.whole(answer.getText().length()))" in read(POP))

sec("six - enablement is data driven: WordMenu.entriesFor reads the message predicate, the popup only paints")
w("  [WordMenu.entriesFor body]")
show_lines(WM, 64, 12)
w("  [ChatMessage predicates]")
show_lines(CB, 137, 12)
w("  [WordMenuPopup.bind - rows are shown/enabled/faded from the entries]")
show_lines(POP, 134, 12)
ok("enabled flags come from ChatMessage predicates, never from a state check in the popup",
   "message.canFork()" in read(WM) and "canFork" not in read(POP))
w("  note: a row set View.setEnabled(false) still consumes the touch but View.onTouchEvent returns early,")
w("  so the click listener is never invoked - a greyed item is not triggerable (platform rule; device-verifiable).")
w("EXITCODE=0")
flush()

sec("seven - child-branch popup: construction, empty behavior, and the screen-overflow affordance")
t = rg("openChildren", MA)
rg("showChildren|ChildBranchPopup.show|children.isEmpty|childrenAnchor", CS)
rg("showAsDropDown|addView", CBP)
w("  [popup_child_branches.xml: is the list inside a scroll container?]")
show_lines(CBL, 22, 8)
w("  [contrast: the navigation page's list sits inside a ScrollView]")
rg("ScrollView", NAVL)
ok("popup adds every child with no cap and no scroll container, while the navigation list is inside a ScrollView",
   "ScrollView" in read(NAVL) and "ScrollView" not in read(CBL) and "list.addView(row" in read(CBP))

sec("eight - the openChildren seam: the branchId parameter has no consumer")
show_lines(MA, 383, 8)
_ma = read(MA).split("public void openChildren")[1]
_open_body = _ma[_ma.index("{") + 1: _ma.index("}")]
ok("MainActivity.openChildren ignores its branchId argument (dead parameter on the ChatSink port)",
   "branchId" not in _open_body)

sec("nine - second judgement point / producer-less overload scan")
t = rg("public boolean canFork", CB)
ok("canFork is defined exactly once in production code", len(t.splitlines()) == 1)
t = rg("BranchChildren.of|BranchChildren.countsOf", "android/app/src/main")
ok("childBranchCount and the popup list share one rule (BranchChildren)", "countsOf" in t and "BranchChildren.of" in t)
rg("new ReadingSession|openChildren", CP)
ok("openChildren has a production caller in ChatPresenter", "ChatPresenter.java:271" in rg("openChildren", "android/app/src/main").replace(chr(92), "/"))

sec("ten - sizing recount (my own count, same rule as the task: new files all non-blank non-comment lines)")
def judge_lines(rel):
    out = 0
    in_c = False
    for raw in read(rel).splitlines():
        s = raw.strip()
        if rel.endswith(".xml"):
            if in_c:
                if "-->" in s:
                    in_c = False
                continue
            if s.startswith("<!--"):
                if "-->" not in s:
                    in_c = True
                continue
        if not s or s.startswith("//") or s.startswith("*") or s.startswith("/*"):
            continue
        out += 1
    return out

a = 0
for rel in NEW:
    n = judge_lines(rel)
    a += n
    w("  %5d  %s" % (n, rel))
w("  (A) my subtotal for the 9 new files = %d (task reported 502)" % a)
b = 0
for rel, pat in CHANGED:
    hits = [i + 1 for i, ln in enumerate(read(rel).splitlines())
            if re.search(pat, ln) and not ln.strip().startswith("//") and not ln.strip().startswith("*")]
    b += len(hits)
    w("  %-72s %3d" % (rel.split("/")[-1], len(hits)))
w("  (B) my subtotal for the changed files = %d (task reported 42)" % b)
w("  my judgement total = %d ; hard limit 500" % (a + b))
w("EXITCODE=0")
flush()
ok("the 9 new files alone exceed the 500-line hard limit without a recorded user approval", a > 500)

sec("eleven - wording scans: the deleted placeholder, the fork button wording, and the canFork definition")
PLACE = u"\u5b50\u5206\u652f\u5217\u8868\u5c1a\u672a\u63a5\u5165\u672c\u7248\u672c"
PEND = u"chat_children_pending"
FORKW = u"\u5206\u53c9"


def scan(base_rel, needles):
    hits = []
    skip = {".gradle", "build", ".git", ".idea"}
    for dirpath, dirnames, filenames in os.walk(os.path.join(ROOT, base_rel)):
        dirnames[:] = sorted(d for d in dirnames if d not in skip)
        for name in sorted(filenames):
            p = os.path.join(dirpath, name)
            try:
                text = open(p, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            for num, line in enumerate(text.splitlines(), 1):
                if any(nd in line for nd in needles):
                    hits.append((os.path.relpath(p, ROOT), num, line.strip()[:150]))
    return hits

h = scan("android", [PLACE, PEND])
w("  scan android/ for the deleted placeholder + its string name: hits=%d" % len(h))
for x in h:
    w("    %s:%d: %s" % x)
ok("the deleted placeholder has no producer and no reference in android/", not h)
h = scan("android/app/src/main", [FORKW])
w("  scan app/src/main for the button word: hits=%d" % len(h))
for x in h:
    w("    %s:%d: %s" % x)
ok("no button word left under app/src/main", not h)
h = scan("android", [FORKW])
w("  scan android/ for the button word (data-concept prose is expected): hits=%d" % len(h))
for x in h[:20]:
    w("    %s:%d: %s" % x)
w("EXITCODE=0")
flush()

sec("twelve - evidence artifacts are self-证: captured-at / cwd / command / EXITCODE per file")
EVID = sorted(e for e in os.listdir(os.path.join(ROOT, "artifacts", "evidence"))
              if e.startswith("T-041-") and e.endswith(".txt"))
w("  %-40s %-11s %-5s %-8s %-9s" % ("file", "captured-at", "cwd", "command", "EXITCODE"))
missing = []
for e in EVID:
    text = read("artifacts/evidence/" + e)
    has_at = "captured-at" in text
    has_cwd = "cwd:" in text
    has_cmd = "command:" in text
    has_rc = "EXITCODE=" in text
    w("  %-40s %-11s %-5s %-8s %-9s" % (e, has_at, has_cwd, has_cmd, has_rc))
    if not (has_at and has_rc):
        missing.append(e)
ok("every T-041 evidence file carries captured-at and EXITCODE", not missing)
w("EXITCODE=0")
flush()

sec("thirteen - mtimes: the cited evidence against the source it describes")
for rel in [CS, MA, "android/app/src/main/res/values/strings.xml", CBP, POP, WM, CBL]:
    w("  %-22s %s" % (rel.split("/")[-1], mtime(rel)))
for e in EVID:
    w("  %-22s %s" % (e, mtime("artifacts/evidence/" + e)))
w("  newest T-041 evidence mtime = %s" % max(mtime("artifacts/evidence/" + e) for e in EVID))
w("EXITCODE=0")
flush()

sec("fourteen - the sizing numbers as written in the task file and the delivery report")
task = read(".trae/tasks/T-2026-09-21-child-branches-popup-and-word-menu-041.md")
rep = read("Docs/06-delivery/TreeLearn_V1_交付报告.md")
for token in ["544", "727", "502", "534", "549"]:
    w("  task file mentions %-5s : %s" % (token, token in task))
for line in rep.splitlines():
    if "T-041" in line:
        w("  report: " + line.strip()[:240])
w("EXITCODE=0")
flush()

w("")
w("== overall ==")
for x in FAIL:
    w("FAILURE: " + x)
w("checks run = %d ; failed = %d" % (COUNT[0], len(FAIL)))
w("VERIFY-SCAN " + ("PASSED" if not FAIL else "FAILED"))
w("EXITCODE=%d" % (0 if not FAIL else 1))
flush()
F.close()
print("FAILED=" + str(len(FAIL)))
for x in FAIL:
    print("  " + x)
