# Filename: T-2026-09-29-selection-flow-responsiveness-107.md
# Treelearn Harness Task: 对话文本选中与高亮反馈提速

id: T-2026-09-29-107
title: 对话文本选中与高亮反馈提速
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-28-chat-highlight-annotation-025.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 260
shared_code_touched: true
depends_on: [T-2026-09-28-annot-menu-prose-highlight-027.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: T-027 已完成六格菜单与 prose 高亮基础链路，本任务只收口选中触发和高亮反馈速度。

- Problem/background: 长按触发不灵敏；第二次点击高亮后要等待一至两秒才显示。
- Users/scenarios: Android 对话页稳定回答 prose 文本的阅读、选中、高亮场景。
- Expected outcome: 约 300ms 长按触发且可被移动取消；高亮点击后立即显示，持久化失败可回滚。
- Core flow and reversibility: 短延迟候选 → 词选中 → 现有 ActionMode；高亮即时投影 → 后台持久化 → 权威读回确认或回滚。
- Inputs/outputs: 触点坐标、当前文本、当前 marks、选区范围；输出为选区菜单、黄色 span、SQLite 记录或既有存储提示。
- State and exception handling: 移动/松手/取消/页面销毁取消长按；写入失败不保留假高亮。
- Constraints and trade-offs: Java-only；不改成单击选词；不改菜单结构、schema 或 API；SQLite 保持唯一权威。
- AI recommendation and rationale: 使用短延迟可取消触摸桥与 optimistic projection，改动小且保留既有平台句柄和数据边界。
- User choice and custom additions: 用户确认保留长按语义，不改成单击选词。
- AI-autonomous decisions: 具体延迟、词边界、预览对象生成、设备等待窗口。
- User-confirmation decisions: 2026-09-29 确认推荐方案。
- Acceptance evidence: JVM 纯逻辑测试、真实设备长按回归、高亮即时反馈与失败回滚回归、编译/Lint/Java-only guard。
- Facts: `ChatScreen` 使用 selectable TextView 与 `LinkMovementMethod`；`ChatPresenter.onHighlightPressed` 当前先后台执行再触发读回；`Highlighter.repaint` 已有独立 span flush。
- Assumptions: 现有稳定回答文本和 marks 在操作开始时仍是当前可见版本。
- Unknowns: 不同设备对短延迟触摸桥与 ClickableSpan 的细节差异。
- Risks: 触摸桥误吞滚动或高亮点击；optimistic projection 与权威读回短暂不一致。
- Deferred: 表格单元格选中、高亮动画和批注编辑。
- Non-goals: 不改后端、不改 SQLite schema、不改菜单六格信息架构、不新增遥测。
- Skipped non-critical questions and reason: 是否使用单击选词已由用户明确否决，不再扩展讨论。

## Goal

让对话 prose 文本更容易长按选中，并让高亮反馈从“等待读回”变成“先显示后落库”，同时保留可取消、可回滚和 SQLite 权威边界。

## Scope

### In scope
- `ChatScreen.Row` 的 prose TextView 短延迟长按候选、移动取消、词选中和 ActionMode 启动。
- `Highlighter`/`ChatScreen`/`ChatPresenter` 的高亮即时投影与失败回滚路径。
- JVM 与真实设备回归测试、安装验证和文档同步。

### Out of scope
- 表格单元格选中与高亮。
- 单击普通文本选词。
- 数据库 schema、后端接口、菜单结构和颜色 token 改动。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 长按更快但仍需按住；拖动选择、滚动、ClickableSpan 点击和现有 ActionMode 菜单保持原语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): prose TextView 的触摸入口 → `SelectionMenuCallback` → `WordMenuPopup`；高亮格 → `ChatPresenter.onHighlightPressed` → `ChatWiring.applyHighlight` → `SqliteChatStore.applyHighlight`。
- Native bridge or independent-client compatibility/update requirements: N/A，Android 本地 View 链路，无外部客户端。
- Behavior-neutral non-applicability reason, if applicable: 不涉及 API、跨端契约或数据库结构。

## Documentation impact record

- Changed runtime facts: 长按触发策略和高亮反馈状态发生变化。
- Authoritative code/config/migration evidence: `ChatScreen.java`、`Highlighter.java`、`ChatPresenter.java`、现有 Annotation store 路径。
- Documentation surfaces checked: 本任务意图、规格、父任务 T-027 的交接记录。
- Documentation files updated: `Docs/intent/INTENT-2026-09-29-selection-flow-responsiveness.md`、`Docs/spec/SPEC-2026-09-29-selection-flow-responsiveness.md`、本任务文件。
- Producer/consumer/sibling/compatibility paths checked: `WordMenuPopup`、`SelectionMenuCallback`、`Highlighter.OnHighlightTapped`。
- Superseded phrases/examples reverse-searched: 现有“等待 read 后绘制”和“完全依赖系统长按”的实现描述将在完成记录中改为当前策略。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 父任务 T-027 保留历史验收事实，本任务只追加体验优化事实。

## Acceptance criteria

- [x] 真机：稳定回答 prose 连续长按多次，约 300ms 内出现选区与六格菜单；移动取消不会误触发，滚动不被吞。
- [x] 真机：点击高亮后黄色 span 立即出现，不等待 SQLite 读回；成功读回后保持一致。
- [x] 回归：高亮持久化失败时，optimistic 黄色状态被权威读回清除并出现既有存储提示。
- [x] 回归：高亮 ClickableSpan 二次点击、拖动选择、普通复制和全选仍可用。
- [x] 构建：JVM 测试、定向 instrumentation、`assembleDebug`、`lintDebug`、Java-only guard、依赖方向检查通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `WordMenuPopupInstrumentedTest.physicalLongPressKeepsSelectionAndMenuAfterGestureReturns` shortened below the platform long-press timeout, plus `highlightAppearsBeforeReadback` asserting an immediate yellow span after the menu click.
- Command: `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.WordMenuPopupInstrumentedTest' --no-build-cache --no-daemon --console=plain`
- Exit code: 1
- Observed failure: After the output-lock retry, the old implementation failed physicalLongPressKeepsSelectionAndMenuAfterGestureReturns and highlightAppearsBeforeReadback; both assertions failed on ALP-AN00 - 13.
- Failure reason validated: The command reached the device test runner and reported exactly two behavior failures, not a compile, install, or device-connection failure; the retry was required because the first run hit a stale utp.0.log.lck with no Java/Gradle process alive.

### GREEN — minimum implementation
- Implementation: ChatScreen.Row now schedules a cancellable 300ms selection candidate, cancels on movement/slop/up/detach, selects the touched word and starts the existing ActionMode; Highlighter.optimisticMarks projects the existing merge/cancel decision, and ChatScreen repaints that projection before dispatching persistence.
- Command: `./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.WordMenuPopupInstrumentedTest' --no-build-cache --no-daemon --console=plain`
- Exit code: 0
- Passing evidence: WordMenuPopupInstrumentedTest 5/5 on ALP-AN00 - 13, including short long press, moving-cancels, immediate highlight, six-cell menu, and render-preserves-selection; HighlighterTest 10/10.
- Regression evidence: assembleDebug, lintDebug, Java-only guard, and verifyDependencyDirection all exited 0; installed APK local/device SHA-256 matched and cold launch exited 0. Evidence bundle: artifacts/evidence/T-2026-09-29-selection-flow-responsiveness.txt.

### REFACTOR — optional
- Cleanup: 无需重构——本次只补齐覆盖，生产代码零改动（唯一写入是测试夹具 `ChatTestDoubles` 的观察能力）。
- Regression evidence: 全量 `:app:testDebugUnitTest` 86 类 / 788 例 / 0 失败。

### Follow-up regression — the refused store write (2026-09-29 晚)
- Test/scenario: `ChatPresenterTest.aRefusedHighlightLeavesNothingProjected` 与 `anAcceptedHighlightLandsThroughTheSameRead`——store 拒绝高亮写入时，权威读回必须让乐观投影消失并报存储提示；写入被接受时，同一读回必须把落库的 mark 推给屏幕。
- Fixture gap closed first: `ChatTestDoubles.RecordingChatSink.renderAnnotations` 原为空实现，权威读回在测试域**不可观测**——这是该路径此前零覆盖的真实原因。现记录 `marksRenders` 并提供 `lastMarks()`；新增 `ChatTestDoubles.readWithMarks(...)` 让一次读能携带 store 自己的 marks。
- RED (mutation): 这两条断言的是既有正确行为，直接跑即绿，单跑不构成证据。故临时删除 `ChatPresenter.java:323` 的 `sink.renderAnnotations(snapshot.annotations);` 后重跑，**恰好这两条失败**（AssertionError / NullPointerException），其余 45 条不受影响——证明它们锚在「权威回推存在」这一行为上。变异已按前备份还原，回读确认无残留。
- Command: `./gradlew :app:testDebugUnitTest --tests com.treelearn.app.ui.ChatPresenterTest --no-build-cache --no-daemon --console=plain`
- Exit code: 0（还原后）
- Passing evidence: ChatPresenterTest 47/47；全量 86 类 / 788 例 / 0 失败 / 0 error / 0 skipped；`verifyNoKotlinDex` PASS。证据 `artifacts/evidence/T-2026-09-29-highlight-rollback.txt`，日志 `artifacts/tmp/highlight-rollback-green-2026-09-29.log` 与 `-full-jvm-2026-09-29.log`。
- Scope note: 回滚被验证在 presenter 层（权威读回是 `ChatPresenter.read()` 的事实），未走 instrumentation——既有仪器夹具的 `ChatActions` 代理全返回默认值，高亮点击在其上不派发任何命令，设备侧无法注入拒绝。此边界已在证据文件中写明。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 需要先为两个体验问题建立可证伪的真机回归。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 长按触摸桥必须在移动超过系统触摸阈值、松手、取消和 View 脱离时清理回调。
- optimistic marks 只作为短暂 UI 投影，不能替代 SQLite；失败路径必须由权威读回恢复。
- 不把高亮决策复制成第二套规则，优先复用 `Highlighter.plan` 与现有 annotation merge 语义。

## Verification report

- Verdict: **VERIFIED** — 五项验收标准全部达成。两个报告体验问题（长按不灵敏、高亮延迟）已在真机修复并回归；失败回滚此前缺的显式注入回归已补齐，且其可证伪性由变异检验当场证明而非声称。
- Evidence: RED artifacts/tmp/selection-flow-red-2026-09-29.log; GREEN artifacts/tmp/selection-flow-green-2026-09-29.log; JVM artifacts/tmp/selection-flow-jvm-2026-09-29.log; 回滚专项 artifacts/tmp/highlight-rollback-green-2026-09-29.log 与 artifacts/tmp/highlight-rollback-full-jvm-2026-09-29.log; build/lint/guards under artifacts/tmp/selection-flow-*.log; combined evidence artifacts/evidence/T-2026-09-29-selection-flow-responsiveness.txt 与 artifacts/evidence/T-2026-09-29-highlight-rollback.txt。
- Changed/deleted assertion rationale and equivalent behavior coverage: the long-press test intentionally runs before the platform timeout; the immediate-highlight test checks the real BackgroundColorSpan on the rendered TextView before any presenter readback; movement-cancel protects scrolling; the rollback pair covers both the refusal and the acceptance side of the same read, so neither can pass by the read always answering empty.
- Gaps: 回滚覆盖在 presenter 层而非仪器层（既有设备夹具的 ChatActions 代理不派发命令，设备侧无法注入拒绝）；该边界与理由已在证据文件写明，不是未验证而是选定了可验证的层次。
- Risks: a vendor-specific TextView may still arbitrate ClickableSpan and short long-press differently; the implementation keeps the movement/slop cancellation path and the native ActionMode.

## Handoff notes

- Completed: 需求与轻量规格已确认；短延迟长按、移动取消、即时高亮投影、失败回滚显式回归、JVM/真机回归、构建门禁和 APK 安装全部完成。五项验收标准全绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: 父需求 T-027 剩余的真机形态确认（窄屏折叠形态、精确取消态取色）属其自身范围，不在本任务内；若要独立 verifier 串行复核，从本文件与两份证据文件入手即可。
- Watch-outs: 不要把单击选词混入本任务；不要把 optimistic projection 当成持久化成功证据——本轮补的正是这条边界；`ChatTestDoubles.RecordingChatSink` 的 `marksRenders` 现在是权威读回的唯一观测点，改动 `renderAnnotations` 的记录语义会同时影响本任务的回滚断言。
