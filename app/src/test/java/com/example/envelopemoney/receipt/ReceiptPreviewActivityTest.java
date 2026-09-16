package com.example.envelopemoney.receipt;

import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void candidateHelpers_roundTripAndClamp() {
        android.content.Intent check = new android.content.Intent();
        check.putExtra(ReceiptPreviewActivity.EXTRA_CANDIDATE_REFERENCES,
                new String[]{"content://media/external/images/media/2", "file:///tmp/a.jpg"});
        check.putExtra(ReceiptPreviewActivity.EXTRA_CANDIDATE_INDEX, 5);
        assertTrue(ReceiptPreviewActivity.isCandidateIntent(check));
        assertEquals(2, ReceiptPreviewActivity.candidateReferences(check).length);
        assertEquals("file:///tmp/a.jpg",
                ReceiptPreviewActivity.candidateReferences(check)[1]);
        assertEquals(1, ReceiptPreviewActivity.candidateIndex(check));
        assertFalse(ReceiptPreviewActivity.isCandidateIntent(new android.content.Intent()));
        assertFalse(ReceiptPreviewActivity.isCandidateIntent(null));
        assertEquals(0, ReceiptPreviewActivity.candidateReferences(null).length);
        assertEquals(0, ReceiptPreviewActivity.candidateIndex(null));
    }
}
