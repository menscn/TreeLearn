package com.treelearn.app.ui;

public final class AnswerContentProjection {

    private static final String REASONING_OPEN = "<treelearn_reasoning>";
    private static final String REASONING_CLOSE = "</treelearn_reasoning>";
    private static final String ANSWER_OPEN = "<treelearn_answer>";
    private static final String ANSWER_CLOSE = "</treelearn_answer>";

    public final String answer;
    public final String reasoning;
    private final int answerSourceStart;

    private AnswerContentProjection(String answer, String reasoning, int answerSourceStart) {
        this.answer = answer;
        this.reasoning = reasoning;
        this.answerSourceStart = answerSourceStart;
    }

    public static AnswerContentProjection project(ChatMessage message) {
        if (message == null) {
            return new AnswerContentProjection("", "", 0);
        }
        return project(message.text, message.isGenerating());
    }

    public static AnswerContentProjection project(String content, boolean streaming) {
        String value = content == null ? "" : content;
        int frameStart = 0;
        while (frameStart < value.length() && Character.isWhitespace(value.charAt(frameStart))) {
            frameStart++;
        }
        if (!value.startsWith(REASONING_OPEN, frameStart)) {
            if (isPartialPrefix(value, frameStart, REASONING_OPEN)) {
                return new AnswerContentProjection("", "", 0);
            }
            return new AnswerContentProjection(value, "", 0);
        }

        int reasoningStart = frameStart + REASONING_OPEN.length();
        int reasoningEnd = value.indexOf(REASONING_CLOSE, reasoningStart);
        if (reasoningEnd < 0) {
            return new AnswerContentProjection("",
                    withoutPartialSuffix(value.substring(reasoningStart), REASONING_CLOSE), 0);
        }

        String reasoning = value.substring(reasoningStart, reasoningEnd);
        int answerOpenStart = reasoningEnd + REASONING_CLOSE.length();
        while (answerOpenStart < value.length()
                && Character.isWhitespace(value.charAt(answerOpenStart))) {
            answerOpenStart++;
        }
        if (!value.startsWith(ANSWER_OPEN, answerOpenStart)) {
            return new AnswerContentProjection("", reasoning, 0);
        }

        int answerStart = answerOpenStart + ANSWER_OPEN.length();
        int contentEnd = value.length();
        while (contentEnd > answerStart && Character.isWhitespace(value.charAt(contentEnd - 1))) {
            contentEnd--;
        }
        int closingTagStart = contentEnd - ANSWER_CLOSE.length();
        if (closingTagStart >= answerStart
                && value.startsWith(ANSWER_CLOSE, closingTagStart)) {
            return new AnswerContentProjection(value.substring(answerStart, closingTagStart),
                    reasoning, answerStart);
        }
        return new AnswerContentProjection(
                withoutPartialSuffix(value.substring(answerStart), ANSWER_CLOSE), reasoning,
                answerStart);
    }

    public SourceRange sourceRangeForUniqueText(String selectedText) {
        if (selectedText == null || selectedText.isEmpty()) {
            return null;
        }
        int start = answer.indexOf(selectedText);
        if (start < 0 || answer.indexOf(selectedText, start + 1) >= 0) {
            return null;
        }
        return new SourceRange(answerSourceStart + start,
                answerSourceStart + start + selectedText.length());
    }

    public static final class SourceRange {
        public final int start;
        public final int end;

        private SourceRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static boolean isPartialPrefix(String value, int offset, String token) {
        int available = value.length() - offset;
        return available < token.length()
                && token.regionMatches(0, value, offset, available);
    }

    private static String withoutPartialSuffix(String value, String token) {
        int maximum = Math.min(value.length(), token.length() - 1);
        for (int length = maximum; length > 0; length--) {
            if (value.regionMatches(value.length() - length, token, 0, length)) {
                return value.substring(0, value.length() - length);
            }
        }
        return value;
    }
}
