/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.Guard
 */
package com.treelearn.app.ui;

import com.treelearn.app.ui.Recommendation;
import com.treelearn.core.domain.Guard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StarterRecommendations {
    private static final String[] QUESTIONS = new String[]{"\u5e2e\u6211\u7cfb\u7edf\u7406\u89e3 Transformer \u7684\u6ce8\u610f\u529b\u673a\u5236", "\u68b3\u7406\u51b7\u94fe\u7269\u6d41\u7684\u65ad\u94fe\u98ce\u9669\u70b9", "\u4ece\u96f6\u7406\u89e3\u5411\u91cf\u68c0\u7d22\u7684\u539f\u7406", "\u8bb2\u6e05\u695a RAG \u7684\u5b8c\u6574\u94fe\u8def"};

    private StarterRecommendations() {
    }

    public static List<Recommendation> all() {
        ArrayList<Recommendation> items = new ArrayList<Recommendation>(QUESTIONS.length);
        for (String question : QUESTIONS) {
            items.add(new Recommendation(Guard.nonBlank((String)question, (String)"question"), Recommendation.Kind.STARTER));
        }
        return Collections.unmodifiableList(items);
    }
}

