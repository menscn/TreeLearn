package com.treelearn.ai.prompt;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Persona;

/**
 * The static policy block each answer style appends to the main-answer system body
 * (SPEC-2026-09-21-ai-persona-preference 接口; prompt design §3.2, §4.1).
 *
 * <p>It is a separate table rather than part of {@link PromptBodiesV1} for one reason: the baseline
 * tutor body must not move. The default style appends nothing, so every existing answer remains
 * byte-identical to what the registry produced before the persona feature existed, and the policy
 * version stays {@code tl-learning-answer/1.1.0}.
 *
 * <p>These strings are policy text, addressed to the model. The picker's labels are UI copy and never
 * appear here: the prompt must not depend on how a screen chooses to name a style, and a changed
 * label must never change what the model is told.
 */
public final class PersonaPrompts {

    private PersonaPrompts() {
    }

    /**
     * @return the addendum for {@code persona}; the empty string for the default style, which is why
     *         composing with it is a no-op
     * @throws IllegalArgumentException when {@code persona} is null, per the error-channel rule in
     *         {@link Guard}
     */
    public static String addendum(Persona persona) {
        Guard.object(persona, "persona");
        return switch (persona) {
            case DEFAULT -> "";
            case HAND_HOLDING ->
                    "回答风格：把学习者当作刚刚接触这个主题的人。一步一步地演示，每一步都给出可以立刻照做的动作，"
                            + "先确认上一步已经清楚，再引入新内容；不使用未经解释的术语。";
            case STRICT_MENTOR ->
                    "回答风格：要求准确、完整、可检验。直接指出不严谨或有遗漏之处，给出正确表述和自检方法；"
                            + "不因为照顾情绪而降低标准，也不添加与问题无关的鼓励。";
            case STUDY_BUDDY ->
                    "回答风格：以一起学习的同伴身份推进。先说出你的思路和判断，再邀请学习者补充或质疑，"
                            + "用共同解题的语气，而不是单向讲授的语气。";
            case SARCASTIC_FRIEND ->
                    "回答风格：语气可以带一点损友式的调侃，但事实必须准确、结论必须有用；"
                            + "调侃只针对情境或常见误区，不针对学习者本人。";
            case LATE_NIGHT ->
                    "回答风格：学习者可能在疲惫的深夜学习。句子更短，一次只推进一个要点，"
                            + "重要结论放在最前面，避免大段铺陈和额外的背景介绍。";
            case LEARNING_REPAIR ->
                    "回答风格：先定位学习者的理解断在哪里，用一个最小例子把缺口填上，再回到原问题；"
                            + "不要只是重复一个正确答案。";
        };
    }
}
