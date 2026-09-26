/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.content.res.ColorStateList
 *  android.text.format.DateUtils
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.widget.LinearLayout
 *  android.widget.TextView
 *  com.treelearn.app.R$color
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.library.LibraryPresenter;
import com.treelearn.app.library.LibraryViewState;
import java.util.function.Consumer;

public final class LibraryScreen
extends LinearLayout {
    private final LinearLayout rows;
    private final TextView state;
    private final TextView segmentInProgress;
    private final TextView segmentDone;
    private LibraryPresenter actions;

    public LibraryScreen(Context context) {
        super(context);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.screen_library, (ViewGroup)this, true);
        this.rows = (LinearLayout)this.findViewById(R.id.library_rows);
        this.state = (TextView)this.findViewById(R.id.library_state);
        this.segmentInProgress = (TextView)this.findViewById(R.id.library_segment_in_progress);
        this.segmentDone = (TextView)this.findViewById(R.id.library_segment_done);
        this.segmentInProgress.setOnClickListener(view -> this.onSegment(LibraryViewState.Segment.IN_PROGRESS));
        this.segmentDone.setOnClickListener(view -> this.onSegment(LibraryViewState.Segment.DONE));
    }

    public void setActions(LibraryPresenter value) {
        this.actions = value;
    }

    public void render(LibraryViewState next) {
        boolean done = next.segment == LibraryViewState.Segment.DONE;
        this.drawSegments(done);
        boolean listed = next.phase == LibraryViewState.Phase.READY && !(!done ? next.rows.isEmpty() : next.archived.isEmpty());
        this.rows.setVisibility(listed ? 0 : 8);
        this.state.setVisibility(listed ? 8 : 0);
        if (!listed) {
            this.state.setText(this.idleText(next, done));
        }
        this.rows.removeAllViews();
        if (done) {
            this.renderArchived(next);
        } else {
            this.renderTopics(next);
        }
    }

    private void drawSegments(boolean done) {
        this.tint(this.segmentInProgress, !done);
        this.tint(this.segmentDone, done);
    }

    private void tint(TextView pill, boolean selected) {
        Context context = this.getContext();
        pill.setBackgroundTintList(ColorStateList.valueOf((int)context.getColor(selected ? R.color.tl_green_soft : R.color.tl_bg)));
        pill.setTextColor(context.getColor(selected ? R.color.tl_green_deep : R.color.tl_ink3));
    }

    private void renderTopics(LibraryViewState next) {
        LayoutInflater inflater = LayoutInflater.from((Context)this.getContext());
        for (LibraryViewState.Row row : next.rows) {
            View view = inflater.inflate(R.layout.item_library_topic, (ViewGroup)this.rows, false);
            ((TextView)view.findViewById(R.id.library_topic_title)).setText((CharSequence)row.title);
            ((TextView)view.findViewById(R.id.library_topic_meta)).setText(this.meta(row));
            view.findViewById(R.id.library_topic_unread).setVisibility(row.unreadCount > 0L ? 0 : 8);
            view.setOnClickListener(tapped -> {
                if (this.actions != null) {
                    this.actions.onTopicTapped(row);
                }
            });
            this.rows.addView(view);
        }
    }

    private void renderArchived(LibraryViewState next) {
        LayoutInflater inflater = LayoutInflater.from((Context)this.getContext());
        for (LibraryViewState.ArchiveRow row : next.archived) {
            View view = inflater.inflate(R.layout.item_library_archived, (ViewGroup)this.rows, false);
            ((TextView)view.findViewById(R.id.library_done_title)).setText((CharSequence)row.title);
            ((TextView)view.findViewById(R.id.library_done_mark)).setText(R.string.done_archived_mark);
            ((TextView)view.findViewById(R.id.library_done_meta)).setText((CharSequence)row.sourcePath);
            boolean summarised = row.summary != null && !row.summary.trim().isEmpty();
            TextView summary = (TextView)view.findViewById(R.id.library_done_summary);
            summary.setVisibility(summarised ? 0 : 8);
            if (summarised) {
                summary.setText((CharSequence)row.summary);
            }
            boolean pending = next.pendingDelete != null && next.pendingDelete.targetBranchId.equals(row.branchId);
            TextView prompt = (TextView)view.findViewById(R.id.library_done_prompt);
            prompt.setVisibility(pending ? 0 : 8);
            view.findViewById(R.id.library_done_actions).setVisibility(pending ? 0 : 8);
            if (pending) {
                prompt.setText((CharSequence)this.getContext().getString(R.string.done_delete_prompt, new Object[]{row.title, next.pendingDelete.branchCount()}));
            }
            view.findViewById(R.id.library_done_restore).setOnClickListener(tapped -> this.fire(presenter -> presenter.onRestoreRequested(row)));
            view.findViewById(R.id.library_done_delete).setOnClickListener(tapped -> this.fire(presenter -> presenter.onDeleteRequested(row)));
            view.findViewById(R.id.library_done_cancel).setOnClickListener(tapped -> this.fire(LibraryPresenter::onDeleteCancelled));
            view.findViewById(R.id.library_done_confirm).setOnClickListener(tapped -> this.fire(LibraryPresenter::onDeleteConfirmed));
            this.rows.addView(view);
        }
    }

    private CharSequence meta(LibraryViewState.Row row) {
        CharSequence when = DateUtils.getRelativeTimeSpanString((long)row.lastUsedAt, (long)System.currentTimeMillis(), (long)60000L);
        String line = this.getContext().getString(R.string.library_topic_meta, new Object[]{row.branchCount, when});
        return row.unreadCount == 0L ? line : line + " \u00b7 " + this.getContext().getString(R.string.library_topic_unread, new Object[]{row.unreadCount});
    }

    private CharSequence idleText(LibraryViewState next, boolean done) {
        switch (next.phase) {
            case ERROR: {
                return this.getContext().getString(R.string.library_error);
            }
            case LOADING: {
                return this.getContext().getString(R.string.library_loading);
            }
        }
        return done ? this.getContext().getString(R.string.done_empty) + "\n" + this.getContext().getString(R.string.done_empty_guide) : this.getContext().getString(R.string.library_empty) + "\n" + this.getContext().getString(R.string.library_empty_guide);
    }

    private void onSegment(LibraryViewState.Segment segment) {
        this.fire(presenter -> presenter.onSegmentSelected(segment));
    }

    private void fire(Consumer<LibraryPresenter> gesture) {
        if (this.actions != null) {
            gesture.accept(this.actions);
        }
    }
}

