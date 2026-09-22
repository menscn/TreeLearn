package com.treelearn.app.screen;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.TextSelection;
import com.treelearn.app.ui.WordMenu;

/**
 * The four-item menu a long press opens, and the sentence it acts on (PRD 6.2.19 points 1-2, 8).
 *
 * <p><strong>It paints and applies; it never judges.</strong> Which items exist and which of them are
 * pressable comes from {@link WordMenu}, which reads every flag from the answer's own predicate, and
 * where the menu may land comes from there too. This class adds only what needs a window: the
 * measurement of the card and the coordinates of the pressed sentence.
 *
 * <p>The menu applies the sentence to the answer's own text view when it opens, and 全选 widens that
 * same selection in place rather than remembering a second one. So the highlight the learner sees, the
 * range {@link TextView#getSelectionStart()} reports and the passage 追问 and 复制 hand over are one
 * value, and the existing selection-reading path stays the only reader.
 */
public final class WordMenuPopup {

    /** The three gestures that leave the menu; 全选 only changes the selection and is answered here. */
    public interface Listener {

        /** 追问: take the sentence as the context of the next question. */
        void onQuoteRequested();

        /** 新建分支: open a branch from this answer. */
        void onForkRequested();

        /** 复制: put the selected passage on the clipboard. */
        void onCopyRequested();
    }

    /** How far a row that is drawn but not pressable is faded - 追问 while the answer is unfinished. */
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
        LinearLayout content = (LinearLayout) LayoutInflater.from(answer.getContext())
                .inflate(R.layout.popup_word_action, null, false);
        selectionLabel = content.findViewById(R.id.word_menu_selection);
        quoteRow = content.findViewById(R.id.word_menu_quote);
        forkRow = content.findViewById(R.id.word_menu_fork);
        copyRow = content.findViewById(R.id.word_menu_copy);
        selectAllRow = content.findViewById(R.id.word_menu_select_all);
        copyRow.setContentDescription(answer.getContext().getString(R.string.chat_word_copy_desc));
        selectAllRow.setContentDescription(
                answer.getContext().getString(R.string.chat_word_select_all_desc));
        quoteRow.setOnClickListener(row -> choose(listener::onQuoteRequested));
        forkRow.setOnClickListener(row -> choose(listener::onForkRequested));
        copyRow.setOnClickListener(row -> choose(listener::onCopyRequested));
        selectAllRow.setOnClickListener(row -> selectAll());
        popup = new PopupWindow(content, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(answer.getResources().getDimension(R.dimen.card_elevation));
    }

    /** Opens the menu on one sentence, placed so that it never covers the sentence it acts on. */
    public void show(TextSelection selection) {
        apply(selection);
        bind();
        View content = popup.getContentView();
        content.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] origin = new int[2];
        answer.getLocationInWindow(origin);
        View window = answer.getRootView();
        int[] box = selectionBox(selection);
        popup.setWidth(content.getMeasuredWidth());
        popup.showAtLocation(answer, Gravity.NO_GRAVITY,
                Math.round(WordMenu.leftFor(origin[0] + (box[0] + box[2]) / 2f,
                        content.getMeasuredWidth(), window.getWidth())),
                Math.round(WordMenu.topFor(origin[1] + box[1], origin[1] + box[3],
                        content.getMeasuredHeight(), window.getHeight())));
    }

    /** Closes the menu; a menu that was never shown, or is already gone, stays closed. */
    public void close() {
        if (popup.isShowing()) {
            popup.dismiss();
        }
    }

    /**
     * The pressed sentence's box inside the answer, as left, top, right and bottom. The top is the
     * first line it spans and the bottom the last, so the menu's own placement clears the whole
     * sentence rather than only the line the press landed on.
     */
    private int[] selectionBox(TextSelection selection) {
        Layout layout = answer.getLayout();
        if (layout == null || selection.isEmpty()) {
            return new int[] {0, 0, answer.getWidth(), answer.getHeight()};
        }
        int firstLine = layout.getLineForOffset(selection.start);
        int lastLine = layout.getLineForOffset(selection.end);
        int paddingLeft = answer.getTotalPaddingLeft();
        int paddingTop = answer.getTotalPaddingTop();
        return new int[] {
                (int) layout.getPrimaryHorizontal(selection.start) + paddingLeft - answer.getScrollX(),
                layout.getLineTop(firstLine) + paddingTop - answer.getScrollY(),
                (int) layout.getPrimaryHorizontal(selection.end) + paddingLeft - answer.getScrollX(),
                layout.getLineBottom(lastLine) + paddingTop - answer.getScrollY()};
    }

    /** Paints the label and the four rows from the selection the answer reports right now. */
    private void bind() {
        String selected = current().read(answer.getText().toString());
        selectionLabel.setText(answer.getContext()
                .getString(R.string.chat_word_selection, selected));
        forkRow.setVisibility(View.GONE);
        for (WordMenu.Entry entry : WordMenu.entriesFor(message, selected)) {
            TextView row = row(entry.item);
            row.setVisibility(View.VISIBLE);
            row.setEnabled(entry.enabled);
            row.setAlpha(entry.enabled ? 1f : DISABLED_ALPHA);
        }
    }

    /** 全选 takes the whole answer as the selection, and the menu then offers what that enables. */
    private void selectAll() {
        apply(TextSelection.whole(answer.getText().length()));
        bind();
    }

    private void choose(Runnable gesture) {
        close();
        gesture.run();
    }

    /** The selection the answer's own text view holds: the only range any item is derived from. */
    private TextSelection current() {
        return TextSelection.of(answer.getSelectionStart(), answer.getSelectionEnd());
    }

    /**
     * Hands the range to the view. A text view that is selectable keeps its text in a {@link Spannable},
     * which is what makes the highlight visible; a view that does not is left alone rather than crashed.
     */
    private void apply(TextSelection selection) {
        CharSequence text = answer.getText();
        if (text instanceof Spannable) {
            Selection.setSelection((Spannable) text, selection.start, selection.end);
        }
    }

    private TextView row(WordMenu.Item item) {
        switch (item) {
            case QUOTE:
                return quoteRow;
            case FORK:
                return forkRow;
            case COPY:
                return copyRow;
            default:
                return selectAllRow;
        }
    }
}
