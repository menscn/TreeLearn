package com.treelearn.ai.gateway;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;

/**
 * The model list fetch (SPEC-2026-09-23 技术规格, T-2026-09-23-086): one GET to {@code {base}/models}
 * with the Bearer credential, the decoded {@code data[].id} list on success, and a classified
 * failure that carries no ids otherwise — so a caller that merges on success only can never
 * half-write the local model table.
 *
 * <p>The decoder is scripted here, per the repo convention that the platform {@code org.json} layer
 * ({@code ProviderJson.ModelList}) is exercised on a device (ModelListMergeInstrumentedTest); this
 * suite proves the fetch semantics around it. The merge decision is proven in {@code :data}'s
 * ModelDaoMergeTest.
 */
public class AiGatewayModelListTest {

    private static final Executor INLINE = Runnable::run;

    private final List<String> logLines = new ArrayList<>();
    private final FakeTransport transport = new FakeTransport();
    private final ScriptedModelListDecoder decoder = new ScriptedModelListDecoder();

    private AiGateway gateway() {
        return new AiGateway(transport, (model, request) -> new byte[0],
                payload -> GatewayPorts.Delta.EMPTY, payload -> true, decoder,
                (name, details) -> logLines.add(name + " " + details), INLINE);
    }

    private static ProviderEndpoint endpoint() {
        return ProviderEndpoint.of("https://api.other.example/v1", "wire-model");
    }

    @Test
    public void theDecoderIsFedTheExactBodyAndGetAndBearerReachTheModelsEndpoint() {
        transport.body = "{\"object\":\"list\",\"data\":[{\"id\":\"a\"},{\"id\":\"b\"}]}";
        decoder.ids = List.of("a", "b");

        AiGateway.Models models = gateway().listModels("key", endpoint());

        assertTrue(models.ok());
        assertNull(models.error);
        assertEquals(List.of("a", "b"), models.ids);
        assertEquals("https://api.other.example/v1" + GatewayEndpoints.MODELS_PATH,
                transport.requestedUrl);
        assertEquals("key", transport.requestedKey);
        assertEquals("the decoder must see the response body byte for byte", transport.body,
                decoder.receivedPayload);
    }

    @Test
    public void anEmptyDecodeResultIsAValidEmptyList() {
        decoder.ids = List.of();

        AiGateway.Models models = gateway().listModels("key", endpoint());

        assertTrue(models.ok());
        assertTrue(models.ids.isEmpty());
    }

    @Test
    public void aMissingKeyIsRefusedBeforeAnyRequest() {
        AiGateway.Models models = gateway().listModels("  ", endpoint());

        assertFalse(models.ok());
        assertEquals(AiGatewayError.Kind.KEY_MISSING, models.error.kind);
        assertTrue(models.ids.isEmpty());
        assertNull(transport.requestedUrl);
    }

    @Test
    public void anUnauthorizedStatusIsClassifiedAndTheBodyIsNeverDecoded() {
        transport.status = 401;
        transport.body = "{\"error\":\"secret provider text\"}";

        AiGateway.Models models = gateway().listModels("key", endpoint());

        assertFalse(models.ok());
        assertEquals(AiGatewayError.Kind.AUTH_INVALID, models.error.kind);
        assertEquals(401, models.error.httpStatus);
        assertTrue(models.ids.isEmpty());
        assertNull("a failed response body is never read, so never decoded", decoder.receivedPayload);
    }

    @Test
    public void transportFailureIsClassifiedAsNetworkInterrupted() {
        transport.failOnOpen = true;

        AiGateway.Models models = gateway().listModels("key", endpoint());

        assertFalse(models.ok());
        assertEquals(AiGatewayError.Kind.NETWORK_INTERRUPTED, models.error.kind);
        assertTrue(models.ids.isEmpty());
    }

    @Test
    public void aRefusedPayloadIsReportedAsAParseFailureWithNoIds() {
        decoder.refuse = true;
        transport.body = "{\"data\":[]}";

        AiGateway.Models models = gateway().listModels("key", endpoint());

        assertFalse(models.ok());
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, models.error.kind);
        assertTrue(models.ids.isEmpty());
    }

    @Test
    public void anOversizedPayloadIsRefusedInsteadOfTruncated() {
        transport.body = "x".repeat(AiGateway.MAX_MODELS_BYTES + 1);

        AiGateway.Models models = gateway().listModels("key", endpoint());

        assertFalse(models.ok());
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, models.error.kind);
        assertTrue(models.ids.isEmpty());
        assertNull("an oversized body is refused before the decoder sees it",
                decoder.receivedPayload);
    }

    @Test
    public void theCredentialAndPayloadNeverReachTheLog() {
        decoder.ids = List.of("secret-model");
        transport.body = "{\"data\":[{\"id\":\"secret-model\"}]}";

        gateway().listModels("SECRET-KEY-0003", endpoint());

        String joined = String.join("\n", logLines);
        assertFalse(joined, joined.contains("SECRET-KEY-0003"));
        assertFalse(joined, joined.contains("secret-model"));
        assertTrue(joined, joined.contains("models_fetched"));
    }

    /** One scripted HTTP response, recording what was sent; mirrors AiGatewayBalanceTest. */
    private static final class FakeTransport implements GatewayPorts.Transport {

        String body = "";
        int status = 200;
        boolean failOnOpen;
        String requestedUrl;
        String requestedKey;

        @Override
        public GatewayPorts.Response post(String url, String apiKey, byte[] body) {
            throw new UnsupportedOperationException("the model list fetch must use GET");
        }

        @Override
        public GatewayPorts.Response get(String url, String apiKey) throws IOException {
            requestedUrl = url;
            requestedKey = apiKey;
            if (failOnOpen) {
                throw new IOException("fixture: the connection could not be established");
            }
            return new GatewayPorts.Response() {
                @Override
                public int status() {
                    return status;
                }

                @Override
                public InputStream body() {
                    return new ByteArrayInputStream(body.getBytes(UTF_8));
                }

                @Override
                public void close() {
                    // The fixture holds no connection.
                }
            };
        }
    }

    /** A scripted decoder: hands back fixture ids and records the payload it was fed. */
    private static final class ScriptedModelListDecoder implements GatewayPorts.ModelListDecoder {

        List<String> ids = List.of();
        boolean refuse;
        String receivedPayload;

        @Override
        public List<String> decode(String payload) {
            receivedPayload = payload;
            if (refuse) {
                throw new GatewayPorts.PayloadException(AiGatewayError.parseFailed("fixture refusal"),
                        "fixture refusal");
            }
            return ids;
        }
    }
}
