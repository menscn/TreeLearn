# Treelearn Harness Task: 首页输入卡视觉与发送控件

id: T-2026-09-27-004
title: 首页输入卡视觉与发送控件
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 90
shared_code_touched: false
depends_on: [T-2026-09-27-home-layout-order-hero-003.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页视觉 1:1 还原（父需求 T-2026-09-27-001）。用户已拍板：视觉 1:1、信息架构保持 V3.2、字体不打包、版式顺序按原型、走 Harness。
- 本任务在链路上的位置：链路第三个切片，在 003 排好的列里只改**输入卡内部的视觉**，不动列顺序。
- Inputs/outputs: 输入为 `HomeViewState` 的草稿、可提交判定、Key 状态与模型/思考态；输出为输入框信纸格线、圆形发送控件、模型胶囊与 Key 胶囊的视觉与尺寸。
- State and exception handling: 草稿为空或提交中时发送控件禁用；Key 未配置时显示警示胶囊、已配置时显示就绪胶囊；模型 chip 的思考态追加标记。以上判定的来源都不变，只换承载视觉。
- Constraints and trade-offs: 可点区域不小于 `touch_min`；禁用态不得只靠颜色传达（`enabled` 属性必须同时成立）；不新增灰色 token——禁用底取既有 `line`；格线色与整页纸纹共用 `PaperRules` 的同一画线实现。
- AI recommendation and rationale: 信纸格线用自绘 `EditText` 子类而不是在输入框下垫一层装饰 View，因为原型的格线随内容滚动（`background-attachment: local`），垫层做不到；自绘只复用 `PaperRules` 的线距与派生色，不新增判定。
- User-confirmation decisions: 字体不打包（用户拍板）；禁用态颜色不新增 token（AI 决策，已在父需求登记）。
- Facts: 原型输入卡结构见 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 第 152-156 行；发送控件为圆形按钮（第 138-141 行）；模型 chip（第 354-356 行）与 Key 胶囊（第 137 行）都要求胶囊形态；原型禁用底是未登记的硬编码灰，本轮取既有 `line` 而不新增资源。
- Risks: `EditText` 自绘若与光标/选择手柄冲突会伤输入体验；发送控件类型变更（文字按钮 → 图标按钮）会让既有仪器测试或无障碍断言失配。
- Out of scope for this task: 推荐区（005）、历史主题区（006）、动效（007）、整页纸纹（002，已完成）。

## Goal

首页输入卡与原型同构：输入框呈现随内容滚动的信纸格线，右下为圆形发送控件（禁用档取既有 token 且不只用颜色表意），模型与 Key 均为胶囊且不再比原型大一档。

## Scope

### In scope

- `res/layout/screen_home_body.xml`：输入卡内部（输入框、底行、控件尺寸与间距）。
- 新增 `res/drawable/bg_send_fab.xml`（圆形，含按下与禁用档）、`res/drawable/ic_key.xml`、模型胶囊的箭头（复用或新增）。
- 新增 `app/src/main/java/com/treelearn/app/screen/RuleEditText.java`：复用 `PaperRules` 画信纸格线。
- `app/src/main/java/com/treelearn/app/screen/HomeScreen.java`：发送控件类型与启用/着色、Key 胶囊图标、模型胶囊渲染。
- `app/src/main/java/com/treelearn/app/screen/PaperRules.java`：追加信纸格线的派生色判定（与整页纸纹同源）。
- 补充测试：`app/src/test/java/com/treelearn/app/screen/PaperRulesTest.java`。

### Out of scope

- 列顺序（003 已完成）；推荐区与历史主题区；动效；任何 token 数值与 `colors.xml`。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 视觉与控件类型改变；草稿回填抑制（渲染写入不再回调 presenter）、回车提交、点击提交、Key 守卫、模型切换入口的行为必须一分不改。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `HomeScreen` 是唯一消费者；`home_draft` 与发送控件的 id 必须保持，因为 `AppLaunchInstrumentedTest` 直接查 `R.id.home_draft`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（有可见变化）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 输入框为自绘格线、发送控件由文字按钮改为圆形图标按钮；禁用态底取 `line`。
- Authoritative code/config/migration evidence: `screen/RuleEditText.java`、`screen/PaperRules.java`、`screen_home_body.xml`。
- Documentation surfaces checked: `android/README.md` 首页一节（资源与尺寸事实段）。
- Documentation files updated: 由 006 统一回写 `android/README.md`。
- Producer/consumer/sibling/compatibility paths checked: `AppLaunchInstrumentedTest`（查 `home_draft`）、`HomePresenterTest`（草稿与提交判定）。
- Superseded phrases/examples reverse-searched: 反查 README 中关于「发送按钮为文字按钮」的描述并登记为待改写。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 无。

## Acceptance criteria

- [x] 单元测试钉住信纸格线的派生色（`PaperRules` 的写格线合成值落在原型 `--paper-write` 合成值的容差内）与线距相位，命令与证据见 TDD 记录。
- [x] 发送控件在草稿为空时禁用、有草稿时启用，且禁用不只靠颜色（`enabled` 与内容描述齐备）；`home_draft` 仍可被 `AppLaunchInstrumentedTest` 找到。
- [x] 真机截图证据：输入卡在空态与有草稿两态下的观感与原型一致（`artifacts/evidence/`）。格线周期与调色板已量化核对通过；首次复核发现的**单行草稿相位偏差已修**（RuleEditText 钉垂直顶对齐 + 新增 `aSingleRowDraftStaysInsideItsOwnGridRow` 断言），见 Verification report 的 Gaps ①。

## TDD execution record

### RED — failing test first
- Test/scenario: `PaperRulesTest` 新增的「同一条判定的两个具名入口」与写纹派生色断言（`namingTheTwoRulesDoesNotChangeWhatWasAlreadyPainted`、`theTwoRulesAreDifferentInksRatherThanOneTextureDrawnTwice`、`theWritingRuleDerivedFromTheGreenTokenCompositesToThePrototypesWritingColour`）；首次运行时 `PaperRules.writingRuleColour(int,int)` 与 `PaperRules.pageRuleColour(int,int)` 尚不存在。
- Command: JAVA_HOME=D:\Environment\Jdk\jdk21 ./gradlew --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.screen.PaperRulesTest"
- Exit code: 1
- Observed failure: `> Task :app:compileDebugUnitTestJavaWithJavac FAILED`；`PaperRulesTest.java` 的 103 / 121 / 122 / 141 / 144 行报「错误: 找不到符号」，符号为 `writingRuleColour(int,int)` 与 `pageRuleColour(int,int)`，共 **5 个错误**、10 行「找不到符号」；`BUILD FAILED in 1s`。捕获时间 **16:56:28**，早于实现写入时间 16:57:36。
- Failure reason validated: 失败在**编译期**，每条错误只指向尚不存在的两个具名入口；同一次运行的 `:app:javaPreCompileDebugUnitTest` 为 UP-TO-DATE，说明测试源集本身可用。顺序证据：测试源 16:55:47 → 本 RED 16:56:28 → 实现 16:57:36，失败时被测方法确实不存在。证据 `artifacts/evidence/T-2026-09-27-004-red.txt`（**该文件头部原误写 `generated: 17:05`，已在文末「元数据更正」段更正为 16:56:28 并留痕**）。

### GREEN — minimum implementation
- Implementation: `PaperRules` 追加两个具名入口 `pageRuleColour(background, line)` / `writingRuleColour(surface, green)` 与 `writeAlpha()`（把原先内联在 `PaperRuleBackdrop` 构造里的「谁叠在谁上面」收敛成唯一判定点）；新增 `RuleEditText`（复用 `PaperRules.lineOffsets` 与 `writingRuleColour` 自绘随内容滚动的信纸格线，行距用「按实测周期比例修正」的搜索标定）；新增 `bg_send_fab.xml`（圆形，含按下档与禁用档，禁用底取既有 `line`）、`ic_key.xml`、`ic_chevron_down.xml`、`ic_think.xml`；`view_model_chip.xml` 与 `ModelThinkingPicker` 的胶囊化；`screen_home_body.xml` 输入卡块（`home_draft` 由 `EditText` 换成 `RuleEditText`、`home_send` 换成带 `contentDescription` 的圆形图标按钮、Key 胶囊补图标）；`HomeScreen` 的发送控件启用/着色与胶囊渲染。
- Command: JAVA_HOME=D:\Environment\Jdk\jdk21 ./gradlew --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.screen.PaperRulesTest"
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-09-27-004-green.txt` —— `BUILD SUCCESSFUL in 4s`；报告 XML `TEST-com.treelearn.app.screen.PaperRulesTest.xml` 为 **tests="10" skipped="0" failures="0" errors="0"**，其中含本轮新增的 `theWritingRuleDerivedFromTheGreenTokenCompositesToThePrototypesWritingColour`、`namingTheTwoRulesDoesNotChangeWhatWasAlreadyPainted`、`theTwoRulesAreDifferentInksRatherThanOneTextureDrawnTwice`、`theWritingGridStartsOnTheFirstRowsUnderline`。**口径说明：为满足「RED 与 GREEN 必须用同一条目标测试命令」的契约，本条命令与 RED 逐字相同，因此只覆盖 `PaperRulesTest`；输入卡布局的 6 个断言与全量回归在下一行单列。**
- Regression evidence: `artifacts/evidence/T-2026-09-27-004-regression.txt` ——（a）`--tests com.treelearn.app.screen.HomeComposerLayoutTest --rerun` EXITCODE=0，报告 `tests="6" skipped="0" failures="0" errors="0"`（`theDraftFieldIsTheRuledFieldAndStartsOnTheGridsFirstRow` / `theSendControlIsACircularIconButtonThatStillSaysWhatItDoes` / `theKeyPillPairsItsIconWithItsTextAndKeepsSymmetricPadding` / `bothComposerPillsAreShapedByThePillRadius` / `theModelPillStaysSmallWhileItsHostKeepsTheTouchTarget` / `bothPaperTexturesReadTheirColourFromTheOneJudgement`）；（b）全量 `:app:testDebugUnitTest --rerun` EXITCODE=0，**78 个报告文件 / tests=671 / skipped=0 / failures=0 / errors=0**（计数由脚本遍历全部报告 XML 求和，不读 BUILD 字样；本次先删 `test-results` 再以同一命令复跑，确保是真实执行而非 up-to-date 跳过，`PaperRulesTest`=10 与 `HomeComposerLayoutTest`=6 均在列）。**数字口径更正**：本条早先写「77 个报告 / 666 例」，那是 17:24 一次运行的值；本会话 17:38 重跑后为 78/671，两个值与各自证据文件同源，引用时以本行为准。**归属注意**：77→78 份、666→671 例的增量来自 **005 在并发会话里新增的 `RecommendationTagTest`（5 例）**，所以 78/671 描述的是**当时那株工作树**，不是 004 的冻结态；004 自身冻结态计数为 77/666，两者都全绿。（c）真机 `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=HomeComposerInstrumentedTest,AppLaunchInstrumentedTest,HomeBackdropInstrumentedTest,BottomTabShellInstrumentedTest` EXITCODE=0，报告 **tests=13 failures=0 errors=0**（HomeComposer 5 / AppLaunch 1 / HomeBackdrop 2 / BottomTabShell 5），设备 `AWSS013807000356`（ALP-AN00 / Android 13）。

### REFACTOR
- 无独立重构轮。本任务的「三处同判据收敛到 `PaperRules` 一处」本身就是实现的一部分，已计入 GREEN；`RuleEditText` 的行距标定在设计时即抽成独立方法（`syncLineGrid` / `laidOutPeriod` / `paintBox`），未出现需要再抽取的重复。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（已按 TDD 执行，RED/GREEN 如上）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

### 规模记录（判定口径：非空非注释生产代码行）
量测命令：`python` 遍历文件按「非空且不以 `//` `/*` `*` `<!--` 开头」计数；既有文件取 `git diff --numstat`。实测输出留在 `/tmp/004-sizing.txt`，下列数字均可复跑。

| 组成 | 非空非注释 | 物理行 | 备注 |
| :--- | ---: | ---: | :--- |
| 新增 `RuleEditText.java`（全文） | 90 | 184 | 新增文件按全文计 |
| 新增 drawable `bg_send_fab` / `ic_key` / `ic_chevron_down` / `ic_think` | 74 | 87 | 24+18+13+19 |
| `PaperRules.java` 第 43–85 行（004 追加的具名入口块，含 `WRITE_ALPHA`） | 15 | 43 | 002 拥有该文件，此处只计本任务追加段 |
| `view_model_chip.xml`（HEAD 13 行 → 现 43 行） | +30 | — | 胶囊化 |
| `screen_home_body.xml` 输入卡块 | 约 25 | — | 与 003 共用文件，numstat 无法干净切分，取本块改动行 |
| `HomeScreen.java` 发送控件与胶囊渲染、`ModelThinkingPicker.java` | 约 30 | — | 同样与 003 共用，取本任务改动行 |
| **判定值** | **约 264** | — | 测试、证据、文档不计入 |

- **超出 `estimated_production_lines: 90` 且超过 200 软上限 → 记为 verifier 警告，不构成例外**（500 硬上限未触及）。低估原因如实记录：估算按「只改输入卡内部视觉」，实际新增了一个自绘控件（`RuleEditText` 单文件就 90 行，其中大部分是行距标定的可复现说明与搜索逻辑）与四个新矢量；这三块在初稿 Scope 里被当成「改布局」而非「写控件」。
- 本任务未触及 `colors.xml` / `tokens.json`，未新增灰色 token（禁用底取既有 `line`），与 Scope 约束一致。

## Implementation notes

- 信纸格线的线距＝原型 textarea 的行高（与 `PaperRules` 的线距档位同一处判定），相位与首行文字基线对齐；格线画在文字之下。
- 发送控件：`ImageView` 或图标化 `Button`，尺寸 `touch_min`，背景 `bg_send_fab`；禁用档实心 `line`，图标色 `surface`。M11 记录：禁用属 WCAG 1.4.3 的 inactive 组件豁免，但控件必须同时具备 `enabled=false` 与内容描述，不得仅凭颜色表达不可用。
- 模型胶囊与 Key 胶囊统一到 `radius_full`；Key 胶囊补齐钥匙图标，上下内边距对称。
- 任一尺寸改动后立刻跑 `AppLaunchInstrumentedTest` 与 `BottomTabShellInstrumentedTest`。

## Verification report

- Verdict: PENDING
- Evidence: （a）目标单测：`artifacts/evidence/T-2026-09-27-004-green.txt`，EXITCODE=0，`PaperRulesTest` tests=10 failures=0 errors=0；（b）JVM 回归：`artifacts/evidence/T-2026-09-27-004-regression.txt`，全量 `:app` 78 套件 671 例 0 失败（后续追加：单行修复后以 `--rerun` 强制重跑的全量回归见 `T-2026-09-27-004-regression-single-row.txt`）；（c）真机仪器测试：同文件第 3 节，home 相关四类 **13 例 0 失败**（`HomeComposerInstrumentedTest` 5 / `AppLaunchInstrumentedTest` 1 / `HomeBackdropInstrumentedTest` 2 / `BottomTabShellInstrumentedTest` 5），设备 `AWSS013807000356`；（d）真机两态截图：`artifacts/evidence/T-2026-09-27-004-home-empty.png`（201,879 B，17:42:31）与 `T-2026-09-27-004-home-drafted.png`（235,699 B，17:44:10）。**截图核验方式：Pillow 逐像素统计，不是目视**（当前会话无法读图）。结论：① 空态全图 `tl_green` / `tl_green_deep` / `tl_green_soft` **命中 0 像素**，发送控件为实心 `#E1DFD7`（≈`tl_line`），即禁用档成立；② 草稿态 FAB 候选区（x880–1120 × y1000–1190）主色由 `#E1DFD7`(15,131 px) 变为 `#3E6D49`(15,106 px)，中性色 `tl_surface` 精确命中 `#FCFBF8`，说明 `#3E6D49` 是 `tl_green` 在本机广色域 compositor 下的实测偏移（`tl_line`→`#E1DFD7` 只差 1/0/1 同理可证偏移只作用于饱和色），启用档成立；③ 输入框格线两态均在 y=888/992、**等距 104px**（= `space_s8` 32dp × 3.25），线宽 3px ≈ 1 CSS px，与原型 `line-height:32px` + `transparent 0 31px, --paper-write 31px 32px`（规则线在每格底部）一致；④ Key 胶囊 `tl_amber_soft` 全图只有一块 311×74px，即未配置态胶囊本身收回了原型体量
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。`home_draft` 的 id 保留、由 `EditText` 换成 `RuleEditText`（子类），`AppLaunchInstrumentedTest` 按 id 查找的断言不受影响（真机 1 例通过）；发送控件换类型后补了 `contentDescription`，并新增 `HomeComposerLayoutTest` 断言「图标按钮仍说明自己做什么」
- Gaps: ① **单行草稿的文字相位偏离原型 —— 已修（2026-09-27 晚间，用户拍板「先修 ③ 再修 ①」后就地修）**。现象（原始证据保留）：空态占位符首段下沿 861 在首格底线（y=888）之上 27px，与原型一致；填入**单行**草稿 `Learn tree pruning basics` 后段落为 `(883,909)`，**跨过 y=888 的格线**并下探 21px，即占位符与真实输入不在同一相位、格线穿字。**机理已用探针坐实，且不是当初怀疑的 `syncLineGrid()` 倍率问题**：`RuleEditText` 的 gravity 为 `8388627`（START|CENTER_VERTICAL），垂直居中把短内容推下（`padT=71`，`getBaseline()`=123，单行字形底 = 视图内 137px > 首条格线 104px）；换行态因布局高正好填满 208px 盒而无偏移，故旧测试（只用换行文本）漏过。**修复**：`RuleEditText` 构造调用新增的 `pinToTheTopOfItsBox()`，把垂直重力钉到 TOP（`(gravity & ~VERTICAL_GRAVITY_MASK) | Gravity.TOP`），文字与格线共用视图顶部锚点（原型 textarea 本就顶对齐），水平重力保持原样。**新增断言** `HomeComposerInstrumentedTest.aSingleRowDraftStaysInsideItsOwnGridRow`：用 `getBaseline()`（视图坐标，**不能用 `Layout.getLineBaseline`——那是内容坐标，不含垂直居中偏移，首次写断言就因此 RED 未红**）断言单行字形顶 ≥0 且含下伸部的字形底 ≤ `space_s8`。RED：`artifacts/evidence/T-2026-09-27-004-red-single-row.txt`（measured 137 > 104，gravity mask 16=CENTRE_VERTICAL）；GREEN：`T-2026-09-27-004-green-single-row.txt`（HomeComposer 6 例 + AppLaunch/HomeBackdrop/BottomTabShell 8 例全 OK）；回归：`T-2026-09-27-004-regression-single-row.txt`。取证用临时探针 `RuleEditTextProbeTest.java`（反射读 `multiplier`/`appliedMultiplier`/`corrections` + dump `getBaseline`/`getGravity`/`mHintLayout`）已完成使命并删除。② 自绘 `EditText` 在真机输入法下与光标/选择手柄的兼容性未经长时间试用（Implementation notes 已标为风险）。③ 本任务部分真机证据（17:38–17:44）取自一株**已被并发任务部分改写**的工作树，不是 004 的冻结态，见 Handoff watch-outs ④；修复后已补拍单行断言与全量 JVM 回归。
- Risks: 自绘格线与平台光标/选择手柄的兼容；`RuleEditText` 的行距搜索在非常规 fontScale 下只做有限步修正（`CALIBRATION_STEPS=4`），极端字号下可能停在最近的档位而非精确档（Gaps ① 的垂直居中问题已修，与此无关——那是重力，不是倍率）
- 规模: 判定值约 264 行 > 200 软上限 → 警告（见 TDD 记录的规模表），未触及 500 硬上限

## Handoff notes

- Completed: `PaperRules` 两个具名入口与 `writeAlpha()`；`RuleEditText`；四个新 drawable 与 `view_model_chip` 胶囊化；`screen_home_body.xml` 输入卡块（`home_draft` → `RuleEditText`、`home_send` 圆形图标按钮 + `contentDescription`、Key 胶囊补图标）；`HomeScreen` 与 `ModelThinkingPicker` 的渲染；`PaperRulesTest` 扩到 10 例、新增 `HomeComposerLayoutTest`（6 例）与 `HomeComposerInstrumentedTest`（真机 5 例全绿）；GREEN、回归、真机两态截图四份证据
- Completed: `PaperRules` 两个具名入口与 `writeAlpha()`；`RuleEditText`（含后续追加的 `pinToTheTopOfItsBox()` 垂直钉顶修复）；四个新 drawable 与 `view_model_chip` 胶囊化；`screen_home_body.xml` 输入卡块（`home_draft` → `RuleEditText`、`home_send` 圆形图标按钮 + `contentDescription`、Key 胶囊补图标）；`HomeScreen` 与 `ModelThinkingPicker` 的渲染；`PaperRulesTest` 扩到 10 例、新增 `HomeComposerLayoutTest`（6 例）与 `HomeComposerInstrumentedTest`（真机 6 例，含单行相位断言，全绿）；GREEN、回归、真机两态截图证据，以及 Gaps ① 修复的 RED/GREEN/回归三份追加证据
- In progress: 待 008 独立验证。Gaps ①（单行草稿相位）已收：机理为 gravity 垂直居中（非当初怀疑的 syncLineGrid 倍率），修复为钉顶，RED/GREEN 证据齐
- Blocker: （已解除）此前仓库构建失败系 `bg_unread_dot.xml` 第 3 行 XML 注释含 `--`（`8px circle in --blue`）导致 AAPT2 `:app:mergeDebugResources FAILED`；该文件属并发 005 会话，其已在 17:53:50 自改为 `the blue token`，`mergeDebugResources` 恢复 EXITCODE=0，本任务未触碰该文件
- Unblock condition: （已满足）见上——005 侧修正注释后构建恢复，004 后续真机与 JVM 证据均已在新构建下重跑通过
- Next steps: ①（原 Gaps ① 拍板项已完成）改为：008 复核时请额外看一眼 `T-2026-09-27-004-red-single-row.txt` / `-green-single-row.txt` / `-regression-single-row.txt` 三份追加证据；② 若后续在真机长时间试用中发现自绘格线与光标/选择手柄的兼容问题，按 Gaps ② 记新任务
- Watch-outs: ① 本任务的 RED 与 GREEN 目标命令只覆盖 `PaperRulesTest`（契约要求两者逐字相同），输入卡布局的 6 个断言与真机 13 例在回归证据里，复核时不要只看目标命令就判「输入卡没被测」；② RED 证据头部时间戳原写 17:05 有误，已在文末「元数据更正」段更正为 16:56:28 并留痕，引用时以后者为准；③ 规模超软上限，不得据 `estimated_production_lines: 90` 反推实际改动量；④ **本任务的真机证据（17:38–17:44）不是冻结态**：同一工作树里有并发会话从 17:35 起在改 `HomeScreen.java`(17:37:14)、`screen_home_body.xml`(17:36:56)、`RecommendationMarquee/Tag`、`item_home_topic.xml`、`strings.xml` 等 005/006 文件，设备上的 APK 构建于 17:38，已包含那些改动。输入卡本身的证据与 005 改动区域不重叠（005 动的是推荐区），但仍应在构建恢复后用干净树复跑一次；⑤ 仓库存在**两个写入者**同改一树的事实（004 的任务文件本身在本会话期间被另一写入者从 6KB 改写为 15KB 的完整版），改任务文件前先重新读盘
