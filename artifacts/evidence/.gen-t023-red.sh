#!/usr/bin/env bash
# Generator for T-023 slice A's RED evidence (artifacts/evidence/T-023-red.txt).
#
# RED here is *compile-level*, and that is stated rather than dressed up: the acceptance suite is
# written before any production type exists, so javac stops on the names this task has to create. It
# proves the capability is absent; it does not prove "a specific behaviour was observed and then
# fixed". The GREEN run uses a command that is character-for-character identical, which is what the
# task contract requires.
#
# The conclusion section is derived from the capture by this script — never typed by hand — because
# a hand-written count in a conclusion block has already drifted from the runs it described twice in
# this repository (see the failure library in AGENTS.md).
#
# Run from the repository root:
#   bash artifacts/evidence/.gen-t023-red.sh
set -u

E="artifacts/evidence"
RAW="$E/.captures/t023-red.raw"
OUT="$E/T-023-red.txt"
mkdir -p "$E/.captures"

CMD='cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain'
DEVICE='Medium_Phone_API_36.1(AVD) - 16 (API 36)'

started="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
( cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain ) >"$RAW" 2>&1
status=$?

# --- derivations, all read out of $RAW ------------------------------------------------------------
# javac prints its diagnostics twice: once inline and once re-indented under Gradle's FAILURE block.
# Anchoring on a top-level drive letter keeps the counts free of that duplicate.
count_of() { grep -cE "$2" "$1" 2>/dev/null || true; }

# Fail-closed encoding gate for everything this script writes. A mojibake evidence file still has the
# right name and a plausible size, so only a real decode attempt catches it.
assert_utf8() {
    local file
    for file in "$@"; do
        if ! iconv -f UTF-8 -t UTF-8 "$file" >/dev/null 2>&1; then
            echo "ENCODING GUARD FAILED: ${file} is not valid UTF-8" >&2
            return 1
        fi
    done
    echo "ENCODING GUARD PASS: $# file(s) decode as UTF-8"
}
total_reported="$(grep -oE '[0-9]+ 个错误' "$RAW" | head -n 1 | grep -oE '[0-9]+' || true)"
unresolved="$(count_of "$RAW" '^[A-Z]:.*错误: 找不到符号')"
missing_package="$(count_of "$RAW" '^[A-Z]:.*错误: 程序包[^ ]+不存在')"
symbols="$(grep -oE '^ +符号: .*' "$RAW" | sed -E 's/^ +//' | sort | uniq -c | sort -rn || true)"
# Joined with a comma and then substituted, not via `paste -sd'、'` — paste keeps only the first byte
# of a multibyte delimiter and drops the rest, which emits invalid UTF-8. This run happens to name a
# single file (so no separator was inserted and the bug stayed invisible), but the next RED with two
# files would have written a corrupted evidence file.
files="$(grep -oE '^[A-Z]:[^:]*\.java' "$RAW" | sed -E 's#^.*/##' | sort -u | tr '\n' ',' | sed -e 's/,$//' -e 's/,/、/g' || true)"

{
    printf 'Generated: %s\n' "$started"
    printf 'Command  : %s\n' "$CMD"
    printf 'Working dir: D:/Project/Android/Treelearn\n'
    printf 'Device   : %s\n' "$DEVICE"
    printf 'Exit code: %s\n' "$status"
    printf 'RED 类型 : 编译级（用例写在任何生产类型之前；javac 停在待建类型上）\n'
    printf '\n# 以下是本次运行的原样输出，未做任何编辑。\n\n'
    cat "$RAW"
    printf '\nEXITCODE=%s\n' "$status"
    printf '\n=== 结论（本脚本从上面的捕获机械派生，数字非手写）===\n'
    printf 'javac 自报错误总数      : %s\n' "${total_reported:-未读到}"
    printf '"找不到符号" 顶格行数  : %s\n' "${unresolved:-0}"
    printf '"程序包…不存在" 顶格行数: %s\n' "${missing_package:-0}"
    printf '报错文件                : %s\n' "${files:-未读到}"
    printf '缺失符号清单（符号: 行的去重计数）:\n%s\n' "${symbols:-未读到}"
    printf '\n复现上面数字的命令：\n'
    printf '  grep -oE "[0-9]+ 个错误" %s | head -n 1\n' "$RAW"
    printf '  grep -cE "^[A-Z]:.*错误: 找不到符号" %s\n' "$RAW"
    printf '  grep -oE "^ +符号: .*" %s | sed -E "s/^ +//" | sort | uniq -c | sort -rn\n' "$RAW"
} >"$OUT"

assert_utf8 "$OUT"
echo "wrote $OUT (EXITCODE=$status, javac reported ${total_reported:-?} error(s))"
echo '--- 缺失符号（去重）---'
printf '%s\n' "${symbols:-未读到}"
