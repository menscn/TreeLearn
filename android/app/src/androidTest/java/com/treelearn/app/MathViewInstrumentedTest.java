package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

import androidx.test.platform.app.InstrumentationRegistry;

import com.treelearn.app.ui.math.MathNode;
import com.treelearn.app.ui.math.MathParser;
import com.treelearn.app.ui.math.MathSpan;
import com.treelearn.app.ui.math.MathView;

import org.junit.Before;
import org.junit.Test;

/**
 * Device proof for {@link MathView}: a display formula is measured at its own width, so a wide one
 * scrolls inside the answer instead of losing both ends, and a formula that fits is still centred.
 *
 * <p>The scroll view is the point of these tests. The view's own arithmetic could be checked without
 * a device; whether {@code HorizontalScrollView} actually lets its child stay wider than the viewport
 * - and what {@code fillViewport} does to a child that fits - is platform behaviour, and the whole
 * fix rests on it.
 *
 * <p>Every viewport here is derived from the formula's own measured width rather than written down,
 * because the width is real text metrics: a fixture written as "wide" once measured 399px against a
 * hand-picked 800px viewport, and the two tests that were supposed to catch clipping measured
 * nothing at all.
 */
public class MathViewInstrumentedTest {

    private static final float HOST_TEXT_SIZE = 32f;
    private static final String WIDE = "\\frac{Q K^{\\top}}{\\sqrt{d_k}} + \\frac{Q K^{\\top}}"
            + "{\\sqrt{d_k}} + \\frac{Q K^{\\top}}{\\sqrt{d_k}} + \\frac{Q K^{\\top}}{\\sqrt{d_k}}";
    private static final String NARROW = "\\frac{1}{2}";

    private Context context;

    @Before
    public void createContext() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void aWiderFormulaIsNotClampedToTheConstraintItWasGiven() {
        MathView view = view(WIDE);
        int natural = naturalWidth(view);

        view.measure(atMost(natural / 2), atMost(natural * 2));

        assertEquals("an at-most constraint must not shrink a formula that has to scroll",
                natural, view.getMeasuredWidth());
    }

    @Test
    public void anExactlyConstraintIsObeyedSoAFittingFormulaCanBeStretched() {
        MathView view = view(NARROW);
        int stretched = naturalWidth(view) * 3;

        view.measure(exactly(stretched), atMost(stretched * 2));

        assertEquals(stretched, view.getMeasuredWidth());
    }

    @Test
    public void theScrollViewLetsAWideFormulaRunPastTheViewportAndScroll() {
        MathView view = view(WIDE);
        HorizontalScrollView scroll = scroll(view, naturalWidth(view) / 2);

        assertTrue("the child must keep its own width inside the scroll view: child="
                        + view.getWidth() + " viewport=" + scroll.getWidth(),
                view.getWidth() > scroll.getWidth());
        assertTrue("a formula wider than the viewport must be scrollable to its end",
                scroll.canScrollHorizontally(1));
    }

    @Test
    public void aFormulaThatFitsIsStretchedToTheMeasureAndDoesNotScroll() {
        MathView view = view(NARROW);
        HorizontalScrollView scroll = scroll(view, naturalWidth(view) * 3);

        assertEquals("fillViewport must stretch a formula that fits, so the view can centre it",
                scroll.getWidth(), view.getWidth());
        assertFalse(scroll.canScrollHorizontally(1));
        assertFalse(scroll.canScrollHorizontally(-1));
    }

    @Test
    public void inkStartsCentredWhileTheFormulaFitsAndFlushLeftOnceItDoesNot() {
        assertEquals(300f, MathView.centeredOffset(800f, 200f), 0f);
        assertEquals("a centred start would hide the left end of a formula nobody can scroll back to",
                0f, MathView.centeredOffset(800f, 1600f), 0f);
    }

    @Test
    public void theViewAndTheInlineSpanGiveTheSameWidthToOneFormula() {
        MathNode node = MathParser.parse(WIDE);
        assertNotNull("fixture must parse", node);
        Paint paint = new Paint();
        paint.setTextSize(HOST_TEXT_SIZE);
        MathSpan span = MathSpan.create(node, true);
        MathView view = view(WIDE);

        view.measure(unspecified(), unspecified());

        assertEquals("the two hosts must agree, or a formula changes width by how it was drawn",
                span.getSize(paint, "X", 0, 1, null), view.getMeasuredWidth());
    }

    @Test
    public void theViewFollowsTheHostTextSizeRatherThanASizeOfItsOwn() {
        MathView small = view(WIDE);
        MathView large = view(WIDE);
        large.setFormula(MathParser.parse(WIDE), HOST_TEXT_SIZE * 2f, Color.BLACK);

        small.measure(unspecified(), unspecified());
        large.measure(unspecified(), unspecified());

        assertTrue("a zero-width pair would double into zero and pass",
                small.getMeasuredWidth() > 0);
        assertEquals("doubling the prose size must double the formula",
                small.getMeasuredWidth() * 2f, large.getMeasuredWidth(), 2f);
    }

    @Test
    public void theNumeratorAndTheDenominatorBothSurviveTheViewsOwnClip() {
        MathView view = view(NARROW);
        scroll(view, naturalWidth(view) * 3);
        int height = view.getHeight();
        int[] ink = inkPerRow(view);

        assertTrue("a blank bitmap says nothing about where the formula went",
                hasInk(ink, 0, height));
        assertTrue("the numerator must be drawn in the top third; ink pushed below the box's own "
                        + "ascent is the signature of a host that adds the ascent twice",
                hasInk(ink, 0, height / 3));
        assertTrue("the denominator must be drawn in the bottom third",
                hasInk(ink, height - height / 3, height));
    }

    /**
     * The chosen remedy for an over-wide formula is a horizontal drag, so the container has to answer
     * one. {@code canScrollHorizontally} only says the range exists; this feeds the scroll view the
     * events a finger produces and requires the offset to move.
     */
    @Test
    public void aHorizontalDragScrollsTheEndOfAnOverWideFormulaIntoView() {
        MathView view = view(WIDE);
        // Three quarters of the formula: enough overflow to fetch, and a viewport wide enough that
        // one drag across it travels further than the remaining quarter, so the end is reachable.
        HorizontalScrollView scroll = scroll(view, naturalWidth(view) * 3 / 4);
        int range = view.getWidth() - scroll.getWidth();
        assertTrue("the fixture must have an over-wide end to fetch, range=" + range, range > 0);

        drag(scroll, scroll.getWidth() - 10f, 10f);

        assertEquals("a drag past the range must land exactly at the end of the formula",
                range, scroll.getScrollX());
    }

    private MathView view(String latex) {
        MathView view = new MathView(context);
        MathNode node = MathParser.parse(latex);
        assertNotNull("fixture must parse: " + latex, node);
        view.setFormula(node, HOST_TEXT_SIZE, Color.BLACK);
        return view;
    }

    /**
     * The width the formula asks for when nothing constrains it.
     *
     * <p>The floor is load-bearing: every viewport in this file is derived from this number, so a
     * bug that measures the formula at zero would turn each "wider than the viewport" test into a
     * tautology about 0 rather than a test of clipping.
     */
    private static int naturalWidth(MathView view) {
        view.measure(unspecified(), unspecified());
        assertTrue("the fixture must have a real width to lose, got " + view.getMeasuredWidth(),
                view.getMeasuredWidth() > 0);
        return view.getMeasuredWidth();
    }

    /** A formula inside the answer's own container, laid out at a viewport of {@code viewport}. */
    private static HorizontalScrollView scroll(MathView view, int viewport) {
        HorizontalScrollView scroll = new HorizontalScrollView(view.getContext());
        scroll.setFillViewport(true);
        scroll.addView(view, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // An exactly constraint, because the answer's body gives this one match_parent.
        scroll.measure(exactly(viewport), atMost(viewport * 2));
        scroll.layout(0, 0, scroll.getMeasuredWidth(), scroll.getMeasuredHeight());
        return scroll;
    }

    /**
     * How many opaque pixels each row of the laid-out view carries.
     *
     * <p>Width and height alone cannot catch a formula drawn in the wrong place: the box can be the
     * right size and still be painted below itself, because {@code View} clips its canvas to its own
     * bounds and the lost half simply disappears. This is the only measurement here that looks at ink.
     */
    private static int[] inkPerRow(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        assertTrue("the view must be laid out before it can be painted, got " + width + "x" + height,
                width > 0 && height > 0);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        int[] rows = new int[height];
        int[] line = new int[width];
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(line, 0, width, 0, y, width, 1);
            int count = 0;
            for (int pixel : line) {
                if (Color.alpha(pixel) != 0) {
                    count++;
                }
            }
            rows[y] = count;
        }
        bitmap.recycle();
        return rows;
    }

    private static boolean hasInk(int[] rows, int from, int to) {
        for (int y = from; y < to; y++) {
            if (rows[y] > 0) {
                return true;
            }
        }
        return false;
    }

    /** A horizontal drag delivered to {@code view} the way a finger delivers one to its window. */
    private static void drag(View view, float fromX, float toX) {
        long down = SystemClock.uptimeMillis();
        float y = view.getHeight() / 2f;
        view.dispatchTouchEvent(
                MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, fromX, y, 0));
        for (int step = 1; step <= 10; step++) {
            float x = fromX + (toX - fromX) * step / 10f;
            view.dispatchTouchEvent(MotionEvent.obtain(down, down + step * 20L,
                    MotionEvent.ACTION_MOVE, x, y, 0));
        }
        view.dispatchTouchEvent(MotionEvent.obtain(down, down + 240L,
                MotionEvent.ACTION_UP, toX, y, 0));
    }

    private static int unspecified() {
        return View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
    }

    private static int atMost(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST);
    }

    private static int exactly(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
