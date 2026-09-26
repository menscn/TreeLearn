/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.wiring;

import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.HomeViewState;

final class RefusalNotices {
    private RefusalNotices() {
    }

    static HomeViewState.Notice of(GenerationCoordinator.Refusal refusal) {
        switch (refusal) {
            case PROMPT_BUILD: {
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

