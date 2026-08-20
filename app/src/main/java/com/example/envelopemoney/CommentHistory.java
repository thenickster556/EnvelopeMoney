package com.example.envelopemoney;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Recency-ordered comment memory for typeahead. Pure helper — no Android types.
 *
 * <p>Inputs are stored comments (most recent first). Empty/whitespace comments are ignored.
 * Matching is case-insensitive. Cap is {@link #MAX_COMMENTS}.
 */
public final class CommentHistory {

    public static final int MAX_COMMENTS = 50;

    private CommentHistory() {
    }

    /**
     * Returns a new list with {@code comment} at the front, merged case-insensitively.
     */
    public static List<String> remember(List<String> existing, String comment) {
        List<String> next = copyOf(existing);
        if (comment == null) {
            return next;
        }
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            return next;
        }
        for (int i = next.size() - 1; i >= 0; i--) {
            if (next.get(i).equalsIgnoreCase(trimmed)) {
                next.remove(i);
            }
        }
        next.add(0, trimmed);
        if (next.size() > MAX_COMMENTS) {
            next = new ArrayList<>(next.subList(0, MAX_COMMENTS));
        }
        return next;
    }

    /**
     * Prefix matches first, then contains matches, preserving recency. Empty query yields none.
     */
    public static List<String> suggestions(List<String> comments, String query) {
        if (query == null) {
            return Collections.emptyList();
        }
        String needle = query.trim().toLowerCase(Locale.US);
        if (needle.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> prefix = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String comment : copyOf(comments)) {
            String lower = comment.toLowerCase(Locale.US);
            if (lower.startsWith(needle)) {
                prefix.add(comment);
            } else if (lower.contains(needle)) {
                contains.add(comment);
            }
        }
        prefix.addAll(contains);
        return prefix;
    }

    private static List<String> copyOf(List<String> existing) {
        if (existing == null || existing.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(existing);
    }
}
