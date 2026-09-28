# 知芽（TreeLearn）对话页视觉重做规格 V1.0

> 版本：V1.0 ｜ 日期：2026-09-23 ｜ 任务：T-2026-09-23-073（父需求 T-2026-09-23-071）
> 状态：draft，待用户 review；review 通过前 074–076 不得开工。
> **2026-09-27 用户裁决（并入下文，未升版本号）**：① §3 第 3 条「AI 正文左缘 `green` 细竖线」**作废** —— 对话页设计原型截图与高保真原型均无竖线（用户原话「不要左侧绿竖线了」）；② §10 拍板项 A 一并裁决为「无卡片正文 + 无左缘标识」。对话页 1:1 复刻的其它差异（气泡底色 `green-bubble`、气泡四角圆角）见 `知芽-TreeLearn-对话页1比1复刻方案-V1.0.md`；该方案初稿列的「`.ai-tag` 间距」经 DOM 实测判定为**假差异**（原型现值 40 ≈ 截图 40.5），无需改动。**字体（楷体→黑体）已于同日拍板并执行**：原型 `.screen` 改用 `--sans`，与对话页设计稿及真机 `themes.xml` 的 `sans-serif` 一致；`--kai` 保留，现存唯一引用是首页 hero 副标题（`.home-hero .hh-sub`）。
> **注意两处同名不同物的「竖线」**：本规格 §3 作废的是**正文/回答区**的竖线；`Docs/03-prd-draft/V3.2-…PRD.md` §6.2.18 与 Android T-066 实现的**思维链（reasoning）区**左侧竖线（`tl_line` / `dimens` 的 `reasoning_rule`）是另一回事，**不在本次作废范围内**。
> 事实源：本文是**目标态**规格；实现只读本规格、知芽代码与 design token（`Docs/04-ux-design/design-system/tokens.json`）。不做现有原型对比；RikkaHub 仅是行为参照，其像素与文案不进入本文。

## 1. 范围与生产者/消费者

- 生产者：`ChatViewState`/`ChatPresenter`（`android/app/src/main/java/com/treelearn/app/ui/`）产出状态；消费者：`ChatScreen.java`、`item_chat_message.xml`、`view_answer_actions.xml`、`screen_chat_body.xml`、`layout-sw600dp/screen_chat.xml`。
- 本规格不改变：消息顺序、树状分支来源、版本切换语义、阅读位置锚点（`ReadingAnchor`）、提示词与存储契约。

## 2. 状态矩阵（12 态）

「可操作性」列说明该状态下哪些交互可用；「恢复」列说明如何离开该状态。

| 状态 | 触发 | 视觉 | 可操作性 | 恢复 |
|---|---|---|---|---|
| S-EMPTY | 分支无消息 | 居中引导文案（`fs-h2`+`ink3`）+ 可选建议入口 | 输入栏全可用 | 发送首问 |
| S-LOADING | `Phase.LOADING` | `chat_loading` 可见，消息区骨架占位（`bg` 底 + `line` 描边块） | 输入栏可用，发送禁用 | 读入转 S-IDLE |
| S-IDLE | `Phase.READY` 且无生成 | 常态 | 全部动作可用 | — |
| S-REASONING | 生成中且 reasoning 流式 | 思维链区展开（见 §4.2），正文区占位 | 发送键=停止态；其余消息动作禁用 | 阶段推进/停止 |
| S-ANSWERING | 生成中正文流式 | 正文区追加渲染 + 尾部光标微动效 | 同上 | 结束/失败/停止 |
| S-STABLE | 回答落定（`Status` 稳定） | 光标消失，动作条出现 | 全部动作可用 | — |
| S-CANCELED | 用户停止 | 保留已收内容 +「已停止」行内标注（`fs-sm`/`ink3`） | 可重试、可继续追问 | 重试或忽略 |
| S-FAILED | 生成失败 | `chat_failure` 行内错误条（`red-soft` 底 + `red` 文），不弹窗 | 重试、复制错误、继续输入 | 重试成功转稳定 |
| S-ARCHIVED | 分支已归档 | 顶部 `amber-soft` 提示条「分支已归档」；正文只读 | 输入栏禁用并显示原因；浏览/复制可用 | 取消归档 |
| S-NOKEY | `keyConfigured=false` | 输入栏禁用态 + 提示行引导去设置（`amber` 文案） | 仅浏览与设置入口 | 配置 Key 后自动恢复 |
| S-KEYBOARD | IME 弹起 | 输入栏贴 IME 顶缘（`windowSoftInputMode` 既有 T-063 行为），消息列表底 padding 同步 | 不变 | 收起键盘 |
| S-LOWMOTION | 系统低动效开启 | §4 全部动效静态化（直接切终态） | 不变 | 关闭系统低动效 |

平板横屏（`layout-sw600dp/screen_chat.xml`）：以上状态逐条适用；对话列与树画布并列时，状态条（错误/归档/无 Key）只出现在对话列内，不出现在全局位置。

## 3. AI 消息组件规格

层级自上而下（对应 `item_chat_message.xml` 的 `chat_row`）：

1. **元信息行**：`chat_ai_tag`（`fs-cap`、`ink3`、`green-faint` 底、`r-sm` 圆角）+ `chat_time`（`fs-cap`、`ink3`）。高度 ≥ `s4`。
2. **思维链区**（`chat_reasoning`）：见 §4.2；折叠态只留标题行（`fs-sm`、`ink2`）+ 展开指示。
3. **正文区**（`chat_answer`）：Markdown 渲染层，`fs-body` + `other/lh-read` 行高；**无卡片、无左缘标识**——正文直接落在纸感底上，靠留白与标识行分层。
   - ~~左缘 `s1` 处用 `green` 细竖线标识 AI 身份（替代重底色，避免「卡中卡」）。~~
     **2026-09-27 用户裁决：本条作废。** 对话页设计原型截图与高保真原型均无左缘竖线，正文文字从 `--gutter` 起、未给竖线预留位（实测正文左边缘 20.5 逻辑 px）。依据见 `知芽-TreeLearn-对话页1比1复刻方案-V1.0.md`「已一致项」第 16 条与实测证据 `artifacts/evidence/ux-chat-1to1-measure-2026-09-27.txt`。
4. **失败条**（`chat_failure`）：`red-soft` 底、`r-sm`、内边距 `s2`；文案槽位 = 简因 + 可展开详情。
5. **动作条**（`view_answer_actions.xml`）：重排为「主动作（重试/重置）+ 版本切换器 + 更多」，图标按钮各 ≥44dp、间距 `s2`；版本切换器居中显示「n/N」（`fs-sm`、`ink2`）。
6. **来源与子分支入口**：沿用 `sourceCard` 与 `ChildBranchPopup`，仅按 token 对齐视觉，不改结构。

**容器决策（已裁决）**：AI 消息为「**无卡片正文，且无左缘标识**」——2026-09-27 用户拍板，左缘标识方案作废（见本节第 3 条注）；用户消息保持 `question_block` 轻容器（`surface` 底、`r-md`、`sh-1`）。原「轻卡片」备选不再保留：AI 正文不加 `surface`/`r-md`/`sh-1`，**禁止**在卡片内再嵌 reasoning 卡（层级只允许一层容器）。

## 4. 生成态动效

总则（布局稳定性，验收断言）：光标、点状指示器、错误条都占用**固定预留位**——出现/消失不得改变相邻控件（尤其动作条与输入栏）的命中位置；高度变化只发生在内容自身区域。

### 4.1 正文流式（S-ANSWERING）

- 尾部光标：1 个字符宽的 `green` 竖条，闪烁周期列为**新增 token 候选 `motion/caret`（建议 800ms 步进）**；仅在正文区域尾部内联渲染。
- 增量渲染节流：按帧合并增量（每帧最多一次重绘），避免逐字触发布局——知芽现状 UNKNOWN，075 需以「16ms 窗口合并」实现并断言。
- 首字出现：首块文本出现时该行淡入（**候选 `motion/enter`，建议 220ms**）；无缩放/位移。

### 4.2 思维链（S-REASONING）

- 生成中：自动展开，标题行右侧显示已用秒数（`fs-cap`、`ink3`，每秒步进）；内容区限高并自动滚到尾。
- 结束：按用户偏好收起（沿用 T-066 语义）；收起为高度过渡（候选 `motion/collapse`，建议 180ms），标题行保留「已思考 Ns」。
- 低动效：直接切终态，无过渡。

### 4.3 停止 / 失败 / 结束

- 停止（[CANCEL]）：光标即灭，「已停止」行内标注淡入（候选 `motion/quick`，建议 150ms）；已收内容完整保留。
- 失败：错误条从预留位展开（同 180ms 候选）；不遮挡正文。
- 结束转稳定：光标消失 + 动作条淡入（150ms）；不整卡重绘。

## 5. 输入栏规格（`screen_chat_body.xml` composer）

结构自上而下：引用行（`chat_quote_row`，仅引用时显示）→ 多行草稿框（`chat_draft`，`r-lg`、`surface` 底、`line` 描边、内边距 `s3`，最多 5 行后内滚）→ 控件行（附件入口 `chat_attach` + 模型/思考入口 `chat_model_chip` + 发送键 `chat_send`）。

**2026-09-28 用户裁决（动作行与附件批注）**：① 回答底部动作行收敛为**复制 / 重新生成 / 新建分支 / 分支列表** + 右侧**生成时间**，引用 / 翻译 / 朗读 / 版本切换四个控件连同其图标与描述一并移除（引用改由**选词菜单**提供，见 `popup_word_action.xml`）；② 四个按钮的 `layout_width` 统一 `@dimen/touch_min`(44dp)、`paddingStart/End` 归零、无 `marginStart`，既收掉「隔得太开」的多余间距又保住 44dp 热区；③ `chat_attach` 图标由 **24dp 降到 15dp**，与同排 `model_chip_effort` 同档（此前两个控件图标差一档，是「只有附件显大」的根因），`paddingEnd` 由 `s3` 收到 `s2` 与 `paddingStart` 对称；④ 输入框上方灰带**保持不动**。详见 `知芽-对话页动作行与附件批注处理方案-V1.0.md` §0.0。

- **发送键三态**：禁用（`ink3` 图标于 `bg` 底，草稿空或不可发）/ 发送（`green` 图标）/ 停止（`red` 方块图标，`attemptRunning` 时）。三态共用同一控件位（`chat_send`），不新增控件——布局稳定性由单控件换态保证。
- **IME**：沿用 T-063 既有 `windowSoftInputMode` 行为；输入栏随 IME 顶缘移动，消息列表底 padding 同步加到输入栏顶缘，保证最后一条消息在键盘上方可见。
- **草稿与恢复**：草稿、引用、展开集在旋转/进程重建后回填（`ChatViewState` 已持有，持久化由 076 落地，JSON 于 `SharedPreferences` 或 DataStore 等价物——Java-only 下用 `SharedPreferences`）。
- **归档分支（S-ARCHIVED）**：输入栏整体禁用（`ink3`）+ 占位文案「分支已归档，取消归档后可继续提问」；引用行与已有草稿保留不清除。
- **无 Key（S-NOKEY）**：发送禁用 + 占位文案引导设置；模型 chip 仍可点（进设置）。
- **引用/分支上下文**：引用行显示被引用句（`fs-sm`、`ink2`、`green-faint` 底、`r-sm`）+ 清除钮（≥44dp）；从消息选区发起分支时（`onForkPressed`）预填引用并聚焦草稿。

### 5.1 全屏编辑器（2026-09-24 用户拍板：要做；推翻 076「不做」判定）

- **入口**：composer 控件行左侧新增展开图标钮（≥44dp，`contentDescription`=「全屏编辑」），仅在草稿非空或聚焦时可用（空草稿灰显）；归档/无 Key 态仍显示但点击给出对应提示（不进入）。
- **形态**：全屏覆盖层（`DialogFragment` 或全屏 View，Java/XML），底色 `bg`；自上而下：标题行（「编辑提问」，`fs-h2`+`ink`，右上角关闭钮 ≥44dp）、引用行（复用 `chat_quote_row` 视觉，只读展示）、大草稿框（`surface` 底、`r-lg`、`line` 描边、占满剩余高度、`fs-body`+`lh-read` 行高、无行数上限内滚）、底部动作行（「取消」文字钮 `ink2` +「完成」主钮 `green` 底白字 `r-full`，均 ≥44dp）。
- **数据流**：进入时把当前 draft/quote 复制进全屏框；编辑实时回写 draft（同 076 持久化链路，键击节流可复用）；「完成」与系统返回键等效=保存并关闭；「取消」也保存草稿（草稿无破坏性操作，不弹确认）。
- **状态**：S-ARCHIVED/S-NOKEY 下入口灰显；低动效=直接切换无过渡；TalkBack：进入播报「已进入全屏编辑」，关闭播报「已返回对话」。
- **布局稳定性**：全屏层是独立窗口，不影响对话页任何控件命中位置；关闭后焦点回 composer 且光标置于草稿末尾。

## 6. 滚动与跟随（075 专属）

- **自动跟随**：生成中，当用户位于列表底部（现有 `onViewportChanged` 判定）则吸附新内容；手动上滑超过一个屏幕高度阈值即「让位」，跟随停止。
- **恢复跟随**：让位后列表底部浮现「回到最新」浮钮（`surface` 底、`r-full`、`sh-2`、`green` 文案，≥44dp），点击恢复跟随并滚至生成位置。
- **新消息提示**：让位期间有新增内容时，浮钮文案带计数「回到最新（N）」。
- **阅读位置**：离开页面记录 `ReadingAnchor`（既有），返回时恢复；长列表跳转入口（到顶/到底）沿用 068 笔记对照的浮条形态，列为 075 可选项。
- 低动效：滚动直接跳转，无平滑动画。

## 7. 无障碍与触达

- 所有可点控件 ≥44dp（硬约束）；图标钮必须带中文 `contentDescription`（resource 键由 074–076 添加，中英双语 `values-zh` 已是既有约定）。
- 生成状态变化须对 TalkBack 播报：开始生成、已停止、失败（`announceForAccessibility` 或 liveRegion）。
- 版本切换器「n/N」以「第 n 个回答，共 N 个」播报；低动效不减少播报。

## 8. 实现映射（074–076 验收锚点）

| 规格 § | 任务 | 实现文件 | 验证方式 |
|---|---|---|---|
| §3 消息层级/动作条/版本器 | 074 | `item_chat_message.xml`、`view_answer_actions.xml`、`AnswerActionBar.java` | JVM Presenter 断言 + Robolectric 布局断言 + 人工观察 |
| §4 动效/§6 滚动 | 075 | `ChatViewState.java`、`ChatPresenter.java`、`ChatScreen.java`、`item_chat_message.xml` | 状态机单测（RED/GREEN）+ 布局稳定性断言 + 低动效观察 |
| §5 输入栏 | 076 | `screen_chat_body.xml`、`ChatPresenter.java`、`ChatScreen.java` | 三态单测 + 草稿重建单测 + IME/横屏人工观察 |
| §2 状态矩阵 | 074–076 共同 | `ChatViewState.java`（新增态） | 每态一条单测；矩阵逐态勾选 |

## 9. 新增 token 候选（须走 token 守门流程，不得直接写死）

- `motion/quick`（建议 150ms）、`motion/enter`（220ms）、`motion/collapse`（180ms）、`motion/caret`（800ms 步进）。
- 在 tokens.json 收编前，实现中允许以常量持有并集中在一个文件内，守门脚本对齐时收编。

## 10. 待用户拍板（review 时决定）

1. ~~**拍板项 A**：AI 消息「无卡片+左缘标识」（建议）vs「轻卡片」~~ → **已于 2026-09-27 裁决**：取「无卡片正文」，且**左缘标识一并作废**（用户裁定「不要左侧绿竖线了」）。见 §3 第 3 条注。
2. 生成动效强度：建议采用 §4 全部（克制档）；若嫌多，可去掉首字淡入只留光标。
3. 「回到最新」浮钮 vs 顶部提示条二选一（建议浮钮：单手可达）。
4. 长列表跳转浮条是否随 075 一起做（建议：可选项，不阻塞）。

## 11. 出处索引

- 知芽现状（2026-09-23 核对）：`ChatViewState.java`（Phase/draft/sending/sourceCard）、`ChatMessage.java`（Status/Phase/attemptRunning/revisionCount）、`ChatActions.java`（无 onStop）、`screen_chat_body.xml`（无 stop 控件）、`item_chat_message.xml`、`layout-sw600dp/`。
- 行为参照：`Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md`（§1–§3）；design token：`Docs/04-ux-design/design-system/tokens.json`。
