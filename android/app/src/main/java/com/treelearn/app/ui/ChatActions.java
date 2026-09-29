package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.ReadingAnchor;

import java.util.Set;

/**
 * What the conversation screen may ask the presenter to do (prototype v2.5, {@code ChatScreen}).
 *
 * <p>It exists so the view depends on a contract instead of on {@link ChatPresenter}: the screen
 * forwards a gesture, the presenter decides whether it is allowed and where it goes, and the decision
 * is asserted in a JVM test. The gestures mirror the prototype one for one - a suggestion row and its
 * fork hot zone are two separate actions because they do two different things.
 */
public interface ChatActions {

    void onDraftChanged(String text);

    void onSend();

    /**
     * T-075: the learner asked to stop the running attempt. The presenter records the intent and the
     * next read that finds the attempt ended reports it as canceled - the stop is a user semantic the
     * failure path must never swallow (069 E3), and the command itself is wired by the composer slice.
     */
    void onStopRequested();

    /**
     * T-075: the follow-latest button. Re-attaches the automatic follow and clears the missed count;
     * the screen then scrolls to where the generation stands.
     */
    void onFollowLatestPressed();

    /** Opens or closes the reasoning block of one answer. */
    void onReasoningToggled(String messageId);

    /** Copies the whole answer - reasoning and answer - to the clipboard. */
    void onCopyPressed(String messageId);

    /**
     * Copies one passage the learner selected inside an answer (PRD 6.2.19 point 5).
     *
     * <p>Not the same operation as {@link #onCopyPressed}, which copies the whole answer and therefore
     * needs a finished version: a passage is the text the learner pointed at, and PRD 6.2.19 exception
     * 1 keeps it copyable while the answer is still arriving.
     */
    void onSelectionCopyPressed(String selection);

    /** Takes the selected sentence as the context of the next question. */
    void onQuotePressed(String messageId, String anchorText);

    /**
     * 2026-09-28: one of the three 问 AI quick queries was tapped. The query goes out as the user
     * message as-is, carrying the quote already in the composer; the learner's own draft travels
     * unchanged, because the chip answers the selection, not what they were typing.
     */
    void onAskAiChipPressed(String query);

    void onQuoteCleared();

    /** T-097: the composer's attachment chip received a picked file's bytes. */
    void onAttachmentPicked(String displayName, String mimeType, byte[] bytes);

    /** T-097: the learner removed the attachment chip. */
    void onAttachmentCleared();

    /**
     * T-095: the recognizer finished and produced text (SPEC-081). The presenter appends it to
     * the draft - never sends it, never overwrites what the learner already typed.
     */
    void onSpeechRecognized(String text);

    void onRetryPressed(String messageId);

    void onResetPressed(String messageId);

    /** Asks the presenter to translate one finished answer into the learner's language. */
    void onTranslatePressed(String messageId);

    /** Shows or hides the translation fetched for one answer. */
    void onTranslationToggled(String messageId);

    /** Reads one finished answer aloud (text-to-speech). */
    void onSpeakPressed(String messageId);

    /**
     * Opens a branch from this answer (T-065).
     *
     * <p>The selection travels with the gesture: {@code quote} is the anchored sentence exactly as
     * it stands inside the answer's own text, with the offsets it spans, so the store can persist a
     * ranged anchor instead of a whole-answer one. An empty quote is the bottom action bar's fork -
     * the whole answer, as before.
     *
     * @param quote the selected sentence, or empty for a whole-answer fork
     * @param startInclusive the inclusive character offset of the quote in the answer text
     * @param endExclusive the exclusive character offset of the quote in the answer text
     */
    void onForkPressed(String messageId, String quote, int startInclusive, int endExclusive);

    /**
     * 高亮: mark, unmark or merge the selected passage of one prose segment (方案 V1.1 §2.2–§2.3).
     *
     * <p>The offsets are the selection as the answer's own text view reports it, and
     * {@code proseIndex} is the ordinal of the prose run the selection lives in - the same pairing
     * the annotation's locator stores. What the tap means (cancel a highlight the selection sits
     * exactly on, or write one merged with whatever it intersects) is the presenter's to decide,
     * through {@link Highlighter#plan}.
     *
     * @param proseIndex the ordinal of the prose segment carrying the selection, 0-based
     * @param startInclusive the inclusive character offset of the selection in that segment's text
     * @param endExclusive the exclusive character offset of the selection in that segment's text
     */
    void onHighlightPressed(String messageId, int proseIndex, int startInclusive, int endInclusive);

    /**
     * 批注: attach a note to the selected passage. T-B finalises this signature and does nothing
     * with it yet - the editor dialog, the anchor and the list are T-D's, and T-D may not change
     * the signature (task file hand-off note).
     */
    void onNotePressed(String messageId, int proseIndex, int startInclusive, int endInclusive);

    /** Opens or closes the source card's upstream history (T-065). */
    void onSourceCardToggled();

    void onChildrenPressed(String messageId);

    /** Asks the presenter for this position's versions so the screen can offer them. */
    void onVersionPressed(String messageId);

    void onRevisionSelected(String messageId, String revisionId);

    /** A tapped suggestion: it becomes the draft and is sent, exactly as typing it would be. */
    void onRecommendationFollowed(String question);

    /** The suggestion's own hot zone: a branch from the answer, prefilled with the question. */
    void onRecommendationForked(String question);

    void onSettingsPressed();

    /** The overview entry: opens the whole topic's canvas, standing on this branch. */
    void onOverviewPressed();

    /** The navigation entry: opens the topic's branch list, standing on this branch. */
    void onNavigationPressed();

    void onModelSelected(GenerationModel model);

    void onThinkingToggled(boolean enabled);

    /** Turns web search on or off for this conversation. */
    void onSearchToggled(boolean enabled);

    /** Opens settings from the search toggle's missing-key state. */
    void onSearchOpenSettings();

    void onBackPressed();

    /**
     * One row of the conversation has been laid out, it is this tall, and these are the positions on
     * screen at that moment.
     *
     * <p>A stored reading position is semantic - pixels inside the list item it names - so resolving
     * it needs the height of that item, which only a laid-out list knows. The screen reports it once
     * per visit and the presenter answers with {@link ChatSink#renderPosition}.
     *
     * <p>The visible set travels with the height because the first frame is a viewport event like any
     * other settle: a conversation that fits on one screen never scrolls, so a scroll-driven report
     * alone would leave the newest answer - the one the learner is looking at - marked unread. The
     * judgement does not move: the set reaches the same {@code ViewportSeen} call the settle path
     * uses, and only the newest unread answer actually being on screen advances anything.
     */
    void onRowsLaidOut(int itemHeightPx, Set<String> visibleMessageIds);

    /**
     * The learner is leaving this branch, standing where the screen measured them.
     *
     * <p>The anchor is handed over as the view measured it and is never composed by the presenter:
     * {@code ReadingSession.remember} is the one write path for a reading position.
     */
    void onLeaving(ReadingAnchor anchor);

    /**
     * The positions the viewport is showing now.
     *
     * <p>Only the newest unread answer entering the viewport may clear the badge, and that judgement
     * is not taken here: the screen reports what is visible, the presenter asks the session.
     */
    void onViewportChanged(Set<String> visibleMessageIds);

    /** The fullscreen composer was opened. */
    void onFullscreenEditOpened();

    /** The fullscreen composer was closed. */
    void onFullscreenEditClosed();
}
