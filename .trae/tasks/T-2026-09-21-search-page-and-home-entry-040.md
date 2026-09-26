# Treelearn Harness Task: 搜索页与首页搜索入口

id: T-2026-09-21-040
title: 搜索页与首页搜索入口
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 320
shared_code_touched: true
depends_on: [T-2026-09-21-real-data-path-wiring-032.md, T-2026-09-20-search-unread-reading-position-015.md, T-2026-09-21-reading-position-wiring-042.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD 6.2.8（非根分支标题、摘要与搜索）要求用户能在全部分支中搜索并跳回命中位置；搜索的仓储与 presenter 已交付并被单测覆盖，但**没有屏幕、没有布局**，首页的搜索入口还是 `enabled="false"` 的死按钮。
- Problem/background: `search/SearchPresenter`、`SearchViewState`、`SqliteSearchRepository` 已存在，`SearchPresenter` 的唯一构造点在 `src/test`；`res/layout/` 下没有任何含 search 的文件；`screen_home_body.xml` 的搜索按钮写死 `enabled="false"` 且 `HomeScreen` 没给它挂监听器。
- Users/scenarios: 用户记得某句话但忘了在哪个分支，输入关键词后看到跨分支命中列表，点一条直接跳进那条分支并定位到命中位置。
- Expected outcome: 有搜索屏与布局，首页搜索入口可用；输入即搜；命中项显示所属分支与片段；点击跳转到分支并恢复阅读位置。
- Core flow and reversibility: 搜索是只读操作，无副作用；离开搜索页不改变任何数据。查询串在页面存活期内保留，离开即丢弃。
- Inputs/outputs: 输入 `SearchPresenter` 的 `SearchViewState`（`phase`/`draft`/`hits`）与 `SearchPresenter.Sink`（`render`/`openAnchor`）；输出新的 `screen/SearchScreen`、`res/layout/screen_search*.xml`（含 `layout-sw600dp/` 镜像）、首页入口启用与宿主装配。
- State and exception handling: 空查询显示引导而非空结果；无命中显示明确空态；读取失败给可见 notice；搜索不返回未被任何分支引用的过期稳定版本（由仓储保证，屏幕不得自行过滤）。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；布局只用 design token 档位；可点区域 ≥ 44×44 dp；中文输入下的即时搜索不得阻塞 UI 线程（走 `AppContainer.background()`）。
- AI recommendation and rationale: 把「跳回命中位置」接到既有阅读位置存储（`reading/ReadingStore`）而不是新造一套定位，避免同一件事有两个判定点。
- User choice and custom additions: 用户 2026-09-21 明确要求搜索必须实现且可用。
- AI-autonomous decisions: 搜索入口放在首页（原型里就是首页的搜索按钮），结果点击直接进对话页并定位。
- User-confirmation decisions: 设备验证由用户装机实测（2026-09-21 决定）。
- Acceptance evidence: RED→GREEN 的 JVM 测试（输入触发搜索、空态/无命中态、命中点击产生的跳转意图含 topicId/branchId/anchor）、`:app` 单测与 `.\gradlew.bat check` 退出码 0、Java-only guard PASS、设计 token 守门、原文扫描证明首页搜索按钮不再是死按钮。
- Facts: `SearchPresenter.Sink` 已定义为 `render(state)` + `openAnchor(topicId, branchId, ReadingAnchor)`；`SearchViewState` 有 `showsResults()`；首页搜索按钮在 `screen_home_body.xml` 且当前 disabled。
- Assumptions: T-032 已把数据库接好；T-015 的仓储与 presenter 保持现行签名。
- Unknowns: 真机中文输入法下的搜索体验（无设备）。
- Risks: 屏幕自行过滤命中导致与仓储口径不一致；搜索在 UI 线程执行导致卡顿；把死按钮改成可点却忘记补监听器。
- Deferred: 真机输入法与像素证据。
- Non-goals: 不改索引策略与命中排序；不做画布内搜索；不做主题级过滤。
- Skipped non-critical questions and reason: 结果条数与分页沿用仓储既有行为，不额外追问。

## Goal

用户能从首页进入搜索、输入关键词看到跨分支命中、点一条跳进对应分支并定位到命中位置。

## Scope

### In scope
- 新增 `android/app/src/main/java/com/treelearn/app/screen/SearchScreen.java`（渲染 `SearchViewState`，实现 `SearchPresenter.Sink`）。
- 新增 `res/layout/screen_search.xml` / `screen_search_body.xml` / `item_search_hit.xml`，并按既有约定补齐 `res/layout-sw600dp/` 镜像。
- 启用首页搜索入口并挂上监听器；`HomeActions`/`HomePresenter` 增加「打开搜索」这一纯导航动作。
- `AppContainer` 暴露搜索所需端口；`MainActivity` 作为宿主负责进入/离开搜索与处理 `openAnchor`。
- `strings.xml` 补齐搜索文案（引导、空态、无命中、失败）。

### Out of scope
- `SqliteSearchRepository` 的查询与索引策略改动。
- 画布内搜索、主题级过滤、搜索历史。
- 任何 schema 改动。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 首页搜索按钮由禁用变为可用；**保留**仓储的「不返回未被引用版本」语义与 presenter 既有测试结论。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `SqliteSearchRepository`；消费者是新屏幕；宿主是 `MainActivity`；跳转消费者是阅读位置存储。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 搜索从不可达变为可达；首页搜索入口启用；命中跳转接到阅读位置存储。
- Authoritative code/config/migration evidence: `app/src/main/java/com/treelearn/app/screen/SearchScreen.java`、`res/layout/screen_search*.xml`、`screen_home_body.xml`、`MainActivity.java`、测试输出。
- Documentation surfaces checked: `android/README.md`（`:app` 入口索引、「记录缺口」小节）、Android 技术方案 §9、PRD 6.2.8。
- Documentation files updated: README 中「搜索无屏幕、无消费者」与「首页搜索按钮为死按钮」的当前态表述改为真实入口。
- Producer/consumer/sibling/compatibility paths checked: 首页 → 搜索 → 命中 → 对话页定位；返回路径。
- Superseded phrases/examples reverse-searched:「搜索无屏幕、无消费者」「搜索入口硬禁用」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: T-015 的历史记录不改写，只补它的生产 UI。

## Acceptance criteria

- [x] 同一 JVM 测试命令先 RED 后 GREEN：断言输入触发一次搜索、空查询显示引导、无命中显示空态、命中点击产生含 `topicId`/`branchId`/锚点的跳转意图。
- [x] 原文扫描证明首页搜索入口不再是 `enabled="false"` 的禁用控件，且 `HomeScreen` 为其挂了监听器。
- [x] 搜索不在 UI 线程执行：断言搜索调用发生在注入的 background executor 上（不是 `uiThread`）。
- [x] `:app:testDebugUnitTest`、`.\gradlew.bat check`、Java-only guard、设计 token 守门、文档影响记录齐全且退出码 0。
- [x] 真机输入法与像素证据记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得写成 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: SearchPageTest(8) + SearchJumpTest(2) + HomeSearchEntryTest(3)：输入触发一次搜索、空查询给引导而不是「零结果」、无命中是独立空态、命中点击产生含 topicId/branchId/锚点的跳转意图、屏幕不自行过滤或改序、首页搜索入口可点
- Command: .\gradlew.bat --no-daemon --console=plain --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "com.treelearn.app.search.SearchPageTest" --tests "com.treelearn.app.search.SearchJumpTest" --tests "com.treelearn.app.ui.HomeSearchEntryTest"
- Exit code: 1
- Observed failure: javac 报 24 个错误，全部是「新增符号还不存在」（程序包…不存在 / 找不到符号），三个测试类根本无法编译
- Failure reason validated: 失败源于实现缺失而不是测试写错——断言引用的 SearchPage / BranchTitles / onSearchPressed 等符号当时在 src/main 下没有任何声明；实现落盘后同一条命令退出码 0

### GREEN — minimum implementation
- Implementation: 新增 screen/SearchScreen（只渲染与转发）+ search/SearchPage（纯 JVM 页面对象）+ search/BranchTitles（分支标题端口）+ wiring/SqliteSearchStore（惰性开库）与四个布局（含 layout-sw600dp 镜像）；MainActivity 实现 SearchPresenter.Sink（render 交给新屏幕、openAnchor 交给 showChat(branchId, anchor)）；HomeScreen 给 home_search 挂监听器走 HomeActions.onSearchPressed()，并删掉布局上的 android:enabled="false"
- Command: .\gradlew.bat --no-daemon --console=plain --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "com.treelearn.app.search.SearchPageTest" --tests "com.treelearn.app.search.SearchJumpTest" --tests "com.treelearn.app.ui.HomeSearchEntryTest"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL；同一次运行的 JUnit XML 实测 SearchPageTest tests=8 failures=0 errors=0、SearchJumpTest tests=2 failures=0 errors=0、HomeSearchEntryTest tests=3 failures=0 errors=0（合计 13 例 0 失败），原始输出与命令行见 artifacts/evidence/T-040-green.txt
- Regression evidence: 全量 :app 单测 36 suites / 354 tests / 0 failures（artifacts/evidence/T-040-regression.txt，并另从 android/app/build/test-results/testDebugUnitTest 的 XML 聚合复核）；.\gradlew.bat --no-daemon --console=plain check 退出码 0（artifacts/evidence/T-040-check.txt）；Java-only guard VERDICT: PASS (13 OK line(s)) 与设计 token 守门 0 check(s) failed（artifacts/evidence/T-040-guards.txt）；release APK 重建后 dex 里同时含 T-040 与 T-039/T-041/T-042/T-044 的符号、五个测试类均为 0（artifacts/evidence/T-040-apk-contains-search.txt）

### REFACTOR — optional
- Cleanup: 未做独立重构轮；实现一次成形。`SearchPresenter.isStillReachable` 由 UI 线程移到 background 执行属实现期语义变更（方向正确、有单测覆盖），见 Handoff 与 verifier 记录 F3。
- Regression evidence: 与 GREEN 同一命令全绿；`.\gradlew.bat check` EXITCODE=0（`T-040-check.txt`）。

### TDD or sizing exception — only with prior user approval
- Exception type: 规模例外（**事后追认**，2026-09-24 用户批准）
- Reason: verifier 独立复算：新增 8 文件 = 395 行；共享文件新增行（按 patch 记录）= 122（紧）..189（松）→ 合计 **517..584**，在任何严格读法下都超过 500 硬上限。明细与取数命令见 `T-040-verify-scan.txt` 与 `T-040-sizing.txt`。
- User approval: 2026-09-24 用户在对话中裁定「事后追认豁免」。批准落在实现之后，按 `harness-tasks.md` 第 60 行如实记为事后追认。剩余风险：单 task 超硬上限 3%~17%（口径区间）；若需拆分，以「搜索页 UI 切片」与「首页入口接线」为天然切面。
- Alternative verification: 不适用（本项是规模例外；TDD 按 required 执行，见上，含三份变异证据）。

## Implementation notes

- 屏幕只渲染 `SearchViewState` 并转发，不得自行过滤命中或改写排序。
- 跳转（`openAnchor`）由宿主处理：进对话页并把锚点交给阅读位置存储，不在屏幕里直接读库。
- 布局只用 `tokens.json` 档位；新增档位先落原型 `:root` 再跑守门。
- 不写任何正文（查询串、命中片段）到日志或证据文件；测试夹具用短 ASCII 假串。

RED/GREEN 的原始输出、命令行与退出码逐条落在 `artifacts/evidence/T-040-{red,green}.txt`；RED 与 GREEN 用的是逐字符相同的一条命令。变异测试三份（`T-040-mutation-{title-dedupe,empty-pane,row-order}.txt`）：分别把「每分支只查一次标题」「空结果是独立空态」「按仓储顺序列出」退化成缺陷形态，每次只有目标用例转红、还原后回绿。规模与取数命令见 `artifacts/evidence/T-040-sizing.txt`。

## Verification report

- Verdict: PARTIAL
- Evidence: `artifacts/evidence/T-040-verify-scan.txt`（285 行 / 25 个 `EXITCODE=`，10 节，末行 `failing judgement(s) = 0` 与 `VERIFY-SCAN PASSED`；生成器 `artifacts/evidence/T-040-verify-scan-runner.py`）与 `artifacts/evidence/T-040-verify-searchdao.txt`（A–E 节，末行 `VERIFY-SEARCHDAO PASSED`）。两份均为本轮独立静态取证，头部含 captured-at/cwd/命令，正文自带 `EXITCODE=`。**本轮未跑 Gradle**（另一 lane 持锁，见 Gaps）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未发现被删除/弱化的断言；实现者三份变异证据（`T-040-mutation-{title-dedupe,empty-pane,row-order}.txt`）各自只打红目标用例并还原，我在 §5 独立复核了「屏幕不重排/不重滤」这一断言所守的等价行为（`SearchScreen`/`SearchPage` 内 0 处排序/过滤）。

### 审阅文件
`screen/SearchScreen.java`、`search/SearchPage.java`、`search/BranchTitles.java`、`wiring/SqliteSearchStore.java`、`search/SearchPresenter.java`、`search/SearchRepository.java`、`search/SearchViewState.java`、`MainActivity.java`、`ui/HomeScreen.java`、`ui/HomePresenter.java`、`ui/HomeActions.java`、`ui/HomeSink.java`、`res/layout/screen_home_body.xml`、`res/layout/screen_search.xml`、`res/layout-sw600dp/screen_search.xml`、`data/.../dao/SearchDao.java`、`data/src/test/.../SearchDaoStatementTest.java`、`core/.../domain/SearchHit.java`、`android/README.md`、`Docs/06-delivery/TreeLearn_V1_交付报告.md`、`artifacts/evidence/T-040-*.txt`。

### 逐条判据
- **判据 1（首页入口不再死）— PASS。** 复核：`screen_home_body.xml:35-42` 的 `home_search` 块**不含** `android:enabled`；正控 `home_send:154-163` **含** `android:enabled="false"`（证明提取器不瞎）。同时 `HomeScreen` 只在 `send`/`archived` 上 `setEnabled`，**从不**对 `search` 动态禁用（§1 三条 JUDGEMENT 全 OK）。
- **判据 2（监听器真的挂了，正反两面）— PASS。** 正向：`HomeScreen` 给 `home_search` 挂监听 → `HomeActions.onSearchPressed()`（`HomeActions.java:28`）→ `HomePresenter.java:169-170 sink.openSearch()` → `HomeSink.java:35 openSearch()` → `MainActivity.java:357 openSearch()` → `:575 showSearch()` 真实构造 `new SearchScreen(this)` / `new SearchPresenter(..., container.background(), container.searchRepository())`（`:586-588`）并切换屏幕。反向（是否为「死按钮改可点却忘补监听器」）：监听器存在且 `onSearchPressed` 被有意**不加门**（空首页也能开搜索），且无第二处把 `search` 重新禁用。
- **判据 3（搜索不在 UI 线程）— PASS。** `SearchPresenter` 的库读全部经注入的 background：`repository.search(query)` 在 `:85`/`:96` → `search()`（`:128-134`），命中可达性 `repository.isStillReachable(hit)` 在 `:112-115`，结果一律 `ui.post` 回主线程（§4）。
- **判据 4（`render(state)` / `openAnchor(topicId, branchId, ReadingAnchor)` 生产链 + 不新造定位路径）— PASS。** `SearchPresenter.Sink` 就这两个方法（`SearchPresenter.java:34-45`）；`MainActivity` 实现 `SearchPresenter.Sink`（`:66`）并在 `:277`/`:299-300` 落地，锚点只走 T-042 的 `showChat(branchId, anchor)`。全仓**只有 1 处**带锚点的跳转调用点，且只在 `MainActivity`（§3 两条 JUDGEMENT）——不存在第二套定位。
- **判据 5（单测/check/守门/文档）— PARTIAL（采信实现者自证 + 本轮独立复算，见 Gaps）。** `T-040-red.txt` 与 `T-040-green.txt` 的 `Command` 行逐字符相同；`T-040-green.txt`（8/2/3 例 0 失败）、`T-040-regression.txt`（36 suites / 354 tests / 0 failures）、`T-040-check.txt`、`T-040-guards.txt`（Java-only `VERDICT: PASS (13 OK line(s))`、token 守门 0 失败）均自带 captured-at/cwd/命令/`EXITCODE=`。变异三份头部用 `gradle cwd:` / `command (both runs):` 标签，内容齐备（标签形态差异，非缺证据）。
- **判据 6（设备侧）— deferred，不计 PASS/FAIL。** 记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`；`SearchUnreadReadingInstrumentedTest` 10 例 `blocked — 缺少设备`。
- **T-015 契约未被改动 — PASS（复核并更正了我自己的判据）。** `SearchRepository` 仍是 `List<SearchHit> search(SearchQuery)` 与 `boolean isStillReachable(SearchHit)`；`SqliteSearchRepository` 只委托 `SearchDao`、自身无 SQL；归档过滤由仓储保证、屏幕不得自滤（§5/§6）。**注意：此处我上一版判据是缺陷（见 Findings D0），已改为对拼装结果取证。**

### Findings
- **D0 [判据缺陷，非实现缺陷，已更正] — 我曾用「源码字面量」找 `ARCHIVED_AT) IS NULL`。** 错在两点：大小写/括号不符（真实成品是 `b.archived_at IS NULL`），且**层次不对**——这条 SQL 是**拼装**出来的，源码里本就不该出现成品片段。正确判据（`T-040-verify-searchdao.txt` A/B）：`SearchDao.java:213-226` 的 `arm(...)` 每次在 `WHERE` 拼 `col("b", Branches.ARCHIVED_AT) + " IS NULL"`，`:163-187` **连调六次**（TITLE/ANCHOR/ANSWER/REASONING/PARTIAL_ANSWER/PARTIAL_REASONING），`:189` 用 `String.join(" UNION ALL ", arms)` 合成 → 成品里 `b.archived_at IS NULL` 恰好 6 次；既有 JVM 用例 `SearchDaoStatementTest.archivedBranchesAreNotSearchable`（`:74-75`，`assertEquals(6, countOf(SearchDao.statement(), "b.archived_at IS NULL"))`）已把该事实钉住。**结论：归档过滤存在，T-015 契约未破。** 规则已记入自检清单：判据对象是拼装产物时，必须对拼装结果取证，不得对源码做字面量匹配。
- **D1 [需求缺口，已上报工头] — 搜索覆盖缺席「用户问题」臂。** 六臂只有 标题/锚定原文/回答/推理/失败尝试部分正文 ×2；`SearchHit.Field` 恰好 6 个常量、**没有** QUESTION（所以**不是**「声明了却无生产者」的死代码，而是范围缺口），`SearchDao` 内 `user_content`/`question` 出现 0 次。BASE-12 要求搜索覆盖用户问题；T-040 的 Gaps ② 把它记为**刻意**缺口（`search_scope` 引导文案因此刻意不写「问题」）。**非本任务缺陷，工头将单开任务。**
- **F1 [P2，需用户裁定] — 规模越线。** 独立复算：新增 8 文件 = **395**；共享文件新增行（按 patch 记录）= **122（紧）..189（松）** → 合计 **517 .. 584**，**在每种严格读法下都超过 500 硬上限**；任务文件里**没有**用户批准的规模例外记录。按 `harness-verification.md`：verifier **对例外没有追认权**，只判「批准记录是否存在且由用户作出」。**建议**：请用户明确裁定（补批准并如实记为「事后追认」，或拆分）。**理由**：超过 hard cap 且无批准即为流程缺陷。**代价**：追认=留一条超预算记录；拆分=需返工并重排 T-043/T-045 的宿主串行。
- **F2 [P3，文档漂移] — 交付报告 `:113` 表述越界。** 该行写「跨主题搜**问题** / 回答 / 引用」，但检索范围无「问题」臂（D1）。与 T-041 的 D3 同源：**文档漂移，非实现缺陷**；建议把该行改为与 `search_scope` 一致的四项。
- **F3 [P3，记录不全] — `T-040-plan.txt` 与 `patch8.txt` 不一致。** plan 写「`SearchPresenter` 无需改动」，patch8 却把 `isStillReachable` 从 UI 线程移到 background（`SearchPresenter.java:112-115`）。行为有单测覆盖、且方向正确（更好），但任务正文/Handoff 未记这次语义变更。
- **F4 [观察，评审前提更正] — 「`layout-sw600dp` 镜像两处」这一前提在本仓不成立。** `home_search` **只有一处定义**（`screen_home_body.xml:35`）；手机外壳 `layout/screen_home.xml` 与平板外壳 `layout-sw600dp/screen_home.xml` 都 `<include>` **同一** `screen_home_body.xml`（搜索页同构：`layout-sw600dp/screen_search.xml` 也 include 同一个 `screen_search_body.xml`）。所以结构上不可能漂移，比「两处各改一遍」更强；`layout-sw600dp` 下并无 `screen_home_body.xml` 副本。**不是缺陷。**

### Gaps
- **G1** 未跑 Gradle：另一 lane 正持锁改 `app/src/main`（同工作树会互相覆盖 `app/build/test-results`）。因此本轮**没有**独立复跑单测、没有独立回读 JUnit XML；判据 5 采信实现者的自证证据（命令/cwd/退出码/判据行齐全）。**需要一次 Gradle 复跑**，由工头排期。
- **G2** 设备侧 `deferred — 用户自行装机验证（user decision 2026-09-21）`；`SearchUnreadReadingInstrumentedTest` 10 例 `blocked — 缺少设备`。
- **G3** 常驻门禁 E：`app-release.apk`（04:58:17）当前**旧于**最新 `app/src/main`（05:05:32）——这是并发施工的必然中间态，**不是 T-040 的失败**；T-040 收口重建 APK 后必须复跑（`T-040-verify-searchdao.txt` E 节）。
- **G4** git 无法充当「未改动」的证据：`data/.../dao/`、`app/.../search/` 在 git 中未跟踪，所以「T-015 契约未变」由当前源码 + 既有 JVM 用例支持，而非 `git diff`。

### Risks
- 屏幕侧过滤/重排导致口径漂移：已核 `SearchScreen`/`SearchPage` 0 处排序/过滤，唯一排序权威在 `SearchDao.java:112`。
- 第二套定位路径：已核带锚点跳转调用点全仓唯一且在宿主。
- 文档行号会因并行动工漂移：本报告引用的行号取自本轮实测源码；F2/F3 属文档面，需在收口时以当时源码为准复核。

## Handoff notes

- Completed: 屏幕（`screen/SearchScreen`）、页面对象（`search/SearchPage`）、标题端口（`search/BranchTitles`）、惰性开库装配（`wiring/SqliteSearchStore`）与四个布局（含 `layout-sw600dp` 镜像）落盘；首页 `home_search` 不再是死按钮；宿主 `MainActivity` 实现 `SearchPresenter.Sink`，`openAnchor` 走 T-042 的 `showChat(branchId, anchor)`——该缝由此获得第一个真实生产消费者；文档（`android/README.md`、交付报告）当前态已改。
- In progress: 无（实现与证据已完成，等独立 verifier）。
- Blocker: 无（T-042 已在 verifying，组合根与阅读位置路径均可用）。
- Unblock condition: 不适用。
- Next steps: 交独立 verifier；装 release APK 由用户自行实测（设备侧一律 deferred）。
- Watch-outs: 不要把「能不能搜到」的判断复制进屏幕；不要把定位判定复制进屏幕。本任务自报的两处缺口见下方 Gaps。
- Gaps: ①命中行的祖先路径未实现（原型 `renderSearch` 有，但需要一次树遍历，不在本任务 In scope 内，已在 `T-040-plan.txt` 的 DECISION 1 记为缺口）；②`search_scope` 引导文案只写「标题·锚定原文·回答·推理」，**刻意不写「问题」**——T-015 的检索语句没有用户问题臂，写了就是承诺搜不到的东西。
- Ordering reason: 验收项「点一条跳进对应分支并定位到命中位置」需要对话页存在阅读位置的读取与恢复路径，而那条路径属 T-042。若先做本任务，命中点只能打开分支并停在顶部，「定位」这一项无实现可依、也无法验收。因此 `depends_on` 补上 T-042，执行顺序为 T-042 → 本任务。

## Verification report — 收尾复核（verifier #3，2026-09-24）

- Verdict: PASS（针对 2026-09-22 verifier 报告残留项的收尾复核）。
- F1（规模 517..584 超 500 硬上限）→ 2026-09-24 用户**事后追认豁免**，例外登记见「TDD or sizing exception」小节。
- F2（交付报告 `:115` 「跨主题搜问题 / 回答 / 引用」措辞越界）→ 已修：改为「标题 / 锚定原文 / 回答 / 推理」四项并注明与 `search_scope` 一致、无「问题」臂。本轮 grep 确认旧措辞零命中。
- F3（`SearchPresenter.isStillReachable` 从 UI 线程移到 background 的语义变更未记录）→ 已补记于 REFACTOR 段；行为有单测覆盖、方向正确（不在 UI 线程做可达性 IO）。
- G1（需一次 Gradle 复跑）→ 已由本轮门禁满足：`./gradlew --no-daemon --console=plain check` EXITCODE=0（`artifacts/evidence/P3-B3-gates-2026-09-24.txt`）；Java-only guard `VERDICT: PASS (13 OK line(s))`（`artifacts/evidence/P3-B3-no-kotlin-2026-09-24.txt`）。
- D1（搜索无「用户问题」臂）→ 需求范围缺口，非本任务缺陷，已按 verifier 原判保留（工头单开任务的记录不变）。
- 设备侧 → 保持 `deferred — 用户自行装机验证（user decision 2026-09-21）`；`SearchUnreadReadingInstrumentedTest` 10 例 `blocked — 缺少设备`。
- G3（APK 旧于源码的中间态）→ 装机前须重建 release APK，该提醒保持有效。
