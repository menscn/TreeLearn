package com.treelearn.app;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.treelearn.ai.gateway.JsonChatRequestBody;
import com.treelearn.ai.prompt.DeepSeekMessage;
import com.treelearn.ai.prompt.DeepSeekPromptRegistry;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.ai.prompt.DeepSeekRequestAssembler;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * The wire format of one chat request (technical design 9.1, 9.2): the gateway transports the
 * assembled messages unchanged, encodes UTF-8 literally and always states the thinking switch.
 *
 * <p>This lives in {@code :app}'s androidTest source set because {@code android.util.JsonWriter}
 * only exists on the device.
 */
public class DeepSeekRequestBodyInstrumentedTest {

    private static final String FIRST_QUESTION = "\u7b2c\u4e00\u95ee";
    private static final String FIRST_ANSWER = "\u7b2c\u4e00\u7b54";
    private static final String CURRENT_INPUT = "\u5f53\u524d\u95ee\u9898\ud83c\udf31";

    private static DeepSeekRequest request(boolean thinking) {
        return DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, thinking,
                List.of(new DeepSeekMessage(DeepSeekMessage.Role.USER, FIRST_QUESTION),
                        new DeepSeekMessage(DeepSeekMessage.Role.ASSISTANT, FIRST_ANSWER)),
                CURRENT_INPUT, new PromptBudget(1_000_000, 1000));
    }

    @Test
    public void encodesTheAssembledMessagesInOrderWithoutRewritingThem() throws Exception {
        DeepSeekRequest request = request(true);
        byte[] body = new JsonChatRequestBody().encode(GenerationModel.FLASH, request);
        String text = new String(body, UTF_8);
        JSONObject json = new JSONObject(text);

        assertEquals("deepseek-flash", json.getString("model"));
        assertTrue(json.getBoolean("stream"));

        JSONArray messages = json.getJSONArray("messages");
        assertEquals(request.messages.size(), messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        assertEquals("assistant", messages.getJSONObject(2).getString("role"));
        assertEquals("user", messages.getJSONObject(3).getString("role"));
        assertEquals(DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER, true),
                messages.getJSONObject(0).getString("content"));
        assertEquals(FIRST_QUESTION, messages.getJSONObject(1).getString("content"));
        assertEquals(FIRST_ANSWER, messages.getJSONObject(2).getString("content"));
        assertEquals(CURRENT_INPUT, messages.getJSONObject(3).getString("content"));
    }

    @Test
    public void statesThinkingExplicitlyInBothModes() throws Exception {
        JSONObject enabled = new JSONObject(
                new String(new JsonChatRequestBody().encode(GenerationModel.PRO, request(true)), UTF_8));
        assertEquals("enabled", enabled.getJSONObject("thinking").getString("type"));
        assertEquals(JsonChatRequestBody.REASONING_EFFORT,
                enabled.getJSONObject("thinking").getString("reasoning_effort"));
        assertEquals("deepseek-v4-pro", enabled.getString("model"));

        JSONObject disabled = new JSONObject(
                new String(new JsonChatRequestBody().encode(GenerationModel.FLASH, request(false)), UTF_8));
        assertEquals("disabled", disabled.getJSONObject("thinking").getString("type"));
        assertFalse("a disabled request must not carry a reasoning effort",
                disabled.getJSONObject("thinking").has("reasoning_effort"));
    }

    @Test
    public void writesUtf8LiterallyInsteadOfAsciiEscapes() {
        byte[] body = new JsonChatRequestBody().encode(GenerationModel.FLASH, request(true));
        String text = new String(body, UTF_8);

        assertTrue("the current input must survive byte-for-byte",
                contains(body, CURRENT_INPUT.getBytes(UTF_8)));
        assertTrue(contains(body, FIRST_QUESTION.getBytes(UTF_8)));
        assertTrue(contains(body, FIRST_ANSWER.getBytes(UTF_8)));
        assertTrue(text.contains(CURRENT_INPUT));
        assertFalse("non-ASCII must not be escaped", text.contains("\\u5f53"));
        assertFalse(text.contains("\\ud83c"));
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start + needle.length <= haystack.length; start++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}