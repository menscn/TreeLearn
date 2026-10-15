package com.treelearn.ai.gateway;

import com.treelearn.core.domain.GenerationModel;

/**
 * The per-provider request parameters the gateway injects (SPEC-2026-09-23-providers-and-models,
 * protocol abstraction): the base URL and the wire id of the selected model record. The credential
 * is deliberately not part of this object — it is resolved per call and handed to the transport, so
 * no key material is ever held here either.
 *
 * <p>A pure Java value object: it imports nothing from {@code :app}, so the dependency direction
 * stays {@code :app -> :ai}, and the same instance is what a caller assembles from a provider
 * record before handing the request to {@link AiGateway}.
 *
 * <p>Construction is fail-fast: a cleartext or relative base URL is refused by
 * {@link GatewayEndpoints#requireHttps} before any connection can exist, and a blank wire id is
 * refused here — a misconfigured provider must never degrade into a request that cannot name its
 * model.
 */
public final class ProviderEndpoint {

    private final String baseUrl;
    private final String modelWireId;

    private ProviderEndpoint(String baseUrl, String modelWireId) {
        this.baseUrl = GatewayEndpoints.requireHttps(baseUrl);
        if (modelWireId == null || modelWireId.trim().isEmpty()) {
            throw new IllegalArgumentException("the provider model wire id must not be blank");
        }
        this.modelWireId = modelWireId;
    }

    /**
     * The DeepSeek default instance: the documented base URL and the selected model's wire id, so
     * the request this object produces is byte for byte the one the single-provider gateway sent.
     */
    public static ProviderEndpoint deepSeek(GenerationModel model) {
        return new ProviderEndpoint(GatewayEndpoints.BASE_URL, model.wireId);
    }

    /** Any OpenAI-compatible provider: an absolute https base URL and the model record's wire id. */
    public static ProviderEndpoint of(String baseUrl, String modelWireId) {
        return new ProviderEndpoint(baseUrl, modelWireId);
    }

    /** The validated https base URL, without a trailing path. */
    public String baseUrl() {
        return baseUrl;
    }

    /** The wire id of the selected model record; not limited to the built-in tiers. */
    public String modelWireId() {
        return modelWireId;
    }

    /** The chat-completions URL of this provider. */
    public String chatUrl() {
        return baseUrl + GatewayEndpoints.CHAT_PATH;
    }

    /** The model-list URL of this provider (OpenAI-compatible {@code /models}). */
    public String modelsUrl() {
        return baseUrl + GatewayEndpoints.MODELS_PATH;
    }
}
