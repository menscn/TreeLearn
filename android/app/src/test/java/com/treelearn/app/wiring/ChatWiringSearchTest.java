package com.treelearn.app.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.search.web.ChatSearchStep;
import com.treelearn.app.search.web.SearchTestDoubles;
import com.treelearn.app.search.web.WebSearchGateway;
import com.treelearn.app.ui.ChatCommandResult;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.ai.prompt.DeepSeekMessage;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

/**
 * T-092: the conversation send path's grounding semantics, end to end over the real request
 * assembler:
 *
 * <ul>
 *   <li>[NOKEY] a send with the switch on and no engine key is refused before any row is written and
 *       before any attempt is admitted;</li>
 *   <li>an OK retrieval injects the grounding block ahead of the question in the <em>request</em>
 *       only — the persisted user row stays exactly the learner's words, which is what keeps the
 *       fork snapshot contract (references, never content) untouched;</li>
 *   <li>[OFF]-degrade a failed retrieval still generates, with no block and the inline degrade
 *       notice; [PART] an empty result degrades the same way with its own notice;</li>
 *   <li>the switch off is byte-for-byte the old path: no gateway call, no notice;</li>
 *   <li>retry/reset never run a search — a replacement's context stays identical to the attempt it
 *       replaces.</li>
 * </ul>
 */
public class ChatWiringSearchTest {

    private final WiringDoubles.Key key = new WiringDoubles.Key();
    private final WiringDoubles.Chats store = new WiringDoubles.Chats();
    private final WiringDoubles.Generations generation = new WiringDoubles.Generations();
    private final SearchTestDoubles.FakeStore searchStore = new SearchTestDoubles.FakeStore();
    private final SearchTestDoubles.FakeCredentials credentials = new SearchTestDoubles.FakeCredentials();
    private final SearchTestDoubles.FakeGateway gateway = new SearchTestDoubles.FakeGateway();

    private ChatWiring wiring() {
        key.configured = true;
        store.nextOrdinal = 0;
        return new ChatWiring(store, generation, key,
                Clock.fixed(Instant.ofEpochMilli(WiringDoubles.NOW), ZoneOffset.UTC),
                PromptLimits.budget(), null, () -> true,
                new ChatSearchStep(searchStore, credentials, gateway));
    }

    private void searchOn(boolean withKey) {
        searchStore.searchEnabled = true;
        searchStore.hasKey = withKey;
        credentials.key = new char[] {'k'};
    }

    private ChatCommandResult send(String question) {
        return wiring().send(WiringDoubles.BRANCH_ID, question, "", null, WiringDoubles.MODEL, false);
    }

    private String lastUserInput() {
        DeepSeekMessage last = generation.lastRequest.messages
                .get(generation.lastRequest.messages.size() - 1);
        return last.content;
    }

    @Test
    public void sendWithSearchAndNoKeyIsRefusedBeforeAnyRowOrAttempt() {
        searchOn(false);

        ChatCommandResult result = send("a current affairs question");

        assertFalse(result.isAccepted());
        assertEquals(HomeViewState.Notice.SEARCH_KEY_MISSING, result.notice());
        assertEquals("no turn may be written for a [NOKEY] send", 0, store.appendCalls);
        assertEquals("no attempt may be admitted for a [NOKEY] send", 0, generation.submitCalls);
        assertEquals(0, gateway.calls);
    }

    @Test
    public void sendWithSearchOkInjectsTheBlockAheadOfTheQuestionInTheRequestOnly() {
        searchOn(true);
        gateway.next = WebSearchGateway.Outcome.ok(
                Arrays.asList(SearchTestDoubles.item(1, "a fresh result", "example.com", "s")));

        ChatCommandResult result = send("a current affairs question");

        assertTrue(result.isAccepted());
        assertEquals(1, generation.submitCalls);
        String input = lastUserInput();
        assertTrue(input, input.contains("联网检索结果"));
        assertTrue(input, input.contains("a fresh result"));
        assertTrue(input, input.contains("a current affairs question"));
        assertEquals("the persisted row stays the learner's words alone",
                "a current affairs question", store.appendedUser.userContent);
    }

    @Test
    public void sendWithSearchFailureStillGeneratesWithoutGroundingAndNoticesIt() {
        searchOn(true);
        gateway.next = WebSearchGateway.Outcome.failed();

        ChatCommandResult result = send("a current affairs question");

        assertTrue("a retrieval failure must never block the answer", result.isAccepted());
        assertEquals(HomeViewState.Notice.SEARCH_FAILED, result.postNotice());
        assertFalse(lastUserInput().contains("联网检索结果"));
        assertEquals(1, generation.submitCalls);
    }

    @Test
    public void sendWithAnEmptyResultStillGeneratesAndNoticesTheEmptyResult() {
        searchOn(true);
        gateway.next = WebSearchGateway.Outcome.empty();

        ChatCommandResult result = send("a current affairs question");

        assertTrue(result.isAccepted());
        assertEquals(HomeViewState.Notice.SEARCH_EMPTY, result.postNotice());
        assertFalse(lastUserInput().contains("联网检索结果"));
    }

    @Test
    public void sendWithTheSearchOffIsExactlyTheOldPath() {
        searchStore.searchEnabled = false;
        searchStore.hasKey = true;

        ChatCommandResult result = send("a current affairs question");

        assertTrue(result.isAccepted());
        assertNull(result.postNotice());
        assertEquals(0, gateway.calls);
        assertTrue(lastUserInput().contains("a current affairs question"));
    }

    @Test
    public void retryNeverRunsASearch() {
        searchOn(true);
        AnswerRevision stable = revision();
        store.conversation = new ChatStore.Conversation("", Arrays.asList(
                user(0, "the earlier question"),
                answer(1, WiringDoubles.ANSWER_ID, stable)));
        store.retryResult = DomainResult.ok(command());
        store.positions.put(WiringDoubles.ANSWER_ID, Message.restore(WiringDoubles.ANSWER_ID,
                WiringDoubles.BRANCH_ID, com.treelearn.core.domain.MessageRole.AI, 1, null, "",
                WiringDoubles.NOW, stable.id, null));

        ChatCommandResult result = wiring().retry(WiringDoubles.ANSWER_ID, WiringDoubles.MODEL,
                false, WiringDoubles.NOW);

        assertTrue(result.isAccepted());
        assertEquals("a replacement re-answers the same context without a fresh retrieval",
                0, gateway.calls);
        assertFalse(lastUserInput().contains("联网检索结果"));
    }

    // ---------------------------------------------------------------- helpers

    private static ChatStore.Position user(int ordinal, String text) {
        return new ChatStore.Position(Message.user(WiringDoubles.BRANCH_ID, ordinal, text, "",
                WiringDoubles.NOW), null, 0, 0, false, "", "");
    }

    private static ChatStore.Position answer(int ordinal, String messageId,
            AnswerRevision revision) {
        return new ChatStore.Position(Message.restore(messageId, WiringDoubles.BRANCH_ID,
                com.treelearn.core.domain.MessageRole.AI, ordinal, null, "", WiringDoubles.NOW,
                revision.id, null), revision, 1, 0, false, "", "");
    }

    private static AnswerRevision revision() {
        DomainResult<AnswerRevision> created = AnswerRevision.create(WiringDoubles.ANSWER_ID, 1,
                "the reasoning", "the answer", WiringDoubles.MODEL, false, AnswerPolicy.version(),
                WiringDoubles.NOW);
        assertTrue(created.isOk());
        return created.value();
    }

    private static GenerationCommand command() {
        DomainResult<GenerationCommand> pinned = GenerationCommand.create(WiringDoubles.BRANCH_ID,
                WiringDoubles.ANSWER_ID, AnswerPolicy.PURPOSE, AnswerPolicy.version(),
                WiringDoubles.MODEL, false, WiringDoubles.NOW);
        assertTrue(pinned.isOk());
        return pinned.value();
    }
}
