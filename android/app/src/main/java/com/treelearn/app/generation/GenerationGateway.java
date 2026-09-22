package com.treelearn.app.generation;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;

/**
 * The one external capability the coordinator needs: opening a streaming call.
 *
 * <p>The API key is deliberately absent from this signature. Resolving the stored credential belongs
 * to the implementation ({@code AiGatewayStreams}), which is what keeps the secret out of the
 * scheduler, the FIFO queue and every log line.
 */
public interface GenerationGateway {

    /** Handle of one in-flight call. */
    interface Stream {

        void cancel();

        boolean isCancelled();
    }

    Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink);
}
