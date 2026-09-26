/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.PromptBuildException$Reason
 */
package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.PromptBuildException;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.HomeViewState;

final class RefusalNotices {
    private RefusalNotices() {
    }

    static HomeViewState.Notice of(GenerationCoordinator.Refusal refusal) {
        return RefusalNotices.of(refusal, null, false);
    }

    static HomeViewState.Notice of(GenerationCoordinator.Refusal refusal, PromptBuildException.Reason promptReason, boolean historyCompressionApplied) {
        switch (refusal) {
            case PROMPT_BUILD: {
                if (historyCompressionApplied && promptReason == PromptBuildException.Reason.HISTORY_EXCEEDS_BUDGET) {
                    return HomeViewState.Notice.HISTORY_COMPRESSED;
                }
                return HomeViewState.Notice.REQUEST_REJECTED;
            }
            case BRANCH_BUSY: 
            case VERSION_MISMATCH: 
            case STORAGE: {
                return HomeViewState.Notice.STORAGE;
            }
        }
        throw new IllegalArgumentException("unmapped coordinator refusal: " + (Object)((Object)refusal));
    }
}

