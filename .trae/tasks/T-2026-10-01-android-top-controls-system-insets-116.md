# Treelearn Harness Task: 分支顶部控件避让系统栏

id: T-2026-10-01-116
title: 分支顶部控件避让系统栏
template_version: 2
task_type: implementation
status: blocked
parent_requirement: T-2026-10-01-android-daily-use-issues-111.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 100
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 的 #5 报告分支导航/总览顶部按钮栏未避让手机状态栏。

- Problem/background: 分支导航与总览页面的顶部控件可能被状态栏或显示区域遮挡，影响识别和点击。
- Users/scenarios: Magic8 Pro Air / MagicOS 11 上打开分支导航、总览及关联顶部工具栏。
- Expected outcome: 顶部按钮栏位于系统安全区域内，标题与点击热区可见可操作；页面内容不与状态栏重叠。
- Core flow and reversibility: 打开页面 → 检查状态栏/刘海安全区 → 点击顶部按钮 → 返回；离开页面恢复原有窗口行为。
- Inputs/outputs: 系统 WindowInsets、显示区域与页面容器；输出为安全区内的布局及稳定点击。
- State and exception handling: 状态栏 inset 为零或刘海/横屏时布局不崩；遵循当前 edge-to-edge/window 配置，不硬编码目标机像素值。
- Constraints and trade-offs: Java-only；遵循系统栏配置与现有设计尺寸，不改主题色或全局沉浸策略。
- AI recommendation and rationale: 以系统 Insets/安全区为依据调整受影响容器，避免针对 MagicOS 状态栏高度写设备常量。
- User choice and custom additions: 用户确认 Magic8 Pro Air / MagicOS 11 为目标设备。
- AI-autonomous decisions: 覆盖状态栏避让和按钮点击热区；屏幕适配至少包含目标机竖屏与项目已有平板横屏路径。
- User-confirmation decisions: 顶部控件不得被手机状态栏遮挡。
- Acceptance evidence: 定向布局/inset 测试与目标机截图/点击观察。
- Facts: `ChatScreen` 及分支导航/总览视图已有 inset 处理路径需核对；任务 109 的子分支弹窗显示/摆放是独立范围，不等于顶部安全区已通过。
- Assumptions: 使用应用当前状态栏/edge-to-edge 配置，不改变系统栏风格。
- Unknowns: 实际冲突发生的页面、窗口配置及 MagicOS inset 数值。
- Risks: 只给单个页面加 padding 可能导致横屏/弹窗布局重复补偿。
- Deferred: 其他非分支页面系统栏问题不在本任务。
- Non-goals: 全局主题/沉浸式改版、任务 109 的卡片视觉层级、增加状态栏占位的固定高度。
- Skipped non-critical questions and reason: 无；验收目标由用户明确反馈限定。

## Goal

分支导航、总览及其顶部按钮在目标设备系统安全区域内完整可见、可点击，不与状态栏重叠。

## Scope

### In scope
- 追踪相关 Activity/window 与内容容器的 WindowInsets 消费路径，修复重复或遗漏的 inset 处理。
- 对布局安全区与点击边界增加定向测试，覆盖 MagicOS 目标机和既有平板横屏形态。
- 更新 Android 系统栏/分支导航当前态说明（若其描述与修复后行为不符）。

### Out of scope
- 修改全局 edge-to-edge 策略、状态栏图标/颜色设计、无关页面布局或任务 109 的弹窗样式。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 顶部控件避让状态栏/刘海安全区；保留系统栏样式、页面导航和可点区域，不对内容重复增加 inset。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `MainActivity` window 配置 → `ChatScreen`/BranchHeading 顶部容器 → 分支导航与总览消费者；实际受影响 view 需以运行链确认。
- Native bridge or independent-client compatibility/update requirements: N/A — 无 API/桥接改动。
- Behavior-neutral non-applicability reason, if applicable: N/A — 修改布局行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 分支导航与总览顶部控件按系统安全区布局。
- Authoritative code/config/migration evidence: `MainActivity.java` window/inset 配置、实际分支导航视图 Java/XML 与布局测试。
- Documentation surfaces checked: `android/README.md` 中系统栏、方向/设备行为说明。
- Documentation files updated: 按实现后的活动行为更新相关当前态说明；无差异时记录代码证据。
- Producer/consumer/sibling/compatibility paths checked: 分支导航、总览、关联顶部按钮、弹窗入口及平板横屏。
- Superseded phrases/examples reverse-searched: 实现后反查状态栏避让/edge-to-edge 描述。
- `documentation impact: none` reason and evidence, if applicable: 若文档已准确描述 Insets 行为，记录已核对来源与证据后可不改。
- Historical/PRD handling: 不改写历史截图或设计记录。

## Acceptance criteria

- [ ] 定向测试先红后绿：顶部容器满足窗口安全区边界，无状态栏重叠及重复 inset 补偿。
- [ ] Magic8 Pro Air / MagicOS 11 实机截图与点击确认顶部按钮完整可见；项目平板横屏行为未回归。
- [ ] 相关导航/总览入口回归通过；Java-only guard 与受影响文档核对有证据，生产改动不超过 200 行。

## TDD execution record

### RED — failing test first
- Test/scenario: RED GATE ONLY — target-device availability probe; no behavioral RED was run because the reported overlap cannot be tied to a view without the requested phone.
- Command: PowerShell target-device gate: adb shell getprop ro.product.model + ro.product.marketname; require Magic8 Pro Air
- Exit code: 1
- Observed failure: The environment gate returned ALP-AN00 and an empty market name instead of Magic8 Pro Air; this is an environment blocker, not a behavioral failure.
- Failure reason validated: `artifacts/evidence/T-2026-10-01-116-target-device-gate.txt` records the exact probe inputs and `EXITCODE=1`; the target-device availability evidence confirms the connected phone runs Android 13 / MagicOS 7.2.0.106, not MagicOS 11. No behavior RED or GREEN is claimed.

### GREEN — minimum implementation
- Implementation: PENDING — 按实际 Insets 消费路径最小调整顶部容器。
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: 无例外；按 TDD 执行。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 不使用 MagicOS 固定像素高度；先核对根视图/子视图 Insets 消费者，避免双重 padding。
- 估算生产改动 100 行；若超 200 行先拆分。

## Verification report

- Verdict: BLOCKED
- Evidence: `artifacts/evidence/T-2026-10-01-target-device-availability.txt` identifies the connected phone as HONOR ALP-AN00 / MagicOS 7.2.0.106 / Android 13, not Magic8 Pro Air / MagicOS 11; no APK was installed and no target-device layout test or Java-only guard was run.
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A — implementation has not started because the reported inset conflict cannot yet be tied to a specific target-device view.
- Gaps: The affected page, actual status-bar/display-cutout insets, and whether the overlap is reproducible remain unknown.
- Risks: A speculative padding change could double-apply insets or regress tablet landscape behavior.

## Handoff notes

- Completed: Decomposed issue #5 and inspected the current WindowInsets path without changing production code.
- In progress: Blocked before implementation pending target-device reproduction.
- Blocker: Magic8 Pro Air / MagicOS 11 is unavailable; the attached probe confirms the connected device is a different HONOR model and OS build.
- Unblock condition: Connect the requested target device and reproduce the overlapping control with a screenshot and the affected screen/entry path.
- Next steps: Identify the exact view and inset producer/consumer on-device, add a falsifiable RED test, then make the smallest safe-area correction and run device, tablet-landscape, lint, and Java-only checks.
- Watch-outs: Do not hardcode status-bar height; preserve existing system-bar styling and tablet landscape behavior.
