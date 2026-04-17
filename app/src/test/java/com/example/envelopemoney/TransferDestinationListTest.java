package com.example.envelopemoney;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TransferDestinationListTest {

    @Test
    public void excludesSource_only() {
        List<Envelope> envs = Arrays.asList(
                named("Gas"),
                named("Food"),
                named("Vacation"));
        List<String> dest = TransferDestinationList.excludingSource(envs, "Food");
        assertEquals(Arrays.asList("Gas", "Vacation"), dest);
    }

    @Test
    public void excludesSource_trimmed() {
        List<Envelope> envs = Collections.singletonList(named("Gas"));
        assertEquals(0, TransferDestinationList.excludingSource(envs, " Gas ").size());
    }

    @Test
    public void nullOrEmptyList_safe() {
        assertEquals(0, TransferDestinationList.excludingSource(null, "A").size());
        assertEquals(0, TransferDestinationList.excludingSource(new ArrayList<>(), "A").size());
    }

    private static Envelope named(String name) {
        return new Envelope(name, 0);
    }
}
