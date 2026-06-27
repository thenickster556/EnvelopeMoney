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
    public void shouldCopyToAppGallery_trueForExternalPickerUris() {
        assertTrue(ReceiptPickerUriNormalizer.shouldCopyToAppGallery(
                "content://com.android.providers.media.documents/document/image%3A12345"));
        assertTrue(ReceiptPickerUriNormalizer.shouldCopyToAppGallery(
                "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1"));
        assertTrue(ReceiptPickerUriNormalizer.shouldCopyToAppGallery(
                "content://media/external/images/media/999"));
    }

    @Test
    public void shouldCopyToAppGallery_falseForAppAlbum() {
        assertFalse(ReceiptPickerUriNormalizer.shouldCopyToAppGallery(
                "file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg"));
    }
}
