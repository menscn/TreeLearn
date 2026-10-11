package com.treelearn.web.storage;

import java.util.List;

/**
 * One forward, single-step desktop schema change (task T-2026-10-10-009).
 *
 * <p>Published steps are never rewritten: a released step is part of the contract with the
 * databases already on disk (rules/sql.md). A step returns its statements rather than executing
 * them, so {@link DatabaseMigrator} can run a whole chain inside one transaction and validate the
 * chain before running anything.
 */
public interface Migration {

    int fromVersion();

    int toVersion();

    List<String> statements();
}
