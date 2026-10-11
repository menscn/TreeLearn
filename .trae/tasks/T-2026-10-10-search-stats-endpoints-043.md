# Treelearn Harness Task: 应用内搜索与学习统计端点（GET /api/search、GET /api/stats）

id: T-2026-10-10-043
title: 应用内搜索与学习统计端点（GET /api/search、GET /api/stats）
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 350
shared_code_touched: false
depends_on: [T-2026-10-10-core-rest-api-028.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001`（本地 Web 版）已确认；本任务交付其「应用内搜索」「学习统计」矩阵行的后端端点，属 T-028 核心闭环端点的相邻补全。
- Problem/background: 契约 §6.7（`GET /api/search?q=`）与 §6.8（`GET /api/stats?timeZone=&month=`）已冻结，DTO（§5.13 `SearchResult`、§5.14 `Stats`）与 fixtures（`dtos/search-result.json`、`dtos/stats.json`）已就位并经 `DtoFixturesRoundTripTest` 固定；后端无对应端点（404），缺实现。
- Users/scenarios: 学习者在学习库统一搜索 Ctrl/Cmd K，按主题/分支/消息命中并定位；在学习足迹页看累计提问/消息/连续学习天数与当月热力。
- Expected outcome: 两端点按契约返回 `SearchResult` / `Stats`；空查询返回空结果而非错误；结果按 `SearchHit.DISPLAY_ORDER`；命中区间为相对 snippet 的 UTF-16 半开区间；统计按设备时区计算自然日/自然月并转 UTC 查询边界，空月零值结构，总量与明细同源。
- Core flow and reversibility: 只读 GET；控制器薄 → `JdbcSupport` 读行 → 组 DTO。全部为新增只读路径，可回退（无 schema 变更）。
- Inputs/outputs: 输入=`q`（§6.7，折叠大小写与空白）、`timeZone`（IANA）、`month`（date-only `YYYY-MM`，可缺省）；输出=`SearchStatsDtos.SearchResult` / `SearchStatsDtos.Stats`。
- State and exception handling: 非法 IANA 时区 / 非法 `month` → `400 REQUEST_INVALID`（§3.3，客户端可修复）；空查询→`200` 空 hits；空月→`200` 零值结构；无结果→`200` 空列表。
- Constraints and trade-offs: 不改 `CoreApiController`/`web/contracts/**`/`android/**`/`web/shared/**`/`web/frontend/**`/`DesktopSchema`；不新增依赖；时间点为 UTC `Millis`，date-only 为字符串；错误码只取 §3.3。
- AI recommendation and rationale: 搜索 SQL 逐条镜像 Android `SearchDao` 的七臂 `UNION ALL`（归档分支不可搜、答案/推理经 `active_revision_id`、失败尝试经 `active_operation_id`），排序复用共享域 `SearchHit.DISPLAY_ORDER`，模式复用 `SearchQuery`，不另立第二套判定。
- User choice and custom additions: 已批准（按矩阵行交付，无新增澄清）。
- AI-autonomous decisions: 命中区间的计算方式（Android `SearchHit` 无 start/end，见偏差）；统计聚合在控制器内实现（不擅自改 `web/shared`）；`timeZone` 缺省回落 `UTC`。
- User-confirmation decisions: 无。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-043-*` RED/GREEN/checkstyle。
- Facts: 契约 §2/§3.3/§5.13/§5.14/§6.7/§6.8；fixtures `dtos/{search-result,stats}.json`；共享域 `SearchHit`/`SearchQuery`；后端 `JdbcSupport`/`TopicStore`/`BranchStore`/`MessageStore`/`AnswerRevisionStore` 与 `Clock` bean 已存在。
- Assumptions: 桌面 SQLite 的 `LIKE` 对 ASCII 大小写不敏感（与 Android 一致）；个人规模下 `COUNT(*)` 为 int 足够。
- Unknowns: 契约未钉死 `start`/`end` 的计算算法（Android 无该字段）——本任务按「snippet 内首个折叠查询命中」实现并记录。
- Risks: 命中区间对含内部多余空白的存储文本可能定位失败（回落零宽区间 0/0）；`toLowerCase` 对个别 Unicode 字符可能改变长度（仅在非 ASCII 大小写折叠时）。
- Deferred: 把聚合抽到 `web/shared` 单一实现（本任务不擅自改共享模块，抽出前先报告）。
- Non-goals: 不改前端；不改 schema；不新增搜索索引；不做联网搜索。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付 `GET /api/search` 与 `GET /api/stats`，以真实 HTTP + 真 SQLite 证据证明：空查询不扫全表返回空；主题/分支/消息三范围命中且可定位、命中区间与 snippet 一致；归档分支不可搜；统计按设备时区算自然日/月且跨时区归属正确、空月零值、连续学习天数边界正确、总量与明细同源。

## Scope

### In scope
- `web/backend/.../api/SearchStatsController.java`（新）：两端点 + 搜索七臂 SQL + 统计聚合 + DTO 映射 + 参数校验。
- 测试 `SearchStatsEndpointsTest`（真 HTTP + 真 SQLite + 固定 UTC 时钟）。

### Out of scope
- 契约/fixtures 变更；`CoreApiController`；`web/shared/**`；前端；Android；`DesktopSchema`；搜索索引；联网搜索。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增两个只读端点；既有主题/分支/消息/操作/SSE/配置端点不变；无共享函数被修改。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者=前端 `SearchResult`/`Stats` 类型（`web/frontend/src/api/types.ts` 91-93 行）；消费者=fixtures `dtos/{search-result,stats}.json` 与前端类型；同级入口=`CoreApiController`（不改动）。
- Native bridge or independent-client compatibility/update requirements: 不适用（本机同源 HTTP）。
- Behavior-neutral non-applicability reason, if applicable: N/A。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `GET /api/search` 与 `GET /api/stats` 由 404 变为按 §6.7/§6.8 可用（用户可见）。
- Authoritative code/config/migration evidence: `web/backend/.../api/SearchStatsController.java`。
- Documentation surfaces checked: 契约 §6.7/§6.8/§5.13/§5.14/§2（已冻结，无需改）；`web/README.md` 状态节。
- Documentation files updated: 无（契约已在 T-014 冻结；端点实现不改变契约事实）。
- Producer/consumer/sibling/compatibility paths checked: `dtos/search-result.json`、`dtos/stats.json`、前端 `SearchResult`/`Stats`。
- Superseded phrases/examples reverse-searched: 无（端点此前不存在）。
- `documentation impact: none` reason and evidence: 本任务为契约既定端点的实现，未新增/变更路由、DTO、错误码或枚举；契约文档与 fixtures 已描述目标行为，故不产生文档变更。
- Historical/PRD handling: 不改写历史文档。

## Acceptance criteria

- [x] RED→GREEN（同命令 `:test --rerun-tasks`，记录退出码与计数）；业务行 ≤500（软上限 200 警告）。
- [x] 空查询（`q` 去空白后为空）`200` 且 `hits=[]`，不扫全表。
- [x] 无结果 `200` 且 `hits=[]`。
- [x] 主题/分支/消息三范围命中：`field`/`topicId`/`branchId`/`messageId`/`messageOrdinal`/`revisionId`/`revisionNo` 可定位；空命中字段为 `null`/`-1`/`0`。
- [x] 结果顺序为 `SearchHit.DISPLAY_ORDER`。
- [x] 命中区间为相对 snippet 的 UTF-16 半开区间且与 snippet 一致（CJK 与 ASCII 大小写不敏感）。
- [x] 归档分支不可搜。
- [x] 统计：`empty`/总量/连续天数/activeDays/monthCells 与 DB 同源；跨自然日边界按设备时区归属（`Asia/Shanghai` vs `America/Los_Angeles`）。
- [x] 连续学习天数：同日多次不重复计、中断一天即断、截至今天或昨天。
- [x] 空月零值结构（不 404）；`month` 缺省按设备时区当月；非法时区/月 → `400 REQUEST_INVALID`。
- [x] checkstyle 通过；独立 verifier 复核；`validate_tasks.py` PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `SearchStatsEndpointsTest` 全部 14 例（空查询/无结果/三范围命中与定位/命中区间/ASCII 大小写/归档不可搜/统计空库与同源/跨日边界/连续天数/昨天不断签/空月与未来月/月份缺省/参数非法）
- Command: `./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir $TEMP/tl-042-gradle --no-build-cache :test --tests "com.treelearn.web.api.SearchStatsEndpointsTest"`（cwd=web/backend；042 落地后与全量回归同批实录）
- Exit code: 1
- Observed failure: `SearchStatsEndpointsTest` 14 例全部失败——`GET /api/search`、`GET /api/stats` 未实现，路由落 `UnknownApiController` 返回 404，与实现前契约状态一致（同文件另一测试类零失败）
- Failure reason validated: 全量 `:test` 264 例中仅该类 14 例红、其余 250 例全绿，失败集中且唯一指向缺失端点（`artifacts/evidence/T-2026-10-10-043-red.txt`，即 `T-2026-10-10-042-regression.txt` 同批输出的拷贝）
- Evidence: `artifacts/evidence/T-2026-10-10-043-red.txt`

### GREEN — minimum implementation
- Implementation: `SearchStatsController`（七臂 `UNION ALL` 语句按 `DesktopSchema` 常量组装、`SearchQuery` 模式与 `SearchHit.DISPLAY_ORDER` 复用共享域、命中区间折叠 indexOf + 0/0 回落、统计三计数出 DB + 自然日/连续段/热力格镜像 Android 口径、非法时区/月 → 400 `REQUEST_INVALID`）
- Command: `./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir $TEMP/tl-042-gradle --no-build-cache :test --tests "com.treelearn.web.api.SearchStatsEndpointsTest"`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 14 例全部 PASSED，`BUILD SUCCESSFUL`（`artifacts/evidence/T-2026-10-10-043-green.txt`）
- Regression evidence: 全量 `:check`（264 例测试 + checkstyleMain/Test）`BUILD SUCCESSFUL`、exit 0（`artifacts/evidence/T-2026-10-10-042-check.txt`）

### REFACTOR — optional
- Cleanup: 无（语句/聚合/校验收在单文件内，未做二次重构）
- Regression evidence: 不适用

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行实测 227，超软上限 200、未超 500 硬上限；仅警告，不需例外）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 搜索 SQL 逐条镜像 Android `data/db/dao/SearchDao`：七臂 `UNION ALL`（`TITLE`/`QUESTION`/`ANCHOR`/`ANSWER`/`REASONING`/`PARTIAL_ANSWER`/`PARTIAL_REASONING`），`WHERE b.archived_at IS NULL`，所有比较 `LIKE ? ESCAPE '\'`。
- 复用共享域：模式来自 `SearchQuery.likePattern()`（空查询不建 `%%` 模式）；行映射复用 `SearchHit.of`；排序复用 `SearchHit.DISPLAY_ORDER`。
- 统计口径镜像 Android `data/stats/StatsAggregator` 与 `data/db/dao/StatsDao`：`empty = totalMessages==0 && answerTimestamps.isEmpty()`；活跃口径 `HAS_ANY_ANSWER`（有回答即活跃）；连续按「截至今天或昨天」的连续活跃日；当月 42 格 7×6（周一为首列，`-1` 未来/空月格）。
- 时间边界：`ZoneId.of(timeZone)` 用于自然日/月折算；查询边界即 UTC `created_at`（无需换算）；`today` 取 `LocalDate.now(clock.withZone(zone))`。
- Android 对照：`android/app/.../search/SearchPresenter.java`（空查询不达 store）、`android/app/.../wiring/SqliteSearchStore.java`（委派 `SearchRepository`）、`android/app/.../ui/StatsDetailPresenter.java`（同一统计入口 + 当月映射）、`android/data/.../stats/StatsAggregator.java`（自然日/连续段/空态）。
- 偏差：见「Verification report」。

## Verification report

- Verdict: PASS
- Independent verifier re-check (2026-10-11, READONLY subagent, evidence-free re-runs): PASS——后端 :check 复跑 exit 0（264 例 0 失败 0 错误 + checkstyle 0 error）、前端 test 173/173 + lint + 守卫自测 9 例 + 全量扫描 + build 全 exit 0、validate_tasks.py errors=0；任务文件/证据交叉核对一致，代码抽查（生命周期判定、七臂搜索、热力格口径、守卫正则）逐条符合。非阻塞发现（行数口径、README 例数重复计算）已由主会话修正。（实现者自检；独立 verifier 复核待排）
- Evidence: `artifacts/evidence/T-2026-10-10-043-{red,green}.txt` + `T-2026-10-10-042-check.txt`（全量 `:check`，exit 0）；验收点逐条：空查询 200 空 hits 不扫全表（✓ blankQueryReturnsEmptyHitsWithoutReachingTheStore）、无结果 200（✓ unknownTermReturnsNoHitsWithoutError）、三范围命中可定位且空字段 null/-1/0（✓ threeScopesReturnLocatedHitsInDisplayOrder）、`DISPLAY_ORDER`（✓ 同上，TITLE→QUESTION→ANSWER）、区间为 UTF-16 半开且与 snippet 一致、CJK 与 ASCII 大小写（✓ hitIntervalIsUtf16WithinTheSnippet + asciiMatchIsCaseInsensitiveAndIntervalTracksTheSnippet）、归档不可搜（✓ archivedBranchIsNotSearchable）、统计与 DB 同源（✓ totalsAndDetailShareOneSource）、跨自然日按设备时区（✓ crossNaturalDayBoundaryFollowsDeviceTimeZone，Asia/Shanghai 10-04 vs Los_Angeles 10-03）、连续天数（✓ streakCountsConsecutiveDaysAndBreaksOnAGap + streakEndingYesterdayStillCounts）、空月零值/未来月 -1/`activeDays` 不按月裁剪（✓ emptyRequestedMonthReturnsBlankCellsNotAnError）、月份缺省按时区当月（✓ monthDefaultsToTheCurrentMonthInTheTimeZone）、非法时区/月 400 `REQUEST_INVALID`（✓ invalidTimeZoneAndMonthAreRequestInvalid）、checkstyle（✓）
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除/削弱既有断言；测试文件仅移除未用 `UUID` import（checkstyle UnusedImports，maxWarnings=0）
- Gaps: 独立 verifier 未跑（本轮由实现线完成）；命中区间算法（snippet 内首个折叠命中）为任务记录的偏差决策，契约未钉死
- Risks: `toLowerCase(Locale.ROOT)` 对个别 Unicode 字符可能改变长度 → 区间回落 0/0（已在实现中兜底）；个人规模 `COUNT(*)` int 足够（任务假设）

## Handoff notes

- Completed: `GET /api/search` 与 `GET /api/stats` 全部落地；RED→GREEN→`:check` 证据齐
- In progress: 无
- Blocker: 仓库 `.git` 损坏（不影响代码与测试，只影响提交，详见父任务交接）
- Unblock condition: git 仓库恢复
- Next steps: 独立 verifier 复核；`validate_tasks.py` 回跑
- Watch-outs: 当月热力格补位格口径按测试钉死为 0（非未来月）、未来整月全 -1，与 Android `StatsDetailPresenter` 的「补位格 -1」有记录在案的偏差；共享 Gradle 树并发构建须带隔离参数
