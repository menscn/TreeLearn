/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.ContextPlan$Entry
 *  com.treelearn.core.domain.ContextSnapshot
 *  com.treelearn.core.domain.ContextSnapshot$Source
 *  com.treelearn.core.domain.ContextSnapshot$SourceKind
 *  com.treelearn.core.domain.DomainErrorCode
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.Message
 */
package com.treelearn.app.wiring;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Message;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotHistoryMerger {
    private SnapshotHistoryMerger() {
    }

    public static DomainResult<List<ContextPlan.Entry>> merge(ContextSnapshot snapshot, List<ContextPlan.Entry> branchRows, Rows rows) {
        if (snapshot == null) {
            return DomainResult.ok(new ArrayList<ContextPlan.Entry>(branchRows));
        }
        ArrayList<ContextPlan.Entry> entries = new ArrayList<ContextPlan.Entry>();
        for (ContextSnapshot.Source source : snapshot.sources) {
            if (source.kind == ContextSnapshot.SourceKind.USER_MESSAGE) {
                Message row = rows.message(source.refId);
                if (row == null || !row.isUser()) {
                    return DomainResult.fail((DomainErrorCode)DomainErrorCode.CONTEXT_SOURCE_MISSING, (String)("the birth snapshot pins learner message " + source.refId + ", but no such user row exists any more"));
                }
                entries.add(ContextPlan.Entry.userMessage((String)row.id, (String)row.userContent));
                continue;
            }
            if (source.kind == ContextSnapshot.SourceKind.STABLE_REVISION) {
                AnswerRevision revision = rows.revision(source.refId);
                if (revision == null) {
                    return DomainResult.fail((DomainErrorCode)DomainErrorCode.CONTEXT_SOURCE_MISSING, (String)("the birth snapshot pins stable revision " + source.refId + ", but no such revision row exists any more"));
                }
                entries.add(ContextPlan.Entry.stableRevision((String)revision.id, (String)revision.reasoningContent, (String)revision.answerContent));
                continue;
            }
            return DomainResult.fail((DomainErrorCode)DomainErrorCode.CONTEXT_SUMMARY_MISSING, (String)("the birth snapshot pins summary " + source.refId + ", which no read path can resolve yet"));
        }
        entries.addAll(branchRows);
        return DomainResult.ok(entries);
    }

    public static interface Rows {
        public Message message(String var1);

        public AnswerRevision revision(String var1);
    }
}

