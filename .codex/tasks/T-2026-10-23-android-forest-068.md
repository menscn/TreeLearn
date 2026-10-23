# Filename: T-2026-10-23-android-forest-068.md
# Codex Harness Task: Android 我的森林

id: T-2026-10-23-068
title: Android 我的森林（抽屉入口、斜俯瞰林地、树纸条）
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-21-ai-persona-and-forest-requirement-038.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 950
shared_code_touched: true
depends_on: [T-2026-10-23-android-persona-intro-067.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary：父卡 038 已登记用户 2026-10-23「按 harness 开发，web 和 android」的实现授权；森林规格 `Docs/spec/SPEC-2026-10-21-my-forest.md` 已在 040 收口，原型 04/05 屏已画在 `Docs/04-ux-design/知芽-TreeLearn-人设与森林原型-v1.html`。本片只做 Android 森林，Web 森林留 070。
- Problem/background: 主题列表只有「最近学习」的线性视角，学习者看不到自己长期种下的整片林子；抽屉里也没有一个只回顾、不管理的入口。
- Users/scenarios: 学习者在抽屉里想回头看「我一共有多少棵树、哪棵最大、上次停在哪」，从抽屉进入森林；点一棵树读到它的事实，再点纸条箭头回到那棵树停下的分支。
- Expected outcome: 抽屉热力图卡之后、最近学习之前出现 44dp 森林入口；进入独立全屏页，顶栏「我的森林 / 一题一树，慢慢长」，斜俯瞰地块上每个主题一棵树；树的位置由主题 id 稳定决定，树大小随段枝数（含归档）变化；点树出现柔光晕、地面圈、标题胶囊与底部纸条；纸条箭头走既有打开主题路径；页脚只显示真实读到的树数/段枝/归档/连续天数。
- Core flow and reversibility: 抽屉 `onForestTapped` → 宿主 `showForest()` → `ForestPresenter.start()` 读主题集合、每棵树的段枝计数、人设与统计 → 场景绘制；点树只改选中，点箭头 → `showChat(resumeBranchId)`；返回 → 首页。森林不写任何表，不触发生成，不刷新 `updated_at`，退出不改任何主题状态。
- Inputs/outputs: 输入是 `LibraryWiring.topics()` 的主题集合、`TopicDao` 的一条森林计数投影、`TopicDao.personaOfBranch` 的人设读取、`HomeStore.StatsRaw` + `StatsAggregator`；输出是一屏可观赏林地与一次既有主题导航。
- State and exception handling: 首次读取前加载；主题集合为空是空态（空地块 + 「去种第一棵树」回首页输入区）；主题集合读取失败是整页错误态（「森林暂时读不到」+「重试」）；某主题计数读不到时该树按 1 段枝画、纸条不显示归档数、页脚省略段枝与归档总数；统计读不到或历史为空时页脚省略连续天数；重试重走同一读取入口并保留仍存在的选中树。
- Constraints and trade-offs: Java-only；自绘 View + XML；只引 token 颜色；可点区域不小于 44dp；零 schema；不改 `TreeLayout`/`TreeCanvasWiring` 的归档排除；不改首页排序；不新增依赖；不记录任何回答正文；不自动提交。
- AI recommendation and rationale: 主题集合直接复用 `LibraryData.topics()`，计数只在 `TopicDao` 增加一条只读聚合查询，森林不再写第二套主题/统计口径；场景坐标做成 JVM 可测的 `ForestLayout` 纯函数，View 只做像素换算与绘制；树精灵几何逐项对齐原型 `treeSprite`，随机只来自种子。
- User choice and custom additions: 斜俯瞰、能看到树干（2026-10-21）；主题位置固定、不按最近学习重排（2026-10-21）；归档子树一起画且同样式（2026-10-21 拍板）；统计复用抽屉同一入口。
- AI-autonomous decisions: ① 计数查询额外带出 `t.persona` 属于同一行投影，避免为每棵树再开一条 N+1 查询，但纸条仍按既有 `Persona.fromWireId` 回落边界读取；② 进入森林默认不选中任何树，先显示「点一棵树看看」，首次点树才出现纸条；③ 从森林打开主题走 `showChat(resumeBranchId)`，聊天返回仍按既有入口规则回首页，不新增导航栈；④ 空态按钮回首页并把焦点交给首页输入框，不新增创建主题的第二个入口。
- User-confirmation decisions: 本片不新增需要用户拍板的产品决策；所有交互口径来自已 accepted 的森林规格与 2026-10-21/23 拍板。
- Acceptance evidence: 新增 `ForestLayoutTest`、`ForestPresenterTest`、`ForestScreenLayoutTest`、`TopicForestDaoStatementTest` 与 `HomeDrawerPresenterTest` 森林入口断言；`:app:testDebugUnitTest` 全量；`:data:testDebugUnitTest`；`:app:assembleDebug`；真机 `adb install -r` 后截图走通抽屉入口、森林选中、纸条进入主题、返回首页。
- Facts: `LibraryWiring.topics()` 是主题集合的正典读法；`TopicDao.OVERVIEW_SELECT` 的 `branch_count` 已包含归档；`Branches.ARCHIVED_AT` 已存在；`HomeStore.StatsRaw` + `StatsAggregator.aggregate` 是统计唯一口径；`PersonaUi.nameRes` 覆盖七档；抽屉在 `HomeDrawerScreen` 中以代码构建，热力图卡后是「最近学习」标题；`DateUtils.getRelativeTimeSpanString` 是首页/学习库共用的时间格式化。
- Assumptions: 自用规模几十棵树一次绘制可接受；场景在手机竖屏铺满剩余高度；几十棵树时仍可用缩小树冠与密度修正保持可点。
- Unknowns: 真机上树冠叶簇与地面等高线的观感是否达到原型；以截图为准，必要时只调绘制常量，不改判据。
- Risks: 自绘场景若在 `onDraw` 中分配 Paint/Shader 会拖慢滚动/转场；用构造期缓存与复用 Paint 控制。树精灵若每次重算随机序列，选中重绘会抖动；必须只由 seed 决定。抽屉入口若放在「最近学习」之后会与规格冲突；以规格为准放在热力图卡之后。
- Deferred: Web 森林（070）；文档回写 PRD BASE-14 与 README（071）；森林缩放/平移/分享/3D 均为非目标。
- Non-goals: 归档、删除、重命名等管理动作；第二套统计指标；第二套主题查询；修改 `TreeLayout`；修改首页排序；远程同步。
- Skipped non-critical questions and reason: 未追问默认选中哪棵树——规格只要求「点树先选中」，默认留白更符合「点一棵树看看」的提示，也未虚构初始数据。

## Goal

让学习者从抽屉进入一片斜俯瞰的、按主题稳定摆放的林地，点树读到真实事实并回到那棵树停下的分支，全程只读、零 schema、统计只走既有口径。

## Scope

### In scope
- `:data`：`TopicDao` 新增 `ForestCount` 与一条只读森林计数查询（主题、段枝总数含归档、归档数、最近更新时间、人设），不改 schema。
- `ForestWiring`：主题集合复用 `LibraryData.topics()`；计数、人设、统计各自失败可降级；统计走 `HomeStore.StatsRaw` + `StatsAggregator`。
- `ForestLayout`：主题 id + 段枝数 → 稳定归一化坐标、树大小、种子、色调；与输入顺序无关；拥挤时做密度修正。
- `ForestPresenter` / `ForestViewState`：加载/就绪/空/错误四态；选中、打开、重试、页脚数字的可得性。
- `ForestScreen` / `ForestScene` / `ForestTreeSprite`：顶栏、斜俯瞰地面、每棵树的树干/树冠/影子、选中光晕/地面圈/标题胶囊、底部纸条与箭头、页脚、加载/空/错误块。
- 抽屉入口：热力图卡之后、最近学习之前，44dp 行，圆底森林图标 + 主副文案 + 箭头；`HomeDrawerPresenter` 增加 `openForest` 端口。
- 宿主：`MainActivity.showForest()`、返回首页、从纸条打开既有主题、空态回首页输入区。
- 资源：`screen_forest.xml`、`drawer_forest_entry.xml`、森林图标/箭头/地面空圈/提示胶囊等 drawable，森林文案。
- JVM 测试：布局稳定性、Presenter 四态与降级、宿主/抽屉契约、DAO 语句、资源与 token 约束。

### Out of scope
- Web 人设与森林（069/070）。
- PRD、README、证据索引回写（071）。
- 主题内分支树布局与总览画布的归档排除行为。
- 森林里的管理动作、缩放平移、分享导出、动效细化。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增抽屉森林入口与独立森林页；首页/学习库/抽屉快速列表/统计详情/主题打开路径不变；归档分支仍不出现在常规导航，但在森林里按同一棵树计入且样式不弱化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 抽屉 `HomeDrawerScreen` → `HomeDrawerPresenter.onForestTapped` → `MainActivity.showForest` → `ForestPresenter` → `ForestScreen/ForestScene`；森林纸条 `ForestPresenter.onSelectedOpened` → `MainActivity.openBranch` → 既有 `showChat`；返回统一走 `MainActivity.onBackRequested`。
- Native bridge or independent-client compatibility/update requirements: 不适用——纯本机只读页面，不穿越原生桥，Web 端在 070 单独实现。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 抽屉新增「我的森林」入口；新增独立森林页与一条只读森林计数查询；森林显示归档子树但不提供管理动作。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/wiring/ForestWiring.java`、`forest/ForestLayout.java`、`forest/ForestPresenter.java`、`screen/ForestScreen.java`、`data/src/main/java/com/treelearn/data/db/dao/TopicDao.java`、`screen/HomeDrawerScreen.java`、`MainActivity.java`。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-21-my-forest.md`、森林原型 04/05 屏、`android/README.md`、`.codex/rules/android.md`、`.codex/rules/ux-design.md`、`.codex/rules/regression.md`、`.codex/rules/sql.md`。
- Documentation files updated: 本卡；PRD BASE-14、`android/README.md` 与 Web 导航说明随片 071 回写。
- Producer/consumer/sibling/compatibility paths checked: 抽屉入口、森林→主题、返回首页、空态回首页输入区、学习库/首页/抽屉的既有读取、归档分支的计数口径。
- Superseded phrases/examples reverse-searched: 「森林尚未实现」「抽屉没有森林入口」「归档分支不出现在任何回溯视图」。
- `documentation impact: none` reason and evidence, 若适用: 不适用。
- Historical/PRD handling: 原型 04 屏把入口画在最近学习之后、森林页副标题画统计；规格明确入口在热力图之后、副标题固定为「一题一树，慢慢长」、统计在页脚——以 accepted 规格为准，原型只作视觉参考。

## Acceptance criteria

- [ ] 抽屉热力图卡之后、最近学习之前出现 44dp 高的「我的森林 / 全部主题长成一片林子」入口，点按关闭抽屉并进入森林页。
- [ ] 森林页顶栏返回 + 「我的森林」+「一题一树，慢慢长」；场景为斜俯瞰地块，每个主题一棵有树干、树冠、落地影子的树；主题集合与 `LibraryData.topics()` 同源。
- [ ] 树的位置只由主题 id 与集合决定，同一 id 在输入顺序变化后位置不变；树大小随段枝数（含归档）增大并在上下限内收敛；点击命中区不小于 44dp。
- [ ] 点树出现柔光晕、地面圈与标题胶囊，底部纸条显示标题、段枝数、最近学习时间、当前人设与「含 N 段已归档」；点另一棵树只切换选中；点纸条箭头打开该树的 resume 分支。
- [ ] 页脚只显示本次真实读到的数字：树数恒可显示，段枝/归档总数在任一计数缺失时省略，连续天数在统计不可读或历史为空时省略；不写 0 冒充。
- [ ] 加载显示「正在数你的树」；无主题显示空地块 +「去种第一棵树」并回首页输入区；读取失败显示「森林暂时读不到」+「重试」，重试保留仍存在的选中树。
- [ ] 森林只读：不写库、不触发生成、不改 `updated_at`、不刷新首页排序；`TreeLearnSchema.DATABASE_VERSION` 不变；`TreeCanvasWiring`/`TreeLayout` 的归档排除不变。
- [ ] 定向测试与 `:app:testDebugUnitTest`、`:data:testDebugUnitTest` 全绿；`:app:assembleDebug` 通过；真机 `adb install -r` 后截图走通入口→选中→进入主题→返回首页。
- [ ] 页面与场景只引 token 颜色，不另写颜色字面量；森林页不提供归档/删除/重命名管理动作。

## TDD execution record

### RED — failing test first
- Test/scenario: `ForestLayoutTest`、`ForestPresenterTest`、`ForestScreenLayoutTest`、`TopicForestDaoStatementTest`、`HomeDrawerPresenterTest` 森林入口断言。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.forest.ForestLayoutTest" --tests "com.treelearn.app.forest.ForestPresenterTest" --tests "com.treelearn.app.screen.ForestScreenLayoutTest" --tests "com.treelearn.app.ui.HomeDrawerPresenterTest"` 与 `./gradlew :data:testDebugUnitTest --tests "com.treelearn.data.db.dao.TopicForestDaoStatementTest"`。
- Exit code: 1（`:data` 与 `:app` 均编译失败）
- Observed failure: `:data` 报 6 处「错误: 找不到符号」，全部指向尚不存在的 `TopicDao.ForestCount` 与 `TopicDao.TOPIC_FOREST_COUNTS_SELECT`；`:app` 报 77 处同类编译错误，指向尚不存在的 `ForestLayout`、`ForestPresenter`、`ForestViewState`、`ForestScreen`、`ForestScene`、`ForestTreeSprite` 与 `HomeDrawerPresenter.Sink.openForest`。
- Failure reason validated: 错误全部落在本片新增的生产符号上，测试自身语法与断言对象均按规格书写；先补生产实现才能进入断言阶段。

### GREEN — minimum implementation
- Implementation: <待记录>
- Command: 同上。
- Exit code: <待记录>
- Passing evidence: <待记录>
- Regression evidence: <待记录>

### REFACTOR — optional
- Cleanup: <待记录>
- Regression evidence: <待记录>

### LOOP — optional
- Command and rounds:
- Failure identity and repeat count:
- Stop condition hit:
- Handoff:

### TDD or sizing exception — only with prior user approval
- Exception type: sizing（预计约 950 行，超过 500 行硬线；无 TDD 例外）
- Reason: 抽屉入口、数据读法、稳定布局、自绘场景、四态页面与宿主导航构成一个不可分割的回顾面；拆卡会让同一套「主题集合同源 + 一条计数查询 + 树精灵几何」的判据在两片之间漂移，也增加一次真机验收。
- User approval: 用户 2026-10-23 指令「按照harness，结合当前UX风格，进行开发，在web和android上」；森林规格 040 的已拍板记录已把该指令登记为进入实现的授权，本片按 standing 指令连续执行，未就规模单独逐项请示，此处如实登记供事后裁定。
- Alternative verification: TDD 仍按 required 执行；规模不改变任何一次红/绿判据。

## Implementation notes

- 计数投影：`ForestCount(topicId, branchCount, archivedCount, lastVisitedAt, personaWireId)`。前四个值对应规格的只读统计；`persona` 是同一 `topics` 行的第五列，合并在同一条语句里避免每棵树再开一次查询，纸条仍使用既有 `Persona.fromWireId` 回落边界。
- `ForestLayout` 用 FNV-1a 稳定散列、黄金角散布、边界收敛；输入按稳定散列排序后分配槽位，因此返回顺序跟随输入、位置不跟随输入顺序。树数超过舒适密度时缩小树冠并做最小间距修正。
- 场景在构造期缓存 Paint 与点阵 BitmapShader；树精灵只从 seed 生成随机序列，选中重绘不改变树形。
- 森林不缓存主题集合：每次进入重新读，和学习库一样；森林不写任何表，因此没有 store 写方法或事务包裹。
- 空态按钮只回首页并聚焦既有输入框，不新增第二个创建入口。
- 本片不新增 schema、迁移或数据库版本号；森林的「含归档」只发生在自己的聚合投影里。

## Verification report

- Verdict: PENDING
- Evidence: <待记录>
- Changed/deleted assertion rationale and equivalent behavior coverage:
- Gaps:
- Risks:

## Handoff notes

- Completed: 任务卡建立。
- In progress: 实现与验证。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 片 069/070 Web 人设与森林；片 071 文档与证据。
- Watch-outs: 真机返回用手势验证（`input keyevent 4` 在 API 36 会直接结束 Activity）；重装用 `adb install -r`；不要跑会清数据的全量仪器测试；转场后等稳定帧再截图。
