# Treelearn Harness Task: 建立 SQLite schema、helper、迁移与事务边界

id: T-2026-09-20-004
title: 建立 SQLite schema、helper、迁移与事务边界
template_version: 2
task_type: implementation
status: verifying
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
- 七表 DAO 与 `Cursor → 实体` 映射（T-2026-09-21-022、T-2026-09-21-023）、Repository 适配、首题原子业务事务（T-2026-09-21-023）、DeepSeek、Keystore、页面和搜索排序策略。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增持久化骨架；保持 core 的不可变和合法状态迁移约束。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游 core 实体（列定义依据）；下游 T-2026-09-21-022、T-2026-09-21-023 及各 DAO 消费者。
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

> 2026-09-21 收窄：原第 2/3/4 条的行级写入、首题原子性与再水合契约已下推到 T-2026-09-21-022 与 T-2026-09-21-023；本任务只对 **schema、迁移与事务骨架** 本身负责。

- [ ] 同一 instrumentation 命令先 RED 后 GREEN，覆盖新装、重开、逐版本升级及失败回滚。
- [ ] 新装走**生产 `onCreate`**：`sqlite_master` 中恰好 7 张永久表（名称与建表清单 §2 完全一致），`PRAGMA user_version` 为 1。
- [ ] 每张表的列名/类型/`NOT NULL` 与清单 §2、技术方案 §6.1 逐项一致：用 `PRAGMA table_info` 的实测结果与期望清单做**集合比较**，不得用字符串包含判断。
- [ ] 关键索引与约束**存在且生效**：`messages(branch_id, ordinal)` 唯一、`branches` 每主题单根、`answer_revisions(message_id, revision_no)` 唯一、`context_snapshots(branch_id)` 唯一、`ai_artifacts(kind, scope_id)` 索引，以及 `generation_operations` 每分支单活动操作的**偏索引**（须在 API 36 真实 SQLite 上实测该 DDL 可用）。
- [ ] 每次打开数据库 `PRAGMA foreign_keys` 读回为 1；`PRAGMA foreign_key_check` 无结果；`PRAGMA integrity_check` 为 `ok`。
- [ ] **升级路径（本版本为 N/A，但机制必须可验）**：V1 是首个发布版本、无已发布前驱，按 `.trae/rules/sql.md:40`「首个尚未发布版本可将升级路径记为 N/A」记为 N/A，**不得编造前驱版本或假迁移来制造证据**。替代证据：机械断言 `onUpgrade` 存在且委托给前向迁移注册表；注册表对 `1..DATABASE_VERSION-1` 形成**连续、无缺口的 +1 链**；`DATABASE_VERSION` 为 1 且 DDL 只与当前版本绑定。一旦 `DATABASE_VERSION` 升到 2，本项自动转为「必须走真实 `onUpgrade` 实测才有证据」。
- [ ] **已有 v0 文件的打开路径**：对一个 `PRAGMA user_version = 0` 且无表的既存库文件，经生产 helper 打开后走 `onCreate` 建出 7 张表并把 `user_version` 置为 1（这是真实 Android 上「首次安装前的空库文件」路径，可实测）。
- [ ] 事务 runner 语义：抛异常时整体回滚（提交后行数为 0），正常提交后可见；重复开启被拒绝而不是静默并入外层事务。
- [ ] 时间列全部为 `INTEGER` 且由应用显式写入 UTC epoch millis；schema DDL 中不出现 `CURRENT_TIMESTAMP`、`datetime('now')` 或设备本地时区函数。
- [ ] **不落 system 正文（schema 级）**：对 `PRAGMA table_info` 得到的**全部列名**做机械检索，断言不存在 system 提示词正文语义的专用列（列名含 `system` 的命中数为 0；含 `prompt` 的命中只允许是 `prompt_purpose` 与 `prompt_policy_version`）。写入后回读的哨兵子串全表扫描需要真实写路径，由 T-2026-09-21-023 在其 DAO 验收中执行。
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

## TDD execution record

### RED — failing test first
- Test/scenario: `:app` 的 `androidTest/com/treelearn/app/SqliteSchemaInstrumentedTest.java`——本任务验收规格的完整实现（新装、列集合、PRAGMA、索引与约束、级联、事务 runner、不可变性、重开、v0 空库文件），写于任何生产类之前。
- Command: ./gradlew :app:connectedDebugAndroidTest --console=plain
- Exit code: 1
- Observed failure: `:app:compileDebugAndroidTestJavaWithJavac FAILED`，编译器报 **21 errors**；全部落在三个尚未存在的生产类型上：`package com.treelearn.data.db does not exist`、`class TreeLearnDatabaseHelper`、`variable TreeLearnSchema`、`class DatabaseTransactionRunner`。
- Failure reason validated: 每条编译错误的名字都是待建生产类型本身，无第二成因。**证据强度如实标注：这是编译级 RED，不是行为级 RED。** 它证明「能力缺失」，不证明「观察到了某个具体错误行为再修好」。证据见 `artifacts/evidence/T-004-red.txt`（文件头注明退出码、设备、命令，并说明 42 vs 21 的错误计数差异来自 Gradle 重复打印）。

### GREEN — minimum implementation
- Implementation: 命令与 RED **逐字符相同**（同一份验收套件，RED 与 GREEN 之间不修改测试文件来「让它绿」）。新增 `:data` 三个生产类——`TreeLearnSchema`（七表表名/列名常量 + 全部 DDL + 索引 + 4 条不可变性触发器 + 前向迁移注册表，DDL 由列常量拼出）、`TreeLearnDatabaseHelper`（`onConfigure` 开启外键、`onOpen` 回读 `PRAGMA foreign_keys` 强制为 1、`onCreate` 执行 `CREATE_STATEMENTS`、`onUpgrade` 走前向迁移、`onDowngrade` 拒绝）、`DatabaseTransactionRunner`（显式事务边界，拒绝嵌套）。验收套件本身不修改（RED 与 GREEN 是同一份文件）。
- Command: ./gradlew :app:connectedDebugAndroidTest --console=plain
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`；JUnit XML `tests="24" failures="0" errors="0" skipped="0"`，其中 23 例属本任务（第 24 例是 T-002 的启动测试，同批通过）。用例清单见 `artifacts/evidence/T-004-tests.txt`，构建输出见 `artifacts/evidence/T-004-green.txt`。用例数由 21 增至 24 是因为守门复核后重写了验收套件：新增 `unreadHasASingleRepresentationInTheSequencePair`、`transactionRunnerRejectsNestedOpenAndStolenTransactions`、`requiredIdentityColumnsRejectNull` 三例，并删除了原「不区分成因」的弱断言用例。
- Regression evidence: ① 既有 T-002 启动测试在同一次运行中通过（`mainActivityRendersFirstScreenWithoutCrash`）；② Java-only guard `VERDICT: PASS (13 OK line(s))`、exit 0（`artifacts/evidence/T-004-guard.txt`，其中 release APK 实测 `app-release-unsigned.apk` 56012 B / 1 dex，证明 `:data` 的生产代码确实进了包）；③ 守护门自检**完整模式** `OVERALL = SELFTEST PASS (40 case(s))`、`SELFTEST_EXITCODE=0`（`artifacts/evidence/T-004-guard-selftest.txt`，文件头含 `Generated:` / `Command:` 与 Guard、Library 的 SHA-256 及行数）；④ DDL 位置机械检索仅 `TreeLearnSchema.java` 命中（`artifacts/evidence/T-004-ddl-location.txt`）。

### REFACTOR — optional
- Cleanup: 无结构性重构。实现期两处调整，均在 GREEN 前完成：① `onOpen` 原计划调用 `SQLiteDatabase.isForeignKeyConstraintsEnabled()`，实测该 API **在 Android SDK 中不存在**（编译失败），改为回读 `PRAGMA foreign_keys`——这比对 App 层标志位更接近连接的真实状态；② 测试侧 `Work<T>` 接口接收 `SQLiteDatabase`，lambda 由无参改为带参，顺带断言 runner 确实把连接交给工作体。
- Regression evidence: 每次调整后重跑同一命令至 exit 0。

### TDD or sizing exception — only with prior user approval
- Exception type: **Sizing（规模）**，TDD 无例外（`tdd_mode: required` 已满足）。
- Reason: 复核修复后**实测**（命令：`cd android/data/src/main/java/com/treelearn/data/db && wc -l *.java`；非空非注释口径用 `grep -v -E '^[[:space:]]*$|^[[:space:]]*//|^[[:space:]]*\*|^[[:space:]]*/\*'`）生产代码为 **物理 674 行 / 非空非注释 455 行**（复核前为 554 / 392，增量来自 D1/D2 的列修正与 D3 的判据重写）。按物理行口径**超过 `harness-tasks.md:45` 的 500 行硬上限**；按非空非注释口径 455 行只超 200 行软上限。主要成本是 DDL 与列常量块（`TreeLearnSchema` 物理 529 行 / 代码 374 行），它与 helper 的 `onCreate`/`onUpgrade` 强耦合：单独拆出会得到一个无法独立验证的切片（没有 helper 就无法在真实 SQLite 上断言 DDL 语义），因此**没有**按最初设想的「拆第四个切片」处理，也未事后美化口径。两个口径都在此列出，不挑好看的那个。
- User approval: **PENDING — 待用户追认**（与 T-003 的 762 行、T-021 的 210 行属同一类治理问题，需一次性答复）。
- Alternative verification: 若用户不追认规模例外，可选处置为：把 `TreeLearnSchema` 的列常量块与 DDL 块拆成 `TreeLearnSchema`（常量/身份）+ `TreeLearnSchemaDdl`（语句构造）两片，并把本任务已取得的 instrumentation 证据保留为「合并前基线的回归证据」，再按新切片补 RED/GREEN。**本任务当前不以 PASS 上报，见 Verification report。**

## Verification report

- Verdict: **VERIFYING（不得报 PASS）**。理由是治理项而非技术项：物理行口径 674 行超 500 硬上限、尚缺用户批准的规模例外；TDD 的 RED/GREEN 与全部专项守门证据均已取得且可复现。**技术面上两个守门提出的缺陷已全部修复并复验（见上节 D1–D5）**，本条不再因技术原因挂起。
- Evidence: `artifacts/evidence/T-004-red.txt`、`T-004-green.txt`、`T-004-tests.txt`（24 例 / 0 fail / 0 error / 0 skip）、`T-004-guard.txt`（Java-only guard PASS，13 OK 行）、`T-004-guard-selftest.txt`（完整模式自检）、`T-004-ddl-location.txt`（DDL 仅一处产地）、`T-004-constraint-code-probe.txt`（五类约束扩展错误码实测）。设备：`emulator-5554`，`Medium_Phone_API_36.1`，API 36。
- Changed/deleted assertion rationale and equivalent behavior coverage: 验收项「升级路径」按 `.trae/rules/sql.md:40` 记为 **N/A**（V1 无已发布前驱），并以「迁移链连续、无缺口、`DATABASE_VERSION` 与 DDL 绑定」的机制断言替代；同时补了「已有 `user_version=0` 空库文件经生产 helper 走 `onCreate`」这一**真实可执行**的路径。原「七表 DAO / 首题原子创建 / 再水合契约」三项已下推至 T-2026-09-21-022 与 T-2026-09-21-023，本任务不再对其负责。
- Gaps:
  - **规模例外未获批准**（物理行 674 > 500；非空非注释 455 > 200 软上限）——唯一阻塞项。
  - 首题原子创建、DAO 行级读写、`restore` 只读映射的机械检索：由 022/023 承接，本任务无证据。
  - `onUpgrade` 只有机制断言，没有真实 upgrade 执行（V1 无前驱，无法在不编造版本的前提下产生）。
- Risks: schema 漂移（已由「DDL 单产地 + 列常量拼装 + 机械检索」三重叠压）；事务范围过大和锁等待（本任务只提供 runner，实际锁行为待 023 的首题事务实测）。


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

**处置**：本节内容随 DAO 职责一并下推到 **T-2026-09-21-023**（该表 DAO 与首题原子事务的归属任务），
并由该任务的验收条件「不得新增 `GenerationOperation.restore`」承接。本任务只负责该表的 **DDL 与约束**（含每分支单活动操作的偏索引）。

**② 本任务按原范围无法满足行数预算。（已于 2026-09-21 拆分处置）**
原范围逐项估算为 **900+ 生产代码行**（schema DDL 常量 ~150、helper ~60、database/事务 ~80、七表 DAO 合计 ~570、行类型 ~100），
远超 `harness-tasks.md:45` 的 200 行软上限与 **500 行硬上限**。
**处置**：按职责拆成三个切片，各自 ≤ 500 行且可独立验证：

| 切片 | 任务文件 | 范围 | 估算生产代码行 |
| :--- | :--- | :--- | :--- |
| 004（本任务） | T-2026-09-20-sqlite-schema-dao-transactions-004.md | schema 常量、helper、迁移、事务 runner、schema 级验证 | 300 |
| 022 | T-2026-09-21-sqlite-dao-topic-branch-message-022.md | `topics`/`branches`/`messages` 三表 DAO 与只读映射 | 200 |
| 023 | T-2026-09-21-sqlite-dao-generation-artifacts-023.md | 其余四表 DAO（含行形态 operation）与首题原子创建事务 | 200 |

本任务 300 行仍超过 200 行**软**上限（允许，但会被 `validate_tasks.py` 记为 warning）：DDL 常量块是主要成本，
且它与 helper 的 `onCreate`/`onUpgrade` 强耦合，单独拆出会造出一个无法独立验证的切片。
若实施中实测生产行数 **> 400**，必须回到本表把 DDL 常量块拆成第四个切片，而不是继续放宽估算。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: schema 漂移、事务范围过大和锁等待。

## Handoff notes

- Completed: `:data` 的 schema/DDL（七表 + 9 索引含 2 条偏索引 + 6 条触发器）、`TreeLearnDatabaseHelper`（外键回读强制、前向迁移、拒绝降级）、`DatabaseTransactionRunner`（拒绝嵌套、拒绝被工作体窃取事务、失败回滚）；`:app` 的 23 例真实 Android SQLite 验收套件；六份证据文件。
- In progress: 无。
- Blocker: **规模例外待用户追认**（物理行 674 > 500 硬上限）。技术证据已齐，**两个守门的 FAIL 已闭环**，此项不阻塞 022/023 开工（它们只依赖本任务的 schema 常量与事务 runner，接口已稳定并已在真实设备上验证）。
- Unblock condition: 用户答复「追认规模例外」或「要求拆分」。若要求拆分，按 Verification report 的 Alternative verification 执行。
- Next steps: 立即解锁 T-2026-09-21-022 与 T-2026-09-21-023（两者均只依赖本任务的 schema 常量与事务 runner），再由 023 解锁上下文、生成和主题任务；本任务需走 `db-migration-reviewer`（Android SQLite 分支）复核。
- Watch-outs: 推理不可只存在 operation；成功后必须完整进入不可变 revision；提示词版本必须随同复制但 system 正文不得入库。
- Watch-outs（本任务已实现、下游必须遵守的 schema 事实）：
  - `generation_operations` 有 9 个合法 `state` 取值并被 `CHECK` 约束；`messages.role` 只允许 `USER`/`AI`；`collapsed` 与 `thinking_enabled` 只允许 0/1；`revision_no > 0`；`answer_content` 必须非空。写这些列时不要绕过约束去「先落库再修」。
  - 存在**循环外键**（`topics ↔ branches`、`branches ↔ messages/context_snapshots`）。插入顺序因此受约束：根分支插入时全部来源列必须为 `NULL`（由 `CHECK` 强制），非根分支则**必须**同时带上 `source_message_id`/`source_revision_id`/`anchor_hash`。`branches.context_snapshot_id` 与 `messages.current_*_id` 需要在同一事务内二次 `UPDATE` 补齐——首题事务（023）必须为此保留事务边界，不能分两次提交。
  - 删除语义：`branches.source_message_id` / `source_revision_id` 为 `ON DELETE CASCADE`，即**删除来源会删除依赖它的子树**，与「永久删除子树」的语义一致；`topics.last_visited_branch_id` 与 `messages.current_*_id` 为 `ON DELETE SET NULL`，不连坐。
- Watch-outs（已随职责下推）：以下再水合契约自本任务**下推至 T-2026-09-21-022 / T-2026-09-21-023**，本任务只在 DDL 层面保证 `prompt_policy_version` 等列 `NOT NULL`，不再对 DAO 写路径负责：
  - `BranchSource.restore` / `AnswerRevision.restore` 是**持久化再水合的专用入口**，它跳过 `fromOperation` 的 STABLE 校验与 `create` 的 `PromptPurpose.ownerOf` 归属校验（因为数据库行里的来源与策略版本是既成事实，重载时无法、也不应重新推导状态）。
  - 因此**只读映射路径可以用 `restore`，任何写路径禁止用 `restore`**。写路径误用会把 STABLE / 策略归属两条领域不变量在持久化层悄悄放开，而 `:core` 内部无法察觉——这是 `:core` 里已登记的残留风险，由 022/023 的验收条件与机械检索兜住。
  - 若确实需要「从数据库行重建后再做业务变更」，正确做法是先用 `restore` 重建 **只读** 实体供展示，再通过领域工厂/变更方法产生新状态；不要为了让写路径「跑通」而把 `restore` 当成构造器用。
  - 写路径出现 `restore` 时，必须走 T-021 的生成操作状态机重新推导，而不是直接落库。

