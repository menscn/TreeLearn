# Spec: AI 内置人设（用户可选的学习风格）

id: SPEC-2026-09-21-ai-persona-preference
title: AI 内置人设（用户可选的学习风格）
date: 2026-09-21
intent: Docs/intent/INTENT-2026-09-21-ai-persona-preference.md
author: spec-author
status: draft

## Requirement spec（需求规格）

- 范围：主题级「回答风格」选择，固定 7 档内置人设（默认、手把手、严格导师、学霸搭子、毒舌损友、深夜陪读、学习修复师）。选择跟着主题走，只影响之后新生成的回答——不改写已有回答、来源版本或创建快照；另有一个只读的人设介绍页。
- 预期效果：用户不再需要在提问里手写风格要求，主线笔记不会夹着这类指令；主回答既有的导师基线在任何一档下都不被削弱。
- 验收方向：
  - 可机械验证：人设枚举存在且穷尽；主题表有对应列且迁移可前向；每条成功生成的回答记录带当时的人设身份，旧回答可复现；system 消息恰好一条且位于第一位；派生用途（标题、分支摘要、推荐问题）的提示词不含人设段。
  - 需行为样例：各档的风格差异按提示词设计 §8.2 的受控问题集与行为样例表验收，不做逐字断言。
  - 界面侧：选择器恒有值（新建主题即「默认」档），因此不存在空态。
- 基线不可削弱：主回答既有的导师基线（诚实、纠错、不编造）在任何一档下都成立，任何人设都不得解除最高规则；人设只能作用于讲解方式、深度、节奏、措辞、是否主动反问、是否主动延伸。
- 工程约束：Java-only；`:core` 不碰 Android UI、网络与数据库实现。
- 非目标：本期不做用户自建人设；不向用户开放提示词正文编辑；不改写既有 `LEARNING_ANSWER` 基线正文；不新增服务端、账号、同步；不引入非 DeepSeek 供应商；人设不影响标题、分支摘要、推荐问题等派生输出。
- 来源：`Docs/intent/INTENT-2026-09-21-ai-persona-preference.md` 的「Expected outcome」「Core flow」「Constraints and non-goals」「Decision record」（7 档与典型处境另见它的「Users and scenarios」，三条用户自述后果见「Problem」）。本节照抄 intent 结论，不改写；未逐字搬过来的只有处境举例、权衡偏好的原文，以及「单人开发：验收必须能机械验证」这一条——它的要求由本节的「验收方向」承载。

## Interface plan（界面方案）

- 信息架构：
  - 主题内：聊天页输入框下方那一行（原型 `.composer-tools`）现在只放一个「模型信息 chip」按钮；在它**右侧新增一个同款 chip**（样式复用 `.model-chip`，文案是当前档名加一个下拉箭头 `IChevD`），点它打开人设选择。
  - 人设选择弹窗：跟手小弹窗（与模型弹窗同一套 `popover`：贴按钮弹出、空间不足自动翻转）。顶部 `popover-label` 文案「回答风格」；正文是 7 行 `popover-item`，每行 `pi-t` 是档名，当前档在右侧带 `pi-check` 对勾并套 `on` 选中底色；底部一条 `popover-sep`，再一行 `popover-item`「了解每种风格」，进入介绍页。**7 档排成单行**——一句话定位放介绍页，不放在这里，所以这个弹窗放得下。
  - 介绍页：`appbar`（`icon-btn` 返回 + `appbar-title` 的 `t1`）标题「回答风格介绍」；`body-scroll` 内 7 个 `set-section`，每档一张 `set-card` = 档名（`sr-title`）+ 一句话定位（`sr-sub`）+ 一段静态问答示例（示例区底色 `green-faint`、圆角 `r-md`）。页面末尾留一条说明位，写明自建人设将来在这里管理，本期不放可点的增删改查按钮。
  - 设置页：新增一个 `set-section`，`set-label` 文案「回答风格介绍」，内含一张 `set-card`，其中一行 `set-row`（`sr-title`「回答风格介绍」+ `sr-sub`「看 7 档风格各自适合什么处境」+ 右侧 `IChevR`），进入同一个介绍页——这一处只是入口，不是全局设置（人设跟着主题走，改动只发生在主题内那个人设按钮弹出的弹窗里）。
- 入口位置（用户 2026-09-21 拍板，两处）：① 主题内输入框那一行的人设按钮，点开后的弹窗底部有「了解每种风格」；② 设置页「回答风格介绍」小节里的那一行。介绍页**不承载选择**：它只有介绍、示例与未来的人设增删改查位，选择只发生在那个人设按钮弹出的弹窗里。
- 状态与异常：
  - 按钮上恒有档名：新建主题的默认档是「默认」，所以按钮不存在空态，也不用画空态。
  - 选中呈现：`pi-check` 对勾 + `on` 底色（`green-faint`），对勾用 `green-deep`。
  - 切换反馈：沿用现有 `toast` 模式（如「已切换到 严格导师」）；切换立即写入该主题，只影响之后新生成的回答。
  - 回答上**一律不标注人设**（用户 2026-09-21 拍板）：正文区、分支摘要、标题都不出现档名，人设只体现在那个人设按钮与它弹出的弹窗里。
  - 读到未知档位（迁移或降级留下的值）：按钮与弹窗都把「默认」显示为当前档并给一次 `toast`，不出现空白按钮、不阻断选择；介绍页仍完整列出 7 档。
  - 介绍页是静态文案：不发网络请求、不需要已配置 API Key，断网可用；示例不现场生成。
- 数量与呈现方式：
  - 7 档固定清单，「默认」排在最前；按钮与弹窗都只显示档名，一句话定位只在介绍页出现。
  - 档名用 `fs-body`；按钮文字用 `fs-sm` 且为 `ink2`（与模型 chip 同档）；介绍页卡片标题用 `fs-h2`、正文用 `fs-body`、页面主标题用 `fs-h1`；示例区标注用 `fs-cap`。
  - 间距只取 4pt 档位：按钮与弹窗行内上下 `s2`，卡片内 `s4`，卡片之间 `s3`，小节之间 `s6`；页面左右留 `gutter`。
  - 圆角只取 `r-md`（卡片、示例区）与 `r-full`（chip 与胶囊类元素）；卡片底 `surface`、描边 `line`、页面底 `bg`；阴影只用 `sh-1`。
  - 可点区域不小于 44×44 dp（沿用项目约束里的触屏热区）。
- 来源：token 名取自 `Docs/04-ux-design/design-system/tokens.json` 的 `bg`、`surface`、`ink2`、`ink3`、`line`、`green-deep`、`green-faint`、`s2`、`s3`、`s4`、`s6`、`gutter`、`r-md`、`r-full`、`fs-cap`、`fs-h1`、`fs-h2`、`fs-body`、`fs-sm`、`sh-1`；界面模式取自原型 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `.composer-tools` 与 `.model-chip`（第 223-227 行、第 351-353 行）、`.sheet` 区块（第 309-315 行）、`.popover-*` 区块（第 356-372 行）、`.switch` 开关（第 379-382 行）、`set-section` / `set-card` / `set-row` 区块（第 290-296 行）与 `renderSettings`（第 2273 行起）；底线规则取自 `.codex/rules/ux-design.md` 的「设计质量底线」与「可点击区域不小于 44×44 dp」。

## Technical spec（技术规格）

口径说明：**「现状」= 生产代码实测事实**，每条给 `路径:行`（由 `artifacts/tmp/b032-pointers.py` 的锚点唯一命中现场给出，行号能被 `.codex/scripts/harness_check_lib.py:174` 的 `CITATION_RE` 复算）；**「规格」= 本规格为新增物定的口径**，等人 review，不是事实。计数（「共 4 列」「已 8 项」）不抄自任何文档，由 `artifacts/tmp/b032-counts.py` 现场数出。

引用一律写全路径，不写「…TreeLearnSchema.java:47」这类简写、也不写「:47」这类续写：`CITATION_RE` 只认以 Docs、.codex、android、frontend、artifacts、README 开头的反引号路径，简写与续写**根本不被计成引用**，改错了不会有任何命令变红——`problems=0` 于是可以是假安心。本节读起来啰嗦是这条规则的代价，值得付。

人设层在生产代码里还不存在：`android/*/src/main` 与 `frontend/src` 全量扫 `persona|人设|回答风格|answer_style` 命中 2 行，两行都是注释里的英文单词（`android/app/src/main/java/com/treelearn/app/screen/RecommendationTag.java:56`、`android/data/src/main/java/com/treelearn/data/db/dao/SearchDao.java:38`），没有一个标识符。所以本节的每一条「规格」都是新增物的边界，不是对既有实现的转述。

### 接口（人设枚举与主题读写的调用面）

- 现状·身份的形状：提示词身份是「枚举 + 该枚举拥有的版本命名空间字符串」，不是模板名、不是正文、不接受插值（`android/core/src/main/java/com/treelearn/core/domain/PromptPurpose.java:24` 首个枚举项、`android/core/src/main/java/com/treelearn/core/domain/PromptPurpose.java:56` `policyPrefix` 字段、`android/core/src/main/java/com/treelearn/core/domain/PromptPurpose.java:67` `ownsVersion()`；从版本前缀反查用途在 `android/core/src/main/java/com/treelearn/core/domain/PromptPurpose.java:104`）。该枚举现有 8 项。
- 规格：新增 `Persona` 枚举放 `:core`，7 档各带一个稳定 `wireId` 与一段静态附加段（档位清单见 `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md:137`）。它**不进** `PromptPurpose` 的版本命名空间：思考模式已经立着这条口径——附加段只追加、不改变基础版本内容，主回答的版本字面量至今仍是 `tl-learning-answer/1.1.0`（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:82`）。人设与它同构，导师基线正文（`android/ai/src/main/java/com/treelearn/ai/prompt/PromptBodiesV1.java:25`）一个字都不动。
- 现状·穷尽分派：用途→版本与用途→正文各是一个**没有 default 的 switch 表达式**，契约就写在注释里——新增枚举值不写 case 就编译不过（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:79`、`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:80`、`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:94`）。
- 规格：人设→附加段用同一机制承接，让「漏配一档」是编译失败而不是运行期缺省。这是「注册表只接受枚举」那条既有口径的机械形态。
- 现状·附加段的拼接与拒绝：`systemContent(purpose, thinkingEnabled)` 只在 `LEARNING_ANSWER` 上追加静态附加段（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:50`、`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:61`），派生用途索要附加段时**抛** `THINKING_NOT_APPLICABLE_FOR_PURPOSE`，不是静默忽略（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:57`）。
- 规格：人设段走同一个出口、复用同一条拒绝。需求「派生用途的提示词不含人设段」由这条拒绝承接，不靠调用方自觉；派生侧本来就不与主回答共享装配对象（`android/app/src/main/java/com/treelearn/app/artifact/DerivedRequest.java:201`、`android/app/src/main/java/com/treelearn/app/artifact/DerivedRequest.java:206`），且 `ArtifactKind` 明确拒用导师正文（`android/app/src/main/java/com/treelearn/app/artifact/ArtifactKind.java:99`）。
- 现状·消息序列的两端都已经锁死：装配器把 system 放在下标 0（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequestAssembler.java:45`）、把当前输入追加成最后一条 user 消息（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequestAssembler.java:47`），入口签名即这两条约束的所在（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequestAssembler.java:36`）。验收项「system 恰好一条且位于第一位」「当前输入位于最后一条」在人设之前就成立，本需求只承担「不打破它」；人设段无论走哪条附加路径，都只能进下标 0 那一条正文，不得新增消息。
- 现状·身份钉死与漂移拦截：主回答的用途与版本各在一处钉死（`android/app/src/main/java/com/treelearn/app/wiring/AnswerPolicy.java:16`、`android/app/src/main/java/com/treelearn/app/wiring/AnswerPolicy.java:21`），命令在 `ChatWiring.pin()` 组装（`android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java:651`），请求对象自己携带版本字段（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequest.java:19`），提交时命令版本与请求版本不符即拒（`android/app/src/main/java/com/treelearn/app/generation/GenerationCoordinator.java:193`）。
- 规格：人设进入命令（`android/core/src/main/java/com/treelearn/core/domain/GenerationCommand.java:106`）与请求后，那条不符即拒必须**同时比人设**——合取成一条判据，不是新增一条各判各的。只比版本的守门会放行「命令说严格导师、请求拼了手把手」，而那正是既有那条判据要拦的东西换了个字段。
- 规格·日志面：`DeepSeekRequest` 的自述串只打印用途、版本与思考位（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequest.java:42`）；人设加入后这里只许多一个身份字段，不得带出附加段正文（承接 intent「Constraints」的「日志、测试报告与遥测不得记录提示词正文」）。

### 数据结构（主题列、回答记录列与前向迁移）

- 现状·主题行：`topics` 现有 4 列（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:276-282`），值对象 `Topic` 正好 4 个 final 字段、一个公开构造器（`android/core/src/main/java/com/treelearn/core/domain/Topic.java:10`、`android/core/src/main/java/com/treelearn/core/domain/Topic.java:18`），列映射读写各一处（`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:221`、`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:230`）。主题级可变字段今天只有 `last_visited_branch_id` 一个。
- 规格：加 `persona`，`TEXT NOT NULL` 并带默认档的 `wireId` 作 DEFAULT。默认值不是省事：它就是「新建主题即默认档」这条界面事实的落点，有了它读取侧不需要为「空的人设」发明语义；而 `ALTER TABLE ADD COLUMN` 落在非空表上本来也必须带默认值。
- 现状·迁移不是 Flyway：前向迁移是 `TreeLearnSchema` 里注册的编号步骤，共 4 步（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:838` 接口、`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:874` 注册表、`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:1037` 跑器——缺步即抛，拒绝给半成品库盖章），最新一步 V4→V5（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:1002`、`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:1009`），当前 `DATABASE_VERSION = 5`（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:61`）。
- 现状·加列先例只有一条，它把列定义写成了共用常量：`MIGRATION_V1_TO_V2_STATEMENTS`（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:859`）引用 `Messages.QUOTED_TEXT_DECLARATION`，声明理由在 `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:128`——新鲜安装与升级安装必须得到同一份列定义。
- 规格：本次加 V5→V6 一步、`DATABASE_VERSION` 5→6，`persona` 的列定义同样只许有一份文本，由 `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:276` 的建表块与新增那条迁移共用同一个以列为前缀、以 `_DECLARATION` 结尾的常量（先例是同一个文件里的 `Messages.QUOTED_TEXT_DECLARATION`）。
- 现状·迁移列带不上 CHECK：`ALTER TABLE ADD COLUMN` 携带不了 CHECK，「只在新装存在、升级库没有」正是它被排除的原因（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:47`），所以引号列的不变量改用触发器实现（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:705`）。
- 规格：`persona` 列**不加 CHECK**，档位合法性由 `:core` 的枚举全映射负责。理由不是「嫌麻烦」：CHECK 会在升级库上缺席，等于两条安装路径各有各的合法性；而界面方案要求的降级（未知档显示为默认）与 CHECK 直接互斥，见「错误语义」与疑虑点 1。
- 现状·回答记录：`answer_revisions` 现有 9 列（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:416-433`），其中的可复现身份是 `model`、`thinking_enabled`（带 CHECK 的先例，`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:430`）、`prompt_policy_version`（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:432`）三列；它**没有** `prompt_purpose` 列，口径写在注释里——用途能从版本前缀反查（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:145`）。生成中的操作行现有 14 列（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:436-455`）。
- 规格：`answer_revisions.persona` 与 `generation_operations.persona` 各加一列存 `wireId`。这一列不可省：版本前缀反查得出用途、反查不出人设，因为人设是叠加在同一版本上的第二个维度；intent 的「每条成功生成的回答记录带当时的人设身份、旧回答可复现」因此只能由列承载。它不触犯「任何列都不得存放 system 提示词正文」那条边界（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:55`）——存的是身份，不是正文。
- 规格·写侧：定稿时从操作行拷贝身份的路径只有一条（`android/data/src/main/java/com/treelearn/data/db/dao/GenerationStabilizer.java:126`），人设沿用它写入，不在第二处重新决定值。
- 规格·门禁面：主题列与回答记录列一改，`.codex/scripts/run_all_guards.py:122` 那条 schema 守门就要求 androidTest 的期望同步；`.codex/rules/maintainability.md:8` 的 800 有效行硬门禁则限制了这些改动往哪个文件放。本规格把它两条记成边界，不是提醒。

### 状态迁移（切换人设与既有生成、重试流程的相互作用）

- 切换只写主题行，只影响之后新起的生成；已定稿的回答不被改写（intent「Core flow」第 2 条）。这在数据结构上天然成立：`answer_revisions` 只有 insert（`android/data/src/main/java/com/treelearn/data/db/dao/AnswerRevisionDao.java:42`、`android/data/src/main/java/com/treelearn/data/db/dao/AnswerRevisionDao.java:125`），DAO 里没有 update 与 delete 面，存储层的触发器把两者一起拒了（`android/data/src/main/java/com/treelearn/data/db/dao/AnswerRevisionDao.java:18`）。
- 现状·主题行的生产写入口只有一处，且在发消息的那个事务里（`android/app/src/main/java/com/treelearn/app/wiring/SqliteChatStore.java:225`）；主题行创建也只有一个入口（`android/data/src/main/java/com/treelearn/data/db/dao/FirstTopicCreation.java:254`）。`TopicDao.update()` 是整行写回（`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:42`、`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:230`），并要求恰好命中一行（`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:50`）。
- 规格·因此：人设切换需要**新增一条只写 `persona` 的主题写路径**，不复用整行写回。为什么这算界面事实而不是实现细节：首页那一列按 `updated_at` 倒序排（`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:189`、`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:219`，该列定义在 `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:282`），整行写回会把「点了个人设」记成一次主题活动，把整棵树挪到首页顶部。走法交人拍板，见疑虑点 2。
- 与重试的相互作用：重试的命令由调用方给身份（`android/app/src/main/java/com/treelearn/app/generation/AnswerVersions.java:59`；接线层入口 `android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java:388`）。规格：重试取**当下**主题的档，不取失败那次的档——与版本字符串走同一口径。于是一次失败尝试与它的重试成功可以记着不同人设，这是既有口径的同一件事，不是新增缺陷。
- 中间态里另一个旋钮不跟人设同路：模型与思考模式的当前选择存在全局 SharedPreferences（`android/data/src/main/java/com/treelearn/data/settings/SettingsRepository.java:27`、`android/data/src/main/java/com/treelearn/data/settings/SettingsRepository.java:29`；端口面 `android/app/src/main/java/com/treelearn/app/ui/SettingsStore.java:16`），聊天页那个 chip 的选择甚至不落库（`android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java:900`）。所以同一条回答记录由两种生命周期共同写入：人设跟主题，模型跟会话与全局。这个缺口已由 intent「移交清单的已知缺口」记为待拍板，本规格不替它决定。
- 介绍页不在这条状态机上：静态文案、不发请求、不需要已配置 Key（界面方案「状态与异常」那条），对应到代码面就是它不碰上面任何一条链路，新增物只有文案资源与一个只读屏。

### 错误语义（未知档位的处置）

- 规格·写侧没有「未知档位」：能进列映射的只有枚举的 `wireId`，所以坏值只可能来自读取——迁移残留、降级安装、手工改库。
- 现状·同类先例是**抛**：`GenerationModel.fromWireId` 遍历不中即抛 `IllegalArgumentException`（`android/core/src/main/java/com/treelearn/core/domain/GenerationModel.java:23`、`android/core/src/main/java/com/treelearn/core/domain/GenerationModel.java:29`）。
- 冲突：界面方案「状态与异常」定的是读到未知档位时把「默认」显示为当前档并给一次 toast，不出现空白按钮、不阻断选择。抛与降级不能同时成立，所以这一条不是技术选择而是待拍板（疑虑点 1）；本规格按界面方案写要求，把冲突原样上报，不自行挑一条走完。
- 规格·无论拍板走哪条都不变的底线：**一行坏值不能让整个主题列表读失败**，坏值的影响面必须限于它自己那一档。这条底线的形状由读取入口决定——`TopicDao.read()` 直接 `new Topic(...)`（`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:221`），而 `Topic` 的构造器本身就在校验（`android/core/src/main/java/com/treelearn/core/domain/Topic.java:18`）：把未知档当成构造失败在构造器里抛，读的就是整屏。
- 现状·索要不适用的附加段属于编程错误：按既有拒绝抛（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:57`），不返回「没加人设」的静默成功。人设沿用同一条，派生用途要到人设即失败在请求组装前，不产生一次昂贵的调用。

- 来源：行号由 `artifacts/tmp/b032-pointers.py` 的锚点唯一命中给出，计数由 `artifacts/tmp/b032-counts.py` 现场数出，两份读数都记在片 032 卡片的 Verification report。需求口径来自 `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md` 的「Constraints and non-goals」「Decision record」与「移交清单」；条目依赖是 `.codex/ROADMAP.md` 的 B4（本节就是 B0 里 `DEFERRED:待 B4` 那一栏的兑现）。

## Doubts（疑虑点）

- 未决疑虑 2 条，都是技术规格补写时新撞出来的，不由本规格自行拍板：
  - 疑虑 1（未知档位的处置与仓库现成做法相反）：界面方案要「读到未知档位时把『默认』显示为当前档并给一次 toast，不出现空白按钮、不阻断选择」，而同一类坏值在生产代码里的现成处理是直接抛（`android/core/src/main/java/com/treelearn/core/domain/GenerationModel.java:23`、`android/core/src/main/java/com/treelearn/core/domain/GenerationModel.java:29`）。（冲突对象：界面方案「状态与异常」那条 与 `GenerationModel.fromWireId` 的先例；两种走法：A 抄抛——未知档让读取当场失败，界面那条降级要求随之改掉；B 在 DAO 边界做全映射回落默认档，并向上传递一个「回落发生过」的信号供 toast，代价是本仓同时存在两种未知值口径；谁拍板：用户本人。无论走哪条，技术规格「错误语义」里那条底线不变：一行坏值不得让整个主题列表读失败。）
  - 疑虑 2（切换人设算不算一次主题活动）：主题行今天的写入口是整行写回并带上 `updated_at`（`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:42`、`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:230`），而首页那一列正是按 `updated_at` 倒序排（`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:189`、`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:219`）。（冲突对象：intent「Core flow」第 2 条只说「选择立即写入该主题」，没规定它是否刷新活动时间；两种走法：A 新增只写 `persona` 的写路径，首页顺序不动；B 复用整行写回，代价是点一下人设就把这棵树挪到首页顶部；谁拍板：用户本人。技术规格已按 A 写出边界，但那是界面后果决定的，不是技术结论。）
- 已拍板（2026-09-21，用户本人）：
  - 最高规则的落点（原疑虑 1，走 A）：正文写进 `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md` 新增一条，`AGENTS.md` 只留一句指针并形成需求准入闸门，正文不复制。事实依据：全仓检索「宗旨」「最高规则」「最高原则」「北极星」，除两份人设 intent 与本规格引用这四个词的那一句外零命中，即这条规则目前没有产品正文锚点；而 `.codex/context/project-brief.md` 的「事实源（SoT）声明」把产品事实归 `Docs/**`、工程约束归 `.codex/rules/**`。理由：这条规则既是产品口径又是需求准入闸门，而权威正文只能有一份（把同一条规则写成两个入口，本仓已经发生过两次漂移）；`AGENTS.md` 自身声明是「索引摘要」，把正文放那里与它自己的定位冲突。代价：只读 `AGENTS.md` 的人要点开链接才看到全文。措辞定为「这个需求是否让用户学得下去、学得会」，不用「学习效率」——情绪价值档改善的是坚持率与体验，只写「效率」会把本次需求自己挡在门外。落点与本 intent 的 Decision record 一致。
  - 介绍页本期不放可点的增删改查按钮，只留一句「自建人设将来在这里管理」。
  - 7 档的入口是输入框那一行里、模型按钮旁边的人设按钮；不塞进模型弹窗，也不用底部滑板。
  - 回答上一律不出现人设标注。
  - 保持 7 档；介绍页示例里「学霸搭子」写得更专业、「深夜陪读」更侧重情绪。
