# Treelearn Harness Task: 设置端点 model-resolution（无 Key 引导闭环）

id: T-2026-10-10-034
title: 设置端点 model-resolution（无 Key 引导闭环）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 60
shared_code_touched: false
depends_on: [T-2026-10-10-core-rest-api-028.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 第一轮真实浏览器验收发现主页加载即调用 `GET /api/settings/model-resolution` 返回 404，导致「无可用模型」引导不闭环（前端据此判断是否禁用发送并给出可执行引导）。
- Users/scenarios: 首次使用（无 Provider/无模型/无 Key）→ 引导到设置；有内置 Provider 无 Key → 提示配置 Key。
- Expected outcome: 端点恒 `200` 返回契约 §5.12 的 `ModelResolution`（仅 `roles`，`fallback` ∈ `NONE/BUILTIN/EARLIEST/EMPTY`），回落判定复用 `GenerationPlanner`，不另立规则。
- Core flow and reversibility: 新端点；可回退。
- Inputs/outputs: 输入=无参；输出=`ModelResolution`。
- State and exception handling: 空库→`EMPTY`；内置 Provider 无 Key→`BUILTIN`；仅普通可用模型→`EARLIEST`；不触碰凭据。
- Constraints and trade-offs: `GET/PUT /api/settings/model-roles` 需持久化且桌面十二张准入表无承载 → **需 schema 变更与用户确认**，故本任务不移交实现；不新增依赖。
- AI recommendation and rationale: 只做读端点，投影 `GenerationPlanner.resolveAll` 结果。
- User choice and custom additions: 无（缺陷修复）。
- AI-autonomous decisions: 控制器/bean 装配细节。
- User-confirmation decisions: 无新增；model-roles 的 schema 变更需另行确认（未实施）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-034-{red,green,checkstyle}.txt`；浏览器复验（第二轮起）确认 200。
- Facts: 契约 §5.12/§6.6 已定义该端点；fixtures `dtos/model-resolution.json` 为样例。
- Assumptions: 三个角色的配置存储尚未实现（role 配置端点未实现），故以未配置进入回落链。
- Unknowns: 无。
- Risks: `EMPTY` 时 `providerId/modelId` 为 null 的形状仅由「有 Provider」用例覆盖（低风险，Spring 默认含 null）。
- Deferred: `model-roles` GET/PUT（需 schema 准入 + 用户确认）。
- Non-goals: 其它设置端点（Provider/Key/搜索等，属 T-028b）。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付 `GET /api/settings/model-resolution`，使前端无 Key 引导闭环，回落判定复用既有规划器。

## Scope

### In scope
- `web/backend/src/main/java/com/treelearn/web/api/SettingsApiController.java`（恒 200 + `ModelResolution`）；`GenerationPlanner.resolveAll`（公开、复用同一回落链，`plan()` 委托它）；`GenerationApiConfiguration` 注册 bean；测试 4 例。

### Out of scope
- `model-roles` 端点（需 schema 变更）；其它设置端点；凭据读写。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新端点；既有端点与 ready 不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 前端 `homeData.ts`/`HomePage.tsx`（消费方）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 设置读端点可用（用户可见：无 Key 引导闭环）。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/api/SettingsApiController.java`、`.../generation/GenerationPlanner.java`。
- Documentation surfaces checked: 契约 §5.12/§6.6；`web/README.md` 状态节。
- Documentation files updated: `web/README.md` 一行（含 model-roles 缺口）。
- Producer/consumer/sibling/compatibility paths checked: 前端消费面。
- Superseded phrases/examples reverse-searched: 「`model-resolution` 404」表述。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤200。
- [ ] 形状与契约 §5.12 + fixture 一致（键集/`fallback` token）；HTTP 200（非 404）。
- [ ] 三态回落正确：空库→`EMPTY`、内置无 Key→`BUILTIN`、仅普通模型→`EARLIEST`（测试证明）。
- [ ] 回落判定复用 `GenerationPlanner`（未另立规则）。
- [ ] 未改 `DesktopSchema.java`（`git diff` 证明）；checkstyle 通过；独立 verifier PASS。
- [ ] 浏览器复验：主页不再出现 404，引导链路可用（已完成）。

## TDD execution record

### RED — failing test first
- Test/scenario: `SettingsApiControllerTest`（或等价，4 例）。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: 199 例 4 失败（均为 `expected 200 but was 404`）
- Failure reason validated: 端点缺失

### GREEN — minimum implementation
- Implementation: `SettingsApiController` + `GenerationPlanner.resolveAll` + bean 装配。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 199 passed / 0 failed（新增 4 例）
- Regression evidence: 既有 195 全绿（195→199）

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 读端点不触碰凭据端口（只读 Provider/Model 可用性 + Key 存在性判定沿用规划器）。
- `fallback` token 名称直接取域枚举名（与契约 token 一致）。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10；附一条低风险备注）
- Evidence: verifier 自跑 `:test --rerun-tasks` 199/0、`check` EXIT=0；形状与契约 §5.12 行 422-433 及 `fixtures/dtos/model-resolution.json` 一致；`plan()` 现委托 `resolveAll()`（无另立规则）；三态 token 经测试实证；`git diff` 证明 `DesktopSchema.java` 未改。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+4 用例（195→199）。
- 规模：verifier 独立计数 ≈40（≤200）。
- Gaps: `model-roles` GET/PUT（需 schema 准入 + 用户确认，已移交并记录）；`EMPTY` 空字段形状未显式 null 断言（低风险）。
- Risks: 低（读端点、无写入）。

## Handoff notes

- Completed: 端点落地 + 独立 verifier PASS；浏览器复验确认 200 且引导链路可用。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-028b 其余设置端点（含 `model-roles`，届时需 schema 准入与用户确认）。
- Watch-outs: `model-roles` 需持久化承载，不得擅自改 schema。