package com.treelearn.app.files;

import com.treelearn.core.domain.Guard;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Turns one registered attachment's bytes into the text that may enter a request context
 * (T-097; SPEC-2026-09-23 拍板记录 2026-09-23).
 *
 * <p><strong>Scope is the user's own decision.</strong> The first batch is txt/md/PDF-text-layer
 * only (拍板 1: Docx and image OCR deferred); PDF text extraction is the self-implemented
 * zero-dependency path (拍板 2: no PDFBox until a device-side failure rate justifies a separate
 * approval). A file this extractor cannot decode raises {@link Unparsable} — the caller marks the
 * record {@code parse_failed} and the user sees a [PART] notice — it never invents a best-effort
 * guess silently.
 *
 * <p><strong>Two ceilings, both refusal-not-truncation.</strong> {@link #MAX_SOURCE_BYTES} stops
 * the read before any parsing work (拍板 3: a 20&nbsp;MB-class constant). {@link
 * #INJECTION_CEILING_CHARS} caps what may enter one request: over the ceiling {@link
 * #injectable} answers null so the send is blocked with an explicit notice — a silent truncation
 * would make the model answer on a mutilated context while the user believes the attachment went
 * in whole (SPEC-082 错误语义).
 */
public final class AttachmentTextExtractor {

    /** The largest source this build will even read, in bytes (SPEC-082 拍板 3: 20 MB 级). */
    public static final int MAX_SOURCE_BYTES = 20 * 1024 * 1024;

    /**
     * The most attachment text one request may carry, in UTF-16 characters. Chosen here once,
     * against {@code PromptLimits.CAPACITY_CHARS} = 60000: the attachment is the turn's main
     * content, but the history, the search injection (4000) and the answer must still fit, so one
     * third of the capacity is the attachment's share. The exact figure is recorded in the task
     * file (SPEC-082 拍板 3 requires the implementing task to derive and record it).
     */
    public static final int INJECTION_CEILING_CHARS = 20000;

    private static final Charset PDF_LITERAL_CHARSET = StandardCharsets.US_ASCII;

    private AttachmentTextExtractor() {
    }

    /** A parse this extractor refused; the message names the reason the user is shown. */
    public static final class Unparsable extends RuntimeException {

        Unparsable(String message) {
            super(message);
        }
    }

    /**
     * Extracts the decodable text of one source file.
     *
     * @throws Unparsable when the mime is outside the first batch, the source exceeds
     *                    {@link #MAX_SOURCE_BYTES}, or the bytes do not decode
     */
    public static String extract(String displayName, String mimeType, byte[] bytes) {
        Guard.text(displayName, "displayName");
        Guard.text(mimeType, "mimeType");
        if (bytes == null || bytes.length == 0) {
            throw new Unparsable("empty source: " + displayName);
        }
        if (bytes.length > MAX_SOURCE_BYTES) {
            throw new Unparsable("source too large for this build: " + displayName);
        }
        String lower = mimeType.toLowerCase(java.util.Locale.US);
        if (lower.equals("text/plain") || lower.equals("text/markdown")) {
            return decodeText(bytes);
        }
        if (lower.equals("application/pdf")) {
            return extractPdf(displayName, bytes);
        }
        throw new Unparsable("unsupported mime in the first batch: " + mimeType);
    }

    /**
     * @return the text as-is while it fits {@link #INJECTION_CEILING_CHARS}, or null when it does
     *         not — null means the caller must block the send with an explicit notice, never
     *         truncate
     */
    public static String injectable(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= INJECTION_CEILING_CHARS ? text : null;
    }

    private static String decodeText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // PDF text layer: find the streams, inflate the compressed ones, keep the literal
    // strings the text operators write. Coverage limits are a known trade-off (拍板 2):
    // an undecodable stream raises Unparsable and the record degrades to parse_failed.
    // ------------------------------------------------------------------

    private static String extractPdf(String displayName, byte[] bytes) {
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F') {
            throw new Unparsable("not a PDF by its own header: " + displayName);
        }
        List<String> literals = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int streamStart = indexOf(bytes, "stream".getBytes(PDF_LITERAL_CHARSET), cursor);
            if (streamStart < 0) {
                break;
            }
            int bodyStart = streamStart + "stream".length();
            if (bytes[bodyStart] == '\r') {
                bodyStart++;
            }
            if (bytes[bodyStart] == '\n') {
                bodyStart++;
            }
            int bodyEnd = indexOf(bytes, "endstream".getBytes(PDF_LITERAL_CHARSET), bodyStart);
            if (bodyEnd < 0) {
                break;
            }
            boolean flate = isFlate(bytes, streamStart);
            byte[] body = new byte[bodyEnd - bodyStart];
            System.arraycopy(bytes, bodyStart, body, 0, body.length);
            String content = flate ? inflate(displayName, body) : new String(body,
                    PDF_LITERAL_CHARSET);
            collectLiterals(content, literals);
            cursor = bodyEnd + "endstream".length();
        }
        if (literals.isEmpty()) {
            throw new Unparsable("no text layer found in: " + displayName);
        }
        StringBuilder text = new StringBuilder();
        for (String literal : literals) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(literal);
        }
        return text.toString();
    }

    private static boolean isFlate(byte[] bytes, int streamStart) {
        int dictStart = Math.max(0, streamStart - 512);
        byte[] window = new byte[streamStart - dictStart];
        System.arraycopy(bytes, dictStart, window, 0, window.length);
        return new String(window, PDF_LITERAL_CHARSET).contains("FlateDecode");
    }

    private static String inflate(String displayName, byte[] body) {
        Inflater inflater = new Inflater();
        inflater.setInput(body);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(chunk);
                if (n == 0 && inflater.needsInput() && !inflater.finished()) {
                    break;
                }
                out.write(chunk, 0, n);
            }
        } catch (DataFormatException broken) {
            throw new Unparsable("unreadable PDF stream in " + displayName + ": "
                    + broken.getMessage());
        } finally {
            inflater.end();
        }
        return new String(out.toByteArray(), PDF_LITERAL_CHARSET);
    }

    /** Keeps the string literals the text-showing operators write: Tj, TJ array items, and ' . */
    private static void collectLiterals(String content, List<String> into) {
        int i = 0;
        int n = content.length();
        while (i < n) {
            char c = content.charAt(i);
            if (c == '(') {
                int[] span = readLiteral(content, i);
                if (span == null) {
                    return;
                }
                into.add(unescape(content.substring(span[0], span[1])));
                i = span[1] + 1;
                continue;
            }
            i++;
        }
    }

    /** Reads a balanced PDF literal starting at the opening paren; null when unbalanced. */
    private static int[] readLiteral(String content, int open) {
        int depth = 0;
        boolean escaped = false;
        for (int i = open; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return new int[]{open + 1, i};
                }
            }
        }
        return null;
    }

    private static String unescape(String literal) {
        StringBuilder out = new StringBuilder(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c != '\\' || i + 1 >= literal.length()) {
                out.append(c);
                continue;
            }
            char next = literal.charAt(++i);
            switch (next) {
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'b':
                case 'f':
                    break;
                default:
                    out.append(next);
            }
        }
        return out.toString();
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
