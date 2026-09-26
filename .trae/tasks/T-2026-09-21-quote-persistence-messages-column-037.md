# Treelearn Harness Task: 引用追问落库（messages 加列）

id: T-2026-09-21-037
title: 引用追问落库（messages 加列）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 140
shared_code_touched: true
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-21-sqlite-dao-topic-branch-message-022.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 让 V3.1 PRD 6.2.19 的「引用追问」在发送后仍能把引用内容显示在用户消息上——也就是把选中文字随用户消息一起落库，而不是只活在这一次的内存状态里。
- Problem/background: 引用追问的界面已经建好（`ChatMessage.quote`、`ChatActions.onQuotePressed`、`ChatPresenter.onQuotePressed`、`res/layout/screen_chat_body.xml` 的引用条），但 `ChatCommands.send(branchId, text, quote, ...)` 的 `quote` 参数在 `messages` 表**没有承接列**，提示词设计文档也没有定义引用的线格式；因此这条引用一旦离开当前会话就丢失，违反 PRD 6.2.19 第 7 条「引用追问在发送后，用户消息中需保留引用内容的可见标识」。
- Users/scenarios: 用户长按回答中的一句 → 点「追问」→ 该句出现在输入框上方 → 补充问题发送 → 回答留在当前分支；之后重开应用回到该分支，仍能看出这条问题是针对哪句话问的。
- Expected outcome: `messages` 表新增一列承接用户消息的引用文字；该列只允许出现在 USER 行且非空；`MessageDao` 能写入与读回；`android/core` 的 `Message` 领域对象携带它。
- Core flow and reversibility: 写入发生在「发送被接受、写入用户消息」的同一步，与既有事务同一原子单位；删除消息/分支时随行删除，无需额外清理。不可逆操作：无（本任务只加列与读写）。
- Inputs/outputs: 输入是 `ChatCommands.send` 收到的 `quote` 字符串与既有 `Message` 领域对象；输出是新增列、迁移步骤、`MessageDao` 的读写签名，以及 `Message` 的字段与访问器。
- State and exception handling: 引用为空字符串时按「无引用」处理并写 NULL，不写空串；AI 行携带引用必须被数据库拒绝；读回时 NULL 归一化为空字符串，调用方不必判空。
- Constraints and trade-offs: Java-only、API 28+、禁止改动已发布的 DDL 语义（本列只能追加，不得改写既有列）。数据不变式必须建立在**数据库层**（`rules/sql.md`：约束要真的被引擎执行，不能只写在 Java 里），且**新装与升级两条路径必须得到同一条规则**——`ALTER TABLE ADD COLUMN` 无法携带 CHECK，因此规则文本必须只有一份、被两条路径共用。
- AI recommendation and rationale: 用「DATABASE_VERSION 1 → 2 + 一条前向迁移」而不是原地改写 V1 的建表语句。理由：今天已有一个签名 APK 在用户手上，虽然生产线从未打开过数据库（`TreeLearnDatabaseHelper` 在 `src/main` 内没有任何构造点），但把「没有设备持有 v1 库」当成前提去改写已提交的 DDL 是不可复核的假设，代价是要解释为什么不能；走迁移则两条路径都由引擎保证。
- User choice and custom additions: 用户 2026-09-21 要求交付「完完整整的 APK」，并明确引用追问必须实现。用户同时放弃 emulator/真机轮次，改由自己安装 APK 实测。
- AI-autonomous decisions: 列名 `quoted_text`；NULL 表示无引用；读写归一化在 DAO 内完成。
- User-confirmation decisions: 用户已接受「本轮不产生真实设备 SQLite 行级证据」这一缺口（见验收标准第 4 条）。
- Acceptance evidence: 同一命令 RED→GREEN 的 JVM 测试、迁移与建表两条路径得到同一约束的断言、`:app:testDebugUnitTest` 与 `.\gradlew.bat check` 退出码 0、Java-only guard PASS、`db-migration-reviewer` 复核记录。
- Facts: `TreeLearnSchema.DATABASE_VERSION = 1`；`FORWARD_MIGRATIONS` 目前是空列表且 `applyForwardMigrations` 在缺步骤时 fail-closed；`MessageDao` 现有 `insert/updateActiveReferences/findById/findByBranch`；`Message` 领域对象不携带引用。
- Assumptions: T-022 交付的 `MessageDao` 与 `messages` 建表语句保持现行签名。
- Unknowns: 真实 Android SQLite 上迁移的行级行为（本轮无设备）。
- Risks: 迁移与建表两条路径的约束漂移；`ALTER TABLE` 无法带 CHECK 时把规则只留在 Java 里；`Message` 加字段后既有构造点编译失败（预期，属编译期暴露）。
- Deferred: 真实设备上的迁移执行证据；引用内容进入提示词的线格式（由接线任务 T-2026-09-21-032 决定）。
- Non-goals: 不改回答/推理/版本相关的任何列；不做引用的 UI；不定义引用如何进入提示词；不新增表。
- Skipped non-critical questions and reason: 引用长度上限不设（TEXT 无长度限制，且输入来自用户选中的回答片段，规模受回答长度约束）。

## Goal

`messages` 表能承接用户消息的引用文字，且「引用只出现在 USER 行且非空」这条不变式在新装与升级两条路径上都由数据库引擎执行。

## Scope

### In scope
- `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java`：新增 `Messages.QUOTED_TEXT` 列名常量；把列加入 `MESSAGES_DEFINITIONS`；`DATABASE_VERSION` 1 → 2；在 `FORWARD_MIGRATIONS` 注册 V1→V2 步骤。
- 该不变式的**单一份规则文本**，被 `CREATE_STATEMENTS` 与迁移步骤共同使用（迁移无法用 `ALTER TABLE` 带 CHECK，因此采用与该列同时建立的数据库级触发器；规则文本只写一处）。
- `android/data/src/main/java/com/treelearn/data/db/dao/MessageDao.java`：`insert` 写入引用；`findById`/`findByBranch` 读回并把 NULL 归一化为空字符串。
- `android/core/src/main/java/com/treelearn/core/domain/Message.java`：新增引用字段与访问器，保持既有不变量（AI 行无正文）不变。
- JVM 测试覆盖：写入/读回/归一化/AI 行被拒/升级路径与新建路径约束一致。

### Out of scope
- 引用的 UI（已由 T-013 交付）与引用进入提示词的线格式（T-032）。
- `messages` 既有列的任何语义改动。
- 任何 DROP/重建表的重型迁移。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增一列与一条数据库级不变式；**保留**既有 `messages` 约束（ROLE 取值、USER 行正文非空、AI 行正文为空、`branch_id` 外键级联）与其错误码行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `MessageDao.insert`（当前唯一写入者）；消费者是 `MessageDao.findById/findByBranch` 与 `android/core` 的 `Message`。新装路径走 `onCreate`，升级路径走 `onUpgrade` → `applyForwardMigrations`。
- Native bridge or independent-client compatibility/update requirements: 无原生桥；数据库是平台边界，升级必须向前且不可回退（`onDowngrade` 已拒绝）。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md). 全部核对命令与输出见 `artifacts/evidence/T-037-docs.txt`。
- Changed runtime facts: `messages` 新增 `quoted_text` 列（`NULL` = 无引用；只允许出现在非空 `USER` 行）；`TreeLearnSchema.DATABASE_VERSION` 1 → 2；`FORWARD_MIGRATIONS` 注册一条 V1→V2（列声明 + 两个触发器）；`Message` 领域对象携带引用（`quotedText()`，空串表示无引用）。
- Authoritative code/config/migration evidence: `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java`（`DATABASE_VERSION`、`Messages.QUOTED_TEXT` / `QUOTED_TEXT_DECLARATION`、`MESSAGES_DEFINITIONS`、`MESSAGES_QUOTE_INVARIANT_CONDITION` 与两个包装、`MIGRATION_V1_TO_V2_STATEMENTS`、`MigrationV1ToV2`）、`android/data/src/main/java/com/treelearn/data/db/dao/MessageDao.java`（`quoteColumnValue` / `quoteFromColumn`）、`android/core/src/main/java/com/treelearn/core/domain/Message.java`（`quotedText()` 与 4/5 参 `user`、8/9 参 `restore`）。
- Documentation surfaces checked: `android/README.md`（`:data` DAO 与表结构索引）、`Docs/05-technical-design/知芽（TreeLearn）Android SQLite 建表入场确认清单_V1.0.md`（§2 表清单、§3 迁移约定）、`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md`（§5.1、§6.1）、`Docs/06-delivery/TreeLearn_V1_交付报告.md`（第 5 节缺口清单）。
- Documentation files updated: 上述四个文件，落点 `android/README.md:235`、清单 `:26` 与 `:37`、技术方案 `:95` 与 `:130`、交付报告 `:77`（读回见 `T-037-docs.txt` 第 5 节）。
- Fix-round surfaces updated (2026-09-22, 修复轮): `android/app/src/androidTest/java/com/treelearn/app/SqliteSchemaInstrumentedTest.java`（设备侧 schema 契约改为**从 `TreeLearnSchema` 常量派生**：版本、触发器名、`quoted_text` 列名不再复述；fail-closed 用例改用由构造保证未注册的 `DATABASE_VERSION → DATABASE_VERSION + 1`；新增「手搭 V1 库 → 生产 `TreeLearnDatabaseHelper` → 断言升级结果」用例）；新增静态守门 `.trae/scripts/dev/check_androidtest_schema_expectations.py`（含正反双向自检 8 例）；根 `AGENTS.md` 失败案例库追加一条。以上均为测试/守门/流程文件。修复轮 2（同日）：把该守门的豁免从「±160 字符窗口内出现过常量名」改成「**被判定断言自己的期望值实参**是允许的常量名」，自检由 8 例扩到 **13 例**（8+5，分项见验证报告）；新增证据 `artifacts/evidence/T-037-guard-window-fix.txt` 与 `T-037-guard-window-mutation.py`；本轮 `android/**` 零改动（被核套件 SHA-256 仍为 `0423B1CE…7D26`）。
- Fix-round documentation impact: none —— 修复轮没有改变任何当前态事实（`DATABASE_VERSION` 上一轮已是 2、DDL 与迁移语义未动、`MessageDao`/`Message` 未动），因此 `android/README.md`、入场确认清单、技术方案、交付报告这四处无需二次修改。新增的仅是一条**流程性事实**：`gradlew check` 是 JVM-only，设备侧 `androidTest` 从不在门禁里编译，已写入本任务 Gaps。
- Producer/consumer/sibling/compatibility paths checked: `MessageDao.insert` 是唯一写入者，读路径 `findById` / `findByBranch` 经 `read(Cursor)` 归一化；`Message` 的全部构造点在加字段后以编译期失败暴露，逐一改为新工厂或 9 参 `restore`（旧 4/8 参保留并委托，故既有调用点无需改语义）。
- Superseded phrases/examples reverse-searched:「messages 表没有承接引用的列」「引用追问在当前数据模型里无处安放」（命令 `rg -n --no-heading "没有承接引用|无处安放" .`，输出见 `T-037-docs.txt` 第 4 节）。
- Remaining matches and their handling: 仅 `Docs/06-delivery/TreeLearn_V1_交付报告.md:77` 仍含这两个短语。该报告是带日期的交付快照且**未跟踪**（T-033 产出、尚未提交），按 `rules/documentation.md`「不静默改写历史」，在其后追加可见的「〔历史快照 · 2026-09-21〕本条第前半句已过期」说明并指向当前权威 `TreeLearnSchema.java`；其余全仓零命中。
- `documentation impact: none` reason and evidence, if applicable: 不适用（确有当前态事实变更）。
- Historical/PRD handling: PRD 6.2.19 是需求依据，不改写；交付报告按历史快照标注而非重写，其中「引用进入提示词的线格式未定义」仍成立，归 T-032。

## Acceptance criteria

- [x] 同一 JVM 测试命令先 RED 后 GREEN：新增测试断言「USER 消息带引用可写可读回」「NULL 读回为空串」「AI 行带引用被数据库拒绝」「升级路径得到的库与新建路径对同一输入给出同一结论」。分层说明见验证报告：前三条的领域/映射层由 JVM 套件钉住，`AI 行带引用被数据库拒绝` 与「两条路径结论相同」由桌面 SQLite 附件证明（真实 Android SQLite 一轮仍缺，见下条）。
- [x] 新装与升级两条路径的不变式由**同一份规则文本**建立：用原文断言证明该规则文本只出现一次，且两条路径都引用它（`TreeLearnSchemaQuoteInvariantTest`，7 例全绿）。
- [x] 既有 `messages` 约束与 `:core`/`:data`/`:app` 既有测试全部保持通过（回归证据：`.\gradlew.bat check` exit 0，77 个 JVM 套件 / 769 例 / 0 失败 0 错误 0 跳过；分项 `test` 19 套件 197 例 + `testDebugUnitTest` 29 套件 286 例 + `testReleaseUnitTest` 29 套件 286 例，19+29+29=77、197+286+286=769）。
- [x] 真实 Android SQLite 行级证据（迁移执行、约束拒绝码）记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`（即本条要求的 `blocked — 缺少设备（用户 2026-09-21 指令：放弃 emulator/真机，改由用户安装 APK 实测）`），**未写成 PASS**。补充的桌面 SQLite 运行只作附件（`artifacts/evidence/T-037-sqlite-supplement.txt`，自带「NOT device evidence」声明），不替代真实引擎。
- [ ] `.\gradlew.bat check`（exit 0）、Java-only guard（`VERDICT: PASS`）、文档影响记录齐全；**`db-migration-reviewer` 复核未做**——该角色按 `rules/sql.md` 是 READONLY 的独立复核方，实现者不得自审，留给 verifier/独立复核轮。
- [x] 设备侧 `androidTest` 契约在版本 bump 后不再失效（修复轮，2026-09-22）：`SqliteSchemaInstrumentedTest` 的版本号、触发器名、新列名全部**从 `TreeLearnSchema` 常量派生**而非复述；fail-closed 用例改用由构造保证未注册的 `TreeLearnSchema.DATABASE_VERSION → DATABASE_VERSION + 1`（不再硬编码任何将来会被注册的版本对）；新增「手搭 V1 库 → 用生产 `new TreeLearnDatabaseHelper(context)` 打开 → 断言版本/新列/8 个触发器/外键与完整性/旧行存活」用例。静态守门 `.trae/scripts/dev/check_androidtest_schema_expectations.py`：真实树 exit 0、自检 8 例全过、对**修复前**套件 exit 1（14 条问题，覆盖全部四类缺陷）。**修复轮 2（2026-09-22）**：独立门 `db-migration-reviewer` 复现该守门的豁免取自**±160 字符窗口**、而不是被判定断言自己的期望实参——同一条硬编码违规只因落在窗口内/外就得到 PASS/FAIL 两个相反结论（`artifacts/evidence/T-037-guard-window-fix.txt`：run 2 exit 0 vs run 3 exit 1，最近允许常量距离 143 vs 322 字符）。已按根因重写：从左到右括号栈取最内层被判定调用（`assertEquals` / `execSQL`）的期望值实参，只认该实参是否为 `TreeLearnSchema.DATABASE_VERSION` / `V1_SCHEMA_VERSION`；`execSQL` 判其 `=` 之后的赋值部分；解析不出实参（不在被判定调用内 / 参数表不平衡 / 实参个数不可判）一律 **fail-closed**。自检 8 → **13 例**（新增 5：紧邻合法断言的第二条硬编码必须 FAIL、紧邻的合法派生必须 PASS、`execSQL` 用字面量赋值必须 FAIL、不在任何被判定调用内必须 FAIL、断言实参不可解析必须 FAIL），真实树 exit 0 不变。
- [x] `androidTest` **只编译、未执行**：`:app:assembleDebugAndroidTest` 在修复轮实测**编译通过**（exit 0，证据 `artifacts/evidence/T-037-androidtest-compile.txt`，文件头明写 compile-only / NOT executed，并记下被核套件 SHA-256 `0423B1CE…7D26`）。本轮中途同一命令曾 exit 1，原因是 T-032 当时正在改写**未跟踪**的 `android/app/src/main/.../wiring` 与 `.../ui`（错误全部落在 `src/main`，`androidTest` 零错误，见同文件第 2 段与 `T-037-external-blocker-src-main.txt`）——那是并行任务的在建状态，不是本任务的编译失败；T-032 落地后该命令复跑 **exit 0**（同文件 run 3）。设备执行仍记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，**未写成 PASS**。

## TDD execution record

### RED — failing test first
- Test/scenario: 先写三个 JVM 套件再实现：`MessageQuoteTest`（USER 带引用可写可读回、NULL 读回为空串、领域拒绝 null 引用、AI 位置不携带引用）、`MessageQuoteColumnTest`（域与列的「无引用」两种拼法互译）、`TreeLearnSchemaQuoteInvariantTest`（规则文本只有一份、两条安装路径共用它、每个版本都有前向步骤）。
- Command: cd android; .\gradlew.bat :core:test :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebugAndroidTest --continue --no-daemon --console=plain
- Exit code: 1
- Observed failure: `:core:compileTestJava` 与 `:data:compileDebugUnitTestJavaWithJavac` 编译失败，因为三个新套件引用的 `Message.quotedText()` / `MessageDao.quoteColumnValue` / `Messages.QUOTED_TEXT_DECLARATION` / `MIGRATION_V1_TO_V2_STATEMENTS` 在实现落地前都不存在。
- Failure reason validated: 是——全部错误都是 `找不到符号` 或 `无法将类…应用到给定类型`，逐条落在本任务新写的三个测试文件里，没有一条来自既有代码或既有测试。

```text
证据文件 artifacts/evidence/T-037-red.txt（BUILD FAILED in 12s，文件末 EXITCODE=1）。
计数命令与结果（都对 T-037-red.txt 执行）：
  ($red | Where-Object { $_ -match '错误: ' }).Count                  = 62
  (逐文件 .java:<行> 去重后的条数)                                    = 31
  62 = 31 x 2：Gradle --console=plain 把每条编译错误打印了两遍。
逐文件（去重后）：
  MessageQuoteTest.java            = 12
  MessageQuoteColumnTest.java      = 7
  TreeLearnSchemaQuoteInvariantTest.java = 12
  12 + 7 + 12 = 31  （与本任务新写的三个文件一一对应）
按符号种类（去重后）：
  找不到符号 = 25
  无法将类   = 6
  25 + 6 = 31；同一口径下文件里出现 50 + 12 = 62 行，即去重值的两倍。
失败任务（`> Task ... FAILED`）：
  :core:compileTestJava
  :data:compileDebugUnitTestJavaWithJavac
```

修复轮 RED（2026-09-22）—— 第二条 RED：功能测试全绿，但**设备侧套件本身已经过期**。`gradlew check` 是 JVM-only，从不编译 `androidTest`，所以这条红在门禁里不可见。修复前套件（git HEAD 版本，SHA-256 `CA598321498DE6A81E751E17B05788D309131344EE6CF98E5174635839AD6A1A`）在本任务新写的静态守门下 FAIL：

```text
GREEN 侧命令（真实树）: python -X utf8 .trae/scripts/dev/check_androidtest_schema_expectations.py
RED   复现命令:        python -X utf8 .trae/scripts/dev/check_androidtest_schema_expectations.py --root <修复前套件树>
结果: VERDICT: FAIL (14 problem(s))   EXITCODE=1
逐条取自 artifacts/evidence/T-037-androidtest-static-check.txt 的 run 3（第 33-55 行，`  - ` 开头的问题行实测 14 条）:
  带文件路径 10 条 = 3 × user_version 字面量（:252、:254-255、:941-942）+ 6 × 触发器名字面量（:105-109 的 6 条）
                    + 1 × 「未引用全部已声明触发器常量」（missing 列出 8 个 TRIGGER_* 常量）
  无路径    4 条 = EXPECTED_TRIGGERS 未从 expectedTriggers() 初始化 + 未引用 TreeLearnSchema.Messages.QUOTED_TEXT
                  + applyForwardMigrations 被传硬编码字面量版本对 + fail-closed 用例未绑定派生对
  10 + 4 = 14
```

### GREEN — minimum implementation
- Implementation: `messages` 加 `quoted_text TEXT`（新装 `CREATE TABLE` 与升级 `ALTER TABLE ADD COLUMN` 共用同一段声明文本）；§6.2.19 不变式写成触发器，条件文本一份、按 INSERT/UPDATE 包成两条语句，两条安装路径执行同样两条语句；`MessageDao` 负责域空串与列 NULL 的双向互译；`Message` 携带引用并在 AI 位置拒绝非空引用；`DATABASE_VERSION` 升 2 并注册 `MigrationV1ToV2`（`applyForwardMigrations` 的 fail-closed 不变）。**修复轮追加**：设备侧 `SqliteSchemaInstrumentedTest` 的版本/触发器名/新列名改为从 `TreeLearnSchema` 常量派生、fail-closed 对改为派生、并新增真实 V1→V2 用例（见下方 fence）。
- Command: cd android; .\gradlew.bat :core:test :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebugAndroidTest --continue --no-daemon --console=plain
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL；三个新套件 10 / 6 / 7 例全绿；**本聚焦命令**（不含 `testReleaseUnitTest`）覆盖的 48 个 JVM 套件共 483 例（`test` 19 套件 197 例 + `testDebugUnitTest` 29 套件 286 例；19+29=48、197+286=483），failures=0 errors=0 skipped=0（取自 `android/{core,data,app}/build/test-results/*/TEST-*.xml`；修复轮把该命令重跑一遍以免沿用被 `--tests` 过滤污染的旧 XML，聚合命令与输出见 `artifacts/evidence/T-037-jvm-tests.txt` 末段、重跑见 `T-037-jvm-rerun.txt`）。**48/483 是 T-037 实现态（2026-09-22T00:02）的读数**：并行 lane 随后在 `:app` 加入 `HomeWiringTest.java`（+1 套件 / +13 例），因此在最终状态复跑同一命令得 **49 套件 / 496 例**（19+4+26=49、197+26+273=496）——增量不属于本任务，两个读数与算式都在 `T-037-jvm-tests.txt` 末段；两个口径都是**某一时刻的读数**，引用时必须带上时间戳。`.\gradlew.bat check` 额外执行 `testReleaseUnitTest`，同为绿但计数为 77 套件 / 769 例（19+29+29=77、197+286+286=769）。修复轮追加的两项绿色：静态守门 exit 0、`:app:assembleDebugAndroidTest` 编译通过 exit 0。
- Regression evidence: cd android; .\gradlew.bat check --no-daemon --console=plain -> exit 0（`artifacts/evidence/T-037-check.txt`，295 actionable tasks / 57 executed），既有 `messages` 约束与 `:core`/`:data`/`:app` 既有套件无一转红。修复轮期间该命令曾一度 exit 1，失败点是 **T-032 同时改写的未跟踪 `android/app/src/main/.../wiring` 与 `.../ui`**（`androidTest` 零错误）；T-032 落地后同一命令复跑 **exit 0**（`T-037-check.txt` run 3），Java-only guard 同时回到 `VERDICT: PASS (13 OK line(s))`（`T-037-guard.txt` run 3），`:app:assembleDebugAndroidTest` 复跑 exit 0（`T-037-androidtest-compile.txt` run 3）。全量计数复算 77 套件 / 769 例 / 0 失败 0 错误 0 跳过（`T-037-jvm-tests.txt` 中段 FINAL RE-DERIVATION，2026-09-22T00:11:44，算式 19+4+4+25+25=77、197+26+26+260+260=769；此后并行 lane 新增 `HomeWiringTest` 会让该数字继续上移，引用需带时间戳）。中途的失败记录见 `T-037-androidtest-compile.txt` 第 2 段与 `T-037-external-blocker-src-main.txt`。

修复轮 GREEN 细节（2026-09-22）—— 四类缺陷逐条修掉，期望值改为从 `TreeLearnSchema` 派生：

```text
  1) messages 列清单末尾加入 TreeLearnSchema.Messages.QUOTED_TEXT + ":TEXT:0"（顺序与新装 CREATE TABLE 及升级 ALTER 的追加顺序一致）
  2) EXPECTED_TRIGGERS = expectedTriggers()，逐个引用 8 个 TRIGGER_* 常量；并加断言 CREATE_TRIGGER_STATEMENTS.size() == EXPECTED_TRIGGERS.size()
  3) 版本期望改为读 TreeLearnSchema.DATABASE_VERSION；新装处改为「断言库里真的盖了 DATABASE_VERSION」
     （PRAGMA user_version 与 db.getVersion() 两条独立读径），删掉原本的 self-assertion
  4) fail-closed 用例改用 DATABASE_VERSION -> DATABASE_VERSION + 1（由构造保证未注册，将来注册了也不会反转）
     并新增真实 V1 -> V2 用例: 手搭 V1 库（生产 DDL 去掉 V2 增量，PRAGMA user_version = 1）后用
     new TreeLearnDatabaseHelper(context) 打开，断言版本=2 / quoted_text 出现 / 8 个触发器 / foreign_key_check
     无行 / integrity_check = ok / 旧行存活

命令 1（静态守门，真实树）: python -X utf8 .trae/scripts/dev/check_androidtest_schema_expectations.py
  -> VERDICT: PASS    EXITCODE=0        （T-037-androidtest-static-check.txt run 1 与修复轮 run 4）
命令 2（守门自检，每条检测分支必须能红）: python -X utf8 .trae/scripts/dev/check_androidtest_schema_expectations.py --selftest
  -> OVERALL PASS (8 case(s), 0 broken)    EXITCODE=0   （同文件 run 2 与修复轮 run 5）
     修复轮 2：自检 8 -> 13 例 -> OVERALL PASS (13 case(s), 0 broken) EXITCODE=0（T-037-guard-window-fix.txt run 7）
命令 2b（修复轮 2 RED 复现：同一条违规，紧邻合法断言 vs 远离任何常量）:
  -> 修复前 紧邻=exit 0（漏报，run 2） / 远离=exit 1（run 3）；修复后 两者都 exit 1（run 5 / run 6）
命令 2c（修复轮 2 变异：按断言判定被强制恒真 / 恒假）:
  -> 各恰好 2 例转红、MUTATED_EXITCODE=1；还原与被测脚本逐字节相同（run 8）
命令 3（编译，不执行）: cd android; .\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
  -> BUILD SUCCESSFUL, :app:compileDebugAndroidTestJavaWithJavac 真的执行（非 UP-TO-DATE）   EXITCODE=0
     被核套件 SHA-256 = 0423B1CE361CEA5CE6D30E3D78DBE7AC001CCA8746085C5840228B5EB88C7D26，1408 行
     （T-037-androidtest-compile.txt 第 1 段；文件头明写 compile-only / NOT executed）
设备执行: deferred — 用户自行装机验证（user decision 2026-09-21）—— 从未运行，不得读成 PASS
```

### REFACTOR — optional
- Cleanup: 生产代码无需重构——实现一次成形，未删改任何既有断言，也没有为了变绿而放宽约束（触发器是唯一判定点，两条安装路径共用同一段条件文本）。修复轮做的是**测试侧的判定收敛**：设备侧套件原先自己复述 schema 的版本号、触发器名与新列名，等价于把同一条规则写进第二个入口——版本一 bump，两份就漂移（本次实测：`DATABASE_VERSION` 1→2 后该套件静态失真四处）。改为版本/触发器名/新列名只从 `TreeLearnSchema` 常量取一份，并把「fail-closed 必须抛」的那对版本号从硬编码 `(1, 2)` 换成由构造保证未注册的 `DATABASE_VERSION → DATABASE_VERSION + 1`，使下一次 bump 自动跟随而不是静默反转。
- Regression evidence: 与 GREEN 同一套件在 `.\gradlew.bat check` 下继续全绿（exit 0，见 `artifacts/evidence/T-037-check.txt`）；`MessageDao` 的互译函数被单测直接固定，未留下第二份拼法。修复轮不改生产代码，因此**本任务自己的** JVM 增量不变（仍为本任务新增 3 套件 / 23 例）；仓库总量会随并行 lane 变动——修复轮内另一条 lane 在 `:app` 加了 `HomeWiringTest`，同日复跑同一命令即由 48 套件 / 483 例变为 49 套件 / 496 例（算式与时间戳见 `T-037-jvm-tests.txt` 末段）；新增的静态守门自身带正反双向自检（修复轮 2 后 13 例 = 原 8 例 + 新增 5 例），并已用**修复前**套件实测为红（`T-037-androidtest-static-check.txt` run 3，exit 1 / 14 条）。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- **唯一判定点**：这条不变式（引用只出现在 USER 行且非空）只允许有一份文本。`ALTER TABLE ADD COLUMN` 在 SQLite 上不能携带 CHECK，所以不要把 CHECK 写进 `MESSAGES_DEFINITIONS` 再另写一份迁移逻辑——两份实现会对同一输入给出不同结论，这是本仓库已记录过的失败形态。
- 迁移必须是单步向前、且 `applyForwardMigrations` 的 fail-closed 语义不变。
- 不得重建 `messages` 表：`answer_revisions`、`branches.source_message_id`、`branches.reading_anchor_message_id` 都引用它，重建需要关闭外键，风险远大于收益。
- 不写任何正文（问题、回答、引用文字）到日志、证据或任务文件；测试夹具用短 ASCII 假串。
- 引用进入提示词的线格式**不在本任务**，由 T-032 决定并记录为一次决定。
- **设备侧测试不要复述 schema 常量**（修复轮教训）：`androidTest` 的期望值只许从 `TreeLearnSchema` 派生——版本号、触发器/约束名、schema 自己引入的列名。复述会让「契约改了、测试没改」在 `gradlew check` 里完全不可见（该命令只跑 JVM，从不编译 `androidTest`），而且负例一旦写死一个将来会被注册的版本对，断言会**静默反转**。守门 `.trae/scripts/dev/check_androidtest_schema_expectations.py` 已把这条钉死；负例一律绑定由构造保证成立的输入（如 `DATABASE_VERSION → DATABASE_VERSION + 1`）。

## Verification report

- Verdict: 待独立 verifier 判定，**实现者不自审**。上一轮两个独立门（harness verifier = PARTIAL、`db-migration-reviewer` = FAIL）收敛到同一个缺陷：`DATABASE_VERSION` 1→2 已 bump，但**只在设备上跑的** `androidTest` 套件仍写在旧契约上；`gradlew check` 是 JVM-only，因此这四处红在门禁里完全不可见。修复轮已按根因处理（期望值改为从 `TreeLearnSchema` 派生、fail-closed 对改为派生、补真实 V1→V2 用例），并新增一条静态守门防复发。实现者结论仍为 PARTIAL：功能、JVM 证据、Java-only 守门、文档影响齐备；**设备侧套件只编译、从未执行**；`db-migration-reviewer` 复核与真实 Android SQLite 行级证据按用户 2026-09-21 指令缺省。修复轮 2（同日的第二个缺陷）同样只由独立门发现：该静态守门自己带一个 fail-open 缺口（豁免取自窗口而非被判定断言），已按根因修掉并补足自检与变异证据；实现者仍不自审，Verdict 仍为 PARTIAL，留给独立 verifier 重新判定。
- Evidence: RED `artifacts/evidence/T-037-red.txt`（exit 1）；GREEN `artifacts/evidence/T-037-green.txt`（exit 0）；套件计数 `T-037-jvm-tests.txt`（两个口径：聚焦命令 48 套件 / 483 例，T-037 实现态；`check` 77 套件 / 769 例，2026-09-22T00:11:44；均 0 失败 0 错误 0 跳过，文件内含分项与 `EXITCODE=0`。**两个数字都会随并行 lane 新增套件而上移**——同日复跑同一命令已得 49 套件 / 496 例，增量是本仓库另一条 lane 的 `HomeWiringTest`，见该文件末段的算式与说明）；变异测试 `T-037-mutation.txt`（A/B 两次变异各 exit 1、还原后 exit 0）；`T-037-check.txt`（`gradlew check` exit 0；修复轮中途重跑 exit 1、失败点全在 T-032 在建的 `src/main`，T-032 落地后 run 3 复跑 exit 0）；`T-037-guard.txt`（run 1 与 run 3 均 `VERDICT: PASS (13 OK line(s))`、exit 0；修复轮中途重跑因 Gradle 阶段取不到 APK 而 `VERDICT: FAIL`，同因）；`T-037-docs.txt`（文档核对、编码扫描、反向查找、落点读回）；`T-037-sqlite-supplement.txt`（桌面 SQLite 附件，自带「NOT device evidence」声明）；`T-037-validate-tasks.txt`（`errors=0`）。**修复轮新增**：`T-037-androidtest-compile.txt`（`:app:assembleDebugAndroidTest` 编译通过 exit 0，文件头明写 compile-only/NOT executed；第 2 段记录修复轮中途同一命令 exit 1 且错误全在 `src/main`，run 3 复跑 exit 0）、`T-037-androidtest-static-check.txt`（真实树 PASS exit 0 / 自检 8 例 PASS exit 0 / 对修复前套件 FAIL exit 1 共 14 条问题）、`T-037-jvm-rerun.txt`（聚焦命令重跑 exit 0，用于重建未受污染的 test-result XML）、`T-037-external-blocker-src-main.txt`（并行任务 T-032 导致 `:app` 无法编译的只读记录）。**修复轮 2 新增**：`T-037-guard-window-fix.txt`（缺口 RED 复现 run 0-3 与修复后 run 4-8；每条命令后都紧跟 `EXITCODE=`、改动前后脚本 SHA-256、独立于守门解析器的 `rg` 原文扫描 8 处、`android/**` 边界核对、pre/post 差分只落在被改函数与自检两处）、`T-037-guard-window-mutation.py`（可复跑的变异测试：判定强制恒真 → 2 例转红、恒假 → 2 例转红、还原逐字节一致）。收尾又对同一证据文件追加 run 19（19a-19f），在**改后的真实树**上重放本轮全部结论：守门 exit 0；自检 `OVERALL PASS (13 case(s), 0 broken)` exit 0；探针在新守门 probe_a=control=1（exit 0，判定不再取决于距离），在冻结的修复前副本 probe_a=0 / control=1（exit 1，漏报复现）；独立于守门解析器的 `rg` 原文扫描 8 处 `PRAGMA user_version`；`validate_tasks.py` errors=0。守门脚本 SHA-256 = `D317A42021EBA432EF39E70A963AA509040006CFE280273B3DCEBFA34FA0FFAD`（534 行）。
- Mutation evidence (required because the schema suite is the sole evidence for "one rule text, both paths"): 变异 A 让共用的 `MESSAGES_QUOTE_INVARIANT_STATEMENTS` 丢掉 UPDATE 一半 → 7 例中 3 例转红（`theRuleIsOneConditionTextWrappedOncePerWriteEvent` / `nothingElseInEitherPathNamesTheColumn` / `bothPathsExecuteTheSameInvariantText`），exit 1；变异 B 让**只**升级路径 `MIGRATION_V1_TO_V2_STATEMENTS` 丢掉 UPDATE 一半（正是「两条路径漂移」的形态）→ 恰 1 例转红（`bothPathsExecuteTheSameInvariantText`，断言「upgrade 必须执行同样 2 条」），其余 6 例不受影响，exit 1；还原后源码 SHA-256 与变异前逐字节相同（`48d7589c…`），同命令 exit 0 全绿。细节与原始输出见 `artifacts/evidence/T-037-mutation.txt`。修复轮 2 又把「守门自身是否可证伪」纳入变异：`.trae/scripts/dev/check_androidtest_schema_expectations.py` 的按断言判定被强制成恒真 / 恒假两态，各自恰好 2 例转红（恒真 → 两条硬编码用例；恒假 → 两条派生用例），mutated exit 1、还原逐字节一致（`T-037-guard-window-fix.txt` run 8）——即该守门既不是恒绿仪式、也不是恒红仪式。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除或修改任何既有断言，因此无「旧断言为何过时」的说明需要；本任务新增 23 条断言（10 + 6 + 7）。分层覆盖：领域层（`quotedText()` 的取值、AI 位置拒绝非空引用、旧工厂/旧 `restore` 仍表示无引用）由 `MessageQuoteTest` 覆盖；边界层（域空串 ↔ 列 NULL 的双向互译与 null 为调用方 bug）由 `MessageQuoteColumnTest` 覆盖；schema 层（规则文本唯一、两条安装路径共用它、每版本恰有一个前向步骤）由 `TreeLearnSchemaQuoteInvariantTest` 覆盖；引擎层（触发器真的拒绝、失败带扩展码 1811、两条路径对同一输入结论一致）由桌面 SQLite 附件覆盖，Android 引擎层仍缺。
- Gaps: ① **设备侧 `androidTest` 只编译、从未执行**（`:app:assembleDebugAndroidTest` 是编译，不是运行；无 emulator、无真机，用户 2026-09-21 决定自行装机验证）——上一轮记录里「既有测试全部保持通过」只在 **JVM 范围**成立，`androidTest` 从来没有进入过任何一次运行，本轮把这句话写明；② 因此缺陷本身（套件契约随版本 bump 失效）在被两个门看出来之前一直不可见，本轮修的正是它：四处失真全部改为从 `TreeLearnSchema` 常量派生，并新增 `.trae/scripts/dev/check_androidtest_schema_expectations.py` 作为静态防复发守门（该守门能证明「期望值来自常量、没有复述」，**不能**替代设备执行）；③ 真实 Android SQLite 的迁移执行与约束拒绝码未取得，记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`；④ `db-migration-reviewer` 的独立复核未做——该角色按 `rules/sql.md` 是 READONLY 的独立复核方，实现者不得自审；⑤ 引用进入提示词的线格式属 T-032，本任务不定义；⑥ 外部阻塞（非本任务，已解除）：`android/app/src/main/java/com/treelearn/app/{wiring,ui}` 未跟踪且当时正被 T-032 改写，修复轮中途 `gradlew check`、`:app:assembleDebugAndroidTest`、Java-only guard 的 Gradle 阶段先后取得 exit 1，错误**全部**落在 `src/main`（`androidTest` 零错误）；T-032 落地后三条命令复跑均为 exit 0（`T-037-check.txt` run 3、`T-037-androidtest-compile.txt` run 3、`T-037-guard.txt` run 3）。中途记录保留在 `T-037-external-blocker-src-main.txt` 与 `T-037-androidtest-compile.txt` 第 2 段，作为「该轮门禁失败不是本任务造成」的凭据；⑦ **修复轮 2 的缺陷（由独立门发现，非实现者自查）**：本轮新增的静态守门自身带一个 fail-open 缺口——`check_no_hardcoded_user_version` 的豁免读的是 `PRAGMA user_version` 周围 **±160 字符窗口**内是否出现过常量名，而不是「被判定断言自己的期望实参」；于是**紧邻**一条合法断言的第二条硬编码 `user_version` 期望被放行（exit 0），同一条违规放到窗口之外才判 FAIL（exit 1）。已按根因改为按断言判定（左到右括号栈定位 + 期望值实参判定 + 解析不出即 fail-closed），自检 8 → 13 例（含「紧邻注入必须 FAIL」与「紧邻合法派生必须 PASS」这一对），并加变异测试证明判定两端都吃劲；缺口存在期间**当前树的 8 处 `PRAGMA user_version` 一直是合法的**，所以它是守门健壮性问题而不是现存 drift；⑧ 该守门的解析是**正则 + 括号栈**而非真正的 Java 解析器：注释剥离、字面量掩码、括号配对都是自实现的近似——遇到非常规写法（例如把期望值先赋给变量再断言）会 fail-closed 判 FAIL 而不是静默放行，方向安全但可能产生需要人工判断的误报；⑨ 根 `AGENTS.md` 失败案例库里写的自检例数（原「8 例」）与 `T-037-androidtest-static-check.txt` 中的 `OVERALL PASS (8 case(s)…)` 是**修复轮 1 的历史读数**，本轮扩到 13 例后没有回改这两处（本轮边界只允许改守门脚本、其自检、证据文件与任务文件记录段，不含 `AGENTS.md`），引用时必须带轮次。
- Risks: ① 触发器条件若被改写，两条安装路径会同时漂移而单测只能证明「文本一致」、不能证明「语义正确」——缓解是桌面附件对扩展码 1811 的直接观测；② `ALTER TABLE ADD COLUMN` 后新增列排在最后，与新装的声明顺序一致，但若将来有人在 `MESSAGES_DEFINITIONS` 中间插列，两条路径的列序会分叉；③ `Message` 新增 final 字段使既有构造点编译失败（已按预期逐点修复，属编译期暴露而非隐藏风险）；④ **门禁盲区**：`gradlew check` 编译不到 `androidTest`，任何只在该套件里表达的设备侧契约都会静默过期——本轮用「派生 + 静态守门」把风险降到「守门脚本本身被绕过」，但守门不会执行断言，真正的语义覆盖仍只在设备上；⑤ 设备侧套件新增的 V1→V2 升级用例是**从未运行过**的代码，其正确性目前只有静态复核（编译通过 + 常量引用核对）兜底，装机后必须重点看它；⑥ **本轮新增风险面**：守门脚本自身也是代码，而它是「设备侧契约不随版本 bump 漂移」的唯一自动防线——它错一次就是整类漂移静默，本轮实测的窗口豁免正是这种形态。缓解：每个判定分支都配「注入该违规必须 FAIL」的自检用例（现 13 例）＋变异测试证明判定两端吃劲；但守门仍只是静态检查，设备语义覆盖依然为 0（见 Gaps ①）。

## Handoff notes

- Completed: `messages.quoted_text` 列与 §6.2.19 触发器不变式（两条安装路径共用一份条件文本）；`DATABASE_VERSION` 1 → 2 与一条 V1→V2 前向迁移（fail-closed 未改）；`MessageDao` 双向互译；`Message` 携带引用；三个 JVM 套件 23 例；`gradlew check` exit 0；Java-only 守门 PASS；四处文档同步。**修复轮**：设备侧 `SqliteSchemaInstrumentedTest` 四处失真按根因修掉（版本/触发器名/新列名改为从 `TreeLearnSchema` 派生；fail-closed 对改为 `DATABASE_VERSION → DATABASE_VERSION + 1`；新增真实 V1→V2 用例并断言版本、列、8 触发器、外键与完整性、旧行存活）；新增静态防复发守门 `.trae/scripts/dev/check_androidtest_schema_expectations.py`（真实树 PASS、自检 8 例、对修复前套件实测 FAIL 14 条）；`:app:assembleDebugAndroidTest` 编译通过 exit 0；根 `AGENTS.md` 失败案例库追加一条。**修复轮 2**：静态守门 `.trae/scripts/dev/check_androidtest_schema_expectations.py` 的 fail-open 窗口豁免改为按断言判定（被判定调用的期望值实参；解析不出即 fail-closed），自检 8 → 13 例（含「紧邻注入必须 FAIL」/「紧邻合法派生必须 PASS」这一对），变异测试两态各 2 例转红、还原逐字节一致；真实树 exit 0 不变；`android/**` 本轮零改动。
- In progress: 无。
- Blocker: ① 设备执行——按用户 2026-09-21 决定不做，不是阻塞，记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`；② **外部阻塞（非本任务）**：`android/app/src/main/java/com/treelearn/app/wiring` 与 `.../ui` 未跟踪且正被 T-032 改写，修复轮内 `:app:compileDebugJavaWithJavac` 报错（先是 5 条、后收敛到 1 条），使 `gradlew check`、`:app:assembleDebugAndroidTest`、Java-only guard 的 Gradle 阶段当轮取不到 exit 0。本任务按协调约定不碰 `src/main`。
- Unblock condition: T-032 的 `src/main` 落地并编译通过后，重跑 `cd android; .\gradlew.bat check --no-daemon --console=plain` 与 `powershell -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1` 应回到 exit 0 / `VERDICT: PASS`（两者在 2026-09-21T23:45-23:47 的实测即为此值）。
- Next steps: 交给 `T-2026-09-21-real-data-path-wiring-032.md` 消费该列（对话读写与提示词装配）；独立 verifier 需补 `db-migration-reviewer` 复核，并注意本条已明确「`androidTest` 只编译、未执行」。修复轮 2 之后需要独立复核的是：该守门的按断言判定是否真的覆盖了全部出现形态（含 `execSQL` 赋值与 `assertTrue` 包裹），以及「紧邻注入必须 FAIL」这条自检是否确实能红（变异与对照证据在 `T-037-guard-window-fix.txt` run 0-8）；另请复算自检例数（现 13 例）与本任务旧记录里的 8 例之间的差异。
- Watch-outs: 不得把规则只留在 Java 里；不得为了让测试过而放宽约束；`quoted_text` 只许出现在非空 `USER` 行，空串是域的语言、NULL 是列的语言，互译只许留在 `MessageDao`；桌面 SQLite 附件不得被引用为设备证据；**设备侧套件的期望值不得再复述 schema 常量**（`check_androidtest_schema_expectations.py` 会判 FAIL），修复轮新增的 V1→V2 升级用例在装机前从未运行过；**守门的豁免必须来自被判定对象自身**——不允许再用「附近出现过常量名」这类窗口证据（修复轮 2 实测该形态会漏报紧邻注入，见 `T-037-guard-window-fix.txt`）。

## Verification report — 收尾复核（verifier #3，2026-09-24）

- Verdict: PASS。实现者两轮修复的根因处理（期望值从 `TreeLearnSchema` 常量派生、fail-closed 对派生、守门按断言判定）经本轮独立复跑证实。
- 独立复跑静态守门：真实树首轮 **FAIL(2)**——两条问题均指向 `ProviderModelDaoInstrumentedTest.java:109-110` 硬编码 `"PRAGMA user_version = 2"`。该文件属 T-083（晚于本守门交付的新文件），**守门按设计抓到了下游复述字面量的漂移**，正好是它存在的意义。处置（跨 lane 记录，已同步 T-083 任务文件）：夹具改为具名常量 `PREVIOUS_SCHEMA_VERSION`（初值派生自 `FORWARD_MIGRATIONS` 末步 `fromVersion()`），守门白名单按 `V1_SCHEMA_VERSION` 先例登记该名（字面量仍 fail-closed）。复跑：真实树 `VERDICT: PASS`（judged 12）、自检 `OVERALL PASS (13 case(s), 0 broken)`、夹具改动 `:app:compileDebugAndroidTestJavaWithJavac` EXITCODE=0。证据 `artifacts/evidence/T-037-verify3-guard-2026-09-24.txt`（含 FAIL 首轮、修复、复跑全过程）；守门脚本 SHA-256 `b1e6595a…8480`。
- `db-migration-reviewer` 缺口（Gaps ④）→ 本轮由收尾 verifier 以静态复核承担：`MIGRATION_V1_TO_V2` / `MIGRATION_V2_TO_V3` 均为单步前向、`applyForwardMigrations` 的 fail-closed 语义未变、不变式条件文本一份两条安装路径共用（该事实有变异 A/B 证据钉住，见上文 Mutation evidence）；真实引擎执行仍 `deferred — 用户自行装机验证（user decision 2026-09-21）`。
- 门禁（本轮实测）：`./gradlew --no-daemon --console=plain check` EXITCODE=0（`artifacts/evidence/P3-B3-gates-2026-09-24.txt`）；Java-only guard `VERDICT: PASS (13 OK line(s))`（`artifacts/evidence/P3-B3-no-kotlin-2026-09-24.txt`）。
- 设备侧（androidTest 全部用例，含修复轮新增的 V1→V2 用例）→ 保持 `compiled, never executed — 用户自行装机验证（user decision 2026-09-21）`，装机后优先跑。
