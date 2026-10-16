#!/usr/bin/env bash
# T-2026-10-15-005 RED evidence generator (one-off; lives in artifacts/tmp by convention).
# Writes artifacts/evidence/T-2026-10-15-005-red.txt.
# 本片交付的是「屏幕与浮层清单生成器」。RED 要证的是三件事，都不许靠叙述：
#   1) 把仪器真的取走，同一套夹具必须落在 3 这一层并说出缺的是哪个仪器（块 02）；
#   2) 收在位之前它落的是 1 —— 与「某条判据不成立」同一个层，因此当时「harness 崩了」读起来
#      像「覆盖判据红了」。块 03 用同深度副本把两种 rc=1 各跑一遍，正面量出这次碰撞；
#   3) 007 的台账生成器与自检是 005 仪器的消费者：仪器不在场时两处都必须判 3 且同一口径（块 02 D）。
# 起点不在场即拒绝注入并判 3，所以被打断的残留不可能被当成本次的干净基线（AGENTS.md 2026-10-15 那条）。
# 规则沿用：每块自带命令行与自己的退出码；总退出码由 want/got 表派生，不写死 0；每块带内容见证；
# MIN_BLOCKS 是与本文件自身源码比对的字面整数；指纹类值只写复算命令不写进正文；
# 编码闸门之后的正文一律 ASCII-only 且实测 non-ascii=0。
set -uo pipefail

EV=artifacts/evidence/T-2026-10-15-005-red.txt
TMP=artifacts/tmp/_red005
CONSOLE=artifacts/tmp/_red005-console.txt
GEN=.trae/scripts/dev/gen_screen_inventory.py
SELF=.trae/scripts/dev/screen_inventory_selftest.py
LEDGER=.trae/scripts/dev/gen_parity_ledger.py
LSELF=.trae/scripts/dev/parity_ledger_selftest.py
INVDATA=Docs/04-ux-design/parity/screen-inventory.json
mkdir -p "$TMP"
rm -f "$EV" "$CONSOLE"

if [ ! -d .git ]; then
  echo "FATAL: run this script from the repository root (cwd=$(pwd))"
  exit 3
fi
for f in "$GEN" "$SELF" "$LEDGER" "$LSELF" "$INVDATA"; do
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
python -X utf8 -c "$FP" "$GEN" "$SELF" "$LEDGER" "$LSELF" "$INVDATA" > "$PROD" 2>&1
prod_rc=$?

{
  echo "# T-2026-10-15-005 RED - 屏幕清单生成器收编前的失败证据"
  echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)  (UTC)"
  echo "# Generator: artifacts/tmp/gen-005-red.sh   重跑：bash artifacts/tmp/gen-005-red.sh"
  echo "# 本文件覆盖的调用形态："
  echo "#   python -X utf8 $SELF                  （正反自检全套；用例数由产物自己打印，本脚本不钉数）"
  echo "#   python -X utf8 $GEN --no-write        （清单生成器本体）"
  echo "#   python -X utf8 $TMP/window005.py      （块 02：单进程挪开-读数-还原窗口）"
  echo "#   python -X utf8 $TMP/revert005.py      （块 03：同深度副本的退出码层碰撞量测）"
  echo "# 判据面：块 01/04/05/06 全程不动工作树；块 02 只在一个进程的 try/finally 内挪仪器，"
  echo "#   起点不在场即拒绝；块 03 只写 artifacts/tmp/_r005 下的副本，生产字节只读不写。"
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
  local full="$TMP/block-$n.out"
  printf '%s\n' "$out" > "$full"
  printf '%s\n' "$out" | tail -n 22 >> "$EV"
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
"""RED 的前提：本片只加工具，android/ 一字节未动；且四份仪器与清单在判定前后字节相同。"""
import hashlib, os, subprocess, sys
WATCH = [".trae/scripts/dev/gen_screen_inventory.py",
         ".trae/scripts/dev/screen_inventory_selftest.py",
         ".trae/scripts/dev/gen_parity_ledger.py",
         ".trae/scripts/dev/parity_ledger_selftest.py",
         "Docs/04-ux-design/parity/screen-inventory.json"]
bad = 0
r = subprocess.run(["git", "-c", "core.quotepath=false", "diff", "--name-only", "--", "android"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
touched = [l for l in (r.stdout or "").splitlines() if l.strip()]
print("android files changed by the working tree = %d" % len(touched))
if touched:
    print("FAIL 005 must not touch android/ - %s" % touched); bad = 1
before = {}
for p in WATCH:
    if not os.path.isfile(p):
        print("FAIL %s is absent before the window - the comparison would have no baseline" % p)
        bad = 1
        continue
    before[p] = hashlib.sha256(open(p, "rb").read()).hexdigest()
with open(os.path.join("artifacts", "tmp", "_red005", "pre-hashes.txt"), "w",
          encoding="utf-8") as fh:
    fh.write("\n".join("%s %s" % (k, v) for k, v in sorted(before.items())))
print("recorded %d pre-window fingerprint(s)" % len(before))
print("PRE ok" if not bad else "PRE FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="android files changed by the working tree = 0|recorded 5 pre-window fingerprint(s)|PRE ok"
run 1 0 "keep-behaviour premise: no android/ file is touched and the instruments' pre-window bytes are recorded" \
  python -X utf8 "$TMP/precheck.py"

# ---- block 02: the instrument really removed, inside one process -----------------------------
cat > "$TMP/window005.py" <<'PY'
"""把 005 的仪器真的取走，在同一个进程的窗口里读四组「不在场」证据，finally 里按字节还原。

四条读数各自的用处：
  A 005 的正反自检全套在仪器不在场时必须落在 3 这一层（而不是 1），并且要说出缺的是哪个仪器、
    一条 CASE 都不许跑——半个套件跑起来就会把「harness 起不来」和「判据不成立」混成一份输出。
  B 生成器本体不可调用时 Python 自己报 2，读者据此知道「跑不起来」与「跑起来判红」是两回事。
  C 判据令牌 FLOOR screens>= 的持有文件在窗口里必须少掉生成器那一份，剩下的每一份都只是「断言
    这个令牌」的自检而非「发出这个令牌」的正文——A 已经证明那份自检在仪器不在场时根本跑不起来。
  D 消费者侧两条入口（007 的台账生成器与它的自检）在同一缺失下必须给出同一个层 3 和同一口径，
    且不得吐出台账——否则同一缺陷会有两种通道，正是本仓库反复踩过的「同一条规则写在两个入口」。
还原判据：起点必须在场（不在场即拒绝，注入残留永不被当成本次基线），还原后 sha256 与长度都必须
与起点相同，且文件能被 py_compile 解析；令牌持有文件集合也必须回到起点。
"""
import hashlib, os, py_compile, re, shutil, subprocess, sys

GEN = ".trae/scripts/dev/gen_screen_inventory.py"
SELF = ".trae/scripts/dev/screen_inventory_selftest.py"
LEDGER = ".trae/scripts/dev/gen_parity_ledger.py"
LSELF = ".trae/scripts/dev/parity_ledger_selftest.py"
KEEP = os.path.join("artifacts", "tmp", "_red005", "gen-aside.py")
TOKEN = "FLOOR screens>="
DEV = ".trae/scripts/dev"
BASE = os.path.join("artifacts", "tmp", "_red005")

bad = 0


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def carriers():
    hits = []
    for name in sorted(os.listdir(DEV)):
        if not name.endswith(".py"):
            continue
        p = os.path.join(DEV, name)
        try:
            if TOKEN in open(p, encoding="utf-8", errors="replace").read():
                hits.append(name)
        except OSError:
            hits.append("UNREADABLE:" + name)
    return hits


def call(cmd):
    r = subprocess.run([sys.executable, "-X", "utf8"] + cmd,
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (r.stdout or "") + (r.stderr or ""), r.returncode, r.stdout or ""


if not os.path.isfile(GEN):
    print("WINDOW FAULT: %s is already absent at the START of this run." % GEN)
    print("WINDOW FAULT: refusing to treat an absence as the clean baseline - if a previous run was")
    print("WINDOW FAULT: interrupted inside the window, restore it first, e.g. move back %s" % KEEP)
    sys.exit(3)
pre = sha(GEN)
pre_len = len(open(GEN, "rb").read())
carriers_pre = carriers()
if os.path.isfile(KEEP):
    os.remove(KEEP)
moved = False
try:
    os.replace(GEN, KEEP)
    moved = True
    out_a, rc_a, so_a = call([SELF])
    out_b, rc_b, so_b = call([GEN, "--no-write"])
    out_c, rc_c, so_c = call([LEDGER, "--no-write"])
    out_d, rc_d, so_d = call([LSELF])
    carriers_mid = carriers()
finally:
    if moved:
        shutil.move(KEEP, GEN)

post = sha(GEN)
post_len = len(open(GEN, "rb").read())
try:
    py_compile.compile(GEN, doraise=True,
                       cfile=os.path.join(BASE, "c.pyc"))
    parses = True
except py_compile.PyCompileError as exc:
    parses = False
    print("WINDOW FAULT: the restored instrument does not parse: %s" % exc)
carriers_post = carriers()

faults_a = len([l for l in out_a.splitlines() if l.startswith("INSTRUMENT FAULT")])
faults_c = len([l for l in out_c.splitlines() if l.startswith("INSTRUMENT FAULT")])
faults_d = len([l for l in out_d.splitlines() if l.startswith("INSTRUMENT FAULT")])
print("A absent selftest rc=%d" % rc_a)
print("A names the missing instrument: %s"
      % ("gen_screen_inventory.py is not importable next to this script" in out_a))
print("A does not report a case mismatch: %s" % ("CASE " not in out_a))
print("A prints its own exit-code line: %s" % so_a.strip().endswith("EXITCODE=3"))
print("A instrument-fault lines=%d" % faults_a)
print("A fault reading=%s" % next((l for l in out_a.splitlines()
                                  if l.startswith("INSTRUMENT FAULT")), "(no fault line)"))
print("B absent generator rc=%d" % rc_b)
print("B python says it cannot open the file: %s"
      % ("can't open file" in out_b or "No such file or directory" in out_b))
print("C token carriers before=%d aside=%d after=%d"
      % (len(carriers_pre), len(carriers_mid), len(carriers_post)))
print("C carriers aside: %s" % carriers_mid)
print("C generator was one of the before carriers: %s" % ("gen_screen_inventory.py" in carriers_pre))
print("C aside carriers are asserters only: %s"
      % all(n.endswith("_selftest.py") for n in carriers_mid))
print("D ledger generator rc=%d fault=%s names the sibling: %s no ledger printed: %s"
      % (rc_c, faults_c == 1, "could not load the inventory instrument" in out_c,
         "LEDGER" not in so_c))
print("D ledger selftest rc=%d fault=%s names the sibling: %s no CASE lines: %s"
      % (rc_d, faults_d == 1, "gen_screen_inventory.py is not importable" in out_d,
         "CASE " not in out_d))
print("D same tier at both consumer entries: %s" % (rc_c == rc_d == 3))
print("D same tier as the 005 selftest: %s" % (rc_a == rc_c == rc_d == 3))
print("RESTORE equals_pre=%s equals_after_len=%d/%d parses=%s"
      % (post == pre, post_len, pre_len, parses))

if rc_a != 3:
    print("FAIL the whole selftest must fall to tier 3 (instrument fault) without the instrument;"
          " rc=%d means the harness failed for some other reason and the RED proves nothing" % rc_a)
    bad = 1
if "gen_screen_inventory.py is not importable next to this script" not in out_a:
    print("FAIL tier 3 must name WHICH instrument is missing"); bad = 1
if "CASE " in out_a:
    print("FAIL no case may run once the instrument is missing - a partial run would mix the two"
          " conclusions (harness broken vs case failed)"); bad = 1
if not so_a.strip().endswith("EXITCODE=3"):
    print("FAIL the absent-instrument run must print its own EXITCODE=3 line, not just return 3")
    bad = 1
if faults_a != 1:
    print("FAIL expected exactly one INSTRUMENT FAULT line at entry A, got %d" % faults_a); bad = 1
if rc_b == 0:
    print("FAIL the generator must not be runnable while moved aside"); bad = 1
if len(carriers_mid) != len(carriers_pre) - 1 or "gen_screen_inventory.py" in carriers_mid:
    print("FAIL removing the instrument must drop exactly one carrier (before=%s aside=%s) - if it"
          " drops none, the token never lived in this slice's file and the RED reading is about"
          " something else" % (carriers_pre, carriers_mid))
    bad = 1
if not all(n.endswith("_selftest.py") for n in carriers_mid):
    print("FAIL a non-selftest file still carries the token while the instrument is aside (%s):"
          " such a file could emit the floor line without this slice, so 'the guard did not exist"
          " before' would be false" % carriers_mid)
    bad = 1
if carriers_post != carriers_pre:
    print("FAIL after the window the carrier census must return to its start (before=%s after=%s)"
          % (carriers_pre, carriers_post))
    bad = 1
if rc_c != 3 or rc_d != 3 or rc_a != 3:
    print("FAIL the same absence has to give the same tier at all three entries (A=%d ledger=%d"
          " ledger-selftest=%d) - two channels for one rule is the failure family this repo records"
          % (rc_a, rc_c, rc_d))
    bad = 1
if "could not load the inventory instrument" not in out_c:
    print("FAIL the ledger generator must say WHICH instrument it could not load"); bad = 1
if "LEDGER" in so_c:
    print("FAIL the ledger generator printed a ledger while unable to re-derive the inventory -"
          " that is a coverage claim built on nothing"); bad = 1
if faults_c != 1 or faults_d != 1:
    print("FAIL each consumer entry must print exactly one INSTRUMENT FAULT line (ledger=%d"
          " selftest=%d)" % (faults_c, faults_d))
    bad = 1
if "CASE " in out_d:
    print("FAIL the ledger selftest ran cases while the 005 instrument was missing; its fixture"
          " builder imports that module, so any case line would be a partial run"); bad = 1
if post != pre or post_len != pre_len or not parses:
    print("FAIL the window did not restore the instrument byte-for-byte"); bad = 1
print("WINDOW ok" if not bad else "WINDOW FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="A absent selftest rc=3|A names the missing instrument: True|A does not report a case mismatch: True|A prints its own exit-code line: True|A instrument-fault lines=1|A fault reading=INSTRUMENT FAULT: gen_screen_inventory.py is not importable next to this script (|B absent generator rc=2|B python says it cannot open the file: True|C token carriers before=2 aside=1 after=2|C aside carriers are asserters only: True|D ledger generator rc=3 fault=True names the sibling: True no ledger printed: True|D ledger selftest rc=3 fault=True names the sibling: True no CASE lines: True|D same tier at both consumer entries: True|D same tier as the 005 selftest: True|RESTORE equals_pre=True|WINDOW ok"
run 2 0 "instrument absent for real: tier 3 at three entries with one shared channel, the floor token loses its only emitter, restore is byte-exact" \
  python -X utf8 "$TMP/window005.py"

# ---- block 03: exit-tier collision, measured on same-depth copies ---------------------------
cat > "$TMP/revert005.py" <<'PY'
"""量出「没有守卫时」的退出码层碰撞，而不是只用文字声称它存在。

三个同深度副本（都镜像 .trae/scripts/dev 的相对路径，这样副本自己算出的仓库根仍在仓库内）：
  R1 只有自检、守卫被还原成裸 import，仪器不在场 -> 未捕获 ImportError。
  R2 自检与仪器都在场，但把 neg-screens-below-floor 的一条判据人为改成恒 False -> 一条用例不成立。
  R3 只有自检、守卫保持现状，仪器不在场 -> 生产形态。
判据：R1 与 R2 的退出码必须**相同**（都是 1）。这正是碰撞本身——同一层数字对应「harness 起不来」
和「判据不成立」两种互斥结论，只有靠 stdout 有没有 CASE 行才能分辨；块 02 的 A 因此钉住「无 CASE 行」。
R3 必须与它们不同（3）。生产字节全程只读；副本写在 artifacts/tmp/_r005 下。
"""
import hashlib, os, re, shutil, subprocess, sys

DEV = os.path.join(".trae", "scripts", "dev")
SELF_REL = os.path.join(DEV, "screen_inventory_selftest.py")
GEN_REL = os.path.join(DEV, "gen_screen_inventory.py")
ROOT = os.path.join("artifacts", "tmp", "_r005")
GUARD = re.compile(rb"try:\n    import gen_screen_inventory as G\n"
                   rb"except ImportError as exc:.*?\n    sys\.exit\(3\)\n", re.S)
INJECT = b'("floor says ok=False", "ok=False" in out)'


def w(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)


def run_copy(script, extra=()):
    r = subprocess.run([sys.executable, "-X", "utf8", script] + list(extra),
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (r.stdout or ""), (r.stderr or ""), r.returncode


if os.path.isdir(ROOT):
    shutil.rmtree(ROOT)
self_bytes = open(os.path.join(".", SELF_REL), "rb").read()
gen_bytes = open(os.path.join(".", GEN_REL), "rb").read()

hits = len(GUARD.findall(self_bytes))
print("guard anchor hits in the production selftest=%d" % hits)
if hits != 1:
    print("FAIL the guard must be locatable by exactly one anchor (got %d); a second guard elsewhere"
          " would make this control describe a different file" % hits)
    sys.exit(3)
reverted = GUARD.sub(b"import gen_screen_inventory as G\n", self_bytes, count=1)
if b"INSTRUMENT FAULT" in reverted.split(b"CASES = []")[0]:
    print("FAIL the revert left an INSTRUMENT FAULT token in the import region - the copy would not"
          " be the pre-fix shape")
    sys.exit(3)
if reverted.count(self_bytes) == 1:
    print("FAIL the revert changed nothing; the anchor matched but the substitution was a no-op")
    sys.exit(3)
print("reverted bytes=%d production bytes=%d differ=%s"
      % (len(reverted), len(self_bytes), reverted != self_bytes))

r1 = os.path.join(ROOT, "no-guard", ".trae", "scripts", "dev", "screen_inventory_selftest.py")
w(r1, reverted)
so1, se1, rc1 = run_copy(r1)

r2dir = os.path.join(ROOT, "case-mismatch", ".trae", "scripts", "dev")
r2 = os.path.join(r2dir, "screen_inventory_selftest.py")
if self_bytes.count(INJECT) != 1:
    print("FAIL the injected anchor occurs %d time(s), expected exactly 1"
          % self_bytes.count(INJECT))
    sys.exit(3)
w(r2, self_bytes.replace(INJECT, b'("floor says ok=False", False)'))
w(os.path.join(r2dir, "gen_screen_inventory.py"), gen_bytes)
so2, se2, rc2 = run_copy(r2)
failing2 = sorted({m.group(1) for m in
                   re.finditer(r"^CASE (\S+)\s+rc=\d+ want=\d+ FAIL", so2, re.M)})

r3 = os.path.join(ROOT, "with-guard", ".trae", "scripts", "dev", "screen_inventory_selftest.py")
w(r3, self_bytes)
so3, se3, rc3 = run_copy(r3)

print("R1 no guard, instrument absent: rc=%d stdout-bytes=%d ModuleNotFoundError=%s"
      % (rc1, len(so1), "ModuleNotFoundError" in se1))
print("R1 has no CASE line=%s no EXITCODE line=%s" % ("CASE " not in so1,
                                                     "EXITCODE=" not in so1))
print("R2 one judgement forced false: rc=%d failing=%s" % (rc2, failing2))
print("R3 guard as shipped: rc=%d names the instrument=%s no CASE line=%s ends EXITCODE=3=%s"
      % (rc3, "gen_screen_inventory.py is not importable" in so3, "CASE " not in so3,
         so3.strip().endswith("EXITCODE=3")))
print("COLLISION rc(R1)==rc(R2) is %s - both are the tier a case mismatch uses"
      % (rc1 == rc2))
print("RESOLVED rc(R3)!=rc(R1) is %s, rc(R3)=%d" % (rc3 != rc1, rc3))

bad = 0
if rc1 != 1 or "ModuleNotFoundError" not in se1 or so1.strip():
    print("FAIL R1 must reproduce the pre-fix shape: an uncaught import error and no judgement line"
          " at all (rc=%d stdout-bytes=%d)" % (rc1, len(so1)))
    bad = 1
if rc2 != 1:
    print("FAIL R2 must be the case-mismatch tier, measured not asserted (rc=%d)" % rc2); bad = 1
if "neg-screens-below-floor" not in failing2:
    print("FAIL R2's injected false judgement did not turn its own case red - then R2 is not a"
          " case-mismatch witness and the collision comparison means nothing (%s)" % failing2)
    bad = 1
if "CASE " not in so2:
    print("FAIL R2 printed no CASE lines - it is not a suite run"); bad = 1
if rc1 != rc2:
    print("FAIL the collision is the point: R1 and R2 must land on the SAME tier (got %d and %d)"
          % (rc1, rc2))
    bad = 1
if rc3 != 3 or "gen_screen_inventory.py is not importable" not in so3 or "CASE " in so3:
    print("FAIL the shipped guard must answer the same absence with tier 3 and no partial run"
          " (rc=%d)" % rc3)
    bad = 1
if not so3.strip().endswith("EXITCODE=3"):
    print("FAIL the guarded absence must print its own EXITCODE=3 line"); bad = 1
now_self = hashlib.sha256(open(os.path.join(".", SELF_REL), "rb").read()).hexdigest()
now_gen = hashlib.sha256(open(os.path.join(".", GEN_REL), "rb").read()).hexdigest()
if now_self != hashlib.sha256(self_bytes).hexdigest():
    print("FAIL the control wrote into the production selftest"); bad = 1
if now_gen != hashlib.sha256(gen_bytes).hexdigest():
    print("FAIL the control wrote into the production instrument"); bad = 1
# 与**运行开始前**的指纹比，而不是只与本案自己刚读的字节比：块 02 的窗口就发生在本块之前，
# 若它没还原干净，只读当前字节是看不出来的（AGENTS.md 里「注入型夹具的基线必须独立于本次运行」）。
pre_fp = {}
for line in open(os.path.join("artifacts", "tmp", "_red005", "prod-before.txt"), encoding="utf-8"):
    mm = re.match(r"(\S+)\s+bytes=(\d+)\s+lines=(\d+)\s+sha256=([0-9a-f]{64})", line)
    if mm:
        pre_fp[mm.group(1)] = mm.group(4)
for rel, now in ((SELF_REL, now_self), (GEN_REL, now_gen)):
    key = rel.replace(os.sep, "/")
    was = pre_fp.get(key)
    print("pre-run fingerprint match %s -> %s" % (key, was == now))
    if was is None:
        print("FAIL no pre-run fingerprint for %s - the comparison would be vacuous" % key)
        bad = 1
    elif was != now:
        print("FAIL %s moved since this run started" % key); bad = 1
shutil.rmtree(ROOT, ignore_errors=True)
print("REVERT ok" if not bad else "REVERT FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="guard anchor hits in the production selftest=1|differ=True|R1 no guard, instrument absent: rc=1 stdout-bytes=0 ModuleNotFoundError=True|R1 has no CASE line=True no EXITCODE line=True|R2 one judgement forced false: rc=1|R3 guard as shipped: rc=3 names the instrument=True no CASE line=True|COLLISION rc(R1)==rc(R2) is True|RESOLVED rc(R3)!=rc(R1) is True, rc(R3)=3|pre-run fingerprint match .trae/scripts/dev/screen_inventory_selftest.py -> True|pre-run fingerprint match .trae/scripts/dev/gen_screen_inventory.py -> True|REVERT ok"
run 3 0 "measured exit-tier collision: the same absence without the guard lands on the case-mismatch tier, byte-identical to a forced false judgement, while the shipped guard answers 3" \
  python -X utf8 "$TMP/revert005.py"

# ---- block 04: control arm, same fixture with the instrument in place ----------------------
cat > "$TMP/control005.py" <<'PY'
"""对照臂：仪器在场时同一套夹具判 0。用例数只回读产物，不钉进本脚本——钉住它会让下一次加用例
就必须来改 RED 证据，而那是两个无关的动作。

「仪器崩」的信号是 INSTRUMENT FAULT 行，不是 rc=3：本套夹具里五条负向用例**设计上**就落在
tier 3（缺屏幕包 / 缺 MainActivity / 非 UTF-8 输入 / 方法体不闭合 / 死 include），拿 rc=3 当失败
信号会把正常覆盖判成故障。
"""
import re, subprocess, sys
SELF = ".trae/scripts/dev/screen_inventory_selftest.py"
GEN = ".trae/scripts/dev/gen_screen_inventory.py"
r = subprocess.run([sys.executable, "-X", "utf8", SELF],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
stdout = r.stdout or ""
err = r.stderr or ""
out = stdout + err
m = re.search(r"cases executed=(\d+) expected=(\d+) failures=(\d+)", out)
if not m:
    print("CONTROL FAULT: the selftest printed no cases executed line; without it the run cannot be"
          " counted at all")
    sys.exit(1)
executed, expected, failures = (int(x) for x in m.groups())
tiers = sorted({int(x) for x in re.findall(r"CASE \S+\s+rc=(\d+) want=\d", out)})
wanted3 = len(re.findall(r"CASE \S+\s+rc=\d+ want=3", out))
faults = len([l for l in out.splitlines() if l.startswith("INSTRUMENT FAULT")])
print("CONTROL executed=%d expected=%d equal=%s failures=%d tier3-by-design=%d"
      % (executed, expected, executed == expected, failures, wanted3))
print("CONTROL tiers seen=%s instrument-fault-lines=%d verdict-pass=%s last-line-exitcode0=%s rc=%d"
      % (tiers, faults, "VERDICT: PASS" in out, stdout.strip().endswith("EXITCODE=0"),
         r.returncode))
g = subprocess.run([sys.executable, "-X", "utf8", GEN, "--no-write"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
gout = (g.stdout or "") + (g.stderr or "")
gm = re.search(r"MEASURED screens=(\d+) self_drawn=(\d+)", gout)
print("CONTROL generator rc=%d measured=%s spec-pointer line=%s floor ok=%s"
      % (g.returncode, gm.groups() if gm else None, "SPEC_POINTER cites=True" in gout,
         "ok=True" in gout))
bad = 0
if r.returncode != 0:
    print("FAIL the control arm must be green with the instrument in place - if it is red then"
          " block 02's tier 3 is not attributable to the absence")
    bad = 1
if executed != expected or failures != 0:
    print("FAIL every registered case must run and pass in the control arm"); bad = 1
if not {0, 1, 3} <= set(tiers):
    print("FAIL the suite must exercise tiers 0, 1 and 3 (got %s)" % tiers); bad = 1
if faults:
    print("FAIL %d INSTRUMENT FAULT line(s) in the control arm - that is the harness breaking,"
          " which is the conclusion block 02 is supposed to be about" % faults); bad = 1
if not stdout.strip().endswith("EXITCODE=0"):
    print("FAIL the selftest must print its own EXITCODE=0 as the last stdout line; stderr bytes=%d"
          % len(err))
    bad = 1
if g.returncode != 0 or not gm or "SPEC_POINTER cites=True" not in gout or "ok=True" not in gout:
    print("FAIL the generator must be runnable and must print the measured counts, the spec pointer"
          " judgement and the floor judgement in the same run")
    bad = 1
print("CONTROL ok" if not bad else "CONTROL FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="CONTROL executed=|equal=True|failures=0|tiers seen=[0, 1, 3]|instrument-fault-lines=0|verdict-pass=True|last-line-exitcode0=True|CONTROL generator rc=0|spec-pointer line=True|floor ok=True|CONTROL ok"
run 4 0 "control arm: the same fixture returns 0 with every case running, tiers 0/1/3 all reached, and the generator prints measured counts beside the spec-pointer judgement" \
  python -X utf8 "$TMP/control005.py"

# ---- block 05: prior-product state, printed not judged --------------------------------------
cat > "$TMP/no_history.py" <<'PY'
"""005 产物在 git 里的状态：**只打印，不判定**。

这一段原先是判据（commits=0 且未跟踪），自本片产物进入历史之后它按定义转红，而转红的原因不是缺陷
复发，是判据的时效已到——AGENTS.md 2026-10-16 [evidence] 把这类写成「时效性状态只许打印、不得
当判据」。哪一次提交把它们带进历史可回读：git log --oneline -- Docs/04-ux-design/parity/screen-inventory.json
RED 阶段真正要求「产物尚不存在」的那份证据，由块 2/3 的移开—还原实验承担：它把文件从
工作树挪走再原样放回，与提交状态无关，因此在任何一次克隆上都同样可复现。

这里保留读数，是为了让读者仍能看出本片产物**曾经**不存在、现在各有几条历史；数字本身不参与总退出码。
"""
import subprocess, sys
MINE = [".trae/scripts/dev/gen_screen_inventory.py",
        ".trae/scripts/dev/screen_inventory_selftest.py",
        "Docs/04-ux-design/parity/screen-inventory.json"]
INFO_ONLY = ["artifacts/parity/ledger.md"]
for p in MINE:
    r = subprocess.run(["git", "-c", "core.quotepath=false", "log", "--oneline", "--", p],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    commits = len([l for l in (r.stdout or "").splitlines() if l.strip()])
    t = subprocess.run(["git", "-c", "core.quotepath=false", "ls-files", "--error-unmatch", p],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    print("info %s commits=%d tracked=%s (printed not judged - see blocks 2/3 for the"
          " commit-independent reproduction)" % (p, commits, t.returncode == 0))
for p in INFO_ONLY:
    r = subprocess.run(["git", "-c", "core.quotepath=false", "log", "--oneline", "--", p],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    print("info %s commits=%d (007's artefact)"
          % (p, len([l for l in (r.stdout or "").splitlines() if l.strip()])))
print("NOHISTORY printed (no judgement here)")
PY
WANT_TEXT="info .trae/scripts/dev/gen_screen_inventory.py commits=|info .trae/scripts/dev/screen_inventory_selftest.py commits=|info Docs/04-ux-design/parity/screen-inventory.json commits=|NOHISTORY printed"
run 5 0 "the git state of this slice's artefacts is measured and printed without being judged, because \
the pre-commit reading is time-expired by construction while blocks 2/3 give the commit-independent \
reproduction path" \
  python -X utf8 "$TMP/no_history.py"

# ---- block 06: the task's own declared size band and error family ---------------------------
cat > "$TMP/contract005.py" <<'PY'
"""005 自己的门禁读数。两件事必须同时成立，且都不靠本脚本自称：
  front matter 的申报值让规模带出现在门禁里（E1 的教训：藏起申报值就等于没有申报）；本脚本不复述
  该数值，判据是「恰好一条规模告警」，申报值涨到多少都不该让它变绿或变红；
  本片没有「RED 记录之外」的契约错误——id、文件名、状态、依赖这类错误如果存在，说明任务文件本身
  不合格，而那不是填完 TDD 就能消掉的。
「RED 记录还没填」这一条原先也当判据，现在只打印：填完 RED/GREEN 记录之后重跑本脚本，该族错误数
按定义归零，转红的原因不是缺陷复发而是时效已到（AGENTS.md 2026-10-16 [evidence]）。它承担的原始
事实——RED 阶段任务文件除缺 TDD 记录外无其它契约错误——由 non-RED 那条判据保留。
仓库 validate_tasks 的总数只打印不比对（004 块 27 的同一轮教训：别片会把总数顶上去）。
"""
import re, subprocess, sys
SLUG = "screen-inventory-parity-ledger-005"
r = subprocess.run([sys.executable, "-X", "utf8", ".trae/scripts/validate_tasks.py"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
out = (r.stdout or "") + (r.stderr or "")
mine = [l for l in out.splitlines() if SLUG in l]
warn = [l for l in mine if l.startswith("WARNING ")]
size = [l for l in warn if "exceeds soft limit 200" in l]
other_warn = [l for l in warn if "exceeds soft limit 200" not in l]
errs = [l for l in mine if l.startswith("ERROR ")]
red_errs = [l for l in errs if ": RED " in l]
other_errs = [l for l in errs if ": RED " not in l]
m = re.search(r"errors=(\d+) warnings=(\d+)", out)
print("declared band: size-warning count=%d text=%s" % (len(size), [l.split(": ", 1)[1][:52]
                                                                 for l in size]))
print("this task: WARNING count=%d (band=%d other=%d) ERROR count=%d (RED-family=%d other=%d)"
      % (len(warn), len(size), len(other_warn), len(errs), len(red_errs), len(other_errs)))
print("info: RED-family error count=%d is printed, not judged - it is 1 before the TDD record is"
      " filled and 0 after, so judging it would expire on the first re-run" % len(red_errs))
for l in other_errs:
    print("  non-RED error: " + l)
print("note: repo-wide readings are errors=%s warnings=%s and are NOT judged here."
      % (m.groups() if m else ("?", "?")))
bad = 0
if len(size) != 1:
    print("FAIL 007's split made 005's own judged value exceed the soft limit, so exactly one"
          " size-band warning must be visible; %d means the declared value is hidden or the task"
          " warns for something else" % len(size))
    bad = 1
if other_warn:
    print("FAIL unexpected warning(s) on 005: %s" % other_warn); bad = 1
if other_errs:
    print("FAIL contract error(s) outside the RED record family: %s - filling in the TDD record"
          " cannot fix those, so the task file itself is not valid" % other_errs)
    bad = 1
if not m:
    print("FAIL no parsable SUMMARY line - 'cannot read the baseline' is not 'the baseline is fine'")
    bad = 1
print("CONTRACT ok" if not bad else "CONTRACT FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="declared band: size-warning count=1|this task: WARNING count=1 (band=1 other=0)|other=0)|RED-family error count=|is printed, not judged|note: repo-wide readings are errors=|CONTRACT ok"
run 6 0 "task contract: 005's declared judged value shows up as its own size-band warning and no \
contract error falls outside the RED-record family, while the RED-record count itself is printed \
rather than judged because it expires the moment the record is filled" \
  python -X utf8 "$TMP/contract005.py"

# ---- block 07: encoding gate, self-probed both ways ---------------------------------------
cat > "$TMP/utf8_gate.py" <<'PY'
"""闸门自己正反自检：一条已知坏字节串必须被拒（正控），本片文件必须通过（负控）。"""
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
        print("FAIL %s is not valid UTF-8 (%s)" % (p, exc)); bad += 1; continue
    if bytes([239, 191, 189]) in raw:
        print("FAIL %s contains U+FFFD replacement characters" % p); bad += 1; continue
    print("ok   %-58s bytes=%d" % (p, len(raw)))
print("gate covered %d file(s)" % len(sys.argv[1:]))
print("EXITCODE=%d" % (1 if bad else 0))
sys.exit(1 if bad else 0)
PY
WANT_TEXT="GATE SELFTEST ok|EXITCODE=0|gate covered"
run 7 0 "encoding gate over the instruments this slice adds, the filed inventory and this generator's own source, self-probed both ways" \
  python -X utf8 "$TMP/utf8_gate.py" "$GEN" "$SELF" "$LEDGER" "$LSELF" "$INVDATA" \
  artifacts/tmp/gen-005-red.sh .trae/tasks/T-2026-10-15-screen-inventory-parity-ledger-005.md

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
print('DECODE ok file bytes=%d' % len(raw))
" "$EV" 2>&1)
decode_rc=$?
echo "$decode" | tee -a "$EV" >> "$CONSOLE"
if [ "$decode_rc" != "0" ]; then
  echo "OVERALL OVERRIDDEN BY DECODE FAILURE" >> "$CONSOLE"
  final_rc=1
fi
exit "$final_rc"
