# Treelearn Harness Task: 长回答暂停自动滚动并恢复跟随

id: T-2026-10-01-117
title: 长回答暂停自动滚动并恢复跟随
template_version: 2
task_type: implementation
status: blocked
parent_requirement: T-2026-10-01-android-daily-use-issues-111.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 170
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 的 #6 要求长回答超过一屏后停止自动滚动，并提供向下按钮恢复跟随。

- Problem/background: 生成中的长回答持续将视口带到末尾，用户无法停留阅读前文。
- Users/scenarios: Android 对话页接收超过当前可视区域的流式回答，用户阅读中段或希望重新跟随最新输出。
- Expected outcome: 回答内容超出一屏后自动跟随暂停并出现向下按钮；用户滚动进入手动阅读；点击按钮跳到最新位置并恢复自动跟随。
- Core flow and reversibility: 短回答维持当前跟随 → 内容溢出一屏后暂停 → 用户手动滚动 → 按向下按钮 → 回到底部并跟随后续流式增量。
- Inputs/outputs: 内容高度、视口高度、用户触摸滚动和向下按钮点击；输出为明确的 follow/manual 状态及滚动位置。
- State and exception handling: 新消息/列表重绘不能意外夺回手动位置；无溢出时不显示无用按钮；切换分支按当前页面初始化跟随状态。
- Constraints and trade-offs: Java-only；不改变回答内容或流式顺序；状态不得只由一次 scroll callback 推断。
- AI recommendation and rationale: 将内容是否超屏与用户是否手动滚动分开建模，避免列表刷新覆盖用户阅读位置。
- User choice and custom additions: 用户明确要求超过一屏后不自动滚动、向下按钮恢复、手势可进入手动滚动。
- AI-autonomous decisions: “手动滚动”按用户实际拖动/滚动内容区域解释；仅轻触但未改变滚动位置不强制切换模式。
- User-confirmation decisions: 超屏默认暂停自动跟随并提供显式恢复入口。
- Acceptance evidence: ChatScreen 定向测试（超屏、手势、按钮、后续增量）与 MagicOS 11 实机长回答观察。
- Facts: `ChatScreen` 已存在 follow-latest/scroll 逻辑，但触发条件与本次明确交互不同。
- Assumptions: 可通过可控测试内容生成超过屏幕高度的流。
- Unknowns: 当前向下按钮是否可复用、视口与回答高度如何在现有布局中取得。
- Risks: 状态竞争可能造成突然跳到底部或按钮残留。
- Deferred: 非聊天列表的滚动策略不在本任务。
- Non-goals: 调整字体/回答排版、增加分页、修改生成协议。
- Skipped non-critical questions and reason: 无；暂停与恢复行为由用户明确给出。

## Goal

长回答超出一屏时停止自动滚动并显示向下按钮；用户滚动可手动阅读，点按钮后跳到末尾并恢复跟随。

## Scope

### In scope
- 梳理 `ChatScreen` 的列表增量渲染、自动跟随与现有向下控件状态。
- 用定向测试覆盖短回答、超屏暂停、手动滚动、恢复跟随后新增内容及分支切换。
- 在 MagicOS 11 实机验证长流式回答阅读，不丢增量、不产生跳动。

### Out of scope
- 改变消息/回答内容、分页、流式 API 或其他页面滚动策略。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 超过视口高度后停止自动带屏；保留回答顺序与实时更新；向下按钮恢复末尾跟随；手动滚动不被列表重绘抢回。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生成增量/消息行 → `ChatScreen.renderMessages`/滚动容器 → follow/manual 状态和向下按钮；历史加载与切分支需回归。
- Native bridge or independent-client compatibility/update requirements: N/A — 不改桥接或契约。
- Behavior-neutral non-applicability reason, if applicable: N/A — 修改可见滚动行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 长回答超屏后的默认滚动模式及恢复入口。
- Authoritative code/config/migration evidence: `ChatScreen.java` 的 `checkFollowOverflow`、`pauseFollowForManualScroll`、`reattachFollowLatest`；`FollowScrollView.java` 的触摸分发；`screen_chat_body.xml` 的滚动容器；定向仪器测试。
- Documentation surfaces checked: Android 对话页交互事实与长回答跟随/回到最新入口。
- Documentation files updated: `android/README.md` 的「这一层的交互事实」新增 T-117 长回答跟随说明。
- Documentation validation evidence: `git diff --check` exit 0（`artifacts/evidence/T-2026-10-01-117-doc-diff-check.txt:8`）；UTF-8/替换字符/占位符/尾空格检查通过（`artifacts/evidence/T-2026-10-01-117-doc-text-integrity.txt:5-6`）；本地 Markdown 链接 3 条、断链 0（`artifacts/evidence/T-2026-10-01-117-doc-links.txt:5-6`）。
- Producer/consumer/sibling/compatibility paths checked: `renderMessages` 的增量布局、滚动容器与 `chat_follow`；`ChatPresenter.onFollowLatestPaused` / `onFollowLatestPressed`；历史阅读位置、分支切换与短回答回归。
- Superseded phrases/examples reverse-searched: “自动滚动”“自动跟随”“阅读位置”；结果见 `artifacts/evidence/T-2026-10-01-117-doc-reverse-search.txt`。
- `documentation impact: none` reason and evidence, if applicable: N/A — 用户操作语义变化需检查并同步文档。
- Historical/PRD handling: 不改写历史反馈记录。

## Acceptance criteria

- [x] API 36 AVD 定向测试先红后绿：回答超出视口后自动跟随暂停并显示按钮；短回答不显示多余按钮。
- [x] API 36 AVD 验证手动拖动可阅读、点击「回到最新」恢复跟随、后续流式增量可达；分支切换与历史阅读位置相关回归通过。
- [x] Android lint、Java-only guard、文档检查有证据；保守生产 diff 上界为 192 行，见 `artifacts/evidence/T-2026-10-01-117-production-line-budget.txt:10`。
- [ ] Magic8 Pro Air / MagicOS 11 实机观察；当前连接的是 HONOR ALP-AN00 / MagicOS 7.2.0.106（证据：`artifacts/evidence/T-2026-10-01-117-target-build.txt:5`），API 36 AVD 不能替代目标机。

## TDD execution record

### RED — failing test first
- Test/scenario: ChatScreenFollowModeInstrumentedTest.oversizedStreamingAnswerPausesAndShowsFollowButton 在 API 36 AVD 上渲染 80 行合成流式回答，先断言回答高度超过滚动视口，再断言超屏后恢复按钮可见。
- Command: adb -s emulator-5558 shell am instrument -w -e class com.treelearn.app.ChatScreenFollowModeInstrumentedTest com.treelearn.test/androidx.test.runner.AndroidJUnitRunner
- Exit code: 1
- Observed failure: RED 断言 an overflowing answer pauses auto-follow and exposes recovery 期望可见值 0，实际为隐藏值 8；测试 1 项、失败 1 项。ADB 返回 0，JUnit verdict 返回 1。
- Failure reason validated: 合成回答滚动内容高度大于 viewport；当前 renderFollow 仅看 presenter 的 followLatest，而最新行仍与视口相交，不会因答案超屏转为 detached，故按钮保持 GONE。证据：artifacts/evidence/T-2026-10-01-117-red.txt；测试构建证据：artifacts/evidence/T-2026-10-01-117-androidtest-build.txt。

### GREEN — minimum implementation
- Implementation: `ChatScreen` 按答案高度、有效手势和实际滚动分别维护暂停状态；`FollowScrollView` 在分发层捕获子视图接收的完整触摸序列；有效拖动立即暂停；显式按钮恢复并继续跟随增量，分支/尾消息切换重置局部状态。
- Command: adb -s emulator-5558 shell am instrument -w -e class com.treelearn.app.ChatScreenFollowModeInstrumentedTest com.treelearn.test/androidx.test.runner.AndroidJUnitRunner
- Exit code: 0
- Passing evidence: 最新 API 36 AVD 定向运行通过 6 项 UI 测试（`artifacts/evidence/T-2026-10-01-117-manual-reading-stream-growth-instrumentation.txt:12`）且 JUnit verdict 为 0（`:16`）；新 APK 安装证据见 `artifacts/evidence/T-2026-10-01-117-manual-reading-stream-growth-install.txt:9-13`。
- Regression evidence: 新增 `manualReadingSurvivesLaterStreamGrowth` 验证手动阅读时流式回答继续增长不会把视口拉回末尾；`ChatFollowPolicyTest` 与 APK 重建见 `artifacts/evidence/T-2026-10-01-117-manual-reading-stream-growth-build.txt:162-164`；历史位置/Presenter 回归见 `artifacts/evidence/T-2026-10-01-117-reading-regression-tests.txt:90-92`；lint 见 `artifacts/evidence/T-2026-10-01-117-android-lint.txt:156-158`；Java-only 见 `artifacts/evidence/T-2026-10-01-117-android-no-kotlin.txt:34-35`。

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: 无例外；按 TDD 执行。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 按内容溢出视口触发暂停；不要把“用户先滚动”设为唯一暂停条件。
- 触摸判断要求超过系统 touch slop 且内容偏移实际改变；在 `dispatchTouchEvent` 捕获完整序列，避免把分支重绘误判为用户滚动，并在有效拖动时立即通知 Presenter，避免等待惯性滚动结束才暂停。
- 开发复测曾发现分支布局偏移被误判为手势，以及普通 `OnTouchListener` 漏掉子视图起始的 `ACTION_DOWN`；修正与诊断证据分别见 `artifacts/evidence/T-2026-10-01-117-branch-switch-red.txt`、`artifacts/evidence/T-2026-10-01-117-gesture-regression-red.txt`、`artifacts/evidence/T-2026-10-01-117-dispatch-diagnostic-logcat.txt`。未删除或削弱既有行为断言。
- 保守生产 diff 上界 192 行：把共享 `ChatScreen` 的全部未提交改动、滚动 XML 改动和新 `FollowScrollView` 都计入；该上界包含其他并行任务改动，仍低于 200。计算见 `artifacts/evidence/T-2026-10-01-117-production-line-budget.txt:10`。

## Verification report

- Verdict: blocked — 独立 verifier 只读复核确认新增持续增长 UI 证据有效；Magic8 Pro Air / MagicOS 11 实机观察仍未完成。
- Evidence: 最新 API 36 AVD 六项 UI 行为通过（`artifacts/evidence/T-2026-10-01-117-manual-reading-stream-growth-instrumentation.txt:12-16`）；新定向 Presenter 单测及 APK 构建通过（`artifacts/evidence/T-2026-10-01-117-manual-reading-stream-growth-build.txt:162-164`）；阅读位置/Presenter 回归通过（`artifacts/evidence/T-2026-10-01-117-reading-regression-tests.txt:90-92`）；lint 与 Java-only 通过（`artifacts/evidence/T-2026-10-01-117-android-lint.txt:156-158`、`artifacts/evidence/T-2026-10-01-117-android-no-kotlin-final-apk.txt:35-36`）；应用在 API 36 AVD 启动并到前台（`artifacts/evidence/T-2026-10-01-117-startup-launch.txt:7-13`、`artifacts/evidence/T-2026-10-01-117-startup-ready.txt:6-8`），临时进程已停止（`artifacts/evidence/T-2026-10-01-117-startup-stop.txt`）；T-117 Task v2 校验 PASS（`artifacts/evidence/T-2026-10-01-117-task-validator-final.txt:80`），同次全仓扫描有 16 条其它历史任务错误（`artifacts/evidence/T-2026-10-01-117-task-validator-final.txt:85`），未改动其它任务。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除或放宽断言；六项仪器用例覆盖超屏、短回答、后续增量、手动拖动、持续增长时保持阅读位置与分支切换，历史位置由 `ChatReadingTest` 覆盖。
- Gaps: Magic8 Pro Air / MagicOS 11 实机长回答、手势和持续增量观察未执行；当前 HONOR ALP-AN00 / MagicOS 7.2.0.106 不匹配目标（`artifacts/evidence/T-2026-10-01-117-target-devices.txt:6`、`artifacts/evidence/T-2026-10-01-117-target-build.txt:5`）。
- Risks: MagicOS 11 对原生触摸拦截/滚动惯性的差异仍需目标机验证；不得把 AVD 结果宣称为目标机通过。

## Handoff notes

- Completed: 完成超屏暂停、手动拖动暂停、显式恢复与后续流式跟随；补充 README；定向仪器测试、阅读位置回归、lint、Java-only guard 和 AVD 启动检查均有证据。
- In progress: 独立 verifier 复核已完成；仅剩指定目标机实测。
- Blocker: 指定 Magic8 Pro Air / MagicOS 11 当前不可用，阻塞最终实机验收。
- Unblock condition: 实机接入后运行长回答溢出、手动阅读、按钮恢复及后续增量观察。
- Next steps: Magic8 Pro Air / MagicOS 11 接入后补长回答溢出、手动阅读、按钮恢复及后续增量观察，再决定是否关闭。
- Watch-outs: 不因内容重绘夺回手动阅读位置；保留工作区现有改动。
