package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.PaperRuleBackdrop;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * T-2026-09-27-010 acceptance (narrowed 2026-10-23): the app shell carries the prototype's mist scene
 * as a pure sibling decoration in phone and tablet contexts, without perturbing the accepted layout.
 *
 * <p>Mirrors {@link CanvasBackdropInstrumentedTest}: every assertion is structural. The scene must sit
 * <em>below</em> the shell content in z-order, must take no touches and no focus, must stay invisible to
 * assistive tech, and must draw only with values that already exist as tokens - because the one thing
 * this slice must not do is change what the shell decides about scroll, hit testing or the drawer
 * button. Those stay watched by {@code AppLaunchInstrumentedTest} and
 * {@code BottomTabShellInstrumentedTest}, unchanged.
 *
 * <p>The full-page hairline rules this decoration used to paint on top of the scene were removed on
 * 2026-10-23 at the user's request; the assertions that pinned their period, thickness and derived
 * colour went with them, and the view's name is the only thing left of that texture.
 *
 * <p>The tablet configuration is resolved here by overriding the requested screen width rather than by
 * needing a tablet: {@code layout-sw600dp} is selected from the context's configuration, so one run on
 * one device can check both shell contexts.
 */
@RunWith(AndroidJUnit4.class)
public final class HomeBackdropInstrumentedTest {

    /** A width that satisfies the {@code sw600dp} qualifier, so the tablet folder resolves. */
    private static final int TABLET_WIDTH_DP = 840;

    @Test
    public void theBackdropSitsBelowTheColumnAndIsPureDecorationInBothConfigurations() {
        assertBackdropIsBelowTheColumn("phone shell", phone());
        assertBackdropIsBelowTheColumn("tablet shell", tablet());
    }

    @Test
    public void theSceneBaseComesFromAnExistingToken() {
        Context context = context();
        PaperRuleBackdrop backdrop = phone().findViewById(R.id.shell_backdrop);

        assertEquals("the scene base is the mist token",
                context.getColor(R.color.tl_mist_base), backdrop.mistColor());
    }

    @Test
    public void theTabBarAndRouteRootsDoNotHideTheSharedMistScene() {
        assertTranslucentTabBar("phone tab bar", phone());
        assertTranslucentTabBar("tablet tab bar", tablet());
        assertNoPaperBackdrop("phone home layout", R.layout.screen_home, context());
        assertNoPaperBackdrop("chat layout", R.layout.screen_chat_body, context());
        assertNoPaperBackdrop("navigation layout", R.layout.screen_navigation_body, context());
        assertNoPaperBackdrop("tree layout", R.layout.screen_tree_body, context());
    }

    /**
     * The bar used to be fully transparent. Since the 2026-10-21 glass material it draws the
     * translucent chrome itself, so the invariant is "not opaque", not "null": the mist scene and
     * the rule texture must still read through it.
     */
    private void assertTranslucentTabBar(String which, View root) {
        Drawable background = root.findViewById(R.id.tab_bar_pill).getBackground();
        assertNotNull(which + " must carry the shared glass chrome", background);
        assertTrue(which + " must let the mist scene show through",
                background.getOpacity() != PixelFormat.OPAQUE);
    }

    private void assertNoPaperBackdrop(String which, int layout, Context context) {
        View root = LayoutInflater.from(context).inflate(layout, null, false);
        assertFalse(which + " must not own a second paper backdrop",
                containsPaperBackdrop(root));
        Drawable background = root.getBackground();
        assertTrue(which + " must not paint an opaque page background",
                background == null || (background instanceof ColorDrawable
                        && ((ColorDrawable) background).getColor() != context.getColor(R.color.tl_bg)));
    }

    private boolean containsPaperBackdrop(View view) {
        if (view instanceof PaperRuleBackdrop) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); ++i) {
            if (containsPaperBackdrop(group.getChildAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void assertBackdropIsBelowTheColumn(String which, View root) {
        ViewGroup screen = (ViewGroup) root;
        View backdrop = root.findViewById(R.id.shell_backdrop);
        View column = root.findViewById(R.id.main_root);

        assertNotNull(which + " must carry the shared scene layer", backdrop);
        assertTrue(which + " uses the dedicated scene view", backdrop instanceof PaperRuleBackdrop);
        assertTrue(which + " draws the scene before (below) the scrolling column",
                screen.indexOfChild(backdrop) < screen.indexOfChild(column));
        assertFalse(which + ": a decoration never takes touches", backdrop.isClickable());
        assertFalse(which + ": a decoration never takes focus", backdrop.isFocusable());
        assertEquals(which + ": a decoration is invisible to assistive tech",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO, backdrop.getImportantForAccessibility());
        ViewGroup.LayoutParams params = backdrop.getLayoutParams();
        assertEquals(which + ": the scene fills the screen width",
                ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals(which + ": the scene fills the screen height",
                ViewGroup.LayoutParams.MATCH_PARENT, params.height);
    }

    /** The phone configuration: the base layout folder, on this device. */
    private static View phone() {
        return inflate(context());
    }

    /** The tablet configuration: the same inflation with a width that selects {@code layout-sw600dp}. */
    private static View tablet() {
        Configuration configuration = new Configuration(context().getResources().getConfiguration());
        configuration.screenWidthDp = TABLET_WIDTH_DP;
        configuration.smallestScreenWidthDp = TABLET_WIDTH_DP;
        return inflate(context().createConfigurationContext(configuration));
    }

    private static View inflate(Context context) {
        final View[] holder = new View[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> holder[0] = LayoutInflater.from(context).inflate(R.layout.activity_main, null));
        return holder[0];
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
