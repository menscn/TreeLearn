# Spec: 附件与文件管理（输入栏附件链路 + 文件管理页 + 孤儿清理）

id: SPEC-2026-09-23-file-management
title: 附件与文件管理（输入栏附件链路 + 文件管理页 + 孤儿清理）
date: 2026-09-23
intent: Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md
author: spec-author
status: accepted

## Requirement spec（需求规格）

### 范围

1. **输入栏附件链路**（intent「核心流程」：附件从输入栏选择、解析入上下文；实现任务 T-2026-09-23-097）：
   - 输入栏新增附件键，走 SAF（系统文件选择器）选择文件，不申请存储权限（intent「约束」与任务 097「无存储权限（SAF）」一致）。
   - 选中后复制入应用私有目录、解析为文本、进入当次上下文（作为该条消息的附加内容随请求发送）；发送后随消息持久化附件引用。
   - 附件行可移除；解析失败不阻断发送（降级，见 Interface plan）。
2. **文件管理页**（intent「核心流程」：文件管理页可查可删；实现任务 T-2026-09-23-096）：
   - 附件文件列表（文件名、类型、大小）、占用统计、单文件删除、孤儿清理入口。
   - 删除为确认式；删除被引用文件后消息文本保留，仅标注附件缺失（任务 096 watch-outs：「管理页删除不得级联删消息内容（只标注缺失）」）。
3. **孤儿清理与生命周期**（intent「核心流程」对应 RikkaHub 的附件孤儿扫描；实现任务 T-2026-09-23-098）：
   - 删除消息/子树/归档后，不再被任何消息引用的附件在文件管理页被标记为「可清理」，用户确认后清理；不自动删。

### 验收方向

- intent「Decision record」验收标准：每个实现任务（096/097/098）的 acceptance + verifier 独立验收，真机体验由用户自测。
- intent「状态与异常」：无 Key/网络失败/权限拒绝/解析失败须有降级或拦截，不得半成品入口——对应本 spec 的 SAF 取消、解析失败、超大文件、文件缺失四个异常面。
- 任务 096 验收含 SQLite 迁移验证（API 28+ 真实 SQLite）+ db-migration-reviewer；任务 097 验收含 T-076 输入栏回归全绿；任务 098 验收含子树删除/归档回归全绿。

### 非目标

- 文件编辑器与文件预览器（任务 096 non-goals：「不做预览」）。
- OCR / 图像内文字识别（任务 097 Deferred：「图像 OCR」）。
- 云盘导入与云备份（任务 097/098 non-goals）。
- 语音文件附件（intent 中语音为独立能力块，由 T-2026-09-23-093/094/095 承担；本 spec 不含音频附件的选择、解析与存储）。

### 已确认事实 / 假设 / 未知项

- **已确认事实**：
  - RikkaHub 附件走 SAF 选择（`ActivityResultContracts.OpenDocument()`），复制入 `context.filesDir` 私有目录并登记进 `managed_files` 表：`D:\Project\Android\rikkahub-master\app\src\main\java\me\rerere\rikkahub\data\files\FilesManager.kt`（createChatFilesByContents）、`...\data\db\entity\ManagedFileEntity.kt`。
  - RikkaHub 文件管理页为设置内独立页，双列网格卡片 + 单删确认弹窗 + 按时间范围清理 sheet：`...\ui\pages\setting\SettingFilesPage.kt`。
  - RikkaHub 的孤儿防护是全分支引用扫描：删除队列附件前先查 `ConversationRepository.hasFileReference`（对消息节点做全表比对），且「无法确认引用时保留文件，避免误删」：`...\service\ChatService.kt`（cleanupQueuedAttachments）、`...\data\repository\ConversationRepository.kt`。
  - 知芽输入栏与草稿持久化基座（T-076 已 verified）：`android\app\src\main\java\com\treelearn\app\ui\ChatDraftStore.java`、`ChatViewState.java`；引用行（quote row）形态为 T-076 交付面。
  - 知芽 data 层为 SQLiteOpenHelper + DAO 模式：`android\data\src\main\java\com\treelearn\data\db\TreeLearnDatabaseHelper.java`、`TreeLearnSchema.java`、`dao\MessageDao.java`。
  - 知芽上下文快照只存引用不存内容：`android\core\src\main\java\com\treelearn\core\domain\ContextSnapshot.java`（SourceKind 为 USER_MESSAGE/STABLE_REVISION/SUMMARY 三种引用）。
  - 知芽子树删除复用单一 Plan 实现、单事务级联：`.codex\tasks\T-2026-09-22-library-archived-list-and-subtree-delete-053.md` Implementation notes。
- **假设**：
  - RikkaHub 的文件管理页本身未展示占用统计（`countChatFiles()` 存在于 FilesManager.kt 但该页未见调用）；本 spec 的占用统计是知芽侧任务 096 的明确要求，交互形态为知芽自行设计而非照搬。
- **未知项**（保持未知，不得在实现时悄悄升级为事实）：
  - 应用私有目录被系统清理的频率（任务 096 Unknowns 原文）。
  - PDF 文本层抽取在各真机文档上的质量差异（任务 097 Unknowns 原文）。
  - 大量附件时引用扫描的耗时上限（任务 098 Unknowns 原文）。

- 来源：Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md（核心流程/状态与异常/约束与权衡偏好/Decision record）；.codex/tasks/T-2026-09-23-attachment-storage-file-manager-096.md、T-2026-09-23-attachment-picking-parsing-097.md、T-2026-09-23-attachment-orphan-cleanup-098.md。

## Interface plan（界面方案）

### 信息架构

- **入口位置**：文件管理页从设置中心进入（intent「核心流程」：设置中心入口 → 各能力页），层级为「设置 → 文件管理」，与提供商（087）、模型（088）等兄弟页同级；对齐 RikkaHub 把文件管理放设置区的信息架构（SettingFilesPage.kt）。
- **输入栏**：附件键位于输入栏扩展区（`screen_chat_body.xml` composer，任务 097 Facts：附件扩展入口约定，不得伪入口——无附件能力时不显示入口）。附件行是 T-076 引用行（quote row）的兄弟形态：同位置、同关闭交互，**附件行与引用行互斥**（一次只有一种上下文附加；见 Doubts 第 4 条）。

### 输入栏附件行

- 状态流：点击附件键 → SAF 选择 → 「解析中」态（行内进度文案，禁止发送）→ 成功：附件行显示文件名与大小（chip 形态，右上可移除）→ 发送后随消息持久化。
- 附件行未发送期间可移除；移除后回到无附件态，草稿文本不动（复用 ChatDraftStore 的草稿保全语义）。
- 已发送消息内的附件以标注形式显示在消息附件区：文件名 + 状态；文件缺失时以警示色标注。

### 文件管理页

- 头部：占用统计（文件数 + 总大小自适应 KB/MB 展示，数值格式为任务 096 已登记的非关键问题）。
- 列表：每行/卡片显示文件名（最多两行省略）、类型、大小、加入日期；对齐 RikkaHub 卡片信息密度但用知芽单列行式或卡片皆可，视觉走知芽 token。
- 操作：单文件删除（确认弹窗，弹窗展示文件名）；「清理孤儿」入口（仅当存在可清理项时可用），点击后先列出将清理的文件清单，确认后执行。

### 状态与异常

- **[PERM] SAF 取消**：用户在系统选择器取消，返回输入栏原状，无任何副作用（不登记、不复制、不清草稿）。来源：intent「状态与异常」+ 任务 097。
- **[PART] 解析失败**：文件已复制入私有目录但文本抽取失败——保留文件、附件行显示失败态文案，提示「可移除附件后重试发送」；不阻断发送纯文本部分。来源：任务 097 核心流程原文 + intent「解析失败须有降级」。
- **超大文件拦截**：超过大小上限的文件在选择后立即拦截并提示（不复制不解析）；上限值见 Doubts 第 3 条拍板项。
- **物理文件缺失**：文件管理页与消息附件标注均显示「文件已缺失」（警示色），删除该记录仍可用；不自动重新登记、不静默复活。来源：任务 096「文件物理缺失（已被系统清理）时列表标注」+ RikkaHub syncFolder 的双向对账语义（FilesManager.kt）。
- **孤儿清理失败**：清理失败的文件保留标记可重试（任务 098 状态与异常原文）。

### 数量与呈现方式

- 单条消息附件数量上限与注入字符上限为拍板项（见 Doubts 第 3 条）。
- 视觉数值一律引用 design token（Docs/04-ux-design/design-system/tokens.json）：卡片/列表容器底色 `surface`、页面底 `bg`；卡片圆角 `r-md`、chip 圆角 `r-sm`；分隔线 `line` 与极淡分隔 `hairline`；文件名 `fs-h2`、次要信息（类型/大小/日期）`fs-sm`、标注（缺失提示）`fs-cap`；正文与确认弹窗正文 `fs-body`、页标题 `fs-h1`；主文字 `ink`、次级 `ink2`、弱化 `ink3`；主操作/可用态用 `green`（深色变体 `green-deep`，浅底 `green-soft`/`green-faint`）；解析失败与文件缺失警示用 `amber` 承 `amber-soft` 底；删除确认的破坏性动作用 `red` 承 `red-soft` 底；间距用 `s1`–`s4` 与页面安全边距 `gutter`；卡片投影 `sh-1`、弹层投影 `sh-2`。
- 触达热区沿用 intent「Expected outcome」的 44dp 热区约定（来源为 intent 文本，非视觉 token）。

- 来源：token 名全部取自 Docs/04-ux-design/design-system/tokens.json（color/space/radius/fontSize/shadow 区块）；形态参照 RikkaHub `SettingFilesPage.kt`、`FilesPicker.kt`；互斥与入口约定来自 T-076/T-097 任务文件；intent「权衡偏好：界面交互照搬、视觉风格用知芽 token」。

## Technical spec（技术规格）

### 存储布局

- 附件物理文件存应用私有目录（`context.filesDir` 下专属子目录），不申请存储权限；文件名以内容 SHA-256 哈希为前缀去重（同内容只存一份），显示名与哈希名解耦。任务 096 AI-autonomous decisions 原文：「附件文件名用内容哈希去重存储」。
- 对照事实：RikkaHub 用 UUID 命名不去重（FilesManager.kt `buildUuidFileName`）；知芽选择哈希去重，代价是登记前需计算哈希（后台线程）。
- 内容哈希列同时充当幂等键：同一文件被多个消息引用时，物理文件与主记录各只有一条，引用关系多对一。

### 附件引用表字段草案（供 T-2026-09-23-096，挂消息 ID，不改消息主表）

- 主表 `attachments`（一文件一行）：
  - `id`（PK，自增）
  - `content_hash`（TEXT，UNIQUE 索引；SHA-256 hex，幂等去重键）
  - `display_name`（TEXT，用户可见名）
  - `mime_type`（TEXT）
  - `size_bytes`（INTEGER）
  - `extracted_text`（TEXT，可空；解析成功的文本缓存，见下）
  - `parse_state`（TEXT，枚举字符串：`parsed` / `parse_failed`；复制失败不产生记录）
  - `created_at` / `updated_at`（INTEGER，毫秒）
- 引用表 `message_attachments`（一引用一行，挂消息 ID）：
  - `id`（PK，自增）
  - `message_id`（TEXT，FK 语义指向消息主表；不级联删除——删除消息时引用行由清理链路处理）
  - `attachment_id`（INTEGER，FK 语义指向 `attachments.id`）
  - `created_at`（INTEGER）
  - 查询索引：`message_id`、`attachment_id` 各一。
- 草案性质：最终字段与迁移以任务 096 实现 + db-migration-reviewer 复核为准；本草案只约束「不改消息主表、引用挂消息 ID、一文件一物理记录」三条。
- `extracted_text` 的理由：知芽上下文快照只存引用不存内容（ContextSnapshot.java），fork 快照重建时无法重放「当时的解析现场」；解析文本随附件记录持久化一份，发送与快照重建都读缓存，解析只发生一次。代价：DB 体积增长，受注入上限约束。

### 解析方案对照

1. **纯文本类（txt/md）**：直接按 UTF-8 读取，纯 Java，无依赖；编码探测失败按 [PART] 降级。
2. **PDF**：Android 自带 `android.graphics.pdf.PdfRenderer` 只能渲染位图、不能抽取文本层——直接方案不存在。两条候选：
   - 引入 Apache PDFBox（Android 移植版）：完整文本层抽取，但违反「无新依赖原则」，必须用户明示批准才可动 build.gradle（任务 097 watch-outs：依赖引入必须有 082/SPEC 拍板记录，先斩后奏无效）；体积与方法数影响属未知项，批准前需以依赖分析实测，不得凭估算拍板。
   - 轻量方案：自实现 PDF 文本层直接抽取（解析内容流中的文本操作符），无依赖但覆盖度有限（压缩流、字体编码差异会导致抽取失败，按 [PART] 降级）。
   - 两条走法的拍板见 Doubts 第 2 条。
3. **Docx**：Docx 是 ZIP + XML（OOXML），`java.util.zip` + XML 解析即可读取 `word/document.xml` 正文文本，纯 Java 可行、无依赖；是否纳入第一批取决于用户对 Word 场景的实际需求（见 Doubts 第 1 条）。
4. 对照事实：RikkaHub 不做本地文本解析，附件以 Document 消息部件（本地 URL + mime）直接交由模型服务端处理（`ai\src\main\java\me\rerere\ai\ui\Message.kt` 的 `UIMessagePart.Document`；`ChatMessage.kt` 仅按 mime 显示 pdf/docx 图标）。知芽走「本地解析为文本再注入」，因为知芽的请求构造与 fork 快照契约基于纯文本引用链。

### 注入与上限（错误语义）

- 附件解析文本在请求侧并入该条用户消息的上下文信封（与引用信封同层级的兄弟机制），受**注入字符上限**约束，目的：保护模型上下文预算与 fork 快照体积（任务 097 Constraints：「注入字符上限（保护 fork 快照）」）。
- 超上限语义：**拦截并明示**，不静默截断——静默截断会让模型基于残缺上下文作答，伪造了「附件已完整注入」的用户预期；提示文案给出「移除附件或换更小文件」两条出路。
- 发送路径状态迁移（附件）：`SAF 选择 → 已复制 → 已解析(parse_state=parsed) / 解析失败(parse_state=parse_failed) → 随消息引用（message_attachments 落行）→ 消息删除后归孤儿（引用扫描判定）→ 用户确认清理 → 物理删除 + 记录删除`。
- 解析在工作线程执行，禁止阻塞主线程（任务 097 Risks：「解析阻塞主线程」）。

### 孤儿判定与清理

- 判定为孤儿须**双确认**：① `message_attachments` 中该 `attachment_id` 零引用行；② 消息主表按 ID 无任何记录持有该附件（防引用表与消息表短暂不一致时误判）。任务 098 watch-outs 原文：「清理仅作用于引用表零引用 + 消息表无记录双确认」。
- 扫描时机：进入文件管理页时 + 删除消息/子树后（任务 098 AI-autonomous decisions）；工作线程执行，不做定时后台服务。
- 清理为**确认式而非自动删**：扫描只标记，删除必须用户确认（任务 098「误清风险靠确认弹窗」）。对照 RikkaHub 的保守原则——其 ChatService 在「无法确认引用时保留文件，避免误删」，知芽把该原则外化为显式的确认式交互。
- 幂等：清理动作对已删文件、已删记录重复执行不报错（任务 098 状态与异常）。
- 与删除链路的关系：子树删除/归档复用 T-053 的单一 Plan 实现与单事务语义，附件扫描是新增旁路，不改动删除链路本身（任务 098 Behavior impact）。

- 来源：RikkaHub `FilesManager.kt`、`ManagedFileEntity.kt`、`ChatService.kt`、`ConversationRepository.kt`、`Message.kt`（路径见上文）；知芽 `android\data\...\db\`（DAO 模式）、`android\core\...\domain\ContextSnapshot.java`、`android\app\...\ui\ChatDraftStore.java`；`.codex\ROADMAP.md` 条目 T-2026-09-23-096/097/098、T-076、T-053（任务文件路径见 `.codex\tasks\`）。

## Doubts（疑虑点）

- **疑虑 1：第一批支持哪些格式？**
  - 冲突对象：任务 097「SPEC-082 定了格式范围（第一批 txt/md/PDF 文本层，Docx/图像按拍板）」——该拍板尚未发生，本 spec 不得替它收敛。
  - 两种走法：A. 第一批 txt/md/PDF（文本层）+ Docx 视纯 Java 可行性评估后决定，图像 OCR 后置（本 spec 建议）；B. 第一批就含 Docx 或图像。
  - 建议：走 A。理由：txt/md 零成本，PDF 文本层是学习场景最高频文档；Docx 解析可行但用户未表达 Word 场景需求，先不背复杂度；OCR 与知芽无图像附件定位冲突。
  - 代价：走 A 意味着 Word 文档用户第一批需自行转 PDF/txt。
  - 谁拍板：用户。

- **疑虑 2：是否批准引入 PDFBox 类依赖？**
  - 冲突对象：Harness「无新依赖原则」（任务 097 watch-outs：依赖引入必须先记录用户批准原文再动 build.gradle）与 PDF 文本抽取无平台 API 的技术现实（`PdfRenderer` 只渲染不抽文本）。
  - 两种走法：A. 批准引入 PDFBox Android 移植版——文本抽取覆盖度最高，但违反无新依赖原则，体积与方法数影响未知、批准前须依赖分析实测，且 AGPL 依赖合规需一并核对；B. 不引库，采用自实现 PDF 文本层直接抽取的轻量方案——零依赖，但对压缩流、字体编码的覆盖有限，抽取失败按 [PART] 降级（用户可感知为「这份 PDF 读不了」）。
  - 建议：先走 B 验证真实文档的抽取成功率，真机不达标再回来拍板 A；两走法不互斥，可分批。
  - 代价：B 的覆盖度风险落在用户真机体验上；A 的风险落在包体积与依赖治理上。
  - 谁拍板：用户（A 必须用户明示批准，无默认值）。

- **疑虑 3：附件大小上限与注入字符上限取值？**
  - 冲突对象：任务 097「超大文件拦截」「注入字符上限」要求上限，但两个数值无任何已收敛出处（RikkaHub 源码中未见附件大小上限，属未知项，本 spec 不填数）。
  - 两种走法：A. 附件大小上限取 20MB 级（本 spec 建议——覆盖常见教材/讲义 PDF，同时给复制与哈希计算的耗时留余量），注入字符上限在实现任务内以模型上下文预算反推；B. 上限做成设置项由用户自调。
  - 建议：走 A，两个上限先固化常量，单用户场景无需设置项；注入上限的具体数值由实现任务按所选模型的上下文预算反推后回填任务文件，不在本 spec 填空。
  - 代价：20MB 级仍可能放行低文本密度的扫描版 PDF（文本层为空 → [PART] 降级提示），用户可能误以为是「文件太大」。
  - 谁拍板：用户。

- **疑虑 4：附件行与引用行互斥是否成立？**
  - 冲突对象：任务 097 已把「附件行与引用行互斥」登记为 AI-autonomous decisions（一次一种上下文附加），但该决策未经用户确认；同时知芽已有 fork 快照引用链（ContextSnapshot 三种 SourceKind），附件文本并入消息后与引用信封在同一条消息内共存于请求历史。
  - 两种走法：A. 维持互斥——输入栏同一时刻只显示附件行或引用行之一，新附加方式替换旧的（交互简单、上下文构成可预期）；B. 允许并存——引用与附件同时附加（表达力强，但请求信封多一层组合语义，fork 快照重建需处理两种来源叠加，实现与测试面翻倍）。
  - 建议：第一批走 A，互斥成立；B 留作后续若真实场景需要再议。
  - 代价：走 A 时「既想引用原文又想带附件」的用户需发两条消息。
  - 谁拍板：用户（把 097 的自主决策转为显式拍板）。

### 拍板记录（2026-09-23 08:05 用户回复「按照建议采纳」）

- 疑虑 1 **采纳建议 A**：第一批支持 txt/md/PDF（文本层）；Docx 视纯 Java 可行性后续决定，图像 OCR 后置。
- 疑虑 2 **采纳建议 B（先行）**：不引 PDFBox，第一批走自实现 PDF 文本层直接抽取（零依赖，抽取失败按 [PART] 降级）；真机抽取成功率不达标时再回来单独拍板是否引库，两走法可分批。
- 疑虑 3 **采纳建议 A**：附件大小上限取 20MB 级常量；注入字符上限由实现任务按所选模型上下文预算反推后回填任务文件。
- 疑虑 4 **采纳建议 A**：附件行与引用行互斥成立（第一批）；并存留作后续再议。

据此本规格四项拍板点全部收敛，status 翻 accepted。
