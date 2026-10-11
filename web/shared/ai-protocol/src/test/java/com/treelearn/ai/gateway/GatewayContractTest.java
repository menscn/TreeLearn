package com.treelearn.ai.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * The transport-independent contract of the gateway: the endpoint is HTTPS-only (technical design
 * 9.1) and every provider status has exactly one stable category (9.5).
 *
 * <p>The mapping is asserted in one place here and consumed through the same method by the stream
 * and balance paths, so a status cannot be classified differently depending on which call observed
 * it.
 */
public class GatewayContractTest {

    @Test
    public void theDocumentedBaseUrlAndPathsAreHttps() {
        assertEquals("https://api.deepseek.com", GatewayEndpoints.BASE_URL);
        assertEquals("https://api.deepseek.com/chat/completions", GatewayEndpoints.chatUrl());
        assertEquals("https://api.deepseek.com/user/balance", GatewayEndpoints.balanceUrl());
    }

    @Test
    public void cleartextAndRelativeUrlsAreRefused() {
        for (String refused : new String[] {"http://api.deepseek.com/chat/completions",
                "ftp://api.deepseek.com/chat/completions", "//api.deepseek.com/chat/completions",
                "api.deepseek.com/chat/completions", "", "  "}) {
            try {
                String accepted = GatewayEndpoints.requireHttps(refused);
                fail("a cleartext or relative endpoint must be refused, but <" + refused
                        + "> was accepted as <" + accepted + ">");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("https"));
            }
        }
    }

    @Test
    public void theTransportRefusesCleartextBeforeOpeningAnything() throws IOException {
        HttpsTransport transport = new HttpsTransport(1_000, 1_000);

        try {
            transport.post("http://api.deepseek.com/chat/completions", "key", new byte[0]);
            fail("the HTTPS-only rule must be enforced by the transport itself");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("https"));
        }
    }

    @Test
    public void theTransportRefusesNonPositiveTimeouts() {
        try {
            new HttpsTransport(0, 1_000);
            fail("a non-positive connect timeout must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("connectTimeoutMillis"));
        }
    }

    @Test
    public void everyProviderStatusHasOneStableCategory() {
        Map<Integer, AiGatewayError.Kind> expected = new LinkedHashMap<>();
        expected.put(300, AiGatewayError.Kind.PROVIDER_UNAVAILABLE);
        expected.put(400, AiGatewayError.Kind.REQUEST_INVALID);
        expected.put(401, AiGatewayError.Kind.AUTH_INVALID);
        expected.put(402, AiGatewayError.Kind.BALANCE_UNAVAILABLE);
        expected.put(403, AiGatewayError.Kind.AUTH_INVALID);
        expected.put(404, AiGatewayError.Kind.REQUEST_INVALID);
        expected.put(418, AiGatewayError.Kind.REQUEST_INVALID);
        expected.put(422, AiGatewayError.Kind.REQUEST_INVALID);
        expected.put(429, AiGatewayError.Kind.RATE_LIMITED);
        expected.put(500, AiGatewayError.Kind.PROVIDER_UNAVAILABLE);
        expected.put(502, AiGatewayError.Kind.PROVIDER_UNAVAILABLE);
        expected.put(503, AiGatewayError.Kind.PROVIDER_UNAVAILABLE);
        expected.put(504, AiGatewayError.Kind.PROVIDER_UNAVAILABLE);

        for (Map.Entry<Integer, AiGatewayError.Kind> entry : expected.entrySet()) {
            AiGatewayError error = AiGatewayError.forHttpStatus(entry.getKey());
            assertEquals("status " + entry.getKey(), entry.getValue(), error.kind);
            assertEquals("status " + entry.getKey(), entry.getKey().intValue(), error.httpStatus);
            assertTrue("status " + entry.getKey(),
                    error.toString().contains(entry.getValue().name()));
            assertTrue("status " + entry.getKey(),
                    error.toString().contains("http=" + entry.getKey()));
        }
    }

    @Test
    public void aDiagnosticNeverEchoesTheProviderPayload() {
        String payload = "{\"error\":{\"message\":\"insufficient balance for this account\"}}";

        String rendered = AiGatewayError.parseFailed("the stream payload was not valid JSON")
                .toString();

        assertFalse(rendered.contains(payload));
        assertFalse(rendered.contains("insufficient balance"));
        assertTrue(rendered.contains("PARSE_FAILED"));
        assertTrue(rendered.contains("http=0"));
    }

    @Test
    public void malformedErrorAndDeltaValuesAreRefused() {
        for (Runnable refused : new Runnable[] {
                () -> AiGatewayError.of(null, 0, "detail"),
                () -> AiGatewayError.of(AiGatewayError.Kind.AUTH_INVALID, -1, "detail"),
                () -> AiGatewayError.of(AiGatewayError.Kind.AUTH_INVALID, 0, null),
                () -> AiGatewayError.parseFailed(null),
                () -> new GatewayPorts.Delta(null, "answer"),
                () -> new GatewayPorts.Delta("reasoning", null)}) {
            try {
                refused.run();
                fail("a malformed value must be refused");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().length() > 0);
            }
        }
    }

    @Test
    public void anEmptyDeltaCarriesNothing() {
        assertTrue(GatewayPorts.Delta.EMPTY.isEmpty());
        assertFalse(new GatewayPorts.Delta("", " ").isEmpty());
    }
}