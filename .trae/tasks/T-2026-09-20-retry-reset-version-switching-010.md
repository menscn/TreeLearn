# Treelearn Harness Task: 实现重试、重置与版本切换

id: T-2026-09-20-010
title: 实现重试、重置与版本切换
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: true
depends_on: [T-2026-09-20-generation-coordinator-recovery-008.md, T-2026-09-20-topic-branch-lifecycle-009.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 失败重试与稳定回答重置都创建新操作；成功后产生新不可变版本，不覆盖旧来源。
- Problem/background: 原地改写回答会让既有子分支、搜索结果和上下文悄然变化。
- Users/scenarios: 用户在失败位置重试，或对稳定回答重新生成并浏览历史版本。
- Expected outcome: RetryGeneration、ResetAnswer、SelectRevision 用例和明确的操作可用性规则。
- Core flow and reversibility: retry/reset 创建新 operation；成功追加 revision 并切 active；选择旧版仅改展示指针。
- Inputs/outputs: 输入 message/operation/revision ID；输出新 operation 或切换后的 active revision。
- State and exception handling: 活动生成、非 AI message、跨 message revision、partial 版本和重复命令均拒绝。
- Constraints and trade-offs: 用户消息与 AI 逻辑位置不重复；retry/reset 不增加对话轮数。
- AI recommendation and rationale: 将尝试、稳定版本和显示选择分开，维持引用完整性。
- User choice and custom additions: 中断保留部分内容并回到原位手动重试。
- AI-autonomous decisions: 失败 partial 只属于旧 operation；新操作从同一固定输入重新开始。
- User-confirmation decisions: P0 不可变版本与失败恢复语义。
- Acceptance evidence: 纯 JVM + SQLite 集成测试覆盖版本锚定和非法命令。
- Facts: 技术方案 §5.1、§7 是权威。
- Assumptions: message 可保存 activeRevisionId 与 currentOperationId。
- Unknowns: 无。
- Risks: 重试重复插入用户消息或把旧 partial 升级为 stable。
- Deferred: 自动重试和版本对比视图。
- Non-goals: 不实现按钮样式、网络 parser 或分支画布。
- Skipped non-critical questions and reason: 历史版本列表排序按 revisionNo，已由领域约束确定。

## Goal

让每次重试/重置可审计、可回退，并保证旧子分支永远引用原稳定版本。

## Scope

### In scope
- Retry/Reset/SelectRevision 用例、权限校验、事务切换和版本历史查询。

### Out of scope
- 对话页面、复制和搜索 UI。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增重试/重置/切版；保留消息 ordinal、轮数和旧 branch sourceRevisionId。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Chat UI 触发；Coordinator 执行；DAO/revision/snapshot 消费。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 操作创建、稳定切换、历史保留和轮数行为。
- Authoritative code/config/migration evidence: application use cases、Repository 事务与测试。
- Documentation surfaces checked: 技术方案 §5/§7、PRD 重试/重置/版本章节、原型操作菜单。
- Documentation files updated: 实际可用性或版本行为变化时同步当前态文档。
- Producer/consumer/sibling/compatibility paths checked: 失败重试、稳定重置、旧版选择、子分支来源、未读/轮数。
- Superseded phrases/examples reverse-searched: 原地覆盖、重试新增一轮和旧分支随当前版本变化。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写历史版本记录。

## Acceptance criteria

- [ ] 同一目标测试命令先 RED 后 GREEN；retry/reset 只新增 operation，成功后才新增 immutable revision。
- [ ] 重试/重置不重复用户消息、不增加轮数；失败 partial 不成为可分叉来源。
- [ ] 旧子分支在 active revision 切换和手动选择旧版后仍保持原 sourceRevisionId、reasoning 与 answer。
- [ ] 活动生成、跨消息 revision 和重复命令返回稳定错误，不破坏当前版本。

## TDD execution record

### RED — failing test first
- Test/scenario: `PositionCommandsTest`（22 例，纯 JVM）写在 `PositionCommands` 存在之前；受试者是**真实仓库**，这次 RED 没有移动、删除或 stash 任何文件——缺失的符号就是一个还没写的类。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --continue --rerun-tasks --no-build-cache --no-daemon --console=plain
- Exit code: 1
- Observed failure: javac 自报 52 个错误，每一条都落在 `PositionCommandsTest.java`，且都是同一个缺失类型 `PositionCommands`。
- Failure reason validated: 证据文件里带源路径的 `错误:` 行 104 条 = `找不到符号` 54 + `程序包` 50（两遍打印，2 × 52）；`符号:` 行只有一种取值 `变量 PositionCommands`；`88 actionable tasks: 88 executed`（无 UP-TO-DATE 混充）；`EXITCODE=1` 在第 571 行。没有第二种成因，也没有一行落在别人的文件上。
- Evidence: artifacts/evidence/T-010-red.txt
- 作废的早期 RED：最初两版夹具用了 `"branch-1"` / `"revision-1"` 这类可读占位串，而 `Guard.id` 要求规范 UUID，那些占位串会在运行时抛异常——夹具先改对，RED 再重录。作废原因与 GREEN 的 5 次作废尝试一并记在 `artifacts/evidence/T-010-attempts.txt`。

### GREEN — minimum implementation
- Implementation: `:app` 的 `com.treelearn.app.generation` 新增两个类型——`PositionCommands`（重试/重置/切版的判定、「拒绝 → 错误码」的唯一定义、会话语义 `Baseline`）与 `AnswerVersions`（`retry` / `reset` / `selectRevision` / `versions`，拒绝即回滚事务）。未改动 `:core`、`:ai`、`:data`、`:tree-layout` 的任何文件，也未新增第三方依赖。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --continue --rerun-tasks --no-build-cache --no-daemon --console=plain
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL in 26s` 与 `89 actionable tasks: 89 executed`（无 UP-TO-DATE，故确实执行了编译与测试）；`:app:testDebugUnitTest` 汇总 198 例 0 失败 0 错误 0 跳过，其中 `PositionCommandsTest` 22 例 0 失败 0 错误 0 跳过；`:app:assembleDebugAndroidTest` 产出 `app-debug-androidTest.apk`，即 instrumentation 源码编译通过；`EXITCODE=0` 在第 138 行。
- Regression evidence: Java-only guard（`T-010-guard.txt`：`VERDICT: PASS (13 OK line(s))`、exit 0）；`:app:lintDebug`（`T-010-lint.txt`：exit 0，0 error / 18 warning，且没有一条落在本任务的文件里）；源码扫描（`T-010-source-checks.txt`：扫描 1/3/4 均 no-match，扫描 2 的 13 处命中全是 T-012 测试里的明显合成占位串）；变异测试（`T-010-mutation.txt`：在仓库外副本上删掉「在跑尝试」判定 → 恰好那 4 例转红、其余 18 例不动，还原后回到 22/0）。仓库内 GREEN 之前有 5 次作废尝试，全部失败在别人的文件里，逐条记在 `T-010-attempts.txt`。
- Evidence: artifacts/evidence/T-010-green.txt
- 串行窗口内确认复跑（不替代上一行）：`artifacts/evidence/T-010-green-confirm.txt` —— 同一条命令行、同一真实仓库、`BUILD SUCCESSFUL in 24s`、`89 actionable tasks: 89 executed`、`EXITCODE=0`；`:app:testDebugUnitTest` 的 198 例 / 0 失败 / 0 错误由盘上 JUnit XML 复算得到；`android/app/build/outputs/androidTest-results/connected/debug` 文件数仍为 0。
- 另有一轮**仓库之外**的 GREEN：共享树一度无法产出干净的一轮（三条 sibling lane 的测试引用着尚未落地的生产类型，javac 在它们的文件上以 100 条截断失败），于是在镜像副本 `%TEMP%\t010-green\android`（256/256 文件、无多余文件）上跑**同一条命令字符串**，只在副本里把 8 个尚不可编译的 sibling 测试源置为注释；属于本任务的任何文件都未改动，仓库全程未被修改。读数：`BUILD SUCCESSFUL in 30s`、`89 actionable tasks: 89 executed`、`EXITCODE=0`、`PositionCommandsTest` 22 例 0 失败。证据：`artifacts/evidence/T-010-green-scratch.txt`。

### REFACTOR — optional
- Cleanup: 无重构。从盘上读回：生产代码 87 + 125 = 212 行（非空非注释口径；物理 370 行）；测试源码 `PositionCommandsTest` 281 行、`AnswerVersionsInstrumentedTest` 429 行。没有为「更整齐」改动任何已通过的行为，也没有留下重复判定点——拒绝判定与「拒绝 → 错误码」映射各只有一处定义（`PositionCommands`），事务边界沿用既有的 `DatabaseTransactionRunner` 与 `GenerationStabilizer` 的展开手法。
- Regression evidence: 无重构，故沿用 GREEN 的回归证据（guard / lint / 源码扫描 / 变异测试），本轮未重跑。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## 本轮验证状态（用户 2026-09-21 指令：本轮不使用模拟器）

| 验收项 | 状态 |
| :--- | :--- |
| 同一目标命令先 RED 后 GREEN | 已取得：RED exit 1（`T-010-red.txt`）/ GREEN exit 0（`T-010-green.txt`），两条命令行逐字符相同；串行窗口内已确认复跑 exit 0（`T-010-green-confirm.txt`） |
| 权限判定（活动生成 / 非 AI 位置 / 无稳定版本 / 跨消息 revision / 重复命令） | 已取得（JVM，22 例）：判定与领域自身守卫在同一组输入上逐个比对通过 |
| 拒绝不破坏当前版本；retry/reset 不重复用户消息与轮数；旧子分支保持原 sourceRevisionId、reasoning 与 answer | **deferred — emulator not used this round (user instruction 2026-09-21)**。源码在 `AnswerVersionsInstrumentedTest`（8 例），本轮只编译不执行；`android/app/build/outputs/androidTest-results/connected/debug` 文件数为 0 |
| 版本历史查询（按 revision_no 升序） | 同上 deferred。只读路径本身由 `AnswerRevisionDao.findByMessage` 的 `ORDER BY revision_no` 提供 |
| Java-only guard | 已复跑：`VERDICT: PASS (13 OK line(s))`、exit 0（`T-010-guard.txt`） |
| lint（`:app:lintDebug`） | 已复跑：exit 0；0 error / 18 warning，本任务文件 0 条（`T-010-lint.txt`） |
| 源码扫描 | 已复跑：本任务文件无凭据形状文本、无日志/Bundle/Intent/toString 面；证据文件无对话正文（`T-010-source-checks.txt`） |
| 变异测试 | 已做（仓库外副本）：4/22 转红且正是那 4 例；还原后 22/0（`T-010-mutation.txt`） |

## Implementation notes

- `retry` / `reset` 只返回新命令、不写 operation 行：行由协调器在提交时写，所以「重试只新增一个 operation」由构造保证，而不是靠这里第二次插入。
- `attemptRunning` 问的是存储（`GenerationOperationDao.findActiveByBranch`，其部分唯一索引保证每分支至多一条活动行），不问 `messages.active_operation_id`——后者在今天的代码里只有清除方、没有生产者。
- 「拒绝 → 错误码」放在 `PositionCommands.codeFor`，是唯一定义点；`MESSAGE_NOT_FOUND` 与 `REVISION_NOT_OF_THIS_POSITION` 共用 `OPERATION_NOT_COMPLETABLE`，原因（`:core` 归另一条 lane，新增错误码要改它）写在类 javadoc 与 README 里。
- 形参形状检查留在领域守卫里，判定缝只看形状良好的值；`selectRevision` 仍用 `Guard.id` 校验 revisionId，与 `Message.activateRevision` 一致。
- 规模：生产代码 212 行（非空非注释口径，87 + 125），超 200 软上限、未超 500 硬上限；本任务不申请规模例外，按 verifier 警告级处理。

## Verification report

### verifier #3 批量复核（2026-09-24，独立于本任务实现者）

- Verdict: **PASS（设备项 deferred）**。TDD 闭环完整（RED exit 1 / GREEN exit 0 同命令、仓库外副本 GREEN、变异测试 4/22 精准转红、guard/lint/源码扫描全过），「本轮验证状态」表逐项如实标注；唯一缺失的是本报告段的判定本身。212 行超 200 软上限、未超 500，按警告级处理。
- Fresh evidence（2026-09-24 批次 2）: `artifacts/evidence/P3-B1-gates-2026-09-24.txt`（全模块 JVM 套件 + lint + androidTest 编译，BUILD SUCCESSFUL / EXITCODE=0；全库 188 套件 1725 tests / 0 失败）、`P3-B1-no-kotlin-2026-09-24.txt`（EXITCODE=0）。
- 补充核验: `PositionCommandsTest` 22 例在当前全量套件中 0 失败（JUnit XML 回读），与 T-010-green.txt 的历史读数一致；`AnswerVersionsInstrumentedTest` 8 例随 androidTest 编译通过（`P3-B1-gates-2026-09-24.txt`），执行仍 deferred。
- Gaps（不变，均 deferred）: 设备执行项 —— `deferred — emulator not used this round (user instruction 2026-09-21)`。

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: operation / revision 指针竞态——`selectRevision` 的读-判-写在同一事务内，但 `retry` 返回命令与协调器落 operation 行之间仍有窗口。

## Handoff notes

- Completed: `PositionCommands` 与 `AnswerVersions` 落地并接入 `:app`；RED/GREEN 单一记录已取得（exit 1 / exit 0，命令行逐字符相同）；guard、lint、源码扫描、变异测试齐备；`android/README.md` 已同步运行时事实与缺口。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 交独立 verifier 复核。设备可用后跑 `:app:connectedDebugAndroidTest`，把上面三条 deferred 转成实测。下游 chat UI（T-013）调用 `retry` / `reset` / `selectRevision` / `versions`。
- Watch-outs: ① 本任务的仓库内 GREEN 只在 sibling lane 的 UI 代码与测试停止报错时才可能通过；本轮期间的失败全部在别人的文件里，逐条见 `T-010-attempts.txt`，不要把它们当成本任务的 RED。② **重试的「原尝试基线」目前由调用方给出**：要求沿用原 attempt 固定的 model / thinking，但 `android/data/**` 的 `GenerationOperationDao` 只暴露 `findById` / `findActiveByBranch` / `findRecoverable`，没有「某 message 最近一次 operation」的访问器，而该包属另一条 lane，本任务不改；因此 `Message.bindOperation` 至今仍无生产调用者。补一个按 message 取最近 operation 的读取，是数据层 lane 的后续工作。③ 变异的证据只在仓库外副本上做，不要把该做法照搬进仓库。④ `versions()` 对不存在的 message 返回空列表而不是失败，调用方要靠 `retry` 区分「位置没了」与「还没有版本」。⑤ 不要把 `REVISION_NOT_OF_THIS_POSITION` 当作「revision 已过期」用。