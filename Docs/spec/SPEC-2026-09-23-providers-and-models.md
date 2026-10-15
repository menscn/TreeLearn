# Spec: 提供商管理与模型设置（多供应商 OpenAI 兼容接入 + 模型三分工）

id: SPEC-2026-09-23-providers-and-models
title: 提供商管理与模型设置（多供应商 OpenAI 兼容接入 + 聊天/快速/翻译模型分工）
date: 2026-09-23
intent: Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md
author: spec-author
status: accepted

## Requirement spec（需求规格）

### 范围

对应 intent 的 Expected outcome（五块能力）中第①②块与 Core flow 的「提供商增删改/启停 → 模型分工选择 → 对话使用所选模型」链路：

1. **提供商管理页（两屏之一）**：
   - 列表：按新增时间展示已配置提供商；每项含名称、启用开关、模型数徽标；点击进入编辑。
   - 新建：弹出表单，预置「OpenAI 兼容」模板（名称、基址、Key 三字段起底），对齐 RikkaHub 新增即默认 OpenAI 兼容型的做法（出处见 Technical spec 的事实索引 F1）。
   - 编辑：改名、改基址、替换/删除 Key（掩码展示沿用现设置页语义）。
   - 删除：先弹确认，删除不可恢复；删除对象是「最后一个启用中的提供商」时给额外警示（见 Interface plan 状态与异常）。
   - 拉取模型：在编辑页按当前基址与 Key 拉取该提供商的可用模型清单并合并入本地列表。
2. **模型设置页（两屏之二）**：
   - 模型三分工：**聊天模型**（教学回答主链路）、**快速模型**（标题/摘要/派生杂活）、**翻译模型**（翻译用途）各一行，显示当前所选模型，点开选择器更换。
   - 思考预算：随聊天模型一行（RikkaHub 是随快速模型一行，出处 F4；知芽放在聊天模型侧，因为现有思考开关本就绑定回答生成），提供离散档位。
   - 聊天建议开关：控制回答下方推荐问题是否生成；与现有 Recommendation 体系的关系列入拍板区疑虑 ③。

### 验收方向

- 可新增一个 OpenAI 兼容提供商、填基址与 Key、拉取到模型清单、启用后能在对话页选中其模型完成一次真实生成。
- 启用/禁用即时生效：禁用后其模型不再出现在任何分工选择器中。
- 删除提供商有确认；被删除提供商上的分工选择自动回落并提示（规则见 Technical spec）。
- 无任何可用 Key 时，发送被 Key 守卫拦截并引导，不产生半成品入口（intent 的状态与异常条款）。
- 拉取模型失败可重试、不影响已存模型。

### 非目标

- 自定义请求头/请求体（矩阵 4.4 reject 维持，intent 非目标亦列明）。
- MCP、Agent 工作区（intent 非目标）。
- Gemini/Anthropic 原生协议：**暂缓**，非永久拒绝；先做 OpenAI 兼容协议（理由见疑虑 ①）。
- 余额查询、二维码导入/导出、提供商拖拽排序、连接测试器：RikkaHub 有（出处 F3），本期裁剪，需要时另行立项。
- 搜索/语音/文件管理三块：归 intent 移交清单的另外三份规格（079–082 切片中的 080/081/082）。

### 基线变更记录

「只用 DeepSeek」是现行冻结基准的硬约束，本规格在其之上做受控变更，逐处记录：

1. **V3.0 条款原文**（`Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md`）：
   - 第 87 行（技术/业务约束）：「用户自配 DeepSeek API Key，首版仅支持 DeepSeek API」。
   - 第 90 行（依赖项）：「用户提供可用且有额度的 DeepSeek API Key……未指定其他模型供应商或备用服务」。
2. **V3.1 对应条款**（`Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md`）：
   - BASE-23 第 31 行：「首版模型范围严格限定为 DeepSeek」。
   - 第 76 行（非目标区）：「不引入非 DeepSeek 模型供应商」。
3. **V3.3 增量建议稿要点**（尚未成文；`Docs/01-demand/` 现存仅 V3.0 与 V3.1，成文回写不在本角色权限内，要点如下供后续回写）：
   - 修订 V3.0 第 87 行：「首版仅支持 DeepSeek API」→「支持 OpenAI 兼容协议的多供应商接入，预置 DeepSeek」。
   - 废止 V3.1 第 76 行非目标；BASE-23 的「模型范围限定」改写为「提供商可配置，思考模式开关每次请求仍显式传」。
   - 新增一条 BASE 条目登记「提供商管理与模型分工」（以本规格的 Requirement spec 为正文来源）。
4. **project-brief.md 回写清单**（`.trae/context/project-brief.md`）：
   - 第 7 行：「业务数据与 DeepSeek Key 都只留在本机」→ 改「业务数据与各家提供商 Key 都只留在本机」。
   - 第 17 行：「网络与模型：……只用 DeepSeek，思考模式开关每次请求显式传」→ 改「……OpenAI 兼容多供应商，思考模式开关每次请求显式传」。
   - 第 41 行是提示词设计文档的索引标题，含「DeepSeek」字样但非约束，不改。
5. **矩阵决策推翻记录**（`Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md`）：
   - 4.2「多 Provider → reject：与约束直接冲突」：被用户 2026-09-23 指令推翻（intent 的 Problem 与 Convergence verdict 记录了推翻过程），本规格承接多 Provider 吸收。
   - 1.8「消息级翻译 / TTS → reject」：部分推翻——消息级翻译由本规格的「翻译模型」分工承接；TTS 朗读不随本规格，归语音规格（intent 移交清单 081）。

### 事实 / 假设 / 未知（三分）

- **已确认事实**：intent 已 accepted 且用户指令「五个全部搬过来」（intent Open questions 已确认条）；RikkaHub 提供商/模型交互机制（Technical spec 事实索引 F1–F8，均带仓库内路径）；知芽网关、Key 守卫、设置页现状（Technical spec 各条所引代码路径）。
- **假设**：用户所说目标服务商（DeepSeek、硅基流动、AiHubMix 等）均为 OpenAI 兼容协议，故「OpenAI 兼容先行」可覆盖绝大多数接入场景（依据见疑虑 ①，规格阶段不验证每家的文档）。
- **未知项**：思考预算档位到具体参数的最终映射（疑虑 ②，保持未知直至拍板）；聊天建议开关与 Recommendation 的最终关系（疑虑 ③）。未知项在后续会话继续标为未知，不悄悄升级为事实。

- 来源：intent 的 Problem / Expected outcome / Core flow / Constraints and non-goals / Decision record 各节；基线条款见上文逐处行号；RikkaHub 出处见 Technical spec 事实索引。

## Interface plan（界面方案）

### 信息架构

- **现设置页**（`android/app/src/main/res/layout/screen_settings_body.xml`，现为 Key 卡片 + 边界说明）：在 Key 卡片之下、「边界说明」之上插入两个入口行——「提供商管理」（副标题显示「已启用 N 家」）与「模型设置」（副标题显示当前聊天模型名）。原 Key 卡片保留不动：它仍是「当前默认提供商的 Key」的快捷入口，语义收窄由 T-084 的迁移一并处理。
- **提供商管理屏**（新 Screen，竖屏单列，沿用 ScreenController/Presenter + 不可变 ViewState 模式）：
  - 顶栏：返回 + 标题 + 「新建」按钮。
  - 列表项卡片：提供商名（fs-h2）、启用状态开关、模型数徽标（pill 形态，复用现设置页 Key 状态 pill 的结构）；禁用项整卡弱化（ink3 文字 + 弱化底）。
  - 空态：无任何提供商时显示引导文案 + 新建按钮。
  - 点卡片进入**提供商编辑屏**：名称、基址、Key（掩码 + 替换/删除，沿用现 Key 输入的交互与 Notice 语义）、「拉取模型」按钮、已选模型列表（逐项可删）、底部「删除提供商」（红色，先确认）。
- **模型设置屏**（新 Screen）：三个分工行（聊天模型/快速模型/翻译模型），每行显示当前所选模型名；聊天模型行下挂「思考预算」行（离散档位）；「聊天建议」行带开关。

### 入口位置

统一从设置 tab 的 `screen_settings_body.xml` 进入；对话页现有 `chat_model_chip`（ModelThinkingPicker）不变，它继续是「对话前最后一次调整」的入口。

### 状态与异常

- **[NOKEY] 无 Key 降级**：现 Key 守卫（领域词表「Key 守卫」条）从「单一 Key 是否配置」扩展为「所选分工模型所属提供商是否有 Key 且启用」。判定口径沿用 `KeyVault.isConfigured()` 的布尔语义，但按提供商逐个判定（KeyVault 契约见 `android/app/src/main/java/com/treelearn/app/ui/KeyVault.java`）。无可用提供商时：发送拦截 + 引导跳提供商管理屏；新建主题仍守创建原子性（领域词表）。
- **[OFF] 拉取失败**：拉取模型网络失败时在编辑屏内联提示（amber 警示色语义），保留已存模型，「重试」按钮同位可再点；不弹全屏错误。
- **[DUP] 重名校验**：新建/改名时提供商名不允许与现有项重名（含空白裁剪后比较），重名时表单内联提示并阻止保存；同一提供商下模型 ID 重复项在拉取合并时静默去重（规则见 Technical spec）。
- **删除最后一个启用提供商**：确认弹层文案升级为警示级（说明删除后将无可用模型，发送会被守卫拦截），确认按钮用警示色；删除非最后一个提供商用普通确认文案。

### 数量与呈现方式

- 模型数徽标：列表项右侧 pill，数值来自该提供商本地模型表计数；颜色用 green-soft 底 + green 文字的语义对（启用态）或 ink3/line（禁用态）。
- 模型选择器：**下拉/弹层复用 `ModelThinkingPicker` 的模式**——chip/行点击弹出锚定 PopupWindow，空间不足向上翻转，行尾勾选标记（`android/app/src/main/java/com/treelearn/app/screen/ModelThinkingPicker.java` 第 74–138 行的交互骨架）；弹层内容改为「按提供商分组的模型列表」。
- 思考预算档位：五档离散（关/自动/低/中/高）；RikkaHub 有七档（出处 F5），知芽裁剪掉超出常用区间的两档，档位名走字符串资源不写数值。
- 思考预算行与聊天建议开关的卡片结构沿用现设置页 Key 卡片的圆角卡片形态。

### 来源（token）

- 界面数值只引用 `Docs/04-ux-design/design-system/tokens.json` 已定义的 token 名：颜色 `bg` `surface` `ink` `ink2` `ink3` `line` `hairline` `green` `green-deep` `green-soft` `green-faint` `amber` `amber-soft` `red` `red-soft`；间距 `space/s1` `space/s2` `space/s3` `space/s4` `space/s6` `space/s8` `space/gutter`；圆角 `radius/r-sm` `radius/r-md` `radius/r-lg` `radius/r-full`；字号 `fontSize/fs-h1` `fontSize/fs-h2` `fontSize/fs-body` `fontSize/fs-sm` `fontSize/fs-cap`；阴影 `shadow/sh-1` `shadow/sh-2`。触屏热区沿用 project-brief 的 44dp 硬约束（dp 值属工程约束，非本节新造数值）。

## Technical spec（技术规格）

### RikkaHub 事实索引（clean-room：只写机制 + 路径出处，仓库根 `D:\Project\Android\rikkahub-master`）

- **F1** 提供商列表：搜索过滤、列表项启用/禁用标签与模型数标签、新增弹表单（新增默认 OpenAI 兼容型）——`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt`（第 97–114、125–147、537–585、629–643 行）。
- **F2** 数据模型：sealed 三型（OpenAI/Google/Claude），OpenAI 型字段含 apiKey、baseUrl、请求路径等——`ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`（第 26–69 行）。
- **F3** 详情页：配置/模型两 tab、连接测试、非内置才可删、删除有确认弹层、保存时裁剪名称——`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt`（第 197–222、296–331、344–370 行）。
- **F4** 模型设置页：聊天/快速/翻译等分工行 + 快速模型行挂思考档位 + 聊天建议开关 + 选择器为底部弹层——`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPage.kt`（第 103–165、167–185、249 行）。
- **F5** 思考档位枚举：关/自动/低/中/高/超高/最大七档，token 预算仅为档位标签，effort 字符串才是发给 API 的值——`ai/src/main/java/me/rerere/ai/core/Reasoning.kt`（第 7–30 行）。
- **F6** 思考档位 → API 参数按 host 分派：DeepSeek 官方域名发 thinking 开关对象 + reasoning_effort（中/高档归并为高，最大档发 max）；未知 host 缺省发 reasoning_effort；硅基流动发布尔开关——`ai/src/main/java/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt`（第 271–416 行）。
- **F7** 模型列表拉取：对 `{基址}/models` 发 GET，Bearer 鉴权头，解析 `data[].id`，失败即空——`ai/src/main/java/me/rerere/ai/provider/providers/openai/OpenAIProvider.kt`（第 58–85 行）。
- **F8** 模型合并去重：「全选」合并时按模型 ID 精确比对、已有项跳过——`SettingProviderDetailPage.kt`（第 715–728 行）。

### 接口（OpenAI 兼容协议抽象）

- 请求基址：从 `GatewayEndpoints` 的单一硬编码常量（`android/ai/src/main/java/com/treelearn/ai/gateway/GatewayEndpoints.java` 第 15–17 行）参数化为「随提供商记录携带」，`requireHttps` 的强制 https 语义保留并适用于每个基址。
- 模型 ID：请求体 `model` 字段现取 `GenerationModel.wireId`（`JsonChatRequestBody.java` 第 41 行），参数化为「所选 model 记录的 wire ID」，不再限定于内置两档枚举。
- 鉴权头注入点：`HttpsTransport` 的 `Authorization: Bearer`（第 55 行）不变，Key 值改由「所选提供商的 Keystore 记录」解密提供；`GatewayPorts.Transport` 的 `post/get(url, apiKey, body)` 签名已天然支持（`GatewayPorts.java` 第 31–35 行）。
- SSE 语义不动：`SseFrameParser`、`GatewayPorts.Delta`、`StreamOutcome`、`AiGatewayError` 的契约原样沿用，新增提供商不得改动流式解析。

### 数据结构（SQLite 两表字段草案，供实现任务 T-083）

- `provider` 表：`id`（TEXT PK，UUID）、`name`（TEXT，非空，唯一）、`base_url`（TEXT，https）、`enabled`（INTEGER 0/1）、`is_builtin`（INTEGER 0/1，预置 DeepSeek 记录用）、`created_at`/`updated_at`（INTEGER，UTC epoch 毫秒，沿用 project-brief 时间约束）。
- `model` 表：`id`（TEXT PK，UUID）、`provider_id`（TEXT FK → provider.id，级联删除）、`model_id`（TEXT，供应商侧 wire ID，provider 内唯一）、`display_name`（TEXT）、`created_at`（INTEGER）。
- 迁移：前向版本迁移，新增两表；现存的单一 DeepSeek Key 语义迁移为「一条内置 DeepSeek provider 记录 + 其下默认模型记录」。
- 模型分工与思考预算、建议开关的偏好：不进 SQLite，仍走现有 `SettingsStore` 端口扩展（`android/app/src/main/java/com/treelearn/app/ui/SettingsStore.java` 第 12–20 行现有 model/thinkingEnabled 两法的同构扩展：三个分工各存 provider+model 引用，思考档位、建议开关各存一项）。

### Keystore 命名空间（T-084）

- 别名规则：`tl.provider.<provider-uuid>`，一提供商一别名；现单 Key 别名迁移至内置 DeepSeek 记录的别名下。
- 判定口径：`KeyVault` 扩展为「给定 provider id 是否有 Key」，仍返回布尔、不返回密文（沿用其接口注释的契约）；明文不进 SQLite、日志或备份（project-brief 密钥约束不变）。

### 模型列表拉取与去重合并（T-086）

- 拉取：对 `{base_url}/models` 发 GET + Bearer（机制同 F7），解析 `data[].id`。
- 合并：远端 ID 与本地 `model_id` 做**大小写精确**去重（同 F8 口径），只增不删——远端缺失的本地已存项保留（避免一次拉取抖动清掉用户手动添加的项）；重复 ID 静默跳过。
- 失败语义：非 2xx 或网络异常 → 本次不落库、UI 报 [OFF] 可重试；本地数据无损。

### 分工回退规则

- 所选分工模型所属提供商被删除或禁用时，该分工自动回落：先试「内置 DeepSeek 记录下的默认模型」，再试「启用提供商中创建最早的第一个可用模型」；都不可用则该分工置空并触发 [NOKEY] 守卫链路（发送拦截 + 引导）。回落发生在读取时（每次组装请求前校验），不在删除时批量改写偏好，且回落发生时在对话页以 Notice 提示一次。

### 状态迁移与错误语义

- 提供商：新建（表单校验 [DUP]）→ 启用/禁用（即时生效，只改 enabled 位）→ 删除（确认 → 级联删 model 行 → 触发分工回落检查）。
- 拉取：空闲 → 进行中（按钮禁用防重复提交，[DUP] 语义）→ 成功（合并入库）/ 失败（[OFF] 内联提示，可重试）。
- 分工选择：只影响之后新生成的回答，不改写已有回答（沿用 BASE-23 既有语义）。

- 来源：上述代码路径 + intent 移交清单中的实现任务编号 T-083（schema）/T-084（Keystore）/T-086（模型列表）与基座串行约束；`PromptPurpose` 契约（`android/core/src/main/java/com/treelearn/core/domain/PromptPurpose.java`）不受本变更影响，派生任务仍按用途隔离。

## Doubts（疑虑点）

- **疑虑 1：协议范围——OpenAI 兼容先行，Gemini/Anthropic 原生协议后补**
  - 冲突/问题：intent 的开放问题明列「协议范围」未知；矩阵 4.2 原判 reject 的三型中，RikkaHub 用 sealed 三型各配原生 Provider 实现（出处 F2），知芽若一次做三协议，网关改动面会突破单任务 200 行预算。
  - 建议：先做 OpenAI 兼容单协议（基址 + `Authorization: Bearer` + `/chat/completions` + `/models`），Gemini/Anthropic 原生协议暂缓。
  - 理由：DeepSeek、硅基流动、AiHubMix 等主流聚合/国产服务都提供 OpenAI 兼容端点，单协议即可覆盖目标场景；且知芽现有网关本就是 OpenAI 兼容形态的 DeepSeek 实现，参数化改动最小。
  - 代价：需要原生协议的服务（如 Google 直连）暂时接不进来，用户须改用其 OpenAI 兼容入口或后补任务。
  - 谁拍板：用户。
- **疑虑 2：思考预算在 DeepSeek / OpenAI 兼容参数上的映射**
  - 冲突/问题：RikkaHub 事实（出处 F5/F6）显示两件事：档位是离散 effort 字符串而非 token 数；参数形态按 host 分派——DeepSeek 官方域名发 `thinking` 开关对象 + `reasoning_effort`，未知 host 缺省发 `reasoning_effort`，部分服务发布尔开关。而「用 max_tokens 限定思考长度」在 RikkaHub 源码中没有对应做法。
  - 建议：知芽沿用现有 `JsonChatRequestBody` 的显式 `thinking` 开关（project-brief「思考模式开关每次请求显式传」约束不变），在其上叠加离散 `reasoning_effort` 档位（低/中/高）；DeepSeek 官方基址与未知基址统一走 `reasoning_effort`，不实现 per-host 分派表，也不做 max_tokens 映射。档位集合取五档（见 Interface plan），RikkaHub 的超高/最大两档不进首版。
  - 理由：离散档位跨服务兼容性最好；per-host 分派表维护成本高、与知芽「本地优先、单人维护」现实不符；max_tokens 映射在兼容服务上语义不可靠。〔2026-10-15 更新〕该条约束已由 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 从「无服务端」收窄为「无**远程**服务端」；本条推理依赖的是单人维护，不依赖有没有本机服务，结论不变。
  - 代价：个别服务的专有思考参数（如布尔 enable_thinking 型）覆盖不到，该类服务思考开关可能不生效，届时按疑虑 1 的「后补」路径立项。
  - 谁拍板：用户。
- **疑虑 3：聊天建议开关与现有 Recommendation 体系（T-046 派生推荐）合并还是并存**
  - 冲突/问题：RikkaHub 的 enableSuggestion 是单一布尔（出处 F4）；知芽已有两路推荐用途（`PromptPurpose.ANSWER_RECOMMENDATIONS` 与 `HOME_RECOMMENDATIONS`，`PromptPurpose.java` 第 38–42 行），开关若一刀切会连首页推荐一起关掉，语义超出 RikkaHub 原意。
  - 建议：并存——开关只控制 `ANSWER_RECOMMENDATIONS`（回答下方推荐）是否生成；首页推荐不受它控制，暂不提供独立开关。
  - 理由：intent 引入该开关的动机是「省额度/减打扰」，回答内推荐是每次回答都触发的额度大头，首页推荐频次低且独立有价值。
  - 代价：设置项语义与 RikkaHub 不完全一致（照搬度略降）；首页推荐无开关，想全关的用户没有入口。
  - 谁拍板：用户。
- **疑虑 4：快速模型未配置时的缺省回落规则**
  - 冲突/问题：RikkaHub 的分工行允许空选（显示「未选择」占位，出处 F4），但知芽的快速模型承接标题/摘要/派生任务，空选会让派生链路在运行时才失败，违背「无半成品入口」的 intent 异常条款。
  - 建议：快速模型与翻译模型永不为空——新增提供商并拉取到模型后，缺省把「内置 DeepSeek 的默认模型」同时设为三个分工的初始值；用户改选后回落规则走 Technical spec 的分工回退规则。不提供「留空」选项。
  - 理由：派生任务（`PromptPurpose` 的标题/摘要用途）在现有代码里没有空模型分支，补空态分支的代码与测试成本高于「始终有值 + 回落」；且新用户零配置即开即用。
  - 代价：与 RikkaHub 的「可选空」交互不一致；用户想显式留空做不到（单用户场景下该诉求弱）。
  - 谁拍板：用户。

### 拍板记录（2026-09-23 08:05 用户回复「按照建议采纳」）

- 疑虑 1 **采纳建议**：OpenAI 兼容协议先行，Gemini/Anthropic 原生协议后补另立任务。
- 疑虑 2 **采纳建议**：沿用显式 `thinking` 开关并叠加离散 `reasoning_effort` 档位（本规格 Interface plan 的档位集合），不做 per-host 分派表、不做 max_tokens 映射；RikkaHub 的超高/最大两档不进首版。
- 疑虑 3 **采纳建议**：并存——聊天建议开关只控制 `ANSWER_RECOMMENDATIONS`；首页推荐不受其控制，暂无独立开关。
- 疑虑 4 **采纳建议**：快速模型与翻译模型永不为空；新增提供商并拉取到模型后，三分工初始值均为内置 DeepSeek 默认模型；后续按 Technical spec 分工回退规则处理失效。

据此本规格四项未知项全部收敛，status 翻 accepted。
