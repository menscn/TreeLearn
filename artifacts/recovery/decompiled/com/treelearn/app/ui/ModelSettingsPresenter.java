/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.UiThread;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public final class ModelSettingsPresenter {
    private final Sink sink;
    private final UiThread ui;
    private final Executor background;
    private final Store store;
    private State state;

    public static Assignment resolve(String providerId, String modelId, List<Option> options, Option builtinDefault, Option earliest) {
        Option kept = ModelSettingsPresenter.find(options, providerId, modelId);
        if (kept != null) {
            return new Assignment(kept.providerId, kept.modelId, Fallback.NONE);
        }
        if (builtinDefault != null) {
            return new Assignment(builtinDefault.providerId, builtinDefault.modelId, Fallback.BUILTIN);
        }
        if (earliest != null) {
            return new Assignment(earliest.providerId, earliest.modelId, Fallback.EARLIEST);
        }
        return Assignment.EMPTY;
    }

    private static Option find(List<Option> options, String providerId, String modelId) {
        if (options != null && providerId != null && modelId != null) {
            for (Option option : options) {
                if (!option.providerId.equals(providerId) || !option.modelId.equals(modelId)) continue;
                return option;
            }
        }
        return null;
    }

    public ModelSettingsPresenter(Sink sink, UiThread ui, Executor background, Store store) {
        if (sink == null || ui == null || background == null || store == null) {
            throw new IllegalArgumentException("the model settings presenter needs all four parts");
        }
        this.sink = sink;
        this.ui = ui;
        this.background = background;
        this.store = store;
    }

    public State state() {
        return this.state;
    }

    public void start() {
        this.background.execute(this::reload);
    }

    public void onModelSelected(Role role, Option option) {
        if (option == null) {
            return;
        }
        this.background.execute(() -> {
            this.store.save(role, option.providerId, option.modelId);
            this.reload();
        });
    }

    public void onBudgetSelected(Budget budget) {
        this.background.execute(() -> {
            this.store.saveBudget(budget);
            this.reload();
        });
    }

    public void onSuggestionsToggled(boolean enabled) {
        this.background.execute(() -> {
            this.store.saveSuggestions(enabled);
            this.reload();
        });
    }

    private void reload() {
        boolean fallen;
        ArrayList<Option> options = new ArrayList<Option>(this.store.options());
        Assignment chat = this.store.read(Role.CHAT);
        Assignment fast = this.store.read(Role.FAST);
        Assignment translation = this.store.read(Role.TRANSLATION);
        Budget budget = this.store.readBudget();
        boolean suggestions = this.store.readSuggestions();
        boolean bl = fallen = chat.fallback != Fallback.NONE || fast.fallback != Fallback.NONE || translation.fallback != Fallback.NONE;
        Notice notice = options.isEmpty() ? Notice.NO_MODELS : (fallen ? Notice.FALLEN_BACK : Notice.NONE);
        this.ui.post(() -> this.render(new State(options, chat, fast, translation, budget, suggestions, notice)));
    }

    private void render(State next) {
        this.state = next;
        this.sink.render(next);
    }

    public static final class Option {
        public final String providerId;
        public final String providerName;
        public final String modelId;

        public Option(String providerId, String providerName, String modelId) {
            this.providerId = providerId;
            this.providerName = providerName;
            this.modelId = modelId;
        }
    }

    public static final class Assignment {
        public static final Assignment EMPTY = new Assignment(null, null, Fallback.EMPTY);
        public final String providerId;
        public final String modelId;
        public final String label;
        public final Fallback fallback;

        public Assignment(String providerId, String modelId, Fallback fallback) {
            this.providerId = providerId;
            this.modelId = modelId;
            this.label = modelId == null ? "" : modelId;
            this.fallback = fallback;
        }
    }

    public static enum Fallback {
        NONE,
        BUILTIN,
        EARLIEST,
        EMPTY;

    }

    public static interface Sink {
        public void render(State var1);
    }

    public static interface Store {
        public List<Option> options();

        public Assignment read(Role var1);

        public void save(Role var1, String var2, String var3);

        public Budget readBudget();

        public void saveBudget(Budget var1);

        public boolean readSuggestions();

        public void saveSuggestions(boolean var1);
    }

    public static final class State {
        public final List<Option> options;
        public final Assignment chat;
        public final Assignment fast;
        public final Assignment translation;
        public final Budget budget;
        public final boolean suggestions;
        public final Notice notice;

        State(List<Option> options, Assignment chat, Assignment fast, Assignment translation, Budget budget, boolean suggestions, Notice notice) {
            this.options = options;
            this.chat = chat;
            this.fast = fast;
            this.translation = translation;
            this.budget = budget;
            this.suggestions = suggestions;
            this.notice = notice;
        }
    }

    public static enum Role {
        CHAT,
        FAST,
        TRANSLATION;

    }

    public static enum Budget {
        OFF,
        AUTO,
        LOW,
        MID,
        HIGH;

    }

    public static enum Notice {
        NONE,
        FALLEN_BACK,
        NO_MODELS;

    }
}

