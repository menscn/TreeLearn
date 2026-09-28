# Treelearn Harness Task: 分支导航视觉复刻

id: T-2026-09-27-015
title: 分支导航视觉复刻
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 140
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-paper-backdrop-shell-010.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 对齐原型分支导航中的祖先、当前分支、子分支列表与层级关系。
- Expected outcome: 分支行、层级、空子分支及返回状态能按原型识别。
- User choice and custom additions: 沿用现有主题/分支生产入口和导航结果，不增加新路由。
- Risks: 当前设备无有效主题，需隔离分支数据验证有内容状态。

## Goal

分支导航页面与原型结构一致，同时保持祖先/子分支点击目标和返回路径。

## Scope

### In scope
- `NavigationScreen.java`、`screen_navigation*.xml`、`item_navigation_row.xml`、`item_child_branch.xml` 与对应测试。

### Out of scope
- 树画布渲染、分支拓扑算法、数据查询及创建/删除/切换业务规则。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 重排导航标题、层级行、分支卡和空状态；选祖先/子分支及返回目标不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `NavigationScreen` 消费 `NavigationViewState` 并转发事件；`NavigationPresenter` 和 `MainActivity` 决定分支跳转/返回。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（点击行为需执行回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 仅改变导航页呈现，不改层级数据或导航结果。
- Authoritative code/config/migration evidence: `NavigationScreen.java`、`NavigationPresenter`、`screen_navigation*.xml`、item layouts。
- Documentation surfaces checked: `android/README.md` 分支导航入口描述及全屏规格。
- Documentation files updated: 若入口和返回说明不变，记录 none 与反向搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 当前分支、祖先、子分支、无子分支、系统返回。
- Superseded phrases/examples reverse-searched: 独立分支导航路由相关当前态陈述。
- `documentation impact: none` reason and evidence, if applicable: 视觉调整不改变入口/流程时可不改文档。
- Historical/PRD handling: 不改历史评审记录。

## Acceptance criteria

- [ ] 先为祖先/当前/子分支的渲染顺序与点击目标增加回归断言。
- [ ] 隔离样本下与原型同视口截图对应；层级、行高、标签和空态清晰，无遮挡/溢出。
- [ ] 祖先/子分支点击、系统返回、手机和平板横屏表现不回归，可点击区 ≥44dp。
- [ ] 定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；测试层级绘制与分支导航动作。
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

- 有数据截图必须使用隔离主题/分支，不改用户主题库。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；记录路径、状态、设备和截图。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 任务边界已确认。
- In progress: 等待 010。
- Blocker: 共用背景任务未验收。
- Unblock condition: 010 verified。
- Next steps: 解除依赖后先 RED，再复刻层级视图并回归跳转。
- Watch-outs: 不因视觉增加生产路由。
