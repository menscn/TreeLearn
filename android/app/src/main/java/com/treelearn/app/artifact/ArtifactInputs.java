package com.treelearn.app.artifact;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.JsonText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The structured inputs of the six derived purposes (prompt design 6; technical design 10).
 *
 * <p>Every envelope is built as a JSON value tree and rendered by {@link JsonText}, so escaping is
 * the codec's job and no caller can assemble one by concatenating strings. That also makes the
 * inputs data rather than instructions: a question containing "ignore the previous rules" is one
 * escaped string value of one field, and it cannot change the roles, the field set or the output
 * protocol — the system body says so, and the shape of the envelope enforces it.
 *
 * <p>Each purpose takes only what the design grants it. {@link #homeRecommendations} in particular
 * accepts outline entries and a ceiling and nothing else, so a full conversation body has no channel
 * into that request.
 */
public final class ArtifactInputs {

    private ArtifactInputs() {
    }

    /** {@code {"root_question":"..."}} (prompt design 6.1). */
    public static String rootTitle(String rootQuestion) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("root_question", Guard.nonBlank(rootQuestion, "rootQuestion"));
        return encode(fields);
    }

    /** {@code {"source_quote":"...或空字符串","first_question":"..."}} (prompt design 6.1). */
    public static String branchTitle(String sourceQuote, String firstQuestion) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("source_quote", Guard.text(sourceQuote, "sourceQuote"));
        fields.put("first_question", Guard.nonBlank(firstQuestion, "firstQuestion"));
        return encode(fields);
    }

    /** {@code {"birth_context":[...],"branch_messages":[...]}} (prompt design 6.2). */
    public static String branchSummary(List<ContextPlan.Entry> birthContext,
            List<ContextPlan.Entry> branchMessages) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("birth_context", entries(Guard.object(birthContext, "birthContext")));
        fields.put("branch_messages", entries(Guard.object(branchMessages, "branchMessages")));
        return encode(fields);
    }

    /** {@code {"latest_answer":"...","tree_outline":[...],"max_items":N}} (prompt design 6.3). */
    public static String answerRecommendations(String latestAnswer, List<TreeOutline.Entry> outline,
            int maxItems) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("latest_answer", Guard.nonBlank(latestAnswer, "latestAnswer"));
        fields.put("tree_outline", outline(outline));
        fields.put("max_items", itemCeiling(maxItems));
        return encode(fields);
    }

    /** {@code {"tree_outline":[...],"max_items":N}} — an outline and a ceiling, and nothing else. */
    public static String homeRecommendations(List<TreeOutline.Entry> outline, int maxItems) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("tree_outline", outline(outline));
        fields.put("max_items", itemCeiling(maxItems));
        return encode(fields);
    }

    /** {@code {"messages":[...]}} with the question, the reasoning and the answer of every turn. */
    public static String contextCompression(List<CompressionTurn> turns) {
        List<Object> messages = new ArrayList<Object>();
        for (CompressionTurn turn : Guard.object(turns, "turns")) {
            messages.add(turn.questionJson());
            messages.add(turn.answerJson());
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("messages", messages);
        return encode(fields);
    }

    /** The same compression envelope built from the vendor-neutral context plan entries. */
    public static String contextCompressionEntries(List<ContextPlan.Entry> entries) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("messages", entries(Guard.object(entries, "entries")));
        return encode(fields);
    }

    /** One older turn a compression must cover: its question, its reasoning and its answer. */
    public static final class CompressionTurn {

        public final String question;

        public final AnswerRevision revision;

        public CompressionTurn(String question, AnswerRevision revision) {
            this.question = Guard.nonBlank(question, "question");
            this.revision = Guard.object(revision, "revision");
        }

        /** The characters this turn contributes; a compression has to shrink below this total. */
        int chars() {
            return question.length() + revision.reasoningContent.length() + revision.answerContent.length();
        }

        /** False when this turn carries no reasoning, which a compression must cover rather than drop. */
        boolean coversReasoning() {
            return !revision.reasoningContent.trim().isEmpty();
        }

        private Map<String, Object> questionJson() {
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("kind", ContextPlan.EntryKind.USER_MESSAGE.name());
            fields.put("text", question);
            return fields;
        }

        private Map<String, Object> answerJson() {
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("kind", ContextPlan.EntryKind.STABLE_REVISION.name());
            fields.put("reasoning", revision.reasoningContent);
            fields.put("answer", revision.answerContent);
            return fields;
        }
    }

    /**
     * One plan turn as the envelope's own shape: a learner message, a stable revision (reasoning and
     * answer together) or a finished summary. The kind is the plan's, so the two layers cannot drift.
     */
    private static List<Object> entries(List<ContextPlan.Entry> planEntries) {
        List<Object> json = new ArrayList<Object>(planEntries.size());
        for (ContextPlan.Entry entry : planEntries) {
            Guard.object(entry, "entry");
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            fields.put("kind", entry.kind.name());
            switch (entry.kind) {
                case USER_MESSAGE -> fields.put("text", entry.userText);
                case STABLE_REVISION -> {
                    fields.put("reasoning", entry.reasoningText);
                    fields.put("answer", entry.answerText);
                }
                case SUMMARY -> fields.put("summary", entry.summaryText);
            }
            json.add(fields);
        }
        return json;
    }

    private static List<Object> outline(List<TreeOutline.Entry> outlineEntries) {
        List<Object> json = new ArrayList<Object>();
        for (TreeOutline.Entry entry : Guard.object(outlineEntries, "treeOutline")) {
            json.add(Guard.object(entry, "outline entry").json());
        }
        return json;
    }

    private static int itemCeiling(int maxItems) {
        return Guard.atLeast(maxItems, 1, "maxItems");
    }

    private static String encode(Map<String, Object> fields) {
        StringBuilder out = new StringBuilder();
        JsonText.write(fields, out);
        return out.toString();
    }
}
