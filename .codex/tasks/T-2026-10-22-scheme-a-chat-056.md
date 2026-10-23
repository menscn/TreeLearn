# Filename: T-2026-10-22-scheme-a-chat-056.md
# Codex Harness Task: 方案 A 对话结构对齐与行内分支栈

id: T-2026-10-22-056
title: 方案 A 对话结构对齐与行内分支栈
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-scheme-a-morandi-requirement-052.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 430
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 052 已拍板方案 A 结构 1:1。055 已完成外壳与首页；本片把对话页改成胶囊顶栏、玻璃阅读面、行内分支栈与单行 dock 输入卡。
- Problem/background: 049 只换材质没有换结构：顶栏仍是裸 appbar，阅读面没有按方案 A 的 15dp 内边距排版，答案动作仍是透明方形点击区，子分支仍是 `ChildBranchPopup` 浮层，输入卡是两层卡片。真机与方案 A 的对话差异首先是结构。
- Users/scenarios: 单人自用 Android 手机（ALP-AN00，1200×2652，Android 13/SDK 33），平板沿用同一列；场景是阅读回答、复制/重新生成/新建分支、展开某条回答的子分支并进入、在当前分支继续追问。
- Expected outcome: 对话顶栏是一枚 52dp 胶囊玻璃；阅读面 15dp 内边距、28dp 圆角；学习者问题带 9sp「你」标签、12.5sp 正文；知芽标签用芽标 + 9.5sp 绿色；答案 13.2sp/1.82；动作行为 29dp 玻璃圆面（44dp 触达）；「从这条回答继续」下是行内分支栈（左侧 1dp 绿线 + 5dp 圆点 + 14dp 圆角玻璃行）；输入卡为 24dp 圆角单行 dock。
- Core flow and reversibility: 改 `screen_chat_body.xml`、`item_chat_message.xml`、`view_chat_answer_text.xml`、`view_answer_actions.xml`、`item_child_branch.xml`、`ChatScreen` 与 `MainActivity.openChildren` 的接线；删除 `ChildBranchPopup` 及其布局/仪器测试。回退为恢复这些文件并还原浮层。
- Inputs/outputs: 输入是方案 A 的 `.screen-chat`/`.reader`/`.actions`/`.branch-*`/`.composer--dock` 数值与现有玻璃 token；输出是对话页结构与行内分支承载。
- State and exception handling: 分支数据仍由 presenter 经 `onChildrenPressed` 读取真实 `ChildBranch` 列表；没有真实分支时行内区隐藏；折叠/展开不改变数据库与返回栈；浮层删除后不再有窗口级 dismiss 行为。
- Constraints and trade-offs: Java-only、零 AndroidX/Kotlin、无新依赖；发送键与附件键保持 44dp 触达（方案 A 的 38/29 与本项目无障碍底线冲突，取底线并在布局注释登记）；保留真机状态栏；不改文案语义（新增「你」「从这条回答继续」只是方案 A 已有标签）。
- AI recommendation and rationale: 子分支保持「点动作行 → presenter 读库 → 回填行内栈」的现有数据路径，只把承载从 `PopupWindow` 换成答案下方的行内区；同一时刻只展开一条回答的栈，展开另一条会收起前一条，替代浮层的「一次只开一个」约束。
- User-confirmation decisions: ① A + 莫兰迪毛玻璃；② 允许内置中文衬线；③ 接不了真实数据就删；④ 保留真机状态栏。
- AI-autonomous decisions: ① 答案动作按钮保留 44dp 热区，把 29dp 玻璃圆面画在内部，不缩触达；② 发送键保留 44dp，不缩到 38dp；③ 隐藏的搜索/语音/全屏入口保持 GONE；④ 删除已无消费者的 `ChildBranchPopup`、`popup_child_branches.xml` 及其仪器测试，行为覆盖改为行内栈的仪器测试。
- Acceptance evidence: 新 `SchemeAChatLayoutTest` RED→GREEN；全量 JVM 0 失败；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、Java-only 守门；059 真机对话截图。
- Facts: 方案 A 权威 `Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/A-晨雾玻璃.html`（`.topbar` 452-520、`.screen-chat .app-body` 769-775、`.reader` 776-791、`.user-msg` 792-808、`.ai-tag` 809-819、`.answer` 820-837、`.actions` 838-856、`.branch-label` 857-877、`.branch-stack` 878-919、`.composer--dock` 920-947、对话 markup 2318-2401）；当前 `ChatScreen.showChildren` 仍构造 `ChildBranchPopup`；`MainActivity.openChildren` 是唯一宿主接线。
- Assumptions: 行内分支栈展开后位于动作行下方，仍在同一 ScrollView 内，不需要新的滚动容器；玻璃圆面用 token 内的 `glass_chrome`/`glass_sheen`/`glass_edge` 可以读作方案 A 的 24% 白面。
- Unknowns: 行内栈展开后是否会顶动阅读位置；44dp 热区内的 29dp 圆面在真机上的观感是否过小。
- Risks: 删除浮层测试会减少一项覆盖，需用行内仪器测试补上；动作行改玻璃圆面可能被既有 `IconAssetsTest` 判红；输入卡从两层改单行会触碰多条旧断言。
- Deferred: 顶部标题 chevron（父卡判死）；实时模糊；平板专属对话重排。
- Non-goals: 改业务规则、数据库、接口、文案语义；恢复标题 chevron；恢复浮层；缩发送键到 38dp。
- Skipped non-critical questions and reason: 未追问 29dp 动作圆面与 44dp 热区的差值；无障碍底线优先，差异已登记。

## Goal

让对话页在真机上读作方案 A：胶囊玻璃顶栏、15dp 内边距的玻璃阅读面、方案 A 的问答排版与动作行、行内分支栈、24dp 圆角 dock 输入卡，同时所有真实入口保持可用。

## Scope

### In scope
- `android/app/src/main/res/layout/screen_chat_body.xml`。
- `android/app/src/main/res/layout/item_chat_message.xml`、`view_chat_answer_text.xml`、`view_answer_actions.xml`、`item_child_branch.xml`。
- `android/app/src/main/res/drawable/bg_action_glass.xml`、`bg_branch_item.xml`、`bg_branch_line.xml`、`bg_branch_dot.xml`、`bg_glass_dock.xml`。
- `android/app/src/main/res/values/dimens.xml`、`strings.xml`。
- `android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java`（行内分支栈接线）。
- `android/app/src/main/java/com/treelearn/app/MainActivity.java`（`openChildren` 的 listener 类型）。
- 删除 `ChildBranchPopup.java`、`popup_child_branches.xml`、`ChildBranchPopupPlacementTest.java`，改写 `ChildBranchPopupLifecycleInstrumentedTest` 为行内栈仪器测试。
- 新增 `SchemeAChatLayoutTest`。

### Out of scope
- 首页/外壳（055）、学习库/分支导航（057）、Web（058）。
- 推荐、翻译、朗读、全文编辑的业务规则。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改对话的承载与排版；发送、停止、复制、重新生成、新建分支、子分支进入、附件选择、草稿、滚动跟随、返回栈、返回栈恢复保持不变。子分支列表由浮层改为行内是本片唯一有意改变的行为承载。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatPresenter.onChildrenPressed` 生产 `ChildBranch` 列表；`MainActivity.openChildren` 转发；`ChatScreen.showChildren` 消费并绘制。`screen_chat_body.xml` 生产全部可绑定 id；`ChatScreen` 的字段与监听是唯一消费者。手机与平板共用 `screen_chat_body.xml`。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 对话顶栏改为胶囊玻璃；阅读面内边距 15dp；动作行改 29dp 玻璃圆面；子分支从浮层改为答案下方行内栈；输入卡改 24dp 单行 dock。
- Authoritative code/config/migration evidence: `screen_chat_body.xml`、`item_chat_message.xml`、`view_answer_actions.xml`、`ChatScreen.showChildren`、`MainActivity.openChildren`。
- Documentation surfaces checked: `android/README.md`、`.codex/context/source-of-truth.md`、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`、`Docs/04-ux-design/parity/screen-inventory.json`。
- Documentation files updated: `android/README.md` 删掉 `ChildBranchPopup` 行并新增行内栈说明；Web 平权规格把「子分支浮层」改为「子分支行内栈」；`screen-inventory.json` 与 `artifacts/parity/ledger.md` 由生成脚本重算（surfaces 16→15、components 18→17）。
- Producer/consumer/sibling/compatibility paths checked: `ChatMessageRowLayoutTest`、`IconAssetsTest`、`ChildBranchPopupLifecycleInstrumentedTest`、`ChatPresenter`、`MainActivity.openChildren`。
- Superseded phrases/examples reverse-searched: 「子分支弹窗」「ChildBranchPopup」「chat composer 两层卡片」「对话裸 appbar」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留 2026-09-28 的问答排版与动作行决定；本片只追加方案 A 结构。

## Acceptance criteria

- [x] 新 `SchemeAChatLayoutTest` 覆盖：胶囊顶栏、阅读面 15dp/28dp、问答排版、动作行玻璃圆面、24dp dock、行内分支区、无浮层引用。
- [x] 定向 JVM 命令 RED 非 0、GREEN 为 0，命令一致。
- [x] 全量 JVM 0 失败；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyNoKotlinDex` 退出码 0。
- [x] 子分支仍展示 presenter 读到的真实 `ChildBranch`，点击行内行进入对应分支；无分支时隐藏。
- [x] 所有可见控件都有真实动作；隐藏的搜索/语音/全屏入口保持隐藏。
- [x] 未自动提交或推送；未新增依赖；未恢复标题 chevron。

## TDD execution record

### RED — failing test first
- Test/scenario: `SchemeAChatLayoutTest` 的 7 条结构断言（胶囊顶栏无标题 chevron、15dp/28dp 阅读面、方案 A 问答字号、玻璃圆面动作行、单行 dock、行内分支栈、浮层文件已删）。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.SchemeAChatLayoutTest"`
- Exit code: 1
- Observed failure: `056-red.txt`：7 tests completed, 7 failed；失败点是旧结构：裸 appbar、旧阅读面内边距、`bg_icon_button` 动作行、两层输入卡、`ChildBranchPopup` 仍存在。
- Failure reason validated: 断言读的是布局、drawable、dimens、strings 与源码原文，失败原因与「结构仍是 049 的旧结构」一致，不是测试环境或路径问题。

### GREEN — minimum implementation
- Implementation: 重写 `screen_chat_body.xml`（胶囊顶栏、阅读面、单行 dock）、`item_chat_message.xml`（「你」/芽标/答案字号/行内分支区）、`item_child_branch.xml`、`view_answer_actions.xml`、`view_chat_answer_text.xml`；新增 5 个玻璃 drawable 与 29 个尺寸、2 条文案；`ChatScreen.showChildren` 改为填充行内 `chat_branch_list` 并删除浮层字段；删除 `ChildBranchPopup.java`、`popup_child_branches.xml`、`ChildBranchPopupPlacementTest.java`，把生命周期仪器测试改写为 `ChildBranchStackLifecycleInstrumentedTest`。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.SchemeAChatLayoutTest"`
- Exit code: 0
- Passing evidence: `056-green.txt`；行内栈真机仪器测试 `OK (1 test)`（`056-instrumented.txt`，`adb install -r` 后单类执行，未卸载应用）。
- Regression evidence: 全量 JVM `877 tests, 0 failed`（`056-jvm-all.txt`）；构建与 Java-only 守门 `EXIT=0`、`verifyNoKotlinDex: PASS (3 APK(s))`（`056-builds.txt`）；token 守门 10 项 OK（`056-token-guard.txt`）；`screen_inventory_selftest` 23/23 PASS。

### REFACTOR — optional
- Cleanup: 标题 chevron 未恢复（父卡判死）；`ChildBranch` 的注释从「弹层」改为行内栈；`chat_children_popup_title` 随浮层删除；`android/README.md` 与 `screen-inventory.json` 同步再生。
- Regression evidence: 全量 JVM 与守卫命令同上，均在新树上重跑。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 方案 A 数值以 `A-晨雾玻璃.html` 为准；新增尺寸进 `dimens.xml`。
- 分支数据路径不变：`ChatPresenter.onChildrenPressed` → `MainActivity.openChildren` → `ChatScreen.showChildren`。
- 行内栈同一时刻只开一条；展开另一条会收起前一条。
- 发送键与附件键保留 44dp 触达；动作行 29dp 圆面用背景内缩绘制。
- 删除 `ChildBranchPopup` 后不得留下无消费者的布局或测试。

## Verification report

- Verdict: PASS
- Evidence: `056-red.txt`（7/7 红）→ `056-green.txt`（7/7 绿）；`056-jvm-all.txt`（877 tests, 0 failed）；`056-builds.txt`（debug/release/androidTest APK 构建 0，`verifyNoKotlinDex: PASS (3 APK(s))`）；`056-instrumented.txt`（行内栈真机单类 `OK (1 test)`）；`056-token-guard.txt`（10 项 OK）；`screen_inventory_selftest` 23/23 PASS。
- Changed/deleted assertion rationale and equivalent behavior coverage: ① `ChatMessageRowLayoutTest.theLearnerMessageIsPlainTextRatherThanABubble` 的问题色从 `tl_ink` 改为方案 A 的 `tl_ink2`，仍断言「不是气泡表面色」，意图不变；② 同一文件的品牌标签断言从 `ic_ai_spark`/`tl_green_deep` 改为方案 A 的 `ic_brand_sprout`/`tl_green`，仍断言「不是 chip」；③ 输入卡断言从 `bg_composer_inner` 改为 `bg_glass_dock`，仍断言同一张卡片有玻璃底；④ `SchemeAChatLayoutTest` 的「无标题 chevron」从整文件收窄到 `chat_title` 元素，因为 `chat_follow` 合法地复用同一箭头字形；⑤ 删除 `ChildBranchPopupPlacementTest` 的 2 条浮层定位断言，行为覆盖由 `ChildBranchStackLifecycleInstrumentedTest`（真实 presenter 读库、渲染 2 行、刷新不折叠、点击回报 branchId）与 `SchemeAChatLayoutTest` 的源码 pin 接管。
- Gaps: 059 仍需在四屏真机截图中复核行内栈的观感与 44dp 热区内的 29dp 圆面大小；本轮未跑全量仪器测试（会卸载应用、清本地数据与 Key），只以 `adb install -r` 单类执行。
- Risks: 行内栈展开会在消息流内增加高度，可能顶动阅读位置；真机单类测试已通过但未量滚动锚点，059 截图时复核。

## Handoff notes

- Completed: 结构测试 RED→GREEN；布局/尺寸/文案/玻璃 drawable；行内分支栈接线；浮层删除与生命周期测试改写；全量 JVM、构建、Java-only 守门、真机单类仪器测试、token 与库存守卫。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 057 学习库与分支导航对齐；058 Web 莫兰迪 token；059 四屏真机证据与文档收尾。
- Watch-outs: 不要恢复 `ChildBranchPopup` 或标题 chevron；行内栈一次只开一条；隐藏入口保持 GONE；59 收尾时用 `adb install -r`，不要跑全量仪器测试。
