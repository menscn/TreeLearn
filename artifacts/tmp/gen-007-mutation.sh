#!/usr/bin/env bash
# T-2026-10-15-007 mutation evidence generator (one-off; lives in artifacts/tmp by convention).
# Writes artifacts/evidence/T-2026-10-15-007-mutation.txt.
# 这片交付的是一条判据：台账生成器的每条守卫都要被「人为退化」实测过，而不是只在自检里被声明。
# 自检的绿只证明「当前实现下用例通过」，不证明「守卫消失时用例转红」——后者才是这条判据承重的部分
# （既有的「断言可证伪必须做变异测试」规则）。因此本片把守卫逐条退化，看它自己的目标用例是否转红，
# 并且要求红得只有那么多：多红一条说明退化动到了别处，少红一条说明那条守卫从未承重。
# 做法约束（沿用 AGENTS.md）：变异跑在 artifacts/tmp/_m007/prod 的**副本**上，与生产文件同深度，
# 因此按自身位置上溯算仓库根的脚本会算到副本根；副本留在仓库内，git rev-parse 才仍然可用。
# 每条判据自带命令行与退出码；总退出码由 want/got 表派生；每块带内容见证；MIN_BLOCKS 是与本文件
# 自身源码比对的字面整数；基线红/绿由块 02 实测写出、块 03 现场读取，不假设「基线必然是绿的」。
set -uo pipefail

EV=artifacts/evidence/T-2026-10-15-007-mutation.txt
TMP=artifacts/tmp/_m007g
COPY=artifacts/tmp/_m007/prod
GEN=.trae/scripts/dev/gen_parity_ledger.py
SELF=.trae/scripts/dev/parity_ledger_selftest.py
mkdir -p "$TMP"
rm -f "$EV" "$TMP/console.txt"

if [ ! -d .git ]; then
  echo "FATAL: run this script from the repository root (cwd=$(pwd))"
  exit 3
fi
for f in "$GEN" "$SELF" .trae/scripts/dev/gen_screen_inventory.py \
         Docs/04-ux-design/parity/screen-inventory.json; do
  if [ ! -f "$f" ]; then
    echo "FATAL: $f is missing; this mutation battery would judge nothing"
    exit 3
  fi
done

MIN_BLOCKS=7
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
  echo "# T-2026-10-15-007 MUTATION - 台账生成器每条守卫的退化实测"
  echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)  (UTC)"
  echo "# Generator: artifacts/tmp/gen-007-mutation.sh   重跑：bash artifacts/tmp/gen-007-mutation.sh"
  echo "# 变异对象：.trae/scripts/dev/gen_parity_ledger.py 与 parity_ledger_selftest.py 的**副本**"
  echo "#   （artifacts/tmp/_m007/prod/ 下同名同深度路径）；生产字节只在块 04 被读，从不被写。"
  echo "# 生产文件指纹（判定前读数；块 04 在全部变异之后复核是否仍相同）："
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
  local full="$TMP/block-$n.out"
  printf '%s\n' "$out" > "$full"
  printf '%s\n' "$out" | tail -n 22 >> "$EV"
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
"""副本必须是**同深度**的副本而不是临时目录：被测脚本按自身位置上溯算仓库根，放太浅会让夹具读不到
产物而红得合理却是假见证（本仓库实测过）。同时逐文件复核副本字节与生产字节相同——否则「变异造成的红」
可能来自复制失真。"""
import hashlib
import os
import shutil
import sys

REPO = os.path.abspath(".")
COPY = os.path.join("artifacts", "tmp", "_m007", "prod")
DEV = os.path.join(".trae", "scripts", "dev")
FILES = [
    os.path.join(DEV, "gen_parity_ledger.py"),
    os.path.join(DEV, "parity_ledger_selftest.py"),
    os.path.join(DEV, "gen_screen_inventory.py"),
    os.path.join(DEV, "screen_inventory_selftest.py"),
    os.path.join(DEV, "parity", "screens.json"),
    "Docs/04-ux-design/parity/screen-inventory.json",
    "Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md",
    "artifacts/evidence/ux-chat-1to1-measure-2026-09-27.txt",
    "artifacts/fixtures/parity/chat-design-2026-09-27.png",
    "artifacts/tmp/verify_1to1.mjs",
]
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
for rel in FILES + [os.path.join(DEV, "parity", "screens.json")]:
    a, b = os.path.join(REPO, rel), os.path.join(COPY, rel)
    if os.path.isfile(b) and sha(a) != sha(b):
        mismatch.append(rel)
print("byte-identical single files=%d mismatches=%d" % (len(FILES), len(mismatch)))
if mismatch:
    print("FAIL copied bytes differ from production: %s" % mismatch)
    bad = 1

# 同深度检查：生产文件在仓库里的相对路径段数，必须与副本里的段数相同
for rel in (os.path.join(DEV, "gen_parity_ledger.py"), os.path.join(DEV, "parity_ledger_selftest.py")):
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
WANT_TEXT="copy files=|byte-identical single files=10 mismatches=0|same-depth .trae/scripts/dev/gen_parity_ledger.py prod=4 copy=4|COPY ok"
run 1 0 "copy tree: same-depth copy under artifacts/tmp, every copied byte equal to production" \
  python -X utf8 "$TMP/mkcopy.py"

# ---- block 02: the baseline must be green, and its red set must be measured ----------------
cat > "$TMP/pristine.py" <<'PY'
"""基线红不红是**实测**出来的，并写进 baseline.json 给块 03 读；不假设「副本必然全绿」。
若基线已有用例在红，变异就只能报「无法归因」而不是把别人的红算成本次退化的功劳。"""
import json
import os
import re
import subprocess
import sys

COPY = os.path.join("artifacts", "tmp", "_m007", "prod")
SELF = os.path.join(COPY, ".trae", "scripts", "dev", "parity_ledger_selftest.py")
OUT = os.path.join("artifacts", "tmp", "_m007g", "baseline.json")

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
          " attribute any new red to the mutation, so this slice proves nothing until this is fixed")
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

期望集合是**声明的字面量**（本文件下方的 MUTS），实测读数逐条打出来与它比对；基线红集从块 02 的
实测文件读，不写死空集。最后一条是不改变任何判定的注释变异，它必须仍然全绿——否则「任何改动都会红」
的电池和「守卫消失才红」的电池读起来一模一样，前者其实什么都没认证。
"""
import json
import os
import re
import subprocess
import sys

REPO = os.path.abspath(".")
COPY = os.path.join("artifacts", "tmp", "_m007", "prod")
DEV = os.path.join(".trae", "scripts", "dev")
GEN = os.path.join(DEV, "gen_parity_ledger.py")
SELF = os.path.join(DEV, "parity_ledger_selftest.py")
BASELINE = os.path.join("artifacts", "tmp", "_m007g", "baseline.json")

# (name, file, old, new, target case, extra cases that are expected to go red too)
MUTS = [
    ("M1-staleness-guard-off", GEN,
     b'    if fresh["projection"] != inv_proj:', b"    if False:",
     "neg-stale-inventory", []),
    ("M2-pointer-comparison-off", GEN,
     b"    return [fresh_link.get(k) for k in LINK_KEYS] != [inv_link.get(k) for k in LINK_KEYS]",
     b"    return False", "neg-pointer-drift", []),
    ("M3-row-floor-zero", GEN, b"MIN_ROWS = 12", b"MIN_ROWS = 0", "neg-row-floor", []),
    ("M4-visual-always-measured", GEN, b"def visual_state(key, entry):",
     b'def visual_state(key, entry):\n    return "MEASURED", "mutation M4"',
     "pos-absence-is-a-token-not-a-blank", ["pos-real-tree"]),
    ("M5-instrument-always-adopted", GEN, b"def instrument_line(name):",
     b'def instrument_line(name):\n    return "adopted"', "pos-real-tree", []),
    ("M6-adjacency-scan-returns", SELF,
     b'    return [l for l in text.splitlines() if l.startswith("FAIL ")]',
     b'    return [l for l in text.splitlines() if "\\nFAIL " in l]',
     "pos-fail-line-scan-catches-a-leading-fail", []),
    ("M7-noop-comment", GEN, b"MIN_ROWS = 12", b"MIN_ROWS = 12  # no-op control", None, []),
    # the sibling load hoisted back out of the fault guard - the shape measured before the fix
    # (rc=1, empty stdout, traceback on stderr).  The duplicated call inside the try is what makes
    # this a one-anchor edit; with the hoist in place the guard below can never see that fault.
    ("M8-sibling-load-outside-guard", GEN,
     b"    try:\n        # Loading the sibling instrument has to be inside the fault guard",
     b"    mod = inventory_module()\n    try:\n        # (M8) sibling load hoisted above the guard",
     "neg-inventory-instrument-absent", []),
]

MIN_MUTS = 8

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
    print("FAIL the battery declares %d mutations, floor is %d - a battery that lost an entry"
          " silently stops checking that guard while every block still reports want=got"
          % (len(MUTS), MIN_MUTS))
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
                print("FAIL %s did not turn its own target case red; that guard was never load-bearing"
                      % name)
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
print("BATTERY ok" if not bad else "BATTERY FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="baseline read from the measured file: rc=0|mutations declared=8 floor=8 floor-met=True|caught=True new-red=['neg-stale-inventory']|caught=True new-red=['neg-pointer-drift']|caught=True new-red=['neg-row-floor']|caught=True new-red=['pos-absence-is-a-token-not-a-blank', 'pos-real-tree']|caught=True new-red=['pos-real-tree']|caught=True new-red=['pos-fail-line-scan-catches-a-leading-fail']|caught=True new-red=['neg-inventory-instrument-absent']|M7-noop-comment rc=0 want-rc=0 baseline=0 caught=False new-red=[]|after-restore copy rc=0 failing=[]|BATTERY ok"
run 3 0 "battery: each guard degraded turns exactly its own case red, and the no-op control turns nothing red" \
  python -X utf8 "$TMP/battery.py"

# ---- block 04: independent re-read of the production bytes ----------------------------------
cat > "$TMP/recheck.py" <<'PY'
"""块 03 自称还原了副本，这里用**另一双眼睛**复算：生产字节等于判定前的指纹，副本字节等于生产字节，
且两个被判脚本仍然能被 py_compile 解析。变异副本若与生产不同，红就无法归因到某一条守卫。"""
import hashlib
import os
import py_compile
import re
import sys

REPO = os.path.abspath(".")
COPY = os.path.join("artifacts", "tmp", "_m007", "prod")
PRE = os.path.join("artifacts", "tmp", "_m007g", "prod-before.txt")
RELS = [os.path.join(".trae", "scripts", "dev", "gen_parity_ledger.py"),
        os.path.join(".trae", "scripts", "dev", "parity_ledger_selftest.py")]


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


pre = {}
for line in open(PRE, encoding="utf-8"):
    m = re.match(r"(\S+)\s+bytes=(\d+)\s+lines=(\d+)\s+sha256=([0-9a-f]{64})", line)
    if m:
        pre[m.group(1).replace("/", os.sep)] = m.group(4)
bad = 0
for rel in RELS:
    now = sha(os.path.join(REPO, rel))
    was = pre.get(rel)
    same = (sha(os.path.join(COPY, rel)) == now) if os.path.isfile(os.path.join(COPY, rel)) else False
    print("production unchanged %s -> %s" % (rel.replace(os.sep, "/"), now == was))
    print("copy equals production %s -> %s" % (rel.replace(os.sep, "/"), same))
    if was is None or now != was or not same:
        print("FAIL %s is not the same file the battery started from (pre=%s copy-equals-prod=%s)"
              % (rel, (was or "")[:12], same))
        bad = 1
    try:
        py_compile.compile(os.path.join(REPO, rel), doraise=True,
                           cfile=os.path.join("artifacts", "tmp", "_m007g", "c.pyc"))
        print("parses %s -> True" % rel.replace(os.sep, "/"))
    except py_compile.PyCompileError as exc:
        print("FAIL %s does not parse after the battery: %s" % (rel, exc))
        bad = 1
print("RECHECK ok" if not bad else "RECHECK FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="production unchanged .trae/scripts/dev/gen_parity_ledger.py -> True|production unchanged .trae/scripts/dev/parity_ledger_selftest.py -> True|copy equals production .trae/scripts/dev/gen_parity_ledger.py -> True|copy equals production .trae/scripts/dev/parity_ledger_selftest.py -> True|parses .trae/scripts/dev/gen_parity_ledger.py -> True|RECHECK ok"
run 4 0 "independent re-read: production bytes equal the pre-run fingerprints and the copy no longer differs from them" \
  python -X utf8 "$TMP/recheck.py"

# ---- block 05: nothing was written outside the copy -----------------------------------------
cat > "$TMP/scope.py" <<'PY'
"""改动面：判的是「本次运行期间生产面没有位移」，用 git 现场重读再与运行开始时的快照比对。
不判「整棵工作树的生产面干净」——那会把别片尚未提交的改动算成本片的缺陷（实测快照里已有
frontend/src/styles/tokens.css，属 004）。范围统计同时覆盖已跟踪的 diff 与未跟踪的新文件：
只跑 git diff 对新增文件系统性失明（既有规则）。"""
import subprocess
import sys

SURFACES = ("android/", "backend/", "frontend/")
SCRATCH = "artifacts/tmp/"
before = [l.strip() for l in open("artifacts/tmp/_m007g/surfaces-before.txt", encoding="utf-8")
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
run 5 0 "change surface read from git and compared with the run-start snapshot: nothing on a production surface moved during this run" \
  python -X utf8 "$TMP/scope.py"

# ---- block 06: the three 007 evidence files agree with each other ---------------------------
cat > "$TMP/consistency.py" <<'PY'
"""同一件事实在 RED / GREEN / MUTATION 三份产物里必须给同一个读数。用例数不写死，而是从 GREEN 与
本文件已写入的块 02 输出里现场取；两处对不上就是漂移（本仓库已两次因抄旧读数翻车）。

本文件自己的 TOTAL 段还没写（它由本次运行在块 07 之后追加），所以这里只对 RED 与 GREEN 要求收尾行，
对本文件只要求它确实开始记录判定块——「不能自证自己的结尾」是这件事的性质，不是漏检。"""
import os
import re
import sys

D = "artifacts/evidence"
RED = os.path.join(D, "T-2026-10-15-007-red.txt")
GREEN = os.path.join(D, "T-2026-10-15-007-green.txt")
MUT = os.path.join(D, "T-2026-10-15-007-mutation.txt")
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
if "INSTRUMENT FAULT" in texts[MUT]:
    print("FAIL the mutation evidence carries an instrument fault line")
    bad = 1
print("CONSISTENCY ok" if not bad else "CONSISTENCY FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="T-2026-10-15-007-red.txt last-line=DECODE ok|T-2026-10-15-007-green.txt last-line=DECODE ok|this file already carries the battery block|copy-baseline=|equal=True|CONSISTENCY ok"
run 6 0 "cross-file consistency: RED, GREEN and this file report the same case count and the same clean expectation tables" \
  python -X utf8 "$TMP/consistency.py"

# ---- block 07: encoding gate, self-probed both ways -----------------------------------------
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
run 7 0 "encoding gate over the two judged instruments, this generator and the three 007 evidence files" \
  python -X utf8 "$TMP/utf8_gate.py" .trae/scripts/dev/gen_parity_ledger.py \
  .trae/scripts/dev/parity_ledger_selftest.py artifacts/tmp/gen-007-mutation.sh \
  artifacts/evidence/T-2026-10-15-007-red.txt artifacts/evidence/T-2026-10-15-007-green.txt \
  .trae/tasks/T-2026-10-15-parity-ledger-generator-007.md

# ---- remove the copy tree -------------------------------------------------------------------
rm -rf artifacts/tmp/_m007
if [ -d artifacts/tmp/_m007 ]; then
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
print('DECODE ok file bytes=%d' % len(raw))
" "$EV" 2>&1)
decode_rc=$?
echo "$decode" | tee -a "$EV" >> "$TMP/console.txt"
if [ "$decode_rc" != "0" ]; then
  echo "OVERALL OVERRIDDEN BY DECODE FAILURE" >> "$TMP/console.txt"
  final_rc=1
fi
exit "$final_rc"
