package com.treelearn.web.generation;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.web.ai.DesktopGatewayFactory;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.storage.ModelStore;
import com.treelearn.web.storage.ProviderStore;
import java.util.concurrent.ExecutorService;

/**
 * 生产网关（任务 T-2026-10-10-028）：每次调用按模型所属的启用提供商解析 base URL 与 Key，再经
 * {@link DesktopGatewayFactory} 走共享 {@code AiGateway}。Key 只在调用一刻从凭据端口读取并转成共享端口
 * 要求的形态，不在本对象上驻留字符串。
 */
final class ProviderGateway implements DesktopGenerationGateway {

    private final ProviderStore providers;
    private final ModelStore models;
    private final SecretPort secrets;
    private final ExecutorService executor = DesktopGatewayFactory.newStreamExecutor();

    ProviderGateway(ProviderStore providers, ModelStore models, SecretPort secrets) {
        this.providers = providers;
        this.models = models;
        this.secrets = secrets;
    }

    @Override
    public Stream open(GenerationModel model, DeepSeekRequest request, GenerationSink sink) {
        ProviderStore.Provider provider = providerFor(model);
        char[] key = secrets.read(SecretPort.providerTarget(provider.id)).orElseGet(() -> new char[0]);
        var call = DesktopGatewayFactory.create(provider.baseUrl, key, executor)
                .streamChat(model.wireId, request, sink);
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

    private ProviderStore.Provider providerFor(GenerationModel model) {
        for (ProviderStore.Provider provider : providers.list()) {
            if (!provider.enabled) {
                continue;
            }
            for (ModelStore.Model candidate : models.listForProvider(provider.id)) {
                if (candidate.modelId.equals(model.wireId)) {
                    return provider;
                }
            }
        }
        throw new IllegalStateException("no enabled provider offers model " + model.wireId);
    }
}
