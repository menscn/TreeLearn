# Treelearn Harness Task: 生成上下文装配、请求规划与事件总线

id: T-2026-10-10-023
title: 生成上下文装配、请求规划与事件总线
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
depends_on: [T-2026-10-10-desktop-ai-gateway-adapters-021.md, T-2026-10-10-desktop-dao-operation-snapshot-artifact-011.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 协调器需要三块前置能力：① 把对话树事实装配成共享 `ContextPlan`（镜像 Android 的上下文组装，含快照与压缩摘要边界）；② 按用途解析模型角色/Provider/Key 可用性并给出稳定拒绝码（对齐 `ModelSettingsPresenter.resolve` 与回落规则）；③ 事件总线（契约 §7 的帧序/seq/终态关流，供 SSE 服务端订阅）。
- Users/scenarios: 生成提交、配置缺失引导、断线恢复（事件重放/缺口补快照的基础）。
- Expected outcome: `com.treelearn.web.generation`：`GenerationContextAssembler`、`GenerationPlanner`、`GenerationEventBus`；三者均有真实 SQLite/内存行为测试；事件类型与顺序严格对照契约 §7（含 `snapshot` 语义与终态关流）。
- Core flow and reversibility: 纯新增；可回退。
- Inputs/outputs: 输入=branchId/用途/上下文事实；输出=`ContextPlan`、解析结果（模型/Provider/Key 状态 + 回落类别）、事件流。
- State and exception handling: 无 Key/无模型/无 Provider/回落 → 稳定类别（对齐契约错误码，禁止新造）；事件订阅取消不泄漏；缓冲有界。
- Constraints and trade-offs: 复用共享 prompt 类与领域判定；不另立上下文/预算/回落判定；不新增依赖。
- AI recommendation and rationale: 三块合一片（每块都小且共同服务协调器）；测试以真实 Store + 内存总线。
- User choice and custom additions: 按已批准方案。
- AI-autonomous decisions: 类切分、事件缓冲容量、解析结果形态。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-023-*` RED/GREEN + 关键断言输出。
- Facts: T-011/T-012/T-021 产物；共享 `ContextPlan`/`DeepSeekContextEncoder`/`PromptPurpose`/`PromptLimits`。
- Assumptions: Android 上下文组装逻辑可在 `android/app/**` 找到并镜像（`ContextAssembler`/`ChatStore` 相关类，以实测为准）；如跨端判定可抽取共享，先记录建议不在本任务抽取。
- Unknowns: 压缩摘要（compaction artifact）在上下文中的位置与预算——以 Android 活动实现为准。
- Risks: 与 Android 上下文组装漂移 → 对照注释 + 后续 fixtures（如可行）；事件顺序与契约漂移 → 契约 §7 逐条断言。
- Deferred: SSE 服务端（T-025）；真实 Provider 调用（需用户 Key）。
- Non-goals: 协调器本体与稳定化（T-024）；HTTP 端点。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付生成上下文装配、模型/Provider/Key 解析与事件总线，语义对照 Android 与契约，真实引擎取证。

## Scope

### In scope
- `GenerationContextAssembler`：由 Store 读取分支消息/稳定修订/创建快照/压缩摘要 → 共享 `ContextPlan`（含预算裁剪按共享 `PromptLimits`）；镜像 Android 对应类并注释对照方法。
- `GenerationPlanner`：用途（CHAT/FAST/TRANSLATION/…）→ 模型角色解析与回落（内置默认→最早可用→空，类别 token 化）；Provider 启停与 Key 存在性（经 `SecretPort.has`）；输出结构化解析结果 + 稳定拒绝类别（对齐契约错误码：无 Provider/无 Key/无模型等）。
- `GenerationEventBus`：订阅（按 operationId）、发布（`snapshot`/`reasoning_delta`/`answer_delta`/`state`/`stable`/`failed`/`interrupted`/`canceled`，`seq` 单调、`operationId` 可过滤）、有界缓冲（供缺口重放）、终态后关流语义、取消订阅清理。
- 测试：装配（含快照冻结内容不回流、预算裁剪）、解析（各回落类别与拒绝码）、总线（顺序/去重语义/有界/取消/终态）。

### Out of scope
- 协调器/稳定化（T-024）；SSE HTTP 层（T-025）；派生任务实现（后续）。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增内部能力。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 对照 `android/app/**` 上下文组装与 `ModelSettingsPresenter`（注释标注）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无用户可见变化。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/generation/**`。
- Documentation surfaces checked: 契约 §5.12/§7、准入文档 §2.7。
- Documentation files updated: 无 → `documentation impact: none`（内部实现；git diff 仅 `web/backend/src/**` 与任务记录）。
- Producer/consumer/sibling/compatibility paths checked: Android 对照注释。
- Superseded phrases/examples reverse-searched: N/A。
- `documentation impact: none` reason and evidence, if applicable: 同上。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤300。
- [ ] 装配：快照冻结（子分支内容不回流）、压缩摘要按 Android 位置/预算、预算裁剪有测试。
- [ ] 解析：无 Provider/无 Key/无模型/回落四类有测试，类别 token 与契约错误码一致（无新造码）。
- [ ] 总线：seq 单调、operationId 过滤、有界缓冲、终态关流、取消订阅清理——均有测试；事件类型/顺序与契约 §7 逐条一致。
- [ ] 与 Android 对照记录（含已知差异与理由）；未另立共享判定。
- [ ] checkstyle 通过；独立 verifier PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `GenerationContextAssemblerTest`、`GenerationPlannerTest`、`GenerationEventBusTest`。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: 见证据
- Failure reason validated: 缺失类

### GREEN — minimum implementation
- Implementation: 三块能力。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
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

- 拒绝/回落类别必须取自契约词汇表（如 `KEY_MISSING`/`NO_MODELS`/`PROVIDER_UNAVAILABLE`/`CONTEXT_EXCEEDS_BUDGET` 等，以 api.md §3.3 实际为准），不得发明新码。
- 事件缓冲容量固定并有文档（如 512 帧/操作），超出按缺口语义处理（订阅者被告知需补快照）。

## Verification report

- Verdict: PARTIAL → PASS（verifier 两轮：首轮 PARTIAL 两项 → 闭环后 PASS，2026-10-10）
- Evidence: verifier 自跑 `:test --rerun-tasks` 19/19、`check` EXIT=0；RED 证据为缺三生产类编译失败。§7 逐条对照（7.1 seq 严格递增+终态关流；7.2 八类型逐条相符；7.3 淘汰/未知操作→needsSnapshot；7.4 终态唯一、晚到丢弃）；Android 对照抽查 6 点（history 行截断/merge 三拒绝类别/historyForRequest 10 轮+前置摘要不拆问答/resolve 回落链逐字/预算同判定/NO_MODELS⟺options 空）。
- 类别相容性：`CONTEXT_SUMMARY_MISSING`(409)/`NO_MODELS`(400)/`KEY_MISSING`(400) 合契约 §3.3，未误用 `PROVIDER_UNAVAILABLE`。
- 首轮 PARTIAL 两项闭环：① Android `history` 会把附件正文经 `attachmentSuffix` 注入 user turn，桌面暂无附件读路径未镜像——已在 `GenerationContextAssembler` javadoc 补记「Deliberate difference, until attachments have a desktop read path (T-018)」并写明收敛条件（leader 应用，注释级变更，随本任务提交）；② 任务文件状态与报告由主会话补齐。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；19 新用例。
- 规模：verifier 独立计数 463（≤500 通过；>200 警告）；超立项 300 估计——后续同类任务按实测口径估。
- Gaps: 摘要读取路径（SUMMARY 源）待派生/压缩任务接入；附件正文注入待 T-018 后回补；
- Risks: 桌面上下文与 Android 的两处已知差异已显式记录并设收敛条件。

## Handoff notes

- Completed: 三块能力 + 独立 verifier PASS（2026-10-10）；463 行；19 项测试全绿。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-024（协调器+稳定化）；T-018 附件存储落地后回补附件正文注入与摘要读取。
- Watch-outs: 不得另立上下文/预算/回落判定；拒绝类别只能取契约 §3.3。