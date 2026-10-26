# Filename: T-2026-10-25-library-one-sided-segment-102.md
# Codex Harness Task: 学习库只剩一侧有内容时撤掉双段胶囊，改一行点名；字阶抬到 13/14/12.5/12（AL1）

id: T-2026-10-25-102
title: 学习库单边分段与紧凑字阶（AL1）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 AL1（重要）——「已完成 0」也占一整段 44dp 分段位；同批把学习库的字阶从 9–12.5sp 抬到 12–14sp。
- Problem/background: 双段胶囊是「两个列表之间切换」的控件；当一侧为 0 行时，它提供一个切到空页的入口，还白占首行上方 44dp。学习库的正文与 meta 字号也低于 12px 地板（9sp／10.5sp），在手机上读起来像注释。
- Users/scenarios: 打开学习库的人；刚删掉最后一个「进行中」主题、只剩已完成的人。
- Expected outcome: 两侧都有行时才画胶囊；只有一侧有行时改画一行「进行中 N 个主题 / 已完成 N 个分支」，13sp/600 名称 + 12sp/400 灰数字，且不可点；字阶统一为标题 14sp／摘要 12.5sp／meta 12sp／分段 13sp。
- Core flow and reversibility: 改 `LibraryScreen.java`、`screen_library_body.xml`、`item_library_archived.xml`、`strings.xml`、`dimens.xml`。回退＝始终画胶囊并恢复旧字阶。
- Inputs/outputs: 输入是评审板 AL1 与 089 测得的 12px 地板；输出是 `library_segment_summary` 行、`shownSegment()` 的单一真相与六个字号 token。
- State and exception handling: LOADING／ERROR／两侧都空时不画那一行（否则会写「0 个主题」或凭空给数）；删除导致当前分段为空时，列表跟随唯一有内容的一侧（`shownSegment`）。
- Constraints and trade-offs: 单边时少了一次切换手势；但在没有另一侧可切的情况下，那个手势本来无处可去。
- AI recommendation and rationale: 把「现在看的是哪一段」从控件降级为文字，是让 44dp 回到内容而不是空转；跟随唯一有内容的一侧则避免「标签写进行中、页面写已完成里还没有」的自相矛盾。
- User-confirmation decisions: 22 条全做。
- AI-autonomous decisions: ① 单边行是 label 而非 control，不给 44dp 焦点与点击；② 数字用 `RelativeSizeSpan` + `StyleSpan(NORMAL)` + ink3，与双段胶囊的计数同一套写法；③ 已归档卡的恢复／删除按钮保留 44dp，但去掉装饰性底条；④ 字阶一次改齐六个 token，不逐页调。
- Acceptance evidence: `SchemeALibraryNavigationLayoutTest` 三条（胶囊真计数、单边摘要行、卡片紧凑字阶），先红后绿。
- Facts: `LibraryScreen.java:117`（`shownSegment` 参与分段判定）、`:266`（`shownSegment`）、`:302`（胶囊显隐）、`:306`（摘要显隐）、`:316`（`soleSegment` 样式）；`screen_library_body.xml:70-86`（`library_segment_summary`）；`strings.xml:553-554`；`dimens.xml:145-150`、`:227-228`；测试 `SchemeALibraryNavigationLayoutTest.java:189`、`:274`、`:318`。
- Assumptions: `LibraryViewState` 的 `rows`／`archived` 是真实行集合，可据此判断「是否只有一侧有内容」。
- Unknowns: 无。
- Risks: 单边时用户失去「切到空侧」的手势；这是有意的——空侧没有可看的内容。
- Deferred: 搜索态的胶囊显隐联动不做新逻辑：搜索态只把摘要行隐藏，胶囊保持上一次库渲染的状态（既有行为）。
- Non-goals: 不改搜索结果、归档动作与底部标签栏。
- Skipped non-critical questions and reason: 未问「单边行要不要可点回到另一段」——另一段为空，点进去只会得到空态。

## Goal

让学习库在只有一侧有内容时用一行文字代替 44dp 的空转分段，并把全库字号抬到 12px 以上。

## Scope

### In scope
- `android/app/src/main/java/com/treelearn/app/screen/LibraryScreen.java`
- `android/app/src/main/res/layout/screen_library_body.xml`、`item_library_archived.xml`
- `android/app/src/main/res/values/strings.xml`（`library_unit_topics`／`library_unit_branches`）
- `android/app/src/main/res/values/dimens.xml`（六个字阶 + 两个间距）
- `SchemeALibraryNavigationLayoutTest` 三条；两个仪器测试的编译面同步

### Out of scope
- 搜索结果与高亮；归档动作；学习库以外的页面字阶。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 双段胶囊只在两侧都有行时出现；单边改为不可点的摘要行；字阶抬升。必须保留：真实计数（`next.rows.size()`／`next.archived.size()`）、选中面的玻璃高光、44dp 触控地板、搜索态下胶囊作为退出搜索的入口、归档卡的恢复／删除动作与倒计时。
- Producer/consumer/sibling entrypoint and platform branches (code evidence): 生产者 `LibraryScreen.render`／`drawSegments`（手机与 Pad 共用 `screen_library_body.xml`）；消费者 `scheme_a` 的选中状态与仪器测试；无原生桥接。
- Native bridge or independent-client compatibility/update requirements: 无；仪器测试只更新期望的布局 id，不改变行为断言。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 学习库的单边状态不再画双段胶囊；全库最小字号从 9sp 抬到 12sp；新增一条只读摘要行。
- Authoritative code/config/migration evidence: `LibraryScreen.java:266-330`；`screen_library_body.xml:70-86`；`dimens.xml:145-150`。
- Documentation surfaces checked: `.codex/rules/android.md`、`.codex/rules/ux-design.md`（字阶 token 与设计系统同批）。
- Documentation files updated: 无需改文档；`screen_library_body.xml` 头注释记录了单边规则。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "library_segment_summary" android/app/src` 命中布局、屏幕、测试；`library_segment` 的 id 未变，仪器测试的查找不受影响。
- Superseded phrases/examples reverse-searched: 旧注释「两个分段常驻」已改为「两侧都有行才画」。
- `documentation impact: none` reason and evidence: 页面内状态渲染与字号，无契约变化。
- Historical/PRD handling: 不改写 057 的分段胶囊历史；本片是在其上加一条单边规则。

## Acceptance criteria

- [x] 两侧都有行：胶囊可见，两段计数是真实行数，选中面是 4dp 内缩的玻璃高光。
- [x] 只有一侧有行：胶囊隐藏，出现 13sp/600 + 12sp 灰的摘要行，不可点、不可聚焦。
- [x] LOADING／ERROR／两侧都空：摘要行隐藏，不出现「0 个主题」。
- [x] 标题 14sp／摘要 12.5sp／meta 12sp／分段 13sp／计数 12sp／note 12sp。

## TDD execution record

### RED — failing test first
- Test/scenario: 三条——胶囊两段各占一半且计数真实、单边时胶囊让位给摘要行、卡片字阶为 14/12.5/12。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.SchemeALibraryNavigationLayoutTest"`
- Exit code: 1
- Observed failure: `13 tests completed, 3 failed`——`SchemeALibraryNavigationLayoutTest.java:237`（旧字阶与计数写法）、`:367`（卡片仍是 12.5/10.5/9）、`:66`（单边摘要行不存在）（`102-red.txt`）。
- Failure reason validated: 把 `LibraryScreen.java`、`screen_library_body.xml`、`dimens.xml` 还原到 HEAD 后同一条命令判红；恢复后转绿，三条红分别落在「胶囊计数」「单边规则」「卡片字阶」，没有牵连搜索与归档判据。

### GREEN — minimum implementation
- Implementation: 新增 `library_segment_summary` 与 `shownSegment`／`soleSegment`；`drawSegments` 按两侧是否有行决定胶囊与摘要；六个字阶 token 抬升；归档卡去装饰底条。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.SchemeALibraryNavigationLayoutTest"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`、`EXITCODE=0`（`102-green.txt`）。
- Regression evidence: Android 全量 109 套 / 954 用例绿；同类其余十条（搜索、归档、面包屑）保持绿；仪器测试源编译通过（本轮不运行）。

### REFACTOR — optional
- Cleanup: 把「当前有效的段」收成 `shownSegment()` 一处判断，列表渲染与分段绘制共用，避免两处各自推断。
- Regression evidence: 同一条命令保持绿。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 三条各红一次。
- Stop condition hit: 13 条全绿。
- Handoff: 105 的整跑包含本片。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 单边摘要是「状态标签」而不是「切换器」：它只回答现在在看什么，不提供动作，因此没有 44dp 也没有焦点。
- `shownSegment()` 是删除路径的必要修正：删掉最后一个进行中主题后，presenter 仍记得「进行中」，列表却只能显示已完成；跟随唯一有内容的一侧比尊重一个看不见的开关更接近事实。
- 字阶一次改齐：只抬分段不抬卡片会让层级倒置；六个 token 都在 `dimens.xml`，可整批复核。

## Verification report

- Verdict: PASS
- Evidence: `102-red.txt`（3 failed / 13 / EXITCODE=1）、`102-green.txt`（BUILD SUCCESSFUL / EXITCODE=0）；Android 全量绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 旧「双段常驻」断言由「两侧都有行才画」的判据替代，并保留真实计数与玻璃高光的断言。
- Gaps: 未做真机删除最后一个进行中主题的点击回放；真机核验只允许 `adb install -r` + 截图。
- Risks: 单边摘要行与搜索态同时出现会让首行信息叠加；搜索态已显式隐藏该行。

## Handoff notes

- Completed: 单边摘要、`shownSegment` 跟随、六个字阶、归档卡收边。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无。
- Watch-outs: 不要把摘要行做成按钮——它没有可去的地方。
