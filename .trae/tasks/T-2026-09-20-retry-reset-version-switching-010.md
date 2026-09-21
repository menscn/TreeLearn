# Treelearn Harness Task: 实现重试、重置与版本切换

id: T-2026-09-20-010
title: 实现重试、重置与版本切换
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: true
depends_on: [T-2026-09-20-generation-coordinator-recovery-008.md, T-2026-09-20-topic-branch-lifecycle-009.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 失败重试与稳定回答重置都创建新操作；成功后产生新不可变版本，不覆盖旧来源。
- Problem/background: 原地改写回答会让既有子分支、搜索结果和上下文悄然变化。
- Users/scenarios: 用户在失败位置重试，或对稳定回答重新生成并浏览历史版本。
- Expected outcome: RetryGeneration、ResetAnswer、SelectRevision 用例和明确的操作可用性规则。
- Core flow and reversibility: retry/reset 创建新 operation；成功追加 revision 并切 active；选择旧版仅改展示指针。
- Inputs/outputs: 输入 message/operation/revision ID；输出新 operation 或切换后的 active revision。
- State and exception handling: 活动生成、非 AI message、跨 message revision、partial 版本和重复命令均拒绝。
- Constraints and trade-offs: 用户消息与 AI 逻辑位置不重复；retry/reset 不增加对话轮数。
- AI recommendation and rationale: 将尝试、稳定版本和显示选择分开，维持引用完整性。
- User choice and custom additions: 中断保留部分内容并回到原位手动重试。
- AI-autonomous decisions: 失败 partial 只属于旧 operation；新操作从同一固定输入重新开始。
- User-confirmation decisions: P0 不可变版本与失败恢复语义。
- Acceptance evidence: 纯 JVM + SQLite 集成测试覆盖版本锚定和非法命令。
- Facts: 技术方案 §5.1、§7 是权威。
- Assumptions: message 可保存 activeRevisionId 与 currentOperationId。
- Unknowns: 无。
- Risks: 重试重复插入用户消息或把旧 partial 升级为 stable。
- Deferred: 自动重试和版本对比视图。
- Non-goals: 不实现按钮样式、网络 parser 或分支画布。
- Skipped non-critical questions and reason: 历史版本列表排序按 revisionNo，已由领域约束确定。

## Goal

让每次重试/重置可审计、可回退，并保证旧子分支永远引用原稳定版本。

## Scope

### In scope
- Retry/Reset/SelectRevision 用例、权限校验、事务切换和版本历史查询。

### Out of scope
- 对话页面、复制和搜索 UI。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增重试/重置/切版；保留消息 ordinal、轮数和旧 branch sourceRevisionId。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Chat UI 触发；Coordinator 执行；DAO/revision/snapshot 消费。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 操作创建、稳定切换、历史保留和轮数行为。
- Authoritative code/config/migration evidence: application use cases、Repository 事务与测试。
- Documentation surfaces checked: 技术方案 §5/§7、PRD 重试/重置/版本章节、原型操作菜单。
- Documentation files updated: 实际可用性或版本行为变化时同步当前态文档。
- Producer/consumer/sibling/compatibility paths checked: 失败重试、稳定重置、旧版选择、子分支来源、未读/轮数。
- Superseded phrases/examples reverse-searched: 原地覆盖、重试新增一轮和旧分支随当前版本变化。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写历史版本记录。

## Acceptance criteria

- [ ] 同一目标测试命令先 RED 后 GREEN；retry/reset 只新增 operation，成功后才新增 immutable revision。
- [ ] 重试/重置不重复用户消息、不增加轮数；失败 partial 不成为可分叉来源。
- [ ] 旧子分支在 active revision 切换和手动选择旧版后仍保持原 sourceRevisionId、reasoning 与 answer。
- [ ] 活动生成、跨消息 revision 和重复命令返回稳定错误，不破坏当前版本。

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

- retry 必须重新使用原 operation 的规范化输入基线，不读取后来变化的 active revision。
- SelectRevision 是展示选择；已存在子分支的来源引用不得被级联更新。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: operation/revision 指针竞态。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 供对话 UI 使用。
- Watch-outs: 不要把“重试”实现成修改旧 operation 终态。
