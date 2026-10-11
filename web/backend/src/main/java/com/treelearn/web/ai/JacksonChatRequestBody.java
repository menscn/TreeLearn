package com.treelearn.web.ai;

import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.Guard;
import java.io.ByteArrayOutputStream;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonFactory;

/**
 * 桌面请求体编码器：实现共享 {@link GatewayPorts.BodyEncoder}，逐字段对齐 Android
 * {@code com.treelearn.ai.gateway.JsonChatRequestBody}（对照源：{@code android/ai/src/main/java/com/treelearn/ai/gateway/
 * JsonChatRequestBody.java}）。
 *
 * <p>与 Android 的语义约定（逐分支对照）：
 * <ul>
 *   <li>键集与键序一致：{@code model}、{@code stream}、{@code thinking}、{@code messages}；对象内为 {@code type}（
 *       思考时再跟 {@code reasoning_effort}）、消息为 {@code role}、{@code content}。Jackson 的流式生成器按调用顺序
 *       输出字段，与 Android {@code JsonWriter} 的顺序相同。</li>
 *   <li>组装好的消息原样、按序编码：不新增/删除/合并 system 消息，不重写 role，不归一化文本。</li>
 *   <li>思考开关两种模式都显式写出；{@code reasoning_effort} 仅在开启时为常量 {@value #REASONING_EFFORT}。</li>
 *   <li>转义遵循 JSON 规范：Jackson 默认 UTF-8 输出只转义必需字符（{@code "}、{@code \}、控制字符），中文与 emoji
 *       作为 UTF-8 字节原样传输，与 Android {@code JsonWriter} 的行为一致。</li>
 * </ul>
 */
public final class JacksonChatRequestBody implements GatewayPorts.BodyEncoder {

    /** 思考开启时 provider 侧的模式名（与 Android 对齐）。 */
    public static final String THINKING_ENABLED = "enabled";
    /** 思考关闭时 provider 侧的模式名（与 Android 对齐）。 */
    public static final String THINKING_DISABLED = "disabled";
    /** 思考开启时的推理强度常量（与 Android 对齐）。 */
    public static final String REASONING_EFFORT = "high";

    private final JsonFactory factory = new JsonFactory();

    @Override
    public byte[] encode(String modelWireId, DeepSeekRequest request) {
        Guard.nonBlank(modelWireId, "model wire id");
        Guard.object(request, "request");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (JsonGenerator writer = factory.createGenerator(body)) {
            writer.writeStartObject();
            writer.writeStringProperty("model", modelWireId);
            writer.writeBooleanProperty("stream", true);
            writer.writeObjectPropertyStart("thinking");
            writer.writeStringProperty("type",
                    request.thinkingEnabled ? THINKING_ENABLED : THINKING_DISABLED);
            if (request.thinkingEnabled) {
                writer.writeStringProperty("reasoning_effort", REASONING_EFFORT);
            }
            writer.writeEndObject();
            writer.writeArrayPropertyStart("messages");
            for (DeepSeekMessage message : request.messages) {
                writer.writeStartObject();
                writer.writeStringProperty("role", role(message.role));
                writer.writeStringProperty("content", message.content);
                writer.writeEndObject();
            }
            writer.writeEndArray();
            writer.writeEndObject();
        }
        return body.toByteArray();
    }

    private static String role(DeepSeekMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }
}
