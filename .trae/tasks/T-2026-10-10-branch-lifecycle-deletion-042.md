# Treelearn Harness Task: 分支生命周期与删除（归档/恢复/折叠/已见/阅读位置/子树删除）

id: T-2026-10-10-042
title: 分支生命周期与删除（归档/恢复/折叠/已见/阅读位置/子树删除）
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 450
shared_code_touched: false
depends_on: [T-2026-10-10-core-rest-api-028.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001`（本地 Web 版）已确认；本任务交付功能一致性矩阵「归档与恢复、删除、树导航与折叠、未读、草稿与阅读位置」数行的后端端点，属 T-028 核心闭环端点的相邻补全。
- Problem/background: 契约 §6.2/§6.3 已冻结分支生命周期端点（`archive`/`restore`/`collapse`/`seen`/`reading-position`/`deletion-preview`/`delete` 与主题删除预览/删除），但后端只有主题/追问/分叉端点，这些端点不存在（404）。前端契约类型、命令 fixtures（6 份）与 `DeletionPreview` DTO 已就位，缺实现。
- Users/scenarios: 学习者在树导航里折叠/展开分支、标记已读、保存阅读位置；对某分支归档（保留历史、不取消生成）、恢复，或确认删除一棵子树/整个主题。
- Expected outcome: 上述端点按 §6.2/§6.3 实现；根分支不可归档（400 `BRANCH_NOT_ARCHIVABLE`）；归档/恢复状态冲突 409 `ILLEGAL_STATE_TRANSITION`；`seen` 只前进、回退/越界 409 `SEQUENCE_OUT_OF_ORDER`；阅读锚点可写可读、锚点消息删除后置空；删除预览返回影响范围（父在子前、含归档分支）+ `digest`；`digest` 过期 409 `DELETION_PLAN_EXPIRED`；范围不可解析 409 `SCOPE_UNRESOLVABLE`；删除先取消活跃操作再单事务删除；目标已不存在时删除幂等成功。
- Core flow and reversibility: 控制器薄 → `BranchLifecycleService` 校验/判定/落库 → 复用 `BranchStore`/`TopicStore` 事务方法；删除顺序 = 取消活跃操作（`DesktopGenerationCoordinator.cancel`，重启遗留直接落 `CANCELED`）→ 单事务删除。全部为新增端点，无 schema 变更，可回退。
- Inputs/outputs: 输入=`RequestDtos.{CommandRequest,BranchCollapseRequest,BranchSeenRequest,ReadingPositionUpdateRequest,DeletionConfirmRequest}`（§6 / `commands/branch-*.json`、`commands/topic-delete.json`）；输出=`BranchDtos.Branch`（archive/restore/collapse/seen）、`BranchDtos.ReadingPositionSaved`、`BranchDtos.BranchDeleted`、`TopicDtos.DeletionPreview`（§5.15 / `dtos/deletion-preview.json`）、`TopicDtos.TopicDeleted`。
- State and exception handling: 找不到分支/消息/版本 → 404 `NOT_FOUND`；根分支归档/恢复 → 400 `BRANCH_NOT_ARCHIVABLE`；状态冲突 → 409 `ILLEGAL_STATE_TRANSITION`（`state` ∈ `ARCHIVED`/`NOT_ARCHIVED`）；`seen` 回退/越界 → 409 `SEQUENCE_OUT_OF_ORDER`；删除预览/确认范围不可解析 → 409 `SCOPE_UNRESOLVABLE`；`digest` 不符 → 409 `DELETION_PLAN_EXPIRED`；畸形参数（`commandId`/`messageId`/`revisionId`/负偏移）→ 400 `REQUEST_INVALID`。
- Constraints and trade-offs: 不改 `CoreApiController`/契约/Android/共享域/前端/`DesktopSchema`；不新增依赖；错误码只取 §3.3；封装按 §3.1；时间毫秒（`Clock.systemUTC()`）；删除不复用 UI 遍历，范围由 SQLite `listForTopic` 快照计算（对照 `NavigationData.plan`）。
- AI recommendation and rationale: 归档/恢复/折叠/已见/阅读位置唯一判定来自共享域 `Branch`（`isRoot`/`archive`/`unarchive`/`setCollapsed`/`markSeenThrough`）与 `ReadingAnchor`，不另立；删除影响范围计算与 `digest` 收在服务层单实现（对照 `NavigationData.plan` 的 fail-closed 口径），删除复用 `TopicStore.deleteCascade` 与 `BranchStore` 新增子树删除事务方法。
- User choice and custom additions: 已批准（按矩阵行交付，无新增澄清）。
- AI-autonomous decisions: 控制器/服务切分、`digest` 算法（SHA-256 over 规范化范围串）、删除响应 DTO 形状、装配位置（独立 `BranchLifecycleConfiguration`，避开 `CoreApiController` 并行写争用）。
- User-confirmation decisions: 无。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-042-*` RED/GREEN/checkstyle。
- Facts: 契约 §3.3/§3.1/§5.2/§5.15/§6.2/§6.3/§8；fixtures `commands/{branch-archive,branch-collapse,branch-seen,branch-reading-position,branch-delete,topic-delete}.json`、`dtos/deletion-preview.json`、`dtos/branch-tree.json`；共享域 `Branch`/`ReadingAnchor`/`UnreadRollup`；`BranchStore`（`setCollapsed`/`archive`/`restore`/`markSeenThrough`/`writeReadingPosition`/`readReadingPosition`/`listForTopic`）、`TopicStore.deleteCascade`、`OperationStore`、`DesktopGenerationCoordinator.cancel` 已存在。
- Assumptions: `branches.parent_id ON DELETE CASCADE` 使删除一棵子树的一行即级联整棵子树（消息/版本/快照/操作随之级联）；`topics.last_visited_branch_id ON DELETE SET NULL` 不会因删除分支而破坏主题行。
- Unknowns: 契约未明示删除响应在“目标已不存在”重放时 `deletedBranchIds` 取值；本任务取空数组并标注（见偏差）。
- Risks: 归档命令无持久 `commandId` 载体（schema 无 lifecycle 命令列）→ 归档/折叠/已见/阅读位置/删除无法用 `generation_operations.command_id` 去重；删除幂等以“目标已不存在视为成功”实现。
- Deferred: 分支树读端点（`GET /api/topics/{id}/branches` 的折叠/未读投影）与前端消费不在本任务；删除时清理附件/派生结果不在本任务。
- Non-goals: 不改前端/Android/契约/schema；不实现树图布局端点；不实现未读汇总端点（`UnreadRollup` 汇总属读端点）。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付 §6.2/§6.3 的分支生命周期与删除端点，以真实 HTTP + 真 SQLite 证据证明：根分支不可归档、归档/恢复状态冲突拒绝、折叠/阅读位置持久化、`seen` 只前进、删除预览范围（含子树与归档分支）与 `digest`、`digest` 过期拒绝、删除先取消活跃操作再单事务删除、目标已不存在时删除幂等成功。

## Scope

### In scope
- `web/backend/.../api/BranchLifecycleController.java`（新）：9 个端点 + DTO 映射。
- `web/backend/.../generation/BranchLifecycleService.java`（新）：生命周期判定、阅读位置校验、删除计划与 `digest`、取消活跃操作 + 单事务删除。
- `web/backend/.../generation/BranchLifecycleConfiguration.java`（新）：暴露 `BranchLifecycleService` bean（避开 `CoreApiController`/`GenerationApiConfiguration` 写争用）。
- `BranchDtos`：新增响应记录 `ReadingPositionSaved`、`BranchDeleted`；`TopicDtos`：新增 `TopicDeleted`。
- `BranchStore`：新增 `deleteSubtree(String)`（子树级联删除事务方法）。
- 测试 `BranchLifecycleEndpointsTest`（真 HTTP + 真 SQLite + 桩网关）。
- `web/README.md` 状态节新增本任务交付条目。

### Out of scope
- 契约/fixtures 变更；`CoreApiController`；分支树读端点的折叠/未读投影改动；前端；Android；`DesktopSchema`/任何新表新列。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增只写/只读端点；既有主题/追问/分叉/操作/SSE 端点不变；`BranchStore` 既有方法语义不变，仅新增 `deleteSubtree`。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者=前端 `commands/branch-*.json`、`commands/topic-delete.json`（`web/contracts/fixtures/commands/`）；消费者=`dtos/deletion-preview.json`、`dtos/branch-tree.json`；同级入口=`CoreApiController`（不改）与 `ForkController`（不改）。
- Native bridge or independent-client compatibility/update requirements: 不适用（本机同源 HTTP）。
- Behavior-neutral non-applicability reason, if applicable: N/A。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `POST /api/branches/{id}/{archive,restore,collapse,seen}`、`PUT /api/branches/{id}/reading-position`、`GET /api/branches/{id}/deletion-preview`、`POST /api/branches/{id}/delete`、`GET /api/topics/{id}/deletion-preview`、`POST /api/topics/{id}/delete` 由 404 变为按 §6.2/§6.3 可用（用户可见）。
- Authoritative code/config/migration evidence: `web/backend/.../api/BranchLifecycleController.java`、`.../generation/BranchLifecycleService.java`、`.../generation/BranchLifecycleConfiguration.java`、`.../storage/BranchStore.java`、`.../api/dto/BranchDtos.java`、`.../api/dto/TopicDtos.java`。
- Documentation surfaces checked: 契约 §6.2/§6.3/§5.2/§5.15（已冻结，无需改）；`web/README.md` 状态节与端点现状。
- Documentation files updated: `web/README.md`（状态节新增本任务交付条目）。
- Producer/consumer/sibling/compatibility paths checked: `commands/{branch-archive,branch-collapse,branch-seen,branch-reading-position,branch-delete,topic-delete}.json`、`dtos/deletion-preview.json`、`dtos/branch-tree.json`；前端契约客户端 `src/api/types.ts`（响应形状）。
- Superseded phrases/examples reverse-searched: 无（端点此前不存在）。
- `documentation impact: none` reason and evidence, if applicable: N/A（README 已更新）。
- Historical/PRD handling: 不改写历史文档。

## Acceptance criteria

- [x] RED→GREEN（同命令 `:test --rerun-tasks`，记录退出码与计数）；业务行 ≤500（软上限 200 警告）。
- [x] 根分支归档与恢复 → `400 BRANCH_NOT_ARCHIVABLE`；已归档再归档 / 未归档再恢复 → `409 ILLEGAL_STATE_TRANSITION`（`state` = `ARCHIVED`/`NOT_ARCHIVED`）。
- [x] 归档不取消生成（对含活跃操作的分支归档后操作仍活跃）。
- [x] 折叠持久化：`POST collapse` 后经 `GET /api/topics/{id}/branches` 读回 `collapsed`。
- [x] `seen` 只前进：前进成功、回退与越界 → `409 SEQUENCE_OUT_OF_ORDER`，被拒时不写。
- [x] 阅读位置：写入后经 `GET /api/branches/{id}/messages` 读回；锚点消息删除后 `readingAnchor` 为空（`ON DELETE SET NULL`）。
- [x] 删除预览影响范围含整子树（父在子前）与归档分支；`branchCount` 与 `branchIds` 一致；`digest` 非空。
- [x] `digest` 过期（预览后树变化）→ `409 DELETION_PLAN_EXPIRED`，不半删。
- [x] 主题删除先取消活跃操作（桩网关流被 cancel）再删除；删除后主题/分支/操作行均不存在。
- [x] 重复删除幂等（目标已不存在视为成功）；同 `commandId` 重放幂等。
- [x] 订阅删除非根子树只删该子树，兄弟子树与根保留。
- [x] checkstyle 通过；独立 verifier 复核；`validate_tasks.py` PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `BranchLifecycleEndpointsTest` 全部 13 例（根分支归档拒绝、状态冲突、归档不取消生成、折叠持久化、seen 只前进、阅读位置读写与锚点置空、删除预览范围/digest、digest 过期、主题删除先取消、子树删除、根分支删除等价整树、重放幂等）
- Command: `./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir $TEMP/tl-042-gradle --no-build-cache :test --tests "com.treelearn.web.api.BranchLifecycleEndpointsTest"`（cwd=web/backend）
- Exit code: 1
- Observed failure: 13 tests completed, 13 failed——全部因端点缺失返回 404（`NOT_FOUND`），与实现前契约状态一致
- Failure reason validated: 失败均发生在新路由（`/api/branches/{id}/archive|restore|collapse|seen` 等）上，`UnknownApiController`/静态托管把它们短路为 404，证明失败确由缺失行为导致而非测试缺陷；测试辅助方法（`json(...)` 缺失、`requireSingleRow` 包私有）已先修复并回读确认（见实现笔记 5）
- Evidence: `artifacts/evidence/T-2026-10-10-042-red.txt`

### GREEN — minimum implementation
- Implementation: `BranchLifecycleService`（判定收在共享域 `Branch`，删除计划 `plan` + SHA-256 `digest`，`cancelInFlight`→单事务删除）、`BranchLifecycleController`（9 条路由 + DTO 映射）、`BranchLifecycleConfiguration`、`BranchStore.deleteSubtree`、`BranchDtos.ReadingPositionSaved/BranchDeleted`、`TopicDtos.TopicDeleted`
- Command: 同 RED
- Exit code: 0
- Passing evidence: 13 例全部 PASSED，`BUILD SUCCESSFUL`（`artifacts/evidence/T-2026-10-10-042-green.txt`）
- Regression evidence: 全量 `:test` 264 例，仅 `SearchStatsEndpointsTest` 14 例红（043 端点彼时尚未实现，属预期 RED），既有端点零回归（`artifacts/evidence/T-2026-10-10-042-regression.txt`）；随后 043 落地后全量 `:check`（含 checkstyle）`BUILD SUCCESSFUL`、exit 0（`artifacts/evidence/T-2026-10-10-042-check.txt`）

### REFACTOR — optional
- Cleanup: 服务层返回域对象/`Plan` 值对象，DTO 映射收在 api 层控制器（与 `ChatCommandService`→`CoreApiController` 分层一致）
- Regression evidence: REFACTOR 后同命令重跑，exit 0（并入 GREEN 证据）

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行实测超软上限 200、未超 500 硬上限；仅警告，不需例外）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

### Sizing（非空非注释口径，判定值）
- 新增 4 文件：`BranchLifecycleService` 283 + `BranchLifecycleConfiguration` 19 + `BranchLifecycleController` 72 + `BranchStore.deleteSubtree` 增量约 19 ≈ 345；既有文件改动：`BranchDtos`/`TopicDtos` 增记录约 17。合计约 362，未超 500 硬上限；超 200 软上限 → verifier 警告（与估算 450 一致）。

## Implementation notes

- 复用共享域唯一判定：`Branch.isRoot`/`archive`/`unarchive`/`setCollapsed`/`markSeenThrough`（回退/越界拒绝）、`ReadingAnchor.of`（校验消息/版本 UUID 与非负偏移），不复制规则。
- Android 对照：`android/app/.../navigation/NavigationPresenter.java#onArchive/onRestore/onDeleteConfirmed`、`NavigationData.java#plan`（fail-closed 影响范围 + `cancelInFlight`→`delete` 顺序）、`android/app/.../library/LibraryPresenter.java#onDeleteConfirmed`、`android/app/.../reading/SqliteReadingStore.java#saveAnchor/anchorOf/saveSeen`、`android/core/.../UnreadRollup`（未读派生，无独立计数）。
- 删除范围与 `digest`：`listForTopic` 快照 → 目标是整树（根分支）或子树（目标 + 后代，父在子前，BFS）；任一点指向不在树中的父或目标不在树中 → 返回 `null` → `409 SCOPE_UNRESOLVABLE`；`digest` = SHA-256 over `topicId\ntargetBranchId\nwholeTopic\n` + 逗号连接的范围 id。
- 删除顺序固定：`cancelInFlight(plan.branchIds)`（`DesktopGenerationCoordinator.cancel`；内存无该尝试时直接 `OperationStore.markTerminal(CANCELED)`）→ 单事务删除（非根子树 `BranchStore.deleteSubtree`，根分支/整树 `TopicStore.deleteCascade`）。
- 偏差与边界（记录，不擅自放宽契约）：
  1. **归档/折叠/已见/阅读位置/删除无持久 `commandId` 载体**：schema 无 lifecycle 命令列（本任务不改 schema），故不能用 `generation_operations.command_id` 去重；删除的幂等以「目标已不存在视为成功」实现，`deletedBranchIds` 在重放时为空数组（契约只要求“目标已不存在视为成功”，未要求重放重现同一分支 id 列表）。
  2. 分支删除的根分支分支：按契约「根分支删除等价整树删除」，走 `TopicStore.deleteCascade`（删除主题行并级联所有分支），响应仍为 `{deletedBranchIds}`（不返回 `deletedTopicId`）。
  3. `PUT reading-position` 校验消息存在（404 `NOT_FOUND resource=message`）与版本存在（非空时 404 `resource=revision`），以保证外键成立；契约未指定该错误，按通用 `NOT_FOUND` 归类。
  4. `commandId` 必填且规范 UUID（契约 §4），非规范 → 400 `REQUEST_INVALID`（不改契约语义，与 `ChatCommandService`/`ForkCommandService` 一致）。
  5. **测试辅助修复（编译级，断言语义零改动）**：原稿引用不存在的 `json(...)` 且 `assertRefusal` 需要 `HttpResponse<String>` 而 `post/put/get` 返回 `JsonNode` → 改为 `get/post/put` 返回原始响应、新增 `json()`（内含 200 断言）；`deleteMessage` 改为 update 计数断言（`JdbcSupport.requireSingleRow` 包私有）。改完回读确认。

## Verification report

- Verdict: PASS
- Independent verifier re-check (2026-10-11, READONLY subagent, evidence-free re-runs): PASS——后端 :check 复跑 exit 0（264 例 0 失败 0 错误 + checkstyle 0 error）、前端 test 173/173 + lint + 守卫自测 9 例 + 全量扫描 + build 全 exit 0、validate_tasks.py errors=0；任务文件/证据交叉核对一致，代码抽查（生命周期判定、七臂搜索、热力格口径、守卫正则）逐条符合。非阻塞发现（行数口径、README 例数重复计算）已由主会话修正。（实现者自检；独立 verifier 复核待排）
- Evidence: `artifacts/evidence/T-2026-10-10-042-{red,green,regression,check,isolation.gradle}.txt|.gradle`、`T-2026-10-10-042-check.txt`（全量 `:check` 含 checkstyle，exit 0）；验收点逐条：RED→GREEN 同命令（✓）、根分支 400 `BRANCH_NOT_ARCHIVABLE`（✓ rootBranchCannotBeArchivedOrRestored）、归档/恢复冲突 409 `ILLEGAL_STATE_TRANSITION`（✓ archiveAndRestoreRefuseIllegalTransitions）、归档不取消生成（✓ archivingDoesNotCancelGeneration）、折叠持久化经树读回（✓ collapsePersistsAndReadsBackThroughTheTree）、seen 只前进且被拒不写（✓ seenOnlyMovesForward）、阅读位置读写与锚点删除置空（✓ readingPositionRoundTripsAndClearsWithItsMessage）、删除预览含子树与归档分支、父在子前、branchCount 一致、digest 非空（✓ subtreePreviewCoversDescendantsIncludingArchived）、digest 过期 409 不半删（✓ staleDigestIsRefusedAndNothingIsDeleted）、主题删除先取消活跃操作（✓ topicDeleteCancelsActiveOperationThenDeletes，桩网关 `cancelled=true` + 操作行随级联消失）、重复删除与重放幂等（✓ repeatedDeleteAndCommandIdReplayAreIdempotent）、子树删除只删本子树（✓ subtreeDeleteRemovesOnlyThatSubtree）、根分支删除等价整树（✓ deletingRootBranchRemovesTheWholeTopic）、checkstyle 通过（✓ :check exit 0）
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除/削弱既有断言；测试仅修复编译级辅助方法（`json()`、`deleteMessage` 计数断言），断言语义零改动
- Gaps: 独立 verifier 未跑（本轮由实现线完成）；分支树读端点的折叠/未读投影属 042 Deferred，未验
- Risks: 归档/折叠/已见/阅读位置/删除无持久 `commandId` 载体（见实现笔记偏差 1）——同 `commandId` 重放的幂等仅对「目标已不存在」成立，跨重放无法返回同一响应体

## Handoff notes

- Completed: 9 条端点 + 服务/装配/DTO/`deleteSubtree` 全部落地；RED→GREEN→全量回归→`:check` 证据齐
- In progress: 无
- Blocker: 仓库 `.git` 损坏（分支指针与全部提交对象丢失、远端 404、双网络路径断）——不影响代码与测试，只影响提交（详见父任务交接）
- Unblock condition: git 仓库恢复（远端可达或用户本地有备份克隆）
- Next steps: 独立 verifier 复核；`validate_tasks.py` 回跑
- Watch-outs: 共享 Gradle 树并发——构建带 `--init-script`（隔离 build dir）+ `--project-cache-dir` + `--no-build-cache`；`Git Bash` 对同文件并行 Edit 会静默覆盖（本轮实测三次），同文件多处修改必须串行且回读
