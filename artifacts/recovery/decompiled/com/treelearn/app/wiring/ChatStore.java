/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.ContextPlan$Entry
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.GenerationCommand
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Message
 *  com.treelearn.data.db.dao.BranchCreation$Outcome
 *  com.treelearn.data.db.dao.ContextSnapshotDao$Row
 */
package com.treelearn.app.wiring;

import com.treelearn.app.ui.ChildBranch;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import java.util.Collections;
import java.util.List;

public interface ChatStore {
    public Conversation read(String var1);

    public List<AnswerRevision> versions(String var1);

    public List<ChildBranch> children(String var1, String var2);

    public DomainResult<AnswerRevision> selectRevision(String var1, String var2, long var3);

    public DomainResult<GenerationCommand> retry(String var1, GenerationModel var2, boolean var3, long var4);

    public DomainResult<GenerationCommand> reset(String var1, GenerationModel var2, boolean var3, long var4);

    public List<ContextPlan.Entry> history(String var1, int var2);

    public int nextOrdinal(String var1);

    public void appendTurn(Message var1, Message var2);

    public BranchCreation.Outcome createBranch(Branch var1, ContextSnapshotDao.Row var2);

    public Branch branch(String var1);

    public Message position(String var1);

    public static final class Position {
        public final Message message;
        public final AnswerRevision activeRevision;
        public final int revisionCount;
        public final int childBranchCount;
        public final boolean attemptRunning;
        public final String attemptReasoning;
        public final String attemptAnswer;

        public Position(Message message, AnswerRevision activeRevision, int revisionCount, int childBranchCount, boolean attemptRunning, String attemptReasoning, String attemptAnswer) {
            this.message = message;
            this.activeRevision = activeRevision;
            this.revisionCount = revisionCount;
            this.childBranchCount = childBranchCount;
            this.attemptRunning = attemptRunning;
            this.attemptReasoning = attemptReasoning == null ? "" : attemptReasoning;
            this.attemptAnswer = attemptAnswer == null ? "" : attemptAnswer;
        }
    }

    public static final class Conversation {
        public final String branchTitle;
        public final List<Position> positions;
        public final Branch branch;
        public final List<Message> messages;

        public Conversation(String branchTitle, List<Position> positions) {
            this(branchTitle, positions, null, Collections.emptyList());
        }

        public Conversation(String branchTitle, List<Position> positions, Branch branch, List<Message> messages) {
            this.branchTitle = branchTitle;
            this.positions = positions;
            this.branch = branch;
            this.messages = messages;
        }
    }

    public static final class SnapshotUnreadable
    extends RuntimeException {
        public SnapshotUnreadable(String message) {
            super(message);
        }

        public SnapshotUnreadable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

