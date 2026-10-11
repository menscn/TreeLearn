package com.treelearn.web.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.treelearn.core.domain.Guard;
import com.treelearn.web.api.dto.AttachmentDtos;
import com.treelearn.web.api.dto.RequestDtos;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.AttachmentStore;
import com.treelearn.web.storage.AttachmentStore.Entry;

/**
 * 附件端点（契约 §6.9，任务 T-2026-10-10-054）。JSON 上传样例（multipart 同语义）；
 * 判定（上限/类型）只此一处收在 {@link AttachmentStore#refusalFor}；DELETE 引用保留
 * 缺失标记；孤儿清理幂等。
 */
@RestController
public class AttachmentController {

    private final AttachmentStore attachments;

    public AttachmentController(AttachmentStore attachments) {
        this.attachments = attachments;
    }

    @PostMapping("/api/attachments")
    public AttachmentDtos.Attachment upload(@RequestBody RequestDtos.AttachmentUploadRequest request) {
        Guard.id(request.commandId(), "commandId");
        Guard.text(request.displayName(), "displayName");
        Guard.text(request.mimeType(), "mimeType");
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(
                    request.contentBase64() == null ? "" : request.contentBase64());
        } catch (IllegalArgumentException malformed) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("field", "contentBase64"));
        }
        if (bytes.length == 0) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("field", "contentBase64"));
        }
        String refusal = attachments.refusalFor(request.mimeType(), bytes.length);
        if (refusal != null) {
            Map<String, Object> params = refusal.equals("ATTACHMENT_TOO_LARGE")
                    ? Map.of("maxBytes", attachments.maxBytes())
                    : Map.of("mimeType", request.mimeType());
            throw new ServiceRefusalException(refusal, params);
        }
        String id = attachments.register(request.displayName(), request.mimeType(), bytes,
                System.currentTimeMillis());
        return toDto(entryOf(id));
    }

    @GetMapping("/api/attachments")
    public AttachmentDtos.AttachmentListResponse list() {
        List<Entry> entries = attachments.list();
        long usage = 0;
        List<AttachmentDtos.Attachment> views = new java.util.ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.present) {
                usage += entry.sizeBytes;
            }
            views.add(toDto(entry));
        }
        return new AttachmentDtos.AttachmentListResponse(views, usage);
    }

    @GetMapping("/api/attachments/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable String id) {
        Guard.id(id, "attachmentId");
        byte[] bytes = attachments.contentOf(id).orElseThrow(
                () -> new ServiceRefusalException("NOT_FOUND", Map.of("resource", "attachment_body")));
        String mimeType = attachments.mimeTypeOf(id).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, mimeType)
                .body(bytes);
    }

    @DeleteMapping("/api/attachments/{id}")
    public Map<String, String> delete(@PathVariable String id,
            @RequestBody RequestDtos.CommandRequest request) {
        Guard.id(id, "attachmentId");
        Guard.id(request.commandId(), "commandId");
        attachments.delete(id);
        return Map.of("deletedAttachmentId", id);
    }

    @PostMapping("/api/files/cleanup")
    public Map<String, Integer> cleanup(@RequestBody RequestDtos.CommandRequest request) {
        Guard.id(request.commandId(), "commandId");
        return Map.of("cleaned", attachments.cleanupOrphans(attachments.orphans()));
    }

    private Entry entryOf(String id) {
        return attachments.list().stream().filter((entry) -> entry.id.equals(id)).findFirst()
                .orElseThrow(() -> new ServiceRefusalException("OPERATION_NOT_COMPLETABLE", Map.of()));
    }

    private static AttachmentDtos.Attachment toDto(Entry entry) {
        return new AttachmentDtos.Attachment(entry.id, entry.displayName, entry.mimeType,
                entry.sizeBytes, entry.parseState, entry.present, entry.referenceCount);
    }
}
