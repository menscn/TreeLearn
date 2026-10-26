# Filename: T-2026-10-25-home-first-screen-100.md
# Codex Harness Task: 首页把品牌移到浮动顶栏、首屏首条标题一行、各块统一 22dp 步进（D1）

id: T-2026-10-25-100
title: 首页首屏重排：顶栏品牌 + 22sp 标题 + 22dp 步进（D1）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 D1（重要）——安卓首页可操作元素落在中线以下。
- Problem/background: 41sp 字标 + 12sp 副标题组成两行品牌块，把输入卡压到首屏中线以下；在 369×816dp 的机器上，用户第一眼看到的是身份声明而不是「可以开始问」。
- Users/scenarios: 打开应用、准备问第一个问题的人。
- Expected outcome: 品牌锁合移进悬浮顶栏（20sp 衬线字标 + 22dp 芽标，6dp 间距，行高 20sp）；页面列只留一条 22sp 衬线粗体标题（品牌绿、两行、距顶栏 26dp）；标题、输入卡、推荐区、历史区共用 22dp 步进，输入卡只带上边距。
- Core flow and reversibility: 改 `screen_home.xml`、`screen_home_body.xml`、`dimens.xml`。回退＝把品牌块放回页面列、恢复 16dp 区块间距。
- Inputs/outputs: 输入是评审板 D1 与用户「现在就改取保守值」；输出是新的首页两文件与四个尺寸 token。
- State and exception handling: 顶栏在滚动容器之上浮动（075 既有），品牌随顶栏常驻；页面列不再有 `home_hero` 容器与 `home_brand_*` id 的重复出现。
- Constraints and trade-offs: 20sp 字标比 41sp 小得多，但顶栏宽度有限；`WordmarkTextView` 的行框中心化必须保留，否则 2.858em 的 CFF 行框会把顶栏撑高。
- AI recommendation and rationale: 把「这是哪个应用」交给常驻顶栏，把首屏让给「现在可以做什么」，是这一屏唯一的优先级排序。
- User-confirmation decisions: 22 条全做；方向题（D1）现在就改，取保守值。
- AI-autonomous decisions: ① 标题沿用 `home_hero_title` 的 id 与文案，只改字号／字重／颜色／行距；② 去掉 letterSpacing 与 `text_home_sub` 的旧 12sp 语义；③ 输入卡加 22dp 上边距、不加下边距，避免出现双倍间距。
- Acceptance evidence: `ChineseSerifTitleTest` 两条（字面归属、字标行框）与 `HomeRecommendationStripTest.theStripIsSeparatedFromTheComposerByTheBoardsGap`，先红后绿。
- Facts: `screen_home.xml:75`（`home_brand_lockup`）、`:94`（`home_brand`）；`screen_home_body.xml:42`（`home_hero_title`）、`:56-59`（composer 上边距）、`:132`／`:175`（区块步进）；`dimens.xml:33`（`text_home_sub 22sp`）、`:219-224`（`text_brand_topbar` 等四项）；测试 `ChineseSerifTitleTest.java:79`、`:110`，`HomeRecommendationStripTest.java:283`。
- Assumptions: 369×816dp 设备的顶栏 52dp + 6dp 偏移不变；`WordmarkTextView` 的中心化实现已在 2026-10-22 验证。
- Unknowns: 无以机械判据衡量的「首屏露出多少」——本片按板取保守值，等待用户过目截图。
- Risks: 22sp 标题在极窄屏上两行可能与小屏键盘叠加，压缩可见推荐区；取舍是标题最多两行。
- Deferred: 推荐区与历史区的内容密度不动；不做真机截图以外的首屏量化。
- Non-goals: 不改顶栏的菜单／设置按钮；不改输入卡的内部结构；不改其它页面。
- Skipped non-critical questions and reason: 未问「22sp 会不会太大」——方向题由用户拍板取保守值，先按板落地再看截图。

## Goal

把首页首屏的第一个可操作元素从品牌块之下提到标题之下，并让各块共用同一步进。

## Scope

### In scope
- `android/app/src/main/res/layout/screen_home.xml`：顶栏加入品牌锁合。
- `android/app/src/main/res/layout/screen_home_body.xml`：删除 `home_hero` 容器，标题成为 22sp 衬线行；输入卡与两个区块改用 `home_section_gap`。
- `android/app/src/main/res/values/dimens.xml`：`text_home_sub` 22sp、`text_brand_topbar` 20sp、`brand_mark_topbar` 22dp、`brand_topbar_line_height` 20sp、`brand_topbar_gap` 6dp、`home_hero_top` 26dp、`home_section_gap` 22dp。
- `ChineseSerifTitleTest` 与 `HomeRecommendationStripTest` 的判据。

### Out of scope
- 推荐卡与历史行内容；顶栏左右按钮；Web 首页；Pad 分支（复用同一份 body）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 品牌块从页面列移动到悬浮顶栏；标题 12sp sans → 22sp serif bold；区块间距 16dp → 22dp。必须保留：菜单打开侧栏、设置进入设置页、顶栏滚动时常驻、输入卡 132dp 最小高度、`home_hero_title` 的文案与 id、`WordmarkTextView` 的行框中心化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者两个 layout 与 `dimens.xml`；消费者 `HomeScreen`（点击绑定未变）、`WordmarkTextView.java`；`layout-sw600dp` 不含首页专用分支。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 首页不再有页面级品牌块；顶栏承担字标；首屏标题升为 22sp 衬线；区块步进统一 22dp。
- Authoritative code/config/migration evidence: `screen_home.xml:75-110`；`screen_home_body.xml:36-60`、`:128-176`；`dimens.xml:33`、`:219-224`。
- Documentation surfaces checked: `.codex/rules/android.md`、`.codex/rules/multi-device.md`（顶栏在两配置下同一份）、`.codex/rules/ux-design.md`（无新 token 文件变更）。
- Documentation files updated: 无需改文档；两处 layout 头注释已记录迁移理由与旧值。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "home_hero\b\|home_brand_lockup\|home_brand_mark" android/app/src` 命中仅新位置；旧的 `home_brand_top`／`home_brand_bottom` dimen 已无消费者。
- Superseded phrases/examples reverse-searched: 旧注释「41sp 字标 + 12sp 副标题」已改写；「16dp 间距是 2026-09-28 的故意偏离」保留为历史说明，防止被当作原始值「修回去」。
- `documentation impact: none` reason and evidence: 页面内部版式，无契约变化。
- Historical/PRD handling: 不改写 2026-10-22 的品牌锁合与 2026-09-28 的间距拍板；本片是 2026-10-25 板的再上移。

## Acceptance criteria

- [x] `home_brand` 位于 `screen_home.xml` 的浮动顶栏内并继续使用随包衬线；`screen_home_body.xml` 不再有 `home_hero` 容器。
- [x] `home_hero_title` 为 22sp、衬线、粗体、品牌绿、最多两行、距顶栏 26dp。
- [x] 输入卡、推荐区、历史区都读 `home_section_gap`（22dp）；输入卡无下边距。
- [x] 字标仍以声明的行高为框、由 `WordmarkTextView` 中心化字体行框。

## TDD execution record

### RED — failing test first
- Test/scenario: 三条——衬线只属于品牌／标题／学习库标题；字标行框是声明行高且由 `WordmarkTextView` 绘制；区块步进统一读 `home_section_gap`。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.ChineseSerifTitleTest" --tests "com.treelearn.app.screen.HomeRecommendationStripTest"`
- Exit code: 1
- Observed failure: `15 tests completed, 3 failed`——`ChineseSerifTitleTest.java:48`（旧布局里 `home_brand` 不在顶栏、`home_hero_title` 不是衬线）、`HomeRecommendationStripTest.java:287`（区块仍读 `space_s4`）（`100-red.txt`）。
- Failure reason validated: 三条红分别对应「位置」「字面」「步进」；把两个 layout 与 dimen 还原到 HEAD 后同一条命令判红，恢复后转绿，说明红来自本片的三处结构改动。

### GREEN — minimum implementation
- Implementation: 顶栏加入品牌锁合；页面列收成一条 22sp 衬线标题；三处 22dp 步进；七个 dimen 新增／改值。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.ChineseSerifTitleTest" --tests "com.treelearn.app.screen.HomeRecommendationStripTest"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`、`EXITCODE=0`（`100-green.txt`）。
- Regression evidence: Android 全量 109 套 / 954 用例绿；`ChineseSerifTitleTest` 的字体与许可两条、`HomeRecommendationStripTest` 的其余九条保持绿。

### REFACTOR — optional
- Cleanup: 删除 `home_hero` 容器与旧 `home_brand_top`／`home_brand_bottom` 的消费；标题不再需要 `letterSpacing`。
- Regression evidence: 同一条命令与全量套件保持绿。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 三条各红一次。
- Stop condition hit: 15 条全绿。
- Handoff: 等待用户过目 105 阶段的首页截图。

### TDD or sizing exception — only with prior user approval
- Exception type: 方向题例外（无机械判决）
- Reason: 「首屏露出多少可操作元素」没有可自动判定的阈值；本片按评审板取值（22sp／26dp／22dp），而不是自造一个「首屏覆盖率」判据。
- User approval: 用户已拍板「D1 现在就改，取保守值」（2026-10-25）。
- Alternative verification: 真机截图由用户过目；本卡在用户确认前保持 `verifying`，不标 PASS。

## Implementation notes

- 顶栏 20sp 是上限：再大会把 52dp 胶囊撑高，滚动时常驻的条也会压住内容。
- 标题保留 `home_hero_title` 的 id 与文案，是为了让既有的无障碍名与测试继续成立；改的是等级与字面，不是身份。
- 输入卡只加上边距：上下都加会让 22dp 变成 44dp 的视觉间隔，板上的步进是单侧的。

## Verification report

- Verdict: IMPLEMENTED — 等待用户过目截图（方向题，不标 PASS）
- Evidence: `100-red.txt`（3 failed / 15 / EXITCODE=1）、`100-green.txt`（BUILD SUCCESSFUL / EXITCODE=0）；Android 全量绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除 `home_hero` 容器相关断言；等价覆盖是「品牌在顶栏」「标题是唯一大衬线」两条新判据。
- Gaps: 没有机械判据衡量首屏可操作元素的露出量；真机截图尚未由用户过目。
- Risks: 22sp 两行标题在窄屏可能与键盘叠加，压缩推荐区可见行数。

## Handoff notes

- Completed: 顶栏品牌、22sp 标题、22dp 步进。
- In progress: 等待用户对 D1 截图方向的确认。
- Blocker: 用户过目（方向题例外）。
- Unblock condition: 用户确认截图方向后，把状态翻 `verified` 并补一次 `metrics_report.py --write`。
- Next steps: 105 的统一截图。
- Watch-outs: 不要把 16dp 当成「原始值」修回去——它是 2026-09-28 的故意偏离。
