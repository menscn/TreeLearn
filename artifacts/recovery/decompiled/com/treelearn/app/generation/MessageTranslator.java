/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.gateway.AiGateway$Call
 *  com.treelearn.ai.gateway.GenerationSink
 *  com.treelearn.ai.gateway.ProviderEndpoint
 *  com.treelearn.ai.gateway.StreamOutcome
 *  com.treelearn.ai.gateway.StreamOutcome$Kind
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.ai.prompt.DeepSeekRequestAssembler
 *  com.treelearn.ai.prompt.PromptBudget
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.core.domain.PromptPurpose
 *  com.treelearn.data.secret.SecretStoreException
 */
package com.treelearn.app.generation;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.app.generation.QuickModelGateway;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.data.secret.SecretStoreException;
import java.util.Arrays;
import java.util.Collections;

public final class MessageTranslator {
    private final Routes routes;
    private final Keys keys;
    private final EndpointStreams streams;
    private final GenerationGateway fallback;
    private final PromptBudget budget;

    public MessageTranslator(Routes routes, Keys keys, EndpointStreams streams, GenerationGateway fallback, PromptBudget budget) {
        if (routes == null || keys == null || streams == null || fallback == null || budget == null) {
            throw new IllegalArgumentException("the message translator needs all five parts");
        }
        this.routes = routes;
        this.keys = keys;
        this.streams = streams;
        this.fallback = fallback;
        this.budget = budget;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void translate(GenerationModel fallbackModel, String sourceText, Callback callback) {
        char[] secret;
        DeepSeekRequest request;
        try {
            request = DeepSeekRequestAssembler.assemble((PromptPurpose)PromptPurpose.MESSAGE_TRANSLATION, (boolean)false, Collections.emptyList(), (String)sourceText, (PromptBudget)this.budget);
        }
        catch (RuntimeException refused) {
            callback.onFinished(false, "");
            return;
        }
        QuickModelGateway.Route route = this.routes.translationAssignment();
        if (route == null) {
            this.fallback.open(fallbackModel, request, MessageTranslator.collectingSink(callback));
            return;
        }
        try {
            secret = this.keys.read(route.providerId);
        }
        catch (SecretStoreException unreadable) {
            this.fallback.open(fallbackModel, request, MessageTranslator.collectingSink(callback));
            return;
        }
        try {
            this.streams.open(new String(secret), ProviderEndpoint.of((String)route.baseUrl, (String)route.modelWireId), request, MessageTranslator.collectingSink(callback));
        }
        finally {
            Arrays.fill(secret, '\u0000');
        }
    }

    private static GenerationSink collectingSink(final Callback callback) {
        return new GenerationSink(){
            private final StringBuilder text = new StringBuilder();

            public boolean onAccepted(int httpStatus) {
                return true;
            }

            public void onDelta(String reasoningChunk, String contentChunk) {
                this.text.append(contentChunk);
            }

            public void onTerminal(StreamOutcome outcome) {
                boolean ok = outcome.kind == StreamOutcome.Kind.COMPLETED && this.text.length() > 0;
                callback.onFinished(ok, ok ? this.text.toString() : "");
            }
        };
    }

    public static interface Routes {
        public QuickModelGateway.Route translationAssignment();
    }

    public static interface Keys {
        public char[] read(String var1) throws SecretStoreException;
    }

    public static interface EndpointStreams {
        public AiGateway.Call open(String var1, ProviderEndpoint var2, DeepSeekRequest var3, GenerationSink var4);
    }

    public static interface Callback {
        public void onFinished(boolean var1, String var2);
    }
}

