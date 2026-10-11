# Treelearn Harness Task: 桌面持久化——生成操作/创建快照/派生结果 + 启动中断落定

id: T-2026-10-10-011
title: 桌面持久化——生成操作/创建快照/派生结果 + 启动中断落定
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 400
shared_code_touched: false
depends_on: [T-2026-10-10-desktop-dao-chat-core-010.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 生成操作是跨刷新/重启恢复的权威（`generation_operations`）；快照承载分叉冻结背景；派生结果（标题/推荐/翻译/压缩）需按输入 digest 判 stale。Android 对应 `GenerationCoordinator` 的持久化面与 `DerivedService`/`AiArtifactDao`。
- Users/scenarios: 流式生成中刷新/重启；分叉；推荐与翻译；旧输入结果不得冒充新结果。
- Expected outcome: `OperationStore`（插入含 commandId、状态/部分内容/错误更新、按分支活跃查询、按 commandId 查重、终态行不被后续更新覆盖）、`SnapshotStore`（不可变插入与读取）、`ArtifactStore`（按 kind+scope+purpose 读写 + input digest stale 判定 + 旧结果处理语义与 Android 一致）、`InterruptionSweeper`（启动时把持久化活跃状态落定为 INTERRUPTED，保留部分内容）。
- Core flow and reversibility: 用例 → Store（事务）→ SQLite；启动顺序：迁移 → 中断落定 → ready。可回退：删库重建。
- Inputs/outputs: 输入=领域对象与状态转换请求；输出=领域对象/`Optional`/计数；时间毫秒。
- State and exception handling: 终态后更新被拒（触发器）→ Store 以可读异常暴露；`commandId` 重复→返回既有操作身份；落定幂等（重跑无副作用）。
- Constraints and trade-offs: 状态枚举与转换判定以共享 `GenerationOperation` 为准；不在 Store 里另立状态机。终态写保护由 T-009 触发器兜底。
- AI recommendation and rationale: 中断落定独立成类，启动装配调用；测试用真实 SQLite 插入活跃行后落定。
- User choice and custom additions: 按已批准 Phase 顺序。
- AI-autonomous decisions: 方法命名、stale 处理细节（读时过滤/清理）以 Android `DerivedService`/`AiArtifactDao` 活动语义为准。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-011-*` RED/GREEN；真实引擎测试输出。
- Facts: T-009/T-010 交付的 schema 与 Store 风格；`generation_operations.command_id` 为幂等身份。
- Assumptions: T-010 已验收。
- Unknowns: 无。
- Risks: 中断落定与触发器冲突 → 用真实引擎测试证明活跃→INTERRUPTED 合法而终态不可改。
- Deferred: 生成协调器与 SSE（Phase 4）。
- Non-goals: 网络调用、事件流。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付生成操作、创建快照、派生结果的持久化层与启动中断落定，语义对照 Android 活动实现，真实引擎取证。

## Scope

### In scope
- `OperationStore`：insert（含 commandId、purpose、model、thinking、policy version、初始状态）、byId、byCommandId、activeForBranch、updateProgress（状态/部分内容，终态行拒绝）、markTerminal（STABLE/FAILED/INTERRUPTED/CANCELED + error code）、listActive。
- `SnapshotStore`：insert（schema_version/digest/payload）、findForBranch。
- `ArtifactStore`：insert/replace（按 kind+scope+purpose）、find（含 digest 判定：不匹配按 Android 语义处理并留有测试证据）、listForScope、deleteForScope（供删除用例）。
- `InterruptionSweeper`：`sweep()` 把持久化活跃状态行落定 INTERRUPTED（保留 partial 内容与 updated_at 更新），返回计数；幂等。
- 启动装配：迁移后、对外 ready 前执行落定（在 T-009 的 StorageConfiguration 中挂接）。
- 测试：真实 SQLite；commandId 查重、活跃查询、终态写保护、落定（含部分内容保留、重跑幂等、终态行不受影响）、快照不可变、artifact digest 语义。

### Out of scope
- 网络/流式推送；HTTP 端点；分叉用例（Phase 4）。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 启动序列新增落定步骤；ready 语义不变（落定完成后才 ready）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 对照 `android/app/.../generation/GenerationCoordinator.java`（中断落定与恢复读法）、`artifact/DerivedService.java`、`android/data/.../dao/AiArtifactDao.java`。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 服务启动会把上次异常退出的活跃生成落定为 INTERRUPTED（用户可见语义：刷新/重启后显示“已中断，可重试”）。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/storage/OperationStore.java` 等。
- Documentation surfaces checked: docs 技术方案 §7（中断落定）；准入文档 §2.5/2.6/2.7。
- Documentation files updated: `web/README.md`（启动行为补一句：重启后活跃生成落定为中断）——与 T-009 同文件不同段落，若冲突以本任务为准追加。
- Producer/consumer/sibling/compatibility paths checked: T-009 装配类。
- Superseded phrases/examples reverse-searched: N/A。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令，cwd=web/backend，记录退出码与测试计数）。
- [ ] 真实 SQLite：commandId 查重返回同一身份；活跃查询只含非终态；终态行更新被拒。
- [ ] 落定：活跃行→INTERRUPTED（partial 保留）；重跑幂等；终态行不受影响；启动装配顺序（迁移→落定→ready）由测试或启动证据证明。
- [ ] 快照不可变与按分支唯一由测试证明。
- [ ] artifact digest：输入变化后旧结果不冒充新结果（语义与 Android `DerivedService` 一致，测试证明）。
- [ ] checkstyle 通过；业务行判定值 ≤400（派工目标；硬上限 500；立项估 330 与派工 400 的冲突由 leader 于验收后统一为 400，实测 394 属警告级）。
- [ ] 独立 verifier PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `OperationSnapshotArtifactStoreTest`、`InterruptionSweeperTest`。
- Command: `gradlew test`
- Exit code: 1
- Observed failure: 见证据
- Failure reason validated: 缺失类

### GREEN — minimum implementation
- Implementation: 三类 Store + Sweeper + 装配挂接。
- Command: `gradlew test`
- Exit code: 0
- Passing evidence: 见证据
- Regression evidence: 既有测试全绿

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- stale 语义：以 Android `DerivedService`/`AiArtifactDao` 为准（读时判定并清理或写入替换），实现时在注释标注对照方法。
- 落定使用 `Clock`（UTC）注入以便测试；时间落库毫秒。

## Verification report

- Verdict: PASS（独立 verifier 两轮：首轮 PARTIAL 两项 → 修复后回归 PASS，2026-10-10）
- Evidence: verifier 自跑 `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）72 tests / 0 fail、`check` EXIT=0；RED 证据真实（缺类编译失败 → `...-011-red.txt`；修复 RED 为单测试失败 → `...-011-fix-red.txt`）；修复 GREEN `...-011-fix-green.txt`；checkstyle `...-011-checkstyle.txt`（文件名以实际为准）。
- 验收逐条：commandId 查重同一身份；activeForBranch 仅非终态；终态写拒（Store 先判 + 触发器兜底，测试双证）；落定（STREAMING→INTERRUPTED 保 partial、STAGED→CANCELED、重跑幂等、终态不动、启动顺序 ApplicationContextRunner 证明）；快照不可变+分支唯一；artifact digest（insert 条件化 / `applyIfBaselineMatches` CAS / replace / findFresh 不冒充）；状态机复用共享 `GenerationState`/`GenerationOperation`，未另立。
- Android 语义抽查（对照 `DerivedService`/`AiArtifactDao`/`GenerationOperationDao`）：一致；差异注记——`findFresh` 仅比 digest（Android `compressHistory` 另比 revision，各调用点本就不同，可接受）；Sweeper 用 `listActive` 而非 `processRecoverableStates`（当前集合相同，未来若分叉需同步）。
- 修复记录：① `markTerminal` 拒绝 `STABLE`（verifier 判定为真实语义偏差：领域与 Android 均只允许稳定化路径写 STABLE；修复后 javadoc 明示，调用点仅 Sweeper 走 `recoveryTarget()`，无需同步）；② 规模口径冲突由 leader 修正任务文件为 ≤400（派工目标；立项估 330 系低估，实测 394 警告级）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 原「允许 STABLE」断言按新语义修正为 FAILED 路径（注释说明理由：STABLE 属 Phase 4 稳定化器独占）。
- Gaps: Phase 4 稳定化器（唯一 STABLE 写入路径）待实现；artifact stale 的 revision 维度差异待 Phase 4 派生任务确认是否需要。
- Risks: `listActive` 与 `processRecoverableStates` 未来分叉风险（Watch-out）；394 行接近派工目标上限。

## Handoff notes

- Completed: 实施 + 修复 + 独立 verifier 回归 PASS（2026-10-10）。产物：`OperationStore`/`SnapshotStore`/`ArtifactStore`/`InterruptionSweeper` + `StorageConfiguration` 启动顺序（迁移→落定→就绪，失败关闭）；72 项测试全绿。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-012（Provider/Model Store）与 T-018（附件/批注 Store）；Phase 4 稳定化器独占 STABLE。
- Watch-outs: `markTerminal` 不再接受 STABLE；落定必须在 ready 前完成；`STAGED` 落定为 CANCELED。