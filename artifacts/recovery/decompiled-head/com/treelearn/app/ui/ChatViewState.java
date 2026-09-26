/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.SourceCard;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChatViewState {
    public final Phase phase;
    public final String branchId;
    public final String branchTitle;
    public final List<ChatMessage> messages;
    public final String draft;
    public final String quote;
    public final boolean keyConfigured;
    public final GenerationModel model;
    public final boolean thinkingEnabled;
    public final Set<String> expandedReasoning;
    public final Set<String> collapsedReasoning;
    public final List<Recommendation> recommendations;
    public final String recommendationsMessageId;
    public final HomeViewState.Notice notice;
    public final boolean sending;
    public final SourceCard sourceCard;
    public final boolean sourceCardExpanded;
    public final Stage stage;
    public final boolean followLatest;
    public final int missedCount;
    public final boolean archived;

    private ChatViewState(Phase phase, String branchId, String branchTitle, List<ChatMessage> messages, String draft, String quote, boolean keyConfigured, GenerationModel model, boolean thinkingEnabled, Set<String> expandedReasoning, Set<String> collapsedReasoning, List<Recommendation> recommendations, String recommendationsMessageId, HomeViewState.Notice notice, boolean sending, SourceCard sourceCard, boolean sourceCardExpanded, Stage stage, boolean followLatest, int missedCount, boolean archived) {
        this.phase = (Phase)((Object)Guard.object((Object)((Object)phase), (String)"phase"));
        this.branchId = Guard.id((String)branchId, (String)"branchId");
        this.branchTitle = branchTitle == null ? "" : branchTitle;
        this.messages = Collections.unmodifiableList(new ArrayList<ChatMessage>(messages));
        this.draft = draft == null ? "" : draft;
        this.quote = quote == null ? "" : quote;
        this.keyConfigured = keyConfigured;
        this.model = (GenerationModel)Guard.object((Object)model, (String)"model");
        this.thinkingEnabled = thinkingEnabled;
        this.expandedReasoning = Collections.unmodifiableSet(new LinkedHashSet<String>(expandedReasoning));
        this.collapsedReasoning = Collections.unmodifiableSet(new LinkedHashSet<String>(collapsedReasoning));
        this.recommendations = Collections.unmodifiableList(new ArrayList<Recommendation>(recommendations));
        this.recommendationsMessageId = recommendationsMessageId == null ? "" : recommendationsMessageId;
        this.notice = notice;
        this.sending = sending;
        this.sourceCard = sourceCard;
        this.sourceCardExpanded = sourceCardExpanded;
        this.stage = stage == null ? Stage.IDLE : stage;
        this.followLatest = followLatest;
        this.missedCount = Math.max(0, missedCount);
        this.archived = archived;
    }

    public static ChatViewState initial(String branchId, GenerationModel model, boolean thinkingEnabled, boolean keyConfigured) {
        return new ChatViewState(Phase.LOADING, branchId, "", Collections.emptyList(), "", "", keyConfigured, model, thinkingEnabled, Collections.emptySet(), Collections.emptySet(), Collections.emptyList(), "", null, false, null, false, Stage.IDLE, true, 0, false);
    }

    public ChatMessage message(String messageId) {
        for (ChatMessage message : this.messages) {
            if (!message.messageId.equals(messageId)) continue;
            return message;
        }
        return null;
    }

    public boolean isReasoningExpanded(String messageId) {
        return this.expandedReasoning.contains(messageId);
    }

    public boolean isReasoningEffectivelyExpanded(String messageId) {
        if (this.collapsedReasoning.contains(messageId)) {
            return false;
        }
        if (this.expandedReasoning.contains(messageId)) {
            return true;
        }
        ChatMessage message = this.message(messageId);
        return message != null && message.isFormingReasoning();
    }

    public boolean recommendationsVisible() {
        return !this.recommendations.isEmpty() && !this.recommendationsMessageId.isEmpty();
    }

    public boolean canSubmit() {
        return !this.draft.trim().isEmpty() && !this.sending;
    }

    public boolean attemptRunning() {
        return this.sending || this.stage == Stage.SENDING || this.stage == Stage.REASONING || this.stage == Stage.ANSWERING;
    }

    public SendState sendState() {
        if (this.attemptRunning()) {
            return SendState.STOP;
        }
        if (this.archived || !this.keyConfigured || !this.canSubmit()) {
            return SendState.DISABLED;
        }
        return SendState.SEND;
    }

    public ChatViewState withPhase(Phase value) {
        return this.copy(value, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withBranchTitle(String value) {
        return this.copy(this.phase, value, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withMessages(List<ChatMessage> value) {
        return this.copy(this.phase, this.branchTitle, value, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withDraft(String value) {
        return this.copy(this.phase, this.branchTitle, this.messages, value, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withQuote(String value) {
        return this.copy(this.phase, this.branchTitle, this.messages, this.draft, value == null ? "" : value.trim(), this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withKeyConfigured(boolean value) {
        return this.copy(this.phase, this.branchTitle, this.messages, this.draft, this.quote, value, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withModel(GenerationModel value) {
        return this.copy(this.phase, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, value, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withThinkingEnabled(boolean value) {
        return this.copy(this.phase, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, value, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withReasoningExpanded(String messageId, boolean expanded) {
        String id2 = Guard.id((String)messageId, (String)"messageId");
        LinkedHashSet<String> nextOpen = new LinkedHashSet<String>(this.expandedReasoning);
        LinkedHashSet<String> nextCollapsed = new LinkedHashSet<String>(this.collapsedReasoning);
        if (expanded) {
            nextOpen.add(id2);
            nextCollapsed.remove(id2);
        } else {
            nextOpen.remove(id2);
            nextCollapsed.add(id2);
        }
        return this.copy(this.phase, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, nextOpen, nextCollapsed, this.recommendations, this.recommendationsMessageId, this.notice, this.sending);
    }

    public ChatViewState withRecommendations(List<Recommendation> value, String valueMessageId) {
        return this.copy(this.phase, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, value, valueMessageId, this.notice, this.sending);
    }

    public ChatViewState withNotice(HomeViewState.Notice value) {
        return this.copy(this.phase, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, value, this.sending);
    }

    public ChatViewState withSending(boolean value) {
        return this.copy(this.phase, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, value);
    }

    private ChatViewState copy(Phase nextPhase, String nextBranchTitle, List<ChatMessage> nextMessages, String nextDraft, String nextQuote, boolean nextKeyConfigured, GenerationModel nextModel, boolean nextThinkingEnabled, Set<String> nextExpanded, Set<String> nextCollapsed, List<Recommendation> nextRecommendations, String nextRecommendationsMessageId, HomeViewState.Notice nextNotice, boolean nextSending) {
        return new ChatViewState(nextPhase, this.branchId, nextBranchTitle, nextMessages, nextDraft, nextQuote, nextKeyConfigured, nextModel, nextThinkingEnabled, nextExpanded, nextCollapsed, nextRecommendations, nextRecommendationsMessageId, nextNotice, nextSending, this.sourceCard, this.sourceCardExpanded, this.stage, this.followLatest, this.missedCount, this.archived);
    }

    public ChatViewState withSourceCard(SourceCard value) {
        return new ChatViewState(this.phase, this.branchId, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending, value, this.sourceCardExpanded, this.stage, this.followLatest, this.missedCount, this.archived);
    }

    public ChatViewState withSourceCardExpanded(boolean value) {
        return new ChatViewState(this.phase, this.branchId, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending, this.sourceCard, value, this.stage, this.followLatest, this.missedCount, this.archived);
    }

    public ChatViewState withStage(Stage value) {
        return new ChatViewState(this.phase, this.branchId, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending, this.sourceCard, this.sourceCardExpanded, value == null ? Stage.IDLE : value, this.followLatest, this.missedCount, this.archived);
    }

    public ChatViewState withFollow(boolean following, int missedSoFar) {
        return new ChatViewState(this.phase, this.branchId, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending, this.sourceCard, this.sourceCardExpanded, this.stage, following, missedSoFar, this.archived);
    }

    public ChatViewState withArchived(boolean value) {
        return new ChatViewState(this.phase, this.branchId, this.branchTitle, this.messages, this.draft, this.quote, this.keyConfigured, this.model, this.thinkingEnabled, this.expandedReasoning, this.collapsedReasoning, this.recommendations, this.recommendationsMessageId, this.notice, this.sending, this.sourceCard, this.sourceCardExpanded, this.stage, this.followLatest, this.missedCount, value);
    }

    public static enum Phase {
        LOADING,
        READY,
        ERROR;

    }

    public static enum Stage {
        IDLE,
        SENDING,
        REASONING,
        ANSWERING,
        STABLE,
        CANCELED,
        FAILED;

    }

    public static enum SendState {
        DISABLED,
        SEND,
        STOP;

    }
}

