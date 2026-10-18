#!/usr/bin/env bash
# T-2026-10-15-009 提交后回读的承重实测：把 gen-009-post-commit.py 的每条守卫人为退化，
# 确认它**当场转红**，而不是只在产物里自称「这条判据有意义」。
#
# 为什么需要这支：post-commit 回读里最值钱的两条（反向对照按角色切、归档字节押在父提交上）
# 都是「防未来」的判据——今天的真实数据同时满足强口径与弱口径，只看产物无法分辨它们是不是活的。
# 003 的教训（把「必须携带移动前提交」写成「有任何历史即可」会假过）必须由退化实验托底，
# 否则下一片会把「有反向对照」当成前提而不是当成实测。
#
# 副本一律放在 artifacts/tmp/（与生产文件**同深度**）：被测脚本按自身位置上溯两级算仓库根，
# 放进更深一层的目录会让根算错而得到一份假见证。OUT 一律改到副本同目录，所以这些实验
# 不会覆盖已入库的产物；用完即删（本脚本末尾自己清）。
set -u
cd "$(git rev-parse --show-toplevel)" || exit 3
GEN=artifacts/tmp/gen-009-post-commit.py
EV=artifacts/evidence/T-2026-10-15-009-post-commit-probes.txt
STDERR=artifacts/tmp/_pc009-probes-stderr.txt
: > "$STDERR"

sha_of_gen() { sha256sum "$GEN" | cut -d' ' -f1; }
GEN_SHA_BEFORE=$(sha_of_gen)

{
  echo "# T-2026-10-15-009 提交后回读的承重实测（退化实验跑在副本上，不碰生产文件）"
  echo "# 生成命令：bash artifacts/tmp/gen-009-post-commit-probes.sh"
  echo "# 被核脚本：$GEN（改前改后哈希比对见末块；值不抄进正文，复算 sha256sum $GEN）"
  echo "# 副本深度：全部落在 artifacts/tmp/，与生产文件同级"
  echo "# 期望表：每条退化都要**当场**转红；副本跑不红就说明那条守卫从未承重"
} > "$EV" 2>> "$STDERR"
if [ -s "$STDERR" ]; then
  cat "$STDERR"
  echo "FAULT 头部写入有 stderr 输出，正文可能丢了一段（这正是要防的失败形状）"
  exit 3
fi

ROWS=()
OUT_SED='s@^OUT = os.path.join(EV, "T-2026-10-15-009-post-commit.txt")@OUT = os.path.join(HERE, "OUTNAME")@'

# apply <name> <锚点串> <变异串> <sed 表达式>
# 两个命中数都是判据：锚点在**生产文件**里必须恰好出现一次（改过脚本后 sed 可能静默失配，
# 于是「副本仍然全绿」是在证明一份没被改过的文件），变异串必须在**副本**里恰好出现一次。
apply() {
  local name="$1" anchor="$2" mut="$3" expr="$4"
  local copy="artifacts/tmp/_pc009-${name}.py"
  local hits_old hits_new
  hits_old=$(grep -cF -- "$anchor" "$GEN")
  sed -e "$expr" -e "${OUT_SED/OUTNAME/_pc009-${name}.txt}" "$GEN" > "$copy"
  hits_new=$(grep -cF -- "$mut" "$copy")
  if [ "$hits_old" != "1" ] || [ "$hits_new" != "1" ]; then
    printf 'MUTATION FAULT %s: 锚点在生产文件命中=%s（须 1）、变异串在副本命中=%s（须 1）——' \
      "$name" "$hits_old" "$hits_new" >> "$EV"
    echo "sed 已静默失配，这份副本不是它自称的那个变异" >> "$EV"
    return 1
  fi
  printf '%s' "$copy"
}

judge() {
  local name="$1" want_rc="$2" witness="$3" copy="$4" log="$5"
  local got_rc hits
  python -X utf8 "$copy" > "$log" 2>&1
  got_rc=$?
  hits=$(grep -cF -- "$witness" "$log")
  printf '  %s want-rc=%s got-rc=%s 见证行命中=%s\n' "$name" "$want_rc" "$got_rc" "$hits" >> "$EV"
  printf '     见证串：%s\n' "$witness" >> "$EV"
  grep -m1 '^DECODE gate' "$log" | cut -d' ' -f1-4 | tr -d '\r' >> "$EV"
  if [ "$got_rc" != "$want_rc" ] || [ "$hits" != "1" ]; then
    printf '  block %s: want=rc=%s 且命中=1 -> got=rc=%s 命中=%s <- MISMATCH\n\n' \
      "$name" "$want_rc" "$got_rc" "$hits" >> "$EV"
    ROWS+=("1")
  else
    printf '  block %s: want=0 got=0\n\n' "$name" >> "$EV"
    ROWS+=("0")
  fi
}

run_probe() {
  local name="$1" anchor="$2" mut="$3" expr="$4" want="$5" witness="$6" head="$7"
  local copy
  # 参数个数判死：多出来的引号串会被静默丢弃（bash 的相邻引号串是**不同参数**而不是拼接），
  # 那正是「产物仍然全绿、说明文字少了一段」那一族的形态。
  if [ "$#" != "7" ]; then
    echo "  block $name: 调用给了 $# 个参数（须 7）——说明文案被拆成了多余实参，拒绝继续" >> "$EV"
    ROWS+=("1")
    return
  fi
  echo "--- $head" >> "$EV"
  copy=$(apply "$name" "$anchor" "$mut" "$expr")
  if [ -z "$copy" ]; then
    ROWS+=("1")
    echo "  block $name: 副本没建成 -> MISMATCH" >> "$EV"
    echo "" >> "$EV"
    return
  fi
  judge "$name" "$want" "$witness" "$copy" "artifacts/tmp/_pc009-${name}.log"
}

echo "" >> "$EV"
run_probe p0 'THRESHOLDS = ["", "-M40%", "-M10%", "-M1%"]' \
  'THRESHOLDS = ["", "-M40%", "-M10%", "-M1%"]' 's/@@no-such-anchor@@/x/' 0 \
  'blocks=9 floor=9 expectation_entries=9 faults=0' \
  'P0 基线：只改 OUT、正文逐字不动，副本必须仍然全绿——否则下面每一块的红都可能是「副本本来就红」'

run_probe p1 'pre_move = [c for c in old_commits if c != del_id]' \
  'pre_move = list(old_commits)  # MUTATION: 退回未切分口径' \
  's@pre_move = \[c for c in old_commits if c != del_id\]@pre_move = list(old_commits)  # MUTATION: 退回未切分口径@' \
  1 'FAIL 反向对照为 0 携带' \
  'P1 把「对照文件必须不携带移动前提交」退回「有任何历史即可」（003 的实测缺陷形状）：块 02 必须转红'

run_probe p2 'rc_blob, blob, _ = run_bytes(["git", "show", "%s^:%s" % (del_id, OLD_REL)])' \
  'rc_blob, blob, _ = run_bytes(["git", "show", "%s:%s" % (del_id, OLD_REL)])' \
  's@rc_blob, blob, _ = run_bytes(\["git", "show", "%s\^:%s"@rc_blob, blob, _ = run_bytes(["git", "show", "%s:%s"@' \
  1 'FAIL 归档的前身副本与「移除那次提交的父提交」上的字节逐字节相同' \
  'P2 把归档字节的出处从「移除那次提交的父提交」挪到那次提交本身（那里路径已不存在）：块 03 必须转红。锚点与变异串都带上赋值左端，因为副本里本来就有一条形状相同的对照调用（same_at_move），不带左端时变异串命中 2 行、apply 判 MUTATION FAULT——那是第一次实跑实测到的'

run_probe p3 '\b[0-9a-f]{7,40}\b' '\b[0-9a-f]{41,60}\b' 's/7,40/41,60/' 1 \
  'FAIL 扫描对合成的 40 位提交号形状会响' \
  'P3 让提交号形状的扫描匹配不到 7~40 位：块 04 与块 09 的两条正控必须同时失效（否则「没有裸提交号」只是没在扫）'

run_probe p4 'MIN_BLOCKS = 9' 'MIN_BLOCKS = 10' 's/^MIN_BLOCKS = 9.*/MIN_BLOCKS = 10           # MUTATION: 下限抬一条/' 3 \
  'FLOOR FAULT' \
  'P4 判定块下限抬一条：必须走独立的中止通道 rc=3，不能与「用例不符」的 rc=1 同码'

echo "===== 生产文件未被改动 =====" >> "$EV"
GEN_SHA_AFTER=$(sha_of_gen)
if [ "$GEN_SHA_BEFORE" = "$GEN_SHA_AFTER" ]; then
  echo "  ok   $GEN 跑前跑后哈希相同（复算：sha256sum $GEN）" >> "$EV"
  ROWS+=("0")
else
  echo "  FAIL $GEN 被这次实验改动——副本实验不许碰生产文件" >> "$EV"
  ROWS+=("1")
fi
echo "" >> "$EV"

FAILS=0
for r in "${ROWS[@]}"; do
  [ "$r" = "0" ] || FAILS=$((FAILS + 1))
done
EXPECTED=${#ROWS[@]}
echo "===== 汇总 =====" >> "$EV"
echo "  entries=${EXPECTED}（由期望表条目数现场得出，不是手抄） mismatch=${FAILS}" >> "$EV"
if [ "$EXPECTED" -lt 6 ]; then
  echo "  FLOOR FAULT 判定条目少于 6 条（apply 失败会让条目缩水），总退出码按 3 处理" >> "$EV"
  TOTAL=3
elif [ "$FAILS" -eq 0 ]; then
  TOTAL=0
else
  TOTAL=1
fi
printf 'OVERALL_EXITCODE=%d\n' "$TOTAL" >> "$EV"

# 编码闸门：本机 Git Bash **没有 iconv**（第一次实跑时 `command -v iconv` 为空，闸门因此
# 以 rc=127 判「DECODE FAIL」——方向安全，但读者会把它读成编码缺陷而不是工具缺失）。
# 现在用 python 严格解码，并且先自证这条闸门是活的：已知的坏字节必须被它拒绝。
BAD=artifacts/tmp/_pc009-gate-bad.txt
printf '\351' > "$BAD"          # 0xE9 是非法 UTF-8 起始字节（孤立的多字节首字节）
python -X utf8 -c 'import sys; open(sys.argv[1], "rb").read().decode("utf-8", "strict")' "$BAD" \
  > /dev/null 2>&1
BAD_RC=$?
python -X utf8 -c 'import sys; open(sys.argv[1], "rb").read().decode("utf-8", "strict")' "$EV" \
  > /dev/null 2>&1
EV_RC=$?
CR=$(tr -cd '\r' < "$EV" | wc -c | tr -d ' ')
if [ "$BAD_RC" != "1" ]; then
  printf 'DECODE FAULT validator: 已知坏字节被返回 rc=%s（须为 1）——校验工具缺失或闸门是死的\n' "$BAD_RC" >> "$EV"
  TOTAL=3
elif [ "$EV_RC" = "0" ] && [ "$CR" = "0" ]; then
  printf 'DECODE ok utf8=True cr=0 bad-byte-control-rejected=True\n' >> "$EV"
else
  printf 'DECODE FAIL utf8-rc=%s cr=%s\n' "$EV_RC" "$CR" >> "$EV"
  TOTAL=3
fi
tail -2 "$EV"
rm -f artifacts/tmp/_pc009-p*.py artifacts/tmp/_pc009-p*.txt artifacts/tmp/_pc009-p*.log \
  artifacts/tmp/_pc009-probes-stderr.txt artifacts/tmp/_pc009-gate-bad.txt
exit "$TOTAL"
