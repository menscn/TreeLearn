package com.treelearn.core.domain;

/**
 * The vendor-neutral identity of one versioned system prompt (prompt design V1.0 §3.1, technical
 * design §9.2).
 *
 * <p>Only the identity of a prompt crosses into the domain: which usage it belongs to, which version
 * of it, and the system body that version resolves to. The body travels with the spec because
 * {@code :ai} needs it to assemble a request, but it is deliberately <em>not</em> carried by any
 * persisted entity — §9.2 stores the purpose and version only, so re-wording a prompt is never a data
 * migration. Logging a spec records purpose and version, never the body (§15).
 *
 * <p>This type must not grow a provider-shaped field. Request flags, thinking controls and tool
 * schemas belong to {@code :ai}; the only acceptable field types here are JDK types and domain types,
 * and {@code PromptSpecTest} enforces that mechanically rather than by convention.
 *
 * <p><strong>Error channels.</strong> A missing purpose or a missing/blank body is a caller bug and
 * throws {@link IllegalArgumentException}: the registry holds both as constants, so an absent one
 * means the caller's code is wrong. The policy version is the one argument whose <em>absence</em> is a
 * reportable business condition ({@link Guard} lists the three entry points that treat it that way),
 * so an absent version returns {@link DomainErrorCode#PROMPT_POLICY_VERSION_REQUIRED}, and a version
 * that is well formed but belongs to a <em>different</em> purpose returns
 * {@link DomainErrorCode#PROMPT_POLICY_VERSION_MISMATCH}, because both arguments are individually
 * valid and only their pairing is wrong — the same shape as {@link DomainErrorCode#ANCHOR_INVALID},
 * and the same decision as {@link GenerationCommand#create}.
 *
 * <p>The decision itself lives in {@link PromptPurpose#versionFailureFor} so the two prompt-policy
 * entry points cannot drift apart again: a round-1-independent audit of T-021 found this class
 * throwing while {@code GenerationCommand} returned a code for the identical input.
 *
 * <p>The caller should treat a failure here as a wiring defect and fail fast rather than fall back to
 * another prompt: {@code :ai}'s registry builds every spec from its own constants, so a mismatch
 * means the registry's table is wrong. That is why the method is a {@code DomainResult} and not an
 * exception — {@code :ai} decides to abort at startup, the domain does not decide it for them.
 */
public final class PromptSpec {

    public final PromptPurpose purpose;
    /** {@code "<purpose prefix>/MAJOR.MINOR.PATCH"}, owned by {@link #purpose}. */
    public final String policyVersion;
    /** The system body for this version. Never blank. */
    public final String systemContent;

    private PromptSpec(PromptPurpose purpose, String policyVersion, String systemContent) {
        this.purpose = Guard.object(purpose, "purpose");
        this.policyVersion = Guard.nonBlank(policyVersion, "policyVersion");
        this.systemContent = Guard.nonBlank(systemContent, "systemContent");
    }

    /**
     * @return the spec, or a failure with {@link DomainErrorCode#PROMPT_POLICY_VERSION_REQUIRED} when
     *         no version was supplied or {@link DomainErrorCode#PROMPT_POLICY_VERSION_MISMATCH} when
     *         the version is owned by a different purpose
     * @throws IllegalArgumentException if {@code purpose} is null, or {@code systemContent} is null or
     *         blank — per the error-channel rule in {@link Guard} and the declared-exception list
     *         documented there
     */
    public static DomainResult<PromptSpec> of(
            PromptPurpose purpose, String policyVersion, String systemContent) {
        // Argument shapes first, in declared order, for the reason GenerationCommand.create states: a
        // call carrying both a malformed argument and an unusable version must still report the caller
        // bug. The constructor repeats these checks because it is the invariant authority for any
        // future caller; the definition of "well formed" is not duplicated, only called twice.
        Guard.object(purpose, "purpose");
        Guard.nonBlank(systemContent, "systemContent");
        DomainError versionFailure = PromptPurpose.versionFailureFor(purpose, policyVersion);
        if (versionFailure != null) {
            return DomainResult.fail(versionFailure);
        }
        return DomainResult.ok(new PromptSpec(purpose, policyVersion, systemContent));
    }

    @Override
    public String toString() {
        // Sizes, not text: a log line must never leak prompt content (§15).
        return "PromptSpec[" + purpose + " " + policyVersion + ", " + systemContent.length()
                + " body chars]";
    }
}
