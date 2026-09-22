package com.treelearn.app.ui;

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
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The tree canvas's decisions, with no Android type in sight.
 *
 * <p>The geometry itself is T-016's and stays there; what is decided here is everything the geometry
 * cannot know because it is about a learner in front of a screen:
 *
 * <ul>
 *   <li><strong>A tap is inverted, not guessed.</strong> The finger arrives in screen pixels and is
 *       turned back through the very matrix the canvas drew with before the layout is asked what is
 *       there. Keeping the matrix here - instead of reading it back off the view - is what makes the
 *       hit test decidable on the JVM and what lets it survive a rotation.
 *   <li><strong>A reload does not move the tree under the finger.</strong> Folding, unfolding, and a
 *       branch appearing or being archived all change the geometry; the matrix is then shifted by the
 *       movement of the branch the learner is standing on, so that node keeps its screen position.
 *   <li><strong>Only an explicit fit refits.</strong> Reading the store again must not silently
 *       rescale a canvas the learner has positioned.
 *   <li><strong>The tier follows the zoom.</strong> Crossing a zoom threshold re-lays the tree out,
 *       because the plates and the summary line are placed by the layout and not by the painter.
 *   <li><strong>A refusal keeps the canvas.</strong> The same rule the home composer and the
 *       conversation follow: nothing is lost, and the failure is a category the screen renders.
 * </ul>
 *
 * <p>Every store read and every command runs on the caller's background executor; every state is
 * handed to the sink on the UI thread.
 */
public final class TreeCanvasPresenter implements TreeCanvasActions {

    /** The prototype's node card: wide enough for a title and a fold control, short enough to leave the tree visible. */
    private static final float POPUP_WIDTH_DP = 244f;
    private static final float POPUP_HEIGHT_DP = 116f;

    private final TreeCanvasSink sink;
    private final UiThread ui;
    private final Executor background;
    private final TreeCanvasData data;
    private final TreeCanvasCommands commands;
    private final float pxPerDp;
    private final String requestedBranchId;

    private TreeCanvasViewState state;
    /** The last tree read, kept so a zoom that crosses a tier can re-lay it out without a store call. */
    private TreeSnapshot snapshot;

    public TreeCanvasPresenter(TreeCanvasSink sink, UiThread ui, Executor background,
            TreeCanvasData data, TreeCanvasCommands commands, ViewportConstraints viewport,
            float pxPerDp, String branchId) {
        this.sink = require(sink, "sink");
        this.ui = require(ui, "ui");
        this.background = require(background, "background");
        this.data = require(data, "data");
        this.commands = require(commands, "commands");
        this.pxPerDp = pxPerDp > 0f && Float.isFinite(pxPerDp) ? pxPerDp : 1f;
        this.requestedBranchId = Guard.id(branchId, "branchId");
        this.state = TreeCanvasViewState.initial(null, this.requestedBranchId,
                require(viewport, "viewport"));
    }

    /** The last state handed to the sink; the tests read it, the screen does not. */
    public TreeCanvasViewState state() {
        return state;
    }

    /** First load: the reading phase is shown while the topic is read. */
    public void start() {
        render(state.withPhase(TreeCanvasViewState.Phase.LOADING));
        background.execute(this::read);
    }

    /**
     * Coming back to the canvas: a branch may have been folded, archived or created while the screen
     * was away, so the store is read again. The phase is kept so the canvas does not flash.
     */
    public void onResumed() {
        background.execute(this::read);
    }

    private void read() {
        final String topicId;
        final TreeSnapshot built;
        try {
            topicId = data.topicOf(requestedBranchId);
            if (topicId == null) {
                ui.post(() -> render(blank(null)));
                return;
            }
            built = snapshotOf(data.load(topicId));
        } catch (RuntimeException unreadable) {
            ui.post(() -> render(state.withPhase(TreeCanvasViewState.Phase.ERROR)
                    .withNotice(HomeViewState.Notice.STORAGE)));
            return;
        }
        ui.post(() -> render(built == null ? blank(topicId) : loaded(topicId, built)));
    }

    /** Nothing to draw: no topic yet, or a topic whose branches are all gone. */
    private TreeCanvasViewState blank(String topicId) {
        snapshot = null;
        return state.withTopic(topicId).withCurrentBranch(null).withSnapshot(null)
                .withPhase(TreeCanvasViewState.Phase.EMPTY)
                .withFrame(LayoutResult.empty(), ViewTransform.identity())
                .withPopup(null).withNotice(null);
    }

    /**
     * The matrix is settled before the geometry, and settled again after if the fit landed in another
     * tier: the tier decides whether plates and summary lines are placed, so a layout computed for one
     * tier and drawn at another would carry the wrong level of detail for its own zoom.
     */
    private TreeCanvasViewState loaded(String topicId, TreeSnapshot built) {
        Density tier = state.density;
        LayoutResult layout = TreeLayout.compute(built, state.currentBranchId, tier, state.viewport);
        String current = layout.branch(state.currentBranchId) != null
                ? state.currentBranchId : built.rootId();
        ViewTransform transform = pinned(layout, current);
        Density fitted = Density.forScale(transform.scale);
        if (fitted != tier) {
            layout = TreeLayout.compute(built, current, fitted, state.viewport);
            if (state.layout.branches().isEmpty()) {
                transform = layout.fit(state.viewport);
            }
        }
        snapshot = built;
        return state.withTopic(topicId).withCurrentBranch(current).withSnapshot(built)
                .withPhase(TreeCanvasViewState.Phase.READY)
                .withFrame(layout, transform)
                .withPopup(null).withNotice(null);
    }

    /**
     * The matrix this frame is drawn with. The first real load fits the whole tree; every later read
     * keeps the matrix the learner has and shifts it by the movement of the branch they are on, so a
     * fold or a new sibling does not slide the node out from under the finger.
     */
    private ViewTransform pinned(LayoutResult after, String current) {
        if (state.layout.branches().isEmpty()) {
            return after.fit(state.viewport);
        }
        Pt delta = state.layout.anchorDeltaTo(after, current);
        ViewTransform previous = state.transform;
        if (delta == null) {
            return previous;
        }
        return new ViewTransform(previous.scale, previous.translateX + delta.x * previous.scale,
                previous.translateY + delta.y * previous.scale);
    }

    @Override
    public void onPan(float dx, float dy) {
        ViewTransform current = state.transform;
        render(state.withTransform(new ViewTransform(current.scale, current.translateX + dx,
                current.translateY + dy)));
    }

    @Override
    public void onZoom(float factor, float focusX, float focusY) {
        if (!Float.isFinite(factor) || factor <= 0f) {
            return;
        }
        ViewTransform current = state.transform;
        float scale = clamp(current.scale * factor, TreeLayout.MIN_SCALE, TreeLayout.MAX_SCALE);
        float ratio = scale / current.scale;
        render(atScale(new ViewTransform(scale, focusX - (focusX - current.translateX) * ratio,
                focusY - (focusY - current.translateY) * ratio)));
    }

    /**
     * The same tree at a new matrix: re-laid out only when the zoom crossed into another tier, because
     * the tier is what decides whether a plate and a summary line are placed at all.
     */
    private TreeCanvasViewState atScale(ViewTransform transform) {
        Density tier = Density.forScale(transform.scale);
        if (snapshot == null || tier == state.density) {
            return state.withTransform(transform);
        }
        return state.withFrame(TreeLayout.compute(snapshot, state.currentBranchId, tier, state.viewport),
                transform);
    }

    @Override
    public void onFit() {
        ViewTransform fitted = state.layout.fit(state.viewport);
        LayoutResult layout = state.layout;
        Density tier = Density.forScale(fitted.scale);
        if (snapshot != null && tier != state.density) {
            layout = TreeLayout.compute(snapshot, state.currentBranchId, tier, state.viewport);
            fitted = layout.fit(state.viewport);
        }
        render(state.withFrame(layout, fitted));
    }

    @Override
    public void onViewportChanged(float width, float height) {
        ViewportConstraints next = ViewportConstraints.of(width, height);
        if (state.viewport.width <= 0f || state.viewport.height <= 0f) {
            render(state.withViewport(next));
            return;
        }
        Pt anchor = state.layout.anchorOf(state.currentBranchId);
        if (anchor == null) {
            // Nothing to pin, so the zoom is kept and the tree simply re-centres: refitting here would
            // land in another tier and leave the drawn level of detail describing the old one.
            ViewTransform held = state.transform;
            render(state.withViewport(next).withTransform(new ViewTransform(held.scale,
                    held.translateX + (next.width - state.viewport.width) / 2f,
                    held.translateY + (next.height - state.viewport.height) / 2f)));
            return;
        }
        ViewTransform current = state.transform;
        Pt screen = current.toScreen(anchor.x, anchor.y);
        render(state.withViewport(next).withTransform(new ViewTransform(current.scale,
                screen.x / state.viewport.width * next.width - anchor.x * current.scale,
                screen.y / state.viewport.height * next.height - anchor.y * current.scale)));
    }

    @Override
    public void onTap(float screenX, float screenY) {
        Pt tree = state.transform.toTree(screenX, screenY);
        HitRegion hit = state.layout.hitTest(tree.x, tree.y);
        if (hit == null) {
            render(state.withPopup(null));
            return;
        }
        Pt anchor = state.layout.anchorOf(hit.branchId);
        Pt at = anchor == null ? new Pt(screenX, screenY)
                : state.transform.toScreen(anchor.x, anchor.y);
        render(state.withCurrentBranch(hit.branchId)
                .withPopup(PopupPlacement.place(hit.branchId, at.x, at.y, popupWidth(),
                        popupHeight(), state.viewport.width, state.viewport.height)));
    }

    /** The card never asks for more room than the canvas has: a popup wider than its window cannot be placed. */
    private float popupWidth() {
        return Math.max(1f, Math.min(POPUP_WIDTH_DP * pxPerDp,
                state.viewport.width - 2f * PopupPlacement.MARGIN));
    }

    private float popupHeight() {
        return Math.max(1f, Math.min(POPUP_HEIGHT_DP * pxPerDp,
                state.viewport.height - 2f * PopupPlacement.MARGIN));
    }

    @Override
    public void onDismissPopup() {
        render(state.withPopup(null));
    }

    @Override
    public void onCollapseToggled(String branchId) {
        final BranchGeometry branch = state.layout.branch(branchId);
        if (branch == null) {
            return;
        }
        final boolean fold = branch.kind != BranchGeometry.Kind.BUD;
        background.execute(() -> {
            final DomainResult<Void> result;
            try {
                result = commands.setCollapsed(branchId, fold, System.currentTimeMillis());
            } catch (RuntimeException refused) {
                ui.post(() -> render(state.withNotice(HomeViewState.Notice.STORAGE)));
                return;
            }
            ui.post(() -> {
                if (result.isFailure()) {
                    render(state.withNotice(refusalNotice(result.error().code)));
                    return;
                }
                read();
            });
        });
    }

    @Override
    public void onOpenCurrent() {
        String current = state.currentBranchId;
        if (current != null) {
            sink.openBranch(current);
        }
    }

    /**
     * The navigation entry: the branch list of the topic the canvas is showing, standing on the branch
     * the canvas is centred on - the same branch {@link #onOpenCurrent()} would converse on.
     */
    @Override
    public void onNavigationPressed() {
        String current = state.currentBranchId;
        if (current != null) {
            sink.openNavigation(current);
        }
    }

    @Override
    public void onBackPressed() {
        sink.leaveTree();
    }

    /**
     * Builds the snapshot the geometry consumes. Rows are kept as the store ordered them, and the two
     * shapes that cannot be laid out - a row whose parent the read did not bring back, and a topic with
     * no root among its rows - are answered with "empty" rather than with a guess.
     */
    private static TreeSnapshot snapshotOf(TreeCanvasData.Tree tree) {
        Set<String> ids = new HashSet<String>();
        for (TreeCanvasData.Row row : tree.rows) {
            ids.add(row.branch.id);
        }
        List<LayoutNode> nodes = new ArrayList<LayoutNode>();
        String rootId = null;
        for (TreeCanvasData.Row row : tree.rows) {
            Branch branch = row.branch;
            String parentId = branch.parentBranchId;
            if (parentId != null && !ids.contains(parentId)) {
                continue;
            }
            if (parentId == null && rootId == null) {
                rootId = branch.id;
            }
            nodes.add(new LayoutNode(branch.id, parentId, branch.title(), row.summary, row.rounds,
                    (int) branch.unreadCount(), branch.isCollapsed(), branch.isArchived()));
        }
        if (rootId == null || nodes.isEmpty()) {
            return null;
        }
        return TreeSnapshot.of(rootId, nodes);
    }

    /** The single mapping from a local write refusal to what the canvas shows. */
    private static HomeViewState.Notice refusalNotice(DomainErrorCode code) {
        switch (code) {
            case OPERATION_NOT_COMPLETABLE:
                return HomeViewState.Notice.STORAGE;
            default:
                throw new IllegalArgumentException("unmapped collapse refusal: " + code);
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void render(TreeCanvasViewState next) {
        state = next;
        sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
