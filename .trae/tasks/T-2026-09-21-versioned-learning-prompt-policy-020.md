# Treelearn Harness Task: 实现版本化学习导师提示词策略

id: T-2026-09-21-020
title: 实现版本化学习导师提示词策略
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 190
shared_code_touched: true
depends_on: [T-2026-09-20-model-immutable-answer-domain-003.md, T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-20-deepseek-stream-gateway-006.md, T-2026-09-20-immutable-context-snapshots-007.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: DeepSeek 主回答必须体现知芽“准确、易懂、高效”的学习定位，同时与标题、摘要、推荐和压缩调用隔离。
- Problem/background: 单一提示词覆盖所有请求会污染结构化派生产物；system 被重复、错位或裁剪会使回答行为失控且难以追踪。
- Users/scenarios: 用户学习入门或专业概念、纠正错误前提、询问时效信息、折叠思考后只阅读最终答案；派生调用仍需稳定 JSON。
- Expected outcome: 供应商无关 `PromptPurpose/PromptSpec`、版本化 DeepSeek 注册表、请求组装器、容量预留与确定性回归测试。
- Core flow and reversibility: 按用途取得固定 PromptSpec；主回答选定版本并持久化；system 第一、历史居中、当前 user 最后；构建失败不调用远端。
- Inputs/outputs: 输入用途、thinking 开关、ContextPlan 编码结果与当前输入；输出有序 DeepSeek messages、用途和策略版本，或显式构建错误。
- State and exception handling: 未知用途、空正文、重复/错位 system、容量不足、非法历史标签和动态 system 插值均 fail closed。
- Constraints and trade-offs: 首版不开放用户编辑提示词，不引入用户画像、联网检索、RAG 或新的永久表；准确性是行为约束，不是事实保证。
- AI recommendation and rationale: 用版本化注册表隔离运行时行为，用 PromptPurpose 隔离不同 JSON 契约，避免散落字符串和静默行为漂移。
- User choice and custom additions: 用户选择“自适应导师”，要求学习更高效、信息更准确、更容易懂，并于 2026-09-21 确认设计。
- AI-autonomous decisions: 版本格式 `tl-<purpose>/semver`；数据库只存版本号，完整正文只在 `:ai` 注册表中。
- User-confirmation decisions: 自适应导师正文、思考附加策略、派生用途隔离、版本落库和注入顺序已确认。
- Acceptance evidence: JVM fixture 精确断言注册表、消息顺序、token 边界、恶意上下文和版本复制；真实行为由 T-018/T-019 验收。
- Facts: `Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md` 是提示词正文与策略权威。
- Assumptions: T-006 提供有序消息请求边界，T-007 提供不含 system 的历史 ContextPlan 编码结果。
- Unknowns: 模型对自然语言风格的非确定性只能通过受控样例和真实冒烟评估，不能逐字断言。
- Risks: system 重复、提示词版本静默覆盖、派生任务误用教学提示词、恶意历史文字改变协议。
- Deferred: 用户自定义提示词、用户画像、在线评测服务、RAG 与其它模型供应商。
- Non-goals: 不实现 UI、派生业务用例、网络解析或数据库 DDL。
- Skipped non-critical questions and reason: 提示词正文与默认教学方式已由用户确认，无需再次选择。

## Goal

让每次 DeepSeek 调用都使用可追踪、按用途隔离且不能被历史内容篡改的 system 策略，并保证主回答符合自适应学习导师定位。

## Scope

### In scope
- `PromptPurpose/PromptSpec`、`DeepSeekPromptRegistry`、主回答基础提示词、thinking 静态附加策略、派生用途策略、`DeepSeekRequestAssembler`、token 预留和 fixture 测试。

### Out of scope
- SQLite schema/DAO、GenerationCoordinator 状态流、artifact CAS、页面渲染和真实 DeepSeek 发布冒烟。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增版本化 system 策略；保留 ContextPlan 的分支隔离、历史 reasoning 编码、thinking 显式开关和流式双通道。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 注册表生产 PromptSpec；请求组装器消费 ContextPlan；GenerationCoordinator 与 artifact use cases 选择用途；AiGateway 只发送有序 messages。
- Native bridge or independent-client compatibility/update requirements: DeepSeek messages 的 role/order 和 token 预算是外部契约；无 Web/后端桥。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: PromptPurpose、策略版本、system 正文、thinking 附加段、消息顺序、容量失败与派生用途隔离。
- Authoritative code/config/migration evidence: `android/core` 提示词契约、`android/ai` 注册表/组装器及 JVM fixture。
- Documentation surfaces checked: BASE-26、PRD F21、提示词设计 V1.0、技术架构 §9.2、Android README、DeepSeek 请求契约。
- Documentation files updated: 实现类名、用途、版本或顺序变化时同步上述当前态文档。
- Producer/consumer/sibling/compatibility paths checked: 首次根问题、普通追问、重试、重置、标题、摘要、回答推荐、首页推荐、上下文压缩。
- Superseded phrases/examples reverse-searched: 无 system、单提示词覆盖全部调用、system 参与摘要/裁剪、用户可编辑提示词。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写历史记录；当前行为变化新增版本记录。

## Acceptance criteria

- [ ] 同一 JVM 测试命令先 RED 后 GREEN；所有 PromptPurpose 有唯一非空版本和非空 system，`LEARNING_ANSWER` 与各派生用途内容隔离。
- [ ] 主回答请求恰好一条 system 且位于第一条，当前 user 恰好位于最后一条；thinking on 只追加已确认的静态段，任何 system 内容均无用户/API Key 动态插值。
- [ ] token 预算先预留 system、当前 user 与最小回答空间；空间不足时拒绝构建，不裁剪 system，不静默删除历史 reasoning。
- [ ] 历史内容含 `system:`、同名内部标签或“忽略此前规则”时不改变 role/order/PromptPurpose；派生输入使用结构化 JSON 且不能改变输出 schema。
- [ ] `tl-learning-answer/1.0.0` 正文与提示词设计文档一致，并以行为 fixture 覆盖入门、专业、错误前提、歧义、时效信息、类比边界和最终回答独立性所需指令。
- [ ] 日志/异常只允许用途、版本、字符数和错误类别，不包含 system content、用户/历史/派生输入或输出正文。

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

- 提示词正文必须从设计文档逐字转录为 UTF-8 常量或只读资源，并用快照/digest 测试防止同版本静默变化；不要从 Markdown 在运行时读取。
- `PromptSpec` 对外不可变；注册表不提供任意字符串 lookup 回退，不识别用途即报错。
- 请求组装使用新列表，不修改或复用调用方可变集合；system 与当前 user 不进入 ContextPlan。
- 结构化派生输入使用 JsonWriter/JsonReader；不得用 XML/JSON 字符串拼接做转义。
- 精确行为样例见提示词设计 V1.0 §8；真实模型非确定性评估留给 T-018/T-019。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 提示词自然语言与模型版本交互存在非确定性。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无；真实 Key 不是本任务确定性测试前置条件。
- Unblock condition: 不适用。
- Next steps: 解锁 T-008 主回答生成协调和 T-011 派生 AI 用途接入。
- Watch-outs: 不得把 system 文本写日志、SQLite 或 SharedPreferences，也不得让 ContextPlan 负责 system。
