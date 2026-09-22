package com.treelearn.core.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single structural JSON reader and writer of {@code :core} (technical design 5.3: a stored
 * payload is read through a structured JSON parser, never assembled or split as raw text).
 *
 * <p>It is deliberately small, and it exists for one reason: this project adds no third-party
 * dependency, and Android's {@code org.json} is not on the pure-JVM {@code :core} test path, so a
 * codec written against it could not be proven in the layer that owns the schema. Sharing one reader
 * is what stops each payload from growing its own split-and-substring parser, which is how a JSON
 * document quietly turns into a string format nobody can migrate.
 *
 * <p><strong>Accepted subset.</strong> The root value and every nested value may be an object, an
 * array, a string or an integral number. Booleans and {@code null} are refused instead of coerced: a
 * reader that cannot represent a value it met would have to invent one. Numbers are accepted because
 * a structured provider envelope carries counts (the derived prompt inputs declare a positive
 * {@code max_items}), and the grammar is narrowed to exactly what {@link #write} emits — an optional
 * minus sign followed by {@code 0} or a non-zero digit run. Fractional, exponent and zero-padded
 * forms are refused, so reading a number never has to choose between {@code Long} and {@code Double}.
 *
 * <p><strong>Why reading is strict.</strong> Duplicate keys, raw control characters inside a string,
 * unknown escapes and trailing text after the root value are all refused. A payload is verified
 * against its stored digest, so "the same document, spelled another way" is a real hazard: text that
 * merely looks like JSON must fail here rather than be interpreted.
 *
 * <p>No error carries any part of the input text. A caller may report which shape was wrong, never
 * what it was reading.
 */
public final class JsonText {

    /** Raised when the text is outside the subset above. Deliberately carries no input text. */
    public static final class Syntax extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Syntax(String detail) {
            super(detail);
        }
    }

    /** The characters a string may not carry raw, and the escape letters that stand for them. */
    private static final String RAW = "\"\\\b\f\n\r\t";
    private static final String MARK = "\"\\bfnrt";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String text;
    private int at;

    private JsonText(String text) {
        this.text = text;
    }

    /**
     * Reads exactly one JSON value.
     *
     * @return a {@link LinkedHashMap} (insertion-ordered), an {@link ArrayList} or a {@link String}
     * @throws IllegalArgumentException when {@code text} is null — an omitted argument is a caller bug
     * @throws Syntax when the text is not one well-formed value of the accepted subset
     */
    public static Object read(String text) {
        if (text == null) {
            throw new IllegalArgumentException("json text must not be null");
        }
        JsonText reader = new JsonText(text);
        Object value = reader.value();
        reader.space();
        if (reader.at != text.length()) {
            throw new Syntax("unexpected trailing characters after the root value");
        }
        return value;
    }

    /**
     * Writes {@code value} canonically: strings are escaped the same way every time and a map is
     * written in its own iteration order. A {@link LinkedHashMap} therefore yields byte-identical
     * output for equal content, which is what lets a digest cover the payload rather than a hash order.
     *
     * @throws IllegalArgumentException for null, for a control character, or for a value outside the
     *         accepted subset
     */
    public static void write(Object value, StringBuilder out) {
        if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof List) {
            writeArray((List<?>) value, out);
        } else if (value instanceof Map) {
            writeObject((Map<?, ?>) value, out);
        } else if (value instanceof Integer || value instanceof Long) {
            out.append(((Number) value).longValue());
        } else {
            throw new IllegalArgumentException("unsupported JSON value: "
                    + (value == null ? "null" : value.getClass().getName()));
        }
    }

    private static void writeArray(List<?> values, StringBuilder out) {
        out.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            write(values.get(index), out);
        }
        out.append(']');
    }

    private static void writeObject(Map<?, ?> fields, StringBuilder out) {
        out.append('{');
        String separator = "";
        for (Map.Entry<?, ?> field : fields.entrySet()) {
            out.append(separator);
            separator = ",";
            writeString((String) field.getKey(), out);
            out.append(':');
            write(field.getValue(), out);
        }
        out.append('}');
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            int escape = RAW.indexOf(current);
            if (escape >= 0) {
                out.append('\\').append(MARK.charAt(escape));
            } else if (current < 0x20) {
                out.append("\\u").append(HEX[(current >> 12) & 0xF]).append(HEX[(current >> 8) & 0xF])
                        .append(HEX[(current >> 4) & 0xF]).append(HEX[current & 0xF]);
            } else {
                out.append(current);
            }
        }
        out.append('"');
    }

    private Object value() {
        space();
        if (at >= text.length()) {
            throw new Syntax("the text ends before a value was read");
        }
        char current = text.charAt(at);
        if (current == '{') {
            at++;
            return map();
        }
        if (current == '[') {
            at++;
            return list();
        }
        if (current == '"') {
            return string();
        }
        if (current == '-' || (current >= '0' && current <= '9')) {
            return number();
        }
        throw new Syntax("only objects, arrays, strings and integral numbers are accepted; found "
                + "character code " + (int) current);
    }

    /**
     * Reads the integral grammar {@link #write} emits: an optional minus sign followed by {@code 0}
     * or a non-zero digit run. Nothing else is a number here — {@code 007}, {@code 1.5}, {@code 1e3}
     * and a lone minus would each make "which Java type is this" a guess.
     */
    private Long number() {
        int start = at;
        if (take('-')) {
            if (at >= text.length() || !isDigit(text.charAt(at))) {
                throw new Syntax("a minus sign must be followed by a digit");
            }
        }
        if (take('0')) {
            if (at < text.length() && isDigit(text.charAt(at))) {
                throw new Syntax("a number must not carry a leading zero");
            }
        } else {
            while (at < text.length() && isDigit(text.charAt(at))) {
                at++;
            }
        }
        String literal = text.substring(start, at);
        try {
            return Long.valueOf(literal);
        } catch (NumberFormatException tooLarge) {
            throw new Syntax("the number is outside the signed 64 bit range");
        }
    }

    private static boolean isDigit(char current) {
        return current >= '0' && current <= '9';
    }

    private Map<String, Object> map() {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        if (take('}')) {
            return fields;
        }
        while (true) {
            space();
            String key = string();
            space();
            if (!take(':')) {
                throw new Syntax("expected ':' after an object key");
            }
            space();
            if (fields.put(key, value()) != null) {
                throw new Syntax("the object repeats one key");
            }
            space();
            if (take('}')) {
                return fields;
            }
            if (!take(',')) {
                throw new Syntax("expected ',' or '}' after an object member");
            }
        }
    }

    private List<Object> list() {
        List<Object> values = new ArrayList<Object>();
        if (take(']')) {
            return values;
        }
        while (true) {
            values.add(value());
            space();
            if (take(']')) {
                return values;
            }
            if (!take(',')) {
                throw new Syntax("expected ',' or ']' after an array element");
            }
        }
    }

    private String string() {
        if (!take('"')) {
            throw new Syntax("expected a string");
        }
        StringBuilder value = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw new Syntax("the string is not terminated");
            }
            char current = text.charAt(at++);
            if (current == '"') {
                return value.toString();
            }
            if (current < 0x20) {
                throw new Syntax("a string carries a raw control character");
            }
            if (current != '\\') {
                value.append(current);
                continue;
            }
            if (at >= text.length()) {
                throw new Syntax("the string ends inside an escape");
            }
            char marker = text.charAt(at++);
            int escape = MARK.indexOf(marker);
            if (escape >= 0) {
                value.append(RAW.charAt(escape));
            } else if (marker == 'u') {
                value.append((char) hex());
            } else {
                throw new Syntax("unsupported escape character code " + (int) marker);
            }
        }
    }

    private int hex() {
        if (at + 4 > text.length()) {
            throw new Syntax("the \\u escape is truncated");
        }
        int code = 0;
        for (int index = 0; index < 4; index++) {
            int digit = Character.digit(text.charAt(at + index), 16);
            if (digit < 0) {
                throw new Syntax("the \\u escape carries a non-hexadecimal digit");
            }
            code = (code << 4) | digit;
        }
        at += 4;
        return code;
    }

    /** Consumes {@code wanted} and reports whether it was there, so a caller needs one branch. */
    private boolean take(char wanted) {
        if (at < text.length() && text.charAt(at) == wanted) {
            at++;
            return true;
        }
        return false;
    }

    private void space() {
        while (at < text.length()) {
            char current = text.charAt(at);
            if (current == ' ' || current == '\t' || current == '\n' || current == '\r') {
                at++;
            } else {
                return;
            }
        }
    }
}