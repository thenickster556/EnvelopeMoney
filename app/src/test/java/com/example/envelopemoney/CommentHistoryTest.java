package com.example.envelopemoney;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommentHistoryTest {

    @Test
    public void remember_trimsAndSkipsEmpty() {
        List<String> existing = Collections.singletonList("Fill-up");
        assertEquals(existing, CommentHistory.remember(existing, "   "));
        assertEquals(existing, CommentHistory.remember(existing, null));
        assertEquals(Arrays.asList("Market run", "Fill-up"),
                CommentHistory.remember(existing, "  Market run  "));
    }

    @Test
    public void remember_mergesCaseInsensitiveKeepingLatestCasing() {
        List<String> existing = new ArrayList<>(Arrays.asList("fill-up", "Groceries"));
        List<String> next = CommentHistory.remember(existing, "Fill-up");
        assertEquals(Arrays.asList("Fill-up", "Groceries"), next);
    }

    @Test
    public void remember_movesToFrontAndCapsAtFifty() {
        List<String> existing = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            existing.add("Note " + i);
        }
        List<String> next = CommentHistory.remember(existing, "Newest");
        assertEquals(50, next.size());
        assertEquals("Newest", next.get(0));
        assertEquals("Note 0", next.get(1));
        assertEquals("Note 48", next.get(49));
    }

    @Test
    public void suggestions_prefixThenContainsRecency() {
        List<String> comments = Arrays.asList("Fill-up", "Fun night", "Farmer market", "Coffee");
        List<String> matches = CommentHistory.suggestions(comments, "f");
        assertEquals(Arrays.asList("Fill-up", "Fun night", "Farmer market", "Coffee"), matches);
        assertEquals(Arrays.asList("Fill-up", "Fun night", "Farmer market"),
                CommentHistory.suggestions(comments, "f").subList(0, 3));
    }

    @Test
    public void suggestions_emptyQueryOrNoMatch() {
        List<String> comments = Arrays.asList("Fill-up", "Coffee");
        assertTrue(CommentHistory.suggestions(comments, "").isEmpty());
        assertTrue(CommentHistory.suggestions(comments, "   ").isEmpty());
        assertTrue(CommentHistory.suggestions(comments, "zzz").isEmpty());
    }
}
