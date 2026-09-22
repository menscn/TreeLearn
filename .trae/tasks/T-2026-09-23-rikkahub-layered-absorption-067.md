# Treelearn Harness Task: RikkaHub 分层吸收（父需求）

id: T-2026-09-23-067
title: RikkaHub 分层吸收（父需求）
template_version: 2
task_type: requirement
status: verified
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

- Problem/background: 用户认为 RikkaHub（`D:\Project\Android\rikkahub-master`，AGPL-3.0，Kotlin+Compose）交互与基础功能优秀，希望作为知芽基座完整吸收。评估（`Docs/05-technical-design/知芽（TreeLearn）RikkaHub开源方案吸收评估_V0.1.md`）结论：整库吸收因许可证、Java-only 约束、已交付 V1 三重冲突不可行；推荐分层吸收。
- Users/scenarios: 单人开发者，先拿到可核查的交互基准与两份 clean-room 规格，再决定是否进入编码任务。
- Expected outcome: 三份文档交付——①《RikkaHub 交互基准笔记》；②《SSE 流解析边界 clean-room 规格》；③《消息分支数据模型 clean-room 规格》。
- Core flow and reversibility: 读 RikkaHub 源码 → 产出不含源代码的中文规格/笔记 → 用户 review → 后续再立编码任务。文档产出无不可逆操作。
- Inputs/outputs: 输入 RikkaHub 源码（只读）与知芽权威文档；输出三份 Markdown 到 `Docs/**`。
- State and exception handling: 真机体验项 AI 无法执行，列「真机待办」交用户；发现与知芽约束冲突处显式标注而非自行放宽。
- Constraints and trade-offs: 知芽 Java-only、本地优先、SQLiteOpenHelper、HttpsURLConnection 不变；AGPL 立场按推荐方案默认「不接受整体开源」→ L2 只走严格 clean-room（规格不得含 RikkaHub 源代码、伪代码须改为机制描述）。
- AI recommendation and rationale: L1 全量借鉴 + L2 两项按需 clean-room + L3 不做；见评估报告 V0.1 第三节。
- User choice and custom additions: 用户 2026-09-23 指令「按照推荐方案，使用harness工程创建task，然后按照task来执行」——采纳推荐方案全文，含其默认 AGPL 立场（不接受整体开源，走严格 clean-room）。
- AI-autonomous decisions: ① L1 笔记以源码静态分析为主、真机项列为用户待办；② 规格作者（本轮 AI）读源码，未来实现者只读规格不读 RikkaHub 源码，维持 clean-room 边界；③ 文档落位 `Docs/04a-ux-cross-review/` 与 `Docs/05-technical-design/`。
- User-confirmation decisions: AGPL 立场（由「按推荐方案」追认默认项）；三份文档 review 后才进入编码任务。
- Acceptance evidence: 三份文档存在且 UTF-8、无占位符；`validate_tasks.py` errors=0；文档内每个事实可回溯到 RikkaHub 仓库内的文件路径。
- Facts: RikkaHub AGPL-3.0；app 模块 404 个 kt 文件、ai 模块 38 个（2026-09-23 实测）；知芽 207 个 Java 生产文件、5 模块（`.trae/context/project-brief.md`）。
- Assumptions: RikkaHub 本地副本为上游最近可构建快照；不联网核对其上游演进。
- Unknowns: 真机上交互的实际手感与性能（AI 静态分析无法覆盖）。
- Risks: 规格混入源码级表达导致 clean-room 失效；笔记把 RikkaHub 交互直接当知芽需求（越权改产品）。
- Deferred: L2 规格对应的编码任务、Markdown 渲染选型评估、真机体验评审。
- Non-goals: 不 fork RikkaHub、不改知芽生产代码、不引入 Kotlin、不复制 RikkaHub 像素与文案、不做多 Provider 与 MCP 等云生态功能。
- Skipped non-critical questions and reason: 三份文档的目录命名沿用仓库既有约定，无需再问。

## Goal

以父需求身份持有本需求的完整协作记录，并驱动三个文档子任务（068/069/070）交付，作为「分层吸收」的 L1+L2 阶段产物。

## Scope

### In scope
- 子任务 T-2026-09-23-rikkahub-ux-benchmark-notes-068.md、T-2026-09-23-sse-stream-boundary-cleanroom-spec-069.md、T-2026-09-23-branch-message-model-cleanroom-spec-070.md 的创建、执行与状态跟踪。
- 子任务全部落地后汇总本需求的完成状态与遗留项。

### Out of scope
- 任何生产代码修改；L3 整库基座；Markdown 渲染选型；多 Provider/MCP/云生态。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯文档需求，不改任何运行行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无生产代码改动。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 本任务与三个子任务均为文档产物，不触及运行时路径。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `D:\Project\Android\rikkahub-master/**`（只读参考）、`.trae/context/project-brief.md`、`Docs/05-technical-design/知芽（TreeLearn）RikkaHub开源方案吸收评估_V0.1.md`。
- Documentation surfaces checked: `Docs/04a-ux-cross-review/`、`Docs/05-technical-design/`。
- Documentation files updated: 由三个子任务各自声明。
- Producer/consumer/sibling/compatibility paths checked: 不适用（无代码变更）。
- Superseded phrases/examples reverse-searched: 评估报告 V0.1 的「待拍板」项在本文档标记为已由用户追认。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 评估报告 V0.1 保持 draft 状态不变，本文档为其决策落地记录。

## Acceptance criteria

- [ ] 三个子任务文件存在、`template_version: 2`、通过 `validate_tasks.py` 校验（errors=0）。
- [ ] 三个子任务的交付文档均存在且 UTF-8、无占位符、每个 RikkaHub 事实带仓库内路径出处。
- [ ] 子任务状态与本文档汇总一致；遗留项（真机待办、编码任务）显式列出。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（父需求编排任务，无可测试行为）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 创建三个子任务（068/069/070）并完成执行
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 2026-09-23 复跑 `SUMMARY files=70 errors=0`（初跑曾报 3 个 docs 任务 TDD 例外错误，已按 T-033 先例改 `tdd_mode: approved-exception` 后清零）
- Regression evidence: 不适用（无生产代码）

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（纯文档编排任务）
- Reason: 无可先行失败的测试；正确性由子任务校验与用户 review 保证
- User approval: 用户 2026-09-23 明确指示按 Harness 创建并执行任务
- Alternative verification: `validate_tasks.py` 机械校验 + 用户 review

## Implementation notes

- 本需求遵循 2026-09-21 阶段轻量化裁定：产物为给人 review 的文档，走最低检查，不要求独立 verifier 轮次与证据文件门禁。
- clean-room 边界是本需求最高优先级约束：任何写入 `Docs/**` 的内容不得包含 RikkaHub 源代码或逐行伪代码。

## Verification report

- Verdict: PASS（用户 2026-09-24 问询「067-070 做不做」＝确认收口：三份文档交付完成且已被 072/073 作为事实参照消费；遗留实现项（SSE 边界补齐、分支规格 S1-S3）转入后续任务池，不随本需求执行）
- Evidence: 子任务 068/069/070 均已交付文档并进入 `verifying`：①`Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md`；②`Docs/05-technical-design/知芽（TreeLearn）SSE流解析边界clean-room规格_V1.0.md`；③`Docs/05-technical-design/知芽（TreeLearn）消息分支数据模型clean-room规格_V1.0.md`；`validate_tasks.py` errors=0。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（无代码断言变更）。
- Gaps: 真机体验评审未执行（用户待办，见笔记第 7 节）；SSE 规格与分支规格中的「待核对」条目须在对应实现任务开工前用知芽真实代码确认。
- Risks: 实现者若绕过 clean-room 规则阅读 RikkaHub 源码，L2 产物前提失效；已置顶写入两份规格 §0 与交接备忘。

## Handoff notes

- Completed: 需求澄清与任务创建（067–070）；三个子任务执行完毕并交付三份文档（2026-09-23）。
- In progress: 用户 review 三份文档。
- Blocker: 无（真机待办由用户自行安排，不阻塞本需求收口）。
- Unblock condition: 用户对三份文档表态（采纳条目 / 修改意见 / S1–S3 增补取舍）。
- Next steps: ① 用户 review 后按反馈修订文档并视情况收口子任务；② SSE 边界与分支增补若立项，按 AGENTS.md 确认后另立编码任务（TDD required，实现者禁读 RikkaHub 源码）；③ L1 中被采纳的交互条目进入知芽 UX 文档时走 design token。
- Watch-outs: 未来编码任务的实现者不得直接阅读 RikkaHub 源码；schema 增补不得绕过 `db-migration-reviewer`；本笔记不是需求，不得未经拍板直接改知芽 UX 文档。
