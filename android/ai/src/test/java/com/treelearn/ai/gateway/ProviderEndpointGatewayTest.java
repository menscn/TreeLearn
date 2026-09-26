package com.treelearn.ai.gateway;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;

/**
 * The provider-parameter injection points (SPEC-2026-09-23-providers-and-models, protocol
 * abstraction): base URL, model wire id and the credential travel with the selected provider, and
 * the DeepSeek default instance keeps the documented endpoint and wire id byte for byte.
 *
 * <p>The SSE semantics are not touched here — they are covered by {@code AiGatewayStreamTest} and
 * the T-069 clean-room spec; this suite proves only that a different provider changes what is
 * <em>sent</em>, never how the stream is consumed.
 */
public class ProviderEndpointGatewayTest {

    private static final Executor INLINE = Runnable::run;

    private final List<String> encodedWireIds = new ArrayList<>();
    private final FakeTransport transport = new FakeTransport();
    private final GatewayPorts.BodyEncoder encoder = (wireId, request) -> {
        encodedWireIds.add(wireId);
        return ("{\"model\":\"" + wireId + "\"}").getBytes(UTF_8);
    };

    private AiGateway gateway() {
        return new AiGateway(transport, encoder, payload -> GatewayPorts.Delta.EMPTY,
                payload -> true, new ProviderJson.ModelList(), (name, details) -> { }, INLINE);
    }

    private static DeepSeekRequest request() {
        return DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, true,
                List.of(new DeepSeekMessage(DeepSeekMessage.Role.USER, "之前的问题")),
                "现在的问题", new PromptBudget(1_000_000, 1000));
    }

    @Test
    public void aCustomBaseUrlAndWireIdAreInjectedIntoTheExchange() {
        ProviderEndpoint endpoint = ProviderEndpoint.of("https://api.other.example/v1",
                "custom-model-9");

        gateway().streamChat("key", endpoint, request(), new RecordingSink());

        assertEquals("https://api.other.example/v1" + GatewayEndpoints.CHAT_PATH,
                transport.requestedUrl);
        assertEquals(List.of("custom-model-9"), encodedWireIds);
        assertArrayEquals("{\"model\":\"custom-model-9\"}".getBytes(UTF_8), transport.requestedBody);
        assertEquals("key", transport.requestedKey);
    }

    @Test
    public void theDeepSeekDefaultInstanceKeepsTheDocumentedEndpointAndWireId() {
        ProviderEndpoint endpoint = ProviderEndpoint.deepSeek(GenerationModel.PRO);

        assertEquals(GatewayEndpoints.BASE_URL, endpoint.baseUrl());
        assertEquals(GatewayEndpoints.chatUrl(), endpoint.chatUrl());

        gateway().streamChat("key", endpoint, request(), new RecordingSink());

        assertEquals(GatewayEndpoints.chatUrl(), transport.requestedUrl);
        assertEquals(List.of(GenerationModel.PRO.wireId), encodedWireIds);
        assertArrayEquals("{\"model\":\"deepseek-v4-pro\"}".getBytes(UTF_8), transport.requestedBody);
    }

    @Test
    public void theLegacyModelOverloadStillTargetsTheDeepSeekEndpointWithTheModelWireId() {
        gateway().streamChat("key", GenerationModel.FLASH, request(), new RecordingSink());

        assertEquals(GatewayEndpoints.chatUrl(), transport.requestedUrl);
        assertEquals(List.of(GenerationModel.FLASH.wireId), encodedWireIds);
        assertArrayEquals("{\"model\":\"deepseek-flash\"}".getBytes(UTF_8), transport.requestedBody);
    }

    @Test
    public void cleartextBlankAndNullBaseUrlsAreRefusedBeforeAnyConnection() {
        for (String refused : new String[] {"http://api.other.example/v1", "api.other.example",
                "", "  ", null}) {
            try {
                ProviderEndpoint.of(refused, "m");
                fail("a cleartext or missing base URL must be refused, but was: <" + refused + ">");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("https"));
            }
        }
        assertNull(transport.requestedUrl);
    }

    @Test
    public void aBlankWireIdIsRefused() {
        for (String refused : new String[] {"", "  ", null}) {
            try {
                ProviderEndpoint.of("https://api.other.example/v1", refused);
                fail("a blank wire id must be refused, but was: <" + refused + ">");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("wire id"));
            }
        }
    }

    @Test
    public void aMissingEndpointFailsFastBeforeAnyConnectionIsOpened() {
        try {
            gateway().streamChat("key", (ProviderEndpoint) null, request(), new RecordingSink());
            fail("a missing endpoint must be refused before the exchange is opened");
        } catch (IllegalArgumentException expected) {
            assertNull(transport.requestedUrl);
        }
    }

    /** One scripted HTTP response, recording what was sent. */
    private static final class FakeTransport implements GatewayPorts.Transport {

        String requestedUrl;
        String requestedKey;
        byte[] requestedBody;

        @Override
        public GatewayPorts.Response post(String url, String apiKey, byte[] body) {
            requestedUrl = url;
            requestedKey = apiKey;
            requestedBody = body;
            return new GatewayPorts.Response() {
                @Override
                public int status() {
                    return 200;
                }

                @Override
                public InputStream body() {
                    return new ByteArrayInputStream(new byte[0]);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public GatewayPorts.Response get(String url, String apiKey) throws IOException {
            return post(url, apiKey, null);
        }
    }

    /** Records callbacks in arrival order; the SSE details are covered by the stream suite. */
    private static final class RecordingSink implements GenerationSink {

        @Override
        public boolean onAccepted(int httpStatus) {
            return true;
        }

        @Override
        public void onDelta(String reasoningChunk, String contentChunk) {
            // The SSE delivery contract is proven by AiGatewayStreamTest; nothing to record here.
        }

        @Override
        public void onTerminal(StreamOutcome result) {
            // Same: only what was sent matters in this suite.
        }
    }
}
