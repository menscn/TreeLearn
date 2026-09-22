#!/usr/bin/env bash
# Generator for the GREEN instrumented-test evidence (T-004-green.txt, T-022-green.txt).
#
# These two files used to be produced by capturing the run and then attaching a *retroactive*
# provenance header (.gen-gradle-capture-headers.sh), because the original captures predated the
# repo's rule that evidence must state its own command and exit code. That workaround only ever
# applied to T-022-red.txt: it is still the honest way to describe an old capture. For GREEN the
# capture is cheap to redo, so this script runs the suite and writes the header *as part of the same
# run* — `Generated:` is the moment the run started, and `EXITCODE=` is that run's own exit code.
#
# One run, three files: they describe the same batch (12 T-023 + 15 T-022 + 24 T-004 + 1 T-002
# startup = 52 cases) and differ only in which suite the note foregrounds. Adding a file here rather
# than capturing a second run is deliberate: two runs would show two different `Generated:` moments
# for claims that are supposed to describe one build.
#
# Run from the repository root:
#   bash artifacts/evidence/.gen-android-test-green.sh
set -u

E="artifacts/evidence"
RAW="$E/.captures/android-test.raw"
mkdir -p "$E/.captures"

CMD='cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain'
DEVICE='Medium_Phone_API_36.1(AVD) - 16 (API 36)'

started="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
( cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain ) >"$RAW" 2>&1
status=$?

write_one() {
    local target="$1"
    local suite="$2"
    {
        printf 'Generated: %s\n' "$started"
        printf 'Command  : %s\n' "$CMD"
        printf 'Working dir: D:/Project/Android/Treelearn\n'
        printf 'Device   : %s\n' "$DEVICE"
        printf 'Exit code: %s（与文件末尾 EXITCODE 一致，取自同一次运行）\n' "$status"
        printf '套件规模 : %s\n' "$suite"
        printf '\n# 以下是本次运行的原样输出，未做任何编辑。\n\n'
        cat "$RAW"
        printf '\nEXITCODE=%s\n' "$status"
    } >"$target"
    echo "wrote $target (EXITCODE=$status)"
}

write_one "$E/T-022-green.txt" 'T-022 的 15 例 + T-004 的 24 例 + T-002 启动 1 例 = 40 例（本轮加入 T-023 后为 52 例，见 T-023-green.txt）'
write_one "$E/T-004-green.txt" 'T-004 的 24 例 + T-022 的 15 例 + T-002 启动 1 例 = 40 例（本轮加入 T-023 后为 52 例，见 T-023-green.txt）'
write_one "$E/T-023-green.txt" 'T-023 切片 A 的 12 例 + T-022 的 15 例 + T-004 的 24 例 + T-002 启动 1 例 = 52 例'

# The summary lines are read out of the capture rather than restated, so a reader can check this
# script's claim against the very text it just wrote.
echo '--- 本次运行的结论行（直接从捕获读出）---'
grep -E 'Starting [0-9]+ tests|Tests [0-9]+/[0-9]+ completed|Finished [0-9]+ tests|^BUILD ' "$RAW"
