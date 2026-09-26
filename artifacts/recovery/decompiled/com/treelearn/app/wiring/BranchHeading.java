/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.wiring;

final class BranchHeading {
    private BranchHeading() {
    }

    static String of(String derivedTitle, String firstQuestion) {
        if (derivedTitle != null && !derivedTitle.trim().isEmpty()) {
            return derivedTitle;
        }
        if (firstQuestion != null && !firstQuestion.trim().isEmpty()) {
            return firstQuestion;
        }
        return "";
    }
}

