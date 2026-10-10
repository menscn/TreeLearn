# Treelearn Harness Task: 推荐问题创建分支后自动发送

id: T-2026-09-29-108
title: 推荐问题创建分支后自动发送
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 35
shared_code_touched: false
depends_on: [T-2026-09-27-home-recommendation-restyle-005.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: 首页推荐问题的普通点击继续直接发送；推荐问题的分支热区创建新分支后，自动把同一问题发送到新分支。

- Problem/background: 推荐问题的分支按钮能创建并打开子分支，但子分支没有自动发送问题，用户看到空分支，造成点击后像无响应。
- Users/scenarios: Android 对话页推荐条中，用户点击推荐问题右侧的分支入口。
- Expected outcome: 新分支创建成功并打开后，推荐问题只发送一次到新分支；普通点击和文本选区分支行为保持不变。
- Core flow and reversibility: 推荐点击 → fork 创建分支 → 打开新分支 Presenter → 复用普通 send 入口；发送拒绝时保留草稿并显示既有错误提示。
- Inputs/outputs: 推荐问题文本、推荐所属稳定回答消息；输出为子分支导航、子分支中的用户问题与生成状态。
- State and exception handling: 推荐条隐藏或问题为空时不操作；分支创建失败不发送；发送失败沿既有 ChatCommandResult 错误路径处理。
- Constraints and trade-offs: Java-only；不改数据库 schema、后端 API 或推荐文案；不把问题发送到父分支；避免新增第二套发送逻辑。
- AI recommendation and rationale: 在新分支 Presenter 就绪后调用已有普通发送路径，保留 Key、归档、解析、轮询、草稿和错误语义，避免在 fork 命令中偷偷启动生成。
- User choice and custom additions: 用户已确认“创建分支后自动发送”，普通推荐点击仍保持直接发送。
- AI-autonomous decisions: 发送复用新分支 Presenter 的既有 onRecommendationFollowed 路径；仅推荐分支入口启用自动发送。
- User-confirmation decisions: 2026-09-29 已确认自动发送行为。
- Acceptance evidence: Presenter JVM 回归覆盖自动发送、一次性发送、普通/选区 fork 不误发；编译、Java-only guard、启动检查。
- Facts: ChatPresenter.onRecommendationForked 当前只调用 fork；publish 在成功 fork 后打开分支；MainActivity.showChat 创建新 Presenter。
- Assumptions: 子分支打开后新 Presenter 会先读取推荐快照，推荐问题仍可由分支出生快照恢复。
- Unknowns: 真实设备上分支页面切换与首个生成轮询的时序，需要启动/设备证据补齐。
- Risks: 若自动发送仍使用父 Presenter，会把问题发错分支；若发送触发两次，会产生重复生成。
- Deferred: 真机视觉截图与完整设备回归不在本次定向修复内。
- Non-goals: 不修改 ChatWiring.fork 的分支快照契约，不改普通推荐点击，不改选区分支。
- Skipped non-critical questions and reason: 用户已明确行为目标，不再扩展交互选项。

## Goal

推荐问题点击分支入口后，在新分支内自动发送该问题一次，并沿用已有发送失败与生成轮询语义。

## Scope

### In scope
- 推荐分支创建成功后的新分支自动发送连接。
- Presenter JVM 回归测试与 Android 当前态文档同步。

### Out of scope
- 分支存储、快照、数据库 schema、后端 API。
- 普通推荐点击与文本选区分支的行为改造。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 推荐分支从“只创建并打开”改为“创建、打开并自动发送”；普通推荐点击仍走一次普通发送，选区分支仍只创建分支、不自动发送。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): ChatPresenter.onRecommendationForked → ChatCommands.fork → ChatPresenter.publish → ChatSink.openBranch / MainActivity.showChat → 新 Presenter 的普通发送入口。
- Native bridge or independent-client compatibility/update requirements: N/A，Android 本地 View/Presenter 链路，无外部客户端。
- Behavior-neutral non-applicability reason, if applicable: 不适用；本任务有明确用户可见行为变化。

## Documentation impact record

- Changed runtime facts: 推荐问题分支入口创建子分支后自动发送问题。
- Authoritative code/config/migration evidence: ChatPresenter.java、ChatCommands.java、ChatWiring.java、MainActivity.java。
- Documentation surfaces checked: android/README.md 对话分支行为说明。
- Documentation files updated: android/README.md。
- Producer/consumer/sibling/compatibility paths checked: 普通推荐发送、文本选区分支、推荐分支 fork、新分支 Presenter 初始化。
- Superseded phrases/examples reverse-searched: “fork 不发起生成”“新分支的第一问由学习者自己提”。
- documentation impact: none reason and evidence, if applicable: 不适用；当前态行为说明必须同步。
- Historical/PRD handling: 不改历史 PRD，仅更新当前 Android README。

## Acceptance criteria

- [ ] 推荐问题分支入口成功后，新分支自动调用一次发送，发送文本与推荐问题一致。
- [ ] 分支创建失败、发送拒绝、推荐条隐藏/空问题均不产生错误发送或重复发送。
- [ ] 普通推荐点击仍只发送一次；文本选区分支仍只创建分支、不自动发送。
- [ ] ChatCommands/README 与生产调用链一致，未改变分支快照和数据库契约。
- [ ] 定向 JVM 测试、Android Java-only guard、适用启动检查结果如实记录；未运行的设备/全量验证不得宣称 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: ChatPresenterTest.aRecommendationForkCarriesItsQuestionIntoTheNewBranch 与 ChatPresenterTest.aNewBranchCanAutoSendItsInitialQuestionThroughTheNormalSendPath。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.ChatPresenterTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: ChatSink 尚无携带首问的 openBranch 方法，ChatPresenter 尚无带初始问题的 start 重载，测试编译失败 2 处。
- Failure reason validated: 失败直接对应缺失的分支首问传递与新分支普通发送连接；既有推荐普通点击与 fork 测试未被修改。

### GREEN — minimum implementation
- Implementation: ChatSink 新增带 initialQuestion 的默认分支入口；ChatPresenter 在推荐 fork 成功后把问题传给宿主，MainActivity 在新 Presenter 首次读取完成后复用普通 send 路径自动发送；发送拒绝时先保留问题为草稿；普通/选区 fork 仍走无首问入口。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.ChatPresenterTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL；目标 ChatPresenterTest 完成编译与执行，新增分支首问传递、自动发送、拒绝保留草稿三条回归通过。
- Regression evidence: powershell -NoProfile -ExecutionPolicy Bypass -File scripts/no-kotlin-check.ps1 退出码 0；:app:installDebug 退出码 0 并安装到 ALP-AN00 - 13；RecommendationForkHotZoneInstrumentedTest 退出码 0。

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: none
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 自动发送必须在新分支 Presenter 上执行，不能在父分支的 fork 命令中直接 send。
- 用户提供的 API key 不参与测试、代码、日志或证据。

## Verification report

- Verdict: PARTIAL
- Evidence: 定向 JVM 测试、Java-only guard、Debug APK 安装、MainActivity 冷启动及推荐分支热区仪器测试均有成功证据；冷启动后 dumpsys 显示 com.treelearn/.app.MainActivity 为 ResumedActivity，最近 300 行 logcat 未出现 FATAL EXCEPTION 或 AndroidRuntime 崩溃。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除既有断言；新增断言覆盖推荐 fork 携带问题、新分支只发送一次、发送拒绝保留草稿；既有普通推荐发送与文本选区 fork 测试继续保留。
- Gaps: 按用户此前要求未运行全量 lint、全量 instrumentation 与独立 Harness verifier；任务校验器仍有仓库既有 16 errors、8 warnings，不能据此报告全仓 PASS。
- Risks: 真实 provider/网络生成链路未用用户 API key 验证；本次未记录推荐问题最终服务端生成结果。

## Handoff notes

- Completed: 已完成 RED/GREEN、定向 JVM 回归、Java-only guard、Debug 安装、冷启动与推荐分支热区仪器回归。
- In progress: 等待独立 Harness verifier 与用户在设备上确认推荐分支自动发送体验。
- Blocker: none
- Unblock condition: N/A
- Next steps: 如需完整交付，再单独运行全量 lint/instrumentation 与独立 Harness verifier；当前不自动扩大验证范围。
- Watch-outs: 不要把自动发送接到父分支；不要让普通/选区 fork 误触发发送。
