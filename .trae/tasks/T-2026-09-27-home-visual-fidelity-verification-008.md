# Treelearn Harness Task: 首页视觉 1:1 还原独立验证

id: T-2026-09-27-008
title: 首页视觉 1:1 还原独立验证
template_version: 2
task_type: verification
status: ready
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

- Verdict: PENDING
- Evidence: 待执行时填写
- Changed/deleted assertion rationale and equivalent behavior coverage: 待执行时填写
- Gaps: 待执行时填写
- Risks: 待执行时填写

## Handoff notes

- Completed: 任务创建
- In progress: 未开始
- Blocker: 无
- Unblock condition: 不适用
- Next steps: 002–007 全部 verified 后执行本任务
- Watch-outs: 只读；数字必须来自当场命令输出；历史 error 与新增 error 必须分开计数
