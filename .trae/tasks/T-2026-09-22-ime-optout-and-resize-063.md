# Treelearn Harness Task: 键盘顶起输入框：opt-out edge-to-edge + adjustResize

id: T-2026-09-22-063
title: 键盘顶起输入框：opt-out edge-to-edge + adjustResize
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-09-22-chat-defects-ime-reasoning-fork-062.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 8
shared_code_touched: false
depends_on: []
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 修复用户真机实测的三个对话页缺陷（键盘、思考区、分支上下文）；本片负责键盘遮挡。
- Problem/background: 对话页点输入框唤起输入法后，输入框不被顶起，看不到正在输入的文字。根因有二：`AndroidManifest.xml` 的 activity 未设 `windowSoftInputMode`（默认 adjustUnspecified）；`app/build.gradle` 第 35 行 targetSdk=36，Android 15+ 设备强制 edge-to-edge，而全工程没有任何 insets 处理（grep `WindowInsets`/`fitsSystemWindows` 为 0），键盘直接覆盖窗口。
- Users/scenarios: 用户在对话页输入问题时必须始终看到输入框与已输入文字。
- Expected outcome: 键盘拉起时窗口收缩（adjustResize），输入框始终位于键盘上方可见。
- Core flow and reversibility: 两行配置，删除即回滚。
- Inputs/outputs: 无新输入输出，纯窗口行为。
- State and exception handling: 不适用。
- Constraints and trade-offs: 方案 A（opt-out + adjustResize）共 2 行，放弃沉浸式——当前 UI 本按非沉浸设计（statusBarColor 已设），无观感损失；方案 B（insets 监听）改动大且零 AndroidX 下要手写 API 分支，不选。
- AI recommendation and rationale: 先 A 后观察，真机若仍有边缘问题再评估 B。
- User choice and custom additions: 用户 2026-09-22 批准本片免 TDD；验证层级为代码测试 + 用户真机自测（不用模拟器）。
- AI-autonomous decisions: `windowSoftInputMode="adjustResize"` 不带 `stateHidden`（不改变键盘自动弹起现状）。
- User-confirmation decisions: 免 TDD 已批准（2026-09-22，实现前）。
- Acceptance evidence: `assembleDebug` 成功；用户真机自测键盘行为并反馈。
- Facts: 见 parent brief Facts ①。
- Assumptions: 真机为 Android 15+。
- Unknowns: 无。
- Risks: `android:windowOptOutEdgeToEdgeEnforcement` 为 API 35+ 属性，低版本忽略属预期；statusBarColor 在 opt-out 后恢复生效，与现状一致。
- Deferred: 沉浸式 insets 适配。
- Non-goals: 不改任何布局文件；不做 insets 监听。
- Skipped non-critical questions and reason: 无。

## Goal

键盘拉起时对话页（及全 app）窗口收缩，输入框与已输入文字始终可见。

## Scope

### In scope
- `android/app/src/main/AndroidManifest.xml`：MainActivity 加 `android:windowSoftInputMode="adjustResize"`。
- `android/app/src/main/res/values/themes.xml`：`Theme.TreeLearn` 加 `<item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>`。

### Out of scope
- 任何布局/Java/依赖改动；sw600dp；其他屏的专项 insets。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是键盘弹出时窗口 resize。保留：状态栏配色与浅色图标现状、返回导航、屏幕方向行为（configChanges 不动）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 单 activity 单主题，无平台分支；所有屏共享同一窗口行为，键盘行为全局生效属预期。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 窗口软输入行为与 edge-to-edge 状态。
- Authoritative code/config/migration evidence: 上述两个文件。
- Documentation surfaces checked: `android/README.md`（如有 UI 约定段落则补一行说明）。
- Documentation files updated: `android/README.md` 视其现状补记一句。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 若 README 无对应段落则记录 none 及理由。
- Historical/PRD handling: 不涉及。

## Acceptance criteria

- [x] `assembleDebug` 成功（命令与退出码入证据）。〔GREEN 记录：BUILD SUCCESSFUL in 12s，EXITCODE=0〕
- [x] 主题与 manifest 各含目标属性（文件级断言或 grep 证据）。〔grep 命中 AndroidManifest.xml:31 `adjustResize`、themes.xml:21 `windowOptOutEdgeToEdgeEnforcement`（实现者与独立 verifier 双重 grep）〕
- [x] 用户真机自测：对话页点输入框，键盘拉起后输入框与文字可见（用户反馈记录为证据）。〔**2026-09-24 用户确认自测通过**（对话内反馈；与 065 分叉交互、066 流式观感同批确认），按此收口〕
- [x] 全量 JVM 单测无回归（`testDebugUnitTest` 退出码 0）。〔GREEN 记录 EXITCODE=0；2026-09-24 全量 check 复跑 EXITCODE=0（`P3-B4-gates-2026-09-24.txt`）〕

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用——纯配置改动，无单元测试可写。
- Command: 不适用
- Exit code: 不适用
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation
- Implementation: ① `android/app/src/main/AndroidManifest.xml`：MainActivity 增加 `android:windowSoftInputMode="adjustResize"`；② `android/app/src/main/res/values/themes.xml`：`Theme.TreeLearn` 增加 `<item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>`；③ `android/README.md` 依赖策略段补一句键盘行为说明（见 Documentation impact record）。未改任何 Java/布局/依赖文件，零 Kotlin 引入。
- Command: `cd /d/Project/Android/Treelearn/android && ./gradlew.bat assembleDebug`（BUILD SUCCESSFUL in 12s）；`cd /d/Project/Android/Treelearn/android && ./gradlew.bat testDebugUnitTest`（BUILD SUCCESSFUL in 10s）
- Exit code: assembleDebug = 0；testDebugUnitTest = 0
- Passing evidence: 属性存在性 grep（`grep -rnE "windowSoftInputMode|windowOptOutEdgeToEdgeEnforcement" android/app/src/main --include=AndroidManifest.xml --include=themes.xml`）命中两行：`AndroidManifest.xml:31: android:windowSoftInputMode="adjustResize">`、`themes.xml:21: <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>`。
- Regression evidence: `./gradlew.bat testDebugUnitTest` 全量 JVM 单测退出码 0（62 actionable tasks，全部 up-to-date；本片为纯配置改动，未触碰任何 Java 源，故无测试重编译）。no-kotlin guard 机械扫描 PASS：`find android -name "*.kt" -o -name "*.kts"`（排除 /build/）与 `grep -rn "org.jetbrains.kotlin" --include=*.gradle` 均 0 命中。

### REFACTOR — optional
- Cleanup: 无（配置类改动，无可清理项）。
- Regression evidence: 全量单测 `cd /d/Project/Android/Treelearn/android && ./gradlew.bat testDebugUnitTest`，退出码 0。

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 例外（配置类改动无法先写失败测试）
- Reason: manifest/theme 属性无业务逻辑可断言
- User approval: 用户于 2026-09-22 明确批准（实现前，非事后追认）
- Alternative verification: assembleDebug + 属性存在性 grep + 用户真机自测

## Implementation notes

- `windowOptOutEdgeToEdgeEnforcement` 是 API 35 属性，compileSdk 36 可引用；低版本设备自动忽略。
- 安装真机时 adb 须传 `D:/...` Windows 路径（Git Bash 的 `/d/...` 会被 adb 拒绝）。

## Verification report

- Verdict: **PASS**（2026-09-24 复核 #3 收口：代码与构建层此前已由 verifier 独立复核通过；唯一悬项「真机键盘行为」由用户 2026-09-24 确认自测通过）
- Evidence: manifest:31 `adjustResize`、themes.xml:21 `windowOptOutEdgeToEdgeEnforcement` 均经实现者与独立 verifier 双重 grep 命中；assembleDebug EXITCODE=0、testDebugUnitTest EXITCODE=0（任务 GREEN 记录）；2026-09-24 全量 check 复跑 EXITCODE=0（`P3-B4-gates-2026-09-24.txt`）。verifier 抽核备注（轻微缺口：本片无独立证据文件）不构成 FAIL，本片为两行配置改动、属性 grep 即机械证据。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（无测试断言变更）。
- Gaps: 已消除——真实键盘行为由用户真机自测提供并确认通过（2026-09-24）。
- Risks: opt-out 属权宜方案；未来做沉浸式时须移除并补 insets 监听。

## Handoff notes

- Completed: 2026-09-22 完成。manifest + theme 两处配置已落盘，构建与全量单测通过，README 已补键盘行为说明；余下用户真机自测。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 改两个文件 → build → 交用户真机自测。
- Watch-outs: 本片与 064–066 都跑 Gradle，串行执行。
