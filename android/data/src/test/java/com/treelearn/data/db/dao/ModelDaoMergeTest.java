package com.treelearn.data.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * The T-2026-09-23-086 merge decision (SPEC-2026-09-23 技术规格, F8 口径): remote model ids are
 * compared to the stored ones <em>case-exactly</em>, only the missing ones are planned, duplicates
 * inside the remote list itself are silently skipped, and the plan never reduces the store — a
 * fetch that comes back shorter keeps every local entry, so a merge can only ever add rows.
 *
 * <p>Whether the planned rows really land in {@code models} under a live SQLite engine is proven on
 * a device by the instrumented conventions (rules/sql.md); this suite pins the pure decision the
 * DAO insert loop follows.
 */
public class ModelDaoMergeTest {

    @Test
    public void onlyIdsMissingFromTheStoreArePlannedInRemoteOrder() {
        List<String> plan = ModelDao.newModelIds(
                List.of("deepseek-chat", "deepseek-reasoner"),
                List.of("deepseek-reasoner", "deepseek-coder", "deepseek-chat"));

        assertEquals(List.of("deepseek-coder"), plan);
    }

    @Test
    public void theComparisonIsCaseExact() {
        List<String> plan = ModelDao.newModelIds(
                List.of("Model-X"), List.of("model-x", "MODEL-X", "Model-X"));

        assertEquals(List.of("model-x", "MODEL-X"), plan);
    }

    @Test
    public void duplicatesInsideTheRemoteListAreSilentlySkipped() {
        List<String> plan = ModelDao.newModelIds(
                List.of(), Arrays.asList("a", "b", "a", "b", "a"));

        assertEquals(List.of("a", "b"), plan);
    }

    @Test
    public void anEmptyRemoteListPlansNothingSoTheStoreIsNeverReduced() {
        assertTrue(ModelDao.newModelIds(List.of("kept-1", "kept-2"), List.of()).isEmpty());
    }

    @Test
    public void anEmptyStorePlansEveryRemoteId() {
        assertEquals(List.of("a", "b"), ModelDao.newModelIds(List.of(), List.of("a", "b")));
    }
}
