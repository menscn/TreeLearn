package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.Recommendation;

import java.util.ArrayList;
import java.util.List;

/**
 * The recommendation strip: chips that drift sideways, pause while the learner touches them, and
 * resume shortly after (PRD 6.2.17 point 2).
 *
 * <p>Two implementation facts worth knowing:
 *
 * <ul>
 *   <li>The recommendations are split into two independently moving rows. Each row draws its chip
 *       list twice and loops by one copy's width, so the seam never appears - there is no end to reach.
 *       The first row moves left and the second row moves right.
 *   <li>The drift stops whenever the strip is not on screen, is detached, or is too narrow to scroll.
 *       PRD 6.2.17 exception 4 explicitly does not require the animation to keep running off screen.
 * </ul>
 *
 * <p>The two rows live inside one {@link EdgeFadeLayout}, which dissolves both of them together at
 * the block's left and right edges (the prototype masks {@code .rec-block}, not each row). The mask
 * is on the wrapper rather than on the rows because two independently scrolling rows each carrying
 * their own fade would be cut at two different heights.
 *
 * <p>It carries no state of its own beyond "paused": which chips exist is decided by the presenter, and a
 * tap is reported straight back.
 *
 * <p>What a chip says and how its category tag looks are not decided here either - {@link RecommendationTag}
 * owns that, and this class only asks it (T-2026-09-27-005).
 */
public final class RecommendationMarquee extends LinearLayout {

    /** Where a tapped chip goes. */
    public interface Listener {

        void onRecommendationTapped(Recommendation recommendation);
    }

    private static final int DRIFT_INTERVAL_MILLIS = 16;

    /** The prototype's own resume delay: long enough to read the chip that was touched. */
    private static final int RESUME_DELAY_MILLIS = 2600;

    private static final int DRIFT_PIXELS = 1;

    /** The block the rows are drawn in, and the only thing that fades. */
    private final EdgeFadeLayout block;

    private final List<HorizontalScrollView> rows = new ArrayList<HorizontalScrollView>(2);
    private final List<LinearLayout> strips = new ArrayList<LinearLayout>(2);
    private final List<Integer> directions = new ArrayList<Integer>(2);

    /**
     * The chips currently on screen, or null while the strip has never been drawn.
     *
     * <p>Null rather than an empty list on purpose: an empty list is a real state (the strip has been
     * told there is nothing to show, and has hidden itself), and conflating "nothing was ever drawn"
     * with it would make the first render of an empty strip skip the visibility write it owes.
     *
     * <p>Compared by value on every render, because the screen hands the whole state down on every
     * keystroke and rebuilding the rows would restart the drift and lose the learner's reading
     * position for a change that did not touch the recommendations.
     */
    private List<Recommendation> drawn;

    private Listener listener;
    private boolean paused;

    private final Runnable drift = new Runnable() {
        @Override
        public void run() {
            advance();
            if (isAttachedToWindow() && isShown()) {
                postDelayed(this, DRIFT_INTERVAL_MILLIS);
            }
        }
    };

    private final Runnable resume = new Runnable() {
        @Override
        public void run() {
            paused = false;
            schedule();
        }
    };

    public RecommendationMarquee(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setClipChildren(false);
        block = new EdgeFadeLayout(context, attrs);
        addView(block, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Replaces the chips. An empty strip hides itself rather than showing a blank band.
     *
     * <p>A render that carries the same chips in the same order does nothing at all - not even a
     * visibility write. The screen renders the whole state on every keystroke, so without this the
     * rows would be torn down and rebuilt while the learner types, snapping the drift back to zero
     * and re-running the first frame's layout. "Nothing to change" is answered here, by comparing
     * what is about to be drawn with what already is, rather than by asking the caller to diff.
     */
    public void render(List<Recommendation> items) {
        List<Recommendation> next = new ArrayList<Recommendation>(items);
        if (drawn != null && next.equals(drawn)) {
            return;
        }
        drawn = next;
        stop();
        block.removeAllViews();
        rows.clear();
        strips.clear();
        directions.clear();
        if (items.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        block.setVisibility(VISIBLE);
        List<Recommendation> first = new ArrayList<Recommendation>();
        List<Recommendation> second = new ArrayList<Recommendation>();
        for (int index = 0; index < items.size(); index++) {
            (index % 2 == 0 ? first : second).add(items.get(index));
        }
        if (second.isEmpty()) {
            second.addAll(first);
        }
        addRow(first, 1, false);
        addRow(second, -1, true);
        paused = false;
        post(this::schedule);
    }

    private void addRow(List<Recommendation> items, int direction, boolean separated) {
        HorizontalScrollView row = new HorizontalScrollView(getContext());
        row.setHorizontalScrollBarEnabled(false);
        row.setFillViewport(false);
        row.setClipToPadding(false);
        row.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                paused = true;
                removeCallbacks(drift);
                removeCallbacks(resume);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                removeCallbacks(resume);
                postDelayed(resume, RESUME_DELAY_MILLIS);
            }
            return false;
        });

        LinearLayout strip = new LinearLayout(getContext());
        strip.setOrientation(LinearLayout.HORIZONTAL);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int copy = 0; copy < 2; copy++) {
            for (Recommendation item : items) {
                strip.addView(newChip(inflater, strip, item));
            }
        }
        row.addView(strip, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams rowParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        if (separated) {
            rowParams.topMargin = getResources().getDimensionPixelSize(R.dimen.space_s1);
        }
        block.addView(row, rowParams);
        rows.add(row);
        strips.add(strip);
        directions.add(direction);
    }

    private View newChip(LayoutInflater inflater, LinearLayout parent, final Recommendation item) {
        View chip = inflater.inflate(R.layout.item_recommendation, parent, false);
        TextView tag = chip.findViewById(R.id.rec_tag);
        TextView text = chip.findViewById(R.id.rec_text);
        // Label and tint both come from the one judgement; the markup carries neither, so there is no
        // second place for a category's look to be decided (T-2026-09-27-005).
        tag.setText(RecommendationTag.labelRes(item.kind));
        tag.setTextColor(getContext().getColor(RecommendationTag.textColorRes(item.kind)));
        tag.setBackgroundTintList(ColorStateList.valueOf(
                getContext().getColor(RecommendationTag.backgroundRes(item.kind))));
        text.setText(item.question);
        chip.setOnClickListener(view -> {
            if (listener != null) {
                listener.onRecommendationTapped(item);
            }
        });
        return chip;
    }

    private void advance() {
        if (paused) {
            return;
        }
        for (int index = 0; index < rows.size(); index++) {
            HorizontalScrollView row = rows.get(index);
            LinearLayout strip = strips.get(index);
            int loop = strip.getWidth() / 2;
            if (loop <= 0 || loop <= row.getWidth()) {
                continue;
            }
            int direction = directions.get(index);
            int next = row.getScrollX() + direction * DRIFT_PIXELS;
            if (direction > 0) {
                row.scrollTo(next >= loop ? next - loop : next, 0);
            } else {
                row.scrollTo(next <= 0 ? loop - 1 : next, 0);
            }
        }
    }

    private void schedule() {
        removeCallbacks(drift);
        if (isShown()) {
            postDelayed(drift, DRIFT_INTERVAL_MILLIS);
        }
    }

    private void stop() {
        removeCallbacks(drift);
        removeCallbacks(resume);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            schedule();
        } else {
            removeCallbacks(drift);
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            schedule();
        } else {
            removeCallbacks(drift);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
