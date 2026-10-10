# TreeLearn Harness Task: Harness 一次委托贯通与少打断

id: T-2026-10-08-121
title: Harness 一次委托贯通与少打断
template_version: 2
task_type: requirement
status: verified
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户反馈 Treelearn Harness 执行时常需要自己参与多轮推进，难以一次委托后连续完成。当前 `harness-run` 限制主会话只做调度，子代理重试失败后要求用户介入；澄清与父任务确认也可能形成重复审批。用户已确认以“选择一次 Harness 后尽量自主执行，只为实质决策或真实阻塞暂停”为调整方向。
- Users/scenarios: 单人开发者在已选择 Harness、需求范围明确或已有 accepted intent 时，期待 AI 自主拆分、实现、验证并集中汇报。
- Expected outcome: 保留 Harness 的质量门禁与显式选择；去掉对已确认需求/父任务的重复确认；可恢复执行故障与上下文压力由 AI 内部处理；确需用户决策或无法安全完成时才暂停。
- Core flow and reversibility: Harness 选择 → 只澄清实质未知项 → 自动形成已确认的父需求与子任务 → 按 lane 执行、运行 guard、独立验收 → 内部恢复可恢复失败并处理上下文接力 → 汇总 PASS/FAIL/PARTIAL/blocked。只改流程文档和任务编排说明，修改可逆。
- Inputs/outputs: 输入为用户需求、Harness 选择证据与已有 accepted intent；输出为 Task v2 文件、更新后的流程入口/规则/技能，以及独立验收报告。
- State and exception handling: 已确认范围进入 ready；实质问题未决时保留 clarifying 并只询问该问题；不可恢复执行或无法保持独立验收时记录 blocked，不得自称 PASS。
- Constraints and trade-offs: 保留 Task v2、TDD 约束、独立 verifier、专项 guard、证据要求、安全边界及“未验证不报完成”；不自动提交或推送。
- AI recommendation and rationale: 一次 Harness 选择应授权 AI 在用户已确认范围内自动创建父/子任务并连续执行；将 agent 分工作为优先路径而非失败即停的唯一执行路径，可以减少人工接力而不牺牲独立验收。
- User choice and custom additions: 用户确认上述目标，并于 2026-10-08 明确选择 Harness Task 流程实施。
- AI-autonomous decisions: 仅调整实现该目标所需的 Harness 入口、澄清、任务拆分及失败恢复说明；保留其它未命中的安全/专项规则。
- User-confirmation decisions: 用户已确认目标和本次 Harness Task 流程；仍需用户决定未来任何会实质改变范围、风险或验收的事项。
- Acceptance evidence: 两份本次 Task v2 文件通过定向 `validate_tasks.py`；独立 verifier 逐文件核对目标行为和保留门禁；最终按实际证据报告。
- Facts (pre-change baseline): 修改前 Treelearn `harness-run` 在代理失败后要求用户介入；Task 契约要求独立 verifier 和专项 guard；全库 validator 基线为 58 个文件、16 errors、8 warnings。
- Assumptions: 已 accepted 的 intent 或用户已明确的需求范围可以作为父 requirement 的确认来源，不必再次要求用户批准内容相同的父任务摘要。
- Unknowns: 不同 Codex 环境是否提供可用的 worker/verifier 代理能力存在差异；若无法独立验收，必须如实 blocked。
- Risks: 自动推断可能误解实质需求；通过只对实质决策暂停及保留独立 verifier 控制风险。
- Deferred: 后续用至少一个真实 Harness 请求检验中断次数和终态证据；不在本次规则改造中声称实测已达到目标。
- Non-goals: 不移除 Harness、Task v2、TDD、独立验收、guard、显式 Harness 选择；不授权 AI 擅自作出实质产品决策、自动提交或推送；不处理业务代码。
- Skipped non-critical questions and reason: 不追问具体 agent 重试次数等执行细节，由实施者在保留安全和独立验收前提下选择可验证的恢复顺序。

## Goal

让一次 Harness 选择后 AI 在已确认范围内连续推进，仅在实质决策、不可恢复阻塞或独立验收不可用时暂停。

## Scope

### In scope
- 更新 Harness 入口、澄清规则、Task 契约/拆分职责、调度及上下文交接技能，消除重复审批并明确内部恢复路径。
- 保留显式 Harness opt-in、需求确认、Task v2、独立 verifier、专项 guard、证据与安全要求。
- 同步 `.trae/README.md` 中对用户参与点的描述（仅在实际存在不一致时更新）。

### Out of scope
- 业务功能、API、数据库、前端/Android 生产代码变更。
- 删除 verifier、Task v2、TDD 或任何适用的专项 guard。
- 自动提交、推送，或无条件跳过实质决策。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 已确认需求不再要求重复批准父任务/阶段；子代理可恢复失败不再转为用户手工接力。保留实质需求澄清、lane 约束、独立 verifier 和所有适用 guard。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `AGENTS.md` → `.trae/HARNESS.md` → `.trae/skills/harness-run/SKILL.md`; 澄清由 `.trae/rules/harness-clarification.md` 定义，拆分由 `.trae/rules/harness-tasks.md` 与 `.trae/agents/requirement-decomposer.md` 执行；上下文交接由 `.trae/skills/context-handoff/SKILL.md` 执行；复核由 `.trae/rules/harness-verification.md` 约束。
- Native bridge or independent-client compatibility/update requirements: 不适用；本任务不改运行时 API 或客户端契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用；本任务明确改变 Harness 编排行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无生产运行时变更；规范性 Harness 执行说明将改变。
- Authoritative code/config/migration evidence: `.trae/rules/**` 为工程流程规范权威；`.trae/HARNESS.md` 与 `.trae/skills/harness-run/SKILL.md` 为入口/执行说明；`AGENTS.md` 为规则路由权威。
- Documentation surfaces checked: `AGENTS.md`、`.trae/HARNESS.md`、`.trae/README.md`、`.trae/rules/harness-clarification.md`、`.trae/rules/harness-tasks.md`、`.trae/agents/requirement-decomposer.md`、`.trae/skills/harness-run/SKILL.md`、`.trae/skills/context-handoff/SKILL.md`。
- Documentation files updated: `AGENTS.md`、`.trae/HARNESS.md`、`.trae/README.md`、`.trae/rules/harness-clarification.md`、`.trae/rules/harness-tasks.md`、`.trae/agents/requirement-decomposer.md`、`.trae/skills/harness-run/SKILL.md`、`.trae/skills/context-handoff/SKILL.md`、`Docs/intent/INTENT-2026-10-08-harness-autonomous-completion.md`。
- Producer/consumer/sibling/compatibility paths checked: 用户入口、澄清、父/子任务拆分、worker 派工、恢复、专项 guard、verifier 和最终汇报。
- Superseded phrases/examples reverse-searched: 全仓库 Markdown 的确切 `rg` 命令、0 命中结果与预期退出码记录在唯一子任务 `.trae/tasks/T-2026-10-08-harness-autonomy-orchestration-122.md` 的 Implementation notes 中。
- `documentation impact: none` reason and evidence, if applicable: 不适用；本任务即流程文档变更。
- Historical/PRD handling: 仅改当前规则和执行说明；历史任务、带日期的历史报告不重写。

## Acceptance criteria

- [x] 在用户已选择 Harness 且范围已确认时，任务规则允许由 AI 生成父 requirement 与子任务，不再额外要求用户批准重复的父任务摘要或普通阶段交接。
- [x] 澄清规则只把实质未知项交给用户；用户已采纳推荐后，不重复要求仅为确认摘要而再确认一次。
- [x] 调度与上下文规则规定可恢复失败/上下文压力的内部处理；不能安全继续时报告具体阻塞，不要求用户手工重派、拆任务或重启会话。
- [x] 独立 verifier、必需 guard、Task v2、证据要求与不自动提交/推送规则仍然有效；无法独立验收不得 PASS。
- [x] 入口、澄清、任务契约、拆分角色和调度技能之间没有互相矛盾的确认/停机口径；独立 verifier 按上述文件和实际 `rg` 结果报告。
- [x] 将本任务作为真实 Harness 样本记录从 opt-in 到最终报告的链路、用户确认/实质决策请求数与终态证据。
- [x] `python .trae/scripts/validate_tasks.py .trae/tasks/T-2026-10-08-harness-autonomous-completion-121.md .trae/tasks/T-2026-10-08-harness-autonomy-orchestration-122.md` 输出 `SUMMARY files=2 errors=0 warnings=0` 并退出 0。

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用；父需求记录，无生产行为实现。
- Command: 不适用
- Exit code: 0
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation
- Implementation: 见唯一子任务。
- Command: `python .trae/scripts/validate_tasks.py .trae/tasks/T-2026-10-08-harness-autonomous-completion-121.md .trae/tasks/T-2026-10-08-harness-autonomy-orchestration-122.md`
- Exit code: 0
- Passing evidence: 定向校验输出 `SUMMARY files=2 errors=0 warnings=0` 并退出 0；文档 UTF-8/替换字符/相对链接扫描覆盖 11 个文件通过；变更文件 `git diff --check` 通过。流程规则由独立 verifier 最终核对。
- Regression evidence: 全库 validator 扫描 60 个任务，16 errors、8 warnings，退出 1；两个新增任务均 PASS，错误/警告数与新增任务前 58 个文件的既有基线相同。

### REFACTOR — optional
- Cleanup: 不适用
- Regression evidence: 不适用

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用；父 requirement 不含生产实现。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 唯一实施子任务：`T-2026-10-08-harness-autonomy-orchestration-122.md`。
- 基线全局 Task 校验：58 个文件、16 errors、8 warnings；本次不修复无关旧任务。
- 真实 Harness 样本（本任务）：用户选择 Harness/确认范围后，AI 建立 accepted intent 与父/子 Task v2，实施规则、技能和入口改造，运行文档检查与独立验收；线程中实现前出现 4 条显式确认回复（“是的”“确定。执行”及两次“确定”），范围与例外确认后未再请求实质决策。该次数如实记录，不把旧流程启动确认包装成新流程的零打断证据。
- 当前运行终态：首轮独立 verifier 为 `PARTIAL`；补齐运行样本与含 `--hidden` 的权威搜索后，独立 verifier 跟进复核为 `PASS`，无剩余缺口。

## Verification report

- Verdict: PASS — first-review evidence gaps were corrected and the independent verifier confirmed no remaining issue.
- Evidence: 定向 Task v2 validator 输出 `SUMMARY files=2 errors=0 warnings=0`；11 个 Markdown 文件 UTF-8/替换字符/相对链接检查 PASS；`git diff --check` PASS；权威 `rg --hidden` 全仓库 Markdown 搜索输出 `NO_MATCHES`、预期退出码 1；独立 verifier 重跑该搜索与定向 validator 并通过。全库 validator 为 60 files / 16 errors / 8 warnings，与新增任务前 58 files 的 16/8 基线相同。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本次只变更 Harness 文档与技能，无生产代码或测试断言删除；已记录真实 Harness 链路及 4 条实现前显式确认，范围确认后无新增实质决策请求。
- Gaps: 无。
- Risks: 全库既有 16 个历史 Task 错误与 8 个软上限警告仍在；两个本次任务定向校验通过，未修改无关历史任务。

## Handoff notes

- Completed: 用户确认需求方向、Harness Task 流程及本次 docs/config TDD exception；唯一子任务完成规则/入口/上下文技能修改及文档检查，并记录真实 Harness 样本。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无；父需求与实施子任务均已独立验收并完成。
- Watch-outs: 不触碰工作区中已有的 Android/README/task/evidence 改动；不要把全局 16 条既有 Task 校验错误归到本次变更。
