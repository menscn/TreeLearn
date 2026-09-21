# 知芽（TreeLearn）Android SQLite 建表入场确认清单 V1.0

| 项目 | 内容 |
| :--- | :--- |
| 状态 | **待用户一次性确认**（确认后 T-004 方可开工） |
| 日期 | 2026-09-20 |
| 存储引擎 | Android 设备本地 SQLite（`SQLiteOpenHelper` + 显式 DAO） |
| 权威来源 | 技术方案 V1.0 §5（领域模型）、§6.1/§6.2（七张逻辑表与入场记录）、§10/§11（防旧写回、未读与阅读位置） |
| 规则依据 | `.trae/rules/sql.md`「Permanent table admission guard」与「建表审批边界」 |
| 表数量 | 7（新增第八张表须重新取得用户确认） |

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
| `branches` | 父子关系、标题、归档/折叠、来源版本、未读序号、语义阅读位置 | `BranchRepository` → 聊天、导航、画布 | UUID PK | 每分支 1 行；随主题增长 | 子树删除或主题删除级联 | 标题与锚点 quote 可能含用户内容 | `topic_id + parent_id`、`source_revision_id`、`archived_at` 索引；**每主题仅一个根**；`parentBranchId/sourceMessageId/sourceRevisionId/anchorStart/anchorEnd/anchorQuote/anchorHash/contextSnapshotId` | 树模型被替代 |
| `messages` | 分支内有序的用户消息与 AI 逻辑位置 | `ConversationRepository` → 聊天、搜索、上下文 | UUID PK | 每轮约 2 行；随对话增长 | 随分支删除 | 用户原文 | `branch_id + ordinal` **唯一**；AI 行持有当前版本/当前操作引用；用户消息正文创建后不可变 | 消息序列模型被替代 |
| `answer_revisions` | 成功稳定回答的**不可变**推理、最终正文与生成策略版本 | `GenerationRepository` → 聊天、来源、上下文、搜索、诊断 | UUID PK | 每次成功生成/重置新增一行；**被引用版本长期保留** | 随消息删除 | 推理与回答正文 | `message_id + revision_no` **唯一**；`prompt_policy_version` 非空；稳定序号单调递增；创建后不可变 | 版本事实迁移到等价不可变存储 |
| `generation_operations` | 提交/流式/失败/中断状态、部分推理/回答及本次生成策略版本 | `GenerationCoordinator` → 恢复、重试、诊断 | UUID 操作 ID | 活动及当前失败记录；成功后可清理无引用的旧失败尝试 | 随消息删除 | 部分推理/回答、错误分类 | **每分支最多一个活动操作**；每行 `prompt_policy_version` 非空；首次根问题在 2xx accepted 事务内随树原子写入；终态后不可再改写；SSE 按 250ms 或 256 字符批量落盘 | 生成状态由等价持久载体接管 |
| `context_snapshots` | 子分支**不可变**出生上下文清单 | `CreateBranchUseCase` → `ContextAssembler` | UUID PK | 每非根分支 1 行 | 随分支删除 | 上游内容引用（非正文副本） | `branch_id` **唯一**；`schema_version` 与 `digest`（SHA-256）必填；读取必须走结构化 JSON 解析 | 快照迁移到同等可验证格式 |
| `ai_artifacts` | 标题候选、分支摘要、要点、回答推荐、首页推荐及其提示词用途/版本 | 派生 AI 用例 → 首页、导航、推荐、诊断 | UUID PK | 每 `scope/kind` 保留当前有效结果与必要版本 | 随 scope 删除 | 标题、摘要、推荐文本 | `kind + scope_id` 索引；`prompt_purpose` / `prompt_policy_version` 非空；`based_on_revision_id` / `input_digest` 防旧结果覆盖（写回前事务内复核基线） | 派生物不再持久化或拆入专表 |

## 3. 跨表共同约定

- **时间**：所有真实时间点存 **UTC epoch millis `INTEGER`**，由应用显式写入；禁用 SQLite `CURRENT_TIMESTAMP` 与设备本地时区（`rules/sql.md`）。
- **ID**：全部使用应用生成的 **UUID 字符串**，不使用自增 ID；为未来导出/导入保留空间，但**首版不建同步字段、租约或服务端表**。
- **外键与事务**：所有表启用外键，每次打开显式 `PRAGMA foreign_keys=ON`；跨表写入必须在单个事务内完成。
- **迁移**：DDL 只允许位于生产 `SQLiteOpenHelper.onCreate/onUpgrade`（或集中版本化 migration runner）；`DATABASE_VERSION` 单调递增，已发布版本的迁移步骤不可改写，禁止运行时散落 DDL。
- **不入库的敏感数据**：**API Key 不进入任何表**（也不进日志/Bundle/剪贴板），仅以 Android Keystore AES-GCM 密文存 SharedPreferences；模型与思考模式偏好可存普通 SharedPreferences。
- **提示词存储**：表中只保存稳定的 `prompt_purpose` / `prompt_policy_version`，不保存完整 system 提示词正文。正文由 `:ai` 的版本化注册表维护；主回答成功事务必须把 operation 的版本原样复制到 revision。
- **可观测性**：变更须记录 `PRAGMA user_version`、`integrity_check`、`foreign_key_check`、关键表/列/索引的直接验证结果（`rules/sql.md`「Android SQLite / SQLiteOpenHelper」段）。
- **验收路径**：新装走生产 `onCreate`；升级从每个仍受支持的已发布版本走生产 `onUpgrade`；另需覆盖关闭重开、事务回滚、级联删除、进程中断后的 `INTERRUPTED` 落定与手动重试。

## 4. 用户确认

- [ ] 七张表的**存在性、分工与不可合并性**已确认（§1、§2）。
- [ ] 跨表共同约定已确认（§3）。
- [x] 2026-09-21 新增的提示词用途/版本字段语义已由用户确认；不新增第八张表。
- [ ] 确认人：__________　确认日期：__________

> 确认后本清单即为 T-004（SQLite schema / DAO / 事务）的入场记录权威来源；任何第八张表、字段语义变更或废弃，都须回到本清单重新确认并同步技术方案 §6。
