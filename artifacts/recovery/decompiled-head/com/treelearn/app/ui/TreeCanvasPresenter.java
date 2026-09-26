/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.core.domain.DomainErrorCode
 *  com.treelearn.core.domain.DomainResult
 *  com.treelearn.core.domain.Guard
 *  com.treelearn.treelayout.BranchGeometry
 *  com.treelearn.treelayout.BranchGeometry$Kind
 *  com.treelearn.treelayout.Density
 *  com.treelearn.treelayout.HitRegion
 *  com.treelearn.treelayout.LayoutNode
 *  com.treelearn.treelayout.LayoutResult
 *  com.treelearn.treelayout.Pt
 *  com.treelearn.treelayout.TreeLayout
 *  com.treelearn.treelayout.TreeSnapshot
 *  com.treelearn.treelayout.ViewTransform
 *  com.treelearn.treelayout.ViewportConstraints
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.PopupPlacement;
import com.treelearn.app.ui.TreeCanvasActions;
import com.treelearn.app.ui.TreeCanvasCommands;
import com.treelearn.app.ui.TreeCanvasData;
import com.treelearn.app.ui.TreeCanvasSink;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.Branch;
import com.treelearn.core.domain.DomainErrorCode;
import com.treelearn.core.domain.DomainResult;
import com.treelearn.core.domain.Guard;
import com.treelearn.treelayout.BranchGeometry;
import com.treelearn.treelayout.Density;
import com.treelearn.treelayout.HitRegion;
import com.treelearn.treelayout.LayoutNode;
import com.treelearn.treelayout.LayoutResult;
import com.treelearn.treelayout.Pt;
import com.treelearn.treelayout.TreeLayout;
import com.treelearn.treelayout.TreeSnapshot;
import com.treelearn.treelayout.ViewTransform;
import com.treelearn.treelayout.ViewportConstraints;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executor;

public final class TreeCanvasPresenter
implements TreeCanvasActions {
    private static final float POPUP_WIDTH_DP = 244.0f;
    private static final float POPUP_HEIGHT_DP = 116.0f;
    private final TreeCanvasSink sink;
    private final UiThread ui;
    private final Executor background;
    private final TreeCanvasData data;
    private final TreeCanvasCommands commands;
    private final float pxPerDp;
    private final String requestedBranchId;
    private TreeCanvasViewState state;
    private TreeSnapshot snapshot;

    public TreeCanvasPresenter(TreeCanvasSink sink, UiThread ui, Executor background, TreeCanvasData data, TreeCanvasCommands commands, ViewportConstraints viewport, float pxPerDp, String branchId) {
        this.sink = TreeCanvasPresenter.require(sink, "sink");
        this.ui = TreeCanvasPresenter.require(ui, "ui");
        this.background = TreeCanvasPresenter.require(background, "background");
        this.data = TreeCanvasPresenter.require(data, "data");
        this.commands = TreeCanvasPresenter.require(commands, "commands");
        this.pxPerDp = pxPerDp > 0.0f && Float.isFinite(pxPerDp) ? pxPerDp : 1.0f;
        this.requestedBranchId = Guard.id((String)branchId, (String)"branchId");
        this.state = TreeCanvasViewState.initial(null, this.requestedBranchId, TreeCanvasPresenter.require(viewport, "viewport"));
    }

    public TreeCanvasViewState state() {
        return this.state;
    }

    public void start() {
        this.render(this.state.withPhase(TreeCanvasViewState.Phase.LOADING));
        this.background.execute(this::read);
    }

    public void onResumed() {
        this.background.execute(this::read);
    }

    private void read() {
        TreeSnapshot built;
        String topicId;
        try {
            topicId = this.data.topicOf(this.requestedBranchId);
            if (topicId == null) {
                this.ui.post(() -> this.render(this.blank(null)));
                return;
            }
            built = TreeCanvasPresenter.snapshotOf(this.data.load(topicId));
        }
        catch (RuntimeException unreadable) {
            this.ui.post(() -> this.render(this.state.withPhase(TreeCanvasViewState.Phase.ERROR).withNotice(HomeViewState.Notice.STORAGE)));
            return;
        }
        this.ui.post(() -> this.render(built == null ? this.blank(topicId) : this.loaded(topicId, built)));
    }

    private TreeCanvasViewState blank(String topicId) {
        this.snapshot = null;
        return this.state.withTopic(topicId).withCurrentBranch(null).withSnapshot(null).withPhase(TreeCanvasViewState.Phase.EMPTY).withFrame(LayoutResult.empty(), ViewTransform.identity()).withPopup(null).withNotice(null);
    }

    private TreeCanvasViewState loaded(String topicId, TreeSnapshot built) {
        Density tier = this.state.density;
        LayoutResult layout2 = TreeLayout.compute((TreeSnapshot)built, (String)this.state.currentBranchId, (Density)tier, (ViewportConstraints)this.state.viewport);
        String current = layout2.branch(this.state.currentBranchId) != null ? this.state.currentBranchId : built.rootId();
        ViewTransform transform = this.pinned(layout2, current);
        Density fitted = Density.forScale((float)transform.scale);
        if (fitted != tier) {
            layout2 = TreeLayout.compute((TreeSnapshot)built, (String)current, (Density)fitted, (ViewportConstraints)this.state.viewport);
            if (this.state.layout.branches().isEmpty()) {
                transform = layout2.fit(this.state.viewport);
            }
        }
        this.snapshot = built;
        return this.state.withTopic(topicId).withCurrentBranch(current).withSnapshot(built).withPhase(TreeCanvasViewState.Phase.READY).withFrame(layout2, transform).withPopup(null).withNotice(null);
    }

    private ViewTransform pinned(LayoutResult after, String current) {
        if (this.state.layout.branches().isEmpty()) {
            return after.fit(this.state.viewport);
        }
        Pt delta = this.state.layout.anchorDeltaTo(after, current);
        ViewTransform previous = this.state.transform;
        if (delta == null) {
            return previous;
        }
        return new ViewTransform(previous.scale, previous.translateX + delta.x * previous.scale, previous.translateY + delta.y * previous.scale);
    }

    @Override
    public void onPan(float dx, float dy) {
        ViewTransform current = this.state.transform;
        this.render(this.state.withTransform(new ViewTransform(current.scale, current.translateX + dx, current.translateY + dy)));
    }

    @Override
    public void onZoom(float factor, float focusX, float focusY) {
        if (!Float.isFinite(factor) || factor <= 0.0f) {
            return;
        }
        ViewTransform current = this.state.transform;
        float scale = TreeCanvasPresenter.clamp(current.scale * factor, 0.32f, 1.25f);
        float ratio = scale / current.scale;
        this.render(this.atScale(new ViewTransform(scale, focusX - (focusX - current.translateX) * ratio, focusY - (focusY - current.translateY) * ratio)));
    }

    private TreeCanvasViewState atScale(ViewTransform transform) {
        Density tier = Density.forScale((float)transform.scale);
        if (this.snapshot == null || tier == this.state.density) {
            return this.state.withTransform(transform);
        }
        return this.state.withFrame(TreeLayout.compute((TreeSnapshot)this.snapshot, (String)this.state.currentBranchId, (Density)tier, (ViewportConstraints)this.state.viewport), transform);
    }

    @Override
    public void onFit() {
        ViewTransform fitted = this.state.layout.fit(this.state.viewport);
        LayoutResult layout2 = this.state.layout;
        Density tier = Density.forScale((float)fitted.scale);
        if (this.snapshot != null && tier != this.state.density) {
            layout2 = TreeLayout.compute((TreeSnapshot)this.snapshot, (String)this.state.currentBranchId, (Density)tier, (ViewportConstraints)this.state.viewport);
            fitted = layout2.fit(this.state.viewport);
        }
        this.render(this.state.withFrame(layout2, fitted));
    }

    @Override
    public void onViewportChanged(float width, float height) {
        ViewportConstraints next = ViewportConstraints.of((float)width, (float)height);
        if (this.state.viewport.width <= 0.0f || this.state.viewport.height <= 0.0f) {
            this.render(this.state.withViewport(next));
            return;
        }
        Pt anchor = this.state.layout.anchorOf(this.state.currentBranchId);
        if (anchor == null) {
            ViewTransform held = this.state.transform;
            this.render(this.state.withViewport(next).withTransform(new ViewTransform(held.scale, held.translateX + (next.width - this.state.viewport.width) / 2.0f, held.translateY + (next.height - this.state.viewport.height) / 2.0f)));
            return;
        }
        ViewTransform current = this.state.transform;
        Pt screen = current.toScreen(anchor.x, anchor.y);
        this.render(this.state.withViewport(next).withTransform(new ViewTransform(current.scale, screen.x / this.state.viewport.width * next.width - anchor.x * current.scale, screen.y / this.state.viewport.height * next.height - anchor.y * current.scale)));
    }

    @Override
    public void onTap(float screenX, float screenY) {
        Pt tree = this.state.transform.toTree(screenX, screenY);
        HitRegion hit = this.state.layout.hitTest(tree.x, tree.y);
        if (hit == null) {
            this.render(this.state.withPopup(null));
            return;
        }
        Pt anchor = this.state.layout.anchorOf(hit.branchId);
        Pt at = anchor == null ? new Pt(screenX, screenY) : this.state.transform.toScreen(anchor.x, anchor.y);
        this.render(this.state.withCurrentBranch(hit.branchId).withPopup(PopupPlacement.place(hit.branchId, at.x, at.y, this.popupWidth(), this.popupHeight(), this.state.viewport.width, this.state.viewport.height)));
    }

    private float popupWidth() {
        return Math.max(1.0f, Math.min(244.0f * this.pxPerDp, this.state.viewport.width - 16.0f));
    }

    private float popupHeight() {
        return Math.max(1.0f, Math.min(116.0f * this.pxPerDp, this.state.viewport.height - 16.0f));
    }

    @Override
    public void onDismissPopup() {
        this.render(this.state.withPopup(null));
    }

    @Override
    public void onCollapseToggled(String branchId) {
        BranchGeometry branch = this.state.layout.branch(branchId);
        if (branch == null) {
            return;
        }
        boolean fold = branch.kind != BranchGeometry.Kind.BUD;
        this.background.execute(() -> {
            DomainResult<Void> result;
            try {
                result = this.commands.setCollapsed(branchId, fold, System.currentTimeMillis());
            }
            catch (RuntimeException refused) {
                this.ui.post(() -> this.render(this.state.withNotice(HomeViewState.Notice.STORAGE)));
                return;
            }
            this.ui.post(() -> {
                if (result.isFailure()) {
                    this.render(this.state.withNotice(TreeCanvasPresenter.refusalNotice(result.error().code)));
                    return;
                }
                this.read();
            });
        });
    }

    @Override
    public void onOpenCurrent() {
        String current = this.state.currentBranchId;
        if (current != null) {
            this.sink.openBranch(current);
        }
    }

    @Override
    public void onNavigationPressed() {
        String current = this.state.currentBranchId;
        if (current != null) {
            this.sink.openNavigation(current);
        }
    }

    @Override
    public void onBackPressed() {
        this.sink.leaveTree();
    }

    private static TreeSnapshot snapshotOf(TreeCanvasData.Tree tree) {
        HashSet<String> ids = new HashSet<String>();
        for (TreeCanvasData.Row row : tree.rows) {
            ids.add(row.branch.id);
        }
        ArrayList<LayoutNode> nodes = new ArrayList<LayoutNode>();
        String rootId = null;
        for (TreeCanvasData.Row row : tree.rows) {
            Branch branch = row.branch;
            String parentId = branch.parentBranchId;
            if (parentId != null && !ids.contains(parentId)) continue;
            if (parentId == null && rootId == null) {
                rootId = branch.id;
            }
            nodes.add(new LayoutNode(branch.id, parentId, branch.title(), row.summary, row.rounds, (int)branch.unreadCount(), branch.isCollapsed(), branch.isArchived()));
        }
        if (rootId == null || nodes.isEmpty()) {
            return null;
        }
        return TreeSnapshot.of(rootId, nodes);
    }

    private static HomeViewState.Notice refusalNotice(DomainErrorCode code) {
        switch (code) {
            case OPERATION_NOT_COMPLETABLE: {
                return HomeViewState.Notice.STORAGE;
            }
        }
        throw new IllegalArgumentException("unmapped collapse refusal: " + code);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void render(TreeCanvasViewState next) {
        this.state = next;
        this.sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

