package com.treelearn.app.artifact;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;

/**
 * The title an artifact row carries, read back at the one place that names the branch row itself.
 * Non-title kinds, unusable envelopes, and blank titles must all answer null so the store can
 * leave the branch row untouched instead of writing an empty name over a good one.
 */
public class TitleArtifactApplierTest {

    @Test
    public void rootTitlePayloadCarriesItsTitle() {
        String payload = ArtifactPayload
                .title(ArtifactKind.ROOT_TITLE, "a derived root title").canonicalJson();

        assertEquals("a derived root title",
                TitleArtifactApplier.titleOf(ArtifactKind.ROOT_TITLE, payload));
    }

    @Test
    public void branchTitlePayloadCarriesItsTitle() {
        String payload = ArtifactPayload
                .title(ArtifactKind.BRANCH_TITLE, "a derived branch title").canonicalJson();

        assertEquals("a derived branch title",
                TitleArtifactApplier.titleOf(ArtifactKind.BRANCH_TITLE, payload));
    }

    @Test
    public void nonTitleKindsNameNothing() {
        String payload = ArtifactPayload
                .summary(ArtifactKind.BRANCH_SUMMARY, "主线", Collections.singletonList("要点"))
                .canonicalJson();

        assertNull(TitleArtifactApplier.titleOf(ArtifactKind.BRANCH_SUMMARY, payload));
    }

    @Test
    public void unusableOrBlankEnvelopesNameNothing() {
        assertNull(TitleArtifactApplier.titleOf(ArtifactKind.ROOT_TITLE, "not json at all"));
        assertNull(TitleArtifactApplier.titleOf(ArtifactKind.ROOT_TITLE,
                ArtifactPayload.title(ArtifactKind.ROOT_TITLE, "   ").canonicalJson()));
    }
}
