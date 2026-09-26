package com.treelearn.app.generation;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.data.secret.SecretStoreException;
import java.util.Arrays;

public final class QuickModelGateway
implements GenerationGateway {
    private final Routes routes;
    private final Keys keys;
    private final EndpointStreams streams;
    private final GenerationGateway fallback;

    public QuickModelGateway(Routes routes, Keys keys, EndpointStreams streams, GenerationGateway fallback) {
        if (routes == null || keys == null || streams == null || fallback == null) {
            throw new IllegalArgumentException("the quick-model gateway needs all four parts");
        }
        this.routes = routes;
        this.keys = keys;
        this.streams = streams;
        this.fallback = fallback;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public GenerationGateway.Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
        char[] secret;
        Route route = this.routes.fastAssignment();
        if (route == null) {
            return this.fallback.open(model, request, sink);
        }
        try {
            secret = this.keys.read(route.providerId);
        }
        catch (SecretStoreException unreadable) {
            return this.fallback.open(model, request, sink);
        }
        try {
            final AiGateway.Call call = this.streams.open(new String(secret), ProviderEndpoint.of((String)route.baseUrl, (String)route.modelWireId), request, sink);
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

    public static interface Routes {
        public Route fastAssignment();
    }

    public static interface Keys {
        public char[] read(String var1) throws SecretStoreException;
    }

    public static interface EndpointStreams {
        public AiGateway.Call open(String var1, ProviderEndpoint var2, DeepSeekRequest var3, GenerationSink var4);
    }

    public static final class Route {
        public final String providerId;
        public final String baseUrl;
        public final String modelWireId;

        public Route(String providerId, String baseUrl, String modelWireId) {
            this.providerId = providerId;
            this.baseUrl = baseUrl;
            this.modelWireId = modelWireId;
        }
    }
}

