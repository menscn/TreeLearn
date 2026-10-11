# 知芽 Web 本地版：功能一致性矩阵

日期：2026-10-10。状态：开发验收输入；源码盘点不等于两端运行通过。

用户确认：Web 与手机的数据不同步；功能规则一致，入口和交互按 Web 改造。A 专注阅读默认，配色使用独立 Web 薄荷雾面 token，图标使用 v1.1 原型。

## 口径

- Android 基准优先级：活动装配/生产调用链 → 活动领域/DAO/配置 → 有效规格。旧任务自述、HTML 示例和历史吸收矩阵只能补充。
- 下表“必须一致”是最终产品验收；当前 HTML 用于展示工作区和代表性流程，不是功能全量实现。
- 每一行后续要补 Android 对照、Web 运行、正常场景、失败/重建场景四类结果；未取证保留未验证，不能以页面存在代替通过。
- Web 的具体状态枚举/路由/DTO 以正式唯一契约为准，不把本矩阵复制为第二份接口规范。

## 主题、对话与生成

| 项 | 必须一致的行为 | Web 呈现 | 活动来源 | 验收重点 |
|---|---|---|---|---|
| 主题创建 | 无 Key 不创建空主题；首问按真实接受路径提交 | 主页首问输入；配置后返回草稿 | [HomeWiring](../../android/app/src/main/java/com/treelearn/app/wiring/HomeWiring.java)、[GenerationCoordinator](../../android/app/src/main/java/com/treelearn/app/generation/GenerationCoordinator.java) | 无 Key、网络拒绝、接受后持久化失败、重复点击 |
| 连续追问 | 只向当前分支添加对话，普通追问不增枝 | 主输入栏与明确当前分支标题 | [ChatWiring](../../android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java)、[ChatCommands](../../android/app/src/main/java/com/treelearn/app/ui/ChatCommands.java) | 不混入兄弟历史；切分支不覆盖草稿 |
| 稳定回答分叉 | 仅稳定、可用的回答版本可分叉；冻结创建快照 | 整条动作和选文浮条 | [Branch](../../web/shared/domain/src/main/java/com/treelearn/core/domain/Branch.java)、[SqliteChatStore](../../android/app/src/main/java/com/treelearn/app/wiring/SqliteChatStore.java) | 部分/失败回答拒绝；多次创建不同分支；快照不可变 |
| 来源回溯 | 返回原来源并定位；版本变化不改写子分支快照 | 面包屑、来源侧栏、B 对照栏 | [ChatPresenter](../../android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java)、[NavigationPresenter](../../android/app/src/main/java/com/treelearn/app/navigation/NavigationPresenter.java) | 来源变化/删除可读降级；阅读位置保留 |
| 生成状态 | 正常终止且内容非空才稳定；失败/中断/取消区分 | 状态文案、停止与原位重试 | [GenerationOperation](../../web/shared/domain/src/main/java/com/treelearn/core/domain/GenerationOperation.java)、[GenerationCoordinator](../../android/app/src/main/java/com/treelearn/app/generation/GenerationCoordinator.java) | 空回答、推理无回答、畸形流、晚到回调 |
| 并发与调度 | 单分支活跃操作约束；沿用既有队列/配额规则 | 正在生成时抑制重复命令 | [GenerationScheduler](../../android/app/src/main/java/com/treelearn/app/generation/GenerationScheduler.java)、[LiveAttempts](../../android/app/src/main/java/com/treelearn/app/generation/LiveAttempts.java) | 多标签页竞争、同操作取消/完成竞态 |
| 停止生成 | 已收到部分内容保留；终态不被旧回调覆盖 | 发送按钮切换停止 | [ChatActions](../../android/app/src/main/java/com/treelearn/app/ui/ChatActions.java)、GenerationCoordinator | 停止前无增量、停止后晚到 delta/complete |
| 原位重试与重置 | 使用既有位置与版本规则，不能多插一个重复问答 | 原消息动作、重生成确认 | ChatPresenter、[ChatStore](../../android/app/src/main/java/com/treelearn/app/wiring/ChatStore.java) | 连点、刷新后重复、旧版本仍可追溯 |
| 回答版本 | 稳定版本不可变；显示选择与子分支来源独立 | 版本切换器 | [AnswerRevision](../../web/shared/domain/src/main/java/com/treelearn/core/domain/AnswerRevision.java)、[AnswerRevisionDao](../../android/data/src/main/java/com/treelearn/data/db/dao/AnswerRevisionDao.java) | 越界/不存在版本；分支仍引用出生版本 |
| 思考过程 | 对应请求开关、披露与持久化沿用当前行为 | 折叠块与输入栏思考开关 | ChatActions、[PromptBodiesV1](../../android/ai/src/main/java/com/treelearn/ai/prompt/PromptBodiesV1.java) | reasoning 与 answer 分离；失败仍可读已收内容 |
| 上下文压缩 | 压缩派生用途与教学回答隔离；失败不能静默丢内容 | 有状态说明与恢复动作 | [DerivedService](../../android/app/src/main/java/com/treelearn/app/artifact/DerivedService.java)、[PromptPurpose](../../web/shared/domain/src/main/java/com/treelearn/core/domain/PromptPurpose.java) | 压缩失败、旧结果 stale、预算边界 |
| 提示词策略 | 同用途、同输入的判断收敛到共享规则 | 固定教学行为，不新增任意模板编辑器 | [PromptSpec](../../web/shared/domain/src/main/java/com/treelearn/core/domain/PromptSpec.java)、PromptPurpose | 用途/版本 mismatch、畸形调用参数 |

## 阅读与知识树

| 项 | 必须一致的行为 | Web 呈现 | 活动来源 | 验收重点 |
|---|---|---|---|---|
| Markdown、代码、表格、公式 | 当前格式范围及不支持内容的可读降级 | 统一消息渲染器，代码复制，表格单元格 | [MarkdownRenderer](../../android/app/src/main/java/com/treelearn/app/screen/MarkdownRenderer.java)、[MathSpan](../../android/app/src/main/java/com/treelearn/app/ui/math/MathSpan.java) | 流式未闭合语法；安全链接；公式失败保留原文；XSS |
| 复制与引用 | 整条与选区是不同动作；引用清除不丢草稿 | 动作行、选文浮条、引用 chip | ChatActions、ChatPresenter | 部分选区复制；Clipboard 拒绝；中文/emoji |
| 推荐问题 | 普通跟进与推荐分叉分别执行；不清掉无关草稿 | 问题行及独立分叉按钮 | ChatActions、ChatPresenter、DerivedService | 普通跟进不增枝；分叉目标与版本正确 |
| 高亮 | 同容器相交或相邻区间按共用领域规则合并；精确范围可取消 | 选文浮条和已高亮区菜单 | [Annotation](../../web/shared/domain/src/main/java/com/treelearn/core/domain/Annotation.java)、[Highlighter](../../android/app/src/main/java/com/treelearn/app/ui/Highlighter.java) | 重复、相交、相邻、不同容器、失败回滚 |
| 批注 | 稳定内容写入、非空正文、内容身份一致 | 编辑弹层、批注侧栏 | Annotation、[AnnotationDao](../../android/data/src/main/java/com/treelearn/data/db/dao/AnnotationDao.java) | PROSE/CELL、UTF-16 区间、重复文本、digest 失配 |
| 批注失效 | 当前 DAO 的 digest 不匹配读取会清理对应记录；不能虚构自动重定位 | 明确失效反馈，移除已失效标记 | AnnotationDao.listFor | 内容变化、版本切换、并发读取；与 Android 判定一致 |
| 草稿与阅读位置 | 按分支保存，恢复来源与消息身份 | UI 切换后恢复；输入法安全 | [ChatDraftStore](../../android/app/src/main/java/com/treelearn/app/ui/ChatDraftStore.java)、[SqliteReadingStore](../../android/app/src/main/java/com/treelearn/app/reading/SqliteReadingStore.java) | 页面刷新、返回设置、长文定位、焦点重建 |
| 未读 | 基于用户实际看到的回答；折叠祖先汇总 | 蓝点与侧栏/树图汇总 | [UnreadRollup](../../web/shared/domain/src/main/java/com/treelearn/core/domain/UnreadRollup.java)、[ViewportSeen](../../web/shared/domain/src/main/java/com/treelearn/core/domain/ViewportSeen.java) | 多标签页读进度；不能打开页面就清全部未读 |
| 树导航与折叠 | 树关系不可被画布拖线改写；折叠状态按现有端口保存 | React Flow/SVG 树图、键盘、适应视口 | [TreeCanvasCommands](../../android/app/src/main/java/com/treelearn/app/ui/TreeCanvasCommands.java)、[TreeCanvasPresenter](../../android/app/src/main/java/com/treelearn/app/ui/TreeCanvasPresenter.java)、[TreeLayout](../../web/shared/tree-layout/src/main/java/com/treelearn/treelayout/TreeLayout.java) | 深/宽树、折叠后定位、归档隐藏、可见节点点击 |
| 归档与恢复 | 根分支不可归档；子树整体隐藏和恢复；归档不等于取消生成 | 分支菜单与学习库归档区 | [NavigationData](../../android/app/src/main/java/com/treelearn/app/navigation/NavigationData.java)、[LibraryPresenter](../../android/app/src/main/java/com/treelearn/app/library/LibraryPresenter.java) | 根拒绝、非根子树、生成中归档 |
| 删除 | 确认范围由树快照计算；按真实取消/删除顺序、事务执行 | 明示影响范围的确认层 | NavigationData、NavigationPresenter、LibraryPresenter | 删除计划过期、活跃生成收尾、事务失败不半删 |
| 应用内搜索 | 主题/分支/消息范围与来源定位保持一致 | 学习库统一搜索，Ctrl/Cmd K | [SearchPresenter](../../android/app/src/main/java/com/treelearn/app/search/SearchPresenter.java)、[SqliteSearchStore](../../android/app/src/main/java/com/treelearn/app/wiring/SqliteSearchStore.java) | 归档可达性、空查询、无结果、命中区间 |
| 学习统计 | 同一聚合口径，累计提问/消息/连续学习与当前月热力 | 学习足迹页，不改变统计定义 | [StatsDetailPresenter](../../android/app/src/main/java/com/treelearn/app/ui/StatsDetailPresenter.java)、[StatsAggregator](../../android/data/src/main/java/com/treelearn/data/stats/StatsAggregator.java) | 空月、跨日、设备时区、总量与明细同源 |

## 配置、平台能力与文件

| 项 | 必须一致的行为 | Web 呈现 | 活动来源 | 验收重点 |
|---|---|---|---|---|
| Provider/Key | 新增/编辑/启停/删除、脱敏 Key 与模型获取；仅 OpenAI 兼容范围 | 主从设置页、配置状态 | [ProviderAdminPresenter](../../android/app/src/main/java/com/treelearn/app/ui/ProviderAdminPresenter.java)、[SqliteProviderAdminStore](../../android/app/src/main/java/com/treelearn/app/wiring/SqliteProviderAdminStore.java) | 最后一个提供商警示、无 Key、畸形 URL、保存失败 |
| 模型分工 | CHAT/FAST/TRANSLATION；OFF/AUTO/LOW/MID/HIGH；建议开关 | 分工选择与上下文预算 | [ModelSettingsPresenter](../../android/app/src/main/java/com/treelearn/app/ui/ModelSettingsPresenter.java) | 不可用模型移除；模型获取只增不删/精确去重 |
| 模型回落 | 失效时内置默认 → 最早可用 → 空，发生时提示 | 回落提示与入口引导 | ModelSettingsPresenter.resolve、[多 Provider 规格](../spec/SPEC-2026-09-23-providers-and-models.md) | 配置回落不等于请求失败时偷偷换供应商 |
| 联网搜索 | 智谱/Tavily/博查、结果档位 THREE/FIVE/TEN、独立 Key 与调用开关 | 服务设置和输入栏按次开关 | [SearchSettingsPresenter](../../android/app/src/main/java/com/treelearn/app/ui/SearchSettingsPresenter.java)、[HttpWebSearchGateway](../../android/app/src/main/java/com/treelearn/app/search/web/HttpWebSearchGateway.java)、[ChatSearchStep](../../android/app/src/main/java/com/treelearn/app/search/web/ChatSearchStep.java) | 无 Key、请求失败、错误数据；不混淆应用内搜索 |
| 翻译与朗读 | 翻译用途隔离，原文可恢复；朗读开始/停止 | 消息动作与折叠翻译 | [MessageTranslator](../../android/app/src/main/java/com/treelearn/app/generation/MessageTranslator.java)、[ChatSpeaker](../../android/app/src/main/java/com/treelearn/app/ui/ChatSpeaker.java) | 翻译失败不改原文；切分支时停止旧朗读 |
| 语音输入 | 识别追加草稿、不自动发送、不覆盖已有文本 | 点击录音与确认，权限说明 | [SpeechInputPolicy](../../android/app/src/main/java/com/treelearn/app/ui/SpeechInputPolicy.java)、[PlatformSpeechInput](../../android/app/src/main/java/com/treelearn/app/speech/PlatformSpeechInput.java)、MainActivity | 浏览器权限拒绝/缺支持/取消；明确网络归属 |
| 附件注册与解析 | 内容去重、当前格式支持、失败与超限可感知 | 文件选择/拖入、附件 chip | [AttachmentStore](../../android/app/src/main/java/com/treelearn/app/files/AttachmentStore.java)、ChatPresenter | 重复字节、注册回滚、解析失败、移除不丢草稿 |
| 文件管理与清理 | 可删除被引用附件，引用保留缺失标记；孤儿清理幂等 | 状态列表、确认删除/清理 | AttachmentStore、[FileManagerPresenter](../../android/app/src/main/java/com/treelearn/app/ui/FileManagerPresenter.java) | 缺物理文件、残余引用、清理重跑、占用同源 |
| 设置/返回路径 | Key 入口与 Provider 等页面可达；返回原页面和草稿 | 辅助设置入口，不占一级内容导航 | [MainActivity](../../android/app/src/main/java/com/treelearn/app/MainActivity.java)、[设置入口最新决定](../04-ux-design/知芽-TreeLearn-设置入口与按钮反馈更新-2026-10-09.md) | 从主页/聊天/缺 Key 引导进入后各自返回 |

## 不从历史材料推导的功能

- 当前生产源码未核到可达的人设选择器；固定学习导师提示词属于生成基线。旧已接受人设意图是背景，不能在 Web 增加一个手机实际没有的选择器并宣称一致。
- 自定义用户人设、备份、同步、消息收藏、图片生成、任意 Agent/MCP/RAG、完整 Office/OCR 格式、新图表语法，不因开源参考项目具备就自动进入范围。
- 使用开源 UI 时只吸收交互/组件，不继承其账户、云存储、密钥明文、自由 DAG 或编辑连线规则。

## 原型与生产之间的明确差距

HTML 已提供代表性的页面导航、布局切换、对话/分叉、批注编辑、树导航、输入与模拟生成、归档/恢复/文件确认。版本、模型网络、真实搜索/语音、后台持久化、恢复、全量容器选区和格式解析仍是开发事项，不能把原型中显示的成功 Toast 当作生产证据。

后续功能验收按本矩阵逐行取运行证据。单独的 prototype 点击检查只证明设计交付能体验，不能把本表整体判为 PASS。
