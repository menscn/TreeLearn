/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.DomainErrorCode
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.GenerationCommand
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.PromptPurpose
 */
package com.treelearn.app.generation;

import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;

public final class PositionCommands {
    private PositionCommands() {
    }

    public static Refusal retryRefusal(Message position, boolean attemptRunning) {
        return PositionCommands.positionRefusal(position, attemptRunning);
    }

    public static Refusal resetRefusal(Message position, boolean attemptRunning) {
        Refusal base = PositionCommands.positionRefusal(position, attemptRunning);
        if (base != null) {
            return base;
        }
        return position.activeRevisionId() == null ? Refusal.NO_STABLE_VERSION : null;
    }

    public static Refusal selectRefusal(Message position, String revisionId, String owningMessageId, boolean attemptRunning) {
        Refusal base = PositionCommands.positionRefusal(position, attemptRunning);
        if (base != null) {
            return base;
        }
        if (owningMessageId == null || !owningMessageId.equals(position.id)) {
            return Refusal.REVISION_NOT_OF_THIS_POSITION;
        }
        return revisionId.equals(position.activeRevisionId()) ? Refusal.REVISION_ALREADY_ACTIVE : null;
    }

    private static Refusal positionRefusal(Message position, boolean attemptRunning) {
        if (position == null) {
            return Refusal.MESSAGE_NOT_FOUND;
        }
        if (!position.isAi()) {
            return Refusal.NOT_AN_AI_POSITION;
        }
        return attemptRunning ? Refusal.ATTEMPT_ALREADY_RUNNING : null;
    }

    public static DomainErrorCode codeFor(Refusal refusal) {
        switch (refusal.ordinal()) {
            case 0: 
            case 4: {
                return DomainErrorCode.OPERATION_NOT_COMPLETABLE;
            }
            case 1: {
                return DomainErrorCode.MESSAGE_ROLE_MISMATCH;
            }
            case 2: {
                return DomainErrorCode.ILLEGAL_STATE_TRANSITION;
            }
            case 3: {
                return DomainErrorCode.OPERATION_NOT_STABLE;
            }
            case 5: {
                return DomainErrorCode.REVISION_IMMUTABLE;
            }
        }
        throw new IllegalArgumentException("no error code is defined for refusal " + (Object)((Object)refusal));
    }

    public static enum Refusal {
        MESSAGE_NOT_FOUND,
        NOT_AN_AI_POSITION,
        ATTEMPT_ALREADY_RUNNING,
        NO_STABLE_VERSION,
        REVISION_NOT_OF_THIS_POSITION,
        REVISION_ALREADY_ACTIVE;

    }

    public static final class Baseline {
        public final String branchId;
        public final String messageId;
        public final PromptPurpose purpose;
        public final GenerationModel model;
        public final boolean thinkingEnabled;

        public Baseline(String branchId, String messageId, PromptPurpose purpose, GenerationModel model, boolean thinkingEnabled) {
            this.branchId = Guard.id((String)branchId, (String)"branchId");
            this.messageId = Guard.id((String)messageId, (String)"messageId");
            this.purpose = (PromptPurpose)Guard.object((Object)purpose, (String)"purpose");
            this.model = (GenerationModel)Guard.object((Object)model, (String)"model");
            this.thinkingEnabled = thinkingEnabled;
        }

        public DomainResult<GenerationCommand> commandFor(String policyVersion, long now) {
            return GenerationCommand.create((String)this.branchId, (String)this.messageId, (PromptPurpose)this.purpose, (String)policyVersion, (GenerationModel)this.model, (boolean)this.thinkingEnabled, (long)now);
        }
    }
}

