/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.PromptBudget
 */
package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.PromptBudget;

public final class PromptLimits {
    public static final int CAPACITY_CHARS = 60000;
    public static final int MIN_ANSWER_CHARS = 512;

    private PromptLimits() {
    }

    public static PromptBudget budget() {
        return new PromptBudget(60000, 512);
    }
}

