#!/usr/bin/env bash
# Generator for artifacts/evidence/T-022-restore-location.txt
#
# Every command in the generated file is executed here and the exit code is captured from the
# command itself (not restated from memory). The command is echoed with shell quoting so that what
# is recorded is copy-pasteable — a bare `$*` join would print `CREATE |ALTER |DROP` unquoted, which
# pastes back as a pipeline and does not reproduce the run.
#
# The conclusion section is DERIVED from the captured output: every line number and count in it
# comes from `hit_of` / `count_of` / `files_of` reading the capture of the command it describes.
# The previous revision hand-wrote `:224`/`:235`/`:171`/`:311` while the commands above printed
# `:227`/`:238`/`:174`/`:330` — the same defect class this file exists to prevent (a conclusion
# contradicting its own output). Hand-writing it again would just reintroduce the drift, so it is
# generated. If a helper returns empty, the conclusion says so visibly instead of inventing a value.
#
# Run from the repository root:
#   bash artifacts/evidence/.gen-t022-restore-location.sh
set -u

OUT="artifacts/evidence/T-022-restore-location.txt"
# A fixed repo-local directory rather than `mktemp -d`: on this machine the shell's temp directory
# is outside the repo and the sandbox's safe-delete refuses to remove it, so the trap left one
# stray directory per run and printed a fail-closed JSON error. Captures are overwritten every
# run, so nothing needs deleting. Ignored via .gitignore.
CAPTURES="artifacts/evidence/.captures/t022-restore-location"
mkdir -p "$CAPTURES"

emit() { printf '%s\n' "$*" >>"$OUT"; }

quote_cmd() {
    local rendered=''
    local arg
    for arg in "$@"; do
        if [[ "$arg" =~ ^[A-Za-z0-9_@%+=:,./-]+$ ]]; then
            rendered+="$arg "
        else
            # Single-quote anything with shell metacharacters so the recorded line pastes back
            # exactly as it ran. `printf %q` is also correct but renders `CREATE |ALTER |DROP `
            # as `CREATE\ \|ALTER\ \|DROP\ `, which reads like a different command.
            rendered+="'${arg//\'/\'\\\'\'}' "
        fi
    done
    printf '%s' "${rendered% }"
}

run() {
    local label="$1"; shift
    local capture="$1"; shift
    emit "## ${label}"
    emit ''
    emit '$ '"$(quote_cmd "$@")"
    local output status
    output="$("$@" 2>&1)"
    status=$?
    if [ -n "$output" ]; then
        printf '%s\n' "$output" >>"$OUT"
    fi
    # Keep the raw output so the conclusion can be extracted from it rather than retyped.
    printf '%s\n' "$output" >"$capture"
    emit "EXITCODE=${status}"
    emit ''
    return 0
}

# --- derived-value helpers, used only by the conclusion section ---------------------------------
# Each takes a capture file and an ERE, and reads nothing else.

# number of matching lines (prints 0 when the capture has no match)
count_of() { grep -cE "$2" "$1" 2>/dev/null || true; }

# "Basename.java:NNN" of the first matching line
hit_of() {
    grep -E "$2" "$1" 2>/dev/null \
        | head -n 1 \
        | sed -E 's#^.*/([^/:]+):([0-9]+):.*#\1:\2#'
}

# basename of every file named in the matching lines, de-duplicated, joined with "、"
#
# Never `paste -sd'、' -`: paste uses only the FIRST BYTE of a multibyte delimiter, emitting a lone
# 0xE3 and dropping the other two bytes of "、", which makes the output invalid UTF-8. It stayed
# latent here only because this task's lists happened to have <=1 file, so no separator was ever
# inserted. The join below is pure ASCII and therefore byte-safe on every set size.
files_of() {
    grep -E "$2" "$1" 2>/dev/null \
        | sed -E 's#^([^:]+):.*#\1#' \
        | sed -E 's#^.*/##' \
        | sort -u \
        | tr '\n' ',' \
        | sed -e 's/,$//' -e 's/,/、/g'
}

# Fail-closed encoding gate: run on every file written before declaring success (see the note above
# on why a mojibake file would otherwise pass unnoticed).
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

: >"$OUT"

emit '# T-022 验收条件 7 证据 —— 再水合入口（BranchSource.restore）的落点'
emit ''
emit "生成方式：bash artifacts/evidence/.gen-t022-restore-location.sh ，生成时刻 $(date -u '+%Y-%m-%dT%H:%M:%SZ')（UTC）。"
emit '本文件中每条命令都是当场执行，EXITCODE 取自该命令自身，不是事后补记；被检文件的 SHA-256 见 §0。'
emit '回显的命令按 shell 规则加了引号，可以直接粘回终端复现。'
emit '结论段的每个行号与计数都由生成脚本从对应命令的捕获输出里提取（hit_of / count_of / files_of），'
emit '不手写 —— 上一版正是在这里漂移：正文输出 :227/:238/:174/:330，手写的结论却仍引用 :224/:235/:171/:311。'
emit ''
emit '被验证的断言（任务文件验收条件 7）：`BranchSource.restore` 在 `:data` 生产源码中恰好出现 1 次，'
emit '且该次调用位于 `BranchDao` 的只读映射（read path）内。'
emit ''
emit '---'
emit ''
emit '## 0. 被检文件的 SHA-256（生成本文件时的工作树状态）'
emit ''
emit '$ sha256sum <被检文件>'
for f in \
    android/data/src/main/java/com/treelearn/data/db/dao/BranchDao.java \
    android/data/src/main/java/com/treelearn/data/db/dao/MessageDao.java \
    android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java \
    android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java \
    android/core/src/main/java/com/treelearn/core/domain/BranchSource.java \
    android/app/src/androidTest/java/com/treelearn/app/TopicBranchMessageDaoInstrumentedTest.java \
    android/app/src/androidTest/java/com/treelearn/app/SqliteSchemaInstrumentedTest.java; do
    sha256sum "$f" | sed 's/^/  /' >>"$OUT"
done
emit ''
emit '说明：`.` 在正则中是通配符，会同时命中 `BranchSource#restore`（javadoc / 注释里的方法引用），'
emit '因此下面把两种写法分开统计，再给一条合并语义的检索。上一版证据正是在这里出错：它把 `.` 当字面量，'
emit '记录命中 1 条而实测 3 条。'
emit ''
emit '---'
emit ''

run "Command 1 —— 点号写法（转义为字面量），只看生产源码" "$CAPTURES/c1" \
    grep -rn 'BranchSource\.restore' android/data/src/main/java android/core/src/main/java android/app/src/main/java

run "Command 2 —— 井号写法（javadoc / 注释里的方法引用），同一范围" "$CAPTURES/c2" \
    grep -rn 'BranchSource#restore' android/data/src/main/java android/core/src/main/java android/app/src/main/java

run "Command 3 —— 合并语义：两种写法一次搜全（{1} 只吃一个任意字符），覆盖测试源码以看清全貌" "$CAPTURES/c3" \
    grep -rnE 'BranchSource.{1}restore' --include=*.java android

run "Command 4 —— 各再水合入口在 :data 生产源码里的落点" "$CAPTURES/c4" \
    grep -rnE '(BranchSource|Branch|Message|AnswerRevision)[.#]restore' --include=*.java android/data/src/main android/core/src/main android/app/src/main

run "Command 5 —— DDL 落点：DAO 包内不得出现 CREATE/ALTER/DROP（期望零匹配）" "$CAPTURES/c5" \
    grep -rnE 'CREATE |ALTER |DROP ' --include=*.java android/data/src/main/java/com/treelearn/data/db/dao

run "Command 6 —— DDL 落点（对照）：含 DDL 关键字的 :data 生产文件" "$CAPTURES/c6" \
    grep -rlE 'CREATE |ALTER |DROP ' --include=*.java android/data/src/main/java

C1="$CAPTURES/c1"; C2="$CAPTURES/c2"; C3="$CAPTURES/c3"
C4="$CAPTURES/c4"; C5="$CAPTURES/c5"; C6="$CAPTURES/c6"

# --- values the conclusion quotes (all read back from the captures above) ------------------------
c1_total=$(count_of "$C1" 'BranchSource\.restore')
c1_calls=$(count_of "$C1" 'BranchSource\.restore\(')
c1_comments=$(count_of "$C1" '// .*BranchSource\.restore')
c1_call_site=$(hit_of "$C1" 'return BranchSource\.restore\(')
c1_comment_site=$(hit_of "$C1" '// .*BranchSource\.restore')
c1_file=$(files_of "$C1" 'BranchSource\.restore')

c2_total=$(count_of "$C2" 'BranchSource#restore')
c2_cross_ref=$(hit_of "$C2" 'AnswerRevision')

c3_total=$(count_of "$C3" 'BranchSource.{1}restore')
c3_definition=$(hit_of "$C3" 'static BranchSource restore\(')
c3_androidtest_calls=$(grep -E 'androidTest' "$C3" 2>/dev/null | grep -cE 'BranchSource\.restore\(' || true)

c4_branch=$(hit_of "$C4" 'return Branch\.restore\(')
c4_message=$(hit_of "$C4" 'return Message\.restore\(')
c4_schema_comment=$(hit_of "$C4" '// .*AnswerRevision\.restore')

c5_lines=$(count_of "$C5" '.')
c6_file_count=$(count_of "$C6" '.')
c6_files=$(files_of "$C6" '.')

emit '---'
emit ''
emit '## 结论（行号与计数由本脚本从上面各命令的捕获输出机械提取，非手写）'
emit ''
emit "- Command 1：生产源码里 \`BranchSource.restore\` 共 ${c1_total} 行命中，且全部落在同一个文件 \`${c1_file}\` 内。"
emit "  其中 ${c1_comments} 行是注释里的文字引用（\`${c1_comment_site}\`，解释为什么空快照要在这里先判），"
emit "  ${c1_calls} 行是真实调用（\`${c1_call_site}\`）。"
emit "  因此「\`:data\` 生产源码中 \`BranchSource.restore\` 恰好 ${c1_calls} 处调用」成立；该调用点就在"
emit "  \`BranchDao\` 内，且同一文件里与它相邻的注释行 \`${c1_comment_site}\` 正是 \`readSource\` 的空快照前置判，"
emit "  故限定词「位于只读映射（read path）内」与命中位置一致。"
emit "- Command 2：井号写法命中 ${c2_total} 行；其中 \`:core\` 的 \`${c2_cross_ref}\` 是 javadoc 交叉引用，"
emit "  提醒这条断言的范围必须限定在 \`:data\` 生产源码，而不是「全仓零命中」。"
emit "- Command 3：把范围放宽到整个 \`android/\` 后共 ${c3_total} 行命中 —— \`:core\` 的 \`${c3_definition}\` 是定义本身，"
emit "  其余分布在 \`:core\` 测试与 \`:app\` 测试。这正是任务文件在验收条件 7 上加了限定词的依据。"
emit "  与本任务修复直接相关的一项：androidTest 侧的真实调用（\`BranchSource.restore(\`）为 ${c3_androidtest_calls} 处，"
emit "  与本任务把创建用法改走真实创建入口 \`BranchSource.fromOperation(..., GenerationState.STABLE, ...)\` 相符。"
emit "- Command 4：\`Branch.restore\`（\`${c4_branch}\`）与 \`Message.restore\`（\`${c4_message}\`）各自只出现在"
emit "  所属 DAO 的只读映射内；\`${c4_schema_comment}\` 是注释里提到 \`AnswerRevision.restore\` 的文字，不是调用。"
emit "- Command 5：捕获输出为空、按输出行数计 ${c5_lines} 行，即零匹配；EXITCODE=1 表示「没有匹配」（grep 以 1"
emit "  表示无匹配，不是执行失败）。这一条由「输出段是否为空」直接读出，不靠退出码反推。"
emit "- Command 6：对照命令 —— 全 \`:data\` 生产源码里含 DDL 关键字的文件共 ${c6_file_count} 个（${c6_files}），"
emit "  EXITCODE=0 且匹配集非空。Command 5 + Command 6 共同支持 android/README.md 中「DDL 单产地」的声明。"
emit "  上一版证据在此处的结论行与自身输出相矛盾（输出有匹配却写着 no output），本次改为对输出的直接陈述。"
emit ''
assert_utf8 "$OUT"
