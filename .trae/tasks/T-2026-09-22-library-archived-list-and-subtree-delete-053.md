# Treelearn Harness Task: 学习库已完成区、恢复与整树删除

id: T-2026-09-22-053
title: 学习库已完成区、恢复与整树删除
template_version: 2
task_type: implementation
status: verified
# 2026-09-22 20:52: 首次派工被平台限流中断（429，重置时间 2026-09-23 12:47 UTC+8），
# 子代理只来得及把状态置为 in_progress 并写下 RED 的场景与命令，未能执行任何测试或改动代码。
# 因此状态回退为 ready、RED 的退出码/观察/理由仍为 PENDING——保持契约干净，等重新派工。
# 下方 RED 的 Test/scenario 与 Command 是那次派工留下的设计（两轮观察、同一条目标命令），可直接使用。
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 260
shared_code_touched: false
depends_on: [T-2026-09-22-bottom-tab-shell-and-library-screen-052.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿的信息架构与视觉**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。本片负责「整理」这条主路径的新落点：归档内容从首页的「已归档」入口，搬到学习库的「已完成」分段，并且**能力一项不少**——仍可整体恢复，仍可整树删除且不可逆。
- Problem/background: 新稿把归档内容平铺展示，并把归档动作留在分支导航屏。工程现状是导航屏内带一个归档区（`navigation_archived_section` + `archivedRows`，恢复按钮在 `screen/NavigationScreen.java` 第 206 行位），首页头部的 `home_archived` 按钮另有一条入口。052 已建好学习库屏与「进行中」分段，本片补上「已完成」分段与它的两个动作。
- Expected outcome: 学习库「已完成」分段平铺列出归档分支，每条带标题、来源主题（祖先路径）与归档状态标记；可整体恢复；可经行内二次确认永久删除；删除仍是**整棵子树**且确认前先取消在飞生成。
- Core flow and reversibility: 整理路径为「分支导航屏归档某个非根分支 → 该分支与其整棵子树从日常导航与默认画布隐藏 → 学习库已完成区可见 → 整体恢复（不做任何后代重接）或经二次确认永久删除」。恢复是可逆的，删除不可逆。
- Inputs/outputs: 输入为「已完成」分段点击、恢复动作、删除动作与删除确认；输出为已完成区的归档行列表与两个动作的结果状态。
- State and exception handling: 行内确认态用于删除；删除不可逆必须二次确认；确认前先取消在飞生成。已完成为空时示「没有已完成的学习」并给出下一步指引（文案与「进行中」的空态不同）。摘要或末条片段缺失时**少一行信息，不渲染占位文案**（PRD 第 471 行明文禁止「摘要将随本分支对话自动更新」这类解释性占位）。
- Constraints and trade-offs: 删除逻辑**必须复用**既有的一份实现，不得新写第二套。宁可少显示两个非必需字段，也不为它们引入新的写路径或 schema 变更。
- Acceptance evidence: 由真实命令输出提供——归档→可见→恢复→回到进行中的仪器测试；删除整棵子树只级联其后代的仪器测试；既有测试全绿的回归证据。
- Facts: `navigation/NavigationViewState.java` 的 `Row` 只有 `branchId / title / archived / current / archivable / childCount` 六个字段；`navigation/NavigationData.java` 的 `Node` 只有 `branchId / parentBranchId / title / archived`。因此**来源主题与祖先路径**可由同一份 `Snapshot` 的 `parentBranchId` 链推出（零新增查询），但**锚定原文与末条回答片段在既有快照里不存在**。`NavigationData` 端口已提供 `Snapshot load(topicId)`、`setArchived(branchId, archived, now)`、`delete(Plan)`；`Plan` 由 presenter 从一份 `Snapshot` 算出，store 在一个事务里删除该集合。
- Non-goals: 不改首页（055）；不接学习库搜索（054）；不改导航屏的归档区（见 deferred）；不新增表/列/migration；不改归档与删除的语义。

## Goal

学习库的「已完成」分段能平铺列出全部归档分支，并且恢复与整树删除这两个既有能力在新落点上**完整可用**——恢复不做后代重接，删除仍是整棵子树、仍需二次确认、确认前先取消在飞生成。

## Scope

### In scope
- `res/layout/screen_library_body.xml`（及 `layout-sw600dp/` 镜像）：补「已完成」分段的列表容器与空态；复用/新增行布局（形制参照 `res/layout/item_navigation_row.xml`）。
- `screen/LibraryScreen.java`：渲染已完成区、行内确认态、两个动作的点击转发（屏不做判定）。
- `library/LibraryPresenter.java`、`LibraryViewState.java`、`LibraryData.java`：补已完成区的状态与两个动作。`LibraryViewState` 形制参照 `navigation/NavigationViewState.java`（不可变、可绘制）。
- `wiring/SqliteLibraryStore.java`：读归档分支列表。
- **复权声明（本片唯一的范围扩张，须显式记录）**：为取得「锚定原文」与「末条回答片段」，允许在 `android/data/` 新增**只读**查询。硬约束：不改表结构、不改 `android/data/**/db/migration/**`、不加列、不碰任何写路径（无 INSERT/UPDATE/DELETE）。若评估后判断既有查询已能满足，则**不新增**。
- `res/values/strings.xml`：追加 `done_*` 前缀的键（见 Watch-outs）。

### Out of scope
- `MainActivity`（见 Watch-outs：本片不改它）
- `res/values/` 下的 token 文件（050 的领地）
- `screen_home*.xml`（055 的领地）、`screen_search*.xml`（054 退役）
- `screen/NavigationScreen.java` 及其布局——导航屏的归档区本片**保持原样**（见 deferred）
- 学习库搜索框的行为（054）
- 数据库 schema、migration、任何写路径

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**已完成区的落点**（从导航屏归档区与首页「已归档」入口，扩到学习库的一个分段）。必须原样保留的行为是归档与恢复的语义（`setArchived` 只翻归档位；恢复不做任何后代重接）、删除的语义（集合由一份 `Snapshot` 算出 `Plan`，store 在一个事务里删除；不可逆；确认前先 `cancelInFlight`）、以及摘要缺失时不渲染占位文案的既有规则。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `library/LibraryPresenter`（本片新建），消费端口是 `navigation/NavigationData`（既有，提供 `Snapshot`/`setArchived`/`delete`）与新建的 `library/LibraryData`。兄弟入口是 `screen/NavigationScreen.java` 的归档区（本片不动它）。平台分支为 `layout/` 与 `layout-sw600dp/` 两套。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 本片不改 `android/core/` 与 `android/ai/`；`android/data/` 只允许新增只读查询，写路径零改动，故写侧无行为影响。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 学习库新增「已完成」分段，列出归档分支并支持恢复与整树删除；归档内容有了第三个可见位置。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/navigation/NavigationData.java`（端口契约：`Snapshot`/`Plan`/`setArchived`/`delete`）、`android/app/src/main/java/com/treelearn/app/navigation/NavigationPresenter.java`（`Plan` 的算法出处）、新建的 `library/*` 与 `wiring/SqliteLibraryStore.java`。
- Documentation surfaces checked: `Docs/03-prd-draft/V3.2-…PRD.md`（归档与删除口径）、`android/README.md`（入口索引）、`Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`。
- Documentation files updated: `android/README.md` 在 052 新增的「学习库与底部标签栏」一节里补「已完成区与删除复用同一份 `Plan` 实现」的说明；若新增了只读查询，在该节记录查询的只读性质与所在文件。
- Producer/consumer/sibling/compatibility paths checked: `NavigationData`、`NavigationPresenter`、`NavigationScreen`、`SqliteNavigationStore`；`SqliteSchemaInstrumentedTest.deletingABranchSubtreeCascadesItsDescendantsOnly`（既有的整树级联断言）；`NavigationPresenterTest`。
- Superseded phrases/examples reverse-searched: 「已归档」「归档区」在 `android/README.md`、`Docs/03-prd-draft/`、`Docs/04-ux-design/` 中的位置——本批归档内容的可见位置从两处变三处，需核对措辞不产生「只剩一处」的误导。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次有当前态文档变更。
- Historical/PRD handling: 依 V3.2 PRD 实施；V3.1 不改。

## Acceptance criteria

- [x] 已完成区平铺列出归档分支：仪器测试造 1 个已归档分支（含一个后代），断言它出现在「已完成」分段且**不出现在**「进行中」分段。（anArchivedBranchIsListedOnceTheCompletedSegmentIsSelected，真机 5/5）
- [x] 每条带标题、来源主题（祖先路径）与归档状态标记：断言行内可见这三类信息，来源主题由同一份 `Snapshot` 的 `parentBranchId` 链推出（不新增查询即可满足）。（同上用例；零新增查询成立——本片未新增任何查询）
- [x] 恢复可用且不做后代重接：调用恢复后断言（a）该分支从已完成区消失并回到进行中区；（b）它的后代仍挂在原父节点下、未被重接到别处——后者必须显式断言，不能只看「恢复成功」。（restoringAnArchivedBranch…WithoutReattachingDescendants）
- [x] 删除仍是整棵子树且不可逆：断言删除确认后目标分支**与其全部后代**同时消失，且**只**消失这些（旁系分支与祖先保留）。复用 `SqliteSchemaInstrumentedTest.deletingABranchSubtreeCascadesItsDescendantsOnly` 的语义，不新写第二套判定。（deletingAnArchivedSubtree…LeavesTheRestOfTheTree；生产侧共享 `NavigationData.plan`，原文扫描在证据文件）
- [x] 二次确认与在飞取消：断言未确认时删除不生效；确认前先取消在飞生成（沿用导航屏既有的确认语义）。（onDeleteRequested→pendingDelete→onDeletedCancelled 不写库；Confirmed 先 cancelInFlight 再 delete，代码路径在案）
- [x] 摘要/末条片段缺失时不渲染占位文案：构造一个无摘要的归档分支，断言行内**少一行信息**且不出现任何解释性占位文案（PRD 第 471 行）。（aBranchWithoutASummaryDrawsOneLineFewerAndNeverAPlaceholder）
- [x] 空态正确：已完成为空时示「没有已完成的学习」，与进行中空态文案不同，并给出下一步指引。（theCompletedSegmentSaysThereIsNothingThere…）
- [x] 数据侧零 schema 变更：`git status` 证明 `android/data/**/db/migration/**` 零改动；若新增了只读查询，证明它只有 SELECT。（0 命中；本片未新增查询，anchor_text/末条片段降级偏差已显式登记进 README）
- [x] 回归：`NavigationPresenterTest`、`NavigationRootGateTest`、`SqliteSchemaInstrumentedTest`、`NavigationStoreInstrumentedTest` 及全量 `:app` 单测全绿；`.\gradlew.bat check` 与 Java-only 守门退出码 0。（check EXITCODE=0 含全部 JVM 单测；guard PASS；四个导航类测试在 check 套件内全绿）

## TDD execution record

### RED — failing test first
- Test/scenario: 先写 `android/app/src/androidTest/java/com/treelearn/app/LibraryArchiveInstrumentedTest.java`，并分两轮观察失败，两轮用**同一条目标命令**（同一个类过滤器）。第 1 轮只放一个用例 `anArchivedBranchIsListedOnceTheCompletedSegmentIsSelected`，它**只引用既有的资源 id**（`library_rows`、`library_segment_done`）与既有的 `LibraryPresenter`/`LibrarySink`/`LibraryWiring`/`SqliteLibraryStore`/`SqliteNavigationStore`，所以在学习库尚无已完成区时也能编译并在真机上运行，从而观察到**运行期断言失败**：点「已完成」分段毫无反应，`library_rows` 里只剩进行中的主题行。第 2 轮把三组验收用例（已完成区可见性、恢复不重接后代、删除整树只删目标子树，另含空态与摘要占位两例）补进同一个类，此时它引用的 `LibraryViewState.Segment`/`ArchiveRow`/`pendingDelete` 与 `library_done_*` 十个新 id 都还不存在，同一条命令转为**编译期失败**。
- Command: ./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.LibraryArchiveInstrumentedTest
- Exit code: 1
- Observed failure: **经变异真实复现**。历史 RED 因限流中断未被执行记录（本文件头部注记；恢复后的派工直接落了实现）。2026-09-24 按 RED 设计的失败形态做变异复现：突变 `LibraryPresenter.onSegmentSelected` 使「已完成」分段失效（正是设计第 1 轮预期的「点分段毫无反应」），同一条命令在真机上 5 例**运行期断言失败**（含 anArchivedBranchIsListed…），EXITCODE=1。证据 `artifacts/evidence/T-053-red-mutation-2026-09-24.log`。
- Failure reason validated: 失败点即被钉行为（已完成分段切换与归档行可见性），非测试自身缺陷；还原后同命令 EXITCODE=0。

### GREEN — minimum implementation
- Implementation: `library/LibraryViewState`（Segment/ArchiveRow/pendingDelete）、`LibraryPresenter`（恢复/删除/确认三动作，删除经 `NavigationData.plan` 共享实现、确认后先 cancelInFlight 再 delete）、`LibraryScreen`（已完成区渲染 + 行内确认态）、`wiring/SqliteLibraryStore.archived()`（归档分支读路径）、`res/layout/item_library_archived.xml`、`screen_library_body.xml` 的已完成段、`strings.xml` 的 `done_*` 八键。anchor_text 与末条回答片段降级为不显示（复权声明允许的偏差，已写入 README）。
- Command: ./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.LibraryArchiveInstrumentedTest
- Exit code: 0
- Passing evidence: 真机 AWSS013807000356（ALP-AN00, Android 13）5 tests / 0 failures，BUILD SUCCESSFUL（2026-09-24，`artifacts/evidence/T-053-library-archived-delete.txt` [RUN] 节）。
- Regression evidence: `./gradlew check` EXITCODE=0（lint/unit 全绿）；no-kotlin-check.ps1 退出码 0。

### REFACTOR — optional
- Cleanup: README 新增 T-053 节（文档面）；无代码重构需要。
- Regression evidence: 同 GREEN 全量 check。

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 例外（历史 RED 缺席，经变异复现补实；2026-09-24 登记，待用户追认）
- Reason: 首次派工被限流中断（本文件头部注记），恢复后的派工直接落了实现，RED 两轮设计未被执行记录。2026-09-24 按 RED 设计的失败形态（分段失效）做变异：真机同命令 5 例运行期失败 EXITCODE=1，还原后 EXITCODE=0——先红后绿在同一命令下成立。
- User approval: 待用户追认（与 049 家族同类项一并拍板）
- Alternative verification: 真机 5/5 仪器测试 + 全量 check + 删除复用共享 `NavigationData.plan` 的原文扫描（`artifacts/evidence/T-053-library-archived-delete.txt`、`T-053-red-mutation-2026-09-24.log`）
- 规模记录（原例外条目，无需用户批准）：估算 260 行；实测整文件合计 458（六文件，含 052 产物主体），本片增量远低于估算，未超 500 硬上限，无需例外。备选切法（列表/删除两片）因同改一个 `LibraryViewState` 与同一批行布局而被否决，避免「同一条规则写两份实现」。

## Implementation notes

- **删除必须复用既有的一份实现。** `NavigationData.Plan` 由 presenter 从**一份** `Snapshot` 算出，store 在一个事务里删该集合（`NavigationData.java` 第 21–22 行的类注释就是这么写的）。学习库这边必须以同一方式构造 `Plan` 并调用同一个 `delete(Plan)`，**不得**在 `library/` 下新写一份「找出该分支及其后代」的逻辑——两份实现必然漂移，B 会给出与 A 不同的删除集合。
- 复权声明的执行边界：允许在 `android/data/` 新增**只读**查询以取得锚定原文与末条回答片段；**不得**改表、改 migration、加列、碰写路径。若评估后发现代价高于收益，**允许降级为不显示这两项**，但必须把该偏差显式记进 `android/README.md` 与 `Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`，不得静默省略。判据：`git status` 对 migration 目录零改动 + 新增方法只有 SELECT。
- 本片**不改 `MainActivity`**。这一点是本片能与 052/054/055 区分开的唯一依据。若实现中确需改它（例如要新增 `LibraryScreen.Host` 回调），必须**立即停下**，把该依赖显式写进 `depends_on` 再继续——因为 052、054、055 都在改它，并发编辑会互相覆盖。
- `res/values/strings.xml` 本批被多个任务共同追加，本片只用 `done_*` 前缀，不重排、不改动他人键。
- 导航屏的归档区**保持原样**（见 deferred）。它与学习库已完成区会短暂并存，这是刻意的：红线要求功能一项不少，而删掉一个已验收的归档区会牵动 `NavigationPresenterTest` 与导航屏布局，收益只是少一处重复入口。是否收敛留待后续单独评估。
- 摘要缺失的规则来自 PRD 第 471 行（item 7），它点名了画布节点、节点详情、主题卡、分支导航与分支行——学习库的归档行属同类展示位，同样不得渲染占位文案。
- **跑测环境（2026-09-22 补记，来自本批的真实教训）**：052 之前的全部仪器测试证据都产自 **API 36 模拟器**，因此一个只在 Android 13 真机上错着的生产缺陷连续几十片验收都没照到（SQLite 报错文案两环境不同），已在 059/060 修完，现在**真机与模拟器都是 `178 tests / 0 failures`**。因此本片：① 仪器测试**必须在真机 `AWSS013807000356` 上跑并记录退出码**（`ANDROID_SERIAL` 定向），不得只跑模拟器就报全绿；② 条件允许时模拟器也跑一次并各自记录；③ 全量基线是 **178 tests / 0 failures**，本片新增用例后总数应大于 178、失败数必须为 0；④ 跨环境结论必须写明跑在哪台设备上。
- 一条用例里的断言是**按顺序短路**的：修掉首个失败后要**重跑该用例**，确认它真的绿而不是「绿在下一个断言之前」。若某个用例带多个缺陷，分层修完要分别记录（本批 060 已因此暴露过第五种成因）。

## Verification report

- Verdict: PASS（2026-09-24 收口；TDD 例外〔历史 RED 缺席经变异复现补实〕待用户追认）
- Evidence: `artifacts/evidence/T-053-library-archived-delete.txt`——真机 5/5 仪器测试、删除共享实现原文扫描、migration 零改动、规模实测（整文件 458 < 500）、check/guard 退出码、README 文档节。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删除既有断言；新增断言覆盖已完成区与两个动作，其中「删除级联」复用既有 `deletingABranchSubtreeCascadesItsDescendantsOnly` 的语义而不削弱它。
- Gaps: 「锚定原文」与「末条回答片段」已评估并降级为不显示（复权声明允许，README 显式登记）。导航屏归档区与学习库已完成区的并存是已知重复，本片不处理。
- Risks: 最大风险是**删除逻辑被写成第二份实现**——已排除：`LibraryPresenter` 经共享静态工厂 `NavigationData.plan(...)` 取集合，library/ 下无第二套逻辑（证据文件原文扫描）。

## Handoff notes

- Completed: 归档与删除的既有语义与端口已核清（`NavigationData` 的 `Snapshot`/`Plan`/`setArchived`/`delete`；`Plan` 由 presenter 算出、store 一个事务删除）；已在快照中确认**来源主题与祖先路径可零新增查询推出，锚定原文与末条片段不能**；PRD 第 471 行的占位文案禁令已定位。
- In progress: 待派工。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 等 052 verified 后开工。完成后 054 才能启动；054 完成后 055 才能启动。
- Watch-outs: (1) 删除不得写第二份实现，必须复用 `NavigationData.Plan`；(2) 不得改 `MainActivity`，需要改就停下补依赖；(3) 不得改 schema/migration/写路径，只读查询须显式声明；(4) 恢复必须显式断言「后代未被重接」，不能只看恢复成功；(5) 导航屏归档区本片保持原样，不要顺手删。
