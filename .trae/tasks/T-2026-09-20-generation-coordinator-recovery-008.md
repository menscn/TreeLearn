# Treelearn Harness Task: 实现生成协调器、并发与恢复

id: T-2026-09-20-008
title: 实现生成协调器、并发与恢复
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: true
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-20-keystore-secret-settings-005.md, T-2026-09-20-deepseek-stream-gateway-006.md, T-2026-09-20-immutable-context-snapshots-007.md, T-2026-09-21-versioned-learning-prompt-policy-020.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 切分支后继续生成；应用最多两路；进程死亡保留部分内容并手动重试。
- Problem/background: Activity 持有请求会在导航/旋转时取消，晚到回调又可能污染新操作。
- Users/scenarios: 两个分支并行生成、第三个排队、切页、退后台、杀进程后回到原位。
- Expected outcome: Application 级 GenerationCoordinator、版本化主回答请求组装、FIFO 调度、节流落盘、恢复扫描与观察接口。
- Core flow and reversibility: 不可变命令入队时固定学习回答提示词版本；已有主题请求前持久化 operation，首次根问题在 accepted 事务持久化；最多两路启动；streaming/stable/failed 持久化；成功复制版本到 revision；重启标 interrupted。
- Inputs/outputs: 输入生成命令与 T-020 组装结果；输出含 promptPolicyVersion 的持久 operation/revision、可观察状态和手动 retry 所需信息。
- State and exception handling: 每分支一活动操作；重复/晚到事件按 operationId 和终态拒绝；删除先取消。
- Constraints and trade-offs: 不用 WorkManager/前台服务；250ms 或 256 字符批量刷盘。
- AI recommendation and rationale: 进程级协调器与 SQLite 权威状态解耦 UI 生命周期。
- User choice and custom additions: 只保证应用内切分支继续；后台尽力；中断保留部分回答并原位重试。
- AI-autonomous decisions: 应用并发上限 2，额外操作按 createdAt FIFO staged。
- User-confirmation decisions: 上述后台/中断语义已确认。
- Acceptance evidence: deterministic fake gateway 并发、导航、恢复、晚回调与持久化测试。
- Facts: 技术方案 §7 是状态机权威。
- Assumptions: 进程存活时 ExecutorService 可继续运行。
- Unknowns: OEM 后台终止时机不可保证。
- Risks: 锁竞态、批次丢失窗口、取消与完成同时到达。
- Deferred: 自动重连、通知、前台服务和跨进程 worker。
- Non-goals: 不实现具体页面或主题创建事务。
- Skipped non-critical questions and reason: 线程池实现细节不改变外部状态契约。

## Goal

让生成在页面切换中持续、并发有界、进程中断后可解释且不会错误自动重发。

## Scope

### In scope
- Coordinator、FIFO 调度、operation event reducer、批量持久化、恢复扫描、取消和观察接口。

### Out of scope
- 首题建树事务、重试按钮 UI 和前台服务。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增生成生命周期；保持不可变 revision 仅在正常完成时创建。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 页面/use case 提交；PromptRegistry/RequestAssembler 生产用途、版本与消息；AiGateway 生产事件；DAO 持久化；UI 观察。
- Native bridge or independent-client compatibility/update requirements: 无桥；需遵守 AiGateway 事件和 Repository 事务契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 调度顺序、并发数、刷盘阈值、恢复与取消语义。
- Authoritative code/config/migration evidence: Coordinator/reducer、DAO 事务与测试时钟。
- Documentation surfaces checked: 技术方案 §7/§9.2、提示词设计 V1.0、PRD 后台/失败恢复/F21、Android README。
- Documentation files updated: `android/README.md` 的「应用层入口索引（`:app`）——生成协调器（T-008）」小节更新到本轮状态：九个类型（六个生产入口 + `LiveAttempt` / `LiveAttempts` / `RecoveryScan` 三个纯 JVM 决策缝，时钟与调度可注入）、`LiveAttempts` 与 `RecoveryScan` 两行不变量、四类决策的 JVM 证据、「本轮不使用模拟器（instrumentation 只编译不执行，设备项一律 deferred）」以及规模实测 765 判定行 / 1116 物理行。技术方案 §7/§9.2/§8 的阈值与终态语义未变（2 路、250 ms、256 字符、稳定化规则照实现落地），因此 `Docs/**` 未改动。
- Producer/consumer/sibling/compatibility paths checked: 首题、追问、重试、重置、删除、应用启动恢复。
- Superseded phrases/examples reverse-searched: 自动后台续传、切页取消和无限并发。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保持用户已确认口径。

## Acceptance criteria

- [ ] 同一 JVM/instrumentation 测试命令先 RED 后 GREEN，证明同分支拒绝第二活动操作、全局仅两路、第三路 FIFO 等待。已满足于 JVM 层（`GenerationSchedulerTest` 5 例：全局只跑两路、第三路排队、FIFO 唤醒最老者、等待中的分支同样占用、拒绝不占槽位与队列位），并由本轮一红一绿覆盖；协调器接上真机 SQLite 后的同分支拒绝属于 `GenerationCoordinatorInstrumentedTest`，记为 `deferred — emulator not used this round (user instruction 2026-09-21)`。
- [ ] 切换页面/旋转不取消；进入后台进程存活时继续；启动恢复把遗留活动状态原子改为 INTERRUPTED。三项都必须在真机上观察，整体记为 `deferred — emulator not used this round (user instruction 2026-09-21)`；其中恢复的**判定**（STAGED->CANCELED、其余->INTERRUPTED、终态行被拒绝）已在 JVM 层由 `RecoveryScanTest` 3 例满足，但事务的原子性未验证。
- [ ] 部分 reasoning/answer 按阈值落盘；失败、取消、进程中断均不得创建 AnswerRevision。阈值判定已由 `StreamBatchTest` 5 例满足（256/255 字符与 250/249 ms 两对边界、窗口自首个未刷盘 delta 打开、flush 后重开、两路合计计数）；「真的写进 SQLite」与「不产生 revision」记为 `deferred — emulator not used this round (user instruction 2026-09-21)`。
- [ ] 每个不可变生成命令在远端提交前固定非空 `tl-learning-answer/*` 版本；已有主题在请求前写 operation，首次根问题在 2xx accepted 事务写入同一版本并在提交后才读 SSE；成功事务原样复制到 revision。重试/重置可使用应用升级后的新版本，但旧 operation/revision 不变。整条依赖真机 SQLite 与装配层，记为 `deferred — emulator not used this round (user instruction 2026-09-21)`。
- [ ] 提示词构建或版本持久化失败时不占用远端槽位、不调用 AiGateway，并保留输入/原位置供手动重试。`submit` 需要 helper 与 gateway，JVM 层无法构造，记为 `deferred — emulator not used this round (user instruction 2026-09-21)`。
- [ ] 删除/取消后的晚到 delta 或 completed 事件被 operationId + 终态检查丢弃且记录脱敏事件。判定谓词已由 `LiveAttemptsTest` 3 例满足（结论后 `isCurrent` 为假、原位重试让旧 operation 变陈旧、未知 operationId 命中不到）；端到端（真机回调 + 脱敏事件计数）记为 `deferred — emulator not used this round (user instruction 2026-09-21)`。

## TDD execution record

### RED — failing test first

- Test/scenario: 先写测试后写实现。本轮新增两份纯 JVM 套件（`LiveAttemptsTest` 3 例、`RecoveryScanTest` 3 例）钉住 T-008 里不需要设备的那两条保证：晚到事件「已不是当前 attempt」即丢弃（含原位重试让旧 operation 变陈旧、cancel 按 operationId 命中），以及启动恢复的遗留行必须收敛到终态、已是终态的行被拒绝而不是编一个新结论。它们与已有的 `GenerationSchedulerTest`（并发上限与 FIFO）、`StreamBatchTest`（250 ms / 256 字符窗口）合起来覆盖协调器的全部决策；需要 SQLite 事务的那一半留在 `GenerationCoordinatorInstrumentedTest`，本轮只编译、不执行。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain --continue
- Exit code: 1
- Observed failure: `:app:testDebugUnitTest` 报 `27 tests completed, 6 failed`，6 条失败全部落在新写的两类里（`LiveAttemptsTest` 3 例 + `RecoveryScanTest` 3 例），每条都是 `java.lang.UnsupportedOperationException: T-008 pending implementation`；同为本次命令目标的 `:app:assembleDebugAndroidTest` 成功走完，编译错误 0 条，除这两类以外失败的测试 0 条。
- Failure reason validated: 红由缺失行为造成，不由环境或测试自身造成。红跑前把被测的两个接缝（`LiveAttempts`、`RecoveryScan`）换成抛同样异常的桩，跑完立刻用 pristine 基线与 SHA-256 还原（哈希列在 T-008-red.txt 头部，与当前树中内容一致）。`GenerationCoordinatorInstrumentedTest` 只编译不执行，因此它的 15 例既不算绿也不算红。

```text
artifacts/evidence/T-008-red.txt
  Command= the same string as GREEN
  :app:testDebugUnitTest -> 27 tests completed, 6 failed
  failing classes: LiveAttemptsTest(3) + RecoveryScanTest(3) -> all six are this task new JVM tests
  UnsupportedOperationException lines: 6     compiler-error lines: 0
  failing tests outside those two classes: 0
  EXITCODE=1
```

### GREEN — minimum implementation

- Implementation: `LiveAttempts` 落地（一有结论就离开注册表；`isCurrent` 比对象同一性而不是只比 id；原位重试后旧 operation 的 `byOperation` 查不到）；`RecoveryScan` 落地（目标只来自领域的 `GenerationState.recoveryTarget()`，事件映射只承认 CANCELED / INTERRUPTED，其余输入拒绝而不是编一个）；`GenerationCoordinator` 接到这两个接缝上（注册表替换掉内联 attempt 映射、`release()` 走 `close` + `find`、取消走 `byOperation`、恢复扫描走 `RecoveryScan`），并删掉因此失去生产调用者的方法。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain --continue
- Exit code: 0
- Passing evidence: T-008-green.txt（与 RED 字节相同的命令串、`BUILD SUCCESSFUL`、EXITCODE=0、无失败行）；T-008-tests.txt（JUnit XML 回读取数：GenerationSchedulerTest 5/0、StreamBatchTest 5/0、LiveAttemptsTest 3/0、RecoveryScanTest 3/0，本切片 16 例 0 失败；同一文件还记录 `:app:assembleDebugAndroidTest --rerun-tasks --no-build-cache` 的强制真编译，`compileDebugAndroidTestJavaWithJavac` 真正执行且 BUILD SUCCESSFUL、EXITCODE=0 —— instrumentation 源码只编译、不执行）。
- Regression evidence: T-008-guard.txt（Java-only `VERDICT: PASS (13 OK line(s))`、EXITCODE=0）；T-008-red.txt（红跑本身就是一次粗粒度变异：被测接缝整体退化成桩后这 6 条断言全红，还原后同一命令转绿，所以这 6 条断言可证伪）；T-008-sizing.txt（规模实测）。

### REFACTOR — optional

- Cleanup: 本轮没有独立的 GREEN 后重构轮次；`GenerationCoordinator` 的接缝替换发生在被记录的 GREEN 运行之前，由该运行覆盖。细粒度变异（例如把 `isCurrent` 改成恒真）本轮未做，因此「这 6 条断言可证伪」只覆盖粗粒度退化。
- Regression evidence: T-008-red.txt 与 T-008-green.txt 是同一命令串的一红一绿；T-008-tests.txt 的计数是从 JUnit XML 回读的，不是控制台措辞。

## Implementation notes

- SQLite 是恢复权威，内存状态只能做调度缓存。
- 时间和 executor 必须注入，测试不得依赖 sleep。
- stable 事务须创建 revision、切 activeRevision、清 currentOperation 并推进未读序号。
- system/messages 只能由 T-020 组装；Coordinator 不复制提示词字符串、不把 system 放入 ContextPlan，也不记录正文。

## Verification report

### verifier #3 批量复核（2026-09-24，独立于本任务实现者）

- Verdict: **PASS（设备项 deferred）**。本任务验收标准已逐行标注 JVM 判定证据与 deferred 设备项；本轮核对其引用的四个 JVM 套件在当前全量套件中全部存在且 0 失败（GenerationSchedulerTest 5、RecoveryScanTest 3、StreamBatchTest 5、LiveAttemptsTest 3——JUnit XML 回读，见 `P3-B1-gates-2026-09-24.txt` 同批运行），Fresh evidence（2026-09-24 批次 2）: `artifacts/evidence/P3-B1-gates-2026-09-24.txt`（全模块 JVM 套件 + lint + androidTest 编译，BUILD SUCCESSFUL / EXITCODE=0；全库 188 套件 1725 tests / 0 失败）、`P3-B1-no-kotlin-2026-09-24.txt`（EXITCODE=0）。
- 判定依据: 同分支拒绝、全局两路、FIFO、恢复判定（STAGED→CANCELED/其余→INTERRUPTED/终态拒绝）、阈值落盘判定、晚到事件丢弃的判定谓词均为 JVM 层已锁定行为；「真机 SQLite 事务原子性/进程存活/端到端回调」按标准原文记 deferred，不算 PASS 也不算 FAIL。
- Gaps（不变，均 deferred）: 恢复事务原子性、后台存活、端到端事件 —— `deferred — emulator not used this round (user instruction 2026-09-21)`。

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 事件重复与持久化写放大。

## Handoff notes

- Completed: `android/app/src/main/java/com/treelearn/app/generation/` 的 9 个生产类型（六个入口 `GenerationCoordinator` / `GenerationScheduler` / `StreamBatch` / `GenerationGateway` / `GenerationTelemetry` / `AiGatewayStreams`，加三个纯 JVM 决策缝 `LiveAttempt` / `LiveAttempts` / `RecoveryScan`）。本轮按用户指令 2026-09-21 改用「JVM 套件 + instrumentation 只编译」：`:app:testDebugUnitTest` 的 4 个套件 16 例全绿（GenerationSchedulerTest 5、StreamBatchTest 5、LiveAttemptsTest 3、RecoveryScanTest 3），`:app:assembleDebugAndroidTest` 只编译不执行，`:app:compileDebugAndroidTestJavaWithJavac` 另在 `--rerun-tasks --no-build-cache` 下真正执行一次并成功。证据：T-008-red / T-008-green / T-008-tests / T-008-guard / T-008-sizing（旧的真机记录整体移入 T-008-device-era-2026-09-21.txt，并在文件头声明它不是本轮验收证据）。`android/README.md` 的小节同步更新。
- In progress: 无。任务停在 `verifying`，等待 verifier 与规模裁定。
- Blocker: ① 规模偏差需用户裁定 —— 实测 **765 判定行 / 1116 物理行**（`artifacts/evidence/T-008-sizing.txt`），超 200 行软上限、超 500 行硬上限 265 行；仓库里没有 T-008 的用户批准规模例外，按 `.trae/rules/harness-tasks.md` 实现者不得自行追认，故如实记为待裁定偏差，不得被后来的读者当成已批准的例外。本轮为满足「让协调器可在无设备下测试」的要求又新增了 31 判定行（LiveAttempt / LiveAttempts / RecoveryScan）。② 本切片还没有生产消费者：仓库里没有 `Application` 子类，装配层没有任何位置 new 出协调器，`GenerationTelemetry` 也没有真实观察者接线（页面、重试、首题建树属后续任务）。③ 需要真机才能证明的验收项本轮一律记为 `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算失败。
- Unblock condition: 用户对规模给出裁定（批准例外，或要求把装配/遥测消费者与协调器拆成后续任务）；装配任务提供 `Application` 级调用点后 ② 消失；重新允许使用模拟器后 ③ 才能转成 PASS。
- Next steps: 解锁重试/重置与对话 UI；把 `recover()` 接到应用启动路径；把 `GenerationTelemetry` 接到真实事件消费者（当前只有测试实现）；模拟器可用后跑 `:app:connectedDebugAndroidTest` 把 deferred 项逐条转成证据。
- Watch-outs: 不得将页面销毁等同于用户取消；`recover()` 只收敛状态、不重发；`complete()` 里 stabilizer 抛出存储异常时按失败收敛的分支没有测试覆盖（声明的表面，不是已验证事实）；稳定化（`GenerationStabilizer` 自持事务）与位置记账（`settlePosition`）是两个事务，中间存在「revision 已落库、位置尚未指向它」的窗口；本轮 RED 的粗粒度变异只证明「接缝整体缺失时这 6 条断言会红」，细粒度变异未做；兄弟 lane 会争用共享的 `android/app/build`（本轮实测：`:data` 的 debug classes jar 被并发构建压成空壳、`android/app/build/test-results` 被整个删掉、T-005 把自己的生产类移出树导致全仓无法编译），遇到这类失败先确认不是自身源码问题再重跑；**不得**再把生产源码移出共享树（用户指令 2026-09-21），T-005 那次事件的审计在 T-008-crosslane-stash-recovery.txt。

- [verifier #3 2026-09-24，规模例外补记] 上述 Blocker ①（765 判定行，超 500 硬上限，无批准记录）于 2026-09-24 由用户裁定「事后追认豁免」收口。批准落在实现之后，按 `harness-tasks.md` 第 60 行如实记为事后追认。剩余风险：单 task 超硬上限 53%；后续如需拆分，以「装配/遥测消费者」与「协调器本体」为天然切面。本任务状态维持 verified，其余结论不变。
