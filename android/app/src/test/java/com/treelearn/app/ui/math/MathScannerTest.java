package com.treelearn.app.ui.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class MathScannerTest {

    @Test
    public void blockDoubleDollarIsExtractedAsBlockFormula() {
        MathScanResult result = MathScanner.scan("$$\nE=mc^2\n$$");

        assertEquals(1, result.formulas.size());
        MathFormula formula = result.formulas.get(0);
        assertTrue(formula.block);
        assertEquals("E=mc^2", formula.latex);
        assertEquals("TLMATH0TL", formula.placeholder);
        assertTrue(result.masked.contains("TLMATH0TL"));
        assertFalse("masked must not keep the $ delimiters", result.masked.contains("$"));
    }

    @Test
    public void inlineDollarKeepsSurroundingChineseText() {
        MathScanResult result = MathScanner.scan("设 $x^2+y^2=r^2$ 可得");

        assertEquals(1, result.formulas.size());
        MathFormula formula = result.formulas.get(0);
        assertFalse(formula.block);
        assertEquals("x^2+y^2=r^2", formula.latex);
        assertEquals("设 TLMATH0TL 可得", result.masked);
    }

    @Test
    public void backslashDelimitersAreRecognized() {
        MathScanResult inline = MathScanner.scan("值 \\(a+b\\) 结束");
        assertEquals(1, inline.formulas.size());
        assertFalse(inline.formulas.get(0).block);
        assertEquals("a+b", inline.formulas.get(0).latex);

        MathScanResult block = MathScanner.scan("值 \\[a+b\\] 结束");
        assertEquals(1, block.formulas.size());
        assertTrue(block.formulas.get(0).block);
        assertEquals("a+b", block.formulas.get(0).latex);
    }

    @Test
    public void doubleDollarWinsOverSingleDollar() {
        MathScanResult result = MathScanner.scan("$$a^2$$ 与 $b^2$");

        assertEquals(2, result.formulas.size());
        assertTrue(result.formulas.get(0).block);
        assertEquals("a^2", result.formulas.get(0).latex);
        assertFalse(result.formulas.get(1).block);
        assertEquals("b^2", result.formulas.get(1).latex);
        assertEquals("TLMATH0TL 与 TLMATH1TL", result.masked);
    }

    @Test
    public void fencedCodeBlockIsLeftUntouched() {
        String markdown = "```\n$x$\n```\n";

        MathScanResult result = MathScanner.scan(markdown);

        assertTrue(result.formulas.isEmpty());
        assertEquals(markdown, result.masked);
    }

    @Test
    public void tildeFencedCodeBlockIsLeftUntouched() {
        String markdown = "~~~\n$$E=mc^2$$\n~~~\n";

        MathScanResult result = MathScanner.scan(markdown);

        assertTrue(result.formulas.isEmpty());
        assertEquals(markdown, result.masked);
    }

    @Test
    public void indentedCodeBlockIsLeftUntouched() {
        String markdown = "    $x^2$\n";

        MathScanResult result = MathScanner.scan(markdown);

        assertTrue(result.formulas.isEmpty());
        assertEquals(markdown, result.masked);
    }

    @Test
    public void inlineCodeSpanIsLeftUntouched() {
        String markdown = "代码 `$x$` 结束";

        MathScanResult result = MathScanner.scan(markdown);

        assertTrue(result.formulas.isEmpty());
        assertEquals(markdown, result.masked);
    }

    @Test
    public void currencyAmountsAreNotExtracted() {
        String markdown = "价格 $5 到 $10";

        MathScanResult result = MathScanner.scan(markdown);

        assertTrue(result.formulas.isEmpty());
        assertEquals(markdown, result.masked);
    }

    @Test
    public void unterminatedDelimiterIsLeftAsSource() {
        String markdown = "流式 $$\\frac{1";

        MathScanResult result = MathScanner.scan(markdown);

        assertTrue(result.formulas.isEmpty());
        assertEquals(markdown, result.masked);
    }

    @Test
    public void inlineDollarDoesNotCrossBlankLine() {
        String markdown = "$x^2\n\n+y^2$\n";

        MathScanResult result = MathScanner.scan(markdown);

        assertTrue(result.formulas.isEmpty());
        assertEquals(markdown, result.masked);
    }

    @Test
    public void placeholdersAreAsciiSafeAndIndexed() {
        MathScanResult result = MathScanner.scan("$a_1$ 与 $$b^2$$");

        List<MathFormula> formulas = result.formulas;
        assertEquals(2, formulas.size());
        for (int index = 0; index < formulas.size(); index++) {
            MathFormula formula = formulas.get(index);
            assertEquals("TLMATH" + index + "TL", formula.placeholder);
            assertTrue("placeholder must be ASCII alphanumerics: " + formula.placeholder,
                    formula.placeholder.matches("[A-Za-z0-9]+"));
        }
        assertFalse(result.masked.contains("\\"));
        assertFalse(result.masked.contains("_"));
        assertFalse(result.masked.contains("*"));
        assertFalse(result.masked.contains("`"));
        assertFalse(result.masked.contains("["));
        assertFalse(result.masked.contains("]"));
    }

    @Test
    public void scanIsIdempotentOnMaskedOutput() {
        MathScanResult first = MathScanner.scan("设 $x^2$ 与 $$y_1$$");
        assertEquals(2, first.formulas.size());

        MathScanResult second = MathScanner.scan(first.masked);

        assertTrue(second.formulas.isEmpty());
        assertEquals(first.masked, second.masked);
        assertEquals(first.masked, MathScanner.scan(first.masked).masked);
    }
}
