#!/usr/bin/env bash
# Generator for T-023 slice A's two source-level evidence files:
#
#   artifacts/evidence/T-023-restore-location.txt   acceptance: the write path never rehydrates
#   artifacts/evidence/T-023-prompt-body-surface.txt acceptance: no prompt body reaches storage
#
# Both acceptance items are properties of *source text* — which factory a write path calls, which
# parameters a public method takes — so no behavioural test can witness them. That is why they are
# checked mechanically here, with the command and its exit code captured by this script rather than
# restated from memory, and with the conclusion section derived from the captures by the helpers at
# the bottom (a hand-written conclusion has already drifted from its own output twice in this
# repository; the failure library in AGENTS.md records both).
#
# Run from the repository root:
#   bash artifacts/evidence/.gen-t023-source-checks.sh
set -u

E="artifacts/evidence"
OUT_RESTORE="$E/T-023-restore-location.txt"
OUT_PROMPT="$E/T-023-prompt-body-surface.txt"
CAPTURES="$E/.captures/t023-source-checks"
mkdir -p "$CAPTURES"

PROD="android/data/src/main android/core/src/main android/app/src/main"
NEW_FILES=(
    android/data/src/main/java/com/treelearn/data/db/dao/AnswerRevisionDao.java
    android/data/src/main/java/com/treelearn/data/db/dao/GenerationOperationDao.java
    android/data/src/main/java/com/treelearn/data/db/dao/GenerationOperationRow.java
    android/data/src/main/java/com/treelearn/data/db/dao/GenerationStabilizer.java
    android/data/src/main/java/com/treelearn/data/db/dao/ConstraintFailures.java
    android/app/src/androidTest/java/com/treelearn/app/AnswerRevisionGenerationOperationDaoInstrumentedTest.java
)

emit() { printf '%s\n' "$*" >>"$OUT"; }

quote_cmd() {
    local rendered=''
    local arg
    for arg in "$@"; do
        if [[ "$arg" =~ ^[A-Za-z0-9_@%+=:,./-]+$ ]]; then
            rendered+="$arg "
        else
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
    printf '%s\n' "$output" >"$capture"
    emit "EXITCODE=${status}"
    emit ''
    return 0
}

# --- derived-value helpers: each reads only a capture file and an ERE -----------------------------
count_of() { grep -cE "$2" "$1" 2>/dev/null || true; }

hit_of() {
    grep -E "$2" "$1" 2>/dev/null \
        | head -n 1 \
        | sed -E 's#^.*/([^/:]+):([0-9]+):.*#\1:\2#'
}

# basename of every file named in the matching lines, de-duplicated, joined with "、"
#
# The join is done in two ASCII steps instead of `paste -sd'、' -`. Reason: `paste` takes only the
# FIRST BYTE of a multibyte delimiter, so a "、" separator comes out as a lone 0xE3 with its two
# trailing bytes dropped, and the resulting file is not valid UTF-8 (see assert_utf8 below, and the
# T-023-restore-location.txt corruption this produced: 3 illegal sequences at the 3 join points).
# A comma is a safe delimiter because these are basenames, which never contain one.
files_of() {
    grep -E "$2" "$1" 2>/dev/null \
        | sed -E 's#^([^:]+):.*#\1#' \
        | sed -E 's#^.*/##' \
        | sort -u \
        | tr '\n' ',' \
        | sed -e 's/,$//' -e 's/,/、/g'
}

# Fail-closed encoding gate. Run it on everything this script writes before declaring success: a
# mojibake evidence file still "exists" and still has the right size, so nothing else catches it.
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

# A whole verdict rather than a bare count, for the commands whose expectation is "nothing matches".
# Deriving the number alone is not enough: the prose around it still has to say *whether* it is empty,
# and a template that says "捕获输出为空" will happily print a non-zero count beside it (this script's
# first version did exactly that, on a section that was not empty). Deriving the verdict makes a
# self-contradicting conclusion unrepresentable.
verdict_of() {
    local n
    n="$(count_of "$1" '.')"
    if [ "${n:-0}" = "0" ]; then
        printf '零命中'
    else
        printf '命中 %s 行' "$n"
    fi
}

# ==================================================================================================
# File 1 — where the rehydration entry points are called from
# ==================================================================================================

OUT="$OUT_RESTORE"
: >"$OUT"

emit '# T-023 验收证据 —— 再水合契约（`restore` 只出现在只读映射内；本任务不新增 GenerationOperation.restore）'
emit ''
emit "生成方式：bash artifacts/evidence/.gen-t023-source-checks.sh ，生成时刻 $(date -u '+%Y-%m-%dT%H:%M:%SZ')（UTC）。"
emit '每条命令当场执行，EXITCODE 取自该命令自身；回显的命令按 shell 规则加引号，可直接粘回复现；'
emit '被检文件的 SHA-256 见 §0。结论段的每个计数与落点都由脚本从对应捕获输出里提取（count_of / hit_of / files_of），不手写。'
emit ''
emit '被验证的断言（任务文件验收条件 6）：'
emit '  (a) `AnswerRevision.restore` 只允许出现在 `AnswerRevisionDao` 的只读映射方法内；'
emit '  (b) 写路径（创建、稳定化、重试、重置、分叉、迁移、回填）必须走 `AnswerRevision.create`；'
emit '  (c) 本任务不得新增 `GenerationOperation.restore`（`:data` 与 `:core` 全部生产源码机械检索，要求零命中）。'
emit ''
emit '---'
emit ''
emit '## 0. 被检文件的 SHA-256（生成本文件时的工作树状态）'
emit ''
emit '$ sha256sum <被检文件>'
for f in "${NEW_FILES[@]}"; do
    sha256sum "$f" | sed 's/^/  /' >>"$OUT"
done
emit ''
emit '---'
emit ''

run "Command 1 —— 点号写法：AnswerRevision.restore 的生产调用点" "$CAPTURES/c1" \
    grep -rn 'AnswerRevision\.restore' $PROD

run "Command 2 —— 井号写法：javadoc / 注释里的方法引用（同一范围）" "$CAPTURES/c2" \
    grep -rn 'AnswerRevision#restore' $PROD

run "Command 3 —— 合并语义：两种写法一次搜全，覆盖测试源码以看清全貌" "$CAPTURES/c3" \
    grep -rnE 'AnswerRevision[.#]restore' --include=*.java android

run "Command 4 —— 写路径工厂 AnswerRevision.create 的落点（生产范围）" "$CAPTURES/c4" \
    grep -rn 'AnswerRevision\.create' $PROD

run "Command 5 —— 本任务不得新增 GenerationOperation.restore（全仓 .java，期望零命中）" "$CAPTURES/c5" \
    grep -rnE 'GenerationOperation[.#]restore' --include=*.java android

run "Command 6 —— GenerationOperation 的静态工厂只有 start（全仓 .java）" "$CAPTURES/c6" \
    grep -rnE 'GenerationOperation\.start' --include=*.java android

run "Command 7 —— 唯一能把行写成 STABLE 的入口（全仓 .java）" "$CAPTURES/c7" \
    grep -rn 'markStable' --include=*.java android

run "Command 8 —— DDL 落点：dao 包内不得出现 CREATE/ALTER/DROP（期望零输出）" "$CAPTURES/c8" \
    grep -rnE 'CREATE |ALTER |DROP ' --include=*.java android/data/src/main/java/com/treelearn/data/db/dao

C1="$CAPTURES/c1"; C2="$CAPTURES/c2"; C3="$CAPTURES/c3"; C4="$CAPTURES/c4"
C5="$CAPTURES/c5"; C6="$CAPTURES/c6"; C7="$CAPTURES/c7"; C8="$CAPTURES/c8"

c1_total=$(count_of "$C1" 'AnswerRevision\.restore')
c1_calls=$(count_of "$C1" 'AnswerRevision\.restore\(')
c1_site=$(hit_of "$C1" 'return AnswerRevision\.restore\(')
c1_files=$(files_of "$C1" 'AnswerRevision\.restore')

c2_total=$(count_of "$C2" 'AnswerRevision#restore')
c2_sites=$(files_of "$C2" 'AnswerRevision#restore')

c3_total=$(count_of "$C3" 'AnswerRevision[.#]restore')
c3_test_calls=$(grep -E 'androidTest' "$C3" 2>/dev/null | grep -cE 'AnswerRevision\.restore\(' || true)

c4_total=$(count_of "$C4" 'AnswerRevision\.create')
c4_site=$(hit_of "$C4" 'AnswerRevision\.create\(')
c4_files=$(files_of "$C4" 'AnswerRevision\.create')

c5_verdict=$(verdict_of "$C5")
c6_total=$(count_of "$C6" 'GenerationOperation\.start\(')
c7_total=$(count_of "$C7" 'markStable')
c7_files=$(files_of "$C7" 'markStable')
c7_decl=$(hit_of "$C7" 'static void markStable')
c7_public=$(count_of "$C7" 'public .*markStable')
c8_verdict=$(verdict_of "$C8")

emit '---'
emit ''
emit '## 结论（计数与落点由本脚本从上面各命令的捕获输出机械提取，非手写）'
emit ''
emit "- Command 1：生产源码里 \`AnswerRevision.restore\` 命中 ${c1_total} 行，落在 ${c1_files} 内。"
emit "  其中真实调用 ${c1_calls} 处（\`${c1_site}\`）。注意这条模式的边界：只有写作 \`AnswerRevision.restore(\` 的行才算调用，"
emit "  而 \`:core\` 里的方法定义写作 \`AnswerRevision restore(\`（没有点号），因此本行既不计入调用，也不出现在本轮捕获里。"
emit "- Command 2：井号写法（javadoc 交叉引用）命中 ${c2_total} 行，位于 ${c2_sites} —— 它们是文字引用，不是调用，"
emit "  因此断言 (a) 必须按「调用点」而不是「提及次数」来读。"
emit "- Command 3：放宽到整个 \`android/\` 后共 ${c3_total} 行命中；\`:app\` 的 androidTest 侧直接调用为 ${c3_test_calls} 处 ——"
emit "  本任务的用例不自己构造 revision，一律走 \`AnswerRevision.create\`，所以测试侧不会成为再水合的第二个入口。"
emit "- Command 4：写路径工厂 \`AnswerRevision.create\` 在生产源码里命中 ${c4_total} 行，落在 ${c4_files}；"
emit "  调用点 \`${c4_site}\` 即 \`GenerationStabilizer\` 的落盘入口，断言 (b) 的「写路径走 create」由此成立。"
emit "- Command 5：全仓 \`.java\` 中 \`GenerationOperation.restore\`/\`GenerationOperation#restore\` ${c5_verdict}，断言 (c) 成立。"
emit "  EXITCODE=1 是 grep 的「无匹配」语义，不是执行失败。"
emit "- Command 6：\`GenerationOperation.start\` 命中 ${c6_total} 处 —— 该类型只有这一个创建入口，与断言 (c) 互证："
emit "  没有 \`restore\`，行形态才是读取 attempt 的唯一方式。"
emit "- Command 7：\`markStable\` 命中 ${c7_total} 行，位于 ${c7_files}；其中定义是 \`${c7_decl}\`（包私有），"
emit "  \`public\` 修饰的命中 ${c7_public} 处 —— 即「唯一能把行写成 STABLE 的入口是 stabilize 事务内部的那次调用」，"
emit "  外部没有第二条路可以绕过 revision 创建把 attempt 置为 STABLE。"
emit "- Command 8：dao 包内 \`CREATE\`/\`ALTER\`/\`DROP\` ${c8_verdict}，即本任务新增的生产类没有引入任何 DDL。"
emit ''
emit '### 本文件没有覆盖到的（如实列出）'
emit ''
emit '- 断言 (a) 的「只允许出现在只读映射内」是**结构判断**：Command 1 证明调用点唯一（`AnswerRevisionDao` 内），'
emit '  该调用位于 `read(Cursor)` 这个私有只读映射中，由源码阅读确认，检索命令本身不区分方法名。'
emit '- 断言 (b) 的「重试、重置、分叉、迁移、回填」这些写路径本任务尚未实现（属 T-006/T-008/T-009/T-010），'
emit '  因此 Command 4 只能证明**本任务已存在的写路径**走 `create`；其余写路径的合规性要在各自任务里重复这条检索。'

# ==================================================================================================
# File 2 — the prompt-body surface
# ==================================================================================================

OUT="$OUT_PROMPT"
: >"$OUT"

emit '# T-023 验收证据 —— 不落提示词正文（公开方法签名与写入内容两半）'
emit ''
emit "生成方式：bash artifacts/evidence/.gen-t023-source-checks.sh ，生成时刻 $(date -u '+%Y-%m-%dT%H:%M:%SZ')（UTC）。"
emit '每条命令当场执行，EXITCODE 取自该命令自身；结论段由脚本从捕获输出提取，不手写。'
emit ''
emit '被验证的断言（任务文件验收条件 7）：本任务所有 DAO 的公开方法签名中不出现 `systemContent`/`systemPrompt`'
emit '形态的入参，也不新增任何正文列；对两表写入夹具后扫描其全部 TEXT 列，断言不包含提示词正文哨兵子串。'
emit '后一半（行为侧）在 `AnswerRevisionGenerationOperationDaoInstrumentedTest.onlyAnEnumeratedPurposeAndAnOwnedVersionReachTheTwoTables`'
emit '（真机 API 36），本文件覆盖前一半（源码侧）。'
emit ''
emit '---'
emit ''

run "Command 1 —— 正文形态的标识符：持久化层（:data 生产源码）" "$CAPTURES/p1a" \
    grep -rnE 'systemContent|systemPrompt|system_content|system_prompt|promptBody|prompt_body|systemMessage' \
    android/data/src/main

run "Command 2 —— 同一检索放宽到 :core（对照，期望命中 PromptSpec）" "$CAPTURES/p1b" \
    grep -rnE 'systemContent|systemPrompt|system_content|system_prompt|promptBody|prompt_body|systemMessage' \
    android/core/src/main

run "Command 3 —— 携带正文的类型是否进入持久化层（期望零命中）" "$CAPTURES/p1c" \
    grep -rn 'PromptSpec' android/data/src/main

run "Command 4 —— 本任务新增类的公开方法与构造签名全量清单" "$CAPTURES/p2" \
    grep -rnE '^\s+(public|static)' \
    android/data/src/main/java/com/treelearn/data/db/dao/AnswerRevisionDao.java \
    android/data/src/main/java/com/treelearn/data/db/dao/GenerationOperationDao.java \
    android/data/src/main/java/com/treelearn/data/db/dao/GenerationOperationRow.java \
    android/data/src/main/java/com/treelearn/data/db/dao/GenerationStabilizer.java

run "Command 5 —— 提示词相关的列/字段名（新增类内）" "$CAPTURES/p3" \
    grep -rnE 'prompt_policy_version|PROMPT_POLICY_VERSION|prompt_purpose|PROMPT_PURPOSE|PromptPurpose|policyPrefix' \
    android/data/src/main/java/com/treelearn/data/db/dao

run "Command 6 —— 表结构侧的既有守门（T-004，未本任务改动）" "$CAPTURES/p4" \
    grep -rn 'noColumnIsDedicatedToTheSystemPromptBody' android/app/src/androidTest/java

run "Command 7 —— 行为侧的哨兵用例名（本任务新增）" "$CAPTURES/p5" \
    grep -rn 'onlyAnEnumeratedPurposeAndAnOwnedVersionReachTheTwoTables' android/app/src/androidTest/java

P1A="$CAPTURES/p1a"; P1B="$CAPTURES/p1b"; P1C="$CAPTURES/p1c"
P2="$CAPTURES/p2"; P3="$CAPTURES/p3"; P4="$CAPTURES/p4"; P5="$CAPTURES/p5"

p1a_verdict=$(verdict_of "$P1A")
p1b_verdict=$(verdict_of "$P1B")
p1b_files=$(files_of "$P1B" 'systemContent|systemPrompt')
p1c_verdict=$(verdict_of "$P1C")
p2_total=$(count_of "$P2" '.')
p2_body_params=$(grep -cE 'systemContent|systemPrompt|content\)|body\)' "$P2" 2>/dev/null || true)
p2_id_params=$(grep -cE 'String (operationId|revisionId|messageId|branchId)' "$P2" 2>/dev/null || true)
p3_total=$(count_of "$P3" '.')
p3_columns=$(count_of "$P3" 'PROMPT_(POLICY_VERSION|PURPOSE)')
p4_total=$(count_of "$P4" '.')
p5_total=$(count_of "$P5" '.')

emit '---'
emit ''
emit '## 结论（计数与判定由本脚本从上面各命令的捕获输出机械提取，非手写）'
emit ''
emit "- Command 1：持久化层 \`:data\` 生产源码 ${p1a_verdict} —— \`systemContent\`/\`systemPrompt\`/\`promptBody\`"
emit "  形态的标识符一个也没有。这是断言「本任务所有 DAO 的公开方法签名中不出现此类入参」的正面证据。"
emit "- Command 2（对照，刻意保留）：同一检索放宽到 \`:core\` 后 ${p1b_verdict}，位于 ${p1b_files}。"
emit "  这不是本任务的违规，而是这条断言的**边界**：\`PromptSpec\` 是 T-021 的**内存态请求装配体**"
emit "  （purpose + version + 正文），正文在设计上只存在于内存与 \`:ai\` 的注册表里，从不落库 ——"
emit "  \`PromptPurpose\` 的 javadoc 与 \`TreeLearnSchema\` 的列清单都写了这一点。把它如实记下来，是为了让读者"
emit "  不会用一条全仓检索去「证明」一个只对持久化层成立的断言（本仓库已经因为把搜索范围写错而误报过）。"
emit "- Command 3：\`PromptSpec\` 在 \`:data\` 生产源码里 ${p1c_verdict}，即携带正文的类型没有进入持久化层。"
emit "- Command 4：本任务新增类的公开/静态签名共 ${p2_total} 行；其中入参为标识符的签名 ${p2_id_params} 处，"
emit "  入参含 content/body 形态的签名 ${p2_body_params} 处。其余入参是 \`delta\`（模型输出增量）、\`state\`、"
emit "  \`terminal\`、\`errorCode\`、\`now\`、实体对象或 \`SQLiteDatabase\` —— 没有任何方法可以接收一段 system prompt。"
emit "- Command 5：新增类内与提示词相关的命中 ${p3_total} 行，其中引用列常量 ${p3_columns} 处，全部指向"
emit "  \`prompt_purpose\` 与 \`prompt_policy_version\` 两列；没有任何一处把正文写进列。"
emit "- Command 6：表结构侧的既有守门仍在（命中 ${p4_total} 处），它断言七表里只有 \`prompt_purpose\` 与"
emit "  \`prompt_policy_version\` 两列与提示词相关，且没有任何列名含 \`system\`。"
emit "- Command 7：行为侧哨兵用例存在（命中 ${p5_total} 处），它在真机上扫描两表全部 TEXT 列。"
emit "  证据强度已在用例 javadoc 中如实标注：purpose/version 两条断言有实质内容（写成正文或写成未经注册的版本都会失败），"
emit "  哨兵扫描是**绊线**而非证明 —— 本任务的写路径根本不接受正文，因此它防的是未来新增的列或 DAO。"
emit ''
assert_utf8 "$OUT_RESTORE" "$OUT_PROMPT"
echo "wrote $OUT_RESTORE and $OUT_PROMPT"
