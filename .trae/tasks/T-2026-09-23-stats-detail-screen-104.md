# Treelearn Harness Task: 统计详情页

id: T-2026-09-23-104
title: 统计详情页
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-home-drawer-stats-heatmap-099.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: false
depends_on: [T-2026-09-23-stats-daily-aggregation-100.md, T-2026-09-23-heatmap-custom-view-101.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页抽屉与统计热力图吸收；本任务交付全量统计页（热力图 + 累计指标卡），从抽屉「统计详情」入口进入。
- Problem/background: 抽屉只放摘要，全量热力图（近若干周）与指标卡需要独立页承载。
- Users/scenarios: 抽屉点「统计详情」→ 全量热力图 + 累计提问数/消息数/连续天数。
- Expected outcome: 统计详情页 + 空态；数据读 100 同一入口，View 复用 101。
- Core flow and reversibility: 独立 Screen，返回回到抽屉/首页；无数据空态。
- Inputs/outputs: 输入 100/101；输出页面、Presenter、测试。
- State and exception handling: 查询失败降级占位，不影响返回导航。
- Constraints and trade-offs: XML/View + 既有 Screen 导航模式；44dp；token 守门。
- AI recommendation and rationale: 指标卡数量本期 3 张（累计提问/消息/连续天数），留扩展位不给 token 指标占位（避免半成品入口）。
- User choice and custom additions: 无。
- AI-autonomous decisions: 热力图范围默认近 10 周，设计稿定稿后调整。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN Presenter 测试 + token 检查 + 回归。
- Facts: 数据层无 token/usage 列（2026-09-23 核实）。
- Assumptions: 100/101 verified；抽屉统计入口由 103 提供。
- Unknowns: 真机观感。
- Risks: 给 token 指标留占位导致半成品入口（明确禁止）。
- Deferred: Token 四指标（另立小批）；按树维度分布统计（intent 讨论中提出、未拍板入本批）。
- Non-goals: token 指标；树维度 top 统计。
- Skipped non-critical questions and reason: 周/月切换——热力图范围设计稿定稿后如需再加。

## Goal

交付统计详情页：全量热力图（近 10 周）+ 累计指标卡（提问数/消息数/连续天数）+ 空态。

## Scope

### In scope

- 统计详情页布局、Presenter、导航接线、测试。

### Out of scope

- 数据源（100）；热力 View（101）；抽屉容器（103）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 既有导航栈行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 入口为消费者 103 的抽屉；数据消费 100，View 复用 101。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增统计详情页。
- Authoritative code/config/migration evidence: 页面布局与 Presenter 代码。
- Documentation surfaces checked: V3.2 PRD。
- Documentation files updated: 由 105 统一回写。
- Producer/consumer/sibling/compatibility paths checked: 导航回归。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录（105）。

## Acceptance criteria

- [x] RED/GREEN：热力图渲染、指标卡数值、空态、查询失败降级。
- [x] 页面无 token 指标占位（grep 证据）。
- [x] 触达 ≥44dp、contentDescription、`check_design_tokens.py` 通过；Java-only guard + 测试退出码 0。

## TDD execution record

### RED — failing test first
- Test/scenario: `StatsDetailPresenterTest`（新增，JVM，仿 FileManagerPresenter 测试模式）——①聚合：source 给今天与 40 天前的活跃时间戳 → intensities 恰 70 格、today 落末格=1.0、40 天前格=1.0，totalQuestions/totalMessages/streakDays 原样透传（页面不二次计算）；②空库 → empty=true 且全空格子，不画假「0 天」；③source 抛异常 → failed=true 降级占位，不崩溃；④失败后重读成功 → 恢复正常数据。RED 阶段 `StatsDetailPresenter` 尚不存在，预期编译失败。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.StatsDetailPresenterTest"`
- Exit code: 1
- Observed failure: 编译失败——`StatsDetailPresenter` 不存在（含其 `Sink`/`StatsSource`/`State` 嵌套类型；T-104-red-2026-09-25.log）。
- Failure reason validated: 全部错误指向本片待实现的 presenter 契约，无一条指向既有行为。

### GREEN — minimum implementation
- Implementation: ①新 `ui/StatsDetailPresenter.java`（84 行）——`StatsSource` 端口直接接 `HomeStore::statsRaw`（100 的同一入口，零第二查询源），后台线程跑 `StatsAggregator`，页面只把 `activeDays` 映射到 70 格窗口（today 末格）、totals/streak 原样透传；空库→`empty`，读失败→`failed` 降级占位且可重试恢复；②新 `screen/StatsDetailScreen.java`（57 行）——只渲染不判定；③新 `layout/screen_stats_detail.xml`（87 行）——复用 T-101 `HeatmapView` grid 形态（tl_rows=7、tl_columns=70、tl_cellSize=10dp，HorizontalScrollView 承载）+ 三张 `bg_card` 指标卡 + 空态/失败行，返回键 44dp；④strings 八键；⑤`AppContainer` 暴露 `homeStore()`/`clock()`（单时钟所有者原则），`MainActivity` 加 `showStatsDetail()` 房间（入口由 T-106 抽屉接线）与返回链（统计页返回回首页）。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.StatsDetailPresenterTest"`
- Exit code: 0
- Passing evidence: 4/4（GREEN 同 RED 命令；T-104-green-2026-09-25.log BUILD SUCCESSFUL）。执行修正两处：测试初版把 streak 当透传入参断言（错——它是聚合器判定，改为按聚合器语义断言 1）；布局误用不存在的 `space_s5`（改 `space_s4`，token 守门零新增）。
- Regression evidence: 全量 JVM 单测 633/0/0（73 文件）；check EXITCODE=0；Java-only PASS（13 OK）；`check_design_tokens.py` 0 failed；无 token 占位 grep 0 命中；证据 `artifacts/evidence/T-104-stats-detail.txt`（CMD-1 至 CMD-10）。

### REFACTOR — optional
- Cleanup: 无独立轮——初稿的 HeatmapViewBridge 间接层当场删除，直接绑定。
- Regression evidence: 全量单测 633/0/0 同上。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 指标卡读 100 的聚合结果，不做页面内二次计算。

## Verification report

- Verdict: PASS
- Evidence: RED/GREEN 同命令 EXITCODE 1→0；单测 633/0/0；check 0；Java-only PASS；token 守门 0 failed；无 token 占位 grep 0 命中；生产行数实测 228（新三文件）+ AppContainer 标记归属 +15。汇总 `artifacts/evidence/T-104-stats-detail.txt`（CMD-1 至 CMD-10，命令+退出码内嵌）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无既有断言删除；测试初版 streak 断言（透传语义）在 GREEN 中修正为聚合器判定语义——与 AC「不做页面内二次计算」一致（streak 本就是 100 的聚合产物）。
- Gaps: 热力图范围「近 10 周」是 AI 自主决定值（任务书授权），设计稿定稿后可调（改 `WINDOW_DAYS` 与 `tl_columns` 两处）；指标卡仅 3 张（token 指标明确不做，grep 证据 CMD-7）；真机观感未验（用户自测项）。
- Risks: 70 格窗口映射是页面侧唯一计算（活跃日判定仍在聚合器），若与首页口径漂移只可能发生在映射层——由 `WINDOW_DAYS` 常量与测试锁定；`showStatsDetail()` 当前无生产调用方（抽屉 T-106 是其入口，属下一片交付），非死入口而是接线点。

## Handoff notes

- Completed: 统计详情页全链（presenter/屏/布局/strings/导航房间）交付并 verified；与首页热力条共享同一聚合判定。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: T-106（抽屉容器）接线「统计详情」入口 → T-105（PRD 快捷/正典边界回写）。
- Watch-outs: (1) 禁止 token 指标占位（AC，grep 已证）；(2) 106 接线时抽屉返回语义须与 `onBackRequested` 的 statsDetail 分支（回首页）一致；(3) 规模 228 超估算 150、超 200 软上限（警告级，布局 XML 占 87 行），如实记录不作例外申请。
