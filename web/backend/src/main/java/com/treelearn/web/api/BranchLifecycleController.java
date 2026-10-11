package com.treelearn.web.api;

import com.treelearn.web.api.dto.BranchDtos;
import com.treelearn.web.api.dto.RequestDtos;
import com.treelearn.web.api.dto.TopicDtos;
import com.treelearn.web.generation.BranchLifecycleService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分支生命周期与删除端点（任务 T-2026-10-10-042，契约 §6.2/§6.3）。
 *
 * <p>控制器薄：校验在 {@link BranchLifecycleService} 与共享域 {@code Branch}，§5 DTO 映射经
 * {@link ApiDtoMapper}；错误经 {@link CoreApiExceptionHandler} 统一成 §3.1 封装。归档/恢复/折叠/
 * 已见/阅读位置/删除预览/删除共 9 条路由，此前均为 404。
 */
@RestController
class BranchLifecycleController {

    private final BranchLifecycleService lifecycle;

    BranchLifecycleController(BranchLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/api/branches/{id}/archive")
    BranchDtos.Branch archive(@PathVariable String id,
            @RequestBody RequestDtos.CommandRequest request) {
        return ApiDtoMapper.branch(lifecycle.archive(id, request.commandId()));
    }

    @PostMapping("/api/branches/{id}/restore")
    BranchDtos.Branch restore(@PathVariable String id,
            @RequestBody RequestDtos.CommandRequest request) {
        return ApiDtoMapper.branch(lifecycle.restore(id, request.commandId()));
    }

    @PostMapping("/api/branches/{id}/collapse")
    BranchDtos.Branch collapse(@PathVariable String id,
            @RequestBody RequestDtos.BranchCollapseRequest request) {
        return ApiDtoMapper.branch(lifecycle.collapse(id, request.commandId(), request.collapsed()));
    }

    @PostMapping("/api/branches/{id}/seen")
    BranchDtos.Branch seen(@PathVariable String id,
            @RequestBody RequestDtos.BranchSeenRequest request) {
        return ApiDtoMapper.branch(lifecycle.seen(id, request.commandId(),
                request.seenThroughSequence()));
    }

    @PutMapping("/api/branches/{id}/reading-position")
    BranchDtos.ReadingPositionSaved readingPosition(@PathVariable String id,
            @RequestBody RequestDtos.ReadingPositionUpdateRequest request) {
        return new BranchDtos.ReadingPositionSaved(id, ApiDtoMapper.readingAnchor(
                lifecycle.writeReadingPosition(id, request.commandId(), request.messageId(),
                        request.revisionId(), request.listItemOffset(), request.viewportOffset())));
    }

    @GetMapping("/api/branches/{id}/deletion-preview")
    TopicDtos.DeletionPreview branchDeletionPreview(@PathVariable String id) {
        return toPreview(lifecycle.branchDeletionPreview(id));
    }

    @PostMapping("/api/branches/{id}/delete")
    BranchDtos.BranchDeleted deleteBranch(@PathVariable String id,
            @RequestBody RequestDtos.DeletionConfirmRequest request) {
        return new BranchDtos.BranchDeleted(
                lifecycle.deleteBranch(id, request.commandId(), request.digest()));
    }

    @GetMapping("/api/topics/{id}/deletion-preview")
    TopicDtos.DeletionPreview topicDeletionPreview(@PathVariable String id) {
        return toPreview(lifecycle.topicDeletionPreview(id));
    }

    @PostMapping("/api/topics/{id}/delete")
    TopicDtos.TopicDeleted deleteTopic(@PathVariable String id,
            @RequestBody RequestDtos.DeletionConfirmRequest request) {
        return new TopicDtos.TopicDeleted(id,
                lifecycle.deleteTopic(id, request.commandId(), request.digest()));
    }

    private static TopicDtos.DeletionPreview toPreview(BranchLifecycleService.Plan plan) {
        return new TopicDtos.DeletionPreview(plan.topicId(), plan.targetBranchId(),
                plan.wholeTopic(), List.copyOf(plan.ids()), plan.ids().size(),
                BranchLifecycleService.digestOf(plan));
    }
}
