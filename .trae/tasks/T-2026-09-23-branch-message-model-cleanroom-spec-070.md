# Treelearn Harness Task: 消息分支数据模型 clean-room 规格

id: T-2026-09-23-070
title: 消息分支数据模型 clean-room 规格（L2）
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-23-rikkahub-layered-absorption-067.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-23-rikkahub-layered-absorption-067.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: RikkaHub 分层吸收——L2 机制级 clean-room 重实现，本任务覆盖消息分支数据模型。
- Problem/background: RikkaHub 的 Message Branching 与知芽的树状学习是同一问题域（从既有回答派生、可回溯）。知芽已有自己的七张逻辑表与 Branch/Message 模型（T-004/T-022 交付），本任务提炼 RikkaHub 已验证的回溯语义与边角处理，对照知芽模型查缺补漏，而非替换。
- Users/scenarios: 用户与实现者在 review 知芽分支模型时，用本规格对照 RikkaHub 的成熟处理；未来编码任务只读本规格。
- Expected outcome: `Docs/05-technical-design/知芽（TreeLearn）消息分支数据模型clean-room规格_V1.0.md`。
- Core flow and reversibility: 读 RikkaHub 分支相关数据层（只读）→ 提炼回溯语义、派生关系、删除/归档联动、分支切换状态 → 对照知芽七张逻辑表 → 产出规格。纯文档。
- Inputs/outputs: 输入 RikkaHub `data/db`、`data/model` 等（只读）与知芽 SQLite 建表文档；输出一份规格文档。
- State and exception handling: 规格必须覆盖：派生关系建立、回溯（回到来源）语义、分支上下文继承、删除/归档对子树的影响、并发/重复派生的防护；每类给出期望行为。
- Constraints and trade-offs: 严格 clean-room（规格作者读源码，实现者不得读 RikkaHub 源码）；知芽树状模型为主、RikkaHub 为参照——只吸收已验证的边角处理，不推翻知芽七表结构。
- AI recommendation and rationale: 分支数据模型是 RikkaHub 与知芽同题且经过实战的唯一模块，对照价值最高（评估报告 V0.1 第二节）。
- User choice and custom additions: 用户 2026-09-23「按推荐方案」。
- AI-autonomous decisions: 规格按「概念对照 → 回溯语义 → 边角处理 → 知芽查缺清单」组织；知芽七表结构不动，缺口以「建议增补」形式列出。
- User-confirmation decisions: 用户 review 规格后决定哪些建议增补进入知芽 schema（改 schema 属于后续任务，需另行确认）。
- Acceptance evidence: 规格存在、UTF-8、无源代码、每条机制注明提炼出处路径。
- Facts: 知芽已有 Branch/Message DAO 与七张逻辑表（T-004、T-022 交付记录）；RikkaHub 具备 Message Branching（其 README Features 节）。
- Assumptions: RikkaHub 分支模型的回溯语义可映射到知芽树状模型。
- Unknowns: 知芽七表对上述边角的实际覆盖度（规格中列「待核对」，实现前用真实代码核对）。
- Risks: 把 RikkaHub 线性会话的分支语义硬套知芽树状模型导致架构污染；规格混入源码表达。
- Deferred: 知芽侧 schema 增补与实现（后续任务）。
- Non-goals: 不改知芽生产代码与 migration；不推翻七表结构；不做多会话同步。
- Skipped non-critical questions and reason: 文档落位与命名沿用仓库约定，无需再问。

## Goal

产出《消息分支数据模型 clean-room 规格》：提炼 RikkaHub 分支回溯语义与边角处理为不含源代码的中文规格，对照知芽七表给出查缺补漏清单。

## Scope

### In scope
- 新建 `Docs/05-technical-design/知芽（TreeLearn）消息分支数据模型clean-room规格_V1.0.md`：概念映射（RikkaHub 会话/消息分支 ↔ 知芽主题/树/分支/消息）、回溯语义、派生关系与上下文继承、删除/归档子树联动、并发派生防护、知芽「建议增补/已覆盖/待核对」三档清单。
- 显式声明 clean-room 使用规则。

### Out of scope
- 修改知芽 schema、DAO、migration；RikkaHub 源代码搬运；多 Provider/云同步相关模型。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增文档，不改运行行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无生产代码改动。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档任务。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: RikkaHub `app/src/main/java/me/rerere/rikkahub/data/**`（只读参考）；知芽 `Docs/05-technical-design/知芽（TreeLearn）Android SQLite 建表入场确认清单_V1.0.md` 与 T-004/T-022 任务记录。
- Documentation surfaces checked: `Docs/05-technical-design/` 目录现状。
- Documentation files updated: 本规格本身。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 不适用。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 规格不改变知芽建表文档的权威地位；若建议被采纳，schema 变更必须走 `db-migration-reviewer`（后续任务义务）。

## Acceptance criteria

- [ ] 规格存在且 UTF-8、无占位符；正文不含 RikkaHub 源代码与逐行伪代码。
- [ ] 覆盖至少：派生关系建立、回溯语义、上下文继承、删除/归档子树联动、并发/重复派生防护五类，每类有期望行为描述。
- [ ] 每条机制注明提炼出处（RikkaHub 仓库内路径），并附知芽「已覆盖/待核对/建议增补」三档对照清单。
- [ ] 显式声明 clean-room 使用规则；声明知芽七表为基线、本规格只做对照与增补建议。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（规格文档任务）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 产出《消息分支数据模型 clean-room 规格 V1.0》
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 2026-09-23 复跑 `SUMMARY files=70 errors=0`；规格已落盘 `Docs/05-technical-design/知芽（TreeLearn）消息分支数据模型clean-room规格_V1.0.md`；知芽现状对照已用真实代码核对（`Branch.java`/`Message.java`/`BranchSource.java`）
- Regression evidence: 不适用（无生产代码）

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（规格文档任务）
- Reason: 规格的正确性由用户 review 与后续任务的 RED/GREEN 保证
- User approval: 用户 2026-09-23 明确指示按推荐方案执行（推荐方案含 L2 两份规格）
- Alternative verification: 覆盖面核对 + 源代码混入检查 + 用户 review

## Implementation notes

- 知芽 Branch/Message 模型的现状以 T-022 交付为准，规格撰写前先核对 `android/data` 与 `android/core` 中 Branch/Message 的真实字段，避免凭任务标题推断。
- 「建议增补」条目必须给出增补理由与对现有表的最小改动方向，不改 schema 细节（那是后续任务的事）。

## Verification report

- Verdict: PASS（用户 2026-09-24 确认收口；规格已交付并供 075 对照。遗留：S1 删除前置检查建议立项；S2/S3 随压缩/导出功能立项时一并执行）
- Evidence: 规格存在（`Docs/05-technical-design/知芽（TreeLearn）消息分支数据模型clean-room规格_V1.0.md`，UTF-8）；覆盖概念映射、RikkaHub 机制提炼 8 条、映射注意点 3 条、知芽三档对照清单（已核对 3 条/待核对 4 条/建议增补 3 条）；知芽现状已逐行核对真实代码；clean-room 使用规则置顶；无源代码引用。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 对照清单若凭记忆而非真实代码核对，会出现「已覆盖/待核对」误判。

## Handoff notes

- Completed: 任务创建。
- In progress: 源码分析与规格撰写。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 用户 review 规格后决定增补项；增补若涉及 schema，另立任务并走 `db-migration-reviewer`。
- Watch-outs: 编码任务实现者不得阅读 `D:\Project\Android\rikkahub-master/**`；schema 增补不得绕过 SQL 规则。
