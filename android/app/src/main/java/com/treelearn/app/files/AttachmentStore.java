package com.treelearn.app.files;

import com.treelearn.core.domain.Guard;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.dao.AttachmentDao;

import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Set;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The attachment store (T-096): one private directory of content-hash-named files plus the
 * {@code attachments} / {@code message_attachments} rows that name and reference them.
 *
 * <p><strong>One file per content, whatever it is named.</strong> Registration hashes the bytes
 * (SHA-256) and resolves to the existing record when the content is already stored — a second
 * register of the same bytes never writes a second copy. The row's {@code content_hash} UNIQUE
 * constraint is the database-side backstop of the same rule.
 *
 * <p><strong>Deleting a record is not deleting a reference.</strong> A referenced file can be
 * deleted from the file manager: the physical file and the record row go, the reference rows
 * survive, and a reference whose record is gone reads as the missing annotation (SPEC-2026-09-23).
 * Only a message's own deletion takes its reference rows, through the schema's cascade.
 *
 * <p>This class is the only writer of the attachments directory; nothing else touches those files.
 */
public final class AttachmentStore {

    /** The parse states a record may carry; 096 registers {@code pending}, 097 owns the rest. */
    public static final String PARSE_STATE_PENDING = "pending";

    /** One manager-list entry: the record plus what is physically and referentially true of it. */
    public static final class Entry {

        public final String attachmentId;
        public final String displayName;
        public final String mimeType;
        public final long sizeBytes;
        public final String parseState;
        /** The physical file exists; a system-cleaned directory reports false, never silently. */
        public final boolean present;
        /** How many messages reference this record; the orphan scan's first half. */
        public final int referenceCount;

        Entry(String attachmentId, String displayName, String mimeType, long sizeBytes,
                String parseState, boolean present, int referenceCount) {
            this.attachmentId = attachmentId;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.parseState = parseState;
            this.present = present;
            this.referenceCount = referenceCount;
        }
    }

    /** One message's reference, resolved: present means record row and physical file both exist. */
    public static final class Ref {

        public final String attachmentId;
        /** The record's display name, or null once the record row is gone. */
        public final String displayName;
        public final boolean present;

        Ref(String attachmentId, String displayName, boolean present) {
            this.attachmentId = attachmentId;
            this.displayName = displayName;
            this.present = present;
        }
    }

    private final TreeLearnDatabaseHelper helper;
    private final File directory;

    public AttachmentStore(TreeLearnDatabaseHelper helper, File directory) {
        this.helper = Guard.object(helper, "helper");
        this.directory = Guard.object(directory, "directory");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("cannot create the attachments directory: " + directory);
        }
    }

    /** The attachments directory; exposed for tests and nothing else. */
    public File directory() {
        return directory;
    }

    /**
     * Registers one file's bytes under a user-visible name. Same bytes again resolve to the
     * existing record — the first display name wins, no second copy is written.
     *
     * @return the attachment id (existing or newly created)
     */
    public String register(String displayName, String mimeType, byte[] bytes, long now) {
        Guard.text(displayName, "displayName");
        Guard.text(mimeType, "mimeType");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
        String hash = sha256Hex(bytes);
        AttachmentDao.Record existing = AttachmentDao.findByContentHash(
                helper.getWritableDatabase(), hash);
        if (existing != null) {
            return existing.id;
        }
        String id = UUID.randomUUID().toString();
        File body = new File(directory, hash);
        writeAtomically(body, bytes);
        try {
            AttachmentDao.insert(helper.getWritableDatabase(), new AttachmentDao.Record(id, hash,
                    displayName, mimeType, bytes.length, null, PARSE_STATE_PENDING, now, now));
        } catch (RuntimeException failed) {
            // The row is the record; a row that failed to commit must not leave an orphan body.
            if (!body.delete() && body.exists()) {
                failed.addSuppressed(new IOException("cannot remove the unregistered body "
                        + body.getName()));
            }
            throw failed;
        }
        return id;
    }

    /** Registers from a stream; convenience overload that buffers the bytes first. */
    public String register(String displayName, String mimeType, InputStream content, long now)
            throws IOException {
        return register(displayName, mimeType, readAll(content), now);
    }

    /** The two finished states a parse can land in (the pending state lives before them). */
    public static final String PARSE_STATE_PARSED = "parsed";
    public static final String PARSE_STATE_FAILED = "parse_failed";

    /** One finished parse: the state it landed in, and the text a parsed record cached. */
    public static final class Parsed {

        public final String state;
        public final String text;

        Parsed(String state, String text) {
            this.state = state;
            this.text = text;
        }
    }

    /**
     * Runs the T-097 parse on the record's own physical file and lands the outcome: a decoded
     * source becomes {@code parsed} with its text cached on the record, anything undecodable
     * becomes {@code parse_failed} with no text. Idempotent by refusal — a finished parse is
     * final, and re-landing raises rather than overwriting.
     */
    public void finalizeParse(String attachmentId, long now) {
        AttachmentDao.Record record = AttachmentDao.findById(helper.getWritableDatabase(),
                attachmentId);
        if (record == null) {
            throw new IllegalArgumentException("cannot parse a non-existent attachment "
                    + attachmentId);
        }
        String state;
        String text = null;
        try {
            text = AttachmentTextExtractor.extract(record.displayName, record.mimeType,
                    readAll(new java.io.FileInputStream(fileOf(attachmentId))));
            state = PARSE_STATE_PARSED;
        } catch (RuntimeException | java.io.IOException unreadable) {
            state = PARSE_STATE_FAILED;
        }
        AttachmentDao.updateParseOutcome(helper.getWritableDatabase(), attachmentId, state, text,
                now);
    }

    /** One record's finished parse, or the pending state before it. */
    public Parsed parseOutcome(String attachmentId) {
        AttachmentDao.Record record = AttachmentDao.findById(helper.getWritableDatabase(),
                attachmentId);
        if (record == null) {
            throw new IllegalArgumentException("no attachment record " + attachmentId);
        }
        return new Parsed(record.parseState, record.extractedText);
    }

    /**
     * The cached text of a parsed record, or null while it has none (pending, failed, or the
     * genuinely empty file). Readers — the send path and the history rebuild — take this and never
     * re-parse (SPEC-082 数据结构: the parse happens once).
     */
    public String textOf(String attachmentId) {
        AttachmentDao.Record record = AttachmentDao.findById(helper.getWritableDatabase(),
                attachmentId);
        return record == null ? null : record.extractedText;
    }

    /** Links one message to one attachment; the attachment row must exist. */
    public void link(String messageId, String attachmentId, long now) {
        Guard.id(messageId, "messageId");
        Guard.id(attachmentId, "attachmentId");
        if (AttachmentDao.findById(helper.getWritableDatabase(), attachmentId) == null) {
            throw new IllegalArgumentException("cannot reference a non-existent attachment "
                    + attachmentId);
        }
        AttachmentDao.insertReference(helper.getWritableDatabase(),
                UUID.randomUUID().toString(), messageId, attachmentId, now);
    }

    /** Every record, newest first, each with its physical and referential truth. */
    public List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        for (AttachmentDao.Record record : AttachmentDao.listAll(helper.getWritableDatabase())) {
            entries.add(new Entry(record.id, record.displayName, record.mimeType, record.sizeBytes,
                    record.parseState, fileOf(record.id).exists(),
                    AttachmentDao.referenceCount(helper.getWritableDatabase(), record.id)));
        }
        return entries;
    }

    /** One message's references, resolved against the records and the physical files. */
    public List<Ref> refsOf(String messageId) {
        List<Ref> refs = new ArrayList<>();
        for (AttachmentDao.ResolvedReference reference : AttachmentDao.referencesOfMessage(
                helper.getWritableDatabase(), messageId)) {
            AttachmentDao.Record record = reference.record;
            refs.add(new Ref(reference.attachmentId,
                    record == null ? null : record.displayName,
                    record != null && fileOf(reference.attachmentId).exists()));
        }
        return refs;
    }

    /**
     * Deletes one attachment: the physical file and the record row. Reference rows survive —
     * that is the missing annotation — and an unreferenced record simply leaves the list.
     */
    public void deleteAttachment(String attachmentId) {
        Guard.id(attachmentId, "attachmentId");
        File body = fileOf(attachmentId);
        if (body.exists() && !body.delete()) {
            throw new IllegalStateException("cannot delete the attachment body " + body.getName());
        }
        AttachmentDao.delete(helper.getWritableDatabase(), attachmentId);
    }

    /** The physical body of one record: the private-directory file named by its content hash. */
    public File fileOf(String attachmentId) {
        AttachmentDao.Record record = AttachmentDao.findById(helper.getWritableDatabase(),
                attachmentId);
        if (record == null) {
            return new File(directory, "missing-" + attachmentId);
        }
        return new File(directory, record.contentHash);
    }

    /**
     * The ids the orphan scan marks (T-098): the record rows no message references any more. The
     * judgement itself lives in exactly one place — {@link AttachmentDao#orphanIds} — this is its
     * port.
     */
    public List<String> orphans() {
        return AttachmentDao.orphanIds(helper.getWritableDatabase());
    }

    /**
     * Cleans the given orphan candidates: record row and physical body go, in either order of
     * arrival, and an already-gone id is a silent no-op (T-098's idempotency contract — a double
     * tap or a concurrent delete must never crash). The caller passes ids that came from
     * {@link #orphans()}; this method re-checks nothing because the idempotent delete already
     * makes a stale target harmless — but it refuses to run an empty hand-over.
     */
    public void cleanupOrphans(Set<String> candidateIds) {
        if (candidateIds == null) {
            throw new IllegalArgumentException("candidateIds must not be null");
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        for (String id : candidateIds) {
            Guard.id(id, "candidateId");
            File body = fileOf(id);
            if (body.exists() && !body.delete()) {
                throw new IllegalStateException("cannot delete the attachment body " + body.getName());
            }
            AttachmentDao.deleteIfPresent(db, id);
        }
    }

    /** Total bytes of the records whose physical files still exist. */
    public long usageBytes() {
        long total = 0;
        for (Entry entry : list()) {
            if (entry.present) {
                total += entry.sizeBytes;
            }
        }
        return total;
    }

    private static void writeAtomically(File target, byte[] bytes) {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(bytes);
            out.getFD().sync();
        } catch (IOException failed) {
            throw new IllegalStateException("cannot write the attachment body", failed);
        }
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IllegalStateException("cannot commit the attachment body " + target.getName());
        }
    }

    private static byte[] readAll(InputStream content) throws IOException {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = content.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        content.close();
        return buffer.toByteArray();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(bytes)) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is a platform guarantee", impossible);
        }
    }
}
