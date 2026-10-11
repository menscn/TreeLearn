package com.treelearn.web.storage;

import java.util.Collections;
import java.util.List;

/**
 * The registered forward migration steps for the desktop store (task T-2026-10-10-009).
 *
 * <p>V1 is the first published desktop version, so it writes the final shape directly: the single
 * {@code 0 -> 1} step is {@link DesktopSchema#CREATE_STATEMENTS}. A future version registers one
 * more {@link Migration} here; bumping {@link DesktopSchema#SCHEMA_VERSION} without a matching step
 * makes {@link DatabaseMigrator} refuse to stamp {@code user_version} instead of leaving a
 * half-migrated database that reports itself as current.
 */
public final class Migrations {

    private Migrations() {
    }

    /** The target schema version this build creates. */
    public static final int TARGET_VERSION = DesktopSchema.SCHEMA_VERSION;

    private static final List<Migration> FORWARD = Collections.unmodifiableList(List.of(
            new FullInstallV0ToV1()));

    /** The registered forward steps, in version order. */
    public static List<Migration> forward() {
        return FORWARD;
    }

    /** The single {@code 0 -> 1} step: executes {@link DesktopSchema#CREATE_STATEMENTS} in order. */
    private static final class FullInstallV0ToV1 implements Migration {

        @Override
        public int fromVersion() {
            return 0;
        }

        @Override
        public int toVersion() {
            return 1;
        }

        @Override
        public List<String> statements() {
            return DesktopSchema.CREATE_STATEMENTS;
        }
    }
}
