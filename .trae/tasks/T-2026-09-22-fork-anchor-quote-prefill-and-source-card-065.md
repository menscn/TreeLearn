# Treelearn Harness Task: 新建分支句子级锚定、引用预填与来源卡

id: T-2026-09-22-065
title: 新建分支句子级锚定、引用预填与来源卡
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-22-chat-defects-ime-reasoning-fork-062.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 240
shared_code_touched: true
depends_on: [T-2026-09-22-fork-snapshot-into-request-history-064.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 修复对话页三缺陷；本片负责「新建分支」的交互可感知性——进入子分支能看出它从哪来，并能立即提问。
- Problem/background: 用户实测：点「新建分支」后进入一个空白新对话，交互上毫无变化感，选中的句子也没带走。代码事实：`ChatWiring.fork` 写死 `BranchAnchor.wholeAnswer()`（第 229–231 行），领域层已有的句子级锚定 `BranchAnchor.ofRange`（quote+hash 双定位，含范围校验）从未被使用；`ChatActions.onForkPressed(messageId)` 不携带选中文字；子分支首屏无任何来源信息。
- Users/scenarios: 用户长按选中一句 → 新建分支 → 进入子分支，看到引用已预填、来源卡展示来源回答与上游历史，直接补充问题发送。
- Expected outcome: ① 划词新建分支把选中句连同字符偏移存为句子级锚定；② 进入子分支时输入框上方预填该句为引用（PRD §6.2.19 引用机制复用，追问仍在本分支语义不变）；③ 对话顶部出现来源卡：来源回答摘要 + 可展开的上游历史对话（来自出生快照 sources，只读，不可追问）；④ 底部操作行「新建分支」（无选中句）维持整条回答锚定、不预填整条回答引用。
- Core flow and reversibility: 分叉仍只增节点；锚定写进既有 `BranchSource.anchor`（`branch_provenance` 既有列），无 schema 变更；来源卡为只读渲染，可整体下线而不损数据。
- Inputs/outputs: `onForkPressed`/`onForkRequested` 增加 quote（选中句与偏移）；子分支 `ChatViewState` 增加来源卡数据与预填引用。
- State and exception handling: 选中句为空（整条回答分叉）走 wholeAnswer，行为同现状；quote 与偏移不一致时 `BranchAnchor.ofRange` 域内拒绝 → STORAGE notice；上游历史渲染缺失行时少一条不渲染占位（PRD 第 471 行规则）。
- Constraints and trade-offs: 预估 240 行超 200 软上限——切分理由：预填引用、来源卡、锚定落库三者共享同一条 fork 链路与同一状态类型，拆开会造成中间态（锚定已变而 UI 未变）不可独立验收；已有 13 个历史任务同类警告先例。offsets 取自 `ChatScreen.openWordMenu` 已有的 `TextSelection`（句级起止），hash 的 digest 派生**必须复用工程内既有实现**（实现时先核对 `BranchAnchor.hash` 的既有校验方/派生方，找不到再新增并在此登记）。
- AI recommendation and rationale: 锚定落库与预填引用必须同片交付（数据与表现一致）；来源卡与 064 的快照读取路径复用同一 decode，避免第二条读路径。
- User choice and custom additions: 用户拍板「预填引用 + 顶部来源卡，还要再增加一些历史的对话信息」；并裁决口径：**父分支出生上下文属子分支自身，展示它不是混入其他分支消息**（禁的是同级/后代分支消息）——本片在 PRD §6.2.5 加口径注记固化。
- AI-autonomous decisions: 来源卡默认折叠只显来源回答摘要，展开才见上游历史（与思考区形态一致，降低首屏噪声）；来源卡不提供任何动作按钮。
- User-confirmation decisions: 见 User choice。
- Acceptance evidence: JVM 单测 RED/GREEN（fork 携带 quote → ofRange 锚定；空 quote → wholeAnswer；预填引用与来源卡状态构造）+ 全量单测 + assembleDebug；真实交互由用户真机自测。
- Facts: `TextSelection.sentence(text, offset)` 已返回句级选择；`BranchAnchor.ofRange` 校验 quote 长度必须等于偏移跨度；快照 sources 由 064 提供读回路径；`ChatViewState.withQuote` 已有引用预填机制。
- Assumptions: `answer.getOffsetForPosition` 得到的句偏移在稳定版本文本上稳定（同一显示文本）。
- Unknowns: quote hash 派生的既有实现位置。
- Risks: fork 签名变更波及 `ChatActions`/`ChatCommands`/全部测试桩；上游历史长文本的渲染性能（限制只显示快照内条目，不新增查询）。
- Deferred: 来源卡内跳转父分支定位到锚定句（PRD §6.2.5 规格第 3 条的返回定位已有既有实现，本片不动）。
- Non-goals: 不改 schema/migration；不做快照摘要压缩；不改追问（引用后发送）的既有语义。
- Skipped non-critical questions and reason: 来源卡视觉细节按既有 token 档位取值。

## Goal

「新建分支」后进入的子分支：选中句已存为句子级锚定、引用已预填、顶部来源卡可展开查看上游历史对话；数据与表现来自同一条 fork 链路。

## Scope

### In scope
- `screen/ChatScreen.java`：`openWordMenu` 的选中句与偏移随 fork 传出；来源卡渲染（含展开态）。
- `ui/ChatActions.java`、`ui/ChatCommands.java`：fork 入口携带 quote/偏移。
- `ui/ChatPresenter.java`、`ui/ChatViewState.java`：fork 提交、子分支进入时预填引用与来源卡状态。
- `wiring/ChatWiring.java`：`fork()` 接收锚定参数，quote 存在时走 `BranchAnchor.ofRange`（hash 复用既有派生），否则 wholeAnswer。
- `res/layout/`（含 sw600dp 镜像）：来源卡布局；`res/values/strings.xml`：新键（`source_*` 前缀）。
- `Docs/03-prd-draft/V3.2-...PRD.md` §6.2.5 口径注记（用户裁决原文）。
- 测试：fork 锚定构造、状态构造的 JVM 用例。

### Out of scope
- `SqliteChatStore`/DAO（064 领地）；schema/migration；思考区（066 领地）；首页与学习库文件（049 批次领地）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是 fork 锚定粒度与子分支首屏。必须保留：普通追问（引用后发送）留在当前分支的语义与 `quote-into-request`（044）行为；推荐问题分叉的直达语义（以该问题发出，不停留编辑态）；阅读位置恢复；分叉只增节点。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): fork 生产者 `ChatScreen`（划词菜单 + 底部操作行 + 推荐分叉三入口）与 `HomeScreen`（若有），消费者 `ChatPresenter`/`ChatCommands`/`ChatWiring`；布局手机与 sw600dp 双套。三入口语义差异（划词=句子锚定、底部=整条、推荐=问题直达）必须逐一有测试。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 分叉锚定可为句子级；子分支首屏含预填引用与来源卡。
- Authoritative code/config/migration evidence: 上述文件。
- Documentation surfaces checked: `V3.2 PRD` §6.2.3 规格第 4 条（「选中的文字或整条回答」——句子级锚定是它的实现纠偏）、§6.2.5。
- Documentation files updated: §6.2.5 加口径注记（不改规格条款本身）。
- Producer/consumer/sibling/compatibility paths checked: 三个 fork 入口与既有测试。
- Superseded phrases/examples reverse-searched: 「wholeAnswer」类描述如存在于 README 须核对。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: §6.2.5 注记属口径澄清，不新开 PRD 版本。

## Acceptance criteria

- [x] RED：fork 锚定与状态构造用例先失败（编译期），同一条命令转绿。
- [x] 划词分叉落库的 anchor 为 ofRange（quote+hash+偏移一致），整条分叉仍为 wholeAnswer（语句级断言或状态断言）。
- [x] 进入子分支：预填引用 = 锚定 quote；来源卡展示来源回答摘要，展开可见上游历史（数据来自 064 的快照读取，无第二条读路径）。
- [x] 推荐问题分叉与底部操作行分叉行为不回归（既有用例或新增用例证明）。
- [x] PRD §6.2.5 注记落地。
- [ ] 全量单测 + assembleDebug 绿；真实交互由用户真机自测补证。（全量单测 495 用例与 assembleDebug 均绿；真机交互项待用户自测）

## TDD execution record

### RED — failing test first
- Test/scenario: `forkWithSelectionAnchorsRange`、`forkWithoutSelectionKeepsWholeAnswer`、`childBranchPrefillsQuoteFromAnchor`、`sourceCardListsSnapshotHistory`——引用尚不存在的签名/状态 → 编译期失败。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.*"
- Exit code: 1
- Observed failure: BUILD FAILED，34 个编译错误——`fork(String,String,int,int)`（ChatCommands/ChatPresenter）、`SourceCard` 类型与 `SourceCard.Entry`、`ChatActions.onForkPressed(String,String,int,int)`、`ChatPresenter.onSourceCardToggled()`、`ChatViewState.sourceCard/sourceCardExpanded`、`ChatData.Snapshot.of` 七参签名均不存在；日志见 `artifacts/evidence/.t065-red.log`。
- Failure reason validated: 全部 34 个错误逐一指向上述尚不存在的签名/字段，属「引用尚不存在的签名 → 编译期失败」，非环境或既有用例破坏。

### GREEN — minimum implementation
- Implementation: ① `ChatCommands.fork`/`ChatActions.onForkPressed` 增 quote+偏移，划词入口（`ChatScreen.Row.onForkRequested`）以 `TextSelection` 读 `answer.getText()` 的偏移子串传出（勿 trim），底部操作行传空 quote，推荐分叉只传 question；② `ChatWiring.fork` quote 非空走 `BranchAnchor.ofRange(start, end, quote, ContextSnapshotCodec.digestOf(quote))`，ofRange 拒绝 → STORAGE，空 quote 走 wholeAnswer（现状不变）；③ `SourceCard`（app/ui 新值类型）+ `ChatData.Snapshot.sourceCard` + `ChatViewState.sourceCard/sourceCardExpanded`；`ChatWiring.read` 对非根分支经 `store.history(branchId, 0)`（064 快照读回路径，ordinal 0 截断本分支行、仅留快照前置段）构建来源卡，折叠摘要 = 锚定 quote 或来源回答截断 120 字符，`SnapshotUnreadable` 时少渲染历史区不渲染占位；④ `ChatPresenter` 进入子分支一次性预填 `branch.source.anchor.quote`（复用 `withQuote`），`onSourceCardToggled` 切换展开态；⑤ `ChatScreen.renderSourceCard` 渲染（默认折叠、头部点击展开、无动作按钮、缺失行少一条），布局 `screen_chat_body.xml`（body 为手机/平板共享，无 sw600dp 镜像需要）+ `item_source_history.xml`，strings 新键 `source_card_title/summary/expand/collapse`、`source_history_question/answer`（`source_` 前缀）。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.*"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL；ChatPresenterTest 42 用例、ChatWiringTest 28 用例全部通过，8 个新用例（上述 4 个 + `aBranchWithoutARangedAnchorPrefillsNothing`、`sourceCardArrivesWithTheReadAndExpandsOnToggle`、`forkingWithASelectionForwardsTheSentenceToTheStore`、`forkRefusesAQuoteThatDisagreesWithItsOffsets`）逐一在 test-results XML 中核对执行且 0 失败；日志见 `artifacts/evidence/.t065-green.log`。
- Regression evidence: 全量 testDebugUnitTest 51 个套件 495 用例 0 失败（`artifacts/evidence/.t065-fullregression.log`，EXITCODE=0）；既有 fork 用例（`forkOpensTheChildBranchWithoutStartingAnAttempt` 等 5 处）仅同步新签名并加强断言（底部入口 quote 为空、偏移为 0），无断言削弱。

### REFACTOR — optional
- Cleanup: 修正 `ChatViewState.with*` 对 `copy()` 的参数误扩展（copy 保持 13 参，新字段在 copy 体内透传）；补齐并行编辑中丢失的 import 与字段声明后重跑同条命令确认仍绿。
- Regression evidence: 同条目标测试命令 + 全量回归均以最终代码状态重跑，EXITCODE=0。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（tdd_mode: required）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- `ofRange` 要求 quote 长度恰好等于偏移跨度——从 `answer.getText()` 取子串，勿用 trim 后的句子文本。
- 预填引用复用 `state.withQuote`，发送后清引用的既有规则不变。
- 上游历史条目渲染顺序 = 快照 sources 顺序；用户消息显示原文、稳定版本显示回答文本。

## Verification report

- Verdict: PARTIAL（verifier 独立复核 2026-09-23：代码与 JVM 层全部通过；真机手势链路与真实 SQLite 证据待补，上限 PARTIAL。实现者自评的「代码与 JVM 层 PASS」经 verifier 独立重核确认成立）
- Evidence: RED EXITCODE=1（34 个编译错误，逐一指向新签名）；GREEN 同条命令 EXITCODE=0（8 个新用例 + 既有用例全绿）；全量 495 用例 0 失败；assembleDebug EXITCODE=0。日志：`artifacts/evidence/.t065-red.log`、`.t065-green.log`、`.t065-fullregression.log`、`.t065-assemble.log`。三入口语义：划词=ofRange（`forkWithSelectionAnchorsRange`）、底部=wholeAnswer（`forkWithoutSelectionKeepsWholeAnswer` + `forkingOpensTheBranchTheStoreCreated` 加强断言）、推荐=问题直达不携带 quote（`theForkHotZoneOnARecommendationIsIndependentOfTheFollowUp` 加强断言）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何既有用例；既有 fork 用例仅从 2 参签名迁移到 5 参签名并追加「quote 为空、偏移为 0」断言，等价行为覆盖不变且更强。
- Gaps: 真机交互证据由用户自测提供（豁免仪器测试）——长按划词 → 新建分支 → 子分支首屏预填引用与来源卡展开/收起的实机手势链路。来源卡历史区在快照引用行缺失时整段不渲染（而非逐行过滤）：064 的 `SnapshotHistoryMerger` 按设计 fail-closed 拒绝整个计划，为不建第二条解析路径，展示层复用同一读回路径并整体省略不可信历史，符合「不渲染占位」口径；快照行由 fork 原子写入且不可单独删除，实际缺行概率极低。
- Risks: hash 派生**未新增**第二处实现——复用 `:core` 既有 `ContextSnapshotCodec.digestOf`（UTF-8 SHA-256、小写十六进制，工程唯一 digest 派生方），无需登记第二实现。次要风险：生成轮询期间每次 read 都会经 `history(branchId, 0)` 重解析快照（无新增查询方法，复用 064 路径）；上游历史很长时来源卡展开态的渲染开销待真机确认。

## Handoff notes

- Completed: 句子级锚定落库（quote+hash+偏移，hash 复用 `ContextSnapshotCodec.digestOf`）、三入口 fork 语义（划词=句子锚定/底部=整条/推荐=问题直达）、子分支进入时一次性预填锚定引用（复用 `withQuote`，清除后不被回填）、顶部来源卡（默认折叠显摘要、展开见上游历史、只读无动作、缺失行不渲染占位）、PRD §6.2.5 口径注记（第 386 行）、TDD 全流程与全量回归、no-kotlin 校验与 validate_tasks 校验。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 交由 verifier 复核；真机自测通过后开启 066（思考区，共享 `ChatScreen`/`ChatPresenter`，须串行）。
- Watch-outs: 与 066 共享 `ChatScreen`/`ChatPresenter`，不得并行；本次改动 `screen_chat_body.xml` 与 `strings.xml`，066 及 049 批次的 056–058 若仍在其进行窗口需避开。
