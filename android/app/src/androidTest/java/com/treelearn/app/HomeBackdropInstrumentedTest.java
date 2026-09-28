package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.PaperRuleBackdrop;
import com.treelearn.app.screen.PaperRules;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * T-2026-09-27-010 acceptance: the app shell carries the prototype's paper texture as a pure sibling
 * decoration in phone and tablet contexts, without perturbing the accepted layout.
 *
 * <p>Mirrors {@link CanvasBackdropInstrumentedTest}: every assertion is structural. The texture must
 * sit <em>below</em> the shell content in z-order, must take no touches and no focus, must stay
 * invisible to assistive tech, and must draw only with values that already exist as tokens - because
 * the one thing this slice must not do is change what the shell decides about scroll, hit
 * testing or the drawer button. Those stay watched by {@code AppLaunchInstrumentedTest} and
 * {@code BottomTabShellInstrumentedTest}, unchanged.
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
    public void theRuleParametersComeFromExistingTokens() {
        Context context = context();
        PaperRuleBackdrop backdrop = phone().findViewById(R.id.shell_backdrop);

        assertEquals("the rule period is the space_s8 token",
                (float) context.getResources().getDimensionPixelSize(R.dimen.space_s8),
                backdrop.spacingPx(), 0f);
        assertEquals("the rule thickness is the hairline token",
                (float) context.getResources().getDimensionPixelSize(R.dimen.hairline),
                backdrop.thicknessPx(), 0f);
        assertEquals("the rule colour is the line token composited over the page, not a new resource",
                PaperRules.composite(context.getColor(R.color.tl_bg), context.getColor(R.color.tl_line),
                        PaperRules.ruleAlpha()),
                backdrop.ruleColor());
    }

    @Test
    public void theTabBarAndRouteRootsDoNotHideTheSharedPaperTexture() {
        assertTransparentTabBar("phone tab bar", phone());
        assertTransparentTabBar("tablet tab bar", tablet());
        assertNoPaperBackdrop("phone home layout", R.layout.screen_home, context());
        assertNoPaperBackdrop("chat layout", R.layout.screen_chat_body, context());
        assertNoPaperBackdrop("navigation layout", R.layout.screen_navigation_body, context());
        assertNoPaperBackdrop("tree layout", R.layout.screen_tree_body, context());
    }

    private void assertTransparentTabBar(String which, View root) {
        Drawable background = root.findViewById(R.id.tab_bar).getBackground();
        assertTrue(which + " must let shell paper show through",
                background == null || (background instanceof ColorDrawable
                        && ((ColorDrawable) background).getColor() == 0));
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

        assertNotNull(which + " must carry the paper texture layer", backdrop);
        assertTrue(which + " uses the dedicated rule view", backdrop instanceof PaperRuleBackdrop);
        assertTrue(which + " draws the texture before (below) the scrolling column",
                screen.indexOfChild(backdrop) < screen.indexOfChild(column));
        assertFalse(which + ": a decoration never takes touches", backdrop.isClickable());
        assertFalse(which + ": a decoration never takes focus", backdrop.isFocusable());
        assertEquals(which + ": a decoration is invisible to assistive tech",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO, backdrop.getImportantForAccessibility());
        ViewGroup.LayoutParams params = backdrop.getLayoutParams();
        assertEquals(which + ": the texture fills the screen width",
                ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals(which + ": the texture fills the screen height",
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
