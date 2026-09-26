# Treelearn Harness Task: 规格｜搜索服务

id: T-2026-09-23-080
title: 规格｜搜索服务
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

- Parent summary: RikkaHub 设置中心五能力吸收；本任务产出搜索服务的需求/界面/技术规格。
- Problem/background: 知芽无联网检索能力；RikkaHub 支持配置搜索服务（引擎、结果数）并在对话中调用。用户口中的「本机搜索」按意图文件假设为「搜索服务 API 真实可调用」，规格须向用户核对。
- Users/scenarios: 学习者提问涉及时事/外部资料时，配置一次搜索服务，对话中获得带来源的检索结果。
- Expected outcome: `Docs/spec/SPEC-2026-09-23-search-service.md`，过自检脚本；含搜索引擎选型拍板区。
- Core flow and reversibility: 设置页配置引擎+参数+结果数（可关）→ 对话发送时触发检索（手动开关优先，不默认开启）→ 结果注入上下文，回答带来源标注 → 关闭后回到纯本地上下文。
- Inputs/outputs: 输入 RikkaHub 搜索页交互事实（`ui/pages/setting/` 搜索节、搜索 provider 实现）与知芽 SSE 上下文构造；输出规格。
- State and exception handling: [OFF] 检索失败降级为无搜索继续生成并提示；[NOKEY] 搜索服务 Key 缺失时拦截并引导；[PART] 结果为空时明确反馈。
- Constraints and trade-offs: 无新依赖原则优先（HttpsURLConnection 直连 REST）；不引入 MCP；搜索结果不落库只进当次上下文（落库另拍板）。
- AI recommendation and rationale: 首选 1–2 个无需复杂鉴权的引擎（如 Tavily/Bing 兼容）+ 结果数配置；RikkaHub 预置引擎清单作为候选池列拍板区。
- User choice and custom additions: 用户确认搜索服务要真实可用；引擎选型在拍板区定。
- AI-autonomous decisions: 触发方式默认「对话页搜索开关 + 模型设置页聊天建议并列」，不默认开启。
- User-confirmation decisions: ①引擎选型（第一批做哪 1–2 个）；②搜索结果是否随消息持久化（默认不落库）；③触发交互（输入栏开关 vs 发送前确认）。
- Acceptance evidence: SPEC 存在、自检退出码 0、拍板区齐、异常语义齐。
- Facts: 知芽上下文构造在 fork 快照链路（`core` 模块 ContextSnapshot）；RikkaHub 搜索 provider 在其 `ai`/`data` 层（规格作者核对路径）。
- Assumptions: 目标引擎均有简单 REST API。
- Unknowns: 各引擎鉴权方式与免费额度差异。
- Risks: 检索结果污染学习树上下文；来源不可溯导致回答可信度下降。
- Deferred: 多引擎并发聚合、搜索结果缓存。
- Non-goals: 自建索引、MCP、浏览器抓取。
- Skipped non-critical questions and reason: 结果数上限默认值由规格作者定。

## Goal

产出搜索服务配置页与对话检索增强的完整规格，供 091/092 实现任务直接引用。

## Scope

### In scope

- 搜索服务设置页（引擎、参数、结果数、启停）。
- 对话页检索触发与结果注入语义、来源呈现、异常降级。

### Out of scope

- 代码实现；语音/文件管理/提供商（另三份规格）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯文档。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无代码改动。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 文档任务。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `core` 上下文快照契约、RikkaHub 搜索相关源（规格作者核对）。
- Documentation surfaces checked: `Docs/spec/`。
- Documentation files updated: 新建 SPEC。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 无旧表述冲突。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录，不改旧版。

## Acceptance criteria

- [ ] SPEC 存在且自检脚本退出码 0。
- [ ] 拍板区 3 项附建议+理由+代价；RikkaHub 事实带路径出处。
- [ ] 用户 review 拍板完成。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（文档任务）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 由 spec-author 子代理（agent-ccb89b6f）产出 `Docs/spec/SPEC-2026-09-23-search-service.md` 四节规格；主会话独立复跑自检
- Command: `python .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-23-search-service.md`
- Exit code: 0
- Passing evidence: 四节齐全（sections=5, missing=[]）、intent 指向真实文件、无占位符、无裸 hex/px 全 OK（主会话 2026-09-23 复跑 EXITCODE=0）；引擎传输细节标 DEFERRED:待 T-085（网关抽象）
- Regression evidence: 不适用（纯文档）

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（纯文档任务，沿先例）
- Reason: 无可先行失败的测试
- User approval: 用户 2026-09-23 指令按 Harness 拆解并执行
- Alternative verification: 自检脚本 + 用户 review

## Implementation notes

- 检索结果进入上下文的注入点必须与 fork 快照（创建快照）兼容：分叉时不丢来源。
- 界面数值只引用 token 名。

## Verification report

- Verdict: PASS（用户 2026-09-23 08:05 回复「按照建议采纳」＝四项拍板点全部按建议采纳，规格翻 accepted）
- Evidence: 规格四节齐备且自检脚本 EXITCODE=0（主会话复跑）；拍板记录已写入规格 Doubts 节末尾（本机搜索=搜索服务 API / 首批智谱+Tavily / 检索结果不落库 / 输入栏 chip 弹底部面板）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（无代码断言）。
- Gaps: 引擎 HTTP 传输细节标 DEFERRED:待 T-2026-09-23-085（网关抽象）落地后在实现任务写实；真实 Key 的联调验收需用户后续提供。
- Risks: 免 Key 的 Bing 本地抓取路线被排除后，若用户实际只有 Bing 可用需回来重选型。

## Handoff notes

- Completed: 任务创建。
- In progress: 待开工。
- Blocker: 无。
- Unblock condition: 用户确认拆解。
- Next steps: 产出规格 → review → 驱动 091/092。
- Watch-outs: 拍板未决前 091/092 不得开工；不默认开启搜索（对齐 RikkaHub「搜索开关独立设置，不默认开启」）。
