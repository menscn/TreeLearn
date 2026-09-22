package com.treelearn.data.db.dao;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.PromptPurpose;

/**
 * One {@code generation_operations} row, exactly as stored.
 *
 * <p>A row, not an attempt. {@code GenerationOperation} has no rehydration entry point and that is
 * deliberate: its buffers are mutated as the stream arrives, so rebuilding an attempt from a row
 * would create a second writer of content that no stream is feeding. Recovery (technical design §7)
 * needs only the state to answer "what happened while the process was gone", and
 * {@link GenerationState#processRecoverableStates()} with {@link GenerationState#recoveryTarget()} is
 * what answers it; this type carries the state those two need.
 *
 * <p>Fields are final and the constructor is package-private: rows are produced by
 * {@link GenerationOperationDao}, and a caller able to assemble one could also assemble a row the
 * store would never have written.
 */
public final class GenerationOperationRow {

    public final String id;
    /** The command that pinned this attempt's policy version. */
    public final String commandId;
    public final String branchId;
    public final String messageId;
    public final PromptPurpose purpose;
    public final GenerationModel model;
    public final boolean thinkingEnabled;
    /** The version pinned before submission; never rewritten once the attempt exists. */
    public final String promptPolicyVersion;
    public final GenerationState state;
    /**
     * Reasoning received so far. Empty is normal: it stays empty when thinking was disabled (§9.1),
     * and it is kept — never resent — after a failure or an interruption.
     */
    public final String partialReasoning;
    /** Answer received so far. Non-empty does not mean complete — see {@link GenerationState#STABLE}. */
    public final String partialAnswer;
    /** The mapped provider error of a {@link GenerationState#FAILED} attempt; null otherwise. */
    public final String errorCode;
    public final long createdAt;
    public final long updatedAt;

    GenerationOperationRow(String id, String commandId, String branchId, String messageId,
                           PromptPurpose purpose, GenerationModel model, boolean thinkingEnabled,
                           String promptPolicyVersion, GenerationState state, String partialReasoning,
                           String partialAnswer, String errorCode, long createdAt, long updatedAt) {
        this.id = id;
        this.commandId = commandId;
        this.branchId = branchId;
        this.messageId = messageId;
        this.purpose = purpose;
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
        this.promptPolicyVersion = promptPolicyVersion;
        this.state = state;
        this.partialReasoning = partialReasoning;
        this.partialAnswer = partialAnswer;
        this.errorCode = errorCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        // Lengths, not text: partial content is conversation data and must not reach a log line (§15).
        return "GenerationOperationRow[" + id + ", " + state + ", reasoning="
                + partialReasoning.length() + " chars, answer=" + partialAnswer.length()
                + " chars, policy=" + promptPolicyVersion + "]";
    }
}
