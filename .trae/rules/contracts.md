# 契约与专项 guard 路由

读取时机：API、SSE、原生桥接、共享错误、契约文档或共享配置改动及复核前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## 路由表

| 路径或触发点 | Owner | 对应 guard |
|---|---|---|
| `frontend/**` | `frontend-dev` | API 契约或接口客户端变化时 `contract-guardian` |
| `backend/**` | `backend-dev` | API 变更时 `contract-guardian`；Flyway 迁移时 `db-migration-reviewer` |
| `android/**` | `android-dev` | 完成前 `android-no-kotlin-guard` |
| API 契约文档 | 当前任务 owner | `contract-guardian` |
| 前端到 Android 桥生产者/消费者契约、资源集/数量或共享错误语义 | 分配的栈 owner | `contract-guardian`；Android 变更还需 `android-no-kotlin-guard` |
| `backend/**/db/migration/V*__*.sql` | `backend-dev` | `db-migration-reviewer` |
| `docker-compose.yml` / `.env.example` | leader 协调 | 手动协调与复核 |

> 注：Treelearn 暂未启用 Python 服务；如日后启用，其路径与契约 guard 按源工程规格补充。

## 共享代码 guard

共享代码区在完成前需要明确的 guard/复核；隔离任务仅需在 Harness 内进行（直接执行不需要任务文件）：
- API 契约文档、接口与原生桥接的生产者/消费者契约、共享错误语义 -> `contract-guardian`。应用防回归与行为证据 [guard](regression.md)。
- Flyway 迁移 SQL -> `db-migration-reviewer`。
- `docker-compose.yml` 与 `.env.example` -> leader 协调加手动复核。
- Android 变更 -> 完成前 `android-no-kotlin-guard`。

## 时间字段契约

- API 中的真实时间点只允许使用契约明确单位的 epoch 秒/毫秒，或带 `Z`/明确 offset 的 ISO 8601；禁止新增无时区的本地日期时间字符串。
- date-only 字段必须以类型、字段说明或接口文档明确标识，消费者不得对其应用时区偏移。
- producer 与 consumer 必须共同验证秒/毫秒单位、offset 等价性及跨日展示；数据库存储时区不得泄漏为客户端展示时区。

## contract-guardian 执行与复核职责

- READONLY：拒绝代码编辑。在路由表中契约面（包括 API 路由索引）触发。
- 阅读 [回归证据](regression.md)，用于原生桥接生产者/消费者兼容、共享错误分类、相邻失败与证据结论。
- 比较前端 API 客户端、Java 控制器/客户端/DTO、兼容别名、测试与契约文档。核对路由、方法、请求/响应字段、状态语义、headers、错误封装、流式事件顺序、重试/终止语义与示例。
- 阅读 [文档一致性](documentation.md)，用于活动代码证据、同一变更的文档与规范性安全边界。
- 报告 PASS、FAIL 或 PARTIAL，并给出端点级差异、精确的代码/文档证据、过时别名/示例及修复指引。