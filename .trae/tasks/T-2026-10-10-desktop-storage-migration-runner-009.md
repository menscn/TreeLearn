# Treelearn Harness Task: 桌面存储——schema 与版本化 migration runner

id: T-2026-10-10-009
title: 桌面存储——schema 与版本化 migration runner（真实 sqlite-jdbc 证据）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 180
shared_code_touched: false
depends_on: [T-2026-10-10-web-backend-skeleton-003.md, T-2026-10-10-web-runtime-feasibility-002.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: Web 本地版需要桌面 SQLite 权威存储；`Android TreeLearnSchema`（DATABASE_VERSION 5、12 张逻辑表）是职责对照，但 `android/data` 是 Android 库（`android.database.sqlite`），不能作为桌面 JAR 复用（T-002 实测）。表准入记录已写（`Docs/05-technical-design/TreeLearn_Web本地版_桌面数据表准入_V1.0.md`）。
- Users/scenarios: 本机服务启动时打开/建库；用户刷新、服务重启后从落盘事实恢复。
- Expected outcome: `web/backend` 内集中、版本化的 migration runner（DDL 只出现在 runner/schema 一处）；真实 sqlite-jdbc 上验证新装、重开幂等、失败回滚、降级拒绝、PRAGMA（foreign_keys/integrity/foreign_key_check）、12 表约束与外键级联、种子行。升级路径：桌面 v1 为首个版本，记 N/A，以测试专用合成步骤证明 runner 的升级机制与回滚语义。
- Core flow and reversibility: 启动 → 解析数据目录（默认 `%LOCALAPPDATA%\TreeLearn-Web`，可被 `treelearn.data-dir` 覆盖）→ 打开连接（每次连接启用 foreign_keys）→ 读 `PRAGMA user_version` → 按注册步骤前向迁移（逐步事务，失败回滚且不盖版本）→ 就绪。可回退：删测试库即重来；无生产数据。
- Inputs/outputs: 输入为数据目录路径与注册迁移步骤；输出为已迁移到目标版本的 SQLite 库、`user_version`、清晰的失败信息。
- State and exception handling: 失败关闭（迁移失败→服务不启动并给出可读诊断）；降级（库版本高于程序）拒绝并说明；版本无对应步骤时拒绝盖版本号；重开幂等。
- Constraints and trade-offs: 桌面为独立实现（T-002/准入文档 §3 已记录差异：引擎、凭据、版本机制），不重构 `android/data`；语义对照 Android V5 最终形状（同表名/列名/约束语义/索引/触发器），差异在设计文档记录；单文件单实例 loopback，无跨实例要求。
- AI recommendation and rationale: 采用 `PRAGMA user_version`（与 Android 同风格的整型版本、单文件自证、sqlite-jdbc 原生支持），runner 采用「注册步骤 + 逐步事务 + 失败关闭」。
- User choice and custom additions: 用户确认整体方案与 Phase 顺序（2026-10-10）；表准入按「用户指示下按推荐执行」继续（准入文档 §4），本任务不新增第 13 张表。
- AI-autonomous decisions: 包名/类组织、WAL、busy_timeout、测试清单、guard 形态。
- User-confirmation decisions: 桌面永久表准入（已按推荐执行并留痕）；新增第 13 张表须重新确认（本任务不涉及）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-009-*`：RED/GREEN 命令与退出码、`gradlew test` 输出、真实库文件与 PRAGMA 输出摘要、真实启动/重启（第二次启动幂等）日志。
- Facts: sqlite-jdbc 3.53.4.0（Apache-2.0，T-002 实读 POM/LICENSE）；Spring Boot 4.1.1；端口 8733；数据目录 `%LOCALAPPDATA%\TreeLearn-Web`；Gradle wrapper 可跑 backend 测试（cwd=web/backend）。
- Assumptions: 桌面 SQLite 的 `length()`/`RAISE`/部分索引与 Android 引擎语义一致（本任务用真实引擎验证关键项）。
- Unknowns: 无（WAL 与文件锁行为由本任务测试直接验证）。
- Risks: DDL 与 Android 语义漂移 → 以对照映射 + 约束测试兜底；测试污染真实数据目录 → 全部测试强制临时目录。
- Deferred: DAO/Repository（T-010 起）、附件目录、凭据。
- Non-goals: 修改 `android/data`、Android 迁移历史复刻（桌面不做 ALTER 历史，直接最终形状）、新增表/列。
- Skipped non-critical questions and reason: 已确认目标不再重复确认；常规实现选择自主处理。

## Goal

在 `web/backend` 交付桌面 SQLite 的 schema 定义与版本化 migration runner，并在真实 sqlite-jdbc 引擎上取得新装、重开、失败回滚、降级拒绝、约束/外键/PRAGMA 证据；服务启动接入后失败关闭。

## Scope

### In scope
- `web/backend/build.gradle`：加入 `org.xerial:sqlite-jdbc:3.53.4.0`（锁定版本）。
- `com.treelearn.web.storage`：`DesktopSchema`（12 表 DDL/索引/触发器/种子，语义对照 Android V5）、`Migration`/`Migrations`（注册表）、`DatabaseMigrator`（逐步事务、失败关闭、降级拒绝、幂等）、`DesktopDatabase`（连接工厂：foreign_keys=ON + 校验、busy_timeout、WAL）。
- Spring 装配：`treelearn.data-dir`（默认 `%LOCALAPPDATA%\TreeLearn-Web`，env `TREELEARN_DATA_DIR` 可覆盖）；启动时创建目录并运行迁移；失败→服务不启动且信息可读。
- 测试（真实 sqlite-jdbc，临时目录）：新装全量对象+种子、重开幂等、合成步骤升级与失败回滚、降级拒绝、`foreign_key_check`/`integrity_check`/`foreign_keys`、代表性约束（FK 级联、部分唯一索引、quote 触发器、不可变触发器、CHECK 抽样）、DDL 位置守卫（`src/main` 仅 schema/runner 含 DDL）。
- 真实启动证据：临时数据目录启动服务→ready→库文件/user_version；停止后重启→幂等。

### Out of scope
- DAO/Repository、附件目录、凭据（后续任务）；Android 侧任何改动；新增表/列；Flyway/MySQL。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 后端新增启动期建库/迁移；ready 端点与既有响应不变；既有测试（`ReadyEndpointTest`）改注入临时数据目录，断言保持不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `WebApplication`（启动装配）、`ReadyController`（就绪语义不变）；后续 DAO/用例为消费者。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 服务启动创建/迁移 `%LOCALAPPDATA%\TreeLearn-Web\treelearn.db`；`user_version=1`；`treelearn.data-dir` 可覆盖。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/storage/*`、`application.properties`、`build.gradle`。
- Documentation surfaces checked: `web/README.md`（数据目录/启动）、`Docs/05-technical-design/TreeLearn_Web本地版_桌面数据表准入_V1.0.md`（§3 差异记录）、详细开发方案 §5。
- Documentation files updated: `web/README.md`（数据目录与版本机制落地事实）；准入文档 §3 补实现落地注记。
- Producer/consumer/sibling/compatibility paths checked: Android `TreeLearnSchema` 对照映射写入 `DesktopSchema` 类注释；两引擎证据不互替。
- Superseded phrases/examples reverse-searched: "当前骨架无持久化"（web/README.md）→ 更新。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史材料。

## Acceptance criteria

- [ ] RED→GREEN：先加测试（编译失败或断言失败，记录退出码）再实现；同一命令转绿（`gradlew test`，cwd=web/backend）。
- [ ] 真实 sqlite-jdbc：新装 12 表+索引+触发器+种子；重开幂等；失败回滚（版本不前进、半迁移对象不残留）；降级拒绝；`PRAGMA foreign_keys=1`（每连接）、`foreign_key_check` 空、`integrity_check=ok`。
- [ ] 约束抽样证据：FK 级联删除、部分唯一索引（每主题单根、分支活跃操作互斥）、quote 不变式触发器（INSERT/UPDATE 两半）、修订不可变与禁止直删触发器、annotations 容器 CHECK、attachments parse_state CHECK。
- [ ] 服务启动接入：临时数据目录真实启动→ready→库文件存在且 `user_version=1`；重启幂等；迁移失败时服务不启动（以测试证明）。
- [ ] DDL 位置守卫：`src/main/java` 中 `CREATE TABLE/INDEX/TRIGGER`、`ALTER TABLE` 仅允许出现在 `DesktopSchema.java`（或 runner 注册表）——测试扫描证明。
- [ ] 既有测试全绿；checkstyle 通过；行数判定值（runner/config 业务行）≤200（DDL 装配按迁移内容不计判定，物理另记）。
- [ ] 文档影响记录落实（README 数据目录章节更新）。
- [ ] 独立 verifier 验收 PASS；SQL 变更另由 db-migration-reviewer 给出目标引擎 PASS/PARTIAL。

## TDD execution record

### RED — failing test first
- Test/scenario: `DesktopStorageTest` 等（本任务测试集）。
- Command: `gradlew test`（cwd=web/backend）
- Exit code: 1
- Observed failure: 见证据
- Failure reason validated: 缺失生产类（编译失败）→ 确认由缺失行为导致

### GREEN — minimum implementation
- Implementation: `DesktopSchema`/`DatabaseMigrator`/`DesktopDatabase`/装配。
- Command: `gradlew test`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 见证据
- Regression evidence: 既有 `ReadyEndpointTest`/`SharedClasspathTest` 仍绿

### REFACTOR — optional
- Cleanup: 按 checkstyle/评审整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 版本模型：`PRAGMA user_version`；桌面 `SCHEMA_VERSION=1`（首个未发布版本，升级路径 N/A，机制由测试合成步骤证明）。
- 桌面 DDL 为最终形状（无 ALTER 历史）；表/列/索引/触发器名与 Android 保持同名以便对照；Android 迁移历史不复制。
- 种子：内置 DeepSeek Provider + 默认模型（固定 UUID 与固定 epoch，`INSERT OR IGNORE`）。
- 时间：全部 UTC epoch 毫秒 INTEGER（沿用准入文档）。
- 遥测/日志决策：启动迁移日志只记版本、对象计数、耗时、失败类别；不记正文/Key。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10）+ PASS（独立 db-migration-reviewer，2026-10-10）
- Evidence: verifier 自跑 `.\gradlew.bat cleanTest test check`（cwd=web/backend）EXIT=0、282 项 PASSED（含 6 处名称带 Failed 的用例）；`checkstyleMain checkstyleTest --rerun-tasks` EXIT=0。db-reviewer 自跑 `:test --tests "com.treelearn.web.storage.*"` 23 项全 PASSED，并以临时程序实测 sqlite-jdbc 内嵌引擎 `sqlite_version=3.53.4`、`foreign_keys` 默认 0→置 ON 回读 1、`integrity_check=ok`；逐项比对 Android V5（12 表/15 索引/8 触发器/2 种子/CHECK）**无多无少无差异**；建表准入 12 张一致、无第 13 张。证据文件 `artifacts/evidence/T-2026-10-10-009-{red,green,startup,checkstyle}.txt`（RED=编译级失败 33 错误、GREEN=BUILD SUCCESSFUL、启动两次 ready 200 且第二次幂等）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无既有断言被削弱（`git diff` 证实 `ReadyEndpointTest` 仅加 `@TempDir`/`@DynamicPropertySource`，两条断言原样保留）。
- Gaps: ① UTC 时间列缺显式 round-trip/排序断言（非阻断；由 T-010 Store 测试承接，届时在真实引擎补写入→读取→排序证据）；② `DdlLocationGuardTest` 覆盖 `src/main/java` 的 5 类 token，不含 `src/test`/`DROP TABLE`/`CREATE VIEW`（当前范围可接受，后续如需扩大在守卫任务处理）。
- Risks: 规模判定值 240 业务行（>200 警告、<500 通过）：判定口径=「非空非注释」，`DesktopSchema` 的 DDL 装配按规则「行数预算仅计业务 diff（排除生成/迁移/文档）」视同迁移内容不计；该口径来自 `.trae/rules/harness-verification.md` 既有规定（非新增例外），verifier 复核计数一致（240 去纯括号口径 / 305 含括号）。`gradlew test --tests "com.treelearn.web.storage.*"` 不带 `:test` 会因子项目无命中而退出码 1——验收口径以 `:test` 限定或全量 `test` 为准（db-reviewer 记录）。

## Handoff notes

- Completed: 实施与双守卫验收（2026-10-10）。运行事实：`web/backend` 启动即建库/迁移 `%LOCALAPPDATA%\TreeLearn-Web\treelearn.db`（`treelearn.data-dir` / `TREELEARN_DATA_DIR` 可覆盖），`user_version 0 -> 1 (37 statements)`，重开 `already up to date at user_version=1`（幂等）；迁移失败关闭、降级拒绝；DDL 唯一归属 `DesktopSchema.java`（守卫测试可拦截）。规模判定值 240（警告）。
- In progress: 已将 UTC round-trip 缺口交接给 T-010；commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-010（对话树核心 DAO）承接 UTC 写入→读取→排序断言；T-011（操作/快照/派生 + 启动中断落定）。
- Watch-outs: 验收命令用全量 `test` 或 `:test --tests`（不带 `:test` 会误报退出码 1）；DDL 不得越出 `DesktopSchema.java`。