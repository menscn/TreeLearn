# Treelearn Harness Task: 后端契约 DTO 层（records + fixtures 往返）

id: T-2026-10-10-025
title: 后端契约 DTO 层（records + fixtures 往返）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 300
shared_code_touched: false
depends_on: [T-2026-10-10-web-contract-design-014.md, T-2026-10-10-desktop-config-stores-012.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 唯一契约已定稿（`web/contracts/api.md` + 101 fixtures）；REST/SSE 端点（T-026/T-028）需要 Java DTO 记录类型与 Jackson 编解码，且必须与契约 fixtures 同源约束。
- Users/scenarios: 控制器序列化响应、请求反序列化；测试以 fixtures 往返约束漂移。
- Expected outcome: `com.treelearn.web.api.dto` 记录类型覆盖契约 §5 全部 DTO 与 §6 请求体（含 §5.1b `TopicSummary`）；`ApiErrorResponse`（§3.1 封装）；Jackson 映射配置（`*Millis` 为 number、枚举 token 原样、null 语义）；fixtures 全量往返测试（每个 dtos/*.json 反序列化再序列化语义等价；errors/*.json 结构一致；commands/*.json 请求体可反序列化）。
- Core flow and reversibility: 纯新增；可回退。
- Inputs/outputs: 输入=fixtures JSON；输出=DTO 实例与再序列化 JSON。
- State and exception handling: 未知字段宽容（`FAIL_ON_UNKNOWN_PROPERTIES` 关闭以利前向兼容）？——**不**：契约守卫要求漂移可见；采用严格未知字段拒绝 + 测试证明；未知错误码在类型层用 String 承载（前端负责映射）。
- Constraints and trade-offs: 不新增依赖（Jackson 已在栈内）；DTO 只做数据传输，不含业务判定；字段名/可空性/单位逐字对齐契约。
- AI recommendation and rationale: 一条 record/文件或按域分文件（topics/branches/messages/ops/settings/files/annotations）；fixtures 往返为唯一约束手段。
- User choice and custom additions: 已批准方案。
- AI-autonomous decisions: 文件组织、Jackson 配置位置。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-025-*` RED/GREEN + fixtures 计数输出。
- Facts: fixtures 101（dtos 25/commands 28/errors 42/sse 6）+ fixtures-ai 20。
- Assumptions: Spring Boot 4.1.1 自带 Jackson 3（`tools.jackson`）。
- Unknowns: Jackson 3 的严格模式配置细节——以实测为准。
- Risks: DTO 与契约漂移 → fixtures 往返 + contract-guardian 复核。
- Deferred: 端点实现（T-026/T-028）。
- Non-goals: 业务逻辑；域模型转换（在端点任务内做映射）。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付与契约逐字段一致的后端 DTO 层与错误封装，以 fixtures 全量往返测试约束漂移。

## Scope

### In scope
- `web/backend/src/main/java/com/treelearn/web/api/dto/**`：§5 全部 DTO（Topic/TopicSummary/Branch/BranchSource/BranchAnchor/BranchTree/Message/AnswerRevision/Operation/Attachment/Annotation/Provider/Model/DerivedArtifact/WebSearchSettings/ModelRoles/ModelResolution/SearchResult/Stats/DeletionPreview/ForkResult/ReadingAnchor/TopicBootstrap/TurnAccepted/Conversation 等，以 api.md §5 实际为准）+ §6 请求体记录 + `ApiErrorResponse`。
- Jackson 配置：`*Millis` number、token 枚举原样、严格未知字段（拒绝并测试）。
- 测试：`DtoFixturesRoundTripTest`（dtos 全量往返语义等价 + 数量断言）、`CommandFixturesTest`（commands 可反序列化）、`ErrorEnvelopeFixturesTest`（errors 结构一致）。

### Out of scope
- 控制器/端点（T-026/T-028）；域→DTO 映射。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增类型；无既有行为变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 未来控制器；TS 侧已有对应类型（T-015）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无用户可见变化（内部类型）。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/api/dto/**`。
- Documentation surfaces checked: `web/contracts/api.md` §5/§6。
- Documentation files updated: 无 → `documentation impact: none`（内部类型；git diff 仅 web/backend/src 与任务记录）。
- Producer/consumer/sibling/compatibility paths checked: TS 类型对照（contract-guardian 复核）。
- Superseded phrases/examples reverse-searched: N/A。
- `documentation impact: none` reason and evidence, if applicable: 同上。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤350。
- [ ] fixtures 全量往返：dtos 25 个全部语义等价（键集/类型/可空性）；commands 28 个可反序列化；errors 42 个封装结构一致；数量断言（fixtures 计数变化即失败）。
- [ ] 严格未知字段：多字段 fixture 被拒（测试证明），确保漂移可见。
- [ ] `ApiErrorResponse` 与 §3.1 一致；未知错误码可作为字符串承载。
- [ ] checkstyle 通过；独立 verifier PASS；contract-guardian 复核 DTO↔契约一致。
- [ ] T-026/T-028 可直接消费（编译依赖就绪）。

## TDD execution record

### RED — failing test first
- Test/scenario: `DtoFixturesRoundTripTest`、`CommandFixturesTest`、`ErrorEnvelopeFixturesTest`、`StrictnessTest`。
- Command: `.\gradlew.bat :test --rerun-tasks --console=plain`（cwd=web/backend）
- Exit code: 1
- Observed failure: `:compileTestJava FAILED`——`找不到符号 ApiJson`、`程序包 RequestDtos 不存在` 等（DTO 层缺失）。
- Failure reason validated: 失败确因缺失 DTO 类型；证据 `artifacts/evidence/T-2026-10-10-025-red.txt`。

### GREEN — minimum implementation
- Implementation: `com.treelearn.web.api.dto`（10 个域容器：ApiJson + Topic/Branch/Message/Attachment/Operation/Config/SearchStats/Error/Request）；`ApiJson` 开启 `FAIL_ON_UNKNOWN_PROPERTIES`。
- Command: `.\gradlew.bat :test --rerun-tasks --console=plain`（cwd=web/backend）
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-025-green.txt`（TOTAL_TESTS=148，failures=0；fixtures dtos=25/commands=28/errors=42）。
- Regression evidence: 既有 143 全绿 + 新增 5 测试 = 148；`ReadyEndpointTest` 仍 PASS（严格映射未注册为 Spring bean，默认 Jackson 未受影响）。
- checkstyle: `artifacts/evidence/T-2026-10-10-025-checkstyle.txt`（`.\gradlew.bat check` EXIT 0）。

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 先读 api.md §5/§6 与 fixtures 实际形状（fixtures 是唯一共享样例）；字段名逐字一致。
- 可选字段用包装类型/`JsonNullable` 语义按 Jackson 3 实际能力选择并在测试中固定（null vs 缺失的区别以 fixtures 为准）。

## Verification report

- Verdict: PARTIAL → PASS（verifier 唯一未结项=任务文件 `id` 书写格式，已修正为 `T-2026-10-10-025`）；contract-guardian PASS
- Evidence: verifier 自跑 `:test --rerun-tasks` 148/0、`check` EXIT=0；RED（compileTestJava 59 处找不到符号、EXIT=1）可信；fixtures 消费真实性：三测试真遍历目录、目录缺失即抛（不静默跳过）、硬断言 25/28/42、`JsonNode` 深度等价比较（键集+类型+null+number）。contract-guardian：24 个 §5 DTO 四方（Java/契约/TS/fixtures）逐字段一致、零差异；§6 抽查 8 个请求体一致。
- 裁定与建议（guardian）：枚举用 `String` 承载可接受（保 token 原样与前向兼容），但 T-026/T-028 应在控制器边界做 token 白名单校验；§6 九个纯动作响应包裹未建模（`{deletedTopicIds}`/`{deletedBranchIds}`/`{branchId,readingAnchor}`/`{message,operation}`/`{deletedProviderId}`/`{added,models}`/`{deletedAttachmentId}`/`{cleaned}`/`{deletedAnnotationId}`）→ 登记进 T-026/T-028 并补 fixtures + guardian 回归；严格未知字段策略（opt-in 测试期探测器）与契约无冲突，生产反序列化策略由 T-026/T-028 显式决定。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+5 测试文件（143→148）。
- 规模：verifier 独立计数 357（≤500 通过；>200 警告；自设 ≤350 不可达——理论下限约 354，论证为散文口径，verifier 已记录）。
- Gaps: 9 个响应包裹与 fixtures 补充（T-026/T-028）；生产反序列化严格策略（同上）。
- Risks: Java 侧枚举 token 无类型约束——由控制器边界白名单校验兜底（T-026/T-028 执行）。

## Handoff notes

- Completed: DTO 层 + fixtures 往返 + 双守卫 PASS（2026-10-10）；148 项测试全绿；`ApiJson` 严格模式不影响 Spring 默认（ReadyEndpointTest 复跑通过）。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-026（SSE 端点）与 T-028（REST 端点）消费本层；9 个响应包裹建模 + fixtures + guardian 回归在同一批内完成。
- Watch-outs:
  - 枚举 token 白名单校验；严格策略显式决定；字段名逐字对齐契约。
  - 业务行 357（理论下限约 354；软上限警告级，硬上限未触）。
  - 严格未知字段仅在 `ApiJson`（非 Spring bean）上启用。