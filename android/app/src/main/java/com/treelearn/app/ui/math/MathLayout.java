package com.treelearn.app.ui.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lays a {@link MathNode} tree into a flat list of glyphs and lines, in a geometry that a
 * {@code Canvas} can paint. Deliberately depends only on {@link MathMetrics} - no {@code Paint} or
 * {@code Canvas} - so the result is reproducible in a pure JVM test with a deterministic stub.
 *
 * <p>Geometry: a {@link MathBox} is measured from its top edge. {@code ascent} is the baseline
 * distance from the top; {@code descent} is the distance from the baseline to the bottom; height is
 * their sum. Each {@link Glyph} stores its own baseline measured down from the box top, and each
 * {@link HLine} stores its vertical position in the same frame.
 */
public final class MathLayout {

    private static final float SUPER_SCALE = 0.7f;
    private static final float UNDER_OVER_SCALE = 0.7f;
    private static final float ACCENT_SCALE = 0.85f;
    private static final float PAD_FACTOR = 0.12f;
    private static final float GAP_FACTOR = 0.05f;
    private static final float BAR_FACTOR = 0.06f;
    private static final float DELIM_HEIGHT_FACTOR = 0.9f;

    private MathLayout() {
    }

    /** Lays {@code node} at {@code baseSize}; the italic flag follows the node's own atom kind. */
    public static MathBox layout(MathNode node, float baseSize, MathMetrics m) {
        if (node == null) {
            return leaf("", baseSize, false, m);
        }
        return layoutNode(node, baseSize, m);
    }

    private static MathBox layoutNode(MathNode node, float size, MathMetrics m) {
        switch (node.kind) {
            case ATOM:
                return leaf(node.text == null ? "" : node.text, size,
                        node.atomKind == MathNode.AtomKind.ORDINARY, m);
            case ROW:
                return row(node.children, size, m);
            case FRAC:
                return frac(node, size, m);
            case SQRT:
                return sqrt(node, size, m);
            case SCRIPT:
                return script(node, size, m);
            case BIGOP:
                return bigOp(node, size, m);
            case ACCENT:
                return accent(node, size, m);
            case DELIMITED:
                return delimited(node, size, m);
            default:
                return leaf("", size, false, m);
        }
    }

    // A single line of text.
    private static MathBox leaf(String text, float size, boolean italic, MathMetrics m) {
        List<Glyph> glyphs = new ArrayList<Glyph>(1);
        float width = m.measureText(text, size, italic);
        float ascent = m.ascent(size);
        float descent = m.descent(size);
        glyphs.add(new Glyph(text, 0f, ascent, size, italic));
        return new MathBox(width, ascent, descent, glyphs, Collections.<HLine>emptyList());
    }

    // Horizontal sequence, baselines aligned, children composed left to right.
    private static MathBox row(List<MathNode> children, float size, MathMetrics m) {
        List<MathBox> boxes = new ArrayList<MathBox>(children.size());
        for (MathNode child : children) {
            boxes.add(layoutNode(child, size, m));
        }
        return compose(boxes, 0f);
    }

    private static MathBox frac(MathNode node, float size, MathMetrics m) {
        MathBox num = layoutNode(node.numerator, size, m);
        MathBox den = layoutNode(node.denominator, size, m);
        float pad = PAD_FACTOR * size;
        float bar = Math.max(1f, BAR_FACTOR * size);
        float contentW = Math.max(num.width, den.width);
        float boxW = contentW + 2f * pad;
        float barY = num.ascent + num.descent + pad;
        float denTop = barY + bar + pad;
        float ascent = num.ascent;
        float descent = (denTop + den.ascent + den.descent) - ascent;

        float numX = (boxW - num.width) / 2f;
        float denX = (boxW - den.width) / 2f;
        List<Glyph> glyphs = new ArrayList<Glyph>();
        shift(num, numX, 0f, glyphs, null);
        shift(den, denX, denTop, glyphs, null);
        List<HLine> lines = new ArrayList<HLine>(1);
        lines.add(new HLine(0f, barY, boxW, bar));
        return new MathBox(boxW, ascent, descent, glyphs, lines);
    }

    private static MathBox sqrt(MathNode node, float size, MathMetrics m) {
        MathBox rad = layoutNode(node.radicand, size, m);
        float pad = PAD_FACTOR * size;
        float gap = GAP_FACTOR * size;
        float delimSize = (rad.ascent + rad.descent) / DELIM_HEIGHT_FACTOR;
        MathBox root = leaf("\u221A", delimSize, false, m);
        float boxW = root.width + gap + rad.width;
        float ascent = Math.max(root.ascent, rad.ascent);
        float descent = Math.max(root.descent, rad.descent);
        float radY = ascent - rad.ascent;
        float rootY = ascent - root.ascent;
        float overY = radY - pad;
        List<Glyph> glyphs = new ArrayList<Glyph>();
        shift(root, 0f, rootY, glyphs, null);
        shift(rad, root.width + gap, radY, glyphs, null);
        List<HLine> lines = new ArrayList<HLine>(1);
        lines.add(new HLine(root.width - gap, overY, rad.width + gap, Math.max(1f, BAR_FACTOR * size)));
        return new MathBox(boxW, ascent, descent, glyphs, lines);
    }

    private static MathBox script(MathNode node, float size, MathMetrics m) {
        MathBox base = layoutNode(node.base, size, m);
        MathBox sup = node.superscript == null ? null : layoutNode(node.superscript, size * SUPER_SCALE, m);
        MathBox sub = node.subscript == null ? null : layoutNode(node.subscript, size * SUPER_SCALE, m);
        float rise = sup == null ? 0f : sup.descent * 0.5f + 0.1f * size;
        float drop = sub == null ? 0f : sub.ascent * 0.5f + 0.1f * size;
        float ascent = base.ascent + rise;
        float descent = base.descent + drop;
        float boxW = base.width + Math.max(sup == null ? 0f : sup.width, sub == null ? 0f : sub.width);
        float supBaselineY = sup == null ? ascent : base.ascent - rise;
        float subBaselineY = sub == null ? base.ascent : base.ascent + drop;
        List<Glyph> glyphs = new ArrayList<Glyph>();
        shift(base, 0f, 0f, glyphs, null);
        if (sup != null) {
            shift(sup, base.width, supBaselineY - sup.ascent, glyphs, null);
        }
        if (sub != null) {
            shift(sub, base.width, subBaselineY - sub.ascent, glyphs, null);
        }
        return new MathBox(boxW, ascent, descent, glyphs, Collections.<HLine>emptyList());
    }

    private static MathBox bigOp(MathNode node, float size, MathMetrics m) {
        MathBox op = leaf(node.text == null ? "" : node.text, size, false, m);
        MathBox under = node.under == null ? null : layoutNode(node.under, size * UNDER_OVER_SCALE, m);
        MathBox over = node.over == null ? null : layoutNode(node.over, size * UNDER_OVER_SCALE, m);
        float gap = GAP_FACTOR * size;
        float boxW = Math.max(Math.max(op.width, under == null ? 0f : under.width),
                over == null ? 0f : over.width);
        float overTop = 0f;
        float overBaseline = over == null ? 0f : over.ascent;
        float opTop = overTop + (over == null ? 0f : over.ascent + over.descent) + gap;
        float opBaseline = opTop + op.ascent;
        float underTop = opTop + op.ascent + op.descent + gap;
        float underBaseline = underTop + (under == null ? 0f : under.ascent);
        float total = underTop + (under == null ? 0f : under.ascent + under.descent);
        float ascent = opBaseline;
        float descent = total - ascent;
        List<Glyph> glyphs = new ArrayList<Glyph>();
        if (over != null) {
            shift(over, (boxW - over.width) / 2f, overBaseline - over.ascent, glyphs, null);
        }
        shift(op, (boxW - op.width) / 2f, opBaseline - op.ascent, glyphs, null);
        if (under != null) {
            shift(under, (boxW - under.width) / 2f, underBaseline - under.ascent, glyphs, null);
        }
        return new MathBox(boxW, ascent, descent, glyphs, Collections.<HLine>emptyList());
    }

    private static MathBox accent(MathNode node, float size, MathMetrics m) {
        MathBox base = layoutNode(node.base, size, m);
        String mark = accentMark(node.text);
        MathBox mk = leaf(mark, size * ACCENT_SCALE, false, m);
        float lift = 0.1f * size;
        float ascent = base.ascent + lift + mk.ascent;
        float descent = base.descent;
        // place mark baseline just above the base top
        float mkBaseline = base.ascent + lift;
        List<Glyph> glyphs = new ArrayList<Glyph>();
        shift(base, 0f, 0f, glyphs, null);
        shift(mk, (base.width - mk.width) / 2f, mkBaseline - mk.ascent, glyphs, null);
        return new MathBox(base.width, ascent, descent, glyphs, Collections.<HLine>emptyList());
    }

    private static MathBox delimited(MathNode node, float size, MathMetrics m) {
        MathBox content = layoutNode(node.content, size, m);
        float delimSize = (content.ascent + content.descent) / DELIM_HEIGHT_FACTOR;
        MathBox left = leaf(String.valueOf(node.left), delimSize, false, m);
        MathBox right = leaf(String.valueOf(node.right), delimSize, false, m);
        float gap = GAP_FACTOR * size;
        float boxW = left.width + gap + content.width + gap + right.width;
        float ascent = Math.max(Math.max(left.ascent, right.ascent), content.ascent);
        float descent = Math.max(Math.max(left.descent, right.descent), content.descent);
        List<Glyph> glyphs = new ArrayList<Glyph>();
        shift(left, 0f, ascent - left.ascent, glyphs, null);
        shift(content, left.width + gap, ascent - content.ascent, glyphs, null);
        shift(right, left.width + gap + content.width + gap, ascent - right.ascent, glyphs, null);
        return new MathBox(boxW, ascent, descent, glyphs, Collections.<HLine>emptyList());
    }

    // Stacks boxes left to right, baselines aligned to the tallest ascent.
    private static MathBox compose(List<MathBox> boxes, float gap) {
        float ascent = 0f;
        float descent = 0f;
        float cursor = 0f;
        for (MathBox box : boxes) {
            ascent = Math.max(ascent, box.ascent);
            descent = Math.max(descent, box.descent);
            cursor += box.width + gap;
        }
        cursor -= gap;
        List<Glyph> glyphs = new ArrayList<Glyph>();
        List<HLine> lines = new ArrayList<HLine>();
        float x = 0f;
        for (MathBox box : boxes) {
            shift(box, x, ascent - box.ascent, glyphs, lines);
            x += box.width + gap;
        }
        return new MathBox(cursor, ascent, descent, glyphs, lines);
    }

    // Copies child geometry into the parent frame, offset by (dx, dy).
    private static void shift(MathBox box, float dx, float dy, List<Glyph> glyphs, List<HLine> lines) {
        for (Glyph g : box.glyphs) {
            glyphs.add(new Glyph(g.text, dx + g.x, dy + g.baselineY, g.textSize, g.italic));
        }
        if (lines != null) {
            for (HLine h : box.lines) {
                lines.add(new HLine(dx + h.x, dy + h.y, h.width, h.thickness));
            }
        }
    }

    private static String accentMark(String command) {
        if ("vec".equals(command)) {
            return "\u2192";
        } else if ("bar".equals(command)) {
            return "\u00AF";
        } else if ("hat".equals(command)) {
            return "^";
        } else if ("dot".equals(command)) {
            return "\u00B7";
        } else if ("tilde".equals(command)) {
            return "~";
        }
        return "\u00AF";
    }

    /** Computed geometry plus the draw primitives for one formula. */
    public static final class MathBox {
        public final float width;
        public final float ascent;
        public final float descent;
        public final List<Glyph> glyphs;
        public final List<HLine> lines;

        MathBox(float width, float ascent, float descent, List<Glyph> glyphs, List<HLine> lines) {
            this.width = width;
            this.ascent = ascent;
            this.descent = descent;
            this.glyphs = Collections.unmodifiableList(glyphs);
            this.lines = Collections.unmodifiableList(lines);
        }

        public float height() {
            return ascent + descent;
        }
    }

    /** One run of text to paint at a baseline measured down from the box top. */
    public static final class Glyph {
        public final String text;
        public final float x;
        public final float baselineY;
        public final float textSize;
        public final boolean italic;

        Glyph(String text, float x, float baselineY, float textSize, boolean italic) {
            this.text = text;
            this.x = x;
            this.baselineY = baselineY;
            this.textSize = textSize;
            this.italic = italic;
        }
    }

    /** One horizontal stroke (fraction bar, root overline). y is measured down from the box top. */
    public static final class HLine {
        public final float x;
        public final float y;
        public final float width;
        public final float thickness;

        HLine(float x, float y, float width, float thickness) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.thickness = thickness;
        }
    }
}
