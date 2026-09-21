# Treelearn Harness Task: 实现树 Canvas 与平板适配

id: T-2026-09-20-017
title: 实现树 Canvas 与平板适配
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
depends_on: [T-2026-09-20-navigation-archive-delete-014.md, T-2026-09-20-search-unread-reading-position-015.md, T-2026-09-20-pure-java-tree-layout-016.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 用原生 Canvas 呈现可缩放主题树，手机单栏、平板横屏双栏，并稳定连接导航/未读状态。
- Problem/background: 普通层级列表无法表达分支来源；复杂 View 树难以支撑大树缩放和命中。
- Users/scenarios: 用户拖动、双指缩放、点击叶/芽/标签、折叠/展开、适应屏幕和打开节点弹层。
- Expected outcome: 单 TreeCanvasView、Matrix 手势、命中、节点弹层、控制栏和平板双栏容器。
- Core flow and reversibility: 领域快照交给 layout；Canvas 绘制；屏幕点反变换命中；导航只传 branch UUID。
- Inputs/outputs: 输入 LayoutResult/ViewState；输出选择、折叠、fit、打开聊天等命令。
- State and exception handling: 窗口变化、折叠重排、无标签节点、弹层越界和手势冲突均有回退。
- Constraints and trade-offs: 44dp 热区；缩放边界；弹层按窗口 bounds 翻转；无装饰性嵌套卡片。
- AI recommendation and rationale: 单 View 绘制保证一致矩阵与性能，布局算法保持纯 Java。
- User choice and custom additions: 手机竖屏优先、兼容平板横屏。
- AI-autonomous decisions: 平板导航/列表与画布/内容双栏；手机按原型单栏切换。
- User-confirmation decisions: P1 树布局和设备适配。
- Acceptance evidence: instrumentation 手势测试、手机/平板截图和 canvas 非空像素检查。
- Facts: v2.5 原型与技术方案 §12/§13 是权威。
- Assumptions: 系统 Canvas 在 API 36 满足性能；`minSdk 28` 为声明兼容下限，未在 API 28 实机做性能验证（用户 2026-09-21 决定）。
- Unknowns: 最终字体资产取决于授权与 APK 体积。
- Risks: Matrix 命中误差、弹层遮挡、配置变化丢视口。
- Deferred: 复杂动画、3D 和桌面多窗口增强。
- Non-goals: 不重新实现布局算法或业务 Repository。
- Skipped non-critical questions and reason: 微动效以性能和可访问性为准，可后续调优。

## Goal

交付可操作、无重叠、在手机和平板均保持阅读上下文的树画布。

## Scope

### In scope
- Canvas draw、Matrix pan/zoom/fit、hit test、popup、折叠控制、双栏资源和 UI tests。

### Out of scope
- 树几何算法、数据删除逻辑和对话内容渲染。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增画布入口；保持 layout 输出、归档过滤和未读聚合不被 View 改写。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): TreeLayout/Search/Nav ViewState 生产；Canvas 发出 branch UUID；手机/平板窗口分支。
- Native bridge or independent-client compatibility/update requirements: Android Canvas/Matrix 是平台边界。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 手势、缩放范围、弹层行为和响应式布局。
- Authoritative code/config/migration evidence: TreeCanvasView、layout resources、UI tests 和截图。
- Documentation surfaces checked: PRD 树画布、v2.5 原型、技术方案 §12/§13。
- Documentation files updated: 可见交互变化时同步原型/PRD 当前态。
- Producer/consumer/sibling/compatibility paths checked: 手机/平板、字体放大、旋转、折叠/展开、搜索跳转、归档与未读。
- Superseded phrases/examples reverse-searched: 固定坐标、仅手机和点击区域小于 44dp。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 历史记录保留。

## Acceptance criteria

- [ ] 同一 UI 测试命令先 RED 后 GREEN，pan/pinch/tap/fit 与折叠命令在 API 36 设备通过（用户 2026-09-21：验收环境为 API 36/36.1）。
- [ ] Canvas 像素检查非空；关键节点、叶片和未读芽可见；屏幕点经逆矩阵命中正确 branch。
- [ ] 折叠/展开、新分支和摘要更新后操作节点屏幕位置稳定；fit 使用完整 bounds 且底部对齐。
- [ ] 手机竖屏无重叠；平板横屏双栏可同时导航和查看主内容；弹层始终在实际窗口内翻转。
- [ ] 字体放大、长标题和标签被省略时节点仍可通过至少 44dp 热区操作。

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

- 设备旋转保存领域锚点与矩阵参数，不保存 bitmap 或绝对 View 坐标。
- View 只消费 LayoutResult，不自行移动节点规避碰撞。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: OEM 手势差异和超大树绘制耗时。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 纳入 release 核心流加固。
- Watch-outs: 弹层定位必须使用 window insets 后的 bounds。
