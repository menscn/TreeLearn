package com.treelearn.app.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * The extractor's JVM half: the two pure formats, the PDF text layer, and the two ceilings.
 * Whether the engine reads real PDFs of every producer is a device-side unknown (SPEC-082
 * Doubts 2); what this suite pins is the contract: decode what is decodable, fail loud, never
 * guess a size or a ceiling.
 */
public final class AttachmentTextExtractorTest {

    @Test
    public void plainTextDecodesAsItsOwnWords() {
        byte[] bytes = "SYNTHETIC-FIRST-LINE\nSYNTHETIC-SECOND-LINE".getBytes(
                StandardCharsets.UTF_8);
        assertEquals("SYNTHETIC-FIRST-LINE\nSYNTHETIC-SECOND-LINE",
                AttachmentTextExtractor.extract("SYNTHETIC.txt", "text/plain", bytes));
    }

    @Test
    public void markdownDecodesAsItsOwnWords() {
        byte[] bytes = "# SYNTHETIC-HEADING".getBytes(StandardCharsets.UTF_8);
        assertEquals("# SYNTHETIC-HEADING",
                AttachmentTextExtractor.extract("SYNTHETIC.md", "text/markdown", bytes));
    }

    @Test
    public void aPdfTextLayerYieldsTheLiteralStrings() {
        String stream = "BT /F1 12 Tf 72 720 Td (SYNTHETIC-PDF-PARAGRAPH) Tj ET";
        String pdf = "%PDF-1.4\n4 0 obj\n<< /Length " + stream.length() + " >>\nstream\n"
                + stream + "\nendstream\nendobj\n%%EOF\n";
        String text = AttachmentTextExtractor.extract("SYNTHETIC.pdf", "application/pdf",
                pdf.getBytes(StandardCharsets.US_ASCII));
        assertTrue("the operator's string survives extraction: " + text,
                text.contains("SYNTHETIC-PDF-PARAGRAPH"));
    }

    @Test
    public void aFlateCompressedPdfStreamIsInflatedBeforeExtraction() throws Exception {
        String content = "(SYNTHETIC-COMPRESSED-SENTENCE) Tj";
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setInput(content.getBytes(StandardCharsets.US_ASCII));
        deflater.finish();
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        while (!deflater.finished()) {
            buffer.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        byte[] compressed = buffer.toByteArray();
        String pdf = "%PDF-1.4\n4 0 obj\n<< /Length " + compressed.length
                + " /Filter /FlateDecode >>\nstream\n";
        byte[] head = pdf.getBytes(StandardCharsets.US_ASCII);
        byte[] tail = "\nendstream\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[head.length + compressed.length + tail.length];
        System.arraycopy(head, 0, bytes, 0, head.length);
        System.arraycopy(compressed, 0, bytes, head.length, compressed.length);
        System.arraycopy(tail, 0, bytes, head.length + compressed.length, tail.length);

        String text = AttachmentTextExtractor.extract("SYNTHETIC.pdf", "application/pdf", bytes);
        assertTrue("the inflated stream yields its string: " + text,
                text.contains("SYNTHETIC-COMPRESSED-SENTENCE"));
    }

    @Test
    public void garbageBytesFailLoudAsUnparsable() {
        try {
            AttachmentTextExtractor.extract("SYNTHETIC.pdf", "application/pdf",
                    "definitely not a pdf".getBytes(StandardCharsets.US_ASCII));
            fail("a non-PDF fed as PDF must raise the parse failure");
        } catch (AttachmentTextExtractor.Unparsable expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void anUnsupportedMimeIsRefusedNotGuessed() {
        try {
            AttachmentTextExtractor.extract("SYNTHETIC.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "PK".getBytes(StandardCharsets.US_ASCII));
            fail("the first batch is txt/md/pdf only (SPEC-082 拍板 1)");
        } catch (AttachmentTextExtractor.Unparsable expected) {
            assertTrue(expected.getMessage().contains("unsupported"));
        }
    }

    @Test
    public void anOversizedSourceIsRefusedBeforeAnyParsing() {
        byte[] bytes = new byte[AttachmentTextExtractor.MAX_SOURCE_BYTES + 1];
        try {
            AttachmentTextExtractor.extract("SYNTHETIC.txt", "text/plain", bytes);
            fail("the 20 MB ceiling must stop the read (SPEC-082 拍板 3)");
        } catch (AttachmentTextExtractor.Unparsable expected) {
            assertTrue(expected.getMessage().contains("too large"));
        }
    }

    @Test
    public void anInjectionOverTheCeilingIsRefusedNotTruncated() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i <= AttachmentTextExtractor.INJECTION_CEILING_CHARS; i++) {
            text.append('x');
        }
        assertNull("silently truncating would fake a complete context (SPEC-082 错误语义)",
                AttachmentTextExtractor.injectable(text.toString()));
        assertEquals("a text within the ceiling passes through verbatim",
                "SYNTHETIC-SHORT-TEXT", AttachmentTextExtractor.injectable("SYNTHETIC-SHORT-TEXT"));
    }
}
