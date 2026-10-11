# Treelearn Harness Task: 桌面持久化——Provider/Model 配置 Store

id: T-2026-10-10-012
title: 桌面持久化——Provider/Model 配置 Store
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-10-10-desktop-dao-chat-core-010.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 设置页与 AI 装配需要 Provider/Model 持久化；语义对照 Android `SqliteProviderAdminStore`/Provider-Admin 用例与 SPEC-2026-09-23（最后提供商警示、模型只增不删/精确去重、大小写精确唯一）。
- Users/scenarios: Provider 增删改启停、模型获取合并、模型分工解析。
- Expected outcome: `ProviderStore`（list/get/insert/update(name/baseUrl/enabled)/delete/findByName）与 `ModelStore`（listForProvider/upsertMany（只增不删、精确去重）/deleteForProvider/listAll/countsByProvider）；**不得出现任何 Key 列**（密钥走 T-013 凭据端口）。
- Core flow and reversibility: 用例 → Store（事务）→ SQLite；可回退（删库）。
- Inputs/outputs: 输入=领域/配置对象与 UUID；输出=列表/Optional；时间毫秒。
- State and exception handling: 名称唯一冲突→可读异常（对齐 Android 的 DUP 语义）；删除最后提供商由用例层警示（Store 不做业务判断）。
- Constraints and trade-offs: 大小写精确去重（BINARY，测试证明 `Model-X` 与 `model-x` 可共存、同值拒绝）；内置 Provider 种子的读写不特殊化。
- AI recommendation and rationale: 两个小 Store 合一片；测试用真实 SQLite。
- User choice and custom additions: 按已批准方案。
- AI-autonomous decisions: 方法命名、更新语义（部分更新字段由调用方给全量对象）。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-012-*` RED/GREEN。
- Facts: T-009/T-010 交付的 schema 与 Store 风格。
- Assumptions: T-011 验收后开工（同 gradle 树串行）。
- Unknowns: 无。
- Risks: 与 Android 语义偏差 → 逐方法对照注释。
- Deferred: 附件/批注 Store（T-018）。
- Non-goals: 凭据存取、模型网络获取（Phase 4/5）。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付 Provider/Model 配置持久化，语义对照 Android 活动实现，真实 SQLite 取证。

## Scope

### In scope
- `ProviderStore`、`ModelStore`（含批量 upsert 合并语义：只增不删、精确去重）+ 测试（真实 SQLite：唯一名冲突、启停更新、删除级联模型、大小写精确去重、种子行读写）。

### Out of scope
- Key/凭据；模型网络获取；模型分工解析算法（共享领域/用例层）。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增内部持久化；无对外行为变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 对照 `android/app/.../wiring/SqliteProviderAdminStore.java`（语义注释标注）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无用户可见变化（内部实现）。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/storage/{ProviderStore,ModelStore}.java`。
- Documentation surfaces checked: 准入文档 §2.8/2.9。
- Documentation files updated: 无 → `documentation impact: none`（仅内部类，无端点/启动/数据目录变化；git diff 仅 `web/backend/src/**` 与任务记录）。
- Producer/consumer/sibling/compatibility paths checked: Android 对照注释。
- Superseded phrases/examples reverse-searched: N/A。
- `documentation impact: none` reason and evidence, if applicable: 同上。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令，cwd=web/backend，记录退出码与计数）；业务行 ≤200。
- [ ] 真实 SQLite：名称唯一冲突被拒；启停更新读回一致；删除 Provider 级联删模型；`upsertMany` 只增不删且精确去重（大小写不同可共存）；种子行可读。
- [ ] 类内不出现 Key/secret 语义字段（grep 证明）。
- [ ] checkstyle 通过；独立 verifier PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `ProviderModelStoreTest`。
- Command: `gradlew :test`
- Exit code: 1
- Observed failure: 见证据
- Failure reason validated: 缺失类

### GREEN — minimum implementation
- Implementation: 两个 Store。
- Command: `gradlew :test`
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

- `ModelStore.upsertMany` 的「只增不删/精确去重」与 Android 模型获取合并语义一致（读 `SqliteProviderAdminStore`/`ProviderAdminPresenter` 确认后写注释）。

## Verification report

- Verdict: PASS（独立 verifier 两轮：首轮 PASS 附 1 项建议 → 补丁后回归 PASS，2026-10-10）
- Evidence: verifier 自跑 `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）80 tests / 0 fail、`check` EXIT=0（含强制 checkstyle）；RED 证据真实（缺类 → `...-012-red.txt`；修复 RED 为找不到 `delete(String)` → `...-012-fix-red.txt`）。
- 验收逐条：唯一名冲突被拒且不留第二行；update 读回一致且 `created_at` 不变；删除 Provider 经 FK 级联删模型；`upsertMany` 只增不删、大小写精确（`Model-X`/`model-x` 共存、批内重复只入一行、重跑 added=0）；内置种子可读不被破坏；两 Store 无 Key/secret 字段（grep）。
- Android 对照：列表排序 `created_at ASC,id ASC`、insert 空值校验、级联一致；差异注记——desktop `update` 合并 Android `updateConfig+setEnabled` 并由调用方传时；`upsertMany` 自持事务（语义等价）。
- 修复记录（verifier 建议落地）：① 补 `ModelStore.delete(String id)`——**按行主键**、非恰好 1 行抛错（对照 Android `ModelDao.delete`；避免 wire id 同名跨 Provider 误删，3 个新用例真断言）；② javadoc 明确 `countsByProvider`/`deleteForProvider` 不提供及理由（计数可由 `listAll` 派生；级联已覆盖）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+8 用例（5 首轮 + 3 补丁）。
- Gaps: 无（per-model 删除已补）。
- Risks: 模型列表网络获取与合并的端到端行为待 Phase 4/5 验证（本任务只覆盖存储合并语义）。

## Handoff notes

- Completed: 实施 + 补丁 + 双轮独立 verifier PASS（2026-10-10）。产物：`ProviderStore`（83 行）、`ModelStore`（69 行）、`ProviderModelStoreTest`；真实 SQLite 80 项测试全绿。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-013（Windows 凭据端口，已建任务文件）；Phase 4 Provider 装配。
- Watch-outs: `delete` 按行主键；`upsertMany` 大小写精确（不得 NOCASE）。