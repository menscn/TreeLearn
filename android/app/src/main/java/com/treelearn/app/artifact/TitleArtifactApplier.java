package com.treelearn.app.artifact;

import com.treelearn.app.artifact.ArtifactKind;
import com.treelearn.app.artifact.ArtifactPayloadCodec;
import com.treelearn.app.artifact.DerivedResult;

final class TitleArtifactApplier {
    private TitleArtifactApplier() {
    }

    static String titleOf(ArtifactKind kind, String payload) {
        if (kind != ArtifactKind.ROOT_TITLE && kind != ArtifactKind.BRANCH_TITLE) {
            return null;
        }
        DerivedResult parsed = ArtifactPayloadCodec.parse(kind, payload, Integer.MAX_VALUE);
        if (!parsed.isUsable() || parsed.payload().title == null) {
            return null;
        }
        String title = parsed.payload().title.trim();
        return title.isEmpty() ? null : title;
    }
}

