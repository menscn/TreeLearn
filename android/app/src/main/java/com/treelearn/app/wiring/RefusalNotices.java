package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.HomeViewState;

/**
 * The one place a coordinator refusal becomes something on screen.
 *
 * <p>All four refusals share one guarantee: nothing was sent to the provider and the learner's input
 * is untouched. The screen has a category for that guarantee - {@code STORAGE}, "the local write
 * failed, your input is kept, retry directly" - and one for "this request cannot go out as it
 * stands", which is what a build that did not fit its budget is ({@code REQUEST_REJECTED}, "check the
 * model and the content and retry").
 *
 * <p>Deciding it here rather than at each call site is what keeps the home screen and the
 * conversation from reporting the same refusal two different ways. There is no default arm: a
 * refusal this class does not know must fail loudly rather than quietly become "storage".
 */
final class RefusalNotices {

    private RefusalNotices() {
    }

    static HomeViewState.Notice of(GenerationCoordinator.Refusal refusal) {
        return of(refusal, null, false);
    }

    /**
     * The one judgement, with the prompt-build context attached: a refusal that arrived after the
     * history was already compressed once reports the honest outcome - the conversation is longer
     * than one compression can carry - instead of a generic rejection.
     */
    static HomeViewState.Notice of(GenerationCoordinator.Refusal refusal,
            PromptBuildException.Reason promptReason, boolean historyCompressionApplied) {
        switch (refusal) {
            case PROMPT_BUILD:
                if (historyCompressionApplied
                        && promptReason == PromptBuildException.Reason.HISTORY_EXCEEDS_BUDGET) {
                    return HomeViewState.Notice.HISTORY_COMPRESSED;
                }
                return HomeViewState.Notice.REQUEST_REJECTED;
            case BRANCH_BUSY:
            case VERSION_MISMATCH:
            case STORAGE:
                return HomeViewState.Notice.STORAGE;
            default:
                throw new IllegalArgumentException("unmapped coordinator refusal: " + refusal);
        }
    }
}