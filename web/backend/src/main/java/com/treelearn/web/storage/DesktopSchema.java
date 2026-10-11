package com.treelearn.web.storage;

import com.treelearn.core.domain.GenerationModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The single source of truth for the <em>desktop</em> SQLite schema (task T-2026-10-10-009).
 *
 * <p>Authority: 《知芽 Web 本地版：桌面数据表准入记录 V1.0》
 * ({@code Docs/05-technical-design/TreeLearn_Web本地版_桌面数据表准入_V1.0.md}). The twelve
 * permanent tables are exactly the confirmed set; adding a thirteenth requires a new user
 * confirmation before it may appear here.
 *
 * <p>Semantic reference: Android {@code com.treelearn.data.db.TreeLearnSchema} at
 * {@code DATABASE_VERSION 5}. The desktop tables, columns, indexes, triggers, CHECKs and seed
 * values mirror that final shape name-for-name so both stores cannot quietly become two
 * differently named authorities for the same fact. What is deliberately <em>not</em> copied is the
 * V1 -&gt; V5 migration history: the desktop databases have no pre-existing rows to upgrade, so the
 * first published desktop version writes the final shape directly. Both engines are different
 * (Android {@code android.database.sqlite} vs desktop {@code org.xerial:sqlite-jdbc}) and their
 * evidence is not interchangeable.
 *
 * <p>Design decisions encoded here on purpose:
 * <ul>
 *   <li>DDL exists only in this file. {@code DdlLocationGuardTest} scans {@code src/main/java} and
 *       fails if {@code CREATE TABLE/INDEX/TRIGGER} or {@code ALTER TABLE} appears anywhere else,
 *       so no business path can run DDL (rules/sql.md).</li>
 *   <li>Time points are application-written UTC epoch millis {@code INTEGER}; the SQLite clock is
 *       deliberately never used.</li>
 *   <li>Immutability the product calls hard (stable answer revisions, birth snapshots, user message
 *       bodies, terminal operations) is enforced by triggers. Revisions and snapshots additionally
 *       refuse a direct {@code DELETE} while their owner row still exists, so a stray
 *       {@code DELETE FROM answer_revisions} cannot take a fork subtree with it.</li>
 *   <li>Because a desktop fresh install never runs {@code ALTER TABLE}, the §6.2.19
 *       {@code quoted_text} column is declared inside {@code CREATE TABLE}. Its invariant stays a
 *       trigger on both INSERT and UPDATE rather than a {@code CHECK}, for byte-for-byte parity with
 *       Android's statement text.</li>
 *   <li>"One active operation per branch" and "one root branch per topic" are <em>partial</em>
 *       unique indexes: terminal operations and child branches must remain insertable.</li>
 *   <li>{@code message_attachments.attachment_id} is a semantic reference with no enforced FK
 *       (a reference row must outlive a deleted attachment); see {@link MessageAttachments}.</li>
 * </ul>
 */
public final class DesktopSchema {

    /** The desktop schema version. Bumped only together with a new forward migration. */
    public static final int SCHEMA_VERSION = 1;

    private DesktopSchema() {
    }

    // ------------------------------------------------------------------
    // Table and column names, plus column declaration text shared by the assembled statements.
    // Namespaces only — never instantiated.
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
        public static final String LATEST_STABLE_SEQUENCE = "latest_stable_sequence";
        public static final String LAST_SEEN_STABLE_SEQUENCE = "last_seen_stable_sequence";
        public static final String READING_ANCHOR_MESSAGE_ID = "reading_anchor_message_id";
        public static final String READING_ANCHOR_REVISION_ID = "reading_anchor_revision_id";
        public static final String READING_LIST_ITEM_OFFSET = "reading_list_item_offset";
        public static final String READING_VIEWPORT_OFFSET = "reading_viewport_offset";
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
        public static final String USER_CONTENT = "user_content";
        public static final String QUOTED_TEXT = "quoted_text";
        public static final String CREATED_AT = "created_at";
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
        public static final String MODEL = "model";
        public static final String THINKING_ENABLED = "thinking_enabled";
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

    /** Provider configuration. No column may hold an API key; keys live in the desktop credential store. */
    public static final class Providers {
        public static final String TABLE = "providers", ID = "id", NAME = "name",
                BASE_URL = "base_url", ENABLED = "enabled", IS_BUILTIN = "is_builtin",
                CREATED_AT = "created_at", UPDATED_AT = "updated_at";
    }

    /** Model configuration, one row per provider-side wire id. */
    public static final class Models {
        public static final String TABLE = "models", ID = "id", PROVIDER_ID = "provider_id",
                MODEL_ID = "model_id", DISPLAY_NAME = "display_name", CREATED_AT = "created_at";
    }

    /** One stored attachment file; {@code content_hash} is the idempotent dedup key. */
    public static final class Attachments {
        public static final String TABLE = "attachments", ID = "id", CONTENT_HASH = "content_hash",
                DISPLAY_NAME = "display_name", MIME_TYPE = "mime_type",
                SIZE_BYTES = "size_bytes", EXTRACTED_TEXT = "extracted_text",
                PARSE_STATE = "parse_state", CREATED_AT = "created_at", UPDATED_AT = "updated_at";
    }

    /**
     * One message's reference to one attachment. {@code message_id} is an enforced FK that cascades
     * with its message; {@code attachment_id} is deliberately a semantic reference with no enforced
     * FK — a reference row must outlive a deleted attachment for the missing-annotation rule, which
     * an enforced FK would reject (准入记录 §2.11).
     */
    public static final class MessageAttachments {
        public static final String TABLE = "message_attachments", ID = "id",
                MESSAGE_ID = "message_id", ATTACHMENT_ID = "attachment_id",
                CREATED_AT = "created_at";
    }

    /** One learner's text mark on one answer (方案 V1.1 §3): PROSE and CELL share one table. */
    public static final class Annotations {
        public static final String TABLE = "message_annotations", ID = "id",
                MESSAGE_ID = "message_id", CONTAINER = "container", PROSE_INDEX = "prose_index",
                TABLE_INDEX = "table_index", ROW_INDEX = "row_index", COL_INDEX = "col_index",
                START_OFFSET = "start_offset", END_OFFSET = "end_offset", KIND = "kind",
                CONTENT_DIGEST = "content_digest", BODY = "body", CREATED_AT = "created_at",
                UPDATED_AT = "updated_at";
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
            Providers.TABLE,
            Models.TABLE,
            Attachments.TABLE,
            MessageAttachments.TABLE,
            Annotations.TABLE,
    };

    // ------------------------------------------------------------------
    // Column definition lists.
    // ------------------------------------------------------------------

    private static final String[] TOPICS_DEFINITIONS = {
            Topics.ID + " TEXT NOT NULL PRIMARY KEY",
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
            Branches.LATEST_STABLE_SEQUENCE + " INTEGER NOT NULL DEFAULT 0 CHECK ("
                    + Branches.LATEST_STABLE_SEQUENCE + " >= 0)",
            Branches.LAST_SEEN_STABLE_SEQUENCE + " INTEGER NOT NULL DEFAULT 0 CHECK ("
                    + Branches.LAST_SEEN_STABLE_SEQUENCE + " >= 0 AND "
                    + Branches.LAST_SEEN_STABLE_SEQUENCE + " <= " + Branches.LATEST_STABLE_SEQUENCE
                    + ")",
            Branches.READING_ANCHOR_MESSAGE_ID + " TEXT REFERENCES " + Messages.TABLE + "("
                    + Messages.ID + ") ON DELETE SET NULL",
            Branches.READING_ANCHOR_REVISION_ID + " TEXT REFERENCES " + AnswerRevisions.TABLE + "("
                    + AnswerRevisions.ID + ") ON DELETE SET NULL",
            Branches.READING_LIST_ITEM_OFFSET + " INTEGER",
            Branches.READING_VIEWPORT_OFFSET + " INTEGER",
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
            "CHECK (" + Branches.PARENT_ID + " IS NOT NULL OR (" + Branches.SOURCE_MESSAGE_ID
                    + " IS NULL AND " + Branches.SOURCE_REVISION_ID + " IS NULL AND "
                    + Branches.ANCHOR_START_INCLUSIVE + " IS NULL AND "
                    + Branches.ANCHOR_END_EXCLUSIVE + " IS NULL AND " + Branches.ANCHOR_QUOTE
                    + " IS NULL AND " + Branches.ANCHOR_HASH + " IS NULL AND "
                    + Branches.CONTEXT_SNAPSHOT_ID + " IS NULL))",
            "CHECK (" + Branches.PARENT_ID + " IS NULL OR (" + Branches.SOURCE_MESSAGE_ID
                    + " IS NOT NULL AND " + Branches.SOURCE_REVISION_ID + " IS NOT NULL))",
            "CHECK ((" + Branches.ANCHOR_START_INCLUSIVE + " IS NULL AND "
                    + Branches.ANCHOR_END_EXCLUSIVE + " IS NULL AND " + Branches.ANCHOR_QUOTE
                    + " IS NULL AND " + Branches.ANCHOR_HASH + " IS NULL) OR ("
                    + Branches.ANCHOR_START_INCLUSIVE + " IS NOT NULL AND "
                    + Branches.ANCHOR_END_EXCLUSIVE + " IS NOT NULL AND " + Branches.ANCHOR_QUOTE
                    + " IS NOT NULL AND " + Branches.ANCHOR_HASH + " IS NOT NULL AND "
                    + Branches.ANCHOR_START_INCLUSIVE + " < " + Branches.ANCHOR_END_EXCLUSIVE
                    + "))",
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
            // §6.2.19: declared directly here; the invariant is a trigger, see
            // MESSAGES_QUOTE_INVARIANT_CONDITION.
            Messages.QUOTED_TEXT + " TEXT",
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
            AnswerRevisions.ANSWER_CONTENT + " TEXT NOT NULL CHECK (length("
                    + AnswerRevisions.ANSWER_CONTENT + ") > 0)",
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
            // Polymorphic scope: the referenced table depends on kind (no FK can be declared).
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

    private static final String[] PROVIDERS_DEFINITIONS = {
            Providers.ID + " TEXT NOT NULL PRIMARY KEY",
            Providers.NAME + " TEXT NOT NULL UNIQUE",
            Providers.BASE_URL + " TEXT NOT NULL",
            Providers.ENABLED + " INTEGER NOT NULL CHECK (" + Providers.ENABLED + " IN (0,1))",
            Providers.IS_BUILTIN + " INTEGER NOT NULL CHECK (" + Providers.IS_BUILTIN + " IN (0,1))",
            Providers.CREATED_AT + " INTEGER NOT NULL", Providers.UPDATED_AT + " INTEGER NOT NULL",
    };

    private static final String[] MODELS_DEFINITIONS = {
            Models.ID + " TEXT NOT NULL PRIMARY KEY",
            Models.PROVIDER_ID + " TEXT NOT NULL REFERENCES " + Providers.TABLE + "("
                    + Providers.ID + ") ON DELETE CASCADE",
            Models.MODEL_ID + " TEXT NOT NULL", Models.DISPLAY_NAME + " TEXT NOT NULL",
            Models.CREATED_AT + " INTEGER NOT NULL",
    };

    private static final String[] ATTACHMENTS_DEFINITIONS = {
            Attachments.ID + " TEXT NOT NULL PRIMARY KEY",
            Attachments.CONTENT_HASH + " TEXT NOT NULL UNIQUE",
            Attachments.DISPLAY_NAME + " TEXT NOT NULL",
            Attachments.MIME_TYPE + " TEXT NOT NULL",
            Attachments.SIZE_BYTES + " INTEGER NOT NULL CHECK (" + Attachments.SIZE_BYTES
                    + " >= 0)",
            Attachments.EXTRACTED_TEXT + " TEXT",
            Attachments.PARSE_STATE + " TEXT NOT NULL CHECK (" + Attachments.PARSE_STATE
                    + " IN ('pending','parsed','parse_failed'))",
            Attachments.CREATED_AT + " INTEGER NOT NULL",
            Attachments.UPDATED_AT + " INTEGER NOT NULL",
    };

    private static final String[] MESSAGE_ATTACHMENTS_DEFINITIONS = {
            MessageAttachments.ID + " TEXT NOT NULL PRIMARY KEY",
            MessageAttachments.MESSAGE_ID + " TEXT NOT NULL REFERENCES " + Messages.TABLE + "("
                    + Messages.ID + ") ON DELETE CASCADE",
            // Semantic reference, deliberately not an enforced FK (see the class javadoc).
            MessageAttachments.ATTACHMENT_ID + " TEXT NOT NULL",
            MessageAttachments.CREATED_AT + " INTEGER NOT NULL",
    };

    private static final String[] ANNOTATIONS_DEFINITIONS = {
            Annotations.ID + " TEXT NOT NULL PRIMARY KEY",
            Annotations.MESSAGE_ID + " TEXT NOT NULL REFERENCES " + Messages.TABLE + "("
                    + Messages.ID + ") ON DELETE CASCADE",
            Annotations.CONTAINER + " TEXT NOT NULL",
            Annotations.PROSE_INDEX + " INTEGER NOT NULL",
            Annotations.TABLE_INDEX + " INTEGER NOT NULL",
            Annotations.ROW_INDEX + " INTEGER NOT NULL",
            Annotations.COL_INDEX + " INTEGER NOT NULL",
            Annotations.START_OFFSET + " INTEGER NOT NULL",
            Annotations.END_OFFSET + " INTEGER NOT NULL",
            Annotations.KIND + " TEXT NOT NULL",
            Annotations.CONTENT_DIGEST + " TEXT NOT NULL",
            Annotations.BODY + " TEXT NOT NULL DEFAULT ''",
            Annotations.CREATED_AT + " INTEGER NOT NULL",
            Annotations.UPDATED_AT + " INTEGER NOT NULL",
            "CHECK (" + Annotations.END_OFFSET + " > " + Annotations.START_OFFSET + ")",
            "CHECK (" + Annotations.KIND + " IN ('HIGHLIGHT','NOTE'))",
            "CHECK (" + Annotations.KIND + " <> 'NOTE' OR " + Annotations.BODY + " <> '')",
            "CHECK (" + Annotations.KIND + " <> 'HIGHLIGHT' OR " + Annotations.BODY + " = '')",
            "CHECK ((" + Annotations.CONTAINER + " = 'PROSE' AND " + Annotations.PROSE_INDEX
                    + " >= 0 AND " + Annotations.TABLE_INDEX + " = -1 AND " + Annotations.ROW_INDEX
                    + " = -1 AND " + Annotations.COL_INDEX + " = -1) OR ("
                    + Annotations.CONTAINER + " = 'CELL' AND " + Annotations.PROSE_INDEX
                    + " = -1 AND " + Annotations.TABLE_INDEX + " >= 0 AND " + Annotations.ROW_INDEX
                    + " >= 0 AND " + Annotations.COL_INDEX + " >= 0))",
    };

    // ------------------------------------------------------------------
    // Indexes.
    // ------------------------------------------------------------------

    public static final String INDEX_BRANCHES_TOPIC_PARENT = "idx_branches_topic_parent";
    public static final String INDEX_BRANCHES_SOURCE_REVISION = "idx_branches_source_revision";
    public static final String INDEX_BRANCHES_ARCHIVED = "idx_branches_archived";
    public static final String INDEX_BRANCHES_ROOT = "uq_branches_root";
    public static final String INDEX_MESSAGES_BRANCH_ORDINAL = "uq_messages_branch_ordinal";
    public static final String INDEX_REVISIONS_MESSAGE_NO = "uq_answer_revisions_message_no";
    public static final String INDEX_SNAPSHOTS_BRANCH = "uq_context_snapshots_branch";
    public static final String INDEX_ARTIFACTS_KIND_SCOPE = "idx_ai_artifacts_kind_scope";
    public static final String INDEX_OPERATIONS_ACTIVE = "uq_generation_operations_active";
    public static final String INDEX_MODELS_PROVIDER_MODEL = "uq_models_provider_model";
    public static final String INDEX_ATTACHMENTS_CONTENT_HASH = "uq_attachments_content_hash";
    public static final String INDEX_MESSAGE_ATTACHMENTS_MESSAGE = "idx_message_attachments_message";
    public static final String INDEX_MESSAGE_ATTACHMENTS_ATTACHMENT =
            "idx_message_attachments_attachment";
    public static final String INDEX_ANNOTATIONS_ANCHOR = "idx_annotations_anchor";
    public static final String INDEX_ANNOTATIONS_MESSAGE = "idx_annotations_message";

    /** The named indexes, in creation order. */
    public static final String[] INDEX_NAMES = {
            INDEX_BRANCHES_TOPIC_PARENT,
            INDEX_BRANCHES_SOURCE_REVISION,
            INDEX_BRANCHES_ARCHIVED,
            INDEX_BRANCHES_ROOT,
            INDEX_MESSAGES_BRANCH_ORDINAL,
            INDEX_REVISIONS_MESSAGE_NO,
            INDEX_SNAPSHOTS_BRANCH,
            INDEX_ARTIFACTS_KIND_SCOPE,
            INDEX_OPERATIONS_ACTIVE,
            INDEX_MODELS_PROVIDER_MODEL,
            INDEX_ATTACHMENTS_CONTENT_HASH,
            INDEX_MESSAGE_ATTACHMENTS_MESSAGE,
            INDEX_MESSAGE_ATTACHMENTS_ATTACHMENT,
            INDEX_ANNOTATIONS_ANCHOR,
            INDEX_ANNOTATIONS_MESSAGE,
    };

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
            "CREATE UNIQUE INDEX " + INDEX_REVISIONS_MESSAGE_NO + " ON " + AnswerRevisions.TABLE
                    + "(" + AnswerRevisions.MESSAGE_ID + ", " + AnswerRevisions.REVISION_NO + ")",
            "CREATE UNIQUE INDEX " + INDEX_SNAPSHOTS_BRANCH + " ON " + ContextSnapshots.TABLE + "("
                    + ContextSnapshots.BRANCH_ID + ")",
            "CREATE INDEX " + INDEX_ARTIFACTS_KIND_SCOPE + " ON " + AiArtifacts.TABLE + "("
                    + AiArtifacts.KIND + ", " + AiArtifacts.SCOPE_ID + ")",
            // Partial: at most one in-flight operation per branch; terminal rows are free.
            "CREATE UNIQUE INDEX " + INDEX_OPERATIONS_ACTIVE + " ON " + GenerationOperations.TABLE
                    + "(" + GenerationOperations.BRANCH_ID + ") WHERE " + GenerationOperations.STATE
                    + " IN (" + ACTIVE_STATES + ")",
            // Case-exact (BINARY) dedup of model ids within a provider: no NOCASE may be added.
            "CREATE UNIQUE INDEX " + INDEX_MODELS_PROVIDER_MODEL + " ON " + Models.TABLE + "("
                    + Models.PROVIDER_ID + ", " + Models.MODEL_ID + ")",
            "CREATE UNIQUE INDEX " + INDEX_ATTACHMENTS_CONTENT_HASH + " ON " + Attachments.TABLE
                    + "(" + Attachments.CONTENT_HASH + ")",
            "CREATE INDEX " + INDEX_MESSAGE_ATTACHMENTS_MESSAGE + " ON " + MessageAttachments.TABLE
                    + "(" + MessageAttachments.MESSAGE_ID + ")",
            "CREATE INDEX " + INDEX_MESSAGE_ATTACHMENTS_ATTACHMENT + " ON "
                    + MessageAttachments.TABLE + "(" + MessageAttachments.ATTACHMENT_ID + ")",
            "CREATE UNIQUE INDEX " + INDEX_ANNOTATIONS_ANCHOR + " ON " + Annotations.TABLE + "("
                    + Annotations.MESSAGE_ID + ", " + Annotations.CONTAINER + ", "
                    + Annotations.PROSE_INDEX + ", " + Annotations.TABLE_INDEX + ", "
                    + Annotations.ROW_INDEX + ", " + Annotations.COL_INDEX + ", " + Annotations.KIND
                    + ", " + Annotations.START_OFFSET + ", " + Annotations.END_OFFSET + ")",
            "CREATE INDEX " + INDEX_ANNOTATIONS_MESSAGE + " ON " + Annotations.TABLE + "("
                    + Annotations.MESSAGE_ID + ")",
    };

    // ------------------------------------------------------------------
    // Triggers.
    // ------------------------------------------------------------------

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
    public static final String TRIGGER_MESSAGES_QUOTE_INSERT = "trg_messages_quote_requires_user_row";
    public static final String TRIGGER_MESSAGES_QUOTE_UPDATE =
            "trg_messages_quote_requires_user_row_on_update";

    /** The invariant triggers, in creation order. */
    public static final String[] TRIGGER_NAMES = {
            TRIGGER_REVISIONS_IMMUTABLE,
            TRIGGER_REVISIONS_NO_DIRECT_DELETE,
            TRIGGER_SNAPSHOTS_IMMUTABLE,
            TRIGGER_SNAPSHOTS_NO_DIRECT_DELETE,
            TRIGGER_OPERATIONS_TERMINAL_IMMUTABLE,
            TRIGGER_MESSAGES_USER_CONTENT_IMMUTABLE,
            TRIGGER_MESSAGES_QUOTE_INSERT,
            TRIGGER_MESSAGES_QUOTE_UPDATE,
    };

    /** The one text of the §6.2.19 quote invariant, shared by both write events. */
    private static final String MESSAGES_QUOTE_INVARIANT_CONDITION =
            "NEW." + Messages.QUOTED_TEXT + " IS NOT NULL AND (NEW." + Messages.ROLE
                    + " <> 'USER' OR length(NEW." + Messages.QUOTED_TEXT + ") = 0)";

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
            quoteInvariantStatement(TRIGGER_MESSAGES_QUOTE_INSERT, "INSERT"),
            quoteInvariantStatement(TRIGGER_MESSAGES_QUOTE_UPDATE, "UPDATE"),
    };

    private static String quoteInvariantStatement(String trigger, String event) {
        return "CREATE TRIGGER " + trigger + " BEFORE " + event + " ON " + Messages.TABLE
                + " WHEN " + MESSAGES_QUOTE_INVARIANT_CONDITION
                + " BEGIN SELECT RAISE(ABORT, 'a quote may only be carried by a non-empty USER"
                + " message'); END";
    }

    // ------------------------------------------------------------------
    // Builtin DeepSeek provider seed (SPEC-2026-09-23 迁移).
    // ------------------------------------------------------------------

    /** Fixed synthetic UUIDs, so a desktop credential alias is stable. */
    public static final String BUILTIN_DEEPSEEK_PROVIDER_ID =
            "00000000-0000-0000-0000-000000000001";
    public static final String BUILTIN_DEEPSEEK_DEFAULT_MODEL_ID =
            "00000000-0000-0000-0000-000000000002";
    public static final String BUILTIN_DEEPSEEK_NAME = "DeepSeek";
    public static final String BUILTIN_DEEPSEEK_BASE_URL = "https://api.deepseek.com";

    /**
     * The seed's timestamps are a fixed release constant (2026-09-23T00:00:00Z), not a clock read:
     * application-written UTC epoch millis that keep the migration deterministic and the builtin
     * record oldest in creation order.
     */
    public static final long BUILTIN_SEED_EPOCH_MILLIS = 1_790_121_600_000L;

    public static final List<String> SEED_STATEMENTS =
            Collections.unmodifiableList(Arrays.asList(
                    "INSERT OR IGNORE INTO " + Providers.TABLE + "(" + Providers.ID + ", "
                            + Providers.NAME + ", " + Providers.BASE_URL + ", " + Providers.ENABLED
                            + ", " + Providers.IS_BUILTIN + ", " + Providers.CREATED_AT + ", "
                            + Providers.UPDATED_AT + ") VALUES ('" + BUILTIN_DEEPSEEK_PROVIDER_ID
                            + "', '" + BUILTIN_DEEPSEEK_NAME + "', '" + BUILTIN_DEEPSEEK_BASE_URL
                            + "', 1, 1, " + BUILTIN_SEED_EPOCH_MILLIS + ", "
                            + BUILTIN_SEED_EPOCH_MILLIS + ")",
                    "INSERT OR IGNORE INTO " + Models.TABLE + "(" + Models.ID + ", "
                            + Models.PROVIDER_ID + ", " + Models.MODEL_ID + ", "
                            + Models.DISPLAY_NAME + ", " + Models.CREATED_AT + ") VALUES ('"
                            + BUILTIN_DEEPSEEK_DEFAULT_MODEL_ID + "', '"
                            + BUILTIN_DEEPSEEK_PROVIDER_ID + "', '" + GenerationModel.FLASH.wireId
                            + "', '" + GenerationModel.FLASH.wireId + "', "
                            + BUILTIN_SEED_EPOCH_MILLIS + ")"));

    // ------------------------------------------------------------------
    // Assembled statement lists.
    // ------------------------------------------------------------------

    public static final List<String> CREATE_TABLE_STATEMENTS =
            Collections.unmodifiableList(Arrays.asList(
                    createTable(Topics.TABLE, TOPICS_DEFINITIONS),
                    createTable(Branches.TABLE, BRANCHES_DEFINITIONS),
                    createTable(Messages.TABLE, MESSAGES_DEFINITIONS),
                    createTable(AnswerRevisions.TABLE, ANSWER_REVISIONS_DEFINITIONS),
                    createTable(GenerationOperations.TABLE, GENERATION_OPERATIONS_DEFINITIONS),
                    createTable(ContextSnapshots.TABLE, CONTEXT_SNAPSHOTS_DEFINITIONS),
                    createTable(AiArtifacts.TABLE, AI_ARTIFACTS_DEFINITIONS),
                    createTable(Providers.TABLE, PROVIDERS_DEFINITIONS),
                    createTable(Models.TABLE, MODELS_DEFINITIONS),
                    createTable(Attachments.TABLE, ATTACHMENTS_DEFINITIONS),
                    createTable(MessageAttachments.TABLE, MESSAGE_ATTACHMENTS_DEFINITIONS),
                    createTable(Annotations.TABLE, ANNOTATIONS_DEFINITIONS)));

    public static final List<String> CREATE_INDEX_STATEMENTS =
            Collections.unmodifiableList(Arrays.asList(INDEX_STATEMENTS));

    public static final List<String> CREATE_TRIGGER_STATEMENTS =
            Collections.unmodifiableList(Arrays.asList(TRIGGER_STATEMENTS));

    /**
     * Everything a fresh install must execute, in order: tables, then indexes, then triggers, then
     * the builtin seed. The V0 -&gt; V1 migration iterates exactly this list.
     */
    public static final List<String> CREATE_STATEMENTS = createStatements();

    private static List<String> createStatements() {
        List<String> statements = new ArrayList<>(CREATE_TABLE_STATEMENTS);
        statements.addAll(CREATE_INDEX_STATEMENTS);
        statements.addAll(CREATE_TRIGGER_STATEMENTS);
        statements.addAll(SEED_STATEMENTS);
        return Collections.unmodifiableList(statements);
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
