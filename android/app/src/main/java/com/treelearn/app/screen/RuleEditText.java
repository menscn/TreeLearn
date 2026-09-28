package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.EditText;

import com.treelearn.app.R;

/**
 * The composer's writing field: an ordinary {@code EditText} that paints the prototype's writing rule
 * under every line it shows (T-2026-09-27-004).
 *
 * <p><strong>Why a subclass and not a decorative view behind the field.</strong> The prototype's
 * texture is {@code background-attachment: local}
 * (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html, {@code .home-input textarea} at line 156), so its
 * rules scroll with the text instead of staying pinned to the box. A view painted underneath cannot do
 * that: it would either stand still while the text moved, or have to be pushed around from outside by
 * whoever owns both views. Drawing in {@code onDraw} keeps the rules glued to the lines they belong to
 * and keeps them a single object that cannot drift out of sync with the text.
 *
 * <p><strong>The line box is stretched onto the grid, and the stretch is calibrated, not computed.</strong>
 * The prototype's {@code line-height:32px} is the same 32px its rules repeat at, so this field stretches
 * its own line box to {@code @dimen/space_s8} - one row, one rule - rather than ruling a 32px grid under
 * 20dp lines, which would eventually draw a rule through a glyph.
 *
 * <p>Getting that stretch right needs two measurements that were taken on a device rather than assumed:
 * <ul>
 *   <li><strong>A multiplier, not an added gap.</strong> {@code lineSpacingExtra} grows the box and
 *       leaves the glyphs at the top of it, which pushes the first line's text out of its own box; a
 *       multiplier scales the ascent and descent together, so the extra leading is spread around the
 *       glyphs the way a CSS {@code line-height} does.</li>
 *   <li><strong>The resulting period has to be measured, not derived.</strong> The platform turns the
 *       multiplier into a line box with an internal rounding step, so the period is a staircase in the
 *       multiplier (measured sweep on a 3.25x device: 1.5 to 97px, 1.6 to 103px, 1.7 to 110px), and one
 *       step of that staircase is about 1.6% of the multiplier - narrower than the error of any estimate
 *       taken from {@code Paint}'s metrics, which report a box 1px taller than the layout builds. An
 *       estimate therefore lands on the step below or above and stays there: a first attempt at this
 *       derived the multiplier from the laid-out box each pass and oscillated between 102 and 105.
 *       So the multiplier is <em>searched</em>: a wrong guess is corrected in proportion to how wrong the
 *       measured period is, and the search stops as soon as the period is the token - or after
 *       {@link #CALIBRATION_STEPS} corrections, whichever comes first, so it can never spin.</li>
 * </ul>
 * The correction only runs while the text is being laid out, only while there are rows to measure, and
 * only until it has settled; the multiplier is then fixed for as long as the field keeps its paint
 * (a font-scale change rebuilds the view).
 *
 * <p><strong>What it must not do.</strong> It adds no judgement of its own. Offsets and thickness come
 * from {@link PaperRules#lineOffsets} ({@code @dimen/hairline}), and the colour from
 * {@link PaperRules#writingRuleColour(int, int)} - the identity {@code green} over {@code surface},
 * the counterpart of the page texture {@link PaperRuleBackdrop} paints, decided in that one class so the
 * two textures cannot drift apart.
 */
public final class RuleEditText extends EditText {

    /** The prototype's {@code min-height:64px}, expressed as rows of its own grid. */
    private static final int MIN_ROWS = 2;

    /**
     * How many proportional corrections the line-box search may make before it settles for the closest
     * period it found. Three are enough for the measured staircase; the fourth is headroom.
     */
    private static final int CALIBRATION_STEPS = 4;

    private final Paint rules = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** {@code @dimen/space_s8}: the period the prototype's writing rule repeats at. */
    private final int gridSpacing;
    /** {@code @dimen/hairline}: the prototype paints the last pixel band of each row, nothing wider. */
    private final float thickness;

    /** The multiplier on trial, or -1 before the first guess. */
    private float multiplier = -1f;
    /** The multiplier the current layout was built with, so it is only re-applied when it changes. */
    private float appliedMultiplier = -1f;
    /** How many proportional corrections have been made; once full, the search is over. */
    private int corrections;

    public RuleEditText(Context context) {
        this(context, null);
    }

    public RuleEditText(Context context, AttributeSet attributes) {
        super(context, attributes);
        rules.setStyle(Paint.Style.FILL);
        rules.setColor(PaperRules.writingRuleColour(context.getColor(R.color.tl_surface),
                context.getColor(R.color.tl_green)));
        gridSpacing = getResources().getDimensionPixelSize(R.dimen.space_s8);
        thickness = getResources().getDimensionPixelSize(R.dimen.hairline);
        setMinHeight(MIN_ROWS * gridSpacing);
        pinToTheTopOfItsBox();
        syncLineGrid();
    }

    /**
     * Shares one anchor between the text and the rules. The theme hands this field a vertically
     * centred gravity, so content shorter than the two-row minimum is pushed down the box while the
     * rules stay anchored at the top of it: measured on a 3.25x device, a single-line draft's glyph
     * bottom then lands at 137px inside a 104px grid, which is a rule through the glyphs (the
     * wrapping case escapes it only because its layout happens to fill the box exactly). The
     * prototype's textarea starts its content at the top of its box - its rules are drawn from there
     * too - so the vertical half of the gravity is pinned to the top and the horizontal half is left
     * exactly as it was resolved.
     */
    private void pinToTheTopOfItsBox() {
        setGravity((getGravity() & ~Gravity.VERTICAL_GRAVITY_MASK) | Gravity.TOP);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        syncLineGrid();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Keeps the laid-out line box on the grid: measures the period the current multiplier produced,
     * corrects the multiplier in proportion, and applies it. Settles as soon as the period is the token.
     */
    private void syncLineGrid() {
        if (multiplier < 0f) {
            multiplier = (float) gridSpacing / paintBox();
        } else if (corrections < CALIBRATION_STEPS) {
            int period = laidOutPeriod();
            if (period == gridSpacing) {
                corrections = CALIBRATION_STEPS;
            } else if (period > 0) {
                corrections++;
                multiplier *= (float) gridSpacing / period;
            }
        }
        if (multiplier != appliedMultiplier) {
            appliedMultiplier = multiplier;
            setLineSpacing(0f, multiplier);
        }
    }

    /** The line box the paint alone asks for - the seed for the first guess, nothing more. */
    private float paintBox() {
        Paint.FontMetricsInt metrics = getPaint().getFontMetricsInt();
        return metrics.bottom - metrics.top;
    }

    /**
     * The period between two laid-out rows, or -1 while there is nothing to measure: an empty field lays
     * out a single degenerate line whose box ignores the spacing altogether.
     */
    private int laidOutPeriod() {
        Layout layout = getLayout();
        if (layout == null || layout.getLineCount() < 2 || length() == 0) {
            return -1;
        }
        return layout.getLineTop(1) - layout.getLineTop(0);
    }

    /**
     * The period actually drawn on - the laid-out line box, which {@link #syncLineGrid()} has already
     * made equal to the token. Read back rather than assumed, so a font scale the search never settled on
     * moves the grid with the text instead of letting a rule cut through a glyph.
     */
    private float linePeriod() {
        int period = laidOutPeriod();
        return period > 0 ? period : gridSpacing;
    }

    /**
     * The height the rules have to cover: the field's own box, or the whole laid-out text when the text
     * is taller than the box and has scrolled inside it. The rules are drawn in the field's content
     * coordinates - the scroll offset is already applied on the way in - so covering the content is what
     * makes them scroll with it.
     */
    private int gridHeight() {
        Layout layout = getLayout();
        int text = layout == null ? 0
                : layout.getHeight() + getTotalPaddingTop() + getTotalPaddingBottom();
        return Math.max(getHeight() + getScrollY(), text);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (float top : PaperRules.lineOffsets(gridHeight(), linePeriod(), thickness)) {
            canvas.drawRect(0f, top, getWidth(), top + thickness, rules);
        }
        super.onDraw(canvas);
    }

    /** Test seams: the grid must stay token-derived, so both values are read back and asserted. */
    public int ruleColour() {
        return rules.getColor();
    }

    public float gridSpacingPx() {
        return gridSpacing;
    }

    public float thicknessPx() {
        return thickness;
    }
}
