package com.example.envelopemoney;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BudgetBackupTest {
    @Test
    public void rejectsOversizedText() {
        char[] chars = new char[BudgetBackup.MAX_CHARS + 1];
        java.util.Arrays.fill(chars, 'a');
        BudgetBackup.ParseResult result = BudgetBackup.parse(new String(chars));
        assertFalse(result.ok);
        assertEquals("too-large", result.error);
    }

    @Test
    public void rejectsTextThatIsNotJson() {
        BudgetBackup.ParseResult result = BudgetBackup.parse("not json");
        assertFalse(result.ok);
        assertEquals("not-json", result.error);
    }
}
