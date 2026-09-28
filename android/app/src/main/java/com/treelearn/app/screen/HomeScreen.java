package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.HomeActions;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.TopicCard;

import java.util.Collections;
import java.util.List;

/**
 * The home page as a view (PRD 6.2.16): brand, recommendations, composer, recent learning.
 *
 * <p>It owns no decisions: every gesture is forwarded to {@link HomeActions} and every pixel is
 * painted from a {@link HomeViewState}. The one piece of local state is the "echo" guard - the screen
 * does not push text back into the composer while the composer is the one that just changed it, so a
 * keystroke never moves the cursor.
 *
 * <p>The history section is a compact shortcut surface: it draws at most {@link #MAX_RECENT} of the
 * state's newest topics, counts only what the read found, says so when it found nothing, and forwards
 * each tap to the same resume action as the library.
 */
public final class HomeScreen extends LinearLayout {

    private final EditText draft;
    private final ImageButton send;
    /** The key pill: the container carries the tint, the icon and the text follow it (T-004). */
    private final LinearLayout keyPill;
    private final ImageView keyIcon;
    private final TextView keyText;
    private final TextView notice;
    private final View hero;
    /** The main slogan: shown only while the home has no history topics (T-003). */
    private final TextView heroTitle;
    private final View recommendationsSection;
    /** The strip's short heading; its text is one of two strings, chosen by the same judgement as the hero (T-005). */
    private final TextView recommendationsTitle;
    private final View recentSection;
    /** The section head's count; empty while there is nothing to count (T-006). */
    private final TextView recentCount;
    /** The section's empty sentence, shown instead of the list while the home has no topics (T-006). */
    private final TextView recentEmpty;
    private final LinearLayout recentList;
    private final ModelThinkingPicker modelChip;
    private final RecommendationMarquee recommendations;

    private HomeActions actions;
    /** The drawer button (T-106); the host owns the drawer, the screen only reports the press. */
    private Runnable onDrawerPressed;

    /** The drawer button's press lands on the host, which owns the drawer's lifecycle. */
    public void setOnDrawerPressed(Runnable onDrawerPressed) {
        this.onDrawerPressed = onDrawerPressed;
    }
    private boolean rendering;
    /**
     * True from the moment the shell shows the home until the entrance has been spent on the cards
     * (T-2026-09-27-007). The first draw that carries cards staggers them; every later draw is a
     * redraw and stays still, so a keystroke cannot re-run the entrance.
     */
    private boolean entrancePending;

    public HomeScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_home, this, true);

        draft = findViewById(R.id.home_draft);
        send = findViewById(R.id.home_send);
        keyPill = findViewById(R.id.home_key_hint);
        keyIcon = findViewById(R.id.home_key_icon);
        keyText = findViewById(R.id.home_key_text);
        notice = findViewById(R.id.home_notice);
        hero = findViewById(R.id.home_hero);
        heroTitle = findViewById(R.id.home_hero_title);
        recommendationsSection = findViewById(R.id.home_recommendations_section);
        recommendationsTitle = findViewById(R.id.home_recommendations_title);
        recentSection = findViewById(R.id.home_recent_section);
        recentCount = findViewById(R.id.home_recent_count);
        recentEmpty = findViewById(R.id.home_topics_empty);
        recentList = findViewById(R.id.home_recent_list);
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
            hero.setVisibility(VISIBLE);
            heroTitle.setVisibility(state.hasTopics() ? GONE : VISIBLE);
            recommendationsTitle.setText(state.hasTopics()
                    ? R.string.home_rec_history : R.string.home_rec_starter);
            boolean stripUsable = state.phase == HomeViewState.Phase.READY
                    || state.phase == HomeViewState.Phase.EMPTY;
            recommendations.render(stripUsable ? state.recommendations : Collections.emptyList());
            recommendationsSection.setVisibility(stripUsable && !state.recommendations.isEmpty()
                    ? VISIBLE : GONE);
            renderRecent(state);
        } finally {
            rendering = false;
        }
    }

    /**
     * The entrance the shell starts whenever the home becomes the visible screen (T-2026-09-27-007).
     *
     * <p>The screen as a whole slides in from the right and fades up - the prototype's
     * {@code .screen} animation is on the screen, not on a part of it - and the history cards follow
     * one behind another. The cards are staggered by the draw that carries them - usually the first
     * render, since the topics come back from a store read after the swap - rather than from here,
     * so the stagger is never spent on cards that a later render is about to replace.
     *
     * <p>A device with the system's animations switched off gets nothing here, which is the whole
     * requirement: the content is simply already at its final state, never left translucent. The
     * judgement is {@link ChatMotion}'s - this app's one reader of the animator scale - so the home
     * cannot drift from the rest of the app about what "the system does not want motion" means.
     */
    public void playEntrance() {
        if (ChatMotion.reduced(getContext().getContentResolver())) {
            return;
        }
        entrancePending = true;
        startAnimation(HomeMotion.screenEntrance(getResources().getDisplayMetrics().density));
    }

    /**
     * How many history topics the home draws: the shortcut surface, not the full list.
     *
     * <p>Package-visible because {@code HomeMotion.CARD_STAGGER_STEPS_MAX} is justified by it - the
     * cap on a card's wait must sit above every place this list actually has - and the unit test
     * holds the two together.
     */
    static final int MAX_RECENT = 3;

    /**
     * The history section: a head that counts what is there, the empty sentence while nothing is, or
     * the cards themselves.
     *
     * <p>The section is drawn once the read has settled - a home that is still loading or that failed
     * to read has nothing trustworthy to say about topics yet. The count is hidden rather than zeroed
     * while there are none, which is what {@link HomeViewState#hasTopics()} already means.
     */
    private void renderRecent(HomeViewState state) {
        boolean settled = state.phase == HomeViewState.Phase.READY
                || state.phase == HomeViewState.Phase.EMPTY;
        boolean hasTopics = state.hasTopics();
        recentSection.setVisibility(settled ? VISIBLE : GONE);
        recentCount.setVisibility(hasTopics ? VISIBLE : GONE);
        recentCount.setText(hasTopics
                ? getContext().getString(R.string.home_recent_count, state.topics.size()) : "");
        recentEmpty.setVisibility(settled && !hasTopics ? VISIBLE : GONE);
        recentList.setVisibility(hasTopics ? VISIBLE : GONE);

        recentList.removeAllViews();
        int visibleCount = Math.min(MAX_RECENT, state.topics.size());
        for (int index = 0; index < visibleCount; index++) {
            View card = topicCard(state.topics.get(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (index + 1 < visibleCount) {
                params.bottomMargin = getResources().getDimensionPixelSize(R.dimen.space_s3);
            }
            recentList.addView(card, params);
        }
        if (entrancePending && visibleCount > 0) {
            staggerCards();
        }
    }

    /**
     * The card half of the entrance: every card in the list waits its own turn behind the ones
     * before it. The entrance is spent by this call - a later draw of the same cards is a redraw,
     * not a second entrance, and a home with no cards keeps the entrance pending until it has one.
     */
    private void staggerCards() {
        entrancePending = false;
        float density = getResources().getDisplayMetrics().density;
        for (int order = 0; order < recentList.getChildCount(); order++) {
            recentList.getChildAt(order)
                    .startAnimation(HomeMotion.cardEntrance(order, density));
        }
    }

    /**
     * One card, painted from its {@code TopicCard} and nothing else.
     *
     * <p>Every optional part is decided by a field that is actually there: no summary means no
     * summary line (never a stand-in sentence), and no unread gap means no dot. The card is the tap
     * target, so its whole surface is clickable and it states its own content description rather than
     * asking the reader to assemble one from four labels.
     */
    private View topicCard(TopicCard card) {
        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.item_home_topic, recentList, false);
        ((TextView) view.findViewById(R.id.home_topic_title)).setText(card.title);

        boolean summarised = !card.summary.trim().isEmpty();
        TextView summary = view.findViewById(R.id.home_topic_summary);
        summary.setVisibility(summarised ? VISIBLE : GONE);
        if (summarised) {
            summary.setText(card.summary);
        }

        ((TextView) view.findViewById(R.id.home_topic_meta)).setText(metaText(card));
        view.findViewById(R.id.home_topic_unread).setVisibility(card.unreadCount > 0 ? VISIBLE : GONE);
        view.setContentDescription(description(card));
        view.setOnClickListener(tapped -> {
            if (actions != null) {
                actions.onTopicTapped(card);
            }
        });
        return view;
    }

    /**
     * The meta line: branch count plus last use.
     *
     * <p>Both halves are the library row's: the same {@code branchCount} field and the same platform
     * relative-time formatter ({@code LibraryScreen.meta} reads them the same way), so the two
     * surfaces cannot disagree about what the line means. Only the wording resource differs, because
     * each surface owns its own string.
     */
    private CharSequence metaText(TopicCard card) {
        CharSequence when = DateUtils.getRelativeTimeSpanString(card.lastUsedAt,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        return getContext().getString(R.string.home_topic_meta, card.branchCount, when);
    }

    /**
     * What a screen reader hears. The unread count is spoken as well as drawn: the dot that stands for
     * it is a shape, and no fact may be reachable only by looking.
     */
    private CharSequence description(TopicCard card) {
        if (card.unreadCount <= 0) {
            return card.title;
        }
        return card.title + "\uff0c"
                + getContext().getString(R.string.home_topic_unread_cd, card.unreadCount);
    }

    /** Ready reads as a calm note, a missing key reads as the warning the prototype draws. */
    private void renderKeyHint(boolean keyConfigured) {
        int background = keyConfigured ? R.color.tl_green_soft : R.color.tl_amber_soft;
        int foreground = keyConfigured ? R.color.tl_green_deep : R.color.tl_amber;
        keyPill.setBackgroundTintList(ColorStateList.valueOf(getContext().getColor(background)));
        keyIcon.setImageTintList(ColorStateList.valueOf(getContext().getColor(foreground)));
        keyText.setText(keyConfigured ? R.string.home_key_ready : R.string.home_key_missing);
        keyText.setTextColor(getContext().getColor(foreground));
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
