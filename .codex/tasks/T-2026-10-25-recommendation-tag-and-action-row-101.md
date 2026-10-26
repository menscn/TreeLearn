# Filename: T-2026-10-25-recommendation-tag-and-action-row-101.md
# Codex Harness Task: 推荐卡标签按位交替、标签移到右上角；动作行 29dp 玻璃面 / 18dp 字形且不再重复时间；搜索提示说能搜到什么（AH3 + AL2 + 动作行精修）

id: T-2026-10-25-101
title: 推荐卡标签与动作行精修、搜索提示改人话（AH3 + AL2）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 230
shared_code_touched: true
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 AH3（优化）——四张推荐卡挂同一个「顺着你学的」；AL2（优化）——搜索 placeholder 写的是字段清单。用户另有一条口头精修：「这个以及右边两个按钮，都不好看，傻大傻大的，不够精致」——本片把动作行一并收小。
- Problem/background: ① `HomeWiring` 对每条历史推荐都构造 `FOLLOW_UP`，于是四张卡都挂同一个标签，把一个「区分方向」的标签变成了装饰；② 搜索框提示写「标题、问题、回答、锚定原文…」，描述的是索引结构，不是用户能搜到什么；③ 回答动作行四个按钮是 44dp 满底圆，字形靠 compound drawable 顶到起始边，整行读成一条厚重的带。
- Users/scenarios: 在首页挑推荐问题的人；在学习库搜索的人；读回答、想复制／重答／开分支的人。
- Expected outcome: ① 派生推荐按位置在 `FOLLOW_UP` 与 `NEW_DIRECTION` 之间交替，入门起点保持统一 `STARTER`；② 提示改为「搜索主题、回答或原文…」；③ 动作行每格 44dp 命中区、内嵌 29dp 玻璃面、18dp 居中字形、整行左对齐，行内不再画时间（时间由 096 的阅读头按分支说一次）。
- Core flow and reversibility: 改 `HomeWiring.java`、`RecommendationTag.java`、`item_recommendation.xml`、`view_answer_actions.xml`、`AnswerActionBar.java`、`strings.xml`、Web 文案与两处测试。回退＝恢复单一 `FOLLOW_UP`、旧提示与 `Button` + compound drawable。
- Inputs/outputs: 输入是评审板 AH3／AL2、用户口头精修与 PRD 6.2.17 point 3；输出是按位交替的标签、右上角标签位、29dp 玻璃面与 18dp 字形、新提示文案。
- State and exception handling: 无历史时四张卡全部 `STARTER`——空态不假装有偏好；`NEW_DIRECTION` 标签在数据里没有对应该方向的内容，因此只做「不重复同一句」的交替，不新增数据字段。
- Constraints and trade-offs: 29dp 可见面小于 44dp 命中区，视觉变轻但触控地板不变；标签做成右上角非交互 pill，因此不占 44dp。
- AI recommendation and rationale: 标签按位置交替是「在不发明数据的前提下让四种卡不再同声」的最小改动；动作行缩面不缩靶，是用户「傻大」诉求与可点性之间的直接解。
- User-confirmation decisions: 22 条全做；动作行按钮「不够精致」按 29dp 面 / 18dp 字形收小。
- AI-autonomous decisions: ① 标签移到卡片右上角，火花留在左上；② 卡片最小高度 88dp，保证一行卡片等高；③ 摘要收 2 行（AL3 的落点）；④ 动作行的四条 lead 左对齐而不是均分整行；⑤ 动作行去掉时间，时间只由阅读头说一次。
- Acceptance evidence: Android 四条 + Web 一条——标签两色、按位交替、动作行几何、行内无时间、搜索提示；先红后绿。
- Facts: `HomeWiring.java:138`／`:148`（`derivedKind`）；`RecommendationTag.java:71`（中性标签 ink2）；`item_recommendation.xml`（标签右上、`rec_card_min_height 88dp`、`rec_text maxLines=2`）；`view_answer_actions.xml:32`（`gravity="start|center_vertical"`）、`:41-51`（44dp + 18dp 居中）、`:121`（`action_glass_inset 7.5dp` → 29dp 面）；`AnswerActionBar.java`（删除 `renderTime`）；`strings.xml:546`（新提示）；测试 `RecommendationTagTest.java:87`／`:105`、`SchemeAChatLayoutTest.java:246`、`SchemeALibraryNavigationLayoutTest.java:262`、`ChatMessageRowLayoutTest.java:111`／`:180`、`LibraryPage.test.tsx`。
- Assumptions: PRD 6.2.17 point 3 只允许入门起点与两种非起点方向；产物不提供方向字段，因此交替是按位置推导。
- Unknowns: 无。
- Risks: 交替让相邻两张卡标签不同，若数据顺序变化会跟着变；这是「诚实于可用信息」的代价。
- Deferred: 真正按用户历史方向生成标签需要推荐产物带方向字段，非本线范围。
- Non-goals: 不改推荐内容、点击行为、动作行能力（复制／重答／开分支／子分支）；不改分享（本构建没有）。
- Skipped non-critical questions and reason: 未问「标签要不要可点筛掉同类」——那是新增筛选能力，超出 22 条范围。

## Goal

让推荐卡不再四张同声、标签成为卡头的一部分，并让回答动作行在 44dp 命中区内保持 29dp 面与 18dp 字形的精致比例；搜索提示改写成用户能搜到什么。

## Scope

### In scope
- `android/app/src/main/java/com/treelearn/app/wiring/HomeWiring.java`
- `android/app/src/main/java/com/treelearn/app/screen/RecommendationTag.java`
- `android/app/src/main/res/layout/item_recommendation.xml`
- `android/app/src/main/res/layout/view_answer_actions.xml`、`AnswerActionBar.java`
- `android/app/src/main/res/values/strings.xml` 的 `library_search_hint`
- `frontend/src/copy/zh-CN.ts` 的 `library_search_placeholder` 与 Web 判据
- 四个 Android 测试类与 `frontend/test/pages/LibraryPage.test.tsx`

### Out of scope
- 推荐内容与数据产物；动作行能力；分享；学习库的其它版式（102）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 派生推荐标签由固定 `FOLLOW_UP` 改为按位交替；中性 pill 文字 ink3 → ink2；动作行由 `Button` + compound drawable 改为 44dp `FrameLayout` + 居中 18dp 图标，删除行内时间。必须保留：四格 lead 的动作与可访问名、`duplicateParentState` 的禁用联动、子分支计数徽标、44dp 触控地板、复制／重答／开分支的既有回调。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `HomeWiring.java:134-150`、`RecommendationTag.java:68-71`、两个 layout；消费者 `RecommendationMarquee`（读标签）、`AnswerActionBar`（四格绑定）、`ChatScreen`（不再调用 `renderTime`）。Web 与 Android 的搜索提示是两份独立文案，同批改。
- Native bridge or independent-client compatibility/update requirements: 无；动作能力未变，只是画法变化。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 推荐标签不再四张同声，且位于卡片右上；动作行不再画时间；搜索提示从字段清单改为用户结果。
- Authoritative code/config/migration evidence: `HomeWiring.java:138`；`RecommendationTag.java:71`；`view_answer_actions.xml:12-32`；`dimens.xml:121`；`strings.xml:546`；`zh-CN.ts` 的 `library_search_placeholder`。
- Documentation surfaces checked: `.codex/rules/android.md`、`.codex/rules/frontend.md`（文案）、`.codex/rules/ux-design.md`。
- Documentation files updated: 无需改文档；标签交替的理由写在 `HomeWiring` 的 javadoc 与测试注释里。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "renderTime" android/app/src` 无命中；`grep -rn "act_time" android/app/src/main` 无命中；搜索提示在两端的键都被新判据钉住。
- Superseded phrases/examples reverse-searched: 「标题、问题、回答、锚定原文…」与「搜索标题、摘要或来源问题…」已从两端文案移除。
- `documentation impact: none` reason and evidence: 文案与版式改动，无契约或数据结构变化。
- Historical/PRD handling: 不改写 2026-09-28 动作行只留四条 lead 的拍板；本片只改几何与时间的归属。

## Acceptance criteria

- [x] 派生推荐的标签按位置交替两种非起点方向；入门起点四张统一。
- [x] 推荐卡标签位于右上角，火花在左；卡片最小高度 88dp，问题最多两行。
- [x] 动作行每格 44dp 命中区、29dp 玻璃面、18dp 居中字形、左对齐；行内无 `act_time`。
- [x] 搜索提示说明能搜到什么（主题／回答／原文），不再枚举索引字段。
- [x] Web 与 Android 的搜索提示同义；两端判据都绿。

## TDD execution record

### RED — failing test first
- Test/scenario: 六条——中性标签两色、派生标签按位交替、动作行 29dp 面／18dp 字形／左对齐、动作按钮无内边距、动作行无时间、搜索提示说人话。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.RecommendationTagTest.theTwoTintsAreThePrototypesRatherThanAnEvenSplit" --tests "com.treelearn.app.screen.RecommendationTagTest.theDerivedStripAlternatesTheTwoKindsInsteadOfRepeatingOne" --tests "com.treelearn.app.screen.SchemeAChatLayoutTest.theActionRowDrawsTheSchemeAGlassDotsInsideTheTouchTarget" --tests "com.treelearn.app.screen.SchemeALibraryNavigationLayoutTest.theSearchHintNamesWhatCanBeFoundRatherThanTheIndexedFields" --tests "com.treelearn.app.ui.ChatMessageRowLayoutTest.theActionButtonsCarryNoInnerPadding" --tests "com.treelearn.app.ui.ChatMessageRowLayoutTest.theActionBarLeadsWithThePrototypeSetAndCarriesNoTime"`
- Exit code: 1
- Observed failure: `6 tests completed, 6 failed`——`RecommendationTagTest.java:87`（ink3 而非 ink2）、`:105`（wiring 仍固定 `FOLLOW_UP`）、`SchemeAChatLayoutTest.java:255`（compound drawable）、`SchemeALibraryNavigationLayoutTest.java:262`（旧字段清单）、`ChatMessageRowLayoutTest.java:180`（按钮内边距）、`:111`（行内仍有时间）（`101-red-focused.txt`；同片早前的分轮红读数为 `40 tests completed, 6 failed`，见 `101-red.txt`，六条失败与此处逐条相同）。
- Failure reason validated: 把 `RecommendationTag.java`、`HomeWiring.java`、`AnswerActionBar.java`、`view_answer_actions.xml` 还原到 HEAD 并只把提示行改回字段清单后同一条命令判红；逐个恢复即逐条转绿，六条红与六处改动一一对应。

### GREEN — minimum implementation
- Implementation: 标签按位交替并改 ink2；卡片标签右上、88dp 下限、摘要两行；动作行改 44dp 容器 + 18dp 图标 + 29dp 玻璃面、左对齐、删时间；两端搜索提示改人话。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.RecommendationTagTest.theTwoTintsAreThePrototypesRatherThanAnEvenSplit" --tests "com.treelearn.app.screen.RecommendationTagTest.theDerivedStripAlternatesTheTwoKindsInsteadOfRepeatingOne" --tests "com.treelearn.app.screen.SchemeAChatLayoutTest.theActionRowDrawsTheSchemeAGlassDotsInsideTheTouchTarget" --tests "com.treelearn.app.screen.SchemeALibraryNavigationLayoutTest.theSearchHintNamesWhatCanBeFoundRatherThanTheIndexedFields" --tests "com.treelearn.app.ui.ChatMessageRowLayoutTest.theActionButtonsCarryNoInnerPadding" --tests "com.treelearn.app.ui.ChatMessageRowLayoutTest.theActionBarLeadsWithThePrototypeSetAndCarriesNoTime"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`、`EXITCODE=0`（`101-green-focused.txt`）；Web 侧 `cd frontend && npx vitest run test/pages/LibraryPage.test.tsx` → `Tests 8 passed (8)`（`101-web-green.txt`）。
- Regression evidence: Android 全量 109 套 / 954 用例绿；前端全量 30 文件 / 210 用例绿；`RecommendationTagTest` 其余四条、`SchemeAChatLayoutTest` 其余八条、`ChatMessageRowLayoutTest` 其余十六条保持绿。

### REFACTOR — optional
- Cleanup: 动作行删除 `renderTime` 与 `act_time`，`AnswerActionBar` 的字段类型从 `Button` 收为 `View`；`RecommendationTag` 的颜色注释补充 12sp 下的对比理由。
- Regression evidence: 重跑六条与全量套件，读数不变。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿；Web 文案另跑一次 8 条。
- Failure identity and repeat count: 六条各红一次；无回潮。
- Stop condition hit: 六条 + Web 8 条同时绿。
- Handoff: 105 的整跑与重打包包含本片。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 标签交替是「不发明数据」的做法：推荐产物只给问题文本，不给方向；按位置交替至少让四张卡不再同声，且顺序稳定（值比较 + 跑马灯跳过等值重建）。
- 动作行「缩面不缩靶」：29dp 面由 `action_glass_inset 7.5dp` 从 44dp 内缩得到，命中区仍是 44dp；18dp 字形居中，不再被 compound drawable 顶到起始边。
- 时间从动作行撤走不是删信息：096 的阅读头按分支说一次相对时间，回答行只留动作。
- 搜索提示在两个端各有一份文案，本片同批改并用两条判据钉住同义，避免一端改了另一端停旧。

## Verification report

- Verdict: PASS
- Evidence: `101-red-focused.txt`（6 failed / EXITCODE=1）、`101-green-focused.txt`（BUILD SUCCESSFUL / EXITCODE=0）、`101-red.txt`（早前四类整跑 6 failed / 40）、`101-web-green.txt`（8 passed）；两端全量套件绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除「动作行画时间」的旧断言；等价覆盖是 `theActionBarLeadsWithThePrototypeSetAndCarriesNoTime` 与阅读头的时间判据。
- Gaps: 未做真机截图核验 29dp 面的实际观感（待 105）。
- Risks: 交替标签若被理解成「真实偏好」，会高估数据；文案上没有声称个性化，标签本身来自 PRD 的两种方向。

## Handoff notes

- Completed: 标签交替与右上角、卡片等高、动作行精修、两端搜索提示。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无。
- Watch-outs: 不要把时间加回动作行；也不要恢复 compound drawable，那正是「傻大」的成因。
