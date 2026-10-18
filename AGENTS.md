# Treelearn 项目规则入口

项目规则统一维护在 `.codex/`；它是 Treelearn 唯一的 AI 研发流程入口。`.trae/` 已按用户授权删除，`.agents/`、`.workbuddy/` 和 `.superpowers/` 不承担本项目的权威规则职责。

## 读取协议

- 先按任务目标、预计修改路径和实际调用链匹配下表，在分析、设计、修改或复核前读取命中的正文。
- 范围扩大、发现新的 producer/consumer 或进入下一阶段时补读；已读且未变化的规则不重复加载。
- 不要预读全部 rules、agents、skills 或参考资料；链接是读取指令的目标，不代表工具自动加载了正文。
- 当前运行事实以生产代码、活动配置、迁移和实际 producer/consumer 为首要依据；历史任务、计划、原型和报告不能替代当前事实。

## 执行入口判断

- **直接执行**：改动很小，或目标、边界和验收方式非常明确，且不需要多个独立交付项协调时，直接处理，不创建 Task v2，也不强制运行 verifier。
- **先询问用户**：大型需求、大型改动、高风险改造或多个独立交付项需要协调时，先说明理由并询问是否采用 Harness；确认前不得创建可执行任务。
- **进入 Harness**：用户选择 Harness 或使用 `harness-run`、`工头模式`、`按 harness 调度`、`Harness mode`、`foreman mode`、`cross-stack request` 时，按完整 Harness 流程执行。
- 用户确认范围后，AI 在该范围内连续完成分析、实现、测试、修复和验证；只有会改变目标、架构、权限、数据、兼容性或验收方式的决策，或真实阻塞，才再次询问。
- 用户选择不采用 Harness 时，不创建 Task v2、不强制任务拆分和 verifier，但仍须遵守必要测试、文档同步和专项 guard。

## Treelearn 当前事实

- `android/` 是本地优先 Android 客户端，Java-only 是硬约束；Android SQLite、原生桥接、手机/Pad 展示和 APK 运行证据必须按专项规则验证。
- `frontend/` 是 React/TypeScript 前端；用户可见文案、时间展示、设计 token、API 客户端和错误语义遵循前端规则。
- `backend/` 是规划中的本机环回 Java/Spring Boot 服务；目录尚未落地时只保留规则和 owner，不把规划事实写成已运行服务。
- `Docs/04-ux-design/` 与设计 token 是当前设计事实源；Figma/原型工具只服务于 Treelearn 对应画布。
- 项目默认不引入远程账号、远程同步或远程遥测；若未来改变该边界，必须先更新权威规格和对应规则。

## 通用底线

- 文件以 UTF-8 读写，中文不得出现问号占位、替换字符或乱码。
- 不新增依赖、不自动提交、不自动推送；除非用户明确要求，保留现有业务代码和未提交改动。
- 完成结论必须匹配证据覆盖；已知错误、缺少必要验证或工具未实际运行时不得报 PASS。
- 规则新增或实质修订必须同步回归说明、文档影响和验证记录；同一约束只保留一份权威正文。
- `.codex/tasks/` 是唯一可写任务状态目录；不要把新任务写入 `.trae/`、`.cursor/` 或插件目录。

## 按需规则路由

| 触发条件 | 必读文件 | 读取时机 |
|---|---|---|
| 生产代码、影响行为的配置、共享逻辑、回归修复、规则新增或实质修订 | [防回归](.codex/rules/regression.md) | 影响分析、设计、修改及验收前 |
| 生产代码、API、配置、架构、用户流程或当前态文档 | [文档一致性](.codex/rules/documentation.md) | 事实核对、修改及完成前 |
| 大文件、高复杂度、模块职责、共享业务规则、状态边界或兼容分支 | [可维护性](.codex/rules/maintainability.md) | 影响分析、设计、修改及复核前 |
| 手机/Pad、窗口自适应、跨端数据兼容、原生资源或展示隔离 | [多端边界](.codex/rules/multi-device.md) | 影响分析、设计、修改及复核前 |
| `frontend/**` | [前端规则](.codex/rules/frontend.md) | 分析、修改及复核前 |
| `android/**` | [Android 规则](.codex/rules/android.md)、[契约 guard](.codex/rules/contracts.md) | 分析、修改前；完成前执行 Java-only guard |
| `backend/**` | [后端规则](.codex/rules/backend.md)、[契约 guard](.codex/rules/contracts.md) | 分析、修改及复核前 |
| 任意 SQL、SQLiteOpenHelper、Flyway、建表、维护或数据脚本 | [SQL 规则](.codex/rules/sql.md)、[契约 guard](.codex/rules/contracts.md) | 设计、执行及真实引擎验证前 |
| Redis、缓存、锁、租约、轮询、后台任务、SSE、WebSocket 或补偿 | [共享状态](.codex/rules/shared-state.md) | 分析、设计、修改及跨实例验证前 |
| 新增/改造处理流程、前端/Android 用户功能、日志或遥测 | [日志与遥测](.codex/rules/observability.md) | 设计、修改及验收前 |
| `Docs/04-ux-design/**`、Figma、设计 token 或 `colors.xml` | [UX 规则](.codex/rules/ux-design.md) | 改稿、取数及设计证据核对前 |
| 构建入口、依赖、启动配置、端口、代理、健康检查或安装方式 | [启动与部署](.codex/rules/startup.md) | 修改前；完成前提供启动证据 |
| 用户选择 Harness | [Harness 入口](.codex/HARNESS.md) | 编排开始时按阶段继续读取 |
| 创建、导入、恢复、拆分或执行任务 | [任务契约](.codex/rules/harness-tasks.md)、[任务模板](.codex/tasks/_TEMPLATE.md) | 写入任务前 |
| Harness 验收或用户明确要求 verifier | [验收规则](.codex/rules/harness-verification.md) | 验收前 |
| 未成形的想法或需要判断是否值得做 | [.codex/agents/intent-capturer.toml](.codex/agents/intent-capturer.toml)、[pm-discovery](.codex/skills/pm-discovery/SKILL.md) | Plan 阶段开始前 |
| 需要产品当前阶段、技术栈和权威文档索引 | [Plan 背景包](.codex/context/project-brief.md) | Plan 阶段开始前 |
| 需要知道遗留阶段和下一批工作 | [Treelearn 路线](.codex/ROADMAP.md) | 新会话选题或用户询问下一步时 |
| 已 accepted 的需求进入设计 | [.codex/agents/spec-author.toml](.codex/agents/spec-author.toml)、[UX 规则](.codex/rules/ux-design.md) | Design 阶段开始前 |

## 工具与参考

- 项目 agent：`.codex/agents/*.toml`；项目 skill：`.codex/skills/*/SKILL.md`。只加载实际使用的角色或技能。
- 任务校验：`python -X utf8 .codex/scripts/validate_tasks.py`。
- Treelearn 专属设计、Parity、Android 和规格守门脚本位于 `.codex/scripts/`；脚本只读检查，不替代真实运行或人工复核。
- `.codex/references/` 只保存迁移映射和历史案例，不作为当前产品事实。
- 源项目专属 HClaw、CSPfix、Core-Python 业务规则和 APK 中转流程不在 Treelearn 当前运行面内。
