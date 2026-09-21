# Treelearn Harness Task: 实现搜索、未读序号与阅读位置

id: T-2026-09-20-015
title: 实现搜索、未读序号与阅读位置
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
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-20-chat-reasoning-answer-actions-013.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 本地搜索覆盖标题、回答、思考和锚点；未读按稳定回答序号，阅读位置按语义锚点恢复。
- Problem/background: 中文默认 FTS 不可靠，布尔未读会丢事件，绝对 scrollY 在版本/字体变化后失效。
- Users/scenarios: 用户搜索历史推理/回答，跳转原位置，查看后台完成分支未读并恢复阅读现场。
- Expected outcome: SearchRepository、归一化参数化 LIKE、搜索 UI、序号式未读和语义阅读位置存取。
- Core flow and reversibility: 查询归一化；结果携带定位锚点；稳定完成推进 sequence；实际进入视口才 seen。
- Inputs/outputs: 输入 query/topic/filter/viewport event；输出排序结果、未读计数和 restore anchor。
- State and exception handling: 空查询、特殊通配符、已删/归档结果、旧 revision 与无效 anchor 有回退。
- Constraints and trade-offs: 不持久化绝对 scrollY；不返回无引用过期 revision；所有 SQL 参数化。
- AI recommendation and rationale: Repository 抽象允许未来换 n-gram，不改变页面与用例。
- User choice and custom additions: reasoning 必须可持久读取并进入产品可见历史。
- AI-autonomous decisions: reasoning 可搜索；只有最新回答实际可见时清未读。
- User-confirmation decisions: P1 搜索、未读与阅读恢复。
- Acceptance evidence: SQLite fixture、Presenter/UI 和 viewport 事件测试。
- Facts: 技术方案 §6.3、§11 是权威。
- Assumptions: 个人本机数据量允许 LIKE 首版性能。
- Unknowns: 超大数据阈值需真实使用后测量。
- Risks: `%/_` 转义、中文归一化、RecyclerView 可见性误判。
- Deferred: n-gram/FTS、跨主题高级筛选和搜索建议。
- Non-goals: 不实现树几何或远程搜索。
- Skipped non-critical questions and reason: 相关性权重可在确定性默认排序后迭代。

## Goal

让用户能查到思考和回答、跳回语义位置，并可靠识别后台生成的新内容。

## Scope

### In scope
- SearchRepository/query、结果 UI、sequence unread、折叠祖先聚合、semantic reading anchor 和恢复。

### Out of scope
- 全文索引优化、云搜索和 Canvas 绘制。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增检索/未读/恢复；保持旧子分支只定位其固定 revision。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): DAO/revision/operation/branch 生产；搜索/聊天/导航/画布消费。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 搜索字段/排序、未读推进条件和阅读锚点格式。
- Authoritative code/config/migration evidence: SearchRepository、DAO query、Presenter 与 UI tests。
- Documentation surfaces checked: 技术方案 §6.3/§11、PRD 搜索/未读/继续阅读、原型。
- Documentation files updated: 搜索范围或清未读条件改变时同步 PRD/技术方案。
- Producer/consumer/sibling/compatibility paths checked: 当前 stable、失败 partial、reasoning、旧来源、归档/删除、折叠聚合。
- Superseded phrases/examples reverse-searched: 布尔未读、打开即清未读、绝对 scrollY 和默认 FTS 保证中文。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 当前行为单独维护。

## Acceptance criteria

- [ ] 同一 SQLite/Presenter 测试命令先 RED 后 GREEN，中文、空格、大小写及 `%/_` 输入均参数化且结果确定。
- [ ] 可搜索当前 stable reasoning/answer、当前失败 partial、标题和 anchor；不重复返回无引用过期 revision。
- [ ] stable 完成才递增 latestStableSequence；仅目标回答进入视口才推进 lastSeen，推荐/摘要不制造未读。
- [ ] 字体、版本或窗口变化后优先用 message/revision + item offset + viewport offset 恢复；失效时回退最近合法位置。

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

- LIKE pattern 必须先转义 escape char、`%`、`_`，并显式声明 ESCAPE。
- 阅读锚点保存稳定 ID 与相对偏移，不保存 Adapter position 或整个 ViewState。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 数据增长后的查询延迟。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 供 Canvas 未读聚合与最终验收使用。
- Watch-outs: 搜索 snippet 不得把 reasoning 与 answer 混成错误来源。
