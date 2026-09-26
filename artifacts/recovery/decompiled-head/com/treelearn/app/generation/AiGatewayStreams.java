/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.gateway.AiGateway
 *  com.treelearn.ai.gateway.AiGateway$Call
 *  com.treelearn.ai.gateway.AiGatewayError
 *  com.treelearn.ai.gateway.GenerationSink
 *  com.treelearn.ai.gateway.StreamOutcome
 *  com.treelearn.ai.gateway.StreamOutcome$Kind
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.core.domain.GenerationModel
 *  com.treelearn.data.secret.KeystoreSecretStore
 *  com.treelearn.data.secret.SecretStoreException
 */
package com.treelearn.app.generation;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.secret.SecretStoreException;
import java.util.Arrays;

public final class AiGatewayStreams
implements GenerationGateway {
    private static final GenerationGateway.Stream IDLE = new GenerationGateway.Stream(){

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };
    private final AiGateway gateway;
    private final KeystoreSecretStore secrets;

    public AiGatewayStreams(AiGateway gateway, KeystoreSecretStore secrets) {
        if (gateway == null) {
            throw new IllegalArgumentException("gateway must not be null");
        }
        if (secrets == null) {
            throw new IllegalArgumentException("secrets must not be null");
        }
        this.gateway = gateway;
        this.secrets = secrets;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public GenerationGateway.Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
        char[] secret;
        try {
            secret = this.secrets.read();
        }
        catch (SecretStoreException unreadable) {
            sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.FAILED, AiGatewayError.keyMissing(), 0, 0, 0));
            return IDLE;
        }
        try {
            final AiGateway.Call call = this.gateway.streamChat(new String(secret), model, request, sink);
            GenerationGateway.Stream stream = new GenerationGateway.Stream(){

                @Override
                public void cancel() {
                    call.cancel();
                }

                @Override
                public boolean isCancelled() {
                    return call.isCancelled();
                }
            };
            return stream;
        }
        finally {
            Arrays.fill(secret, '\u0000');
        }
    }
}

