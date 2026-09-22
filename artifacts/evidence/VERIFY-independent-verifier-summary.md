# Independent verifier pass — T-002 / T-003 / T-004 / T-021 / T-022

Generated: 2026-09-21 (UTC+8). Author: independent verifier lane (read-only on production code).

## Reproduced by me (commands + read-back evidence, all in artifacts/evidence/)

| Check | Command (from repo root unless noted) | Result | Evidence file |
| :--- | :--- | :--- | :--- |
| Task-contract validator | `python .trae/scripts/validate_tasks.py` | files=31 errors=0 warnings=5 (EXITCODE=0) | VERIFY-validate-tasks.txt |
| Java-only guard (T-002 acceptance #2) | `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\no-kotlin-check.ps1` | `VERDICT: PASS (13 OK line(s))` EXITCODE=0 | VERIFY-T002-guard.txt |
| Guard identity | `Get-FileHash android/scripts/no-kotlin-check.ps1`; line count | SHA-256 5F3FDB52F9EF8E6F0B20D016065A06F4A925F18353B5DE1AFC28D6277C7A3C32, 692 lines (task claims the same) | VERIFY-T002-verify-readback.txt |
| T-002 acceptance #1 | `cd android; .\gradlew.bat :app:assembleDebug :app:lintDebug --no-daemon --console=plain` | `BUILD SUCCESSFUL in 33s`, EXITCODE=0; lint 0 error / 12 warning | VERIFY-T002-assemble-lint.txt |
| Gradle config size claim (599 / 8) | per-file `(Get-Content).Count` over `android/**/*.gradle` (excl. build/) | sum = 599 across 8 files (matches) | VERIFY-static-facts.txt |
| `:core` purity (T-003 acceptance #4) | Select-String for `import android./androidx./java.sql/java.net/org.jetbrains.kotlin` under `android/core/src/main/java` | zero hits; `core/build.gradle` = java-library + junit only | VERIFY-static-facts.txt |
| T-003 javadoc (round-5 V2) | `cd android; .\gradlew.bat :core:javadoc` | `BUILD SUCCESSFUL`, EXITCODE=0 | VERIFY-core-javadoc.txt |
| `:core:test` (T-003 #5, T-021) | `cd android; .\gradlew.bat :core:test --rerun-tasks --no-daemon --console=plain` | `BUILD SUCCESSFUL`, EXITCODE=0; 13 suites / 154 tests / 0 fail / 0 err / 0 skip | VERIFY-core-test.txt, VERIFY-core-test-counts.txt |
| T-004 + T-022 instrumentation | `cd android; .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain` | 124 tests; **SqliteSchemaInstrumentedTest 24/24 pass**, **TopicBranchMessageDaoInstrumentedTest 15/15 pass**, AppLaunch 1/1 pass | VERIFY-instrumented.txt, VERIFY-instrumentation-classes.txt |
| T-004 sizing (458) / T-022 sizing (349) | 口径 grep -v of blanks///* per the adjudication | 458 and 349 reproduced exactly | VERIFY-static-facts.txt |
| DDL single producer (T-004) | `grep -rnE "CREATE TABLE|ALTER TABLE|CREATE (UNIQUE )?INDEX|CREATE TRIGGER|DROP ..." android --include=*.java` | only `TreeLearnSchema.java` (17 hits) | VERIFY-static-facts.txt |
| `BranchSource.restore` single call site (T-022) | Select-String over `android/data/.../dao/*.java` | 1 call site: `BranchDao.java:239` | VERIFY-static-facts.txt |
| T-021 RED numbers | `grep -c` equivalents on artifacts/evidence/T-021-red.txt | 49 col-0 `cannot find symbol`; 98 total; `49 errors` at lines 254/508; 3 test files (matches the task) | (read-back) |
| T-022 RED numbers | artifacts/evidence/T-022-red.txt | 3 + 63 = 66, `66 个错误` at line 409 (matches the task) | (read-back) |
| T-002 self-test, full mode | `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\dev\guard-selftest.ps1` | **SELFTEST FAIL (1 mismatch)**: CASE 0 only; 39/40 assertions OK (2 runs) | VERIFY-T002-guard-selftest.txt, VERIFY-T002-guard-selftest-run2.txt |
| CASE 0 root cause | manual robocopy of android/ with the script's exclusions, then `gradlew verifyNoKotlinDex` in the copy | torn copy fails (`D8: Class BranchCreation$Refusal requires its nest host BranchCreation`); **fresh copy at a quiet moment: BUILD SUCCESSFUL, verifyNoKotlinDex PASS, EXITCODE=0** | VERIFY-scratch-copy-build.txt, VERIFY-scratch-copy-run3.txt |

## Findings that are FAIL-class from evidence alone (no device needed)

- T-002: `android/scripts/dev/guard-selftest.ps1` is 570 lines (SHA-256 9D680E26...), mtime 12:38:05, i.e. **after** the cited `T-002-guard-selftest.txt` was captured (12:20:25). The task's Handoff says 467 lines. The evidence file records only the Guard/Library hashes, not the self-test's own, so it cannot be bound to the current script.
- T-003: task file still states "waiting for round 5" while `artifacts/evidence/T-003-verification-round5.md` (11:59) already returned **FAIL** with V1-V8; V1/V2 are fixed in the tree (V2 verified: `:core:javadoc` exit 0), V8 is closed by the user's adjudication, but V3 (per-file sizing table: `DomainErrorCode` 13 vs measured 18, `PromptPurpose` 35 vs measured 65), V4 (says 8 classes/83 cases and 116 library-wide; measured 95 for its 8 classes and 154 library-wide), V5 (its own T-003-green/checks files predate the N8/N9/N10 fixes) and V7 (README still copies the three exception names instead of pointing at Guard) are open.
- T-004: RED record reads "javac 自报 24 个错误（去重后 18 条：程序包...不存在 6 条 + 找不到符号 12 条)" - 6+12=18 != 24; the same evidence file's "deduped" block lists 36+6+6 (=48) while its own javac line says 24. Distinct counts measured from the file are 6 package + 18 symbol = 24.
- T-004 / T-022: the cited `T-004-tests.txt` / `T-022-tests.txt` (both regenerated 18:38-18:39 by the shared generator) quote `<testsuite ... tests="52">` while both task files quote tests="40" in the same GREEN record.
- T-021: the task file still asserts (line ~179) that the line numbers cited by the RED evidence match the current test source. They do not: RED cites `PromptSpecTest.java:23`, which is now `public class PromptSpecTest {` (RED ran pre-F3, when `PromptSpec.of` returned a value rather than a DomainResult). This is the round-2 verifier's R2-6, still uncorrected.

## Outstanding device/assemble checklist (deferred per leader directive)

- T-002 acceptance #3 (launch the first screen without a crash) — covered incidentally by my batch run (AppLaunchInstrumentedTest 1/1 pass) but not by a dedicated, uncontended launch check.
- T-004 / T-022 — already covered by my 20:49 run (24/24 and 15/15). No re-run needed unless the implementers change `:data`/`:app` again after 20:50.
- Optional: `:app:connectedDebugAndroidTest` clean full-batch run once all lanes are quiet (used only to confirm the batch total; not needed for the two tasks above).