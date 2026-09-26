/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.AlertDialog$Builder
 *  android.content.Context
 *  android.content.res.ColorStateList
 *  android.text.Editable
 *  android.text.TextWatcher
 *  android.text.format.DateUtils
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.widget.Button
 *  android.widget.EditText
 *  android.widget.LinearLayout
 *  android.widget.TextView
 *  com.treelearn.app.R$color
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.screen;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.screen.ModelThinkingPicker;
import com.treelearn.app.screen.NoticeText;
import com.treelearn.app.screen.RecommendationMarquee;
import com.treelearn.app.ui.HeatmapView;
import com.treelearn.app.ui.HomeActions;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.TopicCard;
import com.treelearn.core.domain.GenerationModel;
import java.util.Collections;
import java.util.List;

public final class HomeScreen
extends LinearLayout {
    private final EditText draft;
    private final Button send;
    private final Button search;
    private final Button archived;
    private final TextView keyHint;
    private final TextView notice;
    private final TextView sectionCount;
    private final View hero;
    private final LinearLayout topics;
    private final View loading;
    private final View error;
    private final View empty;
    private final ModelThinkingPicker modelChip;
    private final RecommendationMarquee recommendations;
    private final View heatStripCard;
    private final TextView heatStreak;
    private final TextView heatEmpty;
    private final HeatmapView heatCells;
    private HomeActions actions;
    private boolean rendering;

    public HomeScreen(Context context) {
        super(context);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.screen_home, (ViewGroup)this, true);
        this.draft = (EditText)this.findViewById(R.id.home_draft);
        this.send = (Button)this.findViewById(R.id.home_send);
        this.search = (Button)this.findViewById(R.id.home_search);
        this.archived = (Button)this.findViewById(R.id.home_archived);
        this.keyHint = (TextView)this.findViewById(R.id.home_key_hint);
        this.notice = (TextView)this.findViewById(R.id.home_notice);
        this.sectionCount = (TextView)this.findViewById(R.id.home_section_count);
        this.hero = this.findViewById(R.id.home_hero);
        this.topics = (LinearLayout)this.findViewById(R.id.home_topics);
        this.loading = this.findViewById(R.id.home_loading);
        this.error = this.findViewById(R.id.home_error);
        this.empty = this.findViewById(R.id.home_empty);
        this.modelChip = (ModelThinkingPicker)this.findViewById(R.id.home_model_chip);
        this.recommendations = (RecommendationMarquee)this.findViewById(R.id.home_recommendations);
        this.heatStripCard = this.findViewById(R.id.home_heat_strip_card);
        this.heatStreak = (TextView)this.findViewById(R.id.home_heat_streak);
        this.heatEmpty = (TextView)this.findViewById(R.id.home_heat_empty);
        this.heatCells = (HeatmapView)this.findViewById(R.id.home_heat_cells);
        this.draft.addTextChangedListener(new TextWatcher(){

            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            public void afterTextChanged(Editable text) {
                if (!HomeScreen.this.rendering && HomeScreen.this.actions != null) {
                    HomeScreen.this.actions.onDraftChanged(text.toString());
                }
            }
        });
        this.draft.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == 4 || event != null && event.getKeyCode() == 66 && event.getAction() == 0) {
                if (!this.rendering && this.actions != null) {
                    this.actions.onSubmit();
                }
                return true;
            }
            return false;
        });
        this.send.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onSubmit();
            }
        });
        this.keyHint.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onSettingsPressed();
            }
        });
        this.findViewById(R.id.home_settings).setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onSettingsPressed();
            }
        });
        this.archived.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onArchivedPressed();
            }
        });
        this.search.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onSearchPressed();
            }
        });
        this.findViewById(R.id.home_empty_guide).setOnClickListener(view -> {
            this.draft.requestFocus();
            this.draft.setSelection(this.draft.getText().length());
        });
        this.heatStripCard.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onHeatStripTapped();
            }
        });
    }

    public void setActions(final HomeActions actions) {
        this.actions = actions;
        this.modelChip.setListener(new ModelThinkingPicker.Listener(){

            @Override
            public void onModelSelected(GenerationModel model) {
                actions.onModelSelected(model);
            }

            @Override
            public void onThinkingToggled(boolean enabled) {
                actions.onThinkingToggled(enabled);
            }
        });
        this.recommendations.setListener(new RecommendationMarquee.Listener(){

            @Override
            public void onRecommendationTapped(Recommendation recommendation) {
                actions.onRecommendationTapped(recommendation);
            }
        });
    }

    public void render(HomeViewState state) {
        this.rendering = true;
        try {
            if (!state.draft.contentEquals((CharSequence)this.draft.getText())) {
                this.draft.setText((CharSequence)state.draft);
                this.draft.setSelection(this.draft.getText().length());
            }
            this.modelChip.render(state.model, state.thinkingEnabled);
            this.send.setEnabled(state.canSubmit());
            this.archived.setEnabled(!state.topics.isEmpty());
            this.renderKeyHint(state.keyConfigured);
            this.renderNotice(state.notice);
            this.renderPhase(state);
            this.renderTopics(state.topics);
            this.sectionCount.setText((CharSequence)(state.phase == HomeViewState.Phase.READY ? this.getContext().getString(R.string.home_section_count, new Object[]{state.topics.size()}) : ""));
            boolean stripUsable = state.phase == HomeViewState.Phase.READY || state.phase == HomeViewState.Phase.EMPTY;
            this.recommendations.render(stripUsable ? state.recommendations : Collections.emptyList());
            this.renderHeatStrip(state.heatStrip);
        }
        finally {
            this.rendering = false;
        }
    }

    private void renderHeatStrip(HomeViewState.HeatStrip strip) {
        boolean visible = strip.status == HomeViewState.HeatStrip.Status.VISIBLE;
        this.heatStripCard.setVisibility(strip.status == HomeViewState.HeatStrip.Status.HIDDEN ? 8 : 0);
        this.heatStreak.setVisibility(visible ? 0 : 8);
        this.heatEmpty.setVisibility(strip.status == HomeViewState.HeatStrip.Status.EMPTY ? 0 : 8);
        this.heatCells.setVisibility(visible ? 0 : 8);
        if (visible) {
            this.heatCells.setIntensities(strip.intensities);
            this.heatStreak.setText((CharSequence)this.getContext().getString(R.string.home_heat_streak, new Object[]{strip.streakDays}));
        }
    }

    private void renderPhase(HomeViewState state) {
        this.hero.setVisibility(state.phase == HomeViewState.Phase.READY ? 8 : 0);
        this.loading.setVisibility(state.phase == HomeViewState.Phase.LOADING ? 0 : 8);
        this.error.setVisibility(state.phase == HomeViewState.Phase.ERROR ? 0 : 8);
        this.empty.setVisibility(state.phase == HomeViewState.Phase.EMPTY ? 0 : 8);
        this.topics.setVisibility(state.phase == HomeViewState.Phase.READY ? 0 : 8);
    }

    private void renderKeyHint(boolean keyConfigured) {
        this.keyHint.setText(keyConfigured ? R.string.home_key_ready : R.string.home_key_missing);
        this.keyHint.setBackgroundTintList(ColorStateList.valueOf((int)this.getContext().getColor(keyConfigured ? R.color.tl_green_soft : R.color.tl_amber_soft)));
        this.keyHint.setTextColor(this.getContext().getColor(keyConfigured ? R.color.tl_green_deep : R.color.tl_amber));
    }

    private void renderNotice(HomeViewState.Notice value) {
        if (value == null) {
            this.notice.setVisibility(8);
            this.notice.setText((CharSequence)"");
            return;
        }
        this.notice.setText((CharSequence)this.getContext().getString(NoticeText.of(value)));
        this.notice.setVisibility(0);
    }

    private void renderTopics(List<TopicCard> cards) {
        this.topics.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from((Context)this.getContext());
        for (TopicCard card : cards) {
            View row = inflater.inflate(R.layout.item_topic, (ViewGroup)this.topics, false);
            ((TextView)row.findViewById(R.id.topic_title)).setText((CharSequence)card.title);
            TextView summary = (TextView)row.findViewById(R.id.topic_summary);
            summary.setText((CharSequence)card.summary);
            summary.setVisibility(card.summary.isEmpty() ? 8 : 0);
            ((TextView)row.findViewById(R.id.topic_meta)).setText(this.meta(card));
            row.findViewById(R.id.topic_rename).setOnClickListener(view -> this.rename(card));
            row.findViewById(R.id.topic_delete).setOnClickListener(view -> this.delete(card));
            row.setOnClickListener(view -> {
                if (this.actions != null) {
                    this.actions.onTopicTapped(card);
                }
            });
            this.topics.addView(row);
        }
    }

    private CharSequence meta(TopicCard card) {
        CharSequence when = DateUtils.getRelativeTimeSpanString((long)card.lastUsedAt, (long)System.currentTimeMillis(), (long)60000L);
        String base = this.getContext().getString(R.string.home_topic_meta, new Object[]{card.branchCount, when});
        return card.unreadCount == 0L ? base : base + " \u00b7 " + this.getContext().getString(R.string.home_topic_unread, new Object[]{card.unreadCount});
    }

    private void rename(TopicCard card) {
        EditText input = new EditText(this.getContext());
        input.setSingleLine(true);
        input.setText((CharSequence)card.title);
        input.setSelection(input.length());
        new AlertDialog.Builder(this.getContext()).setTitle(R.string.home_topic_rename_title).setView((View)input).setNegativeButton(0x1040000, null).setPositiveButton(17039370, (dialog, which) -> this.actions.onRenameTopic(card, input.getText().toString())).show();
    }

    private void delete(TopicCard card) {
        new AlertDialog.Builder(this.getContext()).setTitle(R.string.home_topic_delete_title).setMessage((CharSequence)this.getContext().getString(R.string.home_topic_delete_message, new Object[]{card.branchCount})).setNegativeButton(0x1040000, null).setPositiveButton(R.string.home_topic_delete, (dialog, which) -> this.actions.onDeleteTopic(card)).show();
    }
}

