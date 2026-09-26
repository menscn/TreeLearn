package com.treelearn.app.generation;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.secret.SecretStoreException;

import java.util.Arrays;

/**
 * The production {@link GenerationGateway}: it resolves the stored credential and hands the request
 * to {@link AiGateway}.
 *
 * <p>The credential is read here and nowhere else in the coordinator. It never becomes a field, it
 * is never passed to the scheduler or the queue, and the character array is wiped as soon as the
 * call has been opened, so a crash dump or a heap walk of the coordinator cannot reach it. The
 * {@code String} that {@code AiGateway} requires is built for the call and discarded with the call.
 *
 * <p>An unreadable envelope (corrupted, or its Keystore entry gone) is reported as
 * {@code KEY_MISSING}: from the learner's side the outcome is the same one 9.5 describes — there is
 * no usable key, so the draft is kept and the settings screen is the way forward.
 */
public final class AiGatewayStreams implements GenerationGateway {

    /** The handle returned when nothing was opened, so a caller never sees a null stream. */
    private static final GenerationGateway.Stream IDLE = new GenerationGateway.Stream() {
        @Override
        public void cancel() {
            // Nothing was opened, so there is nothing to abort.
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    private final AiGateway gateway;
    private final KeystoreSecretStore secrets;
    private final String keyProviderId;

    public AiGatewayStreams(AiGateway gateway, KeystoreSecretStore secrets, String keyProviderId) {
        if (gateway == null) {
            throw new IllegalArgumentException("gateway must not be null");
        }
        if (secrets == null) {
            throw new IllegalArgumentException("secrets must not be null");
        }
        if (keyProviderId == null || keyProviderId.trim().isEmpty()) {
            throw new IllegalArgumentException("keyProviderId must not be blank");
        }
        this.gateway = gateway;
        this.secrets = secrets;
        this.keyProviderId = keyProviderId;
    }

    @Override
    public GenerationGateway.Stream open(GenerationModel model, DeepSeekRequest request,
            GenerationSink sink) {
        char[] secret;
        try {
            secrets.migrateLegacySecret(keyProviderId);
            secret = secrets.read(keyProviderId);
        } catch (SecretStoreException unreadable) {
            sink.onTerminal(new StreamOutcome(StreamOutcome.Kind.FAILED, AiGatewayError.keyMissing(),
                    0, 0, 0));
            return IDLE;
        }
        try {
            final AiGateway.Call call =
                    gateway.streamChat(new String(secret), model, request, sink);
            return new GenerationGateway.Stream() {
                @Override
                public void cancel() {
                    call.cancel();
                }

                @Override
                public boolean isCancelled() {
                    return call.isCancelled();
                }
            };
        } finally {
            Arrays.fill(secret, '\0');
        }
    }
}
