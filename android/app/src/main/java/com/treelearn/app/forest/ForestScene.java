package com.treelearn.app.forest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.treelearn.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The slanted forest ground (T-068): a custom view that draws the terrain, places one sprite per
 * topic on it, and reports which tree a tap landed on.
 *
 * <p>Everything visible is painted from the design tokens the rest of the app uses, and the tree
 * art itself is {@link ForestTreeSprite}'s pure function of the layout's seed. The view owns no
 * rule about the forest: it receives {@link ForestViewState.Tree} rows and draws them. The only
 * decisions here are painter's order (trees higher on the ground draw first so nearer trees overlap
 * them) and hit testing, where the nearest eligible tree wins and every target is at least
 * {@code touch_min} across.
 *
 * <p>The ground is deliberately not vertical top-down: the shadow ellipses sit under each trunk and
 * the canopy is offset above it, which is what makes the clearing read as a three-quarter view.
 */
public final class ForestScene extends View {

    /** The tree tap port; the screen forwards it to the presenter as a selection. */
    public interface OnTreeTappedListener {

        void onTreeTapped(String topicId);
    }

    private final Paint groundPaint = new Paint();
    private final Paint terrainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tagText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<Path> contours = new ArrayList<Path>();
    private final List<Path> grasses = new ArrayList<Path>();
    private final List<ForestViewState.Tree> trees = new ArrayList<ForestViewState.Tree>();

    private final int green;
    private final int greenSoft;
    private final int greenDeep;
    private final int surface;
    private final int line;
    private final int ink;
    private final float gutter;
    private final float touchMin;
    private final float density;
    private final ForestTreeSprite.Palette palette;

    private Shader linearGround;
    private Shader topGlow;
    private Shader bottomGlow;
    private Shader vignette;
    private Shader dots;
    private String selectedTopicId;
    private OnTreeTappedListener listener;
    private boolean pressed;

    public ForestScene(Context context) {
        this(context, null);
    }

    public ForestScene(Context context, AttributeSet attrs) {
        super(context, attrs);
        green = context.getColor(R.color.tl_green);
        greenSoft = context.getColor(R.color.tl_green_soft);
        greenDeep = context.getColor(R.color.tl_green_deep);
        surface = context.getColor(R.color.tl_surface);
        line = context.getColor(R.color.tl_line);
        ink = context.getColor(R.color.tl_ink);
        palette = new ForestTreeSprite.Palette(green, greenSoft, greenDeep);
        gutter = getResources().getDimension(R.dimen.page_gutter);
        touchMin = getResources().getDimension(R.dimen.touch_min);
        density = getResources().getDisplayMetrics().density;
        terrainPaint.setStyle(Paint.Style.STROKE);
        terrainPaint.setStrokeCap(Paint.Cap.ROUND);
        tagText.setTextSize(getResources().getDimension(R.dimen.text_cap));
        tagText.setTypeface(android.graphics.Typeface.create(tagText.getTypeface(),
                android.graphics.Typeface.BOLD));
        tagText.setColor(ink);
        setClickable(true);
    }

    /** The trees to draw, in any order; the scene sorts them for the painter's pass. */
    public void setTrees(List<ForestViewState.Tree> next, String selectedTopicId) {
        trees.clear();
        if (next != null) {
            trees.addAll(next);
        }
        trees.sort(new Comparator<ForestViewState.Tree>() {
            @Override
            public int compare(ForestViewState.Tree left, ForestViewState.Tree right) {
                int byY = Float.compare(left.placement.y, right.placement.y);
                return byY != 0 ? byY : left.topicId.compareTo(right.topicId);
            }
        });
        this.selectedTopicId = selectedTopicId;
        setContentDescription(getResources().getString(R.string.cd_forest_scene, trees.size()));
        invalidate();
    }

    public void setOnTreeTappedListener(OnTreeTappedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) {
            return;
        }
        linearGround = new LinearGradient(0f, 0f, width, height,
                new int[]{greenSoft, withAlpha(greenSoft, 0.76f), greenSoft},
                new float[]{0f, 0.44f, 1f}, Shader.TileMode.CLAMP);
        topGlow = new RadialGradient(width * 0.32f, height * 0.24f, width * 0.72f,
                new int[]{withAlpha(green, 0.10f), Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        bottomGlow = new RadialGradient(width * 0.50f, height * 1.08f, width * 0.90f,
                new int[]{withAlpha(greenSoft, 0.95f), Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        vignette = new RadialGradient(width * 0.5f, height * 0.5f,
                (float) Math.hypot(width, height) * 0.62f,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(greenSoft, 0.55f)},
                new float[]{0f, 0.60f, 1f}, Shader.TileMode.CLAMP);
        dots = new BitmapShader(dotTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        buildTerrain(width, height);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        drawGround(canvas, width, height);
        drawTerrain(canvas);
        drawTrees(canvas, width, height);
        drawSelectionTag(canvas, width, height);
    }

    private void drawGround(Canvas canvas, int width, int height) {
        groundPaint.setStyle(Paint.Style.FILL);
        groundPaint.setShader(linearGround);
        canvas.drawRect(0f, 0f, width, height, groundPaint);
        groundPaint.setShader(bottomGlow);
        canvas.drawRect(0f, 0f, width, height, groundPaint);
        groundPaint.setShader(topGlow);
        canvas.drawRect(0f, 0f, width, height, groundPaint);
        groundPaint.setShader(dots);
        groundPaint.setAlpha(255);
        canvas.drawRect(0f, 0f, width, height, groundPaint);
        groundPaint.setShader(vignette);
        canvas.drawRect(0f, 0f, width, height, groundPaint);
        groundPaint.setShader(null);
    }

    private void drawTerrain(Canvas canvas) {
        terrainPaint.setStyle(Paint.Style.STROKE);
        terrainPaint.setStrokeWidth(Math.max(1f, density));
        for (int i = 0; i < contours.size(); i++) {
            terrainPaint.setColor(withAlpha(green, i == contours.size() - 1 ? 0.06f : 0.10f));
            canvas.drawPath(contours.get(i), terrainPaint);
        }
        terrainPaint.setStrokeWidth(1.2f * density);
        terrainPaint.setColor(withAlpha(green, 0.22f));
        for (Path grass : grasses) {
            canvas.drawPath(grass, terrainPaint);
        }
        terrainPaint.setStyle(Paint.Style.FILL);
        terrainPaint.setColor(withAlpha(ink, 0.10f));
        float w = getWidth();
        float h = getHeight();
        canvas.drawOval(w * 0.800f - 7f * density, h * 0.774f - 4f * density,
                w * 0.800f + 7f * density, h * 0.774f + 4f * density, terrainPaint);
        canvas.drawOval(w * 0.174f - 6f * density, h * 0.447f - 3.5f * density,
                w * 0.174f + 6f * density, h * 0.447f + 3.5f * density, terrainPaint);
        canvas.drawOval(w * 0.528f - 5f * density, h * 0.893f - 3f * density,
                w * 0.528f + 5f * density, h * 0.893f + 3f * density, terrainPaint);
    }

    private void drawTrees(Canvas canvas, int width, int height) {
        ForestViewState.Tree selected = selected();
        for (ForestViewState.Tree tree : trees) {
            float size = tree.placement.size * height;
            float cx = tree.placement.x * width;
            float cy = tree.placement.y * height;
            if (tree == selected) {
                drawSelectionGround(canvas, cx, cy, size);
            }
            ForestTreeSprite.draw(canvas, cx - size / 2f, cy - size / 2f, size,
                    tree.placement.seed, tree.placement.tone, palette, crownPaint, path);
        }
    }

    private void drawSelectionGround(Canvas canvas, float cx, float cy, float size) {
        float radius = size * 0.70f;
        crownPaint.setStyle(Paint.Style.FILL);
        crownPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{withAlpha(green, 0.14f), Color.TRANSPARENT},
                new float[]{0.32f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, crownPaint);
        crownPaint.setShader(null);
        crownPaint.setStyle(Paint.Style.STROKE);
        crownPaint.setStrokeWidth(Math.max(1f, density));
        crownPaint.setColor(withAlpha(green, 0.32f));
        float ringCy = cy + size * 0.37f;
        canvas.drawOval(cx - size * 0.38f, ringCy - size * 0.08f,
                cx + size * 0.38f, ringCy + size * 0.08f, crownPaint);
    }

    private void drawSelectionTag(Canvas canvas, int width, int height) {
        ForestViewState.Tree selected = selected();
        if (selected == null) {
            return;
        }
        float size = selected.placement.size * height;
        float cx = selected.placement.x * width;
        float top = selected.placement.y * height - size / 2f;
        float padH = 12f * density;
        float padV = 3f * density;
        float maxWidth = 190f * density - padH * 2f;
        CharSequence label = TextUtils.ellipsize(selected.title, tagText, maxWidth,
                TextUtils.TruncateAt.END);
        float textWidth = tagText.measureText(label, 0, label.length());
        Paint.FontMetrics metrics = tagText.getFontMetrics();
        float textHeight = metrics.descent - metrics.ascent;
        float pillWidth = textWidth + padH * 2f;
        float pillHeight = textHeight + padV * 2f;
        float half = pillWidth / 2f;
        float centerX = cx;
        if (selected.placement.x > 0.72f) {
            centerX = Math.min(width - gutter - half, cx + size / 2f - half);
        } else if (selected.placement.x < 0.26f) {
            centerX = Math.max(gutter + half, cx - size / 2f + half);
        }
        centerX = Math.max(gutter + half, Math.min(width - gutter - half, centerX));
        float bottom = top - 2f * density;
        if (selected.placement.y < 0.24f || bottom - pillHeight < gutter) {
            bottom = top + size + pillHeight + 3f * density;
        }
        float left = centerX - half;
        float right = centerX + half;
        float topEdge = bottom - pillHeight;
        tagPaint.setStyle(Paint.Style.FILL);
        tagPaint.setColor(surface);
        float radius = pillHeight / 2f;
        canvas.drawRoundRect(left, topEdge, right, bottom, radius, radius, tagPaint);
        tagPaint.setStyle(Paint.Style.STROKE);
        tagPaint.setStrokeWidth(Math.max(1f, density));
        tagPaint.setColor(line);
        canvas.drawRoundRect(left, topEdge, right, bottom, radius, radius, tagPaint);
        float baseline = (topEdge + bottom) / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, 0, label.length(), left + padH, baseline, tagText);
    }

    private ForestViewState.Tree selected() {
        if (selectedTopicId == null) {
            return null;
        }
        for (ForestViewState.Tree tree : trees) {
            if (tree.topicId.equals(selectedTopicId)) {
                return tree;
            }
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (pressed) {
                    pressed = false;
                    String topicId = hitTest(event.getX(), event.getY());
                    if (topicId != null && listener != null) {
                        performClick();
                        listener.onTreeTapped(topicId);
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** The smallest hit box under the finger wins; every box is at least {@code touch_min} wide. */
    private String hitTest(float x, float y) {
        int width = getWidth();
        int height = getHeight();
        String best = null;
        float bestArea = Float.MAX_VALUE;
        float bestDistance = Float.MAX_VALUE;
        for (ForestViewState.Tree tree : trees) {
            float size = tree.placement.size * height;
            float cx = tree.placement.x * width;
            float cy = tree.placement.y * height;
            float half = Math.max(size, touchMin) / 2f;
            if (x < cx - half || x > cx + half || y < cy - half || y > cy + half) {
                continue;
            }
            float area = half * half * 4f;
            float distance = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            if (area < bestArea || (area == bestArea && distance < bestDistance)) {
                best = tree.topicId;
                bestArea = area;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A 14dp tile with one token-green pixel at 5% - the prototype's paper grain of the plot. */
    private Bitmap dotTile() {
        int cell = Math.max(2, Math.round(14f * density));
        Bitmap tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tile);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(withAlpha(green, 0.05f));
        canvas.drawCircle(cell / 2f, cell / 2f, Math.max(0.7f, 0.7f * density), dot);
        return tile;
    }

    private void buildTerrain(int width, int height) {
        contours.clear();
        grasses.clear();
        // Five height lines plus one faint line beyond them, the prototype's .contour set.
        float[] baseY = {0.858f, 0.734f, 0.609f, 0.485f, 0.360f, 0.236f};
        for (int i = 0; i < baseY.length; i++) {
            float y = baseY[i] * height;
            Path contour = new Path();
            contour.moveTo(-0.10f * width, y);
            contour.cubicTo(0.10f * width, y - 0.06f * height,
                    0.26f * width, y - 0.02f * height, 0.40f * width, y - 0.06f * height);
            contour.cubicTo(0.56f * width, y - 0.10f * height,
                    0.68f * width, y - 0.06f * height, 0.85f * width, y - 0.02f * height);
            contour.cubicTo(1.00f * width, y + 0.01f * height,
                    1.08f * width, y - 0.04f * height, 1.20f * width, y - 0.02f * height);
            contours.add(contour);
        }
        Path knoll = new Path();
        knoll.moveTo(0.733f * width, 0.317f * height);
        knoll.cubicTo(0.641f * width, 0.284f * height, 0.641f * width, 0.226f * height,
                0.723f * width, 0.195f * height);
        knoll.cubicTo(0.810f * width, 0.163f * height, 0.928f * width, 0.183f * height,
                0.949f * width, 0.228f * height);
        knoll.cubicTo(0.969f * width, 0.278f * height, 0.846f * width, 0.340f * height,
                0.733f * width, 0.317f * height);
        knoll.close();
        contours.add(knoll);
        Path inner = new Path();
        inner.moveTo(0.759f * width, 0.296f * height);
        inner.cubicTo(0.697f * width, 0.272f * height, 0.703f * width, 0.234f * height,
                0.759f * width, 0.213f * height);
        inner.cubicTo(0.821f * width, 0.190f * height, 0.897f * width, 0.204f * height,
                0.910f * width, 0.235f * height);
        inner.cubicTo(0.923f * width, 0.269f * height, 0.836f * width, 0.310f * height,
                0.759f * width, 0.296f * height);
        inner.close();
        contours.add(inner);

        addGrass(0.144f, 0.317f, width, height);
        addGrass(0.846f, 0.419f, width, height);
        addGrass(0.246f, 0.787f, width, height);
        addGrass(0.585f, 0.596f, width, height);
        addGrass(0.913f, 0.876f, width, height);
    }

    private void addGrass(float x, float y, int width, int height) {
        float px = x * width;
        float py = y * height;
        Path blades = new Path();
        blades.moveTo(px, py);
        blades.quadTo(px + 3f * density, py - 11f * density, px, py - 17f * density);
        blades.moveTo(px + 5f * density, py);
        blades.quadTo(px + 9f * density, py - 9f * density, px + 10f * density,
                py - 16f * density);
        blades.moveTo(px - 5f * density, py);
        blades.quadTo(px - 8f * density, py - 9f * density, px - 12f * density,
                py - 14f * density);
        grasses.add(blades);
    }

    private static int withAlpha(int color, float alpha) {
        int normalized = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return Color.argb(normalized, Color.red(color), Color.green(color), Color.blue(color));
    }
}
