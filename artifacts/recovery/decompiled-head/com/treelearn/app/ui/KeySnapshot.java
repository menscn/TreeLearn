/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

public final class KeySnapshot {
    private static final char BULLET = '\u2022';
    private static final int MASK_BULLETS = 10;
    private static final int MIN_REVEALABLE_LENGTH = 12;
    public final Line line;
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

    public static KeySnapshot ofStoredKey(char[] secret) {
        return new KeySnapshot(Line.PRESENT, KeySnapshot.mask(secret));
    }

    static String mask(char[] secret) {
        int i;
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("a stored key must have at least one character");
        }
        StringBuilder out = new StringBuilder();
        if (secret.length < 12) {
            for (int i2 = 0; i2 < 10; ++i2) {
                out.append('\u2022');
            }
            return out.toString();
        }
        if (secret.length > 3 && secret[0] == 's' && secret[1] == 'k' && secret[2] == '-') {
            out.append("sk-");
        }
        for (i = 0; i < 10; ++i) {
            out.append('\u2022');
        }
        for (i = secret.length - 4; i < secret.length; ++i) {
            out.append(secret[i]);
        }
        return out.toString();
    }

    public static enum Line {
        MISSING,
        UNREADABLE,
        PRESENT;

    }
}

