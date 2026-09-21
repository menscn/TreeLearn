# Treelearn Harness Task: 实现对话、思考披露与回答操作

id: T-2026-09-20-013
title: 实现对话、思考披露与回答操作
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-09-20-generation-coordinator-recovery-008.md, T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-20-retry-reset-version-switching-010.md, T-2026-09-20-derived-ai-artifacts-011.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 对话页展示流式 reasoning/answer；思考默认折叠、可展开、灰色，并支持复制、推荐追问/分叉、重试和重置。
- Problem/background: 推理若与回答混排会干扰阅读，若只显示不落库/不复制又破坏信息一致性。
- Users/scenarios: 用户观看生成、展开思考、复制全文、选中文字引用追问、查看子分支或在失败处重试。
- Expected outcome: Chat Presenter/ViewState、消息列表、ReasoningDisclosure、最新回答推荐与回答操作菜单。
- Core flow and reversibility: streaming reasoning/answer 独立渲染；stable 后展示 revision；展开态仅页面会话；操作调用 use case。
- Inputs/outputs: 输入 branch/message/operation/revision 状态；输出用户命令、剪贴板文本和导航参数。
- State and exception handling: partial/failed/interrupted/stable、空 reasoning、版本切换和晚事件均有确定呈现。
- Constraints and trade-offs: 默认折叠；展开内容使用可读次级灰色；复制全文按“思考过程 + 最终回答”组合。
- AI recommendation and rationale: UI 不保存独立 reasoning 副本，始终从 operation/revision ViewState 读取。
- User choice and custom additions: reasoning 展示、持久化、进入后续上下文，默认折叠且颜色更灰。
- AI-autonomous decisions: 展开态不跨重启；空 reasoning 不显示折叠入口。
- User-confirmation decisions: BASE-23～25 与思考交互细节。
- Acceptance evidence: Presenter/UI 自动测试和手机/平板人工可读性检查。
- Facts: v2.5 原型与技术方案 §13 是视觉/行为权威。
- Assumptions: Android 剪贴板可用，复制后由系统管理。
- Unknowns: 无。
- Risks: RecyclerView 复用导致展开态串行、流式更新抖动、灰色对比不足。
- Deferred: Markdown 富文本高级扩展和版本 diff。
- Non-goals: 不实现树画布、全局搜索或派生 AI 生成。
- Skipped non-critical questions and reason: 精确色值由主题 token 确定，但必须通过对比度检查。

## Goal

交付稳定、可读、可操作的对话体验，并完整呈现及保留思考内容。

## Scope

### In scope
- 对话列表、流式状态、思考折叠、版本选择、最新回答推荐双入口、复制/追问/分叉/重试/重置/子分支入口和 UI 测试。

### Out of scope
- 全局导航抽屉、搜索页和树 Canvas。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增对话 UI；保留部分内容、稳定版本和来源锚点语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Coordinator/Repository 生产 ViewState；Topic/Branch/Retry 用例消费命令；手机/平板布局。
- Native bridge or independent-client compatibility/update requirements: 系统剪贴板是唯一平台出口，需展示隐私预期。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 可见状态、思考折叠、复制格式和操作可用性。
- Authoritative code/config/migration evidence: Chat Presenter/ViewState、layout/adapter 与 UI tests。
- Documentation surfaces checked: V3.1 PRD、v2.5 原型、技术方案 §13/§14。
- Documentation files updated: 文案、复制格式或交互变化时同步 PRD/原型。
- Producer/consumer/sibling/compatibility paths checked: thinking on/off、流式/失败/中断/稳定、旧版、手机/平板。
- Superseded phrases/examples reverse-searched: 思考默认展开、思考不复制、仅答案落库。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留版本历史。

## Acceptance criteria

- [ ] 同一 Presenter/UI 测试命令先 RED 后 GREEN，覆盖 reasoning/answer 流式交错、失败部分内容和 stable revision。
- [ ] 有 reasoning 的每条回答默认折叠；点击独立展开/收起；展开文本使用次级灰色且通过可读对比度检查。
- [ ] 展开状态不跨重启、不因列表复用串到其他 revision；空 reasoning 不显示无意义入口。
- [ ] “复制全文”顺序包含完整思考和最终回答；选择引用、追问、分叉、重试、重置和子分支入口只在合法状态启用。
- [ ] 旋转、切分支、回到页面时生成继续，持久 partial/stable 内容不丢失且无 UI 重复消息。
- [ ] 只在当前分支最新稳定回答展示 T-011 的 ANSWER_RECOMMENDATIONS；继续追问后旧推荐立即隐藏，推荐当前追问与新建分支热区语义保持独立。

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

- 展开键使用 revisionId 或 operationId，不使用 adapter position。
- 流式 UI 可合并刷新，但不得改变数据库批次语义；正文不能写 debug 日志。
- 文字选择锚点必须基于稳定 answer，不允许从 reasoning 创建业务分支，除非 PRD 后续明确扩展。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 无障碍语义、长推理性能和剪贴板隐私。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 供搜索阅读位置与最终核心流验收使用。
- Watch-outs: UI 不能把 partial 内容伪装成稳定回答。
