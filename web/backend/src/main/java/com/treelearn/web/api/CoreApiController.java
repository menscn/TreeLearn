package com.treelearn.web.api;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Message;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.api.dto.BranchDtos;
import com.treelearn.web.api.dto.MessageDtos;
import com.treelearn.web.api.dto.OperationDtos;
import com.treelearn.web.api.dto.RequestDtos;
import com.treelearn.web.api.dto.TopicDtos;
import com.treelearn.web.generation.ChatCommandService;
import com.treelearn.web.generation.DesktopGenerationCoordinator;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.TopicStore;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

/**
 * 核心闭环 REST 端点（任务 T-2026-10-10-028，契约 {@code web/contracts/api.md} §6.2/§6.3/§6.4）。
 *
 * <p>控制器保持薄：读端点直接经 Store + {@link ApiDtoMapper} 组 DTO；变更端点委托
 * {@link ChatCommandService} 接受/提交。错误经 {@link CoreApiExceptionHandler} 统一成 §3.1 封装。
 *
 * <p>{@code GET /api/operations/active?branchId=} 按契约 §6.4 返回 {@code 200}：有活跃操作时为
 * {@code Operation}，无则为 JSON 字面量 {@code null}（以 {@link NullNode} 承载，避免 Spring 把
 * {@code null} 方法返回值当成无响应体）。{@code branchId} 缺失/为空/非规范 UUID 属客户端可修复的错误，
 * 统一为 {@code 400 REQUEST_INVALID}（§3.3），不落 500 或 404。
 *
 * <p>{@code GET /api/branches/{id}/messages} 的 {@code attachments} 恒为空数组：附件未实现，缺口已记录。
 */
@RestController
class CoreApiController {

    private final ChatCommandService commands;
    private final TopicStore topics;
    private final BranchStore branches;
    private final MessageStore messages;
    private final AnswerRevisionStore revisions;
    private final OperationStore operations;
    private final DesktopGenerationCoordinator coordinator;

    CoreApiController(ChatCommandService commands, TopicStore topics, BranchStore branches,
            MessageStore messages, AnswerRevisionStore revisions, OperationStore operations,
            DesktopGenerationCoordinator coordinator) {
        this.commands = commands; this.topics = topics; this.branches = branches;
        this.messages = messages; this.revisions = revisions; this.operations = operations;
        this.coordinator = coordinator;
    }

    @GetMapping("/api/topics")
    List<TopicDtos.TopicSummary> topics() {
        return topics.listOrderedByRecency().stream().map(ApiDtoMapper::topicSummary).toList();
    }

    @PostMapping("/api/topics")
    TopicDtos.TopicBootstrap createTopic(@RequestBody RequestDtos.TopicCreateRequest request) {
        ChatCommandService.CreatedTopic created = commands.createTopic(request.commandId(),
                request.question(), request.model(), request.thinkingEnabled(), request.quotedText());
        return new TopicDtos.TopicBootstrap(ApiDtoMapper.topic(created.topic()),
                ApiDtoMapper.branch(created.rootBranch()), message(created.userMessage()),
                message(created.aiMessage()), ApiDtoMapper.operation(created.operation()));
    }

    @GetMapping("/api/topics/{id}")
    TopicDtos.Topic topic(@PathVariable String id) {
        return ApiDtoMapper.topic(topics.get(id).orElseThrow(() -> notFound("topic")));
    }

    @GetMapping("/api/topics/{id}/branches")
    BranchDtos.BranchTree branchTree(@PathVariable String id) {
        topics.get(id).orElseThrow(() -> notFound("topic"));
        List<BranchDtos.Branch> nodes =
                branches.listForTopic(id).stream().map(ApiDtoMapper::branch).toList();
        return new BranchDtos.BranchTree(id, nodes);
    }

    @GetMapping("/api/branches/{id}/messages")
    TopicDtos.Conversation conversation(@PathVariable String id) {
        branches.get(id).orElseThrow(() -> notFound("branch"));
        List<MessageDtos.Message> list =
                messages.listForBranch(id).stream().map(position -> message(position.message)).toList();
        return new TopicDtos.Conversation(id,
                ApiDtoMapper.readingAnchor(branches.readReadingPosition(id).orElse(null)), list, List.of());
    }

    @PostMapping("/api/branches/{id}/messages")
    TopicDtos.TurnAccepted sendMessage(@PathVariable String id,
            @RequestBody RequestDtos.BranchMessageCreateRequest request) {
        ChatCommandService.Turn turn = commands.sendMessage(id, request.commandId(), request.text(),
                request.quote(), request.model(), request.thinkingEnabled());
        return new TopicDtos.TurnAccepted(message(turn.userMessage()), message(turn.aiMessage()),
                ApiDtoMapper.operation(turn.operation()));
    }

    @GetMapping("/api/operations/{id}")
    OperationDtos.Operation operation(@PathVariable String id) {
        return ApiDtoMapper.operation(operations.byId(id).orElseThrow(() -> notFound("operation")));
    }

    @GetMapping("/api/operations/active")
    JsonNode activeOperation(@RequestParam("branchId") String branchId) {
        if (branchId.isBlank()) {
            throw requestInvalid();
        }
        try {
            Guard.id(branchId, "branchId");
        } catch (IllegalArgumentException malformed) {
            throw requestInvalid();
        }
        return operations.activeForBranch(branchId)
                .<JsonNode>map(op -> ApiJson.mapper().valueToTree(ApiDtoMapper.operation(op)))
                .orElse(NullNode.getInstance());
    }

    @PostMapping("/api/operations/{id}/cancel")
    OperationDtos.Operation cancel(@PathVariable String id,
            @RequestBody RequestDtos.CommandRequest request) {
        operations.byId(id).orElseThrow(() -> notFound("operation"));
        coordinator.cancel(id);
        return ApiDtoMapper.operation(operations.byId(id).orElseThrow(() -> notFound("operation")));
    }

    @PostMapping("/api/messages/{id}/retry")
    TopicDtos.MessageOperation retry(@PathVariable String id,
            @RequestBody RequestDtos.MessageRegenerateRequest request) {
        ChatCommandService.Replaced replaced = commands.retry(id, request.commandId(), request.model(),
                request.thinkingEnabled());
        return new TopicDtos.MessageOperation(message(replaced.message()),
                ApiDtoMapper.operation(replaced.operation()));
    }

    private MessageDtos.Message message(Message message) {
        return ApiDtoMapper.message(message, revisions.listForMessage(message.id));
    }

    private static ServiceRefusalException notFound(String resource) {
        return new ServiceRefusalException("NOT_FOUND", Map.of("resource", resource));
    }

    private static ServiceRefusalException requestInvalid() {
        return new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
    }
}
