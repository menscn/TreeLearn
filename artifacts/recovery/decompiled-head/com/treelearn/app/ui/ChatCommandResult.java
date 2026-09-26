/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.HomeViewState;
import com.treelearn.core.domain.Guard;

public final class ChatCommandResult {
    private final String operationId;
    private final String branchId;
    private final HomeViewState.Notice notice;

    private ChatCommandResult(String operationId, String branchId, HomeViewState.Notice notice) {
        this.operationId = operationId;
        this.branchId = branchId;
        this.notice = notice;
    }

    public static ChatCommandResult ran(String operationId) {
        return new ChatCommandResult(Guard.id((String)operationId, (String)"operationId"), "", null);
    }

    public static ChatCommandResult succeeded() {
        return new ChatCommandResult("", "", null);
    }

    public static ChatCommandResult openedBranch(String branchId) {
        return new ChatCommandResult("", Guard.id((String)branchId, (String)"branchId"), null);
    }

    public static ChatCommandResult refused(HomeViewState.Notice notice) {
        return new ChatCommandResult("", "", (HomeViewState.Notice)((Object)Guard.object((Object)((Object)notice), (String)"notice")));
    }

    public boolean isAccepted() {
        return this.notice == null;
    }

    public String operationId() {
        return this.operationId;
    }

    public String branchId() {
        return this.branchId;
    }

    public HomeViewState.Notice notice() {
        return this.notice;
    }
}

