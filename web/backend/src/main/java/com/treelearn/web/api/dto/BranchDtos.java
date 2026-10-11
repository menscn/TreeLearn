package com.treelearn.web.api.dto;

import java.util.List;

/**
 * 分支域 DTO（契约 {@code web/contracts/api.md} §5.2、§5.3、§6.3）。
 *
 * <p>{@code parentBranchId} 与 {@code source} 恰好在根分支同时为 null；非根分支必须带 source。
 * 未读由序列差推导，{@code unreadCount} 为派生只读值。
 */
public final class BranchDtos {

    private BranchDtos() {}

    /** §5.2 Branch。 */
    public record Branch(
            String id,
            String topicId,
            String parentBranchId,
            BranchSource source,
            String title,
            long createdAtMillis,
            long updatedAtMillis,
            int latestStableSequence,
            int lastSeenStableSequence,
            int unreadCount,
            boolean collapsed,
            Long archivedAtMillis) {}

    /** §5.2 source：分叉溯源（父分支、来源消息/版本、锚点、出生快照）。 */
    public record BranchSource(
            String parentBranchId,
            String sourceMessageId,
            String sourceRevisionId,
            BranchAnchor anchor,
            String contextSnapshotId) {}

    /** §5.2 anchor：整条分叉时四字段全 null；选文分叉时区间 + quote + hash 必填。 */
    public record BranchAnchor(
            Integer startInclusive,
            Integer endExclusive,
            String quote,
            String hash) {}

    /** §5.3 BranchTree；nodes 根在前，归档分支仍返回。 */
    public record BranchTree(
            String topicId,
            List<Branch> nodes) {}

    /** §6.3 POST /api/branches/{id}/forks 响应；bootstrap 仅在有 question 时出现。 */
    public record ForkResult(
            Branch branch,
            BranchSource source,
            TopicDtos.TopicBootstrap bootstrap) {}

    /** §6.3 阅读位置（语义锚点）：revisionId 为 null 表示“跟随当前位置显示的版本”。 */
    public record ReadingAnchor(
            String messageId,
            String revisionId,
            int listItemOffset,
            int viewportOffset) {}

    /** §6.3 PUT /api/branches/{id}/reading-position 响应：写回后的阅读位置。 */
    public record ReadingPositionSaved(
            String branchId,
            ReadingAnchor readingAnchor) {}

    /** §6.3 POST /api/branches/{id}/delete 响应；重放（目标已不存在）为空数组。 */
    public record BranchDeleted(
            List<String> deletedBranchIds) {}
}
