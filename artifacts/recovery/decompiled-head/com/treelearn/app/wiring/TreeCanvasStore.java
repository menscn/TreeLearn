/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.DomainResult
 */
package com.treelearn.app.wiring;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import java.util.List;

public interface TreeCanvasStore {
    public String topicOf(String var1);

    public List<Branch> branchesOf(String var1);

    public String summaryPayload(String var1);

    public int committedRounds(String var1);

    public DomainResult<Void> setCollapsed(String var1, boolean var2, long var3);
}

