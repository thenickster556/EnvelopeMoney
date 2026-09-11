package com.example.envelopemoney.receipt;

import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28, manifest = org.robolectric.annotation.Config.NONE)
public class ReceiptBitmapLoaderTest {

    @Test
    public void withAlbumDisplayName_keepsContentScheme() {
        Uri named = MediaStoreReceiptSaver.withAlbumDisplayName(
                Uri.parse("content://media/external/images/media/9"),
                "MountainMoney_1.jpg");
        assertEquals("content", named.getScheme());
        assertEquals("MountainMoney_1.jpg", named.getFragment());
        assertEquals("MountainMoney_1.jpg", ReceiptPickerUriNormalizer.albumDisplayName(named));
    }
}
