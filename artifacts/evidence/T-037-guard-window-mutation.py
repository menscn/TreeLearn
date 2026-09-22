#!/usr/bin/env python3
"""T-037 fix round 2: mutation test for .trae/scripts/dev/check_androidtest_schema_expectations.py.

A self-test case proves the guard can fail on a violation; it does NOT prove the NEW judgement is
load-bearing. This does:

  M1  force the per-assertion judgement to always ACCEPT -> the hardcoded-literal cases must break.
  M2  force the per-assertion judgement to always REJECT -> the constant-derived cases must break.

The mutated text is written over the guard itself (the guard's self-test builds its throwaway trees
from its own REPO_ROOT, so a copy outside the repo would lose its inputs and turn every case into a
false INCONCLUSIVE) and the original bytes are restored in a finally, with a byte-identity check.
"""
import hashlib
import os
import re
import subprocess
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
GUARD = os.path.join(REPO, ".trae", "scripts", "dev", "check_androidtest_schema_expectations.py")
ANCHOR = "    return _strip_outer_parens(expected) in ALLOWED_USER_VERSION_EXPRESSIONS"
MUTATIONS = (("M1 judgement always accepts", "    return True"),
             ("M2 judgement always rejects", "    return False"))


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def read(path):
    with open(path, "r", encoding="utf-8", newline="") as handle:
        return handle.read()


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(text)


def main():
    original = read(GUARD)
    print("guard        : %s" % GUARD)
    print("anchor count : %d" % len(re.findall(re.escape(ANCHOR), original)))
    print("original sha : %s" % sha(original))
    if len(re.findall(re.escape(ANCHOR), original)) != 1:
        print("MUTATION BROKEN: the judgement line is not uniquely identifiable")
        return 2
    try:
        for name, replacement in MUTATIONS:
            mutated = original.replace(ANCHOR, replacement, 1)
            write(GUARD, mutated)
            result = subprocess.run([sys.executable, "-X", "utf8", GUARD, "--selftest"],
                                    capture_output=True, text=True)
            print("--- %s (mutated sha %s) ---" % (name, sha(mutated)[:12]))
            for line in (result.stdout or "").splitlines():
                if "BROKEN" in line or line.startswith("OVERALL"):
                    print("    %s" % line.rstrip())
            print("    MUTATED_EXITCODE=%d" % result.returncode)
    finally:
        write(GUARD, original)
    restored = read(GUARD)
    print("restored sha : %s  byte-identical=%s" % (sha(restored), restored == original))
    return 0 if restored == original else 1


if __name__ == "__main__":
    sys.exit(main())