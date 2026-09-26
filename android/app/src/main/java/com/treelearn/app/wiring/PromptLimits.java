package com.treelearn.app.wiring;

import com.treelearn.ai.prompt.PromptBudget;

/**
 * The one place the request budget's two numbers are written down (technical design 9.2, prompt
 * design 5).
 *
 * <p><strong>No authoritative figure existed, so this task chose both.</strong> The design fixes the
 * mechanism - reserve the system prompt, the current question and a minimum answer before history is
 * considered, and refuse the build when the remainder does not fit - but it states neither a capacity
 * nor a minimum answer length. The choice is recorded here, once, instead of at a call site:
 *
 * <ul>
 *   <li>{@link #CAPACITY_CHARS} = 60000. {@code PromptBudget} counts UTF-16 characters rather than
 *       tokens because V1 ships no tokenizer, and 60000 characters stays inside a 64K-token context
 *       however the text is mixed (a Chinese character costs about one token, a Latin one about a
 *       quarter), while leaving room for the answer itself. A budget that is only exceeded at the
 *       provider would fail on the learner's device instead of on screen, which is what the reserve
 *       exists to prevent.</li>
 *   <li>{@link #MIN_ANSWER_CHARS} = 512. An answer shorter than a paragraph is not a usable learning
 *       answer, so 512 characters are reserved for it and history may not consume that reservation.
 *       The generation tests already use this figure, so the tree holds one number rather than
 *       two.</li>
 * </ul>
 *
 * <p>{@link AppContainer} builds the single {@link PromptBudget} the application uses from these
 * constants; a test that needs an equivalent budget names these constants rather than repeating the
 * numbers, which is what keeps "one judgement point" true for a number as well as for a rule.
 */
public final class PromptLimits {

    /** The context capacity of one assembled request, in UTF-16 characters. */
    public static final int CAPACITY_CHARS = 60000;

    /** The characters reserved for the answer so history cannot consume the whole context. */
    public static final int MIN_ANSWER_CHARS = 512;

    /** The snippet of one web-search result that may enter the prompt. */
    public static final int SEARCH_SNIPPET_CHARS = 200;

    /** The title of one web-search result that may enter the prompt. */
    public static final int SEARCH_TITLE_CHARS = 80;

    /** The user question forwarded to the search engine, in characters. */
    public static final int SEARCH_QUERY_CHARS = 100;

    /** The total budget the search step may add to the assembled prompt. */
    public static final int SEARCH_INJECTION_LIMIT_CHARS = 4000;

    /** The connect timeout of one web-search call, in milliseconds. */
    public static final int SEARCH_CONNECT_TIMEOUT_MS = 5000;

    /** The read timeout of one web-search call, in milliseconds. */
    public static final int SEARCH_READ_TIMEOUT_MS = 10000;

    private PromptLimits() {
    }

    /** @return the budget this build sends every learning answer under */
    public static PromptBudget budget() {
        return new PromptBudget(CAPACITY_CHARS, MIN_ANSWER_CHARS);
    }
}