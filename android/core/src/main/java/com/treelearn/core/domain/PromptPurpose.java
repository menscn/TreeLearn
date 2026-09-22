package com.treelearn.core.domain;

import java.util.regex.Pattern;

/**
 * Vendor-neutral prompt usages (prompt design V1.0 §3.1, §6; technical design §9.2, §10).
 *
 * <p>Each purpose owns its own version namespace. A title prompt can therefore never be swapped for
 * the teaching prompt by accident, and a change in one usage cannot silently change another. The
 * version string looks like {@code "<prefix>/<major>.<minor>.<patch>"}, for example
 * {@code tl-learning-answer/1.1.0}.
 *
 * <p>This enum is the authoritative first-release list from the prompt design document §3.1. It
 * deliberately does <em>not</em> contain a separate key-points purpose: AI key points are one
 * payload field of {@link #BRANCH_SUMMARY}, produced by the same structured response and validated
 * as its own field (technical design §10).
 *
 * <p>The full prompt text never enters a domain entity: {@code :ai} keeps the versioned bodies, the
 * database only stores purpose and version.
 */
public enum PromptPurpose {

    /** The main adaptive-tutor answer (policy {@code tl-learning-answer/1.1.0}). */
    LEARNING_ANSWER("tl-learning-answer"),

    /** The root branch title, derived from the root question alone. */
    ROOT_TITLE("tl-root-title"),

    /** A non-root branch title, derived from the source anchor and the first question. */
    BRANCH_TITLE("tl-branch-title"),

    /** Branch summary; its structured response also carries the AI key points payload field. */
    BRANCH_SUMMARY("tl-branch-summary"),

    /** Next-question recommendations bound to one specific stable revision. */
    ANSWER_RECOMMENDATIONS("tl-answer-recommendations"),

    /** Home recommendations bound to a topic-tree outline digest only. */
    HOME_RECOMMENDATIONS("tl-home-recommendations"),

    /** Context compression over older revisions, covering reasoning and answer together. */
    CONTEXT_COMPRESSION("tl-context-compression");

    /**
     * Canonical {@code MAJOR.MINOR.PATCH}. The version rule (§3.2) forbids app versions and
     * timestamps; leading zeros are refused too, so {@code 01.0.0}, {@code 1.0.00} and a
     * date-shaped {@code 2026.09.21} cannot masquerade as a policy version. Each component is capped
     * at 9 digits so a future caller parsing it into an int cannot silently overflow.
     */
    private static final Pattern SEMVER = Pattern.compile(
            "(?:0|[1-9][0-9]{0,8})\\.(?:0|[1-9][0-9]{0,8})\\.(?:0|[1-9][0-9]{0,8})");

    public final String policyPrefix;

    PromptPurpose(String policyPrefix) {
        this.policyPrefix = policyPrefix;
    }

    /**
     * @return true when {@code policyVersion} is a well-formed version owned by this purpose. A
     *         version of another purpose, a blank suffix or a non-semver suffix is refused, so a
     *         mis-wired caller fails closed instead of sending the wrong system prompt.
     */
    public boolean ownsVersion(String policyVersion) {
        if (policyVersion == null) {
            return false;
        }
        String prefix = policyPrefix + "/";
        if (!policyVersion.startsWith(prefix)) {
            return false;
        }
        return SEMVER.matcher(policyVersion.substring(prefix.length())).matches();
    }

    /** @return the purpose owning {@code policyVersion}, or null when no purpose owns it. */
    public static PromptPurpose ownerOf(String policyVersion) {
        for (PromptPurpose purpose : values()) {
            if (purpose.ownsVersion(policyVersion)) {
                return purpose;
            }
        }
        return null;
    }

    /**
     * @return the purpose whose {@link #policyPrefix} is exactly {@code policyPrefix}.
     *
     * <p>The reverse direction of {@link #policyPrefix}. A stored operation keeps the two halves of
     * the prompt contract in two columns — {@code prompt_purpose} holds the usage,
     * {@code prompt_policy_version} the version — so a row mapper has to read a bare prefix back into
     * a purpose. Deriving it from the version column instead would silently accept a row whose two
     * columns contradict each other.
     *
     * @throws IllegalArgumentException when no purpose uses that prefix, which is the same choice
     *         {@link GenerationModel#fromWireId} makes for an unknown wire id. Per the error-channel
     *         rule in {@link Guard}, a value no purpose can produce is a caller bug rather than a
     *         business condition: a row naming a purpose this build does not know is corrupt or from a
     *         newer schema, and failing loudly is what keeps it from being mapped onto another
     *         usage's prompt body.
     */
    public static PromptPurpose fromPrefix(String policyPrefix) {
        for (PromptPurpose purpose : values()) {
            if (purpose.policyPrefix.equals(policyPrefix)) {
                return purpose;
            }
        }
        throw new IllegalArgumentException("no prompt purpose uses the prefix <" + policyPrefix
                + ">; a stored purpose must be one of this build's wire prefixes");
    }

    /**
     * @return true when {@code policyVersion} is absent — null or blank. The single definition of
     *         "this request pinned no version", shared by every prompt-policy entry point so the
     *         test cannot drift from the report: a caller that asks {@link #isAbsent} and a caller
     *         that asks {@link #versionFailureFor} can never disagree about what "absent" means.
     */
    public static boolean isAbsent(String policyVersion) {
        return policyVersion == null || policyVersion.trim().isEmpty();
    }

    /**
     * The single decision point for "may this purpose be used with this policy version?".
     *
     * <p>It exists because the rule was previously written out twice — once in
     * {@code GenerationCommand} and once in {@code PromptSpec} — and the two copies disagreed about
     * which error channel a cross-purpose pairing belongs to (one returned a code, the other threw).
     * A round-1-independent audit of T-021 flagged exactly that. Two arguments that are individually
     * well formed but mutually inconsistent are a business refusal in this package, the same way
     * {@link DomainErrorCode#ANCHOR_INVALID} handles a quote whose length contradicts its offset
     * range, so {@code PromptSpec} and {@code GenerationCommand} now both route through here.
     *
     * <p>Note the difference from {@link #ownerOf}: this method asks about <em>one</em> purpose. A
     * version owned by a different purpose is a mismatch, not an unknown version. That is also why
     * {@link AnswerRevision#create} does <em>not</em> call this method: a revision records no declared
     * purpose, so its question is the weaker "does any usage own this version" and it asks
     * {@link #ownerOf}. Two different questions, two different predicates — not two copies of one.
     *
     * @param purpose the usage the caller declared; must not be null
     * @param policyVersion the pinned version
     * @return null when the pairing is usable, otherwise the failure the caller must return
     * @throws IllegalArgumentException if {@code purpose} is null — per {@link Guard}, an omitted
     *         argument is a caller bug, so it throws even though this method reports the version
     *         problem through {@link DomainErrorCode}
     */
    public static DomainError versionFailureFor(PromptPurpose purpose, String policyVersion) {
        Guard.object(purpose, "purpose");
        if (isAbsent(policyVersion)) {
            return DomainError.of(DomainErrorCode.PROMPT_POLICY_VERSION_REQUIRED,
                    "a generation request must pin the prompt policy version it will use; without it "
                            + "the resulting answer could not be reproduced or migrated (prompt "
                            + "design §3.2)");
        }
        if (!purpose.ownsVersion(policyVersion)) {
            return DomainError.of(DomainErrorCode.PROMPT_POLICY_VERSION_MISMATCH,
                    "policy version <" + policyVersion + "> is not owned by " + purpose
                            + " (expected " + purpose.policyPrefix + "/MAJOR.MINOR.PATCH); a "
                            + "declared usage "
                            + "and its pinned version must agree, otherwise the request assembler "
                            + "would send the wrong system prompt for that purpose");
        }
        return null;
    }
}
