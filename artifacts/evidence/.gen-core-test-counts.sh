#!/usr/bin/env bash
# Appends a machine-derived summary of the :core unit-test results to the per-task evidence files.
#
# `:core:test` reports UP-TO-DATE when nothing in :core changed, and an UP-TO-DATE task line proves
# nothing about how many tests exist. So the summary is read out of the JUnit XML that the last run
# wrote, with the file list and each file's counts, plus the totals — never typed by hand.
#
# Run from the repository root:
#   bash artifacts/evidence/.gen-core-test-counts.sh
set -u

PY="C:/Users/23745/.workbuddy/binaries/python/versions/3.13.12/python.exe"
DIR="android/core/build/test-results/test"

[ -d "$DIR" ] || { echo "missing $DIR — run :core:test first" >&2; exit 1; }

SUMMARY="$("$PY" - "$DIR" <<'PY'
import glob, os, sys, xml.etree.ElementTree as ET
files = sorted(glob.glob(os.path.join(sys.argv[1], '*.xml')))
t = f = e = s = 0
rows = []
for p in files:
    r = ET.parse(p).getroot()
    a = (int(r.get('tests', 0)), int(r.get('failures', 0)), int(r.get('errors', 0)),
         int(r.get('skipped', 0)))
    t += a[0]; f += a[1]; e += a[2]; s += a[3]
    rows.append('  %-58s tests=%-4d failures=%d errors=%d skipped=%d'
                % (os.path.basename(p), a[0], a[1], a[2], a[3]))
print('SUMMARY files=%d tests=%d failures=%d errors=%d skipped=%d' % (len(files), t, f, e, s))
print('\n'.join(rows))
PY
)"

MARKER='--- per-file counts read from '

for task in T-004 T-022 T-023; do
    OUT="artifacts/evidence/${task}-core-tests.txt"
    [ -f "$OUT" ] || continue
    # Idempotent: drop any block this script appended before, so running it twice does not leave two
    # summaries that disagree the moment the suite changes.
    # `grep -e` is required: the marker starts with '-' and would otherwise be parsed as an option.
    if grep -qF -e "$MARKER" "$OUT"; then
        sed -i "/^${MARKER}/,\$d" "$OUT"
    fi
    {
        printf '\n%s%s (not from the console log) ---\n' "$MARKER" "$DIR"
        printf '%s\n' "$SUMMARY"
    } >>"$OUT"
    echo "appended core-test summary to $OUT"
done
