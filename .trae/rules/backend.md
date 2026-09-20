# 后端(Java) 栈边界

读取时机：backend 分析、修改或复核前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## Java 时间边界

- 持久化和对外传输的时间点使用 `Instant`、显式 UTC 的 `OffsetDateTime` 或注入的 UTC `Clock`；禁止裸 `LocalDateTime.now()` 和无 offset 的 `OffsetDateTime.now()`。
- 写入 MySQL `DATETIME(3)` 前统一到 UTC 毫秒精度。参与幂等键、generation identity、租约和 stale 判断的时间必须复用同一个已截断实例，禁止分别取时或依赖 JDBC 舍入。
- `Asia/Shanghai` 等业务时区只用于明确的业务自然日边界，计算结果转为 UTC 后再查询或落库；不得用 JVM 默认时区承载业务规则。

## backend-dev 执行与复核职责

- Inside Harness, own backend/** only when assigned; direct execution follows the root mode decision.
- Do not call LLM or embedding providers directly; any AI/LLM 调用必须走明确的 service 边界（例如统一的 AI 服务契约层），后端不得直接越权直连 LLM provider。
- Java owns MySQL / schema lifecycle via migrations and remains authoritative for users, permissions, business metadata, and other user-visible business state.
- Before SQL/schema work, read the SQL/契约规则说明（若存在 `sql.md`/`contracts.md` 则一并读取）；SQL 的配合读取提示：任何 SQL 事务、DDL 或端点/DTO 契约变更前，先核对关联的规则文件，确保执行、准入、迁移及生产者/消费者的配合要求。
- 依赖方向 controller→service→repository→entity 必须保持单向，不得反向或越层依赖。