# T-004 独立验收复核报告（verifier · 第三轮）

| 项目 | 内容 |
| :--- | :--- |
| 角色 | `verifier`（READONLY；本轮只读生产代码、验收套件、证据与文档，未修改任何文件） |
| 被验收任务 | `T-2026-09-20-sqlite-schema-dao-transactions-004.md`（status: `verifying`，自评 `VERIFYING`） |
| 目标引擎 | Android SQLite（`android.database.sqlite.SQLiteDatabase` / `SQLiteOpenHelper`，生产路径） |
| 复核日期 | 2026-09-21 |
| 设备 | `emulator-5554` / `Medium_Phone_API_36.1(AVD) - 16` / API 36 |
| **结论** | **PARTIAL**（不得判 PASS，**不建议**解除 `VERIFYING`） |

> 与并行产出的 `artifacts/evidence/T-004-verification-db-review.md`（`db-migration-reviewer` 第二轮，结论 PARTIAL）
> 是两份独立报告。本报告不替代该守门，凡引用其结论处均已自行回读复核并标注。

---

## 0. 本轮我实际执行的命令（可复现）

| 目的 | 命令 | 结果 |
| :--- | :--- | :--- |
| 复跑验收套件 | `cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain` | `Starting 24 tests` / `Finished 24 tests`，`BUILD SUCCESSFUL`，耗时 38s |
| JUnit XML 回读 | `grep -o 'tests=…' …/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml` | `tests="24" failures="0" errors="0" skipped="0"`（timestamp `2026-09-21T06:37:18`，本轮复跑产物） |
| 本任务用例数 | `grep -c 'classname="com.treelearn.app.SqliteSchemaInstrumentedTest"' …` | **23** |
| `:core` 回归 | `./gradlew :core:test :data:test :app:testDebugUnitTest verifyDependencyDirection` | `BUILD SUCCESSFUL`；`:core` 11 个测试类共 120 用例，`failures=0 errors=0`；依赖方向 `PASS` |
| Java-only 守门（复跑） | 复用 `T-004-guard.txt`（14:22 生成，晚于生产代码 12:48） | `VERDICT: PASS (13 OK line(s))`、`EXITCODE=0` |
| lint | `./gradlew :app:lintDebug :data:lintDebug` + `python scripts/dev/lint_summary.py` | `BUILD SUCCESSFUL`；**0 error / 11 warning**（全部为既有资源与版本提示，与本次改动无关） |
| 规模实测 | `wc -l *.java`（`android/data/src/main/java/com/treelearn/data/db`） | `TreeLearnSchema` 529 / `TreeLearnDatabaseHelper` 75 / `DatabaseTransactionRunner` 70 = **物理 674** |
| 规模实测（代码口径） | `grep -v -E '^[[:space:]]*$|^[[:space:]]*//|^[[:space:]]*\*|^[[:space:]]*/\*'` | 374 + 46 + 35 = **非空非注释 455** |
| DDL 产地检索 | `grep -rn "CREATE TABLE\|ALTER TABLE\|CREATE INDEX\|CREATE UNIQUE INDEX\|CREATE TRIGGER\|DROP TABLE" android --include=*.java` | 18 处命中，**全部**落在 `TreeLearnSchema.java` |
| 契约校验 | `python .trae/scripts/validate_tasks.py` | `SUMMARY files=23 errors=0 warnings=2` |
| dex 实测 | `unzip -l app/build/outputs/apk/debug/app-debug.apk` | `classes.dex` + `classes2/3/4.dex` = **4 个 dex** |

---

## 1. 规模核对（第 3 问）

任务文件 `:148` 自述「物理 674 行 / 非空非注释 455 行」，**两个数字均可复现**（见上表）。
`TreeLearnSchema` 529 / 374 亦与 `:148` 一致。数字没有编造。

**但「唯一阻塞项」的依据口径与仓库既有先例冲突**（详见 F4）：

- `.trae/rules/harness-tasks.md:45` 只写「被修改的生产代码行」，未定义物理行还是代码行。
- T-003（`.trae/tasks/T-2026-09-20-model-immutable-answer-domain-003.md:156`）与
  T-021（`T-2026-09-21-generation-operation-prompt-anchoring-021.md:261`，原文「口径与 T-003 一致：
  **只计非空非注释的生产代码行**」）已确立**非空非注释**为 Task v2 预算口径。
- 按该口径 **455 < 500，不触发硬失败**，只有 >200 的软警告（`validate_tasks.py` 已如实输出该 WARNING）。
- 任务文件 `:148`/`:158` 改用物理行 674 判「超 500 硬上限」，与先例不一致；
  且 `:205-207` 当初自设的门槛写的是「实测生产行数 **> 400** 必须拆第四个切片」——
  455（代码口径）已越过这条自设门槛，任务文件却未在「TDD or sizing exception」里回应它。

结论：**规模是否构成硬阻塞，取决于口径，不能由 verifier 代判**，须用户裁定（见第 6 节）。

---

## 2. 验收条件逐条核对（第 1 问）

任务文件「Acceptance criteria」实际为 **12 条**（`:77-88`），题干记为 13 条。逐条如下。

| # | 验收条件 | 判定 | 证据 / 复现方式 |
| :--- | :--- | :--- | :--- |
| 1 | 同一命令先 RED 后 GREEN | **满足** | RED/GREEN 命令逐字符相同（`T-004-red.txt:7` vs `T-004-green.txt`）；RED exit 1、GREEN exit 0；**RED 为编译级失败，任务文件 `:133` 已如实标注证据强度**，未伪装成行为级 RED |
| 2 | 新装走生产 `onCreate`，恰好 7 表、`user_version=1` | **满足** | `SqliteSchemaInstrumentedTest.java:249-258`；我复跑 24/24 全绿 |
| 3 | 列名/类型/NOT NULL 与清单 §2、§6.1 集合比较 | **部分满足** | `:265-284` 确为 `LinkedHashSet` 集合比较（非字符串包含）；但 `context_snapshot_id` 一处与权威不符 → **F5** |
| 4 | 索引与约束存在且生效（含偏索引 API 36 实测） | **满足** | `:393-413`（9 索引集合比较 + `WHERE` 偏索引判定）、`:448-487`（偏索引「允许的那一半必须成功」反向证明，非空的真实行计数 3） |
| 5 | 每次打开 `foreign_keys=1`、`foreign_key_check` 无结果、`integrity_check=ok` | **满足（有覆盖关系需说明）** | `:371-386`；但**该例本身在零行上成立**（空库也返回 0/ok），不变量实际由 `:874-885`（重开后已有行）与 `:612-648`（级联后）覆盖 → **F12** |
| 6 | 升级路径 N/A + 替代证据（委托、无缺口链、版本绑定） | **未满足** | 替代证据是**空见证** → **F1**；失败后未校验 `user_version` → **F3**；无缺口链断言空真且有 off-by-one → **F9** |
| 7 | 已有 `user_version=0` 空库文件经生产 helper 走 `onCreate` | **满足** | `:888-907`，先断言原始库 `0` 表 / 版本 0，再断言打开后 7 表 / 版本 1，**非空真** |
| 8 | 事务 runner：回滚、提交可见、重复开启被拒 | **部分满足** | `:678-731` 三条断言均非空的真实行计数；但「无法被绕过」表述与实现不符 → **F6** |
| 9 | 时间列 `INTEGER`、DDL 无 SQLite 时钟函数 | **满足** | `:287-307`，逐列 `_at` 判类型 + 7 种时钟函数 `assertFalse` |
| 10 | 不落 system 正文（schema 级全列名检索） | **满足** | `:310-328`，`TreeSet` 精确等于 `{prompt_purpose, prompt_policy_version}`；写路径哨兵扫描已确实下推到 023（`.trae/tasks/…-023.md:87`） |
| 11 | DDL 位置：除 helper/schema 常量外零命中 | **结论为真，守门不足** | `T-004-ddl-location.txt` + 我复跑确认；但检索式漏 11 处真实 DDL → **F7** |
| 12 | `db-migration-reviewer` 按 Android SQLite 守门复核 | **未满足** | 该守门已跑（第二轮，14:38），结论 **PARTIAL** 而非 PASS → **F2** |

**关于三类假证据的专项排查结果：**

- ①「断言通过但没触及声称的不变量」：**命中 1 处**（F1，`onUpgrade` 委托断言）。
  上一轮 D3/D5 的同类问题已真正修复：`assertCause` 用扩展错误码 + 具体约束文本，
  两条 `parent_id` 起头的分支 CHECK 用互斥续段区分（`:596` vs `:608`）。
- ②「空表/零行也能过」：**命中 1 处（低危）**（F12，PRAGMA 例）；
  其余计数型断言均有真实种子行（`onlyOneRootBranchPerTopic:466` = 3；
  `generationOperationActiveLimitIsAPartialIndex:486` = 3；
  `deletingATopicCascades…:640` ai_artifacts = 1 且显式排除「空表也能过」的写法）。
- ③「任务数字与证据文件不一致」：**命中 2 处**（F8）。

---

## 3. TDD 契约核对（第 2 问）

| 项 | 核对结果 |
| :--- | :--- |
| RED 与 GREEN 同一命令 | **是**，`T-004-red.txt:7` 与 GREEN 命令逐字符相同（任务文件 `:130`/`:137`） |
| RED 真实失败 | **是**，`:app:compileDebugAndroidTestJavaWithJavac FAILED`、exit 1（`T-004-red.txt:8-9`） |
| RED 是否如实标注强度 | **是**。任务文件 `:133` 明确写「**证据强度如实标注：这是编译级 RED，不是行为级 RED**」，`T-004-red.txt:11-16` 复述同一判断。**未发现把编译失败包装成行为级 RED**。 |
| GREEN 真实通过 | **是**，`T-004-green.txt:133-147`（24 started/finished、`BUILD SUCCESSFUL`、`GRADLE_EXIT=0`）；**我本轮独立复跑复现**（24/24、exit 0） |
| 测试文件在 RED→GREEN 间是否被改绿 | 任务文件 `:136` 声明未修改。**无法从证据反证**（RED 阶段无测试文件快照留档），但 RED 缺的正是三个生产类型、GREEN 补的正是这三个类型，逻辑自洽 |
| 用例数 21→24 的说明 | 任务文件 `:139` 给出增删明细，与实测 23 个 `@Test` + 1 个 T-002 用例一致（`:139` 自述「23 例属本任务」，与 XML 中 `SqliteSchemaInstrumentedTest` 计数 23 吻合） |

RED 证据的一处质量问题见 **F13**（无原始 javac 日志，`21 errors` 不可回推）。

---

## 4. 文档一致性核对（第 4 问）

| 文档面 | 核对结果 |
| :--- | :--- |
| `android/README.md:199-219`（持久化入口索引） | **已同步且与生产代码一致**：三个类型职责、`PRAGMA foreign_keys` 回读 fail-closed、拒绝降级、两条偏索引语义均可在代码中逐条对上（`TreeLearnDatabaseHelper.java:51-74`、`TreeLearnSchema.java:363-377`） |
| `android/README.md:216-219`（两条偏索引由测试反向证明） | **属实**：`:448-487` 与 `:452-466` 确有「允许的那一半必须成功」断言 |
| `android/README.md:58`（`app-debug.apk` 约 70 KB、**3 个 dex**） | **已被本次改动变成不实** → **F10**（实测 4 个 dex） |
| `android/README.md:211-214`（4 条不可变触发器） | 属实，但**漏记 2 条 no-direct-delete 触发器** → **F14** |
| 建表清单 §2 / 技术方案 §6.1 七表 | 表名与值域一致；`unread` 措辞差异见 F15 附注（非本次引入） |
| 依赖方向 / `:core`、`:data` 模块状态 | `verifyDependencyDirection` PASS；`:core` 120 用例全绿 |
| 任务文件自身 | 存在两个 `## Verification report` 段，第二个仍写 `PENDING` → **F11** |

---

## 5. 回归核对（第 5 问）

| 项 | 结果 |
| :--- | :--- |
| `:core` 既有测试 | **未受影响**。`android/core/**` 本次零改动（三个生产文件全在 `:data`）；`:core:test` 11 类 / 120 用例 `failures=0 errors=0` |
| `:data` / `:app` 单元测试 | `:data:test`、`app:testDebugUnitTest` 均为 `NO-SOURCE`（本任务未引入 JVM 单测，符合项目「instrumentation 统一放 `:app`」约定） |
| 依赖方向 | `verifyDependencyDirection: PASS`（`:tree-layout` 空跑警告为既有） |
| Java-only 守门 | **是「有判据行」而非只有退出码**：`T-004-guard.txt:20-25` 含 5 条 `verifyNoKotlinDex:` 判据行（含 `PASS (3 APK(s) inspected …)` 与 APK 计数一致性断言），`:29` `VERDICT: PASS (13 OK line(s))`，`:30` `EXITCODE=0`。符合 AGENTS.md:86「判据必须有证据存在性断言」 |
| 守门双向自检 | `T-004-guard-selftest.txt` 末行 `OVERALL = SELFTEST PASS (40 case(s))`、`SELFTEST_EXITCODE=0`；文件头含 `Generated`/`Command` 与 Guard、Library 的 SHA-256 及行数；与 `android/README.md:152` 声明的「完整模式 40 例」一致 |
| lint | `:app:lintDebug` + `:data:lintDebug` 均 `BUILD SUCCESSFUL`，0 error |

---

## 6. 遗留下推核对（第 6 问）

**结论：这次下推是真的写进了 022/023，没有出现「声称下推但仓库里没有」的情况。**

| T-004 声明下推的职责 | 落点 | 是否在文件里 |
| :--- | :--- | :--- |
| `BranchSource.restore` 只许出现在只读映射 + 机械检索 | `.trae/tasks/…-022.md:81` | ✅ 原文包含「本任务对 `android/data` 全部生产源码做机械检索并给出命令与完整输出」 |
| `AnswerRevision.restore` 只许在只读映射 | `.trae/tasks/…-023.md:86` | ✅ |
| 不得新增 `GenerationOperation.restore` + 机械检索 | `…-023.md:86`、`:120` | ✅ |
| 首题原子创建事务（五类行） | `…-023.md:79-80` | ✅ |
| 写路径哨兵子串全表扫描（不落 system 正文） | `…-023.md:87` | ✅ |
| `generation_operations` 按行形态实现 | `…-023.md:120` | ✅ 并引用了 T-004 的「开工前实测①」 |
| 022/023 状态与依赖 | 两者 `status: ready`、`depends_on` 均含 T-004 文件名 | ✅ `validate_tasks.py` 对两者 PASS |

---

## 7. 发现清单（按严重度）

### F1（高）验收条件 6 的替代证据是**空见证**：`onUpgrade` 委托断言

- **位置**：`android/app/src/androidTest/java/com/treelearn/app/SqliteSchemaInstrumentedTest.java:830-832`；任务文件 `:82`
- **为什么**：`helper.onUpgrade(db, 1, 1)` 走 `applyForwardMigrations(db,1,1)`，其循环 `for (version=1; version<1; …)`（`TreeLearnSchema.java:499`）**一次都不执行**。把 `TreeLearnDatabaseHelper.onUpgrade` 改成空方法体 `{}`，该用例**照样通过**——断言过了，「委托」这个不变量从未被触及。这正是本轮要找的第一类假证据。
- **怎么修**：改为能区分「委托」与「空实现」的断言——
  ```java
  IllegalStateException delegated = expectedFailure(IllegalStateException.class, "onUpgrade delegation",
          () -> helper.onUpgrade(db, 1, 2));   // 注册表无 1→2 步骤
  assertTrue(delegated.getMessage().contains("no forward migration is registered from schema version 1"));
  ```
  空实现不会抛这个异常，只有真正委托才会。现用例 `:839` 直调 `TreeLearnSchema.applyForwardMigrations` 只验了注册表本身，**没验委托**。

### F2（高）验收条件 12 未取得 PASS：专项守门本轮结论是 PARTIAL

- **位置**：`artifacts/evidence/T-004-verification-db-review.md:8-12`（`db-migration-reviewer` 第二轮，14:38 产出）
- **为什么**：任务文件 `:88` 要求「`db-migration-reviewer` 按 Android SQLite 守门复核…」。该守门**已跑**，但结论为 **PARTIAL**（真实引擎证据已取得，故非 FAIL；升级替代证据为空见证、失败路径结果检查缺失，故非 PASS）。按 `.trae/rules/harness-verification.md:12`，verifier 不得用自身替代该守门，因此本条不能记为达成。
- **怎么修**：先闭环 F1/F3（P1/P2），再请 `db-migration-reviewer` 重跑至 PASS，并把结论链接写进任务文件「Verification report」。

### F3（中高）迁移失败后未断言 `user_version` 未被改写

- **位置**：`SqliteSchemaInstrumentedTest.java:837-847`（捕获异常后只断言消息，无 `PRAGMA user_version` 回读）
- **为什么**：`.trae/rules/sql.md:43` 要求「migration 失败不得留下被误报为最新版本的半迁移数据库；失败路径须证明事务回滚或明确的失败关闭行为」。缺的正是这条**结果检查**。
- **怎么修（比 P2 的建议更准）**：注意 `:839` 直调的是 `TreeLearnSchema.applyForwardMigrations(db,1,2)`，而该函数**根本不写 `user_version`**——在它后面读版本是空真。必须改走**生产入口**：
  ```java
  expectedFailure(IllegalStateException.class, "failed upgrade", () -> helper.onUpgrade(db, 1, 2));
  assertEquals("a failed migration must not stamp a newer user_version", 1, rawInt(db, "PRAGMA user_version"));
  assertEquals("and the schema must still be intact", EXPECTED_TABLES, actualTables(db));
  ```
  这一改同时闭环 F1 与 F3。

### F4（中高）「唯一阻塞项」的口径与仓库既有先例冲突，且未回应自设门槛

- **位置**：任务文件 `:148`（物理 674 > 500）、`:158`（列为唯一阻塞项）、`:205-207`（自设「实测 > 400 必须拆第四个切片」）
- **为什么**：T-003 `:156` 与 T-021 `:261` 已确立**非空非注释代码行**为 Task v2 预算口径；按该口径 **455 < 500 未越硬线**。任务改用物理行判超限，却未在「TDD or sizing exception」里说明为何本次换口径；且 455 已越过 `:207` 自设的 400 门槛，该节对此只字未提。
- **怎么修（需用户裁定，不能由实现方自行挑选）**：请用户在下面两项中明确其一，并把裁定写进 `harness-tasks.md` 或 AGENTS.md 以免下一轮再争：
  1. 口径 = 非空非注释 → 本任务 455，**不触发硬失败**，只需保留 >200 警告，撤销「唯一阻塞项」；
  2. 口径 = 物理行 → 本任务 674，**触发硬失败**，须按 `:150` 的 Alternative verification 拆片（或用户追认例外）。
  无论哪种，`:207` 自设的 400 门槛都要在任务文件里给出明确处置结论。

### F5（中）`branches.context_snapshot_id` 可空性同时违反 §5.2 与 `:core` 再水合契约

- **位置**：`TreeLearnSchema.java:228-229`（FK 可空）、`:239-241`（非根 CHECK 只强制 `source_message_id`/`source_revision_id`/`anchor_hash`）；`技术方案_V1.0.md:110`（§5.2 把 `contextSnapshotId` 列为非根分支必存字段）；`android/core/.../BranchSource.java:44`（`Guard.id(contextSnapshotId, …)`，null 即抛 `IllegalArgumentException`）；验收夹具自身 `SqliteSchemaInstrumentedTest.java:927-934` 插入正常分叉行时未给该列
- **为什么**：schema 合法的行（`context_snapshot_id IS NULL`）在 `:core` 再水合时**必炸**。这是**schema 层**缺口，属本任务「本任务是 DDL 的唯一产地」的职责范围，不能推给 022。
- **怎么修（二选一，都要回写清单 §2 / §5.2 决策）**：
  - (a) 把 `context_snapshot_id IS NOT NULL` 加进非根分支 CHECK；夹具与 023 的首题事务改为「先 INSERT 分支 → 建快照 → 同一事务内 UPDATE 回填」（`branches ↔ context_snapshots` 是循环外键，单条语句无法满足，必须两步行——这点已被 T-004 `:227` 自己写进 watch-outs）；
  - (b) 改 `BranchSource.restore` 用 `Guard.idOrNull` 并同步 §5.2 声明「快照引用可空」。
  推荐 (a)：§5.2 已列为必存，且缺失会让分支来源不可复现。

### F6（中）事务 runner「无法被绕过」的表述与实现不符，且「已提交」语义未钉死

- **位置**：`DatabaseTransactionRunner.java:50-59`；`SqliteSchemaInstrumentedTest.java:713-721`；任务文件 `:114-118`、`:221`
- **为什么**：工作体自行 `setTransactionSuccessful() + endTransaction()` 会**真正提交**（Android 的 `beginTransaction()` 在已有事务中静默并入，栈底 `endTransaction()` 即 COMMIT）。runner 只能在提交**之后**检测并抛异常，准确说法是「**可被绕过，但会被检测并上报**」，而非任务文件 `:118`/`:221` 写的「拒绝被工作体窃取事务 / 无法绕过」。此外该用例工作体**没写任何行**，「被窃事务到底回滚了没有」从未被断言，日后易被读成回滚保证。
- **怎么修**：① 统一 runner javadoc 与任务表述为「检测并上报，不承诺阻止」；② 在窃取工作体里先 `insertTopic(workDatabase, "t-stolen")` 再窃取，显式断言 `rowCount(db,"topics")` **增加**，把「已提交、不可回滚」钉死；③ 若真要阻止，需给工作体一层只暴露 DML 的门面。

### F7（中）DDL 产地守门对 11 处真实 DDL 是盲的

- **位置**：`artifacts/evidence/T-004-ddl-location.txt:1`（检索式 `CREATE TABLE|ALTER TABLE|CREATE INDEX`）
- **为什么**：`CREATE UNIQUE INDEX` **不含**子串 `CREATE INDEX`，故 9 条索引只扫到 4 条；6 条 `CREATE TRIGGER`（`TreeLearnSchema.java:402/405/410/413/418/422`）与任何 `DROP`/`ALTER` 也不在范围内。我本轮用完整检索式复跑，18 处命中仍全部落在 `TreeLearnSchema.java`——**结论为真，但守门强度不足，属侥幸**。
- **怎么修**：检索式改为
  `CREATE\s+(UNIQUE\s+)?INDEX|CREATE\s+TABLE|CREATE\s+TRIGGER|ALTER\s+TABLE|DROP\s+(TABLE|INDEX|TRIGGER)`，
  并覆盖 `*.java` 之外的资源目录；重出一份证据文件。

### F8（中低）任务文件里的两组数字与证据文件不同源（命中 AGENTS.md 失败案例库第 3 条）

- **位置 1**：任务文件 `:112` 声称五码均由探针实测 → `artifacts/evidence/T-004-constraint-code-probe.txt` **实际只有 `1299`（:136）与 `275`（:137、:140）**，我 grep 确认 `2067`/`787`/`1811` 在该文件中**零命中**。
- **位置 2**：任务文件 `:139`/`:155` 引用 `tests="24" failures="0" errors="0" skipped="0"` → 六份 `T-004-*.txt` 中**检索不到该串**（`T-004-tests.txt` 只有 24 个用例名，无计数行）。数字本身为真（我本轮从 XML 回读确认），但**引用不在证据文件里**。
- **怎么修**：① 补一次五码全量探针输出，或把 `:112` 改为「1299/275 由探针实测，其余三码取自 SQLite 扩展错误码定义并由绿跑间接验证」；② 把 JUnit XML 统计行与 XML 路径追加进 `T-004-tests.txt`。

### F9（中低）「迁移链无缺口」断言在 V1 是空真，且含两处 off-by-one

- **位置**：`SqliteSchemaInstrumentedTest.java:849-866`
- **为什么**：`FORWARD_MIGRATIONS` 为空（`TreeLearnSchema.java:483`），循环零次，`assertEquals(0, 0)` 空真；注释 `:850-851`「this can never silently iterate zero times」与事实相反。更严重的是两处 off-by-one：
  - `:854` `assertEquals(previousTo /*初值 0*/, migration.fromVersion())` —— 真实链首步是 `1→2`，此处会断言 `0 == 1`；
  - `:865` `assertEquals(DATABASE_VERSION, previousTo + 1)` —— 链 `1→2,2→3`、`DATABASE_VERSION=3` 时 `previousTo=3`，会断言 `3 == 4`。

  即：一旦 `DATABASE_VERSION` 升到 2，这段**不是开始生效，而是会误报失败**。
- **怎么修**：`previousTo` 初值改为 `1`（或从 `migration.fromVersion()` 起算）、`:865` 改为 `assertEquals(DATABASE_VERSION, previousTo)`；注释改写为「V1 下本段空真，注册首个迁移后才开始起作用」。

### F10（中低）`android/README.md` 的 dex 数被本次改动变成不实

- **位置**：`android/README.md:58`「实测 `app-debug.apk` 约 70 KB、**3 个 dex**」；实测（`T-004-guard.txt:21` 与我 `unzip -l` 复核）为 **71096 B / 4 个 dex**（新增 `classes4.dex` 20616 B，来自 `:data` 生产代码）。字节数的免责括注（:59）**不覆盖 dex 数**。
- **怎么修**：改为「4 个 dex（`:data` 入库后新增 `classes4.dex`）」，或改为只声明「零第三方依赖、零 Kotlin 字节码」并注明 dex 数以守门输出为准。同时 `release` APK 由 37852 B → 56012 B，若 README 后续引用需同步。

### F11（低）任务文件存在两个 `## Verification report` 段，后一个仍写 `PENDING`

- **位置**：`.trae/tasks/T-2026-09-20-sqlite-schema-dao-transactions-004.md:152`（VERIFYING）与 `:209-215`（Verdict: PENDING / Evidence: PENDING …）
- **为什么**：从文件尾部读的复核者会得出「PENDING」，与 `:154` 的 VERIFYING 直接矛盾；`validate_tasks.py` 只校验键存在性，不查重复段，因此漏过（`SUMMARY errors=0`）。
- **怎么修**：删除 `:209-215` 整段（保留 `:152` 那一段），或把第二段改名为「Verification report（模板占位，已废弃）」。

### F12（低）PRAGMA 用例在零行上成立（空表可过），不变量靠 sibling 覆盖

- **位置**：`SqliteSchemaInstrumentedTest.java:371-386`
- **为什么**：若 `onCreate` 什么都不建，`foreign_keys=1`、`foreign_key_check` 0 行、`integrity_check=ok` 三条**全部照样通过**。真实覆盖来自 `:884`（重开后已有 topic/branch 行）与 `:645-647`（级联后）。结论可信，但该例自身不满足文件头 `:50` 自设的「no assertion may pass only because a table is empty」。
- **怎么修**：把该例改为先 `seedTopicWithRoot` 再断言，或在方法 javadoc 注明「本例只验连接级 pragma，行级完整性由 `closeAndReopen…` 与 `deletingATopic…` 覆盖」。

### F13（低）RED 证据文件无原始 javac 日志，`21 errors` 不可回推

- **位置**：`artifacts/evidence/T-004-red.txt`（全文为人工摘要，无 `EXITCODE=` 行、无编译器原始输出）
- **为什么**：AGENTS.md:63 要求「证据文件必须自带命令行与退出码（`EXITCODE=`），否则不能自证」。此处 `- Exit code: 1` 是人读格式，且文件里没有任何可 grep 的 `error:` 行，故 `:21` 的 21 errors 与 42/21 的差异说明**无法由文件回推**（需重新跑一次 RED 才能验证，而生产代码已存在，不可复现）。
- **怎么修**：本轮不必重跑 RED（结论不受影响）；后续同类场景在证据文件里追加 javac 原始输出与 `EXITCODE=1` 行。

### F14（低）`android/README.md` 未记录两条「禁止直接删除」触发器

- **位置**：`android/README.md:211-214` 只写 4 条不可变不变量；`TreeLearnSchema.java:405-409/413-417` 的 `trg_answer_revisions_no_direct_delete`、`trg_context_snapshots_no_direct_delete` 未提及
- **为什么**：`:data` 入口索引是 022/023 写 DAO 的第一手文档；这两条会让 `DELETE FROM answer_revisions/context_snapshots` **直接 ABORT**（仅当属主行仍在），下游极易误判为 bug。
- **怎么修**：README 补一句「稳定版本与出生快照只允许随属主行一起消失，直接 `DELETE` 会被 ABORT」。

### F15（低）序列约束断言的 `requiredText` 不够锐（附注：清单措辞差异）

- **位置**：`SqliteSchemaInstrumentedTest.java:353-357`；`TreeLearnSchema.java:206-209`
- **为什么**：`last_seen_stable_sequence` 的 CHECK 表达式自身也引用了 `latest_stable_sequence`，UPDATE 时两条 CHECK 都会被重算；若先命中前者，消息文本同样含 `latest_stable_sequence`，断言仍通过。当前不影响结论（两条都是序列约束），但判据不够锐。
- **怎么修**：`requiredText` 收紧为 `latest_stable_sequence >= 0`（该片段只出现在 `:204-205` 那条 CHECK）。
- **附注（非缺陷）**：清单 §2 `:25` 与技术方案 §6.1 `:127` 仍写「未读序号」，而 §11 `:264` 明确「不存独立布尔权威值」。实现按 §11 取舍正确，但两处措辞并存会让下一次复核产生歧义，建议在清单 §2 补一句引向 §11。

---

## 8. 我验证了什么 / 没验证什么

**验证了（有可复现命令与回读结果）：**

1. 真实 Android SQLite（API 36 模拟器）上 24 例（本任务 23 + T-002 回归 1）**全绿**，我独立复跑、exit 0，JUnit XML `failures=0 errors=0 skipped=0`。
2. RED/GREEN 命令一致、RED 为编译级失败且任务文件如实标注、GREEN 真实通过。
3. 生产代码规模两个口径均可复现（物理 674 / 代码 455）。
4. 七表、列集、类型、NOT NULL、9 索引（含 2 偏索引）、6 触发器与生产代码逐条对上；偏索引的「部分性」有反向证明。
5. `:core` 回归无损（120 用例全绿）、依赖方向 PASS、Java-only 守门有判据行且 PASS、守门自检完整模式 40 例 PASS、lint 0 error。
6. DDL 单产地（用完整检索式复核，18 处命中全在 `TreeLearnSchema.java`）。
7. 下推到 022/023 的职责**确实写进了那两个文件**（逐条比对，见第 6 节）。
8. `validate_tasks.py`：`files=23 errors=0 warnings=2`。
9. 本轮新建的 `db-migration-reviewer` 第二轮报告存在且结论为 PARTIAL（非 PASS）。

**没验证（明确声明）：**

1. **未做变异测试**——本轮 READONLY，不能修改生产/验收代码来证明「套件会红」。F1/F9/F12 的「空见证」判断来自静态推导（断言在退化实现下仍成立），未经实测证伪。
2. **未重跑 RED**——生产代码已存在，RED 不可复现；F13 的 21 errors 只能采信任务自述。
3. **未代判 `db-migration-reviewer`**——验收条件 12 的 PASS 必须由该守门给出。
4. **不覆盖 022/023 的 TDD 执行**——两者 `status: ready`、尚未开工，只核对其验收条件承接了 T-004 的下推。
5. **未验证 `:data` 的 JVM 单测**——本任务未引入，`app:testDebugUnitTest` 为 `NO-SOURCE`。
6. **规模口径不做裁定**——F4 需用户决定，verifier 不代判，也不因「455 < 500」自行放行。

---

## 9. 结论与阻塞点

```text
[verifier 报告]
task: T-2026-09-20-004
审阅文件: android/data/src/main/java/com/treelearn/data/db/{TreeLearnSchema,TreeLearnDatabaseHelper,DatabaseTransactionRunner}.java
          android/app/src/androidTest/java/com/treelearn/app/SqliteSchemaInstrumentedTest.java
          .trae/tasks/T-2026-09-20-sqlite-schema-dao-transactions-004.md
          .trae/tasks/T-2026-09-21-sqlite-dao-topic-branch-message-022.md
          .trae/tasks/T-2026-09-21-sqlite-dao-generation-artifacts-023.md
          android/README.md
          Docs/05-technical-design/知芽（TreeLearn）Android SQLite 建表入场确认清单_V1.0.md
          Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md
          artifacts/evidence/T-004-{red,green,tests,guard,guard-selftest,ddl-location,constraint-code-probe}.txt
          artifacts/evidence/T-004-verification-db-review.md
代码量: 物理 674 行 / 非空非注释 455 行（口径待裁定，见 F4）
任务合规: PARTIAL（结构 errors=0；存在重复 Verification report 段 F11；规模口径自相矛盾 F4）
Lint:
  - android lint (:app:lintDebug, :data:lintDebug): PASS（0 error / 11 warning，均为既有）
  - android Java-only guard: PASS（13 OK 行，含 verifyNoKotlinDex 判据行，EXITCODE=0）
  - guard 双向自检（完整模式）: PASS（40 例，SELFTEST_EXITCODE=0）
  - 依赖方向 verifyDependencyDirection: PASS
  - :core:test 回归: PASS（11 类 / 120 用例，0 failure）
依赖方向: PASS
验收点对照: 满足 8 条（1/2/4/5/7/9/10 全满足，另有 3、8、11 为「结论真但守门/表述不足」）；
            未满足 2 条（第 6 条升级替代证据空见证、第 12 条专项守门未 PASS）
专项守门路由: db-migration-reviewer 第二轮 = PARTIAL（不得由 verifier 替代）；
             android-no-kotlin-guard = PASS；contract-guardian = N/A（未触契约）
文档影响记录: FAIL（README:58 dex 数被本次改动变成不实 F10；README 漏记 2 条触发器 F14；
             任务文件自述数字与证据文件不同源 F8）
启动证据: N/A（:data 为库模块，无启动入口；`:app` 启动由 T-002 用例在同批回归中通过）
结论: PARTIAL
阻塞点与剩余风险:
  1. F1+F3 —— 升级路径的替代证据是空见证，且失败路径缺 user_version 结果检查（sql.md:43）
  2. F2 —— 验收条件 12 的 db-migration-reviewer 未给出 PASS
  3. F4 —— 规模口径需用户裁定（455 vs 674），并回应 :207 自设的 400 门槛
  4. F5 —— context_snapshot_id 可空性缺口会直接打到 022 的再水合契约
  5. F6 —— 事务 runner 表述与实现不符，会误导 023 的首题事务设计
新型错误（建议追加 AGENTS.md 失败案例库）:
  - [2026-09-21] [sqlite] 用「**同版本升级是 no-op**」当「onUpgrade 委托给注册表」的证据 →
    退化成空方法体的 onUpgrade 照样通过，断言过了、委托这条不变量从未被触及。规则：委托类断言必须
    选一个**空实现会失败**的输入（此处为 onUpgrade(old,new) 且注册表缺该步，须抛 fail-closed 异常）；
    凡「机制存在性」被记为 N/A 的替代证据，都要先自问「把它删掉，这条用例还绿吗」。
  - [2026-09-21] [harness] 同一任务的规模在两个口径下**跨过不同的阈值**（代码 455 < 500、物理 674 > 500）
    → 阻塞判定变成「挑口径」。规则：仓库必须把 Task v2 预算口径**写死一处**（建议 harness-tasks.md:45
    补「非空非注释生产代码行」，与 T-003/T-021 的既有先例对齐），禁止在单个任务里换口径；
    任务文件里若两个口径给出不同结论，必须同时列出并明确「本次采用哪一个、为什么」，
    且**自设门槛**（如本任务 :207 的 400 行）也要在同一节给出处置结论。
```

**修复优先级**：F1 → F3（同一处改动可同时闭环）→ F2（重跑守门）→ F4（等用户裁定）→ F5 → F6 → F7 → F8 → F9 → F10。

**关于是否解除 `VERIFYING`**：**不建议**。理由不是技术债未清，而是三个未闭环项各自独立成立：
① 升级替代证据为空见证（F1/F3，属「假证据」类，不可放行）；
② 验收条件 12 要求的专项守门本轮给出的是 PARTIAL 而非 PASS（F2，verifier 无权替代）；
③ 规模口径未定（F4，须用户裁定）。
在 F1/F3/F5/F6 修完且 `db-migration-reviewer` 复跑 PASS、F4 由用户明确口径之后，可再来一轮验收。
F4 的裁定不影响 022/023 开工（两者只依赖 schema 常量与 runner，接口已稳定并在真机验证）。
