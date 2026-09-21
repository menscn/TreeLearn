# Treelearn Harness Task: 实现主题与分支生命周期

id: T-2026-09-20-009
title: 实现主题与分支生命周期
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
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-20-deepseek-stream-gateway-006.md, T-2026-09-20-immutable-context-snapshots-007.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首题远端接受前不建空树；稳定回答可整条或选区分叉并固定来源版本。
- Problem/background: 本地先建主题会在 Key/余额/请求失败时残留垃圾；分叉若跟随 active revision 会改写历史。
- Users/scenarios: 用户创建首题、继续追问、从整条回答或选中文字创建子分支。
- Expected outcome: CreateTopic、AppendQuestion、CreateBranch 用例和事务契约。
- Core flow and reversibility: 草稿先留 UI；验证 Key/余额；模型请求 2xx accepted 后原子建根；分叉原子建 snapshot。
- Inputs/outputs: 输入草稿、branch/revision/anchor；输出 topic/branch/operation ID 或领域错误。
- State and exception handling: 2xx 前失败无树；2xx 后中断保留树与部分内容；非稳定版本禁止分叉。
- Constraints and trade-offs: 一主题一根；用户消息不可变；anchor 用 offsets + quote + hash。
- AI recommendation and rationale: 将“供应商已接受”作为首题本地身份边界，兼顾无空树和流中断恢复。
- User choice and custom additions: 首版个人本地应用，失败回到原位重试。
- AI-autonomous decisions: 本地 operation UUID 去重；供应商计算不可见重复不承诺端到端幂等。
- User-confirmation decisions: P0 首次创建原子性与不可变来源。
- Acceptance evidence: fake gateway + SQLite 事务集成测试覆盖 2xx 前后所有失败点。
- Facts: 技术方案 §5、§8 是权威。
- Assumptions: Gateway 能在读取流前发出 accepted 事件。
- Unknowns: 供应商未提供可依赖幂等键。
- Risks: accepted 与本地事务失败造成远端已计费但本地无树。
- Deferred: 服务端幂等和跨设备合并。
- Non-goals: 不实现页面视觉、重置或归档。
- Skipped non-critical questions and reason: 默认标题由派生 AI 任务负责，不影响树创建。

## Goal

建立无空树、来源版本固定、事务一致的主题和分支核心流程。

## Scope

### In scope
- CreateTopic/AppendQuestion/CreateBranch、anchor 校验、snapshot 创建、首次 accepted 事务和失败结果。

### Out of scope
- 页面、派生标题、重试/重置、删除和搜索。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增主题/分支命令；保持草稿、稳定版本和分支隔离语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 首页/聊天触发；Gateway accepted；DAO/ContextSnapshot/Coordinator 消费。
- Native bridge or independent-client compatibility/update requirements: 需匹配 Gateway accepted 与 Repository 事务接口。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首题落库边界、分支来源、anchor 与快照创建。
- Authoritative code/config/migration evidence: application use cases 与事务集成测试。
- Documentation surfaces checked: 技术方案 §5/§8、PRD 创建/追问/分叉流程、原型入口。
- Documentation files updated: 真实边界改变时同步 PRD 和技术方案。
- Producer/consumer/sibling/compatibility paths checked: 首页提交、聊天追问、整条分叉、选区分叉、失败返回。
- Superseded phrases/examples reverse-searched: 提交即建空树、动态继承父上下文。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 当前态与历史 changelog 分开。

## Acceptance criteria

- [ ] 同一集成测试命令先 RED 后 GREEN；401/402/422/429/503 与 2xx 前断网均不产生 topic/branch/message。
- [ ] 2xx accepted 后一个事务创建根 topic/branch、用户/AI message 和 operation；流中断保留该现场。
- [ ] 仅稳定 revision 可分叉；新 branch 固定 sourceRevisionId、anchor 与 immutable snapshot。
- [ ] 父分支后续追问或重置后，子分支来源和上下文仍指向创建时版本。

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

- 首题 accepted 后数据库事务失败要明确反馈，不得用第二个本地 topic 猜测补偿。
- accepted 回调必须在根事务提交成功后才向网关确认继续读取 SSE；提交失败关闭本次流。
- anchor offsets 以稳定 answer 字符序列定义，并保存 quote/hash 回退。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: accepted/事务之间的不可避免窗口。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 解锁 UI、重试/重置、导航和搜索。
- Watch-outs: 分叉不得引用 partial operation 或未来 active revision。
