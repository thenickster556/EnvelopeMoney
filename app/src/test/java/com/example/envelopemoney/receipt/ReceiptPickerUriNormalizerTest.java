package com.example.envelopemoney.receipt;

import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28, manifest = org.robolectric.annotation.Config.NONE)
public class ReceiptPickerUriNormalizerTest {

    @Test
    public void isAppOwnedReceiptUri_falseForNullAndEmpty() {
        assertFalse(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(null));
        assertFalse(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(""));
        assertFalse(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri("   "));
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 33)
    public void finishImportFlagsConsentWhenMediaSourceSurvives() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        RefusingGalleryProvider.register(context);
        Uri album = Uri.parse("content://media/external/images/media/99999");
        // The refusing provider models a GetContent grant: delete throws, the source survives.
        ReceiptPickerUriNormalizer.ImportResult survived = ReceiptPickerUriNormalizer.finishImportMoveOrRollback(
                context, album, Uri.parse("content://media/external/images/media/12345"));
        assertFalse(survived.sourceDeleted);
        assertTrue(survived.deleteNeedsConsent);
        assertEquals(album, survived.uri);
        // A file:// source that deletes cleanly needs no consent sheet.
        try {
            java.io.File temp = java.io.File.createTempFile("source", ".jpg",
                    context.getCacheDir());
            assertTrue(temp.exists());
            ReceiptPickerUriNormalizer.ImportResult moved = ReceiptPickerUriNormalizer.finishImportMoveOrRollback(
                    context, album, Uri.fromFile(temp));
            assertTrue(moved.sourceDeleted);
            assertFalse(moved.deleteNeedsConsent);
        } catch (java.io.IOException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    /** Minimal media provider whose delete always throws, like a read-only GetContent grant. */
    public static class RefusingGalleryProvider extends android.content.ContentProvider {
        static void register(android.content.Context context) {
            RefusingGalleryProvider provider = new RefusingGalleryProvider();
            android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
            info.authority = "media";
            info.exported = true;
            provider.attachInfo(context, info);
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media", provider);
        }

        @Override public boolean onCreate() { return true; }
        @Override public String getType(android.net.Uri uri) { return "image/jpeg"; }
        @Override public android.net.Uri insert(android.net.Uri uri, android.content.ContentValues values) { return null; }
        @Override public int delete(android.net.Uri uri, String selection, String[] arguments) {
            throw new SecurityException("read-only grant");
        }
        @Override public int update(android.net.Uri uri, android.content.ContentValues values, String selection, String[] arguments) { return 0; }
        @Override public android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] arguments, String sort) { return null; }
    }

    @Test
    public void isAppOwnedReceiptUri_trueForMountainMoneyAlbumPath() {
        assertTrue(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(
                "file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg"));
        assertTrue(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(
                "content://media/external/file/Pictures%2FMountain%20Money%2Fx.jpg"));
    }

    @Test
    public void isAppOwnedReceiptUri_stringAlone_falseForBareMediaStoreId() {
        // Without metadata, a MediaStore ID cannot be proven app-owned from the string alone.
        assertFalse(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(
                "content://media/external/images/media/12345"));
    }

    @Test
    public void matchesAppOwnedMediaMetadata_trueForMountainMoneyDisplayName() {
        assertTrue(ReceiptPickerUriNormalizer.matchesAppOwnedMediaMetadata(
                "MountainMoney_1710000000.jpg", null));
        assertTrue(ReceiptPickerUriNormalizer.matchesAppOwnedMediaMetadata(
                "MountainMoney_1.jpg", "DCIM/Camera"));
    }

    @Test
    public void matchesAppOwnedMediaMetadata_trueForRelativePathAlbum() {
        assertTrue(ReceiptPickerUriNormalizer.matchesAppOwnedMediaMetadata(
                "IMG_001.jpg", "Pictures/Mountain Money"));
        assertTrue(ReceiptPickerUriNormalizer.matchesAppOwnedMediaMetadata(
                null, "Pictures/Mountain Money/"));
    }

    @Test
    public void matchesAppOwnedMediaMetadata_falseForUnrelatedGallery() {
        assertFalse(ReceiptPickerUriNormalizer.matchesAppOwnedMediaMetadata(
                "IMG_001.jpg", "DCIM/Camera"));
        assertFalse(ReceiptPickerUriNormalizer.matchesAppOwnedMediaMetadata(null, null));
        assertFalse(ReceiptPickerUriNormalizer.matchesAppOwnedMediaMetadata("", ""));
    }

    @Test
    public void shouldImportToAppGallery_trueForExternalPickerUris() {
        assertTrue(ReceiptPickerUriNormalizer.shouldImportToAppGallery(
                "content://com.android.providers.media.documents/document/image%3A12345"));
        assertTrue(ReceiptPickerUriNormalizer.shouldImportToAppGallery(
                "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1"));
        // Bare MediaStore ID still imports until Context metadata proves ownership.
        assertTrue(ReceiptPickerUriNormalizer.shouldImportToAppGallery(
                "content://media/external/images/media/999"));
    }

    @Test
    public void shouldImportToAppGallery_falseForAppAlbumPath() {
        assertFalse(ReceiptPickerUriNormalizer.shouldImportToAppGallery(
                "file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg"));
    }

    @Test
    public void shouldCopyToAppGallery_aliasOfShouldImport() {
        assertTrue(ReceiptPickerUriNormalizer.shouldCopyToAppGallery(
                "content://media/external/images/media/999"));
        assertFalse(ReceiptPickerUriNormalizer.shouldCopyToAppGallery(
                "file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg"));
    }

    @Test
    public void persistUriAfterImport_keepsAlbumCopyWhenPickerSourceRemains() {
        Uri album = Uri.parse("content://media/external/images/media/9#MountainMoney_1.jpg");
        Uri picker = Uri.parse(
                "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1");
        assertEquals(album, ReceiptPickerUriNormalizer.persistUriAfterImport(album, picker));
        assertEquals(album, ReceiptPickerUriNormalizer.persistUriAfterImport(album, null));
        assertEquals(picker, ReceiptPickerUriNormalizer.persistUriAfterImport(null, picker));
    }

    @Test
    public void albumDisplayName_fromFragmentAndFilePath() {
        assertEquals("MountainMoney_1.jpg", ReceiptPickerUriNormalizer.albumDisplayName(
                Uri.parse("content://media/external/images/media/9#MountainMoney_1.jpg")));
        assertEquals("MountainMoney_1.jpg", ReceiptPickerUriNormalizer.albumDisplayName(
                Uri.parse("file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg")));
        assertNull(ReceiptPickerUriNormalizer.albumDisplayName(
                Uri.parse("content://media/external/images/media/9")));
        assertTrue(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(
                "content://media/external/images/media/9#MountainMoney_1.jpg"));
    }

    @Test
    public void canStreamCopyBytesInPlace_falseForEmptyOrNonJpeg() throws Exception {
        assertFalse(ReceiptPickerUriNormalizer.canStreamCopyBytesInPlace(new byte[0]));
        assertFalse(ReceiptPickerUriNormalizer.canStreamCopyBytesInPlace(new byte[]{0x00, 0x01}));
    }

    @Test
    public void shouldAttemptDelete_skipsAppOwnedPathSources() {
        assertFalse(ReceiptSourceDeleter.shouldAttemptDelete(
                "file:///storage/emulated/0/Pictures/Mountain Money/a.jpg",
                "file:///storage/emulated/0/Pictures/Mountain Money/b.jpg"));
    }
}
