package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.treelearn.app.R;
import com.treelearn.app.ui.TreeCanvasActions;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.treelayout.Bounds;
import com.treelearn.treelayout.BranchGeometry;
import com.treelearn.treelayout.LabelGeometry;
import com.treelearn.treelayout.TreeLayout;
import com.treelearn.treelayout.ViewTransform;
import com.treelearn.treelayout.ViewportConstraints;

/**
 * The tree as one canvas (architecture 12, prototype v2.5 {@code TreeCanvas}).
 *
 * <p>It paints the geometry the layout computed and nothing else. The view never decides where a node
 * goes, never adjusts one to avoid a collision and never keeps a second copy of the matrix: the
 * transform arrives with the state, finger positions go straight back out, and every hit test happens
 * in the presenter where it can be exercised without a screen.
 *
 * <p>Everything under the matrix is drawn in tree space, so a plate's text scales with the tree the way
 * the prototype's does. That is exactly why the layout, not this class, decides when text stops being
 * worth drawing: at the far tier it places no plates at all, and a node that loses its plate keeps the
 * 44 dp target the layout gave it.
 *
 * <p>What a device adds on top of the JVM suite is the touch stream and the pixels; both are covered by
 * the instrumentation sources, which cannot run this round because no emulator is used.
 */
public final class TreeCanvasView extends View {

    private static final float TITLE_TEXT = 13f;
    private static final float SUMMARY_TEXT = 10f;
    private static final float BADGE_TEXT = 10f;
    private static final float BUD_RADIUS = 9f;
    private static final float BADGE_RADIUS = 8f;
    private static final float LABEL_PAD = 6f;
    private static final float TITLE_BASELINE = 16f;

    private final Paint soil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wood = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint woodCurrent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leaf = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leafUnread = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plateEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint leader = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint summary = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint badge = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private final RectF box = new RectF();

    private final ScaleGestureDetector pinch;
    private final GestureDetector taps;

    private TreeCanvasActions actions;
    private TreeCanvasViewState state;
    private float lastX;
    private float lastY;
    private boolean dragging;

    public TreeCanvasView(Context context) {
        this(context, null);
    }

    public TreeCanvasView(Context context, AttributeSet attributes) {
        super(context, attributes);
        soil.setColor(color(R.color.tl_line));
        wood.setColor(color(R.color.tl_green));
        woodCurrent.setColor(color(R.color.tl_green_deep));
        leaf.setColor(color(R.color.tl_green));
        leafUnread.setColor(color(R.color.tl_amber));
        accent.setColor(color(R.color.tl_amber));
        plate.setColor(color(R.color.tl_surface));
        plateEdge.setColor(color(R.color.tl_line));
        plateEdge.setStyle(Paint.Style.STROKE);
        plateEdge.setStrokeWidth(1f);
        leader.setColor(color(R.color.tl_ink3));
        leader.setStrokeWidth(1f);
        title.setColor(color(R.color.tl_ink));
        title.setTextSize(TITLE_TEXT);
        title.setTextAlign(Paint.Align.CENTER);
        summary.setColor(color(R.color.tl_ink2));
        summary.setTextSize(SUMMARY_TEXT);
        summary.setTextAlign(Paint.Align.CENTER);
        badge.setColor(color(R.color.tl_surface));
        badge.setTextSize(BADGE_TEXT);
        badge.setTextAlign(Paint.Align.CENTER);
        setContentDescription(context.getString(R.string.tree_canvas_description));

        pinch = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (actions != null) {
                            actions.onZoom(detector.getScaleFactor(), detector.getFocusX(),
                                    detector.getFocusY());
                        }
                        return true;
                    }
                });
        taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (actions != null) {
                    actions.onTap(event.getX(), event.getY());
                }
                return true;
            }
        });
    }

    public void setActions(TreeCanvasActions value) {
        this.actions = value;
    }

    /** Takes one frame to draw. The view keeps the state only so that it can repaint with it. */
    public void render(TreeCanvasViewState next) {
        this.state = next;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (actions != null && width > 0 && height > 0) {
            actions.onViewportChanged(width, height);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        pinch.onTouchEvent(event);
        taps.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !pinch.isInProgress() && actions != null) {
                    actions.onPan(event.getX() - lastX, event.getY() - lastY);
                }
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        TreeCanvasViewState frame = state;
        if (frame == null || frame.snapshot == null) {
            return;
        }
        ViewTransform transform = frame.transform;
        canvas.save();
        canvas.translate(transform.translateX, transform.translateY);
        canvas.scale(transform.scale, transform.scale);
        drawSoil(canvas, frame);
        for (BranchGeometry branch : frame.layout.branches()) {
            drawBranch(canvas, branch);
        }
        for (LabelGeometry label : frame.layout.labels()) {
            drawPlate(canvas, frame, label);
        }
        canvas.restore();
    }

    private void drawSoil(Canvas canvas, TreeCanvasViewState frame) {
        Bounds bounds = frame.layout.bounds();
        if (bounds.isEmpty()) {
            return;
        }
        box.set(bounds.minX, 0f, bounds.maxX, ViewportConstraints.DEFAULT_SOIL);
        canvas.drawRoundRect(box, 6f, 6f, soil);
    }

    private void drawBranch(Canvas canvas, BranchGeometry branch) {
        switch (branch.kind) {
            case LEAF:
                drawLeaf(canvas, branch);
                break;
            case BUD:
                canvas.drawCircle(branch.tipX, branch.tipY, BUD_RADIUS, accent);
                canvas.drawCircle(branch.tipX, branch.tipY, BUD_RADIUS / 2f, plate);
                break;
            case LIMB:
            default:
                drawLimb(canvas, branch);
                break;
        }
        if (branch.unreadTotal() > 0) {
            drawUnread(canvas, branch);
        }
    }

    private void drawLimb(Canvas canvas, BranchGeometry branch) {
        float half = Math.max(1f, branch.baseWidth / 2f);
        float sideX = (float) Math.cos(branch.direction);
        float sideY = (float) Math.sin(branch.direction);
        shape.reset();
        shape.moveTo(branch.baseX + sideX * half, branch.baseY + sideY * half);
        shape.lineTo(branch.tipX + sideX * 1.5f, branch.tipY + sideY * 1.5f);
        shape.lineTo(branch.tipX - sideX * 1.5f, branch.tipY - sideY * 1.5f);
        shape.lineTo(branch.baseX - sideX * half, branch.baseY - sideY * half);
        shape.close();
        canvas.drawPath(shape, branch.onCurrentPath ? woodCurrent : wood);
    }

    private void drawLeaf(Canvas canvas, BranchGeometry branch) {
        float forwardX = (float) Math.sin(branch.direction);
        float forwardY = -(float) Math.cos(branch.direction);
        float sideX = (float) Math.cos(branch.direction);
        float sideY = (float) Math.sin(branch.direction);
        float halfWidth = TreeLayout.LEAF_WIDTH / 2f;
        float length = TreeLayout.LEAF_LENGTH;
        float middleX = branch.tipX + forwardX * length * 0.5f;
        float middleY = branch.tipY + forwardY * length * 0.5f;
        shape.reset();
        shape.moveTo(branch.tipX, branch.tipY);
        shape.quadTo(middleX + sideX * halfWidth, middleY + sideY * halfWidth,
                branch.tipX + forwardX * length, branch.tipY + forwardY * length);
        shape.quadTo(middleX - sideX * halfWidth, middleY - sideY * halfWidth, branch.tipX,
                branch.tipY);
        canvas.drawPath(shape, branch.unreadTotal() > 0 ? leafUnread : leaf);
    }

    private void drawUnread(Canvas canvas, BranchGeometry branch) {
        float x = branch.anchor().x;
        float y = branch.anchor().y - BADGE_RADIUS - 2f;
        canvas.drawCircle(x, y, BADGE_RADIUS, accent);
        canvas.drawText(Integer.toString(branch.unreadTotal()), x, y + BADGE_TEXT * 0.36f, badge);
    }

    /**
     * One plate in the canopy bar. The text is ellipsised to the plate the layout placed rather than to
     * a width chosen here, so a long title shortens the same way at every zoom.
     */
    private void drawPlate(Canvas canvas, TreeCanvasViewState frame, LabelGeometry label) {
        box.set(label.x, label.y, label.x + label.width, label.y + label.height);
        canvas.drawRoundRect(box, 7f, 7f, plate);
        canvas.drawRoundRect(box, 7f, 7f, plateEdge);
        canvas.drawLine(label.x + label.width / 2f, label.y + label.height, label.leaderX,
                label.leaderY, leader);
        float room = label.width - 2f * LABEL_PAD;
        canvas.drawText(TextUtils.ellipsize(frame.titleOf(label.branchId), title, room,
                TextUtils.TruncateAt.END).toString(), label.x + label.width / 2f, label.y + TITLE_BASELINE,
                title);
        if (label.showSummary) {
            canvas.drawText(TextUtils.ellipsize(frame.summaryOf(label.branchId), summary, room,
                    TextUtils.TruncateAt.END).toString(), label.x + label.width / 2f, label.y + label.height
                    - LABEL_PAD, summary);
        }
    }

    private int color(int resource) {
        return getContext().getColor(resource);
    }
}