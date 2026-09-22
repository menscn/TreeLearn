package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

/**
 * The answer to one first-topic submission: the new root branch, or the reason nothing was created.
 *
 * <p>Exactly one of the two is present, which is what lets the presenter clear the draft on one arm
 * and keep it on the other without inspecting an error message.
 */
public final class SubmitResult {

    private final String rootBranchId;
    private final HomeViewState.Notice notice;

    private SubmitResult(String rootBranchId, HomeViewState.Notice notice) {
        this.rootBranchId = rootBranchId;
        this.notice = notice;
    }

    public static SubmitResult accepted(String rootBranchId) {
        return new SubmitResult(Guard.id(rootBranchId, "rootBranchId"), null);
    }

    public static SubmitResult refused(HomeViewState.Notice notice) {
        return new SubmitResult(null, Guard.object(notice, "notice"));
    }

    public boolean isAccepted() {
        return rootBranchId != null;
    }

    /** The root branch to enter; only valid when {@link #isAccepted()}. */
    public String rootBranchId() {
        return rootBranchId;
    }

    /** What to tell the learner; only valid when {@link #isAccepted()} is false. */
    public HomeViewState.Notice notice() {
        return notice;
    }
}