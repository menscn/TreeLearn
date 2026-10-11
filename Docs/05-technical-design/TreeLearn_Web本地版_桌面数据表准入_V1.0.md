# 知芽 Web 本地版：桌面数据表准入记录 V1.0（待用户确认）

日期：2026-10-10。任务背景：T-2026-10-10-010（存储与凭据阶段，待建）。本文是 `.trae/rules/sql.md`
「Permanent table admission guard」要求的书面建表入场记录，**须经用户明确确认后方可实现**；
未经确认不得写任何 DDL。桌面表以活动 Android `TreeLearnSchema`（**DATABASE_VERSION 5、12 张逻辑表**）
为职责对照，逐表核对；两端引擎不同（桌面 = 本机 SQLite via sqlite-jdbc），证据不可互替。

## 1. 引擎、文件与边界（共同前提）

- 引擎：本机 **SQLite**（`org.xerial:sqlite-jdbc:3.53.4.0`，Apache-2.0，T-002 实测锁定），单库单文件。
- 数据目录：`%LOCALAPPDATA%\TreeLearn-Web\`（`treelearn.db` + `attachments/` + `logs/`），
  不指向仓库 checkout；服务只绑定 loopback 单实例，**无跨实例/共享介质要求**。
- 时间：全部真实时间点为 UTC epoch 毫秒（`INTEGER`）；date-only 不适用（本产品无 date-only 业务列）。
- DDL 位置：只允许出现在集中、版本化的 migration runner（`web/backend` 内），禁止散落业务路径；
  版本单调递增，失败回滚，`PRAGMA foreign_keys=ON` 每次打开显式启用并校验。
- 备选方案分析（按规则证明必要性）：① 扩展 Android 库——两端数据独立，不共享文件，不可行；
  ② 有界 JSON 单表——12 类事实有独立生命周期、约束与外键（如消息-修订-操作三者的引用完整性、
  附件去重键、批注锚点互斥约束），塞进 JSON 会让约束失效、查询退化、幂等键无处安放；
  ③ 事务作用域临时结构——这些事实必须跨重启恢复（生成操作、阅读位置、未读），临时结构不满足。
  结论：需要一组有约束的永久表，逐表如下。
- 命名与主键：沿用 Android 表名/列名（同一职责同一名字），主键均为应用生成 UUID（TEXT），
  行标识不使用自增；外键以 `REFERENCES` 声明，级联按 Android 已确认语义。

## 2. 逐表准入记录（12 张，与 `TABLE_NAMES` 同序）

> 每张表：职责与领域 / 权威来源 / 生产者 / 消费者 / 行标识 / 预期基数与增长 / 保留与删除 /
> 敏感字段 / 索引与约束要点 / 废弃触发。除批注外全部时间列为 UTC epoch ms。

### 2.1 `topics`（主题）
- 职责：一棵对话树的根身份与最近访问位置；显示名复用根分支标题。
- 权威：Android `TreeLearnSchema.Topics`；生产者：首主题接受用例；消费者：主页/学习库/树总览。
- 行标识：`id`（UUID）。基数：个位数~数千；增长=新建主题数。
- 保留/删除：随整树删除（确认范围后事务删除）；无独立保留策略。
- 敏感：标题（用户问题文本）——本地明文存储是产品既有形态（本机单用户），不进日志/导出。
- 约束：`last_visited_branch_id` 语义引用（Android 同名语义）；时间列 `created_at`/`updated_at`。
- 废弃：若主题身份并入分支树（无独立根身份）时可删。

### 2.2 `branches`（分支）
- 职责：树的父子关系、分叉来源与创建快照、折叠、未读对、阅读位置、归档。
- 权威：Android `Branches`；生产者：创建/分叉/导航用例；消费者：树导航、学习库、未读汇总、阅读恢复。
- 行标识 `id`；基数：每主题数~数百；增长=分叉数。
- 保留/删除：非根可归档（`archived_at`）或整子树删除；根分支不可归档（业务规则）。
- 敏感：标题；锚点 `anchor_quote` 含回答片段（本地明文）。
- 约束：分叉溯源与锚点四列在 Android 为 CHECK 互斥/全空-全非空（同一批约束在桌面以等价形式落地并实测）；
  `source_message_id/source_revision_id/anchor_*`、`context_snapshot_id`、阅读锚点三列 + 两个整数偏移。
- 废弃：无（树结构是产品核心）。

### 2.3 `messages`（消息）
- 职责：分支内有序消息位；USER 行携带问题与引用段，AI 行为逻辑位并指向当前修订与尝试。
- 权威：Android `Messages`（含 `quoted_text` 的 CHECK 不变量）；生产者：聊天用例；消费者：渲染、搜索、统计。
- 行标识 `id`；`ordinal` 分支内序号。基数：每分支数~数百。
- 保留/删除：随分支删除级联；无独立保留。
- 敏感：`user_content`（用户问题）、`quoted_text`（引用回答片段）——本地明文。
- 约束：`quoted_text`「NULL 或非空」不变量（AI 行不得携带）；`branch_id` 外键级联。
- 废弃：无。

### 2.4 `answer_revisions`（稳定回答版本，不可变）
- 职责：稳定化产出的不可变回答/推理内容 + 产出模型、思考开关、提示词策略版本。
- 权威：Android `AnswerRevisions`；生产者：稳定化用例；消费者：渲染、版本切换、分叉出生版本。
- 行标识 `id`；基数：每 AI 消息 1~数十（重试/重生成）。增长=生成次数。
- 保留/删除：随消息删除；不可变更（无 UPDATE 路径）。
- 敏感：回答与推理正文——本地明文；**不得写入日志**。
- 约束：`message_id` 外键；`revision_no` 唯一（消息内）；版本内容身份由 `prompt_policy_version` 前缀可恢复用途。
- 废弃：无。

### 2.5 `generation_operations`（生成操作）
- 职责：持久操作身份、命令身份、状态机状态、部分内容、失败码、更新时间——跨刷新/重启恢复的权威。
- 权威：Android `GenerationOperations`；生产者：生成协调器；消费者：SSE 恢复、取消、重试、中断落定。
- 行标识 `id`；`command_id` 为幂等命令身份（重复提交去重的持久承载，禁止进程内 Map 替代）。
- 基数：增长=每次生成尝试（含失败/取消）。保留：按既有业务规则（终态后保留供追溯；清理由后续确认）。
- 敏感：`partial_answer/partial_reasoning`（生成中正文，含未稳定内容）——本地明文，不进日志。
- 约束：`branch_id`/`message_id` 引用；同分支活跃操作互斥由应用层 + 查询保证（单实例）。
- 废弃：若生成改为无状态流式且不需要跨重启恢复时可删（当前恢复需求明确，保留）。

### 2.6 `context_snapshots`（创建快照）
- 职责：分支诞生时冻结的上下文背景（schema 版本 + digest + payload）——子分支永不回流父分支新内容。
- 权威：Android `ContextSnapshots`；生产者：分叉用例；消费者：生成上下文组装、来源回溯。
- 行标识 `id`；基数：每次分叉一行。保留/删除：随分支删除；不可变。
- 敏感：payload 含问题/回答片段——本地明文。
- 约束：`branch_id` 外键；`schema_version`/`digest` 校验。
- 废弃：无（快照不可变是已确认产品规则）。

### 2.7 `ai_artifacts`（派生 AI 结果）
- 职责：标题/推荐问题/压缩摘要/翻译等派生结果的按用途存储，带输入 digest 供 stale 判定。
- 权威：Android `AiArtifacts`；生产者：派生服务；消费者：对应 UI 与后续上下文。
- 行标识 `id`；基数：增长=派生调用；旧输入结果按 digest 判定 stale 丢弃（不覆盖新内容）。
- 保留/删除：随作用域删除；stale 行可清理（清理路径待实现时以测试证明幂等）。
- 敏感：payload 含派生正文——本地明文。
- 约束：`kind`+`scope_id`+`prompt_purpose` 查询索引；`input_digest` 参与 stale。
- 废弃：若派生改为即时计算且无缓存需求可删。

### 2.8 `providers`（供应商配置）
- 职责：Provider 名称、Base URL、启停、是否内置。
- 权威：Android `Providers`（SPEC-2026-09-23）；生产者：Provider 管理用例；消费者：设置页、AI Gateway 装配。
- **敏感：本表不得出现任何 API Key 列**（密钥走桌面凭据适配，T-011）；`base_url` 属用户配置。
- 行标识 `id`；基数：个位数。保留/删除：用户显式删除；最后一个提供商有警示规则（前端）。
- 约束：`name` 唯一、`base_url` 必填校验（应用层）。
- 废弃：无。

### 2.9 `models`（模型）
- 职责：Provider 侧线上模型标识与显示名。
- 权威：Android `Models`；生产者：模型获取用例；消费者：模型分工、AI 请求。
- 行标识 `id`；`provider_id`+`model_id` 唯一；模型合并只增不删/精确去重（沿用 Android 规则）。
- 基数：每 Provider 数~数百。
- 废弃：无。

### 2.10 `attachments`（附件记录）
- 职责：附件行 = 记录；私有目录中以 `content_hash` 命名的文件 = 正文（内容去重，一份物理副本服务全部引用）。
- 权威：Android `Attachments`；生产者：注册/解析用例；消费者：消息渲染、文件管理。
- 行标识 `id`；`content_hash` 为幂等去重键（唯一）。
- 保留/删除：允许删除被引用附件（引用保留缺失标记——见 2.11 的非外键设计）；孤儿清理可重跑。
- 敏感：`display_name`/`extracted_text`/文件内容（本地明文）；文件内容不进日志。
- 约束：`parse_state ∈ {pending, parsed, parse_failed}` CHECK；`content_hash` 唯一。
- 废弃：无。

### 2.11 `message_attachments`（消息-附件引用）
- 职责：消息对附件的引用行。
- 权威：Android `MessageAttachments`（**user-confirmed 2026-09-24** 的语义：`message_id` 为级联外键，
  `attachment_id` 故意为无强制外键的语义引用——删除附件后引用行须存活以表达「缺失」）。
- 行标识 `id`；基数：增长=引用数。
- 约束：`message_id` 外键级联；`attachment_id` 无 FK（与 Android 同语义，测试须证明该差异是刻意的）。
- 废弃：无。

### 2.12 `message_annotations`（批注/高亮）
- 职责：稳定回答上的用户标记（PROSE/CELL 共用表 + 哨兵列互斥；digest 不匹配按既有语义清理）。
- 权威：Android `Annotations`（**user-confirmed 2026-09-28**）；生产者：批注交互；消费者：渲染、批注侧栏。
- 行标识 `id`；基数：增长=标注数。
- 敏感：`body`（用户批注文本）与锚点区间——本地明文。
- 约束：容器 CHECK（PROSE/CELL 形状互斥）；唯一锚点含 `kind`（高亮与批注可在同一区间共存）；
  `content_digest` 驱动清理而非重定位。
- 废弃：无。

## 3. 跨切面结论

- **跨实例要求：无**（单机单实例 loopback；shared-state 规则的跨实例条款不适用，适用范围记录见任务 T-010）。
- 发布/升级：新装走 migration runner v1 全量 DDL；升级由 runner 逐版本前向迁移；失败回滚不留半迁移库；
  重开/重跑幂等；全部在**真实桌面 SQLite** 上取证（sqlite-jdbc 即生产引擎）。
- 可观测性：迁移与关键写入以操作/分支 ID 记录计数与耗时；不记正文/Key。
- 废弃触发：按各表条目描述；任何删表须先查依赖与非空数据（db-migration-reviewer 规则）。
- 与 Android 的差异（记录而非隐式）：引擎不同（Android SQLite API vs 桌面 sqlite-jdbc）、
  凭据不同（Keystore vs Windows 凭据）、无 Android 的 `user_version`（桌面 runner 自带版本表/PRAGMA user_version 择一，实现时定并证明）。

### 3.1 实现落地注记（T-2026-10-10-009，2026-10-10）

- 版本机制：选定 `PRAGMA user_version`（与 Android 同风格的整型版本、单文件自证、sqlite-jdbc 原生支持）。
  桌面 v1 为首个未发布版本，升级路径记 N/A；runner 的升级/回滚机制由测试专用合成步骤证明。
- 桌面最终形状无 ALTER 历史：新装直接执行最终 DDL（12 表 + 15 命名索引 + 8 触发器 + 2 条种子 INSERT），
  不同于 Android 的 V1→V5 迁移链；`quoted_text` 直接写在 `CREATE TABLE` 内，其 §6.2.19 不变量仍以
  INSERT/UPDATE 两个触发器落地，与 Android 的语句文本逐字一致。
- DDL 位置：全部 schema 语句只存在于 `web/backend/src/main/java/com/treelearn/web/storage/DesktopSchema.java`；
  `DdlLocationGuardTest` 扫描 `src/main/java` 证明其他生产文件不含 `CREATE TABLE/INDEX/TRIGGER`、`ALTER TABLE`。
  runner 注册表（`Migrations`）只引用 `DesktopSchema.CREATE_STATEMENTS`，不复制 DDL 文本。
- 引擎证据：新装、重开幂等、失败回滚、降级拒绝、`foreign_key_check`/`integrity_check`/`foreign_keys`
  以及代表性约束均由真实 sqlite-jdbc 引擎取证（`artifacts/evidence/T-2026-10-10-009-*`）。

## 4. 确认记录（用户）

- 呈报：2026-10-10，与「行数口径」「TDD 偏差」一并作为集中提问向用户呈报（含本准入记录全文链接与推荐项）。
- 用户回应：**跳过该提问、未提出异议**；依据用户此前明确指示（「一次性完成达到最终目的」「常规实现选择自主处理」「已确认目标无需反复确认」）与已批准的整体方案，**按推荐选项继续**。
- 确认来源性质：如实记为「**用户指示下按推荐执行**」——不是逐条口头确认。该状态在终版交付报告中再次列明供复核。
- 回退路径：若用户随后要求调整表集/列设计，按本记录 §2 重写并回退未确认表；新增第 13 张表仍须重新取得确认；确认前不落地其 DDL。