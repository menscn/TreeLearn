# 唯一 API/SSE 契约（`web/contracts/`）

本目录是知芽（TreeLearn）Web 本地版的**唯一权威** API/SSE 契约：规范正文 + 共享 fixtures。
后端 Controller/DTO 与前端 TypeScript 类型都必须以本目录为准；功能一致性矩阵不是接口规范，不得据它复制出第二份路由。

## 文件索引

| 路径 | 内容 |
|---|---|
| `README.md` | 本文件：索引、消费约定、变更流程 |
| `api.md` | 唯一规范正文：基础约定、时间规则、错误封装与错误码词汇表、命令幂等、DTO 词汇表、端点逐条、SSE 协议、语义冻结清单、语音边界、静态托管、对照来源 |
| `fixtures/dtos/*.json` | 每个 DTO 一份样例（请求/响应对象的规范形状） |
| `fixtures/commands/*.json` | 变更请求样例（首问、追问、分叉、retry、cancel、seen、阅读位置、配置、删除确认等） |
| `fixtures/errors/*.json` | 每个错误码一份封装样例（`{"error":{...},"requestId":...}`） |
| `fixtures/sse/*.json` | 有序 SSE 帧序列样例（正常流式、失败、中断、取消、重连缺口补快照、晚到/重复被忽略） |
| `fixtures-ai/requests/*.json` | provider 请求体样例：各 `PromptPurpose` × 思考开关 × 模型的输入与期望请求形状（键集/键序/思考块/消息逐字运输） |
| `fixtures-ai/streams/*.json` | provider SSE chunk 序列样例（正常、仅推理、空/仅 role、畸形 JSON、错误帧、连接截断、心跳注释）与期望事件序列/终态/错误分类 |
| `fixtures-ai/models/*.json` | provider `/models` 响应样例（正常、缺 `data`、条目缺字符串 `id`、非 JSON）与期望 id 列表或拒绝 |

## fixtures 是什么

fixtures 是**唯一共享样例**：同一份 JSON 同时被 Java producer 与 TypeScript consumer 解析。
约定：

- 编码 UTF-8，2 空格缩进；时间点用固定常量（避免“当前时间”导致往返不稳定）。
- 双端测试各自解析 fixtures，并做**往返（round-trip）**：Java 反序列化成 DTO 再序列化回 JSON，TypeScript 反序列化成类型再序列化，两侧都必须与原始 fixtures 语义一致（字段名、枚举、单位、可空性）。
- fixtures 是契约的可执行佐证，不是文档副本；改契约必须同改 fixtures（见下）。
- **包装约定**：
  - `fixtures/dtos/*.json`：直接是 DTO 对象（或对象数组 / 列表包装，如 `attachment-list.json`），可被直接反序列化为目标类型。
  - `fixtures/commands/*.json`：`{"method","path","body"}` 包装；`body` 为请求体，`path` 用 `{占位符}` 表示路径变量，`method` 为 HTTP 方法。测试取 `body` 发往 `path`。
  - `fixtures/errors/*.json`：直接是错误封装对象 `{"error","requestId"}`。
  - `fixtures/sse/*.json`：`{"scenario","operationId","lastEventId","note","frames":[{"id","event","data"}]}` 包装；`lastEventId` 为该场景的重连起点（无则 null），`frames` 为有序帧序列（含场景字段说明）。部分场景另含**客户端期望字段**（非协议帧、仅供测试断言）：`expectedAppliedSeqs`（客户端应实际应用并计入去重的 `seq` 列表）与 `ignored`（应被忽略的帧：`{ "reason", "id" }`，reason ∈ `duplicate seq`/`foreign operationId`/`out-of-order seq`）。这两者只出现在 `late-duplicate-ignored.json`，表示客户端过滤/去重规则的期望结果，不是服务端发送内容。

### Java 如何消费

- Java DTO（`web/backend`）用项目的 JSON 库把 `fixtures/dtos/*.json` 反序列化为对象、再序列化比对（忽略键序）。
- 建议在 T-015 的 `web/backend` 测试中：遍历 `fixtures/dtos` 与 `fixtures/errors`，断言解析出的 `error.code`/`retryable`/`params` 键、DTO 字段名与 `api.md` 一致。
- `fixtures/sse/*.json` 以“帧数组”给出：每项 `{ "id", "event", "data" }`；测试按 `api.md` 第 7 节的顺序与去重/过滤规则重放，断言状态收敛。

### TypeScript 如何消费

- 前端类型与 API 适配层以 `api.md` 为准；测试从 `fixtures/**` 读取同样的 JSON，断言类型守卫/解析器接受样例并保持枚举与单位。
- 错误文案由前端按 `errors.<code>` 的 i18n 资源解析，`params` 作插值；fixtures 中的 `params` 键集合即该错误可用的插值键集合。
- SSE 客户端使用项目统一的认证流客户端（不使用原生 `EventSource`）；`fixtures/sse/*.json` 用于驱动其解析与“按 operationId 过滤、按 seq 去重、缺口补快照”的单元测试。

### provider 协议 fixtures（`fixtures-ai/`）

`fixtures-ai/` 是 DeepSeek/OpenAI 兼容 provider 协议的**跨端唯一样例**：Java 桌面端（`web/backend`，任务
`T-2026-10-10-021`）与 Android 端（T-022，计划）消费同一份样例，用于约束「只替换平台实现」后的语义等价。三组样例：

- `requests/`：输入为 `purpose` / `modelWireId` / `thinkingEnabled` / `history` / `currentInput`，期望为 `expectedTopLevelKeys`（键序）、
  `expectedThinking`（思考块）与消息逐字运输；桌面测试用共享 `DeepSeekRequestAssembler` 组装请求，再用平台编码器编码后断言。
- `streams/`：`lines` 为有序 SSE 文本行（含心跳注释行与结束空行），`expectedEvents` 为应派发的双通道增量序列，
  `expectedTerminal` ∈ `COMPLETED`/`INTERRUPTED`/`FAILED`，`expectedErrorKind` 为 `FAILED` 时的 `AiGatewayError.Kind`（否则 null）。
  测试经共享 `SseFrameParser` 回放后逐事件解码。
- `models/`：`payload` 为 provider 响应文本，`expectedIds` 或 `expectedErrorKind`（`PARSE_FAILED`）二选一。

对照源为 Android `com.treelearn.ai.gateway.JsonChatRequestBody`/`ProviderJson`；已知平台差异（`org.json` 宽松 vs Jackson 严格）
记录在 `T-2026-10-10-021` 任务对照记录中。

## 契约变更流程

任何契约变更必须在**同一任务**内完成，并交 `contract-guardian` 只读复核：

1. 改 `api.md`（端点/字段/枚举/错误码/SSE 语义）。
2. 同步 `fixtures/**`（新增/修改相应样例）。
3. 同步双端类型与客户端/Controller 编解码（Java DTO 与 TS 类型）。
4. 更新 `web/README.md` 的 contracts 状态行（如需）。
5. 运行 JSON 语法校验与双端往返测试；`contract-guardian` 复核 PASS 后方可完成。

禁止：

- 只改 `api.md` 不改 fixtures / 双端类型；
- 通过放宽 fixtures 或断言来“接受”漂移；
- 引入 OpenAPI 生成器或未验证依赖（本契约手写规范 + fixtures 往返约束漂移）。

## 复核与证据

- 规范正文与 fixtures 与活动 Android 源码/共享领域模型的对照记录写在 `api.md` 第 12 节。
- 复核（`contract-guardian`）核对：路由/方法/请求/响应字段、状态语义、错误封装、SSE 事件顺序与重连语义、时间单位、幂等身份，并给出 PASS/FAIL/PARTIAL 与端点级差异。