package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.MarkdownRenderer;
import com.treelearn.app.ui.math.MathSpan;

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

    @Test
    public void renderReplacesInlineAndBlockFormulasWithMathSpans() {
        CharSequence rendered = MarkdownRenderer.render(
                "\u9762\u79ef $x^2$\u3002\n\n$$\n\\frac{1}{2}\n$$");

        assertTrue(rendered instanceof Spanned);
        assertFalse(rendered.toString().contains("$"));
        assertFalse(rendered.toString().contains("\\frac"));
        Spanned spans = (Spanned) rendered;
        assertEquals(2, spans.getSpans(0, spans.length(), MathSpan.class).length);
        assertTrue(spans.getSpans(0, spans.length(), AlignmentSpan.Standard.class).length > 0);
    }

    @Test
    public void renderSegmentsAppliesMathInsideProseAndRealTableCells() {
        List<MarkdownRenderer.Segment> segments = MarkdownRenderer.renderSegments(
                "\u6b63\u6587 $x^2$\u3002\n\n| \u516c\u5f0f |\n| --- |\n| $\\frac{1}{2}$ |", null);

        assertEquals(2, segments.size());
        Spanned prose = (Spanned) segments.get(0).text;
        assertEquals(1, prose.getSpans(0, prose.length(), MathSpan.class).length);
        assertTrue(segments.get(1).isTable);
        CharSequence cell = segments.get(1).table.get(1).get(0);
        assertTrue(cell instanceof Spanned);
        assertFalse(cell.toString().contains("\\frac"));
        assertEquals(1, ((Spanned) cell).getSpans(0, cell.length(), MathSpan.class).length);
    }

    @Test
    public void aDisplayFormulaAloneInItsParagraphBecomesItsOwnSegment() {
        List<MarkdownRenderer.Segment> segments = MarkdownRenderer.renderSegments(
                "正文。\n\n$$\n\\frac{Q K^{\\top}}{\\sqrt{d_k}}\n$$\n\n续。", null);

        assertEquals(3, segments.size());
        assertFalse(segments.get(0).isFormula);
        assertTrue(segments.get(1).isFormula);
        assertNotNull(segments.get(1).formula);
        assertFalse(segments.get(2).isFormula);
        assertEquals(0, spanCount(segments.get(0).text, MathSpan.class));
        assertEquals(0, spanCount(segments.get(2).text, MathSpan.class));
        assertTrue(segments.get(0).text.toString().contains("正文"));
        assertTrue(segments.get(2).text.toString().contains("续"));
    }

    @Test
    public void aFormulaSharingALineWithProseStaysInsideTheText() {
        List<MarkdownRenderer.Segment> segments = MarkdownRenderer.renderSegments(
                "其中 $$\\frac{1}{2}$$ 成立。", null);

        assertEquals(1, segments.size());
        assertFalse(segments.get(0).isFormula);
        assertEquals(1, spanCount(segments.get(0).text, MathSpan.class));
    }

    @Test
    public void anUnsupportedDisplayFormulaStaysAsMonospaceSourceInTheText() {
        List<MarkdownRenderer.Segment> segments = MarkdownRenderer.renderSegments(
                "矩阵：\n\n$$\\begin{matrix}a\\end{matrix}$$", null);

        assertEquals(1, segments.size());
        assertFalse(segments.get(0).isFormula);
        assertEquals(0, spanCount(segments.get(0).text, MathSpan.class));
        assertTrue(segments.get(0).text.toString().contains("\\begin{matrix}a\\end{matrix}"));
        assertEquals(1, spanCount(segments.get(0).text, TypefaceSpan.class));
    }

    @Test
    public void theFlatRenderKeepsADisplayFormulaOnItsOwnLine() {
        CharSequence rendered = MarkdownRenderer.render("正文。\n\n$$\\frac{1}{2}$$");

        String text = rendered.toString();
        assertEquals(1, spanCount(rendered, MathSpan.class));
        assertEquals(1, spanCount(rendered, AlignmentSpan.Standard.class));
        assertTrue("the formula must still reach a flat body as a span", text.endsWith("\ufffc"));
        assertEquals("a lifted formula must not be glued onto the sentence before it",
                '\n', text.charAt(text.length() - 2));
    }

    private static int spanCount(CharSequence text, Class<?> kind) {
        if (!(text instanceof Spanned)) {
            return 0;
        }
        Spanned spanned = (Spanned) text;
        return spanned.getSpans(0, spanned.length(), kind).length;
    }

    @Test
    public void unsupportedMathFallsBackToDelimiterFreeTintedMonospaceSource() {
        CharSequence rendered = MarkdownRenderer.render(
                "\u4e0d\u652f\u6301\uff1a$\\begin{matrix}a\\end{matrix}$\u3002");

        assertTrue(rendered instanceof Spanned);
        assertEquals("\u4e0d\u652f\u6301\uff1a\\begin{matrix}a\\end{matrix}\u3002", rendered.toString());
        Spanned spans = (Spanned) rendered;
        TypefaceSpan[] typefaces = spans.getSpans(0, spans.length(), TypefaceSpan.class);
        BackgroundColorSpan[] backgrounds = spans.getSpans(
                0, spans.length(), BackgroundColorSpan.class);
        assertEquals(1, typefaces.length);
        assertEquals("monospace", typefaces[0].getFamily());
        assertEquals(1, backgrounds.length);
        assertEquals(Color.rgb(242, 240, 232), backgrounds[0].getBackgroundColor());
    }

    @Test
    public void codeCurrencyAndUnclosedStreamingMathRemainUnrecognized() {
        CharSequence rendered = MarkdownRenderer.render(
                "\u884c\u5185\u4ee3\u7801 `$x^2$`\uff0c\u91d1\u989d $5 \u5230 $10\u3002\n\n```tex\n$x^2$\n```");

        assertTrue(rendered.toString().contains("$x^2$"));
        assertTrue(rendered.toString().contains("$5 \u5230 $10"));
        assertEquals(0, ((Spanned) rendered)
                .getSpans(0, rendered.length(), MathSpan.class).length);

        CharSequence streaming = MarkdownRenderer.render("\u6b63\u5728\u8f93\u5165 $$\\frac{1");
        assertEquals("\u6b63\u5728\u8f93\u5165 $$\\frac{1", streaming.toString());
        assertEquals(0, ((Spanned) streaming)
                .getSpans(0, streaming.length(), MathSpan.class).length);
    }

    @Test
    public void scannerShapedLiteralDoesNotCollideWithARealFormulaPlaceholder() {
        CharSequence rendered = MarkdownRenderer.render("TLMATH0TL \u4e0e $x^2$");

        assertTrue(rendered.toString().startsWith("TLMATH0TL \u4e0e "));
        assertEquals(1, ((Spanned) rendered)
                .getSpans(0, rendered.length(), MathSpan.class).length);
    }

    @Test
    public void formulaWiringKeepsCodeBlockCopyCallback() {
        final String[] copied = new String[1];

        CharSequence rendered = MarkdownRenderer.render(
                "```java\nreturn 1;\n```\n\n$x^2$", code -> copied[0] = code);

        ClickableSpan[] clickSpans = ((Spanned) rendered)
                .getSpans(0, rendered.length(), ClickableSpan.class);
        assertEquals(1, clickSpans.length);
        clickSpans[0].onClick(null);
        assertEquals("return 1;\n", copied[0]);
    }
}
