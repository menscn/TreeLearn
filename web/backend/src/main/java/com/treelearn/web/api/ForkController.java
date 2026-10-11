package com.treelearn.web.api;

import com.treelearn.core.domain.BranchAnchor;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.web.api.dto.BranchDtos;
import com.treelearn.web.api.dto.RequestDtos;
import com.treelearn.web.api.dto.TopicDtos;
import com.treelearn.web.generation.ForkCommandService;
import com.treelearn.web.generation.ServiceRefusalException;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 稳定回答分叉端点（任务 T-2026-10-10-037；契约 {@code web/contracts/api.md} §6.3）。
 *
 * <p>控制器保持薄：把请求锚点解码为共享域 {@link BranchAnchor}（畸形或半填充 → {@code ANCHOR_INVALID}，
 * §3.3），委托 {@link ForkCommandService} 完成校验/冻结/落库/可选首问，再用 {@link ApiDtoMapper} 组装
 * {@link BranchDtos.ForkResult}（{@code bootstrap} 仅在带 {@code question} 时出现）。错误经
 * {@link CoreApiExceptionHandler} 统一成 §3.1 封装。
 *
 * <p>{@code source} 取自子分支的溯源（{@link com.treelearn.core.domain.BranchSource}），与
 * {@code branch.source} 同一份事实——不另算一份，避免二者漂移。
 */
@RestController
class ForkController {

    private final ForkCommandService forks;

    ForkController(ForkCommandService forks) {
        this.forks = forks;
    }

    @PostMapping("/api/branches/{id}/forks")
    BranchDtos.ForkResult fork(@PathVariable String id, @RequestBody RequestDtos.BranchForkRequest request) {
        ForkCommandService.Forked forked = forks.fork(id, request.commandId(), request.sourceMessageId(),
                request.sourceRevisionId(), anchorOf(request.anchor()), request.question(), request.model(),
                Boolean.TRUE.equals(request.thinkingEnabled()));

        TopicDtos.TopicBootstrap bootstrap = forked.operation() == null ? null
                : new TopicDtos.TopicBootstrap(ApiDtoMapper.topic(forked.topic()),
                        ApiDtoMapper.branch(forked.branch()), ApiDtoMapper.message(forked.userMessage()),
                        ApiDtoMapper.message(forked.aiMessage()),
                        ApiDtoMapper.operation(forked.operation()));
        return new BranchDtos.ForkResult(ApiDtoMapper.branch(forked.branch()),
                ApiDtoMapper.branchSource(forked.branch().source), bootstrap);
    }

    /** {@code null} = 整条分叉；非空则四列齐全并交给共享域校验（半开区间 + quote/hash + 跨度一致）。 */
    private static BranchAnchor anchorOf(BranchDtos.BranchAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        if (anchor.startInclusive() == null || anchor.endExclusive() == null
                || anchor.quote() == null || anchor.hash() == null) {
            throw new ServiceRefusalException("ANCHOR_INVALID", Map.of());
        }
        DomainResult<BranchAnchor> ranged = BranchAnchor.ofRange(anchor.startInclusive(),
                anchor.endExclusive(), anchor.quote(), anchor.hash());
        if (ranged.isFailure()) {
            throw new ServiceRefusalException("ANCHOR_INVALID", Map.of());
        }
        return ranged.value();
    }
}
