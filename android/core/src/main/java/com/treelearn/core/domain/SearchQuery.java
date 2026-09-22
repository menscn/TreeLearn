package com.treelearn.core.domain;

import java.util.Locale;

/**
 * A learner's search text, normalised, with the LIKE pattern it becomes (technical design 6.3, 11).
 *
 * <p><strong>Why this class exists at all.</strong> Android SQLite's default full-text tokeniser
 * cannot reliably find a Chinese substring, so the first release searches with a parameterised
 * {@code LIKE}. That makes the pattern the only place where a learner's text meets SQL, and it is a
 * place with two ways to get it badly wrong: splicing the text into the statement, or leaving
 * {@code %} and {@code _} unescaped so that a stored percent silently means "anything". Both are
 * decided here, once, so no DAO can get it differently.
 *
 * <p><strong>Normalisation is folding, never rewriting.</strong> Case is folded and runs of
 * whitespace collapse to one space, because two spellings of one query should mean one query. CJK
 * text is passed through untouched: it has no case, and any "translation" here would be this class
 * inventing characters the learner did not type.
 *
 * <p><strong>A blank query has no pattern, and asking for one is an error.</strong>
 * {@code LIKE '%%'} matches every row in the tree — the whole store handed back as "search results".
 * {@link #likePattern()} therefore refuses instead of building it, and callers ask {@link #isEmpty()}
 * first (the search presenter does, and never reaches the store at all).
 */
public final class SearchQuery {

    /** The escape character. Stored with the query because the statement must declare it explicitly. */
    public static final char ESCAPE_CHAR = '\\';

    private final String normalized;
    private final String pattern;

    private SearchQuery(String normalized, String pattern) {
        this.normalized = normalized;
        this.pattern = pattern;
    }

    /**
     * Normalises a learner's text.
     *
     * @throws IllegalArgumentException if {@code raw} is null — a caller with no query at all is a
     *         bug, while a caller with nothing <em>typed</em> passes an empty string
     */
    public static SearchQuery of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw query must not be null");
        }
        String normalized = raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return new SearchQuery(normalized,
                normalized.isEmpty() ? null : "%" + escapePattern(normalized) + "%");
    }

    /** True when nothing was typed: the screen shows its idle state and never asks the store. */
    public boolean isEmpty() {
        return normalized.isEmpty();
    }

    /** The folded text, for comparing "is this result still the query on screen". */
    public String normalized() {
        return normalized;
    }

    /**
     * The pattern to bind as a LIKE argument.
     *
     * @throws IllegalStateException when the query is blank; see the class comment for why this is
     *         refused rather than turned into a match-everything pattern
     */
    public String likePattern() {
        if (pattern == null) {
            throw new IllegalStateException("a blank query has no LIKE pattern: '%%' would match"
                    + " every row in the store, so no statement is ever built from one");
        }
        return pattern;
    }

    /**
     * Escapes the three characters that mean something to LIKE, so the result can be used with an
     * explicit {@code ESCAPE} clause.
     *
     * <p>The escape character itself is escaped too. Skipping it is the subtle half: a stored
     * backslash would otherwise combine with whatever follows it, so a query for {@code c\d} would
     * quietly start meaning {@code cd}.
     */
    public static String escapePattern(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == ESCAPE_CHAR || character == '%' || character == '_') {
                escaped.append(ESCAPE_CHAR);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}