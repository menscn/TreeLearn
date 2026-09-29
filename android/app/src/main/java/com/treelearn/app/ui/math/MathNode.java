package com.treelearn.app.ui.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable node tree for a parsed formula; produced by MathParser, consumed by the layout pass. */
public final class MathNode {

    public enum Kind { ROW, ATOM, FRAC, SQRT, SCRIPT, BIGOP, ACCENT, DELIMITED }

    public enum AtomKind { ORDINARY, NUMBER, OPERATOR, RELATION, PUNCT, TEXT }

    public final Kind kind;
    public final AtomKind atomKind;
    public final String text;
    public final List<MathNode> children;
    public final MathNode numerator;
    public final MathNode denominator;
    public final MathNode radicand;
    public final MathNode index;
    public final MathNode base;
    public final MathNode superscript;
    public final MathNode subscript;
    public final MathNode under;
    public final MathNode over;
    public final char left;
    public final char right;
    public final MathNode content;

    private MathNode(Kind kind, AtomKind atomKind, String text, List<MathNode> children,
            MathNode numerator, MathNode denominator, MathNode radicand, MathNode index,
            MathNode base, MathNode superscript, MathNode subscript, MathNode under,
            MathNode over, MathNode content, char left, char right) {
        this.kind = kind;
        this.atomKind = atomKind;
        this.text = text;
        this.children = children;
        this.numerator = numerator;
        this.denominator = denominator;
        this.radicand = radicand;
        this.index = index;
        this.base = base;
        this.superscript = superscript;
        this.subscript = subscript;
        this.under = under;
        this.over = over;
        this.content = content;
        this.left = left;
        this.right = right;
    }

    public static MathNode row(List<MathNode> children) {
        return new MathNode(Kind.ROW, null, null,
                Collections.unmodifiableList(new ArrayList<MathNode>(children)),
                null, null, null, null, null, null, null, null, null, null, '\0', '\0');
    }

    public static MathNode atom(String text, AtomKind atomKind) {
        return new MathNode(Kind.ATOM, atomKind, text, null,
                null, null, null, null, null, null, null, null, null, null, '\0', '\0');
    }

    public static MathNode frac(MathNode numerator, MathNode denominator) {
        return new MathNode(Kind.FRAC, null, null, null,
                numerator, denominator, null, null, null, null, null, null, null, null, '\0', '\0');
    }

    public static MathNode sqrt(MathNode radicand, MathNode index) {
        return new MathNode(Kind.SQRT, null, null, null,
                null, null, radicand, index, null, null, null, null, null, null, '\0', '\0');
    }

    public static MathNode script(MathNode base, MathNode superscript, MathNode subscript) {
        return new MathNode(Kind.SCRIPT, null, null, null,
                null, null, null, null, base, superscript, subscript, null, null, null, '\0', '\0');
    }

    public static MathNode bigOp(String glyph, MathNode under, MathNode over) {
        return new MathNode(Kind.BIGOP, null, glyph, null,
                null, null, null, null, null, null, null, under, over, null, '\0', '\0');
    }

    public static MathNode accent(String command, MathNode base) {
        return new MathNode(Kind.ACCENT, null, command, null,
                null, null, null, null, base, null, null, null, null, null, '\0', '\0');
    }

    public static MathNode delimited(char left, MathNode content, char right) {
        return new MathNode(Kind.DELIMITED, null, null, null,
                null, null, null, null, null, null, null, null, null, content, left, right);
    }
}
