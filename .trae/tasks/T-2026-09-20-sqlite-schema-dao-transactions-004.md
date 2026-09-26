# Treelearn Harness Task: 建立 SQLite schema、helper、迁移与事务边界

id: T-2026-09-20-004
title: 建立 SQLite schema、helper、迁移与事务边界
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 300
shared_code_touched: true
depends_on: [T-2026-09-20-model-immutable-answer-domain-003.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 七类本地事实必须以可迁移、可事务验证的 SQLite 结构持久化。
- Problem/background: 主题树、不可变回答和流式操作若分散写入，会出现空树、悬空版本及恢复不一致。
- Users/scenarios: 用户创建主题、追问、分叉、重试、删除后重启应用，数据仍保持一致。
- Expected outcome: `:data` 提供七表 schema、前向迁移与事务入口（DAO 由 022/023 两个切片承担）。
- Core flow and reversibility: 新装建库；升级只追加迁移；事务失败完整回滚；删除按已确认范围级联。
- Inputs/outputs: 输入为 schema 常量与迁移版本号；输出为可打开的数据库、可提交/回滚的事务和 schema 级验证结果。
- State and exception handling: 开启外键；拒绝未知 schema 版本；连接或约束异常不得伪装成功。
- Constraints and trade-offs: UUID、UTC epoch millis、参数化 SQL；DDL 只允许集中在 SQLiteOpenHelper/schema migration 入口，禁止业务路径 DDL 和单表 JSON 聚合。
- AI recommendation and rationale: SQLiteOpenHelper + 集中 schema 常量 + 显式事务 runner，保持 Java-only。
- User choice and custom additions: 推理、最终回答和失败部分内容均须落库；只落提示词用途/版本，不落 system 提示词正文。
- AI-autonomous decisions: schema 版本从 1 开始，迁移 fixture 覆盖每个已发布版本。
- User-confirmation decisions: 七张逻辑表及本地优先架构已确认；2026-09-21 确认在既有表增加提示词用途/版本字段，不新增第八张表。
- Acceptance evidence: instrumentation 新装/升级/回滚/重开测试与 schema 直接检查。
- Facts: 技术方案 §6 与 SQLite 建表入场清单已记录每张永久表及提示词版本字段的必要性、生产者、消费者和生命周期。
- Assumptions: 首个发行版尚无真实用户旧库，但仍建立可持续迁移机制。
- Unknowns: 无。
- Risks: SQLite 外键默认关闭；部分唯一索引在旧版本上的可用性需在 API 36 实测确认。
- Deferred: n-gram 搜索索引和云同步字段。
- Non-goals: 不实现 UI、HTTP、业务用例编排与各表 DAO。
- Skipped non-critical questions and reason: 索引微调由真实查询计划决定，不改变 schema 权威事实。

## Goal

让七类权威事实的 schema、迁移与事务边界在真实 Android SQLite 中可直接打开、前向升级、原子提交与完整回滚（行的读写由拆分后的 DAO 任务承担）。

## Scope

### In scope
- `TreeLearnSchema` 常量（表名、列名、DDL、索引与约束）、`TreeLearnDatabaseHelper`（`onCreate`/`onUpgrade`、`PRAGMA foreign_keys=ON`）、集中式事务 runner，以及 schema 级 instrumentation 验证。

### Out of scope
- 七表 DAO 与 `Cursor → 实体` 映射（T-2026-09-21-022 三表、T-2026-09-21-023 两表、T-2026-09-21-024 两表）、Repository 适配、首题原子业务事务（T-2026-09-21-025）、DeepSeek、Keystore、页面和搜索排序策略。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增持久化骨架；保持 core 的不可变和合法状态迁移约束。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游 core 实体（列定义依据）；下游 T-2026-09-21-022、T-2026-09-21-023、T-2026-09-21-024、T-2026-09-21-025 及各 DAO 消费者。
- Native bridge or independent-client compatibility/update requirements: 无独立客户端；只消费 Android SQLite。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 数据库版本、表列、索引、外键、事务与删除行为。
- Authoritative code/config/migration evidence: `android/data` schema、helper、DAO 与 instrumentation fixture。
- Documentation surfaces checked: 技术方案 §6/§9.2、SQLite 建表入场清单、提示词设计 V1.0、`android/README.md`、schema 说明。
- Documentation files updated: 若实际列名或迁移步骤与方案不同，同步技术方案和 Android README。
- Producer/consumer/sibling/compatibility paths checked: schema 常量 → helper `onCreate`/`onUpgrade` → 事务 runner 的调用链，以及 022/023 对列名常量的引用点。
- Superseded phrases/examples reverse-searched: 旧 schema 版本、列名和表清单。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写冻结 PRD；仅记录工程事实。

## Acceptance criteria

> 2026-09-21 收窄：原第 2/3/4 条的行级写入、首题原子性与再水合契约已下推到 T-2026-09-21-022 / -023 / -024 / -025（023 已于当日按用户裁定二次切分为三片）；本任务只对 **schema、迁移与事务骨架** 本身负责。

- [ ] 同一 instrumentation 命令先 RED 后 GREEN，覆盖新装、重开、逐版本升级及失败回滚。
- [ ] 新装走**生产 `onCreate`**：`sqlite_master` 中恰好 7 张永久表（名称与建表清单 §2 完全一致），`PRAGMA user_version` 为 1。
- [ ] 每张表的列名/类型/`NOT NULL` 与清单 §2、技术方案 §6.1 逐项一致：用 `PRAGMA table_info` 的实测结果与期望清单做**集合比较**，不得用字符串包含判断。
- [ ] 关键索引与约束**存在且生效**：`messages(branch_id, ordinal)` 唯一、`branches` 每主题单根、`answer_revisions(message_id, revision_no)` 唯一、`context_snapshots(branch_id)` 唯一、`ai_artifacts(kind, scope_id)` 索引，以及 `generation_operations` 每分支单活动操作的**偏索引**（须在 API 36 真实 SQLite 上实测该 DDL 可用）。
- [ ] 每次打开数据库 `PRAGMA foreign_keys` 读回为 1；`PRAGMA foreign_key_check` 无结果；`PRAGMA integrity_check` 为 `ok`。
- [ ] **升级路径（本版本为 N/A，但机制必须可验）**：V1 是首个发布版本、无已发布前驱，按 `.trae/rules/sql.md:40`「首个尚未发布版本可将升级路径记为 N/A」记为 N/A，**不得编造前驱版本或假迁移来制造证据**。替代证据：机械断言 `onUpgrade` 存在且委托给前向迁移注册表；注册表对 `1..DATABASE_VERSION-1` 形成**连续、无缺口的 +1 链**；`DATABASE_VERSION` 为 1 且 DDL 只与当前版本绑定。一旦 `DATABASE_VERSION` 升到 2，本项自动转为「必须走真实 `onUpgrade` 实测才有证据」。
- [ ] **已有 v0 文件的打开路径**：对一个 `PRAGMA user_version = 0` 且无表的既存库文件，经生产 helper 打开后走 `onCreate` 建出 7 张表并把 `user_version` 置为 1（这是真实 Android 上「首次安装前的空库文件」路径，可实测）。
- [ ] 事务 runner 语义：抛异常时整体回滚（提交后行数为 0），正常提交后可见；重复开启被拒绝而不是静默并入外层事务。
- [ ] 时间列全部为 `INTEGER` 且由应用显式写入 UTC epoch millis；schema DDL 中不出现 `CURRENT_TIMESTAMP`、`datetime('now')` 或设备本地时区函数。
- [ ] **不落 system 正文（schema 级）**：对 `PRAGMA table_info` 得到的**全部列名**做机械检索，断言不存在 system 提示词正文语义的专用列（列名含 `system` 的命中数为 0；含 `prompt` 的命中只允许是 `prompt_purpose` 与 `prompt_policy_version`）。写入后回读的哨兵子串全表扫描需要真实写路径，由 T-2026-09-21-023（两表）与 T-2026-09-21-024（两表）在其 DAO 验收中执行。
- [ ] **DDL 位置**：对 `android/**` 生产源码机械检索 `CREATE TABLE`/`ALTER TABLE`/`CREATE INDEX`，除 helper 与 schema 常量外零命中。
- [ ] `db-migration-reviewer` 按 Android SQLite 守门复核永久表入场、schema version、新装/升级/重开、PRAGMA、事务/约束与级联证据；不得用 MySQL 或内存替身代替。

## Guard review findings and fixes (2026-09-21)

`db-migration-reviewer` 与独立 `verifier` 并行复核后均判 **FAIL**。下面的每一条都先自行核验再修，不是照单全收；核验依据一并写出，便于复核者回读。

### D1 — `answer_revisions` 缺两列，023 的 DAO 将永远无法再水合（真实缺陷，已修）

- 指控：DDL 缺 `model` 与 `thinking_enabled`。
- 核验：`android/core/.../AnswerRevision.java` 的 `create` 与 `restore` **都**要求这两个字段——不是「最好有」，是构造签名的一部分。缺列则 `:core` 实体无法从行重建。
- 权威：技术方案 §5.1「一条稳定 revision 保存模型与思考模式」。
- 修复：`ANSWER_REVISIONS_DEFINITIONS` 增加 `model TEXT NOT NULL` 与 `thinking_enabled INTEGER NOT NULL CHECK (... IN (0,1))`；验收期望列集同步；`requiredIdentityColumnsRejectNull` 增加「revision 缺 model 必须报 NOT NULL」的断言。

### D2 — `branches` 为「未读」造了第二权威（真实缺陷，已修）

- 指控：`unread_from_ordinal` 违反 §11。
- 核验：`:core` 的 `Branch` 用 `latestStableSequence` / `lastSeenStableSequence`，`unreadCount` 是**派生值**。
- 权威：技术方案 §11 明确「未读由两者比较得出，**不存独立布尔/计数权威值**」。
- 修复：删除 `unread_from_ordinal` 等未读专用列，改为 §11 的序列对；新增用例 `unreadHasASingleRepresentationInTheSequencePair`，机械断言 `branches` 全部列名中不得出现 `unread`，且 `last_seen <= latest` 由 CHECK 强制。

### D3 — 约束断言不区分成因（假见证，已修）

- 问题：原用 `assertThrows` 只断言「抛了约束异常」。`foreignKeysAreEnforcedNotMerelyDeclared` 里那条 role CHECK 断言实际是因外键不存在而通过——断言过、不变量没被触及。
- 修复：引入 `assertCause(scenario, message, expectedCode, requiredText)`，按 SQLite **扩展错误码**判定成因，并同时断言命中的具体约束。
- 实测错误码（探针 `probeConstraintCodes`，证据 `artifacts/evidence/T-004-constraint-code-probe.txt`，探针取完值后已删除）：`1299` NOT NULL、`275` CHECK、`2067` UNIQUE、`787` FOREIGN KEY、`1811` TRIGGER。

### D4 — 事务 runner 的单一边界可被工作体绕过（真实缺陷，已修）

- 问题：Android 的 `beginTransaction()` 在已有事务中会静默并入外层，所以工作体自行 `setTransactionSuccessful()` + `endTransaction()` 就能提前提交，绕过 runner 承诺的唯一边界。
- 修复：`run` 在工作体返回后检查 `database.inTransaction()`，若事务已被结束则抛 `IllegalStateException`；`finally` 只在事务仍开着时才 `endTransaction()`，避免用「no current transaction」掩盖真实原因。
- 证据：`transactionRunnerRejectsNestedOpenAndStolenTransactions` 同时覆盖嵌套开启与被窃事务两种情形。

### D5 — 成因判据本身被约束文本欺骗（本轮自查发现，verifier 未报）

- 现象：改完 D3 后 `messageContentIsConstrainedByRole` 失败——断言写的是「含 `CHECK` 且不含 `NOT NULL`」，而该 CHECK 的表达式文本里恰好有 `user_content IS NOT NULL`，两个方向同时满足，判据失效。
- 教训：用**约束的措辞**去判定**约束的类别**，等于让被判对象自己定义判据。
- 修复：判据全面改为错误码 + 具体约束文本的组合；两条都以 `parent_id` 起头的分支 CHECK 改用互斥的后续片段（`source_message_id IS NOT NULL` vs `source_message_id IS NULL`）区分，否则无法分辨是哪一条生效。

### 第三轮复核发现（2026-09-21 下，两个守门并行，均判 PARTIAL）

上一轮我判「技术面已闭环」，两个守门都指出这话说早了。逐条核验后**全部成立**，已修：

### D6 — 「onUpgrade 委托给注册表」是**空见证**（高，两个守门独立命中）

- 问题：`helper.onUpgrade(db, 1, 1)` 让 `applyForwardMigrations` 的循环跑零次，所以一个**完全空实现**的 `onUpgrade` 也能让这条断言通过。而它是「升级路径记为 N/A」的**唯一**替代证据——N/A 是合规的，但托底它的证据必须可证伪。
- 修复：改为 `helper.onUpgrade(db, 1, 2)`，断言抛 `no forward migration is registered from schema version 1`。
- 同时补 D7 的 `user_version` 断言。
- **「可证伪」不是自称的**：把 `onUpgrade` 方法体清空后重跑，该用例**转红**（24 例 / 1 failure，失败信息 `the database accepted an operation it must reject`），还原后转绿。证据 `artifacts/evidence/T-004-mutation-onupgrade.txt`。（两个守门都声明本轮未做变异测试、判断来自静态推导，所以这条由我补上。）

### D7 — fail-closed 未证伪真正的危害（中高）

- 问题：只断言异常消息，没有断言「半迁移的库没被盖上新版本号」——而那才是 `rules/sql.md` 禁止的事。
- 修复：失败后追加 `assertEquals(1, PRAGMA user_version)` 与 `assertEquals(EXPECTED_TABLES, actualTables(db))`。

### D8 — 「迁移链无缺口」断言空真且有两处 off-by-one（中低）

- 问题：注册表现在是空的，循环零次，末尾断言被 `if (visited > 0)` 包住 → 空真；且 `previousTo` 初值为 0、末尾写 `previousTo + 1`，一旦 `DATABASE_VERSION` 升到 2，这段**不是开始生效，而是误报失败**。
- 修复：初值改为 `1`；末尾改为无条件 `assertEquals(DATABASE_VERSION, previousTo)`。空注册表时 `1 == 1` 通过，若有人 bump 版本却不注册迁移立刻红。

### D9 — 事务 runner 的措辞暗示了它做不到的保证（中）

- 问题：javadoc 与任务文件都写得像「边界无法被绕过」，实际是**事后检测**——工作体拿到同一个 `SQLiteDatabase`，自行 `endTransaction()` 会真提交，runner 只能在事后抛错。下游 023 若照「无法绕过」写代码，会误以为异常等于回滚而吞掉它。
- 修复：javadoc 明确写「检测而非阻止」+ 为什么平台 API 阻止不了 + 调用方该如何理解该异常；README 同步；用例钉死语义——工作体写入一行后自己提交，断言该行**确实可见**。

### D10 — 夹具自己在制造违反 §5.2 的行（中）

- 问题：`insertChildBranch` 不写 `context_snapshot_id`，而 §5.2 把 `contextSnapshotId` 列为非根分支必存字段，`:core` 的 `BranchSource.restore` 对它走 `Guard.id`（null 即抛）。即：验收套件插的每一条「合法分叉」都是 `:core` 再水合不了的。
- 修复：夹具改为两步（分支 → 快照 → 回填），级联用例里手工插的快照随之删除（每分支只允许一个快照，否则 `uq_context_snapshots_branch` 会先炸）。
- 残留：schema 层**无法**用 `NOT NULL` 强制（两个外键互为依赖），已作为 Gaps 与 Watch-outs 下推 023。

### D12 — `branches` 的 CHECK 让整条回答分叉永远写不进去（**由 T-022 实现时暴露，已修**）

- 发现方式：不是审出来的，是 T-022 的验收套件在真实设备上第一次尝试写一条「整条回答分叉」的子分支时被约束打回。
- 根因：本任务的 CHECK 要求非根分支 `anchor_hash IS NOT NULL`，而 `:core` 的 `BranchAnchor.wholeAnswer()` **按设计**让 `quote`/`hash` 全为 null（`BranchAnchor.java:14-24` 的字段注释明确写了 "null for a whole-answer fork"）。技术方案 §5.2 的原文是「整条回答分叉时偏移为空，但**版本引用**仍必填」——「版本引用」指 `sourceMessageId`/`sourceRevisionId`，**不是** anchor hash。我把这句话实现宽了。
- 后果：**每一种整条回答分叉都写不进去**，而这是产品的核心操作之一；而 DDL 层的 CHECK 违反在开发期表现为 insert 抛异常，很容易被误读成「夹具写错了」。
- 修复：CHECK 改为只强制 `source_message_id` + `source_revision_id`；ranged 分叉仍存 `quote`/`hash`，而「有偏移却没有回退定位」继续由领域层的 `ANCHOR_INVALID` 拒绝（`BranchAnchor.ofRange`），不由库层兜底。
- 教训：**schema 服从领域模型，不是反过来**。当库层约束与领域类型的公开语义冲突时，先问「哪一边是权威事实」，而不是让调用方去迁就 DDL。

### D11 — 证据与检索式缺陷（低，但都是「证明不了自己」那类）

- DDL 检索式漏了 `CREATE UNIQUE INDEX`（不含 `CREATE INDEX` 子串）、`CREATE TRIGGER` 与全部 `DROP` → 补全后 17 处命中仍全在 `TreeLearnSchema.java`。
- 五码探针的输出在 **JUnit XML** 里，我落盘的却是 Gradle 控制台输出（只有「there were failing tests」），`2067`/`787`/`1811` 零命中 → 重跑探针并把 XML 片段抄进证据文件，五个码现在都 grep 得到。
- `T-004-tests.txt` 只有用例名、没有计数行 → 追加 JUnit XML 统计行与 XML 路径。
- `README.md` 的 debug APK「3 个 dex」已被 `:data` 入库变成 4 个 → 同步；补两条「禁止直接删除」触发器；runner 能力边界写入 README。
- 任务文件存在两段 `## Verification report`（后一段仍 `PENDING`）→ 删除后一段。
> **最新结论：见本段末尾「第九轮独立复核结论」（verifier #2，2026-09-21 第四轮）。下方第一个 - Verdict: 是历史轮次的记录，不是当前判定。**

### D13 — D12 的修法本身放宽过头，且暴露了两个夹具缺陷（**由 T-022 的第一轮守门复核暴露，已修**）

这一轮由 `db-migration-reviewer` 在复核 T-022 时命中（记为 H1），但缺陷在**本任务**的 DDL 与套件里，因此记在这里。

1. **【严重】去掉 `anchor_hash` 必填后，半截锚点变成合法行。** D12 的修法只做减法，没有补上原本被那条 CHECK「顺带」挡住的三类行：`start≠NULL,end=NULL`（读路径 `Integer` 拆箱 **NPE**）、`start/end` 有而 quote/hash 无（`ofRange` 失败后 `.value()` 抛裸 `IllegalStateException`，丢掉了 `ANCHOR_INVALID`）、以及最坏的 `start=NULL` 而 quote/hash 非空（`readSource` 只看 `start == null`，**静默按整条回答分叉读回，quote/hash 被丢弃**）。这不是理论行：`readSource` 的 `ofRange` 分支此前**在任何一条测试里都没被执行过**。
   - 修复：`branches` 增加**四字段同生同灭** CHECK（全空 = 整条回答分叉；全非空 = ranged 且 `start < end`），并把原先独立的 `start <= end` range CHECK **合并进同一条**——两条重叠的锚点 CHECK 会让库层对同一个值判两次，而「是哪一条报的」变成声明顺序的产物。
   - 划界依据：**分组不变量归库层**（「这四列要么一起有要么一起没有」没有一个领域对象在写路径上单点判定，且 023 的夹具与未来 DAO 会用原始 SQL 写这张表）；**内容不变量归领域层**（quote/hash 非空、quote 长度须等于偏移跨度，由 `BranchAnchor.ofRange` 单点判定，读路径调用它并把拒绝原样抛出）。同一条规则不写在两个地方。
2. **【中】`insertChildBranch` 夹具在批量制造领域层产不出的行。** 它写 `anchor_quote`/`anchor_hash` 却不写两个偏移列——正是新的四字段 CHECK 要堵的形态，于是 T-004 套件里三条级联/唯一约束用例当场变红。**约束抓对了，夹具错了**：夹具不得制造领域层永远无法交给存储的状态，否则测试是在一个不存在的状态上「通过」。已改为写**完整 ranged 锚点**（偏移 0–5、quote 长度与跨度一致）。
3. **【中】两个溯源约束夹具是「多缺陷输入」，靠 CHECK 声明顺序侥幸通过。** `foreignKeysAreEnforcedNotMerelyDeclared` 里「非根分支缺来源版本」与「根分支带来源」两个夹具，除了它们要测的那条 CHECK，还同时违反新的锚点 CHECK；它们之所以通过，只是因为溯源 CHECK 在 CREATE TABLE 里声明得更早。**一个违反两条约束的夹具无法为其中任何一条作证**（与失败案例库「多缺陷输入下业务码遮住调用方 bug」同一形态）。已改为**单缺陷输入**：前者去掉 `anchor_hash`，后者改用一条**可解析的** `source_message_id`（并把「source 须同主题」这条已知边界写成注释——见 Gaps）。
4. **补新用例 `forkAnchorColumnsAreAllOrNothing`**：两类合法形态（整条回答分叉、ranged）必须可写，六种坏形态（四种半截 + 空区间 + 逆向区间）必须按 `code 275` 被拒。**只断言拒绝是不够的**——那会被「一条拒绝一切的 CHECK」满足，而这正是 D12 的成因。
5. 上一轮那条「D12 的修法」在 T-022 任务文件里留下的表述（「不由库层兜底」）已同步更正，避免文档与代码互相矛盾。

## TDD execution record

### RED — failing test first
- Test/scenario: `:app` 的 `androidTest/com/treelearn/app/SqliteSchemaInstrumentedTest.java`——本任务验收规格的完整实现（新装、列集合、PRAGMA、索引与约束、级联、事务 runner、不可变性、重开、v0 空库文件），写于任何生产类之前。
- Command: ./gradlew :app:connectedDebugAndroidTest --console=plain
- Exit code: 1
- Observed failure: `:app:compileDebugAndroidTestJavaWithJavac FAILED`，javac 自报 **24 个错误**，构成为 `找不到符号` **18** + `程序包com.treelearn.data.db不存在` **3** + `程序包TreeLearnSchema不存在` **3**（18+3+3=24）；全部落在三个尚未存在的生产类型上（`TreeLearnDatabaseHelper`、`TreeLearnSchema`、`DatabaseTransactionRunner`）。
- Failure reason validated: 每条编译错误的名字都是待建生产类型本身，无第二成因。**证据强度如实标注：这是编译级 RED，不是行为级 RED。** 它证明「能力缺失」，不证明「观察到了某个具体错误行为再修好」。证据 `artifacts/evidence/T-004-red.txt` **可复现而非凭记忆**：三个生产类被物理移出源码树后重跑同一命令取得，随后还原并重建 GREEN；文件内含 javac 原始错误行、去重计数与编译器自报行。（本条原先写 21 errors 且无法从任何产物回推——那是旧套件的数字，已按第三轮 F13 重录。）

  ```
  $ $e = Get-Content artifacts/evidence/T-004-red.txt -Encoding UTF8 | Where-Object { $_ -match '^[A-Za-z]:\\.*\.java:\d+: 错误' }
  $ $e.Count
  24
  $ ($e | Where-Object { $_ -match '错误: 找不到符号' }).Count
  18
  $ ($e | Where-Object { $_ -match '错误: 程序包com\.treelearn\.data\.db不存在' }).Count
  3
  $ ($e | Where-Object { $_ -match '错误: 程序包TreeLearnSchema不存在' }).Count
  3
  ```
  该检索式锚定**顶格**错误行（`^[A-Za-z]:…`），因此不会把 Gradle 在 `FAILURE` 段缩进重印的同一批错误重复计入；被重印的那一份计数见 `T-004-red.txt` 的「Error lines as printed by Gradle」段（48 = 24 × 2）。
### GREEN — minimum implementation
- Implementation: 命令与 RED **逐字符相同**（同一份验收套件，RED 与 GREEN 之间不修改测试文件来「让它绿」）。新增 `:data` 三个生产类——`TreeLearnSchema`（七表表名/列名常量 + 全部 DDL + 索引 + 4 条不可变性触发器 + 前向迁移注册表，DDL 由列常量拼出）、`TreeLearnDatabaseHelper`（`onConfigure` 开启外键、`onOpen` 回读 `PRAGMA foreign_keys` 强制为 1、`onCreate` 执行 `CREATE_STATEMENTS`、`onUpgrade` 走前向迁移、`onDowngrade` 拒绝）、`DatabaseTransactionRunner`（显式事务边界，拒绝嵌套）。验收套件本身不修改（RED 与 GREEN 是同一份文件）。
- Command: ./gradlew :app:connectedDebugAndroidTest --console=plain
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`；JUnit XML `tests="52" failures="0" errors="0" skipped="0"`，其中 **24 例属本任务**（15 例是 T-022 的同批回归、1 例是 T-002 的启动测试，其余为 T-023 加入后同批运行的用例）。用例清单与 JUnit XML 统计行、路径、XML 的 SHA-256 见 `artifacts/evidence/T-004-tests.txt`；构建输出 `Starting 52 tests` / `Finished 52 tests` / `EXITCODE=0` 见 `artifacts/evidence/T-004-green.txt`（N6 修改后整份重出；头部为本次运行自带，不再是由补记头说明的旧捕获）。用例数由 21 → 24 → **24** 的沿革：守门复核后重写验收套件（新增 `unreadHasASingleRepresentationInTheSequencePair`、`transactionRunnerRejectsNestedOpenAndStolenTransactions`、`requiredIdentityColumnsRejectNull`），D13 又新增 `forkAnchorColumnsAreAllOrNothing`。
- Regression evidence: ① 既有 T-002 启动测试在同一次运行中通过（`mainActivityRendersFirstScreenWithoutCrash`）；② Java-only guard `VERDICT: PASS (13 OK line(s))`、exit 0（`artifacts/evidence/T-004-guard.txt`，其中 release APK 实测 `app-release-unsigned.apk` 56012 B / 1 dex，证明 `:data` 的生产代码确实进了包）；③ 守护门自检**完整模式** `OVERALL = SELFTEST PASS (40 case(s))`、`SELFTEST_EXITCODE=0`（`artifacts/evidence/T-004-guard-selftest.txt`，文件头含 `Generated:` / `Command:` 与 Guard、Library 的 SHA-256 及行数）；④ DDL 位置机械检索仅 `TreeLearnSchema.java` 命中（`artifacts/evidence/T-004-ddl-location.txt`）；⑤ `:core:test` `--rerun` 后 **131 例 / 0 失败 / 0 错误 / 0 跳过**（逐文件计数写在同一份证据里，取自 JUnit XML 而非控制台输出）。

### REFACTOR — optional
- Cleanup: 无结构性重构。实现期两处调整，均在 GREEN 前完成：① `onOpen` 原计划调用 `SQLiteDatabase.isForeignKeyConstraintsEnabled()`，实测该 API **在 Android SDK 中不存在**（编译失败），改为回读 `PRAGMA foreign_keys`——这比对 App 层标志位更接近连接的真实状态；② 测试侧 `Work<T>` 接口接收 `SQLiteDatabase`，lambda 由无参改为带参，顺带断言 runner 确实把连接交给工作体。
- Regression evidence: 每次调整后重跑同一命令至 exit 0。

### TDD or sizing exception — only with prior user approval
- Exception type: **Sizing（规模）**，TDD 无例外（`tdd_mode: required` 已满足）。
- Reason（命令 + 读数位置）：判定口径命令
  `grep -v -E '^[[:space:]]*$|^[[:space:]]*//|^[[:space:]]*\*|^[[:space:]]*/\*' android/data/src/main/java/com/treelearn/data/db/*.java | wc -l`；
  物理行对同一批文件用 `cat … | wc -l`。复核修复后生产代码为 **非空非注释 458 行 / 物理 722 行**（复核前 392/554 → D1/D2/D3 后 455/674 → D13 后 458/722；D13 的 +48 物理行几乎全是 `branches` 锚点约束的说明注释与划界理由，代码行仅 +3）。
  - 主要成本是 DDL 与列常量块（`TreeLearnSchema` 物理 563 行 / 代码 377 行），它与 helper 的 `onCreate`/`onUpgrade` 强耦合：单独拆出会得到一个无法独立验证的切片（没有 helper 就无法在真实 SQLite 上断言 DDL 语义），因此**没有**按最初设想的「拆第四个切片」处理。
  - **独立复算**（`artifacts/evidence/sizing-adjudication-2026-09-21.txt`，由脚本生成、数字取自命令捕获输出而非手抄）：非空非注释**今日实测 458，与本记录逐字相同**；物理行今日 741（较记录 +19，来自本轮 N2 对 `TreeLearnSchema` 注释段的改写——注释不计入判定值，故判定值不变）。
- User approval: **已批准（2026-09-21，用户一次裁定口径并批准例外）**。口径裁定为**非空非注释**，本任务 **458 < 500 行硬上限 → 未越线，不需要例外**；超出 200 行软上限属 verifier **警告**级。批准人 / 日期 / 任务 ID / 判定口径实测值均已写入 `.trae/rules/harness-tasks.md` 的 Task sizing 节例外表。
- **自设 400 门槛的处置（原第三轮 F4 第 ② 条）**：本文件 Implementation notes 自设「实测 > 400 必须回到拆分表把 DDL 常量块拆成第四个切片，**而不是继续放宽估算**」。本条**未被满足，如实保留为已知偏差**。裁定被理解为**已批准该例外**——用户是在「四个任务实测值均越线」的前提下选择「追认口径并批准例外」的，而本任务正是这四个之一；不拆的技术理由（语句片无 helper 则不可独立验证）已由独立守门复核接受。**该解释的待确认项留在本任务内**（`User approval` 与 `Handoff notes → Blocker`），并须一并列入最终交付报告的待追认清单；若用户本意只是豁免 500 硬限、不含本任务自设的 400 门槛，则应回到拆分表处理。**状态标注：OPEN — 需用户裁定**（记录修复轮 2026-09-21 重述，实现方未自批；见本文件「记录修复轮」一节）。
- Alternative verification: 若将来确要拆分，处置为把 `TreeLearnSchema` 的列常量块与 DDL 块拆成两片——`TreeLearnSchema`（常量/身份）+ `TreeLearnSchemaDdl`（语句构造）——并把本任务已取得的 instrumentation 证据保留为「合并前基线的回归证据」，再按新切片补 RED/GREEN。

## Verification report

### verifier #3 批量复核（2026-09-24，独立于本任务实现者）

- Verdict: **PASS（设备项 deferred）**。历史两条挂起原因的处置：① 规模——用户 2026-09-21 裁定口径为非空非注释，458 < 500 未越线（`sizing-adjudication-2026-09-21.txt`），「自设 400 行拆分门槛」是实现者内部更严自设线，仓库规则只认 200/500，本项不构成仓库级阻塞；② 「verifier 的 PARTIAL 不能由实现者改判」——本轮由独立于实现者的复核方出具，符合 harness-verification.md 的独立性要求。技术面 D1–D13 已由前轮复验关闭。
- Fresh evidence（2026-09-24）: `artifacts/evidence/P3-B1-gates-2026-09-24.txt`（全模块 JVM 套件 + lint + androidTest 编译，BUILD SUCCESSFUL / EXITCODE=0；count_jvm_tests 全库 188 套件 1725 tests / 0 失败，VERDICT OK）；`P3-B1-no-kotlin-2026-09-24.txt`（EXITCODE=0）；`validate_tasks.py` errors=0。
- 判定依据: 本任务自设验收项已按原报告下推至 022/023/024/025（本轮同批复核）；升级路径 N/A 与机制断言替代经前轮 verifier 认可，未发现新事实推翻。
- Gaps（不变，均 deferred）: 真实 upgrade 执行（V1 无前驱，结构上不可产生）；设备 SQLite 行级证据——`deferred — 本轮不使用模拟器（用户指令 2026-09-21）`；跨主题误删边界与旧库 CHECK 修复只对新装生效两条已写入建表入场清单，属 V1 发布前处置项，非本任务失败项。

- Verdict: **VERIFYING（不得报 PASS）**。原先的治理阻塞（规模例外）**已由用户 2026-09-21 的裁定解除**：按「非空非注释」口径 458 < 500，未越线。**仍不报 PASS 的原因有两条，都不是技术原因**：① 本任务自设的 400 行拆分门槛是否随本次例外一并豁免，属对裁定的解释问题，已登入建表入场确认清单（见 sizing 节）；② 按仓库规则，verifier 的 PARTIAL 不能由实现者自行改判为 PASS。TDD 的 RED/GREEN 与全部专项守门证据均已取得且可复现，**技术面上各轮守门提出的缺陷已全部修复并复验（见 D1–D13）**，本条不因技术原因挂起。
- Evidence: `artifacts/evidence/T-004-red.txt`、`T-004-green.txt`（`Starting 52 tests` / `EXITCODE=0`；N6 修改后整份重出，头部 `Generated:` / `Exit code:` 由 `.gen-android-test-green.sh` 在同一次运行里写入，不再是补记头）、`T-004-tests.txt`（24 例 / 0 fail / 0 error / 0 skip，含 JUnit XML 统计行、路径与 XML SHA-256）、`T-004-core-tests.txt`（`:core:test --rerun` 131 例，逐文件计数取自 XML；**131 是本任务验收时的快照**——全库此后增至 13 套件 / 154 例，见 `artifacts/evidence/VERIFY-core-test-counts.txt`）、`T-004-validate-tasks.txt`、`T-004-guard.txt`（Java-only guard PASS，13 OK 行）、`T-004-guard-selftest.txt`（完整模式 `OVERALL = SELFTEST PASS (40 case(s))`、`SELFTEST_EXITCODE=0`）、`T-004-ddl-location.txt`（DDL 仅一处产地，检索式已补全 `CREATE UNIQUE INDEX`/`CREATE TRIGGER`/`DROP`）、`T-004-constraint-code-probe.txt`（五类约束扩展错误码实测，五个码均可 grep 命中）。**规模裁定证据**：`sizing-adjudication-2026-09-21.txt`。复核报告：`T-004-verification-db-review.md`、`T-004-verification-verifier.md`、`T-022-verification-db-review.md`（第四轮，命中 D13）。设备：`emulator-5554`，`Medium_Phone_API_36.1`，API 36。
- Changed/deleted assertion rationale and equivalent behavior coverage: 验收项「升级路径」按 `.trae/rules/sql.md:40` 记为 **N/A**（V1 无已发布前驱），并以「迁移链连续、无缺口、`DATABASE_VERSION` 与 DDL 绑定」的机制断言替代；同时补了「已有 `user_version=0` 空库文件经生产 helper 走 `onCreate`」这一**真实可执行**的路径。`foreignKeysAreEnforcedNotMerelyDeclared` 里两个溯源夹具由「多缺陷输入」改为**单缺陷输入**（见 D13 第 3 条）——这是**加强**而非放宽：原夹具之所以通过，靠的是 CHECK 声明顺序而非它命名的规则。原「七表 DAO / 首题原子创建 / 再水合契约」三项已下推至 T-2026-09-21-022 / -023 / -024 / -025，本任务不再对其负责。
- Gaps:
  - ~~**规模例外未获批准**（物理行 722 > 500；非空非注释 458 > 200 软上限）——唯一阻塞项。~~ **已解除（2026-09-21 用户裁定：口径取非空非注释，458 < 500 未越线；超 200 软上限属警告级）**。残余待确认项改列于 sizing 节的「自设 400 门槛」条。
  - 首题原子创建、DAO 行级读写、`restore` 只读映射的机械检索：由 022/023 承接，本任务无证据。
  - `onUpgrade` 的**委托**现已可证伪（走 `1→2` 并断言抛 fail-closed，且断言 `user_version` 未被改写），但仍无**真实 upgrade 执行**（V1 无前驱，无法在不编造版本的前提下产生）。
  - `branches.context_snapshot_id` 因与 `context_snapshots.branch_id` 互为外键，**无法**在 schema 层用 `NOT NULL` 强制非根分支必填；该约束只能由创建用例在单一事务内保证，已下推给 **T-2026-09-21-024**（快照写入）与 T-2026-09-20-topic-branch-lifecycle-009（分叉创建的两步写）。验收夹具已改为两步写入（分支 → 快照 → 回填），不再自己制造违反 §5.2 的行。
  - **跨主题误删边界（守门 L3）**：`branches.source_message_id` / `source_revision_id` 为 `ON DELETE CASCADE`，但库层**不保证** source 与被分叉分支同属一个 topic。越界写入的行会让「删主题 A」连带删掉主题 B 的分支。schema 层无法用 CHECK 表达（跨表判定），规则已写入 `TreeLearnSchema` 的列注释并下推给 **T-2026-09-21-025 的写入路径**（原指向 023，该任务于 2026-09-21 切分后不再承担写入路径）。
  - **修复只对新装生效（守门 L4）**：`branches` 锚点 CHECK 的两次修正都只走 `onCreate`。任何用旧 V1 DDL 建过库的设备会永久保留旧 CHECK，而 `DATABASE_VERSION` 仍为 1、`FORWARD_MIGRATIONS` 为空，无法靠迁移修好。V1 未发布（无 tag、versionCode 1）故当前可接受，但**首个发行版前必须处置**：清库，或改为 V2 + 前向迁移（SQLite 重建 `branches` 表并复制数据）。已同步写入建表入场清单 §3 的迁移约定。
  - 上述两条边界已同步到 `Docs/05-technical-design/知芽（TreeLearn）Android SQLite 建表入场确认清单_V1.0.md`（§2 的 `branches` 约束列、§3 迁移约定、§4 待确认项），不再只存在于本任务文件。
- Risks: schema 漂移（已由「DDL 单产地 + 列常量拼装 + 机械检索」三重叠压）；事务范围过大和锁等待（本任务只提供 runner，实际锁行为待 023 的首题事务实测）；**锚点约束的完整语义现在依赖两个地方各司其职**（库层管分组、`BranchAnchor.ofRange` 管内容）——若将来有人在 DDL 里再补一条内容规则，就会形成「同一条规则两处判定」，正是失败案例库记录过的形态。


## Implementation notes

- 不得修改已发布 migration；升级必须使用新的前向步骤。
- 每次打开数据库显式启用并验证 foreign_keys；Cursor、statement 和事务必须可靠关闭。
- 按 `.trae/rules/sql.md` 的 Android SQLite 分支执行：通过生产 SQLiteOpenHelper 入口在**真实 Android framework SQLite** 上验证，不需要无生产消费者的 MySQL。
  验收环境为 API 36 模拟器（用户 2026-09-21 决定以 API 36/36.1 为开发与验收目标，不再建设 API 28 环境；`minSdk 28` 仍是声明的最低兼容版本，由 `compileSdk 36 + minSdk 28` 的 lint `NewApi` 覆盖）。
- 提示词字段只用 `TEXT NOT NULL` 保存稳定用途/版本标识；不得新增提示词正文列、prompt 表或第八张永久表。
- **本任务是 DDL 的唯一产地**：022/023 只许引用 `TreeLearnSchema` 的列名/表名常量，不得自行拼接 DDL 或字面量列名；`db-migration-reviewer` 复核时应以此为准。

### 开工前实测：两处必须先解决的契约事实（2026-09-21）→ 已于同日拆分处置

**① `generation_operations` 不能以 `:core` 实体形态出入库。（已下推至 T-2026-09-21-023）**
实测 `android/core/src/main/java/com/treelearn/core/domain/`：`GenerationOperation` 的构造函数是 `private`，
且**不存在** `restore`/等价再水合入口——唯一的创建路径是 `start(GenerationCommand, long)`（见 `GenerationOperation.java:54-75`），
之后只能靠 `advanceTo`/`appendReasoning`/`appendAnswer` 推进。相比之下
`AnswerRevision.restore`、`BranchSource.restore`、`BranchAnchor.wholeAnswer` 都存在。
结论与做法：该表按**行形态（row-oriented）**实现 DAO，而不是强行把行塞回 `GenerationOperation`：

- DAO 暴露 `GenerationOperationRow`（id / branch_id / message_id / command_id / purpose / model /
  thinking_enabled / prompt_policy_version / state / partial_reasoning / partial_answer / created_at /
  updated_at / error_code），写路径为 `insert(row)`、`appendPartials(...)`、`transition(...)`。
- 恢复流程（§7「下次启动把遗留的 VALIDATING/SUBMITTING/STREAMING_* 原子改为 INTERRUPTED」）用
  `GenerationState.processRecoverableStates()` + `recoveryTarget()` 计算目标状态，**不需要**重建实体。
- 不新增 `GenerationOperation.restore`：那会绕开 `start` 所提供的「版本必须先由命令固定」这一保证，
  与 T-003 下推的再水合契约（`restore` 只许出现在只读映射路径）方向相反。若后续确有「先重建再做业务变更」
  的需求，应由持有命令的一方重新走 `start`，而不是把数据库行当成构造器。

**处置**：本节内容随 DAO 职责一并下推到 **T-2026-09-21-023**（该表 DAO）与 **T-2026-09-21-025**（首题原子创建事务）；023 于 2026-09-21 按用户裁定二次切分，两表 DAO 留在 023、首题事务独立为 025，
并由该任务的验收条件「不得新增 `GenerationOperation.restore`」承接。本任务只负责该表的 **DDL 与约束**（含每分支单活动操作的偏索引）。

**② 本任务按原范围无法满足行数预算。（已于 2026-09-21 拆分处置）**
原范围逐项估算为 **900+ 生产代码行**（schema DDL 常量 ~150、helper ~60、database/事务 ~80、七表 DAO 合计 ~570、行类型 ~100），
远超 `harness-tasks.md:45` 的 200 行软上限与 **500 行硬上限**。
**处置**：按职责拆成三个切片，各自 ≤ 500 行且可独立验证：

| 切片 | 任务文件 | 范围 | 估算生产代码行 |
| :--- | :--- | :--- | :--- |
| 004（本任务） | T-2026-09-20-sqlite-schema-dao-transactions-004.md | schema 常量、helper、迁移、事务 runner、schema 级验证 | 300 |
| 022 | T-2026-09-21-sqlite-dao-topic-branch-message-022.md | `topics`/`branches`/`messages` 三表 DAO 与只读映射 | 200 |
| 023 | T-2026-09-21-sqlite-dao-revisions-operations-023.md | `answer_revisions`/`generation_operations` 两表 DAO（含行形态 operation）与 stabilize 落盘 | 300 |
| 024 | T-2026-09-21-sqlite-dao-snapshot-artifact-024.md | `context_snapshots`/`ai_artifacts` 两表 DAO（行 + 原始 JSON 载荷） | 220 |
| 025 | T-2026-09-21-first-topic-atomic-creation-025.md | 首题原子创建事务（五类行同事务 + 全败回滚 + 跨主题 source 判定） | 180 |

> **2026-09-21 二次切分**：023 原把「其余四表 DAO」与「首题原子创建事务」装在一片，实测预估 480–580 行仍压在/越过 500 行硬限，经用户裁定按**三片垂直切分**处理，上表 023/024/025 三行为切分后的结果（023 的稳定 id 与序号不变，文件名由 `…-sqlite-dao-generation-artifacts-023.md` 改为 `…-sqlite-dao-revisions-operations-023.md`）。因此本表的上限不再是三片而是五片。

本任务 300 行仍超过 200 行**软**上限（允许，但会被 `validate_tasks.py` 记为 warning）：DDL 常量块是主要成本，
且它与 helper 的 `onCreate`/`onUpgrade` 强耦合，单独拆出会造出一个无法独立验证的切片。
若实施中实测生产行数 **> 400**，必须回到本表把 DDL 常量块拆成第四个切片，而不是继续放宽估算。

### 独立复核结论（verifier，2026-09-21；本轮不使用模拟器）

- Verdict: **FAIL（记录层；行为层在设备轮次里已复核为真）**。状态保持 `verifying`，不置 `verified`。
- 本轮亲自复现 / 回读（`VERIFY-static-recheck-2026-09-21.txt`、`VERIFY-instrumentation-classes.txt`）：
  - 规模：`grep -v -E '^[[:space:]]*$|^[[:space:]]*//|^[[:space:]]*\*|^[[:space:]]*/\*' android/data/src/main/java/com/treelearn/data/db/*.java | wc -l` → **458**，与本记录逐字相同（复算见 `artifacts/evidence/sizing-adjudication-2026-09-21.txt`）。
  - DDL 产地：`rg -n 'CREATE TABLE|ALTER TABLE|CREATE INDEX|CREATE TRIGGER' android --glob '*.java'` → 全部落在 `TreeLearnSchema.java`，无第二产地。
  - 设备轮次（2026-09-21 20:49，**在本轮「不使用模拟器」指令之前**由本 lane 执行）：`SqliteSchemaInstrumentedTest` **24/24 通过**（XML SHA-256 `170CF1C127718789E5112E956B2CAE13AA0528C8E77710FB97896B76232E2AE7`）。
- 未通过项：
  1. **RED 记录的自述数与证据不符**：本文件写「javac 自报 **24** 个错误（去重后 **18** 条：`程序包…不存在` 6 条 + `找不到符号` **12** 条）」。6+12=18≠24；`artifacts/evidence/T-004-red.txt` 的实测构成是 `程序包…不存在` **6**（3+3）+ `找不到符号` **18** = **24**，与该文件第 20/21 行的「24 个错误」一致。**「12」在整个证据文件里从未出现**，是凭印象补的。
  2. **证据文件自身互相矛盾**：`T-004-red.txt` 第 25–27 行标着「Distinct error texts (deduped)」的块列的是 `36 + 6 + 6 = 48`——那是**未去重**的重复计数（`找不到符号` 18×2=36），与同一文件上方的「24 个错误」冲突。该块标题与数字必须重写。
  3. **GREEN 记录引用的证据不支持该句**：本文件 GREEN 写「构建输出 `Starting 40 tests` / `Finished 40 tests` … 见 `artifacts/evidence/T-004-green.txt`」；该文件第 138/141 行实际是 `Starting 52 tests` / `Finished 52 tests`（第 6 行补记头写「= 40 例（本轮加入 T-023 后为 52 例）」）。被引证据里**不存在**「Starting 40 tests」这一行。
  4. **自设 400 行门槛的处置仍未决**：Implementation notes 自设「实测 > 400 必须回到拆分表」，实测 458 已越线；sizing 节把它解释为「用户 2026-09-21 裁定已含该例外」，而该解释在本文件内被自己标为「待确认项」。这是**需要用户裁定**的治理问题，verifier 不能代答。
  5. **`:core:test` 引用数过期**：本文件写 **131 例**；本轮实测全库 **154 例**（其它 lane 后续新增）。应标注为「本任务验收时快照」或更新。
- 验收条件对照：②③④⑥⑦⑧ 由设备轮次（20:49）与静态检索复核通过；①（instrumentation 先 RED 后 GREEN）**本轮只复核了 GREEN**：RED 需要把三个生产类移出源码树并要求设备 → `deferred — emulator not used this round (user instruction 2026-09-21)`，**未执行**。⑤（`db-migration-reviewer`）由本文件记录的三轮专项报告承担，本轮未重跑。
- 翻转条件：① 按证据重算 RED 构成（6 + 18 = 24）并重写其「去重」块；② 把 GREEN 的 `Starting/Finished 40` 改成与所引证据一致的写法（或重录证据）；③ 由用户裁定自设 400 门槛；④ 更新 `:core:test` 数字。

### 记录修复轮（record repair，2026-09-21 下；本轮不使用模拟器）

> 本节由**记录修复 lane**（与实现同方，**不是**独立 verifier）填写：只修记录、引用与措辞，不改生产代码、不改测试、不改判据。
> 上方「独立复核结论（verifier，2026-09-21）」原样保留，本节是回应而不是覆盖。

第 5 轮列出的未通过项逐条处置：

| 第 5 轮发现 | 处置 | 修复后实测（命令 + 读数位置） |
| :--- | :--- | :--- |
| ① RED 自述「去重后 18 条：×6 + ×12」自相矛盾（6+12≠24） | 按证据当场复算改写为 `找不到符号` 18 + `程序包com.treelearn.data.db不存在` 3 + `程序包TreeLearnSchema不存在` 3 = 24，并把复算命令写进 RED 段 | `$e = Get-Content artifacts/evidence/T-004-red.txt -Encoding UTF8 \| Where-Object { $_ -match '^[A-Za-z]:\\.*\.java:\d+: 错误' }` → `$e.Count` = **24**；三类各 18 / 3 / 3（命令与输出见本文件 RED 段） |
| ② `T-004-red.txt` 的「Distinct error texts (deduped)」块与同一文件上方的「24 个错误」冲突（该块实为 36+6+6=48 的**未去重**计数） | 重写该块的标题与说明，使其明确陈述「48 行 = Gradle 打印两次，去重后为 24」，并给出复现命令 | `artifacts/evidence/T-004-red.txt` 第 23 行起（重写后的块） |
| ③ GREEN 引用的证据里不存在「Starting 40 tests」 | 按被引证据改为 52，并说明 52 的构成 | `Select-String -Path artifacts\evidence\T-004-green.txt -Pattern 'Starting|Finished'` → 第 138 行 `Starting 52 tests`、第 141 行 `Finished 52 tests` |
| ④ 自设 400 行门槛的解释未决 | 标注为 `OPEN — 需用户裁定`，实现方未自批（治理问题，verifier 不能代答） | 本文件 `### TDD or sizing exception` 段末的「状态标注：OPEN — 需用户裁定」 |
| ⑤ `:core:test` 的 131 例被当作当前值 | 标注为「本任务验收时快照」，并给出全库当前实测 | `artifacts/evidence/VERIFY-core-test-counts.txt` 第 15 行 `TOTAL suites=13 tests=154 failures=0 errors=0 skipped=0` |
| 验收条件 ① 的 RED 半边（instrumentation 先 RED 后 GREEN） | `deferred — emulator not used this round (user instruction 2026-09-21)`；既不判 PASS 也不判 FAIL | 「独立复核结论」的验收条件对照段 |

- 本轮**未**执行任何设备命令（无 `connectedDebugAndroidTest`、无 `adb`、无模拟器）。
### 第九轮独立复核结论（verifier #2，第四轮；2026-09-21；本轮不使用模拟器）

> 本轮针对**记录修复轮**的处置做独立复核，并核对「自设 400 门槛」的登记状态。verifier 只读。
> 自产证据：`VERIFY5-jvm-suites.txt`、`VERIFY5-androidtest-javadoc-depdirection.txt`、`VERIFY4-T002-guard.txt`。

- Verdict: **PARTIAL**。① 记录层修复经复核为真；② **本任务的验收判据几乎全是设备判据**——第 77–88 行的验收条件全部是未勾选 `[ ]`，且新装、列集合、PRAGMA、索引与约束、事务回滚、v0 空库文件等只能在真实 Android SQLite 上判定，按用户 2026-09-21 指令记为 `deferred`，既非 PASS 也非 FAIL；③ 自设 400 行门槛**仍为 `OPEN — 需用户裁定`**。状态保持 `verifying`。
- 记录修复轮各项的独立复核（命令 + 读数位置）：

| 修复项 | 独立复核结果 |
| :--- | :--- |
| RED 构成改写为 24 = 18 + 3 + 3 | **为真，本轮亲自复算**。对 `T-004-red.txt` 用顶格正则 `^[A-Za-z]:.*\.java:\d+: 错误` 实测 **24** 行；其中 `找不到符号` **18**、`程序包com.treelearn.data.db不存在` **3**、`程序包TreeLearnSchema不存在` **3**，分项之和等于总数（18+3+3=24） |
| 把「去重后 36+6+6=48」解释为 Gradle 双印 | **为真**。`T-004-red.txt:23` 段标题为 `## Error lines as printed by Gradle (javac's own count is 24; the whole block is printed twice)`，`:31` = `48`，`:39` = `48 printed error lines = 24 distinct errors printed twice, and 18 + 3 + 3 = 24 distinct errors`。即「48」是未加锚定的检索式造成的**双印**计数，不是另一个构成 |
| GREEN 40 → 52 | **为真**。`T-004-green.txt:138/141` = `Starting 52 tests` / `Finished 52 tests`（`Medium_Phone_API_36.1(AVD) - 16`）、`:154` = `EXITCODE=0` |
| Handoff 23 → 24 例 | **为真**。`T-004-tests.txt:8` = `--- T-004 cases (24) ---`，其后用例块（第 9–32 行）恰好 **24** 条；`:34` 才是 `--- every other case in the same batch ---`（本轮首次复核曾把该表头误计为第 25 条，已自行更正） |
| `:core:test` 131 标注为验收时快照 | **为真**（第 232 行）。本轮实测 `:core` 为 **18 套件 / 187 用例**（`VERIFY5-jvm-suites.txt`），故 131 与 154 均已过期 |
| 自设 400 行门槛 | **仍 `OPEN`**。第 226 行写明「本条未被满足，如实保留为已知偏差 …… **状态标注：OPEN — 需用户裁定**」，实现方未自批。**verifier 不代批** |

- **时间线注记（不得当作本轮设备证据）**：`T-004-red.txt`（14:59）与 `T-004-green.txt` 头部均写明设备 `emulator-5554`、命令 `./gradlew :app:connectedDebugAndroidTest`——它们是**本轮「不使用模拟器」指令之前**取得的设备证据。引用时须连同该时间上下文。
- 本轮亲自复现的非设备证据（自产）：
  - `cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest :data:testDebugUnitTest :tree-layout:test :app:testDebugUnitTest --rerun-tasks --no-daemon --console=plain` → `BUILD SUCCESSFUL`、exit 0；`:data` **2 套件 / 13 用例 / 全 0**，即 schema/helper/事务骨架在 JVM 侧无回归（`VERIFY5-jvm-suites.txt`）。
  - instrumentation 源码**能编译**：`> Task :app:compileDebugAndroidTestJavaWithJavac` 成功、`BUILD SUCCESSFUL`、exit 0（`VERIFY5-androidtest-javadoc-depdirection.txt:728`）。这是无设备条件下能取得的最强替代证据，**只证明编译，不证明执行**。
  - `android/app/build/outputs/androidTest-results/connected/debug` 文件数 **0**，从产物侧确认本轮未做设备运行。
  - Java-only guard `VERDICT: PASS (13 OK line(s))`、exit 0（`VERIFY4-T002-guard.txt`）。
- **规模**：`estimated_production_lines: 300`（≤ 500，validator 无 ERROR）；按用户裁定口径实测 **458 非空非注释 < 500 硬上限**，故**不在未获批越线清单内**。唯一未结事项是本任务**自设的 400 行门槛**（见上表末行）。
- 记录层残留（不影响技术结论，供下一轮清理）：第 77–88 行的验收框仍为未勾选 `[ ]`，其中若干条在历史设备轮次其实已取得证据；建议区分标注「已由设备轮次覆盖但按本轮指令 deferred」与「从未验证」，以免把 deferral 误读为未做。

## Handoff notes

- Completed: `:data` 的 schema/DDL（七表 + 9 索引含 2 条偏索引 + 6 条触发器）、`TreeLearnDatabaseHelper`（外键回读强制、前向迁移、拒绝降级）、`DatabaseTransactionRunner`（拒绝嵌套、拒绝被工作体窃取事务、失败回滚）；`:app` 的 **24** 例真实 Android SQLite 验收套件（`artifacts/evidence/T-004-tests.txt` 第 8 行 `--- T-004 cases (24) ---`；原写 23 是 D13 新增 `forkAnchorColumnsAreAllOrNothing` 之前的旧数）；六份证据文件。
- In progress: 无。
- Blocker: 无（规模例外已由用户 2026-09-21 裁定：口径取非空非注释，本任务 458 < 500 未越线）。**待确认（不阻塞施工）**：本任务自设的 400 行拆分门槛是否随本次例外一并豁免——见 sizing 节的「自设 400 门槛」条，须列入最终交付报告的待追认清单。技术证据已齐，**两个守门的 FAIL 已闭环**，不阻塞 022/023 开工（它们只依赖本任务的 schema 常量与事务 runner，接口已稳定并已在真实设备上验证）。
- Unblock condition: 用户就「自设 400 门槛是否一并豁免」给出答复。若要求拆分，按 Verification report 的 Alternative verification 执行。
- Next steps: 立即解锁 T-2026-09-21-022、T-2026-09-21-023、T-2026-09-21-024（三者均只依赖本任务的 schema 常量与事务 runner），再由 023 解锁 T-2026-09-21-025，以及上下文、生成和主题任务；本任务需走 `db-migration-reviewer`（Android SQLite 分支）复核。
- Watch-outs: 推理不可只存在 operation；成功后必须完整进入不可变 revision；提示词版本必须随同复制但 system 正文不得入库。
- Watch-outs（本任务已实现、下游必须遵守的 schema 事实）：
  - `generation_operations` 有 9 个合法 `state` 取值并被 `CHECK` 约束；`messages.role` 只允许 `USER`/`AI`；`collapsed` 与 `thinking_enabled` 只允许 0/1；`revision_no > 0`；`answer_content` 必须非空。写这些列时不要绕过约束去「先落库再修」。
  - 存在**循环外键**（`topics ↔ branches`、`branches ↔ messages/context_snapshots`）。插入顺序因此受约束：根分支插入时全部来源列必须为 `NULL`（由 `CHECK` 强制），非根分支则**必须**同时带上 `source_message_id`/`source_revision_id`/`anchor_hash`。`branches.context_snapshot_id` 与 `messages.current_*_id` 需要在同一事务内二次 `UPDATE` 补齐——首题事务（023）必须为此保留事务边界，不能分两次提交。
  - 删除语义：`branches.source_message_id` / `source_revision_id` 为 `ON DELETE CASCADE`，即**删除来源会删除依赖它的子树**，与「永久删除子树」的语义一致；`topics.last_visited_branch_id` 与 `messages.current_*_id` 为 `ON DELETE SET NULL`，不连坐。
- Watch-outs（已随职责下推）：以下再水合契约自本任务**下推至 T-2026-09-21-022 / -023 / -024 / -025**，本任务只在 DDL 层面保证 `prompt_policy_version` 等列 `NOT NULL`，不再对 DAO 写路径负责：
  - `BranchSource.restore` / `AnswerRevision.restore` 是**持久化再水合的专用入口**，它跳过 `fromOperation` 的 STABLE 校验与 `create` 的 `PromptPurpose.ownerOf` 归属校验（因为数据库行里的来源与策略版本是既成事实，重载时无法、也不应重新推导状态）。
  - 因此**只读映射路径可以用 `restore`，任何写路径禁止用 `restore`**。写路径误用会把 STABLE / 策略归属两条领域不变量在持久化层悄悄放开，而 `:core` 内部无法察觉——这是 `:core` 里已登记的残留风险，由 022/023 的验收条件与机械检索兜住。
  - 若确实需要「从数据库行重建后再做业务变更」，正确做法是先用 `restore` 重建 **只读** 实体供展示，再通过领域工厂/变更方法产生新状态；不要为了让写路径「跑通」而把 `restore` 当成构造器用。
  - 写路径出现 `restore` 时，必须走 T-021 的生成操作状态机重新推导，而不是直接落库。

