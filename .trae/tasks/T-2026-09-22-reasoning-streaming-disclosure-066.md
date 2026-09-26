# Treelearn Harness Task: 思考过程 DeepSeek 式披露：流式半折叠、自动折叠、次级样式

id: T-2026-09-22-066
title: 思考过程 DeepSeek 式披露：流式半折叠、自动折叠、次级样式
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-22-chat-defects-ime-reasoning-fork-062.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 修复对话页三缺陷；本片负责思考过程的展示形态，对齐用户提供的 DeepSeek 参考交互。
- Problem/background: 用户实测：思考过程与正文没有区分开。代码事实：`ReasoningDisclosure` + `view_reasoning.xml` 已有折叠组件（正文 text_sm + tl_ink2 已区分字号灰度，顶部 44dp toggle），但 ① 思考流式期间 body 不渲染（仅 expanded 时画），用户看不到思考内容，只有一行状态字；② 无「思考完成自动折叠」语义（默认从未展开）；③ 无 DeepSeek 那种从属感样式（左侧竖线 + 半折叠限高滚动区）。
- Users/scenarios: 用户开启思考模式提问：思考流式期间看到半折叠滚动区内的思考文字实时刷新，可手动收起；转入回答阶段思考区自动折叠成一行；之后随时可点开回看。
- Expected outcome: 三段式行为——forming 期默认展开（限高内滚动、流式刷新）、离开 forming 自动折叠、手动开合始终优先于默认值；样式加左侧竖线，字号灰度沿用既有 text_sm/tl_ink2。
- Core flow and reversibility: 纯表现层与状态推导，无数据变更；展开状态仍存于 `ChatViewState`（按 messageId），重建行不串状态。
- Inputs/outputs: 无新输入；输出为披露区的展开状态与渲染。
- State and exception handling: 手动开合记录为显式意图，覆盖默认值；无思考内容的答案仍无入口（既有规则不回归）。
- Constraints and trade-offs: 自动展开/折叠的判定放在 `ChatViewState`（纯 Java，可 JVM 测试），视图只画不做判定（BASE-16 屏不做决策的既有架构规则）；限高值若 `dimens.xml` 无既有档位则新增 token 并登记（该文件手工维护、无守门脚本）。
- AI recommendation and rationale: 默认展开跟随流式阶段而非持久状态，手动意图单向覆盖——这避免「流式结束把用户手动收起的状态又弹开」。
- User choice and custom additions: 用户拍板「对齐 DeepSeek（推荐）」——不做用时统计（需要额外计时持久化，明确排除）。
- AI-autonomous decisions: 限高取约屏高 40%（实现时用 dimens 既有档位或就近新增）；状态文案沿用既有 `chat_reasoning_*` 键，缺再补。
- User-confirmation decisions: 对齐 DeepSeek、不做用时统计（2026-09-22）。
- Acceptance evidence: `ChatViewState` 展开推导的 JVM 用例 RED/GREEN + 布局文件断言（限高与竖线存在，参照 `IconAssetsTest` 的文件断言模式）+ 全量单测 + assembleDebug；流式观感由用户真机自测。
- Facts: `ReasoningDisclosure.render(message, expanded, thinkingEnabled)` 的 expanded 来自 `state.isReasoningExpanded(messageId)`（默认 false）；`ChatMessage.isFormingReasoning()` 已表达「思考流式进行中」；`canExpandReasoning()` 保证无内容不显入口。
- Assumptions: 流式期间 presenter 的 read 刷新（750ms 轮询）足以驱动展开态重算。
- Unknowns: 无。
- Risks: 自动折叠若判定放在视图层会违反屏不做决策的架构规则——已用状态推导方案规避。
- Deferred: 用时统计；思考中打字机动效。
- Non-goals: 不改生成/持久化/上下文链路；不改无 Key、失败重试等既有行为；不做暗色模式。
- Skipped non-critical questions and reason: 竖线颜色用既有 `tl_line`，不新开 token。

## Goal

思考过程按 DeepSeek 三段式呈现：流式期间半折叠展开可读、完成自动折叠、手动开合优先，且与正文有明确的从属样式区分。

## Scope

### In scope
- `ui/ChatViewState.java`：展开状态推导扩展（形成中默认展开、离开自动折叠、显式意图覆盖），纯 JVM 可测。
- `ui/ChatPresenter.java`：切换动作适配新状态语义。
- `screen/ReasoningDisclosure.java` + `res/layout/view_reasoning.xml`：body 流式渲染、限高内滚动（ScrollView 包裹 + maxHeight 语义）、左侧竖线样式。
- `ui/ChatScreen.java`：Row 渲染调用点适配。
- `res/values/dimens.xml` / `strings.xml`：按需新增 token/键并登记。
- 测试：状态推导 JVM 用例 + 布局文件断言用例。

### Out of scope
- 065 领地（fork/来源卡）；`MarkdownRenderer`；生成链路；sw600dp 若 view_reasoning 不分屏则不涉及（核对后记录）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是披露区默认展开时机与样式。必须保留：无思考内容无入口；折叠状态下正文可独立阅读（PRD §6.2.19 F18 验收口径）；手动展开后流式刷新不重置用户意图；重建行不串消息的展开状态。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 状态生产者 `ChatPresenter`，消费者 `ChatScreen.Row`；`ReasoningDisclosure` 另有单参 render 重载（既有调用方核对，避免漏改）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 失败/重试路径的披露行为不在本片语义内，行为不变。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 思考披露默认展开时机。
- Authoritative code/config/migration evidence: 上述文件。
- Documentation surfaces checked: `V3.2 PRD` §6.2.19 F18 规格第 6 条「默认折叠」——**本片把「思考进行中」定义为折叠语义的例外阶段**，PRD 措辞须补一句注记（与 065 的 §6.2.5 注记同类，属口径澄清）。
- Documentation files updated: §6.2.19 F18 规格第 6 条后新增口径注记（V3.2 PRD 第 780 行，格式沿用第 386 行既有注记）。
- Producer/consumer/sibling/compatibility paths checked: 既有 ReasoningDisclosureTest（如存在）同步。——核对结果：无独立 ReasoningDisclosureTest（该视图无 JVM 测试），由新增 ReasoningDisclosureLayoutTest 承担布局断言。
- Superseded phrases/examples reverse-searched: 「默认折叠」措辞在 Docs/ 与 README 的其他出现处核对。——反向检索结果（只记录，不扩大改动）：`Docs/01-demand/…V3.1增量.md:33,57`（BASE-23 冻结基准）、`Docs/03-prd-draft/V3.1-*.md:229,768,979,1014`（历史版本冻结）、`Docs/04-ux-design/…原型-v1.html:2413`（历史原型）、`Docs/05-technical-design/…技术架构方案_V1.0.md:292` 与 `…交付提示词.md:39`、`README.md:37`。均为稳态默认折叠的描述或冻结基准；流式期间例外属展示时机口径，不与其持久化/会话语义冲突，无需改动。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 注记就地加，不新开版本。

## Acceptance criteria

- [x] RED：状态推导用例先失败（forming 默认展开 / 手动收起后流式不弹回 / 离开 forming 自动折叠 / 手动展开后保持），同一条命令转绿。
- [x] 布局断言：body 有界高滚动容器与左侧竖线；展开正文仍为 text_sm + tl_ink2（文件断言证据）。
- [x] 无思考内容的答案无入口（既有用例或新增用例证明不回归）。
- [x] 全量单测 + assembleDebug 绿；流式观感由用户真机自测补证。
- [x] PRD §6.2.19 F18 注记落地。

## TDD execution record

### RED — failing test first
- Test/scenario: `formingReasoningIsExpandedByDefault`、`manualCollapseSurvivesStreamingRefresh`、`leavingFormingAutoCollapses`、`manualExpandSurvivesCompletion`、`answerWithoutReasoningHasNoEntry`（ChatViewStateTest）；`togglingAFormingAnswerFoldsItAgainstTheStreamingDefault`（ChatPresenterTest）；`theBodySitsInALimitedScrollingContainer`、`theSubordinateStyleSurvives`（新增 ReasoningDisclosureLayoutTest，参照 IconAssetsTest 文件断言模式）。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.*"
- Exit code: 1
- Evidence log: artifacts/evidence/.t066-red.log（含 EXITCODE=1）
- Observed failure: 11 个编译错误，全部为「找不到符号: 方法 isReasoningEffectivelyExpanded(String)」——新用例引用尚不存在的方法，编译期失败。
- Failure reason validated: 是。失败原因正是待实现的状态推导 API 缺失，非环境或无关错误；布局断言用例同批编译，待 GREEN 一并验证。

### GREEN — minimum implementation
- Implementation: ChatViewState 增 `collapsedReasoning` 显式收起意图集与 `isReasoningEffectivelyExpanded`（显式收起 > 显式展开 > `ChatMessage.isFormingReasoning()` 默认）；`withReasoningExpanded` 记录显式意图并互斥清除反向意图；ChatPresenter.onReasoningToggled 按有效态取反；ChatScreen.Row.render 改读有效态；ReasoningDisclosure 增 ScrollView 容器限高钳制与左侧 2dp 竖线布局；dimens 新增 `reasoning_rule`/`reasoning_max_height`。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.*"
- Exit code: 0
- Evidence log: artifacts/evidence/.t066-green.log（含 EXITCODE=0）
- Passing evidence: BUILD SUCCESSFUL；测试报告核对 5 个状态用例 + presenter 用例 + 2 个布局用例均执行通过。
- Regression evidence: 既有 `reasoningStartsCollapsedAndIsKeyedByMessageNotByPosition`、`reasoningTogglesPerMessageAndIgnoresWhatItCannotExpand`、`replacingTheMessagesKeepsWhatTheLearnerIsDoing` 等原样通过（显式意图语义兼容旧断言）。

### REFACTOR — optional
- Cleanup: 移除 `ReasoningDisclosure.render(message, expanded)` 两参重载（全库检索确认无调用方，唯一内部委托随 GREEN 一并消除）；类注释同步 T-066 语义。
- Regression evidence: 全量 testDebugUnitTest EXITCODE=0（.t066-fullregression.log）+ assembleDebug EXITCODE=0（.t066-assemble.log）。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（tdd_mode: required）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 展开推导规则：`effective = 显式意图 ?? isFormingReasoning()`；显式意图在消息处于 forming 期间记录「收起」、离开 forming 后记录「展开」，二者都优先于默认。
- 限高容器用 ScrollView 包 body（`android:maxHeight` 对 TextView 不构成滚动语义），高度值走 dimens token。
- 布局文件断言模式参照 `app/src/test/java/com/treelearn/app/IconAssetsTest.java`。
- 实现说明（T-066 落地）：
  - `ChatViewState` 新增字段 `collapsedReasoning`（显式收起意图集）与查询 `isReasoningEffectivelyExpanded(messageId)`：显式收起 > 显式展开 > 该消息 `isFormingReasoning()` 默认；`isReasoningExpanded` 保留为显式展开集查询（既有测试语义不变）。
  - `ReasoningDisclosure`：body 包进 `ScrollView`（id `reasoning_body_scroll`），可见性随展开态切换；`clampBodyHeight()` 在布局后测量——内容矮于 token 时 `WRAP_CONTENT`（流式初期不长出空盒）、超过时钳到 `reasoning_max_height` 内滚动。左侧竖线 View（id `reasoning_rule`，宽 `@dimen/reasoning_rule`=2dp，色 `@color/tl_line`），toggle+body 缩进 `space_s2`。
  - dimens 新 token 登记（dimens.xml 手工维护，无守门脚本）：`reasoning_rule`=2dp；`reasoning_max_height`=320dp（约屏高 40% 量级）。
  - 状态文案沿用既有 `chat_reasoning_*` 四键 + `chat_reasoning_pending`，strings.xml 零新增（未触碰 065 的 `source_*` 键）。
  - `ReasoningDisclosure.render(message, expanded)` 两参重载：全库检索（主源/测试/androidTest）确认无调用方，已删除；三参版仅 ChatScreen.Row 调用。
  - sw600dp 核对：`layout-sw600dp/` 仅 screen 级布局，无 `view_reasoning` 镜像，本片不涉及（与 Out of scope 预判一致）。
  - 065 领地（fork/来源卡、`SourceCard` 相关状态与方法）零改动。

## Verification report

- Verdict: **PASS**（2026-09-24 复核 #3 收口：代码与 JVM 层此前已由 verifier 独立重跑确认（全量单测与 assembleDebug 均 EXITCODE=0）；唯一悬项「流式真机观感」由用户 2026-09-24 确认自测通过，与 063/065 同批）
- Evidence: verifier 核对——`ChatViewState` 四条展开推导语义用例断言真实（含流式刷新不弹回、完成后保持）；`view_reasoning.xml`:68 ScrollView 限高、:27–30 `tl_line` 左侧竖线、正文 text_sm+tl_ink2 未变；`dimens.xml`:40–41 登记 `reasoning_rule`=2dp、`reasoning_max_height`=320dp；PRD §6.2.19 F18 注记（第 780 行）落地且未改条款。证据 `.t066-red(1)/green(0)/fullregression(0)/assemble(0).log` 全部含 EXITCODE=。verifier 独立重跑全量单测与 assembleDebug 均 EXITCODE=0；2026-09-24 全量 check 复跑 EXITCODE=0（`P3-B4-gates-2026-09-24.txt`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除既有用例；`ReasoningDisclosure` 两参重载经全库核对确认无调用方后删除，等价行为覆盖不变（verifier 核对）。
- Gaps: 已消除——流式观感由用户真机自测提供并确认通过（2026-09-24）。
- Risks: 轮询刷新（750ms）下展开态重算的渲染成本——行重建是既有的记录内性能风险，本片不扩大它。

## Handoff notes

- Completed: 思考披露三段式已实现并验证（2026-09-22 落地；本行原为「待实现」，与 TDD 记录/verifier 复核矛盾，2026-09-24 更正）：`ChatViewState.isReasoningEffectivelyExpanded` 状态推导（显式意图 > forming 默认）、`ReasoningDisclosure` 限高滚动 + 左侧竖线、两参重载删除、dimens 双 token 登记、PRD F18 注记。verifier 独立重跑全量单测与 assembleDebug 均 EXITCODE=0。余项仅流式真机观感（用户自测）。
- In progress: 无。
- Blocker: 无技术阻塞；执行排在 065 之后（共享 ChatScreen/ChatPresenter 的写冲突）。
- Unblock condition: 065 verified。
- Next steps: RED → GREEN → verifier。
- Watch-outs: `ReasoningDisclosure.render` 单参重载的既有调用方（测试/其他屏）逐一核对；strings.xml 写冲突同 065。
