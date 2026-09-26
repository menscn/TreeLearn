/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.core.domain.PromptPurpose
 */
package com.treelearn.app.artifact;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.PromptPurpose;

public enum ArtifactKind {
    ROOT_TITLE("root_title", PromptPurpose.ROOT_TITLE, Scope.BRANCH, false, false),
    BRANCH_TITLE("branch_title", PromptPurpose.BRANCH_TITLE, Scope.BRANCH, false, false),
    BRANCH_SUMMARY("branch_summary", PromptPurpose.BRANCH_SUMMARY, Scope.BRANCH, true, false),
    ANSWER_RECOMMENDATIONS("answer_recommendations", PromptPurpose.ANSWER_RECOMMENDATIONS, Scope.BRANCH, true, false),
    HOME_RECOMMENDATIONS("home_recommendations", PromptPurpose.HOME_RECOMMENDATIONS, Scope.TOPIC, false, false),
    CONTEXT_COMPRESSION("context_compression", PromptPurpose.CONTEXT_COMPRESSION, Scope.BRANCH, true, true);

    public final String wireId;
    public final PromptPurpose purpose;
    public final Scope scope;
    private final boolean revisionBound;
    private final boolean refusesEmptySummary;

    private ArtifactKind(String wireId, PromptPurpose purpose, Scope scope, boolean revisionBound, boolean refusesEmptySummary) {
        this.wireId = Guard.nonBlank((String)wireId, (String)"wireId");
        this.purpose = (PromptPurpose)Guard.object((Object)purpose, (String)"purpose");
        this.scope = (Scope)((Object)Guard.object((Object)((Object)scope), (String)"scope"));
        this.revisionBound = revisionBound;
        this.refusesEmptySummary = refusesEmptySummary;
    }

    public boolean revisionBound() {
        return this.revisionBound;
    }

    public boolean refusesEmptySummary() {
        return this.refusesEmptySummary;
    }

    public static ArtifactKind ofPurpose(PromptPurpose purpose) {
        Guard.object((Object)purpose, (String)"purpose");
        for (ArtifactKind kind : ArtifactKind.values()) {
            if (kind.purpose != purpose) continue;
            return kind;
        }
        throw new IllegalArgumentException(purpose + " owns no derived artifact kind" + (purpose == PromptPurpose.LEARNING_ANSWER ? "; LEARNING_ANSWER is the teaching usage and a derived artifact never borrows its body" : ""));
    }

    public static ArtifactKind fromWireId(String wireId) {
        for (ArtifactKind kind : ArtifactKind.values()) {
            if (!kind.wireId.equals(wireId)) continue;
            return kind;
        }
        throw new IllegalArgumentException("no artifact kind uses the wire id <" + wireId + ">; a stored kind must be one this build produces");
    }

    public static enum Scope {
        BRANCH,
        TOPIC;

    }
}

