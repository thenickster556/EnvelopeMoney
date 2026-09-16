package com.example.envelopemoney.receipt;

import com.example.envelopemoney.*;
import com.google.gson.Gson;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReceiptReferenceRepairTest {
    private static final String OLD = "content://media/external/images/media/1";
    private static final String FRESH = "content://media/external/images/media/2";
    private Transaction yesterday() {
        Transaction transaction = new Transaction("Food", 12.50, "2026-09-07", "Lunch");
        transaction.setReceiptImageUri(OLD);
        return transaction;
    }
    @Test public void repairsCurrentAndHistoricalSharedReferencesAndSurvivesRestart() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction current = yesterday(); Transaction historical = yesterday();
        envelope.getTransactions().add(current);
        Envelope.MonthData month = new Envelope.MonthData(100, 87.5);
        month.transactions.add(historical);
        envelope.getMonthlyDataMap().put("2026-09", month);
        List<Envelope> envelopes = Arrays.asList(envelope);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(envelopes);
        assertEquals(2, entries.size());
        Map<String, ReceiptReferenceResolver.Result> results = new HashMap<>();
        for (ReceiptReferenceRepair.Entry entry : entries) results.put(entry.key(), ReceiptReferenceResolver.Result.resolved(FRESH, "yesterday.jpg"));
        assertEquals(2, ReceiptReferenceRepair.apply(envelopes, entries, results));
        assertEquals(0, ReceiptReferenceRepair.apply(envelopes, entries, results));
        Envelope restored = new Gson().fromJson(new Gson().toJson(envelope), Envelope.class);
        assertEquals(FRESH, restored.getTransactions().get(0).getReceiptImageUri());
        assertEquals("yesterday.jpg", restored.getTransactions().get(0).getReceiptImageFileName());
        assertEquals(FRESH, restored.getMonthlyDataMap().get("2026-09").transactions.get(0).getReceiptImageUri());
        assertEquals(12.50, current.getAmount(), 0);
    }
    @Test public void concurrentReplacementDeletionAndFailuresArePreserved() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction changed = yesterday(); Transaction removed = yesterday(); Transaction failed = yesterday();
        envelope.getTransactions().addAll(Arrays.asList(changed, removed, failed));
        List<Envelope> envelopes = Arrays.asList(envelope);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(envelopes);
        changed.setReceiptImageUri("content://replacement/3");
        envelope.getTransactions().remove(removed);
        Map<String, ReceiptReferenceResolver.Result> results = new HashMap<>();
        results.put(entries.get(0).key(), ReceiptReferenceResolver.Result.failure(ReceiptReferenceResolver.Status.MISSING));
        assertEquals(0, ReceiptReferenceRepair.apply(envelopes, entries, results));
        results.put(entries.get(0).key(), ReceiptReferenceResolver.Result.resolved(FRESH, "old.jpg"));
        assertEquals(1, ReceiptReferenceRepair.apply(envelopes, entries, results));
        assertEquals("content://replacement/3", changed.getReceiptImageUri());
        assertEquals(OLD, removed.getReceiptImageUri());
    }
    @Test public void emptyStateAndRepeatedObjectAreSafe() {
        assertTrue(ReceiptReferenceRepair.snapshot(null).isEmpty());
        Envelope envelope = new Envelope("Food", 100);
        Transaction transaction = yesterday();
        Transaction emptyUri = new Transaction("Food", 1, "2026-09-08", "No picture");
        emptyUri.setReceiptImageUri("");
        envelope.getTransactions().addAll(Arrays.asList(null, transaction, transaction, emptyUri));
        envelope.getMonthlyDataMap().put("2026-08", null);
        assertEquals(1, ReceiptReferenceRepair.snapshot(Arrays.asList(null, envelope)).size());
        assertEquals(0, ReceiptReferenceRepair.apply(Arrays.asList(envelope),
                ReceiptReferenceRepair.snapshot(Arrays.asList(envelope)), Collections.emptyMap()));
    }

    @Test public void filenameSurvivesTransferMirrorsAndSplitGroupEdits() {
        Envelope food = new Envelope("Food", 100);
        Envelope savings = new Envelope("Savings", 100);
        List<Envelope> envelopes = Arrays.asList(food, savings);
        Transaction source = yesterday(); source.setReceiptImageFileName("yesterday.jpg");
        food.getTransactions().add(source);
        TransferSyncHelper.applyTransferGroup(envelopes, source, "Food", Arrays.asList(
                new TransferBucketAllocation("bucket", "Savings", 2)));
        assertEquals("yesterday.jpg", savings.getTransactions().get(0).getReceiptImageFileName());
        List<SplitPurchaseSliceAllocation> slices = Arrays.asList(
                new SplitPurchaseSliceAllocation("food", "Food", 5),
                new SplitPurchaseSliceAllocation("savings", "Savings", 5));
        SplitPurchaseSyncHelper.applyGroup(envelopes, "split", "2026-09-07", "Lunch", OLD, slices, "2026-09");
        for (Transaction transaction : food.getTransactions()) {
            if ("split".equals(transaction.getSplitPurchaseGroupId())) transaction.setReceiptImageFileName("yesterday.jpg");
        }
        SplitPurchaseSyncHelper.applyGroup(envelopes, "split", "2026-09-07", "Lunch edited", OLD, slices, "2026-09");
        for (Envelope envelope : envelopes) {
            for (Transaction transaction : envelope.getTransactions()) {
                if ("split".equals(transaction.getSplitPurchaseGroupId())) assertEquals("yesterday.jpg", transaction.getReceiptImageFileName());
            }
        }
    }

    @Test public void resolveAllAssignsUniqueSameDayLeftover() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction unnamed = yesterday();
        envelope.getTransactions().add(unnamed);
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        String name = "MountainMoney_" + noon + ".jpg";
        album.add(FRESH, name, noon);
        Map<String, ReceiptReferenceResolver.Result> results = ReceiptReferenceRepair.resolveAll(
                album, ReceiptReferenceRepair.snapshot(Arrays.asList(envelope)), TimeZone.getTimeZone("UTC"));
        ReceiptReferenceResolver.Result result = results.get(
                ReceiptReferenceRepair.snapshot(Arrays.asList(envelope)).get(0).key());
        assertEquals(FRESH, result.reference);
        assertEquals(name, result.fileName);
    }

    @Test public void resolveAllLeavesTwoSameDayLeftoversAmbiguous() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction first = yesterday();
        Transaction second = new Transaction("Food", 4, "2026-09-07", "Dinner");
        second.setReceiptImageUri("content://media/external/images/media/9");
        envelope.getTransactions().addAll(Arrays.asList(first, second));
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "MountainMoney_" + noon + ".jpg", noon);
        album.add("content://media/external/images/media/3",
                "MountainMoney_" + (noon + 3_600_000L) + ".jpg", noon + 3_600_000L);
        Map<String, ReceiptReferenceResolver.Result> results = ReceiptReferenceRepair.resolveAll(
                album, ReceiptReferenceRepair.snapshot(Arrays.asList(envelope)), TimeZone.getTimeZone("UTC"));
        for (ReceiptReferenceRepair.Entry entry : ReceiptReferenceRepair.snapshot(Arrays.asList(envelope))) {
            assertEquals(ReceiptReferenceResolver.Status.AMBIGUOUS, results.get(entry.key()).status);
        }
    }

    @Test public void resolveAllPrefersUniqueNameOverSameDayFile() {
        Envelope envelope = new Envelope("Food", 100);
        long namedTime = utcNoon(2026, 9, 7);
        String named = "MountainMoney_" + namedTime + ".jpg";
        Transaction namedTx = yesterday();
        namedTx.setReceiptImageFileName(named);
        envelope.getTransactions().add(namedTx);
        FakeAlbum album = new FakeAlbum();
        album.add(FRESH, named, namedTime);
        album.add("content://media/external/images/media/3",
                "MountainMoney_" + (namedTime + 3_600_000L) + ".jpg", namedTime + 3_600_000L);
        Map<String, ReceiptReferenceResolver.Result> results = ReceiptReferenceRepair.resolveAll(
                album, ReceiptReferenceRepair.snapshot(Arrays.asList(envelope)), TimeZone.getTimeZone("UTC"));
        assertEquals(FRESH, results.get(ReceiptReferenceRepair.snapshot(Arrays.asList(envelope)).get(0).key()).reference);
    }

    private static long utcNoon(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0, 0);
        return calendar.getTimeInMillis();
    }

    private static final class FakeAlbum implements ReceiptReferenceResolver.Source {
        final Map<String, ReceiptReferenceResolver.Result> pictures = new HashMap<>();
        final List<ReceiptReferenceResolver.Result> album = new ArrayList<>();
        boolean restricted;
        boolean denyAlbum;
        void add(String reference, String name, long captureMs) {
            ReceiptReferenceResolver.Result picture =
                    ReceiptReferenceResolver.Result.resolved(reference, name, captureMs);
            pictures.put(reference, picture);
            album.add(picture);
        }
        public ReceiptReferenceResolver.Result inspect(String reference) {
            return pictures.getOrDefault(reference,
                    ReceiptReferenceResolver.Result.failure(ReceiptReferenceResolver.Status.MISSING));
        }
        public List<ReceiptReferenceResolver.Result> readAlbum() {
            if (denyAlbum) throw new SecurityException("album");
            return album;
        }
        public boolean albumAccessRestricted() { return restricted; }
    }

    @Test public void resolveAllGuardsAndWorkingUriReserveLeftovers() {
        assertTrue(ReceiptReferenceRepair.resolveAll(null, null, null).isEmpty());
        assertTrue(ReceiptReferenceRepair.resolveAll(new FakeAlbum(), Collections.emptyList(), null).isEmpty());
        Envelope envelope = new Envelope("Food", 100);
        Transaction working = yesterday();
        working.setReceiptImageUri(FRESH);
        Transaction leftover = new Transaction("Food", 3, "2026-09-07", "Snack");
        leftover.setReceiptImageUri("content://media/external/images/media/9");
        envelope.getTransactions().addAll(Arrays.asList(working, leftover));
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "MountainMoney_" + noon + ".jpg", noon);
        List<ReceiptReferenceRepair.Entry> entries = new ArrayList<>();
        entries.add(null);
        entries.addAll(ReceiptReferenceRepair.snapshot(Arrays.asList(envelope)));
        entries.add(entries.get(1));
        Map<String, ReceiptReferenceResolver.Result> results =
                ReceiptReferenceRepair.resolveAll(album, entries, null);
        assertEquals(FRESH, results.get(entries.get(1).key()).reference);
        assertEquals(ReceiptReferenceResolver.Status.MISSING, results.get(entries.get(2).key()).status);
    }

    @Test public void resolveAllUsesInspectedFilenameAndDeniedAlbum() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction unnamed = yesterday();
        envelope.getTransactions().add(unnamed);
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        String named = "MountainMoney_" + noon + ".jpg";
        album.pictures.put(OLD, ReceiptReferenceResolver.Result.failure(
                ReceiptReferenceResolver.Status.MISSING, named));
        album.add(FRESH, named, noon);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        assertEquals(FRESH, ReceiptReferenceRepair.resolveAll(album, entries, TimeZone.getTimeZone("UTC"))
                .get(entries.get(0).key()).reference);
        album.denyAlbum = true;
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED,
                ReceiptReferenceRepair.resolveAll(album, entries, TimeZone.getTimeZone("UTC"))
                        .get(entries.get(0).key()).status);
    }

    @Test public void resolveAllRestrictedAndVerificationFailures() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction unnamed = yesterday();
        envelope.getTransactions().add(unnamed);
        FakeAlbum album = new FakeAlbum();
        album.restricted = true;
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "MountainMoney_" + noon + ".jpg", noon);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED,
                ReceiptReferenceRepair.resolveAll(album, entries, TimeZone.getTimeZone("UTC"))
                        .get(entries.get(0).key()).status);
        album.pictures.put(OLD, ReceiptReferenceResolver.Result.failure(
                ReceiptReferenceResolver.Status.PERMISSION_REQUIRED));
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED,
                ReceiptReferenceRepair.resolveAll(album, entries, TimeZone.getTimeZone("UTC"))
                        .get(entries.get(0).key()).status);
        Transaction namedTx = yesterday();
        namedTx.setReceiptImageFileName("MountainMoney_" + noon + ".jpg");
        envelope.getTransactions().clear();
        envelope.getTransactions().add(namedTx);
        entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        album.pictures.put(FRESH, ReceiptReferenceResolver.Result.failure(
                ReceiptReferenceResolver.Status.CORRUPT));
        assertEquals(ReceiptReferenceResolver.Status.CORRUPT,
                ReceiptReferenceRepair.resolveAll(album, entries, TimeZone.getTimeZone("UTC"))
                        .get(entries.get(0).key()).status);
        album.pictures.put(FRESH, ReceiptReferenceResolver.Result.resolved(FRESH, "other.jpg", noon));
        assertEquals(ReceiptReferenceResolver.Status.MISSING,
                ReceiptReferenceRepair.resolveAll(album, entries, TimeZone.getTimeZone("UTC"))
                        .get(entries.get(0).key()).status);
        album.pictures.put(FRESH, ReceiptReferenceResolver.Result.resolved(FRESH, null, noon));
        assertEquals("MountainMoney_" + noon + ".jpg",
                ReceiptReferenceRepair.resolveAll(album, entries, TimeZone.getTimeZone("UTC"))
                        .get(entries.get(0).key()).fileName);
    }

    @Test public void resolveAllBindsRenamedDayMatchAndPersistsAlbumName() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction renamed = yesterday();
        renamed.setReceiptImageFileName("MountainMoney_gone.jpg");
        envelope.getTransactions().add(renamed);
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "IMG_20260907.jpg", noon);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        ReceiptReferenceResolver.Result result = ReceiptReferenceRepair.resolveAll(
                album, entries, TimeZone.getTimeZone("UTC")).get(entries.get(0).key());
        assertEquals(FRESH, result.reference);
        assertEquals("IMG_20260907.jpg", result.fileName);
        assertEquals(1, ReceiptReferenceRepair.apply(Arrays.asList(envelope), entries,
                Collections.singletonMap(entries.get(0).key(), result)));
        assertEquals(FRESH, renamed.getReceiptImageUri());
        assertEquals("IMG_20260907.jpg", renamed.getReceiptImageFileName());
    }

    @Test public void resolveAllRejectsDayMatchWhenInspectedNameDiffersFromListing() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction unnamed = yesterday();
        envelope.getTransactions().add(unnamed);
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "MountainMoney_" + noon + ".jpg", noon);
        album.pictures.put(FRESH, ReceiptReferenceResolver.Result.resolved(FRESH,
                "renamed-while-scanning.jpg", noon));
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        assertEquals(ReceiptReferenceResolver.Status.MISSING, ReceiptReferenceRepair.resolveAll(
                album, entries, TimeZone.getTimeZone("UTC")).get(entries.get(0).key()).status);
    }

    @Test public void resolveAllRestrictedAlbumAllowsIdentityButNotDateMatches() {
        FakeAlbum album = new FakeAlbum();
        album.restricted = true;
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "MountainMoney_" + noon + ".JPG", noon);
        Envelope envelope = new Envelope("Food", 100);
        Transaction named = yesterday();
        named.setReceiptImageFileName("MountainMoney_" + noon + ".jpg");
        envelope.getTransactions().add(named);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        assertEquals(FRESH, ReceiptReferenceRepair.resolveAll(
                album, entries, TimeZone.getTimeZone("UTC")).get(entries.get(0).key()).reference);
        Transaction unnamed = yesterday();
        envelope.getTransactions().clear();
        envelope.getTransactions().add(unnamed);
        entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED, ReceiptReferenceRepair.resolveAll(
                album, entries, TimeZone.getTimeZone("UTC")).get(entries.get(0).key()).status);
    }

    @Test public void resolveAllKeepsAmbiguousAlternativesForTheChooser() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction first = yesterday();
        Transaction second = new Transaction("Food", 4, "2026-09-07", "Dinner");
        second.setReceiptImageUri("content://media/external/images/media/9");
        envelope.getTransactions().addAll(Arrays.asList(first, second));
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "MountainMoney_" + noon + ".jpg", noon);
        album.add("content://media/external/images/media/3",
                "MountainMoney_" + (noon + 3_600_000L) + ".jpg", noon + 3_600_000L);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        Map<String, ReceiptReferenceResolver.Result> results = ReceiptReferenceRepair.resolveAll(
                album, entries, TimeZone.getTimeZone("UTC"));
        for (ReceiptReferenceRepair.Entry entry : entries) {
            ReceiptReferenceResolver.Result result = results.get(entry.key());
            assertEquals(ReceiptReferenceResolver.Status.AMBIGUOUS, result.status);
            assertEquals(2, result.alternatives.size());
        }
    }

    @Test public void resolveAllRestrictedAlbumStripsAlternativesIntoPermissionRequired() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction claim = yesterday();
        claim.setReceiptImageFileName("twin.jpg");
        envelope.getTransactions().add(claim);
        FakeAlbum album = new FakeAlbum();
        album.restricted = true;
        album.add(FRESH, "twin.jpg", utcNoon(2026, 9, 7));
        album.add("content://media/external/images/media/3", "twin.jpg", utcNoon(2026, 9, 7));
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        ReceiptReferenceResolver.Result result = ReceiptReferenceRepair.resolveAll(
                album, entries, TimeZone.getTimeZone("UTC")).get(entries.get(0).key());
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED, result.status);
        assertTrue(result.alternatives.isEmpty());
    }

    @Test public void swapCandidatesAfterPickReturnsSameDayOthers() {
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "a" + noon + ".jpg", noon);
        album.add("content://media/external/images/media/3", "b.jpg", noon + 3_600_000L);
        List<ReceiptReferenceResolver.Result> others = ReceiptReferenceRepair.swapCandidates(album,
                "a" + noon + ".jpg", "2026-09-07", FRESH, Collections.emptySet(),
                TimeZone.getTimeZone("UTC"));
        assertEquals(1, others.size());
        assertEquals("content://media/external/images/media/3", others.get(0).reference);
    }

    @Test public void swapCandidatesAfterPickReturnsEpochSiblingFromAnotherDay() {
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        long later = utcNoon(2026, 9, 9);
        album.add(FRESH, "a" + noon + ".jpg", noon);
        album.add("content://media/external/images/media/3", "b" + noon + " copy.jpg", later);
        List<ReceiptReferenceResolver.Result> others = ReceiptReferenceRepair.swapCandidates(album,
                "a" + noon + ".jpg", "2026-09-07", FRESH, Collections.emptySet(),
                TimeZone.getTimeZone("UTC"));
        assertEquals(1, others.size());
        assertEquals("content://media/external/images/media/3", others.get(0).reference);
    }

    @Test public void swapCandidatesIncludeNormalizedCopySibling() {
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "MountainMoney_" + noon + ".jpg", noon);
        album.add("content://media/external/images/media/3",
                "MountainMoney_" + noon + " (1).jpg", noon);
        List<ReceiptReferenceResolver.Result> others = ReceiptReferenceRepair.swapCandidates(album,
                "MountainMoney_" + noon + ".jpg", "2026-09-07", FRESH, Collections.emptySet(),
                TimeZone.getTimeZone("UTC"));
        assertEquals(1, others.size());
        assertEquals("content://media/external/images/media/3", others.get(0).reference);
    }

    @Test public void swapCandidatesExcludeReservedReferencesOfOtherRows() {
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "a" + noon + ".jpg", noon);
        album.add("content://media/external/images/media/3", "b.jpg", noon + 3_600_000L);
        List<ReceiptReferenceResolver.Result> others = ReceiptReferenceRepair.swapCandidates(album,
                "a" + noon + ".jpg", "2026-09-07", FRESH,
                Collections.singleton("content://media/external/images/media/3"),
                TimeZone.getTimeZone("UTC"));
        assertTrue(others.isEmpty());
    }

    @Test public void unusedNearbyKeepsPlusMinusOneDayMinusReserved() {
        FakeAlbum album = new FakeAlbum();
        TimeZone utc = TimeZone.getTimeZone("UTC");
        String same = "content://media/external/images/media/same";
        String before = "content://media/external/images/media/before";
        String after = "content://media/external/images/media/after";
        String far = "content://media/external/images/media/far";
        String reserved = "content://media/external/images/media/reserved";
        album.add(same, "IMG_same.jpg", utcNoon(2026, 9, 7));
        album.add(before, "IMG_before.jpg", utcNoon(2026, 9, 6));
        album.add(after, "IMG_after.jpg", utcNoon(2026, 9, 8));
        album.add(far, "IMG_far.jpg", utcNoon(2026, 9, 5));
        album.add(reserved, "IMG_taken.jpg", utcNoon(2026, 9, 7) + 3_600_000L);
        List<ReceiptReferenceResolver.Result> nearby = ReceiptReferenceRepair.unusedNearby(
                album, "2026-09-07", Collections.singleton(reserved), utc);
        assertEquals(3, nearby.size());
        assertEquals(same, nearby.get(0).reference);
        assertEquals(after, nearby.get(1).reference);
        assertEquals(before, nearby.get(2).reference);
        for (ReceiptReferenceResolver.Result picture : nearby) {
            assertFalse(far.equals(picture.reference));
        }
    }

    @Test public void unusedNotReservedKeepsFarFilesForSeeAllAndCloseAppend() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        FakeAlbum album = new FakeAlbum();
        String same = "content://media/external/images/media/same";
        String before = "content://media/external/images/media/before";
        String after = "content://media/external/images/media/after";
        String far = "content://media/external/images/media/far";
        String reserved = "content://media/external/images/media/reserved";
        album.add(same, "IMG_same.jpg", utcNoon(2026, 9, 7));
        album.add(before, "IMG_before.jpg", utcNoon(2026, 9, 6));
        album.add(after, "IMG_after.jpg", utcNoon(2026, 9, 8));
        album.add(far, "IMG_far.jpg", utcNoon(2026, 9, 5));
        album.add(reserved, "IMG_taken.jpg", utcNoon(2026, 9, 7) + 3_600_000L);
        Set<String> reservedSet = Collections.singleton(reserved);
        List<ReceiptReferenceResolver.Result> nearby = ReceiptAlbumMatcher.unusedNearby(
                album.album, "2026-09-07", reservedSet, utc);
        List<ReceiptReferenceResolver.Result> allUnused = ReceiptAlbumMatcher.unusedNotReserved(
                album.album, reservedSet);
        assertEquals(4, allUnused.size());
        List<ReceiptReferenceResolver.Result> leftovers = ReceiptAlbumMatcher.unusedNotAlreadyListed(
                allUnused, nearby);
        assertEquals(1, leftovers.size());
        assertEquals(far, leftovers.get(0).reference);
        assertEquals(4, ReceiptReferenceRepair.unusedNotReserved(album, reservedSet).size());
        FakeAlbum denied = new FakeAlbum();
        denied.denyAlbum = true;
        denied.add(far, "IMG_far.jpg", utcNoon(2026, 9, 5));
        assertTrue(ReceiptReferenceRepair.unusedNotReserved(denied, reservedSet).isEmpty());
        assertTrue(ReceiptReferenceRepair.unusedNotReserved(null, reservedSet).isEmpty());
        assertTrue(ReceiptAlbumMatcher.unusedNotReserved(null, null).isEmpty());
        assertTrue(ReceiptAlbumMatcher.unusedNotAlreadyListed(null, nearby).isEmpty());
    }

    @Test public void unusedNearbyFallsBackToAllUnusedWhenPlusMinusOneEmpty() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        FakeAlbum album = new FakeAlbum();
        String far = "content://media/external/images/media/far";
        String reserved = "content://media/external/images/media/reserved";
        String undated = "content://media/external/images/media/undated";
        album.add(far, "IMG_far.jpg", utcNoon(2026, 9, 5));
        album.add(reserved, "IMG_taken.jpg", utcNoon(2026, 8, 1));
        album.add(undated, "IMG_undated.jpg", 0L);
        List<ReceiptReferenceResolver.Result> nearby = ReceiptReferenceRepair.unusedNearby(
                album, "2026-09-07", Collections.singleton(reserved), utc);
        assertEquals(2, nearby.size());
        assertEquals(far, nearby.get(0).reference);
        assertEquals(undated, nearby.get(1).reference);
        assertEquals(2, ReceiptAlbumMatcher.unusedNearby(album.album, "2026-09-07",
                Collections.singleton(reserved), utc).size());
    }

    @Test public void unusedNearbyInvalidDateStillOffersUnusedAlbum() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        FakeAlbum album = new FakeAlbum();
        album.add(FRESH, "IMG_any.jpg", utcNoon(2026, 8, 16));
        List<ReceiptReferenceResolver.Result> nearby = ReceiptReferenceRepair.unusedNearby(
                album, "not-a-date", Collections.emptySet(), utc);
        assertEquals(1, nearby.size());
        assertEquals(FRESH, nearby.get(0).reference);
        assertEquals(1, ReceiptAlbumMatcher.unusedNearby(album.album, null, null, utc).size());
    }

    @Test public void unusedNearbyOffersRestrictedAlbumFilesAndStaysEmptyWhenDenied() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        FakeAlbum restricted = new FakeAlbum();
        restricted.restricted = true;
        restricted.add(FRESH, "IMG_same.jpg", utcNoon(2026, 9, 7));
        List<ReceiptReferenceResolver.Result> nearby = ReceiptReferenceRepair.unusedNearby(
                restricted, "2026-09-07", Collections.emptySet(), utc);
        assertEquals(1, nearby.size());
        assertEquals(FRESH, nearby.get(0).reference);
        FakeAlbum denied = new FakeAlbum();
        denied.denyAlbum = true;
        denied.add(FRESH, "IMG_same.jpg", utcNoon(2026, 9, 7));
        assertTrue(ReceiptReferenceRepair.unusedNearby(denied, "2026-09-07",
                Collections.emptySet(), utc).isEmpty());
        assertTrue(ReceiptReferenceRepair.unusedNearby(null, "2026-09-07",
                Collections.emptySet(), utc).isEmpty());
        assertTrue(ReceiptReferenceRepair.unusedNearby(new FakeAlbum(), "2026-09-07",
                Collections.emptySet(), utc).isEmpty());
    }

    @Test public void unusedNearbyStripsFragmentsAndIgnoresNullReserved() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        FakeAlbum album = new FakeAlbum();
        String keep = "content://media/external/images/media/keep#keep.jpg";
        String taken = "content://media/external/images/media/taken#taken.jpg";
        album.add(keep, "IMG_keep.jpg", utcNoon(2026, 9, 7));
        album.add(taken, "IMG_taken.jpg", utcNoon(2026, 9, 7) + 1_000L);
        Set<String> reserved = new HashSet<>();
        reserved.add(null);
        reserved.add("");
        reserved.add("content://media/external/images/media/taken");
        List<ReceiptReferenceResolver.Result> nearby = ReceiptReferenceRepair.unusedNearby(
                album, "2026-09-07", reserved, null);
        assertEquals(1, nearby.size());
        assertEquals(keep, nearby.get(0).reference);
        assertEquals(2, ReceiptAlbumMatcher.unusedNearby(album.album, "2026-09-07", null, utc).size());
    }

    @Test public void swapCandidatesUniqueSelfYieldsNone() {
        FakeAlbum album = new FakeAlbum();
        long noon = utcNoon(2026, 9, 7);
        album.add(FRESH, "MountainMoney_" + noon + ".jpg", noon);
        assertTrue(ReceiptReferenceRepair.swapCandidates(album,
                "MountainMoney_" + noon + ".jpg", "2026-09-07", FRESH + "#stale-name.jpg",
                Collections.emptySet(), TimeZone.getTimeZone("UTC")).isEmpty());
    }

    @Test public void swapCandidatesMissingAndDeniedAlbumsAreEmpty() {
        assertTrue(ReceiptReferenceRepair.swapCandidates(new FakeAlbum(),
                "MountainMoney_1.jpg", "2026-09-07", FRESH, Collections.emptySet(),
                TimeZone.getTimeZone("UTC")).isEmpty());
        FakeAlbum denied = new FakeAlbum();
        denied.denyAlbum = true;
        assertTrue(ReceiptReferenceRepair.swapCandidates(denied,
                "MountainMoney_1.jpg", "2026-09-07", FRESH, Collections.emptySet(),
                TimeZone.getTimeZone("UTC")).isEmpty());
    }

    @Test public void applySkipsUnchangedReferenceAndFilename() {
        Envelope envelope = new Envelope("Food", 100);
        Transaction transaction = yesterday();
        transaction.setReceiptImageFileName("yesterday.jpg");
        envelope.getTransactions().add(transaction);
        List<ReceiptReferenceRepair.Entry> entries = ReceiptReferenceRepair.snapshot(Arrays.asList(envelope));
        Map<String, ReceiptReferenceResolver.Result> results = new HashMap<>();
        results.put(entries.get(0).key(), ReceiptReferenceResolver.Result.resolved(OLD, "yesterday.jpg"));
        assertEquals(0, ReceiptReferenceRepair.apply(Arrays.asList(envelope), entries, results));
    }

    @Test public void replacingOrRemovingPictureClearsOldFilename() {
        Transaction transaction = yesterday(); transaction.setReceiptImageFileName("old.jpg");
        transaction.setReceiptImageUri(OLD);
        assertEquals("old.jpg", transaction.getReceiptImageFileName());
        transaction.setReceiptImageUri(FRESH);
        assertNull(transaction.getReceiptImageFileName());
        transaction.setReceiptImageUri(FRESH + "#new.jpg");
        assertEquals("new.jpg", transaction.getReceiptImageFileName());
        transaction.setReceiptImageUri(null);
        assertNull(transaction.getReceiptImageFileName());
    }
}
