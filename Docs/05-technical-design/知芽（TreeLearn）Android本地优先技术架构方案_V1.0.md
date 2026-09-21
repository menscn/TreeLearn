# 知芽（TreeLearn）Android 本地优先技术架构方案 V1.0

## 1. 文档信息

| 项目 | 内容 |
| :--- | :--- |
| 状态 | 已确认设计，供 Harness 开发任务执行 |
| 日期 | 2026-09-20 |
| 最后更新 | 2026-09-21：纳入 BASE-26 自适应学习导师提示词策略与 T-020 |
| 架构选择 | B：Android 本地优先模块化单体 |
| 产品基准 | V3.0 冻结需求基准 + V3.1 增量 BASE-21～BASE-26 |
| 终端 | 纯 Android 原生应用，手机竖屏优先，兼容平板横屏 |
| 分发 | 个人设备直接安装 APK |
| 语言约束 | Java-only；禁止 Kotlin 源码、Kotlin Gradle 插件和 `org.jetbrains.kotlin` 依赖 |

## 2. 目标与非目标

### 2.1 目标

- 在单机 Android 应用中可靠保存主题、分支、消息、回答版本、思考内容、创建快照和生成中间状态。
- 保证重试、重置、分叉、归档、删除和进程中断不会破坏稳定来源与上下文隔离。
- 支持 DeepSeek SSE 流式生成；切换分支后继续生成，进程被系统终止后保留最近已落盘的部分内容并允许原位重试。
- 通过版本化、按用途隔离的系统提示词，把主回答稳定约束为准确性优先的自适应学习导师，同时保持标题、摘要、推荐和压缩的结构化输出。
- 把树画布、搜索和 AI 接入封装在可替换边界内，未来扩展时不要求重写核心领域模型。
- 保持工程对个人 APK 的规模适度，不为未确认的云同步、多用户或跨端需求提前建设服务端。

### 2.2 非目标

- 不建设 Spring Boot、React、账号、云同步、共享、备份或远程遥测服务。
- 不保证 Android 进程被系统终止后自动恢复网络流；首版不使用前台服务或 WorkManager 续传。
- 不引入非 DeepSeek 模型供应商、工具调用、Agent、RAG、知识库或笔记系统。
- 不持久化画布像素坐标，不把 HTML 原型的数据结构当作生产数据模型。

## 3. 架构总览

```text
┌──────────────────────────────────────────────┐
│ :app                                         │
│ Activity / XML Views / Navigation            │
│ AppContainer / GenerationCoordinator         │
└──────────────┬───────────┬───────────┬───────┘
               │           │           │
        ┌──────▼─────┐ ┌───▼────┐ ┌────▼──────────┐
        │ :data      │ │ :ai    │ │ :tree-layout │
        │ SQLite/DAO │ │ HTTPS  │ │ pure Java    │
        │ Key store  │ │ SSE    │ │ geometry     │
        └──────┬─────┘ └───┬────┘ └────┬──────────┘
               │           │           │
               └───────────▼───────────┘
                         :core
              domain / use cases / contracts
```

依赖只能向下：`:app` 依赖其他模块，`:data`、`:ai`、`:tree-layout` 只依赖 `:core`，`:core` 不依赖 Android UI、网络或数据库实现。模块间只传领域对象和接口，不暴露 Cursor、HTTP Response 或 View。

### 3.1 模块职责

| 模块 | 职责 | 禁止事项 |
| :--- | :--- | :--- |
| `:app` | 页面、导航、屏幕状态、应用级生成协调器、依赖装配 | 不直接写 SQL，不直接拼 DeepSeek JSON |
| `:core` | 领域实体、状态机、用例、Repository/AiGateway 接口、错误分类、供应商无关的 `PromptPurpose/PromptSpec` 契约 | 不依赖 Android Context、SQLite、HTTP，不保存 DeepSeek DTO 或具体提示词正文 |
| `:data` | SQLite、DAO、事务、迁移、搜索、阅读状态、Keystore 密钥存储 | 不决定业务流程，不调用 DeepSeek |
| `:ai` | DeepSeek 请求、SSE 解析、错误映射、上下文序列化、版本化提示词注册表、请求组装与结构化结果解析 | 不直接操作 View 或数据库，不接受用户自定义 system 模板 |
| `:tree-layout` | 树几何、标签避碰、命中测试、视野拟合 | 不读取数据库，不持久化像素坐标 |

## 4. 技术选型

| 领域 | 选择 | 原因 |
| :--- | :--- | :--- |
| 构建 | Gradle Groovy DSL，当前稳定 AGP；Java 17 toolchain | 避免 Kotlin 插件，满足现代 Android 构建要求 |
| SDK | `minSdk 28`；实现时采用当前稳定 compile/target SDK | 覆盖目标设备并减少兼容分支 |
| UI | 原生 XML/View，自定义 View/Canvas | Java-only；树画布需要精确矩阵和命中测试 |
| 状态组织 | 显式 ScreenController/Presenter + 不可变 ViewState；应用级 AppContainer | 不依赖 Kotlin 协程或 KTX；依赖可替换、可测试 |
| 本地数据库 | `SQLiteOpenHelper` + 显式 DAO + 前向版本迁移 | 避免 Room/SQLDelight 带来的 Kotlin 依赖风险；便于审计事务 |
| 网络 | `HttpsURLConnection`、`ExecutorService`、`android.util.JsonReader/JsonWriter`、自建 SSE 解析器 | 依赖最小且纯 Java；满足流式读取、取消和错误映射 |
| 密钥 | Android Keystore 生成 AES-GCM 密钥；SharedPreferences 只保存密文、IV 和版本 | API Key 不进入 SQLite、日志或备份 |
| 测试 | JVM JUnit 领域测试 + Android instrumentation 数据/UI 测试 | 布局算法和状态机可快速纯 JVM 验证，平台行为在设备验证 |

任何新增依赖必须先检查完整依赖树；出现 `org.jetbrains.kotlin:*` 或 `-ktx` 即拒绝。现代 OkHttp、Retrofit 组合不得在未证明依赖合规前引入。

## 5. 核心领域模型

```text
Topic 1 ── * Branch 1 ── * Message
                    │          │
                    │          ├── * GenerationOperation
                    │          └── * AnswerRevision (稳定、不可变)
                    │
                    ├── 0..1 ContextSnapshot (子分支出生快照)
                    └── * AiArtifact (摘要、要点、推荐等)
```

### 5.1 消息、尝试和稳定版本

- `Message` 表示分支内固定的逻辑位置。用户消息正文创建后不可变；AI 消息只保存当前展示版本和当前生成操作的引用。
- `GenerationOperation` 表示一次提交、重试或重置。它在远端提交前固定 `promptPolicyVersion`，流式期间持有可追加的 `partialReasoning` 与 `partialAnswer`，状态终止后不可再改写。
- `AnswerRevision` 只在一次操作成功完成后创建，保存完整 `reasoningContent`、`answerContent`、模型、思考模式、`promptPolicyVersion`、生成时间和版本号。稳定版本创建后不可变。
- 重试沿用原 AI 消息逻辑位置并创建新操作；重置成功后创建新稳定版本并原子切换 `Message.activeRevisionId`。
- 既有子分支始终保存 `sourceRevisionId`，因此不会随 `activeRevisionId` 切换而迁移。

### 5.2 分支来源与锚点

非根 `Branch` 保存：

- `parentBranchId`
- `sourceMessageId`
- `sourceRevisionId`
- `anchorStart`、`anchorEnd`
- `anchorQuote`、`anchorHash`
- `contextSnapshotId`

优先用 revision + 字符偏移定位；正文渲染差异或兼容迁移导致偏移失效时，用 quote + hash 回退。整条回答分叉时偏移为空，但版本引用仍必填。

### 5.3 创建快照

`ContextSnapshot` 使用版本化、规范化 JSON 保存有序上下文清单，每项只引用确定的用户消息、稳定回答版本或已完成摘要版本，并保存整体 SHA-256 摘要。读取必须通过结构化 JSON 解析器，禁止字符串拼接解析。

快照创建后不可变。父分支后续追问、标题变化、摘要刷新或来源回答重置都不修改既有快照。正常回答只允许读取本分支对话和出生快照。

## 6. 本地存储设计

### 6.1 七张逻辑表

| 表 | 权威事实 | 关键约束与索引 |
| :--- | :--- | :--- |
| `topics` | 主题身份、最后访问分支、创建/更新时间 | UUID 主键；不保存独立主题名 |
| `branches` | 父子关系、标题、归档/折叠、来源版本、未读序号、语义阅读位置 | `topic_id,parent_id`、`source_revision_id`、`archived_at` 索引；每主题仅一个根 |
| `messages` | 分支内有序用户消息和 AI 逻辑位置 | `branch_id,ordinal` 唯一；AI 当前版本/操作引用 |
| `answer_revisions` | 成功稳定回答的不可变推理、最终正文与生成策略版本 | `message_id,revision_no` 唯一；稳定序号单调递增；`prompt_policy_version` 非空 |
| `generation_operations` | 提交/流式/失败/中断状态、部分推理/回答与本次生成策略版本 | UUID 操作 ID；每分支最多一个活动操作；每行 `prompt_policy_version` 非空 |
| `context_snapshots` | 子分支不可变出生上下文清单 | `branch_id` 唯一；schema 版本与 digest 必填 |
| `ai_artifacts` | 标题候选、分支摘要、要点、回答推荐、首页推荐及其提示词用途/版本 | `kind,scope_id` 索引；`based_on_revision_id`/`input_digest` 防旧结果覆盖 |

API Key 不进入任何表。模型与思考模式偏好可保存在普通 SharedPreferences；密钥只保存 Keystore 加密后的密文。

### 6.2 永久表入场记录

本项目当前无可扩展的既有业务表，七张表分别承载不可合并的权威事实：树身份、树节点、对话顺序、不可变稳定版本、可恢复生成状态、不可变上下文快照和可失效 AI 派生产物。把这些事实塞入单表 JSON 会失去外键、事务约束、局部更新和可验证迁移，因此不采用。

| 表 | 生产者 / 消费者 | 规模与生命周期 | 敏感性 / 删除 | 废弃触发条件 |
| :--- | :--- | :--- | :--- | :--- |
| `topics` | TopicRepository / 首页、导航 | 每主题 1 行；删除主题级联 | 本地标识；整题删除 | 主题聚合被替代 |
| `branches` | BranchRepository / 聊天、导航、画布 | 每分支 1 行；随主题增长 | 标题、锚点可能含用户内容；子树/主题删除 | 树模型被替代 |
| `messages` | ConversationRepository / 聊天、搜索、上下文 | 每轮 2 行左右；随对话增长 | 用户原文；随分支删除 | 消息序列模型被替代 |
| `answer_revisions` | GenerationRepository / 聊天、来源、上下文、搜索 | 每次成功重置新增；被引用版本长期保留 | 推理、回答正文、提示词版本；随消息删除 | 版本事实迁移到等价不可变存储 |
| `generation_operations` | GenerationCoordinator / 恢复、重试、诊断 | 活动及当前失败记录；成功后可清理无引用旧失败尝试 | 部分推理/回答、错误分类、提示词版本；随消息删除 | 生成状态由等价持久载体接管 |
| `context_snapshots` | CreateBranchUseCase / BuildContextPlanUseCase | 每非根分支 1 行；不可变 | 上游内容引用；随分支删除 | 快照迁移到同等可验证格式 |
| `ai_artifacts` | 派生 AI 用例 / 首页、导航、推荐 | 每 scope/kind 保留当前有效结果和必要版本 | 标题、摘要、推荐；随 scope 删除 | 派生物不再持久化或拆入专表 |

所有表启用外键和事务；时间点使用 UTC epoch millis；ID 使用应用生成 UUID，不使用自增 ID。DDL 只允许位于生产 `SQLiteOpenHelper.onCreate/onUpgrade` 或集中、版本化的 migration runner，禁止 Repository、页面或普通业务路径临时建表。迁移只允许前向新增版本，不修改已经发布的迁移步骤。每次升级必须在 API 36 模拟器或真机的 Android framework SQLite 上验证新装、逐版本升级、关闭重开、`foreign_key_check`、`integrity_check`、事务回滚、约束和级联删除。（2026-09-21 用户决定：验收环境统一为 API 36 / 36.1，不再建设 API 28 环境；`minSdk 28` 仍为声明的最低兼容版本，由 `compileSdk 36 + minSdk 28` 的 lint `NewApi` 覆盖。）

### 6.3 搜索

首版通过 `SearchRepository` 查询归一化后的标题、当前展示回答、当前失败部分内容、思考内容和来源锚点。Android 自带 SQLite 的默认全文分词不能可靠覆盖中文子串，因此先采用参数化 `LIKE` 和明确排序；个人本机数据规模下可接受。

搜索不返回未被任何分支引用的过期稳定版本，避免同一逻辑回答重复命中；被旧子分支引用的内容通过该分支的来源锚点可定位。后续若数据量证明需要优化，可在 Repository 内替换为 n-gram 索引，不改变调用方。

## 7. 生成状态机与并发

```text
STAGED -> VALIDATING -> SUBMITTING -> STREAMING_REASONING
                                      -> STREAMING_ANSWER
                                      -> STABLE
任一未完成状态 -> FAILED | INTERRUPTED | CANCELED
FAILED / INTERRUPTED -> 用户手动 RETRY -> 新 GenerationOperation
```

- 同一分支最多一个活动生成，生成期间该分支禁止再次提交问题，避免回答顺序和上下文基线不确定。
- 不同分支可并行，应用级并发上限为 2；更多请求停留在 `STAGED`，按创建顺序启动。这是本机资源和个人 API 配额之间的保守平衡。
- `GenerationCoordinator` 属于 Application 进程，不属于 Activity/Fragment。切换分支、旋转屏幕或进入后台不会主动取消。
- 首版不启用前台服务。系统终止进程后网络连接自然结束；下次启动把遗留的 `VALIDATING/SUBMITTING/STREAMING_*` 原子改为 `INTERRUPTED`，保留已落盘内容，不自动重发。
- SSE 内容按 250ms 或累计 256 字符任一先到的条件批量落盘，避免每个 token 写数据库。进程突然终止时最多损失一个未刷新的小批次。
- 归档不取消生成，结果继续写入已归档分支；永久删除主题/子树先取消范围内操作，再在事务中级联删除。晚到回调必须以 operation ID 和终态校验拒绝写回。

## 8. 首次主题创建原子性

1. 根问题先保留在 UI 草稿，不创建数据库主题。
2. 检查 Key 是否存在，并调用官方余额接口验证 Key 与可用余额。
3. 发送实际模型请求；余额检查不能替代模型权限检查。
4. 收到生成请求 HTTP 2xx 响应头时，视为远端已接受。在一个 SQLite 事务内创建 `Topic`、根 `Branch`、用户 `Message`、AI `Message` 和 `GenerationOperation`，operation 必须写入请求发送前已固定的 `promptPolicyVersion`。网关的 accepted 回调必须同步等待该事务提交，提交成功后才继续读取 SSE body；提交失败立即关闭连接并返回本地持久化错误，避免首个 delta 早于 operation 落库。
5. HTTP 2xx 之前的 401/402/422/429/503 或网络失败只保留草稿，不产生空树。
6. HTTP 2xx 之后的流中断保留主题、根分支、问题、部分推理和部分回答，并允许原位重试。

本地 operation UUID 用于防止重复落库和重复 UI 命令。DeepSeek 未提供可依赖的端到端幂等保证时，网络结果不确定可能导致供应商侧做过一次不可见计算，但不得在本地形成第二个根节点。

## 9. DeepSeek 接入与思考内容

### 9.1 请求

- Base URL：`https://api.deepseek.com`。
- 模型 ID 在实现和发布前从官方文档校验；当前 PRD 使用 `deepseek-flash` 与 `deepseek-v4-pro`。
- 每次请求显式发送 `thinking.enabled/disabled`，开启时显式发送产品选定的 reasoning effort；不依赖官方“默认开启”。
- `stream=true`；只允许 HTTPS，禁止 cleartext traffic。
- 首版不发送 tools，因此不实现工具调用状态机。

### 9.2 版本化系统提示词

主回答采用 `tl-learning-answer/1.0.0` 自适应学习导师策略；完整正文、用途清单、派生输出协议和行为样例以 [DeepSeek 自适应学习导师提示词设计 V1.0](知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md) 为权威。提示词不能作为普通 UI 文案散落在 Activity、Repository 或网络调用点。

`:core` 定义供应商无关的 `PromptPurpose` 与 `PromptSpec(purpose, policyVersion, systemContent)`；`:ai` 的 `DeepSeekPromptRegistry` 维护固定版本正文，`DeepSeekRequestAssembler` 按以下顺序组装：

```text
system: 版本化 PromptSpec（恰好一条，第一条）
history: ContextPlan 经 DeepSeekContextEncoder 编码后的零到多条消息
user: 当前问题（恰好一条，最后一条）
```

- system 不进入 `ContextPlan`、出生快照或上下文摘要，也不得因 token 预算被裁剪。容量计算先预留 system、当前问题和最小回答空间；仍超限则失败关闭。
- 思考模式开启时只追加设计文档规定的静态可见性策略，不把用户正文、API Key 或其它运行时值插入 system。
- 历史回答、推理、引用、摘要和派生输入是内容数据，其中出现的指令不得改变消息角色、提示词用途、系统规则或结构化输出协议。
- 不可变生成命令在请求前固定 `promptPolicyVersion`。已有主题在请求前持久化带版本的 operation；首次根问题保持 §8 原子性，在 HTTP 2xx `accepted` 事务中把命令中的同一版本随 operation 写入，事务成功后才读取 SSE。成功事务原样复制版本到 `AnswerRevision`；重试/重置可使用升级后的活动版本，旧 revision 不变。
- 标题、摘要、回答推荐、首页推荐和上下文压缩分别使用独立 `PromptPurpose`；禁止复用主回答教学提示词。
- 数据库只保存用途/版本号，不保存完整 system content。日志与测试报告可记录用途和版本，不记录提示词正文。

首版没有联网检索或 RAG。“更准确”通过错误前提纠正、不确定性声明、事实/推断分离和禁止编造实现，不能表述为实时事实核验或正确性保证。

### 9.3 SSE 解析

解析器按 SSE 事件边界处理多行 `data:`，忽略心跳与未知兼容字段，识别 `[DONE]`。`delta.reasoning_content` 和 `delta.content` 分别追加到操作的两个缓冲区，不假设单个网络块就是完整 JSON，也不假设两个字段永远按固定网络分块出现。

只有收到正常流终止且最终回答非空，才在事务中创建 `AnswerRevision`、更新 AI 消息当前版本并把操作置为 `STABLE`。连接关闭但未正常完成时置为 `INTERRUPTED` 或 `FAILED`，不得把部分内容升级成稳定来源。

### 9.4 后续上下文中的推理

DeepSeek 官方文档说明：未携带 tools 的请求会忽略历史消息专用字段中的 `reasoning_content`。产品又明确要求推理进入后续上下文，因此这里采用两段式边界：`:core` 的 `BuildContextPlanUseCase` 只选择固定 snapshot、稳定 revision 和摘要，输出供应商无关的有序 `ContextPlan`；`:ai` 的 `DeepSeekContextEncoder` 再把其中的稳定版本规范化为普通 assistant 内容：

```text
<treelearn_reasoning>
已持久化的思考过程
</treelearn_reasoning>
<treelearn_answer>
最终回答
</treelearn_answer>
```

标签是 DeepSeek 适配层内部协议，不在领域实体或 UI 显示。近期版本使用原文；较早版本由上下文压缩用例生成同时覆盖推理和最终回答的摘要。压缩产物保存输入 digest，压缩失败则本轮生成失败并提示重试，不得只删除推理以凑上下文窗口。这样未来替换供应商时只需新增 encoder，不让 `:core` 依赖供应商字段或消息 DTO。

### 9.5 错误映射

| 类别 | 典型来源 | 用户行为 |
| :--- | :--- | :--- |
| `KEY_MISSING` | 本机无 Key | 保留草稿，引导设置 |
| `AUTH_INVALID` | HTTP 401 | 保留草稿或部分内容，引导替换 Key |
| `BALANCE_UNAVAILABLE` | 余额不可用/HTTP 402 | 阻止新提交，引导充值或换 Key |
| `REQUEST_INVALID` | HTTP 400/422 | 保留现场，显示可诊断错误，不自动降级模型 |
| `RATE_LIMITED` | HTTP 429 | 保留现场，手动重试 |
| `PROVIDER_UNAVAILABLE` | HTTP 503 | 保留现场，手动重试 |
| `NETWORK_INTERRUPTED` | 断网、超时、进程恢复 | 保留部分推理/回答，标记中断 |
| `PARSE_FAILED` | 非法 SSE/JSON | 保留已解析部分，记录脱敏诊断，手动重试 |

## 10. AI 派生产物与过期保护

标题、分支摘要、AI 要点、回答推荐和首页推荐都不是主对话权威事实。每次请求记录 `scopeId`、`kind`、`PromptPurpose`、`promptPolicyVersion`、`basedOnRevisionId` 或规范化 `inputDigest`。结果写回时在事务中再次比较当前基线：相同才更新；不同则丢弃旧结果。

- 根标题只依据根问题；非根标题首次依据来源锚点和首问。
- 分支摘要和 AI 要点可由同一次结构化响应产生，但分别作为 payload 字段校验。
- 回答推荐绑定具体稳定 revision，且只有它仍是分支最新稳定回答时才显示。
- 首页推荐绑定主题树轮廓 digest，只提交标题、来源锚点和摘要，不提交完整对话。
- 结构化 JSON 解析失败保留旧值，不允许用字符串截取猜测字段。
- 派生输入使用 `JsonWriter` 等结构化 API 编码为 user 消息，并被 system 明确定义为不可信数据；每种用途严格校验自己的 JSON envelope、必需字段和未知字段。

## 11. 未读、阅读位置与轮数

- `latestStableSequence` 在分支获得新的稳定回答时递增；`lastSeenStableSequence` 只在最新未读回答实际进入视口后更新。未读由两者比较得出，不存独立布尔权威值。
- 折叠祖先的未读数量按可见树实时聚合；推荐、标题和摘要更新不增加未读。
- 阅读位置保存 `anchorMessageId`、可选 `anchorRevisionId`、列表项内偏移和视口相对偏移，不保存绝对页面 scrollY。
- 枝长使用已提交用户消息数量推导对话轮数。重试和重置不增加用户消息，因此不会错误增加枝长。

## 12. 树画布

`:tree-layout` 输入只有不可变树快照、折叠状态、轮数、当前分支和视口约束，输出枝段、叶片、标签、引线、整体 bounds 与命中区域。数据库不保存任何像素几何。

布局必须满足：

- 根在底部向上生长；一个分支一段枝；枝长由轮数推导并有上限。
- 无可见子分支为叶，有可见子分支为枝干；归档子树不进入默认布局。
- 折叠节点输出芽、后代数量和未读汇总。
- 远/中/近三级信息密度；标签不互相重叠、不遮挡叶片，空间不足时允许不放标签但节点仍可点击。
- 折叠、展开、摘要更新和新增分支后，以被操作节点作为稳定锚点修正矩阵。
- “适应屏幕”根据输出 bounds 计算缩放和平移，并保持底部对齐。

绘制层使用单个自定义 View、`Canvas` 和 `Matrix`；手势层负责平移、双指缩放、点击命中和弹层锚点。纯 JVM 测试覆盖几何，设备测试覆盖触控和弹层不越界。

## 13. UI 与设备适配

- 单 Activity + 明确屏幕控制器；导航参数只传 UUID，不传大对象。
- 手机竖屏按原型单栏展示。平板横屏采用列表/导航与内容双栏；树画布占可用主区域，弹层以实际窗口 bounds 翻转。
- 所有可点击目标至少 44dp；系统字体放大后文字换行，不遮挡操作行。
- 思考过程每条回答默认折叠，展开状态只属于当前页面会话，不跨重启持久化；内容本身永久随回答版本保存。展开文字使用次级灰色，但对比度不得低于可读标准。
- 原型远程字体不能用于 APK 运行时加载。实现前确认字体授权并本地打包；若完整 CJK 字体导致 APK 或内存不可接受，回退系统可用中文字体并保持字号、行高与层级。

## 14. 安全、隐私与备份

- API Key：Keystore AES-GCM；不进 SQLite、不进 Bundle、不进日志、不进剪贴板。
- Manifest 禁止明文流量；Network Security Config 只允许 HTTPS。
- 禁用 Android 云备份和设备迁移备份，避免 Key、推理和学习内容离开本机边界。
- 日志仅记录 operation/topic/branch 的随机 ID、状态、计数、耗时、模型档位、`PromptPurpose`、`promptPolicyVersion` 和错误分类；禁止记录问题、回答、推理、system content、派生输入、quote、快照 JSON 或响应原文。
- 发布版不发送远程行为遥测。保留 `TelemetrySink` 接口，release 使用 no-op；debug 可记录不含内容的本地事件供测试。
- 隐私说明必须明确：问题、快照、历史回答和历史思考内容会按上下文策略提交给 DeepSeek；复制后内容进入系统剪贴板控制范围。

## 15. 可观测性

每个生成操作记录脱敏的 `start / accepted / streaming / stable / failed / interrupted / canceled`，包含 operation ID、branch ID、模型、思考开关、提示词版本、reasoning/answer 字符计数、耗时和错误分类。派生 AI 任务另记录用途、提示词版本、`start / applied / stale_discarded / failed` 及 input digest 前 8 位，不记录原始输入。

日志不是业务权威状态；恢复只读取 SQLite。release 默认降低日志级别并禁止正文输出。

## 16. 测试与验收策略

### 16.1 纯 JVM

- 回答版本：重置不覆盖旧 revision，旧子分支仍引用旧推理和回答。
- 状态机：合法/非法迁移、重复回调、进程恢复、取消与晚到事件。
- 上下文：分支隔离、快照不可变、推理标签编入、压缩失败关闭生成。
- 提示词：注册表用途/版本唯一；system 恰好一条且第一、当前 user 最后；system 不参与压缩/裁剪；思考附加段无动态插值；operation 到 revision 版本原样复制。
- 派生结果：旧 digest 结果不能覆盖新状态。
- 未读与轮数：重试/重置不增轮数，进入视口才清未读。
- 树布局：叶数、枝长、bounds、标签无重叠、折叠锚点稳定、手机和平板尺寸。

### 16.2 Android instrumentation

- SQLite 新装、升级、事务回滚、级联删除和进程恢复标记。
- Keystore 保存、替换、删除以及备份关闭配置。
- SSE fixture 覆盖分块 JSON、reasoning/content 交错、错误码、缺失 `[DONE]` 和取消。
- 请求 fixture 覆盖 system 顺序、重复/缺失 system 拒绝、恶意历史标签和派生 JSON schema；SQLite 验证提示词版本列的非空、重开与事务回滚。
- 首页、对话、思考展开、分叉、重置、搜索、归档、画布的关键用户流程。
- 手机竖屏和平板横屏；字体放大；44dp 热区；弹层翻转与不越界。

### 16.3 真实集成与发布

- 使用人工提供但不入库的测试 Key 验证余额、两个模型、思考开关、SSE、错误映射、版本化 system 被接受、派生 JSON 可解析，以及后续上下文确实包含历史推理。行为样例覆盖入门/专业问题、错误前提、关键歧义、时效信息和推理折叠；证据只含版本与结构性断言，不含提示词或对话正文。
- 运行 `lint`、单元/设备测试、依赖方向检查、secret scan 和 Java-only guard。
- 在模拟器或设备实际安装并启动 release APK，确认初始 Activity、数据库创建、离线重开和 APK 可侧载。
- 个人侧载若未提供正式发布证书，生成 Treelearn 专用的本机侧载 keystore 并保存在仓库外；Gradle 只通过环境变量或未跟踪的本地配置读取。不得提交 keystore/口令，不得在日志或报告中打印口令；交付时记录 APK SHA-256、签名证书 SHA-256 与 keystore 保存位置，确保后续升级仍可使用同一签名。该证书仅用于个人侧载，不宣称为应用商店正式签名。

## 17. 演进策略

- 所有 ID 使用 UUID，所有 Repository 和 AiGateway 由接口隔离，未来可增加导出/导入或同步实现，但首版不创建同步字段、租约或服务端表。
- SQLite schema 必须版本化；上下文 snapshot 和 artifact payload 自带 schema version，旧版本通过显式迁移读取。
- 搜索、布局和 DeepSeek 客户端可独立替换；领域层不依赖供应商 DTO 或像素结构。
- 系统提示词通过 `PromptPurpose` 和稳定版本演进；发布后行为或输出协议变化必须升级版本并保留明确迁移/兼容判断，禁止静默改写同一版本。
- 若未来引入 tools，必须重新设计 `reasoning_content` 回传、工具调用循环和安全边界，不能直接复用首版普通文本封装假装等价。

## 18. P0/P1 调整追踪

| 调整项 | 本方案落点 |
| :--- | :--- |
| P0 不可变回答版本 | §5.1、§6、§7 |
| P0 创建快照 | §5.2～5.3、§9.4 |
| P0 持久生成状态机 | §7、§9.3 |
| P0 首次创建远端接受边界 | §8 |
| P0 AI 派生产物防旧写回 | §10 |
| P0 自适应学习导师提示词 | §5.1、§6、§9.2、§10、§16 |
| P1 序号式未读 | §11 |
| P1 语义阅读位置 | §11 |
| P1 独立树布局引擎 | §12 |
| P1 搜索抽象 | §6.3 |
| P1 本地安全与打包 | §13～16 |

## 19. 实施顺序

1. 工程骨架、Java-only 与模块依赖守门。
2. 领域模型、状态机和 SQLite 迁移。
3. DeepSeek/SSE、上下文组装、版本化提示词策略和 GenerationCoordinator。
4. 主题/分支/重试/重置的核心用例。
5. 首页、对话、导航、搜索和归档。
6. 纯 Java 树布局与 Canvas UI。
7. 平板适配、安全、集成测试与 release APK 验收。

对应可执行拆分以 `.trae/tasks/T-*.md` 为准，其中提示词策略由 T-020 独立实现；每个实现任务均须先 RED 后 GREEN，并在完成前通过独立 verifier 与 Android Java-only guard。
