package com.treelearn.app.ui;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.db.dao.FirstTopicCreation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the home screen draws, as one immutable value (PRD 6.2.16, "a single home page").
 *
 * <p>The screen holds no state of its own: the composer's draft, the four layout phases, the key
 * hint, the model chip and the failure notice all live here, so a JVM test can assert what the
 * learner would see without inflating a view.
 *
 * <p><strong>The notice is a category, not a sentence.</strong> {@link Notice} is produced from the
 * classifications that already exist - {@link AiGatewayError.Kind} for anything the provider or the
 * network answered, {@link FirstTopicCreation.Refusal} for a local write the store refused - and the
 * screen is the only place a Chinese string is chosen. That keeps one verdict per input instead of
 * two entry points disagreeing about the same refusal.
 */
public final class HomeViewState {

    /** Which of the four home layouts is on screen. */
    public enum Phase {
        /** The store is being read; the composer stays usable. */
        LOADING,
        /** No topic yet: hero copy, starter recommendations, the guide line where the list goes. */
        EMPTY,
        /** The store could not be read: the list is replaced by the failure, the draft is kept. */
        ERROR,
        /** Topics exist: the list is drawn under the strip. */
        READY
    }

    /** A failure the learner must be told about. */
    public enum Notice {
        /** No key stored, so nothing may be sent (PRD 6.2.13 exception 1). */
        KEY_MISSING,
        /** The provider refused the credential. */
        KEY_REJECTED,
        /** The account cannot pay for this call. */
        BALANCE_UNAVAILABLE,
        /** The provider rejected the request itself. */
        REQUEST_REJECTED,
        /** History was compressed before this request could be built (history exceeded budget). */
        HISTORY_COMPRESSED,
        /** The provider throttled the call. */
        RATE_LIMITED,
        /** The provider failed to serve the call. */
        PROVIDER_UNAVAILABLE,
        /** The connection failed, timed out or ended before the stream completed. */
        NETWORK_INTERRUPTED,
        /** The local store refused a write or could not be read; the attempt was not spent remotely. */
        STORAGE,
        /**
         * The branch's reading state could not be written: the stored position when leaving, or the
         * unread sequence after the newest answer entered the viewport.
         *
         * <p>{@code ReadingPositionDao} and {@code BranchDao} refuse rather than report a write that
         * did not land, and that refusal is told here instead of being thrown at the background
         * thread. Nothing already on screen is lost; only what the next visit restores is.
         */
        READING_POSITION,
        /** The search feature needs a credential that is not configured. */
        SEARCH_KEY_MISSING,
        /** A search call failed to complete. */
        SEARCH_FAILED,
        /** A search returned no results. */
        SEARCH_EMPTY,
        /**
         * The attached document's parsed text exceeds the injection ceiling, so the send was
         * refused before anything was written (T-097; SPEC-082 错误语义: intercept and say so,
         * never truncate silently).
         */
        ATTACHMENT_TOO_LARGE,
        /**
         * The chat-role model assignment fell back to the builtin default (or earliest) because the
         * chosen model is gone or unavailable (T-103). Told once per conversation entry, never on
         * every render.
         */
        CHAT_MODEL_FALLBACK,
        /**
         * The chat role has no usable model at all (T-103): the chip shows a placeholder and the
         * notice guides the learner to the model settings instead of failing a send.
         */
        NO_MODELS;

        /** The single mapping from the provider/network classification to what the home shows. */
        public static Notice from(AiGatewayError.Kind kind) {
            switch (kind) {
                case KEY_MISSING:
                    return KEY_MISSING;
                case AUTH_INVALID:
                    return KEY_REJECTED;
                case BALANCE_UNAVAILABLE:
                    return BALANCE_UNAVAILABLE;
                case REQUEST_INVALID:
                    return REQUEST_REJECTED;
                case RATE_LIMITED:
                    return RATE_LIMITED;
                case PROVIDER_UNAVAILABLE:
                    return PROVIDER_UNAVAILABLE;
                case NETWORK_INTERRUPTED:
                    return NETWORK_INTERRUPTED;
                case PARSE_FAILED:
                    return PROVIDER_UNAVAILABLE;
                default:
                    throw new IllegalArgumentException("unmapped gateway kind: " + kind);
            }
        }

        /** The single mapping from a local first-topic refusal to what the home shows. */
        public static Notice from(FirstTopicCreation.Refusal refusal) {
            switch (refusal) {
                case TOPIC_ID_ALREADY_EXISTS:
                case SOURCE_MESSAGE_NOT_IN_TOPIC:
                case STORAGE_REFUSED:
                    return STORAGE;
                default:
                    throw new IllegalArgumentException("unmapped first-topic refusal: " + refusal);
            }
        }
    }

    public final Phase phase;
    /** The root question as typed. Survives every failure, and is cleared only after acceptance. */
    public final String draft;
    public final boolean keyConfigured;
    public final GenerationModel model;
    public final boolean thinkingEnabled;
    public final List<TopicCard> topics;
    public final List<Recommendation> recommendations;
    public final boolean submitting;
    /** Null when there is nothing to report. */
    public final Notice notice;
    /** The learning-heat strip to draw, or a hidden placeholder when stats are unavailable. */
    public final HeatStrip heatStrip;

    private HomeViewState(Phase phase, String draft, boolean keyConfigured, GenerationModel model,
            boolean thinkingEnabled, List<TopicCard> topics, List<Recommendation> recommendations,
            boolean submitting, Notice notice, HeatStrip heatStrip) {
        this.phase = phase;
        this.draft = draft == null ? "" : draft;
        this.keyConfigured = keyConfigured;
        this.model = model;
        this.thinkingEnabled = thinkingEnabled;
        this.topics = Collections.unmodifiableList(new ArrayList<TopicCard>(topics));
        this.recommendations = Collections.unmodifiableList(new ArrayList<Recommendation>(
                recommendations));
        this.submitting = submitting;
        this.notice = notice;
        this.heatStrip = heatStrip;
    }

    /** The state before the first read: loading, nothing known about the store, composer ready. */
    public static HomeViewState initial(GenerationModel model, boolean thinkingEnabled,
            boolean keyConfigured) {
        return new HomeViewState(Phase.LOADING, "", keyConfigured, model, thinkingEnabled,
                Collections.<TopicCard>emptyList(), Collections.<Recommendation>emptyList(), false,
                null, HeatStrip.hidden());
    }

    /** True when the send control may be pressed: something to send, and no attempt in flight. */
    public boolean canSubmit() {
        return !draft.trim().isEmpty() && !submitting;
    }

    /**
     * The continue-learning row (055): the topic the learner used last, or null when there is no
     * history. The list was ordered newest first, so the first card is the one they left off on -
     * and a null is the state's way of saying the row must not appear at all, not that it appears
     * empty.
     */
    public TopicCard continueTopic() {
        return topics.isEmpty() ? null : topics.get(0);
    }

    public HomeViewState withPhase(Phase value) {
        return new HomeViewState(value, draft, keyConfigured, model, thinkingEnabled, topics,
                recommendations, submitting, notice, this.heatStrip);
    }

    public HomeViewState withDraft(String value) {
        return new HomeViewState(phase, value, keyConfigured, model, thinkingEnabled, topics,
                recommendations, submitting, notice, this.heatStrip);
    }

    public HomeViewState withKeyConfigured(boolean value) {
        return new HomeViewState(phase, draft, value, model, thinkingEnabled, topics,
                recommendations, submitting, notice, this.heatStrip);
    }

    public HomeViewState withModel(GenerationModel value) {
        return new HomeViewState(phase, draft, keyConfigured, value, thinkingEnabled, topics,
                recommendations, submitting, notice, this.heatStrip);
    }

    public HomeViewState withThinkingEnabled(boolean value) {
        return new HomeViewState(phase, draft, keyConfigured, model, value, topics,
                recommendations, submitting, notice, this.heatStrip);
    }

    public HomeViewState withTopics(List<TopicCard> value) {
        return new HomeViewState(phase, draft, keyConfigured, model, thinkingEnabled, value,
                recommendations, submitting, notice, this.heatStrip);
    }

    public HomeViewState withRecommendations(List<Recommendation> value) {
        return new HomeViewState(phase, draft, keyConfigured, model, thinkingEnabled, topics, value,
                submitting, notice, this.heatStrip);
    }

    public HomeViewState withSubmitting(boolean value) {
        return new HomeViewState(phase, draft, keyConfigured, model, thinkingEnabled, topics,
                recommendations, value, notice, this.heatStrip);
    }

    public HomeViewState withNotice(Notice value) {
        return new HomeViewState(phase, draft, keyConfigured, model, thinkingEnabled, topics,
                recommendations, submitting, value, this.heatStrip);
    }

    public HomeViewState withHeatStrip(HeatStrip value) {
        return new HomeViewState(phase, draft, keyConfigured, model, thinkingEnabled, topics,
                recommendations, submitting, notice, value);
    }

    /**
     * The learning-heat strip the home screen draws.
     *
     * <p>One value carries both the visibility decision and the payload, so the screen never asks
     * the store twice and never draws a half-populated strip.
     */
    public static final class HeatStrip {

        public enum Status {
            /** Nothing to show; the card is removed from the layout. */
            HIDDEN,
            /** Stats exist but are empty; the "no record yet" line is shown. */
            EMPTY,
            /** Stats exist and have activity; the heatmap and streak are shown. */
            VISIBLE
        }

        public final Status status;
        public final float[] intensities;
        public final int streakDays;

        private HeatStrip(Status status, float[] intensities, int streakDays) {
            this.status = status;
            this.intensities = intensities;
            this.streakDays = streakDays;
        }

        public static HeatStrip hidden() {
            return new HeatStrip(Status.HIDDEN, new float[0], 0);
        }

        public static HeatStrip empty() {
            return new HeatStrip(Status.EMPTY, new float[0], 0);
        }

        public static HeatStrip visible(float[] intensities, int streakDays) {
            return new HeatStrip(Status.VISIBLE, intensities, streakDays);
        }
    }
}
