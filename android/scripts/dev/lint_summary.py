"""Dev helper: print a compact summary of the AGP lint XML report.

Usage:
    python android/scripts/dev/lint_summary.py [path/to/lint-results-debug.xml]

Kept in the repo because every implementation task has to look at lint findings
and the raw XML is unreadable. Read-only; writes nothing.

The default path is resolved from this file's own location, not from the current
working directory. The documented usage is to run it from the repository root,
where a CWD-relative "app/build/reports/..." does not exist -- so the original
default reported "no lint report" for the one invocation the docstring told you
to use.
"""

import os
import sys
import xml.etree.ElementTree as ET
from collections import Counter


def main() -> int:
    if len(sys.argv) > 1:
        path = sys.argv[1]
    else:
        android_root = os.path.dirname(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        path = os.path.join(
            android_root, "app", "build", "reports", "lint-results-debug.xml")
    if not os.path.exists(path):
        print("no lint report at %s" % path)
        return 1

    root = ET.parse(path).getroot()
    issues = root.findall("issue")
    counts = Counter()
    for issue in issues:
        severity = issue.get("severity") or "?"
        counts[(issue.get("id") or "?", severity)] += 1
        loc = issue.find("location")
        where = ""
        line = ""
        if loc is not None:
            where = os.path.basename(loc.get("file") or "")
            line = loc.get("line") or ""
        message = (issue.get("message") or "").replace("\n", " ")
        print("  %-24s %-8s %s:%s  %s" % (issue.get("id"), severity, where, line, message[:96]))

    print()
    print("  total=%d" % len(issues))
    by_severity = Counter(s for (_, s) in counts)
    print("  by severity: %s" % dict(by_severity))
    for (issue_id, severity), n in sorted(counts.items()):
        print("    %-24s %-8s x%d" % (issue_id, severity, n))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
