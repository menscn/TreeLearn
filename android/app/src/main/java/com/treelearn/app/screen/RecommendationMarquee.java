package com.treelearn.app.screen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.Recommendation;

import java.util.List;

/**
 * The recommendation strip: chips that drift sideways, pause while the learner touches them, and
 * resume shortly after (PRD 6.2.17 point 2).
 *
 * <p>Two implementation facts worth knowing:
 *
 * <ul>
 *   <li>The chip list is drawn twice. The strip scrolls by one copy and then jumps back by exactly
 *       that copy's width, so the seam never appears - there is no end to reach.
 *   <li>The drift stops whenever the strip is not on screen, is detached, or is too narrow to scroll.
 *       PRD 6.2.17 exception 4 explicitly does not require the animation to keep running off screen.
 * </ul>
 *
 * <p>It carries no state of its own beyond "paused": which chips exist is decided by the presenter,
 * and a tap is reported straight back.
 */
public final class RecommendationMarquee extends HorizontalScrollView {

    /** Where a tapped chip goes. */
    public interface Listener {

        void onRecommendationTapped(Recommendation recommendation);
    }

    private static final int DRIFT_INTERVAL_MILLIS = 16;

    /** The prototype's own resume delay: long enough to read the chip that was touched. */
    private static final int RESUME_DELAY_MILLIS = 2600;

    private static final int DRIFT_PIXELS = 1;

    private final LinearLayout strip;

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
        setHorizontalScrollBarEnabled(false);
        setFillViewport(false);
        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        addView(strip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Replaces the chips. An empty strip hides itself rather than showing a blank band. */
    public void render(List<Recommendation> items) {
        stop();
        strip.removeAllViews();
        if (items.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int copy = 0; copy < 2; copy++) {
            for (Recommendation item : items) {
                strip.addView(newChip(inflater, item));
            }
        }
        scrollTo(0, 0);
        paused = false;
    }

    private View newChip(LayoutInflater inflater, final Recommendation item) {
        View chip = inflater.inflate(R.layout.item_recommendation, strip, false);
        TextView tag = chip.findViewById(R.id.rec_tag);
        TextView text = chip.findViewById(R.id.rec_text);
        tag.setText(tagLabelRes(item.kind));
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
        int loop = strip.getWidth() / 2;
        if (loop <= 0 || loop <= getWidth()) {
            return;
        }
        int next = getScrollX() + DRIFT_PIXELS;
        scrollTo(next >= loop ? next - loop : next, 0);
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
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            paused = true;
            removeCallbacks(drift);
            removeCallbacks(resume);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(resume);
            postDelayed(resume, RESUME_DELAY_MILLIS);
        }
        return super.onTouchEvent(event);
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

    private static int tagLabelRes(Recommendation.Kind kind) {
        switch (kind) {
            case FOLLOW_UP:
                return R.string.home_rec_tag_follow;
            case NEW_DIRECTION:
                return R.string.home_rec_tag_fresh;
            case STARTER:
            default:
                return R.string.home_rec_tag_starter;
        }
    }
}