package com.treelearn.ai.prompt;

import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.core.domain.PromptSpec;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The versioned prompt registry — the only place a {@link PromptPurpose} becomes a system body
 * (prompt design V1.1 §3.1, §4, §6; technical design §9.2).
 *
 * <p>It takes an enum and nothing else. There is no lookup by string, no caller-supplied template,
 * no fallback body and no interpolation: a purpose this build does not know is refused, so a request
 * can never silently carry the wrong usage's prompt.
 *
 * <p>Versions and bodies are resolved by exhaustive switches, so adding a {@link PromptPurpose} does
 * not compile until it has been given both — "every purpose has exactly one non-blank version and
 * body" is enforced at build time and re-asserted by {@code DeepSeekPromptRegistryTest}.
 */
public final class DeepSeekPromptRegistry {

    /** The single section break used both inside derived bodies and before the thinking addendum. */
    private static final String SECTION_BREAK = "\n";

    private static final Map<PromptPurpose, PromptSpec> SPECS = buildSpecs();

    private DeepSeekPromptRegistry() {
    }

    /**
     * @return the versioned spec registered for {@code purpose}
     * @throws PromptBuildException with {@code UNKNOWN_PURPOSE} when no purpose was supplied: prompt
     *         design §7 makes a missing purpose a request-build failure, not a crash
     */
    public static PromptSpec spec(PromptPurpose purpose) {
        if (purpose == null) {
            throw new PromptBuildException(PromptBuildException.Reason.UNKNOWN_PURPOSE, null,
                    "a DeepSeek request must name one of this build's PromptPurpose values");
        }
        return SPECS.get(purpose);
    }

    /**
     * @return the system content of {@code purpose}, with the static thinking addendum appended when
     *         thinking is on. The addendum is a main-answer policy (prompt design §4.1); asking a
     *         derived purpose for it is refused instead of quietly ignored.
     */
    public static String systemContent(PromptPurpose purpose, boolean thinkingEnabled) {
        PromptSpec spec = spec(purpose);
        if (!thinkingEnabled) {
            return spec.systemContent;
        }
        if (purpose != PromptPurpose.LEARNING_ANSWER) {
            throw new PromptBuildException(
                    PromptBuildException.Reason.THINKING_NOT_APPLICABLE_FOR_PURPOSE, purpose,
                    "thinking is part of the adaptive-tutor policy; a derived purpose is a single "
                            + "structured call and never carries the addendum");
        }
        return spec.systemContent + SECTION_BREAK + PromptBodiesV1.THINKING_ADDENDUM;
    }

    private static Map<PromptPurpose, PromptSpec> buildSpecs() {
        Map<PromptPurpose, PromptSpec> specs = new EnumMap<>(PromptPurpose.class);
        for (PromptPurpose purpose : PromptPurpose.values()) {
            DomainResult<PromptSpec> built =
                    PromptSpec.of(purpose, versionOf(purpose), bodyOf(purpose));
            if (built.isFailure()) {
                throw new PromptBuildException(PromptBuildException.Reason.REGISTRY_INCONSISTENT,
                        purpose,
                        "the registered version " + versionOf(purpose) + " is not owned by it");
            }
            specs.put(purpose, built.value());
        }
        return Collections.unmodifiableMap(specs);
    }

    /** Exhaustive by construction: a new {@link PromptPurpose} does not compile until it has a version. */
    private static String versionOf(PromptPurpose purpose) {
        return switch (purpose) {
            case LEARNING_ANSWER -> "tl-learning-answer/1.1.0";
            case ROOT_TITLE -> "tl-root-title/1.0.0";
            case BRANCH_TITLE -> "tl-branch-title/1.0.0";
            case BRANCH_SUMMARY -> "tl-branch-summary/1.0.0";
            case ANSWER_RECOMMENDATIONS -> "tl-answer-recommendations/1.1.0";
            case HOME_RECOMMENDATIONS -> "tl-home-recommendations/1.0.0";
            case CONTEXT_COMPRESSION -> "tl-context-compression/1.0.0";
        };
    }

    /** Same exhaustiveness contract as {@link #versionOf}. */
    private static String bodyOf(PromptPurpose purpose) {
        return switch (purpose) {
            case LEARNING_ANSWER -> PromptBodiesV1.LEARNING_ANSWER_TUTOR;
            case ROOT_TITLE -> derivedBody(PromptBodiesV1.ROOT_TITLE_SUFFIX);
            case BRANCH_TITLE -> derivedBody(PromptBodiesV1.BRANCH_TITLE_SUFFIX);
            case BRANCH_SUMMARY -> derivedBody(PromptBodiesV1.BRANCH_SUMMARY_SUFFIX);
            case ANSWER_RECOMMENDATIONS -> derivedBody(PromptBodiesV1.ANSWER_RECOMMENDATIONS_SUFFIX);
            case HOME_RECOMMENDATIONS -> derivedBody(PromptBodiesV1.HOME_RECOMMENDATIONS_SUFFIX);
            case CONTEXT_COMPRESSION -> derivedBody(PromptBodiesV1.CONTEXT_COMPRESSION_SUFFIX);
        };
    }

    /** Prompt design §6: every derived system body is the shared base plus its own static suffix. */
    private static String derivedBody(String suffix) {
        return PromptBodiesV1.DERIVED_PROCESSOR_BASE + SECTION_BREAK + suffix;
    }
}
