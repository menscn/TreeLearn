package com.treelearn.ai.prompt;

import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Redaction-safe equality for system bodies (prompt design V1.0 §5; technical design §9.2: a test
 * report may record the purpose and the version, never the prompt text).
 *
 * <p>Plain {@code assertEquals} on two bodies prints both of them the moment the assertion goes red,
 * and that is not hypothetical: the mutation run recorded in artifacts/evidence/T-020-mutation.txt
 * wrote the whole adaptive-tutor body into the report through a JUnit {@code ComparisonFailure}.
 * These helpers assert exactly the same thing — equality — and report only the two lengths and the
 * two SHA-256 digests, so the assertion stays as strict while the report stays content-free.
 *
 * <p>One shared home on purpose: two copies of this rule would drift apart, which is the same defect
 * class as writing one judgement in two entry points.
 */
final class SystemBodyAssertions {

    private SystemBodyAssertions() {
    }

    /** Asserts {@code expected.equals(actual)} without ever printing either body. */
    static void assertSameSystemText(String scenario, String expected, String actual) {
        if (expected.equals(actual)) {
            return;
        }
        fail(scenario + ": system bodies differ — lengths " + expected.length() + " and "
                + actual.length() + ", sha256 " + sha256(expected) + " and " + sha256(actual));
    }

    /** @return the lowercase hex SHA-256 of {@code text} in UTF-8. */
    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >> 4) & 0xf, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException cause) {
            throw new IllegalStateException("SHA-256 must be available", cause);
        }
    }
}
