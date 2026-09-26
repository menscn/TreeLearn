/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.HomeActions;
import com.treelearn.app.ui.HomeData;
import com.treelearn.app.ui.HomeSink;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.RootQuestionSubmitter;
import com.treelearn.app.ui.SettingsStore;
import com.treelearn.app.ui.SubmitResult;
import com.treelearn.app.ui.TopicCard;
import com.treelearn.app.ui.UiThread;
import com.treelearn.core.domain.GenerationModel;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

public final class HomePresenter
implements HomeActions {
    private final HomeSink sink;
    private final UiThread ui;
    private final Executor background;
    private final KeyVault key;
    private final HomeData data;
    private final RootQuestionSubmitter submitter;
    private final SettingsStore settings;
    private HomeViewState state;

    public HomePresenter(HomeSink sink, UiThread ui, Executor background, KeyVault key, HomeData data, RootQuestionSubmitter submitter, SettingsStore settings) {
        this.sink = HomePresenter.require(sink, "sink");
        this.ui = HomePresenter.require(ui, "ui");
        this.background = HomePresenter.require(background, "background");
        this.key = HomePresenter.require(key, "key");
        this.data = HomePresenter.require(data, "data");
        this.submitter = HomePresenter.require(submitter, "submitter");
        this.settings = HomePresenter.require(settings, "settings");
        this.state = HomeViewState.initial(settings.model(), settings.thinkingEnabled(), key.isConfigured());
    }

    public HomeViewState state() {
        return this.state;
    }

    public void start() {
        this.render(this.refreshed().withPhase(HomeViewState.Phase.LOADING));
        this.background.execute(this::load);
    }

    public void onResumed() {
        this.render(this.refreshed());
        this.background.execute(this::load);
    }

    private HomeViewState refreshed() {
        return this.state.withKeyConfigured(this.key.isConfigured()).withModel(this.settings.model()).withThinkingEnabled(this.settings.thinkingEnabled());
    }

    @Override
    public void onDraftChanged(String text) {
        HomeViewState next = this.state.withDraft(text);
        this.render(this.state.notice == null ? next : next.withNotice(null));
    }

    @Override
    public void onRecommendationTapped(Recommendation recommendation) {
        this.render(this.state.withDraft(HomePresenter.require(recommendation, (String)"recommendation").question));
        this.onSubmit();
    }

    @Override
    public void onTopicTapped(TopicCard card) {
        this.sink.openBranch(HomePresenter.require(card, (String)"card").resumeBranchId);
    }

    @Override
    public void onRenameTopic(TopicCard card, String title) {
        String next;
        TopicCard target = HomePresenter.require(card, "card");
        String string2 = next = title == null ? "" : title.trim();
        if (next.isEmpty()) {
            this.render(this.state.withNotice(HomeViewState.Notice.STORAGE));
            return;
        }
        this.background.execute(() -> {
            try {
                this.data.renameTopic(target.topicId, next, System.currentTimeMillis());
                this.load();
            }
            catch (RuntimeException failed) {
                this.post(current -> current.withNotice(HomeViewState.Notice.STORAGE));
            }
        });
    }

    @Override
    public void onDeleteTopic(TopicCard card) {
        TopicCard target = HomePresenter.require(card, "card");
        this.background.execute(() -> {
            try {
                this.data.deleteTopic(target.topicId);
                this.load();
            }
            catch (RuntimeException failed) {
                this.post(current -> current.withNotice(HomeViewState.Notice.STORAGE));
            }
        });
    }

    @Override
    public void onArchivedPressed() {
        List<TopicCard> topics = this.state.topics;
        if (topics.isEmpty()) {
            return;
        }
        this.sink.openArchived(topics.get((int)0).resumeBranchId);
    }

    @Override
    public void onModelSelected(GenerationModel model) {
        this.settings.setModel(HomePresenter.require(model, "model"));
        this.render(this.state.withModel(model));
    }

    @Override
    public void onThinkingToggled(boolean enabled) {
        this.settings.setThinkingEnabled(enabled);
        this.render(this.state.withThinkingEnabled(enabled));
    }

    @Override
    public void onSubmit() {
        if (this.state.submitting) {
            return;
        }
        String question = this.state.draft.trim();
        if (question.isEmpty()) {
            return;
        }
        if (!this.state.keyConfigured) {
            this.render(this.state.withNotice(HomeViewState.Notice.KEY_MISSING));
            this.sink.openSettings();
            return;
        }
        GenerationModel model = this.state.model;
        boolean thinking = this.state.thinkingEnabled;
        this.render(this.state.withSubmitting(true).withNotice(null));
        this.background.execute(() -> this.submit(question, model, thinking));
    }

    @Override
    public void onSettingsPressed() {
        this.sink.openSettings();
    }

    @Override
    public void onSearchPressed() {
        this.sink.openSearch();
    }

    private void submit(String question, GenerationModel model, boolean thinking) {
        SubmitResult result;
        try {
            result = this.submitter.submit(question, model, thinking);
        }
        catch (RuntimeException refusedByStore) {
            this.post(current -> current.withSubmitting(false).withNotice(HomeViewState.Notice.STORAGE));
            return;
        }
        if (!result.isAccepted()) {
            HomeViewState.Notice notice = result.notice();
            this.post(current -> current.withSubmitting(false).withNotice(notice));
            return;
        }
        String rootBranchId = result.rootBranchId();
        this.post(current -> current.withSubmitting(false).withNotice(null).withDraft(""));
        this.ui.post(() -> this.sink.openBranch(rootBranchId));
    }

    private void load() {
        List<Object> loadedStrip;
        List<TopicCard> topics;
        try {
            topics = this.data.recentTopics();
        }
        catch (RuntimeException storeUnreadable) {
            this.post(current -> current.withPhase(HomeViewState.Phase.ERROR).withNotice(HomeViewState.Notice.STORAGE));
            return;
        }
        try {
            loadedStrip = this.data.recommendations((boolean)(!topics.isEmpty() ? true : false)).items;
        }
        catch (RuntimeException recommendationUnavailable) {
            loadedStrip = Collections.emptyList();
        }
        List recommendations = loadedStrip;
        boolean hasTopics = !topics.isEmpty();
        this.post(current -> current.withTopics(topics).withRecommendations(recommendations).withPhase(hasTopics ? HomeViewState.Phase.READY : HomeViewState.Phase.EMPTY).withNotice(null));
    }

    private void post(UnaryOperator<HomeViewState> transition) {
        this.ui.post(() -> this.render((HomeViewState)transition.apply(this.state)));
    }

    private void render(HomeViewState next) {
        this.state = next;
        this.sink.render(next);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}

