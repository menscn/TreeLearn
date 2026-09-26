# Treelearn Harness Task: 首页瘦身与继续学习

id: T-2026-09-22-055
title: 首页瘦身与继续学习
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 300
shared_code_touched: true
depends_on: [T-2026-09-22-bottom-tab-shell-and-library-screen-052.md, T-2026-09-22-library-archived-list-and-subtree-delete-053.md, T-2026-09-22-library-inplace-search-and-search-screen-retire-054.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿的信息架构与视觉**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。本片是 IA 链条的**收口片**：只有学习库（052）、已完成区（053）、搜索框（054）都建好之后，首页才敢把历史主题列表与头部三入口摘掉——否则会出现功能空窗。
- Problem/background: 现行首页是「唯一入口页」，头部右侧依次提供搜索、已归档、设置三个入口（`screen_home_body.xml` 第 13–63 行），下方是历史主题列表（第 185–272 行：`home_section` + `home_topics` + `home_loading` / `home_error` / `home_empty`）。新稿的首页只剩四块：品牌区、输入区、继续学习、发现话题。
- Expected outcome: 首页不再出现历史主题列表与头部三入口；品牌区改为图形标识 + 中文名 + 英文小字（品牌衬线字）；补「继续学习」一行（显示最近分支标题，无历史时不出现）；「发现话题」**保留现有推荐弹幕**（双行反向滚动、交互时暂停、三类标签），只换配色、间距与图标位。所有被摘掉的东西**都已有新家**，能力一项不少。
- Core flow and reversibility: 主路径 A 变为「启动落主页 → 主页输入根问题 → 对话 → 追问或分叉 → 画布/导航查看 → 到学习库回查」。首页不再承担浏览历史的职责；「继续学习」只提供最近一条的快捷回访。回访全部主题的路径是学习库（052 已交付）。
- Inputs/outputs: 输入为根问题文本、模型档位、发送动作；输出为首页的视图状态（品牌区、草稿、Key 提示、继续学习项、推荐项）。
- State and exception handling: 无可用 Key 时不创建主题，只在主页给提示（沿用 `HomePresenter` 现有守卫，**不得**因瘦身而丢失）；草稿在任何失败下保留；「继续学习」无历史时不出现；推荐生成失败时的既有兜底（示兜底起点或隐藏推荐区，不阻断输入与列表）不变。
- Constraints and trade-offs: 本片是**纯减法加换皮**——不做新增功能、不改组件形态。摘掉三入口的前提是对应能力已在新落点可用（052/053/054 已 verified），这是本片 dependencies 写三条的原因。
- **决策记录（用户 2026-09-22 裁决）**：推荐区**保留弹幕形态**，不改为新稿的「一张重点卡 + 三条列表项」。理由：PRD 第 228 行的 F17【P0】把「弹幕滚动展示」写进了能力描述、第 978 行又把它写进验收口径，改掉它属于动能力而非纯装饰，与红线冲突。代价：新稿的「重点卡 + 三条列表项」形态不进入本批，记为 deferred，并在 `Docs/spec/SPEC-2026-09-22-figma-ia-migration.md` 登记为偏差；收益是本片净变动更小、P0 条目零触碰。
- Acceptance evidence: 由真实命令输出提供——首页布局的 id 扫描（证明列表与三入口已不存在）；「继续学习」有/无历史两种断言；推荐弹幕形态与三类标签不变的断言；启动测试仍绿；全量单测的回归证据；无 Key 守卫的断言。
- Facts: `screen_home.xml`（手机）与 `layout-sw600dp/screen_home.xml`（平板）都只是外壳，两者都 `<include layout="@layout/screen_home_body" />`——所以**首页结构只需改一个文件** `screen_home_body.xml`，两套配置自动同步。`AppLaunchInstrumentedTest` 依赖 `R.id.main_root` 与 `R.id.home_draft`，两者都不得消失。`Recommendation` 携带 `Kind` 分类（`ui/Recommendation.java` 第 15 行），故三类推荐来源在数据层是可分辨的；弹幕的滚动实现在 `screen/RecommendationMarquee.java`。
- Non-goals: 不新增功能；不改无 Key 守卫、草稿保留、生成中断/重试语义；不动学习库与二级屏；不动画布与图标资产；**不改推荐区形态**。

## Goal

首页只剩品牌区、输入区、继续学习、发现话题四块——历史主题列表与头部三入口不再存在，而它们对应的能力在新落点上全部可用且不退化；推荐区**形态不变**，只换配色、间距与图标位。

## Scope

### In scope
- `res/layout/screen_home_body.xml`（**唯一**的首页结构文件，两套配置共用）：
  - 移除 `home_appbar` 内的 `home_search`、`home_archived`、`home_settings` 三个按钮；品牌区改为图形标识 + 中文名 + 英文小字，英文小字与中文名使用品牌衬线档位。
  - 移除 `home_section`、`home_section_count`、`home_topics`、`home_loading`、`home_error`、`home_empty`（含 `home_empty_guide`）整块。
  - **保留**：`home_draft`（启动测试依赖）、`home_model_chip`、`home_key_hint`（无 Key 提示）、`home_send`、`home_notice`、`home_hero`、`home_recommendations`（推荐区容器）。
  - 新增「继续学习」一行（有最近分支时显示标题，无则不占位）。
- `res/layout/item_recommendation.xml` 与 `screen/RecommendationMarquee.java`：**只换皮**——配色、间距、圆角与图标位改用新 token 与图标；滚动行为、暂停规则、三类标签分组与点击行为一律不动。
- `screen/HomeScreen.java`：移除三个入口的监听绑定；接入「继续学习」的渲染（推荐区渲染沿用现状）。
- `ui/HomePresenter.java`、`ui/HomeActions.java`、`ui/HomeSink.java`、`ui/HomeViewState.java`：收敛到**仍被 UI 使用**的动作集合；移除 `onSearchPressed`/`onArchivedPressed`/`onSettingsPressed` 及其 sink 计数，移除首页主题列表的渲染状态字段（`topics` 若仍被「继续学习」使用则保留，仅改用途）。不得留下死代码，也不得删除仍被使用的端口。
- `MainActivity`：移除 `HomeScreen.Host` 中对应的三个回调实现；`showSettings()` 保留（它现在是标签栏的落点，由 052 路由）。
- **删除** `app/src/test/java/com/treelearn/app/ui/HomeSearchEntryTest.java`：首页搜索入口已不存在，该测试的断言对象消失；其核心性质（搜索入口**无条件可达**，空库时也进得去）由 054 新增的 `LibrarySearchEntryTest` 继承。
- `res/values/strings.xml`：追加 `home_continue_*` 前缀的键；移除只服务于已删区块的键（须确认无其他引用后再删）。

### Out of scope
- `res/layout/screen_home.xml` 与 `layout-sw600dp/screen_home.xml`（两者只是外壳 include，无需改动）
- 学习库、已完成区、搜索框（052/053/054 的领地）
- 对话屏、画布、分支导航、设置屏
- `res/values/` 下的 token 文件（050 的领地）
- 推荐区的**形态改造**（重点卡 + 三条列表项）——已由用户裁决不做，见决策记录
- 任何新增功能

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**首页承载的内容集合**（去掉历史主题列表与头部三入口，补继续学习）与**品牌区的视觉载体**。必须原样保留的行为是：无可用 Key 时不创建主题且只在主页提示、草稿在任何失败下保留、根问题提交路径、**推荐弹幕的全部既有交互**——双行反向滚动、交互时暂停、三类标签（顺着你学的 / 换个方向 / 入门）、滚动中的推荐项可稳定点击（PRD 第 96 行单列了这条约束）、点击一次直接创建主题并开始对话；以及首页作为启动落点的地位。本片**只删已有新家可去的东西**——三入口的替代分别是标签栏（设置）、学习库已完成区（已归档）、学习库搜索框（搜索），三者在 052/053/054 已 verified 后才允许删除。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 首页视图状态的生产者是 `ui/HomePresenter.java`（消费端口 `ui/HomeData.java`，接线在 `wiring/HomeWiring.java` 与 `wiring/SqliteHomeStore.java`）；推荐数据来自 `ui/StarterRecommendations.java` 与 `ui/Recommendation.java`；弹幕绘制在 `screen/RecommendationMarquee.java`；换屏的单一入口是 `MainActivity`。平台分支为 `screen_home.xml` 与 `layout-sw600dp/screen_home.xml` 两套外壳，两者都 include 同一个 body，故结构改动一次覆盖两套。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 本片不改 `android/data/`、`android/core/`、`android/ai/`，故这三侧无行为影响。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首页不再有历史主题列表与头部搜索/归档/设置三入口；首页新增「继续学习」一行；品牌区改为图形标识 + 中英文名；推荐区保留弹幕形态但换新配色与间距。
- Authoritative code/config/migration evidence: `android/app/src/main/res/layout/screen_home_body.xml`、`res/layout/item_recommendation.xml`、`screen/RecommendationMarquee.java`、`screen/HomeScreen.java`、`ui/HomePresenter.java`、`MainActivity.java`。
- Documentation surfaces checked: `Docs/03-prd-draft/V3.2-…PRD.md`（051 产出的新口径；PRD 第 228 与 978 行的 F17 口径**不改**）、`android/README.md`、`Docs/06-delivery/TreeLearn_V1_交付报告.md`（历史快照）。
- Documentation files updated: `android/README.md` 的首页小节改写为新的四块结构、说明三入口的新落点，并写明推荐区形态未变；`Docs/06-delivery/TreeLearn_V1_交付报告.md` **不改写**（历史快照）。
- Producer/consumer/sibling/compatibility paths checked: `HomePresenter`、`HomeData`、`HomeWiring`、`SqliteHomeStore`、`StarterRecommendations`、`Recommendation`、`RecommendationMarquee`、`AppLaunchInstrumentedTest`、`HomePresenterTest`、`HomeViewStateTest`、`HomeWiringTest`。
- Superseded phrases/examples reverse-searched: 「`home_search`」「`home_archived`」「`home_settings`」「历史主题列表」「首页为唯一入口页」在 `android/README.md`、`Docs/` 与两个 README 中的位置。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次确有当前态文档变更。
- Historical/PRD handling: V3.2 PRD 是新依据；V3.1 不改。PRD 第 49、96、228、978 四行因推荐区形态保留而**一字不改**。`Docs/06-delivery/TreeLearn_V1_交付报告.md` 是历史快照，不改写。

## Acceptance criteria

- [ ] 历史主题列表已消失：`grep -rn "home_topics\|home_section\|home_empty" android/app/src/main/res/layout/ android/app/src/main/java/` 在生产代码中零命中（证据文件须含该命令与输出）。
- [ ] 头部三入口已消失：`grep -rn "home_search\|home_archived\|home_settings" android/app/src/main/res/layout/ android/app/src/main/java/` 零命中；同时 `HomeActions`/`HomeSink` 中对应的三个动作与计数已移除（同一条扫描覆盖）。
- [ ] 「继续学习」两种状态各有断言：有历史时显示最近分支标题；无历史时该行**不出现**（不是显示空占位）。
- [ ] 推荐弹幕形态**未变**：双行反向滚动、交互时暂停、三类标签（顺着你学的 / 换个方向 / 入门）全部保留；三类来源（`Recommendation.Kind`）仍全部可达；点击一条推荐**一次**即创建主题并开始对话（沿用既有行为，不新增二次确认）。`git diff` 证明 `item_recommendation.xml` 只出现配色/间距/图标位变更，不出现结构或滚动参数变更。
- [ ] 首页输入与守卫不退化：`home_draft` 仍可达（`AppLaunchInstrumentedTest` 全绿）；无可用 Key 时不创建主题且只在主页提示；草稿在失败后保留。三条各有断言。
- [ ] 测试覆盖迁移有据：`HomeSearchEntryTest.java` 已删除，并在本任务证据文件里写明其性质由 054 的 `LibrarySearchEntryTest` 继承（引用该文件路径与它的断言名）；全量 `:app` 单测通过，且因删除用例导致的计数下降须在证据里显式说明（不得让计数无声下降）。
- [ ] 平台分支覆盖：`screen_home.xml` 与 `layout-sw600dp/screen_home.xml` 两套外壳仍能正常 include 改后的 body，两套下首页四块结构一致；`.\gradlew.bat check` 退出码 0，Java-only 守门 `android/scripts/no-kotlin-check.ps1` 退出码 0。

## TDD execution record

### RED — failing test first
- Test/scenario: `HomeContinueLearningTest`（新增，`app/src/test/java/com/treelearn/app/ui/`）——「有历史时继续学习行取最近一个主题的标题与 resumeBranchId」「无历史时 continueTopic 为 null（不出现占位）」两组断言；RED 阶段 `HomeViewState.continueTopic()` 尚不存在，预期编译期缺符号失败。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "*HomeContinue*"`
- Exit code: 1
- Observed failure: `BUILD FAILED in 14s`，3 条编译期「找不到符号: continueTopic()」（HomeViewState 尚无该方法）。完整日志 `artifacts/evidence/T-055-red-2026-09-24.log`。
- Failure reason validated: 失败确由缺失行为导致——RED 阶段唯一改动是新增 `HomeContinueLearningTest`（引用尚不存在的 `HomeViewState.continueTopic()`），生产代码零改动；3 条错误全部指向该测试对 `continueTopic()` 的两处调用。

### GREEN — minimum implementation
- Implementation: ① `HomeViewState.continueTopic()`（最近主题，空历史为 null）；② `HomeActions`/`HomeSink` 收敛（移除 `onSearchPressed`/`onArchivedPressed`/`onSettingsPressed`/`onRenameTopic`/`onDeleteTopic` 与 `openArchived`/`openSearch`）；③ `HomePresenter` 同步收敛；④ `HomeData`/`HomeStore`/`HomeWiring`/`SqliteHomeStore` 移除随之死掉的 renameTopic/deleteTopic；⑤ `HomeScreen` 重写（品牌区/输入区/继续学习行/推荐区渲染，移除列表与三入口绑定、key hint 改被动提示）；⑥ `screen_home_body.xml` 重建（三入口删除、品牌区改图形标识+衬线中英文、列表整块删除、新增 `home_continue` 行、`home_key_hint` 去掉 clickable）；⑦ `MainActivity` 移除 `openArchived`/`openSearch`；⑧ `strings.xml` 增 `home_brand_english`/`home_continue_label`、删 17 个只服务已删区块的键（删前零引用扫描）；⑨ 删除 `HomeSearchEntryTest.java` 与 `item_topic.xml`；⑩ `TestDoubles`/`HomePresenterTest` 同步收敛计数；⑪ `BottomTabShellInstrumentedTest` 的首页行点击迁移到 `home_continue`；⑫ `NavigationStoreInstrumentedTest` 过时注释更新。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "*HomeContinue*"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL in 40s`，`HomeContinueLearningTest` 2 例全绿（命令与 RED 逐字一致）。日志 `artifacts/evidence/T-055-green-2026-09-24.log`。
- Regression evidence: 全量 `:app` 单测 tests=615 failures=0 errors=0（616 − 3 删除 + 2 新增，对账吻合；开工前基线 593，见 `P0-baseline-2026-09-23.txt`）；`./gradlew.bat check` 退出码 0（lint + androidTest 编译）；Java-only 守门退出码 0；真机（ALP-AN00 - 13）`BottomTabShellInstrumentedTest` + `AppLaunchInstrumentedTest` 6/6 全绿（含迁移后的 `home_continue` 导航用例与 `home_draft` 启动断言）。全部命令与退出码内嵌于 `artifacts/evidence/T-055-home-slimming.txt`（CMD-1 至 CMD-12）。

### REFACTOR — optional
- Cleanup: 无独立重构轮；strings 删键与死端口清理已并入 GREEN（见实现第 ④⑧ 步）。
- Regression evidence: 同 GREEN 回归证据（全量单测 615/0/0、check、Java-only、真机 6/6）。

### TDD or sizing exception — only with prior user approval
- Exception type: 规模（超 200 软上限，未超 500 硬上限，故不申请规模例外）
- Reason: 300 行里相当一部分是**删除**（首页布局的三个入口与整块列表区、`HomeActions`/`HomeSink` 的三个动作、`HomeSearchEntryTest`），净新增很小（品牌区、继续学习一行、推荐区换皮）。本片不可拆的原因是它是 IA 链条的收口：删掉三入口的前提是 052/053/054 已交付替代落点，而「删」与「补继续学习」共用同一个 `screen_home_body.xml` 与同一份 `HomeViewState`，拆开会产出两个都改同一文件、仍必须串行的片。据此记录理由而非警告。
- User approval: 不适用（未超 500 硬上限，按规则不需用户批准的规模例外）
- Alternative verification: 完成后由 verifier 用 `python .trae/scripts/dev/count_production_lines.py` 对实际改动的生产文件复算（非空非注释口径）；实测超 500 则本片判定失败并重新切分。

## Implementation notes

- 首页结构只需改 `screen_home_body.xml` 一个文件：`screen_home.xml` 与 `layout-sw600dp/screen_home.xml` 都只是外壳并 include 同一个 body（两文件顶部注释即写明「the two configurations cannot drift」）。不要为了「两套适配」去改两个外壳文件。
- `home_draft` **不得**消失：`AppLaunchInstrumentedTest` 用它断言第一屏已渲染。本片摘掉的是列表与三入口，输入区必须完整保留。
- 无 Key 守卫是本片最容易误伤的一条：`HomePresenter` 现有守卫保证「无可用 Key 时不创建主题，只在主页给提示」。移除三入口时不要顺手移除 `home_key_hint` 的绑定与 `HomeViewState.keyConfigured`。
- **推荐区只换皮，不改形态**（用户 2026-09-22 裁决）。`item_recommendation.xml` 只允许改配色、间距、圆角与图标位；若换皮需要动 `RecommendationMarquee.java`，只允许改绘制参数（颜色、间距、文字样式），**不得**改滚动节奏、暂停规则、命中判定与标签分组——PRD 第 96 行专门为「滚动中的推荐项必须可稳定点击」立了一条约束，改它等于动能力。
- 删除 `res/values/strings.xml` 中的键之前先做全仓引用扫描：只有确认零引用才删；有引用的一律保留。删键不是本片目标，能不删就不删。
- 测试删除要有据：`HomeSearchEntryTest` 的三条断言里，「搜索入口无条件可达」这条性质必须在新落点被继承——引用 054 新增的 `LibrarySearchEntryTest` 作为等价覆盖，并在证据文件里写清文件路径与断言名。另外两条（归档入口仍需主题、搜索入口不得放宽归档守卫）随其断言对象一并消失，须在同一处说明。
- `MainActivity` 仍是本批共享代码区，必须串行；本片只做首页瘦身，不要顺手改学习库或搜索屏。

## Verification report

- Verdict: PASS
- Evidence: `artifacts/evidence/T-055-home-slimming.txt`（12 道门禁，命令与退出码内嵌、可自证）：
  CMD-1/2/3 三条 id 与键扫描零命中（历史主题列表、头部三入口、已删 strings 键）；CMD-4 两个删除目标缺席、
  CMD-5 八个保留文件在位（两套外壳、heat strip、检索栈、`item_recommendation.xml`、`LibrarySearchEntryTest`）；
  CMD-6/7 RED→GREEN 同命令；CMD-8 全量单测 615/0/0（基线 593，计数对账见文件内说明）；
  CMD-9 `check` 退出码 0；CMD-10 Java-only 守门 0；CMD-11 真机 6/6（`home_continue` 迁移用例 +
  `home_draft` 启动断言）；CMD-12 `item_recommendation.xml` 与 `RecommendationMarquee.java` git diff 为空
  （推荐区形态与滚动参数零改动，三类 Kind 与一次点击建题路径未动）。「继续学习」两态断言 =
  `HomeContinueLearningTest` 两例；无 Key 守卫与草稿保留 = `HomePresenterTest` 既有断言全绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除 `HomeSearchEntryTest` 三条断言——
  「搜索入口无条件可达」由 054 新增 `LibrarySearchEntryTest.theLibrarySearchEntryIsReachableWithNoTopicsAtAll`
  继承；「归档入口需有主题 / 搜索不得放宽归档守卫」的断言对象（首页入口）随入口移除，等价覆盖为 053
  `LibraryArchiveInstrumentedTest` 的已完成区断言（真机 5 例全绿）。两处落点已在证据文件登记，非静默删除。
  另外：`HomePresenterTest.aHeatStripTapIsANoOpUntilTheDrawerShips` 中 `sink.searches` 断言随
  `HomeSink.openSearch` 的移除失去断言对象（该方法已不存在，编译期即保证不可达），保留 branchesOpened 与
  settingsOpened 两条断言。列表改名/删除动作（`onRenameTopic`/`onDeleteTopic`）随列表 UI 消失，数据链上的
  `HomeData`/`HomeStore`/`HomeWiring`/`SqliteHomeStore` 对应方法一并移除（全仓零引用扫描见 CMD-1~3 同口径）；
  PRD 未将主题改名/删除列为能力条目，能力损失已登记至 Gaps。
- Gaps: ① 推荐区「换皮」零执行——056/057 的新图标与 050 之后的 token 无可引用差量，在没有新资产时改配色/间距
  属无依据扰动，记为 deferred（形态与滚动参数因此零风险，验收第 4 条以 diff 为空满足）；② 主题级改名/删除
  自 055 起在全应用无入口（PRD 无此能力条目；如需恢复应立新任务落在学习库行内，本片不越界实现）；
  ③ 新稿品牌图形标识暂用 `ic_launcher_foreground` 充当，专属品牌图标随 056/057 落地后替换；
  ④ 真机像素与输入法行为仍由用户自行装机验证（deferred — user decision 2026-09-21）。
- Risks: 已收敛——替代落点（052/053/054）开工前均 verified，未出现功能空窗；弹幕滚动/命中逻辑零触碰。

## Handoff notes

- Completed: 首页瘦身已 100% 完成并 verified（2026-09-24）。首页只剩品牌区、输入区（含草稿/Key 提示/模型档位/发送）、继续学习行、发现话题（弹幕形态不变）四块；三入口与历史主题列表消失，替代落点为标签栏与学习库。全部门禁绿：单测 615/0/0、check 0、Java-only 0、真机 6/6。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 本片 verified 后 049 族的 IA 链条（052→053→054→055）收口。剩余可并行为 056/057（图标）与 058（画布）；056/057 落地时应一并处理本片 Gaps ③（品牌图形标识替换）与推荐区换皮（Gaps ①）。
- Watch-outs: (1) `home_continue` 行依赖 `HomeViewState.continueTopic()`，改动 continue 语义时先改该判定点；(2) `home_key_hint` 现为被动提示——若要恢复点击进设置，须先在 `HomeActions` 重新立动作并过 PRD 6.2.13 口径；(3) 主题级改名/删除当前全应用无入口（Gaps ②），用户提出需求时立新任务，勿在首页复活列表；(4) `item_recommendation.xml`/`RecommendationMarquee.java` 零改动是有意决策，换皮须等图标资产并重新过 PRD 第 96 行验收。
