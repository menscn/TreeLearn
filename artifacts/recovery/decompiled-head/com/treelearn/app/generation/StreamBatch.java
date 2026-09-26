/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.generation;

public final class StreamBatch {
    public static final int MAX_CHARS = 256;
    public static final long MAX_MILLIS = 250L;
    private final StringBuilder reasoning = new StringBuilder();
    private final StringBuilder answer = new StringBuilder();
    private boolean windowOpen;
    private long windowStartMillis;

    public void append(String reasoningDelta, String answerDelta, long nowMillis) {
        String answerChunk;
        String reasoningChunk = reasoningDelta == null ? "" : reasoningDelta;
        String string2 = answerChunk = answerDelta == null ? "" : answerDelta;
        if (!this.windowOpen && reasoningChunk.length() + answerChunk.length() > 0) {
            this.windowOpen = true;
            this.windowStartMillis = nowMillis;
        }
        this.reasoning.append(reasoningChunk);
        this.answer.append(answerChunk);
    }

    public boolean isDue(long nowMillis) {
        if (this.pendingChars() == 0) {
            return false;
        }
        return this.pendingChars() >= 256 || nowMillis - this.windowStartMillis >= 250L;
    }

    public void onFlushed(long nowMillis) {
        this.reasoning.setLength(0);
        this.answer.setLength(0);
        this.windowOpen = false;
        this.windowStartMillis = nowMillis;
    }

    public boolean isEmpty() {
        return this.pendingChars() == 0;
    }

    public int pendingChars() {
        return this.reasoning.length() + this.answer.length();
    }

    public String takeReasoning() {
        return StreamBatch.drain(this.reasoning);
    }

    public String takeAnswer() {
        return StreamBatch.drain(this.answer);
    }

    private static String drain(StringBuilder channel) {
        String drained = channel.toString();
        channel.setLength(0);
        return drained;
    }
}

