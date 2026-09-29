# Filename: T-2026-09-28-annot-menu-prose-highlight-027.md
# Treelearn Harness Task: 菜单 6 格 + prose 高亮（T-B）

id: T-2026-09-28-027
title: 菜单 6 格 + prose 高亮（T-B）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-28-chat-highlight-annotation-025.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 380
shared_code_touched: true
depends_on: [T-2026-09-28-annot-data-layer-026.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: 见 T-2026-09-28-chat-highlight-annotation-025.md。本任务交付标记动线的前半段：菜单扩格与 prose 高亮。

- Problem/background: 选词菜单 4 格无标记动作；正文无法高亮。
- Expected outcome: 6 格菜单（+高亮/批注占位，批注回调本任务只挂接口不实现弹窗）；prose 段高亮落库与二次点击取消；黄色 token 三方同步。
- Core flow and reversibility: 方案 §2.2/§2.3——建立高亮（Markdown 渲染后统一 flush）；精确重合二次点击 → 恢复选区 + 首格「取消高亮」；部分重叠 → 合并并区间落库。
- State and exception handling: 流式中高亮/批注格画出但置灰（与 QUOTE 同规则）；store 拒绝可见。
- Constraints and trade-offs: **菜单格一律图标承载（24dp/1.8 描边/圆角端点），不得文字按钮**（15:23 用户确认硬约束）；「取消高亮」是图标态切换（记号笔→带斜杠记号笔），不是独立格。
- AI-autonomous decisions: `Highlighter` 独立类（不与 MarkdownRenderer 混排）。
- User-confirmation decisions: D 黄色 `#FFF3B0`；C 新增 MARK 组。
- Acceptance evidence: 本文件 TDD + 真机截图。
- Facts: `WordMenu.java:33` Item 枚举；`popup_word_action.xml` 4 等宽格；`ChatScreen:1170` SelectionMenuCallback；`MarkdownRenderer.java:331` 代码块灰底 span（方案 §1 核实）。
- Assumptions: prose 已有 `LinkMovementMethod`，ClickableSpan 可用。
- Unknowns: 无。
- Risks: R-2 绘制顺序 / R-3 ClickableSpan 吞长按（V-2/V-3 专项）。
- Deferred: 单元格（T-C）、批注弹窗与便签（T-D）。
- Non-goals: 不改思考链/译文数据路径；允许调整 `ChatScreen.render()` 的菜单存活判定。
- Skipped non-critical questions and reason: 无。

## Goal

prose 段高亮全链路（建/合并/取消/持久化/重进仍在）+ 菜单 6 格图标化，token 三方同步后守门全绿。

## Scope

### In scope
- `WordMenu`：Item +HIGHLIGHT/+NOTE、Group +MARK、`entriesFor`。
- `popup_word_action.xml`：4 格→6 格，全图标（新增记号笔/便签/带斜杠记号笔 3 矢量，入 `CHAT_ICON_ASSETS`）。
- `WordMenuPopup`：Listener +2 回调；置灰规则。
- `Highlighter.java`（新）：span flush、精确重合判定接 `Annotation`、合并落库。
- `ChatScreen`：prose 段接线（选区→高亮；ClickableSpan 二次点击）。
- `WordMenuPopupInstrumentedTest`：真实 `ActionMode`→`SelectionMenuCallback`→`WordMenuPopup` 设备回归与截图。
- token 三方同步：`yellow-highlight`/`tl_yellow_highlight`（资源 15→16）+ design-system README + 守门 0 failed。

### Out of scope
- 单元格选择与高亮（T-C）；批注编辑弹窗/便签锚（T-D，本任务 NOTE 回调可临时空实现但接口定稿）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 既有 4 格（问AI/新建主题/复制/全选）语义与顺序不变，新增格插在中间；选区读取仍遍历 `answerViews`；单卡片仲裁不变。
- Producer/consumer: WordMenu Listener 增两回调 → ChatScreen 唯一实现；`IconAssetsTest` 契约同步（图标化是拍板行为，改断言须写等价覆盖）。
- Platform branches: 无。
- Behavior-neutral non-applicability reason: N/A。

## Documentation impact record

- Changed runtime facts: 菜单 6 格、token 43→44/资源 15→16、新 drawable ×3。
- Documentation files updated: `design-system/README.md`（token 表/资源数/`:root` 行号/变更日志）、`android/README.md` 菜单描述、方案文档状态行。
- Superseded phrases reverse-searched: 「四格」「4 格」菜单描述反查更新。

## Acceptance criteria

- [x] 真机：真实 `ActionMode` 选中 prose → 6 格图标菜单（无「更多」常驻格）；设备截图 `artifacts/evidence/word-menu-popup.png`，运行证据 `artifacts/evidence/T-2026-09-28-word-menu-popup-green-device.txt`。
- [ ] 窄屏（分屏/极窄窗口）六格装不下时：「更多」格出现，主行保留前缀、尾部操作折进其纵向卡片，操作不减、只多一次点击（JVM 侧由 `WordMenuTest` 折叠四例守着，真机做一次形态确认）。
- [ ] 精确重合点高亮 → 选区恢复 + 首格带斜杠记号笔；部分重叠 → 首格仍记号笔，点击合并为并区间（V-3/V-11）。
- [ ] 与代码块灰底共存：高亮绘制在上（V-R2 专项断言 span 顺序）。
- [ ] 长按 ActionMode 与短按 ClickableSpan 不互相破坏（V-2）。
- [ ] 高亮重进页面仍在（读路径回归）。
- [ ] token 守门 0 failed（44/16）；JVM 全量单测绿；Java-only guard PASS；布局/图标测试同步（`IconAssetsTest` 含 3 新矢量与 6 格结构断言）。
- [x] 文本选中后的普通状态刷新不关闭 `ActionMode`、不清空选区且保留六格菜单；`WordMenuPopupInstrumentedTest` 3/3 通过。

## TDD execution record

### RED — failing test first
- Test/scenario: T-B 三件套失败测试先行：(1) `WordMenuTest` 6 项顺序 + `MARK` 组断言 + 流式置灰（画出但不 enabled，QUOTE 规则）+ 空选区规则；(2) `IconAssetsTest` 3 新矢量（`ic_action_highlight/note/highlight_off`）入 `CHAT_ICON_ASSETS`、`wordMenuIsSixIconCells...` 6 格结构断言、`theMarkCellsSitBetweenForkAndCopyInThePrototypeOrder` 顺序断言；(3) `HighlighterTest` 8 例决策层（精确重合 CANCEL / NOTE 同区间不取消 / 部分重叠并集合并 / 相触合并 / 桥接双吸收 / 纯新建 / 跨锚不取消不合并 / 空表新建）。V-R2 span 顺序属仪器域（app 模块无 Robolectric，`returnDefaultValues=true` 下 Spannable 无真实行为），随 GREEN 后设备轮取证，此处记录执行序决策。
- Command: `JAVA_HOME=/d/Environment/Jdk/jdk21 ./gradlew :app:testDebugUnitTest --no-build-cache`
- Exit code: 1
- Observed failure: app 模块测试编译失败，36 个错误全部为 T-B 新 API 不存在：`WordMenu.Item.HIGHLIGHT`/`NOTE` 与 `WordMenu.Group.MARK` 找不到符号（`WordMenuTest.java:36-77`），`Highlighter` 与 `Highlighter.Plan` 程序包不存在（`HighlighterTest.java:59-156`）。
- Failure reason validated: 失败性质 = 测试引用的 GREEN 产物尚未实现（TDD 预期失败），非环境/锁竞态——错误清单零本线外文件（math 线文件无报错），失败发生在 `compileDebugUnitTestJavaWithJavac` 而非 Gradle 通用失败形态。证据 `artifacts/evidence/T-2026-09-28-annot-tb-red-evidence.txt`（390 行，含命令行、运行时刻、模式标记、36 错误明细、`EXITCODE=1`）。

### GREEN — minimum implementation
- Implementation: T-B 全链路实现就位——菜单**终态 6 格**图标化（`WordMenu`/`WordMenuPopup`/`popup_word_action.xml` + `ic_action_highlight`/`note`/`highlight_off` 三矢量，`CHAT_ICON_ASSETS` 同步）；prose 高亮建/合并/取消/持久化（`Highlighter`/`SqliteChatStore.applyHighlight`/`ChatWiring.applyHighlight`/`ChatPresenter.onHighlightPressed`/`ChatScreen` 重绘 + 二次点击 `OnHighlightTapped`）；黄色 token 三方同步（43→44 / 15→16）；marks 独立通道（`ChatData.Snapshot.annotations` + `ChatSink.renderAnnotations`）。**环境阻断**：android 共享工作树当前被 math-102/103 线进行中代码 `MathSpan.java`（未实现 `LineHeightSpan.WithDensity.chooseHeight(...,TextPaint)`）阻断整体 main 编译，连带 APK 不可出。为验证 T-B 自身，临时将 math 包移出源码树（项目外 `/tmp`），确认 T-B 的 12+ 个 main 文件与测试文件**零编译错误**，随后原样恢复（git 内容无改动）。
- Cross-line conflict and the ruling that closed it: 19:11-19:33 别线把菜单改回「5 格主菜单 + 更多子菜单」并同步改了 `WordMenuTest`/`IconAssetsTest` 断言——我 19:25 那次 `BUILD SUCCESSFUL` 验证的其实是别线那版（改代码同时改测试会「自洽地绿」，故跑测试前必须先 grep 断言口径）。用户裁决 **选项 1：终态回到 6 格无子菜单，但手机上宽度不够时要有更多选项**。据此落地：`WordMenu.Item` 删 `MORE`（更多是容器不是操作，故不在枚举里），`entriesFor` 回 6 值（复制/全选各有独立格）；`popup_word_action.xml` 回 6 格 + 尾部「更多」格（默认 `gone`）；`popup_word_more.xml` 由「复制/全选两格」改为「六格纵向兜底卡」，Java 按折叠尾部显隐；新增纯函数 `WordMenu#fitFor` / `rowFor`——由窗口宽与格宽算出能画几格，装不下则保留**最长前缀**、尾部按原序折进更多（丢的是层级，不是操作），hairline 随所属格一起显隐；高亮斜杠态在两张卡上同步。
- Command: `JAVA_HOME=/d/Environment/Jdk/jdk21 ./gradlew :app:testDebugUnitTest --no-build-cache`
- Exit code: 0
- Passing evidence: 三套 **33 例全绿**（`WordMenuTest` 11 / `IconAssetsTest` 14 / `HighlighterTest` 8，failures=0 errors=0，证据 `app/build/test-results/testDebugUnitTest/*.xml`）：6 格顺序 + `Item.values().length == 6`（更多不是操作）+ MARK 组 + 流式置灰（QUOTE 同规则）+ 空选区；折叠四例（整行放得下就不折 / 差 1px 折尾部复制与全选 / 全宽度扫描不丢操作且顺序不变且首格恒为问 AI / 窄到装不下两格时仍保一格）；`IconAssetsTest` 6 格结构 + 「更多」兜底格恒 `gone` + 六格顺序 + 兜底卡六格图标；8 例高亮决策层（精确重合 CANCEL / NOTE 同区间不取消 / 部分重叠并集合并 / 相触合并 / 桥接双吸收 / 纯新建 / 跨锚不取消不合并 / 空表新建）。
- Regression evidence: math 线 19:27 已补 `MathSpan.chooseHeight`，**main 全量编译 `BUILD SUCCESSFUL`（50 tasks，10 executed），无需隔离**；全量 `:app:testDebugUnitTest` **86 类 / 775 例 / failures=0 / errors=0 / skipped=0**，无回归。

### Follow-up regression — selection menu stability
- Test/scenario: 真实长按 prose 后，手势返回触发一次普通 `ChatScreen.render()`；菜单、`ActionMode` 与选区必须继续存活。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.WordMenuPopupInstrumentedTest' --no-build-cache --no-daemon --console=plain`
- Exit code: 1
- Observed failure: RED 中 `unrelatedStateRenderKeepsSelectionMenuAndSelection` 失败，普通 `ChatScreen.render()` 无条件调用 `dismissWordMenu()`，导致菜单关闭并清空选区；原始日志 `artifacts/tmp/word-menu-state-red-2026-09-29.log`。
- Failure reason validated: 失败稳定复现于目标设备 `AWSS013807000356 / ALP-AN00 - 13`，不是构建或设备连接错误。
- Implementation: 删除 `ChatScreen.render()` 开始处的无条件关闭；新增 `wordMenuMessageId` 与 `dismissWordMenuIfUnavailable(state)`，只在消息不存在或 `WordMenuPopup.hasLiveSelection()` 为 false 时关闭；`showWordMenu()`/`dismissWordMenu()` 成对维护消息归属。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.WordMenuPopupInstrumentedTest' --no-build-cache --no-daemon --console=plain`
- Exit code: 0
- Passing evidence: `WordMenuPopupInstrumentedTest` 3/3 通过，包含手势返回保留选区与普通状态刷新保留菜单两条回归；日志 `artifacts/tmp/word-menu-state-green-2026-09-29.log`，汇总证据 `artifacts/evidence/T-2026-09-29-selection-menu-stability.txt`。
- Regression evidence: 定向真机 5 个测试类合计 19/19 通过；`assembleDebug`、`lintDebug`、Java-only guard、`verifyDependencyDirection` 均退出码 0；APK 安装成功且本地/设备 SHA-256 一致。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（估 380 >200 警告级，<500）。

## Implementation notes

- `renderSegments` 路径改；`ChatScreen.render()` 只允许执行菜单存活判定，不得因普通状态刷新无条件清除有效选区。
- 新增不透明 hex token ⇒ colors.xml 必 +1，README/tokens.json/`:root` 行号同步；改前先跑守门取实测基数。
- 六格宽度 = 2×4 + 6×44 + 5×1 = 277dp（cell=44 / hairline=1 / padding=4，`WordMenu#rowFor` 是这份算术的唯一出处）；`fitFor` 据此决定折不折：窗口（含左右各 8dp 边距）装不下才折，手机竖屏通常不折，分屏/极窄屏才折；`leftFor()` 仍负责夹取贴边（方案 §2.1）。

## Verification report

- Verdict: **PARTIAL——本次选区菜单稳定性修复与定向设备验证 PASS，但父任务仍未完成**：T-B 三套 33 例与 app 全量单测 86 类/775 例均 0 失败；`WordMenuPopupInstrumentedTest` 3/3、定向真机合计 19/19 通过；APK 已安装并完成哈希一致性校验。高亮点击后的黄色取色、精确取消态、窄屏折叠、整包 instrumentation 与独立 verifier 仍未完成。
- Evidence: 选区稳定性 RED/GREEN 证据为 `artifacts/tmp/word-menu-state-red-2026-09-29.log`、`artifacts/tmp/word-menu-state-green-2026-09-29.log`、`artifacts/evidence/T-2026-09-29-selection-menu-stability.txt`；安装构件为 `android/app/build/outputs/apk/debug/app-debug.apk`。
- 阻断：环境阻断已解除；当前剩余是验收证据缺口，不把 JVM 绿或单项菜单绿扩写为全任务完成。

## Handoff notes

- Completed: 菜单终态 6 格 + 窄屏更多兜底（`WordMenu` 删 `MORE` 回 6 值 / `fitFor`+`rowFor` 折叠规则、`WordMenuPopup` 双卡同构绘制、`popup_word_action.xml` 6 格 + 兜底格、`popup_word_more.xml` 六格纵向兜底卡、`WordMenuTest` 折叠四例、`IconAssetsTest` 6 格 + 兜底格 + 兜底卡）；prose 高亮建/合并/取消/持久化全链路；黄色 token 三方同步；文档同步；普通 `ChatScreen.render()` 刷新不再误关选区菜单；定向真机 19/19 通过；debug APK 已安装并完成哈希核对。
- In progress: 设备侧高亮动作与窄屏折叠验收、整包 instrumentation、独立 verifier 复核。
- Blocker: 无环境阻断；任务仍处于 `verifying`，不是 `verified`。
- Unblock condition: N/A。
- Next steps: 补齐真机高亮可见/取消态/窄屏折叠与 V-2/V-R2 instrumentation，再由独立 verifier 复核；不要把当前 19/19 定向回归单独当成 T-B 全部验收通过。
- Watch-outs: 置灰态不吞点击（可见拒绝原则）；批注回调接口定稿后 T-D 不得改签名；「更多」只允许作为宽度兜底出现，不得再退回常驻子菜单——`IconAssetsTest.moreIsAFallbackCellThatStaysHiddenUntilTheRowHasNoRoom` 与 `WordMenuTest` 的 `Item.values().length == 6` 两处守着；跑测试前先 grep 断言口径，别线改代码同时改测试会「自洽地绿」。
