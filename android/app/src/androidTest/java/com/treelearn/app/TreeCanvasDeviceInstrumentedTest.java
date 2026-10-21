package com.treelearn.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.TreeCanvasScreen;
import com.treelearn.app.screen.TreeCanvasView;
import com.treelearn.app.ui.PopupPlacement;
import com.treelearn.app.ui.TreeCanvasActions;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.treelayout.Density;
import com.treelearn.treelayout.LayoutNode;
import com.treelearn.treelayout.LayoutResult;
import com.treelearn.treelayout.TreeLayout;
import com.treelearn.treelayout.TreeSnapshot;
import com.treelearn.treelayout.ViewTransform;
import com.treelearn.treelayout.ViewportConstraints;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * T-017 device evidence for the parts a JVM cannot decide: the tablet's two-column layout is a
 * resource fact, the touch stream is a real MotionEvent stream, and the node popup is a real window
 * that opens and closes over the canvas. The arithmetic behind them (fit, hit-test, placement) is
 * graded in TreeCanvasGeometryTest on the JVM; this suite proves the plumbing carries it.
 */
@RunWith(AndroidJUnit4.class)
public final class TreeCanvasDeviceInstrumentedTest {

    private static final int VIEW_W = 1000;
    private static final int VIEW_H = 2000;

    private final Instrumentation instrumentation =
            InstrumentationRegistry.getInstrumentation();
    private final Context target =
            instrumentation.getTargetContext();

    /** A READY frame over a two-node tree, built only through the production public API. */
    private TreeCanvasViewState readyFrame(boolean withPopup) {
        ViewportConstraints viewport = ViewportConstraints.of(VIEW_W, VIEW_H);
        TreeSnapshot snapshot = TreeSnapshot.of("root", Arrays.asList(
                new LayoutNode("root", null, "ROOT-TITLE", "", 1, 0, false, false),
                new LayoutNode("b1", "root", "CHILD-TITLE", "", 1, 0, false, false)));
        LayoutResult layout = TreeLayout.compute(snapshot, "root", Density.forScale(1f), viewport);
        ViewTransform transform = layout.fit(viewport);
        TreeCanvasViewState state = TreeCanvasViewState
                .initial("topic-1", "root", viewport)
                .withPhase(TreeCanvasViewState.Phase.READY)
                .withFrame(layout, transform)
                .withSnapshot(snapshot);
        return withPopup
                ? state.withPopup(new PopupPlacement("root", VIEW_W / 2f, VIEW_H / 2f,
                        240f, 160f, false, false))
                : state;
    }

    @Test
    public void theTwoColumnLayoutIsATabletResourceOnly() {
        Configuration tabletConfig = new Configuration(target.getResources().getConfiguration());
        tabletConfig.smallestScreenWidthDp = 600;
        Context tablet = target.createConfigurationContext(tabletConfig);

        View tabletTree = LayoutInflater.from(tablet).inflate(R.layout.screen_tree, null);
        assertNotNull("the tablet layout must carry the navigation column",
                tabletTree.findViewById(R.id.tree_nav_host));
        assertNotNull("the tablet layout must carry the two-column divider",
                tabletTree.findViewById(R.id.tree_divider));

        View phoneTree = LayoutInflater.from(target).inflate(R.layout.screen_tree, null);
        assertNull("the phone layout must stay single-column",
                phoneTree.findViewById(R.id.tree_nav_host));
        assertNull(phoneTree.findViewById(R.id.tree_divider));
    }

    @Test
    public void theRealTouchStreamReachesTheCanvasCallbacks() {
        List<String> taps = new ArrayList<>();
        List<float[]> pans = new ArrayList<>();
        List<float[]> zooms = new ArrayList<>();
        TreeCanvasActions recording = new TreeCanvasActions() {

            @Override
            public void onPan(float dx, float dy) {
                pans.add(new float[]{dx, dy});
            }

            @Override
            public void onZoom(float factor, float focusX, float focusY) {
                zooms.add(new float[]{factor, focusX, focusY});
            }

            @Override
            public void onViewportChanged(float width, float height) {
            }

            @Override
            public void onFit() {
            }

            @Override
            public void onTap(float screenX, float screenY) {
                taps.add(screenX + "," + screenY);
            }

            @Override
            public void onDismissPopup() {
            }

            @Override
            public void onCollapseToggled(String branchId) {
            }

            @Override
            public void onOpenCurrent() {
            }

            @Override
            public void onNavigationPressed() {
            }

            @Override
            public void onBackPressed() {
            }
        };

        TreeCanvasView view = new TreeCanvasView(target);
        instrumentation.runOnMainSync(() -> {
            view.setActions(recording);
            view.render(readyFrame(false));
            view.layout(0, 0, VIEW_W, VIEW_H);
        });

        long down = SystemClock.uptimeMillis();
        // A tap: down and up at the same point.
        instrumentation.runOnMainSync(() -> {
            view.dispatchTouchEvent(MotionEvent.obtain(down, down,
                    MotionEvent.ACTION_DOWN, 400f, 800f, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(down, down + 60,
                    MotionEvent.ACTION_UP, 400f, 800f, 0));
        });
        assertTrue("a tap must reach the canvas callback", !taps.isEmpty());

        // A one-finger pan, as its own gesture: a new downTime and a gap past the double-tap
        // window, rather than a second DOWN sharing the tap's downTime in mid-stream.
        SystemClock.sleep(400L);
        long panDown = SystemClock.uptimeMillis();
        instrumentation.runOnMainSync(() -> {
            view.dispatchTouchEvent(MotionEvent.obtain(panDown, panDown,
                    MotionEvent.ACTION_DOWN, 600f, 900f, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(panDown, panDown + 60,
                    MotionEvent.ACTION_MOVE, 540f, 960f, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(panDown, panDown + 120,
                    MotionEvent.ACTION_UP, 540f, 960f, 0));
        });
        assertTrue("a drag must reach onPan", !pans.isEmpty());

        // A two-finger zoom: two pointers spreading apart.
        SystemClock.sleep(400L);
        final long zoomDown = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        // ScaleGestureDetector ignores a pinch whose initial span is below the device's minimum
        // scaling span (27mm here), so the span is derived from the configuration, not guessed.
        final float minSpan = ViewConfiguration.get(target).getScaledMinimumScalingSpan();
        final float spread = Math.min(VIEW_W - 80f, Math.max(300f, minSpan + 120f));
        final float centerX = VIEW_W / 2f;
        final float centerY = VIEW_H / 2f;
        instrumentation.runOnMainSync(() -> {
            coords[0].x = centerX - spread / 2f;
            coords[0].y = centerY;
            coords[1].x = centerX + spread / 2f;
            coords[1].y = centerY;
            view.dispatchTouchEvent(MotionEvent.obtain(zoomDown, zoomDown,
                    MotionEvent.ACTION_DOWN, coords[0].x, coords[0].y, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(zoomDown, zoomDown + 20,
                    MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, props, copy(coords), 0, 0, 0f, 0f, 0, 0, 0, 0));
            coords[0].x = centerX - spread / 2f - 60f;
            coords[1].x = centerX + spread / 2f + 60f;
            view.dispatchTouchEvent(MotionEvent.obtain(zoomDown, zoomDown + 60,
                    MotionEvent.ACTION_MOVE, 2, props, copy(coords), 0, 0, 0f, 0f, 0, 0, 0, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(zoomDown, zoomDown + 80,
                    MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                    2, props, copy(coords), 0, 0, 0f, 0f, 0, 0, 0, 0));
            view.dispatchTouchEvent(MotionEvent.obtain(zoomDown, zoomDown + 100,
                    MotionEvent.ACTION_UP, coords[0].x, coords[0].y, 0));
        });
        assertTrue("a pinch must reach onZoom", !zooms.isEmpty());
    }

    private static MotionEvent.PointerCoords[] copy(MotionEvent.PointerCoords[] source) {
        MotionEvent.PointerCoords[] out = new MotionEvent.PointerCoords[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = new MotionEvent.PointerCoords(source[i]);
        }
        return out;
    }

    @Test
    public void thePopupSeamFollowsTheRenderedState() {
        Activity host = instrumentation.startActivitySync(
                new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            FrameLayout root = host.findViewById(android.R.id.content);
            TreeCanvasScreen screen = new TreeCanvasScreen(host);
            instrumentation.runOnMainSync(() -> {
                root.addView(screen, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                screen.render(readyFrame(true));
            });
            instrumentation.waitForIdleSync();
            assertTrue("a frame with a popup must show the popup", screen.isPopupShowing());

            instrumentation.runOnMainSync(() -> screen.render(readyFrame(false)));
            instrumentation.waitForIdleSync();
            assertFalse("a frame without a popup must take the popup down",
                    screen.isPopupShowing());
        } finally {
            host.finish();
        }
    }
}
