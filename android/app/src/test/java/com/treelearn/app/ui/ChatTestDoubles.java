package com.treelearn.app.ui;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Branch;
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
                Collections.<Message>emptyList());
    }

    /** Records every call, so a test can assert that a gesture did or did not reach the store. */
    static final class FakeChatCommands implements ChatCommands {

        ChatCommandResult sendResult = ChatCommandResult.ran(OP);
        ChatCommandResult retryResult = ChatCommandResult.ran(OP);
        ChatCommandResult resetResult = ChatCommandResult.ran(OP);
        ChatCommandResult selectResult = ChatCommandResult.ran(OP);
        ChatCommandResult forkResult = ChatCommandResult.openedBranch(BRANCH_2);
        List<AnswerRevision> versions = Collections.emptyList();

        final List<String> calls = new ArrayList<String>();
        String lastText = "";
        String lastQuote = "";
        String lastMessageId = "";
        String lastRevisionId = "";
        String lastQuestion = "";
        GenerationModel lastModel;
        boolean lastThinking;
        long lastNow;

        @Override
        public ChatCommandResult send(String branchId, String text, String quote, GenerationModel model,
                boolean thinkingEnabled) {
            calls.add("send");
            lastText = text;
            lastQuote = quote;
            lastModel = model;
            lastThinking = thinkingEnabled;
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
        public ChatCommandResult fork(String messageId, String question) {
            calls.add("fork");
            lastMessageId = messageId;
            lastQuestion = question;
            return forkResult;
        }

        @Override
        public List<AnswerRevision> versions(String messageId) {
            calls.add("versions");
            lastMessageId = messageId;
            return versions;
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
            return children;
        }
    }

    static final class RecordingChatSink implements ChatSink {

        final List<ChatViewState> states = new ArrayList<ChatViewState>();
        final List<String> copied = new ArrayList<String>();
        final List<String> branchesOpened = new ArrayList<String>();
        final List<String> childrenOpened = new ArrayList<String>();
        final List<List<ChildBranch>> childrenListed = new ArrayList<List<ChildBranch>>();
        final List<String> versionMenus = new ArrayList<String>();
        final List<String> overviewsOpened = new ArrayList<String>();
        final List<String> navigationsOpened = new ArrayList<String>();
        final List<ReadingPosition> restored = new ArrayList<ReadingPosition>();
        int settingsOpened;
        int closed;

        @Override
        public void render(ChatViewState state) {
            states.add(state);
        }

        @Override
        public void renderPosition(ReadingPosition position) {
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
        public void openChildren(String branchId, String messageId, List<ChildBranch> children) {
            childrenOpened.add(branchId + "/" + messageId);
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
