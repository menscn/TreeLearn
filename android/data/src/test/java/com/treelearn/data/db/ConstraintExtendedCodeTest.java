package com.treelearn.data.db;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pins the one reading rule every constraint classification depends on, against the <em>measured</em>
 * text of both environments this project runs instrumentation tests on (T-2026-09-22-060).
 *
 * <p><strong>Why two spellings and not one.</strong> Go back one acceptance cycle and the mistake is
 * visible in the evidence rather than in the code: the original probe
 * ({@code artifacts/evidence/T-004-constraint-code-probe.txt}) ran on
 * {@code emulator-5554 / Medium_Phone_API_36.1} only, and that release spells the code as
 * {@code (code 1299 ...)}. Every instrumented assertion written against that probe therefore passed
 * on the emulator while a real Android 13 device, which spells the same failure as
 * {@code (Sqlite code 1299 ...)}, was read as "carries no code" — so a duplicate topic id was reported
 * to the user as a plain storage failure. The samples below are the two measured texts, verbatim.
 *
 * <p>The expected codes are written as literals here on purpose. Sharing the <em>reading</em> with
 * production is what makes the two never drift; sharing the <em>value</em> would turn these
 * assertions into a mirror of the implementation, which would then agree with it even when both are
 * wrong.
 */
public final class ConstraintExtendedCodeTest {

    /** Measured on API 36 ({@code Medium_Phone_API_36.1}): the code follows a {@code (code } prefix. */
    private static final String API_36_UNIQUE =
            "UNIQUE constraint failed: generation_operations.branch_id"
                    + " (code 2067 SQLITE_CONSTRAINT_UNIQUE)";

    /** Measured on Android 13 ({@code ALP-AN00}): the same refusal, spelled by that release. */
    private static final String ANDROID_13_UNIQUE =
            "UNIQUE constraint failed: generation_operations.branch_id"
                    + " (Sqlite code 2067 SQLITE_CONSTRAINT_UNIQUE),"
                    + " (OS error - 2:No such file or directory)";

    /** Measured on API 36: a NOT NULL refusal, so the reading is pinned at more than one code. */
    private static final String API_36_NOT_NULL =
            "NOT NULL constraint failed: context_snapshots.digest"
                    + " (code 1299 SQLITE_CONSTRAINT_NOTNULL)";

    /** The same NOT NULL refusal as the Android 13 device reports it. */
    private static final String ANDROID_13_NOT_NULL =
            "NOT NULL constraint failed: context_snapshots.digest"
                    + " (Sqlite code 1299 SQLITE_CONSTRAINT_NOTNULL),"
                    + " (OS error - 2:No such file or directory)";

    @Test
    public void theSameRefusalReadsTheSameCodeInBothSpellings() {
        assertEquals("the API 36 spelling", 2067, ConstraintExtendedCode.of(API_36_UNIQUE));
        assertEquals("the Android 13 spelling", 2067, ConstraintExtendedCode.of(ANDROID_13_UNIQUE));
        assertEquals("so the two environments cannot disagree about the code",
                ConstraintExtendedCode.of(API_36_UNIQUE),
                ConstraintExtendedCode.of(ANDROID_13_UNIQUE));
    }

    @Test
    public void everyMeasuredCodeSurvivesTheAndroid13Spelling() {
        assertEquals("API 36", 1299, ConstraintExtendedCode.of(API_36_NOT_NULL));
        assertEquals("Android 13", 1299, ConstraintExtendedCode.of(ANDROID_13_NOT_NULL));
    }

    @Test
    public void aTextCarryingNoCodeReadsAsNoneRatherThanAsACode() {
        assertEquals("no code at all", ConstraintExtendedCode.NONE,
                ConstraintExtendedCode.of("FOREIGN KEY constraint failed"));
        assertEquals("the trailing OS detail carries none either", ConstraintExtendedCode.NONE,
                ConstraintExtendedCode.of("(OS error - 2:No such file or directory)"));
    }
}
