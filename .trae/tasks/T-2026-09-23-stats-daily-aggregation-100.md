# Treelearn Harness Task: 统计按天聚合与连续天数查询

id: T-2026-09-23-100
title: 统计按天聚合与连续天数查询
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-home-drawer-stats-heatmap-099.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页抽屉与统计热力图吸收；本任务交付数据统计口径（`:data` 层），全批最先。
- Problem/background: 热力图、连续天数、累计指标需要一个唯一的数据查询入口；现状 `TreeLearnSchema.java` 相关表只有逐条记录，无聚合视图。
- Users/scenarios: 首页热力条与抽屉热力图/指标卡读取同一数据源。
- Expected outcome: 按天活跃聚合、累计提问数、累计消息数、连续学习天数（streak）与空态表示；单一查询入口供 102/104 消费。
- Core flow and reversibility: 只读查询，无状态变更；空数据返回空态值而非 0 误导。
- Inputs/outputs: 输入既有回答/消息记录（时间戳）；输出聚合结果对象（含空态标记）。
- State and exception handling: DB 查询失败按既有数据层错误路径抛出，由 UI 层降级；当天边界与时区口径须与 SQL 时区规则一致（设计阶段核实后写进测试）。
- Constraints and trade-offs: Java-only；不改 schema、不加列；DAO 只读新方法。
- AI recommendation and rationale: 聚合算法（streak）放纯 Java 可测类，SQL 只做按天 group；分开可测。
- User choice and custom additions: 无。
- AI-autonomous decisions: 活跃口径默认「有回答即活跃」（intent 开放问题 2 默认项，实现期若设计稿另定则跟随设计稿并记录）。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN 单元测试（聚合、streak 断档、空态、时区边界）。
- Facts: 既有列证据见 `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java`（ANSWER_REVISIONS 等）；无 token/usage 列。
- Assumptions: 时间戳足以支撑按天口径。
- Unknowns: 设计稿对活跃口径的最终定义。
- Risks: streak 断档判定与用户直觉错位。
- Deferred: Token 四指标（本批外）。
- Non-goals: 不改既有 DAO 行为；不做 UI。
- Skipped non-critical questions and reason: 聚合缓存策略——数据量小暂不需要，留观察。

## Goal

交付统计聚合查询（按天活跃 / 累计提问 / 累计消息 / 连续天数 / 空态），作为热力条、抽屉热力图、统计详情页的唯一数据源。

## Scope

### In scope

- `:data` 只读查询方法与聚合纯类；单元测试。

### Out of scope

- 热力图 View（101）；首页/抽屉/详情页 UI（102/103/104）；schema 变更。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 既有 DAO 读写行为不变（只新增方法）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者为消息/回答 DAO；消费者为 102/104 的 Presenter。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增只读统计查询入口。
- Authoritative code/config/migration evidence: 新增 DAO 方法与聚合类代码。
- Documentation surfaces checked: 技术架构方案（数据层）。
- Documentation files updated: 无（纯内部查询，随 105 一并说明口径）。
- Producer/consumer/sibling/compatibility paths checked: 既有 DAO 测试回归。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 无对外契约。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED/GREEN：按天聚合正确（含跨月/空天）、streak 断档与跨边界、空态值、时区当天边界。
- [ ] 既有 DAO 测试全部通过（回归证据）。
- [ ] Java-only guard + 单测命令退出码 0；生产代码 ≤200 行（非空非注释口径）。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增 `StatsAggregatorTest`（10 用例：空态、问题-only 非空态、按天聚合去重、升序排列、跨月连续、空天断档、今天未活跃宽限、昨天前断档、业务时区当天边界 vs UTC、可替换谓词）与 `StatsDaoStatementTest`（4 用例：三张语句形状、USER 行口径、仅取 created_at）。
- Command: `cd android && ./gradlew.bat :data:testDebugUnitTest`
- Exit code: 1
- Observed failure: 见 `artifacts/evidence/.t100-red.log`
  ```
  > Task :data:compileDebugUnitTestJavaWithJavac FAILED
  StatsAggregatorTest.java:37: 错误: 程序包StatsAggregator不存在
  StatsAggregatorTest.java:39: 错误: 找不到符号 StatsAggregator.aggregate(...)
  StatsDaoStatementTest.java:22: 错误: 程序包StatsDao不存在
  （共 26 个错误，全部为 StatsAggregator / StatsDao 符号不存在）
  BUILD FAILED in 1s
  ```
- Failure reason validated: 26 个错误全部是被测类缺失导致的符号解析失败，无其他编译错误，确认失败由缺失行为导致。

### GREEN — minimum implementation
- Implementation: 新增纯 Java `android/data/src/main/java/com/treelearn/data/stats/StatsAggregator.java`（UTC epoch millis → 业务时区 LocalDate 桶；单一可替换 `ActivityPredicate`，默认「有回答即活跃」；streak 自今天/昨天向回走连续天，首个空天断档；`StatsResult.isEmpty()` 空态）与只读 `android/data/src/main/java/com/treelearn/data/db/dao/StatsDao.java`（answer_revisions 时间戳、USER 行提问数、全行消息数三条语句，全部由 `TreeLearnSchema` 常量拼装）。实现期修正：跨月用例改以 Sep 1 为测量「今天」以贴合 streak 口径；`LocalDate.ofInstant`（API 34）改为 `Instant.atZone(zone).toLocalDate()`（API 26+）修复 lint NewApi。
- Command: `cd android && ./gradlew.bat :data:testDebugUnitTest`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`；`StatsAggregatorTest tests=10 failures=0 errors=0; StatsDaoStatementTest tests=4 failures=0 errors=0`（来源 `artifacts/evidence/.t100-green.log`、`.t100-testcount.log`）。lint 修复后重跑仍为 `Exit code: 0`。
- Regression evidence: `cd android && ./gradlew.bat testDebugUnitTest`（全模块）→ Exit code: 0，`BUILD SUCCESSFUL`（:ai、:app、:data），既有 DAO/Schema 测试（MessageQuoteColumnTest、SearchDaoStatementTest、TreeLearnSchemaQuoteInvariantTest、ConstraintExtendedCodeTest、SecretEnvelopeTest 等，:data 共 44 用例）全部通过；来源 `artifacts/evidence/.t100-regression.log`。

### REFACTOR — optional
- Cleanup: 无（实现即最小形态；新增文件非空非注释行数 StatsAggregator.java 100 + StatsDao.java 38 = 138，来源 `artifacts/evidence/.t100-linecount.log`，≤200）。
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 活跃口径默认「有回答即活跃」（`StatsAggregator.HAS_ANY_ANSWER`，按当天的 answer revision 数判定）；若设计稿定为其他口径，改这一处谓词并同步测试。
- streak 口径：自「今天」向回数连续活跃天；今天尚无活跃则从昨天起算（宽限），最后一个活跃天在昨天之前即 0；断档只影响 streak，不裁剪 activeDays（热力图完整展示历史）。
- 时区口径（对齐 rules/sql.md）：存储时间为应用写入的 UTC epoch millis；「哪一天」在显式传入的业务时区上由 instant 换算得到，不换算存储值、不依赖设备默认时区；跨月/跨年由 `LocalDate` 日历算术保证。
- 空态：`StatsResult.isEmpty()` 仅在既无消息行也无 answer 行时为 true；仅有提问无回答不是空态，activeDays 为空、totals 照实返回。
- 真实 Android SQLite 执行证据（三条只读语句的写入/读取/边界）按仓库惯例留给设备轮次（与 SearchDao 语句测试同一分工）；JVM 证据覆盖语句形状与聚合算法。
- 与 103 串行：本任务与抽屉容器同触首页 Presenter/ViewState 面。102/104 消费方式：`StatsDao.answerTimestamps/questionCount/messageCount` → `StatsAggregator.aggregate(...)` → `StatsResult`（isEmpty/activeDays/totalQuestions/totalMessages/currentStreak）。

## Verification report

- Verdict: PASS（verifier 2026-09-23 两轮独立验收：首轮 PARTIAL，唯一缺口为 lint 成功日志；083 落定后 verifier 复跑 `./gradlew.bat lint` EXIT 0、`:data:testDebugUnitTest` EXIT 0（14/14 新用例通过）、no-kotlin-check VERDICT PASS，缺口消除。主会话归档证据 `artifacts/evidence/.t100-lint-rerun.log`（`BUILD SUCCESSFUL`）与 `.t100-lint-rerun-code.log`（`EXITCODE=0`）后升级 PASS）
- Evidence: ① 独立跑 `:data:testDebugUnitTest` EXIT 1，26 错误全部来自 083 未落定的共享文件（`TreeLearnSchema.java` 未提交改动引用尚未写出的 `Models` 类），与本任务零关联；② 变通独立验证：HEAD 版 Schema + 本任务源码 javac 编译 EXIT 0，JUnitCore `OK (14 tests)`；③ GREEN 运行报告 XML（08:17）：`:data` 7 套件 44 用例 failures=0；④ no-kotlin-check.ps1 独立复跑 VERDICT: PASS；⑤ 行数独立复核 138 ≤200；⑥ 代码审阅：DAO 纯只读、语句由 Schema 常量拼装、无 schema/migration 改动、streak 跨边界与时区口径符合 sql.md、空态不误导、活跃谓词可替换。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删改既有断言；新增 14 用例覆盖跨月/空天断档/时区边界/空态/可替换谓词。
- Gaps: 无（首轮的 lint 证据缺口已由第二轮 verifier 复跑消除，证据见 `artifacts/evidence/.t100-lint-rerun.log` 与 `.t100-lint-rerun-code.log`）。
- Risks: `answerTimestamps` 全表无上限，数据量增长需留意；真机 SQLite 执行证据按分工留设备轮次。

## Handoff notes

- Completed: 任务创建；RED→GREEN 完成（测试 14 用例全过，全模块回归通过）；no-kotlin-check.ps1 VERDICT: PASS（Exit code: 0）；生产代码 138 行（非空非注释）≤200；verifier 两轮验收终判 PASS；**status: verified（2026-09-23）**。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 101（热力图自绘 View）开工，消费 `StatsAggregator.aggregate`；随后 102 → 103 → 104 → 105。
- Watch-outs: 后续任务不得改动本任务产出的聚合口径而不同步测试；活跃口径若被设计稿更改只改一处谓词；真实 SQLite 执行证据留给设备轮次。
