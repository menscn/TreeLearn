# Treelearn Harness Task: 首页纸纹基元与整页背景层

id: T-2026-09-27-002
title: 首页纸纹基元与整页背景层
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 60
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页视觉 1:1 还原（父需求 T-2026-09-27-001）。用户已拍板：视觉 1:1、信息架构保持 V3.2、字体不打包、版式顺序按原型、走 Harness。
- 本任务在链路上的位置：链路第一个切片。它把「纸感横格」的画线判定落成**唯一实现**，供 004 的输入框信纸格线复用；同时把整页纹理层接到首页两种布局配置上。
- Inputs/outputs: 输入为既有 token（`@color/tl_bg`、`@color/tl_line`、`@dimen/space_s8`、`@dimen/hairline`）；输出为一个纯判定类、一个装饰 View、两个首页布局的接入、一个单元测试与一个仪器测试。
- State and exception handling: 装饰层无状态、不读数据、不接收触摸；未附加或尺寸为零时 `onDraw` 自然不画。
- Constraints and trade-offs: 唯一画线实现（本仓失败案例库：同一条规则两个入口会给同一输入两个结果）；不新增 token、不改 `colors.xml`/`tokens.json`；装饰层不可点、不可聚焦、对无障碍隐藏。
- AI recommendation and rationale: 判定（线距相位与派生色）抽进纯类以便 JVM 单测——`:app` 单测只有 JUnit、无 Robolectric，View 行为只能进 androidTest；把判定留在 View 里就等于无法回归。
- User-confirmation decisions: 无（本任务不含用户可见的取舍）。
- Facts: 原型 `.screen` 的底色与横格在同一处声明——`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 第 115 行 `background-color:var(--bg)` + 第 116 行 `background-image:repeating-linear-gradient(to bottom,transparent 0 31px,var(--paper-rule) 31px 32px)`；`--paper-rule` 的 token 定义在第 72 行。画布已有同构先例 `android/app/src/main/java/com/treelearn/app/screen/GridBackdrop.java` 与其仪器测试 `android/app/src/androidTest/java/com/treelearn/app/CanvasBackdropInstrumentedTest.java`（路径已实测确认存在）。
- Risks: 全屏装饰层若可点会吃掉抽屉与滚动手势；派生色若与原型差得明显，纸纹会读成参考线。
- Out of scope for this task: 输入卡内的信纸格线（004 复用本任务的画线实现）、其它页面的背景、任何 token 数值变更。

## Goal

首页在手机与平板两种配置下都呈现与原型同构的纸感横格背景，且横格的线距相位与派生色只有一个判定实现（`com.treelearn.app.screen.PaperRules`），并被单元测试钉住。

## Scope

### In scope

- 新增 `app/src/main/java/com/treelearn/app/screen/PaperRules.java`：线距相位与「由 `line` 派生纸纹色」的纯判定（纯整数运算，不触碰任何 Android API，保证可 JVM 测试）。
- 新增 `app/src/main/java/com/treelearn/app/screen/PaperRuleBackdrop.java`：纯装饰 View，按 `PaperRules` 画横线。
- 接入 `res/layout/screen_home.xml` 与 `res/layout-sw600dp/screen_home.xml`。
- 新增 `app/src/test/java/com/treelearn/app/screen/PaperRulesTest.java`。
- 新增 `app/src/androidTest/java/com/treelearn/app/HomeBackdropInstrumentedTest.java`：验收点 2 要求「两种配置都渲染 + 不可点/不可聚焦/对无障碍隐藏」，这三条只有可执行断言能钉住（截图钉不住「不可聚焦」）。写法照同构先例 `app/src/androidTest/java/com/treelearn/app/CanvasBackdropInstrumentedTest.java`。该测试断言的是**已经接入完成**的布局，因此首次运行即为 GREEN——这不是 TDD 的 RED，TDD 的 RED/GREEN 由上面的单元测试承担，此处不计入 TDD 记录。

### Out of scope

- 输入框信纸格线（004）；其它页面背景；`bg_*` 共享 drawable；token 数值。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增背景纹理；首页的滚动、抽屉按钮点击、输入与提交行为一分不改。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者是首页布局自身（`screen_home.xml` 被 `HomeScreen` inflate，手机与平板两份配置都 include `screen_home_body.xml`）；装饰层不参与任何 presenter/ViewState 路径。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（有可见变化）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首页新增一个全屏装饰层（`home_backdrop`）与其自绘类；首页根布局由 `ScrollView` 变为 `FrameLayout` 包 `ScrollView`（`home_root` 语义不变，仍是那个可滚动列）。
- Authoritative code/config/migration evidence: `screen/PaperRules.java`、`screen/PaperRuleBackdrop.java`、`res/layout/screen_home.xml`、`res/layout-sw600dp/screen_home.xml`。
- Documentation surfaces checked: `android/README.md` 首页一节（应用层入口索引 T-012）。
- Documentation files updated: 由 006 统一回写 `android/README.md`（本任务只在此登记，避免两处各写一份）。
- Producer/consumer/sibling/compatibility paths checked: `GridBackdrop`（同构先例）与其仪器测试；`HomeScreen.java` 的 `findViewById` 列表（未新增/未改名任何既有 id）。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 无。

## Acceptance criteria

- [x] 单元测试钉住判定：线距相位（首条线在一档间距处、按间距推进、越过高度即止）与派生色（`line` 以 `ruleAlpha` 叠在 `bg` 上的合成值落在原型 `--paper-rule` 合成值的容差内），命令与证据见 TDD 记录。
- [x] 手机（`layout/`）与平板（`layout-sw600dp/`）两种配置都渲染纸纹，且装饰层不可点、不可聚焦、对无障碍隐藏（仪器测试证据：`HomeBackdropInstrumentedTest`；另有 APK 级 aapt2 取证，见 TDD 记录）。
- [x] 旧行为保持：`AppLaunchInstrumentedTest` 与 `BottomTabShellInstrumentedTest` 全绿（首页 id 契约与抽屉按钮不受影响）。

## TDD execution record

### RED — failing test first
- Test/scenario: `PaperRulesTest` 的 6 个用例（线距相位、退界、退化间距、派生色合成、合成边界、通道饱和）；首次运行时被测类型 `com.treelearn.app.screen.PaperRules` 尚不存在。
- Command: JAVA_HOME=<jdk21> ./gradlew --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.screen.PaperRulesTest"
- Workdir: android/
- Exit code: 1
- Observed failure: `> Task :app:compileDebugUnitTestJavaWithJavac FAILED` —— `app/src/test/java/com/treelearn/app/screen/PaperRulesTest.java:42` 报「错误: 找不到符号 / 符号: 变量 PaperRules」，gradle 汇总 **20 个错误**、40 行「找不到符号」；`BUILD FAILED in 1s`。
- Failure reason validated: 失败发生在**编译期，且每条错误都只指向缺失的新类型 `PaperRules`**，不是环境问题——同一次运行的 `:app:javaPreCompileDebugUnitTest` 为 `UP-TO-DATE`，说明测试源集与依赖本身可用；把对 `PaperRules` 的引用去掉即可编译。失败原因正是本任务要新增的判定行为。证据 `artifacts/evidence/T-2026-09-27-002-red.txt`。

### GREEN — minimum implementation
- Implementation: `PaperRules.java`（`lineOffsets`、`composite`、`ruleAlpha()`=79、`withAlpha`/`rgbOf`，全部纯整数运算）+ `PaperRuleBackdrop.java`（构造里只调 `PaperRules` 与既有 token，不复制判定）+ 两份 `screen_home.xml` 的接入。
- Command: JAVA_HOME=<jdk21> ./gradlew --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.screen.PaperRulesTest"
- Workdir: android/（与 RED 同一条目标测试命令，字符串逐字相同）
- Exit code: 0
- Passing evidence: artifacts/evidence/T-2026-09-27-002-green.txt —— BUILD SUCCESSFUL；单测报告 tests="6" skipped="0" failures="0" errors="0"。
- Regression evidence: artifacts/evidence/T-2026-09-27-002-green-layout.txt、artifacts/evidence/T-2026-09-27-002-green-regression.txt、artifacts/evidence/T-2026-09-27-002-androidtest.txt（三份均为当场捕获，各自自带命令行与 EXITCODE；明细见下表）

| 证据文件 | 命令 | 退出码 | 结果 |
| :--- | :--- | ---: | :--- |
| `T-2026-09-27-002-green-layout.txt` | `./gradlew --offline :app:assembleDebug`（布局接入后） | 0 | aapt2 直接核验 APK 内编译后的两份布局：`res/layout/screen_home.xml` 与 `res/layout-sw600dp-v13/screen_home.xml` 首个子节点都是 `com.treelearn.app.screen.PaperRuleBackdrop`，且排在 `ScrollView` 之前（XML 子节点序 = 绘制序）；两项判定 PASS |
| `T-2026-09-27-002-green-regression.txt` | `./gradlew --offline :app:testDebugUnitTest --tests "*PaperRulesTest" :app:assembleDebug` | 0 | 单测仍 `tests="6" failures="0" errors="0"`；APK 正常产出（1988325 字节） |
| `T-2026-09-27-002-androidtest.txt` | `./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=HomeBackdropInstrumentedTest,AppLaunchInstrumentedTest,BottomTabShellInstrumentedTest` | 0 | 真机 AWSS013807000356（ALP-AN00 / Android 13）**8 用例 0 失败 0 错误**：`HomeBackdropInstrumentedTest` 2 + `BottomTabShellInstrumentedTest` 5 + `AppLaunchInstrumentedTest` 1 |

### REFACTOR
- 无。判定在设计时就已抽成纯类（`PaperRules`），View 只调不判，未出现需要再抽取的重复。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（已按 TDD 执行，RED/GREEN 如上）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

### 规模记录（判定口径：非空非注释生产代码行）
- 新增 Java：`PaperRules.java` 64 + `PaperRuleBackdrop.java` 42 = **106 行**（命令：`grep -v 空行/注释 | wc -l`，见本轮执行记录）。
- 既有布局改动：`git diff --numstat` 得 `layout/screen_home.xml` +22、`layout-sw600dp/screen_home.xml` +27 = **49 行**（其中含 XML 注释，故此处是上界）。
- 合计 ≤ **155 行**，未超 200 软上限，远低于 500 硬上限。物理行参考：两份新 Java 共 201 行；测试不计入。
- 有意偏差：`estimated_production_lines: 60` 低估了（实际 106+49）。偏差原因是初稿估的是「两个类 + 少量布局改动」，实际布局接入需要包一层 `FrameLayout` 并写清层叠契约注释。仍在 200 以内，不构成例外。

## Implementation notes

- 派生色推导（落在 `PaperRules` 的注释里，必须标原型出处）：原型 `--paper-rule` 是 rgba(30,43,35,.028) 叠在 `--bg` 上，合成约 (238,236,229)；既有 token `line`(#E2DFD6) 以 `ruleAlpha`(79/255≈0.31) 叠在 `bg`(#F4F1EB) 上得 (238,235,228)，每通道差不超过 2/255。故用 `line` 派生而不新增资源。
- 线距取 `@dimen/space_s8`（32dp），线粗取 `@dimen/hairline`（1dp）。依据是原型 `.screen` 的横格定义：`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 第 116 行 `repeating-linear-gradient(to bottom,transparent 0 31px,var(--paper-rule) 31px 32px)` —— 周期 32px、线占 31→32px，故首条线顶边在 31、周期 32。任务初稿此处误记为 `space_s6`(24dp)，已按原型行号更正（24dp 会让首条线落在 23 而不是 31，观感与相位都不符）。
- 装饰层照 `GridBackdrop` 的做法：`setClickable(false)`、`setFocusable(false)`、`IMPORTANT_FOR_ACCESSIBILITY_NO`。
- 布局接入方式：把现有 `ScrollView` 包进一层 `FrameLayout`（新 id `home_screen`），装饰层作为第一个子节点画在 `ScrollView` 之下（不得包住 `ScrollView` 的子节点，否则会被滚动带走）；`home_root` 仍留在那个 `ScrollView` 上，语义不变（全仓无代码/测试引用 `home_root`，已 grep 确认）。
- 装饰层不设自身底色：页面底色来自 `themes.xml` 的 `windowBackground=@color/tl_bg` 与 `activity_main` 根，缝隙透出的正是 `tl_bg`，与原型的 `background-color:var(--bg)` 等价。
- 平板配置比手机多一层居中 `FrameLayout`（限宽 `content_measure`），纹理层放在**最外层**，因此居中列两侧留白同样有横格——与原型「横格属于屏幕容器」一致。

## Verification report

- Verdict: PENDING（本任务证据已齐，终判由 `T-2026-09-27-home-visual-fidelity-verification-008.md` 出具；本任务不自判 verified）
- Evidence: 见 TDD 记录的 4 份证据文件（红 1、绿 1、回归 2），全部位于 `artifacts/evidence/`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。**2026-09-28 由 008 独立验收更正（G2）**：此表述与生产事实不符。本任务的装饰层接入**已被同日 T-010 壳层方案取代**——现树中两份 `layout*/screen_home.xml` 均**不再包含** `PaperRuleBackdrop`（现为 `FrameLayout>ScrollView>include`），纸纹层上移至 `activity_main.xml` 的 `shell_backdrop`；`home_backdrop` 该 id 全仓 grep 0 命中。随之 `HomeBackdropInstrumentedTest` 被整写为壳层断言（该文件:26-38 自注 T-010 acceptance，并新增反向断言「screen_home 不得再带第二层纸纹」:73）。等价覆盖成立且经 verifier 隔离复跑 3 例全绿（两配置渲染、不可点/不可聚焦/无障碍隐藏、token 取值）。本任务的 `PaperRules` 判定实现本身仍是唯一判定点，未被推翻。
- Gaps: 真机截图上纸纹的**观感**（是否读作纹理而非参考线）未确认——仪器测试只能证结构，证不了观感；留给 008 的真机截图与人工目视。
- Risks: 低密度屏上横线可能偏明显（1dp 线在低 dpi 下视觉占比更大）；若观感不成立，调整手段是降 `ruleAlpha` 或改 `hairline` 为更细，均在 `PaperRules` 单点完成。

## Handoff notes

- Completed: `PaperRules`（唯一判定）+ `PaperRuleBackdrop`（装饰层）+ 两份首页布局接入 + 单测（6 例）+ 仪器测试（2 例）+ 4 份证据。
- In progress: 无（实现已结束，待 008 复核）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 008 跑 `check_design_tokens.py` 与 Java-only 守门并出终判；② 若 008 判定观感不成立，按「Risks」里的单点手段回调；③ 003 起按依赖链继续，`screen_home_body.xml` 与 `HomeScreen.java` 的改动须串行。
- Watch-outs: 不得在 `PaperRuleBackdrop` 里重复一份线距/颜色判定（004 要复用同一实现）；不得改动共享 `bg_*` 资源；`home_backdrop`/`home_screen` 是新增 id，改动首页布局的后续任务不要移除或改名。
  **已过时（2026-09-28 核）**：本节末尾的 id 约定已不成立——纸纹层改由壳层 `activity_main.xml` 的 `shell_backdrop` 承载，
  `home_backdrop` 已不存在；后续任务请以 `activity_main.xml` 的壳层为准，勿按本行回改首页布局。
