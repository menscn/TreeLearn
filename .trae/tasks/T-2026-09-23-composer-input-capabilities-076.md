# Treelearn Harness Task: 输入栏与输入能力吸收

id: T-2026-09-23-076
title: 输入栏与输入能力吸收
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-functional-visual-absorption-071.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 190
shared_code_touched: true
depends_on: [T-2026-09-23-rikkahub-capability-absorption-matrix-072.md, T-2026-09-23-chat-page-visual-redesign-spec-073.md, T-2026-09-22-ime-optout-and-resize-063.md, T-2026-09-22-fork-anchor-quote-prefill-and-source-card-065.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 功能与对话页视觉分层吸收；本片负责输入栏视觉与第一批输入能力。
- Problem/background: RikkaHub 输入栏包含多行编辑、上下文/附件行、模型/搜索/思考入口、发送/停止、队列和语音状态；知芽已有草稿、模型/思考选择、引用/分支上下文和 IME 修复基础，但缺少统一状态规格。
- Users/scenarios: 学习者在键盘弹起时编辑多行问题，引用句子或来源分支继续提问，生成中停止，生成期间保留草稿，必要时切换模型/思考模式；无 Key 或归档分支时得到清晰反馈。
- Expected outcome: 输入栏在手机/平板、键盘/旋转/重建下稳定；send/stop/disabled 三态清晰；quote/fork context 可关闭且不丢草稿；矩阵批准的输入能力以最小切片落地。
- Core flow and reversibility: draft change → state → composer render → send/stop/context action；发送失败保留可恢复草稿；取消不清空未发送草稿；归档分支只读。
- Inputs/outputs: 输入 072/073、063/065 与当前 `ChatScreen`/`ChatPresenter`；输出 Java/XML/资源、状态测试和 IME/触达证据。
- State and exception handling: empty/valid/loading/stop/editing/quote/fork/no-Key/archived/IME-open/rotation/disabled 全覆盖；附件/语音若未被 072 选为 P0，必须保留为明确延后入口而非半成品。
- Constraints and trade-offs: Java-only、XML/View、无新依赖；复用现有模型/思考选择器与 quote/source card；生产代码目标 ≤200 行；不改消息 schema。
- AI recommendation and rationale: 先做输入栏状态稳定和上下文可见性，再决定附件/语音等复杂能力；状态即入口，避免额外悬浮停止按钮。
- User choice and custom additions: 用户要求重点重做输入栏，并要求功能吸收；具体附加能力以 072 矩阵 review 结果为准。
- AI-autonomous decisions: 不自动引入附件权限、麦克风、文件 provider 或队列持久化；这些能力若被采纳必须另立有界任务。
- User-confirmation decisions: 是否把附件/语音/队列纳入本版本、输入栏是否显示扩展入口，由 072/073 review 拍板。
- Acceptance evidence: 状态 JVM RED/GREEN、布局/IME/热区断言、全量单测、assembleDebug、Java-only guard；真机键盘观感记录缺口。
- Facts: 063 已处理键盘 resize/insets 方向；065 已有 quote/source card；现有 composer 位于 `screen_chat_body.xml`，模型/思考在 `ModelThinkingPicker`。
- Assumptions: 063/065 依赖已 verified；本片与 075 不并行修改同一 ChatScreen 状态代码。
- Unknowns: 各 Android 版本和输入法对多行高度、window resize 的差异。
- Risks: 输入栏被键盘遮挡；发送/停止混淆；引用/分支上下文与草稿清理互相覆盖；横屏空间不足。
- Deferred: 附件、语音、队列、搜索服务、快捷消息和多 Provider 由矩阵后续拆分。
- Non-goals: 不改 DeepSeek 请求格式、SSE、提示词、数据库或模型 Provider。
- Skipped non-critical questions and reason: 附加能力统一在 072 处理，本片只锁定输入栏核心状态。

## Goal

实现稳定、可恢复、可访问的对话输入栏，并吸收 RikkaHub 中与知芽主流程兼容的输入能力。

## Scope

### In scope
- `screen_chat_body.xml` composer：多行编辑、IME 安全区、上下文行、模型/思考入口、send/stop/disabled 状态。
- `ChatViewState`/`ChatPresenter`/`ChatActions` 的草稿、引用/分支上下文、发送/停止和归档/无 Key 反馈。
- 输入栏状态、触达 ≥44dp、contentDescription、中文/英文资源键和横屏布局核对。
- 072 明确批准且不需新权限/schema 的输入能力。

### Out of scope
- 附件上传、语音 ASR/TTS、消息队列持久化、快捷消息、搜索服务和多 Provider；如矩阵采纳，另立任务。
- 生成态滚动/动效（075）和消息动作/富内容（074）。
- 新权限、新依赖、数据库迁移、网络协议修改。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 改进输入栏表现与草稿/上下文反馈；保留发送语义、引用/分支快照、无 Key 拦截和归档只读规则。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatActions`/`ChatPresenter` 生产状态，`ChatScreen`/`screen_chat_body.xml`/`ModelThinkingPicker` 消费；`layout-sw600dp` 需核对。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 输入栏状态和草稿/上下文反馈发生变化。
- Authoritative code/config/migration evidence: `ChatScreen.java`、`ChatPresenter.java`、`ChatActions.java`、`ChatViewState.java`、`screen_chat_body.xml`、`ModelThinkingPicker.java`。
- Documentation surfaces checked: 063、065、073、PRD 输入/分支/Key/归档条款。
- Documentation files updated: 按实现结果更新 073 与相关 PRD 当前态说明；无变化时提供 `documentation impact: none` 证据。
- Producer/consumer/sibling/compatibility paths checked: draft TextWatcher、IME action、send click、quote clear、fork context、model/thinking selector。
- Superseded phrases/examples reverse-searched: `chat_draft_hint`、`chat_send`、`chat_quote_row`、`onQuoteCleared`、`onSend`、`canSubmit`。
- `documentation impact: none` reason and evidence, if applicable: 由实现结果决定。
- Historical/PRD handling: 冲突回到产品/UX review，不静默覆盖冻结条款。

## Acceptance criteria

- [ ] RED 先证明输入栏状态和 IME/上下文断言缺失，GREEN 同命令转绿。
- [ ] 键盘弹起时草稿和光标可见；多行高度受约束且不遮挡发送/停止；旋转/重建不丢草稿。
- [ ] send/stop/disabled、quote/fork、no-Key、archived 状态与 073 一致，context 清除不误删草稿。
- [ ] 模型/思考入口保留现有行为；附加能力未获批准时不显示伪入口。
- [ ] 全量 JVM 单测、assembleDebug、Java-only guard、IME/布局/token 扫描有证据；真机键盘观感缺口如实记录。
- [ ] 生产代码非空非注释行数 ≤200，数字来自证据文件。

## TDD execution record

### RED — failing test first
- Test/scenario: 发送键三态判定（canSubmit/attemptRunning/keyConfigured/归档 → DISABLED|SEND|STOP）、草稿持久化往返（仅 draft/quote/model/thinking，不含敏感字段）、归档与无 Key 态 composer 行为。RED 为 35 个编译错误（缺 `SendState`/`ChatDraftStore`/`withArchived`/`sendState()`）。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.*Chat*"`
- Exit code: 1
- Observed failure: 编译期缺失符号即 RED 证据（注：RED 运行与测试文件修正在同一窗口进行，日志混有少量测试自身符号错误，已如实记录）。
- Failure reason validated: 缺失符号全部对应本任务目标行为，实现后转绿。

### GREEN — minimum implementation
- Implementation: 新增 `ui/ChatDraftStore.java`（31 行）与 `wiring/SharedPreferencesChatDraftStore.java`（50 行，仅存 draft/quote/model/thinking）；`ChatViewState`（+27，`SendState`/`withArchived`）、`ChatPresenter`（+22）、`ChatScreen`（+26，`chat_send` 单控件三态换态+点击路由复用 075 `onStopRequested`）、`AppContainer`（+6）、`MainActivity`（+1）；新增 `drawable/ic_chat_stop.xml`；strings +3 键（`chat_draft_hint_no_key`/`chat_draft_hint_archived`/`cd_chat_send_stop`）；`screen_chat_body.xml` 引用行对齐 text_sm/ink2（净 0 行）。归档态保留草稿；旋转由 manifest `configChanges` 承接，进程重建走 SharedPreferences。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.*Chat*"`
- Exit code: 0
- Passing evidence: 同命令 160 个 Chat 测试通过（含新增 11 个）；全量 `gradlew check assembleDebug` 另行退出码 0，证据 `artifacts/evidence/.t076-green.log`、`.t076-check.log`（`verifyDependencyDirection: PASS`、`verifyNoKotlinDex: PASS`，debug/release 全量 JVM 测试通过）。
- Regression evidence: `.t076-check.log`（首跑 release dex 因 Windows 文件锁失败，`--stop` 重跑即绿，涉及未改动文件，非代码问题）；新增生产代码 175 行（`.t076-linecount.log`，≤190）。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 同上 `check` 退出码 0。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（tdd_mode: required）。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 草稿是用户输入，不是消息内容；任何停止/取消/失败路径都不得未经规格允许清空它。
- Context card 的来源文字只作可见提示，真正上下文仍由既有快照/quote 链路提供。
- 触达区域和视觉容器分离：输入栏可紧凑，但发送/清除/模型入口都要满足 44dp。

## Verification report

- Verdict: PASS（独立 verifier 2026-09-24 一次性 PASS，含 RED 有效期专项结论）
- Evidence: verifier 只读复跑退出码 0、`verifyNoKotlinDex` PASS、Java-only 抽查为空；行数 175≤190；RED 有效性：35 错误中约 27 个为缺失生产符号（`SendState`×8、`sendState()`×5、`ChatDraftStore`/`ChatDraft`×10、`archived` 字段、接受 DraftStore 的构造重载），可独立支撑编译失败，混入的测试夹具符号（×8）已被如实记录且不掩盖因果——与 T-074 RED-1 先例同型，判定有效；`chat_send` 单控件三态核实（`screen_chat_body.xml:288-303` 单 Button、`ChatScreen.java:427-443` 按 SendState 换态）；`SharedPreferencesChatDraftStore.java` 仅存 text/quote/model/thinking，无敏感字段。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。
- Gaps: 真机 IME、旋转、长草稿和大字号待补；每键击一次 `apply()` 的 IO 压力未实测。
- Risks: Android 15 edge-to-edge 与不同输入法组合导致布局差异。

## Handoff notes

- Completed: 任务契约已生成；实现完成（2026-09-24，android-dev 子代理，TDD 证据齐备，生产行 175/190）。
- In progress: 待独立 verifier 验收。
- Blocker: 无技术阻塞。
- Unblock condition: verifier PASS 后转 verified。
- Next steps: verifier 核对三态判定、持久化往返（敏感字段排除）、归档/无 Key 行为、guard 结论。
- Watch-outs: 不把附件/语音入口半实现；不以 UI context 文案替代真实快照上下文。
- [leader 2026-09-24] 范围决定：3.7 全屏编辑器**不做**（073 §5 无其视觉规格，且旋转/重建丢失已由 configChanges+持久化覆盖，属伪入口风险）。已知缺口：真机 IME/大字号/横屏观感未验证（无设备）；每键击一次 `apply()` 的 IO 压力未实测；语言键沿用仓库中文单语约定（无 values-zh 目录）。RED 日志混有测试自身符号错误的修正痕迹，已如实记录，verifier 重点复核 RED 有效期。 **范围更新**：全屏编辑器（3.7）原判定「不做」，用户 2026-09-24 推翻——已立 T-2026-09-23-composer-fullscreen-editor-077.md 执行，073 §5.1 已补规格。
