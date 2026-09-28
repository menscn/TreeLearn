# Treelearn Harness Task: 全站纸纹与底部标签栏

id: T-2026-09-27-010
title: 全站纸纹与底部标签栏视觉复刻
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 110
shared_code_touched: true
depends_on: [T-2026-09-27-home-visual-fidelity-verification-008.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 按已接受 V1.1 原型统一 Android 外壳、底部一级标签和全站纸纹，不改路由与屏幕功能。
- User choice and custom additions: 保持系统状态栏/导航栏原生；首页已有纸纹须去重；纸纹 token 数值不变。
- Risks: `MainActivity` 正由首页动效任务涉及，因此必须等首页验证任务 008 完成；纸纹层不可遮挡内容、抽屉或触控。

## Goal

应用外壳、一级标签栏及所有现有屏幕共享原型纸色与细横线底纹，且每个视口只绘制一次。

## Scope

### In scope
- `MainActivity.java`、`activity_main.xml`、`screen_tab_bar.xml` 与生产路由根布局的背景承载；复用 `PaperRuleBackdrop` / `PaperRules`。
- 清除首页重复底纹，确保抽屉覆盖层、屏幕内容、标签栏的层级与触达不变；同步 `android/README.md` 当前外壳描述。

### Out of scope
- 页面内部卡片、业务行为、状态栏/导航栏和设计 token 数值。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改变全站底层背景与标签栏视觉；`swap()`、标签选择、抽屉开合、返回栈和屏幕点击结果保持。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `MainActivity.swap(View)` 为屏幕容器唯一换屏入口；`activity_main.xml` 持有 `shell_host`、`tab_bar` 和抽屉根；`PaperRuleBackdrop` 原由手机/平板首页布局消费。
- Native bridge or independent-client compatibility/update requirements: 无桥接变化。
- Behavior-neutral non-applicability reason, if applicable: 不适用（验证外壳交互不变）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 纸纹由首页专属变为全站外壳背景；一级标签外观调整。
- Authoritative code/config/migration evidence: `MainActivity.java`、`activity_main.xml`、`screen_tab_bar.xml`、页面根布局。
- Documentation surfaces checked: `android/README.md` 外壳、标签与首页纸纹说明；V1.1 原型共享 `.screen` 背景。
- Documentation files updated: `android/README.md`。
- Producer/consumer/sibling/compatibility paths checked: 首页手机/平板、学习库、设置、对话、统计、树、导航及设置二级屏。
- Superseded phrases/examples reverse-searched: 首页独占纸纹的当前态表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改历史报告或 PRD。

## Acceptance criteria

- [ ] 先增加回归断言；测试证明每条现有生产路由仅有一层纸纹且根布局不以不透明底色遮蔽它，首页无双绘制。
- [ ] 手机与 sw600dp 横屏截图覆盖首页、学习库、设置、二级屏和打开抽屉；背景相位与原型一致，内容/系统安全区未被遮挡。
- [ ] 底部标签仍仅在一级页面显示、点击仍切换原页面；抽屉关闭/返回行为不变，触达区域 ≥44dp。
- [ ] `android/README.md` 与实现事实一致；定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `HomeBackdropInstrumentedTest` still expected `R.id.home_backdrop` after the intended ownership moved to the shell.
- Command: `android\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac --rerun-tasks`
- Exit code: 1
- Observed failure: `HomeBackdropInstrumentedTest.java` had two `cannot find symbol: home_backdrop` errors.
- Failure reason validated: The failure was caused by the stale test contract, not by production Java or resource compilation.

### GREEN — minimum implementation
- Implementation: Added `shell_backdrop` to `activity_main.xml`, removed opaque `tl_bg` roots and the home-only backdrop, made phone/tablet tab bars transparent, migrated `HomeBackdropInstrumentedTest` to the shell contract, and added route-root regression assertions.
- Command: `android\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac --rerun-tasks`
- Exit code: 0
- Additional verification: `android\gradlew.bat :app:testDebugUnitTest --rerun`; `android\gradlew.bat :app:lintDebug`; `python -X utf8 .trae/scripts/dev/check_design_tokens.py`; `powershell -NoProfile -File android\scripts\no-kotlin-check.ps1`; targeted `adb shell am instrument -w -r -e class com.treelearn.app.HomeBackdropInstrumentedTest com.treelearn.test/androidx.test.runner.AndroidJUnitRunner` all exit 0; the full device suite exits 1.
- Passing evidence: targeted instrumentation reports 3 tests / 0 failures / 0 errors / 0 skipped; token guard reports 44/44/16 and 0 failures; Java-only reports PASS; verifier evidence is in `artifacts/evidence/T-2026-09-27-010-verifier-report.md` and `artifacts/evidence/T-2026-09-27-010-verifier-static.txt`.
- Regression evidence: Full device suite reports 239 tests / 23 failures, all outside `HomeBackdropInstrumentedTest`; failures are existing data/animation/tree/generation environment cases and are not attributed to the shell backdrop. Startup succeeded after installing `app-debug.apk` and launching `com.treelearn/.app.MainActivity` (actual class `com.treelearn.app.MainActivity`), with `mCurrentFocus` on the Activity; screenshot: `artifacts/evidence/android-shell-backdrop-2026-09-27.png`.

### REFACTOR — optional
- Cleanup: Removed the duplicate `ShellBackdropInstrumentedTest`; the existing home backdrop test now owns the shell-level structural assertions.
- Regression evidence: `git diff --check` clean for the task/documentation changes; no new dependency or token resource.

### TDD or sizing exception — only with prior user approval
- Exception type: None; TDD is required.
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 纸纹使用已有 token 派生，不新增颜色/尺寸，不在首页保留第二个绘制层。
- 保留 phone 与 sw600dp 两套既有首页布局和全部屏幕安全区行为。

## Verification report

- Verdict: PARTIAL
- Evidence: Shell structure, transparent tab bars, route-root regression assertions and backdrop token parameters pass; JVM/lint/token/Java-only/startup pass. Targeted instrumentation is 3/3 green. Phone home/drawer/statistics screenshots exist under `artifacts/evidence/`; the connected suite remains 239 tests / 23 failures because of unrelated pre-existing data/animation/tree/generation cases.
- Changed/deleted assertion rationale and equivalent behavior coverage: The old home-only backdrop assertion was replaced because ownership moved to `activity_main.xml`; the new test checks shell z-order, transparency, duplicate backdrop absence and route-root opacity.
- Gaps: A clean-data full-suite rerun and complete phone/sw600dp screen screenshot matrix are still outstanding; this task remains PARTIAL rather than verified.
- Risks: `MainActivity.java` and `android/README.md` already had uncommitted changes; the shell patch avoided changing MainActivity Java and preserved those changes.

## Handoff notes

- Completed: 全站纸纹外壳实现、旧首页纸纹测试迁移、README 更新和定向验证完成。
- In progress: 独立 verifier 已接受本轮修复；统计详情月历任务 012 已实现并进入验证。
- Blocker: 完整设备套件存在 23 条既有失败，且完整手机/平板截图矩阵未齐；不能把本任务标为 verified。
- Unblock condition: clean-data full-suite 通过或完成可复核归因，并补齐截图矩阵。
- Next steps: 保留当前补丁，继续收口统计详情任务 012，再按依赖推进其余视觉切片。
- Watch-outs: 不覆盖 008 及其首页动效/控件变更。
