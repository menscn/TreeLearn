package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

/**
 * The answer to one conversation command: the attempt it started, the branch it opened, or why
 * nothing happened.
 *
 * <p>Four arms rather than two because the commands differ in what they produce. A send, a retry and
 * a reset act on the position you are already reading and produce an attempt; a fork produces a
 * branch to walk into and starts nothing; a version switch does neither, because it moves a display
 * pointer and calls nobody. A refusal carries the failure category the screen shows and nothing else - the presenter
 * keeps the draft, clears the in-flight flag and renders it.
 */
public final class ChatCommandResult {

    private final String operationId;
    private final String branchId;
    private final HomeViewState.Notice notice;

    private ChatCommandResult(String operationId, String branchId, HomeViewState.Notice notice) {
        this.operationId = operationId;
        this.branchId = branchId;
        this.notice = notice;
    }

    /** An attempt was admitted for the position the learner is reading. */
    public static ChatCommandResult ran(String operationId) {
        return new ChatCommandResult(Guard.id(operationId, "operationId"), "", null);
    }

    /**
     * The command was applied without starting an attempt and without opening a branch. A version
     * switch is the only such command: it moves which stored revision is displayed and calls nothing.
     */
    public static ChatCommandResult succeeded() {
        return new ChatCommandResult("", "", null);
    }

    /**
     * A branch was created and is ready to be entered.
     *
     * <p>No operation id travels with it: a fork writes rows and starts nothing, so reporting an
     * attempt here would name a row that does not exist. The presenter enters the branch and the
     * learner's first question in it is what opens an attempt.
     */
    public static ChatCommandResult openedBranch(String branchId) {
        return new ChatCommandResult("", Guard.id(branchId, "branchId"), null);
    }

    public static ChatCommandResult refused(HomeViewState.Notice notice) {
        return new ChatCommandResult("", "", Guard.object(notice, "notice"));
    }

    public boolean isAccepted() {
        return notice == null;
    }

    /** The attempt that was admitted; only meaningful when accepted. */
    public String operationId() {
        return operationId;
    }

    /** The branch to enter; empty unless a fork was accepted. */
    public String branchId() {
        return branchId;
    }

    /** What to tell the learner; only meaningful when refused. */
    public HomeViewState.Notice notice() {
        return notice;
    }
}