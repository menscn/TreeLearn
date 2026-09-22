package com.treelearn.app.screen;

import android.content.Context;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.treelearn.app.R;
import com.treelearn.app.ui.HomeActions;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.TopicCard;

import java.util.Collections;
import java.util.List;

/**
 * The single home page as a view (PRD 6.2.16).
 *
 * <p>It owns no decisions: every gesture is forwarded to {@link HomeActions} and every pixel is
 * painted from a {@link HomeViewState}. The one piece of local state is the "echo" guard - the screen
 * does not push text back into the composer while the composer is the one that just changed it, so a
 * keystroke never moves the cursor.
 */
public final class HomeScreen extends LinearLayout {

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

    private HomeActions actions;
    private boolean rendering;

    public HomeScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_home, this, true);

        draft = findViewById(R.id.home_draft);
        send = findViewById(R.id.home_send);
        search = findViewById(R.id.home_search);
        archived = findViewById(R.id.home_archived);
        keyHint = findViewById(R.id.home_key_hint);
        notice = findViewById(R.id.home_notice);
        sectionCount = findViewById(R.id.home_section_count);
        hero = findViewById(R.id.home_hero);
        topics = findViewById(R.id.home_topics);
        loading = findViewById(R.id.home_loading);
        error = findViewById(R.id.home_error);
        empty = findViewById(R.id.home_empty);
        modelChip = findViewById(R.id.home_model_chip);
        recommendations = findViewById(R.id.home_recommendations);

        draft.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                if (!rendering && actions != null) {
                    actions.onDraftChanged(text.toString());
                }
            }
        });
        draft.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                if (!rendering && actions != null) {
                    actions.onSubmit();
                }
                return true;
            }
            return false;
        });
        send.setOnClickListener(view -> {
            if (actions != null) {
                actions.onSubmit();
            }
        });
        keyHint.setOnClickListener(view -> {
            if (actions != null) {
                actions.onSettingsPressed();
            }
        });
        findViewById(R.id.home_settings).setOnClickListener(view -> {
            if (actions != null) {
                actions.onSettingsPressed();
            }
        });
        archived.setOnClickListener(view -> {
            if (actions != null) {
                actions.onArchivedPressed();
            }
        });
        search.setOnClickListener(view -> {
            if (actions != null) {
                actions.onSearchPressed();
            }
        });
        findViewById(R.id.home_empty_guide).setOnClickListener(view -> {
            draft.requestFocus();
            draft.setSelection(draft.getText().length());
        });
    }

    public void setActions(HomeActions actions) {
        this.actions = actions;
        modelChip.setListener(new ModelThinkingPicker.Listener() {
            @Override
            public void onModelSelected(com.treelearn.core.domain.GenerationModel model) {
                actions.onModelSelected(model);
            }

            @Override
            public void onThinkingToggled(boolean enabled) {
                actions.onThinkingToggled(enabled);
            }
        });
        recommendations.setListener(new RecommendationMarquee.Listener() {
            @Override
            public void onRecommendationTapped(Recommendation recommendation) {
                actions.onRecommendationTapped(recommendation);
            }
        });
    }

    public void render(HomeViewState state) {
        rendering = true;
        try {
            if (!state.draft.contentEquals(draft.getText())) {
                draft.setText(state.draft);
                draft.setSelection(draft.getText().length());
            }
            modelChip.render(state.model, state.thinkingEnabled);
            send.setEnabled(state.canSubmit());
            archived.setEnabled(!state.topics.isEmpty());
            renderKeyHint(state.keyConfigured);
            renderNotice(state.notice);
            renderPhase(state);
            renderTopics(state.topics);
            sectionCount.setText(state.phase == HomeViewState.Phase.READY
                    ? getContext().getString(R.string.home_section_count, state.topics.size())
                    : "");
            boolean stripUsable = state.phase == HomeViewState.Phase.READY
                    || state.phase == HomeViewState.Phase.EMPTY;
            recommendations.render(stripUsable ? state.recommendations : Collections.emptyList());
        } finally {
            rendering = false;
        }
    }

    private void renderPhase(HomeViewState state) {
        hero.setVisibility(state.phase == HomeViewState.Phase.READY ? GONE : VISIBLE);
        loading.setVisibility(state.phase == HomeViewState.Phase.LOADING ? VISIBLE : GONE);
        error.setVisibility(state.phase == HomeViewState.Phase.ERROR ? VISIBLE : GONE);
        empty.setVisibility(state.phase == HomeViewState.Phase.EMPTY ? VISIBLE : GONE);
        topics.setVisibility(state.phase == HomeViewState.Phase.READY ? VISIBLE : GONE);
    }

    /** Ready reads as a calm note, a missing key reads as the warning the prototype draws. */
    private void renderKeyHint(boolean keyConfigured) {
        keyHint.setText(keyConfigured ? R.string.home_key_ready : R.string.home_key_missing);
        keyHint.setBackgroundTintList(ColorStateList.valueOf(getContext()
                .getColor(keyConfigured ? R.color.tl_green_soft : R.color.tl_amber_soft)));
        keyHint.setTextColor(getContext()
                .getColor(keyConfigured ? R.color.tl_green_deep : R.color.tl_amber));
    }

    private void renderNotice(HomeViewState.Notice value) {
        if (value == null) {
            notice.setVisibility(GONE);
            notice.setText("");
            return;
        }
        notice.setText(getContext().getString(NoticeText.of(value)));
        notice.setVisibility(VISIBLE);
    }

    private void renderTopics(List<TopicCard> cards) {
        topics.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (final TopicCard card : cards) {
            View row = inflater.inflate(R.layout.item_topic, topics, false);
            ((TextView) row.findViewById(R.id.topic_title)).setText(card.title);
            TextView summary = row.findViewById(R.id.topic_summary);
            summary.setText(card.summary);
            summary.setVisibility(card.summary.isEmpty() ? GONE : VISIBLE);
            ((TextView) row.findViewById(R.id.topic_meta)).setText(meta(card));
            row.findViewById(R.id.topic_rename).setOnClickListener(view -> rename(card));
            row.findViewById(R.id.topic_delete).setOnClickListener(view -> delete(card));
            row.setOnClickListener(view -> {
                if (actions != null) {
                    actions.onTopicTapped(card);
                }
            });
            topics.addView(row);
        }
    }

    /** The last use is formatted by the platform, so it follows the device's own zone and locale. */
    private CharSequence meta(TopicCard card) {
        CharSequence when = DateUtils.getRelativeTimeSpanString(card.lastUsedAt,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        CharSequence base = getContext().getString(R.string.home_topic_meta, card.branchCount, when);
        return card.unreadCount == 0 ? base : base + " · "
                + getContext().getString(R.string.home_topic_unread, card.unreadCount);
    }

    private void rename(TopicCard card) {
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setText(card.title);
        input.setSelection(input.length());
        new AlertDialog.Builder(getContext()).setTitle(R.string.home_topic_rename_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> actions.onRenameTopic(card, input.getText().toString()))
                .show();
    }

    private void delete(TopicCard card) {
        new AlertDialog.Builder(getContext()).setTitle(R.string.home_topic_delete_title)
                .setMessage(getContext().getString(R.string.home_topic_delete_message,
                        card.branchCount))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.home_topic_delete,
                        (dialog, which) -> actions.onDeleteTopic(card))
                .show();
    }

}
