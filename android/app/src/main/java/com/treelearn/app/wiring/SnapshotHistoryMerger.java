package com.treelearn.app.wiring;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ContextPlan;
import com.treelearn.core.domain.ContextSnapshot;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * T-064: the one merge between a branch's birth snapshot and its own rows.
 *
 * <p>A non-root branch's request history is its birth snapshot's sources - every learner message and
 * every stable version the fork was created from, in the order the fork pinned them - followed by the
 * branch's own entries (PRD §6.2.4 point 2). The references are resolved row by row through the
 * {@link Rows} port, so this type owns no database and a plain-JVM test can drive it; the store only
 * reads the snapshot row, decodes it through {@link ContextSnapshot#fromStored} and hands it here.
 *
 * <p><strong>Fail closed.</strong> The upstream background is the reason a child branch exists, so a
 * reference whose row is gone refuses the whole plan ({@link DomainErrorCode#CONTEXT_SOURCE_MISSING})
 * instead of being silently shortened - the same verdict a snapshot that fails its digest check gets.
 * A {@code SUMMARY} source is refused too ({@link DomainErrorCode#CONTEXT_SUMMARY_MISSING}): no read
 * path can produce a summary's content yet (T-007 deferral), and inventing one here would put words
 * in the upstream conversation's mouth. Both refusals surface to the learner as the STORAGE notice,
 * through {@link ChatStore.SnapshotUnreadable}.
 *
 * <p><strong>压缩口径（T-064 约束 ②）：</strong>前置 entry 是普通的 {@link ContextPlan.Entry}
 * （USER_MESSAGE / STABLE_REVISION），不引入新类别，因此 {@code ChatWiring.historyForRequest}
 * 的「最近十轮」压缩按原样把它们计入轮次统计——快照来源与本分支行在同一口径下，无处漂移。
 */
public final class SnapshotHistoryMerger {

    private SnapshotHistoryMerger() {
    }

    /** The rows a snapshot's references resolve against. */
    public interface Rows {

        /** @return the message row, or null when no row carries the id */
        Message message(String messageId);

        /** @return the stable revision row, or null when no row carries the id */
        AnswerRevision revision(String revisionId);
    }

    /**
     * Merges one decoded birth snapshot in front of a branch's own entries.
     *
     * @param snapshot the decoded snapshot, or null for a branch with none - the root branch's
     *        rows come back unchanged, which is the behavior this task must preserve byte for byte
     * @param branchRows the branch's own entries, already built in conversation order
     * @return the snapshot's sources as entries, in source order, followed by {@code branchRows};
     *         or a failure with {@link DomainErrorCode#CONTEXT_SOURCE_MISSING} or
     *         {@link DomainErrorCode#CONTEXT_SUMMARY_MISSING}
     */
    public static DomainResult<List<ContextPlan.Entry>> merge(ContextSnapshot snapshot,
            List<ContextPlan.Entry> branchRows, Rows rows) {
        if (snapshot == null) {
            return DomainResult.ok(new ArrayList<ContextPlan.Entry>(branchRows));
        }
        List<ContextPlan.Entry> entries = new ArrayList<ContextPlan.Entry>();
        for (ContextSnapshot.Source source : snapshot.sources) {
            if (source.kind == ContextSnapshot.SourceKind.USER_MESSAGE) {
                Message row = rows.message(source.refId);
                if (row == null || !row.isUser()) {
                    return DomainResult.fail(DomainErrorCode.CONTEXT_SOURCE_MISSING,
                            "the birth snapshot pins learner message " + source.refId
                                    + ", but no such user row exists any more");
                }
                entries.add(ContextPlan.Entry.userMessage(row.id, row.userContent));
            } else if (source.kind == ContextSnapshot.SourceKind.STABLE_REVISION) {
                AnswerRevision revision = rows.revision(source.refId);
                if (revision == null) {
                    return DomainResult.fail(DomainErrorCode.CONTEXT_SOURCE_MISSING,
                            "the birth snapshot pins stable revision " + source.refId
                                    + ", but no such revision row exists any more");
                }
                entries.add(ContextPlan.Entry.stableRevision(revision.id, revision.reasoningContent,
                        revision.answerContent));
            } else {
                return DomainResult.fail(DomainErrorCode.CONTEXT_SUMMARY_MISSING,
                        "the birth snapshot pins summary " + source.refId
                                + ", which no read path can resolve yet");
            }
        }
        entries.addAll(branchRows);
        return DomainResult.ok(entries);
    }
}
