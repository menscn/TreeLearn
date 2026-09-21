# Treelearn Harness Task: 生成操作状态机与提示词策略锚定

id: T-2026-09-21-021
title: 生成操作状态机与提示词策略锚定
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 210
shared_code_touched: false
depends_on: [T-2026-09-20-model-immutable-answer-domain-003.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 持久化生成操作、固定分支来源，并按版本化提示词策略发起生成。
- Problem/background: T-003 已建立稳定版本与来源锚定，但一次生成尝试本身（可追加缓冲区、状态迁移、
  策略版本固定）尚未有领域载体；没有它，重试/重置无法区分「哪一次尝试」、也无法在流中断后保留部分内容。
- Users/scenarios: 用户提交问题、看到推理与回答流式出现、断网后原位重试、重置后仍能回到旧版本。
- Expected outcome: `:core` 提供 `GenerationCommand`、`GenerationOperation`、`PromptSpec`，以及
  「命令 → 操作 → 稳定 revision」的版本原样传递链路。
- Core flow and reversibility: 命令固定策略版本 → 创建 operation → 流式追加 reasoning/answer →
  正常结束且回答非空时 `stabilize` 生成 `AnswerRevision`；失败可删除本任务新增类型重建，不触及产品数据。
- Inputs/outputs: 输入为命令与流式增量；输出为领域结果、`AnswerRevision` 或领域错误。
- State and exception handling: 非法迁移返回 `ILLEGAL_STATE_TRANSITION`；终态拒绝晚到回调；答案为空时
  不可稳定化。
- Constraints and trade-offs: 纯 Java、无 Android/数据库/HTTP 依赖、无供应商 DTO；流式正文只存在于
  operation，不进稳定版本。
- AI recommendation and rationale: 把「可变的部分缓冲区」与「不可变的稳定版本」彻底分离，让不可变
  契约在流式场景下仍然成立。
- User choice and custom additions: 推理与最终回答属于同一稳定版本；operation 与 revision 都保留所用
  `promptPolicyVersion`。
- AI-autonomous decisions: 命令构造时校验用途与版本命名空间一致；缓冲区只在流式态可写。
- User-confirmation decisions: P0 不可变回答版本与持久生成状态机（V3.0 冻结）。
- Acceptance evidence: 纯 JVM 状态机与版本锚定测试。
- Facts: 技术方案 §5.1、§7、§9.2、§9.3 与提示词设计 V1.0 §3.1/§3.2 是权威；T-003 已交付
  `GenerationState`（含迁移表与返回领域错误的 `transitionTo`）、`AnswerRevision`、`PromptPurpose`。
- Assumptions: 物理持久化由 T-008 完成；本任务只做领域对象与不变量。
- Unknowns: 无。
- Risks: 把缓冲区做成稳定版本的一部分，破坏不可变契约。
- Deferred: SQLite 落盘、SSE 解析、并发上限、恢复扫描。
- Non-goals: 不实现网络、不实现 DAO、不实现 UI。
- Skipped non-critical questions and reason: 缓冲区批量落盘阈值（250ms/256 字符）属 T-008。

## Goal

让一次生成尝试的状态、部分内容与提示词策略版本在纯领域层可表达、可迁移、可机械验证，并保证版本从
命令到稳定 revision 原样传递。

## Scope

### In scope
- `GenerationCommand`、`GenerationOperation`、`PromptSpec`；命令构造校验；状态迁移；流式追加；稳定化。

### Out of scope
- SQLite schema/DAO（T-004/T-008）、DeepSeek DTO 与 SSE（T-006）、并发上限与恢复扫描（T-008）、UI。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增生成尝试领域载体；保持 T-003 已交付的
  `AnswerRevision` 不可变与 `BranchSource` 仅接受 `STABLE` 的语义不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `:data`（T-004）将持久化
  operation 行；`:ai`（T-006）将按 `PromptSpec` 组装请求；`:app`（T-008）的 GenerationCoordinator 驱动
  迁移。测试覆盖这三类消费者所需接口。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 无旧生产行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 生成尝试的领域状态与策略版本锚定。
- Authoritative code/config/migration evidence: `android/core/src/main/java/com/treelearn/core/domain/**`
  与 `android/core/src/test/java/com/treelearn/core/domain/**`。
- Documentation surfaces checked: 技术方案 §5.1/§7/§9.1/§9.2/§9.3/§6.1、提示词设计 V1.0 §3.1/§3.2、§16.1、
  `android/README.md`、`android/core/src/main/java/com/treelearn/core/domain/**`。
- Documentation files updated:
  - `android/README.md`：
    ① 新增「领域层入口索引（`:core`）」表，补齐本任务三项新类型与 T-003 既有类型，并写入三条错误通道规则
    的权威位置（`Guard` 类注释）；② 修掉被 T-003/T-021 变成不实的当前态断言——
    「`:core` 纯净性扫描空跑」改为「`:core` 已是真实强制、仅 `:tree-layout` 仍空跑」、
    「`:tree-layout` 由 T-016 填入」、「debug APK 15 KB / 2 dex」改为「约 70 KB / 3 dex 且注明字节数随构建变化」、
    「依赖配置三条全查」改为「按模式匹配全部变体 classpath（实测 53 个）+ 排除构建工具配置（实测 36 个）」、
    「自检 11 条注入 / 5 条正当写法」改为「`-Fast` 23 例 / 完整 25 例」并指向落盘脚本
    `scripts/dev/guard-selftest.ps1`；③ 守门禁忌补两条本轮踩到的真实陷阱（`-f` 比 `+` 结合紧、
    证据文件勿用 `>>` 重定向致 UTF-16）；④ 常用命令补自检脚本与 lint 摘要命令。
  - `android/scripts/dev/lint_summary.py`：默认报告路径改为**按脚本自身位置**解析。原默认值
    `app/build/reports/...` 是相对当前工作目录的，而该文件 docstring 给出的用法是「从仓库根运行
    `python android/scripts/dev/lint_summary.py`」——两者矛盾，照文档执行必然打印
    `no lint report`。属文档与行为不一致，已修。
  - `AGENTS.md`（失败案例库）：本轮新增 8 条实战教训，其中两条直接来自本任务的验证过程——
    ① 「`-f` 比 `+` 结合更紧」：实测 `'a{0}' + 'b{2}' -f 1,2,3` → `a{0}b3`，守门证据行长这样会**静默丢掉要证明的数字**；
    ② 「`>>` 重定向原生命令输出在 PowerShell 5.1 下写 UTF-16LE」，证据文件会变成二进制、评审无法复核。
    其余 6 条为「解析器不可自我背书」「不写手写清单，要断言匹配集非空」「无生产者/不可达规则必须删除」
    「dev 脚本默认路径须按脚本自身位置解析」「任务文件 RED/GREEN 的机械契约」「`harness` 类格式错误」。
    另外本轮把「本轮新增两条」以外的条目合并去重，避免失败案例库超过 30 条上限。
  - `android/README.md`（第 2 轮补充）：① 修掉「`AnswerRevision` 对缺失策略版本返回错误码是**已声明的唯一例外**」
    这条被本轮改动变成不实的当前态断言，改为「例外清单只在 `Guard` 类注释里维护，共**三处**：
    `AnswerRevision.create`、`GenerationCommand.create`、`PromptSpec.of`」（F1）；
    ② 新增「校验顺序：参数形状先于业务判定」一段（F9）；③ 领域入口索引中 `PromptSpec` 行补「`of` 返回 `DomainResult`」。
- `android/README.md` 领域入口索引补三项类型 → 已落地（见上）。
- §7 状态机有记录扩展已在 T-003 的 `GenerationState` 类注释与本文件说明，若本轮再增补边需同步技术方案 §7。
  → 本轮**删除**了一条内部偏离边（D2），未新增任何边，因此 §7 正文不需要改动。
- **§7 图示的读法（第 2 轮修正，原自述作废）**：实测 §7 代码块的三行是
  `STAGED -> VALIDATING -> SUBMITTING -> STREAMING_REASONING`（`STREAMING_REASONING` 起始列 **38**，
  `SUBMITTING` 起始列 **24**），后两行前导 **38 个空格**——与 `STREAMING_REASONING` 起始列对齐。
  **原自述「缩进使两条箭头看起来都从 `SUBMITTING` 分出」不成立**：若要从 `SUBMITTING` 分出，缩进应对齐
  第 24 列或它后面的箭头列（35），而实测是 38。按实测读法，§7 画的是
  `STREAMING_REASONING -> STREAMING_ANSWER` 与 `STREAMING_REASONING -> STABLE` 两条边的扇形展开；
  后一条正是 D2 删除的边，**因此这不是「§7 正文不需要改动」可以一笔带过的事**，重新论证如下：
  1. §7 图示是阶段草图，箭头不标源；三种读法中唯一被布局支持的是「对齐到 `STREAMING_REASONING` 列」这一种。
  2. 该读法下 §7 的 `STREAMING_REASONING -> STABLE` 在**任何**满足 §9.3「稳定版本要求最终回答非空」的
     实现里都不可达：回答缓冲区的唯一写入者 `appendAnswer` 写入前必把状态推进到 `STREAMING_ANSWER`。
     即 §7 这条边与 §9.3 互相矛盾，实现按 §9.3 收敛并显式记录（`GenerationState` 类注释的偏离清单第 3 条）。
  3. **没有行为丢失**：§7 读法下可实现的路径 `STREAMING_REASONING -> STREAMING_ANSWER -> STABLE` 都在图中，
     且 `appendAnswer` 一步同时完成「追加回答」与「进入回答阶段」，所以 38 列那条被删的边所描述的运行
     （只有推理、没有回答）按 §9.3 本来就不允许稳定化。
  4. §7 读法下**没有**列出的 `STREAMING_ANSWER -> STABLE`（唯一入口）与 `SUBMITTING -> STREAMING_ANSWER`（§9.1
     thinking disabled 必需）两条边，在 `GenerationState` 里显式存在并各自记录了理由。
  5. 结论：**不改 §7 正文**，但把读法与偏离写进 `GenerationState` 类注释与本记录，供后续维护者复核。
- Producer/consumer/sibling/compatibility paths checked: `:data`（T-004 需要 `commandId`/`branchId`/
  `messageId`/`purpose`/`promptPolicyVersion`/`state`/两个缓冲区/`createdAt` 这一组列）、
  `:ai`（T-006 需要 `PromptSpec` 与 `PromptPurpose` 组装请求，成功事务把版本原样写入 `AnswerRevision`）、
  `:app`（T-008 的协调器驱动 `advanceTo`/`appendReasoning`/`appendAnswer`/`stabilize`）。
- Superseded phrases/examples reverse-searched: 把流式正文写进稳定版本；用应用版本号代替策略版本。
  两处均已反向检索：代码中不存在把缓冲区交给 `AnswerRevision` 的路径（只有 `stabilize` 一处，
  且复制前后不做规范化）；`PromptPurpose.SEMVER` 拒绝 `1.0.0`、`2026.09.21`、`01.0.0` 等形似值，
  有 6 个专门用例。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改历史 PRD。

## Acceptance criteria

- [x] `GenerationCommand` 构造要求非空 `promptPolicyVersion`，且该版本必须由声明用途拥有（`PromptPurpose.ownsVersion`）；违反时返回领域错误而非静默接受。
      → `GenerationCommand.create` 返回 `PROMPT_POLICY_VERSION_REQUIRED` / `PROMPT_POLICY_VERSION_MISMATCH`；
      `GenerationCommandTest` 4 个用例覆盖（缺失 3 种输入、跨用途 1 种、version 形似值 6 种、校验顺序 3 种输入）。
      第 2 轮起判定委托给 `PromptPurpose.versionFailureFor` 这一**唯一判定点**，
      并由 `PromptSpecTest.theVersionDecisionIsTheSameOneGenerationCommandMakes` 直接比对两个入口的返回码；
      版本缺失的三处例外清单登记在 `Guard` 类注释（F2/F3/F9）。
- [x] 由命令创建的任何 `GenerationOperation` 继承同一 `promptPolicyVersion`，且该值在 operation 生命周期内不可改写。
      → `GenerationOperation.start(GenerationCommand, long)` 是唯一创建路径，逐字段复制；
      `GenerationOperationTest.thePinnedVersionCannotBeRewrittenForTheLifeOfTheOperation` 用反射断言字段 `final`
      且类型不存在任何 `set*` 方法，并断言迁移与追加之后取值不变。
- [x] 正常结束（终态 `STABLE` 且最终回答非空）时才可通过 `stabilize` 产生 `AnswerRevision`；该 revision 的 `promptPolicyVersion` 与命令逐字符相同，`reasoningContent`/`answerContent` 与缓冲区内容一致。
      → `stabilizeCopiesTheBuffersAndThePinnedVersionVerbatim`（含带前后空白的缓冲区，断言逐字符相等）；
      `aThinkingDisabledAttemptStabilizesWithEmptyReasoning`（§9.1 thinking disabled 形状）。
      **「正常结束」的前半句是调用方义务，领域层无法观测**：`stabilize` 只见「非终态 + 回答去空白后非空」，
      因此流仍在进行中时调用它也会成功。该限制已写入 `stabilize` 的 javadoc、Gaps 与 Handoff watch-outs（F8）。
- [x] 非法状态迁移与终态后的晚到回调均返回领域错误（`ILLEGAL_STATE_TRANSITION`）；部分内容在失败/中断后仍可读，但**不能**用于 `stabilize`。
      → `illegalTransitionsAreReportedAsDomainErrors`、`aFailedAttemptRejectsLateCallbacksAndCannotBeStabilized`、
      `anInterruptedAttemptBehavesLikeAFailedOne`、`aSecondStabilizationIsRefused`。
- [x] `STREAMING_ANSWER` 状态下追加 reasoning 增量必须被接受（§9.3 分块边界不确定），且不把状态回退为 `STREAMING_REASONING`。
      → `aLateReasoningDeltaIsAppendedWithoutSteppingBack`。
- [x] `PromptSpec` 只含 `purpose`/`policyVersion`/`systemContent`，不引用任何 DeepSeek 类型；空 system content 被拒绝。
      → `theSpecHasNoProviderSpecificField` 用反射机械断言**恰好 3 个字段**且每个字段类型只能来自
      `java.lang.*` 或 `com.treelearn.core.domain.*`；`aBlankSystemContentIsRefused` 覆盖 `""`/空格/换行制表。
- [x] `cd android; .\gradlew :core:test` 返回 0，且新增用例可被 `--rerun-tasks` 强制真实执行。
      → `artifacts/evidence/T-021-green.txt`：首行命令行、`BUILD SUCCESSFUL in 12s`、`EXITCODE=0`；
      逐类统计 **121 用例 / 0 失败 / 0 错误 / 0 跳过**（本轮新增 38 个，见 `T-021-green-counts.txt`）。

## TDD execution record

### RED — failing test first
- Test/scenario: `PromptSpecTest`、`GenerationCommandTest`、`GenerationOperationTest` 三个测试类先落盘，
  对应生产类型尚未存在。
- Command: `cd android && ./gradlew --no-daemon --console=plain :core:test --rerun-tasks`
- Exit code: 1
- Observed failure: `> Task :core:compileTestJava FAILED`，随后 **49** 处
  `error: cannot find symbol`（`grep -n "errors$" artifacts/evidence/T-021-red.txt` → 第 254、508 行
  均为 `49 errors`；同一错误在正文与 `What went wrong` 各出现一次，故 `grep -c` 报 98），
  覆盖**三个**测试文件（`PromptSpecTest` / `GenerationCommandTest` / `GenerationOperationTest`）。
- Failure reason validated: 失败原因是**符号不存在**，不是环境或配置错误——
  三个生产源文件当时尚未创建；测试类本身没有语法或依赖问题
  （同一批测试文件在类型补齐后即编译通过），且 `:core:compileJava` 成功、只有 `:core:compileTestJava` 失败。
  另可交叉核对：RED 证据引用的测试行号（`PromptSpecTest.java:23/33/45/51/62/81/88/103` 等）与当前落盘的
  测试源一致，说明 RED 跑的就是最终测试源。

### GREEN — minimum implementation
- Implementation: 新增 `PromptSpec`、`GenerationCommand`、`GenerationOperation` 三个生产类型；
  同时删除 `DomainErrorCode.OPERATION_TERMINAL`（见「实现期发现」D1）并删除
  `GenerationState` 中一条不可达迁移边（见「实现期发现」D2）。
- Command: `cd android && ./gradlew --no-daemon --console=plain :core:test --rerun-tasks`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-021-green.txt`（首行为命令行，
  末行为 `BUILD SUCCESSFUL in 12s` / `3 actionable tasks: 3 executed` / `EXITCODE=0`；
  该文件已在第 2 轮**重录**以满足「证据须自带命令行与退出码」的要求，第 1 轮版本为 `in 11s` 且无退出码行）；
  逐类统计取自 `core/build/test-results/test/*.xml`（`artifacts/evidence/T-021-green-counts.txt`）：
  `AnswerRevisionTest 11 / BranchSourceTest 12 / BranchTest 12 / GenerationCommandTest 8 /
  GenerationOperationTest 20 / GenerationStateTest 15 / GuardTest 6 / MessageTest 13 /
  PromptPurposeTest 11 / PromptSpecTest 7 / TopicTest 6` = **121 用例，failures=0 errors=0 skipped=0**。
- Regression evidence: T-003 已有的 83 个用例全部保留并通过（121 − 38 新增 = 83）。
  唯一被替换的断言是 `GenerationStateTest` 中针对已删除迁移边的那一条，
  替换理由与等价覆盖写在该测试方法自身的 javadoc 里（见 D2）。
  专项守门：`artifacts/evidence/T-021-guard.txt` → `VERDICT: PASS (13 OK line(s))`、`GUARD EXITCODE=0`
  （第六轮刷新。守门覆盖全部五个模块、与本任务并非一一对应，故同一条命令的结果也在 T-002 的
  `artifacts/evidence/T-002-no-kotlin-guard.txt` 留档，两份同源同命令）；
  自检 `artifacts/evidence/T-002-guard-selftest.txt` → `OVERALL = SELFTEST PASS (40 case(s))`、
  `SELFTEST EXITCODE=0`（**完整模式**。第 1 轮引用的是同一路径下的一次 `-Fast` 运行，23 例且跳过 CASE 7/8，
  见 T-002 第六轮 F1——引用证据必须核对运行模式）。
  lint：`artifacts/evidence/T-021-lint.txt` → `BUILD SUCCESSFUL`、`LINT EXITCODE=0`，
  `:app:lintDebug` 0 error / 11 warning（与 T-003 基线一致；`:core` 无 lint 任务的说明见 Gaps）。

### RED/GREEN — 第 2 轮（修复第 1 轮独立复核发现 F1–F10）
- Test/scenario（RED）：新增两条**校验顺序**回归锁——`GenerationCommandTest.aCallerBugIsReportedBeforeTheVersionDecision`
  与 `GenerationOperationTest.aCallerBugIsReportedBeforeTheAttemptStateIsConsulted`；
  后者针对未修改的 `GenerationOperation.stabilize`（它先看状态、后看参数）。
- Command: `cd android && ./gradlew --no-daemon --console=plain :core:test --rerun-tasks`
- Exit code: 1
- Observed failure: `119 tests completed, 1 failed` —
  `GenerationOperationTest > aCallerBugIsReportedBeforeTheAttemptStateIsConsulted FAILED`，
  `java.lang.AssertionError: a malformed revision number must throw even on a terminal attempt`
  （`artifacts/evidence/T-021-round2-red.txt`）。另一条顺序锁当轮即通过，说明 `GenerationCommand`
  的顺序修复已生效、该用例成为回归锁。
- Failure reason validated: 失败是**断言失败**而非编译或环境错误；失败断言指向的行为缺陷与第 1 轮
  复核 F9 观察到的现象同源（业务码遮蔽调用方 bug）。修复只动 `stabilize` 的检查顺序，
  不改任何既有语义。
- Implementation（GREEN）：`stabilize` 在读取状态**之前**先用 `Guard.atLeast(revisionNo, 1, …)` 与
  `Guard.epochMillis(now, "createdAt")` 校验参数形状；`GenerationCommand.create` 把五个非版本参数的
  形状校验抽成 `requireArgumentShape` 并在版本判定前执行；`PromptSpec.of` 同样先校验形状。
- Command: 同上
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-021-green.txt` + `T-021-green-counts.txt`，
  **119 用例 / 0 失败 / 0 错误 / 0 跳过**（当轮新增 3 个用例：顺序锁 2 个 + `PromptSpecTest` 的跨入口一致性锁 1 个）。
  该文件是**当轮**证据：此后又补了 2 个 `PromptPurposeTest` 用例（新公开方法 `isAbsent`/`versionFailureFor` 的
  直接覆盖），因此最终 GREEN 是 121 用例，见上一条；两处数字差异已在此说明，勿当成证据不一致。
- Regression evidence: 第 1 轮的 116 个用例全部保留并通过（121 − 5 新增 = 116），
  逐类计数差仅为 `GenerationCommandTest 7→8`、`GenerationOperationTest 19→20`、`PromptSpecTest 6→7`。
  以下三条断言被**改写**（不是删除），理由见「独立守门发现与修复」表格 F3/F9/F10 行：
  1. `PromptSpecTest.aBlankVersionIsRefused` → `aBlankVersionIsReportedAsTheMissingPolicyCode`：
     同一输入原先期望抛 `IllegalArgumentException`，现期望 `PROMPT_POLICY_VERSION_REQUIRED`。
  2. `PromptSpecTest.aVersionOwnedByAnotherPurposeIsRefusedAtConstruction` →
     `aVersionOwnedByAnotherPurposeIsReportedAsAMismatch`（同上，期望 `PROMPT_POLICY_VERSION_MISMATCH`）。
  3. `GenerationStateTest` 新方法的 javadoc 由「Replaces two previous assertions」改为单数（仅表述修正）。

### REFACTOR — optional
- Cleanup: 无独立重构环节。第一次 GREEN 尝试在
  `GenerationOperation.stabilize` 处因 `StringBuilder` 无 `trim()` 而编译失败
  （45 行处的 `error: cannot find symbol … method trim()`），当场改为先快照
  `finalReasoning`/`finalAnswer` 两个字符串再判断，顺带消除了原先的重复 `toString()` 调用；
  该失败与修复都保留在证据演进中，未回改 RED 记录。
- Regression evidence: 修复后重跑 `:core:test --rerun-tasks` → `BUILD SUCCESSFUL`、exit 0，
  即上一条 GREEN 证据。

### TDD or sizing exception — only with prior user approval
- Exception type: sizing（超出 200 行软预算，未超 500 行硬上限；无用户批准的规模例外）
- Reason: 实测**生产代码 230 行**（新增 227 + 改动 3），高于本任务 `estimated_production_lines: 210`
  约 9.5%，高于软预算 200 约 15%。物理行 763（含注释 380、空行 55），逐文件：

  | 文件 | 代码行 | 注释 | 空行 | 物理行 |
  | :--- | ---: | ---: | ---: | ---: |
  | `GenerationOperation.java`（新） | 139 | 132 | 15 | 286 |
  | `GenerationCommand.java`（新） | 62 | 63 | 6 | 131 |
  | `PromptSpec.java`（新） | 26 | 48 | 5 | 79 |
  | `GenerationState.java`（改） | 89 | 111 | 19 | 219 |
  | `DomainErrorCode.java`（改） | 12 | 26 | 10 | 48 |

  口径与 T-003 一致：**只计非空非注释的生产代码行**，测试、文档、生成文件不计
  （`.trae/rules/harness-tasks.md` 第 45 行）。`GenerationState.java` 与 `DomainErrorCode.java`
  是既有文件，计入预算的只有被改动的 3 行（删边 2 行 + 删枚举常量 1 行），因此
  **被修改的生产代码行 = 227 + 3 = 230**。
  与第 1 轮的差异（224→227 生产代码行，667→763 物理行）全部来自第 1 轮复核的修复：
  集中式例外清单、`requireArgumentShape`、`versionFailureFor`、`isAbsent`、`stabilize` 调用方义务段
  与「Check order」段，**基本落在注释与显式前置校验上**；这一轮没有新增类型、没有新增公开 API
  （唯一签名变化是 `PromptSpec.of` 的返回类型，见 F3）。
  测试 707 物理行（`PromptSpecTest` 160 + `GenerationCommandTest` 183 + `GenerationOperationTest` 364），不计入。
  实测命令与逐文件结果落盘在 `artifacts/evidence/T-021-sizing.txt`（口径用 T-003 的未改动文件
  `DomainErrorCode.java` 校准：复算得 12/26/10/48，与第 1 轮 verifier 的独立复算逐格相同）。
- User approval: 未取得。**本项留待用户追认**（同 T-003）。
- Alternative verification: 不适用（TDD 已真实执行，非以例外替代验证）。

## Implementation notes

- 为什么要拆出本任务：T-003 的实现规模已超预算（见 T-003「TDD or sizing exception」），把生成尝试
  从领域身份与不可变版本中分离，既让 T-003 收敛，也让「可变缓冲区 vs 不可变版本」这条边界由一个独立
  任务单独验收。
- `PromptSpec` 是 `:core` 的供应商无关契约；完整提示词正文由 `:ai` 的注册表持有，**不进入领域实体**。
- 缓冲区只在流式态可写；`FAILED`/`INTERRUPTED` 之后只读，用于原位重试展示与部分内容搜索。
- 稳定化必须原子：产生 `AnswerRevision` 与把状态置为 `STABLE` 是同一个领域动作，不允许中间态。
- `PromptPurpose.ownsVersion` 已收紧为「前缀 + `/` + `MAJOR.MINOR.PATCH`」；命令构造直接复用它。
- 第 2 轮把策略版本的判定收敛为**唯一判定点** `PromptPurpose.versionFailureFor`：`GenerationCommand.create`
  与 `PromptSpec.of` 都委托给它，`AnswerRevision.create` 因为不持有「声明用途」而问的是较弱的问题，
  走 `PromptPurpose.ownerOf`（两个不同问题、两个谓词，不是同一规则的两份拷贝）。
  「版本是否缺失」只有一个定义 `PromptPurpose.isAbsent`。这三处「缺失返回错误码」的例外**集中登记**在
  `Guard` 类注释，README 只指向它、不再复制清单。

## 实现期发现与修复（IMPLEMENT 阶段，非 verifier 提出）

| # | 发现 | 影响 | 处理 | 证据 |
| :--- | :--- | :--- | :--- | :--- |
| D1 | 本任务落地后，`DomainErrorCode.OPERATION_TERMINAL` **没有任何生产者**：验收条件 4 明确规定终态晚到回调走 `ILLEGAL_STATE_TRANSITION`，`DomainErrorCode` 的旧注释也自述它「属 T-021」。 | 一个永远不可能返回的错误码会让调用方 `switch` 到不可达分支，与 T-003 已删除的 `BRANCH_SOURCE_INVALID` 是同一缺陷类。 | **删除该枚举常量**，并把 `DomainErrorCode` 的类注释改为「每个码都必须有生产者；新增码要连着返回它的代码路径一起加」。 | `git grep OPERATION_TERMINAL` 现在只命中该注释本身；`:core:test` 全绿。 |
| D2 | `GenerationState` 的 `STREAMING_REASONING -> STABLE` 边**不可达**。稳定化要求回答非空，而回答缓冲区的唯一写入者 `appendAnswer` 会先把状态推进到 `STREAMING_ANSWER`，因此「非空回答」蕴含 `STREAMING_ANSWER`。该边在 T-003 记录为「defensive, not proven necessary」，T-021 直接证明它**没有可达执行路径**。 | 与 D1 同类：规则无生产者。原单元测试为它给的理由（「模型不返回推理阶段直接给答案」）实际对应 `SUBMITTING -> STREAMING_ANSWER`，与这条边并不相符——即原测试的**理由与该边不对应**。 | **删除该边**，并在 `GenerationState` 类注释中重写偏离清单（第 1 轮曾把 §7 图示读成「单链」，第 2 轮按实测缩进改为「扇形展开」并据此重新论证，见 F6）；同步替换 `GenerationStateTest` 中那条断言，替换理由与等价覆盖写在该测试方法 javadoc 里。 | `GenerationStateTest` 15 用例通过，其中新增 `aReasoningPhaseThatProducedNoAnswerCannotBecomeStable` 同时锁住「该边不可用」与「唯一前进方向是进入回答阶段」。 |

> D2 触及 T-003 的交付面，因此 T-003 的复核轮次必须重新覆盖该改动；
> 已在 T-003 任务文件中作为 **N10** 登记（见该文件「独立守门发现与修复（VERIFY 阶段，第 4 轮）」）。

## 独立守门发现与修复（VERIFY 阶段，第 1 轮）

第 1 轮独立复核（报告：`artifacts/evidence/T-021-verification.md`）判 **`PARTIAL`**：
7 条验收条件实质全部满足、自写 30 条探针全过、**未发现伪造证据**、**未发现规模超 500 硬上限**，
但指出三类问题共 10 项。逐项处置如下（全部已落地并重跑证据）。

| # | 级别 | 发现 | 处置 | 证据 |
| :--- | :--- | :--- | :--- | :--- |
| F1 | 中 | `android/README.md` 仍写「`AnswerRevision` … 是**已声明的唯一例外**」，与同一文件 12 行外的 `GenerationCommand` 行及代码矛盾 | 改写为「例外清单只在 `Guard` 类注释里维护，共**三处**」，并补校验顺序一段；不再在 README 复制清单 | `grep -n "唯一例外" android/README.md` → 无命中 |
| F2 | 中 | `GenerationCommand.create` 把「缺必填参数」转成错误码却**未**声明为通道例外，反称自己符合规则 | 在 `create` javadoc 写明「Declared exception to the first bullet」，并把**唯一**的例外清单集中到 `Guard` 类注释（含「新增同类入口必须登记」规则）；`AnswerRevision.create` 的 javadoc 改为指向该清单 | `Guard.java` 类注释「The declared exceptions to the first bullet」段 |
| F3 | 中 | **同一输入**「版本合法但不属于声明用途」在 `PromptSpec.of` 抛异常、在 `GenerationCommand.create` 返回业务码——同类输入走两种通道 | 采纳方案 (a)：新增唯一判定点 `PromptPurpose.versionFailureFor`，`PromptSpec.of` 改为返回 `DomainResult<PromptSpec>`，两个入口共用同一判定；并新增**跨入口一致性断言**（对 8 种输入逐一比对两个入口的成功/失败与错误码）锁住该修复 | `PromptSpecTest.theVersionDecisionIsTheSameOneGenerationCommandMakes`；`:core:test` 121 用例全绿 |
| F4 | 轻 | TDD 记录写「40 处 / 两个测试文件」，实测为 **49 errors / 三个测试文件** | 改为 49 与三个文件，并给出可复核命令（`grep -n "errors$"` → 254/508 行） | 本文件 RED 记录；`artifacts/evidence/T-021-red.txt` |
| F5 | 轻 | `T-021-green.txt` 无命令行、无退出码，不能自证 | 用 `2>&1 \| Out-File -Encoding utf8 -Append` 重录：首行命令行、末行 `EXITCODE=0`；另落盘逐类用例数到 `T-021-green-counts.txt` | `artifacts/evidence/T-021-green.txt`、`T-021-green-counts.txt` |
| F6 | 轻 | 同一交付对 §7 图示给出两种互斥读法（任务文件说从 `SUBMITTING` 分出，`GenerationState` 注释说是单链） | 实测两行前导均 **38** 空格 = `STREAMING_REASONING` 起始列（`SUBMITTING` 为 24），**两种自述都不成立**；已在 `GenerationState` 类注释与本文件统一为「扇形展开，对齐 `STREAMING_REASONING` 列」，并据此**重新论证**「不改 §7 正文」的四点理由（含 §7 该边与 §9.3 互相矛盾、无行为丢失） | 本文件「§7 图示的读法」段；`GenerationState` 类注释偏离清单 |
| F7 | 轻 | 文档影响记录只列 `android/README.md` 与 `lint_summary.py`，漏了本轮改过的 `AGENTS.md` | 补列 `AGENTS.md` 及其 8 条内容概要 | 本文件「Documentation files updated」 |
| F8 | 轻 | Gaps 不完整：「正常结束」在领域层完全不可表达，`stabilize` 对流仍在进行中的操作也会成功，javadoc 未把该义务写给调用方 | `stabilize` javadoc 新增「Caller obligation: "normally finished" is not observable here」一段（说明替代方案只会把流状态塞进纯领域类型）；同义项同步进 Gaps 与 Handoff watch-outs | `GenerationOperation.java` `stabilize` javadoc；本文件 Gaps / Handoff |
| F9 | 轻 | 参数校验顺序未定义：同时带「畸形 id」与「缺失版本」时返回业务码，遮住调用方 bug | 定为包级规则「参数形状先于业务判定，按参数声明顺序」：`GenerationCommand.create` 抽出 `requireArgumentShape`，`PromptSpec.of` 与 `stabilize` 同样前置；`Guard`、两个 javadoc 与 README 写明该顺序 | 新增 2 条顺序锁用例（第 2 轮 RED 即其中 `stabilize` 那条） |
| F10 | 轻 | `GenerationStateTest` 新方法 javadoc 写「Replaces two previous assertions」，实际只有 1 条 | 改为单数「Replaces one previous assertion」 | `GenerationStateTest` 方法 javadoc |

**本轮新确立的一条原则（已写入 `AGENTS.md` 失败案例库）**：F3 与 F9 是同一缺陷类的两次出现——
「同一条规则/判定写在两处」。修法不是把两处改成一致的措辞，而是**收敛到唯一判定点**并**加断言直接比对**
两个入口，否则下一轮仍会漂移。

## Verification report

> 本节由**独立 verifier** 填写。以下为交付方自述的待核事实与已知缺口，**不构成 PASS**。

- Verdict: `PARTIAL`（第 1 轮，见 `artifacts/evidence/T-021-verification.md`）→ 交付方已完成 F1–F10
  全部修复并重跑证据（见上一节），**等待第 2 轮独立复核**；`PARTIAL` 期间任务状态**不推进**到 `verified`。
  第 1 轮明确复核通过的 7 项结论（7 条验收条件实质全部满足、2800 条动作序列穷举无「`STABLE` 无 revision」
  反例、其余 8 个错误码全部有生产者、D2 可达性论证成立、规模复算逐格相同且未超 500、守门与自检通过、
  `:core` 无 lint 任务）在修复中**未被削弱**：修复只收敛判定点、补前置校验与改注释，未改动任何成功路径语义。
- Evidence:
  - `artifacts/evidence/T-021-red.txt` — `:core:compileTestJava FAILED`、**49** 处 `cannot find symbol`
    （三个测试文件）、`49 errors` 两处、exit 1
  - `artifacts/evidence/T-021-round2-red.txt` — 第 2 轮 RED：首行命令行、`119 tests completed, 1 failed`、
    `AssertionError: a malformed revision number must throw even on a terminal attempt`、`EXITCODE=1`
  - `artifacts/evidence/T-021-green.txt` — 首行命令行、`BUILD SUCCESSFUL in 12s`、`EXITCODE=0`
  - `artifacts/evidence/T-021-green-counts.txt` — 逐类用例数，合计 **121 / 0 / 0 / 0**
  - `artifacts/evidence/T-021-guard.txt` — 全仓完整守门（stage 0 兄弟脚本自解析、stage 3 的 53 个变体 classpath、
    stage 3b 递归发现的 3 个 APK），`VERDICT: PASS (13 OK line(s))`、`GUARD EXITCODE=0`（第六轮刷新）
  - `artifacts/evidence/T-002-guard-selftest.txt` — **完整模式** `OVERALL = SELFTEST PASS (40 case(s))`、`SELFTEST EXITCODE=0`
  - `artifacts/evidence/T-021-lint.txt` — `BUILD SUCCESSFUL`、`:app:lintDebug` 0 error / 11 warning、`LINT EXITCODE=0`
  - `artifacts/evidence/T-021-sizing.txt` — 逐文件代码/注释/空行/物理行复算
- Changed/deleted assertion rationale and equivalent behavior coverage:
  1. `GenerationStateTest.happyPathTransitionsAreLegal` 中
     `assertTrue(STREAMING_REASONING.canTransitionTo(STABLE))` **被删除**。
     理由见 D2：该边无可达执行路径，且它原本的理由与边不对应。
     等价覆盖：`SUBMITTING -> STREAMING_ANSWER -> STABLE` 仍在同一测试中断言，
     并新增 `aReasoningPhaseThatProducedNoAnswerCannotBecomeStable` 锁住删除结果。
  2. `PromptSpecTest` 的**两条**断言被改写（非删除）：两条涉跨用途/缺失版本的用例原先期望
     `IllegalArgumentException`，现期望 `DomainResult` 失败码，以配合 F3 的通道统一；语义覆盖不变，
     并新增 `theVersionDecisionIsTheSameOneGenerationCommandMakes` 直接比对两个入口（F3 的回归锁）。
  3. 其余无断言被删除或放宽。T-003 的 83 个用例全部原样通过。
- Gaps:
  - **`:core` 没有 lint 任务**。`:core` 是 `java-library`（纯 JVM，按设计不引 Android 依赖），
     因此 `:core:lint` / `:core:lintDebug` 都不存在，`lint` 只对 Android 模块有意义。
     本轮 lint 证据只覆盖 `:app`（0 error / 11 warning，与 T-003 基线一致），
     **不能**声称 `:core` 通过了 lint。对 `:core` 的有效静态检查是编译、单元测试与 Java-only 守门。
  - 失败分类（`generation_operations.错误分类`，技术方案 §6.1）**未在本任务建模**。
     任务 Non-goals 已把网络与错误映射排除，`ErrorMapper` 属 T-006；
     T-004 建表时若需要该列，应由 T-006 补领域字段，**不要**为此任务补一个半成品字段。
  - **「流已正常终止」在领域层不可表达（F8）**。`stabilize` 的前提只有「未处于终态 + 回答去空白后非空」，
     它既不接收也不观测「协调器已收到正常流终止」这一事实，因此对一个**仍在流式中**的 operation
     调用 `stabilize` 会成功。领域层唯一能提供的相反保证是：一旦成功即为 `STABLE`，此后的晚到增量与
     第二次稳定化都会被拒。**该义务属于 T-008/T-009 的协调器**，已写入 `stabilize` javadoc。
  - 本任务未做任何设备/模拟器验证（用户 2026-09-21 决定：模拟与安装实测由用户自行进行）。
- Risks:
  - `stabilize(int revisionNo, long now)` 的 `revisionNo` 由调用方（T-008 的协调器）提供，
     本类型无法自证其新鲜度。真正的守卫是 `answer_revisions(message_id, revision_no)` 唯一约束
     （T-004 建表）+ 协调器在同一事务内读取当前最大序号。若 T-004 未落地该唯一约束，
     这里会静默产生「同一位置两个同号 revision」。
  - 缓冲区只有内存态，落盘批量化（250 ms / 256 字符，§7）在 T-008；本任务每收到一个 delta 就追加。
  - 与 F8 相关：若协调器过早调用 `stabilize`，回答的尾部会被丢弃（后续增量被终态拒绝）。
     这是**有意的失败方向**（宁可丢尾部也不污染已冻结的 revision），但需要 T-008 用「收到终止帧」
     作为调用前置条件，并由 T-019 的端到端验证覆盖。

## Handoff notes

- Completed: `:core` 新增 `PromptSpec`、`GenerationCommand`、`GenerationOperation`
  与对应 3 个测试类（38 个新用例）+ `GenerationState` 可达性收紧（+1 用例，含替换 1 条旧断言）；
  「命令 → 操作 → 稳定 revision」的策略版本原样传递链路已可用且被机械断言覆盖。
  策略版本的判定收敛到 `PromptPurpose.versionFailureFor` **唯一判定点**，三处「版本缺失返回错误码」的
  例外集中登记在 `Guard` 类注释，并由跨入口一致性断言锁住。
  证据：`artifacts/evidence/T-021-{red,round2-red,green,green-counts,guard,lint,sizing}.txt`。
- In progress: 无（等待第 2 轮独立复核）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: T-004（SQLite schema/DAO 持久化 operation 行）、T-008（GenerationCoordinator 驱动迁移与恢复扫描）。
- Watch-outs:
  - **不得**为方便把缓冲区暴露为稳定版本的一部分；`AnswerRevision` 必须继续全字段 final、无 setter。
  - `appendReasoning` 在 `STREAMING_ANSWER` 状态下必须被接受；不要为此新增状态或回退状态。
  - 终态一旦到达即封闭；晚到回调必须返回领域错误而不是丢弃或抛出未检查异常。
  - 版本原样复制意味着**不得**在稳定化时做任何规范化、trim 或升级；要换版本必须新建 operation。
  - **`advanceTo(STABLE)` 被刻意拒绝**，`STABLE` 只能经 `stabilize` 到达。任何试图「先把状态置为
    STABLE 再补 revision」的改动都会破坏「稳定化原子性」，请不要为了方便放开这条限制。
  - **`stabilize` 不验证「流已正常终止」**（F8）：调用方（T-008/T-009 的协调器）必须**先用终止帧
    作为前置条件**，否则回答尾部会被终态拒绝而丢失。不要试图在领域类型里加流状态标志来「修」这一点。
  - **空 delta 不推进阶段**：`appendReasoning("")` / `appendAnswer("")` 被接受但不改变状态。
    这是为了让 `reasoning_content: ""` 这类保活帧不能把 thinking-disabled 的尝试错推进推理阶段。
  - **不要重新引入无生产者的错误码或无可达路径的迁移边**。本轮已因此删掉
    `OPERATION_TERMINAL`（D1）与 `STREAMING_REASONING -> STABLE`（D2）。
  - **同一条规则不要写在两处**：策略版本的判定只有 `PromptPurpose.versionFailureFor` 一处，
    「版本缺失」的定义只有 `PromptPurpose.isAbsent` 一处，通道例外清单只有 `Guard` 类注释一处。
    新增同类入口要登记到 `Guard`，不要让 README 或第二个类持有副本（F2/F3 的成因）。
  - **校验顺序是包级规则**：参数形状先于业务判定，按参数声明顺序。新增返回 `DomainResult` 的入口
    必须先把形状校验前置，否则会重演 F9（业务码遮住调用方 bug）。
  - `stabilize` 的 `revisionNo` 新鲜度依赖 T-004 的 `(message_id, revision_no)` 唯一约束；
    建表时必须落地，否则见 Risks。
  - T-006 若需要失败分类，请补成显式领域字段并同步 §6.1 的列定义，不要塞进现有字符串。
