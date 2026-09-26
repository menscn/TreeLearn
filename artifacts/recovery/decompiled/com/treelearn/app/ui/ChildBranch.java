/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

public final class ChildBranch {
    public final String branchId;
    public final String title;
    public final long unreadCount;

    public ChildBranch(String branchId, String title, long unreadCount) {
        this.branchId = Guard.id((String)branchId, (String)"branchId");
        this.title = title == null ? "" : title.trim();
        this.unreadCount = Math.max(unreadCount, 0L);
    }
}

