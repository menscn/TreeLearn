# Treelearn Harness Task: 修复 ChatGenerationStageTest 既有失败（时序敏感）

id: T-2026-10-10-008
title: 修复 ChatGenerationStageTest 既有失败（时序敏感）
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-28-androidtest-db-fixture-isolation-024.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 40
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 挂在 T-2026-09-28-024「仪器测试全量批跑 DB 夹具隔离缺陷」下——两者同属「全量批跑红、按类/隔离跑绿，于是全量回归门禁不可用」这一族，024 覆盖 instrumentation（`connectedDebugAndroidTest`）那一半，本任务覆盖 JVM 单测（`:app:testDebugUnitTest` / `check`）那一半。本任务原先挂在 Web 本地版父需求 T-2026-10-10-001 下；该需求已随 2026-10-15「移除 Web 本地版」一并删除，因本任务属纯 Android 跟进（`stack: android`、不涉及 Web 代码）故予保留并改挂到此。来源为 verifier 建议 ②（非阻断）——`gradlew check` 长期非零会掩盖未来 app 回归。
- Problem/background: `android/app` 的 `ChatGenerationStageTest` 在迁移前基线（d2c10c1）即失败：整包 `check` 831 项 1 失败、隔离复跑 9 项 2 失败（`anAcceptedSendShowsTheSendingStageUntilTheFirstRowArrives` 期望 SENDING 得 IDLE；`aStopRequestTurnsTheTerminalReadIntoTheCanceledStage` 期望 CANCELED 得 FAILED）。更早记录见 T-2026-10-09-002。
- Users/scenarios: 开发与验收；该失败让全量回归门禁不可用。
- Expected outcome: 两个用例稳定通过且语义未被削弱；整包 `check` 恢复绿色（至少 app 单测面）。
- Core flow and reversibility: 先复现（隔离 + 整包）→ 定位时序/共享状态根因 → 修复（测试夹具或生产时序，按证据选择）→ 变异验证保留断言有效性。
- Inputs/outputs: 输入为测试与生产源码；输出为测试输出与退出码。
- State and exception handling: 若根因在生产代码时序（非测试夹具），需最小业务修复并说明行为影响。
- Constraints and trade-offs: 不得削弱断言；不得跳过/禁用用例；Java-only。
- AI recommendation and rationale: 先静态阅读 `ChatPresenter` 的发送/停止路径与 `QueueingExecutor`/`InlineUi` 夹具，判断是否存在真实时钟依赖或跨用例共享状态，再决定修夹具或修生产。
- User choice and custom additions: 无（属规则允许的常规实现选择）。
- AI-autonomous decisions: 修复层级与实现方式。
- User-confirmation decisions: 无。
- Acceptance evidence: 隔离与整包复跑输出、变异测试记录（退化实现使对应用例转红）。
- Facts: 失败在基线提交同样出现（原归因证据 `artifacts/evidence/T-2026-10-10-005-preexisting-attribution.txt` 已随 Web 版移除删除；「基线 d2c10c1 即失败」这一结论按本文件 Problem 段的复跑记录成立）。
- Assumptions: 根因在测试夹具或展示层时序，不涉及数据副作用。
- Unknowns: 根因未定位。
- Risks: 若根因在生产逻辑，修复可能触及聊天发送状态机（需完整回归）。
- Deferred: 无。
- Non-goals: 改变聊天业务语义、新增功能。

## Goal

`ChatGenerationStageTest` 的 9 个用例在隔离与整包两种运行方式下稳定通过，且断言语义保持或加强。

## Scope

### In scope
- 复现、根因定位、修复与变异验证。
- 必要时对 `ChatPresenter`/夹具的最小修复（保持行为契约）。

### Out of scope
- 其它历史失败、界面视觉、Web 端。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 发送中显示 SENDING、停止后终态按停止意图显示 CANCELED 的既有产品语义不得改变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): ChatPresenter 的 onSend/onStopRequested/onResumed 与测试夹具 QueueingExecutor。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason: N/A。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 视根因；若仅测试夹具则无运行时变化。
- Authoritative code/config/migration evidence: `android/app/src/{main,test}/java/com/treelearn/app/ui/**`。
- Documentation surfaces checked: T-2026-10-09-002、T-2026-10-10-005 记录。（此处为当时的核对记录；T-2026-10-10-005 记录与证据已于 2026-10-15 随 Web 本地版移除，不再存在于仓库。）
- Documentation files updated: 本任务记录。
- Producer/consumer/sibling/compatibility paths checked: 生成状态展示链。
- Superseded phrases/examples reverse-searched: N/A。
- `documentation impact: none` reason and evidence, if applicable: 待定（按根因）。
- Historical/PRD handling: N/A。

## Acceptance criteria

- [ ] 隔离复跑 `:app:testDebugUnitTest --tests "*ChatGenerationStageTest*"` 9/9 通过（含至少两次连续复跑证明稳定）。
- [ ] 整包 `gradlew check` 全绿（不再有该失败）。
- [ ] 变异验证：人为退化实现使对应用例转红后再还原，证据落盘。
- [ ] 断言未被削弱（diff 对照 + 说明）。
- [ ] 行数预算与文档影响记录。

## TDD execution record

### RED — failing test first
- Test/scenario: 既有失败即 RED（`ChatGenerationStageTest` 两用例）。
- Command: PENDING
- Exit code: PENDING
- Observed failure: 期望 SENDING 得 IDLE；期望 CANCELED 得 FAILED。
- Failure reason validated: PENDING（根因定位后补充）

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
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 本任务不阻塞 Web 主线；主线继续时保持 `ready`。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 任务创建（verifier 建议）。
- In progress: 待执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: Web 主线阶段间隙执行。
- Watch-outs: 不得用禁用用例或削弱断言通过；先证明根因再改。