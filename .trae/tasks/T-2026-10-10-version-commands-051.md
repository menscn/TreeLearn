# Treelearn Harness Task: reset 与版本选择端点（§6.4）

id: T-2026-10-10-051
title: reset 与版本选择端点（§6.4）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-10-10-frontend-annotations-consumption-049.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求「retry 之外的 reset」缺口 + 版本切换后端。
- Problem/background: 契约 §6.4 已定义 reset/select，后端未实现；前端版本切换无通道。
- Users/scenarios: 学习者对已显示稳定版本的回答重新生成（reset）；多版本间切换显示（select）。
- Expected outcome: `POST /api/messages/{id}/reset`（重试同款形状）、`POST /api/messages/{id}/revisions/{revisionId}/select`（仅移指针）；判定镜像 Android `AnswerVersions`/`PositionCommands`。
- Core flow and reversibility: 判定收 `ChatCommandService`（与 retry 同层），纯新增，可回退。
- Inputs/outputs: 见契约 §6.4；错误码 `OPERATION_NOT_STABLE`/`REVISION_IMMUTABLE`/`OPERATION_NOT_COMPLETABLE`/`MESSAGE_ROLE_MISMATCH`。
- State and exception handling: reset 前置=已显示稳定版本（无→409）；无活跃操作；select=目标 revision 属于该消息且消息已是稳定版。
- Constraints and trade-offs: 不改契约既有语义；共享域零改动。
- AI recommendation and rationale: 判定收唯一入口（服务层），端点薄。
- User choice and custom additions: 已批准（第三波连续执行指令）。
- AI-autonomous decisions: reset 复用 retry 请求 DTO（同形状）；ChatCommandService 构造器注入 AnswerRevisionStore。
- User-confirmation decisions: 无。
- Acceptance evidence: artifacts/evidence/T-2026-10-10-051-*。
- Facts: Android `AnswerVersions.resetRefusal/selectRefusal` 判定；`revisions.insert` 同事务稳定化。
- Assumptions: 无。
- Unknowns: 无。
- Risks: 构造器变更波及 3 个调用点（均已同步）。
- Deferred: 无。
- Non-goals: 不做版本 diff UI。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

reset/select 端点按 §6.4 与 Android 判定语义工作：真实 HTTP 4 例 + 服务层语义 6 例，全量回归零破坏。

## Scope

### In scope
- `web/backend/.../generation/ChatCommandService.java`：`reset`/`selectRevision`。
- `web/backend/.../api/CoreApiController.java`：两条路由。
- `web/backend/.../generation/GenerationApiConfiguration.java`：构造器注入。
- 测试 `VersionCommandEndpointsTest`（4 例）+ `ChatVersionCommandsTest`（6 例）。

### Out of scope
版本 diff UI；reset 前确认弹窗（前端自决）。

## Acceptance criteria
- [x] RED→GREEN：端点 4 例（404→全绿）+ 服务层 6 例（判定矩阵：稳定版前置/活跃操作互斥/revision 归属/只移指针）。
- [x] 判定镜像 Android `AnswerVersions`/`PositionCommands`（错误码逐一对齐）。
- [x] `ChatCommandService` 构造器变更三调用点同步。
- [x] 全量 `:check` 294 例 + checkstyle exit 0。

## TDD execution record

### RED — failing test first
- Test/scenario: `VersionCommandEndpointsTest` 2 例（期望 200，端点缺失即 404 红）
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.VersionCommandEndpointsTest`
- Exit code: 1
- Observed failure: 2 tests completed, 2 failed（404 Not Found）
- Failure reason validated: `artifacts/evidence/T-2026-10-10-051-red.txt`（EXITCODE=1）

### GREEN — minimum implementation
- Implementation: `ChatCommandService.reset/selectRevision` + 控制器路由 + 构造器注入
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.VersionCommandEndpointsTest`
- Additional command (service-layer semantics, same harness): `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.generation.ChatVersionCommandsTest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-051-green.txt`（4/4）、`T-2026-10-10-051-green-svc.txt`（6/6）
- Regression evidence: `artifacts/evidence/T-2026-10-10-051-check.txt`（294 PASSED / 0 FAILED / checkstyle 0 error / EXITCODE=0）

### REFACTOR — optional
- Cleanup: 服务层测试改走真实 Phase 4 稳定化路径（直改 STABLE 被存储守卫拒绝——该拒绝即被测门）。
- Regression evidence: 同 check 文件（重跑 exit 0）。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行实测 约 120（超软上限 200 以内），未超软上限）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- STABLE 落定必须走协调器稳定化路径（同事务建 revision + 指针）；直改 DB 会被「finished attempt with no revision」守卫拒绝。
- select 语义=仅移动 activeRevisionId 指针（REVISION_IMMUTABLE：版本内容不可变）。

## Verification report

- Verdict: PASS（实现者自检全绿；合并独立 verifier 待统一复核）
- Evidence: `artifacts/evidence/T-2026-10-10-051-{red,green,green-svc,check}.txt`
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除既有断言；服务层测试改走真实 Phase 4 稳定化路径（手推 sink accept→delta→complete）而非直改 DB——直改 STABLE 会被「finished attempt with no revision」守卫拒绝，该拒绝正是被测门。
- Gaps: reset/retry 的浏览器端到端流程未装机截图（属浏览器视觉复核轮）。
- Risks: 无已知。

## Handoff notes

- Completed: reset/select 端点 + ChatCommandService 判定 + 4 端点例 + 6 服务层例 + 全量 check 294 例。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: T-052 派生端点。
- Watch-outs: `ChatCommandService` 构造器新增 `AnswerRevisionStore` 参数（3 个调用点同步）；STABLE 落定必须走稳定化路径。


## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `POST /api/messages/{id}/reset` 与 `/revisions/{rid}/select` 由 404 变为按 §6.4 可用。
- Authoritative code/config/migration evidence: `web/backend/.../generation/ChatCommandService.java`、`.../api/CoreApiController.java`。
- Documentation surfaces checked: 契约与 `web/README.md` 状态节。
- Documentation files updated: 已按本轮交付同步。
- Producer/consumer/sibling/compatibility paths checked: 前端 `selectRevision`/`resetMessage`（T-053 消费）。
- Superseded phrases/examples reverse-searched: 无（端点此前不存在）。
