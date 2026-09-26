/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.JsonText
 *  com.treelearn.core.domain.JsonText$Syntax
 */
package com.treelearn.app.artifact;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactOutcome;
import com.treelearn.app.artifact.ArtifactPayload;
import com.treelearn.app.artifact.DerivedResult;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.JsonText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArtifactPayloadCodec {
    private ArtifactPayloadCodec() {
    }

    public static DerivedResult parse(ArtifactKind kind, String text, int itemCeiling) {
        Object root;
        Guard.object((Object)((Object)kind), (String)"kind");
        Guard.atLeast((int)itemCeiling, (int)1, (String)"itemCeiling");
        if (text == null) {
            return DerivedResult.refused(ArtifactOutcome.PAYLOAD_INVALID);
        }
        try {
            root = JsonText.read((String)text);
        }
        catch (JsonText.Syntax notJson) {
            return DerivedResult.refused(ArtifactOutcome.PAYLOAD_INVALID);
        }
        if (!(root instanceof Map)) {
            return DerivedResult.refused(ArtifactOutcome.PAYLOAD_INVALID);
        }
        try {
            return ArtifactPayloadCodec.shapeOf(kind, (Map)root, itemCeiling);
        }
        catch (Shape refused) {
            return DerivedResult.refused(ArtifactOutcome.PAYLOAD_INVALID);
        }
    }

    private static DerivedResult shapeOf(ArtifactKind kind, Map<?, ?> fields, int itemCeiling) {
        return switch (kind) {
            default -> throw new IncompatibleClassChangeError();
            case ArtifactKind.ROOT_TITLE, ArtifactKind.BRANCH_TITLE -> {
                ArtifactPayloadCodec.only(fields, "title");
                yield DerivedResult.usable(ArtifactPayload.title(kind, ArtifactPayloadCodec.nonBlank(fields.get("title"))));
            }
            case ArtifactKind.BRANCH_SUMMARY -> {
                ArtifactPayloadCodec.only(fields, "summary", "key_points");
                yield DerivedResult.usable(ArtifactPayload.summary(kind, ArtifactPayloadCodec.text(fields.get("summary")), ArtifactPayloadCodec.nonBlankStrings(fields.get("key_points"))));
            }
            case ArtifactKind.ANSWER_RECOMMENDATIONS -> DerivedResult.usable(ArtifactPayload.items(kind, ArtifactPayloadCodec.items(fields, "question", itemCeiling)));
            case ArtifactKind.HOME_RECOMMENDATIONS -> DerivedResult.usable(ArtifactPayload.items(kind, ArtifactPayloadCodec.items(fields, "topic", itemCeiling)));
            case ArtifactKind.CONTEXT_COMPRESSION -> {
                ArtifactPayloadCodec.only(fields, "summary");
                String summary = ArtifactPayloadCodec.text(fields.get("summary"));
                if (summary.trim().isEmpty()) {
                    yield DerivedResult.refused(ArtifactOutcome.SUMMARY_EMPTY);
                }
                yield DerivedResult.usable(ArtifactPayload.summary(kind, summary, List.of()));
            }
        };
    }

    private static void only(Map<?, ?> fields, String ... names) {
        if (!fields.keySet().equals(Set.of(names))) {
            throw new Shape();
        }
    }

    private static String nonBlank(Object value) {
        String text;
        if (!(value instanceof String) || (text = (String)value).trim().isEmpty()) {
            throw new Shape();
        }
        return text;
    }

    private static String text(Object value) {
        if (!(value instanceof String)) {
            throw new Shape();
        }
        String text = (String)value;
        return text;
    }

    private static List<String> nonBlankStrings(Object value) {
        if (!(value instanceof List)) {
            throw new Shape();
        }
        List values = (List)value;
        ArrayList<String> texts = new ArrayList<String>(values.size());
        for (Object element : values) {
            texts.add(ArtifactPayloadCodec.nonBlank(element));
        }
        return texts;
    }

    private static List<String> items(Map<?, ?> fields, String key, int itemCeiling) {
        List values;
        ArtifactPayloadCodec.only(fields, "items");
        Object obj = fields.get("items");
        if (!(obj instanceof List) || (values = (List)obj).size() > itemCeiling) {
            throw new Shape();
        }
        ArrayList<String> texts = new ArrayList<String>(values.size());
        for (Object element : values) {
            if (!(element instanceof Map)) {
                throw new Shape();
            }
            Map one = (Map)element;
            ArtifactPayloadCodec.only(one, key);
            texts.add(ArtifactPayloadCodec.nonBlank(one.get(key)));
        }
        return texts;
    }

    private static final class Shape
    extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private Shape() {
        }
    }
}

