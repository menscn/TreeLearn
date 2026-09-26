package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * The home screen's decisions, with no Android type in sight.
 *
 * <p>Four rules live here, and each one is a PRD requirement rather than a rendering choice:
 *
 * <ul>
 *   <li><strong>The draft survives every failure.</strong> It is cleared in exactly one place - the
 *       accepted arm of {@link #onSubmit()} - so a refused submission, a provider failure or a
 *       missing key leaves the learner's question on screen (PRD 6.2.14 exceptions 1-3).
 *   <li><strong>A recommendation tap is a submission, not a second path.</strong> It writes the
 *       recommendation into the draft and calls the same {@link #onSubmit()}, which is what makes the
 *       key guard and the atomic first-topic rule apply to it unchanged (PRD 6.2.17 point 4).
 *   <li><strong>A failed recommendation never blocks the page.</strong> The strip may disappear, but
 *       the composer and the topic list stay usable (PRD 6.2.17 exception 3).
 *   <li><strong>A missing key opens settings instead of sending.</strong> Nothing is written and the
 *       question stays put (PRD 6.2.13 exception 1).
 * </ul>
 *
 * <p>Every store read happens on the caller-supplied background executor; every state is handed to
 * the sink on the UI thread. Transitions are applied to the <em>current</em> state when they arrive,
 * not to a copy captured before the read, so a draft typed while the list was loading is not
 * overwritten by the load result.
 */
public final class HomePresenter implements HomeActions {

    private final HomeSink sink;
    private final UiThread ui;
    private final Executor background;
    private final KeyVault key;
    private final HomeData data;
    private final RootQuestionSubmitter submitter;
    private final SettingsStore settings;

    private HomeViewState state;

    public HomePresenter(HomeSink sink, UiThread ui, Executor background, KeyVault key, HomeData data,
            RootQuestionSubmitter submitter, SettingsStore settings) {
        this.sink = require(sink, "sink");
        this.ui = require(ui, "ui");
        this.background = require(background, "background");
        this.key = require(key, "key");
        this.data = require(data, "data");
        this.submitter = require(submitter, "submitter");
        this.settings = require(settings, "settings");
        this.state = HomeViewState.initial(settings.model(), settings.thinkingEnabled(),
                key.isConfigured());
    }

    /** The last state handed to the sink; the tests read it, the screen does not. */
    public HomeViewState state() {
        return state;
    }

    /** First load: the list phase is shown while the store is read. */
    public void start() {
        render(refreshed().withPhase(HomeViewState.Phase.LOADING));
        background.execute(this::load);
    }

    /**
     * Coming back to the home screen: the key or the model may have changed in settings, and a topic
     * may have been created in the conversation. The current phase is kept so the list does not
     * flash, and the draft is untouched.
     */
    public void onResumed() {
        render(refreshed());
        background.execute(this::load);
    }

    /**
     * Re-reads the facts that live outside this screen. The vault is asked every time rather than
     * cached from construction: a key can be added in settings, and the composer's guard has to
     * follow it without the screen being rebuilt.
     */
    private HomeViewState refreshed() {
        return state.withKeyConfigured(key.isConfigured()).withModel(settings.model())
                .withThinkingEnabled(settings.thinkingEnabled());
    }

    @Override
    public void onDraftChanged(String text) {
        HomeViewState next = state.withDraft(text);
        render(state.notice == null ? next : next.withNotice(null));
    }

    /** A tapped chip becomes the draft first, so a blocked send still shows what was tapped. */
    @Override
    public void onRecommendationTapped(Recommendation recommendation) {
        render(state.withDraft(require(recommendation, "recommendation").question));
        onSubmit();
    }

    /** Resumes a topic at the branch the learner last opened (PRD 6.2.16 point 4). */
    @Override
    public void onTopicTapped(TopicCard card) {
        sink.openBranch(require(card, "card").resumeBranchId);
    }

    @Override
    public void onModelSelected(GenerationModel model) {
        settings.setModel(require(model, "model"));
        render(state.withModel(model));
    }

    @Override
    public void onThinkingToggled(boolean enabled) {
        settings.setThinkingEnabled(enabled);
        render(state.withThinkingEnabled(enabled));
    }

    /** The single send path: the composer's key, the chip's tap and a recommendation all land here. */
    @Override
    public void onSubmit() {
        if (state.submitting) {
            return;
        }
        String question = state.draft.trim();
        if (question.isEmpty()) {
            return;
        }
        if (!state.keyConfigured) {
            render(state.withNotice(HomeViewState.Notice.KEY_MISSING));
            sink.openSettings();
            return;
        }
        final GenerationModel model = state.model;
        final boolean thinking = state.thinkingEnabled;
        render(state.withSubmitting(true).withNotice(null));
        background.execute(() -> submit(question, model, thinking));
    }

    /**
     * The heat strip. Navigation only; no destination screen is wired in this build, so the tap is
     * accepted and currently does nothing - the strip is informational.
     */
    @Override
    public void onHeatStripTapped() {
    }

    private void submit(String question, GenerationModel model, boolean thinking) {
        final SubmitResult result;
        try {
            result = submitter.submit(question, model, thinking);
        } catch (RuntimeException refusedByStore) {
            post(current -> current.withSubmitting(false)
                    .withNotice(HomeViewState.Notice.STORAGE));
            return;
        }
        if (!result.isAccepted()) {
            final HomeViewState.Notice notice = result.notice();
            post(current -> current.withSubmitting(false).withNotice(notice));
            return;
        }
        final String rootBranchId = result.rootBranchId();
        post(current -> current.withSubmitting(false).withNotice(null).withDraft(""));
        ui.post(() -> sink.openBranch(rootBranchId));
    }

    private void load() {
        final List<TopicCard> topics;
        try {
            topics = data.recentTopics();
        } catch (RuntimeException storeUnreadable) {
            post(current -> current.withPhase(HomeViewState.Phase.ERROR)
                    .withNotice(HomeViewState.Notice.STORAGE));
            return;
        }
        List<Recommendation> loadedStrip;
        try {
            loadedStrip = data.recommendations(!topics.isEmpty()).items;
        } catch (RuntimeException recommendationUnavailable) {
            loadedStrip = Collections.emptyList();
        }
        final List<Recommendation> recommendations = loadedStrip;
        final boolean hasTopics = !topics.isEmpty();
        HomeViewState.HeatStrip heatStrip = loadHeatStrip();
        post(current -> current.withTopics(topics).withRecommendations(recommendations)
                .withPhase(hasTopics ? HomeViewState.Phase.READY : HomeViewState.Phase.EMPTY)
                .withNotice(null).withHeatStrip(heatStrip));
    }

    private HomeViewState.HeatStrip loadHeatStrip() {
        try {
            HomeStats stats = data.stats();
            return stats.empty ? HomeViewState.HeatStrip.empty()
                    : HomeViewState.HeatStrip.visible(stats.intensities, stats.streakDays);
        } catch (RuntimeException statsUnavailable) {
            return HomeViewState.HeatStrip.hidden();
        }
    }

    private void post(UnaryOperator<HomeViewState> transition) {
        ui.post(() -> render(transition.apply(state)));
    }

    private void render(HomeViewState next) {
        state = next;
        sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
