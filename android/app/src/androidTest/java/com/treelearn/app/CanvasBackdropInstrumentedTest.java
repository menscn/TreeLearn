package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.GridBackdrop;
import com.treelearn.app.screen.TreeCanvasScreen;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * T-058 acceptance: the canvas carries the mock's dotted-grid backdrop as a pure sibling decoration,
 * and the control bar's three actions keep their ids, order and reachability while switching their
 * visual carrier from text to the shared line-icon convention (056/057).
 *
 * <p>Every assertion here is structural: the backdrop must sit <em>below</em> the canvas in the stage's
 * z-order, must take no touches and must draw only with values that already exist as tokens - because
 * the one thing this slice must not do is perturb the accepted layout, geometry or hit testing of
 * {@code TreeCanvasView} (the existing tree-layout and canvas tests keep watching those, unchanged).
 */
@RunWith(AndroidJUnit4.class)
public final class CanvasBackdropInstrumentedTest {

    @Test
    public void theBackdropSitsBelowTheCanvasAndIsPureDecoration() {
        TreeCanvasScreen screen = screen();
        ViewGroup stage = screen.findViewById(R.id.tree_stage);
        View backdrop = screen.findViewById(R.id.canvas_backdrop);
        View canvas = screen.findViewById(R.id.tree_canvas);

        assertNotNull("the stage must carry the backdrop layer", backdrop);
        assertTrue("the backdrop is the dedicated grid view", backdrop instanceof GridBackdrop);
        assertTrue("the backdrop must be drawn before (below) the canvas",
                stage.indexOfChild(backdrop) < stage.indexOfChild(canvas));
        assertFalse("a decoration never takes touches", backdrop.isClickable());
        assertFalse("a decoration never takes focus", backdrop.isFocusable());
        ViewGroup.LayoutParams params = backdrop.getLayoutParams();
        assertEquals("the backdrop fills the stage width", ViewGroup.LayoutParams.MATCH_PARENT,
                params.width);
        assertEquals("the backdrop fills the stage height", ViewGroup.LayoutParams.MATCH_PARENT,
                params.height);
    }

    @Test
    public void theGridParametersComeFromExistingTokens() {
        Context context = context();
        GridBackdrop backdrop = screen().findViewById(R.id.canvas_backdrop);

        assertEquals("the dot spacing is the space_s6 token",
                (float) context.getResources().getDimensionPixelSize(R.dimen.space_s6),
                backdrop.spacingPx(), 0f);
        assertEquals("the dot radius is the hairline token",
                (float) context.getResources().getDimensionPixelSize(R.dimen.hairline),
                backdrop.dotRadiusPx(), 0f);
        assertEquals("the dots use the line color, not a new one",
                context.getColor(R.color.tl_line), backdrop.dotColor());
    }

    @Test
    public void theControlBarActionsCarryIconsAndDescriptions() {
        TreeCanvasScreen screen = screen();
        int touchMin = context().getResources().getDimensionPixelSize(R.dimen.touch_min);

        assertIconAction(screen.findViewById(R.id.tree_fold), touchMin, "the fold action");
        assertIconAction(screen.findViewById(R.id.tree_open), touchMin, "the open action");
        assertIconAction(screen.findViewById(R.id.tree_fit), touchMin, "the fit action");
    }

    @Test
    public void thePrototypeCanvasUsesAnOverlayToolRailAndTwoLineTitle() {
        TreeCanvasScreen screen = screen();
        int touchMin = context().getResources().getDimensionPixelSize(R.dimen.touch_min);

        assertEquals("the legacy bottom control row is not visible",
                View.GONE, screen.findViewById(R.id.tree_controls).getVisibility());
        assertIconAction(screen.findViewById(R.id.tree_zoom_in), touchMin, "the zoom-in action");
        assertIconAction(screen.findViewById(R.id.tree_zoom_out), touchMin, "the zoom-out action");
        assertIconAction(screen.findViewById(R.id.tree_fit_tool), touchMin, "the fit rail action");
        assertIconAction(screen.findViewById(R.id.tree_center), touchMin, "the center action");
        assertNotNull("the title must have a prototype subtitle line",
                screen.findViewById(R.id.tree_subtitle));
        assertNotNull("the node card must have a summary slot",
                LayoutInflater.from(context()).inflate(R.layout.view_node_popup, null)
                        .findViewById(R.id.node_popup_summary));
    }

    private void assertIconAction(View action, int touchMin, String why) {
        assertTrue(why + " keeps its id and is an icon button now", action instanceof ImageButton);
        assertTrue(why + " stays reachable at touch_min height",
                action.getMinimumHeight() >= touchMin);
        assertNotNull(why + " must tell assistive tech what it does",
                action.getContentDescription());
        assertTrue(why + "'s description must not be blank",
                action.getContentDescription().length() > 0);
    }

    private TreeCanvasScreen screen() {
        final TreeCanvasScreen[] holder = new TreeCanvasScreen[1];
        instrumentation().runOnMainSync(() -> holder[0] = new TreeCanvasScreen(context()));
        return holder[0];
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static android.app.Instrumentation instrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
}
