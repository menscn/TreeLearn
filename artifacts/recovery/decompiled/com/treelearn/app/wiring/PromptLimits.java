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
    public static final int SEARCH_SNIPPET_CHARS = 200;
    public static final int SEARCH_TITLE_CHARS = 80;
    public static final int SEARCH_QUERY_CHARS = 100;
    public static final int SEARCH_INJECTION_LIMIT_CHARS = 4000;
    public static final int SEARCH_CONNECT_TIMEOUT_MS = 5000;
    public static final int SEARCH_READ_TIMEOUT_MS = 10000;

    private PromptLimits() {
    }

    public static PromptBudget budget() {
        return new PromptBudget(60000, 512);
    }
}

