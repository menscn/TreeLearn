# Treelearn Harness Task: 阅读位置保存与恢复接线

id: T-2026-09-21-042
title: 阅读位置保存与恢复接线
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 170
shared_code_touched: true
depends_on: [T-2026-09-21-real-data-path-wiring-032.md, T-2026-09-20-search-unread-reading-position-015.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD 6.2.5（聚焦阅读与现场恢复）要求离开分支再回来时回到原来的阅读位置，并且看过即清未读；存储与用例已交付，但**没有任何 Activity 消费者**。
- Problem/background: `reading/ReadingSession`、`ReadingStore`、`SqliteReadingStore` 已存在并被单测覆盖；全仓引用只在 `src/test`。`branches` 表已有 `reading_anchor_message_id`/`reading_anchor_revision_id`/`reading_list_item_offset`/`reading_viewport_offset` 四个列与 `latest_stable_sequence`/`last_seen_stable_sequence` 未读对。结果：读到一半退出应用再回来会丢位置，未读也永远不会被清掉。
- Users/scenarios: 用户在一条长回答中间退出应用，重新打开该分支后回到那一句；看完的分支不再显示未读。
- Expected outcome: 对话页在离开时保存语义阅读位置、在进入时恢复它；视口扫过稳定回答时把未读清掉。
- Core flow and reversibility: 保存位置是幂等覆盖写；未读清算是单调前进（`last_seen_stable_sequence` 不得超过 `latest_stable_sequence`，由库层 CHECK 保证）。
- Inputs/outputs: 输入 `ReadingSession`/`ReadingStore` 既有接口与 `branches` 既有列；输出对话页生命周期接线（进入恢复、离开与滚动保存）与宿主装配。
- State and exception handling: 位置指向的消息/版本被删除时库层 `ON DELETE SET NULL` 清位置，恢复退化为活动末端（最新一条）并标记 `atLiveEnd`，而不是崩溃；保存失败只记可见 notice，不影响阅读。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；不新增 schema（四个列与未读对都已存在）；保存不得在 UI 线程做 IO。
- AI recommendation and rationale: 把保存点放在对话页的生命周期与滚动回调上，复用既有列，不新造一套「上次读到哪」的存储——同一事实只能有一个判定点。
- User choice and custom additions: 用户 2026-09-21 要求交付完整可用的 APK，阅读位置属 PRD P0 且目前完全没有消费者。
- AI-autonomous decisions: 恢复时机是分支打开后首帧定位；保存时机是离开分支与列表滚动停止。
- User-confirmation decisions: 设备验证由用户装机实测（2026-09-21 决定）。
- Acceptance evidence: RED→GREEN 的 JVM 测试（进入恢复、离开保存、删除锚点后退化为活动末端且不崩、未读单调前进）、`:app` 单测与 `.\gradlew.bat check` 退出码 0、Java-only guard PASS、原文扫描证明 `ReadingStore` 有生产消费者。
- Facts: `branches` 的四个阅读位置列与两个未读序列列已在 schema 中并有约束；`SqliteReadingStore` 已实现；`ReadingSession` 已实现 `onViewport` 清未读。
- Assumptions: T-032 已把数据库与组合根接好。
- Unknowns: 真机上的滚动回调频率与定位观感（无设备）。
- Risks: 在 UI 线程做 IO；保存频率过高造成写放大；把未读清零写成可后退。
- Deferred: 真机滚动与像素证据。
- Non-goals: 不改未读的定义与派生规则；不做跨主题的最近阅读列表；不新增 schema。
- Skipped non-critical questions and reason: 定位精度（按条目 vs 按像素）沿用技术方案既有「语义阅读位置优先」的口径，不额外追问。

## Goal

对话页进入时恢复到上次的阅读位置、离开时保存它，并且视口扫过的稳定回答会把未读清掉（单调前进、不可后退）。

## Scope

### In scope
- `MainActivity` 的对话页生命周期接线：打开分支时恢复位置，离开分支时保存位置。
- `screen/ChatScreen` 的滚动位置上报（列表滚动停止时把位置交给 presenter/宿主），不在屏幕里读库。
- 读路径的返回结构补上位置（若 `ChatData.read` 目前不带，则在 T-032 的读结果上扩展一处，不做第二份查询）。
- 未读清算接到既有 `ReadingSession.onViewport`。
- JVM 测试覆盖四条：进入恢复、离开保存、锚点被删后退化为活动末端、未读单调前进。

### Out of scope
- 任何 schema 改动（列已存在）。
- 搜索跳转产生的定位（由搜索任务消费同一存储）。
- 主题级「最近阅读」列表。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 由「位置永不保存、未读永不清」变为「保存与恢复」；**保留** `branches` 既有 CHECK（`last_seen_stable_sequence <= latest_stable_sequence`）与 `ON DELETE SET NULL` 语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是对话页生命周期与滚动回调；消费者是 `SqliteReadingStore` 与 `ReadingSession`；宿主是 `MainActivity`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 阅读位置在对话页真实保存与恢复；未读在视口扫过后清零；`ReadingStore` 有了生产消费者。
- Authoritative code/config/migration evidence: `MainActivity.java`、`screen/ChatScreen.java`、`reading/**`、测试输出。
- Documentation surfaces checked: `android/README.md`（`:app` 入口索引、「没有任何 Activity 消费者」的表述）、Android 技术方案 §11、PRD 6.2.5/6.2.9。
- Documentation files updated: README 中「阅读位置无 Activity 消费者」的当前态表述改为真实接线。
- Producer/consumer/sibling/compatibility paths checked: 进入分支 → 恢复位置 → 滚动 → 离开保存 → 重进恢复。
- Superseded phrases/examples reverse-searched:「没有任何 Activity 消费者」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: T-015 的历史记录不改写，只补它的生产接线。

## Acceptance criteria

- [x] 同一 JVM 测试命令先 RED 后 GREEN：断言进入分支时用存储里的位置恢复、离开时写入位置、位置指向的消息被删后退化为活动末端且不抛异常、未读清算单调前进。RED `artifacts/evidence/T-042-red.txt`（EXITCODE=1）；GREEN `artifacts/evidence/T-042-green.txt`（EXITCODE=0）。七例落点：`ChatReadingTest` 的 `openingTheBranchRestoresTheStoredSemanticPosition`、`aBranchWithNothingStoredOpensAtTheLiveEnd`、`aStoredPositionWhoseMessageIsGoneDegeneratesWithoutCrashing`、`leavingTheBranchStoresThePositionItWasGiven`、`theNewestAnswerEnteringTheViewportClearsUnreadAndNeverMovesBackwards`、`anOlderAnswerAloneLeavesUnreadUntouched`、`thePositionIsSavedOnTheBackgroundExecutorAndNotOnTheUiThread`。
- [x] 保存路径不在 UI 线程：`thePositionIsSavedOnTheBackgroundExecutorAndNotOnTheUiThread` 断言 IO 发生在注入的 background executor 上；变异 M1（把 `ChatPresenter.onLeaving` 的 `background.execute` 改成内联调用）之后**只有该例**转红（1 failure），其余 6 例不受影响，证据 `artifacts/evidence/T-042-mutation-save-thread.txt`。
- [x] 原文扫描证明 `ReadingStore`/`ReadingSession` 在生产代码中有消费者（不再是只被测试引用），且没有第二份未读判定。证据 `artifacts/evidence/T-042-fix1-source-scan.txt（2026-09-22 修复轮重扫；首轮 `T-042-source-scan.txt` 的行号已被并行 T-040 lane 挪走）`：`app/src/main` 下 `reading/` 包外命中 `AppContainer.java:130`（构造 `SqliteReadingStore`）、`MainActivity.java:459`、`ChatPresenter.java:87`（全仓唯一 `new ReadingSession`）；未读写入只有 `BranchDao.update` 一条路径，判定只有 `ViewportSeen.onViewport` 一处。
- [x] `:app:testDebugUnitTest`、`.\gradlew.bat check`、Java-only guard、文档影响记录齐全且退出码 0。证据：`T-042-green.txt`（33 个测试类 / 341 例 / 0 failures / 0 errors，`check` EXITCODE=0）、`T-042-java-only.txt`（`VERDICT: PASS (13 OK line(s))`）。
- [x] 真机滚动与像素证据记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得写成 PASS。README 与交付报告两处均如此记录，任务文件 Deferred 段同。

- [x] Fix round 1 / D1：保存阅读位置与推进未读两条后台写路径都接住了 DAO 的失败关闭异常（`ReadingPositionDao.write` / `BranchDao.update` 的 `IllegalStateException`），后台线程不再把异常冒到进程；失败只渲染阅读位置自己的 notice 文案 `notice_reading_position`（不复用 `notice_storage` 的「输入已保留」措辞），既有可见状态不变。DAO 未改。证据 `artifacts/evidence/T-042-fix1-red-d1.txt`（RED，EXITCODE=1）、`T-042-fix1-green.txt`（GREEN，EXITCODE=0）、`T-042-fix1-mutation-d1.txt`（变异：把 `remember` 还原成裸调用后只有 `aRefusedStoredPositionBecomesANoticeInsteadOfKillingTheBackgroundThread` 转红，`restored-identical=TRUE`）。
- [x] Fix round 1 / D2：首帧布局完成后补一次可见集上报（`ChatActions.onRowsLaidOut(int, Set<String>)`，`ChatScreen.reportRowsLaidOut` 与 `reportScroll` 共用同一个 `visibleRows()` 几何判定）；一屏装得下且最新回答可见时首帧即清未读，最新回答不在可见集时不清；判定仍唯一走 `ViewportSeen`，没有第二个判定点、没有新计数器。证据 `T-042-fix1-red-d2.txt`（RED，EXITCODE=1，javac 报 5 处 `onRowsLaidOut` 类型不符）、`T-042-fix1-mutation-d2.txt`（变异：首帧改报空可见集后只有 `aConversationThatFitsOnOneScreenClearsUnreadOnItsFirstFrame` 转红）、`T-042-fix1-revert-green.txt`（还原后 11 例 0 失败）。
- [x] Fix round 1 / D3：任务正文的「退化为分支顶部」已按权威口径改为「退化为活动末端（最新一条）并标记 `atLiveEnd`」，实现未改（`reading/ReadingPosition.liveEnd`）。
- [x] Fix round 1 / D4：行号引用已按修复轮重扫 `artifacts/evidence/T-042-fix1-source-scan.txt` 的实测值刷新（`MainActivity.java:459`、`AppContainer.java:130`、`ChatPresenter.java:87`），Handoff 写明「引用的行号在证据之后被改过」。
- [x] Fix round 1 / D5：删掉无生产者的 `ChatData.Snapshot.of` 4 参重载（生产永远走 `ChatWiring.java:79` 的 6 参），调用形态同步到 `ChatTestDoubles.read` 与 `ChatPresenterTest`。
- [x] Fix round 1 收尾：重建 release APK（`523861` 字节、mtime `2026-09-22 05:13:06`，晚于最新改动源码 `05:05:32`），dex 内 `NoticeText` 3 / `READING_POSITION` 1 / `noticeReadingFailure` 2 / `visibleRows` 1 / `onRowsLaidOut` 3、`resources.arsc` 内 `notice_reading_position` 与 `chat_notice` 各 1，阴性对照 `ChatReadingTest` / `ChatPresenterTest` 均为 0；`apksigner verify --verbose --print-certs` 退出码 0、证书 SHA-256 `d8670c86…c561`。证据 `T-042-fix1-apk-build.txt` / `-apk-contains-wiring.txt` / `-apk-signature.txt`；设备侧仍 `deferred — 用户自行装机验证（user decision 2026-09-21）`。

## TDD execution record

### RED — failing test first
- Test/scenario: app/src/test/java/com/treelearn/app/ui/ChatReadingTest.java 的七个新 JVM 用例——进入分支恢复存储位置、无存储时落在对话末尾、锚点消息被删后退化为活动末端且不崩、离开时保存位置、最新回答进入视口才清未读且不可后退、只有旧回答时未读不动、保存发生在注入的 background executor 上而不是 UI 线程。
- Command: cd D:\Project\Android\Treelearn\android; .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatReadingTest"
- Exit code: 1
- Observed failure: `:app:compileDebugUnitTestJavaWithJavac` FAILED，javac 自报 13 个错误，全部是「错误: 找不到符号」：测试引用的 `ChatSink.renderPosition`、`ChatActions.onRowsLaidOut` / `onLeaving` / `onViewportChanged` 与 `ChatPresenter` 的第十个构造参数当时都还不存在。
- Failure reason validated: 每一条错误的成因都是「本任务尚未新增的生产缝不存在」，不是测试自身写错；实现补齐后同一命令一次全绿（见 GREEN）。原文 `artifacts/evidence/T-042-red.txt` 末尾为 `BUILD FAILED in 12s` 与 `EXITCODE=1`。

### GREEN — minimum implementation
- Implementation: 11 个既有生产文件的接线改动（本任务不新建生产文件）：`ui/ChatData`（Snapshot 增 branch/readingRows）、`ui/ChatSink`（renderPosition）、`ui/ChatActions`（三个新动作）、`ui/ChatPresenter`（第十参 ReadingStore + 内部 ReadingSession + 三个方法）、`screen/ChatScreen`（settle / applyPosition / reportRowsLaidOut / reportScroll / onDetachedFromWindow 上报）、`wiring/ChatStore`（Conversation 增 branch/messages）、`wiring/SqliteChatStore`（read 一并传出 branch/rows）、`wiring/ChatWiring`（Snapshot.of 传参）、`reading/SqliteReadingStore`（改持 helper、延迟开库避免 UI 线程 IO）、`AppContainer`（readingStore()）、`MainActivity`（唯一接缝 showChat(String, ReadingAnchor)）。
- Command: cd D:\Project\Android\Treelearn\android; .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatReadingTest"
- Exit code: 0
- Passing evidence: 同一命令两次都退出码 0——规范跑与加 `--rerun-tasks --no-build-cache` 的强制跑（后者证明任务真的执行过而不是 UP-TO-DATE）。回读 JUnit XML `app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.ChatReadingTest.xml` 得 `tests="7" skipped="0" failures="0" errors="0"`、`failure-elements: 0`，七个 `testcase` 名逐个列出。文件：`artifacts/evidence/T-042-green.txt`。
- Regression evidence: `.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest` 跑全量 33 个测试类 / 341 例 / 0 failures / 0 errors / 0 skipped，退出码 0；`.\gradlew.bat --no-daemon --console=plain check`（含 `:app:test`、`:app:lint`、`lintVitalReportRelease`）退出码 0。同文件。

### REFACTOR — optional
- Cleanup: 未做独立重构轮。三处变异（`ChatPresenter.onLeaving` 去掉 background.execute；`ReadingAnchor.resolve` 把 index<0 退化成 0；`ViewportSeen.onViewport` 去掉 unreadCount==0 守卫）都是一次性注入并已还原；`android/core` 在本工作树里是未跟踪文件，所以还原按内容核对而不是靠 git。
- Regression evidence: 还原后重跑规范命令与全量套件均为退出码 0，见 `artifacts/evidence/T-042-mutation-reverted.txt`；三份变异记录为 `T-042-mutation-save-thread.txt` / `T-042-mutation-gone-anchor.txt` / `T-042-mutation-unread-monotonic.txt`。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- 复用 `branches` 既有列，不得新增列或表；位置为空/失效一律退化到活动末端（最新一条）并标记 `atLiveEnd`（权威口径见 `reading/ReadingPosition.liveEnd`、`android/README.md` 与 T-015 验收），不抛异常给用户。
- 屏幕只上报滚动事实，读写库由宿主/presenter 经 `AppContainer.background()` 完成。
- 列表项高度按**首帧真实测量行高**取值（`ChatScreen.reportRowsLaidOut` 读 `row.root.getHeight()`），不写进 `dimens.xml` 做 token。2026-09-22 由协调方裁定：理由是不引入设计面数值且随字号自适应；代价是首帧布局完成前不做定位。实现者在 Handoff 中提出该选择并请求裁定。
- 不写任何正文（回答、问题）到日志或证据文件。

## Verification report

- Verdict: PARTIAL（独立 verifier，Fix round 1 复验，2026-09-22 05:1x；只判不实现。**上一轮的 3 个 PARTIAL 项里 D2/D3/D5 已闭合、D1/D4 各自残留一处**，故仍是 PARTIAL、状态留在 `verifying`。上一轮那份 PARTIAL 报告已由本轮报告取代；其结论与 D1–D5 原文保留在 `## Fix round 1 — 工头裁定` 段作为日期化历史。）
- Evidence: `artifacts/evidence/T-042-fix1-verify-scan.txt`（本轮独立静态扫描，203 行，头部 captured-at/cwd/command，逐节 `EXITCODE=` 与 `JUDGEMENT` 行；含 J1–J5 全部出处）。**本轮未跑 Gradle**：开工时实测有并行 Gradle（java pid 36912/42756，05:11:41 起），按约束不抢锁，构建类结论采信实现者自证证据（其头部/退出码/判据行齐全，见 J5）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本轮新增的四个用例正是 D1/D2 的回归锁，且我独立核过它们钉的等价行为——`ChatReadingTest` 的拒绝写入两例钉「不崩 + 只出 notice + 既有可见状态不变」（`:237-242`、`:257-260`），首帧两例钉「最新回答可见才清未读 / 不可见则不清」（`:189-190`、`:203-204`）。上一轮 `ViewportSeen` 单判定点的断言未被削弱。

### 时点关系（开判前实测）
- 任务文件 mtime `05:11:09`、`status: verifying`；13 份 `T-042-fix1-*` 证据 mtime `05:03:33`–`05:11:21`。
- `android/app/src/main` 最新 mtime `05:05:32`（`ui/ChatData.java`，即 D5 那一改）。**除两份最早的 RED（05:03:33 / 05:04:01，理应早于修复）外，全部证据都晚于最后一次源码改动**——D4 类的「证据早于源码」风险本轮**不成立**。
- 并行 Gradle：`java` pid 36912 / 42756（05:11:41 起）→ 未跑 Gradle。

### 逐条判据
1. **J1（D1：只改调用点、DAO 未被弱化）— PARTIAL。** ① DAO 未弱化：`ReadingPositionDao.write` 在所有分支仍 `throw new IllegalStateException`（`:96`，「affected N row(s)」），`BranchDao.update` 仍 `throw`（`:75`）——**没有任何静默返回**。② 点名的两条后台写路径已包：`ChatPresenter.remember`（`:433-438`，`onLeaving` 走它）与 `ChatPresenter.seen`（`:447-453`，`onViewportChanged` 走它），失败汇到 `noticeReadingFailure()`（`:457-458`）→ `state.withNotice(Notice.READING_POSITION)`。③ **新文案独立**：`strings.xml:88 notice_reading_position`＝「阅读进度没能保存，下次打开的位置与未读可能不准。」，与 `strings.xml:87 notice_storage`＝「本机写入失败，输入已保留，可以直接重试。」**资源名与措辞都不同**（你特别点出的那条：未复用）。④ 新用例真断言「不崩 + 只出 notice + 既有可见状态不变」（`ChatReadingTest:237-242`、`:257-260`）。**⑤ 残留缺陷见 F1。**
2. **J2（D2：首帧补报 + 唯一判定点 + README 约定）— PASS。** `ChatScreen.reportRowsLaidOut`（`:297-308`）读共用 `visibleRows()`（`:312`）并把可见集交给 `onRowsLaidOut(int, Set<String>)`（`ChatActions:90`、`ChatPresenter:372`）；presenter 在**同一** `session.onViewport` 上判定（`:380-387` 先 `session.open` 再 `seen`）。判定点唯一：`ViewportSeen.onViewport` 全仓**只有 `ReadingSession.java:63` 一处调用**，`ChatPresenter` 只在 javadoc 里提到它；**没有第二个计数器或第二处判定**。README 约定仍在（`android/README.md:533`「打开分支不清未读」），且被新用例钉住（`ChatReadingTest:203-204`：最新回答不在可见集 → `savedSeen` 保持 0）。
3. **J3（两处变异各自只打红目标用例 + 还原回绿）— PASS。** `-mutation-d1.txt`：`11 tests completed, 1 failed`，唯一失败者 `aRefusedStoredPositionBecomesANoticeInsteadOfKillingTheBackgroundThread`；`-mutation-d2.txt`：`11 tests completed, 1 failed`，唯一失败者 `aConversationThatFitsOnOneScreenClearsUnreadOnItsFirstFrame`；两份都记 `restored-identical=TRUE`（sha256 回到变异前）。`-revert-green.txt`：`tests=11 failures=0 errors=0`；`-gate-tests.txt`：`suite-totals: tests=358 failures+errors=0`。
4. **J4（D3/D4/D5）— PARTIAL。** D3 **PASS**：正文已改「活动末端（最新一条）并标记 `atLiveEnd`」（`:27`、`:33`、`:53`、`:81`、`:89`、`:121`），实现未动。D5 **PASS**：`ChatData.Snapshot.of` 只剩 6 参重载（`:82-90`），4 参已删；生产调用者 `ChatWiring.java:79`，测试侧 `ChatTestDoubles:64`、`ChatReadingTest:293` 都是 6 参形态。D4 **PARTIAL**：扫描已重跑（`T-042-fix1-source-scan.txt`，05:08:54，实测值 `MainActivity.java:445`/`:431`、`AppContainer.java:130`/`:199` 与今日源码一致），Handoff 也如实写了「引用的行号在证据之后被改过」；**但交付报告未刷新**，见 F2。
5. **J5（证据自证性）— PASS。** 13 份 `T-042-fix1-*.txt` 全部有 captured-at/cwd/command（多数另有 `mode:`）与 `EXITCODE=`，且都有判据行而不只是退出码；`T-042-fix1-gate-no-kotlin.txt` 明确带 `VERDICT: PASS (13 OK line(s))`（不是只有 OK 行）。`python -X utf8 .trae/scripts/validate_tasks.py` → `SUMMARY files=48 errors=0 warnings=15`。

### Findings
- **F1 [P2，残留缺陷，建议保留 `verifying`] — D1 的第三个写路径仍未兜底：`MainActivity.java:467`。** 我上一轮的 D1 原文点名了两个调用点：`MainActivity.java:467`（显式 anchor 写库，T-040 搜索跳转那道门）与 `ChatPresenter.java:383`（离开分支保存）。本轮修复**只包了 `ChatPresenter` 的两条**（`remember` / `seen`），`MainActivity.showChat(String, ReadingAnchor)` 里 `container.background().execute(() -> reading.saveAnchor(branchId, explicitAnchor));`（`:467`）**依旧没有 try/catch**；它经 `SqliteReadingStore.saveAnchor`（`:38`）直达 `ReadingPositionDao.write`，在分支行不存在时抛 `IllegalStateException`（`:96`），异常逃逸到后台线程，而全仓没有 `setDefaultUncaughtExceptionHandler` → **进程终止**，与 D1 被判定为缺陷的机制完全相同（`ChatPresenter:429-431` 的 javadoc 把这条理由写得很清楚，它同样适用于 467）。**建议**：用同一手法包住这一处（`try { reading.saveAnchor(...) } catch (RuntimeException refused) { /* 同一个 notice */ }`），并让宿主也有一条阅读位置 notice 通路。**理由**：修复口径应按「路径」而非「文件」收口，同一写路径的三个调用点只兜两个，缺陷只是从一处搬到另一处。**代价**：约 6–10 行 + 1 例测试；若判定该点触发窗口太窄（仅「搜索读到」与「点开」之间分支被删）而接受现状，请在裁定里写明「D1 只覆盖 ChatPresenter 两条路径，`MainActivity:467` 明确豁免」，否则后人会以为 D1 已全修。
- **F2 [P3，记录项残留] — D4 只做了一半：交付报告的行号未刷新。** `Docs/06-delivery/TreeLearn_V1_交付报告.md:107` 与 `:208` 仍写「唯一接缝 `MainActivity.showChat(String, ReadingAnchor)`（`MainActivity.java:383`）」，而扫描实测值是 **445**。D4 的裁定明确包含「任务文件**与交付报告**里的行号引用」。任务文件侧已处理（当前态引用见 `Fix round 1` 段与 Handoff 的行号披露，旧行号被标为日期化历史，符合 D4「不重写历史」的写法）。**建议**：把交付报告那两处 `:383` 改成 `:445`（或改为不带行号的签名引用，README 的做法）。**理由**：交付报告是装机者的入口文档，指到别处会误导。**代价**：两处文本，零代码。
- **F3 [观察，非缺陷] — `T-042-fix1-command-identity.txt` 是新增的第 13 份证据。** 它记 `IDENTICAL=TRUE`，用于证明 RED/GREEN 命令逐字符相同；本轮 GREEN 的 `Command` 与我回读的 RED `Command` 一致。记录在案，供后人理解证据集为何是 13 份而非 12 份。

### Gaps
- **G1** 未独立跑 Gradle：开工时已有并行 Gradle（java pid 36912/42756）持有构建目录，按约束不抢锁，避免互相覆盖 `app/build/test-results`。因此 J3 的「变异只打红目标用例」与 J5 的 gate 结论采信实现者证据（其自证完整），**没有**独立复跑或独立回读 JUnit XML。若需独立确认，请在无并行 lane 时排一次 `:app:testDebugUnitTest`。
- **G2** 设备侧一律 `deferred — 用户自行装机验证（user decision 2026-09-21）`（滚动观感、像素定位、输入法）；不计 PASS/FAIL。
- **G3** D4 的「行号」是时间敏感量：本轮引用的是 `T-042-fix1-source-scan.txt`（05:08:54）实测值；若并行 lane 再改 `MainActivity`/`AppContainer`，F2 修好之后仍可能再次漂移——这是流程存量，不是本任务的新缺陷。

### 覆盖边界
- 覆盖：D1（DAO 未弱化、两条路径兜底、新文案独立、新用例断言）、D2（首帧上报、唯一判定点、README 约定）、D3（措辞）、D4（扫描+披露）、D5（重载删除）、J5（证据自证）；全部为**静态源码 + 证据回读**。
- 未覆盖：设备侧运行观感与像素定位（deferred）；未独立复跑任何 Gradle 任务（G1）；未独立回读 JUnit XML。
## Handoff notes

- Completed: 实现与证据全部落盘——唯一接缝 `MainActivity.showChat(String, ReadingAnchor)`、`ChatScreen` 滚动事实上报、`AppContainer` 装配 `SqliteReadingStore` 与 `ReadingSession`、`ChatReadingTest` 七例 RED→GREEN、三次变异测试与还原、原文扫描、规模计量、README 与交付报告同步。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 交独立 verifier 判定；T-040 搜索页的跳转复用同一接缝 `showChat(branchId, explicitAnchor)`；真机装机后复核滚动观感与像素定位。
- Watch-outs: 不要新造第二套阅读位置存储；不要在 UI 线程做 IO；`android/core` 未跟踪，任何「还原」只能按内容核对；规模为归属下限 94 行（整文件上界 1904 判定 / 2580 非空物理行，含其它 lane 的在制品）。

- Fix round 1（2026-09-22，D1–D5）：D1 两条后台写路径包 try/catch + 新 notice `notice_reading_position`（DAO 未改）；D2 首帧补一次可见集上报（复用 `ViewportSeen` 的唯一判定，无新计数器）；D3 只改正文措辞为「活动末端 + `atLiveEnd`」；D4 行号已按重扫刷新；D5 删掉 4 参 `Snapshot.of` 重载。证据 `T-042-fix1-red-d1.txt` / `-red-d2.txt` / `-green.txt` / `-mutation-d1.txt` / `-mutation-d2.txt` / `-revert-green.txt` / `-source-scan.txt`。
- 行号披露（D4）：本文件与交付报告里引用的 `MainActivity.java` / `AppContainer.java` 行号在证据之后被改过——这两个文件曾被并行 T-040 lane 重写，行号以 `artifacts/evidence/T-042-fix1-source-scan.txt`（2026-09-22 修复轮扫描当时的实测值）为准；本文件 `Verification report` 与 `Fix round 1 — 工头裁定` 段里保留的旧行号是**日期化历史记录**，未改写。

## Fix round 1 — 工头裁定（2026-09-22）

独立 verifier（`artifacts/evidence/T-042-verify-scan.txt`）判 PARTIAL，提出 D1/D2 两个缺陷与 D3/D4/D5 三条记录项。工头裁定如下，本轮按此施工。裁定依据是本任务的**权威正文**（Requirement brief「保存失败只记可见 notice，不影响阅读」）与既有 README 约定，不是 verifier 的个人偏好。

- **D1（写路径无兜底）→ 修实现。** brief 的 State and exception handling 已承诺「保存失败只记可见 notice」，实现却让 `ReadingPositionDao.write` / `BranchDao.update` 的 `IllegalStateException` 冒到后台线程；全仓没有 `setDefaultUncaughtExceptionHandler`，等于**崩进程**。契约不改：DAO「写没写到就抛」是整个 data 模块一致的失败关闭设计，**不许为了让这里不抛而弱化 DAO**。改**调用点**——保存阅读位置与推进未读这两条后台写路径包 try/catch，失败时渲染一个可见 notice（`strings.xml` 的 `notice_storage` 是同形先例，但阅读位置要自己的文案，不要复用「输入已保留」的措辞）。新增用例：写在后台线程抛异常时**不崩**、只出 notice、既有可见状态不变。
- **D2（视口只在滚动停止时采样）→ 修实现。** 短对话、或恢复目标就是当前位置时，`settle()` 无位移、不触发 `onScrollChanged`，最新回答明明完整可见却不清未读，用户得手动蹭一下列表才清。在**首帧布局完成后**补一次可见集上报。**必须守住 `android/README.md` 的既有约定「打开分支不清未读」**：本次上报只有在最新回答确实落在可见集内时才推进，判定继续走 `ViewportSeen`，不得新增第二个判定点、不得新增计数器。新增用例：一屏装得下且最新回答可见时，未读在首帧后即被清除；最新回答不在可见集时未被清除。
- **D3（任务文件措辞与实测相反）→ 只改措辞，不改实现。** 实现跟的是权威 `ReadingPosition.liveEnd`＝**活动末端（最新一条）**，与 `android/README.md` 和 T-015 验收一致。正文三处「退化为分支顶部」是错的，就地改正为「退化为活动末端并标记 `atLiveEnd`」（不重写历史记录，只改当前态措辞）。
- **D4（被引用行号已失效）→ 本轮一并刷新。** `MainActivity.java`/`AppContainer.java` 已被并行 lane 改过，任务文件与交付报告里的行号引用（`MainActivity.java:383`、`MainActivity.java:395`、`AppContainer.java:119`）今天已指向别处。本轮修完重跑一次原文扫描，把行号引用刷新为**扫描当时**的实测值，并在 Handoff 如实写明「引用的行号在证据之后被改过」。
- **D5（只有测试调用的重载）→ 删。** `ChatData.Snapshot.of` 的 4 参重载在生产路径永不发生（生产永远是 `ChatWiring.java:79` 的 6 参）。按本仓失败案例库「发现无生产者时删除，而不是加注释保留」删掉它，并同步 `ChatTestDoubles` 与 `ChatPresenterTest` 的调用形态。

本轮完成标准：D1/D2 各有 RED→GREEN 的 JVM 用例；两处变异测试各自只打红目标用例、其余用例不受影响；`:app:testDebugUnitTest` 与 `.gradlew.bat check` 退出码 0；Java-only guard PASS；D3/D4/D5 处理完毕；README 与交付报告同步。完成后状态回到 `verifying`，交**原独立 verifier**复验（不复用实现者自评，也不由实现者自己写 verified）。

## TDD execution record — Fix round 1

### RED — failing test first (Fix round 1)
- Test/scenario: `app/src/test/java/com/treelearn/app/ui/ChatReadingTest.java` 新增四个 JVM 用例——D1 两条（`aRefusedStoredPositionBecomesANoticeInsteadOfKillingTheBackgroundThread`、`aRefusedUnreadAdvanceBecomesANoticeAndWritesNothing`：注入的 `FakeReadingStore` 在 `saveAnchor` / `saveSeen` 抛 `IllegalStateException`，断言不崩、只出一个 notice、既有可见状态不变）与 D2 两条（`aConversationThatFitsOnOneScreenClearsUnreadOnItsFirstFrame`、`aFirstFrameThatDoesNotShowTheNewestAnswerLeavesUnreadAlone`）。
- Command: cd D:\Project\Android\Treelearn\android; .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatReadingTest"
- Exit code: 1
- Observed failure: D1 的两条新用例把 `IllegalStateException` 冒到测试线程，证据回读得 `9 tests completed, 2 failed`（`ChatReadingTest.java:206` 与 `:226`）；同一命令的 D2 记录里 javac 自报 5 个错误，全部是「无法将类 ChatPresenter中的方法 onRowsLaidOut应用到给定类型」（首帧上报那时还只有行高一个参数）。
- Failure reason validated: 两条 RED 的成因都是「本修复轮要新增的生产兜底/参数尚未存在」，不是测试自身写错：D1 的异常正是未加 catch 的 `ReadingSession.remember/onViewport` 直接抛出；D2 是 `ChatActions.onRowsLaidOut` 尚未携带可见集。实现补齐后同一命令一次全绿（见 GREEN）。
- Evidence: `artifacts/evidence/T-042-fix1-red-d1.txt`（末行 `EXITCODE=1`）、`artifacts/evidence/T-042-fix1-red-d2.txt`（末行 `EXITCODE=1`）。
- Command identity: 与 GREEN 的 `Command` 逐字符相同，机制比对见 `artifacts/evidence/T-042-fix1-command-identity.txt`（`IDENTICAL=TRUE`）。

### GREEN — minimum implementation (Fix round 1)
- Implementation: `ui/ChatPresenter`（新增 `remember`/`seen`/`noticeReadingFailure`，`onLeaving` 与 `onViewportChanged` 改走它们；`onRowsLaidOut` 改为接 `(int, Set<String>)` 并在首帧先 `session.open` 再 `seen`）、`ui/HomeViewState`（`Notice.READING_POSITION`）、`ui/ChatActions`（`onRowsLaidOut` 第二参）、`ui/ChatData`（删 4 参 `Snapshot.of`）、`screen/ChatScreen`（`chat_notice` 视图 + `renderNotice` + 共用 `visibleRows()` 的 `reportRowsLaidOut`/`reportScroll`）、新文件 `screen/NoticeText`（`Notice` → 字符串资源的唯一映射，消掉两处 switch 的重复判定）、`res/values/strings.xml`（`notice_reading_position`）、`res/layout/screen_chat_body.xml`（notice 条）与测试侧 `ChatTestDoubles` / `ChatPresenterTest`。
- Command: cd D:\Project\Android\Treelearn\android; .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatReadingTest"
- Exit code: 0
- Passing evidence: 同一命令退出码 0，`BUILD SUCCESSFUL`；回读 JUnit XML `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.ChatReadingTest.xml` 得 `tests=11 failures=0 errors=0`（新增四例在列）。文件：`artifacts/evidence/T-042-fix1-green.txt`（末行 `EXITCODE=0`）与 `artifacts/evidence/T-042-fix1-revert-green.txt`（变异还原后的绿跑，带 `tests=11 failures=0 errors=0` 回读）。
- Regression evidence: `.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest` 全量退出码 0（`tests=358 failures+errors=0`，`artifacts/evidence/T-042-fix1-gate-tests.txt`）；`.\gradlew.bat --no-daemon --console=plain check` 退出码 0（`artifacts/evidence/T-042-fix1-gate-check.txt`）；Java-only guard `VERDICT: PASS (13 OK line(s))`（`artifacts/evidence/T-042-fix1-gate-no-kotlin.txt`；重建 APK 后复跑为 `T-042-fix1-gate-no-kotlin-2.txt`，同为 `VERDICT: PASS (13 OK line(s))`）。

### MUTATION — Fix round 1 regression locks
- D1 lock: 把 `ChatPresenter.remember` 还原成裸 `session.remember` 调用（缺陷形态）——`tests=11 failures=1`，**只有** `aRefusedStoredPositionBecomesANoticeInsteadOfKillingTheBackgroundThread` 转红，其余十例不受影响；`sha256 restored: 3ABAEE25…D609` 与变异前相等，`restored-identical=TRUE`。证据 `artifacts/evidence/T-042-fix1-mutation-d1.txt`。
- D2 lock: 把首帧上报的可见集换成 `Collections.<String>emptySet()`（缺陷形态）——`tests=11 failures=1`，**只有** `aConversationThatFitsOnOneScreenClearsUnreadOnItsFirstFrame` 转红，其余十例不受影响；`restored-identical=TRUE`。证据 `artifacts/evidence/T-042-fix1-mutation-d2.txt`。
- Restoration green: 两次变异均按 SHA-256 还原后重跑同一命令，`EXITCODE=0`、`tests=11 failures=0 errors=0`（`artifacts/evidence/T-042-fix1-revert-green.txt`）。
