/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

public final class TextSelection {
    public final int start;
    public final int end;

    private TextSelection(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public static TextSelection of(int start, int end) {
        return new TextSelection(start, end);
    }

    public static TextSelection whole(int length) {
        return new TextSelection(0, Math.max(length, 0));
    }

    public static TextSelection sentence(String text, int offset) {
        int i;
        if (text == null || text.isEmpty()) {
            return TextSelection.of(0, 0);
        }
        int at = Math.max(0, Math.min(offset, text.length() - 1));
        int start = 0;
        int end = text.length();
        for (i = at; i > 0; --i) {
            if (!TextSelection.endsSentence(text, i - 1)) continue;
            start = i;
            break;
        }
        for (i = at; i < text.length(); ++i) {
            if (!TextSelection.endsSentence(text, i)) continue;
            end = i + 1;
            break;
        }
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            ++start;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            --end;
        }
        return TextSelection.of(start, end);
    }

    public boolean isEmpty() {
        return this.start < 0 || this.end <= this.start;
    }

    public String read(String text) {
        if (text == null || this.isEmpty() || this.end > text.length()) {
            return "";
        }
        return text.substring(this.start, this.end);
    }

    private static boolean endsSentence(String text, int index) {
        char character = text.charAt(index);
        if (character == '\n' || "\u3002\uff01\uff1f\uff1b".indexOf(character) >= 0) {
            return true;
        }
        if (".!?;".indexOf(character) < 0) {
            return false;
        }
        return index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1));
    }
}

