package com.treelearn.app.ui;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ReadingPosition;

import java.util.List;

/**
 * What the conversation screen is asked to do besides drawing.
 *
 * <p>Navigation, the clipboard and the version menu are ports for the same reason the data is: the
 * presenter decides <em>when</em> each one happens - an accepted fork opens a branch, a copy carries
 * the composed text, a version menu carries the versions that were just read - and a test can assert
 * that decision without an Activity, a clipboard or a popup.
 */
public interface ChatSink {

    void render(ChatViewState state);

    /**
     * Puts the text on the system clipboard.
     *
     * <p>The text contains the answer and its reasoning, so it is handed over and forgotten: the
     * implementation writes it to the clipboard and nothing else - no log, no stored copy.
     */
    void copyToClipboard(String text);

    /** Enters a branch: the one a fork just created, or another the learner picked. */
    void openBranch(String branchId);

    /**
     * Opens the child-branch list drawn from one answer.
     *
     * <p>The branches are read before this is called and handed over with it, so the popup never reads
     * the store on the UI thread; they are exactly the branches PRD 6.2.20 point 7 lists.
     */
    void openChildren(String branchId, String messageId, List<ChildBranch> children);

    /** Shows the versions of one position and reports back through {@link ChatActions}. */
    void openVersions(String messageId, List<AnswerRevision> versions);

    void openSettings();

    /**
     * Opens the tree canvas on the topic this branch belongs to.
     *
     * <p>Navigation only: the canvas is centred on the branch the conversation is showing, and the
     * conversation's own branch is not changed by looking around it.
     */
    void openOverview(String branchId);

    /**
     * Opens the branch list of the topic this branch belongs to.
     *
     * <p>Navigation and nothing else, the same move as {@link #openOverview(String)}: the list is
     * centred on the branch the conversation is showing, and the conversation keeps the branch it was
     * showing (prototype v2.5: the entry only changes which screen is on top).
     */
    void openNavigation(String branchId);

    /** Leaves the conversation for the home screen. */
    void close();

    /**
     * Scrolls the conversation back to a resolved reading position.
     *
     * <p>The presenter decided where the learner was; the screen only has to put that row there. The
     * position is resolved against the rows that exist now, so a stored spot whose answer has been
     * deleted arrives as the live end rather than as a row the screen would have to guess about.
     */
    void renderPosition(ReadingPosition position);
}
