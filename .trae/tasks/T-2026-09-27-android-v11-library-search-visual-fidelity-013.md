# Treelearn Harness Task: 学习库与搜索视觉复刻

id: T-2026-09-27-013
title: 学习库与搜索视觉复刻
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-paper-backdrop-shell-010.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 学习库进行中、已完成、搜索和现有可达的空/加载/错误状态按 V1.1 复刻。
- Expected outcome: 同一现有学习库容器内视觉呈现搜索，不新增搜索路由或归档能力。
- User choice and custom additions: 保留全部生产列表数据、搜索执行及返回结果；原型演示内容不变成默认数据。
- Risks: `LibraryScreen`/搜索 presenter 是共享入口，样式调整不得改排序、查询时机或点击目标。

## Goal

学习库列表、分段和搜索命中/空态在现有路由中与原型一致，并保留列表与搜索行为。

## Scope

### In scope
- `LibraryScreen.java`、`screen_library*.xml`、`item_library_topic.xml`、`item_library_archived.xml`、`item_search_hit.xml` 与关联图标/测试。

### Out of scope
- `SearchPage`/搜索算法、数据排序、归档新增入口、删除/恢复规则和导航架构。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 重排与美化列表、搜索及空状态；分段切换、搜索提交/清空、行点击、归档恢复/删除行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `LibraryScreen` 消费 `LibraryViewState` 和 `SearchPage`；`LibraryPresenter` 接收搜索、segment、row actions；`MainActivity` 保持唯一生产路由。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（搜索及列表动作均需回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 只改变列表、搜索状态视觉层级，不改变搜索路由/查询和数据管理规则。
- Authoritative code/config/migration evidence: `LibraryScreen.java`、`LibraryPresenter`、`screen_library_body.xml`、相关 item layouts。
- Documentation surfaces checked: `android/README.md` 学习库路由描述与全屏规格。
- Documentation files updated: 若路由/动作说明无变化，记录 `documentation impact: none` 及对生产路由和旧术语的搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 首页最近主题卡、学习库进行中/完成、搜索命中与 MainActivity 返回栈。
- Superseded phrases/examples reverse-searched: “搜索独立路由/页面”的当前态陈述。
- `documentation impact: none` reason and evidence, if applicable: 仅视图层级变化时，无当前态行为文档需要改写。
- Historical/PRD handling: 原型独立页面只作视觉意图，不把其转换成新路由。

## Acceptance criteria

- [x] 先为进行中、已完成、搜索命中和无结果状态补回归断言，确认当前实现与原型差异后再实现。
- [ ] 同视口截图逐态对照原型；卡片、分段、搜索头部与空态层次对齐，无裁切/溢出。
- [x] 行排序由原数据状态决定；切换分段可退出搜索；行点击、归档恢复/删除确认与历史导航目标不变。
- [ ] phone/sw600dp 覆盖、定向测试、lint、token guard、Java-only guard 和启动证据通过；触达区 ≥44dp。

## TDD execution record

### RED — failing test first
- Test/scenario: BottomTabShellInstrumentedTest now asserts the normal-state search scope contract; a reversible RED mutation removed library_search_scope from screen_library_body.xml before compiling.
- Command: android\gradlew.bat -p android :app:compileDebugAndroidTestJavaWithJavac --rerun-tasks
- Exit code: 1
- Observed failure: Compilation failed because LibraryScreen.java could not resolve R.id.library_search_scope after the contract view was removed.
- Failure reason validated: The failure was the intentionally removed production view contract; restoring the view and rerunning the identical command produced a zero exit code.

### GREEN — minimum implementation
- Implementation: Restyled screen_library_body.xml, item_library_topic.xml, item_library_archived.xml and item_search_hit.xml with prototype spacing, surfaces, chips, search scope and accessible 44dp controls; added bg_library_card.xml, bg_library_search.xml, bg_library_segment.xml and ic_search.xml; LibraryScreen now renders segment counts and scopes the search hint line to search state; updated BottomTabShellInstrumentedTest and LibraryArchiveInstrumentedTest assertions.
- Command: android\gradlew.bat -p android :app:compileDebugAndroidTestJavaWithJavac --rerun-tasks
- Exit code: 0
- Passing evidence: Restoring the contract view makes the identical compile command pass; the JVM unit suite, lint, design-token guard and Java-only guard also pass; screenshots android-library-search-shell-2026-09-27-v4.png and android-library-search-empty-2026-09-27-v1.png show the phone empty and no-result states.
- Regression evidence: Full device suite remains 241 tests / 23 failures, including pre-existing generation, home data, tree canvas and fixture failures; no new route, query, sort or click target was changed.

### REFACTOR — optional
- Cleanup: Kept LibraryPresenter/SearchPage/SearchViewState unchanged; visual state is still rendered in the existing library container.
- Regression evidence: Search and library JVM tests remain green; the known full-suite device failures are recorded above.

### TDD or sizing exception — only with prior user approval
- Exception type: None; TDD is required。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 延续 `LibraryScreen.showSearch` 的现有页面承载，不复制原型演示结果或新增生产 route。

## Verification report

- Verdict: PARTIAL
- Evidence: phone empty library screenshot `artifacts/evidence/android-library-search-shell-2026-09-27-v4.png`; phone search-empty screenshot `artifacts/evidence/android-library-search-empty-2026-09-27-v1.png`; independent verifier report `artifacts/evidence/T-2026-09-27-013-verifier-report.md`; static review `artifacts/evidence/T-2026-09-27-013-verifier-static.txt`; token guard `artifacts/evidence/T-2026-09-27-013-token-guard.txt`; compile, JVM, lint and Java-only outputs from the commands in GREEN.
- Changed/deleted assertion rationale and equivalent behavior coverage: No behavior assertion was deleted; count/surface assertions were added to BottomTabShellInstrumentedTest and LibraryArchiveInstrumentedTest. Presenter/search tests remain unchanged and cover ordering, query forwarding, empty results and row actions.
- Gaps: No populated topic card, archived card, search-hit card or sw600dp screenshot; full device suite is not green with 241 tests and 23 failures, including three library-adjacent BottomTabShell fixture failures. The initial search-scope visibility risk was fixed by setting the XML node to gone before first render.
- Risks: Visual proof is limited to empty/no-result states; populated layouts are compiled and statically reviewed but not device-captured. The task remains PARTIAL until populated phone and sw600dp evidence is available.

## Handoff notes

- Completed: 学习库普通态、进行中/已完成分段、搜索范围说明和无结果态已按 V1.1 调整；查询、排序、归档和导航链未改。
- In progress: 设备套件和完整 phone/sw600dp 截图矩阵仍待补齐。
- Blocker: 当前设备全量 instrumentation 仍有 23 个已知失败，且未取得有数据学习库/搜索命中和平板横屏截图。
- Unblock condition: 解决 fixture 数据隔离后重跑库相关 instrumentation，并补齐 phone/sw600dp 的进行中、已完成、搜索命中截图。
- Next steps: 独立 verifier 复核当前截图与行为证据；不要把全量设备套件的既有失败写成 013 的视觉回归。
- Watch-outs: 不动 SearchPresenter、SearchPage、LibraryPresenter 的查询时机、排序、点击目标或归档语义。
