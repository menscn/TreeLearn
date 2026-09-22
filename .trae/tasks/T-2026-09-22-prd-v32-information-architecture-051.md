# Treelearn Harness Task: 新建 V3.2 全量 PRD 并改写失效的信息架构条目

id: T-2026-09-22-051
title: 新建 V3.2 全量 PRD 并改写失效的信息架构条目
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: spec-author
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿的信息架构与视觉**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。新稿引入底部标签栏并把主题列表与归档内容迁入独立「学习库」屏，这与已冻结的 PRD 条文直接冲突，所以必须先让文档与工程口径一致，否则迁移完成后工程与已验收的 PRD 自相矛盾。
- Problem/background: 新稿与工程是**同一产品**（新稿内嵌 PRD 与工程 V3.1 PRD 的 md5 相同：`a4d8fe8c42f5192ce442d6c2342eb26c`）。新稿改动的是信息架构——首页不再是唯一入口页，主题列表迁至学习库，搜索与归档入口换位置。这些是产品架构改动，不是样式改动。
- Expected outcome: 新建 `Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md`（**全量文档**，以 V3.1 为基线，不是增量条），使 PRD 与迁移后的工程口径一致；V3.1 PRD 作为已冻结历史版本保持内容原样。
- Core flow and reversibility: 这是文档任务，无运行时流程。可逆性体现在**历史处理**：V3.1 不被就地修改，V3.2 才是新的当前态；需要回溯时 V3.1 仍是完整的旧口径快照。
- Constraints and trade-offs: 只改**因本次 IA 迁移而失效**的条目，不得顺手改其他条文——PRD 是已验收规则，改动面越小，回归验收越简单。改写粒度止于信息架构，不借机调整功能语义（红线：功能规格一项不动）。
- Acceptance evidence: 由真实命令输出提供——新文件的存在性与章节齐备；改写点的逐条落位；对新文件的反向检索零命中。
- Facts: 冲突点经反向检索实测**共 8 处**，比父任务最初记录的 3 处多——除「首页为唯一入口页」「无第二套首页」「头部右侧依次提供搜索、已归档、设置三个入口」之外，另有 5 处同源表述在首页主题列表、首页流程节点、F16 需求条目与背景段中。
- Non-goals: 不改 V3.1 PRD 的正文；不把 V3.2 写成增量条；不新增或删减任何功能条目；不借本片调整搜索范围、归档语义、删除语义。

## Goal

产出一份与迁移后工程口径一致的 V3.2 全量 PRD，使「首页不再是唯一入口页、主题列表由学习库承载、搜索与归档入口换位」这三项 IA 变更在文档里有唯一权威表述，并让所有残留的旧口径被显式取代而不是被并存。

## Scope

### In scope
- 新建 `Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md`：以 V3.1 全量文档为基线复制，然后改写下列 8 处（行号指 V3.1 原件，供定位；V3.1 原件不改）：
  1. **L91** —「用户进入首页即可同时看到"开始新建"与"继续历史"两条路径…首页不得再出现两套品牌头部」
  2. **L164** — mermaid 首页流程节点 `A[用户进入首页] --> B[看到根问题输入框 / 推荐区 / 历史主题列表]`
  3. **L227** — F16 需求表格行「新建入口与历史主题列表合并为单一首页」
  4. **L693** — 背景段「消除"两个主页"的认知割裂」
  5. **L703** — 功能规格 1「首页为唯一入口页，包含固定的品牌头部；头部右侧依次提供搜索、已归档、设置三个入口」
  6. **L706** — 功能规格 4「首页在推荐区下方展示历史主题列表…」
  7. **L742** — 推荐失败不阻断「不得阻断首页输入与历史主题列表的使用」
  8. **L902** —「首页唯一性：首版只有首页一个入口页…新建与历史主题在同一页面内共存」
  9. **L977** — 验收表格「首页信息架构统一」行（含「无第二套首页」）
  10. **执行时补入（原作业单遗漏的覆盖面）**：V3.1 第 6.2.16 节「首页信息架构统一」内，除 L703/L706 外另有约 8 行仍以「首页承载主题列表 / 头部三入口」为前提（V3.2 对应行 L705、L706、L707 的场景示例，L715、L716 的功能规格 5/6，L720、L722 的异常 1/3，共 7 行），以及第 7.2 节 L973「首页历史主题卡片」一行。只改 L703/L706 会让这些相邻行与新写法直接矛盾，违背本任务 Goal「让所有残留的旧口径被显式取代而不是被并存」。**这 8 行属于本任务范围**，一并改写；改写同样不得改动任何 F 编号、不得调整任何功能语义，只把「入口与归属」的表述对齐新 IA。
- 在 V3.2 内明确新口径：首页不再是唯一入口页；底部标签栏承载主页/学习库/设置三个一级入口；主题列表由学习库承载；搜索的唯一入口是学习库顶部搜索框；归档动作留在分支导航屏；「无第二套首页」改写为「首页不重复承载主题列表」。同时保留 V3.1 已有的功能条目原文语义（F16、F17、F18 等的能力不缩水）。
- **已裁决：推荐区相关 4 行一字不改**（用户 2026-09-22 裁决「发现话题」保留弹幕形态）。以下四行**不在**本任务的改写范围，V3.2 中也不得出现「重点卡」类表述：
  - L49（需求清单）「首页增加推荐主题，结合已学内容推荐，可点击，弹幕滚动」
  - L96（交互约束）「滚动中的推荐项必须可稳定点击…」
  - L228 — F17【P0】「依据已学内容生成推荐主题，弹幕滚动展示，点击直接创建主题并开始对话」
  - L978（验收表格）「推荐区双行反向滚动，交互时暂停；分「顺着你学的 / 换个方向 / 入门」标签…」
  - 理由：弹幕形态是该 P0 条目的**能力**而非纯装饰，改它越过红线；新稿的「重点卡 + 三条列表项」形态记为 deferred，偏差已在 SPEC 登记。
- 历史文件加**指向性批注**（只增批注，不改内容行）：
  - `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md` L18（「首版只有一个首页…同一首页组织」）加注：已被 V3.2 取代。
  - `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` L2404（「单一首页…全在一页」）加注：该叙事已被 Figma 新稿取代；本文件仍作为设计 token 的 `:root` 权威源。

### Out of scope
- `Docs/03-prd-draft/V3.1-…PRD.md` 的任何正文行（冻结历史）
- `android/**` 全部代码与资源
- `Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`（本批的分析产物，已定稿描述差异分类）
- 上一轮已定稿的 `Docs/spec/SPEC-2026-09-22-figma-ia-migration.md` 与 `Docs/intent/INTENT-2026-09-22-figma-ia-migration.md`

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**文档口径**（入口页数量、主题列表归属、搜索与归档入口位置）；必须原样保留的是全部**功能条目**——归档与恢复语义、删除的整树与不可逆语义、检索的六类字段覆盖、生成的中断/重试语义、无 Key 守卫。红线：功能规格一项不动。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 文档的生产者是本任务；消费者是后续实现任务（052–055）与后续验收（verifier 按 PRD 判定）。代码侧的对应生产者是 `MainActivity` 的换屏注册与 `screen/HomeScreen.java`、`navigation/NavigationScreen.java`，本片不改它们。
- Native bridge or independent-client compatibility/update requirements: 不适用——无跨端契约与外部客户端。
- Behavior-neutral non-applicability reason, if applicable: 本片只改文档，不产生运行时行为变化；「行为影响」一节在此用于声明**哪些功能条目受保护、不得被文档改写连累**。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts（由后续实现任务落地，本片只先行记录口径）: 首页不再是唯一入口页；出现底部标签栏三个一级入口；主题列表与归档内容的家迁至学习库；搜索的唯一入口是学习库顶部搜索框；归档动作留在分支导航屏。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/MainActivity.java`（换屏注册）、`android/app/src/main/res/layout/screen_home_body.xml`（现行首页头部三入口）、`android/app/src/main/res/layout/activity_main.xml`（单一宿主容器）。
- Documentation surfaces checked: `Docs/03-prd-draft/`、`Docs/01-demand/`、`Docs/04-ux-design/`、`Docs/06-delivery/`、`Docs/05-technical-design/`、`README.md`、`README_EN.md`、`android/README.md`。
- Documentation files updated: 新建 V3.2 全量 PRD；`Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md` 与 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 各加一条指向性批注。
- Producer/consumer/sibling/compatibility paths checked: 后续以 PRD 为验收依据的 052–055 四片；`Docs/06-delivery/TreeLearn_V1_交付报告.md` 中关于「搜索页（T-040）已接线」的记载与本批「视图层退役」的关系（交付报告是历史快照，不改写，但需确认新 PRD 与它的差异已被本批其它任务记录）。
- Superseded phrases/examples reverse-searched: 「唯一入口页」「无第二套首页」「头部右侧依次」「头部提供搜索/归档/设置」「品牌头」「历史主题列表」——已反查 `Docs/**` 与两个 README，命中位置即上方 Scope 的 8 处加两个历史文件。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次确有当前态文档变更。
- Historical/PRD handling: V3.1 PRD 与 V3.1 增量需求基准说明书均为**已冻结历史版本**，内容行原样保留，只加一条指向 V3.2 的批注；V3.2 是全量文档而非增量条，并在开头说明「V3.2 相对 V3.1 只改信息架构相关条目，功能条目未增未减」。

## Acceptance criteria

- [x] `Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md` 存在，且为**全量文档**而非增量条：章节结构与 V3.1 对应可比，不出现「仅记录变更点」的形态。
- [x] 上方 Scope 列出的 9 处（含 L977）在 V3.2 中逐条落位；每条都能在 V3.2 里找到改写后的表述。
- [x] 反向检索零残留：`grep -rn "唯一入口页\|无第二套首页\|头部右侧依次" Docs/03-prd-draft/V3.2-*.md` **零命中**；同一命令在 V3.1 上仍有命中（证明检索本身有效，不是模式写错）。**检索词集合必须扩展到本次实测暴露的漏网表述**：至少覆盖「唯一入口页」「无第二套首页」「头部右侧依次」「历史主题列表」「首页历史主题」，并对 V3.2 逐个证明零命中、对 V3.1 逐个证明有命中。**只查前三个词是本次的教训**——正是它们漏掉了第 6.2.16 节里另外 8 行同源前提。
- [x] 两个历史文件各新增**一条指向 V3.2 的批注**，且 `git diff` 证明它们的内容行未被改写（只有新增行，无修改行与删除行——除批注所在行的插入）。
- [x] 功能条目零增删：V3.2 的功能条目（F 编号集合）与 V3.1 一致，可由 `grep -o "| F[0-9]\+" ... | sort -u` 的两次输出逐行比对证明。
- [x] 命令与退出码落进 `artifacts/evidence/T-051-prd-v32.txt`，文件自带命令行与 `EXITCODE=` 行。

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用——文档任务不存在可先写的失败测试，等价判据为上方的反向检索与条目集合比对断言。
- Command: 不适用
- Exit code: 不适用
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation
- Implementation: 新建 V3.2 全量 PRD，改写 17 处 IA 条目，含 In scope 第 10 条补入的 8 行；为两个历史文件各加一条指向性批注。
- Command: bash artifacts/evidence/.t051-evidence.sh
- Exit code: 0
- Passing evidence: artifacts/evidence/T-051-prd-v32.txt
- Regression evidence: artifacts/evidence/T-051-prd-v32.txt 的 [2][3][3b][4][5][6][10] 节

### REFACTOR — optional
- Cleanup: 不适用
- Regression evidence: 不适用

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（文档类任务，无生产代码，不存在可先写的失败测试）
- Reason: 交付物是一份 PRD 文档；可执行的判据是反向检索与条目集合比对，属结构断言而非行为测试。仓库对 docs/config 类任务已有同类先例：`T-2026-09-21-roadmap-and-agent-routing-031.md` 即以「用户批准 + 结构断言自检脚本」作为替代验证。
- User approval: 用户于 2026-09-22 **明确批准**本任务的文档类 TDD 例外（用户原话：「051 的文档类 TDD 例外  批准」）。批准发生在实现之前，非事后追认。
- Alternative verification: 上方六条验收标准，其中反向检索与 F 编号集合比对由真实命令输出证明。

## Implementation notes

- 「全量文档」是本任务最容易做错的地方：V3.2 必须是一份可以独立阅读的完整 PRD，不是在 V3.1 上贴的差异说明。基线复制后**改写**受影响条目，其余原样继承。
- 冲突点比父任务最初记录的多：父任务只记了三处措辞，反向检索实测出**8 处 + 1 行验收表格**。不要只改那三处——L706 与 L742 同样以「首页展示历史主题列表」为前提，漏掉它们会让 PRD 内部自相矛盾。
- 改写时守住红线：改的是**入口与归属**，不是能力。F16 的「消除双主页割裂」意图仍然成立（现在有底部标签栏承担一级导航，反而更彻底），但表述必须从「单一入口页」升级为「一级入口由标签栏承载、首页不重复承载主题列表」。
- `Docs/06-delivery/TreeLearn_V1_交付报告.md` 记载「搜索页（T-040）已接线」，本批将退役其视图层。这是**历史快照**，不改写；但 V3.2 里关于搜索入口的表述必须与「学习库顶部搜索框是唯一入口」一致，且不能声称独立搜索屏仍存在。
- 两个历史文件的批注要写成**指向性**（「本文件此处口径已被 V3.2 取代，见 …」），不得改动被指向的内容行——`git diff` 会逐行验证这一点。

## Verification report

- Verdict: **PASS**（独立复核者：`agent-38209e44`，2026-09-22，READONLY，未采信实现者结论）
- 独立复核原始报告摘要：
  - 5 词反向检索逐词 `grep -c` 实测：`唯一入口页` V3.2=0 / V3.1=1；`无第二套首页` V3.2=0 / V3.1=1；`头部右侧依次` V3.2=0 / V3.1=1；`历史主题列表` V3.2=0 / V3.1=5；`首页历史主题` V3.2=0 / V3.1=1。5 词对 V3.2 全零命中、对 V3.1 全部有命中 → 检索模式有效，零残留成立。
  - F 编号集合：`grep -o "| F[0-9]\+" ... | sort -u` 两次输出各 21 个，逐行 `diff` 无输出（F01–F21 一致）。
  - `git diff --numstat`：`01-demand/…V3.1增量.md` = 1 增 0 删；原型 HTML 相对 HEAD = 29 增 28 删，但按 hunk 归属拆开后 **051 部分 = 1 增 0 删**（`@@ -2404,0 +2405 @@` 纯插入），其余 28 处为 050 的 `:root` 数值改写（8 个 hunk 全在 L22–71）。
  - 推荐区 4 行：V3.1 L49/L96/L228/L978 原文以 Python 精确 `==` 在 V3.2 中找到整行逐字相同（对应 V3.2 L57/L104/L236/L986）；`重点卡` 在 V3.1 与 V3.2 均 0 命中。
  - 「合法命中」独立逐行复判：`历史主题` 6 处（L62 需求来源表 / L172 本轮改写后的新 IA 描述 / L176 mermaid 用户动作分支 / L701 V3.0 历史问题描述 / L742·L749 F17 推荐区分类条件）、`品牌头` 3 处（L99·L910 否定式「不得再出现第二套」/ L701 历史描述）、`两个主页` 2 处（L62·L701 历史记录）——**无一条在当前态描述里以旧 IA 为前提**，判定成立，无漏改。
  - 全仓反向查找：5 词在 V3.1、`.trae/`、`artifacts/` 之外的命中只剩迁移方案 V1.0（Out of scope）、SPEC/INTENT（计划性表述）、已批注的 01-demand L18 与原型 L2404，无「当前态仍按旧 IA 断言」的残留。
  - 相对链接独立补跑：V3.2 的 2 条 markdown 链接 + 7 处反引号路径引用，目标 0 缺失（实现者证据文件缺此项，已由复核者独立补证，不构成阻塞）。
  - 越界检查：`git status` 中 android 侧改动仅 `README.md`/`colors.xml`/`dimens.xml`/`themes.xml`，与 050 声明领地完全吻合，**051 未触碰任何 android 文件**。
  - 任务合规：`validate_tasks.py` 对 051 → `files=1 errors=0 warnings=0`；文件名 ↔ `id` 一致；父任务存在；分区齐全；`status` 在复核时为 `verifying`（实现者未自判）。
  - TDD 例外：`tdd_mode: approved-exception` + 用户原话与事先时点记录在案（父任务 049 独立复述同一原话），复核者仅判「记录存在且由用户作出、记为事先」——成立。
- Evidence: `artifacts/evidence/T-051-prd-v32.txt`（596 行 / 46 条 `EXITCODE=`）。
- Evidence 补充（第 2 轮，In scope 第 10 条）: 补入的 8 行在 V3.2 的 L705、L706、L707、L715、L716、L720、L722、L973 逐条落位；扩围后的 5 词检索集合（唯一入口页 / 无第二套首页 / 头部右侧依次 / 历史主题列表 / 首页历史主题）对 V3.2 逐个零命中、对 V3.1 逐个有命中；宽口径旧口径词在 V3.2 的合法保留命中已逐行判定并给理由。改写点合计 17 处。原始输出见 `artifacts/evidence/T-051-prd-v32.txt` 的 [3]、[3b]、[10] 节。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用——不涉及测试断言变更。
- Gaps: PRD 的可读性与「口径是否真的自洽」最终由人 review 判定，结构断言只能证明检索零残留与条目未增删。
- Risks: 若只改了三处显式措辞而漏掉隐含前提（L706、L742），V3.2 会内部矛盾，且后续 052–055 的验收依据会被误读；反向检索的覆盖面因此是必须项而非可选。
- Risks（复核者补记）:
  - R1（低级）`artifacts/evidence/_t050_gen.py`、`_t050-nokotlin.log`、`_t050-nokotlin.utf8.txt` 及 `.t051-*` 辅助脚本均**未被 `.gitignore` 覆盖**（`.gitignore` 仅含 `artifacts/evidence/.captures/`），存在误入库可能。属 050 片/后续收尾动作，不阻塞本片。
  - R2（低级）原型 HTML 的 L2405 批注额外声明「全站霞鹜文楷」口径亦作废并引 050 片——比本片 Scope 的单点（L2404「单一首页」叙事）略宽。因是纯新增行、被指向内容行未改，不判违规。
  - R3（低级）mermaid L176 `C -->|点击历史主题| E[…]` 挂在 `A[用户进入主页]` 之下，虽同图 B 节点已写明「回查历史主题走学习库」且动作本身未变，读图者仍可能误读为首页入口。V3.2 若后续有修订机会，建议把该边标签改为「在学习库点击历史主题」以消歧。
  - R4（记录性）证据文件未含相对链接检查行（已由复核者独立补跑通过）；重建证据时该缺口会复现。
  - R5（本质性）PRD 口径自洽最终依赖人 review，结构断言只能证明「5 词零残留 + F 编号未增删 + 17 处落位」。

## Handoff notes

- Completed: 冲突点已全量反向检索并定位（8 处 + L977 验收行 + 2 个历史文件）；功能条目保护清单已明确；V3.2 全量 PRD 已产出并改写 17 处，含 In scope 第 10 条补入的 8 行；两个历史文件各加一条指向性批注。
- In progress: 无。实现与独立复核均已完成，`status: verified`。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 本片已 verified，052 的依赖（050 + 051）双双满足，可开工。派工顺序仍为 052 → 053 → 054 → 055 串行。
- Watch-outs: (1) 不得写成增量条；(2) 不得改 V3.1 的内容行；(3) 不得只改显式措辞而漏隐含前提；(4) 不得借本片调整任何功能语义；(5) **与 050 的写冲突**：本片要在 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的叙事处加批注，而 050 要改同一文件的 `:root`——按共享代码区规则两者不得并行，派工时 **050 先、051 后**。注意这是写冲突而非技术依赖：`depends_on` 仍为空（本片不消费 050 的产物），串行只由文件归属决定。
