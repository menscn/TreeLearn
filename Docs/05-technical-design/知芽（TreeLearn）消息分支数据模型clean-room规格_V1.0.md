# 知芽（TreeLearn）消息分支数据模型 clean-room 规格 V1.0

> 版本：V1.0 ｜ 日期：2026-09-23 ｜ 任务：T-2026-09-23-070（父需求 T-2026-09-23-067）
> 状态：draft，待用户 review；review 通过前不得据此改 schema 或代码。
> 基线声明：**知芽七张逻辑表与 Branch/Message 模型是基线，本规格只做对照与增补建议，不推翻、不替换。** 涉及 schema 的建议若被采纳，必须另立任务并走 `db-migration-reviewer`。

## 0. clean-room 使用规则

1. 本规格是对一个成熟开源实现（RikkaHub）分支机制的中文提炼，正文不含源代码或逐行伪代码。
2. **实现者只读本规格与知芽代码，禁止阅读 RikkaHub 源码**；§9 出处索引仅供评审者审计。

## 1. 概念映射

| 知芽概念 | RikkaHub 对应物 | 关系 |
|---|---|---|
| 主题（Topic） | 会话（Conversation） | 一对一近似 |
| 树 / 分支（Branch，显式 parentId） | 消息节点列表（线性） + 节点内候选 | **模型本质不同**，见 §2 |
| 消息位置（Message 位置 + activeRevisionId） | 消息节点（候选数组 + 选中下标） | 形似神异：知芽是「位置引用版本」，RikkaHub 是「槽位装候选」 |
| `BranchSource`（派生溯源，必填） | **无对应物** | 知芽已强于 RikkaHub |
| `AnswerRevision`（答案版本） | 节点内候选消息 | 版本语义近似 |

知芽现状字段（已核对真实代码，出处见 §9）：`Branch`（id、topicId、parentBranchId、BranchSource、title、createdAt/updatedAt、latestStableSequence/lastSeenStableSequence、collapsed、archivedAt）；`Message`（id、branchId、role、ordinal、userContent、quotedText、createdAt、activeRevisionId、activeOperationId）。

## 2. RikkaHub 分支模型机制（提炼）

出处：`app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`、`data/db/entity/MessageNodeEntity.kt`、`data/repository/ConversationRepository.kt`、`service/ChatService.kt`（均为机制提炼，实现者免读）。

1. **不是树，是「位置级多版本」**：会话是有序节点列表；每个节点是一个槽位，装同一位置的多个候选消息和一个选中下标。分支 = 同槽位内的并列候选，**没有派生关系字段、没有来源指针、没有树状可视化**。
2. **两种分支来源**：① 重新生成/编辑不覆盖旧消息，而是向槽位追加新候选并自动选中——兄弟版本由此自然形成；② 「创建分支」把某消息及之前的全部节点**深拷贝为一条全新会话**（附件物理复制、标题加序号去重），派生后与原会话完全独立，**不记录来源**。
3. **切换**：只改选中下标并整体保存；越界下标抛异常；切换后下游状态不联动——线性模型里这意味着上下文可能「静默不一致」（上层换了版本、下层各节点指针不动）。
4. **回溯**：不存在「回到来源」能力（模型里没有来源指针）。
5. **持久化是整体替换**：每次保存在事务内先删光该会话的节点再全量重插；无乐观锁/版本号，并发是 last-writer-wins。消息顺序靠列表位置而非时间戳。
6. **删除联动**：删单条消息 = 只从其槽位移除该候选，同槽位其他分支保留；槽位空则整节点移除，选中下标收缩到合法范围。删整个会话 = 外键级联删节点 + 清搜索索引 + **扫描所有分支的所有候选**找文件引用，再删孤儿附件。生成中禁止删除会话。
7. **恢复校验**：启动/生成前清理损坏候选（含未完成动作的消息），选中下标越界则回退一格，最后滤除空节点；分页加载遇超大行异常跳页继续。
8. **上下文压缩是有损操作**：用摘要替换历史节点，**丢弃全部旧分支候选**。

## 3. RikkaHub 机制对知芽的语义提醒（映射注意点，非需求）

1. 知芽的显式树（`parentBranchId` + 必填 `BranchSource`）已解决 RikkaHub 无法表达的两个问题：任意深度子树、派生溯源。**不要为对齐 RikkaHub 而引入每槽位选中下标**——它是线性模型的产物，树状模型天然避免其「静默上下文不一致」缺陷。
2. RikkaHub 的「编辑/重生成 = 追加版本」与知芽「重试/重置切换 `activeRevisionId`、从不覆盖」语义一致，方向上互相印证。
3. RikkaHub 的截断（重生成用户消息时硬截后续、不可撤销）在树状模型中应表达为「子树 detach」，不可照搬数组截断。

## 4. 知芽对照清单（三档）

> 「已核对」= 本任务已用知芽真实代码核实；「待核对」= 需在实现任务开工前用真实代码确认；「建议增补」= 知芽模型可能缺失的边角处理，采纳与否由用户拍板。

### 已核对（知芽已覆盖，无需动作）

- 派生溯源：`BranchSource` 必填且不可变（`android/core/src/main/java/com/treelearn/core/domain/BranchSource.java`）——强于 RikkaHub（它无来源指针）。
- 版本不覆盖：`Message` 位置引用 `activeRevisionId`，重试/重置只切换引用（`Message.java` 注释与字段结构）。
- 归档不隐藏历史：`Branch.archive` 语义（`Branch.java`）。

### 待核对（实现任务开工前逐条确认）

- [ ] 越界/损坏恢复：RikkaHub 有「加载时清理损坏候选、指针回退、滤空节点」；知芽对 `activeRevisionId` 指向已删除 revision、以及 restore 时行损坏的行为是否有等价防护（对照 `Branch.java` restore 与 DAO 读取路径）。
- [ ] 孤儿附件清理：知芽消息若带附件，删除分支/子树时是否有「全树引用扫描后再删物理文件」的机制。
- [ ] 并发写：知芽单会话单用户下整体替换是否够用；是否存在多入口写同一分支（如生成 Worker 与 UI 同时落库），是否需要版本号。
- [ ] 搜索覆盖面：RikkaHub 的全文索引覆盖所有分支候选（含未选中版本）；知芽搜索（`BranchTitles.java` 及相关）是否明确决策过「搜全部版本还是仅当前版本」。

### 建议增补（每条需用户拍板后才立项）

| # | 建议 | 理由 | 最小改动方向 |
|---|---|---|---|
| S1 | 子树删除时对「父链上正在生成的任务」做防冲突检查 | RikkaHub 在生成中禁止删会话；知芽归档已声明不取消生成，但**删除**是破坏性的，需显式规则 | 数据层加「删除前置检查」，不改表 |
| S2 | 上下文压缩只作用于当前路径、保留其他分支 | RikkaHub 压缩丢光全部分支候选是有损反面教材；知芽做压缩时必须保留分支历史 | 压缩逻辑约束，不改表 |
| S3 | 深拷贝类操作（若未来有导出/复制分支）必须同步复制附件引用 | RikkaHub fork 物理复制附件 URL 的做法保证两个会话互不牵连 | 操作规范，不改表 |

## 5. clean-room 边界重申

本文档全部机制表述均为行为级描述；知芽实现者不得以任何「对照理解」为由阅读 RikkaHub 源码。若规格表述不足以支撑实现，回到本任务补规格，而不是去读源码。

## 6. 出处索引（评审者用，实现者免读）

- RikkaHub：`D:\Project\Android\rikkahub-master\app\src\main\java\me\rerere\rikkahub\`（`data/model/Conversation.kt`、`data/db/entity/MessageNodeEntity.kt`、`data/db/entity/ConversationEntity.kt`、`data/repository/ConversationRepository.kt`、`service/ChatService.kt`、`data/db/fts/MessageFtsManager.kt`、`data/db/migrations/Migration_16_17.kt`）。
- 知芽现状：`android/core/src/main/java/com/treelearn/core/domain/Branch.java`、`Message.java`、`BranchSource.java`（2026-09-23 逐行核对）。
- 提炼过程：本任务 T-070 执行记录（2026-09-23，Explore 代理只读分析）。
