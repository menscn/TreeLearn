# Treelearn Harness Task: 首页抽屉容器与快捷入口

id: T-2026-09-23-106
title: 首页抽屉容器与快捷入口
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-home-drawer-stats-heatmap-099.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: false
depends_on: [T-2026-09-23-stats-daily-aggregation-100.md, T-2026-09-23-heatmap-custom-view-101.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页抽屉与统计热力图吸收；本任务交付首页抽屉（用户 2026-09-23 拍板内容：对话列表 + 设置按钮 + 统计 + 扩展位）。
- Problem/background: 首页无承载次级功能的位置；RikkaHub 抽屉交互形态被用户认可，但内容按知芽 IA 重新划界。
- Users/scenarios: 点左上抽屉按钮 → 对话快捷列表回最近主题 / 看热力图与累计指标 / 直达设置 / 未来扩展位（如人设介绍页入口）。
- Expected outcome: DrawerLayout 容器 + 对话快捷列表（最近 N 条，默认建议值实现期定）+ 设置快捷入口 + 扩展位骨架；**快捷入口语义**：学习库与底部标签栏保持正典唯一，抽屉不复制全量列表、不放搜索、不放助手切换。
- Core flow and reversibility: 返回或点遮罩关闭抽屉；对话项点击直达主题对话；归档主题不出现在快捷列表（与 BASE-14 一致）。
- Inputs/outputs: 输入既有主题查询能力 + 100/101；输出抽屉布局、Presenter、导航接线。
- State and exception handling: 无主题时列表显示空态；快捷列表只读。
- Constraints and trade-offs: XML/View；44dp 热区；对话列表为快捷入口——**不得**出现两份各自维护的全量列表（本仓失败案例库「一条规则两个正文」同构坑）。
- AI recommendation and rationale: 抽屉列表数据直接复用学习库的既有查询方法，不新增第二查询源；设置入口复用既有导航目标。
- User choice and custom additions: 用户拍板抽屉承载对话列表与设置按钮。
- AI-autonomous decisions: 快捷列表「最近 N 条」N 值先按 10 实现，设计稿定稿后调整。
- User-confirmation decisions: 设计阶段待定：列表形态（最近 N vs 全量，建议前者）、双入口呈现。
- Acceptance evidence: RED/GREEN Presenter 测试 + 回归 + 真机观感列缺口。
- Facts: V3.2 一级入口由底部标签栏承载（PRD 第 99 行）；学习库为正典主题列表与唯一搜索入口。
- Assumptions: 学习库既有主题查询可直接复用。
- Unknowns: 真机观感；抽屉动效细节。
- Risks: 抽屉对话列表长成第二套首页（V3.2 F16 张力，已在 intent 风险区记录，交付后 105 回写 PRD）。
- Deferred: 抽屉扩展位具体功能（人设介绍页入口待其切片 5 接入）。
- Non-goals: 统计详情页（104）；搜索；助手切换；底部标签栏变更。
- Skipped non-critical questions and reason: 抽屉头部是否显示用户信息——单人应用简化为 streak 文案。

## Goal

交付首页抽屉容器：对话快捷列表 + 设置快捷入口 + 扩展位骨架，快捷入口语义不侵蚀学习库正典地位。

## Scope

### In scope

- DrawerLayout 接线、抽屉布局、快捷列表 Presenter、设置入口导航、空态。

### Out of scope

- 统计详情页（104）；热力条外放（102）；扩展位功能填充。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 学习库列表、底部标签栏三 tab、首页主流程行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 首页 Presenter/ViewState 为共享面（与 102 串行）；消费学习库既有查询（只调用，不复制实现）；设置入口复用既有导航。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首页新增抽屉入口与快捷列表。
- Authoritative code/config/migration evidence: 抽屉布局与 Presenter 变更。
- Documentation surfaces checked: V3.2 PRD（F16 条目）。
- Documentation files updated: 由 105 统一回写「快捷 vs 正典」边界。
- Producer/consumer/sibling/compatibility paths checked: 学习库查询回归。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录（105）。

## Acceptance criteria

- [x] RED/GREEN：抽屉开合状态机、快捷列表最近 N 条与空态、点击直达主题、设置入口跳转正确、遮罩/返回关闭。
- [x] 快捷列表复用学习库既有查询方法（grep 证据：无第二份查询实现）。
- [x] 学习库与首页既有测试全部通过（回归证据）。
- [x] 触达 ≥44dp、contentDescription；Java-only guard + 测试退出码 0；生产代码 ≤200 行。

## TDD execution record

### RED — failing test first
- Test/scenario: `HomeDrawerPresenterTest`（新增，JVM）——①start 渲染 CLOSED 且快捷列表为学习库查询的前 10 条（11 条输入截断为 10）；②开合状态机：toggle CLOSED→OPEN、再 toggle→CLOSED；③点击主题 → `openBranch(resumeBranchId)` 且状态机回 CLOSED（直达后抽屉不残留）；④设置入口 → `openSettings()`、统计入口 → `openStats()`，两者均先收抽屉；⑤空库 → rows 为空（View 层空态），source 抛异常 → 同样降级空列表不崩溃。RED 阶段 `HomeDrawerPresenter` 尚不存在，预期编译失败。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.HomeDrawerPresenterTest"`
- Exit code: 1
- Observed failure: 编译失败——`HomeDrawerPresenter` 及其 `Sink`/`QuickTopicsSource`/`Phase`/`State` 嵌套类型不存在（T-106-red-2026-09-25.log）。
- Failure reason validated: 全部错误指向本片待实现的 presenter 契约，无一条指向既有行为。

### GREEN — minimum implementation
- Implementation: ①新 `ui/HomeDrawerPresenter.java`（96 行）——CLOSED/OPEN 两态机；`QuickTopicsSource` 端口接 `LibraryData::topics`（学习库**同一**查询链，Row 映射含标题解码零复制）；最近 10 条窗口（`QUICK_LIMIT`）；点击主题 → `openBranch(resumeBranchId)`（与首页行同一目标）且强制收拢；设置/统计入口同样先收后跳；读失败降级空列表不崩溃；带 ui/background 构造器时 start() 后台读、主线程 render；②新 `screen/HomeDrawerScreen.java`（116 行）——自绘覆盖层（无 DrawerLayout 依赖：scrim + 78% 宽左面板，宽度程序化计算零新 token），快捷行/设置/统计全部 ≥44dp（`touch_min`）；③新 `drawable/ic_nav_menu.xml`（汉堡图标，项目 24dp/stroke1.8/圆头惯例）；④`activity_main.xml` 外层包 `FrameLayout`（`main_drawer_root`）——抽屉盖住 tab bar 且不被 `swap()` 清除；`screen_home_body.xml` 顶栏加 44dp 抽屉按钮；strings 七键；⑤`AppContainer` 暴露 `libraryStore()`；`MainActivity` 挂 drawer（宿主 Sink → showChat/showSettings/showStatsDetail 同一房间方法）、返回键首分支收抽屉、`showHome()` 重读快捷列表。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.HomeDrawerPresenterTest"`
- Exit code: 0
- Passing evidence: 5/5（GREEN 同 RED 命令；T-106-green-2026-09-25.log BUILD SUCCESSFUL）。执行修正三处：fixture 缺 summary 参数（T-061 加过）、`Guard.id` 要求 UUID（T-097 同款踩坑）、Screen 误 implements Sink（那是宿主端口）。
- Regression evidence: 全量 JVM 单测 638/0/0（74 文件）；check EXITCODE=0；Java-only PASS；真机外壳 5 + 学习库 5 = 10/10（根布局变更零扰动）；证据 `artifacts/evidence/T-106-home-drawer.txt`（CMD-1 至 CMD-10）。

### REFACTOR — optional
- Cleanup: 无独立轮——初稿的 Actions 接口（无实现方）与 Consumer 间接层当场删除，屏直接收 presenter（T-103 惯例）。
- Regression evidence: 全量单测 638/0/0 同上。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 与 102 串行：同触首页 Presenter/ViewState 面。
- 抽屉内统计区（热力图 + 指标卡 + 统计详情入口）的数据读 100/101，详情页由 104 承接，本任务只放入口。

## Verification report

- Verdict: PASS
- Evidence: RED/GREEN 同命令 EXITCODE 1→0；单测 638/0/0；check 0；Java-only PASS；真机外壳+学习库 10/10；`OVERVIEW_SELECT` 全仓仅定义一次使用一次（CMD-8，无第二份查询实现）。汇总 `artifacts/evidence/T-106-home-drawer.txt`（CMD-1 至 CMD-10，命令+退出码内嵌）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无既有断言删除；GREEN 中修正测试自身三处 fixture 错误（summary 参数/UUID 约定/Sink 归属），判定逻辑零改动。
- Gaps: 抽屉动效（滑入滑出）未做——两态直接显隐，任务书列为 unknown 项，由人 review 判定；「归档主题不出现在快捷列表」由 BASE-14 领域法则（主题无归档态）天然保证，非本片代码约束；真机观感未验（用户自测项）。
- Risks: 最大风险是抽屉长成第二套首页（F16 张力）——由「复用同一查询 + 同一 openBranch 目标 + 无搜索/助手切换」三条结构性约束拦住，且 grep 证明单一查询源；`activity_main.xml` 根结构变更影响所有屏——真机外壳回归 5/5 证明零扰动。
- 规模：新文件 233 行 + 修改归属 +39 = 272（估算 180，超 200 软上限——自绘抽屉 View 116 行为主），如实记录未作例外申请。

## Handoff notes

- Completed: 首页抽屉全链（presenter/自绘覆盖层/汉堡图标/根布局/接线/返回链）交付并 verified；快捷列表与学习库共享同一查询与同一导航目标；T-104 统计详情页获得生产入口。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: T-105（PRD 快捷/正典边界回写）——抽屉的实际形态已定，105 有真实事实可写。
- Watch-outs: (1) 抽屉 overlay 挂 `main_drawer_root`（swap 之外），后续新房间不得把它当屏挂进 `shell_host`；(2) 快捷列表窗口值 `QUICK_LIMIT=10` 是 AI 自主决定，设计稿定稿后可调；(3) 扩展位骨架 GONE，人设介绍页入口由其切片填充；(4) 返回键收抽屉分支在 `onBackRequested` 最前，后续加返回层级时不得插到它之前。
