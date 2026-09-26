/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.SearchHit
 *  com.treelearn.core.domain.SearchQuery
 */
package com.treelearn.app.search;

import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import java.util.List;

public interface SearchRepository {
    public List<SearchHit> search(SearchQuery var1);

    public boolean isStillReachable(SearchHit var1);
}

