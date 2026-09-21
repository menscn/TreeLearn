# T-021 独立复核报告（generation-operation-prompt-anchoring-021）

- 复核者：独立 verifier（只读；未改动任何生产/测试代码）
- 复核时间：2026-09-21
- 被复核任务：`.trae/tasks/T-2026-09-21-generation-operation-prompt-anchoring-021.md`
- 环境：`JAVA_HOME=D:\Environment\Jdk\jdk21`、`JAVA_TOOL_OPTIONS=-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8`

## 1. Verdict

**`PARTIAL`**

- 7 条验收条件**全部实质满足**（我用独立探针 + 独立重跑逐条复核，见第 2 节）。
- 但存在**自述与实测不符**、**当前态文档与代码互相矛盾**、**同类输入走两种错误通道未如实声明**三类问题，
  按 `.trae/rules/documentation.md`（「与活动的生产者/消费者事实相矛盾的当前态主张」必须 FAIL）与
  `.trae/rules/harness-tasks.md` 第 47 行（RED/GREEN 必须展示真实观察结果）不能判 PASS。
- 未发现伪造证据、未发现规模超 500 行硬上限、未发现隐藏的行为破坏。

## 2. 逐项核实（核实项 → 命令 → 观察输出 → 结论）

### 2.1 验收条件 1（命令必须固定非空且被声明用途拥有的版本）

```
$ 'C:\...\python.exe' 探针（见 2.9）section I/J
     GenerationCommand.create(null purpose, no version) -> IllegalArgumentException: purpose must not be null
     create(malformed branchId, missing version) -> DomainResult PROMPT_POLICY_VERSION_REQUIRED
$ grep -n "aMissingVersionIsRefusedWithThePromptPolicyCode\|aVersionOwnedByAnotherPurposeIsRefused\|versionLookalikesAreRefused" \
    android/core/src/test/java/com/treelearn/core/domain/GenerationCommandTest.java
61:    public void aMissingVersionIsRefusedWithThePromptPolicyCode() {
72:    public void aVersionOwnedByAnotherPurposeIsRefused() {
83:    public void versionLookalikesAreRefused() {
```
→ **证实**（3 个用例、缺失 3 种输入 + 跨用途 1 种 + 形似值 6 种，与自述一致）。通道混用问题见 F1/F2/F3。

### 2.2 验收条件 2（operation 继承同一版本且不可改写）

```
$ 探针 section A（引用相等，比「逐字符相等」更强的证据）
OK   command.promptPolicyVersion is the very same String instance  [identity]
OK   operation.promptPolicyVersion is the very same String instance  [identity]
OK   revision.promptPolicyVersion is the very same String instance  [identity]
$ 探针 section G
OK   GenerationOperation keeps the pinned version final  [reflection]
OK   no setter on GenerationOperation  [reflection]
```
→ **证实**：命令 → 操作 → 稳定版本三跳传的是**同一个 String 实例**，不存在 trim/规范化/重拼接。

### 2.3 验收条件 3（正常结束且回答非空才可 stabilize；版本与缓冲区逐字符一致）

```
$ 探针 section D
OK   empty-answer stabilize refused with OPERATION_NOT_COMPLETABLE  [...]
OK   state after refused stabilize = STREAMING_REASONING  [STREAMING_REASONING]
OK   whitespace-only answer refused too  [... but only 3 answer character(s) arrived ...]
OK   state after whitespace-only refusal = STREAMING_ANSWER  [STREAMING_ANSWER]
OK   stabilize(0) throws IllegalArgumentException  [IllegalArgumentException: revisionNo must be >= 1 but was 0]
OK   state untouched after stabilize(0) throw  [STREAMING_ANSWER]
OK   advanceTo(STABLE) refused  [DomainResult.fail(ILLEGAL_STATE_TRANSITION: STABLE is reached only through stabilize(...) ...)]
OK   state untouched after refused advanceTo(STABLE)  [STREAMING_ANSWER]
$ 探针 section G
OK   reasoning copied byte for byte (no trim)  [[  pad  ]]
OK   answer copied byte for byte (no trim)  [[  answer  ]]
```
→ **证实**。原子性用穷举复现复核（见 2.8），未找到反例。

### 2.4 验收条件 4（非法迁移与终态晚到回调返回 ILLEGAL_STATE_TRANSITION；部分内容可读但不可稳定化）

```
$ 探针 section E
     replays explored: 2800
     distinct states reached: [SUBMITTING, STREAMING_REASONING, STREAMING_ANSWER, STABLE]
OK   invariant holds on every replayed sequence
$ grep -n "aFailedAttemptRejectsLateCallbacksAndCannotBeStabilized\|anInterruptedAttemptBehavesLikeAFailedOne\|aSecondStabilizationIsRefused" \
    android/core/src/test/java/com/treelearn/core/domain/GenerationOperationTest.java
240:  aFailedAttemptRejectsLateCallbacksAndCannotBeStabilized
265:  anInterruptedAttemptBehavesLikeAFailedOne
227:  aSecondStabilizationIsRefused
```
→ **证实**。2800 条动作序列（7 种公开动作、深度 4）全部满足「`state()==STABLE` ⟺ 有一次 stabilize 返回 ok」。

### 2.5 验收条件 5（STREAMING_ANSWER 下 appendReasoning 必须被接受且不得回退）

```
$ 探针 section C
OK   appendReasoning accepted while STREAMING_ANSWER  [DomainResult.ok(STREAMING_ANSWER)]
OK   state did not step back  [STREAMING_ANSWER -> STREAMING_ANSWER]
OK   late reasoning landed in the reasoning buffer  [late thought]
OK   advanceTo(STREAMING_REASONING) refused from the answer phase  [ILLEGAL_STATE_TRANSITION ...]
OK   refused advanceTo left the state alone  [STREAMING_ANSWER]
```
→ **证实**。并额外核实「是否存在别的路径会把状态回退」：`state` 的全部写入点在
`GenerationOperation.java`（构造、`advanceTo`、`appendReasoning`、`appendAnswer`、`stabilize`），
`advanceTo(STREAMING_REASONING)` 经 `GenerationState.transitionTo` 被拒且不改动字段 —— 无回退路径。

### 2.6 验收条件 6（PromptSpec 恰好三字段、无供应商类型、空正文被拒）

```
$ 探针 section H
OK   PromptSpec declares exactly 3 fields  [3]
OK   PromptSpec.of(mismatched version) throws IAE  [IllegalArgumentException]
```
→ **证实**（测试 `theSpecHasNoProviderSpecificField` 另有名字白名单 + 类型命名空间断言，我读了源码确认不是空壳）。

### 2.7 验收条件 7（`:core:test` 返回 0 且可被强制真实执行）

```
$ export JAVA_HOME='D:\Environment\Jdk\jdk21'; cd android && ./gradlew --no-daemon --console=plain :core:test --rerun-tasks
BUILD SUCCESSFUL in 13s
3 actionable tasks: 3 executed
EXITCODE=0
$ cd android/core/build/test-results/test && head -1 TEST-*.xml | grep -o 'name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"'
AnswerRevisionTest tests="11" skipped="0" failures="0" errors="0"
BranchSourceTest tests="12" skipped="0" failures="0" errors="0"
BranchTest tests="12" skipped="0" failures="0" errors="0"
GenerationCommandTest tests="7" skipped="0" failures="0" errors="0"
GenerationOperationTest tests="19" skipped="0" failures="0" errors="0"
GenerationStateTest tests="15" skipped="0" failures="0" errors="0"
GuardTest tests="6" skipped="0" failures="0" errors="0"
MessageTest tests="13" skipped="0" failures="0" errors="0"
PromptPurposeTest tests="9" skipped="0" failures="0" errors="0"
PromptSpecTest tests="6" skipped="0" failures="0" errors="0"
TopicTest tests="6" skipped="0" failures="0" errors="0"
```
合计 **116 用例 / 0 失败 / 0 错误 / 0 跳过**（XML 实数，非 Gradle 摘要行）→ **证实**自述的 116 与逐类分布。
回归：`T-003-green.txt` 为 83 用例、`GenerationStateTest` 14；本轮 8 个旧类逐类数与 T-003 完全相同，
仅 `GenerationStateTest` 14→15 → 「83 保留 + 33 新增 = 116」**证实**。

### 2.8 规模口径（自己复算）

```
$ python（非空非注释口径，见 tools 说明）
GenerationOperation.java   code=137 comment=110 blank= 15 physical=262
GenerationCommand.java     code= 58 comment= 36 blank=  5 physical= 99
PromptSpec.java            code= 29 comment= 24 blank=  6 physical= 59
GenerationState.java       code= 89 comment= 91 blank= 19 physical=199
DomainErrorCode.java       code= 12 comment= 26 blank= 10 physical= 48
3 new files code total = 224
all 5 touched files code total = 325
all 5 touched files physical total = 667
```
→ **与交付方表格逐格相同**（137/110/15/262、58/36/5/99、29/24/6/59、89/91/19/199、12/26/10/48）。
新增文件生产代码 **224**，加「被改动的 3 行」= **227**；227/200 = **超软预算 13.5%** ✓；227/210 ≈ **+8.1%** ✓。
即使把两个既有文件的全部代码行也算进来（325 行）仍 **< 500**，**不存在硬失败**（`.trae/rules/harness-tasks.md` 第 45 行）。
`git status` 显示整个 `android/` 未被 git 跟踪，故「GenerationState 删边 2 行 + DomainErrorCode 删常量 1 行 = 3」
无法用版本库核对 → 见 Gaps G1。

### 2.9 独立探针（我自己写的补充用例，不在仓库内，只读调用交付的类）

```
$ export JAVA_HOME='D:\Environment\Jdk\jdk21'; cd /d/tmp/t021verify
$ "$JAVA_HOME/bin/javac" -cp "D:/Project/Android/Treelearn/android/core/build/classes/java/main" -d . Probe.java
$ "$JAVA_HOME/bin/java" -cp ".;D:/Project/Android/Treelearn/android/core/build/classes/java/main" Probe
（A–J 共 10 组探针，30 条断言）
ALL PROBES PASSED
```
探针覆盖：版本引用相等传递、版本带空白被拒（不 trim）、STREAMING_ANSWER 下追加 reasoning、
`advanceTo` 回退被拒、stabilize 三种拒绝路径（空回答 / 纯空白 / revisionNo=0）后状态不变、
`advanceTo(STABLE)` 被拒且状态不变、2800 条序列穷举原子性不变量、D2 边不可用且 STREAMING_ANSWER 是唯一定向 STABLE 的状态、
缓冲区逐字符复制、`AnswerRevision` 全字段 final / 无 setter / 无 `StringBuilder` 出现在公开签名、
`PromptSpec` 恰好 3 字段、以及每个公开入口的缺参数通道。

### 2.10 D1（删除 `OPERATION_TERMINAL`）与其余错误码的生产者

```
$ grep -rn "OPERATION_TERMINAL" --exclude-dir=.git .
.trae/tasks/T-2026-09-21-...-021.md:152 / :212 / :272（本任务记录）
AGENTS.md:78（失败案例库）
android/core/src/main/java/com/treelearn/core/domain/DomainErrorCode.java:16（注释：说明它为何被删）
```
→ **证实**：`OPERATION_TERMINAL` 已无生产者，唯一命中在注释/记录里。

其余 8 个码逐一在全仓库检索 `src/main` 的生产点：

```
ILLEGAL_STATE_TRANSITION        Message.java:86,109  GenerationState.java:159  GenerationOperation.java:114,207,239,249
OPERATION_NOT_STABLE            BranchSource.java:71
OPERATION_NOT_COMPLETABLE       GenerationOperation.java:217
REVISION_IMMUTABLE              Message.java:114
PROMPT_POLICY_VERSION_REQUIRED  AnswerRevision.java:78  GenerationCommand.java:79
PROMPT_POLICY_VERSION_MISMATCH  AnswerRevision.java:82  GenerationCommand.java:84
ANCHOR_INVALID                  BranchAnchor.java:47,51,55,59,67
SEQUENCE_OUT_OF_ORDER           Branch.java:193,197
MESSAGE_ROLE_MISMATCH           Message.java:82,104
```
→ **全部有生产者**。交付方「刚以无生产者为由删了一个码，其余码必须同样站得住」这一关**通过**。

### 2.11 D2（删除 `STREAMING_REASONING -> STABLE`）的可达性论证

```
$ 探针 section F
OK   canTransitionTo(STABLE) false from STREAMING_REASONING  [false]
OK   transitionTo(STABLE) from STREAMING_REASONING fails  [ILLEGAL_STATE_TRANSITION: cannot move from STREAMING_REASONING to STABLE]
OK   STREAMING_ANSWER -> STABLE still legal in the graph  [true]
OK   SUBMITTING -> STABLE refused  [false]
OK   STREAMING_ANSWER is the only state that may target STABLE  [checked]
$ 探针 section E：2800 条序列中没有任何一条从 STREAMING_REASONING 直接进入 STABLE
```
→ **可达性论证成立**：`stabilize` 要求回答去空白后非空，而回答缓冲的唯一写入者 `appendAnswer`
在写入前必把状态推进到 `STREAMING_ANSWER`，且 `advanceTo(STREAMING_REASONING)` 被拒——不存在从
`STREAMING_REASONING` 出发的稳定化路径。`GenerationState` 虽为 public，但唯一内部消费者
`BranchSource.fromOperation` 只读 `isStable()`；`:data`/`:ai`/`:app` 目前没有任何代码引用 `GenerationState`（grep 零命中）。
被替换断言的等价覆盖**成立**（`SUBMITTING -> STREAMING_ANSWER -> STABLE` 仍在 `happyPathTransitionsAreLegal` 中，
新增 `aReasoningPhaseThatProducedNoAnswerCannotBecomeStable` 反锁该边不可回归）。
但对 §7 图示的**读法自述有误** → F6。

### 2.12 守门与自检

```
$ powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1 -AndroidRoot android
OK   no Kotlin coordinates on 53 variant classpath configuration(s) across :app, :core, :data, :ai, :tree-layout
OK   excluded 36 build-tooling configuration(s)
OK   verifyNoKotlinDex: [app/build/outputs/apk/debug/app-debug.apk] shipped APK = 70742 bytes, 3 dex file(s)
VERDICT: PASS
GUARD EXITCODE=0
$ powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/dev/guard-selftest.ps1 -Fast
OVERALL = SELFTEST PASS (23 case(s))
SELFTEST EXITCODE=0
```
→ **证实**：新增 Java 代码没有让守门出现任何新问题。README 里「53 个变体 classpath / 排除 36 个构建工具配置 /
约 70 KB / 3 dex」与实测一致；`-Fast` 23 例一致；完整模式 25 例由
`artifacts/evidence/T-002-guard-selftest.txt`（`OVERALL = SELFTEST PASS (25 case(s))`，`Guard under test SHA-256` 与当前脚本一致）支撑。

### 2.13 `:core` 没有 lint 任务（核实交付方的自述）

```
$ ./gradlew --no-daemon --console=plain :core:lint
* What went wrong:
Cannot locate tasks that match ':core:lint' as task 'lint' not found in project ':core'.
$ ./gradlew --no-daemon --console=plain :core:lintDebug
* What went wrong:
Cannot locate tasks that match ':core:lintDebug' as task 'lintDebug' not found in project ':core'.
```
→ **证实**。`T-021-lint.txt` 里的 `Warning: Lint will treat :core as an external dependency and not analyze it.` 也印证该缺口。

### 2.14 lint 摘要与脚本默认路径修复

```
$ 'C:\...\python.exe' android/scripts/dev/lint_summary.py      # 从仓库根运行
  ... 11 条 Warning 明细 ...
  total=11
  by severity: {'Warning': 4}
```
→ **证实**「`:app:lintDebug` 0 error / 11 warning」，并**证伪了脚本修复前的缺陷**（照 docstring 从仓库根运行现在能读到
`android/app/build/reports/lint-results-debug.xml`，而不是 `no lint report`）。

### 2.15 纯 JVM 纯净性（README 本轮新写的断言）

```
$ ./gradlew --no-daemon --console=plain verifyDependencyDirection
verifyDependencyDirection: WARNING :tree-layout has no src/main/java sources yet, so its purity contract is vacuous until T-003 / T-016 land code
verifyDependencyDirection: PASS (downward-only module graph + pure-JVM module purity) ; purity NOT yet enforced for [:tree-layout] (no sources)
EXITCODE=0
$ find android/tree-layout -type f   # 只有 build.gradle 与 build/ 产物，无 src/
```
→ **证实** README「`:core` 已是真实强制、仅 `:tree-layout` 仍空跑」以及所引用的 WARNING 文案。

### 2.16 任务文件机械校验

```
$ 'C:\...\python.exe' .trae/scripts/validate_tasks.py
WARNING T-2026-09-21-generation-operation-prompt-anchoring-021.md: estimated production lines 210 exceeds soft limit 200
PASS T-2026-09-21-generation-operation-prompt-anchoring-021.md
SUMMARY files=21 errors=0 warnings=1
VALIDATE EXITCODE=0
```
→ **证实**：任务文件本身机械合规（仅规模估算告警）。

### 2.17 TDD 真实性

```
$ tail -2 artifacts/evidence/T-021-red.txt
BUILD FAILED in 9s
2 actionable tasks: 2 executed
$ grep -c "error: cannot find symbol" artifacts/evidence/T-021-red.txt      # 同一错误在正文与 "What went wrong" 各出现一次
98
$ grep -n "errors$" artifacts/evidence/T-021-red.txt
254:49 errors
508:  49 errors
```
失败原因全部是**新类型符号不存在**（`GenerationCommand`/`GenerationOperation`/`PromptSpec`），
且 `:core:compileJava` 成功、`:core:compileTestJava` 失败 —— 与「依赖/环境/语法」无关 → **RED 真实**。
更进一步：RED 证据里引用的测试文件行号（`GenerationCommandTest.java:24/25/31/33/55/63/73/92/117/127/142/144`、
`GenerationOperationTest.java:29/35/36/40/50/51/72/74/78/85/99/114/131/141/165/174/192/216/228/241/266/280/295/306/319`、
`PromptSpecTest.java:23/33/45/51/62/81/88/103`）与**当前落盘的测试源逐行一致**，说明 RED 跑的就是最终测试源
（测试先行且未被事后改写）。数字误述见 F4。

## 3. 发现表

| # | 级别 | 发现 | 为什么不满足契约（引用原文） | 建议处置 | 最小复现命令 |
| :-- | :-- | :-- | :-- | :-- | :-- |
| F1 | 中 | `android/README.md:165` 仍写「`AnswerRevision` 对缺失策略版本返回错误码是**已声明的唯一例外**」，但同一份 README 第 153 行（本轮新写的表）自己写着 `GenerationCommand`「版本必须非空且由声明用途拥有；否则返回领域错误」，代码 `GenerationCommand.java:78-82` 也确实对 null/空白版本返回 `PROMPT_POLICY_VERSION_REQUIRED` | `.trae/rules/documentation.md`「必需的影响映射」+「项目 Harness 校验器在以下情况必须返回 FAIL：… 与活动的生产者/消费者事实相矛盾的当前态主张」。README 是当前态文档，本轮改动把它变成不实（且与自身 12 行外的表述互斥） | 改成「已声明的**两处**例外：`AnswerRevision.create`、`GenerationCommand.create`」并各指向 javadoc；或删掉「唯一」字样 | `grep -n "唯一例外" android/README.md` ; `sed -n '76,88p' android/core/src/main/java/com/treelearn/core/domain/GenerationCommand.java` |
| F2 | 中 | `GenerationCommand.create` 把「缺必填参数（版本为 null/空白）」转成错误码，却**没有**像 `AnswerRevision.create` 那样在 javadoc 里声明为错误通道例外，反而声称自己符合规则 | `Guard.java:19-22`：「Malformed input — a null where a value is required … **factories must throw it too rather than convert a missing argument into an error code**」；`Guard.java:32-35`：「每个公开入口必须选对通道」。同包 `AnswerRevision.java:62-66` 用「Deliberate exception to the error-channel rule」明确声明，本条没有 → 同一包内两处同类行为、一处声明一处不声明 | 既然验收条件 1 要求返回领域错误，就把它写成**第二处显式例外**（引用验收条件 1 与 §3.2），不要声称无需例外 | `grep -rn "Deliberate exception" android/core/src/main/java/com/treelearn/core/domain/`（只命中 `AnswerRevision.java`） |
| F3 | 中 | 同一输入「版本合法但不属于声明用途」在 `PromptSpec.of` 抛 `IllegalArgumentException`、在 `GenerationCommand.create` 返回 `PROMPT_POLICY_VERSION_MISMATCH`。javadoc 给的理由（「a command is a request-shaped value whose version is contractually required, so its **absence** is a declared business condition」）只解释**缺失**，未解释**存在但跨用途**这一被比较的情形；两个类都是静态工厂，且版本都来自 `:ai` 注册表常量，无法用「常量 vs 运行时值」区分 | `Guard.java:14-16`：「独立审计发现个别入口**对同一类输入混用通道**」+ 三条通道按输入类别划分的规则。属该缺陷类的第 N 次出现 | 二选一并写进记录：(a) `PromptSpec.of` 改为返回 `DomainResult`（与验收条件 1 一致）；(b) 承认方向相反是有意的，并补上覆盖「跨用途」而非「缺失」的理由 | `探针 section H/J`（`PromptSpec.of(ROOT_TITLE, LEARNING, "body") -> IllegalArgumentException`；`GenerationCommand.create(ROOT_TITLE, LEARNING) -> DomainResult PROMPT_POLICY_VERSION_MISMATCH`） |
| F4 | 轻 | TDD 记录写「随后 **40 处** `error: cannot find symbol`（覆盖**两个**测试文件）」，实测 RED 证据为 **49 errors**、且覆盖**三个**测试文件（`PromptSpecTest` / `GenerationCommandTest` / `GenerationOperationTest`） | `.trae/rules/harness-tasks.md` 第 47 行要求执行记录「展示 … 观察到的失败」；自述数字与证据文件不符即自述不实（Verification report 同一说法重复了一次） | 改为 49 与三个文件 | `grep -n "49 errors" artifacts/evidence/T-021-red.txt` ; `grep -c "error: cannot find symbol" artifacts/evidence/T-021-red.txt` |
| F5 | 轻 | 任务文件与 Verification report 两处声称 `T-021-green.txt` 含 `--rerun-tasks`、`BUILD SUCCESSFUL in 11s`、**`EXITCODE=0`**；该文件**没有**退出码行，也没有命令行（对照 `T-021-guard.txt` 有 `Command:` 与 `GUARD EXITCODE=0`） | 第 47 行要求 GREEN 展示「同一目标测试命令，**零退出码**，通过输出」。证据文件缺退出码与命令行，不能自证；我独立重跑为 exit 0，但那是我的证据不是它的 | 用 `2>&1 \| Out-File -LiteralPath … -Encoding utf8 -Append` 重录，头部记录命令行与 `EXITCODE` | `tail -3 artifacts/evidence/T-021-green.txt`（结尾只有 `3 actionable tasks: 3 executed`） |
| F6 | 轻 | 同一交付对 §7 状态机图示给出**两种互斥读法**：任务文件写「§7 的缩进使 `-> STREAMING_ANSWER` 与 `-> STABLE` 看起来都从 `SUBMITTING` 分出」，而 `GenerationState` 类注释写「The diagram draws a single chain … `-> STREAMING_ANSWER -> STABLE`」。实测两条续行前导 **38 个空格**，与 `STREAMING_REASONING` 起始列（38）对齐，`SUBMITTING` 起始列是 24 —— 缩进**不支持**「从 SUBMITTING 分出」 | `.trae/rules/documentation.md` 要求「对当前运行时行为的主张」以权威来源为准并如实记录偏离。若按「续行是 `STREAMING_REASONING` 的后继」读法，§7 确实含被删的那条边，则「§7 正文不需要改动」的结论需要重新论证 | 统一读法；若采用「后继」读法，在 §7 或 `GenerationState` 偏离清单里补一句「§7 画了该边，实现删除并给出可达性论证」 | `sed -n '159,166p' "Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md" \| cat -A` |
| F7 | 轻 | 本轮还改了 `AGENTS.md`（失败案例库新增两条，分别引用 D1/D2 与 `lint_summary.py` 改动），但任务文件「Documentation files updated」只列了 `android/README.md` 与 `android/scripts/dev/lint_summary.py` | `.trae/rules/documentation.md`：「…必须说明：… 受影响的文档面与**已更新的文件**」 | 把 `AGENTS.md` 补进文档影响记录 | `git diff --stat AGENTS.md`（17 insertions / 3 deletions，末两条为本轮新增） |
| F8 | 轻 | **Gaps 不完整**：「正常结束」这一前提在领域层完全不可表达。`stabilize` 只检查「非终态 + 回答去空白后非空」，因此一个仍在流式中的操作（协调器尚未收到 `[DONE]`）也能被稳定化；javadoc 只说 "Closes a normally finished attempt"，未把该义务写给调用方。交付方 Gaps 只写了 `revisionNo` 新鲜度与落盘批量化 | §9.3「只有收到正常流终止且最终回答非空，才在事务中创建 `AnswerRevision`…」；验收条件 3 的「正常结束」是调用方义务，而 T-008 只会读类型的 javadoc | 在 `stabilize` javadoc 明确「调用方必须先确认流正常终止；本类型无法观测终止」，并写进 Handoff 的 watch-outs | 探针 section D（未收到任何终止信号的 attempt 上 `stabilize` 返回 ok） |
| F9 | 轻（观察） | 参数校验顺序未定义：一次调用同时有「畸形 id」与「缺失版本」两个缺陷时，返回 `DomainResult PROMPT_POLICY_VERSION_REQUIRED` 而不是为畸形 id 抛 `IllegalArgumentException`，即「格式非法 → IAE」被业务码遮蔽 | `Guard` 三条通道规则未规定同类多缺陷的优先级；不影响单缺陷调用，但会让「格式非法必抛」在组合输入下不成立 | 在 `create` javadoc 写明校验顺序，或把格式校验提到版本校验之前 | 探针 section J：`create("not-a-uuid", MESSAGE, LEARNING_ANSWER, null, PRO, true, T0) -> DomainResult PROMPT_POLICY_VERSION_REQUIRED` |
| F10 | 轻（表述） | `GenerationStateTest` 新方法的 javadoc 首句写「Replaces **two** previous assertions」，正文只描述 **1** 条被删断言；Verification report 也写「**唯一**被替换的断言…那一条」，T-003 记录同样只列 1 条 | 第 47 行要求「变更断言的理由」如实可核；三处记录不一致 | 改为单数，或说明第二条指什么 | `sed -n '27,41p' android/core/src/test/java/com/treelearn/core/domain/GenerationStateTest.java` |

## 4. Gaps（我无法验证的部分）

- **G1**：`android/` 目录**未被 git 跟踪**（`git status` 显示为 `?? android/`），因此
  「`GenerationState` 删边 2 行 + `DomainErrorCode` 删常量 1 行 = 3 行改动」以及「T-003 的 83 用例原样保留」
  只能通过 T-003 的**证据文件与任务记录**间接核对（我核了：T-003-green 83 用例、`GenerationStateTest` 14，
  本轮其余 8 类逐类数完全相同），无法用版本库 diff 独立证明。这不影响规模结论：即使把两个既有文件的
  全部代码行（89+12）都计入，总量也只有 325 行，仍 < 500。
- **G2**：RED 证据的**时间顺序**（测试确实先于生产代码落盘）无法独立验证——只验证到「RED 跑的就是最终测试源」
  且失败原因是符号缺失。这是无 VCS 基线下能达到的上限。
- **G3**：未做任何设备/模拟器验证（用户 2026-09-21 决定由用户自行进行），本任务 Non-goals 也不含网络与 UI；
  我同样未做。**不要把本报告读作设备行为已通过**。
- **G4**：`stabilize` 的 `revisionNo` 新鲜度依赖 T-004 的 `(message_id, revision_no)` 唯一约束，该表尚未建
  （T-004 未落地），故「同一位置两个同号 revision」这一风险无法在此轮关闭；交付方已在 Risks 声明，我确认该声明准确。
- **G5**：`AGENTS.md` 的字符编码在 Git Bash 下显示为乱码（`AGENTS.md:78` 的 `sed` 输出为 GBK 乱码），
  但 `git diff` 输出正常，属**终端显示**差异而非文件损坏；我未据此判断文件有问题。
- **G6**：「完整模式自检 25 例」我是从 `T-002-guard-selftest.txt`（`OVERALL = SELFTEST PASS (25 case(s))`，
  且其中记录的 `Guard under test SHA-256` 与当前脚本一致）与脚本结构（`-Fast` 跳过 CASE 7/8，23+2=25）推断，
  未实际运行完整模式（需真实 Gradle 两次）。README 的「最近一次完整运行结果见 `artifacts/evidence/T-002-guard-selftest.txt`」
  因此是**可追溯**的，但我没有重跑它。

## 5. 结论与翻盘条件

### 如果判 PASS，我确认了以下具体事实

（本次**未**判 PASS，但为免歧义，明确列出我确实复核通过的部分）
1. `:core:test --rerun-tasks` 独立重跑：`BUILD SUCCESSFUL`、exit 0、3 个任务真实执行；XML 实数 **116 用例 / 0 失败 / 0 错误 / 0 跳过**，逐类数与自述完全一致。
2. 7 条验收条件**实质全部满足**，关键行为用我自己写的 30 条断言的独立探针复核（版本按**引用**原样传递、
   `STREAMING_ANSWER` 下追加 reasoning 不回退、2800 条序列穷举无「STABLE 无 revision」反例、`advanceTo(STABLE)` 拒绝生效、
   `stabilize(0)` 抛 IAE 且状态不动、缓冲区逐字符复制、`AnswerRevision` 全 final 无 setter 且无 `StringBuilder` 泄漏）。
3. D1 成立（`OPERATION_TERMINAL` 确无生产者，其余 8 个码**全部**有生产者）；D2 的可达性论证成立、等价覆盖成立。
4. 规模口径复算逐格吻合：新增 224 + 改动 3 = 227，超软预算 13.5%、未超 500 硬上限（无硬失败）。
5. 守门 `VERDICT: PASS` / exit 0；`-Fast` 自检 23 例全过、exit 0；`verifyDependencyDirection` PASS 且仅 `:tree-layout` 空跑。
6. `:core:lint` / `:core:lintDebug` 确不存在（task not found）；`lint_summary.py` 从仓库根可读到报告、`:app:lintDebug` 0 error / 11 warning。
7. RED 是真实「新类型不存在」的编译失败，且失败行号与最终测试源逐行一致。

### 交付方翻盘需要做的（按重要性）

1. **F1**：修 `android/README.md` 的「唯一例外」断言（当前态文档与代码矛盾，是 documentation.md 明列的 FAIL 条件）。
2. **F2/F3**：把 `GenerationCommand.create` 的「缺失版本返回错误码」补成与 `AnswerRevision.create` 同等的
   **显式例外声明**（引用验收条件 1 与 §3.2）；并解决或如实声明 `PromptSpec.of` 与 `GenerationCommand.create`
   在**同一输入（版本跨用途）**上的通道分歧——现给的理由只覆盖「缺失」，不覆盖被比较的「跨用途」。
3. **F4/F5**：改正 RED 记录里的 40→49、两个→三个测试文件；重录带命令行与 `EXITCODE` 的 GREEN 证据文件。
4. **F6**：统一 §7 图示读法（我实测缩进对齐的是 `STREAMING_REASONING` 列，不是 `SUBMITTING` 列），
   并据此重新论证「§7 正文不需要改动」。
5. **F7/F8**：补 `AGENTS.md` 到文档影响记录；把「调用方必须先确认流正常终止」写进 `stabilize` javadoc 与 Handoff，
   并把这条补进 Gaps（现 Gaps 只覆盖 `revisionNo` 与落盘批量化）。
6. **F9/F10**（可一并处理）：写明校验顺序；修正测试 javadoc 的「replaces two」表述。

以上 1–5 修完并把任务文件的 Verification report 填成本报告的结论，即可判 PASS；`PARTIAL` 期间**不应**把任务状态推进到 `verified`。
