# Treelearn Harness Task: 新建分支句子级锚定、引用预填与来源卡

id: T-2026-09-22-065
title: 新建分支句子级锚定、引用预填与来源卡
template_version: 2
task_type: implementation
status: ready
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

- [ ] RED：fork 锚定与状态构造用例先失败（编译期），同一条命令转绿。
- [ ] 划词分叉落库的 anchor 为 ofRange（quote+hash+偏移一致），整条分叉仍为 wholeAnswer（语句级断言或状态断言）。
- [ ] 进入子分支：预填引用 = 锚定 quote；来源卡展示来源回答摘要，展开可见上游历史（数据来自 064 的快照读取，无第二条读路径）。
- [ ] 推荐问题分叉与底部操作行分叉行为不回归（既有用例或新增用例证明）。
- [ ] PRD §6.2.5 注记落地。
- [ ] 全量单测 + assembleDebug 绿；真实交互由用户真机自测补证。

## TDD execution record

### RED — failing test first
- Test/scenario: `forkWithSelectionAnchorsRange`、`forkWithoutSelectionKeepsWholeAnswer`、`childBranchPrefillsQuoteFromAnchor`、`sourceCardListsSnapshotHistory`——引用尚不存在的签名/状态 → 编译期失败。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.*"
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
- Exception type: 不适用（tdd_mode: required）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- `ofRange` 要求 quote 长度恰好等于偏移跨度——从 `answer.getText()` 取子串，勿用 trim 后的句子文本。
- 预填引用复用 `state.withQuote`，发送后清引用的既有规则不变。
- 上游历史条目渲染顺序 = 快照 sources 顺序；用户消息显示原文、稳定版本显示回答文本。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: 真机交互证据由用户自测提供（豁免仪器测试）。
- Risks: hash 派生若确需新增，必须在本节登记为「第二处实现」并说明为何无法复用。

## Handoff notes

- Completed: 待实现。
- In progress: 无。
- Blocker: 064 须先 verified（快照读取路径与 MainActivity 守卫是本片前提）。
- Unblock condition: 064 verified。
- Next steps: RED → GREEN → verifier；完成后再开 066。
- Watch-outs: 与 066 共享 `ChatScreen`/`ChatPresenter`，不得并行；strings.xml 与 049 批次的 056–058 也共享，执行须避开其进行窗口。
