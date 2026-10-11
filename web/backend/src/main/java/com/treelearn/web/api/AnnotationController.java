package com.treelearn.web.api;

import com.treelearn.core.domain.Annotation;
import com.treelearn.core.domain.Message;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.AnnotationStore;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 批注端点（契约 §6.10，任务 T-2026-10-10-047）。
 *
 * <p><strong>digest 自愈读取只有一条写路径</strong>：读取时按「当前显示文本」的 SHA-256 清理失配行
 * （{@link AnnotationStore#listFor}，同事务、删除不重定位）。写入方义务（Android reviewer F3）：
 * 批注只应写在 stable 答案上——AI 消息尚无 active revision（生成中）时拒绝写入
 * {@code OPERATION_NOT_STABLE}，否则批注会挂在永远不能成为 stable 的 digest 上、下次读取被正确清理。
 *
 * <p>HIGHLIGHT 合并由共享领域规则 {@link Annotation#mergeHighlights} 在服务端执行：
 * absorbed 行删除与并集行落在同一事务，任何读取都看不到「半标记」区间。
 */
@RestController
class AnnotationController {

    private final JdbcSupport jdbc;
    private final MessageStore messages;
    private final AnswerRevisionStore revisions;
    private final AnnotationStore annotations;

    AnnotationController(JdbcSupport jdbc, MessageStore messages, AnswerRevisionStore revisions,
            AnnotationStore annotations) {
        this.jdbc = jdbc;
        this.messages = messages;
        this.revisions = revisions;
        this.annotations = annotations;
    }

    // ------------------------------------------------------------------ DTOs

    /** §5.8 Annotation 投影：空容器/不适用索引用 -1 哨兵（与共享域一致）。 */
    public record AnnotationDto(String id, String kind, String container, int proseIndex,
            int tableIndex, int rowIndex, int colIndex, int start, int end, String body) {}

    public record AnnotationList(String messageId, List<AnnotationDto> annotations) {}

    public record AnnotationWritten(List<AnnotationDto> annotations) {}

    public record AnnotationDeleted(String deletedAnnotationId) {}

    public record AnnotationRequest(String commandId, String kind, String container,
            Integer proseIndex, Integer tableIndex, Integer rowIndex, Integer colIndex,
            int start, int end, String body) {}

    // ---------------------------------------------------------------- routes

    @GetMapping("/api/messages/{id}/annotations")
    AnnotationList list(@PathVariable String id) {
        Message message = requireMessage(id);
        String text = displayedText(id, message);
        String digest = digest(text);
        List<AnnotationDto> out = new ArrayList<>();
        for (Annotation annotation : annotations.listFor(id, digest)) {
            out.add(toDto(annotation));
        }
        return new AnnotationList(id, out);
    }

    @PostMapping("/api/messages/{id}/annotations")
    AnnotationWritten write(@PathVariable String id, @RequestBody JsonNode payload) {
        AnnotationRequest request = ApiJson.mapper().convertValue(payload, AnnotationRequest.class);
        Message message = requireMessage(id);
        String text = displayedText(id, message);
        if (request.start < 0 || request.end <= request.start || request.end > text.length()) {
            throw new ServiceRefusalException("ANCHOR_INVALID", Map.of("field", "start/end"));
        }
        Annotation.Locator locator = locatorOf(request);
        long now = System.currentTimeMillis();
        String digest = digest(text);

        if ("NOTE".equals(request.kind)) {
            if (request.body == null || request.body.isBlank()) {
                throw new ServiceRefusalException("NOTE_BODY_REQUIRED", Map.of("field", "body"));
            }
            Annotation note = Annotation.note(newId(), locator, request.start, request.end,
                    request.body);
            annotations.insert(id, digest, note, now);
            return new AnnotationWritten(currentAnnotations(id, digest));
        }
        if (!"HIGHLIGHT".equals(request.kind)) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("field", "kind"));
        }

        // 相交/相邻合并为并集；absorbed 行删除与 union 行落在同一事务（方案 V1.1 §2.2）。
        List<Annotation> existing = new ArrayList<>();
        for (Annotation annotation : annotations.listFor(id, digest)) {
            existing.add(annotation);
        }
        Annotation.Merge merge = Annotation.mergeHighlights(existing, locator, request.start,
                request.end);
        Annotation union = Annotation.highlight(newId(), locator, merge.span.start, merge.span.end);
        jdbc.runInTransaction(connection -> {
            for (String absorbed : merge.absorbedIds) {
                annotations.delete(absorbed);
            }
            annotations.insert(id, digest, union, now);
            return null;
        });
        return new AnnotationWritten(currentAnnotations(id, digest));
    }

    @DeleteMapping("/api/annotations/{id}")
    AnnotationDeleted delete(@PathVariable String id) {
        annotations.delete(id);
        return new AnnotationDeleted(id);
    }

    // --------------------------------------------------------------- helpers

    private List<AnnotationDto> currentAnnotations(String messageId, String digest) {
        List<AnnotationDto> out = new ArrayList<>();
        for (Annotation annotation : annotations.listFor(messageId, digest)) {
            out.add(toDto(annotation));
        }
        return out;
    }

    private Message requireMessage(String id) {
        Message message = messages.get(id).orElse(null);
        if (message == null) {
            throw new ServiceRefusalException("NOT_FOUND", Map.of("resource", "message"));
        }
        return message;
    }

    /** 当前显示文本：USER=user_content；AI=active revision 的 answer（生成中 → 拒绝，见类注释）。 */
    private String displayedText(String messageId, Message message) {
        if ("USER".equals(message.role.name())) {
            return message.userContent;
        }
        var revision = revisions.findActiveForMessage(messageId).orElse(null);
        if (revision == null) {
            throw new ServiceRefusalException("OPERATION_NOT_STABLE", Map.of());
        }
        return revision.answerContent;
    }

    private Annotation.Locator locatorOf(AnnotationRequest request) {
        if ("CELL".equals(request.container)) {
            if (request.tableIndex == null || request.rowIndex == null || request.colIndex == null) {
                throw new ServiceRefusalException("ANCHOR_INVALID",
                        Map.of("field", "tableIndex/rowIndex/colIndex"));
            }
            return Annotation.Locator.cell(request.tableIndex, request.rowIndex,
                    request.colIndex);
        }
        if (!"PROSE".equals(request.container)) {
            throw new ServiceRefusalException("ANCHOR_INVALID", Map.of("field", "container"));
        }
        return Annotation.Locator.prose(request.proseIndex == null ? -1 : request.proseIndex);
    }

    private static AnnotationDto toDto(Annotation annotation) {
        return new AnnotationDto(annotation.id, annotation.kind.name(),
                annotation.locator.container.name(), annotation.locator.proseIndex,
                annotation.locator.tableIndex, annotation.locator.rowIndex,
                annotation.locator.colIndex, annotation.span.start, annotation.span.end,
                annotation.body == null ? "" : annotation.body);
    }

    /** 与 Android `SqliteChatStore.digest` 同源：显示文本 UTF-8 字节的 SHA-256 hex。 */
    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String newId() {
        return java.util.UUID.randomUUID().toString();
    }
}
