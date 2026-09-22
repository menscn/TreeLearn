# Independent verifier lane — final report for T-002 / T-003 / T-004 / T-021 / T-022

Generated: 2026-09-21T21:24:16+08:00. Repo: D:\Project\Android\Treelearn (branch master).
Emulator **not used this round** (user instruction 2026-09-21): no `connectedDebugAndroidTest`, no `adb install`.
Read-only on production code / tests. Edits limited to the five task files above and new files under `artifacts/evidence/`.

## Verdict table

| Task | Verdict | Proving command + evidence | status set | Remaining blocking gaps |
| :--- | :--- | :--- | :--- | :--- |
| T-2026-09-20-002 | FAIL (record layer) | `powershell -File android/scripts/no-kotlin-check.ps1` -> VERDICT: PASS (13 OK line(s)), exit 0 (VERIFY-T002-guard.txt); `gradlew :app:assembleDebug :app:lintDebug` -> BUILD SUCCESSFUL (VERIFY-T002-assemble-lint.txt); `gradlew :app:assembleDebugAndroidTest` blocked cross-lane | verifying | guard-selftest.ps1 is 570 lines while the file says 467 and its cited evidence was captured before the script's last edit; full-mode self-test currently cannot reach 40/40; acceptance #3 deferred |
| T-2026-09-20-003 | FAIL (record layer) | `gradlew :core:test --rerun-tasks` exit 0, 13 suites / 154 tests / 0 failures (VERIFY-core-test-counts.txt); `gradlew :core:javadoc` exit 0 (VERIFY-core-javadoc.txt); per-file + test counts read back in VERIFY-static-recheck-2026-09-21.txt | verifying | round-5 FAIL (V1-V8) not recorded in the file; V3 per-file table stale (DomainErrorCode 18 vs 13, PromptPurpose 65 vs 35); V4 counts stale (8 classes 95 tests, library 154 vs stated 83/116); V5 own evidence predates N8-N10; V7 README copies the exception list; N8 grep "zero hits" now false (3 historical hits) |
| T-2026-09-20-004 | FAIL (record layer) | `gradlew :app:connectedDebugAndroidTest` (pre-instruction, 2026-09-21 20:49) -> SqliteSchemaInstrumentedTest 24/24 pass (VERIFY-instrumentation-classes.txt); sizing 458 reproduced (sizing-adjudication-2026-09-21.txt); DDL single producer (VERIFY-static-recheck-2026-09-21.txt) | verifying | RED record says 24 errors "deduped 18 = 6 + 12" but the evidence composes 6 + 18 = 24; T-004-red.txt's own "deduped" block says 36 + 6 + 6 = 48; GREEN claims "Starting 40 tests" while the cited file says 52; its self-imposed 400-line threshold needs a user ruling; acceptance #1 RED deferred |
| T-2026-09-21-021 | FAIL (record layer) | `grep` read-back of T-021-red.txt: 49 col-0 cannot-find-symbol, 98 total, 49 errors at lines 254/508 (VERIFY-static-recheck-2026-09-21.txt); `:core:test` exit 0, 154 tests | verifying | R2-6 open (RED line numbers do not match current test sources, yet the file claims they do); R2-2 open (README:200 still copies the three exception names); round-2 PARTIAL not recorded in the file; "121 cases" presented as current while the library is 154 |
| T-2026-09-21-022 | FAIL (record layer) | `rg -n 'BranchSource\.restore' android/data/src/main/java` -> exactly one production call site BranchDao.java:239 (VERIFY-static-recheck-2026-09-21.txt); device run 20:49 -> TopicBranchMessageDaoInstrumentedTest 15/15 (VERIFY-instrumentation-classes.txt); sizing 349 reproduced | verifying | GREEN cites tests="40" / "Starting 40 tests" but T-022-tests.txt says tests="52" and T-022-green.txt says "Starting 52 tests"; REFACTOR section left PENDING; acceptance #1 RED deferred |

No task was set to `verified`.

## Cross-lane interference (recorded, not a defect of these tasks)

`gradlew :app:assembleDebugAndroidTest :app:assembleDebug` fails inside files this lane did not author:

- `android/app/src/main/java/com/treelearn/app/generation/AiGatewayStreams.java:9,10` -> `程序包com.treelearn.data.secret不存在` (5 errors total, `:app:compileDebugJavaWithJavac FAILED`).

Reproduced twice: `VERIFY-batch-compile.txt` (3 attempts) and `VERIFY-assemble-androidtest-run2.txt` / `-run3.txt` (2 more attempts after 100 s waits). The instrumentation-compilation criterion is therefore **blocked by cross-lane churn**, not FAIL for T-004/T-022.

## Why the 20:49 device results still speak for the current T-004 / T-022 files

No file authored by T-004 or T-022 was modified after 2026-09-21 20:05 (latest: TopicBranchMessageDaoInstrumentedTest 20:05:36). Everything modified after the 20:49 run belongs to other lanes: `app/main/.../generation/{StreamBatch,GenerationScheduler,LiveAttempt,RecoveryScan,GenerationCoordinator,LiveAttempts}.java`, `data/main/.../db/dao/BranchCreation.java`, `app/src/test/.../FirstTopicCreationRulesTest.java`, `app/src/androidTest/.../GenerationCoordinatorInstrumentedTest.java`.

## Deferred (device-dependent) criteria

Recorded as `deferred — emulator not used this round (user instruction 2026-09-21)` — neither PASS nor FAIL:

- T-002 acceptance #3 (first screen launches without a crash).
- T-004 acceptance #1 RED half (instrumentation RED before GREEN) — GREEN half was covered on device at 20:49.
- T-022 acceptance #1 RED half — GREEN half covered on device at 20:49.

## Could not be verified, and why

- Instrumentation **compilation** for the current tree: blocked by the `AiGatewayStreams.java` / `com.treelearn.data.secret` breakage in another lane (5 consecutive failed attempts).
- `guard-selftest.ps1` full-mode 40/40: the script copies `android/` including `build/`; under concurrent writes the copy is torn (`D8: Class BranchCreation$Refusal requires its nest host`) and CASE 0 fails. One fresh copy built at a quiet moment passed (`verifyNoKotlinDex: PASS (3 APK(s))`, VERIFY-scratch-copy-run3.txt). Re-running full mode now would fail for the unrelated `:app` breakage above, so it adds no signal.
- T-003's 741 / per-file table by directory: the directory is shared and has been edited by T-021/T-022/T-007 since; only the recorded round-4 value is meaningful (as `.trae/rules/harness-tasks.md` itself states).
- T-004 RED evidence re-recording: requires both the emulator and physically moving three production classes out of the tree — impossible this round.