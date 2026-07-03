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
    public void isAppOwnedReceiptUri_trueForMountainMoneyAlbum() {
        assertTrue(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(
                "file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg"));
        assertTrue(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(
                "content://media/external/file/Pictures%2FMountain%20Money%2Fx.jpg"));
    }

    @Test
    public void isAppOwnedReceiptUri_falseForGenericMedia() {
        assertFalse(ReceiptPickerUriNormalizer.isAppOwnedReceiptUri(
                "content://media/external/images/media/12345"));
    }

    @Test
    public void shouldImportToAppGallery_trueForExternalPickerUris() {
        assertTrue(ReceiptPickerUriNormalizer.shouldImportToAppGallery(
                "content://com.android.providers.media.documents/document/image%3A12345"));
        assertTrue(ReceiptPickerUriNormalizer.shouldImportToAppGallery(
                "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1"));
        assertTrue(ReceiptPickerUriNormalizer.shouldImportToAppGallery(
                "content://media/external/images/media/999"));
    }

    @Test
    public void shouldImportToAppGallery_falseForAppAlbum() {
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
}
