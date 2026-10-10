# Treelearn Harness Task: 新建子分支后返回父分支

id: T-2026-10-01-115
title: 对话返回按分支父级逐层上行
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-01-android-daily-use-issues-111.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-09-29-recommendation-fork-auto-send-108.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 的 #3 指出新建子分支后点击左上角返回会回首页，而不是回到创建它的父节点。

- Problem/background: 用户实测指出按最近访问分支返回不符合预期，要求分支返回逐级向上；项目树关系由 `Branch.parentBranchId` 明确表示。
- Users/scenarios: 在聊天页通过学习库、导航页、画布、回答分叉或子分支入口打开任意深度分支，再点顶部返回。
- Expected outcome: 每次顶部返回恰好打开当前分支的直接父分支；无父分支时关闭当前对话并重建打开聊天的来源页面，不保留来源页的临时视图状态。
- Core flow and reversibility: 深度 A → B → C，每按一次返回依次到 B、A；外部打开 C 时同样先到 B；根分支 A 返回后关闭对话。
- Inputs/outputs: 当前分支的 `parentBranchId`；不使用访问历史或“上一访问分支”作为返回目标。
- State and exception handling: 父分支为空时走 `ChatSink.close` 并按首次聊天入口恢复主页、学习库、导航页或画布；来源页按正常入口重新创建，不恢复搜索词或画布视口；系统返回路径保持不变。
- Constraints and trade-offs: Java-only；不改分支数据库关系、创建流程、全局系统返回处理或其它屏幕导航；外部打开深层分支后首次顶部返回改为父分支，而非直接回入口。
- AI recommendation and rationale: 直接读取当前 `Branch.parentBranchId`，因为用户确认“逐层往上”且树节点已保存直接父级；不再维护重复的会话访问栈。
- User choice and custom additions: 2026-10-08 用户确认按父分支每次上移一层，外部入口同样适用；2026-10-08 用户确认根分支关闭后恢复来源页面，但不保留搜索词、画布视口等临时状态。
- AI-autonomous decisions: fork、推荐 fork、子分支选择和所有外部入口共用父级返回规则；同级分支返回共同父分支。
- User-confirmation decisions: 只改聊天页顶部返回；系统返回及其它页面导航保持当前行为。
- Acceptance evidence: Presenter 回归覆盖父级逐层上行；入口路由单元测试覆盖学习库、导航页、画布与一级标签恢复；MainActivity 实际界面及 MagicOS 11 真机手测待后续完成。
- Facts: 顶部按钮调用 `ChatPresenter.onBackPressed`；fork 经 `publish`；子分支选择由 `MainActivity.openChildren` 将回调交给当前 Presenter；外部入口经 `MainActivity.openBranch/showChat`。
- Assumptions: `ChatPresenter` 当前分支快照的 `parentBranchId` 足以确定直接上一级；不要求进程重建导航状态。
- Unknowns: MagicOS 11 真机操作手感尚未确认。
- Risks: 外部打开深层分支的首次返回行为改变为父级；已由用户明确确认。
- Deferred: MainActivity 实际界面验收、MagicOS 11 实机验收、APK dex 级 Java-only 检查及独立 verifier 报告。
- Non-goals: 改造全应用导航栈、修改分支数据模型或重做首页。
- Skipped non-critical questions and reason: 无；外部入口是否也按父级上行已由用户明确确认。

## Goal

聊天页顶部返回按 `Branch.parentBranchId` 每次上行一个分支；根分支返回时关闭当前对话并回到原入口。

## Scope

### In scope
- 移除 `ChatPresenter` 与 `MainActivity` 之间的访问历史栈；顶部返回读取当前分支的直接父级并用普通分支路由打开。
- fork、推荐 fork、子分支选择及学习库/导航页/画布外部入口都遵循父级层次；根分支返回走既有关闭路由。
- 用定向测试覆盖未访问父级、连续祖先、同级分支、外部深层入口与根分支关闭；MagicOS 11 真机手测待后续验收。

### Out of scope
- 数据库 schema/migration、`MainActivity.onBackRequested` 系统返回路径、其它屏幕导航或分支关系语义变更。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 顶部返回读取当前分支的 `parentBranchId` 并上移一层，根分支通过 `ChatSink.close` 回原入口；分支数据、系统返回及其它屏幕行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): fork → `ChatPresenter.publish/openConversationBranch` → `ChatSink.openBranch` → `MainActivity.showChat`；子分支弹层 → `MainActivity.openChildren` → `ChatScreen.showChildren` 回调 → `ChatPresenter.onChildBranchChosen`；学习库、导航页与画布的 `openBranch/showChat` 同样使用当前分支父级返回规则。
- Native bridge or independent-client compatibility/update requirements: N/A — 无 API/桥接改动。
- Behavior-neutral non-applicability reason, if applicable: N/A — 修改用户可见导航行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 聊天页顶部返回依据当前分支 `parentBranchId` 逐级上行；入口来源不改变规则。
- Authoritative code/config/migration evidence: `ChatPresenter.java`、`ChatSink.java`、`MainActivity.java` 及 Presenter 回归测试。
- Documentation surfaces checked: 分支返回流程的当前业务说明、`android/README.md`。
- Documentation files updated: `android/README.md` 的对话返回行为已同步为按父级逐层上行。
- Producer/consumer/sibling/compatibility paths checked: fork、推荐 fork、子分支弹层、Presenter 替换、外部库/导航/画布开门及顶部返回；系统返回 handler 保持独立不变。
- Superseded phrases/examples reverse-searched: 反查“按访问历史返回”“最近访问分支”“外部入口空历史”“不按 parentBranchId”等旧行为表述。
- `documentation impact: none` reason and evidence, if applicable: N/A — 用户流程发生变化，需同步文档。
- Historical/PRD handling: 不改写历史任务；若历史说明与当前行为不同，标注历史并链接现行流程。

## Acceptance criteria

- [x] 定向测试先红后绿：返回到当前分支直接父级；连续返回逐个祖先上行；同级分支回共同父级。
- [x] 外部深层入口也返回父级，根分支返回后关闭；`ChatPresenterTest` 60 项通过。
- [x] 根关闭路由捕获并分派主页/学习库、导航页和画布来源，且父级切换保留首次入口；新增 `ChatEntryRouteTest` 6 项通过，变异测试能检出入口被覆盖。
- [ ] 从实际 `MainActivity` 交互验证根关闭后来源页面可见；本轮未启动应用或设备。
- [x] Android lint 与依赖方向通过；README/代码反查未发现仍生效的访问历史返回规则。
- [x] Java-only 源码扫描及 53 个变体 classpath 无 Kotlin；guard 明确因跳过 APK dex 阶段返回 PARTIAL。
- [ ] APK dex 完整扫描与 MagicOS 11 / ALP-AN00 应用启动体验待后续验收；本轮不构建、安装或启动 APK。

## TDD execution record

> 2026-10-08 用户拒绝访问历史行为并确认父级逐层上行；此前历史实现证据 `T-2026-10-08-115-navigation-history-red.txt` 与 `T-2026-10-08-115-navigation-history-green.txt` 仅作历史记录，不代表当前验收。最终测试集的 RED 变异与 GREEN 证据见下。

### RED — failing test first
- Test/scenario: `backAfterOpeningForkReturnsToItsTreeParent`, `backFromExternallyOpenedBranchReturnsToItsParent`, `backAfterSelectingAChildReturnsToItsDirectParent`, `backAfterSelectingASiblingReturnsToTheirCommonParent` 与 `nestedBranchReturnsFollowEachParentUntilTheRootCloses`。
- Command: `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --tests com.treelearn.app.ui.ChatPresenterTest`
- Exit code: 1
- Observed failure: 原访问历史实现下 60 项中 4 项失败；最终测试集在“返回直接关闭”变异下 60 项中 5 项失败。
- Failure reason validated: 最终 RED 变异只把 `onBackPressed` 改为关闭；父级、子分支、兄弟、fork 和多层用例全部失败，根分支关闭仍通过。证据 `artifacts/evidence/T-2026-10-08-115-parent-hierarchy-mutation-red.txt`；原实现 RED 见 `artifacts/evidence/T-2026-10-08-115-parent-hierarchy-red.txt`。
- Expanded contract RED: 两次 RED 均使用相同定向测试命令；变异证据的测试 SHA 与 GREEN 一致，历史路由编译证据已被用户确认的新契约取代。

### GREEN — minimum implementation
- Implementation: 删除访问历史栈及专用对话路由；顶部返回读取当前 `readingBranch.parentBranchId` 并用普通分支路由打开父级，无父级时关闭对话；所有入口遵循同一规则。
- Command: `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --tests com.treelearn.app.ui.ChatPresenterTest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-08-115-parent-hierarchy-green.txt`，与 RED 使用同一测试命令；退出码 0。
- Regression evidence: `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.ChatPresenterTest.xml`（60 tests、0 failures/errors/skipped）；覆盖 fork 父级、子分支直接父级、兄弟共同父级、连续祖先、外部深层入口及根分支关闭。

### REFACTOR — optional
- Cleanup: 删除 `branchBackStack`、`openBranchFromConversation` 及对应 Presenter 路由参数，避免保留与树父级规则冲突的访问历史状态。
- Regression evidence: 同 GREEN 测试套件。

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: 无例外；按 TDD 执行。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 不保存访问历史栈；当前分支快照里的 `parentBranchId` 是唯一逐级返回来源；所有入口共用普通 `openBranch` 路由。
- `ChatEntryRoute` 记录首次打开聊天的来源；父分支切换保留该路由，从聊天进入画布/导航页后再开聊天则记录新的来源。恢复来源页面时重新创建页面，不复用临时视图状态。
- 估算生产变更 190 行；超 200 行时先拆任务。

## Requirement clarification — 2026-10-08

- 用户最新反馈：“返回栈还是不对！没有按照分值一层一层往上”；项目没有“分值”字段，用户确认按**分支父级**逐层返回。
- 每次聊天页顶部返回打开当前 `Branch.parentBranchId`；连续操作逐个祖先上行；同级分支返回共同父级；包括学习库、导航页和画布打开的深层分支。
- 到根分支（`parentBranchId == null`）后关闭聊天并重建打开聊天的来源页面；用户确认不保留来源页的搜索词、画布视口等临时状态。此前按访问历史返回及“外部深层入口直接关闭”的口径由本次确认取代。
- 系统返回 handler、数据库关系、创建流程和其它页面导航仍不在本任务范围内。
- 本轮不得构建、安装或启动设备应用；MagicOS 11 真机操作由用户后续验收，当前保留为待验证项。

## Root-entry follow-up — 2026-10-08

- 独立 verifier 发现 `MainActivity.close()` 原先只调用 `showTab(currentTab)`，导航页/画布等来源页面已在 `showChat()` 中销毁，不能返回原入口。
- 用户确认根分支关闭应恢复实际来源页面，但不保留临时视图状态。实现通过 `ChatEntryRoute` 记录首次入口；普通父分支切换不覆盖，进入画布/导航页后重新开聊时记录新的来源。
- 定向红测将 `captureIfAbsent` 退化为总是捕获当前屏：6 项中 `openingAncestorBranchesDoesNotReplaceTheOriginalEntry` 失败；恢复后 6 项通过。证据分别见 `artifacts/evidence/T-2026-10-08-115-entry-route-red.txt` 与 `artifacts/evidence/T-2026-10-08-115-entry-route-green.txt`。
- 单元测试覆盖路由决策与分派，不等同于 `MainActivity` 真机交互验证；该验证仍待后续执行。

## Verification report

- Verdict: PARTIAL
- Evidence: 原父级层级测试及其首次 lint/依赖方向结果见 `parent-hierarchy-*.txt`。本次最终代码的 `:app:lintDebug verifyDependencyDirection` 退出码 0，证据 `artifacts/evidence/T-2026-10-08-115-entry-route-lint.txt`；Java-only 源码扫描和 53 个变体 classpath 无 Kotlin，但跳过 APK dex 后退出码 2 / PARTIAL，证据 `artifacts/evidence/T-2026-10-08-115-java-only-partial.txt`。根入口路由新增测试命令为 `:app:testDebugUnitTest --rerun-tasks --tests com.treelearn.app.ChatEntryRouteTest --no-daemon --console=plain`：最终代码 6 tests、0 failures/errors/skipped；入口覆盖变异 6 项中 1 项按预期失败。证据见本任务对应 `entry-route-*.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 移除按“最近访问分支”返回及外部深层入口直接关闭断言；变异确认 5 个层级导航测试会因只关闭而失败，根分支关闭用例通过。
- Gaps: 独立 verifier 已复核路由结构与新增单测，但当前只有路由值/分派测试，未实际驱动 `MainActivity` 页面恢复；APK dex 扫描、应用启动及 MagicOS 11 / ALP-AN00 体验均未验证。Java-only guard 明确跳过 APK dex；本轮未组装 APK、安装或启动设备应用。
- Risks: 来源页会重新加载且不保留临时视图状态，已由用户确认；`MainActivity.onBackRequested` 系统返回语义保持原样。

## Handoff notes

- Completed: 父级逐层返回逻辑、来源路由恢复逻辑、层级/路由定向单元测试及 README 同步。
- In progress: MainActivity 实际界面、APK dex、应用启动及 ALP-AN00 手测保留待验收。
- Blocker: 完整 PASS 需要 APK dex 检查与 Android 应用启动证据；本轮按已确认范围不构建 APK、不连接设备。
- Unblock condition: 后续允许构建/安装/启动时，在 ALP-AN00 验证父级逐层返回并完成 APK dex 扫描。
- Next steps: verifier 只读验收；用户后续实测 fork→父分支、兄弟→共同父级、多层逐级上行及根分支返回。
- Watch-outs: 顶部返回按 `parentBranchId`；系统返回 handler 不变；保留工作区其它未提交改动。
