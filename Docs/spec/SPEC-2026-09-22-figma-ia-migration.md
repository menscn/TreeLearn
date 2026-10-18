# Spec: Figma 新稿的信息架构与视觉迁移（只改交互，不动功能规格）

id: SPEC-2026-09-22-figma-ia-migration
title: Figma 新稿的信息架构与视觉迁移（只改交互，不动功能规格）
date: 2026-09-22
intent: Docs/intent/INTENT-2026-09-22-figma-ia-migration.md
author: spec-author
status: draft

## Requirement spec（需求规格）

从已 accepted 的 intent 抄来，不改写它的结论。

- 范围：
  - 设计基座：吸收新稿的配色与度量（修正两个 token 后三处同步），同步字体回退栈。
  - 信息架构：引入底部标签栏（主页 / 学习库 / 设置）；新建学习库屏承担主题列表与归档内容；首页瘦身为品牌区、输入区、继续学习、发现话题。
  - 保功能的最小交互：归档动作落在分支导航屏内；检索的唯一入口是学习库顶部的搜索框，引擎不变；删除维持整树语义与二次确认。
  - 资产与装饰：约 28 个线性图标的 Vector Drawable；画布的点阵网格背景（仅叠加，控制条保留现状）。
  - 文档：新建 V3.2 全量 PRD，改写与信息架构相关的条目。
- 验收方向：
  1. `check_design_tokens.py` 三方逐值比对零失败，且对比度审计脚本对回退项零命中。
     - 口径更新（2026-10-15，不改动上面这条当时的判据）：该守门已扩为**四方**逐值比对，第 4 个消费者是生成的 `frontend/src/styles/tokens.css`；链路、生成命令与豁免规则以 [`design-system/README.md`](../04-ux-design/design-system/README.md) 为准。本条当时按三方达成，记录保留原样。
  2. 底部标签栏三个标签各自可达且切换正确；二级屏返回落在正确标签。
  3. 归档、恢复、整树删除三条动作仍可用，且删除在确认前先取消在飞的生成。
  4. 学习库搜索框能命中标题、问题、锚定原文、回答、思考、失败半成品内容六类字段中的任一类，结果带祖先路径与归档状态。
  5. 首页不再出现历史主题列表与头部的搜索/归档/设置三入口。
  6. Java-only 守门与各栈 lint 通过；单任务生产代码行不超预算。
- 非目标：不新增功能；不改任何已有功能的语义；不做暗色模式；不迁移原型的内存模型与演示数据；不改已验收的树布局算法；不把新稿的 SVG 布局常量搬进工程。
- 来源：`Docs/intent/INTENT-2026-09-22-figma-ia-migration.md` 的「Expected outcome」「Constraints and non-goals」「Decision record」三节。

## Interface plan（界面方案）

- 信息架构：
  - 一级入口改为底部标签栏，三个标签：**主页**、**学习库**、**设置**，可点击区不小于 `touch_min`。
  - 主页（标签一）：品牌区（图形标识 + 中文名 + 英文小字）、输入区（问题输入框 + 模型档位标识 + 发送按钮）、继续学习（一行，显示最近分支标题，无历史时不出现）、发现话题。
  - **发现话题：保留现有推荐弹幕**（双行反向滚动、交互时暂停、三类标签「顺着你学的 / 换个方向 / 入门」），只换配色、间距与图标位。用户 2026-09-22 裁决：新稿的「一张重点卡 + 三条列表项」形态**不采用**，理由是该形态触及 PRD 第 228 行的 F17 能力描述与第 978 行的验收口径，改它越过红线；该形态记为 deferred。
  - 学习库（标签二）：标题、搜索框、分段控件（进行中 / 已完成，各自带数量）、列表区。进行中列出主题（标题、分支数量、时间、未读点、左侧强调条）；已完成列出归档分支（来源主题、锚定原文、标题、末条回答片段、恢复与删除两个操作）。
  - 设置（标签三）：由二级屏升为一级标签，内容不变（API Key 区、模型选择、推理设置、关于）。
  - 二级屏保持四个：对话、树画布、分支导航、**搜索不再有独立屏**。
- 入口位置：
  - 归档动作：留在分支导航屏的分支行操作里，视觉换新、动作与顺序不变（与 `NavigationPresenter` 现有的归档/恢复语义对齐）。
  - 检索：学习库顶部搜索框是唯一入口。输入即触发全局检索；结果替换列表区，不再嵌套第二个搜索入口。
  - 删除：在已完成区的归档行内就地二次确认（与现工程导航屏的确认语义一致），不在别处新增删除入口。
  - 设置：原首页头部的设置入口随首页瘦身移除，改由底部标签承载。**对话页内的设置入口本轮保持现状、不动**——本规格此前写成「见疑虑点」是悬空引用（疑虑点清单里并无此条），现按保持现状结案，理由是该入口属已验收行为，删除它要牵动对话屏与其测试却零功能收益。
- 状态与异常：
  - 搜索四态：尚未输入（示空闲）、检索中（示进行态）、有结果（示结果列表）、无结果或失败（分别示两种不同文案，失败时保留草稿）。
  - 生成三态沿用现状：生成中（品牌标识 + 进行指示）、稳定、失败（原位重试）。
  - 归档与删除的操作中态：行内确认时该行呈现确认态；删除不可逆必须二次确认。
  - 空态：学习库进行中为空时示「还没有学习记录」，已完成为空时示「没有已完成的学习」，两者文案不同且各自给出下一步指引。
- 数量与呈现方式：
  - 结果项字段：命中字段名、祖先路径、归档状态、片段，四者缺一不可——这是 PRD 要求的信息，不是装饰。
  - 跨归档状态的检索结果必须显式标出归档状态，避免与日常分支混淆。
  - 呈现密度：列表项一行标题加一行次要信息；长文本单行截断，不换行撑高。
  - 圆角、间距、字号、阴影一律取 `radius_*`、`space_*`、`page_gutter`、`text_*` 档位；触达用 `touch_min`；平板列宽用 `content_measure`。本规格不复写具体数值。
- 来源：
  - 设计 token 名与分组见 `Docs/04-ux-design/design-system/tokens.json`；一致性由 `.codex/scripts/dev/check_design_tokens.py` 判定。
  - 新稿对应小节：来源 `src/index.css` 的 `:root` 区块（设计 token）与 `src/App.tsx`（标签栏与换屏）、`src/screens/HomeScreen.tsx`、`src/screens/ArchiveScreen.tsx`、`src/screens/NavScreen.tsx`、`src/screens/CanvasScreen.tsx`。
  - 迁移方案与差异分类见 `Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`。

## Technical spec（技术规格）

- 接口：
  - 检索栈整体保留：`android/app/src/main/java/com/treelearn/app/search/SearchPresenter.java`、`SearchRepository.java`、`SqliteSearchRepository.java`、`SearchViewState.java`、`SearchPage.java`、`BranchTitles.java`，以及 `android/data/src/main/java/com/treelearn/data/db/dao/SearchDao.java`。学习库屏的搜索框绑定同一个 presenter，不新写第二套检索。
  - 归档与删除继续走 `android/app/src/main/java/com/treelearn/app/navigation/NavigationData.java` 的端口：`setArchived` 只翻归档位，`delete` 是不可逆动作且调用方需先 `cancelInFlight`；删除集合由 presenter 从一份 `Snapshot` 算出 `Plan`，store 在一个事务里删除该集合。学习库的已完成区必须复用同一份 `Plan` 构造与删除实现，不得新写第二套。
  - 换屏继续走 `android/app/src/main/java/com/treelearn/app/MainActivity.java` 的私有 `swap()`（`host.removeAllViews()` 后挂入新屏）；新增学习库屏时在同一个机制里注册，不引入 Fragment 或 AndroidX Navigation。标签栏做成常驻外壳，由 `swap()` 按目标屏统一决定其显隐。
- 数据结构：
  - 不新增数据库表，不改 migration。学习库的主题列表与归档列表都由既有表与既有 store 读出。若为归档行的「锚定原文 / 末条回答片段」确需新查询，只允许在既有表上新增**只读**查询（不加列、不改表、不碰写路径），否则降级为不显示并记录偏差。
  - 新增学习库屏的视图状态类型（不可变、可绘制），形制参照 `android/app/src/main/java/com/treelearn/app/navigation/NavigationViewState.java`。
  - 搜索结果项继续用 `android/core/src/main/java/com/treelearn/core/domain/SearchHit.java`，它已携带主题、分支、消息序号、修订号与片段，足以渲染祖先路径、归档状态与定位。
- 状态迁移：
  - 标签切换与二级屏进出：新增「当前标签」状态；从二级屏返回时回到发起它的标签。
  - 学习库分段（进行中 / 已完成）是屏内状态，不持久化。
  - 搜索的「输入 → 检索中 → 有结果 / 无结果 / 失败」迁移与迟到响应丢弃语义沿用 `SearchPresenter` 现有实现。
  - 归档后该分支与其整棵子树从日常导航与默认画布隐藏；恢复时不做任何后代重接。
- 错误语义：
  - 无可用 Key 时不创建主题，只在主页给提示（沿用 `HomePresenter` 现有守卫）。
  - 检索失败保留草稿并示错误态；空查询不触库。
  - 删除必须经二次确认；不可恢复，且确认前先取消在飞生成。
- 来源：
  - `docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md`
  - `android/app/src/main/java/com/treelearn/app/`（`screen/`、`navigation/`、`search/`、`ui/`）
  - `android/data/src/main/java/com/treelearn/data/db/dao/SearchDao.java`
  - `android/core/src/main/java/com/treelearn/core/domain/`
  - `android/README.md`（UI 约定、对比度记录、尺寸令牌表）
  - `.codex/ROADMAP.md`（Stage 2–6 未交付事项）

## Doubts（疑虑点）

**判定结果（2026-09-22 用户逐条拍板；疑虑 5 已按证据自行结案；疑虑 7 为落盘拆分时新发现并由用户裁决）：**

| 疑虑 | 结论 |
| :--- | :--- |
| 1 首页与 PRD「唯一入口页」口径冲突 | 采 A：在 V3.2 里改写相关条目，明确首页不再是唯一入口页，搜索与归档由底部标签与学习库承担，「无第二套首页」改为「首页不重复承载主题列表」 |
| 2 已完成区分组方式 | 采 A：平铺，每条补祖先路径与归档状态 |
| 3 独立搜索屏视图层 | 采「删视图层 + 把入口可达类测试改写为学习库版本」；用户已明确授权删除，覆盖面不缩水 |
| 4 画布控制条处置 | 采 B：保留现控制条，只叠加点阵网格背景 |
| 5 Vector Drawable 行数口径 | 已按证据结案：计入生产代码行预算 |
| 6 衬线回退观感差异 | 作为残余风险接受，不再追问 |
| 7 推荐区形态（落盘时新发现） | **保留现有推荐弹幕**，不采用新稿的「一张重点卡 + 三条列表项」。理由：PRD 第 228 行的 F17【P0】把「弹幕滚动展示」写进能力描述、第 978 行又写进验收口径，改它属于动能力而非纯装饰，越过红线。代价：新稿形态记为 deferred，V3.2 中 PRD 第 49、96、228、978 四行一字不改。另：`Recommendation` 携带 `Kind` 分类，三类来源在数据层可分辨，无论形态如何都必须全部可达 |
| 8 对话页内的设置入口（悬空引用归位） | 保持现状、不动。本规格原写「见疑虑点」但疑虑点清单里并无此条，属悬空引用；现按保持现状结案 |

以下保留原始条目作为裁决记录：

- 疑虑 1：**首页瘦身与 PRD 的「唯一入口页」口径冲突。**（冲突对象：`Docs/03-prd-draft/V3.1-知芽（TreeLearn）产品需求文档（PRD）.md` 第 703 行「首页为唯一入口页，包含固定的品牌头部；头部右侧依次提供搜索、已归档、设置三个入口」与第 977 行验收口径「无第二套首页」；两种走法：A 在 V3.2 里改写该条，明确首页不再是唯一入口页、三入口由底部标签与学习库承担；B 保留首页头部三入口，只在底部再加标签栏；谁拍板：用户）
- 疑虑 2：**已完成区的分组方式。**（冲突对象：新稿把所有归档分支平铺，而 PRD 第 469 行要求每条结果显示祖先路径及归档状态；两种走法：A 平铺但每条显示祖先路径与归档状态；B 按主题分组、组内平铺，祖先路径退化为主题名；谁拍板：用户）
- 疑虑 3：**独立搜索屏的处置。**（冲突对象：`android/app/src/main/java/com/treelearn/app/screen/SearchScreen.java`、`res/layout/screen_search_body.xml`、`res/layout/item_search_hit.xml`、`res/layout-sw600dp/screen_search.xml`，以及 8 个搜索相关测试文件——`SearchPresenterTest`、`SearchPageTest`、`SearchJumpTest`、`HomeSearchEntryTest`、`SearchHitTest`、`SearchQueryTest`、`SearchDaoStatementTest`、`SearchUnreadReadingInstrumentedTest`；两种走法：A 删除视图层与仅服务于独立屏的测试，检索栈与领域类型保留；B 文件保留但不再被路由；谁拍板：用户——删除属于本次「保功能」边界外的一次清理，需明确授权）
- 疑虑 4：**画布控制条的处置。**（冲突对象：`res/layout/screen_tree_body.xml` 的现有控制条（折叠 / 进入 / 适应）与新稿画布右上角的缩放工具栏（适应 / 放大 / 缩小 / 定位）；两种走法：A 用新工具栏替换现有控制条；B 保留现有控制条，只叠加点阵网格背景；谁拍板：用户——两种走法对已验收的画布测试影响不同）
- 疑虑 5：**已按证据结案——Vector Drawable 计入生产代码行预算。**（依据：`.codex/scripts/dev/count_production_lines.py` 第 30 行对 `.xml` 走 `strip_xml`，所以 XML 资源与 Java 同样计入。影响：约 28 个图标的 XML 很可能超过单任务 200 行的软上限，拆解时要么切成两片，要么按契约记录一条软上限警告；实际行数在拆解时用该脚本实测，不预估。）
- 疑虑 6：**品牌字的衬线回退栈在中文设备上观感不一致。**（冲突对象：已决的「本轮不打包字体」；两种走法：接受各厂商 ROM 的衬线差异，或后续追加一次子集化字体内嵌；谁拍板：用户——本轮按接受处理，此处只登记残余风险）
- 疑虑 7（落盘时新发现）：**推荐区形态是否跟着新稿改。**（冲突对象：PRD 第 228 行 F17【P0】「弹幕滚动展示」与第 978 行验收口径「双行反向滚动，交互时暂停」，对上新稿的「一张重点卡 + 三条列表项」；两种走法：A 按新稿改为重点卡 + 列表；B 保留弹幕只换皮；谁拍板：用户——**采 B**。此外 PRD 第 49、96、228、978 行的同源表述一并保留。）
- 疑虑 8（悬空引用归位）：**对话页内的设置入口保留还是收敛到标签栏。**（原条目写在本规格「入口位置」一节但未在疑虑点清单中出现，属悬空引用；谁拍板：AI 按「保持现状、不动」结案，理由为该入口属已验收行为，删除它要牵动对话屏与其测试却零功能收益。）

<!-- FIELDS: id | title | date | intent | author | status | Requirement spec（需求规格） | Interface plan（界面方案） | Technical spec（技术规格） | Doubts（疑虑点） -->
