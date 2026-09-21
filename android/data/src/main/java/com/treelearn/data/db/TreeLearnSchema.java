package com.treelearn.data.db;

import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The single source of truth for the local SQLite schema.
 *
 * <p>Authority: 《知芽（TreeLearn）Android SQLite 建表入场确认清单 V1.0》§2/§3,
 * 技术方案 V1.0 §5.1 (message / attempt / stable revision), §5.2 (fork source and anchor),
 * §6 (tables), §11 (unread, reading position, rounds), and the field surface of the
 * {@code :core} entities those sections describe. The seven permanent tables are exactly the
 * confirmed set; a future eighth table requires a new user confirmation before it may appear here.
 *
 * <p>Every DDL statement below is assembled from the column constants in the same file, so a
 * renamed constant cannot drift away from the statements that use it. DAO code must reference
 * these constants instead of literal table or column names.
 *
 * <p>Column names follow the confirmed design vocabulary on purpose
 * ({@code latest_stable_sequence} / {@code last_seen_stable_sequence} from §11,
 * {@code active_revision_id} from §5.1) so the store cannot quietly become a second, differently
 * named authority for the same fact.
 *
 * <p>Design decisions encoded here on purpose:
 * <ul>
 *   <li>DDL exists only in {@code onCreate}/{@code onUpgrade} via these lists — no business path
 *       may run DDL (rules/sql.md).</li>
 *   <li>Time points are application-written UTC epoch millis {@code INTEGER}; the SQLite clock is
 *       deliberately never used.</li>
 *   <li>Immutability that the product contract calls hard (stable answer revisions, birth
 *       snapshots, user message bodies, terminal operations) is enforced by triggers, so a buggy
 *       DAO cannot silently rewrite history. Revisions and snapshots additionally refuse a direct
 *       {@code DELETE}: they may only disappear together with their owner row, so a stray
 *       {@code DELETE FROM answer_revisions} cannot take a user's fork subtree with it.</li>
 *   <li>"One active operation per branch" and "one root branch per topic" are <em>partial</em>
 *       unique indexes: terminal operations and child branches must remain insertable.</li>
 *   <li>Unread state has exactly one representation: the pair
 *       {@code latest_stable_sequence} / {@code last_seen_stable_sequence}. §11 forbids storing an
 *       independent unread authority, so there is no unread counter and no unread ordinal column.</li>
 *   <li>Prompt storage is limited to the stable {@code prompt_purpose} /
 *       {@code prompt_policy_version} pair. No column may hold the system prompt body.</li>
 * </ul>
 */
public final class TreeLearnSchema {

    /** Bumped only together with a new forward migration. */
    public static final int DATABASE_VERSION = 1;

    private TreeLearnSchema() {
    }

    // ------------------------------------------------------------------
    // Table and column names. Namespaces only — never instantiated.
    // ------------------------------------------------------------------

    public static final class Topics {
        public static final String TABLE = "topics";
        public static final String ID = "id";
        public static final String LAST_VISITED_BRANCH_ID = "last_visited_branch_id";
        public static final String CREATED_AT = "created_at";
        public static final String UPDATED_AT = "updated_at";
    }

    public static final class Branches {
        public static final String TABLE = "branches";
        public static final String ID = "id";
        public static final String TOPIC_ID = "topic_id";
        public static final String PARENT_ID = "parent_id";
        public static final String TITLE = "title";
        public static final String CREATED_AT = "created_at";
        public static final String UPDATED_AT = "updated_at";
        public static final String ARCHIVED_AT = "archived_at";
        public static final String COLLAPSED = "collapsed";
        /** §11: incremented when the branch gains a new stable answer; unread is derived from the pair. */
        public static final String LATEST_STABLE_SEQUENCE = "latest_stable_sequence";
        public static final String LAST_SEEN_STABLE_SEQUENCE = "last_seen_stable_sequence";
        /** §11 semantic reading position: never an absolute scroll offset. Offsets are integer px. */
        public static final String READING_ANCHOR_MESSAGE_ID = "reading_anchor_message_id";
        public static final String READING_ANCHOR_REVISION_ID = "reading_anchor_revision_id";
        public static final String READING_LIST_ITEM_OFFSET = "reading_list_item_offset";
        public static final String READING_VIEWPORT_OFFSET = "reading_viewport_offset";
        /** §5.2 fork provenance: only meaningful for a non-root branch. */
        public static final String SOURCE_MESSAGE_ID = "source_message_id";
        public static final String SOURCE_REVISION_ID = "source_revision_id";
        public static final String ANCHOR_START_INCLUSIVE = "anchor_start_inclusive";
        public static final String ANCHOR_END_EXCLUSIVE = "anchor_end_exclusive";
        public static final String ANCHOR_QUOTE = "anchor_quote";
        public static final String ANCHOR_HASH = "anchor_hash";
        public static final String CONTEXT_SNAPSHOT_ID = "context_snapshot_id";
    }

    public static final class Messages {
        public static final String TABLE = "messages";
        public static final String ID = "id";
        public static final String BRANCH_ID = "branch_id";
        public static final String ORDINAL = "ordinal";
        public static final String ROLE = "role";
        /** §5.1: only a USER row carries content; an AI row is a pure logical position. */
        public static final String USER_CONTENT = "user_content";
        public static final String CREATED_AT = "created_at";
        /** §5.1: the AI row holds the currently displayed revision and attempt references. */
        public static final String ACTIVE_REVISION_ID = "active_revision_id";
        public static final String ACTIVE_OPERATION_ID = "active_operation_id";
    }

    public static final class AnswerRevisions {
        public static final String TABLE = "answer_revisions";
        public static final String ID = "id";
        public static final String MESSAGE_ID = "message_id";
        public static final String REVISION_NO = "revision_no";
        public static final String REASONING_CONTENT = "reasoning_content";
        public static final String ANSWER_CONTENT = "answer_content";
        /** §5.1: a revision stores the model and thinking mode that produced it. */
        public static final String MODEL = "model";
        public static final String THINKING_ENABLED = "thinking_enabled";
        /** The purpose is recoverable from this version's prefix via PromptPurpose.ownerOf. */
        public static final String PROMPT_POLICY_VERSION = "prompt_policy_version";
        public static final String CREATED_AT = "created_at";
    }

    public static final class GenerationOperations {
        public static final String TABLE = "generation_operations";
        public static final String ID = "id";
        public static final String BRANCH_ID = "branch_id";
        public static final String MESSAGE_ID = "message_id";
        public static final String COMMAND_ID = "command_id";
        public static final String PROMPT_PURPOSE = "prompt_purpose";
        public static final String MODEL = "model";
        public static final String THINKING_ENABLED = "thinking_enabled";
        public static final String PROMPT_POLICY_VERSION = "prompt_policy_version";
        public static final String STATE = "state";
        public static final String PARTIAL_REASONING = "partial_reasoning";
        public static final String PARTIAL_ANSWER = "partial_answer";
        public static final String ERROR_CODE = "error_code";
        public static final String CREATED_AT = "created_at";
        public static final String UPDATED_AT = "updated_at";
    }

    public static final class ContextSnapshots {
        public static final String TABLE = "context_snapshots";
        public static final String ID = "id";
        public static final String BRANCH_ID = "branch_id";
        public static final String SCHEMA_VERSION = "schema_version";
        public static final String DIGEST = "digest";
        public static final String PAYLOAD = "payload";
        public static final String CREATED_AT = "created_at";
    }

    public static final class AiArtifacts {
        public static final String TABLE = "ai_artifacts";
        public static final String ID = "id";
        public static final String KIND = "kind";
        public static final String SCOPE_ID = "scope_id";
        public static final String PROMPT_PURPOSE = "prompt_purpose";
        public static final String PROMPT_POLICY_VERSION = "prompt_policy_version";
        public static final String PAYLOAD = "payload";
        public static final String BASED_ON_REVISION_ID = "based_on_revision_id";
        public static final String INPUT_DIGEST = "input_digest";
        public static final String CREATED_AT = "created_at";
        public static final String UPDATED_AT = "updated_at";
    }

    /** The confirmed permanent tables, in creation order. */
    public static final String[] TABLE_NAMES = {
            Topics.TABLE,
            Branches.TABLE,
            Messages.TABLE,
            AnswerRevisions.TABLE,
            GenerationOperations.TABLE,
            ContextSnapshots.TABLE,
            AiArtifacts.TABLE,
    };

    // ------------------------------------------------------------------
    // DDL.
    // ------------------------------------------------------------------

    private static final String[] TOPICS_DEFINITIONS = {
            Topics.ID + " TEXT NOT NULL PRIMARY KEY",
            // Deleting the last visited branch must not delete the topic itself.
            Topics.LAST_VISITED_BRANCH_ID + " TEXT REFERENCES " + Branches.TABLE + "("
                    + Branches.ID + ") ON DELETE SET NULL",
            Topics.CREATED_AT + " INTEGER NOT NULL",
            Topics.UPDATED_AT + " INTEGER NOT NULL",
    };

    private static final String[] BRANCHES_DEFINITIONS = {
            Branches.ID + " TEXT NOT NULL PRIMARY KEY",
            Branches.TOPIC_ID + " TEXT NOT NULL REFERENCES " + Topics.TABLE + "(" + Topics.ID
                    + ") ON DELETE CASCADE",
            Branches.PARENT_ID + " TEXT REFERENCES " + Branches.TABLE + "(" + Branches.ID
                    + ") ON DELETE CASCADE",
            Branches.TITLE + " TEXT",
            Branches.CREATED_AT + " INTEGER NOT NULL",
            Branches.UPDATED_AT + " INTEGER NOT NULL",
            Branches.ARCHIVED_AT + " INTEGER",
            Branches.COLLAPSED + " INTEGER NOT NULL DEFAULT 0 CHECK (" + Branches.COLLAPSED
                    + " IN (0,1))",
            // §11: unread is derived from these two, never from a separate counter.
            Branches.LATEST_STABLE_SEQUENCE + " INTEGER NOT NULL DEFAULT 0 CHECK ("
                    + Branches.LATEST_STABLE_SEQUENCE + " >= 0)",
            Branches.LAST_SEEN_STABLE_SEQUENCE + " INTEGER NOT NULL DEFAULT 0 CHECK ("
                    + Branches.LAST_SEEN_STABLE_SEQUENCE + " >= 0 AND "
                    + Branches.LAST_SEEN_STABLE_SEQUENCE + " <= " + Branches.LATEST_STABLE_SEQUENCE
                    + ")",
            // §11 semantic reading position. A deleted anchor clears the position instead of
            // leaving a pointer at a row that no longer exists.
            Branches.READING_ANCHOR_MESSAGE_ID + " TEXT REFERENCES " + Messages.TABLE + "("
                    + Messages.ID + ") ON DELETE SET NULL",
            Branches.READING_ANCHOR_REVISION_ID + " TEXT REFERENCES " + AnswerRevisions.TABLE + "("
                    + AnswerRevisions.ID + ") ON DELETE SET NULL",
            Branches.READING_LIST_ITEM_OFFSET + " INTEGER",
            Branches.READING_VIEWPORT_OFFSET + " INTEGER",
            // Fork provenance. Deleting a source deletes the subtree that anchors on it, which is
            // the same semantics as "permanently delete this subtree" (§7).
            Branches.SOURCE_MESSAGE_ID + " TEXT REFERENCES " + Messages.TABLE + "(" + Messages.ID
                    + ") ON DELETE CASCADE",
            Branches.SOURCE_REVISION_ID + " TEXT REFERENCES " + AnswerRevisions.TABLE + "("
                    + AnswerRevisions.ID + ") ON DELETE CASCADE",
            Branches.ANCHOR_START_INCLUSIVE + " INTEGER",
            Branches.ANCHOR_END_EXCLUSIVE + " INTEGER",
            Branches.ANCHOR_QUOTE + " TEXT",
            Branches.ANCHOR_HASH + " TEXT",
            Branches.CONTEXT_SNAPSHOT_ID + " TEXT REFERENCES " + ContextSnapshots.TABLE + "("
                    + ContextSnapshots.ID + ") ON DELETE CASCADE",
            // A root branch must not carry any fork provenance.
            "CHECK (" + Branches.PARENT_ID + " IS NOT NULL OR (" + Branches.SOURCE_MESSAGE_ID
                    + " IS NULL AND " + Branches.SOURCE_REVISION_ID + " IS NULL AND "
                    + Branches.ANCHOR_START_INCLUSIVE + " IS NULL AND "
                    + Branches.ANCHOR_END_EXCLUSIVE + " IS NULL AND " + Branches.ANCHOR_QUOTE
                    + " IS NULL AND " + Branches.ANCHOR_HASH + " IS NULL AND "
                    + Branches.CONTEXT_SNAPSHOT_ID + " IS NULL))",
            // A non-root branch must always carry its source version and anchor hash
            // (§5.2: 整条回答分叉时偏移为空，但版本引用仍必填).
            "CHECK (" + Branches.PARENT_ID + " IS NULL OR (" + Branches.SOURCE_MESSAGE_ID
                    + " IS NOT NULL AND " + Branches.SOURCE_REVISION_ID + " IS NOT NULL AND "
                    + Branches.ANCHOR_HASH + " IS NOT NULL))",
            "CHECK (" + Branches.ANCHOR_START_INCLUSIVE + " IS NULL OR "
                    + Branches.ANCHOR_END_EXCLUSIVE + " IS NULL OR "
                    + Branches.ANCHOR_START_INCLUSIVE + " <= " + Branches.ANCHOR_END_EXCLUSIVE
                    + ")",
    };

    private static final String[] MESSAGES_DEFINITIONS = {
            Messages.ID + " TEXT NOT NULL PRIMARY KEY",
            Messages.BRANCH_ID + " TEXT NOT NULL REFERENCES " + Branches.TABLE + "(" + Branches.ID
                    + ") ON DELETE CASCADE",
            Messages.ORDINAL + " INTEGER NOT NULL CHECK (" + Messages.ORDINAL + " >= 0)",
            Messages.ROLE + " TEXT NOT NULL CHECK (" + Messages.ROLE + " IN ('USER','AI'))",
            Messages.USER_CONTENT + " TEXT",
            Messages.CREATED_AT + " INTEGER NOT NULL",
            Messages.ACTIVE_REVISION_ID + " TEXT REFERENCES " + AnswerRevisions.TABLE + "("
                    + AnswerRevisions.ID + ") ON DELETE SET NULL",
            Messages.ACTIVE_OPERATION_ID + " TEXT REFERENCES " + GenerationOperations.TABLE + "("
                    + GenerationOperations.ID + ") ON DELETE SET NULL",
            // §5.1: a USER row is content, an AI row is a logical position without content.
            "CHECK (" + Messages.ROLE + " = 'AI' OR (" + Messages.USER_CONTENT
                    + " IS NOT NULL AND length(" + Messages.USER_CONTENT + ") > 0))",
            "CHECK (" + Messages.ROLE + " = 'USER' OR " + Messages.USER_CONTENT + " IS NULL)",
    };

    private static final String[] ANSWER_REVISIONS_DEFINITIONS = {
            AnswerRevisions.ID + " TEXT NOT NULL PRIMARY KEY",
            AnswerRevisions.MESSAGE_ID + " TEXT NOT NULL REFERENCES " + Messages.TABLE + "("
                    + Messages.ID + ") ON DELETE CASCADE",
            AnswerRevisions.REVISION_NO + " INTEGER NOT NULL CHECK (" + AnswerRevisions.REVISION_NO
                    + " > 0)",
            AnswerRevisions.REASONING_CONTENT + " TEXT NOT NULL DEFAULT ''",
            // Only a normally terminated, non-empty answer may become a stable revision (§9.3).
            AnswerRevisions.ANSWER_CONTENT + " TEXT NOT NULL CHECK (length("
                    + AnswerRevisions.ANSWER_CONTENT + ") > 0)",
            // §5.1: model and thinking mode are part of the revision, so a reloaded revision can
            // be rehydrated through AnswerRevision.restore without consulting the operation
            // (whose row may legitimately be cleaned up once it is terminal and unreferenced).
            AnswerRevisions.MODEL + " TEXT NOT NULL",
            AnswerRevisions.THINKING_ENABLED + " INTEGER NOT NULL CHECK ("
                    + AnswerRevisions.THINKING_ENABLED + " IN (0,1))",
            AnswerRevisions.PROMPT_POLICY_VERSION + " TEXT NOT NULL",
            AnswerRevisions.CREATED_AT + " INTEGER NOT NULL",
    };

    private static final String[] GENERATION_OPERATIONS_DEFINITIONS = {
            GenerationOperations.ID + " TEXT NOT NULL PRIMARY KEY",
            GenerationOperations.BRANCH_ID + " TEXT NOT NULL REFERENCES " + Branches.TABLE + "("
                    + Branches.ID + ") ON DELETE CASCADE",
            GenerationOperations.MESSAGE_ID + " TEXT NOT NULL REFERENCES " + Messages.TABLE + "("
                    + Messages.ID + ") ON DELETE CASCADE",
            GenerationOperations.COMMAND_ID + " TEXT NOT NULL",
            GenerationOperations.PROMPT_PURPOSE + " TEXT NOT NULL",
            GenerationOperations.MODEL + " TEXT NOT NULL",
            GenerationOperations.THINKING_ENABLED + " INTEGER NOT NULL CHECK ("
                    + GenerationOperations.THINKING_ENABLED + " IN (0,1))",
            GenerationOperations.PROMPT_POLICY_VERSION + " TEXT NOT NULL",
            GenerationOperations.STATE + " TEXT NOT NULL CHECK (" + GenerationOperations.STATE
                    + " IN ('STAGED','VALIDATING','SUBMITTING','STREAMING_REASONING',"
                    + "'STREAMING_ANSWER','STABLE','FAILED','INTERRUPTED','CANCELED'))",
            GenerationOperations.PARTIAL_REASONING + " TEXT NOT NULL DEFAULT ''",
            GenerationOperations.PARTIAL_ANSWER + " TEXT NOT NULL DEFAULT ''",
            GenerationOperations.ERROR_CODE + " TEXT",
            GenerationOperations.CREATED_AT + " INTEGER NOT NULL",
            GenerationOperations.UPDATED_AT + " INTEGER NOT NULL",
    };

    private static final String[] CONTEXT_SNAPSHOTS_DEFINITIONS = {
            ContextSnapshots.ID + " TEXT NOT NULL PRIMARY KEY",
            ContextSnapshots.BRANCH_ID + " TEXT NOT NULL REFERENCES " + Branches.TABLE + "("
                    + Branches.ID + ") ON DELETE CASCADE",
            ContextSnapshots.SCHEMA_VERSION + " INTEGER NOT NULL CHECK ("
                    + ContextSnapshots.SCHEMA_VERSION + " > 0)",
            ContextSnapshots.DIGEST + " TEXT NOT NULL",
            ContextSnapshots.PAYLOAD + " TEXT NOT NULL",
            ContextSnapshots.CREATED_AT + " INTEGER NOT NULL",
    };

    private static final String[] AI_ARTIFACTS_DEFINITIONS = {
            AiArtifacts.ID + " TEXT NOT NULL PRIMARY KEY",
            AiArtifacts.KIND + " TEXT NOT NULL",
            // Polymorphic scope on purpose: the referenced table depends on kind, so no FK can be
            // declared. Deleting a scope therefore does NOT delete its artifacts automatically;
            // that cleanup belongs to the use case that deletes the scope, and the store-level
            // limit is asserted explicitly in the instrumentation suite rather than assumed.
            AiArtifacts.SCOPE_ID + " TEXT NOT NULL",
            AiArtifacts.PROMPT_PURPOSE + " TEXT NOT NULL",
            AiArtifacts.PROMPT_POLICY_VERSION + " TEXT NOT NULL",
            AiArtifacts.PAYLOAD + " TEXT NOT NULL",
            AiArtifacts.BASED_ON_REVISION_ID + " TEXT REFERENCES " + AnswerRevisions.TABLE + "("
                    + AnswerRevisions.ID + ") ON DELETE SET NULL",
            AiArtifacts.INPUT_DIGEST + " TEXT NOT NULL",
            AiArtifacts.CREATED_AT + " INTEGER NOT NULL",
            AiArtifacts.UPDATED_AT + " INTEGER NOT NULL",
    };

    /** Index names, exposed so DBAs and guards can assert them without string literals. */
    public static final String INDEX_BRANCHES_TOPIC_PARENT = "idx_branches_topic_parent";
    public static final String INDEX_BRANCHES_SOURCE_REVISION = "idx_branches_source_revision";
    public static final String INDEX_BRANCHES_ARCHIVED = "idx_branches_archived";
    public static final String INDEX_BRANCHES_ROOT = "uq_branches_root";
    public static final String INDEX_MESSAGES_BRANCH_ORDINAL = "uq_messages_branch_ordinal";
    public static final String INDEX_REVISIONS_MESSAGE_NO = "uq_answer_revisions_message_no";
    public static final String INDEX_SNAPSHOTS_BRANCH = "uq_context_snapshots_branch";
    public static final String INDEX_ARTIFACTS_KIND_SCOPE = "idx_ai_artifacts_kind_scope";
    public static final String INDEX_OPERATIONS_ACTIVE = "uq_generation_operations_active";

    /** The only states that count as an in-flight generation (技术方案 §7). */
    private static final String ACTIVE_STATES =
            "'STAGED','VALIDATING','SUBMITTING','STREAMING_REASONING','STREAMING_ANSWER'";

    /** The only states a row may never leave (技术方案 §7). */
    private static final String TERMINAL_STATES = "'STABLE','FAILED','INTERRUPTED','CANCELED'";

    private static final String[] INDEX_STATEMENTS = {
            "CREATE INDEX " + INDEX_BRANCHES_TOPIC_PARENT + " ON " + Branches.TABLE + "("
                    + Branches.TOPIC_ID + ", " + Branches.PARENT_ID + ")",
            "CREATE INDEX " + INDEX_BRANCHES_SOURCE_REVISION + " ON " + Branches.TABLE + "("
                    + Branches.SOURCE_REVISION_ID + ")",
            "CREATE INDEX " + INDEX_BRANCHES_ARCHIVED + " ON " + Branches.TABLE + "("
                    + Branches.ARCHIVED_AT + ")",
            // Partial: exactly one root per topic, any number of children.
            "CREATE UNIQUE INDEX " + INDEX_BRANCHES_ROOT + " ON " + Branches.TABLE + "("
                    + Branches.TOPIC_ID + ") WHERE " + Branches.PARENT_ID + " IS NULL",
            "CREATE UNIQUE INDEX " + INDEX_MESSAGES_BRANCH_ORDINAL + " ON " + Messages.TABLE + "("
                    + Messages.BRANCH_ID + ", " + Messages.ORDINAL + ")",
            "CREATE UNIQUE INDEX " + INDEX_REVISIONS_MESSAGE_NO + " ON "
                    + AnswerRevisions.TABLE + "(" + AnswerRevisions.MESSAGE_ID + ", "
                    + AnswerRevisions.REVISION_NO + ")",
            "CREATE UNIQUE INDEX " + INDEX_SNAPSHOTS_BRANCH + " ON " + ContextSnapshots.TABLE + "("
                    + ContextSnapshots.BRANCH_ID + ")",
            "CREATE INDEX " + INDEX_ARTIFACTS_KIND_SCOPE + " ON " + AiArtifacts.TABLE + "("
                    + AiArtifacts.KIND + ", " + AiArtifacts.SCOPE_ID + ")",
            // Partial: at most one in-flight operation per branch; terminal rows are free.
            "CREATE UNIQUE INDEX " + INDEX_OPERATIONS_ACTIVE + " ON "
                    + GenerationOperations.TABLE + "(" + GenerationOperations.BRANCH_ID + ")"
                    + " WHERE " + GenerationOperations.STATE + " IN (" + ACTIVE_STATES + ")",
    };

    /**
     * Trigger names, exposed for reviewers and tests.
     *
     * <p>The two DELETE guards exist because the fork FK is {@code ON DELETE CASCADE}: without
     * them a stray {@code DELETE FROM answer_revisions} would silently take a user's fork subtree
     * with it, which is data loss far beyond the authorised "delete this subtree" action. The
     * guards abort only while the owning row still exists, so the legitimate cascade (owner row
     * deleted first, child rows follow) still works — and the instrumentation suite proves both
     * halves.
     */
    public static final String TRIGGER_REVISIONS_IMMUTABLE = "trg_answer_revisions_immutable";
    public static final String TRIGGER_REVISIONS_NO_DIRECT_DELETE =
            "trg_answer_revisions_no_direct_delete";
    public static final String TRIGGER_SNAPSHOTS_IMMUTABLE = "trg_context_snapshots_immutable";
    public static final String TRIGGER_SNAPSHOTS_NO_DIRECT_DELETE =
            "trg_context_snapshots_no_direct_delete";
    public static final String TRIGGER_OPERATIONS_TERMINAL_IMMUTABLE =
            "trg_generation_operations_terminal_immutable";
    public static final String TRIGGER_MESSAGES_USER_CONTENT_IMMUTABLE =
            "trg_messages_user_content_immutable";

    private static final String[] TRIGGER_STATEMENTS = {
            "CREATE TRIGGER " + TRIGGER_REVISIONS_IMMUTABLE + " BEFORE UPDATE ON "
                    + AnswerRevisions.TABLE
                    + " BEGIN SELECT RAISE(ABORT, 'answer_revisions rows are immutable'); END",
            "CREATE TRIGGER " + TRIGGER_REVISIONS_NO_DIRECT_DELETE + " BEFORE DELETE ON "
                    + AnswerRevisions.TABLE + " WHEN EXISTS (SELECT 1 FROM " + Messages.TABLE
                    + " WHERE " + Messages.ID + " = OLD." + AnswerRevisions.MESSAGE_ID + ")"
                    + " BEGIN SELECT RAISE(ABORT, 'a stable revision is deleted only together with"
                    + " its message, never on its own'); END",
            "CREATE TRIGGER " + TRIGGER_SNAPSHOTS_IMMUTABLE + " BEFORE UPDATE ON "
                    + ContextSnapshots.TABLE
                    + " BEGIN SELECT RAISE(ABORT, 'context_snapshots rows are immutable'); END",
            "CREATE TRIGGER " + TRIGGER_SNAPSHOTS_NO_DIRECT_DELETE + " BEFORE DELETE ON "
                    + ContextSnapshots.TABLE + " WHEN EXISTS (SELECT 1 FROM " + Branches.TABLE
                    + " WHERE " + Branches.ID + " = OLD." + ContextSnapshots.BRANCH_ID + ")"
                    + " BEGIN SELECT RAISE(ABORT, 'a birth snapshot is deleted only together with"
                    + " its branch, never on its own'); END",
            "CREATE TRIGGER " + TRIGGER_OPERATIONS_TERMINAL_IMMUTABLE + " BEFORE UPDATE ON "
                    + GenerationOperations.TABLE + " WHEN OLD." + GenerationOperations.STATE
                    + " IN (" + TERMINAL_STATES + ")"
                    + " BEGIN SELECT RAISE(ABORT, 'a terminal generation operation is final'); END",
            "CREATE TRIGGER " + TRIGGER_MESSAGES_USER_CONTENT_IMMUTABLE + " BEFORE UPDATE OF "
                    + Messages.USER_CONTENT + " ON " + Messages.TABLE + " WHEN OLD." + Messages.ROLE
                    + " = 'USER' AND NEW." + Messages.USER_CONTENT + " IS NOT OLD."
                    + Messages.USER_CONTENT
                    + " BEGIN SELECT RAISE(ABORT, 'user message content is immutable'); END",
    };

    /** The seven {@code CREATE TABLE} statements, in creation order. */
    public static final List<String> CREATE_TABLE_STATEMENTS =
            Collections.unmodifiableList(Arrays.asList(
                    createTable(Topics.TABLE, TOPICS_DEFINITIONS),
                    createTable(Branches.TABLE, BRANCHES_DEFINITIONS),
                    createTable(Messages.TABLE, MESSAGES_DEFINITIONS),
                    createTable(AnswerRevisions.TABLE, ANSWER_REVISIONS_DEFINITIONS),
                    createTable(GenerationOperations.TABLE, GENERATION_OPERATIONS_DEFINITIONS),
                    createTable(ContextSnapshots.TABLE, CONTEXT_SNAPSHOTS_DEFINITIONS),
                    createTable(AiArtifacts.TABLE, AI_ARTIFACTS_DEFINITIONS)));

    /** Index DDL for a fresh install, exposed for reviewers and tests. */
    public static final List<String> CREATE_INDEX_STATEMENTS =
            Collections.unmodifiableList(Arrays.asList(INDEX_STATEMENTS));

    /** Immutability triggers, exposed for reviewers and tests. */
    public static final List<String> CREATE_TRIGGER_STATEMENTS =
            Collections.unmodifiableList(Arrays.asList(TRIGGER_STATEMENTS));

    /**
     * Everything a fresh install must execute, in order: tables, then indexes, then triggers.
     * {@code onCreate} iterates exactly this list, so a statement cannot be added to the schema
     * and forgotten by the helper.
     */
    public static final List<String> CREATE_STATEMENTS = createStatements();

    private static List<String> createStatements() {
        List<String> statements = new ArrayList<>(CREATE_TABLE_STATEMENTS);
        statements.addAll(CREATE_INDEX_STATEMENTS);
        statements.addAll(CREATE_TRIGGER_STATEMENTS);
        return Collections.unmodifiableList(statements);
    }

    // ------------------------------------------------------------------
    // Forward migrations.
    // ------------------------------------------------------------------

    /** One forward, single-step schema change. Published steps are never rewritten. */
    public interface Migration {

        int fromVersion();

        int toVersion();

        void apply(SQLiteDatabase database);
    }

    /**
     * V1 is the first published version, so there is no predecessor to migrate from and this list
     * is intentionally empty. It is not a placeholder: {@code onUpgrade} delegates to
     * {@link #applyForwardMigrations}, which fails closed when a step is missing, and the
     * instrumentation suite proves the delegation and the fail-closed behaviour on a real device.
     * Adding version 2 therefore only means appending one migration here and bumping the constant.
     */
    public static final List<Migration> FORWARD_MIGRATIONS = Collections.emptyList();

    /**
     * Applies every registered step that lies inside {@code (oldVersion, newVersion]}.
     *
     * <p>Fails closed when any single step in that range is missing. Without this check a future
     * release that bumps {@link #DATABASE_VERSION} without registering the matching migration
     * would bump {@code user_version} anyway and leave a half-migrated database that reports
     * itself as current — exactly what rules/sql.md forbids.
     */
    public static void applyForwardMigrations(SQLiteDatabase database, int oldVersion,
                                              int newVersion) {
        if (newVersion < oldVersion) {
            throw new IllegalStateException("downgrade is not supported: " + oldVersion + " -> "
                    + newVersion);
        }
        for (int version = oldVersion; version < newVersion; version++) {
            Migration step = findMigrationFrom(version);
            if (step == null) {
                throw new IllegalStateException("no forward migration is registered from schema"
                        + " version " + version + " to " + (version + 1) + "; refusing to stamp "
                        + "user_version " + newVersion + " on a half-migrated database");
            }
            step.apply(database);
        }
    }

    private static Migration findMigrationFrom(int fromVersion) {
        for (Migration migration : FORWARD_MIGRATIONS) {
            if (migration.fromVersion() == fromVersion) {
                return migration;
            }
        }
        return null;
    }

    private static String createTable(String table, String[] definitions) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(table).append(" (");
        for (int i = 0; i < definitions.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(definitions[i]);
        }
        return sql.append(")").toString();
    }
}
