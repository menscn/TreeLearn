package com.treelearn.web.generation;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.web.ai.JacksonChatRequestBody;
import com.treelearn.web.ai.JacksonProviderJson;
import java.util.List;
import java.util.Map;

/**
 * 桌面模型目录拉取（任务 T-2026-10-10-036，契约 §6.6 {@code POST /api/providers/{id}/models}）：复用共享
 * {@link AiGateway#listModels} 读取供应商 {@code {base}/models}（Bearer 鉴权），只把成功结果交给调用方合并；
 * 失败一律以 §3.3 码拒绝，绝不半写。
 *
 * <p>语义对照 Android {@code com.treelearn.app.wiring.SqliteProviderAdminStore.fetch}：同一 {@code listModels}
 * 调用与同一占位模型 wire id（{@code "list"}——{@code modelsUrl} 只用 baseUrl）。传输由构造注入：生产为
 * {@code HttpsTransport}，测试注入本机 stub（真 HTTP over socket），因此测试不访问外网。
 */
public final class ProviderModelCatalog {

    /** 占位 wire id：{@code /models} URL 只用 baseUrl，该值仅为满足 {@link ProviderEndpoint} 的非空校验。 */
    private static final String MODEL_LIST_PLACEHOLDER = "list";
    private static final GatewayPorts.Log NOOP_LOG = (name, details) -> { };

    private final AiGateway gateway;

    public ProviderModelCatalog(GatewayPorts.Transport transport) {
        this.gateway = new AiGateway(transport, new JacksonChatRequestBody(),
                new JacksonProviderJson.Deltas(), new JacksonProviderJson.Balance(),
                new JacksonProviderJson.ModelList(), NOOP_LOG, Runnable::run);
    }

    /** 拉取供应商模型 wire id；失败以 §3.3 码拒绝（无 Key→{@code KEY_MISSING}，其余→{@code PROVIDER_UNAVAILABLE}）。 */
    public List<String> fetchModels(String baseUrl, char[] key) {
        AiGateway.Models fetched = gateway.listModels(new String(key),
                ProviderEndpoint.of(baseUrl, MODEL_LIST_PLACEHOLDER));
        if (fetched.ok()) {
            return fetched.ids;
        }
        throw refusal(fetched.error);
    }

    private static ServiceRefusalException refusal(AiGatewayError error) {
        if (error.kind == AiGatewayError.Kind.KEY_MISSING) {
            return new ServiceRefusalException("KEY_MISSING", Map.of());
        }
        return new ServiceRefusalException("PROVIDER_UNAVAILABLE",
                Map.of("httpStatus", error.httpStatus));
    }
}
