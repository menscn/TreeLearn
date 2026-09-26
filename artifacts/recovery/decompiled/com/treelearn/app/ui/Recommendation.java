/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

public final class Recommendation {
    public final String question;
    public final Kind kind;

    public Recommendation(String question, Kind kind) {
        this.question = Guard.nonBlank((String)question, (String)"question");
        this.kind = (Kind)((Object)Guard.object((Object)((Object)kind), (String)"kind"));
    }

    public static enum Kind {
        STARTER,
        FOLLOW_UP,
        NEW_DIRECTION;

    }
}

