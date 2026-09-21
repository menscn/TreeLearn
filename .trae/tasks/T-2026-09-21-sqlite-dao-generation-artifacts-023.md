# Treelearn Harness Task: answer_revisions / generation_operations / context_snapshots / ai_artifacts 四表 DAO 与首题原子创建

id: T-2026-09-21-023
title: answer_revisions / generation_operations / context_snapshots / ai_artifacts 四表 DAO 与首题原子创建
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
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-21-sqlite-dao-topic-branch-message-022.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 七类本地事实必须以可迁移、可事务验证的 SQLite 结构持久化（见《Android SQLite 建表入场确认清单 V1.0》）。
- Problem/background: 原 T-004 把七表 schema、DAO、事务与迁移放在一个任务里，实测生产代码约 900+ 行，超过 `harness-tasks.md:45` 的 500 行硬上限。按「拆解阶段就估算 ≤ 400 行」的规则拆为三个切片，本任务是第三片（收口片）。
- Users/scenarios: 用户发出根问题后立刻断网/杀进程；重启后要么看到完整的主题树与可重试的生成操作，要么什么都没有，绝不出现空树。
- Expected outcome: `:data` 提供 `AnswerRevisionDao`、`GenerationOperationDao`（行形态）、`ContextSnapshotDao`、`AiArtifactDao`，以及供 T-006 网关 accepted 回调同步调用的**首题原子创建**事务入口。
- Core flow and reversibility: 首题在单个事务内写入五类行（`Topic`+根`Branch`+用户`Message`+AI`Message`+`GenerationOperation`）；任一步失败则整体回滚且不留空树；成功事务把 operation 的 `prompt_policy_version` 原样复制进 revision。
- Inputs/outputs: 输入为 `:core` 实体/行对象与命令；输出为不可变实体、显式空值或分类错误。
- State and exception handling: 终态操作不可再改写；`(message_id, revision_no)` 重复与 `branch_id` 重复快照必须被真实 SQLite 拒绝；过期派生结果（基线不匹配）必须被拒绝而不是覆盖。
- Constraints and trade-offs: DDL 一律不在本任务；时间用 UTC epoch millis；参数化 SQL；按行形态读写 `generation_operations`，不新增 `GenerationOperation.restore`。
- AI recommendation and rationale: 行形态 DAO 而非实体再水合，因为 `GenerationOperation` 无 `restore` 入口，且恢复只需状态推导（§7）。
- User choice and custom additions: 推理、最终回答和失败部分内容均须落库；只落提示词用途/版本，不落 system 正文。
- AI-autonomous decisions: 活动操作上限由 T-004 的偏索引（partial unique index）承担，DAO 只负责把约束冲突翻译成领域错误。
- User-confirmation decisions: 七张逻辑表及 2026-09-21 的提示词用途/版本字段已确认；不新增第八张表。
- Acceptance evidence: instrumentation 首题原子性、回滚、唯一约束、活动操作上限、基线防旧写回、终态不可改写、关闭重开后读取一致。
- Facts: 技术方案 §6.1/§6.2、§8、§9.2、§9.3；建表清单 §2/§3。
- Assumptions: 首版无历史库；迁移路径由 T-004 覆盖。
- Unknowns: 无。
- Risks: 事务范围过大造成锁等待；批量流式落盘与终态写并发。
- Deferred: SSE 批量落盘节流（250ms/256 字符）由 T-006/T-008 实现，本任务只提供落盘入口。
- Non-goals: 不实现 SSE 解析、网络、UI 或并发调度。
- Skipped non-critical questions and reason: 索引微调由真实查询计划决定。

## Goal

让不可变回答版本、可恢复生成状态、不可变出生快照与可失效派生结果在真实 Android SQLite 中原子写入、按约束拒绝非法行，并让首个主题的九行级联在单个事务内全成或全败。

## Scope

### In scope
- `AnswerRevisionDao`、`GenerationOperationDao`（含 `GenerationOperationRow`）、`ContextSnapshotDao`、`AiArtifactDao`。
- 首题原子创建事务入口：`Topic` + 根 `Branch` + 用户 `Message` + AI `Message` + `GenerationOperation` 五类行同事务写入，并返回可供网关同步等待的提交结果。
- 四表的 `Cursor → 实体`/`Cursor → 行` 只读映射。
- 过期派生结果的基线复核（`based_on_revision_id` / `input_digest`）与终态不可改写约束。

### Out of scope
- schema/DDL 与 `SQLiteOpenHelper`（T-004）、`topics`/`branches`/`messages` 三表 DAO（T-2026-09-21-022）、SSE 解析与节流（T-006/T-008）、上下文组装（T-007）、UI 与搜索。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增持久化读写与首题事务；保持 `:core` 的不可变约束、状态迁移规则与「不稳定内容不得升级为稳定来源」不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游 `:core` 的 `AnswerRevision`/`GenerationOperation`/`ContextSnapshot`/`AiArtifact` 与 T-021 的状态机；下游 T-006 网关 accepted 回调、T-008 协调器恢复、T-007 上下文、T-011 派生、T-015 搜索、UI。
- Native bridge or independent-client compatibility/update requirements: 无桥接；只消费 Android framework SQLite。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 四表列名、索引、唯一/偏索引约束、级联行为、首题事务边界与失败语义。
- Authoritative code/config/migration evidence: `android/data` 的 DAO 与事务源码、`:app` 的 instrumentation 夹具。
- Documentation surfaces checked: 技术方案 §6.1/§6.2、§8、§9.2/§9.3、建表清单 §2/§3、`android/README.md`。
- Documentation files updated: `android/README.md` 的持久化入口索引；若首题事务边界与技术方案 §8 有差异，同步技术方案。
- Producer/consumer/sibling/compatibility paths checked: 四表全部 DAO 方法、级联路径、T-006/T-008 的调用点。
- Superseded phrases/examples reverse-searched: 旧列名、旧 DAO 方法名、`GenerationOperation.restore`。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写冻结 PRD。

## Acceptance criteria

- [ ] 同一 instrumentation 命令先 RED 后 GREEN。
- [ ] **首题原子性**：在同一事务内写入 `topics`1 行 + `branches`1 行（根）+ `messages`2 行 + `generation_operations`1 行；事务提交后五类行全部可读，`PRAGMA foreign_key_check` 无结果。
- [ ] **首题全败**：在事务中途注入失败（例如同一主题插入第二个根分支或重复 operation id），事务回滚后 `topics`/`branches`/`messages`/`generation_operations` 中该主题相关行数均为 0，且不残留空树。
- [ ] **版本随同复制**：成功事务把 operation 的 `prompt_policy_version` 原样复制到 `answer_revisions.prompt_policy_version`（同一字符串，非重新取值）。
- [ ] `answer_revisions`：`(message_id, revision_no)` 重复插入被真实 SQLite 拒绝；`prompt_policy_version` 为空被拒绝；删除消息后该表行级联清空。
- [ ] `generation_operations`：同一 `branch_id` 的第二个活动操作被拒绝；终态行不可再改写（更新终态行被判 FAIL）；部分 reasoning/answer 可批量追加且不产生中间可见脏状态。
- [ ] `context_snapshots`：同一 `branch_id` 的第二行被拒绝；`schema_version`/`digest` 为空被拒绝；读回必须经结构化 JSON 解析（不得字符串拼接解析），且快照写入后不可被改写。
- [ ] `ai_artifacts`：`prompt_purpose`/`prompt_policy_version` 为空被拒绝；基线不匹配（`based_on_revision_id` 或 `input_digest` 与当前事实不一致）的写回被判 FAIL，而不是覆盖。
- [ ] **再水合契约（自 T-003 下推）**：`AnswerRevision.restore` 只允许出现在 `AnswerRevisionDao`/`GenerationOperationDao` 的**只读映射**方法内；写路径（创建、重试、重置、分叉、迁移、回填）必须走 `AnswerRevision.create`。另：本任务不得新增 `GenerationOperation.restore`，对 `android/data` 与 `:core` 全部生产源码做机械检索并给出命令与完整输出。
- [ ] **不落 system 正文**：本任务所有 DAO 的公开方法签名中不出现 `systemContent`/`systemPrompt` 形态的入参，也不新增任何正文列；对四表写入夹具后扫描其全部 TEXT 列，断言不包含提示词正文哨兵子串。
- [ ] `db-migration-reviewer` 按 Android SQLite 守门复核四表约束、级联、事务回滚与偏索引证据，不得用替身数据库。

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
- 列名一律引用 T-004 的 schema 常量，不写字面量字符串。
- 按照 `android/README.md` 与 T-2026-09-20-sqlite-schema-dao-transactions-004.md 的「开工前实测①」：`generation_operations` 按**行形态**实现，恢复流程用 `GenerationState.processRecoverableStates()` + `recoveryTarget()` 推导目标状态，**不新增** `GenerationOperation.restore`。
- 首题事务入口必须能被 T-006 的 accepted 回调**同步等待**（提交成功后才返回并允许读取 SSE body）；失败必须返回明确错误而不是抛裸异常穿透网络层。
- 不稳定内容（部分推理/回答）只写 operation 缓冲区；只有收到正常流终止且最终回答非空才允许创建 `AnswerRevision` 并把 operation 置为 `STABLE`——本任务只提供落盘入口与约束，不实现判定逻辑。
- 偏索引（活动操作上限）的具体 DDL 属于 T-004；本任务若发现 T-004 未提供该约束，应报告缺口而不是在本任务临时建索引。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 事务范围过大、锁等待、终态改写与基线复核被绕过。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 完成后解锁 T-006（网关 accepted 事务）、T-008（恢复与重试）、T-007（快照消费）、T-011（派生写回）、T-015（搜索）。
- Watch-outs:
  - 不要为了让写路径「跑通」而把 `restore` 当构造器用；需要「重建后再变更」时先用 `restore` 重建只读实体，再经领域工厂产生新状态。
  - 首题事务必须覆盖**五类行**；漏掉 AI 消息或 operation 会让 §8 的原子性假设在恢复路径上失效。
  - `ai_artifacts` 的基线复核必须在**写回事务内**进行，事务外先查后写会重新引入旧结果覆盖问题。
