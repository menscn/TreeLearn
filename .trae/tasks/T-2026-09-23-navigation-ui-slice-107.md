# Treelearn Harness Task: 导航页 UI 切片（屏幕与布局，T-039 拆分 A）

id: T-2026-09-23-107
title: 导航页 UI 切片（屏幕与布局）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 496
shared_code_touched: false
depends_on: [T-2026-09-21-real-data-path-wiring-032.md, T-2026-09-20-navigation-archive-delete-014.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: T-2026-09-21-039（分支导航页）实现已完成、五条验收标准全 PASS、六道门禁独立复跑全绿，唯一未达标项是 646 非空非注释生产行超 500 硬上限（超 146 行 / 29.2%）且无用户批准。用户 2026-09-23 经结构化选项裁定：**不批准整体规模例外，改为拆分为两个切片任务分别判定规模**，本文件是切片 A。
- Problem/background: 原任务按「UI 切片 + 装配切片」两个文件边界回溯拆分。本切片承接其中全部新增文件：`screen/NavigationScreen.java`(285 判定行) 与 4 份布局（`screen_navigation.xml` 11 / `screen_navigation_body.xml` 150 / `item_navigation_row.xml` 34 / `layout-sw600dp/screen_navigation.xml` 16），合计 496 行，不超 500。
- Users/scenarios: 与父任务一致——查看分支列表与 AI 总结、归档/恢复/删除确认；本切片只负责「渲染与转发」，判定留在 presenter/存储。
- Expected outcome: 上述 5 个文件按拆分口径归属本切片并保留既有实现；规模按 496 判定（>200 软上限警告，<500 合规）。
- Core flow and reversibility: 与父任务一致；本切片无独立运行时行为，行为由父任务整体承载。
- Inputs/outputs: 读 `NavigationViewState`（visible/archived/summary/keyPoints/pendingDelete 等）；转发 `NavigationActions`。
- State and exception handling: 与父任务一致（读取失败 notice、总结降级、删除确认前不改库）。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX、布局仅用 tokens.json 档位。
- AI recommendation and rationale: 回溯拆分是治理动作而非重写——代码已存在且证据齐全，拆分只改变规模判定与验收归属边界，不产生新 diff。
- User choice and custom additions: 用户 2026-09-23 拍板「拆分为两个子任务」。
- AI-autonomous decisions: 切片边界按文件粒度划分（新增文件归 A、既有文件改动与跨文件字段归 B），避免同一文件出现在两个切片。
- User-confirmation decisions: 设备验证 deferred — 用户自行装机验证（user decision 2026-09-21）。
- Acceptance evidence: 共享父任务证据 `artifacts/evidence/T-039-*.txt`；独立复核报告见父任务 Verification report。
- Facts: 5 文件 496 行口径来自 `artifacts/evidence/T-039-sizing-final.txt` 与父任务 Handoff（`count_production_lines.py` 实测）。
- Assumptions: 拆分不改变任何生产代码内容。
- Unknowns: 无（与父任务共享 deferred 真机观感）。
- Risks: 拆分若被用于伪装「事后批准」，按 harness-tasks.md 属流程缺陷；本切片如实记录为回溯治理，规模判定值 496 以证据文件为准。
- Deferred: 真机观感（同父任务）。
- Non-goals: 不改 presenter/存储判定规则；不做装配（切片 B）；不改任何生产代码。
- Skipped non-critical questions and reason: 切片粒度是否按行为再细分——不需要，496 已在限额内。

## Goal

导航页屏幕与 4 份布局作为独立规模判定单元（496 ≤ 500 行）通过验收，渲染与转发逻辑维持父任务既有证据覆盖。

## Scope

### In scope
- 规模归属（回溯，不产生新 diff）：`android/app/src/main/java/com/treelearn/app/screen/NavigationScreen.java`(285)、`res/layout/screen_navigation.xml`(11)、`res/layout/screen_navigation_body.xml`(150)、`res/layout/item_navigation_row.xml`(34)、`res/layout-sw600dp/screen_navigation.xml`(16)。
- 验收本切片：复跑设计 token 守门与 Java-only guard，规模按 496 判定。

### Out of scope
- `wiring/NavigationWiring.java`、`AppContainer`、`MainActivity` 宿主、三条入口、`NavigationViewState.Row` 字段、`strings.xml`（全部属切片 B = T-2026-09-23-108）。
- 任何生产代码修改。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 无行为变更（治理拆分）；保留父任务全部既有断言与门禁结论。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 同父任务——生产者 `SqliteNavigationStore`，消费者本切片屏幕 + `NavigationPresenter`，宿主 `MainActivity`（属切片 B）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯归属治理，无代码变更。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: 上述 5 文件已在仓库中存在，父任务证据 `T-039-wiring.txt` 证明构造点。
- Documentation surfaces checked: 父任务 Documentation impact record 已覆盖 README/技术方案/PRD 面。
- Documentation files updated: 无需更新（运行事实未变）。
- Producer/consumer/sibling/compatibility paths checked: 同父任务。
- Superseded phrases/examples reverse-searched: 不适用。
- `documentation impact: none` reason and evidence, if applicable: 拆分不改变运行事实，父任务文档记录仍准确。
- Historical/PRD handling: 父任务记录不改写，本文件为新增拆分记录。

## Acceptance criteria

- [x] 切片规模 ≤ 500：按 `count_production_lines.py` 口径 496 行（依据 `artifacts/evidence/T-039-sizing-final.txt`，回读复算与父任务一致）。
- [x] 屏幕不自行判定「哪些分支可见/能否归档」：父任务独立复核已确认 `NavigationScreen.java` 匹配集全部为读状态/渲染参数（见父任务 Verification report）。
- [x] 切片 A+ B 合并复跑门禁全绿：`check`、Java-only guard、`check_design_tokens.py` 退出码 0（在 P3 批量验收中以新鲜证据执行）。〔2026-09-24 复核 #3：`T-107-T108-gates-2026-09-24.txt`——check EXITCODE=0（`P3-B4-gates-2026-09-24.txt`）、guard PASS（`P3-B3-no-kotlin-2026-09-24.txt`）、token 守门 0 failed〕
- [x] 真机观感记为 deferred — 用户自行装机验证（user decision 2026-09-21），不得写成 PASS。〔按既定口径维持 deferred，不判 FAIL〕

## TDD execution record

### RED — failing test first
- Test/scenario: 共享父任务 `NavigationRootGateTest`（断言 `Row.archivable()`/`childCount()` 渲染契约；字段本体属切片 B）。
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests '*Navigation*' --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 1
- Observed failure: 12 处「找不到符号」（`archivable()`/`childCount()`），证据 `artifacts/evidence/T-039-red.txt`（共享）。
- Failure reason validated: 失败确由缺失行为导致；回溯拆分不重跑 RED，沿用父任务共享证据。

### GREEN — minimum implementation
- Implementation: 5 个文件已存在（父任务 2026-09-22 03:13 落盘）；本任务为回溯归属。
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests '*Navigation*' --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 0
- Passing evidence: 26 例 0 失败（NavigationPresenterTest 21 + NavigationRootGateTest 5），`artifacts/evidence/T-039-green.txt`（共享）；独立 verifier 在冻结快照复跑全绿（父任务 Verification report）。
- Regression evidence: `artifacts/evidence/T-039-check.txt`(EXITCODE=0)、`T-039-jvmtests.txt`(全模块 0 失败)（共享）。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 无重构步骤。

### TDD or sizing exception — only with prior user approval
- Exception type: 无例外。本切片 496 行未超硬上限；>200 软上限按规则记 verifier 警告即可。
- Reason: 不适用。
- User approval: 不适用（需要的是拆分裁定，已由用户 2026-09-23 作出，记录在父任务）。
- Alternative verification: 不适用。

## Implementation notes

- 本任务与 T-2026-09-23-108 由父任务 T-2026-09-21-039 于 2026-09-23 按用户裁定拆分产生，实现先于拆分存在（回溯治理），不产生任何新生产代码。
- 切片边界按文件粒度：新增文件归本切片，既有文件改动归切片 B，确保无文件双重归属。
- 规模证据引用链：`T-039-sizing-final.txt`（实测工具产出）→ 父任务 Handoff 第 157 行 → 本文件。不得手抄改数。

## Verification report

- Verdict: **PASS**（2026-09-24 复核 #3 收口，见下方「复核记录 #3」）
- Evidence: 规模 496（`T-039-sizing-final.txt`，回溯口径，不重测目录）；共享父任务证据
  `T-039-red/green/check/jvmtests.txt`（26 例 0 失败、六道门禁快照内全绿）；合并门禁新鲜证据
  `artifacts/evidence/T-107-T108-gates-2026-09-24.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言变更（治理拆分）。
- Gaps: 与父任务共享（真实 SQLite 路径、运行期行为、真机观感 deferred — user decision 2026-09-21）。
- Risks: 拆分被误读为规模例外批准——本切片明确按 496 独立判定，无需例外。

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- 规模：496 行 ≤ 500 硬上限（>200 软上限记 verifier 警告，无需例外）；判定值引用
  `T-039-sizing-final.txt`，未重测目录（Watch-outs ① 遵守）。
- 门禁合并复跑：`check` EXITCODE=0、no-kotlin guard PASS、设计 token 守门 0 failed、
  `validate_tasks.py` errors=0（`T-107-T108-gates-2026-09-24.txt`，本日新鲜证据）。
- 屏幕无第二判定点：沿用父任务独立复核结论（`NavigationScreen.java` 匹配集全部为读状态/渲染参数），
  本轮无新 diff，结论仍成立。
- 与切片 B 同批收口；T-039 按拆分口径归档。
- 结论：验收 4 条全勾（其中设备项按既定口径 deferred）→ **verified**。

## Handoff notes

- Completed: 任务立案（2026-09-23，leader，依用户拆分裁定）。
- In progress: 待 P3 批量验收（与切片 B 同批复跑门禁）。
- Blocker: 无。
- Unblock condition: 切片 B 与本切片门禁复跑全绿后转 verifying。
- Next steps: P3 验收时与 T-108 同批复跑 `check` + guard，回填本文件 Verification report。
- Watch-outs: ① 规模判定值 496 只能引用证据文件，不得重测目录（文件可能被后续任务编辑）；② 引用父任务证据时核对证据文件自带的命令行与 EXITCODE 行。
