# Treelearn Harness Task: 附件存储与文件管理页

id: T-2026-09-23-096
title: 附件存储与文件管理页
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: true
depends_on: [T-2026-09-23-file-management-spec-082.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务建附件存储布局与消息-附件引用关系，并交付文件管理页（列表/大小/删除/占用统计）。
- Problem/background: 知芽无附件体系；SPEC-082 定义了存储与引用规则。本任务先立数据与文件地基 + 管理页，选择与解析在 097。
- Users/scenarios: 用户在文件管理页看到聊天文件、大小、删除；删除被引用文件后消息标注附件缺失。
- Expected outcome: 应用私有目录附件存储约定、消息-附件引用表（或既有表扩展，按 SPEC-082）、文件管理页、删除链路、单测。
- Core flow and reversability: 复制入私有目录 → 登记 → 消息引用 → 管理页删除（确认弹窗）→ 引用消息显示缺失标注（文本不丢）。
- Inputs/outputs: 输入 SPEC-082；输出存储/DAO/UI/测试。
- State and exception handling: 文件物理缺失（已被系统清理）时列表标注；删除失败提示。
- Constraints and trade-offs: 触 SQLite schema（引用关系）→ 本任务含迁移，走 db-migration-reviewer，独立执行。
- AI recommendation and rationale: 引用关系用独立附件表挂消息 ID，避免改消息主表（消息表七表结构稳定）。
- User choice and custom additions: 无。
- AI-autonomous decisions: 附件文件名用内容哈希去重存储。
- User-confirmation decisions: 无（082 已拍板）。
- Acceptance evidence: RED/GREEN + 迁移验证（API 28+ 真实 SQLite）+ db-migration-reviewer。
- Facts: `android/data` SQLiteOpenHelper；设置页结构见 087。
- Assumptions: 082 verified。
- Unknowns: 私有目录被系统清理的频率。
- Risks: 引用表迁移破坏消息读取路径。
- Deferred: 云备份。
- Non-goals: 不做附件选择与解析（097）；不做预览。
- Skipped non-critical questions and reason: 占用统计格式（KB/MB 自适应）。

## Goal

附件可存储、可引用、可在文件管理页查看与删除，存量消息数据无损。

## Scope

### In scope

- 附件存储层（私有目录 + 哈希去重）、附件引用表 + 迁移 + DAO、文件管理页 UI、删除链路、单测。

### Out of scope

- 附件选择与解析入上下文（097）；孤儿清理调度（098）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 消息读写主路径不变；新增表不影响既有七表。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `android/data` SQLiteOpenHelper、消息读取 DAO、设置页导航。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增附件存储与引用表（逻辑表数量变化）。
- Authoritative code/config/migration evidence: 迁移 SQL 与 DAO。
- Documentation surfaces checked: 建表入场确认清单、SPEC-082。
- Documentation files updated: 建表文档增补附件表。
- Producer/consumer/sibling/compatibility paths checked: 097/098 将消费。
- Superseded phrases/examples reverse-searched: 表数量表述在 079 基线回写统一。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [x] RED/GREEN：存储登记/引用/删除/缺失标注断言。
- [x] API 28+ 真实 SQLite 迁移验证；db-migration-reviewer 复核 PASS。
- [x] 44dp/token/Java-only guard + 全量单测通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `AttachmentStorageInstrumentedTest`（新增，`app/src/androidTest/java/com/treelearn/app/`）——五组断言：①登记幂等（同字节流二次登记返回同 id、单行）；②文件落在应用私有目录（`getFilesDir()/attachments/`）且字节逐位一致；③引用挂接后按消息可解析（JOIN 出附件记录，含 displayName 等）；④删除附件后引用行仍在、`referencesOfMessage` 返回该引用且 record 为 null（「缺失标注」——消息文本引用它，文件记录消失）；⑤真实 V3 库经生产 helper 升级到 V4 后既有行完好、新表可写。RED 阶段 `AttachmentStore`/`AttachmentDao`/V4 schema 尚不存在，预期编译期失败。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.AttachmentStorageInstrumentedTest`
- Exit code: 1
- Observed failure: 编译失败——首条为 `AttachmentStorageInstrumentedTest.java:15: 错误: 程序包com.treelearn.app.files不存在`，其后为 `AttachmentStore`/`AttachmentDao`/`TreeLearnSchema.Attachments` 等符号缺失（日志内「错误」行共 28 处）。
- Failure reason validated: 失败全部指向本片尚未存在的新类型（存储登记端口、DAO、schema 第 8/9 张表），无一条指向既有行为——证明测试测的是新契约而非旧回归。

### GREEN — minimum implementation
- Implementation: ① `TreeLearnSchema`：新增 `Attachments`/`MessageAttachments` 表类与 DDL（`content_hash` UNIQUE 幂等去重；`message_id` 强制 FK + `ON DELETE CASCADE`；`attachment_id` 语义引用不声明 FK；`parse_state` CHECK 三态）、三条索引、`DATABASE_VERSION` 3→4、`MIGRATION_V3_TO_V4_STATEMENTS` + `MigrationV3ToV4` 注册（与 onCreate 共用同一 `createTable()`/定义数组，逐字一致）；② 新 `AttachmentDao`（insert/findById/insertReference/referencesOfMessage/delete，JOIN 用固定下标读列）；③ 新 `AttachmentStore`（`app/files`，私有目录 `attachments/`、SHA-256 命名、字节逐位写回、删除文件+记录）；④ 新 `FileManagerPresenter` + `SqliteFileManagerStore` + `FileManagerScreen`（设置页「文件管理」入口 → 代码构建子页，行内展示名称/类型/大小/解析态/引用数/缺失标注，确认先行删除）；⑤ `MainActivity` 接线（房间字段/入口/返回链）；⑥ `SettingsScreen` + `screen_settings_body.xml` + `strings.xml` + `ids.xml` 支撑；⑦ 既有测试同步：`SqliteSchemaInstrumentedTest`（表集/列 spec/索引集/V1 fixture 过滤）、`ProviderModelDaoInstrumentedTest`（V3 形状 fixture + SEED）、`ProviderModelSchemaTest`/`TreeLearnSchemaQuoteInvariantTest`（版本字面 3→4）。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.AttachmentStorageInstrumentedTest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-096-green-2026-09-24.log`——与 `FileManagerScreenInstrumentedTest` 合并执行 7/7（Starting 7 tests on ALP-AN00 - 13 / Finished 7 tests / BUILD SUCCESSFUL）。
- Regression evidence: `artifacts/evidence/T-096-schema-instrumented-2026-09-24.log`（schema 25/25）；`T-096-full-unittest-2026-09-24.log`（app 单测 615/0/0，XML 69 文件汇总）；`T-096-check-2026-09-24.log`（EXITCODE=0，含 data 模块 58 例与 lint）；`T-096-nokotlin-2026-09-24.txt`（PASS，EXITCODE=0）；`T-096-regression-instrumented-2026-09-24.log`（BottomTabShell+LibraryArchive+NavigationStore+TreeCanvasStore+ProviderModelDao 31/31，含真实 V3→V4 升级用例）。db-migration-reviewer 复核 PASS（迁移与 DDL 逐字一致、纯增量、DAO 下标正确；3 条 non-blocking 建议已记入 Handoff）。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 触 schema，独立执行不并行；db-migration-reviewer 必走。
- 实现者禁读 RikkaHub 源码。

## Verification report

- Verdict: PASS
- Evidence: `artifacts/evidence/T-096-attachment-storage.txt`（CMD-1 起，命令+退出码内嵌自证，Python 实时执行生成）。要点：RED EXITCODE=1（编译失败，符号全为新契约）；GREEN 7/7 真机（ALP-AN00 - 13）；schema 25/25；升级用例（V3→V4 真机）全绿；app 单测 615/0/0；check 0；Java-only PASS；回归 31/31；db-migration-reviewer PASS。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删除既有断言；`SqliteSchemaInstrumentedTest` 的表集/列 spec/索引集按入场记录 §5.2 扩到 11 表（新增 `attachments`/`message_attachments` 列 census，含 `extracted_text` 的显式 `:0` 可空标记）；`ProviderModelDaoInstrumentedTest` 的「上一版本」fixture 从 V2 形状改为 V3 形状（+SEED），因为 `PREVIOUS_SCHEMA_VERSION` 随迁移注册表自动前移到 3——fixture 形状必须跟着版本走，这是升级用例在本片暴露并修正的真实缺口；两个 data 模块版本字面 3→4 属既有守卫的同步。
- Gaps: 引用行缺 `UNIQUE(message_id, attachment_id)`（db-migration-reviewer 建议 2）：UUID 主键下同消息同附件可重复引用，SPEC-082 未明文要求唯一约束，是否收紧留给 097/098 或用户裁决；管理页观感（行距、字号）由人 review；`referencesOfMessage` 多选未读的 `r.message_id` 列（建议 1）留作后续微调。
- Risks: 最大风险是迁移重放（CREATE TABLE 撞车）与 fixture 形状漂移——真机 25+31 例已把两条路径都走到；其次是删除链路（053）与新表的交互，由 `message_id` CASCADE + 回归 31/31 中的 LibraryArchive 用例覆盖。

## Handoff notes

- Completed: 082 已 verified、入场记录 §5.2 已获用户确认（双表 + CASCADE + attachment_id 语义引用）；双表 + V4 迁移 + DAO + AttachmentStore + 文件管理页 + 设置入口全部落地；db-migration-reviewer PASS。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: T-097（解析与 extracted_text 缓存）与 T-098（引用/删除链路）按链序开工；`parse_state` 三态已就位，097 只需写 `extracted_text` 与状态流转。
- Watch-outs: (1) 迁移语句已发布即冻结——后续任何 schema 变更必须新增 V4→V5，不得改写 `MIGRATION_V3_TO_V4_STATEMENTS`；(2) fixture 形状必须跟随 `PREVIOUS_SCHEMA_VERSION` 派生值同步演进，下次版本 bump 时 `ProviderModelDaoInstrumentedTest.versionTwoCreateStatements` 与 `SqliteSchemaInstrumentedTest.versionOneCreateStatements` 都要复查过滤清单；(3) 管理页删除只删文件与记录、引用行转缺失标注，绝不触碰消息文本（053 主路径不受影响）；(4) db-migration-reviewer 建议 2（引用行唯一约束）未采纳前，097/098 对同一引用的写入必须各自幂等；(5) 与 097/098 共享 `strings.xml`/README/构建目录，必须串行。
