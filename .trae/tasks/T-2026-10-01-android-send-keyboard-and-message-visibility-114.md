# Treelearn Harness Task: 发送后收起输入法并显示用户消息

id: T-2026-10-01-114
title: 发送后收起输入法并显示用户消息
template_version: 2
task_type: implementation
status: blocked
parent_requirement: T-2026-10-01-android-daily-use-issues-111.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 140
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 收集十项 Android 反馈。本任务合并同一发送链路的 #2 输入法未收起与 #8 用户问题未显示。

- Problem/background: 用户报告点击发送后软键盘仍停留；另有 AI 已回复但自己刚发的问题没有出现在界面上的情况。
- Users/scenarios: Magic8 Pro Air / MagicOS 11 对话页发送有效文本，等待 AI 回复，或遇到本地保存/发送拒绝。
- Expected outcome: 发送被接受后收起软键盘且用户问题只显示一次、无需等待 AI 回复；拒绝时保留草稿与既有错误反馈。
- Core flow and reversibility: 输入 → 点击发送 → 接受时本地消息出现并收键盘 → 流式生成；拒绝时输入保留可编辑，不丢内容。
- Inputs/outputs: 输入草稿与接受/拒绝结果；输出为可见的一条用户消息、输入法状态及错误反馈。
- State and exception handling: 区分本地持久化失败、重复点击、生成失败与正常接受；不能重复插入或在未接受时丢草稿。
- Constraints and trade-offs: Java-only；本地 SQLite 仍为消息权威；不改变消息排序、生成协议或分支快照。
- AI recommendation and rationale: 以发送命令接受状态为键盘收起与清草稿的边界，以消息持久化/投影链路排查显示缺失。
- User choice and custom additions: 用户确认只拆 Android Harness；发送拒绝时保留草稿来自父任务已确认的约束。
- AI-autonomous decisions: AI 回复失败时仍保留已接受且已持久化的用户消息；相同消息只出现一次。
- User-confirmation decisions: 发送成功后软键盘收起；用户问题在 AI 回复前可见；失败草稿保留。
- Acceptance evidence: ChatPresenter 定向单测、相关存储/视图回归，以及 Magic8 Pro Air / MagicOS 11 实机观察。
- Facts: `ChatWiring.send` 当前在提交生成前持久化用户消息；`ChatPresenter.send/publish` 驱动视图状态；`ChatScreen.renderMessages` 渲染消息行。现行反馈表明显示时序仍需验证。
- Assumptions: 用户报告的发送入口为当前对话页标准发送按钮。
- Unknowns: 当前设备 APK 与日志；问题是否受快速点击、历史分支或键盘类型影响。
- Risks: 为掩盖视图未刷新而增加重复持久化会造成重复消息。
- Deferred: 不可复现的输入法问题可留作设备差异诊断，不扩大到 IME 重构。
- Non-goals: 新增发送方式、后端契约、API 或数据库 schema；不改键盘布局。
- Skipped non-critical questions and reason: 无；复用标准发送入口，异常按接受/拒绝结果分支覆盖。

## Goal

标准发送被接受后立即呈现一条用户问题并收起软键盘；发送被拒绝时保留草稿与失败反馈。

## Scope

### In scope
- 沿 `ChatPresenter.send/publish`、`ChatWiring.send`、`SqliteChatStore` 与 `ChatScreen.renderMessages` 修复真实断点。
- 先加定向回归测试，覆盖接受、拒绝、快速重复点击、生成失败及首次渲染/权威读回。
- 更新 Android 当前用户流程文档，并在 MagicOS 11 实机确认软键盘和消息可见时序。

### Out of scope
- 修改接口契约/schema、消息排序或生成服务；强制清空拒绝发送的草稿；重做输入框交互。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 接受发送后隐藏 IME、显示唯一用户消息；拒绝/持久化失败时保留草稿、错误提示和已有消息顺序。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatPresenter.send/publish` → `ChatWiring.send` → `SqliteChatStore` → `ChatScreen.renderMessages`；覆盖普通对话与分支对话的标准发送入口。
- Native bridge or independent-client compatibility/update requirements: N/A — 不改接口或原生桥接契约。
- Behavior-neutral non-applicability reason, if applicable: N/A — 本任务改变可见交互。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: accepted 发送先从权威存储读回用户问题，再清草稿并请求宿主隐藏输入法；refused 发送保留草稿与原错误提示。
- Authoritative code/config/migration evidence: `ChatPresenter.java`、`ChatWiring.java`、`SqliteChatStore.java`、`ChatScreen.java` 与对应单测。
- Documentation surfaces checked: `android/README.md`、发送/消息持久化相关当前态流程文档。
- Documentation files updated: `android/README.md` 的「发送接受后即时可见并收起输入法」说明（T114 生产路径已核对）。
- Producer/consumer/sibling/compatibility paths checked: 发送入口、存储生产者、Presenter 状态消费者、消息列表渲染与分支发送入口。
- Superseded phrases/examples reverse-searched: 搜索「发送后」「收起输入法」「键盘」；当前行为说明已更新，既有 `adjustResize` 系统配置说明仍有效。
- `documentation impact: none` reason and evidence, if applicable: N/A — 用户可见发送流程变化需同步文档。
- Historical/PRD handling: 不静默改写历史截图/反馈；若旧说明与新行为冲突，保留历史标记并链接当前流程。

## Acceptance criteria

- [ ] 新增测试先红后绿：接受发送后软键盘收起，用户问题在 AI 首个响应前只出现一次。
- [ ] 拒绝/持久化失败保留草稿与既有错误提示；生成失败仍保留已接受的用户消息；快速重复点击不产生重复消息。
- [ ] 普通与分支对话的标准发送路径回归通过；目标设备观察符合预期；Java-only guard 与受影响文档检查有证据。

## TDD execution record

### RED — failing test first
- Test/scenario: Added anAcceptedSendShowsTheStoredQuestionBeforeTheNextBackgroundRead and anAcceptedSendRequestsKeyboardDismissalButARefusedSendDoesNot to ChatPresenterTest; they cover store-backed visibility before the queued read and accepted-only keyboard dismissal.
- Command: .\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.ChatPresenterTest
- Exit code: 1
- Observed failure: Task record reports 52 tests completed, 2 failed; both new tests failed at ChatPresenterTest.java:320 and :333. Original RED console output was not retained.
- Failure reason validated: Compilation succeeded; the accepted path leaves the stored question absent until the queued read and emits no keyboard-dismiss request, so both failures are behavior-specific RED evidence.

### GREEN — minimum implementation
- Implementation: Accepted sends perform one authoritative read before UI publish, skip the trailing duplicate read, and request keyboard dismissal only for accepted results; refusal/draft behavior and view-state non-optimistic message sourcing remain unchanged.
- Command: .\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.ChatPresenterTest
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-01-114-green-chatpresenter-iteration-2.txt` records BUILD SUCCESSFUL / EXITCODE=0; independent forced rerun is `artifacts/evidence/T-2026-10-01-114-verifier-unit-test-forced.txt`; its test XML records 52 tests, 0 failures, 0 errors, 0 skipped.
- Regression evidence: `artifacts/evidence/T-2026-10-01-114-presenter-iteration-1.txt` records the duplicate-read regression caught during implementation; the final suite rerun passes.

### REFACTOR — optional
- Cleanup: No additional refactor beyond the minimal read-before-publish change.
- Regression evidence: The final targeted test XML and independent verifier rerun remain green.

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: 无例外；按 TDD 执行。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 先确认测试能观测到消息首次出现与 IME 请求，避免只断言最终数据库有记录。
- 估算生产改动 140 行；超出 200 行时先拆任务，不扩大本任务。

## Verification report

- Verdict: BLOCKED — local checks pass; Magic8 Pro Air / MagicOS 11 acceptance is unavailable.
- Evidence: RED details remain in this task record; the original RED console output was not retained. GREEN and independent forced rerun: `artifacts/evidence/T-2026-10-01-114-green-chatpresenter-iteration-2.txt`, `artifacts/evidence/T-2026-10-01-114-verifier-unit-test-forced.txt`, and `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.ChatPresenterTest.xml` (52 tests, 0 failures, 0 errors, 0 skipped). Lint: `artifacts/evidence/T-2026-10-01-114-android-lint.txt` (EXITCODE=0). Java-only guard: `artifacts/evidence/T-2026-10-01-114-android-no-kotlin.txt` and independent rerun `artifacts/evidence/T-2026-10-01-114-verifier-java-only-guard.txt` (VERDICT: PASS). Device mismatch: `artifacts/evidence/T-2026-10-01-114-target-device-check.txt` (TargetMatch=false; no APK installed).
- Changed/deleted assertion rationale and equivalent behavior coverage: No existing assertion was removed. `readBeforePublish` prevents a redundant trailing read that caused the iteration-1 read-count regression; accepted/refused keyboard requests and duplicate in-flight send prevention have presenter coverage.
- Gaps: Physical IME dismissal and the exact before-first-AI-response sequence are unverified on the target phone; accepted user-message retention through an actual generation failure is not directly asserted in this task. RED's raw console log is also unavailable, so its result is preserved only as the contemporaneous task record.
- Risks: A local sink assertion proves a keyboard-dismiss request, not the OEM keyboard's physical behavior. The connected HONOR ALP-AN00 / MagicOS 7.2.0.106 / Android 13 is not a substitute for the requested target.

## Handoff notes

- Completed: 本地接受发送时序修复、定向单测、Android lint、Java-only guard 与 Android 当前态文档更新。
- In progress: 等待目标机操作验收，并补齐生成失败保留用户问题的直接验证。
- Blocker: 连接设备为 HONOR ALP-AN00 / MagicOS 7.2.0.106 / Android 13，不是 Magic8 Pro Air / MagicOS 11；未在错机安装 APK。
- Unblock condition: 接入 Magic8 Pro Air / MagicOS 11 并复现标准与分支发送路径。
- Next steps: 实测仅 accepted 才收起 IME、用户问题先于 AI 首次响应且只出现一次；覆盖拒绝、持久化失败、生成失败与快速重复点击，然后更新接受项并进入 T120 回归矩阵。
- Watch-outs: Presenter 单测只证明请求了键盘隐藏；不要把其他 HONOR 设备结果或屏幕外推断当成目标机证据。
