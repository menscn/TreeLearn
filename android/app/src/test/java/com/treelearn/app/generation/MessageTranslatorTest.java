package com.treelearn.app.generation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.wiring.PromptLimits;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.data.secret.SecretStoreError;
import com.treelearn.data.secret.SecretStoreException;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

/**
 * T-090: the message-level translation route. The judgement under test is the 089 shape applied to
 * the translation role: a routed call rides the translation assignment exactly (its provider's base
 * URL, its model's wire id, the resolved credential), an unresolvable assignment or an unreadable
 * key keeps the legacy chat path (the task file's 回落聊天模型), and the terminal outcome - never
 * the fallback - reports a failed stream, because a retry is the learner's second tap, not a hidden
 * second spend.
 */
public final class MessageTranslatorTest {

    private static final String PROVIDER = "provider-1";
    private static final String BASE_URL = "https://api.example.com/v1";
    private static final String WIRE_ID = "test-translation-model";
    private static final String SOURCE = "The attention mechanism scales by root d.";
    private static final String TRANSLATION = "注意力机制按根号 d 缩放。";

    /** The verdict receiver, so a test can read what the learner would get. */
    private static final class RecordingCallback implements MessageTranslator.Callback {

        Boolean ok;
        String text = "";

        @Override
        public void onFinished(boolean ok, String translation) {
            this.ok = ok;
            this.text = translation;
        }
    }

    /** Records the open parameters and replays the scripted outcome through the sink. */
    private static final class RecordingStreams implements MessageTranslator.EndpointStreams {

        String scriptedTranslation = TRANSLATION;
        boolean failOnOpen;
        String apiKey;
        ProviderEndpoint endpoint;
        DeepSeekRequest request;
        GenerationSink sink;
        int opens;

        @Override
        public AiGateway.Call open(String apiKey, ProviderEndpoint endpoint,
                DeepSeekRequest request, GenerationSink sink) {
            this.apiKey = apiKey;
            this.endpoint = endpoint;
            this.request = request;
            this.sink = sink;
            opens++;
            if (failOnOpen) {
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.FAILED,
                        AiGatewayError.forHttpStatus(500), 500, 0, 0));
            } else {
                sink.onAccepted(200);
                sink.onDelta("", scriptedTranslation);
                sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.COMPLETED, null, 200, 0,
                        scriptedTranslation.length()));
            }
            return new AiGateway.Call() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    /** The legacy chat path, recorded so a test can prove the route taken. */
    private static final class RecordingFallback implements GenerationGateway {

        GenerationModel model;
        DeepSeekRequest request;
        GenerationSink sink;
        int opens;

        @Override
        public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
            this.model = model;
            this.request = request;
            this.sink = sink;
            opens++;
            sink.onAccepted(200);
            sink.onDelta("", "旧路径译文");
            sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.COMPLETED, null, 200, 0, 5));
            return null;
        }
    }

    private static QuickModelGateway.Route routed() {
        return new QuickModelGateway.Route(PROVIDER, BASE_URL, WIRE_ID);
    }

    private static MessageTranslator translator(QuickModelGateway.Route route,
            RecordingStreams streams, RecordingFallback fallback) {
        return new MessageTranslator(
                () -> route,
                providerId -> providerId.equals(PROVIDER)
                        ? "secret".toCharArray()
                        : "other".toCharArray(),
                streams, fallback, PromptLimits.budget());
    }

    private static RecordingCallback run(MessageTranslator translator, GenerationModel fallbackModel) {
        RecordingCallback received = new RecordingCallback();
        translator.translate(fallbackModel, SOURCE, received);
        return received;
    }

    @Test
    public void routedCallRidesTheTranslationAssignmentExactly() {
        RecordingStreams streams = new RecordingStreams();
        RecordingFallback fallback = new RecordingFallback();
        RecordingCallback outcome = run(translator(routed(), streams, fallback), GenerationModel.FLASH);

        assertEquals(1, streams.opens);
        assertEquals(0, fallback.opens);
        assertEquals("secret", streams.apiKey);
        assertEquals(BASE_URL, streams.endpoint.baseUrl());
        assertEquals(WIRE_ID, streams.endpoint.modelWireId());
        assertTrue(outcome.ok);
        assertEquals(TRANSLATION, outcome.text);
    }

    @Test
    public void routedRequestCarriesTheTranslationPurposeWithoutThinking() {
        RecordingStreams streams = new RecordingStreams();
        RecordingFallback fallback = new RecordingFallback();
        run(translator(routed(), streams, fallback), GenerationModel.FLASH);

        DeepSeekRequest request = streams.request;
        assertEquals(PromptPurpose.MESSAGE_TRANSLATION, request.purpose);
        assertFalse(request.thinkingEnabled);
        assertEquals(2, request.messages.size());
        assertEquals(DeepSeekMessage.Role.SYSTEM, request.messages.get(0).role);
        assertEquals(DeepSeekMessage.Role.USER, request.messages.get(1).role);
        assertEquals(SOURCE, request.messages.get(1).content);
    }

    @Test
    public void unassignedTranslationFallsBackToTheChatPath() {
        RecordingStreams streams = new RecordingStreams();
        RecordingFallback fallback = new RecordingFallback();
        RecordingCallback outcome =
                run(translator(null, streams, fallback), GenerationModel.FLASH);

        assertEquals(0, streams.opens);
        assertEquals(1, fallback.opens);
        assertEquals(GenerationModel.FLASH, fallback.model);
        assertEquals(PromptPurpose.MESSAGE_TRANSLATION, fallback.request.purpose);
        assertTrue(outcome.ok);
        assertEquals("旧路径译文", outcome.text);
    }

    @Test
    public void unreadableKeyFallsBackToTheChatPath() {
        RecordingStreams streams = new RecordingStreams();
        RecordingFallback fallback = new RecordingFallback();
        MessageTranslator unreadable = new MessageTranslator(
                () -> routed(),
                providerId -> {
                    throw new SecretStoreException(SecretStoreError.KEY_ENTRY_MISSING,
                            "the envelope is gone");
                },
                streams, fallback, PromptLimits.budget());
        RecordingCallback outcome = run(unreadable, GenerationModel.FLASH);

        assertEquals(0, streams.opens);
        assertEquals(1, fallback.opens);
        assertTrue(outcome.ok);
    }

    @Test
    public void failedStreamReportsFailureWithoutRetryingAnywhere() {
        RecordingStreams streams = new RecordingStreams();
        streams.failOnOpen = true;
        RecordingFallback fallback = new RecordingFallback();
        RecordingCallback outcome =
                run(translator(routed(), streams, fallback), GenerationModel.FLASH);

        assertEquals(1, streams.opens);
        assertEquals(0, fallback.opens);
        assertFalse(outcome.ok);
        assertTrue(outcome.text.isEmpty());
    }

    @Test
    public void blankSourceIsRefusedBeforeAnyCall() {
        RecordingStreams streams = new RecordingStreams();
        RecordingFallback fallback = new RecordingFallback();
        RecordingCallback received = new RecordingCallback();
        translator(routed(), streams, fallback).translate(GenerationModel.FLASH, "   ", received);

        assertEquals(0, streams.opens);
        assertEquals(0, fallback.opens);
        assertFalse(received.ok);
    }
}
