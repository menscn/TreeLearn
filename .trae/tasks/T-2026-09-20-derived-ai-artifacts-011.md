# Treelearn Harness Task: 实现 AI 派生产物与过期保护

id: T-2026-09-20-011
title: 实现 AI 派生产物与过期保护
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: true
depends_on: [T-2026-09-20-deepseek-stream-gateway-006.md, T-2026-09-20-immutable-context-snapshots-007.md, T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-21-versioned-learning-prompt-policy-020.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 标题、摘要、要点、推荐和上下文压缩可调用 AI，但不能覆盖已经变化的主状态或丢失必需上下文。
- Problem/background: 慢请求晚返回会用旧输入改写新回答的推荐或分支摘要。
- Users/scenarios: 用户快速重置、继续对话、切换主题时，旧派生请求仍可能完成。
- Expected outcome: artifact use cases、按用途隔离的 PromptSpec、结构化 JSON 校验、inputDigest/basedOnRevision CAS 写回。
- Core flow and reversibility: 请求记录基线；结果解析后事务内复核；匹配则 apply，不匹配 stale_discarded。
- Inputs/outputs: 输入 scope/kind/revision/digest；输出版本化 artifact 或分类失败。
- State and exception handling: 非法 JSON、缺字段、旧 digest、删除 scope 和网络失败均不改当前值。
- Constraints and trade-offs: 派生产物非权威；不得复用 LEARNING_ANSWER；不猜测截取 JSON；输入用结构化 API 编码；内容日志禁止。
- AI recommendation and rationale: 统一 artifact envelope 与 CAS，避免每个功能各自处理竞态。
- User choice and custom additions: 首页 AI 推荐、标题、摘要、要点和回答推荐属于 V3.1；上下文压缩必须同时覆盖历史问题、推理和回答。
- AI-autonomous decisions: 根标题只依根问题；回答推荐绑定具体稳定 revision。
- User-confirmation decisions: P0 派生产物防旧写回。
- Acceptance evidence: fake gateway 的乱序完成、解析失败和删除竞态测试。
- Facts: 技术方案 §10 是权威。
- Assumptions: provider 可按提示返回结构化 JSON，但必须严格验证。
- Unknowns: 推荐条数可在 UI 任务按 PRD 常量确定。
- Risks: digest 输入不规范导致误判相同或过度刷新。
- Deferred: 远程缓存、推荐个性化和服务端排序。
- Non-goals: 不实现具体首页/聊天视觉。
- Skipped non-critical questions and reason: 提示词按稳定版本迭代；本任务以提示词设计 V1.0 的用途和 artifact 契约为准。

## Goal

确保所有 AI 派生产物只在输入基线仍有效时写回，并能可靠丢弃晚到旧结果。

## Scope

### In scope
- 标题、摘要、要点、回答推荐、首页推荐、上下文压缩用例；对应 PromptPurpose 接入；结构化输入/输出 codec；CAS Repository；脱敏事件。

### Out of scope
- 页面布局、主对话生成和搜索。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增派生产物；不修改 message/revision/snapshot 权威内容。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): PromptRegistry/RequestAssembler 生产用途、版本与消息；Gateway 生产；artifact repository 存储；首页、导航、聊天消费。
- Native bridge or independent-client compatibility/update requirements: 结构化请求使用 AiGateway，不暴露 provider DTO。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: artifact kind、payload schema、digest 输入和失效条件。
- Authoritative code/config/migration evidence: use case/codec/repository 与乱序测试。
- Documentation surfaces checked: 技术方案 §9.2/§10、提示词设计 V1.0、PRD 推荐/标题/摘要/要点/F21 章节。
- Documentation files updated: kind 或失效策略变化时同步技术方案。
- Producer/consumer/sibling/compatibility paths checked: 根标题、分支标题、摘要/要点、回答推荐、首页推荐。
- Superseded phrases/examples reverse-searched: 无基线异步写回和字符串截取 JSON。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 当前态同步，历史保留。

## Acceptance criteria

- [ ] 同一 JVM/集成测试命令先 RED 后 GREEN，旧 revision/digest 的晚到结果全部 stale_discarded。
- [ ] 合法且基线未变的结构化结果原子写入；非法 JSON/缺字段保留旧 artifact。
- [ ] 删除 scope 后的回调不能重建孤儿 artifact；日志仅含 kind、随机 ID、digest 前缀和状态。
- [ ] 首页推荐只提交技术方案允许的树轮廓，不提交完整对话正文。
- [ ] ROOT_TITLE、BRANCH_TITLE、BRANCH_SUMMARY、ANSWER_RECOMMENDATIONS、HOME_RECOMMENDATIONS 使用各自 PromptPurpose/版本，均不包含 LEARNING_ANSWER 教学正文；输入由 JsonWriter 等结构化 API 编码并视为不可信数据。
- [ ] 每个 artifact 保存 `promptPurpose/promptPolicyVersion`；未知用途、schema 外字段、恶意输入指令或提示词构建失败均保留旧 artifact，不调用错误用途或降级为主回答提示词。
- [ ] CONTEXT_COMPRESSION 同时输入并覆盖对应问题、reasoning 与 answer，保留关键定义/条件/结论/不确定点；解析失败、遗漏必需段或压缩后仍超窗时失败关闭主回答，不以删掉 reasoning 降级。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- digest 必须由规范化输入产生；写回时在同一事务内重新读取基线。
- 结构化解析使用 JsonReader/Writer 或等价 Java 结构化 API。
- 输出 envelope 与输入边界按提示词设计 V1.0 §6；PromptPurpose 到 artifact kind 使用穷尽映射，不提供默认回退。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: prompt/schema 漂移。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 解锁首页/设置与导航摘要展示。
- Watch-outs: artifact 失败不得阻断主回答已成功落库。
