# Treelearn Harness Task: 模型设置视觉复刻

id: T-2026-09-27-018
title: 模型设置视觉复刻
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-settings-home-visual-fidelity-016.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 模型设置页按 V1.1 对齐模型分工、选择器与思考预算状态。
- Expected outcome: 各模型角色、当前配置和思考开关/预算清晰对应原型。
- User choice and custom additions: 沿用现有模型和思考配置，不新增模型能力或持久化字段。
- Risks: 控件外观不得改变现有模型分配与保存语义。

## Goal

模型设置与思考选项视觉复刻原型，同时保留当前模型配置和选择行为。

## Scope

### In scope
- `ModelSettingsScreen.java`、`ModelThinkingPicker.java` 与模型设置专属资源/测试。

### Out of scope
- 模型调用、模型目录、后端契约、默认档位与生成参数。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 模型卡/思考预算控件视觉和布局变化；角色绑定、当前值、启停和提交动作不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ModelSettingsScreen` 消费 `ModelSettingsPresenter` 状态并转发修改；`MainActivity` 负责设置子页入口/返回。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（配置交互需回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 仅模型设置 UI，不改模型调用参数或配置字段。
- Authoritative code/config/migration evidence: `ModelSettingsScreen.java`、`ModelSettingsPresenter`、`ModelThinkingPicker.java`。
- Documentation surfaces checked: `android/README.md` 模型设置和设置路由说明。
- Documentation files updated: 若配置能力和路由不变，记录 none 与搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 模型角色、切换选择、思考预算与返回。
- Superseded phrases/examples reverse-searched: 无；确认未改模型档位文案契约。
- `documentation impact: none` reason and evidence, if applicable: 纯视觉变化且当前说明准确时适用。
- Historical/PRD handling: 不改历史说明。

## Acceptance criteria

- [ ] 先增加角色/当前模型/思考预算状态和选择动作断言，再实现布局。
- [ ] 测试配置下截图覆盖默认值、打开 picker、已选择和禁用状态，与原型对应。
- [ ] 每个角色仍保存到原配置位置；取消/返回不提交，手机和平板布局及 ≥44dp 触达区通过。
- [ ] 定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；模型分工和思考预算选择行为。
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

- 使用无敏感配置的测试状态，不在截图或日志中泄漏 Key。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；记录角色、选择状态、设备和截图。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 任务边界已确认。
- In progress: 等待 016。
- Blocker: 设置中心尚未验收。
- Unblock condition: 016 verified。
- Next steps: 先 RED，再对照原型复刻模型设置页。
- Watch-outs: 不更改参数与配置存储契约。
