# Treelearn Harness Task: 对话内容区视觉复刻

id: T-2026-09-27-022
title: 对话内容区视觉复刻
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 190
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-paper-backdrop-shell-010.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 对齐原型对话区的消息、回答、思考过程、回答操作、来源卡及推荐追问。
- Expected outcome: 空会话、生成中、回答完成、来源和错误状态视觉对应 V1.1。
- User choice and custom additions: 保持现有对话和生成能力；有内容截图必须在隔离数据环境，不配置或写入用户 Key。
- Risks: 当前设备无有效主题/Key，既有 `android-chat-batch-abc-2026-09-27.png` 是首页图，不可当作对话页证据。

## Goal

对话主体在对应状态下与原型视觉一致，消息/回答语义和生成交互不回归。

## Scope

### In scope
- `ChatScreen.java` 的对话内容渲染、`screen_chat*.xml`、`item_chat_message.xml`、`item_chat_suggestion.xml`、`view_reasoning.xml`、`view_answer_actions.xml`、`item_source_history.xml`、`AnswerActionBar.java`、`ReasoningDisclosure.java`、`MarkdownRenderer.java` 及相关测试。

### Out of scope
- 输入栏、模型/分支/引用/附件/搜索弹层（任务 023）；生成业务、prompt、计量与数据契约。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 调整消息、回答、思考、来源与追问的布局/排版；流式状态、复制、反馈、朗读、来源打开和推荐追问行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatScreen` 消费 `ChatViewState` 和 `ChatPresenter` 动作；`MarkdownRenderer` 负责现有正文渲染；回答操作由 `AnswerActionBar` 承载。
- Native bridge or independent-client compatibility/update requirements: 无桥接变化。
- Behavior-neutral non-applicability reason, if applicable: 不适用（生成/复制/来源动作必须回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话内容展示变化；生成状态、来源和消息动作规则不变。
- Authoritative code/config/migration evidence: `ChatScreen.java`、`ChatViewState`、`ChatPresenter`、对话相关布局。
- Documentation surfaces checked: `android/README.md` 对话行为与 V1.1 全屏规格；现有截图证据正确性。
- Documentation files updated: 若流程、动作及来源规则不变，记录 none 和搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 用户消息、回答修订、思考展开、来源卡、追问、失败/生成中。
- Superseded phrases/examples reverse-searched: 错误引用 `android-chat-batch-abc-2026-09-27.png` 为对话截图的现状描述。
- `documentation impact: none` reason and evidence, if applicable: 纯呈现调整且 README 行为事实仍准确时适用。
- Historical/PRD handling: 不覆盖旧截图；新证据另存并注明时间/设备/数据态。

## Acceptance criteria

- [ ] 先补消息/回答状态与原有复制、反馈、朗读、来源、追问动作断言，再实现主体视觉。
- [ ] 通过隔离主题和假生成状态截图覆盖空会话、用户消息、思考/回答、来源、追问及失败/生成中状态，与原型同视口对照。
- [ ] Markdown、滚动、回答修订和操作目标保持；手机/平板无遮挡，触达区 ≥44dp。
- [ ] 不使用用户 Key/数据；定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；锁定消息状态呈现及回答操作/来源目标。
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
- Exception type: None; TDD is required.
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 有回答内容的截图/仪器测试使用隔离假数据和测试替身；不调真实 LLM 服务或记录正文日志。
- 当前工作树已含对话未提交改动，先审阅增量，不重置/覆盖既有文件内容。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；必须记录有效对话页真截图路径、设备/视口、状态与隔离数据来源。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 任务边界与隔离验证限制已确认。
- In progress: 等待 010。
- Blocker: 共用外壳任务未验收；对话有内容数据需隔离构造。
- Unblock condition: 010 verified，且验证夹具可用。
- Next steps: 先 RED，完成内容区后由 023 接续输入与弹层。
- Watch-outs: 不把错误图片或首页截图作为聊天验收；不写入用户 Key/主题。
