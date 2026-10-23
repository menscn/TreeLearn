# Filename: T-2026-10-21-android-glass-surfaces-049.md
# Codex Harness Task: Android 晨雾玻璃场景与共享玻璃表面

id: T-2026-10-21-049
title: Android 晨雾玻璃场景与共享玻璃表面
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-glass-material-requirement-047.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 420
shared_code_touched: true
depends_on: [T-2026-10-21-glass-token-chain-048.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 047 已确认 A 晨雾玻璃 + C 1px 高光边 + 更强通透；本片在 Android 生产界面消费 048 的 token。
- Problem/background: 当前 Android 所有页面都落在不透明 `tl_bg`／`tl_surface` 上，`PaperRuleBackdrop` 只画横格，没有雾面场景；共享 drawable 是不透明实色，没有高光边。
- Users/scenarios: 手机为主、平板横屏保留；首页输入、对话长读、分支导航、学习库、设置、抽屉、底部 tab。
- Expected outcome: 全屏 `PaperRuleBackdrop` 画雾面渐变＋三处柔光＋纸感横格；共享表面改为半透明玻璃＋1px 高光边＋22/28 圆角；tab bar 与抽屉变为玻璃；聊天输入区、卡片、搜索、字段跟随；不引入实时模糊。
- Core flow and reversibility: `activity_main` 的背景 view 画场景 → 共享 drawable 以 `glass.xml` 的 ARGB 副本填充 → 各 layout 复用同一批 drawable。回退为恢复原 drawable 内容、移除 `glass.xml` 与场景绘制，布局 id 与业务不动。
- Inputs/outputs: 输入是 048 的 `glass-*`/`glow-*`/`mist-*` token 与 `r-xl`/`r-2xl`；输出是 `values/glass.xml`、`values/dimens.xml` 两个圆角、`PaperRuleBackdrop` 的场景绘制、共享 drawable 与少量 layout 背景替换。
- State and exception handling: 无运行时状态；API 28+ 一律使用静态柔焦场景，不伪造实时模糊；带语义色的 tint 卡片保留语义色（失败条与译文卡各自使用独立的 `bg_semantic_alert`／`bg_semantic_note`）。
- Constraints and trade-offs: Java-only、零 AndroidX、零 Kotlin、无新依赖；不改 SQLite/接口/业务；不跑会卸载应用的全量仪器测试；真机只用 `adb install -r`。
- AI recommendation and rationale: 用「一面全屏场景 + 共享 drawable」取得最大覆盖面，避免逐屏复制；rgba 玻璃色集中到 `values/glass.xml`，由 JUnit 自动比对 `tokens.json`，不手抄第五份清单。
- AI-autonomous decisions: ① 不实现 `RenderEffect` 实时模糊，场景本身柔焦；② 玻璃 drawable 用 layer-list（顶部 sheen + 半透明填充 + 1px edge）；③ 语义 tint 的两张聊天卡片迁移到不透明的 `bg_semantic_alert`（软红）与 `bg_semantic_note`（浅绿），避免 tint 抹掉高光边；④ tab bar 去掉装饰分隔线，由玻璃顶边承担分层；⑤ 抽屉面板改用 `bg_glass_panel`。
- User-confirmation decisions: 见父卡 047。
- Acceptance evidence: `GlassMaterialTokensTest` RED/GREEN、`GlassSurfaceLayoutTest`、`:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`（仅编译）、`verifyNoKotlinDex`、`adb install -r` 后四屏截图。
- Facts: 唯一全屏背景是 `activity_main` 的 `shell_backdrop`；共享 drawable 被 40+ layout 引用；`HomeBackdropInstrumentedTest` 现断言 tab bar 为透明或 null；`HomeDrawerScreen` 用 `setBackgroundColor(tl_surface)`；`colors.xml` 只收 6 位 hex。
- Assumptions: 8 位 ARGB `<color>` 在 API 28+ 的 shape solid 中按 alpha 混合；layer-list 的渐变 sheen 不会显著增加绘制成本。
- Unknowns: 不同厂商对白色半透明的色偏；低端机多层半透明叠加的滚动表现。
- Risks: `backgroundTint` 会覆盖整个 drawable（含 stroke），必须迁移语义卡片；tab bar 断言的旧判据会失效，需改为非不透明；圆角变大可能让既有阴影在卡片边缘露出。
- Deferred: 实时背景模糊、动态取色、深色模式、玻璃动效。
- Non-goals: 不改 IA、文案、交互、数据库；不新增依赖或 Kotlin；不改 Web。
- Skipped non-critical questions and reason: 未追问 sheen 的具体高度与光斑坐标；由统一 drawable 与截图复核决定，不影响验收。

## Goal

让 Android 所有一级与二级界面落在同一套晨雾玻璃场景与共享玻璃表面上，保持既有布局、命中区域、业务与数据不变。

## Scope

### In scope
- `android/app/src/main/res/values/glass.xml`：rgba token 的 8 位 ARGB 副本。
- `android/app/src/main/res/values/dimens.xml`：`radius_xl=22dp`、`radius_2xl=28dp`。
- `android/app/src/main/java/com/treelearn/app/screen/PaperRuleBackdrop.java`：雾面渐变＋三处柔光＋横格，新增测试 seam。
- `android/app/src/main/res/drawable/`：`bg_glass_card.xml`、`bg_glass_reader.xml`、`bg_glass_field.xml`、`bg_glass_bar.xml`、`bg_glass_panel.xml`、`bg_glass_chip.xml`、`bg_glass_segment.xml`、`bg_semantic_alert.xml`、`bg_semantic_note.xml`；改写 `bg_card`、`bg_library_card`、`bg_topic_card`、`bg_composer_inner`、`bg_field`、`bg_library_search`、`bg_ask_ai_chip`、`bg_library_segment`（或新增玻璃段并替换引用）。
- `android/app/src/main/res/layout/screen_tab_bar.xml`：玻璃底、移除装饰分隔线。
- `android/app/src/main/res/layout/screen_chat_body.xml`：聊天外层条透明、阅读区玻璃、语义卡片迁移。
- `android/app/src/main/res/layout/item_chat_message.xml`：语义卡片迁移。
- `android/app/src/main/res/layout/screen_library_body.xml`：未选中 segment 去掉不透明 tint。
- `android/app/src/main/res/layout/activity_main.xml`（如需）：保持场景 view 在最底层。
- `android/app/src/main/java/com/treelearn/app/screen/HomeDrawerScreen.java`：玻璃面板。
- `android/app/src/main/res/values/themes.xml`：状态栏取雾面基色。
- `android/app/src/test/java/com/treelearn/app/screen/GlassMaterialTokensTest.java`、`GlassSurfaceLayoutTest.java`。
- `android/app/src/androidTest/java/com/treelearn/app/HomeBackdropInstrumentedTest.java`：tab bar 断言改为非不透明。

### Out of scope
- 全量 instrumentation 运行；实时模糊；深色模式；新字体/图标。
- Web `glass.css`（050）。
- 业务逻辑、文案、数据库、接口。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改背景与表面绘制；所有 id、可点区域、滚动、输入法、返回栈、数据不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 场景唯一生产者在 `PaperRuleBackdrop`；`PaperRules` 继续决定横格颜色/间距；共享 drawable 被 phone/tablet 两套 layout 复用。
- Native bridge or independent-client compatibility/update requirements: 不涉及桥与夹具。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: Android 背景为静态雾面场景；表面为半透明玻璃＋1px 高光边；tab bar 去分隔线并变为半透明；抽屉面板玻璃化；状态栏颜色变化。
- Authoritative code/config/migration evidence: `values/glass.xml`、`colors.xml`、`dimens.xml`、共享 drawable、`PaperRuleBackdrop`、`HomeDrawerScreen`。
- Documentation surfaces checked: `android/README.md`、`.codex/rules/android.md`、`.codex/rules/multi-device.md`。
- Documentation files updated: `android/README.md` 的材质与降级说明（051 汇总时复核）。
- Producer/consumer/sibling/compatibility paths checked: `HomeBackdropInstrumentedTest` 的透明 tab bar 判据、`bg_card` 的两处 `backgroundTint`、phone/tablet layout。
- Superseded phrases/examples reverse-searched: 「tab bar 透明」「纸感不透明 surface」——改为「非不透明」与「玻璃表面」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留纸感历史记录，追加材质变更。

## Acceptance criteria

- [x] `GlassMaterialTokensTest`：`tokens.json` 里的 rgba 玻璃/光斑 token 与 `values/glass.xml` 的 8 位 ARGB 逐项一致；4 个 mist hex 与 `colors.xml` 一致；资源名映射可逆。
- [x] `GlassSurfaceLayoutTest`：tab bar 使用玻璃底且不再有分隔线；聊天、首页输入、学习库搜索/分段、设置卡片引用玻璃 drawable；语义卡片不再用 `bg_card + backgroundTint`。
- [x] `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.GlassMaterialTokensTest" --tests "com.treelearn.app.screen.GlassSurfaceLayoutTest"` RED 非 0、GREEN 为 0，命令一致。
- [x] `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` 退出码 0（仪器源码只编译）。
- [x] `./gradlew verifyNoKotlinDex` 退出码 0。
- [x] `adb install -r` 后首页、对话、学习库、设置四屏截图可见玻璃场景与高光边，既有数据未被清理。
- [x] 既有 `PaperRulesTest`、`HomeComposerLayoutTest`、`ChatMotionTest` 等 JVM 套件仍绿。
- [x] 未自动提交或推送；未新增依赖。

## TDD execution record

### RED — failing test first
- Test/scenario: 两个新 JVM 测试类在资源、场景与共享 drawable 落地前先跑：`GlassMaterialTokensTest`（10 个 rgba + 4 个 mist 的映射）与 `GlassSurfaceLayoutTest`（9 条表面钉）。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.GlassMaterialTokensTest" --tests "com.treelearn.app.screen.GlassSurfaceLayoutTest"`
- Exit code: 1
- Transcript: `artifacts/evidence/glass-material-2026-10-21/android-red.txt`
- Observed failure: `11 tests completed, 10 failed`——`everyGlassTokenHasTheSameArgbCopyOnAndroid` 在 `glass.xml` 尚不存在时失败；`GlassSurfaceLayoutTest` 的 9 条钉全红（玻璃 drawable 不存在、tab bar 仍有分隔线、聊天阅读面与 composer 仍是实色、语义卡片仍是 `bg_card + backgroundTint`、未选中 segment 仍涂 `tl_surface`、抽屉仍涂实色、场景仍未消费 mist/glow、圆角仍未成为 token）。唯一绿的是 048 已落地的 mist hex 对照。
- Failure reason validated: 失败源于目标实现缺失，不是环境或偶发：同一命令在实现后转绿，失败集合与改动面一一对应。

### GREEN — minimum implementation
- Implementation: 新增 `values/glass.xml`（10 个 rgba 的 ARGB 副本）与 `radius_xl|2xl`；`PaperRuleBackdrop` 改为 mist 渐变 + 三处柔光 + 横格；新增 9 个玻璃/语义 drawable 并把既有共享 drawable 别名过去；tab bar、聊天阅读面、composer、学习库 segment、抽屉面板与状态栏跟随；语义卡改用 `bg_semantic_alert`／`bg_semantic_note`。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.GlassMaterialTokensTest" --tests "com.treelearn.app.screen.GlassSurfaceLayoutTest"`
- Exit code: 0
- Transcript: `artifacts/evidence/glass-material-2026-10-21/android-green.txt`（`BUILD SUCCESSFUL in 30s`）
- Passing evidence: 两个测试类全部通过；`GlassMaterialTokensTest` 逐项核对 10 个 rgba→ARGB 与 4 个 mist hex，`GlassSurfaceLayoutTest` 9 条表面钉全绿。
- Regression evidence: 首轮全量 JVM 暴露 `ChatMotionLayoutStabilityTest.theFailureBarUsesTheRedTokens`（要求软红仍在）——改为独立的 `bg_semantic_alert` 保留 `tl_red_soft` 后，全量 `:app:testDebugUnitTest` 859 例全绿（见 051 汇总）。

### REFACTOR — optional
- Cleanup: 删除仅用于语义卡的过渡 drawable `bg_semantic_card.xml`，没有留下无引用资源。
- Regression evidence: 同一 JVM 套件保持全绿。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- `GlassMaterialTokensTest` 从 `src/test` 工作目录（`android/app`）读 `../../Docs/04-ux-design/design-system/tokens.json`，用正则读取 rgba 条目，不引入 JSON 依赖。
- rgba→ARGB：`round(alpha * 255)`，按 `#AARRGGBB` 大写比较；玻璃资源名 = token 名把 `-` 换成 `_`（`glass-chrome` → `glass_chrome`，`glow-green` → `glow_green`）。
- 场景绘制：先铺四角线性渐变的 mist 底，再画三处径向柔光（绿/沙/白），最后画 `PaperRules` 横格；所有颜色来自资源，坐标按宽高比例计算。
- 1px 边：shape `<stroke android:width="@dimen/hairline" android:color="@color/glass_edge" />`；sheen 用 layer-list 顶部渐变。
- 不新增 `backgroundTint`；需要语义色的两张聊天卡片改用不透明的 `bg_semantic_alert`（`tl_red_soft`）与 `bg_semantic_note`（`tl_green_faint`）。
- tab bar 断言改为：background 非 null 且 `getOpacity() != PixelFormat.OPAQUE`，并保留「页面不各自拥有第二份背景」的断言。

## Verification report

- Verdict: PASS
- Evidence: 定向 RED `rc=1`（11 例中 10 红）/ GREEN `rc=0`；全量 `:app:testDebugUnitTest` 96 个结果文件共 859 例、0 失败、0 错误（`artifacts/evidence/glass-material-2026-10-21/android-jvm-results.txt`）；`assembleDebug` + `assembleDebugAndroidTest` + `verifyNoKotlinDex` PASS，`BUILD SUCCESSFUL in 3m 34s`（`android-build.txt`）；`adb install -r` 后四屏截图与原始数据库恢复读数见证据目录 README。
- Changed/deleted assertion rationale and equivalent behavior coverage: `HomeBackdropInstrumentedTest` 对 tab bar 的旧判据是「透明或 null」，玻璃化后合理形态是「非 null 且 `getOpacity() != PixelFormat.OPAQUE`」——保留「页面不各自拥有第二份背景」断言，仍能防止底栏遮住场景。`ChatMotionLayoutStabilityTest.theFailureBarUsesTheRedTokens` 的要求（失败条仍用软红）未删，只把引用从 `bg_card + tint` 改为独立的 `bg_semantic_alert`，红色 token 断言原样保留。
- Gaps: Android 按设计不做实时背景模糊；平板横屏未单独截图复核；聊天与学习库的玻璃读数来自临时种子库（已恢复删除）；厂商白色半透明色偏未做仪器测量。
- Risks: 8 位 ARGB 半透明叠加在低端机滚动时可能有帧率损失；后续新增表面若继续新建实色 drawable 会绕过玻璃层；`backgroundTint` 若被重新施加到玻璃 drawable 会抹掉 1px 高光边。

## Handoff notes

- Completed: token 映射、共享玻璃 drawable、雾面场景、tab bar/抽屉/composer/segment/语义卡、JVM 全量与构建守门、真机四屏取证。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 051 已汇总；后续材质扩展沿用 `bg_glass_*` 与 `glass.xml`。
- Watch-outs: 不要跑全量 instrumentation；不要把 rgba 写进 colors.xml；不要在 Java 里写死颜色。
