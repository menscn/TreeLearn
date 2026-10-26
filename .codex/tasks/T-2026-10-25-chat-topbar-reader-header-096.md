# Filename: T-2026-10-25-chat-topbar-reader-header-096.md
# Codex Harness Task: 对话面顶栏收成三格并把分支标题移进阅读头；画布空态补出口（AC2 + AC3）

id: T-2026-10-25-096
title: 对话顶栏收成三格 + 阅读头（AC2）与空态出口（AC3）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 260
shared_code_touched: true
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 AC2（重要）——顶栏标题截到约 10 字还挤三个图标；AC3（优化）——空态只有文字没有出口。
- Problem/background: 顶栏同时承担返回、分支树、总览、设置四个入口，标题只能分到「返回 + 三图标」剩下的宽度；手机上大约 10 个字就截断，而它正是当前分支的唯一名字。另一侧，画布的「这棵树还没有分支。」只说缺什么，不给下一步，读者停在一句否定上。
- Users/scenarios: 长标题分支的读者（他们要确认自己读的是哪一支）；刚建树、还没有分支的人（他们需要回到能继续提问的地方）。
- Expected outcome: 顶栏三格——返回 / 13sp 标题 / 更多；分支全名与时间进入消息区上方的阅读头，树入口是阅读头 meta 行上的 44dp 单元；总览与设置收进「更多」。画布空态在句子下面多一个 44dp 的「回到对话」。
- Core flow and reversibility: 只改 `screen_chat_body.xml`、`ChatScreen.java`、`screen_tree_body.xml`、`TreeCanvasScreen.java`、`strings.xml`、`dimens.xml`。回退＝顶栏恢复四图标、删除阅读头、删除空态按钮；无数据或状态机改动。
- Inputs/outputs: 输入是评审板 AC2／AC3 原文与 2026-10-22「标题下不做假 chevron」的既往拍板；输出是三格顶栏、阅读头三件套（标题／时间／树入口）与画布空态按钮。
- State and exception handling: 阅读头只在 `branchTitle` 非空时显示（未命名分支正在推导标题，单独一行时间会比空白标题更少信息）；`ChatViewState` 只有 LOADING／ERROR／READY，**没有 EMPTY 相**，所以对话页不新造退出键；画布有真实 `Phase.EMPTY`，按钮只在它下面出现。
- Constraints and trade-offs: 顶栏保持 52dp 玻璃胶囊与 6dp 状态栏偏移；标题仍是单行 13sp（全名交给阅读头两行 19sp 衬线）；阅读头占去消息区约 60dp 高度，换来标题第一次完整可读。
- AI recommendation and rationale: 把「识别这是哪一支」与「跳到树」合成一个阅读头，比继续在顶栏加宽更接近阅读顺序——读者先看标题，再读正文，最后才需要树。
- User-confirmation decisions: 22 条全做；旧的两行式对话顶栏此前已被否；标题下不做假 chevron（2026-10-22）。
- AI-autonomous decisions: ① 顶栏保留「更多」而不是把总览／设置彻底移出对话面，菜单只列已存在的两个手势，不发明分享；② 阅读头时间取分支第一行 `createdAt` 的相对时间，不按每条回答重复；③ 空态按钮复用 `TreeCanvasActions::onBackPressed`，不新增动作通道；④ 空态按钮文案「回到对话」，与画布的返回语义一致。
- Acceptance evidence: `SchemeAChatLayoutTest` 的三条（顶栏三格／阅读头／溢出菜单）与 `EmptyStateExitTest` 一条，先红后绿；证据 `096-combined-red.txt`／`096-combined-green.txt`。
- Facts: `screen_chat_body.xml:28`（appbar）、`:128`（reader header）、`:135`（19sp 衬线标题）、`:167`（树入口）、`:64`（更多）；`ChatScreen.java:336`（更多点击）、`:474`（`showMoreMenu`）、`:502`（`renderReader`）、`:513`（相对时间）、`:337`（树入口→`onNavigationPressed`）；`screen_tree_body.xml:231`（空态按钮）、`TreeCanvasScreen.java:44/77/89/128`；`strings.xml:75`（「回到对话」）；`dimens.xml:111-112`（19sp／12dp）。
- Assumptions: `chat_overview`／`settings_title`／`cd_chat_more`／`chat_branch_tree` 文案键均已在既有资源中存在或由本片补齐；`DateUtils` 在 API 21 起可用（项目 minSdk 24）。
- Unknowns: 无。
- Risks: 阅读头在极窄屏上标题两行会挤压首条回答的上沿；若真机截图显示拥挤，下压 `maxLines` 之前应先量第一屏可见行数。
- Deferred: 空态按钮不做真机点击回放——本轮真机核验只允许 `adb install -r` + 截图（见项目记忆）。
- Non-goals: 不改分支树页面；不改总览／设置的目的地；不改消息行内容与选区浮条；不给对话页造 EMPTY 相。
- Skipped non-critical questions and reason: 未问「更多里要不要放分享」——本构建没有分享通道，菜单不画不能行动的项。

## Goal

让顶栏回到「返回 / 标题 / 更多」三格，把分支全名、时间与树入口放进消息区上方的阅读头；并让画布空态在否定句下面给出唯一存在的下一步。

## Scope

### In scope
- `android/app/src/main/res/layout/screen_chat_body.xml`：三格顶栏、`chat_reader_header` 子树、`chat_branch_tree` 单元。
- `android/app/src/main/res/layout/screen_tree_body.xml`：`tree_empty` 与 `tree_empty_action` 同组居中。
- `android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java`：`showMoreMenu`、`renderReader`、树入口绑定。
- `android/app/src/main/java/com/treelearn/app/screen/TreeCanvasScreen.java`：空态按钮的字段、点击与相位显隐。
- `strings.xml`（`tree_empty_action`）、`dimens.xml`（`text_reader_title`、`reader_header_gap`）。
- 判据：`SchemeAChatLayoutTest` 三条方法与 `EmptyStateExitTest`。

### Out of scope
- 对话页 EMPTY 相（不存在）；分享通道；树页面布局；Web 与桌面面。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 顶栏从四图标变三格；阅读头新增。必须保留：返回与「更多」仍是 36dp 胶囊键、标题仍单行 13sp、树入口仍触发既有 `onNavigationPressed`、总览／设置仍到达原目的地、失败态与重试行不改、画布 `tree_back` 行为不变、消息滚动位置恢复与草稿不受影响。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `screen_chat_body.xml:28-196` 与 `screen_tree_body.xml:210-242`；消费者 `ChatScreen.java:336-337`（点击绑定）、`:474-500`（菜单）、`:502-518`（阅读头）、`TreeCanvasScreen.java:77-128`（空态显隐）；`res/layout-sw600dp/screen_tree.xml` 复用同一份 `screen_tree_body.xml`，Pad 面自动获得空态按钮。
- Native bridge or independent-client compatibility/update requirements: 无；不改本地库 schema 与 Android 业务语义。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 对话面顶栏由四图标收为三格；分支标题与时间从「每条回答的动作行」移到「分支级阅读头」；画布空态由一句话变为一句话 + 一个 44dp 出口。
- Authoritative code/config/migration evidence: `screen_chat_body.xml:28`、`:128`、`:167`；`screen_tree_body.xml:231`；`ChatScreen.java:502`；`TreeCanvasScreen.java:89`。
- Documentation surfaces checked: `.codex/rules/android.md`、`.codex/rules/multi-device.md`、`.codex/rules/observability.md`（无新增日志或遥测）；`android/README.md`（只述构建与运行，不述页面结构）。
- Documentation files updated: 无需改文档；设计理由写在布局注释与测试注释里。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "chat_overview\|chat_settings\|chat_nav" android/app/src/main` 只剩菜单文案与既有动作接口，不再有顶栏 id；`grep -rn "tree_empty_action" android/app/src` 命中布局、屏幕与字符串三处。
- Superseded phrases/examples reverse-searched: 未发现「顶栏四入口」「标题截断即全名」等旧表述留在当前态文档；旧两行顶栏方案只存在于历史评审记录。
- `documentation impact: none` reason and evidence: 改动是页面内部版式与一个空态控件，不改任何对外契约、配置或数据形状。
- Historical/PRD handling: 不改写 2026-10-22 的 chevron 拍板记录；本片只是遵守它。

## Acceptance criteria

- [x] 顶栏只有 `chat_back`、`chat_title`、`chat_more` 三格，36dp 胶囊键与 20dp 图标不变；`chat_nav`／`chat_overview`／`chat_settings` 不再出现在顶栏。
- [x] 阅读头位于消息列表之前，含 19sp 两行衬线标题、12sp 相对时间、44dp 树入口，且标题为空时整头隐藏。
- [x] 更多菜单只列总览与设置，分别调用既有 `onOverviewPressed`／`onSettingsPressed`，不出现分享。
- [x] 画布空态在 `tree_empty` 之下有 44dp 的 `tree_empty_action`，点击走 `TreeCanvasActions::onBackPressed`，只在 `Phase.EMPTY` 显示。
- [x] 对话页不新造空态按钮（`ChatViewState` 无 EMPTY 相），判据显式断言没有 `chat_empty_exit`。

## TDD execution record

### RED — failing test first
- Test/scenario: 两轮红合在一条命令里——① 顶栏三格／阅读头／溢出菜单三条文本判据（`SchemeAChatLayoutTest`）；② 画布空态出口（`EmptyStateExitTest`）。四条各自钉一个可观察事实：缺 id、缺 44dp、缺相位显隐、缺点击绑定。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.SchemeAChatLayoutTest.theTopbarIsTheSchemeACapsuleWithoutATitleChevron" --tests "com.treelearn.app.screen.SchemeAChatLayoutTest.theReaderHeaderCarriesTheTitleTheTimeAndTheTreeEntry" --tests "com.treelearn.app.screen.SchemeAChatLayoutTest.theOverflowCarriesOnlyTheGesturesThatExist" --tests "com.treelearn.app.screen.EmptyStateExitTest"`
- Exit code: 1
- Observed failure: `4 tests completed, 4 failed`——`SchemeAChatLayoutTest.java:59`（`chat_more`／`chat_reader_title` 等 id 在 HEAD 布局中不存在）、`SchemeAChatLayoutTest.java:181`（`showMoreMenu` 方法缺席）、`EmptyStateExitTest.java:49`（`tree_empty_action` 不存在，空态没有出口）。证据 `096-combined-red.txt`；两轮分跑的原始读数另见 `096-red.txt`（3 failed）与 `096-ac3-red.txt`（1 failed）。
- Failure reason validated: 把六个生产文件还原到 HEAD（`git show HEAD:` 覆盖，空白行差异按 `--ignore-cr-at-eol` 复算为 0）后同一条命令判红；恢复工作树后同一条命令转绿，说明红点全部来自本片的版式与控件，而不是文案键或测试脚手架。此前单独跑 096 时还撞到一次真实编译红：只还原 `ChatScreen.java` 会因工作树 `AnswerActionBar` 已删 `renderTime` 而编译失败，处置是把动作行三件套一并还原，证明本片的绿不依赖别片的半成品。

### GREEN — minimum implementation
- Implementation: 顶栏删 `chat_nav`／`chat_overview`／`chat_settings`，保留 `chat_back`／`chat_title`／`chat_more`；新增 `chat_reader_header`（标题 19sp 衬线两行、meta 12sp、`chat_branch_tree` 44dp）；`showMoreMenu` 只列总览与设置；画布空态加 44dp `tree_empty_action` 并接 `onBackPressed`。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.SchemeAChatLayoutTest.theTopbarIsTheSchemeACapsuleWithoutATitleChevron" --tests "com.treelearn.app.screen.SchemeAChatLayoutTest.theReaderHeaderCarriesTheTitleTheTimeAndTheTreeEntry" --tests "com.treelearn.app.screen.SchemeAChatLayoutTest.theOverflowCarriesOnlyTheGesturesThatExist" --tests "com.treelearn.app.screen.EmptyStateExitTest"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`、`EXITCODE=0`（`096-combined-green.txt`）；分轮绿另见 `096-green.txt` 与 `096-ac3-green.txt`。
- Regression evidence: Android 全量 109 套 / 954 用例（当次 `096-full-suite.txt`，`--rerun-tasks` 实跑，读数由测试结果 XML 复算）绿；`SchemeAChatLayoutTest` 其余用例（玻璃阅读面板、选区浮条、草稿）与 `ChatMotionLayoutStabilityTest` 均保持绿。

### REFACTOR — optional
- Cleanup: 阅读头把「标题为空则整头隐藏」做成一次性判断，避免时间行单独悬空；空态句子与按钮放进同一竖向容器，靠 `center_horizontal` 对齐，不各占一个居中子节点。
- Regression evidence: 重跑同一条 GREEN 命令与全量套件，读数不变。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿；AC2 与 AC3 先分跑取证，再用合并命令复跑一对，确保卡片记录的命令与证据逐字对应。
- Failure identity and repeat count: 四条独立失败各出现一次；无重复回潮。
- Stop condition hit: 四条判据同时绿且全量套件无新增红。
- Handoff: 105 的整跑与重打包包含本片。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 顶栏三格是「不假造数据」的延伸：标题不能被加宽，那就给标题第二个位置（阅读头），而不是把树／总览／设置删掉不交代归宿——三者都有既有手势，收进「更多」或移到 meta 行即可。
- 阅读头的时间只取分支根行：同一条分支里每条回答共享一个开始时间，逐条重复会让动作行再次变重；这也是 101 把时间从动作行撤走的同一理由。
- AC3 在对话页**不适用**：`ChatViewState` 只有 LOADING／ERROR／READY，没有空相；给它造一个「空对话」既没有状态来源，也会让读者以为可以清空分支。画布是唯一有真实空相的面，出口就是既有的返回手势。
- 空态按钮沿用 `touch_min`（44dp）与 `bg_button_ghost`，与失败卡重试键同一档控件语言。

## Verification report

- Verdict: PASS
- Evidence: `096-combined-red.txt`（4 failed / EXITCODE=1）、`096-combined-green.txt`（BUILD SUCCESSFUL / EXITCODE=0）、`096-full-suite.txt`（全量 109 套 / 954 用例，0 失败）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除；新增四条方法级判据与一条缺省断言（对话页不得出现 `chat_empty_exit`）。
- Gaps: 未做真机点击回放与截图；阅读头在真机窄屏上的首屏高度仍待 105 的统一截图核验。
- Risks: 阅读头占用消息区高度；若真机显示挤压，优先缩标题行数而不是撤掉时间或树入口。

## Handoff notes

- Completed: 顶栏三格、阅读头、溢出菜单、画布空态出口。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 097–105 的整树复核与重打包。
- Watch-outs: 不要把树／总览／设置塞回顶栏以「省一次点击」——那会把标题再次压回约 10 字；也不要给对话页补空态退出键，它没有空相。
