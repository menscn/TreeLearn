/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.Canvas
 *  android.graphics.Paint
 *  android.graphics.Paint$Align
 *  android.graphics.Paint$Style
 *  android.graphics.Path
 *  android.graphics.RectF
 *  android.text.TextPaint
 *  android.text.TextUtils
 *  android.text.TextUtils$TruncateAt
 *  android.util.AttributeSet
 *  android.view.GestureDetector
 *  android.view.GestureDetector$OnGestureListener
 *  android.view.GestureDetector$SimpleOnGestureListener
 *  android.view.MotionEvent
 *  android.view.ScaleGestureDetector
 *  android.view.ScaleGestureDetector$OnScaleGestureListener
 *  android.view.ScaleGestureDetector$SimpleOnScaleGestureListener
 *  android.view.View
 *  com.treelearn.app.R$color
 *  com.treelearn.app.R$string
 *  com.treelearn.treelayout.Bounds
 *  com.treelearn.treelayout.BranchGeometry
 *  com.treelearn.treelayout.LabelGeometry
 *  com.treelearn.treelayout.ViewTransform
 */
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
import com.treelearn.treelayout.ViewTransform;

public final class TreeCanvasView
extends View {
    private static final float TITLE_TEXT = 13.0f;
    private static final float SUMMARY_TEXT = 10.0f;
    private static final float BADGE_TEXT = 10.0f;
    private static final float BUD_RADIUS = 9.0f;
    private static final float BADGE_RADIUS = 8.0f;
    private static final float LABEL_PAD = 6.0f;
    private static final float TITLE_BASELINE = 16.0f;
    private final Paint soil = new Paint(1);
    private final Paint wood = new Paint(1);
    private final Paint woodCurrent = new Paint(1);
    private final Paint leaf = new Paint(1);
    private final Paint leafUnread = new Paint(1);
    private final Paint accent = new Paint(1);
    private final Paint plate = new Paint(1);
    private final Paint plateEdge = new Paint(1);
    private final Paint leader = new Paint(1);
    private final TextPaint title = new TextPaint(1);
    private final TextPaint summary = new TextPaint(1);
    private final TextPaint badge = new TextPaint(1);
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
        this.soil.setColor(this.color(R.color.tl_line));
        this.wood.setColor(this.color(R.color.tl_green));
        this.woodCurrent.setColor(this.color(R.color.tl_green_deep));
        this.leaf.setColor(this.color(R.color.tl_green));
        this.leafUnread.setColor(this.color(R.color.tl_amber));
        this.accent.setColor(this.color(R.color.tl_amber));
        this.plate.setColor(this.color(R.color.tl_surface));
        this.plateEdge.setColor(this.color(R.color.tl_line));
        this.plateEdge.setStyle(Paint.Style.STROKE);
        this.plateEdge.setStrokeWidth(1.0f);
        this.leader.setColor(this.color(R.color.tl_ink3));
        this.leader.setStrokeWidth(1.0f);
        this.title.setColor(this.color(R.color.tl_ink));
        this.title.setTextSize(13.0f);
        this.title.setTextAlign(Paint.Align.CENTER);
        this.summary.setColor(this.color(R.color.tl_ink2));
        this.summary.setTextSize(10.0f);
        this.summary.setTextAlign(Paint.Align.CENTER);
        this.badge.setColor(this.color(R.color.tl_surface));
        this.badge.setTextSize(10.0f);
        this.badge.setTextAlign(Paint.Align.CENTER);
        this.setContentDescription(context.getString(R.string.tree_canvas_description));
        this.pinch = new ScaleGestureDetector(context, (ScaleGestureDetector.OnScaleGestureListener)new ScaleGestureDetector.SimpleOnScaleGestureListener(){

            public boolean onScale(ScaleGestureDetector detector) {
                if (TreeCanvasView.this.actions != null) {
                    TreeCanvasView.this.actions.onZoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                }
                return true;
            }
        });
        this.taps = new GestureDetector(context, (GestureDetector.OnGestureListener)new GestureDetector.SimpleOnGestureListener(){

            public boolean onDown(MotionEvent event) {
                return true;
            }

            public boolean onSingleTapUp(MotionEvent event) {
                if (TreeCanvasView.this.actions != null) {
                    TreeCanvasView.this.actions.onTap(event.getX(), event.getY());
                }
                return true;
            }
        });
    }

    public void setActions(TreeCanvasActions value) {
        this.actions = value;
    }

    public void render(TreeCanvasViewState next) {
        this.state = next;
        this.invalidate();
    }

    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (this.actions != null && width > 0 && height > 0) {
            this.actions.onViewportChanged(width, height);
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        this.pinch.onTouchEvent(event);
        this.taps.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case 0: {
                this.lastX = event.getX();
                this.lastY = event.getY();
                this.dragging = true;
                return true;
            }
            case 2: {
                if (this.dragging && !this.pinch.isInProgress() && this.actions != null) {
                    this.actions.onPan(event.getX() - this.lastX, event.getY() - this.lastY);
                }
                this.lastX = event.getX();
                this.lastY = event.getY();
                return true;
            }
            case 1: 
            case 3: {
                this.dragging = false;
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        TreeCanvasViewState frame = this.state;
        if (frame == null || frame.snapshot == null) {
            return;
        }
        ViewTransform transform = frame.transform;
        canvas.save();
        canvas.translate(transform.translateX, transform.translateY);
        canvas.scale(transform.scale, transform.scale);
        this.drawSoil(canvas, frame);
        for (BranchGeometry branch : frame.layout.branches()) {
            this.drawBranch(canvas, branch);
        }
        for (LabelGeometry label : frame.layout.labels()) {
            this.drawPlate(canvas, frame, label);
        }
        canvas.restore();
    }

    private void drawSoil(Canvas canvas, TreeCanvasViewState frame) {
        Bounds bounds = frame.layout.bounds();
        if (bounds.isEmpty()) {
            return;
        }
        this.box.set(bounds.minX, 0.0f, bounds.maxX, 44.0f);
        canvas.drawRoundRect(this.box, 6.0f, 6.0f, this.soil);
    }

    private void drawBranch(Canvas canvas, BranchGeometry branch) {
        switch (branch.kind) {
            case LEAF: {
                this.drawLeaf(canvas, branch);
                break;
            }
            case BUD: {
                canvas.drawCircle(branch.tipX, branch.tipY, 9.0f, this.accent);
                canvas.drawCircle(branch.tipX, branch.tipY, 4.5f, this.plate);
                break;
            }
            default: {
                this.drawLimb(canvas, branch);
            }
        }
        if (branch.unreadTotal() > 0) {
            this.drawUnread(canvas, branch);
        }
    }

    private void drawLimb(Canvas canvas, BranchGeometry branch) {
        float half = Math.max(1.0f, branch.baseWidth / 2.0f);
        float sideX = (float)Math.cos(branch.direction);
        float sideY = (float)Math.sin(branch.direction);
        this.shape.reset();
        this.shape.moveTo(branch.baseX + sideX * half, branch.baseY + sideY * half);
        this.shape.lineTo(branch.tipX + sideX * 1.5f, branch.tipY + sideY * 1.5f);
        this.shape.lineTo(branch.tipX - sideX * 1.5f, branch.tipY - sideY * 1.5f);
        this.shape.lineTo(branch.baseX - sideX * half, branch.baseY - sideY * half);
        this.shape.close();
        canvas.drawPath(this.shape, branch.onCurrentPath ? this.woodCurrent : this.wood);
    }

    private void drawLeaf(Canvas canvas, BranchGeometry branch) {
        float forwardX = (float)Math.sin(branch.direction);
        float forwardY = -((float)Math.cos(branch.direction));
        float sideX = (float)Math.cos(branch.direction);
        float sideY = (float)Math.sin(branch.direction);
        float halfWidth = 7.5f;
        float length = 34.0f;
        float middleX = branch.tipX + forwardX * length * 0.5f;
        float middleY = branch.tipY + forwardY * length * 0.5f;
        this.shape.reset();
        this.shape.moveTo(branch.tipX, branch.tipY);
        this.shape.quadTo(middleX + sideX * halfWidth, middleY + sideY * halfWidth, branch.tipX + forwardX * length, branch.tipY + forwardY * length);
        this.shape.quadTo(middleX - sideX * halfWidth, middleY - sideY * halfWidth, branch.tipX, branch.tipY);
        canvas.drawPath(this.shape, branch.unreadTotal() > 0 ? this.leafUnread : this.leaf);
    }

    private void drawUnread(Canvas canvas, BranchGeometry branch) {
        float x = branch.anchor().x;
        float y = branch.anchor().y - 8.0f - 2.0f;
        canvas.drawCircle(x, y, 8.0f, this.accent);
        canvas.drawText(Integer.toString(branch.unreadTotal()), x, y + 3.6000001f, (Paint)this.badge);
    }

    private void drawPlate(Canvas canvas, TreeCanvasViewState frame, LabelGeometry label) {
        this.box.set(label.x, label.y, label.x + label.width, label.y + label.height);
        canvas.drawRoundRect(this.box, 7.0f, 7.0f, this.plate);
        canvas.drawRoundRect(this.box, 7.0f, 7.0f, this.plateEdge);
        canvas.drawLine(label.x + label.width / 2.0f, label.y + label.height, label.leaderX, label.leaderY, this.leader);
        float room = label.width - 12.0f;
        canvas.drawText(TextUtils.ellipsize((CharSequence)frame.titleOf(label.branchId), (TextPaint)this.title, (float)room, (TextUtils.TruncateAt)TextUtils.TruncateAt.END).toString(), label.x + label.width / 2.0f, label.y + 16.0f, (Paint)this.title);
        if (label.showSummary) {
            canvas.drawText(TextUtils.ellipsize((CharSequence)frame.summaryOf(label.branchId), (TextPaint)this.summary, (float)room, (TextUtils.TruncateAt)TextUtils.TruncateAt.END).toString(), label.x + label.width / 2.0f, label.y + label.height - 6.0f, (Paint)this.summary);
        }
    }

    private int color(int resource) {
        return this.getContext().getColor(resource);
    }
}

