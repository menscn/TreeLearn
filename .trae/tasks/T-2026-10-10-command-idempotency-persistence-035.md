# Treelearn Harness Task: commandId 持久幂等（共享领域承载客户端命令身份）

id: T-2026-10-10-035
title: commandId 持久幂等（共享领域承载客户端命令身份）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 250
shared_code_touched: true
depends_on: [T-2026-10-10-core-rest-api-028.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 契约 §4「命令幂等（commandId）」要求幂等身份持久化于 `generation_operations.command_id`；现状：REST 层用进程内 `ConcurrentHashMap` 记忆客户端 commandId，服务重启后同一 commandId 重放会二次落库/二次调用模型。开发方案与数据表准入文档均明确「去重信息必须有持久化承载」「禁止进程内 Map 冒充持久幂等」。
- Users/scenarios: 客户端在结果不确定时重放同一命令（刷新、网络超时后重试）；服务重启后重放。
- Expected outcome: 客户端 `commandId` 经共享领域承载到 `generation_operations.command_id`（持久），`ChatCommandService` 以 `OperationStore.byCommandId` 先去重；同 commandId 重放返回同一 operation/结果且**不产生第二次行与第二次网关调用**（含服务重启后的重放）。
- Core flow and reversibility: 请求 → 以 commandId 查持久记录 → 命中即返回既有结果；未命中 → 正常接受并以该 commandId 落库。
- Inputs/outputs: 输入=客户端 commandId；输出=同一操作身份或新操作。
- State and exception handling: 命中→返回既有（含终态操作的重放语义：返回该操作当前状态）；未命中→接受；并存竞态→以数据库部分唯一/查询兜底（单实例 loopback，仍须正确）。
- Constraints and trade-offs: 改共享 `web/shared/domain`（`GenerationCommand` 或等价）会同时影响 Android —— **必须完成 Android 全回归**（构建 + 单测 + 依赖方向 + Java-only guard）；不得破坏 Android 既有行为；不新增依赖。
- AI recommendation and rationale: 让共享 `GenerationCommand` 接受并保留客户端 id（若既有字段语义不同，新增可选承载并在 Android 侧保持向后兼容），`OperationStore.insert` 写入该 id，`byCommandId` 查询去重。
- User choice and custom additions: 该缺口由 T-028 双复核发现并登记，属规范符合性修复。
- AI-autonomous decisions: 承载字段设计、迁移策略（无需 schema 变更——`command_id` 列已存在）。
- User-confirmation decisions: 无新增（无新增表/列）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-035-*`：双端测试、**重启后重放**证据（两次进程/两次上下文）、Android 回归证据、Java-only guard。
- Facts: `generation_operations.command_id` 列已存在（T-009）；`OperationStore.byCommandId` 已存在（T-011）；`ChatCommandService`（T-028）现为进程内 Map。
- Assumptions: Android 侧对 `GenerationCommand` 的既有用法可保持兼容（以真实编译/测试证明）。
- Unknowns: Android 是否已有客户端 commandId 概念（读源码后决定承载方式）。
- Risks: 改共享类影响 Android → 全回归兜底；重放语义（终态操作重放返回当前状态）需明确并测试。
- Deferred: 多实例幂等（本项目单实例，不适用）。
- Non-goals: 新增表/列；REST 端点扩展。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

把客户端 commandId 的幂等去重从进程内迁到持久承载，覆盖 REST 重放与重启后重放，并保持 Android 全回归通过。

## Scope

### In scope
- `web/shared/domain`：命令身份承载（最小改动，保持 Android 兼容）。
- `web/backend`：`OperationStore.insert` 写客户端 commandId；`ChatCommandService` 去重改为持久查询；`markTerminal`/重放语义（终态重放返回当前状态）。
- 测试：同 commandId 重放（同进程）→ 同一操作且网关调用计数不增；**模拟重启**（新 Spring 上下文/新实例，同一 SQLite 文件）→ 重放仍返回同一操作；Android 回归（构建+单测+依赖方向+Java-only）。

### Out of scope
- 多实例/分布式幂等；新增 schema；其它端点。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: Android 生成行为不变（共享类改动向后兼容）；Web 幂等语义增强。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Android 各 `GenerationCommand` 消费者；Web `ChatCommandService`。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 命令幂等改为持久承载（重启后重放不再二次执行）。
- Authoritative code/config/migration evidence: `web/shared/domain/**`、`web/backend/src/main/java/com/treelearn/web/generation/ChatCommandService.java`、`.../storage/OperationStore.java`。
- Documentation surfaces checked: 契约 §4；开发方案 §9/§10；`web/README.md`（T-028 登记的「已知缺口」→ 改为已闭合）。
- Documentation files updated: `web/README.md` 缺口行更新。
- Producer/consumer/sibling/compatibility paths checked: Android 全回归。
- Superseded phrases/examples reverse-searched: 「commandId 幂等仅进程内」表述。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [x] RED→GREEN（同命令 `:test --rerun-tasks`，退出码 1→0、计数 182/6failed→182/0failed）；业务行 ≈60 非空非注释（共享 43 物理行中约 11 生产行 + 后端净增约 50），≤300。
- [x] 同 commandId 重放（同进程）：同一操作身份 + 网关调用计数不增（`CommandIdIdempotencyTest` 断言 openCount）。
- [x] **重启后重放**：新实例（新 `DesktopGenerationCoordinator`，空进程内 Map，同一 SQLite 文件）对同一 commandId 返回同一操作、行数不增、网关计数不增（真实 sqlite-jdbc 证据）。
- [x] 终态操作重放语义明确且测试覆盖（FAILED 重放返回当前状态、不产生新版本、不重启生成）。
- [x] 共享类改动向后兼容：**Android 全回归通过**（`:core:test :ai:testDebugUnitTest :data:testDebugUnitTest` + `:app:assembleDebug` + `verifyDependencyDirection` PASS + `no-kotlin-check.ps1` VERDICT PASS），证据留档。
- [~] checkstyle 通过（`check` EXIT=0）；独立 verifier 与 contract-guardian 复核待办。
- [x] 未新增表/列（`DesktopSchema` 未改）。

## TDD execution record

### RED — failing test first
- Test/scenario: `CommandIdIdempotencyTest`（同进程 + 重启后 + 终态重放，4 例）。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: 182 tests / 6 failed —— 4 例新用例 + 2 例既有回放用例（`CoreApiEndToEndTest`）。失败为二次执行（第二 operation id 不同于首 operation，`BRANCH_BUSY`）。
- Failure reason validated: 命令身份未持久承载——重放查不到持久行而二次执行
- RED 复现方式（如实记录）：实现先于 RED 落笔，RED 通过把 `ChatCommandService.command(...)` 临时回退为 `GenerationCommand.create(...)`（去掉客户端 commandId 承载）复跑得到，失败确由「命令身份未承载→持久去重查不到行」导致。证据 `artifacts/evidence/T-2026-10-10-035-red.txt`。

### GREEN — minimum implementation
- Implementation: 共享 `createWithId` 承载客户端 id + `ChatCommandService` 持久去重/重放重建。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 182 PASSED / 0 failed，`BUILD SUCCESSFUL` → `artifacts/evidence/T-2026-10-10-035-green.txt`
- Regression evidence: `.\gradlew.bat check` EXIT=0（checkstyleMain/checkstyleTest 通过）→ `artifacts/evidence/T-2026-10-10-035-checkstyle.txt`；Android 全回归见下。

### REFACTOR — optional
- Cleanup: 三个接受入口统一经持久重放加载器返回，避免返回绑定前的陈旧内存位置（修正 `theCoreLoopRunsOverRealHttp` 的 `activeOperationId` 断言）。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: 无规模例外；RED/GREEN 同命令 `:test --rerun-tasks`。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 先读 Android 对 `GenerationCommand` 的既有用法，确认承载方式（新增可选字段 vs 语义扩展）。
- 重启重放测试用两个 Spring 上下文（同一 `@TempDir` 数据库文件）或 `ApplicationContextRunner`；断言行数不增、网关调用计数不增。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10）+ PASS（contract-guardian，2026-10-10）
- Evidence: verifier 自跑 `:test --rerun-tasks` 182/0、`check` EXIT=0；**Android 全回归独立复跑**：`:core:test :ai:testDebugUnitTest :data:testDebugUnitTest :app:assembleDebug verifyDependencyDirection` BUILD SUCCESSFUL（core 223 / ai 31 / data 65 全绿）、`no-kotlin-check.ps1` VERDICT PASS（13 OK）；证据 `artifacts/evidence/T-2026-10-10-035-*`。
- 重启重放判定（verifier）：成立——同一 operation 身份、行数不增、网关调用计数不增、无新版本；偏差①（未启第二 Spring 上下文）不影响结论（`ChatCommandService` 已无 commandId 进程内 Map）。
- 兼容性（verifier + guardian）：`create` 未改、`createWithId` 纯增量；Android 全部调用点仍用 `create`；`:core` 即 `web/shared/domain`（同源、无副本、无 Kotlin）。
- **T-028 的 §4 缺口闭合判定（guardian）**：持久身份、同/跨重启/终态重放、不二次落库/调用均以真实 SQLite 证据成立 → **已闭合**。
- RED 诚实记录：实现先于 RED 落笔，RED 以临时回退复现（verifier 确认真实失败原因）——流程偏差，非例外申请。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+4 用例（178→182）。
- 规模：共享 ≈19 生产行 + 后端 ≈50 行 ≈ 70（≤300）。
- Gaps（非阻断）：①「按 commandId 直查」端点/文档澄清缺失（客户端丢响应且无 operationId/branchId 时）→ 登记 T-028b；② sendMessage/retry 终态重放缺专项用例；③ 非法 commandId → 400 有实现无测试（建议补一例）。
- Risks: 无表/列变更；`byCommandId` 返回首行语义在单实例 loopback 下成立。

## Handoff notes

- Completed: 持久幂等落地 + 双守卫 PASS（2026-10-10）；182 项后端测试全绿；Android 全回归通过；`web/README.md` 缺口行已更新为「已闭合（T-035）」。
- In progress: commit 由主会话执行（与 T-028 连续提交）。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-029（发行打包）；T-028b（其余端点 + 上述 Gaps ①②③）。
- Watch-outs: `create` 不得改语义；`generation_operations.command_id` 现承载客户端 id；新增端点需白名单校验；重启口径以「新实例+空进程内状态+同一 SQLite」表达。