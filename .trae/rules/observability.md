# 日志与行为遥测

读取时机：新增或改造处理流程、用户功能或遥测改动前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## 可排障日志

- 新增或改造处理流程时必须补充可排障日志：至少记录 start / success / fallback / failure，包含关键 ID、计数、耗时、模式、fallback reason；不得记录密钥、完整原文、完整 prompt、长 quote 或 sourceRefs 细节。

## 服务端 RequestId 通用接入

- 服务端追踪是基础设施横切能力，不允许业务代码逐条手工拼接 `requestId`/`spanId`。后端每条日志记录必须由统一日志配置固定输出这两个字段；无业务上下文时输出 `-`。根链路继续使用 `X-Request-Id`/`requestId`，不得新增同值 `traceId` 别名，也不得复用业务 `request_id`、`client_request_id`、供应商请求号、幂等键或任务 ID。
- 使用标准入口的普通新增业务默认零适配：Java Servlet 请求、已有的线程池 executor/scheduler、统一后端 HTTP 客户端/Reactor 边界、Web 框架入口，以及公共持久异步框架的 publish/worker/retry/调度边界，必须自动继承统一 trace scope。业务 owner 仍须通过日志测试或覆盖门禁证明实际入口没有绕开这些基础设施。
- 新增以下边界时必须在同一任务内显式接入并测试，不能以「已有全局日志格式」为由省略：
  - 新建原生线程、自定义 executor/scheduler、独立进程或绕过公共 Reactor/HTTP/持久异步入口；
  - 新增持久异步载体、任务表、批次表、恢复记录或其它请求结束后才消费的载体；
  - 新增 Redis/SSE、消息队列或其它跨实例消息协议；
  - 新增独立日志初始化、handler/formatter 或 Worker 启动入口。
- 持久异步 producer 必须保存当前合法 `requestId`/`spanId`；claim/attempt 必须以已保存 span 为父级创建新 span，并与 claim 原子写回。重试沿用 requestId、创建新 span；旧行、缺失或非法 trace 必须安全创建新根并记录一次 `legacy_recovered=true`，追踪恢复失败不得阻塞业务。
- Redis/SSE/内部消息的 trace metadata 必须保持可选和向后兼容；非法字段整组丢弃并安全补号。不得改变 TTL、Redis Cluster 槽位、事件顺序、游标、重连、幂等、计费或租约语义；不得向对象存储、OAuth、预签名 URL 或其它非受控上游泄漏内部 trace Header。
- 新增业务完成前必须运行项目内 trace 覆盖门禁脚本（如 `scripts/check-tracing-coverage.ps1`）。新增 executor、scheduler、持久异步载体、durable consumer 或持久异步任务必须被门禁识别并接入统一 scope；确属上下文无关的入口只能通过路径级明确例外放行，并在任务/PR 中记录生产代码证据和理由。禁止为通过门禁添加宽泛目录例外。
- 最低回归证据包括：同一 requestId 能关联入口与业务日志；子任务 span 不复用父 span；并发链路不串号；异常/finally 后不残留上下文；无上下文启动日志为 `requestId=- spanId=-`。涉及持久或跨进程边界时，必须另有旧记录、非法字段、retry 新 span 和实际 consumer scope 的证据。

## Cross-cutting telemetry guard

用户行为遥测管道实现后，每个新的面向用户的前端或 Android 功能在完成前都必须决定并记录其 action-event 覆盖。至少，owner 应记录页面/功能入口、主要命令、成功/失败/fallback 结果，以及 input-start/input-change 动作，但不含原始文本、prompt、文件名、手机号、文档内容或其它敏感负载。若某功能有意不发射遥测，须在任务或 PR 说明中记录原因。

每个遥测事件的新增、删除、重命名、属性变更、同意/采样变更或中文分析文案变更，都必须以同一任务/PR 更新事件目录、生产者调用点、生成的前端定义、用户行为打点表文档及相应测试。从 `frontend/` 运行 `pnpm check:behavior-event-catalog`；目录/生成文档漂移或缺失带 UTF-8 中文元数据时，Harness 校验器必须拒绝。