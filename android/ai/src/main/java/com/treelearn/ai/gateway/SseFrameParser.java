package com.treelearn.ai.gateway;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Incremental server-sent-events framing (technical design 9.3), with no {@code android.*} import so
 * the rules can be proven in a plain JVM test.
 *
 * <p>It is fed raw bytes, because a network chunk is never an event and never even a character
 * boundary: the incremental UTF-8 decoder keeps a split multi-byte sequence for the next chunk. Per
 * the SSE rules, {@code data} lines of one event are joined with a newline, comments and unknown
 * fields ({@code event}, {@code id}, {@code retry}, anything else) are ignored, and a blank line
 * dispatches the accumulated payload. An event whose data buffer stays empty is not dispatched.
 *
 * <p><strong>Deliberate deviation at end of stream.</strong> The SSE specification discards a
 * pending event when the file ends. This parser dispatches it instead, because the provider ends the
 * stream with {@code data: [DONE]} and does not always terminate that event with a blank line;
 * discarding it would turn a normally finished stream into an interrupt. Everything else about the
 * end of stream is unchanged: an EOF without the sentinel is still an interrupt, decided by the
 * caller of this parser.
 */
public final class SseFrameParser {

    /** Receives one complete event's data payload. */
    public interface EventSink {
        void onData(String payload);
    }

    private static final String DATA_FIELD = "data";
    private static final int MAX_SPLIT_SEQUENCE_BYTES = 3;

    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    private final StringBuilder line = new StringBuilder();
    private final StringBuilder data = new StringBuilder();
    private byte[] tail = new byte[0];
    private boolean dataSeen;
    private boolean endedOnCarriageReturn;

    /** Feeds the next {@code length} bytes of the response body. */
    public void accept(byte[] chunk, int length, EventSink sink) {
        if (chunk == null || length <= 0) {
            return;
        }
        int total = tail.length + length;
        byte[] bytes = new byte[total];
        System.arraycopy(tail, 0, bytes, 0, tail.length);
        System.arraycopy(chunk, 0, bytes, tail.length, Math.min(length, chunk.length));
        ByteBuffer encoded = ByteBuffer.wrap(bytes);
        CharBuffer decoded = CharBuffer.allocate(total);
        if (decoder.decode(encoded, decoded, false).isOverflow()) {
            throw new IllegalStateException("UTF-8 decoding must never need more characters than "
                    + "bytes, but " + total + " bytes overflowed the output buffer");
        }
        decoded.flip();
        consume(decoded, sink);
        int remaining = Math.min(encoded.remaining(), MAX_SPLIT_SEQUENCE_BYTES);
        tail = new byte[remaining];
        System.arraycopy(bytes, total - remaining, tail, 0, remaining);
    }

    /** Ends the stream: the pending line, and then the pending event, are dispatched. */
    public void endOfStream(EventSink sink) {
        endLine(sink);
        dispatch(sink);
    }

    private void consume(CharBuffer decoded, EventSink sink) {
        for (int index = 0; index < decoded.length(); index++) {
            char current = decoded.get(index);
            if (endedOnCarriageReturn) {
                endedOnCarriageReturn = false;
                if (current == '\n') {
                    continue;
                }
            }
            if (current == '\r') {
                endLine(sink);
                endedOnCarriageReturn = true;
            } else if (current == '\n') {
                endLine(sink);
            } else {
                line.append(current);
            }
        }
    }

    private void endLine(EventSink sink) {
        int length = line.length();
        int colon = line.indexOf(":");
        if (length == 0) {
            dispatch(sink);
        } else if (colon > 0 && DATA_FIELD.contentEquals(line.subSequence(0, colon))) {
            int start = colon + 1;
            if (start < length && line.charAt(start) == ' ') {
                start++;
            }
            if (dataSeen) {
                data.append('\n');
            }
            data.append(line, start, length);
            dataSeen = true;
        }
        line.setLength(0);
    }

    private void dispatch(EventSink sink) {
        boolean deliver = dataSeen && data.length() > 0;
        dataSeen = false;
        if (!deliver) {
            data.setLength(0);
            return;
        }
        String payload = data.toString();
        data.setLength(0);
        sink.onData(payload);
    }
}