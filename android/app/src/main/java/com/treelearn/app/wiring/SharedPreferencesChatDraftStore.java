package com.treelearn.app.wiring;

import android.content.Context;
import android.content.SharedPreferences;

import com.treelearn.app.ui.ChatDraftStore;
import com.treelearn.core.domain.GenerationModel;

/**
 * T-076: the draft store on {@code SharedPreferences}, one namespaced key group per branch (073 §5).
 *
 * <p>Four facts per branch - draft text, quote, and the two composer preferences - and nothing
 * else: no key material ever passes through here, and no message body either, because a draft is
 * the learner's unsent input rather than content. Writes use {@code apply()}, so a keystroke never
 * blocks the UI thread; a killed process loses at most the very last keystroke.
 */
public final class SharedPreferencesChatDraftStore implements ChatDraftStore {

    private static final String PREFIX = "chat_draft_";

    private final SharedPreferences prefs;

    public SharedPreferencesChatDraftStore(Context context) {
        this.prefs = context.getSharedPreferences("treelearn_chat_drafts", Context.MODE_PRIVATE);
    }

    @Override
    public void save(String branchId, ChatDraft draft) {
        prefs.edit()
                .putString(key(branchId, "text"), draft.text)
                .putString(key(branchId, "quote"), draft.quote)
                .putString(key(branchId, "model"), draft.model.name())
                .putBoolean(key(branchId, "thinking"), draft.thinkingEnabled)
                .apply();
    }

    @Override
    public ChatDraft load(String branchId) {
        String text = prefs.getString(key(branchId, "text"), "");
        String quote = prefs.getString(key(branchId, "quote"), "");
        if (text.isEmpty() && quote.isEmpty()) {
            return null;
        }
        return new ChatDraft(text, quote, model(prefs.getString(key(branchId, "model"), "")),
                prefs.getBoolean(key(branchId, "thinking"), false));
    }

    @Override
    public void clear(String branchId) {
        prefs.edit()
                .remove(key(branchId, "text"))
                .remove(key(branchId, "quote"))
                .remove(key(branchId, "model"))
                .remove(key(branchId, "thinking"))
                .apply();
    }

    /** An unrecognised stored name falls back to the default tier instead of crashing a rebuild. */
    private static GenerationModel model(String name) {
        try {
            return GenerationModel.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return GenerationModel.FLASH;
        }
    }

    private static String key(String branchId, String field) {
        return PREFIX + branchId + "_" + field;
    }
}
