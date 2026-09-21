package com.treelearn.core.domain;

/**
 * Where inside a source answer a child branch was forked from (technical design 5.2).
 *
 * <p>Two locating strategies are stored together on purpose. Character offsets address the stable
 * answer sequence and are the fast path; {@code quote} plus {@code hash} is the fallback used when
 * rendering differences or a compatibility migration invalidate the offsets. A fork of the whole
 * answer carries no offsets — but even then the revision reference on the parent
 * {@link BranchSource} stays mandatory.
 */
public final class BranchAnchor {

    /** Inclusive character offset into the source revision's answer; null for a whole-answer fork. */
    public final Integer startInclusive;

    /** Exclusive character offset into the source revision's answer; null for a whole-answer fork. */
    public final Integer endExclusive;

    /** Exact source text of the anchored range; null for a whole-answer fork. */
    public final String quote;

    /** Digest of {@link #quote}; null for a whole-answer fork. */
    public final String hash;

    private BranchAnchor(Integer startInclusive, Integer endExclusive, String quote, String hash) {
        this.startInclusive = startInclusive;
        this.endExclusive = endExclusive;
        this.quote = quote;
        this.hash = hash;
    }

    public static BranchAnchor wholeAnswer() {
        return new BranchAnchor(null, null, null, null);
    }

    /**
     * Creates a ranged anchor.
     *
     * @return a failure with {@link DomainErrorCode#ANCHOR_INVALID} when the range is malformed or
     *         the quote/hash fallback is missing; a ranged anchor without a fallback would silently
     *         become an unrestorable provenance record.
     */
    public static DomainResult<BranchAnchor> ofRange(
            int startInclusive, int endExclusive, String quote, String hash) {
        if (startInclusive < 0) {
            return DomainResult.fail(DomainErrorCode.ANCHOR_INVALID,
                    "anchor start must not be negative but was " + startInclusive);
        }
        if (endExclusive <= startInclusive) {
            return DomainResult.fail(DomainErrorCode.ANCHOR_INVALID,
                    "anchor end must be greater than start but was [" + startInclusive + ", " + endExclusive + ")");
        }
        if (quote == null || quote.isEmpty()) {
            return DomainResult.fail(DomainErrorCode.ANCHOR_INVALID,
                    "a ranged anchor requires a non-empty quote for offset fallback");
        }
        if (hash == null || hash.isEmpty()) {
            return DomainResult.fail(DomainErrorCode.ANCHOR_INVALID,
                    "a ranged anchor requires a non-empty hash for offset fallback");
        }
        // The two locators must describe the same range. Without this check a caller could persist a
        // quote that does not match its offsets, and the fallback would silently disagree with the
        // primary locator (round-3-independent audit finding).
        int span = endExclusive - startInclusive;
        if (quote.length() != span) {
            return DomainResult.fail(DomainErrorCode.ANCHOR_INVALID,
                    "the quote must be exactly the anchored range: offsets span " + span
                            + " characters but the quote is " + quote.length());
        }
        return DomainResult.ok(new BranchAnchor(startInclusive, endExclusive, quote, hash));
    }

    public boolean isWholeAnswer() {
        return startInclusive == null;
    }

    @Override
    public String toString() {
        return isWholeAnswer()
                ? "BranchAnchor.wholeAnswer"
                : "BranchAnchor[" + startInclusive + ", " + endExclusive + ")";
    }
}
