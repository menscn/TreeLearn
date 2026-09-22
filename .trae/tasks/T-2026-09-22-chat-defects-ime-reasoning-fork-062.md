# Treelearn Harness Task: 对话页实测缺陷修复：键盘、思考区、分支上下文（父需求）

id: T-2026-09-22-062
title: 对话页实测缺陷修复：键盘、思考区、分支上下文（父需求）
template_version: 2
task_type: requirement
status: verifying
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户 2026-09-22 在真机（AWSS013807000356，已装 052 成果的 APK）实测对话页发现三个缺陷：① 点输入框唤起输入法后输入框不被顶起，看不到正在输入的文字；② 思考过程与正文无区分，没有 DeepSeek 那种「思考中半折叠流式展示 → 完成后自动折叠 → 可手动展开」的形态；③ 长按输出文字选「新建分支」后跳进一个空白新对话，来源不可感知，且发出的问题不携带任何上游上下文，AI 无从回答。
- Users/scenarios: 唯一用户是产品经理本人，在 Android 手机上通过对话与分支探索学习。
- Expected outcome: ① 键盘拉起时输入框始终可见；② 思考过程按 DeepSeek 式三段交互呈现（流式半折叠、自动折叠、手动开合、次级灰小字号）；③ 新建分支后进入的子分支携带创建快照上下文发给 AI，界面预填引用并展示来源卡（含可展开的上游历史对话）。
- Core flow and reversibility: 对话流不变；分叉流为「长按选句 → 新建分支 → 进入子分支（预填引用 + 来源卡）→ 首问携带出生快照」。分叉仍只增节点、可从画布/导航回溯，无不可逆动作。
- Inputs/outputs: 输入为草稿文本、思考开关、长按选中句、新建分支指令；输出为可见输入区、思考披露区、携带快照上下文的请求与来源卡。
- State and exception handling: 无 Key 时新建分支按 PRD §6.2.3 异常 2 拦截（现状缺失，补齐）；快照解码失败按 STORAGE 拒绝发问，不静默丢弃上游背景（PRD §6.2.4 异常 2）。
- Constraints and trade-offs: Java-only、零 AndroidX、不新增依赖；不改 schema/migration/写路径；单任务生产代码行目标 ≤200（口径：非空非注释行）；验证层级经用户指示为**代码测试，不跑真机或模拟器**——由此产生的验证缺口必须如实记录，不得报 PASS。
- AI recommendation and rationale: 拆成四个串行小片（配置 / 数据链路 / 分支交互 / 思考区），理由：063 无法单测须独立成片记录例外；064 是数据正确性核心；065 与 066 都改 ChatScreen/ChatPresenter，必须串行。
- User choice and custom additions: 用户 2026-09-22 逐项拍板——(1) 流程走 Harness；(2) 思考区「对齐 DeepSeek」，不做用时统计；(3) 分支交互「预填引用 + 顶部来源卡，还要再增加一些历史的对话信息」；(4) 口径裁决：子分支展示**来源分支的上游历史属其出生上下文，不是混入其他分支消息**——PRD §6.2.5 禁的是同级分支与（孙）后代分支的消息，需在 PRD 加注记固化；(5) 063 免 TDD 批准；(6) 验证用代码测试，不用真机或模拟器。
- AI-autonomous decisions: 执行顺序 063 → 064 → 065 → 066 串行（065/066 共享 ChatScreen/ChatPresenter，064/065 共享 ChatWiring，且都要跑 Gradle）；底部操作行的「新建分支」（无选中句）维持整条回答锚定，不为它预填整条回答引用。
- User-confirmation decisions: 见 User choice 六项，全部已确认。
- Acceptance evidence: 各子任务自己的真实命令输出；063 为 assembleDebug + 用户真机自测记录；064/065/066 为 JVM 单测 RED/GREEN + 全量单测回归 + assembleDebug；SQLite 真实引擎验证按用户指示豁免，缺口记录在案。
- Facts: ① `android/app/src/main/AndroidManifest.xml` activity 无 `windowSoftInputMode`；`android/app/build.gradle` 第 35 行 targetSdk=36（Android 15+ 强制 edge-to-edge）；全工程 grep `WindowInsets`/`fitsSystemWindows` 为 0。② `ReasoningDisclosure.java` + `view_reasoning.xml` 已有折叠组件（text_sm + tl_ink2 已区分字号灰度），但 body 仅 expanded 时渲染、无自动展开/折叠语义（`ChatViewState` 展开集默认空）。③ `SqliteChatStore.history()`（第 133–156 行）只读本分支消息行；`ChatWiring.fork`（第 203–260 行）写快照但无人读回；`ChatWiring.fork` 无 missingKey 拦截；`MainActivity.render`（第 305 行）不校验 branchId；`ChatWiring.fork` 写死 `BranchAnchor.wholeAnswer()`，而领域层已有 `BranchAnchor.ofRange`（quote+hash 双定位）支持句子级锚定；`ChatScreen.openWordMenu` 已能取到选中句（`TextSelection.sentence`）。
- Assumptions: 真机系统为 Android 15+（edge-to-edge 强制生效），是键盘问题的主导成因。
- Unknowns: quote+hash 的 digest 派生在工程内是否已有现成实现（065 实现时核对，复用不得新写第二套）。
- Risks: 064 动 SQLite 读路径，豁免真机验证后存在 SQL 与 JVM 假实现不一致的漏检风险（已记录为缺口）；065 体量可能超 200 行软上限（已按切片理由登记）；063 的 windowOptOut 属性在 <API 35 设备上被忽略属预期行为。
- Deferred: 沉浸式 insets 适配；思考用时统计；思考区限高 token 若 dimens 无既有档位需新增并在任务内登记。
- Non-goals: 不改数据库 schema/migration/任何写路径；不做暗色模式；不引入 AndroidX/Kotlin；不改生成流程与提示词结构。
- Skipped non-critical questions and reason: 思考区限高的具体档位、来源卡的折叠默认态等表现细节，不改变范围与验收方式，实现时按既有 token 档位取值。

## Goal

产出一份经用户确认的缺陷修复需求，并拆成四个有界、可独立验证的 Harness 子任务，交付键盘可见、思考区对齐 DeepSeek 交互、新建分支携带上下文且来源可感知的对话页。

## Scope

### In scope
- `android/app/src/main/AndroidManifest.xml`、`res/values/themes.xml`（键盘）
- `wiring/SqliteChatStore.java`、`wiring/ChatWiring.java`、`MainActivity.java`（上下文）
- `screen/ChatScreen.java`、`ui/ChatActions.java`、`ui/ChatCommands.java`、`screen/ReasoningDisclosure.java`、`ui/ChatViewState.java`、`ui/ChatPresenter.java` 及相关布局与 strings（交互与思考区）
- `Docs/03-prd-draft/V3.2-...PRD.md` §6.2.5 口径注记
- `.trae/tasks/`（子任务文件）

### Out of scope
- 数据库 schema、migration、DAO 写路径
- AI 网关与提示词结构
- 沉浸式 insets、暗色模式、思考用时统计
- 049 批次（050–061）的任何领地文件

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是键盘窗口行为、思考披露的展开语义、分叉的锚定与请求上下文、子分支首屏内容。必须保留：生成中断/重试语义、稳定版本不可变、阅读位置恢复、划词追问留在当前分支、无 Key 时 send/retry/reset 的既有拦截。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 分叉生产者 `ChatWiring.fork`，消费端口 `ChatCommands`/`ChatStore`（`SqliteChatStore` 实现）；请求历史消费者 `DeepSeekRequestAssembler`（经 `historyForRequest`）；状态生产者 `ChatPresenter`，消费者 `MainActivity.render`；手机与 `layout-sw600dp/` 两套布局同步覆盖。
- Native bridge or independent-client compatibility/update requirements: 不适用，单一 Android 客户端。
- Behavior-neutral non-applicability reason, if applicable: 不适用，本批全部为行为变更。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 子分支请求上下文含出生快照；分叉可携带句子级锚定；子分支首屏出现来源卡与预填引用；窗口改为 resize 且退出 edge-to-edge 强制。
- Authoritative code/config/migration evidence: 见 Facts 引用的文件与行号。
- Documentation surfaces checked: `V3.2 PRD` §6.2.3/§6.2.4/§6.2.5、`android/README.md`。
- Documentation files updated: 各子任务落地；父需求自身不改文档。
- Producer/consumer/sibling/compatibility paths checked: 同 Behavior impact。
- Superseded phrases/examples reverse-searched: §6.2.5 的「不混入其他分支消息」措辞在 065 中加注记澄清口径。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: V3.2 为现行全量 PRD，注记就地加在 §6.2.5，不新开版本（属口径澄清而非规格变更）。

## Acceptance criteria

- [ ] 四个子任务全部 verified，且各自验收标准逐条有真实命令证据。
- [ ] 三个缺陷在用户真机自测中确认消失（键盘可见、思考区分明、分支携带上下文且来源可感知）。
- [ ] PRD §6.2.5 口径注记已落地并被 `check_spec_author_agent.py`/文档检查接受。
- [ ] 全量 JVM 单测与 assembleDebug 在最后一片完成后全绿。
- [ ] 验证缺口（豁免真机/模拟器的部分）逐片如实记录，无虚报 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用——父需求任务不含生产实现，TDD 证据由各子任务提供。
- Command: 不适用
- Exit code: 不适用
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation
- Implementation: 不适用——本任务只产出需求与任务拆分。
- Command: 不适用
- Exit code: 不适用
- Passing evidence: `validate_tasks.py` 对本批 5 个文件的校验退出码。
- Regression evidence: 不适用

### REFACTOR — optional
- Cleanup: 不适用
- Regression evidence: 不适用

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（父需求任务无生产实现）
- Reason: 父需求聚合子任务证据，自身不含业务改动
- User approval: 用户于 2026-09-22 明确选择走 Harness 并确认需求
- Alternative verification: 各子任务的真实命令输出

## Implementation notes

- 063 免 TDD 已获用户批准（2026-09-22），批准在实现之前，非事后追认。
- 064 的 SQLite 读路径按用户指示不跑真机/模拟器：合并逻辑抽成纯 JVM 类型测试，DAO 层做语句级断言；真实引擎验证缺口记入各任务 Verification report 的 Gaps。
- 各片都要跑 Gradle，执行必须串行：063 → 064 → 065 → 066。

## Verification report

- Verdict: PENDING
- Evidence: 待四个子任务 verified 后汇总。
- Changed/deleted assertion rationale and equivalent behavior coverage: 待子任务给出。
- Gaps: SQLite 真实引擎验证被用户豁免（2026-09-22 指示），已在 064 登记为残留缺口。
- Risks: 见协作记录 Risks。

## Handoff notes

- Completed: 需求澄清（六项决策全部获用户确认）；三个缺陷的代码级定位；本批 4 个子任务落盘。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 按 063 → 064 → 065 → 066 串行派工，每片完成必走 verifier，verifier 串行；065/066 不得并行（共享 ChatScreen/ChatPresenter），064/065 不得并行（共享 ChatWiring）。
- Watch-outs: (1) 065 的 fork 签名变更波及 `ChatActions`/`ChatCommands` 两端口，所有实现与测试桩同步；(2) 064 不得为图省事把快照合并逻辑留在 Android 类里导致无法 JVM 测试；(3) 063 的 opt-out 属权宜方案，沉浸式改造时须回看。

### 子任务索引

| id | 描述性文件名 | owner_lane | stack | 预估生产行数 | depends_on | shared_code |
| :--- | :--- | :--- | :--- | ---: | :--- | :--- |
| T-2026-09-22-063 | `T-2026-09-22-ime-optout-and-resize-063.md` | android | android | 8 | 无 | 否 |
| T-2026-09-22-064 | `T-2026-09-22-fork-snapshot-into-request-history-064.md` | android | android | 140 | 无（执行须在 063 后） | 是 |
| T-2026-09-22-065 | `T-2026-09-22-fork-anchor-quote-prefill-and-source-card-065.md` | android | android | 240 | 064 | 是 |
| T-2026-09-22-066 | `T-2026-09-22-reasoning-streaming-disclosure-066.md` | android | android | 150 | 无（执行须在 065 后） | 是 |
