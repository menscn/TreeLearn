package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.FileManagerPresenter;

import java.util.HashSet;
import java.util.Set;

/**
 * The file manager page (T-096): every stored attachment with its size, parse state, reference
 * count and physical truth, the total usage, and a confirm-first delete per row.
 *
 * <p>The screen draws and decides nothing: which rows exist, what deletion is pending and what the
 * last delete answered all arrive in one {@link FileManagerPresenter.State}. A missing file is a
 * visible mark, never a silent row and never an error dialog — the learner must be able to see the
 * gap the system cleaned.
 */
public final class FileManagerScreen
        extends LinearLayout
        implements FileManagerPresenter.Sink {

    private final LinearLayout rows;
    private final TextView usage;
    private final TextView notice;
    private final TextView empty;
    private final LinearLayout cleanupBar;
    private final TextView cleanupPrompt;
    private FileManagerPresenter actions;
    private Runnable onBack;
    private FileManagerPresenter.State state;

    public FileManagerScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        int gutter = getResources().getDimensionPixelSize(R.dimen.page_gutter);
        int bottom = getResources().getDimensionPixelSize(R.dimen.space_s8);
        setPadding(gutter, 0, gutter, bottom);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s4), 0, 0);
        ImageView back = new ImageView(context);
        back.setImageResource(R.drawable.ic_nav_back);
        back.setImageTintList(android.content.res.ColorStateList.valueOf(
                context.getColor(R.color.tl_green_deep)));
        back.setContentDescription(context.getString(R.string.cd_files_back));
        back.setLayoutParams(new LayoutParams(getResources().getDimensionPixelSize(
                R.dimen.touch_min), getResources().getDimensionPixelSize(R.dimen.touch_min)));
        back.setOnClickListener(view -> {
            if (onBack != null) {
                onBack.run();
            }
        });
        header.addView(back);
        TextView title = new TextView(context);
        title.setText(R.string.files_title);
        title.setTextColor(context.getColor(R.color.tl_ink));
        title.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_h1));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams titleLayout = new LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLayout.setMarginStart(getResources().getDimensionPixelSize(R.dimen.space_s3));
        title.setLayoutParams(titleLayout);
        header.addView(title);
        addView(header);

        usage = new TextView(context);
        usage.setTextColor(context.getColor(R.color.tl_ink3));
        usage.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_cap));
        usage.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s3), 0, 0);
        addView(usage);

        notice = new TextView(context);
        notice.setTextColor(context.getColor(R.color.tl_amber));
        notice.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
        notice.setVisibility(GONE);
        addView(notice);

        empty = new TextView(context);
        empty.setText(R.string.files_empty);
        empty.setTextColor(context.getColor(R.color.tl_ink3));
        empty.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
        empty.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s6), 0, 0);
        empty.setVisibility(GONE);
        addView(empty);

        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        addView(rows);

        cleanupBar = new LinearLayout(context);
        cleanupBar.setOrientation(HORIZONTAL);
        cleanupBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        cleanupBar.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s4), 0, 0);
        cleanupBar.setVisibility(GONE);
        cleanupPrompt = new TextView(context);
        cleanupPrompt.setTextColor(context.getColor(R.color.tl_ink2));
        cleanupPrompt.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
        cleanupPrompt.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cleanupBar.addView(cleanupPrompt);
        addView(cleanupBar);
    }

    public void setActions(FileManagerPresenter actions) {
        this.actions = actions;
    }

    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    @Override
    public void render(FileManagerPresenter.State next) {
        state = next;
        usage.setText(getContext().getString(R.string.files_usage,
                Formatter.formatShortFileSize(getContext(), next.usageBytes)));
        notice.setVisibility(next.notice == FileManagerPresenter.Notice.DELETE_FAILED
                || next.notice == FileManagerPresenter.Notice.CLEANUP_FAILED ? VISIBLE : GONE);
        if (next.notice == FileManagerPresenter.Notice.DELETE_FAILED) {
            notice.setText(R.string.files_delete_failed);
        } else if (next.notice == FileManagerPresenter.Notice.CLEANUP_FAILED) {
            notice.setText(R.string.files_cleanup_failed);
        }
        empty.setVisibility(next.rows.isEmpty() ? VISIBLE : GONE);
        rows.removeAllViews();
        Set<String> drawnConfirmations = new HashSet<>();
        for (FileManagerPresenter.Row row : next.rows) {
            rows.addView(rowView(row, next, drawnConfirmations));
        }
        renderCleanupBar(next);
    }

    /** T-098: the cleanup bar — the scan's count, then the confirm-first whole-orphan action. */
    private void renderCleanupBar(FileManagerPresenter.State next) {
        int cleanable = next.cleanableIds.size();
        if (cleanable == 0) {
            cleanupBar.setVisibility(GONE);
            return;
        }
        cleanupBar.setVisibility(VISIBLE);
        cleanupBar.removeAllViews();
        cleanupBar.addView(cleanupPrompt);
        if (next.pendingCleanupAll) {
            cleanupPrompt.setText(getContext().getString(R.string.files_cleanup_prompt, cleanable));
            cleanupBar.addView(actionButton(R.id.files_cleanup_cancel, R.string.files_cleanup_cancel,
                    () -> actions.onCleanupCancelled()));
            cleanupBar.addView(actionButton(R.id.files_cleanup_confirm, R.string.files_cleanup_confirm,
                    () -> actions.onCleanupConfirmed()));
        } else {
            cleanupPrompt.setText(getContext().getString(R.string.files_cleanup_section, cleanable));
            cleanupBar.addView(actionButton(R.id.files_cleanup_all, R.string.files_cleanup_all,
                    () -> actions.onCleanupRequested()));
        }
    }

    private View rowView(FileManagerPresenter.Row row, FileManagerPresenter.State state,
            Set<String> drawnConfirmations) {
        boolean confirming = row.attachmentId.equals(state.pendingDeleteId);
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        int gutter = getResources().getDimensionPixelSize(R.dimen.space_s4);
        card.setPadding(gutter, gutter, gutter, gutter);
        LayoutParams cardLayout = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLayout.topMargin = getResources().getDimensionPixelSize(R.dimen.space_s2);
        card.setLayoutParams(cardLayout);

        TextView name = new TextView(getContext());
        name.setText(row.displayName);
        name.setTextColor(getContext().getColor(R.color.tl_ink));
        name.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_body));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(name);

        if (!row.present) {
            TextView missing = new TextView(getContext());
            missing.setText(R.string.files_missing);
            missing.setTextColor(getContext().getColor(R.color.tl_amber));
            missing.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_cap));
            missing.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s1), 0, 0);
            card.addView(missing);
        }

        TextView meta = new TextView(getContext());
        meta.setText(getContext().getString(R.string.files_row_meta, row.mimeType,
                Formatter.formatShortFileSize(getContext(), row.sizeBytes), row.referenceCount));
        meta.setTextColor(getContext().getColor(R.color.tl_ink3));
        meta.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_cap));
        meta.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s1), 0, 0);
        card.addView(meta);

        if (state.cleanableIds.contains(row.attachmentId)) {
            TextView cleanable = new TextView(getContext());
            cleanable.setText(R.string.files_row_cleanable);
            cleanable.setTextColor(getContext().getColor(R.color.tl_amber));
            cleanable.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_cap));
            cleanable.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s1), 0, 0);
            card.addView(cleanable);
        }

        LinearLayout actionsRow = new LinearLayout(getContext());
        actionsRow.setOrientation(HORIZONTAL);
        actionsRow.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        actionsRow.setPadding(0, getResources().getDimensionPixelSize(R.dimen.space_s2), 0, 0);
        if (confirming) {
            TextView prompt = new TextView(getContext());
            prompt.setText(getContext().getString(R.string.files_delete_prompt, row.displayName));
            prompt.setTextColor(getContext().getColor(R.color.tl_ink2));
            prompt.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
            prompt.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            actionsRow.addView(prompt);
            actionsRow.addView(actionButton(R.id.files_row_cancel, R.string.files_cancel_delete,
                    () -> actions.onDeleteCancelled()));
            actionsRow.addView(actionButton(R.id.files_row_confirm, R.string.files_confirm_delete,
                    () -> actions.onDeleteConfirmed(row.attachmentId)));
        } else {
            actionsRow.addView(actionButton(R.id.files_row_delete, R.string.files_delete,
                    () -> actions.onDeleteRequested(row.attachmentId)));
        }
        card.addView(actionsRow);
        return card;
    }

    private View actionButton(int id, int label, Runnable action) {
        Button button = new Button(getContext());
        button.setId(id);
        button.setText(label);
        button.setTextSize(0, getResources().getDimensionPixelSize(R.dimen.text_sm));
        button.setAllCaps(false);
        button.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.touch_min));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                getResources().getDimensionPixelSize(R.dimen.touch_min));
        layout.setMarginStart(getResources().getDimensionPixelSize(R.dimen.space_s2));
        button.setLayoutParams(layout);
        return button;
    }
}
