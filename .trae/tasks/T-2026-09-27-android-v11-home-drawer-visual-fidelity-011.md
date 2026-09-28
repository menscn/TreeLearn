# Treelearn Harness Task: 首页抽屉视觉复刻

id: T-2026-09-27-011
title: 首页抽屉视觉复刻
template_version: 2
task_type: implementation
status: verifying
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

- Parent summary: 复刻现有 Android 首页抽屉对应的 V1.1 视觉，不增加抽屉入口或新能力。
- Expected outcome: 标题/关闭、月历热力卡、最近学习行、空态及遮罩层级与原型同状态对齐。
- User choice and custom additions: 演示数据只用于理解；截图中的有数据状态须在隔离环境构造，不写用户设备。
- Risks: 当前设备无主题和统计数据，不能只凭空态截图推断数据态通过。

## Goal

首页抽屉空态与有数据态视觉对应 V1.1，同时保持现有统计、最近主题导航和关闭行为。

## Scope

### In scope
- `HomeDrawerScreen.java`、`drawer_heatmap.xml` 及抽屉专属布局/绘制代码和测试。

### Out of scope
- 首页主体、统计详情页、抽屉导航目标、数据聚合与持久化。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 调整抽屉视觉、留白和卡片呈现；关闭、统计详情跳转、最近主题点击目标不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `MainActivity` 打开/关闭 `HomeDrawerScreen`；`HomeDrawerPresenter` 提供同源统计与最近主题状态。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（交互需要回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 抽屉布局视觉，不改变入口、导航、状态或统计口径。
- Authoritative code/config/migration evidence: `HomeDrawerScreen.java`、`drawer_heatmap.xml`、`HomeDrawerPresenter`。
- Documentation surfaces checked: `android/README.md` 抽屉职责与 `Docs/spec/SPEC-2026-09-27-android-v11-full-screen-fidelity.md`。
- Documentation files updated: 如 README 的当前视觉/交互陈述发生变化则同任务更新；否则记录 none 理由。
- Producer/consumer/sibling/compatibility paths checked: 首页按钮、返回键、关闭按钮、统计卡与最近主题行。
- Superseded phrases/examples reverse-searched: 抽屉标题/滚动/热力图布局的当前态说明。
- `documentation impact: none` reason and evidence, if applicable: 若功能路径与当前文档陈述均未改变，记录差异搜索命令及结果。
- Historical/PRD handling: 不改带日期历史评审。

## Acceptance criteria

- [ ] 新增能复现旧布局 Gap 的失败断言，再实现；空态及隔离有数据态截图与原型对应状态逐项对照。
- [ ] 7×6 热力卡、关闭按钮、最近学习行和抽屉遮罩无裁切/遮挡；手机和平板横屏均检查。
- [ ] 统计点击仍进入详情，最近主题仍进入原主题，返回键优先关闭抽屉；可点区域 ≥44dp。
- [ ] 定向测试、lint、token guard、Java-only guard 和启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `HomeDrawerScreenInstrumentedTest.emptyStatsKeepTheHeatCardButHideCalendarCells` added the accepted empty-state contract.
- Command: `android\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac --rerun-tasks`
- Exit code: 1
- Observed failure: The new test could not resolve `R.id.drawer_heatmap_empty` because the empty-state view did not exist in the drawer layout.
- Failure reason validated: The compile failure was the missing production view contract, not the Android test runner; after aligning imports, this was the only compile error.

### GREEN — minimum implementation
- Implementation: Kept the heat card visible for an empty stats snapshot, added an in-card empty message, hid weekdays/cells for empty data, and marked future/month-end HomeStats cells transparent so the existing HeatmapView skips them.
- Command: `android\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac --rerun-tasks`
- Exit code: 0
- Additional verification: `android\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.HomeStatsTest --tests com.treelearn.app.ui.StatsDetailPresenterTest --rerun`; targeted `adb shell am instrument -w -r -e class com.treelearn.app.HomeDrawerScreenInstrumentedTest com.treelearn.test/androidx.test.runner.AndroidJUnitRunner` both exit 0.
- Passing evidence: targeted drawer instrumentation reports 1 test / 0 failures / 0 errors / 0 skipped; HomeStats and StatsDetailPresenter unit tests pass.
- Regression evidence: presenter navigation and stats source tests remain unchanged; no drawer route or aggregation code changed.

### REFACTOR — optional
- Cleanup: Reused existing HeatmapView and HomeStats contracts; no second statistics source or new dependency was introduced.
- Regression evidence: Empty card, future-cell transparency and minimum 44dp close/topic targets remain covered by layout and targeted assertions.

### TDD or sizing exception — only with prior user approval
- Exception type: None; TDD is required。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 使用测试专用/隔离数据生成有数据截图；不对用户设备写入原型演示主题或活动记录。

## Verification report

- Verdict: PARTIAL
- Evidence: Empty drawer card contract is device-verified; HomeStats future/month-end mapping is JVM-verified; heatmap index adjudication is recorded in `artifacts/evidence/T-2026-09-27-011-heatmap-index-adjudication.txt`; shell phone drawer screenshot is `artifacts/evidence/android-home-drawer-2026-09-27.png`.
- Changed/deleted assertion rationale and equivalent behavior coverage: The old behavior drew all zero-valued cells even for an empty history; the new assertion proves the prototype's empty message replaces the grid while the card remains navigable. Existing HeatmapLayout tests prove the consumer is column-major, matching HomeStats's first-day offset plus day index; no production fix is warranted for the verifier's row-major allegation.
- Gaps: Isolated non-empty drawer screenshot and real sw600dp drawer screenshot are still unavailable on the current keyless/empty device; close/statistics/topic interactions are covered by existing presenter tests but not re-run as a dedicated UI class.
- Risks: The drawer panel's full visual comparison remains limited until an isolated data fixture can be rendered on-device; the independent verifier report remains REJECT because its index interpretation conflicts with the producer/consumer tests and has been explicitly adjudicated here.

## Handoff notes

- Completed: 任务边界已确认。
- In progress: 空态结构与未来日期透明语义实现完成，等待独立 verifier 只读复核。
- Blocker: 010 整体仍 PARTIAL；非空/平板截图缺口保留。
- Unblock condition: verifier 接受当前实现，并补齐或明确接受隔离截图缺口。
- Next steps: 收口 011 后继续学习库/分支导航视觉切片。
- Watch-outs: 抽屉测试数据不能写入用户数据目录。
