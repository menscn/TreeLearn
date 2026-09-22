package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.gateway.ProviderJson;
import org.junit.Test;

/**
 * Provider JSON decoding (technical design 9.3): {@code delta.reasoning_content} and
 * {@code delta.content} are two independent channels, unknown compatibility fields are ignored, and
 * a payload that cannot be trusted fails loudly instead of silently dropping answer text.
 *
 * <p>Device-only because {@code org.json} is part of the Android platform.
 */
public class DeepSeekPayloadDecoderInstrumentedTest {

    private static final String REASONING = "\u601d\u8003\u8fc7\u7a0b";
    private static final String ANSWER = "\u56de\u7b54";

    private final GatewayPorts.DeltaDecoder deltas = new ProviderJson.Deltas();
    private final GatewayPorts.BalanceDecoder balance = new ProviderJson.Balance();

    @Test
    public void readsBothChannelsFromOneEvent() {
        GatewayPorts.Delta delta = deltas.decode("{\"choices\":[{\"index\":0,\"delta\":{"
                + "\"role\":\"assistant\",\"reasoning_content\":\"" + REASONING + "\",\"content\":\""
                + ANSWER + "\"},\"finish_reason\":null}]}");

        assertEquals(REASONING, delta.reasoningChunk);
        assertEquals(ANSWER, delta.contentChunk);
        assertFalse(delta.isEmpty());
    }

    @Test
    public void readsEitherChannelAlone() {
        assertEquals(REASONING, deltas.decode(chunk("\"reasoning_content\":\"" + REASONING + "\""))
                .reasoningChunk);
        assertEquals("", deltas.decode(chunk("\"reasoning_content\":\"" + REASONING + "\""))
                .contentChunk);
        assertEquals(ANSWER, deltas.decode(chunk("\"content\":\"" + ANSWER + "\"")).contentChunk);
        assertEquals("", deltas.decode(chunk("\"content\":\"" + ANSWER + "\"")).reasoningChunk);
    }

    @Test
    public void ignoresUnknownCompatibilityFieldsAtEveryLevel() {
        String payload = "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
                + "\"model\":\"deepseek-flash\",\"system_fingerprint\":\"fp_x\","
                + "\"choices\":[{\"index\":0,\"logprobs\":{\"tokens\":[\"a\"]},"
                + "\"delta\":{\"role\":\"assistant\",\"content\":\"" + ANSWER
                + "\",\"reasoning_content\":\"" + REASONING + "\",\"vendor_extra\":{\"deep\":[1,2]}},"
                + "\"finish_reason\":null,\"vendor_choice_extra\":true}],"
                + "\"usage\":{\"prompt_tokens\":1},\"vendor_top_extra\":7}";

        GatewayPorts.Delta delta = deltas.decode(payload);

        assertEquals(REASONING, delta.reasoningChunk);
        assertEquals(ANSWER, delta.contentChunk);
    }

    @Test
    public void treatsMissingAndNullFieldsAsEmpty() {
        assertTrue(deltas.decode(chunk("\"role\":\"assistant\",\"content\":\"\"")).isEmpty());
        assertTrue(deltas.decode(chunk("\"content\":null,\"reasoning_content\":null")).isEmpty());
        assertTrue(deltas.decode("{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\"}]}").isEmpty());
        assertTrue(deltas.decode("{\"choices\":[]}").isEmpty());
    }

    @Test
    public void refusesAPayloadWithoutAChoicesArray() {
        GatewayPorts.PayloadException refusal =
                refusal("{\"id\":\"c1\",\"object\":\"chat.completion.chunk\"}");

        assertEquals(AiGatewayError.Kind.PARSE_FAILED, refusal.error.kind);
        assertEquals(0, refusal.error.httpStatus);
        assertTrue(refusal.getMessage(), refusal.getMessage().contains("choices"));
    }

    @Test
    public void refusesMalformedJsonAndNonObjectPayloads() {
        for (String payload : new String[] {"{\"choices\":[", "not json", "[1,2]",
                "{\"choices\":{\"index\":0}}"}) {
            assertEquals("payload <" + payload + ">", AiGatewayError.Kind.PARSE_FAILED,
                    refusal(payload).error.kind);
        }
    }

    @Test
    public void refusesAnInBandProviderErrorWithoutEchoingItsText() {
        GatewayPorts.PayloadException refusal = refusal(
                "{\"error\":{\"message\":\"Insufficient Balance\",\"type\":\"insufficient_quota\"}}");

        assertEquals(AiGatewayError.Kind.PROVIDER_UNAVAILABLE, refusal.error.kind);
        assertFalse(refusal.error.detail.contains("Insufficient Balance"));
        assertFalse(refusal.getMessage(), refusal.getMessage().contains("Insufficient Balance"));
    }

    @Test
    public void readsTheBalanceAvailabilityFlag() {
        assertTrue(balance.isAvailable(
                "{\"is_available\":true,\"balance_infos\":[{\"currency\":\"CNY\"}]}"));
        assertFalse(balance.isAvailable("{\"is_available\":false}"));
    }

    @Test
    public void refusesABalancePayloadWithoutABooleanFlag() {
        for (String payload : new String[] {"{}", "{\"is_available\":\"yes\"}"}) {
            GatewayPorts.PayloadException refused = balanceRefusal(payload);

            assertEquals("payload <" + payload + ">", AiGatewayError.Kind.PARSE_FAILED,
                    refused.error.kind);
            assertTrue("payload <" + payload + "> " + refused.getMessage(),
                    refused.getMessage().contains("is_available"));
        }
    }

    @Test
    public void refusesABalancePayloadThatIsNotJson() {
        GatewayPorts.PayloadException refused = balanceRefusal("nope");

        assertEquals(AiGatewayError.Kind.PARSE_FAILED, refused.error.kind);
        assertTrue(refused.getMessage(), refused.getMessage().contains("balance response"));
    }

    private GatewayPorts.PayloadException balanceRefusal(String payload) {
        try {
            balance.isAvailable(payload);
        } catch (GatewayPorts.PayloadException expected) {
            return expected;
        }
        throw new AssertionError("expected a refusal for " + payload);
    }

    private GatewayPorts.PayloadException refusal(String payload) {
        try {
            deltas.decode(payload);
        } catch (GatewayPorts.PayloadException expected) {
            return expected;
        }
        throw new AssertionError("expected a refusal for " + payload);
    }

    private static String chunk(String deltaBody) {
        return "{\"choices\":[{\"index\":0,\"delta\":{" + deltaBody + "},\"finish_reason\":null}]}";
    }
}