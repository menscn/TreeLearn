#!/usr/bin/env bash
# Generator for T-023 slice A's remaining evidence:
#
#   T-023-guard.txt            Java-only guard, full mode
#   T-023-guard-selftest.txt   the guard's own positive/negative self-test
#   T-023-core-tests.txt       :core unit tests (--rerun), per-file counts appended separately
#   T-023-core-javadoc.txt     :core javadoc, because "tests green, javadoc red" has happened here
#   T-023-lint.txt             :app lintDebug
#   T-023-validate-tasks.txt   the task-contract mechanical validator
#   T-022-checks.txt           refreshed: the old file was a headerless capture with no command and
#                              no exit code, so it could not prove its own claim (see below)
#
# Every file gets its command and its own exit code written by this script at the moment it runs, and
# a command that fails still writes its file before this script stops, so a red gate leaves red
# evidence behind instead of nothing.
#
# Run from the repository root:
#   bash artifacts/evidence/.gen-t023-evidence.sh
set -u

E="artifacts/evidence"
PY="C:/Users/23745/.workbuddy/binaries/python/versions/3.13.12/python.exe"
GUARD="D:/Project/Android/Treelearn/android/scripts/no-kotlin-check.ps1"
SELFTEST="D:/Project/Android/Treelearn/android/scripts/dev/guard-selftest.ps1"

failures=0

run_to() {
    local out="$1"; shift
    {
        printf 'Generated: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
        printf 'Working dir: D:/Project/Android/Treelearn\n'
        printf 'Command  : %s\n\n' "$*"
    } >"$out"
    "$@" >>"$out" 2>&1
    local status=$?
    printf '\nEXITCODE=%s\n' "$status" >>"$out"
    echo "wrote $out (EXITCODE=$status)"
    [ "$status" -eq 0 ] || failures=$((failures + 1))
}

run_to "$E/T-023-guard.txt" \
    powershell -NoProfile -ExecutionPolicy Bypass -File "$GUARD"
run_to "$E/T-023-guard-selftest.txt" \
    powershell -NoProfile -ExecutionPolicy Bypass -File "$SELFTEST"

cd android || exit 1
# `--rerun`: an UP-TO-DATE line says nothing about how many tests exist, and the per-file counts that
# .gen-core-test-counts.sh appends are read from the XML this run writes.
run_to "../$E/T-023-core-tests.txt" ./gradlew :core:test --rerun --console=plain
run_to "../$E/T-023-core-javadoc.txt" ./gradlew :core:javadoc --rerun --console=plain
run_to "../$E/T-023-lint.txt" ./gradlew :app:lintDebug --console=plain
# Same batch the old T-022-checks.txt captured, regenerated with provenance: the previous file began
# directly with Gradle output, so a reader could not tell which command produced it or whether it
# passed. These two tasks are the only consumers, and both are listed here.
run_to "../$E/T-022-checks.txt" ./gradlew :app:lintDebug :core:test --console=plain
cd .. || exit 1

run_to "$E/T-023-validate-tasks.txt" "$PY" .trae/scripts/validate_tasks.py

# Per-file :core test counts, read out of the JUnit XML rather than the console log.
bash artifacts/evidence/.gen-core-test-counts.sh

echo "--- gates with a non-zero exit code: $failures ---"
