package com.treelearn.app.search.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.SearchSettingsPresenter;

import org.junit.Test;

import java.util.Arrays;

/**
 * The conversation send-path's retrieval step (T-092, SPEC-2026-09-23 异常降级路径):
 *
 * <ul>
 *   <li>[OFF] the master switch off answers OFF and never touches the gateway;</li>
 *   <li>[NOKEY] the selected engine without a key answers NOKEY before any network call, and an
 *       unreadable credential answers NOKEY too — the store said "has" but the keystore could not
 *       honestly produce it;</li>
 *   <li>OK runs the gateway with the selected engine, the query and the tier's result count, and
 *       returns the injection block;</li>
 *   <li>[OFF]-degrade a gateway failure or crash answers FAILED with no block — never an exception;</li>
 *   <li>[PART] an empty result answers EMPTY with no block;</li>
 *   <li>the key buffer is wiped once the call returns, whatever the outcome.</li>
 * </ul>
 */
public class ChatSearchStepTest {

    private final SearchTestDoubles.FakeStore store = new SearchTestDoubles.FakeStore();
    private final SearchTestDoubles.FakeCredentials credentials = new SearchTestDoubles.FakeCredentials();
    private final SearchTestDoubles.FakeGateway gateway = new SearchTestDoubles.FakeGateway();

    private ChatSearchStep step() {
        return new ChatSearchStep(store, credentials, gateway);
    }

    @Test
    public void theSwitchOffAnswersOffWithoutTouchingTheGateway() {
        store.searchEnabled = false;
        store.hasKey = true;

        ChatSearchStep.Result result = step().run("a question");

        assertEquals(ChatSearchStep.Status.OFF, result.status());
        assertEquals("", result.block());
        assertEquals(0, gateway.calls);
    }

    @Test
    public void anEngineWithoutAStoredKeyAnswersNokeyBeforeAnyNetworkCall() {
        store.searchEnabled = true;
        store.hasKey = false;

        ChatSearchStep.Result result = step().run("a question");

        assertEquals(ChatSearchStep.Status.NOKEY, result.status());
        assertEquals("", result.block());
        assertEquals(0, gateway.calls);
        assertEquals("no credential may even be read for a [NOKEY] engine", 0, credentials.calls);
    }

    @Test
    public void anUnreadableCredentialAnswersNokeyToo() {
        store.searchEnabled = true;
        store.hasKey = true;
        credentials.key = null;

        assertEquals(ChatSearchStep.Status.NOKEY, step().run("a question").status());
        assertEquals(0, gateway.calls);
    }

    @Test
    public void okRunsTheSelectedEngineWithTheTierCountAndCarriesTheBlock() {
        store.searchEnabled = true;
        store.engine = SearchSettingsPresenter.Engine.TAVILY;
        store.tier = SearchSettingsPresenter.Tier.FIVE;
        store.hasKey = true;
        gateway.next = WebSearchGateway.Outcome.ok(
                Arrays.asList(SearchTestDoubles.item(1, "a title", "example.com", "a snippet")));

        ChatSearchStep.Result result = step().run("a question");

        assertEquals(ChatSearchStep.Status.OK, result.status());
        assertEquals(1, gateway.calls);
        assertEquals(SearchSettingsPresenter.Engine.TAVILY, gateway.lastEngine);
        assertEquals("a question", gateway.lastQuery);
        assertEquals("the FIVE tier asks for five results", 5, gateway.lastCount);
        assertTrue(result.block(), result.block().contains("a title"));
    }

    @Test
    public void aGatewayFailureDegradesToFailedInsteadOfBlockingTheSend() {
        store.searchEnabled = true;
        store.hasKey = true;
        gateway.next = WebSearchGateway.Outcome.failed();

        ChatSearchStep.Result result = step().run("a question");

        assertEquals(ChatSearchStep.Status.FAILED, result.status());
        assertEquals("", result.block());
    }

    @Test
    public void aGatewayCrashDegradesToFailedToo() {
        store.searchEnabled = true;
        store.hasKey = true;
        gateway.throwNext = new IllegalStateException("boom");

        assertEquals(ChatSearchStep.Status.FAILED, step().run("a question").status());
    }

    @Test
    public void anEmptyResultDegradesToEmpty() {
        store.searchEnabled = true;
        store.hasKey = true;
        gateway.next = WebSearchGateway.Outcome.empty();

        ChatSearchStep.Result result = step().run("a question");

        assertEquals(ChatSearchStep.Status.EMPTY, result.status());
        assertEquals("", result.block());
    }

    @Test
    public void theKeyBufferIsWipedOnceTheCallReturnsWhateverTheOutcome() {
        store.searchEnabled = true;
        store.hasKey = true;
        gateway.next = WebSearchGateway.Outcome.failed();

        char[] handed = credentials.key;
        step().run("a question");

        for (char c : handed) {
            assertEquals("the key material must be wiped after the transport call", '\0', c);
        }
    }

    @Test
    public void theSelectedEngineCarriesItsChineseLabel() {
        store.engine = SearchSettingsPresenter.Engine.ZHIPU;
        assertEquals("智谱", step().engineLabel());
        store.engine = SearchSettingsPresenter.Engine.TAVILY;
        assertEquals("Tavily", step().engineLabel());
        store.engine = SearchSettingsPresenter.Engine.BOCHA;
        assertEquals("博查", step().engineLabel());
    }

    @Test
    public void enabledAndCredentialReadsAnswerTheStoredFacts() {
        assertFalse(step().enabled());
        store.searchEnabled = true;
        assertTrue(step().enabled());
        store.hasKey = true;
        assertTrue(step().hasCredential());
        store.hasKey = false;
        assertFalse(step().hasCredential());
    }
}
