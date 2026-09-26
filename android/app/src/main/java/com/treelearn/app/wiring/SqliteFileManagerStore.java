package com.treelearn.app.wiring;

import com.treelearn.app.files.AttachmentStore;
import com.treelearn.app.ui.FileManagerPresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The file manager's real data path (T-096): one read and one delete, both delegated to the
 * {@link AttachmentStore}, which owns the private directory and the two attachment tables.
 *
 * <p>Nothing here second-guesses the store's deletion rule — records and files go, reference rows
 * survive as the missing annotation — because a second copy of that rule is exactly how the file
 * manager and the message renderer would come to disagree about what a missing attachment is.
 */
public final class SqliteFileManagerStore implements FileManagerPresenter.Store {

    private final AttachmentStore attachments;

    public SqliteFileManagerStore(AttachmentStore attachments) {
        this.attachments = attachments;
    }

    @Override
    public List<FileManagerPresenter.Row> list() {
        List<FileManagerPresenter.Row> rows = new ArrayList<>();
        for (AttachmentStore.Entry entry : attachments.list()) {
            rows.add(new FileManagerPresenter.Row(entry.attachmentId, entry.displayName,
                    entry.mimeType, entry.sizeBytes, entry.parseState, entry.present,
                    entry.referenceCount));
        }
        return rows;
    }

    @Override
    public long usageBytes() {
        return attachments.usageBytes();
    }

    @Override
    public void delete(String attachmentId) {
        attachments.deleteAttachment(attachmentId);
    }

    @Override
    public java.util.Set<String> orphanIds() {
        return new java.util.HashSet<>(attachments.orphans());
    }

    @Override
    public void cleanupOrphans(java.util.Set<String> candidateIds) {
        attachments.cleanupOrphans(candidateIds);
    }
}
