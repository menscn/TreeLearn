#!/usr/bin/env bash
# Generator for the per-task instrumented-test case lists.
#
# The counts and names in the generated files are read out of the JUnit XML that Gradle just wrote,
# never typed by hand — the failure library records a delivery claim whose counts did not match its
# own evidence file ("40 处 cannot find symbol" that was really 49).
#
# Run from the repository root, after `:app:connectedDebugAndroidTest`:
#   bash artifacts/evidence/.gen-android-test-cases.sh
set -u

XML="android/app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml"
PY="C:/Users/23745/.workbuddy/binaries/python/versions/3.13.12/python.exe"

if [ ! -f "$XML" ]; then
    echo "the JUnit XML is missing: $XML" >&2
    exit 1
fi

# Emits "<classname>\t<name>" for every testcase, so no attribute ordering is assumed.
"$PY" - "$XML" >/tmp/tl-cases.tsv <<'PY'
import sys, xml.etree.ElementTree as ET
for case in ET.parse(sys.argv[1]).getroot().iter('testcase'):
    print(case.get('classname') + '\t' + case.get('name'))
PY
STATUS=$?
echo "EXTRACT_EXITCODE=$STATUS"
[ "$STATUS" -eq 0 ] || exit "$STATUS"

emit_file() {
    # Split deliberately: `local a="$1" b="${a}-x"` expands every argument before `local` runs the
    # assignments, so under `set -u` the reference to `a` is unbound on the first call. This is a
    # shell-level trap, not a property of this script.
    local task="$1"
    local suite="$2"
    local out="artifacts/evidence/${task}-tests.txt"
    local own other
    own="$(awk -F'\t' -v c="$suite" '$1 == c' /tmp/tl-cases.tsv | cut -f2 | sort)"
    other="$(awk -F'\t' -v c="$suite" '$1 != c { print $2" ("$1")" }' /tmp/tl-cases.tsv | sort)"
    {
        printf 'Generated: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
        printf 'Command  : cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain\n'
        printf 'XML      : %s\n' "$XML"
        printf 'XML sha  : %s\n' "$(sha256sum "$XML" | cut -d' ' -f1)"
        printf '\n'
        grep -o '<testsuite[^>]*' "$XML" | head -1
        printf '\n'
        printf -- '--- %s cases (%s) ---\n' "$task" "$(printf '%s\n' "$own" | grep -c .)"
        printf '%s\n' "$own"
        printf '\n--- every other case in the same batch ---\n'
        printf '%s\n' "$other"
    } >"$out"
    echo "wrote $out ($(printf '%s\n' "$own" | grep -c .) cases)"
}

emit_file "T-004" "com.treelearn.app.SqliteSchemaInstrumentedTest"
emit_file "T-022" "com.treelearn.app.TopicBranchMessageDaoInstrumentedTest"
emit_file "T-023" "com.treelearn.app.AnswerRevisionGenerationOperationDaoInstrumentedTest"

echo '--- totals straight from the XML attribute ---'
grep -o 'tests="[0-9]*" failures="[0-9]*" errors="[0-9]*" skipped="[0-9]*"' "$XML" | head -1
