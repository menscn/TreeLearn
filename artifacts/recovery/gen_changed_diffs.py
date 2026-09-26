"""Generate unified diffs (decompiled preloss vs decompiled HEAD) for the 30 changed classes."""
import difflib
import os

ROOT = r"D:\Project\Android\Treelearn"
PRE = os.path.join(ROOT, "artifacts", "recovery", "decompiled")
HEAD = os.path.join(ROOT, "artifacts", "recovery", "decompiled-head")
OUT = os.path.join(ROOT, "artifacts", "recovery", "changed-diffs.txt")

CHANGED = [
    "com/treelearn/app/AppContainer.java",
    "com/treelearn/app/MainActivity.java",
    "com/treelearn/app/artifact/DerivedService.java",
    "com/treelearn/app/artifact/SqliteDerivedStore.java",
    "com/treelearn/app/generation/AiGatewayStreams.java",
    "com/treelearn/app/screen/AnswerActionBar.java",
    "com/treelearn/app/screen/ChatScreen.java",
    "com/treelearn/app/screen/ChildBranchPopup.java",
    "com/treelearn/app/screen/HomeScreen.java",
    "com/treelearn/app/screen/NoticeText.java",
    "com/treelearn/app/screen/SettingsScreen.java",
    "com/treelearn/app/ui/ChatActions.java",
    "com/treelearn/app/ui/ChatCommandResult.java",
    "com/treelearn/app/ui/ChatMessage.java",
    "com/treelearn/app/ui/ChatPresenter.java",
    "com/treelearn/app/ui/ChatSink.java",
    "com/treelearn/app/ui/ChatViewState.java",
    "com/treelearn/app/ui/HomeActions.java",
    "com/treelearn/app/ui/HomeData.java",
    "com/treelearn/app/ui/HomePresenter.java",
    "com/treelearn/app/ui/HomeViewState.java",
    "com/treelearn/app/ui/KeyVault.java",
    "com/treelearn/app/ui/SettingsStore.java",
    "com/treelearn/app/wiring/AndroidSettingsStore.java",
    "com/treelearn/app/wiring/ChatWiring.java",
    "com/treelearn/app/wiring/HomeStore.java",
    "com/treelearn/app/wiring/HomeWiring.java",
    "com/treelearn/app/wiring/PromptLimits.java",
    "com/treelearn/app/wiring/RefusalNotices.java",
    "com/treelearn/app/wiring/SqliteHomeStore.java",
]

CFR_HEADER_END = "*/\n"


def load(base, rel):
    path = os.path.join(base, rel.replace("/", os.sep))
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    # drop CFR header for cleaner diffs
    if text.startswith("/*") and "Decompiled with CFR" in text[:400]:
        idx = text.find(CFR_HEADER_END)
        if idx != -1:
            text = text[idx + len(CFR_HEADER_END):]
    return text.splitlines(keepends=True)


def main():
    total_changed = 0
    with open(OUT, "w", encoding="utf-8", newline="\n") as out:
        for rel in CHANGED:
            a = load(PRE, rel)   # preloss (with lost edits)
            b = load(HEAD, rel)  # HEAD (base)
            diff = list(difflib.unified_diff(b, a, fromfile="HEAD/" + rel, tofile="PRELOSS/" + rel, n=3))
            n = sum(1 for d in diff if d.startswith("+") and not d.startswith("+++"))
            m = sum(1 for d in diff if d.startswith("-") and not d.startswith("---"))
            total_changed += 1 if diff else 0
            out.write(f"===== {rel}  (+{n} -{m}) =====\n")
            out.writelines(diff if diff else ["  (identical)\n"])
            out.write("\n")
            print(f"{rel}: +{n} -{m}")
    print(f"\nreport: {OUT}")


if __name__ == "__main__":
    main()
