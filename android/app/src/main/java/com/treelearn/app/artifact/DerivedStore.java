package com.treelearn.app.artifact;

import com.treelearn.data.db.dao.AiArtifactDao;
import java.util.function.Supplier;

/**
 * The write-back port over {@code ai_artifacts} (technical design 10).
 *
 * <p>Two facts decide whether a derived result may be stored: the scope's <em>live</em> baseline and
 * the row already there. Both are read through this port, and both reads happen inside
 * {@link #inTransaction} together with the write, so "re-compare the current baseline and then
 * update" is one atomic step rather than a check that a late arrival can slip past.
 *
 * <p>{@link #liveBaseline} is a question only the scope's owner can answer — the newest stable
 * revision of a branch, whether a branch still exists, or the digest of a topic's current outline.
 * A null answer means the scope is gone, and a gone scope must never gain an artifact back: without
 * that answer, a callback that lands after the learner deleted the branch would re-create an orphan
 * row, because "nothing is stored yet" and "the scope was just retired" look identical from the
 * artifacts table alone.
 *
 * <p>{@link #write} owns the first-write/second-write rule of {@code AiArtifactDao}: the first
 * derivation of a pair inserts, and every later one replaces. It is only ever called after
 * {@link #liveBaseline} matched the candidate's baseline, so a replace here is a replace that was
 * just re-authorized.
 */
public interface DerivedStore {

    /** Runs {@code work} in one transaction, so its reads and its write commit or roll back together. */
    <T> T inTransaction(Supplier<T> work);

    /** @return the scope's live baseline, or null when the scope is gone */
    String liveBaseline(ArtifactKind kind, String scopeId);

    /** Stores {@code row}: inserting the pair's first artifact, replacing the row that is already there. */
    void write(AiArtifactDao.Row row);
}