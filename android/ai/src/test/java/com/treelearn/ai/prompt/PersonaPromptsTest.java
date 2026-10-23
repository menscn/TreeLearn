package com.treelearn.ai.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Persona;
import com.treelearn.core.domain.PromptPurpose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * The persona layer is a second append-only dimension on the main-answer system body (SPEC-2026-09-21
 * 接口 and 状态迁移; prompt design §3.2, §4.1).
 *
 * <p>The base tutor body and its version literal must not move. A style only appends a static
 * instruction block, only for {@code LEARNING_ANSWER}, and a derived purpose asking for one is a
 * programming error - refused through the same category thinking already uses, never silently
 * ignored. No display name from the picker may enter the prompt: the addendum is policy, the names
 * are UI copy.
 */
public class PersonaPromptsTest {

    private static final String[] DISPLAY_NAMES = {
            "默认", "手把手", "严格导师", "学霸搭子", "毒舌损友", "深夜陪读", "学习修复师"
    };

    @Test
    public void theDefaultStyleAppendsNothing() {
        assertEquals("", PersonaPrompts.addendum(Persona.DEFAULT));
        String base = DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).systemContent;
        SystemBodyAssertions.assertSameSystemText("default persona", base,
                DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER, false,
                        Persona.DEFAULT));
    }

    @Test
    public void everyNonDefaultStyleHasItsOwnStaticAddendum() {
        Set<String> seen = new HashSet<>();
        for (Persona persona : Persona.values()) {
            String addendum = PersonaPrompts.addendum(persona);
            if (persona == Persona.DEFAULT) {
                assertEquals("", addendum);
                continue;
            }
            assertFalse(persona + " has no addendum", addendum.trim().isEmpty());
            assertTrue("each style must be distinguishable: " + persona, seen.add(addendum));
        }
        assertEquals(Persona.values().length - 1, seen.size());
    }

    @Test
    public void addendaCarryPolicyNotPickerCopy() {
        for (Persona persona : Persona.values()) {
            String addendum = PersonaPrompts.addendum(persona);
            for (String name : DISPLAY_NAMES) {
                assertFalse("the picker's display name must not enter the prompt: " + name,
                        addendum.contains(name));
            }
            assertFalse("a wire id is an identity, not instruction text: " + persona.wireId,
                    addendum.contains(persona.wireId));
        }
    }

    @Test
    public void theStyleIsAppendedAfterTheBaseAndTheVersionLiteralIsUnchanged() {
        Persona persona = Persona.STRICT_MENTOR;
        String base = DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).systemContent;
        String withStyle = DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER,
                false, persona);

        SystemBodyAssertions.assertSameSystemText("base plus style",
                base + "\n" + PersonaPrompts.addendum(persona), withStyle);
        assertEquals("the main-answer policy version must not move for a style",
                "tl-learning-answer/1.1.0",
                DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).policyVersion);
        assertEquals("the base body itself is untouched",
                base, DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).systemContent);
    }

    @Test
    public void thinkingAndStyleStackInOneSystemMessage() {
        Persona persona = Persona.HAND_HOLDING;
        String composed = DeepSeekPromptRegistry.systemContent(PromptPurpose.LEARNING_ANSWER, true,
                persona);
        String expected = DeepSeekPromptRegistry.spec(PromptPurpose.LEARNING_ANSWER).systemContent
                + "\n" + PromptBodiesV1.THINKING_ADDENDUM
                + "\n" + PersonaPrompts.addendum(persona);
        SystemBodyAssertions.assertSameSystemText("thinking plus style", expected, composed);
    }

    @Test
    public void aNonDefaultStyleOnADerivedPurposeIsRefusedThroughTheExistingCategory() {
        for (PromptPurpose purpose : PromptPurpose.values()) {
            if (purpose == PromptPurpose.LEARNING_ANSWER) {
                continue;
            }
            PromptBuildException refusal = assertThrows(PromptBuildException.class,
                    () -> DeepSeekPromptRegistry.systemContent(purpose, false,
                            Persona.STUDY_BUDDY));
            assertSame(PromptBuildException.Reason.THINKING_NOT_APPLICABLE_FOR_PURPOSE,
                    refusal.reason);
            assertFalse("refusal detail must not carry prompt text: " + refusal.getMessage(),
                    refusal.getMessage().contains("回答风格"));
        }
    }

    @Test
    public void theDefaultStyleKeepsEveryDerivedPurposeWorking() {
        for (PromptPurpose purpose : PromptPurpose.values()) {
            String plain = DeepSeekPromptRegistry.systemContent(purpose, false);
            String withDefault = DeepSeekPromptRegistry.systemContent(purpose, false,
                    Persona.DEFAULT);
            SystemBodyAssertions.assertSameSystemText(purpose.toString(), plain, withDefault);
        }
    }

    @Test
    public void theAssembledRequestCarriesTheStyleAsIdentityAndNeverAsAnExtraMessage() {
        Persona persona = Persona.SARCASTIC_FRIEND;
        List<DeepSeekMessage> history = new ArrayList<>();
        history.add(new DeepSeekMessage(DeepSeekMessage.Role.USER, "第一问"));
        history.add(new DeepSeekMessage(DeepSeekMessage.Role.ASSISTANT, "第一答"));
        DeepSeekRequest request = DeepSeekRequestAssembler.assemble(
                PromptPurpose.LEARNING_ANSWER, false, history, "第二问", persona,
                new PromptBudget(1_000_000, 512));

        assertEquals(persona, request.persona);
        assertEquals("exactly one system message, at position 0", 1,
                countRole(request, DeepSeekMessage.Role.SYSTEM));
        assertEquals(DeepSeekMessage.Role.SYSTEM, request.messages.get(0).role);
        assertEquals("the current input stays last", "第二问",
                request.messages.get(request.messages.size() - 1).content);
        assertEquals(4, request.messages.size());
        assertTrue("the system message is the only place the style lives",
                request.messages.get(0).content.endsWith(PersonaPrompts.addendum(persona)));
        assertEquals(Persona.STRICT_MENTOR,
                DeepSeekRequestAssembler.assemble(PromptPurpose.LEARNING_ANSWER, false, history,
                        "第二问", Persona.STRICT_MENTOR, new PromptBudget(1_000_000, 512)).persona);
    }

    @Test
    public void theRequestDescriptionCarriesIdentityButNoAddendumText() {
        DeepSeekRequest request = DeepSeekRequestAssembler.assemble(
                PromptPurpose.LEARNING_ANSWER, false, new ArrayList<DeepSeekMessage>(), "问题",
                Persona.LEARNING_REPAIR, new PromptBudget(1_000_000, 512));
        String description = request.toString();
        assertTrue(description, description.contains(Persona.LEARNING_REPAIR.wireId));
        assertFalse("the addendum is prompt body and must not be logged: " + description,
                description.contains("回答风格"));
        assertFalse(description, description.contains("补"));
    }

    private static int countRole(DeepSeekRequest request, DeepSeekMessage.Role role) {
        int count = 0;
        for (DeepSeekMessage message : request.messages) {
            if (message.role == role) {
                count++;
            }
        }
        return count;
    }
}
