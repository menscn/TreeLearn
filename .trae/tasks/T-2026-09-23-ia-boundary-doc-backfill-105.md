# Treelearn Harness Task: V3.2 PRD 快捷/正典边界回写

id: T-2026-09-23-105
title: V3.2 PRD 快捷/正典边界回写
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-23-home-drawer-stats-heatmap-099.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-23-home-drawer-container-106.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 首页抽屉与统计热力图吸收；本任务把「抽屉快捷入口 vs 学习库/标签栏正典」的 IA 边界增量回写进 V3.2 PRD。
- Problem/background: 抽屉承载对话列表与设置入口与 V3.2 F16（一级入口由标签栏承载、消除双主页）存在张力；intent 风险区明确要求交付后按 documentation.md 回写显式边界。
- Users/scenarios: 未来会话/verifier 引用 PRD 时不误判抽屉为第二套首页。
- Expected outcome: V3.2 PRD 增加一条增量说明：抽屉内对话列表与设置为**快捷入口**；学习库保持主题列表与搜索正典唯一；底部标签栏三 tab 结构不变。
- Core flow and reversibility: 纯文档增量，可回滚（git）。
- Inputs/outputs: 输入 intent 风险区与 103 交付事实；输出 PRD 增量段落。
- State and exception handling: 不适用。
- Constraints and trade-offs: 只增量补充，不回改 V3.2 其它条目；一条边界一个正文（PRD 指向，抽屉代码注释不复制正文）。
- AI recommendation and rationale: 边界写在 F16 条目邻近位置并标注 2026-09-23 决策来源。
- User choice and custom additions: 无。
- AI-autonomous decisions: 无。
- User-confirmation decisions: 回写后由用户 review 确认。
- Acceptance evidence: PRD diff + 用户确认记录。
- Facts: intent 文件风险区原文；PRD 第 99 行（F16 相关）。
- Assumptions: 103 verified（快捷入口语义已落地）。
- Unknowns: 无。
- Risks: 两份正文漂移（PRD 与代码注释重复写边界）。
- Deferred: Token 指标小批的 PRD 条目（待该 intent 成立）。
- Non-goals: 修改 F16 原条目语义；回写 V3.1。
- Skipped non-critical questions and reason: 无。

## Goal

把「抽屉快捷入口、学习库/标签栏正典唯一」的边界增量写入 V3.2 PRD，消除与 F16 的解读冲突。

## Scope

### In scope

- V3.2 PRD 增量段落（F16 邻近）、来源标注、用户 review。

### Out of scope

- 代码注释体系调整；V3.1 及更早版本。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 无运行行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 不适用。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: PRD 新增 IA 边界条目。
- Authoritative code/config/migration evidence: 103 的实现事实（快捷列表复用学习库查询的 grep 证据）。
- Documentation surfaces checked: V3.2 PRD F16 条目及第 99 行。
- Documentation files updated: `Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md`。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 「首页没有历史主题入口」按增量口径补注例外（抽屉快捷列表）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量不回改。

## Acceptance criteria

- [ ] PRD 增量段落存在且只指向唯一正文，与代码注释不重复。
- [ ] 103 的快捷入口事实（复用学习库查询）在段落中有证据引用。
- [ ] 用户 review 确认记录。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（文档任务）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: PRD 增量段落
- Command: `git diff --stat HEAD -- Docs/03-prd-draft/`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-105-ia-boundary-backfill.txt`（CMD-1 至 CMD-8，命令+退出码内嵌）：①PRD diff 纯增量 +15/-1（唯一修改行为 §7.2 核对表行，未回改 F16 原条目）；②增量段落落位 6.2.16 尾部（第 726 行，6.2.17 之前）；③版本修改记录表新增 V3.2 行（第 1038 行）；④「快捷 vs 正典」正文仅在文档面（PRD/intent），生产代码零复制（grep 0 命中）；⑤代码侧仅留指向注释（HomeDrawerScreen.java 第 28 行）；⑥指针注释加入后 `:app:compileDebugJavaWithJavac` BUILD SUCCESSFUL。
- Regression evidence: 不适用

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（纯文档）
- Reason: 无可测试行为
- User approval: 用户 2026-09-23 指令「按照 harness，拆解成完整的 task」
- Alternative verification: 用户 review + diff 证据

## Implementation notes

- 边界正文只写 PRD 一处；代码侧仅以注释指向 PRD。

## Verification report

- Verdict: PASS
- Evidence: `artifacts/evidence/T-105-ia-boundary-backfill.txt` CMD-1 至 CMD-8（全部实时执行、退出码内嵌）：PRD 纯增量 +15/-1、增量段落落位正确、版本表 V3.2 行就位、边界正文单源（代码零复制）、指向注释不破坏编译。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（docs 任务，无测试断言）；唯一修改的既有行是 §7.2 核对表行——原「首页不重复承载主题列表（F16）」语义原样保留，仅追加「抽屉为快捷入口层，正典不变」的事实性补充。
- Gaps: 版本表 V3.2 行状态为「待评审」，需用户 review 后定稿；§6.2.16 增量段落同属待评审正文。
- Risks: 最大风险是边界正文被复制进代码注释形成第二份正文——由「一条边界一个正文」守门（CMD-4/5/6 三段 grep + 指针注释）拦住；其次 intent 文档里的缓解口径与 PRD 增量并存（两处均为文档层、语义一致，PRD 为权威出处，intent 是其来源记录）。

## Handoff notes

- Completed: V3.2 PRD 三处回写全部落地（§6.2.16 增量段、§7.2 核对表行、版本修改记录表行）并 verified；代码侧补 PRD 指向注释；INTENT-2026-09-23 的交付后回写义务闭环。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 主线 T-095（语音输入）→ T-017（平板 Canvas）→ 父需求收口（078/034/059/049/001）→ T-019 终验。
- Watch-outs: (1) 不得回改 F16 原条目语义（已守：diff 纯增量）；(2) 不得复制第二份边界正文到代码注释（已守：CMD-5 零命中，新增抽屉相关代码时同样只写指向）；(3) V3.2 行「待评审」状态待用户定稿后更新。
