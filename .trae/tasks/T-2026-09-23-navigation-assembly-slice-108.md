# Treelearn Harness Task: 导航页装配切片（宿主、端口与入口，T-039 拆分 B）

id: T-2026-09-23-108
title: 导航页装配切片（宿主、端口与入口）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: true
depends_on: [T-2026-09-23-navigation-ui-slice-107.md, T-2026-09-21-real-data-path-wiring-032.md, T-2026-09-20-navigation-archive-delete-014.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: T-2026-09-21-039（分支导航页）按用户 2026-09-23 裁定拆分为两个切片分别判定规模；本文件是切片 B，承接全部既有文件改动与跨文件字段。
- Problem/background: 装配层使导航页可达——`wiring/NavigationWiring.java`(35，新增)、`AppContainer.navigationData()`、`MainActivity` 的 `NavigationSink` 宿主、三条入口（对话页 `chat_nav`、画布 `tree_nav`、首页 `home_archived`）、`NavigationViewState.Row` 增 `archivable`/`childCount`/`liveChildren()`、`strings.xml` 文案（含 `navigation_delete_body` 的 `%2$d` 占位）。合计 150 行（新增 35 + 既有文件改动 115）。
- Users/scenarios: 与父任务一致；本切片负责「进入/离开导航页、转发动作到 presenter、回写选择」。
- Expected outcome: 上述改动按拆分口径归属本切片；规模按 150 判定（<200，无警告）。
- Core flow and reversibility: 返回/遮罩关闭、删除确认前不改库、删除后选择回退——判定在 presenter/存储，本切片只接线。
- Inputs/outputs: 宿主实现 `NavigationSink`；`AppContainer` 暴露 `NavigationData`。
- State and exception handling: 同父任务。
- Constraints and trade-offs: Java-only；`NavigationWiring` 刻意不实现 `NavigationActions`（避免第二份归档/删除判定，父任务 Implementation notes 已记）。
- AI recommendation and rationale: 拆分按文件粒度——既有文件改动全部归 B，避免同一文件双重归属。
- User choice and custom additions: 用户 2026-09-23 拍板「拆分为两个子任务」。
- AI-autonomous decisions: `NavigationSink` 放 `MainActivity`（与全仓四个屏幕同形，父任务已记录偏差与理由）。
- User-confirmation decisions: 设备验证 deferred — 用户自行装机验证（user decision 2026-09-21）。
- Acceptance evidence: 共享父任务证据 `artifacts/evidence/T-039-*.txt`；接线的静态替代证据 `T-039-wiring.txt`（MainActivity.java:451/452、三处 findViewById）。
- Facts: 150 行 = 新增 `NavigationWiring.java` 35 + 既有文件改动 115（标识符匹配下界，见父任务 Gaps ⑦）。
- Assumptions: 拆分不改变任何生产代码内容。
- Unknowns: 同父任务（真实 SQLite 路径未执行）。
- Risks: `NavigationModification` 幻影标识符已在父任务 Deferred 更正中删除；接线属 Android View 层无 JVM 接缝，只能以静态替代证据 + 编译覆盖。
- Deferred: 真机观感、端到端接线执行、真实 SQLite 归档/删除路径（同父任务）。
- Non-goals: 不改 presenter/存储判定规则；不做屏幕与布局（切片 A）。
- Skipped non-critical questions and reason: 115 行是下界口径——父任务独立复核已记录「完整 diff 未重导」，判定不依赖它（150 远低于上限）。

## Goal

导航页装配层（wiring、端口、宿主、入口、Row 字段、文案）作为独立规模判定单元（150 ≤ 200 行）通过验收，页面在生产代码中有真实构造点与三条入口。

## Scope

### In scope
- 规模归属（回溯，不产生新 diff）：`wiring/NavigationWiring.java`(35 新增)、`AppContainer.navigationData()`、`MainActivity` `NavigationSink` 宿主方法与 `onBackRequested`/`onResume` 优先序、三条入口、`NavigationViewState.Row` 的 `archivable`/`childCount`/`liveChildren()`、`strings.xml` 导航文案。
- 验收本切片：生产源码原文扫描证明构造点与入口存在（沿用并复跑 `T-039-wiring.txt` 的扫描）。

### Out of scope
- `screen/NavigationScreen.java` 与 4 份布局（切片 A = T-2026-09-23-107）。
- 任何生产代码修改。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 无行为变更（治理拆分）；保留「首页归档按钮 enabled=false 已去除」这一运行事实。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 宿主 `MainActivity`（NavigationSink）；端口 `AppContainer.navigationData()`；消费者 presenter/存储。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯归属治理，无代码变更。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `T-039-wiring.txt`（构造点与入口的原文扫描）。
- Documentation surfaces checked: 父任务 Documentation impact record 已覆盖。
- Documentation files updated: 无需更新。
- Producer/consumer/sibling/compatibility paths checked: 同父任务。
- Superseded phrases/examples reverse-searched: 不适用。
- `documentation impact: none` reason and evidence, if applicable: 拆分不改变运行事实。
- Historical/PRD handling: 父任务记录不改写。

## Acceptance criteria

- [x] 切片规模 ≤ 200：150 行（新增 35 + 改动 115，依据 `T-039-sizing-final.txt` 与父任务 Handoff）。
- [x] 生产构造点与三条入口存在：`T-039-wiring.txt` 原文扫描（MainActivity.java:451 `new NavigationScreen(this)`、:452 presenter 构造、chat_nav/tree_nav/home_archived 三处 findViewById）。
- [x] 切片 A+B 合并复跑门禁全绿：`check`、Java-only guard 退出码 0（P3 批量验收以新鲜证据执行）。〔2026-09-24 复核 #3：`T-107-T108-gates-2026-09-24.txt`——check EXITCODE=0、guard PASS；wiring 原文扫描本日重跑命中全部锚点（MainActivity:858-860、AppContainer:225/:293、三入口 ChatScreen:214 / HomeScreen:69 / TreeCanvasScreen:71/78）〕
- [x] 真机观感记为 deferred — 用户自行装机验证（user decision 2026-09-21），不得写成 PASS。〔按既定口径维持 deferred，不判 FAIL〕

## TDD execution record

### RED — failing test first
- Test/scenario: 共享父任务 `NavigationRootGateTest`（`Row.archivable()`/`childCount()` 访问器即本切片字段）。
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests '*Navigation*' --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 1
- Observed failure: 12 处「找不到符号」，证据 `artifacts/evidence/T-039-red.txt`（共享）。
- Failure reason validated: 失败确由缺失行为导致（字段当时不存在）；回溯拆分不重跑 RED。

### GREEN — minimum implementation
- Implementation: 装配改动已存在（父任务 2026-09-22 03:30 落盘）；本任务为回溯归属。
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests '*Navigation*' --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 0
- Passing evidence: 26 例 0 失败，`artifacts/evidence/T-039-green.txt`（共享）；独立 verifier 冻结快照复跑全绿。
- Regression evidence: `T-039-check.txt`(EXITCODE=0)、`T-039-jvmtests.txt`（共享）。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 无重构步骤。

### TDD or sizing exception — only with prior user approval
- Exception type: 无例外。本切片 150 行未超任何上限。
- Reason: 不适用。
- User approval: 不适用（拆分裁定由用户 2026-09-23 作出，记录在父任务）。
- Alternative verification: 接线无 JVM 接缝，沿用父任务「编译通过 + 源码原文扫描」静态替代证据，已如实标注非端到端执行。

## Implementation notes

- 与 T-2026-09-23-107 由父任务于 2026-09-23 按用户裁定拆分产生，实现先于拆分存在，不产生新生产代码。
- 115 行为标识符匹配下界（父任务 Gaps ⑦），判定不依赖其精确性（余量充足）。
- 引用父任务证据文件时核对自带命令行与 EXITCODE 行，不凭记忆转写。

## Verification report

- Verdict: **PASS**（2026-09-24 复核 #3 收口，见下方「复核记录 #3」）
- Evidence: 规模 150（`T-039-sizing-final.txt`）；共享父任务证据（26 例 0 失败、六道门禁快照全绿、
  `T-039-wiring.txt` 静态替代证据）；合并门禁新鲜证据 `artifacts/evidence/T-107-T108-gates-2026-09-24.txt`
  （含本日 wiring 原文扫描：构造点/注入点/三入口全部命中）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言变更。
- Gaps: 同父任务（真实 SQLite 路径、端到端执行、真机观感 deferred — user decision 2026-09-21）。
- Risks: 无新增。

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- 规模：150 行 ≤ 200 软上限，无需例外。
- wiring 原文扫描（本日实跑，替代证据刷新）：`new NavigationScreen` MainActivity:858、presenter
  构造 :859-860（`container.navigationData()` 注入）、`SqliteNavigationStore` AppContainer:225 +
  暴露点 :293、三入口 `chat_nav`（ChatScreen:214）/ `home_archived`（HomeScreen:69）/ `tree_nav`
  （TreeCanvasScreen:71/78）全部命中。静态证据的边界（SUFFICIENT for 存在性、INSUFFICIENT for
  端到端执行）沿用父任务 verifier 判定，未夸大。
- 门禁合并复跑：`check` EXITCODE=0、guard PASS（`T-107-T108-gates-2026-09-24.txt`）。
- 与切片 A 同批收口；T-039 按拆分口径归档。
- 结论：验收 4 条全勾 → **verified**。

## Handoff notes

- Completed: 任务立案（2026-09-23，leader，依用户拆分裁定）。
- In progress: 待 P3 批量验收（与切片 A 同批）。
- Blocker: 无。
- Unblock condition: 与切片 A 同批复跑门禁全绿后转 verifying。
- Next steps: P3 验收时复跑 `check` + guard + wiring 原文扫描，回填 Verification report。
- Watch-outs: 归档/删除后 `MainActivity.navigationBranchId` 清空逻辑在 showHome/showChat/showTree 三处都存在，后续改动勿漏（父任务 Watch-outs ①）。
