package com.treelearn.app.ui;

/**
 * The stored credential as the settings screen may show it (PRD 6.2.13 point 6).
 *
 * <p>Three states, and the middle one matters: a stored envelope that cannot be read is not the same
 * as no key at all. The store reports "an envelope is present" for both a corrupted ciphertext and a
 * lost Keystore entry, and only an actual read tells them apart - so the screen asks for a fresh key
 * in that case instead of claiming the learner never configured one.
 *
 * <p>The mask is the only form of the key that may leave this class. It is built here, once, so there
 * is a single place that could ever put key material on screen or into a log.
 */
public final class KeySnapshot {

    public enum Line {
        /** No envelope stored. */
        MISSING,
        /** An envelope is stored but could not be decrypted; asking for the key again is the fix. */
        UNREADABLE,
        /** Readable, and shown only as {@link #mask}. */
        PRESENT
    }

    /** Bullets per mask. Written as an escape so the source stays readable in any editor. */
    private static final char BULLET = '\u2022';

    private static final int MASK_BULLETS = 10;

    /** Shorter keys are not partially revealed at all. */
    private static final int MIN_REVEALABLE_LENGTH = 12;

    public final Line line;
    /** Empty unless {@link #line} is {@link Line#PRESENT}. */
    public final String mask;

    private KeySnapshot(Line line, String mask) {
        this.line = line;
        this.mask = mask;
    }

    public static KeySnapshot missing() {
        return new KeySnapshot(Line.MISSING, "");
    }

    public static KeySnapshot unreadable() {
        return new KeySnapshot(Line.UNREADABLE, "");
    }

    /** @param secret a call-for-use buffer; the caller wipes it as soon as this returns */
    public static KeySnapshot ofStoredKey(char[] secret) {
        return new KeySnapshot(Line.PRESENT, mask(secret));
    }

    /**
     * The mask: an optional recognizable prefix, a fixed run of bullets, and the last four
     * characters.
     *
     * <p>A key too short to have a middle worth hiding is masked entirely, so no short credential can
     * be reconstructed from its own preview.
     */
    static String mask(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("a stored key must have at least one character");
        }
        StringBuilder out = new StringBuilder();
        if (secret.length < MIN_REVEALABLE_LENGTH) {
            for (int i = 0; i < MASK_BULLETS; i++) {
                out.append(BULLET);
            }
            return out.toString();
        }
        if (secret.length > 3 && secret[0] == 's' && secret[1] == 'k' && secret[2] == '-') {
            out.append("sk-");
        }
        for (int i = 0; i < MASK_BULLETS; i++) {
            out.append(BULLET);
        }
        for (int i = secret.length - 4; i < secret.length; i++) {
            out.append(secret[i]);
        }
        return out.toString();
    }
}