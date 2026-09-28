# Treelearn Harness Task: 首页版式顺序与品牌锁定区

id: T-2026-09-27-003
title: 首页版式顺序与品牌锁定区
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 45
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页视觉 1:1 还原（父需求 T-2026-09-27-001）。用户已拍板：视觉 1:1、信息架构保持 V3.2、字体不打包、**版式顺序按原型**、走 Harness。
- 本任务在链路上的位置：链路第二个切片，是 004/005/006 的地基——它们都在这份已被重排的页面上继续加视觉。本任务拥有 `screen_home_body.xml` 的**结构所有权**，后续任务只填充各区块内部。
- Inputs/outputs: 输入为 `HomeViewState` 的既有相位与主题列表；输出为首页列结构的顺序、品牌锁定区的横排结构、主标语的出现条件。
- State and exception handling: 有主题（READY）时主标语不出现，品牌锁定区仍在；无主题（EMPTY）时主标语出现；加载中（LOADING）与失败（ERROR）按无主题处理出现主标语，不新增相位。
- Constraints and trade-offs: 用户裁决顺序取代 `Docs/spec/SPEC-2026-09-27-home-drawer-ux.md` 的对应条目——按「一条规则唯一正文出处」，该文件只加指针、不复制正文；不改 token 数值；品牌字走平台衬线（`android:fontFamily="serif"`），不打包字体。
- AI recommendation and rationale: 主标语的出现条件抽成 `HomeViewState` 上的一个判定方法而不是写在 `HomeScreen.render()` 里，理由同 002——View 层判定无法 JVM 回归；而「有没有主题」本来就是 ViewState 的事实，放它是唯一判定点。
- User-confirmation decisions: 版式顺序（原型）为用户 2026-09-27 明确拍板。
- Facts: 原型首页列顺序与品牌锁定区结构见 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 第 1665-1685 行（`renderHome`：`appbar` → `home-hero` → `home-input` → `renderRec()` → `home-sec`）；原型品牌锁定区为横排、幼苗用品牌色（第 147-150 行 `.hh-brand`），幼苗图标定义在第 445 行 `ISprout`（size=56，左叶 `fillOpacity=.18`、右叶 `.30`，`strokeWidth 1.7`）；`.hh-title` 的 `line-height:1.5` 在第 152 行。该顺序是原型**本轮未提交 diff** 刚改出的形态。
- Facts（执行的额外核实）: 原型第 1685 行 `const hasTopics = data.topicOrder.length > 0;` 与第 1681 行 `{!hasTopics && <div className="hh-title">…}` 证明「主标语仅在没有历史主题时出现」是同一判定；`HomePresenter.load()` 原本也自带一份 `final boolean hasTopics = !topics.isEmpty()`，且 `HomeData.recommendations(boolean hasTopics)` 的形参就叫 `hasTopics` —— 即同一条规则在本仓已有**三处**表达式，故本任务把它收敛成唯一实现。
- Risks: 顺序变更落在被仪器测试断言的区域；`appbar` 的 padding 调整可能影响顶栏观感（本任务未动 appbar）。
- Out of scope for this task: 输入卡内部视觉（004）、推荐区内部视觉（005）、历史主题区内部视觉（006）、动效（007）。

## Goal

首页在手机与平板两种配置下按原型自上而下排布（顶栏 → 品牌锁定区 → 主标语 → 输入卡 → 推荐区 → 历史主题），品牌锁定区为「幼苗 ＋ 知芽」横排居中，主标语只在该首页没有历史主题时出现。

## Scope

### In scope

- `res/layout/screen_home_body.xml`：改为顶部自然排布的滚动列（去掉把内容垂直居中的权重容器），顺序按原型；品牌锁定区改横排。
- 新增 `res/drawable/ic_brand_sprout.xml`：原型 `ISprout` 的矢量复刻（茎＋左叶低不透明度＋右叶中不透明度），颜色引用 `@color/tl_green_deep`。
- `ui/HomeViewState.java`：新增「有没有历史主题」的判定（**唯一判定点**），由 `screen/HomeScreen.java`（主标语可见性，取反）与 006（区头计数，取正）共同消费。
- `ui/HomePresenter.java`：**执行时新增的本文件改动**（原 Scope 未列出，理由见下）。它是同一条判定的既有第二、第三个表达式所在处（第 176 行的本地 `hasTopics`、第 171 行传给 `data.recommendations(...)` 的同名布尔），若不改就会与 `HomeViewState` 的新判定并存——正是 Implementation notes 禁止的「同一条规则两个入口」。改动仅是把这两处改为调用共享判定，不改变任何行为。
- `res/values/strings.xml`：主标语去掉强制换行。
- `Docs/spec/SPEC-2026-09-27-home-drawer-ux.md`：为被取代的顺序条加一句指向本规格的指针（不复制正文）。
- 新增/补充单元测试：`app/src/test/java/com/treelearn/app/ui/HomeViewStateTest.java`。

### Out of scope

- 各区块内部视觉；动效；`android/README.md`（由 006 统一回写）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 列的排布顺序与垂直定位改变；首页的输入、提交、历史主题点击、抽屉按钮行为必须一分不改。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `HomeScreen` 是唯一 inflate 者，手机与平板两份配置都 include 同一份 `screen_home_body.xml`（APK 内实测两份的 include 目标同为 `0x7f060013 layout/screen_home_body`），因此两配置不可能漂移；`HomeViewState` 的新判定被 `HomeScreen`、`HomePresenter` 与 `HomeData.recommendations(boolean)` 的调用点消费。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（有可见变化）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首页列顺序与垂直定位；主标语的出现条件从「恒显示」改为「无主题时显示」；品牌区从「Logo 在上、名称在下」改为「幼苗 ＋ 名称」横排；轮播区与输入卡的先后互换。
- Authoritative code/config/migration evidence: `screen_home_body.xml`、`ui/HomeViewState.java`、`ui/HomePresenter.java`、`screen/HomeScreen.java`、`drawable/ic_brand_sprout.xml`。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-09-27-home-drawer-ux.md`、`android/README.md`、`Docs/spec/SPEC-2026-09-27-home-visual-fidelity.md`。
- Documentation files updated: `Docs/spec/SPEC-2026-09-27-home-drawer-ux.md`（本任务改：需求规格第 7 行的顺序条与技术规格第 26 行的「推荐学习先于输入框」各加一句指针，正文不复制）；`android/README.md`（登记给 006）。
- Producer/consumer/sibling/compatibility paths checked: `HomePresenter` 的相位来源与 `HomeViewState` 的既有测试；`HomeData.recommendations(boolean hasTopics)` 端口与 `HomeWiring` 的实现。
- Superseded phrases/examples reverse-searched: 在 `Docs/spec/` 与 `android/README.md` 内反查「推荐区在输入框之上」的同义表述，确认只有 `SPEC-2026-09-27-home-drawer-ux.md` 两处需要加指针。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 不动（不涉信息架构）。

## Acceptance criteria

- [x] 单元测试钉住唯一判定 `hasTopics()`：有主题为真、无主题为假、加载中与失败态为假（故主标语在无主题与两个未就绪相位下出现）；命令与证据见 TDD 记录。
- [x] 布局顺序按原型（顶栏 → 品牌锁定区 → 主标语 → 输入卡 → 推荐区 → 历史主题），手机与平板两份配置同构（APK 内实测：两份配置 include 同一份 body；body 内主干序 PASS）。
- [x] 旧行为保持：`AppLaunchInstrumentedTest`、`BottomTabShellInstrumentedTest` 全绿；首页 id 契约（`home_draft`、`home_recent_list`）未变。

## TDD execution record

### RED — failing test first
- Test/scenario: `HomeViewStateTest` 新增两例——`onlyAReadyHomeWithTopicsHasTopics`（READY＋有主题为真；LOADING／ERROR／EMPTY／READY＋空列表为假）与 `thePhaseFollowsTheSameJudgementTheHeroReads`（`hasTopics(List)` 与 `phaseFor(List)` 同源）。
- Command: JAVA_HOME=<jdk21> ./gradlew --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.ui.HomeViewStateTest"
- Workdir: android/
- Exit code: 1
- Observed failure: `> Task :app:compileDebugUnitTestJavaWithJavac FAILED`；`app/src/test/java/com/treelearn/app/ui/HomeViewStateTest.java:120` 起报「错误: 找不到符号 / 符号: 方法 hasTopics()」，共 **10 个错误**、20 行「找不到符号」；`BUILD FAILED in 1s`。证据 `artifacts/evidence/T-2026-09-27-003-red.txt`。
- Failure reason validated: 失败在**编译期**且每条错误都只指向尚未新增的 `hasTopics(List)`／`phaseFor(List)`／`hasTopics()`；同一次运行里被改动的生产类（`HomeViewState`/`HomePresenter`/`HomeScreen`）在 RED 时尚未改动，故失败只可能来自缺失的新判定，不是环境或依赖问题。

### GREEN — minimum implementation
- Implementation: `HomeViewState` 新增 `hasTopics(List<TopicCard>)`（唯一判定）、`phaseFor(List)`（相位由同一判定派生）与实例 `hasTopics()`（相位护栏＋判定）；`HomePresenter` 的两处同判据调用点改走共享判定；`HomeScreen` 把 `home_hero_title` 的可见性接到判定；`screen_home_body.xml` 按原型重排并把品牌锁定区改横排；新增 `ic_brand_sprout.xml`；`strings.xml` 主标语去换行。
- Command: JAVA_HOME=<jdk21> ./gradlew --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.ui.HomeViewStateTest"
- Workdir: android/
- Exit code: 0
- Passing evidence: artifacts/evidence/T-2026-09-27-003-green.txt —— BUILD SUCCESSFUL；报告 tests="9" skipped="0" failures="0" errors="0"，其中含 `onlyAReadyHomeWithTopicsHasTopics` 与 `thePhaseFollowsTheSameJudgementTheHeroReads`。
- Regression evidence: artifacts/evidence/T-2026-09-27-003-regression.txt、artifacts/evidence/T-2026-09-27-003-both-configs.txt、artifacts/evidence/T-2026-09-27-003-unit-all.txt（三份均为当场捕获，各自自带命令行与 EXITCODE；明细见下表）

| 证据文件 | 命令 | 退出码 | 结果 |
| :--- | :--- | ---: | :--- |
| `T-2026-09-27-003-regression.txt` | `./gradlew --offline :app:assembleDebug :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=AppLaunchInstrumentedTest,BottomTabShellInstrumentedTest` | 0 | 真机 AWSS013807000356（ALP-AN00 / Android 13）6 用例 0 失败（AppLaunch 1 + BottomTabShell 5）；并对 APK 内编译后的 `screen_home_body.xml` 取元素出现序，期望主干序与实得主干序逐字相等——**PASS**；`home_draft`/`home_recent_list` 仍在 |
| `T-2026-09-27-003-both-configs.txt` | aapt2 直查 APK（不重跑构建） | 0 | 手机与平板的 `screen_home` include 目标同为 `0x7f060013 layout/screen_home_body` —— **PASS**；两份配置都含 `PaperRuleBackdrop` |
| `T-2026-09-27-003-unit-all.txt` | `./gradlew --offline :app:testDebugUnitTest`（全套） | 0 | 全仓 JVM 单测 **tests=656 failures=0 errors=0**，含 `HomePresenterTest` 17、`HomeWiringTest` 18、`HomeViewStateTest` 9、`PaperRulesTest` 6 |

### REFACTOR
- 无独立重构轮：本任务的收敛（三处同判据 → 一处）本身就是实现的一部分，已计入 GREEN。

### 实现期失败与修正
- 第 1 次 GREEN 尝试 **EXITCODE=1**，失败于资源阶段而非测试：`:app:parseDebugLocalResources FAILED` 与 `:app:mergeDebugResources FAILED`，aapt2 报 `ParseError ... 注释中不允许出现字符串 "--"`（`ic_brand_sprout.xml` 第 4 行）。原因是新矢量注释里写了 `--green-deep`。
- 修正：注释改述为「the prototype's green-deep」，并扫过本轮所有新增/改动 XML 确认无残留双连字符（命令：`sed 's/<!--//g; s/-->//g' <file> | grep -- "--"`，四个文件均为空）。第 2 次尝试通过，证据文件 `-003-green.txt` 的 `# note:` 行记录了这次尝试是第 2 次。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（已按 TDD 执行，RED/GREEN 如上）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

### 规模记录（判定口径：非空非注释生产代码行）
- **量法说明**：本工作区有 88 个未提交改动文件（多轮积压），`git diff --numstat` 量的是全部积压而非本次改动，故不作判定值；下面按「新增文件全文 + 既有文件本次改动行」量。
- 新增 `ic_brand_sprout.xml`：31 物理行（8 条 path）。
- `HomeViewState.java` 新增块：**20 行**（命令：`git diff -- HomeViewState.java | grep '^+' | 去空行与注释 | wc -l`）。
- `HomePresenter.java`：改写 2 行、删除 1 行。
- `HomeScreen.java`：新增 4 行（1 行字段注释 + 字段 + `findViewById` + 可见性）。
- `screen_home_body.xml`：HEAD→当前 numstat 为 +99/−132；其中 **67 行是 composer 块整体位移**（内容逐字未改，只换位置）、约 33 行删除属本任务之前的未提交积压，故本次**真正新增内容约 32 行**（hero 结构重写与 brand 锁定区横排）；删除的 `layout_weight`/`gravity="center_vertical"`/composer 的 `layout_marginTop` 共 4 行。
- `strings.xml`：1 行。
- **判定值约 60 行**（20+3+4+32+1）；即便按「新增文件全文 + 全部 churn」的最宽口径 20+3+4+31+99+1=158 行，仍低于 200 软上限。物理行参考：body 布局 221 行、新矢量 31 行。
- 与 `estimated_production_lines: 45` 的差异：实际 60 行，超出估算约 15 行，原因是原 Scope 未把 `HomePresenter` 的判据收敛算进去（见 In scope 的说明）。仍在软上限内，不构成例外。

## Implementation notes

- 布局结构：去掉 `home_primary` 的 `layout_weight` 垂直居中语义，改为顺序堆叠的滚动列；`home_recent_section` 仍是最后一节（原型把它放在推荐之后）。
- 品牌锁定区：`LinearLayout` 横排、`gravity="center"`、子项为 `ImageView`（新矢量）与 `TextView`（`android:fontFamily="serif"`、`text_brand`、`tl_ink`、粗体）。
- 新矢量的叶子不透明度照原型（左叶低、右叶中），`fillColor` 与 `strokeColor` 一律引用 `@color/tl_green_deep`，不得写死十六进制。
- 判定命名 `hasTopics()`，javadoc 写明它与原型「主标语仅在没有历史主题时出现」同源，并注明两个消费方：主标语可见性取其反、区头计数取它本身。**不得再写一个与之互为反义的第二个谓词**——本仓失败案例库记过「同一条规则两个入口会给同一输入两个结论」。
- 判定的定义式是 `hasTopics(topics) && phase == READY`：相位护栏让 LOADING/ERROR 答「否」（验收明确要求），列表判据让「READY 但列表为空」也答「否」。两者可观测结果与原型的「只看列表长度」在现有 presenter 下完全一致（LOADING 只在冷启动、topics 必为空时出现），但显式护栏不会随 presenter 变化而漂移。
- 顺序调整后立刻跑 `BottomTabShellInstrumentedTest`：**无需改写任何断言**，6 例全绿（该测试断言的是底部标签与 `home_draft`/`home_recent_list` 的存在与语义，不依赖首页内部顺序），故无「被改断言的等价覆盖」需要登记。
- 按原型补的两处 hero 视觉：`home_hero_title` 加 `fontFamily="serif"` 并把 `lineSpacingMultiplier` 由 1.25 改为 **1.5**（原型第 152 行 `.hh-title{line-height:1.5}`）；`home_hero` 的内边距由「top s2 / bottom s4」改为原型的「top s4 / bottom s6」（原型第 151 行 `.home-hero{padding:var(--s4) var(--gutter) var(--s6)}`）。原型的左右 `--gutter` 由 `home_primary` 的 `page_gutter` 承担，故 hero 不再重复加左右内边距。
- 撤掉的越界改动：曾给 `home_recommendations_section` 加过 `layout_marginTop="@dimen/space_s6"`，随后删除——输入卡与推荐区之间的间隔属 005 的区块职责，留着会让 005 落地后出现双倍间距。

## Verification report

- Verdict: PENDING（本任务证据已齐，终判由 `T-2026-09-27-home-visual-fidelity-verification-008.md` 出具；本任务不自判 verified）
- Evidence: 见 TDD 记录的 5 份证据文件，全部位于 `artifacts/evidence/`。
- Changed/deleted assertion rationale and equivalent behavior coverage: **无断言被改或删除**；`BottomTabShellInstrumentedTest` 与 `AppLaunchInstrumentedTest` 原样通过。
- Gaps: 真机截图上首屏留白是否真的缓解未确认；原型「先逛逛示例主题」链接按偏差表不做，故空态只有文案。
- Risks: 顺序变更的观感需真机确认；推荐区紧随输入卡、两者间距留白暂为 0，要等 005 落地。

## Handoff notes

- Completed: `HomeViewState` 的唯一判定（`hasTopics(List)`/`phaseFor(List)`/`hasTopics()`）+ `HomePresenter` 判据收敛 + `HomeScreen` 标题可见性 + 首页整列重排 + 品牌锁定区横排 + 新幼苗矢量 + 主标语去换行 + 规格指针 + 2 个新单测 + 5 份证据。
- In progress: 无（实现已结束，待 008 复核）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 008 出终判；② 004 起按依赖链继续，`screen_home_body.xml` 与 `HomeScreen.java` 的改动须串行。
- Watch-outs:
  - **推荐区与输入卡之间暂无间距**（本任务只负责顺序，间隔属 005 的区块职责）：005 落地 `.rec-block` 时必须自己给出上间距，否则两块会贴死。006 同理负责历史区头，本任务保留了 `home_recent_section` 原有的 `layout_marginTop=space_s6`。
  - `home_hero_title` 的可见性**只由 `HomeScreen.render()` 设置**；新增渲染分支时不得再写一份「有没有主题」的反义谓词，一律读 `state.hasTopics()`。
  - 原型的 `.hh-title` 字重是 600，Android 侧按规格矩阵未加 `textStyle`（`bold` 是 700，比原型重）；若真机观感偏轻，改动点只有这一处属性。
  - **建议追加进 `AGENTS.md` 失败案例库**（本任务实测踩到、不在现有条目内）：新增/改动 XML 时，注释里出现 `--`（如写 `--green-deep`、`--paper-rule` 这类 CSS 变量名）会让 aapt2 抛 `注释中不允许出现字符串 "--"`，构建失败在资源阶段而非编译阶段，报错信息只指向行号不指向原因。本仓原型是 CSS，抄注释时极易带出双连字符。
