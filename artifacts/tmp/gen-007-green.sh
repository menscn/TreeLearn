#!/usr/bin/env bash
# T-2026-10-15-007 GREEN evidence generator (one-off; lives in artifacts/tmp by convention).
# Writes artifacts/evidence/T-2026-10-15-007-green.txt.
# 本片交付的是「台账生成器 + 台账产物」。GREEN 要证的不是「跑通了」，而是四件各自可回读的事：
#   判据全套为绿（块 02）；产物由本次运行写出且能自描述它的出处（块 04）；同一输入两次生成字节
#   相同、且与已落盘产物相同（块 05）；零运行时改动且姊妹门不受影响（块 01/06）。
# 规则沿用：每块自带命令行与自己的退出码；总退出码由 want/got 表派生，不写死 0；每块带内容见证；
# MIN_BLOCKS 是与本文件自身源码比对的字面整数；指纹类值只写复算命令不写进正文；
# 编码闸门之后的正文一律 ASCII-only 且实测 non-ascii=0。
set -uo pipefail

EV=artifacts/evidence/T-2026-10-15-007-green.txt
TMP=artifacts/tmp/_g007
CONSOLE=artifacts/tmp/_g007-console.txt
GEN=.trae/scripts/dev/gen_parity_ledger.py
SELF=.trae/scripts/dev/parity_ledger_selftest.py
INVPY=.trae/scripts/dev/gen_screen_inventory.py
INVS=.trae/scripts/dev/screen_inventory_selftest.py
PSELF=.trae/scripts/dev/parity/parity_selftest.py
INVDATA=Docs/04-ux-design/parity/screen-inventory.json
LEDGER=artifacts/parity/ledger.md
PROFILE=.trae/scripts/dev/parity/screens.json
mkdir -p "$TMP"
rm -f "$EV" "$CONSOLE"

if [ ! -d .git ]; then
  echo "FATAL: run this script from the repository root (cwd=$(pwd))"
  exit 3
fi
for f in "$GEN" "$SELF" "$INVPY" "$INVDATA" "$PROFILE"; do
  if [ ! -f "$f" ]; then
    echo "FATAL: $f is missing; this GREEN would judge nothing"
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
python -X utf8 -c "$FP" "$GEN" "$SELF" "$INVPY" "$INVDATA" "$PROFILE" > "$PROD" 2>&1
prod_rc=$?

{
  echo "# T-2026-10-15-007 GREEN - 1:1 台账生成器的判定证据"
  echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)  (UTC)"
  echo "# Generator: artifacts/tmp/gen-007-green.sh   重跑：bash artifacts/tmp/gen-007-green.sh"
  echo "# 本文件覆盖的调用形态："
  echo "#   python -X utf8 $GEN                  （写已落盘台账）"
  echo "#   python -X utf8 $GEN --out <副本>      （块 05 的两次重导出，与已落盘逐字节比对）"
  echo "#   python -X utf8 $SELF                  （正反自检全套；用例数由产物打印）"
  echo "# 判定面全部跑在真实仓库上；本片只新增工具与产物。android/ 只读：运行开始时已脏的路径"
  echo "# 只打印、不判定（共享工作树里那是别片的未提交工作），「本窗口内零位移」由末块增量判定。"
  echo "# 被核文件指纹（判定前读数；块 01 在全部判定之后复核是否仍相同）："
  sed 's/^/#   /' "$PROD"
  echo "# 指纹名单的边界：本运行会重写 台账 artifacts/parity/ledger.md，所以它不在名单内，也没有任何一块承诺它的字节未变——它变正是本片在做的事（生成器写它，另一块判它等于重导出）。名单中的生成物在这里是只读对象，由同一次运行的窗口之后复核是否位移；但同一支产物的字节会被另一片生成器在它自己的运行里重写，所以本行不是「你今天读到同样字节」的承诺——要复核当前字节就用 sha256sum 现场读该路径，并与台账里「清单文件哈希」「实测面哈希」两行对同一条命令回读。"
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
  printf '%s\n' "$out" | tail -n 20 >> "$EV"
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

# ---- block 01: production run, then fingerprints unchanged ---------------------------------
cat > "$TMP/keep_behaviour.py" <<'PY'
"""生产运行一次台账生成，随后复核：判定面文件字节未变；android/ 面按「运行窗口内是否位移」判定
（块 01 记下起始脏集，末块比对），不要求整棵树对本片干净——共享工作树里别的片可以带着未提交的
android 改动，把那种脏算成本片缺陷是假指控，而放弃这条判据又等于没有守卫。
指纹在这里比对而不是只由脚本自称——台账产物本来就是被写出的那个文件，它应当变（由块 05 单独
判它与重导出是否相同），所以只列入指纹的工具与清单不在写出之列。"""
import hashlib, json, os, re, subprocess, sys
WATCH = [".trae/scripts/dev/gen_parity_ledger.py", ".trae/scripts/dev/parity_ledger_selftest.py",
         ".trae/scripts/dev/gen_screen_inventory.py", "Docs/04-ux-design/parity/screen-inventory.json",
         ".trae/scripts/dev/parity/screens.json"]
pre_path = os.path.join("artifacts", "tmp", "_g007", "prod-before.txt")
pre = {}
for line in open(pre_path, encoding="utf-8"):
    m = re.match(r"(\S+)\s+bytes=(\d+)\s+lines=(\d+)\s+sha256=([0-9a-f]{64})", line)
    if m:
        pre[m.group(1)] = m.group(4)
bad = 0
start = {}
for q in [l for l in (subprocess.run(["git", "-c", "core.quotepath=false", "diff", "--name-only",
                                      "--", "android"], capture_output=True, text=True,
                                     encoding="utf-8", errors="replace").stdout
                      or "").splitlines() if l.strip()]:
    start[q] = hashlib.sha256(open(q, "rb").read()).hexdigest() if os.path.isfile(q) else "ABSENT"
open(os.path.join("artifacts", "tmp", "_g007", "android-start.json"), "w",
     encoding="utf-8").write(json.dumps(start, sort_keys=True))
print("android/ paths dirty at run start = %d (not judged: another slice may hold uncommitted"
      " android work in this shared worktree; the judgement is the start-to-end delta in the"
      " last block, so this run is measured against the tree it inherited)" % len(start))
run = subprocess.run([sys.executable, "-X", "utf8", ".trae/scripts/dev/gen_parity_ledger.py"],
                     capture_output=True, text=True, encoding="utf-8", errors="replace")
out = (run.stdout or "") + (run.stderr or "")
tail = out.strip().splitlines()[-1] if out.strip() else "(empty)"
print("production run rc=%d last line=%s" % (run.returncode, tail))
if run.returncode != 0:
    print("FAIL the generator must return 0 on the filed inventory"); bad = 1
for p in WATCH:
    now = hashlib.sha256(open(p, "rb").read()).hexdigest()
    was = pre.get(p)
    if was is None:
        print("FAIL no pre-run fingerprint for %s - the comparison would be vacuous" % p); bad = 1
        continue
    print("unchanged %s -> %s" % (p, now == was))
    if now != was:
        bad = 1
print("KEEP ok" if not bad else "KEEP FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="android/ paths dirty at run start =|production run rc=0|unchanged .trae/scripts/dev/gen_parity_ledger.py -> True|unchanged Docs/04-ux-design/parity/screen-inventory.json -> True|KEEP ok"
run 1 0 "keep-behaviour: the generator runs on the filed inventory and no judged file's bytes move" \
  python -X utf8 "$TMP/keep_behaviour.py"

# ---- block 02: the whole selftest suite, with tier coverage ---------------------------------
cat > "$TMP/suite007.py" <<'PY'
"""全套自检为绿之外，还要证明四个退出码层都真的被走到：只报「全绿」看不出 2/3 那两层有没有用例。"""
import re, subprocess, sys
r = subprocess.run([sys.executable, "-X", "utf8", ".trae/scripts/dev/parity_ledger_selftest.py"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
stdout = r.stdout or ""
out = stdout + (r.stderr or "")
m = re.search(r"cases executed=(\d+) expected=(\d+) failures=(\d+)", out)
if not m:
    print("SUITE FAULT: no counting line printed"); sys.exit(3)
executed, expected, failures = (int(x) for x in m.groups())
tiers = sorted({int(x) for x in re.findall(r"CASE \S+\s+rc=(\d+) want=\d", out)})
faults = len([l for l in out.splitlines() if l.startswith("INSTRUMENT FAULT")])
print("SUITE executed=%d expected=%d equal=%s failures=%d rc=%d"
      % (executed, expected, executed == expected, failures, r.returncode))
print("SUITE tiers seen=%s instrument-fault-lines=%d" % (tiers, faults))
bad = 0
if r.returncode != 0 or failures or executed != expected:
    print("FAIL every case must run and pass"); bad = 1
if not {0, 1, 2, 3} <= set(tiers):
    print("FAIL the cases must cover exit tiers 0,1,2 and 3 (got %s) - a tier with no case is a"
          " tier nothing checks" % tiers); bad = 1
if faults:
    print("FAIL %d INSTRUMENT FAULT line(s) inside a green suite" % faults); bad = 1
if not stdout.strip().endswith("EXITCODE=0"):
    print("FAIL the suite must print its own EXITCODE=0 as the last stdout line"); bad = 1
print("SUITE ok" if not bad else "SUITE FAULT")
sys.exit(3 if not m else (1 if bad else 0))
PY
WANT_TEXT="SUITE executed=|equal=True|failures=0|tiers seen=[0, 1, 2, 3]|instrument-fault-lines=0|SUITE ok"
run 2 0 "selftest suite: every case runs, all four exit tiers are exercised, no instrument fault line" \
  python -X utf8 "$TMP/suite007.py"

# ---- block 03: absence is a counted token, never a blank or a pass --------------------------
cat > "$TMP/absence.py" <<'PY'
"""台账的「没有证据」必须是可数的记号而不是空格——这是本片唯一防止「覆盖完成」变成自我声明的形态。
读数全部从已落盘产物里取，不重跑生成器；行数不写死，而是与清单自己的 counts 相核对。
表格行按「以 | 起头且不是分隔行」识别：分隔行是 |---|---|，不以「| 」开头，按空格筛会误吞第一条
数据行（实测把唯一有量测记录的那条 ChatScreen 行当成表头丢掉，于是 MEASURED 读数变成 0）。"""
import collections, json, re, sys
text = open("artifacts/parity/ledger.md", encoding="utf-8").read()
inv = json.load(open("Docs/04-ux-design/parity/screen-inventory.json", encoding="utf-8"))["counts"]
piped = [l for l in text.splitlines() if l.startswith("|")]
sep = re.compile(r"^\|[\s:\-\|]+$")
header = [l for l in piped if "条目" in l]
rows = [l for l in piped if not sep.match(l) and "条目" not in l]
expected_rows = inv["screens"] + inv["surfaces"]
print("table header lines=%d data rows=%d inventory screens+surfaces=%d"
      % (len(header), len(rows), expected_rows))
cells = collections.Counter()
empty = 0
for l in rows:
    parts = [p.strip() for p in l.strip().strip("|").split("|")]
    if len(parts) != 5:
        print("FAIL a ledger row does not have 5 cells: %s" % l); sys.exit(1)
    for layer in parts[2:]:
        if not layer:
            empty += 1
        cells[re.split(r"[：:（(]", layer)[0]] += 1
print("empty-cell-count=%d" % empty)
print("cell tally: %s" % dict(cells))
bad = 0
if len(header) != 1:
    print("FAIL expected exactly one header line, got %d - the row slice below depends on it"
          % len(header)); bad = 1
if len(rows) != expected_rows:
    print("FAIL the ledger carries %d rows but the inventory counts %d + %d = %d entries"
          % (len(rows), inv["screens"], inv["surfaces"], expected_rows)); bad = 1
if empty:
    print("FAIL %d cell(s) are blank - blank reads as nothing-wrong to a reader; absence must be a"
          " named token" % empty); bad = 1
if cells.get("MEASURED", 0) < 1:
    print("FAIL nothing in the ledger is backed by a measurement record, yet the run is green - the"
          " tally would then be all-absence and that must be said, not implied"); bad = 1
if "NO-PROFILE" not in cells and "NO-RECORD" not in cells:
    print("FAIL no absence token at all - with %d entries and one profile key that is impossible"
          " unless the generator stopped reporting it" % expected_rows); bad = 1
if "PASS" in text or "全部覆盖" in text:
    print("FAIL the ledger must not print PASS or a coverage claim; it reports which evidence exists"
          " for each cell, nothing more"); bad = 1
print("ABSENCE ok" if not bad else "ABSENCE FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="table header lines=1|data rows=|inventory screens+surfaces=|empty-cell-count=0|cell tally:|MEASURED|NO-PROFILE|ABSENCE ok"
run 3 0 "the filed ledger makes absence a counted token: no blank cell, no PASS wording" \
  python -X utf8 "$TMP/absence.py"

# ---- block 04: the artefact self-describes its provenance -----------------------------------
cat > "$TMP/provenance.py" <<'PY'
"""产物必须自述出处：生成命令、现场 HEAD、清单哈希、新鲜度结论。HEAD 与哈希都现场复算再比对，
不抄值——抄进正文的值会随文件移动而过期，且过期方向不可控。"""
import hashlib, re, subprocess, sys
text = open("artifacts/parity/ledger.md", encoding="utf-8").read()
head = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True,
                      encoding="utf-8", errors="replace").stdout.strip()
inv_sha = hashlib.sha256(open("Docs/04-ux-design/parity/screen-inventory.json", "rb").read()).hexdigest()
bad = 0
print("live HEAD length=%d matches the ledger line=%s"
      % (len(head), ("来源提交：%s" % head) in text or (head in text)))
print("recomputed inventory sha matches the ledger line=%s" % (inv_sha in text))
print("generation command present=%s" % ("gen_parity_ledger.py" in text))
print("freshness verdict present=%s" % ("清单新鲜度：" in text))
print("actual args line present=%s" % ("本次实际参数：" in text))
if len(head) != 40 or head not in text:
    print("FAIL the ledger must carry the HEAD this run actually read (40 hex) - a recorded value"
          " that was not read live is the drift-protection clause pretending to work"); bad = 1
if inv_sha not in text:
    print("FAIL the ledger must carry the hash of the inventory bytes it just read"); bad = 1
if "gen_parity_ledger.py" not in text:
    print("FAIL the ledger must name the command that regenerates it"); bad = 1
m = re.search(r"清单新鲜度：(.+)", text)
if not m or not m.group(1).strip():
    print("FAIL the freshness verdict line is missing or empty"); bad = 1
else:
    print("freshness reads: %s" % m.group(1).strip()[:24])
    if "一致" not in m.group(1):
        print("FAIL the filed ledger says the tree is stale against its own inventory - fix the"
              " inventory before anything reads this ledger"); bad = 1
if not text.rstrip().endswith("EXITCODE=0"):
    print("FAIL the ledger's own last line must be the exit code of the run that wrote it"); bad = 1
print("PROVENANCE ok" if not bad else "PROVENANCE FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="matches the ledger line=True|recomputed inventory sha matches the ledger line=True|generation command present=True|freshness verdict present=True|actual args line present=True|PROVENANCE ok"
run 4 0 "the written artefact self-describes: live HEAD, recomputed inventory hash, generation command, freshness verdict" \
  python -X utf8 "$TMP/provenance.py"

# ---- block 05: two re-exports are byte-identical and equal to the filed artefact -----------
cat > "$TMP/determinism.py" <<'PY'
"""同一输入两次重导出必须逐字节相同，且与已落盘产物相同。带时间戳的输出会让这条永远红，
所以头部只带内容与提交号——这条判据顺代守住「头部不许加时间戳」。

唯一允许不同的行是「本次实际参数」：它如实记录产生该文件的那条命令行，而已落盘产物由无参数运行
写出、这里用 --out 重导出，命令行本来就不同。因此比较前先确认这一行**存在且各文件各不相同**，
再把它排除掉比其余全部字节——排除的理由是看得见的事实，不是为了让比较通过。
"""
import os, re, subprocess, sys
GEN = ".trae/scripts/dev/gen_parity_ledger.py"
FILED = "artifacts/parity/ledger.md"
OUT = "artifacts/tmp/_g007"
a = os.path.join(OUT, "ledger-a.md")
b = os.path.join(OUT, "ledger-b.md")
ARGS = "- 本次实际参数："
bad = 0


def parts(path):
    lines = open(path, encoding="utf-8").read().splitlines(True)
    args = [l for l in lines if l.startswith(ARGS)]
    if len(args) != 1:
        print("FAIL %s carries %d recorded-args line(s), expected exactly 1" % (path, len(args)))
        return None, None
    return args[0], "".join(l for l in lines if not l.startswith(ARGS))


for t in (a, b):
    r = subprocess.run([sys.executable, "-X", "utf8", GEN, "--out", t],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print("FAIL re-export to %s returned %d: %s" % (t, r.returncode, (r.stderr or "")[:200]))
        bad = 1
if not bad:
    pa, ba = parts(a)
    pb, bb = parts(b)
    pf, bf = parts(FILED)
    if None in (pa, pb, pf):
        bad = 1
    else:
        print("recorded args differ per invocation: filed=%s a=%s b=%s all-distinct=%s"
              % (pf.strip()[:34], pa.strip()[:34], pb.strip()[:34],
               len({pa, pb, pf}) == 3))
        print("two re-exports byte-identical ignoring the args line=%s" % (ba == bb))
        print("re-export equals the filed artefact on every other byte=%s" % (ba == bf))
        print("bytes a=%d b=%d filed=%d" % (os.path.getsize(a), os.path.getsize(b),
                                            os.path.getsize(FILED)))
        if len({pa, pb, pf}) != 3:
            print("FAIL the args line is not recording the actual invocation (two of three are the"
                  " same although three different commands ran) - that line would then be decoration"); bad = 1
        if ba != bb:
            print("FAIL the ledger is not deterministic - a reader cannot tell a real change from a"
                  " regeneration artefact"); bad = 1
        if ba != bf:
            print("FAIL the filed artefact is not what the current tree derives - someone hand-edited"
                  " it or the last write came from a different inventory"); bad = 1
for name, p in (("a", a), ("filed", FILED)):
    text = open(p, encoding="utf-8").read()
    stamped = re.findall(r"Generated:|Generated at|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}", text)
    print("%s timestamp-shaped tokens=%d" % (name, len(stamped)))
    if stamped:
        print("FAIL %s carries a timestamp; a generated ledger must be byte-stable" % name); bad = 1
print("DETERMINISM ok" if not bad else "DETERMINISM FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="two re-exports byte-identical ignoring the args line=True|re-export equals the filed artefact on every other byte=True|all-distinct=True|timestamp-shaped tokens=0|DETERMINISM ok"
run 5 0 "determinism: two re-exports and the filed artefact are byte-identical except the line that records which command wrote each" \
  python -X utf8 "$TMP/determinism.py"

# ---- block 06: sibling gates and this task's own contract reading ---------------------------
cat > "$TMP/siblings.py" <<'PY'
"""姊妹门保持绿 + 本片的门禁可见性。仓库 validate_tasks 的总数只打印不比对（004 块 27 的同一轮
教训：别片申报规模带会把总数顶上去，钉住它就是把别人的 paperwork 当自己的结论）。
checkers_selftest 实测仍是 rc=3，那是 002 治理面的已知债务，本片不把它算进任何「全绿」结论。"""
import hashlib, json, os, re, subprocess, sys
GATES = [("005 inventory selftest", ".trae/scripts/dev/screen_inventory_selftest.py"),
         ("003 parity selftest", ".trae/scripts/dev/parity/parity_selftest.py"),
         ("004 design-token guard", ".trae/scripts/dev/check_design_tokens.py")]
bad = 0
for label, script in GATES:
    args = [sys.executable, "-X", "utf8", script]
    if label.endswith("guard"):
        args.append("--selftest")
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (r.stdout or "") + (r.stderr or "")
    last = (r.stdout or "").strip().splitlines()[-1] if (r.stdout or "").strip() else "(empty stdout)"
    print("sibling-gate %s rc=%d last=%s" % (label, r.returncode, last[:80]))
    if r.returncode != 0:
        print("FAIL %s is not green; 007 must not land on top of a broken sibling" % label); bad = 1
r = subprocess.run([sys.executable, "-X", "utf8", ".trae/scripts/dev/checkers_selftest.py"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
print("sibling-gate checkers_selftest rc=%d (known debt, reported not asserted)" % r.returncode)
SLUG = "parity-ledger-generator-007"
v = subprocess.run([sys.executable, "-X", "utf8", ".trae/scripts/validate_tasks.py"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
out = (v.stdout or "") + (v.stderr or "")
mine = [l for l in out.splitlines() if SLUG in l]
passes = len([l for l in mine if l.startswith("PASS ")])
warn = [l for l in mine if l.startswith("WARNING ")]
size = len([l for l in warn if "exceeds soft limit 200" in l])
errs = len([l for l in mine if l.startswith("ERROR ")])
m = re.search(r"errors=(\d+) warnings=(\d+)", out)
print("this task: PASS count=%d WARNING count=%d (size band=%d) ERROR count=%d"
      % (passes, len(warn), size, errs))
print("note: repo-wide readings are errors=%s warnings=%s and are NOT judged here"
      % (m.groups() if m else ("?", "?")))
if (passes, size, errs) != (1, 1, 0):
    print("FAIL 007 must sit at exactly one PASS and exactly one size-band WARNING with no error -"
          " the declared value is what makes the band visible (E1's lesson)"); bad = 1
if not m:
    print("FAIL no parsable SUMMARY line - 'cannot read it' is not 'it is fine'"); bad = 1
a = subprocess.run(["git", "-c", "core.quotepath=false", "diff", "--name-only", "--", "android"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
now = {}
for q in [l for l in (a.stdout or "").splitlines() if l.strip()]:
    now[q] = hashlib.sha256(open(q, "rb").read()).hexdigest() if os.path.isfile(q) else "ABSENT"
start = json.load(open(os.path.join("artifacts", "tmp", "_g007", "android-start.json"),
                       encoding="utf-8"))
movers = sorted(k for k in set(start) | set(now) if start.get(k) != now.get(k))
print("android/ dirty at start=%d at end=%d moved during this run=%d movers=%s"
      % (len(start), len(now), len(movers), movers))
if movers:
    print("FAIL android/ moved inside this run's window - while the production surface is being"
          " written under the run the keep-behaviour claim cannot be certified; re-run this"
          " generator once the neighbouring writer stops instead of dropping the judgement")
    bad = 1
# 正控：「一条本来干净的路径在窗口内变脏」必须被点出，否则这条增量守卫是空见证
_c_start, _c_end = {}, {"android/app/src/main/java/com/treelearn/app/Fake.java": "deadbeef"}
_c_moved = sorted(k for k in set(_c_start) | set(_c_end)
                  if _c_start.get(k) != _c_end.get(k))
print("positive control clean->dirty named=%s" % (_c_moved == list(_c_end)))
if _c_moved != list(_c_end):
    print("FAIL the delta does not see a clean path becoming dirty - the guard has no teeth")
    bad = 1
print("SIBLINGS ok" if not bad else "SIBLINGS FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="sibling-gate 005 inventory selftest rc=0|sibling-gate 003 parity selftest rc=0|sibling-gate 004 design-token guard rc=0|sibling-gate checkers_selftest rc=|known debt, reported not asserted|this task: PASS count=1 WARNING count=1 (size band=1) ERROR count=0|moved during this run=0 movers=[]|positive control clean->dirty named=True|SIBLINGS ok"
run 6 0 "sibling gates stay green, the known rc=3 debt is reported rather than folded into a green claim, and 007's own size band is visible" \
  python -X utf8 "$TMP/siblings.py"

# ---- block 07: encoding gate, self-probed both ways ---------------------------------------
cat > "$TMP/utf8_gate.py" <<'PY'
"""闸门自己正反自检：已知坏字节串必须被拒（正控），本片产物与工具必须通过（负控）。
台账产物由块 05 重导出过，因此它的字节也在闸门覆盖里。"""
import sys
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
    print("ok   %-56s bytes=%d" % (p, len(raw)))
print("gate covered %d file(s)" % len(sys.argv[1:]))
print("EXITCODE=%d" % (1 if bad else 0))
sys.exit(1 if bad else 0)
PY
WANT_TEXT="GATE SELFTEST ok|EXITCODE=0|gate covered"
run 7 0 "encoding gate over the instruments, the inventory, the written ledger and this generator, self-probed both ways" \
  python -X utf8 "$TMP/utf8_gate.py" "$GEN" "$SELF" "$INVPY" "$INVDATA" "$PROFILE" "$LEDGER" \
  artifacts/tmp/gen-007-red.sh artifacts/tmp/gen-007-green.sh \
  artifacts/evidence/T-2026-10-15-007-red.txt \
  .trae/tasks/T-2026-10-15-parity-ledger-generator-007.md

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
