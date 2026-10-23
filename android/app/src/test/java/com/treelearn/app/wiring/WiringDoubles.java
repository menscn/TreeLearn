package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.Highlighter;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Annotation;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.GenerationOperation;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.Persona;
import com.treelearn.core.domain.Topic;
import com.treelearn.data.db.dao.BranchCreation;
import com.treelearn.data.db.dao.ContextSnapshotDao;
import com.treelearn.data.db.dao.FirstTopicCreation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ports of the wiring package answered from lists, with every call recorded.
 *
 * <p>The deciding half of the home and conversation paths has to run without a database, which is
 * what these fakes buy: they own no rule and only answer what a test set up. The production SQL lives
 * in {@code SqliteHomeStore} / {@code SqliteChatStore} and is exercised by the instrumented tests.
 */
final class WiringDoubles {

    /** Canonical lowercase UUIDs: Guard.id refuses every other spelling of an identifier. */
    static final String TOPIC_ID = "11111111-1111-4111-8111-111111111111";
    static final String BRANCH_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    static final String RESUME_BRANCH_ID = "13131313-1313-4313-8313-131313131313";
    static final String QUESTION_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    static final String ANSWER_ID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
    static final String LATER_ANSWER_ID = "dddddddd-dddd-4ddd-8ddd-dddddddddddc";
    static final String OPERATION_ID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
    static final String REVISION_ID = "ffffffff-ffff-4fff-8fff-ffffffffffff";

    static final String FIRST_QUESTION = "first question in this branch";
    static final String LATER_QUESTION = "the follow-up question";
    static final String QUOTED_LINE = "the sentence the learner selected";
    static final String SUMMARY_JSON = "{\"summary\":\"a stored summary\",\"key_points\":[\"one\"]}";

    static final long NOW = 1760000000000L;
    static final GenerationModel MODEL = GenerationModel.PRO;

    private WiringDoubles() {
    }

    /** The one question the home composer asks about the credential. */
    static final class Key implements KeyVault {

        boolean configured;

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public boolean isConfigured(String providerId) {
            return configured;
        }
    }

    /** The home path's rows: the list it was given, and the write it was asked to make. */
    static final class Homes implements HomeStore {

        final List<Row> rows = new ArrayList<Row>();
        HomeStore.StatsRaw statsRaw;
        int statsRawCalls;
        FirstTopicCreation.Refusal refusal;
        int createCalls;
        Topic createdTopic;
        Branch createdBranch;
        Message createdUser;
        Message createdAi;
        GenerationOperation createdOperation;

        @Override
        public List<Row> recentTopics() {
            return rows;
        }

        @Override
        public StatsRaw statsRaw() {
            statsRawCalls++;
            return statsRaw;
        }

        @Override
        public FirstTopicCreation.Outcome createFirstTopic(Topic topic, Branch rootBranch,
                Message userMessage, Message aiMessage, GenerationOperation operation) {
            createCalls++;
            createdTopic = topic;
            createdBranch = rootBranch;
            createdUser = userMessage;
            createdAi = aiMessage;
            createdOperation = operation;
            if (refusal != null) {
                return FirstTopicCreation.Outcome.refused(refusal, "refused by the fake store");
            }
            return FirstTopicCreation.Outcome.created(topic);
        }
    }

    /**
     * The admission seam: it builds the request through the caller's own assembler, which is what
     * keeps "the request is the real assembler's build" true in a JVM test.
     *
     * <p>It also runs a supplied {@code localWrite} before returning, because production writes the
     * rows before it opens the call and refuses the submission when that write refuses. The two
     * counters split the admissions by whether rows were written, so a test can tell the first-topic
     * path from a follow-up without naming the coordinator's private ordering.
     */
    static final class Generations implements GenerationPort {

        GenerationCoordinator.SubmitOutcome submitOutcome =
                GenerationCoordinator.SubmitOutcome.accepted(OPERATION_ID, true);
        GenerationCoordinator.SubmitOutcome firstOutcome =
                GenerationCoordinator.SubmitOutcome.accepted(OPERATION_ID, true);
        int submitCalls;
        int firstCalls;
        GenerationCommand lastCommand;
        DeepSeekRequest lastRequest;

        @Override
        public GenerationCoordinator.SubmitOutcome submit(GenerationCommand command,
                GenerationCoordinator.Assembler assembler) {
            return submit(command, assembler, null);
        }

        @Override
        public GenerationCoordinator.SubmitOutcome submit(GenerationCommand command,
                GenerationCoordinator.Assembler assembler,
                GenerationCoordinator.LocalWrite localWrite) {
            if (localWrite == null) {
                submitCalls++;
            } else {
                firstCalls++;
            }
            lastCommand = command;
            lastRequest = assembler.assemble(command);
            if (localWrite != null && !localWrite.persist(GenerationOperation.start(command, NOW + 1),
                    NOW + 1)) {
                return GenerationCoordinator.SubmitOutcome.refused(
                        GenerationCoordinator.Refusal.STORAGE, null);
            }
            return localWrite == null ? submitOutcome : firstOutcome;
        }
    }

    /** The conversation path's rows and decisions, answered from what a test set up. */
    static final class Chats implements ChatStore {

        Conversation conversation = new Conversation("", new ArrayList<Position>());
        List<AnswerRevision> versions = new ArrayList<AnswerRevision>();
        DomainResult<AnswerRevision> selectResult;
        DomainResult<GenerationCommand> retryResult;
        DomainResult<GenerationCommand> resetResult;
        List<ContextPlan.Entry> history = new ArrayList<ContextPlan.Entry>();
        int historyOrdinal = -1;
        /** When set, the history read raises - the store-side "birth snapshot untrustworthy" channel. */
        RuntimeException historyFailure;
        int nextOrdinal;
        int appendCalls;
        Message appendedUser;
        Message appendedAi;
        BranchCreation.Refusal branchRefusal;
        int createBranchCalls;
        Branch createdBranch;
        ContextSnapshotDao.Row createdSnapshot;
        Branch branch;
        List<ChildBranch> children = new ArrayList<ChildBranch>();
        final Map<String, Message> positions = new HashMap<String, Message>();
        Persona writtenPersona;
        boolean personaWriteFails;

        @Override
        public Conversation read(String branchId) {
            return conversation;
        }

        @Override
        public List<AnswerRevision> versions(String messageId) {
            return versions;
        }

        @Override
        public List<ChildBranch> children(String branchId, String messageId) {
            return children;
        }

        @Override
        public Map<String, List<Annotation>> annotations(List<ChatMessage> answers) {
            return new HashMap<String, List<Annotation>>();
        }

        @Override
        public void applyHighlight(String messageId, String answerText, int proseIndex,
                Highlighter.Plan plan, long now) {
        }

        @Override
        public DomainResult<AnswerRevision> selectRevision(String messageId, String revisionId,
                long now) {
            return selectResult;
        }

        @Override
        public DomainResult<GenerationCommand> retry(String messageId, GenerationModel model,
                boolean thinkingEnabled, long now) {
            return retryResult;
        }

        @Override
        public DomainResult<GenerationCommand> reset(String messageId, GenerationModel model,
                boolean thinkingEnabled, long now) {
            return resetResult;
        }

        @Override
        public List<ContextPlan.Entry> history(String branchId, int questionOrdinal) {
            historyOrdinal = questionOrdinal;
            if (historyFailure != null) {
                throw historyFailure;
            }
            return history;
        }

        @Override
        public int nextOrdinal(String branchId) {
            return nextOrdinal;
        }

        @Override
        public void appendTurn(Message userMessage, Message aiMessage) {
            appendCalls++;
            appendedUser = userMessage;
            appendedAi = aiMessage;
        }

        @Override
        public BranchCreation.Outcome createBranch(Branch newBranch, ContextSnapshotDao.Row snapshot) {
            createBranchCalls++;
            createdBranch = newBranch;
            createdSnapshot = snapshot;
            if (branchRefusal != null) {
                return BranchCreation.Outcome.refused(branchRefusal, "refused by the fake store");
            }
            return BranchCreation.Outcome.created(newBranch);
        }

        @Override
        public Branch branch(String branchId) {
            return branch;
        }

        @Override
        public void updatePersona(String branchId, Persona persona) {
            if (personaWriteFails) {
                throw new IllegalStateException("the narrow write was refused");
            }
            writtenPersona = persona;
        }

        @Override
        public Message position(String messageId) {
            return positions.get(messageId);
        }
    }
}
