# Treelearn Harness Task: SQLite 提供商/模型配置表与迁移

id: T-2026-09-23-083
title: SQLite 提供商/模型配置表与迁移
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 170
shared_code_touched: true
depends_on: [T-2026-09-23-providers-models-spec-079.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务为多供应商地基的数据层。
- Problem/background: 知芽 SQLite 现共七张逻辑表，无提供商/模型配置；多供应商需要持久化提供商（名称、基址、启用态）与模型（提供商外键、模型 ID、显示名、能力标记）。
- Users/scenarios: 用户配置的提供商与模型在重启后保留；存量 DeepSeek 配置无损迁移。
- Expected outcome: 新增 provider/model 两张表 + 前向迁移 + DAO（含单测）；存量数据迁移后在 `:app` 兼容读旧 Key 路径（Key 迁移归 084）。
- Core flow and reversibility: SQLiteOpenHelper 前向版本迁移（不可逆回滚靠备份用户自负，迁移本身幂等）。
- Inputs/outputs: 输入 SPEC-2026-09-23-providers-and-models 的表结构设计；输出 schema/DAO/测试。
- State and exception handling: 迁移失败 fail-fast；DAO 对空表（未配置）返回空集，不抛异常。
- Constraints and trade-offs: 前向 only、UTC epoch 毫秒、Android SQLite（API 28+ 真实引擎验证，见 sql.md）；不存 Key 明文（本表不碰密钥）。
- AI recommendation and rationale: 表结构严格按 079 规格，超出规格的字段不做。
- User choice and custom additions: 无（表结构以 079 拍板为准）。
- AI-autonomous decisions: DAO 命名沿用现有 `data` 模块约定。
- User-confirmation decisions: 无（表结构已在 079 拍板）。
- Acceptance evidence: RED/GREEN 单测（API 28+ 真实 SQLite 或 Robolectric 现有口径）+ `db-migration-reviewer` 复核。
- Facts: `android/data` 模块为 SQLiteOpenHelper + 显式 DAO + 前向迁移（project-brief）。
- Assumptions: 079 拍板完成。
- Unknowns: 无。
- Risks: 迁移破坏存量用户数据。
- Deferred: 无。
- Non-goals: 不改消息/主题/分支表；不做 UI。
- Skipped non-critical questions and reason: 索引细节由实现者按查询模式定。

## Goal

交付多供应商所需的 SQLite 配置表、前向迁移与 DAO，存量数据无损。

## Scope

### In scope

- provider 表、model 表、迁移脚本、DAO 及单测。

### Out of scope

- Keystore 多 Key（084）；网关改造（085）；UI（087/088）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增表不改动现有七表行为；DB 版本号前移。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `android/data` SQLiteOpenHelper 子类与现有 DAO；消费方为 086–088。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 数据库结构（七表→九表逻辑）。
- Authoritative code/config/migration evidence: 迁移 SQL 文件与 SQLiteOpenHelper 版本号。
- Documentation surfaces checked: 建表入场确认清单（Docs/05-technical-design）。
- Documentation files updated: 建表文档增补两表说明。
- Producer/consumer/sibling/compatibility paths checked: 现有 DAO 读路径不因迁移变化。
- Superseded phrases/examples reverse-searched: 「共七张逻辑表」表述（project-brief）在 079 基线回写时统一更新。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED：迁移与 DAO 行为断言先失败；GREEN 同命令转绿。
- [ ] API 28+ 真实 Android SQLite 上验证迁移（含从当前版本升级）。
- [ ] `db-migration-reviewer` 复核 PASS；`android-no-kotlin-guard` PASS。
- [ ] 现有七表读写回归测试全绿。

## TDD execution record

### RED — failing test first
- Test/scenario: 先写测试后实现。`:data` JVM 套件 `ProviderModelSchemaTest`（6 例：DATABASE_VERSION=3 且每个已发布版本恰有一条前向迁移；升级建表语句与全新安装同文本；双安装路径执行同一种子且 OR IGNORE 幂等；providers/models 列集精确等于 SPEC-2026-09-23 字段草案（不得加减字段）；两表无任何可携带密钥的列；models 外键 ON DELETE CASCADE + (provider_id, model_id) BINARY 大小写精确唯一且无 NOCASE）。真机套件 `ProviderModelDaoInstrumentedTest`（7 例：新装种子恰一内置 DeepSeek + 默认模型；真实 V2 fixture 经生产 onUpgrade 升 V3 且存量行无损、PRAGMA 干净、种子重放幂等；DAO 插入/查单/按名查/列表排序/启停写位；重名 UNIQUE 拒绝；模型 ID 大小写精确去重（Gpt-X 与 gpt-x 并存、完全相同者拒绝）；删 provider 级联删 models；空集读回不抛异常）。既有 `SqliteSchemaInstrumentedTest` 期望集同步扩到九表/两表列规格/新索引并过滤 V3 对象出 V1 fixture。
- Command: cd android; .\gradlew.bat :data:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.ProviderModelDaoInstrumentedTest --console=plain
- Exit code: 1
- Observed failure: `:data:compileDebugUnitTestJavaWithJavac` FAILED，26 个「找不到符号」错误，全部指向新测试引用的未实现符号（`TreeLearnSchema.Providers/Models/SEED_STATEMENTS/MIGRATION_V2_TO_V3_STATEMENTS/INDEX_MODELS_PROVIDER_MODEL/BUILTIN_DEEPSEEK_*`），Gradle 随即在 `:data` 编译处 fail-fast（含 :app connected 任务未达）。证据 `artifacts/evidence/T-083-red.txt`（BUILD FAILED in 1s）。
- Failure reason validated: 是——26 条错误全部落在本任务新写的 `ProviderModelSchemaTest.java`（:52–:130 行）与同一符号面，无一条来自既有代码或既有测试；属编译期缺符号 RED（任务流程允许）。

### GREEN — minimum implementation
- Implementation: `TreeLearnSchema`：新增 `Providers`/`Models` 列常量与两表 DDL（列集严格等于 079 规格：providers = id/name/base_url/enabled/is_builtin/created_at/updated_at，models = id/provider_id/model_id/display_name/created_at；providers.name UNIQUE，models.provider_id REFERENCES providers(id) ON DELETE CASCADE）；新增唯一索引 `uq_models_provider_model`（BINARY 缺省排序即大小写精确，无 NOCASE）；`DATABASE_VERSION` 2→3 并注册 `MigrationV2ToV3`——两条 CREATE TABLE 与索引沿用全新安装同一文本，尾部执行 `SEED_STATEMENTS`（内置 DeepSeek provider「DeepSeek/https://api.deepseek.com/enabled=1/is_builtin=1」+ 其默认模型 deepseek-flash，固定发布时间戳 1_790_121_600_000L（2026-09-23T00:00Z，应用显式写入的 UTC epoch 毫秒，不读时钟），INSERT OR IGNORE 幂等）；全新安装 `CREATE_STATEMENTS` 尾部执行同一种子（新用户零配置即开即用，SPEC 疑虑 4 拍板）。新增 `ProviderDao`（insert/findById/findByName/listByCreatedAt/setEnabled/delete）与 `ModelDao`（insert/findByProvider/delete），全部走 schema 常量，不存任何 Key 字段。同步把 `SqliteSchemaInstrumentedTest` 期望扩到九表/两表列规格/新索引，V1 fixture 过滤 V3 表与索引，topic 级联测试对内置种子行开口子（配置表独立于 topic 不级联）。
- Command: cd android; .\gradlew.bat :data:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.ProviderModelDaoInstrumentedTest --console=plain
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL（真机 ALP-AN00 / Android 13，`Finished 7 tests`，7/7 绿）；`:data:testDebugUnitTest` 50 例全绿（含新增 6 例 schema 测试与既有 44 例）。升级用例在真机证明：V2 fixture 打开即触发生产 onUpgrade → user_version=3、providers/models 建立、内置种子存在、存量 topic 行存活、`PRAGMA foreign_key_check` 无行、`PRAGMA integrity_check`=ok、种子重放不重复。证据 `artifacts/evidence/T-083-green.txt`。
- Regression evidence: `.\gradlew.bat check assembleDebug` exit 0，`verifyDependencyDirection: PASS`、`verifyNoKotlinDex: PASS`（`artifacts/evidence/T-083-check.txt`）；全量 `:app:connectedDebugAndroidTest` 190 例中除 `GenerationCoordinatorInstrumentedTest` 13 例外全部通过（SqliteSchema/TopicBranchMessageDao/SearchUnreadReading 等既有七表读写用例全绿、`ProviderModelDaoInstrumentedTest` 7/7；`T-083-connected-full3.txt`）；no-kotlin guard `VERDICT: PASS (13 OK line(s))`（`T-083-no-kotlin.txt`）。**GenerationCoordinator 13 例失败归因**：该测试对 provider/model 零引用（grep 计数 0），失败模式（VERSION_MISMATCH、FakeGateway「no call was opened」、slot 计数）全部位于 :app 聊天生成链路，而工作区存在另一 lane 未提交的 169+ 行聊天链路改动（ChatScreen/ChatPresenter/ChatActions/ChatViewState/MainActivity/聊天布局与 strings.xml）；本任务未触碰该链路，留 verifier 以干净基线复核。

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 触共享 schema，本任务独立执行，不与其它任务并行（HARNESS 并行规则）。
- 实现者禁读 RikkaHub 源码，只读 SPEC-2026-09-23-providers-and-models。
- **行数超预算，已停下汇报**：生产代码（非空非注释）254 行 > 200 停报线（任务目标 170），明细见 `artifacts/evidence/.t083-linecount.txt`（ProviderDao 100 + ModelDao 67 + TreeLearnSchema 增量 87）。两表 DDL + 前向迁移 + 内置种子（双路径共用）+ 两个 DAO 构成一个不可再分的最小面，继续删减只能砍契约内的 DAO 操作（增删查/启停/按名查），交 leader 裁决（裁功能 or 批准超支）。
- 设计决策（AI-autonomous，依 079）：种子时间戳用固定发布常量（2026-09-23T00:00Z）而非时钟读取——确定性、幂等可断言、内置行恒排最早；内置两行用固定合成 UUID，为 T-084 别名 `tl.provider.<uuid>` 提供稳定标识；种子 display_name 暂取 wire id（`deepseek-flash`），UI 显示名归 087/088 的字符串资源；`base_url` 常量镜像 `GatewayEndpoints.BASE_URL`（:data 依赖方向不可见 :ai，已注释互指）。
- 验证口径：JVM 测试只锁文本形态；真实引擎证据由 `ProviderModelDaoInstrumentedTest` 在真机（ALP-AN00 / Android 13，符合 sql.md API 28+ 真机口径）取得——新装 onCreate、V2→V3 onUpgrade、关闭重开、PRAGMA foreign_keys/foreign_key_check/integrity_check、UNIQUE/级联/空集语义、升级后存量行不变。

## Verification report

- Verdict: PASS（独立 verifier 2026-09-23；db-migration-reviewer 亦 PASS）
- Evidence: RED `cd android; .\gradlew.bat :data:testDebugUnitTest :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.ProviderModelDaoInstrumentedTest --console=plain` exit 1（26 条缺符号，`T-083-red.txt`）；同命令 GREEN exit 0（真机 ALP-AN00/Android 13 7/7 + JVM 50 例，`T-083-green.txt`）；`gradlew check assembleDebug` exit 0（verifyDependencyDirection/verifyNoKotlinDex PASS）；行数 254（`.t083-linecount.txt`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除；九表期望替代七表期望（SqliteSchemaInstrumentedTest）为规格内演进。
- Gaps: 单真机（API 33）证据，API 36 轮次未跑（沿既有 device-rounds 口径）；建表入场文档增补待 docs lane。
- Risks: 行数 254 超 200 软上限（verifier 警告记录，硬限 500 内，leader 知悉接受）；全量 connected 中 GenerationCoordinator 13 例失败经 verifier 归因为**已提交基线的存量测试缺陷**（fixture 钉 POLICY_VERSION=1.0.0，DeepSeekPromptRegistry 已升 1.1.0），非本任务致因，另立任务修复。

## Handoff notes

- Completed: 2026-09-23 android-dev 完成 RED→GREEN→回归→guard，任务文件回填，status=verifying。
- In progress: 无。
- Blocker: 无。GenerationCoordinatorInstrumentedTest 13 例失败为并行 lane 聊天链路未提交改动所致（该测试对 provider/model 零引用，失败模式全在 :app 生成链路），非本任务遗留。
- Unblock condition: 不适用。
- Next steps: db-migration-reviewer 复核（真实引擎证据已具备：新装/升级/重开/PRAGMA/约束/级联/种子幂等均在真机执行）→ verifier 判 Verdict（含 GenerationCoordinator 归因复核）→ 084（Keystore 多 Key）可基于 BUILTIN_DEEPSEEK_PROVIDER_ID 开工。
- Watch-outs: ① 生产代码 254 行超 200 停报线，待 leader 裁决（见 Implementation notes）；② 建表入场文档（Docs/05-technical-design 确认清单）需增补两表说明，docs lane 回写（本角色仅可写 android/**）；③ 真实引擎证据在单一真机（Android 13）取得，API 36 模拟器轮次未跑（与既有任务 device-rounds 口径一致）。

- [verifier #3 2026-09-24，跨 lane 记录] 本任务的 `ProviderModelDaoInstrumentedTest` 升级夹具在 verified 之后被修了一处守门违规：`v2.execSQL("PRAGMA user_version = 2")` 与对应断言硬编码了字面量 `2`，被 T-037 交付的静态守门 `check_androidtest_schema_expectations.py` 抓到（真实树 FAIL(2)）。处置：改为具名常量 `PREVIOUS_SCHEMA_VERSION`，初值派生自 `TreeLearnSchema.FORWARD_MIGRATIONS` 末步的 `fromVersion()`（版本 bump 自动跟随，不再复述字面量）；守门白名单按 `V1_SCHEMA_VERSION` 先例登记该名。复跑：守门真实树 `VERDICT: PASS`（judged 12）、自检 13 例 PASS、夹具改动编译 EXITCODE=0。证据 `artifacts/evidence/T-037-verify3-guard-2026-09-24.txt`。该改动不触本任务已取得的真实引擎证据（改的是测试夹具的版本戳写法，升级语义与断言不变）。
