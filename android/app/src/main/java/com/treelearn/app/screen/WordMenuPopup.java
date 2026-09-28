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
 * The four-item menu the app draws over an answer's selected passage, and the sentence it acts on
 * (PRD 6.2.19 points 1-2, 8).
 *
 * <p><strong>It paints and applies; it never judges.</strong> Which items exist and which of them are
 * pressable comes from {@link WordMenu}, which reads every flag from the answer's own predicate, and
 * where the menu may land comes from there too. This class adds only what needs a window: the
 * measurement of the card and the coordinates of the selected passage.
 *
 * <p><strong>It is the menu, not a second one.</strong> The screen empties the platform's own
 * selection toolbar and shows this card instead, because a platform menu can neither be ordered nor
 * kept out of its overflow sheet by the app - which is how 新建主题 ended up behind a 更多 sheet while
 * 全选 / 复制 / 翻译 held the visible slots. The rows here, and their order, are therefore the only
 * menu the learner sees.
 *
 * <p>The menu applies the passage to the answer's own text view when it opens, and 全选 widens that
 * same selection in place rather than remembering a second one. So the highlight the learner sees, the
 * range {@link TextView#getSelectionStart()} reports and the passage 追问 and 复制 hand over are one
 * value, and the existing selection-reading path stays the only reader.
 */
public final class WordMenuPopup {

    /** The three gestures that leave the menu; 全选 only changes the selection and is answered here. */
    public interface Listener {

        /** 追问: take the sentence as the context of the next question. */
        void onQuoteRequested();

        /** 新建主题: open a branch from this answer. */
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
        // Not focusable on purpose: the card must not take window focus away from the answer whose
        // selection it acts on. A transparent background plus outside-touch still dismisses it, and on
        // a non-focusable popup that outside touch also reaches the answer, so tapping away ends the
        // selection in the same gesture instead of leaving handles behind with no menu.
        popup = new PopupWindow(content, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(answer.getResources().getDimension(R.dimen.card_elevation));
    }

    /**
     * Told when the card goes away for any reason - the learner chose a row, tapped outside it, or the
     * selection it was drawn over ended. The screen ends the platform selection with it, so the handles
     * and the menu appear and disappear as one thing.
     */
    public void setOnClosed(Runnable closed) {
        popup.setOnDismissListener(() -> closed.run());
    }

    /** Opens the menu on one sentence, placed so that it never covers the sentence it acts on. */
    public void show(TextSelection selection) {
        apply(selection);
        bind();
        place(selection, false);
    }

    /**
     * Redraws the open card over the selection as it stands now. Dragging a handle moves the selection
     * without closing the action mode, and the platform's own toolbar follows it while it moves; this is
     * what makes the card, and the passage it names, do the same instead of labelling a sentence the
     * learner has already changed. A card that is not showing, or whose selection has gone, stays as it
     * is - or closes, when there is nothing left to act on.
     */
    public void refresh() {
        if (!popup.isShowing()) {
            return;
        }
        TextSelection now = TextSelection.of(answer.getSelectionStart(), answer.getSelectionEnd());
        if (now.isEmpty()) {
            close();
            return;
        }
        bind();
        place(now, true);
    }

    /** Where the card lands for one passage: sized by itself, centred on the passage, clamped inside. */
    private void place(TextSelection selection, boolean alreadyShowing) {
        View content = popup.getContentView();
        content.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = content.getMeasuredWidth();
        int height = content.getMeasuredHeight();
        int[] origin = new int[2];
        answer.getLocationInWindow(origin);
        View window = answer.getRootView();
        int[] box = selectionBox(selection);
        int x = Math.round(WordMenu.leftFor(origin[0] + (box[0] + box[2]) / 2f,
                width, window.getWidth()));
        int y = Math.round(WordMenu.topFor(origin[1] + box[1], origin[1] + box[3],
                height, window.getHeight()));
        if (alreadyShowing) {
            popup.update(x, y, width, height);
            return;
        }
        popup.setWidth(width);
        popup.showAtLocation(answer, Gravity.NO_GRAVITY, x, y);
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

    /**
     * Runs the gesture first and only then puts the card away: 追问, 新建主题 and 复制 all read the
     * answer's live selection, and it is the card closing that makes the screen end that selection.
     */
    private void choose(Runnable gesture) {
        gesture.run();
        close();
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
