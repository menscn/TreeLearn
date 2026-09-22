"""Count the JVM unit tests that actually ran, straight from Gradle's JUnit XML.

Why this exists: a bare "N tests" number is ambiguous for this repo. `app`, `ai` and
`data` are Android modules, so `test` runs BOTH `testDebugUnitTest` and
`testReleaseUnitTest` over the same classes; `core` and `tree-layout` are plain JVM
modules with a single `test` task. Reporting one number without saying which reading
it is has already produced two irreconcilable figures in the delivery report, so this
script always prints both.

Default root is resolved from this file's own location, NOT the current directory, so
`python .trae/scripts/dev/count_jvm_tests.py` works from anywhere in the repo
(see the AGENTS.md failure case "dev scripts resolve default paths against the CWD").

Usage:
    python -X utf8 .trae/scripts/dev/count_jvm_tests.py [repo_root]

Exit code: 0 when at least one test suite was found; 1 when the match set is empty
(an empty result must never read as "all green").
"""
import os
import re
import sys

MODULES = ["app", "core", "ai", "data", "tree-layout"]
HEADER_BYTES = 4000


def attribute(text, name):
    """Read one integer attribute from the <testsuite ...> header."""
    match = re.search(r"\b" + name + r"=\"(\d+)\"", text)
    return int(match.group(1)) if match else None


def main(argv):
    default_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
    root = argv[1] if len(argv) > 1 else default_root
    android = os.path.join(root, "android")
    grand = {"suites": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    per_module_one_variant = {}
    for module in MODULES:
        results = os.path.join(android, module, "build", "test-results")
        if not os.path.isdir(results):
            print("%-12s %-22s MISSING" % (module, "-"))
            continue
        variants = [v for v in sorted(os.listdir(results)) if os.path.isdir(os.path.join(results, v))]
        counts = []
        for variant in variants:
            directory = os.path.join(results, variant)
            suites = tests = failures = errors = skipped = 0
            for name in sorted(os.listdir(directory)):
                if not (name.startswith("TEST-") and name.endswith(".xml")):
                    continue
                header = open(os.path.join(directory, name), encoding="utf-8", errors="replace").read(HEADER_BYTES)
                total = attribute(header, "tests")
                if total is None:
                    print("  !! no tests attribute, not a JUnit report:", variant, name)
                    continue
                suites += 1
                tests += total
                failures += attribute(header, "failures") or 0
                errors += attribute(header, "errors") or 0
                skipped += attribute(header, "skipped") or 0
            print("%-12s %-22s suites=%3d tests=%4d failures=%d errors=%d skipped=%d"
                  % (module, variant, suites, tests, failures, errors, skipped))
            counts.append(tests)
            grand["suites"] += suites
            grand["tests"] += tests
            grand["failures"] += failures
            grand["errors"] += errors
            grand["skipped"] += skipped
        per_module_one_variant[module] = counts[0] if counts else 0
    print("")
    print("reading A - one variant per module: %s -> total %d"
          % (", ".join("%s=%d" % kv for kv in per_module_one_variant.items()),
             sum(per_module_one_variant.values())))
    print("reading B - every variant counted: suites=%d tests=%d failures=%d errors=%d skipped=%d"
          % (grand["suites"], grand["tests"], grand["failures"], grand["errors"], grand["skipped"]))
    if grand["suites"] == 0:
        print("VERDICT: FAIL - no JUnit report found under android/*/build/test-results; run the tests first")
        return 1
    print("VERDICT: OK - %d suite(s) counted" % grand["suites"])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
