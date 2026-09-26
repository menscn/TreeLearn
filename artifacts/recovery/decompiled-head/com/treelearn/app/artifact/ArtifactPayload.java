/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.JsonText
 */
package com.treelearn.app.artifact;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.core.domain.JsonText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public final class ArtifactPayload {
    private final ArtifactKind kind;
    public final String title;
    public final String summary;
    public final List<String> keyPoints;
    public final List<String> items;

    private ArtifactPayload(ArtifactKind kind, String title, String summary, List<String> keyPoints, List<String> items) {
        this.kind = kind;
        this.title = title;
        this.summary = summary;
        this.keyPoints = Collections.unmodifiableList(keyPoints);
        this.items = Collections.unmodifiableList(items);
    }

    static ArtifactPayload title(ArtifactKind kind, String title) {
        return new ArtifactPayload(kind, title, null, Collections.emptyList(), Collections.emptyList());
    }

    static ArtifactPayload summary(ArtifactKind kind, String summary, List<String> keyPoints) {
        return new ArtifactPayload(kind, null, summary, keyPoints, Collections.emptyList());
    }

    static ArtifactPayload items(ArtifactKind kind, List<String> items) {
        return new ArtifactPayload(kind, null, null, Collections.emptyList(), items);
    }

    public String canonicalJson() {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
        switch (this.kind) {
            case ROOT_TITLE: 
            case BRANCH_TITLE: {
                fields.put("title", this.title);
                break;
            }
            case BRANCH_SUMMARY: {
                fields.put("summary", this.summary);
                fields.put("key_points", this.keyPoints);
                break;
            }
            case ANSWER_RECOMMENDATIONS: {
                fields.put("items", this.keyedItems("question"));
                break;
            }
            case HOME_RECOMMENDATIONS: {
                fields.put("items", this.keyedItems("topic"));
                break;
            }
            case CONTEXT_COMPRESSION: {
                fields.put("summary", this.summary);
            }
        }
        StringBuilder out = new StringBuilder();
        JsonText.write(fields, (StringBuilder)out);
        return out.toString();
    }

    private List<Object> keyedItems(String key) {
        ArrayList<Object> keyed = new ArrayList<Object>(this.items.size());
        for (String item : this.items) {
            LinkedHashMap<String, String> one = new LinkedHashMap<String, String>();
            one.put(key, item);
            keyed.add(one);
        }
        return keyed;
    }
}

