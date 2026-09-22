package com.treelearn.app.generation;

/**
 * The 250 ms / 256 character write window of technical design 7.
 *
 * <p>A batch becomes due when 256 characters have accumulated across <em>both</em> channels, or when
 * 250 ms have passed since the first delta of the window, whichever comes first. The clock is passed
 * in, so the window is a pure function of an injected "now" and no test has to sleep.
 *
 * <p>The window opens with the first unflushed delta rather than with the object: an attempt that
 * receives nothing is never due, however long it lives. {@link #onFlushed} closes the window, so the
 * delta after a flush opens a fresh one, while taking the two channels does not close it — which is
 * why the caller takes both channels and then reports the flush.
 *
 * <p>A batch holds content for at most one window and is never logged: it goes straight into the
 * operation's own append statement.
 */
public final class StreamBatch {

    /** 7: 256 accumulated characters, counted across both channels. */
    public static final int MAX_CHARS = 256;

    /** 7: 250 ms since the first delta of the window. */
    public static final long MAX_MILLIS = 250L;

    private final StringBuilder reasoning = new StringBuilder();
    private final StringBuilder answer = new StringBuilder();
    private boolean windowOpen;
    private long windowStartMillis;

    /**
     * Adds one delta of each channel; a null chunk counts as empty, which is how a reasoning-only or
     * an answer-only delta is expressed.
     */
    public void append(String reasoningDelta, String answerDelta, long nowMillis) {
        String reasoningChunk = reasoningDelta == null ? "" : reasoningDelta;
        String answerChunk = answerDelta == null ? "" : answerDelta;
        if (!windowOpen && reasoningChunk.length() + answerChunk.length() > 0) {
            windowOpen = true;
            windowStartMillis = nowMillis;
        }
        reasoning.append(reasoningChunk);
        answer.append(answerChunk);
    }

    /** @return true when the caller must write now; an empty batch is never due */
    public boolean isDue(long nowMillis) {
        if (pendingChars() == 0) {
            return false;
        }
        return pendingChars() >= MAX_CHARS || nowMillis - windowStartMillis >= MAX_MILLIS;
    }

    /** Reports that the pending content is durable, which closes the window. */
    public void onFlushed(long nowMillis) {
        reasoning.setLength(0);
        answer.setLength(0);
        windowOpen = false;
        windowStartMillis = nowMillis;
    }

    public boolean isEmpty() {
        return pendingChars() == 0;
    }

    /** @return the characters waiting to be written, both channels together */
    public int pendingChars() {
        return reasoning.length() + answer.length();
    }

    /** @return the reasoning delta accumulated in this window, drained */
    public String takeReasoning() {
        return drain(reasoning);
    }

    /** @return the answer delta accumulated in this window, drained */
    public String takeAnswer() {
        return drain(answer);
    }

    private static String drain(StringBuilder channel) {
        String drained = channel.toString();
        channel.setLength(0);
        return drained;
    }
}
