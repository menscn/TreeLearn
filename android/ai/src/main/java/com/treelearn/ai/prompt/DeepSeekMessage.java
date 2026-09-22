package com.treelearn.ai.prompt;

import com.treelearn.core.domain.Guard;

/**
 * One ordered message of a DeepSeek chat request (prompt design V1.0 §5).
 *
 * <p>The role set is the wire set, which is deliberately wider than {@code :core}'s
 * {@code MessageRole}: an encoded history is made of user and assistant turns, while the single
 * system message is composed by {@link DeepSeekRequestAssembler} and never comes from the
 * {@code ContextPlan} (technical design §9.2).
 */
public final class DeepSeekMessage {

    /** The three roles the DeepSeek chat endpoint accepts. */
    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    public final Role role;
    public final String content;

    public DeepSeekMessage(Role role, String content) {
        this.role = Guard.object(role, "role");
        this.content = Guard.text(content, "content");
    }

    @Override
    public String toString() {
        // Role and size only: a diagnostic must never carry message text (§15).
        return "DeepSeekMessage[" + role + ", " + content.length() + " chars]";
    }
}
