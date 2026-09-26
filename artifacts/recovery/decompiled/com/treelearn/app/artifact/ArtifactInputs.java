/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.ContextPlan$Entry
 *  com.treelearn.core.domain.ContextPlan$EntryKind
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.JsonText
 */
package com.treelearn.app.artifact;

import com.treelearn.app.artifact.TreeOutline;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.JsonText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactInputs {
    private ArtifactInputs() {
    }

    public static String rootTitle(String rootQuestion) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("root_question", Guard.nonBlank((String)rootQuestion, (String)"rootQuestion"));
        return ArtifactInputs.encode(fields);
    }

    public static String branchTitle(String sourceQuote, String firstQuestion) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("source_quote", Guard.text((String)sourceQuote, (String)"sourceQuote"));
        fields.put("first_question", Guard.nonBlank((String)firstQuestion, (String)"firstQuestion"));
        return ArtifactInputs.encode(fields);
    }

    public static String branchSummary(List<ContextPlan.Entry> birthContext, List<ContextPlan.Entry> branchMessages) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("birth_context", ArtifactInputs.entries((List)Guard.object(birthContext, (String)"birthContext")));
        fields.put("branch_messages", ArtifactInputs.entries((List)Guard.object(branchMessages, (String)"branchMessages")));
        return ArtifactInputs.encode(fields);
    }

    public static String answerRecommendations(String latestAnswer, List<TreeOutline.Entry> outline, int maxItems) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("latest_answer", Guard.nonBlank((String)latestAnswer, (String)"latestAnswer"));
        fields.put("tree_outline", ArtifactInputs.outline(outline));
        fields.put("max_items", ArtifactInputs.itemCeiling(maxItems));
        return ArtifactInputs.encode(fields);
    }

    public static String homeRecommendations(List<TreeOutline.Entry> outline, int maxItems) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("tree_outline", ArtifactInputs.outline(outline));
        fields.put("max_items", ArtifactInputs.itemCeiling(maxItems));
        return ArtifactInputs.encode(fields);
    }

    public static String contextCompression(List<CompressionTurn> turns) {
        ArrayList<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        for (CompressionTurn turn : (List)Guard.object(turns, (String)"turns")) {
            messages.add(turn.questionJson());
            messages.add(turn.answerJson());
        }
        LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("messages", messages);
        return ArtifactInputs.encode(fields);
    }

    public static String contextCompressionEntries(List<ContextPlan.Entry> entries) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("messages", ArtifactInputs.entries((List)Guard.object(entries, (String)"entries")));
        return ArtifactInputs.encode(fields);
    }

    private static List<Object> entries(List<ContextPlan.Entry> planEntries) {
        ArrayList<Object> json = new ArrayList<Object>(planEntries.size());
        for (ContextPlan.Entry entry : planEntries) {
            Guard.object((Object)entry, (String)"entry");
            LinkedHashMap<String, String> fields = new LinkedHashMap<String, String>();
            fields.put("kind", entry.kind.name());
            switch (entry.kind) {
                case USER_MESSAGE: {
                    fields.put("text", entry.userText);
                    break;
                }
                case STABLE_REVISION: {
                    fields.put("reasoning", entry.reasoningText);
                    fields.put("answer", entry.answerText);
                    break;
                }
                case SUMMARY: {
                    fields.put("summary", entry.summaryText);
                }
            }
            json.add(fields);
        }
        return json;
    }

    private static List<Object> outline(List<TreeOutline.Entry> outlineEntries) {
        ArrayList<Object> json = new ArrayList<Object>();
        for (TreeOutline.Entry entry : (List)Guard.object(outlineEntries, (String)"treeOutline")) {
            json.add(((TreeOutline.Entry)Guard.object((Object)entry, (String)"outline entry")).json());
        }
        return json;
    }

    private static int itemCeiling(int maxItems) {
        return Guard.atLeast((int)maxItems, (int)1, (String)"maxItems");
    }

    private static String encode(Map<String, Object> fields) {
        StringBuilder out = new StringBuilder();
        JsonText.write(fields, (StringBuilder)out);
        return out.toString();
    }

    public static final class CompressionTurn {
        public final String question;
        public final AnswerRevision revision;

        public CompressionTurn(String question, AnswerRevision revision) {
            this.question = Guard.nonBlank((String)question, (String)"question");
            this.revision = (AnswerRevision)Guard.object((Object)revision, (String)"revision");
        }

        int chars() {
            return this.question.length() + this.revision.reasoningContent.length() + this.revision.answerContent.length();
        }

        boolean coversReasoning() {
            return !this.revision.reasoningContent.trim().isEmpty();
        }

        private Map<String, Object> questionJson() {
            LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("kind", ContextPlan.EntryKind.USER_MESSAGE.name());
            fields.put("text", this.question);
            return fields;
        }

        private Map<String, Object> answerJson() {
            LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("kind", ContextPlan.EntryKind.STABLE_REVISION.name());
            fields.put("reasoning", this.revision.reasoningContent);
            fields.put("answer", this.revision.answerContent);
            return fields;
        }
    }
}

