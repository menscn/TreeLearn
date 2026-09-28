package com.treelearn.app.ui.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Single pass scanner that pulls math formulas out of markdown so that commonmark
 * never rewrites them. Pure Java: no Android API, no regular expressions.
 *
 * Recognized delimiters: $$...$$ (block), $...$ (inline), \(...\) (inline), \[...\] (block).
 * Fenced code blocks, indented code blocks and inline code spans are copied verbatim.
 * Unterminated delimiters are left as source so streaming output does not flicker.
 */
public final class MathScanner {

    private static final String PLACEHOLDER_PREFIX = "TLMATH";
    private static final String PLACEHOLDER_SUFFIX = "TL";
    private static final String MATH_SIGNALS = "\\^_{}";
    private static final String DOUBLE_DOLLAR = "$$";
    private static final String CLOSE_PAREN = "\\)";
    private static final String CLOSE_BRACKET = "\\]";

    private MathScanner() {
    }

    public static MathScanResult scan(String markdown) {
        List<MathFormula> formulas = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return new MathScanResult(markdown == null ? "" : markdown, formulas);
        }
        StringBuilder out = new StringBuilder(markdown.length());
        int length = markdown.length();
        int i = 0;
        boolean lineStart = true;
        while (i < length) {
            char c = markdown.charAt(i);
            if (lineStart) {
                if (isIndentedCodeLine(markdown, i)) {
                    i = copyLine(out, markdown, i);
                    continue;
                }
                int fenceEnd = fencedCodeEnd(markdown, i);
                if (fenceEnd > 0) {
                    out.append(markdown, i, fenceEnd);
                    i = fenceEnd;
                    continue;
                }
            }
            if (c == '\n') {
                out.append(c);
                i++;
                continue;
            }
            lineStart = false;
            if (c == '`') {
                i = copyInlineCode(out, markdown, i);
                continue;
            }
            if (c == '\\') {
                i += consumeBackslash(out, markdown, i, formulas);
                continue;
            }
            if (c == '$') {
                i += consumeDollar(out, markdown, i, formulas);
                continue;
            }
            out.append(c);
            i++;
        }
        return new MathScanResult(out.toString(), formulas);
    }

    private static int consumeDollar(StringBuilder out, String src, int start, List<MathFormula> formulas) {
        if (start + 1 < src.length() && src.charAt(start + 1) == '$') {
            int close = findClosing(src, start + 2, DOUBLE_DOLLAR, true, false);
            if (close >= 0 && !src.substring(start + 2, close).trim().isEmpty()) {
                appendFormula(out, src.substring(start + 2, close), true, formulas);
                return close + 2 - start;
            }
            out.append(DOUBLE_DOLLAR);
            return 2;
        }
        int close = findClosing(src, start + 1, "$", false, true);
        if (close > start + 1) {
            String latex = src.substring(start + 1, close);
            if (hasMathSignal(latex)) {
                appendFormula(out, latex, false, formulas);
                return close + 1 - start;
            }
        }
        out.append('$');
        return 1;
    }

    private static int consumeBackslash(StringBuilder out, String src, int start, List<MathFormula> formulas) {
        if (start + 1 < src.length()) {
            char next = src.charAt(start + 1);
            if (next == '(') {
                int close = findClosing(src, start + 2, CLOSE_PAREN, false, false);
                if (close >= 0 && !src.substring(start + 2, close).trim().isEmpty()) {
                    appendFormula(out, src.substring(start + 2, close), false, formulas);
                    return close + 2 - start;
                }
            } else if (next == '[') {
                int close = findClosing(src, start + 2, CLOSE_BRACKET, true, false);
                if (close >= 0 && !src.substring(start + 2, close).trim().isEmpty()) {
                    appendFormula(out, src.substring(start + 2, close), true, formulas);
                    return close + 2 - start;
                }
            } else if (next == '$' || next == '\\' || next == '`') {
                out.append(src, start, start + 2);
                return 2;
            }
        }
        out.append('\\');
        return 1;
    }

    private static void appendFormula(StringBuilder out, String raw, boolean block, List<MathFormula> formulas) {
        String placeholder = PLACEHOLDER_PREFIX + formulas.size() + PLACEHOLDER_SUFFIX;
        formulas.add(new MathFormula(block, raw.trim(), placeholder));
        out.append(placeholder);
    }

    private static int findClosing(String src, int from, String token, boolean allowBlankLine,
            boolean requireNonSpaceBefore) {
        int limit = src.length() - token.length();
        for (int i = from; i <= limit; i++) {
            if (src.startsWith(token, i)) {
                boolean usable = !requireNonSpaceBefore
                        || (i > from && !Character.isWhitespace(src.charAt(i - 1)));
                if (usable) {
                    return i;
                }
            } else if (!allowBlankLine && src.charAt(i) == '\n' && isBlankLine(src, i)) {
                return -1;
            }
        }
        return -1;
    }

    private static boolean isBlankLine(String src, int newlineIndex) {
        int i = newlineIndex + 1;
        while (i < src.length() && (src.charAt(i) == ' ' || src.charAt(i) == '\t')) {
            i++;
        }
        return i >= src.length() || src.charAt(i) == '\n';
    }

    private static boolean hasMathSignal(String latex) {
        for (int i = 0; i < latex.length(); i++) {
            if (MATH_SIGNALS.indexOf(latex.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIndentedCodeLine(String src, int start) {
        int i = start;
        int spaces = 0;
        while (i < src.length() && spaces < 4) {
            char c = src.charAt(i);
            if (c == ' ') {
                spaces++;
                i++;
                continue;
            }
            return c == '\t';
        }
        return spaces >= 4;
    }

    private static int fencedCodeEnd(String src, int start) {
        int i = start;
        int indent = 0;
        while (i < src.length() && indent < 3 && src.charAt(i) == ' ') {
            indent++;
            i++;
        }
        if (i >= src.length()) {
            return -1;
        }
        char fence = src.charAt(i);
        if (fence != '`' && fence != '~') {
            return -1;
        }
        int run = 0;
        while (i < src.length() && src.charAt(i) == fence) {
            run++;
            i++;
        }
        if (run < 3) {
            return -1;
        }
        int openingEnd = src.indexOf('\n', i);
        int scan = openingEnd < 0 ? src.length() : openingEnd + 1;
        while (scan <= src.length()) {
            int lineEnd = src.indexOf('\n', scan);
            int stop = lineEnd < 0 ? src.length() : lineEnd;
            if (isClosingFence(src, scan, stop, fence, run)) {
                return lineEnd < 0 ? src.length() : lineEnd + 1;
            }
            if (lineEnd < 0) {
                break;
            }
            scan = lineEnd + 1;
        }
        return src.length();
    }

    private static boolean isClosingFence(String src, int from, int to, char fence, int run) {
        int i = from;
        int indent = 0;
        while (i < to && indent < 3 && src.charAt(i) == ' ') {
            indent++;
            i++;
        }
        int count = 0;
        while (i < to && src.charAt(i) == fence) {
            count++;
            i++;
        }
        if (count < run) {
            return false;
        }
        while (i < to) {
            if (!Character.isWhitespace(src.charAt(i))) {
                return false;
            }
            i++;
        }
        return true;
    }

    private static int copyLine(StringBuilder out, String src, int start) {
        int newline = src.indexOf('\n', start);
        int end = newline < 0 ? src.length() : newline + 1;
        out.append(src, start, end);
        return end;
    }

    private static int copyInlineCode(StringBuilder out, String src, int start) {
        int i = start;
        while (i < src.length() && src.charAt(i) == '`') {
            i++;
        }
        String fence = src.substring(start, i);
        int close = src.indexOf(fence, i);
        if (close < 0) {
            out.append(fence);
            return i;
        }
        int end = close + fence.length();
        out.append(src, start, end);
        return end;
    }
}
