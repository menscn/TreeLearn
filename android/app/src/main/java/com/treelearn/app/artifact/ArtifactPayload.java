package com.treelearn.app.artifact;

import com.treelearn.core.domain.JsonText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed, validated derived payload (technical design 10; prompt design 6).
 *
 * <p>Only {@link ArtifactPayloadCodec} constructs one, and it constructs it from the fields it
 * validated — never from the provider's text. That is what {@link #canonicalJson()} stores: the
 * envelope re-encoded in the design's own field order, so an output that merely <em>looked</em> like
 * JSON cannot smuggle extra keys, duplicate keys or decoration into the database, and two equal
 * payloads store byte-identically.
 *
 * <p>Only the fields of this payload's kind are set. A title carries {@link #title}; a summary
 * carries {@link #summary} and {@link #keyPoints}; a recommendation carries {@link #items}. Nothing
 * here holds a prompt body, a question or a raw provider string beyond what was validated.
 */
public final class ArtifactPayload {

    private final ArtifactKind kind;

    /** Set for {@link ArtifactKind#ROOT_TITLE} and {@link ArtifactKind#BRANCH_TITLE}. */
    public final String title;

    /** Set for {@link ArtifactKind#BRANCH_SUMMARY} and {@link ArtifactKind#CONTEXT_COMPRESSION}. */
    public final String summary;

    /** Set for {@link ArtifactKind#BRANCH_SUMMARY}; the AI key points of the same response. */
    public final List<String> keyPoints;

    /** Set for the two recommendation kinds; questions or topics, in the provider's order. */
    public final List<String> items;

    private ArtifactPayload(ArtifactKind kind, String title, String summary, List<String> keyPoints,
            List<String> items) {
        this.kind = kind;
        this.title = title;
        this.summary = summary;
        this.keyPoints = Collections.unmodifiableList(keyPoints);
        this.items = Collections.unmodifiableList(items);
    }

    static ArtifactPayload title(ArtifactKind kind, String title) {
        return new ArtifactPayload(kind, title, null, Collections.<String>emptyList(),
                Collections.<String>emptyList());
    }

    static ArtifactPayload summary(ArtifactKind kind, String summary, List<String> keyPoints) {
        return new ArtifactPayload(kind, null, summary, keyPoints, Collections.<String>emptyList());
    }

    static ArtifactPayload items(ArtifactKind kind, List<String> items) {
        return new ArtifactPayload(kind, null, null, Collections.<String>emptyList(), items);
    }

    /** The envelope as it is stored: this kind's fields, in the design's order, canonically encoded. */
    public String canonicalJson() {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        switch (kind) {
            case ROOT_TITLE, BRANCH_TITLE -> fields.put("title", title);
            case BRANCH_SUMMARY -> {
                fields.put("summary", summary);
                fields.put("key_points", keyPoints);
            }
            case ANSWER_RECOMMENDATIONS -> fields.put("items", keyedItems("question"));
            case HOME_RECOMMENDATIONS -> fields.put("items", keyedItems("topic"));
            case CONTEXT_COMPRESSION -> fields.put("summary", summary);
        }
        StringBuilder out = new StringBuilder();
        JsonText.write(fields, out);
        return out.toString();
    }

    private List<Object> keyedItems(String key) {
        List<Object> keyed = new ArrayList<Object>(items.size());
        for (String item : items) {
            Map<String, Object> one = new LinkedHashMap<String, Object>();
            one.put(key, item);
            keyed.add(one);
        }
        return keyed;
    }
}