# SQL 与永久表治理

读取时机：任何 SQL（含 Java 内嵌 SQL）、Flyway、SQLiteOpenHelper、建表或数据库复核前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## 目标引擎判定

- 先用生产代码、活动配置与实际 consumer 判定 SQL 的运行引擎，再选择对应守门；不得因为验证环境方便而改用无关数据库。
- `backend/**` 的 Flyway、JdbcTemplate、MyBatis、repository SQL 默认按生产 MySQL 验证，除非活动配置明确证明使用其它引擎。
- `android/**` 中由 `android.database.sqlite.SQLiteDatabase`、`SQLiteOpenHelper` 或等价 Android 平台 API 执行的 SQL，按真实 Android SQLite 验证；不得要求为这类 SQL 额外搭建无生产消费者的 MySQL。
- 同一变更若同时影响多个生产数据库引擎，每个引擎都必须分别取得真实执行证据。无法确定目标引擎时先追查调用链，不能自行选择较宽松的守门。

## UTC 时间存储规范

- 所有表示真实时间点的数据统一使用 UTC 语义；业务自然日、生日等 date-only 值不做时区换算。
- MySQL 时间点字段使用 UTC `DATETIME(3)` 语义；实例、Flyway 与应用 session 必须为 UTC，并在启动时失败关闭验证。默认值使用 `UTC_TIMESTAMP(3)`，不得依赖 `NOW()`、`CURRENT_TIMESTAMP` 或 `CURDATE()` 猜测时区。
- Android SQLite 时间点使用应用显式写入的 UTC epoch milliseconds `INTEGER`；不得持久化无 offset 的本地日期时间字符串，也不得依赖 SQLite `CURRENT_TIMESTAMP` 或设备默认时区生成业务关键时间。
- 签到、自然日/月等规则可使用明确的业务时区计算边界，但传入查询和落库的时间点边界必须转换为 UTC。
- 历史数据来源不明确时不得整表统一加减时区；只能在证据充分、范围可审计且已获授权的 migration 中修复。
- 时间 SQL 变更必须在对应真实引擎上验证写入、读取、排序、升级后已有行不变及适用的跨日边界。

## 真实目标引擎执行守门

任何新增或修改的 SQL、schema、索引、迁移、seed、数据修复、导入导出、清理或维护代码，在报告为正确、完整、PASS 或已验证前，必须通过其真实生产数据库引擎执行。

- mock、SQL 字符串断言、解析器/linter、代码复核和纯 JVM 单元测试只能补充，不能替代真实引擎。
- 证据必须记录目标引擎及版本、精确命令或入口、起始 schema/数据假设、执行结果，以及对 schema、索引、约束、行数或副作用的直接验证。
- 声称支持新装、升级、重开、失败回滚、重跑/幂等或级联删除时，必须实际执行对应路径。
- 真实目标引擎或必要设备不可用时，相关 SQL 任务为 `blocked` / `unverified`；不得用另一个数据库通过后宣称等价。

### MySQL / Flyway

- 使用与项目支持版本兼容的隔离本地、CI 或测试 MySQL，绝不使用生产数据库做临时验证。
- Flyway 迁移须通过真实 Flyway 或应用启动入口运行，不得只复制 SQL 单独执行；同时检查迁移历史与最终 schema/数据。
- H2、SQLite 或其它替代引擎不能替代真实 MySQL。
- 涉及 session 时区、默认值或自动更新时间的变更，还须直接验证实例/session UTC、默认插入、自动更新与既有行。

### Android SQLite / SQLiteOpenHelper

- 使用 API 28+ 模拟器或真实设备，通过 Android framework 的 `SQLiteDatabase`/`SQLiteOpenHelper` instrumentation 测试执行；不得用 H2、JDBC SQLite、Robolectric shadow 或仅内存 fake 代替最终证据。
- 新装必须走生产 `onCreate`；升级必须从每个仍受支持的已发布 `PRAGMA user_version` 通过生产 `onUpgrade` 或专用 migration runner 升到最新版本。首个尚未发布版本可将升级路径记为 N/A，但新装、关闭重开和约束验证仍必需。
- 每次打开显式启用并验证 `PRAGMA foreign_keys=ON`；验证 `PRAGMA foreign_key_check` 无结果、`PRAGMA integrity_check` 为 `ok`，并直接检查关键表、列、索引和 `user_version`。
- 必须覆盖事务提交与回滚、唯一/非空/外键约束、声明的级联删除、关闭重开后数据一致性，以及变更涉及的真实 DAO 查询。
- migration 失败不得留下被误报为最新版本的半迁移数据库；失败路径须证明事务回滚或明确的失败关闭行为。
- instrumentation fixture、APK 和测试数据库不得包含真实用户内容或真实 API Key。

## Permanent table admission guard

每个提议的 `CREATE TABLE` 在实现前必须包含书面的建表入场记录。该要求适用于直接执行与 Harness 流程，也适用于 MySQL 与 Android SQLite。

- 证明为何扩展既有权威表、有界 JSON、view/CTE、事务作用域临时结构或该引擎可用的其它既有存储不足以满足需求。
- 记录所属领域、权威来源、生产者、消费者、行标识、预期基数/增长、保留与删除任务、敏感字段、索引/约束、跨实例要求、发布/升级、可观测性及明确的废弃触发条件。
- 仅用于一次性修复、迁移暂存、归档副本、重复审计事实、反规范化映射或投机性未来使用的永久表默认被拒绝；例外需要明确用户批准、具名运行时/运维消费者与经过测试的清理/废弃路径。
- 一个特性多于一张新永久表，或任何重复业务事实，即使脱离 Harness 也需要明确用户确认与 `db-migration-reviewer` 复核。
- MySQL schema 变更只允许新增前向 Flyway migration，禁止修改已发布 migration，禁止业务请求路径运行 DDL。
- Android SQLite schema DDL 只允许位于生产 `SQLiteOpenHelper.onCreate/onUpgrade` 或集中、版本化的 migration runner；禁止散落在 Repository、页面或普通业务路径。已发布版本的迁移步骤不可改写，schema version 必须单调递增。
- 表清单与相关架构/业务文档必须在同一变更中更新。
- 入场记录缺失、备选方案分析流于结论、生命周期无 owner，或无明确批准把临时关注建模为永久表时，复核必须 FAIL。

## 建表审批边界

所有新建永久表在实现前均须取得用户明确确认，不仅限于例外类型；必要性证明按上文填写。Treelearn V1 技术方案已确认的七张 Android SQLite 逻辑表仍须由实现与 reviewer 核对是否与已批准清单一致，新增第八张表必须重新取得用户确认。

## db-migration-reviewer 执行与复核职责

- READONLY：拒绝代码编辑。在 backend 迁移 SQL、Android SQLite schema/migration 或建表入场场景触发。
- 先判定目标引擎，再检查迁移顺序、历史不可变性、schema 兼容性和对应真实引擎证据；不得把 MySQL 与 SQLite 的证据互相替代。
- MySQL 变更应用真实 MySQL/Flyway、UTC 与 table-admission 要求；Android SQLite 变更应用真实 Android SQLite、新装/升级/重开、PRAGMA、事务/约束与 table-admission 要求。
- DROP/删表迁移要求依赖、非空数据与保留检查发生在首次破坏性操作之前，并在未知状态时 fail closed；可能掩盖 schema drift 的条件式删除须拒绝。
- 报告目标引擎、PASS/FAIL/PARTIAL、精确证据和修复指令。缺少对应真实引擎执行证据时必须 FAIL；仅当已有真实执行但升级、失败或结果检查不完整时可 PARTIAL。
- 项目 Harness verifier 不得在真实目标引擎证据缺失时把数据库相关任务判为 PASS。
