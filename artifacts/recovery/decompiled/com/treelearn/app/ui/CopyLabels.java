/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

public final class CopyLabels {
    public final String reasoning;
    public final String answer;

    private CopyLabels(String reasoning, String answer) {
        this.reasoning = Guard.nonBlank((String)reasoning, (String)"reasoning");
        this.answer = Guard.nonBlank((String)answer, (String)"answer");
    }

    public static CopyLabels of(String reasoningHeading, String answerHeading) {
        return new CopyLabels(reasoningHeading, answerHeading);
    }
}

