# Treelearn Harness Task: 首页迷你热力条外放

id: T-2026-09-23-102
title: 首页迷你热力条外放
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-home-drawer-stats-heatmap-099.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 130
shared_code_touched: true
depends_on: [T-2026-09-23-stats-daily-aggregation-100.md, T-2026-09-23-heatmap-custom-view-101.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页抽屉与统计热力图吸收；本任务把迷你热力条（最近 14 天 + 连续天数）外放到首页顶部问候区下方（用户 2026-09-23 拍板位置）。
- Problem/background: 学习热度属于激励型信息，应常驻首页而非藏进设置；现状首页无此承载位。
- Users/scenarios: 打开首页即见连续学习天数与最近 14 天热度。
- Expected outcome: 首页顶部问候区下方常驻热力条卡片；无记录时显示空态引导文案；点按进入抽屉（默认，可随设计稿直达统计页）。
- Core flow and reversibility: 首页加载时读 100 的聚合结果渲染；查询失败时卡片降级不阻塞首页其余内容。
- Inputs/outputs: 输入 100 聚合 + 101 View；输出首页布局与 Presenter 变更。
- State and exception handling: 空态文案不显示 0 误导；DB 失败卡片隐藏或降级占位。
- Constraints and trade-offs: XML/View + 既有首页 ScreenController/Presenter 模式；44dp 热区；触达 ≥44dp；contentDescription。
- AI recommendation and rationale: 卡片为独立 include 布局，Presenter 只加一个 ViewState 字段，降低与 103 的冲突面。
- User choice and custom additions: 用户拍板「热力图按照你的来，放入到顶部下方」。
- AI-autonomous decisions: 空态文案初稿（实现期可调整并记录）。
- User-confirmation decisions: 点按目标（抽屉 vs 直达统计页）留设计阶段。
- Acceptance evidence: RED/GREEN Presenter 测试 + token 检查 + 回归。
- Facts: 首页结构见 `app/ui/` 主页相关 Screen/Presenter；102 与 103 同触首页 Presenter/ViewState 面须串行。
- Assumptions: 100/101 verified。
- Unknowns: 真机观感（用户自测项）。
- Risks: 首页加载被统计查询拖慢（聚合走索引/限制行数）。
- Deferred: 热力条动画。
- Non-goals: 抽屉容器（103）；统计详情页（104）。
- Skipped non-critical questions and reason: 空态是否给「去提问」跳转按钮——留设计稿。

## Goal

首页顶部问候区下方常驻迷你热力条（最近 14 天 + 连续天数 + 空态），点击进抽屉。

## Scope

### In scope

- 首页布局增量、Presenter ViewState 字段、空态与降级、测试。

### Out of scope

- 抽屉容器与对话列表（103）；全量热力图（104）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 首页新建提问主流程与推荐主题区行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 首页 Presenter/ViewState 为共享面（与 103 冲突，串行）；消费 100/101。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首页新增热力条卡片。
- Authoritative code/config/migration evidence: 首页布局与 Presenter 变更。
- Documentation surfaces checked: V3.2 PRD 首页条目。
- Documentation files updated: 由 105 统一回写。
- Producer/consumer/sibling/compatibility paths checked: 首页现有测试回归。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录（105）。

## Acceptance criteria

- [ ] RED/GREEN：有数据渲染 14 格 + 连续天数；空数据显示空态不显示 0；查询失败降级不崩溃。
- [ ] 首页既有测试全部通过（回归证据）。
- [ ] 触达 ≥44dp、contentDescription、`check_design_tokens.py` 通过；Java-only guard + 测试退出码 0。

## TDD execution record

### RED — failing test first
- Test/scenario: `HomePresenterTest` 新增 4 例（有数据渲染 14 格+连续天数、空历史空态不显示 0、查询失败降级 HIDDEN 且页面可用、点击不做死跳转）；新建 `HomeStatsTest` 3 例（14 天映射、窗口外不点亮、empty 工厂）；`HomeWiringTest` 新增 2 例（raw reads 折叠、无行即空态）；`TestDoubles.FakeHomeData`/`WiringDoubles.Homes` 增补 stats 桩。
- Command: `gradlew :app:testDebugUnitTest --tests '*HomePresenterTest' --tests '*HomeStatsTest' --tests '*HomeWiringTest' --console=plain`
- Exit code: 1
- Observed failure: `:app:compileDebugUnitTestJavaWithJavac FAILED`（新类型 `HomeStats`/`HeatStrip`/`HomeData.stats()` 尚不存在，符号无法解析）

```
命令口径说明（2026-09-23 补记）：历史 RED 执行时仅含 '*HomePresenterTest' '*HomeStatsTest' 两个过滤
（HomeWiringTest 用例为 GREEN 阶段补充回归）；2026-09-23 补跑统一为含三个过滤的同一条命令，
RED 失败观察（符号缺失）不受影响。详见 artifacts/evidence/T-102-targeted-green-2026-09-23.txt。
```
- Failure reason validated: 失败确因测试先行引用未实现的生产类型，属预期 RED，非环境问题。

### GREEN — minimum implementation
- Implementation: `HomeStats`（14 天窗口映射+empty 语义）、`HomeViewState.HeatStrip` 三态（HIDDEN/EMPTY/VISIBLE）+ `withHeatStrip`、`HomePresenter.loadHeatStrip()`（挂在既有后台 load 内，try/catch 降级 hidden）、`HomeData.stats()` default 端口、`HomeStore.StatsRaw`+`SqliteHomeStore.statsRaw()`（StatsDao 三语句）、`HomeWiring.stats()`（StatsAggregator 折叠，clock.getZone() 切日）、`HomeActions.onHeatStripTapped()`（TODO(T-103) 空实现）、`home_heat_strip.xml` include 卡片（strip 形态 HeatmapView、token 色/尺寸、44dp、contentDescription）、`HomeScreen.renderHeatStrip`；另将 `AppContainer` 时钟 `Clock.systemUTC()`→`Clock.systemDefaultZone()`（millis 不变，仅让 T-100 切日按设备时区，避免东八区早上 8 点才翻日）。
- Command: `gradlew :app:testDebugUnitTest --tests '*HomePresenterTest' --tests '*HomeStatsTest' --tests '*HomeWiringTest' --console=plain`
- Exit code: 0
- Passing evidence: 定向补跑 `BUILD SUCCESSFUL in 26s` EXITCODE=0（`artifacts/evidence/T-102-targeted-green-2026-09-23.txt`）；历史 green-run4：481 tests, 0 failures, 0 errors；`HomePresenterTest` 21（含新增 4）、`HomeStatsTest` 3、`HomeWiringTest` 增 2 全过；日志 `android/app/build/green-run4.log`，证据副本 `artifacts/evidence/T-102-green.log`。全量复跑（2026-09-23 P0）：tests=593 failures=0（`artifacts/evidence/P0-baseline-2026-09-23.txt`）。
- Regression evidence: 同一全量绿跑覆盖首页既有测试（HomeSearchEntryTest 3、HomeViewStateTest 7 等）与提问主流程用例；后续 green-run5/6 因 T-087 在制 main/资源编译错误（`ProviderAdminPresenter.java`、resource linking）无法复跑全量，归因 git：该两文件均为他批次新增未跟踪/在制内容，非本任务触碰。
- Guard 证据：`gradlew lint` exit 0（lint-run.log）；`android/scripts/no-kotlin-check.ps1` exit 0 PASS；`python .trae/scripts/dev/check_design_tokens.py` EXITCODE=0。生产代码增量（非空非注释，实测）：195 行（新文件 HomeStats.java 22 + home_heat_strip.xml 48，其余为 11 个改动文件的 diff 增量，strings.xml/AppContainer 的 diff 已剔除他批次行）。

### REFACTOR — optional
- Cleanup: HeatStrip/HomeStats 工厂方法单行化（行为无关，-6 行）。
- Regression evidence: 语义与 green-run4 完全一致；全量复跑被 T-087 在制 RED 阻断，已如实记录。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 与 103 串行：二者同触首页 Presenter/ViewState 面。
- 统计查询放后台线程，结果经 ViewState 投递，禁止主线程 DB。

## Verification report

- Verdict: PARTIAL（verifier 2026-09-23 独立验收：功能与验收项全部达标且取证；两处待办——① 087 转绿后复跑全量测试与 lint（当前 `:app` 编译被 087 在制中间态阻断，git 归因清楚非本任务）；② 行数精算，见 Gaps）
- Evidence: verifier 独立清点测试 XML（2026-09-23 11:14 green-run4 时点）：481 tests / 0 failures / 0 errors，含新增 9 例（HomePresenterTest 21、HomeStatsTest 3、HomeWiringTest 15、HomeSearchEntryTest 3、HomeViewStateTest 7）；no-kotlin-check.ps1 EXIT 0；check_design_tokens.py EXIT 0（tokens=0 违规）。`:app:testDebugUnitTest` 与 lint 独立复跑 EXIT 1，32 处编译错误全部落在 087 在制文件（MainActivity/ProviderAdminPresenter/SqliteProviderAdminStore/SettingsScreen 及 AppContainer 的 087 段），git 归因非本任务。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除/削弱既有断言；新增断言覆盖三态状态机、14 格映射、空态无「0 天」、失败降级、点击不跳转、wiring 折叠与空态。
- **AppContainer 时钟专项结论（verifier）**：实际改动 1 行（`systemUTC()`→`systemDefaultZone()`）+2 注释；消费者仅 HomeWiring.stats()（getZone() 切日），GenerationCoordinator 只用 millis() 不受影响；与 sql.md 口径一致（落库 UTC millis、设备时区算自然日）。**判定：保留不回退**（回退会使东八区 8 点才翻日，属缺陷修复）；但与本任务原声明 `shared_code_touched: false` 冲突，front matter 已更正为 true。
- Gaps: ① ~~087 转绿后复跑全量测试与 lint~~ **已解决（2026-09-23 22:12）**：087 已 verified，P0 基线重建复跑 `:app:testDebugUnitTest :app:lintDebug` BUILD SUCCESSFUL / EXITCODE=0，单测清点 tests=593 failures=0 errors=0 skipped=0，证据 `artifacts/evidence/P0-baseline-2026-09-23.txt`（此前 32 处编译错误确认已随 087 收尾消失）；② 行数精算 **已解决（2026-09-23，有界口径）**：新文件判定值 72（`T-102-linecount.txt` 实测 HomeStats 24 + home_heat_strip 48，非注释全文件口径——**本文件早前正文写 66 是手抄旧读数，以证据文件 72 为准**，属「计数与证据不同源」缺陷，在此更正）；既有文件改动归属 139 物理行（同证据文件，`git diff` 归属时 083-087 在制、无法机械重derive），非注释口径必然 ≤139（剥离注释只减不增）→ **判定值区间 [72, 211]，上界 211 < 500 硬上限，硬上限合规与归属精度无关；软上限 200 按最坏情况触发，记 verifier 警告不构成失败**（verifier 估 ≈197 落在区间内）；③ 真机观感留用户自测；点按进抽屉留 T-103（已重编号 T-2026-09-23-106）。
- Risks: HeatmapView 绘制行为证据沿用 T-101（设备轮次一并目验）；instrumented 轮次未跑（本任务非该层变更）；103 开工前需以本文件最新实现为基线。

## Handoff notes

- Completed: 布局 include、Presenter/ViewState 状态机、数据端口与装配、空态/降级/TODO 点击、RED/GREEN/guard 证据回填；verifier 独立验收 PARTIAL（详见 Verification report）。
- In progress: 无——两个遗留 Gap 已于 2026-09-23 解决（复跑证据 `artifacts/evidence/P0-baseline-2026-09-23.txt`；行数精算见 Gaps ②），主会话升级 verified。
- Upgrade record (2026-09-23, 主会话): Gap ① 复跑全绿（EXITCODE=0，tests=593/0 失败，lint 0）；Gap ② 有界精算上界 211 < 500。verifier 的 PARTIAL 判定条件全部满足，状态 in_progress → verified。
- Blocker: T-087（settings-center 批）在制中间态阻断 `:app` 编译（32 处错误全部在其文件，git 归因）。
- Unblock condition: T-087 main 编译通过（转 verified）→ 复跑 `:app:testDebugUnitTest` + lint + 行数精算（含 087 落定后可精确归属的既有文件 diff）→ verifier 复核升级。
- Next steps: 087 落定 → 复跑取证 → 102 verified → 103 基于本实现接抽屉跳转（`onHeatStripTapped` 内 TODO 位）→ 用户真机自测观感。
- Watch-outs: 空态文案不显示「0 天」（有测试钉住）；统计查询失败卡片隐藏、页面可用（有测试钉住）；AppContainer 时钟已改 `systemDefaultZone()`（verifier 判保留，后续任务勿改回 UTC）；行数判定值待精算，暂按两套数并列（66 新文件裁定口径 + 139 既有改动行记录值）。
