/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.util.AttributeSet
 *  android.view.LayoutInflater
 *  android.view.MotionEvent
 *  android.view.View
 *  android.view.ViewGroup
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.FrameLayout$LayoutParams
 *  android.widget.HorizontalScrollView
 *  android.widget.LinearLayout
 *  android.widget.TextView
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.Recommendation;
import java.util.List;

public final class RecommendationMarquee
extends HorizontalScrollView {
    private static final int DRIFT_INTERVAL_MILLIS = 16;
    private static final int RESUME_DELAY_MILLIS = 2600;
    private static final int DRIFT_PIXELS = 1;
    private final LinearLayout strip;
    private Listener listener;
    private boolean paused;
    private final Runnable drift = new Runnable(){

        @Override
        public void run() {
            RecommendationMarquee.this.advance();
            if (RecommendationMarquee.this.isAttachedToWindow() && RecommendationMarquee.this.isShown()) {
                RecommendationMarquee.this.postDelayed(this, 16L);
            }
        }
    };
    private final Runnable resume = new Runnable(){

        @Override
        public void run() {
            RecommendationMarquee.this.paused = false;
            RecommendationMarquee.this.schedule();
        }
    };

    public RecommendationMarquee(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setHorizontalScrollBarEnabled(false);
        this.setFillViewport(false);
        this.strip = new LinearLayout(context);
        this.strip.setOrientation(0);
        this.addView((View)this.strip, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-2, -2));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void render(List<Recommendation> items) {
        this.stop();
        this.strip.removeAllViews();
        if (items.isEmpty()) {
            this.setVisibility(8);
            return;
        }
        this.setVisibility(0);
        LayoutInflater inflater = LayoutInflater.from((Context)this.getContext());
        for (int copy = 0; copy < 2; ++copy) {
            for (Recommendation item : items) {
                this.strip.addView(this.newChip(inflater, item));
            }
        }
        this.scrollTo(0, 0);
        this.paused = false;
    }

    private View newChip(LayoutInflater inflater, Recommendation item) {
        View chip = inflater.inflate(R.layout.item_recommendation, (ViewGroup)this.strip, false);
        TextView tag = (TextView)chip.findViewById(R.id.rec_tag);
        TextView text = (TextView)chip.findViewById(R.id.rec_text);
        tag.setText(RecommendationMarquee.tagLabelRes(item.kind));
        text.setText((CharSequence)item.question);
        chip.setOnClickListener(view -> {
            if (this.listener != null) {
                this.listener.onRecommendationTapped(item);
            }
        });
        return chip;
    }

    private void advance() {
        if (this.paused) {
            return;
        }
        int loop = this.strip.getWidth() / 2;
        if (loop <= 0 || loop <= this.getWidth()) {
            return;
        }
        int next = this.getScrollX() + 1;
        this.scrollTo(next >= loop ? next - loop : next, 0);
    }

    private void schedule() {
        this.removeCallbacks(this.drift);
        if (this.isShown()) {
            this.postDelayed(this.drift, 16L);
        }
    }

    private void stop() {
        this.removeCallbacks(this.drift);
        this.removeCallbacks(this.resume);
    }

    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == 0) {
            this.paused = true;
            this.removeCallbacks(this.drift);
            this.removeCallbacks(this.resume);
        } else if (action == 1 || action == 3) {
            this.removeCallbacks(this.resume);
            this.postDelayed(this.resume, 2600L);
        }
        return super.onTouchEvent(event);
    }

    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == 0) {
            this.schedule();
        } else {
            this.removeCallbacks(this.drift);
        }
    }

    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == 0) {
            this.schedule();
        } else {
            this.removeCallbacks(this.drift);
        }
    }

    protected void onDetachedFromWindow() {
        this.stop();
        super.onDetachedFromWindow();
    }

    private static int tagLabelRes(Recommendation.Kind kind) {
        switch (kind) {
            case FOLLOW_UP: {
                return R.string.home_rec_tag_follow;
            }
            case NEW_DIRECTION: {
                return R.string.home_rec_tag_fresh;
            }
        }
        return R.string.home_rec_tag_starter;
    }

    public static interface Listener {
        public void onRecommendationTapped(Recommendation var1);
    }
}

