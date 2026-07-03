package com.example.envelopemoney.receipt;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptSourceDeleterTest {

    @Test
    public void shouldAttemptDelete_falseForNullOrAppOwned() {
        assertFalse(ReceiptSourceDeleter.shouldAttemptDelete(null, "content://x"));
        assertFalse(ReceiptSourceDeleter.shouldAttemptDelete("", "content://x"));
        assertFalse(ReceiptSourceDeleter.shouldAttemptDelete(
                "file:///storage/emulated/0/Pictures/Mountain Money/a.jpg", null));
        assertFalse(ReceiptSourceDeleter.shouldAttemptDelete(
                "content://same", "content://same"));
    }

    @Test
    public void shouldAttemptDelete_trueForExternalPicker() {
        assertTrue(ReceiptSourceDeleter.shouldAttemptDelete(
                "content://media/external/images/media/12345",
                "content://media/external/images/media/99999"));
    }

    @Test
    public void isMediaStoreImagesUri_trueForMediaContent() {
        assertTrue(ReceiptSourceDeleter.isMediaStoreImagesUri(
                "content://media/external/images/media/12345"));
        assertTrue(ReceiptSourceDeleter.isMediaStoreImagesUri(
                "content://com.android.providers.media.documents/document/image%3A12345"));
    }

    @Test
    public void isMediaStoreImagesUri_falseForNullAndPickerOnly() {
        assertFalse(ReceiptSourceDeleter.isMediaStoreImagesUri(null));
        assertFalse(ReceiptSourceDeleter.isMediaStoreImagesUri(
                "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1"));
    }

    @Test
    public void tryDeleteSource_falseForNullAndAppOwned() {
        assertFalse(ReceiptSourceDeleter.tryDeleteSource(null, null));
        assertFalse(ReceiptSourceDeleter.tryDeleteSource(
                null,
                android.net.Uri.parse("file:///storage/Pictures/Mountain Money/x.jpg")));
    }

    @Test
    public void shouldAttemptDelete_trueForDocumentUri() {
        assertTrue(ReceiptSourceDeleter.shouldAttemptDelete(
                "content://com.android.providers.media.documents/document/image%3A12345",
                "content://media/external/images/media/99999"));
    }
}
