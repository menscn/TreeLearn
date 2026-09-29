# 知芽（TreeLearn）RikkaHub 功能吸收矩阵 V1.0

> 版本：V1.0 ｜ 日期：2026-09-23 ｜ 任务：T-2026-09-23-072（父需求 T-2026-09-23-071）
> 状态：draft，待用户 review；**review 通过前，任何条目不得进入实现任务。**

## 0. 决策口径

- 四档决策：**adopt**（按语义吸收，第一批实现）、**adapt**（吸收但需改造或对照补缺）、**defer**（有价值但延后，须用户再次拍板才立项）、**reject**（不进入知芽，写明理由）。
- 优先级：P0（第一批，074–076 已预留位置）、P1（第一批备选或紧随其后）、P2（远期候选池）。
- 「吸收」只吸收**功能语义与交互行为**，不引入 RikkaHub 的 Kotlin 类、Compose、Room/DataStore 或任何其依赖（AGPL clean-room 约束沿用 T-067/069/070）。
- 异常语义字典（正文用代号引用，避免逐条重复）：**[OFF]** 离线/网络失败 · **[NOKEY]** 未配置 DeepSeek Key · **[CANCEL]** 用户取消/停止 · **[PART]** 部分成功（半截回答） · **[DUP]** 重复操作防护 · **[RECOVER]** 数据恢复/重建 · **[PERM]** 权限拒绝。
- 未知项一律写 UNKNOWN，不写成结论。

## 1. 消息富内容与动作

| # | 能力 | RikkaHub 事实（出处） | 知芽现状（证据） | 决策 | 优先级 | 依赖 | 异常语义 |
|---|---|---|---|---|---|---|---|
| 1.1 | Markdown 正文渲染（列表/粗斜体/链接/引用块） | `ui/components/richtext/` | 已有 `MarkdownRenderer.java`（`app/screen/`），覆盖范围 UNKNOWN（待逐语法核对） | adapt：先盘点缺口再补 | P0 | 074 | [PART] 渲染失败回退纯文本不丢内容 |
| 1.2 | 代码块：高亮、语言标注、块内复制 | `:highlight` 模块、`ui/components/richtext/` | 无代码块专用呈现（UNKNOWN，待核 renderer） | adapt：先做「等宽块+复制」高亮库延后 | P1 | 074 | [PART] 复制失败提示 |
| 1.3 | 表格 / LaTeX / Mermaid 渲染 | `ui/components/table/`、README Features | 现状（2026-09-28）：表格由 Android 真实表格视图呈现；四种公式定界符已接入扫描、解析与 MathSpan，renderer 仪器断言通过；但 MathSpan 独立复核 FAIL（固定 16f、未适配宿主字号/密度、绘制后未恢复 Paint 状态），故整条公式链路未验收；Mermaid 未实现 | 历史决策（原矩阵日期 2026-09-23）保留：defer（学习主流程非必需，渲染成本高）/ P2 / [PART] 降级为原文。现状（2026-09-28）：不支持公式降级为去定界符源码、未闭合流式公式保留原文；Mermaid 继续 defer | P2 | T-101–104 | [PART] 不支持或未闭合公式保留为源码 |
| 1.4 | 思维链（reasoning）折叠卡：生成中自动展开+计时、结束自动收起 | `ui/components/message/ChatMessageReasoning.kt` | 已有 `ReasoningDisclosure.java` + 流式披露（T-066 交付）；计时 UNKNOWN | adapt：对照补计时与收起时机 | P0 | 075 | [CANCEL] 取消时保留已披露内容 |
| 1.5 | 消息常驻动作条 + 更多抽屉 | `ui/components/message/ChatMessageActions.kt` | 已有 `view_answer_actions.xml`/`AnswerActionBar.java`（retry/reset/fork/quote/copy） | adopt：按 073 视觉规格重排 | P0 | 074 | [DUP] 生成中禁用破坏性动作 |
| 1.6 | 版本切换器「← 1/N →」 | `ui/components/message/ChatMessageBranch.kt` | 已有 version/revision 动作（`ChatActions.java` onVersionPressed/onRevisionSelected），呈现形式 UNKNOWN | adapt：按 073 统一呈现 | P0 | 074 | [RECOVER] 越界下标回退（见分支规格 T-070） |
| 1.7 | 消息收藏（引用式：跳回原会话+定位） | `ui/pages/favorite/FavoritePage.kt` | 无 | defer：与树状学习主流程正交 | P2 | 独立任务 | [RECOVER] 来源被删时收藏项降级显示 |
| 1.8 | 消息级翻译 / TTS 朗读 | README Features（AI Translation、TTS）；`ChatMessageActions.kt` | 无 | reject：偏离树状学习主流程，且引入额外服务依赖 | — | — | — |
| 1.9 | 分享/导出（多选消息导出） | `ChatList.kt` Export 流程 | 无 | defer：单机价值低，等真实诉求 | P2 | 独立任务 | [PART] 导出中断保留已生成文件 |

## 2. 生成可靠性

| # | 能力 | RikkaHub 事实（出处） | 知芽现状（证据） | 决策 | 优先级 | 依赖 | 异常语义 |
|---|---|---|---|---|---|---|---|
| 2.1 | 停止生成（发送键三态中的停止态） | `ui/components/ai/ChatInput.kt` 发送键三态、`ChatVM.stopGeneration` | `ChatMessage.attemptRunning` 存在、`ChatViewState.sending` 存在，但 `ChatActions.java` **无 onStop 动作**、`screen_chat_body.xml` 无 stop 控件 | adopt：P0 首项 | P0 | 075/076 | [CANCEL] 停止后已收内容落库（SSE 规格 §5.2） |
| 2.2 | 网络异常自动重试（3 次指数退避、从快照重启） | `data/ai/GenerationLoop.kt` | `:ai` gateway 重试策略 UNKNOWN（待核对，对照 SSE 规格 E4） | adapt：按 SSE 规格 T-069 补齐 | P1 | 075 + :ai 任务 | [OFF] 仅网络类异常重试；[NOKEY] 直接失败不重试 |
| 2.3 | 取消/失败后的部分内容保存与恢复 | `service/ConversationSession.kt` | 已有生成恢复链路（T-008 generation-coordinator-recovery 交付） | adapt：对照补「取消也落库」语义 | P1 | 075 | [PART]/[CANCEL] 统一收尾点落库 |
| 2.4 | 发送消息队列（生成中排队下一条） | `MessageQueue`（`service/`） | 无 | defer：状态范围大，用户已明示延后 | P2 | 独立任务 | [DUP] 队列去重 |
| 2.5 | 重新生成前确认弹窗（截断警告） | `ChatMessageActions.kt`（用户消息重生成需确认） | 不适用：知芽树状模型重生成不截断兄弟分支，破坏性不存在 | reject：模型不同，无此破坏面 | — | — | — |
| 2.6 | 生成中禁止删除/重命名会话 | `ChatVM.kt`（生成中删除被阻止） | 归档已声明不取消生成（`Branch.java` archive 注释）；删除前置检查 UNKNOWN | adapt：列入分支规格 S1 待核对项 | P1 | S1 拍板后 | [DUP] 生成中删除被阻止并提示 |

## 3. 输入与草稿

| # | 能力 | RikkaHub 事实（出处） | 知芽现状（证据） | 决策 | 优先级 | 依赖 | 异常语义 |
|---|---|---|---|---|---|---|---|
| 3.1 | 多行输入 + IME 安全区适配 | `ChatInput.kt`（最多 5 行） | 已有 IME 适配（T-063 ime-optout-and-resize 交付） | adopt：076 按 073 规格增强 | P0 | 076 | 旋转/进程重建不丢草稿 |
| 3.2 | 草稿保留与恢复 | `ChatInput.kt` + ViewModel 持有 | `ChatViewState.draft` 内存持有；进程级持久化 UNKNOWN | adopt：补持久化与恢复 | P0 | 076 | [RECOVER] 重建后草稿回填 |
| 3.3 | 引用上下文行（引用展示+清除） | `ChatInput.kt` 编辑横条 | 已有 `chat_quote_row/quote_text/quote_clear`（T-065 交付） | 已覆盖：076 仅做视觉对齐 | P0 | 076 | [CANCEL] 清除引用不丢草稿 |
| 3.4 | 发送键三态（禁用/发送/停止） | `ChatInput.kt` | 仅发送+禁用（`canSubmit()`，`ChatViewState.java` L148），无停止态 | adopt：P0 | P0 | 076 | [NOKEY] 无 Key 时禁用+引导 |
| 3.5 | 粘贴长文本自动转附件 / 图片粘贴 | `ChatInput.kt` 粘贴处理 | 无 | defer：随附件体系延后（用户已定） | P2 | 附件任务 | [PERM] 权限拒绝降级为纯文本 |
| 3.6 | 快捷短语 / 输入补全 | `ChatInput.kt` | 无 | defer：低优先 | P2 | — | — |
| 3.7 | 全屏编辑器 | `ChatInput.kt` 全屏入口 | 无 | adapt：低成本，列第一批备选 | P1 | 076（可选） | 旋转保存草稿 |

## 4. 模型 / 助手

| # | 能力 | RikkaHub 事实（出处） | 知芽现状（证据） | 决策 | 优先级 | 依赖 | 异常语义 |
|---|---|---|---|---|---|---|---|
| 4.1 | 模型/思考开关入口 | `ChatInput.kt` 模型选择、思考强度 | 已有 `chat_model_chip` + `ModelThinkingPicker.java` + `thinkingEnabled` | 已覆盖：076 视觉对齐 | P0 | 076 | [NOKEY] 入口降级 |
| 4.2 | 多 Provider（OpenAI/Claude/Google） | `ai/.../provider/providers/` | 硬约束 DeepSeek-only（project-brief） | reject：与约束直接冲突 | — | — | — |
| 4.3 | 助手/人设列表管理（搜索/拖拽/复制） | `ui/pages/assistant/AssistantPage.kt` | 已交付内置人设（T-034/036）；自建人设 INTENT 未收敛 | defer：等 INTENT-…-user-authored-persona 收敛 | P2 | 人设任务 | [DUP] 重名去重 |
| 4.4 | 自定义请求头/请求体 | README Features | 无，且密钥边界禁止明文透传 | reject：与本地安全边界冲突 | — | — | — |
| 4.5 | Prompt 变量系统 | README Features（时间/模型名变量） | 提示词走版本化策略（T-020） | reject：与现有 prompt 契约冲突，收益低 | — | — | — |

## 5. 会话管理

| # | 能力 | RikkaHub 事实（出处） | 知芽现状（证据） | 决策 | 优先级 | 依赖 | 异常语义 |
|---|---|---|---|---|---|---|---|
| 5.1 | 历史列表左滑删除 + 撤销 Snackbar | `ui/pages/history/HistoryPage.kt` | LibraryScreen 已有归档/子树删除（T-053/054），撤销交互 UNKNOWN | adapt：补撤销 Snackbar | P1 | 独立小任务 | [DUP] 误删可撤销；[RECOVER] 撤销恢复子树 |
| 5.2 | 置顶 | 同上 | 无 | defer：价值待用户确认 | P2 | — | — |
| 5.3 | 收藏页 | `FavoritePage.kt` | 无 | defer（同 1.7） | P2 | — | [RECOVER] 来源失效降级 |
| 5.4 | 备份/恢复 | `ui/pages/backup/` | 无（本地优先但无导出） | defer：单机数据安全有价值，范围大 | P2 | 独立任务 | [PART] 中断保留备份文件 |
| 5.5 | 按助手过滤的会话抽屉 | `ChatDrawer.kt` | 知芽导航为树画布+库，模型不同 | reject：与知芽 IA 冲突（T-051 V3.2 信息架构） | — | — | — |
| 5.6 | 会话内全局消息搜索 | `ui/pages/search/` | 已有库内搜索（T-054） | 已覆盖 | — | — | — |

## 6. 附件 / 语音

| # | 能力 | RikkaHub 事实（出处） | 知芽现状（证据） | 决策 | 优先级 | 依赖 | 异常语义 |
|---|---|---|---|---|---|---|---|
| 6.1 | 多模态输入（图片/PDF/Docx） | README Features、`data/files/` | 无 | defer：用户已明示延后（权限+文件生命周期） | P2 | 附件任务 | [PERM] 拒绝降级；[PART] 部分上传失败 |
| 6.2 | 语音输入 / TTS | `ChatInput.kt` 语音入口、speech 能力 | 无 | defer：同上 | P2 | — | [PERM] 麦克风拒绝降级 |
| 6.3 | 附件孤儿清理 | `ConversationRepository.kt` 全分支引用扫描 | 无附件体系 | defer：随附件体系 | P2 | — | — |

## 7. 主题 / 无障碍

| # | 能力 | RikkaHub 事实（出处） | 知芽现状（证据） | 决策 | 优先级 | 依赖 | 异常语义 |
|---|---|---|---|---|---|---|---|
| 7.1 | 低动效模式（系统减少动效时静态化） | README（动画开关）；`ChatList.kt` | 无系统低动效联动（UNKNOWN） | adopt：P0，075 内实现 | P0 | 075 | 全动效可静态降级 |
| 7.2 | TalkBack 标签与状态播报 | 各组件 contentDescription | 已有部分标签（UNKNOWN 覆盖面） | adopt：074–076 各自覆盖 | P0 | 074–076 | — |
| 7.3 | Material You 动态取色 | README Features、`ui/theme/` | 知芽有自己的「生长绿」token 体系（tokens.json，含 parity guard） | reject：与知芽品牌 token 冲突 | — | — | — |
| 7.4 | 深色模式 | README Features、`ui/theme/` | 无；073 non-goal 明确本轮不做 | defer：用户拍板后另立任务 | P2 | 独立任务 | — |
| 7.5 | 触达 ≥44dp | — | 已是知芽硬约束（project-brief） | 已覆盖 | — | — | — |

## 8. 大屏适配

| # | 能力 | RikkaHub 事实（出处） | 知芽现状（证据） | 决策 | 优先级 | 依赖 | 异常语义 |
|---|---|---|---|---|---|---|---|
| 8.1 | 平板/横屏布局 | `ui/pages/` 双栏模式（README desktop 图） | 已有 `layout-sw600dp/screen_chat.xml` | 已覆盖：073 核对对话页横屏映射 | P0 | 073/074–076 | 旋转不丢状态 |
| 8.2 | Web 桌面端 | `web/`、`ui/pages/webview/` | 无服务端（硬约束） | reject：与无服务端约束冲突 | — | — | — |
| 8.3 | 音量键翻页 | `ChatPage.kt`（可设比例） | 无 | adapt：长阅读场景契合，低成本 | P2 | — | — |

## 9. 数量回读（验收用，从上表实数）

- adopt：2.1 停止生成、3.1 IME 增强、3.2 草稿持久化、3.4 发送键三态、7.1 低动效、7.2 TalkBack —— **6 项**。
- adapt：1.1、1.2、1.4、1.5、1.6、2.2、2.3、2.6、3.7、5.1、8.3 —— **11 项**（其中已覆盖仅对齐的：3.3、4.1、5.6、8.1 —— 4 项，不计入 adapt）。
- defer：1.3、1.7、1.9、2.4、3.5、3.6、4.3、5.2、5.3、5.4、6.1、6.2、6.3、7.4 —— **14 项**。
- reject：1.8、2.5、4.2、4.4、4.5、5.5、7.3、8.2 —— **8 项**。

## 10. 第一批实现映射（与 074–076 对应）

- **074（消息呈现与动作）**：1.1、1.2（基础）、1.5、1.6。
- **075（生成态与滚动）**：2.1、2.3、1.4（计时）、7.1、滚动四态（跟随/让位/恢复/新消息提示，075 专属）。
- **076（输入栏）**：3.1、3.2、3.3、3.4、3.7（可选）、4.1。
- **不进入第一批**（须用户另行拍板才立项）：2.2 的 `:ai` 网关改动（涉及网络层，先核对现状）、2.6（依赖 S1 拍板）、5.1、以及全部 defer 项。

## 11. 用户确认区（review 时逐项拍板）

1. 第 9 节的 adopt 清单是否照单进入 074–076（建议：照单，理由：全部落在对话页主流程且不扩依赖；代价：无）。
2. defer 池里 5.4 备份/恢复是否提前（建议：不提前，理由：单机数据量小、schema 未稳定；代价：数据丢失风险自担）。
3. 2.2 自动重试是否随第一批进入 `:ai` 网关（建议：不随，理由：网络层改动应与 SSE 规格核对一起做独立任务；代价：第一批弱网体验无自动重试）。
4. 7.4 深色模式（建议：维持 defer，理由：token 体系单主题先稳；代价：夜间阅读体验缺）。

## 12. 出处索引

- RikkaHub：README Features/Development 节；`D:\Project\Android\rikkahub-master\app\src\main\java\me\rerere\rikkahub\`（ui/pages/**、ui/components/**、service/**、data/**）与 `ai/src/main/java/me/rerere/ai/**`。
- 知芽现状（2026-09-23 逐项核对）：`android/app/src/main/java/com/treelearn/app/ui/ChatViewState.java`、`ChatActions.java`、`ChatMessage.java`、`android/app/src/main/res/layout/screen_chat_body.xml`、`item_chat_message.xml`、`android/core/src/main/java/com/treelearn/core/domain/Branch.java`、`Docs/04-ux-design/design-system/tokens.json`。
- 关联规格：T-068 交互基准笔记、T-069 SSE 规格、T-070 分支规格。
