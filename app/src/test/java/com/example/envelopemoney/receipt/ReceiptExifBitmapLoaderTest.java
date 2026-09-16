package com.example.envelopemoney.receipt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReceiptExifBitmapLoaderTest {

    @Test
    public void exifToDegrees_rotate90() {
        assertEquals(90, ReceiptExifBitmapLoader.exifToDegrees(
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90));
    }

    @Test
    public void exifToDegrees_normal() {
        assertEquals(0, ReceiptExifBitmapLoader.exifToDegrees(
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL));
    }
}
