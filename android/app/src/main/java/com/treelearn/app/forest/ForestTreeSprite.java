package com.treelearn.app.forest;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * One slanted three-quarter tree (T-068), drawn exactly the way the persona/forest prototype's
 * {@code treeSprite} draws it: the trunk meets the ground, the canopy sits above it, and the shadow
 * falls to the lower right. The sprite is a pure function of size, seed and tone, so a tree looks
 * the same on every frame and every visit.
 *
 * <p>The pseudo-random stream is mulberry32, seeded by {@link ForestLayout}'s stable hash. No
 * {@code java.util.Random} appears anywhere: a platform RNG would let a redraw change a canopy.
 */
final class ForestTreeSprite {

    /** The three token colors the prototype's canopy uses. */
    static final class Palette {

        final int green;
        final int greenSoft;
        final int greenDeep;

        Palette(int green, int greenSoft, int greenDeep) {
            this.green = green;
            this.greenSoft = greenSoft;
            this.greenDeep = greenDeep;
        }
    }

    private ForestTreeSprite() {
    }

    /**
     * Draws one tree whose sprite box is {@code (left, top, left + size, top + size)}.
     *
     * @param seed the stable per-topic seed; 0 is treated as 1
     * @param tone the prototype's canopy shade, 0.80..1.15
     */
    static void draw(Canvas canvas, float left, float top, float size, int seed, float tone,
            Palette palette, Paint paint, Path path) {
        float shade = tone <= 0f ? 1f : tone;
        Rand random = new Rand(seed == 0 ? 1 : seed);
        float cx = left + size * 0.50f;
        float cy = top + size * 0.37f;
        float baseY = top + size * 0.85f;
        float crownTopY = top + size * 0.47f;
        float lean = (random.next() * 2f - 1f) * size * 0.05f;
        float crownX = cx + lean;
        float trunkWidth = Math.max(1.7f, size * 0.027f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(palette.greenDeep, 0.10f));
        oval(canvas, paint, left + size * 0.60f, top + size * 0.87f, size * 0.32f,
                size * 0.085f);
        paint.setColor(withAlpha(palette.greenDeep, 0.07f));
        oval(canvas, paint, left + size * 0.54f, top + size * 0.845f, size * 0.17f,
                size * 0.05f);

        path.reset();
        path.moveTo(cx - trunkWidth, baseY);
        path.cubicTo(cx - trunkWidth * 0.8f, top + size * 0.74f,
                crownX - trunkWidth * 0.45f, top + size * 0.60f,
                crownX - trunkWidth * 0.32f, crownTopY);
        path.lineTo(crownX + trunkWidth * 0.32f, crownTopY);
        path.cubicTo(crownX + trunkWidth * 0.45f, top + size * 0.60f,
                cx + trunkWidth * 0.8f, top + size * 0.74f,
                cx + trunkWidth, baseY);
        path.close();
        paint.setColor(withAlpha(palette.greenDeep, 0.50f));
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(trunkWidth * 0.65f);
        paint.setColor(withAlpha(palette.greenDeep, 0.42f));
        path.reset();
        path.moveTo(crownX - trunkWidth * 0.15f, top + size * 0.68f);
        path.quadTo(crownX - size * 0.09f, top + size * 0.62f,
                crownX - size * 0.16f, top + size * 0.57f);
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(trunkWidth * 0.6f);
        paint.setColor(withAlpha(palette.greenDeep, 0.36f));
        path.reset();
        path.moveTo(crownX + trunkWidth * 0.05f, top + size * 0.66f);
        path.quadTo(crownX + size * 0.08f, top + size * 0.61f,
                crownX + size * 0.15f, top + size * 0.56f);
        canvas.drawPath(path, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(palette.greenDeep, Math.min(0.24f, 0.12f * shade)));
        oval(canvas, paint, crownX + size * 0.01f, cy + size * 0.075f, size * 0.29f,
                size * 0.155f);

        int lobes = 8 + (int) Math.floor(random.next() * 4f);
        for (int i = 0; i < lobes; i++) {
            float angle = (i / (float) lobes) * (float) Math.PI * 2f
                    + (random.next() - 0.5f) * 0.34f;
            boolean outer = i < lobes - 3;
            float rx = size * (outer ? 0.13f + random.next() * 0.045f
                    : 0.10f + random.next() * 0.035f);
            float ry = size * (outer ? 0.095f + random.next() * 0.035f
                    : 0.075f + random.next() * 0.03f);
            float x = crownX + (float) Math.cos(angle) * size
                    * (outer ? 0.17f + random.next() * 0.035f : 0.06f + random.next() * 0.05f);
            float y = cy + (float) Math.sin(angle) * size
                    * (outer ? 0.10f + random.next() * 0.03f : 0.04f + random.next() * 0.04f);
            float opacity = Math.min(0.9f, (0.54f + random.next() * 0.22f) * shade);
            paint.setColor(withAlpha(palette.green, opacity));
            oval(canvas, paint, x, y, rx, ry);
        }

        paint.setColor(withAlpha(palette.green, 0.30f));
        for (int i = 0; i < 9; i++) {
            float angle = random.next() * (float) Math.PI * 2f;
            float x = crownX + (float) Math.cos(angle) * size * (0.27f + random.next() * 0.06f);
            float y = cy + (float) Math.sin(angle) * size * (0.16f + random.next() * 0.04f);
            float radius = size * 0.018f;
            canvas.drawCircle(x, y, radius, paint);
        }

        paint.setColor(withAlpha(palette.greenSoft, 0.30f));
        oval(canvas, paint, crownX - size * 0.10f, cy - size * 0.09f, size * 0.13f,
                size * 0.08f);
        paint.setColor(withAlpha(palette.greenSoft, 0.22f));
        oval(canvas, paint, crownX - size * 0.17f, cy + size * 0.01f, size * 0.075f,
                size * 0.05f);
        paint.setColor(withAlpha(palette.greenSoft, 0.20f));
        oval(canvas, paint, crownX - size * 0.01f, cy - size * 0.15f, size * 0.075f,
                size * 0.045f);
    }

    private static void oval(Canvas canvas, Paint paint, float cx, float cy, float rx,
            float ry) {
        canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint);
    }

    private static int withAlpha(int color, float alpha) {
        int normalized = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return Color.argb(normalized, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** A faithful Java port of the prototype's mulberry32; the stream depends on the seed only. */
    private static final class Rand {

        private int state;

        Rand(int seed) {
            this.state = seed;
        }

        float next() {
            state = state + 0x6D2B79F5;
            int t = (state ^ (state >>> 15)) * (1 | state);
            t = (t + (t ^ (t >>> 7)) * (61 | t)) ^ t;
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296f;
        }
    }
}
