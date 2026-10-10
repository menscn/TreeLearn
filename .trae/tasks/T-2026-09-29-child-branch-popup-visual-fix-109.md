# Treelearn Harness Task: Child branch popup visibility and visual fix

id: T-2026-09-29-109
title: Child branch popup visibility and visual fix
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 110
shared_code_touched: false
depends_on: [T-2026-09-29-recommendation-fork-auto-send-108.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: The answer action bar child-branch button must open a readable, clickable list that is not covered by the bottom composer.

- Problem/background: The popup currently uses dropdown placement. Near the bottom of the chat, its title is visible but rows are clipped or fall into the composer area; the current white card and single-line rows also lack hierarchy.
- Users/scenarios: Android chat screen, stable answer, child-branch action, especially when the answer is in the lower half of the window.
- Expected outcome: The card prefers above-anchor placement, stays within the window, and shows branch titles and unread state while preserving row navigation.
- Core flow and reversibility: Tap child action -> read children -> measure card -> place above or below with clamping -> tap a row to dismiss and open a branch; outside tap/back only dismisses.
- Inputs/outputs: `List<ChildBranch>` in; visible card, title, unread count, and selected branch id out.
- State and exception handling: Empty lists do not open; long lists scroll inside the card; small windows clamp to a safe margin; refresh keeps existing popup lifecycle behavior.
- Constraints and trade-offs: Java-only; no schema, API, or branch-read changes; reuse Treelearn warm surface and green tokens; numeric placement must be JVM-testable.
- AI recommendation and rationale: Reuse the existing token system and the word-menu measured/manual placement pattern instead of relying on `showAsDropDown` near the bottom edge.
- User choice and custom additions: User requested Huashu-Design assistance; use the existing codebase design context, warm surface, dark green, and restrained elevation.
- AI-autonomous decisions: Keep PopupWindow; keep a controlled popup width; allow two-line branch titles; retain the green unread badge meaning.
- User-confirmation decisions: User confirmed the screenshot issue on 2026-09-29.
- Acceptance evidence: JVM placement regression, existing child-branch instrumentation test, Java-only guard, Debug build/install, and device startup check.
- Facts: `ChildBranchPopup.show` currently calls `showAsDropDown(anchor)`; the layout already has a title, ScrollView, and rows; instrumentation only asserts row presence and clicks.
- Assumptions: `showAtLocation` and `getLocationInWindow` share the host-window coordinate system.
- Unknowns: Final appearance under different font scales and split-screen sizes requires device screenshot review.
- Risks: An oversized card could cover answer content; incorrect scroll measurement could hide rows again.
- Deferred: Do not change action icons, title generation, recommendation flow, or selection fork flow.
- Non-goals: Do not refactor ChatPresenter, ChatWiring, or database reads.
- Skipped non-critical questions and reason: The user specified visibility and visual quality; existing design context is sufficient to choose the direction.

## Goal

Make the child-branch popup fully visible in the Android window, prefer avoiding the bottom composer, and preserve branch selection behavior.

## Scope

### In scope
- PopupWindow measurement, vertical flip, and window clamping.
- Child-branch card, title, row text, unread badge, and scroll hierarchy.
- JVM placement regression and existing device interaction regression.

### Out of scope
- Child-branch data reads, title generation, branch creation, and navigation contracts.
- Recommendation auto-send behavior.

### Behavior impact - applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: Replace fixed dropdown placement with safe-window placement; preserve row click, outside dismissal, refresh retention, and empty-list behavior.
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatScreen.showChildren` -> `ChildBranchPopup.show` -> `ChildBranchPopup.row`; data still comes from `ChatPresenter`/`ChatData.children`.
- Native bridge or independent-client compatibility/update requirements: N/A; local Android View/PopupWindow chain.
- Behavior-neutral non-applicability reason, if applicable: N/A; this task intentionally changes visible placement and styling.

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: Popup prefers above-anchor placement and clamps inside the window; rows scroll inside the card.
- Authoritative code/config/migration evidence: `ChildBranchPopup.java`, `popup_child_branches.xml`, `item_child_branch.xml`, related drawables and dimens.
- Documentation surfaces checked: `android/README.md`, ChatScreen/ChildBranchPopup Javadoc, and this task.
- Documentation files updated: This task; update `android/README.md` if existing behavior notes are insufficient.
- Producer/consumer/sibling/compatibility paths checked: `ChatScreen.showChildren`, `MainActivity.openChildren`, `ChildBranchPopupLifecycleInstrumentedTest`.
- Superseded phrases/examples reverse-searched: `showAsDropDown(anchor)` and the old auto-flip Javadoc.
- `documentation impact: none` reason and evidence, if applicable: N/A.
- Historical/PRD handling: PRD and prototype are visual context; production code and tokens are authoritative.

## Acceptance criteria

- [ ] Near the bottom edge, the popup is above the action bar or safely clamped and does not fall into the composer.
- [ ] Multiple branch titles, unread count, and hit targets are visible and clickable; refresh does not dismiss; outside tap still dismisses.
- [ ] Targeted JVM, existing instrumentation, Java-only guard, Debug install, and startup checks have evidence.

## TDD execution record

### RED - failing test first
- Test/scenario: Child-branch placement prefers above-anchor and clamps both axes inside the safe margin.
- Command: `.\\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.screen.ChildBranchPopupPlacementTest --no-daemon --console=plain`
- Exit code: 1
- Observed failure: javac reported missing `ChildBranchPopup.topFor` and `ChildBranchPopup.leftFor` methods in the new placement test.
- Failure reason validated: The targeted test failed before the production placement methods existed, so the failure was caused by the missing behavior rather than an unrelated test failure.

### GREEN - minimum implementation
- Implementation: Added measured PopupWindow placement with above-first vertical selection, safe-margin clamping, adaptive narrow-window width, and the card/row visual hierarchy.
- Command: `.\\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.screen.ChildBranchPopupPlacementTest --no-daemon --console=plain`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`; `:app:testDebugUnitTest` completed for `ChildBranchPopupPlacementTest`.
- Regression evidence: `:app:connectedDebugAndroidTest` completed `ChildBranchPopupLifecycleInstrumentedTest` successfully on `ALP-AN00 - 13`; device screenshot shows the card above the action row with both rows visible.

### REFACTOR - optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception - only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- Apply Huashu-Design design-context guidance: lift exact codebase tokens first, then refine card, row height, spacing, and hierarchy without inventing a second visual language.
- Keep numeric placement rules separate from PopupWindow execution so the boundary can be tested without a device.

## Verification report

- Verdict: VERIFIED
- Evidence: `:app:testDebugUnitTest --tests com.treelearn.app.screen.ChildBranchPopupPlacementTest` passed with exit code 0; `:app:installDebug` passed with exit code 0 on `ALP-AN00 - 13`; `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.ChildBranchPopupLifecycleInstrumentedTest` passed with exit code 0; `scripts/no-kotlin-check.ps1` reported `VERDICT: PASS`; cold start `com.treelearn/.app.MainActivity` reached `mCurrentFocus` with no `FATAL EXCEPTION` or `AndroidRuntime` lines.
- Changed/deleted assertion rationale and equivalent behavior coverage: Added placement assertions without changing the existing row-count, measured-hit-area, refresh-retention, and row-selection assertions.
- Gaps: Full repository task validation still reports pre-existing errors in older task files; full lint and full instrumentation are outside this targeted slice.
- Risks: Final visual quality still depends on device font scale and window mode; the tested device is `ALP-AN00 - 13`.

## Handoff notes

- Completed: Task created, RED/GREEN placement test completed, popup layout and safe-window placement implemented, device screenshot reviewed, APK installed, and final startup check completed.
- In progress: None.
- Blocker: None.
- Unblock condition: N/A.
- Next steps: User can review the installed build and commit/push the working tree when ready.
- Watch-outs: Do not use the supplied API key; do not commit or push automatically; keep Android Java-only.
