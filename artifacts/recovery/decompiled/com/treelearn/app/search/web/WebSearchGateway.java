/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.search.web;

import com.treelearn.app.ui.SearchSettingsPresenter;
import java.util.Collections;
import java.util.List;

public interface WebSearchGateway {
    public Outcome search(SearchSettingsPresenter.Engine var1, char[] var2, String var3, int var4);

    public static final class Outcome {
        public static final Outcome EMPTY = new Outcome(Status.EMPTY, Collections.emptyList());
        public static final Outcome FAILED = new Outcome(Status.FAILED, Collections.emptyList());
        public final Status status;
        public final List<Item> items;

        private Outcome(Status status, List<Item> items) {
            this.status = status;
            this.items = Collections.unmodifiableList(items);
        }

        public Status status() {
            return this.status;
        }

        public static Outcome ok(List<Item> items) {
            return new Outcome(Status.OK, items);
        }

        public static Outcome empty() {
            return EMPTY;
        }

        public static Outcome failed() {
            return FAILED;
        }
    }

    public static final class Item {
        public final int index;
        public final String title;
        public final String url;
        public final String domain;
        public final String snippet;

        public Item(int index, String title, String url, String domain, String snippet) {
            this.index = index;
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
            this.domain = domain == null ? "" : domain;
            this.snippet = snippet == null ? "" : snippet;
        }
    }

    public static enum Status {
        OK,
        EMPTY,
        FAILED;

    }
}

