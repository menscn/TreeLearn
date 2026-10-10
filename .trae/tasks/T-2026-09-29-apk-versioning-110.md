# Treelearn Harness Task: APK three-part versioning

id: T-2026-09-29-110
title: APK three-part versioning
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 90
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

Parent summary: The Android APK needs a single three-part version source and repeatable major/minor/patch bump rules.

- Problem/background: `app/build.gradle` currently hard-codes `versionCode 1` and `versionName '1.0'`, so the APK has no three-part release identity or safe bump workflow.
- Users/scenarios: The project owner builds or installs a Debug/Release APK and needs to know whether a change is a bug fix, small feature, or major update.
- Expected outcome: The initial APK is `1.0.0`; patch bumps increment the third component, minor bumps increment the second and reset patch, and major bumps increment the first and reset the other two.
- Core flow and reversibility: Edit one tracked version source or run the bump script -> Gradle reads and validates it -> APK exposes `versionName` and a monotonic numeric `versionCode`; every bump is a plain text diff and can be reverted.
- Inputs/outputs: `1.0.0` version source; APK `versionName` plus derived `versionCode`; `show`, `patch`, `minor`, and `major` script actions.
- State and exception handling: Reject malformed versions, negative values, leading-zero components, and unknown bump actions; do not write a partial file.
- Constraints and trade-offs: Java-only Android source; no new runtime dependency; the version source must be reviewable and usable by Gradle and Windows PowerShell.
- AI recommendation and rationale: Use `android/version.properties` as the single source, derive `versionName` and `versionCode` in Gradle, and provide an ASCII-only PowerShell bump script for Windows reliability.
- User choice and custom additions: User specified the exact semantic bump rules and starting version `1.0.0`.
- AI-autonomous decisions: Use `major * 1000000 + minor * 1000 + patch` for Android `versionCode`, preserving numeric ordering for the documented three-part range.
- User-confirmation decisions: User confirmed the three-part version semantics in the request on 2026-09-29.
- Acceptance evidence: JVM BuildConfig test, Gradle version print task, PowerShell show/bump checks, APK install, Java-only guard, and device startup.
- Facts: `android/app/build.gradle` owns the current hard-coded version; `android/README.md` documents build and release commands.
- Assumptions: No external release service rewrites the APK version after Gradle packaging.
- Unknowns: The eventual maximum component values are not specified; the implementation documents the derived code range.
- Risks: Manual edits to `version.properties` can still bypass the bump script, so Gradle validation must fail closed.
- Deferred: No Play Store metadata, signing automation, or remote release pipeline changes.
- Non-goals: Do not change application id, package name, signing, or runtime UI.
- Skipped non-critical questions and reason: The version source and bump semantics are explicit, so no additional product decision is needed.

## Goal

Make every APK expose a validated three-part version beginning at `1.0.0`, with reproducible patch/minor/major bump commands.

## Scope

### In scope
- Tracked version source and Gradle validation/injection.
- Derived numeric `versionCode` and `printVersion` Gradle task.
- Windows PowerShell version bump script and documentation.
- JVM, build, APK, guard, and startup verification.

### Out of scope
- Store publishing, signing, changelog generation, or runtime version display.
- Backend/frontend version contracts.

### Behavior impact - applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: Replace `1.0`/`1` hard-coded metadata with `1.0.0` and a derived monotonic code; preserve application id, build types, signing, and install behavior.
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `android/version.properties` -> `android/app/build.gradle` -> Gradle BuildConfig/manifest APK metadata; `android/scripts/version.ps1` writes only the same source.
- Native bridge or independent-client compatibility/update requirements: N/A; package id and runtime APIs are unchanged.
- Behavior-neutral non-applicability reason, if applicable: N/A; APK metadata intentionally changes.

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: APK version starts at `1.0.0`; version code is derived from the three components; bump commands are available from `android/`.
- Authoritative code/config/migration evidence: `android/version.properties`, `android/app/build.gradle`, and `android/scripts/version.ps1`.
- Documentation surfaces checked: root `README.md`, `android/README.md`, Gradle build commands, and release signing notes.
- Documentation files updated: `android/README.md` and root `README.md` if the root command section needs the version workflow.
- Producer/consumer/sibling/compatibility paths checked: Debug/Release Gradle variants, `BuildConfig`, APK packaging, install command, and startup entrypoint.
- Superseded phrases/examples reverse-searched: `versionName '1.0'`, `versionCode 1`, and any old version command examples.
- `documentation impact: none` reason and evidence, if applicable: N/A.
- Historical/PRD handling: Historical task/report version strings are not current APK metadata and remain unchanged.

## Acceptance criteria

- [x] Fresh build reports `versionName=1.0.0` and derived `versionCode=1000000`.
- [x] Patch/minor/major bump commands produce `1.0.1`, `1.1.0`, and `2.0.0` from `1.0.0`; leading-zero and malformed input fail without partial writes.
- [x] Debug APK installs, exposes the expected metadata, passes Java-only guard, and cold-starts without a crash.

## TDD execution record

### RED - failing test first
- Test/scenario: `VersionSourceTest` must find the tracked three-part version source and verify the initial value `1.0.0`.
- Command: `.\\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.VersionSourceTest --no-daemon --console=plain`
- Exit code: 1
- Observed failure: The test failed before implementation because `android/version.properties` did not exist.
- Failure reason validated: The failure was caused by the missing version source, not by compilation or device setup.

### GREEN - minimum implementation
- Implementation: Added `android/version.properties`, Gradle parsing and fail-closed validation, derived `versionCode`, `:app:printVersion`, the ASCII-only `android/scripts/version.ps1` bump workflow, documentation, and `VersionSourceTest`.
- Command: `.\\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.VersionSourceTest --no-daemon --console=plain`
- Exit code: 0
- Passing evidence: Gradle printed `versionName=1.0.0` and `versionCode=1000000`; `VersionSourceTest` and `assembleDebug` completed with `BUILD SUCCESSFUL`.
- Regression evidence: Temporary version-source checks produced `1.0.1`, `1.1.0`, and `2.0.0`; `01.0.0` was rejected with exit code 1 and its file contents were preserved. The PowerShell parser check reported `VERSION_PS1_PARSE=PASS`.

### REFACTOR - optional
- Cleanup: Tightened both Gradle and PowerShell validation to reject leading-zero components while allowing the literal zero component.
- Regression evidence: The valid source still prints `1.0.0 / 1000000`, and the debug APK rebuilt successfully after the validation tightening.

### TDD or sizing exception - only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- Keep the tracked version source as one line so a release diff makes the semantic bump obvious.
- Keep the PowerShell script ASCII-only because Windows PowerShell 5.1 can misread UTF-8 without BOM.
- Gradle must validate before configuring `defaultConfig`; malformed release metadata must fail the build rather than silently produce an unexpected APK version.

## Verification report

- Verdict: **VERIFIED** - all three acceptance criteria passed on 2026-09-29.
- Evidence: `:app:printVersion`, `VersionSourceTest`, and `assembleDebug` exited 0; `aapt2 dump badging android/app/build/outputs/apk/debug/app-debug.apk` reported `versionCode='1000000' versionName='1.0.0'`; `:app:installDebug` installed on `ALP-AN00 - 13`; `adb shell dumpsys package com.treelearn` reported the same metadata; cold start of `com.treelearn/.app.MainActivity` exited 0 and the logcat crash check passed; `scripts/no-kotlin-check.ps1` reported `VERDICT: PASS (13 OK line(s))`.
- Changed/deleted assertion rationale and equivalent behavior coverage: The tracked source test protects the initial version, the bump script checks cover all three semantic increments plus invalid-input preservation, Gradle owns the APK metadata derivation, and the APK/device checks verify packaging and runtime startup.
- Gaps: Store publishing, release signing, changelog generation, and runtime version display remain out of scope.
- Risks: Manual edits can still be attempted, but Gradle and the bump script now reject malformed, negative, leading-zero, and over-999 components before producing a versioned APK.

## Handoff notes

- Completed: Version source, Gradle metadata derivation, bump workflow, documentation, JVM/build checks, APK metadata inspection, device installation, cold start, and Java-only guard.
- In progress: None.
- Blocker: None.
- Unblock condition: N/A.
- Next steps: User may review and commit/push the existing working-tree changes; no commit or push was performed by the agent.
- Watch-outs: Do not use the supplied API key; do not commit or push automatically; preserve Java-only Android rules.
