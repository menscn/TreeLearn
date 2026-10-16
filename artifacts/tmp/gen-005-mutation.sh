#!/usr/bin/env bash
# T-2026-10-15-005 mutation evidence generator (one-off; lives in artifacts/tmp by convention).
# Writes artifacts/evidence/T-2026-10-15-005-mutation.txt.
# 自检的绿只证明「当前实现下用例通过」，不证明「守卫消失时该用例转红」——后者才是本片两条地板
# （MIN_SCREENS / MIN_SURFACES）、分类判据（自绘 / sw600dp / 确定性）与两道 fail-closed 分支承重的
# 部分（既有的「断言可证伪必须做变异测试」规则）。因此这里把守卫逐条人为退化，看它自己的目标用例
# 是否转红，并要求红得只有那么多：多红一条说明退化动到了别处，少红一条说明那条守卫从未承重。
# 做法约束（沿用 AGENTS.md）：变异跑在 artifacts/tmp/_m005/prod 的**副本**上，与生产文件同深度，
# 因此按自身位置上溯算仓库根的脚本会算到副本根；副本留在仓库内，git rev-parse 才仍然可用。
# 每条判据自带命令行与自己的退出码；总退出码由 want/got 表派生，不写死 0；每块带内容见证；
# MIN_BLOCKS 是与本文件自身源码比对的字面整数；基线红/绿由块 02 实测写出、块 03 现场读取，
# 不假设「基线必然是绿的」；生产字节全程只读（块 04 用另一双眼睛按指纹复算）。
set -uo pipefail

EV=artifacts/evidence/T-2026-10-15-005-mutation.txt
TMP=artifacts/tmp/_m005g
COPY=artifacts/tmp/_m005/prod
GEN=.trae/scripts/dev/gen_screen_inventory.py
SELF=.trae/scripts/dev/screen_inventory_selftest.py
SPEC=Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md
mkdir -p "$TMP"
rm -f "$EV" "$TMP/console.txt"

if [ ! -d .git ]; then
  echo "FATAL: run this script from the repository root (cwd=$(pwd))"
  exit 3
fi
for f in "$GEN" "$SELF" "$SPEC"; do
  if [ ! -f "$f" ]; then
    echo "FATAL: $f is missing; this mutation battery would judge nothing"
    exit 3
  fi
done

MIN_BLOCKS=8
if ! grep -Eq '^MIN_BLOCKS=[0-9]+[[:space:]]*$' "$0"; then
  echo "FLOOR FAULT: MIN_BLOCKS is not a bare integer literal in this script's own source"
  exit 3
fi
if [ "$((MIN_BLOCKS - 1))" -ge "$MIN_BLOCKS" ]; then
  echo "FLOOR SELFTEST FAULT: the floor accepts its own value minus one"
  exit 3
fi

FP='import hashlib, sys
for p in sys.argv[1:]:
    try:
        b = open(p, "rb").read()
    except OSError as exc:
        print("%s  UNREADABLE %s" % (p, exc)); continue
    print("%s  bytes=%d  lines=%d  sha256=%s" % (p, len(b), b.count(bytes([10])), hashlib.sha256(b).hexdigest()))'
PRE="$TMP/prod-before.txt"
python -X utf8 -c "$FP" "$GEN" "$SELF" > "$PRE" 2>&1
pre_rc=$?

{
  echo "# T-2026-10-15-005 MUTATION - 屏幕清单生成器每条守卫的退化实测"
  echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)  (UTC)"
  echo "# Generator: artifacts/tmp/gen-005-mutation.sh   重跑：bash artifacts/tmp/gen-005-mutation.sh"
  echo "# 变异对象：gen_screen_inventory.py 与 screen_inventory_selftest.py 的**副本**"
  echo "#   （artifacts/tmp/_m005/prod/ 下同名同深度路径）；生产字节只在块 05 被读，从不被写。"
  echo "# 判据面：M1/M2 两条地板，M3 根布局判据，M4 平板变体列，M5 确定性，M6 方法体不闭合的"
  echo "#   fail-closed 分支，M7 隐藏 include 的消费方证明，M8 是不改变任何判定的注释型空变异——它必须"
  echo "#   仍然全绿，否则「任何改动都会红」的电池和「守卫消失才红」的电池读起来一模一样。"
  echo "#   M9/M10/M11 锁本轮新增的三条判据（两种浮层模式、注释行排除、无布局浮层不就近取布局）。"
  echo "#   它们要挡的缺陷不是崩溃而是「清单少了几行且没人知道」——这种失败在清单内部永远看不见。"
  echo "# 死 include 那道守卫（raise Fault）的退化**不能**记成用例转红：去掉它之后生成器在同一份夹具上"
  echo "#   抛出未捕获的 KeyError，自检因此按「仪器崩了」那一层中止，而不是某条判据不成立。块 04 把这种"
  echo "#   「移除后既不静默放行、也无法归因到一条用例」的后果单独量测，所以它不进电池（电池的规则是"
  echo "#   变异只许让用例转红，仪器故障必须另说）。"
  echo "# 生产文件指纹（判定前读数；块 05 在全部变异之后复核是否仍相同）："
  sed 's/^/#   /' "$PRE"
  if [ "$pre_rc" != "0" ]; then
    echo "# FINGERPRINT PRE-RUN rc=${pre_rc} - not every file was readable"
  fi
} > "$EV" 2> "$TMP/header.err"
hdr_err=$(wc -c < "$TMP/header.err" | tr -d ' ')
echo "header stderr bytes=${hdr_err}" | tee -a "$TMP/console.txt"
if [ "$hdr_err" != "0" ]; then
  echo "INSTRUMENT FAULT: header write produced stderr; evidence text would be silently short" | tee -a "$TMP/console.txt"
  exit 3
fi

# 生产面快照：块 05 判的是「本次运行期间没有动过生产面」，不是「整棵工作树的生产面是干净的」——
# 后者会把别片尚未提交的改动（实测本次有 frontend/src/styles/tokens.css，属 004）算成本片的缺陷。
SNAP="$TMP/surfaces-before.txt"
python -X utf8 -c "
import subprocess, sys
paths = set()
for c in (['git', '-c', 'core.quotepath=false', 'diff', '--name-only', 'HEAD'],
          ['git', '-c', 'core.quotepath=false', 'ls-files', '--others', '--exclude-standard']):
    r = subprocess.run(c, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.returncode != 0:
        print('SNAPSHOT FAULT'); sys.exit(3)
    for l in (r.stdout or '').splitlines():
        p = l.strip().strip('\"')
        if p.startswith(('android/', 'backend/', 'frontend/')):
            paths.add(p)
for p in sorted(paths):
    print(p)
" > "$SNAP" 2>&1
snap_rc=$?
if [ "$snap_rc" != "0" ]; then
  echo "INSTRUMENT FAULT: the production-surface snapshot did not complete (rc=${snap_rc})" | tee -a "$TMP/console.txt"
  exit 3
fi

declare -A EXPECT GOT LABELS
BLOCKS=0
bad=0

run() {
  local n=$1 want=$2 label=$3
  shift 3
  BLOCKS=$((BLOCKS + 1))
  EXPECT[$n]=$want
  LABELS[$n]=$label
  local want_text="${WANT_TEXT:-}"
  WANT_TEXT=""
  {
    echo ""
    echo "===== block ${n} - ${label} ====="
    echo "\$ $*"
  } >> "$EV"
  local out rc
  out=$("$@" 2>&1)
  rc=$?
  local cr_in
  cr_in=$(printf '%s' "$out" | tr -cd '\r' | wc -c)
  if [ "$cr_in" != "0" ]; then
    out=$(printf '%s' "$out" | tr -d '\r')
    echo "captured CR normalised: ${cr_in} carriage return(s) removed from block ${n} output - the artefact of record is LF-only" >> "$EV"
  fi
  local full="$TMP/block-$n.out"
  printf '%s\n' "$out" > "$full"
  printf '%s\n' "$out" | tail -n 34 >> "$EV"
  GOT[$n]=$rc
  echo "----- exit code: ${rc} (want ${want}) -----" >> "$EV"
  if [ "$rc" != "$want" ]; then
    bad=$((bad + 1))
    echo "UNEXPECTED rc for block ${n}: got ${rc} want ${want} (${label})" | tee -a "$TMP/console.txt"
  fi
  if [ -n "$want_text" ]; then
    local t miss=""
    local IFS='|'
    for t in $want_text; do
      [ -z "$t" ] && continue
      grep -qF -- "$t" "$full" || miss="${miss} [${t}]"
    done
    if [ -n "$miss" ]; then
      bad=$((bad + 1))
      echo "MISSING CONTENT WITNESS for block ${n}:${miss} (${label})" | tee -a "$TMP/console.txt"
    else
      echo "content witness ok for block ${n}" | tee -a "$TMP/console.txt"
    fi
  fi
}

# ---- block 01: build the copy tree ----------------------------------------------------------
cat > "$TMP/mkcopy.py" <<'PY'
"""同深度副本：把两份被判仪器与被真实树用例读到的最小输入拷进 artifacts/tmp/_m005/prod/。

段数必须与生产路径相同，否则被测脚本按自身位置上溯算出的仓库根会跳出副本，变异就落在别处。
"""
import hashlib
import os
import shutil
import sys

REPO = os.path.abspath(".")
COPY = os.path.join("artifacts", "tmp", "_m005", "prod")
DEV = os.path.join(".trae", "scripts", "dev")
FILES = [os.path.join(DEV, "gen_screen_inventory.py"),
         os.path.join(DEV, "screen_inventory_selftest.py"),
         "Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md"]
TREES = ["android/app/src/main/java", "android/app/src/main/res"]


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def cp(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(src, dst)


bad = 0
if os.path.isdir(COPY):
    shutil.rmtree(COPY)
n = 0
for rel in FILES:
    src = os.path.join(REPO, rel)
    if not os.path.isfile(src):
        print("FAIL a required copy source is missing: %s" % rel)
        bad = 1
        continue
    cp(src, os.path.join(COPY, rel))
    n += 1
for base in TREES:
    for root, _d, fs in os.walk(os.path.join(REPO, base)):
        for f in fs:
            src = os.path.join(root, f)
            cp(src, os.path.join(COPY, os.path.relpath(src, REPO)))
            n += 1
print("copy files=%d under %s" % (n, COPY.replace(os.sep, "/")))

mismatch = []
for rel in FILES:
    a, b = os.path.join(REPO, rel), os.path.join(COPY, rel)
    if os.path.isfile(b) and sha(a) != sha(b):
        mismatch.append(rel)
print("byte-identical single files=%d mismatches=%d" % (len(FILES), len(mismatch)))
if mismatch:
    print("FAIL copied bytes differ from production: %s" % mismatch)
    bad = 1

for rel in (os.path.join(DEV, "gen_screen_inventory.py"),
            os.path.join(DEV, "screen_inventory_selftest.py")):
    p_now = len(os.path.relpath(rel, REPO).split(os.sep))
    p_copy = len(os.path.relpath(os.path.join(COPY, rel), COPY).split(os.sep))
    print("same-depth %s prod=%d copy=%d" % (rel.replace(os.sep, "/"), p_now, p_copy))
    if p_now != p_copy:
        bad = 1

if not COPY.startswith(os.path.join("artifacts", "tmp")):
    print("FAIL the copy must live under artifacts/tmp so it can never be mistaken for a production path")
    bad = 1
print("COPY ok" if not bad else "COPY FAULT")
sys.exit(3 if n == 0 else (1 if bad else 0))
PY
WANT_TEXT="copy files=|byte-identical single files=3 mismatches=0|same-depth .trae/scripts/dev/gen_screen_inventory.py prod=4 copy=4|COPY ok"
run 1 0 "copy tree: same-depth copy under artifacts/tmp, every copied byte equal to production" \
  python -X utf8 "$TMP/mkcopy.py"

# ---- block 02: the baseline must be green, and its red set must be measured -----------------
cat > "$TMP/pristine.py" <<'PY'
"""基线红不红是**实测**出来的，并写进 baseline.json 给块 03 读；不假设「副本必然全绿」。
若基线已有用例在红，变异就只能报「无法归因」而不是把别人的红算成本次退化的功劳。"""
import json
import os
import re
import subprocess
import sys

COPY = os.path.join("artifacts", "tmp", "_m005", "prod")
SELF = os.path.join(COPY, ".trae", "scripts", "dev", "screen_inventory_selftest.py")
OUT = os.path.join("artifacts", "tmp", "_m005g", "baseline.json")

r = subprocess.run([sys.executable, "-X", "utf8", SELF],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
out = (r.stdout or "") + (r.stderr or "")
counts = re.search(r"cases executed=(\d+) expected=(\d+) failures=(\d+)", out)
if not counts:
    print("BASELINE FAULT: the copy suite printed no counting line at all")
    print(out[-800:])
    sys.exit(3)
executed, expected, failures = (int(x) for x in counts.groups())
failing = sorted({m.group(1) for m in re.finditer(r"^CASE (\S+)\s+rc=\d+ want=\d+ FAIL", out, re.M)})
faults = len([l for l in out.splitlines() if l.startswith("INSTRUMENT FAULT")])
print("BASELINE rc=%d executed=%d expected=%d failures=%d instrument-fault-lines=%d"
      % (r.returncode, executed, expected, failures, faults))
print("BASELINE failing-cases=%s" % failing)
bad = 0
if r.returncode != 0 or failures or executed != expected or failing:
    print("FAIL the copy baseline is red before any mutation - a battery on a red baseline cannot"
          " attribute any new red to the mutation, so this proves nothing until this is fixed")
    bad = 1
if faults:
    print("FAIL the copy suite reports an instrument fault; the copy is not usable as a fixture")
    bad = 1
json.dump({"rc": r.returncode, "executed": executed, "expected": expected,
           "failures": failures, "failing": failing}, open(OUT, "w", encoding="utf-8"))
print("BASELINE written=%s" % OUT.replace(os.sep, "/"))
print("BASELINE ok" if not bad else "BASELINE FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="BASELINE rc=0 executed=|expected=|failures=0|instrument-fault-lines=0|BASELINE failing-cases=[]|BASELINE ok"
run 2 0 "measured baseline: the pristine copy suite is green and its failing-case set is recorded for block 03" \
  python -X utf8 "$TMP/pristine.py"

# ---- block 03: the battery ------------------------------------------------------------------
cat > "$TMP/battery.py" <<'PY'
"""逐条把守卫人为退化，看它自己的目标用例是否转红，且红得只有那么多。

期望集合是**声明的字面量**（下方 MUTS），实测读数逐条打出来与它比对；基线红集从块 02 的实测文件读，
不写死空集。最后一条是不改变任何判定的注释变异，它必须仍然全绿——否则电池是在对「任何改动」起反应，
而不是对「守卫消失」起反应。M6 把 raise Fault 换成同形的 print(...)：语句仍然闭合、括号仍然配平，
变的只有「退化输入不再大声失败」这一件事。M3 一次让四条判据转红是实测结果而不是猜测——把根布局判据
反向之后，所有依赖「这台屏有根布局」的读数一起失效，期望集按实测如实登记（少登记一条，电池就把
「红多了」当成归因失败；多登记一条，就把没承重的守卫算成承重）。
"""
import json
import os
import re
import subprocess
import sys

REPO = os.path.abspath(".")
COPY = os.path.join("artifacts", "tmp", "_m005", "prod")
DEV = os.path.join(".trae", "scripts", "dev")
GEN = os.path.join(DEV, "gen_screen_inventory.py")
SELF = os.path.join(DEV, "screen_inventory_selftest.py")
BASELINE = os.path.join("artifacts", "tmp", "_m005g", "baseline.json")

# (name, file, old, new, target case, extra cases that are expected to go red too)
MUTS = [
    ("M1-screen-floor-zero", GEN, b"MIN_SCREENS = 8", b"MIN_SCREENS = 0",
     "neg-screens-below-floor", []),
    ("M2-surface-floor-zero", GEN, b"MIN_SURFACES = 3", b"MIN_SURFACES = 0",
     "neg-surfaces-below-floor", []),
    ("M3-root-inflate-condition", GEN,
     b"this\\s*,\\s*true\\s*", b"this\\s*,\\s*false\\s*",
     "pos-self-drawn", ["pos-hidden-include-proven", "pos-renamed-layout-ref-diff",
                        "pos-sw600dp-variant"]),
    ("M4-sw600dp-column-off", GEN,
     b'"sw600dp": bool(root_layout) and\n'
     b'                        "layout-sw600dp" in census.get(root_layout, {}).get("dirs", []),',
     b'"sw600dp": False,',
     "pos-sw600dp-variant", []),
    ("M5-volatile-commit-stamp", GEN,
     b'    inv["source_commit"] = commit',
     b'    import time\n    inv["source_commit"] = "%s-%d" % (commit, time.monotonic_ns())',
     "pos-determinism", []),
    ("M6-unbalanced-body-not-a-fault", GEN,
     b'raise Fault("unbalanced method body for %s at line %d"',
     b'print("unbalanced method body for %s at line %d"',
     "neg-unbalanced-java-body", []),
    ("M7-consumer-proof-off", GEN,
     b'                             "consumer_proven": bool(meta["root_id"]) and host_class in files\n'
     b'                             and meta["root_id"] in files[host_class]["ids"],',
     b'                             "consumer_proven": True,',
     "pos-hidden-include-unproven", []),
    ("M8-noop-comment", GEN, b"MIN_SCREENS = 8", b"MIN_SCREENS = 8  # no-op control", None, []),
    # M9-M11 lock the surface-pattern widening: the defect they stand for is not a crash but a
    # silently short list, which is the one failure mode an inventory cannot see from inside.
    ("M9-new-patterns-dropped", GEN,
     b'SURFACE_PATTERNS = (("popup_window", POPUP_WINDOW), ("dialog", DIALOG),\n'
     b'                    ("popup_menu", POPUP_MENU), ("alert_dialog", ALERT_DIALOG))',
     b'SURFACE_PATTERNS = (("popup_window", POPUP_WINDOW), ("dialog", DIALOG))',
     "pos-popup-menu-site",
     # pos-comment-line-excluded is declared here because it asserts '"popup_menu": 1' for the live
     # PopupMenu in its own fixture - the measured red set, not a guessed one. The battery ran this
     # mutation first with four names declared and reported the fifth, so the declaration was
     # corrected to the reading rather than the expectation loosened to "any red".
     ["pos-alert-dialog-site", "pos-comment-line-excluded", "pos-surface-kinds-disjoint",
      "pos-real-tree"]),
    ("M10-comment-filter-off", GEN,
     b'    return not (start.startswith("//") or start.startswith("*")'
     b' or start.startswith("/*"))',
     b'    return True  # MUTATION: every comment line becomes live code',
     "pos-comment-line-excluded", []),
    ("M11-layoutless-guard-off", GEN,
     b'            if kind in LAYOUTLESS_KINDS:',
     b'            if kind in ():  # MUTATION: menus and message dialogs get a nearest layout',
     "pos-surface-kinds-disjoint",
     # Measured on the real tree: NavigationScreen:221's PopupMenu sits 23 lines below a live
     # R.layout.item_navigation_row, so with this guard off the system menu is reported as owning
     # that layout. pos-real-tree asserts every popup_menu/alert_dialog line carries layout=-, so it
     # is the case that sees the same defect on production source rather than on a fixture.
     ["pos-real-tree"]),
    # M12 locks the word list of the *printed* restatement fallback, the hole that let "现值 14 屏"
    # sit in the line that forbids restating counts. Patterns are written as encoded str, not byte
    # literals, because Python 3 rejects non-ASCII in b"...".
    ("M12-count-shape-vocab-narrowed", GEN,
     'COUNT_SHAPE = re.compile(r"\\d+\\s*个?\\s*(?:屏幕|浮层|自绘屏|屏)")'.encode("utf-8"),
     'COUNT_SHAPE = re.compile(r"\\d+\\s*个?\\s*(?:屏幕|浮层|自绘屏)")'.encode("utf-8"),
     "neg-spec-pointer-missing", []),
]

MIN_MUTS = 12
pristine = {rel: open(os.path.join(REPO, rel), "rb").read() for rel in (GEN, SELF)}
base = json.load(open(BASELINE, encoding="utf-8"))
base_failing = set(base["failing"])
print("baseline read from the measured file: rc=%s failures=%s failing=%s"
      % (base["rc"], base["failures"], sorted(base_failing)))
print("mutations declared=%d floor=%d floor-met=%s" % (len(MUTS), MIN_MUTS, len(MUTS) >= MIN_MUTS))


def suite():
    r = subprocess.run([sys.executable, "-X", "utf8", os.path.join(COPY, SELF)],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (r.stdout or "") + (r.stderr or "")
    counts = re.search(r"cases executed=(\d+) expected=(\d+) failures=(\d+)", out)
    failing = sorted({m.group(1) for m in
                      re.finditer(r"^CASE (\S+)\s+rc=\d+ want=\d+ FAIL", out, re.M)})
    faults = len([l for l in out.splitlines() if l.startswith("INSTRUMENT FAULT")])
    return r.returncode, failing, (counts.groups() if counts else None), faults


def restore():
    for rel, raw in pristine.items():
        open(os.path.join(COPY, rel), "wb").write(raw)


bad = 0
if len(MUTS) < MIN_MUTS:
    print("FAIL the battery declares %d mutations, floor is %d - a battery that lost an entry silently"
          " stops checking that guard" % (len(MUTS), MIN_MUTS))
    sys.exit(3)
try:
    for name, rel, old, new, target, extra in MUTS:
        restore()
        path = os.path.join(COPY, rel)
        raw = open(path, "rb").read()
        hits = raw.count(old)
        if hits != 1:
            print("MUTATION FAULT %s anchor hits=%d expected 1 - the mutation would not be the"
                  " single change it claims to be" % (name, hits))
            sys.exit(3)
        open(path, "wb").write(raw.replace(old, new))
        rc, failing, counts, faults = suite()
        want_rc = 0 if target is None else 1
        expected = set(base_failing) | ({target} if target else set()) | set(extra)
        caught = set(failing) > base_failing
        new_red = sorted(set(failing) - base_failing)
        print("%s rc=%d want-rc=%d baseline=%d caught=%s new-red=%s expected-new=%s"
              % (name, rc, want_rc, len(base_failing), caught, new_red,
                 sorted((set(extra) | {target}) if target else set())))
        print("%s new-red==declared: %s"
              % (name, new_red == sorted((set(extra) | {target}) if target else set())))
        if faults:
            print("FAIL %s produced an instrument fault rather than a case mismatch; that is tier 3"
                  " and means the mutation broke the harness, not a guard" % name)
            bad = 1
        if rc != want_rc:
            print("FAIL %s returned %d, expected %d" % (name, rc, want_rc))
            bad = 1
        if counts and (int(counts[0]) != int(counts[1])):
            print("FAIL %s ran %s of %s registered cases; a short run cannot attribute red"
                  % (name, counts[0], counts[1]))
            bad = 1
        if set(failing) != expected:
            print("FAIL %s failing set %s != declared %s" % (name, failing, sorted(expected)))
            bad = 1
        if target is None and caught:
            print("FAIL %s is a no-op mutation yet turned something red - the battery would then be"
                  " reacting to any edit, not to the guard being removed" % name)
            bad = 1
        if target is not None:
            if target not in failing:
                print("FAIL %s did not turn its own target case red; that guard was never"
                      " load-bearing" % name)
                bad = 1
            if not caught:
                print("FAIL %s turned nothing red beyond the baseline" % name)
                bad = 1
finally:
    restore()

rc, failing, counts, faults = suite()
print("after-restore copy rc=%d failing=%s counts=%s" % (rc, failing, counts))
if rc != 0 or failing:
    print("FAIL the battery did not leave the copy green again")
    bad = 1
longest = 0
for root, _d, fs in os.walk(COPY):
    for f in fs:
        longest = max(longest, len(os.path.join(root, f)))
print("copy longest path=%d chars (fail-closed at 240)" % longest)
if longest > 240:
    print("FAIL the copy tree got within 20 chars of the Windows path limit; a truncated fixture"
          " would be read as a case failure")
    bad = 1
print("BATTERY ok" if not bad else "BATTERY FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="baseline read from the measured file: rc=0|mutations declared=12 floor=12 floor-met=True|M1-screen-floor-zero rc=1 want-rc=1 baseline=0 caught=True|M1-screen-floor-zero new-red==declared: True|M2-surface-floor-zero new-red==declared: True|M3-root-inflate-condition new-red==declared: True|M4-sw600dp-column-off new-red==declared: True|M5-volatile-commit-stamp new-red==declared: True|M6-unbalanced-body-not-a-fault new-red==declared: True|M7-consumer-proof-off new-red==declared: True|M8-noop-comment rc=0 want-rc=0 baseline=0 caught=False|M8-noop-comment new-red==declared: True|M9-new-patterns-dropped new-red==declared: True|M10-comment-filter-off new-red==declared: True|M11-layoutless-guard-off new-red==declared: True|after-restore copy rc=0 failing=[]|copy longest path=|BATTERY ok"
run 3 0 "battery: each guard degraded turns exactly its own case red, and the no-op control turns nothing red" \
  python -X utf8 "$TMP/battery.py"

# ---- block 04: the guard whose removal cannot be attributed to one case ---------------------
cat > "$TMP/namedfault.py" <<'PY'
"""死 include 守卫单独量测：移除它**不会**让那条用例转红，而是让自检按「仪器崩了」那一层中止。

为什么值得单独记：电池的每条变异都要落到「某条用例不成立」这一层，读者据此把红归因到守卫。这一条
落不到那一层——去掉 raise 之后，同一份夹具让生成器抛出未捕获的 KeyError，自检的 except Exception 分支
打印 INSTRUMENT FAULT 并返回 3。两种后果都认证了同一件事（该守卫承重），但通道不同，所以这里按通道
如实断言：崩在**哪一个用例上**必须可读出，且还原后同一副本必须重新全绿（否则这条读数只是副本坏了）。
"""
import os
import re
import subprocess
import sys

REPO = os.path.abspath(".")
COPY = os.path.join("artifacts", "tmp", "_m005", "prod")
GEN = os.path.join(".trae", "scripts", "dev", "gen_screen_inventory.py")
SELF = os.path.join(".trae", "scripts", "dev", "screen_inventory_selftest.py")
ANCHOR = b'raise Fault("dead include: %s refers to missing layout %s"'
REPL = b'print("dead include: %s refers to missing layout %s"'
CASE = "neg-dead-include"

prod = {rel: open(os.path.join(REPO, rel), "rb").read() for rel in (GEN, SELF)}
bad = 0


def suite():
    r = subprocess.run([sys.executable, "-X", "utf8", os.path.join(COPY, SELF)],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return r.returncode, (r.stdout or "") + (r.stderr or "")


def restore():
    for rel, raw in prod.items():
        open(os.path.join(COPY, rel), "wb").write(raw)


try:
    restore()
    path = os.path.join(COPY, GEN)
    raw = open(path, "rb").read()
    hits = raw.count(ANCHOR)
    print("guard anchor hits=%d expected 1" % hits)
    if hits != 1:
        print("MUTATION FAULT the dead-include guard is not a single removable statement")
        sys.exit(3)
    open(path, "wb").write(raw.replace(ANCHOR, REPL))
    rc, out = suite()
    fault_line = [l for l in out.splitlines() if l.startswith("INSTRUMENT FAULT")]
    named = [l for l in fault_line if CASE in l]
    verdict = [l for l in out.splitlines()
               if re.match(r"^CASE %s\s" % re.escape(CASE), l)]
    print("guard-off rc=%d fault-lines=%d names-the-case=%s case-verdict-line-printed=%s"
          % (rc, len(fault_line), bool(named), bool(verdict)))
    print("guard-off fault reading=%s" % (named[0][:120] if named else
                                         (fault_line[0][:120] if fault_line else "(none)")))
    if rc != 3:
        print("FAIL removing this guard was expected to break the harness (tier 3), got rc=%d -"
              " a 0 or 1 here would mean the guard was not load-bearing after all" % rc)
        bad = 1
    if not named:
        print("FAIL the tier-3 reading does not name which case broke, so the consequence cannot be"
              " attributed to this guard"); bad = 1
    if verdict:
        print("FAIL a CASE verdict line for %s was printed - that would put this consequence back in"
              " the battery's channel and make the separate measurement unnecessary" % CASE)
        bad = 1
finally:
    restore()

rc, out = suite()
print("after-restore copy rc=%d counting-line=%s"
      % (rc, bool(re.search(r"cases executed=\d+ expected=\d+ failures=0", out))))
if rc != 0:
    print("FAIL the copy did not return to green after this measurement; the reading above would"
          " then be about a broken copy, not about the guard"); bad = 1
print("NAMEDFAULT ok" if not bad else "NAMEDFAULT FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="guard anchor hits=1 expected 1|guard-off rc=3 fault-lines=1 names-the-case=True case-verdict-line-printed=False|after-restore copy rc=0 counting-line=True|NAMEDFAULT ok"
run 4 0 "the dead-include guard measured on its own channel: removing it aborts the harness naming that case, and the copy returns to green" \
  python -X utf8 "$TMP/namedfault.py"

# ---- block 05: independent re-read of the production bytes ----------------------------------
cat > "$TMP/recheck.py" <<'PY'
"""块 03 与块 04 自称还原了副本，这里用**另一双眼睛**复算：生产字节等于判定前的指纹，副本字节等于生产字节，
且两份被判脚本仍然能被 py_compile 解析。变异副本若与生产不同，红就无法归因到某一条守卫。"""
import hashlib
import os
import py_compile
import re
import sys

REPO = os.path.abspath(".")
COPY = os.path.join("artifacts", "tmp", "_m005", "prod")
PRE = os.path.join("artifacts", "tmp", "_m005g", "prod-before.txt")
RELS = [os.path.join(".trae", "scripts", "dev", "gen_screen_inventory.py"),
        os.path.join(".trae", "scripts", "dev", "screen_inventory_selftest.py")]


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


pre = {}
for line in open(PRE, encoding="utf-8"):
    m = re.match(r"(\S+)\s+bytes=(\d+)\s+lines=(\d+)\s+sha256=([0-9a-f]{64})", line)
    if m:
        pre[m.group(1).replace("/", os.sep)] = m.group(4)
bad = 0
if len(pre) != len(RELS):
    print("FAIL the pre-run fingerprint holds %d of %d judged paths; a missing entry makes the"
          " comparison vacuous" % (len(pre), len(RELS)))
    bad = 1
for rel in RELS:
    now = sha(os.path.join(REPO, rel))
    print("production unchanged %s -> %s" % (rel.replace(os.sep, "/"), now == pre.get(rel)))
    if now != pre.get(rel):
        bad = 1
    cnow = sha(os.path.join(COPY, rel))
    print("copy equals production %s -> %s" % (rel.replace(os.sep, "/"), cnow == now))
    if cnow != now:
        bad = 1
    try:
        py_compile.compile(os.path.join(REPO, rel), doraise=True,
                           cfile=os.path.join("artifacts", "tmp", "_m005g", "c.pyc"))
        print("parses %s -> True" % rel.replace(os.sep, "/"))
    except py_compile.PyCompileError as exc:
        print("parses %s -> False (%s)" % (rel.replace(os.sep, "/"), exc))
        bad = 1
print("RECHECK ok" if not bad else "RECHECK FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="production unchanged .trae/scripts/dev/gen_screen_inventory.py -> True|production unchanged .trae/scripts/dev/screen_inventory_selftest.py -> True|copy equals production .trae/scripts/dev/gen_screen_inventory.py -> True|copy equals production .trae/scripts/dev/screen_inventory_selftest.py -> True|parses .trae/scripts/dev/gen_screen_inventory.py -> True|RECHECK ok"
run 5 0 "independent re-read: production bytes equal the pre-run fingerprints and the copy no longer differs from them" \
  python -X utf8 "$TMP/recheck.py"

# ---- block 06: nothing was written outside the copy -----------------------------------------
cat > "$TMP/scope.py" <<'PY'
"""改动面：判的是「本次运行期间生产面没有位移」，用 git 现场重读再与运行开始时的快照比对。
范围统计同时覆盖已跟踪的 diff 与未跟踪的新文件：只跑 git diff 对新增文件系统性失明（既有规则）。"""
import subprocess
import sys

SURFACES = ("android/", "backend/", "frontend/")
SCRATCH = "artifacts/tmp/"
before = [l.strip() for l in open("artifacts/tmp/_m005g/surfaces-before.txt", encoding="utf-8")
          if l.strip()]
now = set()
for tag, cmd in (("diff", ["git", "-c", "core.quotepath=false", "diff", "--name-only", "HEAD"]),
                 ("ls-files", ["git", "-c", "core.quotepath=false", "ls-files", "--others",
                               "--exclude-standard"])):
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    print("command %s ok rc=%d" % (tag, r.returncode))
    if r.returncode != 0:
        print("FAIL git did not answer; 'cannot read the diff' is not 'the diff is clean'")
        sys.exit(3)
    for l in (r.stdout or "").splitlines():
        p = l.strip().strip('"')
        if not p or p.startswith(SCRATCH):
            continue                       # scratch and the copy tree are not a change surface
        if p.startswith(SURFACES):
            now.add(p)
now = sorted(now)
print("production-surface paths snapshot=%d now=%d identical=%s"
      % (len(before), len(now), before == now))
print("surface list=%s" % now)
bad = 0
if before != now:
    added = sorted(set(now) - set(before))
    removed = sorted(set(before) - set(now))
    print("FAIL the battery moved a production surface during this run: added=%s removed=%s"
          % (added, removed))
    bad = 1
print("SCOPE ok" if not bad else "SCOPE FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="command diff ok rc=0|command ls-files ok rc=0|production-surface paths snapshot=|identical=True|SCOPE ok"
run 6 0 "change surface read from git and compared with the run-start snapshot: nothing on a production surface moved during this run" \
  python -X utf8 "$TMP/scope.py"

# ---- block 07: the three 005 evidence files agree with each other ---------------------------
cat > "$TMP/consistency.py" <<'PY'
"""同一件事实在 RED / GREEN / MUTATION 三份产物里必须给同一个读数。用例数不写死，而是从 GREEN 与
本文件已写入的块 02 输出里现场取；两处对不上就是漂移（本仓库已两次因抄旧读数翻车）。

本文件自己的 TOTAL 段还没写（它由本次运行在块 08 之后追加），所以这里只对 RED 与 GREEN 要求收尾行，
对本文件只要求它确实开始记录判定块——「不能自证自己的结尾」是这件事的性质，不是漏检。"""
import os
import re
import sys

D = "artifacts/evidence"
RED = os.path.join(D, "T-2026-10-15-005-red.txt")
GREEN = os.path.join(D, "T-2026-10-15-005-green.txt")
MUT = os.path.join(D, "T-2026-10-15-005-mutation.txt")
bad = 0
texts = {}
for p in (RED, GREEN, MUT):
    if not os.path.isfile(p):
        print("FAIL %s does not exist yet - the mutation evidence has nothing to cross-check" % p)
        sys.exit(3)
    texts[p] = open(p, encoding="utf-8").read()
    tail = texts[p].rstrip().splitlines()[-1]
    print("%s last-line=%s" % (os.path.basename(p), tail[:60]))
for p in (RED, GREEN):
    if "unexpected count = 0" not in texts[p] or not texts[p].rstrip().splitlines()[-1].startswith("DECODE ok"):
        print("FAIL %s does not end with a clean expectation table and a decode check" % p)
        bad = 1
if "===== block 3" not in texts[MUT]:
    print("FAIL this file has not recorded the battery itself, so there is nothing to cross-read")
    bad = 1
else:
    print("this file already carries the battery block (its own TOTAL is written after this check)")
g = re.search(r"SUITE executed=(\d+) expected=(\d+)", texts[GREEN])
m = re.search(r"BASELINE rc=\d+ executed=(\d+) expected=(\d+)", texts[MUT])
if not g or not m:
    print("FAIL the case counts cannot be read out of GREEN and MUTATION (got green=%s mut=%s)"
          % (bool(g), bool(m)))
    bad = 1
else:
    print("case count green=%s copy-baseline=%s equal=%s"
          % (g.group(1), m.group(1), g.group(1) == m.group(1)))
    if g.group(1) != m.group(1) or g.group(2) != m.group(2):
        print("FAIL the production suite and the copy baseline disagree about how many cases exist -"
              " the copy is not the same instrument")
        bad = 1
chunks = {}
for part in texts[MUT].split("\n===== block ")[1:]:
    chunks[part.split(" - ")[0].strip()] = part
print("block sections found=%s" % sorted(chunks, key=int))
battery = chunks.get("3", "")
named = chunks.get("4", "")
if not battery or not named:
    print("FAIL the battery (found=%s) or the named-fault section (found=%s) is not in this file"
          " yet - an empty window would make both readings below vacuously true"
          % (bool(battery), bool(named)))
    sys.exit(1)
if "INSTRUMENT FAULT" in battery:
    print("FAIL the battery block carries an instrument fault line; a mutation that breaks the"
          " harness cannot attribute its red to a guard")
    bad = 1
else:
    print("battery block has no instrument-fault line (the named-fault reading lives in block 4)")
if "INSTRUMENT FAULT in case neg-dead-include" not in named:
    print("FAIL block 4 did not record the harness-breaking channel for the dead-include guard")
    bad = 1
print("CONSISTENCY ok" if not bad else "CONSISTENCY FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="T-2026-10-15-005-red.txt last-line=DECODE ok|T-2026-10-15-005-green.txt last-line=DECODE ok|block sections found=|this file already carries the battery block|copy-baseline=|equal=True|battery block has no instrument-fault line|CONSISTENCY ok"
run 7 0 "cross-file consistency: RED, GREEN and this file report the same case count and the same clean expectation tables" \
  python -X utf8 "$TMP/consistency.py"

# ---- block 08: encoding gate, self-probed both ways -----------------------------------------
cat > "$TMP/utf8_gate.py" <<'PY'
"""闸门自己正反自检：已知坏字节串必须被拒（正控），本片判过的文件必须通过（负控）。"""
import sys

broken = bytes([107, 97, 169, 102, 101])
try:
    broken.decode("utf-8")
    print("GATE SELFTEST FAIL: the decoder accepted a known-broken byte string")
    sys.exit(3)
except UnicodeDecodeError:
    print("GATE SELFTEST ok: a known-broken byte string is rejected")
bad = 0
for p in sys.argv[1:]:
    raw = open(p, "rb").read()
    try:
        raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        print("FAIL %s is not valid UTF-8 (%s)" % (p, exc))
        bad += 1
        continue
    if bytes([239, 191, 189]) in raw:
        print("FAIL %s contains U+FFFD replacement characters" % p)
        bad += 1
        continue
    print("gate-ok %s bytes=%d" % (p, len(raw)))
print("gate covered %d file(s)" % len(sys.argv[1:]))
print("EXITCODE=%d" % (1 if bad else 0))
sys.exit(1 if bad else 0)
PY
WANT_TEXT="GATE SELFTEST ok|EXITCODE=0|gate covered 6 file(s)"
run 8 0 "encoding gate over the two judged instruments, this generator and the 005 evidence and task files" \
  python -X utf8 "$TMP/utf8_gate.py" .trae/scripts/dev/gen_screen_inventory.py \
  .trae/scripts/dev/screen_inventory_selftest.py artifacts/tmp/gen-005-mutation.sh \
  artifacts/evidence/T-2026-10-15-005-red.txt artifacts/evidence/T-2026-10-15-005-green.txt \
  .trae/tasks/T-2026-10-15-screen-inventory-parity-ledger-005.md

# ---- remove the copy tree -------------------------------------------------------------------
rm -rf artifacts/tmp/_m005
if [ -d artifacts/tmp/_m005 ]; then
  echo "CLEANUP FAULT: the copy tree is still present" | tee -a "$TMP/console.txt"
  bad=$((bad + 1))
else
  echo "copy tree removed" | tee -a "$TMP/console.txt"
fi

longest=$(python -X utf8 -c "
import os, sys
best = 0
for base, _d, fs in os.walk(sys.argv[1]):
    for f in fs:
        best = max(best, len(os.path.join(base, f)))
print(best)
" "$TMP")
if [ "$longest" -gt 200 ]; then
  echo "PATH FAULT: scratch longest path ${longest} chars is too close to the Windows 260 limit" | tee -a "$TMP/console.txt"
  bad=$((bad + 1))
fi
echo "scratch longest path = ${longest} chars (limit 260, margin $((260 - longest)))" | tee -a "$TMP/console.txt"

{
  echo ""
  echo "===== TOTAL (derived, ASCII-only tail) ====="
  echo "block count floor (bare integer literal, asserted against this script's own source line):"
  echo "  blocks=${BLOCKS} expectation_entries=${#EXPECT[@]} floor=${MIN_BLOCKS}"
} >> "$EV"
if [ "$BLOCKS" -lt "$MIN_BLOCKS" ] || [ "${#EXPECT[@]}" -lt "$MIN_BLOCKS" ] \
   || [ "$BLOCKS" -ne "${#EXPECT[@]}" ]; then
  echo "  -> FAIL floor not met, or a block has no expectation entry" >> "$EV"
  bad=$((bad + 1))
else
  echo "  -> ok" >> "$EV"
fi

{
  echo "unexpected count = ${bad}"
  echo "expectation table (want/got per block):"
  for k in $(printf '%s\n' "${!EXPECT[@]}" | sort -n); do
    echo "  block ${k}: want=${EXPECT[$k]} got=${GOT[$k]} label=${LABELS[$k]}"
  done
} >> "$EV"

tail_measure=$(python -X utf8 -c "
import sys
raw = open(sys.argv[1], 'rb').read()
marker = bytes([61, 61, 61, 61, 61]) + b' TOTAL (derived, ASCII-only tail) ====='
i = raw.rfind(marker)
if i < 0:
    print('TAIL MEASURE FAULT: marker not found')
    sys.exit(0)
seg = raw[i:]
print('ascii tail: bytes=%d non-ascii=%d (must be 0)' % (len(seg), sum(1 for b in seg if b > 127)))
" "$EV" 2>&1)
echo "$tail_measure" >> "$EV"
case "$tail_measure" in
  *"non-ascii=0"*) ;;
  *) bad=$((bad + 1)); echo "ASCII TAIL VIOLATION: ${tail_measure}" | tee -a "$TMP/console.txt" ;;
esac

if [ "$bad" != "0" ]; then final_rc=1; else final_rc=0; fi
echo "OVERALL_EXITCODE=${final_rc}" >> "$EV"

decode=$(python -X utf8 -c "
import sys
raw = open(sys.argv[1], 'rb').read()
try:
    raw.decode('utf-8')
except UnicodeDecodeError as exc:
    print('DECODE FAIL', exc)
    sys.exit(1)
cr = raw.count(bytes([13]))
if cr:
    print('DECODE FAIL line-ending: %d carriage return(s) present in an artefact pinned to LF' % cr)
    sys.exit(1)
print('DECODE ok utf8=True cr=0 (size: wc -c this file)')
" "$EV" 2>&1)
decode_rc=$?
echo "$decode" | tee -a "$EV" >> "$TMP/console.txt"
if [ "$decode_rc" != "0" ]; then
  echo "OVERALL OVERRIDDEN BY DECODE FAILURE" >> "$TMP/console.txt"
  final_rc=1
fi
exit "$final_rc"
