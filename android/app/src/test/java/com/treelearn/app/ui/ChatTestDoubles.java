package com.treelearn.app.ui;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Annotation;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.BranchSource;
import com.treelearn.core.domain.ContextSnapshotCodec;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;
import com.treelearn.app.reading.ReadingStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Doubles for the chat suites. They are separate from {@link TestDoubles} so the home suite keeps its
 * own shape, and they reuse that class for the three ports both screens share.
 */
final class ChatTestDoubles {

    static final String BRANCH = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    static final String BRANCH_2 = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
    static final String M_ASK = "11111111-1111-4111-8111-111111111111";
    static final String M_ANSWER = "22222222-2222-4222-8222-222222222222";
    static final String M_ASK_2 = "33333333-3333-4333-8333-333333333333";
    static final String M_ANSWER_2 = "44444444-4444-4444-8444-444444444444";
    static final String REV_1 = "55555555-5555-4555-8555-555555555555";
    static final String REV_2 = "66666666-6666-4666-8666-666666666666";
    static final String OP = "77777777-7777-4777-8777-777777777777";
    static final String CHILD_BRANCH = "88888888-8888-4888-8888-888888888888";
    static final String PARENT_BRANCH = "99999999-9999-4999-8999-999999999999";
    static final String TOPIC = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    static final String BIRTH_SNAPSHOT = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    /** One stored highlight row's id, for tests that hand the store's own marks back through a read. */
    static final String ANNOTATION = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";

    static final String QUESTION = "why does attention scale by root d";
    static final String ANSWER = "because the dot products grow with d";
    static final String REASONING = "recall the variance of a dot product";

    private ChatTestDoubles() {
    }

    static ChatMessage ask(String id, String text) {
        return ChatMessage.user(id, text, "", 1_700_000_000_000L);
    }

    static ChatMessage stableAnswer(String id, String answer) {
        return ChatMessage.ai(id, answer, REASONING, ChatMessage.Status.STABLE,
                ChatMessage.Phase.ANSWER, REV_1, 1, 0, false, 1_700_000_000_000L);
    }

    /** T-066: an attempt that is filling the reasoning stream right now. */
    static ChatMessage formingReasoning(String id, String reasoning) {
        return ChatMessage.ai(id, "", reasoning, ChatMessage.Status.GENERATING,
                ChatMessage.Phase.REASONING, "", 0, 0, false, 1_700_000_000_000L);
    }

    /** T-066: a stable answer that never had reasoning - no disclosure at all. */
    static ChatMessage stableAnswerWithoutReasoning(String id, String answer) {
        return ChatMessage.ai(id, answer, "", ChatMessage.Status.STABLE,
                ChatMessage.Phase.ANSWER, REV_1, 1, 0, false, 1_700_000_000_000L);
    }

    /** A conversation: the question, the answer, and wherever the strip is attached. */
    static ChatData.Snapshot snapshot(List<ChatMessage> messages) {
        return read("Transformer attention", messages, Collections.<Recommendation>emptyList(), "");
    }

    /**
     * A read a suite can build by hand: a conversation with no branch row behind it.
     *
     * <p>It lives here rather than on {@link ChatData.Snapshot} because no production path reads a
     * conversation without the branch row the reading rules resolve against - {@code ChatWiring}
     * always reads both - so a four-argument factory would be a second way to build a snapshot the
     * app itself never takes.
     */
    static ChatData.Snapshot read(String title, List<ChatMessage> messages,
            List<Recommendation> recommendations, String revisionId) {
        return ChatData.Snapshot.of(title, messages, recommendations, revisionId, null,
                Collections.<Message>emptyList(), null);
    }

    /**
     * A read that also carries the marks the store holds for each answer.
     *
     * <p>The annotations channel rides the same read as the rows, so a test about a projection
     * that must be corrected by the store has to hand the store's own answer to the read: an empty
     * map here is "the store holds no mark", which is exactly what a refused highlight leaves
     * behind.
     */
    static ChatData.Snapshot readWithMarks(String title, List<ChatMessage> messages,
            java.util.Map<String, List<Annotation>> annotations) {
        return ChatData.Snapshot.of(title, messages, Collections.<Recommendation>emptyList(), "",
                null, Collections.<Message>emptyList(), null, annotations);
    }

    /** A read that also carries the branch row behind it and the source card the wiring built. */
    static ChatData.Snapshot withBranch(List<ChatMessage> messages, Branch branch, SourceCard card) {
        return ChatData.Snapshot.of("Transformer attention", messages,
                Collections.<Recommendation>emptyList(), "", branch,
                Collections.<Message>emptyList(), card);
    }

    /**
     * A child branch whose provenance anchors the given sentence, or the whole answer when the
     * quote is empty (T-065).
     */
    static Branch childBranch(String anchorQuote) {
        boolean ranged = anchorQuote != null && !anchorQuote.isEmpty();
        BranchAnchor anchor = ranged
                ? BranchAnchor.ofRange(0, anchorQuote.length(), anchorQuote,
                        ContextSnapshotCodec.digestOf(anchorQuote)).value()
                : BranchAnchor.wholeAnswer();
        DomainResult<Branch> created = Branch.child(TOPIC, BranchSource.restore(PARENT_BRANCH,
                M_ANSWER, REV_1, anchor, BIRTH_SNAPSHOT), 1_700_000_000_000L);
        if (created.isFailure()) {
            throw new IllegalStateException("the test child branch was refused: " + created.error());
        }
        return created.value();
    }

    /** Records every call, so a test can assert that a gesture did or did not reach the store. */
    static final class FakeChatCommands implements ChatCommands {

        ChatCommandResult sendResult = ChatCommandResult.ran(OP);
        ChatCommandResult retryResult = ChatCommandResult.ran(OP);
        ChatCommandResult resetResult = ChatCommandResult.ran(OP);
        ChatCommandResult selectResult = ChatCommandResult.ran(OP);
        ChatCommandResult forkResult = ChatCommandResult.openedBranch(BRANCH_2);
        ChatCommandResult highlightResult = ChatCommandResult.ran(OP);
        List<AnswerRevision> versions = Collections.emptyList();
        Runnable afterAcceptedSend;

        final List<String> calls = new ArrayList<String>();
        String lastText = "";
        String lastQuote = "";
        String lastMessageId = "";
        String lastRevisionId = "";
        String lastQuestion = "";
        String lastAttachmentId;
        int lastForkStart;
        int lastForkEnd;
        GenerationModel lastModel;
        boolean lastThinking;
        long lastNow;

        @Override
        public ChatCommandResult send(String branchId, String text, String quote,
                String attachmentId, GenerationModel model, boolean thinkingEnabled) {
            calls.add("send");
            lastText = text;
            lastQuote = quote;
            lastAttachmentId = attachmentId;
            lastModel = model;
            lastThinking = thinkingEnabled;
            if (sendResult.isAccepted() && afterAcceptedSend != null) {
                afterAcceptedSend.run();
            }
            return sendResult;
        }

        @Override
        public ChatCommandResult retry(String messageId, GenerationModel model,
                boolean thinkingEnabled, long now) {
            calls.add("retry");
            lastMessageId = messageId;
            lastModel = model;
            lastThinking = thinkingEnabled;
            lastNow = now;
            return retryResult;
        }

        @Override
        public ChatCommandResult reset(String messageId, GenerationModel model,
                boolean thinkingEnabled, long now) {
            calls.add("reset");
            lastMessageId = messageId;
            lastModel = model;
            lastThinking = thinkingEnabled;
            lastNow = now;
            return resetResult;
        }

        @Override
        public ChatCommandResult selectRevision(String messageId, String revisionId, long now) {
            calls.add("selectRevision");
            lastMessageId = messageId;
            lastRevisionId = revisionId;
            lastNow = now;
            return selectResult;
        }

        @Override
        public ChatCommandResult fork(String messageId, String question, String quote,
                int startInclusive, int endExclusive) {
            calls.add("fork");
            lastMessageId = messageId;
            lastQuestion = question;
            lastQuote = quote == null ? "" : quote;
            lastForkStart = startInclusive;
            lastForkEnd = endExclusive;
            return forkResult;
        }

        @Override
        public List<AnswerRevision> versions(String messageId) {
            calls.add("versions");
            lastMessageId = messageId;
            return versions;
        }

        @Override
        public ChatCommandResult applyHighlight(String messageId, String answerText, int proseIndex,
                int startInclusive, int endInclusive) {
            calls.add("applyHighlight");
            lastMessageId = messageId;
            return highlightResult;
        }

        boolean called(String name) {
            return calls.contains(name);
        }
    }

    /** Queues the work instead of running it, so a test can control when the store answers. */
    static final class QueueingExecutor implements java.util.concurrent.Executor {

        private final List<Runnable> pending = new ArrayList<Runnable>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        boolean runNext() {
            if (pending.isEmpty()) {
                return false;
            }
            pending.remove(0).run();
            return true;
        }

        int drain() {
            int ran = 0;
            while (!pending.isEmpty()) {
                List<Runnable> batch = new ArrayList<Runnable>(pending);
                pending.clear();
                for (Runnable runnable : batch) {
                    runnable.run();
                    ran++;
                }
            }
            return ran;
        }
    }
    static final class FakeChatData implements ChatData {

        ChatData.Snapshot snapshot = snapshot(Collections.<ChatMessage>emptyList());
        List<ChildBranch> children = Collections.emptyList();
        boolean throwsInstead;
        boolean childrenThrow;
        int reads;
        final List<String> branchesRead = new ArrayList<String>();

        @Override
        public Snapshot read(String branchId) {
            reads++;
            branchesRead.add(branchId);
            if (throwsInstead) {
                throw new IllegalStateException("the branch could not be read");
            }
            return snapshot;
        }

        @Override
        public List<ChildBranch> children(String branchId, String messageId) {
            branchesRead.add(branchId + "/" + messageId);
            if (childrenThrow) {
                throw new IllegalStateException("the child list could not be read");
            }
            return children;
        }
    }

    static final class RecordingChatSink implements ChatSink {

        final List<ChatViewState> states = new ArrayList<ChatViewState>();
        final List<String> copied = new ArrayList<String>();
        final List<String> branchesOpened = new ArrayList<String>();
        final List<String> branchQuestions = new ArrayList<String>();
        final List<String> childrenOpened = new ArrayList<String>();
        final List<List<ChildBranch>> childrenListed = new ArrayList<List<ChildBranch>>();
        final List<String> versionMenus = new ArrayList<String>();
        final List<String> overviewsOpened = new ArrayList<String>();
        final List<String> navigationsOpened = new ArrayList<String>();
        final List<ReadingPosition> restored = new ArrayList<ReadingPosition>();
        /**
         * Every marks push the presenter makes, in order. The screen draws from the newest one, so
         * a test that cares whether a projection survived an authoritative read has to see the
         * pushes themselves - an empty list here means the presenter never repushed, which is a
         * different failure from a push that carried the old marks.
         */
        final List<java.util.Map<String, List<Annotation>>> marksRenders =
                new ArrayList<java.util.Map<String, List<Annotation>>>();
        int settingsOpened;
        int closed;
        int keyboardDismissals;

        @Override
        public void render(ChatViewState state) {
            states.add(state);
        }

        @Override
        public void dismissKeyboard() {
            keyboardDismissals++;
        }

        @Override
        public void renderAnnotations(java.util.Map<String, List<Annotation>> annotations) {
            marksRenders.add(annotations);
        }

        /** The marks the screen would be drawing right now; null before the first push. */
        java.util.Map<String, List<Annotation>> lastMarks() {
            return marksRenders.isEmpty() ? null : marksRenders.get(marksRenders.size() - 1);
        }

        @Override
        public void renderPosition(String branchId, ReadingPosition position) {
            restored.add(position);
        }

        @Override
        public void copyToClipboard(String text) {
            copied.add(text);
        }

        @Override
        public void openBranch(String branchId) {
            branchesOpened.add(branchId);
        }

        @Override
        public void openBranch(String branchId, String initialQuestion) {
            branchesOpened.add(branchId);
            if (!initialQuestion.isEmpty()) {
                branchQuestions.add(initialQuestion);
            }
        }

        @Override
        public void openChildren(String messageId, List<ChildBranch> children) {
            childrenOpened.add(messageId);
            childrenListed.add(children);
        }

        @Override
        public void openVersions(String messageId, List<AnswerRevision> versions) {
            versionMenus.add(messageId + ":" + versions.size());
        }

        @Override
        public void openSettings() {
            settingsOpened++;
        }

        @Override
        public void openOverview(String branchId) {
            overviewsOpened.add(branchId);
        }

        @Override
        public void openNavigation(String branchId) {
            navigationsOpened.add(branchId);
        }

        @Override
        public void close() {
            closed++;
        }

        ChatViewState last() {
            return states.get(states.size() - 1);
        }
    }

    static Recommendation recommendation(String question) {
        return new Recommendation(question, Recommendation.Kind.FOLLOW_UP);
    }

    static List<ChatMessage> messages(ChatMessage... items) {
        return new ArrayList<ChatMessage>(Arrays.asList(items));
    }

    /**
     * A reading store for the suites that exercise the conversation and not the reading position: the
     * branch has nothing stored and its unread sequence never moves, so the reading rules are wired
     * but never asked to decide anything.
     */
    static final class NoPositionStore implements ReadingStore {

        @Override
        public ReadingAnchor anchorOf(String branchId) {
            return null;
        }

        @Override
        public void saveAnchor(String branchId, ReadingAnchor anchor) {
        }

        @Override
        public void saveSeen(Branch branch) {
        }
    }
}
