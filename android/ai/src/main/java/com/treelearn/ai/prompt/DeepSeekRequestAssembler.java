package com.treelearn.ai.prompt;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.PromptSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the ordered message list of one DeepSeek call: the versioned system prompt first, the
 * encoded history in the middle, the current input last (prompt design V1.0 §5, §7; technical
 * design §9.2).
 *
 * <p>The order is constructed, not validated after the fact, so "exactly one system message, at
 * position 0, and the current input last" cannot be broken by any input: history entries that would
 * introduce a second system message are refused, and everything else travels as data.
 *
 * <p>Nothing here calls the network and nothing here mutates its arguments: a refused build produces
 * no request at all, which is what makes "fail closed without calling DeepSeek" mechanical.
 */
public final class DeepSeekRequestAssembler {

    private DeepSeekRequestAssembler() {
    }

    /**
     * @param encodedHistory the {@code ContextPlan} as encoded by the context encoder: zero or more
     *        user/assistant messages, never a system message. The caller's list is copied.
     * @param currentInput the current question for {@code LEARNING_ANSWER}, or the structured JSON
     *        payload for a derived purpose
     * @throws PromptBuildException when the build is refused; {@link PromptBuildException#reason}
     *         carries the category. Refusals are decided in one fixed order — purpose, history shape,
     *         current input shape, then capacity — so a build that is wrong in several ways reports
     *         the same first defect every time
     */
    public static DeepSeekRequest assemble(PromptPurpose purpose, boolean thinkingEnabled,
            List<DeepSeekMessage> encodedHistory, String currentInput, PromptBudget budget) {
        Guard.object(budget, "budget");
        PromptSpec spec = DeepSeekPromptRegistry.spec(purpose);
        String systemContent = DeepSeekPromptRegistry.systemContent(purpose, thinkingEnabled);
        List<DeepSeekMessage> history = validateHistory(purpose, encodedHistory);
        String latest = validateCurrentInput(purpose, currentInput);
        reserveFor(purpose, systemContent, latest, history, budget);
        List<DeepSeekMessage> messages = new ArrayList<>(history.size() + 2);
        messages.add(new DeepSeekMessage(DeepSeekMessage.Role.SYSTEM, systemContent));
        messages.addAll(history);
        messages.add(new DeepSeekMessage(DeepSeekMessage.Role.USER, latest));
        return new DeepSeekRequest(purpose, spec.policyVersion, thinkingEnabled, messages,
                systemContent.length(), charsOf(history), latest.length());
    }

    private static List<DeepSeekMessage> validateHistory(PromptPurpose purpose,
            List<DeepSeekMessage> encodedHistory) {
        if (encodedHistory == null) {
            throw new PromptBuildException(PromptBuildException.Reason.MISSING_HISTORY, purpose,
                    "the encoded history must be supplied; pass an empty list to mean no history");
        }
        List<DeepSeekMessage> copy = new ArrayList<>(encodedHistory.size());
        for (DeepSeekMessage message : encodedHistory) {
            if (message == null || message.content.trim().isEmpty()) {
                throw new PromptBuildException(PromptBuildException.Reason.MALFORMED_HISTORY_MESSAGE,
                        purpose, "history entry " + copy.size() + " is missing or blank");
            }
            if (message.role == DeepSeekMessage.Role.SYSTEM) {
                throw new PromptBuildException(
                        PromptBuildException.Reason.SYSTEM_MESSAGE_IN_HISTORY, purpose,
                        "history entry " + copy.size() + " carries the system role; exactly one system "
                                + "message is composed here and it always sits at position 0");
            }
            copy.add(message);
        }
        return copy;
    }

    private static String validateCurrentInput(PromptPurpose purpose, String currentInput) {
        if (currentInput == null || currentInput.trim().isEmpty()) {
            throw new PromptBuildException(PromptBuildException.Reason.BLANK_CURRENT_INPUT, purpose,
                    "the current user input must not be blank");
        }
        if (purpose != PromptPurpose.LEARNING_ANSWER && !JsonObjectText.isObject(currentInput)) {
            throw new PromptBuildException(PromptBuildException.Reason.DERIVED_INPUT_NOT_JSON, purpose,
                    "a derived purpose takes one JSON object built from its structured input, not "
                            + "free text");
        }
        return currentInput;
    }

    /**
     * Reserves the system prompt, the current input and the minimum answer before history is
     * considered. A refusal never trims the system prompt and never drops or summarises history:
     * the caller has to shrink the plan (prompt design §5).
     */
    private static void reserveFor(PromptPurpose purpose, String systemContent, String currentInput,
            List<DeepSeekMessage> history, PromptBudget budget) {
        int reserved = systemContent.length() + currentInput.length() + budget.minAnswerChars;
        if (reserved > budget.capacityChars) {
            throw new PromptBuildException(PromptBuildException.Reason.RESERVE_EXCEEDS_BUDGET, purpose,
                    "reserving " + reserved + " chars (system " + systemContent.length()
                            + " + current input " + currentInput.length() + " + minimum answer "
                            + budget.minAnswerChars + ") exceeds the budget of "
                            + budget.capacityChars + " chars");
        }
        int historyChars = charsOf(history);
        if (reserved + historyChars > budget.capacityChars) {
            throw new PromptBuildException(PromptBuildException.Reason.HISTORY_EXCEEDS_BUDGET, purpose,
                    "the " + reserved + " char reservation plus " + historyChars
                            + " chars of history exceeds the budget of " + budget.capacityChars
                            + " chars; history is never silently dropped, truncated or summarised here");
        }
    }

    private static int charsOf(List<DeepSeekMessage> messages) {
        int chars = 0;
        for (DeepSeekMessage message : messages) {
            chars += message.content.length();
        }
        return chars;
    }
}
