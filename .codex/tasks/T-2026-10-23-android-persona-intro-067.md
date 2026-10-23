# Filename: T-2026-10-23-android-persona-intro-067.md
# Codex Harness Task: Android 回答风格介绍页与两处入口

id: T-2026-10-23-067
title: Android 回答风格介绍页与设置/弹窗入口
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-ai-persona-and-forest-requirement-038.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 330
shared_code_touched: true
depends_on: [T-2026-10-23-android-persona-picker-066.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary：父卡 038 已登记用户 2026-10-23「按 harness 开发，web 和 android」的实现授权；人设规格 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 的界面方案与原型 `Docs/04-ux-design/知芽-TreeLearn-人设与森林原型-v1.html`（03 介绍页）已定：介绍页只读、静态文案、七档各一张卡，入口两处——聊天页人设弹窗底部「了解每种风格」、设置页「回答风格介绍」一行。
- Problem/background: 066 让聊天页能选档，但七档分别适合什么处境、示例差异是什么，界面里看不到；设置页也没有一条通往说明的路。
- Users/scenarios: 学习者在弹窗里拿不准选哪档，点底部「了解每种风格」看七张卡；或从设置页进入同一页；读完按返回回到进入前的页面。
- Expected outcome: 介绍页展示标题「回答风格介绍」、副标题、导语、七个 set-card（档名 + 定位标签 + 一句话定位 + 同一问题的静态示例）、页脚「自建人设将来在这里管理」；两处入口都进入同一页且返回回到来源；介绍页不选择、不写库、不发请求、不需要 API Key。
- Core flow and reversibility: 弹窗底部行 → `ChatActions.onPersonaIntroPressed` → `ChatPresenter` → `ChatSink.openPersonaIntro` → 宿主换屏；设置行 → `SettingsScreen.setOnPersonaIntroPressed` → 宿主换屏；返回按来源回到聊天或设置。纯导航，回退不改任何数据。
- Inputs/outputs: 输入是人设枚举与静态字符串资源；输出是一屏只读卡片与一次返回导航。没有存储、网络或请求链路。
- State and exception handling: 介绍页没有可变状态；卡片按 `Persona.values()` 生成，未知档不会出现；返回目标是打开时记录的来源，来源页已不可用（分支为空）时回首页。
- Constraints and trade-offs: Java-only；不新增依赖；不改 066 的选择/窄写；文案与示例取原型原文；不记录任何学习者内容；不自动提交。
- AI recommendation and rationale: 复用设置页现有「44dp 返回 + 标题」二级页形态与 `bg_card` 卡片；介绍卡用一份 `item_persona_intro_card.xml` 循环生成，避免七份重复 XML；文案走 `PersonaUi` 的 `subRes`/`demoRes` 映射，保持「枚举是唯一顺序」。
- User choice and custom additions: 未知档回落默认并提示；切换不刷新首页排序；回答不标注人设；森林含归档子树——均已在 064/066 落地，本片不新增产品拍板。
- AI-autonomous decisions: ① 介绍页只读，不提供选择按钮；选择仍只发生在主题内弹窗（规格明确）。 ② 返回来源用布尔记录「来自设置」，不引入导航栈。 ③ 弹窗底部行沿用原型「星形图标 + 了解每种风格 + 右箭头」。
- User-confirmation decisions: 本片不新增需要用户拍板的产品决策。
- Acceptance evidence: `PersonaIntroLayoutTest`（静态页、七卡、示例区、两处入口、资源齐全）、`ChatPresenterPersonaTest` 新增「介绍入口只导航不写库」；全量 `:app:testDebugUnitTest`；`:app:assembleDebug`；真机截图。
- Facts: 066 的 `PersonaPicker` 已有七行弹窗与 listener；`ChatActions`/`ChatSink` 已有导航端口先例（设置、概览、导航页）；`SettingsScreen` 的每个房间都有一个 `setOn...Pressed` 接线；`screen_settings_body.xml` 被手机与 Pad 共用。
- Assumptions: 介绍页内容量可以放在一个 ScrollView 内；七张卡在 API 36 设备上无需折叠。
- Unknowns: 七张卡在 360dp 矮屏上的滚动观感；实际以真机截图为准。
- Risks: 从聊天页返回会重建聊天屏（既有二级页返回口径），未发送草稿依赖草稿存储；介绍页若被误接成可选择会破坏「选择只在弹窗」的规格。
- Deferred: 森林（片 068）；Web 人设与森林（片 069/070）；文档与证据（片 071）。
- Non-goals: 用户自建人设的增删改查；介绍页内直接切换档位；现场生成示例；全局人设；修改 066 的写路径。
- Skipped non-critical questions and reason: 未追问介绍页是否要显示当前档——规格要求它只介绍，不承载选择，也不标注当前档。

## Goal

给回答风格一个只读介绍页，并让聊天页弹窗底部与设置页都能进入同一页、按来源返回，同时不引入任何选择或写入路径。

## Scope

### In scope
- 新 `PersonaIntroScreen`；`screen_persona_intro.xml`、`item_persona_intro_card.xml`、`view_persona_more.xml`、`bg_persona_demo.xml`、`bg_popover.xml`。
- `AndroidManifest.xml` 显式 `enableOnBackInvokedCallback="true"`：API 36 真机上手势返回原先绕过 `OnBackInvokedDispatcher` 直接结束 Activity，介绍页的来源返回因此不可达。
- `PersonaUi` 增加 `subRes`/`demoRes`；七档定位与示例文案、介绍页标题/副标题/导语/问题/页脚字符串。
- `PersonaPicker` 弹窗底部「了解每种风格」行与 `onIntroRequested`。
- `ChatActions.onPersonaIntroPressed`、`ChatSink.openPersonaIntro`、`ChatPresenter` 转发、`ChatScreen` 接线。
- 设置页新增「回答风格介绍」分组与入口行；`SettingsScreen.setOnPersonaIntroPressed`。
- `MainActivity` 宿主：介绍页字段、打开方法、按来源返回、各处清理。
- JVM 测试覆盖静态页契约、七卡、两处入口、资源齐全与「只导航不写库」。

### Out of scope
- 在介绍页里选择或写入档位。
- 森林（片 068）与 Web（片 069/070）。
- 修改 066 的 chip/弹窗选择行为与窄写。
- 文档回写与设备证据归档（片 071）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增一屏只读介绍与两处导航入口；066 的 chip、七行选择、窄写与回落提示不变；聊天页/设置页的既有入口与返回不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 弹窗 `PersonaPicker` → `ChatScreen` → `ChatPresenter.onPersonaIntroPressed` → `MainActivity.openPersonaIntro`；设置 `screen_settings_body.xml` → `SettingsScreen.setOnPersonaIntroPressed` → `MainActivity.showPersonaIntro`；返回统一走 `MainActivity.onBackRequested`。
- Native bridge or independent-client compatibility/update requirements: 不适用——纯本机界面导航，不穿越原生桥，Web 不消费。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 新增回答风格介绍页；设置页新增「回答风格介绍」入口；聊天页人设弹窗底部新增入口。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/screen/PersonaIntroScreen.java`、`PersonaPicker.java`、`SettingsScreen.java`、`MainActivity.java`、`screen_settings_body.xml`、`AndroidManifest.xml`。
- Documentation surfaces checked: 人设规格界面方案、人设与森林原型（03 介绍页）、`android/README.md`、`.codex/rules/ux-design.md`、`.codex/rules/android.md`、`.codex/rules/regression.md`。
- Documentation files updated: 本卡；README 与规格的现状行随片 071 回写。
- Producer/consumer/sibling/compatibility paths checked: 弹窗入口、设置入口、返回来源、聊天页重建、Pad 共用布局。
- Superseded phrases/examples reverse-searched: 「介绍页尚未实现」「设置页没有人设入口」「弹窗只有七行」。
- `documentation impact: none` reason and evidence, 若适用: 不适用。
- Historical/PRD handling: 介绍页示例文案取原型原文，作为设计资产，不承诺模型逐字输出。

## Acceptance criteria

- [x] 聊天页人设弹窗底部出现「了解每种风格」，点按进入介绍页；七行选择行为不变。
- [x] 设置页出现「回答风格介绍」入口，点按进入同一介绍页。
- [x] 介绍页展示七张卡（档名、定位标签、一句话定位、同一问题的静态示例）与页脚说明，内容不随主题档位变化。
- [x] 介绍页只有阅读与返回：不写库、不发请求、不需要 API Key；返回回到进入前的页面。
- [x] 066 的人设选择、窄写、回落提示与全量既有测试全部通过。
- [x] `:app:testDebugUnitTest` 全绿；`:app:assembleDebug` 通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `PersonaIntroLayoutTest`（静态介绍页、`Persona.values()` 七卡顺序、无 ChatStore/ChatCommands/setPersona、弹窗底行与设置入口、七组文案与示例区资源）+ `ChatPresenterPersonaTest.theIntroRoadIsNavigationAndNeverAWrite`；`ChatTestDoubles.RecordingChatSink.openPersonaIntro` 记录导航次数。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.PersonaIntroLayoutTest" --tests "com.treelearn.app.ui.ChatPresenterPersonaTest"`
- Exit code: 1
- Observed failure: `ChatTestDoubles.java:388` 报「方法不会覆盖或实现超类型的方法」（`ChatSink` 尚无 `openPersonaIntro`）；`ChatPresenterPersonaTest.java:107` 报「找不到符号」（`ChatPresenter` 尚无 `onPersonaIntroPressed`）。主源码编译失败，`PersonaIntroLayoutTest` 未执行。
- Failure reason validated: 两处错误都指向本片新增的导航端口与被测方法缺失，不是测试自身写错；先补 `ChatSink`/`ChatPresenter` 的端口后测试才能进入断言。

### GREEN — minimum implementation
- Implementation: 新增静态 `PersonaIntroScreen`（无 presenter/store/网络，按 `Persona.values()` 循环七卡）；`screen_persona_intro.xml` 用整页 `ScrollView` 承载标题区与卡片，`item_persona_intro_card.xml`、`view_persona_more.xml`、`bg_persona_demo.xml`、`bg_popover.xml` 各一份；`PersonaUi.subRes/demoRes` 映射七档定位与示例；`PersonaPicker` 弹窗底行「了解每种风格」+ `onIntroRequested`；`ChatActions/ChatSink/ChatPresenter/ChatScreen` 只导航不写库；设置页入口行 + `SettingsScreen.setOnPersonaIntroPressed`；`MainActivity` 记录来源并按来源返回。另在 `AndroidManifest.xml` 显式 `enableOnBackInvokedCallback="true"`，修复 API 36 真机手势返回绕过宿主 `OnBackInvokedDispatcher` 直接结束 Activity 的既有缺口。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.PersonaIntroLayoutTest" --tests "com.treelearn.app.ui.ChatPresenterPersonaTest"`
- Exit code: 0
- Passing evidence: `PersonaIntroLayoutTest` 5 例全过；`ChatPresenterPersonaTest` 7 例全过（含新增「介绍入口只导航不写库」）。
- Regression evidence: `cd android && ./gradlew :app:testDebugUnitTest` exit 0，105 个 suite / 923 个测试 / 0 failure / 0 error / 0 skipped；`./gradlew :app:assembleDebug` exit 0。

### REFACTOR — optional
- Cleanup: ① 弹窗背景由玻璃层 `bg_card` 换成实心 `bg_popover`，避免聊天正文透出影响七行与底行的可读性；② 介绍页标题区移入整页 `ScrollView`，滚动后卡片不再贴在半透明固定标题下；③ `ChatScreen` 的匿名 listener 补 `Persona` 导入。
- Regression evidence: 重构后定向 12 例与全量 923 例均重新通过；真机重装了 manifest 修复后的 APK 并重截全部 067 证据。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 新增生产文件 6 个共 261 严格行（`.codex/scripts/dev/count_production_lines.py`）；067 归属的改动 hunk 保守上限约 178 行（含 manifest 1 行）；合计 ≤439 行，超过 200 行软阈值但低于 500 行失败阈值，按一个可独立验收的只读页评估。
- User approval: 用户 2026-10-23 授权按 harness 开发；未触发 500 行例外。
- Alternative verification: 不适用。

## Implementation notes

- 介绍页不持有当前档位，也不提供选择按钮：七卡由枚举顺序生成，示例文案来自字符串资源，页脚固定「自建人设将来在这里管理。本期只有内置七档。」
- 返回来源只用一个布尔记「来自设置」：来自设置回设置，否则回打开它的分支；分支已不可用时回首页。没有引入导航栈。
- API 36 真机验证时发现 `adb shell input keyevent 4` 会直接结束 Activity，不能代表返回手势；改用左右边缘向内滑动（`input swipe`）验证真实 `OnBackInvokedDispatcher` 路径。显式 manifest 开关后，手势返回在「聊天→介绍」与「设置→介绍」两条路径都按来源返回。
- 设备证据：`persona-067-popover.png`（实心弹窗七行+底行）、`persona-067-intro.png` / `persona-067-intro-end.png`（七卡与页脚）、`persona-067-back.png`（聊天→介绍→手势返回同一聊天）、`persona-067-settings-entry.png`、`persona-067-settings.png`、`persona-067-back-settings.png`、`persona-067-back-settings-gesture.png`。

## Verification report

- Verdict: PASS
- Evidence: 定向 `PersonaIntroLayoutTest` 5 例 + `ChatPresenterPersonaTest` 7 例，exit 0；全量 `:app:testDebugUnitTest` exit 0（105 suite / 923 test / 0 failure / 0 error / 0 skipped）；`:app:assembleDebug` exit 0；`adb install -r` 后真机走通「聊天弹窗→介绍→手势返回同一聊天」「设置→介绍→页内返回设置」「设置→介绍→手势返回设置」，截图见 Implementation notes。介绍页本身没有存储、网络或请求链路，`ChatPresenterPersonaTest` 断言打开介绍不调用 `setPersona`、不触发切换。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除既有断言。新增断言覆盖静态页不持有 ChatStore/ChatCommands/setPersona、七卡顺序等于 `Persona.values()`、示例区使用 `bg_persona_demo`、弹窗底行存在且带分隔线、设置入口存在、七组 `_sub`/`_demo` 文案与标题/导语/页脚资源齐全；等价行为「选择仍只在弹窗」由 066 的 7 例选择测试与全量回归兜底。
- Gaps: Pad（1024/1366）未单独截图，设置页与介绍页共用手机布局，按共享布局与约束验证；设备证据来自一台 API 36 手机；介绍页为纯静态视图，未加仪器测试，按项目惯例用 JVM 契约 + 真机截图。页脚「自建人设将来在这里管理」的文案随片 071 回写文档。
- Risks: API 36 上系统手势返回依赖 `android:enableOnBackInvokedCallback="true"`，若后续移除此开关会退回「返回直接退出应用」；真机截图在转场后可能滞后一拍，验证返回时以稳定帧与手势滑动为准，`input keyevent 4` 不能替代手势返回。

## Handoff notes

- Completed: 回答风格介绍页与两处入口；弹窗底行、设置分组、来源返回、manifest 返回开关与全部证据。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 片 068 Android 我的森林；片 069/070 Web 人设与森林；片 071 文档与证据回写。
- Watch-outs: 真机返回务必用边缘手势验证（keyevent 4 在 API 36 会直接结束 Activity）；重装用 `adb install -r`，不要跑会清数据的全量仪器测试；manifest 的 `enableOnBackInvokedCallback` 不得删；转场后先等稳定帧再截图，避免把旧帧当新结果。
