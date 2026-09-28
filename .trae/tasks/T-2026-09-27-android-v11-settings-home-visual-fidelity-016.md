# Treelearn Harness Task: 设置中心视觉复刻

id: T-2026-09-27-016
title: 设置中心视觉复刻
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-paper-backdrop-shell-010.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 设置首页与 V1.1 中 Key/能力配置入口、分组和状态一致。
- Expected outcome: 设置首页层级、入口卡片与描述文字按原型对应。
- User choice and custom additions: 保留现有页面入口及能力，不展示或记录真实 Key。
- Risks: 设置入口连接多个子页，视觉调整不能改变点击目标或密钥脱敏。

## Goal

设置首页视觉与原型一致，所有现有能力入口和 Key 保护语义保持。

## Scope

### In scope
- `SettingsScreen.java`、`screen_settings*.xml`、设置首页专属 drawable/string 与测试。

### Out of scope
- Provider/Model/Search/Voice/File 子页内部视觉、Key 存储和配置业务规则。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 调整设置分组、入口布局和状态表现；保存/删除 Key、子页入口和返回方式保持。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `SettingsScreen` 消费 `SettingsViewState` 并转发 `SettingsPresenter`；`MainActivity` 负责设置一级/二级页路由。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（入口与 Key 状态须回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 仅调整设置首页视觉。
- Authoritative code/config/migration evidence: `SettingsScreen.java`、`SettingsPresenter`、`screen_settings*.xml`。
- Documentation surfaces checked: `android/README.md` 设置入口与 Key 脱敏事实。
- Documentation files updated: 若入口/能力不变，记录 none 并给出对应搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 每个现有子页入口、Key 掩码/缺失状态、返回一级设置。
- Superseded phrases/examples reverse-searched: 设置入口名称与层级的当前态描述。
- `documentation impact: none` reason and evidence, if applicable: 视觉变化不改变设置契约时适用。
- Historical/PRD handling: 不改历史文档。

## Acceptance criteria

- [ ] 先增加入口映射和 Key 掩码状态断言，再按原型调整设置首页。
- [ ] 同视口截图核对标题、入口顺序、分组、说明文字和状态；不得显示真实 Key。
- [ ] 所有原入口仍打开原子页并能返回；触达区 ≥44dp，phone/sw600dp 横屏无溢出。
- [ ] 定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；入口绑定、Key 掩码与设置路由保持。
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

- 不把密钥原文放入 ViewState、日志、测试输出或截图。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；记录各入口点击与返回、Key 缺失/已配置但脱敏状态截图。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 任务边界已确认。
- In progress: 等待 010。
- Blocker: 共用背景任务未验收。
- Unblock condition: 010 verified。
- Next steps: 先 RED，再完成设置首页视觉与入口回归。
- Watch-outs: T017–021 的子页任务依赖本任务并独立实现。
