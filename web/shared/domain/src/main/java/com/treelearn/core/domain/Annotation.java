package com.treelearn.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One learner's text mark on an answer — a highlight or a note — anchored in exactly one container
 * of the rendered answer (方案 V1.1 §2.2–§2.4, §3.1, user-confirmed 2026-09-28).
 *
 * <p>This type is the single judge of the rules the whole feature stands on; the DDL only backstops
 * the shape of a stored row, and the screen only consumes decisions made here:
 *
 * <ul>
 *   <li><strong>Locator shape</strong> — a {@link Locator} anchors in one container: a prose
 *       segment carries {@code proseIndex} and the -1 sentinel elsewhere, a cell carries the cell
 *       triple with {@code proseIndex = -1}. The same grouping the annotations CHECK enforces,
 *       judged before a row can even be built. The two judgements agree on every input, which is
 *       the deliberate bounded overlap the schema already documents for anchors.</li>
 *   <li><strong>Strict overlap</strong> (拍板 2026-09-28) — a duplicate is the <em>exact</em> same
 *       anchor: same locator, same span, regardless of kind, because the consumer of this judgement
 *       is the "you tapped an existing mark" flow, not the store's dedup (the unique index, which
 *       does carry kind, is the storage-side backstop).</li>
 *   <li><strong>Merging</strong> (拍板 2026-09-28) — a new highlight that intersects <em>or
 *       touches</em> existing highlights of the same locator merges into their union interval; the
 *       absorbed rows are deleted and replaced by the union row. The touching case follows the
 *       confirmed interactive demo (mergeRanges, 交互 Demo V1.1), not the narrower word "相交" of
 *       the prose: two adjacent highlights read as one, and only a merge keeps a boundary click
 *       from belonging to "neither" span in the user's mind. {@link #mergeHighlights} computes that
 *       union and names the rows to absorb — it decides, the caller writes.</li>
 * </ul>
 *
 * <p>What an {@code Annotation} deliberately does not carry: the owning message, the answer's
 * content digest, and the write timestamps. Those are storage facts (the digest drives the
 * clear-on-mismatch read, 方案 §3.3) and live in the DAO's signatures, not in the value the screen
 * reasons about — a mark is the same mark no matter which revision generation it was written in.
 */
public final class Annotation {

    /** The container a mark anchors in; the pairing with the locator's sentinel columns is fixed. */
    public enum Container {
        /** A prose segment of the rendered answer; the k-th one, offsets into its text. */
        PROSE,
        /** One table cell; offsets into that cell's text (方案 §3.2, header rows included). */
        CELL
    }

    /** The two mark kinds; the DDL's kind CHECK admits exactly these two spellings. */
    public enum Kind {
        /** A mark without a body; the stored row always reads {@code body = ''} (F1). */
        HIGHLIGHT,
        /** A mark with the learner's note; the body is never blank (the NOTE half of the rule). */
        NOTE
    }

    /**
     * Where in the rendered answer the mark anchors. Immutable and total on its five components,
     * so two locators are the same anchor exactly when their components agree — a prose mark and a
     * cell mark never compare equal even when their numbers look alike.
     */
    public static final class Locator {

        public final Container container;
        /** The prose segment index; {@code -1} on a cell mark. */
        public final int proseIndex;
        /** The table index; {@code -1} on a prose mark. */
        public final int tableIndex;
        /** The row inside the table (header rows included, 方案 §3.2); {@code -1} on a prose mark. */
        public final int rowIndex;
        /** The column inside the table; {@code -1} on a prose mark. */
        public final int colIndex;

        private Locator(Container container, int proseIndex, int tableIndex, int rowIndex,
                        int colIndex) {
            this.container = container;
            this.proseIndex = proseIndex;
            this.tableIndex = tableIndex;
            this.rowIndex = rowIndex;
            this.colIndex = colIndex;
        }

        /** The locator of a mark on the k-th prose segment. */
        public static Locator prose(int proseIndex) {
            if (proseIndex < 0) {
                throw new IllegalArgumentException("proseIndex must not be negative: " + proseIndex);
            }
            return new Locator(Container.PROSE, proseIndex, -1, -1, -1);
        }

        /** The locator of a mark inside the cell at (table, row, col). */
        public static Locator cell(int tableIndex, int rowIndex, int colIndex) {
            if (tableIndex < 0 || rowIndex < 0 || colIndex < 0) {
                throw new IllegalArgumentException("cell indices must not be negative: table="
                        + tableIndex + ", row=" + rowIndex + ", col=" + colIndex);
            }
            return new Locator(Container.CELL, -1, tableIndex, rowIndex, colIndex);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Locator)) {
                return false;
            }
            Locator that = (Locator) other;
            return container == that.container && proseIndex == that.proseIndex
                    && tableIndex == that.tableIndex && rowIndex == that.rowIndex
                    && colIndex == that.colIndex;
        }

        @Override
        public int hashCode() {
            int result = container.hashCode();
            result = 31 * result + proseIndex;
            result = 31 * result + tableIndex;
            result = 31 * result + rowIndex;
            result = 31 * result + colIndex;
            return result;
        }

        @Override
        public String toString() {
            return container == Container.PROSE
                    ? "prose(" + proseIndex + ")"
                    : "cell(" + tableIndex + "," + rowIndex + "," + colIndex + ")";
        }
    }

    /**
     * The character range the mark covers, {@code start} inclusive and {@code end} exclusive, into
     * the text of its container. Non-empty by construction: an empty or inverted selection is
     * refused here rather than stored and then refused by the DDL.
     */
    public static final class Span {

        public final int start;
        public final int end;

        private Span(int start, int end) {
            this.start = start;
            this.end = end;
        }

        /** @throws IllegalArgumentException for a negative start or an empty/inverted range */
        public static Span of(int start, int end) {
            if (start < 0) {
                throw new IllegalArgumentException("start must not be negative: " + start);
            }
            if (end <= start) {
                throw new IllegalArgumentException("end must be above start: " + start + ".."
                        + end);
            }
            return new Span(start, end);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Span)) {
                return false;
            }
            Span that = (Span) other;
            return start == that.start && end == that.end;
        }

        @Override
        public int hashCode() {
            return 31 * start + end;
        }

        @Override
        public String toString() {
            return start + ".." + end;
        }
    }

    /**
     * The outcome of a highlight merge: the union span to write, and the ids of the absorbed rows
     * to delete. The merge decides; the caller (T-B's highlighter, inside one store transaction)
     * performs the delete-plus-insert, so the domain stays free of storage concerns.
     */
    public static final class Merge {

        /** The union interval the surviving row carries. */
        public final Span span;
        /** The ids absorbed into {@link #span}, in the order the caller handed them over. */
        public final List<String> absorbedIds;

        private Merge(Span span, List<String> absorbedIds) {
            this.span = span;
            this.absorbedIds = Collections.unmodifiableList(absorbedIds);
        }
    }

    /** The mark's own identity (a UUID the store generates on first write). */
    public final String id;
    public final Locator locator;
    public final Span span;
    public final Kind kind;
    /** The learner's note; always {@code ""} on a highlight (F1's java-side mirror). */
    public final String body;

    private Annotation(String id, Locator locator, Span span, Kind kind, String body) {
        this.id = Guard.id(id, "id");
        this.locator = Guard.object(locator, "locator");
        this.span = Guard.object(span, "span");
        this.kind = Guard.object(kind, "kind");
        this.body = body;
    }

    /** A body-less mark. */
    public static Annotation highlight(String id, Locator locator, int start, int end) {
        return new Annotation(id, locator, Span.of(start, end), Kind.HIGHLIGHT, "");
    }

    /**
     * A mark carrying the learner's note.
     *
     * @throws IllegalArgumentException for a blank body — a note without text is not a note, the
     *                                   same rule the DDL's NOTE CHECK carries
     */
    public static Annotation note(String id, Locator locator, int start, int end, String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("a note's body must not be blank");
        }
        return new Annotation(id, locator, Span.of(start, end), Kind.NOTE, body);
    }

    /**
     * Whether {@code other} anchors the exact same spot: same locator, same span, regardless of
     * kind. Partial overlaps and same-start-different-end selections are not duplicates — they are
     * new marks, and a highlight goes on to merge with them.
     *
     * @return false for {@code null}, so a caller can pass a lookup result straight in
     */
    public boolean isExactOverlap(Annotation other) {
        return other != null && locator.equals(other.locator) && span.equals(other.span);
    }

    /**
     * Computes the merge of a fresh highlight selection into the stored highlights of one locator.
     *
     * <p>Every stored highlight of {@code locator} that intersects <em>or touches</em> the growing
     * union is absorbed — transitively, so a selection bridging two highlights absorbs both and a
     * touch chain folds into one interval. Highlights of another locator are never reached across.
     * Notes are never absorbed: merging is a highlight-only rule, and a note's body belongs to the
     * range the learner attached it to.
     *
     * @param existingHighlights the stored highlights to merge into (may be empty; may carry other
     *                           locators, which are ignored)
     * @param locator            where the fresh selection anchors
     * @param start              the fresh selection's inclusive start
     * @param end                the fresh selection's exclusive end
     * @return the union span and the absorbed ids; {@code absorbedIds} is empty when nothing
     *         connected, in which case the caller simply writes the selection as its own row
     * @throws IllegalArgumentException for an empty or inverted selection
     */
    public static Merge mergeHighlights(List<Annotation> existingHighlights, Locator locator,
                                        int start, int end) {
        Guard.object(existingHighlights, "existingHighlights");
        Guard.object(locator, "locator");
        Span union = Span.of(start, end);
        List<String> absorbed = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Annotation candidate : existingHighlights) {
                if (absorbed.contains(candidate.id)) {
                    continue;
                }
                if (candidate.kind != Kind.HIGHLIGHT || !candidate.locator.equals(locator)) {
                    continue;
                }
                if (candidate.span.start <= union.end && union.start <= candidate.span.end) {
                    absorbed.add(candidate.id);
                    union = Span.of(Math.min(candidate.span.start, union.start),
                            Math.max(candidate.span.end, union.end));
                    changed = true;
                }
            }
        }
        return new Merge(union, absorbed);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Annotation)) {
            return false;
        }
        Annotation that = (Annotation) other;
        return id.equals(that.id) && locator.equals(that.locator) && span.equals(that.span)
                && kind == that.kind && body.equals(that.body);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + locator.hashCode();
        result = 31 * result + span.hashCode();
        result = 31 * result + kind.hashCode();
        result = 31 * result + body.hashCode();
        return result;
    }

    @Override
    public String toString() {
        // Body length, not body: a note is conversation data and must not reach a log line (§15).
        return "Annotation[" + id + ", " + locator + ", " + span + ", " + kind + ", body="
                + body.length() + " chars]";
    }
}
