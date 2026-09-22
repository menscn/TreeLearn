package com.treelearn.app.artifact;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.JsonText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The strict output codec of the six derived purposes (technical design 10; prompt design 6).
 *
 * <p>One structural reader does the whole job, and every deviation is refused instead of repaired:
 * a code fence, an explanation after the object, two concatenated objects, a bare string, an array
 * root, a trailing comma, an unquoted key, a truncated object or an unknown field each produce
 * {@link ArtifactOutcome#PAYLOAD_INVALID} and keep the stored artifact. "Trim the first braces out of
 * the text" is the failure mode the design names explicitly, so nothing here searches, trims or
 * repairs — it parses or it refuses.
 *
 * <p>Each purpose accepts exactly its own envelope from prompt design 6, and the accepted field set
 * is compared for equality, so extra keys are rejected rather than ignored. The parsed fields are
 * then re-encoded by {@link ArtifactPayload#canonicalJson()} in the design's order; the provider's
 * spelling never reaches the database.
 *
 * <p>No refusal path carries any part of the provider's text: a failure message says which shape was
 * wrong, never what it read (§15).
 */
public final class ArtifactPayloadCodec {

    private ArtifactPayloadCodec() {
    }

    /**
     * @param itemCeiling the caller's {@code max_items} for a recommendation array; must be >= 1,
     *        because "at most zero items" is a caller bug rather than an empty list
     * @return the payload, or the classified refusal. Never a partial payload.
     */
    public static DerivedResult parse(ArtifactKind kind, String text, int itemCeiling) {
        Guard.object(kind, "kind");
        Guard.atLeast(itemCeiling, 1, "itemCeiling");
        if (text == null) {
            return DerivedResult.refused(ArtifactOutcome.PAYLOAD_INVALID);
        }
        Object root;
        try {
            root = JsonText.read(text);
        } catch (JsonText.Syntax notJson) {
            return DerivedResult.refused(ArtifactOutcome.PAYLOAD_INVALID);
        }
        if (!(root instanceof Map)) {
            return DerivedResult.refused(ArtifactOutcome.PAYLOAD_INVALID);
        }
        try {
            return shapeOf(kind, (Map<?, ?>) root, itemCeiling);
        } catch (Shape refused) {
            return DerivedResult.refused(ArtifactOutcome.PAYLOAD_INVALID);
        }
    }

    private static DerivedResult shapeOf(ArtifactKind kind, Map<?, ?> fields, int itemCeiling) {
        return switch (kind) {
            case ROOT_TITLE, BRANCH_TITLE -> {
                only(fields, "title");
                yield DerivedResult.usable(ArtifactPayload.title(kind, nonBlank(fields.get("title"))));
            }
            case BRANCH_SUMMARY -> {
                only(fields, "summary", "key_points");
                yield DerivedResult.usable(ArtifactPayload.summary(kind, text(fields.get("summary")),
                        nonBlankStrings(fields.get("key_points"))));
            }
            case ANSWER_RECOMMENDATIONS -> DerivedResult.usable(
                    ArtifactPayload.items(kind, items(fields, "question", itemCeiling)));
            case HOME_RECOMMENDATIONS -> DerivedResult.usable(
                    ArtifactPayload.items(kind, items(fields, "topic", itemCeiling)));
            case CONTEXT_COMPRESSION -> {
                only(fields, "summary");
                String summary = text(fields.get("summary"));
                if (summary.trim().isEmpty()) {
                    yield DerivedResult.refused(ArtifactOutcome.SUMMARY_EMPTY);
                }
                yield DerivedResult.usable(
                        ArtifactPayload.summary(kind, summary, List.<String>of()));
            }
        };
    }

    /** Raised for a malformed envelope; {@link #parse} turns it into a refusal. Carries no text. */
    private static final class Shape extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    /** The accepted field set, compared for equality: an unknown field is refused, not ignored. */
    private static void only(Map<?, ?> fields, String... names) {
        if (!fields.keySet().equals(Set.of(names))) {
            throw new Shape();
        }
    }

    /** A required, non-blank string field. */
    private static String nonBlank(Object value) {
        if (!(value instanceof String text) || text.trim().isEmpty()) {
            throw new Shape();
        }
        return text;
    }

    /** A string field an empty value is allowed for: a summary may legitimately say "nothing yet". */
    private static String text(Object value) {
        if (!(value instanceof String text)) {
            throw new Shape();
        }
        return text;
    }

    private static List<String> nonBlankStrings(Object value) {
        if (!(value instanceof List<?> values)) {
            throw new Shape();
        }
        List<String> texts = new ArrayList<String>(values.size());
        for (Object element : values) {
            texts.add(nonBlank(element));
        }
        return texts;
    }

    /** {@code {"items":[{"<key>":"..."}]}} — the shape both recommendation kinds share. */
    private static List<String> items(Map<?, ?> fields, String key, int itemCeiling) {
        only(fields, "items");
        if (!(fields.get("items") instanceof List<?> values) || values.size() > itemCeiling) {
            throw new Shape();
        }
        List<String> texts = new ArrayList<String>(values.size());
        for (Object element : values) {
            if (!(element instanceof Map<?, ?> one)) {
                throw new Shape();
            }
            only(one, key);
            texts.add(nonBlank(one.get(key)));
        }
        return texts;
    }
}