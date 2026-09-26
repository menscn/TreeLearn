/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.gateway.AiGatewayError$Kind
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.data.db.dao.FirstTopicCreation$Refusal
 */
package com.treelearn.app.ui;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.app.ui.Recommendation;
import com.treelearn.app.ui.TopicCard;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.db.dao.FirstTopicCreation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HomeViewState {
    public final Phase phase;
    public final String draft;
    public final boolean keyConfigured;
    public final GenerationModel model;
    public final boolean thinkingEnabled;
    public final List<TopicCard> topics;
    public final List<Recommendation> recommendations;
    public final boolean submitting;
    public final Notice notice;

    private HomeViewState(Phase phase, String draft, boolean keyConfigured, GenerationModel model, boolean thinkingEnabled, List<TopicCard> topics, List<Recommendation> recommendations, boolean submitting, Notice notice) {
        this.phase = phase;
        this.draft = draft == null ? "" : draft;
        this.keyConfigured = keyConfigured;
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
        this.topics = Collections.unmodifiableList(new ArrayList<TopicCard>(topics));
        this.recommendations = Collections.unmodifiableList(new ArrayList<Recommendation>(recommendations));
        this.submitting = submitting;
        this.notice = notice;
    }

    public static HomeViewState initial(GenerationModel model, boolean thinkingEnabled, boolean keyConfigured) {
        return new HomeViewState(Phase.LOADING, "", keyConfigured, model, thinkingEnabled, Collections.emptyList(), Collections.emptyList(), false, null);
    }

    public boolean canSubmit() {
        return !this.draft.trim().isEmpty() && !this.submitting;
    }

    public HomeViewState withPhase(Phase value) {
        return new HomeViewState(value, this.draft, this.keyConfigured, this.model, this.thinkingEnabled, this.topics, this.recommendations, this.submitting, this.notice);
    }

    public HomeViewState withDraft(String value) {
        return new HomeViewState(this.phase, value, this.keyConfigured, this.model, this.thinkingEnabled, this.topics, this.recommendations, this.submitting, this.notice);
    }

    public HomeViewState withKeyConfigured(boolean value) {
        return new HomeViewState(this.phase, this.draft, value, this.model, this.thinkingEnabled, this.topics, this.recommendations, this.submitting, this.notice);
    }

    public HomeViewState withModel(GenerationModel value) {
        return new HomeViewState(this.phase, this.draft, this.keyConfigured, value, this.thinkingEnabled, this.topics, this.recommendations, this.submitting, this.notice);
    }

    public HomeViewState withThinkingEnabled(boolean value) {
        return new HomeViewState(this.phase, this.draft, this.keyConfigured, this.model, value, this.topics, this.recommendations, this.submitting, this.notice);
    }

    public HomeViewState withTopics(List<TopicCard> value) {
        return new HomeViewState(this.phase, this.draft, this.keyConfigured, this.model, this.thinkingEnabled, value, this.recommendations, this.submitting, this.notice);
    }

    public HomeViewState withRecommendations(List<Recommendation> value) {
        return new HomeViewState(this.phase, this.draft, this.keyConfigured, this.model, this.thinkingEnabled, this.topics, value, this.submitting, this.notice);
    }

    public HomeViewState withSubmitting(boolean value) {
        return new HomeViewState(this.phase, this.draft, this.keyConfigured, this.model, this.thinkingEnabled, this.topics, this.recommendations, value, this.notice);
    }

    public HomeViewState withNotice(Notice value) {
        return new HomeViewState(this.phase, this.draft, this.keyConfigured, this.model, this.thinkingEnabled, this.topics, this.recommendations, this.submitting, value);
    }

    public static enum Phase {
        LOADING,
        EMPTY,
        ERROR,
        READY;

    }

    public static enum Notice {
        KEY_MISSING,
        KEY_REJECTED,
        BALANCE_UNAVAILABLE,
        REQUEST_REJECTED,
        RATE_LIMITED,
        PROVIDER_UNAVAILABLE,
        NETWORK_INTERRUPTED,
        STORAGE,
        READING_POSITION;


        public static Notice from(AiGatewayError.Kind kind) {
            switch (kind) {
                case KEY_MISSING: {
                    return KEY_MISSING;
                }
                case AUTH_INVALID: {
                    return KEY_REJECTED;
                }
                case BALANCE_UNAVAILABLE: {
                    return BALANCE_UNAVAILABLE;
                }
                case REQUEST_INVALID: {
                    return REQUEST_REJECTED;
                }
                case RATE_LIMITED: {
                    return RATE_LIMITED;
                }
                case PROVIDER_UNAVAILABLE: {
                    return PROVIDER_UNAVAILABLE;
                }
                case NETWORK_INTERRUPTED: {
                    return NETWORK_INTERRUPTED;
                }
                case PARSE_FAILED: {
                    return PROVIDER_UNAVAILABLE;
                }
            }
            throw new IllegalArgumentException("unmapped gateway kind: " + kind);
        }

        public static Notice from(FirstTopicCreation.Refusal refusal) {
            switch (refusal) {
                case TOPIC_ID_ALREADY_EXISTS: 
                case SOURCE_MESSAGE_NOT_IN_TOPIC: 
                case STORAGE_REFUSED: {
                    return STORAGE;
                }
            }
            throw new IllegalArgumentException("unmapped first-topic refusal: " + refusal);
        }
    }
}

