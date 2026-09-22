package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the conversation screen draws, as one immutable value (technical design 13, PRD 6.2.14).
 *
 * <p>The screen holds no state of its own: the message list, the composer's draft and quoted sentence,
 * which reasoning blocks are open, the strip under the latest answer, the model chip and the failure
 * notice all live here, so a JVM test can assert what the learner would see without inflating a view.
 *
 * <p><strong>The expanded set is keyed by message id.</strong> Not by list position, which is the
 * difference between a disclosure that belongs to one answer and a flag that follows an index around:
 * a keyed set cannot leak onto another answer when the list is rebuilt, and rebuilding is what every
 * read from the store does. The set is a page-session fact and is never persisted, which is what "the
 * expanded state does not survive a restart" means in practice.
 *
 * <p><strong>A read keeps the learner's work.</strong> {@link #withMessages} replaces only the list;
 * the draft, the quote, the expanded set and the composer settings travel through it unchanged, so a
 * read that lands while the learner is typing cannot discard what they typed.
 *
 * <p><strong>The failure categories are reused, not re-declared.</strong> {@link HomeViewState.Notice}
 * is T-012's single classification of provider, network and store failures; this screen reads the same
 * enum instead of writing a second mapping, because two mappings for one provider answer is how the
 * same 401 ends up described two ways. The enum's name is historical - it predates its second user -
 * and a behaviour-free rename is recorded as a follow-up in {@code android/README.md}.
 */
public final class ChatViewState {

    /** Which of the three conversation layouts is on screen. */
    public enum Phase {
        /** The branch is being read. */
        LOADING,
        /** The conversation is drawn. */
        READY,
        /** The branch could not be read; the composer stays usable and the draft is kept. */
        ERROR
    }

    public final Phase phase;
    public final String branchId;
    /** The branch title, or empty until it has been derived (it is a T-011 artifact). */
    public final String branchTitle;
    public final List<ChatMessage> messages;
    public final String draft;
    /** The sentence a follow-up will be asked from; empty when the learner is asking free-hand. */
    public final String quote;
    public final boolean keyConfigured;
    public final GenerationModel model;
    public final boolean thinkingEnabled;
    /** Message ids whose reasoning block is open. Never null; unmodifiable. */
    public final Set<String> expandedReasoning;
    /** Never null; unmodifiable. */
    public final List<Recommendation> recommendations;
    /** The message the strip belongs to; empty when it belongs to none. */
    public final String recommendationsMessageId;
    /** Null when there is nothing to report. */
    public final HomeViewState.Notice notice;
    public final boolean sending;

    private ChatViewState(Phase phase, String branchId, String branchTitle, List<ChatMessage> messages,
            String draft, String quote, boolean keyConfigured, GenerationModel model,
            boolean thinkingEnabled, Set<String> expandedReasoning, List<Recommendation> recommendations,
            String recommendationsMessageId, HomeViewState.Notice notice, boolean sending) {
        this.phase = Guard.object(phase, "phase");
        this.branchId = Guard.id(branchId, "branchId");
        this.branchTitle = branchTitle == null ? "" : branchTitle;
        this.messages = Collections.unmodifiableList(new ArrayList<ChatMessage>(messages));
        this.draft = draft == null ? "" : draft;
        this.quote = quote == null ? "" : quote;
        this.keyConfigured = keyConfigured;
        this.model = Guard.object(model, "model");
        this.thinkingEnabled = thinkingEnabled;
        this.expandedReasoning = Collections.unmodifiableSet(new LinkedHashSet<String>(expandedReasoning));
        this.recommendations = Collections.unmodifiableList(
                new ArrayList<Recommendation>(recommendations));
        this.recommendationsMessageId = recommendationsMessageId == null ? "" : recommendationsMessageId;
        this.notice = notice;
        this.sending = sending;
    }

    public static ChatViewState initial(String branchId, GenerationModel model,
            boolean thinkingEnabled, boolean keyConfigured) {
        return new ChatViewState(Phase.LOADING, branchId, "", Collections.<ChatMessage>emptyList(), "",
                "", keyConfigured, model, thinkingEnabled, Collections.<String>emptySet(),
                Collections.<Recommendation>emptyList(), "", null, false);
    }

    /** @return the row with this id, or null when the conversation does not contain it */
    public ChatMessage message(String messageId) {
        for (ChatMessage message : messages) {
            if (message.messageId.equals(messageId)) {
                return message;
            }
        }
        return null;
    }

    public boolean isReasoningExpanded(String messageId) {
        return expandedReasoning.contains(messageId);
    }

    /** The strip is drawn only when it belongs to a message that is still on screen. */
    public boolean recommendationsVisible() {
        return !recommendations.isEmpty() && !recommendationsMessageId.isEmpty();
    }

    public boolean canSubmit() {
        return !draft.trim().isEmpty() && !sending;
    }

    public ChatViewState withPhase(Phase value) {
        return copy(value, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withBranchTitle(String value) {
        return copy(phase, value, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withMessages(List<ChatMessage> value) {
        return copy(phase, branchTitle, value, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withDraft(String value) {
        return copy(phase, branchTitle, messages, value, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withQuote(String value) {
        return copy(phase, branchTitle, messages, draft, value == null ? "" : value.trim(),
                keyConfigured, model, thinkingEnabled, expandedReasoning, recommendations,
                recommendationsMessageId, notice, sending);
    }

    public ChatViewState withKeyConfigured(boolean value) {
        return copy(phase, branchTitle, messages, draft, quote, value, model, thinkingEnabled,
                expandedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withModel(GenerationModel value) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, value, thinkingEnabled,
                expandedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withThinkingEnabled(boolean value) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, value,
                expandedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withReasoningExpanded(String messageId, boolean expanded) {
        Set<String> next = new LinkedHashSet<String>(expandedReasoning);
        if (expanded) {
            next.add(Guard.id(messageId, "messageId"));
        } else {
            next.remove(messageId);
        }
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                next, recommendations, recommendationsMessageId, notice, sending);
    }

    /** @param value the message the strip belongs to; empty means "belongs to no message" */
    public ChatViewState withRecommendations(List<Recommendation> value, String valueMessageId) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, value, valueMessageId, notice, sending);
    }

    public ChatViewState withNotice(HomeViewState.Notice value) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, recommendations, recommendationsMessageId, value, sending);
    }

    public ChatViewState withSending(boolean value) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, recommendations, recommendationsMessageId, notice, value);
    }

    private ChatViewState copy(Phase nextPhase, String nextBranchTitle, List<ChatMessage> nextMessages,
            String nextDraft, String nextQuote, boolean nextKeyConfigured, GenerationModel nextModel,
            boolean nextThinkingEnabled, Set<String> nextExpanded,
            List<Recommendation> nextRecommendations, String nextRecommendationsMessageId,
            HomeViewState.Notice nextNotice, boolean nextSending) {
        return new ChatViewState(nextPhase, branchId, nextBranchTitle, nextMessages, nextDraft,
                nextQuote, nextKeyConfigured, nextModel, nextThinkingEnabled, nextExpanded,
                nextRecommendations, nextRecommendationsMessageId, nextNotice, nextSending);
    }
}