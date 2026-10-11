package com.treelearn.web.generation;

import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.OperationStore;
import com.treelearn.web.storage.TopicStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 分支生命周期与删除的判定与执行（任务 T-2026-10-10-042，契约 §6.2/§6.3）。
 * <p><strong>判定只此一处。</strong>根分支不可归档、归档/恢复状态冲突、{@code seen} 只前进这三条
 * 规则的裁决者是共享域 {@link Branch}（{@code isRoot}/{@code isArchived}/{@code markSeenThrough}），
 * 本服务只把域的答案翻译成 §3.3 错误码，不复制判定。删除范围与 {@code digest} 同理收在
 * {@link #plan} 一个实现里：范围由 {@code listForTopic} 快照 + BFS 计算（父在子前，对照 Android
 * {@code NavigationData.plan} 的 fail-closed 口径），{@code digest} 是该范围的 SHA-256 指纹。
 * 返回值保持域对象/值对象，§5 DTO 映射留在 {@code api} 层控制器。
 *
 * <p><strong>删除顺序固定</strong>（契约 §6.2）：先取消范围内活跃操作（{@code coordinator.cancel}，
 * 进程内无该尝试的重启遗留直接落 {@code CANCELED} 终态），再单事务删除。归档恰好相反——
 * 归档从不取消生成（{@link #archive} 只写 {@code archived_at}）。
 *
 * <p><strong>幂等</strong>：生命周期命令无持久 {@code commandId} 载体（schema 无该列，本任务不改
 * schema），删除的幂等以「目标已不存在视为成功」实现，重放返回空列表。
 */
public class BranchLifecycleService {

    private final TopicStore topics;
    private final BranchStore branches;
    private final MessageStore messages;
    private final AnswerRevisionStore revisions;
    private final OperationStore operations;
    private final DesktopGenerationCoordinator coordinator;
    private final Clock clock;

    public BranchLifecycleService(TopicStore topics, BranchStore branches, MessageStore messages,
            AnswerRevisionStore revisions, OperationStore operations,
            DesktopGenerationCoordinator coordinator, Clock clock) {
        this.topics = topics;
        this.branches = branches;
        this.messages = messages;
        this.revisions = revisions;
        this.operations = operations;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ archive / restore

    /** 归档：根分支不可归档；已归档再归档为状态冲突；归档不取消生成。 */
    public Branch archive(String branchId, String commandId) {
        requireCommand(commandId);
        Branch branch = requireBranch(branchId);
        if (branch.isRoot()) {
            throw new ServiceRefusalException("BRANCH_NOT_ARCHIVABLE", Map.of("branchId", branchId));
        }
        if (branch.isArchived()) {
            throw illegalTransition("ARCHIVED");
        }
        return branches.archive(branchId, clock.millis());
    }

    /** 恢复：根分支同样不可操作；未归档再恢复为状态冲突。 */
    public Branch restore(String branchId, String commandId) {
        requireCommand(commandId);
        Branch branch = requireBranch(branchId);
        if (branch.isRoot()) {
            throw new ServiceRefusalException("BRANCH_NOT_ARCHIVABLE", Map.of("branchId", branchId));
        }
        if (!branch.isArchived()) {
            throw illegalTransition("NOT_ARCHIVED");
        }
        return branches.restore(branchId, clock.millis());
    }

    // ------------------------------------------------------------------------ collapse

    /** 折叠/展开是视图状态：只写 {@code collapsed}，不触碰未读序列。 */
    public Branch collapse(String branchId, String commandId, boolean collapsed) {
        requireCommand(commandId);
        requireBranch(branchId);
        return branches.setCollapsed(branchId, collapsed, clock.millis());
    }

    // ---------------------------------------------------------------------------- seen

    /** 标记已见：只前进、不超过已提交值；域拒绝时被拒值不得写入。 */
    public Branch seen(String branchId, String commandId, int seenThroughSequence) {
        requireCommand(commandId);
        requireBranch(branchId);
        DomainResult<Void> outcome = branches.markSeenThrough(branchId, seenThroughSequence);
        if (outcome.isFailure()) {
            throw new ServiceRefusalException(outcome.error().code.name(),
                    Map.of("seenThroughSequence", seenThroughSequence));
        }
        return branches.get(branchId).orElseThrow(() -> notFound("branch"));
    }

    // --------------------------------------------------------------- reading position

    /** 保存阅读位置：校验消息/版本存在以保证外键成立，负偏移为客户端可修复错误。 */
    public ReadingAnchor writeReadingPosition(String branchId, String commandId, String messageId,
            String revisionId, int listItemOffset, int viewportOffset) {
        requireCommand(commandId);
        requireBranch(branchId);
        if (messages.get(messageId).isEmpty()) {
            throw notFound("message");
        }
        if (revisionId != null && revisions.get(revisionId).isEmpty()) {
            throw notFound("revision");
        }
        ReadingAnchor anchor;
        try {
            anchor = ReadingAnchor.of(messageId, revisionId, listItemOffset, viewportOffset);
        } catch (IllegalArgumentException malformed) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
        branches.writeReadingPosition(branchId, anchor);
        return anchor;
    }

    // ---------------------------------------------------------------- deletion preview

    /** 子树删除预览（契约 §6.3）；根分支预览等价整树（§6.2 语义）。 */
    public Plan branchDeletionPreview(String branchId) {
        Branch branch = requireBranch(branchId);
        return requirePlan(plan(branch.topicId, branchId));
    }

    /** 整树删除预览（契约 §6.2）：范围是主题下全部分支。 */
    public Plan topicDeletionPreview(String topicId) {
        requireTopic(topicId);
        return requirePlan(plan(topicId, rootBranchIdOf(topicId)));
    }

    // -------------------------------------------------------------------- delete flows

    /**
     * 确认删除子树；目标已不存在视为幂等成功，返回实际删除的分支 id 列表（重放为空列表）。
     * 根分支删除等价整树删除：走 {@code TopicStore.deleteCascade}。
     */
    public List<String> deleteBranch(String branchId, String commandId, String digest) {
        requireCommand(commandId);
        requireDigest(digest);
        Optional<Branch> existing = branches.get(branchId);
        if (existing.isEmpty()) {
            return List.of();
        }
        Plan plan = requirePlan(plan(existing.get().topicId, branchId));
        requireDigestMatch(plan, digest);
        cancelInFlight(plan.ids());
        if (plan.wholeTopic()) {
            topics.deleteCascade(plan.topicId());
        } else {
            branches.deleteSubtree(branchId);
        }
        return plan.ids();
    }

    /** 确认删除整树；主题已不存在视为幂等成功，返回实际删除的分支 id 列表。 */
    public List<String> deleteTopic(String topicId, String commandId, String digest) {
        requireCommand(commandId);
        requireDigest(digest);
        if (topics.get(topicId).isEmpty()) {
            return List.of();
        }
        Plan plan = requirePlan(plan(topicId, rootBranchIdOf(topicId)));
        requireDigestMatch(plan, digest);
        cancelInFlight(plan.ids());
        topics.deleteCascade(topicId);
        return plan.ids();
    }

    // ------------------------------------------------------------------------ plan

    /**
     * One deletion plan: the scope (target first, then descendants breadth-first) and whether it
     * covers the whole topic. Computed from a {@code listForTopic} snapshot so the digest commits to
     * one readable state of the tree, and fail-closed: a dangling parent pointer or a missing target
     * yields {@code null} — the caller turns that into {@code 409 SCOPE_UNRESOLVABLE} rather than
     * guessing a smaller scope.
     */
    private Plan plan(String topicId, String targetBranchId) {
        List<Branch> all = branches.listForTopic(topicId);
        Map<String, Branch> byId = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (Branch branch : all) {
            byId.put(branch.id, branch);
            children.computeIfAbsent(branch.parentBranchId, key -> new ArrayList<>()).add(branch.id);
        }
        for (Branch branch : all) {
            if (branch.parentBranchId != null && !byId.containsKey(branch.parentBranchId)) {
                return null;
            }
        }
        if (!byId.containsKey(targetBranchId)) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(targetBranchId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            ids.add(current);
            queue.addAll(children.getOrDefault(current, List.of()));
        }
        return new Plan(topicId, targetBranchId, byId.get(targetBranchId).isRoot(), ids);
    }

    private Plan requirePlan(Plan plan) {
        if (plan == null) {
            throw new ServiceRefusalException("SCOPE_UNRESOLVABLE", Map.of());
        }
        return plan;
    }

    /** 校验 digest 与当前计划一致：不一致 = 预览后树已变化，拒绝半删。 */
    private void requireDigestMatch(Plan plan, String digest) {
        if (!digest.equals(digestOf(plan))) {
            throw new ServiceRefusalException("DELETION_PLAN_EXPIRED", Map.of());
        }
    }

    /** 指纹原文：主题、目标、是否整树与范围 id 序列（父在子前），SHA-256 十六进制。 */
    public static String digestOf(Plan plan) {
        String canonical = plan.topicId() + "\n" + plan.targetBranchId() + "\n" + plan.wholeTopic()
                + "\n" + String.join(",", plan.ids());
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hashed = sha256.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is missing from the JVM", missing);
        }
    }

    // ----------------------------------------------------------------- cancel + delete

    /**
     * 取消范围内的活跃操作：进程内仍登记的尝试走 {@code coordinator.cancel}（中止流、保留部分内容、
     * 落 CANCELED）；重启遗留（注册表里没有的）直接落终态——对照 Android {@code NavigationData
     * .cancelInFlight} 与 {@code InterruptionSweeper} 的收尾。
     */
    private void cancelInFlight(List<String> branchIds) {
        Set<String> scope = new HashSet<>(branchIds);
        long now = clock.millis();
        for (OperationStore.Operation operation : operations.listActive()) {
            if (!scope.contains(operation.branchId)) {
                continue;
            }
            if (!coordinator.cancel(operation.id)) {
                operations.markTerminal(operation.id, GenerationState.CANCELED, null, now);
            }
        }
    }

    // --------------------------------------------------------------------- guard rails

    private Branch requireBranch(String branchId) {
        return branches.get(branchId).orElseThrow(() -> notFound("branch"));
    }

    private void requireTopic(String topicId) {
        topics.get(topicId).orElseThrow(() -> notFound("topic"));
    }

    private String rootBranchIdOf(String topicId) {
        return branches.listForTopic(topicId).stream().filter(Branch::isRoot).findFirst()
                .map(branch -> branch.id)
                .orElseThrow(() -> new ServiceRefusalException("SCOPE_UNRESOLVABLE", Map.of()));
    }

    /**
     * 校验 {@code commandId}：必填且必须是规范 UUID（契约 §4），与 {@code ChatCommandService} 同一口径。
     */
    private static void requireCommand(String commandId) {
        if (commandId == null || commandId.trim().isEmpty()) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
        try {
            Guard.id(commandId, "commandId");
        } catch (IllegalArgumentException malformed) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
    }

    private static void requireDigest(String digest) {
        if (digest == null || digest.isBlank()) {
            throw new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
        }
    }

    private static ServiceRefusalException illegalTransition(String state) {
        return new ServiceRefusalException("ILLEGAL_STATE_TRANSITION", Map.of("state", state));
    }

    private static ServiceRefusalException notFound(String resource) {
        return new ServiceRefusalException("NOT_FOUND", Map.of("resource", resource));
    }

    /** 删除计划值对象：范围 id 序列父在子前，{@code wholeTopic} 以目标是否根分支为准。 */
    public record Plan(String topicId, String targetBranchId, boolean wholeTopic, List<String> ids) {}
}
