/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.CopyLabels;
import com.treelearn.core.domain.Guard;

public final class ChatMessage {
    public final String messageId;
    public final boolean ai;
    public final String text;
    public final String quote;
    public final String reasoning;
    public final Status status;
    public final Phase phase;
    public final String activeRevisionId;
    public final int activeRevisionNo;
    public final int revisionCount;
    public final int childBranchCount;
    public final boolean attemptRunning;
    public final long createdAt;

    private ChatMessage(String messageId, boolean ai, String text, String quote, String reasoning, Status status, Phase phase, String activeRevisionId, int activeRevisionNo, int revisionCount, int childBranchCount, boolean attemptRunning, long createdAt) {
        this.messageId = Guard.id((String)messageId, (String)"messageId");
        this.ai = ai;
        this.text = Guard.text((String)text, (String)"text");
        this.quote = quote == null ? "" : quote.trim();
        this.reasoning = reasoning == null ? "" : reasoning;
        this.status = (Status)((Object)Guard.object((Object)((Object)status), (String)"status"));
        this.phase = (Phase)((Object)Guard.object((Object)((Object)phase), (String)"phase"));
        this.activeRevisionId = activeRevisionId == null || activeRevisionId.isEmpty() ? "" : Guard.id((String)activeRevisionId, (String)"activeRevisionId");
        this.activeRevisionNo = Guard.atLeast((int)activeRevisionNo, (int)0, (String)"activeRevisionNo");
        this.revisionCount = Guard.atLeast((int)revisionCount, (int)0, (String)"revisionCount");
        this.childBranchCount = Guard.atLeast((int)childBranchCount, (int)0, (String)"childBranchCount");
        this.attemptRunning = attemptRunning;
        this.createdAt = Guard.epochMillis((long)createdAt, (String)"createdAt");
    }

    public static ChatMessage user(String messageId, String text, String quote, long createdAt) {
        return new ChatMessage(messageId, false, text, quote, "", Status.STABLE, Phase.ANSWER, "", 0, 0, 0, false, createdAt);
    }

    public static ChatMessage ai(String messageId, String answer, String reasoning, Status status, Phase phase, String activeRevisionId, int revisionCount, int childBranchCount, boolean attemptRunning, long createdAt) {
        return ChatMessage.ai(messageId, answer, reasoning, status, phase, activeRevisionId, 0, revisionCount, childBranchCount, attemptRunning, createdAt);
    }

    public static ChatMessage ai(String messageId, String answer, String reasoning, Status status, Phase phase, String activeRevisionId, int activeRevisionNo, int revisionCount, int childBranchCount, boolean attemptRunning, long createdAt) {
        return new ChatMessage(messageId, true, answer, "", reasoning, status, phase, activeRevisionId, activeRevisionNo, revisionCount, childBranchCount, attemptRunning, createdAt);
    }

    public boolean isAi() {
        return this.ai;
    }

    public boolean isStable() {
        return this.ai && this.status == Status.STABLE;
    }

    public boolean isFailed() {
        return this.ai && this.status == Status.FAILED;
    }

    public boolean isGenerating() {
        return this.ai && this.status == Status.GENERATING;
    }

    public Phase phase() {
        return this.phase;
    }

    public int activeRevisionNo() {
        return this.activeRevisionNo;
    }

    public boolean hasReasoning() {
        return !this.reasoning.isEmpty();
    }

    public boolean canExpandReasoning() {
        return this.ai && (this.hasReasoning() || this.isFormingReasoning());
    }

    public boolean isFormingReasoning() {
        return this.isGenerating() && this.phase == Phase.REASONING;
    }

    public boolean canCopy() {
        return this.isStable();
    }

    public boolean canQuote() {
        return this.isStable();
    }

    public boolean canFork() {
        return this.isStable();
    }

    public boolean canRetry() {
        return this.ai && !this.attemptRunning;
    }

    public boolean canReset() {
        return this.canRetry() && !this.activeRevisionId.isEmpty();
    }

    public boolean canSelectRevision(String revisionId) {
        return this.canRetry() && revisionId != null && !revisionId.equals(this.activeRevisionId);
    }

    public boolean hasVersionHistory() {
        return this.ai && this.revisionCount > 1;
    }

    public boolean hasChildren() {
        return this.childBranchCount > 0;
    }

    public String copyText(CopyLabels labels) {
        Guard.object((Object)labels, (String)"labels");
        if (this.reasoning.isEmpty()) {
            return this.text;
        }
        return labels.reasoning + "\n" + this.reasoning + "\n\n" + labels.answer + "\n" + this.text;
    }

    public static enum Status {
        GENERATING,
        FAILED,
        STABLE;

    }

    public static enum Phase {
        REASONING,
        ANSWER;

    }
}

