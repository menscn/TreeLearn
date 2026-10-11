package com.treelearn.web.ai;

import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GatewayPorts;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 桌面 provider JSON 读取边界：实现共享 {@link GatewayPorts.DeltaDecoder} / {@link GatewayPorts.BalanceDecoder} /
 * {@link GatewayPorts.ModelListDecoder}，逐分支对齐 Android {@code com.treelearn.ai.gateway.ProviderJson}（对照源：
 * {@code android/ai/src/main/java/com/treelearn/ai/gateway/ProviderJson.java}）。
 *
 * <p>与 Android 的语义约定（逐分支对照）：
 * <ul>
 *   <li>只查找两个通道键，其余兼容字段在任意层级被忽略。</li>
 *   <li>顶层不是 JSON 对象、缺 {@code choices} 数组、缺 {@code data} 数组、条目缺字符串 {@code id}、余额非布尔、或
 *       流内出现 {@code error} 对象，都被拒绝（{@link GatewayPorts.PayloadException}），绝不当作空值静默吞掉。</li>
 *   <li>存在但为 JSON {@code null} 的 {@code content}/{@code delta} 视为空字符串，而不是字符串 {@code "null"}。</li>
 *   <li>{@code error} 键存在即为流内错误（即使值为 null），与 Android {@code JSONObject.has} 一致。</li>
 * </ul>
 *
 * <p>已知差异：Android 使用 {@code org.json} 宽松解析（允许单引号等），Jackson 严格。provider 返回非严格 JSON 时
 * 桌面会分类为 {@code PARSE_FAILED}；正常 provider 输出无差异。详见任务对照记录。
 */
public final class JacksonProviderJson {

    private static final String CHOICES = "choices";
    private static final String DELTA = "delta";
    private static final String REASONING_CONTENT = "reasoning_content";
    private static final String CONTENT = "content";
    private static final String ERROR = "error";
    private static final String IS_AVAILABLE = "is_available";
    private static final String DATA = "data";
    private static final String ID = "id";

    private static final JsonMapper MAPPER = new JsonMapper();

    private JacksonProviderJson() {
    }

    /** 把一个流式 chunk 解码成两个通道。 */
    public static final class Deltas implements GatewayPorts.DeltaDecoder {

        @Override
        public GatewayPorts.Delta decode(String payload) {
            JsonNode root = parse(payload, "stream payload");
            if (root.has(ERROR)) {
                throw refuse(AiGatewayError.inBandProviderError(),
                        "the provider sent an error object inside an accepted stream");
            }
            JsonNode choices = choices(root);
            if (choices.size() == 0) {
                return GatewayPorts.Delta.EMPTY;
            }
            JsonNode first = choices.get(0);
            JsonNode delta = first != null && first.isObject() ? first.get(DELTA) : null;
            if (delta != null && !delta.isObject()) {
                delta = null;
            }
            return new GatewayPorts.Delta(text(delta, REASONING_CONTENT), text(delta, CONTENT));
        }
    }

    /** 读取余额可用性布尔。 */
    public static final class Balance implements GatewayPorts.BalanceDecoder {

        @Override
        public boolean isAvailable(String payload) {
            JsonNode available = parse(payload, "balance response").get(IS_AVAILABLE);
            if (available == null || !available.isBoolean()) {
                throw refuse(AiGatewayError.parseFailed("the balance response must carry a boolean "
                        + IS_AVAILABLE), "the balance response has no boolean " + IS_AVAILABLE);
            }
            return available.booleanValue();
        }
    }

    /** 从 {@code /models} 响应读出模型 wire id。 */
    public static final class ModelList implements GatewayPorts.ModelListDecoder {

        @Override
        public List<String> decode(String payload) {
            JsonNode root = parse(payload, "model list response");
            JsonNode data = dataArray(root);
            List<String> ids = new ArrayList<>(data.size());
            for (int i = 0; i < data.size(); i++) {
                JsonNode entry = data.get(i);
                JsonNode id = entry != null && entry.isObject() ? entry.get(ID) : null;
                if (id == null || !id.isTextual() || id.textValue().trim().isEmpty()) {
                    throw refuse(AiGatewayError.parseFailed("the model list response carries an "
                            + "entry without a string " + ID), "a model list entry has no string " + ID);
                }
                ids.add(id.textValue());
            }
            return ids;
        }
    }

    private static JsonNode dataArray(JsonNode root) {
        JsonNode data = root.get(DATA);
        if (data == null || !data.isArray()) {
            throw refuse(AiGatewayError.parseFailed("a model list response carries no " + DATA
                    + " array, so its contents cannot be trusted"), "the payload has no " + DATA);
        }
        return data;
    }

    private static JsonNode parse(String payload, String what) {
        JsonNode root;
        try {
            root = MAPPER.readTree(payload == null ? "" : payload);
        } catch (JacksonException malformed) {
            // 固定文案：解析器消息可能回显负载，而负载可能包含问题或回答正文。
            String detail = "a " + what + " was not one JSON object";
            throw refuse(AiGatewayError.parseFailed(detail), detail);
        }
        if (root == null || !root.isObject()) {
            String detail = "a " + what + " was not one JSON object";
            throw refuse(AiGatewayError.parseFailed(detail), detail);
        }
        return root;
    }

    private static JsonNode choices(JsonNode root) {
        JsonNode choices = root.get(CHOICES);
        if (choices == null || !choices.isArray()) {
            throw refuse(AiGatewayError.parseFailed("a stream payload carries no " + CHOICES
                    + " array, so its content cannot be trusted"), "the payload has no " + CHOICES);
        }
        return choices;
    }

    private static String text(JsonNode delta, String name) {
        JsonNode value = delta == null ? null : delta.get(name);
        return value != null && value.isTextual() ? value.textValue() : "";
    }

    private static GatewayPorts.PayloadException refuse(AiGatewayError error, String message) {
        return new GatewayPorts.PayloadException(error, message);
    }
}
