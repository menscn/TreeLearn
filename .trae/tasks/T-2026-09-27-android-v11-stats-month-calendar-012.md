# Treelearn Harness Task: 统计详情月历视觉复刻

id: T-2026-09-27-012
title: 统计详情月历视觉复刻
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 170
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-paper-backdrop-shell-010.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 统计页按原型显示累计指标卡在前、当前月 7 列月历热力卡在后。
- User choice and custom additions: 用户选择 A；只改变统计详情可视月份，历史数据与统计聚合不删除、不改写，较早日期不显示在此页。
- Risks: 现有十周网格已消费共享聚合，不能改变抽屉口径或底层记录。

## Goal

统计详情布局和月历视觉匹配 V1.1，同时继续使用现有统计聚合与累计指标。

## Scope

### In scope
- `StatsDetailScreen.java`、`StatsDetailPresenter.java`、`screen_stats_detail.xml`、统计页用热力视图/布局代码与定向测试；更新 `android/README.md` 的统计页当前态描述。

### Out of scope
- 数据库、聚合规则、抽屉热力图窗口、历史数据清理与任何统计 API。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 将详情页十周可视窗口改为当前月日历并重排卡片；累计提问/消息/连续天数与活跃日判定保持。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `StatsDetailPresenter` 消费现有 stats store；`HomeDrawerScreen` 与详情页共享 `StatsAggregator` 结果但使用各自显示窗口。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（改变可见日期范围，须测边界与聚合回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 详情页由近十周改为当月日历；底层历史和聚合事实不变。
- Authoritative code/config/migration evidence: `StatsDetailPresenter.java`、`screen_stats_detail.xml`、统计热力视图与测试。
- Documentation surfaces checked: `android/README.md` 第 455 行附近统计详情说明、抽屉统计说明及全屏规格。
- Documentation files updated: `android/README.md`。
- Producer/consumer/sibling/compatibility paths checked: 抽屉月卡、统计详情入口、聚合消费者和当前月份边界。
- Superseded phrases/examples reverse-searched: “近 10 周”“70 格”详情页表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留历史截图与报告时间点，不将其当作现状。

## Acceptance criteria

- [ ] 先为指标顺序、月份首尾/跨月边界和可见格数增加失败测试，再实现当月 7 列日历。
- [ ] 指标卡在热力卡之前；当前月日期及活跃颜色与隔离统计样本正确对应，无前后月日期冒充活动日。
- [ ] 现有统计聚合、指标值和抽屉月历不变；手机与平板横屏截图无溢出，热力格触达区/无障碍说明合理。
- [ ] README 不再把详情页描述成近十周；定向测试、lint、token guard、Java-only guard 和启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `StatsDetailPresenterTest` changed from the obsolete 70-day state to current-month fields and leap-February boundary assertions.
- Command: `android\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.StatsDetailPresenterTest --rerun`
- Exit code: 1
- Observed failure: Test compilation reported missing `monthIntensities`, `monthFirstWeekday` and `monthDays` fields on `StatsDetailPresenter.State`.
- Failure reason validated: The failure was caused by the missing month-calendar contract, not by the existing aggregator or test runner.

### GREEN — minimum implementation
- Implementation: Added current-month mapping to `StatsDetailPresenter`, a responsive `MonthCalendarView` with transparent leading/trailing/future cells, metrics-first/card-second XML, weekday labels, empty-card rendering, and README synchronization; `StatsAggregator` and drawer mapping remain unchanged.
- Command: `android\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.StatsDetailPresenterTest --rerun`
- Exit code: 0
- Additional verification: `android\gradlew.bat :app:lintDebug --rerun`; `python -X utf8 .trae/scripts/dev/check_design_tokens.py`; `powershell -NoProfile -File android\scripts\no-kotlin-check.ps1`; targeted `adb shell am instrument -w -r -e class com.treelearn.app.HomeBackdropInstrumentedTest com.treelearn.test/androidx.test.runner.AndroidJUnitRunner` all exit 0.
- Passing evidence: `StatsDetailPresenterTest` and the full JVM unit suite pass; lint, token guard and Java-only guard pass; the empty-state device screenshot is `artifacts/evidence/android-stats-detail-empty-2026-09-27-v2.png`.
- Regression evidence: existing `HomeStatsTest`, `HomeDrawerPresenterTest` and `StatsAggregatorTest` remain green; the targeted shell instrumentation is 3/3 green.

### REFACTOR — optional
- Cleanup: Kept the existing `HeatmapView` and `StatsAggregator` contracts for drawer/history surfaces; the detail page owns only its responsive month renderer and display window.
- Regression evidence: `android/README.md` no longer describes the detail page as a ten-week/70-cell view; no new dependency or token was introduced.

### TDD or sizing exception — only with prior user approval
- Exception type: None; TDD is required。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 月份边界以 Android 当前本地日历和既有统计日期语义为准；不改底层日期数据。

## Verification report

- Verdict: PARTIAL
- Evidence: Current-month, future-cell, month-end and leap-February behavior is covered by `StatsDetailPresenterTest`; empty-state visual evidence is `artifacts/evidence/android-stats-detail-empty-2026-09-27-v2.png`; lint/token/Java-only and targeted instrumentation pass; README line 454 now describes drawer 7x6 and detail current-month 7-column rendering.
- Changed/deleted assertion rationale and equivalent behavior coverage: The 70-day detail assertion was replaced because the accepted V1.1 decision is current-month-only; drawer/home 14-day/month aggregation tests remain unchanged and continue to cover the shared producer.
- Gaps: No isolated non-empty production-data device screenshot is available on the current keyless/empty device, and a real sw600dp stats screenshot is still outstanding; do not claim full 1:1 visual verification from the empty state alone. The earlier README wording gap is resolved by the current line-454 evidence.
- Risks: `-1.0f` is an internal transparent-cell sentinel consumed only by `MonthCalendarView`; callers must not feed it to the drawer `HeatmapView`.

## Handoff notes

- Completed: 用户统计窗口决策已落规格。
- In progress: 实现完成，等待独立 verifier 只读复核。
- Blocker: 010 整体仍 PARTIAL；统计页非空/平板截图缺口保留。
- Unblock condition: verifier 接受当前实现，并补齐或明确接受截图缺口。
- Next steps: 复核 012 后推进首页抽屉、学习库、树导航与设置切片。
- Watch-outs: 不删除早于当月的记录，不把抽屉窗口改为详情页窗口。
