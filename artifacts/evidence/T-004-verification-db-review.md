# T-004 独立复核报告（db-migration-reviewer · 第二轮）

| 项目 | 内容 |
| :--- | :--- |
| 角色 | `db-migration-reviewer`（READONLY，本轮只读 + 出报告） |
| 目标引擎 | **Android SQLite**（`android.database.sqlite.SQLiteDatabase` / `SQLiteOpenHelper`，生产路径） |
| 复核日期 | 2026-09-21 |
| 复核性质 | 上一轮 FAIL 后的**修复复核**（独立、只读，不修改任何生产/验收代码） |
| 结论 | **PARTIAL** |

> 判定依据（`.trae/rules/sql.md:69`）：真实目标引擎执行证据已取得（API 36.1 模拟器，24 例全绿、`GRADLE_EXIT=0`），故不是 FAIL；
> 但「升级路径被记为 N/A」所依赖的**替代证据（onUpgrade 委托）是空见证**，「迁移失败不得留下半迁移库」这一结果检查**缺失**，
> 属于「已有真实执行但升级、失败或结果检查不完整」，故不能 PASS。

---

## 0. 复核范围与方式

回读（非控制台回显）的文件：

| 文件 | 回读要点 |
| :--- | :--- |
| `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java`（529 行） | 七表 DDL、列常量、9 索引、6 触发器、前向迁移注册表 |
| `android/data/src/main/java/com/treelearn/data/db/TreeLearnDatabaseHelper.java`（75 行） | `onConfigure`/`onCreate`/`onUpgrade`/`onDowngrade`/`onOpen` |
| `android/data/src/main/java/com/treelearn/data/db/DatabaseTransactionRunner.java`（70 行） | 事务边界 |
| `android/app/src/androidTest/java/com/treelearn/app/SqliteSchemaInstrumentedTest.java`（1134 行，23 个 `@Test`） | 验收用例 |
| `artifacts/evidence/T-004-{green,tests,guard,guard-selftest,ddl-location,constraint-code-probe}.txt` | 证据文件逐行回读 |
| `.trae/tasks/T-2026-09-20-sqlite-schema-dao-transactions-004.md` | D1–D5 修复声明 |
| `.trae/rules/sql.md`；`Docs/05-technical-design/知芽（TreeLearn）Android SQLite 建表入场确认清单_V1.0.md`；`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` | 权威来源 |
| `android/core/src/main/java/com/treelearn/core/domain/`（Topic/Branch/BranchSource/BranchAnchor/Message/AnswerRevision/GenerationOperation/GenerationState/GenerationModel/Guard） | `:core` 实体字段面 |
| `android/README.md:199-219` | 文档同步 |

另做机械检索：全仓 `unread_from_ordinal|unread_count` 仅剩测试内的一处否定断言（`SqliteSchemaInstrumentedTest.java:336`）；
`CREATE UNIQUE INDEX|CREATE TRIGGER|ALTER TABLE|DROP TABLE|DROP INDEX` 在 `android/**` 全仓仅命中 `TreeLearnSchema.java` 的 11 行。

**证据时效性已核**：验收用例最后修改 `2026-09-21 14:20:13`，`T-004-green.txt` 生成于 `14:21`，三个生产类最后修改 `12:48`。
绿证晚于最后一次代码修改，**不是过期证据**。

---

## 一、上一轮每条 FAIL 是否真修好

| 编号 | 上轮指控 | 复核结论 | 文件:行号证据 |
| :--- | :--- | :--- | :--- |
| **D1** | `answer_revisions` 缺 `model` / `thinking_enabled` | **真修好** | 列常量 `TreeLearnSchema.java:118-119`；DDL `model TEXT NOT NULL`(`:279`)、`thinking_enabled INTEGER NOT NULL CHECK (... IN (0,1))`(`:280-281`)。与权威对齐：技术方案 §5.1「保存完整 reasoningContent、answerContent、**模型、思考模式**、promptPolicyVersion、生成时间和版本号」（`技术方案_V1.0.md:97`）；`:core` `AnswerRevision.restore(...)` 签名确实含 `model`/`thinkingEnabled`（`AnswerRevision.java:114-126`，字段 `:27-28`）。验收侧期望列集含 `model:TEXT:1`/`thinking_enabled:INTEGER:1`（`SqliteSchemaInstrumentedTest.java:196-197`），并有独立「revision 缺 model 须报 NOT NULL」断言（`:505-513`） |
| **D2** | `branches` 存了独立的「未读」权威列 | **真修好** | 全仓已无 `unread_from_ordinal`/`unread_count`（唯一命中是测试自身的否定断言 `:336`）。改为 §11 序列对：`latest_stable_sequence`（`:204-205`）+ `last_seen_stable_sequence`（`:206-209`），并用 CHECK 强制 `last_seen <= latest`。与 `:core` 一致：`Branch.java:25-26` 只有序列对，`unreadCount()` 是 `latest - seen` 的**派生值**（`:179-182`）。用例 `unreadHasASingleRepresentationInTheSequencePair`（`:331-364`）机械扫全列名禁 `unread`、断言序列对存在、负值与越序被 CHECK 拒、合法推进被接受 |
| **D3** | 约束断言不按成因（假见证） | **真修好** | 引入 `assertCause(scenario, message, expectedCode, requiredText)`（`:1009-1017`），判据为 SQLite **扩展错误码**常量（`:123-127`）。回读探针证据实测到真实报文格式 `NOT NULL constraint failed: ... (code 1299 SQLITE_CONSTRAINT_NOTNULL)`、`CHECK constraint failed: ... (code 275 SQLITE_CONSTRAINT_CHECK)`（`T-004-constraint-code-probe.txt:136-140`），故 `contains("(code 1299")` 这类前缀匹配成立。上一个假见证的具体场景（role CHECK 实际死在外键）已按成因改写：先建分支再插非法 role（`:577-585`） |
| **D4** | 事务 runner 单一边界可被工作体绕过 | **部分修好（见 P3）** | 工作体返回后检查 `database.inTransaction()`，被窃则抛 `IllegalStateException`（`DatabaseTransactionRunner.java:55-59`）；`finally` 仅在事务仍开着时 `endTransaction()`（`:62-68`），不会用「no current transaction」掩盖真因。用例覆盖嵌套开启与被窃两种情形（`:700-722`）。**但这是「事后检测」不是「阻止」**，见第三节 P3 |
| **D5** | 成因判据被 CHECK 文本里的 `IS NOT NULL` 欺骗 | **真修好** | 判据全面改为「错误码 + 具体约束文本」。两条都以 `parent_id` 起头的分支 CHECK 用互斥续段区分：`source_message_id IS NOT NULL`（`:596`）vs `source_message_id IS NULL`（`:608`）。role 相关断言改为要求 `role = 'AI'`（`:549`）/`role = 'USER'`（`:557`）并配错误码 275 |

**小结**：上一轮五项技术缺陷中，D1/D2/D3/D5 已真正闭环并有可核行号；D4 的修复是「检测并抛错」而非「无法绕过」，属于残留，单列为 P3。

---

## 二、列集逐表核对（权威文档 × `:core` 实体 × DDL）

逐表比对「清单 §2 / 技术方案 §5.1§5.2§6.1§11」与 `:core` 实体字段面，结论：**无漂移**（下表 `=` 表示 DDL 列集 == 权威 ∪ `:core` 字段面）。

| 表 | `:core` 实体字段 | DDL 列数 | 核对结果 |
| :--- | :--- | :--- | :--- |
| `topics` | `Topic`: id / lastVisitedBranchId / createdAt / updatedAt | 4 | `=`。无 `title`（清单 §2「不保存独立主题名」）✓ |
| `branches` | `Branch`: id/topicId/parentBranchId/title/createdAt/updatedAt/latestStableSequence/lastSeenStableSequence/collapsed/archivedAt + `BranchSource`(parent/sourceMessageId/sourceRevisionId/contextSnapshotId) + `BranchAnchor`(start/end/quote/hash) + §11 阅读位置四段 | 20 | `=`（含 `reading_anchor_message_id/revision_id/list_item_offset/viewport_offset`，§11:266） |
| `messages` | `Message`: id/branchId/role/ordinal/userContent/createdAt/activeRevisionId/activeOperationId | 8 | `=` |
| `answer_revisions` | `AnswerRevision`: id/messageId/revisionNo/reasoningContent/answerContent/**model**/**thinkingEnabled**/promptPolicyVersion/createdAt | 9 | `=`（D1 补齐后） |
| `generation_operations` | `GenerationOperation`: id/commandId/branchId/messageId/purpose/promptPolicyVersion/model/thinkingEnabled/createdAt/state/partialReasoning/partialAnswer | 14 | `=` + 2 个纯持久化列（`error_code` 承载 §9.5 错误分类；`updated_at`），合理 |
| `context_snapshots` | 无 `:core` 实体（按清单 §2 / §5.3） | 6 | `=`（id/branch_id/schema_version/digest/payload/created_at） |
| `ai_artifacts` | 无 `:core` 实体（按 §10） | 10 | `=`（含 `based_on_revision_id`/`input_digest` 防旧写回） |

**唯一不一致（列级）**：`branches.context_snapshot_id` 在 DDL 中可空（`TreeLearnSchema.java:228-229`），且非根分支 CHECK（`:239-241`）只强制 `source_message_id`/`source_revision_id`/`anchor_hash` 非空，**没有强制 `context_snapshot_id` 非空**；而 `:core` 的 `BranchSource.restore` 对它走 `Guard.id`（`BranchSource.java:44`），`Guard.id` 对 null 直接抛 `IllegalArgumentException`（`Guard.java:77-80`）。后果：一条**符合 schema 的非根分支行**（`context_snapshot_id IS NULL`）在 `:core` 再水合时必炸；而验收套件自己的「正常分叉」夹具正是这么插的（`SqliteSchemaInstrumentedTest.java:927-934`，`INSERT INTO branches(... source_message_id, source_revision_id, anchor_quote, anchor_hash)` 未列 `context_snapshot_id`）。§5.2 明确把 `contextSnapshotId` 列为非根分支必存字段（`技术方案_V1.0.md:110`）。见 P4。

---

## 三、发现清单（按严重度）

### P1（高）「onUpgrade 委托给注册表」这条替代证据是**空见证**

- **位置**：`SqliteSchemaInstrumentedTest.java:830`（`helper.onUpgrade(db, 1, 1)`）+ 断言 `:831-832`；方法名与注释 `:823-829`
- **为什么是问题**：这是验收项「升级路径 N/A」的**替代证据**（任务文件 `:82` 明确把「机械断言 `onUpgrade` 存在且委托给前向迁移注册表」当作 N/A 的替代）。但同版本 `onUpgrade(1→1)` 走 `applyForwardMigrations` 时循环 `for (version=1; version<1; ...)` 一次都不执行，是**纯空操作**。若 `onUpgrade` 被写成空方法体 `{}`，该用例**照样通过**——断言通过、声称的不变量（委托）完全没被触及。这正是本轮要找的假见证类型。
- **建议怎么修**：改成能区分「委托」与「空实现」的断言——
  ```java
  IllegalStateException delegated = expectedFailure(IllegalStateException.class, "onUpgrade delegation",
          () -> helper.onUpgrade(db, 1, 2));           // 注册表无 1->2 步骤
  assertTrue(delegated.getMessage().contains("no forward migration is registered from schema version 1"));
  ```
  空实现的 `onUpgrade` 不会抛这个异常，只有真正委托才会。现用例中直接调 `TreeLearnSchema.applyForwardMigrations(db,1,2)`（`:839`）只验了注册表本身，没验委托。

### P2（中高）迁移失败后**没有断言 `user_version` 未被改写**，即「半迁移库被盖上新版本号」未被证伪

- **位置**：`SqliteSchemaInstrumentedTest.java:837-847`——捕获 `IllegalStateException` 后只断言异常消息，**没有回读 `PRAGMA user_version`**
- **为什么是问题**：`.trae/rules/sql.md:43` 要求「migration 失败不得留下被误报为最新版本的半迁移数据库；失败路径须证明事务回滚或明确的失败关闭行为」。当前缺的正是这条**结果检查**。代码层面其实大概率是对的（`SQLiteOpenHelper` 把 `onUpgrade` 包在事务里并在其中 `setVersion`，抛出即回滚），但那是未验证的假设，不能代替实测。
- **建议怎么修**：在 `:842` 之后补
  ```java
  assertEquals("a failed migration must not stamp a newer user_version",
          1, rawInt(db, "PRAGMA user_version"));
  ```
  并另加一例：真实走生产入口的失败升级（`helper.onUpgrade(db, 1, 2)` 抛错后回读 `user_version` 与七表仍在）。

### P3（中）事务 runner 只**检测**被窃事务，不能**阻止**；且用例未固定「已提交」这一语义

- **位置**：`DatabaseTransactionRunner.java:50-59`；用例 `SqliteSchemaInstrumentedTest.java:713-721`
- **为什么是问题**：工作体 `setTransactionSuccessful() + endTransaction()` 会**真正提交**外层事务（Android 的 `beginTransaction()` 在已有事务中静默并入，`endTransaction()` 到栈底即 COMMIT）。runner 只能在提交**之后**发现并抛异常。于是出现一个矛盾信号：**数据已落库，调用方却收到异常**。代码注释（`:50-54`）对此是诚实的，但任务/报告表述为「runner 无法被绕过」（任务 `:114-118`、报告 `:221`）与实现不符——准确说法是「可被绕过，但会被检测并上报」。此外该用例的工作体**没有写任何行**，因此「被窃的事务到底回滚了没有」从未被断言，留下了反向误解空间（读者可能以为抛异常=已回滚）。
- **建议怎么修**：① 把 runner 的 javadoc/注释与任务表述统一为「检测并上报，不承诺阻止」；② 在用例的窃取工作体里先 `insertTopic(workDatabase, "t-stolen")` 再窃取，然后显式断言 `rowCount(db,"topics")` **增加**——把「已提交、不可回滚」这一真实语义钉死，避免它日后被读成回滚保证；③ 若确实要阻止，需把 `SQLiteDatabase` 包一层只暴露 DML 的门面（不允许工作体碰事务 API），否则无法从 runner 内部阻止。

### P4（中低）`branches.context_snapshot_id` 的可空性与 `:core` 再水合契约冲突

- **位置**：`TreeLearnSchema.java:228-229`（FK，可空）、`:239-241`（非根 CHECK 未要求其非空）；`BranchSource.java:44` + `Guard.java:77-80`（null 即抛）
- **为什么是问题**：见第二节。schema 允许的行无法被 `:core` 重建；验收夹具自己的正常分叉行就是这种行（`:927-934`）。
- **建议怎么修**（二选一，都要写进清单 §2 或 §5.2 的决策记录）：
  - (a) 把 `context_snapshot_id IS NOT NULL` 加入非根分支 CHECK，夹具改为「先 INSERT 分支 → 建快照 → 同一事务内 UPDATE 回填」（注意 `branches ↔ context_snapshots` 是循环外键，单条语句无法同时满足，必须两步行）；或
  - (b) 把 `BranchSource.restore` 的 `contextSnapshotId` 改为 `Guard.idOrNull`，并同步 §5.2 说明「快照引用可空」。
  推荐 (a)：§5.2 已把它列为非根分支必存字段，且快照是「不可变出生上下文」，缺失会让分支来源不可复现。

### P5（低）「迁移链无缺口」循环空转，注释过度声明

- **位置**：`SqliteSchemaInstrumentedTest.java:849-866`，尤其注释 `:850-851`「The loop counter is asserted against the list size so this can never silently iterate zero times」
- **为什么是问题**：`FORWARD_MIGRATIONS` 为空（`TreeLearnSchema.java:483`），循环**确实一次没跑**，`assertEquals(0, 0)` 是空真。注释声称「永远不会静默空转」与事实相反。真正防的是「将来非空时被跳过」，而不是现在。
- **建议怎么修**：把注释改为「当前列表为空，本段在 V1 是空真；一旦注册首个迁移它才开始起作用」，避免复核者把它当成现役证据。

### P6（低）DDL 位置机械检索漏掉 `CREATE UNIQUE INDEX` / `CREATE TRIGGER` / `DROP`

- **位置**：`artifacts/evidence/T-004-ddl-location.txt:1`（命令 `grep -rn "CREATE TABLE|ALTER TABLE|CREATE INDEX" android --include=*.java`）；命中仅 `:3-8`
- **为什么是问题**：`CREATE UNIQUE INDEX` **不含**子串 `CREATE INDEX`，所以 9 条索引里只有 4 条被扫到（回读确认：`CREATE UNIQUE INDEX` 出现在 `TreeLearnSchema.java:363/365/367/370/375`，全部未进证据）；6 条 `CREATE TRIGGER`（`:402/405/410/413/418/422`）与任何 `DROP`/`ALTER` 也不在检索范围内。也就是说「DDL 只有一处产地」这条守门对 11 处真实 DDL 语句是**盲的**——目前结论碰巧为真，但守门强度不足。
- **建议怎么修**：检索式改为 `CREATE\s+(UNIQUE\s+)?INDEX|CREATE\s+TABLE|CREATE\s+TRIGGER|ALTER\s+TABLE|DROP\s+(TABLE|INDEX|TRIGGER)`，并覆盖 `*.java` 之外的资源目录；重出一份证据文件。

### P7（低）探针证据只记录了 5 个错误码中的 2 个

- **位置**：`artifacts/evidence/T-004-constraint-code-probe.txt` 全文；任务文件 `:112` 声称「实测：1299 NOT NULL、275 CHECK、2067 UNIQUE、787 FOREIGN KEY、1811 TRIGGER」
- **为什么是问题**：文件里实际出现的只有 `1299`（`:136`）与 `275`（`:137`、`:140`）。`2067/787/1811` 在该文件中**没有任何实测输出**。这三者与 SQLite 官方扩展码定义一致、且绿跑通过（用例确实断言了这三个码并通过）可以佐证其正确，但**证据文件与结论不一致**（属本轮第 6 问）。
- **建议怎么修**：补一次五码全量的探针输出（或在 `T-004-tests.txt` 旁附各码出现次数统计）；若不再复跑，就把任务文件 `:112` 的表述改为「1299/275 由探针实测，其余三码取自 SQLite 扩展错误码定义并由绿跑间接验证」。

### P8（低）`T-004-tests.txt` 缺少任务引用的 JUnit XML 计数

- **位置**：`artifacts/evidence/T-004-tests.txt:1-25`（仅 24 个用例名，首行写 `Command: grep -c from JUnit XML` 但无计数行）；任务文件 `:139` 与 `:155` 引用 `tests="24" failures="0" errors="0" skipped="0"`
- **为什么是问题**：全仓 `artifacts/evidence/T-004-*.txt` 中**检索不到** `tests=`/`failures=`/`errors=`/`skipped=` 任何一个（已用 grep 确认）。引用了一个不在证据文件里的数字。结论本身可信（`T-004-green.txt:133-135` 显示 24 started/24 finished，`:145-147` `BUILD SUCCESSFUL` + `GRADLE_EXIT=0`，Gradle 在有失败测试时必然 FAILED），但**引用与文件不一致**。
- **建议怎么修**：把 JUnit XML 的统计行（`tests/failures/errors/skipped`）与 XML 路径追加进 `T-004-tests.txt`，或把任务里的引用改成指向 `T-004-green.txt` 的实测行。

### P9（低）`android/README.md` 未记录两条「禁止直接删除」触发器

- **位置**：`android/README.md:211-214` 只写了 4 条不可变不变量；`TreeLearnSchema.java:405-409/413-417` 的 `trg_answer_revisions_no_direct_delete`、`trg_context_snapshots_no_direct_delete` 未提及
- **为什么是问题**：`:data` 入口索引是 022/023 写 DAO 的第一手文档。这两条触发器会让 `DELETE FROM answer_revisions/context_snapshots` **直接 ABORT**（仅当属主行仍存在时），是极易踩到的行为；漏记会造成下游误判「删不掉是 bug」。
- **建议怎么修**：README 补一句「稳定版本与出生快照只允许随属主行一起消失，直接 DELETE 会被 ABORT」。

### P10（低）序列断言可能由另一条 CHECK 命中，无法区分

- **位置**：`SqliteSchemaInstrumentedTest.java:353-357`（`UPDATE latest_stable_sequence=-1` 后要求文本含 `latest_stable_sequence`）
- **为什么是问题**：`last_seen_stable_sequence` 的 CHECK 表达式本身也引用了 `latest_stable_sequence`（`TreeLearnSchema.java:206-209`），且 SQLite 在 UPDATE 时会重算所有引用被改列的 CHECK。若先命中 `last_seen...<= latest_stable_sequence`，消息文本同样含 `latest_stable_sequence`，断言仍通过，但命中的是另一条约束。当前不影响结论（两条都是序列约束），但判据不够锐。
- **建议怎么修**：把 `requiredText` 收紧为 `latest_stable_sequence >= 0`（该片段只出现在 `:204-205` 的那条 CHECK 中）。

---

## 四、事务边界专项（第 4 问）

| 检查项 | 结论 | 证据 |
| :--- | :--- | :--- |
| 嵌套开启被拒 | **是**（抛 `IllegalStateException`，不静默并入） | `DatabaseTransactionRunner.java:41-45`；用例 `:704-709` 并断言结束后 `!db.inTransaction()` |
| 工作体抛异常时**真回滚** | **是** | `:47` begin → `:49` 执行 → 异常直接穿出 → `finally :65-67` 在未 `setTransactionSuccessful` 的情况下 `endTransaction()`（即 ROLLBACK）。用例 `:688-696` 断言提交后 `topics` 仍为 1 且 `t-rolled-back` 行数为 0——**不是靠空表通过** |
| 提交成功可见 | 是 | 用例 `:682-686` |
| 单一边界**无法被绕过** | **否**（只能检测，不能阻止） | 见 P3 |
| 参数缺失走 `IllegalArgumentException` | 是 | `:35-40`；用例 `:725-730` |

另：runner 未处理「`beginTransaction()` 自身抛错」的情形，但 `finally` 里 `inTransaction()` 为 false 会跳过 `endTransaction()`，不会二次抛错掩盖真因，行为正确。

---

## 五、迁移机制专项（第 5 问）

| 检查项 | 结论 | 证据 |
| :--- | :--- | :--- |
| `onUpgrade` 真委托给注册表 | **代码是真委托；测试是空见证** | 委托：`TreeLearnDatabaseHelper.java:44-48` → `TreeLearnSchema.applyForwardMigrations`。测试缺陷见 P1 |
| 缺步 fail-closed（不执行任何 DDL 就抛错） | **是** | `TreeLearnSchema.java:499-508`：`findMigrationFrom(version)` 为 null 即抛，且在 `step.apply` **之前**；`:495-498` 额外拒绝降级 |
| 缺步时**不改写 `user_version`** | **代码上成立（依托 `SQLiteOpenHelper` 事务包装），但未实测** | 见 P2 |
| 已发布迁移步骤不可改写 | 是（V1 为空注册表，`:483`；注释 `:476-482` 说明非占位） | |
| 降级 fail-closed | 是 | `TreeLearnDatabaseHelper.java:51-56` 抛 `IllegalStateException`；`applyForwardMigrations:495-498` 二次兜底；用例 `:844-847` |
| V1 升级路径记 N/A | **合规** | `.trae/rules/sql.md:40`「首个尚未发布版本可将升级路径记为 N/A」；任务未编造前驱版本（`:82`、`:156-157`） |
| 单调递增 / 单一入口 | 是 | DDL 唯一产地（见 P6 的强度问题）；`DATABASE_VERSION=1`（`TreeLearnSchema.java:51`） |

---

## 六、证据文件回读核对（第 6 问）

| 证据文件 | 声明 | 回读结果 | 判定 |
| :--- | :--- | :--- | :--- |
| `T-004-green.txt` | 24 例全绿、exit 0 | `:133-135` 24 started / 24 finished；`:145-147` `BUILD SUCCESSFUL` `GRADLE_EXIT=0`；设备 `Medium_Phone_API_36.1(AVD) - 16` | 一致 |
| `T-004-tests.txt` | 24 个用例名 | 实列 24 个（含 T-002 的 `mainActivityRendersFirstScreenWithoutCrash`），本任务 23 个；与测试文件 23 个 `@Test` 逐名比对一致 | 一致（但缺计数，见 P8） |
| `T-004-guard.txt` | Java-only guard PASS、13 OK 行、exit 0 | `:29` `VERDICT: PASS (13 OK line(s))`；`:22` release APK 56012 B / 1 dex | 一致 |
| `T-004-guard-selftest.txt` | 完整模式自检 PASS | 末行 `OVERALL = SELFTEST PASS (40 case(s))`、`SELFTEST_EXITCODE=0`；`:2-5` 含 Generated/Command 与 Guard、Library 的 SHA-256 及行数 | 一致 |
| `T-004-ddl-location.txt` | DDL 仅 `TreeLearnSchema.java` 命中 | 命中 `:356/358/360/372/429/520`；**漏** 5 条 `CREATE UNIQUE INDEX` 与 6 条 `CREATE TRIGGER` | 结论正确但检索不全（P6） |
| `T-004-constraint-code-probe.txt` | 五码实测 | 仅出现 `1299`、`275`；该文件自身 `BUILD FAILED`/`GRADLE_EXIT=1`（探针性质，符合预期） | 与声明不一致（P7） |
| 任务文件引用 `tests="24" failures="0"...` | — | 六份 T-004 证据文件中 grep 不到该串 | 引用与文件不符（P8） |
| 任务文件「9 索引含 2 偏索引 + 6 触发器」 | — | `INDEX_STATEMENTS` 9 条、`TRIGGER_STATEMENTS` 6 条、偏索引 2 条 | 一致 |

未发现**过期**证据（绿证 14:21 > 用例最后修改 14:20:13 > 生产代码 12:48）。

---

## 七、PASS 前提下必须说明的边界

**我验证了什么（有可核证据）**

1. 真实 Android SQLite（API 36.1 模拟器）上 23 例验收用例 + 1 例回归全绿，命令 `:app:connectedDebugAndroidTest`，退出码 0。
2. 上一轮 D1/D2/D3/D5 四项修复在代码与验收两侧均有行号级证据（第一节）。
3. 七表列集与清单 §2、技术方案 §5.1/§5.2/§6.1/§11 及 `:core` 实体字段面逐表比对，除 `context_snapshot_id` 外无漂移（第二节）。
4. 事务 runner 的提交/回滚/嵌套拒绝/参数校验均由非空表断言支撑（第四节）。
5. 迁移注册表缺步即抛、拒绝降级；DDL 唯一产地（强度不足见 P6）。
6. 六份证据文件逐行回读，时效性成立（第六节）。
7. 专项 guard：`android-no-kotlin-guard` PASS 且自检完整模式 PASS。

**我没验证什么（不作为 PASS 依据，也不作为 FAIL 依据）**

1. **没有实机复跑**本轮测试——结论基于回读代码 + 回读证据文件，未自行执行 `connectedDebugAndroidTest`。
2. **没有真实 upgrade 执行**（V1 无前驱，按 `sql.md:40` 合规记为 N/A）；正因如此 P1 的空见证才关键——它是这条 N/A 唯一的替代证据。
3. **没验证 DAO 层**（022/023 未开工）：无 `Cursor → 实体` 映射、无 `restore` 只读路径的机械检索、无首题原子事务。
4. `:core` 缺 `Branch.restore` / `Message.restore`（`Branch.java` 仅有 `root`/`child`，`Message.java` 仅有 `user`/`ai`），因此 `title/updatedAt/collapsed/archivedAt/序列对/阅读位置` 与 `activeRevisionId/activeOperationId` 目前**无再水合入口**。这是 `:core` 的既有缺口，不属于 T-004 的 DDL 缺陷，但会由 022/023 直接撞上，建议在 022 开工前解决。
5. 未验证 `error_code`/`updated_at`（`generation_operations` 的纯持久化列）的取值域——`:core` 无对应字段，也无 CHECK 约束（`TreeLearnSchema.java:303-305`）。
6. **规模例外（物理 674 行 > 500 行硬上限）仍是治理阻塞项**，本报告不对其作判断。

---

## 八、修复指令（给实现者，按优先级）

1. **P1**：`SqliteSchemaInstrumentedTest.java:830` 改为 `helper.onUpgrade(db, 1, 2)` 并断言抛「no forward migration is registered from schema version 1」，使「委托」可被证伪。
2. **P2**：在 `:842` 之后补 `assertEquals(1, rawInt(db, "PRAGMA user_version"))`，并补一例走生产入口的真实失败升级路径。
3. **P3**：统一「检测而非阻止」的表述；在窃取用例的工作体里写一行并断言其**可见**，钉死语义。
4. **P4**：决定 `branches.context_snapshot_id` 非根必填（改 CHECK + 夹具两步写）或 `:core` 改 `idOrNull`，并回写 §5.2/清单 §2。
5. **P5–P10**：注释纠偏、DDL 检索式补全、探针五码补齐、JUnit 计数入证据、README 补两条 DELETE 守卫、序列断言文本收紧。

以上 1–2 项完成并复跑绿证后，本任务的「升级/失败路径」替代证据即完整，可重新提交复核。
