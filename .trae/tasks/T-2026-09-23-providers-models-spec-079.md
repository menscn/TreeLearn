# Treelearn Harness Task: 规格｜提供商与模型设置（含基线变更）

id: T-2026-09-23-079
title: 规格｜提供商与模型设置（含基线变更）
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-23-rikkahub-settings-capabilities-078.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务产出「提供商管理 + 模型设置」两屏的需求/界面/技术规格，并承担 DeepSeek-only 基线变更记录。
- Problem/background: 知芽当前只支持 DeepSeek 单供应商单 Key（`project-brief.md`）；用户要求搬入 RikkaHub 的多提供商体系与按用途分工的模型设置。矩阵 V1.0 曾将多 Provider 判 reject（4.2），本次被用户指令推翻。
- Users/scenarios: 用户在提供商页增删改供应商（API 地址/Key/模型列表），在模型设置页指定聊天/快速/翻译模型与思考预算、开关聊天建议。
- Expected outcome: `Docs/spec/SPEC-2026-09-23-providers-and-models.md`（四节：需求规格/界面方案/技术规格/疑虑点），含基线变更记录与拍板区；过 `check_spec_author_agent.py`。
- Core flow and reversibility: 首次配置向导或设置入口 → 新建提供商（预置 OpenAI 兼容模板 + 自定义）→ 拉取模型列表 → 分工选择 → 对话页生效；删除提供商有确认，模型分工失效时回退默认并提示。
- Inputs/outputs: 输入 RikkaHub 提供商页/模型设置页交互事实（可读源码 `ui/pages/provider/`、设置项）、知芽 Keystore/SSE/派生任务现状；输出规格文档。
- State and exception handling: [NOKEY] 无可用提供商/Key 的全应用降级语义（沿用 Key 守卫，扩展为多提供商判定）；[OFF] 拉取模型列表失败可重试；[DUP] 重名提供商/模型去重。
- Constraints and trade-offs: Java-only、AGPL clean-room、密钥只进 Keystore、无自定义请求头/请求体（矩阵 4.4 维持 reject）；协议范围默认「OpenAI 兼容先行」，Gemini/Anthropic 原生协议列拍板区。
- AI recommendation and rationale: OpenAI 兼容协议先行——覆盖 DeepSeek/硅基流动/AiHubMix 等绝大多数服务，改动集中在请求基址与模型 ID；原生协议后补。
- User choice and custom additions: 用户已确认提供商+模型设置两屏全搬；细节拍板区由用户 review。
- AI-autonomous decisions: 规格按 SPEC 模板四节组织；预置提供商清单从 RikkaHub 预置列表中挑选国内可用项。
- User-confirmation decisions: ①协议范围（OpenAI 兼容先行 vs 同步原生协议）；②思考预算在 DeepSeek/OpenAI 兼容参数上的映射（reasoning_effort/max_tokens）；③聊天建议开关与现有 Recommendation（T-046 派生推荐）合并还是并存；④「快速模型」缺省回落到聊天模型的规则。
- Acceptance evidence: 规格存在、自检脚本退出码 0、四节齐全、拍板区每项附「建议+理由+代价」、基线变更段落可追回 V3.0 条款原文。
- Facts: 知芽 `:ai` gateway 现为 DeepSeek 专用（`ai/src/main/java/com/treelearn/ai/gateway/`）；Key 存 Keystore（`app/ui/KeyVault.java`）；派生任务走 PromptPurpose 契约。
- Assumptions: OpenAI 兼容 `/v1/models` 端点在目标服务可用；模型列表可缓存。
- Unknowns: RikkaHub 预置提供商的具体字段结构（规格作者读源码核对）。
- Risks: 基线变更未同步 project-brief 导致后续任务引用旧约束；规格遗漏无 Key 降级路径。
- Deferred: Gemini/Anthropic 原生协议实现（拍板后另立任务）；二维码导入导出提供商（RikkaHub 有，本期不做）。
- Non-goals: 自定义请求头/请求体、MCP、云模型路由聚合服务。
- Skipped non-knowledge questions and reason: 预置清单具体条目由规格作者静态分析决定，不逐条问用户。

## Goal

产出提供商管理与模型设置两屏的完整规格（含 DeepSeek-only→多供应商基线变更记录），供 083–090 实现任务直接引用。

## Scope

### In scope

- 提供商管理页：列表（启用开关、模型数）、新建/编辑（名称、API 基址、Key、拉取模型）、删除确认、预置模板。
- 模型设置页：聊天模型、快速模型、翻译模型选择、思考预算、聊天建议开关。
- 基线变更：V3.3 增量建议稿要点 + `project-brief.md` 回写清单 + 矩阵旧决策（4.2/1.8）推翻记录。
- [NOKEY]/[OFF]/[DUP] 异常语义与回退规则。

### Out of scope

- 代码实现；Gemini/Anthropic 原生协议；二维码导入导出；搜索/语音/文件管理（另三份规格）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯文档；不改运行行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无代码改动。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 文档任务。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无（本任务）。
- Authoritative code/config/migration evidence: `ai/src/main/java/com/treelearn/ai/gateway/`、`app/ui/KeyVault.java`、`app/ui/SettingsStore.java`、`Docs/01-demand/`V3.0/V3.1。
- Documentation surfaces checked: `Docs/spec/`、`Docs/01-demand/`、`.trae/context/project-brief.md`。
- Documentation files updated: 新建 SPEC；`project-brief.md` 的「只用 DeepSeek」表述更新为多供应商（在规格 verified 后执行回写，避免规格被否决时文档先行）。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 「只用 DeepSeek」「DeepSeek Key」在 brief 与 PRD 的出现位置，逐处标注变更。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不回改 V3.0/V3.1 原文，以增量文档记录。

## Acceptance criteria

- [ ] SPEC 存在且 `python .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-23-providers-and-models.md` 退出码 0。
- [ ] 四节齐全；拍板区 4 项每项附建议+理由+代价；每条 RikkaHub 事实带仓库内路径出处。
- [ ] 基线变更段落明确「旧条款→新条款」及 project-brief 回写清单。
- [ ] 用户 review 拍板完成并记录。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（文档任务）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 由 spec-author 子代理（agent-ab49f478）产出 `Docs/spec/SPEC-2026-09-23-providers-and-models.md` 四节规格；主会话独立复跑自检
- Command: `python .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-23-providers-and-models.md`
- Exit code: 0
- Passing evidence: 四节齐全（sections=4, missing=[]）、intent 指向真实文件、无占位符、无裸 hex/px 全 OK（主会话 2026-09-23 复跑 EXITCODE=0）；RikkaHub 事实核对 8 处路径（SettingProviderPage/ProviderSetting/SettingProviderDetailPage/SettingModelPage/Reasoning.kt 等）
- Regression evidence: 不适用（纯文档）

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（纯文档任务，沿 T-033/T-068 先例）
- Reason: 无可先行失败的测试；正确性由自检脚本与用户 review 保证
- User approval: 用户 2026-09-23 指令按 Harness 拆解并执行全部任务
- Alternative verification: 自检脚本 + 用户 review

## Implementation notes

- 阶段轻量化（2026-09-21 用户决定）：不要求独立复核轮次与证据文件门禁，结果由人拍板。
- 规格作者可读 `D:\Project\Android\rikkahub-master` 核对交互事实；产出不得含 RikkaHub 源代码或逐行伪代码。
- 界面数值只引用 token 名（tokens.json），不写裸 hex/临场 px。

## Verification report

- Verdict: PASS（用户 2026-09-23 08:05 回复「按照建议采纳」＝四项拍板点全部按建议采纳，规格翻 accepted）
- Evidence: 规格四节齐备且自检脚本 EXITCODE=0（主会话复跑）；拍板记录已写入规格 Doubts 节末尾（协议范围 OpenAI 兼容先行 / reasoning_effort 离散档位无 per-host 分派 / 建议开关只控 ANSWER_RECOMMENDATIONS / 快速·翻译模型永不为空）；project-brief 回写由本任务 documentation impact 承担，已于同日执行。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（无代码断言）。
- Gaps: V3.3 增量需求文档正文未成文（规格中为建议稿要点）；matrix V1.0 正文未回改（推翻记录以本规格与 intent 为权威）。
- Risks: 实现者若绕过本规格直接读 RikkaHub 源码，clean-room 前提失效。

## Handoff notes

- Completed: 任务创建。
- In progress: 待开工。
- Blocker: 无。
- Unblock condition: 用户确认拆解。
- Next steps: 产出规格 → 用户 review 拍板 → 驱动 083–090。
- Watch-outs: 拍板区未决前 083/084/085 不得开工；「自定义请求头/请求体」是维持 reject 的边界，规格不得悄悄纳入。
