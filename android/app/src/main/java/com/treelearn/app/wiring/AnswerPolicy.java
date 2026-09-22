package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.core.domain.PromptPurpose;

/**
 * The one place the conversation's usage and the version it pins are named.
 *
 * <p>5.1 defines a conversation position as a learning answer, and T-010 uses the same constant for a
 * replacement: a retry or a reset is the same usage as the attempt it replaces, not a new one. The
 * version is read from the registry rather than written down here, so the string a command pins and
 * the string the request carries come from the same entry.
 */
final class AnswerPolicy {

    static final PromptPurpose PURPOSE = PromptPurpose.LEARNING_ANSWER;

    private AnswerPolicy() {
    }

    static String version() {
        return DeepSeekPromptRegistry.spec(PURPOSE).policyVersion;
    }
}