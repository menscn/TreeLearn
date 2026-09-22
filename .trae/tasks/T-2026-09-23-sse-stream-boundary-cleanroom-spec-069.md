# Treelearn Harness Task: SSE 流解析边界 clean-room 规格

id: T-2026-09-23-069
title: SSE 流解析边界 clean-room 规格（L2）
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

- Parent summary: RikkaHub 分层吸收——L2 机制级 clean-room 重实现，本任务覆盖 SSE 流解析边界。
- Problem/background: 知芽现用 `HttpsURLConnection` + 自建 SSE 解析（`.trae/context/project-brief.md`「当前阶段与技术栈」）。RikkaHub 的流式实现经过多 Provider 实战，其边界处理（断流、不完整 chunk、错误恢复、取消）值得提炼成规格供知芽对照补齐。
- Users/scenarios: 未来实现者只读本规格，用 Java 在知芽现有 `:ai` 模块内补齐 SSE 解析边界用例。
- Expected outcome: `Docs/05-technical-design/知芽（TreeLearn）SSE流解析边界clean-room规格_V1.0.md`。
- Core flow and reversibility: 读 RikkaHub `me/rerere/ai/stream/**` 与 provider 层（只读）→ 机制提炼成中文规格（不含源代码）→ 用户 review → 后续另立编码任务。纯文档。
- Inputs/outputs: 输入 RikkaHub 源码（只读）与知芽 `:ai` 模块现状；输出一份规格文档。
- State and exception handling: 规格必须覆盖：正常事件序列、不完整/跨 chunk 事件、连接中断、服务端错误事件、用户取消、超时；每类给出期望行为。
- Constraints and trade-offs: 严格 clean-room——规格作者读源码，实现者不得读 RikkaHub 源码；规格不含源代码与逐行伪代码，只写机制、边界条件与验收用例。知芽 Java-only、`HttpsURLConnection`、DeepSeek-only 不变。
- AI recommendation and rationale: 选 SSE 边界作为 L2 首批两项之一，因其与知芽现有自建解析直接对照、收益立现（评估报告 V0.1 第三节）。
- User choice and custom additions: 用户 2026-09-23「按推荐方案」。
- AI-autonomous decisions: 规格按「输入边界 → 状态机 → 异常与恢复 → 验收用例清单」组织；用例写成可翻译成 JUnit 的行为描述。
- User-confirmation decisions: 用户 review 规格后才立编码任务。
- Acceptance evidence: 规格存在、UTF-8、无源代码、每条机制注明「提炼自 RikkaHub 何处（文件路径）」但正文表达为机制描述。
- Facts: 知芽 `:ai` 模块已有 gateway 与 prompt 包（`android/ai/src/main/java/com/treelearn/ai/` 实测）。
- Assumptions: RikkaHub 流实现覆盖的边界集合是 SSE 场景的充分近似。
- Unknowns: 知芽现有自建解析已覆盖哪些边界（需在规格中列「对照清单」，实现任务执行前核对）。
- Risks: 规格混入源码表达破坏 clean-room；规格超出知芽架构（如引入 Okhttp）导致不可实施。
- Deferred: 知芽侧实现与 RED/GREEN 证据（后续编码任务）。
- Non-goals: 不改知芽生产代码；不引入新 HTTP 客户端；不做多 Provider 抽象。
- Skipped non-critical questions and reason: 规格版本号沿用 V1.0 命名约定，无需再问。

## Goal

产出《SSE 流解析边界 clean-room 规格》：把 RikkaHub 流式实现中值得吸收的边界处理提炼为不含源代码的中文规格，供知芽 Java 实现对照。

## Scope

### In scope
- 新建 `Docs/05-technical-design/知芽（TreeLearn）SSE流解析边界clean-room规格_V1.0.md`：边界条件清单（不完整 chunk、跨包事件、注释行、多行 data、[DONE] 哨兵、连接中断、错误事件、取消、超时）、期望行为状态机、验收用例清单、与知芽现状的对照清单（哪些已覆盖/待核对）。
- 显式声明 clean-room 使用规则（实现者只读本规格）。

### Out of scope
- 知芽 `:ai` 模块任何代码修改；HTTP 客户端替换；RikkaHub 多 Provider 抽象的吸收。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增文档，不改运行行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无生产代码改动；未来消费者是实现者与编码任务。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档任务。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `D:\Project\Android\rikkahub-master/ai/src/main/java/me/rerere/ai/stream/**`、`me/rerere/ai/provider/**`（只读参考）；知芽 `.trae/context/project-brief.md`。
- Documentation surfaces checked: `Docs/05-technical-design/` 目录现状。
- Documentation files updated: 本规格本身。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 不适用。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 规格不改变 `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` 的权威地位，是其 SSE 章节的补充。

## Acceptance criteria

- [ ] 规格存在且 UTF-8、无占位符；正文不含 RikkaHub 源代码与逐行伪代码。
- [ ] 覆盖至少：不完整/跨 chunk 事件、连接中断、服务端错误事件、用户取消、超时、[DONE] 哨兵六类边界，每类有期望行为与可翻译为 JUnit 的验收用例。
- [ ] 每条机制注明提炼出处（RikkaHub 仓库内路径），并附「与知芽现状对照清单」（已覆盖/待核对/建议补齐）。
- [ ] 显式声明 clean-room 使用规则与越界后果。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（规格文档任务）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 产出《SSE 流解析边界 clean-room 规格 V1.0》
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 2026-09-23 复跑 `SUMMARY files=70 errors=0`；规格已落盘 `Docs/05-technical-design/知芽（TreeLearn）SSE流解析边界clean-room规格_V1.0.md`
- Regression evidence: 不适用（无生产代码）

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（规格文档任务）
- Reason: 规格的正确性由用户 review 与未来实现任务的 RED/GREEN 保证
- User approval: 用户 2026-09-23 明确指示按推荐方案执行（推荐方案含 L2 两份规格）
- Alternative verification: 边界覆盖面核对 + 源代码混入检查 + 用户 review

## Implementation notes

- 规格语言必须是「机制描述」，例如「事件以空行分隔；解析器须保留跨读取缓冲的不完整尾部直到下一批数据到达」，不得出现与源码同构的函数/类结构。
- 知芽 DeepSeek-only：规格只需覆盖 OpenAI 兼容 SSE（`data:` 行 + [DONE]），不必覆盖 Claude/Google 原生格式。

## Verification report

- Verdict: PASS（用户 2026-09-24 确认收口；规格已被 075 作为行为依据消费。遗留：§7「待核对」条目的知芽实现补齐——自动重试已拍板延后，其余边界项建议另立 `:ai` 任务，未立项前不阻塞本任务收口）
- Evidence: 规格存在（`Docs/05-technical-design/知芽（TreeLearn）SSE流解析边界clean-room规格_V1.0.md`，UTF-8）；覆盖 B1–B10 解析边界、E1–E6 异常/取消/超时、§5 落库时机、G1–G5 RikkaHub 未覆盖边界、§7 知芽对照清单（待核对口径显式声明）、§8 八条可翻译 JUnit 的验收用例；clean-room 使用规则置顶；无源代码引用。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 若实现者绕过规格直接读 RikkaHub 源码，clean-room 失效，此约束已写入交接备忘。

## Handoff notes

- Completed: 任务创建。
- In progress: 源码分析与规格撰写。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 用户 review 规格后，另立编码任务（届时按 AGENTS.md 确认）。
- Watch-outs: 编码任务实现者不得阅读 `D:\Project\Android\rikkahub-master/**`；规格中的「对照清单」须在实现前用知芽真实代码核对。
