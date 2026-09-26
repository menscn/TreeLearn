/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.DomainError
 *  com.treelearn.core.domain.DomainErrorCode
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.GenerationCommand
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.PromptPurpose
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.dao.AnswerRevisionDao
 *  com.treelearn.data.db.dao.GenerationOperationDao
 *  com.treelearn.data.db.dao.MessageDao
 */
package com.treelearn.app.generation;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.generation.PositionCommands;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.DomainError;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.MessageDao;
import java.util.Collections;
import java.util.List;

public final class AnswerVersions {
    private static final PromptPurpose ANSWER_PURPOSE = PromptPurpose.LEARNING_ANSWER;
    private final DatabaseTransactionRunner runner;

    public AnswerVersions(DatabaseTransactionRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
        this.runner = runner;
    }

    public DomainResult<GenerationCommand> retry(SQLiteDatabase database, String messageId, GenerationModel model, boolean thinkingEnabled, String policyVersion, long now) {
        return this.replacement(database, messageId, model, thinkingEnabled, policyVersion, now, false);
    }

    public DomainResult<GenerationCommand> reset(SQLiteDatabase database, String messageId, GenerationModel model, boolean thinkingEnabled, String policyVersion, long now) {
        return this.replacement(database, messageId, model, thinkingEnabled, policyVersion, now, true);
    }

    public DomainResult<AnswerRevision> selectRevision(SQLiteDatabase database, String messageId, String revisionId, long now) {
        Guard.id((String)messageId, (String)"messageId");
        Guard.id((String)revisionId, (String)"revisionId");
        Guard.epochMillis((long)now, (String)"now");
        try {
            return (DomainResult)this.runner.run(database, work -> {
                Message position;
                AnswerRevision target = (position = MessageDao.findById((SQLiteDatabase)work, (String)messageId)) == null ? null : AnswerRevisionDao.findById((SQLiteDatabase)work, (String)revisionId);
                PositionCommands.Refusal refusal = PositionCommands.selectRefusal(position, revisionId, target == null ? null : target.messageId, AnswerVersions.attemptRunning(work, position));
                if (refusal != null) {
                    throw new Refused(AnswerVersions.errorFor(refusal));
                }
                DomainResult switched = position.activateRevision(revisionId);
                if (switched.isFailure()) {
                    throw new Refused(switched.error());
                }
                MessageDao.updateActiveReferences((SQLiteDatabase)work, (Message)position);
                return DomainResult.ok((Object)target);
            });
        }
        catch (Refused refusal) {
            return DomainResult.fail((DomainError)refusal.error);
        }
    }

    public DomainResult<List<AnswerRevision>> versions(SQLiteDatabase database, String messageId) {
        Guard.id((String)messageId, (String)"messageId");
        return DomainResult.ok(Collections.unmodifiableList(AnswerRevisionDao.findByMessage((SQLiteDatabase)database, (String)messageId)));
    }

    private DomainResult<GenerationCommand> replacement(SQLiteDatabase database, String messageId, GenerationModel model, boolean thinkingEnabled, String policyVersion, long now, boolean requiresStableVersion) {
        Guard.id((String)messageId, (String)"messageId");
        Guard.object((Object)model, (String)"model");
        Guard.epochMillis((long)now, (String)"now");
        try {
            return (DomainResult)this.runner.run(database, work -> {
                PositionCommands.Refusal refusal;
                Message position = MessageDao.findById((SQLiteDatabase)work, (String)messageId);
                boolean running = AnswerVersions.attemptRunning(work, position);
                PositionCommands.Refusal refusal2 = refusal = requiresStableVersion ? PositionCommands.resetRefusal(position, running) : PositionCommands.retryRefusal(position, running);
                if (refusal != null) {
                    throw new Refused(AnswerVersions.errorFor(refusal));
                }
                PositionCommands.Baseline baseline = new PositionCommands.Baseline(position.branchId, position.id, ANSWER_PURPOSE, model, thinkingEnabled);
                DomainResult<GenerationCommand> command = baseline.commandFor(policyVersion, now);
                if (command.isFailure()) {
                    throw new Refused(command.error());
                }
                return command;
            });
        }
        catch (Refused refusal) {
            return DomainResult.fail((DomainError)refusal.error);
        }
    }

    private static boolean attemptRunning(SQLiteDatabase database, Message position) {
        return position != null && GenerationOperationDao.findActiveByBranch((SQLiteDatabase)database, (String)position.branchId) != null;
    }

    private static DomainError errorFor(PositionCommands.Refusal refusal) {
        return DomainError.of((DomainErrorCode)PositionCommands.codeFor(refusal), (String)AnswerVersions.detailFor(refusal));
    }

    private static String detailFor(PositionCommands.Refusal refusal) {
        switch (refusal) {
            case MESSAGE_NOT_FOUND: {
                return "no messages row carries that id";
            }
            case NOT_AN_AI_POSITION: {
                return "only an AI position runs an attempt or displays a version";
            }
            case ATTEMPT_ALREADY_RUNNING: {
                return "an attempt is already running in this branch";
            }
            case NO_STABLE_VERSION: {
                return "the position displays no stable version, so there is nothing to regenerate";
            }
            case REVISION_NOT_OF_THIS_POSITION: {
                return "the named revision is not a version of this position";
            }
            case REVISION_ALREADY_ACTIVE: {
                return "the named revision is already the version this position displays";
            }
        }
        throw new IllegalArgumentException("no detail is defined for refusal " + (Object)((Object)refusal));
    }

    private static final class Refused
    extends RuntimeException {
        private final transient DomainError error;

        Refused(DomainError error) {
            super("refused", null, false, false);
            this.error = error;
        }
    }
}

