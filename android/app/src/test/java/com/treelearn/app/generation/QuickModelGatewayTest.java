package com.treelearn.app.generation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.artifact.DerivedRequest;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.secret.SecretStoreError;
import com.treelearn.data.secret.SecretStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The fast-model route of the derived chain (T-089, SPEC-2026-09-23 分工回退规则): a configured
 * fast assignment is injected into the gateway as its provider endpoint, an unconfigured one keeps
 * the legacy chat-model path byte for byte, and a fast-model failure never reruns the request
 * through the chat model — the derived chain's existing failure semantics (a null output, no
 * artifact) stay the only answer.
 *
 * <p>The fallback judgment itself is not re-tested here: it is 088's shared
 * {@code ModelSettingsPresenter.resolve} behind the {@code Routes} port, and this gateway only
 * answers "route or legacy". That single seam is what keeps the judgment in one place.
 */
public final class QuickModelGatewayTest {

    private static final String PROVIDER_ID = "provider-fast-1";
    private static final String BASE_URL = "https://api.example.com";
    private static final String FAST_WIRE_ID = "fast-model-x";

    private static DeepSeekRequest aDerivedRequest() {
        return DerivedRequest.rootTitle("00000000-0000-0000-0000-000000000001",
                "the root question", GenerationModel.FLASH,
                new com.treelearn.ai.prompt.PromptBudget(200_000, 4_000)).assemble();
    }

    /** Records every endpoint-addressed open; can replay a terminal outcome into the sink. */
    private static final class FakeStreams implements QuickModelGateway.EndpointStreams {

        final List<String> apiKeys = new ArrayList<String>();
        final List<ProviderEndpoint> endpoints = new ArrayList<ProviderEndpoint>();
        final List<DeepSeekRequest> requests = new ArrayList<DeepSeekRequest>();
        final List<GenerationSink> sinks = new ArrayList<GenerationSink>();
        /** When non-null, the fake reports this outcome to the sink during open. */
        StreamOutcome terminalToReport;

        @Override
        public AiGateway.Call open(String apiKey, ProviderEndpoint endpoint,
                DeepSeekRequest request, GenerationSink sink) {
            apiKeys.add(apiKey);
            endpoints.add(endpoint);
            requests.add(request);
            sinks.add(sink);
            if (terminalToReport != null) {
                sink.onTerminal(terminalToReport);
            }
            return new FakeCall();
        }
    }

    private static final class FakeCall implements AiGateway.Call {

        boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /** Records every legacy-path open. */
    private static final class FakeFallback implements GenerationGateway {

        final List<GenerationModel> models = new ArrayList<GenerationModel>();
        final List<DeepSeekRequest> requests = new ArrayList<DeepSeekRequest>();
        final List<GenerationSink> sinks = new ArrayList<GenerationSink>();

        @Override
        public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
            models.add(model);
            requests.add(request);
            sinks.add(sink);
            return new Stream() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    private static QuickModelGateway.Route route() {
        return new QuickModelGateway.Route(PROVIDER_ID, BASE_URL, FAST_WIRE_ID);
    }

    @Test
    public void aConfiguredFastAssignmentOpensTheEndpointWithItsProviderKey() throws Exception {
        FakeStreams streams = new FakeStreams();
        FakeFallback fallback = new FakeFallback();
        final List<String> keyReads = new ArrayList<String>();
        QuickModelGateway gateway = new QuickModelGateway(() -> route(),
                providerId -> {
                    keyReads.add(providerId);
                    return "sk-fast-key".toCharArray();
                }, streams, fallback);
        DeepSeekRequest request = aDerivedRequest();
        GenerationSink sink = silentSink();

        gateway.open(GenerationModel.FLASH, request, sink);

        assertEquals("the key is read for the fast provider alone",
                Arrays.asList(PROVIDER_ID), keyReads);
        assertEquals(1, streams.apiKeys.size());
        assertEquals("sk-fast-key", streams.apiKeys.get(0));
        assertEquals(BASE_URL, streams.endpoints.get(0).baseUrl());
        assertEquals(FAST_WIRE_ID, streams.endpoints.get(0).modelWireId());
        assertSame("the request travels unchanged (thinking switch included)",
                request, streams.requests.get(0));
        assertSame(sink, streams.sinks.get(0));
        assertEquals("the legacy path stays untouched on a routed call", 0, fallback.models.size());
    }

    @Test
    public void anUnconfiguredFastAssignmentKeepsTheLegacyChatModelPath() {
        FakeStreams streams = new FakeStreams();
        FakeFallback fallback = new FakeFallback();
        QuickModelGateway gateway = new QuickModelGateway(() -> null,
                providerId -> "sk-unused".toCharArray(), streams, fallback);
        DeepSeekRequest request = aDerivedRequest();
        GenerationSink sink = silentSink();

        gateway.open(GenerationModel.FLASH, request, sink);

        assertEquals(1, fallback.models.size());
        assertSame(GenerationModel.FLASH, fallback.models.get(0));
        assertSame(request, fallback.requests.get(0));
        assertSame(sink, fallback.sinks.get(0));
        assertEquals("no endpoint stream may open on the legacy path", 0, streams.apiKeys.size());
    }

    @Test
    public void anUnreadableProviderKeyFallsBackToTheChatModelPath() {
        FakeStreams streams = new FakeStreams();
        FakeFallback fallback = new FakeFallback();
        QuickModelGateway gateway = new QuickModelGateway(() -> route(), providerId -> {
            throw new SecretStoreException(SecretStoreError.NO_STORED_SECRET, "no envelope");
        }, streams, fallback);
        DeepSeekRequest request = aDerivedRequest();
        GenerationSink sink = silentSink();

        gateway.open(GenerationModel.FLASH, request, sink);

        assertEquals("the fast provider without a key falls back, never fails the chain",
                1, fallback.models.size());
        assertSame(GenerationModel.FLASH, fallback.models.get(0));
        assertEquals(0, streams.apiKeys.size());
    }

    @Test
    public void aFailedFastStreamDoesNotRetryThroughTheChatModel() {
        FakeStreams streams = new FakeStreams();
        streams.terminalToReport = new StreamOutcome(StreamOutcome.Kind.FAILED,
                AiGatewayError.forHttpStatus(500), 500, 0, 0);
        FakeFallback fallback = new FakeFallback();
        QuickModelGateway gateway = new QuickModelGateway(() -> route(),
                providerId -> "sk-fast-key".toCharArray(), streams, fallback);
        RecordingSink sink = new RecordingSink();

        gateway.open(GenerationModel.FLASH, aDerivedRequest(), sink);

        assertEquals(1, streams.apiKeys.size());
        assertEquals("a fast-model failure stays a derived failure: no second call", 0,
                fallback.models.size());
        assertNotNull(sink.terminal);
        assertEquals(StreamOutcome.Kind.FAILED, sink.terminal.kind);
    }

    @Test
    public void theProviderKeyBufferIsWipedAfterTheCallIsOpened() {
        final char[] secret = "sk-fast-key".toCharArray();
        QuickModelGateway gateway = new QuickModelGateway(() -> route(), providerId -> secret,
                new FakeStreams(), new FakeFallback());

        gateway.open(GenerationModel.FLASH, aDerivedRequest(), silentSink());

        boolean wiped = true;
        for (char c : secret) {
            wiped = wiped && c == '\0';
        }
        assertTrue("the key buffer is wiped as soon as the call is opened", wiped);
    }

    @Test
    public void cancelDelegatesToTheEndpointCall() {
        QuickModelGateway gateway = new QuickModelGateway(() -> route(),
                providerId -> "sk-fast-key".toCharArray(), new FakeStreams(), new FakeFallback());

        GenerationGateway.Stream stream = gateway.open(GenerationModel.FLASH, aDerivedRequest(),
                silentSink());
        assertFalse(stream.isCancelled());
        stream.cancel();
        assertTrue(stream.isCancelled());
    }

    private static GenerationSink silentSink() {
        return new GenerationSink() {
            @Override
            public boolean onAccepted(int httpStatus) {
                return true;
            }

            @Override
            public void onDelta(String reasoningChunk, String contentChunk) {
            }

            @Override
            public void onTerminal(StreamOutcome outcome) {
            }
        };
    }

    private static final class RecordingSink implements GenerationSink {

        StreamOutcome terminal;

        @Override
        public boolean onAccepted(int httpStatus) {
            return true;
        }

        @Override
        public void onDelta(String reasoningChunk, String contentChunk) {
        }

        @Override
        public void onTerminal(StreamOutcome outcome) {
            terminal = outcome;
        }
    }
}
