package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.MarkdownRenderer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Verifies Markdown rendering against the real Android text/span implementation. */
@RunWith(AndroidJUnit4.class)
public final class MarkdownRendererInstrumentedTest {

    @Test
    public void commonMarkdownSyntaxBecomesReadableTextAndSpans() {
        CharSequence rendered = MarkdownRenderer.render(
                "# Heading\n\n**bold** and *italic* with `code`\n\n- one\n- two");

        assertFalse(rendered.toString().contains("**"));
        assertFalse(rendered.toString().contains("`code`"));
        assertTrue(rendered.toString().contains("Heading"));
        assertTrue(rendered.toString().contains("• one"));
        assertTrue(rendered instanceof Spanned);
        Spanned spans = (Spanned) rendered;
        assertTrue(spans.getSpans(0, rendered.length(), StyleSpan.class).length > 0);
        assertTrue(spans.getSpans(0, rendered.length(), TypefaceSpan.class).length > 0);
    }

    @Test
    public void fencedCodeAndLinksStaySelectableWithoutRawFenceMarkers() {
        CharSequence rendered = MarkdownRenderer.render(
                "> note\n\n```java\nreturn 1;\n```\n\n[Treelearn](https://example.com)");

        assertFalse(rendered.toString().contains("```"));
        assertTrue(rendered.toString().contains("│ note"));
        assertTrue(rendered.toString().contains("return 1;"));
        assertTrue(((Spanned) rendered).getSpans(0, rendered.length(), URLSpan.class).length > 0);
    }

    @Test
    public void gfmTableBecomesATableSegmentNotBoxCharacters() {
        CharSequence rendered = MarkdownRenderer.render(
                "| Name | Count |\n| :--- | ---: |\n| apple | 10 |\n| pear | 2 |");

        // 2026-09-28 user decision: box characters are gone. They cannot align inside a
        // match_parent TextView, because a wrapped row moves every following glyph, and the CJK
        // wide cells were counted as one character anyway.
        assertFalse(rendered.toString().contains("┼"));
        assertFalse(rendered.toString().contains("│"));
        assertTrue(rendered.toString().contains("Name"));
        assertTrue(rendered.toString().contains("apple"));
        assertFalse(rendered.toString().contains("| :--- |"));
    }

    @Test
    public void aTableIsASeparateSegmentWithItsRowsAsData() {
        List<MarkdownRenderer.Segment> segments = MarkdownRenderer.renderSegments(
                "前一句。\n\n| 概念 | 区别 |\n| --- | --- |\n| A | B |\n\n后一句。", null);

        assertEquals("prose, table, prose", 3, segments.size());
        assertFalse(segments.get(0).isTable);
        assertTrue(segments.get(0).text.toString().contains("前一句。"));

        MarkdownRenderer.Segment table = segments.get(1);
        assertTrue(table.isTable);
        assertTrue(table.hasHeader);
        assertEquals(2, table.table.size());
        assertEquals("概念", table.table.get(0).get(0).toString());
        assertEquals("区别", table.table.get(0).get(1).toString());
        assertEquals("A", table.table.get(1).get(0).toString());
        assertEquals("B", table.table.get(1).get(1).toString());
        assertEquals("概念", table.header.get(0));

        assertFalse(segments.get(2).isTable);
        assertTrue(segments.get(2).text.toString().contains("后一句。"));
        // No marker, no box character, in either the prose or the cells.
        for (MarkdownRenderer.Segment segment : segments) {
            String flat = segment.isTable ? "" : segment.text.toString();
            assertFalse(flat.contains("|"));
            assertFalse(flat.contains("─"));
        }
    }

    @Test
    public void aTableThatDoesNotSitBetweenProseIsTheWholeBody() {
        List<MarkdownRenderer.Segment> segments = MarkdownRenderer.renderSegments(
                "| A | B |\n| --- | --- |\n| 1 | 2 |", null);

        assertEquals(1, segments.size());
        assertTrue(segments.get(0).isTable);
    }
}
