package com.example.envelopemoney.receipt;

import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReceiptFolderOpenerTest {

    @Test
    public void albumDisplayName_fromFilePath() {
        assertEquals("MountainMoney_123.jpg", ReceiptFolderOpener.albumDisplayName(
                Uri.parse("file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_123.jpg")));
    }

    @Test
    public void albumDisplayName_fromContentPathLastSegment() {
        assertEquals("MountainMoney_123.jpg", ReceiptFolderOpener.albumDisplayName(
                Uri.parse("content://media/external/images/media/MountainMoney_123.jpg")));
    }

    @Test
    public void albumDisplayName_nullForPickerAndBareMediaId() {
        assertNull(ReceiptFolderOpener.albumDisplayName(
                Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1")));
        assertNull(ReceiptFolderOpener.albumDisplayName(
                Uri.parse("content://media/external/images/media/12345")));
        assertNull(ReceiptFolderOpener.albumDisplayName(null));
    }

    @Test
    public void persistUriAfterImport_prefersAlbumFileUriOverPicker() {
        Uri album = Uri.parse(
                "file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg");
        Uri picker = Uri.parse(
                "content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1");
        assertEquals(album, ReceiptFolderOpener.persistUriAfterImport(album, picker));
        assertEquals(album, ReceiptFolderOpener.persistUriAfterImport(album, null));
        assertEquals(album, ReceiptFolderOpener.persistUriAfterImport(picker, album));
    }

    @Test
    public void folderFileUri_includesDisplayName() {
        Uri uri = ReceiptFolderOpener.folderFileUri("MountainMoney_1.jpg");
        assertTrue(uri.toString().contains("MountainMoney_1.jpg"));
        assertEquals("MountainMoney_1.jpg", ReceiptFolderOpener.albumDisplayName(uri));
        assertFalse(ReceiptFolderOpener.isEphemeralPickerUri(uri));
    }

    @Test
    public void isEphemeralPickerUri_trueForPickerAndDocuments() {
        assertTrue(ReceiptFolderOpener.isEphemeralPickerUri(
                Uri.parse("content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1")));
        assertTrue(ReceiptFolderOpener.isEphemeralPickerUri(
                Uri.parse("content://com.android.providers.media.documents/document/image%3A9")));
        assertFalse(ReceiptFolderOpener.isEphemeralPickerUri(
                Uri.parse("file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg")));
    }
}
