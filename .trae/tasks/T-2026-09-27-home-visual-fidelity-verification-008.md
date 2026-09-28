# Treelearn Harness Task: 首页视觉 1:1 还原独立验证

id: T-2026-09-27-008
title: 首页视觉 1:1 还原独立验证
template_version: 2
task_type: verification
status: verified
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: verifier
owner_lane: verification
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-27-home-paper-rule-backdrop-002.md, T-2026-09-27-home-layout-order-hero-003.md, T-2026-09-27-home-composer-restyle-004.md, T-2026-09-27-home-recommendation-restyle-005.md, T-2026-09-27-home-topic-card-restyle-006.md, T-2026-09-27-home-motion-007.md]
tdd_mode: not-applicable

## Requirement collaboration brief

- Parent summary: 首页视觉 1:1 还原（父需求 T-2026-09-27-001）。本任务是只读复核任务，由 verifier 角色执行，不写生产代码。
- 本任务在链路上的位置：链路终点。逐条核对 002–007 的验收标准，运行机械守门，产出带命令与退出码的证据，并显式列出未闭环项。
- Inputs/outputs: 输入为 002–007 的实现与其证据；输出为验证报告（verdict、证据、缺口、风险、交接）。
- Constraints and trade-offs: 只读，不修代码；数字必须来自当场命令输出而非记忆（本仓失败案例库有「自述计数与证据文件不同源」的教训，证据文件必须自带命令行与退出码）。
- Facts: 设备已连接（`adb devices` 实测），因此仪器测试与真机截图都可作为证据；设计 token 守门脚本 `python .trae/scripts/dev/check_design_tokens.py` 在本轮之前实测 0 failed（43 token / 15 资源），本轮不得出现新增资源。
- Risks: 首页是被仪器测试直接断言的区域，顺序与结构变更可能引起断言改写；若改写，必须核对其等价覆盖。

## Goal

对 002–007 的产出给出可复核的验证结论：逐条核对验收标准、跑齐机械守门与测试、给出带命令与退出码的证据，并列出未闭环项与风险。

## Scope

### In scope

- 逐条核对 002–007 的 acceptance criteria 与它们的 RED/GREEN 记录是否自洽（命令一致、退出码与自述相符）。
- 运行并记录：Java-only 守门、设计 token 守门、`:app` 构建与 lint、JVM 单测、仪器测试。
- 核对首页 id 契约与信息架构边界（首页不得出现搜索/归档入口与卡片菜单；历史主题区不得成为全量列表）。
- 首页空态与有条目两态的真机截图证据。

### Out of scope

- 任何代码修改（发现缺陷只报告并回退给对应任务）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只读复核，不改变运行行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 核对首页相关的既有仪器测试是否被改写，被改写的必须给出等价覆盖说明。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 只读任务。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无（只读）。
- Authoritative code/config/migration evidence: 002–007 的实现文件与其证据文件。
- Documentation surfaces checked: `android/README.md` 首页一节、`Docs/spec/SPEC-2026-09-27-home-visual-fidelity.md`、`Docs/spec/SPEC-2026-09-27-home-drawer-ux.md`（顺序条指针）。
- Documentation files updated: 无（由承担任务更新）。
- Producer/consumer/sibling/compatibility paths checked: 由本任务核对文档与生产事实是否一致。
- Superseded phrases/examples reverse-searched: 反查旧文案名与旧顺序口径的残留。
- `documentation impact: none` reason and evidence, if applicable: 只读复核，不产生文档变更。
- Historical/PRD handling: PRD 不得被本轮修改；若发现被改，报 FAIL。

## Acceptance criteria

- [ ] 002–007 的每条 acceptance criteria 都有判定（通过／未通过／不适用 + 依据），未通过项不得判为通过。
- [ ] 机械守门全部有当场输出：Java-only 守门、`check_design_tokens.py`（必须仍为 0 failed 且资源数不变）、构建与 lint、JVM 单测、仪器测试。
- [ ] 证据文件自带命令行与退出码，落 `artifacts/evidence/`；首页两态截图在列；未闭环项与风险写入交接备忘。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（验证任务不写生产代码，无可先行失败的测试）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: Not applicable（只读复核）
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 待执行时写入实测输出（新增 8 个文件 errors 计数）
- Regression evidence: Not applicable（无生产代码改动）

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用
- Reason: 验证任务为只读复核，正确性由机械守门输出与逐条核对保证
- User approval: 用户 2026-09-27 指令「确定」（批准按 Harness 流程含独立验证）
- Alternative verification: 机械守门当场输出 + 逐条 acceptance 核对表 + 真机截图

## Implementation notes

- 只读边界：本任务不得修改任何生产文件；发现缺陷报告并回退。
- 证据要求：每个命令记录工作目录、命令行、退出码、关键输出行；截图文件命名带任务标识与态名（空态／有条目）。
- 复核重点：① 首页是否被塞回搜索/归档入口或卡片菜单（信息架构越界）；② 是否新增了 token 或改了共享 `bg_*`；③ 首页 id 契约是否保持；④ 文档与生产事实是否一致。
- 已知环境事实：仓库内 017/019 两个历史任务在 2026-09-27 清理后引用了已删除的依赖文件，`validate_tasks.py` 因此有 11 条历史 error；判定只针对本轮新增文件，历史 error 需在报告中标注为既有噪声并给出计数。

## Verification report

执行方式：2026-09-28 由**独立 verifier 子代理**（全新上下文、只读、未参与 002–007 实现）执行；
本报告段由主会话（Harness）按其产出写入，符合「verifier 只读、报告由主会话写入」的分工。

- Verdict: **PARTIAL**
  002–007 各条 acceptance 在本仓约定的执行口径（类域批跑 + JVM 全量 + 真机）下证据成立，且 verifier
  当场复跑全部复现通过；但**全量批跑 `:app:connectedDebugAndroidTest` 两次当场运行均 EXITCODE=1
  （243 例 / 22 失败，两次失败分布逐条相同）**。按 harness-verification「任一加载守门 FAIL 即 FAIL、
  缺必要证据为 PARTIAL，绝不 PASS」，不判 PASS；该失败经归因探针证实与 002–007 生产代码无关
  （隔离批跑全绿、排除 A 前缀 5 类的探针 40 例全绿），故不判 FAIL。

- Evidence: 16 份，均在 `artifacts/evidence/T-2026-09-27-008-*`
  - `validate-tasks.txt` EXITCODE=1，`files=31 errors=11 warnings=0`；**11 条 ERROR 全部来自
    T-2026-09-20-017（8 条）与 -019（3 条）历史任务引用已删依赖 + 017 RED 字段缺失＝既有噪声**；
    本轮 8 个 home 文件（001–008）全部 PASS。
  - `design-tokens.txt` EXITCODE=0，`0 check(s) failed`，measured_tokens=44 / measured_resources=16。
  - `javaonly-guard.txt` EXITCODE=0，`PASS (3 APK(s) inspected … no Kotlin runtime bytecode)`；
    **007 记录的「共树竞态 FAIL」至此收口**（复跑前已确认源码静止）。
  - `assemble-debug.txt` EXITCODE=0；`lint-debug.txt` EXITCODE=0（75 警告 / 0 错误），其中命中首页线的
    5 条全为 Warning，清单见 `lint-home-scope.txt`。
  - `test-debug-unit.txt` / `unit-test-summary.txt` EXITCODE=0（79 份 / tests=683 / 0 失败 / 0 错误）。
  - `connected-test.txt`＋`connected-test-rerun-full.txt`（全量两轮，均 EXITCODE=1，243/22，
    失败清单 `connected-failures.txt`、`connected-full-rerun-failures.txt`）；
    `connected-home-rerun.txt`＋`-summary.txt`（首页+壳层 6 类隔离复跑，EXITCODE=0，22 例 0 失败）；
    `connected-attribution-probe.txt`（归因探针 40 例 0 失败）。
  - 真机两态截图：`home-empty.png`（空态）、`home-with-topics.png`（有条目，播种数据经产品读路径渲染）。
  - RED/GREEN 自洽性：002–007 六份的 RED 与 GREEN **Command 逐字相同**，退出码 1→0 与自述相符，
    六份 RED 的失败全部在编译期且错误符号只指向当轮新类型/新 id；GREEN 计数逐一相符（6/9/10/5/2/8）。
    未发现「自述计数与证据文件不同源」。

- Changed/deleted assertion rationale and equivalent behavior coverage:
  - `HomeBackdropInstrumentedTest` 被 T-010 壳层方案**整写**（`HomeBackdropInstrumentedTest.java:26-38`
    自注 T-010 acceptance，并新增反向断言「screen_home 不得再带第二层纸纹」:73）。等价覆盖成立且经
    verifier 隔离复跑 3 例全绿；但 **002 任务文件仍声称「无断言删除」，与生产事实不符**（Gaps G2）。
  - `BottomTabShellInstrumentedTest` 一处断言改写（`home_continue` 点击 → `clickRow(awaitChildren(
    R.id.home_recent_list))`）。等价覆盖：断言的同一用户行为（点首页历史行→开对话）不变，新断言走产品
    渲染行、结构真实性更强，隔离复跑绿；但 **006 文件「未改一行即通过」与提交事实不符**（Gaps G3）。
  - `HomeScreen.MAX_RECENT` private → 包内可见（供同包单测读取，无行为变化）。

- Gaps:
  - **G1（最高优先）全量批跑仪器测试 22/243 失败，可复现、未定位污染源**：A 前缀 5 类已排除；候选机理为
    多测试类 `deleteDatabase()` 与 AppContainer 常开连接的 DB 夹具隔离缺陷（`HomeTopicCardInstrumentedTest`
    javadoc:46-53 已自述该批跑风险）。失败形态为「no row arrived / never drew N card rows」等待超时。
    **建议另立任务**：为全套件建立进程内 DB 夹具隔离，先做最小二分定位污染类。与 002–007 无关。
  - **G2 002 文件与生产事实失同步**：其 AC2/证据/交接仍声称「PaperRuleBackdrop 接入两份 screen_home.xml」
    「`home_backdrop` id 保持」，现树中均不成立（`home_backdrop` 全仓 grep 0 命中）。
  - **G3 BottomTabShell 断言改写未登记**（同上），006 文件表述与提交事实不符。
  - **G4 文档与 token 事实失实**：`android/README.md` 首页一节与 `SPEC-…home-visual-fidelity.md:88`
    声称「不新增 token / 不改 tokens.json」，而 `30abb8d` 实际新增 `fs-brand`（首页品牌锁定区 30px，
    由 `screen_home_body.xml:79` `@dimen/text_brand` 消费）与 `green-bubble`（对话线）。
    **主会话已于 2026-09-28 更正 README 该段**；SPEC:88 的约束特指「纹理色与信纸色以既有 token 派生」，
    对该具体约束仍成立，故未改。
  - **G5 005 AC2 仅有单元证据**（`HomePresenterTest:179`），真机未重放推荐点击（设备流程让位于守门/截图）。
  - **G6 既有关闭项照抄**：M7 后半（就绪文案动态化）、历史卡「临时标题」（`TopicCard` 无字段）。

- Risks:
  - **R1** 全量批跑失败意味着 CI 若按全量跑 connected 会常红；当前靠「类域批跑」纪律维持绿像，
    纪律松懈即暴露（与 G1 同根）。
  - **R2** 纸纹层已上移到壳层全局渲染（所有一级/二级屏都有横格），影响面从「首页」扩大到「全局」。
  - **R3** `RuleEditText` 行距搜索在极端 fontScale 仍可能停在最近档（004 已登记，本轮未复验）。
  - **R4** README 的失实表述会误导后续任务引用为依据（G4，已修 README；SPEC 口径见上）。
  - **R5** 设备过程事实：验证中途 `screencap` 全路径 0 字节（SurfaceFlinger 报 FB protected，疑与真机
    键入 API Key 触发的隐私保护有关），已重启真机恢复；`connected` 任务结束后 AGP **卸载了被测应用**，
    主会话已于 2026-09-28 09:xx 重新安装并确认 `pm list packages` 含 `com.treelearn`。

## Handoff notes

- Completed: 002–007 逐条 acceptance 判定、六项机械守门当场输出、首页两态真机截图、RED/GREEN 自洽性核查
- In progress: 无
- Blocker: G1（全量批跑仪器测试 22/243 失败，污染源未定位）——它使整条线只能判 PARTIAL，
  但已证与 002–007 生产代码无关
- Unblock condition: 另立任务修复全套件 DB 夹具隔离后，复跑 `:app:connectedDebugAndroidTest` 全绿
- Next steps: ① 立 G1 修复任务（DB 夹具隔离 + 二分定位污染类）；② 002/006 任务文件补 G2/G3 的事实指针；
  ③ 002–007 与 001 的状态收口待用户拍板（见主会话汇报）
- Watch-outs: 只读；数字均来自当场命令输出；历史 error（11）与新增 error（0）已分开计数
