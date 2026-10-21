# Codex Harness Task: ROADMAP D8——生成阶段机不再依赖线程调度

id: T-2026-10-18-018
title: ROADMAP D8——生成阶段机不再依赖线程调度
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-android-phone-usability-requirement-017.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 31
shared_code_touched: false
depends_on: [T-2026-10-18-android-nav-untitled-branch-016.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 017 要求「手机版各入口真实可用 + JVM 门禁可判绿」。本片做后半句：`:app:testDebugUnitTest` 自 2026-10-16 起被 §D8 记着两条红，任何片子的完成结论都要先绕开它们，门禁因此不可用。
- Problem/background: 两条红同名固定（`anAcceptedSendShowsTheSendingStageUntilTheFirstRowArrives` 期望 SENDING 读到 IDLE、`aStopRequestTurnsTheTerminalReadIntoTheCanceledStage` 期望 CANCELED 读到 FAILED）。读码定位到同一根因：阶段是「第几次读」的函数。`send()` 先渲染 SENDING，再在后台任务里 `read()` 后 `publish()`，而 `awaitingFirstRow` 只在 `publish()` 的 accepted 分支置位（在 read 之后）；`startGenerationPolling()` 又起真实线程并在 t=0 立刻排一次 `read()`，这条读能否被手工驱动的执行器 `drain()` 捞到全看线程调度。捞到 → 补出 SENDING 帧、并把终态行读两次把 `stopRequested` 消费掉 → FAILED。
- Users/scenarios: 学习者发出一条提问后，屏幕应当稳定停在「发送中」直到首行落地；点过停止的那条回答在后续任何一次读回里都应当仍读作「已取消」；而对同一条回答点重试后再次失败，应当读作「失败」而不是沿用上一次停止的解释。
- Expected outcome: 同一批存储行只派生一种阶段序列；`:app` JVM 套件零红，门禁恢复可判定。
- Core flow and reversibility: 五处生产改动全在 `ChatPresenter.java` 一处文件，可逐行回退；不改 SQL、不改命令层、不改任何用户文案与视觉规格。
- Inputs/outputs: 输入 `ChatData.Snapshot.messages` + `stopRequested` + `awaitingFirstRow`；输出 `ChatViewState.Stage`。新增内部状态 `canceledRowId`（被停止意图解释过的那条终态行 id）。
- State and exception handling: 「重读同一终态行」「同一行重生成后再失败」「下一次尝试的新终态行」三者必须区分——第一种保持 CANCELED，第二、三种不再享受上一次的停止解释（`theStopIntentIsConsumedByTheTerminalItExplains` 继续成立）。
- Constraints and trade-offs: Java-only；不新增依赖；不为凑绿放宽断言（§D8 的修法明确要求补「同一批消息序列只能派生出一种阶段序列」的断言，本片照做而不是把超时放宽）；轮询线程不引入新的可注入端口——9 个构造器重载已经是要防的东西，再加参数是反向操作。
- AI recommendation and rationale: 把「等待首行」的标志归到发起发送的那一刻（它描述的就是这一轮），把重复读挪出 t=0（发起轮询的那条命令刚读过存储），把取消解释绑到它解释的那行并按行生命周期释放。三处都是把状态归位，不是加补偿逻辑。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、门禁不红是目标的一部分。
- AI-autonomous decisions: ① 轮询改为 sleep-then-read 而不是给测试开后门（生产语义只是第一次重读推迟 200ms，那次读本就是重复）；② `canceledRowId` 用行的 `messageId` 而不是内容指纹——内容增长不该改变「这一轮被谁停止」的事实；③ 删掉 `publish()` 里那行重复置位而不是留着双写，避免两个所有者。
- User-confirmation decisions: 无新增（沿用 017 已确认范围）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-18-web-android-slice-018-d8-stage-determinism.txt` 九块 + want/got 6/6；真机截图 `T-2026-10-18-d8-chat-stable-after-fix.png`。
- Facts: 改动前定向跑 9 例 2 红；把本片新断言对着改动前的 `ChatPresenter.java` 跑 → 10 例 3 红；删掉「非终态清空 canceledRowId」这一支 → 11 例 1 红；恢复后同一条命令 11/11 绿；全量 `:app` 848 例 0 失败 0 错误（此前 846 例 2 失败）。`ChatWiring.retry/reset` 经读码确认走 `replace(messageId, …)`，即原地复用同一 id——这是第二支变异测试的来源。
- Assumptions: 轮询推迟 200ms 不影响任何真实生成流的最终可见性（它只把一次重复读挪后）。
- Unknowns: 在线真实发送一次时 SENDING→REASONING→ANSWERING 的观感未实测（会花学习者额度并写真实数据，本片不擅自触发）。
- Risks: 若某条链让同一 `messageId` 在终态与生成态之间反复而不经过 `send()`，`canceledRowId` 的释放点只在非终态读上；现有 retry/reset 会产生 GENERATING 行，已被 `aRetryOfTheCanceledRowThatFailsAgainReadsFailedNotCanceled` 覆盖。
- Deferred: §D7 真机仪器既有红的逐条定性；在线发送的真实流观测。
- Non-goals: 不做 Web 复刻、不重构 `ChatPresenter` 的 9 个构造器重载、不改阶段机的用户可见语义。
- Skipped non-critical questions and reason: 未追问是否把轮询间隔做成可配置，因为当前没有任何第二个消费者。

## Goal

让对话页的生成阶段只由存储行与学习者意图决定，并使 `:app` JVM 套件第一次零红。

## Scope

### In scope
- `ChatPresenter.send()` 的 `awaitingFirstRow` / `canceledRowId` 复位时机；`publish()` 删除重复置位；`startGenerationPolling()` 改为先等待后读；`withGenerationState()` / `stageOf()` 的取消解释按行绑定并在非终态读释放。
- `ChatGenerationStageTest` 新增 `assertStageSurvivesMoreReads()`、`theTerminalRowAStopExplainedKeepsReadingCanceledOnEveryLaterRead()`、`aRetryOfTheCanceledRowThatFailsAgainReadsFailedNotCanceled()`。
- `.codex/ROADMAP.md` §D8 的处置回写。

### Out of scope
- §D7 真机仪器既有红、`ChatPresenter` 构造器收敛、在线真实发送验证、任何视觉规格改动。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 学习者看到的阶段语义不变（发送中/思考中/回答中/稳定/已取消/失败）；改变的是「多读一次不得改变结论」与「同一行重生成后不得沿用上一次停止的解释」。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `ChatPresenter`；消费者 `ChatView`/`ChatScreen`（阶段驱动生成槽位可见性与 T-117 跟随判定）；共用 `stageOf` 的同机测试（`ChatPresenterTest`、跟随判定等）由全量套件覆盖（块 07）。Web 线尚无对话屏（阶段 3+），无跨端正文需要同步。
- Native bridge or independent-client compatibility/update requirements: 无桥接改动。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: 无用户可见事实变化；`.codex/ROADMAP.md` §D8 从「既有红」变为「已闭合」。
- Authoritative code/config/migration evidence: `ChatPresenter.withGenerationState()/stageOf()/send()/startGenerationPolling()`；`ChatGenerationStageTest` 三条新断言。
- Documentation surfaces checked: `.codex/ROADMAP.md` §D8、`Docs/04-ux-design/知芽（TreeLearn）对话页视觉重做规格_V1.0.md`（§2 阶段机的用户可见规则未变，故不回写）、`Docs/05-technical-design/*`（无「重读会翻结论」这类描述）。
- Documentation files updated: `.codex/ROADMAP.md` §D8 加处置行；本卡。
- Producer/consumer/sibling/compatibility paths checked: 阶段派生只此一处（`grep -n "withGenerationState\|stageOf" ChatPresenter.java`）。
- Superseded phrases/examples reverse-searched: 测试类注释旧句「the generation poller's first read cannot race the state the test asserts on」已被证伪，随本片改写。
- `documentation impact: none` reason and evidence, 若适用：不适用——ROADMAP 欠账状态本身就是文档面。
- Historical/PRD handling: 历史读数（831/2、846/2）保留在 §D8 原文与旧证据里，不改写。

## Acceptance criteria

- [x] 定向命令由红转绿，且新断言在缺陷存在时为红（证据块 01/04/05/06：改动前 9 例 2 红 → 变异 10 例 3 红 → 删支 11 例 1 红 → 恢复后 11/11 绿）。
- [x] `./gradlew :app:testDebugUnitTest` 全量零红，`:app` 门禁恢复可判定（块 07：94 个结果文件、848 tests / 0 failures / 0 errors）。
- [x] Java-only 不破（块 08：`verify-no-kotlin.gradle` 随 5 次 Gradle 调用通过，diff 内 0 个 `.kt/.kts`）。
- [x] 真机 `adb install -r` 后主页与对话页正常渲染，`TreelearnUi` 无新增读失败行（块 09，install 22:45:37 + dex 含 `canceledRowId` + 截图）。
- [x] 同一行原地重生成（retry/reset 复用 messageId）后再次失败读作 FAILED，不读作 CANCELED（块 05 的变异判据）。
- [x] 在线真实发送一次，观察 SENDING→REASONING→ANSWERING 实际观感——**用户在 2026-10-20 的范围内确认中明确排除「真实 AI 发送测试」**，本项按 prior approval 豁免执行；离线路径（定向单测 + 两路变异 + 全量 JVM 848/0）已覆盖状态机本身，见 Gaps ①。

## TDD execution record

### RED — failing test first
- Test/scenario: 三条断言分两路变异验证——① 把本片改完的 `ChatPresenter.java` 备份后 `git checkout HEAD --` 回退（新断言对着缺陷代码跑）；② 只删掉「非终态读清空 `canceledRowId`」这一支
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatGenerationStageTest"`
- Exit code: 1
- Observed failure: 改动前 9 例 2 红（`expected:<CANCELED> but was:<FAILED>`、`expected:<SENDING> but was:<IDLE>`）；变异 ① 10 例 3 红（新增那条同为 `expected:<CANCELED> but was:<FAILED>`）；变异 ② 11 例 1 红（`the same row failing again is a new attempt, not the old stop expected:<FAILED> but was…`）
- Failure reason validated: 两条原始红同源——`awaitingFirstRow` 在 read 之后才置位、轮询线程在 t=0 抢一次读；被抢到的那次读把终态行读两次并消费掉 `stopRequested`。不是断言写错，也不是超时太紧。

### GREEN — minimum implementation
- Implementation: `send()` 里在 read 之前置 `awaitingFirstRow = true` 并复位 `canceledRowId`；`publish()` 删除重复置位；`startGenerationPolling()` 先 `sleep(200)` 再 `execute(read)`；`withGenerationState()` 把 CANCELED 绑到它解释的那条终态行 id、非终态读释放；`stageOf()` 读该 id 而非仅读一次性标志
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatGenerationStageTest"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`；结果 XML `{'tests': '11', 'skipped': '0', 'failures': '0', 'errors': '0'}`
- Regression evidence: 全量 `./gradlew :app:testDebugUnitTest` → 94 个结果文件、`tests 848 skipped 0 failures 0 errors`（块 07）；真机 install -r 后对话页渲染正常（块 09）

### REFACTOR — optional
- Cleanup: 无额外清理；删掉 `publish()` 的重复置位属于本片最小改动的一部分。
- Regression evidence: 同 GREEN 的全量套件。

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- `estimated_production_lines: 31` 取生产文件的净新增行数（`git diff --numstat` = 31 增 / 4 删）；测试文件另计 73 增 / 2 删。
- 为什么不用「给测试注入一个可控调度器」：那要给 `ChatPresenter` 加第 10 个构造器重载，并把一个真实缺陷（重复读 + 状态归位错误）永久藏在测试替身后面。
- `canceledRowId` 与 `tailFingerprint` 是两件事：前者回答「这一轮是被停止解释的」，后者回答「内容有没有长」。

## Verification report

- Verdict: PASS（独立复核，2026-10-20：单测 + 全量 JVM + 两路变异 + 真机渲染四层；在线发送按用户决定豁免。）
- Evidence: `artifacts/evidence/T-2026-10-18-web-android-slice-018-d8-stage-determinism.txt` 块 01–09，want/got 6/6；2026-10-20 全量仪器套件 `android-instrumented-2026-10-20.xml` 301 例 0 失败（含 D7 真机红全部转绿），JVM 848/0。环境：Windows / Gradle 8.14.3 wrapper / 真机 AWSS013807000356。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何断言；改写了测试类注释里被证伪的那句前提，并用「再 drain 两次」把该前提变成可判定的断言而不是假设。
- Gaps: ① 在线真实发送未观测，**用户在 2026-10-20 明确将「真实 AI 发送测试」排除出本期范围**，属 prior approval 的豁免而非遗漏；离线状态机与变异证据覆盖 SENDING→REASONING→ANSWERING 的判定逻辑，但不覆盖真实网络观感；② §D7 真机仪器既有红已由 2026-10-20 全量套件 301/0 关闭；③ 平板横屏按用户决定下期做。
- Risks: `canceledRowId` 让「已取消」比原先多活若干次读——释放点是任意非终态读与任何非 CANCELED 终态读；retry/reset 复用同一 id 的路径已由测试覆盖，其余复用同 id 的命令若存在，需在下一次动命令层时复查。

## Handoff notes

- Completed: §D8 闭合，`:app` JVM 门禁 848/0 首次全绿；五处生产改动 + 三条新断言 + 两路变异校验；真机最终 APK 复跑；ROADMAP 回写。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: ①§D7 真机既有红的逐条定性（先读代码与既有读数判性质，不跑会卸载应用的套件）；②回到 Web 线阶段一夹具采集与逐屏 1:1；③下一次动命令层时复查「同 id 复用」的其它路径。
- Watch-outs: ① 任何写进卡片的命令必须与实际执行逐字相同（本片的 RED/GREEN 是同一条定向命令）；② 真机验证只用 `adb install -r`；③ 门禁绿不等于在线流已验证，别把 848/0 当成真实生成的证据；④ 设备 `System.err` 里有 `com.dragon.read` 的崩溃行，与本应用无关，别把它当回归。
