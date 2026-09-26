# Treelearn Harness Task: 首题原子创建事务（五类行同事务 + 全败回滚）

id: T-2026-09-21-025
title: 首题原子创建事务（五类行同事务 + 全败回滚）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: true
depends_on: [T-2026-09-21-sqlite-dao-topic-branch-message-022.md, T-2026-09-21-sqlite-dao-revisions-operations-023.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 七类本地事实必须以可迁移、可事务验证的 SQLite 结构持久化（见《Android SQLite 建表入场确认清单 V1.0》）。
- Problem/background: 本任务由 **T-2026-09-21-023 于 2026-09-21 按用户裁定的「三片垂直切分」拆出**。首题原子创建把五张表串在一个事务里，与单表 DAO 的验收轴不同（事务边界与全败语义），且它依赖 T-022 的三表 DAO 与 T-023 的 operation 写路径，故独立成片。
- Users/scenarios: 用户发出根问题后立刻断网/杀进程；重启后要么看到完整的主题树与可重试的生成操作，要么什么都没有，**绝不出现空树**。
- Expected outcome: `:data` 提供一个事务入口，在单个事务内写入五类行（`topics` 1 + `branches` 1（根）+ `messages` 2 + `generation_operations` 1），任一步失败则整体回滚且不留空树；返回可供调用方同步等待的提交结果。
- Core flow and reversibility: 顺序为 topic → 根 branch → 用户 message → AI message → operation；`branches.context_snapshot_id` 与 `context_snapshots.branch_id` 互为外键，本任务**不**写快照（首题无父分支，快照属分支分叉路径，见 T-2026-09-21-024 与 T-009），因此必须显式断言根分支的 `context_snapshot_id` 为 `NULL` 而不是留一个半成品引用。
- Inputs/outputs: 输入为已构造好的 `:core` 实体（`Topic`/`Branch`/`Message`）与 operation 的构造参数；输出为显式成功结果（含新主题 id）或分类错误。
- State and exception handling: 任一步违反约束必须让整体回滚；失败后 `topics`/`branches`/`messages`/`generation_operations` 中该主题相关行数均为 0；不得抛裸异常穿透调用方。
- Constraints and trade-offs: DDL 一律不在本任务；时间用 UTC epoch millis；参数化 SQL；复用 T-004 的 `DatabaseTransactionRunner`，**不新造事务机制**。
- AI recommendation and rationale: 五类行必须同事务，因为 §8 的原子性假设同时被恢复路径（T-008）与 UI 首屏依赖：留下「有 topic 没有根分支」或「有分支没有 operation」都会让恢复推导出不可重试的空树。
- **跨主题写入路径规则（T-004 点名交给本任务，原指向 023，切分后改指本任务）**: `branches.source_message_id` / `source_revision_id` 是 `ON DELETE CASCADE`，而 schema 与领域都不保证 source 与被分叉主题同属一个 topic；越过该规则的行会让「删主题 A」连带删掉主题 B 的分支。该规则库层无法用 CHECK 表达（跨表），因此**必须在写入路径上判定并拒绝**。本任务至少覆盖根分支（无 source）与「创建时显式提供的 source 必须与目标主题同属」的判定入口。
- User-confirmation decisions: ① 七张逻辑表及提示词用途/版本字段已确认；不新增第八张表。② 2026-09-21 裁定三片垂直切分 T-023，本片承担首题事务。
- Acceptance evidence: instrumentation 五类行同事务可读、注入失败后整体回滚无残留、`PRAGMA foreign_key_check` 无结果、跨主题 source 被拒、关闭重开后一致。
- Facts: 技术方案 §8（首次主题创建原子性）、§5.2、§6.1/§6.2、§7；建表清单 §2/§3。
- Assumptions: 调用方已用 `:core` 工厂构造好实体（本任务不重复领域校验）；首版无历史库。
- Unknowns: 无。
- Risks: 事务范围过大造成锁等待；把「五类行」写成四类（漏 AI message 或 operation）会让原子性在恢复路径上失效。
- Deferred: SSE 请求发起与 accepted 回调（T-006）；分支分叉时的两步写（branch → snapshot → 回填 `context_snapshot_id`）属 T-009/T-2026-09-21-024。
- Non-goals: 不实现网络、SSE、UI；不实现分支分叉（非首题）的创建；不实现稳定化（T-2026-09-21-023）。
- Skipped non-critical questions and reason: 重试时的事务重放策略由 T-008/T-010 决定。

## Goal

让首个主题的五类行在真实 Android SQLite 中全成或全败：提交后五类行全部可读且无悬空引用，中途失败后该主题不留任何行、更不留空树。

## Scope

### In scope
- **首题原子创建事务入口**：`Topic` + 根 `Branch` + 用户 `Message` + AI `Message` + `GenerationOperation` 五类行同事务写入，返回显式提交结果。
- 事务内对根分支的断言：`context_snapshot_id` 为 `NULL`、无 source（根分支不得携带任何分叉溯源）。
- **跨主题 source 判定**：写入路径上拒绝 source 消息/版本与目标主题不同属的行（库层无法表达，见 Requirement brief）。
- 复用 T-004 的 `DatabaseTransactionRunner`；复用 T-022/T-023 的 DAO，不复制其 SQL。

### Out of scope
- schema/DDL 与 `SQLiteOpenHelper`（T-004）。
- 三表 DAO（T-2026-09-21-022）与两表 DAO（T-2026-09-21-023）。
- `context_snapshots`/`ai_artifacts` DAO（T-2026-09-21-024）。
- 分支分叉创建的**两步写**（branch → 快照行 → `setContextSnapshot` 回填）属 T-009/T-2026-09-21-024。
- 稳定化落盘（T-2026-09-21-023）、SSE 与 accepted 回调（T-006）、UI。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增首题创建的事务边界；保持既有 DAO 的单表语义与「不稳定内容不得升级为稳定来源」不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游 `:core` 的 `Topic`/`Branch`/`Message` 工厂与 T-021 的命令/操作构造；下游 T-006 网关（在事务提交后才发起请求）、T-008 恢复（依赖五类行齐备）、T-012 UI 首屏、T-009 分支创建（复用同一事务风格）。
- Native bridge or independent-client compatibility/update requirements: 无桥接；只消费 Android framework SQLite。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首题事务的行数、顺序、失败语义与跨主题 source 判定；`DatabaseTransactionRunner` 的复用方式。
- Authoritative code/config/migration evidence: `android/data` 的事务入口源码、`:app` 的 instrumentation 夹具。
- Documentation surfaces checked: 技术方案 §8、§5.2、§6.1/§6.2、§7；建表清单 §2/§3；`android/README.md`。
- Documentation files updated: `android/README.md` 的持久化入口索引；若事务边界与技术方案 §8 有差异，同步技术方案（含「首题不写快照」这一点的明确记录）。
- Producer/consumer/sibling/compatibility paths checked: 事务入口的全部调用方（T-006/T-008/T-012）、根分支的 CHECK 约束、`branches.source_*` 的级联路径。
- Superseded phrases/examples reverse-searched: 旧事务入口名、把 023 写作跨主题规则的承接者（已改为本任务）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写冻结 PRD。

## Acceptance criteria

本轮按用户 2026-09-21 指令**不使用模拟器**：需要真机 SQLite 的条目一律记录为 deferred，绝不记 PASS、也不做假通过。

- [x] 同一条验收命令先 RED 后 GREEN（本轮为 JVM 套件 + androidTest 编译校验，非设备命令）：RED exit 1（javac 自报 14 个错误，全在本任务新增的 JVM 用例内）→ GREEN exit 0。证据 `T-025-red.txt` / `T-025-green.txt`，两条 Command 逐字节相同。
- [x] **跨主题 source 的判定**（本任务独有、库层无法用 CHECK 表达的那条规则）：已在 JVM 上逐格证伪——同主题接受；消息属他主题、版本属他主题、任一侧没有任何行，全部拒绝。证据 `FirstTopicCreationRulesTest` 11 例，计数见 `T-025-tests.txt`。
- [x] **重复主题 id 的明确错误**：判定按实测扩展码 1555 + SQLite 自己的小写约束文本，且同码的 `messages.id`、2067、1299 一律不得被误报成「主题已存在」。证据同上文件内 `classifyStorageRefusal` 的用例。
- [x] Instrumentation 套件仍在且仍**编译得过**（9 例保留在 `:app` androidTest），说明设备侧验收面没有被削弱或删掉。证据：`T-025-green.txt` 里 `:app:assembleDebugAndroidTest` 通过（只编译、不执行）。
- [ ] **首题原子性**：五类行同事务写入、`PRAGMA foreign_key_check` 无结果、根分支 `context_snapshot_id`/`source_*` 为 `NULL` —— deferred — emulator not used this round (user instruction 2026-09-21)。对应用例 `firstTopicCommitsAllFiveRowGroupsAndTheyAllAgreeOnOneTopic` / `theRootBranchIsWrittenWithoutABirthSnapshotOrAnySource` 保留但本轮未执行。
- [ ] **首题全败（回滚后行数为 0、不留空树）** —— deferred — emulator not used this round (user instruction 2026-09-21)。对应用例 `aStoreRefusalInTheMiddleOfTheTransactionRollsBackEveryRowGroup`。
- [ ] **拒绝后不留下分支行**（跨主题 source 的库层后果） —— deferred — emulator not used this round (user instruction 2026-09-21)。JVM 侧只证到「判定返回拒绝」这一步，行是否真的没落库要真机事务才能看。
- [ ] **失败时返回分类错误而非抛裸异常穿透调用方** —— deferred — emulator not used this round (user instruction 2026-09-21)。JVM 侧证到分类函数本身（含 `Outcome` 契约），未证 SQLite 异常穿过 `DatabaseTransactionRunner` 的那一段。
- [ ] **关闭重开后逐字段相等（含 UTC 毫秒）** —— deferred — emulator not used this round (user instruction 2026-09-21)。对应用例 `theFirstTopicSurvivesCloseAndReopenFieldByField`。
- [ ] **相邻失败场景**：嵌套/被窃取事务与 T-004 既有约定一致；任一步失败后下一次创建同一主题成功 —— deferred — emulator not used this round (user instruction 2026-09-21)。T-004 的 `DatabaseTransactionRunner` 既有设备用例未被本轮改动，也未复跑。
- [ ] `db-migration-reviewer` 按 Android SQLite 守门复核 —— deferred — emulator not used this round (user instruction 2026-09-21)；本轮没有新的设备侧 SQLite 证据可供其复核，复核应在恢复设备运行后进行。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增 JVM 用例 FirstTopicCreationRulesTest（11 个用例）在两个判定点尚不存在时编译失败（判定点是本任务的跨主题 source 规则与存储拒绝分类）
- Command: cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest :tree-layout:test :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: javac 自报 14 个错误，全部位于 android/app/src/test/java/com/treelearn/data/db/dao/FirstTopicCreationRulesTest.java（找不到符号 方法 decideSourceRefusal(String,String,String) 与 方法 classifyStorageRefusal(int,String)）
- Failure reason validated: 该次运行中不属于本任务的编译错误行为 0 行，因此「两个判定点尚未实现」是唯一原因
- Evidence file: artifacts/evidence/T-025-red.txt

```text
编译错误（取自 T-025-red.txt 的编译器输出，javac 自报 14 个错误）：
  找不到符号 方法 decideSourceRefusal(String,String,String)   6 处
  找不到符号 方法 classifyStorageRefusal(int,String)          8 处
  => 合计 14，与 javac 自报数字一致（6+8=14；verifier 复核：T-025-red.txt 里
     符号: 方法 decideSourceRefusal 共 12 行 / 两遍打印 = 6，classifyStorageRefusal 共 16 行 = 8）
同一次运行里其它文件的错误行 = 0（grep 结果见 T-025-red.txt 的 # attribution 头）
说明：本命令不含 :data:testDebugUnitTest——该源集里的 SecretEnvelopeTest.java 属另一任务，
其生产类尚不存在，会让整个 :data 单测编译失败；T-025 的 JVM 证明因此落在 :app 的单测源集。
```

### GREEN — minimum implementation
- Implementation: android/data/src/main/java/com/treelearn/data/db/dao/FirstTopicCreation.java 抽出两个**纯判定点** decideSourceRefusal / classifyStorageRefusal（唯一判定点，供 SQL 包装层与 JVM 用例共同使用）；on-device 行为不变
- Command: cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest :tree-layout:test :app:testDebugUnitTest :app:assembleDebugAndroidTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-025-green.txt（exit 0；:app:assembleDebugAndroidTest 通过 = 9 例 instrumentation 套件仍然编译得过）+ artifacts/evidence/T-025-tests.txt（四套 JVM 套件回读合计 tests=287 failures=0 errors=0 skipped=0，其中 FirstTopicCreationRulesTest tests=11 failures=0）+ artifacts/evidence/T-025-jvm-rerun.txt（--rerun-tasks 强制真跑一次：exit 0，FirstTopicCreationRulesTest tests=11 failures=0）
- Regression evidence: artifacts/evidence/T-025-device-runs-pre-directive.txt（本轮之前、用户下令停用模拟器之前捕获的设备证据，含当时 exit 0 的 99 例全绿与设备侧变异测试）；本轮不再有设备回归证据
- 补充证据: artifacts/evidence/T-025-supplement-data-suite.txt —— 另一任务的 SecretEnvelope 回到树里之后，把用户列出的完整 JVM 命令（含 :data:testDebugUnitTest）再跑一次同样 exit 0，因此上面这处偏差只影响 RED/GREEN 记录的命令字符串，不影响结论

```text
为什么验收命令里没有 :data:testDebugUnitTest：
  android/data/src/test/java/com/treelearn/data/secret/SecretEnvelopeTest.java 属另一任务，
  其生产类 SecretEnvelope 在整棵树里尚不存在（grep 'class SecretEnvelope' 只命中该测试文件），
  该源集一编译就整体失败，且失败发生在**本任务没有作者权限的文件**里。
  把 T-025 的 exit code 押在别的 lane 未完成的实现上不可接受，因此 JVM 证明放进 :app 的单测源集
  （本类与被测类同包 com.treelearn.data.db.dao，包内可见性在 classpath 上成立）。
  这是相对指令 (a) 的一处偏差，已在此处与 Handoff notes 明写。
```

### REFACTOR — optional
- Cleanup: 已完成——把两个判定从 SQL 包装里抽出为纯函数，使「唯一判定点」既被生产调用、也能在无设备时被证伪
- Regression evidence: artifacts/evidence/T-025-jvm-rerun.txt（抽取后 :app 单测真跑，exit 0，11/11）

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required；规模按用户 2026-09-21 裁定的三片切分控制在 500 行硬限内
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- instrumentation 测试写在 `:app` 的 androidTest 源集（项目约定：单一测试 APK），不在 `:data` 声明 androidTest 依赖。
- JVM 判定用例写在 `:app` 的**单测**源集（`app/src/test/java/com/treelearn/data/db/dao/FirstTopicCreationRulesTest.java`），与实际类同包，因此能取到包私有的纯判定点。放 `:data/src/test` 更自然但本轮不可用：该源集里另一任务的 `SecretEnvelopeTest.java` 会让整个 :data 单测编译失败，把本任务的红绿绑在别人的未完成实现上。
- 本轮按用户 2026-09-21 指令**不使用模拟器**：需要真机 SQLite 的验收条目一律记 deferred，不跑 `connectedDebugAndroidTest`、不 install、不启动设备。
- 事务必须复用 T-004 的 `DatabaseTransactionRunner`（它已断言拒绝嵌套与拒绝被工作体窃取事务）；**不新造**事务机制、不手工 `beginTransaction`。
- 五类行必须齐全。漏掉 AI message 或 operation 会让 §8 的原子性假设在恢复路径上失效——恢复推导会得到一棵无法重试的空树。
- 本任务**不写快照**：首题是根分支，无父分支来源，`context_snapshot_id` 必须显式为 `NULL`。不要为了「让写入看起来完整」而补一行快照。
- 跨主题 source 的判定必须在**同一事务内**完成（先查后写或写前查都可，但必须与写入同事务），否则并发下仍可越过。
- 调用方义务：`:core` 工厂已完成领域校验；本任务不重复实现领域规则，只做「事务 + 存储约束 + 跨表规则」。

## Verification report

> 独立 verifier 复核（verifier #2，2026-09-21 第三轮），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；需要真机的验收项一律记为
> `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY3-T025-*.txt`（helper 在 `artifacts/evidence/.verify3/`）。

- Verdict: PARTIAL（功能判据 PASS；一处必须修的记录缺陷；设备判据 deferred）
- 审阅文件与证据: 本任务文件；`android/data/src/main/java/com/treelearn/data/db/dao/FirstTopicCreation.java`（376 行）、
  `.../data/db/DatabaseTransactionRunner.java`、`.../data/db/TreeLearnSchema.java`（`BRANCHES` 第 238-243 行的 root CHECK）、
  `android/app/src/test/java/com/treelearn/data/db/dao/FirstTopicCreationRulesTest.java`（177 行 / 11 例）、
  `android/app/src/androidTest/java/com/treelearn/app/FirstTopicCreationInstrumentedTest.java`；
  证据 `T-025-{red,green,jvm-rerun,tests,supplement-data-suite,constraint-code-probe,device-runs-pre-directive}.txt`；
  verifier 证据 `VERIFY3-T025-jvm-direct.txt`、`VERIFY3-T025-androidtest-compile.txt`、`VERIFY3-T025-green-rerun.txt`。
- 代码量: 本任务生产改动只有 `FirstTopicCreation.java`；verifier 独立复算 **173** 非空非注释行（物理 376 / 非空 338，见
  `VERIFY3` helper 口径）→ 与自报一致，/ 500 硬上限 → **在预算内**。
- 任务合规: PASS（`validate_tasks.py` 对本文件 PASS；RED/GREEN 五个键为纯值；两条 `Command` 逐字节相同（`-ceq` True））。
- 验收点对照:
  - [x] **五类行同事务（原子入口）— 静态核对 PASS**: `create()`（第 186-195 行）把整段写入交给**一个**
        `runner.run(database, db -> createInside(...))`；`createInside()`（第 246-266 行）在同一个 work body 内按
        `TopicDao.insert` → `sourceRefusal` 判定 → `BranchDao.insert` → `MessageDao.insert`（用户 + AI 两条）→
        `GenerationOperationDao.insert` 依次写满五类行，中途**没有任何提交点**。任一步被拒即 `throw refuse(...)`，
        由 `DatabaseTransactionRunner.run`（第 61/74/80 行的 `beginTransaction`/`setTransactionSuccessful`/`endTransaction`）
        回滚。verifier 只做静态核对与编译核对，**未执行**（设备停用）。
  - [x] **根分支不写快照 / 无 source — 由 DDL 强制 PASS**: `TreeLearnSchema.java:238-243` 的
        `CHECK (parent_id IS NOT NULL OR (source_message_id IS NULL AND source_revision_id IS NULL AND
        anchor_start_inclusive IS NULL AND anchor_end_exclusive IS NULL AND anchor_quote IS NULL AND
        anchor_hash IS NULL AND context_snapshot_id IS NULL))` —— 根行必须八列全 NULL，与任务「首题不写快照」一致。
  - [x] **跨主题 source 判定 — PASS**: 唯一判定点 `decideSourceRefusal`（第 237-244 行，纯函数）＋ 事务内查表包装
        `sourceRefusal`（第 211-220 行）；同主题放行，消息属他主题 / 版本属他主题 / 任一侧无行全部拒绝。
        verifier **亲自用 javac + JUnitCore 直跑** `FirstTopicCreationRulesTest`（绕开 Gradle 与其它 lane）：
        `OK (11 tests)`、`EXITCODE=0`（`VERIFY3-T025-jvm-direct.txt`）。
  - [x] **重复主题 id 的分类 — PASS**: `classifyStorageRefusal`（第 292-298 行）用**实测**扩展码 `1555`
        **与** SQLite 自己的小写约束文本 `UNIQUE constraint failed: topics.id` 共同判定；取码证据在
        `T-025-constraint-code-probe.txt:1127`（`code 1555 SQLITE_CONSTRAINT_PRIMARYKEY`）。
  - [x] **Instrumentation 源码仍编译得过（设备面的最强替代证据）**: verifier 用 `--rerun` 强制
        `:app:compileDebugAndroidTestJavaWithJavac` **真实执行**（日志行为 `> Task :app:compileDebugAndroidTestJavaWithJavac`，
        无 `UP-TO-DATE` 后缀）、`BUILD SUCCESSFUL`、`EXITCODE=0`，产物 `FirstTopicCreationInstrumentedTest.class`
        于 21:51:09 重写（`VERIFY3-T025-androidtest-compile.txt`）。**只证明可编译，未执行任何用例。**
  - [ ] **首题原子性的设备结论**（五类行同事务可读、`PRAGMA foreign_key_check` 无结果、关闭重开后一致）——
        `deferred — emulator not used this round (user instruction 2026-09-21)`。
  - [ ] **全败回滚的设备结论**（注入失败后行数为 0、不留空树、拒绝后不落分支行）——
        `deferred — emulator not used this round (user instruction 2026-09-21)`。JVM 侧只证到「判定返回拒绝」这一步，
        「行是否真的没落库」只有真机事务能观测。
- 专项守门路由: `verifyDependencyDirection` PASS（verifier 复跑，`VERIFY3-verifyDependencyDirection.txt`）。
- 文档影响记录: PASS — `android/README.md:224/230/239` 已把 `FirstTopicCreation` 的行组、事务边界、
  「首题不写快照」、「跨主题 source 判定是全仓唯一一处」与 1555 + 小写文本的判定写成现行事实，verifier 逐行复核。
- 启动证据: N/A（库模块 + 用例，无进程/端口）。
- 结论: **PARTIAL**
- 修复指令:
  ① **必须修**：第 107-108 行把 RED 分项写成「`decideSourceRefusal` **7** 处 + `classifyStorageRefusal` **7** 处」，
     与证据矛盾。verifier 从证据与源码双向复核，实际是
     **`decideSourceRefusal` 6 处 + `classifyStorageRefusal` 8 处 = 14**：
     `T-025-red.txt` 里 `符号: 方法 decideSourceRefusal…` 共 12 行（两遍打印 → 12/2 = 6）、
     `符号: 方法 classifyStorageRefusal…` 共 16 行（→ 16/2 = 8）；测试源码
     `FirstTopicCreationRulesTest.java` 的调用点同样是 6 + 8。总数 14 正确，**是分项写错了**，
     按「每个数字都要能指到哪条命令哪一行」的规则必须改为 6 + 8。
  ② 设备恢复后跑一次 `:app:connectedDebugAndroidTest`，把上述两条 deferred 逐条转成实测结论；本任务代码无需改动。
- 阻塞点与剩余风险: 设备执行 deferred；跨主题 source 规则**只有这一道防线**（库层 CHECK 表达不了跨表规则），
  其库层后果（拒绝后不落分支行）也只在设备上可观；`sourceRefusal` 的「必须同事务调用」是调用方义务，
  签名强制不了（javadoc 已写明）。
- 跨 lane 记录（**不是**本任务缺陷）: verifier 复跑本任务 GREEN 命令时失败，失败文件全部是
  `android/app/src/test/java/com/treelearn/app/artifact/{ArtifactInputTest,ArtifactSchemaTest,DerivedRequestTest}.java`
  等**他人正在编辑的文件**（`VERIFY3-T025-green-rerun.txt`），不在本任务作者权限范围，按并发规则记为跨 lane 干扰。
## Handoff notes

- Completed: :data 的 FirstTopicCreation 抽出两个纯判定点（decideSourceRefusal / classifyStorageRefusal），生产行为不变；:app 新增 JVM 用例 FirstTopicCreationRulesTest（11 例，全绿）；androidTest 套件 FirstTopicCreationInstrumentedTest（9 例）保留且仍编译得过；RED/JVM GREEN/守卫/计数四类证据落盘。
- In progress: 无。
- Blocker: 无。设备侧验收整体 deferred（用户 2026-09-21 指令本轮不用模拟器），不是失败也不是通过。
- Unblock condition: 恢复设备运行后，跑一次 `cd android; .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain` 即可把 deferred 的八条逐条转成实测结论；本任务代码无需改动。
- Next steps: 交给 verifier 复核；完成后解锁 T-2026-09-20-deepseek-stream-gateway-006（accepted 回调在提交后才发起请求）、T-2026-09-20-generation-coordinator-recovery-008（恢复依赖五类行齐备）、T-2026-09-20-topic-branch-lifecycle-009（分支创建复用同一事务风格与两步写，并复用 sourceRefusal）。
- Watch-outs:
  - 「不留空树」的判据是**回滚后行数为 0**，不是「没有报错」。只断言抛了异常会把「异常发生了但行还在」这种最糟的情形判成通过。
  - 跨主题 source 规则库层无法用 CHECK 表达，因此它是**本任务独有的**一道防线：删掉这条判定不会有任何其它测试变红。用例必须用真实跨主题数据，而不是靠注释声明。
  - 上述「唯有一道防线」的变异测试在**本轮之前**做在设备上（把 sourceRefusal 降级为恒 null：只有跨主题那例转红，其余 98 例不动；还原后 9/9 复绿），原文保存在 `T-025-device-runs-pre-directive.txt`。本轮停用模拟器后，这条规则改由 JVM 判定点承接，同一类退化（判定恒返回「放行」）现在会让 `FirstTopicCreationRulesTest` 至少 3 例转红。
  - 重复 topic id 的分类依赖 SQLite **扩展码 1555 + 它自己的小写措辞**（`UNIQUE constraint failed: topics.id`）。首版把前缀写成大写，编译与阅读都正常却永不匹配，结果是每次重复创建都被降级成 STORAGE_REFUSED——这条缺陷最初由设备用例抓到，现在同一段文本由 JVM 用例 `theMeasuredDeviceTextIsTheTextThatMatches` 钉住（它用的就是实测字符串）。改这里必须重跑该用例，不要凭「看起来对」放行。
  - 本任务**不写快照**：首题是根分支，无父分支来源，`context_snapshot_id` 与 `source_*` 必须为 NULL。分叉的两步写（branch → 快照行 → setContextSnapshot）属 T-009/T-024，不要为「让写入看起来完整」在这里补一行快照。
  - sourceRefusal 的 javadoc 写明调用方义务：必须在**同一事务内**调用。签名无法强制这一点，只有调用点（create 与将来的 T-009）能保证。
  - **相对指令的一处偏差**：用户本轮要求跑 `:data:testDebugUnitTest`，但该源集里另一任务的 `SecretEnvelopeTest.java`（其生产类整棵树都不存在）会让整个 :data 单测编译失败；T-025 的 JVM 证明因此落在 `:app` 的单测源集（同包 `com.treelearn.data.db.dao`）。同期曾把该用例文件放进 `:data/src/test` 并被卡住，最终**移出**——注意：只移动了本任务自己的测试文件，没有移动任何生产源码。

## Verification report — 收尾复核（verifier #3，2026-09-24）

- Verdict: PASS（针对 verifier #2 第 3 轮的两条翻转条件的收尾复核；只判不实现）。
- ①（RED 分项 6+8）→ 已闭合。本文件 RED 段代码块现写「decideSourceRefusal 6 处 + classifyStorageRefusal 8 处 = 14」，与证据复算一致（`T-025-red.txt` 内 12 行 / 2 = 6、16 行 / 2 = 8）。
- ②（设备执行）→ 按用户 2026-09-21 决定保持 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不计 PASS/FAIL。Unblock condition（`connectedDebugAndroidTest`）保留，装机后执行即可把 deferred 逐条转为实测结论。
- 功能判据：第 3 轮已确认 7 条验收条件实质满足（五类行同事务静态核对、根分支 DDL 强制无快照、跨主题 source 唯一判定点、1555 + 小写文本分类、instrumentation 编译通过）。
- 门禁（本轮实测）：`./gradlew --no-daemon --console=plain check` EXITCODE=0（`artifacts/evidence/P3-B3-gates-2026-09-24.txt`）；Java-only guard `VERDICT: PASS (13 OK line(s))`（`artifacts/evidence/P3-B3-no-kotlin-2026-09-24.txt`）。
