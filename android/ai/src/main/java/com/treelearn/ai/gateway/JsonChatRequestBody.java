package com.treelearn.ai.gateway;

import android.util.JsonWriter;
import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.Guard;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * The request body, written with the platform's {@code JsonWriter} (technical design 9.1, 9.2).
 *
 * <p>It encodes the assembled messages verbatim and in order: no system message is added, none is
 * removed or merged, no role is rewritten and no text is normalised. {@code JsonWriter} escapes only
 * what JSON requires, so Chinese text and emoji travel as their UTF-8 bytes rather than as
 * {@code \\uXXXX} escapes.
 *
 * <p>The thinking switch is always written explicitly, in both modes. The reasoning-effort value is
 * the product's selection; it is a constant here rather than a per-call argument, and it is one of the
 * provider facts to re-verify against the official documentation before release.
 */
public final class JsonChatRequestBody implements GatewayPorts.BodyEncoder {

    /** Provider name of the enabled thinking mode. */
    public static final String THINKING_ENABLED = "enabled";
    /** Provider name of the disabled thinking mode. */
    public static final String THINKING_DISABLED = "disabled";
    /** The product's reasoning effort when thinking is on. */
    public static final String REASONING_EFFORT = "high";

    @Override
    public byte[] encode(GenerationModel model, DeepSeekRequest request) {
        Guard.object(model, "model");
        Guard.object(request, "request");
        StringWriter body = new StringWriter();
        JsonWriter writer = new JsonWriter(body);
        try {
            writer.beginObject();
            writer.name("model").value(model.wireId);
            writer.name("stream").value(true);
            writer.name("thinking").beginObject();
            writer.name("type").value(request.thinkingEnabled ? THINKING_ENABLED : THINKING_DISABLED);
            if (request.thinkingEnabled) {
                writer.name("reasoning_effort").value(REASONING_EFFORT);
            }
            writer.endObject();
            writer.name("messages").beginArray();
            for (DeepSeekMessage message : request.messages) {
                writer.beginObject();
                writer.name("role").value(role(message.role));
                writer.name("content").value(message.content);
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
            writer.flush();
        } catch (IOException impossible) {
            throw new IllegalStateException("writing to a StringWriter cannot fail", impossible);
        }
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String role(DeepSeekMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }
}