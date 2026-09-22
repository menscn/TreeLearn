package com.treelearn.app;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.ProviderJson;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The gateway end to end on the device: real UTF-8 framing plus real provider JSON, fed one slice at
 * a time because the network chunking is not part of the protocol (technical design 9.3).
 *
 * <p>The stream below is a realistic DeepSeek shape: a comment/heartbeat, an unknown {@code event:}
 * field, a role-only first chunk, an interleaved {@code reasoning_content} / {@code content} event,
 * a {@code finish_reason}-only chunk, a {@code usage}-only chunk and the {@code [DONE]} sentinel.
 */
public class DeepSeekStreamInstrumentedTest {

    private static final String REASONING = "\u601d\u8003\u4e00\u601d\u8003\u4e8c\u601d\u8003\u4e09";
    private static final String ANSWER = "\u7b54\u6848\u4e00";

    private static final List<String> EVENTS = List.of(
            ": keep-alive",
            "",
            "event: chunk",
            "data: " + chunk("{\"role\":\"assistant\"}"),
            "",
            "data: " + chunk("{\"reasoning_content\":\"\u601d\u8003\u4e00\"}"),
            "",
            "id: 7",
            "data: " + chunk("{\"reasoning_content\":\"\u601d\u8003\u4e8c\",\"content\":\""
                    + ANSWER + "\"}"),
            "",
            ": ping",
            "",
            "data: " + chunk("{\"reasoning_content\":\"\u601d\u8003\u4e09\"}"),
            "",
            "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"completion_tokens\":3}}",
            "",
            "data: {\"choices\":[],\"usage\":{\"total_tokens\":9}}");

    private static final String TERMINATED = String.join("\n", EVENTS) + "\n\ndata: [DONE]\n\n";
    private static final String TERMINATED_AT_EOF =
            String.join("\n", EVENTS) + "\n\ndata: [DONE]";
    private static final String UNTERMINATED = String.join("\n", EVENTS) + "\n\n";

    private final GatewayPorts.Log quiet = (name, details) -> {
        // No logging under test: the log contract is asserted in the JVM suite.
    };

    @Test
    public void reassemblesBothChannelsWhateverTheChunkBoundariesAre() {
        for (int slice : new int[] {1, 2, 3, 5, 7, 64, 4096}) {
            RecordingSink sink = new RecordingSink();
            gateway(TERMINATED, slice).streamChat("key", GenerationModel.FLASH, request(), sink);

            assertEquals("slice " + slice, REASONING, String.join("", sink.reasoning));
            assertEquals("slice " + slice, ANSWER, String.join("", sink.answers));
            assertEquals("slice " + slice, StreamOutcome.Kind.COMPLETED, sink.outcome.kind);
            assertNull("slice " + slice, sink.outcome.error);
            assertEquals("slice " + slice, 1, sink.terminalCount);
            assertEquals("slice " + slice, 9, sink.outcome.reasoningChars);
            assertEquals("slice " + slice, 3, sink.outcome.answerChars);
        }
    }

    @Test
    public void deliversTheDoneSentinelThatArrivesWithoutATrailingBlankLine() {
        RecordingSink sink = new RecordingSink();

        gateway(TERMINATED_AT_EOF, 6).streamChat("key", GenerationModel.FLASH, request(), sink);

        assertEquals(StreamOutcome.Kind.COMPLETED, sink.outcome.kind);
        assertNull(sink.outcome.error);
        assertEquals(REASONING, String.join("", sink.reasoning));
        assertEquals(1, sink.terminalCount);
    }

    @Test
    public void reportsAnInterruptedStreamWhenTheConnectionJustEnds() {
        RecordingSink sink = new RecordingSink();

        gateway(UNTERMINATED, 9).streamChat("key", GenerationModel.FLASH, request(), sink);

        assertEquals(StreamOutcome.Kind.INTERRUPTED, sink.outcome.kind);
        assertNull(sink.outcome.error);
        assertEquals("the partial channels must survive an interrupt", REASONING,
                String.join("", sink.reasoning));
        assertEquals(ANSWER, String.join("", sink.answers));
        assertEquals(1, sink.terminalCount);
    }

    private AiGateway gateway(String body, int slice) {
        return new AiGateway(new SlicedTransport(body, slice), (model, request) -> new byte[0],
                new ProviderJson.Deltas(), new ProviderJson.Balance(), quiet, Runnable::run);
    }

    private static DeepSeekRequest request() {
        return DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, true,
                List.of(new DeepSeekMessage(DeepSeekMessage.Role.USER, "\u4e4b\u524d\u7684\u63d0\u95ee")),
                "\u73b0\u5728\u7684\u95ee\u9898", new PromptBudget(1_000_000, 1000));
    }

    private static String chunk(String deltaBody) {
        return "{\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"model\":\"deepseek-flash\","
                + "\"choices\":[{\"index\":0,\"delta\":" + deltaBody + ",\"finish_reason\":null}],"
                + "\"system_fingerprint\":\"fp\"}";
    }

    /** Hands the fixture out in fixed slices, so no test depends on the platform's read sizes. */
    private static final class SlicedTransport implements GatewayPorts.Transport {

        private final byte[] payload;
        private final int slice;

        SlicedTransport(String payload, int slice) {
            this.payload = payload.getBytes(UTF_8);
            this.slice = slice;
        }

        @Override
        public GatewayPorts.Response post(String url, String apiKey, byte[] body) {
            return response();
        }

        @Override
        public GatewayPorts.Response get(String url, String apiKey) {
            return response();
        }

        private GatewayPorts.Response response() {
            return new GatewayPorts.Response() {
                @Override
                public int status() {
                    return 200;
                }

                @Override
                public InputStream body() {
                    return new InputStream() {
                        private int position;

                        @Override
                        public int read() {
                            byte[] one = new byte[1];
                            int read = read(one, 0, 1);
                            return read < 0 ? -1 : one[0];
                        }

                        @Override
                        public int read(byte[] buffer, int offset, int length) {
                            if (position >= payload.length) {
                                return -1;
                            }
                            int count = Math.min(slice, Math.min(length, payload.length - position));
                            System.arraycopy(payload, position, buffer, offset, count);
                            position += count;
                            return count;
                        }
                    };
                }

                @Override
                public void close() {
                    // The fixture holds no connection.
                }
            };
        }
    }

    private static final class RecordingSink implements GenerationSink {

        final List<String> reasoning = new ArrayList<>();
        final List<String> answers = new ArrayList<>();
        StreamOutcome outcome;
        int terminalCount;

        @Override
        public boolean onAccepted(int httpStatus) {
            return true;
        }

        @Override
        public void onDelta(String reasoningChunk, String contentChunk) {
            reasoning.add(reasoningChunk);
            answers.add(contentChunk);
        }

        @Override
        public void onTerminal(StreamOutcome result) {
            outcome = result;
            terminalCount++;
        }
    }
}