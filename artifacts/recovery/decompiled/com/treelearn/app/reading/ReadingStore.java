/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.ReadingAnchor
 */
package com.treelearn.app.reading;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ReadingAnchor;

public interface ReadingStore {
    public ReadingAnchor anchorOf(String var1);

    public void saveAnchor(String var1, ReadingAnchor var2);

    public void saveSeen(Branch var1);
}

