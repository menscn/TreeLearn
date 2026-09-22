import os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
WIRING = os.path.join(ROOT, "android/app/src/main/java/com/treelearn/app/wiring")

BLOCK = re.compile(r"/\*.*?\*/", re.S)
LINE = re.compile(r"//.*$")

def count(path):
    text = open(path, encoding="utf-8").read()
    text = BLOCK.sub("", text)
    total = 0
    for raw in text.splitlines():
        s = LINE.sub("", raw).strip()
        if s:
            total += 1
    return total

groups = {}
files = sorted(f for f in os.listdir(WIRING) if f.endswith(".java"))
groups["wiring/ (all 14 files)"] = [os.path.join(WIRING, f) for f in files]
groups["wiring/ minus AndroidSettingsStore.java (T-012's file)"] = [
    os.path.join(WIRING, f) for f in files if f != "AndroidSettingsStore.java"]
groups["AndroidSettingsStore.java (T-012, not this task)"] = [os.path.join(WIRING, "AndroidSettingsStore.java")]
groups["RealDataPathWiringInstrumentedTest.java (test, not counted in the budget)"] = [
    os.path.join(ROOT, "android/app/src/androidTest/java/com/treelearn/app/RealDataPathWiringInstrumentedTest.java")]

for name in groups:
    paths = groups[name]
    per = [(os.path.basename(p), count(p)) for p in paths]
    print("%s -> %d non-blank non-comment lines in %d file(s)" % (name, sum(n for _, n in per), len(per)))
    for base, n in per:
        print("    %5d  %s" % (n, base))
    print()