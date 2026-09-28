package com.treelearn.app.ui.math;

/**
 * One math formula extracted from markdown before commonmark parsing.
 */
public final class MathFormula {

    /** true when the formula was delimited as block level ($$...$$ or \[...\]). */
    public final boolean block;

    /** Inner LaTeX source without the delimiters. */
    public final String latex;

    /** ASCII-only marker left in the masked markdown. */
    public final String placeholder;

    public MathFormula(boolean block, String latex, String placeholder) {
        this.block = block;
        this.latex = latex;
        this.placeholder = placeholder;
    }
}
