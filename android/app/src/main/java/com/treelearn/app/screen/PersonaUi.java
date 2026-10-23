package com.treelearn.app.screen;

import com.treelearn.app.R;
import com.treelearn.core.domain.Persona;

/**
 * The seven answer styles as words.
 *
 * <p>Only a resource mapping - which string belongs to which enum value. The order the picker draws
 * is {@link Persona#values()}'s own, so this class can never disagree with the stored wire ids. The
 * default branch is the compile-time warning a newly added persona trips by showing the default
 * style's name, which is visible in review instead of being silent.
 */
public final class PersonaUi {

    private PersonaUi() {
    }

    public static int nameRes(Persona persona) {
        switch (persona) {
            case HAND_HOLDING:
                return R.string.persona_hand_holding;
            case STRICT_MENTOR:
                return R.string.persona_strict_mentor;
            case STUDY_BUDDY:
                return R.string.persona_study_buddy;
            case SARCASTIC_FRIEND:
                return R.string.persona_sarcastic_friend;
            case LATE_NIGHT:
                return R.string.persona_late_night;
            case LEARNING_REPAIR:
                return R.string.persona_learning_repair;
            case DEFAULT:
            default:
                return R.string.persona_default;
        }
    }

    public static int tagRes(Persona persona) {
        switch (persona) {
            case HAND_HOLDING:
                return R.string.persona_hand_holding_tag;
            case STRICT_MENTOR:
                return R.string.persona_strict_mentor_tag;
            case STUDY_BUDDY:
                return R.string.persona_study_buddy_tag;
            case SARCASTIC_FRIEND:
                return R.string.persona_sarcastic_friend_tag;
            case LATE_NIGHT:
                return R.string.persona_late_night_tag;
            case LEARNING_REPAIR:
                return R.string.persona_learning_repair_tag;
            case DEFAULT:
            default:
                return R.string.persona_default_tag;
        }
    }

    /** The one-sentence positioning shown only on the introduction page, never in the picker. */
    public static int subRes(Persona persona) {
        switch (persona) {
            case HAND_HOLDING:
                return R.string.persona_hand_holding_sub;
            case STRICT_MENTOR:
                return R.string.persona_strict_mentor_sub;
            case STUDY_BUDDY:
                return R.string.persona_study_buddy_sub;
            case SARCASTIC_FRIEND:
                return R.string.persona_sarcastic_friend_sub;
            case LATE_NIGHT:
                return R.string.persona_late_night_sub;
            case LEARNING_REPAIR:
                return R.string.persona_learning_repair_sub;
            case DEFAULT:
            default:
                return R.string.persona_default_sub;
        }
    }

    /** The static demo answer; design copy, not a promise about generated answers. */
    public static int demoRes(Persona persona) {
        switch (persona) {
            case HAND_HOLDING:
                return R.string.persona_hand_holding_demo;
            case STRICT_MENTOR:
                return R.string.persona_strict_mentor_demo;
            case STUDY_BUDDY:
                return R.string.persona_study_buddy_demo;
            case SARCASTIC_FRIEND:
                return R.string.persona_sarcastic_friend_demo;
            case LATE_NIGHT:
                return R.string.persona_late_night_demo;
            case LEARNING_REPAIR:
                return R.string.persona_learning_repair_demo;
            case DEFAULT:
            default:
                return R.string.persona_default_demo;
        }
    }
}
