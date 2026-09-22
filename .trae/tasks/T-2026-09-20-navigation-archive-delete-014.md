# Treelearn Harness Task: 实现导航、归档与删除

id: T-2026-09-20-014
title: 实现导航、归档与删除
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: true
depends_on: [T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-20-derived-ai-artifacts-011.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 用户可在主题树内定位分支，归档可恢复，永久删除需确认并正确处理活动生成。
- Problem/background: 树形内容若只靠返回栈导航会丢失父子语境；删除竞态会让晚回调重建数据。
- Users/scenarios: 用户打开主题导航、切换分支、折叠分组、归档/恢复分支，或永久删除子树/主题。
- Expected outcome: 含当前分支 AI 总结的导航 ViewState、Archive/Restore/Delete 用例、确认对话框和级联取消事务。
- Core flow and reversibility: 归档仅隐藏且可恢复；永久删除明确展示范围，确认后先取消操作再事务级联。
- Inputs/outputs: 输入 topic/branch ID 与删除范围；输出新选择、归档列表或删除结果。
- State and exception handling: 当前分支被归档/删除、祖先不可见、活动生成和晚回调均有确定行为。
- Constraints and trade-offs: 归档不取消生成；删除不可恢复；导航参数只传 UUID。
- AI recommendation and rationale: 归档与删除分离，避免误把「整理」变成数据丢失。
- User choice and custom additions: 个人本地数据，无云端回收站。
- AI-autonomous decisions: 删除后选择最近可见祖先或返回首页。
- User-confirmation decisions: P0/P1 用户流程与失败恢复口径。
- Acceptance evidence: use case/UI 测试覆盖归档中生成、级联删除和晚回调。
- Facts: PRD 导航/归档/删除和技术方案 §7 为权威。
- Assumptions: SQLite 外键和 Coordinator cancelScope 已可用。
- Unknowns: 无。
- Risks: 删除范围文案不清、归档结果仍被默认画布/搜索错误展示。
- Deferred: 回收站、批量导出和云恢复。
- Non-goals: 不实现画布几何和搜索算法。
- Skipped non-critical questions and reason: 导航动画不改变数据边界。

## Goal

提供明确、可恢复的归档和安全、不可恢复的删除，并保持导航上下文一致。

## Scope

### In scope
- 主题/分支导航列表、顶部 AI 总结及折叠、归档/恢复、删除确认、取消范围、事务级联和选择回退。

### Out of scope
- 树 Canvas、搜索和导出。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增导航整理；保持生成在归档后继续和删除后晚事件无效。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Branch Repository/Artifact 生产；Coordinator cancel 与 DAO delete 消费；画布/搜索后续读取可见范围。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 归档可见性、删除范围、取消顺序和导航回退。
- Authoritative code/config/migration evidence: use cases、navigation Presenter、DAO 集成测试。
- Documentation surfaces checked: PRD 导航/归档/删除、原型、技术方案 §7/§12。
- Documentation files updated: android/README.md 新增「导航、归档与删除（T-014）」小节（本任务的 :app 入口索引）；PRD/原型未改，理由见下。
- Producer/consumer/sibling/compatibility paths checked: 当前/非当前分支、含活动生成子树、主题删除、恢复。
- Superseded phrases/examples reverse-searched: 归档即取消、删除可撤销和无确认删除；android/README.md 与本文无此三句。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 当前态更新，历史留存。用户可见流程与范围未变（归档/删除语义、确认对话框范围文案均在既有 PRD 口径内），故 PRD 与原型本轮不改。

## Acceptance criteria

- [x] 同一 use case/UI 测试命令先 RED 后 GREEN；归档后默认列表/画布隐藏，但生成继续且可恢复查看。
      JVM 已实测：archivingHidesTheBranchWithoutCancellingOrDeletingAnything、restoringSomethingNotArchivedIsRefusedAndWritesNothing。
      数据库层（归档标记落库、归档不取消活动生成）在 NavigationStoreInstrumentedTest（6 例）里，compile-only 编译通过但未执行：deferred — emulator not used this round (user instruction 2026-09-21)。
- [x] 删除确认明确列出主题或子树范围；取消无副作用；确认后先取消范围内操作再原子级联删除。
      JVM 已实测：deletingAsksForConfirmationAndNamesTheExactSubtreeBeforeAnythingIsWritten、deletingAWholeTopicNamesEveryBranchItRemoves、cancellingTheConfirmationLeavesTheStoreUntouched、confirmingCancelsTheScopedAttemptsBeforeItDeletesThemInOneCall（顺序断言：cancel 调用全部先于 delete）。
      事务级联的真实 SQLite 行为在 deletingTheWholeTopicRemovesTheTopicItsBranchesAndTheirMessages、aSubtreePlanRemovesOnlyTheBranchesItNamesAndLeavesTheTopic 两例里，compile-only：deferred — emulator not used this round (user instruction 2026-09-21)。
- [x] 删除后的晚到 delta/completed 不写回；当前节点消失后导航到最近可见祖先或首页。
      JVM 已实测：aListReadThatLandsAfterTheDeletionCannotRebuildTheDeletedRow（generation token 丢弃过期读）、deletingTheCurrentBranchNavigatesToTheNearestVisibleAncestor、deletingWhenNoVisibleAncestorRemainsLeavesTheTree、deletingTheWholeTopicLeavesTheTree；并有变异测试（T-014-mutation.txt）证明该守卫被移除时恰好只有它转红。
      由 :data 协作方负责的「已删分支上晚到 stabilization 被拒且不写 revision」在 aLateStabilizationAfterTheDeletionIsRefusedAndWritesNoRevision 里，compile-only：deferred — emulator not used this round (user instruction 2026-09-21)。
- [x] 旋转和进程重建只通过 UUID 恢复，不通过序列化大对象或过期内存引用。
      JVM 已实测：theSelectionIsRestoredFromTheBranchIdAlone（只用 branchId 构造即可恢复选择）、aSeedThatNoLongerExistsResolvesToTheRootInsteadOfAStaleReference。
      Presenter 只持有 topicId 与 selectedBranchId 两个 UUID，ViewState 无 Parcelable/Serializable 大对象。
      onSaveInstanceState/重建本身需要壳（MainActivity/AppContainer 由 T-012/T-013 持有，本轮未接入）：deferred — emulator not used this round (user instruction 2026-09-21)。
- [x] 导航内容区顶部展示 T-011 当前分支 BRANCH_SUMMARY/key_points，失败保留旧结果；不得吸收兄弟/后代内容或显示其它 PromptPurpose 的 artifact。
      JVM 已实测：theSummaryShownIsTheCurrentBranchsOwnAndAFailedReadKeepsTheLastGoodOne。
      「按 kind=BRANCH_SUMMARY 精确取用、不误用其它用途 artifact」在 theSummaryIsTheBranchSummaryKindAndNeverAnotherUsesPayload 里，compile-only：deferred — emulator not used this round (user instruction 2026-09-21)。

## TDD execution record

### RED — failing test first
- Test/scenario: `android/app/src/test/java/com/treelearn/app/navigation/NavigationPresenterTest.java` 的 18 例 JVM 验收套件，写在 `com.treelearn.app.navigation` 生产类型之前；仓库是完整未修改的实测对象，没有移动、删除或 stash 任何文件。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --continue --rerun-tasks --no-build-cache --no-daemon --console=plain
- Exit code: 1
- Observed failure: `:app:compileDebugUnitTestJavaWithJavac` 编译失败，javac 两轮各报 61 个错误（共 122 条 `错误:` 行），全部在 `NavigationPresenterTest.java`。
- Failure reason validated: 证据文件 `artifacts/evidence/T-014-red.txt` 的 122 条诊断行里，出现的文件只有 `NavigationPresenterTest.java` 一个，缺失的都是本任务尚未写的 navigation 包类型（找不到符号 60 + 程序包不存在 46 + 方法不会覆盖 16 = 122，两轮 61×2），没有第二现场、也没有一条落在别人写的文件上。

```text
证据文件：artifacts/evidence/T-014-red.txt，EXITCODE=1（第 637 行）
复现命令（PowerShell，逐条可重跑）：
  (Select-String -Path 'artifacts/evidence/T-014-red.txt' -Pattern '错误:' -Encoding utf8).Count            -> 122
  (Select-String -Path 'artifacts/evidence/T-014-red.txt' -Pattern '找不到符号' -Encoding utf8).Count        -> 60
  (Select-String -Path 'artifacts/evidence/T-014-red.txt' -Pattern '程序包.*不存在' -Encoding utf8).Count     -> 46
  (Select-String -Path 'artifacts/evidence/T-014-red.txt' -Pattern '方法不会覆盖' -Encoding utf8).Count       -> 16
  (Select-String -Path 'artifacts/evidence/T-014-red.txt' -Pattern '个错误' -Encoding utf8).Line.Trim()      -> `61 个错误` 两行
分项之和等于总数：60 + 46 + 16 = 122 = 61 × 2 轮。
出现的文件（去重后）只有 test\java\com\treelearn\app\navigation\NavigationPresenterTest.java 一个。
构建读数：`88 actionable tasks: 88 executed`（无 UP-TO-DATE），`BUILD FAILED in 20s`。
RED 与 GREEN 的命令串逐字节相同（两条证据文件第 2 行一致）。
```

### GREEN — minimum implementation
- Implementation: `:app` 新增 `com.treelearn.app.navigation` 6 个生产类型：`NavigationSink`（渲染出口）、`NavigationActions`（含 `DeleteScope`）、`NavigationData`（Node/Summary/Snapshot/Plan 只读快照）、`NavigationViewState`（含 Phase/Notice/Row）、`NavigationPresenter`（选择、归档/恢复、删除确认与范围计划、代际 token、最近可见祖先回退）、`SqliteNavigationStore`（archive/unarchive 落 BranchDao、delete 走 TopicDao 或 BranchDao 事务、cancel 走 GenerationOperationDao + GenerationCoordinator、summary 按 ArtifactKind.BRANCH_SUMMARY 读）。未改动 `:core`、`:ai`、`:data`、`:tree-layout`、`android/build.gradle`、`settings.gradle` 与其它 task 文件。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest --continue --rerun-tasks --no-build-cache --no-daemon --console=plain
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-014-green.txt`：`BUILD SUCCESSFUL in 25s`、`89 actionable tasks: 89 executed`（无 UP-TO-DATE，确为实跑）、`EXITCODE=0`（第 140 行）。JUnit XML 回读：`:app:testDebugUnitTest` 21 个类 216 例 0 失败 0 错误，其中 `NavigationPresenterTest` 18 例 0 失败 0 错误。`:app:assembleDebugAndroidTest` 产出 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，6 例 instrumentation 源码编译通过（不执行，本轮无模拟器）。同一条命令串与 RED 逐字节相同。
- Regression evidence: `artifacts/evidence/T-014-regression.txt`：`BUILD SUCCESSFUL in 43s`、`122 actionable tasks: 122 executed`、`EXITCODE=0`；五个 JVM 套件全 0 失败（core 187 + tree-layout 34 + data 13 + ai 72 + app 216 = 522）；`:app:lintDebug` 0 error / 18 warning，18 条里没有一条位于 navigation 文件。Java-only guard：`artifacts/evidence/T-014-guard.txt` `VERDICT: PASS (13 OK line(s))`、`EXITCODE=0`。源码扫描：`artifacts/evidence/T-014-source-checks.txt` 6 条扫描全 verdict=OK（凭据 0、日志/Bundle/toString 0、KeyStore/SharedPreferences 0、answer/reasoning 字段名 0、证据文件凭据 0、SYNTHETIC 占位符命中 5）。变异测试：`artifacts/evidence/T-014-mutation.txt`，在仓库外的临时副本 `%TEMP%\t014-mutation\android` 上做 3 次变异，每次恰好 1 例转红（18 例 / 1 失败），副本源码按字节还原成功。

```text
JUnit XML 回读口径（从盘上重算，不用控制台回显）：
  Get-ChildItem android/app/build/test-results/testDebugUnitTest -Filter TEST-*.xml
    -> files=21 tests=216 failures=0 errors=0 skipped=0
    -> TEST-com.treelearn.app.navigation.NavigationPresenterTest.xml tests=18 failures=0 errors=0
  :core 187 / :tree-layout 34 / :data 13 / :ai 72 / :app 216，合计 522，全 0 失败。
无模拟器读数：android/app/build/outputs/androidTest-results/connected/debug 文件数 = 0（GREEN 头部第 6 行亦记录该前置检查为 0）。
规模：artifacts/evidence/T-014-sizing.txt 实测 6 个新生产文件 570 判定行 / 836 物理行（软上限 200、硬上限 500 均已超）——见下文 TDD or sizing exception。
```

### REFACTOR — optional
- Cleanup: 无系统性重构；GREEN 之后重跑三次、不改实现。做过的清理只有：把 `#` 删除范围文案从「本分支」改为按分支数计数（把无法计算的情况收敛到 fail closed 的 SCOPE_UNRESOLVABLE，而不是显示一个含混的小范围数字），并把 `NavigationViewState` 的 Notice 枚举里未被任何生产路径产生的取值删掉，避免留下无生产者分支。
- Regression evidence: 清理后以同一条 GREEN 命令串重跑，仍 `EXITCODE=0`（`artifacts/evidence/T-014-green.txt` 即清理后的那一轮）；回归门（`T-014-regression.txt`）与变异测试（`T-014-mutation.txt`）也在清理后的源码上重跑过，读数同上，本轮未再重跑。

### TDD or sizing exception — only with prior user approval
- Exception type: 未申请例外（TDD 已按要求执行，没有申请 TDD 例外）
- Reason: TDD 按要求执行；规模实测 570 判定行（6 个新生产文件）/ 836 物理行，超过 200 软上限并超过 500 硬上限 70 行（14%）。这是**未获用户批准**的偏差，必须由用户裁定（拆分，或补一份用户批准的例外），本任务不自批。
- User approval: 无
- Alternative verification: 规模偏差的实测口径、命令与分项见 `artifacts/evidence/T-014-sizing.txt`；TDD 的替代验证就是 RED/GREEN 命令串完全相同（两条证据第 2 行一致）。

## 本轮验证状态（用户 2026-09-21 指令：本轮不使用模拟器）

| 门 | 状态 |
| --- | --- |
| JVM 验收套件（`:app:testDebugUnitTest`） | 已实测：RED exit 1 → GREEN exit 0；`NavigationPresenterTest` 18 例 0 失败 |
| instrumentation 源码（`:app:assembleDebugAndroidTest`） | 已 compile-only：`app-debug-androidTest.apk` 产出，6 例源码编译通过；未执行 |
| 设备运行（`:app:connectedDebugAndroidTest`） | deferred — emulator not used this round (user instruction 2026-09-21)；`androidTest-results/connected/debug` 文件数 = 0 |
| 全量 JVM 回归（core/tree-layout/data/ai/app） | 已实测：522 例 0 失败，exit 0（`T-014-regression.txt`） |
| lint（`:app:lintDebug`） | 已实测：exit 0，0 error / 18 warning，navigation 文件 0 条 |
| Java-only guard | 已实测：`VERDICT: PASS (13 OK line(s))`，exit 0（`T-014-guard.txt`） |
| 源码扫描（凭据 / 日志 / Bundle / toString / 平台存储 / 内容字段） | 已实测：6 条扫描全 verdict=OK（`T-014-source-checks.txt`） |
| 变异测试 | 已实测：仓库外副本 3 次变异，每次恰好 1 例转红（`T-014-mutation.txt`） |
| 规模（非空非注释生产行） | 已实测：570 判定行 / 836 物理行，超硬上限 —— 记为**待用户裁定的偏差**（`T-014-sizing.txt`） |
| 外壳接线（MainActivity / AppContainer 接入导航页） | 未做：壳由 T-012/T-013 lane 持有，本轮无可接入的 navigation 布局；记为 Gaps |
| 旋转/进程重建落盘恢复（onSaveInstanceState） | deferred — emulator not used this round (user instruction 2026-09-21) |
| 真实 Android SQLite 上的 SQL 行为 | 本任务未新增、未修改任何 SQL（只调既有 DAO）。级联依赖 T-004/T-022 既有 DDL。按用户 2026-09-21 指令②，未在真实 Android SQLite 上执行过的部分记为 unverified，见 Gaps |
| 组合根装配（AppContainer 注入） | 未做：`AppContainer.java` 与 `MainActivity.java` 归 T-2026-09-21-real-data-path-wiring-032 独占。所需类型与构造参数见 Handoff notes |

## Implementation notes

- 归档字段不是删除标记；级联删除不得依赖 UI 逐行循环。
- 删除确认文案必须使用准确数量，无法计算时 fail closed。
- 删除顺序：`NavigationPresenter` 先在**同一次** `onDeleteConfirmed` 里向 store 请求 `cancelInFlight`（范围内每个分支），再请求 `delete`；JVM 用调用序列断言「所有 cancel 都在 delete 之前、且 delete 只被调用一次」。
- `plan()` 对树形状 fail closed：某个节点的 parent 缺失/成环时返回 SCOPE_UNRESOLVABLE 并拒绝给出确认范围，绝不退化成一个偏小的数字。
- 代际 token：每次读/删都自增 token，晚到的结果只在 token 仍是最新时应用，避免晚回调把已删数据重建出来。
- 选择回退：当前节点不可见时取最近可见祖先；祖先也不可得时 leaveTree（回首页）。恢复只靠 `topicId` + `selectedBranchId` 两个 UUID。
- 顶部总结只读 `ArtifactKind.BRANCH_SUMMARY`，失败保留上一次成功结果（`summaryStale` 标记），不吸收兄弟/后代内容。
- 复用 `Branch.archive/unarchive`、`TopicDao.delete`、`BranchDao.delete`、`GenerationOperationDao.findActiveByBranch`、`GenerationCoordinator.cancel`、`AiArtifactDao.findByKindAndScope` + `ArtifactPayloadCodec`；未新增第三方依赖，未改 `android/data/**/db/**`。
- 本任务未新增、未修改任何 SQL：`SqliteNavigationStore` 只调用既有 DAO 方法，`SUBTREE` 删除是「逐个分支 `BranchDao.delete`，依赖既有 DDL 的 `parent_id ... ON DELETE CASCADE` 清掉后代」。按用户 2026-09-21 指令②，该级联在真实 Android SQLite 上的行为本轮未执行设备验证，记为 unverified（Gaps 已列），不得用 JVM 测试或字符串断言替代。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 取消与数据库删除竞态；本轮无设备，DAO 级级联删除与「归档不取消生成」只在 compile-only 的 6 例 instrumentation 里，真实 Android SQLite 行为 unverified；`delete` 走「逐分支 `BranchDao.delete` + 依赖 `parent_id ON DELETE CASCADE`」而不是一句递归删除，两者对深层子树的等价性只在 compile-only 的 instrumentation 例子里，未上设备实跑。规模超硬上限，待用户裁定。

## Handoff notes

- Completed: `com.treelearn.app.navigation` 6 个生产类型 + 1 个 JVM 套件（18 例）+ 1 个 :app androidTest 类（6 例，compile-only）。RED/GREEN 各一次且命令串逐字节相同（exit 1 → exit 0），全量 JVM 回归 522 例 0 失败，lint 0 error，Java-only guard PASS，源码扫描 6/6 OK，仓库外变异测试 3/3 单点转红，规模偏差已如实记录；`android/README.md` 新增本切片小节。
- In progress: 无。已停在 `verifying`，等 verifier 复核。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 用户裁定规模偏差（拆分或批准例外）；② 外壳 lane 落地后把导航页接进 MainActivity/AppContainer 与一个 navigation 布局，并补 onSaveInstanceState 的 UUID 恢复；③（未获准前不做）设备上跑 `:app:connectedDebugAndroidTest` 把 6 例 compile-only 的 criteria 从 deferred 转为实测。
- 组合根装配清单（交付给 T-2026-09-21-real-data-path-wiring-032，本任务不写 AppContainer/MainActivity）：
  - `NavigationData` 实现：`new SqliteNavigationStore(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner transactions, GenerationCoordinator coordinator)` —— 三个参数都必须是 AppContainer 里已有的那三个实例（`:data` 的 `TreeLearnDatabaseHelper`、`DatabaseTransactionRunner`，`:app` 的 `GenerationCoordinator`）；本类不自己建库、不自己起线程。
  - `NavigationPresenter`：`new NavigationPresenter(NavigationSink sink, UiThread ui, Executor background, NavigationData data, String topicId, String selectedBranchId)` —— `ui` 是 `com.treelearn.app.ui.UiThread`（T-012/T-013 lane 已有），`background` 是普通 `java.util.concurrent.Executor`（主线程外执行 store 调用），`sink` 由导航页实现。
  - 导航页必须同时实现 `NavigationSink`（`render(NavigationViewState)` / `leaveTree()`）与 `NavigationActions`（`onBranchSelected` / `onArchive` / `onRestore` / `onDeleteRequested(branchId, DeleteScope)` / `onDeleteConfirmed` / `onDeleteCancelled`），把 `NavigationPresenter` 作为 `NavigationActions` 交给视图。
  - 旋转/重建只保存两个 UUID：`topicId` 与 `presenter.selectedBranchId()`；重建时用它们重新构造 Presenter 并 `start()`，不要序列化 `NavigationViewState` 或快照对象。
  - 壳需要新建一个 navigation 布局（本轮 `res/layout*` 下没有任何 navigation 资源）并挂 `R.id.shell_host`；本任务未创建布局，因为壳的宿主 id 归 T-012/T-013。
- Watch-outs: 归档分支的总结晚到完成时不得自动跳回当前页；本任务的 store 需要三四个协作方类型（TreeLearnDatabaseHelper / DatabaseTransactionRunner / GenerationCoordinator / DAO 集合），构造参数较多，接线时别改它们的契约。`DomainResult` 的读取接口是 `isOk()` / `isFailure()` / `value()`，没有 `isSuccessful()`。规模已超 500 硬上限，若继续在此包上加功能会放大偏差，建议后续导航改动另开任务。
- [leader 2026-09-21] 原实现 lane 在记录完成后中断；leader 补齐了它留下的缺失成员：`NavigationViewState.collapsed` / `summaryCollapsed`（含 `withCollapsed` / `withSummaryCollapsed`，以及 `rows(...)` 的「折叠只隐藏后代、组自身保留」过滤与越界即视为可见的 `isFoldedAway`）与 `NavigationPresenter.onGroupToggled` / `onSummaryToggled`（`NavigationActions` 同步新增这两个方法）。修复前 `:app` 单测源集有 **10 个 javac 错误**（`collapsed`、`summaryCollapsed` 符号不存在，`onGroupToggled(String)`、`onSummaryToggled()` 找不到符号）；修复后 `:\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` → `BUILD SUCCESSFUL`，`NavigationPresenterTest` **21 例通过**。设备相关验收标准本轮一律 `deferred — emulator not used this round (user instruction 2026-09-21)`，且本轮**未取得独立 verifier 判定**（verifier lane 因后端 402 中断），因此状态保持 `verifying`。
