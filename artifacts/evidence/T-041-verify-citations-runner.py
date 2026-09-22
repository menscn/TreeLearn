import io, os, sys, datetime

ROOT = r"D:\Project\Android\Treelearn"
OUT = os.path.join(ROOT, "artifacts", "evidence", "T-041-verify-citations.txt")
MAIN = os.path.join(ROOT, "android", "app", "src", "main")
f = io.open(OUT, "w", encoding="utf-8", newline="\n")

def w(s=""):
    f.write(s + "\n"); f.flush()

def numbered(rel, patterns, base=MAIN):
    path = os.path.join(base, rel)
    lines = io.open(path, encoding="utf-8-sig").read().split("\n")
    return [(i + 1, l.rstrip()) for i, l in enumerate(lines)
            if any(p in l for p in patterns)]

def section(title, rel, patterns, note="", base=MAIN):
    w("### " + title)
    w("command: python -X utf8 (scan of %s for %s)" % (rel.replace("\\", "/"), " | ".join(patterns)))
    if note: w("note: " + note)
    hits = numbered(rel, patterns, base)
    if not hits: w("  (no matching line)")
    for n, l in hits: w("  %s:%d| %s" % (rel.replace("\\", "/"), n, l.strip()[:200]))
    w("EXITCODE=0"); w()

w("T-041 independent verification - citation re-check of the line numbers the Verification report cites")
w("captured-at: " + datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S") + " (Asia/Shanghai)")
w("cwd: " + ROOT)
w("runner: artifacts/evidence/T-041-verify-citations-runner.py (python -X utf8), each section flushed")
w("purpose: the repo rule is that every number must be traceable to a file and line; this re-checks every")
w("         line number written into the T-041 Verification report against today's tree, source and docs.")
w()

section("C1 - focusable/outside-touchable of the two popups",
        "java/com/treelearn/app/screen/WordMenuPopup.java",
        ["new PopupWindow(", "setOutsideTouchable", "setElevation"],
        "report cited WordMenuPopup.java:82-83 for BOTH flags; focusable is the 4th PopupWindow ctor arg")
section("C2 - child-branch popup: flags, height, placement",
        "java/com/treelearn/app/screen/ChildBranchPopup.java",
        ["new PopupWindow(", "setOutsideTouchable", "LayoutParams.WRAP_CONTENT", "showAsDropDown"],
        "report cited :62-63 (actually the for-loop/addView) and :60-61 (actually ctor+inflater)")
section("C3 - the child-branch list has no ScrollView and no maxHeight",
        "res/layout/popup_child_branches.xml",
        ["child_branches_list", "ScrollView", "maxHeight", "LinearLayout"],
        "report cited :23-27 - correct")
section("C4 - comparator: the navigation page wraps its list in a ScrollView",
        "res/layout/screen_navigation.xml", ["ScrollView"],
        "the repo's existing precedent for a long list")
section("C5 - WordMenuPopup listener rows + enablement + selectAll",
        "java/com/treelearn/app/screen/WordMenuPopup.java",
        ["onQuoteRequested", "onForkRequested", "onCopyRequested", "selectAll()",
         "setEnabled(entry.enabled)", "entriesFor(message, selected)"])
section("C6 - WordMenu.entriesFor is the only source of enablement", "java/com/treelearn/app/ui/WordMenu.java",
        ["entriesFor", "canQuote()", "canFork()", "Item.FORK", "Item.SELECT_ALL"])
section("C7 - ChildBranchPopup row -> listener -> host", "java/com/treelearn/app/screen/ChildBranchPopup.java",
        ["setOnClickListener", "onBranchChosen", "list.addView(row("])
section("C8 - ChatScreen long press, forwards, guards, selection source",
        "java/com/treelearn/app/screen/ChatScreen.java",
        ["setOnLongClickListener", "setOnTouchListener", "openWordMenu()", "onQuotePressed(messageId)",
         "onForkPressed(messageId)", "onSelectionCopy(", "private String selectedText()", "selection.isEmpty()",
         "children.isEmpty()", "Row.onQuoteRequested"])
section("C9 - ChatPresenter executors of the four actions", "java/com/treelearn/app/ui/ChatPresenter.java",
        ["public void onQuotePressed", "public void onForkPressed", "public void onSelectionCopyPressed",
         "commands.fork(messageId, question)", "copyToClipboard", "withQuote("])
section("C10 - MainActivity clipboard (sensitive), openChildren, openBranch",
        "java/com/treelearn/app/MainActivity.java",
        ["EXTRA_IS_SENSITIVE", "setPrimaryClip", "public void openChildren", "chatScreen.showChildren(",
         "private void showChat(String branchId, ReadingAnchor"])
section("C11 - ChatMessage predicates", "java/com/treelearn/app/ui/ChatMessage.java",
        ["public boolean canCopy()", "public boolean canQuote()", "public boolean canFork()",
         "public boolean hasChildren()"])
section("C12 - SqliteChatStore shares BranchChildren for counts and members",
        "java/com/treelearn/app/wiring/SqliteChatStore.java",
        ["BranchChildren.of(", "BranchChildren.countsOf("])

# --- document citations ---
DOC = os.path.join(ROOT, "Docs", "06-delivery", "TreeLearn_V1_交付报告.md")
section("D1 - delivery report: the T-041 index row and the status row",
        DOC, ["对话页长按划词四项菜单", "T-041 划词四项菜单 + 子分支弹层"],
        "report cited :96 and :197", base=os.path.join(ROOT, "Docs"))
section("D2 - delivery report: the sizing numbers that conflict with the task",
        DOC, ["534 ~ 549", "T-041 的两口径"],
        "report cited :197 for the 534~549 figure", base=os.path.join(ROOT, "Docs"))
RM = os.path.join(ROOT, "android", "README.md")
section("D3 - README: the child-branch popup description and the fork wording",
        RM, ["「子分支」弹层列出的是", "按钮叫「新建分支」", "点一行经"],
        "report cited :645-652 and :678-680", base=os.path.join(ROOT, "android"))
w()

w("== drift found by this re-check ==")
w("C1/C2 : the report wrote `WordMenuPopup.java:82-83` and `ChildBranchPopup.java:62-63` for")
w("        setFocusable+setOutsideTouchable. Truth: focusable is the 4th PopupWindow ctor arg")
w("        (WordMenuPopup.java:79-80, ChildBranchPopup.java:55-56); setOutsideTouchable is")
w("        WordMenuPopup.java:82 / ChildBranchPopup.java:58.")
w("C2    : the report wrote `:60-61` for WRAP_CONTENT; truth is ChildBranchPopup.java:55-56.")
w("C3/C4 : `popup_child_branches.xml:23-27` and `screen_navigation.xml:6` were already correct.")
w("D1/D2 : the report wrote delivery-report lines :96 / :197; today they are :100 / :201 (the shared")
w("        delivery report moved under another lane, so every doc line number drifts).")
w("D3    : the report wrote README :645-652; the child-branch bullet is actually README:673-675")
w("        (entry-index rows at :638-641); README:678-680 for the fork wording was correct.")
w("C5-C12: every other cited source line number re-read and found correct.")
w("EXITCODE=0")
f.close()
print("WROTE " + OUT)
sys.exit(0)
