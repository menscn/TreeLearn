/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.HomeViewState;
import com.treelearn.core.domain.Guard;

public final class SubmitResult {
    private final String rootBranchId;
    private final HomeViewState.Notice notice;

    private SubmitResult(String rootBranchId, HomeViewState.Notice notice) {
        this.rootBranchId = rootBranchId;
        this.notice = notice;
    }

    public static SubmitResult accepted(String rootBranchId) {
        return new SubmitResult(Guard.id((String)rootBranchId, (String)"rootBranchId"), null);
    }

    public static SubmitResult refused(HomeViewState.Notice notice) {
        return new SubmitResult(null, (HomeViewState.Notice)((Object)Guard.object((Object)((Object)notice), (String)"notice")));
    }

    public boolean isAccepted() {
        return this.rootBranchId != null;
    }

    public String rootBranchId() {
        return this.rootBranchId;
    }

    public HomeViewState.Notice notice() {
        return this.notice;
    }
}

