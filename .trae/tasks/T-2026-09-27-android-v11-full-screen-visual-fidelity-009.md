# Treelearn Harness Task: Android V1.1 全屏视觉复刻（父需求）

id: T-2026-09-27-009
title: Android V1.1 全屏视觉复刻
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: requirement-decomposer
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: Android 当前各屏与 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.1.html` 的视觉和布局并不完全一致。已确认的代码差异包括全站纸感横线底纹只在首页绘制、统计热力图顺序/区间不一致、搜索页在原型与生产端的呈现组织不同，以及单主题新树在既有截图中缩至几乎不可识别。其余页面需以当前生产代码和同状态截图逐项核对。
- Users/scenarios: 使用 TreeLearn Android 的单人用户，覆盖首页、学习库、设置、主题对话、学习树与分支导航；在空态、已有记录、输入、生成、检索、确认和失败反馈等实际状态中使用。
- Expected outcome: 所有 V1.1 中可映射到现有 Android 生产路由的屏幕、抽屉、弹层和关键状态，按已接受规格实现视觉与布局 1:1；现有功能、数据操作、触达区域及导航结果保持不变。
- Core flow and reversibility: 首页 → 抽屉 → 统计/最近学习；底部标签 → 学习库/设置；学习库 → 搜索结果/主题；主题 → 对话/画布/分支导航；设置 → 提供商、模型、搜索服务、语音、文件子页。布局变更不得改写这些返回/跳转结果；视觉变更可按独立子任务回退，不迁移或删除用户数据。
- Inputs/outputs: 输入为已接受的 V1.1 原型、现有 Android 生产状态与活动设计 token；输出为 Android Java/XML 原生 UI 调整及对应测试、截图与文档更新，不新增 API、数据表或外部服务调用。
- State and exception handling: 覆盖生产页面已有的空、加载、错误、禁用、生成中、检索中、有来源/无来源、确认与可用状态。当前设备无学习主题、附件、统计数据且未配置模型 Key；不得将这些数据/配置差异记作样式缺陷，也不得在用户设备写入原型演示数据。
- Constraints and trade-offs: Java-only、零新增依赖、沿用 V1 原型 `:root` token 权威源且不改 token 数值、现有可点击区域不小于 44dp、保留 sw600dp 横屏支持。单个实现任务目标为非空非注释生产代码行不超过 200；现有工作树含首页/对话等未提交变更，严禁重置、覆盖或把既有变更算成本需求。
- AI recommendation and rationale: 先按已接受的全屏规格建立单一父需求，再按页面/窄行为拆实现任务，避免把多个界面及共享背景一次性塞进单 task；共享底纹作为单一视觉基元处理，首页与其余页面避免重复绘制。首页现有任务链先完成/明确交接后再触碰重叠文件。
- User choice and custom additions: 用户接受规格；统计详情选择 A——按原型显示当月日历，底层历史数据与统计聚合不删除、不改写；较早日期不在统计详情当前视图显示。用户允许重排现有控件，并要求保留功能、数据、操作和导航结果，不加入仅原型拥有的新能力。
- AI-autonomous decisions: 保留 Android 当前生产路由，不为视觉相似新增独立搜索、归档或提供商编辑路由；系统状态栏/导航栏保持原生；无原型 hover 时映射为按压反馈；有数据视觉验证在隔离环境进行。
- User-confirmation decisions: 用户选择 Harness 流程；确认复刻全部现有 Android 页面及其面板；接受统计当月显示 A；用户于 2026-09-27 接受规格并确认本父需求范围。
- Acceptance evidence: 对照原型逐屏截图、屏幕与状态映射表、受影响页面测试、Android lint、Java-only guard、token guard、目标设备冷启动与交互记录；截图注明设备/视口/状态。不得以静态代码检查或功能单测替代视觉截图证据。
- Facts: 原型 V1.1 的走查记录 `artifacts/evidence/ux-v11-prototype-2026-09-27.txt` 为 24/24 步通过且页面异常为零；Android 生产入口为 `MainActivity`。`activity_main.xml` 使用纯底色，`PaperRuleBackdrop` 当前只挂首页手机/平板布局。统计热力图已实现，当前布局是十周横向网格在前、指标卡在后；`android/README.md` 第 455 行仍描述近十周。首页任务 001 为 ready，002–007 为 verifying，008 为 ready；ChatScreen 及相关布局有现存未提交改动。
- Assumptions: 现有用户数据与原型演示数据不是同一内容；实现验收可使用独立、可清理的隔离验证环境，但不得更改用户设备数据。
- Unknowns: 多数子页没有同设备同状态的当前 Android 对照截图；需在实现切片中按映射补足。当前设备缺少 Key 与主题，无法在其上直达有效对话/分支状态。
- Risks: 全站底纹、统计展示区间与学习树初始比例可能触及共享 View、统计展示契约或既有视觉判断；首页和对话代码有活动工作树差异。任何共享文件冲突、已有用户改动归属不清或 500 行聚合硬预算无法归因时，先停并报告，不擅自回退、覆盖或申请事后例外。
- Deferred: 用户已确认父需求并建立 14 个子任务；首页任务链验证结束后按依赖执行；完成全部实现和逐屏验收后关闭父需求。
- Non-goals: 不改 PRD 的产品能力、不改数据库/API/权限/统计底层聚合、不添加演示默认数据或原型独有入口、不改导航目标、不改 V1 token 数值、不做暗色主题或字体打包。
- Skipped non-critical questions and reason: 不逐项重新确认 token 数值、字号和间距；它们由被接受的原型与权威 token 决定。未为有数据截图选择用户设备造数方案；已限定在隔离环境验证。

## Goal

把已接受的全屏视觉规格拆解并交付为一组可独立验收的 Android 视觉复刻任务；最终让所有现有 Android 页面与原型 V1.1 的对应视觉状态一致，同时保持现有功能、数据与导航。

## Scope

### In scope
- 应用壳与跨屏纸感背景、底部标签、状态栏安全区的视觉一致性。
- 首页、抽屉、统计详情、学习库/已完成/搜索、对话与相关弹层、学习树、分支导航、设置及全部设置子页。
- 原型对应的空/有数据/错误/禁用/生成/检索/确认状态视觉映射。
- 为每个实现切片补相应测试、同视口截图与当前态文档影响记录。

### Out of scope
- 新增原型专属功能或 Android 导航路由。
- 业务数据、表结构、API、模型 Key、权限及服务边界改造。
- 在用户设备灌入原型演示数据或配置真实 Key。
- 覆盖/重做首页任务 001–008 中已经进行的工作；仅在其稳定后复核剩余视觉差异。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 主要改变界面呈现；统计热力图改为当前月视觉范围（用户已选 A），底层记录和累计统计不删除、不改写。保留输入、搜索、分段切换、对话生成/停止、分支进入、设置操作及抽屉关闭等既有行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `MainActivity` 为路由宿主；`LibraryScreen` 承载学习库搜索状态；`HomeDrawerScreen` 打开 `StatsDetailScreen`；`StatsDetailPresenter` 从既有统计聚合读取；首页、聊天、画布、导航及设置各 screen 通过其 presenter/host 承接操作。手机与 `layout-sw600dp` 平板布局均需覆盖。
- Native bridge or independent-client compatibility/update requirements: 不触及前端/Android 桥接、API 或独立发布客户端。
- Behavior-neutral non-applicability reason, if applicable: 除统计页显示窗口按已批准的 A 调整外，其余子任务预期均为界面呈现变更；各子任务需证明原行为等价且没有更改路由或数据。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 尚无代码变更；已批准规格确立全屏视觉基线与统计当月展示选择。
- Authoritative code/config/migration evidence: `MainActivity.java`、各 `screen/` Java 类、`res/layout/`；统计详情现状见 `StatsDetailPresenter.java`、`screen_stats_detail.xml` 与 `android/README.md:455`。
- Documentation surfaces checked: 全屏原型 V1.1、已接受规格、首页视觉规格、抽屉统计规格、提供商/模型/搜索服务/语音/文件规格、PRD 截图对齐报告及 `android/README.md`。
- Documentation files updated: `Docs/spec/SPEC-2026-09-27-android-v11-full-screen-fidelity.md` 已接受；实现任务需按代码变化更新 `android/README.md` 等当前态文档。
- Producer/consumer/sibling/compatibility paths checked: `MainActivity` 路由与 screen/presenter；统计聚合入口由抽屉和详情页共用；学习库搜索由既有 search stack 驱动；不变更 API/原生桥接。
- Superseded phrases/examples reverse-searched: 实现统计月历后反查 README 第 455 行“近 10 周”描述与相关旧截图判断；历史报告保留原貌并引用新规格，不将其当作当前实现证据。
- `documentation impact: none` reason and evidence, if applicable: 不适用；统计可视区间与屏幕视觉事实变化需同步当前态文档。
- Historical/PRD handling: 不静默改写 PRD 或带日期的评审报告；当前实现差异以生产代码为准，过时结论注明时间点并指向本规格。

## Acceptance criteria

- [ ] 子任务覆盖本文件 Scope 中所有现有生产屏与面板；逐项以同设备视口/对应状态截图对照原型，记录通过、差异与未验证项。
- [ ] 视觉一致性通过人工截图复核且无溢出/遮挡；所有可点热区不低于 44dp，手机与平板横屏表现保持。
- [ ] 既有功能、数据操作与导航目标回归通过；统计只改变当月显示窗口，历史数据与共享统计聚合保持不变。
- [ ] 每个 Android 子任务执行对应 RED/GREEN、定向测试、lint、Java-only guard、token guard 与启动/设备交互证据；主任务最后由独立 verifier 汇总。
- [ ] 当前态文档与实现事实一致；secret-scan、任务校验及全局业务行预算在最终验收时均有可复核证据。

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用；本父需求只记录范围与任务组织，不写生产代码。
- Command: 不适用。
- Exit code: 不适用。
- Observed failure: 不适用。
- Failure reason validated: 不适用。

### GREEN — minimum implementation
- Implementation: 不适用；待子任务拆分和执行。
- Command: `python -X utf8 .trae/scripts/validate_tasks.py`，在创建任务文件后立即校验。
- Exit code: 待执行时记录。
- Passing evidence: 子任务与父任务文件通过 Task v2 契约校验。
- Regression evidence: 不适用；父任务无生产代码变更。

### REFACTOR — optional
- Cleanup: 不适用。
- Regression evidence: 不适用。

### TDD or sizing exception — only with prior user approval
- Exception type: 本父需求不适用 TDD；子实现任务默认 `tdd_mode: required`。
- Reason: 该任务只定义复刻范围与交接，不修改生产代码。
- User approval: 用户已选择 Harness 并接受全屏规格；本父任务仍待单独确认。
- Alternative verification: 子任务总览、Task v2 机械校验及最终逐屏独立验收。

## Implementation notes

- 本任务不授权直接改生产代码；父需求已于 2026-09-27 确认。实现由下列 Android 子任务承接，逐项完结后再运行独立总验收。
- 当前工作树已有大量未提交修改；实现任务必须明确写集，按任务归属统计行数，不回退、不覆盖任何现存变更。
- 子任务文件与依赖：010 全站纸纹/外壳；011 抽屉；012 统计；013 学习库/搜索；014 画布；015 分支导航；016 设置中心；017 提供商；018 模型；019 搜索服务；020 语音；021 文件管理；022 对话内容；023 对话输入/弹层。对应描述性文件均在 `.trae/tasks/`，写集已避开 008 完成前的 `MainActivity` 冲突。

## Verification report

- Verdict: PENDING
- Evidence: 用户已接受规格和父需求；14 个实现任务已创建，新增任务逐批通过 Task v2 校验；当前还没有本父需求生产代码验收。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用。
- Gaps: 多屏截图基线、隔离数据状态和跨设备视觉验收由子任务建立。
- Risks: 首页任务 002–007 仍在 verifying，008 尚待执行；聊天页有效 Key/数据状态不可在当前设备验证。

## Handoff notes

- Completed: 全屏规格与父需求已确认；统计显示区间 A 已记录；14 个页面实现子任务已建立，且新增任务均通过 Task v2 单文件校验。
- In progress: 等待首页验证任务 008 完成，以安全释放 `MainActivity` 写集；随后启动 010。
- Blocker: 008 依赖的首页任务 002–007 尚未 verified；生产代码尚未在本父需求下修改。
- Unblock condition: 首页任务链 002–008 完成独立复核。
- Next steps: 按依赖执行 010 → 011–016/022 → 017–021/023；每个实现任务独立复核，最终汇总逐屏截图与守门证据。
- Watch-outs: 统计页原型为当月日历，现实现为十周视图；纸纹在原型全屏出现但 Android 仅首页绘制；`android-chat-batch-abc-2026-09-27.png` 是首页图，不得作为对话页证据。
