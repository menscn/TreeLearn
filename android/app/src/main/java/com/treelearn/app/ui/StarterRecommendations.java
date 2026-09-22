package com.treelearn.app.ui;

import com.treelearn.core.domain.Guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The starter starting points, shown when the learner has no topic yet (PRD 6.2.17 point 3).
 *
 * <p>These are product copy, taken from the interaction authority's own starter set
 * (Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html, {@code REC_STARTER}). They are also what a
 * failed recommendation falls back to (PRD 6.2.17 exception 3), which is why they live here as a
 * value rather than inside whichever adapter happens to answer first.
 */
public final class StarterRecommendations {

    private static final String[] QUESTIONS = {
        "帮我系统理解 Transformer 的注意力机制",
        "梳理冷链物流的断链风险点",
        "从零理解向量检索的原理",
        "讲清楚 RAG 的完整链路"
    };

    private StarterRecommendations() {
    }

    /** Never empty, always {@link Recommendation.Kind#STARTER}. */
    public static List<Recommendation> all() {
        List<Recommendation> items = new ArrayList<Recommendation>(QUESTIONS.length);
        for (String question : QUESTIONS) {
            items.add(new Recommendation(Guard.nonBlank(question, "question"),
                    Recommendation.Kind.STARTER));
        }
        return Collections.unmodifiableList(items);
    }
}