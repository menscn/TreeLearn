package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.text.Layout;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.app.screen.ModelThinkingPicker;
import com.treelearn.app.screen.PaperRules;
import com.treelearn.app.screen.RuleEditText;

import java.util.concurrent.Callable;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * T-2026-09-27-004 acceptance: the home composer's visuals are what the prototype draws, checked on a
 * real device because the JVM source set cannot lay a view out.
 *
 * <p>Three things can only be proven here, and each is asserted as a number rather than as "looks
 * right":
 *
 * <ul>
 *   <li><strong>The writing grid and the text share one period.</strong> The prototype's
 *       {@code line-height:32px} is the same 32px its rules repeat at, so the field's laid-out line box
 *       is measured against {@code @dimen/space_s8} - if a later change lets the text drift off the
 *       grid, a rule starts crossing glyphs and this turns red.
 *   <li><strong>The control states still work.</strong> The send control changed type (text
 *       {@code Button} to circular {@code ImageButton}), which is exactly the kind of change that can
 *       silently cost a control its name or its disabled state, so both are asserted through the real
 *       screen.
 *   <li><strong>The small pill and the 44dp hot zone are two different boxes.</strong> The prototype
 *       draws the model chip around 22px tall; the pill keeps that size while the control that takes
 *       the tap keeps {@code touch_min}, and both halves of that split are pinned.
 * </ul>
 *
 * <p>Deliberately written against the platform APIs so the androidTest classpath stays free of Kotlin.
 * The composer body is defined once ({@code layout/screen_home_body.xml}) and included by both the
 * phone and the {@code sw600dp} screen layouts, so one run covers both configurations.
 */
@RunWith(AndroidJUnit4.class)
public final class HomeComposerInstrumentedTest {

    private static final String DRAFT = "为什么天空是蓝色的";

    /** Long enough to wrap onto several rows at the composer's width, so the grid has rows to judge. */
    private static final String WRAPPING = DRAFT + DRAFT + DRAFT + DRAFT + DRAFT + DRAFT;

    @Test
    public void theRuledFieldPutsItsLinesOnTheSamePeriodAsTheGrid() {
        Activity activity = launch();
        try {
            int spacing = activity.getResources().getDimensionPixelSize(R.dimen.space_s8);

            float[] empty = onMain(() -> {
                RuleEditText field = activity.findViewById(R.id.home_draft);
                return new float[] {field.gridSpacingPx(), field.getHeight()};
            });
            assertEquals("the field's grid is the spacing token", (float) spacing, empty[0], 0f);
            assertEquals("and an empty field is the prototype's two rows of it",
                    2f * spacing, empty[1], 0f);

            // The period only means something once there are rows; an empty field lays out one
            // degenerate line whose box ignores the spacing (measured: 66px against a 104px grid).
            // The field derives its multiplier from the box the platform built, so it settles one
            // frame after the text arrives - the test waits for it the way an eye would.
            RuleEditText field = activity.findViewById(R.id.home_draft);
            onMain(() -> {
                field.setText(WRAPPING);
                return null;
            });
            int[] period = new int[1];
            await(() -> {
                Layout layout = field.getLayout();
                period[0] = layout == null || layout.getLineCount() < 2
                        ? -1 : layout.getLineTop(1) - layout.getLineTop(0);
                return period[0] == spacing;
            });
            assertEquals("consecutive rows sit exactly one grid apart", spacing, period[0]);

            int[] rows = onMain(() -> {
                Layout layout = field.getLayout();
                return new int[] {
                        layout.getLineCount(),
                        layout.getLineBottom(0) - layout.getLineTop(0),
                        layout.getLineBaseline(0) + layout.getLineAscent(0),
                        layout.getLineBaseline(0) + layout.getLineDescent(0),
                };
            });
            assertTrue("the draft must wrap onto several rows: " + rows[0], rows[0] >= 2);
            assertEquals("so each row's rule lands on a grid line", spacing, rows[1]);
            assertTrue("and the glyphs stay inside their own row rather than being pushed out of its "
                            + "top: " + rows[2],
                    rows[2] >= 0);
            assertTrue("and inside its bottom edge: " + rows[3] + " of " + rows[1],
                    rows[3] <= rows[1]);

            onMain(() -> {
                field.setText("");
                return null;
            });
        } finally {
            activity.finish();
        }
    }

    @Test
    public void aSingleRowDraftStaysInsideItsOwnGridRow() {
        Activity activity = launch();
        try {
            int spacing = activity.getResources().getDimensionPixelSize(R.dimen.space_s8);
            RuleEditText field = activity.findViewById(R.id.home_draft);

            onMain(() -> {
                field.setText(DRAFT);
                return null;
            });
            await(() -> {
                Layout layout = field.getLayout();
                return layout != null && layout.getLineCount() == 1;
            });

            int[] row = onMain(() -> {
                Layout layout = field.getLayout();
                // The layout's own coordinates start at the content top, which vertical centring
                // pushes down inside the min-height box; the baseline's position in the VIEW is
                // what the painted rules are measured against, so the check happens there.
                int baseline = field.getBaseline();
                return new int[] {
                        layout.getLineCount(),
                        baseline + layout.getLineAscent(0),
                        baseline + layout.getLineDescent(0),
                        field.getGravity() & Gravity.VERTICAL_GRAVITY_MASK,
                };
            });

            assertEquals("this draft must fit on one row for the assertion to mean anything", 1, row[0]);
            assertTrue("the glyph's top stays inside the first grid row, measured " + row[1], row[1] >= 0);
            assertTrue("the glyph's bottom - descender included - stays above the first rule at "
                            + spacing + ", measured " + row[2] + " (vertical gravity mask "
                            + row[3] + ", 0=top 16=centre_vertical 80=bottom)",
                    row[2] <= spacing);
        } finally {
            activity.finish();
        }
    }

    @Test
    public void theRuledFieldPaintsTheSameJudgementThePageTextureUses() {
        Activity activity = launch();
        try {
            int painted = onMain(() -> ((RuleEditText) activity.findViewById(R.id.home_draft))
                    .ruleColour());

            assertEquals("the field's rules are the writing rule, read from PaperRules rather than "
                            + "re-derived at the view",
                    PaperRules.writingRuleColour(activity.getColor(R.color.tl_surface),
                            activity.getColor(R.color.tl_green)),
                    painted);
        } finally {
            activity.finish();
        }
    }

    @Test
    public void theSendControlStartsDisabledAndWakesUpWithADraft() {
        Activity activity = launch();
        try {
            ImageButton send = activity.findViewById(R.id.home_send);

            assertFalse("an empty composer cannot send", onMain(send::isEnabled));
            assertEquals("and being unable to send is not left to colour: the control is described",
                    activity.getString(R.string.home_send),
                    String.valueOf(onMain(send::getContentDescription)));

            onMain(() -> {
                ((EditText) activity.findViewById(R.id.home_draft)).setText(DRAFT);
                return null;
            });
            assertTrue("a draft wakes the control up", await(send::isEnabled));

            onMain(() -> {
                ((EditText) activity.findViewById(R.id.home_draft)).setText("");
                return null;
            });
            assertTrue("clearing it puts the control back to sleep", await(() -> !send.isEnabled()));
        } finally {
            activity.finish();
        }
    }

    @Test
    public void theModelPillStaysSmallWhileTheControlKeepsTheTouchTarget() {
        Activity activity = launch();
        try {
            ModelThinkingPicker picker = activity.findViewById(R.id.home_model_chip);
            int touchMin = activity.getResources().getDimensionPixelSize(R.dimen.touch_min);
            int[] heights = onMain(() -> new int[] {
                    picker.getHeight(), ((ViewGroup) picker.getChildAt(0)).getHeight() });

            assertTrue("the chip that takes the tap is a full touch target, measured " + heights[0],
                    heights[0] >= touchMin);
            assertTrue("while the pill stays the size the prototype draws it, measured " + heights[1],
                    heights[1] < touchMin);
            assertTrue("the host is what takes the click", onMain(picker::isClickable));
            assertNotNull("and it carries the chip's name, because the label inside is hidden from "
                    + "assistive tech to keep it one node", onMain(picker::getContentDescription));
        } finally {
            activity.finish();
        }
    }

    @Test
    public void theKeyPillIsPaddedSymmetricallyAndCarriesItsGlyph() {
        Activity activity = launch();
        try {
            LinearLayout pill = activity.findViewById(R.id.home_key_hint);
            ImageView icon = activity.findViewById(R.id.home_key_icon);
            TextView label = activity.findViewById(R.id.home_key_text);

            assertEquals("the pill pads top and bottom with one token, so the label sits centred",
                    onMain(pill::getPaddingTop), onMain(pill::getPaddingBottom));
            assertNotNull("and it carries the key glyph the prototype draws", onMain(icon::getDrawable));
            assertTrue("with the label that says which state the key is in",
                    onMain(() -> label.getText().length()) > 0);
        } finally {
            activity.finish();
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Activity launch() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(intent);
        assertNotNull("MainActivity failed to launch", activity);
        return activity;
    }

    /**
     * Waits for something the screen settles a frame or two later: neither the presenter's render nor
     * the field's grid-onto-line-box correction is synchronous with the change that caused it.
     */
    private static boolean await(Callable<Boolean> settled) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (onMain(settled)) {
                return true;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return onMain(settled);
    }

    /** Views may only be touched on the main thread, so every read goes through here. */
    private static <T> T onMain(Callable<T> probe) {
        Object[] holder = new Object[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                holder[0] = probe.call();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }
}
