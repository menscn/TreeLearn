# Treelearn Harness Task: 实现纯 Java 树布局引擎

id: T-2026-09-20-016
title: 实现纯 Java 树布局引擎
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
depends_on: [T-2026-09-20-model-immutable-answer-domain-003.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 树画布几何必须从不可变领域快照推导，不持久化像素，并适配手机/平板视口。
- Problem/background: 将布局写进 View 会难以测试，持久化坐标又会在字体、折叠和窗口变化时失效。
- Users/scenarios: 用户查看多层分支、折叠/展开、切换节点、适应屏幕并观察未读芽点。
- Expected outcome: `:tree-layout` 的输入/输出模型、确定性布局、bounds、label placement 和 hit regions。
- Core flow and reversibility: 输入可见树/轮数/约束；输出几何；任意状态变化重新计算并以稳定节点修正视口。
- Inputs/outputs: 输入不可变 TreeSnapshot/ViewportConstraints；输出 branch/leaf/bud/label/leader/bounds/hit geometry。
- State and exception handling: 空树、深/宽树、极长标题、小视口和折叠祖先均返回有限有效几何。
- Constraints and trade-offs: 纯 Java、无 android.*、无随机全局状态；空间不足可省略标签但节点仍可命中。
- AI recommendation and rationale: 几何内核独立模块，Canvas 只绘制和做矩阵转换。
- User choice and custom additions: 手机竖屏优先，兼容平板横屏。
- AI-autonomous decisions: 根从底部向上；枝长由用户轮数推导并封顶；三级密度。
- User-confirmation decisions: P1 独立树布局引擎。
- Acceptance evidence: 纯 JVM golden/property tests 覆盖几何不变量和两类视口。
- Facts: 技术方案 §12 是权威。
- Assumptions: 字体测量通过接口注入，不依赖 Android Paint。
- Unknowns: 最终视觉参数可由 Canvas 任务微调。
- Risks: 标签碰撞、浮点不稳定、极端树 O(n²)。
- Deferred: 3D、物理动画和服务端布局。
- Non-goals: 不绘制 View、不处理手势或数据库。
- Skipped non-critical questions and reason: 美术曲线参数可在不破坏输出契约下迭代。

## Goal

提供确定、可测、与 Android View 解耦的树几何和命中布局。

## Scope

### In scope
- immutable layout input/output、几何算法、标签避让、bounds/hit areas、stable anchor delta 和 JVM tests。

### Out of scope
- Canvas 绘制、Matrix 手势、弹层和真实字体资源。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增派生几何；不写数据库、不改变分支领域顺序。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Branch/Unread snapshot 生产；TreeCanvasView 消费；手机/平板传不同 constraints。
- Native bridge or independent-client compatibility/update requirements: 无 Android 依赖是契约要求。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 布局输入、密度层级、枝长、标签与锚点算法。
- Authoritative code/config/migration evidence: `android/tree-layout` Java 源码和 JVM tests。
- Documentation surfaces checked: 技术方案 §12、PRD 树画布、v2.5 原型。
- Documentation files updated: 算法可见语义变化时同步技术方案/原型说明。
- Producer/consumer/sibling/compatibility paths checked: 空/单链/宽树/深树、折叠、归档过滤、未读聚合、两种视口。
- Superseded phrases/examples reverse-searched: 持久化坐标和 View 内布局。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 当前态同步。

## Acceptance criteria

- [ ] 同一 JVM 测试命令先 RED 后 GREEN；输出对相同输入确定且所有坐标/bounds 有限。
- [ ] 叶/枝/芽数量、枝长轮数、折叠后代与未读聚合符合输入，不把归档子树带入默认布局。
- [ ] 已放置标签互不重叠且不遮挡叶片；无法放置可省略标签但 hit region 仍存在且不小于交互下限。
- [ ] 手机竖屏和平板横屏 fixture 的 fit bounds、底部对齐与操作节点 stable anchor delta 正确。

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

- 所有集合按稳定 ID/领域顺序排序；禁止依赖 HashMap 遍历顺序。
- 性能测试至少覆盖代表性大树，并记录节点数与耗时，不以随机截图代替不变量测试。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 大树布局性能和标签策略。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 供 Canvas UI 使用。
- Watch-outs: 不得引用 android.graphics 类型。
