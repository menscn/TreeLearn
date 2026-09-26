package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.HomeActions;
import com.treelearn.app.ui.HeatmapView;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.TopicCard;

import java.util.Collections;

/**
 * The home page as a view (PRD 6.2.16, slimmed by 055): brand, composer, continue-learning,
 * recommendations.
 *
 * <p>It owns no decisions: every gesture is forwarded to {@link HomeActions} and every pixel is
 * painted from a {@link HomeViewState}. The one piece of local state is the "echo" guard - the screen
 * does not push text back into the composer while the composer is the one that just changed it, so a
 * keystroke never moves the cursor.
 *
 * <p>The history list the page used to draw lives in the library now (052); the page keeps only the
 * continue-learning row, which is the state's own pick of the most recent topic and disappears
 * entirely when there is no history. Browsing everything is the library's job.
 */
public final class HomeScreen extends LinearLayout {

    private final EditText draft;
    private final Button send;
    private final TextView keyHint;
    private final TextView notice;
    private final View hero;
    private final View continueRow;
    private final TextView continueTitle;
    private final ModelThinkingPicker modelChip;
    private final RecommendationMarquee recommendations;
    private final View heatStripCard;
    private final TextView heatStreak;
    private final TextView heatEmpty;
    private final HeatmapView heatCells;

    private HomeActions actions;
    /** The drawer button (T-106); the host owns the drawer, the screen only reports the press. */
    private Runnable onDrawerPressed;

    /** The drawer button's press lands on the host, which owns the drawer's lifecycle. */
    public void setOnDrawerPressed(Runnable onDrawerPressed) {
        this.onDrawerPressed = onDrawerPressed;
    }
    private boolean rendering;

    public HomeScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_home, this, true);

        draft = findViewById(R.id.home_draft);
        send = findViewById(R.id.home_send);
        keyHint = findViewById(R.id.home_key_hint);
        notice = findViewById(R.id.home_notice);
        hero = findViewById(R.id.home_hero);
        continueRow = findViewById(R.id.home_continue);
        continueTitle = findViewById(R.id.home_continue_title);
        modelChip = findViewById(R.id.home_model_chip);
        recommendations = findViewById(R.id.home_recommendations);
        heatStripCard = findViewById(R.id.home_heat_strip_card);
        heatStreak = findViewById(R.id.home_heat_streak);
        heatEmpty = findViewById(R.id.home_heat_empty);
        heatCells = findViewById(R.id.home_heat_cells);

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
        continueRow.setOnClickListener(view -> {
            if (actions != null && continueRow.getTag() instanceof TopicCard) {
                actions.onTopicTapped((TopicCard) continueRow.getTag());
            }
        });
        heatStripCard.setOnClickListener(view -> {
            if (actions != null) {
                actions.onHeatStripTapped();
            }
        });
    }

    public void setActions(HomeActions actions) {
        this.actions = actions;
        findViewById(R.id.home_drawer_button).setOnClickListener(view -> {
            if (onDrawerPressed != null) {
                onDrawerPressed.run();
            }
        });
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
            renderKeyHint(state.keyConfigured);
            renderNotice(state.notice);
            hero.setVisibility(state.phase == HomeViewState.Phase.READY ? GONE : VISIBLE);
            renderContinue(state.continueTopic());
            boolean stripUsable = state.phase == HomeViewState.Phase.READY
                    || state.phase == HomeViewState.Phase.EMPTY;
            recommendations.render(stripUsable ? state.recommendations : Collections.emptyList());
            renderHeatStrip(state.heatStrip);
        } finally {
            rendering = false;
        }
    }

    /**
     * The continue-learning row is the state's decision, drawn without judgment: a card to resume,
     * or nothing at all - never an empty placeholder where the row would be.
     */
    private void renderContinue(TopicCard card) {
        if (card == null) {
            continueRow.setVisibility(GONE);
            continueRow.setTag(null);
            continueTitle.setText("");
            return;
        }
        continueRow.setTag(card);
        continueTitle.setText(card.title);
        continueRow.setVisibility(VISIBLE);
    }

    private void renderHeatStrip(HomeViewState.HeatStrip strip) {
        boolean visible = strip.status == HomeViewState.HeatStrip.Status.VISIBLE;
        heatStripCard.setVisibility(strip.status == HomeViewState.HeatStrip.Status.HIDDEN ? GONE : VISIBLE);
        heatStreak.setVisibility(visible ? VISIBLE : GONE);
        heatEmpty.setVisibility(strip.status == HomeViewState.HeatStrip.Status.EMPTY ? VISIBLE : GONE);
        heatCells.setVisibility(visible ? VISIBLE : GONE);
        if (visible) {
            heatCells.setIntensities(strip.intensities);
            heatStreak.setText(getContext().getString(R.string.home_heat_streak, strip.streakDays));
        }
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
}
