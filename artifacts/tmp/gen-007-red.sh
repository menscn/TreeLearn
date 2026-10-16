#!/usr/bin/env bash
# T-2026-10-15-007 RED evidence generator (one-off; lives in artifacts/tmp by convention).
# Writes artifacts/evidence/T-2026-10-15-007-red.txt.
# 本片要新增的是台账生成器。RED 的正确形态不是「我想象中的失败」，而是把仪器**真的取走**
# 再看同一套夹具怎么判：因此块 02 在一个进程内用 os.replace 把 gen_parity_ledger.py 挪开，
# 在窗口里读三条「不在场」证据，再在 finally 里按字节还原并复核哈希。
# 已知前提：该文件本片之前从未入库（块 05 实测 git log 为空），所以「独立于本次运行的基线」
# 只能取「起点必须在场」这一条 —— 起点不在场即拒绝注入并判 3，上一次被打断的残留因此
# 不可能被当成本次的干净基线（AGENTS.md 失败案例库 2026-10-15 那条的适用形态）。
# 规则沿用：每块自带命令行与自己的退出码；总退出码由 want/got 表派生，不写死 0；
# 每块带内容见证（只有退出码的绿不认证任何东西）；MIN_BLOCKS 是与本文件自身源码比对的字面整数；
# 编码闸门之后的正文一律 ASCII-only 且实测 non-ascii=0。
set -uo pipefail

EV=artifacts/evidence/T-2026-10-15-007-red.txt
TMP=artifacts/tmp/_red007
CONSOLE=artifacts/tmp/_red007-console.txt
GEN=.trae/scripts/dev/gen_parity_ledger.py
SELF=.trae/scripts/dev/parity_ledger_selftest.py
INV=.trae/scripts/dev/gen_screen_inventory.py
LEDGER=artifacts/parity/ledger.md
mkdir -p "$TMP"
rm -f "$EV" "$CONSOLE"

if [ ! -d .git ]; then
  echo "FATAL: run this script from the repository root (cwd=$(pwd))"
  exit 3
fi
for f in "$GEN" "$SELF" "$INV"; do
  if [ ! -f "$f" ]; then
    echo "FATAL: $f is missing; this RED cannot tell 'instrument absent' from 'fixture broken'"
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
PROD="$TMP/prod-before.txt"
python -X utf8 -c "$FP" "$GEN" "$SELF" "$INV" "$LEDGER" > "$PROD" 2>&1
prod_rc=$?

{
  echo "# T-2026-10-15-007 RED - 1:1 台账生成器收编前的失败证据"
  echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)  (UTC)"
  echo "# Generator: artifacts/tmp/gen-007-red.sh   重跑：bash artifacts/tmp/gen-007-red.sh"
  echo "# 本文件覆盖的调用形态："
  echo "#   python -X utf8 $SELF                       （正反自检全套；用例数由产物自己打印，本脚本不钉数）"
  echo "#   python -X utf8 $GEN --no-write             （台账生成器本体）"
  echo "#   python -X utf8 $TMP/window007.py            （块 02：单进程挪开-读数-还原窗口）"
  echo "# 判据面：块 01/03/04/05/06 全程不动工作树；只有块 02 在一个进程的 try/finally 内挪仪器，"
  echo "#   起点不在场即拒绝（残留不会被当基线），还原后按字节复核 sha256 与长度。"
  echo "# 被核文件指纹（判定前读数；块 01 在窗口之后再复核是否相同）："
  sed 's/^/#   /' "$PROD"
  if [ "$prod_rc" != "0" ]; then
    echo "# FINGERPRINT PRE-RUN rc=${prod_rc} - not every file was readable"
  fi
} > "$EV" 2> "$TMP/header.err"
hdr_err=$(wc -c < "$TMP/header.err" | tr -d ' ')
echo "header stderr bytes=${hdr_err}" | tee -a "$CONSOLE"
if [ "$hdr_err" != "0" ]; then
  echo "INSTRUMENT FAULT: header write produced stderr; evidence text would be silently short" | tee -a "$CONSOLE"
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
  printf '%s\n' "$out" | tail -n 18 >> "$EV"
  GOT[$n]=$rc
  echo "----- exit code: ${rc} (want ${want}) -----" >> "$EV"
  if [ "$rc" != "$want" ]; then
    bad=$((bad + 1))
    echo "UNEXPECTED rc for block ${n}: got ${rc} want ${want} (${label})" | tee -a "$CONSOLE"
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
      echo "MISSING CONTENT WITNESS for block ${n}:${miss} (${label})" | tee -a "$CONSOLE"
    else
      echo "content witness ok for block ${n}" | tee -a "$CONSOLE"
    fi
  fi
}

# ---- block 01: the slice touches no production code ------------------------------------------
cat > "$TMP/precheck.py" <<'PY'
"""RED 的前提：本片只加工具；android/ 的既有脏集打印而不判定（判定它的地方在 GREEN 的窗口增量
与提交本身），且三份仪器在判定前后字节相同。"""
import hashlib, os, subprocess, sys
WATCH = [".trae/scripts/dev/gen_parity_ledger.py", ".trae/scripts/dev/parity_ledger_selftest.py",
         ".trae/scripts/dev/gen_screen_inventory.py", "artifacts/parity/ledger.md"]
bad = 0
r = subprocess.run(["git", "-c", "core.quotepath=false", "diff", "--name-only", "--", "android"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
touched = [l for l in (r.stdout or "").splitlines() if l.strip()]
print("android/ paths already dirty at run start = %d %s" % (len(touched), touched))
print("info: that count is PRINTED, not judged - a shared worktree can hold another slice's"
      " uncommitted android work, and judging it here would blame 007 for a neighbouring slice."
      " 'This slice adds no android path' is judged where it is decidable - the commit itself"
      " (git show --name-only <this commit> | grep ^android/ must be empty) - and the"
      " keep-behaviour half is judged in the GREEN run as a start-to-end delta.")
before = {p: hashlib.sha256(open(p, "rb").read()).hexdigest() for p in WATCH if os.path.isfile(p)}
open(os.path.join("artifacts", "tmp", "_red007", "pre-hashes.txt"), "w",
     encoding="utf-8").write("\n".join("%s %s" % (k, v) for k, v in sorted(before.items())))
print("recorded %d pre-window fingerprint(s)" % len(before))
print("PRE ok" if not bad else "PRE FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="android/ paths already dirty at run start =|PRINTED, not judged|pre-window fingerprint(s)|PRE ok"
run 1 0 "keep-behaviour premise: the instruments' pre-window bytes are recorded and the android/ \
dirt that predates this run is printed rather than judged" \
  python -X utf8 "$TMP/precheck.py"

# ---- block 02: the instrument really removed, inside one process -----------------------------
cat > "$TMP/window007.py" <<'PY'
"""把仪器真的取走，在同一个进程的窗口里读三条「不在场」证据，finally 里按字节还原。

三条读数各自的用处：
  A 同一套夹具（全套正反自检）在仪器不在场时必须落在 3 这一层，而不是 1（用例不符）或 0；
  B 生成器本体不可调用时 Python 自己报 2，读者据此知道「跑不起来」与「跑起来判红」是两回事；
  C 腐坏守卫令牌 INVENTORY STALE 的**持有文件**在窗口里必须少掉生成器那一份，且剩下的每一份都只是
    「断言这个令牌」的自检而不是「发出这个令牌」的正文——A 已经证明那份自检在生成器不在场时根本跑不起来
    （rc=3），所以窗口里没有任何东西能写出这条判据。RED 红的是「判据不存在」，不是路径没配好。
还原判据：起点必须在场（不在场即拒绝，注入残留永不被当成本次基线），还原后 sha256 与长度都
必须与起点相同，且文件能被 py_compile 解析。本片之前该文件从未入库，故 HEAD 版本无法作独立基线。
"""
import hashlib, os, py_compile, shutil, subprocess, sys

GEN = ".trae/scripts/dev/gen_parity_ledger.py"
SELF = ".trae/scripts/dev/parity_ledger_selftest.py"
KEEP = os.path.join("artifacts", "tmp", "_red007", "gen-aside.py")
STALE = "INVENTORY STALE"
DEV = ".trae/scripts/dev"

bad = 0


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def bytes_of(p):
    return len(open(p, "rb").read())


def stale_carriers():
    hits = []
    for name in sorted(os.listdir(DEV)):
        if not name.endswith(".py"):
            continue
        p = os.path.join(DEV, name)
        try:
            if STALE in open(p, encoding="utf-8", errors="replace").read():
                hits.append(name)
        except OSError:
            hits.append("UNREADABLE:" + name)
    return hits


def call(cmd):
    r = subprocess.run([sys.executable, "-X", "utf8"] + cmd,
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (r.stdout or "") + (r.stderr or ""), r.returncode


if not os.path.isfile(GEN):
    print("WINDOW FAULT: %s is already absent at the START of this run." % GEN)
    print("WINDOW FAULT: refusing to treat an absence as the clean baseline - if a previous run was")
    print("WINDOW FAULT: interrupted inside the window, restore it first, e.g. move back %s" % KEEP)
    sys.exit(3)
pre = sha(GEN)
pre_len = bytes_of(GEN)
carriers_pre = stale_carriers()
if os.path.isfile(KEEP):
    os.remove(KEEP)
moved = False
try:
    os.replace(GEN, KEEP)
    moved = True
    out_a, rc_a = call([SELF])
    out_b, rc_b = call([GEN, "--no-write"])
    carriers_mid = stale_carriers()
finally:
    if moved:
        shutil.move(KEEP, GEN)

post = sha(GEN)
post_len = bytes_of(GEN)
try:
    py_compile.compile(GEN, doraise=True, cfile=os.path.join("artifacts", "tmp", "_red007", "c.pyc"))
    parses = True
except py_compile.PyCompileError as exc:
    parses = False
    print("WINDOW FAULT: the restored generator does not parse: %s" % exc)
carriers_post = stale_carriers()

print("A absent selftest rc=%d" % rc_a)
print("A names the missing generator: %s" % ("gen_parity_ledger.py not present next to this script" in out_a))
print("A does not report a case mismatch: %s" % ("CASE " not in out_a))
print("A prints its own exit-code line: %s" % out_a.strip().endswith("EXITCODE=3"))
print("B absent generator rc=%d" % rc_b)
print("B python says it cannot open the file: %s" % ("can't open file" in out_b or "No such file or directory" in out_b))
print("C stale-token carriers before=%d aside=%d after=%d"
      % (len(carriers_pre), len(carriers_mid), len(carriers_post)))
print("C carriers aside: %s" % carriers_mid)
print("C generator was one of the before carriers: %s" % ("gen_parity_ledger.py" in carriers_pre))
print("C aside carriers are asserters only: %s"
      % all(n.endswith("_selftest.py") for n in carriers_mid))
print("RESTORE equals_pre=%s equals_after_len=%d/%d parses=%s"
      % (post == pre, post_len, pre_len, parses))

if rc_a != 3:
    print("FAIL the whole selftest must fall to tier 3 (instrument fault) without the generator;"
          " rc=%d means the harness failed for some other reason and the RED proves nothing" % rc_a); bad = 1
if "gen_parity_ledger.py not present next to this script" not in out_a:
    print("FAIL tier 3 must name WHICH instrument is missing"); bad = 1
if "CASE " in out_a:
    print("FAIL no case may run once the instrument is missing - a partial run would mix the two"
          " conclusions (harness broken vs case failed)"); bad = 1
if not out_a.strip().endswith("EXITCODE=3"):
    print("FAIL the absent-instrument run must print its own EXITCODE=3 line, not just return 3"); bad = 1
if rc_b == 0:
    print("FAIL the generator must not be runnable while moved aside"); bad = 1
if len(carriers_mid) != len(carriers_pre) - 1 or "gen_parity_ledger.py" in carriers_mid:
    print("FAIL removing the generator must drop exactly one carrier (before=%s aside=%s) - if it drops"
          " none, the token never lived in this slice's file and the RED reading is about something"
          " else" % (carriers_pre, carriers_mid)); bad = 1
if not all(n.endswith("_selftest.py") for n in carriers_mid):
    print("FAIL a non-selftest file still carries the token while the generator is aside (%s): such a file"
          " could emit staleness without this slice, so 'the guard did not exist before' would be false"
          % carriers_mid); bad = 1
if carriers_post != carriers_pre:
    print("FAIL after the window the carrier census must return to its start (before=%s after=%s)"
          % (carriers_pre, carriers_post)); bad = 1
if post != pre or post_len != pre_len or not parses:
    print("FAIL the window did not restore the instrument byte-for-byte"); bad = 1
print("WINDOW ok" if not bad else "WINDOW FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="A absent selftest rc=3|A names the missing generator: True|A does not report a case mismatch: True|A prints its own exit-code line: True|B absent generator rc=2|C stale-token carriers before=2 aside=1 after=2|C aside carriers are asserters only: True|C generator was one of the before carriers: True|RESTORE equals_pre=True|WINDOW ok"
run 2 0 "instrument absent for real: same fixture falls to tier 3 and names it, the staleness token loses its only emitter, restore is byte-exact" \
  python -X utf8 "$TMP/window007.py"

# ---- block 03: fingerprints identical after the window --------------------------------------
cat > "$TMP/postcheck.py" <<'PY'
"""块 02 自称按字节还原了——这里由**读产物而不是读它的话**独立复核。"""
import hashlib, os, sys
WATCH = [".trae/scripts/dev/gen_parity_ledger.py", ".trae/scripts/dev/parity_ledger_selftest.py",
         ".trae/scripts/dev/gen_screen_inventory.py", "artifacts/parity/ledger.md"]
rec = {}
for line in open(os.path.join("artifacts", "tmp", "_red007", "pre-hashes.txt"), encoding="utf-8"):
    k, v = line.strip().split(" ")
    rec[k] = v
bad = 0
for p in WATCH:
    now = hashlib.sha256(open(p, "rb").read()).hexdigest()
    was = rec.get(p)
    if was is None:
        print("FAIL no pre-window fingerprint recorded for %s - the comparison would be vacuous" % p)
        bad = 1
        continue
    print("%-58s same=%s" % (p, now == was))
    if now != was:
        bad = 1
if os.path.isfile(os.path.join("artifacts", "tmp", "_red007", "gen-aside.py")):
    print("FAIL residue left inside the window (gen-aside.py still exists)"); bad = 1
print("POST ok" if not bad else "POST FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="same=True|POST ok"
run 3 0 "the working tree is exactly what the window started with, read back from bytes not from the driver's own claim" \
  python -X utf8 "$TMP/postcheck.py"

# ---- block 04: control arm, same fixture with the instrument in place ----------------------
cat > "$TMP/control007.py" <<'PY'
"""对照臂：仪器在场时同一套夹具判 0。用例数只回读产物，不钉进本脚本——钉住它会让下一次加用例
就必须来改 RED 证据，而那是两个无关的动作。

「仪器崩」的信号是 INSTRUMENT FAULT 行，不是 rc=3：本套夹具里三条负向用例**设计上**就落在
tier 3（缺输入 / 非 JSON / 缺档案），拿 rc=3 当失败信号会把正常覆盖判成故障。
"""
import re, subprocess, sys
SELF = ".trae/scripts/dev/parity_ledger_selftest.py"
r = subprocess.run([sys.executable, "-X", "utf8", SELF],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
stdout = r.stdout or ""
err = r.stderr or ""
out = stdout + err
m = re.search(r"cases executed=(\d+) expected=(\d+) failures=(\d+)", out)
if not m:
    print("CONTROL FAULT: the selftest printed no 'cases executed=… expected=… failures=…' line;"
          " without it the run cannot be counted at all"); sys.exit(1)
executed, expected, failures = (int(x) for x in m.groups())
wanted3 = len(re.findall(r"CASE \S+\s+rc=\d+ want=3", out))
faults = len([l for l in out.splitlines() if l.startswith("INSTRUMENT FAULT")])
print("CONTROL executed=%d expected=%d equal=%s failures=%d tier3-by-design=%d"
      % (executed, expected, executed == expected, failures, wanted3))
print("CONTROL verdict=%s last-line-exitcode0=%s instrument-fault-lines=%d rc=%d"
      % ("VERDICT: PASS" in out, stdout.strip().endswith("EXITCODE=0"), faults, r.returncode))
bad = 0
if r.returncode != 0:
    print("FAIL the control arm must be green with the instrument in place - if it is red then"
          " block 02's tier 3 is not attributable to the absence"); bad = 1
if executed != expected or failures != 0:
    print("FAIL every registered case must run and pass in the control arm"); bad = 1
if not stdout.strip().endswith("EXITCODE=0"):
    print("FAIL the selftest must print its own EXITCODE=0 as the last stdout line, and stderr must"
          " not carry anything the reader would miss: stderr bytes=%d" % len(err)); bad = 1
if faults:
    print("FAIL %d INSTRUMENT FAULT line(s) in the control arm - that is the harness breaking,"
          " which is the conclusion block 02 is supposed to be about" % faults); bad = 1
if "VERDICT: PASS" not in out:
    print("FAIL the control arm must print VERDICT: PASS"); bad = 1
print("CONTROL ok" if not bad else "CONTROL FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="CONTROL ok|equal=True|failures=0|instrument-fault-lines=0|rc=0"
run 4 0 "control arm: the same fixture returns 0 with every case running and none on tier 3, so block 02's tier 3 was the absence and nothing else" \
  python -X utf8 "$TMP/control007.py"

# ---- block 05: prior-product state, printed not judged --------------------------------------
cat > "$TMP/no_history.py" <<'PY'
"""007 产物在 git 里的状态：**只打印，不判定**。

原先这块判「三个产物 commits=0 且未跟踪」，用来支撑「以前没有台账」。台账片进入历史之后，该判据
按定义转红，而转红的原因不是缺陷复发，是时效已到——AGENTS.md 2026-10-16 [evidence] 已把这类写成
「时效性状态只许打印、不得当判据」。RED 真正要求的「被判生成器真的会缺席」由块 02/03 的
移开—还原实验承担：它与提交状态无关，任何一次克隆上都能重跑。读数保留在这里是为了让读者看见本片
产物各有几条历史，不参与总退出码。哪一次提交带入的：git log --oneline -- artifacts/parity/ledger.md
"""
import subprocess, sys
PATHS = [".trae/scripts/dev/gen_parity_ledger.py", ".trae/scripts/dev/parity_ledger_selftest.py",
         "artifacts/parity/ledger.md"]
for p in PATHS:
    r = subprocess.run(["git", "-c", "core.quotepath=false", "log", "--oneline", "--", p],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    commits = len([l for l in (r.stdout or "").splitlines() if l.strip()])
    t = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files", "--error-unmatch", p],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    print("info %s commits=%d tracked=%s (printed not judged - blocks 2/3 give the"
          " commit-independent reproduction)" % (p, commits, t.returncode == 0))
print("NOHISTORY printed (no judgement here)")
PY
WANT_TEXT="info .trae/scripts/dev/gen_parity_ledger.py commits=|info .trae/scripts/dev/parity_ledger_selftest.py commits=|info artifacts/parity/ledger.md commits=|NOHISTORY printed"
run 5 0 "the git state of this slice's artefacts is measured and printed without being judged, because \
the pre-commit reading is time-expired by construction while blocks 2/3 give the commit-independent \
reproduction path" \
  python -X utf8 "$TMP/no_history.py"

# ---- block 06: the task's own declared size band is visible to the gate ---------------------
cat > "$TMP/contract007.py" <<'PY'
"""E1 的教训用在 007 自己身上：申报值必须让规模带出现在门禁里，且仓库总数只打印不比对。"""
import re, subprocess, sys
SLUG = "parity-ledger-generator-007"
r = subprocess.run([sys.executable, "-X", "utf8", ".trae/scripts/validate_tasks.py"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
out = (r.stdout or "") + (r.stderr or "")
mine = [l for l in out.splitlines() if SLUG in l]
passes = len([l for l in mine if l.startswith("PASS ")])
warn = [l for l in mine if l.startswith("WARNING ")]
size = len([l for l in warn if "exceeds soft limit 200" in l])
other = len([l for l in warn if "exceeds soft limit 200" not in l])
errs = len([l for l in mine if l.startswith("ERROR ")])
m = re.search(r"errors=(\d+) warnings=(\d+)", out)
print("this task: PASS count=%d WARNING count=%d (size band=%d, other=%d) ERROR count=%d"
      % (passes, len(warn), size, other, errs))
for l in mine:
    print("  line: " + l)
print("note: repo-wide readings are errors=%s warnings=%s and are NOT judged here (see 004 block 27's"
      " same-round correction)." % (m.groups() if m else ("?", "?")))
bad = 0
if (passes, size, other, errs) != (1, 1, 0, 0):
    print("FAIL 007 must sit at exactly one PASS and exactly one size-band WARNING - a hidden declared"
          " value is the E1 defect and more than one means this slice warns for something else"); bad = 1
if not m:
    print("FAIL no parsable SUMMARY line - 'cannot read the baseline' is not 'the baseline is fine'"); bad = 1
print("CONTRACT ok" if not bad else "CONTRACT FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="this task: PASS count=1 WARNING count=1 (size band=1, other=0) ERROR count=0|repo-wide readings are errors=|CONTRACT ok"
run 6 0 "task contract: 007's declared judged value shows up as its own size-band warning; repo totals printed not pinned" \
  python -X utf8 "$TMP/contract007.py"

# ---- block 07: encoding gate, self-probed both ways ---------------------------------------
cat > "$TMP/utf8_gate.py" <<'PY'
"""闸门自己正反自检：一条已知坏字节串必须被拒（正控），四个本片文件必须通过（负控）。"""
import io, sys
broken = bytes([107, 97, 169, 102, 101])
try:
    broken.decode("utf-8")
    print("GATE SELFTEST FAIL: the decoder accepted a known-broken byte string"); sys.exit(3)
except UnicodeDecodeError:
    print("GATE SELFTEST ok: a known-broken byte string is rejected")
bad = 0
for p in sys.argv[1:]:
    raw = open(p, "rb").read()
    try:
        raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        print("FAIL %s is not valid UTF-8 (%s)" % (p, exc)); bad += 1; continue
    if bytes([239, 191, 189]) in raw:
        print("FAIL %s contains U+FFFD replacement characters" % p); bad += 1; continue
    print("ok   %-58s bytes=%d" % (p, len(raw)))
print("gate covered %d file(s)" % len(sys.argv[1:]))
print("EXITCODE=%d" % (1 if bad else 0))
sys.exit(1 if bad else 0)
PY
WANT_TEXT="GATE SELFTEST ok|EXITCODE=0|gate covered"
run 7 0 "encoding gate over the instruments this slice adds and this generator's own source, self-probed both ways" \
  python -X utf8 "$TMP/utf8_gate.py" "$GEN" "$SELF" "$INV" "$LEDGER" \
  artifacts/tmp/gen-007-red.sh .trae/tasks/T-2026-10-15-parity-ledger-generator-007.md

# --- derived total ---------------------------------------------------------------------------
longest=$(python -X utf8 -c "
import os, sys
best = 0
for base, _d, fs in os.walk(sys.argv[1]):
    for f in fs:
        best = max(best, len(os.path.join(base, f)))
print(best)
" "$TMP")
if [ "$longest" -gt 200 ]; then
  echo "PATH FAULT: scratch longest path ${longest} chars is too close to the Windows 260 limit" | tee -a "$CONSOLE"
  bad=$((bad + 1))
fi
echo "scratch longest path = ${longest} chars (limit 260, margin $((260 - longest)))" | tee -a "$CONSOLE"

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

tail_nonascii=$(python -X utf8 -c "
import sys
raw = open(sys.argv[1], 'rb').read()
marker = bytes([61, 61, 61, 61, 61]) + b' TOTAL (derived, ASCII-only tail) ====='
i = raw.rfind(marker)
if i < 0:
    print('TAIL MEASURE FAULT: marker not found'); sys.exit(0)
seg = raw[i:]
print('ascii tail: bytes=%d non-ascii=%d (must be 0)' % (len(seg), sum(1 for b in seg if b > 127)))
" "$EV" 2>&1)
echo "$tail_nonascii" >> "$EV"
case "$tail_nonascii" in
  *"non-ascii=0"*) ;;
  *) bad=$((bad + 1)); echo "ASCII TAIL VIOLATION: ${tail_nonascii}" | tee -a "$CONSOLE" ;;
esac

if [ "$bad" != "0" ]; then final_rc=1; else final_rc=0; fi
echo "OVERALL_EXITCODE=${final_rc}" >> "$EV"

decode=$(python -X utf8 -c "
import sys
raw = open(sys.argv[1], 'rb').read()
try:
    raw.decode('utf-8')
except UnicodeDecodeError as exc:
    print('DECODE FAIL', exc); sys.exit(1)
cr = raw.count(bytes([13]))
if cr:
    print('DECODE FAIL line-ending: %d carriage return(s) present in an artefact pinned to LF' % cr)
    sys.exit(1)
print('DECODE ok utf8=True cr=0 (size: wc -c this file)')
" "$EV" 2>&1)
decode_rc=$?
echo "$decode" | tee -a "$EV" >> "$CONSOLE"
if [ "$decode_rc" != "0" ]; then
  echo "OVERALL OVERRIDDEN BY DECODE FAILURE" >> "$CONSOLE"
  final_rc=1
fi
exit "$final_rc"
