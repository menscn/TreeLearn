package com.treelearn.web.api;

import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationCommand;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Message;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.web.generation.DesktopGenerationCoordinator;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.ArtifactStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.api.dto.OperationDtos;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 派生任务端点（契约 §6.5，任务 T-2026-10-10-052）。
 *
 * <p><strong>判定只此一处。</strong>POST 本切片支持 {@code MESSAGE_TRANSLATION}（语义自足：源文本
 * 即 currentInput、稳定版前置、purpose 正确、走 operation + SSE）；其余六个 kind 依赖 Android
 * {@code ArtifactInputs.entries/outline} 的深度输入组装（birth context / tree outline /
 * compression turns），切片内返回 400 {@code DERIVED_KIND_UNSUPPORTED} 明示不支持——绝不以错误
 * 语义替代。GET 全 kind 支持（{@link ArtifactStore} 读取 + {@code stale} 判定：请求带
 * {@code basedOnRevisionId} 时与行内基线比对）。
 */
@RestController
public class DerivedController {

    private static final Set<String> UNSUPPORTED_KINDS = Set.of(
            "ROOT_TITLE", "BRANCH_TITLE", "BRANCH_SUMMARY", "ANSWER_RECOMMENDATIONS",
            "HOME_RECOMMENDATIONS", "CONTEXT_COMPRESSION");

    private final ArtifactStore artifacts;
    private final AnswerRevisionStore revisions;
    private final MessageStore messages;
    private final OperationStore operations;
    private final DesktopGenerationCoordinator coordinator;
    private final PromptBudget budget;
    private final Clock clock;

    public DerivedController(ArtifactStore artifacts, AnswerRevisionStore revisions,
            MessageStore messages, OperationStore operations,
            DesktopGenerationCoordinator coordinator, PromptBudget budget, Clock clock) {
        this.artifacts = artifacts;
        this.revisions = revisions;
        this.messages = messages;
        this.operations = operations;
        this.coordinator = coordinator;
        this.budget = budget;
        this.clock = clock;
    }

    /** 请求记录：commandId 必填；kind 为契约词表大写枚举；MESSAGE_TRANSLATION 的 scopeId 即消息 id。 */
    public record DerivedStartRequest(String commandId, String kind, String scopeId,
            String basedOnRevisionId, String model) {}

    @PostMapping("/api/derived")
    OperationDtos.Operation start(@RequestBody DerivedStartRequest request) {
        if (request.kind() == null || (!"MESSAGE_TRANSLATION".equals(request.kind())
                && !UNSUPPORTED_KINDS.contains(request.kind()))) {
            throw new ServiceRefusalException("DERIVED_KIND_INVALID",
                    Map.of("kind", String.valueOf(request.kind())));
        }
        if (UNSUPPORTED_KINDS.contains(request.kind())) {
            throw new ServiceRefusalException("DERIVED_KIND_UNSUPPORTED",
                    Map.of("kind", request.kind()));
        }
        if (request.scopeId() == null || request.scopeId().isBlank()) {
            throw new ServiceRefusalException("OPERATION_NOT_COMPLETABLE", Map.of());
        }
        Message message = messages.get(request.scopeId()).orElseThrow(
                () -> new ServiceRefusalException("OPERATION_NOT_COMPLETABLE", Map.of()));
        if (!message.isAi()) {
            throw new ServiceRefusalException("MESSAGE_ROLE_MISMATCH", Map.of());
        }
        AnswerRevision source = revisions.findActiveForMessage(message.id).orElseThrow(
                () -> new ServiceRefusalException("OPERATION_NOT_STABLE", Map.of()));
        GenerationCommand command = GenerationCommand.create(message.branchId, message.id,
                PromptPurpose.MESSAGE_TRANSLATION,
                DeepSeekPromptRegistry.spec(PromptPurpose.MESSAGE_TRANSLATION).policyVersion,
                GenerationModel.FLASH, false, clock.millis()).value();
        DesktopGenerationCoordinator.AcceptOutcome accept = coordinator.acceptForRetry(command);
        if (!accept.isAccepted()) {
            throw new ServiceRefusalException(accept.refusal.name(), Map.of());
        }
        String sourceText = source.answerContent;
        coordinator.submit(command, forCommand -> DeepSeekRequestAssembler.assemble(
                forCommand.purpose, false, List.of(), sourceText, budget));
        return ApiDtoMapper.operation(operations.byId(accept.accepted.operationId)
                .orElseThrow(() -> new ServiceRefusalException("OPERATION_NOT_COMPLETABLE", Map.of())));
    }

    /**
     * §6.5 读取已落库的派生结果；{@code stale=true} 表示 {@code basedOnRevisionId} 与行内基线不符
     * （前端作无结果处理）。{@code kind} 用 wireId（小写，契约 5.10）。
     */
    @GetMapping("/api/derived")
    Object read(@RequestParam String kind, @RequestParam String scopeId,
            @RequestParam(name = "basedOnRevisionId", required = false) String basedOnRevisionId) {
        ArtifactStore.Artifact artifact = artifacts.find(kind, scopeId).orElseThrow(
                () -> new ServiceRefusalException("NOT_FOUND", Map.of("resource", "derived_artifact")));
        // fail-closed：请求带基线而行内基线缺失或不等 → stale（无法证明新鲜即视为过期）。
        boolean stale = basedOnRevisionId != null
                && !basedOnRevisionId.equals(artifact.basedOnRevisionId);
        Object payload = ApiJson.mapper().readTree(artifact.payload);
        return Map.of("kind", artifact.kind, "scopeId", artifact.scopeId,
                "basedOnRevisionId", artifact.basedOnRevisionId == null ? "" : artifact.basedOnRevisionId,
                "inputDigest", artifact.inputDigest == null ? "" : artifact.inputDigest,
                "payload", payload, "stale", stale);
    }
}
