package com.treelearn.web.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.gateway.SseFrameParser;
import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.PromptPurpose;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 桌面适配器与共享 fixtures（{@code web/contracts/fixtures-ai/}）的等价测试（T-2026-10-10-021）。
 *
 * <p>fixtures 是唯一样例来源：请求体 fixtures 断言 {@link JacksonChatRequestBody} 的键集/键序/思考开关/消息逐字
 * 运输，流 fixtures 通过 {@link SseFrameParser} 回放 chunk 序列并断言事件序列与错误分类，模型 fixtures 断言
 * {@link JacksonProviderJson.ModelList} 的解析与拒绝。对照源为 Android {@code JsonChatRequestBody}/{@code ProviderJson}。
 */
class GatewayAdapterFixturesTest {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final Path FIXTURES = resolveFixturesDir();
    private static final JacksonChatRequestBody ENCODER = new JacksonChatRequestBody();
    private static final JacksonProviderJson.Deltas DELTAS = new JacksonProviderJson.Deltas();
    private static final JacksonProviderJson.ModelList MODEL_LIST = new JacksonProviderJson.ModelList();
    private static final PromptBudget GENEROUS = new PromptBudget(1_000_000, 512);

    @Test
    void requestBodiesMatchTheAndroidRequestShape() throws IOException {
        List<Path> files = jsonFiles(FIXTURES.resolve("requests"));
        assertTrue(files.size() >= 8, "the request fixtures must cover every PromptPurpose");

        for (Path file : files) {
            JsonNode fx = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            String modelWireId = fx.get("modelWireId").asText();
            PromptPurpose purpose = PromptPurpose.valueOf(fx.get("purpose").asText());
            boolean thinking = fx.get("thinkingEnabled").booleanValue();
            DeepSeekRequest request = DeepSeekRequestAssembler.assemble(purpose, thinking,
                    history(fx.get("history")), fx.get("currentInput").asText(), GENEROUS);

            JsonNode body = MAPPER.readTree(new String(ENCODER.encode(modelWireId, request),
                    StandardCharsets.UTF_8));

            assertEquals(expectedKeys(fx.get("expectedTopLevelKeys")), keyOrder(body), file.toString());
            assertEquals(modelWireId, body.get("model").asText(), file.toString());
            assertTrue(body.get("stream").booleanValue(), file.toString());
            assertEquals(fx.get("expectedThinking"), body.get("thinking"),
                    "the thinking block must match the fixture: " + file);

            JsonNode messages = body.get("messages");
            assertEquals(request.messages.size(), messages.size(), file.toString());
            for (int i = 0; i < messages.size(); i++) {
                DeepSeekMessage source = request.messages.get(i);
                assertEquals(source.role.name().toLowerCase(), messages.get(i).get("role").asText(),
                        file + " message " + i + " role");
                assertEquals(source.content, messages.get(i).get("content").asText(),
                        file + " message " + i + " content must travel verbatim");
            }
            JsonNode last = messages.get(messages.size() - 1);
            assertEquals("user", last.get("role").asText(), file + " last role");
            assertEquals(fx.get("currentInput").asText(), last.get("content").asText(),
                    file + " the current input is the last message");
        }
    }

    @Test
    void streamChunksDecodeToTheExpectedEventsAndTerminal() throws IOException {
        List<Path> files = jsonFiles(FIXTURES.resolve("streams"));
        assertTrue(files.size() >= 6, "the stream fixtures must cover the documented scenarios");

        for (Path file : files) {
            JsonNode fx = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            List<String> payloads = replayThroughParser(lines(fx.get("lines")));

            List<GatewayPorts.Delta> events = new ArrayList<>();
            boolean done = false;
            AiGatewayError failure = null;
            for (String payload : payloads) {
                if (done || failure != null) {
                    break;
                }
                if ("[DONE]".equals(payload.trim())) {
                    done = true;
                    break;
                }
                try {
                    GatewayPorts.Delta delta = DELTAS.decode(payload);
                    if (!delta.isEmpty()) {
                        events.add(delta);
                    }
                } catch (GatewayPorts.PayloadException refused) {
                    failure = refused.error;
                } catch (RuntimeException other) {
                    failure = AiGatewayError.parseFailed("the decoder refused an event");
                }
            }

            String terminal = failure != null ? "FAILED" : (done ? "COMPLETED" : "INTERRUPTED");
            assertEquals(fx.get("expectedTerminal").asText(), terminal, file.toString());

            JsonNode expectedEvents = fx.get("expectedEvents");
            assertEquals(expectedEvents.size(), events.size(), file + " event count");
            for (int i = 0; i < events.size(); i++) {
                assertEquals(expectedEvents.get(i).get("reasoningChunk").asText(),
                        events.get(i).reasoningChunk, file + " event " + i + " reasoning");
                assertEquals(expectedEvents.get(i).get("contentChunk").asText(),
                        events.get(i).contentChunk, file + " event " + i + " content");
            }

            JsonNode expectedKind = fx.get("expectedErrorKind");
            if (expectedKind == null || expectedKind.isNull()) {
                assertEquals(null, failure, file + " must not fail");
            } else {
                assertNotNull(failure, file + " must fail");
                assertEquals(AiGatewayError.Kind.valueOf(expectedKind.asText()), failure.kind,
                        file.toString());
            }
        }
    }

    @Test
    void modelListsDecodeToTheExpectedIdsOrRefusal() throws IOException {
        List<Path> files = jsonFiles(FIXTURES.resolve("models"));
        assertTrue(files.size() >= 3, "the model fixtures must cover success and refusal");

        for (Path file : files) {
            JsonNode fx = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            String payload = fx.get("payload").asText();
            JsonNode expectedKind = fx.get("expectedErrorKind");
            if (expectedKind == null || expectedKind.isNull()) {
                assertEquals(expectedKeys(fx.get("expectedIds")), MODEL_LIST.decode(payload),
                        file.toString());
            } else {
                try {
                    List<String> ids = MODEL_LIST.decode(payload);
                    fail(file + " must be refused but decoded to " + ids);
                } catch (GatewayPorts.PayloadException refused) {
                    assertEquals(AiGatewayError.Kind.valueOf(expectedKind.asText()),
                            refused.error.kind, file.toString());
                }
            }
        }
    }

    @Test
    void theFactoryWiresTheSharedGatewayToTheDesktopAdapters() {
        AiGateway gateway = DesktopGatewayFactory
                .create("https://api.deepseek.com", "sk-fixture".toCharArray(), Runnable::run)
                .gateway();
        assertNotNull(gateway);
        assertThrows(IllegalArgumentException.class, () -> DesktopGatewayFactory
                .create("http://api.deepseek.com", "sk-fixture".toCharArray(), Runnable::run));
    }

    private static List<String> replayThroughParser(List<String> lines) {
        byte[] bytes = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
        List<String> payloads = new ArrayList<>();
        SseFrameParser parser = new SseFrameParser();
        parser.accept(bytes, bytes.length, payloads::add);
        parser.endOfStream(payloads::add);
        return payloads;
    }

    private static List<DeepSeekMessage> history(JsonNode entries) {
        List<DeepSeekMessage> messages = new ArrayList<>();
        if (entries != null) {
            for (JsonNode entry : entries) {
                messages.add(new DeepSeekMessage(DeepSeekMessage.Role.valueOf(entry.get("role").asText()),
                        entry.get("content").asText()));
            }
        }
        return messages;
    }

    private static List<String> expectedKeys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        for (JsonNode entry : node) {
            keys.add(entry.asText());
        }
        return keys;
    }

    private static List<String> keyOrder(JsonNode node) {
        return new ArrayList<>(node.propertyNames());
    }

    private static List<String> lines(JsonNode node) {
        List<String> lines = new ArrayList<>();
        for (JsonNode entry : node) {
            lines.add(entry.asText());
        }
        return lines;
    }

    private static List<Path> jsonFiles(Path dir) throws IOException {
        assertTrue(Files.isDirectory(dir), "fixtures directory must exist: " + dir);
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            assertFalse(files.isEmpty(), "fixtures directory must not be empty: " + dir);
            return files;
        }
    }

    /**
     * The fixtures live in the repository's only contract directory; the test working directory is the
     * backend project, so the first existing candidate wins. No candidate means the fixtures were moved
     * and the test must fail loudly rather than silently skip.
     */
    private static Path resolveFixturesDir() {
        List<String> candidates = Arrays.asList("../contracts/fixtures-ai",
                "web/contracts/fixtures-ai", "contracts/fixtures-ai");
        for (String candidate : candidates) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        throw new IllegalStateException("could not locate web/contracts/fixtures-ai from "
                + Path.of("").toAbsolutePath());
    }
}
