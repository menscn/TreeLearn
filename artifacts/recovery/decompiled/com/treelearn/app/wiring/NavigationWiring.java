/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.wiring;

import com.treelearn.app.navigation.NavigationData;
import com.treelearn.core.domain.Guard;
import java.util.List;

public final class NavigationWiring
implements NavigationData {
    private final NavigationData store;

    public NavigationWiring(NavigationData store) {
        this.store = (NavigationData)Guard.object((Object)store, (String)"store");
    }

    @Override
    public String topicOf(String branchId) {
        return this.store.topicOf(branchId);
    }

    @Override
    public NavigationData.Snapshot load(String topicId) {
        return this.store.load(topicId);
    }

    @Override
    public NavigationData.Summary summary(String branchId) {
        return this.store.summary(branchId);
    }

    @Override
    public void setArchived(String branchId, boolean archived, long now) {
        this.store.setArchived(branchId, archived, now);
    }

    @Override
    public int cancelInFlight(List<String> branchIds) {
        return this.store.cancelInFlight(branchIds);
    }

    @Override
    public void delete(NavigationData.Plan plan) {
        this.store.delete(plan);
    }
}

