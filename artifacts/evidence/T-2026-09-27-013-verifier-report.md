# T-2026-09-27-013 verifier report

- **Role:** Independent Android read-only verifier.
- **Scope:** Library implementation files, task acceptance/TDD records, RED/GREEN evidence, phone empty/no-result screenshots, JVM/lint/token/Java-only evidence, full connected-device XML, and `LibraryScreen` search-scope visibility.
- **Run policy:** No new tests, builds, or device runs were started. Existing artifacts and current XML were inspected only.

## Conclusion

**PARTIAL**

The phone empty state and search no-result state visually support the intended layout, and the search scope behavior is correct after state rendering: normal library rendering hides it, while `showSearch()` displays it. The task is not ready for ACCEPT because populated/archived/search-hit/tablet visual coverage is missing, the full connected XML is not green, the token result is not task-scoped and independently traceable, and the scope view is not fail-closed at XML inflation.

## Verified

- `android/app/src/main/java/com/treelearn/app/screen/LibraryScreen.java:88` hides `library_search_scope` during ordinary library rendering.
- `android/app/src/main/java/com/treelearn/app/screen/LibraryScreen.java:120` shows `library_search_scope` only when the search page is rendered; search results replace the existing `library_rows` container instead of creating a second route/container.
- `android/app/src/main/res/layout/screen_library_body.xml:26` uses the existing `touch_min` token for the search control; the segment controls at `:48-62` also have `touch_min` heights and tokenized surfaces.
- `android/app/src/main/res/layout/screen_library.xml:6` and `android/app/src/main/res/layout-sw600dp/screen_library.xml:6` share the same body; the tablet layout constrains it to `content_measure`.
- `android/app/src/main/res/layout/item_library_topic.xml:8`, `item_library_archived.xml:16`, and `item_search_hit.xml:7` use card surfaces and 44dp-equivalent touch sizing for the relevant rows/actions.
- `android/app/src/main/java/com/treelearn/app/MainActivity.java:439` keeps search inside the existing library route; no new search destination was introduced.
- `artifacts/evidence/android-library-search-shell-2026-09-27-v4.png` shows the phone normal-empty state without the scope line. `artifacts/evidence/android-library-search-empty-2026-09-27-v1.png` shows the phone no-result state with query `xyz` and the scope line visible.
- `artifacts/evidence/T-013-red-isolated.txt` records the isolated RED JVM command with `EXITCODE=1`; `T-013-green.txt` and `T-013-green-repo.txt` record the corresponding GREEN JVM command with `EXITCODE=0`.
- `artifacts/evidence/T-013-lint.txt` records successful lint runs; `T-013-guard.txt` records Java-only `VERDICT: PASS` and `EXITCODE=0`; `T-013-final-gate.txt` records unit test, lint and Android-test compilation success with `EXITCODE=0`.
- Current connected XML `android/app/build/outputs/androidTest-results/connected/debug/TEST-ALP-AN00 - 13-_app-.xml` reports 241 tests, 23 failures, 0 errors, 0 skipped.

## Gaps and risks

- **Visual coverage:** No populated in-progress topic card, completed/archived card, search-hit card, or real `sw600dp`/tablet-landscape screenshot is available. The two phone screenshots cover only normal empty and search no-result states.
- **Device gate:** The full XML is not green. Three failures are directly adjacent to this task's library surface: two BottomTabShell library route/return cases and `theLibraryListsTheStoredTopicsWithTheirOwnFields`; the task records these as fixture/time-out issues, but they prevent a clean device acceptance.
- **Token evidence traceability:** The task GREEN record claims `check_design_tokens.py` passed, but the task-scoped evidence files do not contain that command/result. Generic token artifacts exist, but the verifier cannot treat them as a T-013-specific gate without a task-scoped command record.
- **Search-scope initial state:** `screen_library_body.xml:38-42` does not set `library_search_scope` to `android:visibility="gone"`. `LibraryScreen.render()` corrects it before the normal state is shown in the captured screenshot, but a freshly inflated screen is not fail-closed to search-only visibility.
- **JVM result freshness:** The GREEN text evidence is usable and self-identifies its commands/exit codes, but the current `android/app/build/test-results/testDebugUnitTest` directory does not expose a current Library/Search class XML result; no verifier rerun was performed.

## Evidence paths

- Static evidence: `artifacts/evidence/T-2026-09-27-013-verifier-static.txt`
- This report: `artifacts/evidence/T-2026-09-27-013-verifier-report.md`
- Normal phone empty: `artifacts/evidence/android-library-search-shell-2026-09-27-v4.png`
- Search no-result phone: `artifacts/evidence/android-library-search-empty-2026-09-27-v1.png`
- Full connected XML: `android/app/build/outputs/androidTest-results/connected/debug/TEST-ALP-AN00 - 13-_app-.xml`

## Required follow-up for ACCEPT

- Add task-scoped token-guard evidence with command and `EXITCODE=0`.
- Obtain populated in-progress, completed/archived and search-hit phone screenshots plus the corresponding real `sw600dp`/tablet screenshots.
- Resolve or isolate the three library-adjacent connected-test fixture failures and produce an inspectable passing XML for the library-specific instrumentation.
- Make the search-scope view default to `GONE` at inflation, or add equivalent constructor-level fail-closed initialization, so it is visible only after entering search state.
