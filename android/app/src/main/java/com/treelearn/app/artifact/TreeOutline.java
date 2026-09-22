package com.treelearn.app.artifact;

import com.treelearn.core.domain.Guard;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tree outline a recommendation may see: titles, anchors and short summaries only (technical
 * design 10; prompt design 6.3).
 *
 * <p>This type exists to make a leak <em>impossible</em> rather than merely forbidden. Home
 * recommendations are told to look at the learner's tree, and the design's whole constraint is that
 * they see the outline and never a conversation body. Since an entry has exactly four fields and
 * none of them is a message, an answer or a reasoning text, a caller that wanted to submit a full
 * turn would have nowhere to put it — the input can only be built from outline entries.
 */
public final class TreeOutline {

    private TreeOutline() {
    }

    /** The two levels the design grants to a recommendation input. */
    public enum Level {
        TOPIC,
        BRANCH
    }

    /** One outline row. {@code sourceQuote} is empty for a topic; a branch may have a short one. */
    public static final class Entry {

        public final Level level;
        public final String title;
        public final String sourceQuote;
        public final String summary;

        private Entry(Level level, String title, String sourceQuote, String summary) {
            this.level = Guard.object(level, "level");
            this.title = Guard.nonBlank(title, "title");
            this.sourceQuote = Guard.text(sourceQuote, "sourceQuote");
            this.summary = Guard.text(summary, "summary");
        }

        public static Entry topic(String title, String summary) {
            return new Entry(Level.TOPIC, title, "", summary);
        }

        public static Entry branch(String title, String sourceQuote, String summary) {
            return new Entry(Level.BRANCH, title, sourceQuote, summary);
        }

        /** The design's four fields, in the design's order (prompt design 6.3). */
        Map<String, Object> json() {
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("level", level.name());
            fields.put("title", title);
            fields.put("source_quote", sourceQuote);
            fields.put("summary", summary);
            return fields;
        }
    }
}