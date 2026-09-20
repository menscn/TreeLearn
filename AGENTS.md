# Treelearn 项目规则入口

> 本工程复刻自 `knowledge_space` 的 Harness 工程开发流程，按 Treelearn 的「Android + 后端(Java/Spring Boot) + 前端(React+TS)」三端适配。
> 详细背景与使用说明见 [.trae/README.md](.trae/README.md)。

项目规则统一维护在 `.trae/`；详细规则只有一份权威正文，按下表触发读取。

## 读取协议
- 先按任务目标、预计修改路径和实际调用链匹配下表，在相关分析、设计、修改或复核前读取命中的文件。路径与行为触发取并集，不只看文件后缀。
- 范围扩大、发现新的 producer/consumer 或进入下一阶段时补读；本会话已读且未变化的规则不重复加载。
- 不要预读全部 rules、agents、skills 或参考资料；普通任务不要求读取完整 HARNESS。链接是读取指令的目标，不代表工具自动加载了正文。
- 跨栈工作叠加所需规则；未命中不代表可绕过适用约束。无法判断时先检查实际调用链；必需规则不可读时报告缺口，不自行豁免。
- 下表是索引摘要；具体适用范围、例外、证据和判定以目标正文为准。新增细则写入对应主题，入口仅补必要触发条件。

## 执行入口判断
- **直接执行**：改动很小，或者目标、边界和验收方式非常明确，明显不需要多个独立任务协调时，默认直接处理，不创建 Task，也不强制运行 verifier。
- **先询问用户**：面对大型需求、大型改动、高风险改造或包含多个独立交付项的工作，先说明判断理由，并询问用户是否采用 Harness Task 流程；用户确认前不得创建父需求或实现 Task。
- **进入 Harness**：用户选择采用 Harness，或明确使用下列 Harness 触发词时，按完整 Harness 流程执行。显式触发词视为已经同意采用 Harness，无需重复询问是否列 Task，但仍需澄清和确认具体需求。
- 是否需要询问主要依据独立交付项数量、协调复杂度、需求宽泛程度，以及架构、数据、权限、迁移等风险判断。跨栈或预计代码行数本身不自动触发 Harness。
- 用户选择不采用 Harness 时，不创建 Task，不应用 Harness 的任务拆分、lane 分配、行数预算或 verifier 强制要求；但仍必须遵守本文件的安全边界、必要测试、文档同步和适用的专项 guard。

## Harness 触发词
以下表达表示用户选择 Harness，仍须澄清和确认业务需求：
`按 harness-run 做这个跨栈需求`、`工头模式`、`按 harness 调度`、`harness-run`、`Harness mode`、`foreman mode`、`cross-stack request`。

## 通用底线
- 文件以 UTF-8 读写，写入中文前确认工具链编码；不得写入问号占位、替换字符或乱码。
- 完成结论必须匹配证据覆盖，已知错误和缺少必要验证不得报完成或 PASS。
- 当前运行事实以生产代码、活动配置、migration 和实际 producer/consumer 为首要依据；历史材料不作为现行契约，安全规范不得为迁就违规代码而降低。
- 变更必须按下表完成相应文档影响记录与验证；直接执行不免除适用规则，也不因此新增 Task 或例行审批。
- 只有用户选择 Harness、明确要求任务或其它已获准流程实际创建任务时，才创建 Task；任务及执行计划、交接、状态统一放在 `.trae/tasks/`，写入前读取任务规则。
- 不新增未经验证的依赖，不做未经验证的自动提交。

## 按需规则路由
| 触发条件 | 必读文件 | 读取时机 |
|---|---|---|
| 行为修改、共享逻辑、回归修复；生成、重生成/删除/保存、轮询、幂等、计量或日志噪声修复 | [防回归](.trae/rules/regression.md) | 影响分析、修改及行为验收前 |
| 生产代码、API、配置、SQL、架构、用户流程变更，或当前态文档修改 | [文档一致性](.trae/rules/documentation.md) | 事实核对、修改及完成前 |
| `frontend/**`；用户可见文案、错误提示、用户可见时间或设备时区 | [前端](.trae/rules/frontend.md) | 分析、修改及复核前 |
| `backend/**`（Java/Spring Boot） | [后端边界](.trae/rules/backend.md)、[契约](.trae/rules/contracts.md) | 分析、修改及复核前 |
| `backend/**/src/main/resources/db/migration/**` 或其它后端 SQL | [SQL 与建表](.trae/rules/sql.md)、[契约](.trae/rules/contracts.md) | 设计及执行前；真实 MySQL 验证后方可完成 |
| `android/**` | [Android](.trae/rules/android.md) | 分析、修改前；完成前执行 Java-only guard |
| 任意 SQL、Flyway、建表、维护或数据脚本；数据库时间或时区 | [SQL 与建表](.trae/rules/sql.md) | 设计及执行前；真实 MySQL 验证后方可完成 |
| 外部 AI/LLM/Embedding 服务调用边界、生成相关 | [后端边界](.trae/rules/backend.md) 之 AI 边界 | 调用链分析及修改前 |
| 状态存储、缓存、租约/锁/Worker owner；轮询、后台/异步任务、SSE、WebSocket、文件处理、补偿或定时任务 | [共享状态](.trae/rules/shared-state.md) | 设计、修改及跨实例验证前 |
| API/DTO/SSE/请求头、时间戳、前后端桥接、共享错误语义、契约文档或路由索引 | [契约与 guard](.trae/rules/contracts.md) | 影响分析、修改及复核前 |
| 受影响模块生产代码、构建入口、依赖、启动配置、端口、代理、健康检查、环境变量或安装方式 | [启动与部署](.trae/rules/startup.md) | 修改前；完成前提供各模块启动证据 |
| 新增/改造处理流程、前端/Android 用户功能、日志或遥测 | [日志与遥测](.trae/rules/observability.md) | 设计、修改及验收前 |
| 用户已选择 Harness | [Harness 入口](.trae/HARNESS.md) | 编排开始时，按阶段继续读取 |
| 已授权创建、导入、恢复、拆分或执行任务，含外部 skill/plugin 产物 | [任务契约](.trae/rules/harness-tasks.md) | 产生任务文件或执行任务前 |
| Harness 验收，或用户明确要求 verifier | [验收](.trae/rules/harness-verification.md) | 验收前，叠加本次变更命中的专项规则 |

## 工具与参考
- 项目技能：`.trae/skills/*/SKILL.md`。仅加载实际使用的技能。
- [排除说明](.trae/rules/EXCLUDED.md)：列出从 knowledge_space 迁移时明确排除的规则（HClaw、CSPfix 归档等）及理由。

## 失败案例库（不要再犯）
> verifier 发现新型错误或守门多次命中时 → 必须建议追加一条到本节。条目 > 30 → 整理合并去重。

- [初始] [contract] 改接口契约文档不派 `contract-guardian` 复查 → 前后端字段对不齐；规则：触契约必走 contract-guardian。
- [初始] [android] 误引入 Kotlin 文件或依赖 → APK 体积膨胀 + 团队约定违反；规则：android 提交前必走 Java-only guard。
- [初始] [backend] 直接调 LLM/Embedding 或写不归属本服务的共享索引 → 越权；规则：AI 调用必须走明确的服务边界。
- [初始] [budget] 一个 task 涌出 > 500 行 diff → verifier 拒绝；规则：拆解阶段就估算 ≤ 400 行，留 100 行缓冲。
- [初始] [shared] 改 `V1__init.sql` 不走 `db-migration-reviewer` → schema drift；规则：迁移脚本必走该守门。