package com.treelearn.ai.prompt;

/**
 * The capacity reservation of one assembled request (prompt design V1.0 §5, technical design §9.2).
 *
 * <p><strong>Characters, not tokens.</strong> V1 ships no tokenizer — this project adds no
 * third-party dependency, and a provider-specific tokenizer would make a refusal depend on the
 * model — so the design's token budget is enforced as a character budget: {@link #capacityChars} is
 * the context capacity expressed in UTF-16 characters. Every reserve/refuse decision is therefore
 * deterministic and reproducible in a plain JVM test, and character counts are exactly what a
 * diagnostic is allowed to record (§5, §15).
 *
 * <p>The system prompt, the current input and {@link #minAnswerChars} are reserved <em>before</em>
 * history is considered: the reservation is what makes "the system prompt is never trimmed and
 * history is never silently dropped" a mechanical property instead of a convention.
 */
public final class PromptBudget {

    public final int capacityChars;
    public final int minAnswerChars;

    public PromptBudget(int capacityChars, int minAnswerChars) {
        if (capacityChars < 1) {
            throw new IllegalArgumentException("capacityChars must be >= 1 but was " + capacityChars);
        }
        if (minAnswerChars < 0) {
            throw new IllegalArgumentException("minAnswerChars must be >= 0 but was " + minAnswerChars);
        }
        if (minAnswerChars > capacityChars) {
            throw new IllegalArgumentException("minAnswerChars (" + minAnswerChars
                    + ") must not exceed capacityChars (" + capacityChars + ")");
        }
        this.capacityChars = capacityChars;
        this.minAnswerChars = minAnswerChars;
    }
}
