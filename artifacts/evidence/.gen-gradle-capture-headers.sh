#!/usr/bin/env bash
# Prepends a provenance header to the raw Gradle captures, so every evidence file states the command
# that produced it and that command's exit code.
#
# Why this exists: T-022-red.txt was captured before this repo required that, so a reader could not
# tell how it was made. The header is added explicitly as a *later addition* — the run output below it
# is untouched — because stamping a fresh `Generated:` time over an old capture would be a fabricated
# provenance.
#
# Input contract: the run command writes to `<target>.raw`. This script turns that into `<target>`,
# with a header, and deletes the `.raw`. Files that already carry a header (T-022-red.txt) are
# re-headed in place. Idempotent: strip-and-add is one pass keyed on the marker, so re-running never
# stacks headers and never shifts the original bytes. (A naive `tail -c +4` prepend is NOT idempotent
# — it eats 3 bytes off the header on the second run.)
#
# Run from the repository root after the Gradle runs:
#   bash artifacts/evidence/.gen-gradle-capture-headers.sh
set -u

PY="C:/Users/23745/.workbuddy/binaries/python/versions/3.13.12/python.exe"

"$PY" - <<'PY'
import datetime, os

MARKER = '# ---------------------------------------------------------------------------\n'
BOM = b'\xef\xbb\xbf'
CMD = 'cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain'

# target -> (raw file or None when the target itself holds the capture, exit code, suite, note)
#
# Only the RED capture is listed. The two GREEN files used to be here too, but they are now produced
# by .gen-android-test-green.sh, which runs the suite and writes its own `Generated:` / `Exit code:`
# in the same pass. Leaving them in this SPEC would be actively harmful: this script prepends a
# "补记的来历头" that asserts the content below it does *not* come from the current run, which is
# the opposite of the truth for a fresh capture, and its strip-and-add is keyed on its own marker so
# it would not even recognise the new header to replace it.
SPEC = [
    ('artifacts/evidence/T-022-red.txt', None, '1',
     'T-022 的 13 例版本（交付版为 15 例；编译期失败成因完全相同，都是同一批待建 DAO）',
     '编译级 RED：javac 自报 66 个错误 = 程序包com.treelearn.data.db.dao不存在 ×3 + 找不到符号 ×63。'),
]

NOW = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')

for path, raw, code, suite, note in SPEC:
    src = raw or path
    if not os.path.exists(src):
        print(f'skip {path}: no capture at {src}')
        continue

    data = open(src, 'rb').read()
    has_bom = data.startswith(BOM)
    text = data[len(BOM):].decode('utf-8') if has_bom else data.decode('utf-8')

    # Drop a previously added header, so this is safe to run repeatedly.
    if text.startswith(MARKER):
        end = text.find('\n\n', len(MARKER))
        text = text[end + 2:]

    header = (
        MARKER
        + f'# 补记的来历头（{NOW}）——以下内容不是本次运行产生的，是对文件里那次运行的说明。\n'
        + f'# Command    : {CMD}\n'
        + f'# Working dir: D:/Project/Android/Treelearn\n'
        + f'# Exit code  : {code}（以文件末尾的 GRADLE_EXIT 为准，那是那次运行自己的记录）\n'
        + f'# 套件规模   : {suite}\n'
        + f'# 说明       : {note}\n'
        + '# 头以下全部是当时运行的原样输出，未做任何编辑。\n'
        + MARKER
        + '\n'
    )

    out = (BOM if has_bom else b'') + (header + text).encode('utf-8')
    open(path, 'wb').write(out)
    if raw and os.path.exists(raw):
        os.remove(raw)
    print(f'rewrote {path} ({len(out)} bytes){"" if not raw else " and removed the .raw"}')
PY
