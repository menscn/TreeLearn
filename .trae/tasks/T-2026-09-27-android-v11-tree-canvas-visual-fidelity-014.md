# Treelearn Harness Task: 学习树画布视觉复刻

id: T-2026-09-27-014
title: 学习树画布视觉复刻
template_version: 2
task_type: implementation
status: verifying
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

- Parent summary: 对齐 V1.1 学习树画布的根/枝/叶层级、节点卡片、画布视口与空/有内容状态。
- Expected outcome: 单主题新树清晰可识别，初始缩放与原型阅读尺度对应。
- User choice and custom additions: 画布和分支生产路由不变；有数据视觉验证只能用隔离样本。
- Risks: 当前设备无主题，既有截图不能证明单主题状态；缩放改动可能影响平移、缩放和节点点击。

## Goal

在手机和平板上按原型复刻学习树画布及节点弹层，首次进入时信息清晰且原手势不回归。

## Scope

### In scope
- `TreeCanvasScreen.java`、`TreeCanvasView.java`、`GridBackdrop.java`、`screen_tree*.xml`、`view_node_popup.xml`、画布定向测试与隔离截图 fixture。

### Out of scope
- 树布局领域算法、数据结构、节点增删改、持久化和导航页内容。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 调整画布留白、初始视口、节点尺寸与视觉；平移/缩放/选择/弹层目标保持。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `TreeCanvasScreen` 持有画布视图并转发手势；`TreeCanvasView` 消费 `TreeCanvasViewState` 与现有布局结果；`MainActivity` 保持进入/返回目标。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（画布触控行为须回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 画布显示比例/视口变化；树数据与布局拓扑不变。
- Authoritative code/config/migration evidence: `TreeCanvasScreen.java`、`TreeCanvasView.java`、`TreeCanvasViewState`、`screen_tree*.xml`。
- Documentation surfaces checked: `android/README.md` 树画布职责及全屏规格。
- Documentation files updated: 仅视口/绘制视觉变化且职责陈述不变时记录 none 及搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 新树空态、已有树、节点弹层、手机和平板横屏。
- Superseded phrases/examples reverse-searched: 旧截图中“单主题树缩至不可识别”的判断更新为本次实测状态。
- `documentation impact: none` reason and evidence, if applicable: 视图尺度变化不修改业务/路由契约时可不改文档。
- Historical/PRD handling: 旧截图保留历史标记，不删除或覆盖。

## Acceptance criteria

- [x] 已补初始视口、节点点击、缩放边界回归测试；负变异确认旧 fit 行为会使首屏定位断言失败。
- [x] 已生成并检查手机单主题、节点弹层、空树和 sw600dp 横屏四张隔离截图；截图使用原型 0.92 阅读缩放与底部 180 偏移，不写入用户数据库。
- [ ] 平移、双指缩放、节点选择、返回和空树状态通过；手机与 sw600dp 横屏均覆盖。当前设备仅拖动回调用例失败，弹层用例通过。
- [x] 触达区 ≥44dp；定向测试、lint、token guard、Java-only guard 和启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: TreeCanvasGeometryTest.firstLoadUsesPrototypeReadableScaleAndCentersTheCurrentBranch；临时将首次 initialTransform 替换为 layout.fit。
- Command: android\\gradlew.bat -p android :app:testDebugUnitTest --tests com.treelearn.app.ui.TreeCanvasGeometryTest
- Exit code: 1
- Observed failure: TreeCanvasGeometryTest > firstLoadUsesPrototypeReadableScaleAndCentersTheCurrentBranch FAILED at TreeCanvasGeometryTest.java:112; 13 tests completed, 1 failed。
- Failure reason validated: 负变异只改变首次视口定位，恢复原实现后同一目标测试退出码为 0；完整输出见 artifacts/evidence/T-2026-09-27-014-red.txt。

### GREEN — minimum implementation
- Implementation: 首次进入使用 0.92 阅读缩放并将当前分支定位到原型底部阅读偏移；增加回到当前分支工具栏动作、双行标题、摘要槽位、原型节点牌与淡化非当前枝干，同时保留旧路由和显式适应屏幕行为。
- Command: android\\gradlew.bat -p android :app:testDebugUnitTest --tests com.treelearn.app.ui.TreeCanvasGeometryTest
- Exit code: 0
- Passing evidence: TreeCanvasGeometryTest、TreeCanvasPresenterTest、AndroidTest Java 编译、lint、设计 token guard、Java-only guard 和 Android Activity 启动均退出码 0；完整命令输出见 artifacts/evidence/T-2026-09-27-014-green.txt。
- Regression evidence: connectedDebugAndroidTest 最终完成 243 tests / 22 failures / 0 errors / 0 skipped；TreeCanvasVisualEvidenceInstrumentedTest 通过并生成四张 PNG，节点弹层测试通过，既有拖动回调测试仍失败；索引见 artifacts/evidence/T-2026-09-27-014-screenshots.txt，套件输出见 artifacts/evidence/T-2026-09-27-014-screenshot-final2-run.txt。

### REFACTOR — optional
- Cleanup: 保留 TreeCanvasActions.onCenterCurrent 默认兼容实现，未改树布局算法、数据模型、持久化或生产路由。
- Regression evidence: TreeCanvasPresenterTest.fitRestoresTheCompleteBoundsBottomAligned 与既有画布回调路径仍通过；lint 和 Java-only guard 通过。

### TDD or sizing exception — only with prior user approval
- Exception type: None; TDD is required.
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 通过测试专用隔离数据构造内容树，不向用户数据库写演示内容。
- 保留平板横屏现有最大画布宽度和已有网格层行为。

## Verification report

- Verdict: PARTIAL
- Evidence: artifacts/evidence/T-2026-09-27-014-red.txt、artifacts/evidence/T-2026-09-27-014-green.txt、artifacts/evidence/T-2026-09-27-014-device.txt、artifacts/evidence/T-2026-09-27-014-screenshots.txt、artifacts/evidence/tree-canvas-phone-single.png、artifacts/evidence/tree-canvas-phone-popup.png、artifacts/evidence/tree-canvas-phone-empty.png、artifacts/evidence/tree-canvas-sw600-landscape.png；定向测试、编译、lint、token guard、Java-only guard、Activity 启动、节点弹层设备测试和隔离截图已有可复核输出。
- Changed/deleted assertion rationale and equivalent behavior coverage: fit 仍由显式 onFit 保留；首次加载改为原型阅读定位；新增首屏缩放与当前分支 X/Y 断言、工具栏/标题/摘要槽位断言；未删除旧手势语义断言。
- Gaps: 当前截图是测试专用内存 TreeSnapshot，不是生产数据库主题；尚未建立原型 HTML 与 Android PNG 的自动像素差异阈值；设备拖动回调仍失败。
- Risks: connectedDebugAndroidTest 的 22 个失败主要来自前序首页/生成流程与设备状态；本任务可直接归因的失败是 TreeCanvasDeviceInstrumentedTest.theRealTouchStreamReachesTheCanvasCallbacks，需单独修复或取得明确豁免后再验证。

## Handoff notes

- Completed: 画布生产代码、布局资源、节点弹层、定向测试、RED/GREEN 证据、四类隔离截图与设备回归已完成；任务状态保持 verifying。
- In progress: 处理或隔离设备拖动回调失败，并补原型 HTML 与 Android PNG 的自动对照阈值。
- Blocker: TreeCanvasDeviceInstrumentedTest.theRealTouchStreamReachesTheCanvasCallbacks 未通过；自动像素差异仍未建立。
- Unblock condition: 拖动回调通过或记录经用户批准的替代证据，并完成原型/Android 截图对照后再由 verifier 决定是否转 verified。
- Next steps: 保留四张截图作为视觉基线，不写入用户数据库；先定位真实触控回调失败，再进行最终验收。
- Watch-outs: 不把当前无主题设备截图报告成有数据通过；不把全量设备套件的前序失败归因到本任务。
