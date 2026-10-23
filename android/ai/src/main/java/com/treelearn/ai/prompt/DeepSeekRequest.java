package com.treelearn.ai.prompt;

import com.treelearn.core.domain.Persona;
import com.treelearn.core.domain.PromptPurpose;
import java.util.Collections;
import java.util.List;

/**
 * One assembled, ordered DeepSeek chat request (prompt design V1.0 §5): exactly one system message,
 * at position 0, then the encoded history, then the current input as the last message.
 *
 * <p>The request is the boundary object the gateway transports unchanged (T-006): the gateway adds,
 * removes, merges and reorders nothing. {@link #policyVersion} is the version the caller has to pin
 * on the operation and copy onto the revision (prompt design §3.2); the body itself stays in
 * {@link PromptBodiesV1} and travels only as {@code messages[0].content}.
 */
public final class DeepSeekRequest {

    public final PromptPurpose purpose;
    public final String policyVersion;
    public final boolean thinkingEnabled;
    /** The style the system message was composed with; identity for logs, never a second message. */
    public final Persona persona;
    /** Immutable and ordered; index 0 is the only system message, the last entry is the current input. */
    public final List<DeepSeekMessage> messages;
    public final int systemChars;
    public final int historyChars;
    public final int currentInputChars;

    DeepSeekRequest(PromptPurpose purpose, String policyVersion, boolean thinkingEnabled,
            Persona persona, List<DeepSeekMessage> messages, int systemChars, int historyChars,
            int currentInputChars) {
        this.purpose = purpose;
        this.policyVersion = policyVersion;
        this.thinkingEnabled = thinkingEnabled;
        this.persona = persona;
        this.messages = Collections.unmodifiableList(messages);
        this.systemChars = systemChars;
        this.historyChars = historyChars;
        this.currentInputChars = currentInputChars;
    }

    @Override
    public String toString() {
        // Identity and counts only (prompt design §5, §15): never a message body.
        return "DeepSeekRequest[" + purpose + " " + policyVersion + ", thinking=" + thinkingEnabled
                + ", persona=" + persona.wireId + ", messages=" + messages.size()
                + ", systemChars=" + systemChars + ", historyChars=" + historyChars
                + ", currentInputChars=" + currentInputChars + "]";
    }
}
