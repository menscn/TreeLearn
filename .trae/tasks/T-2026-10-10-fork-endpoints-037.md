# Treelearn Harness Task: 稳定回答分叉端点（POST /api/branches/{id}/forks）

id: T-2026-10-10-037
title: 稳定回答分叉端点（POST /api/branches/{id}/forks）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 380
shared_code_touched: false
depends_on: [T-2026-10-10-core-rest-api-028.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001`（本地 Web 版）已确认；本任务交付其「稳定回答分叉、来源回溯」矩阵行的后端端点，属 T-028 核心闭环端点的相邻补全。
- Problem/background: 契约 §6.3 已冻结 `POST /api/branches/{id}/forks`（整条/选文分叉：冻结快照 + 溯源 + 可选首问原子接受），但后端只有主题/追问/重试端点，分叉端点不存在（404）。前端契约类型（`ForkRequest`/`ForkResult`）与 fixtures 已就位，缺实现。
- Users/scenarios: 学习者在某个稳定回答上整条分叉、或选中一句话分叉，并可选在新分支直接提问。
- Expected outcome: `POST /api/branches/{id}/forks` 按 §6.3 实现；仅稳定版本可分叉；冻结不可变出生快照；写全溯源列；可选首问原子接受；`commandId` 幂等；响应 `ForkResult`。
- Core flow and reversibility: 控制器薄 → `ForkCommandService` 校验/冻结/落库 → 可选委托 `ChatCommandService.sendMessage` 接受首问。全部为新增只写路径，可回退（无 schema 变更）。
- Inputs/outputs: 输入=`RequestDtos.BranchForkRequest`（§6.3 / `commands/fork-*.json`）；输出=`BranchDtos.ForkResult`（§5.2 / `dtos/fork-result.json`）。
- State and exception handling: 非稳定源/版本缺失→`OPERATION_NOT_STABLE`(409)；锚点畸形→`ANCHOR_INVALID`(400)；来源跨主题→`SOURCE_MESSAGE_NOT_IN_TOPIC`(400)；找不到分支/消息→`NOT_FOUND`(404)；拒绝封装按 §3.1。
- Constraints and trade-offs: 不改 `CoreApiController`/契约/Android/共享域/Rust 前端/schema；不新增依赖；时间毫秒；复用共享域（`Branch`/`BranchSource`/`BranchAnchor`/`ContextSnapshot`）唯一判定，不自造。
- AI recommendation and rationale: 复用既有 `ChatCommandService`（模型解析、幂等）与协调器接受路径，避免第二套模型/命令判定；分支+快照+指针在单事务内落库。
- User choice and custom additions: 已批准（按矩阵行交付，无新增澄清）。
- AI-autonomous decisions: 控制器/服务切分、store 事务化方法、错误码在此上下文中的取值。
- User-confirmation decisions: 无。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-037-*` RED/GREEN/checkstyle。
- Facts: 契约 §3.3/§5.2/§5.3/§6.3/§8；fixtures `commands/fork-{whole,selection}.json`、`dtos/fork-result.json`、`dtos/branch-tree.json`；共享域与 `BranchStore`/`SnapshotStore`/`ChatCommandService` 已存在。
- Assumptions: `DesktopSchema` 的 branches CHECK 已兜底「根分支不得携带溯源」；`context_snapshots` 唯一索引保证一分支一快照。
- Unknowns: `ForkResult.bootstrap` 在 `question` 存在时的精确字段语义（契约只说「等价首问」）——按 `TopicBootstrap` 形状取 `{topic, rootBranch=分叉出的子分支, userMessage, aiMessage, operation}`，报告中标注。
- Risks: 空问题分叉（仅建分支）无持久 `commandId` 载体 → 无法用 `OperationStore.byCommandId` 去重；已记录为偏差。
- Deferred: 分叉后子分支的生成上下文合并（`SnapshotHistoryMerger` 等价物）不在本任务；附件/派生。
- Non-goals: 不实现分支树读端点的改动；不改前端；不改 schema。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付 `POST /api/branches/{id}/forks`，以真实 HTTP + 真 SQLite 证据证明：仅稳定版本可分叉、出生快照冻结且不回流、溯源列落库、`commandId` 幂等、可选首问原子接受。

## Scope

### In scope
- `web/backend/.../api/ForkController.java`（新）：端点 + 锚点解码为共享域 `BranchAnchor`（畸形→`ANCHOR_INVALID`）+ 响应映射。
- `web/backend/.../generation/ForkCommandService.java`（新）：校验来源稳定性、冻结快照、落子分支+快照+指针（单事务）、`commandId` 幂等、可选首问委托 `ChatCommandService.sendMessage`。
- `BranchStore`：新增 `insertChild(Connection, Branch)`、`attachContextSnapshot(Connection, ...)`（事务作用域，复用既有 SQL）。
- `SnapshotStore`：新增 `insert(Connection, ContextSnapshot)`；`insert(branchId, sources, now)` 改为复用之。
- `GenerationApiConfiguration`：暴露 `SnapshotStore`、`ForkCommandService` bean。
- 测试 `ForkEndpointsTest`（真 HTTP + 桩网关 + 真 SQLite）。

### Out of scope
- 契约/fixtures 变更；`CoreApiController`/`GET /api/topics/{id}/branches`；前端；Android；`DesktopSchema`。
- 分叉后子分支的生成上下文合并、附件、派生任务。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增只写端点；既有主题/追问/重试/操作/SSE 端点不变；`BranchStore`/`SnapshotStore` 新增方法的既有单参方法语义不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者=前端 `ForkRequest`（`web/frontend/src/api/types.ts`）；消费者=`ForkResult` fixture 与前端类型；同级入口=`CoreApiController` 的 topic/branch/message 端点（不改动）。
- Native bridge or independent-client compatibility/update requirements: 不适用（本机同源 HTTP）。
- Behavior-neutral non-applicability reason, if applicable: N/A。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `POST /api/branches/{id}/forks` 由 404 变为按 §6.3 可用（用户可见）。
- Authoritative code/config/migration evidence: `web/backend/.../api/ForkController.java`、`.../generation/ForkCommandService.java`、`.../storage/{BranchStore,SnapshotStore}.java`。
- Documentation surfaces checked: 契约 §6.3/§5.2/§5.3/§8（已冻结，无需改）；`web/README.md` 状态节。
- Documentation files updated: 无（契约已在 T-014 冻结；端点实现不改变契约事实）。`documentation impact`: 见下。
- Producer/consumer/sibling/compatibility paths checked: `commands/fork-*.json`、`dtos/fork-result.json`、前端 `ForkRequest`/`ForkResult`。
- Superseded phrases/examples reverse-searched: 无（端点此前不存在）。
- `documentation impact: none` reason and evidence: 本任务为契约既定端点的实现，未新增/变更路由、DTO、错误码或枚举；契约文档与 fixtures 已描述目标行为，故不产生文档变更。
- Historical/PRD handling: 不改写历史文档。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test --rerun-tasks`，记录退出码与计数）；业务行 ≤500（软上限 200 警告）。
- [ ] 整条分叉成功且出生快照冻结（`context_snapshots` 有行、`branches.context_snapshot_id` 指向它）。
- [ ] 选文分叉：anchor 四列（start/end/quote/hash）落库且可经 `BranchStore.get` 读回。
- [ ] 非稳定源（版本缺失/非 AI 位置）→ `409 OPERATION_NOT_STABLE`；锚点畸形 → `400 ANCHOR_INVALID`。
- [ ] 快照不回流：改父分支后续内容后，子分支出生快照的来源列表不变。
- [ ] 根分支携带溯源列被 `DesktopSchema` CHECK 拒绝（真实 SQLite 断言）。
- [ ] `commandId` 幂等：同 id 重放返回同一子分支与同一 operation，不二次创建。
- [ ] 可选首问：成功落 USER+AI+操作（`bootstrap` 非空）；失败（非法模型）时问题位置不落库。
- [ ] checkstyle 通过；独立 verifier 复核；`validate_tasks.py` PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `ForkEndpointsTest`（整条/选文分叉、非稳定拒绝、锚点畸形拒绝、跨主题拒绝、不回流、根分支溯源 schema 拒绝、幂等、可选首问成功/失败共 10 例）。
- Command: `.\gradlew.bat :test --rerun-tasks`
- Exit code: 1
- Observed failure: `209 tests completed, 10 failed`——`ForkEndpointsTest` 10 例全部失败，根因为分叉端点及其装配缺失：`No qualifying bean of type 'com.treelearn.web.storage.SnapshotStore' available`，`POST /api/branches/{id}/forks` 未实现。
- Failure reason validated: 失败确因分叉端点/服务未实现（新测试无法建立上下文），非环境问题。
- Evidence: `artifacts/evidence/T-2026-10-10-037-red.txt`

### GREEN — minimum implementation
- Implementation: 新增 `ForkController`（端点 + 锚点解码为共享域 `BranchAnchor`）+ `ForkCommandService`（来源稳定性校验、冻结出生快照、子分支+快照+指针单事务三分写、可选首问复用 `ChatCommandService.sendMessage`）；`BranchStore` 增 `insertChild(Connection, Branch)`/`attachContextSnapshot(Connection, …)`；`SnapshotStore` 增 `insert(Connection, ContextSnapshot)`；`GenerationConfiguration` 暴露 `SnapshotStore` bean；新增 `ForkConfiguration` 暴露 `ForkCommandService` bean。
- Command: `.\gradlew.bat :test --rerun-tasks`
- Exit code: 0
- Passing evidence: 231 tests / 0 failed（`artifacts/evidence/T-2026-10-10-037-green.txt`）；新增 `ForkEndpointsTest` 10 例全绿。
- Regression evidence: 既有 221 例全绿（231 = 221 + 新增 10）；`.\gradlew.bat check` 退出码 0，checkstyle 通过（`artifacts/evidence/T-2026-10-10-037-checkstyle.txt`）。

### REFACTOR — optional
- Cleanup: 首次实现即为最小；无额外重构。首次 `check` 报出测试中 3 个无用导入，已删除（见 checkstyle 证据）。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行实测约 267，超软上限 200，未超 500 硬上限；仅警告，不需例外）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 复用共享域唯一判定：`BranchSource.fromOperation`（要求 STABLE）、`BranchAnchor.ofRange`（半开区间 + quote/hash + 跨度一致）、`ContextSnapshot.create/fromStored`（冻结载荷与摘要）、`Branch.child`（溯源必填）。
- 分叉写路径对齐 Android `BranchCreation.create`：单事务写 分支 + 快照 + 指针；指针先置空再回填（`branches.context_snapshot_id` 与 `context_snapshots.branch_id` 互相引用，单条 INSERT 无法承载）。
- 首问委托既有 `ChatCommandService.sendMessage`：复用模型解析与 `acceptForBranch` 的 USER+AI+操作单事务与 `commandId` 幂等，不另立判定。
- Android 对照：`android/.../wiring/SqliteChatStore.java#createBranch`（分叉写路径，委派 `BranchCreation`）、`android/data/.../dao/BranchCreation.java#create`（三分写单事务）、`android/app/.../wiring/ChatWiring.java#fork`（来源锚点 + `sourcesThrough` 快照来源）、`android/app/.../wiring/SnapshotHistoryMerger.java#merge`（出生快照逐行解析，fail-closed）。
- **偏差与边界（记录，不擅自放宽契约）**：
  1. `SOURCE_MESSAGE_NOT_IN_TOPIC` 按契约 §6.3「来源必须属于本主题」做**主题级**判定：子分支父级取来源消息所在分支（对照 Android `ChatWiring.fork` 的 `parent = store.branch(source.branchId)`）；路径 `{id}` 校验存在且与来源同主题。
  2. **空问题分叉无持久 `commandId` 载体**：schema 无分叉命令列（本任务不改 schema），故不带 `question` 的分叉无法用 `OperationStore.byCommandId` 去重；带首问的分叉经 `ChatCommandService.sendMessage` 获得持久幂等（本任务的幂等断言即针对此路径）。
  3. 可选首问在**子分支创建之后**接受：首问接受失败（如模型非法）时问题位置不落库（`acceptForBranch` 单事务回滚），但已建的子分支保留——对齐 Android「先建分支后提问」两步，契约未要求回滚分支。
  4. `ForkResult.bootstrap.rootBranch` 取分叉出的子分支（契约仅说「等价首问」，字段未明示，此处标注）。
  5. 分叉后子分支的生成上下文**未合并出生快照**（无 `SnapshotHistoryMerger` 等价物）：`ChatCommandService.assemble` 只用分支自身行；不在本任务范围。
  6. 装配位置：因 `GenerationApiConfiguration.java` 与并行任务存在写争用，`ForkCommandService` bean 落在独立 `ForkConfiguration.java`（`SnapshotStore` bean 落在 `GenerationConfiguration.java`）；`GenerationApiConfiguration.java` 保持原状未改。

## Verification report

- Verdict: PASS（独立 verifier 全过；contract-guardian 复核分叉端点与契约 §6.3 + `commands/fork-*.json` + `dtos/fork-result.json` 一致，错误码全属 §3.3）
- Evidence: 独立 verifier 实测同一命令 `:test --rerun-tasks` 237/0、`check` EXIT=0。`ForkEndpointsTest`（真 HTTP + 桩网关 + 真 SQLite）10 例覆盖：整条/选文分叉、冻结快照、anchor 四列回读、409 `OPERATION_NOT_STABLE`、400 `ANCHOR_INVALID`、`SOURCE_MESSAGE_NOT_IN_TOPIC`、**快照不回流**（真改父分支后断言子快照不变）、根分支溯源被 schema CHECK 拒绝、`commandId` 重放同结果不二次创建、可选首问成功/失败两例；复用共享域（`BranchSource`/`BranchAnchor`/`ContextSnapshot`/`Branch`）未另立判定。
- 契约澄清: 契约 §6.3 已补一句 `ForkResult.bootstrap.rootBranch` 指**新建子分支**（由 T-040 落地，guardian 判准确且仅此一处）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除/削弱既有断言；新增 10 例；既有 221 例全绿。
- Gaps: ① 空问题分叉无持久 `commandId` 载体（schema 无分叉命令列，无法用 `OperationStore.byCommandId` 去重）；② 首问失败保留子分支（对齐 Android「先建分支后提问」两步）；③ 分叉子分支生成上下文未合并出生快照（无 `SnapshotHistoryMerger` 等价物）。
- Risks: 契约未明示的 `bootstrap.rootBranch` 语义（偏差 4）已由 T-040 在 §6.3 澄清并经 guardian 确认；并行任务对共享文件的写争用曾致构建瞬时失败（已隔离，见偏差 6）。
- Sizing: 独立计数 ≈271（≤500 硬上限，>200 软警告）。

## Handoff notes

- Completed: `POST /api/branches/{id}/forks` 实现（`ForkController` + `ForkCommandService`）；`BranchStore`/`SnapshotStore` 事务作用域方法；`ForkConfiguration`/`SnapshotStore` bean；`ForkEndpointsTest` 10 例；`:test` 231/0、`:check` EXIT=0；证据三份。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: 独立 verifier PASS 与 contract-guardian 复核已完成；`status` 置 `verified`，提交由主会话决定。
- Watch-outs:
  - `GenerationApiConfiguration.java` 与并行任务（T-036）写争用：本任务已把 bean 挪到独立 `ForkConfiguration.java`，勿再把 `ForkCommandService`/`SnapshotStore` bean 加回该文件。
  - `SnapshotStore` 现为单例 bean；`BranchStore.attachContextSnapshot` 新增了 `Connection` 重载，旧单参方法语义不变。
  - 空问题分叉无持久幂等；如后续需覆盖，需要新增 schema 列（须另立任务与用户批准）。
