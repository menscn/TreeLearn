# 知芽（TreeLearn）SSE 流解析边界 clean-room 规格 V1.0

> 版本：V1.0 ｜ 日期：2026-09-23 ｜ 任务：T-2026-09-23-069（父需求 T-2026-09-23-067）
> 状态：draft，待用户 review；review 通过前不得进入编码任务。

## 0. clean-room 使用规则（最高优先级）

1. 本规格是把一个成熟开源实现（RikkaHub）的流式解析**机制**提炼而成的中文描述，正文中不含任何源代码或逐行伪代码。
2. **实现者只允许读本规格与知芽自己的代码，禁止阅读 RikkaHub 源码**（`D:\Project\Android\rikkahub-master/**`）。每条机制末尾的「出处」仅供评审者审计提炼过程，实现者无需阅读、也不得借出处顺藤读源码。
3. 本规格是「边界行为契约」，不是实现设计：类名、包结构、函数拆分由知芽实现任务自行决定，唯一要求是行为与本规格一致。

## 1. 范围

- 知芽只用 DeepSeek（OpenAI 兼容协议），本规格只覆盖 OpenAI 兼容的 SSE 流（`data:` 行 + `[DONE]` 哨兵 + 增量 delta），不覆盖 Anthropic/Google 原生流格式。
- 知芽传输层保持 `HttpsURLConnection` + 自建解析不变；本规格描述该解析器必须表现出的边界行为。
- 知芽现状基线：`android/ai/src/main/java/com/treelearn/ai/gateway/`（网关与流解析所在，T-006 交付），本文不改动它，只列对照。

## 2. 事件模型（机制层）

流式回复应被建模为**与传输层无关**的两级事件：

1. 传输级事件：一次服务端推送 = 一个「数据载荷」，由若干行 `data:` 组成，以空行结束。
2. 语义级事件（建议知芽采用的三段式增量模型）：
   - 文本增量：开始 → 若干增量 → 结束；
   - 推理（reasoning）增量：同上三段式；
   - 用量事件（token 统计）、结束事件（携带 finish 原因）。
   - 三段式的价值：让下游 UI 把「开始打字」「打字中」「本段完成」分开处理，且允许一个回复里文本段与推理段交错。

出处：RikkaHub `ai/src/main/java/me/rerere/ai/ui/StreamChunk.kt`（三段式事件模型）、`ai/src/main/java/me/rerere/ai/provider/stream/SseEvent.kt`（传输级事件）。

## 3. 解析器边界行为契约

每条格式：「输入情形 → 期望行为」。出处均为机制提炼位置，实现者不读。

| # | 输入情形 | 期望行为 | 出处 |
|---|---|---|---|
| B1 | `data` 载荷为 `[DONE]` | 视为流正常结束：关闭所有未闭合的增量段，发出结束事件，解析器进入完成态 | `ai/.../openai/ChatCompletionsStreamDecoder.kt` |
| B2 | 一个事件载荷含多行 `data:` | 拆行、跳过空行，每行作为独立 JSON 解析（OpenAI 兼容方存在一行一 JSON 的多 data 形态） | 同上 |
| B3 | 跨网络分包的不完整行 | 解析器必须保留未收满一行的尾部字节，与下一批拼接后再解析；不得把半行当事件处理 | OkHttp SSE 读取层机制（`ChatCompletionsAPI.kt` 经由标准 SSE reader） |
| B4 | 事件 JSON 含 `error` 字段 | 视为服务端业务失败：终止整个流并向调用方抛出携带服务端错误信息的异常；**不当作网络错误重试** | 同上 |
| B5 | 增量文本字段（`delta.content`） | 产出文本增量；字段缺省时为空增量，不崩溃 | 同上 |
| B6 | 推理增量字段多来源（`reasoning_content` / `reasoning`） | 按优先级兜底读取多个候选字段名，产出推理增量——DeepSeek 用 `reasoning_content`，其余字段为兼容兜底 | 同上 |
| B7 | `finish_reason` 字段 | **先暂存不立即发**：等流结束（`[DONE]` 或连接关闭）时统一发出，保证结束事件是流的最后一个事件 | 同上 |
| B8 | 连接关闭但从未收到 `[DONE]` | 兜底收尾：关闭所有未闭合段、发出已暂存的结束事件；「结束事件」全流程只允许发出一次（幂等），无论正常结束、兜底结束还是取消 | 同上 |
| B9 | 增量到达但对应的「开始」事件缺失 | 容忍：直接视为该段已隐式开始，不要求上游严格按序 | RikkaHub `ai/.../ui/StreamChunkHandler.kt` |
| B10 | usage 统计字段多方言 | 兼容多种缓存 token 的位置（嵌套对象 / 顶层 / DeepSeek 专有字段名），取第一个非空来源 | `ChatCompletionsStreamDecoder.kt` |

## 4. 异常、取消与超时契约

出处：`ai/.../provider/ChatCompletionsAPI.kt`、`app/.../di/DataSourceModule.kt`、`app/.../data/ai/GenerationLoop.kt`、`app/.../service/ChatService.kt`（机制提炼）。

| # | 情形 | 期望行为 |
|---|---|---|
| E1 | HTTP 非 2xx，或 Content-Type 不是事件流 | 不进入解析；读取错误响应体，从中提取服务端错误信息形成结构化失败；**不自动重试** |
| E2 | 解析中 JSON 非法 | 以异常结束整个流（RikkaHub 不做坏事件跳过；知芽可自行决定是否更宽容，见 §6） |
| E3 | 用户取消 | 立即关闭底层连接；取消产生的 I/O 异常必须与真实网络故障**区分开**——取消不得触发自动重试，也不得向用户报错 |
| E4 | 真实网络异常（IOException 类） | 允许自动重试：最多 3 次、1s/2s/4s 指数退避；**每次重试必须从消息快照重新开始**，不得把半截回复叠加进新尝试；仅重试网络类异常，HTTP 错误与流内 error（B4/E1）一律不重试；重试开关由用户设置控制 |
| E5 | 超时预算 | 连接、读、写三段独立预算；进入流式读取后，连接级总超时应让位于读超时（长回答远超常规请求时长） |
| E6 | 流式期间缓冲 | 下游消费队列不得因缓冲满而**静默丢字**——宁可阻塞生产也不丢增量（RikkaHub 因此修复过丢字缺陷） |

## 5. 落库与 UI 消费时机

出处：`app/.../service/ConversationSession.kt`、`ChatService.kt`（机制提炼）。

1. 流式期间只更新内存态并驱动 UI，不逐增量落库。
2. 落库只在流**终态**统一执行一次：成功、失败、取消三种终态走同一个收尾点；即使取消，已收到的部分内容也要保存（NonCancellable 语义的 Java 表述：收尾写入不允许被再次取消打断）。
3. 流结束后才触发的衍生工作（标题生成、摘要刷新）必须异步、且不得阻塞收尾落库。

## 6. RikkaHub 也未覆盖、知芽自建解析需自行决策的边界

出处：RikkaHub `ai/.../util/SSE.kt`（该自研实现为死代码）与 OkHttp 内部 reader 的对照结论。

| # | 边界 | RikkaHub 行为 | 知芽建议 |
|---|---|---|---|
| G1 | SSE `retry:` 字段与断线自动重连 | 完全忽略，无自动重连 | 单机应用可同样忽略，但应显式决策并记录 |
| G2 | `id:` 字段与 Last-Event-ID 续传 | 未消费 | 同上 |
| G3 | BOM、CR-only 行尾、注释行（`:` 开头） | 依赖底层库处理；自建解析必须自己覆盖 | 建议覆盖：BOM 剥离、`\r\n` 与 `\r` 统一按行、注释行跳过 |
| G4 | `[DONE]` 匹配不做 trim 容错 | 精确匹配，带空白即失效 | 建议比它更稳：匹配前 trim |
| G5 | 单个事件 JSON 非法即整流失败 | 无坏事件容错 | 建议保持「失败要响亮」：首个坏事件即失败并保留日志，不做静默跳过（与知芽「corrupt row 必须被点名」的既有原则一致） |

## 7. 与知芽现状对照清单（实现任务执行前必须逐条用真实代码核对）

> 撰写本规格时未逐行核对知芽 `:ai` 网关的现有实现，下表按「实现任务开工前核对」给出，不得作为结论引用。

- 已覆盖（高置信，来自 T-006/T-066 交付范围）：基本 `data:`/`[DONE]` 解析、文本与 reasoning 增量、HTTP 错误结构化。
- 待核对：B3（跨包拼接）、B7（finish_reason 暂存时机）、B8（结束幂等 + 未收 DONE 的兜底收尾）、E3（取消与网络错误区分）、E4（重试从快照重启 + 指数退避）、E5（三段超时预算）、E6（缓冲不丢字）、§5.2（取消也落库部分内容）、G3。
- 建议补齐：核对后未命中的条目；每条补齐走独立 RED/GREEN 用例。

## 8. 验收用例清单（实现任务直接翻译为 JUnit）

1. 分三次喂入 `data: {"choices":[{"delta":{"content":"你"}}]}` 的字节碎片（切在 JSON 中间），期望只产出一次完整文本增量。
2. 喂入含两行 `data:` 的事件，期望解析出两个独立事件。
3. 喂入 `[DONE]` 前带首尾空格的变体（按 G4 决策），期望按既定容错行为处理。
4. 喂入 `{"error":{...}}` 事件，期望流以业务失败结束，且不触发重试。
5. 喂入若干增量后直接关闭字节流（无 `[DONE]`），期望收到且仅收到一次结束事件，未闭合段落被关闭。
6. 模拟取消：关闭底层连接并断言产生的异常类型为「取消」而非网络异常，且不触发重试；随后断言已收内容被落库。
7. 模拟 IOException 并开启自动重试：断言 3 次退避、每次从空快照开始、最终内容不叠加。
8. 灌入 10 万个增量事件不断言丢字（E6 回归）。

## 9. 出处索引（评审者用，实现者免读）

- 事件模型与解析边界：`D:\Project\Android\rikkahub-master\ai\src\main\java\me\rerere\ai\`（`ui/StreamChunk.kt`、`provider/stream/SseEvent.kt`、`provider/providers/openai/ChatCompletionsStreamDecoder.kt`、`provider/ChatCompletionsAPI.kt`）。
- 重试/取消/落库：`app/src/main/java/me/rerere/rikkahub/`（`data/ai/GenerationLoop.kt`、`service/ConversationSession.kt`、`service/ChatService.kt`、`di/DataSourceModule.kt`）。
- 提炼过程：本任务 T-069 执行记录（2026-09-23，Explore 代理只读分析）。
