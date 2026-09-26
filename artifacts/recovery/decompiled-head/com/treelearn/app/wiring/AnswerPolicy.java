/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.prompt.DeepSeekPromptRegistry
 *  com.treelearn.core.domain.PromptPurpose
 */
package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.core.domain.PromptPurpose;

final class AnswerPolicy {
    static final PromptPurpose PURPOSE = PromptPurpose.LEARNING_ANSWER;

    private AnswerPolicy() {
    }

    static String version() {
        return DeepSeekPromptRegistry.spec((PromptPurpose)AnswerPolicy.PURPOSE).policyVersion;
    }
}

