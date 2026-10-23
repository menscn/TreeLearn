package com.treelearn.app.wiring;

import android.database.sqlite.SQLiteDatabase;

import com.treelearn.ai.prompt.DeepSeekContextEncoder;
import com.treelearn.app.generation.AnswerVersions;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.Highlighter;
import com.treelearn.core.domain.Annotation;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Persona;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AnswerRevisionDao;
import com.treelearn.data.db.dao.AnnotationDao;
import com.treelearn.data.db.dao.AttachmentDao;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.BranchDao;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.GenerationOperationDao;
import com.treelearn.data.db.dao.GenerationOperationRow;
import com.treelearn.data.db.dao.MessageDao;
import com.treelearn.data.db.dao.TopicDao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The conversation's rows and its store-side decisions, read and written through the real local
 * database.
 *
 * <p>It owns no rule either. A retry, a reset and a version switch are {@link AnswerVersions}, and a
 * fork is {@link BranchCreation}; this class supplies the connection and shapes what comes back.
 * Everything the screen reasons about - which status a row is in, which notice a refusal becomes,
 * which history a request carries - is in {@link ChatWiring}, where a plain-JVM test can reach it.
 *
 * <p>{@link #appendTurn} touches the topic's stamp in the same transaction as the two rows. That is
 * not bookkeeping for its own sake: the home list is ordered by it, and a conversation that did not
 * move its topic to the top would make "recently used" a lie.
 */
public final class SqliteChatStore implements ChatStore {

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
    public Conversation read(String branchId) {
        Guard.id(branchId, "branchId");
        SQLiteDatabase database = database();
        Branch branch = BranchDao.findById(database, branchId);
        if (branch == null) {
            throw new IllegalStateException("no branch row carries " + branchId
                    + "; a conversation cannot be read for a branch that is not stored");
        }
        List<Message> rows = MessageDao.findByBranch(database, branchId);
        Map<String, Integer> forks = childCounts(database, branch.topicId);
        GenerationOperationRow attempt = GenerationOperationDao.findActiveByBranch(database, branchId);
        List<Position> positions = new ArrayList<Position>(rows.size());
        for (Message row : rows) {
            positions.add(position(database, row, forks, attempt));
        }
        TopicDao.PersonaRead persona = TopicDao.personaOfBranch(database, branchId);
        return new Conversation(BranchHeading.of(branch.title(), firstLearnerMessage(rows)),
                Collections.unmodifiableList(positions), branch, Collections.unmodifiableList(rows),
                persona == null ? Persona.DEFAULT : persona.persona,
                persona != null && persona.fellBack);
    }

    @Override
    public TopicDao.PersonaRead personaOfBranch(String branchId) {
        Guard.id(branchId, "branchId");
        return TopicDao.personaOfBranch(database(), branchId);
    }

    @Override
    public void updatePersona(String branchId, Persona persona) {
        Guard.id(branchId, "branchId");
        Guard.object(persona, "persona");
        SQLiteDatabase database = database();
        Branch branch = BranchDao.findById(database, branchId);
        if (branch == null) {
            throw new IllegalStateException("no branch row carries " + branchId
                    + "; an answer style cannot be stored for a branch that is not stored");
        }
        TopicDao.updatePersona(database, branch.topicId, persona);
    }

    @Override
    public List<AnswerRevision> versions(String messageId) {
        Guard.id(messageId, "messageId");
        DomainResult<List<AnswerRevision>> stored = answers.versions(database(), messageId);
        return stored.isFailure() ? Collections.<AnswerRevision>emptyList() : stored.value();
    }

    /**
     * The child list of one answer, named through the branch heading rule so the popup draws the same
     * name the tree does. The topic is scanned once, which is the read {@link BranchChildren} needs.
     */
    @Override
    public List<ChildBranch> children(String branchId, String messageId) {
        Guard.id(branchId, "branchId");
        Guard.id(messageId, "messageId");
        SQLiteDatabase database = database();
        Branch branch = BranchDao.findById(database, branchId);
        if (branch == null) {
            return Collections.emptyList();
        }
        List<ChildBranch> children = new ArrayList<ChildBranch>();
        for (Branch child : BranchChildren.of(BranchDao.findByTopic(database, branch.topicId),
                messageId)) {
            children.add(new ChildBranch(child.id,
                    childTitleOf(database, child, MessageDao.findByBranch(database, child.id)),
                    child.unreadCount()));
        }
        return children;
    }

    @Override
    public DomainResult<AnswerRevision> selectRevision(String messageId, String revisionId, long now) {
        return answers.selectRevision(database(), messageId, revisionId, now);
    }

    @Override
    public DomainResult<GenerationCommand> retry(String messageId, GenerationModel model,
            boolean thinkingEnabled, long now) {
        return answers.retry(database(), messageId, model, thinkingEnabled, AnswerPolicy.version(),
                now);
    }

    @Override
    public DomainResult<GenerationCommand> reset(String messageId, GenerationModel model,
            boolean thinkingEnabled, long now) {
        return answers.reset(database(), messageId, model, thinkingEnabled, AnswerPolicy.version(),
                now);
    }

    @Override
    public List<ContextPlan.Entry> history(String branchId, int questionOrdinal) {
        Guard.id(branchId, "branchId");
        SQLiteDatabase database = database();
        List<ContextPlan.Entry> entries = new ArrayList<ContextPlan.Entry>();
        // The branch's own rows first in the builder, but ordinal truncation applies to these alone:
        // the snapshot's prepended segment below is never cut by the question's ordinal (T-064).
        for (Message row : MessageDao.findByBranch(database, branchId)) {
            if (row.ordinal >= questionOrdinal) {
                break;
            }
            if (row.isUser()) {
                entries.add(ContextPlan.Entry.userMessage(row.id,
                        row.userContent + attachmentSuffix(database, row.id)));
                continue;
            }
            String displayed = row.activeRevisionId();
            if (displayed == null || displayed.isEmpty()) {
                continue;
            }
            AnswerRevision revision = AnswerRevisionDao.findById(database, displayed);
            if (revision != null) {
                entries.add(ContextPlan.Entry.stableRevision(revision.id, revision.reasoningContent,
                        revision.answerContent));
            }
        }
        // The birth snapshot is the branch's upstream context (PRD 6.2.4 point 2). fromStored is the
        // one reader of the payload; the merge resolves its references row by row and refuses the
        // whole plan when one is gone, which the wiring shows as the STORAGE notice.
        ContextSnapshotDao.Row stored = ContextSnapshotDao.findByBranchId(database, branchId);
        if (stored == null) {
            return entries;
        }
        DomainResult<ContextSnapshot> decoded = ContextSnapshot.fromStored(stored.id, stored.branchId,
                stored.schemaVersion, stored.digest, stored.payload, stored.createdAt);
        if (decoded.isFailure()) {
            throw new ChatStore.SnapshotUnreadable("the birth snapshot of branch " + branchId
                    + " cannot be trusted: " + decoded.error());
        }
        DomainResult<List<ContextPlan.Entry>> merged = SnapshotHistoryMerger.merge(decoded.value(),
                entries, snapshotRows(database));
        if (merged.isFailure()) {
            throw new ChatStore.SnapshotUnreadable("the birth snapshot of branch " + branchId
                    + " cannot be resolved: " + merged.error());
        }
        return merged.value();
    }

    /** The rows a snapshot's references resolve against, read through the same DAOs the screen reads. */
    private static SnapshotHistoryMerger.Rows snapshotRows(SQLiteDatabase database) {
        return new SnapshotHistoryMerger.Rows() {
            @Override
            public Message message(String messageId) {
                return MessageDao.findById(database, messageId);
            }

            @Override
            public AnswerRevision revision(String revisionId) {
                return AnswerRevisionDao.findById(database, revisionId);
            }
        };
    }

    @Override
    public int nextOrdinal(String branchId) {
        Guard.id(branchId, "branchId");
        List<Message> rows = MessageDao.findByBranch(database(), branchId);
        return rows.isEmpty() ? 0 : rows.get(rows.size() - 1).ordinal + 1;
    }

    @Override
    public void appendTurn(Message userMessage, Message aiMessage) {
        Guard.object(userMessage, "userMessage");
        Guard.object(aiMessage, "aiMessage");
        final SQLiteDatabase database = database();
        runner.run(database, work -> {
            MessageDao.insert(work, userMessage);
            MessageDao.insert(work, aiMessage);
            Branch branch = BranchDao.findById(work, userMessage.branchId);
            Topic topic = branch == null ? null : TopicDao.findById(work, branch.topicId);
            if (topic != null) {
                TopicDao.update(work, topic.touched(userMessage.createdAt));
            }
            return null;
        });
    }

    @Override
    public BranchCreation.Outcome createBranch(Branch branch, ContextSnapshotDao.Row snapshot) {
        return new BranchCreation(runner).create(database(), branch, snapshot);
    }

    @Override
    public Branch branch(String branchId) {
        Guard.id(branchId, "branchId");
        return BranchDao.findById(database(), branchId);
    }

    @Override
    public Message position(String messageId) {
        Guard.id(messageId, "messageId");
        return MessageDao.findById(database(), messageId);
    }

    @Override
    public Map<String, List<Annotation>> annotations(List<ChatMessage> answers) {
        SQLiteDatabase database = database();
        Map<String, List<Annotation>> out = new LinkedHashMap<String, List<Annotation>>();
        for (ChatMessage answer : answers) {
            // A streaming attempt's displayed text is not the stored text the digest was computed
            // from; reading against it would clear the marks the moment the read lands. The row's
            // own stored text is empty until a stable revision exists, which skips it here.
            if (answer == null || answer.messageId == null || answer.text.isEmpty()
                    || !answer.isStable()) {
                continue;
            }
            out.put(answer.messageId,
                    AnnotationDao.listFor(database, answer.messageId, digest(answer.text)));
        }
        return out;
    }

    @Override
    public void applyHighlight(String messageId, String answerText, int proseIndex,
            Highlighter.Plan plan, long now) {
        Guard.id(messageId, "messageId");
        Guard.object(plan, "plan");
        String digest = digest(answerText);
        SQLiteDatabase database = database();
        if (plan.kind == Highlighter.Kind.CANCEL) {
            // The caller's duty (T-026's F3): this only runs on a stable answer, so the digest
            // hashed here is the one the next read will hash again.
            runner.run(database, db -> {
                AnnotationDao.delete(db, plan.cancelId);
                return null;
            });
            return;
        }
        if (plan.merge.absorbedIds.isEmpty()) {
            Annotation union = Annotation.highlight(UUID.randomUUID().toString(),
                    Annotation.Locator.prose(proseIndex),
                    plan.merge.span.start, plan.merge.span.end);
            runner.run(database, db -> {
                AnnotationDao.insert(db, messageId, digest, union, now);
                return null;
            });
            return;
        }
        // One transaction for the whole merge: the absorbed rows die and the union row lands
        // together, so no read can see the passage half-marked (方案 V1.1 §2.2's merge rule).
        String unionId = UUID.randomUUID().toString();
        Annotation union = Annotation.highlight(unionId, Annotation.Locator.prose(proseIndex),
                plan.merge.span.start, plan.merge.span.end);
        runner.run(database, db -> {
            for (String absorbed : plan.merge.absorbedIds) {
                AnnotationDao.delete(db, absorbed);
            }
            AnnotationDao.insert(db, messageId, digest, union, now);
            return null;
        });
    }

    /**
     * The SHA-256 hex of the answer text, the one digest rule the table's clear-on-mismatch read
     * judges against (方案 V1.1 §3.3). Computed from the same text the row displays, UTF-8.
     */
    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte part : hash) {
                String digit = Integer.toHexString(part & 0xff);
                if (digit.length() == 1) {
                    hex.append('0');
                }
                hex.append(digit);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JVM ships SHA-256", impossible);
        }
    }

    private SQLiteDatabase database() {
        return helper.getWritableDatabase();
    }

    /**
     * The rebuilt attachment block of one user turn (T-097): the cached {@code extracted_text}
     * framed exactly as the send path frames it, so a forked branch's replay and the original
     * request read identically. Empty for a turn without attachments, or whose attachment's text
     * is genuinely blank — the cached text was ceiling-checked when the send was admitted, so the
     * rebuild never re-parses and never re-checks (SPEC-082 数据结构: the parse happens once).
     */
    private static String attachmentSuffix(android.database.sqlite.SQLiteDatabase database,
            String messageId) {
        for (AttachmentDao.ResolvedReference reference
                : AttachmentDao.referencesOfMessage(database, messageId)) {
            if (reference.record != null && reference.record.extractedText != null) {
                String block = DeepSeekContextEncoder.attachmentBlock(
                        reference.record.extractedText);
                if (!block.isEmpty()) {
                    return "\n" + block;
                }
            }
        }
        return "";
    }

    private Position position(SQLiteDatabase database, Message row, Map<String, Integer> forks,
            GenerationOperationRow attempt) {
        Integer seen = forks.get(row.id);
        int childBranchCount = seen == null ? 0 : seen;
        if (row.isUser()) {
            return new Position(row, null, 0, childBranchCount, false, "", "");
        }
        String displayed = row.activeRevisionId();
        AnswerRevision revision = displayed == null || displayed.isEmpty() ? null
                : AnswerRevisionDao.findById(database, displayed);
        int revisionCount = AnswerRevisionDao.findByMessage(database, row.id).size();
        boolean running = attempt != null && row.id.equals(attempt.messageId);
        return new Position(row, revision, revisionCount, childBranchCount, running,
                running ? attempt.partialReasoning : "", running ? attempt.partialAnswer : "");
    }

    /**
     * How many branches were forked from each position of the topic, in one read: a query per row
     * would be N+1 on a screen that is drawn in one pass.
     *
     * <p>It delegates to {@link BranchChildren} rather than applying the rule a second time, so the
     * count the row shows and the list the popup opens cannot leave out different children.
     */
    private static Map<String, Integer> childCounts(SQLiteDatabase database, String topicId) {
        return BranchChildren.countsOf(BranchDao.findByTopic(database, topicId));
    }

    /** The child-list heading through the one implementation of that rule. */
    private static String childTitleOf(SQLiteDatabase database, Branch branch, List<Message> rows) {
        return BranchHeading.of(branch.title(), firstLearnerMessage(rows), sourceAnswer(database,
                branch));
    }

    /** The branch's first learner message, or null when it stores none. */
    private static String firstLearnerMessage(List<Message> rows) {
        for (Message row : rows) {
            if (row.isUser()) {
                return row.userContent;
            }
        }
        return null;
    }

    /** The stable answer captured when a child branch was created, or null when it is unavailable. */
    private static String sourceAnswer(SQLiteDatabase database, Branch branch) {
        if (branch.source == null) {
            return null;
        }
        AnswerRevision revision = AnswerRevisionDao.findById(database, branch.source.sourceRevisionId);
        return revision == null ? null : revision.answerContent;
    }
}
