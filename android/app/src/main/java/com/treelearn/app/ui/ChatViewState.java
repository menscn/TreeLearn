package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * T-075: the generation stage of the newest answer row, 073 §2's S-REASONING / S-ANSWERING /
     * S-STABLE / S-CANCELED / S-FAILED mapped onto one value the row draws. The stage is derived by
     * the presenter from the newest row plus the learner's stop request - the store reports a
     * canceled attempt and a failed one as the same unfinished row, so the stop intent is the
     * tie-breaker between the two.
     */
    public enum Stage {
        /** Nothing is being generated and no row needs a generation face. */
        IDLE,
        /** A send was accepted and no row has streamed in yet. */
        SENDING,
        /** The attempt is filling the reasoning stream. */
        REASONING,
        /** The attempt is filling the answer stream. */
        ANSWERING,
        /** The newest answer is a stored version. */
        STABLE,
        /** The learner stopped the attempt; the kept content reads as stopped, not failed. */
        CANCELED,
        /** The attempt ended without a stable answer and without a stop request. */
        FAILED
    }

    /**
     * T-076: the send key's three states (073 §5). One control, one of three faces - the attempt
     * running wins over everything, and anything that cannot be sent reads as disabled.
     */
    public enum SendState {
        /** Nothing to send, no key, or an archived branch. */
        DISABLED,
        /** The learner's words can go out now. */
        SEND,
        /** An attempt is in flight; the same key becomes the stop control. */
        STOP
    }

    /**
     * T-075: the scroll-follow state (073 §6) - the screen follows the newest content only while
     * the learner reads near it; scrolling away detaches, and what grows while detached is counted
     * so the follow-latest button can say how much was missed.
     */

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
    /** Message ids whose reasoning block the learner explicitly opened. Never null; unmodifiable. */
    public final Set<String> expandedReasoning;
    /**
     * T-066: message ids whose reasoning block the learner explicitly folded. A fold is an intent
     * like an open: it overrides the streaming default until the learner taps again.
     */
    public final Set<String> collapsedReasoning;
    /** Never null; unmodifiable. */
    public final List<Recommendation> recommendations;
    /** The message the strip belongs to; empty when it belongs to none. */
    public final String recommendationsMessageId;
    /** Null when there is nothing to report. */
    public final HomeViewState.Notice notice;
    public final boolean sending;
    /** T-065: the read-only provenance card; null on a root branch and until the first read lands. */
    public final SourceCard sourceCard;
    /** Whether the source card's upstream history is showing; the card itself starts collapsed. */
    public final boolean sourceCardExpanded;
    /** T-075: the generation stage of the newest answer row; never null. */
    public final Stage stage;
    /** T-075: whether new content may pull the view; false once the learner scrolled away. */
    public final boolean followLatest;
    /** T-075: how many content growths arrived while detached; zero while following. */
    public final int missedCount;
    /**
     * T-076: whether this branch is archived (073 §2's S-ARCHIVED). The composer stays drawn but
     * disabled, and the draft and quote the learner already had are kept, never cleared.
     */
    public final boolean archived;
    /** Whether the composer is in fullscreen edit mode. */
    public final boolean fullscreenEditing;
    /** Per-message translation state; never null, defaults to {@link Translations#EMPTY}. */
    public final Translations translations;
    /** Whether web search is on for this conversation. */
    public final boolean searchEnabled;
    /** The search engine's human label, or empty when search is off. */
    public final String searchEngineLabel;

    /** The one attachment the composer carries (T-097); null while the composer carries none. */
    public final ComposerAttachment composerAttachment;

    /** The composer's attachment chip: what it is, how big, and where its parse stands (T-097). */
    public static final class ComposerAttachment {

        public enum ParseState { PARSING, READY, FAILED }

        public final String attachmentId;
        public final String displayName;
        public final long sizeBytes;
        public final ParseState state;

        public ComposerAttachment(String attachmentId, String displayName, long sizeBytes,
                ParseState state) {
            this.attachmentId = com.treelearn.core.domain.Guard.id(attachmentId, "attachmentId");
            this.displayName = com.treelearn.core.domain.Guard.text(displayName, "displayName");
            this.sizeBytes = com.treelearn.core.domain.Guard.nonNegative(sizeBytes, "sizeBytes");
            this.state = state == null ? ParseState.PARSING : state;
        }
    }

    private ChatViewState(Phase phase, String branchId, String branchTitle, List<ChatMessage> messages,
            String draft, String quote, boolean keyConfigured, GenerationModel model,
            boolean thinkingEnabled, Set<String> expandedReasoning, Set<String> collapsedReasoning,
            List<Recommendation> recommendations,
            String recommendationsMessageId, HomeViewState.Notice notice, boolean sending,
            SourceCard sourceCard, boolean sourceCardExpanded, Stage stage, boolean followLatest,
            int missedCount, boolean archived, boolean fullscreenEditing, Translations translations,
            boolean searchEnabled, String searchEngineLabel, ComposerAttachment composerAttachment) {
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
        this.collapsedReasoning = Collections.unmodifiableSet(new LinkedHashSet<String>(collapsedReasoning));
        this.recommendations = Collections.unmodifiableList(
                new ArrayList<Recommendation>(recommendations));
        this.recommendationsMessageId = recommendationsMessageId == null ? "" : recommendationsMessageId;
        this.notice = notice;
        this.sending = sending;
        this.sourceCard = sourceCard;
        this.sourceCardExpanded = sourceCardExpanded;
        this.stage = stage == null ? Stage.IDLE : stage;
        this.followLatest = followLatest;
        this.missedCount = Math.max(0, missedCount);
        this.archived = archived;
        this.fullscreenEditing = fullscreenEditing;
        this.translations = translations == null ? Translations.EMPTY : translations;
        this.searchEnabled = searchEnabled;
        this.searchEngineLabel = searchEngineLabel == null ? "" : searchEngineLabel;
        this.composerAttachment = composerAttachment;
    }

    public static ChatViewState initial(String branchId, GenerationModel model,
            boolean thinkingEnabled, boolean keyConfigured) {
        return new ChatViewState(Phase.LOADING, branchId, "", Collections.<ChatMessage>emptyList(), "",
                "", keyConfigured, model, thinkingEnabled, Collections.<String>emptySet(),
                Collections.<String>emptySet(),
                Collections.<Recommendation>emptyList(), "", null, false, null, false,
                Stage.IDLE, true, 0, false, false, Translations.EMPTY, false, "", null);
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

    /** Whether the learner explicitly opened this message's reasoning at some point. */
    public boolean isReasoningExpanded(String messageId) {
        return expandedReasoning.contains(messageId);
    }

    /**
     * T-066: the disclosure's state as the row draws it - the explicit intent when there is one,
     * the streaming stage otherwise. Reasoning that is forming is open by default (the DeepSeek
     * half-folded read), a manual fold or open overrides that default one-way, and an answer that
     * has left the reasoning phase folds again unless the learner asked otherwise.
     */
    public boolean isReasoningEffectivelyExpanded(String messageId) {
        if (collapsedReasoning.contains(messageId)) {
            return false;
        }
        if (expandedReasoning.contains(messageId)) {
            return true;
        }
        ChatMessage message = message(messageId);
        return message != null && message.isFormingReasoning();
    }

    /** The strip is drawn only when it belongs to a message that is still on screen. */
    public boolean recommendationsVisible() {
        return !recommendations.isEmpty() && !recommendationsMessageId.isEmpty();
    }

    public boolean canSubmit() {
        return !draft.trim().isEmpty() && !sending;
    }

    /** T-076: whether an attempt is in flight - the send key's stop face (073 §5). */
    public boolean attemptRunning() {
        return sending || stage == Stage.SENDING || stage == Stage.REASONING
                || stage == Stage.ANSWERING;
    }

    /**
     * T-076: the send key's state (073 §5). A running attempt is a stop key; an archived branch,
     * a missing key or an empty draft is a disabled one; only then is it a send key.
     */
    public SendState sendState() {
        if (attemptRunning()) {
            return SendState.STOP;
        }
        if (archived || !keyConfigured || !canSubmit()) {
            return SendState.DISABLED;
        }
        return SendState.SEND;
    }

    public ChatViewState withPhase(Phase value) {
        return copy(value, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withBranchTitle(String value) {
        return copy(phase, value, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withMessages(List<ChatMessage> value) {
        return copy(phase, branchTitle, value, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withDraft(String value) {
        return copy(phase, branchTitle, messages, value, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withQuote(String value) {
        return copy(phase, branchTitle, messages, draft, value == null ? "" : value.trim(),
                keyConfigured, model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending);
    }

    /**
     * Swaps the composer's attachment chip (T-097). Null clears it; the mutual exclusion with the
     * quote is the presenter's decision and is applied before this builder is reached.
     */
    public ChatViewState withComposerAttachment(ComposerAttachment value) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote,
                keyConfigured, model, thinkingEnabled, expandedReasoning, collapsedReasoning,
                recommendations, recommendationsMessageId, notice, sending, sourceCard,
                sourceCardExpanded, stage, followLatest, missedCount, archived, fullscreenEditing,
                translations, searchEnabled, searchEngineLabel, value);
    }

    public ChatViewState withKeyConfigured(boolean value) {
        return copy(phase, branchTitle, messages, draft, quote, value, model, thinkingEnabled,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withModel(GenerationModel value) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, value, thinkingEnabled,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    public ChatViewState withThinkingEnabled(boolean value) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, value,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, notice, sending);
    }

    /**
     * Records a tap as an explicit intent, whichever way it went: an open removes any fold intent
     * and a fold removes any open intent, so the learner's last word is the only override.
     */
    public ChatViewState withReasoningExpanded(String messageId, boolean expanded) {
        String id = Guard.id(messageId, "messageId");
        Set<String> nextOpen = new LinkedHashSet<String>(expandedReasoning);
        Set<String> nextCollapsed = new LinkedHashSet<String>(collapsedReasoning);
        if (expanded) {
            nextOpen.add(id);
            nextCollapsed.remove(id);
        } else {
            nextOpen.remove(id);
            nextCollapsed.add(id);
        }
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                nextOpen, nextCollapsed, recommendations, recommendationsMessageId, notice, sending);
    }

    /** @param value the message the strip belongs to; empty means "belongs to no message" */
    public ChatViewState withRecommendations(List<Recommendation> value, String valueMessageId) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, collapsedReasoning, value, valueMessageId, notice, sending);
    }

    public ChatViewState withNotice(HomeViewState.Notice value) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, value, sending);
    }

    public ChatViewState withSending(boolean value) {
        return copy(phase, branchTitle, messages, draft, quote, keyConfigured, model, thinkingEnabled,
                expandedReasoning, collapsedReasoning, recommendations, recommendationsMessageId, notice, value);
    }

    private ChatViewState copy(Phase nextPhase, String nextBranchTitle, List<ChatMessage> nextMessages,
            String nextDraft, String nextQuote, boolean nextKeyConfigured, GenerationModel nextModel,
            boolean nextThinkingEnabled, Set<String> nextExpanded, Set<String> nextCollapsed,
            List<Recommendation> nextRecommendations, String nextRecommendationsMessageId,
            HomeViewState.Notice nextNotice, boolean nextSending) {
        return new ChatViewState(nextPhase, branchId, nextBranchTitle, nextMessages, nextDraft,
                nextQuote, nextKeyConfigured, nextModel, nextThinkingEnabled, nextExpanded,
                nextCollapsed, nextRecommendations, nextRecommendationsMessageId, nextNotice,
                nextSending, sourceCard, sourceCardExpanded, stage, followLatest, missedCount,
                archived, fullscreenEditing, translations, searchEnabled, searchEngineLabel, composerAttachment);
    }

    /** Replaces the source card with a read's answer; the expansion state travels unchanged. */
    public ChatViewState withSourceCard(SourceCard value) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote,
                keyConfigured, model, thinkingEnabled, expandedReasoning, collapsedReasoning,
                recommendations,
                recommendationsMessageId, notice, sending, value, sourceCardExpanded, stage,
                followLatest, missedCount, archived, fullscreenEditing, translations, searchEnabled,
                searchEngineLabel, composerAttachment);
    }

    public ChatViewState withSourceCardExpanded(boolean value) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote,
                keyConfigured, model, thinkingEnabled, expandedReasoning, collapsedReasoning,
                recommendations,
                recommendationsMessageId, notice, sending, sourceCard, value, stage,
                followLatest, missedCount, archived, fullscreenEditing, translations, searchEnabled,
                searchEngineLabel, composerAttachment);
    }

    /** T-075: sets the generation stage; the rest of the state travels unchanged. */
    public ChatViewState withStage(Stage value) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote, keyConfigured,
                model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending, sourceCard, sourceCardExpanded,
                value == null ? Stage.IDLE : value, followLatest, missedCount, archived,
                fullscreenEditing, translations, searchEnabled, searchEngineLabel, composerAttachment);
    }

    /** T-075: sets the scroll-follow state; the rest of the state travels unchanged. */
    public ChatViewState withFollow(boolean following, int missedSoFar) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote, keyConfigured,
                model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending, sourceCard, sourceCardExpanded,
                stage, following, missedSoFar, archived, fullscreenEditing, translations,
                searchEnabled, searchEngineLabel, composerAttachment);
    }

    /**
     * T-076: sets the archived flag from the read's branch row. Nothing else moves: the draft and
     * the quote travel unchanged, which is what "archived is read-only" means for the composer.
     */
    public ChatViewState withArchived(boolean value) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote, keyConfigured,
                model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending, sourceCard, sourceCardExpanded,
                stage, followLatest, missedCount, value, fullscreenEditing, translations,
                searchEnabled, searchEngineLabel, composerAttachment);
    }

    /** Enters or leaves fullscreen edit mode; the rest of the state travels unchanged. */
    public ChatViewState withFullscreenEditing(boolean value) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote, keyConfigured,
                model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending, sourceCard, sourceCardExpanded,
                stage, followLatest, missedCount, archived, value, translations, searchEnabled,
                searchEngineLabel, composerAttachment);
    }

    /**
     * Whether the fullscreen edit entry may show: the branch is not archived, the key is configured,
     * and there is a draft to edit.
     */
    public boolean fullscreenEditEntryAvailable() {
        return !archived && keyConfigured && !draft.trim().isEmpty();
    }

    public ChatViewState withTranslationStarted(String messageId) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote, keyConfigured,
                model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending, sourceCard, sourceCardExpanded,
                stage, followLatest, missedCount, archived, fullscreenEditing,
                translations.started(messageId), searchEnabled, searchEngineLabel, composerAttachment);
    }

    public ChatViewState withTranslationFinished(String messageId, String translation) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote, keyConfigured,
                model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending, sourceCard, sourceCardExpanded,
                stage, followLatest, missedCount, archived, fullscreenEditing,
                translations.finished(messageId, translation), searchEnabled, searchEngineLabel, composerAttachment);
    }

    public ChatViewState withTranslationToggled(String messageId) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote, keyConfigured,
                model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending, sourceCard, sourceCardExpanded,
                stage, followLatest, missedCount, archived, fullscreenEditing,
                translations.toggled(messageId), searchEnabled, searchEngineLabel, composerAttachment);
    }

    public ChatViewState withSearchEnabled(boolean value, String engineLabel) {
        return new ChatViewState(phase, branchId, branchTitle, messages, draft, quote, keyConfigured,
                model, thinkingEnabled, expandedReasoning, collapsedReasoning, recommendations,
                recommendationsMessageId, notice, sending, sourceCard, sourceCardExpanded,
                stage, followLatest, missedCount, archived, fullscreenEditing, translations,
                value, engineLabel, composerAttachment);
    }

    public boolean isTranslating(String messageId) {
        return translations.isRunning(messageId);
    }

    public boolean translationFailed(String messageId) {
        return translations.isFailed(messageId);
    }

    public String translationText(String messageId) {
        return translations.textOf(messageId);
    }

    public boolean translationExpanded(String messageId) {
        return translations.isExpanded(messageId);
    }

    public boolean translationVisible(String messageId) {
        return translations.isVisible(messageId);
    }

    /**
     * Per-message translation state, kept as one immutable value so a read that lands while a
     * translation is running cannot discard it (the same rule as the rest of the view state). A
     * translation is either in flight, failed, or shown - and "shown" is the presence of its text,
     * which is what lets the row decide between a spinner, an error and the translated passage.
     */
    public static final class Translations {
        public static final Translations EMPTY = new Translations(Collections.<String, String>emptyMap(),
                Collections.<String>emptySet(), Collections.<String>emptySet(),
                Collections.<String>emptySet());

        private final Map<String, String> texts;
        private final Set<String> failed;
        private final Set<String> running;
        private final Set<String> collapsed;

        private Translations(Map<String, String> texts, Set<String> failed, Set<String> running,
                Set<String> collapsed) {
            this.texts = Collections.unmodifiableMap(new LinkedHashMap<String, String>(texts));
            this.failed = Collections.unmodifiableSet(new LinkedHashSet<String>(failed));
            this.running = Collections.unmodifiableSet(new LinkedHashSet<String>(running));
            this.collapsed = Collections.unmodifiableSet(new LinkedHashSet<String>(collapsed));
        }

        public boolean isRunning(String messageId) {
            return running.contains(messageId);
        }

        public boolean isFailed(String messageId) {
            return failed.contains(messageId);
        }

        public String textOf(String messageId) {
            return texts.get(messageId);
        }

        public boolean isExpanded(String messageId) {
            return texts.containsKey(messageId) && !collapsed.contains(messageId);
        }

        public boolean isVisible(String messageId) {
            return isRunning(messageId) || isFailed(messageId) || texts.containsKey(messageId);
        }

        public Translations started(String messageId) {
            LinkedHashMap<String, String> nextTexts = new LinkedHashMap<String, String>(texts);
            nextTexts.remove(messageId);
            LinkedHashSet<String> nextFailed = new LinkedHashSet<String>(failed);
            nextFailed.remove(messageId);
            LinkedHashSet<String> nextCollapsed = new LinkedHashSet<String>(collapsed);
            nextCollapsed.remove(messageId);
            LinkedHashSet<String> nextRunning = new LinkedHashSet<String>(running);
            nextRunning.add(messageId);
            return new Translations(nextTexts, nextFailed, nextRunning, nextCollapsed);
        }

        public Translations finished(String messageId, String translation) {
            LinkedHashSet<String> nextRunning = new LinkedHashSet<String>(running);
            nextRunning.remove(messageId);
            if (translation == null || translation.trim().isEmpty()) {
                LinkedHashSet<String> nextFailed = new LinkedHashSet<String>(failed);
                nextFailed.add(messageId);
                return new Translations(texts, nextFailed, nextRunning, collapsed);
            }
            LinkedHashMap<String, String> nextTexts = new LinkedHashMap<String, String>(texts);
            nextTexts.put(messageId, translation);
            return new Translations(nextTexts, failed, nextRunning, collapsed);
        }

        public Translations toggled(String messageId) {
            if (!texts.containsKey(messageId)) {
                return this;
            }
            LinkedHashSet<String> nextCollapsed = new LinkedHashSet<String>(collapsed);
            if (nextCollapsed.contains(messageId)) {
                nextCollapsed.remove(messageId);
            } else {
                nextCollapsed.add(messageId);
            }
            return new Translations(texts, failed, running, nextCollapsed);
        }
    }
}