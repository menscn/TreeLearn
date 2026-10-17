package com.treelearn.app.ui.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MathParserTest {

    @Test
    public void fracParsesNumeratorAndDenominator() {
        MathNode node = MathParser.parse("\\frac{1}{2}");
        assertNotNull(node);
        assertEquals(MathNode.Kind.FRAC, node.kind);
        assertEquals(MathNode.Kind.ATOM, node.numerator.kind);
        assertEquals("1", node.numerator.text);
        assertEquals(MathNode.AtomKind.NUMBER, node.numerator.atomKind);
        assertEquals("2", node.denominator.text);
        assertEquals(MathNode.AtomKind.NUMBER, node.denominator.atomKind);
    }

    @Test
    public void superscriptSupportsBareTokenAndBracedGroup() {
        MathNode bare = MathParser.parse("x^2");
        assertNotNull(bare);
        assertEquals(MathNode.Kind.SCRIPT, bare.kind);
        assertEquals("x", bare.base.text);
        assertEquals(MathNode.AtomKind.ORDINARY, bare.base.atomKind);
        assertEquals("2", bare.superscript.text);
        assertNull(bare.subscript);

        MathNode braced = MathParser.parse("x^{ab}");
        assertNotNull(braced);
        assertEquals(MathNode.Kind.SCRIPT, braced.kind);
        assertEquals(MathNode.Kind.ROW, braced.superscript.kind);
        assertEquals(2, braced.superscript.children.size());
    }

    @Test
    public void subscriptCommaGroupBecomesRow() {
        MathNode node = MathParser.parse("x_{i,j}");
        assertNotNull(node);
        assertEquals(MathNode.Kind.SCRIPT, node.kind);
        assertNull(node.superscript);
        MathNode sub = node.subscript;
        assertNotNull(sub);
        assertEquals(MathNode.Kind.ROW, sub.kind);
        assertEquals(3, sub.children.size());
        assertEquals("i", sub.children.get(0).text);
        assertEquals(",", sub.children.get(1).text);
        assertEquals(MathNode.AtomKind.PUNCT, sub.children.get(1).atomKind);
        assertEquals("j", sub.children.get(2).text);
    }

    @Test
    public void scriptKeepsBothSuperscriptAndSubscript() {
        MathNode node = MathParser.parse("x^2_i");
        assertNotNull(node);
        assertEquals(MathNode.Kind.SCRIPT, node.kind);
        assertEquals("2", node.superscript.text);
        assertEquals("i", node.subscript.text);
    }

    @Test
    public void sqrtSupportsOptionalIndex() {
        MathNode plain = MathParser.parse("\\sqrt{x}");
        assertNotNull(plain);
        assertEquals(MathNode.Kind.SQRT, plain.kind);
        assertEquals("x", plain.radicand.text);
        assertNull(plain.index);

        MathNode indexed = MathParser.parse("\\sqrt[3]{x}");
        assertNotNull(indexed);
        assertEquals(MathNode.Kind.SQRT, indexed.kind);
        assertEquals("x", indexed.radicand.text);
        assertNotNull(indexed.index);
        assertEquals("3", indexed.index.text);
    }

    @Test
    public void textKeepsContentVerbatim() {
        MathNode node = MathParser.parse("\\text{其中 x}");
        assertNotNull(node);
        assertEquals(MathNode.Kind.ATOM, node.kind);
        assertEquals(MathNode.AtomKind.TEXT, node.atomKind);
        assertEquals("其中 x", node.text);

        MathNode raw = MathParser.parse("\\text{其中 \\alpha}");
        assertNotNull(raw);
        assertEquals("其中 \\alpha", raw.text);
    }

    @Test
    public void delimitedKeepsLeftAndRightFences() {
        MathNode node = MathParser.parse("\\left(\\frac{a}{b}\\right)");
        assertNotNull(node);
        assertEquals(MathNode.Kind.DELIMITED, node.kind);
        assertEquals('(', node.left);
        assertEquals(')', node.right);
        assertEquals(MathNode.Kind.FRAC, node.content.kind);

        MathNode braces = MathParser.parse("\\left\\{x\\right\\}");
        assertNotNull(braces);
        assertEquals('{', braces.left);
        assertEquals('}', braces.right);

        MathNode angle = MathParser.parse("\\left\\langle x \\right\\rangle");
        assertNotNull(angle);
        assertEquals('⟨', angle.left);
        assertEquals('⟩', angle.right);
    }

    @Test
    public void biglBigrCommandsCreateDelimitedNode() {
        MathNode node = MathParser.parse("\\bigl(x\\bigr)");
        assertNotNull(node);
        assertEquals(MathNode.Kind.DELIMITED, node.kind);
        assertEquals('(', node.left);
        assertEquals(')', node.right);
        assertEquals("x", node.content.text);
    }

    @Test
    public void BiglBigrCommandsCreateDelimitedNode() {
        MathNode node = MathParser.parse("\\Bigl(x\\Bigr)");
        assertNotNull(node);
        assertEquals(MathNode.Kind.DELIMITED, node.kind);
        assertEquals('(', node.left);
        assertEquals(')', node.right);
        assertEquals("x", node.content.text);
    }

    @Test
    public void bigglBiggrCommandsCreateDelimitedNode() {
        MathNode node = MathParser.parse("\\biggl(x\\biggr)");
        assertNotNull(node);
        assertEquals(MathNode.Kind.DELIMITED, node.kind);
        assertEquals('(', node.left);
        assertEquals(')', node.right);
        assertEquals("x", node.content.text);
    }

    @Test
    public void BigglBiggrCommandsCreateDelimitedNode() {
        MathNode node = MathParser.parse("\\Biggl(x\\Biggr)");
        assertNotNull(node);
        assertEquals(MathNode.Kind.DELIMITED, node.kind);
        assertEquals('(', node.left);
        assertEquals(')', node.right);
        assertEquals("x", node.content.text);
    }

    @Test
    public void mismatchedBigDelimiterCommandsAreUnsupported() {
        assertNull(MathParser.parse("\\bigl(x\\Bigr)"));
        assertNull(MathParser.parse("\\Bigl(x\\bigr)"));
        assertNull(MathParser.parse("\\biggl(x\\Biggr)"));
        assertNull(MathParser.parse("\\Biggl(x\\biggr)"));
    }

    @Test
    public void bigDelimiterWithoutClosingCommandIsUnsupported() {
        assertNull(MathParser.parse("\\bigl(x"));
        assertNull(MathParser.parse("\\Bigl(x"));
        assertNull(MathParser.parse("\\biggl(x"));
        assertNull(MathParser.parse("\\Biggl(x"));
    }

    @Test
    public void bigOperatorCarriesUpperAndLowerLimits() {
        MathNode node = MathParser.parse("\\sum_{i=1}^{n}");
        assertNotNull(node);
        assertEquals(MathNode.Kind.BIGOP, node.kind);
        assertEquals("∑", node.text);
        assertEquals(MathNode.Kind.ROW, node.under.kind);
        assertEquals(3, node.under.children.size());
        assertEquals("=", node.under.children.get(1).text);
        assertEquals(MathNode.AtomKind.RELATION, node.under.children.get(1).atomKind);
        assertEquals("n", node.over.text);
    }

    @Test
    public void limUsesWordGlyphAndLowerLimit() {
        MathNode node = MathParser.parse("\\lim_{x \\to 0}");
        assertNotNull(node);
        assertEquals(MathNode.Kind.BIGOP, node.kind);
        assertEquals("lim", node.text);
        assertNull(node.over);
        assertEquals(MathNode.Kind.ROW, node.under.kind);
        assertEquals(3, node.under.children.size());
        assertEquals("→", node.under.children.get(1).text);
        assertEquals("0", node.under.children.get(2).text);
    }

    @Test
    public void greekLettersBecomeOrdinaryAtoms() {
        MathNode node = MathParser.parse("\\alpha\\beta");
        assertNotNull(node);
        assertEquals(MathNode.Kind.ROW, node.kind);
        assertEquals(2, node.children.size());
        assertEquals("α", node.children.get(0).text);
        assertEquals("β", node.children.get(1).text);
        assertEquals(MathNode.AtomKind.ORDINARY, node.children.get(0).atomKind);
    }

    @Test
    public void accentWrapsBase() {
        MathNode node = MathParser.parse("\\vec{x}");
        assertNotNull(node);
        assertEquals(MathNode.Kind.ACCENT, node.kind);
        assertEquals("vec", node.text);
        assertEquals("x", node.base.text);
    }

    @Test
    public void operatorsRelationsAndPunctuationAreClassified() {
        MathNode node = MathParser.parse("a+b=c");
        assertNotNull(node);
        assertEquals(MathNode.Kind.ROW, node.kind);
        assertEquals(5, node.children.size());
        assertEquals(MathNode.AtomKind.OPERATOR, node.children.get(1).atomKind);
        assertEquals(MathNode.AtomKind.RELATION, node.children.get(3).atomKind);
    }

    @Test
    public void spacingCommandsAreIgnored() {
        MathNode node = MathParser.parse("a\\,b\\;c");
        assertNotNull(node);
        assertEquals(MathNode.Kind.ROW, node.kind);
        assertEquals(3, node.children.size());
    }

    @Test
    public void environmentIsUnsupported() {
        assertNull(MathParser.parse("\\begin{matrix}a\\end{matrix}"));
    }

    @Test
    public void unknownCommandIsUnsupported() {
        assertNull(MathParser.parse("\\unknowncmd{x}"));
    }

    @Test
    public void unmatchedBracesAreUnsupported() {
        assertNull(MathParser.parse("{a"));
        assertNull(MathParser.parse("a}"));
        assertNull(MathParser.parse("\\frac{1}{2"));
    }

    @Test
    public void leftWithoutRightIsUnsupported() {
        assertNull(MathParser.parse("\\left(x"));
    }

    @Test
    public void newlineCommandIsUnsupported() {
        assertNull(MathParser.parse("a \\\\ b"));
    }

    @Test
    public void escapedBracesAreLiteralBraces() {
        MathNode node = MathParser.parse("\\{a,b\\}");
        assertNotNull(node);
        assertEquals(MathNode.Kind.ROW, node.kind);
        assertEquals("{", node.children.get(0).text);
        assertEquals("}", node.children.get(node.children.size() - 1).text);
    }

    @Test
    public void escapedPunctuationIsALiteralAndNotItsCommandMeaning() {
        MathNode percent = MathParser.parse("x \\% y");
        assertNotNull(percent);
        assertEquals(MathNode.Kind.ROW, percent.kind);
        assertEquals("%", percent.children.get(1).text);

        MathNode underscore = MathParser.parse("a\\_b");
        assertNotNull(underscore);
        assertEquals("an escaped underscore is a character, not a subscript",
                MathNode.Kind.ROW, underscore.kind);
        assertEquals(3, underscore.children.size());
        assertEquals("_", underscore.children.get(1).text);

        MathNode ampersand = MathParser.parse("A \\& B");
        assertNotNull(ampersand);
        assertEquals("&", ampersand.children.get(1).text);

        MathNode hash = MathParser.parse("p \\# q");
        assertNotNull(hash);
        assertEquals("#", hash.children.get(1).text);

        MathNode dollar = MathParser.parse("c_1 \\$ d");
        assertNotNull(dollar);
        assertEquals(MathNode.Kind.ROW, dollar.kind);
        assertEquals("$", dollar.children.get(1).text);
    }

    @Test
    public void topSuperscriptIsDrawnAsItsOwnGlyph() {
        MathNode node = MathParser.parse("K^\\top");
        assertNotNull(node);
        assertEquals(MathNode.Kind.SCRIPT, node.kind);
        assertEquals("⊤", node.superscript.text);
    }

    @Test
    public void aModelAnsweredAttentionFormulaParses() {
        assertNotNull(MathParser.parse("\\text{Attention}(q, \\{k_i,v_i\\}) = \\sum_i"
                + " \\text{softmax}_i!\\left(\\text{score}(q,k_i)\\right)\\cdot v_i"));
        assertNotNull(MathParser.parse("\\text{Attention}(Q,K,V) = \\text{softmax}\\!"
                + "\\left(\\frac{QK^\\top}{\\sqrt{d_k}}\\right)V"));
    }

    @Test
    public void nullInputIsUnsupported() {
        assertNull(MathParser.parse(null));
        assertTrue(MathParser.parse("x") != null);
    }
}
