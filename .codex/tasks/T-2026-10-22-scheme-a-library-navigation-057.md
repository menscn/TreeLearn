# Filename: T-2026-10-22-scheme-a-library-navigation-057.md
# Codex Harness Task: 方案 A 学习库与分支导航结构对齐

id: T-2026-10-22-057
title: 方案 A 学习库与分支导航结构对齐
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-scheme-a-morandi-requirement-052.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 620
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 052 已拍板方案 A 结构 1:1。055 完成外壳与首页，056 完成对话；本片把学习库与分支导航改成方案 A 的胶囊顶栏、玻璃搜索、单容器分段、玻璃卡片列表、28dp 总结面板、节点分支行与虚线空态。
- Problem/background: 049 只换了材质。学习库仍是「返回箭头 + 20sp 标题」的裸页头、两颗各自着色的 44dp 分段按钮、16sp 话题卡与 3dp 左侧色条；分支导航仍是裸 appbar、20sp 衬线标题、16dp 内边距的旧总结卡、纯文字分支行。真机与方案 A 的差异首先是结构，不是色值。
- Users/scenarios: 单人自用 Android 手机（ALP-AN00，1200×2652，Android 13/SDK 33），平板沿用同一列。场景是打开学习库按「进行中／已完成」浏览与检索、从学习库恢复主题、进入分支导航查看当前分支 AI 总结、切换分支、归档/恢复/删除、进入对话或画布。
- Expected outcome: 学习库顶栏是 52dp 胶囊玻璃，标题为 19sp 衬线；搜索框是 44dp 全圆角玻璃；两个分段共享一枚 44dp 玻璃胶囊，选中项是白色内嵌亮面并带真实条数；话题卡为 12.5sp/600 标题 + 10.5sp 摘要 + 9sp 带树图标的元信息 + 14dp chevron，无装饰色条；列表底部有一行芽标说明。分支导航顶栏是同一枚胶囊，标题为 13sp sans；总结面板 28dp 圆角、15/15/13 内边距、13.5sp 标题、8.5sp 根分支标签、9sp「AI 总结」标签与 11sp/1.78 正文；分支列表头左侧「分支列表」右侧真实子分支数；分支行是 17dp 玻璃行，22dp 节点圆 + 12dp 树图标 + 11.5sp 标题 + 9sp 元信息；没有子分支时显示虚线空态说明。
- Core flow and reversibility: 改 `screen_library_body.xml`、`item_library_topic.xml`、`item_library_archived.xml`、`screen_navigation_body.xml`、`item_navigation_row.xml`，新增少量 drawable 与 dimens/strings，并调整 `LibraryScreen`/`NavigationScreen` 的绑定与 `ChineseSerifTitleTest`/`GlassSurfaceLayoutTest`。回退为恢复这些文件并还原旧布局。
- Inputs/outputs: 输入是方案 A 的 `.screen-library` / `.screen-branch` 数值与现有玻璃 token、真实 `LibraryViewState`（`rows.size()`、`archived.size()`）与 `NavigationViewState`（当前行 `childCount`、`summary`、`keyPoints`）。输出是两屏结构与真实数据绑定；不新增数据源。
- State and exception handling: 学习库的相位/分段/搜索态仍由 `LibraryPresenter` 与搜索栈决定；本片只画。导航页的可见行、选中、归档与删除计划仍由 `NavigationPresenter` 决定；空态只在「已就绪且当前分支子分支数为 0」时出现；总结缺失时仍显示既有空句，不伪造摘要。
- Constraints and trade-offs: Java-only、零 AndroidX/Kotlin、无新依赖；不改业务规则、数据库、接口、文案语义；保留真机状态栏；44dp 触达底线优先于方案 A 的 40dp 分段与 24dp 更多键，差值在布局注释与验收记录登记；不自动提交或推送。
- AI recommendation and rationale: 学习库分段继续用一个 `TextView` 承载「进行中 N」文本（条数用 `RelativeSizeSpan` 落到 9.5sp），这样既保留既有仪器测试读取 `library_segment_*` 文本的契约，也能画方案 A 的「标签 + 小号计数」；分段容器统一 44dp，选中亮面用上下各 4dp 内缩的背景绘制，触达仍是 44dp。导航页把列表头拆成左右两枚 `TextView`，右侧数字来自 presenter 已给的 `childCount`，不新增查询。
- User-confirmation decisions: ① A + 莫兰迪毛玻璃；② 允许内置中文衬线；③ 接不了真实数据就删；④ 保留真机状态栏；⑤ 按 Harness 实现。
- AI-autonomous decisions: ① 导航页标题按方案 A 的 `.topbar .title` 使用 13sp sans，学习库按 `.title-lg` 使用 19sp 衬线；同步修订 054 的 `ChineseSerifTitleTest`，保留「卡片标题不得使用衬线」的原覆盖；② 移除学习库话题卡与归档卡的左侧装饰色条（方案 A 没有，且状态已由文字/标记表达），并同步修订旧仪器测试的色条断言；③ 搜索框用新的全圆角玻璃形状，保留 `bg_library_search` 名字；④ 空态说明只在当前分支确无子分支时显示；⑤ 分段条数继续用真实 `rows.size()`/`archived.size()`。
- Acceptance evidence: 新 `SchemeALibraryNavigationLayoutTest` RED→GREEN；全量 JVM 0 失败；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyNoKotlinDex`；token 守门；059 四屏真机截图。
- Facts: 方案 A 权威 `Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/A-晨雾玻璃.html`（topbar 452-505、branch 890-1095、library 1108-1200、markup 2420-2600）；当前学习库 `LibraryScreen.render` 已拿到真实 `rows`/`archived` 计数；导航页 `NavigationScreen.render` 已拿到当前行 `childCount` 与 `summary`/`keyPoints`；`ic_nav_more`/`ic_nav_restore`/`ic_search`/`ic_sparkle`/`ic_tree_branch`/`ic_brand_sprout`/`ic_chevron_down`/`ic_chevron_right` 均已存在。
- Assumptions: 44dp 分段容器配合 4dp 内缩亮面，在真机上仍能读作方案 A 的 40dp 分段；顶部胶囊放在既有 ScrollView 内（与 055/056 的页内顶栏一致），滚动时随内容移动，本轮不改 wrapper 的滚动结构。
- Unknowns: 22dp 节点圆与 12dp 树图标在真机小屏上的可辨度；11.5sp 分支标题在 1200px 宽屏上的行长；总结正文 15 行截断是否与方案 A 的 300px 遮罩观感接近。
- Risks: 修改 `ChineseSerifTitleTest` 会减少一条旧断言，需保留「只有品牌/页面主标题可用衬线」的等价覆盖；触摸底线上调会让分段比方案 A 高 4dp；移除色条会触碰旧仪器测试。
- Deferred: 顶部胶囊吸顶（需改 phone/tablet wrapper 的滚动结构）；总结正文的渐隐遮罩；导航页平板专属双栏。
- Non-goals: 改业务规则、数据库、接口、文案语义；新增依赖；删除真实入口；恢复死按钮；实时模糊；深色模式。
- Skipped non-critical questions and reason: 未追问 40dp 分段与 44dp 触达的 4dp 差值；仓库无障碍底线优先，差值已登记。

## Goal

让学习库与分支导航在真机上读作方案 A：胶囊玻璃顶栏、玻璃搜索与单容器分段、紧凑玻璃卡片、28dp 总结面板、带节点的玻璃分支行与虚线空态，同时所有真实数据与既有手势保持可用。

## Scope

### In scope
- `android/app/src/main/res/layout/screen_library_body.xml`、`item_library_topic.xml`、`item_library_archived.xml`。
- `android/app/src/main/res/layout/screen_navigation_body.xml`、`item_navigation_row.xml`。
- 新增 `bg_glass_search.xml`、`bg_segment_active.xml`、`bg_summary_panel.xml`、`bg_root_tag.xml`、`bg_nav_row.xml`、`bg_nav_node.xml`、`bg_empty_branch.xml`。
- `android/app/src/main/res/values/dimens.xml`、`strings.xml`。
- `android/app/src/main/java/com/treelearn/app/screen/LibraryScreen.java`（分段计数样式、搜索态隐藏底部说明）。
- `android/app/src/main/java/com/treelearn/app/screen/NavigationScreen.java`（折叠图标旋转、列表头计数、空态、图标按钮类型）。
- 新增 `SchemeALibraryNavigationLayoutTest`；修订 `ChineseSerifTitleTest`、`GlassSurfaceLayoutTest` 与 `BottomTabShellInstrumentedTest` 的过时断言。
- `android/README.md` 同步两屏结构与字体适用范围。

### Out of scope
- 首页/外壳（055）、对话（056）、Web（058）。
- 顶部胶囊吸顶、总结遮罩、平板双栏。
- 业务规则、数据库、搜索语义、删除/归档规则。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改两屏的承载与排版。学习库的分段切换、搜索、恢复、删除确认，导航页的分支选择、折叠、归档、恢复、删除确认，进入对话/画布与返回栈全部保持不变；删除的只有纯装饰色条。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `LibraryPresenter` 生产 `LibraryViewState`；`LibraryScreen.render/showSearch` 消费。`NavigationPresenter` 生产 `NavigationViewState`；`NavigationScreen.render` 消费。`MainActivity.swap` 是学习库底栏让位与两屏返回的唯一判定点。手机与平板共用 `screen_library_body.xml` / `screen_navigation_body.xml`。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 学习库改为胶囊衬线顶栏、全圆角玻璃搜索、单容器分段与紧凑玻璃卡；导航页改为胶囊 sans 顶栏、28dp 总结面板、带节点分支行与虚线空态；导航标题不再是衬线。
- Authoritative code/config/migration evidence: `screen_library_body.xml`、`item_library_topic.xml`、`item_library_archived.xml`、`screen_navigation_body.xml`、`item_navigation_row.xml`、`LibraryScreen.drawSegments`、`NavigationScreen.render`。
- Documentation surfaces checked: `android/README.md`、`.codex/context/source-of-truth.md`、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`、`Docs/04-ux-design/parity/screen-inventory.json`。
- Documentation files updated: `android/README.md`（玻璃共享形状清单、字体适用范围、两屏结构说明）。
- Producer/consumer/sibling/compatibility paths checked: `ChineseSerifTitleTest`、`GlassSurfaceLayoutTest`、`IconAssetsTest`、`BottomTabShellInstrumentedTest`、`LibraryArchiveInstrumentedTest`、`LibraryTopicSummaryInstrumentedTest`、`LibraryScreen`、`NavigationScreen`。
- Superseded phrases/examples reverse-searched: 「navigation_title 引用衬线」「学习库两颗独立分段」「话题卡左侧色条」「裸 appbar 导航页」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留 2026-09-28 的参数化标题与玻璃决定；本片只追加方案 A 结构与导航标题的字面修订。

## Acceptance criteria

- [x] 新 `SchemeALibraryNavigationLayoutTest` 覆盖：学习库胶囊顶栏/衬线标题、44dp 全圆角玻璃搜索、单容器分段与真实计数、12.5sp 玻璃话题卡与 14dp chevron、底部芽标说明；导航胶囊 sans 顶栏、28dp 总结面板、真实列表计数、22dp 节点分支行、虚线空态。
- [x] 定向 JVM 命令 RED 非 0、GREEN 为 0，命令一致。
- [x] 全量 JVM 0 失败；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyNoKotlinDex` 退出码 0。
- [x] 分段条数与列表头计数来自 presenter 给的真实数据；当前分支没有子分支时才显示空态。
- [x] 既有可点区域（分段、搜索、恢复、删除、归档、分支菜单、进入对话/画布、返回）全部保留且有真实动作。
- [x] 未自动提交或推送；未新增依赖；未恢复死按钮。

## TDD execution record

### RED — failing test first
- Test/scenario: `SchemeALibraryNavigationLayoutTest` 的结构断言（学习库胶囊顶栏与 19sp 衬线标题、44dp 玻璃搜索、单容器分段与真实计数、紧凑玻璃卡与树图标元信息、底部说明；导航胶囊 13sp sans 顶栏、28dp 总结面板与 AI 总结标签、列表头真实计数、22dp 节点分支行、虚线空态）。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.SchemeALibraryNavigationLayoutTest"`
- Exit code: 1
- Observed failure: `10 tests completed, 9 failed`（证据 `artifacts/evidence/android-scheme-a-parity-2026-10-22/057-red.txt`）。断言消息与测试对应：`id library_appbar must exist`（学习库顶栏）、`id library_note must exist`（搜索态说明）、`the hint is the proposal's 11sp`（胶囊搜索）、`Schema A's 12dp vertical padding`（紧凑话题卡）、`with the library card's padding`（归档卡）、`the navigation leads with the same capsule`（导航顶栏）、`the summary is the 28dp glass panel`（总结面板）、`missing element: navigation_list_head`（列表头与空态）、`the branch row is Scheme A's 17dp glass row`（节点分支行）。唯一通过的是「既有入口存活」的纯 Java 断言，它不依赖新结构。
- Failure reason validated: 失败均落在 057 之前的两屏布局与旧测试口径上——`screen_library_body.xml` 没有 `library_appbar` / `library_note`，搜索框是 18dp 圆角字段而非全圆角胶囊，话题卡是 16sp 标题 + 左侧色条 + 16dp 内边距，`screen_navigation_body.xml` 没有胶囊顶栏、没有列表头/空态、总结卡仍是 16dp 内边距，`item_navigation_row.xml` 仍是纯文字行。断言读取的是真实布局文件与 `LibraryScreen`/`NavigationScreen` 源码，不是可被环境偶发的条件，因此 RED 被验证为「结构未实现」而非用例自身错误。

### GREEN — minimum implementation
- Implementation: 学习库重写为 52dp 胶囊玻璃顶栏（19sp 衬线标题）、44dp 全圆角玻璃搜索、单容器 44dp 分段（`RelativeSizeSpan` 把真实条数落到 9.5sp、选中亮面为 4dp 内缩 `bg_segment_active`）、12.5sp 紧凑玻璃话题卡（树图标元信息 + 14dp chevron，去掉装饰色条）、归档卡同规格并保留恢复/删除确认、列表底部芽标说明且搜索态隐藏；导航页重写为同一枚 52dp 胶囊 sans 顶栏（13sp）、28dp 圆角总结面板（15/15/13 内边距、AI 总结标签、折叠键旋转 180°）、左右分列的真实列表头（`childCount`）、17dp 玻璃分支行（22dp 节点圆 + 12dp 树图标 + 11.5sp 标题 + 44dp 更多/恢复触达）、仅在 `ready && current != null && current.childCount == 0` 时出现的虚线空态。`LibraryScreen` 新增分段计数与说明显隐，`NavigationScreen` 改图标按钮类型并接入列表头计数与空态。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.SchemeALibraryNavigationLayoutTest"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`，`testDebugUnitTest` 无失败项（`artifacts/evidence/android-scheme-a-parity-2026-10-22/057-green.txt`）。
- Regression evidence: 全量 JVM `952 tests / 0 failures / 0 errors`（app 887 + data 65，`057-jvm-all.txt`）；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:assembleRelease` 与 `verifyNoKotlinDex: PASS (3 APK(s) inspected across app/build/outputs/apk, no Kotlin runtime bytecode)` 退出码 0（`057-builds.txt`）；token 守门 `check_design_tokens: 0 check(s) failed`（`057-token-guard.txt`）。

### REFACTOR — optional
- Cleanup: 无结构性返工。GREEN 前只修了一次资源编译错误（`bg_summary_panel.xml` 注释里出现 XML 非法的 `--`），改为文字描述 token 后重跑同一命令通过；测试辅助方法 `elementAround`/`elementFor` 在写用例时已就位。
- Regression evidence: 同 GREEN 的全量 JVM 与构建记录（`057-jvm-all.txt`、`057-builds.txt`）。

### TDD or sizing exception — only with prior user approval
- Exception type: sizing（620 行 > 500 行硬线：单卡承载学习库 + 分支导航两屏的结构重写）
- Reason: 两屏共用同一套胶囊顶栏、玻璃形状与分段/列表判据，拆成两卡会让共享 drawable 与 `dimens` 的归属在两片之间漂移；且两屏的 RED 判据必须同时立在旧结构上，才能证明「结构未实现」而不是单屏缺件。TDD 本身仍按 `required` 执行，未申请 TDD 例外。
- User approval: 2026-10-22 用户确认 052 的 Harness 拆分顺序（055 ∥ 056 ∥ 057）并指示「按照Harness实现」「按照Harness，继续完成上述未完成的任务」；057 的 620 行估算随该拆分计划一并确认。
- Alternative verification: 不适用（定向 RED→GREEN、全量 JVM、三份 APK 构建、`verifyNoKotlinDex` 与 token 守门均已执行）。

## Implementation notes

- 方案 A 数值以 `A-晨雾玻璃.html` 为准；新增尺寸进 `dimens.xml` 并注明来源。
- 学习库分段容器 44dp（无障碍底线），选中亮面用 `bg_segment_active` 上下内缩 4dp 绘制；计数用 `RelativeSizeSpan` 落在 9.5sp。
- 导航页列表头右侧数字来自当前行 `childCount`；空态条件为 `ready && current != null && current.childCount == 0`。
- 折叠键改为 `ic_chevron_down` 图标，折叠时旋转 180°，头部保留 44dp 可点区。
- 更多/恢复键保留 44dp 触达，视觉字形 15dp 由 14.5dp 内边距绘出。
- 学习库搜索框保留 `bg_library_search` 名字，但改为全圆角 `bg_glass_search`。

## Verification report

- Verdict: PASS
- Evidence: `057-red.txt`（定向 RED，rc=1，10 例 9 败）、`057-green.txt`（同一命令 rc=0）、`057-jvm-all.txt`（全量 JVM 952/0）、`057-builds.txt`（debug/androidTest/release 三份 APK + `verifyNoKotlinDex: PASS`）、`057-token-guard.txt`（10 项检查 0 失败）。真机四屏截图按 052 的顺序由 059 交付，本片不单独出图。
- Changed/deleted assertion rationale and equivalent behavior coverage:
  - `ChineseSerifTitleTest.onlyTheBrandAndPageTitlesUseTheBundledSerif` → `onlyTheBrandAndLibraryTitleUseTheBundledSerif`：导航顶栏在方案 A 是 13sp sans，原断言改为 `assertFalse` 并保留正面对照（品牌与学习库标题必须仍是内置衬线），「卡片/搜索标题不得用衬线」的 `cardAndSearchTitlesStaySans` 原样保留。
  - `GlassSurfaceLayoutTest` 的学习库搜索断言：仍要求 `bg_library_search` 走玻璃字段色与玻璃描边，只把形状从 18dp 字段改为全圆角胶囊（经 `bg_glass_search`），覆盖面不缩小。
  - `BottomTabShellInstrumentedTest` 删除 `library_topic_accent` 断言：色条是纯装饰且方案 A 没有，话题状态仍由标题、未读点与元信息文字表达；该测试同时保留列表/详情/返回等行为断言。
- Gaps: 本片未跑任何仪器测试——`BottomTabShellInstrumentedTest` 的 `@Before`/`@After` 会 `clearTopics()`，在用户真机上执行会抹掉本机学习记录；按仓库规则不做全量仪器安装。因此真机证据只有「`adb install -r` 后应用可启动、数据保留」的冒烟结果，四屏观感截图与滚动/折叠走查归 059。
- Risks: ① 分段 44dp 比方案 A 的 40px 高 4dp（无障碍底线，已在布局注释登记）；② 顶部胶囊仍在页内 ScrollView 中，滚动时随内容移动（吸顶为 deferred）；③ 22dp 节点圆与 11.5sp 标题的小屏可辨度待 059 真机截图确认；④ 总结正文仍为 15 行截断，未做方案 A 的渐隐遮罩（deferred）。

## Handoff notes

- Completed: 学习库与分支导航两屏的 057 结构对齐（胶囊顶栏、玻璃搜索/分段、紧凑卡片、总结面板、节点行、虚线空态），真实计数与真实入口全部保留；定向 RED→GREEN、全量 JVM、三份 APK 构建、`verifyNoKotlinDex`、token 守门均留证。
- In progress: 无（057 已 verified）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 058 让 Web 消费莫兰迪玻璃 token 并跑 `pnpm --dir frontend verify`；059 汇总全量 JVM + 构建 + 四屏真机截图与证据 README，跑 `validate_tasks.py`、`run_all_guards.py --tier core` 与 metrics，最后关闭父需求 052。
- Watch-outs: ① 禁止为补真机证据去跑全量仪器——`BottomTabShellInstrumentedTest` 会清空本机话题；需要装机只看界面时用 `adb install -r`（保留数据）+ 截图，包名/组件是 `com.treelearn/com.treelearn.app.MainActivity`。② 学习库分段文本仍是「进行中 N」单串，改文案或拆 TextView 会同时打断 `BottomTabShellInstrumentedTest` 的文本契约与 `RelativeSizeSpan` 计数样式。③ 导航空态只在 `current.childCount == 0` 时出现；不要在 `childCount == 0` 之外放宽条件，否则会在加载中误报「没有分支」。
