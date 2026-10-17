package com.treelearn.app.screen;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.view.View;

import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.treelearn.app.ui.math.MathFormula;
import com.treelearn.app.ui.math.MathNode;
import com.treelearn.app.ui.math.MathParser;
import com.treelearn.app.ui.math.MathScanResult;
import com.treelearn.app.ui.math.MathScanner;
import com.treelearn.app.ui.math.MathSpan;

/**
 * Converts CommonMark into selectable Android spans without exposing Markdown syntax to the user.
 *
 * <p><strong>A table is a view, not text</strong> (2026-09-28 user decision). The first cut drew a
 * GFM table as monospaced box characters; it cannot work, and the reason is structural rather than
 * cosmetic. Box drawing only lines up inside one unwrapped line, and the answer's {@link
 * android.widget.TextView} is {@code match_parent}, so a wide row soft-wraps and every following
 * box character lands in the wrong column. Monospace guarantees equal character advance, never that
 * the box goes unbroken. Counting widths in {@code String.length()} made it worse: a CJK glyph is
 * one UTF-16 unit but takes two character cells.
 *
 * <p><strong>A display formula is a view for the same reason</strong> (2026-10-16). A formula never
 * wraps, so a wide one in that same {@code match_parent} view is cut at both edges while still
 * looking rendered. {@link #renderSegments} lifts a formula that owns its paragraph out of the text
 * just as it lifts a table; a formula sharing a line with prose stays a span, because it is sized by
 * that line.
 *
 * <p>So {@link #renderSegments} emits a <em>sequence</em>: prose runs, tables carrying their rows as
 * data, and display formulas carrying their parsed tree. {@link ChatScreen} lays each table out with
 * real cells and each formula with a {@link com.treelearn.app.ui.math.MathView}, which is the one
 * thing layout is good at. {@link #render(String, OnCodeBlockClick)} stays for reasoning and
 * translation bodies - flat text with neither of their own - and joins the segments back into one
 * string.
 */
public final class MarkdownRenderer {

    /** Matrix 1.2: a tap on a code block copies it; the callback is the screen's to forward. */
    public interface OnCodeBlockClick {

        void onCodeBlockClick(String code);
    }

    private static final int CODE_BACKGROUND = Color.rgb(242, 240, 232);
    private static final int QUOTE_COLOR = Color.rgb(91, 109, 99);

    private MarkdownRenderer() {
    }

    /** One piece of a rendered answer: a run of prose, a table, or one display formula. */
    public static final class Segment {

        /** True when {@link #table} is present and {@link #text} is not. */
        public final boolean isTable;
        /** True when {@link #formula} is present and {@link #text} is not. */
        public final boolean isFormula;
        /** The prose run; null for a table or formula segment. */
        public final CharSequence text;
        /** The display formula, already parsed; null unless {@link #isFormula}. */
        public final MathNode formula;
        /** The table's rows; null for a prose segment. Every row has the table's column count. */
        public final List<List<CharSequence>> table;
        /** The header spelling of each column, or empty when the source table declared no header. */
        public final List<String> header;
        /** True when the source table declared a header row. */
        public final boolean hasHeader;

        private Segment(CharSequence text, List<List<CharSequence>> table, List<String> header,
                boolean hasHeader, MathNode formula) {
            this.text = text;
            this.formula = formula;
            this.table = table == null ? null : Collections.unmodifiableList(table);
            this.header = header == null ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(header);
            this.hasHeader = hasHeader;
            this.isTable = table != null;
            this.isFormula = formula != null;
        }

        static Segment prose(CharSequence text) {
            return new Segment(text, null, null, false, null);
        }

        static Segment table(List<List<CharSequence>> rows, List<String> header, boolean hasHeader) {
            return new Segment(null, rows, header, hasHeader, null);
        }

        static Segment formula(MathNode node) {
            return new Segment(null, null, null, false, node);
        }
    }

    public static CharSequence render(String markdown) {
        return render(markdown, null);
    }

    /** Flat rendering: tables and formulas are joined back into text. For bodies that show neither. */
    public static CharSequence render(String markdown, OnCodeBlockClick codeClick) {
        List<Segment> segments = renderSegments(markdown, codeClick);
        if (segments.size() == 1 && segments.get(0).text != null) {
            return segments.get(0).text;
        }
        SpannableStringBuilder joined = new SpannableStringBuilder();
        for (Segment segment : segments) {
            if (segment.isTable) {
                joined.append(flattenTable(segment));
            } else if (segment.isFormula) {
                ensureBlockBreak(joined);
                joined.append(flattenFormula(segment));
            } else {
                joined.append(segment.text);
            }
        }
        trimTrailingNewlines(joined);
        return joined;
    }

    /**
     * The answer body's rendering: prose runs, table blocks and display formulas in document order.
     * A table never reaches a {@code TextView}, so no box character can be mis-wrapped; a formula
     * alone in its paragraph does not either, because a formula cannot wrap and a wide one would
     * lose both ends. The caller lays each one out as a view.
     */
    public static List<Segment> renderSegments(String markdown, OnCodeBlockClick codeClick) {
        List<Segment> segments = new ArrayList<Segment>();
        if (markdown == null || markdown.isEmpty()) {
            return segments;
        }
        PlaceholderProtection literalProtection = protectLiteralPlaceholders(markdown);
        MathScanResult math = MathScanner.scan(literalProtection.markdown);
        Node document = Parser.builder()
                .extensions(Collections.singletonList(TablesExtension.create()))
                .build()
                .parse(math.masked);
        SpannableStringBuilder prose = new SpannableStringBuilder();
        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof TableBlock) {
                applyMath(prose, math.formulas, literalProtection.placeholders);
                flush(segments, prose);
                segments.add(readTable((TableBlock) child, codeClick, math.formulas,
                        literalProtection.placeholders));
                continue;
            }
            MathNode display = standaloneFormula(child, math.formulas);
            if (display != null) {
                applyMath(prose, math.formulas, literalProtection.placeholders);
                flush(segments, prose);
                segments.add(Segment.formula(display));
                continue;
            }
            renderNode(child, prose, 0, codeClick);
        }
        applyMath(prose, math.formulas, literalProtection.placeholders);
        trimTrailingNewlines(prose);
        flush(segments, prose);
        mergeAdjacentProse(segments);
        return segments;
    }

    /**
     * The node behind {@code block} when that top-level block is nothing but one display formula.
     *
     * <p>Only a formula alone in its paragraph can leave the text. One sharing a line with prose is
     * sized by that line and would break the sentence in two if it left, and a formula the parser
     * does not support returns null here so it keeps the monospace source fallback it already has.
     */
    private static MathNode standaloneFormula(Node block, List<MathFormula> formulas) {
        if (!(block instanceof Paragraph)) {
            return null;
        }
        Node only = block.getFirstChild();
        if (only == null || only.getNext() != null || !(only instanceof Text)) {
            return null;
        }
        String literal = ((Text) only).getLiteral().trim();
        for (MathFormula formula : formulas) {
            if (formula.block && formula.placeholder.equals(literal)) {
                return MathParser.parse(formula.latex);
            }
        }
        return null;
    }

    private static void flush(List<Segment> segments, SpannableStringBuilder prose) {
        trimTrailingNewlines(prose);
        if (prose.length() > 0) {
            segments.add(Segment.prose(new SpannableStringBuilder(prose)));
            prose.clear();
        }
    }

    /** A table between two prose runs must not leave the runs as two boxes with a seam between. */
    private static void mergeAdjacentProse(List<Segment> segments) {
        for (int i = segments.size() - 1; i > 0; i--) {
            Segment previous = segments.get(i - 1);
            Segment current = segments.get(i);
            if (isBlock(previous) || isBlock(current)) {
                continue;
            }
            SpannableStringBuilder merged = new SpannableStringBuilder(previous.text);
            if (merged.length() > 0 && merged.charAt(merged.length() - 1) != '\n') {
                merged.append('\n');
            }
            merged.append(current.text);
            segments.set(i - 1, Segment.prose(merged));
            segments.remove(i);
        }
    }

    /** A segment that sits in its own box between prose runs, rather than inside a run. */
    private static boolean isBlock(Segment segment) {
        return segment.isTable || segment.isFormula;
    }

    /** Turns a table node into a segment: rows as data, so the screen can lay them out. */
    private static Segment readTable(TableBlock table, OnCodeBlockClick codeClick) {
        return readTable(table, codeClick, Collections.<MathFormula>emptyList(),
                Collections.<LiteralPlaceholder>emptyList());
    }

    private static Segment readTable(TableBlock table, OnCodeBlockClick codeClick,
            List<MathFormula> formulas, List<LiteralPlaceholder> literalPlaceholders) {
        List<List<CharSequence>> rows = new ArrayList<List<CharSequence>>();
        List<String> header = new ArrayList<String>();
        boolean hasHeader = false;
        for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            boolean isHead = section instanceof TableHead;
            if (!isHead && !(section instanceof TableBody)) {
                continue;
            }
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                if (!(row instanceof TableRow)) {
                    continue;
                }
                List<CharSequence> cells = new ArrayList<CharSequence>();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (!(cell instanceof TableCell)) {
                        continue;
                    }
                    SpannableStringBuilder cellText = new SpannableStringBuilder();
                    renderChildren(cell, cellText, 0, codeClick);
                    trimTrailingNewlines(cellText);
                    cellText = new SpannableStringBuilder(
                            cellText.toString().replace('\n', ' ').trim());
                    applyMath(cellText, formulas, literalPlaceholders);
                    cells.add(cellText);
                    if (isHead) {
                        header.add(cellText.toString());
                    }
                }
                rows.add(cells);
                if (isHead) {
                    hasHeader = true;
                }
            }
        }
        return Segment.table(rows, header, hasHeader);
    }

    /**
     * A table as plain text, one row per line, cells separated by two spaces. The fallback for {@link
     * #render}, which must return one string, and for a table nested inside another table's cell.
     */
    private static CharSequence flattenTable(Segment segment) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (List<CharSequence> row : segment.table) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    out.append("  ");
                }
                out.append(row.get(i));
            }
            out.append('\n');
        }
        return out;
    }

    /**
     * A display formula back into one line of text: the centred span the prose path used to draw it.
     * Only the flat {@link #render} needs this, since an answer body shows the formula as a view.
     */
    private static CharSequence flattenFormula(Segment segment) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        out.append('\ufffc');
        out.setSpan(MathSpan.create(segment.formula, true), 0, 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        applyBlockAlignment(out, 0, 1);
        return out;
    }

    private static void applyMath(SpannableStringBuilder out, List<MathFormula> formulas,
            List<LiteralPlaceholder> literalPlaceholders) {
        for (MathFormula formula : formulas) {
            String rendered = out.toString();
            int start = rendered.indexOf(formula.placeholder);
            if (start < 0) {
                continue;
            }
            int end = start + formula.placeholder.length();
            MathNode node = MathParser.parse(formula.latex);
            if (node == null) {
                out.replace(start, end, formula.latex);
                int fallbackEnd = start + formula.latex.length();
                if (fallbackEnd > start) {
                    out.setSpan(new TypefaceSpan("monospace"), start, fallbackEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(CODE_BACKGROUND), start, fallbackEnd,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                continue;
            }
            out.replace(start, end, "\ufffc");
            int formulaEnd = start + 1;
            out.setSpan(MathSpan.create(node, formula.block), start, formulaEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (formula.block) {
                applyBlockAlignment(out, start, formulaEnd);
            }
        }
        restoreLiteralPlaceholders(out, literalPlaceholders);
    }

    private static void applyBlockAlignment(SpannableStringBuilder out, int start, int end) {
        String rendered = out.toString();
        int paragraphStart = rendered.lastIndexOf('\n', start - 1) + 1;
        int paragraphBreak = rendered.indexOf('\n', end);
        int paragraphEnd = paragraphBreak < 0 ? rendered.length() : paragraphBreak + 1;
        if (paragraphEnd > paragraphStart) {
            out.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), paragraphStart,
                    paragraphEnd, Spannable.SPAN_PARAGRAPH);
        }
    }

    private static void restoreLiteralPlaceholders(SpannableStringBuilder out,
            List<LiteralPlaceholder> placeholders) {
        for (LiteralPlaceholder placeholder : placeholders) {
            int start = out.toString().indexOf(placeholder.marker);
            while (start >= 0) {
                int end = start + placeholder.marker.length();
                out.replace(start, end, placeholder.literal);
                start = out.toString().indexOf(placeholder.marker, start + placeholder.literal.length());
            }
        }
    }

    private static PlaceholderProtection protectLiteralPlaceholders(String markdown) {
        StringBuilder protectedMarkdown = new StringBuilder(markdown.length());
        List<LiteralPlaceholder> placeholders = new ArrayList<LiteralPlaceholder>();
        int index = 0;
        while (index < markdown.length()) {
            int end = scannerPlaceholderEnd(markdown, index);
            if (end < 0) {
                protectedMarkdown.append(markdown.charAt(index++));
                continue;
            }
            String literal = markdown.substring(index, end);
            int markerIndex = placeholders.size();
            String marker;
            do {
                marker = "\ue000TLLITERAL" + markerIndex++ + "\ue001";
            } while (markdown.contains(marker));
            protectedMarkdown.append(marker);
            placeholders.add(new LiteralPlaceholder(literal, marker));
            index = end;
        }
        return new PlaceholderProtection(protectedMarkdown.toString(), placeholders);
    }

    private static int scannerPlaceholderEnd(String text, int start) {
        if (!text.startsWith("TLMATH", start)) {
            return -1;
        }
        int index = start + "TLMATH".length();
        int digitsStart = index;
        while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') {
            index++;
        }
        if (index == digitsStart || !text.startsWith("TL", index)) {
            return -1;
        }
        return index + 2;
    }

    private static final class PlaceholderProtection {

        final String markdown;
        final List<LiteralPlaceholder> placeholders;

        PlaceholderProtection(String markdown, List<LiteralPlaceholder> placeholders) {
            this.markdown = markdown;
            this.placeholders = placeholders;
        }
    }

    private static final class LiteralPlaceholder {

        final String literal;
        final String marker;

        LiteralPlaceholder(String literal, String marker) {
            this.literal = literal;
            this.marker = marker;
        }
    }

    private static void renderChildren(Node parent, SpannableStringBuilder out, int depth,
            OnCodeBlockClick codeClick) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            renderNode(child, out, depth, codeClick);
        }
    }

    private static void renderNode(Node node, SpannableStringBuilder out, int depth,
            OnCodeBlockClick codeClick) {
        if (node instanceof Text) {
            out.append(((Text) node).getLiteral());
        } else if (node instanceof TableBlock) {
            // A table nested inside a table has no cell to land in; flattening is the honest answer.
            out.append(flattenTable(readTable((TableBlock) node, codeClick)));
            ensureBlockBreak(out);
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            out.append('\n');
        } else if (node instanceof Code) {
            int start = out.length();
            out.append(((Code) node).getLiteral());
            codeSpan(out, start);
        } else if (node instanceof FencedCodeBlock) {
            blockCode(out, ((FencedCodeBlock) node).getLiteral(), codeClick);
        } else if (node instanceof IndentedCodeBlock) {
            blockCode(out, ((IndentedCodeBlock) node).getLiteral(), codeClick);
        } else if (node instanceof Heading) {
            int start = out.length();
            renderChildren(node, out, depth, codeClick);
            int end = out.length();
            out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new RelativeSizeSpan(((Heading) node).getLevel() == 1 ? 1.3f : 1.15f),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ensureBlockBreak(out);
        } else if (node instanceof StrongEmphasis) {
            styledChildren(node, out, depth, new StyleSpan(Typeface.BOLD));
        } else if (node instanceof Emphasis) {
            styledChildren(node, out, depth, new StyleSpan(Typeface.ITALIC));
        } else if (node instanceof Link) {
            int start = out.length();
            renderChildren(node, out, depth, codeClick);
            int end = out.length();
            out.setSpan(new URLSpan(((Link) node).getDestination()), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ForegroundColorSpan(Color.rgb(24, 111, 84)), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (node instanceof BlockQuote) {
            int start = out.length();
            out.append("│ ");
            renderChildren(node, out, depth + 1, codeClick);
            int end = out.length();
            out.setSpan(new ForegroundColorSpan(QUOTE_COLOR), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ensureBlockBreak(out);
        } else if (node instanceof BulletList) {
            renderList((BulletList) node, out, depth, false, codeClick);
        } else if (node instanceof OrderedList) {
            renderList((OrderedList) node, out, depth, true, codeClick);
        } else if (node instanceof ListItem) {
            renderChildren(node, out, depth, codeClick);
            ensureBlockBreak(out);
        } else {
            renderChildren(node, out, depth, codeClick);
            if (node.getFirstChild() != null) {
                ensureBlockBreak(out);
            }
        }
    }

    private static void renderList(Node list, SpannableStringBuilder out, int depth,
            boolean ordered, OnCodeBlockClick codeClick) {
        int number = ordered ? ((OrderedList) list).getStartNumber() : 0;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            out.append(indent(depth));
            out.append(ordered ? number++ + ". " : "• ");
            renderNode(child, out, depth + 1, codeClick);
        }
        ensureBlockBreak(out);
    }

    private static void styledChildren(Node node, SpannableStringBuilder out, int depth,
            Object span) {
        int start = out.length();
        renderChildren(node, out, depth, null);
        if (out.length() > start) {
            out.setSpan(span, start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void codeSpan(SpannableStringBuilder out, int start) {
        out.setSpan(new TypefaceSpan("monospace"), start, out.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new BackgroundColorSpan(CODE_BACKGROUND), start, out.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void blockCode(SpannableStringBuilder out, String literal,
            OnCodeBlockClick codeClick) {
        int start = out.length();
        out.append(literal == null ? "" : literal);
        ensureBlockBreak(out);
        codeSpan(out, start);
        if (codeClick != null && out.length() > start) {
            final String code = literal == null ? "" : literal;
            out.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    codeClick.onCodeBlockClick(code);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    // The block keeps its own look; the tap is affordance enough for a copy.
                }
            }, start, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static String indent(int depth) {
        StringBuilder indent = new StringBuilder(depth * 2);
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }
        return indent.toString();
    }

    private static void ensureBlockBreak(SpannableStringBuilder out) {
        if (out.length() == 0 || out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        if (out.length() < 2 || out.charAt(out.length() - 2) != '\n') {
            out.append('\n');
        }
    }

    private static void trimTrailingNewlines(SpannableStringBuilder out) {
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }
    }
}
