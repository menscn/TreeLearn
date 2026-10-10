# Treelearn Harness 编排入口

> 这是用户选择 Harness 后的编排入口。执行模式和跨场景规则由 [根入口](../AGENTS.md) 路由；直接执行不需要读取本文件。

## Harness flow after opt-in
1. 需求澄清前读取 [协作协议](rules/harness-clarification.md)，只追问会实质改变目标、范围、风险或验收的未知项。以用户当前明确请求、已接受的 intent 或已确认规格作为需求依据；在 `.trae/tasks/` 记录父 requirement 与子任务，不要求用户再次批准等价摘要或普通阶段交接。实质事项未决时暂停拆分。
2. 创建、导入或恢复任务前读取 [Task 契约](rules/harness-tasks.md) 和 [模板](tasks/_TEMPLATE.md)。任务小步拆分、状态和 TDD 证据均由该正文定义。
3. 派工前读取 [负责人及专项 guard 路由](rules/contracts.md#route-table)，只加载实际使用的规则；按根入口路由叠加变更相关规则。
4. 实现保持在分配的 lane 内，默认由对应 owner 执行。可恢复的派工或执行失败由 AI 内部重试、缩小任务或改派；只有在具备相应能力且不越过已确认范围、lane、权限或专项 guard 时，主会话才可安全接手。每次执行与恢复都按任务契约记录，不因普通失败要求用户手工重派或拆任务。
5. 验收前读取 [验收规则](rules/harness-verification.md)，运行独立 verifier 及命中的只读 guard，报告结果、证据、缺口、风险和交接。
6. 没有安全恢复路径、缺少必要权限，或无法提供独立 verifier 时，将任务标为 `blocked` 并写明阻塞、解除条件与续接步骤；不得自我验收或报告 PASS。

## 三栈依赖方向（水流方向，用 lint 机械化）
- **前端**：页面(pages) → 布局(layouts) → 组件(components) → hooks → 状态(store) → 接口(api) → 平台适配(platform) → 工具(utils)
- **后端(Java)**：接口层(controller) → 业务层(service) → 数据层(repository) → 模型(entity)
- **Android**：依赖方向按模块层次（presentation → domain → data），Java-only，禁止 Kotlin。

## 5 条强制守门（verifier 必查 + Harness 拒绝合并）
1. **500 行硬预算**：`git diff --stat HEAD` 排除生成/迁移/文档后业务行 ≤ 500；超出 → 拆 task 重做。
2. **各栈 lint**：按改动栈跑（前端 pnpm lint / 后端 mvnw spotless+checkstyle / android gradlew lint + no-kotlin-check）；任一 FAIL 即 FAIL。
3. **依赖方向机械化**：正向依赖，反向 import → lint FAIL。
4. **task 验收点对照**：verifier 读 `.trae/tasks/<id>.md` 的 acceptance，逐条勾选；任一未达 → FAIL。
5. **密钥不入库**：提交前先跑 secret-scan（gitleaks）；命中 → 拒绝提交。

## 并行规则
- 同一 task 内不并行 implementer；不同 task 可同时派，但 verifier **串行**。
- 触共享代码区（接口契约 / 数据库 schema 或 migration / docker-compose / .env.example）的 task **必须独立**，不与其它 task 并行。

## 上下文管理（context anxiety 对抗）
- 上下文用量阈值只是 AI 的内部交接信号：及时把进度、决策、证据和下一步写入 task「交接备忘」，并在当前会话继续；平台支持时由 AI 自行压缩或接续上下文。
- 不因达到比例就要求用户重开会话。若平台限制确实导致无法安全继续，保存可续接状态并如实标记 `blocked` / `PARTIAL`，给出精确续接点；不得把上下文压力当作完成或 PASS 的依据。

## 提交规则
- 一律走 `commit-cn`：Conventional Commits 中文消息；内置先跑 secret-scan 再提交。

## 参考索引
- 入口与路由：根 `AGENTS.md`
- 需求协作：[rules/harness-clarification.md](rules/harness-clarification.md)
- 任务契约：[rules/harness-tasks.md](rules/harness-tasks.md) · [tasks/_TEMPLATE.md](tasks/_TEMPLATE.md)
- 验收：[rules/harness-verification.md](rules/harness-verification.md)
- 专项目 guard 路由：[rules/contracts.md](rules/contracts.md)
