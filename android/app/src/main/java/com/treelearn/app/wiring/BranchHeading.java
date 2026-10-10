package com.treelearn.app.wiring;

/**
 * The one implementation of "what a tree or a branch is called right now" (PRD 6.2.2).
 *
 * <p>The rule is small and it has two readers - the home card and the conversation heading - so it
 * lives in exactly one place. Two copies would let the same topic be named one way in the list and
 * another way after it is opened, which is the drift this class exists to make impossible.
 *
 * <p>The heading is the stored title while it carries text, and otherwise the first question the
 * branch was created from: a tree is readable and identifiable long before the naming call has run,
 * and PRD 6.2.2 point 2 makes the question that temporary name. A child branch that has neither yet
 * can use the source answer's first sentence or paragraph as a temporary readable name. The final
 * fixed fallback keeps a list row from becoming blank when the stored history is incomplete.
 */
final class BranchHeading {

    private BranchHeading() {
    }

    /** Keeps existing readers on the stored-title and first-question rule. */
    static String of(String derivedTitle, String firstQuestion) {
        return resolve(derivedTitle, firstQuestion, null, "");
    }

    /**
     * @param derivedTitle the branch's stored title, or null while it has not been derived
     * @param firstQuestion the branch's first learner message, or null when the branch stores none
     * @param sourceAnswer the answer the branch was forked from, or null when it cannot be read
     * @return the first non-blank stored title, question, source preview, or fixed fallback
     */
    static String of(String derivedTitle, String firstQuestion, String sourceAnswer) {
        return resolve(derivedTitle, firstQuestion, previewOf(sourceAnswer), "未命名分支");
    }

    private static String resolve(String derivedTitle, String firstQuestion, String sourcePreview,
            String emptyFallback) {
        if (derivedTitle != null && !derivedTitle.trim().isEmpty()) {
            return derivedTitle;
        }
        if (firstQuestion != null && !firstQuestion.trim().isEmpty()) {
            return firstQuestion;
        }
        String preview = sourcePreview == null ? "" : sourcePreview;
        return preview.isEmpty() ? emptyFallback : preview;
    }

    private static String previewOf(String sourceAnswer) {
        if (sourceAnswer == null || sourceAnswer.trim().isEmpty()) {
            return "";
        }
        String firstParagraph = sourceAnswer.trim().split("\\R", 2)[0].trim();
        String normalized = firstParagraph.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "";
        }
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '。' || current == '！' || current == '？'
                    || current == '!' || current == '?') {
                return normalized.substring(0, index + 1);
            }
        }
        return normalized;
    }
}
