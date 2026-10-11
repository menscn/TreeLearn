package com.treelearn.web.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 附件存储（契约 §6.9，任务 T-2026-10-10-054）。语义权威 = Android
 * {@code app/files/AttachmentStore}：内容寻址去重（同字节解析到既有记录，首个显示名保留）、
 * 物理体以内容哈希命名（{@code <dataDir>/attachments/<sha256>}）、行落库失败必须回收已写的
 * 物理体（行是记录，不许留孤儿体）、DELETE 引用保留缺失标记、孤儿清理幂等。
 */
public final class AttachmentStore {

    public static final String PARSE_STATE_PENDING = "pending";

    /** {@code list()} 的一行：记录事实 + 物理存在 + 引用计数（Android Entry 镜像）。 */
    public static final class Entry {
        public final String id;
        public final String displayName;
        public final String mimeType;
        public final long sizeBytes;
        public final String parseState;
        public final boolean present;
        public final int referenceCount;

        public Entry(String id, String displayName, String mimeType, long sizeBytes,
                String parseState, boolean present, int referenceCount) {
            this.id = id; this.displayName = displayName; this.mimeType = mimeType;
            this.sizeBytes = sizeBytes; this.parseState = parseState;
            this.present = present; this.referenceCount = referenceCount;
        }
    }

    private static final String SUPPORTED_MIME_PREFIX = "text/";
    private static final List<String> SUPPORTED_MIMES = List.of(
            "text/plain", "text/markdown", "application/pdf");

    private final JdbcSupport jdbc;
    private final Path directory;
    private final long maxBytes;

    public AttachmentStore(JdbcSupport jdbc, Path dataDir, long maxBytes) {
        this.jdbc = jdbc;
        this.directory = dataDir.resolve("attachments");
        this.maxBytes = maxBytes;
    }

    /** 判定只此一处：上传上限与类型白名单（Android AttachmentTextExtractor 首批集合）。 */
    public String refusalFor(String mimeType, int byteCount) {
        if (byteCount > maxBytes) {
            return "ATTACHMENT_TOO_LARGE";
        }
        String lower = mimeType == null ? "" : mimeType.toLowerCase();
        if (!SUPPORTED_MIMES.contains(lower) && !lower.startsWith(SUPPORTED_MIME_PREFIX)) {
            return "ATTACHMENT_TYPE_UNSUPPORTED";
        }
        return null;
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** 注册一个文件：去重命中返回既有 id；否则原子写体后落行（行失败回收体）。 */
    public String register(String displayName, String mimeType, byte[] bytes, long now) {
        String hash = sha256Hex(bytes);
        Optional<String> existing = jdbc.queryOne(
                "SELECT id FROM attachments WHERE content_hash = ?",
                s -> s.setString(1, hash),
                rs -> rs.getString("id"));
        if (existing.isPresent()) {
            return existing.get();
        }
        String id = UUID.randomUUID().toString();
        Path body = directory.resolve(hash);
        try {
            Files.createDirectories(directory);
            Path staging = directory.resolve(hash + ".part-" + UUID.randomUUID());
            Files.write(staging, bytes);
            Files.move(staging, body, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            throw new StorageException("writing attachment body " + hash, failed);
        }
        try {
            jdbc.update("INSERT INTO attachments (id, content_hash, display_name, mime_type,"
                            + " size_bytes, extracted_text, parse_state, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?)",
                    s -> {
                        s.setString(1, id);
                        s.setString(2, hash);
                        s.setString(3, displayName);
                        s.setString(4, mimeType);
                        s.setLong(5, bytes.length);
                        s.setString(6, PARSE_STATE_PENDING);
                        s.setLong(7, now);
                        s.setLong(8, now);
                    });
        } catch (RuntimeException failed) {
            // 行是记录；落库失败的体不许留下（Android 同款回滚）。
            try {
                Files.deleteIfExists(body);
            } catch (IOException suppressed) {
                failed.addSuppressed(suppressed);
            }
            throw failed;
        }
        return id;
    }

    /** 列表（最新在前）+ 总字节（present 体之和，Android usage 语义）。 */
    public List<Entry> list() {
        record Row(String id, String hash, String displayName, String mimeType,
                long sizeBytes, String parseState) {}
        List<Row> rows = jdbc.queryList(
                "SELECT id, content_hash, display_name, mime_type, size_bytes, parse_state"
                        + " FROM attachments ORDER BY created_at DESC, id",
                JdbcSupport.NO_PARAMS,
                rs -> new Row(rs.getString("id"), rs.getString("content_hash"),
                        rs.getString("display_name"), rs.getString("mime_type"),
                        rs.getLong("size_bytes"), rs.getString("parse_state")));
        List<Entry> entries = new ArrayList<>(rows.size());
        long usage = 0;
        for (Row row : rows) {
            boolean present = Files.isRegularFile(directory.resolve(row.hash()));
            int refs = jdbc.queryOne(
                    "SELECT COUNT(*) AS n FROM message_attachments WHERE attachment_id = ?",
                    s -> s.setString(1, row.id()),
                    rs -> rs.getInt("n")).orElse(0);
            if (present) {
                usage += row.sizeBytes();
            }
            entries.add(new Entry(row.id(), row.displayName(), row.mimeType(), row.sizeBytes(),
                    row.parseState(), present, refs));
        }
        return entries;
    }

    public Optional<byte[]> contentOf(String attachmentId) {
        Optional<String> hash = jdbc.queryOne(
                "SELECT content_hash FROM attachments WHERE id = ?",
                s -> s.setString(1, attachmentId),
                rs -> rs.getString("content_hash"));
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        Path body = directory.resolve(hash.get());
        if (!Files.isRegularFile(body)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(body));
        } catch (IOException failed) {
            throw new StorageException("reading attachment body " + hash.get(), failed);
        }
    }

    public Optional<String> mimeTypeOf(String attachmentId) {
        return jdbc.queryOne(
                "SELECT mime_type FROM attachments WHERE id = ?",
                s -> s.setString(1, attachmentId),
                rs -> rs.getString("mime_type"));
    }

    /** 删除记录与体；引用行保留（缺失标记语义）。幂等：不存在的 id 静默成功。 */
    public void delete(String attachmentId) {
        Optional<String> hash = jdbc.queryOne(
                "SELECT content_hash FROM attachments WHERE id = ?",
                s -> s.setString(1, attachmentId),
                rs -> rs.getString("content_hash"));
        if (hash.isEmpty()) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(hash.get()));
        } catch (IOException failed) {
            throw new StorageException("deleting attachment body " + hash.get(), failed);
        }
        jdbc.update("DELETE FROM attachments WHERE id = ?", s -> s.setString(1, attachmentId));
    }

    /** 孤儿 = 无任何消息引用的记录（判定只此一处，镜像 AttachmentDao.orphanIds）。 */
    public List<String> orphans() {
        return jdbc.queryList(
                "SELECT a.id FROM attachments a WHERE NOT EXISTS"
                        + " (SELECT 1 FROM message_attachments m WHERE m.attachment_id = a.id)",
                JdbcSupport.NO_PARAMS,
                rs -> rs.getString("id"));
    }

    /** 幂等清理：体与行任一顺序到达都收敛，已消失的 id 静默无操作。 */
    public int cleanupOrphans(List<String> candidateIds) {
        int cleaned = 0;
        for (String id : candidateIds) {
            delete(id);
            cleaned += 1;
        }
        return cleaned;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
