# Treelearn Harness Task: 搜索补上「用户问题」臂（BASE-12 检索范围）

id: T-2026-09-22-048
title: 搜索补上用户问题臂（需求基准 BASE-12 的检索范围）
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
depends_on: [T-2026-09-20-search-unread-reading-position-015.md, T-2026-09-21-search-page-and-home-entry-040.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 需求基准 BASE-12 规定检索范围是「主题所复用的根标题、其他分支标题、**用户问题**、稳定或部分 AI 回答及锚定原文」。当前检索只有六臂（标题 / 锚定原文 / 回答 / 思考 / 失败尝试的部分正文 ×2），**没有用户问题臂**，学习者搜不到自己问过的任何一句追问。
- Problem/background: 实测事实链——`android\data\src\main\java\com\treelearn\data\db\dao\SearchDao.java:162-189` 建六臂后 `String.join(" UNION ALL ", arms)`；`android\core\src\main\java\com\treelearn\core\domain\SearchHit.java:23-30` 的 `Field` 只有 TITLE / ANCHOR / ANSWER / REASONING / PARTIAL_ANSWER / PARTIAL_REASONING。问题文本本就在库：`android\data\src\main\java\com\treelearn\data\db\TreeLearnSchema.java:107` 的 `Messages.ROLE` 与 `:109` 的 `Messages.USER_CONTENT`。T-040 的 Gaps ② 把这件事记为刻意缺口，T-015 未记录任何用户批准的删减，因此这是**需求缺口**而非已批准的收缩。**T-045 落地后主题标题不再等于首个问题，缺口会从「搜不到追问」扩大到「连首问也搜不到」**，故本条应在 T-045 之前或同期收口。
- Users/scenarios: 学习者想「上次我问的那个约束条件是怎么说的」，在搜索页输入自己的原句 → 今天零命中。
- Expected outcome: 输入学习者问过的原句能命中，命中行标出匹配来自「问题」，展示顺序仍由 `SearchHit.DISPLAY_ORDER` 唯一决定。
- Core flow and reversibility: 只加一臂；不改 schema、不写 migration、不新增依赖；回退即删掉该臂与枚举值。
- Inputs/outputs: 输入 `SearchQuery`；输出多一类 `SearchHit`（`matched_field = QUESTION`，`branchId` 为所在分支，`messageId` 为该用户消息）。
- State and exception handling: 与既有六臂同构——第七臂必须一并带 `b.archived_at IS NULL`（归档分支连同子树不得被搜到）；空查询仍抛 `IllegalStateException`；通配符仍不内联。
- Constraints and trade-offs: 只加臂，不改相关性、不引入权重、不改排序算法。
- AI recommendation and rationale: 建议做。理由：这是明确编号需求（BASE-12）与真实用户动作的交集，成本低（一臂 + 一个枚举值 + 同步两处计数）。
- User choice and custom additions: PENDING（本条由工头依 BASE-12 立；用户可一句话取消，它排在 T-047 之后，取消成本为零）
- AI-autonomous decisions: 新增 `Field.QUESTION` 在 `DISPLAY_ORDER` 中的位置——建议紧随 `TITLE`，理由：「我这么问过」是最强的识别线索。
- User-confirmation decisions: 归档分支是否应可被搜到（见 Unknowns）。
- Acceptance evidence: RED→GREEN 的 JVM 用例（原句命中、归档分支的问题搜不到、既有六类结果与顺序不变）；`SearchDaoStatementTest` 的臂数与归档过滤计数由 6 同步为 7 并写明理由；`:app` 单测与 `.\gradlew.bat check` 退出码 0；Java-only guard PASS；设计 token 守门；原文扫描证明 `strings.xml` 的 `search_scope` 已含「问题」。
- Facts: 见 Problem/background，均带文件与行号。
- Assumptions: `messages.role` 表示用户问题的取值需在实现时以既有 DAO 与 schema 实测确认，不得凭记忆写死字面量。
- Unknowns: 归档分支是否应可被搜到——PRD 6.2.8 说已归档子树「可经已归档入口或搜索找到」（BASE-14 用「或」连接），当前实现只保留「已归档入口」这一条路，属既有设计，本任务不动。
- Risks: `SearchHit.Field` 是 `:core` 公共枚举，`DISPLAY_ORDER` 是唯一排序权威，屏幕与测试都读它；改枚举必须同步，且不得让屏幕自排序。
- Deferred: 命中行显示**祖先路径**（BASE-12「结果显示祖先路径及归档状态」；T-040 Gaps ① 已记为缺口，需要一次树遍历）不在本任务 In scope，另行立任务。
- Non-goals: 不改相关性排序、不引入全文索引、不改屏幕视觉与布局。
- Skipped non-critical questions and reason: 不为「问题」臂单设权重（与 T-015 同口径：权重可在确定性默认排序后迭代）。

## Goal

搜索能命中学习者自己问过的原句，且归档分支里的问题仍然搜不到。

## Scope

### In scope
- `:data` `SearchDao` 增加第七臂（用户问题），复用既有 `arm(...)` 与 `like(...)`，带上 `b.archived_at IS NULL`。
- `:core` `SearchHit.Field` 增加 `QUESTION`，并在 `DISPLAY_ORDER` 中给出位置。
- `SearchDaoStatementTest`：臂数 6 到 7、归档过滤计数 6 到 7（同步更新而非放宽），并在任务文件写明理由与等价覆盖。
- `res/values/strings.xml` 的 `search_scope` 引导文案补上「问题」。
- 屏幕侧若按 `Field` 分支显示匹配来源，为 `QUESTION` 给出文案。

### Out of scope
- 祖先路径显示；相关性权重；归档分支可搜性；屏幕视觉重做；schema 与 migration。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增一类命中；既有六类命中的结果、顺序与跳转锚点不得变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `android\data\...\db\dao\SearchDao.java` 的 `buildStatement()`；消费者 `android\core\...\domain\SearchHit.java`、`android\app\...\search\SqliteSearchRepository.java`、`SearchPresenter.java`、`screen\SearchScreen.java`；兄弟路径 `android\app\...\wiring\SqliteSearchStore.java`。
- Native bridge or independent-client compatibility/update requirements: 无桥接；`matched_field` 是本地展示值，不出设备。
- Behavior-neutral non-applicability reason, if applicable: Not applicable

## Documentation impact record

- Changed runtime facts: `matched_field` 多一个取值 `QUESTION`；检索臂由 6 变 7；引导文案含「问题」。
- Authoritative code/config/migration evidence: `SearchDao.java`、`SearchHit.java`、`res/values/strings.xml`、`SearchDaoStatementTest.java`。
- Documentation surfaces checked: `android/README.md` 的 `SearchDao` 表行与该小节（写有「六臂」与「六臂一律 `b.archived_at IS NULL`」）；`Docs/06-delivery/TreeLearn_V1_交付报告.md` 第 2.1 节。
- Documentation files updated: `android/README.md`（六臂改七臂及依据）；交付报告相应行。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact。
- Superseded phrases/examples reverse-searched: 六臂、六个字段各一臂、search_scope、UNION ALL。
- `documentation impact: none` reason and evidence, if applicable: Not applicable
- Historical/PRD handling: PRD 6.2.8 与需求基准 BASE-12 是本任务的**需求来源**（不是历史材料）；本次实现让代码向它对齐，不改 PRD 正文。

## Acceptance criteria

- [x] 搜到用户问题：输入某分支内学习者原句的连续子串，命中行 `matched_field` 为 `QUESTION` 且 `branchId` 正确；RED 先证明当前零命中。〔RED：`QUESTION` 枚举与第七臂不存在 → 编译失败（Exit code 1）；GREEN 后真机 `SearchUnreadReadingInstrumentedTest` 12 例 0 失败，含 question 命中（GREEN 回归证据）〕
- [x] 归档不泄漏：归档分支里同样的原句搜不到（第七臂同样带 `b.archived_at IS NULL`）。〔第七臂与既有六臂同构带归档过滤；真机用例含 archived-question exclusion〕
- [x] 既有六类命中的结果与展示顺序不变；`SearchHit.DISPLAY_ORDER` 仍是唯一排序权威。〔GREEN 定向套件 + :core:test 全过，既有断言未改；新增 `go.go.*` 双限定防回归断言反而加强〕
- [x] 通配符仍不内联：百分号与下划线作为字面量仍由参数绑定处理。〔真机用例含 wildcard escaping；`SearchQuery.likePattern()` 既有唯一产地未改〕
- [x] 门禁：`.\gradlew.bat check` 退出码 0；Java-only guard PASS；设计 token 守门 PASS；`validate_tasks.py` errors=0。〔check：`P3-B4-gates-2026-09-24.txt` EXITCODE=0；guard：`P3-B3-no-kotlin-2026-09-24.txt` PASS；token 守门：`T-048-verify3-2026-09-24.txt` 0 failed；validate_tasks 108 文件 errors=0〕

## TDD execution record

### RED — failing test first
- Test/scenario: Search statement and domain enum had no `QUESTION` arm/value.
- Command: `.\gradlew.bat :data:testDebugUnitTest --tests '*SearchDaoStatementTest' :core:test`
- Exit code: 1
- Observed failure: `SearchHit.Field.QUESTION` could not compile and the six-arm assertions expected
  the old counts.
- Failure reason validated: The failure was caused by the missing field and SQL arm, not by the test
  harness.

### GREEN — minimum implementation
- Implementation: Added `SearchHit.Field.QUESTION`, a bound seventh `SearchDao` arm over non-archived
  `USER.user_content`, the screen label and scope copy, and a regression assertion preventing the old
  `go.go.*` double qualification in failed-attempt arms.
- Command: `.\gradlew.bat :data:testDebugUnitTest --tests '*SearchDaoStatementTest' :core:test`
- Exit code: 0
- Passing evidence: Targeted statement suite and all core tests passed.
- Regression evidence: On real Android SQLite device `AWSS013807000356`,
  `SearchUnreadReadingInstrumentedTest` ran **12 tests, 0 failures**, including question hits,
  archived-question exclusion, wildcard escaping and the existing search behaviors.

### REFACTOR — optional
- Cleanup: 无独立重构轮（实现为「一臂 + 一枚举值 + 两处计数同步 + 文案」的纯增量）。
- Regression evidence: GREEN 定向命令 + :core:test 全过；2026-09-24 全量 `check` 复跑 EXITCODE=0
  （`P3-B4-gates-2026-09-24.txt`）。

### TDD or sizing exception — only with prior user approval
- Exception type: not applicable（TDD 已走完；行数为单臂增量，远低于上限）。
- Reason: 不适用。
- User approval: not needed。
- Alternative verification: not needed。

## Implementation notes

- 判定只允许一处：`SearchDao.buildStatement()`。不得在屏幕或 presenter 里再拼一段「补充检索」。
- `SearchHit.Field` 在 `:core`，被 `DISPLAY_ORDER`、屏幕与测试同时读；改枚举必须同步排序表。
- 不得弱化既有断言：`SearchDaoStatementTest` 的计数是**同步更新**并写明理由，不是放宽。
- 计划外发现：本任务由工头依 BASE-12 于 2026-09-22 立，来源是独立 verifier 在 T-040 复核中撞见的「六臂里没有用户问题臂」；用户可取消。

## Verification report

- Verdict: **PASS**（2026-09-24 复核 #3 收口，见下方「复核记录 #3」）
- Evidence: JVM targeted run, full `check`, Java-only guard, and the 12-test real-device SQLite run；
  复核 #3 门禁刷新证据 `artifacts/evidence/T-048-verify3-2026-09-24.txt`（token 守门 0 failed）。
- Changed/deleted assertion rationale and equivalent behavior coverage: Existing six-arm counts were
  increased to seven rather than weakened; the new question arm carries the exact message id and ordinal
  needed for search jumps.
- Gaps: Independent verifier and design/documentation review still pending.〔2026-09-24：独立复核已由复核 #3 完成；文档面（README 六臂→七臂、交付报告）已在实现轮与 T-040 F2 修复轮更新〕
- Risks: The full historical instrumentation suite still has unrelated constraint-code/fixture failures;
  the new search class itself is green on the device.

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- 验收 5 条逐条核对：全勾（框内已标注证据）。其中验收项 1/2/4 的行级证据来自真机
  `SearchUnreadReadingInstrumentedTest` 12 例 0 失败（GREEN 回归证据，用户真机 AWSS013807000356）——
  这是本仓少有的**已有真机 SQLite 证据**的搜索判据。
- 门禁刷新：全量 `check` EXITCODE=0（`P3-B4-gates-2026-09-24.txt`）、no-kotlin guard PASS
  （`P3-B3-no-kotlin-2026-09-24.txt`）、设计 token 守门 0 failed、`validate_tasks.py` errors=0
  （见 `T-048-verify3-2026-09-24.txt`）。
- 文档面核对：`android/README.md` 六臂→七臂与交付报告措辞已在实现轮/T-040 F2 轮同步（T-040 复核记录）。
- 结论：验收全勾 + 真机 SQLite 行级证据在案 + 门禁全绿 → **verified**。

## Handoff notes

- Completed: 第七臂（用户问题）+ `SearchHit.Field.QUESTION`（DISPLAY_ORDER 紧随 TITLE）+ `search_scope` 文案 + 臂数/归档过滤计数 6→7 同步 + 真机 12 例验证；2026-09-24 复核 #3 收口转 verified。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 祖先路径显示（BASE-12 剩余缺口）另行立任务。
- Watch-outs: 本文件引用的行号可能被其它 lane 改漂移，施工前按符号名复核一次；`messages.role` 的取值以实测为准，不要凭记忆写字面量。
