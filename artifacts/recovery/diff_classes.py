"""Diff preloss recovered classes vs freshly compiled HEAD classes.

Outputs:
- added:    top-level classes present only in preloss (new files, lost)
- changed:  top-level classes present in both but differing bytes (modified files, lost edits)
- identical: common top-level classes with same bytes (safe, no action)
"""
import hashlib
import os
import sys

ROOT = r"D:\Project\Android\Treelearn"
PRE = os.path.join(ROOT, "artifacts", "recovery", "classes-preloss-debug")
FRESH = os.path.join(
    ROOT, "android", "app", "build", "intermediates", "javac", "debug",
    "compileDebugJavaWithJavac", "classes",
)


def scan(base):
    out = {}
    for dirpath, _dirnames, filenames in os.walk(base):
        for name in filenames:
            if not name.endswith(".class"):
                continue
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, base).replace("\\", "/")
            with open(full, "rb") as fh:
                out[rel] = hashlib.md5(fh.read()).hexdigest()
    return out


def toplevel(rel):
    # strip inner classes: Foo$Bar.class -> Foo.class
    if "$" in rel:
        rel = rel.split("$", 1)[0] + ".class"
    return rel


def main():
    pre = scan(PRE)
    fresh = scan(FRESH)
    pre_top, fresh_top = {}, {}
    for rel, h in pre.items():
        pre_top.setdefault(toplevel(rel), set()).add(h)
    for rel, h in fresh.items():
        fresh_top.setdefault(toplevel(rel), set()).add(h)

    added = sorted(set(pre_top) - set(fresh_top))
    removed = sorted(set(fresh_top) - set(pre_top))
    changed = sorted(
        t for t in set(pre_top) & set(fresh_top) if pre_top[t] != fresh_top[t]
    )

    print(f"preloss top-level: {len(pre_top)}, fresh top-level: {len(fresh_top)}")
    print(f"\n== ADDED (lost new files): {len(added)} ==")
    for t in added:
        print(t)
    print(f"\n== CHANGED (lost edits to existing files): {len(changed)} ==")
    for t in changed:
        print(t)
    print(f"\n== REMOVED at HEAD (unexpected): {len(removed)} ==")
    for t in removed:
        print(t)

    with open(os.path.join(ROOT, "artifacts", "recovery", "diff-report.txt"), "w", encoding="utf-8") as fh:
        fh.write("ADDED (lost new files)\n")
        fh.write("\n".join(added) + "\n\n")
        fh.write("CHANGED (lost edits to existing files)\n")
        fh.write("\n".join(changed) + "\n")
    print("\nreport written to artifacts/recovery/diff-report.txt")


if __name__ == "__main__":
    sys.exit(main())
