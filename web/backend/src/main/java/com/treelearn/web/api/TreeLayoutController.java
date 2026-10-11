package com.treelearn.web.api;

import com.treelearn.core.domain.Branch;
import com.treelearn.treelayout.Bounds;
import com.treelearn.treelayout.BranchGeometry;
import com.treelearn.treelayout.Density;
import com.treelearn.treelayout.HitRegion;
import com.treelearn.treelayout.LabelGeometry;
import com.treelearn.treelayout.LayoutNode;
import com.treelearn.treelayout.LayoutResult;
import com.treelearn.treelayout.TreeLayout;
import com.treelearn.treelayout.TreeSnapshot;
import com.treelearn.treelayout.ViewportConstraints;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.generation.ServiceRefusalException;
import com.treelearn.web.storage.ArtifactStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 树图布局端点（契约 §6.11，任务 T-2026-10-10-045）。
 *
 * <p>几何全部由共享 {@code :tree-layout} 模块计算（与 Android {@code TreeCanvasPresenter} 同一实现），
 * 本控制器只做数据装配与 DTO 投影，不自行推导任何几何。装配镜像 Android
 * {@code TreeCanvasWiring}/{@code TreeCanvasPresenter.snapshotOf}：行序保持
 * {@link BranchStore#listForTopic} 的顺序；{@code rounds} 为该分支已提交 USER 消息数；
 * {@code summary} 取 {@code branch_summary} artifact payload JSON 的 {@code summary} 字段文本
 * （缺失/畸形/空一律为空串——「没有摘要」不是占位符）；父行缺失的孤儿行跳过；根行缺失返回空布局。
 * 唯一的 fail-closed 出口：根行被绕过领域规则置为 archived 时，共享快照校验会拒绝，这里转
 * {@code STORAGE_REFUSED}——域不变量被绕过属于存储损坏，不能用猜测布局掩盖。
 */
@RestController
class TreeLayoutController {

    /** 固定视口（契约 §6.11）：前端只做缩放平移适配，不参与几何计算。 */
    private static final ViewportConstraints VIEWPORT = ViewportConstraints.of(800f, 1200f);
    private static final float DEFAULT_SCALE = 1.0f;

    private final JdbcSupport jdbc;
    private final BranchStore branches;
    private final ArtifactStore artifacts;

    TreeLayoutController(JdbcSupport jdbc, BranchStore branches, ArtifactStore artifacts) {
        this.jdbc = jdbc;
        this.branches = branches;
        this.artifacts = artifacts;
    }

    // ------------------------------------------------------------------ DTOs

    /** 响应 DTO：字段名与 {@code :tree-layout} 几何类一一对应（契约 §6.11）。 */
    public record TreeLayoutResponse(
            String topicId,
            String currentBranchId,
            BoundsDto bounds,
            List<BranchDto> branches,
            List<LabelDto> labels,
            List<HitDto> hits) {}

    public record BoundsDto(float minX, float minY, float maxX, float maxY) {}

    public record BranchDto(String branchId, String kind, int depth, int rounds, int ownUnread,
            int hiddenDescendants, int hiddenUnread, float baseX, float baseY, float tipX,
            float tipY, float baseWidth, float tipWidth, float length, float direction,
            boolean onCurrentPath) {}

    public record LabelDto(String branchId, float x, float y, float width, float height, int row,
            boolean showSummary, float leaderX, float leaderY, String title, String summary) {}

    public record HitDto(String branchId, String source, float x, float y, float width,
            float height) {}

    // ---------------------------------------------------------------- routes

    @GetMapping("/api/topics/{id}/tree-layout")
    TreeLayoutResponse treeLayout(@PathVariable String id,
            @RequestParam(required = false) String scale,
            @RequestParam(required = false) String currentBranchId) {
        double scaleValue = DEFAULT_SCALE;
        if (scale != null && !scale.isBlank()) {
            try {
                scaleValue = Double.parseDouble(scale.trim());
            } catch (NumberFormatException ignored) {
                throw new ServiceRefusalException("SCALE_INVALID", Map.of("field", "scale"));
            }
            if (!Double.isFinite(scaleValue) || scaleValue <= 0) {
                throw new ServiceRefusalException("SCALE_INVALID", Map.of("field", "scale"));
            }
        }
        Integer topicRows = jdbc.queryOne("SELECT COUNT(*) FROM " + DesktopSchema.Topics.TABLE
                        + " WHERE " + DesktopSchema.Topics.ID + " = ?",
                s -> s.setString(1, id), rows -> Integer.valueOf(rows.getInt(1))).orElse(0);
        if (topicRows == 0) {
            throw new ServiceRefusalException("NOT_FOUND", Map.of("resource", "topic"));
        }

        Map<String, String> titleTexts = new java.util.LinkedHashMap<>();
        Map<String, String> summaryTexts = new java.util.LinkedHashMap<>();
        TreeSnapshot snapshot = snapshotOf(id, titleTexts, summaryTexts);
        LayoutResult layout = snapshot.isEmpty()
                ? LayoutResult.empty()
                : TreeLayout.compute(snapshot, currentBranchId, Density.forScale((float) scaleValue),
                        VIEWPORT);
        return new TreeLayoutResponse(id, currentBranchId, bounds(layout.bounds()),
                branches(layout), labels(layout, titleTexts, summaryTexts), hits(layout));
    }

    // --------------------------------------------------------------- mapping

    /** 镜像 Android {@code TreeCanvasPresenter.snapshotOf}：孤儿行跳过、无根=空、根 archived=fail-closed。 */
    private TreeSnapshot snapshotOf(String topicId, Map<String, String> titleTexts,
            Map<String, String> summaryTexts) {
        List<Branch> rows = branches.listForTopic(topicId);
        Set<String> ids = new HashSet<>();
        for (Branch branch : rows) {
            ids.add(branch.id);
        }
        List<LayoutNode> nodes = new ArrayList<>(rows.size());
        String rootId = null;
        for (Branch branch : rows) {
            String parentId = branch.parentBranchId;
            if (parentId != null && !ids.contains(parentId)) {
                continue;
            }
            if (parentId == null && rootId == null) {
                rootId = branch.id;
            }
            String summary = summaryOf(branch.id);
            titleTexts.put(branch.id, branch.title() == null ? "" : branch.title());
            summaryTexts.put(branch.id, summary);
            nodes.add(new LayoutNode(branch.id, parentId, branch.title(), summary,
                    committedRounds(branch.id), (int) branch.unreadCount(), branch.isCollapsed(),
                    branch.isArchived()));
        }
        if (rootId == null || nodes.isEmpty()) {
            return TreeSnapshot.empty();
        }
        try {
            return TreeSnapshot.of(rootId, nodes);
        } catch (IllegalArgumentException invariantViolated) {
            // 只有「根行 archived」一类域不变量绕过能走到这里（孤儿/无根已被上游消化）：
            // fail-closed，不用猜测布局掩盖存储损坏。
            throw new ServiceRefusalException("STORAGE_REFUSED",
                    Map.of("reason", String.valueOf(invariantViolated.getMessage())));
        }
    }

    /** 该分支已提交 USER 消息数；生成中的行不在 messages 表，无「未提交」混入面。 */
    private int committedRounds(String branchId) {
        return jdbc.queryOne("SELECT COUNT(*) FROM " + DesktopSchema.Messages.TABLE
                        + " WHERE " + DesktopSchema.Messages.BRANCH_ID + " = ? AND "
                        + DesktopSchema.Messages.ROLE + " = 'USER'",
                s -> s.setString(1, branchId), rows -> Integer.valueOf(rows.getInt(1))).orElse(0);
    }

    /** {@code branch_summary} artifact payload JSON 的 summary 字段文本；缺失/畸形/空=空串。 */
    private String summaryOf(String branchId) {
        return artifacts.find("branch_summary", branchId)
                .map(artifact -> {
                    try {
                        var payload = ApiJson.mapper().readTree(artifact.payload);
                        var summary = payload.get("summary");
                        return summary == null || summary.isNull() ? "" : summary.asText();
                    } catch (RuntimeException malformed) {
                        return "";
                    }
                })
                .orElse("");
    }

    private static BoundsDto bounds(Bounds bounds) {
        return new BoundsDto(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY);
    }

    private static List<BranchDto> branches(LayoutResult layout) {
        List<BranchDto> out = new ArrayList<>(layout.branches().size());
        for (BranchGeometry b : layout.branches()) {
            out.add(new BranchDto(b.branchId, b.kind.name(), b.depth, b.rounds, b.ownUnread,
                    b.hiddenDescendants, b.hiddenUnread, b.baseX, b.baseY, b.tipX, b.tipY,
                    b.baseWidth, b.tipWidth, b.length, b.direction, b.onCurrentPath));
        }
        return out;
    }

    private static List<LabelDto> labels(LayoutResult layout, Map<String, String> titleTexts,
            Map<String, String> summaryTexts) {
        List<LabelDto> out = new ArrayList<>(layout.labels().size());
        for (LabelGeometry l : layout.labels()) {
            out.add(new LabelDto(l.branchId, l.x, l.y, l.width, l.height, l.row, l.showSummary,
                    l.leaderX, l.leaderY,
                    titleTexts.getOrDefault(l.branchId, ""),
                    l.showSummary ? summaryTexts.getOrDefault(l.branchId, "") : ""));
        }
        return out;
    }

    private static List<HitDto> hits(LayoutResult layout) {
        List<HitDto> out = new ArrayList<>(layout.hits().size());
        for (HitRegion h : layout.hits()) {
            out.add(new HitDto(h.branchId, h.source.name(), h.x, h.y, h.width, h.height));
        }
        return out;
    }
}
