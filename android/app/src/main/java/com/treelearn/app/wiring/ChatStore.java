package com.treelearn.app.wiring;

import com.treelearn.core.domain.Annotation;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.Highlighter;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Persona;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.TopicDao;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The conversation path's rows and decisions, behind one port, without a database handle.
 *
 * <p>Same reason as {@link HomeStore}: the deciding half must run on a plain JVM. Two methods
 * deserve a note because they are not plain reads.
 *
 * <p>{@link #retry}, {@link #reset} and {@link #selectRevision} are T-010's use cases. They are
 * offered here as delegations rather than re-decided: {@code AnswerVersions} and
 * {@code PositionCommands} already own those verdicts, and a second copy of them in this package is
 * exactly the drift the design forbids. What this port adds is the transaction boundary, not a rule.
 *
 * <p>{@link #history} returns the plan entries that precede a question, in conversation order, using
 * the context encoder's own vocabulary. Handing the encoder a plan the store built is what keeps
 * "the history is the stored rows" true instead of a summary a screen made up.
 */
public interface ChatStore {

    /** The whole conversation of one branch, as read. */
    Conversation read(String branchId);

    /** The stored versions of one position, oldest first. Never null; empty when there is none. */
    List<AnswerRevision> versions(String messageId);

    /**
     * The branches forked directly from one answer of one branch, unarchived and in creation order
     * (PRD 6.2.20 point 7).
     */
    List<ChildBranch> children(String branchId, String messageId);

    /** T-010's version switch, run against the store. */
    DomainResult<AnswerRevision> selectRevision(String messageId, String revisionId, long now);

    /** T-010's replacement of a failed attempt, run against the store. */
    DomainResult<GenerationCommand> retry(String messageId, GenerationModel model,
            boolean thinkingEnabled, long now);

    /** T-010's regeneration of the displayed version, run against the store. */
    DomainResult<GenerationCommand> reset(String messageId, GenerationModel model,
            boolean thinkingEnabled, long now);

    /**
     * The plan entries before {@code questionOrdinal}: the branch's birth snapshot's sources (when
     * it has one) followed by every learner message and displayed stable answer of the branch
     * itself, in order (PRD 6.2.4 point 2). The snapshot's prepended entries are unaffected by
     * {@code questionOrdinal}, which truncates the branch's own rows only.
     *
     * @throws SnapshotUnreadable when the branch's birth snapshot cannot be decoded or resolved row
     *         by row - a context that cannot be proven intact stops the request rather than being
     *         silently shortened (PRD 6.2.4 exception 2)
     */
    List<ContextPlan.Entry> history(String branchId, int questionOrdinal);

    /**
     * The store's failure channel for a birth snapshot that cannot be trusted: a payload that fails
     * its digest check, a schema this build cannot read, or a pinned row that is gone. Unchecked,
     * because the port's other failures are values and this one means "the history itself is not
     * readable"; the wiring catches it and shows the STORAGE notice.
     */
    final class SnapshotUnreadable extends RuntimeException {

        public SnapshotUnreadable(String message) {
            super(message);
        }

        public SnapshotUnreadable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The next free ordinal of a branch: one above the highest stored, or 0. */
    int nextOrdinal(String branchId);

    /** Appends the learner's question and the answer position it is answered at, or neither. */
    void appendTurn(Message userMessage, Message aiMessage);

    /** @return the fork and its birth snapshot in one transaction, or a classified refusal */
    BranchCreation.Outcome createBranch(Branch branch, ContextSnapshotDao.Row snapshot);

    /** @return the branch, or null when no row carries the id */
    Branch branch(String branchId);

    /**
     * The topic's current answer style, read through {@code branchId}.
     *
     * <p>A default keeps test fakes source-compatible; the real store answers from the row. The
     * result keeps the fallback visible so the screen can tell the learner once instead of silently
     * adopting a default, which is why it is this small result and not a bare {@link Persona}.
     */
    default TopicDao.PersonaRead personaOfBranch(String branchId) {
        return new TopicDao.PersonaRead(Persona.DEFAULT, false);
    }

    /**
     * 2026-10-23: stores one answer style on the topic behind {@code branchId}.
     *
     * <p>Abstract rather than defaulted: a store that silently "succeeded" without writing would
     * let the chip claim a style the next send does not use, which is the one failure this path
     * may not hide. Only the SQLite store and the wiring test double implement this port.
     */
    void updatePersona(String branchId, Persona persona);

    /** @return the position, or null when no row carries the id */
    Message position(String messageId);

    /**
     * The annotations whose digest still matches each answer's displayed text (T-026's
     * digest-clearing read, 方案 V1.1 §3.3). The digest is computed here, from the very text the
     * answer displays, so no caller ever carries the hashing rule and no mark can be read against a
     * different revision than the one on screen.
     *
     * @param answers the rows to read marks for; rows without a stable text are skipped inside
     * @return marks per message id; a row whose read came back empty may be absent
     */
    Map<String, List<Annotation>> annotations(List<ChatMessage> answers);

    /**
     * Runs one highlight decision against the store in a single transaction (方案 V1.1 §2.2): a
     * cancel deletes the row it names, a write deletes the absorbed rows and inserts the union row.
     * The digest is computed here from {@code answerText} — the same text the read path hashed —
     * so a mark written now is readable by the next read.
     *
     * @param proseIndex the ordinal of the prose segment the mark anchors in
     * @param plan       the decision {@code Highlighter.plan} produced
     * @param now        the write's application-written UTC epoch millis (rules/sql.md)
     */
    void applyHighlight(String messageId, String answerText, int proseIndex,
            Highlighter.Plan plan, long now);

    /** One branch and its positions, in conversation order. */
    final class Conversation {

        /** Empty until the branch title has been derived; a branch is readable before that. */
        public final String branchTitle;
        public final List<Position> positions;
        /** The branch row the positions came from, or null when the caller read no branch row. */
        public final Branch branch;
        /** The same positions as stored rows, in the same order; empty when there is no branch row. */
        public final List<Message> messages;
        /** The topic's current style; the composer chip displays it, the send path re-reads the row. */
        public final Persona persona;
        /** True when the stored wire id was unknown and the default was adopted for display. */
        public final boolean personaFellBack;

        public Conversation(String branchTitle, List<Position> positions) {
            this(branchTitle, positions, null, Collections.<Message>emptyList());
        }

        /**
         * The read that also carries the row the positions were built from.
         *
         * <p>The reading session resolves a stored position against the branch's unread sequence
         * pair and against the positions themselves, so a read that produces a screen has to hand
         * both on. They come from the one query this read already ran, which is what keeps a
         * restored position and the screen from describing two different conversations.
         */
        public Conversation(String branchTitle, List<Position> positions, Branch branch,
                List<Message> messages) {
            this(branchTitle, positions, branch, messages, Persona.DEFAULT, false);
        }

        /** The full read: rows plus the topic's current style and its fallback flag. */
        public Conversation(String branchTitle, List<Position> positions, Branch branch,
                List<Message> messages, Persona persona, boolean personaFellBack) {
            this.branchTitle = branchTitle;
            this.positions = positions;
            this.branch = branch;
            this.messages = messages;
            this.persona = persona == null ? Persona.DEFAULT : persona;
            this.personaFellBack = personaFellBack;
        }
    }

    /**
     * One position with the facts the conversation row draws.
     *
     * <p>{@code activeRevision} is the version the position displays, and it is the whole of the
     * "is there a stable answer here" question: an AI position either displays a stored version or
     * it does not. The three attempt fields describe the one attempt that may be in flight for the
     * branch; when there is none they are empty and {@code attemptRunning} is false.
     */
    final class Position {

        public final Message message;
        /** The version displayed, or null while there is none. */
        public final AnswerRevision activeRevision;
        /** How many versions this position has stored. */
        public final int revisionCount;
        /** How many branches were forked from this position. */
        public final int childBranchCount;
        /** Whether the branch has an attempt in flight for this position. */
        public final boolean attemptRunning;
        /** The reasoning of the attempt in flight; empty when there is none. */
        public final String attemptReasoning;
        /** The answer of the attempt in flight; empty when there is none. */
        public final String attemptAnswer;

        public Position(Message message, AnswerRevision activeRevision, int revisionCount,
                int childBranchCount, boolean attemptRunning, String attemptReasoning,
                String attemptAnswer) {
            this.message = message;
            this.activeRevision = activeRevision;
            this.revisionCount = revisionCount;
            this.childBranchCount = childBranchCount;
            this.attemptRunning = attemptRunning;
            this.attemptReasoning = attemptReasoning == null ? "" : attemptReasoning;
            this.attemptAnswer = attemptAnswer == null ? "" : attemptAnswer;
        }
    }
}
