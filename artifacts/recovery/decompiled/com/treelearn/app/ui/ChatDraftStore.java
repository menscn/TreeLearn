/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;

public interface ChatDraftStore {
    public static final ChatDraftStore NOOP = new ChatDraftStore(){

        @Override
        public void save(String branchId, ChatDraft draft) {
        }

        @Override
        public ChatDraft load(String branchId) {
            return null;
        }

        @Override
        public void clear(String branchId) {
        }
    };

    public void save(String var1, ChatDraft var2);

    public ChatDraft load(String var1);

    public void clear(String var1);

    public static final class ChatDraft {
        public final String text;
        public final String quote;
        public final GenerationModel model;
        public final boolean thinkingEnabled;

        public ChatDraft(String text, String quote, GenerationModel model, boolean thinkingEnabled) {
            this.text = text == null ? "" : text;
            this.quote = quote == null ? "" : quote;
            this.model = model == null ? GenerationModel.FLASH : model;
            this.thinkingEnabled = thinkingEnabled;
        }
    }
}

