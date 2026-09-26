# Treelearn Harness Task: 首页抽屉与统计热力图吸收（父需求）

id: T-2026-09-23-099
title: 首页抽屉与统计热力图吸收（父需求）
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

- Problem/background: 意图文件 `Docs/intent/INTENT-2026-09-23-home-drawer-stats-heatmap.md`（accepted，2026-09-23 用户三项拍板）：首页加抽屉承载对话快捷列表 + 统计（热力图 + 指标）+ 设置快捷入口 + 扩展位；迷你热力条外放首页顶部下方；Token 四指标另立小批（本批不做，需 usage 列迁移）。
- Users/scenarios: 单人自用；首页快速回最近对话、看学习热度、直达设置。
- Expected outcome: 5 个实现任务（100–104）+ 1 个文档回写任务（105）交付并通过 verifier；真机体验项由用户自测。
- Core flow and reversibility: 首页热力条 → 点按进抽屉 → 对话项直达主题 / 统计详情 / 设置；抽屉不承载搜索与助手切换；无记录时空态不误导。
- Inputs/outputs: 输入为本地回答记录（时间戳等既有列）与既有主题查询能力；输出为代码、资源与 PRD 边界回写。
- State and exception handling: 空态（无学习记录）与 DB 查询失败按既有数据层错误路径降级，抽屉其余功能不受影响。
- Constraints and trade-offs: Java-only、XML/View、单任务 ≤200 行；抽屉内对话列表与设置为**快捷入口**，学习库与底部标签栏保持正典唯一；热力信息放首页（激励优先）不藏进设置。
- AI recommendation and rationale: 数据口径先行（100），再自绘 View（101）、首页外放（102）、抽屉容器（103）、统计详情页（104）；V3.2 PRD「快捷 vs 正典」边界由 105 回写。已被用户采纳（intent 移交清单）。
- User choice and custom additions: 用户 2026-09-23 拍板抽屉内容（对话列表 + 设置按钮 + 统计）、热力条位置（顶部问候区下方）、Token 指标拆批。
- AI-autonomous decisions: 任务切分粒度按 intent 移交清单 5 切片加文档任务 105；100 与 103 串行（同触首页 Presenter/ViewState 面）。
- User-confirmation decisions: 无新增（intent 开放问题 1–4 为设计阶段项，随实现任务 watch-outs 跟踪：对话列表形态、热力口径、双入口呈现、热力条点击目标）。
- Acceptance evidence: 各子任务 RED/GREEN + guard + verifier；`validate_tasks.py` errors=0；PRD 回写证据。
- Facts: `TreeLearnSchema.java` 无 token/usage 列（2026-09-23 grep 核实）；V3.2 一级入口由底部标签栏承载（`Docs/03-prd-draft/V3.2-…PRD.md` 第 99 行）。
- Assumptions: 回答记录时间戳可支撑按天活跃口径（时区/当天边界设计阶段核实）。
- Unknowns: 真机观感；热力图按提问还是按回答记活跃；对话列表最近 N 的 N 值。
- Risks: 抽屉对话列表长成第二套首页（违背 V3.2 F16 修复初衷）；streak 断档算法与用户直觉错位；首页 Presenter 面被 100/103 双任务抢占。
- Deferred: Token 四指标（另立 intent，触发条件：回答记录加 usage 列的迁移设计）；Widget。
- Non-goals: 账号/同步/分享成绩单；搜索入口；助手切换；底部标签栏结构变更。
- Skipped non-critical questions and reason: 热力条点击目标（抽屉 vs 直达统计页）留实现期按设计稿定。

## Goal

以父需求身份持有本需求完整协作记录，驱动 5 个实现任务（100–104）与 1 个文档任务（105）交付首页抽屉与统计热力图。

## Scope

### In scope

- 子任务 100–105 的创建、执行与状态跟踪；验收汇总。

### Out of scope

- 生产代码直接修改（由子任务承担）；Token 用量指标；Widget。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 父需求为编排任务，不改运行行为；子任务各自声明。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 不适用（无代码改动）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯编排。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无（本任务）。
- Authoritative code/config/migration evidence: `Docs/intent/INTENT-2026-09-23-home-drawer-stats-heatmap.md`。
- Documentation surfaces checked: `Docs/intent/`、`Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md`。
- Documentation files updated: 由 105 承担 V3.2 PRD「快捷 vs 正典」边界回写。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录（105），不回改 V3.2 其它条目。

## Acceptance criteria

- [ ] 7 个子任务文件存在、`template_version: 2`、`validate_tasks.py` errors=0。
- [ ] 5 个实现任务全部 verified；Java-only guard、design token 检查证据齐备。
- [ ] 105 完成 V3.2 PRD「快捷 vs 正典」边界回写并经用户确认。
- [ ] 真机体验遗留项显式列出交用户。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（父需求编排任务，无可测试行为）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 创建 7 个子任务（100–105）并驱动执行
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: `python .trae/scripts/validate_tasks.py`（2026-09-23 复跑）输出 `SUMMARY files=105 errors=0 warnings=20`，20 条 warnings 全部为 099 之前的历史任务遗留，099–105 七件无 error 无 warning。
- Regression evidence: 不适用（无生产代码）

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（纯编排任务）
- Reason: 无可先行失败的测试；正确性由子任务校验与用户 review 保证
- User approval: 用户 2026-09-23 指令「按照 harness，拆解成完整的 task」
- Alternative verification: `validate_tasks.py` 机械校验 + 用户 review

## Implementation notes

- 调度：Gradle 同工程任务一律串行；100 与 103 触同一首页 Presenter/ViewState 面须串行；verifier 串行。本批无 schema 变更（token 指标已拆出）。
- 子 agent 分工：实现=android-dev；验收=verifier（只读独立）；文档=leader。

## Verification report

- Verdict: PENDING
- Evidence:
- Changed/deleted assertion rationale and equivalent behavior coverage:
- Gaps:
- Risks:

## Handoff notes

- Completed: 需求澄清（intent accepted，2026-09-23）；任务拆解（099–105 共 7 个任务）。
- In progress: 用户 review 拆解结果。
- Blocker: 无。
- Unblock condition: 用户确认拆解后按依赖顺序开工。
- Next steps: 100（统计查询）先行 → 101/102 → 103（与 102 串行）→ 104 → 105（PRD 回写随交付）。
- Watch-outs: 100 与 103 串行；抽屉对话列表必须是快捷入口不得复制全量列表；无 token 指标需求混入本批。
