# Treelearn Harness Task: AI 人设——内置人设与主题级切换

id: T-2026-09-21-034
title: AI 人设——内置人设与主题级切换
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: true
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

**唯一权威是 `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`（status: accepted）**；本节只是指针与摘要，不复制正文（复制会形成「一条规则两个正文」，本仓失败案例库第一条例即为此坑）。用户自建人设另见 `Docs/intent/INTENT-2026-09-21-user-authored-persona.md`（needs-more-info，本期不做）。

- Problem/background: 用户只有一个「回答风格」的旋钮都不存在——现状可调的只有模型与思考模式，两者都不改变风格。三条可观察后果：回答太水学不到东西；被迫每次在提问里手写风格要求；手写要求污染主线笔记。
- Users/scenarios: 单人开发者的自用学习工具；同一套导师口吻在不同学习处境（赶进度 / 想被追问 / 只想有人陪着）下都不合身。
- Expected outcome: 用户能在主题内选定一种回答风格，之后新生成的回答在可观察行为上符合该风格；旧回答不被改写；不再需要在提问里手写风格要求。
- Core flow and reversibility: 主题内打开人设选择（与模型、思考模式同排）→ 选择写入该主题 → 只影响之后新生成的回答 → 下次进入同一主题选择保持。切换不改写已有回答、来源版本或创建快照，可随时再切。
- Inputs/outputs: 输入是从固定 7 档里选一档；输出是主题级人设选择，以及每条回答记录上的人设身份。
- State and exception handling: 沿用既有口径——切换只影响之后新生成的回答；人设身份随不可变回答记录落库，保证旧回答可复现。新建主题用「默认」档。
- Constraints and trade-offs: 人设只能作用于讲解方式、深度、节奏、措辞、是否主动反问、是否主动延伸；不得触及事实准确性、纠错义务与不得编造。人设是主题级设置，不影响标题、分支摘要、推荐问题等派生输出。system 消息恰好一条且位于请求第一位。Java-only，`:core` 不碰 Android UI、网络、数据库实现。权衡偏好：可复现与基线不被削弱优先，人设自由度让位于此。
- AI recommendation and rationale: 先做内置 7 档、不做自建；先把能力做在枚举与主题列上，不做自由文本提示词——枚举可版本化、可复现，自由文本会同时打破可复现与「不写提示词正文进库」两条既有约束。
- User choice and custom additions: 用户选择「内置人设先做、自建后做」，并逐条推翻了三个初始提案：作用域由全局改为**每主题**；「耐心学姐」改为**「学习修复师」**（前者在六根行为轴上与「手把手」取值相同、无法验收区分）；人设**不影响**标题等派生输出。
- AI-autonomous decisions: 六根行为轴的具体命名与取值表（起点假设、信息密度、会不会反问你、会不会催你继续、表达情绪、会不会主动延伸）；介绍页的展示形态。
- User-confirmation decisions: 7 档清单；人设跟着主题走；模型与思考模式也改为跟着主题走；介绍页入口有两处（主题内选择弹窗里的按钮 + 设置里的独立入口），且介绍页**不承担人设的选择**，只做人设的介绍、示例与将来的增删改查；新建主题默认用「默认」档；最高规则措辞为「这个需求是否让用户学得下去、学得会」。
- Acceptance evidence: 机械可验证项——主题表新增人设列后旧库可升级；人设身份进入回答记录枚举列；提示词版本未变时旧回答可复现；每个内置档在受控问题集上的行为样例表。具体用例在设计阶段定。
- Facts: 内置 7 档与七处冻结边界逐条比对全部不成立（选择≠编辑、不插入用户正文、是内置非自定义、是枚举、存的是枚举身份非正文），因此内置人设**不需要任何边界修订**。受影响的两个既有任务（`T-2026-09-20-home-settings-ui-012`、`T-2026-09-20-chat-reasoning-answer-actions-013`）状态均为 `ready`，均未开工。
- Assumptions: 「同一套导师口吻在不同学习处境下都不合身」是真实痛点。依据为用户本人自述，无频次与量化数据。
- Unknowns: 每档的具体验收用例（模型行为不能逐字断言，需沿用提示词设计的「受控问题集 + 行为样例表」模式）；介绍页里「未来承载增删改查」的界面形态。
- Risks: 人设会同时触到数据库（主题表新列）与提示词枚举（回答记录枚举列），属共享代码区，**不得与其它任务并行**；模型与思考模式「改为跟着主题走」需要修订架构方案第 136 行（原文写存 SharedPreferences，不进库），修订时机在实现任务里。
- Deferred: 用户自建人设（含增删改查）；每档的量化验收用例；最高规则「不可解除」的机械验证方式（用户决定留到设计阶段解决）。
- Non-goals: 本期不做用户自建人设；不做「用户可编辑提示词正文」；不新增服务端、账号、同步；不引入非 DeepSeek 供应商；不改写既有 `LEARNING_ANSWER` 基线正文；不允许任何人设解除最高规则。
- Skipped non-critical questions and reason: 介绍页的具体视觉稿件未定——它不影响数据结构与验收判据，留到实现任务的设计阶段。

## Goal

让用户在主题内选定一种 AI 回答风格（内置 7 档），选择随主题持久化、只影响之后新生成的回答，且人设身份随不可变回答记录落库以保证旧回答可复现。

## Scope

### In scope
- 内置 7 档人设的定义与提示词接入（枚举化，不落提示词正文）。
- 主题级人设选择：主题表新增列 + 一次数据库迁移 + 与模型、思考模式同排的选择 UI。
- 人设介绍页：主题内弹窗的入口 + 设置里的独立入口；本期只做介绍与示例，预留将来的增删改查位置。
- 最高规则「这个需求是否让用户学得下去、学得会」写入产品正文一处，并在 `AGENTS.md` 形成需求准入闸门（只指向正文，不复制）。

### Out of scope
- 用户自建人设与增删改查。
- 用户可编辑提示词正文。
- 服务端、账号、同步。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 旧回答不被改写；提示词版本验证仍然拒绝「版本合法但不属于声明用途」；system 消息恰好一条且位于第一位、当前输入位于最后。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 主题表（`TreeLearnSchema` 的 `TOPICS_DEFINITIONS`，当前 4 列）与回答记录枚举列（`AnswerRevisions.THINKING_ENABLED` 是先例）；提示词侧 `PromptSpec` / `PromptPurpose` / `DeepSeekPromptRegistry` / `DeepSeekRequestAssembler`。
- Native bridge or independent-client compatibility/update requirements: 旧库必须能 upgrade 到新 schema，且有迁移测试。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 主题表新增人设列；人设身份进入回答记录；模型与思考模式的存放位置由 SharedPreferences 改为随主题入库。
- Authoritative code/config/migration evidence: `TreeLearnSchema.java`（`TOPICS_DEFINITIONS` 第 333 行附近、`AnswerRevisions.THINKING_ENABLED` 第 119 行、类注释第 44/45 行）、migration 脚本。
- Documentation surfaces checked: `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md`（§三共同约束末条、BASE-26）、`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md`（第 63、136 行）、`Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md`（第 51 行、§3.2）、`Docs/03-prd-draft/V3.1-...PRD.md`（第 770、771 行）。
- Documentation files updated: 尚未——用户已授权「暂时不限制改」，修订由实现任务执行，本任务只登记清单。
- Producer/consumer/sibling/compatibility paths checked: 两份 intent 与 PRD 第 770/771 行不冲突（切换只影响之后新生成的回答 / 当前选择在输入框常驻可见）。
- Superseded phrases/examples reverse-searched: 需在实现任务里反向检索 BASE-23、BASE-26 与架构方案第 63/136 行的过时表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: V3.0 与 V3.1 的历史材料保持原样，只改当前态设计文档。

## Acceptance criteria

- [ ] 主题内可选定 7 档内置人设之一，选择随主题持久化，重新进入主题保持。
- [ ] 切换只影响之后新生成的回答；已有回答、来源版本与创建快照不被改写。
- [ ] 人设身份随不可变回答记录落库，且是稳定枚举。
- [ ] 旧库可迁移到新增人设列的 schema，迁移有真实引擎上的测试证据。
- [ ] 任一人设档下「纠错义务 / 不编造」不被削弱（最高规则的准入闸门成立）。
- [ ] 人设介绍页从主题内弹窗与设置两处都能进入，且介绍页**不承担**选择功能。

## TDD execution record

### RED — failing test first

- Test/scenario: 不适用（本任务是父 `requirement`，不产出代码）。
- Command: 不适用。
- Exit code: 0
- Observed failure: 不适用。
- Failure reason validated: 不适用。

### GREEN — minimum implementation

- Implementation: 不适用（子任务执行）。
- Command: 不适用。
- Exit code: 0
- Passing evidence: 不适用。
- Regression evidence: 不适用。

### REFACTOR — optional

- Cleanup: 不适用。
- Regression evidence: 不适用。

### TDD or sizing exception — only with prior user approval

- Exception type: 不适用（父 `requirement` 任务不实现代码）。
- Reason: 本任务只保存协作记录与子任务指针。
- User approval: 不适用。
- Alternative verification: 子任务各自的验收命令。

## Implementation notes

- 子任务引用本文件用描述性文件名（`T-2026-09-21-ai-persona-builtin-034.md`）。
- 共享代码区：主题表 schema + 回答记录枚举列。实现子任务不得与其它任务并行。
- 需求正文一律以 intent 文件为准；本文件若与 intent 冲突，以 intent 为准。

## Verification report

- Verdict: PENDING
- Evidence: 不适用（父任务，由子任务产出证据）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用。
- Gaps: 每档的量化验收用例尚未定义。
- Risks: 若实现阶段把「模型与思考模式改为跟着主题走」与「人设入库」混在一个任务里，会同时改动主题表与回答记录枚举列，diff 容易越线。

## Handoff notes

- Completed: 需求收敛（intent accepted）、三个待拍板项已由用户 2026-09-21 拍板。
- In progress: Design 阶段。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: `T-2026-09-21-035`（spec-author 能力）→ `T-2026-09-21-036`（人设规格，只出三节）→ 人 review 规格 → 拆实现任务。
- Watch-outs: 并发会话正在改 `.trae/tasks/**`、`Docs/06-delivery/**`、`.agents/**`；ID 032/033 已被占用。
