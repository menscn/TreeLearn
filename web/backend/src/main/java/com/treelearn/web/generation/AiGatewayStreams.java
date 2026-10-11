package com.treelearn.web.generation;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;

/**
 * The real {@link DesktopGenerationGateway} over the shared {@link AiGateway} (task T-2026-10-10-024).
 *
 * <p>It is the desktop port's production adapter: it owns the base URL and the {@code char[]} credential
 * (never a {@code String} field on the coordinator), derives the per-model {@link ProviderEndpoint} at
 * call time, and hands the shared gateway the request unchanged. The credential is only turned into the
 * {@code String} the shared port asks for at the moment of the call, so no key material is retained
 * here either. T-025 wires the production instance; the stub HTTP test builds the same adapter over a
 * loopback transport, so the port is proven end to end against a real socket.
 */
public final class AiGatewayStreams implements DesktopGenerationGateway {

    private final AiGateway gateway;
    private final String baseUrl;
    private final char[] apiKey;

    public AiGatewayStreams(AiGateway gateway, String baseUrl, char[] apiKey) {
        this.gateway = Guard.object(gateway, "gateway");
        this.baseUrl = Guard.nonBlank(baseUrl, "baseUrl");
        this.apiKey = apiKey == null ? new char[0] : apiKey.clone();
    }

    @Override
    public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
        AiGateway.Call call = gateway.streamChat(new String(apiKey),
                ProviderEndpoint.of(baseUrl, Guard.object(model, "model").wireId), request, sink);
        return new Stream() {
            @Override
            public void cancel() {
                call.cancel();
            }

            @Override
            public boolean isCancelled() {
                return call.isCancelled();
            }
        };
    }
}
