package com.example.envelopemoney;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SplitPurchaseSyncHelperTest {

    @Test
    public void applyGroup_createsLinkedPositiveSlices() {
        Envelope fun = new Envelope("Fun", 100d);
        Envelope edu = new Envelope("Edu", 100d);
        List<Envelope> envelopes = Arrays.asList(fun, edu);

        String gid = "split-group-1";
        List<SplitPurchaseSliceAllocation> slices = Arrays.asList(
                new SplitPurchaseSliceAllocation("b1", "Fun", 4d),
                new SplitPurchaseSliceAllocation("b2", "Edu", 6d));

        SplitPurchaseSyncHelper.applyGroup(envelopes, gid, "2026-05-11", "Movie", null, slices, "2026-05");

        assertEquals(1, fun.getTransactions().size());
        assertEquals(1, edu.getTransactions().size());
        Transaction tFun = fun.getTransactions().get(0);
        assertEquals(gid, tFun.getSplitPurchaseGroupId());
        assertEquals("b1", tFun.getSplitPurchaseBucketId());
        assertEquals(4d, tFun.getAmount(), 0.0001d);
        Transaction tEdu = edu.getTransactions().get(0);
        assertEquals(gid, tEdu.getSplitPurchaseGroupId());
        assertEquals("b2", tEdu.getSplitPurchaseBucketId());
        assertEquals(6d, tEdu.getAmount(), 0.0001d);
    }

    @Test
    public void removeGroup_removesAllSlices() {
        Envelope fun = new Envelope("Fun", 100d);
        Envelope edu = new Envelope("Edu", 100d);
        List<Envelope> envelopes = Arrays.asList(fun, edu);
        SplitPurchaseSyncHelper.applyGroup(envelopes, "g", "2026-05-11", "x", null,
                Arrays.asList(
                        new SplitPurchaseSliceAllocation("b1", "Fun", 1d),
                        new SplitPurchaseSliceAllocation("b2", "Edu", 2d)),
                "2026-05");

        Set<String> months = SplitPurchaseSyncHelper.removeGroup(envelopes, "g");
        assertTrue(months.contains("2026-05"));
        assertEquals(0, fun.getTransactions().size());
        assertEquals(0, edu.getTransactions().size());
    }
}
