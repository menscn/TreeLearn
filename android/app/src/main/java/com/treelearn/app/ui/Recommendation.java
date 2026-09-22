package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

/**
 * One chip of the home recommendation strip (PRD 6.2.17).
 *
 * <p>{@link #question} is what a tap sends as the root question, verbatim: PRD 6.2.17 point 4 makes a
 * tapped recommendation equivalent to typing the question, so the presenter must not rewrite it into
 * an instruction of its own. {@link #kind} only decides which tag the chip shows.
 */
public final class Recommendation {

    /** The three tags the PRD allows. There is no fourth and no default. */
    public enum Kind {
        /** 入门起点 - shown when the learner has no topic yet. */
        STARTER,
        /** 顺着你学的 - an extension of what is already stored locally. */
        FOLLOW_UP,
        /** 换个方向 - a direction different from what is already stored locally. */
        NEW_DIRECTION
    }

    public final String question;
    public final Kind kind;

    public Recommendation(String question, Kind kind) {
        this.question = Guard.nonBlank(question, "question");
        this.kind = Guard.object(kind, "kind");
    }
}