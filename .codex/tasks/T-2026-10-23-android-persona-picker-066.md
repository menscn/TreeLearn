# Filename: T-2026-10-23-android-persona-picker-066.md
# Codex Harness Task: Android 人设界面：聊天页人设 chip、七行弹窗与主题窄写

id: T-2026-10-23-066
title: Android 人设选择器与主题窄写
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-ai-persona-and-forest-requirement-038.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 470
shared_code_touched: true
depends_on: [T-2026-10-23-android-persona-core-064.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary：父卡 038 已登记用户 2026-10-23「按 harness 开发，web 和 android」的实现授权；人设规格 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 与原型 `Docs/04-ux-design/知芽-TreeLearn-人设与森林原型-v1.html` 已定：人设跟着主题走，入口是聊天页输入框那一行、模型 chip 右侧的同款 chip，弹窗内七行单选，切换只写主题的 `persona` 一列，回答上不标注人设。
- Problem/background: 064 已让人设进入生成身份与三张表的列，但聊天页还看不到、也改不了主题档位；没有选择入口，V6 列只能靠外部写入。
- Users/scenarios: 学习者在主题内点人设 chip，看见当前档并换成七档之一；下一次发送/重试/重置按新档生成，已有回答不变，首页主题排序不动。
- Expected outcome: 聊天页 chip 显示主题当前档；弹窗七行、当前行有勾选与浅绿底、每行 44dp；切换走只写 `persona` 的窄写并即时反馈；同一档不重复写；写失败回退 chip 并显示存储错误；读到未知档显示默认并只提示一次。
- Core flow and reversibility: 读取 `ChatStore.Conversation` 的人设 → `ChatData.Snapshot` → `ChatViewState.persona` → `PersonaPicker`；选择 → `ChatCommands.setPersona` → `ChatStore.updatePersona` → `TopicDao.updatePersona`。每一步都可单独回退；回退界面不删已写入的列。
- Inputs/outputs: 输入是主题行的人设、chip 的选择；输出是更新后的主题 `persona`、聊天页 chip 与一次切换 toast。请求链路沿用 064 的「主题行是唯一权威」，本片不把 chip 状态送进请求。
- State and exception handling: 未知值在读取边界回落默认并由 presenter 每次进入只提示一次；写入只接受枚举；窄写影响行数不是 1 时拒绝并显示 `STORAGE`；同一档点击不产生写。
- Constraints and trade-offs: Java-only；不新增依赖；不改基线提示词；不记录提示词正文；窄写不刷新 `updated_at`；不把回落塞进 `Topic`；不自动提交。
- AI recommendation and rationale: 选择器沿用 `ModelThinkingPicker` 已验证的「视觉胶囊 + 44dp 容器 + 贴锚点弹窗」模式；弹窗根节点与七行在代码里按 `Persona.values()` 生成，避免七份重复 XML 与顺序漂移。Scheme A 的单行 dock 在加入人设 chip 后实测会把草稿压到约 17dp（360dp 屏），因此 dock 改为同一张玻璃卡内的两行：第一行附件/输入/发送，第二行模型 chip 与人设 chip；仍是当前 Scheme A 的玻璃 dock 与间距体系，只是把工具从输入行里拿出来。
- User choice and custom additions: 未知档回落默认并提示；切换人设不刷新活动时间；回答上不标注人设；入口在模型 chip 右侧。
- AI-autonomous decisions: ① 066 只做选择器与窄写，介绍页、设置入口与弹窗底部「了解每种风格」拆到 067，保证每片可独立验收。② dock 两行化（见上）是本片唯一的视觉结构改动，原因与测量写进本卡。③ 回落提示按「每次进入聊天页一次」实现，和 T-103 的 chat-role 回落同构。
- User-confirmation decisions: 本片不新增产品决策；视觉结构的 dock 两行化属于执行层适配，若真机截图不可接受，067 之前可退回单行并改为图标 chip。
- Acceptance evidence: `ChatPresenterPersonaTest`、`ChatPersonaWiringTest`、`ChatViewStateTest`、`PersonaPickerLayoutTest`、`SchemeAChatLayoutTest` 的 RED/GREEN；全量 `:app:testDebugUnitTest`；`:app:assembleDebug`。
- Facts: `ChatStore.Conversation` 已带 `persona`/`personaFellBack`；`ChatData.Snapshot` 还没有；`ChatPresenter.apply` 是读取变状态的唯一入口；`ChatWiring.pin` 在重试/重置时重读主题行人设；`ModelThinkingPicker` 是本项目已验证的 chip+弹窗模式。
- Assumptions: 360dp 宽是本次布局的最低目标；`picker_min_width` 240dp 足够放下七行。
- Unknowns: 已在本片真机确认——空间不足时弹窗翻到 dock 上方而不是压住输入行；两行 dock 在 1200x2652 / 520dpi 上输入行仍占满宽度。截图见 `artifacts/evidence/persona-066-*.png`。
- Risks: 两行 dock 抬高输入区，可能压缩阅读区；窄写路径若误复用整行写回会改变首页排序。
- Deferred: 介绍页、设置入口、弹窗底部入口（片 067）；森林（片 068）；Web（片 069/070）；文档与设备证据（片 071）。
- Non-goals: 用户自建人设；提示词正文编辑；全局人设；回答上的人设标签；把 chip 状态写入请求。
- Skipped non-critical questions and reason: 没有追问弹窗顶部标签是否必须与原型逐字相同；规格已写「回答风格」，按规格实现。

## Goal

让聊天页能看见并切换当前主题的回答风格：七行弹窗单选、当前档可见、选择走只写 `persona` 的窄写并即时反馈，未知值回落默认且只提示一次，同时不改变首页排序与既有回答。

## Scope

### In scope
- `ChatData.Snapshot` 携带 `persona` 与 `personaFellBack`；旧工厂保持默认档。
- `ChatViewState` 增加 `persona` 与 `withPersona`；`ChatPresenter.apply` 采纳读取值，并在回落时每次进入只提示一次。
- `ChatActions.onPersonaSelected`；`ChatSink.showPersonaSwitched`/`showPersonaFellBack`。
- `ChatCommands.setPersona`；`ChatStore.updatePersona`；`SqliteChatStore` 经 `BranchDao` 找到主题后调用 `TopicDao.updatePersona`；`ChatWiring` 把写失败映射为 `STORAGE`。
- 新 `PersonaUi`（档名/标签资源映射）、`PersonaPicker`（chip + 七行弹窗）、`view_persona_chip.xml`、`view_persona_option.xml`。
- `screen_chat_body.xml` 的 dock 两行化与 `ChatScreen` 接线；`MainActivity` 的切换/回落 toast。
- JVM 测试覆盖选择、同档不写、写失败回退、回落只提示一次、快照默认档、布局契约。

### Out of scope
- 介绍页、设置页入口、弹窗底部「了解每种风格」（片 067）。
- 森林（片 068）、Web 人设与森林（片 069/070）、文档与设备证据（片 071）。
- 人设提示词效果调优与任何请求链路改动。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增聊天页人设入口与主题写路径；默认档行为与「没有人设」完全一致；旧回答不改写；首页排序仍只看 `updated_at`；发送/重试/重置仍从主题行读取人设。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `SqliteChatStore.read` → `ChatWiring.read` → `ChatPresenter.apply`；写者 `ChatPresenter.onPersonaSelected` → `ChatWiring.setPersona` → `SqliteChatStore.updatePersona` → `TopicDao.updatePersona`；消费者 `PersonaPicker`；请求路径 `ChatWiring.pin` 不经过 chip。
- Native bridge or independent-client compatibility/update requirements: 不适用——不穿越原生桥；Web 不消费本片状态。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 聊天页新增人设 chip 与七行弹窗；dock 由单行改为两行玻璃卡；新增只写 `persona` 的界面写路径。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java`、`android/app/src/main/java/com/treelearn/app/screen/PersonaPicker.java`、`android/app/src/main/java/com/treelearn/app/wiring/SqliteChatStore.java`、`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java`。
- Documentation surfaces checked: 人设规格、人设与森林原型、`android/README.md`、`.codex/rules/android.md`、`.codex/rules/ux-design.md`、`.codex/rules/regression.md`。
- Documentation files updated: 本卡；`android/README.md` 与规格行号随片 071 回写；034/039/040/064 的片号顺延为本卡的 066–071。
- Producer/consumer/sibling/compatibility paths checked: 读取、展示、选择、写回、失败回退、重试/重置请求身份。
- Superseded phrases/examples reverse-searched: 「聊天页 chip 还不存在」「单行 dock 是唯一形态」「人设界面片 066 包含介绍页」。
- `documentation impact: none` reason and evidence, 若适用: 不适用。
- Historical/PRD handling: 原型与规格保留为设计事实源；实现口径写进代码注释与本卡。

## Acceptance criteria

- [x] 聊天页 chip 显示主题当前档；弹窗七行、当前行有勾选与浅绿底、每行至少 44dp。
- [x] 选择一档后 chip 即时更新、写入只发生在 `persona` 一列、成功时提示「已切换到 X」。
- [x] 重复选择当前档不产生写；写失败时 chip 回退到原档并显示存储错误。
- [x] 读取到未知档时显示默认档，并且每次进入聊天页只提示一次。
- [x] 旧 `ChatData.Snapshot` 工厂仍得到默认档；既有发送/重试/重置与首页排序测试全部通过。
- [x] `:app:testDebugUnitTest` 全绿；`:app:assembleDebug` 通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `ChatPresenterPersonaTest`（读取带档、无档默认、未知档回落一次、选择写主题并提示、同档不写、写失败回退）、`ChatPersonaWiringTest`（快照带档与回落标记、窄写落库、写失败映射 STORAGE）、`ChatViewStateTest`（默认档与构建器保留）、`PersonaPickerLayoutTest`（chip 胶囊、44dp 行、七行枚举、两行 dock、资源齐全）。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatPresenterPersonaTest" --tests "com.treelearn.app.wiring.ChatPersonaWiringTest" --tests "com.treelearn.app.ui.ChatViewStateTest" --tests "com.treelearn.app.screen.PersonaPickerLayoutTest"`
- Exit code: 1
- Observed failure: 编译期 21 个错误——`ChatViewState` 无 `persona`/`withPersona`，`ChatData.Snapshot` 无 `persona`/`personaFellBack` 与九参 `of`，`ChatPresenter` 无 `onPersonaSelected`，`ChatCommands` 无 `setPersona`，`ChatSink` 无 `showPersonaSwitched`/`showPersonaFellBack`，`ChatStore` 无 `updatePersona`，`ChatWiring` 无 `setPersona`；`PersonaPickerLayoutTest` 因编译先于运行未执行，资源与布局同样缺失。
- Failure reason validated: 是。新增断言全部指向本片要新增的读取字段、写命令与界面入口；失败不是环境或既有行为回归。

### GREEN — minimum implementation
- Implementation: 新增 `PersonaUi`、`PersonaPicker`、`view_persona_chip.xml`、`view_persona_option.xml`、`bg_persona_option.xml`；`ChatData.Snapshot` 携带 `persona`/`personaFellBack`（旧工厂转发默认档）；`ChatViewState` 增 `persona`/`withPersona`；`ChatPresenter` 采纳读取值、每次进入只提示一次回落、选择时乐观更新并在窄写被拒时回退；`ChatActions`/`ChatSink`/`ChatCommands`/`ChatStore`/`SqliteChatStore`/`ChatWiring` 接通只写 `persona` 的窄写；`screen_chat_body.xml` dock 两行化；`ChatScreen`/`MainActivity` 接线与切换/回落 toast。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatPresenterPersonaTest" --tests "com.treelearn.app.wiring.ChatPersonaWiringTest" --tests "com.treelearn.app.ui.ChatViewStateTest" --tests "com.treelearn.app.screen.PersonaPickerLayoutTest"`
- Exit code: 0
- Passing evidence: 命令 `BUILD SUCCESSFUL`；四个 suite 共 29 个测试（`ChatPresenterPersonaTest` 6、`ChatPersonaWiringTest` 3、`ChatViewStateTest` 15、`PersonaPickerLayoutTest` 5）0 失败 0 错误；`SchemeAChatLayoutTest` 7 个测试另行通过。
- Regression evidence: 全量 `:app:testDebugUnitTest` 104 个 suite、917 个测试、0 失败、0 错误、0 跳过；`:app:assembleDebug` `BUILD SUCCESSFUL`。真机 API 36 上 `adb install -r` 后确认：chip 显示当前档、弹窗七行且当前行有勾选与浅绿底、选「学霸搭子」出现切换 toast、chip 更新、强制停止重启后档位保留；截图 `artifacts/evidence/persona-066-{chat,popover,switched,selected,persisted,home}.png`。

### REFACTOR — optional
- Cleanup: 无独立重构。弹窗行按 `Persona.values()` 生成，避免七份 XML；`ChatWiring.pin` 改为实例方法仅为读取主题行，请求契约未动；两行 dock 沿用原有玻璃卡、圆角与 14/11/12/11 内边距 token。
- Regression evidence: 同上全量测试、`assembleDebug` 与真机重启持久化截图。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 按仓库自有口径（`count_production_lines.py`：非空、非注释、只计生产文件）实测：本片新增 5 个生产文件共 267 行；既有文件中人设相关 hunk 的新增最多 177 行（该上界含 064 留在同一工作树的读取/`pin` 行与少量无关字符串，本片实际新增低于此数），上界合计 444 行。低于 500 行失败阈值，高于 200 行软阈值，按规则记 verifier warning，无需超限批准。
- User approval: 用户 2026-10-23 授权按 harness 开发；未触发 500 行例外。
- Alternative verification: 不适用。

## Implementation notes

- 弹窗七行按 `Persona.values()` 生成，顺序即规格顺序，默认档在首。
- `PersonaUi` 只做资源映射，不参与决策；选择与写回由 presenter 决定。
- dock 两行化是本片唯一的结构改动：第一行附件/输入/发送，第二行模型 chip + 人设 chip；玻璃卡、圆角与间距沿用 Scheme A token。
- 回落提示与 T-103 的 chat-role 回落同构，一次进入一次提示，不进入持久状态。
- 写失败先回退 chip 再渲染 `STORAGE`，不让界面显示未落库的档位。

## Verification report

- Verdict: PASS
- Evidence: 定向 4 个 suite 29 个测试 exit 0；全量 `:app:testDebugUnitTest` 917 个测试 0 失败；`:app:assembleDebug` 成功。真机 API 36 以 `adb install -r` 安装后：初始 chip「默认」；弹窗七行、当前行勾选 + `tl_green_faint`、触发区 44dp；选「学霸搭子」后 toast「已切换到「学霸搭子」」、chip 同步；强制停止再启动仍为「学霸搭子」。截图 `artifacts/evidence/persona-066-chat.png`、`persona-066-popover.png`、`persona-066-switched.png`、`persona-066-selected.png`、`persona-066-persisted.png`。
- Changed/deleted assertion rationale and equivalent behavior coverage: `SchemeAChatLayoutTest` 由「单一水平行」改为「垂直玻璃卡 + `chat_composer_row` + `chat_composer_tools` 两条水平行」。原因：加人设 chip 后单行 dock 在 360dp 上把草稿压到约 17dp（真机实测）。原有 dock 内外边距、圆角、玻璃卡断言保留；既有控件 id 一个未删，只是分到两条行里。行为覆盖从「全部控件同排」等价改为「输入控件同排、工具控件同排」，并新增「人设 chip 在工具行、模型 chip 右侧」的断言。
- Gaps: 未跑 instrumentation——按项目规则它会卸载应用并清掉本地数据与 API Key，只做 `adb install -r` 与截图；Web 端属于 069/070。
- Risks: 两行 dock 抬高输入区，矮屏阅读区更紧；弹窗翻转依赖 `chat_composer_card` id（找不到时回退贴 chip）；本轮设备截图证明当前形态可用，未覆盖横屏与小窗。

## Handoff notes

- Completed: 聊天页人设 chip、七行单选弹窗（勾选/浅绿底/44dp）、只写 `persona` 的窄写与切换 toast、同档不写、写失败回退 + `STORAGE`、未知档回落默认且每次进入只提示一次、旧快照工厂默认档、两行 dock 与全部 JVM/真机验证。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 片 067 介绍页 + 设置入口 + 弹窗底部「了解每种风格」；片 068 Android 森林；片 069/070 Web 人设与森林；片 071 文档与证据回写。
- Watch-outs: `ChatStore.updatePersona` 故意保持抽象，假实现不能静默“成功”；`PersonaPicker` 的七行顺序就是 `Persona.values()` 顺序，改枚举顺序会改规格顺序；`ChatWiring.pin` 仍以主题行为唯一权威，不要把 chip 状态塞进请求。
