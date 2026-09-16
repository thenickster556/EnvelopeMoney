package com.example.envelopemoney.receipt;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Regression fixtures deliberately include yesterday and today: recovery has no age threshold. */
public class ReceiptReferenceResolverTest {
    private static final String YESTERDAY = "MountainMoney_1788825600000.jpg";
    private static final String TODAY = "MountainMoney_1788912000000.jpg";
    private static final String OLD = "content://media/external/images/media/1";
    private static final String FRESH = "content://media/external/images/media/2";

    private static class Gallery implements ReceiptReferenceResolver.Source {
        final Map<String, ReceiptReferenceResolver.Result> pictures = new HashMap<>();
        final List<ReceiptReferenceResolver.Result> album = new ArrayList<>();
        int scans;
        boolean denied;
        boolean restricted;
        public ReceiptReferenceResolver.Result inspect(String reference) {
            return pictures.getOrDefault(reference, ReceiptReferenceResolver.Result.failure(
                    ReceiptReferenceResolver.Status.MISSING));
        }
        public List<ReceiptReferenceResolver.Result> readAlbum() {
            scans++;
            if (denied) throw new SecurityException();
            return album;
        }
        public boolean albumAccessRestricted() { return restricted; }
        void add(String reference, String name) {
            ReceiptReferenceResolver.Result picture = ReceiptReferenceResolver.Result.resolved(reference, name);
            pictures.put(reference, picture);
            album.add(picture);
        }
    }

    @Test public void yesterdayAndSameDayBrokenReferencesRecoverWithoutAgeCutoff() {
        for (String name : Arrays.asList(YESTERDAY, TODAY)) {
            Gallery gallery = new Gallery(); gallery.add(FRESH, name);
            ReceiptReferenceResolver resolver = new ReceiptReferenceResolver(gallery);
            assertEquals(FRESH, resolver.resolve(OLD, name).reference);
            assertEquals(FRESH, resolver.resolve("file:///storage/emulated/0/Pictures/Mountain Money/" + name, null).reference);
            assertEquals(FRESH, resolver.resolve(OLD + "#" + name, null).reference);
            assertEquals(1, gallery.scans);
        }
    }

    @Test public void workingOriginalWinsEvenWhenAlbumHasDuplicateNames() {
        Gallery gallery = new Gallery(); gallery.add(OLD, TODAY); gallery.add(FRESH, TODAY);
        assertEquals(OLD, new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).reference);
        assertEquals(0, gallery.scans);
    }

    @Test public void duplicateFallbackIsAmbiguousAndNeverSelectsNewest() {
        Gallery gallery = new Gallery(); gallery.add(FRESH, TODAY); gallery.add("content://other/3", TODAY);
        assertEquals(ReceiptReferenceResolver.Status.AMBIGUOUS,
                new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).status);
    }

    @Test public void missingIdentifierDoesNotGuessFromDatesOrNearbyPictures() {
        Gallery gallery = new Gallery(); gallery.add(FRESH, TODAY);
        assertEquals(ReceiptReferenceResolver.Status.MISSING,
                new ReceiptReferenceResolver(gallery).resolve(OLD, null).status);
        assertEquals("Even an unidentified reference triggers automatic folder discovery", 1, gallery.scans);
    }

    @Test public void failedStreamStillUsesReadableFilenameMetadata() {
        Gallery gallery = new Gallery(); gallery.add(FRESH, YESTERDAY);
        gallery.pictures.put(OLD, ReceiptReferenceResolver.Result.failure(
                ReceiptReferenceResolver.Status.MISSING, YESTERDAY));
        assertEquals(FRESH, new ReceiptReferenceResolver(gallery).resolve(OLD, null).reference);
    }

    @Test public void deniedFolderIsOnlyScannedOncePerPass() {
        Gallery gallery = new Gallery(); gallery.denied = true;
        ReceiptReferenceResolver resolver = new ReceiptReferenceResolver(gallery);
        resolver.resolve(OLD, TODAY); resolver.resolve(OLD, YESTERDAY);
        assertEquals(1, gallery.scans);
    }

    @Test public void unidentifiedRestrictedReferenceAsksForLibraryAccess() {
        Gallery gallery = new Gallery();
        gallery.restricted = true;
        gallery.add(FRESH, TODAY);
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED,
                new ReceiptReferenceResolver(gallery).resolve(OLD, null).status);
        assertEquals(1, gallery.scans);
    }

    @Test public void incompleteLibraryAccessAsksForPermissionInsteadOfMissing() {
        Gallery gallery = new Gallery();
        gallery.restricted = true;
        gallery.add(FRESH, "owned-today.jpg");
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED,
                new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).status);
    }

    @Test public void uniqueOwnedMatchIsUsedEvenWhenLibraryAccessIsRestricted() {
        Gallery gallery = new Gallery();
        gallery.restricted = true;
        gallery.add(FRESH, TODAY);
        assertEquals(FRESH, new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).reference);
    }

    @Test public void deniedAccessCanBeRetriedAfterGrant() {
        Gallery gallery = new Gallery(); gallery.denied = true;
        gallery.pictures.put(OLD, ReceiptReferenceResolver.Result.failure(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED));
        assertEquals(ReceiptReferenceResolver.Status.PERMISSION_REQUIRED,
                new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).status);
        gallery.denied = false; gallery.add(FRESH, TODAY);
        assertEquals(FRESH, new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).reference);
    }

    @Test public void corruptCandidateIsNotPersistable() {
        Gallery gallery = new Gallery(); gallery.add(FRESH, TODAY);
        gallery.pictures.put(FRESH, ReceiptReferenceResolver.Result.failure(ReceiptReferenceResolver.Status.CORRUPT));
        assertEquals(ReceiptReferenceResolver.Status.CORRUPT,
                new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).status);
    }

    @Test public void reusedMediaIdWithDifferentFilenameDoesNotOpenWrongPicture() {
        Gallery gallery = new Gallery(); gallery.add(OLD, "unrelated.jpg"); gallery.add(FRESH, TODAY);
        assertEquals(FRESH, new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).reference);
    }

    @Test public void candidateReplacedAfterFolderScanMustNotBePersisted() {
        Gallery gallery = new Gallery(); gallery.add(FRESH, TODAY);
        gallery.pictures.put(FRESH, ReceiptReferenceResolver.Result.resolved(FRESH, "another-picture.jpg"));
        assertEquals(ReceiptReferenceResolver.Status.MISSING,
                new ReceiptReferenceResolver(gallery).resolve(OLD, TODAY).status);
    }

    @Test public void referenceNamesSupportEncodedPathsFragmentsAndInvalidInput() {
        assertEquals(TODAY, ReceiptReferenceResolver.fileNameFromReference("/storage/Pictures/Mountain%20Money/" + TODAY));
        assertEquals("old receipt.jpg", ReceiptReferenceResolver.fileNameFromReference(OLD + "#old%20receipt.jpg"));
        assertNull(ReceiptReferenceResolver.fileNameFromReference(OLD));
        assertNull(ReceiptReferenceResolver.fileNameFromReference(null));
        assertNull(ReceiptReferenceResolver.fileNameFromReference(""));
        assertNull(ReceiptReferenceResolver.fileNameFromReference("%broken"));
        assertNull(ReceiptReferenceResolver.fileNameFromReference(OLD + "#../bad.jpg"));
    }

    @Test public void emptyMissingAndCorruptOriginalReturnExplicitOutcomes() {
        Gallery gallery = new Gallery();
        ReceiptReferenceResolver resolver = new ReceiptReferenceResolver(gallery);
        assertEquals(ReceiptReferenceResolver.Status.MISSING, resolver.resolve(null, null).status);
        assertEquals(ReceiptReferenceResolver.Status.MISSING, resolver.resolve("", TODAY).status);
        assertEquals(ReceiptReferenceResolver.Status.MISSING, resolver.resolve(OLD, TODAY).status);
        gallery.pictures.put(OLD, ReceiptReferenceResolver.Result.failure(ReceiptReferenceResolver.Status.CORRUPT));
        assertEquals(ReceiptReferenceResolver.Status.CORRUPT, resolver.resolve(OLD, null).status);
    }
}
