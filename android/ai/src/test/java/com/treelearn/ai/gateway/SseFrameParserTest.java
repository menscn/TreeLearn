package com.treelearn.ai.gateway;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The framing half of the SSE contract (technical design 9.3): event boundaries, multi-line
 * {@code data:} accumulation, comments/heartbeats, unknown compatibility fields and the trailing
 * event at EOF.
 *
 * <p>Every case is fed through {@link SseFrameParser} with a configurable slice size, because the
 * provider's network chunking is not part of the protocol: one chunk is never assumed to be one
 * event, and the UTF-8 cases split multi-byte characters across chunk boundaries on purpose.
 *
 * <p>This class is JVM-only on purpose. The class under test imports nothing from {@code android.*}
 * so the framing rules can be proven without a device; provider JSON decoding is covered by the
 * instrumentation suite in {@code :app}.
 */
public class SseFrameParserTest {

    private final List<String> events = new ArrayList<>();

    private final SseFrameParser.EventSink sink = payload -> events.add(payload);

    private void feed(String stream, int slice, boolean endOfStream) {
        feed(stream.getBytes(UTF_8), slice, endOfStream);
    }

    private void feed(byte[] stream, int slice, boolean endOfStream) {
        SseFrameParser parser = new SseFrameParser();
        for (int offset = 0; offset < stream.length; offset += slice) {
            byte[] chunk = Arrays.copyOfRange(stream, offset, Math.min(stream.length, offset + slice));
            parser.accept(chunk, chunk.length, sink);
        }
        if (endOfStream) {
            parser.endOfStream(sink);
        }
    }

    @Test
    public void dispatchesOneEventOnTheBlankLine() {
        feed("data: {\"a\":1}\n\n", 4096, true);
        assertEquals(List.of("{\"a\":1}"), events);
    }

    @Test
    public void neverAssumesOneChunkIsOneEvent() {
        String stream = "data: {\"a\":1}\n\ndata: {\"b\":2}\n\n";
        feed(stream, 4096, true);
        List<String> whole = List.copyOf(events);
        events.clear();
        feed(stream, 1, true);
        assertEquals(whole, events);
        assertEquals(List.of("{\"a\":1}", "{\"b\":2}"), events);
    }

    @Test
    public void joinsMultiLineDataWithNewline() {
        feed("data: {\"a\":\ndata: 1}\n\n", 4096, true);
        assertEquals(List.of("{\"a\":\n1}"), events);
    }

    @Test
    public void keepsAnEmptyFirstDataLineInsideTheJoin() {
        feed("data:\ndata: b\n\n", 4096, true);
        assertEquals(List.of("\nb"), events);
    }

    @Test
    public void ignoresCommentAndHeartbeatLines() {
        feed(": keep-alive\n\n:ping\ndata: x\n\n: another\n\ndata: y\n\n", 4096, true);
        assertEquals(List.of("x", "y"), events);
    }

    @Test
    public void ignoresUnknownCompatibilityFields() {
        feed("event: ping\nid: 42\nretry: 1000\ndata: x\n\n", 4096, true);
        assertEquals(List.of("x"), events);
    }

    @Test
    public void handlesLfCrLfAndCrLineEndings() {
        for (String separator : List.of("\n", "\r\n", "\r")) {
            events.clear();
            feed("data: x" + separator + separator + "data: y" + separator, 4096, true);
            assertEquals("separator <" + separator + ">", List.of("x", "y"), events);
        }
    }

    @Test
    public void decodesUtf8ThatIsSplitAcrossChunkBoundaries() {
        String text = "\u601d\u8003\u4e0e\u56de\u7b54\ud83c\udf31";
        feed("data: " + text + "\n\n", 1, true);
        assertEquals(List.of(text), events);
        assertEquals(7, events.get(0).length());
    }

    @Test
    public void stripsExactlyOneLeadingSpaceAfterTheColon() {
        feed("data:  x\n\ndata:y\n\ndata: z\n\n", 4096, true);
        assertEquals(List.of(" x", "y", "z"), events);
    }

    @Test
    public void doesNotDispatchAnEventWithoutData() {
        feed("data:\n\n: only a comment\n\nevent: ping\n\n", 4096, true);
        assertTrue("no data line carried content: " + events, events.isEmpty());
    }

    @Test
    public void dispatchesTheTrailingEventWhenEofArrivesWithoutABlankLine() {
        feed("data: x", 4096, true);
        assertEquals(List.of("x"), events);
        events.clear();
        feed("data: [DONE]", 4096, true);
        assertEquals(List.of("[DONE]"), events);
    }

    @Test
    public void deliversTheDoneSentinelVerbatim() {
        feed("data: [DONE]\n\n", 4096, true);
        assertEquals(List.of("[DONE]"), events);
    }
}