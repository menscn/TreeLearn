# Treelearn Harness Task: Figma 新稿信息架构与视觉迁移（父需求）

id: T-2026-09-22-049
title: Figma 新稿信息架构与视觉迁移（父需求）
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 现有 Android 工程的表现层停在 v1「生长绿」设计系统，而用户已在 Figma 做出精修稿（暖白纸感底、深绿墨、衬线品牌字、线性图标、克制的描边与阴影），并改动了信息架构（引入底部标签栏，主题列表与归档内容迁入独立的「学习库」屏）。两侧是**同一产品**：新稿内嵌的 PRD 与工程内 `Docs/03-prd-draft/V3.1-知芽（TreeLearn）产品需求文档（PRD）.md` 的 md5 完全一致（`a4d8fe8c42f5192ce442d6c2342eb26c`），所以迁移性质是「换底 + 改壳 + 补件」，不是重写。
- Users/scenarios: 唯一用户是产品经理本人，同时是唯一决策人与唯一复核者。场景是在 Android 手机上学习陌生领域：发起学习、在分支间导航、用画布看整棵树、检索已学内容、整理不再需要的探索路径。
- Expected outcome: 见 `Docs/spec/SPEC-2026-09-22-figma-ia-migration.md` 的「Requirement spec」与「Interface plan」。要点：吸收新稿配色与度量；底部标签栏（主页/学习库/设置）；学习库承载主题与归档列表并成为检索唯一入口；首页瘦身；归档动作落在分支导航屏；删除保持整树语义；补约 28 个线性图标与画布点阵装饰；新建 V3.2 全量 PRD。
- Core flow and reversibility: 学习路径为「主页提问 → 对话 → 追问或分叉 → 画布/导航查看 → 学习库回查」；整理路径为「分支导航 → 归档 → 学习库已完成区 → 整体恢复或经二次确认永久删除」；查找路径为「学习库搜索框 → 结果带祖先路径与归档状态 → 定位」。归档可整体恢复且不重接后代；删除不可逆、需二次确认、确认前先取消在飞生成；生成中断保留部分内容并可原位重试。
- Inputs/outputs: 输入为根问题、追问文本、搜索关键词、归档/恢复/删除指令、模型档位与思考开关；输出为树形结构、AI 回答、检索结果列表、学习库的主题与归档列表。
- State and exception handling: 无可用 Key 时不创建主题，只在主页提示；检索四态（空闲/进行中/有结果/无结果或失败），失败保留草稿，空查询不触库；行内确认态用于删除；学习库两条分段各有独立空态文案。生成三态（生成中/稳定/失败）沿用现状。
- Constraints and trade-offs: 红线是**只改交互与表现，不动功能规格**；零 AndroidX、Java-only、不新增第三方依赖；设计 token 三处同步（原型 `:root` → `tokens.json` → `colors.xml`）后跑守门脚本；单任务生产代码行目标不超过 200、超过 500 为失败（口径为非空非注释行，用户 2026-09-21 裁定）；本轮不打包字体。权衡偏好是宁可分批交付可独立验证的切片，也不做一次性大改；宁可保留功能的最朴素入口，也不砍能力换观感。
- AI recommendation and rationale: 建议分三层推进——设计基座（只改 token 文件，零交互逻辑风险但覆盖全部屏幕）、外壳与信息架构（唯一改产品语义的一层，需先改 PRD）、组件与细节（纯增量）。理由是把风险分层隔离，避免一屏一屏重写；且工程现首页与对话屏的体量均已超过单任务预算，任何「重做一屏」都注定要切片。
- User choice and custom additions: 用户 2026-09-22 逐项选择——(1) 信息架构**改**，走 PRD 增量正式变更；(2) 字体取**正文系统 `sans-serif` + 品牌字 `serif` 族回退 + 本轮不打包**；(3) 流程**走 Harness**；(4) 归档动作入口放**分支导航屏内**；(5) 搜索取**库内单框为唯一入口但检索引擎不换**（此条经一次回退澄清才定：用户初选「只保留库内过滤框」，AI 核实该走法会削掉 PRD 第 214 行 F08【P0】的六字段检索、且日常非根分支将完全搜不到，与红线互斥，用户改选升级方案）；(6) PRD 落点为**新建 V3.2 全量 PRD**；(7) 本批范围为**全量含画布装饰**。用户自定义补充一句红线：「这次只是交互的调整，不能影响功能的规格！」
- AI-autonomous decisions: 迁移中用 token 名引用设计数值而不在文档里复写色值清单；把「设计基座」排为本批第一个独立验收切片；「发现话题」的视觉形态**不再按新稿改**（用户 2026-09-22 裁决保留弹幕，新稿的重点卡加列表形态记为 deferred，偏差已在 SPEC 登记）。
- User-confirmation decisions: 信息架构是否变更（已确认：改）；字体落地方式（已确认：回退栈）；是否走 Harness（已确认：走）；归档入口位置（已确认：分支导航屏）；搜索落地方式（已确认：库内框升级为完整检索）；PRD 落点（已确认：V3.2 全量）；批次范围（已确认：全量）。SPEC 的疑虑点已逐条落定（现为 8 条），其中两条由用户直接裁决：推荐区形态「保留弹幕」、051 的文档类 TDD 例外「批准」。
- Acceptance evidence: 由各子任务自己的真实命令输出提供，不自述。要求包含：`check_design_tokens.py` 的退出码；对比度审计脚本对回退项的零命中；`android/scripts/no-kotlin-check.ps1` 与 `gradlew lint` 的结果；检索六个字段类各命中一次的仪器测试；归档/恢复/整树删除的仪器测试；首页不再出现历史主题列表与头部三入口的界面核对。
- Facts: 新稿内嵌 PRD 与工程内 V3.1 PRD md5 相同；工程配色 100% 走 `tl_*` token；间距/圆角/字号 100% 走 `res/values/dimens.xml`；`res/drawable/` 无任何 UI 图标；无 `res/font/`；`SearchDao` 是七臂 `UNION ALL`（标题、问题、锚定原文、回答、思考、失败尝试的半成品内容）；整树删除与级联已是既有行为（`SqliteSchemaInstrumentedTest.deletingABranchSubtreeCascadesItsDescendantsOnly`、`NavigationData.Plan`）；新稿的库内过滤框只比较主题根标题/摘要与已归档分支的标题/摘要；新稿的归档动作无任何触发点。
- Assumptions: 新稿的「简约高级」观感主要来自配色、字形、图标与克制的描边，而不是底部标签栏本身；底部标签栏是设计稿不可退让的部分，决定了首页的瘦身形态。
- Unknowns: Vector Drawable 这类 XML 资源是否计入生产代码行预算，需按 `.trae/scripts/dev/count_production_lines.py` 的实际口径确认。
- Risks: 主风险是功能回归（设计稿本身缺归档入口、删子树语义不完整、把检索降级成过滤框），已列入必须按 PRD 保留的项；次风险是规模（首页与对话屏现有体量已超单任务预算）；再次是画布装饰会碰到自绘逻辑与已验收的布局测试，必须只做叠加、不改布局行为。另有流程风险：`dimens.xml` 是手工维护、守门脚本不校验间距/字号/字体，改度量没有脚本兜底。
- Deferred: 暗色模式；字体子集化内嵌；图标描边的逐枚精修；画布交互的进一步改造。
- Non-goals: 不新增任何功能；不调整任何已有功能的语义；不做暗色模式；不迁移原型的内存数据模型与演示假数据；不改动已验收的纯 Java 树布局算法；不把新稿的 SVG 布局常量搬进工程；不把独立搜索屏直接删掉而不承接它的检索能力。
- Skipped non-critical questions and reason: 未追问 SPEC 六条疑虑点之外的设计细节（图标描边的具体网格、卡片阴影档位选择等），因为它们不改变范围、架构或验收方式，按既有 token 档位取值即可。

## Goal

产出一份经用户确认的需求与交互规格，并把「Figma 新稿的信息架构与视觉迁移」拆成有界、可独立验证的 Harness 子任务，交付一个配色与信息架构已迭代到新稿、且功能规格一项未减的 Android App。

## Scope

### In scope
- `Docs/04-ux-design/`（设计 token 权威原型与 `design-system/tokens.json`）
- `android/app/src/main/res/values/colors.xml`、`dimens.xml`、`themes.xml`
- `android/app/src/main/res/layout/` 与 `layout-sw600dp/`（新增学习库布局、首页瘦身、标签栏容器）
- `android/app/src/main/java/com/treelearn/app/`（`MainActivity` 换屏注册、新增学习库的屏与 presenter/view-state、首页瘦身）
- `android/app/src/main/res/drawable/`（新增线性图标）
- `Docs/03-prd-draft/`（新建 V3.2 全量 PRD）
- `.trae/tasks/`（子任务文件）

### Out of scope
- 数据库 schema、migration、DAO 语义（不新增表、不改 migration）
- AI 网关、提示词、生成流程、派生产物的业务逻辑
- 原型 `src/` 下的任何代码（只作设计意图来源，不作为迁移目标）
- 暗色模式

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是入口位置、导航结构与视觉表现；必须原样保留的行为是归档与恢复的语义、删除的整树与不可逆语义、检索的六字段覆盖与迟到响应丢弃、生成的中断/重试语义、无 Key 时不创建主题的守卫。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 归档与删除的生产者是 `NavigationPresenter`（`android/app/src/main/java/com/treelearn/app/navigation/NavigationPresenter.java`），消费端口是 `NavigationData`；检索的生产者是 `SearchPresenter`，消费端口是 `SearchRepository` → `SqliteSearchRepository` → `SearchDao`；换屏的单一入口是 `MainActivity` 的私有 `swap()`。平台分支为手机布局与 `layout-sw600dp/` 两套，新增屏两边都要覆盖。
- Native bridge or independent-client compatibility/update requirements: 不适用——本项目为单一 Android 客户端，无跨端契约与外部客户端。
- Behavior-neutral non-applicability reason, if applicable: 数据库与 AI 侧不发生行为变化，理由是本父需求范围内不修改 `android/data/`、`android/core/` 与 `android/ai/` 的任何语义；子任务若需要触碰它们，必须在各自任务里单独声明并重新评估。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首页不再是唯一入口页；出现底部标签栏；主题列表与归档内容的家从首页迁到学习库；检索入口从独立搜索屏迁到学习库顶部的搜索框；配色与度量 token 的值变更；品牌字改为系统衬线回退栈。
- Authoritative code/config/migration evidence: `android/app/src/main/res/values/colors.xml`、`dimens.xml`、`themes.xml`；`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root`；`Docs/04-ux-design/design-system/tokens.json`；`android/app/src/main/java/com/treelearn/app/MainActivity.java`。
- Documentation surfaces checked: `Docs/03-prd-draft/V3.1-知芽（TreeLearn）产品需求文档（PRD）.md`（第 703 行、第 977 行的首页信息架构条目）、`Docs/04-ux-design/design-system/README.md`、`android/README.md`（UI 约定与对比度记录）、`Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`、`README.md` / `README_EN.md`。
- Documentation files updated: 待子任务执行时落地——新建 `Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md`；更新 `Docs/04-ux-design/design-system/tokens.json` 与其 `README.md` 的变更记录；更新 `android/README.md` 的对比度条目与尺寸令牌说明。
- Producer/consumer/sibling/compatibility paths checked: `MainActivity`（换屏）、`NavigationPresenter` 与 `NavigationData`（归档与删除）、`SearchPresenter` 与 `SearchDao`（检索）、`HomePresenter`（无 Key 守卫与推荐）。
- Superseded phrases/examples reverse-searched: 「首页为唯一入口页」「无第二套首页」「头部右侧依次提供搜索、已归档、设置三个入口」三处措辞需在 V3.2 中改写；反向检索应覆盖全部 `Docs/` 与两个 README。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次确有当前态文档变更。
- Historical/PRD handling: V3.1 PRD 保持原样作为已冻结历史版本，不就地修改；新信息架构写进新建的 V3.2 全量 PRD，并在其中说明 V3.2 相对 V3.1 只改信息架构相关条目。

## Acceptance criteria

- [ ] 需求与交互规格已成稿并通过自检：`python .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-22-figma-ia-migration.md` 零失败，且 `Docs/intent/INTENT-2026-09-22-figma-ia-migration.md` 为 accepted。
- [ ] 六条疑虑点逐条获得用户拍板，或明确记录为延后并说明不影响本次拆分。
- [ ] 子任务已按「移交清单」的依赖顺序拆出，且每个子任务的 `estimated_production_lines` 在 200 行目标内；无法压到 200 以内的给出切分理由。
- [ ] 父需求任务本身通过机械校验：`python .trae/scripts/validate_tasks.py` 对该文件零错误。
- [ ] 保留行为不退化：归档、恢复、整树删除、六字段检索、无 Key 守卫这五项在子任务的验收里都有对应条目，且由真实命令或仪器测试证明。

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用——父需求任务不含生产实现，TDD 证据由各子任务提供。
- Command: 不适用
- Exit code: 不适用
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation
- Implementation: 不适用——本任务只产出需求、规格与任务拆分。
- Command: 不适用
- Exit code: 不适用
- Passing evidence: `check_spec_author_agent.py` 与 `validate_tasks.py` 的退出码。
- Regression evidence: 不适用

### REFACTOR — optional
- Cleanup: 不适用
- Regression evidence: 不适用

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（父需求任务无生产实现）
- Reason: 父需求聚合子任务证据，自身不含业务改动
- User approval: 用户于 2026-09-22 明确选择走 Harness 并确认需求
- Alternative verification: 各子任务的真实命令输出与仪器测试，加上最终集成验证

## Implementation notes

- 设计系统的当前态权威是 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root` 区块；改设计的固定顺序是先改该处、再同步 `tokens.json`、再同步 `colors.xml`、最后跑守门脚本。
- 新稿自身有两个 token 不可照搬：`ink3` 与 `amber` 的取值会使对比度回退到 WCAG AA 之下（实测数字与修正候选见 `Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md` 与对比度审计脚本）。这是本批必须先处理的一项。
- 设计稿是意图，生产代码是事实。设计稿缺归档入口、删除只删单节点不删子树、把检索降级成过滤框这三处，按 PRD 走，不按设计稿走。
- 新稿的 `src/App.tsx` 从未导入 `SearchScreen.tsx`，该屏是死代码——这一点是本批「搜索不得降级」判断的直接证据。

## Verification report

- Verdict: PENDING
- Evidence: 待全部子任务验证完成后汇总；本任务自身的证据为两个自检脚本的退出码（`check_spec_author_agent.py` 已实测零失败）与 `validate_tasks.py` 的输出。
- Changed/deleted assertion rationale and equivalent behavior coverage: 待子任务给出。
- Gaps: 本任务尚未产生任何 Android 代码（子任务未开工）；原「六条疑虑点待拍板」与「Vector Drawable 行数口径未定」两项已结清（前者已逐条落定，后者按 `count_production_lines.py` 第 30 行判定为计入）。新登记的缺项有两处：055 的「发现话题」形态待用户裁决；053 的归档行「锚定原文 / 末条回答片段」在既有快照中不存在，需按只读查询或降级记录处理。
- Risks: 见协作记录的 Risks 一节。

## Handoff notes

- Completed: 需求澄清（7 项决策全部获得用户明确回答，含一次针对搜索降级的冲突回退）；`Docs/intent/INTENT-2026-09-22-figma-ia-migration.md`（accepted）；`Docs/spec/SPEC-2026-09-22-figma-ia-migration.md`（自检零失败）；`Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`（现状核对与差异分类）；**9 个子任务文件已落盘**并通过机械校验。
- In progress: 050、051、052 均已 verified（051 复核者 `agent-38209e44`；052 复核者 `agent-dc545c33` 全量轮判 PARTIAL 并给出三处文字修法 + `agent-0eae13ee` 窄范围复查轮判 PASS）。052 是 IA 链条的首片，它的完成解锁 053。落盘时的实测证据：`python .trae/scripts/dev/validate_tasks.py` 覆盖新建 9 片的结果为 `files=9 errors=0 warnings=4`、全库为 `files=60 errors=0 warnings=19`（退出码均为 0）。
- Blocker: 无。原两项待拍板事项已于 2026-09-22 裁决完毕（055 的推荐区保留弹幕、051 的文档类 TDD 例外获明确批准）。
- Unblock condition: 不适用。开工前置只有依赖关系与文件归属：IA 链条四片须等前一片 verified；056/057/058 须等 050 verified，且三片之间依次串行（写冲突，见 Implementation notes 的第五处修正）。
- Next steps: 按下方子任务索引与依赖顺序派工；**050 必须先于 051**（写冲突，见下）；052 起必须等 050 与 051 都完成；IA 链条 052 → 053 → 054 → 055 必须依次串行（共享 `MainActivity` 与学习库文件）；056/057/058 只依赖 050，但**无技术依赖不等于可并行——三片同样必须串行**（三片都写 `res/values/strings.xml` 与 `android/README.md`，且都需在同一个工作树里运行 Gradle，构建目录会互相争抢）。每个子任务完成必走 verifier，verifier 串行。
- 落盘时相对初稿的实质修正（供复核）：(1) PRD 冲突点由 3 处实测扩为 **8 处 + 1 行验收表格 + 2 个历史文件**，另有推荐区 4 行属条件性改写（见 051 的 Scope 与 055 的 Blocker）；(2) 初稿「053/054/055 三者互不依赖可并行」经文件归属核对为**不成立**，已改为串行；(3) 053 的「锚定原文 / 末条回答片段」在既有快照（`NavigationViewState.Row`、`NavigationData.Node`）中**不存在**，已写成显式的只读查询扩权声明与降级判定规则；(4) 图标片为避开文件冲突，把标签栏图标划给 052、画布控制条图标划给 058，056/057 只做非 IA 领地。

### 子任务索引

| id | 描述性文件名 | owner_lane | stack | 预估生产行数 | depends_on | shared_code |
| :--- | :--- | :--- | :--- | ---: | :--- | :--- |
| T-2026-09-22-050 | `T-2026-09-22-design-token-baseline-sync-050.md` | android | android | 30 | 无 | 是 |
| T-2026-09-22-051 | `T-2026-09-22-prd-v32-information-architecture-051.md` | docs | docs | 0 | 无 | 否 |
| T-2026-09-22-052 | `T-2026-09-22-bottom-tab-shell-and-library-screen-052.md` | android | android | 340 | 050、051 | 是 |
| T-2026-09-22-053 | `T-2026-09-22-library-archived-list-and-subtree-delete-053.md` | android | android | 260 | 052 | 否 |
| T-2026-09-22-054 | `T-2026-09-22-library-inplace-search-and-search-screen-retire-054.md` | android | android | 330 | 052、053 | 是 |
| T-2026-09-22-055 | `T-2026-09-22-home-slimming-055.md` | android | android | 300 | 052、053、054 | 是 |
| T-2026-09-22-056 | `T-2026-09-22-icon-assets-navigation-and-tabs-056.md` | android | android | 200 | 050 | 否 |
| T-2026-09-22-057 | `T-2026-09-22-icon-assets-branch-and-actions-057.md` | android | android | 200 | 050 | 否 |
| T-2026-09-22-058 | `T-2026-09-22-canvas-grid-backdrop-overlay-058.md` | android | android | 120 | 050 | 否 |

**索引相对初稿有三处依赖修正**（落盘时按实际文件归属重算，理由见每片的 `depends_on` 与 Watch-outs）：

1. **054 由「只依赖 052」改为「依赖 052、053」**——053、054、055 都要改同一批学习库文件（`screen/LibraryScreen.java`、`library/LibraryViewState.java`、`screen_library_body.xml`）与 `MainActivity`，三者**不可并行**。初稿把三者写成互不依赖是错的。
2. **055 由「只依赖 052」改为「依赖 052、053、054」**——首页瘦身要摘掉头部三入口与历史主题列表，前提是它们的替代落点已可用：设置由标签栏（052）、归档内容由学习库已完成区（053）、搜索由学习库搜索框（054）。任一未 verified 就摘，等于制造功能空窗。
3. **058 保持只依赖 050**——初稿担心它与图标片争夺 `screen_tree_body.xml`，落盘时已把该文件划为 058 独占领地并把 056/057 的领地明确排除它，因此不需要串行。

建议派发顺序：**050 → 051 → 052 → 053 → 054 → 055（IA 链条四片必须依次串行）→ 056 → 057 → 058（三片只依赖 050，但同样必须依次串行，理由见下方第五处修正）**。另有一支**不属于本批**的独立片穿插在内：`T-2026-09-22-constraint-code-classification-defect-059`（父需求）/ `T-2026-09-22-constraint-codes-and-red-suites-060`（实现）——它修的是 052 在真机上跑全量仪器测试时暴露的既有缺陷（Android 13 真机的 SQLite 报错文案与 API 36 模拟器不同，导致生产侧取不到约束错误码），与本批无依赖，但**与 IA 链条、图标三片一样要跑 Gradle，不得并行**；建议排在 **052 复核之后、053 开工之前**，因为 053 的验收基线需要「全量仪器测试可判定」。

**050 与 051 也不能并行**（这是落盘后复核才发现的第四处修正）：两片都要改 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html`——050 改它的 `:root` 取值，051 在它的叙事处加指向性批注。按共享代码区规则（`.trae/HARNESS.md` 并行规则第 2 条）同一文件不得由两片同时写，否则后写的会覆盖先写的。这是**写冲突**而非技术依赖：051 的 `depends_on` 仍为空（它不消费 050 的产物），串行只由文件归属决定。050 因此成为本批唯一的硬瓶颈——052、056、057、058 都在等它。
052 触碰 `MainActivity` 与 `res/values/`，属共享代码区，必须独立、不与其它任务并行。
056/057/058 与 IA 链条并行时的唯一硬约束：三片都不得改动 IA 链条的领地文件（`screen_home_body.xml`、`screen_library*.xml`、`screen_tab_bar.xml`、`activity_main.xml`、`item_topic.xml`、`item_recommendation.xml`），这一点已逐片写进各自的 Out of scope。

**第五处修正（051 复核后发现的）**：初稿把 056/057/058 写成「三片领地互不重叠、可任意并行」，这是**错的**。三片虽然消费不同布局、各带一套图标前缀（`ic_nav_*`/`ic_settings_*`、`ic_chat_*`/`ic_branch_*`/`ic_action_*`、`ic_tree_*`），但**共享两个文件**：(1) `res/values/strings.xml`——三片都要为自己替换的动作位补 `cd_*` 的 `contentDescription`；(2) `android/README.md`——三片都要写/补「图标资产约定」一节。此外三片都要在同一个工作树里跑 `.\gradlew.bat check`，Gradle 的构建目录与守护进程会互相争抢，并发跑出的结果不可信。因此三片**必须依次串行**（056 → 057 → 058）。这是文件归属与构建资源决定的**写冲突**，不是技术依赖——三片的 `depends_on` 仍只列 050。若未来确要它们并行，正确做法是把 `cd_*` 键拆到三个互不相交的 `res/values/strings_*.xml` 文件、并把 README 的图标登记集中到 056 一片完成，然后才谈并行。

**派工前需用户拍板的两项——均已于 2026-09-22 裁决完毕，无遗留阻塞**：

- **055 的推荐区形态 → 保留弹幕。** 用户裁决不采用新稿的「一张重点卡 + 三条列表项」。理由：PRD 第 228 行的 F17【P0】把「弹幕滚动展示」写进能力描述、第 978 行又写进验收口径，改它属于动能力而非纯装饰，越过红线。055 据此收敛为「推荐区只换皮」：只改配色、间距与图标位，不动滚动节奏、暂停规则、命中判定与三类标签分组；并新增一条验收（弹幕形态未变）与一条 Watch-out（不得改成重点卡形态）。连带效果：V3.2 对 PRD 第 49、96、228、978 四行**一字不改**，偏差已登记进 `Docs/spec/SPEC-2026-09-22-figma-ia-migration.md` 的疑虑点 7。
- **051 的文档类 TDD 例外 → 已批准。** 用户于 2026-09-22 明确批准（原话：「051 的文档类 TDD 例外  批准」），批准发生在实现之前、非事后追认，已写入 051 的 `User approval` 键。051 可正常进入 `in_progress`。

**顺带收口的一处规格内部不一致**（不涉及用户拍板，由本次裁决暴露后一并修正，均已跑 `check_spec_author_agent.py` 复验通过）：

1. SPEC 的「范围」一节原写画布装饰含「缩放工具栏（仅叠加）」，与疑虑 4 的结论 B「保留现控制条、只叠加点阵网格」互相矛盾——已改为只列点阵网格并注明控制条保留现状，与 058 的实作范围一致。
2. SPEC 的「入口位置」一节原写「对话页内的设置入口…见疑虑点」，但疑虑点清单里并无该条（悬空引用，会被实现者当成未决项）——已按「保持现状、不动」结案并补为疑虑点 8，理由是该入口属已验收行为，删除它要牵动对话屏与其测试却零功能收益。
- Watch-outs: (1) 不得为迁就设计稿删掉归档入口或把检索降级成只筛标题与摘要——这条已由用户亲自澄清并回退过一次；(2) 不得把新稿画布的 SVG 布局常量搬进工程，画布只做叠加；(3) 不得改动 `dimens.xml` 之外的临场数值，布局只引用 token 名；(4) 首页与对话屏现有体量已超单任务预算，任何「重做一屏」都必须切片；(5) 父需求任务本身不得报完成，直到所有子任务 verified。
