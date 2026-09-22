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

    void onQuoteCleared();

    void onRetryPressed(String messageId);

    void onResetPressed(String messageId);

    /** Opens a branch from this answer, with no question yet. */
    void onForkPressed(String messageId);

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
}
