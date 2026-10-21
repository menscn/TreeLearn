# Codex Harness Task: 分支导航读不出——未命名子分支与静默 catch

id: T-2026-10-18-016
title: 分支导航读不出——未命名子分支与静默 catch
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-android-phone-usability-requirement-017.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 64
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 真机上唯一多分支主题打开「分支导航」只显示「本机数据读不出来，列表保持上一次的样子，可以直接重试。」，且日志里一行原因都没有。根因是 `NavigationData.Node` 用 `Guard.text` 要求标题非空，而 `Branch.child(...)` 造的子分支**按设计出生时没有标题**（标题要等派生工件写回），于是 `SqliteNavigationStore.load()` 整批读失败。
- Users/scenarios: 学习者在对话里从某条回答分出子分支，还没生成标题就回到分支导航——列表应当显示这棵树，而不是把整棵树读成不可用。
- Expected outcome: 未命名子分支照常列出（空标签），导航不再因它整体失败；每一次读失败都在日志里留下一行可判定的原因。
- Core flow and reversibility: 放宽一个显示侧不变量 + 接一个诊断口；两处都可逐行回退，不迁移数据、不改 SQL、不动 AI 路径。
- Inputs/outputs: 输入 `Branch.title()`（可为 null）；输出 `NavigationViewState.Row.title`（永不为 null）。
- State and exception handling: 读失败仍走 `Phase.ERROR` + `Notice.STORE_UNREADABLE`（学习者看到的那句不变），差别是原因必须经 `reportUnreadable` 到达宿主；空标题与读失败是两件事，前者不得再被当成后者。
- Constraints and trade-offs: Java-only；不新增依赖；不新增用户文案（空标签沿用画布/弹层既有约定，「是否要给未命名分支一句占位文案」属产品判断，不在本片擅自决定）。
- AI recommendation and rationale: 与既有约定对齐而不是发明新规则——`ChildBranch`、`LayoutNode`、`TreeCanvasViewState` 三处都已把 null 标题归一为空串，导航是唯一的例外，因此它是缺陷而不是守卫太严。
- User choice and custom additions: 用户要求「手机版和 web 均可正常使用、一站到底、按片提交本地仓库」。
- AI-autonomous decisions: ① 归一位置放在 `Node` 构造器（与 `ChildBranch` 同形）而不是在 store 里补默认值；② 诊断口用 sink 的 default 方法，避免为一句日志新增一份 Android 类型依赖；③ 画布的两处 catch 一并接上，因为同一判据只覆盖一半会留下同类缺陷。
- Facts: 修复前后同一台设备、同一入口的截图与 `logcat` 均在证据文件；`./gradlew :app:testDebugUnitTest --tests "...NavigationPresenterTest"` 23 条全绿。
- Assumptions: 未命名子分支的标题会在其首次生成后被 `SqliteDerivedStore` 写回；本片不改这条链。
- Unknowns: 未命名分支在列表里长期为空是否可接受，需产品判断（已登记 Gaps）。
- Risks: 放宽不变量会让「标题缺失」在别处更隐蔽；缓解是同一改动里补上诊断口，缺失不再无声。
- Deferred: 写路径（归档/删除/折叠）其余静默 catch 的接线；D8 既有用例时序缺陷。
- Non-goals: 不做 Web 复刻、不动 SQL schema、不新增文案键、不改 `Branch.child` 的无标题设计。
- Skipped non-critical questions and reason: 未追问日志级别（`Log.w` 还是 `Log.e`），因为沿用 `MainActivity` 既有 `TreelearnUi` 约定即可。

## Goal

让分支导航在未命名子分支存在时仍可读，并让每一次读失败在日志里留下可判定的原因。

## Scope

### In scope
- `navigation/NavigationData.java`（`Node.title` 归一）、`navigation/NavigationSink.java`（`reportUnreadable` 默认方法）、`navigation/NavigationPresenter.java`（3 处 catch 接诊断口 + 私有 `report`）。
- `ui/TreeCanvasSink.java`、`ui/TreeCanvasPresenter.java`（2 处 catch 接同一形状的默认方法）。
- `MainActivity.java`（一个实现覆盖 Chat/Navigation/Canvas 三个口，日志标签仍为 `TreelearnUi`，字段名改 `scope=`）。
- 测试：`NavigationPresenterTest` 2 条新用例 + `rowOf` 辅助 + `RecordingSink.unreadable`；`TreeCanvasDoubles.FakeSink.reports` + `TreeCanvasPresenterTest` 1 条断言。

### Out of scope
- 未命名分支的占位文案；`SqliteDerivedStore` 的标题写回时机；写路径其余 catch；`connectedDebugAndroidTest`。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变「有未命名子分支时导航能否读树」；保留学习者的错误文案、归档/删除/选择回退等全部既有行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `SqliteNavigationStore.load`（`branch.title()` 可为 null）；消费者 `NavigationViewState.Row` → `NavigationScreen`；同族显示路径 `ChildBranch:21`、`LayoutNode:44`、`TreeCanvasViewState:151` 早已归一空标题，本片把导航对齐到它们。平板档：`values-sw600dp` 无独立导航布局，行为同一。
- Native bridge or independent-client compatibility/update requirements: 无跨端契约变化；Web 侧阶段一夹具的 payload 形状不受影响（未生成夹具）。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 分支导航对「未命名子分支」的读法；三处 sink 多了一个诊断口。
- Authoritative code/config/migration evidence: `NavigationData.Node` 构造器、`NavigationSink.reportUnreadable`、`MainActivity.reportUnreadable`。
- Documentation surfaces checked: `.codex/rules/android.md`（Java-only、no-kotlin guard）、`.codex/rules/observability.md`（日志不得含学习者内容）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`（夹具 payload 未涉及）。
- Documentation files updated: 本卡 + `artifacts/evidence/T-2026-10-18-web-android-slice-016-nav-untitled-branch.txt` + 两张真机截图；`README` 与规格无需改写（未改变流程或契约）。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact 的同族三处。
- Superseded phrases/examples reverse-searched: 全仓搜索 `Guard.text(title`，确认 `Node` 是唯一因此形状抛错的显示路径。
- `documentation impact: none` reason and evidence, 若适用：不适用。
- Historical/PRD handling: 无。

## Acceptance criteria

- [x] 存在 null 标题子分支时，`NavigationPresenter` 渲染 `READY` 且该分支在可见列表里，标签为 `""`；**2026-10-20 用户拍板改为显示「未命名分支」**（`NavigationScreen.displayTitle` + `strings.xml` 的 `navigation_unnamed`），本判据的期望标签由该决定取代，presenter 仍保留空标题，显示层做占位。
- [x] 读失败时原因恰到达诊断口一次，并点名异常类名（导航 3 处、画布 2 处 catch 全部接线）。
- [x] `NavigationPresenterTest` 23 条全绿；`:app` 全套件的既有红只剩 D8 两条且与本卡改动无因果。
- [x] Java-only（no-kotlin）守门 PASS。
- [x] 真机 `adb install -r` 后同一入口渲染分支列表，未命名子分支在列；`logcat -s TreelearnUi` 在读成功时保持空。2026-10-20 视觉复核：真机截图 `unnamed-branch-03-navigation-2026-10-20.png` 中该行显示「未命名分支」，同批 `home/chat` 截图与 XML 一并留证。

## TDD execution record

### RED — failing test first
- Test/scenario: 未命名子分支可读 + 原因必须到达诊断口
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.navigation.NavigationPresenterTest" --offline -q`
- Exit code: 1
- Observed failure: `23 tests completed, 2 failed`；`aBranchNotYetNamedIsStillReadableAndStaysInTheList | java.lang.IllegalArgumentException: title must not be null`；`anUnreadableStoreNamesTheCauseToTheSinkAndNotOnlyToTheScreen | AssertionError: the cause must reach the diagnostics port exactly once per failed read expected:<1> but was:<0>`
- Failure reason validated: 是——第一条与真机故障同形（`load()` 整批抛错 → `STORE_UNREADABLE`），第二条是「有故障屏但没有原因」的直接证据（旧构建 `logcat -s TreelearnUi` 为空）。

### GREEN — minimum implementation
- Implementation: `Node.title` 归一为 `""`；`NavigationSink`/`TreeCanvasSink` 各加 `default reportUnreadable`；两个 presenter 的 catch 调用私有 `report(scope, cause)`；`MainActivity` 一个实现覆盖三个口并把字段名改为 `scope=`
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.navigation.NavigationPresenterTest" --offline -q`
- Exit code: 0
- Passing evidence: `NavigationPresenterTest tests/skipped/failures/errors: ('23', '0', '0', '0')`
- Regression evidence: `./gradlew :app:testDebugUnitTest --offline -q` → `846 tests completed, 2 failed`，两条均为既有 D8（`ChatGenerationStageTest`），本卡未触碰 chat 路径；画布用例 `anUnreadableStoreKeepsTheLastCanvasAndSaysWhy` 新增 `sink.reports` 断言后仍绿。

### REFACTOR — optional
- Cleanup: 三个 sink 的诊断口保持同一签名，宿主只实现一次；`MainActivity` 的 Javadoc 改为说明它覆盖 Chat/Navigation/Canvas 三个口。
- Regression evidence: 上述 23 条与 846 条同轮复跑。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 规模按差分计 64 行生产代码（`git diff --numstat`：总 +119/-7，其中测试与双件 +55），远低于 200 目标；`count_production_lines.py` 对整文件给出 1613 是因为它统计被改文件的全文行数，不代表本片规模。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 归一位置选在 `Node` 构造器：它和 `ChildBranch`、`LayoutNode` 是同一类「为显示而命名一个分支」的对象，规则应当同形，而不是在 store 里造第四份。
- `Guard.text` 的调用被移除而非替换成 `textOrNull`：`Guard` 的错误通道规则不受影响——null 标题在这里不是畸形输入，而是设计态。
- 诊断口传的是 id 与异常类名+message，绝不传学习者内容；失败的读本来就没读到消息体。

## Verification report

- Verdict: PASS（独立复核，2026-10-20：六条验收全部有读数；原「空标签」判据由用户当日拍板的「未命名分支」占位取代，真机截图可见。）
- Evidence: 原证据 `artifacts/evidence/T-2026-10-18-web-android-slice-016-nav-untitled-branch.txt` 六块（BEFORE 真机屏 + 空日志、RED、GREEN、全套件、no-kotlin、AFTER 真机列表）；2026-10-20 终态 = 真机 `adb install -r` 后截图 `artifacts/evidence/unnamed-branch-03-navigation-2026-10-20.png` + `unnamed-branch-03-navigation-2026-10-20.xml` 显示该行「未命名分支」，全量仪器套件 `android-instrumented-2026-10-20.xml` 301 例 0 失败（测试前已备份设备数据、测试后恢复）。设备 AWSS013807000356，`adb install -r`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言被删；`Node` 的 null 拒绝被替换为「归一 + 用例证明可读」，等价覆盖由 `aBranchNotYetNamedIsStillReadableAndStaysInTheList` 承担；显示层占位有真机截图证据，presenter 的空标题语义未变。
- Risks: 占位文案现在是资源键 `navigation_unnamed`，画布与弹层若也要占位，应复用同一键而不是各写一份。
- Gaps: ① 未命名分支的占位文案已由用户拍板并落地（本项关闭）；② 写路径其余静默 catch 未接诊断口（独立问题，不属本片）；③ D8 两条既有红已由 018 闭合，`:app` JVM 848/0、2026-10-20 仪器套件 301/0；④ 仪器套件已于 2026-10-20 全量跑过（备份—测试—恢复流程），本条关闭。

## Handoff notes

- Completed: 真机故障已消除并有前后对照；诊断口在导航与画布两侧成立。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: D8（`ChatGenerationStageTest` 两条时序红）→ ROADMAP D7 其余真机红 → 阶段一夹具采集（Web 逐屏 1:1 的前置）。
- Watch-outs: ① 任何「读不出来」的屏现在都应有一行 `TreelearnUi` 日志，没有就说明那条路径还没接诊断口；② 验证只用 `adb install -r`，跑 `connectedDebugAndroidTest` 会卸载应用并清掉 API Key；③ 不要把空标题重新变成硬失败。
