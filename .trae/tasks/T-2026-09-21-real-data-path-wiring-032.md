# Treelearn Harness Task: 接线真实数据路径（首页与对话）

id: T-2026-09-21-032
title: 接线真实数据路径（首页与对话）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 460
shared_code_touched: true
depends_on: [T-2026-09-20-home-settings-ui-012.md, T-2026-09-20-chat-reasoning-answer-actions-013.md, T-2026-09-20-generation-coordinator-recovery-008.md, T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-20-derived-ai-artifacts-011.md, T-2026-09-20-keystore-secret-settings-005.md, T-2026-09-21-first-topic-atomic-creation-025.md, T-2026-09-21-quote-persistence-messages-column-037.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把已落地的 DAO/用例/协调器接成真实数据路径，使应用从空状态可创建主题、可提问、可重开读回，而不是在屏幕上返回 `NOT_WIRED`。
- Problem/background: T-012/T-013 交付 UI 与 presenter 时，数据路径以 `wiring.PendingHomeWiring` / `wiring.PendingChatWiring` 占位，两者对每个命令返回可见的 `NOT_WIRED`。占位类的 javadoc 已逐条列出缺口（主题枚举查询缺失、首个主题提交缺少 `AcceptedWrite`、对话缺少装配）。APK 因此在真实设备上不可用。
- Users/scenarios: 用户在个人设备安装后创建第一个主题并提问；重开应用后在首页看到自己的主题并继续对话；对既有回答重试/重置/切版本/分叉。
- Expected outcome: 首页与对话由真实存储与生成协调器回答；占位类被删除；生产装配中不存在 `NOT_WIRED` 生产者。
- Core flow and reversibility: 创建走单事务，失败整体回滚；卸载清除本机数据；不新增 schema，不写新迁移。
- Inputs/outputs: 输入已落地的 DAO、`FirstTopicCreation`/`BranchCreation`、`GenerationCoordinator`、`GenerationStabilizer`、`SqliteDerivedStore`、`KeyVault` 与网关工厂；输出真实 `HomeData`/`RootQuestionSubmitter`/`ChatData`/`ChatCommands` 实现与组合根装配。
- State and exception handling: 未配置 Key、无主题、读回空分支、重复源、协调器忙碌、DAO 拒绝都在屏幕上给出可见错误码，不静默吞掉。
- Constraints and trade-offs: Java-only、API 28+、禁写正文日志；主题列表需要一条新的只读查询，而本轮无设备（用户 2026-09-21 指令）。
- AI recommendation and rationale: 用一条垂直切片把「首页 → 创建 → 对话 → 重开读回」接成真实路径，而不是逐屏补丁，因为缺口集中在同一装配点。
- User choice and custom additions: 保留屏幕上可见的失败提示；本轮不使用 emulator。
- AI-autonomous decisions: 占位类整体删除而不是保留双实现；新查询只读、只加在 `TopicDao`。
- User-confirmation decisions: 父需求与本地安全边界；是否需要设备轮次验证 SQL 由用户后续决定。
- Acceptance evidence: JVM 测试 RED→GREEN、原文扫描证明占位无引用、`:app:assembleDebug`、Java-only guard、文档同步记录；SQL 的真实引擎证据按第 5 条记为 blocked。
- Facts: 占位类 javadoc 与 `AppContainer` 的当前装配是权威现状；`rules/sql.md` 要求 Android SQL 在真实 Android SQLite 上验证。
- Assumptions: 依赖任务的 DAO/用例已落地且其签名稳定。
- Unknowns: 真实设备上的 SQL 行级行为（本轮无法取得）。
- Risks: 把「编译通过」当成「可用」；新查询的排序列与索引不匹配导致全表扫描；删除占位类后遗漏引用。
- Deferred: 真实 DeepSeek 冒烟（无 Key）、设备安装启动、SQL 行级验证。
- Non-goals: 不新增表、不改 schema、不做 Canvas/导航/安全配置。
- Skipped non-critical questions and reason: 主题列表排序键细节按技术方案既有 updatedAt 语义处理，无需再问。

## Goal

让首页与对话两条数据路径由真实存储与生成协调器回答：占位类删除后，创建首个主题、提问、重开读回、重试/重置/切版本/分叉全部经过已落地的用例与 DAO，且生产装配中不再存在 `NOT_WIRED`；用户消息的引用文字随消息落库并在重开后读回（消费 T-037 新增的列）。

## Scope

### In scope
- `TopicDao` 新增一条只读查询：列出主题及其根分支标题、当前摘要与分支数（新 SQL，须按 `rules/sql.md` 在真实 Android SQLite 上验证）。
- `com.treelearn.app.wiring` 中新增首页与对话的真实实现，实现 `HomeData`、`RootQuestionSubmitter`、`ChatData`、`ChatCommands` 四个接口。
- 请求装配：`KeyVault` → 网关工厂 → `GenerationCoordinator.submit`；`AcceptedWrite` 落到 `FirstTopicCreation`（首题）与 `BranchCreation`（分叉）。
- 组合根：`AppContainer` 返回真实实现；`MainActivity` 只做依赖传递与生命周期，不写 SQL、不拼 JSON。
- 删除 `wiring.PendingHomeWiring` 与 `wiring.PendingChatWiring`，并清掉它们的全部引用与 README 当前态表述。

### Out of scope
- 树 Canvas 绘制与平板布局（T-017）；导航列表、归档与删除 UI（T-014）；安全、release 签名与端到端测试（T-018）。
- 任何 schema/DDL/迁移改动与新表（引用列由 T-037 交付，本任务只消费）。
- 真实 DeepSeek 的联网冒烟验证（需真实 Key，由用户装机实测）；网关的**装配**在本任务范围内。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 占位返回 `NOT_WIRED` 的**可见拒绝**改为真实读写；**保留**「失败必须可见、不得静默假装成功」的语义——未配置 Key、无主题、协调器忙碌仍返回屏幕可见的错误码。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `HomeData`/`RootQuestionSubmitter`/`ChatData`/`ChatCommands` 的生产者由占位类换成真实实现；`AppContainer` 是唯一装配点；`MainActivity` 只做传递。手机竖屏与平板横屏共用同一数据路径，线程边界沿用 `AppContainer.background()` 单线程 + `UiThread`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首页列表与提交、对话读写的真实数据来源；Key 到网关的装配链；`TopicDao` 新增只读查询。
- Authoritative code/config/migration evidence: `app/src/main/java/com/treelearn/app/wiring/**`、`AppContainer.java`、`MainActivity.java`、`data/src/main/java/com/treelearn/data/db/dao/TopicDao.java`、测试与构建输出。
- Documentation surfaces checked: `android/README.md`（`:app`/`:data` 入口索引、「记录缺口」小节）、`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` §9.2、`Docs/06-delivery/TreeLearn_V1_交付报告.md` §1/§4/§5。
- Documentation files updated: ① `android/README.md`——`:app` 入口索引把 `wiring.PendingHomeWiring` / `wiring.PendingChatWiring` 两行换成 `wiring.HomeWiring` / `wiring.ChatWiring` 及其协作者，新增「请求预算（T-032 的一次决定）」小节，两处「数据路径是占位」的当前态改写为「已接线（T-032，2026-09-22）」并列残留缺口，`:data` 段 `TopicDao` 行补记 `listOverviews`（只读、不改 schema）；② `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` §9.2 追加〔2026-09-22 落地值〕60000 / 512；③ `Docs/06-delivery/TreeLearn_V1_交付报告.md` §1 该行加〔历史快照 · 2026-09-21〕、§4 该行加〔2026-09-22 更新〕、§5 标题下插入〔过期说明 · 2026-09-22〕blockquote。
- Producer/consumer/sibling/compatibility paths checked: 首页 → 创建 → 对话 → 版本切换 → 重试；重开应用后读回。
- Superseded phrases/examples reverse-searched: 「首页的数据路径有一半是占位」「对话数据路径整体是占位」「`NOT_WIRED`」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 只同步当前运行事实。

## Acceptance criteria

- [x] 同一条 JVM 测试命令先 RED 后 GREEN：新实现覆盖读、写、可见拒绝三组断言（RED 命令给出非零退出码与观察到的失败，GREEN 给出零退出码与通过输出）。
- [x] 生产装配中 `NOT_WIRED` 不可达：`PendingHomeWiring`/`PendingChatWiring` 已删除、全仓（排除证据与任务文件）无引用，装配后的数据路径不再返回该码——用测试断言与原文扫描双重证明。
- [x] 首个主题提交经 `FirstTopicCreation` 单事务完成（topic/branch/snapshot/message/operation 同事务）；失败整体回滚，重复源被拒绝时不留下半份数据。
- [x] 对话命令经已落地用例：send/retry/reset 组装 `GenerationCommand` 交 `GenerationCoordinator.submit`；`selectRevision` 直接落库；`fork` 走 `BranchCreation`；`versions` 返回该消息的 `AnswerRevision` 序列。
- [ ] `TopicDao` 新查询与 `messages.quoted_text` 的真实行级行为必须按 [SQL 与建表](../rules/sql.md) 在 API 28+ 真实 Android SQLite 上执行验证（instrumentation，不得用 H2/JDBC SQLite/Robolectric/JVM fake 替代）。**用户 2026-09-21 决定放弃 emulator/真机轮次、改由用户本人安装 release APK 实测；该条记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得写成 PASS。**
- [x] `:app:testDebugUnitTest`、`.\gradlew.bat check`、Java-only guard、文档影响记录齐全且退出码为 0。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增的 wiring 垂直切片套件（HomeWiringTest 13 例 + ChatWiringTest 19 例，共 32 例）在 fork 的 id 钉法修复前运行：`ChatWiring.fork` 原用 `Guard.newId()` 当 branchId，而 branch 由 `Branch.child()` 生成，两者 id 不同，`BranchCreation.requireForkable` 要求 `snapshot.branchId == branch.id`，于是真实 fork 会抛异常。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.wiring.*" --no-daemon --console=plain
- Exit code: 1
- Observed failure: ChatWiringTest > forkOpensTheChildBranchWithoutStartingAnAttempt FAILED，org.junit.ComparisonFailure at ChatWiringTest.java:287（32 tests completed, 1 failed）
- Failure reason validated: 失败只来自 fork 的 id 钉法（修复前 ChatWiring.java sha256=5745C820B9F5BB125CFD5E90770FA9CCFE6B719A97A62B206E5C08F33B761858），与生产装配和其余 31 例无关；证据 artifacts/evidence/T-032-red.txt（末尾 EXITCODE=1）。

RED 细节（非键值）：`BUILD FAILED in 12s`，`54 actionable tasks: 4 executed, 50 up-to-date`。

### GREEN — minimum implementation
- Implementation: 真实实现 `wiring.HomeWiring`、`wiring.ChatWiring`（辅以 `HomeStore`/`SqliteHomeStore`、`ChatStore`/`SqliteChatStore`、`BranchHeading`、`AnswerPolicy`、`PromptLimits`、`RefusalNotices`、`GenerationPort`/`CoordinatorGenerationPort`、`LogcatTelemetry`）；`TopicDao.listOverviews` 新增一条只读查询；`AppContainer` 接真实装配、`MainActivity` 只传依赖；`wiring.PendingHomeWiring` 与 `wiring.PendingChatWiring` 整体删除。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.wiring.*" --no-daemon --console=plain
- Exit code: 0
- Passing evidence: 同一命令 GREEN：`BUILD SUCCESSFUL in 11s`，JUnit XML 回读 ChatWiringTest tests=19 failures=0 errors=0、HomeWiringTest tests=13 failures=0 errors=0（artifacts/evidence/T-032-green.txt）；`--rerun` 强制重跑时 testDebugUnitTest 真实执行、非 FROM-CACHE。全套件 `cd android; .\gradlew.bat check --no-daemon --console=plain` EXITCODE=0，98 个测试类 tests=1011 failures=0 errors=0 skipped=0（artifacts/evidence/T-032-checks.txt）。
- Regression evidence: 未命中回归——`cd android; .\gradlew.bat check --no-daemon --console=plain` EXITCODE=0（T-032-checks.txt）；Java-only guard `VERDICT: PASS (13 OK line(s))`（T-032-no-kotlin.txt）；占位删除后无残留生产引用（T-032-orphan-scan.txt）。

### REFACTOR — optional
- Cleanup: 删除 `wiring.PendingHomeWiring` / `wiring.PendingChatWiring` 及全部引用——`HomeViewState.Notice.NOT_WIRED`、`HomeScreen` 的 silent default、`ChatScreen.notWired`、`res/layout/screen_chat_body.xml` 的 `chat_not_wired`、`res/values/strings.xml` 的 `notice_not_wired` 与 `chat_not_wired`。
- Regression evidence: 同一 GREEN 命令 EXITCODE=0；原文扫描（排除 artifacts/** 与 .trae/tasks/**）仅剩文档历史引用且均已标注过期（artifacts/evidence/T-032-orphan-scan.txt）。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- 文件所有权：本任务独占 `android/app/src/main/java/com/treelearn/app/wiring/**`、`AppContainer.java`、`MainActivity.java`，以及 `TopicDao.java` 的新增只读方法。兄弟 lane 若要改组合根，必须先经 leader 交接，不要并行编辑同一文件。
- 只加只读查询，不加表、不加列、不改 `SQLiteOpenHelper` 的 version 与迁移；`rules/sql.md` 的建表入场与 `db-migration-reviewer` 不因本任务触发。
- 占位类整体删除，不得保留第二份实现；任何规则只能有一个判定点。
- 未配置 Key 时返回可见错误码，不发起请求；绝不把 Key、system prompt、问题、回答或 reasoning 正文写入日志、证据或任务文件。
- 无主题时首页列表返回空并使 UI 走空状态；不得用启动兜底数据假装有主题。
- 本轮无 emulator/真机：设备相关条目一律写 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得记为 PASS（用户 2026-09-21 决定放弃模拟器轮次、改由本人安装 release APK 实测）。
- 决定（PromptBudget，即 leader 交接第 5 条要求的一次性决定）：`wiring.PromptLimits` 是这两个数字的唯一落点——`CAPACITY_CHARS = 60000`、`MIN_ANSWER_CHARS = 512`，唯一构造点 `PromptLimits.budget()`，`AppContainer` 从它取唯一一份 `PromptBudget`。理由：技术方案与提示词设计只规定「先预留 system、当前问题与最小回答，不足则失败关闭」，**没有权威数字**；`PromptBudget` 以 UTF-16 字符计数（V1 无 tokenizer），60000 字符在最不利的中英混排下仍留在 64K token 上下文内并给回答留出空间，512 是生成测试早已使用的「一段可用回答」下限，选它们使仓库里只有一个数字。测试需要等价预算时引用这两个常量，不复述数字。
- 决定（fork 的 id 钉法）：`ChatWiring.fork` 只钉 **snapshot id**，branch 由 `ContextSnapshot.create` 生成后再用 `ContextSnapshot.fromStored` 重读校验，不再用 `Guard.newId()` 另取 branchId——后者与 `Branch.child()` 生成的 id 不同，会违反 `BranchCreation.requireForkable` 的 `snapshot.branchId == branch.id`。这是 RED 命中的真实缺陷，属本任务范围内的修复。
- 决定（设备侧 SQL 证据）：`TopicDao.listOverviews` 的只读查询与 `messages.quoted_text` 的往返由新 instrumentation 套件 `com.treelearn.app.RealDataPathWiringInstrumentedTest`（6 个 overview 用例 + 4 个引用往返用例）表达，本轮**只编译、未在设备上执行**（`T-032-instrumentation-compile.txt` EXITCODE=0）。按用户 2026-09-21 决定，设备执行记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`；桌面 SQLite 附件与 JVM 测试都不得当作设备证据。
- 规模结论（如实记录，不得当作已批准例外）：生产代码按用户 2026-09-21 裁定的「非空非注释」口径实测 **793 行**（`wiring/` 13 个本任务文件，已扣除 T-012 的 `AndroidSettingsStore.java` 68 行；`T-032-sizing.txt` EXITCODE=0），**超过 500 行硬预算**；`estimated_production_lines: 460` 是开工前估计、低于硬预算，故未触发 `validate_tasks.py` 的 sizing 例外校验。这是**待裁定偏差**，不是已批准的例外；`line-budget.ps1` 在物理行口径下另报 FAIL（1361 行）。两个口径都记在 `T-032-sizing.txt`。
- 未做（不在本任务范围）：真实 DeepSeek 联网冒烟（无 Key）；release 签名工程的正式复核（T-018）。附带核查（不改变本任务产物）：现存 `app-release.apk` 经 `apksigner verify` 确认为 v2 已签名、晚于全部源文件、含真实接线类，故用户可直接侧载，证据 `T-032-apk-signature.txt`。

## Verification report

- Verdict: 实现者自检 PASS（待独立 verifier 复核）；设备侧 SQL 行为 `deferred — 用户自行装机验证（user decision 2026-09-21）`。
- Evidence: artifacts/evidence/T-032-red.txt（EXITCODE=1）、T-032-green.txt（EXITCODE=0；ChatWiringTest tests=19 / HomeWiringTest tests=13 全绿）、T-032-checks.txt（`gradlew check` EXITCODE=0，98 类 tests=1011 failures=0 errors=0 skipped=0）、T-032-no-kotlin.txt（`VERDICT: PASS (13 OK line(s))`）、T-032-instrumentation-compile.txt（设备侧套件编译 EXITCODE=0）、T-032-orphan-scan.txt（排除 artifacts/** 与 .trae/tasks/**，仅剩文档历史引用）、T-032-mutation-round.txt（变异测试命中 6 例）、T-032-sizing.txt（规模）、T-032-apk-signature.txt（release APK 的 v2 签名与新鲜度核查）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除的是 UI 对 `HomeViewState.Notice.NOT_WIRED` 的依赖、`ChatScreen.notWired` 与两条字符串资源；等价覆盖由 `HomeWiringTest` / `ChatWiringTest` 的「可见拒绝」断言承担——缺 Key、无主题、协调器忙碌、DAO 拒绝仍各自给出屏幕可见错误码，故 `NOT_WIRED` 本身无需替代断言（它只是「拒绝」的一种措辞）。`fork` 的 id 钉法断言是**唯一**锁住该缺陷的用例，已做变异测试。
- Gaps: ① `TopicDao.listOverviews` 与 `messages.quoted_text` 的真实 Android SQLite 行级行为未在设备上执行——`RealDataPathWiringInstrumentedTest` 只编译通过，其 10 个用例从未运行（按用户决定记 deferred，见验收标准第 5 条）；② `FirstTopicCreation` 的单事务与「失败整体回滚、不留半份数据」语义由 T-025 的设备侧证据承担，本任务只断言路由经 `FirstTopicCreation` 而非第二条路径；③ `HOME_RECOMMENDATIONS` 仍无生产者、对话推荐条恒空且不指向 revision、终态 partial 答案读不到、`fork` 不发起生成且只允许 AI 位置——这四条已写进 `android/README.md` 的残留缺口；④ 真实 DeepSeek 冒烟与 APK 签名不在本任务范围（后者见 Handoff notes）。
- 静态复核（设备无法执行时的兜底，**不构成设备证据**）：`TopicDao.listOverviews` 的行乘法风险已逐条查过——① 根分支用 `LEFT JOIN ... r.parent_id IS NULL`，而 `uq_branches_root` 是 `UNIQUE(topic_id) WHERE parent_id IS NULL`，故每主题至多一行；② 摘要用 `LEFT JOIN ai_artifacts s ON s.kind=? AND s.scope_id=r.id`，该表**没有**唯一索引（`idx_ai_artifacts_kind_scope` 是普通索引），唯一性的真正判定点是写路径 `AiArtifactDao.insert` 的 `INSERT ... WHERE NOT EXISTS`（`changedRows()!=1` 即抛 `IllegalStateException`），因此「一个 (kind, scope) 至多一条」是存储层的既有规则而非注释，联接不会把主题乘开；`RealDataPathWiringInstrumentedTest.homeListCannotBeMultipliedByASummaryBecauseOneSummaryPerScopeIsTheStoredRule` 正是同时断言这两半。③ 排序 `updated_at DESC, id ASC` 是确定性全序。以上是阅读得到的结论，仍须装机后由该套件实际确认。
- Risks: 新查询的行级行为在真实设备上未经执行（装机后应优先看首页列表与引用往返）；占位删除后若有遗漏引用，编译期才会暴露（已用原文扫描兜底）；`TopicDao.listOverviews` 的排序列 `updated_at DESC, id ASC` 与既有索引的匹配未在设备上实测；本轮规模实测超 500 行硬预算，属待裁定偏差。

## Handoff notes

- Completed: 首页与对话两条数据路径接上真实存储与生成协调器；`TopicDao.listOverviews` 一条只读查询；`PromptBudget` 两个数字的一次性落点（`wiring.PromptLimits`）；`wiring.PendingHomeWiring` / `wiring.PendingChatWiring` 及全部引用删除；JVM 32 例 RED→GREEN；`gradlew check` exit 0（98 类 tests=1011 failures=0）；Java-only guard `VERDICT: PASS (13 OK line(s))`；设备侧套件已编译（未运行）；README 与两份 Docs 同步。
- In progress: 无。
- Blocker: 无阻塞实现；设备侧 SQL 证据按用户 2026-09-21 决定不做，记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`。
- Unblock condition: 若需要 SQL 行级证据，由用户提供一次设备/模拟器轮次并授权；用户当前路径是自己安装 release APK 实测。
- Next steps: 交独立 verifier 复核（注意：设备侧套件只编译未执行；规模超预算待裁定）；随后 T-018 做安全与 release 签名，T-019 做最终验收。
- Watch-outs: 不得用编译通过、JVM 测试或桌面 SQLite 附件替代真实设备 SQLite 证据；不得在组合根复制 presenter 规则；`PromptBudget` 的数字只许改 `wiring.PromptLimits`；`fork` 的 branchId 只许由 `ContextSnapshot.create` 产生。
- [leader 2026-09-21] **本任务本轮未开始**，原因是流程性的而非技术性的：分配给它并行的实现 lane 在开工前即收到 subagent 后端 `402 Payment Required: Insufficient Balance`，随后全部 lane 一并中断；容器允许的显式模型覆盖只接受 `gpt-*`，而后端只接受 `deepseek-flash`/`deepseek-v4-pro`，无法换后端续跑。主会话随后按优先级把余下预算用于恢复构建、补齐 T-014/T-017 的中断半成品、产出并校验签名 release APK 与交付报告。
- [leader 2026-09-21] 主会话在准备接线时已确认以下事实，可直接作为施工依据（完整叙述见 `Docs/06-delivery/TreeLearn_V1_交付报告.md` 第 5 节）：
  1. `TopicDao` **没有**枚举主题的查询，首页列表必须新增一条只读 SQL；按 `rules/sql.md` 其正确性只能在真实 Android SQLite 上取证，本轮无设备 → 该条注定 `blocked`。
  2. `messages` 表有 `active_revision_id` / `active_operation_id`，但 `MessageDao` **没有任何读回入口**，`Message` 领域对象也不携带它们；对话页要显示当前版本必须先补这条读路径。
  3. `ChatCommands.send(...)` 的 `quote` 参数在 `messages` 表**没有承接列**，提示词设计文档也未定义引用的线格式——引用追问在当前数据模型里无处安放，接线前需先补 schema（触发 `db-migration-reviewer` 与用户确认）或明确返回可见拒绝。
  4. 请求装配路径已探明：`DeepSeekRequestAssembler.assemble(purpose, thinkingEnabled, DeepSeekContextEncoder.encode(new ContextPlan(entries)), currentInput, PromptBudget)`；`Assembler` / `AcceptedWrite` 的可照抄形状见 `app/src/androidTest/java/com/treelearn/app/GenerationCoordinatorInstrumentedTest.java:344-410`。首个主题历史为空，`ContextPlan.Entry` 可由 `MessageDao.findByBranch` + `AnswerRevisionDao.findById` 拼出。
  5. `PromptBudget` 的容量与最小回答长度**没有权威数字**（技术方案与提示词设计只规定「先预留 system、当前问题与最小回答，不足则失败关闭」），接线时必须显式选定并记为一次决定。
- [leader 2026-09-21] 因此本任务保持 `ready`；`AppContainer` 仍装配 `PendingHomeWiring` / `PendingChatWiring`，应用在设备上依旧会显示「尚未接入本版本」。
- [android-dev 2026-09-22] 本轮接线完成并交 verifier。三条需要复核者注意的事实：① **`app-release.apk` 可直接侧载**——`android/app/build/outputs/apk/release/app-release.apk`（448376 字节，2026-09-22 00:25）已含 `wiring/ChatWiring` / `wiring/HomeWiring`、不含两个占位类，经 `apksigner verify` 判定 **APK Signature Scheme v2 验证通过**（签名者 `CN=Treelearn Personal Sideload`，RSA 4096），其 mtime 晚于 `android/` 下每一个源文件，dex 内含 `wiring/ChatWiring`（3 处）与 `wiring/HomeWiring`（3 处）、不含两个占位类与 `NOT_WIRED`（均 0 处），证据 `T-032-apk-signature.txt`（EXITCODE=0）。**勘误**：此前交接块据「`META-INF/` 里没有 `.RSA`/`.SF`」推断该 APK 未签名，该推断错误——v2/v3 签名在 APK Signing Block 而非 META-INF；字节数与交付报告记录的 429964 不同只因此后代码已变（真实接线使 dex 变大），不表示产物被替换。故用户可按计划自行侧载实测；签名与 release 工程的正式复核仍属 T-018。② 本任务规模实测 793 行 > 500 行硬预算，属待裁定偏差，证据 `T-032-sizing.txt`。③ 设备侧套件 `RealDataPathWiringInstrumentedTest` 从未运行，装机后应优先看它。
- [android-dev 2026-09-22] leader 交接 5 条事实的落点：第 1 条 → `TopicDao.listOverviews`（新增，仅只读）；第 2 条 → `ChatStore` / `SqliteChatStore` 提供读回入口并读 `active_revision_id`；第 3 条 → **已由 T-037 解决**，本任务只消费 `messages.quoted_text` 往返；第 4 条 → 请求装配按 `GenerationCoordinatorInstrumentedTest` 第 344-410 行的形状照抄；第 5 条 → `wiring.PromptLimits`（60000 / 512），见 Implementation notes 的决定。上述 leader 文字中「本任务保持 ready」「第 3 条待补 schema」是 2026-09-21 的历史陈述，现状以本块为准。
