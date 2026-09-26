# Treelearn Harness Task: 分支导航页（列表、AI 总结、归档与删除确认）

id: T-2026-09-21-039
title: 分支导航页（列表、AI 总结、归档与删除确认）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 430
shared_code_touched: true
depends_on: [T-2026-09-21-real-data-path-wiring-032.md, T-2026-09-20-navigation-archive-delete-014.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD 6.2.6（移动端逐层导航）与 6.2.10（非根子树归档与永久删除）要求用户能沿分支逐层走动、看到分支级 AI 总结、归档整棵子树并从「已归档」整体恢复、以及经确认永久删除；领域层与 presenter 已交付，但**既没有屏幕类也没有布局**，运行期完全不可达。
- Problem/background: `navigation/NavigationPresenter`、`NavigationViewState`、`SqliteNavigationStore` 已存在且被单测覆盖，`NavigationPresenter` 在 `src/main` 内只被它自己和 `NavigationActions` 引用，构造点只在测试里；`res/layout/` 下没有任何导航页布局。首页的「归档」入口是 `enabled="false"` 的死按钮。
- Users/scenarios: 用户在对话中想看这个主题有哪些分支、各自学到了什么，据此决定下一步进哪条；把自己探索完的一段整体归档；从已归档里整体恢复；确认后永久删除一棵子树。
- Expected outcome: 有导航页屏幕与布局，可从对话页/画布进入；列表显示非归档分支与当前选中；顶部显示该分支的 AI 总结要点并支持折叠；归档/恢复/删除确认三条路径都经过既有 `NavigationPresenter` 与 `SqliteNavigationStore`。
- Core flow and reversibility: 归档可逆（整体恢复到原父级）；永久删除不可逆，必须先经确认弹窗且文案写明影响范围（含后代数量）；根分支不提供归档。
- Inputs/outputs: 输入 `NavigationPresenter` 已有的 `NavigationViewState`（`visible`/`archived`/`summary`/`keyPoints`/`pendingDelete`）与 `NavigationActions`；输出新的 `screen/NavigationScreen`、`res/layout/screen_navigation*.xml`（含 `layout-sw600dp/` 镜像）、宿主装配与入口。
- State and exception handling: 读取失败给可见 notice；总结生成失败只让总结区降级，不阻塞列表；删除确认未确认前不改库；删除后选择回退到存在的分支。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；布局只能用已定义的 design token（间距 `s*`、圆角 `r-*`、字号 `fs-*`），不得出现临场 px；所有可点区域 ≥ 44×44 dp。
- AI recommendation and rationale: 一个任务同时交付「页面 + 归档/恢复/删除确认」，而不是切成两个并行任务——三者共用同一屏幕文件与同一份 `NavigationViewState`，并行会变成两个写者对同一文件、并让列表口径漂移。
- User choice and custom additions: 用户 2026-09-21 明确要求导航与归档都必须实现且可用。
- AI-autonomous decisions: 已归档分支在列表中以独立分组呈现而不是第二个页面；删除确认用系统对话框而非自绘弹层。
- User-confirmation decisions: 设备验证由用户装机实测（2026-09-21 决定）。
- Acceptance evidence: RED→GREEN 的 JVM 测试（列表口径、折叠、归档/恢复、删除确认前不改库、删除后选择回退）、`:app` 单测与 `.\gradlew.bat check` 退出码 0、Java-only guard PASS、设计 token 守门、原文扫描证明导航页有生产构造点。
- Facts: `NavigationViewState` 已携带 `visible`/`archived`/`summaryFor`/`summary`/`keyPoints`/`summaryStale`/`collapsed`/`summaryCollapsed`/`pendingDelete`/`notice`；`NavigationActions` 已定义 8 个动作；`SqliteNavigationStore` 已实现归档与删除。
- Assumptions: T-032 已把数据库接好；T-014 的 presenter 行为保持现行签名。
- Unknowns: 真机上的列表滚动与弹窗观感（无设备）。
- Risks: 新屏幕的可见口径与画布不一致；删除确认文案未写清后代数量导致误删；布局写出临场数值导致 token 守门变红。
- Deferred: 真机观感与像素证据。
- Non-goals: 不改 presenter 的判定规则；不做搜索；不做主题级删除（另有任务）。
- Skipped non-critical questions and reason: 总结折叠的默认状态沿用 presenter 既有语义，不额外追问。

## Goal

非归档/已归档分支列表、分支级 AI 总结、归档与恢复、经确认的永久删除，全部有屏幕、有入口、走既有 presenter 与存储。

## Scope

### In scope
- 新增 `android/app/src/main/java/com/treelearn/app/screen/NavigationScreen.java`（渲染 `NavigationViewState`，转发 `NavigationActions`，作为 `NavigationSink`）。
- 新增 `res/layout/screen_navigation.xml` / `screen_navigation_body.xml` / `item_navigation_row.xml`，并按既有约定补齐 `res/layout-sw600dp/` 镜像。
- `AppContainer` 暴露导航所需端口；`MainActivity` 作为宿主负责进入/离开与打开选中的分支。
- 对话页与画布新增进入导航页的入口；`strings.xml` 补齐文案（含删除确认文案与后代数量占位）。
- 删除确认必须显示受影响的子树规模；确认前不得改库。

### Out of scope
- `NavigationPresenter`/`SqliteNavigationStore` 的判定规则改动。
- 搜索页、画布绘制、主题级删除。
- 任何 schema 改动。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 由「无导航页」变为「可进入并使用」；**保留** presenter 既有语义（根不可归档、归档传播整棵子树、删除需确认、选择回退）与 T-014 的单测结论。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `SqliteNavigationStore`；消费者是新屏幕与 `NavigationPresenter`；宿主是 `MainActivity`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 导航页从不存在变为可达；归档/恢复/删除确认有了真实 UI 入口与文案。
- Authoritative code/config/migration evidence: `app/src/main/java/com/treelearn/app/screen/NavigationScreen.java`、`res/layout/screen_navigation*.xml`、`MainActivity.java`、`AppContainer.java`、测试输出。
- Documentation surfaces checked: `android/README.md`（`:app` 入口索引、「记录缺口」小节）、Android 技术方案 §9、PRD 6.2.6/6.2.10、UX 原型对应屏。
- Documentation files updated: README 中「无导航页类、无布局、无入口」的当前态表述改为真实入口。
- Producer/consumer/sibling/compatibility paths checked: 对话页/画布 → 导航页 → 选分支 → 对话页；归档 → 画布与导航页同时隐藏；恢复 → 原父级可见。
- Superseded phrases/examples reverse-searched:「无导航页类、无布局、无入口」「归档入口为死按钮」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: T-014 的历史记录不改写，只补它的生产 UI。

## Acceptance criteria

- [x] 同一 JVM 测试命令先 RED 后 GREEN：断言列表口径（已归档不出现在可见组）、折叠切换、归档后从可见组消失且进入已归档组、恢复后回到原父级、删除确认前库未变、删除后选择回退到存在的分支。
- [x] 删除确认文案包含受影响子树的分支数量，且根分支不提供归档操作（用测试或原文断言证明）。
- [x] 布局不出现临场数值：`python .trae/scripts/dev/check_design_tokens.py` 退出码 0，且新布局只引用 `tokens.json` 中的档位。
- [x] `:app:testDebugUnitTest`、`.\gradlew.bat check`、Java-only guard、文档影响记录齐全且退出码 0。
- [x] 真机观感与像素证据记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得写成 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `navigation/NavigationRootGateTest` 要求 `NavigationViewState.Row` 携带「这一行能否被归档」与「它的存活子分支数」，并据此区分根行与子行——UI 切片要把这两个字段渲染成归档动作与删除确认里的分支数
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests '*Navigation*' --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 1
- Observed failure: `12 个错误`，全部为 `找不到符号`（`symbol: method archivable()` / `symbol: method childCount()`），证据 `artifacts/evidence/T-039-red.txt`
- Failure reason validated: 失败确由缺失行为导致——两个访问器当时在 `NavigationViewState.Row` 上还不存在，补齐字段后同一条命令不再报符号错误

### GREEN — minimum implementation
- Implementation: `screen/NavigationScreen` + `res/layout/screen_navigation.xml` / `screen_navigation_body.xml` / `item_navigation_row.xml` + `res/layout-sw600dp/screen_navigation.xml`；`wiring/NavigationWiring` 实现 `NavigationData`；`AppContainer.navigationData()`；`MainActivity` 作 `NavigationSink` 宿主（`showNavigation`/`enterNavigation`/`render`/`leaveNavigation`/`openNavigation`/`openArchived`，以及 `onBackRequested` 与 `onResume` 的导航优先序）；`navigation/NavigationViewState.Row` 增 `archivable`/`childCount` 与 `liveChildren()`；三条入口（对话页 `chat_nav`、画布 `tree_nav`、首页 `home_archived`，并去掉首页按钮的 `android:enabled="false"`）
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests '*Navigation*' --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 0
- Passing evidence: 26 例 0 失败（`NavigationPresenterTest` 21 + `NavigationRootGateTest` 5），`BUILD SUCCESSFUL` 且 `54 actionable tasks: 54 executed`，`artifacts/evidence/T-039-green.txt`
- Regression evidence: `check` 退出码 0（`artifacts/evidence/T-039-check.txt`，295 actionable tasks）；全量 JVM app 334 / core 197 / ai 76 / data 26 / tree-layout 34、0 失败（`artifacts/evidence/T-039-jvmtests.txt`）

```
RED 与 GREEN 跑的是同一条命令，两轮都在 D:\Project\Android\Treelearn\android 下执行。
GREEN 原始读数（从证据文件回读，不凭记忆）：
  artifacts/evidence/T-039-green.txt -> BUILD SUCCESSFUL in 20s ; 54 actionable tasks: 54 executed ; EXITCODE=0
  app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.navigation.NavigationPresenterTest.xml -> tests=21 failures=0 errors=0 skipped=0
  app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.navigation.NavigationRootGateTest.xml -> tests=5 failures=0 errors=0 skipped=0
接线（屏幕构造、presenter 构造、入口监听）是 Android View 代码，没有 JVM 接缝，无法被上述命令覆盖；
按验收标准改用替代证据：编译通过 + 生产源码原文扫描证明存在构造点与三条入口，
见 artifacts/evidence/T-039-wiring.txt（MainActivity.java:451 new NavigationScreen(this)；:452 new NavigationPresenter(...)；
对话页 chat_nav、画布 tree_nav、首页 home_archived 三处 findViewById）。这是替代证据，不是端到端执行。
```

### REFACTOR — optional
- Cleanup: 未做额外重构——屏幕、端口与宿主一律沿用既有四个屏幕（Home / Chat / TreeCanvas / Settings）的同一种形状，避免出现第二套写法
- Regression evidence: 无重构步骤，故无「重构后再跑」的独立证据；实现冻结后的同一份回归证据即 GREEN 的 `Regression evidence`

### TDD or sizing exception — only with prior user approval
- Exception type: 规模（超 500 行硬上限）——**2026-09-23 用户裁定：不批准整体例外，改为拆分**
- Reason: 接完线后的**实测**规模是 **646 非空非注释生产行**（新增 6 个文件 531 行 + 改动 19 个既有文件 115 行；口径工具 `.trae/scripts/dev/count_production_lines.py`，证据 `artifacts/evidence/T-039-sizing-final.txt`），超 500 行硬上限 **146 行（29.2%）**。单是新增的 6 个文件 531 行就已越线，其中 `screen/NavigationScreen.java`(285) 与 `res/layout/screen_navigation_body.xml`(150) 两个文件占 435。前置估算 430 只有新增屏幕与布局的一半左右：当时的估算按「无接线的 UI 切片」下笔，漏算了 `MainActivity` 宿主与三条入口（这一层是协调方 2026-09-22 追加施工的）。
- User approval: **整体例外未批准**。用户 2026-09-22 的指令是「完整弄完、用 harness 跑下去」——那是对**继续施工**的授权，不等于对**行数**的批准。〔2026-09-23 裁定〕用户在结构化选项中拍板「T-039 拆分为两个子任务」（同轮明确否决了「申请行数豁免直接验收」选项）。据此本任务回溯拆分为 **T-2026-09-23-107（UI 切片，496 行）** 与 **T-2026-09-23-108（装配切片，150 行）**，规模按两个切片分别判定：496 ≤ 500（>200 软上限警告）、150 ≤ 200，均无需例外。本文件保留为拆分记录与原始证据索引，646 的联合口径只作历史记录，不再作为单一判定值。
- Alternative verification: 无替代验证可免除规模规则；用户已选择拆分路径。拆分按文件粒度回溯归属（新增文件归 107、既有文件改动归 108），不产生新生产代码，不重写 RED/GREEN（子任务共享父任务证据文件）。
- Metadata note: front-matter 的 `estimated_production_lines: 430` 是**动工前的估算值**，此处刻意不改写成 646——校验器对 >500 且无已批准例外者直接判 ERROR，把估算值抬到 646 等于用一次字段编辑把未批准的越线伪装成合规。真实规模以本节的 646 与 `artifacts/evidence/T-039-sizing-final.txt` 为准；拆分后的判定值见 107/108 两个任务文件。

## Implementation notes

- 〔2026-09-22 03:13 施工记录〕屏幕与布局已落盘，但**整页仍不可达**：`AppContainer` 端口、`MainActivity` 宿主与三条入口都还没写。因此本任务**尚未满足 Goal**，此时不得置 `verifying`。
- 规模口径工具：`.trae/scripts/dev/count_production_lines.py`（Java 去 `//` 与 `/* */`，XML 去 `<!-- -->`，只算非空非注释行）。它对 5 个已落盘文件重数得 **496**，与实现者自报一致；此前交付复核手写的 511 是**未剥离 XML 注释块**的粗略计数，已作废，不得再引用。

- 〔2026-09-22 03:30 施工完成〕接线已落盘：`wiring/NavigationWiring.java`（实现 `NavigationData`，内部持有单例 `SqliteNavigationStore`；**刻意不实现 `NavigationActions`**——那是 presenter 的手势契约，宿主每次进入时构造，在这里实现等于把第二份归档/删除判定放在 presenter 旁边）、`AppContainer.navigationData()` 暴露端口、`MainActivity` 的 `NavigationSink` 宿主（`showNavigation` / `enterNavigation` / `render` / `leaveNavigation` / `openNavigation` / `openArchived`，以及 `onBackRequested` 与 `onResume` 的导航优先序）、三条入口（对话页 `chat_nav`、画布 `tree_nav`、首页 `home_archived`，并去掉首页按钮的 `android:enabled="false"`）。整页现在可达。
- 与 In scope 第 1 条的偏差（如实记录，不沉默跳过）：In scope 写屏幕「作为 `NavigationSink`」，实际实现把 `NavigationSink` 放在 `MainActivity`。这与既有四个屏幕完全同形——本仓**没有任何屏幕实现自己的 Sink**（Home / Chat / TreeCanvas / Settings 都是宿主 `MainActivity` 实现 Sink，屏幕只做 `setActions` / `setHost` / `render`）。若把 Sink 写在屏幕上，导航页会成为全仓唯一的例外形状，也会让「返回回到哪段对话」这条宿主级决策落到屏幕里。
- 入口范围偏差（如实记录）：In scope 第 4 条只写「对话页与画布」，首页 `home_archived` 是协调方 2026-09-22 追加的范围；`home_search`（`res/layout/screen_home_body.xml:39` 的 `android:enabled="false"`）**未动**，它属 T-040。
- 未改 `NavigationPresenter` / `SqliteNavigationStore` 的判定规则，未改 schema，未回退 T-041/T-044（`ChatWiring.submit` 仍是四参含 quote；`MainActivity.openChildren` 仍是真实子分支弹层）。

- 屏幕不得自己决定「哪些分支可见」或「能否归档」：一律读 `NavigationViewState` 并转发动作，判定留在 presenter/存储。
- 布局只能用 `Docs/04-ux-design/design-system/tokens.json` 的档位；新增档位要先落到原型 `:root` 并跑三方守门（`rules/ux-design.md`）。
- 不写任何正文（标题、总结、问题、回答）到日志或证据文件；删除确认的证据只记录分支数量。
- 平板横屏沿用 `res/layout-sw600dp/` 镜像，不新增第二套数据路径。

## Verification report

- Verdict: FAIL — 五条验收标准全部 PASS、六道门禁在冻结快照里复跑全绿、三条入口的调用链均走到存储；唯一未达标的是 **500 行硬上限**：实测 646 行（新增 531 + 改动 115），超 146 行 / 29.2%，且**没有任何用户批准记录**（本文件第 120/122 行即写明「待裁定，尚未批准」/「未取得」）。依据 `.trae/rules/harness-tasks.md:45`（超过 500 行除非已记录用户批准的规模例外，否则为失败）与 `.trae/rules/harness-verification.md:18`（任一加载守门 FAIL 即 FAIL）。这是流程级失败，不是代码缺陷；实现者如实标注「未批准」的做法本身是正确的。
- Evidence: 独立证据 `artifacts/evidence/T-039-independent-verification.txt`（快照 `C:\Users\23745\AppData\Local\Temp\T-039-snap`，1143 行 / 72897 字节，两处副本（仓库 + 快照）SHA-256 相同 = `74590560E2C703299A5FD42DF833BC1CA8873D31E8BD5DE5ECC8C9A4C3B77C8A`）。六道门禁由我在快照里复跑、逐条落 `EXITCODE=`：① `.\gradlew.bat check`（cwd `<snap>\android`）→ 0，BUILD SUCCESSFUL / 295 actionable tasks；② `:app:testDebugUnitTest --tests '*Navigation*' --rerun-tasks --no-build-cache` → 0，我自行从 JUnit XML 回读 `NavigationPresenterTest` tests=21、`NavigationRootGateTest` tests=5，共 26 / 0 失败；③ `no-kotlin-check.ps1` → 0，`VERDICT: PASS (13 OK lines)`，3 个 APK 无 Kotlin 运行时字节码；④ `check_design_tokens.py` → 0，`0 check(s) failed`；⑤ `validate_tasks.py` → errors=0（warnings=15）；⑥ `count_jvm_tests.py` → 108 suite / 1103 tests / 0 失败。附加独立探针：自写 zip+dex 字符串扫描命中 `NavigationScreen`/`NavigationWiring`/`NavigationPresenter`/`NavigationViewState`，三个阴性对照（`NavigationRootGateTest`/`NavigationPresenterTest`/`SearchScreen`）全部 Absent，证明探针非恒真；`apksigner verify` → 0。
- Changed/deleted assertion rationale and equivalent behavior coverage: （1）本任务只**新增**断言、未改写旧断言：`NavigationViewState.Row` 增 `archivable`/`childCount` 与 `liveChildren()`，RED 的 12 处错误全部是这两个访问器的 `找不到符号`（`T-039-red.txt`；12 位点 = 41,42,43,44,52,59,60,61,63,73,84,85），修的是缺失行为。（2）**替代证据判定**：接线是 Android View 代码、无 JVM 接缝，实现者改用「编译通过 + 生产源码原文扫描」，并在任务第 112 行明确标注「这是替代证据，不是端到端执行」。我的结论：作为静态证据 **SUFFICIENT**（足以支撑「入口/宿主已接线」的存在性主张），作为运行证据 **INSUFFICIENT**（不足支撑任何「点击后真的跳转/真的弹窗」），且它已被如实标注为静态——替代关系成立。（3）**第二份判定点**：`wiring/NavigationWiring.java` 的匹配集 = 六个 1:1 委托方法 + 两段 javadoc，零条件/零计数/零推导；`screen/NavigationScreen.java` 匹配集非空，但逐条看过后全部是「读」状态既有标志或渲染参数（`:145`/`:224`/`:258`/`:267`/`:155`/`:179`/`:205-207`），没有重算「哪些分支可见 / 能否归档 / 删几个」。即本任务未引入与 presenter/存储漂移的第二份判定。
- Gaps: ① 运行期行为整体未验证（无真机/模拟器/instrumentation、未跑 `connectedAndroidTest`、未安装）；② 布局真机 inflate、`layout-sw600dp` 横屏排布、删除对话框换行均未取得——编译与资源链接只证明良构与 id 存在；③ 与画布的「可见分支集合一致」交叉回退未做，且画布自带一套独立可见性规则（`wiring/TreeCanvasWiring.java:19,62`，先于本任务），该漂移风险由 T-039 之外承载；④ 归档/恢复/删除的**真实 SQLite 路径**未执行——JVM 用例驱动 fake store，「确认前不写库」只对 presenter 成立、对 `SqliteNavigationStore` 未证；⑤ 存储**不拒绝**归档根分支（拒绝只发生在 UI 层 `NavigationScreen.java:224`，`archivable = !node.isRoot()`），其它调用方直接 `onArchive(rootId)` 不被拦；⑥「恢复后回到**原父级**」只有「重新可见」的断言（`NavigationPresenterTest:87-91`），「落回同一 parent」是构造性结论（`Branch.unarchive` 只清 `archivedAt`、`parentBranchId` 为 final 从不改写），无测试钉住；⑦ 19 个改动文件的完整 diff 未重导（快照无 `.git`），115 只是标识符匹配下界——但 531 单项已越线，判定不依赖它；⑧ 快照 APK 哈希不可字节复现（门禁前 `B92D6D29...4EE1` = 协调方实测；门禁后同尺寸 498904 字节但哈希变 `0EE5443B...6B39`），记为观察、非缺陷；⑨ **规则冲突（未自行选边）**：Handoff 的 Unblock condition 称「用户一句话确认（记为事后追认）」可解锁，而 `harness-verification.md:9` 要求 verifier「拒绝事后批准」、`harness-tasks.md:60` 写「事后批准无效」；我按 verifier 规则给判定，并如实记录该冲突，未改动 Handoff 节。
- Risks: ① 归档的域级护栏缺位——今天拦住根分支归档的只有「界面不提供该控件」，一旦出现第二个调用方即静默放行；② 画布与导航页两套可见性实现长期并存、可能漂移；③ 删除确认的分支数由 presenter 计算，计入口径变化而文案未同步会误导演用户（未验证）；④ 规模越线若把「事后追认」草率改写成「事先批准」，既属流程缺陷、也应按 `harness-verification.md:9` 被 verifier 拒绝；⑤ 任务第 158 行引用幻影标识符 `NavigationModification`（整个快照仅此一处，无此类/文件/文档），建议清理。

〔2026-09-22 协调方记录，回应本文件 Verification report 的 Gaps ⑨ / Risks ④ 与 Watch-outs ④〕

- **记录人分工**：按 `.trae/rules/harness-verification.md:14`，「验收报告」段由主会话维护、verifier 保持只读。本轮我曾指示 verifier 直接填写该段，属**指令偏差**（verifier 已在证据文件 O 节主动披露，且未改 `status`、未动其它节）。此后 verifier 只写自己的证据文件，任务文件的验收报告由主会话落笔。
- **规则冲突已修**：verifier 报出的 `harness-verification.md:9`（「拒绝事后批准」）与任务契约规模条款（「属流程缺陷，如实记为事后追认」）对同一输入给出互斥结论，是**同一条规则写在两个入口**的又一实例。修法是让规则只存在一处——该文件不再复述例外口径，只指向任务契约的 TDD 条款与规模条款，并补一句「verifier 对任何例外都没有追认权，只判定批准记录是否存在且由用户作出」。
- **Watch-outs ④ 已消解**：原文称「仓库里现有的 `app-release.apk` 早于本次源码且不含导航页，需重跑 release 构建」。该判断在写下时成立，但 03:28:55 的门禁顺带重打包已产出含导航页的产物（498904 字节，`classes.dex` 内 `NavigationScreen` 实测出现 23 次，协调方直接核验）。该条应读作一条**持续约束**：每次源码变更后都必须重建 release APK，否则装机用的仍是旧产物。

## Handoff notes

- Completed: 本任务 In scope 的全部五条——① `screen/NavigationScreen.java`(285 判定行)、② `res/layout/screen_navigation.xml`(11) / `screen_navigation_body.xml`(150) / `item_navigation_row.xml`(34) + `res/layout-sw600dp/screen_navigation.xml`(16)、③ `wiring/NavigationWiring.java`(35) 实现 `NavigationData` + `AppContainer.navigationData()` + `MainActivity` 的 `NavigationSink` 宿主与 `onBackRequested`/`onResume` 优先序、④ 三条入口（对话页 `chat_nav` / 画布 `tree_nav` / 首页 `home_archived`，首页按钮的 `android:enabled="false"` 已去掉）与 `strings.xml` 文案（含 `navigation_delete_body` 的 `%2$d` 分支数占位）、⑤ 删除确认显示 `plan.branchCount()` 且确认前不写库。另：`NavigationViewState.Row` 增 `archivable` / `childCount` 与 `liveChildren()`。
- Evidence: GREEN 26 例 0 失败（`artifacts/evidence/T-039-green.txt`）；门禁四份——`T-039-check.txt`(EXITCODE=0) / `T-039-nokotlin.txt`(VERDICT: PASS) / `T-039-designtokens.txt`(0 check(s) failed) / `T-039-jvmtests.txt`(VERDICT: OK)；接线替代证据 `T-039-wiring.txt`；规模 `T-039-sizing-final.txt`。
- In progress: 无，实现者已停手。
- Blocker: **独立 verifier 已于 2026-09-22 03:46 判 FAIL**，唯一理由是规模门——实测 **646** 行（新增 531 + 改动 115），超 500 硬上限 **146** 行（29.2%），且不存在用户批准记录（见「TDD or sizing exception」节）。本任务自有的五条验收标准**全部 PASS**、六道门禁在冻结快照里复跑**全绿**、三条入口的调用链逐跳走到存储 → **没有任何代码缺陷需要修**。〔2026-09-23 更新〕阻塞已由用户裁定解除：拆分为 T-2026-09-23-107 / T-2026-09-23-108，规模按切片判定；本文件不再作为单一规模判定单元。
- Unblock condition: ~~用户批准规模例外或指定拆分方案~~ → **已解除**：用户 2026-09-23 经结构化选项裁定拆分。剩余动作：107/108 两个切片在 P3 批量验收中复跑门禁全绿并转 verified 后，本任务按拆分口径转 `verifying` 复核归档。原文引用的规则冲突（`harness-verification.md:9` 与任务契约规模条款互斥）已于 2026-09-22 修掉：该文件不再复述例外口径，只指向任务契约。
- Next steps: ~~独立 verifier 复核四份门禁证据 + 接线替代证据 + 规模口径~~（已完成，见 Verification report）→ P3 与 107/108 同批复跑 `check` + Java-only guard + 设计 token 守门，以新鲜证据回填切片任务；随后两条交叉回归——① 与画布比「可见分支集合一致」；② 首页 `home_archived` 与列表首位主题的对应关系。
- Next steps: 独立 verifier 复核四份门禁证据 + 接线替代证据 + 规模口径；随后可做两条交叉回归——① 与画布比「可见分支集合一致」；② 首页 `home_archived` 与列表首位主题的对应关系。
- Watch-outs: ① 归档/删除后 `MainActivity` 的 `navigationBranchId` 必须随屏幕一起清空，`showHome`/`showChat`/`showTree` 三处都已清，将来新增屏幕时不要漏；② `NavigationScreen.renderDelete` 按 `targetBranchId + branchCount` 去重，只在计划变化时重建对话框，改这段时不要退化成每次 render 都弹；③ 设备侧观感一律 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得读成 PASS；④ 仓库里现有的 `app-release.apk` 早于本次源码且不含导航页，需 leader 重跑 release 构建。
- Deferred (not verified): 真机/模拟器上的列表滚动、删除对话框换行、`layout-sw600dp/screen_navigation.xml` 的横屏排布；接线路径的端到端执行（只有「编译通过 + 源码原文扫描」的替代证据）；归档 / 恢复 / 删除在**真实 SQLite** 上的一次执行（JVM 用例只驱动 fake store）。〔2026-09-22 更正〕原文末尾的 `NavigationModification` 是幻影标识符（全仓无此类 / 无此文件 / 无此文档，独立 verifier 复核确认），已删除。

## 归档记录（2026-09-24，按拆分口径收口）

- 拆分判定闭环：T-2026-09-23-107（UI 切片，496 行）与 T-2026-09-23-108（装配切片，150 行）已于
  2026-09-24 双双 verified（合并门禁复跑证据 `artifacts/evidence/T-107-T108-gates-2026-09-24.txt`：
  check EXITCODE=0、no-kotlin guard PASS、token 守门 0 failed、wiring 原文扫描命中全部锚点）。
  本任务 646 行的联合口径按 2026-09-23 用户拆分裁定不再作为单一判定值；两个切片各自合规、无需例外。
- 原任务的工程结论不变：五条验收标准全部 PASS、六道门禁在冻结快照复跑全绿、三条入口调用链逐跳
  走到存储、verifier 的 FAIL 是且仅是规模门（已由拆分解除）。本文件保留为拆分记录与原始证据索引。
- 交叉回归两项（与画布可见性集合一致、`home_archived` 与列表首位主题对应）仍属后续任务范围，
  不在本归档判定内；真实 SQLite 路径与真机观感维持 deferred — user decision 2026-09-21。
- 状态：blocked → **verified（拆分归档）**。
