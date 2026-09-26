/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.search.web;

import com.treelearn.app.search.web.SearchCredentials;
import com.treelearn.app.search.web.SearchInjection;
import com.treelearn.app.search.web.WebSearchGateway;
import com.treelearn.app.ui.SearchSettingsPresenter;
import java.util.Arrays;

public final class ChatSearchStep {
    private final SearchSettingsPresenter.Store settings;
    private final SearchCredentials credentials;
    private final WebSearchGateway gateway;

    public ChatSearchStep(SearchSettingsPresenter.Store settings, SearchCredentials credentials, WebSearchGateway gateway) {
        if (settings == null || credentials == null || gateway == null) {
            throw new IllegalArgumentException("the search step needs all three parts");
        }
        this.settings = settings;
        this.credentials = credentials;
        this.gateway = gateway;
    }

    public boolean enabled() {
        return this.settings.searchEnabled();
    }

    public boolean hasCredential() {
        return this.settings.hasKey(this.settings.engine());
    }

    public String engineLabel() {
        switch (this.settings.engine()) {
            case TAVILY: {
                return "Tavily";
            }
            case BOCHA: {
                return "\u535a\u67e5";
            }
        }
        return "\u667a\u8c31";
    }

    public int resultCount() {
        switch (this.settings.tier()) {
            case THREE: {
                return 3;
            }
            case FIVE: {
                return 5;
            }
        }
        return 10;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public Result run(String query) {
        if (!this.settings.searchEnabled()) {
            return Result.OFF;
        }
        SearchSettingsPresenter.Engine engine = this.settings.engine();
        if (!this.settings.hasKey(engine)) {
            return new Result(Status.NOKEY, "");
        }
        char[] key = this.credentials.keyFor(engine);
        if (key == null) {
            return new Result(Status.NOKEY, "");
        }
        try {
            WebSearchGateway.Outcome outcome = this.gateway.search(engine, key, query, this.resultCount());
            if (outcome.status() == WebSearchGateway.Status.OK) {
                Result result = new Result(Status.OK, SearchInjection.block(query, outcome.items));
                return result;
            }
            Result result = new Result(outcome.status() == WebSearchGateway.Status.EMPTY ? Status.EMPTY : Status.FAILED, "");
            return result;
        }
        catch (RuntimeException failed) {
            Result result = new Result(Status.FAILED, "");
            return result;
        }
        finally {
            Arrays.fill(key, '\u0000');
        }
    }

    public static final class Result {
        private static final Result OFF = new Result(Status.OFF, "");
        private final Status status;
        private final String block;

        private Result(Status status, String block) {
            this.status = status;
            this.block = block;
        }

        public static Result off() {
            return OFF;
        }

        public Status status() {
            return this.status;
        }

        public String block() {
            return this.block;
        }
    }

    public static enum Status {
        OFF,
        NOKEY,
        OK,
        FAILED,
        EMPTY;

    }
}

