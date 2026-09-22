package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;

/**
 * T-076: the composer's per-branch work, kept where a process rebuild can find it again (073 §5).
 *
 * <p>One record per branch, holding exactly four facts - the draft text, the quoted sentence and
 * the two composer preferences. It holds no key material and no message body: the draft is the
 * learner's unsent input, not content, and the preferences are already non-sensitive by the
 * settings store's own contract. An archived branch keeps its record - archiving is read-only.
 */
public interface ChatDraftStore {

    /** A presenter built without persistence (tests, previews) loses nothing but the rebuild. */
    ChatDraftStore NOOP = new ChatDraftStore() {
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

    /** Overwrites the branch's record; an accepted send is followed by {@link #clear}. */
    void save(String branchId, ChatDraft draft);

    /** @return the branch's record, or null when nothing was kept */
    ChatDraft load(String branchId);

    /** Drops the branch's record; only an accepted send may ask for this. */
    void clear(String branchId);

    /** What a rebuild restores: the words, the quote and the composer's two preferences. */
    final class ChatDraft {

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
