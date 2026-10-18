"""Who can reach these presenters (read-only). Unreachable = unbuilt.

For each symbol: every file that mentions it, split into src/main, src/test and src/androidTest.
A presenter whose src/main hits are all inside its own package has no screen, no layout and no
caller, so no gesture reaches it.
"""
import os
import sys

SYMBOLS = [
    "NavigationPresenter",
    "NavigationScreen",
    "NavigationViewState",
    "SearchPresenter",
    "SearchScreen",
    "ReadingSession",
    "ReadingStore",
    "SqliteNavigationStore",
    "SqliteSearchRepository",
    "TreeCanvasScreen",
    "TreeCanvasPresenter",
]

ROOTS = {
    "main": os.path.join("src", "main"),
    "test": os.path.join("src", "test"),
    "androidTest": os.path.join("src", "androidTest"),
}


def classify(path):
    for kind, marker in ROOTS.items():
        if marker in path:
            return kind
    return "other"


def main(root):
    android = os.path.join(root, "android")
    files = []
    for base, _dirs, names in os.walk(android):
        for name in names:
            if name.endswith(".java"):
                files.append(os.path.join(base, name))
    for symbol in SYMBOLS:
        hits = {}
        for path in files:
            with open(path, encoding="utf-8") as handle:
                count = handle.read().count(symbol)
            if count:
                hits[path] = count
        buckets = {}
        for path in hits:
            buckets.setdefault(classify(path), []).append(path)
        print(symbol + ":")
        for kind in ("main", "test", "androidTest", "other"):
            paths = sorted(buckets.get(kind, []))
            if not paths:
                continue
            print("  %s (%d file(s)):" % (kind, len(paths)))
            for path in paths:
                print("    " + os.path.relpath(path, root))
        if not hits:
            print("  no references at all")
        print("")


if __name__ == "__main__":
    main(sys.argv[1])
