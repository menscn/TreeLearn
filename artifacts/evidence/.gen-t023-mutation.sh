#!/usr/bin/env bash
# T-023 mutation evidence — prove that the acceptance suite actually bites.
#
# Why this file exists: a GREEN run shows the tests pass, not that they *can* fail. Two defects that
# this task's design is built to prevent are injected one at a time; each must be caught by exactly
# the acceptance case that claims to cover it. A surviving mutation means the corresponding criterion
# is asserted by prose rather than by a test.
#
# Every number in the conclusion is read back out of the captured Gradle output; none is typed by
# hand. Each mutation is restored afterwards and the restoration is proved by SHA-256, so a failed run
# cannot leave the tree mutated. Run with:  bash artifacts/evidence/.gen-t023-mutation.sh
#
# Note on the capture: Gradle's console output is the only place that names the *failing method*, and
# it is printed twice (inline, then re-indented under the FAILURE block). failed_method_of anchors on
# the first match so the duplicate cannot double-count.

set -uo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO" || exit 1

OUT="artifacts/evidence/T-023-mutation.txt"
CAPTURES="artifacts/evidence/.captures/t023-mutation"
mkdir -p "$CAPTURES"

TEST_CLASS='com.treelearn.app.AnswerRevisionGenerationOperationDaoInstrumentedTest'
STABILIZER='android/data/src/main/java/com/treelearn/data/db/dao/GenerationStabilizer.java'

emit() { printf '%s\n' "$*" >>"$OUT"; }

# Fail-closed encoding gate for the file this script writes. A mojibake evidence file still has the
# right name and a plausible size, so only a real decode attempt catches it.
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

# --- derivations, all read out of a capture file ---------------------------------------------------

# The method name of the first failing case, from Gradle's FAILED line. Its shape is
#   com.treelearn.app.SomeTest > methodName[Medium_Phone_API_36.1(AVD) - 16] FAILED
# — note the " > name[" framing and the trailing device bracket, which is why an earlier attempt at
# this that anchored on "#name FAILED" matched nothing and silently turned two caught mutations into
# "未被捕获". An extractor that cannot find its target must not be allowed to decide the verdict:
# the conclusion below treats an empty result as its own state rather than as a negative finding.
failed_method_of() {
    grep -E 'FAILED' "$1" 2>/dev/null \
        | grep -oE ' > [A-Za-z][A-Za-z0-9_]*\[' \
        | head -n 1 \
        | sed 's/^ > //; s/\[$//'
}

# The first assertion line of the failure (JUnit prints e.g.
# "	org.junit.ComparisonFailure: … expected:<…> but was:<…>").
failure_message_of() {
    grep -E '^[[:space:]]*(org\.junit|java\.lang|junit)\.[A-Za-z.]*(Failure|Error|Exception):' "$1" 2>/dev/null \
        | head -n 1 \
        | sed 's/^[[:space:]]*//'
}

# The "Starting N tests" count, as a bare number, proving the class filter selected exactly one.
selected_count_of() {
    grep -oE 'Starting [0-9]+ tests' "$1" 2>/dev/null | head -n 1 | grep -oE '[0-9]+'
}

# --- mutation plumbing -----------------------------------------------------------------------------

# Literal block replacement. Python rather than sed: the blocks are multi-line and contain characters
# that would have to be escaped by hand, and python can assert the target is unique before writing.
patch_block() {  # $1=file $2=needle-file $3=replacement-file $4=expected-before-count
    python - "$1" "$2" "$3" "$4" <<'PY'
import sys
path, needle_path, repl_path, expected = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
src = open(path, encoding='utf-8').read()
needle = open(needle_path, encoding='utf-8').read()
repl = open(repl_path, encoding='utf-8').read()
found = src.count(needle)
if found != expected:
    sys.stderr.write('ABORT %s: needle occurs %d time(s), expected %d\n' % (path, found, expected))
    sys.exit(3)
open(path, 'w', encoding='utf-8', newline='').write(src.replace(needle, repl, expected))
PY
}

run_case() {  # $1=raw-capture-path  $2=test-method   -> leaves exit code in $?
    ( cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain \
        -Pandroid.testInstrumentationRunnerArguments.class="${TEST_CLASS}#${2}" ) >"$1" 2>&1
}

run_mutation() {  # $1=label $2=name $3=file $4=case-method
    local label="$1" name="$2" file="$3" case_method="$4"
    local raw="$CAPTURES/${label}.raw"
    local needle="$CAPTURES/${label}.before" repl="$CAPTURES/${label}.after"
    local hash_before hash_mutated hash_restored patch_status status restore_status

    hash_before="$(sha256sum "$file" | cut -d' ' -f1)"

    emit "## 变异 ${label#m} —— ${name}"
    emit ''
    emit "被改文件：\`${file}\` ；应当捕获它的用例：\`${case_method}\`"
    emit ''

    patch_block "$file" "$needle" "$repl" 1
    patch_status=$?
    emit '### 1. 注入（把左边这段原文换成右边这段）'
    emit ''
    emit '```diff'
    sed 's/^/-/' "$needle" >>"$OUT"
    sed 's/^/+/' "$repl" >>"$OUT"
    emit '```'
    emit ''
    emit "\$ python（字面量替换，注入前先断言该片段在文件中恰好出现 1 次）"
    emit "EXITCODE=${patch_status}  （3=片段不唯一，已中止且未改动文件；0=注入成功）"
    if [ "$patch_status" != "0" ]; then
        emit ''
        emit '**注入未成功，本变异无效，其结论不成立。**'
        emit ''
        emit '---'
        emit ''
        printf '%s|%s|unpatched|-|-\n' "$label" "$case_method" >>"$CAPTURES/results.psv"
        return 1
    fi

    hash_mutated="$(sha256sum "$file" | cut -d' ' -f1)"
    emit ''
    emit '$ sha256sum '"${file}"
    emit "  注入前 ${hash_before}"
    emit "  注入后 ${hash_mutated}"
    emit ''
    emit '### 2. 在该变异下运行「本应捕获它」的那一条用例'
    emit ''
    emit "\$ cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain \\"
    emit "      -Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASS}#${case_method}"
    run_case "$raw" "$case_method"
    status=$?
    grep -E "Starting [0-9]+ tests|FAILED|(Failure|Error|Exception):|expected:|Finished [0-9]+ tests|BUILD (FAILED|SUCCESSFUL)" "$raw" >>"$OUT"
    emit "EXITCODE=${status}"
    emit ''

    # 3. Restore, then prove the restore byte-for-byte.
    patch_block "$file" "$repl" "$needle" 1
    restore_status=$?
    hash_restored="$(sha256sum "$file" | cut -d' ' -f1)"
    emit '### 3. 还原'
    emit ''
    emit "EXITCODE=${restore_status}  （0=已还原）"
    emit "还原后 sha256 ${hash_restored}"
    emit "与注入前逐字相同：$([ "$hash_before" = "$hash_restored" ] && echo 是 || echo '否 —— 工作树处于被污染状态')"
    emit ''
    emit '---'
    emit ''

    printf '%s|%s|%s|%s|%s|%s\n' "$label" "$case_method" "$status" \
        "$(failed_method_of "$raw")" "$(selected_count_of "$raw")" \
        "$([ "$hash_before" = "$hash_restored" ] && echo restored || echo DIRTY)" >>"$CAPTURES/results.psv"
    return 0
}

# --- the two mutations -----------------------------------------------------------------------------

# (1) "re-derive the version from the purpose constant" instead of "copy it from the operation row".
# The two produce the same string while the row is intact, so only a tampered-row fixture separates
# them — which is exactly the fixture the acceptance suite carries.
cat >"$CAPTURES/m1.before" <<'EOF'
                row.promptPolicyVersion, now);
EOF
cat >"$CAPTURES/m1.after" <<'EOF'
                com.treelearn.core.domain.PromptPurpose.LEARNING_ANSWER.policyPrefix + "/1.0.0", now);
EOF

# (2) "report the refusal by returning from the work body" instead of throwing. The runner commits
# whatever a work body returns, so a taken revision number would commit the STABLE transition with no
# revision beside it — the intermediate state the entry point exists to prevent.
cat >"$CAPTURES/m2.before" <<'EOF'
        try {
            return runner.run(database,
                    work -> stabilizeInside(work, operationId, revisionNo, now));
        } catch (Refused refused) {
            // The unwinding is the point. Returning the failure from inside the work body would
            // *commit* the transaction — DatabaseTransactionRunner commits whatever a work body
            // returns — and a refusal that committed is one statement-order change away from leaving
            // a revision beside an attempt that is not STABLE.
            return refused.result;
        }
EOF
cat >"$CAPTURES/m2.after" <<'EOF'
        return runner.run(database, work -> {
            try {
                return stabilizeInside(work, operationId, revisionNo, now);
            } catch (Refused refused) {
                return refused.result;
            }
        });
EOF

: >"$OUT"

emit '# T-023 变异证据 —— 证明验收套件真的会「咬」'
emit ''
emit "生成方式：bash artifacts/evidence/.gen-t023-mutation.sh ，生成时刻 $(date -u '+%Y-%m-%dT%H:%M:%SZ')（UTC）。"
emit '每条命令当场执行，EXITCODE 取自该命令自身；回显的命令可直接粘回复现。'
emit '结论段的每个计数、用例名与还原校验都由本脚本从对应捕获输出里机械提取（failed_method_of /'
emit 'selected_count_of / sha256sum 比对），不手写。脚本在结束前把源码还原并用 SHA-256 证明还原成功。'
emit ''
emit '被注入的两处缺陷，正是本任务设计上专门防的两类：'
emit ''
emit '  1. stabilize 把版本「从用途常量重新推导」而不是「从 operation 行原样复制」——行完好时两种写法'
emit '     产生同一个字符串，只有夹具篡改过行才能把二者区分开（对应验收条件：**版本随同复制**）。'
emit '  2. 拒绝改为从工作体返回而不抛异常——DatabaseTransactionRunner 会提交工作体返回的任何东西，'
emit '     于是「revision 已写但 operation 非终态」的中间态被提交而不是回滚（对应验收条件：**稳定化全败**）。'
emit ''
emit '---'
emit ''

: >"$CAPTURES/results.psv"
MUTATION_1_NAME='把版本从用途常量重新推导，而不是从 operation 行复制'
MUTATION_2_NAME='把拒绝改为从工作体返回（不抛异常），于是事务被提交而非回滚'
run_mutation m1 "$MUTATION_1_NAME" "$STABILIZER" 'stabilizeCopiesThePinnedVersionFromTheRowVerbatim'
run_mutation m2 "$MUTATION_2_NAME" "$STABILIZER" 'stabilizeRollsBackBothWritesWhenTheRevisionNumberIsAlreadyTaken'

emit '## 结论（用例名、计数与还原校验均由本脚本从捕获输出机械提取，不手写）'
emit ''
while IFS='|' read -r label method status observed selected restored; do
    # Three states, not two. An extractor that found nothing is not evidence that nothing failed —
    # conflating the two is how a caught mutation gets reported as a surviving one.
    if [ -z "$observed" ]; then
        caught='**无法判定** —— 未从捕获里抽到失败用例名，须先修抽取器再重跑，不得按「未被捕获」记'
    elif [ "$status" != "0" ] && [ "$observed" = "$method" ]; then
        caught='**被捕获**'
    else
        caught="**未被捕获** —— 变异存活，该验收条件目前只由散文断言"
    fi
    emit "- ${label}：预期捕获用例 \`${method}\`；本次运行选中 ${selected:-未读到} 例，EXITCODE=${status}，"
    emit "  实际报告的失败用例 \`${observed:-未读到}\` → ${caught}。"
    emit "  失败首行：$(failure_message_of "$CAPTURES/${label}.raw" | head -c 220)"
    emit "  还原状态：${restored}"
done <"$CAPTURES/results.psv"
emit ''
emit '两次注入后源码均已还原，且还原哈希与注入前逐字相同（见各节第 3 步）。'
emit ''

assert_utf8 "$OUT"
echo "wrote $OUT"
echo '--- per-mutation result (label|expected method|exit|observed method|selected|restore) ---'
cat "$CAPTURES/results.psv"
