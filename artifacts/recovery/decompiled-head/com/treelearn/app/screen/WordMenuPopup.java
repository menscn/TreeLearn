/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.graphics.drawable.ColorDrawable
 *  android.graphics.drawable.Drawable
 *  android.text.Layout
 *  android.text.Selection
 *  android.text.Spannable
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.View$MeasureSpec
 *  android.widget.LinearLayout
 *  android.widget.PopupWindow
 *  android.widget.TextView
 *  com.treelearn.app.R$dimen
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.TextSelection;
import com.treelearn.app.ui.WordMenu;

public final class WordMenuPopup {
    private static final float DISABLED_ALPHA = 0.4f;
    private final TextView answer;
    private final ChatMessage message;
    private final Listener listener;
    private final TextView selectionLabel;
    private final TextView quoteRow;
    private final TextView forkRow;
    private final TextView copyRow;
    private final TextView selectAllRow;
    private final PopupWindow popup;

    public WordMenuPopup(TextView answer, ChatMessage message, Listener listener) {
        this.answer = answer;
        this.message = message;
        this.listener = listener;
        LinearLayout content = (LinearLayout)LayoutInflater.from((Context)answer.getContext()).inflate(R.layout.popup_word_action, null, false);
        this.selectionLabel = (TextView)content.findViewById(R.id.word_menu_selection);
        this.quoteRow = (TextView)content.findViewById(R.id.word_menu_quote);
        this.forkRow = (TextView)content.findViewById(R.id.word_menu_fork);
        this.copyRow = (TextView)content.findViewById(R.id.word_menu_copy);
        this.selectAllRow = (TextView)content.findViewById(R.id.word_menu_select_all);
        this.copyRow.setContentDescription((CharSequence)answer.getContext().getString(R.string.chat_word_copy_desc));
        this.selectAllRow.setContentDescription((CharSequence)answer.getContext().getString(R.string.chat_word_select_all_desc));
        this.quoteRow.setOnClickListener(row -> this.choose(listener::onQuoteRequested));
        this.forkRow.setOnClickListener(row -> this.choose(listener::onForkRequested));
        this.copyRow.setOnClickListener(row -> this.choose(listener::onCopyRequested));
        this.selectAllRow.setOnClickListener(row -> this.selectAll());
        this.popup = new PopupWindow((View)content, -2, -2, true);
        this.popup.setBackgroundDrawable((Drawable)new ColorDrawable(0));
        this.popup.setOutsideTouchable(true);
        this.popup.setElevation(answer.getResources().getDimension(R.dimen.card_elevation));
    }

    public void show(TextSelection selection) {
        this.apply(selection);
        this.bind();
        View content = this.popup.getContentView();
        content.measure(View.MeasureSpec.makeMeasureSpec((int)0, (int)0), View.MeasureSpec.makeMeasureSpec((int)0, (int)0));
        int[] origin = new int[2];
        this.answer.getLocationInWindow(origin);
        View window = this.answer.getRootView();
        int[] box = this.selectionBox(selection);
        this.popup.setWidth(content.getMeasuredWidth());
        this.popup.showAtLocation((View)this.answer, 0, Math.round(WordMenu.leftFor((float)origin[0] + (float)(box[0] + box[2]) / 2.0f, content.getMeasuredWidth(), window.getWidth())), Math.round(WordMenu.topFor(origin[1] + box[1], origin[1] + box[3], content.getMeasuredHeight(), window.getHeight())));
    }

    public void close() {
        if (this.popup.isShowing()) {
            this.popup.dismiss();
        }
    }

    private int[] selectionBox(TextSelection selection) {
        Layout layout2 = this.answer.getLayout();
        if (layout2 == null || selection.isEmpty()) {
            return new int[]{0, 0, this.answer.getWidth(), this.answer.getHeight()};
        }
        int firstLine = layout2.getLineForOffset(selection.start);
        int lastLine = layout2.getLineForOffset(selection.end);
        int paddingLeft = this.answer.getTotalPaddingLeft();
        int paddingTop = this.answer.getTotalPaddingTop();
        return new int[]{(int)layout2.getPrimaryHorizontal(selection.start) + paddingLeft - this.answer.getScrollX(), layout2.getLineTop(firstLine) + paddingTop - this.answer.getScrollY(), (int)layout2.getPrimaryHorizontal(selection.end) + paddingLeft - this.answer.getScrollX(), layout2.getLineBottom(lastLine) + paddingTop - this.answer.getScrollY()};
    }

    private void bind() {
        String selected = this.current().read(this.answer.getText().toString());
        this.selectionLabel.setText((CharSequence)this.answer.getContext().getString(R.string.chat_word_selection, new Object[]{selected}));
        this.forkRow.setVisibility(8);
        for (WordMenu.Entry entry : WordMenu.entriesFor(this.message, selected)) {
            TextView row = this.row(entry.item);
            row.setVisibility(0);
            row.setEnabled(entry.enabled);
            row.setAlpha(entry.enabled ? 1.0f : 0.4f);
        }
    }

    private void selectAll() {
        this.apply(TextSelection.whole(this.answer.getText().length()));
        this.bind();
    }

    private void choose(Runnable gesture) {
        this.close();
        gesture.run();
    }

    private TextSelection current() {
        return TextSelection.of(this.answer.getSelectionStart(), this.answer.getSelectionEnd());
    }

    private void apply(TextSelection selection) {
        CharSequence text = this.answer.getText();
        if (text instanceof Spannable) {
            Selection.setSelection((Spannable)((Spannable)text), (int)selection.start, (int)selection.end);
        }
    }

    private TextView row(WordMenu.Item item) {
        switch (item) {
            case QUOTE: {
                return this.quoteRow;
            }
            case FORK: {
                return this.forkRow;
            }
            case COPY: {
                return this.copyRow;
            }
        }
        return this.selectAllRow;
    }

    public static interface Listener {
        public void onQuoteRequested();

        public void onForkRequested();

        public void onCopyRequested();
    }
}

