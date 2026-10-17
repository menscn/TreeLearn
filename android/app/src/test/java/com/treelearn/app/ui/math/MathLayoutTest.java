package com.treelearn.app.ui.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Layout geometry on the JVM with a deterministic stub - no Android Paint involved. */
public class MathLayoutTest {

    /** Length-driven stub: each character is 10 units wide, 0.8/0.2 above/below the baseline. */
    private static final class FakeMetrics implements MathMetrics {
        @Override
        public float measureText(String text, float textSize, boolean italic) {
            return text.length() * 10f * (textSize / 100f);
        }

        @Override
        public float ascent(float textSize) {
            return 0.8f * textSize;
        }

        @Override
        public float descent(float textSize) {
            return 0.2f * textSize;
        }
    }

    private static MathLayout.MathBox layout(String latex) {
        MathNode node = MathParser.parse(latex);
        assertNotNull("latex must parse: " + latex, node);
        return MathLayout.layout(node, 100f, new FakeMetrics());
    }

    @Test
    public void fracIsTallerThanASingleLineAndHasABar() {
        MathLayout.MathBox box = layout("\\frac{1}{2}");

        float singleLine = 0.8f * 100f + 0.2f * 100f;
        assertTrue("fraction must occupy more than one text line", box.height() > singleLine);
        assertEquals("fraction bar is a horizontal line", 1, box.lines.size());
        // Bar sits between numerator and denominator, i.e. below the numerator's baseline.
        MathLayout.HLine bar = box.lines.get(0);
        assertTrue("bar is below the top of the box", bar.y > 0f);
        assertTrue("bar is above the bottom of the box", bar.y < box.height());
    }

    @Test
    public void superscriptSitsAboveTheBaseAndIsSmaller() {
        MathLayout.MathBox box = layout("x^2");

        // Find the base glyph ("x", normal size) and the superscript glyph ("2", smaller).
        MathLayout.Glyph base = find(box, "x");
        MathLayout.Glyph sup = find(box, "2");
        assertNotNull(base);
        assertNotNull(sup);
        assertTrue("superscript baseline is above the base baseline", sup.baselineY < base.baselineY);
        assertTrue("superscript is drawn smaller", sup.textSize < base.textSize);
        assertEquals("base letter is slanted like a math variable", true, base.italic);
    }

    @Test
    public void bigOperatorCarriesLimitsAndIsAtLeastAsWideAsTheGlyph() {
        MathLayout.MathBox box = layout("\\sum_{i=1}^{n}");

        // Lower limit is a row of i, =, 1; upper limit is n; the operator glyph is sum.
        assertTrue("operator glyph present", hasText(box, "\u2211"));
        assertTrue("lower limit carries i", hasText(box, "i"));
        assertTrue("lower limit carries 1", hasText(box, "1"));
        assertTrue("upper limit carries n", hasText(box, "n"));
        assertTrue("box at least as wide as the glyph itself", box.width >= 10f);
    }

    @Test
    public void delimiterGrowsWithTallContent() {
        MathLayout.MathBox tall = layout("\\left(\\frac{a}{b}\\right)");
        MathLayout.MathBox shortBox = layout("\\left(x\\right)");

        assertTrue("delimiter height follows the enclosed content", tall.height() > shortBox.height());
    }

    @Test
    public void textAtomIsUprightWhileLetterIsItalic() {
        MathLayout.MathBox text = layout("\\text{hi}");
        MathLayout.MathBox letter = layout("x");

        MathLayout.Glyph t = find(text, "hi");
        MathLayout.Glyph l = find(letter, "x");
        assertNotNull(t);
        assertNotNull(l);
        assertEquals("text content stays upright", false, t.italic);
        assertEquals("single letter is a math variable, slanted", true, l.italic);
    }

    /**
     * A rule drawn by an enclosed box has to reach the box that paints it. Every composite used to
     * collect glyphs but pass {@code null} for lines, so a fraction inside {@code \left(} lost its
     * bar and a radical inside a numerator lost its overline while the measurement stayed correct -
     * the formula looked sized but incomplete, and no width test could see it.
     */
    @Test
    public void aNestedRuleSurvivesTheBoxThatEnclosesIt() {
        MathLayout.MathBox plain = layout("\\frac{a}{b}");

        MathLayout.MathBox inDelimiter = layout("\\left(\\frac{a}{b}\\right)");
        MathLayout.MathBox inScript = layout("x^{\\frac{a}{b}}");
        MathLayout.MathBox radicalInFrac = layout("\\frac{\\sqrt{a}}{b}");

        assertEquals("the fraction bar must survive inside a delimiter",
                plain.lines.size(), inDelimiter.lines.size());
        assertEquals("the fraction bar must survive inside a superscript",
                plain.lines.size(), inScript.lines.size());
        assertEquals("a radical's overline must survive inside a fraction, next to its bar",
                2, radicalInFrac.lines.size());

        MathLayout.HLine moved = inDelimiter.lines.get(0);
        assertTrue("the copied bar must be re-based into the enclosing box, not left in child "
                        + "coordinates: child=" + plain.lines.get(0).y + " parent=" + moved.y
                        + " box=" + inDelimiter.height(),
                moved.y > plain.lines.get(0).y && moved.y < inDelimiter.height());
    }

    private static MathLayout.Glyph find(MathLayout.MathBox box, String text) {
        for (MathLayout.Glyph g : box.glyphs) {
            if (text.equals(g.text)) {
                return g;
            }
        }
        return null;
    }

    private static boolean hasText(MathLayout.MathBox box, String text) {
        return find(box, text) != null;
    }
}
