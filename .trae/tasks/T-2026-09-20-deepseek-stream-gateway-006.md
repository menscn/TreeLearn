# Treelearn Harness Task: 实现 DeepSeek HTTP 与 SSE 网关

id: T-2026-09-20-006
title: 实现 DeepSeek HTTP 与 SSE 网关
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
depends_on: [T-2026-09-20-bootstrap-java-android-modules-002.md, T-2026-09-20-model-immutable-answer-domain-003.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 使用 DeepSeek 流式生成，并将 reasoning 与 answer 两条通道独立交付业务层。
- Problem/background: 网络块不等于 SSE/JSON 事件，错误处理或拼接错误会丢失思考内容并污染状态机。
- Users/scenarios: 用户验证余额、选择模型/思考模式、流式生成、断网后原位重试。
- Expected outcome: 纯 Java AiGateway、严格保序的 role/messages 请求映射、余额接口、SSE parser、取消句柄和稳定错误分类。
- Core flow and reversibility: 显式构建请求；2xx 后回调 accepted；逐事件回调 reasoning/content；DONE 完成；取消关闭连接。
- Inputs/outputs: 输入 Key、模型、thinking、PromptPurpose/policyVersion 和已组装的有序 messages；输出 accepted、delta、completed 或分类错误事件。
- State and exception handling: 401/402/400/422/429/503、超时、EOF、非法 JSON、未知字段和取消分别映射。
- Constraints and trade-offs: HttpsURLConnection、JsonReader/Writer、无 tools、禁止明文流量和内容日志；网关不自行补 system 或改写消息顺序。
- AI recommendation and rationale: 独立网关屏蔽供应商 DTO，业务层不依赖网络分块或 HTTP 细节。
- User choice and custom additions: thinking 内容必须展示、落库并进入后续上下文。
- AI-autonomous decisions: 每次请求显式写 thinking 开关，不依赖供应商默认值。
- User-confirmation decisions: DeepSeek、两种模型和首版无后台自动续传。
- Acceptance evidence: fixture 服务器与受控真实 API 冒烟测试。
- Facts: 当前官方 base URL 为 `https://api.deepseek.com`；模型 ID 发布前必须复核。
- Assumptions: 用户提供测试 Key，且不写入仓库。
- Unknowns: 供应商未来字段或模型名可能变化。
- Risks: SSE 多行、UTF-8 边界、取消竞态、响应正文泄露。
- Deferred: tools、其他模型供应商和自动重连。
- Non-goals: 不落库、不组装上下文、不管理应用级并发。
- Skipped non-critical questions and reason: 连接池优化不影响 V1 正确性。

## Goal

提供可测试、可取消、不会混淆 reasoning/content 的 DeepSeek 流式边界。

## Scope

### In scope
- 请求/响应 DTO 映射（含 role/messages 保序）、余额查询、SSE parser、超时、取消、错误映射和脱敏网络测试。

### Out of scope
- system 提示词选择/组装、operation 状态持久化、上下文压缩和页面渲染。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增远端生成入口；保持 Key 与正文不进入日志。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): GenerationCoordinator 与派生 AI 用例消费；SecretStore 提供临时 Key。
- Native bridge or independent-client compatibility/update requirements: DeepSeek HTTP/SSE 是唯一外部契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: base URL、模型 ID、请求字段、SSE 和错误映射。
- Authoritative code/config/migration evidence: `android/ai` gateway、parser 和 fixture 测试。
- Documentation surfaces checked: 技术方案 §9、提示词设计 V1.0、PRD 模型/思考/错误/F21 章节、Android README。
- Documentation files updated: 官方契约变化时同步技术方案与 PRD 当前态。
- Producer/consumer/sibling/compatibility paths checked: 余额、首次主题、普通生成、派生 AI、取消。
- Superseded phrases/examples reverse-searched: 旧模型名、默认 thinking 和旧响应字段。
- `documentation impact: none` reason and evidence, if applicable: 无变化时记录官方复核日期与链接。
- Historical/PRD handling: 不改写历史 changelog。

## Acceptance criteria

- [ ] 同一 parser/gateway 测试命令先 RED 后 GREEN，覆盖任意网络分块、多行 data、心跳、未知字段与 `[DONE]`。
- [ ] reasoning_content 与 content 交错时按各自顺序准确回调，EOF 无 DONE 不得报告成功。
- [ ] 401/402/422/429/503、超时、取消和解析失败均得到稳定错误分类并保留已回调部分。
- [ ] fixture 精确验证传入的 system/user/assistant role、顺序与 UTF-8 内容被 JsonWriter 原样编码；网关不新增、删除、合并或重排消息，日志只含用途/版本等脱敏元数据。
- [ ] 使用临时外部 Key 的真实冒烟测试验证余额、模型、thinking on/off；证据不含 Key、prompt 或响应正文。

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

- 不允许按 read() 块直接 parse JSON；先完成 SSE 事件组装。
- accepted 回调必须由调用方同步确认后才继续读取 SSE body；确认失败立即关闭连接，禁止 delta 抢在本地 operation 事务之前到达。
- 原始请求/响应不得写日志；日志只记录状态、字符数、耗时和错误类别。
- 网关只传输 T-020 产出的消息；不得内置兜底 system、根据 thinking 替换正文或接受调用方传入可写日志的 prompt 字符串。
- 实现开始与发布前各复核一次 DeepSeek 官方文档。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 供应商契约漂移。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 真实集成需要人工提供测试 Key。
- Unblock condition: 单元/fixture 可先完成；真实集成阶段注入临时 Key。
- Next steps: 供上下文和 GenerationCoordinator 使用。
- Watch-outs: 不得把 reasoning 当最终答案或忽略。
