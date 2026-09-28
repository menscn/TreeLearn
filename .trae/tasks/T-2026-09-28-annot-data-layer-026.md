# Filename: T-2026-09-28-annot-data-layer-026.md
# Treelearn Harness Task: 高亮批注数据层（T-A）

id: T-2026-09-28-026
title: 高亮批注数据层 + 纯逻辑（T-A）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-28-chat-highlight-annotation-025.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 460
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

Parent summary: 见父需求 T-2026-09-28-chat-highlight-annotation-025.md——对话页正文高亮与批注，方案 V1.1 已全部拍板。

- Problem/background: 标记数据无处存：需新增 `message_annotations` 表 + DAO + 纯逻辑定位/判重/合并。
- Expected outcome: DB V4→V5（fresh 与升级两路都建表）；`AnnotationDao`（含 digest 失配清除）；`app/ui/Annotation.java` 纯 JVM 可测。
- Core flow and reversibility: 写入高亮/批注行；读路径 `listFor(messageId, digest)` 先 `DELETE WHERE digest <> ?` 再 SELECT（自愈）。
- State and exception handling: 流式期间调用方不传 stable digest 时不触发清除（清 除只在 `isStable()` 语义下调用，接口设计体现）。
- Constraints and trade-offs: 单表 + `container` 哨兵列（PROSE: prose_index>=0 其余 -1；CELL: table/row/col>=0 prose_index=-1）；`kind` 入唯一索引；`anchor_text` 列已删（拍板 B）。
- AI recommendation and rationale: 方案 §3/§5。
- User choice and custom additions: 方案 §3.1 DDL 随 8 项拍板获用户确认（2026-09-28）。
- AI-autonomous decisions: DDL 字符串常量 fresh/upgrade 复用（防两路漂移）。
- User-confirmation decisions: 建表确认见上；Harness 开工方式。
- Acceptance evidence: 本文件 TDD 记录 + 真机 SQLite 仪器测试证据。
- Facts: `TreeLearnSchema.java` 现 `DATABASE_VERSION = 4`、11 表、`FORWARD_MIGRATIONS` 至 V3→V4（方案 §1 核实）。
- Assumptions: minSdk 28，无 API 障碍。
- Unknowns: 无。
- Risks: 迁移失败留半迁移库（须证明回滚）；仪器测试与别线抢真机。
- Deferred: UI 一切（T-B/C/D）。
- Non-goals: 不改既有 11 表。
- Skipped non-critical questions and reason: 无。

## Goal

`message_annotations` 表（含索引/CHECK）+ V4→V5 迁移 + `AnnotationDao`（digest 清除/增删改/mergeRange）+ `Annotation` 纯逻辑（严格重合判重、并区间合并、定位器），全部有 RED→GREEN 证据。

## Scope

### In scope
- `android/data/.../db/TreeLearnSchema.java`：`Annotations` 常量类、`TABLE_NAMES`、`CREATE_TABLE_STATEMENTS`、`CREATE_INDEX_STATEMENTS`、`MIGRATION_V4_TO_V5_STATEMENTS`、`MigrationV4ToV5` 注册、版本 5。
- `android/data/.../db/dao/AnnotationDao.java`（新）：`listFor(messageId, digest)`（内含清除）、`insert`、`delete`、`updateBody`、`mergeRange`。
- `SqliteChatStore` 接线（最小暴露面）。
- `android/app/.../ui/Annotation.java`（新）：定位器、严格重合判重（`isExactOverlap`）、并区间合并（`mergeInto`）+ JVM 测试。
- 仪器测试：真实 Android SQLite 上 onCreate/onUpgrade/PRAGMA/约束/级联/重开。

### Scope amendments（实现期决策，2026-09-28）
- **`Annotation` 落点改为 `android/core/.../domain/Annotation.java`**：`:data` 的 DAO 行类型与 `:app` 的 UI 逻辑需要同一个值类型，放 `app/ui` 会造出第二份类型或让 `:data` 反向依赖 `:app`；`:core` 是纯 JVM 域模块，`ReadingAnchor`/`BranchAnchor`（同为定位器值类型）已在此。「纯 JVM 可测」目标不变。
- **`SqliteChatStore` 接线移入 T-B**：T-A 内 DAO 的唯一 consumer 是测试，store 暴露面此刻没有任何生产调用方——按本仓「无读者的常量即死表面」纪律，T-A 不新增 store 方法；T-B（第一个消费者）接入时一并定义端口形态。
- **F1/F2/F3（db-migration-reviewer 2026-09-28，见方案 V1.1 §3.1/§3.3）进入验收口径**：DDL 含第 5 条 CHECK（HIGHLIGHT 恒空 body）；`listFor` 的 DELETE+SELECT 必须同事务（DAO 断言 `inTransaction()`）；insert javadoc 写明「仅 stable + 当前显示修订 digest 时写入」的调用方约定。
- **DAO 去掉 `mergeRange`（Scope 正文与方案 §3.4 的方法清单按此修订）**：合并流的定稿语义是「吸收」（`Annotation.mergeHighlights` 返回并区间 + `absorbedIds`，consumer 删旧行、插新行，同事务执行），单行区间 update 通道在该流中没有调用方；留一个无消费者的 DAO 方法即死表面。`listFor`/`insert`/`delete`/`updateBody` 保留（均有 T-B/D 消费者）。

### Out of scope
- span 渲染、菜单、便签 UI（T-B/C/D）；既有表改动。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增表与 DAO；既有 11 表 schema、既有 store 行为不变（V4 升级后旧数据逐行保留）。
- Producer/consumer: producer=ChatScreen 标记动作（T-B/D 接入）；consumer=标记读路径。本任务内 consumer=DAO 测试。
- Platform branches: 手机/平板同一 DB，无分支。
- Behavior-neutral non-applicability reason: N/A。

## Documentation impact record

- Changed runtime facts: `DATABASE_VERSION` 4→5；表清单 11→12。
- Authoritative evidence: schema 常量类 + 迁移语句常量。
- Documentation files updated: `android/README.md` 表清单/版本行（完成后）；表清单与架构文档同变更更新（sql.md 要求）。
- Superseded phrases reverse-searched: 「DATABASE_VERSION = 4」「11 张表」出现处完成后反查更新。

## Acceptance criteria

- [ ] 真实 Android SQLite（API 28+ 设备/模拟器）上：fresh install 经生产 `onCreate` 建出表+2 索引，`PRAGMA user_version=5`；`PRAGMA foreign_keys=ON`、`foreign_key_check` 空、`integrity_check=ok`。
- [ ] `onUpgrade` V4→V5 后既有行逐表保留（升级前后行数相等断言）。
- [ ] CHECK/唯一/FK 约束生效：非法 container 哨兵组合被拒；同 (message,container,定位,kind,start,end) 重复插入被拒；`message_id` 外键级联删除生效；NOTE 空 body 被拒、HIGHLIGHT 带 body 被拒。
- [ ] `AnnotationDao.listFor` 先删 digest 失配行再返回；事务回滚路径不留半迁移库。
- [ ] `Annotation` 纯逻辑 JVM 测试：严格重合判重（部分重叠≠重复）、相交合并为并区间、定位器 equals/边界。
- [ ] 关闭重开后数据一致；JVM 全量单测绿；Java-only guard PASS；`validate_tasks.py` PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: 三件套——`:core` AnnotationTest（Locator/Span/工厂/isExactOverlap/mergeHighlights 吸收流，302 行）；`:data` TreeLearnSchemaAnnotationsTest（DDL/迁移/版本链钉文本，210 行）；`:app` AnnotationDaoInstrumentedTest（真实 SQLite 上 fresh/upgrade/约束/DAO 语义，见 androidTest）。外加 SqliteSchemaInstrumentedTest 同批扩展为 12 表世界（含 V1 fixture 过滤器排除 annotations）。RED 取证另含 CMD3 `:app:assembleDebugAndroidTest`（编译仪器测试源集，见 Failure reason validated）。
- Command: 测试集 = `:core:test` + `:data:testDebugUnitTest`（均带 `--no-build-cache`）；RED 取证见 `artifacts/evidence/T-2026-09-28-annot-red-evidence.txt`，GREEN 取证见 `artifacts/evidence/T-2026-09-28-annot-green-evidence.txt`。
- Exit code: 1
- Observed failure: CMD1「程序包Annotation不存在」（AnnotationTest.java:45+）；CMD2「找不到符号」（TreeLearnSchemaAnnotationsTest.java:37+，Annotations 常量类缺失）；CMD3「找不到符号」（AnnotationDaoInstrumentedTest.java:15+，Annotation/AnnotationDao 缺失）。
- Failure reason validated: 三条失败均为「生产类未实现」的编译失败＝预期 RED；非环境竞态（编译到达 test source set 并点名缺失符号）；设备未占用（编译失败阶段无设备轮价值，真机证据在 GREEN 后取）。

### GREEN — minimum implementation
- Implementation: `:core` `domain/Annotation.java`（`Container`/`Kind` 枚举、`Locator` prose()/cell() 工厂 + -1 哨兵、`Span` 非空构造、`Merge`（span + absorbedIds 不可变列表）、`isExactOverlap` 严格重合、`mergeHighlights` 传递闭包吸收流、`toString()` 只带 body 长度）；`:data` `TreeLearnSchema.java` V5（`Annotations` 常量类 14 列 + 5 CHECK（含 F1：HIGHLIGHT 恒空 body）、唯一锚索引含 `kind`、`annotationsAnchorIndexStatement()`/`annotationsMessageIndexStatement()` fresh 与迁移共用、`MIGRATION_V4_TO_V5_STATEMENTS` 三语句全复用常量、`MigrationV4ToV5` 注册、`DATABASE_VERSION = 5`）与 `dao/AnnotationDao.java`（`insert` insertOrThrow、`listFor` 同事务 DELETE 失配行 + SELECT 显式过滤 digest（F2）、`delete` 幂等、`updateBody` note-only）；两处既有版本钉文本测试 bump 4→5（`ProviderModelSchemaTest`、`TreeLearnSchemaQuoteInvariantTest`，注释点名 T-026）；`SqliteSchemaInstrumentedTest` 适配 12 表世界（V1 fixture 过滤器排除 annotations）。`mergeRange` 按 Scope amendment 第 4 条不实现。
- Command: 测试集 = `:core:test` + `:data:testDebugUnitTest`（均带 `--no-build-cache`）；RED 取证见 `artifacts/evidence/T-2026-09-28-annot-red-evidence.txt`，GREEN 取证见 `artifacts/evidence/T-2026-09-28-annot-green-evidence.txt`。
- Exit code: 0
- Passing evidence: GREEN 实跑形式为两测试任务合并一条 `--rerun` 命令强制执行（:data 于 17:34 真实重跑；:core:test 的 XML（17:12）经同命令 17:33 BUILD SUCCESSFUL（up-to-date 判定）确认输入未变仍有效）。`AnnotationTest` 25/25（core test-results XML `tests="25" failures="0" errors="0"`）；`TreeLearnSchemaAnnotationsTest` 7/7（data test-results XML 17:34 重生成，`tests="7" failures="0"`）；两模块合计 288 tests / 0 failures / 0 errors（逐 test-results XML 的 `tests=/failures=/errors=` 属性汇总而得）。
- Regression evidence: :core 全部 20 个测试类、:data 全部 11 个测试类 failures=0（含版本 bump 后的 `ProviderModelSchemaTest` 6/6、`TreeLearnSchemaQuoteInvariantTest` 7/7）。构建期遭遇四次 `fileHashes.lock 拒绝访问`（别线构建持锁，性质判定＝缓存锁竞态：零 Kotlin 坐标、发生在构建服务启动阶段、非测试失败），锁释放后第五次成功；全程留痕 `artifacts/evidence/T-2026-09-28-annot-green-lockcontention-1723.txt`。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（估 460 行 >200 软上限，verifier 警告级；<500 红线内，先例 T-004/T-022）。

## Implementation notes

- **db-migration-reviewer 设计复核（2026-09-28，实现前）**：结论 PARTIAL——设计可过；F1（`CHECK (kind <> 'HIGHLIGHT' OR body = '')`）必须修入 DDL，已修入方案 §3.1；F2/F3（listFor 同事务清除+SELECT 过滤、insert 稳定语义）已采纳修入方案 §3.3；F4 保留 message 索引（实现时在常量旁注明理由）；F5 非负判定集中域层。真实引擎证据 = 本任务验收条款，GREEN 后补。证据：`artifacts/evidence/T-2026-09-28-annot-schema-design-review.txt`。
- **建表入场记录（table admission guard，sql.md 强制）**：
  - 为何不用既有存储：标记是按 (消息,容器,区间,kind) 可枚举、需唯一约束与级联删除的一等业务事实；塞进 messages 有界 JSON 会失去唯一索引判重与行级删除，且与既有 11 张规范化表的设计不一致。
  - 领域/权威来源：学习标记（对话域）；producer=用户标记动作；consumer=对话页渲染与读路径。
  - 行标识/基数/增长：`id`(UUID)；每消息 0~数百行，随使用线性增长。
  - 保留与删除：随消息级联删除；digest 失配自愈清除；无归档需求。
  - 敏感字段：body 为用户手写批注文本（本机 SQLite，无出域）。
  - 索引/约束：唯一锚索引（含 kind）+ message_id 索引 + 5 条 CHECK + FK ON DELETE CASCADE。
  - 跨实例：无（单机）。发布/升级：V4→V5 前向迁移，历史迁移不可改写。
  - 可观测性：DAO 操作随既有 store 日志纪律（无新增噪声）。
  - 废弃触发：若标记功能下线，随该特性删除表（前向迁移）。
  - 用户确认证据：方案 §3.1 完整 DDL 于 2026-09-28 随 8 项拍板获用户确认（方案变更记录 V1.1/15:23 行）；本表为第 12 张，超出「已确认七张」清单，按 sql.md 建表审批边界由父需求记录确认链。
- DDL 一律复用字符串常量；时间字段用应用显式写入的 UTC epoch millis `INTEGER`（sql.md）。
- `db-migration-reviewer` 在 schema 设计定稿后、实现收口前各过一遍。
- 仪器测试真机窗口与别线协调；AGP 测试后重装应用（工作区记忆）。

## Verification report

- Verdict: PENDING

## Handoff notes

- Completed: RED 三件套 + GREEN 实现（对方会话 16:44–17:01 写 Annotation/TreeLearnSchema V5/AnnotationDao/仪器测试，本会话以 25 例契约测试裁决全绿，javadoc 断句修复一处）；GREEN 记录五键 + `validate_tasks.py` 026 零 error（17:36）；文档同步（`android/README.md` 三处：领域类型表加 Annotation 行、TreeLearnSchema 行十二张+标记表、DAO 表加 AnnotationDao 行；方案文档 §1 现状行加注「T-026 实现后 5/12 张」、§5.1 措辞「现有」→「既有」）；Java-only 基础检查（T-A 产物零 .kt；dex 级 verifyNoKotlinDex 待别线静止后与设备轮同跑）。
- In progress: T-A 收口提交（分主题、排除设置页别线文件）。
- Blocker: 无（构建期 fileHashes.lock 竞态四连，锁释放后自愈，留痕见 evidence）。
- Unblock condition: —
- Next steps: 桌面 SQLite 补充验证（JVM 先例 `TreeLearnSchemaQuoteInvariantTest` 模式，明确标注补充非设备证据）→ db-migration-reviewer 收口前第二遍 → 真机 SQLite 仪器测试证据（设备轮，AGP 测后重装；`AnnotationDaoInstrumentedTest` 707 行已就绪，含真 V4 文件升级与 raw SQL 约束断言）→ verified 收口。
- Watch-outs: 与 T-B/C/D 的接口一旦定稿不再改签名；跨线并发期构建带 `--no-build-cache`；别线（设置页重设计 + ChatPresenter/ReasoningDisclosure 等）持续间歇构建，fileHashes.lock 冲突时按「零 Kotlin 坐标 + 构建服务启动阶段失败」判定性质，勿误判为代码红；别线文件严禁纳入 T-A 提交。
- 接力记录（2026-09-28 15:4x，第二会话）：曾误判上轮 RED 文件为别线产物并写了冲突的 `app/ui/AnnotationTest.java`，已删除（未提交历史，无损失）；仪器测试已按上轮 `:core` API 重写对齐（`Annotation.Locator/Span/Kind`、`insert(db,messageId,digest,annotation,at)`、`listFor→List<Annotation>`、无 Row 中间类型、无 mergeRange）。上轮 4 项 scope amendments 全部继续有效。
- 接力记录（2026-09-28 17:4x，并行实现线）：设备轮已全绿——`Starting 42 tests on CGA-W00 - 17`（HONOR CGA-W00，Android 17/API 37 ≥ minSdk 28）→ `Finished 42 tests`，BUILD SUCCESSFUL，`EXITCODE=0`，完整输出 `artifacts/evidence/T-2026-09-28-annot-green-device.log`（SqliteSchemaInstrumentedTest 同批断言 `foreign_key_check` 空 + `integrity_check=ok`）。桌面 SQLite 交叉复核 5/5 PASS（SQLite 3.53.1，`EXITCODE=0`）：`artifacts/evidence/T-2026-09-28-annot-desktop-sqlite.txt` + 脚本 `artifacts/tmp/desktop_sqlite_v5_probe.py`（语句自 TreeLearnSchema.java 行 541-569/646-662/1063-1072 逐字转录）。Java-only guard PASS（`EXITCODE=0`）：`artifacts/evidence/T-2026-09-28-annot-javaonly-guard.txt`。⚠️ 上一 Handoff 所写「707 行已就绪」为修复前快照：raw SQL 断言里的单引号裸字面量（`message_id='m-1'` 等 9 处）与 `delete(db,"a-never-existed")` 未过 Guard.id，曾致设备轮 6+1 失败；已全部 uuid 化（暂存区现为 713 行全绿版，42/42），勿回退 707 行。教训：批量正则替换 uuid 化会漏掉 raw SQL 字符串内嵌的字面量，改后必须 grep 回读 `'(m|a)-[a-z0-9-]*'`。
