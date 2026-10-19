# 知芽（TreeLearn）Android SQLite 建表入场确认清单 V1.0

| 项目 | 内容 |
| :--- | :--- |
| 状态 | **待用户一次性确认**（确认后 T-004 方可开工） |
| 日期 | 2026-09-20 |
| 存储引擎 | Android 设备本地 SQLite（`SQLiteOpenHelper` + 显式 DAO） |
| 权威来源 | 技术方案 V1.0 §5（领域模型）、§6.1/§6.2（七张逻辑表与入场记录）、§10/§11（防旧写回、未读与阅读位置） |
| 规则依据 | `.codex/rules/sql.md`「Permanent table admission guard」与「建表审批边界」 |
| 表数量 | 11（V1 七张 + 083 两张 + 096 两张，见 §5；新增表仍须回到本清单确认） |

## 1. 备选方案分析（为什么不是单表 JSON）

- **扩展既有权威表**：本项目从零建立，不存在既有表，不适用。
- **有界 JSON 列**：`context_snapshots` 与 `ai_artifacts` 的 payload 确实是有界 JSON，但它们依附的**身份与引用关系**（branch_id 唯一、kind+scope_id、based_on_revision_id / input_digest 防旧写回）必须可约束、可索引、可事务校验。
- **全量单表 JSON**：主题树、对话顺序、稳定版本、生成状态、出生快照、派生物是**七类不可合并的权威事实**。塞进单表 JSON 会同时失去外键、唯一约束、事务级局部更新、可验证的前向迁移和局部索引，且无法保证「稳定版本不可变」「快照不可变」这类硬不变量。
- **view/CTE / 临时表**：不满足跨会话持久化与进程中断恢复要求。
- **结论**：七张永久表成立，各自承载单一权威事实；payload 内部仍用有界 JSON 并自带 schema version。

## 2. 七张表入场记录

| 表 | 权威事实（不可与它表重复） | 领域 / 生产者 → 消费者 | 行标识 | 预期基数与增长 | 保留与删除 | 敏感字段 | 关键索引与约束 | 废弃触发条件 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `topics` | 主题身份、最后访问分支、创建/更新时间 | `TopicRepository` → 首页、导航 | UUID PK | 每主题 1 行；随用户创建线性增长 | 删除主题时级联整题删除 | 本地标识，无用户内容 | UUID 主键；**不保存独立主题名**（根标题属 `ai_artifacts`） | 主题聚合被替代 |
| `branches` | 父子关系、标题、归档/折叠、来源版本、未读序号、语义阅读位置 | `BranchRepository` → 聊天、导航、画布 | UUID PK | 每分支 1 行；随主题增长 | 子树删除或主题删除级联 | 标题与锚点 quote 可能含用户内容 | `topic_id + parent_id`、`source_revision_id`、`archived_at` 索引；**每主题仅一个根**；`parentBranchId/sourceMessageId/sourceRevisionId/anchorStart/anchorEnd/anchorQuote/anchorHash/contextSnapshotId`；**锚点四字段同生同灭**——整条回答分叉时四者全空（此时 `anchorQuote`/`anchorHash` 亦为空），ranged 分叉时四者全非空且 `anchorStart < anchorEnd`（见 §4 待确认项） | 树模型被替代 |
| `messages` | 分支内有序的用户消息与 AI 逻辑位置 | `ConversationRepository` → 聊天、搜索、上下文 | UUID PK | 每轮约 2 行；随对话增长 | 随分支删除 | 用户原文；`quoted_text` 亦为用户内容 | `branch_id + ordinal` **唯一**；AI 行持有当前版本/当前操作引用；用户消息正文创建后不可变；`quoted_text`（V2 新增）承接 §6.2.19 引用追问所选原文，`NULL` 表示无引用，**只允许出现在非空 `USER` 行**——该不变式是触发器（`ALTER TABLE ADD COLUMN` 无法带 CHECK），见 §3 迁移 | 消息序列模型被替代 |
| `answer_revisions` | 成功稳定回答的**不可变**推理、最终正文与生成策略版本 | `GenerationRepository` → 聊天、来源、上下文、搜索、诊断 | UUID PK | 每次成功生成/重置新增一行；**被引用版本长期保留** | 随消息删除 | 推理与回答正文 | `message_id + revision_no` **唯一**；`prompt_policy_version` 非空；稳定序号单调递增；创建后不可变 | 版本事实迁移到等价不可变存储 |
| `generation_operations` | 提交/流式/失败/中断状态、部分推理/回答及本次生成策略版本 | `GenerationCoordinator` → 恢复、重试、诊断 | UUID 操作 ID | 活动及当前失败记录；成功后可清理无引用的旧失败尝试 | 随消息删除 | 部分推理/回答、错误分类 | **每分支最多一个活动操作**；每行 `prompt_policy_version` 非空；首次根问题在 2xx accepted 事务内随树原子写入；终态后不可再改写；SSE 按 80ms 或 64 字符批量落盘 | 生成状态由等价持久载体接管 |
| `context_snapshots` | 子分支**不可变**出生上下文清单 | `CreateBranchUseCase` → `ContextAssembler` | UUID PK | 每非根分支 1 行 | 随分支删除 | 上游内容引用（非正文副本） | `branch_id` **唯一**；`schema_version` 与 `digest`（SHA-256）必填；读取必须走结构化 JSON 解析 | 快照迁移到同等可验证格式 |
| `ai_artifacts` | 标题候选、分支摘要、要点、回答推荐、首页推荐及其提示词用途/版本 | 派生 AI 用例 → 首页、导航、推荐、诊断 | UUID PK | 每 `scope/kind` 保留当前有效结果与必要版本 | 随 scope 删除 | 标题、摘要、推荐文本 | `kind + scope_id` 索引；`prompt_purpose` / `prompt_policy_version` 非空；`based_on_revision_id` / `input_digest` 防旧结果覆盖（写回前事务内复核基线） | 派生物不再持久化或拆入专表 |

## 3. 跨表共同约定

- **时间**：所有真实时间点存 **UTC epoch millis `INTEGER`**，由应用显式写入；禁用 SQLite `CURRENT_TIMESTAMP` 与设备本地时区（`rules/sql.md`）。
- **ID**：全部使用应用生成的 **UUID 字符串**，不使用自增 ID；为未来导出/导入保留空间，但**首版不建同步字段、租约或服务端表**。
- **外键与事务**：所有表启用外键，每次打开显式 `PRAGMA foreign_keys=ON`；跨表写入必须在单个事务内完成。
- **迁移**：DDL 只允许位于生产 `SQLiteOpenHelper.onCreate/onUpgrade`（或集中版本化 migration runner）；`DATABASE_VERSION` 单调递增，已发布版本的迁移步骤不可改写，禁止运行时散落 DDL。**已知限制（2026-09-21）**：V1 在开发期被修正过两次（`branches` 的锚点 CHECK），而当时 `DATABASE_VERSION` 为 1、前向迁移表为空——因此这些修正**只对新建库生效**。现状（T-037 之后）：`DATABASE_VERSION` 已升为 **2**，前向迁移表注册了一条 **V1→V2**（新增 `messages.quoted_text` 与其不变式触发器，见 §2 `messages`），该步骤**不重建 `branches`**，所以上述锚点限制**仍未被任何迁移覆盖**；任何已用旧 V1 DDL 建过库的设备会永久保留旧约束。首发前仍须处置：清库，或为 `branches` 另写一条前向迁移（SQLite 需重建该表并复制数据）。**承接者：首个发行版的版本切割任务（`T-2026-09-20-security-release-core-hardening-018` 的发布检查项）**；该任务必须在本限制被处置或明确豁免并记录之前，不得宣告可发布。
- **不入库的敏感数据**：**API Key 不进入任何表**（也不进日志/Bundle/剪贴板），仅以 Android Keystore AES-GCM 密文存 SharedPreferences；模型与思考模式偏好可存普通 SharedPreferences。
- **提示词存储**：表中只保存稳定的 `prompt_purpose` / `prompt_policy_version`，不保存完整 system 提示词正文。正文由 `:ai` 的版本化注册表维护；主回答成功事务必须把 operation 的版本原样复制到 revision。
- **可观测性**：变更须记录 `PRAGMA user_version`、`integrity_check`、`foreign_key_check`、关键表/列/索引的直接验证结果（`rules/sql.md`「Android SQLite / SQLiteOpenHelper」段）。
- **验收路径**：新装走生产 `onCreate`；升级从每个仍受支持的已发布版本走生产 `onUpgrade`；另需覆盖关闭重开、事务回滚、级联删除、进程中断后的 `INTERRUPTED` 落定与手动重试。

## 4. 用户确认

- [ ] 七张表的**存在性、分工与不可合并性**已确认（§1、§2）。
- [ ] 跨表共同约定已确认（§3）。
- [x] 2026-09-21 新增的提示词用途/版本字段语义已由用户确认；不新增第八张表。
- [ ] **待用户确认的字段语义修正**（2026-09-21，由 T-004/T-022 实现期发现并已落库，尚未追认）：`branches.anchorHash` 由「非根分支必填」改为「仅 ranged 锚点必填，整条回答分叉时为空」。原约束与领域模型 `BranchAnchor.wholeAnswer()` 直接矛盾——`wholeAnswer()` 按设计产出四字段全空，旧 CHECK 使**每一条整条回答分叉都无法写入**。修正依据 `技术方案 §5.2`：「整条回答分叉时偏移为空，但版本引用仍必填」（原文只把版本引用列为必填）。补偿措施为锚点四字段同生同灭 CHECK，已在真机 API 36 上以「两类合法形态可写 + 六种半截/逆向形态被 `code 275` 拒绝」的用例收口。请确认该语义，或要求改回并另行给出整条回答分叉的写入方案。
- [ ] 确认人：__________　确认日期：__________

> 确认后本清单即为 T-004（SQLite schema / DAO / 事务）的入场记录权威来源；任何第八张表、字段语义变更或废弃，都须回到本清单重新确认并同步技术方案 §6。

## 5. 追补与新增入场记录（2026-09-24，T-2026-09-23-096 实现前增补）

> 本节回填 083 的文档欠账，并落 096 的两张新表入场记录。**§5.2 为待用户确认草案——确认前不得实现迁移。** 确认通过后，§头表数量更新为 11。

### 5.1 追补：`providers` / `models`（T-083 已落库，docs lane 欠账回填）

| 表 | 权威事实 | 领域 / 生产者 → 消费者 | 行标识 | 预期基数与增长 | 保留与删除 | 敏感字段 | 关键索引与约束 | 废弃触发条件 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `providers` | 用户配置的模型提供商身份与端点 | 设置（T-083）→ 聊天网关、模型页 | UUID PK | 每提供商 1 行，个位数到十几行 | 随用户在设置页删除 | 端点 URL（非凭证） | `id` 主键；**API Key 不入表**（仅 Keystore 密文 SharedPreferences） | 提供商体系被替代 |
| `models` | 提供商下可用模型清单与元数据 | 设置（T-083）→ 模型选择、快速模型分工（089） | UUID PK | 每提供商数行到数十行 | 随提供商级联删除 | 模型名（非用户内容） | `provider_id` 外键索引 | 同上 |

> 注：083 的真实引擎证据（Android 13 真机、V2→V3 前向迁移、PRAGMA/integrity）见其任务文件 Verification report；本行文字仅为文档欠账回填，不替代该证据。

### 5.2 新增：`attachments` / `message_attachments`（T-096，已确认）

> **2026-09-24 用户确认：双表 + `message_id` ON DELETE CASCADE 偏离方案通过**（会话内拍板，T-096 据此开工）。

依据：`Docs/spec/SPEC-2026-09-23-file-management.md`（082 verified）双表草案；本节按清单 §3 跨表共同约定细化并做一处**显式偏离**（见 ⚠）。

| 表 | 权威事实 | 领域 / 生产者 → 消费者 | 行标识 | 预期基数与增长 | 保留与删除 | 敏感字段 | 关键索引与约束 | 废弃触发条件 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `attachments` | 一份已复制入私有目录的文件：内容哈希、显示名、mime、大小、解析状态与解析文本缓存 | 附件存储层（096）/ 选择解析（097）→ 文件管理页（096）、消息注入（097）、孤儿清理（098） | UUID PK（对齐 §3「全部 UUID」约定，偏离 SPEC 草案的「自增」——草案自述最终以实现+reviewer 为准） | 每唯一文件 1 行；随使用线性增长；哈希去重压缩重复 | 仅经文件管理页显式删除或 098 孤儿双确认清理；**不随消息/分支/主题删除级联** | `display_name`、`extracted_text` 含用户文档内容（本地库，不上传） | `content_hash` **UNIQUE**（幂等去重键）；`created_at`/`updated_at` 为 UTC epoch millis（时间口径权威 `.codex/rules/sql.md`） | 云备份接管本地存储（082 Deferred），或附件能力移除 |
| `message_attachments` | 一条消息对一份附件的引用（1 引用 1 行） | 发送路径（097）→ 消息附件标注、098 孤儿判定 | UUID PK | 每消息每附件 1 行；随对话增长 | 见 ⚠ 级联语义 | 无正文（纯引用） | `message_id` 索引、`attachment_id` 索引；均 FK 语义指向 `messages.id` / `attachments.id` | 同上 |

**⚠ 与 SPEC-082 草案字面的一处偏离（请一并确认）**：SPEC 写「`message_id` 不级联删除——删除消息时引用行由清理链路处理」。落库后若**不**级联，删除带引用的消息会因 `foreign_keys=ON` 直接失败，破坏既有消息删除主路径（053 已验收行为）。本记录改为：`message_id` **ON DELETE CASCADE**（引用行随消息消失），`attachment_id` **不级联**（文件记录永不随消息删除）——消息删除后附件自动成为「零引用」候选，仍由 098 的「引用表零引用 + 消息表无记录」**双确认**判定与用户确认后清理，文件绝不静默消失。该语义使 SPEC 的三条硬约束（不改消息主表、引用挂消息 ID、一文件一物理记录）全部保持成立。

**`attachment_id` 为语义引用、不声明强制外键**（SPEC「FK 语义」的落地）：删除**被引用**的附件是规格明确要求的路径（管理页删除 → 消息标注缺失），引用行必须比附件行活得久才能支撑缺失标注；若声明强制 FK，该路径被 `foreign_keys=ON` 拒绝。故 `attachment_id` 仅建普通索引、不写 `REFERENCES`，引用解析一律 LEFT JOIN——附件行不在即「缺失」。`message_id` 仍为强制 FK 并随消息级联。两个引用方向的非对称是规格语义（缺失标注）与既有删除主路径共同要求的，db-migration-reviewer 复核时按此判定。

**迁移与验证计划**：`DATABASE_VERSION` 3→4，`MIGRATION_V3_TO_V4_STATEMENTS` 仅两条 `CREATE TABLE` + 索引（纯增量、无破坏性操作、不改已发布步骤）；真机 API 28+ 走生产 `onCreate` + 从 V3 升级 + 关闭重开 + `PRAGMA foreign_keys`/`foreign_key_check`/`integrity_check` + DAO 读写与约束验证；`db-migration-reviewer` 复核必过。
