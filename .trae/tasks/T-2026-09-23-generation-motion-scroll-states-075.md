# Treelearn Harness Task: 生成态动效与滚动状态吸收

id: T-2026-09-23-075
title: 生成态动效与滚动状态吸收
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-functional-visual-absorption-071.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: true
depends_on: [T-2026-09-23-chat-page-visual-redesign-spec-073.md, T-2026-09-23-sse-stream-boundary-cleanroom-spec-069.md, T-2026-09-22-reasoning-streaming-disclosure-066.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 功能与对话页视觉分层吸收；本片负责生成阶段的视觉反馈和阅读滚动权。
- Problem/background: RikkaHub 对 reasoning/answer 使用阶段状态、局部动画、自动跟随和消息跳转；知芽已有 SSE/思考披露基础，但需要把 forming、answering、stable、cancel、failure 与滚动状态统一起来。
- Users/scenarios: 学习者发送问题后观察思考和回答流；上滑阅读旧内容时不被拉回底部；需要时恢复跟随；网络失败或手动停止后能理解当前内容是否可继续。
- Expected outcome: 生成态动效克制、稳定、不改变布局命中；自动跟随只在用户接近底部时生效，上滑让位，并提供恢复入口；取消/失败/部分回答状态可行动。
- Core flow and reversibility: stream event → `ChatViewState` → render → scroll policy；停止/取消不触发重试；网络异常按 069 规则进入可识别重试态；所有状态可继续或返回。
- Inputs/outputs: 输入 069/073/066 规格和现有 `ChatViewState`/`ChatPresenter`/`ChatScreen`；输出纯 Java 状态与 Android View 动效/滚动实现。
- State and exception handling: 首字延迟、reasoning→answer、无内容、[DONE]、断流、取消、重试、低动效、后台恢复、长会话都必须有确定行为。
- Constraints and trade-offs: 不改 SSE 契约和持久化；无 Lottie/Compose/新依赖；动效不能依赖高频全屏重建；生产代码目标 ≤200 行。
- AI recommendation and rationale: 使用有限的 alpha/translation/光标/进度指示和局部 `post` 滚动，不做持续复杂动画；以阅读权优先于自动吸附。
- User choice and custom additions: 用户重点要求生成动效；本片按 073 直接落地，不做现有原型对比。
- AI-autonomous decisions: 默认尊重手动滚动，恢复跟随使用明确入口；低动效偏好关闭闪烁和位移动效但保留状态文案。
- User-confirmation decisions: 动效强度、恢复跟随入口样式由 073 review 一次拍板。
- Acceptance evidence: 状态 JVM RED/GREEN、滚动策略测试、布局/可见性断言、全量单测、assembleDebug、Java-only guard；真机帧率/手感另记缺口。
- Facts: 069 描述结束幂等、取消、网络重试和缓冲边界；066 已有思考区流式展开/自动折叠实现；当前 `ChatScreen` 用 ScrollView，`ChatViewState` 保存 phase/reading position。
- Assumptions: 069/066 规格与实现契约在本片开工前已 verified，若未完成则不得并行改共享状态。
- Unknowns: 长答案高频重绘的真实帧率、IME 与后台恢复时序需要真机观察。
- Risks: 自动滚动抢夺阅读位置；停止被误判为网络失败；动画改变布局高度导致按钮热区移动。
- Deferred: 复杂粒子/骨架屏、跨平台动效库、音效与振动增强。
- Non-goals: 不改 SSE parser、重试次数、prompt、数据库 schema 或大屏导航结构。
- Skipped non-critical questions and reason: 动效具体曲线与时长在视觉规格中统一定义，不在任务创建轮次重复询问。

## Goal

实现生成阶段的稳定动效、阶段反馈和不抢阅读权的滚动状态。

## Scope

### In scope
- `ChatViewState`/`ChatPresenter` 的生成阶段与滚动策略状态。
- `ChatScreen`/`item_chat_message.xml`/reasoning 视图的局部动效、首字/结束/失败/取消反馈。
- 自动跟随、上滑让位、恢复跟随、新消息提示和长会话跳转入口。
- 低动效降级、无障碍状态描述和布局稳定性断言。

### Out of scope
- SSE 协议或网络重试实现（069 的行为契约只作为输入）。
- 输入栏、消息动作和富内容实现（074/076）。
- 新动画依赖、音效、振动和暗色主题。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 增加生成态与滚动反馈；保留已有 stable/failed/canceled 语义、阅读位置锚点和 066 的思考展开意图。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatPresenter` 生产 `ChatViewState`，`ChatScreen` 消费；`screen_chat_body.xml` 与 `view_reasoning.xml` 的手机/平板资源需核对。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 生成态与滚动状态反馈发生变化。
- Authoritative code/config/migration evidence: `ChatViewState.java`、`ChatPresenter.java`、`ChatScreen.java`、`ReasoningDisclosure.java`、`view_reasoning.xml`。
- Documentation surfaces checked: 069、066、073、PRD 流式/阅读位置条款。
- Documentation files updated: 按实现结果更新 073/PRD 口径；若无文档差异，记录明确 `documentation impact: none` 证据。
- Producer/consumer/sibling/compatibility paths checked: stream sink、presenter phase、screen scroll listeners、reading position。
- Superseded phrases/examples reverse-searched: `chat_loading`、`chat_error`、`chat_generating_note`、`isReasoningEffectivelyExpanded`、`ReadingAnchor`。
- `documentation impact: none` reason and evidence, if applicable: 由实现结果决定。
- Historical/PRD handling: 冲突回到 PRD/UX review，不在实现中默改。

## Acceptance criteria

- [ ] RED 先验证形成/回答/稳定/取消/失败及滚动策略缺失，GREEN 同命令转绿。
- [ ] 自动跟随只在底部/近底部生效；用户上滑后不跳回；有明确恢复入口。
- [ ] reasoning→answer、[DONE]、断流、取消和重试的视觉状态与 069/066 一致，结束事件不重复触发 UI。
- [ ] 动效不改变相邻动作热区位置；低动效模式移除闪烁/位移动效但保留信息。
- [ ] 全量 JVM 单测、assembleDebug、Java-only guard 和布局/token 扫描有证据；真机手感缺口如实记录。
- [ ] 生产代码非空非注释行数 ≤200，数字来自证据文件。

## TDD execution record

### RED — failing test first
- Test/scenario: 生成阶段状态机（SENDING→REASONING→ANSWERING→STABLE/CANCELED/FAILED，停止意图竞态）、跟随策略（视口判定/上滑让位/missed 计数/恢复清零）、低动效降级、布局稳定性。RED 为 55 个编译错误（缺 `Stage`/`follow`/`onStopRequested()`/`onFollowLatestPressed()`/`ChatMotion`/布局 id/文案键）。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.*Chat*" --tests "com.treelearn.app.screen.*"`
- Exit code: 1
- Observed failure: 编译期 55 个错误（缺 `Stage`/`follow`/`onStopRequested()`/`onFollowLatestPressed()`/`ChatMotion`/布局 id/文案键），即缺失行为清单。
- Failure reason validated: 全部缺失符号对应本任务目标行为，实现后转绿。

### GREEN — minimum implementation
- Implementation: `ChatViewState`/`ChatPresenter` 新增 Stage 状态机与跟随策略（停止意图由终态消费，竞态败者保持 STABLE）；`ChatMotion` 集中动效常量（073 §9 token 候选：quick≈150/enter≈220/collapse≈180/caret≈800ms）与低动效判定（`ANIMATOR_DURATION_SCALE==0` 全静态化）；布局稳定性=占位互斥槽位（光标/生成中注/错误条/已停止标注），动作条仅 STABLE 可见，浮钮叠放 FrameLayout；「回到最新（N）」浮钮 ≥44dp；TalkBack 阶段播报。新增文案键 `chat_stopped`/`chat_back_to_latest`/`chat_back_to_latest_count`、dimen `caret_width`。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.*Chat*" --tests "com.treelearn.app.screen.*"`
- Exit code: 0
- Passing evidence: 同命令转绿；全量 `gradlew check assembleDebug` 另行退出码 0，证据 `artifacts/evidence/.t075-green.log`、`.t075-check.log`（BUILD SUCCESSFUL；`verifyNoKotlinDex: PASS`、`verifyDependencyDirection: PASS`）。
- Regression evidence: `.t075-check.log` 全量单测 + lint + assembleDebug；新增生产行 198（≤200，`.t075-added-lines.log`）。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 同上 `check` 退出码 0。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（tdd_mode: required）。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 动效只在 View 层做表现，状态判断留在 `ChatViewState`/`ChatPresenter`；不要把流状态藏在 View 字段。
- 滚动策略必须基于已存在的阅读位置/可见行事实，不以“每次 render 都 fullScroll”实现。
- 取消与网络失败是不同用户语义：取消静默结束并保留部分内容，网络失败提供可行动重试。

## Verification report

- Verdict: PASS（独立 verifier 2026-09-24 一次性 PASS）
- Evidence: verifier 只读复跑全量单测 `--rerun` 退出码 0、`verifyNoKotlinDex` PASS、Java-only 静态抽查为空、未触及禁区路径；`.t075-added-lines.log` 实测 198≤200；布局稳定性抽查：`item_chat_message.xml:120-186` 光标/生成注/错误条/已停止为独立槽位、`ChatScreen.java:649-664` 条件互斥、动作条仅 STABLE 可见（:638）、`screen_chat_body.xml:99-218` 浮钮叠放 FrameLayout；`ChatMotion.java` 低动效判定与 token 候选值与任务记录一致。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。
- Gaps: 073 §4.2 reasoning 已用秒数计时未实现（leader 已记缺口）；真机帧率/手感未测。
- Risks: 高频流式刷新性能回退（沿用既有整行重建设计）；需真机测量而非观感宣称。

## Handoff notes

- Completed: 任务契约已生成；实现完成（2026-09-24，android-dev 子代理，TDD 证据见 TDD 执行记录，生产行 198/200）。
- In progress: 待独立 verifier 验收。
- Blocker: 无技术阻塞。
- Unblock condition: verifier PASS 后转 verified。
- Next steps: verifier 核对 TDD 证据、布局稳定性断言、guard 结论与 073 §4/§6 映射。
- Watch-outs: 不把断流当稳定答案；不因动画方便而取消阅读位置保护。
- [leader 2026-09-24] 已知缺口（verifier 必查）：① 073 §4.2 的 reasoning 已用秒数计时未实现（T-066 行为保留），属规格映射部分缺口；② 长列表跳转浮条（可选项）未做——浮钮已提供到底入口；③ 取消后 FAILED 行部分内容回显依赖 wiring 现状；④ 真机帧率/手感未测。依赖说明：本任务使用 T-069 规格作为行为依据（该任务仍 verifying，用户以「执行」指令直接授权）。
