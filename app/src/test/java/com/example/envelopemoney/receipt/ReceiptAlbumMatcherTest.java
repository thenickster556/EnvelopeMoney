package com.example.envelopemoney.receipt;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReceiptAlbumMatcherTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String OLD = "content://media/external/images/media/1";
    private static final String A = "content://media/external/images/media/2";
    private static final String B = "content://media/external/images/media/3";

    private static long utcNoon(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(UTC);
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0, 0);
        return calendar.getTimeInMillis();
    }

    private static ReceiptReferenceResolver.Result picture(String reference, long captureMs) {
        String name = "MountainMoney_" + captureMs + ".jpg";
        return ReceiptReferenceResolver.Result.resolved(reference, name, captureMs);
    }

    private static ReceiptAlbumMatcher.Claim claim(String key, String fileName, String date) {
        return new ReceiptAlbumMatcher.Claim(key, fileName, date);
    }

    @Test
    public void captureTimeFromMountainMoneyEpochName() {
        long noon = utcNoon(2026, 9, 7);
        assertEquals(Long.valueOf(noon),
                ReceiptAlbumMatcher.captureTimeMs("MountainMoney_" + noon + ".jpg"));
        assertNull(ReceiptAlbumMatcher.captureTimeMs("IMG_001.jpg"));
        assertNull(ReceiptAlbumMatcher.captureTimeMs(null));
    }

    @Test
    public void uniqueSameDayUnnamedReceiptIsAssigned() {
        long noon = utcNoon(2026, 9, 7);
        List<ReceiptReferenceResolver.Result> album = Arrays.asList(picture(A, noon));
        Map<String, ReceiptReferenceResolver.Result> assigned = ReceiptAlbumMatcher.assign(
                Arrays.asList(claim("k1", null, "2026-09-07")), album, UTC);
        assertEquals(A, assigned.get("k1").reference);
        assertEquals("MountainMoney_" + noon + ".jpg", assigned.get("k1").fileName);
    }

    @Test
    public void twoSameDayUnnamedReceiptsStayAmbiguous() {
        List<ReceiptReferenceResolver.Result> album = Arrays.asList(
                picture(A, utcNoon(2026, 9, 7)),
                picture(B, utcNoon(2026, 9, 7) + 3_600_000L));
        Map<String, ReceiptReferenceResolver.Result> assigned = ReceiptAlbumMatcher.assign(
                Arrays.asList(claim("k1", null, "2026-09-07"), claim("k2", null, "2026-09-07")),
                album, UTC);
        assertEquals(ReceiptReferenceResolver.Status.AMBIGUOUS, assigned.get("k1").status);
        assertEquals(ReceiptReferenceResolver.Status.AMBIGUOUS, assigned.get("k2").status);
    }

    @Test
    public void uniqueNameBeatsSameDayUnusedFile() {
        long namedTime = utcNoon(2026, 9, 7);
        long otherTime = namedTime + 3_600_000L;
        String named = "MountainMoney_" + namedTime + ".jpg";
        List<ReceiptReferenceResolver.Result> album = Arrays.asList(
                ReceiptReferenceResolver.Result.resolved(A, named, namedTime),
                picture(B, otherTime));
        Map<String, ReceiptReferenceResolver.Result> assigned = ReceiptAlbumMatcher.assign(
                Arrays.asList(claim("k1", named, "2026-09-07")), album, UTC);
        assertEquals(A, assigned.get("k1").reference);
        assertEquals(named, assigned.get("k1").fileName);
    }

    @Test
    public void namedFileIsNotReusedForUnnamedSameDayReceipt() {
        long namedTime = utcNoon(2026, 9, 7);
        String named = "MountainMoney_" + namedTime + ".jpg";
        List<ReceiptReferenceResolver.Result> album = Arrays.asList(
                ReceiptReferenceResolver.Result.resolved(A, named, namedTime));
        Map<String, ReceiptReferenceResolver.Result> assigned = ReceiptAlbumMatcher.assign(
                Arrays.asList(claim("named", named, "2026-09-07"), claim("unnamed", null, "2026-09-07")),
                album, UTC);
        assertEquals(A, assigned.get("named").reference);
        assertEquals(ReceiptReferenceResolver.Status.MISSING, assigned.get("unnamed").status);
    }

    @Test
    public void emptyOrNullClaimsReturnEmpty() {
        assertTrue(ReceiptAlbumMatcher.assign(null, Collections.emptyList(), UTC).isEmpty());
        assertTrue(ReceiptAlbumMatcher.assign(Collections.emptyList(), null, null).isEmpty());
    }

    @Test
    public void namedFileMissingFromAlbumFallsThroughToSameDay() {
        long noon = utcNoon(2026, 9, 7);
        Map<String, ReceiptReferenceResolver.Result> assigned = ReceiptAlbumMatcher.assign(
                Arrays.asList(claim("k1", "MountainMoney_missing.jpg", "2026-09-07")),
                Arrays.asList(picture(A, noon), null,
                        ReceiptReferenceResolver.Result.resolved(B, null, 0L)),
                UTC, null, true);
        assertEquals(A, assigned.get("k1").reference);
    }

    @Test
    public void duplicateAlbumNamesAreAmbiguous() {
        long noon = utcNoon(2026, 9, 7);
        String named = "MountainMoney_" + noon + ".jpg";
        Map<String, ReceiptReferenceResolver.Result> assigned = ReceiptAlbumMatcher.assign(
                Arrays.asList(claim("k1", named, "2026-09-07")),
                Arrays.asList(
                        ReceiptReferenceResolver.Result.resolved(A, named, noon),
                        ReceiptReferenceResolver.Result.resolved(B, named, noon)),
                UTC);
        assertEquals(ReceiptReferenceResolver.Status.AMBIGUOUS, assigned.get("k1").status);
    }

    @Test
    public void invalidDateAndDisabledDateMatchStayMissing() {
        assertEquals(ReceiptReferenceResolver.Status.MISSING,
                ReceiptAlbumMatcher.assign(Arrays.asList(claim("k1", null, "not-a-date")),
                        Arrays.asList(picture(A, utcNoon(2026, 9, 7))), UTC).get("k1").status);
        assertEquals(ReceiptReferenceResolver.Status.MISSING,
                ReceiptAlbumMatcher.assign(Arrays.asList(claim("k1", null, "2026-09-07")),
                        Arrays.asList(picture(A, utcNoon(2026, 9, 7))), UTC,
                        Collections.emptySet(), false).get("k1").status);
    }

    @Test
    public void filenameEpochSuppliesCaptureWhenAlbumRowHasNone() {
        long noon = utcNoon(2026, 9, 7);
        String named = "MountainMoney_" + noon + ".jpg";
        ReceiptReferenceResolver.Result noStamp = ReceiptReferenceResolver.Result.resolved(A, named, 0L);
        Map<String, ReceiptReferenceResolver.Result> assigned = ReceiptAlbumMatcher.assign(
                Arrays.asList(claim("k1", null, "2026-09-07")),
                Arrays.asList(noStamp), UTC);
        assertEquals(A, assigned.get("k1").reference);
        assertNull(ReceiptAlbumMatcher.captureTimeMs("MountainMoney_123.jpg"));
        assertNull(ReceiptAlbumMatcher.captureTimeMs("MountainMoney_12345678901.png"));
        assertNull(ReceiptAlbumMatcher.captureTimeMs("MountainMoney_12a45678901.jpg"));
        assertEquals(Long.valueOf(1_234_567_890L),
                ReceiptAlbumMatcher.captureTimeMs("MountainMoney_1234567890.JPG"));
    }

    @Test
    public void reservedWorkingReferenceIsNotReusedForUnnamedSameDay() {
        long noon = utcNoon(2026, 9, 7);
        List<ReceiptReferenceResolver.Result> album = Arrays.asList(picture(A, noon));
        Map<String, ReceiptReferenceResolver.Result> assigned = ReceiptAlbumMatcher.assign(
                Arrays.asList(claim("unnamed", null, "2026-09-07")),
                album, UTC, Collections.singleton(A), true);
        assertEquals(ReceiptReferenceResolver.Status.MISSING, assigned.get("unnamed").status);
    }
}
