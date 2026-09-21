# Treelearn Harness Task: 实现生成协调器、并发与恢复

id: T-2026-09-20-008
title: 实现生成协调器、并发与恢复
template_version: 2
task_type: implementation
status: ready
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
- Documentation files updated: 阈值或终态语义变化时同步技术方案。
- Producer/consumer/sibling/compatibility paths checked: 首题、追问、重试、重置、删除、应用启动恢复。
- Superseded phrases/examples reverse-searched: 自动后台续传、切页取消和无限并发。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保持用户已确认口径。

## Acceptance criteria

- [ ] 同一 JVM/instrumentation 测试命令先 RED 后 GREEN，证明同分支拒绝第二活动操作、全局仅两路、第三路 FIFO 等待。
- [ ] 切换页面/旋转不取消；进入后台进程存活时继续；启动恢复把遗留活动状态原子改为 INTERRUPTED。
- [ ] 部分 reasoning/answer 按阈值落盘；失败、取消、进程中断均不得创建 AnswerRevision。
- [ ] 每个不可变生成命令在远端提交前固定非空 `tl-learning-answer/*` 版本；已有主题在请求前写 operation，首次根问题在 2xx accepted 事务写入同一版本并在提交后才读 SSE；成功事务原样复制到 revision。重试/重置可使用应用升级后的新版本，但旧 operation/revision 不变。
- [ ] 提示词构建或版本持久化失败时不占用远端槽位、不调用 AiGateway，并保留输入/原位置供手动重试。
- [ ] 删除/取消后的晚到 delta 或 completed 事件被 operationId + 终态检查丢弃且记录脱敏事件。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- SQLite 是恢复权威，内存状态只能做调度缓存。
- 时间和 executor 必须注入，测试不得依赖 sleep。
- stable 事务须创建 revision、切 activeRevision、清 currentOperation 并推进未读序号。
- system/messages 只能由 T-020 组装；Coordinator 不复制提示词字符串、不把 system 放入 ContextPlan，也不记录正文。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 事件重复与持久化写放大。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 解锁重试/重置和对话 UI。
- Watch-outs: 不得将页面销毁等同于用户取消。
