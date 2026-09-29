package com.treelearn.app.screen;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;
import com.treelearn.app.ui.Highlighter;
import com.treelearn.app.ui.TextSelection;
import com.treelearn.app.ui.WordMenu;
import com.treelearn.core.domain.Annotation;

import java.util.Collections;
import java.util.List;

/**
 * The six-cell menu the app draws over an answer's selected passage, and the sentence it acts on
 * (PRD 6.2.19 points 1-2, 8, 方案 V1.1 §2.1 for the mark pair).
 *
 * <p><strong>Six cells in the row is the terminal state; 更多 is a width fallback.</strong> 问 AI,
 * 新建主题, 高亮, 批注, 复制 and 全选 are each their own icon cell, and there is no 更多 submenu - that
 * is the 2026-09-28 decision. What remains of 更多 answers one case only: a window too narrow to draw
 * the six cells whole, a small phone or a split screen. There the row keeps a prefix of the six and
 * 更多 carries the tail into a second vertical card, so what the learner pays for a narrow screen is
 * one more tap, never a missing operation. The split is not this class's judgement either - it comes
 * from {@link WordMenu#fitFor}, which reads the window's width and the cell the layout draws - so a
 * wide phone and a narrow one agree on where the fold falls instead of each guessing.
 *
 * <p><strong>It paints and applies; it never judges.</strong> Which items exist and which of them are
 * pressable comes from {@link WordMenu}, which reads every flag from the answer's own predicate, and
 * where the menu may land comes from there too. This class adds only what needs a window: the
 * measurement of the card and the coordinates of the selected passage. The one look it takes at the
 * marks is the mark cell's own face - whether the selection sits exactly on an existing highlight,
 * which is what flips that cell between 记号笔 and 带斜杠记号笔 - and even that judgement runs through
 * {@link Highlighter#plan}, the same decision the tap itself will execute.
 *
 * <p><strong>Six icons in one row, and the words moved to the descriptions.</strong> There is no
 * caption at all - 菜单格一律图标承载，不得文字按钮. The item names are not drawn anywhere, so the cells'
 * content descriptions - set in the layout - are what a screen reader reads out, and this class
 * writes no label of its own. A cell the row has no room for, and the hairline that belongs to it,
 * are hidden together, so a folded row never trails a stray rule.
 *
 * <p><strong>It is the menu, not a second one.</strong> The screen empties the platform's own
 * selection toolbar and shows this card instead, because a platform menu can neither be ordered nor
 * kept out of its overflow sheet by the app - which is how 新建主题 ended up behind a 更多 sheet while
 * 全选 / 复制 held the visible slots. The cells here, and their order, are therefore the primary menu
 * the learner sees; 更多, when a narrow window asks for it, opens a second icon-only card with the
 * same cells in the same order and without ending the selection.
 *
 * <p>The menu applies the passage to the answer's own text view when it opens, and 全选 widens that
 * same selection in place rather than remembering a second one. So the highlight the learner sees, the
 * range {@link TextView#getSelectionStart()} reports and the passage 问 AI and 复制 hand over are one
 * value, and the existing selection-reading path stays the only reader.
 */
public final class WordMenuPopup {

    /** The gestures that leave the menu; 全选 only changes the selection and is answered here. */
    public interface Listener {

        /** 问 AI: take the sentence as the context of the next question. */
        void onQuoteRequested();

        /** 新建主题: open a branch from this answer. */
        void onForkRequested();

        /** 高亮: mark, unmark or merge the passage, as {@link Highlighter#plan} decides. */
        void onHighlightRequested();

        /** 批注: the flow T-D owns; the signature is final now, the dialog comes later. */
        void onNoteRequested();

        /** 复制: put the selected passage on the clipboard. */
        void onCopyRequested();
    }

    /** How far a cell that is drawn but not pressable is faded - 问 AI while the answer is unfinished. */
    private static final float DISABLED_ALPHA = 0.4f;

    /** The row's slots, in the order the prototype draws them; 更多 stands behind them, not among them. */
    private static final WordMenu.Item[] ROW = {
            WordMenu.Item.QUOTE,
            WordMenu.Item.FORK,
            WordMenu.Item.HIGHLIGHT,
            WordMenu.Item.NOTE,
            WordMenu.Item.COPY,
            WordMenu.Item.SELECT_ALL,
    };

    private final TextView answer;
    private final ChatMessage message;
    private final List<Annotation> marks;
    private final int proseIndex;
    private final Listener listener;
    private final View[] rowCells = new View[ROW.length];
    private final View[] rowSeparators = new View[ROW.length + 1];
    private final View[] moreCells = new View[ROW.length];
    private final View[] moreSeparators = new View[ROW.length];
    private final View moreCell;
    private final ImageView highlightIcon;
    private final ImageView moreHighlightIcon;
    private final PopupWindow popup;
    private final PopupWindow morePopup;

    public WordMenuPopup(TextView answer, ChatMessage message, List<Annotation> marks,
            int proseIndex, Listener listener) {
        this.answer = answer;
        this.message = message;
        this.marks = marks == null ? Collections.<Annotation>emptyList() : marks;
        this.proseIndex = proseIndex;
        this.listener = listener;
        LinearLayout content = (LinearLayout) LayoutInflater.from(answer.getContext())
                .inflate(R.layout.popup_word_action, null, false);
        rowCells[0] = content.findViewById(R.id.word_menu_ask_ai);
        rowCells[1] = content.findViewById(R.id.word_menu_fork);
        rowCells[2] = content.findViewById(R.id.word_menu_highlight);
        rowCells[3] = content.findViewById(R.id.word_menu_note);
        rowCells[4] = content.findViewById(R.id.word_menu_copy);
        rowCells[5] = content.findViewById(R.id.word_menu_select_all);
        rowSeparators[1] = content.findViewById(R.id.word_menu_sep_fork);
        rowSeparators[2] = content.findViewById(R.id.word_menu_sep_highlight);
        rowSeparators[3] = content.findViewById(R.id.word_menu_sep_note);
        rowSeparators[4] = content.findViewById(R.id.word_menu_sep_copy);
        rowSeparators[5] = content.findViewById(R.id.word_menu_sep_select_all);
        rowSeparators[6] = content.findViewById(R.id.word_menu_sep_more);
        moreCell = content.findViewById(R.id.word_menu_more);
        highlightIcon = content.findViewById(R.id.word_menu_highlight_icon);
        rowCells[0].setOnClickListener(cell -> choose(listener::onQuoteRequested));
        rowCells[1].setOnClickListener(cell -> choose(listener::onForkRequested));
        rowCells[2].setOnClickListener(cell -> choose(listener::onHighlightRequested));
        rowCells[3].setOnClickListener(cell -> choose(listener::onNoteRequested));
        rowCells[4].setOnClickListener(cell -> choose(listener::onCopyRequested));
        rowCells[5].setOnClickListener(cell -> selectAll());
        moreCell.setOnClickListener(cell -> showMore());
        // Not focusable on purpose: the card must not take window focus away from the answer whose
        // selection it acts on. A transparent background plus outside-touch still dismisses it, and on
        // a non-focusable popup that outside touch also reaches the answer, so tapping away ends the
        // selection in the same gesture instead of leaving handles behind with no menu.
        popup = new PopupWindow(content, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(false);
        popup.setElevation(answer.getResources().getDimension(R.dimen.card_elevation));

        LinearLayout moreContent = (LinearLayout) LayoutInflater.from(answer.getContext())
                .inflate(R.layout.popup_word_more, null, false);
        moreCells[0] = moreContent.findViewById(R.id.word_menu_more_quote);
        moreCells[1] = moreContent.findViewById(R.id.word_menu_more_fork);
        moreCells[2] = moreContent.findViewById(R.id.word_menu_more_highlight);
        moreCells[3] = moreContent.findViewById(R.id.word_menu_more_note);
        moreCells[4] = moreContent.findViewById(R.id.word_menu_more_copy);
        moreCells[5] = moreContent.findViewById(R.id.word_menu_more_select_all);
        moreSeparators[1] = moreContent.findViewById(R.id.word_menu_more_sep_fork);
        moreSeparators[2] = moreContent.findViewById(R.id.word_menu_more_sep_highlight);
        moreSeparators[3] = moreContent.findViewById(R.id.word_menu_more_sep_note);
        moreSeparators[4] = moreContent.findViewById(R.id.word_menu_more_sep_copy);
        moreSeparators[5] = moreContent.findViewById(R.id.word_menu_more_sep_select_all);
        moreHighlightIcon = moreContent.findViewById(R.id.word_menu_more_highlight_icon);
        // The two cards carry the same six operations, so a folded cell answers to the very same
        // gesture it would have answered to in the row.
        moreCells[0].setOnClickListener(cell -> choose(listener::onQuoteRequested));
        moreCells[1].setOnClickListener(cell -> choose(listener::onForkRequested));
        moreCells[2].setOnClickListener(cell -> choose(listener::onHighlightRequested));
        moreCells[3].setOnClickListener(cell -> choose(listener::onNoteRequested));
        moreCells[4].setOnClickListener(cell -> choose(listener::onCopyRequested));
        moreCells[5].setOnClickListener(cell -> selectAll());
        morePopup = new PopupWindow(moreContent, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, false);
        morePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        morePopup.setOutsideTouchable(false);
        morePopup.setElevation(answer.getResources().getDimension(R.dimen.card_elevation));
    }

    /**
     * Told when the card goes away for any reason - the learner chose a row, tapped outside it, or the
     * selection it was drawn over ended. The screen ends the platform selection with it, so the handles
     * and the menu appear and disappear as one thing.
     */
    public void setOnClosed(Runnable closed) {
        popup.setOnDismissListener(() -> {
            closeMore();
            closed.run();
        });
    }

    /** Opens the menu on one sentence, placed so that it never covers the sentence it acts on. */
    public void show(TextSelection selection) {
        apply(selection);
        bind();
        place(selection, false);
        enableOutsideTouchAfterOpening(popup);
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
        closeMore();
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
        closeMore();
        if (popup.isShowing()) {
            popup.dismiss();
        }
    }

    /** Whether this card still has a visible popup over a non-empty live selection. */
    public boolean hasLiveSelection() {
        return popup.isShowing() && !current().isEmpty();
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

    /**
     * Paints both cards from the current selection: the row with the operations it has room for, 更多
     * with the tail it had to fold, and the mark cell's own face on whichever of the two draws it.
     */
    private void bind() {
        TextSelection selection = current();
        String selected = selection.read(answer.getText().toString());
        // The mark cell's own face: slashed exactly when the selection sits on a whole stored
        // highlight - the state where the tap cancels instead of marking (方案 §2.3).
        boolean cancelling = !selection.isEmpty() && Highlighter.plan(marks,
                Annotation.Locator.prose(proseIndex), selection.start, selection.end)
                .kind == Highlighter.Kind.CANCEL;
        face(rowCells[2], highlightIcon, cancelling);
        face(moreCells[2], moreHighlightIcon, cancelling);
        List<WordMenu.Entry> entries = WordMenu.entriesFor(message, selected);
        WordMenu.Fit fit = WordMenu.fitFor(entries, room(), cellWidth(), hairline(), cardPadding());
        draw(rowCells, rowSeparators, fit.primary);
        draw(moreCells, moreSeparators, fit.overflow);
        boolean folded = fit.folded();
        moreCell.setVisibility(folded ? View.VISIBLE : View.GONE);
        rowSeparators[6].setVisibility(folded ? View.VISIBLE : View.GONE);
        if (!folded) {
            closeMore();
        }
    }

    /**
     * Shows exactly the entries named, in the row's own order, and hides the rest - a cell and the
     * hairline drawn before it together, so a folded tail leaves no rule hanging at the card's end.
     */
    private void draw(View[] cells, View[] separators, List<WordMenu.Entry> entries) {
        boolean previous = false;
        for (int i = 0; i < ROW.length; i++) {
            WordMenu.Entry entry = entryFor(entries, ROW[i]);
            boolean drawn = entry != null;
            cells[i].setVisibility(drawn ? View.VISIBLE : View.GONE);
            if (drawn) {
                cells[i].setEnabled(entry.enabled);
                cells[i].setAlpha(entry.enabled ? 1f : DISABLED_ALPHA);
            }
            if (separators[i] != null) {
                separators[i].setVisibility(drawn && previous ? View.VISIBLE : View.GONE);
            }
            previous |= drawn;
        }
    }

    /** The mark cell between 记号笔 and 带斜杠记号笔, on whichever card is drawing it. */
    private void face(View cell, ImageView icon, boolean cancelling) {
        icon.setImageResource(cancelling
                ? R.drawable.ic_action_highlight_off : R.drawable.ic_action_highlight);
        cell.setContentDescription(answer.getResources().getString(cancelling
                ? R.string.chat_word_highlight_cancel_desc : R.string.chat_word_highlight_desc));
    }

    private static WordMenu.Entry entryFor(List<WordMenu.Entry> entries, WordMenu.Item item) {
        for (WordMenu.Entry entry : entries) {
            if (entry.item == item) {
                return entry;
            }
        }
        return null;
    }

    /**
     * The width the row may occupy: the window, less the margin the menu keeps from either edge. A
     * window that has not been laid out answers with no limit at all, so a bind that happens before
     * the first layout draws the six cells rather than folding them for a width of zero.
     */
    private int room() {
        int window = answer.getRootView().getWidth();
        if (window <= 0) {
            return Integer.MAX_VALUE;
        }
        float margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, WordMenu.MARGIN,
                answer.getResources().getDisplayMetrics());
        return Math.round(window - 2 * margin);
    }

    private int cellWidth() {
        return answer.getResources().getDimensionPixelSize(R.dimen.word_menu_cell);
    }

    private int hairline() {
        return answer.getResources().getDimensionPixelSize(R.dimen.hairline);
    }

    private int cardPadding() {
        return answer.getResources().getDimensionPixelSize(R.dimen.space_s1);
    }

    /** 全选 takes the whole answer as the selection, and the menu then offers what that enables. */
    private void selectAll() {
        apply(TextSelection.whole(answer.getText().length()));
        closeMore();
        bind();
    }

    /**
     * Runs the gesture first and only then puts the card away: 问 AI, 新建主题, the mark pair and 复制
     * all read the answer's live selection, and it is the card closing that makes the screen end that
     * selection.
     */
    private void choose(Runnable gesture) {
        closeMore();
        gesture.run();
        close();
    }

    /** Opens 更多 without ending the platform selection or dismissing the primary card. */
    private void showMore() {
        if (morePopup.isShowing()) {
            closeMore();
            return;
        }
        View content = morePopup.getContentView();
        content.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = content.getMeasuredWidth();
        int height = content.getMeasuredHeight();
        int[] origin = new int[2];
        moreCell.getLocationInWindow(origin);
        View window = answer.getRootView();
        int x = Math.round(WordMenu.leftFor(origin[0] + moreCell.getWidth() / 2f,
                width, window.getWidth()));
        int y = Math.round(WordMenu.topFor(origin[1], origin[1] + moreCell.getHeight(),
                height, window.getHeight()));
        morePopup.setWidth(width);
        morePopup.showAtLocation(answer, Gravity.NO_GRAVITY, x, y);
        enableOutsideTouchAfterOpening(morePopup);
    }

    private void closeMore() {
        if (morePopup.isShowing()) {
            morePopup.dismiss();
        }
    }

    /** Do not let the long-press ACTION_UP dismiss a popup opened from that same gesture. */
    private void enableOutsideTouchAfterOpening(PopupWindow target) {
        answer.post(() -> {
            if (target.isShowing()) {
                target.setOutsideTouchable(true);
            }
        });
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
}
