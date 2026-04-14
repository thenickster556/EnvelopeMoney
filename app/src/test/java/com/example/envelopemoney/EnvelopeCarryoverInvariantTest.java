package com.example.envelopemoney;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for carry-over invariants: {@link Envelope#getLimit()} is the user monthly budget;
 * carry flows through {@link Envelope#getRemaining()} and {@link Envelope.MonthData}.
 */
public class EnvelopeCarryoverInvariantTest {

    @Test
    public void reset_carryOver_true_doesNotInflateEnvelopeLimit() {
        Envelope e = new Envelope("E", 100d);
        e.setRemaining(25d);
        e.reset(true);
        assertEquals(100d, e.getLimit(), 0.001d);
        assertEquals(100d, e.getOriginalLimit(), 0.001d);
        assertEquals(125d, e.getRemaining(), 0.001d);
    }

    @Test
    public void reset_carryOver_false_syncsLimitToOriginal() {
        Envelope e = new Envelope("E", 100d);
        e.setRemaining(40d);
        e.reset(false);
        assertEquals(100d, e.getLimit(), 0.001d);
        assertEquals(100d, e.getRemaining(), 0.001d);
    }

    @Test
    public void initializeMonth_newMonth_usesOriginalLimitPlusPriorRemaining_notPriorLimitPlusRemaining() {
        Envelope e = new Envelope("Pond", 100d);
        Envelope.MonthData feb = e.getMonthlyData("2026-02");
        feb.limit = 200d;
        feb.remaining = 30d;

        e.initializeMonth("2026-03", true);

        Envelope.MonthData mar = e.getMonthlyData("2026-03");
        assertEquals(130d, mar.limit, 0.001d);
        assertEquals(130d, mar.remaining, 0.001d);
    }
}
