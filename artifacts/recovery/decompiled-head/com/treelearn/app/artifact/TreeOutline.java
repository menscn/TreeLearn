/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.artifact;

import com.treelearn.core.domain.Guard;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TreeOutline {
    private TreeOutline() {
    }

    public static final class Entry {
        public final Level level;
        public final String title;
        public final String sourceQuote;
        public final String summary;

        private Entry(Level level, String title, String sourceQuote, String summary) {
            this.level = (Level)((Object)Guard.object((Object)((Object)level), (String)"level"));
            this.title = Guard.nonBlank((String)title, (String)"title");
            this.sourceQuote = Guard.text((String)sourceQuote, (String)"sourceQuote");
            this.summary = Guard.text((String)summary, (String)"summary");
        }

        public static Entry topic(String title, String summary) {
            return new Entry(Level.TOPIC, title, "", summary);
        }

        public static Entry branch(String title, String sourceQuote, String summary) {
            return new Entry(Level.BRANCH, title, sourceQuote, summary);
        }

        Map<String, Object> json() {
            LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("level", this.level.name());
            fields.put("title", this.title);
            fields.put("source_quote", this.sourceQuote);
            fields.put("summary", this.summary);
            return fields;
        }
    }

    public static enum Level {
        TOPIC,
        BRANCH;

    }
}

