package com.treelearn.app.ui;

/**
 * A range of one answer's text, and the two ranges the selection menu asks for (PRD 6.2.19).
 *
 * <p>It is a value type with no Android type in it on purpose. The screen can only hand over two
 * integers and the fact that a text view answers {@code -1} for both ends before it is touched, and
 * "which sentence did the long press land in" is a rule about characters - so both live here, where a
 * plain-JVM test can reach them, instead of inside a gesture handler no test can drive.
 */
public final class TextSelection {

    public final int start;
    public final int end;

    private TextSelection(int start, int end) {
        this.start = start;
        this.end = end;
    }

    /** The range a text view reports right now, which is {@code (-1, -1)} before it is touched. */
    public static TextSelection of(int start, int end) {
        return new TextSelection(start, end);
    }

    /** 全选: the whole answer, which is what PRD 6.2.19 point 6 selects. */
    public static TextSelection whole(int length) {
        return new TextSelection(0, Math.max(length, 0));
    }

    /**
     * 长按选中该句 (PRD 6.2.19 point 1): the sentence the pressed character belongs to.
     *
     * <p>A sentence ends at a CJK full stop, at a line break, or at an ASCII stop that closes a word -
     * the last condition is what keeps {@code V3.1} in one piece. The answer's own leading and trailing
     * whitespace is not part of the sentence, so a selection never highlights a blank run.
     */
    public static TextSelection sentence(String text, int offset) {
        if (text == null || text.isEmpty()) {
            return of(0, 0);
        }
        int at = Math.max(0, Math.min(offset, text.length() - 1));
        int start = 0;
        int end = text.length();
        for (int i = at; i > 0; i--) {
            if (endsSentence(text, i - 1)) {
                start = i;
                break;
            }
        }
        for (int i = at; i < text.length(); i++) {
            if (endsSentence(text, i)) {
                end = i + 1;
                break;
            }
        }
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return of(start, end);
    }

    /** Whether there is anything selected at all. */
    public boolean isEmpty() {
        return start < 0 || end <= start;
    }

    /** The selected characters, or the empty string when this range is not a selection. */
    public String read(String text) {
        if (text == null || isEmpty() || end > text.length()) {
            return "";
        }
        return text.substring(start, end);
    }

    private static boolean endsSentence(String text, int index) {
        char character = text.charAt(index);
        if (character == '\n' || "。！？；".indexOf(character) >= 0) {
            return true;
        }
        if (".!?;".indexOf(character) < 0) {
            return false;
        }
        return index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1));
    }
}
