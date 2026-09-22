# Treelearn Harness Task: 修复真机上的约束错误分类并清掉 26 个既有仪器测试红灯

id: T-2026-09-22-060
title: 修复真机上的约束错误分类并清掉 26 个既有仪器测试红灯
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-22-constraint-code-classification-defect-059.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 40
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 本片**不属于**「Figma 新稿信息架构迁移」那批（049 的子任务 050–058），是那次迁移在真机上跑全量仪器测试时**顺带暴露**出来的独立缺陷修复。之所以必须单独立片，是因为它的根因与迁移无关，却被迁移片验收时撞见。
- Problem/background: 在真机 `ALP-AN00`（Android 13）上跑全量仪器测试，`178 tests / 26 failures`。26 个失败**不是同一个原因**，实测分四类（原始输出见 `artifacts/evidence/.t052-full-androidtest.log` 第 133–237 行）：
  | 成因 | 数量 | 性质 |
  | --- | ---: | --- |
  | 测试里把报错文案写死成 `(code 2067` 一类的前缀，真机实际是 `(Sqlite code 2067` | 21 | 测试侧期望串 |
  | **生产代码取不到扩展错误码**：`ConstraintFailures.EXTENDED_CODE` 的正则 `\(code ([0-9]+)` 匹配不上真机文案，`extendedCode()` 返回 0 → 唯一约束冲突认不出来 | **2** | **生产侧缺陷，真机用户可见** |
  | 测试在断言消息里**无条件**调用 `.error()`，成功结果上直接抛 `IllegalStateException: domain success has no error` | 2 | 测试侧自身写错 |
  | `NavigationStoreInstrumentedTest` 的夹具没给主题建标题行，而生产已要求标题非空 → `Guard.text` 抛 `title must not be null` | 1 | 测试侧夹具漂移 |
- 为什么以前没暴露（本片的直接证据）: 当初的错误码探针 `artifacts/evidence/T-004-constraint-code-probe.txt` 记录得非常明确——测的是 **`emulator-5554 / Medium_Phone_API_36.1 / API 36`**，那台机器吐的文案是 `... (code 1299 SQLITE_CONSTRAINT_NOTNULL)`，**恰好与代码里写的正则一致**；这台真机 Android 13 吐的是 `... (Sqlite code 1299 SQLITE_CONSTRAINT_NOTNULL), (OS error - 2:No such file or directory)`。模拟器 `Medium_Phone_API_36.1` 在本机仍然存在。因此第 1、2 类失败**只在真机上出现**，过去以模拟器为环境的证据天然测不到——也就是说，**生产侧的那个缺陷一直在真机上错着，只是从没被任何一次验收照到**。
- 生产侧缺陷的可见后果（实测，非推断）: `FirstTopicCreationInstrumentedTest > aSecondCreationForTheSameTopicIdIsRefusedAndChangesNothing` 报 `expected:<TOPIC_ID_ALREADY_EXISTS> but was:<STORAGE_REFUSED>` —— 真机上新建一个 id 已存在的主题，用户拿到的是「存储失败」而不是「主题已存在」；`AnswerRevisionGenerationOperationDaoInstrumentedTest > stabilizeRollsBackBothWritesWhenTheRevisionNumberIsAlreadyTaken` 则让裸的 `android.database.sqlite.SQLiteConstraintException` 直接穿透到调用方，而不是被翻译成领域错误。
- Expected outcome: 真机 `ALP-AN00` 上全量仪器测试 `178 tests / 0 failures`；同一套测试在 `Medium_Phone_API_36.1` 模拟器上同样全绿（证明修复是「两种文案都认」，不是「把期望改成真机那一份」）；`ConstraintFailures` 的取码判定收敛到**唯一一处**，测试不再各自写一份文案匹配。
- Core flow and reversibility: 纯缺陷修复，可逆性正常——改动集中在取码与分类，回退即恢复原状。错误分类的**目标值不变**（重复主题 id 仍应是 `TOPIC_ID_ALREADY_EXISTS`），本片只让它在该报的时候真的报出来。
- Inputs/outputs: 输入是设备抛出的 `SQLiteConstraintException` 文案；输出是 `DomainErrorCode`。本片不改输入来源，只改「怎么从文案里读出码」这一步。
- Constraints and trade-offs: 判定必须收敛到一处（`.trae/AGENTS.md` 失败案例库首条：同一条规则写在两个入口必然漂移）。测试不得再自己写一套文案匹配——那是把同一判定复制成 N 份，下一轮 Android 改文案时还会集体再红一次。跨模块可见性是个真实约束：`ConstraintFailures` 是 `data` 模块里的 package-private 类，而 androidTest 在 `app` 模块，因此需要把「取码」这一步提成 `data` 模块里可被两侧共用的单一公开判定点，由 `ConstraintFailures` 委托它。
- Acceptance evidence: 由真实命令输出提供——两个设备/模拟器上的全量仪器测试退出码与计数；针对两种文案的取码断言；`grep` 证明手写文案匹配只剩一处；`gradlew check`、Java-only 守门与全量 JVM 单测的退出码。
- Facts: 设备为真机 `AWSS013807000356 / ALP-AN00 / Android 13`，模拟器为 `Medium_Phone_API_36.1 / API 36`，两者本机都可用。五类约束码与文案：UNIQUE=2067、NOT NULL=1299、TRIGGER=1811、FK=787、CHECK=275（`T-004-constraint-code-probe.txt` 实测）。真机文案在码前多了 `Sqlite ` 前缀，并在尾部多了 `, (OS error - 2:No such file or directory)`。
- Assumptions: 真机与模拟器的差异来自 Android 版本对 `SQLiteException` 文案的构造方式，不是 SQLite 本身采用了不同的扩展码；此假设由「两边的码值相同、仅前缀与尾缀不同」的实测支持。
- Unknowns: 是否还有其它 Android 版本使用第三种文案，本机无法穷举。因此修复取向是**不依赖前缀**（只认 `code <digits>` 这一稳定片段），而不是枚举两三个已知前缀。
- Risks: (1) 放宽正则可能误匹配——必须保留「码值本身 + 列名列表」双重判据（`isUniqueOn` 现有做法），不能退化成只按文案子串判定；(2) 测试若改为调用生产判定点，会让测试与实现同源而降低独立性，因此**取码**共用、**期望的错误码**仍由测试独立给出；(3) `NavigationStoreInstrumentedTest` 的夹具补标题可能牵出别的夹具缺口，发现后如实记录，不要顺手改生产去迁就夹具。
- Deferred: 把仪器测试的**默认跑测设备**固定下来（真机 vs 模拟器）并写进 README——本片只在 README 记录「两种环境都要跑」的事实，不改跑测默认。
- Non-goals: 不改 schema、不改 migration、不新增表或列；不改任何 `DomainErrorCode` 的取值或语义；不修 `android/app/` 下与迁移批次（050–058）领地重叠的文件；不新增第三方依赖。
- Skipped non-critical questions and reason: 未追问「是否要为第三种文案预留」——本机只能观测到两种，按「不依赖前缀」实现即可覆盖未知的第三种，追问无法增加可验证信息。

## Goal

真机 `ALP-AN00`（Android 13）与模拟器 `Medium_Phone_API_36.1`（API 36）上，全量仪器测试都全绿；重复主题 id 在两条环境上都报 `TOPIC_ID_ALREADY_EXISTS`，且「怎么从异常文案里读出扩展码」这件事全仓只判定一次。

## Scope

### In scope
- `android/data/src/main/java/com/treelearn/data/db/dao/ConstraintFailures.java`：把取码正则从 `\(code ([0-9]+)` 改为**只依赖 `code <digits>` 这一片段**（从而同时匹配模拟器的 `(code N` 与真机的 `(Sqlite code N`），或在 `data` 模块内新增一个可被 `app` 模块 androidTest 引用的公开取码判定点并让本类委托它——两种做法择一，但**判定只能留一处**。
- 修掉四类失败中属于**测试侧**的部分：
  1. 写死 `(code NNNN` 前缀的期望串（21 处失败，分布在 `SqliteSchemaInstrumentedTest`、`AnswerRevisionGenerationOperationDaoInstrumentedTest`、`ContextSnapshotAiArtifactDaoInstrumentedTest`、`TopicBranchMessageDaoInstrumentedTest`）——改为**不再各自写死文案**，共用同一个取码判定点，期望的**码值**仍由测试独立给出。
  2. `TreeCanvasStoreInstrumentedTest` 里三处断言消息中**无条件**求值的 `.error()` 调用（约 L261 / L273 / L308）——改为只在失败分支求值（同文件 L357 一带已有 `isOk() ? "" : error()` 的正确写法可循）。
  3. `NavigationStoreInstrumentedTest > archivingFlipsTheFlagKeepsTheHistoryAndIsReversible` 的夹具补上主题标题行，使 `store.load(TOPIC)` 不再因 `Guard.text` 的「标题非空」而抛。
- 保留并强化**跨环境**证据：同一套全量仪器测试在真机与模拟器上各跑一次，两次都记录退出码与计数。

### Out of scope
- 任何 schema、migration、建表或列变更
- `DomainErrorCode` 的取值与语义
- 迁移批次（049 的子任务 050–058）的领地文件：`library/*`、`screen/LibraryScreen.java`、`screen_library*.xml`、`screen_tab_bar.xml`、`activity_main.xml`、`MainActivity` 的标签栏部分、`res/drawable/ic_*`、`res/values/` 下的 token 文件
- 把仪器测试的默认跑测设备改成固定某一个（本片只记录两种环境都要跑）
- **给 CHECK 约束起名**（`CONSTRAINT ck_... CHECK (...)`）：这是「精确到哪条约束」的版本无关结构解，但它改的是 `CREATE TABLE` DDL，既有库还需要重建表才能拿到约束名，属 schema 与 migration 变更，**越过本片红线**。已记为 deferred 候选（见 Implementation notes 的裁定记录），本片不做。
- 第三方依赖、AndroidX 之外的新库、Kotlin

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**约束冲突的分类准确度**——同一次唯一约束冲突，此前在真机上得到 `STORAGE_REFUSED`，修复后应得到 PRD 规定的 `TOPIC_ID_ALREADY_EXISTS`。必须原样保留的是：`isUniqueOn` 的「码值 + 列名列表」双重判据（不得退化为只看文案子串）、各 `DomainErrorCode` 的既有语义、以及「无法命名的拒绝必须由调用方重新抛出，不得伪报成某个业务码」这条既有约定（`ConstraintFailures` 类注释第 36–37 行）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 错误分类的生产者是 `data/db/dao/ConstraintFailures.java`，直接消费端口是 `data/db/dao/FirstTopicCreation.java`（实测 `TOPIC_ID_ALREADY_EXISTS` 那条路径）与 `AnswerRevision`/`GenerationOperation` 的 stabilize 路径；间接消费者是 `app` 侧把它们呈现给用户的屏。平台分支为**运行环境**分支（真机 Android 13 vs 模拟器 API 36）——这正是本片的核心，两套都要有证据。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本片确有生产行为变化（真机上的错误分类结果改变），且该变化是修复而非规格调整。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 约束冲突的扩展码读取不再依赖 `(code ` 前缀，因此在 Android 13 真机上也能正确分类；重复主题 id 在真机上恢复报 `TOPIC_ID_ALREADY_EXISTS`。
- Authoritative code/config/migration evidence: `android/data/src/main/java/com/treelearn/data/db/dao/ConstraintFailures.java`；`android/app/src/androidTest/java/com/treelearn/app/` 下四个套件与 `TreeCanvasStoreInstrumentedTest`、`NavigationStoreInstrumentedTest`；`artifacts/evidence/T-004-constraint-code-probe.txt`（旧探针，只测了 API 36 模拟器）。
- Documentation surfaces checked: `android/README.md`（测试与设备小节）、`Docs/05-technical-design/`（若记载了错误分类的实现方式）。
- Documentation files updated: `android/README.md` 补一节「约束错误码与跑测环境」：说明扩展码来自异常文案、文案前缀随 Android 版本变化（已实测 API 36 = `(code N`、Android 13 真机 = `(Sqlite code N`）、因此取码判定不依赖前缀，且**全量仪器测试必须在真机与模拟器上各跑一次**。
- Producer/consumer/sibling/compatibility paths checked: `FirstTopicCreation.java`、`FirstTopicCreationRulesTest`（JVM 侧同名规则的消费者）、`DomainError`/`DomainErrorCode`、`DomainResult.error()`、`Guard.text`。
- Superseded phrases/examples reverse-searched: `(code ` 与 `Sqlite code` 在 `android/**` 与 `Docs/**` 中的位置；`ConstraintFailures` 类注释里「SQLite's own stable format」这句已被实测推翻，必须同步改掉。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次有 README 变更。
- Historical/PRD handling: 本片不改 PRD。V3.1/V3.2 均不动。注意本片修的是**既有 PRD 规格**（重复主题 id 的报错语义）在真机上未被满足的问题，属「把规格修回来」。

## Acceptance criteria

- [x] 真机全绿：`ANDROID_SERIAL=AWSS013807000356 ./gradlew.bat :app:connectedDebugAndroidTest` 在真机 `ALP-AN00` 上退出码 0，报告为 `tests="178" failures="0" errors="0" skipped="0"`（完工前基线为 `178 tests / 26 failures`，见 `artifacts/evidence/.t052-full-androidtest.log`）。26 个红灯分两步清完：先按作业单四类清掉 22 个（真机降到 4），第五种成因（CHECK 标识文案随版本变化）经 leader 裁定后按「三角证明」改断言形状，再清掉最后 4 个（方法级、共 13 条断言）。裁定前的 4 条失败原文见证据文件第 3 节，裁定后的全绿运行见第 10 节（日志 `artifacts/evidence/.t059-real-device-after.log`：`Finished 178 tests on ALP-AN00 - 13` / `BUILD SUCCESSFUL` / `EXITCODE=0`）。
- [x] 模拟器同样全绿：`ANDROID_SERIAL=emulator-5554 ./gradlew.bat :app:connectedDebugAndroidTest` 退出码 0，JUnit 报告 `tests="178" failures="0" errors="0" skipped="0"`（`artifacts/evidence/.t059-emulator.log`）。同一份代码在 API 36 全绿、在 Android 13 有 4 红，正是「两种文案都认」的正面证据，也同时定位了第五种成因。
- [x] 取码判定只有一处：取码正则全仓只剩 `android/data/src/main/java/com/treelearn/data/db/ConstraintExtendedCode.java:41` 一处（`Pattern.compile("code ([0-9]+)")`），且**不含任何前缀字面量**；四个 androidTest 套件里已无任何文案匹配常量。**注意**：作业单给的原样命令 `grep -rn "\(code \|Sqlite code" android/` 在 GNU grep 的 BRE 下语法非法（`\(` 是分组起始符），实测报 `grep: Unmatched ( or \(`、退出码 2；等价 ERE（`grep -rnE "\(code |Sqlite code" android/`）的命中已逐条分类，全部落在「文档/注释」「测试输入样例」「无关命中」三类，见 `artifacts/evidence/T-059-constraint-codes.txt` 第 6 节。
- [x] 分类目标值不变：真机日志里 `FirstTopicCreationInstrumentedTest > aSecondCreationForTheSameTopicIdIsRefusedAndChangesNothing` 与 `AnswerRevisionGenerationOperationDaoInstrumentedTest > stabilizeRollsBackBothWritesWhenTheRevisionNumberIsAlreadyTaken` 都不再失败（重复主题 id 在真机上恢复报 `TOPIC_ID_ALREADY_EXISTS`，stabilize 不再让裸 `SQLiteConstraintException` 穿透）。
- [x] 双重判据未被削弱：`ConstraintFailures.isUniqueOn` 仍是「码值相同 **且** 文案含 `UNIQUE constraint failed: <列名列表>`」两条同时成立（`git diff` 未触碰该方法体，只把其中的取码调用改为委托 `ConstraintExtendedCode`）；未新增任何只看子串的判定。真机日志里所有 UNIQUE 断言（列名列表那半）逐个通过，也印证该判据在两种环境上都成立。
- [x] 未越界：开工前/后 `git status --porcelain` 差集只有 9 条代码路径（6 个 androidTest 套件 + `ConstraintFailures` + 2 个新增文件），没有 049 批次领地文件、没有 schema/migration；`android/README.md` 开工前已被迁移批次改成 M，本片只增三处（索引表一行、「常用命令」的跑测约定、「约束错误码与跑测环境」一节），详见证据文件第 8 节。
- [x] 回归与守门：`.\gradlew.bat check` 退出码 0；`android/scripts/no-kotlin-check.ps1` 退出码 0（VERDICT: PASS）；全量 JVM 单测零失败且 `app testDebugUnitTest` 通过数为 `suites=37 tests=364`（与基线持平），全模块合计 `suites=71 tests=702 failures=0`。
- [x] 规模实测：`python .trae/scripts/dev/count_production_lines.py` 实测新增文件 16 行、既有文件改动 2 行，判定值 **18 行**（口径：非空非注释），低于 `estimated_production_lines: 40`；测试文件不计入，物理行数并列在证据文件第 7 节。
- [x] **CHECK 类约束的版本无关判据**（实现中暴露的第五种成因；裁定见 Implementation notes「CHECK 标识文案的裁定」）：CHECK（码 275）的标识文案两环境**无共同片段**（API 36 报表达式、Android 13 只报表名），因此这 4 个测试方法改为**两段式证明**——运行时断言「码 = 275 **且** 文案含两环境唯一共享片段 `CHECK constraint failed: `，且拒绝发生在本场景对该表的写语句上」（「来自该表」是**构造性**满足的，由本场景写语句与该表 DDL 共同固定，**不在文案里比对表名**——API 36 的文案不含表名，比对表名会在 API 36 上打红；这一点由复核者指出并已补注），静态断言「`sqlite_master` 的 DDL 声明了该约束的期望片段」（版本无关）；并在同一测试内保留/补齐**正控**（合法行必须被接受），以补回判别力。**残余损失须如实登记**：运行时文案无法再证明「是该表上的哪一条 CHECK 拒绝的」，这部分只能由「DDL 已声明该约束」＋「该表确实发生了 CHECK 拒绝」＋「合法行被接受」三者合起来承担，不得写成「精确到该约束」。**已落地**：三段见 `android/app/src/androidTest/java/com/treelearn/app/SqliteSchemaInstrumentedTest.java` 的助手 `assertCheckRefusedOn`（L1383）——静态半 `tableDdl(db, table).contains(declaredCheck)`（L1392），运行时半 `ConstraintExtendedCode.of(message) == CODE_CHECK` 且文案含两环境唯一共享片段 `CHECK constraint failed: `（L1385/L1388），正控由四个方法各自持有（方法 2、3 本次补齐，方法 1、4 原有）。8 个调用点、13 条断言见证据文件第 12 节。**静态半确实取到了片段**：两环境全量仪器测试各 `178 tests / 0 failures`（第 10、11 节）——若 `sqlite_master` 里取不到期望片段，这 13 条断言会在两台设备上同时变红。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增 `android/data/src/test/java/com/treelearn/data/db/ConstraintExtendedCodeTest.java`，喂两条**实测**文案样例（API 36 的 `UNIQUE constraint failed: generation_operations.branch_id (code 2067 SQLITE_CONSTRAINT_UNIQUE)` 与 Android 13 真机的 `... (Sqlite code 2067 SQLITE_CONSTRAINT_UNIQUE), (OS error - 2:No such file or directory)`），断言两者取到**同一个码**，并独立给出期望值 `2067`／`1299`。为了让断言能对着「现有的取码规则」运行而不是先报编译错误，同一步里把 `ConstraintFailures` 里既有的正则 `\(code ([0-9]+)` **逐字搬移**进新公开类 `com.treelearn.data.db.ConstraintExtendedCode`，行为保持不变（该搬移不是修复）。
- Command: `cd android && ./gradlew.bat :data:testDebugUnitTest --tests "com.treelearn.data.db.ConstraintExtendedCodeTest" --console=plain`
- Exit code: 1
- Observed failure: 两条真机文案断言失败——`the Android 13 spelling expected:<2067> but was:<0>` 与 `Android 13 expected:<1299> but was:<0>`，而同一套里读 API 36 文案的断言全部通过。
- Failure reason validated: 该套 3 个用例中只有依赖 `(code ` 前缀的两条失败，API 36 那半边与「无码文案读成 NONE」都通过，说明失败来自取码规则不认真机文案这一缺失行为，既不是环境阻碍（同一次运行里 JVM 与 Android 依赖都正常工作），也不是判据退化（期望值是测试独立给出的字面量）。原始失败在 `android/data/build/test-results/testDebugUnitTest/TEST-com.treelearn.data.db.ConstraintExtendedCodeTest.xml`。

### GREEN — minimum implementation
- Implementation: 把取码判定收敛到唯一一处——`ConstraintExtendedCode` 的正则由 `\(code ([0-9]+)` 改为只认稳定片段 `code ([0-9]+)`（**不枚举前缀**，因此同时覆盖已知两种与未知的第三种），`:data` 侧唯一调用者 `ConstraintFailures.extendedCode` 委托它；同步改掉 `ConstraintFailures` 类注释里被真机实测推翻的「SQLite's own stable format」一句。`isUniqueOn` 的「码值 + 列名列表」双重判据与「无法命名的拒绝必须由调用方重新抛出」的约定均未改动。
- Command: `cd android && ./gradlew.bat :data:testDebugUnitTest --tests "com.treelearn.data.db.ConstraintExtendedCodeTest" --console=plain`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`；该套 XML 为 `tests="3" skipped="0" failures="0" errors="0"`（`android/data/build/test-results/testDebugUnitTest/TEST-com.treelearn.data.db.ConstraintExtendedCodeTest.xml`）。
- Regression evidence: `:data:testDebugUnitTest` 全量（5 套，含本片新增套）零失败；真机 `ALP-AN00` 与模拟器 `Medium_Phone_API_36.1` 的全量仪器测试退出码 0、`tests="178" failures="0" errors="0"`（两处原文见 `artifacts/evidence/T-059-constraint-codes.txt`）。

### REFACTOR — optional
- Cleanup: 不单独做重构步骤——RED 步的「正则逐字搬移进 `ConstraintExtendedCode`」本身即行为不变的结构调整，GREEN 只改了一行正则与注释；测试侧四个套件的 `CODE_*` 由字符串前缀常量改为独立给出的 `int` 码值并统一经 `ConstraintExtendedCode` 判定。
- Regression evidence: 见上一条 GREEN 的回归证据；另 `.\gradlew.bat check`、Java-only 守门与全量 JVM 单测的退出码与计数落进 `artifacts/evidence/T-059-constraint-codes.txt`。

### RED 2 — CHECK 断言的失败先行（第五种成因，真机）

- Test/scenario: 驱动的不是新写的用例，而是**既有的 4 个方法在真机上失败**这一状态：`SqliteSchemaInstrumentedTest` 里 13 条 CHECK 断言断的是失败的**标识文案**（`role = 'AI'` / `anchor_start_inclusive` / `last_seen_stable_sequence` …），而 Android 13 的文案里只有表名。先把「失败的是什么」记录下来，再改断言的形状——这一条是第二段改动的 RED。
- Command: `cd android && ANDROID_SERIAL=AWSS013807000356 ./gradlew.bat :app:connectedDebugAndroidTest --console=plain`
- Exit code: 1
- Observed failure: `There was 4 failure(s)`，四条都是 `the failure must name <表达式片段> but was CHECK constraint failed: <表名>`（`foreignKeysAreEnforcedNotMerelyDeclared` / `forkAnchorColumnsAreAllOrNothing` / `unreadHasASingleRepresentationInTheSequencePair` / `messageContentIsConstrainedByRole`），而码值那一半已经通过。
- Failure reason validated: 同一条命令、同一份代码在模拟器上是 `178 tests / 0 failures`，且四条失败里都不再出现 `expected code 275` 那半——所以失败来自「断言的第二半依赖版本相关的文案」，不是码值判定、不是环境阻碍、也不是夹具（四条原文与 13 条断言的枚举见 `artifacts/evidence/T-059-constraint-codes.txt` 第 3 节，模拟器对照见第 4 节）。

### GREEN 2 — CHECK 判定改为三角证明（最小改动，只动测试侧）

- Implementation: 新增助手 `assertCheckRefusedOn(db, table, declaredCheck, scenario, message)`，把 CHECK 的判定拆成两半——运行时「码 = 275 且文案含两环境唯一共享片段 `CHECK constraint failed: `（拒绝发生在本场景对该表的写语句上）」、静态「`tableDdl(db, table)` 的 DDL 声明了该约束的期望片段」——并给方法 2、3 补上正控（方法 1、4 原有）。生产代码、schema 与 migration **一字未动**。
- Command: `cd android && ANDROID_SERIAL=AWSS013807000356 ./gradlew.bat :app:connectedDebugAndroidTest --console=plain`
- Exit code: 0
- Passing evidence: `Finished 178 tests on ALP-AN00 - 13` / `BUILD SUCCESSFUL` / JUnit `tests="178" failures="0" errors="0" skipped="0"`（`artifacts/evidence/.t059-real-device-after.log`；证据文件第 10 节）。
- Regression evidence: 同一命令在模拟器 `ANDROID_SERIAL=emulator-5554` 上同样 `178 tests / 0 failures / EXITCODE=0`（第 11 节）；`gradlew check` 退出码 0（`:app:lint` / `:data:lint` 实际执行）、Java-only 守门 `VERDICT: PASS`、全量 JVM 单测 `app suites=37 tests=364 failures=0`（第 13 节）；三角证明三段的代码证据见第 12 节。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: TDD 适用——「两种文案取到同一个码」是一条可先写、可独立失败的断言。规模估算 40 行生产代码（取码点若提成公开类约 30 行 + 正则改动约 5 行 + 注释同步），未超 200 软上限。
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- **判定只留一处是本片的第一要求。** 现状是「生产一份取码正则 + 四个测试套件各写一份文案前缀」，同一条规则共五份实现——这正是 `.trae/AGENTS.md` 失败案例库首条描述的漂移结构。修法必须让测试**共用**取码判定点；但期望的**码值**仍由测试独立给出，否则测试会退化成实现的镜像、失去独立性。
- **正则不要枚举前缀。** 本机只能观测到 `(code N`（API 36）与 `(Sqlite code N`（Android 13）两种，但无法穷举其它版本。改成只认 `code ([0-9]+)` 这一稳定片段即可同时覆盖已知两种与未知的第三种；**不要**写成 `\((?:Sqlite )?code ([0-9]+)` 这类按已知前缀枚举的形式。
- **放宽正则的代价必须用双重判据兜住。** 正则放宽后误匹配面变大，因此 `isUniqueOn` 的「码值相同 **且** 文案含 `UNIQUE constraint failed: <列名列表>`」这条双重判据**不得**被简化成只看码值或只看子串——后者会把 NOT NULL/CHECK 报成唯一冲突（`ConstraintFailures` 类注释第 70–75 行已写明这一点）。
- **跨模块可见性**：`ConstraintFailures` 是 `data` 模块的 package-private 类，androidTest 在 `app` 模块。若选择新增公开取码点，放在 `com.treelearn.data.db`（或同级可见的包）并让 `ConstraintFailures` 委托它；不要让 `app` 用反射或复制一份。
- **测试里的 `.error()` 是急切求值**：`assertTrue("msg" + result.error(), result.isOk())` 在**成功**结果上就会先崩，因为消息串在断言前已算完。同文件已有 `isOk() ? "" : error()` 的正确写法，照它改。
- **`NavigationStoreInstrumentedTest` 的夹具**：补标题行时只补夹具，**不要**为了让夹具过关去放宽生产的「标题非空」约束——那个约束是后续片刻意加上的。
- 跑测环境：真机 `AWSS013807000356`（ALP-AN00 / Android 13）与模拟器 `Medium_Phone_API_36.1`（API 36）本机都可用，模拟器自检命令为 `emulator -list-avds`。同一台机器上不要并发跑两次 Gradle。
- 历史探针 `artifacts/evidence/T-004-constraint-code-probe.txt` 只测了 API 36 模拟器，其结论「按 measured-on-device 的文案格式取码」在真机上不成立——**不要**把它当作仍然有效的契约，但也不要删它：它是本片「为什么以前没发现」的直接证据。

### CHECK 标识文案的裁定（2026-09-22，leader 裁定，记录实现中暴露的第五种成因）

- **事实**（已由主会话独立核实，非采信实现者自述）：真机剩余 4 个失败全部是 `assertCause` 的第二半（`requiredText`）不满足，码值那一半是对的。实测两种文案：
  | 环境 | `messages` 上的 role CHECK | `branches` 上的锚点/序列 CHECK |
  | --- | --- | --- |
  | API 36 模拟器 | `CHECK constraint failed: role IN ('USER','AI')`（**表达式**） | `CHECK constraint failed: anchor_start_inclusive ...`（**表达式**） |
  | Android 13 真机 | `CHECK constraint failed: messages`（**仅表名**） | `CHECK constraint failed: branches`（**仅表名**） |
  两环境**无共同片段**：表达式片段只出现在 API 36 一侧，表名只出现在 Android 13 一侧（API 36 的文案里不含表名）。根因是 `TreeLearnSchema` 里的 CHECK 全是**匿名**的（`"CHECK (" + ... + ")"` 直接写在列定义里，见 `TreeLearnSchema.java` 的 `Branches` L220/223/225/257/273/310 与 `Messages` L324/325/336/338）。
- **为什么原来一直没露出来**：这 13 条断言在真机上卡在**更早的码值断言**上就失败了，从没走到 `requiredText` 那一步，所以这段版本依赖从未被看见；本片把码值那半修好后它才浮现。**这本身是一个教训**：一次失败只暴露最前面那个原因，把它修掉不等于把这条路径验证完了。
- **原断言在 API 36 上其实已经是「运气」而非严谨**：`messages` 上一个非法 role 行会**同时**违反 role CHECK 与 `role='USER' OR user_content IS NOT NULL` 这条内容 CHECK（`role='ROBOT'` 且 content 为 NULL 时两条都假）。也就是说 API 36 报出「role CHECK 赢了」取决于 SQLite 的求值顺序，是 DDL 书写顺序的副产物。所以旧断言并不是「更精确」，而是「恰好在那个版本上稳定」。
- **裁定：采用两段式证明（测试侧），不采用按已知拼写二选一，也不在本片给约束命名。**
  - **运行时**：断言「扩展码 = 275 **且** 拒绝来自该表（`messages` / `branches`）」。这半是版本无关的，且是本环境能给出的最强运行时事实。
  - **静态**：断言 `sqlite_master` 的 DDL 里**声明了**该约束的期望片段（套件已有 `tableDdl`/`schemaObjectDdl` 读法）。这半把「该约束确实存在」从运行时文案搬到了版本无关的 schema 定义上。
  - **正控**：同一测试内必须同时断言**合法行被接受**，以补回判别力——否则「某个 CHECK 拒绝了」与「本该拒绝的那条 CHECK 拒绝了」无法区分。
  - 三段合起来是「声明存在 → 该表发生 CHECK 拒绝 → 合法行通过」的三角证明，逐段都可独立复核。
- **残余损失（必须如实登记，不得写成「精确到该约束」）**：运行时文案不再能证明「是该表上的**哪一条** CHECK 拒绝的」。在 `branches`（有 6 条 CHECK）与 `messages`（有 3 条 CHECK）上，这一点由上述三角证明承担，而不是由文案承担。
- **被否决 / 延后的选项**：
  - 否决「按已知拼写二选一（表达式 **或** 表名）」：它是 `||` 条件的并集，判别力比两段式证明更弱，且把版本差异写进断言会让下一轮再漂移。
  - 延后「给 CHECK 约束起名」（`CONSTRAINT ck_... CHECK (...)`）：这是唯一能恢复「精确到该约束」且版本无关的**结构解**——命名约束在所有版本上都报 DDL 自己的名字。但它改 `CREATE TABLE` DDL，既有库还需重建表才能拿到约束名，属 schema 与 migration 变更，**越过本片「不得改 schema/migration」的红线**。记为独立片候选，须由用户另行授权。
- **本轮未受影响**：`ConstraintFailures.isUniqueOn` 的「码值 + 列名列表」双重判据判的是 UNIQUE，而 UNIQUE 的列名列表文案在两种环境上**实测一致**（真机日志里 `messages.branch_id, messages.ordinal`、`branches.topic_id`、`generation_operations.branch_id` 的断言全部通过），因此不需要同样的处理。

## Verification report

- Verdict: **PASS**（独立复核者 `agent-45e1ab1e`，2026-09-22，READONLY，未采信实现者自述）
- 独立复核原始结论摘要（复核者自跑，非采信实现者日志）:
  - **两个环境都由复核者自己跑出**：真机 `AWSS013807000356` → `Starting/Finished 178 tests on ALP-AN00 - 13`、`BUILD SUCCESSFUL in 1m 5s`、`EXITCODE=0`、JUnit XML `tests=178 failures=0 errors=0 skipped=0`；模拟器由复核者现场启动 `Medium_Phone_API_36.1`（`getprop ro.build.version.sdk`=36）→ `Finished 178 tests`、`BUILD SUCCESSFUL in 1m 17s`、`EXITCODE=0`、XML 同样全零。两次都用 `ANDROID_SERIAL` 定向、未并发跑 Gradle，收尾 `emu kill` 已还原。**证据新鲜度**：源码 mtime 19:41–20:08，两次运行在 20:21/20:25 之后，跑的确实是冻结后的同一份代码。
  - **生产侧修复正确且未过度放宽**：`ConstraintExtendedCode.java:41` = `Pattern.compile("code ([0-9]+)")`，**不含任何前缀字面量**（无 `(code `、无 `Sqlite `），非前缀枚举式。复核者把真实类编到系统临时目录、用自写探针驱动两条实测文案：API 36 形如 `... (code 2067 ...)` 与 Android 13 形如 `... (Sqlite code 2067 ...), (OS error - 2:...)` **取到同一个码**，1299/275 两拼写同样一致，无码文案返回 0。
  - **RED 确由缺失行为导致（复核者自己复现）**：只把正则换回 `\(code ([0-9]+)`，API 36 三条 PASS、Android 13 三条 FAIL（`expected=2067 actual=0`），与任务记录的 RED 形态一字不差；换回后 11 项全 PASS。
  - **唯一判定点成立**：`grep -rn "Pattern.compile" android/ --include=*.java` 全仓仅 2 处，取码正则只 1 处；`ConstraintExtendedCode.of` 的调用者全集 = 4 个 androidTest 套件 + `ConstraintFailures:69` + 新单测，无第六份实现；`FirstTopicCreation.classifyStorageRefusal(int, String)` 的码由调用方传入，自身不含取码逻辑。ERE 命中逐条分为「注释/javadoc」「测试输入样例」「无关命中」三类，**无一条是又写了一份判定**。
  - **双重判据未被削弱**：`ConstraintFailures.java:78-81` 的 `isUniqueOn` = 码值相同 **且** 文案含 `UNIQUE constraint failed: <列名列表>`，两条同时成立；`git diff` 逐 hunk 核对，该方法体**未被触碰**，改动仅限 `extendedCode` 内两行改为委托 + import 与 javadoc；「无法命名的拒绝必须由调用方重新抛出」仍在。被真机推翻的注释「SQLite's own stable format」已改写为「That text is *not* a stable format across releases」。
  - **三角证明三段 + 正控逐方法核对**：助手 `assertCheckRefusedOn`（`SqliteSchemaInstrumentedTest.java:1383`）——运行时段 L1385/L1388、静态段 L1389–1392（`tableDdl(db, table).contains(declaredCheck)`，读 `sqlite_master` 建表原文）、8 个调用点、13 条断言；**四个方法都有正控、无缺席**（L426-430 / L639-651 / L720-733 / L753-766）。**「表达式 或 表名」的 `||` 并集写法 0 处**（`grep -n "||"` 的 3 处命中经核对均与 CHECK 无关）。残余损失按原话登记，全仓「精确」的 6 处出现**全在否定/延后语境**，未被拔高。
  - **越界与红线**：差集复算得 T-060 真正改动的代码路径 **9 条**（6 个 androidTest 套件 + `ConstraintFailures.java` + 2 个新增文件），**无迁移批次领地文件、无 schema/migration**；`TreeLearnSchema.java` 在 `git status` 中无记录（未修改）、`DATABASE_VERSION` 未变、`grep -n "CONSTRAINT ck"` → **0 命中**（未给 CHECK 起名）。复核者另指出：`MainActivity.java`/`activity_main.xml`/`res/values/*` 在开工前**已是 M**（迁移批次未提交的工作区改动），因此「未出现在 diff」不能证明本片没碰它们——证明是本片**差集里没有它们**，这个区分记对了。
  - **回归与守门（复核者复核三项而非一项）**：`gradlew check` EXITCODE=0（`:app:lint`/`:data:lint`/`:ai:lint` 与 `:verifyDependencyDirection` 均**实际执行**非 UP-TO-DATE）；Java-only 守门自跑 `VERDICT: PASS (13 OK line(s))`；JVM 单测**强制 `--rerun-tasks`** 重跑 → app `suites=37 tests=364 failures=0`、data `suites=5 tests=30 failures=0`，debug 合计 71/702/0，与自述逐位一致。
  - **规模**：复核者独立复算新增 16 行、既有 2 行 = 18 行（并指出两套算式口径差异：按「整文件非空非注释相减」为 -3，仓库已裁定口径为「只计 added 行」得 2；结论一致，远低于 40/200/500）。
  - **复核者独立判断并确认了「第五种成因为何从未被看见」**：修复前 4 个 CHECK 方法的失败原文**全部**是旧 `assertCause` 的**码值那半**（`expected (code N but the store reported ...`），没有一条出现 `the failure must name ...`，即那 13 条 `requiredText` 断言**从未被执行到**；全日志 `expected (code ` 命中 21 条，其中仅 3 条是 `expected (code 275`（第四个方法先卡在 787 的 FK 断言上）。→ 教训「一次失败只暴露最前面那个原因，把它修掉不等于把这条路径验证完」**由原始输出支撑**。
  - **两处表述问题已由主会话修正**（不影响交付正确性，非 FAIL/PARTIAL）：(a) AC 第 9 条的运行时段补注「『来自该表』是构造性满足的、不在文案里比对表名」，以免后来者「加固」成表名断言而在 API 36 上打红；(b) Handoff 第 3 条的计数口径改为显式区分「21 条写死前缀」与「第一轮转绿的 17 条」，并给出 17+2+2+1=22、22+4=26 的算式。
  - **复核者如实登记的残余风险（不构成失败）**：无前缀正则的匹配面更大，探针实测 `"sample encode abc (decode 12)"` 会被读成 12；各调用点均由「具体期望码 + 列名列表/该表 DDL」双重固定，实际误分类面很小，文档中**未**被写成「零风险」。
- Evidence: `artifacts/evidence/T-059-constraint-codes.txt`（自证型，每条命令自带命令行与其后的 `EXITCODE=`）。第 0 节 = 摘要与五类归因；第 1/2 节 = 第一段 TDD（取码）的 RED/GREEN；第 3 节 = 裁定前真机 4 个失败（第二段 TDD 的 RED）与 13 条断言的枚举；第 4 节 = 裁定前模拟器对照；第 5 节 = 裁定前守门；第 6 节 = 取码判定只剩一处；第 7 节 = 规模 18 行；第 8 节 = 裁定前越界证明；第 9 节 = 第五种成因的裁定记录（保留 A/B/C 原始分析）；第 10/11 节 = 裁定后真机与模拟器的全绿运行；第 12 节 = 三角证明三段的代码证据；第 13 节 = 裁定后守门复跑；第 14 节 = 裁定后改动面与越界证明。
- Changed/deleted assertion rationale and equivalent behavior coverage: CHECK 断言从「比对版本相关的标识文案」改为「码 = 275 ＋ 该表 DDL 声明了该约束 ＋ 正控」。判据强度按**可证明性**看是更强而非更弱：旧断言在 API 36 上其实是运气（一个非法 role 行同时违反 role CHECK 与内容 CHECK，谁报出来取决于 DDL 求值顺序），而新三段里「该表确实拒绝」与「该表确实接受」是旧断言完全没有的判别力。非 CHECK 断言（UNIQUE / NOT NULL / FK / TRIGGER）未改形状——它们的文案两环境实测一致。不删除任何用例，不降低任何期望值。
- Gaps: (1) 运行时无法证明「是该表上的哪一条 CHECK 拒绝的」，改由三角证明承担，这是本片如实登记的残余损失；(2) 是否还有第三种 CHECK 文案只能由「不依赖前缀 + 静态半」保证，不能由穷举证明；(3) 若审查要求恢复「精确到该约束」，只有给 CHECK 起名（裁定中的选项 C）能做到，属 schema/migration 变更，须另立片并由用户授权。
- Risks: (1) 三角证明的运行时半对「该表」是**构造性**的（由本场景的写语句与 DDL 取值共同固定），不再由文案证明——这是裁定的明确取舍，已在代码 javadoc 与证据第 12.5 节写明；(2) 静态半依赖 `sqlite_master` 保存的 CREATE TABLE 原文，将来若改用重建表式迁移会一并改写该原文，届时断言会随之失配而不是静默通过（`tableDdl` 读的是实际建表文本，不是常量）；(3) 正则放宽引入误匹配——由 `isUniqueOn` 的双重判据与两环境全量测试兜底。

## Handoff notes

- Completed:
  1. **生产侧取码缺陷已修（真机用户可见的那条）**：新增唯一公开取码点 `android/data/src/main/java/com/treelearn/data/db/ConstraintExtendedCode.java`（正则 `code ([0-9]+)`，不枚举前缀），`ConstraintFailures.extendedCode` 委托它；`ConstraintFailures` 类注释里被真机推翻的「SQLite's own stable format」已改写。真机上「重复主题 id 报 `STORAGE_REFUSED`」与「裸 `SQLiteConstraintException` 穿透」两条后果已消失。
  2. **第一段 TDD（取码）走完**：RED 用两条实测文案（API 36 / Android 13）断言取到同一个码，`EXITCODE=1`；GREEN 改一行正则，`EXITCODE=0`、`tests="3" failures="0"`。
  3. **26 个红灯全部清完，真机 26 → 0**。口径说明（避免两个数字被读成打架）：`grep -c "expected (code " .t052-full-androidtest.log` = **21**（写死前缀类，其中仅 3 条是 `expected (code 275`——第四个 CHECK 方法 `foreignKeysAreEnforcedNotMerelyDeclared` 更早就卡在 787 的 FK 断言上）；另有生产取码 2、测试急切求值 `.error()` 2、夹具缺标题 1，**合计 26**。第一轮清掉 **22** = 上述 21 条中的 **17** 条（取码共用后即转绿）+ 生产取码 2 + 急切求值 2 + 夹具 1；第二轮清掉剩余 **4** = 21 条里的另外 4 条，即 CHECK 那 4 个方法——它们虽归在「写死前缀」这一格里，**真实成因是第五类**（CHECK 标识文案随版本漂移），修好前缀后才露出来，连同 13 条受影响断言在第二轮一并修完。
  4. **第五种成因按 leader 裁定以「三角证明」落地（只动测试侧）**：`SqliteSchemaInstrumentedTest` 新增助手 `assertCheckRefusedOn(db, table, declaredCheck, scenario, message)`——运行时段（码 = 275 ＋ 文案含两环境唯一共享片段 `CHECK constraint failed: `，拒绝发生在本场景对该表的写语句上）＋ 静态段（`tableDdl(db, table)` 的 DDL 声明了该约束的期望片段）＋ 正控（方法 2、3 本次补齐，方法 1、4 原有）。**明确未做**：否决「按已知拼写二选一」，延后「给 CHECK 约束起名」（属 schema/migration，记为独立片候选）。残余损失如实登记：运行时文案不能证明「是该表上的哪一条 CHECK 拒绝的」。
  5. **两环境全绿**：真机 `AWSS013807000356`（ALP-AN00 / Android 13）与模拟器 `Medium_Phone_API_36.1`（API 36）各 `178 tests / 0 failures / EXITCODE=0`；两次都用 `ANDROID_SERIAL` 定向（两台设备同时在线）。
  6. **守门与规模**：`gradlew check` 退出码 0（`:app:lint` / `:data:lint` 实际执行）；Java-only 守门 `VERDICT: PASS`；全量 JVM 单测 `app suites=37 tests=364 failures=0`（与基线持平）；规模判定 18 行 < 40；`validate_tasks.py errors=0`。
  7. **文档**：`android/README.md` 上一轮三处（持久化入口索引一行、跑测约定、「约束错误码与跑测环境」一节），本轮在该节补一段「CHECK 标识文案的漂移与三角证明」（含残余损失，以及「给 CHECK 起名」属 schema/migration、记为独立片候选）；`artifacts/evidence/T-059-constraint-codes.txt` 第 9 节由「未决项（需任务作者裁定）」改为「已裁定」，保留 A/B/C 原始分析作为裁定依据，并新增第 10–14 节记录裁定后的运行与落地证据。
- In progress: 无。
- Blocker: 无——原 Blocker（第五种成因）已由 leader 裁定解除。
- Unblock condition: 不适用。
- Next steps: (1) 交独立复核；`verifying` → `verified` 由复核方给出，本片不自评；(2) 若审查认为必须恢复「精确到该约束」，另立一片做「给 CHECK 约束起名 + 既有库重建表迁移」（裁定中选项 C 的完整版），本片不做；(3) 本片与迁移批次（050–058）都要跑 Gradle，不得并行——建议本片排在 052 的独立复核之后、053 开工之前（053 的验收基线需要「全量仪器测试可判定」）。
- Watch-outs: (1) 不得按已知前缀枚举取码写法；(2) 不得弱化 `isUniqueOn` 的双重判据；(3) **不得为让真机变绿而把 CHECK 断言放宽成「拼写二选一」或删掉正控**——那正是本轮被否决的做法；(4) 不得把三角证明读成「精确到该约束」；(5) 不得触碰迁移批次领地文件与 schema / migration / `TreeLearnSchema` 的 DDL；(6) 同一台机器上不得并发跑两次 Gradle。
