#!/usr/bin/env python3
"""T-037 fix round 2: reproduce the window-exemption hole in check_no_hardcoded_user_version.

THE PROPERTY UNDER TEST: the guard's verdict for a hardcoded `PRAGMA user_version` expectation must
not depend on WHERE in the file the violation sits. Pre-fix it did, because the exemption was read
from a +/-160-character window around the pragma: the SAME violation immediately after a legitimate
(constant-derived) assertion was exempted (exit 0 = fail-open), while the same violation far from any
allowed constant was caught (exit 1).

It builds two throwaway repo-shaped trees under the system temp directory (never in the repo):
  probe_a : the violation inserted directly after the legitimate line-1099 anchor
            rawInt(reopened, "PRAGMA user_version"));
  control : the SAME violation inserted after the SECOND assertPragmasAreClean(reopened); (line 1102)

It prints, for each tree, the distance from the violation to the nearest allowed constant (the only
thing that differs between the two cases) and the guard's own exit code. Exit code of THIS script:
  0  the two trees agree (the verdict does not depend on distance -- the fix holds, or a
     distance-independent breakage exists; read the printed codes)
  1  the two trees disagree = the fail-open hole is present
  2  the probe could not be built (anchor missing)

Usage:
  python -X utf8 artifacts/evidence/T-037-guard-window-probe.py                      # repo guard
  python -X utf8 artifacts/evidence/T-037-guard-window-probe.py --guard <other copy> # e.g. the
      frozen pre-fix copy kept at %TEMP%/t037-guard-probe/guard-prefix.py
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
DEFAULT_GUARD = os.path.join(REPO, ".trae", "scripts", "dev", "check_androidtest_schema_expectations.py")
SCHEMA_REL = os.path.join("android", "data", "src", "main", "java", "com", "treelearn", "data", "db",
                          "TreeLearnSchema.java")
SUITE_REL = os.path.join("android", "app", "src", "androidTest", "java", "com", "treelearn", "app",
                         "SqliteSchemaInstrumentedTest.java")
ALLOWED = ("TreeLearnSchema.DATABASE_VERSION", "V1_SCHEMA_VERSION")
TREE_BASE = os.path.join(tempfile.gettempdir(), "t037-guard-probe", "trees")

INJECT_AFTER = 'rawInt(reopened, "PRAGMA user_version"));'
COMPANION = "assertPragmasAreClean(reopened);"
MARK = {"probe_a": '"adjacent hardcoded expectation"', "control": '"far hardcoded expectation"'}
VIOLATION = {
    "probe_a": 'assertEquals("adjacent hardcoded expectation", 7, rawInt(reopened, "PRAGMA user_version"));',
    "control": 'assertEquals("far hardcoded expectation", 7, rawInt(reopened, "PRAGMA user_version"));',
}


def build(destination):
    for relative in (SCHEMA_REL, SUITE_REL):
        target = os.path.join(destination, relative)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(os.path.join(REPO, relative), target)
    return os.path.join(destination, SUITE_REL)


def flatten(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    return re.sub(r"\s+", " ", text)


def inject(name, text):
    if name == "probe_a":
        if text.count(INJECT_AFTER) != 1:
            raise SystemExit("ANCHOR NOT UNIQUE: %d" % text.count(INJECT_AFTER))
        return text.replace(INJECT_AFTER, INJECT_AFTER + VIOLATION[name], 1)
    positions = [match.start() for match in re.finditer(re.escape(COMPANION), text)]
    if len(positions) != 2:
        raise SystemExit("COMPANION ANCHOR COUNT: %d" % len(positions))
    at = positions[-1] + len(COMPANION)
    return text[:at] + VIOLATION[name] + text[at:]


def nearest_allowed(text, mark):
    flat = flatten(text)
    matches = [m for m in re.finditer(r"PRAGMA user_version", flat)
               if mark in flat[max(0, m.start() - 120):m.start()]]
    if len(matches) != 1:
        raise SystemExit("INJECTED OCCURRENCE NOT UNIQUE: %d" % len(matches))
    start = matches[0].start()
    hits = [h.start() for name in ALLOWED for h in re.finditer(re.escape(name), flat)]
    return start, min(abs(start - hit) for hit in hits)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--guard", default=DEFAULT_GUARD)
    args = parser.parse_args()

    if os.path.isdir(TREE_BASE):
        shutil.rmtree(TREE_BASE)
    print("guard under test   : %s" % args.guard)
    print("guard sha256       : %s" % __import__("hashlib").sha256(
        open(args.guard, "rb").read()).hexdigest())
    print("throwaway trees at : %s   (outside the repo)" % TREE_BASE)
    codes = {}
    for name in ("probe_a", "control"):
        suite = build(os.path.join(TREE_BASE, name))
        text = inject(name, open(suite, "r", encoding="utf-8").read())
        with open(suite, "w", encoding="utf-8", newline="") as handle:
            handle.write(text)
        offset, distance = nearest_allowed(text, MARK[name])
        result = subprocess.run([sys.executable, "-X", "utf8", args.guard, "--root",
                                 os.path.join(TREE_BASE, name)], capture_output=True, text=True)
        codes[name] = result.returncode
        verdict = [line for line in result.stdout.splitlines() if line.startswith("VERDICT")]
        print("%-8s violation offset=%d  nearest allowed constant=%d chars  guard_exit=%d  %s"
              % (name, offset, distance, result.returncode,
                 verdict[0] if verdict else "<no verdict line>"))
    agree = codes["probe_a"] == codes["control"]
    print("SAME_VERDICT=%s  (probe_a=%d control=%d)" % (agree, codes["probe_a"], codes["control"]))
    if not agree:
        print("FAIL-OPEN: the SAME violation is exempted when it sits near an allowed constant")
        return 1
    print("OK: the verdict does not depend on where the violation sits")
    return 0


if __name__ == "__main__":
    sys.exit(main())