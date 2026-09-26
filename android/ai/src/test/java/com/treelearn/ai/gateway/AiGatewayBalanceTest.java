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
 * The balance boundary (technical design 8, 9.5): the key is checked before a generation slot is
 * spent, and a refused balance is classified rather than reported as a success.
 */
public class AiGatewayBalanceTest {

    private static final Executor INLINE = Runnable::run;

    private final List<String> logLines = new ArrayList<>();
    private final FakeTransport transport = new FakeTransport();
    private final GatewayPorts.Log log = (name, details) -> logLines.add(name + " " + details);

    private AiGateway gateway(GatewayPorts.BalanceDecoder decoder) {
        return new AiGateway(transport, (model, request) -> new byte[0],
                payload -> GatewayPorts.Delta.EMPTY, decoder, new ProviderJson.ModelList(),
                log, INLINE);
    }

    @Test
    public void availableBalanceIsReportedFromTheProviderPayload() {
        transport.body = "{\"is_available\":true}";

        AiGateway.Balance balance = gateway(payload -> payload.contains("true")).checkBalance("key");

        assertTrue(balance.available);
        assertNull(balance.error);
        assertEquals(GatewayEndpoints.balanceUrl(), transport.requestedUrl);
        assertEquals("key", transport.requestedKey);
    }

    @Test
    public void anUnavailableAccountIsReportedWithoutABalanceError() {
        transport.body = "{\"is_available\":false}";

        AiGateway.Balance balance = gateway(payload -> payload.contains("true")).checkBalance("key");

        assertFalse(balance.available);
        assertNull(balance.error);
    }

    @Test
    public void aPaymentRequiredStatusIsClassified() {
        transport.status = 402;
        transport.body = "{\"detail\":\"secret provider text\"}";

        AiGateway.Balance balance = gateway(payload -> true).checkBalance("key");

        assertFalse(balance.available);
        assertEquals(AiGatewayError.Kind.BALANCE_UNAVAILABLE, balance.error.kind);
        assertEquals(402, balance.error.httpStatus);
        assertFalse(balance.error.detail.contains("secret provider text"));
    }

    @Test
    public void anUnauthorizedStatusIsClassifiedAsAnInvalidCredential() {
        transport.status = 401;

        AiGateway.Balance balance = gateway(payload -> true).checkBalance("key");

        assertEquals(AiGatewayError.Kind.AUTH_INVALID, balance.error.kind);
    }

    @Test
    public void aMissingKeyIsReportedWithoutAnyRequest() {
        AiGateway.Balance balance = gateway(payload -> true).checkBalance("  ");

        assertFalse(balance.available);
        assertEquals(AiGatewayError.Kind.KEY_MISSING, balance.error.kind);
        assertNull(transport.requestedUrl);
    }

    @Test
    public void transportFailureIsClassifiedAsNetworkInterrupted() {
        transport.failOnOpen = true;

        AiGateway.Balance balance = gateway(payload -> true).checkBalance("key");

        assertFalse(balance.available);
        assertEquals(AiGatewayError.Kind.NETWORK_INTERRUPTED, balance.error.kind);
    }

    @Test
    public void aRefusedBalancePayloadIsReportedAsAParseFailure() {
        transport.body = "{";

        AiGateway.Balance balance = gateway(payload -> {
            throw new GatewayPorts.PayloadException(AiGatewayError.parseFailed("fixture refusal"),
                    "fixture refusal");
        }).checkBalance("key");

        assertFalse(balance.available);
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, balance.error.kind);
    }

    @Test
    public void anOversizedBalancePayloadIsRefusedInsteadOfTruncated() {
        transport.body = "x".repeat(AiGateway.MAX_BALANCE_BYTES + 1);

        AiGateway.Balance balance = gateway(payload -> true).checkBalance("key");

        assertFalse(balance.available);
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, balance.error.kind);
    }

    @Test
    public void theBalancePayloadAndKeyNeverReachTheLog() {
        transport.body = "{\"secret_payload\":\"do-not-log-me\"}";

        gateway(payload -> true).checkBalance("SECRET-KEY-0002");

        String joined = String.join("\n", logLines);
        assertFalse(joined, joined.contains("SECRET-KEY-0002"));
        assertFalse(joined, joined.contains("do-not-log-me"));
        assertTrue(joined, joined.contains("balance_checked"));
    }

    private static final class FakeTransport implements GatewayPorts.Transport {

        String body = "";
        int status = 200;
        boolean failOnOpen;
        String requestedUrl;
        String requestedKey;

        @Override
        public GatewayPorts.Response post(String url, String apiKey, byte[] body) throws IOException {
            throw new UnsupportedOperationException("the balance check must use GET");
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
}