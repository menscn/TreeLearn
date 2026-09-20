# SQL 与永久表治理

读取时机：任何 SQL（含 Java 内嵌 SQL）、Flyway、建表或数据库复核前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## UTC 时间存储规范

- 所有表示真实时间点的 MySQL 字段统一使用 UTC `DATETIME(3)` 语义；MySQL 实例、Flyway 与应用连接 session 必须为 UTC，并在应用启动时失败关闭验证。
- 新建或修改的时间点默认值使用 `UTC_TIMESTAMP(3)`，不得使用依赖 session 的 `NOW()`、`CURRENT_TIMESTAMP` 或 `CURDATE()`。MySQL `ON UPDATE CURRENT_TIMESTAMP(3)` 仅可在连接已强制 UTC 且有启动校验时保留；业务关键更新时间优先由应用显式写入 UTC。
- `DATE`、生日、业务自然日不是时间点，不做时区换算。签到、自然日/月等规则可使用明确的业务时区计算边界，但传入查询和落库的边界时间点必须转换为 UTC。
- 禁止根据服务器、开发机或数据库默认时区猜测无 offset 时间。历史数据来源不明确时不得整表统一加减时区；只能在证据充分、范围可审计且已获授权的 migration 中修复。
- 时间 SQL 变更必须在真实 MySQL 上验证实例/session 时区、默认插入、自动更新、已有行不变及业务跨日边界。

## Real MySQL execution guard

任何新增或修改的 SQL 代码或 SQL 脚本，在变更可被报告为正确、完整、PASS 或已验证之前，必须对隔离的真实 MySQL 实例执行。这适用于 Flyway 迁移、repository/JdbcTemplate/MyBatis SQL、schema 与索引变更、seed 或初始化 SQL、数据修复/回填、导入/导出、清理、对账与运维维护脚本。

- 单元测试、mock、H2 或其它替代数据库、SQL 字符串断言、解析器/linter 检查、迁移契约测试与代码复核仅作为补充证据；都不能替代真实 MySQL 执行。
- 使用与项目支持的生产版本兼容的一次性本地/CI/测试 MySQL 实例。绝不为临时验证使用生产数据库。
- 证据必须记录 MySQL 版本、精确命令或入口、起始 schema/数据假设、成功执行情况，以及对相关 schema、索引、约束、行数或数据副作用的直接验证。当变更 SQL 声称支持重要的失败、重跑/幂等、从既有 schema 升级等路径时，要实际执行这些路径。
- 当 SQL 是 Flyway 迁移时，须通过真实迁移路径运行 Flyway 或应用启动，而不是只执行复制的语句，并同时验证迁移历史与最终 schema/数据。
- 若真实 MySQL 环境不可用，相关 SQL 任务或复核即为 blocked / unverified。项目校验器与 `db-migration-reviewer` 不得仅凭代码或单元测试证据返回 PASS。

## Permanent table admission guard

每个提议的 `CREATE TABLE` 在实现前必须包含书面的建表入场记录。该要求适用于直接执行与 Harness 流程。

- 证明为何扩展既有权威表、有界 JSON 列、Redis、对象存储、view/CTE 或事务作用域的临时表不足以满足需求。
- 记录所属领域、权威来源、生产者、消费者、行标识、预期基数/增长、保留与删除任务、敏感字段、索引/约束、跨实例要求、发布/回填、可观测性及明确的废弃触发条件。
- 仅用于一次性修复、迁移暂存、归档副本、重复的审计事实、反规范化映射或投机性未来使用的永久表默认被拒绝。它们需要明确的用户批准，外加具名的运行时/运维消费者与经过测试的清理/废弃路径。
- 一个特性多于一张新的永久表，或任何重复的业务事实，即使脱离 Harness 也需要明确的用户确认与 `db-migration-reviewer` 复核。
- 禁止运行时 DDL。schema 变更必须使用新的前向-only Flyway 迁移；历史迁移不得编辑。表清单与相关架构/业务文档必须在同一变更中更新。
- 复核者在这些情况下必须返回 FAIL：入场记录缺失、备选方案分析流于结论、生命周期无 owner，或无明确批准下把临时关注建模为永久表。

## 建表审批边界

所有新建永久表在实现前均须取得用户明确确认，不仅限于例外类型；必要性证明按上文填写。补充规范按需补充到项目治理文档。

## db-migration-reviewer 执行与复核职责

- READONLY：拒绝代码编辑。在 backend 迁移 SQL 变更及需要该 guard 的建表入场场景触发。
- 检查迁移顺序、幂等预期、与既有 schema 的兼容性，以及编辑历史迁移的风险。
- 应用上述 real-MySQL 与 table-admission 要求。变更迁移缺少真实 MySQL 执行证据时返回 FAIL；仅当 MySQL 执行存在但要求的升级、重跑/幂等、失败或结果检查不完整时返回 PARTIAL。
- 报告 PASS、FAIL 或 PARTIAL，并给出具体的 schema 关注点。
- 对 DROP 迁移，要求所有依赖、非空数据与保留检查都发生在首次 DROP 之前，并在未知状态时 fail closed；当 `IF EXISTS` 会掩盖 schema 漂移时拒绝之。
- 变更 SQL 缺少真实 MySQL 执行与直接结果证据时，项目 Harness 校验器也必须使验证失败。