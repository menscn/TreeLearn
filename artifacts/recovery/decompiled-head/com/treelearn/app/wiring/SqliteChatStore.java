/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.database.sqlite.SQLiteDatabase
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.ContextPlan$Entry
 *  com.treelearn.core.domain.ContextSnapshot
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.GenerationCommand
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.Message
 *  com.treelearn.core.domain.Topic
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.db.dao.AnswerRevisionDao
 *  com.treelearn.data.db.dao.BranchCreation
 *  com.treelearn.data.db.dao.BranchCreation$Outcome
 *  com.treelearn.data.db.dao.BranchDao
 *  com.treelearn.data.db.dao.ContextSnapshotDao
 *  com.treelearn.data.db.dao.ContextSnapshotDao$Row
 *  com.treelearn.data.db.dao.GenerationOperationDao
 *  com.treelearn.data.db.dao.GenerationOperationRow
 *  com.treelearn.data.db.dao.MessageDao
 *  com.treelearn.data.db.dao.TopicDao
 */
package com.treelearn.app.wiring;

import android.database.sqlite.SQLiteDatabase;
import com.treelearn.app.generation.AnswerVersions;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.wiring.AnswerPolicy;
import com.treelearn.app.wiring.BranchChildren;
import com.treelearn.app.wiring.BranchHeading;
import com.treelearn.app.wiring.ChatStore;
import com.treelearn.app.wiring.SnapshotHistoryMerger;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationOperationRow;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SqliteChatStore
implements ChatStore {
    private final TreeLearnDatabaseHelper helper;
    private final DatabaseTransactionRunner runner;
    private final AnswerVersions answers;

    public SqliteChatStore(TreeLearnDatabaseHelper helper, DatabaseTransactionRunner runner) {
        if (helper == null) {
            throw new IllegalArgumentException("helper must not be null");
        }
        if (runner == null) {
            throw new IllegalArgumentException("runner must not be null");
        }
        this.helper = helper;
        this.runner = runner;
        this.answers = new AnswerVersions(runner);
    }

    @Override
    public ChatStore.Conversation read(String branchId) {
        Guard.id((String)branchId, (String)"branchId");
        SQLiteDatabase database = this.database();
        Branch branch = BranchDao.findById((SQLiteDatabase)database, (String)branchId);
        if (branch == null) {
            throw new IllegalStateException("no branch row carries " + branchId + "; a conversation cannot be read for a branch that is not stored");
        }
        List rows = MessageDao.findByBranch((SQLiteDatabase)database, (String)branchId);
        Map<String, Integer> forks = SqliteChatStore.childCounts(database, branch.topicId);
        GenerationOperationRow attempt = GenerationOperationDao.findActiveByBranch((SQLiteDatabase)database, (String)branchId);
        ArrayList<ChatStore.Position> positions = new ArrayList<ChatStore.Position>(rows.size());
        for (Message row : rows) {
            positions.add(this.position(database, row, forks, attempt));
        }
        return new ChatStore.Conversation(SqliteChatStore.titleOf(branch, rows), Collections.unmodifiableList(positions), branch, Collections.unmodifiableList(rows));
    }

    @Override
    public List<AnswerRevision> versions(String messageId) {
        Guard.id((String)messageId, (String)"messageId");
        DomainResult<List<AnswerRevision>> stored = this.answers.versions(this.database(), messageId);
        return stored.isFailure() ? Collections.emptyList() : (List)stored.value();
    }

    @Override
    public List<ChildBranch> children(String branchId, String messageId) {
        Guard.id((String)branchId, (String)"branchId");
        Guard.id((String)messageId, (String)"messageId");
        SQLiteDatabase database = this.database();
        Branch branch = BranchDao.findById((SQLiteDatabase)database, (String)branchId);
        if (branch == null) {
            return Collections.emptyList();
        }
        ArrayList<ChildBranch> children = new ArrayList<ChildBranch>();
        for (Branch child : BranchChildren.of(BranchDao.findByTopic((SQLiteDatabase)database, (String)branch.topicId), messageId)) {
            children.add(new ChildBranch(child.id, SqliteChatStore.titleOf(child, MessageDao.findByBranch((SQLiteDatabase)database, (String)child.id)), child.unreadCount()));
        }
        return children;
    }

    @Override
    public DomainResult<AnswerRevision> selectRevision(String messageId, String revisionId, long now) {
        return this.answers.selectRevision(this.database(), messageId, revisionId, now);
    }

    @Override
    public DomainResult<GenerationCommand> retry(String messageId, GenerationModel model, boolean thinkingEnabled, long now) {
        return this.answers.retry(this.database(), messageId, model, thinkingEnabled, AnswerPolicy.version(), now);
    }

    @Override
    public DomainResult<GenerationCommand> reset(String messageId, GenerationModel model, boolean thinkingEnabled, long now) {
        return this.answers.reset(this.database(), messageId, model, thinkingEnabled, AnswerPolicy.version(), now);
    }

    @Override
    public List<ContextPlan.Entry> history(String branchId, int questionOrdinal) {
        ContextSnapshotDao.Row stored;
        Guard.id((String)branchId, (String)"branchId");
        SQLiteDatabase database = this.database();
        ArrayList<ContextPlan.Entry> entries = new ArrayList<ContextPlan.Entry>();
        for (Message row : MessageDao.findByBranch((SQLiteDatabase)database, (String)branchId)) {
            AnswerRevision revision;
            if (row.ordinal >= questionOrdinal) break;
            if (row.isUser()) {
                entries.add(ContextPlan.Entry.userMessage((String)row.id, (String)row.userContent));
                continue;
            }
            String displayed = row.activeRevisionId();
            if (displayed == null || displayed.isEmpty() || (revision = AnswerRevisionDao.findById((SQLiteDatabase)database, (String)displayed)) == null) continue;
            entries.add(ContextPlan.Entry.stableRevision((String)revision.id, (String)revision.reasoningContent, (String)revision.answerContent));
        }
        if ((stored = ContextSnapshotDao.findByBranchId((SQLiteDatabase)database, (String)branchId)) == null) {
            return entries;
        }
        DomainResult decoded = ContextSnapshot.fromStored((String)stored.id, (String)stored.branchId, (int)stored.schemaVersion, (String)stored.digest, (String)stored.payload, (long)stored.createdAt);
        if (decoded.isFailure()) {
            throw new ChatStore.SnapshotUnreadable("the birth snapshot of branch " + branchId + " cannot be trusted: " + decoded.error());
        }
        DomainResult<List<ContextPlan.Entry>> merged = SnapshotHistoryMerger.merge((ContextSnapshot)decoded.value(), entries, SqliteChatStore.snapshotRows(database));
        if (merged.isFailure()) {
            throw new ChatStore.SnapshotUnreadable("the birth snapshot of branch " + branchId + " cannot be resolved: " + merged.error());
        }
        return (List)merged.value();
    }

    private static SnapshotHistoryMerger.Rows snapshotRows(final SQLiteDatabase database) {
        return new SnapshotHistoryMerger.Rows(){

            @Override
            public Message message(String messageId) {
                return MessageDao.findById((SQLiteDatabase)database, (String)messageId);
            }

            @Override
            public AnswerRevision revision(String revisionId) {
                return AnswerRevisionDao.findById((SQLiteDatabase)database, (String)revisionId);
            }
        };
    }

    @Override
    public int nextOrdinal(String branchId) {
        Guard.id((String)branchId, (String)"branchId");
        List rows = MessageDao.findByBranch((SQLiteDatabase)this.database(), (String)branchId);
        return rows.isEmpty() ? 0 : ((Message)rows.get((int)(rows.size() - 1))).ordinal + 1;
    }

    @Override
    public void appendTurn(Message userMessage, Message aiMessage) {
        Guard.object((Object)userMessage, (String)"userMessage");
        Guard.object((Object)aiMessage, (String)"aiMessage");
        SQLiteDatabase database = this.database();
        this.runner.run(database, work -> {
            Topic topic;
            MessageDao.insert((SQLiteDatabase)work, (Message)userMessage);
            MessageDao.insert((SQLiteDatabase)work, (Message)aiMessage);
            Branch branch = BranchDao.findById((SQLiteDatabase)work, (String)userMessage.branchId);
            Topic topic2 = topic = branch == null ? null : TopicDao.findById((SQLiteDatabase)work, (String)branch.topicId);
            if (topic != null) {
                TopicDao.update((SQLiteDatabase)work, (Topic)topic.touched(userMessage.createdAt));
            }
            return null;
        });
    }

    @Override
    public BranchCreation.Outcome createBranch(Branch branch, ContextSnapshotDao.Row snapshot) {
        return new BranchCreation(this.runner).create(this.database(), branch, snapshot);
    }

    @Override
    public Branch branch(String branchId) {
        Guard.id((String)branchId, (String)"branchId");
        return BranchDao.findById((SQLiteDatabase)this.database(), (String)branchId);
    }

    @Override
    public Message position(String messageId) {
        Guard.id((String)messageId, (String)"messageId");
        return MessageDao.findById((SQLiteDatabase)this.database(), (String)messageId);
    }

    private SQLiteDatabase database() {
        return this.helper.getWritableDatabase();
    }

    private ChatStore.Position position(SQLiteDatabase database, Message row, Map<String, Integer> forks, GenerationOperationRow attempt) {
        int childBranchCount;
        Integer seen = forks.get(row.id);
        int n = childBranchCount = seen == null ? 0 : seen;
        if (row.isUser()) {
            return new ChatStore.Position(row, null, 0, childBranchCount, false, "", "");
        }
        String displayed = row.activeRevisionId();
        AnswerRevision revision = displayed == null || displayed.isEmpty() ? null : AnswerRevisionDao.findById((SQLiteDatabase)database, (String)displayed);
        int revisionCount = AnswerRevisionDao.findByMessage((SQLiteDatabase)database, (String)row.id).size();
        boolean running = attempt != null && row.id.equals(attempt.messageId);
        return new ChatStore.Position(row, revision, revisionCount, childBranchCount, running, running ? attempt.partialReasoning : "", running ? attempt.partialAnswer : "");
    }

    private static Map<String, Integer> childCounts(SQLiteDatabase database, String topicId) {
        return BranchChildren.countsOf(BranchDao.findByTopic((SQLiteDatabase)database, (String)topicId));
    }

    private static String titleOf(Branch branch, List<Message> rows) {
        return BranchHeading.of(branch.title(), SqliteChatStore.firstLearnerMessage(rows));
    }

    private static String firstLearnerMessage(List<Message> rows) {
        for (Message row : rows) {
            if (!row.isUser()) continue;
            return row.userContent;
        }
        return null;
    }
}

