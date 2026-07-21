package com.example.envelopemoney.receipt;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptPickerUriNormalizerTest {

    @Test
    public void isAppOwnedReceiptUri_falseForNullAndEmpty() {
        assertFalse(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(null));
        assertFalse(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(""));
        assertFalse(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri("   "));
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
