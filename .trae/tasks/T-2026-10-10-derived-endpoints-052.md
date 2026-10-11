# Treelearn Harness Task: 派生任务端点（§6.5，MESSAGE_TRANSLATION 切片）

id: T-2026-10-10-052
title: 派生任务端点（§6.5，MESSAGE_TRANSLATION 切片）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 100
shared_code_touched: false
depends_on: [T-2026-10-10-version-commands-051.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求第三波「翻译/推荐/压缩」的后端通道。
- Problem/background: 契约 §6.5 定义 POST/GET /api/derived 八个 kind；协调器派生 prompt 链路（Planner/ContextAssembler 认识 purpose）已存在但无端点。
- Users/scenarios: 前端对稳定回答发起翻译，经 operation+SSE 到终态后读产物。
- Expected outcome: POST 切片支持 MESSAGE_TRANSLATION（语义自足：源文本即 currentInput）；GET 全 kind（含 stale 判定）。其余 kind 400 明示不支持（输入组装依赖 Android ArtifactInputs 的 entries/outline/compression 深度材料，Deferred）。
- Core flow and reversibility: DerivedController（单文件）+ 协调器既有 submit/derive 路径；纯新增。
- Inputs/outputs: 见契约 §6.5；错误码 `DERIVED_KIND_UNSUPPORTED`（新目录条目）。
- State and exception handling: 非 AI 消息 409 `MESSAGE_ROLE_MISMATCH`；生成中 409 `OPERATION_NOT_STABLE`；scope 缺失 404 `NOT_FOUND`。
- Constraints and trade-offs: 契约补切片说明而非放宽语义。
- AI recommendation and rationale: 切片交付保证每一步有真实证据，不承诺未组装材料的 kind。
- User choice and custom additions: 已批准。
- AI-autonomous decisions: stale 判定 fail-closed（行基线缺失即 stale）；请求基线参数为空 → 非 stale（首次读取）。
- User-confirmation decisions: 无。
- Acceptance evidence: artifacts/evidence/T-2026-10-10-052-*。
- Facts: `PromptPurpose.MESSAGE_TRANSLATION`、`DeepSeekPromptRegistry` tl-message-translation/1.0.0、`ArtifactStore.find(kind, scopeId)`。
- Assumptions: 无。
- Unknowns: 无。
- Risks: 无已知。
- Deferred: 其余六 kind；解析态生命周期。
- Non-goals: 不做翻译缓存。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

派生端点按 §6.5 切片工作：POST 翻译走 operation+SSE、GET 带 stale 判定、不支持的 kind 400 明示。

## Scope

### In scope
- `web/backend/.../api/DerivedController.java`（新）。
- `web/backend/.../api/ErrorCatalog.java`：`DERIVED_KIND_UNSUPPORTED`。
- 契约 §6.5 切片说明。
- 测试 `DerivedEndpointsTest`（8 例）。

### Out of scope
其余六 kind 的输入组装；翻译缓存。

## Acceptance criteria
- [x] RED→GREEN：8 例（404→全绿）。
- [x] POST 翻译返回 operation（promptPurpose=MESSAGE_TRANSLATION）；走协调器派生路径。
- [x] GET 带基线判定（fail-closed stale）。
- [x] 契约切片说明落稿。
- [x] 全量 `:check` 300 例 + checkstyle exit 0。

## TDD execution record

### RED — failing test first
- Test/scenario: POST 期望 operation 形状（端点缺失 404 红）
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.DerivedEndpointsTest`
- Exit code: 1
- Observed failure: 端点 404
- Failure reason validated: `artifacts/evidence/T-2026-10-10-052-red.txt`（EXITCODE=1）

### GREEN — minimum implementation
- Implementation: `DerivedController` + `DERIVED_KIND_UNSUPPORTED` 目录条目 + 契约切片
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.DerivedEndpointsTest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-052-green.txt`（8/8）
- Regression evidence: `artifacts/evidence/T-2026-10-10-052-check.txt`（300 PASSED / 0 FAILED / EXITCODE=0）

### REFACTOR — optional
- Cleanup: payload 解析为对象下发（前端不自行 JSON.parse）；fail-closed stale 注释。
- Regression evidence: 同 check 文件。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行实测 100，未超软上限）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 翻译 policy 版本访问器是 `PromptSpec.promptPolicyVersion()`。
- GET 响应 payload 为解析后 JSON 对象。

## Verification report

- Verdict: PASS（实现者自检全绿；合并独立 verifier 待统一复核）
- Evidence: `artifacts/evidence/T-2026-10-10-052-{red,green,check}.txt`（check 300 例 0 失败 + checkstyle）
- Changed/deleted assertion rationale and equivalent behavior coverage: 契约 §6.5 补实现切片说明（仅 MESSAGE_TRANSLATION 可 POST，其余 400 `DERIVED_KIND_UNSUPPORTED` 明示 Deferred）；GET 全 kind 可用。
- Gaps: 其余六个派生 kind 的输入组装（ArtifactInputs.entries/outline/compression）Deferred；翻译产物解析态生命周期未做。
- Risks: 无已知。

## Handoff notes

- Completed: DerivedController + 8 例 + 全量 check。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: T-053 前端消费。
- Watch-outs: `PromptSpec` 的 policy 版本字段是 `promptPolicyVersion()`；`ArtifactStore.Artifact` 字段为 public final；错误响应 params 在 `error.params` 下。


## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `POST/GET /api/derived` 由 404 变为按 §6.5 切片可用。
- Authoritative code/config/migration evidence: `web/backend/.../api/DerivedController.java`、`.../api/ErrorCatalog.java`、`web/contracts/api.md` §6.5。
- Documentation surfaces checked: 契约与 `web/README.md` 状态节。
- Documentation files updated: 已按本轮交付同步。
- Producer/consumer/sibling/compatibility paths checked: 前端 `startTranslation`/`loadDerivedArtifact`（T-053 消费）。
- Superseded phrases/examples reverse-searched: 无（端点此前不存在）。
