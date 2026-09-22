# Treelearn Harness Task: RikkaHub 功能与对话页视觉分层吸收（父需求）

id: T-2026-09-23-071
title: RikkaHub 功能与对话页视觉分层吸收（父需求）
template_version: 2
task_type: requirement
status: verified
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-23-rikkahub-layered-absorption-067.md]
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 现有 RikkaHub 吸收任务主要产出了交互基准、SSE 边界和分支模型规格，但没有把「功能吸收」与「对话页视觉重做」拆成可执行任务。用户明确要求知芽吸收 RikkaHub 的基础功能与 UX，重点重做 AI 消息卡、生成态动效、输入栏和消息操作。
- Users/scenarios: 单人开发者在 Android 手机和平板上使用知芽进行树状学习；实现者需要一份可审计的功能取舍、一份直接面向最终体验的对话页视觉规格，以及互不冲突的实现切片。
- Expected outcome: ① 功能能力矩阵，明确采用/改造/延后/拒绝及理由；② 不依赖现有原型对比的对话页视觉重做规格；③ 三个可按依赖执行的 Android 实现任务，覆盖消息与动作、生成态与滚动、输入栏与输入能力。
- Core flow and reversibility: 读取 RikkaHub 与知芽当前代码 → 形成能力矩阵和最终视觉规格 → 用户 review → 按依赖执行 Android 子任务。规格与 UI 变更均可回滚，不改 RikkaHub 副本。
- Inputs/outputs: 输入 RikkaHub 源码/README、知芽当前 Android UI、既有 PRD 与 design token；输出 `Docs/05-technical-design/` 功能矩阵、`Docs/04-ux-design/` 视觉规格、`android/**` 的后续实现。
- State and exception handling: 功能未被矩阵列为 P0/P1 的不得进入实现；生成中、取消、网络失败、部分回答、归档分支、无 Key、键盘弹出和低动效偏好必须有显式 UI 状态；RikkaHub 仅作参照，不把其线性会话模型直接移植为知芽树模型。
- Constraints and trade-offs: Java-only、原生 XML/View、无 Kotlin/Compose/新未验证依赖；本地优先、DeepSeek-only、SQLiteOpenHelper 与既有树状模型不变；视觉落地必须引用知芽 token；AGPL clean-room 只允许基于独立规格实现，不把 clean-room 当作法律豁免。
- AI recommendation and rationale: 先做功能矩阵与视觉规格，再做三个窄实现片。这样能同时吸收 RikkaHub 的功能经验和视觉细节，又不会把多 Provider、云生态、Kotlin/Compose 直接带入知芽。
- User choice and custom additions: 用户 2026-09-23 明确要求「不需要原型对比，直接生成对应的 task」，并补充「不只是交互的吸收，功能也有吸收」。本任务据此新增功能矩阵、视觉重做规格和实现任务。
- AI-autonomous decisions: 视觉任务直接定义目标体验，不安排现有原型对比；功能实现先锁定消息动作/富内容、生成态/滚动、输入栏/草稿三组高价值能力；附件、语音、多 Provider、MCP、workspace 等由矩阵明确延后或拒绝。
- User-confirmation decisions: 功能矩阵中的 P0/P1 取舍、最终视觉规格中的 AI 消息卡和输入栏风格、是否把延后能力纳入后续版本，均在规格 review 时拍板。
- Acceptance evidence: `validate_tasks.py` errors=0；两份规格存在、UTF-8、无占位符、事实有出处；三个 Android 子任务各自提供 RED/GREEN、全量回归、Java-only guard 与 token/无障碍证据。
- Facts: 知芽为 Java-only 原生 Android、本地优先、DeepSeek-only，当前已有 `ChatScreen`、`MarkdownRenderer`、分支来源卡、思考披露和消息动作；RikkaHub 具备丰富消息渲染、流式状态、输入栏、模型/助手、消息队列、附件/语音及主题设置能力。
- Assumptions: 用户希望先建立可执行任务，不要求本轮立即修改生产代码；RikkaHub 本地副本继续作为只读分析来源。
- Unknowns: 功能矩阵 review 前，附件/语音/收藏/翻译/多 Provider 等功能的版本优先级尚未确定；视觉规格的最终细节尚未进入当前态 UX 文档。
- Risks: 把 RikkaHub 的实现细节误当作知芽契约；clean-room provenance 与实现者隔离不足；视觉任务改动 `ChatScreen`、布局和 strings 造成共享文件冲突；过度吸收导致任务超过 200 行生产代码预算。
- Deferred: 真机视觉观感、附件/语音/多 Provider/MCP/workspace、Markdown 表格/LaTeX 的选型与实现，均待矩阵或独立任务确认。
- Non-goals: 不 fork RikkaHub；不复制其 Kotlin/Compose 源码、像素、文案或依赖；不改服务端、数据库 schema/migration、提示词契约或树状分支核心模型。
- Skipped non-critical questions and reason: 本轮只生成任务，不进入能力取舍的最终产品拍板；矩阵与视觉规格任务会把需要用户决定的事项集中列出。

## Goal

建立 RikkaHub 功能与对话页视觉吸收的可执行任务链，确保功能取舍、视觉状态和 Android 实现边界各自独立且可验收。

## Scope

### In scope
- 子任务 072：RikkaHub 功能能力矩阵与知芽采用/改造/延后/拒绝决策。
- 子任务 073：对话页最终视觉重做规格，覆盖 AI 消息卡、生成态动效、输入栏、消息操作、滚动与无障碍。
- 子任务 074–076：按依赖执行 Android 消息与动作、生成态与滚动、输入栏与输入能力实现。

### Out of scope
- RikkaHub 整库 fork 或代码搬运。
- 本轮直接修改生产代码；生产修改由 074–076 单独执行。
- 多 Provider、MCP、workspace、云同步、服务端和数据库 schema/migration。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 本父任务只新增任务文件；后续子任务会在不改变树状分支、提示词、持久化契约的前提下改进对话页表现与已拍板功能。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 现有生产消费者集中在 `android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java`、`ui/ChatPresenter.java`、`ui/ChatViewState.java`、`res/layout/screen_chat_body.xml`、`res/layout/item_chat_message.xml` 与 `res/layout/view_answer_actions.xml`；手机与 `layout-sw600dp` 需在实现任务中逐一核对。
- Native bridge or independent-client compatibility/update requirements: 不适用，Android 单客户端。
- Behavior-neutral non-applicability reason, if applicable: 本父任务不改运行时代码。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 本父任务只新增任务；后续实现会改变对话页展示状态和功能入口。
- Authoritative code/config/migration evidence: `.trae/context/project-brief.md`、`Docs/03-prd-draft/`、`Docs/04-ux-design/`、`android/app/src/main/java/com/treelearn/app/screen/` 与 `D:\Project\Android\rikkahub-master` 只读副本。
- Documentation surfaces checked: `Docs/04-ux-design/`、`Docs/05-technical-design/`、现有 067–070 任务。
- Documentation files updated: 由 072、073 及后续实现任务各自声明。
- Producer/consumer/sibling/compatibility paths checked: 由 074–076 按各自共享 UI 入口声明并核对。
- Superseded phrases/examples reverse-searched: 「RikkaHub 交互基准」「完整吸收」「AI 消息卡」「输入栏」「生成动效」等将由新任务区分为参照、目标体验和实现事实。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 067–070 保留为前一阶段分析记录；072/073 review 通过后，才更新当前态 UX/技术文档。

## Acceptance criteria

- [ ] 072 和 073 任务文件存在，依赖与产物路径明确，状态可进入 `ready`。
- [ ] 072 能覆盖 RikkaHub 主要功能类别并给出知芽取舍，不把“未决定”写成已采纳。
- [ ] 073 直接描述最终对话页视觉状态，不依赖现有原型对比，且覆盖消息卡、生成态、输入栏、滚动、错误/取消、响应式与无障碍。
- [ ] 074–076 为窄垂直切片，生产代码预算各不超过 200 行目标，依赖 072/073 后再执行。
- [ ] 新任务全部通过 `python .trae/scripts/validate_tasks.py`，errors=0。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（父任务只创建任务契约）。
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 创建 071–076 六个 Task v2 文件，建立规格先行、实现后置的依赖链；072/073 已执行并交付规格文档。
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 2026-09-23 执行 `SUMMARY files=76 errors=0`；072 交付 `Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md`、073 交付 `Docs/04-ux-design/知芽（TreeLearn）对话页视觉重做规格_V1.0.md`，均进入 `verifying`
- Regression evidence: 不适用（本轮无生产代码）。

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: 父任务不适用；子任务分别遵守其 `tdd_mode`。
- Reason: 本轮只生成任务契约。
- User approval: 用户 2026-09-23 明确要求直接生成对应任务。
- Alternative verification: 任务机械校验 + 用户 review。

## Implementation notes

- 072 是功能事实与取舍的唯一入口；074–076 不得自行从 RikkaHub 源码补充功能。
- 073 是对话页视觉事实与状态的唯一入口；实现任务只读该规格、知芽代码和 token，不做现有原型对比。
- 未来 clean-room 实现应把实现规格与 provenance 审计记录分离；规格中的出处不得被当作实现者阅读上游源码的授权。

## Verification report

- Verdict: PASS（2026-09-24 收口：072/073 经用户拍板采纳推荐后 verified；074/075/076 由独立 verifier 逐一验收 PASS——074 初判 PARTIAL 缺行数证据，补 `.t074-linecount.txt` 后复核改判 PASS；`validate_tasks.py` errors=0）
- Evidence: 子任务状态 072–076 全部 `verified`；三任务生产行 109/198/175 均在预算内；三任务 `gradlew check` 退出码 0（含 verifyNoKotlinDex、verifyDependencyDirection）；证据文件 `artifacts/evidence/.t074-*.log/.t074-linecount.txt`、`.t075-*.log`、`.t076-*.log`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（父需求）。
- Gaps: 真机体验（帧率/IME/横屏/大字号）全链路未测（无设备）；073 §4.2 reasoning 计时未实现（075 缺口，可另立小任务）；defer 池 14 项与 reject 8 项维持矩阵结论。
- Risks: 工作区含多个未提交任务的改动，git 归因粒度受限（各任务以自报增量口径判定并留证据）。

## Handoff notes

- Completed: 任务链创建；072/073 规格文档交付（2026-09-23）。
- In progress: 用户 review 072 第 11 节与 073 第 10 节的拍板项。
- Blocker: 用户 review 是 074–076 进入实现的门槛。
- Unblock condition: 072/073 按用户拍板修订后转 `verified`。
- Next steps: 拍板通过后按 074 → 075 → 076 串行执行（共享 `ChatViewState`/`ChatPresenter`/`ChatScreen`，禁并行）；附件/语音/消息队列等 defer 项在矩阵确认后另立任务。
- Watch-outs: 不要把 067–070 的静态分析当作视觉或功能已落地证据；不要把 AGPL clean-room 描述成法律豁免；074–076 不得从 RikkaHub 源码自行补充功能，只能引用 072 矩阵与 073 规格已批准条目。
