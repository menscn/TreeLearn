# T-021 独立复核报告 · 第 2 轮（generation-operation-prompt-anchoring-021）

- 复核者：独立 verifier（只读；未改动任何生产代码、测试代码或任务文件）
- 复核时间：2026-09-21
- 被复核任务：`.trae/tasks/T-2026-09-21-generation-operation-prompt-anchoring-021.md`（第 2 轮）
- 上一轮：`artifacts/evidence/T-021-verification.md`（`PARTIAL`，F1–F10）
- 环境：`JAVA_HOME=D:\Environment\Jdk\jdk21`、`JAVA_TOOL_OPTIONS=-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8`
- 我的独立探针（仓库外）：`/d/tmp/t021round2/Probe.java`（41 条断言）、`/d/tmp/t021round2b/Probe2.java`

## 1. Verdict

**`PARTIAL`**

- 7 条验收条件**全部实质满足**，F1–F10 中 **9 项已真正修好**（我逐项用自写探针/命令复核，不复述交付方证据）。
- 但仍有 **2 项中等**发现与 **6 项轻/观察**发现：
  - **R2-1（中）**：本轮把「参数形状先于业务判定」定为**包级规则**并写进当前态文档（`android/README.md:171`）与 Handoff，但**同包第四个返回 `DomainResult` 的入口 `AnswerRevision.create` 仍让业务码遮住 4 个调用方 bug**——F9 的修复在自己的声明范围内**不完整**，且文档现在对全包宣称了一条不成立的规则。
  - **R2-6（中）**：任务文件仍写着「RED 证据引用的测试行号与**当前落盘的测试源一致**，说明 RED 跑的就是最终测试源」。**这句在本轮已被改成不实**：RED 证据引用的 `PromptSpecTest` 8 处行号**全部**与当前源不符（整体后移 11 行），且 RED 源里 `PromptSpec.of` 是**直接返回值**（`PromptSpec spec = PromptSpec.of(...)`），而交付的 API 返回 `DomainResult`。即 RED 跑的是 F3 改 API **之前**的测试源；其余两个测试文件的前段逐字未变、只有尾部因本轮新增用例后移。
- 未发现伪造证据：RED 的 49 errors / 三个文件、GREEN 的 121 用例与命令行+退出码、规模表 5 个文件逐格**与我独立复算完全相同**。
- 未发现规模超 500 硬上限；未发现隐藏的行为破坏（38400 步随机动作序列穷举 0 反例）。

## 2. 逐项核实（核实项 → 命令 → 观察输出 → 结论）

### 2.1 验收条件 1（命令必须固定非空且由声明用途拥有的版本）

```
$ export JAVA_HOME='D:\Environment\Jdk\jdk21'; cd /d/tmp/t021round2
$ "$JAVA_HOME/bin/java" -cp ".;D:/Project/Android/Treelearn/android/core/build/classes/java/main" Probe
OK   create(malformed branchId + absent version)  [IllegalArgumentException: branchId must be a UUID string but was: not-a-uuid]
OK   create(cross-purpose version + negative now, shape wins)  [IllegalArgumentException: createdAt must not be negative: -1]
```
```
$ grep -rn "versionFailureFor\|ownerOf\|ownsVersion\|isAbsent" android/core/src/main/java/
→ 判定只有 PromptPurpose 一处（ownsVersion / ownerOf / versionFailureFor / isAbsent）；
  GenerationCommand.java:116 与 PromptSpec.java:66 都 `PromptPurpose.versionFailureFor(...)`；
  AnswerRevision.java:79/83 走 isAbsent + ownerOf（较弱的问题，已在 Guard 明说是两个谓词）。
```
→ **证实**。且**别处没有第二份判定实现**（R2 的重点之一）。

### 2.2 验收条件 2（operation 继承同一版本且不可改写）

```
$ 探针 A
OK   version travels as the same String instance  [identity]      # 命令 → 操作 → 稳定 revision 三跳同一实例
$ 探针 H
OK   GenerationOperation.promptPolicyVersion is final  [true]
OK   GenerationOperation setters  [0]
```
→ **证实**（比「逐字符相等」更强的引用相等）。

### 2.3 验收条件 3（正常结束且回答非空才可 stabilize；版本与缓冲区逐字符一致）

```
$ 探针 E
OK   reasoning not trimmed  [  pad  ]
OK   answer not trimmed  [	ans\n]
OK   second stabilize refused  [refused]
OK   advanceTo(STABLE) refused on a streaming attempt  [refused]
$ 探针（stabilizeRequiresANonEmptyAnswer 的独立复现）
OK   stabilize(0, -1) on terminal attempt  [IllegalArgumentException: revisionNo must be >= 1 but was 0]
```
→ **证实**；「正常结束」的前半句领域层不可观测见 2.8（F8）。

### 2.4 验收条件 4（非法迁移/晚到回调 → `ILLEGAL_STATE_TRANSITION`；部分内容可读但不可稳定化）

```
$ 探针 G（12 个随机种子 × 400 次 × 8 步）
OK   randomised action sequences checked: 38400, invariant violations  [0]
$ 探针 J
OK   late appendAnswer on STABLE  [refused]
OK   late appendReasoning on STABLE  [refused]
OK   advanceTo on STABLE  [refused]
OK   SR attempt cannot stabilize  [refused]
```
→ **证实**：38400 步中「`state()==STABLE` ⟺ 恰好一次 stabilize 成功」无反例（上一轮为 2800 步穷举，本轮扩大到随机 38400 步，动作集加入 `advanceTo(STABLE)` 与多次 `stabilize`）。

### 2.5 验收条件 5（`STREAMING_ANSWER` 下追加 reasoning 必须被接受且不回退）

```
$ 探针 F
OK   appendReasoning accepted in STREAMING_ANSWER  [true state=STREAMING_ANSWER buffer=late]
OK   empty deltas do not open a phase  [SUBMITTING]
OK   advanceTo(STREAMING_REASONING) refused from the answer phase  [refused]
```
→ **证实**，无回退路径（`state` 的唯一写入点在 `GenerationOperation`，`transitionTo` 拒绝回退且不改字段）。

### 2.6 验收条件 6（`PromptSpec` 恰好三字段、无供应商类型、空正文被拒）

```
$ 探针 H
OK   PromptSpec field count  [3]
OK   PromptSpec field types  [[com.treelearn.core.domain.PromptPurpose, java.lang.String, java.lang.String]]
```
→ **证实**（另有 `theSpecHasNoProviderSpecificField` 的名字白名单 + 命名空间断言，我读了源码确认非空壳）。

### 2.7 验收条件 7（`:core:test` 返回 0 且可强制真实执行）

```
$ export JAVA_HOME='D:\Environment\Jdk\jdk21'; cd android && ./gradlew --no-daemon --console=plain :core:test --rerun-tasks
BUILD SUCCESSFUL in 14s
3 actionable tasks: 3 executed
EXITCODE=0
$ python（解析 core/build/test-results/test/TEST-*.xml，UTF-8-BOM、testsuite 在第 2 行）
AnswerRevisionTest tests=11 ... GenerationCommandTest tests=8 ... GenerationOperationTest tests=20 ...
GenerationStateTest tests=15 ... PromptPurposeTest tests=11 ... PromptSpecTest tests=7 ...
TOTAL tests=121 failures=0 errors=0 skipped=0
```
→ **证实**。与 `T-021-green-counts.txt` 逐类相同；`T-021-green.txt` 内 `PASSED` 行数我也独立数过 **121**，两个证据文件同源。

### 2.8 F1–F10 逐项

| # | 级别（第 1 轮） | 我的复核命令 | 观察输出 | 结论 |
| :-- | :-- | :-- | :-- | :-- |
| F1 | 中 | `grep -rn "唯一例外" android/README.md` | 无命中（旧断言已删；`README.md:166-169` 改为指向 `Guard`） | **已修**（残留见 R2-2） |
| F2 | 中 | `sed -n '38,62p' Guard.java`；`grep -rn "Declared exception" android/core/src/main/java/` | `Guard` 注释新增「The declared exceptions to the first bullet」三条清单；`GenerationCommand.create` javadoc 有「Declared exception to the first bullet」；`AnswerRevision.create` 改为「One of the declared exceptions … where the full list lives」 | **已修** |
| F3 | 中 | 探针 B（15 种版本输入两入口逐一比对）；`grep -rn "ownsVersion\|ownerOf\|versionFailureFor\|isAbsent" src/main` | `PromptSpec.of and GenerationCommand.create agree on 15 inputs  [0 mismatch]`；判定只剩 `PromptPurpose` 一处 | **已修** |
| F4 | 轻 | `python3` 解析 `T-021-red.txt` 分两段计数 | 1–254 行 49 处；255–508 行 49 处（总 98 = javac 输出与 `What went wrong` 各一份）；逐文件 `GenerationCommandTest 12 / GenerationOperationTest 28 / PromptSpecTest 9` | **已修**（49 / 三个文件成立） |
| F5 | 轻 | `head -3`/`tail -3 artifacts/evidence/T-021-green.txt` | 首行 `Command: ./gradlew … --rerun-tasks`、末行 `EXITCODE=0`、`BUILD SUCCESSFUL in 12s` | **已修**（残留表述见 R2-3） |
| F6 | 轻 | `python3` 逐行量 §7 代码块缩进 | 块在 160–166 行；161 行 `SUBMITTING idx=24`、`STREAMING_REASONING idx=38`；162/163 行 `lead=38` | **已成**（38/24 与自述一致；论证瑕疵见 R2-4） |
| F7 | 轻 | `grep -c "^- \[" AGENTS.md`；任务文件「Documentation files updated」 | `AGENTS.md` 已列入并给 8 条概要；条目总数 **22**（≤30） | **已修** |
| F8 | 轻 | 探针 D；`sed -n '187,222p' GenerationOperation.java` | `stabilize succeeds while the stream is still open … [true state=STABLE]`；javadoc 有「Caller obligation: "normally finished" is not observable here」；Gaps/Handoff 同步 | **已修** |
| F9 | 轻 | 探针 C（三个入口的「畸形参数 + 不可用业务条件」组合） | 三入口均为「形状先抛」：`branchId must be a UUID string` / `systemContent must not be blank` / `revisionNo must be >= 1` | **部分修**：三入口成立，**第四入口不成立** → R2-1 |
| F10 | 轻 | `sed -n '27,41p' GenerationStateTest.java` | `Replaces one previous assertion.`（单数） | **已修** |

### 2.9 规模（自己复算，口径同 T-003：非空非注释的生产代码行）

```
$ python（按文件逐行状态机统计：块注释/javadoc、行注释、空行、其余为代码）
GenerationOperation.java   code=139 comment=132 blank= 15 physical=286
GenerationCommand.java     code= 62 comment= 63 blank=  6 physical=131
PromptSpec.java            code= 26 comment= 48 blank=  5 physical= 79
GenerationState.java       code= 89 comment=111 blank= 19 physical=219
DomainErrorCode.java       code= 12 comment= 26 blank= 10 physical= 48
5 文件合计 code=328 comment=380 blank= 55 physical=763
三个新文件 code 合计 = 227
```
→ **与任务文件表格逐格相同**（139/132/15/286、62/63/6/131、26/48/5/79、89/111/19/219、12/26/10/48；763）。
被修改的生产代码行 = 227 + 3 = **230**：> 200 → **verifier 警告**（软预算），未超 500 **无硬失败**；
即使把两个既有文件的**全部**代码行（89+12）都计入也仅 328 < 500。
`.trae/rules/harness-tasks.md:45`「超过 200 行是 verifier 警告；超过 500 行除非已记录用户批准的规模例外，否则为失败」→ 本条**不构成失败**。

### 2.10 回归（T-003 的 83 用例）

```
$ grep -o "^[A-Za-z]*Test > .* PASSED" artifacts/evidence/T-003-green.txt | wc -l   → 83
   11 AnswerRevisionTest / 12 BranchSourceTest / 12 BranchTest / 14 GenerationStateTest /
    6 GuardTest / 13 MessageTest / 9 PromptPurposeTest / 6 TopicTest
$ 本轮 XML：同 8 个类 11/12/12/15/6/13/11/6 = 86，另 GenerationCommand 8 + GenerationOperation 20 + PromptSpec 7 = 35
```
→ 8 个 T-003 测试类**全部存在且逐类数 ≥ 基线**；`121 = 83 + 38` 成立（35 个新类用例 + `GenerationStateTest` +1、`PromptPurposeTest` +2）。
被改写断言共 3 条（2 条 `PromptSpecTest` 换通道 + 1 条 javadoc 表述），**等价覆盖成立**：
`PromptSpecTest.theVersionDecisionIsTheSameOneGenerationCommandMakes` 直接比对两入口，比我自己的探针更严；
`GenerationState` 删边由 `happyPathTransitionsAreLegal`（保留 `SUBMITTING -> STREAMING_ANSWER -> STABLE`）
+ 新增的 `aReasoningPhaseThatProducedNoAnswerCannotBecomeStable` 反锁。**未发现为变绿而 weaken 的断言**。

### 2.11 TDD 真实性

```
$ python3 解析 T-021-red.txt：正文段 49 处 cannot find symbol（three test files）
$ 逐行比对 RED 引用行的原文 vs 当前测试源（46 组）
GenerationCommandTest.java:24/25/31/33/55/63/73/92  SAME
GenerationOperationTest.java:29/35/36/40/50/51/72/74/78/85/99/114/131/141/165/174/192/216/228/241/266/280/295/306  SAME
PromptSpecTest.java:23/33/45/51/62/81/88/103  DIFF（8/8）
  red:23 = 'PromptSpec spec = PromptSpec.of(PromptPurpose.LEARNING_ANSWER, LEARNING, "You are a tutor.");'
  now:23 = ''   now:34 = 同上
$ tail -3 artifacts/evidence/T-021-round2-red.txt
119 tests completed, 1 failed / BUILD FAILED in 12s / EXITCODE=1
```
→ RED **本身真实**（失败原因是新类型符号不存在，`:core:compileJava` 成功、只有 `compileTestJava` 失败）。
但「RED 跑的就是最终测试源」这句**已被本轮改动变成不实** → R2-6。

### 2.12 守门、lint、任务校验（独立重跑）

```
$ powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1 -AndroidRoot android
OK   verifyNoKotlinDex: PASS (3 APK(s) inspected across app/build/outputs/apk, no Kotlin runtime bytecode)
VERDICT: PASS
GUARD EXITCODE=0
$ python android/scripts/dev/lint_summary.py        # 从仓库根运行（F 项修过的脚本）
  total=11 / by severity: {'Warning': 4}
LINT SUMMARY EXIT=0
$ ./gradlew --no-daemon --console=plain :core:lint
Cannot locate tasks that match ':core:lint' as task 'lint' not found in project ':core'.
$ python .trae/scripts/validate_tasks.py
WARNING …-021.md: estimated production lines 210 exceeds soft limit 200 / PASS / SUMMARY errors=0 warnings=1
```
→ 全部**证实**（`:core` 确无 lint 任务；`lint_summary.py` 从仓库根确能读到报告）。

### 2.13 文档一致性最小检查

```
$ git diff --check        → 退出码 0（无空白错误）
$ U+FFFD 扫描（AGENTS.md / android/README.md / lint_summary.py / 任务文件）→ 0
$ grep -rn "唯一例外" android/README.md AGENTS.md → 无命中
$ grep -rn "单链\|single chain" android/core/src/main/java/ → 无命中（已统一为扇形展开）
$ git ls-files android | wc -l → 0        # android/ 整个目录未被 git 跟踪
```

### 2.14 对抗性核查（自找的角度）

```
$ 每个错误码的生产者计数（排除枚举定义与注释行）
ILLEGAL_STATE_TRANSITION 7 / OPERATION_NOT_STABLE 1 / OPERATION_NOT_COMPLETABLE 1 /
REVISION_IMMUTABLE 1 / PROMPT_POLICY_VERSION_REQUIRED 2 / PROMPT_POLICY_VERSION_MISMATCH 2 /
ANCHOR_INVALID 5 / SEQUENCE_OUT_OF_ORDER 2 / MESSAGE_ROLE_MISMATCH 2 / OPERATION_TERMINAL 0
$ 公开成员被测试引用的次数（机械统计）
GenerationState.isStreaming  → 0（main 里也 0 个消费者）
其余公开成员均有引用；PromptSpec.toString / GenerationCommand.toString 无直接断言
$ 探针 2
PromptSpec.toString = PromptSpec[LEARNING_ANSWER tl-learning-answer/1.0.0, 25 body chars]
leaks body?          false            # javadoc 的 §15 不泄漏主张为真，但没有测试断言
two commands get distinct ids? true
revision frozen after late delta? true (partial now = first)
```
→ 错误码「无生产者」这一关**通过**（唯一 0 生产者的是已删除的 `OPERATION_TERMINAL`）。
两处小缺口见 R2-8。

## 3. 发现表

| # | 级别 | 发现 | 为什么不满足契约（引用原文） | 建议处置 | 最小复现命令 |
| :-- | :-- | :-- | :-- | :-- | :-- |
| **R2-1** | **中** | 本轮把 F9 定为**包级规则**（任务文件 Handoff：`**校验顺序是包级规则**：参数形状先于业务判定`；`android/README.md:171`：`**校验顺序**：参数形状先于业务判定，按参数声明顺序执行。因此一次同时带「畸形参数」与「不可用版本」的调用抛 IllegalArgumentException，不会用业务码遮住调用方的 bug`），并在 `GenerationCommand.create` / `PromptSpec.of` / `GenerationOperation.stabilize` 三处落地；但**同包第四个返回 `DomainResult` 的公开入口 `AnswerRevision.create` 仍是业务判定在前**：`AnswerRevision.create("not-a-uuid", 0, null, "  ", null, false, null, -1L)` **返回 `PROMPT_POLICY_VERSION_REQUIRED`**（4 个调用方 bug：畸形 messageId、revisionNo=0、answerContent 空白、now<0 全被遮住）；换成「无主版本」时返回 `PROMPT_POLICY_VERSION_MISMATCH`；只有版本合法时才抛 `messageId must be a UUID string` | ① 规则被**声明为包级**却只在前三处成立，等于把 F9 的缺陷类往下推了一个入口（第 1 轮 F9 指出的是 `create`，本轮修了 `create` 却没有机械枚举**全部**返回 `DomainResult` 的入口）；② `documentation.md`「对规范性约束…即便当前代码违反，也不得削弱该规则。保留规范本身，用代码证据与 severity 记录实现偏差，并把代码修复单独路由」——当前**没有任何地方记录这条偏差**，README 却已对全包宣称该规则成立 | 二选一：(a) 把 `AnswerRevision.create` 的 `Guard.id(messageId)` 等形状校验提到版本判定之前（并补一条顺序锁用例，同 `aCallerBugIsReportedBeforeTheAttemptStateIsConsulted`）；或 (b) 在 `Guard` 的例外清单里按 documentation.md 的口径**登记该偏差 + severity + 路由到 T-003**，并把 README 的措辞限定为「在已登记的入口上成立」 | `cd /d/tmp/t021round2 && "$JAVA_HOME/bin/java" -cp ".;D:/Project/Android/Treelearn/android/core/build/classes/java/main" Probe`（看第 11、13 行：`PROMPT_POLICY_VERSION_REQUIRED` / `PROMPT_POLICY_VERSION_MISMATCH`） |
| **R2-6** | **中** | 任务文件 RED 记录仍写「另可交叉核对：RED 证据引用的测试行号（`PromptSpecTest.java:23/33/45/51/62/81/88/103` 等）与**当前落盘的测试源一致**，说明 RED 跑的就是最终测试源」。实测：`PromptSpecTest` 8 处**全部**不符（当前源整体后移 11 行），且 RED 引用行原文是 `PromptSpec spec = PromptSpec.of(...)`——即 RED 时期的 `PromptSpec.of` **直接返回值**，而交付 API 返回 `DomainResult`；`GenerationCommandTest`/`GenerationOperationTest` 前段逐字相同、尾部各后移（本轮新增用例所致） | `harness-tasks.md:47` 要求执行记录「展示 … 观察到的失败」；这句话是**自证测试先行的载荷性断言**，而本轮 F3 改 API + 本轮新增 3 个用例**已使它不实**，交付方未同步。（第 1 轮该句为真，属「跨轮次失效的自述」） | 把该句改成与事实一致的表述，例如：「RED 跑的是 F3 改 API 之前的一版测试源：`PromptSpecTest` 的 8 处引用行整体后移 11 行且 `of` 当时直接返回值；另两个文件前段逐字未变，尾部因本轮新增用例后移。RED 的失败原因（新类型符号不存在）不受影响。」若要保持「最终测试源」的说法，只能重录 RED（需回退 API，不现实） | `python3`（见 2.11 脚本：逐行比对 RED 引用行 vs 当前测试源，`PromptSpecTest.java:23 DIFF`） |
| R2-2 | 轻 | F1 处置写「**不再在 README 复制清单**」、Implementation notes 写「README 只指向它、不再复制清单」，但 `android/README.md:167` 仍逐个点名 `AnswerRevision.create`、`GenerationCommand.create`、`PromptSpec.of`（并把数量「三处」也写了一遍） | 交付方自己的失败案例库条目（`AGENTS.md:61`）要求「例外清单**只存一处**（`Guard` 类注释），README 等文档只指向它、不复制」；现在「三处 + 三个类名」在 `Guard` 与 README 各存一份，新增第四个例外时必须同时改两处（正是 F2/F3 的成因） | README 那句只保留指针（「清单与成员见 `Guard` 类注释」），删掉类名枚举与「三处」计数；或加一条机械断言比对两处一致 | `sed -n '166,169p' android/README.md`；`sed -n '42,49p' android/core/src/main/java/com/treelearn/core/domain/Guard.java` |
| R2-3 | 轻 | 任务文件第 2 轮 GREEN 段写「该文件是**当轮**证据：此后又补了 2 个 `PromptPurposeTest` 用例…因此最终 GREEN 是 121 用例，见上一条；两处数字差异已在此说明」 | 实测 `T-021-green.txt` 内含 **121** 条 `PASSED`（含 `absenceOfAVersionHasOneDefinition`、`theVersionDecisionIsExpressedAsADomainError` 两个新用例），且 `grep -c "^Command:"` = 1（单次运行）——即该文件**就是 121 那次运行**的记录，「当轮 119」的解释与证据文件自身矛盾（方向偏保守，不掩盖问题，但属自述不实、且让「119 那次」没有证据文件） | 改为「GREEN 证据已在全部用例落地后重录，`T-021-green.txt` 内含 121 条 PASSED，与 `green-counts.txt` 同源」 | `grep -c PASSED artifacts/evidence/T-021-green.txt` → 121；`grep -c "^Command:" artifacts/evidence/T-021-green.txt` → 1 |
| R2-4 | 轻 | F6 重新论证的第 3 点写「**没有行为丢失**：§7 读法下可实现的路径 `STREAMING_REASONING -> STREAMING_ANSWER -> STABLE` **都在图中**」，但同一段的第 4 点又说「§7 读法下**没有**列出的 `STREAMING_ANSWER -> STABLE`（唯一入口）」 | 在交付方自己选定的「扇形展开」读法下，§7 的两条续行是 `STREAMING_REASONING -> STREAMING_ANSWER` 与 `STREAMING_REASONING -> STABLE`，**并不包含** `STREAMING_ANSWER -> STABLE`——第 3 点的措辞与第 4 点直接冲突。真正成立的是**较弱但正确**的说法：**没有运行时行为丢失**，因为 §7 那条 `SR -> STABLE` 只能在「回答为空」的运行里被使用，而 §9.3 本来就禁止稳定化 | 把第 3 点改写为「无**运行时**行为丢失」并说明理由（§9.3 使该边在任何实现里都不可执行）；同时明确「§7 按扇形读法本身也不可实现（缺 `SUBMITTING -> STREAMING_ANSWER`），偏离清单在 `GenerationState` 类注释」，避免读者以为实现与 §7 边集一致 | `python3 -c`（见 2.8 缩进测量）；`sed -n '20,60p' android/core/src/main/java/com/treelearn/core/domain/GenerationState.java` |
| R2-5 | 轻 | 本轮把 §7 读法从「单链」改为「扇形展开」，于是 `GenerationState` 类注释写「**three deviations** are allowed here」（2 处新增 + 1 处删除）。但 T-003 任务文件的 N10 行（本任务明确声明「已在 T-003 任务文件中作为 N10 登记」，且要求 T-003 第 5 轮复核该改动）写的是「`GenerationState` 类注释的「三处偏离」改为「**两处偏离**」」 | 两处对同一个类注释的当前态描述互斥；T-003 仍在 `verifying`（其文件第 293 行「等待第 5 轮复核」），复核者会读到矛盾。另外本任务「Documentation surfaces checked」清单**没有列 T-003 的任务文件**，尽管本任务第 294 行说 D2「已在 T-003 任务文件中作为 N10 登记」 | 更新 T-003 的 N10 行文案（改为「按扇形读法共三处偏离：2 新增 + 1 删除」），并把 T-003 任务文件列入本任务的文档影响记录 | `sed -n '216p' .trae/tasks/T-2026-09-20-model-immutable-answer-domain-003.md`；`sed -n '28,34p' android/core/src/main/java/com/treelearn/core/domain/GenerationState.java` |
| R2-7 | 轻 | 本轮两处「顺序锁」中，`GenerationCommandTest.aCallerBugIsReportedBeforeTheVersionDecision` **当轮即通过**（交付方如实写明「说明该用例成为回归锁」），`T-021-round2-red.txt` 只有 1 条失败（`stabilize` 那条）；F3 的 API 变更（`PromptSpec.of` 返回 `DomainResult`）**没有任何 RED 记录** | `harness-tasks.md:47`「代码与行为变更的执行记录必须展示 RED（先加测试，命令，非零退出码，观察到的失败…）；若任务无法遵循 TDD，必须在执行前说明例外、理由、用户批准和替代验证证据；**事后批准无效**」——本轮至少 `GenerationCommand` 的顺序修复与 `PromptSpec.of` 的通道变更没有 RED。可确认的是：`theVersionDecisionIsTheSameOneGenerationCommandMakes` 若在改 API 之前运行**会**失败（那时 `of` 对同一输入抛异常），只是没有留下那次运行 | 在 TDD 记录里为这两项补写「无法先 RED 的原因 + 替代验证证据」（就用跨入口比对断言与本报告 2.8 的探针结果），不要用「当轮即通过」当作 TDD 证据；若判定为 TDD 例外，须说明用户批准状态（事后批准无效则此项应长期留 `PARTIAL`） | `tail -3 artifacts/evidence/T-021-round2-red.txt`；`grep -n "aCallerBugIsReportedBeforeTheVersionDecision" artifacts/evidence/T-021-round2-red.txt`（无 FAILED 行） |
| R2-8 | 观察 | ① `GenerationState.isStreaming()` 在 `src/main` 有 **0 个消费者**、测试里 **0 次引用**；② `PromptSpec.toString()` javadoc 声称「a log line must never leak prompt content (§15)」但**无测试断言**（我用探针确认当前实现为真：`leaks body? false`）；`GenerationCommand.toString()` 同样无断言 | `DomainErrorCode` 类注释自定的原则「每个码都必须有生产者…A code with no producer is not "surface we may need later": it is a rule nobody can be held to」；`AGENTS.md:82` 把「无生产者 / 不可达」列为已三次命中的缺陷类。`isStreaming()` 是**公开谓词但无生产者、无消费者、无断言**——不能称为已交付能力 | 给 `isStreaming()` 补一条断言（或在 T-008 真正消费前标注为「已声明但未接入」）；给两个 `toString` 补「不得泄漏正文」的断言（`GenerationOperationTest.theOperationDescriptionNamesTheStateAndTheBuffers` 已有同形断言可照抄） | `grep -rn "isStreaming" android/core/src/main/java android/core/src/test/java`（只有定义行）；探针 2 的 `leaks body? false` |

## 4. Gaps（我无法验证的部分）

- **G1**：`android/` **整个目录未被 git 跟踪**（`git ls-files android | wc -l` = 0）。因此
  「`GenerationState` 删边 2 行 + `DomainErrorCode` 删枚举常量 1 行 = 被改动 3 行」**无法用版本库核对**；
  「T-003 的 83 个用例原样保留」也只能用「逐类计数 ≥ 基线 + 被改写断言有理由」间接支持，
  不能证明类内没有其他断言被静默削弱。规模结论不受影响（即使把两个既有文件全部代码行计入也只有 328 < 500）。
- **G2**：无 VCS 基线，RED/GREEN 的**时间顺序**仍无法直接证明。本轮我能达到的上限是：
  RED 的失败原因确为符号不存在、49 处、覆盖三个文件；且 RED 的测试源是**改 API 之前**的一版（反而说明 RED 早于 F3）。
- **G3**：未做任何设备/模拟器验证（用户 2026-09-21 决定自行进行），本任务 Non-goals 也不含网络与 UI。**本报告不得读作设备行为已通过。**
- **G4**：`stabilize` 的 `revisionNo` 新鲜度依赖 T-004 的 `(message_id, revision_no)` 唯一约束，该表尚未落地，该风险本轮无法关闭；交付方已在 Risks 声明，我确认声明准确。
- **G5**：本轮我没有重跑 `verifyDependencyDirection`（`:core` 纯净性 WARNING 文案与「仅 `:tree-layout` 空跑」的结论沿用第 1 轮的独立验证结果）。
- **G6**：守门自检**完整模式（25 例）**我未重跑（需真实 Gradle 两次）；我只重跑了守门本体（`VERDICT: PASS`）与 `-Fast` 相关的既有证据（`OVERALL = SELFTEST PASS (23 case(s))`，第 1 轮已独立验证）。
- **G7**：T-003 的第 5 轮复核（N10 重新覆盖 D2 改动）不在我的范围内；我只核对了 T-003 记录与代码注释**文案**是否一致（R2-5）。

## 5. 结论与翻盘条件

### 我确实复核通过的（若这些之外无实质问题，即可判 PASS）

1. `:core:test --rerun-tasks` 独立重跑：`BUILD SUCCESSFUL`、exit 0、3 个任务真实执行；按 XML 实数 **121 用例 / 0 失败 / 0 错误 / 0 跳过**，与 `T-021-green-counts.txt`、`T-021-green.txt`（121 条 PASSED）三处同源。
2. 7 条验收条件**实质全部满足**，关键行为由自写探针（41 条断言）复核：版本按**引用**三跳传递、跨入口判定 15 种输入 0 分歧、三入口「形状先于业务」无条件成立、`STREAMING_ANSWER` 下允许晚到 reasoning 且不回退、缓冲逐字符复制、空/纯空白回答不可稳定化、38400 步随机序列无「STABLE 无 revision」反例、终端后一切写入被拒。
3. F1/F2/F3/F4/F5/F6/F7/F8/F10 **已真正修好**（逐项命令与输出见第 2.8 节）；F9 在三个已声明入口上成立。
4. 规模复算**逐格吻合**（新增 227 + 改动 3 = 230，超软预算 30 行为**警告**、未超 500 **无硬失败**）。
5. 回归：T-003 的 83 用例逐类保留（8 个类逐类数 ≥ 基线），3 条改写断言有理由且等价覆盖成立。
6. 守门 `VERDICT: PASS` / exit 0（我重跑）；`lint_summary.py` 从仓库根可读（0 error / 11 warning）；`:core:lint` 确不存在；`validate_tasks.py` 0 error / 1 warning。
7. 对抗性：其余 8 个错误码全部有生产者；`PromptSpec` 的「不泄漏正文」在实现层面为真。

### 交付方翻盘需要做的（按重要性）

1. **R2-1（中）**：把 `AnswerRevision.create` 的形状校验前置（首选），或按 `documentation.md` 口径在 `Guard` 里登记该偏差 + severity 并路由到 T-003，同时把 README 的包级措辞收紧。无论选哪条，都要让「包级规则」这句话与全包事实一致。
2. **R2-6（中）**：修掉任务文件里「RED 引用的行号与当前测试源一致 / RED 跑的就是最终测试源」这句已被本轮改成不实的话，改为本轮的真实关系（F3 前的测试源、`PromptSpecTest` 后移 11 行）。
3. R2-2 / R2-3 / R2-4 / R2-5 / R2-7（轻）：README 与 `Guard` 的例外清单去重；GREEN 证据的「119/121」表述与文件对齐；§7 论证第 3 点改成「无运行时行为丢失」；更新 T-003 的 N10 文案并把 T-003 任务文件列入文档影响记录；为两处无 RED 的改动补写原因与替代证据。
4. R2-8（观察）：补 `isStreaming()` 与两个 `toString` 的断言，或显式标注未接入。

以上 1–2 修完（3 可一并处理）即可判 PASS；`PARTIAL` 期间**不应**把任务状态推进到 `verified`。

### 建议追加到根 `AGENTS.md` 失败案例库

```markdown
- [2026-09-21] [domain] 把一条规则**声明为「包级」**却只在前几个入口落地 → 规则在包内仍有反例：
  T-021 第 2 轮把「参数形状先于业务判定」写成包级规则（README + Handoff）并给三个入口加了顺序锁，
  但**第四个**返回 `DomainResult` 的公开入口 `AnswerRevision.create` 依旧先判业务版本：
  `create("not-a-uuid", 0, null, "  ", null, false, null, -1L)` 返回 `PROMPT_POLICY_VERSION_REQUIRED`，
  4 个调用方 bug（畸形 id / revisionNo=0 / 空白正文 / now<0）全被一个业务码遮住。
  规则：写下「包级/X 级规则」时，**必须机械枚举该范围内全部同类入口**（此处 = 所有返回 `DomainResult`
  的 public static 工厂），逐个加断言（或逐个登记偏差 + severity），并把「枚举命令」写进证据；
  否则「包级」二字本身就是一条新的、无人能核对的断言。

- [2026-09-21] [harness] 让**跨轮次仍为真**的自述留在任务文件里 → 下一轮的修复使它变不实而无人同步：
  「RED 证据引用的行号与当前落盘的测试源一致，说明 RED 跑的就是最终测试源」在第 1 轮为真，
  第 2 轮改掉 `PromptSpec.of` 的返回类型并新增 3 个用例后，`PromptSpecTest` 的 8 处引用行全部失配
  （整体后移 11 行、且 RED 源里 `of` 直接返回值），这句话就成了不实的 TDD 自证。
  规则：任何「与某个文件当前内容一致/行号一致」的自述都要**标注它对哪一版成立**（附该版本的
  文件行数或 SHA-256），并在同一轮修改该文件后**重跑那条核对命令**；否则请改成不依赖版本的表述。
```

（说明：上述两条中，第一条是本轮**新型**缺陷——第 1 轮 F3/F9 是「同一条规则写在两处」，本轮是「规则被声明为包级而只落地三处」，
是同一家族的新形态，符合 `AGENTS.md:59` 的追加条件；第二条是「跨轮次自述失效」，此前失败案例库未覆盖。）
