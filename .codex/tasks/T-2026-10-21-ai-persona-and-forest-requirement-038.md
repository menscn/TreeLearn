# Filename: T-2026-10-21-ai-persona-and-forest-requirement-038.md
# Codex Harness Task: AI 内置人设与「我的森林」需求父卡（设计收口与实现编排）

id: T-2026-10-21-038
title: AI 内置人设与「我的森林」需求父卡
template_version: 2
task_type: requirement
status: verified
parent_requirement: null
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: harness
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 两份 intent 均已 accepted，但都还没有可执行的生产实现。AI 内置人设（`Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`）已有一份 draft 规格，规格里还挂着两条未决疑虑；「我的森林」（`Docs/intent/INTENT-2026-09-23-my-forest.md`）只有 intent，没有规格，布局策略（滚动花园 / 缩放画布）和归档树的呈现口径尚未落成设计正文。1.0.0 更新说明明确把两者排除在本版本之外。另有一条尚未落到产品正文的最高规则：「这个需求是否让用户学得下去、学得会」；人设 intent 已确认它应有一处产品正文与一处 `AGENTS.md` 指针。
- Users/scenarios: 单人自用。人设场景是按学习处境切换回答风格（打基础、冲刺、状态差、想轻松探索等）；森林场景是打开抽屉 → 进入全屏森林页 → 看见自己种下的全部主题树 → 点某棵树回到该主题继续学习。
- Expected outcome: 先闭合两份设计与最高规则，使每个实现切片都有可追来源和可机械验证的边界；随后按「人设先行、森林随后」的顺序交付，且旧回答、默认画布、统计口径和 Android Java-only 约束都不被削弱。
- Core flow and reversibility: 设计收口（规格 + UX）→ 人设 `:core` 枚举 → `:ai` 静态附加段 → V5→V6 迁移 → 生成链路与回答身份 → 主题内选择器与介绍页 → 森林数据/布局/页面 → 抽屉入口 → 统计卡 → PRD 回写。每一步都是独立可回退的切片；设计收口的回退是删除新增规格与任务记录，不触碰生产代码。
- Inputs/outputs: 输入是两份 intent、现有 draft 规格、`Docs/04-ux-design/design-system/tokens.json`、`TreeLayout`/`TreeCanvasData`/`LibraryViewState`/`StatsDao`/`StatsAggregator` 的现有实现；输出是两份可拆任务的规格、最高规则正文、森林 UX 方案，以及后续每片的生产代码与证据。
- State and exception handling: 人设读到未知 wire 值时回落默认档并给一次 toast，**一行坏值不得让整个主题列表读失败**；切换人设只写 `persona` 一列，不刷新 `updated_at`、不改变首页排序。森林读不到主题或树时走既有数据层错误路径，显示错误态而不是崩溃；无主题或无统计时显示空态，不用 0 值伪装成有数据。
- Constraints and trade-offs: Android Java-only；`:core` 不碰 UI/网络/数据库实现；人设只作用于讲解方式、深度、节奏、措辞、是否主动反问、是否主动延伸，不削弱诚实、纠错、不编造基线；提示词正文不进数据库、日志或遥测。森林零 schema 变更，树布局复用 `:tree-layout`，统计复用 `StatsDao` + `StatsAggregator`，不新增平行口径。森林以情绪价值优先于信息密度；主题数量按自用规模（几十棵以内）设计，不先为大规模做缩放画布。
- AI recommendation and rationale: 先做设计收口再动生产代码。人设规格的两条疑虑已经由用户拍板，应把它们从「待拍板」改成「已拍板」并写死边界；森林规格应选**纵向滚动花园**而不是缩放画布：每棵树一张卡片、卡片内用 `TreeLayout` 的远景档绘制，触控热区稳定、实现面小、不需要新手势层，和「观赏优先」的目标一致。归档树按用户裁定进入森林且同样式，这需要给 `TreeLayout` 一个显式的回顾模式（默认画布仍排除归档），不在森林里复制一套布局。
- User choice and custom additions: 用户 2026-10-21 选择 **Harness 调度**；未知人设 wire 值 **回落默认并提示**；切换人设 **不刷新活动时间 / 首页排序**；森林 **连归档子树一起画**。用户 2026-09-21 已确认人设 7 档、主题级作用域、介绍页两处入口、静态示例、最高规则口径。用户 2026-10-23 追加指令「按照 harness，结合当前 UX 风格，进行开发，在 web 和 android 上」：设计收口视为已接受，范围扩到 Web，人设与森林同时进入生产实现。
- AI-autonomous decisions: ① 先人设、后森林，因为人设改动集中在生成链路与数据库迁移，森林不依赖人设；② 森林的场景摆放采用**斜俯瞰、固定坐标**（最终原型），不复用 `TreeLayout` 的分支布局——后者画的是主题内分支树，森林画的是主题之间的地块，两者不是同一个问题；③ 森林的主题集合与主题列表同源，段枝数走一条只读聚合查询，统计脚注复用 `StatsRaw` + `StatsAggregator`；④ 人设的发送身份以主题行为权威：`ChatWiring` 在发送/重试/重置的同一事务入口读取 `topics.persona`，不把 chip 状态当第二事实源；⑤ Web 同步交付人设与森林视图，但 Web 是只读 parity 演示，不伪造连续天数或生成数据；⑥ 本轮不做用户自建人设、森林分享/导出、森林管理动作、新统计指标。
- User-confirmation decisions: 采用 Harness；未知档位回落默认并提示；切换人设不刷新活动时间；森林包含归档子树且同样式；**2026-10-23 用户授权设计收口并直接进入 Web + Android 生产实现**。以上均为用户明确选择。
- Acceptance evidence: 039/040 的两份规格与守门读数；064、066–071 七张实现片的 RED/GREEN 与两端构建/测试；`python -X utf8 .codex/scripts/validate_tasks.py` 全目录 errors=0；`python -X utf8 .codex/scripts/check_pointer_liveness.py` 退出码 0；Android 与 Web 的真机/浏览器截图证据落 `artifacts/evidence/`。
- Facts: `TreeLearnSchema.DATABASE_VERSION = 5`（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:61`）；`topics`、`answer_revisions`、`generation_operations` 目前都没有 `persona` 列；归档过滤有**两层**——`TreeCanvasWiring.load(topicId)` 在 `android/app/src/main/java/com/treelearn/app/wiring/TreeCanvasWiring.java:63` 就跳过归档分支，`TreeLayout` 的 `Tree` 构造在 `android/tree-layout/src/main/java/com/treelearn/treelayout/TreeLayout.java:333` 再过滤一次，因此森林的回顾模式必须显式穿过读取与布局两层，默认 `load(topicId)` 与默认 `compute(...)` 的排除行为保持不变；`LibraryWiring.topics()` 是主题列表的唯一查询入口，`LibraryViewState` 已有 LOADING/READY/EMPTY/ERROR 四态；`StatsDetailPresenter` 已示范 `HomeStore.StatsRaw` + `StatsAggregator` 的复用方式；PRD 的归档口径是「根分支与完整主题不可归档，只有非根子树可归档」，所以 intent 里「已归档主题」应读作「主题内的归档子树」，森林不出现「归档主题」这个不存在的对象。
- Assumptions: 自用规模下逐主题读取并绘制几十棵树在可接受范围内；用户会先 review 规格与 UX，再授权生产实现；`figwright` 不可用时可以用现有 CDP 渲染同一原型取截图证据。
- Unknowns: 各档行为样例的区分度（尤其「学霸搭子」与「深夜陪读」）要靠设计阶段的受控问题集验证；森林主题数量增长后的性能上界尚未实测；森林 UX 是否需要进入 Figma 还是只需原型截图，取决于本机 figwright 是否连通。
- Risks: V5→V6 迁移是本批唯一的 schema 变更，必须经 `db-migration-reviewer`；`TopicDao.update()` 是整行写回，若复用它切换人设会把主题挪到首页顶部；`TreeLayout` 的默认过滤是既有画布行为，回顾模式若直接改默认路径会同时改变总览画布；森林统计若另写查询会制造第二套口径；PRD 的 BASE-14 若不回写，会出现「文档说隐藏、森林显示」的漂移；本工作树同时有别的线在飞，提交需整树一次。
- Deferred: 生产实现（人设六片、森林五片）；Web/H5 复刻；用户自建人设；森林分享/导出/3D/复杂动效；森林页的归档/删除管理动作；新增统计指标；平板档的森林差异。
- Non-goals: 不新增服务端、账号、同步或遥测；不改写 `LEARNING_ANSWER` 基线正文；不向用户开放提示词正文编辑；不把 SQLite、API Key 或生成权威搬进 JavaScript；不改默认总览画布的归档过滤；不重排首页排序；不新增第二套树布局或统计口径。
- Skipped non-critical questions and reason: 没有追问瀑布流列数、卡片圆角或森林页统计卡的精确排版；这些不改变目标、架构、验收或风险，交给森林规格与 UX 片按 token 定。没有追问行为样例的具体措辞；它属于设计资产，由 039 片产出后交用户 review。

## Goal

按最终斜俯瞰原型与已拍板边界，把 AI 内置人设与「我的森林」同时交付到 Android 与 Web：人设从 `:core` 枚举、提示词附加段、V5→V6 迁移、生成身份贯穿一直落到聊天页 picker 与介绍页；森林从主题聚合读取、确定性场景摆放、自绘树精灵一直落到抽屉入口、全屏页与统计脚注。旧回答、默认画布、首页排序、统计口径与 Android Java-only 约束都不被削弱。

## Scope

### In scope
- 片 039：`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 两条疑虑转已拍板，补七档 × 六轴行为矩阵、稳定 wireId、窄写路径。**已完成。**
- 片 040：新建 `Docs/spec/SPEC-2026-10-21-my-forest.md`，按斜俯瞰定稿森林的数据、布局、状态与归档口径。**已完成。**
- 片 064（Android 人设核心）：`Persona` 枚举与七档穷尽附加段；`GenerationCommand`/`DeepSeekRequest`/装配器/协调器/稳定器的人设身份；V5→V6 三列迁移；`TopicDao.updatePersona` 窄写与未知值回落。
- 片 066（Android 人设选择器）：聊天页人设 chip、七行弹窗、`ChatViewState`/`ChatPresenter`/`ChatActions`/`ChatScreen` 接线与主题窄写。
- 片 067（Android 人设介绍页）：回答风格介绍页、设置页入口、弹窗底部「了解每种风格」与返回链。
- 片 068（Android 森林）：主题聚合读取、`ForestLayout` 纯函数、树精灵自绘、`ForestViewState`/`ForestPresenter`/`ForestScreen`、抽屉入口、返回链、统计脚注。
- 片 069（Web 人设）：`persona_*` 文案、主题级本地状态、聊天页 chip 与弹窗、介绍页、设置入口。
- 片 070（Web 森林）：`forest` 视图与路由、侧栏入口、SVG 斜俯瞰树精灵、选中纸条、诚实的统计脚注。
- 片 071（文档与证据）：PRD BASE-14 回写、`android/README.md`/Web parity 文档、两端截图证据、全量守门。

### Out of scope
- 用户自建人设、森林分享/导出、森林管理动作、新增统计指标、缩放/平移手势层、3D 与复杂动效。
- `backend/**`；远程账号、同步或遥测。
- 修改两份 intent 的结论；intent 与规格冲突时以规格的已拍板记录为准。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 人设只影响之后新生成的回答，不改写旧回答；默认档与任何未知值的表现一致；system 消息仍恰好一条且位于第一位；派生用途仍不带人设段。森林不写库、不改 `updated_at`、不改首页排序、不改默认总览画布的归档过滤。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 人设的生产者链是 `ChatPresenter.send` → `ChatWiring.pin`/`DeepSeekRequestAssembler` → `GenerationCoordinator` → `GenerationStabilizer`；消费者是 `AnswerRevisionDao` 的读取与聊天页 chip。森林的生产者是 `LibraryWiring.topics()` 与新增的 `:data` 聚合读取；消费者是 `ForestPresenter` 与抽屉入口。两端共享的只有界面语义与树精灵归一化常量，不共享运行时数据。
- Native bridge or independent-client compatibility/update requirements: 不适用——没有人设或森林穿越原生桥；Web 只消费既有 fixture 字段。
- Behavior-neutral non-applicability reason, if applicable: 不适用——六个实现片都有运行时行为。

## Documentation impact record

- Changed runtime facts: `topics` 新增 `persona` 列；`answer_revisions` 与 `generation_operations` 各新增 `persona` 列；`DATABASE_VERSION` 5→6；聊天页新增人设 chip 与介绍页；抽屉新增森林入口与全屏森林页；Web 新增 `persona` 与 `forest` 两个视图。
- Authoritative code/config/migration evidence: `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java` 的 V6 迁移与列定义、`android/core/src/main/java/com/treelearn/core/domain/Persona.java`、`android/ai/src/main/java/com/treelearn/ai/prompt/PersonaPrompts.java`、`android/app/src/main/java/com/treelearn/app/ui/forest/ForestLayout.java`、`frontend/src/store/workspace.ts`、`frontend/src/pages/ForestPage.tsx`。
- Documentation surfaces checked: 两份规格、PRD V3.1 增量、`android/README.md`、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`、`.codex/context/source-of-truth.md`、`.codex/ROADMAP.md`。
- Documentation files updated: 039/040 规格、PRD BASE-14、`android/README.md`、Web parity 规格、本卡与 064、066–071。
- Producer/consumer/sibling/compatibility paths checked: 人设的身份一致性校验必须同时比版本与人设；森林的段枝数必须与主题列表同源；Web 的 `WorkspaceView` 联合类型、`VIEWS`、路由与外壳四处必须同时改。
- Superseded phrases/examples reverse-searched: 「纵向滚动花园」「缩放画布」「已归档主题」「TreeLayout 远景档」「设计 review 前不实现」。
- `documentation impact: none` reason and evidence, 若适用: 不适用。
- Historical/PRD handling: 两份 intent 原文保留；规格新增 2026-10-23 拍板与实现授权记录；PRD 的 BASE-14 按实际落地回写，不删除旧口径的冲突证据。

## Acceptance criteria

- [x] 039/040 两份规格通过 `check_spec_author_agent.py`，未决疑虑为 0；森林规格明确斜俯瞰、固定位置、归档同样式、统计复用与零 schema。（两片实测 `0 check(s) failed`、退出码 0，四节齐全、front-matter intent 指向真实文件、无占位符、无裸色值/裸 px；两份 Doubts 节各自首条即「未决疑虑 0 条」，人设原疑虑 1/2 已记为 2026-10-23 用户拍板并落地）
- [x] Android 人设：七档 wireId 可解析、未知值回落默认且不拖垮读取；人设只追加在 `LEARNING_ANSWER`；V5→V6 迁移在新装与升级两条路径得到同一列定义；切换只写 `persona` 一列且首页顺序不变；旧回答不改写。（片 064/066/067 的 RED/GREEN 读数；窄写为 `TopicDao.updatePersona`，刻意不复用整行 `update`；列声明常量由 fresh 建表与 `MIGRATION_V5_TO_V6_STATEMENTS` 共用）
- [x] Android 森林：主题集合与 `LibraryWiring.topics()` 同源；归档分支计入段枝数并在纸条标注；空态/错误态/加载态各有渲染路径；不写库、不改 `updated_at`。（片 068：`TOPIC_FOREST_COUNTS_SELECT` 一次读、总数不带 `archived_at` 仅第二计数收窄；`ForestViewState` 四态；`ForestScreenLayoutTest` 6 例；全片零写路径）
- [x] Web 人设与森林：所有用户可见中文走 `zh-CN.ts`；人设 chip、弹窗、介绍页与设置入口可操作；森林路由可达、点树选中、纸条跳转正确；不伪造统计数字。（片 069/070：`pnpm lint` 含 user-copy i18n 基线只减不增；CDP 探针走通入口→林地→选中→切换→纸条进主题→返回→窄屏；页脚只写读到的数字，读不到即省略不写 0）
- [x] 两端证据：Android `./gradlew :app:testDebugUnitTest` 与编译通过，真机安装后截图；Web `pnpm lint`、`pnpm test`、`pnpm build` 通过，浏览器截图。（片 068 回归 `:data`/`:app` 单测 + `:app:assembleDebug` exit 0；片 070 全量 `pnpm test` 24 文件 154 例 exit 0；真机 `adb install -r` 截图 persona-066（6 张）/067（9 张）/forest-068（5 图 + 3 份原文），Web 069（8 张）/070（6 张）+ `readings.json`；本批收尾另复跑一次 Android 回归，读数见 Verification report）
- [x] 守门：`validate_tasks.py` errors=0；`check_pointer_liveness.py`、`check_design_tokens.py`、`check_ux_vendor.py`、`check_source_of_truth.py` 退出码 0。（core 档整跑 `RUN-ALL-GUARDS pass=39 fail=0 inconclusive=0 fault=0 timeout=0`、退出码 0；四条具名守门逐条 `0 check(s) failed`；`frontend verify` 16 步全绿、`no-kotlin-check` `VERDICT: PASS`、`guard-selftest -Fast` 38 例 PASS，读数见 Verification report）
- [ ] 行数：本批新增生产逻辑落在新文件；触碰的既有大文件只做接线，`maintainability.md` 的 800/150/20/4 门禁不被突破，历史超限文件不增长。（**前半条达成、后半条未达成并已实测归因**：新增逻辑确在新文件，但三个历史超限文件的已超限指标比修改前增长——`MainActivity` 951→1055、`ChatScreen` 1436→1466、`ChatPresenter` 856→886 有效行。逐 hunk 归因给本批的人设/森林相关新增为 62 / 8 / 15 行，其余属同在 `HEAD~1`→工作树区间的玻璃线。详见 Verification report 与 Gaps ⑤）

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（需求父卡，无被测实现）
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 需求父卡只承载已确认需求、设计收口与实现编排，不产生生产代码。
- User approval: 用户 2026-10-21 选择 Harness 并确认四项产品决策；本卡不执行生产实现。
- Alternative verification: 039–042 各自的文档/原型守门与用户 review。

## Implementation notes

- 编号说明：`.codex/tasks/` 当时最高编号为 063，本批实现片原计划顺延 064–069；065 随后由已在飞的玻璃不透明度片占用并已 verified，故人设界面起改为 066。066 执行时按可独立验收把「选择器 + 窄写」与「介绍页 + 设置入口」拆成两片，森林与 Web/文档顺延为 068–071，本批收口于 071。设计要求与最终原型已由用户 2026-10-23 的指令接受，不再另立设计片。
- 人设实现顺序：`Persona` 枚举与 `:ai` 穷尽 switch → V5→V6 三列迁移 → `GenerationCommand`/`DeepSeekRequest`/`GenerationCoordinator`/`GenerationStabilizer` → 聊天页选择器 → 介绍页 → 设置入口。迁移片单独经 `db-migration-reviewer` 规则复核。
- 森林实现顺序：`:data` 主题分支聚合读取 → `ForestLayout` 纯函数与树精灵 → `ForestData`/`ForestPresenter`/`ForestViewState` → `ForestScreen` 与抽屉入口 → 统计脚注 → BASE-14/PRD 回写。森林片不新增 schema，不改默认画布。
- 森林的「已归档主题」校正：PRD 规定根分支和完整主题不可归档，实际存在的是主题内的归档子树；森林展示全部主题，并在每棵树里包含该主题的归档子树。
- 人设身份的单一事实源：发送、重试、重置都在 `ChatWiring` 的事务入口读 `topics.persona`；聊天页 chip 只负责显示与窄写，不参与请求身份的第二次决定。
- Web 的范围口径：Web 是只读 parity 演示。人设状态可以本地保存以演示主题级作用域，但 UI 不得声称已调用模型；森林只显示 fixture 能证明的树数与段枝数。
- 工作树卫生：本工作树同时有 glass/morandi 在飞线；本批只增改本需求相关文件，不清理、不回退其他线的改动，最终按整树一次提交处理。

## Verification report

- Verdict: PASS（附一条已登记的实测偏差：验收第 7 条的「历史超限文件不增长」半条未达成，见该条与 Gaps ⑤；其余六条验收均有命令级读数）
- Evidence: 本卡的证据是八张子片的 RED/GREEN 记录加一次收尾整跑，逐条可回读；卡片本身不重复各片的命令原文，只登记谁覆盖了哪一条验收。
  - 设计收口：039/040 两片规格经 `check_spec_author_agent.py` 各 4 项判据全绿、退出码 0，两份 Doubts 节首条均为「未决疑虑 0 条」；
    最高规则（「这个需求是否让用户学得下去、学得会」）已落 `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md`，`AGENTS.md` 只留指针。
  - Android 人设：064（`:core` 七档枚举 + `:ai` 穷尽附加段 + V5→V6 三列迁移 + 窄写与回落）、066（对话页 chip/弹窗/接线）、067（介绍页与设置入口）。
  - Android 森林：068（`:data` 一次聚合读 + `ForestLayout` 纯函数 + 自绘树精灵 + `ForestScreen` + 抽屉入口 + 统计脚注），
    回归 `cd android && ./gradlew :data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` exit 0（`:app` 946 例 / `:data` 78 例，0 失败 0 跳过）；
    收尾复跑同一条命令 exit 0、`BUILD SUCCESSFUL in 38s`、82 actionable tasks 全部 UP-TO-DATE——071 只改文档，Android 源码与测试输入零变化，
    因此这条读数是「缓存命中即无回归」而不是「946 例重跑一遍」，重跑的逐类计数以 068 那次为准。
  - Web：069（人设 chip/弹窗/介绍页/设置入口 + 主题级内存状态）、070（`forest` 视图 + 侧栏入口 + SVG 树精灵 + 诚实页脚），
    全量 `pnpm test` 24 文件 154 例、`pnpm lint`、`pnpm build` 均 exit 0。
  - 文档与证据：071（PRD BASE-14 回写注记、`android/README.md` 人设/森林两节、parity/抽屉/人设三规格时效条目、
    `artifacts/evidence/persona-forest-2026-10-23-index.md` 证据索引、`.codex/ROADMAP.md` 两处时效）。
  - 设计复核：070 片的 Web 森林经设计师两轮缺陷修复（胶囊朝向、入口卡）后重拍；Android 真机截图 20 张 + 命令原文 3 份在
    `artifacts/evidence/`，逐单元一行索引见 071 的证据索引。
  - 收尾整跑读数（文档定稿、两张卡状态翻转并 `metrics_report.py --write` 之后，仓库根执行）：
    - `python -X utf8 .codex/scripts/run_all_guards.py`（core 档）→ `RUN-ALL-GUARDS pass=39 fail=0 inconclusive=0 fault=0 timeout=0`、
      `VERDICT: PASS (subset only — 39/45 entry(ies) judged, not the whole roster)`、退出码 0。
      本卡验收第 6 条点名的四支全在其中：`validate_tasks` `SUMMARY files=72 errors=0 warnings=25`、
      `check_pointer_liveness` `0 check(s) failed`、`check_design_tokens` `0 check(s) failed`、`check_ux_vendor` `0 check(s) failed`（自检 21 例 mismatch=0）、
      `check_source_of_truth` `0 check(s) failed`；`checkers_selftest` `OVERALL: PASS`、`verify_1to1_selftest` `cases=39 failures=0`。
    - `python -X utf8 .codex/scripts/run_all_guards.py --tier precommit` → `pass=5 fail=1`、`VERDICT: FAIL`、退出码 1。
      五条绿：`check_offline_bundle`（violations=0）、其自检 6 例、`frontend verify` `steps=16 ok=16 bad=0`、
      `no-kotlin-check` `VERDICT: PASS (13 OK line(s))`（Java-only 硬约束的机器面）、`guard-selftest -Fast` `38 case(s) PASS`。
      **一条红是 `line-budget`**：`业务变更行: 3637`、`FAIL: 业务变更 >500 行，须拆 task`。它量的是 `HEAD`→整个工作树，
      而本工作树按用户既有拍板是「混合线整树一次提交」，玻璃线 049–065 的未提交部分与本批 069–071 都在这一个读数里，
      因此这条红属于**提交形状**而不是本批的切片形状——本批每片的 500 行硬预算由 064–071 各自卡面的 line-budget 记录承担（例：066 实测上界 444 行）。
    - **验收第 7 条的后半条实测未达成**（增量棘轮）：`HEAD~1`→工作树按有效行（非空、非纯注释）量得
      `MainActivity` 951→1055、`ChatScreen` 1436→1466、`ChatPresenter` 856→886，三个历史超限文件均增长。
      逐 hunk 归因：含 `persona`/`forest` 关键字的新增行分别是 62 / 8 / 15，其余 85 / 66 / 27 属玻璃线与其他在飞改动。
      未超限的触碰面按规则允许正常演进：`ChatWiring` 568→584、`TreeLearnSchema` 660→696 有效行，仍在 800 之下。
      这条偏差**不在本收口片里靠重构去凑绿**——那会把已 verified 的 066/067/068 的验收面推翻，且规则明确禁止为降行数做机械拆分；
      登记为 Gaps ⑤ 与下一批的热点治理片。
- Changed/deleted assertion rationale and equivalent behavior coverage: 两处卡片登记与实测不符，均已按实测纠正且等价覆盖由「产品口径未变、只有落盘位置变了」承担：
  ① 本卡 Facts 写「PRD 增量正文在 `..._V3.1增量.md`」，实测 BASE-14 正文在 V3.0 基线（V3.1 是 BASE-21~26），故 071 的回写落点在 V3.0；
  ② 片 070 验收第 6 条原写「loading 显示『正在数你的树』」，实现确认 Web 路由对 `loading`/`error` 用共享状态行统一挡门，森林专属 loading 文案在任何路径都不渲染，已按共享挡门口径改述。
  本卡 Facts 的 `DATABASE_VERSION = 5`（`:61`）是建卡时的真值，落地后为 6；按归档规则不改写当时读数，以 064/071 的迁移与回写记录为准。
- Gaps: ① Android 真机上的 V5→V6 迁移执行仍只有 JVM 契约测试背书（既有拍板：全量仪器测试会卸载应用并清本地数据，设备面只走 `adb install -r` + 截图）；
  ② 森林主题数量增长后的绘制上界未实测（自用规模按几十棵设计，超出后需重新评估缩放画布）；
  ③ 「学霸搭子」与「深夜陪读」的行为样例区分度未经受控问题集实测；④ 平板档的森林与人设差异复刻尚未成片，其前置是平板基线截图采集（当前仓库内不存在）；
  ⑤ **增量棘轮欠账**：`MainActivity`（1055 有效行）、`ChatScreen`（1466）、`ChatPresenter`（886）三个历史超限文件在本批+玻璃线的共用区间里继续增长，
  其中人设/森林接线占 62 / 8 / 15 行。需要一支独立的热点治理片按 `.codex/rules/maintainability.md` 的抽取判据处理（先内聚职责、后行数），
  不能靠本批收尾时的机械拆分还账。
- Risks: ① `android/README.md` 与 parity 规格现处在 `check_pointer_liveness.py` 被判定面上，后续实现片只改代码不看文档会新增一条红；
  ② 窄写路径让主题写入口从一处变两处，第三处写 `topics` 的入口出现就会绕过「切换风格不刷新活动时间」这条拍板；
  ③ Web 与 Android 的树精灵几何靠**同常量同算法**保持一致，没有跨端共享文件的机器判据，改一端常量必须同步改另一端（片 070 的 watch-out）。

## Handoff notes

- Completed: 039/040 设计收口 + 042 原型 + 064/066/067（Android 人设三片）+ 068（Android 森林）+ 069/070（Web 两片）+ 071（文档与证据）全部 verified；用户四项决策与 2026-10-23 的 Web+Android 实现授权均已落地并有对应产物。
- In progress: 无。本批 064–071 七张实现/文档片结案。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 下一批候选看 `.codex/ROADMAP.md` 的 Phase B/C；② 产品面仍未闭合的实事是 Android 真机上 V5→V6 的执行证据（需要一次允许卸载的窗口，见 Gaps ①）与平板档基线截图采集（parity 阶段 3 之后的前置片）；③ 森林主题量上界与两档人设的区分度要等真实使用一段时间后再评估，不预先起优化片。
- Watch-outs: ① 迁移的新装与升级两条路径必须共用列声明常量；② `TopicDao.update()` 的整行写回不得用于人设切换，且不得再出现第三处 `topics` 写入口；③ 森林统计只能复用 `StatsRaw` + `StatsAggregator`，读不到就省略、不写 0；④ 默认总览画布的归档过滤不能被顺手改掉；⑤ Web 是只读 parity 演示，不得伪造 streak/生成/归档数字；⑥ 树精灵两端靠同常量同算法对齐，改一端必须同步改另一端；⑦ 本工作树有别的在飞线，提交时整树一次。
