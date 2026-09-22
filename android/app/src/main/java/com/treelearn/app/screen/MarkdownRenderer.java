package com.treelearn.app.screen;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

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
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.parser.Parser;

/** Converts CommonMark into selectable Android spans without exposing Markdown syntax to the user. */
public final class MarkdownRenderer {

    private static final int CODE_BACKGROUND = Color.rgb(242, 240, 232);
    private static final int QUOTE_COLOR = Color.rgb(91, 109, 99);

    private MarkdownRenderer() {
    }

    public static CharSequence render(String markdown) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (markdown == null || markdown.isEmpty()) {
            return out;
        }
        Node document = Parser.builder().build().parse(markdown);
        renderChildren(document, out, 0);
        trimTrailingNewlines(out);
        return out;
    }

    private static void renderChildren(Node parent, SpannableStringBuilder out, int depth) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            renderNode(child, out, depth);
        }
    }

    private static void renderNode(Node node, SpannableStringBuilder out, int depth) {
        if (node instanceof Text) {
            out.append(((Text) node).getLiteral());
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            out.append('\n');
        } else if (node instanceof Code) {
            int start = out.length();
            out.append(((Code) node).getLiteral());
            codeSpan(out, start);
        } else if (node instanceof FencedCodeBlock) {
            blockCode(out, ((FencedCodeBlock) node).getLiteral());
        } else if (node instanceof IndentedCodeBlock) {
            blockCode(out, ((IndentedCodeBlock) node).getLiteral());
        } else if (node instanceof Heading) {
            int start = out.length();
            renderChildren(node, out, depth);
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
            renderChildren(node, out, depth);
            int end = out.length();
            out.setSpan(new URLSpan(((Link) node).getDestination()), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ForegroundColorSpan(Color.rgb(24, 111, 84)), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (node instanceof BlockQuote) {
            int start = out.length();
            out.append("│ ");
            renderChildren(node, out, depth + 1);
            int end = out.length();
            out.setSpan(new ForegroundColorSpan(QUOTE_COLOR), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ensureBlockBreak(out);
        } else if (node instanceof BulletList) {
            renderList((BulletList) node, out, depth, false);
        } else if (node instanceof OrderedList) {
            renderList((OrderedList) node, out, depth, true);
        } else if (node instanceof ListItem) {
            renderChildren(node, out, depth);
            ensureBlockBreak(out);
        } else {
            renderChildren(node, out, depth);
            if (node.getFirstChild() != null) {
                ensureBlockBreak(out);
            }
        }
    }

    private static void renderList(Node list, SpannableStringBuilder out, int depth,
            boolean ordered) {
        int number = ordered ? ((OrderedList) list).getStartNumber() : 0;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            out.append(indent(depth));
            out.append(ordered ? number++ + ". " : "• ");
            renderNode(child, out, depth + 1);
        }
        ensureBlockBreak(out);
    }

    private static void styledChildren(Node node, SpannableStringBuilder out, int depth,
            Object span) {
        int start = out.length();
        renderChildren(node, out, depth);
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

    private static void blockCode(SpannableStringBuilder out, String literal) {
        int start = out.length();
        out.append(literal == null ? "" : literal);
        ensureBlockBreak(out);
        codeSpan(out, start);
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
