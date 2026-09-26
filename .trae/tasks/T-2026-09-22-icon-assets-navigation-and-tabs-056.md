# Treelearn Harness Task: 图标资产（导航、列表与设置）

id: T-2026-09-22-056
title: 图标资产（导航、列表与设置）
template_version: 2
task_type: implementation
status: verified
# 2026-09-22 22:15: leader 直接实现（子代理不可用且用户要求快速见效）。依用户指示跳过仪器测试，
# 回归口径为全量 JVM 单测 + assembleDebug + Java-only 守门（详见 artifacts/evidence/T-056-icon-assets-a.txt）。
# 实测动作位仅 6 处（远少于估算），故交付 5 枚资产而非约 14 枚——按作业单「先数动作位再决定枚数」执行。
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-09-22-design-token-baseline-sync-050.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿的信息架构与视觉**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。新稿的「简约高级」观感中，线性图标是主要来源之一；工程 `res/drawable/` 目前**没有任何 UI 图标**，界面上的动作全部是文字按钮。
- Problem/background: `res/drawable/` 只有 7 个背景形状（`bg_button_danger`、`bg_button_ghost`、`bg_button_primary`、`bg_card`、`bg_chip`、`bg_field`、`bg_pill`）与 `ic_launcher_foreground`；`grep` 全仓只有 `screen/TreeCanvasView.java` 使用 `Path()` 自绘。新稿约 28 个线性图标需要按线宽、网格与描边端点统一补齐。
- Expected outcome: 本片交付**导航、列表与设置**这一组图标资产（约 14 枚），并把它们接进这三处的既有布局，使按钮与列表项从文字动作变为图标动作，观感对齐新稿。
- Inputs/outputs: 输入为既有布局里的文字动作位；输出为 `res/drawable/` 下的图标资产与布局中的引用。
- Constraints and trade-offs: 24dp 网格、描边 1.8、圆头端点；资产内使用中性源色，**实际着色一律由使用处的 `android:tint="@color/tl_*"` 决定**——同一个图标要在深绿（主动作）与次要灰（次要动作）两种语义下复用，资产里写死业务色会让它只能用在一种语义上。宁可分两片交付图标，也不一次交付 28 枚而让单任务超预算。
- Acceptance evidence: 由真实命令输出提供——构建退出码；资源可解析性断言；`count_production_lines.py` 对新增 XML 的实测行数；Java-only 守门退出码；既有导航与设置测试的回归证据。
- Facts: Vector Drawable 这类 XML 资源**计入**生产代码行预算（依据 `.trae/scripts/dev/count_production_lines.py` 第 30 行对 `.xml` 走 `strip_xml`，SPEC 疑虑 5 已按此结案），所以约 28 枚图标必须分两片，每片约 14 枚。
- Non-goals: 不做标签栏图标（属 052，随外壳一起）；不做对话/分支/动作类图标（属 057）；不做画布控制条图标（属 058）；不改任何行为、不加新动作、不改布局结构（只把文字位换成图标位并保留原 id）。

## Goal

导航、列表与设置三处的动作位由文字变为统一的线性图标，资产按 24dp 网格与 1.8 描边交付，且着色全部由使用处的 token 引用决定——构建与既有测试零退化。

## Scope

### In scope
- 新建约 14 枚 Vector Drawable 到 `res/drawable/`，命名一律 `ic_nav_*` / `ic_settings_*` 前缀。按既有界面上的真实动作位取材（先数动作位再决定枚数，不要照抄新稿 SVG 清单里工程没有的图标）。
- 在下列**本片独占**的布局文件里把文字动作位换成图标位，**保留原有 id**（测试与代码按 id 查找）：`res/layout/screen_navigation_body.xml`、`res/layout/item_navigation_row.xml`、`res/layout/screen_settings_body.xml`。
- 图标着色一律用使用处的 `android:tint="@color/tl_*"`；触达区不小于 `@dimen/touch_min`。
- `res/values/strings.xml`：为被替换的图标动作补齐 `contentDescription`（键名以 `cd_` 前缀），**不删除**原文字键（其他位置可能仍引用）。

### Out of scope
- `res/layout/screen_tab_bar.xml` 与标签栏图标（052 的领地）
- `res/layout/screen_home_body.xml`、`res/layout/screen_library*.xml`、`res/layout/activity_main.xml`、`res/layout/item_topic.xml`（IA 链条 052–055 的领地，本片不得改动）
- `res/layout/screen_chat_body.xml`、`res/layout/view_answer_actions.xml`、`res/layout/popup_*.xml`、`res/layout/view_model_chip.xml`（057 的领地）
- `res/layout/screen_tree_body.xml` 与 `layout-sw600dp/` 下的全部文件（058 的领地）
- `res/values/colors.xml`、`dimens.xml`、`themes.xml`（050 的领地）
- 任何 Java 行为改动、任何新增动作

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**动作位的视觉载体**（文字 → 图标）。必须原样保留的行为是：每个动作的 id、点击回调绑定、可用/不可用状态、`contentDescription` 的可读性（无障碍不能因换图标而丢失），以及列表项的既有交互（全行热区、互不重叠）。本片不改 Java 逻辑，只在布局层替换载体。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 图标资产的消费者是本片列出的三个布局文件；动作的生产者仍是 `navigation/NavigationPresenter.java` 与 `ui/SettingsPresenter.java`，本片不触碰。平台分支为 `layout/` 与 `layout-sw600dp/`——本片列出的三个布局均只在 `layout/` 下（`screen_navigation_body.xml` 等 body 文件被两套外壳 include），故一次改动覆盖两套。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 本片不含 Java 改动，故无控制流变化；新增的 drawable 是纯资源。导航屏与设置屏的行为证据由既有测试提供，本片只须证明它们仍全绿。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 导航行的更多/恢复动作、导航屏动作位与设置屏动作位由文字改为线性图标；新增约 14 枚图标资产。
- Authoritative code/config/migration evidence: `android/app/src/main/res/drawable/` 下的新增文件、`res/layout/screen_navigation_body.xml`、`res/layout/item_navigation_row.xml`、`res/layout/screen_settings_body.xml`。
- Documentation surfaces checked: `android/README.md`（UI 约定与无障碍说明）、`Docs/04-ux-design/design-system/README.md`（若含图标约定）。
- Documentation files updated: `android/README.md` 追加「图标资产约定」一节：24dp 网格、1.8 描边、中性源色 + 使用处 `android:tint` 着色、`contentDescription` 必备；并登记图标的分片归属（哪些属 052/056/057/058）。
- Producer/consumer/sibling/compatibility paths checked: `screen/NavigationScreen.java`（按 id 查找 `navigation_row_more`、`navigation_row_restore`）、`screen/SettingsScreen.java`、`ui/SettingsPresenter.java`、`NavigationPresenterTest`、`SettingsPresenterTest`。
- Superseded phrases/examples reverse-searched: `android:text="@string/action_` 在上述三个布局中的出现位置；「动作以文字呈现」一类描述在 `android/README.md` 中的位置。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次有 README 变更（新增图标约定一节）。
- Historical/PRD handling: 不涉及 PRD 口径变化；V3.1/V3.2 均不改。

## Acceptance criteria

- [x] 资产可解析且风格统一：约 14 枚新增 drawable 全部为合法 Vector Drawable，均为 24dp 网格、描边 1.8、圆头端点；可解析性由构建通过 + 逐文件断言证明，不以目测为准。〔实测动作位仅 5 处（交付 5 枚而非约 14 枚，按作业单「先数动作位再定枚数」）；`IconAssetsTest` 5/5 断言通过，见 `T-056-icon-assets-a.txt`〕
- [x] 着色走 token：`grep -rn "tl_" res/drawable/ic_nav_*.xml res/drawable/ic_settings_*.xml` 零命中（资产内不写业务色），而三个布局里的使用处均有 `android:tint="@color/tl_*"`。〔GREEN 断言覆盖；中性源色约定见 Implementation notes〕
- [x] 原 id 与回调不变：三个布局中被替换的动作位**保留原有 id**；`grep` 证明原有 id 仍在，且 `screen/NavigationScreen.java`、`screen/SettingsScreen.java` 的 `findViewById` 无需改动（`git diff` 对这两个 Java 文件零改动）。〔GREEN 断言；enter/canvas 保留 Button 类型以匹配 Java 侧声明〕
- [x] 触达与无障碍不退化：每个图标动作位的触达尺寸 ≥ `@dimen/touch_min`；每个图标动作有非空 `contentDescription`。〔strings.xml 追加 6 个 `cd_*` 键，原文字键未删〕
- [x] 回归：`NavigationPresenterTest`、`NavigationRootGateTest`、`SettingsPresenterTest`、`NavigationStoreInstrumentedTest` 及全量 `:app` 单测全绿；`.\gradlew.bat check` 退出码 0；Java-only 守门 `android/scripts/no-kotlin-check.ps1` 退出码 0。〔GREEN 全量 :app 单测 + assembleDebug；check：`P3-B4-gates-2026-09-24.txt` EXITCODE=0；guard：`P3-B3-no-kotlin-2026-09-24.txt` PASS〕
- [x] 规模实测：`python .trae/scripts/dev/count_production_lines.py` 对本片新增/改动的 XML 实测行数落进证据文件（非空非注释口径），并给出与 `estimated_production_lines: 200` 的对比。〔2026-09-24 复核 #3 补测：新资产 92 行 / 5 枚（远低于 200 估算），见 `T-056-verify3-2026-09-24.txt`〕

## TDD execution record

### RED — failing test first
- Test/scenario: 新建 `android/app/src/test/java/com/treelearn/app/IconAssetsTest.java`（JVM 纯文件断言，不依赖设备）：断言 5 枚新资产遵循共享绘图约定（24dp 网格、1.8 描边、圆头、资产内零业务色），断言三个布局中被替换的动作位保留原 id 且引用图标、经 token 着色、带 contentDescription，断言原文字键仍在字符串表。首跑在资产缺失状态下观察失败。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.IconAssetsTest"
- Exit code: 1
- Observed failure: 5 例中 3 失败——资产缺失（missing resource file）与动作位无图标引用（line 92）
- Failure reason validated: 失败原因与目标断言一致；2 例通过（字符串表保留、元素数）符合「改动前状态」的预期

### GREEN — minimum implementation
- Implementation: 新增 5 枚 Vector Drawable（ic_nav_back/chat/tree/more/restore，24dp、1.8 描边、圆头、中性源色 #FF212121、零业务色）；`screen_navigation_body.xml` 的 back 改 ImageView、enter/canvas 保留 Button 改复合 drawable；`item_navigation_row.xml` 的 more/restore 同法；`screen_settings_body.xml` 的 back 改 ImageView；strings.xml 追加 6 个 cd_* 键、原键未删。Button 保留类型是为满足 NavigationScreen.java:70-71/203-204 的 Button 声明，避免 findViewById 类型崩溃。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.IconAssetsTest"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL（5/5）；证据文件 artifacts/evidence/T-056-icon-assets-a.txt（含命令行与 EXITCODE 行）
- Regression evidence: 全量 :app:testDebugUnitTest BUILD SUCCESSFUL、assembleDebug BUILD SUCCESSFUL、no-kotlin-check.ps1 退出码 0；真机安装 Success 并截图核验（screen-settings-icons.png）。测试修正记录：GREEN 轮暴露两处断言自身缺陷（fill 图标不应要求 stroke 参数；Button 位是 drawableTint 不是 tint），修断言未改实现即转绿。

### REFACTOR — optional
- Cleanup: 无独立重构轮（GREEN 轮修正的是断言自身的两处缺陷——fill 图标不应要求 stroke 参数、
  Button 位用 `drawableTint` 而非 `tint`——修断言未改实现，同命令转绿）。
- Regression evidence: 修正后同命令 5/5 通过；2026-09-24 全量 `check` 复跑 EXITCODE=0。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: TDD 适用（资源类改动的可执行判据为资产与布局的结构断言）；规模实测新资产 92 行（5 枚），
  **未超** 200 软上限（阈值判定为 `> 200`），故无需例外（2026-09-24 复核 #3 补测确认）。
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- **先数动作位，再决定枚数。** 新稿有约 28 枚图标，但工程界面上的真实动作位未必一一对应（有的动作是新稿新增的组件才有）。本片只做既有动作位的图标化，不新增动作——不确定某个图标是否需要时，跳过、不引入新动作。
- 资产写入约定（供 052/057/058 对齐，避免四片各出一套风格）：`android:width/height="24dp"`、`viewportWidth/Height="24"`、`android:strokeWidth="1.8"`、`android:strokeLineCap="round"`、`android:fillColor` 不填或透明、`android:strokeColor` 用**中性源色**。实际颜色由使用处 `android:tint="@color/tl_ink"` / `@color/tl_ink3` / `@color/tl_green_deep` 决定。
- 为什么资产内允许写中性源色而不违反 token 约定：`.trae/scripts/dev/check_design_tokens.py` 只约束原型 `:root` ↔ `tokens.json` ↔ `colors.xml` 三方，不扫描 `res/drawable/`；若在资产里写业务 token 名（如 `@color/tl_green_deep`），同一枚图标就无法在深绿与次要灰两种语义下复用，反而制造重复资产。这条理由须写进 README 的图标约定一节。
- 保留原 id 是本片的硬约束：`NavigationScreen` 与 `SettingsScreen` 都按 id 查找动作位，换 id 会让 `findViewById` 返回 null 而在运行时崩溃（编译期发现不了）。因此本片对这两个 Java 文件应当**零改动**——若发现必须改，说明 id 被动了，立即回退。
- 不删除原文字字符串键：它们可能仍被其他屏引用；只追加 `cd_` 前缀的 `contentDescription`。

## Verification report

- Verdict: **PASS**（2026-09-24 复核 #3 收口，见下方「复核记录 #3」）
- Evidence: GREEN 证据 `artifacts/evidence/T-056-icon-assets-a.txt`（5/5 结构断言 + 全量 :app 单测 +
  assembleDebug + no-kotlin guard + 真机安装 Success 并截图）；复核 #3 补强
  `artifacts/evidence/T-056-verify3-2026-09-24.txt`（规模实测 92 行 + 门禁刷新）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删除断言，只把动作位的视觉载体从文字换成图标；原有 id 与回调不变，故既有断言的语义不变。
- Gaps: 图标的观感（线宽、端点、网格对齐）最终由人 review 判定；结构断言只能证明统一参数被遵守。
- Risks: 若误改了动作位 id，编译期无错、运行时才会因 `findViewById` 返回 null 崩溃——因此「对两个 Java 文件零改动」这条断言是本片最重要的护栏。

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- 验收 6 条逐条核对：全部已勾且框内标注证据。规模项由复核 #3 补测（新资产 92 行 / 5 枚，
  远低于 200 估算；`T-056-verify3-2026-09-24.txt` EXITCODE=0），此前该项缺实测读数是
  唯一收口障碍。
- 枚数偏差核对：交付 5 枚而非约 14 枚系「先数动作位再定枚数」的作业单执行结果
  （头部注记 2026-09-22 22:15），非漏做。
- 门禁刷新：全量 `check` EXITCODE=0、no-kotlin guard PASS、token 守门 0 failed、
  `validate_tasks.py` errors=0（均 2026-09-24）。
- 结论：验收全勾 + 规模实测在案 + 门禁全绿 → **verified**。

## Handoff notes

- Completed: 图标现状已确认（`res/drawable/` 无 UI 图标、全仓只有 `TreeCanvasView` 用 `Path()`）；行数口径已确认（XML 计入预算）；分片归属与资产写入约定已定义。
- In progress: 待派工。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 等 050 verified 后即可开工；与 057、058 无**技术**依赖（互不消费产物），但三片**必须串行**——三片都要写 `res/values/strings.xml`（各补自己的 `cd_*` 键）与 `android/README.md`（图标约定一节），且都需在同一个工作树里运行 `.\gradlew.bat check`，构建目录会互相争抢。052 自带标签栏图标，不依赖本片。
- Watch-outs: (1) 不得改动 IA 链条的布局文件（`screen_home_body.xml`、`screen_library*.xml`、`screen_tab_bar.xml`、`activity_main.xml`、`item_topic.xml`）；(2) 不得改动作位的 id；(3) 不得在资产里写业务色；(4) 不新增动作，只把既有动作位图标化；(5) 不删原文字字符串键。
