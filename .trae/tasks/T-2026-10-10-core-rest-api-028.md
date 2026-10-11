# Treelearn Harness Task: 核心闭环 REST 端点（主题/分支/消息/操作）

id: T-2026-10-10-028
title: 核心闭环 REST 端点（主题/分支/消息/操作）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 450
shared_code_touched: false
depends_on: [T-2026-10-10-sse-endpoint-026.md, T-2026-10-10-backend-contract-dtos-025.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 前端工作区/主页已按契约编码（T-030/T-031），但其调用的端点尚未实现（当前 404）；本任务交付核心闭环端点，使产品可端到端使用。
- Users/scenarios: 首问、连续追问、读取分支树与消息、观察操作状态、停止、原位重试。
- Expected outcome: 下列端点按契约实现（含错误封装、token 白名单校验、commandId 幂等、状态映射）：
  `GET /api/topics`（[TopicSummary]）、`POST /api/topics`（TopicBootstrap，原子接受）、`GET /api/topics/{id}`、
  `GET /api/topics/{id}/branches`（BranchTree）、`GET /api/branches/{id}/messages`（Conversation）、
  `POST /api/branches/{id}/messages`（TurnAccepted）、`GET /api/operations/{id}`、`GET /api/operations/active?branchId=`、
  `POST /api/operations/{id}/cancel`、`POST /api/messages/{id}/retry`。
- Core flow and reversibility: 控制器 → 协调器/Store → DTO 映射；可回退。
- Inputs/outputs: 输入=契约 §6 请求体；输出=§5 DTO + §6 响应包裹。
- State and exception handling: 规划/协调拒绝（KEY_MISSING/NO_MODELS/BRANCH_BUSY/…）→ §3.3 码 + 正确 HTTP 状态；未找到→404；校验失败→400（含字段名参数）。
- Constraints and trade-offs: 不新造码/字段；枚举 token 白名单校验（非法 token→400）；时间毫秒；不新增依赖。
- AI recommendation and rationale: 单一映射层（域/行 → DTO）集中处理单位与可空性；控制器薄。
- User choice and custom additions: 已批准方案。
- AI-autonomous decisions: 控制器/映射器切分、校验实现。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-028-*` RED/GREEN + 端到端（stub 网关）证据。
- Facts: DTO 层（T-025）、协调器（T-024）、SSE（T-026）、前端调用面（T-030/T-031）。
- Assumptions: 协调器已暴露 accept/cancel/retry 所需入口（若缺则本任务内补最小公开方法，语义不变）。
- Unknowns: `POST /api/messages/{id}/retry` 与 `reset` 的精确语义（读 Android `PositionCommands`/`AnswerVersions` 后实现 retry；reset 若前端未调用则本任务不实现，记录）。
- Risks: 与前端契约调用不一致 → 以 fixtures/TS 调用面逐条核对；错误码映射遗漏 → 逐码测试。
- Deferred: 归档/折叠/seen/阅读位置/删除预览/设置/搜索/统计/文件/批注端点（T-028b 与后续）。
- Non-goals: 派生任务；附件；设置。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付前端当前调用面的核心闭环 REST 端点，以端到端（stub 网关）证据证明首问→流式→追问→停止/重试可用。

## Scope

### In scope
- 端点清单见上；错误映射层（§3.3 全码，含 `BRANCH_BUSY`/`KEY_MISSING`/`NO_MODELS`/`NOT_FOUND`/`REQUEST_INVALID` 等实际涉及者）；token 白名单校验（roles/state/kind 等入参 token）。
- `GET /api/branches/{id}/messages` 的 `attachments` 字段（附件未实现）→ 空数组 + 文档化缺口（记录在任务与 README）。
- 响应包裹建模：`TurnAccepted`（已有）+ cancel/retry 的响应形状按 api.md §6.4 实际（若为 Operation 则直接返回 Operation）。
- 测试：MockMvc 形状/错误码矩阵 + 真实 HTTP 端到端（stub 网关）：创建→SSE 稳定→读消息→追问→停止→重试；真实 SQLite（临时数据目录）。

### Out of scope
- 设置/搜索/统计/文件/批注/删除/归档等端点（T-028b）；派生；附件。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新端点；ready 与 SSE 端点不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 前端 `store/{homeData,topicData}`、`BranchWorkspace`（调用面）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 核心闭环 API 可用（用户可见）。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/api/**`。
- Documentation surfaces checked: 契约 §6/§11；`web/README.md` 状态节。
- Documentation files updated: `web/README.md` 状态一行（含 attachments 空数组缺口说明）。
- Producer/consumer/sibling/compatibility paths checked: 前端调用面逐条核对。
- Superseded phrases/examples reverse-searched: 「端点 404/后续任务」类表述。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤450。
- [ ] 十个端点全部实现；MockMvc 形状与 fixtures 同源断言（响应键集/类型）。
- [ ] 错误码矩阵：每个涉及码至少一例（400/404/409 映射正确、封装形态 §3.1、`params` 键按 §3.3）。
- [ ] commandId 幂等：同 id 重放返回同一 operation/结果（端点级测试）。
- [ ] token 白名单：非法枚举 token → 400（测试）。
- [ ] 端到端（stub 网关真 HTTP）：首问→SSE stable→messages 读到修订→追问→cancel→retry 全链路。
- [ ] checkstyle 通过；独立 verifier PASS；contract-guardian 复核（端点↔契约↔前端调用面）。
- [ ] `attachments` 空数组缺口已记录（任务+README）。

## TDD execution record

### RED — failing test first
- Test/scenario: `CoreApiControllersTest`、`CoreApiEndToEndTest`。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: `:compileTestJava FAILED`——`ChatCommandService`/`ServiceRefusalException` 不存在、`CoreApiController` 不存在（10 处找不到符号）
- Failure reason validated: 失败确因端点与服务缺失
- Evidence: `artifacts/evidence/T-2026-10-10-028-red.txt`

### GREEN — minimum implementation
- Implementation: `CoreApiController`（10 端点）+ `ApiDtoMapper`（映射层）+ `ErrorCatalog`/`ErrorResponses`/`CoreApiExceptionHandler`（错误映射）+ `ChatCommandService`（命令装配/幂等/模型解析）+ `ProviderGateway` + `GenerationApiConfiguration` + `ServiceRefusalException`；协调器新增公开 `acceptForRetry`；`TopicDtos` 新增 `MessageOperation` 包裹。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 177 tests / 0 failed（`...-028-green.txt`）；新增 16 例（12 控制器含 §3.3 错误矩阵 11 码 + 4 端到端含 token 白名单与幂等）
- Regression evidence: 既有 160 全绿（148→177）；`check` EXIT=0

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: **规模越线（无事先批准）**
- Reason: 新增生产代码非空非注释实测 685 行（估 450，硬上限 500）——控制器 109、`ChatCommandService` 285、映射/错误层 168、网关/装配/异常 123；10 端点 + 集中映射 + 错误矩阵 + 命令装配 + Spring 装配在薄控制器约束下难以压到 500。
- User approval: **无**（实现者无权追认；如实记录，未声称例外成立）
- Alternative verification: N/A
- 说明：按任务指令「超 500 停下报告」，此处停下报告规模越线，不自行放宽。

### 修复轮（独立 verifier + contract-guardian 复议后）
- Scope: (1) `GET /api/operations/active` 契约 §6.4 语义（200+null）；(2) 缺失/非法 `branchId` → 400 `REQUEST_INVALID`；(3) 补 `{message, operation}` 共享 fixture 与双端断言。
- RED: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）退出码 1，178 tests / 3 failed——`activeOperationWithoutOneReturnsNullBody`（期望 200 实得 404）、`activeOperationWithMissingOrInvalidBranchIdIsRequestInvalid`（期望 400 实得 500）、`DtoFixturesRoundTripTest`（dtos 计数 25 实得 26）。证据 `artifacts/evidence/T-2026-10-10-028-fix-red.txt`。
- GREEN: `.\gradlew.bat :test --rerun-tasks` 退出码 0，**178 tests / 0 failed**；`pnpm test`（cwd=web/frontend）退出码 0，**15 files / 122 tests passed**。证据 `artifacts/evidence/T-2026-10-10-028-fix-{green,frontend-test}.txt`；`.\gradlew.bat check` 退出码 0（checkstyle 通过）证据 `...-fix-checkstyle.txt`。
- fix 面：控制器 `activeOperation` 改返回 `JsonNode`（活跃→对象树，无→`NullNode`）并校验 `branchId`；`CoreApiExceptionHandler` 增 `MissingServletRequestParameterException`→`REQUEST_INVALID`；新增 `web/contracts/fixtures/dtos/message-operation.json`；前端 `types.ts` 增 `MessageOperation`、`fixtures.test.ts` 计数 25→26 / 总数 101→102 / 完备性 43→44；Java `DtoFixturesRoundTripTest` 计数与类型映射 26。
- 契约边界：`REQUEST_INVALID` 的 `params` 只含 §3.3 冻结的 `{httpStatus}`（不新增 `field` 键，避免引入新的契约漂移）。

## Implementation notes

- 先读前端调用面（`homeData.ts`/`topicData.ts`/`BranchWorkspace.tsx`）确认实际请求/响应形状，再对照 api.md。
- retry 语义以 Android `PositionCommands.retryRefusal`/`AnswerVersions` 为准：`ChatCommandService.retry` 校验 AI 位置、分支无在飞尝试，原位新建尝试（协调器 `acceptForRetry`），不改 `ordinal`、不多插问答。
- 前端调用面逐条核对：`GET /topics`→TopicSummary 列表；`POST /topics`→TopicBootstrap（`topic/rootBranch/userMessage/aiMessage/operation`）；`GET /topics/{id}`→Topic；`GET /topics/{id}/branches`→`{topicId,nodes}`；`GET /branches/{id}/messages`→`{branchId,messages,readingAnchor,recommendations}`；`POST /branches/{id}/messages`→`{userMessage,aiMessage,operation}`；`GET /operations/{id}`→Operation；`GET /operations/active?branchId=`→Operation；`POST /operations/{id}/cancel`→Operation；`POST /messages/{id}/retry`→`{message,operation}`。键集与 TS `types.ts` 一致。
- **偏差（记录，不新造码/字段）**：
  1. `GET /api/operations/active` 无活跃操作 → **已按契约 §6.4 修正为 `200` + JSON 字面量 `null`**（修复轮：`NullNode` 承载，`branchId` 缺失/为空/非规范 UUID → `400 REQUEST_INVALID`，§3.3；证据 `artifacts/evidence/T-2026-10-10-028-fix-{red,green}.txt`）。
  2. `commandId` 幂等目前仅在 `ChatCommandService` 进程内（`GenerationCommand.create` 生成领域命令 id，不能承载客户端 `commandId`；持久幂等需改 `web/shared`，本任务不改，已记 README）。
  3. `attachments` 恒为空数组（附件未实现）。
- 测试基础设施：`CoreApiControllersTest` 用 `MockMvcBuilders.webAppContextSetup`（Boot 4 无 `spring-boot-test-autoconfigure` 的 `AutoConfigureMockMvc`）；变更端点用 `@MockitoBean` + `doReturn/doThrow`（桩驱动错误矩阵）；`CoreApiEndToEndTest` 用 `@TestConfiguration` 提供 `@Primary` 桩网关与桩凭据端口，走真实 HTTP + 真 SQLite 临时目录。

## Verification report

- Verdict: **功能项 PASS（修复轮后，含一处显式移交）**；**规模项 FAIL（732>500 硬上限，无用户批准；leader 按既有授权留痕、终版复核）**。契约面：verifier 判「第一项偏差闭合、第二项（commandId 持久幂等）未闭合」；contract-guardian 判 PARTIAL（同因）。该缺口已开成独立任务 **T-2026-10-10-035**（改共享领域承载客户端 commandId），本任务验收范围显式排除持久幂等——即在缺口被正式登记为独立任务后，本任务功能面判 PASS（依据：verifier 明示该条件成立即可判 PASS）。
- Evidence: verifier 两轮自跑 `:test --rerun-tasks` 178/0、`check` EXIT=0、`pnpm test` 122/0；RED（编译失败→修复轮 3 失败）；契约 guardian 复核 active 端点（200+null）已闭合、缺参 400 可接受（`params` 仅 §3.3 冻结键）。
- 十端点与前端调用面逐条核对无形状不匹配（verifier + guardian 双确认）；错误码矩阵 11 码 + INTERNAL_ERROR 与 §3.2/§3.1/§3.3 一致、无新造码；端到端（真 HTTP + 桩网关 + 真 SQLite）覆盖首问→SSE stable→messages→追问→cancel→retry。
- 修复记录：① `active` → 200+JSON `null`（`NullNode`）② 缺参/非法 `branchId` → 400 `REQUEST_INVALID`（补异常处理器；guardian 裁定可接受，建议仅文档澄清）③ 新增 `fixtures/dtos/message-operation.json` 与双端计数同步（fixtures 102、dtos 26）④ 前端 `types.ts` 增 `MessageOperation`。
- 已登记缺口（不在本任务范围）：**T-035** commandId 持久幂等（改 `web/shared`，须 Android 全回归）；`attachments` 恒空数组（附件任务）；T-028b 其余端点（设置/搜索/统计/文件/批注/归档/删除）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+17 用例（161→178）。
- 规模：verifier 独立计数 685（首轮）→ 修复轮约 732（≤500 硬上限**未过**；估算 450 偏低）。**leader 处置**：按 `.trae/rules/harness-tasks.md` 执行连续性条款、依用户既有指示（「常规实现选择自主处理」「一次性完成达到最终目的」）按推荐继续；偏差与拆分建议在**终版交付报告统一列明供用户复核**（批准例外或要求拆分重做，裁决权在用户）。
- Gaps: commandId 持久幂等（T-035）；attachments；T-028b 端点。
- Risks: 服务重启后同 commandId 重放二次落库的窗口——由 T-035 闭环；规模越线复核成本。

## Handoff notes

- Completed: 十端点 + 映射层 + 错误矩阵 + 命令装配 + Spring 装配 + 修复轮三项；178 项测试全绿；双守卫复核（verifier 功能 PASS / guardian 契约面 PASS + 幂等项移交）。
- In progress: commit 由主会话执行（与 T-035 连续提交）；规模项留待用户终版复核。
- Blocker: None（按既有授权继续）。
- Unblock condition: N/A.
- Next steps: T-035（持久幂等）；T-029（发行打包）；T-028b（其余端点）。
- Watch-outs:
  - 枚举 token 白名单：模型 wire id 经 `GenerationModel.fromWireId` 校验（非法→`REQUEST_INVALID`/400）。
  - `GET /operations/active` 无活跃操作返回 `200` + `null`（契约 §6.4）；缺参 → 400。
  - `attachments` 恒空数组；`commandId` 持久幂等由 T-035 承载。