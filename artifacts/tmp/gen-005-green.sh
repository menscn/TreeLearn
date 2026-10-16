#!/usr/bin/env bash
# T-2026-10-15-005 GREEN evidence generator (one-off; lives in artifacts/tmp by convention).
# Writes artifacts/evidence/T-2026-10-15-005-green.txt.
# 本片交付的是「屏幕与浮层清单生成器 + 已落盘清单」。GREEN 要证的不是「跑通了」，而是五件各自
# 可回读的事：判据全套为绿且四个退出码层都被走到（块 02）；清单每一条都能自述承载类/布局或自绘
# 标记/到达入口，且每个 counts 项都能从条目数组现场重数得出（块 03）；被代码判为无根布局充气的屏
# 必然在册且标记与代码派生集合双向一致（块 04，成员数不抄文档名单）；
# 对**真实源码树的副本**改名一个布局引用必须出现差异、清空屏幕包必须判 FAIL（块 05）；同一输入两次
# 导出逐字节相同且与已落盘清单在同一实测面上相同（块 06）；零运行时改动且姊妹门不受影响（块 01/07）。
# 规则沿用：每块自带命令行与自己的退出码；总退出码由 want/got 表派生，不写死 0；每块带内容见证；
# MIN_BLOCKS 是与本文件自身源码比对的字面整数；见证令牌一律不依赖 %-Ns 的补齐宽度；
# 编码闸门之后的正文一律 ASCII-only 且实测 non-ascii=0。
set -uo pipefail

EV=artifacts/evidence/T-2026-10-15-005-green.txt
TMP=artifacts/tmp/_g005
CONSOLE=artifacts/tmp/_g005-console.txt
GEN=.trae/scripts/dev/gen_screen_inventory.py
SELF=.trae/scripts/dev/screen_inventory_selftest.py
INVPY=.trae/scripts/dev/gen_parity_ledger.py
LSELF=.trae/scripts/dev/parity_ledger_selftest.py
PSELF=.trae/scripts/dev/parity/parity_selftest.py
DTOK=.trae/scripts/dev/check_design_tokens.py
INVDATA=Docs/04-ux-design/parity/screen-inventory.json
SPEC=Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md
mkdir -p "$TMP"
rm -f "$EV" "$CONSOLE"

if [ ! -d .git ]; then
  echo "FATAL: run this script from the repository root (cwd=$(pwd))"
  exit 3
fi
for f in "$GEN" "$SELF" "$INVDATA" "$SPEC"; do
  if [ ! -f "$f" ]; then
    echo "FATAL: $f is missing; this GREEN would judge nothing"
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
PROD="$TMP/prod-before.txt"
python -X utf8 -c "$FP" "$GEN" "$SELF" "$INVDATA" "$SPEC" > "$PROD" 2>&1
prod_rc=$?

{
  echo "# T-2026-10-15-005 GREEN - 屏幕清单生成器的判定证据"
  echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)  (UTC)"
  echo "# Generator: artifacts/tmp/gen-005-green.sh   重跑：bash artifacts/tmp/gen-005-green.sh"
  echo "# 本文件覆盖的调用形态："
  echo "#   python -X utf8 $GEN                  （写已落盘清单）"
  echo "#   python -X utf8 $GEN --java <副本> --res <副本> --out <副本>  （块 05 的真实树副本判据）"
  echo "#   python -X utf8 $SELF                  （正反自检全套；用例数由产物打印）"
  echo "# 判定面全部跑在真实仓库上；块 05 只在 artifacts/tmp/_g005c 的副本上动刀，"
  echo "#   android/ 源码在整份证据里只读；本次运行开始时已脏的路径只打印、不判定，"
  echo "#   「本窗口内没有被移动」由末块的起始—结束增量判定并配 clean->dirty 正控。"
  echo "# 被核文件指纹（判定前读数；块 01 在全部判定之后复核是否仍相同）："
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

# ---- block 01: production run, then fingerprints unchanged ---------------------------------
cat > "$TMP/keep_behaviour.py" <<'PY'
"""生产运行一次清单生成，随后复核：被判读数的工具与清单字节未变；android/ 面按「运行前后是否位移」
判定（块 01 记下起始脏集，末块比对），而不是要求整棵树对本片干净——共享工作树里别的片可以带着
未提交的 android 改动，把那种脏算成本片缺陷是假指控，而放弃这条判据又等于没有守卫。
清单本身就是被写出的那个文件，它允许变（块 06 单独判它与重导出是否相同），所以指纹监视里只留
生成器、自检与规格——它们不该被生产运行改动。"""
import hashlib, json, os, re, subprocess, sys
WATCH = [".trae/scripts/dev/gen_screen_inventory.py",
         ".trae/scripts/dev/screen_inventory_selftest.py",
         "Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md"]
pre_path = os.path.join("artifacts", "tmp", "_g005", "prod-before.txt")
pre = {}
for line in open(pre_path, encoding="utf-8"):
    m = re.match(r"(\S+)\s+bytes=(\d+)\s+lines=(\d+)\s+sha256=([0-9a-f]{64})", line)
    if m:
        pre[m.group(1)] = m.group(4)
bad = 0
start = {}
for p in [l for l in (subprocess.run(["git", "-c", "core.quotepath=false", "diff", "--name-only",
                                      "--", "android"], capture_output=True, text=True,
                                     encoding="utf-8", errors="replace").stdout
                      or "").splitlines() if l.strip()]:
    start[p] = hashlib.sha256(open(p, "rb").read()).hexdigest() if os.path.isfile(p) else "ABSENT"
with open(os.path.join("artifacts", "tmp", "_g005", "android-start.json"), "w",
          encoding="utf-8") as fh:
    json.dump(start, fh, sort_keys=True)
print("android/ paths dirty at run start = %d (not judged: another slice may hold uncommitted"
      " android work in this shared worktree; the judgement is the start-to-end delta in the"
      " last block, so this run is measured against the tree it inherited, not against a"
      " cleanliness the neighbouring slice owes)" % len(start))
run = subprocess.run([sys.executable, "-X", "utf8", ".trae/scripts/dev/gen_screen_inventory.py"],
                     capture_output=True, text=True, encoding="utf-8", errors="replace")
out = (run.stdout or "") + (run.stderr or "")
lines = out.strip().splitlines()
print("production run rc=%d judgement lines=%d last=%s"
      % (run.returncode, len(lines), lines[-1] if lines else "(empty)"))
if run.returncode != 0:
    print("FAIL the generator must return 0 on the real tree"); bad = 1
if not lines:
    print("FAIL the generator printed nothing - an empty stdout is not a clean inventory"); bad = 1
if not out.strip().endswith("EXITCODE=0"):
    print("FAIL the run must print its own exit code as the last line"); bad = 1
if len([l for l in lines if l.startswith("SCREEN ")]) == 0:
    print("FAIL no SCREEN line printed; the listing is the point of this instrument"); bad = 1
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
WANT_TEXT="android/ paths dirty at run start =|production run rc=0|unchanged .trae/scripts/dev/gen_screen_inventory.py -> True|unchanged .trae/scripts/dev/screen_inventory_selftest.py -> True|unchanged Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md -> True|KEEP ok"
run 1 0 "keep-behaviour: the generator runs on the real tree, prints its per-screen listing and its own exit code, and no watched file's bytes move" \
  python -X utf8 "$TMP/keep_behaviour.py"

# ---- block 02: the whole selftest suite, with tier coverage ---------------------------------
cat > "$TMP/suite005.py" <<'PY'
"""全套自检为绿之外，还要证明四个退出码层都真的被走到。

自检套件内部把 tier 3 用作**正常覆盖**（缺屏幕包 / 缺 MainActivity / 非 UTF-8 输入 / 方法体不闭合 /
死 include 五条负向用例），所以「仪器崩」的信号必须是 INSTRUMENT FAULT 行而不是 rc=3；tier 2 自检
自己不覆盖（argparse 的 SystemExit 在 main 里被吃掉），因此这里额外起一个真实子进程去拿 2。
"""
import re, subprocess, sys
SELF = ".trae/scripts/dev/screen_inventory_selftest.py"
GEN = ".trae/scripts/dev/gen_screen_inventory.py"
r = subprocess.run([sys.executable, "-X", "utf8", SELF],
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
verdict = "VERDICT: PASS" in out
print("SUITE verdict line present=%s" % verdict)
u = subprocess.run([sys.executable, "-X", "utf8", GEN, "--definitely-not-an-option"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
print("USAGE rc=%d no judgement printed=%s"
      % (u.returncode, not [l for l in (u.stdout or "").splitlines() if l.startswith("SCREEN ")]))
bad = 0
if r.returncode != 0 or failures or executed != expected:
    print("FAIL every case must run and pass"); bad = 1
if not {0, 1, 3} <= set(tiers):
    print("FAIL the cases must cover exit tiers 0, 1 and 3 (got %s) - a tier with no case is a"
          " tier nothing checks" % tiers); bad = 1
if u.returncode != 2:
    print("FAIL tier 2 (usage error) must be reachable by a real invocation, got rc=%d"
          % u.returncode); bad = 1
if faults:
    print("FAIL %d INSTRUMENT FAULT line(s) inside a green suite" % faults); bad = 1
if not stdout.strip().endswith("EXITCODE=0"):
    print("FAIL the suite must print its own EXITCODE=0 as the last stdout line"); bad = 1
if not verdict:
    print("FAIL the suite must print its verdict"); bad = 1
print("SUITE ok" if not bad else "SUITE FAULT")
sys.exit(3 if not m else (1 if bad else 0))
PY
WANT_TEXT="SUITE executed=|equal=True|failures=0|tiers seen=[0, 1, 3]|instrument-fault-lines=0|USAGE rc=2|no judgement printed=True|SUITE verdict line present=True|SUITE ok"
run 2 0 "selftest suite: every case runs, tiers 0/1/3 exercised by cases and tier 2 reached by a real subprocess, no instrument fault line" \
  python -X utf8 "$TMP/suite005.py"

# ---- block 03: every entry self-describes and every count re-derives ------------------------
cat > "$TMP/entries.py" <<'PY'
"""已落盘清单必须逐条自述、且每个 counts 项都能从条目数组现场重数得出。

这是本片的核心判据：数字不是人抄的。所有读数从 JSON 现场算，脚本里不出现任何「应当有几个屏」的
期望值——规格对范围只提供一个**指针**（2026-10-16 裁定清单为范围权威），本块回读该指针指向的文件并
要求指针行是对那个文件的重读结果，而不是手抄；kinds 词汇表同样从生产者模块现场导入，不在这里复述。
"""
import hashlib, importlib.util, json, sys
raw = open("Docs/04-ux-design/parity/screen-inventory.json", "rb").read()
text = raw.decode("utf-8")
inv = json.loads(text)
bad = 0
screens, surfaces, counts = inv["screens"], inv["surfaces"], inv["counts"]
# The kind vocabulary is read out of the producer that mints it. Restating it here made the green
# evidence a second source, so the two PopupMenu and four AlertDialog kinds this slice added read
# as "unknown kind" until the copy was widened - which is the failure mode of a copy, not of a
# guard. Import failure exits 3 (instrument fault), never a silent pass.
try:
    spec = importlib.util.spec_from_file_location(
        "gsi", ".trae/scripts/dev/gen_screen_inventory.py")
    gsi = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gsi)
    allowed_kinds = set(gsi.SURFACE_KINDS)
except Exception as exc:
    print("INSTRUMENT FAULT cannot read the producer's kind vocabulary: %r" % (exc,))
    sys.exit(3)
if not allowed_kinds:
    print("INSTRUMENT FAULT the producer exports an empty kind vocabulary - an empty allow-list"
          " would make every entry look wrong, and a widened one would make nothing look wrong")
    sys.exit(3)
print("derived kind vocabulary from the producer: kinds=%d %s"
      % (len(allowed_kinds), sorted(allowed_kinds)))
print("entry counts screens=%d surfaces=%d components=%d layout-files=%d"
      % (len(screens), len(surfaces), len(inv["components"]), counts["layout_files"]))
for s in screens:
    missing = [k for k in ("class", "path", "root_layout", "self_drawn", "sw600dp",
                           "reached_by", "channels", "layout_refs") if k not in s]
    if missing:
        print("FAIL screen %s lacks %s" % (s.get("class"), missing)); bad = 1
    if not s["reached_by"]:
        print("FAIL screen %s has no arrival entry - it cannot be part of the scope" % s["class"])
        bad = 1
    for h in s["reached_by"]:
        for k in ("method", "line", "channel"):
            if not h.get(k):
                print("FAIL screen %s arrival entry misses %s: %s" % (s["class"], k, h)); bad = 1
    if s["root_layout"] is None and not s["self_drawn"]:
        print("FAIL screen %s has no layout yet is not marked self-drawn" % s["class"]); bad = 1
for s in surfaces:
    if s["kind"] not in allowed_kinds:
        print("FAIL surface of kind %s which the producer cannot emit (vocabulary it exports:"
              " %s)" % (s["kind"], sorted(allowed_kinds))); bad = 1
    if not s["producer"]:
        print("FAIL surface %s has no producer class" % s); bad = 1
print("all screens carry an arrival entry: %s" % all(s["reached_by"] for s in screens))
print("all surfaces carry a producer: %s" % all(bool(s["producer"]) for s in surfaces))
recheck = {
    "screens": len(screens),
    "surfaces": len(surfaces),
    "self_drawn": sum(1 for s in screens if s["self_drawn"]),
    "sw600dp_variants": sum(1 for s in screens if s["sw600dp"]),
    "component_classes": len(inv["components"]),
    "unreferenced_layouts": len(inv["unreferenced_layouts"]),
    "unreached_screens": len(inv["unreached_screens"]),
    "visible_includes": sum(1 for name, info in inv["layout_census"].items()
                            for inc in info["includes"]
                            if not inv["layout_census"][inc]["hidden"]),
    "screen_package_files": len(screens) + len(inv["components"]),
}
for key, got in sorted(recheck.items()):
    filed = counts[key]
    print("recount %s filed=%d recomputed=%d same=%s" % (key, filed, got, filed == got))
    if filed != got:
        bad = 1
by_kind = {}
for s in surfaces:
    by_kind[s["kind"]] = by_kind.get(s["kind"], 0) + 1
print("recount surfaces_by_kind filed=%s recomputed=%s same=%s"
      % (json.dumps(counts["surfaces_by_kind"], sort_keys=True),
         json.dumps(by_kind, sort_keys=True),
         counts["surfaces_by_kind"] == by_kind))
if counts["surfaces_by_kind"] != by_kind:
    bad = 1
if sum(counts["surfaces_by_kind"].values()) != counts["surfaces"]:
    print("FAIL surfaces_by_kind does not sum to surfaces - a per-kind table that disagrees with the"
          " total is exactly the drift this block exists to catch")
    bad = 1
link = inv["spec_link"]
# The pointer row must be reproducible from the file it names, using the producer's own pattern
# rather than a copy of it - that is the same single-source rule the kind vocabulary follows above.
try:
    spec_text = open(link["source"], "rb").read().decode("utf-8")
except Exception as exc:
    print("INSTRUMENT FAULT the pointer row names a spec that cannot be read: %r" % (exc,))
    sys.exit(3)
fresh_cited = gsi.INVENTORY_NAME in spec_text
fresh_lines = [i for i, l in enumerate(spec_text.splitlines(), 1) if gsi.COUNT_SHAPE.search(l)]
print("pointer row filed cites=%s re-read cites=%s same=%s"
      % (link["inventory_cited"], fresh_cited, link["inventory_cited"] == fresh_cited))
print("restatements filed lines=%s re-read lines=%s same=%s"
      % (link["count_shaped_lines"], fresh_lines,
         link["count_shaped_lines"] == fresh_lines))
if link["inventory_cited"] != fresh_cited or link["count_shaped_lines"] != fresh_lines:
    print("FAIL the pointer row is not a reading of the file it names - it was typed or left stale")
    bad = 1
if not link["inventory_cited"]:
    print("FAIL the spec must cite the inventory; without the pointer nothing says where the"
          " range counts come from"); bad = 1
if "generated_by" not in inv or "gen_screen_inventory.py" not in inv["generated_by"]:
    print("FAIL the inventory must name the command that regenerates it"); bad = 1
if len(inv["source_commit"]) != 40:
    print("FAIL source_commit must be a full 40-hex HEAD (got %d chars)"
          % len(inv["source_commit"])); bad = 1
print("strict utf-8 bytes=%d sha256=%d-chars" % (len(raw), len(hashlib.sha256(raw).hexdigest())))
if "PASS" in text or "全部覆盖" in text:
    print("FAIL the inventory is a listing, not a verdict - it must not carry a coverage claim")
    bad = 1
print("ENTRIES ok" if not bad else "ENTRIES FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="derived kind vocabulary from the producer: kinds=|entry counts screens=|surfaces=|all screens carry an arrival entry: True|all surfaces carry a producer: True|recount screens filed=|recount surfaces_by_kind|same=True|pointer row filed cites=True|re-read cites=True same=True|restatements filed lines=|ENTRIES ok"
run 3 0 "the filed inventory self-describes every entry and every count re-derives from the entry arrays, with the spec's pointer re-read from the file it names" \
  python -X utf8 "$TMP/entries.py"

# ---- block 04: the self-drawn screens and the sw600dp census --------------------------------
cat > "$TMP/selfdrawn.py" <<'PY'
"""本片存在的理由就是这一条：没有布局 XML 的屏必须**必然**出现在清单里并带自绘标记。

名单不在这里手写判定——它是从 Android 源码现场算出来的：凡 screen/ 包里没有 inflate 根布局的
*Screen 类都必须 self_drawn=True，反之必须带根布局；两处对不上就是漏盘。规格文档里写过的四个名字
只作为「历史事实是否仍成立」的对照打印，判定用的是代码。
"""
import json, os, re, sys
inv = json.loads(open("Docs/04-ux-design/parity/screen-inventory.json", encoding="utf-8").read())
JAVA = "android/app/src/main/java/com/treelearn/app/screen"
ROOT_INFLATE = re.compile(r"inflate\(\s*R\.layout\.(\w+)\s*,\s*(?:\([\w.]+\)\s*)?this\s*,\s*true\s*\)")
by_class = {s["class"]: s for s in inv["screens"]}
bad = 0
drawn_in_code, laid_out_in_code = [], []
for f in sorted(os.listdir(JAVA)):
    if not f.endswith("Screen.java"):
        continue
    cls = f[:-5]
    text = open(os.path.join(JAVA, f), encoding="utf-8").read()
    (laid_out_in_code if ROOT_INFLATE.search(text) else drawn_in_code).append(cls)
print("code-derived self-drawn screens=%d with-root screens=%d"
      % (len(drawn_in_code), len(laid_out_in_code)))
for cls in drawn_in_code:
    entry = by_class.get(cls)
    if entry is None:
        print("FAIL %s builds its view in code but is not in the inventory at all" % cls); bad = 1
        continue
    print("self-drawn %s listed=%s flag=%s root=%s"
          % (cls, True, entry["self_drawn"], entry["root_layout"]))
    if not entry["self_drawn"] or entry["root_layout"] is not None:
        bad = 1
for cls in laid_out_in_code:
    entry = by_class.get(cls)
    if entry is None:
        print("FAIL %s inflates a root layout but is not in the inventory" % cls); bad = 1
    elif entry["self_drawn"] or not entry["root_layout"]:
        print("FAIL %s inflates a layout yet is marked self-drawn (%s)" % (cls, entry)); bad = 1
named = ["FileManagerScreen", "ModelSettingsScreen", "SearchServiceSettingsScreen",
         "VoiceSettingsScreen"]
print("doc-named set still self-drawn: %s"
      % all(by_class.get(n, {}).get("self_drawn") is True for n in named))
for n in named:
    if by_class.get(n, {}).get("self_drawn") is not True:
        print("FAIL the spec names %s as a self-drawn screen but the inventory does not mark it -"
              " either the doc or the instrument is out of date" % n)
        bad = 1
sw = [s["class"] for s in inv["screens"] if s["sw600dp"]]
print("sw600dp census screens-with-variant=%d of %d" % (len(sw), len(inv["screens"])))
print("sw600dp line: %s" % ", ".join(sw))
if len(sw) != inv["counts"]["sw600dp_variants"]:
    print("FAIL the sw600dp count does not equal the per-screen census"); bad = 1
layout_dirs = set()
for name, info in inv["layout_census"].items():
    layout_dirs.update(info["dirs"])
print("layout variant dirs seen by the census: %s" % sorted(layout_dirs))
if "layout-sw600dp" not in layout_dirs:
    print("FAIL no layout-sw600dp directory was read - the variant column would be all-False"
          " without saying so")
    bad = 1
print("SELFDRAWN ok" if not bad else "SELFDRAWN FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="code-derived self-drawn screens=|self-drawn FileManagerScreen listed=True flag=True root=None|doc-named set still self-drawn: True|sw600dp census screens-with-variant=|layout variant dirs seen by the census: ['layout', 'layout-sw600dp']|SELFDRAWN ok"
run 4 0 "the screens that build their view in code are in the inventory because the code says so, and the sw600dp variant column re-derives from the layout census" \
  python -X utf8 "$TMP/selfdrawn.py"

# ---- block 05: real-tree copy - rename, empty package, fail-closed --------------------------
cat > "$TMP/realcopy.py" <<'PY'
"""把**真实源码树**拷一份，只在副本上动刀，量三条只有实树才给得出的判据：
  1 副本未改时导出的实测面与已落盘清单相同（拷贝本身没有改变判据）；
  2 把某个屏的根布局引用改名后，该屏的 root 必须变成新名字，且被弃用的布局必须打印成
    UNREFERENCED_LAYOUT——未知引用要单独可见，不能静默丢弃；
  3 清空 screen 包后必须判 FAIL（rc=1）、打印 screens=0、且不出现任何 SCREEN 行或覆盖结论。
拷完之后副本目录只用于本块，末尾删除。android/ 与 Docs/ 全程只读（块 01/03/06 的指纹独立复核）。
"""
import hashlib, json, os, re, shutil, subprocess, sys

GEN = ".trae/scripts/dev/gen_screen_inventory.py"
SPEC = "Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md"
FILED = "Docs/04-ux-design/parity/screen-inventory.json"
JAVA_SRC = os.path.join("android", "app", "src", "main", "java")
RES_SRC = os.path.join("android", "app", "src", "main", "res")
ROOT = os.path.join("artifacts", "tmp", "_g005c", "prod")
FACE = ("schema_version", "screens", "surfaces", "components", "layout_census",
        "unreferenced_layouts", "unreached_screens", "counts")


def face_sha(obj):
    return hashlib.sha256(json.dumps({k: obj[k] for k in FACE}, sort_keys=True,
                                     ensure_ascii=False).encode("utf-8")).hexdigest()


def cp_tree(base):
    for root, _d, fs in os.walk(base):
        for f in fs:
            src = os.path.join(root, f)
            dst = os.path.join(ROOT, os.path.relpath(src, "."))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)


def derive(tag, java, res, out):
    r = subprocess.run([sys.executable, "-X", "utf8", GEN, "--java", java, "--res", res,
                        "--spec", SPEC, "--out", out],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    open(out + ".log", "w", encoding="utf-8").write((r.stdout or "") + (r.stderr or ""))
    return r.returncode, (r.stdout or "") + (r.stderr or "")


if os.path.isdir(ROOT):
    shutil.rmtree(ROOT)
cp_tree(JAVA_SRC)
cp_tree(RES_SRC)
java = os.path.join(ROOT, JAVA_SRC, "com", "treelearn", "app")
res = os.path.join(ROOT, RES_SRC)
longest = max((len(os.path.join(b, f)) for b, _d, fs in os.walk(ROOT) for f in fs))
print("copy files=%d longest path=%d chars (fail-closed at 240)"
      % (sum(len(fs) for _b, _d, fs in os.walk(ROOT)), longest))
if longest > 240:
    print("FAIL the copy tree would sit too close to the Windows path limit and a truncated copy"
          " is not the same tree as the source")
    sys.exit(3)

bad = 0
base_out = os.path.join(ROOT, "inventory-base.json")
rc0, out0 = derive("base", java, res, base_out)
filed = json.loads(open(FILED, encoding="utf-8").read())
base = json.loads(open(base_out, encoding="utf-8").read())
print("BASE rc=%d face equal to the filed inventory=%s"
      % (rc0, rc0 == 0 and face_sha(base) == face_sha(filed)))
if rc0 != 0 or face_sha(base) != face_sha(filed):
    print("FAIL the copy of the real tree must derive to the same measured face as the filed"
          " inventory (rc=%d)" % rc0)
    bad = 1
    print("BASE stdout tail: %s" % out0.strip().splitlines()[-3:])

pick = None
for s in base["screens"]:
    if not s["root_layout"]:
        continue
    path = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "treelearn",
                        "app", "screen", s["class"] + ".java")
    if not os.path.isfile(path):
        continue
    raw = open(path, encoding="utf-8").read()
    if raw.count("R.layout." + s["root_layout"]) == 1:
        pick = (s, path, raw)
        break
if pick is None:
    print("FAIL no screen in the real tree inflates exactly one root layout, so the rename control"
          " cannot be built - this is not a pass")
    sys.exit(3)
s, path, raw = pick
moved = raw.replace("R.layout." + s["root_layout"], "R.layout." + s["root_layout"] + "_gone", 1)
open(path, "w", encoding="utf-8", newline="").write(moved)
ren_out = os.path.join(ROOT, "inventory-renamed.json")
rc1, out1 = derive("rename", java, res, ren_out)
ren = json.loads(open(ren_out, encoding="utf-8").read())
rentry = [x for x in ren["screens"] if x["class"] == s["class"]][0]
orphans = [l for l in out1.splitlines() if l.startswith("UNREFERENCED_LAYOUT")]
print("RENAME class=%s old root=%s new root=%s rc=%d"
      % (s["class"], s["root_layout"], rentry["root_layout"], rc1))
print("RENAME orphan lines=%d names the abandoned layout=%s"
      % (len(orphans), any(s["root_layout"] in l for l in orphans)))
if rentry["root_layout"] != s["root_layout"] + "_gone":
    print("FAIL the renamed reference was not carried into the inventory"); bad = 1
if not orphans or not any(s["root_layout"] in l for l in orphans):
    print("FAIL the abandoned layout file must be printed as unreferenced - a dangling reference"
          " that silently disappears is the blind spot this instrument exists to close"); bad = 1
if rentry["self_drawn"]:
    print("FAIL a screen that inflates a (now missing) layout must not read as self-drawn"); bad = 1
if face_sha(ren) == face_sha(base):
    print("FAIL the inventory face did not move although the tree changed"); bad = 1

shutil.move(path, path + ".bak")
open(path, "w", encoding="utf-8", newline="").write(raw)
screen_dir = os.path.join(java, "screen")
shutil.rmtree(screen_dir)
os.makedirs(screen_dir)
emp_out = os.path.join(ROOT, "inventory-empty.json")
rc2, out2 = derive("empty", java, res, emp_out)
lines = out2.splitlines()
screen_lines = [l for l in lines if l.startswith("SCREEN ")]
print("EMPTY rc=%d screens-zero line=%s SCREEN lines printed=%d"
      % (rc2, "MEASURED screens=0 " in out2, len(screen_lines)))
print("EMPTY floor judged-not-ok=%s coverage wording=%s exit line=%s"
      % ("ok=False" in out2, ("全部覆盖" in out2 or "VERDICT: PASS" in out2),
         out2.strip().endswith("EXITCODE=1")))
if rc2 != 1:
    print("FAIL an emptied screen package must judge FAIL, not certify an empty coverage set"
          " (rc=%d)" % rc2)
    bad = 1
if "MEASURED screens=0 " not in out2 or "ok=False" not in out2:
    print("FAIL the floor must print both numbers so the shortfall is readable"); bad = 1
if screen_lines:
    print("FAIL %d SCREEN line(s) printed from an empty package" % len(screen_lines)); bad = 1
if out2.strip().endswith("EXITCODE=0"):
    print("FAIL the emptied run printed exit code 0"); bad = 1
shutil.rmtree(ROOT, ignore_errors=True)
print("REALCOPY ok" if not bad else "REALCOPY FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="copy files=|longest path=|BASE rc=0 face equal to the filed inventory=True|old root=|new root=|names the abandoned layout=True|EMPTY rc=1 screens-zero line=True SCREEN lines printed=0|EMPTY floor judged-not-ok=True coverage wording=False exit line=True|REALCOPY ok"
run 5 0 "the real tree copied: rename one layout reference and the inventory moves and prints the orphan; empty the screen package and it fails closed instead of certifying nothing" \
  python -X utf8 "$TMP/realcopy.py"

# ---- block 06: determinism of the filed inventory -------------------------------------------
cat > "$TMP/determinism.py" <<'PY'
"""同一棵树两次导出的字节必须相同，且与已落盘清单在实测面上相同。

清单本身是 JSON、没有「本次命令行」那一类装饰行，所以整文件应当逐字节相同；唯一允许不同的是
source_commit——它在每次提交后自然位移，因此判据写成「整文件差异的顶层键必须是 source_commit 的
子集」，而不是硬要求全等（后者会让本片证据在一次无关提交之后必然转红，而那不代表界面变了）。
时间戳形状的令牌必须为 0：带时间戳的产物会让「两次导出相同」永远红，也就让块 05 的副本判据失去意义。
"""
import hashlib, json, os, re, subprocess, sys
GEN = ".trae/scripts/dev/gen_screen_inventory.py"
FILED = "Docs/04-ux-design/parity/screen-inventory.json"
OUT = os.path.join("artifacts", "tmp", "_g005")
FACE = ("schema_version", "screens", "surfaces", "components", "layout_census",
        "unreferenced_layouts", "unreached_screens", "counts")
a = os.path.join(OUT, "inv-a.json")
b = os.path.join(OUT, "inv-b.json")
bad = 0


def face_sha(obj):
    return hashlib.sha256(json.dumps({k: obj[k] for k in FACE}, sort_keys=True,
                                     ensure_ascii=False).encode("utf-8")).hexdigest()


for t in (a, b):
    r = subprocess.run([sys.executable, "-X", "utf8", GEN, "--out", t],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print("FAIL export to %s returned %d: %s" % (t, r.returncode, (r.stderr or "")[:200]))
        bad = 1
da, db = open(a, "rb").read(), open(b, "rb").read()
df = open(FILED, "rb").read()
ja, jb, jf = (json.loads(x.decode("utf-8")) for x in (da, db, df))
differing = sorted(k for k in set(ja) | set(jf) if ja.get(k) != jf.get(k))
print("two exports byte-identical=%s" % (da == db))
print("measured face equal to the filed inventory=%s" % (face_sha(ja) == face_sha(jf)))
print("filed vs re-export: bytes=%d/%d differing top-level keys=%s allowed=%s"
      % (len(da), len(df), differing, ["source_commit"]))
print("face sha=%s" % face_sha(ja)[:16])
if da != db:
    print("FAIL the inventory is not deterministic - a reader cannot tell a real interface change"
          " from a regeneration artefact")
    bad = 1
if face_sha(ja) != face_sha(jf):
    print("FAIL the filed inventory does not describe the current tree; regenerate it before"
          " anything reads it (this is the same face 007 compares for staleness)")
    bad = 1
if not set(differing) <= {"source_commit"}:
    print("FAIL the filed bytes differ from the current derivation by more than the commit stamp"
          " - someone hand-edited the inventory")
    bad = 1
for name, raw in (("a", da), ("filed", df)):
    stamped = re.findall(rb"Generated:|generated_at|timestamp|\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}",
                         raw)
    print("%s timestamp-shaped tokens=%d" % (name, len(stamped)))
    if stamped:
        print("FAIL %s carries a timestamp; the JSON must be byte-stable" % name)
        bad = 1
print("DETERMINISM ok" if not bad else "DETERMINISM FAULT")
sys.exit(1 if bad else 0)
PY
WANT_TEXT="two exports byte-identical=True|measured face equal to the filed inventory=True|differing top-level keys=[] allowed=['source_commit']|timestamp-shaped tokens=0|DETERMINISM ok"
run 6 0 "determinism: two exports are byte-identical, the measured face equals the filed inventory, and any whole-file difference is confined to the commit stamp" \
  python -X utf8 "$TMP/determinism.py"

# ---- block 07: sibling gates and this task's own contract reading ---------------------------
cat > "$TMP/siblings.py" <<'PY'
"""姊妹门保持绿 + 本片的门禁可见性。仓库 validate_tasks 的总数只打印不比对（004 块 27 的同一轮
教训：别片申报规模带会把总数顶上去，钉住总数就是把别人的 paperwork 当自己的结论）。
checkers_selftest 实测是 rc=3，那是 002 治理面的已知债务，本片不把它折进任何「全绿」结论——
它作为 Gaps 打印出来，而不是当作满足。"""
import hashlib, json, os, re, subprocess, sys
GATES = [("007 ledger selftest", ".trae/scripts/dev/parity_ledger_selftest.py"),
         ("007 ledger generator", ".trae/scripts/dev/gen_parity_ledger.py"),
         ("003 parity selftest", ".trae/scripts/dev/parity/parity_selftest.py"),
         ("004 design-token guard", ".trae/scripts/dev/check_design_tokens.py")]
bad = 0
for label, script in GATES:
    args = [sys.executable, "-X", "utf8", script]
    if label.endswith("guard"):
        args.append("--selftest")
    if label.endswith("generator"):
        args.append("--no-write")
    r = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (r.stdout or "") + (r.stderr or "")
    last = (r.stdout or "").strip().splitlines()[-1] if (r.stdout or "").strip() else "(empty stdout)"
    print("sibling-gate %s rc=%d last=%s" % (label, r.returncode, last[:70]))
    if r.returncode != 0:
        print("FAIL %s is not green; 005 must not land on top of a broken sibling" % label); bad = 1
    if label.endswith("generator") and "清单新鲜度：一致" not in out:
        print("FAIL the ledger generator read the 005 inventory and found the measured faces"
              " disagreeing - the consumer no longer agrees with the producer"); bad = 1
r = subprocess.run([sys.executable, "-X", "utf8", ".trae/scripts/dev/checkers_selftest.py"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
print("sibling-gate checkers_selftest rc=%d (known debt, printed not asserted)" % r.returncode)
SLUG = "screen-inventory-parity-ledger-005"
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
    print("FAIL 005 must sit at exactly one PASS and exactly one size-band WARNING with no error -"
          " the declared judged value is what makes the band visible (E1's lesson)"); bad = 1
if not m:
    print("FAIL no parsable SUMMARY line - 'cannot read it' is not 'it is fine'"); bad = 1
a = subprocess.run(["git", "-c", "core.quotepath=false", "diff", "--name-only", "--", "android"],
                   capture_output=True, text=True, encoding="utf-8", errors="replace")
now = {}
for q in [l for l in (a.stdout or "").splitlines() if l.strip()]:
    now[q] = hashlib.sha256(open(q, "rb").read()).hexdigest() if os.path.isfile(q) else "ABSENT"
start = json.load(open(os.path.join("artifacts", "tmp", "_g005", "android-start.json"),
                       encoding="utf-8"))
movers = sorted(k for k in set(start) | set(now) if start.get(k) != now.get(k))
print("android/ dirty at start=%d at end=%d moved during this run=%d movers=%s"
      % (len(start), len(now), len(movers), movers))
if movers:
    print("FAIL android/ moved inside this run's window - while the production surface is being"
          " written under the run, the keep-behaviour claim cannot be certified; re-run the"
          " generator once the neighbouring writer stops rather than dropping this judgement")
    bad = 1
# 正控：形状必须是「一条本来干净的路径在窗口内变脏」，比对必须点出它——否则这条守卫是空见证
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
WANT_TEXT="sibling-gate 007 ledger selftest rc=0|sibling-gate 007 ledger generator rc=0|sibling-gate 003 parity selftest rc=0|sibling-gate 004 design-token guard rc=0|sibling-gate checkers_selftest rc=|known debt, printed not asserted|this task: PASS count=1 WARNING count=1 (size band=1) ERROR count=0|moved during this run=0 movers=[]|positive control clean->dirty named=True|SIBLINGS ok"
run 7 0 "sibling gates stay green and the consumer still agrees with the producer, the known rc=3 debt is printed rather than folded into a green claim, and 005's own band is visible" \
  python -X utf8 "$TMP/siblings.py"

# ---- block 08: encoding gate, self-probed both ways ---------------------------------------
cat > "$TMP/utf8_gate.py" <<'PY'
"""闸门自己正反自检：已知坏字节串必须被拒（正控），本片产物与工具必须通过（负控）。
清单 JSON 由块 06 重导出过，因此它的字节也在闸门覆盖里。"""
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
    print("ok %s bytes=%d" % (p, len(raw)))
print("gate covered %d file(s)" % len(sys.argv[1:]))
print("EXITCODE=%d" % (1 if bad else 0))
sys.exit(1 if bad else 0)
PY
WANT_TEXT="GATE SELFTEST ok|EXITCODE=0|gate covered"
run 8 0 "encoding gate over the instruments, the filed inventory, the spec it reads and this generator, self-probed both ways" \
  python -X utf8 "$TMP/utf8_gate.py" "$GEN" "$SELF" "$INVDATA" "$SPEC" \
  artifacts/tmp/gen-005-red.sh artifacts/tmp/gen-005-green.sh \
  artifacts/evidence/T-2026-10-15-005-red.txt \
  .trae/tasks/T-2026-10-15-screen-inventory-parity-ledger-005.md

# --- derived total ---------------------------------------------------------------------------
for d in "$TMP" artifacts/tmp/_g005c; do
  if [ -d "$d" ]; then
    longest=$(python -X utf8 -c "
import os, sys
best = 0
for base, _d, fs in os.walk(sys.argv[1]):
    for f in fs:
        best = max(best, len(os.path.join(base, f)))
print(best)
" "$d")
    if [ "$longest" -gt 200 ]; then
      echo "PATH FAULT: scratch longest path ${longest} chars under ${d} is too close to 260" | tee -a "$CONSOLE"
      bad=$((bad + 1))
    fi
    echo "scratch longest path under ${d} = ${longest} chars (limit 260, margin $((260 - longest)))" | tee -a "$CONSOLE"
  fi
done

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
