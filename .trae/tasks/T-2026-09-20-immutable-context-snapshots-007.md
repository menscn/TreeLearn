# Treelearn Harness Task: 实现不可变上下文快照与组装器

id: T-2026-09-20-007
title: 实现不可变上下文快照与组装器
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 190
shared_code_touched: true
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-20-deepseek-stream-gateway-006.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 分支出生上下文必须冻结；历史 reasoning 必须真实进入后续 DeepSeek 上下文。
- Problem/background: DeepSeek 在无 tools 时忽略历史消息专用 `reasoning_content` 字段，且动态回溯父分支会改变旧分支语义。
- Users/scenarios: 用户从旧回答版本分叉、父分支继续变化后，在子分支继续追问。
- Expected outcome: 版本化 ContextSnapshot、digest、供应商无关 ContextPlan、DeepSeekContextEncoder 和容量压缩边界。
- Core flow and reversibility: 创建分支时原子固化引用；core 结构化选择上下文；ai 将稳定回答编码成普通 assistant content。
- Inputs/outputs: 输入 snapshot 与本分支稳定消息；输出有序 ContextPlan，再输出 provider messages 或显式超限/压缩失败。
- State and exception handling: 缺失 revision、digest 不符、非法 JSON、超窗或摘要失败均 fail closed。
- Constraints and trade-offs: 使用结构化 JSON；内部标签固定；不得静默删除 reasoning。
- AI recommendation and rationale: `<treelearn_reasoning>` 与 `<treelearn_answer>` 封装使供应商实际消费完整历史。
- User choice and custom additions: reasoning 展示、落库、加入后续上下文三项均为强需求。
- AI-autonomous decisions: 标签只用于内部请求，不在 UI 显示；快照创建后不可变。
- User-confirmation decisions: 不可变回答版本与 P0 创建快照。
- Acceptance evidence: 纯 JVM fixture 精确断言上下文顺序、标签、版本锚定和失败关闭。
- Facts: 技术方案 §5.3 与 §9.4 是权威；system 注入归 T-020，不属于 ContextPlan。
- Assumptions: 当前首版不发送 tools。
- Unknowns: 实际上下文上限由发布时模型契约复核。
- Risks: 标签转义、摘要覆盖范围不足、错误引用 active revision。
- Deferred: tools 专用 reasoning 回传协议。
- Non-goals: 不执行网络调用或写 UI。
- Skipped non-critical questions and reason: token 估算器实现可替换，不改变 fail-closed 语义。

## Goal

保证每个分支后续请求使用创建时固定来源，并包含可辨识的历史思考与最终回答。

## Scope

### In scope
- snapshot codec/digest、创建读取、BuildContextPlanUseCase、DeepSeekContextEncoder、reasoning 标签、历史容量预算和摘要接口边界；为 T-020 暴露明确的预算输入/结果。

### Out of scope
- system 提示词正文/注入、摘要网络生成、GenerationCoordinator 和页面折叠状态。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增固定上下文；保留分支隔离和旧 revision 引用。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): CreateBranch 生产 snapshot；GenerationCoordinator 和派生 AI 消费。
- Native bridge or independent-client compatibility/update requirements: 输出需兼容 AiGateway 普通 messages 契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: snapshot schema/version、digest 和内部标签协议。
- Authoritative code/config/migration evidence: `android/core` context plan/use case、`android/data` snapshot repository 与 `android/ai` DeepSeek encoder。
- Documentation surfaces checked: 技术方案 §5/§9、PRD 上下文与隐私章节。
- Documentation files updated: schema 或标签变化时同步技术方案；隐私仍声明推理会重发。
- Producer/consumer/sibling/compatibility paths checked: 整条回答分叉、选区分叉、普通追问、重置后旧子分支。
- Superseded phrases/examples reverse-searched: “reasoning 不回传”与专用字段历史回传示例。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 只更新当前态。

## Acceptance criteria

- [ ] 同一 JVM 测试命令先 RED 后 GREEN，证明快照在父分支后续变化和 active revision 切换后仍完全不变。
- [ ] 输出普通 assistant content 精确包含 reasoning/answer 标签、转义后的原文与确定顺序。
- [ ] 缺 revision、digest 损坏、非法 snapshot、摘要失败或在调用方预留 system/当前问题/最小回答空间后仍超窗时禁止请求，不得只丢弃推理。
- [ ] ContextPlan 与 DeepSeekContextEncoder 不生成、保存或摘要 system 消息；system 恰好一次且首位的职责明确交给 T-020 请求组装器。
- [ ] 隐私文档与真实组装行为一致：历史问题、推理和回答可能提交给 DeepSeek。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- JSON 读写必须使用结构化 API；digest 基于规范化字节，不基于 HashMap 遍历顺序。
- `:core` 不得引用 DeepSeek DTO 或标签；标签协议集中在 `:ai`，并做往返、恶意同名标签和空 reasoning 测试。
- 容量接口必须接收调用方已预留预算或返回可组合的精确估算，不能假设 system 为零成本。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: provider 对标签文本的解释和 token 膨胀。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 解锁 T-020 提示词策略，再解锁 GenerationCoordinator 和派生 AI。
- Watch-outs: 不得读取 Message.activeRevisionId 替换 snapshot.sourceRevisionId。
