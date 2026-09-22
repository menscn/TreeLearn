# Treelearn Harness Task: 学习库就地搜索与独立搜索屏退役

id: T-2026-09-22-054
title: 学习库就地搜索与独立搜索屏退役
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 330
shared_code_touched: true
depends_on: [T-2026-09-22-bottom-tab-shell-and-library-screen-052.md, T-2026-09-22-library-archived-list-and-subtree-delete-053.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿的信息架构与视觉**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。本片是本批**功能风险最高**的一片：新稿把全局检索降级成了「只筛主题标题与摘要」的过滤框，照搬即等于砍掉 PRD 第 214 行的 F08【P0】能力。
- Problem/background: 新稿 `src/screens/SearchScreen.tsx`（91 行）存在，但 `src/App.tsx` 的 `Screen` 类型只有 `home | chat | nav | canvas | settings | archive`——**从未导入或路由到它**，是死代码；同时 `src/screens/ArchiveScreen.tsx` 第 38–39 行的过滤框只比较 `root.title`/`root.summary` 与已归档分支的 `b.title`/`b.summary`，日常非根分支**完全搜不到**。工程侧相反：`SearchDao` 是七臂 `UNION ALL`，逐字段检索标题、问题、锚定原文、回答、思考、失败尝试的半成品内容，并有 8 个搜索相关测试。
- Expected outcome: 检索的唯一入口是**学习库顶部的搜索框**；检索引擎**不换**，仍走既有的六类字段全局检索；结果在学习库列表区以「搜索结果态」呈现，带祖先路径与归档状态；独立搜索屏的**视图层**退役，检索栈与领域类型完整保留。
- Core flow and reversibility: 查找路径为「学习库顶部搜索框输入 → 检索中 → 有结果（替换列表区）/ 无结果 / 失败（保留草稿）→ 点结果进入对应分支或定位到锚定原文」。检索入口的搬迁是可逆的（入口位置变了，能力没变）。
- Inputs/outputs: 输入为搜索关键词与结果点击；输出为结果列表视图状态（沿用 `search/SearchViewState.java` 与 `search/SearchPage.java`）。
- State and exception handling: 检索四态——尚未输入（空闲）、检索中、有结果、无结果或失败（两者文案不同，失败保留草稿）；空查询不触库；迟到响应被丢弃而不是渲染。以上全部沿用 `SearchPresenter` 现有实现，本片不得改动其语义。
- Constraints and trade-offs: 视图层删除是本批唯一的一次「删代码」，已由用户明确授权（SPEC 疑虑 3 结论）。删除范围严格限定在**只服务于独立搜索屏**的文件；检索栈、领域类型、DAO 与全部搜索测试必须保留，覆盖面不缩水。结果行**只保留一套**布局，不得复制第二套。
- Acceptance evidence: 由真实命令输出提供——六类字段各命中一次的检索断言；四态断言；入口可达断言；删除后全量单测全绿的回归证据。
- Facts: `SearchScreen` 在生产代码中只被 `MainActivity` 引用（import、字段、`showSearch()`、`drawSearch()` 与 6 处置空）；测试侧只有一个文件引用了入口（`HomeSearchEntryTest`，属首页侧，不在本片删除范围）。SPEC 列出的 8 个搜索相关测试中，`SearchPresenterTest`、`SearchPageTest`、`SearchJumpTest`、`SearchHitTest`、`SearchQueryTest`、`SearchDaoStatementTest`、`SearchUnreadReadingInstrumentedTest` 七者测的是**检索栈与领域类型**，均须保留。
- Non-goals: 不改检索范围、不改 DAO 语义、不改 `SearchPresenter` 的状态机；不改首页（055）；不改已完成区（053）；不新建第二套结果行布局。

## Goal

学习库顶部的搜索框成为检索的**唯一入口**且能力一项不减——仍能命中 PRD 规定的六类字段（标题、问题、锚定原文、回答、思考、失败尝试的半成品内容），结果带祖先路径与归档状态，四态行为不变；同时独立搜索屏的视图层退役，而检索栈完整保留。

## Scope

### In scope
- `res/layout/screen_library_body.xml`（及 `layout-sw600dp/` 镜像）：把 052 预留的搜索框占位接到真实输入；结果态替换列表区。
- `screen/LibraryScreen.java`：搜索框的文本监听、四态渲染、结果点击转发（屏不做判定）。
- `library/LibraryPresenter.java`、`LibraryViewState.java`、`LibraryData.java`：接入既有 `search/SearchPresenter`（或经 `library/LibraryData` 端口转发到 `search/SearchRepository`），承载「列表态 / 搜索结果态」两种列表内容。
- `MainActivity`：删除 `showSearch()`、`drawSearch()`、`searchScreen`/`searchPresenter` 字段与 import、以及 6 个 `showX()` 里的 `searchScreen = null;` 行；保留 `openAnchor`/`showChat(branchId, explicitAnchor)` 这条既有缝（它是搜索跳转的生产消费者，不得删）。
- **删除**（唯一一次删代码，范围严格限定）：`screen/SearchScreen.java`、`res/layout/screen_search.xml`、`res/layout/screen_search_body.xml`、`res/layout-sw600dp/screen_search.xml`。
- **保留**：`res/layout/item_search_hit.xml`（作为唯一的结果行布局；若其字段不足以承载「命中字段名、祖先路径、归档状态、片段」四类信息，在本片内**扩展它**，不得新建第二套结果行）。
- **保留**：`search/` 全部类、`data/` 的 `SearchDao`、`core/` 的 `SearchHit`/`SearchQuery`、以及 7 个检索栈测试。
- 新增 `LibrarySearchEntryTest`（纯 JVM）：断言学习库搜索入口**无条件可达**——在没有任何主题时也能进入检索态（这是 `HomeSearchEntryTest` 的核心性质，必须在新落点上被继承）。
- `res/values/strings.xml`：追加 `library_search_*` 前缀的键。

### Out of scope
- `HomeSearchEntryTest` 与其断言的首页入口——**属 055 的删除范围**（见 Implementation notes 的责任划分）
- `screen_home*.xml`（055 的领地）
- 检索范围、DAO 语义、`SearchPresenter` 状态机
- 导航屏、画布、设置屏
- 数据库 schema 与 migration

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**检索入口的位置**（独立屏 → 学习库顶部搜索框）与**结果的呈现位置**（独立屏 → 学习库列表区）。必须原样保留的行为是：六类字段的检索覆盖、只取当前显示版本的连接语义（经 `messages.active_revision_id`）、四态迁移、空查询不触库、迟到响应被丢弃、失败保留草稿、以及结果点击的锚定定位（`openAnchor`）。本片**不得**因为入口搬迁而收窄任何一类的检索范围——这正是用户在原方案上回退澄清过一次的那条线。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 检索的生产者是 `search/SearchPresenter.java`，消费端口是 `search/SearchRepository.java` → `wiring/SqliteSearchStore.java` → `data/…/dao/SearchDao.java`（七臂 `UNION ALL`）。结果行的消费端口是 `core/…/SearchHit.java`（携带 topicId/branchId/messageId/ordinal/revisionId/revisionNo/snippet）。兄弟入口是对话屏的锚定跳转（复用 `MainActivity.openAnchor`）。平台分支为 `layout/` 与 `layout-sw600dp/` 两套。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 本片不改 `android/core/` 的领域类型与 `android/data/` 的 DAO 语义；检索链路的每一段都保留，只有视图层换宿主。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 检索入口由独立搜索屏迁移到学习库顶部搜索框；独立搜索屏的视图层被删除；检索栈与结果行布局保留。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/search/*`（检索栈）、`android/data/src/main/java/com/treelearn/data/db/dao/SearchDao.java`（七臂）、`android/core/src/main/java/com/treelearn/core/domain/SearchHit.java`、`android/app/src/main/java/com/treelearn/app/MainActivity.java`（退役点）。
- Documentation surfaces checked: `android/README.md` 的「应用层入口索引（`:app`）—— 搜索页与首页搜索入口（T-040）」一节（第 558 行位起）、`Docs/06-delivery/TreeLearn_V1_交付报告.md`（历史快照，记载 T-040 已接线）、`Docs/03-prd-draft/V3.2-…PRD.md`。
- Documentation files updated: `android/README.md` 的 T-040 小节必须改写——它现在描述的是独立搜索屏与其四个布局，本片删除了其中三个；改写为「检索栈保留、入口迁至学习库」并保留 `artifacts/evidence/T-040-source-scan.txt` 的引用（历史证据不改写）。
- Producer/consumer/sibling/compatibility paths checked: `SearchPresenter`、`SearchRepository`、`SqliteSearchRepository`、`SqliteSearchStore`、`SearchDao`、`SearchPage`、`SearchViewState`、`BranchTitles`、`SearchHit`、`SearchQuery`；`MainActivity.openAnchor` 与 `showChat(branchId, anchor)`。
- Superseded phrases/examples reverse-searched: 「`screen_search`」「独立搜索屏」「搜索页的入口」「`home_search`」在 `android/README.md`、`Docs/06-delivery/`、`Docs/04-ux-design/` 中的位置。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次确有当前态文档变更（README 的 T-040 小节）。
- Historical/PRD handling: `Docs/06-delivery/TreeLearn_V1_交付报告.md` 是历史快照，记载 T-040 时的状态与「搜索页（T-040）已接线」，**不改写**；但须确认新 README 文本不会让读者以为独立搜索屏仍然存在。

## Acceptance criteria

- [ ] 六类字段覆盖不缩水：检索断言逐字段命中标题、问题、锚定原文、回答、思考、失败尝试的半成品内容六类，每类各至少一次；只取当前显示版本的语义不退化（沿用经 `active_revision_id` 的既有断言）。
- [ ] 入口唯一且无条件可达：学习库顶部搜索框是唯一入口；没有独立搜索屏可被路由；`LibrarySearchEntryTest` 断言在**没有任何主题**时也能进入检索态。
- [ ] 结果四字段齐备：结果项显示命中字段名、祖先路径、归档状态、片段；跨归档状态的命中必须显式标出归档状态。
- [ ] 四态行为不变：尚未输入 / 检索中 / 有结果 / 无结果或失败（两种文案不同，失败保留草稿）各有断言；空查询不触库；迟到的响应被丢弃而不是渲染。
- [ ] 视图层退役且范围正确：`screen/SearchScreen.java` 与 3 个 `screen_search*` 布局被删除；`git status` 证明 `item_search_hit.xml`、`search/**`、`SearchDao.java`、`SearchHit.java`、`SearchQuery.java` **未被删除**；全仓 `grep -rn "SearchScreen"` 零命中（除历史证据文件）。
- [ ] 锚定跳转不退化：结果点击仍能进入对应分支并定位到锚定原文，复用既有 `MainActivity.openAnchor`；`SearchJumpTest` 全绿。
- [ ] 回归：`SearchPresenterTest`、`SearchPageTest`、`SearchJumpTest`、`SearchHitTest`、`SearchQueryTest`、`SearchDaoStatementTest`、`SearchUnreadReadingInstrumentedTest` 七者全绿，且全量 `:app` 单测通过计数不低于本片开工前的基线（须记录两个数字）。
- [ ] 平台分支覆盖：`layout/` 与 `layout-sw600dp/` 两套都覆盖学习库的搜索态；`.\gradlew.bat check` 退出码 0，Java-only 守门 `android/scripts/no-kotlin-check.ps1` 退出码 0。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING（进入 `in_progress` 前补齐）——预期先写 `LibrarySearchEntryTest` 与六类字段的检索断言（含无主题时入口可达、空查询不触库），此时学习库尚无搜索框，观察失败。
- Command: PENDING（预期形如 `.\gradlew.bat :app:testDebugUnitTest --tests "*LibrarySearch*"`）
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING（须与 RED 同一条目标测试命令）
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: 规模（超 200 软上限，未超 500 硬上限，故不申请规模例外）
- Reason: 330 行里相当一部分是**删除**（`SearchScreen` 与其布局、`MainActivity` 的退役点）与**搬迁**（搜索态从独立屏的渲染代码搬到学习库的渲染路径），净新增远小于估算。更重要的是本片有一项不可拆的耦合：「先让新入口可用」与「再让旧入口消失」必须由同一个任务保证——若拆成两片并在中间被中断，会出现「新旧入口并存」或「旧入口已删、新入口未接」的功能空窗，而检索是 P0。据此记录理由而非警告。
- User approval: 不适用（未超 500 硬上限，按规则不需用户批准的规模例外）
- Alternative verification: 完成后由 verifier 用 `python .trae/scripts/dev/count_production_lines.py` 对实际改动的生产文件复算（非空非注释口径）；实测超 500 则本片判定失败并重新切分。

## Implementation notes

- **红线复述**：新稿的库内过滤框只比较主题根标题/摘要与已归档分支的标题/摘要（`src/screens/ArchiveScreen.tsx` 第 38–39 行），日常非根分支完全搜不到。工程必须保留六类字段的全局检索。这条已由用户在原方案上回退澄清过一次，不允许在实现中再被简化。
- **测试覆盖的责任划分（本片与 055 的界）**：本片**新增** `LibrarySearchEntryTest` 继承「搜索入口无条件可达」这个性质，并**不动** `HomeSearchEntryTest`。055 在移除首页搜索入口时删除 `HomeSearchEntryTest`，并在它的任务里引用本片新增的测试作为等价覆盖。这样两片不会互相踩同一批断言。
- 结果行**只保留一套**布局。优先复用 `res/layout/item_search_hit.xml`；若字段不足就在它上面扩展。复制出第二套结果行布局会导致两处各自漂移——这正是本仓失败案例库里记过的重复实现模式。
- `MainActivity.openAnchor` 与 `showChat(branchId, explicitAnchor)` 是 T-042 一条缝的**唯一真实生产消费者**，删除搜索屏时**不得**顺手删掉它——`SearchJumpTest` 直接盯着这条路径。
- 检索栈整体保留：`search/` 全部类、`SearchDao` 的七臂、`core/` 的 `SearchHit`/`SearchQuery`。删除范围只有 `screen/SearchScreen.java` 与 3 个 `screen_search*` 布局文件，一多一少都算越界。
- 删除前先做一次全仓引用扫描（`grep -rn "SearchScreen" android/`），确认生产侧只有 `MainActivity`、测试侧没有别处引用；删除后再扫一次，两次输出都进证据文件。
- `MainActivity` 是本批共享代码区（052/054/055 都改它），必须串行；本片只做「删搜索屏」这一件事，不要顺手做 055 的首页瘦身。

## Verification report

- Verdict: PENDING
- Evidence: 待提供——`LibrarySearchEntryTest` 与六类字段断言的输出；四态断言输出；删除前后的两次 `grep -rn "SearchScreen"` 输出；`git status` 对保留文件的证明；七个检索栈测试的结果；全量 `:app` 单测的通过计数（须与开工前基线并列）。全部落进 `artifacts/evidence/T-054-library-search-retire.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删除既有断言，只删除**视图层文件**。`HomeSearchEntryTest` 的删除属 055，且届时由本片新增的 `LibrarySearchEntryTest` 提供等价覆盖（同一条性质：搜索入口无条件可达）。
- Gaps: 真机输入法行为与像素证据仍由用户自行装机验证；`layout-sw600dp/` 的搜索态只由构建与 id 可达性证明。
- Risks: 最大风险是**入口搬迁过程中检索范围被悄悄收窄**（照搬新稿的过滤框），或**删除范围越界**（连带删掉检索栈或 `item_search_hit.xml`）。两者都必须靠显式断言与两次引用扫描拦住，而非靠自查。

## Handoff notes

- Completed: 检索栈的完整清单已核清（生产者/端口/DAO 七臂/领域类型/7 个必须保留的测试）；`SearchScreen` 的生产引用只有 `MainActivity` 一处；新稿的降级证据（`App.tsx` 未导入 `SearchScreen.tsx`、`ArchiveScreen.tsx` 第 38–39 行）已定位。
- In progress: 待派工。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 等 053 verified 后开工。完成后 055 才能启动（055 需要学习库搜索框已存在，才能移除首页的搜索入口）。
- Watch-outs: (1) 不得把检索降级成只筛标题与摘要；(2) 删除范围只有 `SearchScreen.java` + 3 个 `screen_search*` 布局，`item_search_hit.xml` 与 `search/**` 必须留下；(3) 不得删 `MainActivity.openAnchor`；(4) 不得动 `HomeSearchEntryTest`（属 055）；(5) 本片只做搜索搬迁，不顺手做首页瘦身。
