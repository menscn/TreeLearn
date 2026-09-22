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
 * and PRD 6.2.2 point 2 makes the question that temporary name. A branch with neither yields the
 * empty string; whether that is an error is the caller's decision, not this rule's - the home card
 * refuses it loudly through {@code TopicCard}'s own guard, while the conversation draws an untitled
 * branch.
 */
final class BranchHeading {

    private BranchHeading() {
    }

    /**
     * @param derivedTitle the branch's stored title, or null while it has not been derived
     * @param firstQuestion the branch's first learner message, or null when the branch stores none
     * @return the stored title when it carries text, otherwise the first question, otherwise the
     *         empty string
     */
    static String of(String derivedTitle, String firstQuestion) {
        if (derivedTitle != null && !derivedTitle.trim().isEmpty()) {
            return derivedTitle;
        }
        if (firstQuestion != null && !firstQuestion.trim().isEmpty()) {
            return firstQuestion;
        }
        return "";
    }
}