package com.treelearn.app.ui.math;

import java.util.ArrayList;
import java.util.List;

/** Recursive descent parser for the supported LaTeX subset; unsupported input yields null (never throws). */
public final class MathParser {

    private static final int MAX_DEPTH = 24;
    private static final String IGNORED = " quad qquad displaystyle limits nolimits space ";
    /**
     * A backslash before one of these means the character itself, not the structure it would
     * otherwise start: `\{` is a brace in the formula, not the opening of a group, and `\_` is a
     * low line, not a subscript.
     */
    private static final String ESCAPED_LITERALS = "{}%#_$&";
    private static final String ACCENTS = " vec bar hat dot tilde ddot check breve acute grave ";
    private static final String FUNCTIONS =
            " lim log ln lg sin cos tan cot sec csc max min sup inf det gcd exp arg deg dim hom ker ";
    private static final String RELATIONS = "≈≠≡∼≃≤≥≪≫∝∈∉∋⊂⊃⊆⊇→←⇒⇐↔⇔↦∥⊥";
    private static final String OPERATORS = "±∓×·÷∗∪∩∖∘⊕⊗⋆";
    private static final String[] BIG_GLYPHS = {"sum", "∑", "prod", "∏", "int", "∫", "iint", "∬",
            "oint", "∮", "bigcup", "⋃", "bigcap", "⋂"};

    private final String src;
    private final int len;
    private int pos;
    private int depth;
    private String expectedDelimiterCloser;

    private MathParser(String src) {
        this.src = src;
        this.len = src.length();
    }

    /** Parses latex into a node tree, or returns null when the input is not supported. */
    public static MathNode parse(String latex) {
        if (latex == null) return null;
        try {
            MathParser parser = new MathParser(latex);
            List<MathNode> items = parser.parseSequence('\0');
            return items == null || parser.pos != parser.len ? null : collapse(items);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<MathNode> parseSequence(char end) {
        List<MathNode> items = new ArrayList<MathNode>();
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == end || (c == '}' && end == '}')) return items;
            if (c == '}' || c == '&') return null;
            if (" \t\n\r".indexOf(c) >= 0) {
                pos++;
                continue;
            }
            if (expectedDelimiterCloser != null && expectedDelimiterCloser.equals(peekName())) return items;
            if (skipIgnored()) continue;
            MathNode item = parseItem();
            if (item == null) return null;
            MathNode[] scripts = readScripts();
            if (scripts == null) return null;
            items.add(scripts[0] == null && scripts[1] == null
                    ? item : MathNode.script(item, scripts[0], scripts[1]));
        }
        return items;
    }

    private MathNode parseItem() {
        char c = src.charAt(pos);
        if (c == '{') return parseGroup();
        if (c == '\\') return parseCommand();
        if (c == '}' || c == '^' || c == '_' || c == '&') return null;
        pos++;
        return MathNode.atom(String.valueOf(c), atomKindOf(c));
    }

    private MathNode parseCommand() {
        String name = nextName();
        if (name.isEmpty()) return parseEscapedLiteral();
        if ("frac".equals(name)) {
            MathNode numerator = parseArg();
            MathNode denominator = parseArg();
            return numerator == null || denominator == null
                    ? null : MathNode.frac(numerator, denominator);
        }
        if ("sqrt".equals(name)) return parseSqrt();
        if ("text".equals(name)) {
            String raw = readGroupRaw();
            return raw == null ? null : MathNode.atom(raw, MathNode.AtomKind.TEXT);
        }
        if ("left".equals(name)) return parseDelimited("right");
        String rightCommand = rightCommandFor(name);
        if (rightCommand != null) return parseDelimited(rightCommand);
        if ("right".equals(name) || "begin".equals(name) || "end".equals(name)) return null;
        if (contains(ACCENTS, name)) {
            MathNode base = parseArg();
            return base == null ? null : MathNode.accent(name, base);
        }
        String glyph = bigGlyph(name);
        if (glyph == null && contains(FUNCTIONS, name)) glyph = name;
        if (glyph == null) {
            String symbol = MathSymbols.lookup(name);
            return symbol == null ? null : MathNode.atom(symbol, symbolKind(symbol));
        }
        MathNode[] scripts = readScripts();
        return scripts == null ? null : MathNode.bigOp(glyph, scripts[1], scripts[0]);
    }

    /** Reads the character a backslash escapes, or refuses when that pairing is not one we draw. */
    private MathNode parseEscapedLiteral() {
        if (pos >= len) return null;
        char c = src.charAt(pos);
        if (ESCAPED_LITERALS.indexOf(c) < 0) return null;
        pos++;
        return MathNode.atom(String.valueOf(c), atomKindOf(c));
    }

    private MathNode parseSqrt() {
        MathNode index = null;
        skipSpaces();
        if (pos < len && src.charAt(pos) == '[') {
            pos++;
            List<MathNode> items = parseSequence(']');
            if (items == null || pos >= len || src.charAt(pos) != ']') return null;
            pos++;
            index = collapse(items);
            if (index == null) return null;
        }
        MathNode radicand = parseArg();
        return radicand == null ? null : MathNode.sqrt(radicand, index);
    }

    private MathNode[] readScripts() {
        MathNode sup = null;
        MathNode sub = null;
        while (pos < len && (src.charAt(pos) == '^' || src.charAt(pos) == '_')) {
            boolean superscript = src.charAt(pos) == '^';
            pos++;
            MathNode arg = parseArg();
            if (arg == null) return null;
            if (superscript) {
                if (sup != null) return null;
                sup = arg;
            } else {
                if (sub != null) return null;
                sub = arg;
            }
        }
        return new MathNode[] {sup, sub};
    }

    private MathNode parseArg() {
        skipSpaces();
        if (pos >= len) return null;
        char c = src.charAt(pos);
        if (c == '{') return parseGroup();
        if (c == '\\') return skipIgnored() ? parseArg() : parseCommand();
        if (c == '}' || c == '^' || c == '_' || c == '&' || c == ']') return null;
        pos++;
        return MathNode.atom(String.valueOf(c), atomKindOf(c));
    }

    private MathNode parseGroup() {
        pos++;
        if (++depth > MAX_DEPTH) return null;
        List<MathNode> items = parseSequence('}');
        depth--;
        if (items == null || pos >= len || src.charAt(pos) != '}') return null;
        pos++;
        return collapse(items);
    }

    private MathNode parseDelimited(String rightCommand) {
        char left = readDelimiter();
        if (left == '\0') return null;
        String outer = expectedDelimiterCloser;
        expectedDelimiterCloser = rightCommand;
        List<MathNode> items = parseSequence('}');
        expectedDelimiterCloser = outer;
        if (items == null || !rightCommand.equals(peekName())) return null;
        nextName();
        char right = readDelimiter();
        MathNode content = collapse(items);
        return right == '\0' || content == null ? null : MathNode.delimited(left, content, right);
    }

    private static String rightCommandFor(String leftCommand) {
        if ("bigl".equals(leftCommand)) return "bigr";
        if ("Bigl".equals(leftCommand)) return "Bigr";
        if ("biggl".equals(leftCommand)) return "biggr";
        if ("Biggl".equals(leftCommand)) return "Biggr";
        return null;
    }

    private char readDelimiter() {
        skipSpaces();
        if (pos >= len) return '\0';
        char c = src.charAt(pos);
        if ("()[]|.".indexOf(c) >= 0) {
            pos++;
            return c;
        }
        if (c != '\\' || pos + 1 >= len) return '\0';
        char next = src.charAt(pos + 1);
        if (next == '{' || next == '}') {
            pos += 2;
            return next;
        }
        String name = readLetters(pos + 1);
        if ("langle".equals(name) || "rangle".equals(name)) {
            pos += 1 + name.length();
            return "langle".equals(name) ? '⟨' : '⟩';
        }
        if ("vert".equals(name) || "mid".equals(name) || "Vert".equals(name)) {
            pos += 1 + name.length();
            return '|';
        }
        return '\0';
    }

    /** Reads a brace group verbatim so that \text{...} keeps Chinese and backslashes untouched. */
    private String readGroupRaw() {
        skipSpaces();
        if (pos >= len || src.charAt(pos) != '{') return null;
        int start = ++pos;
        int level = 1;
        while (pos < len) {
            char c = src.charAt(pos++);
            if (c == '{') level++;
            else if (c == '}' && --level == 0) return src.substring(start, pos - 1);
        }
        return null;
    }

    private boolean skipIgnored() {
        if (pos >= len || src.charAt(pos) != '\\') return false;
        String name = readLetters(pos + 1);
        if (name.isEmpty()) {
            char next = pos + 1 < len ? src.charAt(pos + 1) : '\0';
            if (next == ',' || next == ';' || next == '!' || next == ':' || next == ' ') {
                pos += 2;
                return true;
            }
            return false;
        }
        if (contains(IGNORED, name)) {
            pos += 1 + name.length();
            return true;
        }
        return false;
    }

    private void skipSpaces() {
        while (pos < len && " \t\n\r".indexOf(src.charAt(pos)) >= 0) pos++;
    }

    private String nextName() {
        pos++;
        String name = readLetters(pos);
        pos += name.length();
        return name;
    }

    private String readLetters(int from) {
        int end = from;
        while (end < len && isLetter(src.charAt(end))) end++;
        return src.substring(from, end);
    }

    private String peekName() {
        return pos < len && src.charAt(pos) == '\\' ? readLetters(pos + 1) : null;
    }

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static String bigGlyph(String name) {
        for (int i = 0; i < BIG_GLYPHS.length; i += 2) {
            if (BIG_GLYPHS[i].equals(name)) return BIG_GLYPHS[i + 1];
        }
        return null;
    }

    private static boolean contains(String spaced, String name) {
        return spaced.indexOf(" " + name + " ") >= 0;
    }

    private static MathNode collapse(List<MathNode> items) {
        if (items.isEmpty()) return null;
        return items.size() == 1 ? items.get(0) : MathNode.row(items);
    }

    private static MathNode.AtomKind atomKindOf(char c) {
        if (c >= '0' && c <= '9') return MathNode.AtomKind.NUMBER;
        if ("+-*/".indexOf(c) >= 0) return MathNode.AtomKind.OPERATOR;
        if ("=<>".indexOf(c) >= 0) return MathNode.AtomKind.RELATION;
        if (",;:!".indexOf(c) >= 0) return MathNode.AtomKind.PUNCT;
        return MathNode.AtomKind.ORDINARY;
    }

    private static MathNode.AtomKind symbolKind(String glyph) {
        if (RELATIONS.indexOf(glyph) >= 0) return MathNode.AtomKind.RELATION;
        if (OPERATORS.indexOf(glyph) >= 0) return MathNode.AtomKind.OPERATOR;
        return MathNode.AtomKind.ORDINARY;
    }
}
