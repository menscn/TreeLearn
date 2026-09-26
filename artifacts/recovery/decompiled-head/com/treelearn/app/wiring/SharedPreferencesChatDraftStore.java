/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.content.SharedPreferences
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.wiring;

import android.content.Context;
import android.content.SharedPreferences;
import com.treelearn.app.ui.ChatDraftStore;
import com.treelearn.core.domain.GenerationModel;

public final class SharedPreferencesChatDraftStore
implements ChatDraftStore {
    private static final String PREFIX = "chat_draft_";
    private final SharedPreferences prefs;

    public SharedPreferencesChatDraftStore(Context context) {
        this.prefs = context.getSharedPreferences("treelearn_chat_drafts", 0);
    }

    @Override
    public void save(String branchId, ChatDraftStore.ChatDraft draft) {
        this.prefs.edit().putString(SharedPreferencesChatDraftStore.key(branchId, "text"), draft.text).putString(SharedPreferencesChatDraftStore.key(branchId, "quote"), draft.quote).putString(SharedPreferencesChatDraftStore.key(branchId, "model"), draft.model.name()).putBoolean(SharedPreferencesChatDraftStore.key(branchId, "thinking"), draft.thinkingEnabled).apply();
    }

    @Override
    public ChatDraftStore.ChatDraft load(String branchId) {
        String text = this.prefs.getString(SharedPreferencesChatDraftStore.key(branchId, "text"), "");
        String quote = this.prefs.getString(SharedPreferencesChatDraftStore.key(branchId, "quote"), "");
        if (text.isEmpty() && quote.isEmpty()) {
            return null;
        }
        return new ChatDraftStore.ChatDraft(text, quote, SharedPreferencesChatDraftStore.model(this.prefs.getString(SharedPreferencesChatDraftStore.key(branchId, "model"), "")), this.prefs.getBoolean(SharedPreferencesChatDraftStore.key(branchId, "thinking"), false));
    }

    @Override
    public void clear(String branchId) {
        this.prefs.edit().remove(SharedPreferencesChatDraftStore.key(branchId, "text")).remove(SharedPreferencesChatDraftStore.key(branchId, "quote")).remove(SharedPreferencesChatDraftStore.key(branchId, "model")).remove(SharedPreferencesChatDraftStore.key(branchId, "thinking")).apply();
    }

    private static GenerationModel model(String name) {
        try {
            return GenerationModel.valueOf((String)name);
        }
        catch (IllegalArgumentException unknown) {
            return GenerationModel.FLASH;
        }
    }

    private static String key(String branchId, String field) {
        return PREFIX + branchId + "_" + field;
    }
}

