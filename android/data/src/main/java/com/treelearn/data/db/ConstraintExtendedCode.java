package com.treelearn.data.db;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one place an SQLite extended result code is read out of a constraint failure's text.
 *
 * <p>Android's {@code SQLiteConstraintException} exposes no accessor for the extended result code, so
 * the code can only be read out of the message text Android built for the failure. This class exists
 * because that reading is needed in two places that must never disagree: the {@code :data} call sites
 * that turn a refusal into a domain error, and the instrumentation suites in {@code :app} that assert
 * <em>which</em> constraint spoke. A second copy of the reading rule is the defect this type removes.
 *
 * <p><strong>What it does not decide.</strong> Reading a code is not naming a cause: {@code 2067} is
 * a taken revision number in one table and a second in-flight attempt in another, so the caller has
 * to combine the code with the constraint identity it expects. This class answers one question only,
 * and the expected value is always supplied by the caller rather than derived here.
 *
 * <p><strong>Why the pattern carries no prefix.</strong> The text is Android's, not SQLite's, and
 * Android's releases do not spell it the same way: API 36 writes
 * {@code UNIQUE constraint failed: ... (code 2067 SQLITE_CONSTRAINT_UNIQUE)}, while Android 13 writes
 * the same refusal as {@code ... (Sqlite code 2067 SQLITE_CONSTRAINT_UNIQUE), (OS error - 2:No such
 * file or directory)}. Both were measured rather than assumed — the first on
 * {@code Medium_Phone_API_36.1} ({@code artifacts/evidence/T-004-constraint-code-probe.txt}), the
 * second on a real {@code ALP-AN00} device ({@code artifacts/evidence/T-059-constraint-codes.txt}).
 * An earlier version of this rule matched {@code (code N} and therefore read <em>no code</em> on
 * Android 13, so a duplicate topic id reached the user as a plain storage failure. Matching only the
 * stable {@code code <digits>} fragment covers both measured spellings and any third one, which is
 * why no known prefix is enumerated here: a list of the prefixes seen so far would have to be
 * extended by whoever meets the next release, while this fragment does not.
 */
public final class ConstraintExtendedCode {

    /**
     * The answer when the text carries no code. Zero is not an SQLite result code, so it can never
     * be confused with a real one.
     */
    public static final int NONE = 0;

    private static final Pattern EXTENDED_CODE = Pattern.compile("code ([0-9]+)");

    private ConstraintExtendedCode() {
    }

    /**
     * @param failureMessage the text of the store's refusal, verbatim
     * @return the extended result code the text reports, or {@link #NONE} when it carries none
     * @throws IllegalArgumentException if {@code failureMessage} is null — a caller that has no text
     *         to read has a bug, not a refusal it can classify
     */
    public static int of(String failureMessage) {
        if (failureMessage == null) {
            throw new IllegalArgumentException("failureMessage must not be null");
        }
        Matcher matcher = EXTENDED_CODE.matcher(failureMessage);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : NONE;
    }
}
