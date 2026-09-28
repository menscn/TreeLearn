# Treelearn Harness Task: 首页视觉 1:1 还原（父需求）

id: T-2026-09-27-001
title: 首页视觉 1:1 还原（父需求）
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 高保真原型 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的首页视觉（纸感横格底、品牌横排锁定区、空态才显的主标语、输入卡与信纸格线、圆形发送按钮、模型与 Key 胶囊、推荐双轨卡、历史主题卡）从未落到 Android 生产界面；现网首页是通用 Material 卡面（`screen_home_body.xml` 的竖排品牌 + 文字「发送」按钮 + `bg_card` 行列表）。原型首页今天刚被改过（未提交 diff：新增品牌字号档、`.home-hero` 改为横排锁定区 + 空态标题、入门推荐拆两条轨），要还原的是这一版。
- Users/scenarios: 单人自用。用户打开 App 落在首页，看到与原型一致的纸感首页；无主题时看到主标语与起点推荐，有主题时看到历史主题卡。
- Expected outcome: 首页视觉与原型同构（见 `Docs/spec/SPEC-2026-09-27-home-visual-fidelity.md` 的还原矩阵）；信息架构保持 V3.2 不变；7 个实现任务（002–007）+ 1 个验证任务（008）交付。
- Core flow and reversibility: 首页自上而下＝顶栏（左上抽屉按钮）→ 品牌锁定区 → 主标语（仅无主题）→ 输入卡 → 推荐区 → 历史主题。撤销方式是逐任务回退（每个任务独立可回退的视觉切片），不涉及数据与状态迁移，无不可逆操作。
- Inputs/outputs: 输入为原型 HTML 的 `:root` token 与既有 Android token（`colors.xml`/`dimens.xml`）、`HomeViewState` 的既有字段；输出为布局、资源、少量 View 层 Java 与其测试。
- State and exception handling: 无主题（EMPTY）显示主标语与起点推荐；有主题（READY）隐藏主标语、显示历史主题卡与其计数；空列表时不显示计数器；材质层与装饰层必须不可点、不可聚焦，不得拦截抽屉与滚动手势。
- Constraints and trade-offs: Java-only、零 AndroidX、单任务生产行 ≤200（总计 ≤500）；不改设计 token 数值、不改 `tokens.json`/`colors.xml`；共享 drawable（`bg_card`/`bg_pill`/`bg_chip`/`bg_field`/`bg_button_primary`）本次冻结；触屏无 hover，改按压反馈；可点区域不小于 `touch_min`。
- AI recommendation and rationale: 按垂直切片切分（纸纹层 / 版式与 Hero / 输入卡 / 推荐区 / 主题卡 / 动效 / 验证），而不是按技术阶段切；纸纹基元先做，因为输入卡的信纸格线要复用它——同一个画线判定只允许一份实现（本仓失败案例库「一条规则两个入口」的教训）。
- User choice and custom additions: 用户 2026-09-27 连续拍板四项——① 视觉 1:1 但信息架构保持 V3.2（首页不加搜索/归档图标、主题卡不加改名删除菜单、主题区仍为最近若干条快捷面）；② 字体走系统 sans-serif + 平台衬线，不打包字体；③ 走 Harness 流程；④ **版式顺序按原型**（品牌 → 标语 → 输入卡 → 推荐 → 历史主题），即 `Docs/spec/SPEC-2026-09-27-home-drawer-ux.md` 的对应顺序条被取代。
- AI-autonomous decisions: ① 不新增设计 token，纸纹与原型硬编码色一律从既有 token 派生（推导落在唯一实现的注释里并标原型出处行号）；② 空态不做「先逛逛示例主题」（App 无示例数据生成器）；③ 胶囊高度按 `touch_min` 而非原型的实际像素高；④ 平板沿用既有 `content_measure` 列宽；⑤ 首页专属资源与全站共享资源并存，统一收敛记 Deferred。
- User-confirmation decisions: 上述四项拍板；本轮范围裁剪（M3 热力条、M8 全站顶栏安全区不纳入）已向用户说明并获默认；M7 的「就绪文案动态化」从中裁出（见 Deferred）。
- Acceptance evidence: 父需求层面＝7 个实现任务全部 verified + 008 验证通过 + `python .trae/scripts/validate_tasks.py` errors=0 + `Docs/spec/SPEC-2026-09-27-home-visual-fidelity.md` 自检 0 failed + 首页两态截图落 `artifacts/evidence/`。
- Facts: `python .trae/scripts/dev/check_design_tokens.py` **2026-09-27 16:33 实测 0 failed（44 token / 16 资源）**——比本任务初稿记的 43/15 各多 1，来源是原型今天新增的 `--fs-brand:30px`（原型第 54 行、`tokens.json` 第 126 行），三方仍一致；`app/build.gradle` 单测只有 JUnit（无 Robolectric），`testOptions.unitTests.returnDefaultValues = true`，因此 View 行为只能进 androidTest，可 JVM 测的判定必须抽成纯类（`ui/HeatmapLayout`、`screen/PaperRules` 这一层）；`BottomTabShellInstrumentedTest` 与 `AppLaunchInstrumentedTest` 断言 `R.id.home_draft`、`R.id.home_recent_list` 及其行子视图。
- Assumptions: 原型 `:root` 是设计事实源且本轮不改；平台衬线在各 ROM 的字形差异用户已接受。
- Unknowns: 真机观感（首屏留白是否真的缓解）；纸纹横线在低密度屏上是否读作纹理而非参考线。
- Risks: 首页是被仪器测试直接断言的区域，版式顺序与 id 变动会红；新增全屏装饰层若可点会吃掉抽屉与滚动手势；首页专属 drawable 与全站共享 drawable 双份并存，后续统一要另做收敛。
- Deferred: M7 的「就绪文案动态化」（缺默认提供商名的既有端口，需另立条目）；M3 首页迷你热力条（原型首页也没有，属新增内容）；M8 全站顶栏安全区内边距（要求所有页面统一，属另一条工作线）；首页专属与全站共享 drawable 的统一收敛。
- Non-goals: 暗色主题；新依赖与新字体包；学习库/设置/对话/画布/导航页的版式；原型的桌面舞台与 Android 框外壳；`bg_*` 共享资源的形状调整。
- Skipped non-critical questions and reason: 纸纹横线的具体不透明度不做二次确认，按既有 token 派生（`line` 一档比例 / `green` 一档比例），真机截图后再决定是否微调。

## Goal

以父需求身份持有本需求完整协作记录，驱动 7 个实现任务（002–007）与 1 个验证任务（008）把首页视觉还原为原型同构，并保持 V3.2 信息架构与既有 id 契约。

## Scope

### In scope

- 子任务 002–008 的创建、执行顺序与状态跟踪；完成后汇总验收与遗留项。
- 设计规格 `Docs/spec/SPEC-2026-09-27-home-visual-fidelity.md` 的落地核对。

### Out of scope

- 生产代码的直接修改（由子任务承担）；信息架构调整（搜索/归档/改名删除仍归学习库）；token 数值变更。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 父需求为编排任务，不改运行行为；首页 id 契约、抽屉开合、根问题提交、历史主题点击续读必须保持，由子任务各自声明。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 不适用（无代码改动）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯编排。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无（本任务）。
- Authoritative code/config/migration evidence: `Docs/spec/SPEC-2026-09-27-home-visual-fidelity.md`。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-09-27-home-drawer-ux.md`、`android/README.md`（首页一节）、`Docs/04a-ux-cross-review/知芽-TRELEARN-PRD截图对齐分析_2026-09-27.md`。
- Documentation files updated: 由 003/006 分别承担 `SPEC-2026-09-27-home-drawer-ux.md` 顺序条指针与 `android/README.md` 首页一节回写。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 「推荐区在输入框之上」这一顺序口径已在规格裁决条款中标注取代。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 不动（本轮不涉信息架构）；评审产物 M1–M11 的 7 项待拍板不因本需求关闭。

## Acceptance criteria

- [ ] 8 个子任务文件存在、`template_version: 2`、本轮新增的 8 个文件在 `python .trae/scripts/validate_tasks.py` 下 PASS（仓库既有 017/019 两个历史任务自 2026-09-27 清理后引用了已删除的依赖，会留下 11 条历史 error；判定只针对本轮新增文件，历史 error 单独计数）。
- [ ] 7 个实现任务全部 verified；`check_design_tokens.py` 保持 0 failed（本轮不新增 token）。
- [ ] 首页 id 契约（`home_draft`、`home_recent_list`）与抽屉开合、根问题提交、历史主题点击续读行为未回归。
- [ ] 首页空态与有条目两态截图落 `artifacts/evidence/`；未做项（M3/M7 后半/M8/drawable 收敛）在交接备忘中显式列出交用户。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（父需求编排任务，无可测试行为）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 创建 8 个子任务（002–008）并驱动执行
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 待执行时回填实测输出（文件清单与 errors 计数）
- Regression evidence: 不适用（无生产代码）

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用
- Reason: 父需求为纯编排任务，无可先行失败的测试；正确性由子任务校验与用户 review 保证
- User approval: 用户 2026-09-27 指令「确定」（批准按 Harness 流程拆分与执行）
- Alternative verification: `validate_tasks.py` 机械校验 + 规格自检 + 用户 review

## Implementation notes

- 调度：002 只新增文件（可与 003 并行）；003→004→005→006 串行，因为它们同改 `screen_home_body.xml` 与 `HomeScreen.java`（同一文件不并行派工）；007 依赖 006；008 最后串行执行。
- 实现分工：本轮由主会话在 android lane 内直接实现并记录 RED/GREEN。理由：改动全部在单一 lane，且设计判据（原型选择器→token 映射）来自本轮已核对的上下文，转交子 agent 需重读原型且易丢失映射精度；该偏离已在交接备忘留痕，用户可要求改为派 android-dev。
- 验证分工：008 由只读 verifier 复核，串行执行。

## Verification report

- Verdict: PENDING
- Evidence: 待 008 与父需求汇总时填写（环境、命令、期望与实测；区分单测、构建、真机交互三类证据）
- Changed/deleted assertion rationale and equivalent behavior coverage: 若版式顺序导致首页相关仪器测试断言调整，需在此记录被改断言的等价覆盖
- Gaps: 真机观感与首屏留白未经实机确认
- Risks: 见协作记录的 Risks 段

## Handoff notes

- Completed: 需求澄清与四项拍板、Stage A 规格与自检（0 failed）、任务拆分；**002 首页纸纹基元与整页背景层**（`PaperRules` 唯一判定 + `PaperRuleBackdrop` 装饰层 + 两份首页布局接入 + 单测 6 例 + 仪器测试 2 例，4 份证据落 `artifacts/evidence/`，`validate_tasks.py` PASS）
- In progress: 003–007 实现
- Blocker: 无
- Unblock condition: 不适用
- Next steps: 从 003 起按依赖链串行执行；每完成一个任务记录 RED/GREEN 并即时跑 `validate_tasks.py`
- Watch-outs:
  - 不得改 `tokens.json` 与 `colors.xml`；不得改共享 `bg_*`；装饰层必须不可点；`home_draft`/`home_recent_list` 的 id 与语义必须保持。
  - **TDD 记录必须过机械校验**（002 实测踩到，已修）：① RED 与 GREEN 的 `- Command:` 取值必须**逐字相同**，任何括注（如「（与 RED 同一条命令）」）都会触发 `RED and GREEN must use the same targeted test command`——工作目录等信息另起一行写；② 回归证据的键名必须原样写作 `- Regression evidence:`，把三条证据写成「`- Regression evidence（说明）:`」会让校验器找不到该键；③ 明细放进表格或代码块，**不要用 `- ` 续行**，否则会被解析成一堆垃圾键。改完立刻跑 `python .trae/scripts/validate_tasks.py <文件>`。
  - **线距口径已核实**：首页整页横格的周期是原型第 116 行的 32px（=`space_s8`），不是 `space_s6`；004 的输入框信纸格线要用的是另一个数——原型第 74 行 `--lh-read:28px`（阅读行高 == 信纸格距），两者不要混用。
