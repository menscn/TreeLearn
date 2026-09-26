# Treelearn Harness Task: context_snapshots / ai_artifacts 两表 DAO（行 + 原始 JSON 载荷）

id: T-2026-09-21-024
title: context_snapshots / ai_artifacts 两表 DAO（行 + 原始 JSON 载荷）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 220
shared_code_touched: true
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-21-sqlite-dao-topic-branch-message-022.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 七类本地事实必须以可迁移、可事务验证的 SQLite 结构持久化（见《Android SQLite 建表入场确认清单 V1.0》）。
- Problem/background: 本任务由 **T-2026-09-21-023 于 2026-09-21 按用户裁定的「三片垂直切分」拆出**。原 T-023 一片装四表 DAO 加首题事务，实测预估 480–580 行压在/越过 500 行硬上限，故按「载荷表」与「行状态表」拆开：本任务收 `context_snapshots` 与 `ai_artifacts` 两张**以不透明载荷为主体**的表。
- Users/scenarios: 分支创建时固定来源被一次性写入且此后永不改变；分支溯源上下文必须可重建；标题/摘要/推荐等派生结果只在输入基线仍有效时覆盖，晚到的旧结果必须被丢弃而不是覆盖新结果。
- Expected outcome: `:data` 提供 `ContextSnapshotDao` 与 `AiArtifactDao`，按「行 + 原始 JSON 载荷」形态读写；载荷的结构化解析不使用本层任何字符串拼接。
- Core flow and reversibility: 快照写入后不可改写、不可单独删除（只随所属分支级联消失）；artifact 写回是**条件更新**——只有当 `(kind, scope_id, based_on_revision_id, input_digest)` 与现存行一致时才覆盖，否则判为过期丢弃（stale）而不是覆盖。
- Inputs/outputs: 输入为原始列值与不透明载荷字符串；输出为行对象（含原始载荷字符串）或分类错误。
- State and exception handling: 同一 `branch_id` 的第二行快照必须被真实 SQLite 拒绝；`schema_version`/`digest`/`prompt_purpose`/`prompt_policy_version` 为空必须被拒绝；基线不匹配的写回判 FAIL 且**保留旧 payload**。
- Constraints and trade-offs: DDL 一律不在本任务（属 T-004）；时间用 UTC epoch millis；参数化 SQL；列名一律引用 `TreeLearnSchema` 常量；**不新增**任何正文列。
- AI recommendation and rationale: 见下面的「领域类型归属」——本任务不定义领域类型。
- **领域类型归属（用户 2026-09-21 明确裁定，非实现者的自由选择）**: `ContextSnapshot` 与 `AiArtifact` 两个领域类型分别归 **T-2026-09-20-immutable-context-snapshots-007** 与 **T-2026-09-20-derived-ai-artifacts-011**，`:core` 目前不存在这两个类型。因此本任务的 DAO **按「行 + 原始 JSON 载荷」形态实现**：DAO 只负责存储与约束，把 `payload` 当**不透明字符串**读写；载荷的 schema 与结构化解析（codec）属 T-007/T-011。用户裁定的三个选项中，另两个分别是「推迟到 T-007/T-011 之后再建」与「由本任务在 `:core` 补两个最小类型（会与 007/011 范围重叠）」，均未被采纳。
- User-confirmation decisions: ① 七张逻辑表及提示词用途/版本字段已确认；不新增第八张表。② 2026-09-21 裁定三片垂直切分 T-023。③ 2026-09-21 裁定两表 DAO 取「行 + 原始 JSON 载荷」形态。
- Acceptance evidence: instrumentation 唯一约束、不可改写、不可单独删除、CAS 基线复核与 stale 丢弃、scope 删除后的清理入口、不落正文。
- Facts: 技术方案 §5.3、§6.1/§6.2、§10；建表清单 §2/§3。
- Assumptions: `ai_artifacts.scope_id` 是多态外键（引用哪张表取决于 `kind`），因此**没有** FK 约束，scope 删除不会自动清理 artifact——清理是调用方义务。
- Unknowns: 无。
- Risks: 基线复核若在事务外「先查后写」会重新引入旧结果覆盖问题；载荷被当成可拼接的字符串处理会破坏 JSON 结构。
- Deferred: 载荷 schema 定义与结构化解析（T-007 codec / T-011 codec）；摘要/推荐的具体 use case（T-011）。
- Non-goals: 不定义 `ContextSnapshot`/`AiArtifact` 领域实体；不实现摘要、标题、推荐等业务用例；不实现网络与 UI。
- Skipped non-critical questions and reason: artifact 的保留策略由 T-011 决定。

## Goal

让出生快照的一次性写入与派生结果的条件写回在真实 Android SQLite 中按约束成立：快照不可改写且每分支唯一，派生结果只在基线仍有效时覆盖，基线失效时保留旧值并判为过期。

## Scope

### In scope
- `ContextSnapshotDao`：写入（`id`/`branch_id`/`schema_version`/`digest`/原样 `payload`/UTC 时间）、按 `branch_id` 读回**原始载荷字符串**、每分支唯一。
- `AiArtifactDao`：按 `(kind, scope_id)` 读回、**基线 CAS 写回**（`based_on_revision_id` + `input_digest` 一致才覆盖；返回「已应用 / 已过期丢弃」而不是布尔）、按 scope 删除 artifact 的清理入口。
- 两表的 `Cursor → 行对象` 只读映射。
- 复用 T-2026-09-21-023 的约束冲突 → 领域错误翻译 helper（若 023 尚未交付，本任务先按同一约定实现并在交付时改为复用）。

### Out of scope
- schema/DDL 与 `SQLiteOpenHelper`（T-004）。
- `topics`/`branches`/`messages`（T-2026-09-21-022）、`answer_revisions`/`generation_operations`（T-2026-09-21-023）。
- 首题原子创建事务（T-2026-09-21-025）。
- `ContextSnapshot`/`AiArtifact` 领域类型与载荷 schema/codec（T-007/T-011）。
- 摘要、标题、推荐等 use case（T-011）；上下文组装（T-007）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增两表持久化入口；不修改 message/revision/snapshot 的权威内容语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游 T-007 的 snapshot codec/digest 与 CreateBranch、T-011 的 artifact use case/Repository；下游 T-007 上下文组装、T-011 首页与导航消费、T-006 网关（快照 id 写入 `branches.context_snapshot_id`，两步写属 T-2026-09-21-025 与 T-009）。
- Native bridge or independent-client compatibility/update requirements: 无桥接；只消费 Android framework SQLite。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 两表列名、`uq_context_snapshots_branch` 唯一索引、`idx_ai_artifacts_kind_scope`、快照触发器（不可改写 / 不可单独删除）、artifact 的 CAS 语义与「scope 删除不自动清理」的边界。
- Authoritative code/config/migration evidence: `android/data` 的 DAO 源码、`:app` 的 instrumentation 夹具。
- Documentation surfaces checked: 技术方案 §5.3、§6.1/§6.2、§10；建表清单 §2/§3；`android/README.md`。
- Documentation files updated: `android/README.md` 的持久化入口索引与「多态 scope 不自动清理」边界。
- Producer/consumer/sibling/compatibility paths checked: 两表全部 DAO 方法、快照级联路径、T-007/T-011 的调用点。
- Superseded phrases/examples reverse-searched: 字符串截取解析 JSON 的旧做法（T-011 的 Scope 已把「字符串截取 JSON」列为被取代表述）、旧列名。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写冻结 PRD。

## Acceptance criteria

- [ ] 同一 instrumentation 命令先 RED 后 GREEN。
- [ ] `context_snapshots`：同一 `branch_id` 的第二行被真实 SQLite 拒绝（按扩展码断言成因）；`digest` 为空、`payload` 为空、`schema_version <= 0` 均被拒绝；行被 `UPDATE` 时触发器拒绝；分支仍存在时单独 `DELETE` 快照被拒；删除分支后快照级联消失。
- [ ] `context_snapshots` 读回必须经**结构化 JSON 解析**（真机用 `org.json`/等价结构化 API 解析后断言字段），**不得**用字符串拼接或子串截取构造载荷；用例须断言「载荷经过一次真实 parse 往返后仍与写入值等价」。
- [ ] `ai_artifacts`：`prompt_purpose` 为空、`prompt_policy_version` 为空、`payload` 为空均被拒绝。
- [ ] `ai_artifacts` **基线 CAS**：基线一致时覆盖并返回「已应用」；`based_on_revision_id` 不匹配、`input_digest` 不匹配、或该 `(kind, scope_id)` 尚无行而实际已过期时返回「已过期丢弃」，且**旧 payload 逐字节未变**。
- [ ] `ai_artifacts` 按 scope 的清理入口：删除 scope 后该 scope 的 artifact 行数为 0，且**不误删**其它 scope 的同 kind 行（多态 scope 无 FK，故必须有真机证据而不是依赖级联）。
- [ ] 关闭重开后逐字段相等（含 UTC 毫秒时间点与载荷字符串），且 `PRAGMA foreign_key_check` 无结果。
- [ ] 相邻失败场景：载荷是**非法 JSON** 时写入路径的行为被明确断言（本层不解析载荷，因此必须证明非法载荷要么被拒要么原样存储并由上层解析失败——二选一必须写清且有用例，不得含糊）。
- [ ] **不落 system 正文**：本任务所有 DAO 的公开方法签名中不出现 `systemContent`/`systemPrompt` 形态的入参，也不新增任何正文列；对两表写入含哨兵子串的夹具后扫描其全部 TEXT 列，断言声明范围内的列不含正文哨兵。
- [ ] `db-migration-reviewer` 按 Android SQLite 守门复核两表约束、级联、CAS 语义与唯一索引证据，不得用替身数据库。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增 `:app` androidTest 用例类 `ContextSnapshotAiArtifactDaoInstrumentedTest`（12 例：快照往返、每分支唯一、不可改写/不可单独删除、结构化解析往返、artifact 条件 insert、基线 CAS、清空基线后永不写回、scope 清理、非法 JSON 原样存储、正文哨兵扫描）
- Command: cd android; .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: 两个新 DAO 暂时移出源码树后，javac 自报 61 个错误，全部是这两个类的符号缺失（`找不到符号：类 AiArtifactDao / ContextSnapshotDao` 及其 import、`程序包 AiArtifactDao 不存在`）
- Failure reason validated: 61 个错误逐一落在本任务新增的 `ContextSnapshotDao`/`AiArtifactDao` 上，没有第二个模块的缺失符号；把两个文件放回后同一条命令转绿，说明失败由缺失实现导致而不是环境

### GREEN — minimum implementation
- Implementation: `ContextSnapshotDao`（Row + insert + findByBranchId）与 `AiArtifactDao`（Row + 条件 insert + findByKindAndScope + 基线 CAS `applyIfBaselineMatches` + `deleteByScope`）
- Command: cd android; .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-024-green.txt（`BUILD SUCCESSFUL in 39s`）；JUnit XML `android/app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml`：tests=64 failures=0 errors=0 skipped=0，其中本任务 12 例（逐例名单见 artifacts/evidence/T-024-tests.txt）
- Regression evidence: 同一 XML 的 64 例即 T-004/T-022/T-023 既有套件的回归（0 failed / 0 error）；Java-only 守门 `VERDICT: PASS (13 OK line(s))`，见 artifacts/evidence/T-024-guard.txt。**仓库级 `gradlew check` 本次没有拿到干净证据**：并行 lane（T-020）当时正在 `:ai` 写自己的 RED，`:ai:compileDebugUnitTestJavaWithJavac` 因对方尚未写完的测试源码 FAILED，该次失败已如实留存为 artifacts/evidence/T-024-regression-interference.txt（不是本任务的红）。仓库级 check 在 lane 静默后由验收阶段重跑

### REFACTOR — optional
- Cleanup: 修掉一处夹具缺陷：`artifactWithAClearedBaselineNeverAcceptsAWriteback` 原本直接 `DELETE FROM answer_revisions`，被 `trg_answer_revisions_no_direct_delete` 以 code 1811 拒绝；改为删除所属 message、让 revision 走级联后，`ON DELETE SET NULL` 才真正发生（这正是该用例要观测的行为）
- Regression evidence: 同一条命令重跑到 GREEN（artifacts/evidence/T-024-green.txt），64 例全绿

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required；规模按用户 2026-09-21 裁定的三片切分控制在 500 行硬限内
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- instrumentation 测试写在 `:app` 的 androidTest 源集（项目约定：单一测试 APK），不在 `:data` 声明 androidTest 依赖。
- 列名一律引用 T-004 的 schema 常量，不写字面量字符串。
- `payload` 在本层是**不透明字符串**：本层不得出现任何「按字符位置取子串」或「拼接出合法 JSON」的构造；唯一允许的解析动作发生在**测试**里（用于断言结构），以及 T-007/T-011 的 codec 里。
- CAS 必须在**写回语句自身**（`UPDATE … WHERE based_on_revision_id = ? AND input_digest = ?`）里完成，并依据受影响行数判定「已应用 / 已过期丢弃」。**禁止**「先 SELECT 判断、再无条件 UPDATE」——那是把基线复核放到事务外，正是要修的问题。
- 过期丢弃不是失败：它是正常路径，必须带可排障日志（kind、随机 ID、digest 前缀、状态），且日志不得包含载荷内容。
- scope 清理是调用方义务（多态 scope 无法用 FK 表达）；本任务提供入口并断言不误删。
- **实现期新增的两个决定（供 verifier 复核）**：① `AiArtifactDao.insert` 写成带 `WHERE NOT EXISTS` 的单条 `INSERT … SELECT`，并用 `SELECT changes()` 读受影响行数——不加这条，CAS 的「受影响行数 = 1」就没有意义（同一个 (kind, scope) 可能出现两行，写回时「当前载荷」不存在）；它是单条语句，因此不存在被禁止的「先查后写」。② 空 digest/payload/purpose/version 由构造器抛 `IllegalArgumentException` 而不是返回领域码：按 `:core` 的 error-channel 规则，形状错误是调用方 bug；库层对 `NULL` 的 NOT NULL 拒绝仍单独有用例（用原始 SQL 绕过构造器触发，断言 code 1299）。快照的唯一冲突同样**不**翻译成领域码——`:core` 没有对应业务码，按 `ConstraintFailures` 的既有约定「无法命名的拒绝必须原样重抛」。

## Verification report

### verifier #3 批量复核（2026-09-24，独立于本任务实现者）

- Verdict: **PASS（设备项 deferred）**。verifier #2 的 PARTIAL 除设备项外已无遗留；本轮新鲜门禁全绿。
- Fresh evidence（2026-09-24）: `artifacts/evidence/P3-B1-gates-2026-09-24.txt`（同批次，EXITCODE=0，全库 1725 tests / 0 失败）；`P3-B1-no-kotlin-2026-09-24.txt`（EXITCODE=0）；`P3-B1-rehydration-probes-2026-09-24.txt` R6（DAO 层 `systemContent`/`systemPrompt` 零命中）。补充探针：T-045 收尾（2026-09-23）为 `AiArtifactDao` 新增了消费侧读取（`DerivedService`/`SqliteDerivedStore`），其 CAS/写回语义沿用本任务交付的 `applyIfBaselineMatches`，无 API 改动——消费侧落地反而补上了本任务「写入侧无调用者」的长期背景缺口。
- 判定依据: 唯一约束/触发器/CAS/清理入口均为 verifier #2 已核对证据，本轮无新事实推翻；规模 205 < 500 维持。
- Gaps（不变，均 deferred）: instrumentation 执行项（真实 SQLite 拒绝/CAS 覆盖/多态 scope 清理不误删）—— `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`，用例已编译。

> 独立 verifier 复核（verifier #2，2026-09-21），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；凡严格需要真机的验收项一律记为
> `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY2-*.txt`（helper 脚本在 `artifacts/evidence/.verify2/`）。

- Verdict: PARTIAL
- 审阅文件: 本任务文件；`android/data/.../db/dao/{ContextSnapshotDao,AiArtifactDao}.java`；
  `:app` 的 `ContextSnapshotAiArtifactDaoInstrumentedTest.java`；证据
  `T-024-{red,green,tests,guard,regression-interference}.txt`；verifier 证据
  `VERIFY2-{assemble-androidtest,guard,lint,sizing,static-findings}.txt`。
- 代码量: **205** 非空非注释行（verifier 独立复算：`AiArtifactDao` 138 + `ContextSnapshotDao` 67；
  物理行 338）/ 500 硬上限 → **在预算内**（超 200 软上限，警告）。
- 任务合规: PASS（template_version 2；id/文件名一致；`status: verifying`；RED/GREEN 键为纯值且两条
  `Command` 逐字节相同；`validate_tasks.py` PASS）。
- Lint: `:app:lintDebug` PASS（EXITCODE=0）；本任务无独立 lint 证据文件，由 verifier 补跑覆盖。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`）。
- 验收点对照:
  - [x] 先 RED 后 GREEN（编译级）— RED 自述 61 个错误全部落在两个新 DAO 的符号缺失上，
        与「把两个文件暂时移出源码树」这一操作相符。
  - [x] 快照不可改写/不可单独删除、每分支唯一、CAS 写回与 stale 丢弃、scope 清理不误删 —
        由 12 个 instrumentation 用例设计覆盖（含 `artifactWithAClearedBaselineNeverAcceptsAWriteback`、
        `illegalJsonPayloadIsStoredVerbatimAndFailsOnlyWhenParsed`、`onlyPayloadColumnsCanCarryBodyText`）。
  - [x] 「本层不解析载荷」— 代码层成立：`payload` 只作不透明字符串传递，结构化解析只在 `:core`
        `ContextSnapshotCodec`（README:235 已把该边界写成当前态事实）。
  - [ ] **instrumentation 执行** — **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。
        最强替代：`:app:compileDebugAndroidTestJavaWithJavac` **真实执行**并产出
        `ContextSnapshotAiArtifactDaoInstrumentedTest.class`（含内部类），EXITCODE=0
        （`VERIFY2-assemble-androidtest.txt`）。**只证明可编译，未执行任何用例。**
  - [x] 仓库级 `gradlew check` 的缺口被如实留存 — `T-024-regression-interference.txt` 记录的是
        **并行 lane 在 `:ai` 写 RED 时** `:ai:compileDebugUnitTestJavaWithJavac` 失败，
        不是本任务的红；verifier 独立复跑 `:core:test :ai:testDebugUnitTest :tree-layout:test`
        （`--rerun-tasks`）在 lane 静默后全绿（`:core` 154、`:ai` 72、`:tree-layout` 34，0 失败），
        该干扰项已消解。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）；
  `db-migration-reviewer` 对本任务 DDL 约束/级联/CAS 的复核由 T-004/T-022 的守门轮次覆盖，
  本任务未改 DDL。
- 文档影响记录: PASS（README:235 `ContextSnapshotDao`、README:236 `AiArtifactDao` 两行经 verifier 复核存在，
  且已写明「多态 scope 无外键、删除 scope 不自动清理」这一边界）。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: 无代码级修复指令。设备可用时整包跑 `:app:connectedDebugAndroidTest`，确认本任务 12 例
  在列且 `failures="0" errors="0"`；注意该 XML 每次运行被最后一个 lane 覆盖，须在跑完后立刻存档。
- 阻塞点与剩余风险: 设备执行 deferred；`ai_artifacts.scope_id` 是多态键、无外键，清理是调用方义务，
  只能靠 `deleteByScope` 的用例保证，未经真机执行前该保证未闭环。

## Handoff notes

- Completed: `ContextSnapshotDao`（行 + 不透明载荷；每分支唯一由 `uq_context_snapshots_branch` 兜底）；`AiArtifactDao`（条件 insert、基线 CAS、多态 scope 清理入口）；`:app` 新增 12 例真机证据；`android/README.md` 的 `:data` 持久化入口索引已补两行；T-022 测试里两处「快照 DAO 属于 023」的过时注释已更正为 024。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 完成后解锁 T-2026-09-20-immutable-context-snapshots-007（快照 codec 建立在 `ContextSnapshotDao` 之上）、T-2026-09-20-derived-ai-artifacts-011（CAS Repository 建立在 `AiArtifactDao` 之上）、T-2026-09-20-topic-branch-lifecycle-009（创建分支时写快照）。
- Watch-outs:
  - 本任务的 DAO **不是** T-007/T-011 的领域 Repository；两者之间隔一层 codec。把载荷 schema 知识写进本层会造成两处 schema 真相。
  - `ai_artifacts.based_on_revision_id` 是 `ON DELETE SET NULL`。**已钉住的取舍**：被清空基线的 artifact 永远判 stale 且保留旧 payload——重新采纳它会让一条过时摘要活得比它描述的答案更久；需要重新派生时必须显式 delete + insert。用例 `artifactWithAClearedBaselineNeverAcceptsAWriteback`。
  - `insert` 的条件写让「每个 (kind, scope) 一行」成为事实，CAS 的「受影响行数 = 1」才有意义；若将来有人改成无条件 insert，`applyIfBaselineMatches` 会在 >1 行时**抛错**而不是静默覆盖。这条链不要拆。
  - 两表的删除语义不同，清理代码不要混：快照**只能**随分支级联消失（触发器拒绝单独 DELETE），artifact **必须**由调用方 `deleteByScope` 清理（多态 scope 无外键）。
