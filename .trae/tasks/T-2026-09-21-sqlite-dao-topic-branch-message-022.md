# Treelearn Harness Task: topics / branches / messages 三表 DAO 与只读映射

id: T-2026-09-21-022
title: topics / branches / messages 三表 DAO 与只读映射
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
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 七类本地事实必须以可迁移、可事务验证的 SQLite 结构持久化（见《Android SQLite 建表入场确认清单 V1.0》）。
- Problem/background: 原 T-004 把七表 schema、DAO、事务与迁移放在一个任务里，实测生产代码约 900+ 行，超过 `harness-tasks.md:45` 的 500 行硬上限。按「拆解阶段就估算 ≤ 400 行」的规则拆为三个切片，本任务是第二片。
- Users/scenarios: 用户创建主题、追问、分叉、归档、删除后重启应用，树结构与消息顺序仍一致。
- Expected outcome: `:data` 提供 `topics`/`branches`/`messages` 三表的窄 DAO，并让 `:core` 的 `Topic`/`Branch`/`Message` 可双向映射。
- Core flow and reversibility: 插入与更新走显式事务；只读查询通过 `Cursor → 实体` 映射；删除由外键级联承担，不在 DAO 里手写递归删除。
- Inputs/outputs: 输入为 `:core` 实体与查询条件；输出为不可变实体、显式空值或分类错误。
- State and exception handling: 拒绝重复 `(branch_id, ordinal)`；拒绝同一主题出现第二个根分支；连接或约束异常不得伪装成功。
- Constraints and trade-offs: UUID 字符串主键、UTC epoch millis、参数化 SQL；DDL 一律不出现本任务的代码里（只在 T-004 的 schema/helper 中）。
- AI recommendation and rationale: 每个 DAO 只暴露窄方法，事务边界由调用方显式开启，保持 Java-only。
- User choice and custom additions: 标题与阅读位置字段落在 `branches`；用户消息正文创建后不可变。
- AI-autonomous decisions: 复用 T-004 的事务 runner 与 schema 常量，不重复定义 DDL/列名。
- User-confirmation decisions: 七张逻辑表与跨表约定已确认（清单 §2/§3）；不新增第八张表。
- Acceptance evidence: instrumentation 新装写入、唯一约束、每主题单根、级联删除、关闭重开后读取一致。
- Facts: 技术方案 §5/§6、清单 §2/§3。
- Assumptions: 首个发行版无历史库，迁移路径由 T-004 覆盖。
- Unknowns: 无。
- Risks: 手写列名与 schema 常量漂移；只读映射误用写路径工厂。
- Deferred: 搜索排序与未读聚合（T-015）。
- Non-goals: 不实现 UI、HTTP、生成编排与搜索。
- Skipped non-critical questions and reason: 索引微调由真实查询计划决定。

## Goal

让主题、分支与消息三类权威事实在真实 Android SQLite 中可原子写入、按约束拒绝非法行、并可无损读回 `:core` 实体。

## Scope

### In scope
- `TopicDao`、`BranchDao`、`MessageDao` 及三表的 `Cursor → 实体` 只读映射。

### Out of scope
- schema/DDL 与 `SQLiteOpenHelper`（T-004）、其余四表 DAO（T-2026-09-21-023）、UI、搜索、生成编排。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增持久化读写；保持 `:core` 的不可变约束与状态迁移规则不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游 `:core` 的 `Topic`/`Branch`/`Message`；下游 T-2026-09-21-023、上下文、生成、搜索与 UI 用例。
- Native bridge or independent-client compatibility/update requirements: 无桥接；只消费 Android framework SQLite。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 三表的列名、索引、唯一约束与级联行为及其 DAO 入口。
- Authoritative code/config/migration evidence: `android/data` 的 DAO 源码与 `:app` 的 instrumentation 夹具。
- Documentation surfaces checked: 技术方案 §6.1、清单 §2/§3、`android/README.md`。
- Documentation files updated: `android/README.md` 的领域层/持久化入口索引。
- Producer/consumer/sibling/compatibility paths checked: 三表全部 DAO 方法与级联路径。
- Superseded phrases/examples reverse-searched: 旧列名与旧 DAO 方法名。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写冻结 PRD。

## Acceptance criteria

- [ ] 同一 instrumentation 命令先 RED 后 GREEN。
- [ ] `(branch_id, ordinal)` 重复插入被真实 SQLite 拒绝，且不留下半行。
- [ ] 同一主题插入第二个根分支被判 FAIL（`branches` 的根唯一约束）。
- [ ] 删除主题后 `branches`/`messages` 级联清空，`PRAGMA foreign_key_check` 无结果。
- [ ] 关闭重开后 `Topic`/`Branch`/`Message` 通过只读映射读回与写入前逐字段相等。
- [ ] 非根分支读回时必须带 `BranchSource`（`parent/sourceMessage/sourceRevision/anchor/contextSnapshot`），且整条回答分叉时偏移为空、版本引用仍非空。
- [ ] **再水合契约**：`BranchSource.restore` 只允许出现在 `BranchDao` 的只读映射方法内；本任务对 `android/data` 全部生产源码做机械检索并给出命令与完整输出。
- [ ] `db-migration-reviewer` 按 Android SQLite 守门复核约束与级联证据，不得用替身数据库。

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

- instrumentation 测试写在 `:app` 的 androidTest 源集（项目约定：单一测试 APK），不在 `:data` 声明 androidTest 依赖。
- 只读映射用 `AnswerRevision.restore` / `BranchSource.restore`；**写路径**必须走 `:core` 的工厂与变更方法。
- 列名一律引用 T-004 的 schema 常量，不写字面量字符串。
- 每打开一次数据库显式启用并验证 `PRAGMA foreign_keys=ON`（由 T-004 的 helper 负责，本任务只依赖）。
- 级联删除由外键 `ON DELETE CASCADE` 承担，DAO 不得手写递归删除。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 列名与 schema 常量漂移、只读映射被写路径误用。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 完成后解锁 T-2026-09-21-023 与 T-009（主题/分支生命周期）、T-007（上下文快照消费）。
- Watch-outs: 不要在本任务里新增 DDL；不要为「让写路径跑通」而使用 `restore`。
