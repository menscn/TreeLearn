#!/usr/bin/env bash
# Regenerates every guard / self-test / task-contract evidence file for T-004 and T-022 after the
# anchor-CHECK change. Each file records the command that produced it and that command's exit code,
# because the failure library has a case where the evidence file could not prove its own claim.
#
# Run from the repository root:
#   bash artifacts/evidence/.gen-guard-evidence.sh
set -u

E="artifacts/evidence"
PY="C:/Users/23745/.workbuddy/binaries/python/versions/3.13.12/python.exe"

GUARD="D:/Project/Android/Treelearn/android/scripts/no-kotlin-check.ps1"
SELFTEST="D:/Project/Android/Treelearn/android/scripts/dev/guard-selftest.ps1"

run_to() {
    local out="$1"; shift
    {
        printf 'Generated: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
        printf 'Command  : %s\n\n' "$*"
    } >"$out"
    "$@" >>"$out" 2>&1
    local status=$?
    printf '\nEXITCODE=%s\n' "$status" >>"$out"
    echo "wrote $out (EXITCODE=$status)"
}

for task in T-004 T-022; do
    run_to "$E/${task}-guard.txt" \
        powershell -NoProfile -ExecutionPolicy Bypass -File "$GUARD"
    run_to "$E/${task}-guard-selftest.txt" \
        powershell -NoProfile -ExecutionPolicy Bypass -File "$SELFTEST"
done

cd android || exit 1
# `--rerun` is not cosmetic: without it Gradle prints "> Task :core:test UP-TO-DATE" whenever nothing
# in :core changed, and an UP-TO-DATE line proves nothing about how many tests exist. The per-file
# counts are then appended from the JUnit XML by .gen-core-test-counts.sh.
run_to "../$E/T-004-core-tests.txt" ./gradlew :core:test --rerun --console=plain
run_to "../$E/T-022-core-tests.txt" ./gradlew :core:test --rerun --console=plain
run_to "../$E/T-022-core-javadoc.txt" ./gradlew :core:javadoc --rerun --console=plain
cd .. || exit 1

run_to "$E/T-004-validate-tasks.txt" \
    "$PY" .trae/scripts/validate_tasks.py
run_to "$E/T-022-validate-tasks.txt" \
    "$PY" .trae/scripts/validate_tasks.py
