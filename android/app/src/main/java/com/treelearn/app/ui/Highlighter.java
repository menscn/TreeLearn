package com.treelearn.app.ui;

import android.text.Selection;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.core.domain.Annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The highlight flow for one container of a rendered answer (方案 V1.1 §2.2–§2.3,
 * user-confirmed 2026-09-28): the judgement of what a tap on 高亮 means, and the span shell that
 * paints and un-paints the yellow once the judgement is made.
 *
 * <p><strong>Judgement first, in plain JVM terms.</strong> {@link #plan} answers one question for
 * the selection under the learner's fingers: is it sitting <em>exactly</em> on an existing highlight
 * ({@code CANCEL} — the menu's mark cell has already flipped to the slashed marker), or is it a mark
 * to write ({@code WRITE}), merged into the union of whatever it intersects or touches. The merging
 * rule itself is T-A's {@link Annotation#mergeHighlights}; notes are never absorbed by it. A note
 * under the very same span does not flip the menu: cancelling answers 「点在已有高亮上」 only.
 *
 * <p><strong>The paint is a thin shell with one rule of its own.</strong> {@link #flush} runs
 * <em>after</em> {@code MarkdownRenderer} has built the segment text — spans painted later draw
 * above spans painted earlier, which is exactly how the highlight survives the code block's grey
 * background (V-R2; {@code MarkdownRenderer} stays untouched). Every painted highlight also plants
 * a {@link ClickableSpan} whose tap hands the mark back to the screen, which selects it and reopens
 * the menu (方案 §2.3) — the same menu, now offering to cancel what the tap landed on.
 *
 * <p>This class deliberately does not touch storage: {@code plan} decides, the screen performs the
 * delete-plus-insert through the store, and the spans carry no state beyond the mark they name.
 */
public final class Highlighter {

    private Highlighter() {
    }

    /** What a tap on 高亮 means for the selection under it. */
    public enum Kind {
        /** The selection sits exactly on an existing highlight; the tap removes it. */
        CANCEL,
        /** The selection becomes (or merges into) a stored highlight row. */
        WRITE
    }

    /** The outcome of {@link #plan}: the decision the screen performs, storage included. */
    public static final class Plan {

        public final Kind kind;
        /** The highlight the tap removes on {@link Kind#CANCEL}; {@code null} on a write. */
        public final String cancelId;
        /** The union to write and the absorbed rows to delete on {@link Kind#WRITE}; {@code null} on a cancel. */
        public final Annotation.Merge merge;

        private Plan(Kind kind, String cancelId, Annotation.Merge merge) {
            this.kind = kind;
            this.cancelId = cancelId;
            this.merge = merge;
        }

        static Plan cancel(String id) {
            return new Plan(Kind.CANCEL, id, null);
        }

        static Plan write(Annotation.Merge merge) {
            return new Plan(Kind.WRITE, null, merge);
        }
    }

    /**
     * What the menu's mark cell means for one selection: a cancellation when it sits exactly on an
     * existing highlight of the same anchor, a write (merged or fresh) otherwise.
     *
     * <p>The exact-overlap judgement here is {@link Annotation#isExactOverlap}'s — same anchor, same
     * span, kind apart — narrowed to highlights, because a note sharing the span is not something
     * this tap cancels. Marks of another anchor never answer.
     *
     * @param marks   the stored marks of the answer (other anchors and notes included; ignored)
     * @param locator where the selection anchors
     * @param start   the selection's inclusive start, into the container's text
     * @param end     the selection's exclusive end
     */
    public static Plan plan(List<Annotation> marks, Annotation.Locator locator, int start, int end) {
        for (Annotation mark : marks) {
            if (mark.kind != Annotation.Kind.HIGHLIGHT || !mark.locator.equals(locator)) {
                continue;
            }
            // Same judgement isExactOverlap makes (same locator, same span); the locator half is
            // already settled by the filter above.
            if (mark.span.start == start && mark.span.end == end) {
                return Plan.cancel(mark.id);
            }
        }
        return Plan.write(Annotation.mergeHighlights(marks, locator, start, end));
    }

    /** Projects the same mark decision onto the current view before the store round trip returns. */
    public static List<Annotation> optimisticMarks(List<Annotation> marks,
            Annotation.Locator locator, int start, int end) {
        Plan decision = plan(marks, locator, start, end);
        List<Annotation> projected = new ArrayList<Annotation>();
        for (Annotation mark : marks) {
            if (decision.kind == Kind.CANCEL && mark.id.equals(decision.cancelId)) {
                continue;
            }
            if (decision.kind == Kind.WRITE && decision.merge.absorbedIds.contains(mark.id)) {
                continue;
            }
            projected.add(mark);
        }
        if (decision.kind == Kind.WRITE) {
            projected.add(Annotation.highlight(UUID.randomUUID().toString(), locator,
                    decision.merge.span.start, decision.merge.span.end));
        }
        return Collections.unmodifiableList(projected);
    }

    /** The tap a highlight's clickable span answers; the screen selects and reopens the menu. */
    public interface OnHighlightTapped {

        void onHighlightTapped(TextView container, Annotation highlight);
    }

    /**
     * Repaints one container's highlights: lifts the highlights this class drew before and paints
     * the current marks. Safe to call on every state hand-over - without the lift, a second flush
     * would stack a second yellow over the first - and narrow in what it lifts: only spans of this
     * class's own two types come off, so the code block's grey background and every other Markdown
     * span stay exactly where the renderer put them (V-R2).
     *
     * @param container the prose run (or, from T-C, the cell) whose text carries the spans
     * @param marks     the stored marks of the answer
     * @param locator   which anchor this container is
     * @param listener  the tap receiver
     */
    public static void repaint(TextView container, List<Annotation> marks,
            Annotation.Locator locator, OnHighlightTapped listener) {
        clear(container);
        flush(container, marks, locator, listener);
    }

    /** Lifts only the spans {@link #flush} drew, leaving every Markdown span untouched. */
    public static void clear(TextView container) {
        CharSequence text = container.getText();
        if (!(text instanceof Spannable)) {
            return;
        }
        Spannable spannable = (Spannable) text;
        for (Object span : spannable.getSpans(0, spannable.length(), YellowHighlightSpan.class)) {
            spannable.removeSpan(span);
        }
        for (Object span : spannable.getSpans(0, spannable.length(), HighlightTapSpan.class)) {
            spannable.removeSpan(span);
        }
    }

    /**
     * Paints one container's highlights onto its rendered text — after Markdown, so the yellow
     * draws above the code grey (V-R2). Each highlight also plants the clickable span the
     * second-tap flow rides.
     *
     * <p>Highlights whose span no longer fits the container's text are skipped, not fatal: the
     * render path must survive a mark that raced a digest clear rather than crash the answer.
     *
     * @param container the prose run (or, from T-C, the cell) whose text carries the spans
     * @param marks     the stored marks of the answer
     * @param locator   which anchor this container is
     * @param listener  the tap receiver
     */
    public static void flush(TextView container, List<Annotation> marks,
            Annotation.Locator locator, OnHighlightTapped listener) {
        CharSequence text = container.getText();
        if (!(text instanceof Spannable)) {
            // Nothing to paint on. The caller keeps spans alive with setText(..., SPANNABLE);
            // a plain CharSequence here means the container never opted in.
            return;
        }
        Spannable spannable = (Spannable) text;
        int yellow = container.getResources().getColor(R.color.tl_yellow_highlight,
                container.getContext().getTheme());
        for (Annotation mark : marks) {
            if (mark.kind != Annotation.Kind.HIGHLIGHT || !mark.locator.equals(locator)) {
                continue;
            }
            int start = mark.span.start;
            int end = mark.span.end;
            if (start < 0 || end > spannable.length() || start >= end) {
                continue;
            }
            spannable.setSpan(new YellowHighlightSpan(yellow), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new HighlightTapSpan(container, mark, listener), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** Selects the mark's span inside its container, as 方案 §2.3 step ① does before the menu reopens. */
    public static void select(TextView container, Annotation highlight) {
        CharSequence text = container.getText();
        if (text instanceof Spannable) {
            Selection.setSelection((Spannable) text, highlight.span.start, highlight.span.end);
        }
    }

    /**
     * This class's own yellow, so {@link #clear} lifts exactly what it painted. Subclassing is the
     * marker: a plain {@code BackgroundColorSpan} could not be told apart from the code block's.
     */
    private static final class YellowHighlightSpan extends BackgroundColorSpan {

        YellowHighlightSpan(int color) {
            super(color);
        }
    }

    /** The click the second tap lands on; the text keeps its own ink (方案 §2.2). */
    private static final class HighlightTapSpan extends ClickableSpan {

        private final TextView container;
        private final Annotation mark;
        private final OnHighlightTapped listener;

        HighlightTapSpan(TextView container, Annotation mark, OnHighlightTapped listener) {
            this.container = container;
            this.mark = mark;
            this.listener = listener;
        }

        @Override
        public void onClick(View widget) {
            listener.onHighlightTapped(container, mark);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            // The yellow carries the affordance. Without this override the platform would paint
            // the span blue and underline it.
        }
    }
}
