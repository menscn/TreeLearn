package com.treelearn.ai.prompt;

/**
 * Structural guard for the single JSON input of the derived purposes (prompt design V1.0 §6).
 *
 * <p>A derived call takes one JSON object built from structured input; free text would let a caller
 * put prose into the data channel, which is exactly the shape the design forbids. This guard makes
 * "the payload is structured data, not a second prompt" a mechanical property of the request.
 *
 * <p><strong>Boundary.</strong> This is a shape check, not a JSON parser and not a schema validator:
 * it verifies one balanced object with escape-aware strings. Which keys a purpose accepts, unknown
 * field rejection, and parsing the model's output all belong to the derived use cases and the
 * gateway (technical design §9.3, §10), not to the assembler.
 */
public final class JsonObjectText {

    private JsonObjectText() {
    }

    /** @return true when {@code text} is exactly one balanced JSON object. */
    public static boolean isObject(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '{'
                || trimmed.charAt(trimmed.length() - 1) != '}') {
            return false;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                depth++;
            } else if (current == '}' || current == ']') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return !inString && depth == 0;
    }
}
