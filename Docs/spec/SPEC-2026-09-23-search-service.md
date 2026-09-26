# Spec: 搜索服务（联网搜索增强）——设置页与对话集成

id: SPEC-2026-09-23-search-service
title: 搜索服务（联网搜索增强）——设置页与对话集成
date: 2026-09-23
intent: Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md
author: spec-author
status: accepted

## 事实 / 假设 / 未知（前置清单）

- 已确认事实：
  - RikkaHub 的搜索能力全部走搜索服务 API（Tavily、Bing、智谱、博查、秘塔、Exa、Brave 等 18 种），没有任何一种调用 Android 系统级网页搜索——出处 `D:\Project\Android\rikkahub-master\search\src\main\java\me\rerere\search\SearchService.kt`（`SearchServiceOptions.TYPES` 与 `getService` 分发，152-172 行、46-67 行）。
  - RikkaHub 搜索结果结构为 `SearchResult{answer?, items[{title,url,text,publishedDate?,highlights}], images, retrievedAt}`，通用参数只有 `resultSize`（默认 10）——同文件 96-117 行。
  - RikkaHub 的对话集成机制是 function-calling：注册 `search_web` 工具由模型自主调用，结果以工具回包注入，正文用 `[citation,domain](id)` 标注——`app\src\main\java\me\rerere\rikkahub\data\ai\tools\SearchTools.kt` 20-87 行、`data\ai\tools\ChatToolFactory.kt` 21-23 行。
  - 知芽 :ai 网关是 DeepSeek 兼容的 chat-SSE 请求，无 function-calling——`android\ai\src\main\java\com\treelearn\ai\prompt\DeepSeekRequest.java`、`gateway\DeepSeekGatewayFactory.java`。
  - 知芽上下文预算为字符预算：容量与最小答案空间由 `PromptLimits` 一处定义（60000 / 512），注入内容必须计入 `BuildContextPlanUseCase.Budget.reservedChars`——`android\app\src\main\java\com\treelearn\app\wiring\PromptLimits.java`、`android\core\src\main\java\com\treelearn\core\domain\BuildContextPlanUseCase.java` 21-30 行。
  - 知芽 fork 快照是纯引用列表（USER_MESSAGE / STABLE_REVISION / SUMMARY 三种 refId），不含内容——`android\core\src\main\java\com\treelearn\core\domain\ContextSnapshot.java` 29-79 行。
  - 知芽出处卡的既有形态：非根分支顶部只渲染的 summary + history 列表——`android\app\src\main\java\com\treelearn\app\ui\SourceCard.java` 7-16 行。
- 假设：
  - intent 中的「搜索调用本机的搜索」按「搜索服务 API 真实可调用」理解（intent 39 行原文要求规格阶段核对，见疑虑 1）。
  - 知芽侧对话集成采用「发送前检索 + 注入上下文」而非 RikkaHub 的 tool-calling（依据：本次规格任务指令给定该交互；且 :ai 网关无 function-calling，改造网关超出本期范围）。
- 未知项：
  - 用户手上已有哪家搜索服务的 Key（影响首批引擎的可用性验收）。
  - 引擎选择是全局一份还是允许多引擎配置多实例（本规格按单实例 + 单选引擎写，见疑虑 2 与疑虑 4）。

## Requirement spec（需求规格）

- 范围：
  - 搜索服务设置页：搜索引擎的增删与启停、每引擎的参数表单（API Key 等）、检索结果数设置；搜索能力默认关闭（intent「核心流程」的设置中心入口与「输入/输出」的搜索服务配置，对应移交清单中搜索 2 个实现任务）。
  - 对话集成：输入栏搜索开关、发送前检索、检索结果注入对话上下文、回答带来源标注（intent「Expected outcome」第③块「搜索调用真实搜索服务」+「核心流程」输出侧）。
  - 密钥管理口径沿用既有约束：Key 只进 Keystore（intent「Constraints」原文，落点为既有 `KeyVault` / `SettingsStore.saveKey` 链路）。
- 验收方向：
  - 能力真实：配置任一引擎的 Key 后，对话中开启搜索能拿到真实检索结果并出现在回答来源里（intent「Expected outcome」「能力真实优先于数量」）。
  - 异常全覆盖：无 Key 拦截、网络失败降级、空结果明确反馈，不允许半成品入口（intent「核心流程·状态与异常」原文）。
  - 验收方式：每个实现任务 acceptance + verifier 独立验收；真机体验由用户自测（intent「Decision record·验收标准与证据」）。
- 非目标：
  - MCP（intent「Constraints and non-goals」原文，RikkaHub 的 MCP 工具体系不搬）。
  - 自建索引、多引擎并发聚合、检索结果缓存（本规格按任务指令补充；依据 intent 权衡偏好「能力真实优先于数量」与「能力相互独立、可分期交付」，列为待拍板的补充非目标）。
- 来源：Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md 的 Problem、Expected outcome、Core flow、Constraints and non-goals、Decision record 各节；RikkaHub 事实源见前置清单出处。

## Interface plan（界面方案）

- 信息架构（对齐 RikkaHub 三层级，见 `app\src\main\java\me\rerere\rikkahub\ui\pages\setting\SettingSearchPage.kt` 与 `SettingSearchDetailPage.kt`）：
  - 第一层「搜索服务」列表页：引擎卡片（名称 + 启停开关 + 能力标注）纵向排列，卡片可编辑、可删除（保留至少一个引擎的约束照搬 RikkaHub：`canDelete = settings.searchServices.size > 1`）；页尾「通用参数」卡：检索结果数数字输入（对应 RikkaHub `SearchCommonOptions.resultSize`）。
  - 第二层「引擎详情」页：按引擎渲染参数表单——API Key 输入（掩码显示，走 Keystore 保存链路）、该引擎特有的选项（如 Tavily 的检索深度；RikkaHub 的字段清单见 `SearchService.kt` 175-290 行）。
  - 视觉遵循知芽 token 体系，不搬 RikkaHub 的 Compose Material 配色。
- 入口位置：
  - 设置中心列表新增「搜索服务」项，与提供商、模型、语音、文件管理并列（intent「核心流程」设置中心入口）。
  - 对话输入栏新增搜索开关 chip，与既有模型档位 chip、思考开关 chip 同排；开关状态是全局存储事实，输入栏与设置屏共享同一份（既有先例：`android\app\src\main\java\com\treelearn\app\ui\SettingsStore.java` 6-10 行「两个屏幕对同一存储事实永不各说各话」）。点击开关弹出底部面板：开启/关闭、当前引擎名与「去设置」入口（交互基准：RikkaHub `ui\components\ai\SearchPicker.kt` 92-170 行的按钮 + bottom sheet 形态）。
- 状态与异常（三态编号沿用任务指令）：
  - [NOKEY] 开启开关或发送时所选引擎无 Key：拦截该动作，提示「搜索服务未配置 Key」并给「去设置」直达入口；不进入生成。
  - [OFF] 检索失败或超时：降级为无搜索继续生成，消息流内以行内提示告知「联网检索失败，本次回答未联网」；不产生失败的消息行，草稿按既有「失败保留草稿」规则不受影响（`ChatPresenter.java` 37-38 行规则）。
  - [PART] 检索成功但结果为空：行内提示「未检索到相关结果」，继续无搜索生成。
  - 检索进行中：发送按钮区域显示「检索中」阶段态，此时不可重复提交（复用既有 `ChatViewState.SendState` / `sending` 的单飞约束，`ChatViewState.java` 75-89、114 行）。
- 数量与呈现方式：
  - 回答来源对齐既有 SourceCard 的只读卡片风格：surface 承载 + radius.r-md 圆角 + shadow.sh-1；折叠态显示来源域名 chips（fontSize.fs-cap，ink2），展开态逐条列出标题（fs-h2）、域名与摘要（fs-sm）；正文内的引用标记与来源卡条目按序号对应。
  - 提示与警示配色：[NOKEY] 拦截用 amber-soft 底 + amber 文字；[OFF] 降级提示用 red-soft 底 + red 文字；中性标注用 ink3。强调态（如开关选中）用 green / green-soft。
  - 间距：卡片内边距 space.s4，列表项间 space.s3，页面安全边距 gutter；标题层级 fs-h1（页面）/ fs-h2（卡片）。
- 来源：token 均出自 Docs/04-ux-design/design-system/tokens.json（color.surface、color.green、color.green-soft、color.ink2、color.ink3、color.amber、color.amber-soft、color.red、color.red-soft、space.s3、space.s4、space.gutter、radius.r-md、shadow.sh-1、fontSize.fs-h1、fontSize.fs-h2、fontSize.fs-sm、fontSize.fs-cap）；交互基准 Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md 未覆盖搜索节，本节交互直接以 RikkaHub 源码为准。

## Technical spec（技术规格）

- 触发时序（发送前串行检索，非流式中插）：
  - 发送路径：`ChatPresenter`（`android\app\src\main\java\com\treelearn\app\ui\ChatPresenter.java`）提交问题 → 后台 executor 上先执行检索步骤（阻塞步骤，串行于生成之前）→ 组装注入块 → 再走既有 `GenerationCoordinator` / `GenerationScheduler` 流式生成（`android\app\src\main\java\com\treelearn\app\generation\`）。
  - 检索只发一次、失败不重试重排生成流程；检索阶段的状态经 `ChatViewState` 阶段字段上屏（对应界面方案「检索中」态）。
  - 与 RikkaHub 的机制差异：RikkaHub 用 function-calling 让模型自主多次检索（`SearchTools.kt`），知芽网关无该能力，采用单次发送前检索；多次追问靠用户再次发送。
- 注入点与字符上限：
  - 注入点：作为本次请求的上下文前缀块拼入提示组装（不写进任何持久化消息行），内容为「检索指令框架 + 结果条目列表」。
  - 预算：注入块字符数计入 `BuildContextPlanUseCase.Budget.reservedChars`（该契约已明确「调用方义务」：reservedChars 必须覆盖 system prompt、当前问题、最小答案空间之外的一切附加内容，`BuildContextPlanUseCase.java` 21-30 行）；上限常量并入 `PromptLimits`（一处一个数字的既有纪律），不得挤占 `MIN_ANSWER_CHARS`；超限时报 `PromptBuildException` 走既有拒绝路径而不是静默截断（`android\ai\src\main\java\com\treelearn\ai\prompt\PromptBuildException.java`）。注入块自身上限的具体数值由实现任务在 `PromptLimits` 定值并配测试。
  - fork 快照体积保护：因为检索结果不落库也不进快照（见下），快照体积不受搜索开启影响。
- 字段清单（机制与数据，不含实现代码）：
  - 引擎配置：`SearchEngineConfig{engineType, keyAlias, enabled}`——engineType 取首批引擎枚举（见疑虑 2）；keyAlias 指向 Keystore 中的条目，明文 Key 只进 Keystore（intent 约束 + `KeyVault` 既有链路，`android\app\src\main\java\com\treelearn\app\ui\KeyVault.java`）；enabled 为启停开关。
  - 全局存储：`SettingsStore` 新增 `searchEnabled()` 存储事实与引擎选择/结果数读取口，模式照搬 `model()` / `thinkingEnabled()`（`SettingsStore.java` 14-20 行）。
  - 注入块结构：`{query, retrievedAt, items[{index, title, url, domain, snippet}]}`，条目数等于结果数设置（默认对齐 RikkaHub 的 10，`SearchService.kt` 96-98 行）。
  - 引用标记：回答正文内以「序号 + 域名」的文本标记出现（格式语义对齐 RikkaHub 的 `[citation,domain](id)`，`SearchTools.kt` 41-44 行；具体记号由实现任务定稿），标记是答案文本的一部分。
- 检索结果与创建快照的兼容性（分叉不丢来源）：
  - `ContextSnapshot` 只存 refId 引用、内容不可入库（`ContextSnapshot.java` 12-16 行），因此检索结果条目无法被快照引用。
  - 兼容口径：来源标注的「锚」放在答案文本内的引用标记里——标记随答案进入稳定修订（STABLE_REVISION）持久化，fork 后子分支的快照引用该修订，标记原样可见；来源详情（url、snippet）不恢复，子分支中引用标记降级为纯文本（不可展开详情），界面按 [PART] 的弱化样式呈现。
- 不落库口径：
  - 检索原始结果（title/url/snippet 全部条目）不进 SQLite：不加新表、不加来源列、不写 `context_snapshots`；SQLite schema（基座任务）无需为本能力做任何迁移。
  - 唯一持久化痕迹是答案文本里的引用标记（属答案内容，非检索结果）。
- 异常降级路径（与界面三态一一对应）：
  - [NOKEY]：发送前校验 keyAlias 是否有 Key，缺失即拦截，不发起任何网络请求。
  - [OFF]：检索异常或超时（超时上限并入 `PromptLimits` 同批定值）→ 记录一条行内提示状态，跳过注入块继续生成；生成请求的 reservedChars 按无注入口径计算。
  - [PART]：检索成功但 items 为空 → 记录行内提示，继续无搜索生成。
  - 三态都不得产生「有搜索开关痕迹但内容残缺」的半成品消息行（intent「状态与异常」禁半成品）。
- 引擎接入的传输口径：`DEFERRED:待 T-2026-09-23-085`（intent 移交清单 083-098 中的「网关抽象」基座任务；ID 按移交清单排列顺序对应，拆任务时以任务文件为准）。落地后要补：搜索引擎的 HTTP 传输是否复用 :ai 模块的 `HttpsTransport`（`android\ai\src\main\java\com\treelearn\ai\gateway\HttpsTransport.java`）与超时/重试口径，以及引擎实现放 :ai 模块还是 app 模块。
- 来源：intent「核心流程/Constraints/移交清单」；代码出处见上文各条内联路径。

## Doubts（疑虑点）

- 疑虑 1：「本机搜索」的语义核对。intent「Expected outcome」写「搜索调用本机的搜索」，而 Android 无系统级网页搜索 API；RikkaHub 实测全部走 Tavily/Bing/智谱等服务 API（`SearchService.kt` TYPES 清单），无一调用系统搜索。（冲突对象：intent 21 行 vs intent 39 行自身的假设标注；两种走法：A. 确认为「搜索服务 API 真实可调用」，本规格按此写；B. 用户另有含义（如应用内跳转浏览器搜索），则本规格范围重写。建议：A。理由：与 RikkaHub 实现一致、与「能力真实优先于数量」一致、无平台 API 可用。代价：若用户本意是 B，设置页与对话集成的规格作废重写。谁拍板：用户。）
- 疑虑 2：第一批引擎选型（建议 2 个）。RikkaHub 支持 18 种引擎；其中免 Key 的 Bing 本地依赖设备侧抓取、稳定性和合规存疑，建议排除。建议首批：智谱（国内直连、单 API Key、中文检索质量好）+ Tavily（国际通用、单 API Key、RikkaHub 中参数最简且有 advanced 深度选项）。备选：博查（国内、Tavily 同构）。理由：两个引擎都只需一个 Key 字段，接入难度最低，且覆盖国内外两种使用场景。代价：每多一个引擎多一份表单、一组解析测试与一条真实 Key 的验收；只做一个引擎则国内/国际场景缺一边。（冲突对象：intent「Expected outcome」未指定引擎；两种走法：A. 智谱+Tavily 双引擎；B. 先只做智谱单引擎，Tavily 二期。谁拍板：用户。）
- 疑虑 3：检索结果是否随消息持久化。建议：不落库，仅答案文本内的引用标记随答案持久化。理由：ContextSnapshot 的结构决定了内容不可能进快照（`ContextSnapshot.java` 12-16 行）；检索结果有时效性，落库会让历史消息携带过期快照式的伪来源；不落库则 SQLite 零迁移。代价：fork 与历史回看时来源只剩域名标记，点不开详情——这是明确的降级而非缺陷，界面已按此设计。（冲突对象：本规格 vs「分叉不丢来源」的理想体验；两种走法：A. 不落库（本规格口径）；B. 新增来源表并扩 ContextSnapshot.SourceKind——违反快照「引用而非内容」的结构决策，且需 schema 迁移。谁拍板：用户。）
- 疑虑 4：输入栏触发交互形态。RikkaHub 是点击弹底部面板选模式/引擎（`SearchPicker.kt` 92-170 行），知芽输入栏现状是 chip 直接切换（模型档位、思考开关）。建议：搜索 chip 点击弹底部面板（照搬 RikkaHub），面板内含开关、当前引擎与「去设置」。理由：intent 要求「界面交互照搬 RikkaHub」；搜索有引擎选择与未配置 Key 两个需要解释空间的次级状态，单次点击 toggle 表达不了。代价：新增一个 bottom sheet 组件（约一个 UI 任务的量）；比 chip 直切多一步操作。（冲突对象：intent「界面交互照搬」vs 既有 chip 直切的轻量先例；两种走法：A. 弹底部面板（本建议）；B. chip 直切 + 长按进设置。谁拍板：用户。）

### 拍板记录（2026-09-23 08:05 用户回复「按照建议采纳」）

- 疑虑 1 **采纳建议 A**：「本机搜索」确认为「搜索服务 API 真实可调用」（与 RikkaHub 实现一致，Android 无系统级网页搜索 API）。
- 疑虑 2 **采纳建议 A**：首批智谱 + Tavily 双引擎，博查为备选，Bing 本地排除。
- 疑虑 3 **采纳建议 A**：检索结果不落库，仅答案文本内的引用标记随答案持久化；fork 与历史回看来源降级为域名标记。
- 疑虑 4 **采纳建议 A**：输入栏搜索 chip 点击弹底部面板（面板含开关、当前引擎与「去设置」），照搬 RikkaHub。

据此本规格四项拍板点全部收敛，status 翻 accepted。
