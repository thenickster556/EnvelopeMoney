package com.example.envelopemoney.receipt;

import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28, manifest = org.robolectric.annotation.Config.NONE)
public class ReceiptPreviewActivityTest {

    @Test
    public void intentDataUri_onlyContentScheme() {
        assertNotNull(ReceiptPreviewActivity.intentDataUri(
                Uri.parse("content://media/external/images/media/9")));
        assertNull(ReceiptPreviewActivity.intentDataUri(
                Uri.parse("file:///storage/emulated/0/Pictures/Mountain Money/MountainMoney_1.jpg")));
        assertNull(ReceiptPreviewActivity.intentDataUri(null));
    }

    @Test
    public void intentDataUri_stripsFragmentSoProviderOpenDoesNotSeeNameHint() {
        Uri named = Uri.parse("content://media/external/images/media/9#MountainMoney_1.jpg");
        Uri data = ReceiptPreviewActivity.intentDataUri(named);
        assertNotNull(data);
        assertEquals("content://media/external/images/media/9", data.toString());
        assertNull(data.getFragment());
    }
}
