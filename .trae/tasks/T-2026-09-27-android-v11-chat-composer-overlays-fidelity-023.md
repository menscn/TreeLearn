# Treelearn Harness Task: 对话输入与弹层视觉复刻

id: T-2026-09-27-023
title: 对话输入与弹层视觉复刻
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
depends_on: [T-2026-09-27-android-v11-chat-content-visual-fidelity-022.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 对齐对话输入栏、模型选择、分支列表、引用/附件/搜索面板、选中文字菜单和确认层。
- Expected outcome: 所有当前 Android 已提供的弹层状态对应原型，维持当前页面/路由组织。
- User choice and custom additions: 不因原型视觉新增能力、路由、权限或真实 Key；现有交互结果不变。
- Risks: `ChatScreen` 与部分布局在当前工作树有未提交改动，必须在任务 022 后串行接手并保留基线内容。

## Goal

对话输入与当前所有弹层达到 V1.1 视觉层级，同时保留提交、选择、附件/检索面板与返回行为。

## Scope

### In scope
- `ChatScreen.java` 的输入/弹层绑定，`screen_chat_body.xml` 输入区，`view_model_chip.xml`、`popup_model_picker.xml`、`popup_child_branches.xml`、`popup_word_action.xml`、`panel_search_picker.xml`、`overlay_fullscreen_editor.xml`、`ChildBranchPopup.java`、`WordMenuPopup.java` 与对应测试。

### Out of scope
- 对话消息主体（任务 022）、附件/搜索业务、模型参数、剪贴板/权限和新增面板功能。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 调整输入栏及弹层形态、尺寸、遮罩和位置；提交、模型选择、分支选择、引用/附件/搜索、确认与取消结果不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatScreen` 绑定现有 composer 和面板；`ChildBranchPopup`/`WordMenuPopup` 由当前对话动作调用；搜索/附件动作沿用当前 presenter/平台入口。
- Native bridge or independent-client compatibility/update requirements: 不修改原生桥或独立客户端。
- Behavior-neutral non-applicability reason, if applicable: 不适用（输入和弹层动作需逐项回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话输入及弹层视觉变化，不新增入口或更改动作。
- Authoritative code/config/migration evidence: `ChatScreen.java`、现有 presenter/actions、相关 popup/panel 布局。
- Documentation surfaces checked: `android/README.md` 对话输入/弹层与权限/路由说明。
- Documentation files updated: 若入口、权限和功能均不变，记录 none 及现状搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 输入提交、模型/分支选择、引用、附件、搜索、文字菜单、确认取消、系统返回。
- Superseded phrases/examples reverse-searched: 无；不得把原型独有能力写成生产功能。
- `documentation impact: none` reason and evidence, if applicable: 仅呈现变化且文档路径事实准确时适用。
- Historical/PRD handling: 保留旧截图并将新截图作为本次验证证据。

## Acceptance criteria

- [ ] 在改动前增加输入和弹层动作回归断言；任务 022 验收后串行执行，记录本任务前基线避免覆盖既有变更。
- [ ] 同视口截图覆盖键盘隐藏/显示、模型选择器、分支/引用/附件/搜索面板、选中文字菜单及确认层，与原型对应。
- [ ] 输入提交、取消/返回、焦点、键盘和弹层遮罩/位置正确；所有点击区域 ≥44dp，无溢出/遮挡。
- [ ] 不调用真实服务或使用用户密钥；定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；锁定输入发送及每类弹层的打开/关闭目标。
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

- 用户设备无有效 Key；所有面板视觉/有数据状态使用测试替身或隔离环境。
- 与 022 共享 `ChatScreen.java`、`screen_chat_body.xml`，不得并行修改。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；截图标注键盘、焦点、弹层和视口状态。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 输入与弹层范围已确认。
- In progress: 等待 022。
- Blocker: 对话主体任务尚未验收，且共享文件必须串行。
- Unblock condition: 022 verified。
- Next steps: 记录新基线后先 RED，再分组复刻弹层并回归操作。
- Watch-outs: 不增加原型独有能力，不覆盖用户工作树既有聊天改动。
