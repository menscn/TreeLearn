# Treelearn Harness Task: RikkaHub 设置中心五能力吸收（父需求）

id: T-2026-09-23-078
title: RikkaHub 设置中心五能力吸收（父需求）
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

- Problem/background: 用户比对 RikkaHub 设置中心后要求把五块能力全量搬入知芽（意图文件 `Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md`，accepted）：①提供商管理；②模型设置（聊天/快速/翻译分工+思考预算+聊天建议开关）；③搜索服务；④语音（TTS+语音识别）；⑤文件管理。界面交互照搬、视觉用知芽 token、底层能力必须真实（如本机系统 TTS）。
- Users/scenarios: 单人开发者即唯一用户；学习场景按用途分工选模型、朗读回答、语音输入、带附件提问、联网检索。
- Expected outcome: 四份规格（079–082）+ 十六个实现任务（083–098）全部交付并通过 verifier；真机体验项由用户自测。
- Core flow and reversibility: 设置中心 → 提供商增删改/启停 → 模型分工 → 对话使用；每块能力独立交付可独立回滚（schema 迁移除外，须前向兼容）。
- Inputs/outputs: 输入为意图文件、矩阵 V1.0（T-072）、RikkaHub 交互基准笔记（T-068）、知芽 token 体系；输出为规格与代码。
- State and exception handling: [NOKEY]/[OFF]/[PERM]/[PART] 语义沿用矩阵字典；无 Key/权限拒绝/解析失败不得出现半成品入口。
- Constraints and trade-offs: Java-only、无服务端、AGPL clean-room（规格作者可读 `D:\Project\Android\rikkahub-master`，实现者禁读）、密钥只进 Keystore、单任务 ≤200 行。
- AI recommendation and rationale: 分四份规格先行（对应五个界面，提供商与模型设置合一份），实现按「地基→两屏→接线」顺序；已被用户采纳。
- User choice and custom additions: 用户 2026-09-23 07:25 指令五块全搬、交互照搬、风格对齐知芽、能力真实；并要求按 Harness 拆解全部任务与子 agent。
- AI-autonomous decisions: 推翻矩阵 V1.0 中 4.2（多 Provider reject）、1.8（消息级翻译/TTS reject）、6.1/6.2/6.3（附件/语音 defer）四项旧决策，以本需求用户指令为准；维持 7.3/4.4/4.5/8.2 reject 与 7.4 defer 不变（用户「全部搬」明确限定为截图五块，不含主题/请求体定制/Web 端）。
- User-confirmation decisions: 协议范围（OpenAI 兼容先行 vs 同步原生协议）、思考预算映射、聊天建议开关与现有 Recommendation 的关系、搜索服务选型——在 079 规格拍板区由用户逐项拍板。
- Acceptance evidence: 四份规格存在且过 `check_spec_author_agent.py`；16 个实现任务逐个 RED/GREEN + guard + verifier；`validate_tasks.py` errors=0。
- Facts: 知芽现状见 `project-brief.md`（DeepSeek-only、Keystore、SSE 自建、七张表）；V3.0 基准「只用 DeepSeek」条款将被本需求推翻，须基线增补。
- Assumptions: 「本机搜索」= 搜索服务 API 真实可调用（Android 无系统网页搜索 API）；系统 TTS/STT 为默认语音引擎。
- Unknowns: 各提供商模型列表端点差异；Docx 解析在 Java-only 下的可行库（无新依赖约束可能需要放宽，须规格评估）。
- Risks: 基线变更未落文档就动代码；clean-room 失效；schema 迁移破坏存量数据；任务并发触发 Gradle 锁。
- Deferred: 真机体验评审；Gemini/Anthropic 原生协议（默认先 OpenAI 兼容，待拍板）；深色模式。
- Non-goals: Material You、自定义请求头/请求体、Prompt 变量、Web 端、MCP、Agent 工作区、云同步。
- Skipped non-critical questions and reason: 各规格文档命名沿用仓库既有约定。

## Goal

以父需求身份持有本需求完整协作记录，驱动 4 个规格任务（079–082）与 16 个实现任务（083–098）交付五块设置中心能力。

## Scope

### In scope

- 子任务 079–098 的创建、执行与状态跟踪；基线变更文档落位；验收汇总。

### Out of scope

- 生产代码直接修改（由子任务承担）；深色模式；Web 端；MCP。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 父需求为编排任务，不改运行行为；子任务各自声明。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 不适用（无代码改动）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯编排。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无（本任务）。
- Authoritative code/config/migration evidence: `Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md`、`Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md`、`Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md`。
- Documentation surfaces checked: `Docs/intent/`、`Docs/spec/`、`.trae/context/project-brief.md`。
- Documentation files updated: 由 079 承担基线变更（DeepSeek-only→多供应商）的增补与 `project-brief.md` 回写。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 矩阵 V1.0 第 4/6 节被推翻条目由 079 在规格中标注决策变更记录。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: V3.0/V3.1「只用 DeepSeek」为历史冻结条款，通过基线增补（V3.3）方式变更，不回改旧版。

## Acceptance criteria

- [ ] 20 个子任务文件存在、`template_version: 2`、`validate_tasks.py` errors=0。
- [ ] 四份规格过自检脚本且用户 review 拍板完成，被推翻的矩阵旧决策有书面变更记录。
- [ ] 16 个实现任务全部 verified；guard（Java-only、db-migration-reviewer、依赖方向）证据齐备。
- [ ] 真机体验遗留项显式列出交用户。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（父需求编排任务，无可测试行为）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 创建 20 个子任务（079–098）并驱动执行
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 2026-09-23 `SUMMARY files=101 errors=0`（含本批 21 个新任务；同日另有他会话新增 T-099/100/101，无编号冲突）；四份规格自检脚本逐份复跑 EXITCODE=0
- Regression evidence: 不适用（无生产代码）

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（纯文档编排任务）
- Reason: 无可先行失败的测试；正确性由子任务校验与用户 review 保证
- User approval: 用户 2026-09-23 指令「按体系中规定的 harness 工程把所有任务包括子任务拆解出来」
- Alternative verification: `validate_tasks.py` 机械校验 + 用户 review

## Implementation notes

- clean-room 最高优先级：规格任务（079–082）作者可读 RikkaHub 源码以核对交互事实；实现任务（083–098）实现者禁读 RikkaHub 源码，只读规格。
- 调度：Gradle 同工程任务一律串行；083（schema）与 085/086（:ai 网关）触共享代码区必须独立执行不与其它任务并行；verifier 串行。
- 子 agent 分工：规格=spec-author（产品决策点回 intent-capturer 视角）；实现=android-dev；schema/迁移复核=db-migration-reviewer；验收=verifier（只读独立）。

## Verification report

- Verdict: PENDING
- Evidence: <scenario, environment, command/steps, expected and observed result; distinguish unit, contract, startup, and real interaction evidence>
- Changed/deleted assertion rationale and equivalent behavior coverage:
- Gaps:
- Risks:

## Handoff notes

- Completed: 需求澄清（intent accepted）；任务拆解（079–098 共 20 个子任务）；4 个规格任务执行完毕（2026-09-23，四个 spec-author 子代理并行产出，主会话独立复检自检脚本全过）。
- In progress: 用户 review 四份规格（Docs/spec/SPEC-2026-09-23-*.md）并逐项拍板（合计 15 项拍板点，详见各规格 Doubts 节）。
- Blocker: 用户拍板（规格阶段轻流程，结果由人拍板）。
- Unblock condition: 四份规格获用户接受（或按反馈修订后接受）。
- Next steps: ① 用户逐份拍板（079 协议范围/思考预算映射/建议开关关系/回落规则；080 引擎选型等 4 项；081 云 TTS/滚动跟随/语音键位置 3 项；082 格式范围/PDFBox 依赖/大小上限/互斥 4 项）；② 拍板后实现任务按依赖链开工（先 083/084/085 基座，schema 与网关任务独立串行）。
- Watch-outs: 规格未 verified 前实现任务不得开工；实现者禁读 RikkaHub 源码；082 的 PDFBox 依赖未经用户明示批准不得动 build.gradle；矩阵 V1.0 的 4.2/1.8/6.1/6.2/6.3 旧决策已被本需求推翻。
