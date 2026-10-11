package com.treelearn.web.generation;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;

/**
 * The one external capability the desktop coordinator needs: opening a streaming call (task
 * T-2026-10-10-024).
 *
 * <p>Semantic reference — the Android {@code com.treelearn.app.generation.GenerationGateway} it
 * mirrors. The API key is deliberately absent from this signature: resolving the stored credential
 * belongs to the adapter ({@link AiGatewayStreams}), which is what keeps the secret out of the
 * coordinator and every log line. A fake implementation of this port lets a test drive failure
 * combinations that a real socket cannot be made to produce on demand, while {@link AiGatewayStreams}
 * proves the same contract over a real loopback socket.
 */
public interface DesktopGenerationGateway {

    /** Handle of one in-flight call. {@link #cancel()} is safe to call from any thread and idempotent. */
    interface Stream {

        void cancel();

        boolean isCancelled();
    }

    Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink);
}
