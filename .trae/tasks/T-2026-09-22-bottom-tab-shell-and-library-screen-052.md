# Treelearn Harness Task: 底部标签栏外壳与学习库屏（主题列表）

id: T-2026-09-22-052
title: 底部标签栏外壳与学习库屏（主题列表）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 340
shared_code_touched: true
depends_on: [T-2026-09-22-design-token-baseline-sync-050.md, T-2026-09-22-prd-v32-information-architecture-051.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿的信息架构与视觉**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。本片是新 IA 的**唯一改产品语义的一层**，也是本批其余 IA 片的前置：它先把「家」建好，后面的归档、搜索、首页瘦身才有落点。
- Problem/background: 工程现状是一级入口唯一（首页），二级屏（对话/画布/分支导航/搜索/设置）全部由 `MainActivity` 的私有 `swap()` 换进单一宿主容器 `shell_host`。新稿要求一级入口改为常驻底部标签栏（主页/学习库/设置），并把主题列表从首页搬进学习库。工程没有标签栏概念，也没有「学习库」这个概念。
- Expected outcome: App 底部出现常驻三标签栏；学习库能列出全部主题（标题、分支数、时间、未读点、左侧强调条），数据来自既有表；设置由二级屏升为一级标签；二级屏（对话/画布/分支导航）进出时不显示标签栏，返回时落回发起它的标签。
- Core flow and reversibility: 标签间切换可任意往返，是无损导航；从「主页」发起根问题进对话屏，返回落回主页；从「学习库」点主题进分支导航或对话，返回落回学习库。切换标签不丢进行中的事情——生成中的分支仍按其既有语义继续，本片不改动生成协调器。
- Inputs/outputs: 输入为标签点击、主题行点击、根问题提交（沿用 `HomePresenter`）；输出为标签栏的高亮状态与学习库的主题列表视图状态。
- Constraints and trade-offs: 零 AndroidX、Java-only、不新增第三方依赖、无 Fragment。标签栏必须是**常驻外壳**而不是每屏各画一份——否则返回时的落点与高亮会各自漂移。宁可把标签栏显隐判断集中在一处，也不让每个屏自己决定自己的位置。
- Acceptance evidence: 由真实命令输出与界面核对提供——构建与 Java-only 守门的退出码；标签切换与二级屏返回的仪器测试；学习库列出真实数据的仪器测试；既有测试全绿的回归证据。
- Facts: 宿主是 `res/layout/activity_main.xml` 的 `FrameLayout(main_root)` > `FrameLayout(shell_host)`；`AppLaunchInstrumentedTest` 通过 `R.id.main_root` 与 `R.id.home_draft` 定位第一屏，两者都不得消失。`MainActivity` 现有 6 个 `showX()` 方法（`showHome` 415 行位、`showSettings` 423、`showChat` 465、`showTree` 503、`showNavigation` 542、`showSearch` 582），每个都会把其它屏的引用与 presenter 置空。主题数据来自既有表，无新表、无 migration。
- Non-goals: 不做首页瘦身（055）；不做学习库的已完成区、恢复与删除（053）；不接学习库的搜索框（054）；不批量做图标资产（056/057）；不动画布（058）；不新增任何功能。

## Goal

App 出现一个常驻的底部三标签外壳（主页/学习库/设置），其中「学习库」是一个新建的、能列出全部真实主题的屏，且二级屏进出与标签切换的落点、高亮与返回行为都正确。

## Scope

### In scope
- `res/layout/activity_main.xml`：改为「内容容器 + 底部标签栏」两段结构；保留 `R.id.main_root`（启动测试依赖）与内容宿主 id。
- 新建 `res/layout/screen_tab_bar.xml`（三标签，每项触达区 ≥ `@dimen/touch_min`，含选中/未选中态）与 `res/layout-sw600dp/` 镜像（如需）。
- 新建 `res/layout/screen_library.xml` + `screen_library_body.xml`（标题、搜索框占位、分段控件「进行中 / 已完成」、列表区）与 `layout-sw600dp/` 镜像。本片只实现「进行中」分段的渲染与空态；「已完成」分段与搜索框分别为 053、054 的工作，本片只留出结构与 id。
- 新建 `screen/LibraryScreen.java`（`LinearLayout` 子类，形制参照 `screen/NavigationScreen.java`；不含任何判定逻辑）。
- 新建 `library/LibraryPresenter.java`、`library/LibraryViewState.java`、`library/LibraryData.java`、`library/LibrarySink.java`（形制参照 `navigation/` 下同名四件套）。
- 新建 `wiring/LibraryWiring.java`、`wiring/SqliteLibraryStore.java`（读既有表的主题列表），并在 `AppContainer` 暴露。**只读**：不改表、不改 migration、不加列、不动写路径。
- `MainActivity`：新增 `showLibrary()`；在**每个**既有 `showX()` 里清空 `libraryScreen`/`libraryPresenter`；标签点击路由到 home/library/settings；记录「当前标签」，使二级屏返回时回到发起它的标签；`swap()` 决定标签栏显隐（一级标签屏显示、二级屏隐藏）。
- 3 个标签栏图标 `res/drawable/ic_tab_home.xml`、`ic_tab_library.xml`、`ic_tab_settings.xml`（本片自带，因为标签栏是它新建的文件）。
- `res/values/strings.xml`：追加标签名与学习库文案，键名一律以 `tab_`/`library_` 前缀（见 Watch-outs）。

### Out of scope
- `res/values/colors.xml`、`dimens.xml`、`themes.xml`（050 的领地）
- `res/layout/screen_home*.xml`（055 的领地）、`res/layout/screen_search*.xml`（054 退役）
- 学习库的已完成区、恢复、删除（053）；学习库的搜索行为（054）
- 对既有二级屏的任何视觉改造（留给后续或不做）
- Java 之外的任何语言、任何第三方依赖

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**一级入口的承载方式**（唯一首页 → 底部标签栏）与**主题列表的位置**（首页 → 学习库）。必须原样保留的行为是：无可用 Key 时不创建主题且只在主页提示、生成的中断与重试语义、归档与删除语义、检索语义、二级屏的返回落点语义。本片**只加不删**——首页的主题列表此时仍在（055 才移除），搜索与归档入口此时仍在，因此本片的任何失败都不会造成功能缺失。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 换屏的唯一生产者是 `MainActivity` 的私有 `swap()`（`host.removeAllViews(); host.addView(screen)`）；标签栏的消费者是 `MainActivity`；主题列表的生产者是新建的 `wiring/SqliteLibraryStore`，消费端口是新建的 `library/LibraryData`。平台分支为 `layout/` 与 `layout-sw600dp/` 两套，两套都要覆盖。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 本片不改 `android/data/` 的写路径、不改 `android/core/`、不改 `android/ai/`，故这三侧无行为影响；新增的 store 是只读读取者。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 一级入口由底部标签栏承载（主页/学习库/设置）；新增学习库屏并列出全部主题；设置由二级屏升为一级标签；`activity_main.xml` 变为「内容容器 + 标签栏」结构。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/MainActivity.java`、`android/app/src/main/res/layout/activity_main.xml`、新建的 `screen/LibraryScreen.java` 与 `library/*`、`wiring/LibraryWiring.java`、`wiring/SqliteLibraryStore.java`。
- Documentation surfaces checked: `Docs/03-prd-draft/V3.2-…PRD.md`（051 产出，本片的口径依据）、`android/README.md`（应用层入口索引章节）、`Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`。
- Documentation files updated: `android/README.md` 的应用层入口索引新增「学习库与底部标签栏」一节（说明换屏注册、标签栏显隐规则与新增 store 的只读性质）。
- Producer/consumer/sibling/compatibility paths checked: `MainActivity` 的全部 6 个 `showX()` 方法；`AppContainer` 的装配；`AppLaunchInstrumentedTest`（依赖 `R.id.main_root` 与 `R.id.home_draft`）；`NavigationPresenterTest`、`HomePresenterTest`、`SearchPresenterTest` 等既有测试。
- Superseded phrases/examples reverse-searched: 「`shell_host`」「单一宿主容器」在注释与 README 中的表述——`activity_main.xml` 的注释写了「one Activity, one host」，本片改变了它的形态，注释必须同步。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次确有当前态文档变更。
- Historical/PRD handling: 本片依 `V3.2` PRD 实施；V3.1 不改。

## Acceptance criteria

- [x] 三个标签各自可达且切换正确：以仪器测试点击每个标签，断言对应屏被挂载、标签高亮与当前标签一致；三个标签之间任意往返均成立。-> `BottomTabShellInstrumentedTest.theThreeTabsAreAllReachableAndEachOneHighlightsWhatItShows`（PASS，`assertHighlighted` 对三个标签逐个比对 label 颜色与 icon tint），见 GREEN 的 `Passing evidence`。
- [x] 二级屏隐藏标签栏、返回落回发起标签：从「主页」进对话屏后返回落回主页；从「学习库」进分支导航后返回落回学习库——两条路径各有断言，不能只测一条。-> 两个用例各自 PASS：`aConversationOpenedFromHomeHidesTheBarAndBackLandsOnHome`、`aConversationOpenedFromTheLibraryHidesTheBarAndNavigationLandsBackOnTheLibrary`（学习库 → 对话 → 分支导航 → 逐层返回，每层都断言标签栏隐藏）。
- [x] 学习库列出真实主题：以仪器测试在既有表中造 2 个主题，断言学习库的可见主题数与字段（标题、分支数、时间、未读点、左侧强调条的存在性）与数据一致；空表时显示空态文案「还没有学习记录」并给出下一步指引。-> `theLibraryListsTheStoredTopicsWithTheirOwnFields`（2 行、标题、`library_topic_meta(1, "")` 前缀、`library_topic_unread(3)` 后缀、未读点 VISIBLE/GONE、强调条与行等高）与 `anEmptyLibraryShowsTheGuidanceInsteadOfABlankList`（同时含 `library_empty` 与 `library_empty_guide`），均 PASS。
- [x] 数据来源只读：`git status` 证明 `android/data/**/db/migration/**` 零改动；新增 store 只有读方法（无 INSERT/UPDATE/DELETE）。-> `git status --porcelain -- android/data` 输出为空（整个 data 模块零改动，`TreeLearnSchema.DATABASE_VERSION` 仍为 2）；`wiring/SqliteLibraryStore.java` 全文只有 `topics()` 一个方法，内部只有 `TopicDao.listOverviews(...)`。见证据文件第 7 节。
- [x] 保留行为不退化：`AppLaunchInstrumentedTest`（`main_root` + `home_draft` 仍可达）、`HomePresenterTest`、`NavigationPresenterTest`、`SearchPresenterTest` 及全量 `:app` 单测全绿。-> 真机全量仪器测试里 `AppLaunchInstrumentedTest` PASS（1/1）、本片 `BottomTabShellInstrumentedTest` PASS（5/5）；`:app` 单测 `testDebugUnitTest suites= 37 tests= 364 failures=0 errors=0`，全仓 reading A 699 / reading B 1166 且 failures=0。**但同一轮真机全量仪器测试整体退出码 1**：178 个用例里 26 个失败，散在 7 个**既有**数据层套件（`SqliteSchemaInstrumentedTest` 10/25、`AnswerRevisionGenerationOperationDaoInstrumentedTest` 5/12、`ContextSnapshotAiArtifactDaoInstrumentedTest` 4/12 等）。已判定与本片无关并给出两条依据：单独跑 `SqliteSchemaInstrumentedTest`（本片用例不在这一轮）得到**逐个相同**的 10/25 失败；失败全在同一个 `assertCause` 文案比对上——期望串 `"(code 2067"` 是给旧版 AOSP 格式写的，而本机文案是 `"(Sqlite code 2067 ...)"`，即约束按设计生效、只是文案前缀不匹配。本片不改这 7 个既有套件（那是另一片的工作），只回报。详见 `artifacts/evidence/T-052-tab-shell-library.txt` 第 11 节。
- [x] 平台分支覆盖：`layout/` 与 `layout-sw600dp/` 两套都覆盖标签栏与学习库；`.\gradlew.bat check` 退出码 0，Java-only 守门 `android/scripts/no-kotlin-check.ps1` 退出码 0。-> `layout/screen_tab_bar.xml` + `layout-sw600dp/screen_tab_bar.xml`、`layout/screen_library.xml` + `layout-sw600dp/screen_library.xml` 四份都在，两份标签栏逐项相同；`check` 退出码 0（`BUILD SUCCESSFUL in 1m 28s`），守门 `VERDICT: PASS (13 OK line(s))` 退出码 0。
- [x] 无 AndroidX/Fragment：**`main` 源集**的新增代码不引入任何 `androidx.*` 导入、不引入 Fragment、不新增第三方依赖（`androidTest` 侧沿用既有 `androidx.test` 测试栈，属既有约定，不计违规；由 Java-only 守门与依赖清单核对证明）。-> `screen/LibraryScreen.java`、`library/*`、`wiring/LibraryWiring.java`、`wiring/SqliteLibraryStore.java`、`MainActivity` 的生产代码里没有 `androidx.` 导入（守门脚本「no Kotlin coordinates on 53 variant classpath configuration(s)」与「no built APK contains Kotlin runtime bytecode」两条通过）；androidTest 侧沿用既有 20 个测试文件同样使用的 `androidx.test` 测试栈，`no-kotlin-check.ps1` 只扫构建配置与 APK dex，未报新增坐标。

## TDD execution record

### RED — failing test first
- Test/scenario: 先写 `android/app/src/androidTest/java/com/treelearn/app/BottomTabShellInstrumentedTest.java`（5 个用例：三标签可达且高亮与当前标签一致且有往返；从主页进对话屏返回落主页；从学习库进对话屏再进分支导航、逐层返回落回学习库；学习库列出真实主题的标题/分支数/时间/未读点/强调条；空表空态文案）。此时 `screen_tab_bar.xml`、`screen_library*.xml`、`LibraryScreen` 均不存在，androidTest 源集引用的资源 id 一个都解析不出来。
- Command: ./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.BottomTabShellInstrumentedTest
- Exit code: 1
- Observed failure: `:app:compileDebugAndroidTestJavaWithJavac FAILED`，编译器逐条报「错误: 找不到符号」，去重后涉及 37 个唯一代码位置、15 个尚不存在的资源 id（tab_bar、tab_home、tab_library、tab_settings、tab_icon、tab_label、library_root、library_rows、library_state、library_empty、library_empty_guide、library_topic_title、library_topic_meta、library_topic_unread、library_topic_accent），androidTest 无法编译，安装与设备执行阶段一步都没进入。
- Failure reason validated: 失败确由「标签栏与学习库尚不存在」造成，且不是环境阻碍或判据退化：同一轮 `./gradlew.bat --version` 退出码 0（Gradle 8.14.3 / Launcher JVM 21.0.10），说明工具链可用；失败任务是夹在 `processDebugAndroidTestResources` 之后的 `compileDebugAndroidTestJavaWithJavac`，即编译期而非运行期；15 个符号名与 Scope 里待新建的布局 id 一一对应，没有一个是既有 id，所以不存在「断言写错」或「偶发超时」这类替代解释。补齐实现后同一条命令退出码 0，见 GREEN。
- 计数口径更正（本键原先记的「39 个错误 / 16 个符号名」是读错口径，已按实测更正）：对 `artifacts/evidence/.t052-red.log` 实测——`grep -o "错误: 找不到符号" | wc -l` = 78（同一批报错被打印两轮：36 个位置各 2 次、`:357` 一处 6 次），`grep -oE "...\.java:[0-9]+: 错误:" | sort -u | wc -l` = 37（真实出错位置数），`grep -o "变量 [a-z_]*" | sort -u | wc -l` = 15（真实缺失 id 数）。命令与输出见 `artifacts/evidence/T-052-tab-shell-library.txt` 第 1 节。`tab_icon`/`tab_label` 这两个 id 在 GREEN 阶段因同布局重复 id 被 lint 拦下而改名为按标签命名（见 REFACTOR），此处保留 RED 当时的原始事实。

### GREEN — minimum implementation
- Implementation: **标签栏外壳**：`activity_main.xml` 由「`FrameLayout(main_root)` > `FrameLayout(shell_host)`」改为「`LinearLayout(main_root)` > `shell_host`（`0dp` + `weight=1`）+ `<include layout="@layout/screen_tab_bar"/>`」，`main_root` 与 `home_draft` 都保留；新增 `screen_tab_bar.xml` 与 `layout-sw600dp/` 镜像（三个标签项 `tab_home`/`tab_library`/`tab_settings`，每项 `minHeight=@dimen/touch_min`，图标 24dp、`importantForAccessibility="no"`，图标与标签按标签各自命名 id，选中态不着色于 XML）。**换屏与显隐**：`MainActivity` 新增 `Tab` 枚举、`currentTab`、`showTab(Tab)`、`selectTab(Tab)`、`markTab(item, iconId, labelId, selected)`、`settingsIsAFrontRoom()`、`showLibrary()`、`render(LibraryViewState)`，`swap(View)` 成为**唯一**判定标签栏显隐的地方（`screen == homeScreen || screen == libraryScreen || (screen == settingsScreen && settingsIsAFrontRoom())`），`onBackRequested()` 的搜索与对话分支改为 `showTab(currentTab)`、`leaveTree()` 与 `close()` 同样走 `showTab(currentTab)`，并把 `libraryScreen`/`libraryPresenter` 在**全部既有** `showX()`（`showHome`/`showSettings`/`showChat`/`showTree`/`enterNavigation`/`showSearch`）里清空。**学习库**：新增 `library/LibraryData`、`library/LibrarySink`、`library/LibraryViewState`（四相 + `Row`）、`library/LibraryPresenter`（唯一判定处）、`screen/LibraryScreen`（不含判定）、只读的 `wiring/SqliteLibraryStore`（只有 `topics()`，委托 `TopicDao.listOverviews`）、`wiring/LibraryWiring`（用 `BranchHeading.of` 解析树名），`AppContainer` 暴露 `libraryData()`；新增 `screen_library.xml` + `layout-sw600dp/` 镜像 + `screen_library_body.xml` + `item_library_topic.xml`（强调条/标题/「分支数 · 最近使用 · 未读数」/未读点）与 3 个 `ic_tab_*.xml`；`strings.xml` 只追加 `tab_*` 与 `library_*` 前缀键。
- Command: ./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.BottomTabShellInstrumentedTest
- Exit code: 0
- Passing evidence: 同一条命令 `BUILD SUCCESSFUL in 35s`；Gradle 自己写出的报告 `android/app/build/outputs/androidTest-results/connected/debug/TEST-ALP-AN00 - 13-_app-.xml` 头部为 `tests="5" failures="0" errors="0" skipped="0"`，5 个 `<testcase>` 逐个无 `<failure>`：三标签可达/高亮/往返（`assertHighlighted` 对三个标签逐个比对 `tab_*_label` 的 `getCurrentTextColor()` 与 `tab_*_icon` 的 `getImageTintList().getDefaultColor()` 是否等于 `tl_green_deep`/`tl_ink3`）、主页→对话隐藏标签栏→返回落主页并高亮主页、学习库→对话→分支导航→逐层返回落学习库并高亮学习库、学习库列出真实行（标题 `SYNTHETIC-LIBRARY-TITLE-A`、`meta` 以 `library_topic_meta(1, "")` 起并以 `library_topic_unread(3)` 收、未读点 VISIBLE/GONE、强调条与行等高）、空表空态同时含 `library_empty` 与 `library_empty_guide`。日志 `artifacts/evidence/.t052-green.log`；命令、退出码与报告原文见 `artifacts/evidence/T-052-tab-shell-library.txt` 第 2 节。
- Regression evidence: `cd android && ./gradlew.bat check` 退出码 0（`BUILD SUCCESSFUL in 1m 11s`，含 `:app:lintDebug`；lint 0 error / 46 warning，本片新增文件只剩既有基线里的 `Overdraw`）；`android/scripts/no-kotlin-check.ps1` 退出码 0 且 `VERDICT: PASS (13 OK line(s))`；`python .trae/scripts/dev/count_jvm_tests.py` 退出码 0，app `testDebugUnitTest suites= 37 tests= 364 failures=0 errors=0`（T-039 记录的同口径基线为 `suites= 32 tests= 334 failures=0`；本片自身贡献 1 suite / 5 tests，其 JUnit 报告头 `tests="5" failures="0"`，排除本片后 36 suites / 359 tests / 0 failures，无失败、无减少）；`git status --porcelain -- android/data` 输出为空 -> `android/data` 零改动，只读主张成立。

### REFACTOR — optional
- Cleanup: (1) 同一文件的多处改动改为逐个串行应用——并行编辑曾互相覆盖，导致 `:app:assembleDebug` 报 14 个编译错误（缺 `ColorStateList`/`ImageView`/`TextView` 导入、`render` 重载冲突），重做后 grep 回读逐条确认才恢复 `BUILD SUCCESSFUL`。(2) `render(ChatViewState)` 与 `renderPosition(ReadingPosition)` 补上与导航页同款的空守卫（既有的晚到回调会让进程 NPE 崩溃，本片新验收路径第一次把它走出来），并把导航页那段「只有它一个会晚到」的注释同步成事实。(3) 三个标签项原先共用 `tab_icon`/`tab_label`，同布局重复 id 被 `:app:lintDebug` 以 8 个 `DuplicateIds` 错误拦下（`abortOnError`）；改为按标签命名 `tab_home_icon` 等，纯声明式，图标与文案仍留在布局里。(4) 两个标签栏布局的横向行加 `android:baselineAligned="false"`，消除本片引入的 2 条 `DisableBaselineAlignment` 告警（lint 告警 48 -> 46）。(5) **交付前用 grep 逐条核对硬约束，抓到两处测试抓不到但约束明文要求的缺口并补齐**：`showChat(String, ReadingAnchor)` 漏了清空 `libraryScreen`/`libraryPresenter`（其余 5 个开屏方法都有）——正是作业单点名的「屏已离开但引用还在」缺陷；`LibraryPresenter.onResumed()` 在生产里是死代码（`onResume()` 没有学习库分支，学习库在前台被 resume 会去刷新首页）。补齐后逐方法复核 6 个开屏方法全部为 True，见 `artifacts/evidence/T-052-tab-shell-library.txt` 第 12 节。
- Regression evidence: 重构后重跑同一条目标命令仍 `Exit code: 0`（`tests="5" failures="0"`，`BUILD SUCCESSFUL in 34s`），`./gradlew.bat check` 仍退出码 0（`BUILD SUCCESSFUL in 1m 11s`，lint 0 error / 46 warning），Java-only 守门仍 `VERDICT: PASS`；见 `artifacts/evidence/T-052-tab-shell-library.txt` 第 2、3、4、10、12 节（两处中途修正与两处约束缺口的诚实记录）。

### TDD or sizing exception — only with prior user approval
- Exception type: 规模。**判定值 575 非空非注释生产行（新增文件 439 + 既有文件净增 136），超 200 软上限，也超 500 硬上限（超出 15%）**——本节原写的「未超 500 硬上限」是按 `estimated_production_lines: 340` 的估算写的，实测已推翻它。判定值取自仓库自带脚本的净增口径（见下方 `Alternative verification`）。
- Reason: 「标签栏外壳」与「学习库首屏」放在同一片，是因为两者共享同一批**必须同时存在**的产物：`activity_main.xml` 的容器结构、`screen_library*.xml` 的骨架、以及 `MainActivity` 的换屏注册。把标签栏单独拆出会产出一个「三个标签指向两个不存在的屏」的中间态——该中间态无法独立验收，且因为两片都要改 `MainActivity` 与 `activity_main.xml`（本批的共享代码区），拆开后仍必须串行，收益为零。已评估的备选切法是「按屏拆」（外壳一片、学习库一片），因上述共享文件原因被否决。据此记录理由而非警告。
- 超出估算的结构性来源（不是写歪）：两套平台分支的标签栏 **101 行**（`screen_tab_bar.xml` 50 + `layout-sw600dp/` 51，仓库既有约定要求两份逐项相同以免漂移）、两套平台分支的学习库页壳 **27 行**（11 + 16）、`screen_library_body.xml` 43 行与 `item_library_topic.xml` 26 行（本片要列的四个字段 + 强调条 + 未读点，缺一不可）、7 个新增 Java 生产文件 215 行（`library/` 四件套 + `LibraryScreen` + `LibraryWiring` + 只读 `SqliteLibraryStore`）。340 这个估算没有把前三项的平台分支重复算进去。
- 先例：`T-2026-09-21-search-page-and-home-entry-040.md` 的 `estimated_production_lines: 320` 同样超 200 软上限；`T-039`（646 行）与 `T-012`（1428 行）也都超 500 硬上限，均以「待裁定偏差、不得当成已批准例外引用」留档。
- User approval: **已由用户批准，2026-09-22。** 批准人：用户（本会话问答选择「批准例外，继续复核」）。批准时点：**在实现完成之后**，因此本例外如实记为**事后追认**，不得写成事先批准（依 `.trae/rules/harness-tasks.md` 的规模条款：规模例外的成立依据是用户的明确批准，批准晚于实现完成属流程缺陷、须如实记为事后追认）。
- 判定口径下的实测行数与超出比例: 575 行 / 500 行硬上限 → 超出 75 行，**超出 15%**。口径为零非空非注释生产行——新增文件按脚本全文计数（439），既有文件按「对本任务改动前后整文件剥注释后相减」的净增计数（136）。命令与输出见下方 `Alternative verification`。
- 剩余风险: (1) 本片一次交付 575 行，出问题时的二分定位粒度比小片粗；(2) 本例外按任务逐项记录，不自动适用于 053–058，它们若超线须各自申请；(3) 超出部分的结构性来源（两套平台分支布局重复 128 行）在 053–058 仍会出现，预计后续片也会贴线。
- Alternative verification: 本片完成后由 verifier 用 `python .trae/scripts/dev/count_production_lines.py` 对实际改动的生产文件复算，行数口径为非空非注释行。实测 575 > 500，作业单第 109 行预登记的「超 500 即判定失败并重新切分」判据**已触发**；实现侧如实记录并停下回报，不自行放宽、不自行判定通过；**用户已于 2026-09-22 就该触发给出明确批准（事后追认）**，故本片按「例外已批准」继续走独立复核，不重新切分。
- 三个口径并列（依 `.trae/rules/harness-tasks.md`「同一任务不得用两套口径给出两个结论；口径存疑时并列两个数并指明哪一个是判定值」）:
  | 口径 | 数值 | 说明 |
  | --- | ---: | --- |
  | **净增（判定值）** | **575** | 仓库脚本语义：新增文件按全文 439；既有文件对改动前后整文件用 `strip_java`/`strip_xml` 剥注释后相减得 136。可由仓库自带脚本机械复现 |
  | 逐行新增（剥注释） | 676 | 新增文件 439 + 对 diff 的新增行逐行剥注释得 237。会把块注释内部行误计为代码，偏高 |
  | 实现者手搓过滤 | 592 | 新增文件 439 + 只排除「以 `//` `/*` `*` `<!--` 起首」的新增行得 153。**不是仓库脚本的口径**，把带行尾注释的代码行也计为注释，偏低 |
  三者同向，**均超 500 硬上限**，不改变「已触发规模条款」这一结论；判定值取 575。实现者原记的 592 与其手搓的过滤方式一并如实留档，作为下一轮「测量必须用仓库脚本、不得手搓过滤」的实例。

## Implementation notes

- 标签栏必须做成**常驻外壳**：`activity_main.xml` 变成「内容容器（上）+ 标签栏（下）」；`swap()` 只替换内容容器的子视图，并按目标屏决定标签栏显隐。不要在每个屏里各画一份标签栏——那会让返回落点与高亮各自漂移，且 `layout-sw600dp/` 会多出五份副本。
- `MainActivity` 的既有约定必须遵守：每个 `showX()` 都要把其它屏的引用与 presenter 置空（现有 6 个方法都这么做）。新增 `showLibrary()` 时必须同步**在所有既有 `showX()` 里清空 `libraryScreen`/`libraryPresenter`**，否则会出现「屏已离开但引用还在，异步回调往里画」的旧缺陷。
- 学习库屏**不做判定**：能不能列出、列出什么由 `LibraryPresenter` 从 `LibraryData` 端口算出来，屏只画。这条约定与 `NavigationScreen`/`SearchScreen` 一致（它们的类注释都写了「It owns no rule」）。
- 主题列表的读取走新建的 `SqliteLibraryStore`，**只读**。不要为它新增表或列——父需求的 Out of scope 明确排除了 schema 与 migration 变更，本片只在既有表上加只读查询。
- `AppLaunchInstrumentedTest` 依赖 `R.id.main_root` 与 `R.id.home_draft`：前者是外壳根，后者是首页输入框。本片不得移除任何一个；055 瘦身首页时同样不得移除 `home_draft`。
- `res/values/strings.xml` 本批会被 052/053/054/055/056/057 共同追加，本片只追加 `tab_*` 与 `library_*` 前缀的键，不重排、不改动他人键。
- 3 个标签栏图标的描边与网格约定见 056/057 的资产约定（24dp 网格、描边 1.8、`android:tint` 由使用处着色），本片按同一约定产出，避免两套风格。

## Verification report

- Verdict: **PASS**（两轮独立复核：全量轮 `agent-dc545c33` 判 PARTIAL 并给出三处文字修法；窄范围复查轮 `agent-0eae13ee` 判 PASS）
- 独立复核原始结论摘要（不复用实现者结论）:
  - 全量轮已由复核者用**自跑命令**复现通过的项：规模判定值 **575**（新增文件 439 精确复现 + 既有文件净增 136 复现）；`cd android && ./gradlew.bat check` → `BUILD SUCCESSFUL in 16s`、退出码 0；强制 `--rerun-tasks` 重跑 `:app:testDebugUnitTest` → 退出码 0、`suites=37 tests=364 failures=0`；lint `0 errors, 46 warnings`；Java-only 守门 PASS（复核者另自行 `find` 无 `.kt`/`.kts`、APK 内无 Kotlin 运行时字节码）；依赖方向 PASS（`core`/`data`/`ai`/`tree-layout` 无反向 import）；AC1–AC7 逐条；Watch-outs 全项；RED/GREEN 的 `Command` 两行逐字符相同、`Exit code` 为纯净值；`android/data` 整模块零改动；**26 个真机仪器失败确认与 052 无关**（052 未碰 `ConstraintFailures.java`、`FirstTopicCreation.java`、`GenerationStabilizer.java` 及那 6 个受影响测试文件）；`main_root`（`activity_main.xml:14`）与 `home_draft`（`screen_home_body.xml:108`）均在。
  - 全量轮判 PARTIAL 的唯一原因：文档一致性——`android/README.md` 的 T-052 小节仍写「未经用户裁定 / 没有用户批准的规模例外 / 592」，与本文件的批准记录互斥。复核者明确「修好这三处文字即可转 PASS，**不需要改任何代码**」。
  - **本轮修正的三处**（由主会话执行，**均为文字、无代码改动**）：`android/README.md` 的 T-052 规模段；本文件的 `Evidence` 与 `Gaps`；本文件 AC 第 7 条补上 `main` 源集限定语。
  - 窄范围复查轮确认：任务文件与 README 四处规模表述彼此同源同值（均为 575、均记**事后追认**、均声明已解除）；任务文件 `592` 仅存于「非判定口径留档」与「三者并列」两处，无一处当判定值；`未裁定`/`没有用户批准的规模例外`/`尚未裁定` 在 T-052 语境零命中；README 其余 `没有用户批准的规模例外` 命中行（374/418/530/567/717/744/825）经逐章节归属核对**属 T-008/T-011/T-012/T-015/T-013/T-039 的正确历史记录，不报违规**；AC 第 7 条限定语已存在；**判定值 575 由复核者第二次独立复算（439 + 136）逐位吻合**；修正只触达 `android/README.md` 与本文件，未顺手改代码（未提交工作树上无 `git diff` 边界，复核者改用 mtime 作旁证：README/本文件 19:32–19:33，`android/app/**` 全部落在 17:23–18:54）。
  - **复核者提出的、本片不承接的遗留项**（须在后续片显式落位或显式记为不做）：(a) `render(TreeCanvasViewState)` 仍无空守卫，与 chat/navigation 同为「离开后可能晚到」的形状；(b) 学习库空态缺 PRD 6.2.16-5 的「载入示例主题入口」，学习库行缺 PRD 6.2.16-4 的「摘要」字段——两处**目前无任何切片认领**；(c) 「学习库按返回键落回主页标签」只写在 `MainActivity` 类注释（L232–234），README 的返回链未点明该落点；(d) 新 JVM 套件 `LibraryPresenterTest`（5 用例）在 TDD 段没有自己的 RED 记录，只随仪器测试那条目标命令；这四条均**不构成合规失败**，如实留档。
- Evidence: 已齐，全部落进 `artifacts/evidence/T-052-tab-shell-library.txt`（每条命令自带命令行与 `EXITCODE=` 行）：RED 的编译期失败（`Exit code: 1`，37 个出错位置 / 15 个缺失 id）与 GREEN 的同一条命令（`Exit code: 0`，`tests="5" failures="0"`）；`./gradlew.bat check` 退出码 0（lint 0 error / 46 warning）；`no-kotlin-check.ps1` 退出码 0 且 `VERDICT: PASS (13 OK line(s))`；`count_jvm_tests.py` 退出码 0（app 37 suites / 364 tests / 0 failures）；`git status --porcelain -- android/data` 为空；规模判定值 575 非空非注释生产行（新增文件 439 + 既有文件净增 136）；`showChat`/`onResume` 两处约束缺口的自查与补齐；设备唤醒前置条件与 GREEN 期间两处中途修正的诚实记录。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删除既有断言。新增断言覆盖「标签切换」「返回落点」「学习库列表」「空态」四类；既有断言全部保留。唯一改到既有生产行为的地方是 `MainActivity.render(ChatViewState)` / `renderPosition(ReadingPosition)` 加了空守卫（原先晚到的回调会让进程崩溃），这是修复而非改变语义，且屏幕为空时本来也无处可画。
- Gaps: (1) **规模偏差已由用户裁定**：判定值 **575** 非空非注释生产行（新增文件 439 + 既有文件净增 136），超 500 行硬上限 15%；用户于 2026-09-22 明确批准继续复核，批准发生在实现完成之后、如实记为**事后追认**（不得写成事先批准）。本项**已解除，不重新切分**。另有两个非判定口径的数字曾出现（实现者手搓过滤 592、逐行剥注释 676），三者同向、均超 500，判定值取 575；依 `.trae/rules/harness-tasks.md`「同一任务不得用两套口径给出两个结论」，任务与 README 只引用判定值；(2) **发现 7 个既有仪器测试套件的 26 个既有失败**（全量真机轮 `178 tests / 26 failures`，本片用例与 `AppLaunchInstrumentedTest` 都 PASS）。**成因经主会话逐条复核为四类，不是一类**（原始日志 `artifacts/evidence/.t052-full-androidtest.log` 第 133–237 行）：21 个是测试里写死 `(code NNNN` 文案前缀（真机 Android 13 是 `(Sqlite code NNNN ...), (OS error - 2:...)`）；**2 个是生产缺陷**——`android/data/src/main/java/com/treelearn/data/db/dao/ConstraintFailures.java:56` 的 `Pattern.compile("\\(code ([0-9]+)")` 在真机文案上取不到码（返回 0），于是 `FirstTopicCreation` 报的是 `STORAGE_REFUSED` 而不是 `TOPIC_ID_ALREADY_EXISTS`、stabilize 裸抛 `SQLiteConstraintException`；2 个是 `TreeCanvasStoreInstrumentedTest` 在断言消息里**无条件**调用 `.error()`；1 个是 `NavigationStoreInstrumentedTest` 夹具缺主题标题行。之所以过去没暴露：旧探针 `artifacts/evidence/T-004-constraint-code-probe.txt` 记录其环境是 API 36 模拟器，那台机器吐的正是 `(code NNNN`。**与本片无关已由独立复核确认**（`android/data`/`android/core`/`android/ai` 整模块零改动）。已由用户批准另立一片承接：`T-2026-09-22-constraint-codes-and-red-suites-060.md`（父需求 059）；(3) 真机上的输入法与像素核对仍由用户自行装机验证（沿用 2026-09-21 的既有决定）；(4) `layout-sw600dp/` 的实际观感只由构建、id 可达性与 `content_measure` 封顶证明，未做像素证据；(5) `theLibraryListsTheStoredTopicsWithTheirOwnFields` 里对「最近使用时间」只做 `meta` 前缀断言，时间文本本身随设备时区与当前时间变化，未逐字断言。
- Risks: `MainActivity` 已是 607 行且被本批四个任务连续修改，是本批最大的冲突源——必须严格串行（052 → 053 → 054 → 055），任一片未 verified 时下一片不得进入 `in_progress`。若标签栏显隐判断写散在多处，后续片极易引入「二级屏露出标签栏」的回归。

## Handoff notes

- Completed: 全部实现已落盘并通过：常驻底部标签栏外壳（`activity_main.xml` 改为「内容容器 + `<include layout="@layout/screen_tab_bar"/>`」、两套平台分支、三个 `ic_tab_*.xml`）；`MainActivity` 的 `Tab`/`showTab`/`selectTab`/`markTab`/`settingsIsAFrontRoom`/`showLibrary` 与**集中在 `swap(View)` 一处**的标签栏显隐判定，全部既有 `showX()` 都在清空学习库引用；学习库屏与 `library/` 四件套、只读 `SqliteLibraryStore`、`LibraryWiring`、`AppContainer.libraryData()`；`strings.xml` 只追加 `tab_*`/`library_*`。真机 5 个验收用例全过；`gradlew check`、Java-only 守门、`android/` 零改动、单测无失败均有退出码为证的证据（`artifacts/evidence/T-052-tab-shell-library.txt`）。`android/README.md` 已加「底部标签栏与学习库（T-052）」一节，并同步了「一个宿主」段落、`MainActivity` 表格行与系统返回的优先序。
- In progress: 无。实现与两轮独立复核均已完成，`status: verified`。
- Blocker: (1) **规模偏差已由用户裁定**：判定值 575 非空非注释生产行（新增 439 + 既有文件净增 136），超 500 硬上限 15%；用户于 2026-09-22 选择「批准例外，继续复核」，本例外如实记为**事后追认**（批准晚于实现完成）。本项**已解除**，不重新切分。(2) **本片之外的阻断项（只回报、不改）**：真机全量仪器测试 `178 tests / 26 failures`，26 个失败全在 7 个既有数据层套件上，成因是它们按旧版 AOSP 的 `(code NNNN)` 写死期望串，而本机文案为 `(Sqlite code NNNN ...)`；单独跑 `SqliteSchemaInstrumentedTest` 得到逐个相同的 10/25 失败，证明与本片无关。**用户已于 2026-09-22 决定「现在补一小片修掉」**，该片独立于本批，不由本片承接。
- Unblock condition: 不适用（规模裁定已给出；本片无其它阻塞）。
- Next steps: 053（学习库「已完成」分段与非根子树删除）直接在本片产物 `screen_library_body.xml`、`screen/LibraryScreen.java`、`library/*` 上续建；但按本作业单第 127 行的串行要求，053 必须等本片 `verified` 后才进 `in_progress`——若用户选择重新切分，则 053 的落点随切分结果调整。
- Watch-outs: (1) 不得引入 AndroidX 或 Fragment（本片 androidTest 侧沿用既有 `androidx.test` 测试栈，是既有约定）；(2) 不得移除 `main_root`/`home_draft`（本片保留了两者，`main_root` 由 `FrameLayout` 变 `LinearLayout` 但 id 不变）；(3) 新增 `showLibrary()` 已在全部既有 `showX()` 里清空学习库引用——后续片新增任何 `showX()` 必须照做，否则会出现「屏已离开但异步回调往里画」；(4) 学习库 store 只读，不得改 schema/migration/加列；(5) 本片只加不删——首页的主题列表、搜索与归档入口都还在，任何一片把它们删掉之前都要先有自己的落点；(6) **标签栏显隐判断只准有一处**（现在是 `swap()`），后续片不得让某个屏自己 `setVisibility` 标签栏；(7) 标签项的图标与标签**按标签各自命名 id**（`tab_home_icon` 等），不要再引入同布局重复 id——`:app:lintDebug` 在 `abortOnError=true` 下会直接让 `gradlew check` 失败。
