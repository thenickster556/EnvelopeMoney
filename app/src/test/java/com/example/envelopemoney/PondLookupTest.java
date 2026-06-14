package com.example.envelopemoney;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PondLookupTest {

    @Test
    public void findByName_exactMatch() {
        Envelope gas = named("Gas");
        assertEquals(gas, PondLookup.findByName(Collections.singletonList(gas), "Gas"));
    }

    @Test
    public void findByName_trimmedInputAndStoredName() {
        Envelope gas = named(" Gas ");
        assertEquals(gas, PondLookup.findByName(Collections.singletonList(gas), "Gas"));
        assertEquals(gas, PondLookup.findByName(Collections.singletonList(named("Gas")), " Gas "));
    }

    @Test
    public void findByName_missingOrDeleted() {
        assertNull(PondLookup.findByName(Arrays.asList(named("A"), named("B")), "Deleted"));
        assertNull(PondLookup.findByName(null, "A"));
        assertNull(PondLookup.findByName(Collections.singletonList(named("A")), null));
        assertNull(PondLookup.findByName(Collections.singletonList(named("A")), "   "));
    }

    @Test
    public void canonicalName_returnsStoredName() {
        Envelope gas = named(" Gas ");
        assertEquals(" Gas ", PondLookup.canonicalName(Collections.singletonList(gas), "Gas"));
    }

    private static Envelope named(String name) {
        return new Envelope(name, 0);
    }
}
