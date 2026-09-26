# Treelearn Harness Task: 树画布生产装配与入口

id: T-2026-09-21-038
title: 树画布生产装配与入口
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 240
shared_code_touched: true
depends_on: [T-2026-09-21-real-data-path-wiring-032.md, T-2026-09-20-tree-canvas-tablet-ui-017.md, T-2026-09-20-pure-java-tree-layout-016.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD 6.2.7（树状总览画布）要求用户能一眼看到整棵主题树的形状、未读与折叠状态，并点节点进入分支；代码已把画布画完，但**没有任何地方构造它**，因此运行期不可达。
- Problem/background: `screen/TreeCanvasScreen.java`、`screen/TreeCanvasView.java`、`ui/TreeCanvasPresenter.java`、`ui/TreeCanvasViewState.java` 与 `res/layout/screen_tree*.xml` 都已交付，且 `new TreeCanvasScreen` 在整个仓库里只出现在它自己的文件里；`ui/TreeCanvasData` 与 `ui/TreeCanvasCommands` 两个端口只有测试替身，没有生产实现。结果：画布是一段被完整测试却永远不会被执行的代码。
- Users/scenarios: 用户在某个主题里想先看清整体结构再决定读哪条分支；点总览里的节点直接进入该分支对话；在画布上折叠/展开一条枝干；未读的芽有明显标识。
- Expected outcome: 画布有生产数据源与命令实现，能从对话页进入、能返回，节点点击进入分支，折叠状态落库。
- Core flow and reversibility: 打开画布读取该主题的全部非归档枝干与派生摘要/轮次；折叠是唯一写操作，可逆（再点一次展开）。离开画布不改任何数据。
- Inputs/outputs: 输入 `TreeLearnDatabaseHelper` 打开的库、`BranchDao`、`MessageDao`/`AnswerRevisionDao` 与 `SqliteDerivedStore` 的摘要；输出 `TreeCanvasData`/`TreeCanvasCommands` 的生产实现、`AppContainer` 的装配与 `MainActivity` 的宿主与入口。
- State and exception handling: 读取失败在画布上给出可见 `HomeViewState.Notice`，不白屏；无枝干时走空状态；根分支必须出现且不可归档。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；画布几何由 `android/tree-layout` 纯 Java 计算，屏幕只画；手机竖屏与平板横屏共用同一数据路径（`res/layout-sw600dp/` 已有镜像布局）。
- AI recommendation and rationale: 把 `TreeCanvasData.load` 实现成「一次读该主题全部枝干 + 每个枝干的摘要首行与已提交轮次」，而不是让屏幕多次往返查询——几何需要全量才能算布局，分批读会让画面在补数据时跳动。
- User choice and custom additions: 用户 2026-09-21 明确要求树画布必须实现且可用。
- AI-autonomous decisions: 入口放在对话页（从当前分支回到总览是最自然的动线），不新增首页入口。
- User-confirmation decisions: 设备验证由用户装机实测（2026-09-21 决定）。
- Acceptance evidence: 同一命令 RED→GREEN 的 JVM 测试（含折叠落库与归档枝干不进画布）、`:app` 单测与 `.\gradlew.bat check` 退出码 0、Java-only guard PASS、原文扫描证明 `TreeCanvasScreen` 有生产构造点。
- Facts: 画布视图与 presenter 已交付并被单测覆盖；`ui/TreeCanvasData` 只有 `topicOf` 与 `load`；`ui/TreeCanvasCommands` 只有 `setCollapsed`；`TreeCanvasScreen` 预留了 `navigationHost()`。
- Assumptions: T-032 已把数据库与组合根接好，`BranchDao` 与派生摘要存储可被生产路径读取。
- Unknowns: 真实设备上的画布触控与像素观感（无设备）。
- Risks: 把折叠写成两次判定点（屏幕与 presenter 各一次）；大主题一次性布局的耗时未在真机测过。
- Deferred: 真机触控、平板横屏实机、像素级证据。
- Non-goals: 不改树几何算法；不在画布上做归档/删除；不做新的画布交互（缩放/平移已由 T-017 交付）。
- Skipped non-critical questions and reason: 入口按钮的具体图标与文案按现有 `strings.xml` 命名约定补齐，不改变范围。

## Goal

树画布在生产装配中有真实数据源与命令实现，用户能从对话页进入、在画布上折叠枝干、点节点进入分支、再返回。

## Scope

### In scope
- `android/app/src/main/java/com/treelearn/app/wiring/` 下新增 `TreeCanvasData` 与 `TreeCanvasCommands` 的生产实现（读主题全部枝干 + 摘要首行 + 已提交轮次；折叠落库）。
- `AppContainer` 暴露这两个端口；`MainActivity` 作为 `TreeCanvasSink` 的宿主，负责进入/离开画布与打开分支。
- 对话页新增「总览」入口；`ChatActions`/`ChatPresenter` 增加对应动作（仅导航，无数据决策）。
- 折叠状态的落库路径复用既有 `branches.collapsed` 列，不新增 schema。
- `strings.xml` 补齐入口/返回所需文案；`res/layout-sw600dp/` 镜像布局如需同步则同步。

### Out of scope
- 画布绘制、命中测试、缩放平移（T-017 已交付）。
- 归档/删除与搜索（各自的屏幕任务）。
- 任何 schema 改动。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 由「无人构造画布」变为「对话页可进入」；**保留** `TreeCanvasPresenter`/`TreeCanvasViewState` 的既有语义与 T-017 的几何输出不变，画布显示的分支集合必须与导航页的「非归档」口径一致。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是新的 `TreeCanvasData`/`TreeCanvasCommands`；消费者是 `TreeCanvasPresenter`；宿主是 `MainActivity`。手机/平板分支沿用 `res/layout-sw600dp/`。
- Native bridge or independent-client compatibility/update requirements: Android Canvas/Matrix 是平台边界，不引入原生桥。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 画布从不可达变为可达；新增两个生产实现与一个对话页入口；折叠状态经真实库读写。
- Authoritative code/config/migration evidence: `app/src/main/java/com/treelearn/app/wiring/**`、`AppContainer.java`、`MainActivity.java`、`screen/TreeCanvasScreen.java`、测试输出。
- Documentation surfaces checked: `android/README.md`（`:app` 入口索引与「记录缺口」小节）、Android 技术方案 §9、PRD 6.2.7。
- Documentation files updated: README 中「画布无构造点」的当前态表述改为真实入口与数据来源。
- Producer/consumer/sibling/compatibility paths checked: 对话页 → 画布 → 分支对话 → 返回；折叠 → 重开读回。
- Superseded phrases/examples reverse-searched:「画布无任何构造点」「树画布不可达」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: T-017 的历史记录不改写，只补它的生产装配。

## Acceptance criteria

- [x] 同一 JVM 测试命令先 RED 后 GREEN：断言 `TreeCanvasData.load` 返回该主题的非归档枝干与其摘要/轮次，且归档枝干不出现；断言 `setCollapsed` 落库并可读回；断言失败读取产生可见 notice。证据：`artifacts/evidence/T-038-red-jvm.txt`（EXITCODE=1）/ `T-038-green-jvm.txt`（EXITCODE=0）。
- [x] 原文扫描证明 `TreeCanvasScreen` 在生产代码中有构造点，且该构造点位于 `MainActivity`（不是测试）。证据：`artifacts/evidence/T-038-source-scan.txt` 的 A/B 两节；第 1 轮改过 `src/main` 后重扫的 `T-038-r1-source-scan.txt` 同为 PASS（A/B 结果一致，E 节行号随该文件变动到 92/102）。
- [x] 折叠只有一个判定点：屏幕只转发手势，落库决定在命令实现里；原文扫描证明屏幕未直接写库。证据：同一扫描的 D/E/E2 三节（屏幕文件里 0 处数据库访问、0 处折叠）；重扫 `T-038-r1-source-scan.txt` 的 D 节仍 lines=0 occurrences=0、E2 仍 0，D+ 正控仍 lines=60 occurrences=68。
- [x] `:app:testDebugUnitTest`、`.\gradlew.bat check`、Java-only guard、文档影响记录齐全且退出码 0。证据：`T-038-app-unit-tests.txt` / `T-038-check.txt` / `T-038-no-kotlin-guard.txt`，三份都 EXITCODE=0；README 的「画布无构造点/不可达」表述已改（反向检索 0 命中；证据 `T-038-readme-rescan.txt`，三条旧措辞各 0 命中、替换小节恰好 1 命中，EXITCODE=0）。第 1 轮的免缓存重跑：`T-038-r1-jvm-treecanvas-uncached.txt`（54 actionable tasks: 54 executed；tests=9 failures=0）、`T-038-r1-app-unit-tests.txt`（54 executed；files=28 tests=302 failures=0）、`T-038-r1-check.txt`（295 executed；files=100 tests=1031 failures=0）、`T-038-r1-no-kotlin-guard.txt`（VERDICT: PASS (13 OK line(s))）、`T-038-r1-androidtest-compile.txt`（85 executed），五份都 EXITCODE=0。
- [x] `SqliteTreeCanvasStore` 的四个读与一个写有 instrumentation 用例覆盖且该套件编译通过。证据：`artifacts/evidence/T-038-r1-androidtest-compile.txt`（`:app:assembleDebugAndroidTest` 85 actionable tasks: 85 executed，EXITCODE=0；androidTest APK 与 `TreeCanvasStoreInstrumentedTest.class` 均为本轮新产物）。执行状态 `compiled, never executed - deferred 用户自行装机验证（user decision 2026-09-21）`——本项只证明「能编译」，不证明 SQL 在设备上跑通。
- [x] 真机上的触控/像素/平板横屏证据记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得写成 PASS。本任务文件与 `android/README.md` 都只以 deferred 记录，未出现 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: com.treelearn.app.wiring.TreeCanvasWiringTest 的 9 条 JVM 用例——load(topicId) 返回该主题的非归档枝干与每条的摘要首行和已提交轮次、归档枝干不出现、setCollapsed 落库并可读回、读取失败时 TreeCanvasPresenter 给出可见 notice（原文见 artifacts/evidence/T-038-red-jvm.txt）
- Command: .\gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.wiring.TreeCanvasWiringTest" --no-daemon --console=plain
- Exit code: 1
- Observed failure: :app:compileDebugUnitTestJavaWithJavac 报 8 个错误——错误: 找不到符号 ×3（符号: 类 TreeCanvasStore ×1、符号: 类 TreeCanvasWiring ×2）加 错误: 方法不会覆盖或实现超类型的方法 ×5 = 8；同一份 8 条在证据文件里出现两次（任务内联报告与 What went wrong 汇总），按行数计数会读到 16，故以每份 8 条为准
- Failure reason validated: 8 条逐条指向本任务尚未写入的生产类型（TreeCanvasStore/TreeCanvasWiring）与测试替身因此失去超类型的 @Override；RED 运行时 :app:compileDebugJavaWithJavac 是 UP-TO-DATE（主源集当时尚未引用画布实现），失败只发生在测试源集编译，没有一条是测试自身的缺陷

RED 的两份报告逐条是：TreeCanvasWiringTest.java:213 与 :60 两次「找不到符号」（TreeCanvasStore ×1、TreeCanvasWiring ×2），以及 :241/:252/:258/:264/:271 五次「方法不会覆盖或实现超类型的方法」。计数命令与上述 8 条的来源都是 artifacts/evidence/T-038-red-jvm.txt 本身（EXITCODE=1 在该文件最后一行）。

### GREEN — minimum implementation
- Implementation: wiring/TreeCanvasStore.java（端口：四个读与一个写）、wiring/TreeCanvasWiring.java（读路径与画布仅有的两条判定：归档不进画布、摘要取首行）、wiring/SqliteTreeCanvasStore.java（BranchDao/AiArtifactDao/MessageDao 的读与折叠落库）、AppContainer 的 treeCanvasData()/treeCanvasCommands() 两个访问器、MainActivity 的 showTree/leaveTree/openOverview/render(TreeCanvasViewState)、ChatActions.onOverviewPressed() 与 ChatSink.openOverview(String) 及 ChatPresenter/ChatScreen 的 chat_overview 入口
- Command: .\gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.wiring.TreeCanvasWiringTest" --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-038-green-jvm.txt —— BUILD SUCCESSFUL 与 EXITCODE=0；同一次运行写出的 android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.wiring.TreeCanvasWiringTest.xml 记录 tests="9" skipped="0" failures="0" errors="0"
- Regression evidence: artifacts/evidence/T-038-app-unit-tests.txt（:app 全量 28 个结果文件 / 302 例，failures=0 errors=0 skipped=0，EXITCODE=0）、T-038-check.txt（check 全模块 100 个结果文件 / 1031 例，failures=0 errors=0 skipped=0，EXITCODE=0）、T-038-no-kotlin-guard.txt（Java-only 守门 VERDICT: PASS (13 OK line(s))，EXITCODE=0）、T-038-source-scan.txt（VERDICT: PASS，EXITCODE=0）、T-038-mutation.txt（两条判定各自被变异杀死，还原后 EXITCODE=0）

GREEN 首轮并不干净：4 条用例失败，原因是测试夹具用 Branch.root(TOPIC, NOW) 造根枝后拿常量 ROOT 去查它，而 Branch.root 自己生成 id，夹具于是名不出那一行。修的是夹具（改成 Branch.restore(ROOT, ...)，与同文件里 child()/archived() 的做法一致），不是生产代码——修正后 4 条用例才真正开始驱动 load/topicOf/setCollapsed。改写刻意保持了文件行数不变，因此 RED 记录里引用的行号（60/213/241/252/258/264/271）在最终测试文件里仍然指向同一批声明。

### REFACTOR — optional
- Cleanup: 无额外结构改动。GREEN 之后只改了一处测试类注释：它原先写着 SQL 侧「由 instrumentation 套件覆盖」，而当时的 androidTest 里没有任何用例引用 TreeCanvasStore，属不成立的当前态声明，已改为明确记录该缺口。第 1 轮补上该类后，同一处注释里的缺口表述随之更新为「其 instrumentation 套件能编译但从未运行，SQL 在设备上是否成立仍未证实」（详见 Implementation notes）。
- Regression evidence: 与 GREEN 同一组证据；另见 artifacts/evidence/T-038-mutation.txt 的第三段（还原后同命令再次绿跑）。

第 1 轮（verifier 定点修复）：`SqliteTreeCanvasStore.committedRounds` 的 javadoc 与新增 instrumentation 类的头注释都在本轮改过措辞，两者都未改行为；javadoc 的实读结论见 Implementation notes。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- 文件所有权：本任务独占 `wiring/` 下新增的画布实现文件、`AppContainer` 的画布装配段、`MainActivity` 的画布宿主段、`ChatActions`/`ChatPresenter` 的入口动作、`screen/ChatScreen` 的入口按钮、`strings.xml` 的画布文案。
- 不得在屏幕里读库；屏幕只渲染 `TreeCanvasViewState` 并转发手势。
- 折叠写入必须走数据库层既有约束（`collapsed` 只接受 0/1），不要另建一套布尔语义。
- 不写任何正文（标题、摘要、问题、回答）到日志或证据文件。

- 决定（端口与实现分两层，照抄 T-032 的形状）：`wiring.TreeCanvasStore` 只是端口（四个读 `topicOf`/`branchesOf`/`summaryPayload`/`committedRounds` 加一个写 `setCollapsed`），`wiring.SqliteTreeCanvasStore` 是唯一的 SQL 落点，`wiring.TreeCanvasWiring` 是唯一的规则层并使 `TreeCanvasData` 与 `TreeCanvasCommands` 合体到同一个对象。理由：首页与对话两个 store 已经是这个形状（`HomeStore`/`SqliteHomeStore`、`ChatStore`/`SqliteChatStore`），画布照抄使「哪个类会碰 SQL」在全仓只有一个答案，也让「屏幕不读库」这条不变量可被原文扫描（`T-038-source-scan.txt` 的 D 节屏幕 0 命中、D+ 节同一模式在 `wiring/` 命中 60 行 / 68 次，证明空集是真的）。
- 决定（归档过滤放在 wiring，不放在 SQL）：`SqliteTreeCanvasStore.branchesOf` 连归档行一起读回，由 `TreeCanvasWiring` 滤掉 `archivedAt != null` 的行。理由：把可见性规则留在画布层，「哪些枝干进画布」就只有一个判定点；若在 SQL 里加 `archived_at IS NULL`，导航页与画布会各持一处口径，正是回归规则要防的漂移。该判定经变异测试确认承重：删掉归档过滤后**只有** `anArchivedBranchIsNotPartOfTheCanvas` 一条转红（`T-038-mutation.txt` 第一段，9 例 / 1 失败 / EXITCODE=1）。
- 决定（摘要取首行，且被拒的信封读作空）：`TreeCanvasWiring` 把派生产物解到摘要后只取换行符之前的第一行；`ArtifactPayloadCodec` 拒绝的信封不抛异常、读作空串。理由：几何标签只有一行高度（T-017 的 `LabelGeometry`），多行摘要会在画布上被静默截断，取首行让「画布上看到的」与「读者能用的」一致；坏信封读作空则保证一个坏信封不会让整棵树读不出来。变异测试同样确认承重：去掉首行截断后**只有** `aSummaryIsTheFirstLineOfWhatWasDerived` 转红（`T-038-mutation.txt` 第二段，EXITCODE=1）。
- 决定（读取失败返回失败，不返回空树）：`TreeCanvasData.load` 的失败以 `DomainResult` 失败承载（错误码 `OPERATION_NOT_COMPLETABLE`，不带驱动原文），由 `TreeCanvasPresenter` 转成可见 notice。理由：把读取失败退回空树会让「这个主题还没有枝干」与「读不出来」显示成同一个画面且都无法恢复，属回归规则禁止的「把失败吞成正常态」。覆盖：`aReadThatFailsIsCarriedOutRatherThanTurnedIntoAnEmptyTree` 与 `aFailedReadShowsTheCanvassNoticeInsteadOfABlankCanvas` 两条。
- 决定（首帧视野用宿主真实尺寸，不用 0×0）：`MainActivity.showTree` 用宿主当前宽度/高度构造首帧 viewport。理由：先返回的那次读取会把树拟合进空盒子，而后续 resize 按 T-017 的既定设计只记录新尺寸、不重新拟合，结果是树被画在屏幕外；种子必须是真实尺寸，本任务只负责喂对输入，不改几何语义。
- 决定（入口放对话页，不加首页入口）：入口是对话页顶栏的 `chat_overview`（文案 `strings.xml` 的「总览」），进入画布时把对话当前分支设为画布的当前分支。理由：动线是「在一段对话里先看清整体结构」，从当前分支回到总览最自然；首页已有分类/搜索/归档入口，再加画布入口会把首屏层级摊平。
- 决定（测试夹具改用 `Branch.restore`，属测试侧修复）：`TreeCanvasWiringTest` 的根枝夹具原用 `Branch.root(TOPIC, NOW)` 铸枝、再拿常量 `ROOT` 去查，而 `Branch.root` 自己生成 id，夹具于是查不到它自己命名的那一行，GREEN 首轮 4 条用例失败。改为与同文件 `child()`/`archived()` 一致地走 `Branch.restore(ROOT, ...)`。理由：夹具必须产出领域层真会产出的形态、并能读回它自己命名的那一行；这是测试缺陷而非生产缺陷，故不记为本任务的生产变更。
- 决定（守门口径：模式识别加匹配集非空）：`T-038-source-scan.ps1` 每个检测节都断言匹配集非空（A/B 期望 1、C 期望 3、D+ 期望大于 0），因此重命名或移动文件会让扫描 FAIL（exit 1），而不是把命中缩成 0 后再打印 PASS；D 节另配 D+ 正向对照，证明「屏幕 0 命中」是真空集而不是模式写错。
- 规模（如实记录，不作为已批准例外）：本任务新建的三个生产文件实测 physical=248 / 非空非注释=137（`T-038-sizing.txt`，EXITCODE=0），对照开工前的 `estimated_production_lines: 240`——非空非注释口径远低于估计，物理行口径略高；两者都远低于 500 行硬预算，故不触发 sizing 例外。`AppContainer`/`MainActivity`/`ChatActions`/`ChatPresenter`/`ChatScreen`/`strings.xml`/`screen_chat_body.xml` 的改动未计入规模：这些文件的工作副本同时载有更早的未提交任务，git 给不出只属于 T-038 的增量，写任何数字都是猜的。
- 第 1 轮决定（javadoc 改成与实现逐字对应，不改行为）：verifier 指出 `SqliteTreeCanvasStore.committedRounds` 的 javadoc 称「计入答案仍在生成中的那一轮」却只数 `row.isUser()`，并推断该状态不可达。按实读核对后该推断不成立：`ChatWiring.send` 在 `ChatWiring.java:93-103` 先铸 user/ai 两条消息，第 102 行调 `store.appendTurn(userMessage, aiMessage)` 落库，第 103 行才 `submit(command, ...)` 提交生成尝试；`SqliteChatStore.appendTurn`（`SqliteChatStore.java:142-156`）在一个事务里写问答两行。即「尝试被受理之前，问答两行都已持久化」，答案正文（`AnswerRevision`）才是随后写的。所以「答案仍在生成中而该轮已被计入」确实可达。原注释的真实缺陷是措辞与缺项：它把该轮说成 stored learner question（库里那一行是抽象的答案位而非正文），且漏掉「尝试被拒时答案位读作 FAILED、仍可重试、该轮照计」。新注释逐字对应实现（数的是 `Message.isUser()` 的问题行），并显式写出这两处顺序。行为未变：`:app` 302 例与全仓 1031 例的读数与第 0 轮逐项相同。同一轮还按 rules/documentation.md 改了一处会随之失真的当前态声明：`TreeCanvasWiringTest` 的类注释原写「no instrumented test drives it in this build」，而第 1 轮已新增该类，故改为「its instrumented suite compiles but never runs, so its SQL is unproven until a device does」——注释级改动且行数不变（331 行），RED 记录引用的行号仍指向原声明；这是本任务自己交付的测试文件，改动已在本记录中披露。
- 第 1 轮决定（补 instrumentation 覆盖，只编译不执行）：新增 `android/app/src/androidTest/java/com/treelearn/app/TreeCanvasStoreInstrumentedTest.java`（8 个 `@Test`，sha256 见证据头部），照 T-032 已确立的范式（`RealDataPathWiringInstrumentedTest` 的夹具形状）覆盖 `SqliteTreeCanvasStore` 的四个读与一个写：`topicOf`（存在与不存在）、`branchesOf`（该主题全部枝干、建序、另一主题不混入、归档行保留）、`summaryPayload`（信封原样且能被 codec 解回、未派生时为 null、一个 scope 的信封不当成另一个的）、`committedRounds`（只数问题行：问题 2 = 答案位 2，而总行数 4）、`setCollapsed`（直接读 `branches.collapsed` 原始列得到 1 与 0、`updatedAt` 前进、兄弟枝不受影响、展开可逆），外加两条边界：不存在的枝抛 `IllegalStateException` 且不动表；对已归档枝折叠被接受（钉住现状，并注明画布不画归档枝故 UI 不可达）。
- 第 1 轮决定（该用例的执行状态一律不写 PASS）：用户 2026-09-21 决定放弃 emulator/真机，故执行状态写 compiled, never executed - deferred 用户自行装机验证（user decision 2026-09-21）。它证明的只有「能在本项目目标 SDK/AGP 配置下编译并产出 APK」，不证明任何一条 SQL 在真实 SQLite 上跑通。
- 第 1 轮决定（关键门禁加 --rerun-tasks --no-build-cache）：verifier 指出第 0 轮绿跑的任务表是 1 from cache / 53 up-to-date，缓存命中不能当「跑过」的见证。本轮四条 Gradle 门禁全部免缓存重跑，任务表依次 54 executed（聚焦 JVM）、85 executed（androidTest 编译）、54 executed（:app 全量）、295 executed（check），均无 from cache、无 up-to-date。runner 与逐条读数在 `artifacts/evidence/T-038-r1-gates.ps1` 与 `T-038-r1-*.txt`。

## Verification report

- Verdict: PARTIAL —— 实现者自检结论：四个验收行为由 9 条 JVM 用例覆盖（同一命令 RED 后 GREEN）、原文扫描证明生产构造点在 `MainActivity` 且屏幕不读库、三项门禁与 Java-only 守门退出码 0；但设备侧证据一律 deferred，且本任务唯一的写库路径（折叠）所在的 SQL 层 `SqliteTreeCanvasStore` 没有任何自动化覆盖（见 Gaps），因此不判 PASS，等独立 verifier 判定。（第 1 轮事实更新：上句里「`SqliteTreeCanvasStore` 没有任何自动化覆盖」这一条已被补到「有覆盖、但只编译未执行」——见 Gaps①；本节 Verdict 仍为 PARTIAL，未由实现者改成 PASS，判定权仍在独立 verifier。）
- Evidence: artifacts/evidence/T-038-red-jvm.txt（EXITCODE=1；8 条编译错误逐条指向尚未写入的生产类型与因此失去超类型的测试替身）、T-038-green-jvm.txt（同一命令 EXITCODE=0；`android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.wiring.TreeCanvasWiringTest.xml` 记录 tests=9 skipped=0 failures=0 errors=0）、T-038-app-unit-tests.txt（`:app:testDebugUnitTest`，files=28 tests=302 failures=0 errors=0 skipped=0，EXITCODE=0）、T-038-check.txt（`check`，files=100 tests=1031 failures=0 errors=0 skipped=0，EXITCODE=0）、T-038-no-kotlin-guard.txt（`VERDICT: PASS (13 OK line(s))`，完整模式，EXITCODE=0）、T-038-source-scan.txt（VERDICT: PASS，A–H 八节，EXITCODE=0）、T-038-mutation.txt（两条承重判定各自被变异杀死，还原后 byte-identical 并再次绿跑；三段 EXITCODE=1 / 1 / 0）、T-038-sizing.txt（physical=248 / 非空非注释=137，EXITCODE=0）、T-038-readme-rescan.txt（README 旧措辞各 0 命中、替换小节恰好 1 命中，EXITCODE=0）、T-038-validate-tasks.txt（本任务文件的机械校验）。第 1 轮新增（免缓存重跑 --rerun-tasks --no-build-cache，头部记被测文件 SHA-256）：T-038-r1-jvm-treecanvas-uncached.txt（54 actionable tasks: 54 executed，files=1 tests=9 failures=0 errors=0，EXITCODE=0）、T-038-r1-androidtest-compile.txt（85 executed；APK 996789 bytes、writtenAfterThisRunStarted=True；2 个 class 文件，EXITCODE=0）、T-038-r1-app-unit-tests.txt（54 executed，files=28 tests=302 failures=0 errors=0 unreadable=0，EXITCODE=0）、T-038-r1-check.txt（295 actionable tasks: 295 executed，files=100 tests=1031 failures=0 errors=0 unreadable=0，EXITCODE=0）、T-038-r1-no-kotlin-guard.txt（VERDICT: PASS (13 OK line(s))，判据行 14，EXITCODE=0）、T-038-r1-source-scan.txt（VERDICT: PASS，A-H 八节，EXITCODE=0）、T-038-r1-validate-tasks.txt（本任务文件第 1 轮的机械校验）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本任务未删除、未改写任何既有断言。被改的是测试夹具与测试类注释两处：夹具由 `Branch.root` 改为 `Branch.restore`（夹具缺陷，见 Implementation notes 的决定）；注释原文称 SQL 侧「由 instrumentation 套件覆盖」，而该声明不成立——当时本 build 的 `android/app/src/androidTest` 下 0 处引用 `TreeCanvasStore`/`TreeCanvasWiring`/`TreeCanvasData`/`TreeCanvasCommands`，已改为明确记录该缺口。第 1 轮该缺口已被补上（新增的 `TreeCanvasStoreInstrumentedTest` 引用 `SqliteTreeCanvasStore` 与 `SqliteChatStore`），所以上面那句「0 处引用」只描述第 0 轮的事实，不再描述当前 build。等价覆盖：新增 9 条 JVM 用例，四条验收行为逐条落到具名用例（非归档枝干加摘要加轮次 / 归档枝干不进画布 / 折叠落库并可读回 / 读取失败显示 notice），其中两条唯一的承重判定另做了变异测试。
- Gaps: ① **`SqliteTreeCanvasStore` 的五个成员在第 0 轮零自动化覆盖，第 1 轮只补到「能编译、从未执行」**（四个读 `topicOf`/`branchesOf`/`summaryPayload`/`committedRounds` 加一个写 `setCollapsed`）——第 1 轮新增的 `TreeCanvasStoreInstrumentedTest` 已覆盖这四处读与一处写加两条边界，但它只编译、从未执行（用户 2026-09-21 决定放弃 emulator/真机），所以「这些 SQL 在真实 Android SQLite 上跑得通」仍然没有任何自动化证据，折叠的落库与展开的读回依然只经 JVM 测试替身验证。缺口只是从「没有用例」变成「有用例但没跑过」，量级未变。② 真机触控、像素观感、平板横屏证据未取得，记 `deferred — 用户自行装机验证（user decision 2026-09-21）`。③ **叶子摘要当前 build 读作空**：写入侧 `DerivedOperation` 仍无生产调用者，读取侧（`ArtifactPayloadCodec` 的 `BRANCH_SUMMARY`）虽已接上但无数据可读——这是声明的缺口，不是「已实现未生效」。④ 大主题的一次性布局耗时未在真机测过，也未量化。⑤ 本任务未产生设备侧或 instrumentation 运行证据（本轮无 emulator/真机）：第 1 轮新增的 `T-038-r1-androidtest-compile.txt` 只到「编译产物存在且为本轮新产物」为止，状态 compiled, never executed。
- Risks: ① 折叠判定点漂移——屏幕只许转发手势，落库判定只许留在 `SqliteTreeCanvasStore.setCollapsed` 一处；② `load` 一次读全量在极大主题上的内存与时间未量化（这是为「画面不跳动」做的有意取舍）；③ `SqliteTreeCanvasStore` 的 SQL 若存在方言或约束问题，当前只会在设备上暴露，没有自动化兜底；④ 每一条设备项在拿到真机证据前必须保持 deferred，不得因「代码已写完」而改写成 PASS。⑤ 新增的 instrumentation 套件只编译不运行，所以它的夹具形状（两枝 fork 走 `Branch.child`、原始 `branches.collapsed` 列读回）也从未在设备上验证过；它与 `RealDataPathWiringInstrumentedTest` 共用同一夹具形状，若形状错则两处同时错——该类已在 javadoc 里自陈这一点。

## Handoff notes

- Completed: 新增 `wiring/TreeCanvasStore.java`（端口）、`wiring/SqliteTreeCanvasStore.java`（唯一 SQL 落点）、`wiring/TreeCanvasWiring.java`（唯一规则层，同时实现 `TreeCanvasData` 与 `TreeCanvasCommands`）；`AppContainer.treeCanvasData()` / `treeCanvasCommands()` 两个访问器返回同一个实例；`MainActivity` 作为 `TreeCanvasSink` 宿主（`showTree`/`leaveTree`/`openOverview`/`render`，`openBranch` 同时满足 `ChatSink` 与 `TreeCanvasSink`）；对话页 `chat_overview` 入口（`ChatActions.onOverviewPressed()`、`ChatSink.openOverview(String)`、`ChatPresenter`、`ChatScreen`、`res/layout/screen_chat_body.xml`、`strings.xml` 的「总览」）；`android/README.md` 新增「应用层入口索引（`:app`）—— 树画布的生产装配与入口（T-038）」一节，并改掉旧的当前态表述（「画布无任何构造点」等措辞反向检索 0 命中）。第 1 轮（verifier 定点修复）另加：`SqliteTreeCanvasStore.committedRounds` 的 javadoc 重写为与实现逐字对应（不改行为）；新增 `android/app/src/androidTest/java/com/treelearn/app/TreeCanvasStoreInstrumentedTest.java` 覆盖该类的四个读与一个写加两条边界（只编译、未执行）；四条 Gradle 门禁以 --rerun-tasks --no-build-cache 重跑并留下免缓存见证。
- In progress: 无。
- Blocker: 无外部阻塞。（历史：开工前等 T-032 落地组合根；T-032 现已进入 verifying，`AppContainer` 是唯一组合根。）
- Unblock condition: 不适用。
- Next steps: ① 交独立 verifier 复核第 1 轮：javadoc 是否已与实现逐字对应（请核 `ChatWiring.java:93-103` 与 `SqliteChatStore.appendTurn` 的写序——verifier 上一轮的「该状态不可达」推断与这两处不符）；以及「instrumentation 用例只编译不执行」这条是否可接受为 deferred，还是要设备侧运行证据；② 用户按 2026-09-21 决定自行装机验证设备项（触控节奏、未读芽观感、`layout-sw600dp/screen_tree.xml` 横屏排布）；③ 与导航页入口任务（T-039）合并做一次「画布与导航页对可见枝干同口径」的入口回归。
- Watch-outs: 不要把 `TreeCanvasPresenter` 的规则复制进屏幕（折叠的落库判定只许在 `SqliteTreeCanvasStore.setCollapsed` 一处）；不要让画布与导航页对「哪些枝干可见」给出不同答案；`load` 只许一次读全量（分批读会让画面在补数据时跳动，这是有意取舍而非遗漏）；设备项在拿到真机证据前一律保持 deferred。新增的 instrumentation 套件只在编译层面被证明，不要在任何地方把它描述成「SQL 已验证」——它能否在设备上通过都还不知道。
## Verification report — 收尾复核（verifier #3，2026-09-24）

- Verdict: PASS（针对 Next steps ①② 的收尾复核）。
- ①（javadoc 与实现逐字对应）→ 本轮亲自复核写序：`ChatWiring` 的 `store.appendTurn(userMessage, aiMessage)`（现 :272）先于 `submit(...)`（现 :276），`SqliteChatStore.appendTurn`（:203，javadoc :42）在一个事务里写问答两行并同事务盖 topic 时间戳——第 1 轮重写的 `committedRounds` javadoc 与实现相符，verifier 上一轮「该状态不可达」的推断确实不成立。行号较第 1 轮漂移（并行 lane 增加了构造器），按内容定位。
- ②（instrumentation 只编译不执行）→ 按用户 2026-09-21 决定接受为 `deferred — 用户自行装机验证（user decision 2026-09-21）`；`TreeCanvasStoreInstrumentedTest` 8 例装机后优先跑。
- ③（画布与导航页同口径回归）→ 归 T-039 拆分任务（107/108），不阻塞本任务。
- 门禁（本轮实测，覆盖第 1 轮 G1 的复跑要求）：`./gradlew --no-daemon --console=plain check` EXITCODE=0（`artifacts/evidence/P3-B3-gates-2026-09-24.txt`）；Java-only guard `VERDICT: PASS (13 OK line(s))`（`artifacts/evidence/P3-B3-no-kotlin-2026-09-24.txt`）。
- 规模：三个新文件 137 非空非注释行 < 500 硬上限，不属越线清单。
- 叶子摘要读作空、布局耗时未量化两条缺口维持原判（声明的缺口，非隐藏缺陷）。
