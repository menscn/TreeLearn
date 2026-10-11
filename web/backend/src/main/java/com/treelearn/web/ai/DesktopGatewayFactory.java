package com.treelearn.web.ai;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.GatewayEndpoints;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.HttpsTransport;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.Guard;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 组装桌面网关：把共享 {@link AiGateway} 与 {@link HttpsTransport} 接到桌面平台 JSON 适配器
 * （{@link JacksonChatRequestBody}、{@link JacksonProviderJson}），并注入 base URL、{@code char[]} 形态的 API Key 与流式
 * executor（对照 Android {@code com.treelearn.ai.gateway.DeepSeekGatewayFactory}）。
 *
 * <p>Key 以 {@code char[]} 持有（桌面凭据端口 {@code SecretPort.read} 返回 {@code Optional<char[]>}）；仅在实际发起调用
 * 的一刻转成共享端口要求的 {@code String}，不在本对象上保留副本的字符串字段。
 *
 * <p>base URL 在构造时经 {@link GatewayEndpoints#requireHttps} 校验（失败关闭），HTTP 明文在连接建立前即被拒绝。
 */
public final class DesktopGatewayFactory {

    /** 连接超时：与 Android 对齐。 */
    public static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    /** 读超时：须容忍较慢的推理阶段，与 Android 对齐。 */
    public static final int READ_TIMEOUT_MILLIS = 60_000;

    private static final Logger LOG = LoggerFactory.getLogger("com.treelearn.web.ai");

    private final AiGateway gateway;
    private final String baseUrl;
    private final char[] apiKey;

    private DesktopGatewayFactory(String baseUrl, char[] apiKey, Executor executor) {
        this.baseUrl = GatewayEndpoints.requireHttps(baseUrl);
        this.apiKey = apiKey == null ? new char[0] : apiKey.clone();
        this.gateway = new AiGateway(new HttpsTransport(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS),
                new JacksonChatRequestBody(), new JacksonProviderJson.Deltas(),
                new JacksonProviderJson.Balance(), new JacksonProviderJson.ModelList(),
                new Slf4jLog(), Guard.object(executor, "executor"));
    }

    /** 以 provider 的 base URL、{@code char[]} API Key 与流式 executor 组装桌面网关。 */
    public static DesktopGatewayFactory create(String baseUrl, char[] apiKey, Executor executor) {
        return new DesktopGatewayFactory(baseUrl, apiKey, executor);
    }

    /** 一条流式调用的取消句柄：endpoint 由 base URL 与模型 wire id 派生，key 在调用那一刻转成 String。 */
    public AiGateway.Call streamChat(String modelWireId, DeepSeekRequest request, GenerationSink sink) {
        return gateway.streamChat(new String(apiKey), ProviderEndpoint.of(baseUrl, modelWireId),
                request, sink);
    }

    /** 底层共享网关，供余额检查、模型列表等非流式调用使用。 */
    public AiGateway gateway() {
        return gateway;
    }

    /** 每次流式调用一个守护线程；并发上限属于应用级协调器，不在这里。 */
    public static ExecutorService newStreamExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "treelearn-deepseek-stream");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 诊断出口：网关只传元数据，绝不会带上 key、问题、回答或推理正文（技术方案 §15）。 */
    private static final class Slf4jLog implements GatewayPorts.Log {

        @Override
        public void event(String name, String details) {
            LOG.debug("deepseek {} {}", name, details);
        }
    }
}
