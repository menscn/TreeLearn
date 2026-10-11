# 知芽 Web 本地版 —— 唯一 API/SSE 契约（规范正文）

状态：Task `T-2026-10-10-014` 产出，等待 `contract-guardian` 只读复核。
范围：本机 Java 服务（`web/backend`，Spring Boot，仅 loopback）与 React/TypeScript 客户端（`web/frontend`）之间的 HTTP + SSE 契约。
权威性：本文件是**唯一权威**的 API/SSE 规范。功能一致性矩阵（`Docs/05-technical-design/TreeLearn_Web本地版_功能一致性矩阵.md`）是行为验收输入，**不是**第二份接口规范；发生措辞冲突时以本文件与活动生产源码为准。
变更流程：见 `web/contracts/README.md`。任何契约变更必须在同一任务内同步更新本文、`fixtures/**` 与双端类型，并交 `contract-guardian` 复核。

术语：正文用中文；路径、字段、枚举值、错误码保持英文原样，首次出现处给中文解释。DTO 字段词汇以 `web/shared/domain/src/main/java/com/treelearn/core/domain/` 的领域类为对照来源，端点语义以 `android/app`（及 `android/data`）的活动调用链为对照来源，逐项标注见文末「12. 对照来源」。

---

## 1. 基础约定

- **传输**：HTTP/1.1，`Content-Type: application/json; charset=UTF-8`（SSE 见第 7 节）。服务只绑定 loopback（`127.0.0.1:8733`），浏览器经 `localhost`/同源访问。
- **鉴权**：无。服务为单机本地应用，不设账户、令牌或跨端同步（技术方案 §2.2；`web/README.md`）。
- **成功响应**：返回**裸对象或裸数组**，不使用 `{data: ...}` 包裹层。就绪端点的响应体冻结为 `{"service":"treelearn-web-backend","status":"ready"}`（见 6.1）。
- **非 2xx 响应**：统一错误封装，见第 3 节。校验失败、找不到资源、状态冲突、上游失败都不返回裸异常文本。
- **请求体**：变更类请求（产生持久副作用的 POST/PUT/DELETE）必须携带客户端生成的 `commandId`（UUID 字符串，见第 4 节）。只读 GET 不带 `commandId`。
- **命名**：路径段用 kebab-case（`reading-position`、`deletion-preview`、`model-roles`）；JSON 字段用 lowerCamelCase；枚举值用 SCREAMING_SNAKE_CASE。
- **默认不含**：响应不返回 API Key 明文（只返回 `hasKey` 布尔）；不返回 prompt 正文、快照 JSON、供应商响应原文（技术方案 §14/§15）。

---

## 2. 时间与日期规则

- **真实时间点**：DTO 中一律为 JSON number，含义为 **UTC epoch 毫秒**；字段名以 `Millis` 结尾（如 `createdAtMillis`、`updatedAtMillis`、`archivedAtMillis`、`stableAtMillis`）。禁止无时区的本地日期时间字符串。规则来源：`rules/contracts.md`「时间字段契约」、`rules/backend.md`「Java 时间边界」、`rules/sql.md`「UTC 时间存储规范」。
- **date-only**：只有“某一天”语义的值用字符串并**单独标识**，消费者不得对它施加任何时区偏移：
  - `month`：`"YYYY-MM"`（例 `"2026-10"`），表示业务自然月；
  - `day`：`"YYYY-MM-DD"`（例 `"2026-10-05"`），表示业务自然日。
- **设备时区**：真实时间点由前端按**当前设备时区**在渲染时格式化（`rules/frontend.md`「设备时区与时间展示」）；状态层保留原始 `Millis`，不持久化本地化文案。
- **统计端点**：`timeZone` 查询参数为 IANA 时区名（例 `Asia/Shanghai`）。服务端按传入的业务时区把 UTC 时间点折算到自然日/自然月后计算；响应中的时间点仍回传 UTC `Millis`，`month`/`day` 字段为 date-only 字符串。设备时区变化后，客户端应重新请求（`rules/frontend.md`）。
- **fixtures**：所有时间点样例取固定常量（如 `1700000000000`），避免“当前时间”导致往返比对不稳定。

---

## 3. 错误封装与状态码映射

### 3.1 封装格式

所有非 2xx 响应体：

```json
{
  "error": {
    "code": "RATE_LIMITED",
    "params": { "httpStatus": 429 },
    "retryable": true
  },
  "requestId": "b6f1c0d2-..."
}
```

字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `error.code` | string | 机器可读错误码，取自 3.2 词汇表；前端据此选择 i18n 文案 `errors.<code>` |
| `error.params` | object | 仅含**安全插值参数**（数字、枚举名、字段名、剩余字符数等），不得含问题正文、回答正文、prompt、quote、快照或供应商原文；允许为空对象 `{}` |
| `error.retryable` | boolean | 该错误是否可由客户端/用户直接重试且不改变已确认范围 |
| `requestId` | string | 服务端生成的传输请求标识（UUID），仅用于排障关联，**不是**业务提交身份（不得拿它当 `commandId`；见第 4 节与 `observability.md`） |

文案归属：前端按 `errors.<code>` 从 `zh-CN`/`en-US` 资源解析，并把 `params` 作为插值传入；后端不返回面向用户的句子（`rules/frontend.md`「用户错误提示」、`rules/documentation.md`）。

### 3.2 HTTP 状态码映射

| HTTP | 语义 | 何时使用 |
|---|---|---|
| 400 | 请求不可用（客户端可修复） | 参数/校验失败、无 Key/无模型、供应商拒绝请求、附件超限或类型不支持、上下文超出预算 |
| 404 | 目标不存在 | 主题/分支/消息/版本/操作/提供商/附件/批注/派生结果不存在 |
| 409 | 状态冲突（当前资源状态禁止该操作） | 单分支已有活跃操作、非稳定回答不可分叉、删除计划 digest 过期、版本已激活、序列越界、已归档再归档（幂等冲突）、主题 id 已存在 |
| 500 | 服务端内部错误 | 本地写失败（STORAGE）、prompt 策略版本错配、快照损坏、未分类内部错误 |
| 503 | 上游/网络暂不可用 | 限流、供应商不可用、网络中断、流畸形 |

同一错误码的 HTTP 状态是固定的（见 3.3 表）。语义分组与 `rules/contracts.md`、`AiGatewayError.forHttpStatus` 保持一致，但**暴露给浏览器的是本表的后端映射，不是供应商原始状态**（禁止泄漏供应商状态码与响应体）。

### 3.3 错误码词汇表

`retryable` 指“在用户/客户端不改变已确认输入的前提下，直接重试是否有意义”。词汇对齐 Android 活动错误分类（见文末对照来源），不发明新类别。

**上游/供应商类**（来源：`android/ai/.../AiGatewayError.Kind`，`AiGatewayError.forHttpStatus`）

| code | HTTP | retryable | params 键 | 触发条件 |
|---|---|---|---|---|
| `KEY_MISSING` | 400 | false | `{}` | 未配置任何可用 Key，请求不得发出（`AiGatewayError.keyMissing`） |
| `AUTH_INVALID` | 400 | false | `{ "httpStatus" }` | 供应商拒绝凭据（401/403 → `Kind.AUTH_INVALID`） |
| `BALANCE_UNAVAILABLE` | 400 | false | `{ "httpStatus" }` | 账户无法为该次调用付费（402 → `Kind.BALANCE_UNAVAILABLE`） |
| `REQUEST_INVALID` | 400 | false | `{ "httpStatus" }` | 供应商拒绝请求本身（其它 4xx → `Kind.REQUEST_INVALID`） |
| `RATE_LIMITED` | 503 | true | `{ "httpStatus" }` | 供应商限流（429 → `Kind.RATE_LIMITED`） |
| `PROVIDER_UNAVAILABLE` | 503 | true | `{ "httpStatus" }` | 供应商 5xx 或 2xx 流内错误对象（`Kind.PROVIDER_UNAVAILABLE`、`inBandProviderError`） |
| `NETWORK_INTERRUPTED` | 503 | true | `{}` | 连接失败/超时/流未完成即断开（`Kind.NETWORK_INTERRUPTED`） |
| `PARSE_FAILED` | 503 | true | `{ "channel" }` | 流载荷不可信被拒（`Kind.PARSE_FAILED`）；`channel` ∈ `REASONING`/`ANSWER` |

**领域状态类**（来源：`web/shared/domain/.../DomainErrorCode`、`android/app/.../PositionCommands`）

| code | HTTP | retryable | params 键 | 触发条件 |
|---|---|---|---|---|
| `OPERATION_NOT_STABLE` | 409 | false | `{}` | 非 STABLE 的尝试被用于要求稳定版本处（分叉、摘要基线等；`DomainErrorCode.OPERATION_NOT_STABLE`、`PositionCommands.NO_STABLE_VERSION`） |
| `OPERATION_NOT_COMPLETABLE` | 404 | false | `{}` | 位置/版本不存在或无对应行（`PositionCommands.MESSAGE_NOT_FOUND`/`REVISION_NOT_OF_THIS_POSITION` → `OPERATION_NOT_COMPLETABLE`；`GenerationOperation.stabilize` 回答为空） |
| `REVISION_IMMUTABLE` | 409 | false | `{}` | 试图改写稳定版本，或重复选择当前版本（`DomainErrorCode.REVISION_IMMUTABLE`、`PositionCommands.REVISION_ALREADY_ACTIVE`） |
| `MESSAGE_ROLE_MISMATCH` | 400 | false | `{}` | 消息角色不支持该操作（如对 USER 位置做版本操作；`DomainErrorCode.MESSAGE_ROLE_MISMATCH`） |
| `ILLEGAL_STATE_TRANSITION` | 409 | false | `{ "state" }` | 当前状态不允许该转换，含终态收到晚到回调（`DomainErrorCode.ILLEGAL_STATE_TRANSITION`、`PositionCommands.ATTEMPT_ALREADY_RUNNING`） |
| `ANCHOR_INVALID` | 400 | false | `{}` | 分叉锚点区间畸形或缺少 quote/hash 回退（`DomainErrorCode.ANCHOR_INVALID`、`BranchAnchor.ofRange`） |
| `SEQUENCE_OUT_OF_ORDER` | 409 | false | `{}` | 已读序列回退或越过已提交值（`DomainErrorCode.SEQUENCE_OUT_OF_ORDER`、`Branch.markSeenThrough`） |
| `PROMPT_POLICY_VERSION_REQUIRED` | 500 | false | `{}` | 生成请求未固定 prompt 策略版本（`DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED`） |
| `PROMPT_POLICY_VERSION_MISMATCH` | 500 | false | `{ "purpose" }` | 固定的版本不属于声明的用途（`DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH`） |
| `CONTEXT_EXCEEDS_BUDGET` | 400 | false | `{ "capacityChars", "minAnswerChars" }` | 上下文加保留容量不满足请求预算（`DomainErrorCode.CONTEXT_EXCEEDS_BUDGET`；容量常量见 `android/.../wiring/PromptLimits`） |
| `CONTEXT_SOURCE_MISSING` | 409 | false | `{}` | 快照固定的上下文来源在库中无法解析（`DomainErrorCode.CONTEXT_SOURCE_MISSING`，`SnapshotHistoryMerger.merge` 拒绝） |
| `CONTEXT_SUMMARY_MISSING` | 409 | false | `{}` | 快照固定的摘要尚未生成（`DomainErrorCode.CONTEXT_SUMMARY_MISSING`） |
| `SNAPSHOT_PAYLOAD_INVALID` | 500 | false | `{}` | 存储的快照载荷非法（`DomainErrorCode.SNAPSHOT_PAYLOAD_INVALID`） |
| `SNAPSHOT_DIGEST_MISMATCH` | 500 | false | `{}` | 快照载荷与其摘要不符（`DomainErrorCode.SNAPSHOT_DIGEST_MISMATCH`） |
| `SNAPSHOT_SCHEMA_UNSUPPORTED` | 500 | false | `{ "schemaVersion" }` | 本构建无法读取该快照 schema 版本（`DomainErrorCode.SNAPSHOT_SCHEMA_UNSUPPORTED`） |

**命令/并发类**

| code | HTTP | retryable | params 键 | 触发条件 |
|---|---|---|---|---|
| `BRANCH_BUSY` | 409 | false | `{ "branchId" }` | 同一分支同一时刻只能有一个活跃操作（`GenerationCoordinator.Refusal.BRANCH_BUSY`、`GenerationScheduler.Admission.BRANCH_BUSY`） |
| `DELETION_PLAN_EXPIRED` | 409 | false | `{}` | 删除确认携带的预览 digest 与当前树快照不符（Web 新增：预览-确认两步 + digest；对齐 `NavigationData.plan` 的 fail-closed 口径，见 6.2 / 6.3） |
| `SCOPE_UNRESOLVABLE` | 409 | false | `{}` | 删除预览依赖的树快照无法解析/已失效：目标不在树中、无树、或节点指向不存在的父（`NavigationData.plan` 返回 null → `NavigationViewState.Notice.SCOPE_UNRESOLVABLE`），fail-closed 不展示错误范围 |
| `TOPIC_ID_ALREADY_EXISTS` | 409 | false | `{}` | 同一 `commandId` 之外重复创建同一主题 id（`FirstTopicCreation.Refusal.TOPIC_ID_ALREADY_EXISTS`） |
| `BRANCH_NOT_ARCHIVABLE` | 400 | false | `{}` | 对根分支执行归档/恢复：根分支不可归档（`Branch.isRoot()`；对齐矩阵「根分支不可归档」） |
| `NOTE_BODY_REQUIRED` | 400 | false | `{ "field" }` | 写入 NOTE 批注但正文为空；`field` 恒为 `body`（`Annotation.note` 拒绝空正文） |
| `SOURCE_MESSAGE_NOT_IN_TOPIC` | 400 | false | `{}` | 分叉来源消息/版本不属于本主题（`FirstTopicCreation.Refusal.SOURCE_MESSAGE_NOT_IN_TOPIC`、`BranchCreation.Refusal.SOURCE_NOT_IN_THIS_TOPIC`） |
| `STORAGE_REFUSED` | 500 | true | `{}` | 本地写被拒（`FirstTopicCreation.Refusal.STORAGE_REFUSED`、`GenerationCoordinator.Refusal.STORAGE`）；响应不携带 SQLite 原文 |

**配置类**

| code | HTTP | retryable | params 键 | 触发条件 |
|---|---|---|---|---|
| `PROVIDER_INVALID` | 400 | false | `{ "field" }` | 提供商名或 baseUrl 非法；`field` ∈ `name`/`baseUrl`（`ProviderAdminPresenter.Notice.NAME_REQUIRED`/`BASE_REQUIRED`）；baseUrl 必须 `https://` 开头 |
| `PROVIDER_NAME_TAKEN` | 409 | false | `{}` | 提供商名重复（`ProviderAdminPresenter.Notice.NAME_TAKEN`） |
| `KEY_REQUIRED` | 400 | false | `{ "scope" }` | 保存的 Key 为空；`scope` ∈ `PROVIDER`/`WEB_SEARCH`（`ProviderAdminPresenter.Notice.KEY_REQUIRED`、`SearchSettingsPresenter.Notice.KEY_REQUIRED`） |
| `KEY_SAVE_FAILED` | 500 | true | `{ "scope" }` | Key 落盘失败（`ProviderAdminPresenter.Notice.KEY_SAVE_FAILED`、`SearchSettingsPresenter.Notice.KEY_SAVE_FAILED`） |
| `NO_MODELS` | 400 | false | `{}` | 该角色（CHAT/FAST/TRANSLATION）无任何可用模型（`HomeViewState.Notice.NO_MODELS`、`ModelSettingsPresenter.Notice.NO_MODELS`） |
| `WEB_SEARCH_KEY_MISSING` | 400 | false | `{ "engine" }` | 联网搜索已启用但对应引擎无 Key（`HomeViewState.Notice.SEARCH_KEY_MISSING`、`SearchSettingsPresenter.Notice.NOKEY`） |
| `WEB_SEARCH_FAILED` | 503 | true | `{ "engine" }` | 联网搜索调用未完成（`HomeViewState.Notice.SEARCH_FAILED`） |

**附件类**

| code | HTTP | retryable | params 键 | 触发条件 |
|---|---|---|---|---|
| `ATTACHMENT_TOO_LARGE` | 400 | false | `{ "maxBytes" }` | 上传字节超过来源上限（`android/.../files/AttachmentTextExtractor.MAX_SOURCE_BYTES` = 20 MiB；注入上限 `HomeViewState.Notice.ATTACHMENT_TOO_LARGE`） |
| `ATTACHMENT_TYPE_UNSUPPORTED` | 400 | false | `{ "mimeType" }` | 内容无法按受支持格式解析（`AttachmentTextExtractor.extract` 拒绝 → `AttachmentStore.PARSE_STATE_FAILED`） |

**通用类**

| code | HTTP | retryable | params 键 | 触发条件 |
|---|---|---|---|---|
| `NOT_FOUND` | 404 | false | `{ "resource" }` | 通用“目标不存在”；`resource` ∈ `topic`/`branch`/`message`/`revision`/`operation`/`provider`/`attachment`/`annotation`/`artifact`/`endpoint`（`endpoint` 用于未声明的 `/api/**` 路径兜底，含大小写变体） |
| `INTERNAL_ERROR` | 500 | true | `{}` | 未分类服务端内部错误；不泄漏堆栈与内部标识 |

---

## 4. 命令幂等（commandId）

- 每个产生持久副作用的变更请求，请求体必须带 `commandId`：客户端生成、全生命周期唯一的 UUID 字符串。
- **同 `commandId` 重放语义**：服务端必须返回**与首次相同**的结果（同一 operation / 同一资源 id），**不得**产生第二条消息、第二次模型调用或第二个操作行。幂等身份持久化在 `generation_operations.command_id`（对齐技术方案 §8；`web/shared/domain/.../GenerationOperation.commandId`）。
- 客户端义务：网络不确定（超时、连接中断）后**不得**生成新的 `commandId` 重发；应携带原 `commandId` 重放，或查询权威 `GET /api/operations/{id}` / `GET /api/operations/active?branchId=` 确认状态（详细开发方案 §6：禁止盲目重发）。
- `requestId`（错误封装内的传输标识）与 `commandId` 是**两个不同身份**，不得互相替代（`observability.md`）。
- 只读 GET 不要求 `commandId`。

---

## 5. DTO 词汇表

以下为端点会出现的对象形状。字段词汇对照 `web/shared/domain/.../domain` 的领域类，`*Millis` 一律 UTC epoch 毫秒。

### 5.1 Topic（主题）

对照 `Topic`。注意：主题**不携带自身名称**，树的标题总是来自根分支标题。

```json
{
  "id": "uuid",
  "lastVisitedBranchId": "uuid-or-null",
  "createdAtMillis": 1700000000000,
  "updatedAtMillis": 1700000000000
}
```

### 5.1b TopicSummary（主题概览行）

`GET /api/topics` 的列表行：主题自身加上主页卡片所需的派生显示名。**2026-10-10 新增，替代裸 `Topic` 列表**。主题不携带自身名称（5.1），标题一律来自根分支；`rootTitle` 未派生或 `rootQuestion` 为空时按序回退，`displayTitle` 即该回退结果。

```json
{
  "topicId": "6f3a1c2b-0d4e-4a5f-9b6c-7d8e9f0a1b2c",
  "rootTitle": "大语言模型的注意力机制",
  "rootQuestion": "注意力机制到底是什么？",
  "displayTitle": "大语言模型的注意力机制",
  "lastUsedAtMillis": 1700000600000
}
```

- `topicId`：主题 id（`Topic.id`）。
- `rootTitle`：根分支（`parentBranchId IS NULL`）的 `Branch.title`；尚未派生时为 null。
- `rootQuestion`：根分支首条 USER 消息的 `userContent`（按 `ordinal` 升序第一条）；根分支尚无消息时为 null。它是 `displayTitle` 的回退来源。
- `displayTitle`：派生只读字段 = `rootTitle`，为 null 时回退 `rootQuestion`；两者皆 null（根分支尚未写入首问，非正常创建态）时为 null。
- `lastUsedAtMillis`：主题自身的 `updatedAt` 时间戳（UTC epoch 毫秒），列表即按它倒序。

### 5.2 Branch（分支）

对照 `Branch`。`parentBranchId` 与 `source` **恰好在根分支同时为 null**；非根分支必须带 `source`。未读由序列差推导：`unreadCount = max(0, latestStableSequence - lastSeenStableSequence)`（不存在独立未读布尔）。

```json
{
  "id": "uuid",
  "topicId": "uuid",
  "parentBranchId": "uuid-or-null",
  "source": { "parentBranchId": "uuid", "sourceMessageId": "uuid", "sourceRevisionId": "uuid",
              "anchor": { "startInclusive": 12, "endExclusive": 30, "quote": "...", "hash": "..." },
              "contextSnapshotId": "uuid" },
  "title": "string-or-null",
  "createdAtMillis": 1700000000000,
  "updatedAtMillis": 1700000000000,
  "latestStableSequence": 2,
  "lastSeenStableSequence": 1,
  "unreadCount": 1,
  "collapsed": false,
  "archivedAtMillis": null
}
```

- `source` 为 `null` 表示根分支；否则为 `BranchSource`：`anchor` 为整条分叉时四字段全 null（`BranchAnchor.wholeAnswer`），为选文分叉时 `startInclusive`/`endExclusive`（半开区间，end exclusive）+ `quote` + `hash` 必填（`BranchAnchor.ofRange`）。
- `unreadCount` 为派生只读值；树级汇总（折叠祖先携带后代未读、归档分支不计入父级）由 `UnreadRollup.visibleUnread` 决定。

### 5.3 BranchTree（分支树）

`GET /api/topics/{id}/branches` 的响应：`{ "topicId", "nodes": [Branch, ...] }`，`nodes` 根在前。折叠/归档/未读/阅读位置随各 `Branch` 携带；归档分支仍返回（`archivedAtMillis != null`），前端按可见性规则隐藏。

### 5.4 Message（消息位置）

对照 `Message`。消息是**位置**，不是版本容器。`ordinal` 为分支内顺序，retry/reset 不改动它。USER 位置的 `userContent` 不可变、`quotedText` 为其引用的回答片段（无引用为 `""`，永不为 null）；AI 位置的 `userContent` 为 null 且 `quotedText` 恒为 `""`。

```json
{
  "id": "uuid",
  "branchId": "uuid",
  "role": "USER",
  "ordinal": 0,
  "userContent": "问题的原文",
  "quotedText": "",
  "createdAtMillis": 1700000000000,
  "activeRevisionId": null,
  "activeOperationId": "uuid-or-null",
  "attachments": [ { "attachmentId": "uuid", "displayName": "notes.pdf", "present": true } ],
  "versions": [ AnswerRevision, ...（完整对象列表，最旧在前） ]
}
```

- AI 位置：`role=AI`，`userContent=null`，`activeRevisionId` 指向当前显示版本（无则为 null），`activeOperationId` 指向正在运行的尝试（无则为 null），`versions` 为该位置的全部稳定 `AnswerRevision` **完整对象**列表（最旧在前，`AnswerRevisionDao.findByMessage`）；不是摘要或部分字段。
- `attachments` 仅出现在 USER 位置；引用记录已被删除时 `present=false` 且 `displayName=null`（缺失标注，`AttachmentStore.refsOf`）。
- 派生结果（推荐、翻译）以只读投影附带于 AI 位置，见 5.10。

### 5.5 AnswerRevision（回答版本）

对照 `AnswerRevision`。版本一旦产生即冻结、不可变；`revisionNo` 从 1 单调递增；`promptPolicyVersion` 保持产生它的版本，永不改写。

```json
{
  "id": "uuid",
  "messageId": "uuid",
  "revisionNo": 1,
  "reasoningContent": "思考过程全文，可为空",
  "answerContent": "最终回答全文，非空",
  "model": "deepseek-v4-pro",
  "thinkingEnabled": true,
  "promptPolicyVersion": "tl-learning-answer/1.1.0",
  "createdAtMillis": 1700000000000
}
```

- `model` 取值：`deepseek-flash`（FLASH）、`deepseek-v4-pro`（PRO，`GenerationModel.wireId`）。

### 5.6 Operation（生成操作）

对照 `GenerationOperation` + `GenerationState`。

```json
{
  "id": "uuid",
  "commandId": "uuid",
  "branchId": "uuid",
  "messageId": "uuid",
  "promptPurpose": "LEARNING_ANSWER",
  "promptPolicyVersion": "tl-learning-answer/1.1.0",
  "model": "deepseek-v4-pro",
  "thinkingEnabled": true,
  "state": "STREAMING_ANSWER",
  "partialReasoning": "已收到的推理增量",
  "partialAnswer": "已收到的回答增量",
  "createdAtMillis": 1700000000000,
  "errorCode": null,
  "sseUrl": "/api/operations/{id}/events"
}
```

- `state` 枚举（`GenerationState`）：`STAGED`、`VALIDATING`、`SUBMITTING`、`STREAMING_REASONING`、`STREAMING_ANSWER`、`STABLE`、`FAILED`、`INTERRUPTED`、`CANCELED`。活跃 = 前五者；`STABLE`/`FAILED`/`INTERRUPTED`/`CANCELED` 为终态，终态不再改变。
- `errorCode`：终态为 `FAILED` 时取自错误码词汇表（上游类），否则为 null（对齐 `GenerationOperationDao.markTerminal(... errorKind ...)`）。
- `partialReasoning`/`partialAnswer` 为部分内容，恢复时读取（`SqliteChatStore.position` 用 `attempt.partialReasoning/partialAnswer`）。

### 5.7 Attachment（附件）

对照 `AttachmentStore.Entry`。

```json
{
  "attachmentId": "uuid",
  "displayName": "notes.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 12345,
  "parseState": "parsed",
  "present": true,
  "referenceCount": 1
}
```

- `parseState` ∈ `pending`/`parsed`/`parse_failed`（`AttachmentStore.PARSE_STATE_*`）。
- `present` = 物理文件存在；`referenceCount` = 引用该记录的消息数。

### 5.8 Annotation（批注）

对照 `Annotation`。

```json
{
  "id": "uuid",
  "container": "PROSE",
  "proseIndex": 0,
  "tableIndex": -1,
  "rowIndex": -1,
  "colIndex": -1,
  "start": 12,
  "end": 30,
  "kind": "HIGHLIGHT",
  "body": ""
}
```

- `container` ∈ `PROSE`/`CELL`（`Annotation.Container`）；`kind` ∈ `HIGHLIGHT`/`NOTE`（`Annotation.Kind`）。空容器/不适用索引用 `-1` 哨兵。
- `start` inclusive、`end` exclusive（半开区间）；`body` 在 HIGHLIGHT 恒为 `""`，NOTE 非空。
- HIGHLIGHT 的相交或相邻合并、精确范围取消由共享领域规则 `Annotation.mergeHighlights`/`Highlighter.plan` 在**服务端**执行。

### 5.9 Provider / Model（提供商与模型）

对照 `ProviderAdminPresenter.Row`、`ModelSettingsPresenter.Option`。

Provider：
```json
{ "id": "uuid", "name": "DeepSeek", "baseUrl": "https://api.deepseek.com", "enabled": true,
  "hasKey": true, "modelCount": 2 }
```
Model：
```json
{ "providerId": "uuid", "providerName": "DeepSeek", "modelId": "deepseek-v4-pro" }
```
> Key **只写不读**：请求体可含 `apiKey`，任何响应都只回 `hasKey`，绝不回明文。

### 5.10 DerivedArtifact（派生结果）

对照 `ArtifactKind`、`DerivedOperation`、`DerivedResult`。派生结果按 `kind` 落 `ai_artifacts`。

```json
{
  "kind": "answer_recommendations",
  "purpose": "ANSWER_RECOMMENDATIONS",
  "scopeId": "uuid",
  "basedOnRevisionId": "uuid-or-null",
  "inputDigest": "hex",
  "payload": { "items": ["...", "...", "..."] },
  "createdAtMillis": 1700000000000,
  "stale": false
}
```

- `kind` 与 `purpose`（`ArtifactKind`/`PromptPurpose`）：
  | kind (wireId) | purpose | scope | revisionBound |
  |---|---|---|---|
  | `root_title` | `ROOT_TITLE` | BRANCH | 否 |
  | `branch_title` | `BRANCH_TITLE` | BRANCH | 否 |
  | `branch_summary` | `BRANCH_SUMMARY` | BRANCH | 是 |
  | `answer_recommendations` | `ANSWER_RECOMMENDATIONS` | BRANCH | 是 |
  | `home_recommendations` | `HOME_RECOMMENDATIONS` | TOPIC | 否 |
  | `context_compression` | `CONTEXT_COMPRESSION` | BRANCH | 是 |
  | （翻译，无独立 kind 落库） | `MESSAGE_TRANSLATION` | — | — |
- **stale 判定**：`revisionBound` 的结果必须与请求时的 `basedOnRevisionId` 一致；否则视为过期（读作“无结果”而非旧文本）。`inputDigest` 为输入指纹；写回时在同一事务内比较实时基线，不一致则丢弃（`DerivedOperation.writeBack` CAS）。压缩额外要求覆盖 reasoning（`inputCoversReasoning`）且确实收缩（`OVER_WINDOW`）。

### 5.11 WebSearchSettings（联网搜索设置）

```json
{
  "searchEnabled": true,
  "engine": "TAVILY",
  "tier": "FIVE",
  "services": [ { "engine": "ZHIPU", "hasKey": false },
                { "engine": "TAVILY", "hasKey": true },
                { "engine": "BOCHA", "hasKey": false } ],
  "notice": "NONE"
}
```
- `engine` ∈ `ZHIPU`/`TAVILY`/`BOCHA`；`tier` ∈ `THREE`/`FIVE`/`TEN`（`SearchSettingsPresenter.Engine`/`Tier`）。
- `notice` ∈ `NONE`/`NOKEY`。

### 5.12 ModelRoles / ModelResolution（模型分工与回落）

`GET /api/settings/model-roles`：
```json
{
  "roles": {
    "CHAT":        { "providerId": "uuid", "modelId": "deepseek-v4-pro", "fallback": "NONE" },
    "FAST":        { "providerId": "uuid", "modelId": "deepseek-flash",  "fallback": "BUILTIN" },
    "TRANSLATION": { "providerId": null,  "modelId": null,               "fallback": "EMPTY" }
  },
  "budget": "AUTO",
  "suggestions": true,
  "notice": "NONE"
}
```
- `roles` 键固定为 `CHAT`/`FAST`/`TRANSLATION`（`ModelSettingsPresenter.Role`）；每项 `fallback` ∈ `NONE`/`BUILTIN`/`EARLIEST`/`EMPTY`（`ModelSettingsPresenter.Fallback`）。
- `budget` ∈ `OFF`/`AUTO`/`LOW`/`MID`/`HIGH`（`ModelSettingsPresenter.Budget`）；`suggestions` 为建议开关；`notice` ∈ `NONE`/`FALLEN_BACK`/`NO_MODELS`。

`GET /api/settings/model-resolution` 响应 `ModelResolution`（完整形状）：
```json
{
  "roles": {
    "CHAT":        { "providerId": "uuid", "modelId": "deepseek-v4-pro", "fallback": "NONE" },
    "FAST":        { "providerId": "uuid", "modelId": "deepseek-flash",  "fallback": "BUILTIN" },
    "TRANSLATION": { "providerId": null,  "modelId": null,               "fallback": "EMPTY" }
  }
}
```
- `ModelResolution` **只含 `roles`**，不含 `budget`/`suggestions`/`notice`；键集合与每项字段同 `ModelRoles.roles`。
- 每个角色给**最终解析结果**，回落类别只以 `fallback` token 表达：`NONE`（配置的模型可用）、`BUILTIN`（失效时回落到内置默认）、`EARLIEST`（无内置默认时回落到最早可用）、`EMPTY`（无任何可用模型）。回落 token → 文案由前端按 `modelFallback.<token>` i18n 渲染，**后端不返回面向用户的句子**（多 Provider 规格：配置回落不等于请求失败时偷偷换供应商）。

### 5.13 SearchResult（应用内搜索命中）

对照 `SearchHit`、`SearchQuery`。

```json
{
  "query": "光合作用",
  "hits": [
    { "field": "ANSWER", "topicId": "uuid", "branchId": "uuid", "messageId": "uuid",
      "messageOrdinal": 1, "revisionId": "uuid", "revisionNo": 1,
      "snippet": "...命中周边文本...", "start": 12, "end": 16 }
  ]
}
```
- `field` ∈ `TITLE`/`QUESTION`/`ANCHOR`/`ANSWER`/`REASONING`/`PARTIAL_ANSWER`/`PARTIAL_REASONING`（`SearchHit.Field`）。`messageId`/`revisionId` 为 null 表示非消息命中（如标题）；`messageOrdinal=-1`/`revisionNo=0` 表示无消息/无版本。
- `snippet` 为命中文本；`start`/`end` 为命中区间的 **UTF-16** 半开区间（相对 `snippet`）。
- 空查询（`q` 去掉空白后为空）不触发全表扫描；服务端返回 `200` 且 `hits=[]`（对齐 `SearchQuery.isEmpty`：`LIKE '%%'` 会匹配全表，禁止）。
- 结果顺序采用 `SearchHit.DISPLAY_ORDER`（field → topicId → branchId → messageOrdinal → revisionNo → messageId → revisionId）。

### 5.14 Stats（学习统计）

```json
{
  "empty": false,
  "timeZone": "Asia/Shanghai",
  "month": "2026-10",
  "totalQuestions": 42,
  "totalMessages": 88,
  "currentStreakDays": 5,
  "activeDays": ["2026-10-03", "2026-10-04", "2026-10-05"],
  "monthCells": [ -1, -1, 1, 0, 1, ... ]
}
```
- 口径：有回答即活跃（`StatsAggregator.HAS_ANY_ANSWER`）；连续学习按“截至今天或昨天”的连续活跃日推算（`StatsAggregator.currentStreak`）；`empty` 仅在库中无任何行时为 true。
- `activeDays` 为 date-only `YYYY-MM-DD`；`monthCells` 为当月热力格（`-1` 表示未来/空月格），对齐 `HomeStats.monthIntensities` 语义；`month` 为 date-only `YYYY-MM`。
- 空月返回零值结构（`empty` 视库而定，`currentStreakDays=0`，`activeDays=[]`），不返回 404。

### 5.15 DeletionPreview（删除预览）

```json
{
  "topicId": "uuid",
  "targetBranchId": "uuid",
  "wholeTopic": false,
  "branchIds": ["uuid", "uuid"],
  "branchCount": 2,
  "digest": "hex"
}
```
- 由树快照计算影响范围（父在子前）；失败关闭：目标不在树中、无树、或节点指向不存在的父时返回 `409 SCOPE_UNRESOLVABLE`（`NavigationData.plan` 返回 null；`NavigationViewState.Notice.SCOPE_UNRESOLVABLE`）。`digest` 是范围快照指纹，用于确认时校验未过期（过期时 `409 DELETION_PLAN_EXPIRED`）。端点见 6.2（整树）与 6.3（子树）。

---

## 6. 端点逐条

通用约定：成功状态码见各条；错误见第 3 节。异步生成类命令成功后，客户端打开返回对象的 `sseUrl`。所有变更请求体含 `commandId`（第 4 节）。

### 6.1 健康

`GET /api/health/ready` —— **冻结现状**（`web/backend/.../api/ReadyController.java`）。
- 响应 `200`：`{"service":"treelearn-web-backend","status":"ready"}`（字段名与值均不变）。

### 6.2 主题

**`GET /api/topics`** —— 主题列表（概览行），按最近使用（`Topic.updatedAt`）倒序。
- 响应 `200`：`[TopicSummary, ...]`（见 5.1b；带派生显示名 `displayTitle`）。空库返回 `[]`。

**`POST /api/topics`** —— 首问原子接受（创建主题 + 根分支 + USER 消息 + AI 逻辑位 + 操作）。
- 请求：`{ "commandId", "question", "model", "thinkingEnabled", "quotedText"?, "attachmentId"? }`
- 语义：先校验配置/Key 与预算；**无 Key 或校验失败不得落空主题**（`FirstTopicCreation` 五组行同一事务，或全不落；对齐 `HomeViewState`「无 Key 不创建空主题」）。
- 响应 `200`：`TopicBootstrap`：`{ "topic": Topic, "rootBranch": Branch, "userMessage": Message, "aiMessage": Message, "operation": Operation }`。
- 错误：`KEY_MISSING`/`NO_MODELS`/`REQUEST_INVALID`/`CONTEXT_EXCEEDS_BUDGET`/`STORAGE_REFUSED`。
- 竞态：同一 `commandId` 重放返回同一结果（第 4 节）。

**`GET /api/topics/{id}`** —— 主题详情。
- 响应 `200`：`Topic`；不存在 `404 NOT_FOUND`。

**`GET /api/topics/{id}/deletion-preview`** —— 整树删除预览（影响范围 + digest）。
- 响应 `200`：`DeletionPreview`（`wholeTopic=true`）；范围不可计算 `409`。

**`POST /api/topics/{id}/delete`** —— 确认删除整树。
- 请求：`{ "commandId", "digest" }`
- 语义：`digest` 与当前预览不一致 → `409 DELETION_PLAN_EXPIRED`；否则先取消活跃操作、再单事务删除（`NavigationData.cancelInFlight` → `NavigationData.delete`，顺序固定）。
- 响应 `200`：`{ "deletedTopicId": "uuid", "deletedBranchIds": ["uuid", ...] }`。
- 幂等：同 `commandId` 重放返回同一删除结果；目标已不存在视为成功。

### 6.3 分支

**`GET /api/topics/{id}/branches`** —— 分支树与折叠/未读/阅读位置/归档。
- 响应 `200`：`BranchTree`。

**`GET /api/branches/{id}/messages`** —— 分支消息（含当前版本、附件引用、派生投影、阅读位置）。
- 响应 `200`：`{ "branchId", "messages": [Message, ...], "readingAnchor": ReadingAnchor?, "recommendations": [string] }`。`messages` 按 `ordinal` 升序（`MessageDao.findByBranch`）。

**`POST /api/branches/{id}/messages`** —— 连续追问（只向当前分支追加，普通追问不增枝）。
- 请求：`{ "commandId", "text", "quote"?, "attachmentId"?, "model", "thinkingEnabled" }`（`quote` 可为空串；切换分支不得覆盖他分支草稿，草稿由前端按分支保存）。
- 响应 `200`：`{ "userMessage": Message, "aiMessage": Message, "operation": Operation }`。
- 错误：`BRANCH_BUSY`（分支已有活跃操作）、`KEY_MISSING`、`NO_MODELS`、`STORAGE_REFUSED`。

**`POST /api/branches/{id}/forks`** —— 整条/选文分叉（冻结快照 + 溯源 + 可选首问原子接受）。
- 请求：`{ "commandId", "sourceMessageId", "sourceRevisionId", "anchor": {"startInclusive","endExclusive","quote","hash"} | null, "question"?, "model"?, "thinkingEnabled"? }`
- 语义：只消费**稳定**版本；快照不可变；`anchor` 为 null 表示整条分叉（`wholeAnswer`），否则区间半开且 quote 长度必须等于区间跨度（`BranchAnchor.ofRange`）。`question` 存在时等价首问：在创建分支后原子接受该首问（同源规则：来源必须属于本主题）。
- 响应 `200`：`{ "branch": Branch, "source": BranchSource, "bootstrap": TopicBootstrap? }`（`bootstrap` 仅在有 `question` 时出现）。
  - `bootstrap.rootBranch` 指**新建的子分支**（新子树的根），不是原根分支。
- 错误：`OPERATION_NOT_STABLE`（非稳定回答不可分叉）、`ANCHOR_INVALID`、`SOURCE_MESSAGE_NOT_IN_TOPIC`、`NOT_FOUND`。

**`POST /api/branches/{id}/archive` / `POST /api/branches/{id}/restore`** —— 归档 / 恢复。
- 请求：`{ "commandId" }`。
- 语义：**根分支不可归档**（拒绝）；子树整体隐藏/恢复；归档不取消生成、不删除历史（`NavigationPresenter.onArchive`、`Branch.archive`）。
- 响应 `200`：`Branch`。
- 幂等/拒绝：已归档再归档（或未归档再恢复）→ `409 ILLEGAL_STATE_TRANSITION`（`params.state` 为 `ARCHIVED`/`NOT_ARCHIVED`，对齐 `NavigationViewState.Notice.ALREADY_ARCHIVED`/`NOT_ARCHIVED`）；对根分支执行 → `400 BRANCH_NOT_ARCHIVABLE`。

**`POST /api/branches/{id}/collapse`** —— 折叠/展开（视图状态，不改未读序列）。
- 请求：`{ "commandId", "collapsed": true|false }`。
- 响应 `200`：`Branch`。

**`POST /api/branches/{id}/seen`** —— 标记已见序列（最新未读回答真正进入视口才清未读）。
- 请求：`{ "commandId", "seenThroughSequence": 2 }`。
- 语义：只前进、不超过已提交值；回退或越界 → `409 SEQUENCE_OUT_OF_ORDER`（`Branch.markSeenThrough`、`ViewportSeen.onViewport`）。
- 响应 `200`：`Branch`。

**`PUT /api/branches/{id}/reading-position`** —— 保存阅读位置（语义锚点，非像素绝对偏移）。
- 请求：`{ "commandId", "messageId", "revisionId"?, "listItemOffset", "viewportOffset" }`（`ReadingAnchor`：`revisionId` 为 null 表示“跟随当前位置显示的版本”）。
- 响应 `200`：`{ "branchId", "readingAnchor": ReadingAnchor }`。

**`GET /api/branches/{id}/deletion-preview`** —— 子树删除预览。
- 响应 `200`：`DeletionPreview`（`wholeTopic=false`）；范围不可计算 `409`。

**`POST /api/branches/{id}/delete`** —— 确认删除子树。
- 请求：`{ "commandId", "digest" }`；`digest` 过期 → `409 DELETION_PLAN_EXPIRED`。
- 响应 `200`：`{ "deletedBranchIds": ["uuid", ...] }`；根分支删除等价整树删除（`wholeTopic=true` 预览）。

### 6.4 生成操作

**`GET /api/operations/{id}`** —— 操作快照（state/部分内容/错误）。
- 响应 `200`：`Operation`；不存在 `404 NOT_FOUND`。

**`GET /api/operations/active?branchId={id}`** —— 恢复用的活跃操作。
- 响应 `200`：`Operation` 或 `null`（该分支无活跃操作）。语义：重启/刷新后权威来源是持久行，不依赖进程内缓存（`GenerationCoordinator.recover`、`GenerationOperationDao.findActiveByBranch`）。

**`POST /api/operations/{id}/cancel`** —— 取消。
- 请求：`{ "commandId" }`。
- 语义：先中止调用再落终态；已收到部分内容保留；终态不再被晚到回调覆盖（`GenerationCoordinator.cancel`/`settle`）。
- 响应 `200`：`Operation`（`state=CANCELED`）。

**`GET /api/operations/{id}/events`** —— SSE 流（见第 7 节）。

**`POST /api/messages/{id}/retry`** —— 原位重试（同一位置新尝试，不改 `ordinal`，不新增重复问答）。
- 请求：`{ "commandId", "model", "thinkingEnabled" }`；沿用既有位置与版本规则（`PositionCommands.retryRefusal`；`AnswerVersions.retry`）。
- 响应 `200`：`{ "message": Message, "operation": Operation }`。
- 错误：`OPERATION_NOT_COMPLETABLE`（位置不存在）、`MESSAGE_ROLE_MISMATCH`（USER 位置）、`ILLEGAL_STATE_TRANSITION`（已有尝试在飞）。

**`POST /api/messages/{id}/reset`** —— 重置（重新生成当前显示的版本）。
- 请求：`{ "commandId", "model", "thinkingEnabled" }`。
- 语义要点（对照 `AnswerVersions.reset` + `ChatCommands.reset`）：reset 是**对同一位置发起新尝试**；要求该位置**已显示稳定版本**（否则 `409 OPERATION_NOT_STABLE`）；旧版本保留、可追溯，不覆盖；不改 `ordinal`、不多插问答。
- 响应 `200`：`{ "message": Message, "operation": Operation }`。

**`POST /api/messages/{id}/revisions/{revisionId}/select`** —— 切换显示的版本（仅移动显示指针）。
- 请求：`{ "commandId" }`。
- 语义：只改显示指针；子分支的出生版本不受影响（`BranchSource.sourceRevisionId` 不被级联）；重复选择当前版本 → `409 REVISION_IMMUTABLE`；版本不属于该位置 → `404 OPERATION_NOT_COMPLETABLE`（`AnswerVersions.selectRevision`）。
- 响应 `200`：`AnswerRevision`。

### 6.5 派生任务

派生任务复用 operation + SSE 机制，以 `promptPurpose` 区分，结果落 `ai_artifacts`（按 kind）。派生失败**保留旧结果**，绝不回落到教学提示词（`DerivedOperation.run`）。

**`POST /api/derived`** —— 启动一个派生操作。
- 请求：`{ "commandId", "kind", "scopeId", "basedOnRevisionId"?, "model"? }`
  - **请求 `kind` 词汇表（大写枚举，仅请求侧）**：`ROOT_TITLE`/`BRANCH_TITLE`/`BRANCH_SUMMARY`/`ANSWER_RECOMMENDATIONS`/`HOME_RECOMMENDATIONS`/`CONTEXT_COMPRESSION`/`MESSAGE_TRANSLATION`。它不同于落库 `ai_artifacts.kind` 的 wireId（小写、见 5.10）：`MESSAGE_TRANSLATION` 只存在于请求（翻译不落库），其余六项与 5.10 表格一一对应但大小写不同。
  - `scopeId`：`BRANCH` 作用域为 branchId，`TOPIC` 作用域为 topicId（`ArtifactKind.Scope`）。
  - `MESSAGE_TRANSLATION` 以 messageId 作为 `scopeId`。
- 响应 `200`：`Operation`（`promptPurpose` 对应 kind 的 purpose）。客户端打开其 `sseUrl`。
- 错误：`OPERATION_NOT_STABLE`（如翻译要求稳定版本）、`CONTEXT_EXCEEDS_BUDGET`、`NO_MODELS`、`KEY_MISSING`。

**`GET /api/derived?kind={kind}&scopeId={id}`** —— 读取已落库的派生结果。
- 响应 `200`：`DerivedArtifact`（`stale=true` 表示 `basedOnRevisionId`/`inputDigest` 与实时基线不符，前端作“无结果”处理）；不存在 `404 NOT_FOUND`。
- 说明：推荐/摘要/标题也随 6.3 的读端点以内嵌投影返回，客户端无需二次请求；本端点用于显式读取翻译或刷新。

### 6.6 配置：Provider / 模型 / 搜索

**`GET /api/providers`** —— 提供商列表。响应 `200`：`[Provider, ...]`。
**`POST /api/providers`** —— 新增。请求 `{ "commandId", "name", "baseUrl", "apiKey"? }`；响应 `200`：`Provider`。
**`PUT /api/providers/{id}`** —— 编辑（名/baseUrl/启停）。请求 `{ "commandId", "name"?, "baseUrl"?, "enabled"? }`；响应 `200`：`Provider`。
  - 校验：name 非空、baseUrl 非空且 `https://` 开头（`ProviderAdminPresenter.onSavePressed`）；name 重复 → `409 PROVIDER_NAME_TAKEN`。
  - 删除最后一个已启用提供商时必须给出警示（`ProviderAdminPresenter.onDeletePressed` 的 `lastEnabled` 判定 → `params` 外由前端提示；服务端不阻断）。
**`DELETE /api/providers/{id}`** —— 删除。请求 `{ "commandId" }`；响应 `200`：`{ "deletedProviderId" }`。
**`GET /api/providers/{id}/models`** —— 模型列表。响应 `200`：`[Model, ...]`。
**`POST /api/providers/{id}/models`** —— 刷新模型（**只增不删、精确去重**）。请求 `{ "commandId" }`；响应 `200`：`{ "added": 2, "models": [Model, ...] }`；失败 → `503 PROVIDER_UNAVAILABLE`。
**`PUT /api/providers/{id}/key`** —— 写 Key（只写不读）。请求 `{ "commandId", "apiKey" }`；空 Key → `400 KEY_REQUIRED`（`scope=PROVIDER`）；保存失败 → `500 KEY_SAVE_FAILED`。响应 `200`：`Provider`（只含 `hasKey`）。
**`DELETE /api/providers/{id}/key`** —— 删 Key。响应 `200`：`Provider`。

**`GET /api/settings/model-roles`** —— 见 5.12。响应 `200`：`ModelRoles`。
**`PUT /api/settings/model-roles`** —— 更新分工/预算/建议开关。请求 `{ "commandId", "roles"?, "budget"?, "suggestions"? }`；响应 `200`：`ModelRoles`。
**`GET /api/settings/model-resolution`** —— 回落解析（含回落说明）。响应 `200`：`ModelResolution`。

**`GET /api/settings/web-search`** —— 见 5.11。响应 `200`：`WebSearchSettings`。
**`PUT /api/settings/web-search`** —— 更新开关/引擎/档位。请求 `{ "commandId", "searchEnabled"?, "engine"?, "tier"? }`；启用但无 Key → 响应保留 `notice=NOKEY`（不报错，前端提示引导）。响应 `200`：`WebSearchSettings`。
**`PUT /api/settings/web-search/keys/{engine}`** —— 写引擎 Key。请求 `{ "commandId", "apiKey" }`；空 → `400 KEY_REQUIRED`（`scope=WEB_SEARCH`）。响应 `200`：`WebSearchSettings`。
**`DELETE /api/settings/web-search/keys/{engine}`** —— 删引擎 Key。响应 `200`：`WebSearchSettings`。

### 6.7 应用内搜索

**`GET /api/search?q={text}`** —— 主题/分支/消息命中 + 可定位信息 + 命中区间。
- 响应 `200`：`SearchResult`。空 `q` → `200` 且 `hits=[]`（不扫全表）。`q` 长度上限对应用户输入不设硬切（服务端按 `SearchQuery` 折叠大小写与空白）。

### 6.8 统计

**`GET /api/stats?timeZone={IANA}&month={YYYY-MM}`** —— 累计提问/消息/连续学习 + 当月热力。
- 语义：服务端按传入设备时区计算自然日与连续段；空月返回零值结构；总量与明细同源（`StatsAggregator`）。
- 响应 `200`：`Stats`。

### 6.9 附件/文件

**`POST /api/attachments`** —— 上传（内容寻址去重）。
- 请求：`multipart/form-data`（`file` 部件）+ `commandId` 表单字段，或 `{ "commandId", "displayName", "mimeType", "contentBase64" }`（二者择一，见 README 约定；本规范以 JSON 形式为规范样例）。
- 语义：SHA-256 内容哈希去重，同名不同内容各存一份，同内容重复上传返回已存在记录（`AttachmentStore.register`）。
- 响应 `200`：`Attachment`。错误：`ATTACHMENT_TOO_LARGE`、`ATTACHMENT_TYPE_UNSUPPORTED`。

**`GET /api/attachments`** —— 记录列表（按最新在前，含物理/引用事实，`AttachmentStore.list`）。响应 `200`：`{ "attachments": [Attachment, ...], "usageBytes": 12345 }`。
**`GET /api/attachments/{id}/content`** —— 文件内容。响应 `200`：原始字节（`Content-Type` 为记录 mimeType）；物理文件缺失 → `404 NOT_FOUND`。
**`DELETE /api/attachments/{id}`** —— 删除记录与文件；**引用保留缺失标记**（不改成“有引用就禁止删除”）。请求 `{ "commandId" }`；响应 `200`：`{ "deletedAttachmentId" }`。
**`POST /api/files/cleanup`** —— 孤儿清理（幂等）。请求 `{ "commandId" }`；语义：清理孤儿候选=无消息引用的记录；重跑安全，竞态不得误删新出现的有效引用（`AttachmentStore.orphans`/`cleanupOrphans`）。响应 `200`：`{ "cleaned": 3 }`。

### 6.10 批注

**`GET /api/messages/{id}/annotations`** —— 读取时按 digest 清理失效记录（内容变化即清理，不自动重定位）。
- 响应 `200`：`{ "messageId", "annotations": [Annotation, ...] }`。digest 不匹配的记录在读取时被清理（`AnnotationDao.listFor`），不得挪到相似文字上“保住批注”。

**`POST /api/messages/{id}/annotations`** —— 写入批注。
- 请求：`{ "commandId", "kind", "container", "proseIndex"?, "tableIndex"?, "rowIndex"?, "colIndex"?, "start", "end", "body"? }`
- 语义：HIGHLIGHT 用共享领域规则服务端合并（相交/相邻合并为并集，absorbed 行删除）；NOTE 正文必须非空；区间半开、UTF-16 编码单位（`Annotation`、`Highlighter.plan`）。
- 响应 `200`：`{ "annotations": [Annotation, ...] }`（写后该消息的当前批注集）。错误：空 NOTE 正文 → `400 NOTE_BODY_REQUIRED`（`params.field=body`）；区间畸形 → `400 ANCHOR_INVALID`。

**`DELETE /api/annotations/{id}`** —— 删除批注。请求 `{ "commandId" }`；响应 `200`：`{ "deletedAnnotationId" }`；幂等（不存在视为成功）。

---

## 7. SSE 协议

生成操作与派生操作的流式结果通过 SSE 推送。

### 7.1 帧格式

- `Content-Type: text/event-stream; charset=UTF-8`。
- 每个事件三行：
  - `id: <seq>` —— 同操作内**严格递增**的正整数序列号；
  - `event: <type>`；
  - `data: <json>` —— 单行 JSON 对象，至少含 `operationId` 与 `seq`。
- 心跳：每 15 秒发送注释行 `: keepalive`（注释行即以 `:` 开头的行，无 `id`、无 `event`），保持连接与代理存活。
- 终态事件发出后，服务端**关闭流**。

### 7.2 事件类型

| `event` | 含义 | `data` 关键字段 |
|---|---|---|
| `snapshot` | 订阅首帧，或重连缺口后先补快照 | `operationId`、`seq`、`state`、`reasoningContent`（已收全文）、`answerContent`（已收全文） |
| `reasoning_delta` | 推理增量 | `operationId`、`seq`、`delta` |
| `answer_delta` | 回答增量 | `operationId`、`seq`、`delta` |
| `state` | 状态迁移（如 STAGED→SUBMITTING→STREAMING_*） | `operationId`、`seq`、`state` |
| `stable` | 正常终止且回答非空，已写稳定版本 | `operationId`、`seq`、`revision`（`AnswerRevision`） |
| `failed` | 以映射错误终止 | `operationId`、`seq`、`errorCode`、`params` |
| `interrupted` | 进程被杀/连接断开 | `operationId`、`seq` |
| `canceled` | 用户/删除取消 | `operationId`、`seq` |

### 7.3 顺序与恢复

- 正常流顺序：`snapshot` → （`reasoning_delta`）* → （`answer_delta`）* → `state`*（穿插）→ `stable`。`reasoning_delta` 与 `answer_delta` 不保证成帧绑定（`GenerationOperation` §9.3：回答流中仍可能到达推理增量）。
- 失败/中断/取消：`snapshot`（可选）→ 增量 → 相应终态事件之一（`failed`/`interrupted`/`canceled`）。终态只确认一次。
- **Last-Event-ID**：重连时浏览器携带 `Last-Event-ID: <seq>` 请求头。若服务端仍可从该 `seq` 之后连续补发，则直接续发；若出现**缺口**（`seq` 不连续、缓冲区已淘汰、或该 `seq` 不属于本操作），服务端**先发 `snapshot`**（补偿当前完整部分内容），再继续。
- **服务端事件缓冲容量 512 帧/操作**，超出按缺口语义处理（订阅者将先收到 `snapshot`）。
- **客户端处理规则**：
  1. 按 `operationId` 过滤：不同操作的晚到事件必须忽略（不得写入当前操作）。
  2. 按 `seq` 去重：`seq` 不大于已处理最大值的事件忽略（重复/乱序）。
  3. `snapshot` 用作部分内容/状态的权威刷新（覆盖本地累积），再应用其后增量。
  4. 收到任一终态事件后停止累积并关闭连接。

### 7.4 竞态与过期语义

- 单分支同一时刻至多一个活跃操作；对已有活跃操作的分支发起新生成 → `409 BRANCH_BUSY`。
- 取消/完成竞态：取消后到达的旧 delta/complete 回调不得覆盖终态（`GenerationOperation` 终态拒绝；`GenerationCoordinator.stale`）。
- 晚到/重复事件：见 7.3 客户端规则；服务端亦保证终态唯一。
- 删除计划 digest 过期：预览与确认之间的树变化使 digest 不匹配 → `409 DELETION_PLAN_EXPIRED`，不得半删（`NavigationData.delete` 单事务）。

---

## 8. 语义冻结清单

以下为不可协商的语义，双端与 fixtures 必须一致：

1. 正常终止**且**回答非空才产生稳定版本并进入 `STABLE`；回答为空不得稳定（`GenerationOperation.stabilize`、`OPERATION_NOT_COMPLETABLE`）。
2. 失败/中断/取消是**不同**终态，分别对应 `failed`/`interrupted`/`canceled`，不合并、不互相覆盖。
3. 部分回答**不可分叉**（`BranchSource.fromOperation` 要求 STABLE → `OPERATION_NOT_STABLE`）。
4. 稳定版本**不可变**；修改稳定版本被拒（`REVISION_IMMUTABLE`）。
5. 快照（birth snapshot）**不可变**；子分支出生版本不因父级 reset 迁移（`BranchSource.sourceRevisionId` 无级联）。
6. 单分支同一时刻**至多一个活跃操作**（`BRANCH_BUSY` / 409）。
7. 取消后**晚到回调不得覆盖终态**。
8. 未读由序列差推导，无独立未读布尔；只在最新未读回答真正进入视口后清除（`Branch.unreadCount`、`ViewportSeen`）。
9. 归档不等于删除、不等于取消生成；根分支不可归档（`Branch.archive`、`NavigationPresenter`）。
10. 删除按真实取消/删除顺序、单事务执行；范围由树快照计算（`NavigationData.cancelInFlight` 先 → `delete` 后）。
11. 幂等：同 `commandId` 重放不产生第二条消息或第二次模型调用（第 4 节）。
12. `requestId`（传输）与 `commandId`（业务提交）分离。
13. 真实时间点一律 UTC `Millis`；date-only 不加时区偏移（第 2 节）。

---

## 9. 语音边界

- **无后端端点**：语音输入不经过本服务。识别由**浏览器能力**（如 `SpeechRecognition`/`webkitSpeechRecognition`）完成；无对应端点。
- 能力探测与降级：
  - 无识别能力 → 不渲染语音键（无占位、无死按钮），对齐 `SpeechInputPolicy.keyVisible(recognitionAvailable)`。
  - 权限被拒 → 键置灰；首次点击给出**一次**“前往设置”的引导，之后不再重复弹窗（`SpeechInputPolicy.guidanceOnTap` 只返回一次 true）。
  - 权限后续恢复 → 无需重启，键重新可用（`markPermissionGranted`）。
- 识别结果**追加草稿**，不自动发送、不覆盖用户已有文本（`ChatActions.onSpeechRecognized`）。
- 网络归属：浏览器识别可能依赖厂商在线服务，与本地模型调用无关；前端需明确告知（`SpeechInputPolicy`/SPEC-081 语音识别输入节）。
- 朗读（TTS）：同样为浏览器能力（`speechSynthesis`），无后端端点；切分支/离开时停止当前朗读。

---

## 10. 静态托管说明

- 发行阶段由后端**同源**托管前端静态产物（`web/frontend/dist`），浏览器经 `http://127.0.0.1:8733` 访问；`/api/**` 为接口命名空间，其余路径回退到前端入口（SPA history fallback）。
- 开发阶段（Vite dev server，`127.0.0.1:5173`）通过代理把 `/api` 转发到 `127.0.0.1:8733`（`web/README.md`）。
- 无论开发或发行，前端与后端**同源**：契约不引入 CORS 或跨站凭据；SSE 使用同源的认证流客户端（前端只用统一 SSE 客户端，不使用原生 `EventSource`）。
- 数据目录与附件物理位置为桌面本地路径（默认 `%LOCALAPPDATA%\TreeLearn-Web`，`web/README.md`），不属于本 HTTP 契约的一部分。

---

## 11. 端点索引

| 分组 | 方法与路径 | 说明 |
|---|---|---|
| 健康 | `GET /api/health/ready` | 冻结现状 |
| 主题 | `GET /api/topics` | 概览列表（派生显示名 `displayTitle`） |
| 主题 | `POST /api/topics` | 首问原子接受 |
| 主题 | `GET /api/topics/{id}` | 详情 |
| 主题 | `GET /api/topics/{id}/deletion-preview` | 删除预览 + digest |
| 主题 | `POST /api/topics/{id}/delete` | 确认删除 |
| 分支 | `GET /api/topics/{id}/branches` | 树 + 折叠/未读/阅读/归档 |
| 分支 | `GET /api/branches/{id}/messages` | 消息 + 当前版本 + 附件 |
| 分支 | `POST /api/branches/{id}/messages` | 连续追问 |
| 分支 | `POST /api/branches/{id}/forks` | 整条/选文分叉 |
| 分支 | `POST /api/branches/{id}/archive` | 归档（根拒绝） |
| 分支 | `POST /api/branches/{id}/restore` | 恢复 |
| 分支 | `POST /api/branches/{id}/collapse` | 折叠/展开 |
| 分支 | `POST /api/branches/{id}/seen` | 已见序列 |
| 分支 | `PUT /api/branches/{id}/reading-position` | 阅读位置 |
| 分支 | `GET /api/branches/{id}/deletion-preview` | 子树删除预览 |
| 分支 | `POST /api/branches/{id}/delete` | 确认删除子树 |
| 生成 | `GET /api/operations/{id}` | 操作快照 |
| 生成 | `GET /api/operations/active?branchId=` | 恢复活跃操作 |
| 生成 | `POST /api/operations/{id}/cancel` | 取消 |
| 生成 | `GET /api/operations/{id}/events` | SSE |
| 生成 | `POST /api/messages/{id}/retry` | 原位重试 |
| 生成 | `POST /api/messages/{id}/reset` | 重置新版本 |
| 生成 | `POST /api/messages/{id}/revisions/{revisionId}/select` | 切换显示版本 |
| 派生 | `POST /api/derived` | 启动派生操作 |
| 派生 | `GET /api/derived?kind=&scopeId=` | 读取派生结果 |
| 配置 | `GET/POST /api/providers` | 列表/新增 |
| 配置 | `PUT/DELETE /api/providers/{id}` | 编辑/删除 |
| 配置 | `GET/POST /api/providers/{id}/models` | 模型列表/刷新（只增不删） |
| 配置 | `PUT/DELETE /api/providers/{id}/key` | Key 只写不读 |
| 配置 | `GET/PUT /api/settings/model-roles` | 分工/预算/建议 |
| 配置 | `GET /api/settings/model-resolution` | 回落解析 |
| 配置 | `GET/PUT /api/settings/web-search` | 联网搜索设置 |
| 配置 | `PUT/DELETE /api/settings/web-search/keys/{engine}` | 引擎 Key |
| 搜索 | `GET /api/search?q=` | 应用内搜索 |
| 统计 | `GET /api/stats?timeZone=&month=` | 学习统计 |
| 附件 | `POST /api/attachments` | 上传去重 |
| 附件 | `GET /api/attachments` | 列表 |
| 附件 | `GET /api/attachments/{id}/content` | 内容 |
| 附件 | `DELETE /api/attachments/{id}` | 删除（引用保留缺失） |
| 附件 | `POST /api/files/cleanup` | 孤儿清理（幂等） |
| 批注 | `GET /api/messages/{id}/annotations` | 读取（digest 清理） |
| 批注 | `POST /api/messages/{id}/annotations` | 写入（服务端合并） |
| 批注 | `DELETE /api/annotations/{id}` | 删除 |
| 通用 | `ANY /api/**`（未声明路径） | 兜底 `404 NOT_FOUND`（`resource=endpoint`） |

---

## 12. 对照来源

本契约的端点/DTO/错误/状态语义来自以下**活动生产来源**。字段与错误码不得凭空命名。

### 12.1 共享领域模型（DTO 字段词汇）

`web/shared/domain/src/main/java/com/treelearn/core/domain/`：

- `Topic.java` → 5.1 Topic（无自身名称；`lastVisitedBranchId`/`createdAt`/`updatedAt`）。
- `Branch.java` → 5.2 Branch（`latestStableSequence`/`lastSeenStableSequence`/`unreadCount()`/`collapsed`/`archivedAt`/`isRoot()`）。
- `BranchSource.java`、`BranchAnchor.java` → 5.2 `source`/`anchor`（`fromOperation` 要求 STABLE；`ofRange` 半开区间 + quote/hash 回退）。
- `Message.java` → 5.4 Message（位置而非版本；`ordinal` 不因 retry/reset 改；USER 的 `quotedText` 空串表示无引用；`activeRevisionId`/`activeOperationId`）。
- `MessageRole.java` → `USER`/`AI`。
- `AnswerRevision.java` → 5.5（`revisionNo`≥1；`promptPolicyVersion` 保持；`answerContent` 非空）。
- `GenerationOperation.java`、`GenerationState.java` → 5.6 / 第 7 节（状态枚举、终态拒绝晚到回调、`STABLE` 只经 `stabilize`）。
- `GenerationCommand.java` → 操作由命令固定 prompt 策略版本（`commandId`/`branchId`/`messageId`/`purpose`/`policyVersion`/`model`/`thinkingEnabled`）。
- `GenerationModel.java` → 5.5 `model`（`deepseek-flash`/`deepseek-v4-pro`）。
- `PromptPurpose.java`、`PromptSpec.java` → 5.10 purpose 与策略版本命名空间。
- `Annotation.java`（含 `Container`/`Kind`/`Locator`/`Span`/`mergeHighlights`）→ 5.8、6.10。
- `UnreadRollup.java`、`ViewportSeen.java` → 5.2 未读汇总与清除规则。
- `ReadingAnchor.java`、`ReadingPosition.java` → 5.4/6.3 阅读位置。
- `SearchHit.java`、`SearchQuery.java` → 5.13 搜索（字段枚举、`DISPLAY_ORDER`、空查询禁止 `%%`）。
- `DomainErrorCode.java` → 3.3 领域状态类错误码。
- `ContextSnapshot.java`/`ContextSnapshotCodec.java`/`BuildContextPlanUseCase.java` → 3.3 `SNAPSHOT_*`/`CONTEXT_*`（fail-closed）。
- `Guard.java` → 参数形态 vs 业务拒绝的错误通道区分（影响 400/409/500 归类）。

### 12.2 生成与错误分类（端点语义）

- `android/ai/src/main/java/com/treelearn/ai/gateway/AiGatewayError.java` → 3.3 上游/供应商类错误码与 HTTP 映射（`Kind`、`forHttpStatus`、`keyMissing`、`networkInterrupted`、`parseFailed`、`inBandProviderError`）。
- `android/app/src/main/java/com/treelearn/app/generation/GenerationCoordinator.java` → 6.4 提交/取消/恢复；`Refusal`（`BRANCH_BUSY`/`PROMPT_BUILD`/`VERSION_MISMATCH`/`STORAGE`）；终态落定与晚到事件丢弃。
- `GenerationScheduler.java`、`LiveAttempts.java`、`LiveAttempt.java` → 单分支活跃操作约束与调度（`BRANCH_BUSY`、两槽上限）。
- `PositionCommands.java` → 6.4 retry/reset/select 的拒绝分类与错误码映射（`retryRefusal`/`resetRefusal`/`selectRefusal`/`codeFor`）。
- `AnswerVersions.java` → reset=同一位置新尝试、要求已显示稳定版本、旧版本保留。
- `RecoveryScan.java` → 6.4 `GET /api/operations/active` 的恢复目标（STAGED→CANCELED，其余→INTERRUPTED）。
- `MessageTranslator.java`、`QuickModelGateway.java` → 翻译派生（`MESSAGE_TRANSLATION`）。

### 12.3 命令/UI 语义

- `android/app/src/main/java/com/treelearn/app/ui/ChatCommands.java` → 6.3/6.4（`send`/`retry`/`reset`/`selectRevision`/`fork`/`versions`/`applyHighlight`）。
- `ChatActions.java` → 6.3 停止/草稿/选文/引用/翻译/朗读/分叉/高亮/语音语义。
- `ChatPresenter.java` → 分支切换、草稿保护、来源回溯、阅读位置恢复。
- `Highlighter.java`、`ChatCommands.applyHighlight` → 6.10 HIGHLIGHT 合并/取消（`Kind.CANCEL`/`WRITE`）。
- `TreeCanvasCommands.java`、`TreeCanvasPresenter.java` → 6.3 树导航/折叠（视图状态，不改未读）。
- `NavigationPresenter.java`、`NavigationData.java` → 6.2/6.3 归档/删除（`plan`/`cancelInFlight`/`delete` 顺序、范围由树快照计算、fail-closed）。
- `LibraryPresenter.java`、`LibraryData.java` → 删除计划与归档区的共用口径。
- `NavigationViewState.java` → 6.2/6.3 归档/删除的拒绝与状态类别（`Notice.SCOPE_UNRESOLVABLE`/`BRANCH_GONE`/`ALREADY_ARCHIVED`/`NOT_ARCHIVED`/`STORE_UNREADABLE`）。
- `FileManagerPresenter.java` → 6.9 文件删除/孤儿清理（confirm-first、`orphanIds`、`cleanupOrphans` 幂等）。
- `StatsDetailPresenter.java` → 6.8 统计展示口径。
- `ProviderAdminPresenter.java` → 6.6 提供商增改删/Key/模型刷新（`Notice` 与 `lastEnabled` 警示）。
- `ModelSettingsPresenter.java` → 6.6 分工/预算/建议/回落（`Role`/`Budget`/`Fallback`/`resolve`）。
- `SearchSettingsPresenter.java` → 6.6 联网搜索（`Engine`/`Tier`/`Notice`）。
- `SpeechInputPolicy.java` → 第 9 节 语音能力探测与降级（`keyVisible`/`guidanceOnTap`）。
- `HomeViewState.java` → 3.3 用户可见类别映射（`Notice` 与 `from(AiGatewayError.Kind)`、`from(FirstTopicCreation.Refusal)`）。
- `HomeStats.java` → 5.14/6.8 热力格与连续天数。

### 12.4 数据/用例

- `SqliteChatStore.java` → 6.3 消息读取/历史重建/附件块/批注 digest。
- `ChatStore.java` → 读/写端口形状（`Conversation`/`Position`/`SnapshotUnreadable`）。
- `android/app/src/main/java/com/treelearn/app/wiring/SnapshotHistoryMerger.java` → 6.3 `history` 对出生快照引用的逐行解析与 fail-closed（`CONTEXT_SOURCE_MISSING`/`SnapshotUnreadable`）。
- `android/data/src/main/java/com/treelearn/data/db/dao/MessageDao.java`、`AnswerRevisionDao.java` → 6.3/6.4 消息与版本读取（`findByBranch`/`findById`/`findByMessage`/`nextRevisionNo`）。
- `SqliteSearchStore.java`、`android/app/.../search/` → 6.7 搜索。
- `SqliteProviderAdminStore.java` → 6.6 提供商/Key（`KeySnapshot` 脱敏）。
- `SqliteReadingStore.java`、`ReadingStore.java` → 6.3 阅读位置/已读持久化。
- `android/app/.../artifact/DerivedService.java`、`DerivedOperation.java`、`ArtifactKind.java`、`ArtifactOutcome.java`、`DerivedRequest.java`、`DerivedResult.java` → 6.5/5.10 派生用途、scope、stale/CAS、失败保留旧值。
- `android/app/.../files/AttachmentStore.java`、`AttachmentTextExtractor.java` → 6.9 内容寻址去重、解析状态、上限（`MAX_SOURCE_BYTES`=20 MiB）、引用保留缺失标记、孤儿清理幂等。
- `android/data/src/main/java/com/treelearn/data/stats/StatsAggregator.java` → 6.8 自然日/连续段/空态口径。
- `android/data/src/main/java/com/treelearn/data/db/dao/FirstTopicCreation.java` → 6.2 首问五组行原子写入与拒绝分类（`Refusal`）。
- `android/data/src/main/java/com/treelearn/data/db/dao/BranchCreation.java` → 6.3 分叉三写单事务与 `Refusal`。
- `android/data/src/main/java/com/treelearn/data/db/dao/GenerationOperationDao.java`、`GenerationOperationRow.java` → 6.4 操作持久化、`findActiveByBranch`、`markTerminal(errorKind)`、`command_id` 幂等列。
- `android/app/src/main/java/com/treelearn/app/wiring/PromptLimits.java` → 3.3 `CONTEXT_EXCEEDS_BUDGET.params`（`capacityChars`=60000、`minAnswerChars`=512）。
- `android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java`（`listOverviews`）与 `web/backend/src/main/java/com/treelearn/web/storage/TopicStore.java`（`listOrderedByRecency` 摘要行）→ 5.1b `TopicSummary`：同一条 `LEFT JOIN` 根分支 + 首条 USER 消息回退的派生显示名（`displayTitle = rootTitle ?? rootQuestion`，`TopicStore.Summary.displayTitle()`）。Web 契约只携带桌面摘要行可产出的字段；Android `TopicDao.Overview` 另含 `resumeBranchId`/`summaryPayload`/`branchCount`/`unreadCount`，属 Android 主页自有数据路径，不在本 HTTP 契约内。

### 12.5 现状冻结与配置

- `web/backend/src/main/java/com/treelearn/web/api/ReadyController.java` → 6.1 就绪响应冻结。
- `web/backend/src/main/resources/application.properties` → 端口 `8733`（loopback）。
- `web/README.md` → 同源/loopback/无鉴权/数据目录。

> 对照说明：`DELETION_PLAN_EXPIRED` 与删除预览 `digest` 是 Web 契约为实现“预览-确认两步 + 过期 409”而引入的**传输语义**，其行为口径对齐 `NavigationData.plan` 的 fail-closed 计算与 `NavigationPresenter` 的确认顺序；Android 侧无同名常量，故在此显式标注为契约层新增、非源码枚举。同类：`SCOPE_UNRESOLVABLE`（对齐 `NavigationViewState.Notice.SCOPE_UNRESOLVABLE` 的类别名）、`BRANCH_NOT_ARCHIVABLE`（对齐矩阵「根分支不可归档」与 `Branch.isRoot()`）、`NOTE_BODY_REQUIRED`（对齐 `Annotation.note` 的空正文拒绝）为在 §3.3 正式登记并配 fixtures 的契约层码。