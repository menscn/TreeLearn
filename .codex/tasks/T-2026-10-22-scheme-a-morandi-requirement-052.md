# Filename: T-2026-10-22-scheme-a-morandi-requirement-052.md
# Codex Harness Task: 方案 A 结构 1:1 + 莫兰迪毛玻璃 + 中文衬线字体需求父卡

id: T-2026-10-22-052
title: 方案 A 结构 1:1 + 莫兰迪毛玻璃 + 中文衬线字体需求父卡
template_version: 2
task_type: requirement
status: verified
parent_requirement: null
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 2026-10-21 的 047–051 只把「晨雾玻璃」材质铺到了既有原生结构上，用户把 APK 装到真机后认定「和方案 A 完全不一致」。根因不是色值，而是结构：Android 仍是裸 appbar、无胶囊顶栏、无悬浮 tab bar、推荐条是漂移 chips、聊天子分支是弹窗、学习库是两颗独立分段按钮。2026-10-22 用户拍板：以方案 A 的**结构、图标、按钮样式与位置**为 1:1 目标，叠加莫兰迪增量；允许内置中文衬线字体；能接真实数据就接，接不了就删，不做死按钮；保留真机状态栏。
- Users/scenarios: 单人自用 Android 手机为主（ALP-AN00，1200×2652，Android 13/SDK 33），平板横屏保留 `layout-sw600dp` 边界。场景：首页发问 / 对话阅读与继续分支 / 分支导航 / 学习库检索与分段 / 底部 tab 切换。
- Expected outcome: 真机首页、对话、分支导航、学习库在结构、图标、按钮形状与位置上读作方案 A：胶囊玻璃顶栏、首页大号衬线品牌、玻璃输入卡、232dp 推荐卡、带 chevron 的历史卡、悬浮胶囊 tab bar；对话为阅读面 + 行内「从这条回答继续」分支栈 + dock 输入；学习库为胶囊顶栏 + 玻璃搜索 + 单容器分段。莫兰迪只加低饱和柔光与非文字表面色，绿色仍是唯一动作色。
- Core flow and reversibility: 先扩 token 链（原型 `:root` → `tokens.json` → `colors.xml`/`glass.xml` → 重新生成 `tokens.css`）→ 打包中文衬线字体 → 改 Android 外壳与各屏结构 → 真机 `adb install -r` 截图复核 → Web 只消费新 token 并回归。回退按片删除：每片都能独立撤销，原始业务数据与未提交改动不动。
- Inputs/outputs: 输入是方案 A 的 CSS/HTML 结构与 2026-10-21 的玻璃 token；输出是新增莫兰迪 token、Android 字体资源、外壳/首页/对话/导航/学习库的结构改造、Web 材质消费与截图证据。不新增网络、存储或遥测副作用。
- State and exception handling: 只改视图与绘制；业务状态、草稿、滚动位置、返回栈、数据库和 API Key 不变。Android 仍不做实时模糊，使用静态柔焦场景。字体文件缺失或设备无法加载时回退系统 `serif`，不得崩溃。任何无法接通的控件直接删除，不保留空转入口。
- Constraints and trade-offs: Java-only、零 AndroidX、零 Kotlin、无新依赖（字体是资源不是依赖）；不改 SQLite/后端/接口；不自动提交或推送；保留真机状态栏，不画假 9:41；平板边界必须继续成立；设计数值只能来自 token 权威链。
- AI recommendation and rationale: 结构对齐优先于继续叠材质；先 token 后字体再各屏，避免每屏各自发明数值。首页附件入口没有从首页草稿到首条消息的完整附件链路（`RootQuestionSubmitter` 只建主题，聊天附件在 `ChatPresenter` 内异步解析），按用户「接不了就删」删掉首页附件图标；聊天顶部标题 chevron 没有独立的真实分支切换数据，删掉 chevron；聊天行内分支栈复用现有 `onChildrenPressed` 读取的真实 `ChildBranch` 列表，只把承载方式从弹窗改为行内。
- User-confirmation decisions: ① A + 莫兰迪毛玻璃；② 允许内置中文衬线字体；③ 能接真实数据就接，接不了就删，不做死按钮；④ 保留真机状态栏；⑤ 按 Harness 实现。
- AI-autonomous decisions: ① Android 结构优先，Web 本轮只消费材质 token 并回归，不追 Web 结构 1:1；② 衬线字体从设备 `/system/fonts/NotoSerifCJK-Regular.ttc` 提取 SC 单字重并随 APK 分发（OFL），避免联网下载阻塞；③ 莫兰迪新增 `glow-rose`、`glow-lilac` 与 `tint-rose/lilac/sand/sage`，只用于柔光、推荐卡图标底、分支标签等非文字表面；④ 首页推荐条保留既有漂移/暂停行为，只把 chip 改成 232dp 玻璃卡；⑤ 首页附件图标与聊天标题 chevron 删除；⑥ 字体只用于品牌、页面主标题与需要衬线的标题，正文仍 sans。
- Acceptance evidence: 053 token 守门 RED→GREEN；054 字体资源与结构测试；055–057 Android 单测 RED→GREEN、`:app:assembleDebug`、`:app:verifyNoKotlinDex`、`adb install -r` 四屏截图；058 Web 测试与 `pnpm --dir frontend verify`；059 整跑、`validate_tasks.py`、`run_all_guards.py --tier core` 与证据目录。
- Facts: 方案 A 权威文件 `Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/A-晨雾玻璃.html`；token 权威 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root`（第 22–98 行）；Android 单 Activity 外壳在 `MainActivity`/`activity_main.xml`；tab bar 如今在 `main_root` 的纵向流里；首页推荐是 `RecommendationMarquee` 双行漂移 chips；聊天子分支由 `ChildBranchPopup` 弹窗承载；设备已有 `NotoSerifCJK-Regular.ttc`（26,283,508 字节），仓库无任何字体资源；fontTools 4.61.1 可用。
- Assumptions: 设备系统衬线字体的 OFL 许可允许随 APK 分发；提取出的 SC 字重能覆盖用户输入的主题与回答标题；悬浮 tab bar 用宿主的底部内边距让出空间即可保持滚动与输入法行为；方案 A 的 232dp 推荐卡在双行漂移容器里仍能读作同一设计。
- Unknowns: 26MB 字体对 APK 体积与安装时间的影响；部分厂商 ROM 对 `.otf` 资源字体的加载差异；莫兰迪低饱和 tint 在真机屏幕上的色偏程度。
- Risks: 字体体积过大；悬浮 tab bar 若只改布局不改宿主会让首页/学习库底部被遮住；分段/搜索的结构改造可能破坏既有搜索状态；改动既有断言时必须保留等价行为覆盖；token 数写入散文会被事实源守门判红。
- Deferred: Android 实时背景模糊、深色模式、Web 结构 1:1、字体多字重/可变字体、推荐卡按分类整卡着色（当前只给图标底着色）。
- Non-goals: 不改业务逻辑、数据库、接口、权限、文案语义（仅按方案 A 调整标题/副标题的展示位置）、不新增依赖、不引入 AndroidX/Kotlin、不自动提交或推送、不删除用户未提交的改动。
- Skipped non-critical questions and reason: 未追问字体字重数量与是否做子集化；单字重 + 全量 SC 是「允许内置」下的保守选择，体积风险在验收中实测登记。未追问推荐条双行是否改单行；保留漂移行为比重写交互更符合「不为改而改」。

## Goal

把用户选定的「方案 A 结构 1:1 + 莫兰迪增量 + 中文衬线字体」落成 Android 生产界面，并在 Web 侧消费同一批新 token，以守门、构建、真机和浏览器证据收口。

## Scope

### In scope
- 片 053：莫兰迪 token 链与 Android 场景柔光。
- 片 054：中文衬线字体资源、许可与标题应用。
- 片 055：Android 外壳悬浮 tab bar + 首页结构对齐。
- 片 056：Android 对话结构对齐（胶囊顶栏、阅读面、行内分支栈、dock 输入）。
- 片 057：Android 学习库与分支导航结构对齐。
- 片 058：Web 消费新 token 与整线回归。
- 片 059：整跑、真机四屏截图、文档同步与 Task v2 收口。

### Out of scope
- Web 结构 1:1、深色模式、实时模糊、字体子集化工具链。
- 业务规则、数据库、网络、权限、依赖、账号/同步/遥测。
- 提案 HTML 与三套方向稿的视觉修改。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 外观结构向方案 A 对齐；所有既有可点区域、草稿、滚动、返回栈、输入法、数据读取与缓存行为不变。首页附件图标与聊天标题 chevron 是唯一删除的入口，理由与验证记录在本卡。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): token 生产者是原型 `:root`；消费者是 `tokens.json`、`colors.xml`、`glass.xml`、生成的 `tokens.css`、`PaperRuleBackdrop` 与 Web `glass.css`。结构生产者是 `activity_main.xml`、各 `screen_*_body.xml` 与对应 Screen 类；`MainActivity.swap/selectTab` 是 tab bar 可见性与选中态唯一决策点。
- Native bridge or independent-client compatibility/update requirements: 不涉及原生桥、数据库或夹具；Android 与 Web 各自消费 token，不互相调用。
- Behavior-neutral non-applicability reason, if applicable: 字体与材质不改变 View 层级；结构改造改变层级但不改变 View id 对应的业务绑定。

## Documentation impact record

- Changed runtime facts: Android 悬浮 tab bar 与底部让位；首页推荐卡尺寸与附件入口删除；聊天子分支改为行内；学习库分段为单容器；新增字体资源与莫兰迪 token。
- Authoritative code/config/migration evidence: 原型 `:root`、`tokens.json`、`colors.xml`、`glass.xml`、`activity_main.xml`、`screen_home_body.xml`、`screen_chat_body.xml`、`screen_library_body.xml`、`screen_navigation_body.xml`、`MainActivity.java`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`android/README.md`、`.codex/rules/ux-design.md`、`.codex/rules/android.md`、`.codex/context/source-of-truth.md`、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`。
- Documentation files updated: 053 更新设计系统变更记录；054 更新 Android 字体说明；055–057 更新 Android README 的结构与入口变化；059 写证据 README 与提案落地状态。
- Producer/consumer/sibling/compatibility paths checked: `HomeRecommendationStripTest`、`RecommendationTagTest`、`GlassSurfaceLayoutTest`、`GlassMaterialTokensTest`、`HomeScreen`、`ChatScreen`、`LibraryScreen`、`NavigationScreen`、`MainActivity.swap`。
- Superseded phrases/examples reverse-searched: 「tab bar 在 `main_root` 纵向流里」「首页附件入口」「推荐 chips 双行漂移」「子分支弹窗」「学习库两颗独立分段」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 2026-09-22 与 2026-10-21 的历史记录保留；本次追加 2026-10-22 结构对齐说明，不改写历史结论。

## Acceptance criteria

- [x] 053–057 的定向 JVM 测试 RED→GREEN，全量 `:app:testDebugUnitTest` 0 失败。
- [x] `:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:verifyNoKotlinDex` 通过。
- [x] `adb install -r` 后真机首页、对话、分支导航、学习库四屏与方案 A 的结构/图标/按钮位置一致；既有数据未被清理。
- [x] 首页无死按钮；聊天顶栏无死 chevron；所有保留入口都有真实数据或真实跳转。
- [x] 中文衬线字体在真机品牌与页面主标题上生效，字体许可随仓库分发。
- [x] token 四方守门、`validate_tasks.py`、`run_all_guards.py --tier core` 退出码 0 或如实记录既有红项。
- [x] Web `pnpm --dir frontend verify` 16/16 且 Vitest 全绿。
- [x] 未自动提交或推送；未新增依赖；未删除用户未提交的改动。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（需求父卡）
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 父卡只承载已确认需求；实现由 053–058 各自 TDD，059 做整跑与取证。
- User approval: 用户 2026-10-22 已确认四项决定并要求按 Harness 实现。
- Alternative verification: 子卡 RED/GREEN、构建、真机与浏览器证据。

## Implementation notes

- 任务顺序：052 → 053 → 054 →（055 ∥ 056 ∥ 057）→ 058 → 059。
- 方案 A 的结构与 CSS 数值以 `A-晨雾玻璃.html` 为准；token 值仍以原型 `:root` 为权威。
- 首页附件与聊天标题 chevron 已按用户规则删除；不得以「更接近方案 A」为理由恢复死入口。
- 行内分支栈只改变承载方式，不改变 `ChatPresenter` 读取 `ChildBranch` 的路径。
- 真机验证只用 `adb install -r`，不跑会卸载应用的整包仪器测试。

## Verification report

- Verdict: PASS
- Evidence:
  - 结构片：053/054/055/056/057 各自完成定向 RED→GREEN，GREEN 退出码 0；全量 JVM 在 059 以 `--rerun-tasks` 实跑，app 887 / data 65 tests，0 failures / 0 errors（`artifacts/evidence/android-scheme-a-parity-2026-10-22/059-jvm-all.txt`）。
  - 构建与 Java-only：debug / androidTest / release 三份 APK `BUILD SUCCESSFUL`；`verifyNoKotlinDex: PASS (3 APK(s) inspected ...)`，退出码 0（`059-android.txt`）。
  - 真机：`adb install -r` 后本机 3 个真实主题与历史保留；首页、对话、分支导航、学习库四屏截图保留真机状态栏（`059-home.png`、`059-chat.png`、`059-navigation.png`、`059-library.png`）。结构核对：首页胶囊顶栏/衬线品牌/玻璃输入卡/推荐与历史卡/悬浮胶囊 tab bar；对话胶囊顶栏 + 阅读面 + 行内分支栈 + dock；导航 28dp 总结面板 + 真实列表头 + 22dp 节点行 + 虚线空态；学习库胶囊顶栏 + 玻璃搜索 + 单容器分段「进行中 3 / 已完成 0」。
  - 死按钮清理：首页附件图标（无完整附件链路）与聊天标题 chevron（无真实分支切换数据）已删除；聊天行内分支栈读真实 `ChildBranch` 列表，导航行读真实 `childCount`，空态只在真实无子分支时出现；学习库搜索与分段均接真实数据。`055`–`057` 的 GREEN 测试覆盖这些入口与空态条件。
  - 字体：`res/font/noto_serif_sc.otf` 随 APK 分发，OFL 1.1 许可在 `assets/licenses/NotoSerifCJK-OFL.txt`；`ChineseSerifTitleTest` 校验 sfnt 头、许可文本、品牌与学习库标题引用以及导航/卡片回退 sans（`054-green.txt`）。
  - 守门：token 四方 10/10 OK、0 failed（`059-token-guard.txt`）；`validate_tasks.py` 0 errors；core guard `pass=39 fail=0 inconclusive=0`、`VERDICT: PASS`、`EXITCODE=0`（`059-guards-core.txt`）；metrics 已按当前任务卡重生成（`artifacts/metrics/report.md`）。
  - Web：`verify` 16/16，Vitest 19 files / 116 tests（`058-verify.txt`、`058-vitest-all.txt`）。
  - 范围纪律：HEAD 仍为 `f77d34f`，无自动提交/推送；未改 `package.json`、锁文件或 Gradle 依赖清单；未删除用户未提交改动。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除 `ChildBranchPopup` 及其仪器测试是 057 结构改造的直接结果，分支承载改为聊天行内栈与独立导航屏；等价行为覆盖由 `ChatScreen` 行内分支栈测试与 `NavigationScreen` 列表/空态测试承接。删除首页附件入口与聊天 chevron 是按用户「接不了就删」裁决，避免死入口；没有删除任何真实可用的数据绑定。
- Gaps: 未运行仪器测试（会卸载应用并清本地数据/API Key）；Pad 横屏四屏未截图；`tint-rose`/`tint-lilac`/`tint-sand` 生成后仍无消费者；顶部胶囊吸顶、AI 总结渐隐遮罩、平板双栏、Web 结构 1:1、深色模式、实时模糊仍 deferred。
- Risks: 26MB 字体带来的 APK 体积与安装时间；厂商 ROM 对 tint 的色偏；22dp 节点与 9sp 计数在小屏上的可辨度；未覆盖输入法与悬浮 tab bar 的交互回归。
- Handoff notes: 父卡收口完成；后续按 deferred 清单单独开片，不回填死入口。

## Handoff notes

- Completed: 053–059 全部子片；Android 结构 1:1 + 莫兰迪 + 中文衬线；Web 材质消费；真机四屏与整跑证据。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 从 deferred 清单挑下一片（建议顶部胶囊吸顶或 Pad 横屏证据）；为剩余 tint 找真实消费面或退役。
- Watch-outs: 不恢复首页附件与聊天 chevron；不把莫兰迪 tint 用到正文文字；不手改生成的 `tokens.css`、`screen-inventory.json`、`artifacts/parity/ledger.md`；任务卡改动后先重生成 metrics 再跑 core guard。
