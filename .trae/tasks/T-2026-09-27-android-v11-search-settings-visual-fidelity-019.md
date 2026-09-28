# Treelearn Harness Task: 搜索服务设置视觉复刻

id: T-2026-09-27-019
title: 搜索服务设置视觉复刻
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 130
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-settings-home-visual-fidelity-016.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 搜索服务配置和引擎详情按 V1.1 复刻现有页面状态。
- Expected outcome: 服务列表、当前启用状态、引擎选择/详情和反馈状态对应原型。
- User choice and custom additions: 保留已有服务及配置入口，不新增服务或外部调用能力。
- Risks: 设置只改视觉；不能触发真实搜索或更改服务优先级。

## Goal

搜索服务与引擎详情呈现匹配原型，现有配置读写和返回行为保持。

## Scope

### In scope
- `SearchServiceSettingsScreen.java` 及搜索服务页专属资源/测试。

### Out of scope
- 搜索算法、后端/第三方服务、服务契约、网络请求与优先级规则。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 列表和详情控件视觉变化；服务选择、启用/禁用、保存/返回行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `SearchServiceSettingsScreen` 消费 `SearchSettingsPresenter`；搜索执行端不属于本屏写集。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（保存与切换动作需回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 搜索服务视觉变更，不更改服务选择或调用链。
- Authoritative code/config/migration evidence: `SearchServiceSettingsScreen.java`、`SearchSettingsPresenter`。
- Documentation surfaces checked: `android/README.md` 搜索设置和生产搜索调用说明。
- Documentation files updated: 若配置流程/服务集保持不变，记录 none 与代码搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 设置入口、服务列表、引擎详情、保存/返回和搜索消费者。
- Superseded phrases/examples reverse-searched: 无，确认未变更服务名称或能力。
- `documentation impact: none` reason and evidence, if applicable: 纯视觉变更时适用。
- Historical/PRD handling: 不改历史材料。

## Acceptance criteria

- [ ] 先为服务选中/关闭、引擎详情和保存返回补断言，再实施视觉差异。
- [ ] 使用隔离配置截图对照原型列表、详情、空/错误状态；无溢出/遮挡。
- [ ] 服务开关与选择值仍写回原设置端口；不发起真实搜索请求；手机/平板及 ≥44dp 触达区通过。
- [ ] 定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；配置状态显示与变更流程。
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

- 截图使用隔离配置；仅测试环境模拟启用/禁用与详情。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；记录配置状态与设备截图。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 任务边界已确认。
- In progress: 等待 016。
- Blocker: 设置中心尚未验收。
- Unblock condition: 016 verified。
- Next steps: 先 RED，再实现列表/详情视觉并回归设置。
- Watch-outs: 不增服务、不触发生产搜索。
