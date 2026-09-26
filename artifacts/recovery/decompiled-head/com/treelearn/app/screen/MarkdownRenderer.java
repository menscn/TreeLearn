/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.graphics.Color
 *  android.text.SpannableStringBuilder
 *  android.text.TextPaint
 *  android.text.style.BackgroundColorSpan
 *  android.text.style.ClickableSpan
 *  android.text.style.ForegroundColorSpan
 *  android.text.style.RelativeSizeSpan
 *  android.text.style.StyleSpan
 *  android.text.style.TypefaceSpan
 *  android.text.style.URLSpan
 *  android.view.View
 *  org.commonmark.node.BlockQuote
 *  org.commonmark.node.BulletList
 *  org.commonmark.node.Code
 *  org.commonmark.node.Emphasis
 *  org.commonmark.node.FencedCodeBlock
 *  org.commonmark.node.HardLineBreak
 *  org.commonmark.node.Heading
 *  org.commonmark.node.IndentedCodeBlock
 *  org.commonmark.node.Link
 *  org.commonmark.node.ListItem
 *  org.commonmark.node.Node
 *  org.commonmark.node.OrderedList
 *  org.commonmark.node.SoftLineBreak
 *  org.commonmark.node.StrongEmphasis
 *  org.commonmark.node.Text
 *  org.commonmark.parser.Parser
 */
package com.treelearn.app.screen;

import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
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
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

public final class MarkdownRenderer {
    private static final int CODE_BACKGROUND = Color.rgb((int)242, (int)240, (int)232);
    private static final int QUOTE_COLOR = Color.rgb((int)91, (int)109, (int)99);

    private MarkdownRenderer() {
    }

    public static CharSequence render(String markdown) {
        return MarkdownRenderer.render(markdown, null);
    }

    public static CharSequence render(String markdown, OnCodeBlockClick codeClick) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (markdown == null || markdown.isEmpty()) {
            return out;
        }
        Node document = Parser.builder().build().parse(markdown);
        MarkdownRenderer.renderChildren(document, out, 0, codeClick);
        MarkdownRenderer.trimTrailingNewlines(out);
        return out;
    }

    private static void renderChildren(Node parent, SpannableStringBuilder out, int depth, OnCodeBlockClick codeClick) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            MarkdownRenderer.renderNode(child, out, depth, codeClick);
        }
    }

    private static void renderNode(Node node, SpannableStringBuilder out, int depth, OnCodeBlockClick codeClick) {
        if (node instanceof Text) {
            out.append((CharSequence)((Text)node).getLiteral());
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            out.append('\n');
        } else if (node instanceof Code) {
            int start = out.length();
            out.append((CharSequence)((Code)node).getLiteral());
            MarkdownRenderer.codeSpan(out, start);
        } else if (node instanceof FencedCodeBlock) {
            MarkdownRenderer.blockCode(out, ((FencedCodeBlock)node).getLiteral(), codeClick);
        } else if (node instanceof IndentedCodeBlock) {
            MarkdownRenderer.blockCode(out, ((IndentedCodeBlock)node).getLiteral(), codeClick);
        } else if (node instanceof Heading) {
            int start = out.length();
            MarkdownRenderer.renderChildren(node, out, depth, codeClick);
            int end = out.length();
            out.setSpan((Object)new StyleSpan(1), start, end, 33);
            out.setSpan((Object)new RelativeSizeSpan(((Heading)node).getLevel() == 1 ? 1.3f : 1.15f), start, end, 33);
            MarkdownRenderer.ensureBlockBreak(out);
        } else if (node instanceof StrongEmphasis) {
            MarkdownRenderer.styledChildren(node, out, depth, new StyleSpan(1));
        } else if (node instanceof Emphasis) {
            MarkdownRenderer.styledChildren(node, out, depth, new StyleSpan(2));
        } else if (node instanceof Link) {
            int start = out.length();
            MarkdownRenderer.renderChildren(node, out, depth, codeClick);
            int end = out.length();
            out.setSpan((Object)new URLSpan(((Link)node).getDestination()), start, end, 33);
            out.setSpan((Object)new ForegroundColorSpan(Color.rgb((int)24, (int)111, (int)84)), start, end, 33);
        } else if (node instanceof BlockQuote) {
            int start = out.length();
            out.append((CharSequence)"\u2502 ");
            MarkdownRenderer.renderChildren(node, out, depth + 1, codeClick);
            int end = out.length();
            out.setSpan((Object)new ForegroundColorSpan(QUOTE_COLOR), start, end, 33);
            MarkdownRenderer.ensureBlockBreak(out);
        } else if (node instanceof BulletList) {
            MarkdownRenderer.renderList((Node)((BulletList)node), out, depth, false, codeClick);
        } else if (node instanceof OrderedList) {
            MarkdownRenderer.renderList((Node)((OrderedList)node), out, depth, true, codeClick);
        } else if (node instanceof ListItem) {
            MarkdownRenderer.renderChildren(node, out, depth, codeClick);
            MarkdownRenderer.ensureBlockBreak(out);
        } else {
            MarkdownRenderer.renderChildren(node, out, depth, codeClick);
            if (node.getFirstChild() != null) {
                MarkdownRenderer.ensureBlockBreak(out);
            }
        }
    }

    private static void renderList(Node list, SpannableStringBuilder out, int depth, boolean ordered, OnCodeBlockClick codeClick) {
        int number = ordered ? ((OrderedList)list).getStartNumber() : 0;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            out.append((CharSequence)MarkdownRenderer.indent(depth));
            out.append((CharSequence)(ordered ? number++ + ". " : "\u2022 "));
            MarkdownRenderer.renderNode(child, out, depth + 1, codeClick);
        }
        MarkdownRenderer.ensureBlockBreak(out);
    }

    private static void styledChildren(Node node, SpannableStringBuilder out, int depth, Object span) {
        int start = out.length();
        MarkdownRenderer.renderChildren(node, out, depth, null);
        if (out.length() > start) {
            out.setSpan(span, start, out.length(), 33);
        }
    }

    private static void codeSpan(SpannableStringBuilder out, int start) {
        out.setSpan((Object)new TypefaceSpan("monospace"), start, out.length(), 33);
        out.setSpan((Object)new BackgroundColorSpan(CODE_BACKGROUND), start, out.length(), 33);
    }

    private static void blockCode(SpannableStringBuilder out, String literal, final OnCodeBlockClick codeClick) {
        int start = out.length();
        out.append((CharSequence)(literal == null ? "" : literal));
        MarkdownRenderer.ensureBlockBreak(out);
        MarkdownRenderer.codeSpan(out, start);
        if (codeClick != null && out.length() > start) {
            final String code = literal == null ? "" : literal;
            out.setSpan((Object)new ClickableSpan(){

                public void onClick(View widget) {
                    codeClick.onCodeBlockClick(code);
                }

                public void updateDrawState(TextPaint ds) {
                }
            }, start, out.length(), 33);
        }
    }

    private static String indent(int depth) {
        StringBuilder indent = new StringBuilder(depth * 2);
        for (int i = 0; i < depth; ++i) {
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

    public static interface OnCodeBlockClick {
        public void onCodeBlockClick(String var1);
    }
}

