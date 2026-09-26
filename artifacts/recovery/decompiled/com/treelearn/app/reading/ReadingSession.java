/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.ReadingAnchor
 *  com.treelearn.core.domain.ReadingPosition
 *  com.treelearn.core.domain.ViewportSeen
 */
package com.treelearn.app.reading;

import com.treelearn.app.reading.ReadingStore;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;
import com.treelearn.core.domain.ViewportSeen;
import java.util.List;
import java.util.Set;

public final class ReadingSession {
    private final ReadingStore store;

    public ReadingSession(ReadingStore store) {
        this.store = (ReadingStore)Guard.object((Object)store, (String)"store");
    }

    public ReadingPosition open(Branch branch, List<Message> messages, int itemHeightPx) {
        Guard.object((Object)branch, (String)"branch");
        Guard.object(messages, (String)"messages");
        ReadingAnchor stored = this.store.anchorOf(branch.id);
        if (stored == null) {
            return ReadingPosition.liveEnd(messages);
        }
        return stored.resolve(messages, itemHeightPx);
    }

    public boolean onViewport(Branch branch, List<Message> messages, Set<String> visibleMessageIds) {
        Guard.object((Object)branch, (String)"branch");
        if (!ViewportSeen.onViewport((Branch)branch, messages, visibleMessageIds)) {
            return false;
        }
        this.store.saveSeen(branch);
        return true;
    }

    public void remember(Branch branch, ReadingAnchor anchor) {
        Guard.object((Object)branch, (String)"branch");
        this.store.saveAnchor(branch.id, (ReadingAnchor)Guard.object((Object)anchor, (String)"anchor"));
    }
}

